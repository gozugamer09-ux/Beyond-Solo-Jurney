package com.beyondsolo.armory;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.ArsenalHits;
import com.beyondsolo.combat.CombatDamage;
import com.beyondsolo.combat.CombatFx;
import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.combat.ElementalHits;
import com.beyondsolo.combat.ModEffects;
import com.beyondsolo.combat.Statuses;
import com.beyondsolo.item.ArmorSets;
import com.beyondsolo.network.CombatFxPayload;
import com.beyondsolo.skill.SkillAPI;
import com.beyondsolo.skill.SkillEvent;
import com.beyondsolo.util.ServerScheduler;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The four-piece perks of the armory's sets ({@link ArmorCatalog}), divine sets included. A perk only works for a
 * wearer of all four pieces who draws the set out ({@link ArmorSets#fullSet}). Its guards over a blow go through
 * {@link com.beyondsolo.system.BalanceHooks#keep} and its dodges through {@code avoids}, inside the ceilings every guard
 * and dodge shares. Effects stay light: a spark, a ring, a sound.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class SetPerks {
    // offence
    public static final float HUNTERS_MARK = 0.15f;
    public static final float WEBSHOT = 0.10f;
    public static final float POUNCE = 0.35f;
    public static final float PRISM_CHARGE = 0.30f;
    public static final int PRISM_COOLDOWN = 4 * 20;
    public static final float DRAGONS_BLOOD = 0.20f;
    public static final float DRAGONS_BLOOD_HEAL = 0.05f;
    public static final float MOONLIT = 0.20f;
    public static final int MOON_FANG_EVERY = 5;
    public static final float CASCADE_PER_STACK = 0.05f;
    public static final int CASCADE_MAX = 5;
    public static final float FURY_PER_STACK = 0.06f;
    public static final int FURY_MAX = 5;
    public static final float VEIL_STRIKE = 0.60f;
    public static final float VEIL_DODGE = 0.15f;
    public static final float WINDWALKER_RANGE = 20f;
    public static final float WINDWALKER_FAR = 0.20f;
    public static final float WINDWALKER_ARROW = 0.40f;
    public static final float HOLLOW_ECHO_CHANCE = 0.2f;
    public static final float HOLLOW_ECHO = 0.35f;
    public static final float STORM_SIGIL_CHANCE = 0.25f;
    public static final float STORM_SIGIL = 0.30f;
    public static final int CLEAVE_EVERY = 3;
    public static final float CLEAVE = 0.5f;
    public static final float PHANTOM_ECHO = 0.5f;
    // defence
    public static final float TOWER_GUARD = 0.20f;
    public static final float ASHEN_BULWARK = 0.25f;
    public static final float OATH_BRACED = 0.25f;
    public static final float OATH_ALLIES = 0.10f;
    public static final float SANCTUARY = 0.15f;
    public static final float CRYSTAL_SPINES = 0.20f;
    /** The most shield Overflowing Grace builds out of spilled healing. */
    public static final float GRACE_SHIELD = 6f;

    // ------------------------------------------------------------------ state (cooldowns, primed strikes, stacks)

    private static final class State {
        final Map<String, Long> until = new HashMap<>();
        final Map<String, Integer> count = new HashMap<>();
    }

    private static final Map<LivingEntity, State> STATE = new WeakHashMap<>();

    private static State state(LivingEntity e) {
        return STATE.computeIfAbsent(e, k -> new State());
    }

    private static long now(LivingEntity e) {
        return e.level().getGameTime();
    }

    static void prime(LivingEntity e, String key, int ticks) {
        state(e).until.put(key, now(e) + ticks);
    }

    public static boolean primed(LivingEntity e, String key) {
        State s = STATE.get(e);
        Long until = s == null ? null : s.until.get(key);
        return until != null && now(e) <= until && until - now(e) < 20 * 600;
    }

    static boolean consume(LivingEntity e, String key) {
        if (!primed(e, key)) return false;
        state(e).until.remove(key);
        return true;
    }

    /** True when the cooldown has run out, and starts it again. */
    static boolean ready(LivingEntity e, String key, int cooldown) {
        State s = state(e);
        long t = now(e);
        Long next = s.until.get("cd." + key);
        if (next != null && t < next && next - t <= cooldown) return false;
        s.until.put("cd." + key, t + cooldown);
        return true;
    }

    static int bump(LivingEntity e, String key) {
        State s = state(e);
        int c = s.count.getOrDefault(key, 0) + 1;
        s.count.put(key, c);
        return c;
    }

    static void clear(LivingEntity e, String key) {
        State s = STATE.get(e);
        if (s != null) {
            s.count.remove(key);
            s.until.remove("st." + key);
        }
    }

    /** Stacks that run out {@code window} ticks after the last one was added. */
    public static int stacks(LivingEntity e, String key) {
        if (!primed(e, "st." + key)) return 0;
        return state(e).count.getOrDefault(key, 0);
    }

    static int addStack(LivingEntity e, String key, int max, int window) {
        int c = Math.min(max, stacks(e, key) + 1);
        state(e).count.put(key, c);
        prime(e, "st." + key, window);
        return c;
    }

    /** Forgets every cooldown and stack (tests). */
    public static void reset(LivingEntity e) {
        STATE.remove(e);
    }

    // ------------------------------------------------------------------ what kind of hit

    static boolean melee(DamageSource s, LivingEntity attacker) {
        return s.getDirectEntity() == attacker && !s.is(DamageTypeTags.IS_PROJECTILE)
                && !s.is(DamageTypes.INDIRECT_MAGIC) && !s.is(DamageTypes.MAGIC);
    }

    static @Nullable AbstractArrow arrow(DamageSource s, LivingEntity attacker) {
        return s.getDirectEntity() instanceof AbstractArrow a && s.getEntity() == attacker ? a : null;
    }

    static boolean spell(DamageSource s, LivingEntity attacker) {
        return s.getEntity() == attacker && (s.is(DamageTypes.INDIRECT_MAGIC) || s.is(DamageTypes.MAGIC));
    }

    /** True when the hit comes from in front of the target. */
    static boolean fromFront(LivingEntity target, DamageSource source) {
        Vec3 from = source.getSourcePosition();
        if (from == null) return false;
        Vec3 to = from.subtract(target.position()).multiply(1, 0, 1);
        if (to.lengthSqr() < 1e-4) return false;
        return Vec3.directionFromRotation(0, target.getYRot()).dot(to.normalize()) > 0.2;
    }

    static List<Player> allies(LivingEntity center, double radius) {
        return center.level().getEntitiesOfClass(Player.class, center.getBoundingBox().inflate(radius),
                p -> p.isAlive() && !p.isSpectator() && p.distanceToSqr(center) <= radius * radius);
    }

    static boolean dark(LivingEntity e) {
        return e.level().getMaxLocalRawBrightness(e.blockPosition()) < 8;
    }

    // ------------------------------------------------------------------ damage in: more for the attacker, less for the wearer

    @SubscribeEvent(priority = EventPriority.LOW)
    static void incoming(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        DamageSource source = event.getSource();
        // what guards the wearer (inside the floor every guard over a blow shares) and what the attacker adds
        float guard = 1f;
        float mult = 1f;

        // the wearer is hit
        ArmorSets.Set worn = ArmorSets.fullSet(target);
        if (worn != null && worn.perk != null && source.getEntity() != null && source.getEntity() != target) {
            switch (worn.perk) {
                case "tower_guard" -> {
                    if (fromFront(target, source)) guard -= TOWER_GUARD;
                }
                case "ashen_bulwark" -> {
                    if (fromFront(target, source)) guard -= ASHEN_BULWARK;
                }
                case "oath_of_iron" -> {
                    if (target.isShiftKeyDown() || target.isBlocking()) guard -= OATH_BRACED;
                }
                case "shadow_veil" -> {
                    if (com.beyondsolo.system.BalanceHooks.avoids(target, source, VEIL_DODGE)) {
                        event.setCanceled(true);
                        prime(target, "veil", 3 * 20);
                        burst(target, ParticleTypes.SQUID_INK, 12, 0.4);
                        sound(target, SoundEvents.ENDERMAN_TELEPORT, 0.5f, 1.6f);
                        return;
                    }
                }
                default -> {}
            }
        }
        // guardians watching over the players around them
        if (target instanceof Player && source.getEntity() != null && source.getEntity() != target) {
            boolean oath = false, sanctuary = false;
            for (Player ally : allies(target, 8)) {
                ArmorSets.Set g = ArmorSets.fullSet(ally);
                if (g == null || g.perk == null) continue;
                if (!sanctuary && g.perk.equals("sanctuary")) {
                    sanctuary = true;
                    guard -= SANCTUARY;
                }
                if (!oath && ally != target && g.perk.equals("oath_of_iron") && ally.distanceToSqr(target) <= 36) {
                    oath = true;
                    guard -= OATH_ALLIES;
                }
            }
        }

        // the wearer hits
        if (source.getEntity() instanceof LivingEntity attacker && attacker != target && !ArsenalHits.inProc() && !ElementalHits.isProc(source)) {
            ArmorSets.Set set = ArmorSets.fullSet(attacker);
            if (set != null && set.perk != null) {
                boolean melee = melee(source, attacker);
                AbstractArrow arrow = arrow(source, attacker);
                boolean spell = spell(source, attacker);
                switch (set.perk) {
                    case "hunters_mark" -> {
                        if (arrow != null && target.hasEffect(MobEffects.GLOWING)) mult += HUNTERS_MARK;
                    }
                    case "webshot" -> {
                        if (arrow != null && target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) mult += WEBSHOT;
                    }
                    case "pounce" -> {
                        if (melee && consume(attacker, "pounce")) {
                            mult += POUNCE;
                            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), attacker);
                            burst(target, ParticleTypes.CRIT, 10, 0.3);
                        }
                    }
                    case "prism_charge" -> {
                        if (melee && attacker.isSprinting() && ready(attacker, "prism", PRISM_COOLDOWN)) {
                            mult += PRISM_CHARGE;
                            target.knockback(1.1, attacker.getX() - target.getX(), attacker.getZ() - target.getZ());
                            burst(target, ParticleTypes.END_ROD, 10, 0.3);
                            sound(target, SoundEvents.AMETHYST_BLOCK_BREAK, 1f, 0.7f);
                        }
                    }
                    case "dragons_blood" -> {
                        if (melee && attacker.getHealth() < attacker.getMaxHealth() * 0.5f) mult += DRAGONS_BLOOD;
                    }
                    case "moonlit_hunt" -> {
                        if (arrow != null && dark(attacker)) mult += MOONLIT;
                    }
                    case "arcane_cascade" -> {
                        if (spell) mult += CASCADE_PER_STACK * stacks(attacker, "cascade");
                    }
                    case "warlords_fury" -> {
                        if (melee || arrow != null || spell) mult += FURY_PER_STACK * stacks(attacker, "fury");
                    }
                    case "shadow_veil" -> {
                        if ((melee || arrow != null) && consume(attacker, "veil")) {
                            mult += VEIL_STRIKE;
                            Statuses.apply(target, DamageElement.BLOOD, 30f, attacker);
                            burst(target, ParticleTypes.DAMAGE_INDICATOR, 6, 0.3);
                        }
                    }
                    case "windwalker" -> {
                        if (arrow != null && attacker.distanceTo(target) >= WINDWALKER_RANGE) mult += WINDWALKER_FAR;
                    }
                    default -> {}
                }
            }
        }
        if (guard < 1f) mult *= com.beyondsolo.system.BalanceHooks.keep(target, Math.max(0.05f, guard));
        if (mult != 1f) event.setAmount(event.getAmount() * Math.max(0.05f, mult));
    }

    // ------------------------------------------------------------------ after the hit

    @SubscribeEvent
    static void afterDamage(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        float dealt = event.getNewDamage();
        if (dealt <= 0) return;
        DamageSource source = event.getSource();

        // the wearer was hit
        ArmorSets.Set worn = ArmorSets.fullSet(target);
        if (worn != null && worn.perk != null && target.isAlive()) {
            LivingEntity attacker = source.getEntity() instanceof LivingEntity a && a != target ? a : null;
            switch (worn.perk) {
                case "crystal_spines" -> {
                    if (attacker != null && melee(source, attacker) && !ArsenalHits.inProc()) {
                        ArsenalHits.magicHit(target, attacker, Math.max(1f, dealt * CRYSTAL_SPINES));
                        burst(attacker, ParticleTypes.ELECTRIC_SPARK, 6, 0.3);
                        sound(target, SoundEvents.AMETHYST_CLUSTER_HIT, 0.8f, 1.2f);
                    }
                }
                case "ashen_bulwark" -> {
                    if (attacker != null && ready(target, "taunt", 5 * 20)) taunt(target, 6);
                }
                case "sanctuary" -> {
                    if (target.getHealth() < target.getMaxHealth() * 0.4f && ready(target, "barrier", 60 * 20)) {
                        for (Player p : allies(target, 8)) p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 160, 1));
                        target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 160, 1));
                        CombatFx.play(target, CombatFxPayload.Kind.NOVA, ArsenalHits.packNova(0xFFE7A0, 8));
                        sound(target, SoundEvents.BEACON_POWER_SELECT, 1f, 1.4f);
                    }
                }
                default -> {}
            }
        }

        // the wearer hit something
        if (ArsenalHits.inProc() || ElementalHits.isProc(source) || !(source.getEntity() instanceof LivingEntity attacker) || attacker == target) return;
        ArmorSets.Set set = ArmorSets.fullSet(attacker);
        if (set == null || set.perk == null) return;
        boolean melee = melee(source, attacker);
        AbstractArrow arrow = arrow(source, attacker);
        boolean spell = spell(source, attacker);
        float power = ElementalHits.power(attacker);
        switch (set.perk) {
            case "venom_fangs" -> {
                if (melee && target.isAlive()) Statuses.apply(target, DamageElement.POISON, 8f * power, attacker);
            }
            case "empress_venom" -> {
                if (melee && target.isAlive()) Statuses.apply(target, DamageElement.POISON, 12f * power, attacker);
            }
            case "hunters_mark" -> {
                if (arrow != null && target.isAlive() && !target.hasEffect(MobEffects.GLOWING)) {
                    target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 6 * 20, 0, false, false, true), attacker);
                    sound(target, SoundEvents.ARROW_HIT_PLAYER, 0.6f, 1.6f);
                }
            }
            case "webshot" -> {
                if (arrow != null && target.isAlive()) {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), attacker);
                    burst(target, ParticleTypes.ITEM_COBWEB, 6, 0.3);
                }
            }
            case "frostbite_arrows" -> {
                if (arrow != null && target.isAlive()) ArsenalHits.frostbite(target, attacker, 1.5f);
            }
            case "moonlit_hunt" -> {
                if (arrow != null && target.isAlive() && bump(attacker, "moonfang") % MOON_FANG_EVERY == 0) {
                    ArsenalHits.magicHit(attacker, target, com.beyondsolo.system.Balance.flatDamage(attacker, 8f));
                    if (target.isAlive()) Statuses.apply(target, DamageElement.BLOOD, 20f, attacker);
                    burst(target, ParticleTypes.SCULK_SOUL, 10, 0.4);
                    sound(target, SoundEvents.WOLF_GROWL, 0.9f, 0.7f);
                }
            }
            case "ashfall" -> {
                if (arrow != null) {
                    if (target.isAlive()) Statuses.apply(target, DamageElement.FIRE, 15f * power, attacker);
                    if (arrow.isCritArrow()) {
                        for (LivingEntity e : ArsenalHits.foesAround(attacker, target.position(), 2.5)) {
                            ArsenalHits.proc(() -> CombatDamage.deal(e, DamageElement.FIRE, com.beyondsolo.system.Balance.flatDamage(attacker, 4f), attacker));
                        }
                        burst(target, ParticleTypes.FLAME, 16, 0.6);
                        sound(target, SoundEvents.FIRECHARGE_USE, 0.7f, 1.2f);
                    }
                }
            }
            case "cinder_burst" -> {
                if (spell && target.isAlive()) Statuses.apply(target, DamageElement.FIRE, 12f * power, attacker);
            }
            case "hollow_echo" -> {
                if (spell && attacker.getRandom().nextFloat() < HOLLOW_ECHO_CHANCE) {
                    float echo = dealt * HOLLOW_ECHO;
                    ServerScheduler.schedule(10, () -> {
                        if (!target.isAlive() || !attacker.isAlive()) return;
                        ArsenalHits.magicHit(attacker, target, echo);
                        burst(target, ParticleTypes.SOUL, 8, 0.4);
                        sound(target, SoundEvents.SOUL_ESCAPE.value(), 1f, 0.8f);
                    });
                }
            }
            case "storm_sigil" -> {
                if (spell && attacker.getRandom().nextFloat() < STORM_SIGIL_CHANCE) ArsenalHits.chainLightning(attacker, target, dealt * STORM_SIGIL);
            }
            case "vanguard_cleave" -> {
                if (melee && bump(attacker, "cleave") % CLEAVE_EVERY == 0) cleave(attacker, target, dealt * CLEAVE);
            }
            case "dragons_blood" -> {
                if (melee) {
                    if (target.isAlive()) Statuses.apply(target, DamageElement.FIRE, 12f * power, attacker);
                    if (attacker.getHealth() < attacker.getMaxHealth() * 0.5f) com.beyondsolo.system.Balance.healRaw(attacker, dealt * DRAGONS_BLOOD_HEAL);
                }
            }
            case "phantom_blade" -> {
                if (melee && consume(attacker, "phantom")) {
                    float echo = dealt * PHANTOM_ECHO;
                    ServerScheduler.schedule(6, () -> {
                        if (!target.isAlive() || !attacker.isAlive()) return;
                        ArsenalHits.magicHit(attacker, target, echo);
                        if (target.isAlive()) Statuses.apply(target, DamageElement.CRIMSON, 15f, attacker);
                        burst(target, ParticleTypes.SQUID_INK, 8, 0.3);
                        sound(target, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 0.6f);
                    });
                }
            }
            default -> {}
        }
    }

    /** Hits everything in front of the attacker but the target. */
    private static void cleave(LivingEntity attacker, LivingEntity target, float damage) {
        Vec3 look = Vec3.directionFromRotation(0, attacker.getYRot());
        DamageSource src = attacker instanceof Player p ? attacker.damageSources().playerAttack(p) : attacker.damageSources().mobAttack(attacker);
        for (LivingEntity e : ArsenalHits.foesAround(attacker, attacker.position(), 3.5)) {
            if (e == target) continue;
            Vec3 to = e.position().subtract(attacker.position()).multiply(1, 0, 1);
            if (to.lengthSqr() > 1e-4 && look.dot(to.normalize()) < 0.2) continue;
            ArsenalHits.proc(() -> e.hurt(src, damage));
        }
        if (attacker.level() instanceof ServerLevel level) {
            Vec3 at = attacker.position().add(look.scale(1.5)).add(0, attacker.getBbHeight() * 0.55, 0);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 2, 0.6, 0.1, 0.6, 0);
        }
        sound(attacker, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.8f);
    }

    /** Every creature near the guardian that is after someone else turns on the guardian. */
    static void taunt(LivingEntity guardian, double radius) {
        for (Mob m : guardian.level().getEntitiesOfClass(Mob.class, guardian.getBoundingBox().inflate(radius),
                m -> m.isAlive() && ArsenalHits.isFoe(guardian, m) && m.getTarget() != guardian)) {
            m.setTarget(guardian);
        }
        CombatFx.play(guardian, CombatFxPayload.Kind.NOVA, ArsenalHits.packNova(0xE0B060, (int) radius));
        sound(guardian, SoundEvents.IRON_GOLEM_ATTACK, 0.7f, 0.7f);
    }

    // ------------------------------------------------------------------ kills

    @SubscribeEvent
    static void died(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide || !(event.getSource().getEntity() instanceof LivingEntity killer) || killer == dead) return;
        ArmorSets.Set set = ArmorSets.fullSet(killer);
        if (set == null || set.perk == null) return;
        switch (set.perk) {
            case "pyre_burst" -> {
                for (LivingEntity e : ArsenalHits.foesAround(killer, dead.position(), 3)) {
                    if (e == dead) continue;
                    ArsenalHits.proc(() -> CombatDamage.deal(e, DamageElement.FIRE, com.beyondsolo.system.Balance.flatDamage(killer, 6f), killer));
                    if (e.isAlive()) Statuses.apply(e, DamageElement.FIRE, 20f * ElementalHits.power(killer), killer);
                }
                burst(dead, ParticleTypes.FLAME, 24, 0.8);
                burst(dead, ParticleTypes.LARGE_SMOKE, 6, 0.6);
                sound(dead, SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.4f);
            }
            case "alphas_howl" -> {
                killer.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 4 * 20, 0, false, true, true));
                killer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 4 * 20, 0, false, true, true));
                sound(killer, SoundEvents.WOLF_HOWL, 0.4f, 1.3f);
            }
            case "empress_venom" -> {
                if (dead.hasEffect(ModEffects.VENOM) || dead.hasEffect(ModEffects.HEAVY_VENOM)) {
                    for (LivingEntity e : ArsenalHits.foesAround(killer, dead.position(), 4)) {
                        if (e != dead) Statuses.apply(e, DamageElement.POISON, 30f * ElementalHits.power(killer), killer);
                    }
                    burst(dead, ParticleTypes.SNEEZE, 20, 1.2);
                    sound(dead, SoundEvents.SPIDER_DEATH, 0.7f, 1.4f);
                }
            }
            case "phantom_blade" -> prime(killer, "phantom", 4 * 20);
            case "warlords_fury" -> {
                int n = addStack(killer, "fury", FURY_MAX, 10 * 20);
                if (killer instanceof ServerPlayer p) {
                    p.displayClientMessage(net.minecraft.network.chat.Component.translatable("perk.beyondsolo.warlords_fury.stacks", n)
                            .withStyle(s -> s.withColor(0xFFB060)), true);
                }
            }
            default -> {}
        }
    }

    // ------------------------------------------------------------------ skill casts

    @SubscribeEvent
    static void cast(SkillEvent.Cast event) {
        ServerPlayer p = event.getPlayer();
        ArmorSets.Set set = ArmorSets.fullSet(p);
        if (set == null || set.perk == null) return;
        String path = event.getSkill().path();
        boolean hasCooldown = event.getSkill().cooldown(event.getLevel()) > 0;
        // a tick later the cast has gone through (or been refused): only a real cast counts
        ServerScheduler.schedule(1, () -> {
            if (!p.isAlive() || (hasCooldown && SkillAPI.get(p).cooldownLeft(path, p.level().getGameTime()) <= 0)) return;
            casted(p, set.perk);
        });
    }

    /** What a perk does when its wearer casts a skill. */
    public static void casted(ServerPlayer p, String perk) {
        switch (perk) {
            case "bone_ward" -> {
                if (!ready(p, "bone_ward", 10 * 20)) return;
                for (Player ally : allies(p, 6)) ally.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 0));
                CombatFx.play(p, CombatFxPayload.Kind.NOVA, ArsenalHits.packNova(0xE8DCC0, 6));
                sound(p, SoundEvents.SKELETON_AMBIENT, 0.5f, 1.6f);
            }
            case "cradle_silk" -> {
                if (!ready(p, "cradle", 8 * 20)) return;
                Player hurt = allies(p, 8).stream().min(Comparator.comparingDouble(a -> a.getHealth() / a.getMaxHealth())).orElse(p);
                hurt.heal(3f);
                hurt.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0));
                burst(hurt, ParticleTypes.ITEM_COBWEB, 8, 0.4);
                burst(hurt, ParticleTypes.HEART, 2, 0.3);
            }
            case "rime_bloom" -> {
                if (!ready(p, "rime_bloom", 6 * 20)) return;
                for (LivingEntity e : ArsenalHits.foesAround(p, p.position(), 5)) ArsenalHits.frostbite(e, p, 1.5f);
                for (Player ally : allies(p, 5)) ally.heal(2f);
                CombatFx.play(p, CombatFxPayload.Kind.NOVA, ArsenalHits.packNova(0xBDEBFF, 5));
                burst(p, ParticleTypes.SNOWFLAKE, 24, 2.0);
                sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.5f);
            }
            case "arcane_cascade" -> {
                int n = addStack(p, "cascade", CASCADE_MAX, 6 * 20);
                if (n >= CASCADE_MAX) {
                    clear(p, "cascade");
                    for (LivingEntity e : ArsenalHits.foesAround(p, p.position(), 4)) ArsenalHits.magicHit(p, e, com.beyondsolo.system.Balance.flatDamage(p, 8f));
                    CombatFx.play(p, CombatFxPayload.Kind.NOVA, ArsenalHits.packNova(0x9B7BFF, 4));
                    sound(p, SoundEvents.ILLUSIONER_CAST_SPELL, 1f, 1.2f);
                }
            }
            case "arcane_overflow" -> {
                int n = addStack(p, "overflow", 4, 20 * 20);
                if (n >= 4) {
                    clear(p, "overflow");
                    for (LivingEntity e : ArsenalHits.foesAround(p, p.position(), 6)) ArsenalHits.magicHit(p, e, com.beyondsolo.system.Balance.flatDamage(p, 10f));
                    CombatFx.play(p, CombatFxPayload.Kind.NOVA, ArsenalHits.packNova(0xFFF3C0, 6));
                    burst(p, ParticleTypes.END_ROD, 30, 2.5);
                    sound(p, SoundEvents.BEACON_ACTIVATE, 1f, 1.8f);
                }
            }
            default -> {}
        }
    }

    // ------------------------------------------------------------------ over time

    @SubscribeEvent
    static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || (p.tickCount + p.getId()) % 20 != 0 || !p.isAlive()) return;
        ArmorSets.Set set = ArmorSets.fullSet(p);
        if (set == null || set.perk == null) return;
        switch (set.perk) {
            case "starbinding" -> {
                if (!ready(p, "starbinding", 8 * 20)) return;
                Player hurt = allies(p, 12).stream().filter(a -> a.getHealth() < a.getMaxHealth())
                        .min(Comparator.comparingDouble(a -> a.getHealth() / a.getMaxHealth())).orElse(null);
                if (hurt == null) {
                    clearCooldown(p, "starbinding");
                    return;
                }
                hurt.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 1));
                line(p, hurt, ParticleTypes.END_ROD);
                sound(hurt, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.8f);
            }
            case "saints_grace" -> {
                Player hurt = allies(p, 12).stream().filter(a -> a.getHealth() < a.getMaxHealth() * 0.35f)
                        .min(Comparator.comparingDouble(a -> a.getHealth() / a.getMaxHealth())).orElse(null);
                if (hurt == null || !ready(p, "saints_grace", 45 * 20)) return;
                com.beyondsolo.system.Balance.healRaw(hurt, hurt.getMaxHealth() * 0.3f);
                hurt.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 0));
                CombatFx.play(hurt, CombatFxPayload.Kind.NOVA, ArsenalHits.packNova(0xFFF0B0, 2));
                burst(hurt, ParticleTypes.END_ROD, 16, 0.6);
                sound(hurt, SoundEvents.BEACON_POWER_SELECT, 0.9f, 1.6f);
            }
            case "overflowing_grace" -> {
                if (!ready(p, "grace", 6 * 20)) return;
                for (Player ally : allies(p, 10)) graceHeal(ally, 2f);
                CombatFx.play(p, CombatFxPayload.Kind.NOVA, ArsenalHits.packNova(0xC8FFB0, 10));
            }
            case "windwalker" -> {
                if (p.isSprinting() && bump(p, "sprint") >= 2) {
                    clear(p, "sprint");
                    prime(p, "windwalker", 3 * 20);
                } else if (!p.isSprinting()) {
                    clear(p, "sprint");
                }
            }
            default -> {}
        }
    }

    private static void clearCooldown(LivingEntity e, String key) {
        State s = STATE.get(e);
        if (s != null) s.until.remove("cd." + key);
    }

    /**
     * Heals; whatever would spill over full health becomes a shield (up to 6). Both grow with the hunter's health pool
     * ({@link com.beyondsolo.system.Balance#legacyScale}), so they keep their share of it.
     */
    public static void graceHeal(LivingEntity e, float amount) {
        float scale = (float) com.beyondsolo.system.Balance.legacyScale(e);
        amount *= scale;
        float cap = GRACE_SHIELD * scale;
        float missing = e.getMaxHealth() - e.getHealth();
        com.beyondsolo.system.Balance.healRaw(e, amount);
        float spill = amount - missing;
        if (spill > 0 && e.getAbsorptionAmount() < cap) e.setAbsorptionAmount(Math.min(cap, e.getAbsorptionAmount() + spill));
        if (e.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY(1.0), e.getZ(), 4, 0.3, 0.4, 0.3, 0);
        }
    }

    // ------------------------------------------------------------------ rolls and arrows

    /** A Combat Roll started (wired in {@link ArmoryEvents}). */
    public static void rolled(ServerPlayer p) {
        ArmorSets.Set set = ArmorSets.fullSet(p);
        if (set == null || set.perk == null) return;
        switch (set.perk) {
            case "pounce" -> prime(p, "pounce", 3 * 20);
            case "phantom_blade" -> prime(p, "phantom", 4 * 20);
            case "windwalker" -> prime(p, "windwalker", 3 * 20);
            default -> {}
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void arrowLoosed(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof AbstractArrow arrow) || arrow.tickCount > 0) return;
        if (!(arrow.getOwner() instanceof ServerPlayer p) || arrow.getPersistentData().getBoolean("beyondsolo.wind")) return;
        ArmorSets.Set set = ArmorSets.fullSet(p);
        if (set == null || !"windwalker".equals(set.perk) || !consume(p, "windwalker")) return;
        arrow.getPersistentData().putBoolean("beyondsolo.wind", true);
        arrow.setBaseDamage(arrow.getBaseDamage() * (1f + WINDWALKER_ARROW));
        arrow.setCritArrow(true);
        sound(p, SoundEvents.BREEZE_SHOOT, 0.8f, 1.4f);
    }

    // ------------------------------------------------------------------ effects

    static void burst(LivingEntity at, ParticleOptions particle, int count, double spread) {
        if (at.level() instanceof ServerLevel level) {
            level.sendParticles(particle, at.getX(), at.getY(0.6), at.getZ(), count, spread, spread * 0.8, spread, 0.02);
        }
    }

    static void line(LivingEntity from, LivingEntity to, ParticleOptions particle) {
        if (!(from.level() instanceof ServerLevel level)) return;
        Vec3 a = from.position().add(0, from.getBbHeight() * 0.6, 0);
        Vec3 b = to.position().add(0, to.getBbHeight() * 0.6, 0);
        int n = (int) Math.min(24, a.distanceTo(b) * 2);
        for (int i = 0; i <= n; i++) {
            Vec3 at = a.lerp(b, i / (double) Math.max(1, n));
            level.sendParticles(particle, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }

    static void sound(LivingEntity at, SoundEvent sound, float volume, float pitch) {
        at.level().playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    private SetPerks() {}
}
