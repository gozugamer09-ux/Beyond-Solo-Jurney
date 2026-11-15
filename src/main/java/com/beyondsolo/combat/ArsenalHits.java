package com.beyondsolo.combat;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.item.SystemBowItem;
import com.beyondsolo.item.arsenal.ArsenalWeapon;
import com.beyondsolo.item.arsenal.Signature;
import com.beyondsolo.item.arsenal.Trait;
import com.beyondsolo.network.CombatFxPayload;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * What the arsenal's traits do in a fight: bonus damage (armor breaker, executioner, backstab, twin, rhythm,
 * shatter), on-hit procs (lifesteal, mending, frostbite, thunderclap), less damage taken (bulwark), spell power on
 * magic skill hits, System bow arrows (power, element, volley) and the divine weapons' class binding.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class ArsenalHits {
    /** Arrow data: the id of the System bow that loosed it. */
    public static final String BOW_TAG = "beyondsolo.bow";
    private static final String SPLIT_TAG = "beyondsolo.split";
    public static final float SHATTER_BONUS = 0.4f;
    public static final int RHYTHM_EVERY = 4;
    /** Divine weapons in the wrong hands hit this hard. */
    public static final float REFUSED_DAMAGE = 0.5f;

    /** True while a trait or signature deals its own damage, so it does not proc traits again. */
    private static int procDepth;
    private static final Map<LivingEntity, Rhythm> RHYTHM = new WeakHashMap<>();
    private static final Map<Player, Integer> LAST_REFUSAL = new WeakHashMap<>();

    private record Rhythm(int targetId, int count, int tick) {}

    /** A hit and the weapon behind it. */
    public record Hit(LivingEntity attacker, ArsenalWeapon weapon, ItemStack stack, boolean melee, @Nullable AbstractArrow arrow) {}

    public static @Nullable Hit hitOf(DamageSource source) {
        if (!(source.getEntity() instanceof LivingEntity attacker)) return null;
        // spells (skills, signatures) are not weapon hits, even though the caster is also the direct source
        if (source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.MAGIC)) return null;
        Entity direct = source.getDirectEntity();
        if (direct == attacker && !source.is(DamageTypeTags.IS_PROJECTILE)) {
            ItemStack stack = attacker.getMainHandItem();
            if (stack.getItem() instanceof ArsenalWeapon w && !(w instanceof SystemBowItem)) return new Hit(attacker, w, stack, true, null);
            return null;
        }
        if (direct instanceof AbstractArrow arrow && arrow.getPersistentData().contains(BOW_TAG)) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(arrow.getPersistentData().getString(BOW_TAG)));
            if (item instanceof ArsenalWeapon w) return new Hit(attacker, w, new ItemStack(item), false, arrow);
        }
        return null;
    }

    /** Divine weapons only fully answer to their own class. */
    public static boolean refuses(LivingEntity wielder, ArsenalWeapon w) {
        if (!w.boundToClass()) return false;
        if (!(wielder instanceof Player p)) return false;
        return !SystemAPI.isAwakened(p) || SystemAPI.get(p).playerClass() != w.favoredClass();
    }

    public static void warnRefused(Player p, ArsenalWeapon w) {
        Integer last = LAST_REFUSAL.get(p);
        if (last != null && p.tickCount - last < 100 && p.tickCount >= last) return;
        LAST_REFUSAL.put(p, p.tickCount);
        p.displayClientMessage(Component.translatable("message.beyondsolo.weapon_refuses", w.favoredClass().displayName())
                .withStyle(net.minecraft.ChatFormatting.RED), true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    /** Runs damage dealt by a trait or signature without it proccing traits again. */
    public static void proc(Runnable damage) {
        procDepth++;
        try {
            damage.run();
        } finally {
            procDepth--;
        }
    }

    public static boolean inProc() {
        return procDepth > 0;
    }

    /** Hostile things a weapon's area effects may hit: monsters and anything hunting the wielder, never players or pets. */
    public static boolean isFoe(LivingEntity wielder, LivingEntity e) {
        if (e == wielder || !e.isAlive() || e instanceof Player) return false;
        if (e instanceof TamableAnimal pet && pet.isTame()) return false;
        return e instanceof Enemy || (e instanceof Mob m && m.getTarget() == wielder);
    }

    public static List<LivingEntity> foesAround(LivingEntity wielder, Vec3 at, double radius) {
        return wielder.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
                e -> isFoe(wielder, e) && e.position().distanceTo(at) <= radius + e.getBbWidth() / 2);
    }

    /** Magic damage from the wielder (procs, signatures). */
    public static void magicHit(LivingEntity wielder, LivingEntity target, float amount) {
        proc(() -> {
            int inv = target.invulnerableTime;
            target.invulnerableTime = 0;
            target.hurt(wielder.damageSources().indirectMagic(wielder, wielder), amount);
            if (target.isAlive()) target.invulnerableTime = Math.max(inv, 0);
        });
    }

    // ------------------------------------------------------------------ damage in

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void incoming(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        if (target.level().isClientSide) return;
        float amount = event.getAmount();

        // spell power: magic skill hits from a wielder of a staff
        if ((source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.MAGIC)) && !inProc()
                && source.getEntity() instanceof LivingEntity caster && caster.getAttribute(ModAttributes.SPELL_POWER) != null) {
            amount *= 1f + (float) caster.getAttributeValue(ModAttributes.SPELL_POWER);
        }

        // bulwark: less damage while the weapon is in hand
        if (target.getMainHandItem().getItem() instanceof ArsenalWeapon guard && guard.has(Trait.Kind.BULWARK)
                && !refuses(target, guard)) {
            amount *= 1f - Math.min(0.6f, guard.trait(Trait.Kind.BULWARK));
        }

        Hit hit = inProc() || ElementalHits.isProc(source) ? null : hitOf(source);
        if (hit != null) {
            ArsenalWeapon w = hit.weapon();
            LivingEntity attacker = hit.attacker();
            float mult = 1f;
            if (w.has(Trait.Kind.ARMOR_BREAKER)) mult += Math.min(0.6f, w.trait(Trait.Kind.ARMOR_BREAKER) * target.getArmorValue());
            if (w.has(Trait.Kind.EXECUTIONER) && target.getHealth() < target.getMaxHealth() * 0.35f) mult += w.trait(Trait.Kind.EXECUTIONER);
            if (w.has(Trait.Kind.BACKSTAB) && behind(attacker, target)) {
                mult += w.trait(Trait.Kind.BACKSTAB);
                ((ServerLevel) target.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                        target.getX(), target.getY(0.6), target.getZ(), 12, 0.2, 0.3, 0.2, 0.3);
            }
            if (w.has(Trait.Kind.TWIN) && hit.melee() && attacker.getOffhandItem().getItem() instanceof ArsenalWeapon off && off.has(Trait.Kind.TWIN)) {
                mult += w.trait(Trait.Kind.TWIN);
            }
            if (hit.melee() && target.hasEffect(ModEffects.FROZEN)) {
                mult += SHATTER_BONUS;
                target.removeEffect(ModEffects.FROZEN);
                target.level().playSound(null, target.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1f, 0.8f);
                CombatFx.play(target, CombatFxPayload.Kind.SHATTER, 0);
            }
            if (w.has(Trait.Kind.RHYTHM) && hit.melee()) {
                Rhythm r = RHYTHM.get(attacker);
                int count = r != null && r.targetId() == target.getId() && attacker.tickCount - r.tick() < 60 ? r.count() + 1 : 1;
                if (count >= RHYTHM_EVERY) {
                    mult *= w.trait(Trait.Kind.RHYTHM);
                    count = 0;
                    target.level().playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 1.6f);
                    CombatFx.play(target, CombatFxPayload.Kind.NOVA, packNova(0xFFC8E0, 2));
                }
                RHYTHM.put(attacker, new Rhythm(target.getId(), count, attacker.tickCount));
            }
            if (refuses(attacker, w)) {
                mult *= REFUSED_DAMAGE;
                if (attacker instanceof Player p) warnRefused(p, w);
            }
            amount *= mult;
        }
        event.setAmount(amount);
    }

    private static boolean behind(LivingEntity attacker, LivingEntity target) {
        Vec3 facing = Vec3.directionFromRotation(0, target.getYRot());
        Vec3 toAttacker = attacker.position().subtract(target.position()).multiply(1, 0, 1).normalize();
        return facing.dot(toAttacker) < -0.3;
    }

    // ------------------------------------------------------------------ after the hit

    @SubscribeEvent
    static void afterDamage(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide || inProc() || ElementalHits.isProc(event.getSource())) return;
        float dealt = event.getNewDamage();
        if (dealt <= 0) return;
        Hit hit = hitOf(event.getSource());
        if (hit == null) return;
        ArsenalWeapon w = hit.weapon();
        LivingEntity attacker = hit.attacker();
        if (attacker == target) return;
        boolean refused = refuses(attacker, w);

        // arrows carry their bow's element
        if (!hit.melee() && w.buildup() > 0) Statuses.apply(target, w.element(), w.buildup() * ElementalHits.power(attacker), attacker);

        if (w.has(Trait.Kind.LIFESTEAL)) attacker.heal(dealt * w.trait(Trait.Kind.LIFESTEAL));
        if (w.has(Trait.Kind.MENDING)) mend(attacker, Math.max(1f, dealt * w.trait(Trait.Kind.MENDING)));
        if (w.has(Trait.Kind.FROSTBITE) && target.isAlive()) frostbite(target, attacker, w.trait(Trait.Kind.FROSTBITE));
        if (w.has(Trait.Kind.THUNDERCLAP) && attacker.getRandom().nextFloat() < w.trait(Trait.Kind.THUNDERCLAP)) {
            chainLightning(attacker, target, 2f + dealt * 0.35f);
        }
        if (attacker.hasEffect(ModEffects.INFERNAL_REND) && hit.melee()) {
            Statuses.apply(target, DamageElement.FIRE, 40, attacker);
            proc(() -> CombatDamage.deal(target, DamageElement.FIRE, 4f, attacker));
        }
        if (!refused && w.signature() == Signature.STARFALL && hit.arrow() != null && hit.arrow().isCritArrow()) {
            starfall(attacker, target);
        }
    }

    private static void mend(LivingEntity healer, float amount) {
        healer.heal(amount);
        if (healer instanceof Player) {
            for (Player ally : healer.level().getEntitiesOfClass(Player.class, healer.getBoundingBox().inflate(6), p -> p != healer && p.isAlive())) {
                ally.heal(amount);
            }
        }
        if (healer.level() instanceof ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, healer.getX(), healer.getY(1.1), healer.getZ(), 1, 0.3, 0.2, 0.3, 0);
        }
    }

    /** Stacks Frostbite I-III; one more hit freezes the target for {@code seconds}. */
    public static void frostbite(LivingEntity target, @Nullable LivingEntity source, float seconds) {
        if (target.hasEffect(ModEffects.FROZEN)) return;
        MobEffectInstance current = target.getEffect(ModEffects.FROSTBITE);
        int level = current == null ? 0 : current.getAmplifier() + 1;
        if (level >= 3) {
            freeze(target, source, Math.round(seconds * 20));
            return;
        }
        target.addEffect(new MobEffectInstance(ModEffects.FROSTBITE, 100, level, false, true, true), source);
        target.level().playSound(null, target.blockPosition(), SoundEvents.POWDER_SNOW_STEP, SoundSource.PLAYERS, 1f, 1.2f);
        if (target.level() instanceof ServerLevel l) {
            l.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE, target.getX(), target.getY(0.6), target.getZ(), 6 + level * 4, 0.25, 0.4, 0.25, 0.02);
        }
    }

    public static void freeze(LivingEntity target, @Nullable LivingEntity source, int ticks) {
        target.removeEffect(ModEffects.FROSTBITE);
        target.addEffect(new MobEffectInstance(ModEffects.FROZEN, ticks, 0, false, true, true), source);
        target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + 20));
        target.level().playSound(null, target.blockPosition(), SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 1.2f, 0.6f);
        CombatFx.play(target, CombatFxPayload.Kind.FREEZE, ticks);
    }

    /** Lightning arcs from the target into up to three more foes nearby. */
    public static void chainLightning(LivingEntity attacker, LivingEntity from, float damage) {
        List<LivingEntity> foes = foesAround(attacker, from.position(), 6).stream()
                .filter(e -> e != from)
                .sorted(Comparator.comparingDouble(e -> e.distanceToSqr(from)))
                .limit(3).toList();
        from.level().playSound(null, from.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.35f, 1.8f);
        CombatFx.play(from, CombatFxPayload.Kind.CHAIN, from.getId());
        LivingEntity prev = from;
        for (LivingEntity e : foes) {
            CombatFx.play(e, CombatFxPayload.Kind.CHAIN, prev.getId());
            magicHit(attacker, e, damage);
            prev = e;
        }
        if (foes.isEmpty()) magicHit(attacker, from, damage * 0.5f);
    }

    /** Starfall: a crit arrow brings down a bolt of starlight that hurts everything around the target. */
    static void starfall(LivingEntity attacker, LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level)) return;
        for (LivingEntity e : foesAround(attacker, target.position(), 3)) magicHit(attacker, e, 6f);
        Vec3 at = target.position();
        level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 1.2f, 1.5f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.4f, 0.7f);
        ArsenalAbilities.send(attacker, Signature.STARFALL, com.beyondsolo.network.SignatureFxPayload.IMPACT, List.of(at));
    }

    /** NOVA effect parameter: colour and radius in blocks. */
    public static int packNova(int rgb, int radius) {
        return (rgb & 0xFFFFFF) | (Math.min(radius, 127) << 24);
    }

    // ------------------------------------------------------------------ System bows

    @SubscribeEvent
    static void arrowLoosed(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (arrow.getPersistentData().contains(BOW_TAG) || !(arrow.getOwner() instanceof LivingEntity owner)) return;
        ItemStack bow = arrow.getWeaponItem();
        if (bow == null || !(bow.getItem() instanceof SystemBowItem)) {
            bow = owner.getMainHandItem().getItem() instanceof SystemBowItem ? owner.getMainHandItem()
                    : owner.getOffhandItem().getItem() instanceof SystemBowItem ? owner.getOffhandItem() : ItemStack.EMPTY;
        }
        if (!(bow.getItem() instanceof SystemBowItem sb)) return;
        arrow.getPersistentData().putString(BOW_TAG, BuiltInRegistries.ITEM.getKey(sb).toString());
        com.beyondsolo.gear.Infusions.tagArrow(arrow, bow);
        boolean refused = refuses(owner, sb);
        arrow.setBaseDamage(arrow.getBaseDamage() * sb.damageMultiplier() * (refused ? REFUSED_DAMAGE : 1f));
        if (refused && owner instanceof Player p) warnRefused(p, sb);

        int extra = Math.round(sb.trait(Trait.Kind.SPLIT_SHOT));
        if (extra > 0 && arrow.isCritArrow() && !arrow.getPersistentData().getBoolean(SPLIT_TAG) && !refused) {
            Vec3 v = arrow.getDeltaMovement();
            for (int i = 0; i < extra; i++) {
                double angle = Math.toRadians((i % 2 == 0 ? 1 : -1) * 7 * (i / 2 + 1));
                Arrow split = new Arrow(arrow.level(), owner, new ItemStack(net.minecraft.world.item.Items.ARROW), bow);
                split.setPos(arrow.getX(), arrow.getY(), arrow.getZ());
                split.setDeltaMovement(v.yRot((float) angle));
                split.setCritArrow(true);
                split.setBaseDamage(arrow.getBaseDamage());
                split.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                split.getPersistentData().putString(BOW_TAG, BuiltInRegistries.ITEM.getKey(sb).toString());
                split.getPersistentData().putBoolean(SPLIT_TAG, true);
                com.beyondsolo.gear.Infusions.copyTag(arrow, split);
                arrow.level().addFreshEntity(split);
            }
        }
    }

    // ------------------------------------------------------------------ held effects

    /** The World Tree's branch quietly heals its healer's party. */
    @SubscribeEvent
    static void heldTick(PlayerTickEvent.Post event) {
        Player p = event.getEntity();
        if (p.level().isClientSide || p.tickCount % 40 != 0) return;
        if (p.getMainHandItem().getItem() instanceof ArsenalWeapon w && w.signature() == Signature.VERDANT_BLESSING && !refuses(p, w)) {
            for (Player ally : p.level().getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(8), Player::isAlive)) {
                if (ally.getHealth() < ally.getMaxHealth()) {
                    ally.heal(1f);
                    ((ServerLevel) p.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                            ally.getX(), ally.getY(1.0), ally.getZ(), 3, 0.3, 0.4, 0.3, 0);
                }
            }
        }
        if (p instanceof ServerPlayer sp && p.hasEffect(ModEffects.FROZEN)) sp.setSprinting(false);
        if (p.hasEffect(ModEffects.FROZEN)) p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 45, 1, false, false, false));
    }

    private ArsenalHits() {}
}
