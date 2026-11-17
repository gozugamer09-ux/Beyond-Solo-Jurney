package com.beyondsolo.enemy;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.network.EnemyTraitsPayload;
import com.beyondsolo.skill.SkillElement;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.util.ServerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * What affinities, variants and affixes do in a fight: elemental strengths and weaknesses, affinity hits, and the
 * affixes (vampiric, frenzied, volatile, splitting, warded, thorned). Works for any creature that carries
 * {@link EnemyTraits}, vanilla or not, bosses included.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class EnemyHooks {
    private static final String FRENZY = "beyondsolo_frenzy";
    private static final String SPLIT = "beyondsolo_split";

    /**
     * How much a skill hit of {@code element} does to {@code target}: what the creature is weak to or shrugs off
     * ({@link ElementalProfile}: its kind, its affinity, a boss's kind), less for a warded creature. Players take
     * every element alike. Called by the skills' hit code ({@code SkillCombat.strike}) and weapon infusions.
     */
    public static float skillMultiplier(LivingEntity target, @Nullable SkillElement element) {
        float k = target instanceof Player ? 1f : ElementalProfile.of(target).multiplier(element);
        if (EnemyTraits.has(target) && EnemyTraits.of(target).has(Affix.WARDED)) k *= 0.67f;
        return k;
    }

    /** The rating a skill hit of {@code element} lands with on {@code target} (neutral on players). */
    public static ElementalProfile.Rating rating(LivingEntity target, @Nullable SkillElement element) {
        return target instanceof Player ? ElementalProfile.Rating.NEUTRAL : ElementalProfile.of(target).rating(element);
    }

    /** A roster creature's affinity mark on whoever it hits (power scales with its rank). */
    public static void affinityHit(LivingEntity attacker, LivingEntity victim, float scale) {
        if (!EnemyTraits.has(attacker)) return;
        EnemyTraits t = EnemyTraits.of(attacker);
        if (t.affinity() == Affinity.NONE) return;
        int tier = attacker instanceof Mob m && Enemies.rankOf(m) != null ? Enemies.rankOf(m).tier() : 0;
        t.affinity().onHit(attacker, victim, (10 + 3 * tier) * scale * (t.variant().special() ? 1.4f : 1f));
    }

    @Nullable
    private static com.beyondsolo.combat.DamageElement elementOf(DamageSource source) {
        ResourceLocation key = source.typeHolder().unwrapKey().map(k -> k.location()).orElse(null);
        if (key == null || !key.getNamespace().equals(BeyondSolo.MOD_ID)) return null;
        return com.beyondsolo.combat.DamageElement.get(key);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void incoming(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        DamageSource source = event.getSource();
        com.beyondsolo.combat.DamageElement element = elementOf(source);
        if (element != null && !(victim instanceof Player)) {
            // element damage (burns and venom ticking, crimson and blood bursts, weapon procs): burns and venom hurt as
            // the creature takes fire and nature; bursts take a share of health, so only an affinity bends them
            SkillElement as = Affinity.skillElementOf(element);
            float k = element == com.beyondsolo.combat.DamageElement.FIRE || element == com.beyondsolo.combat.DamageElement.POISON
                    ? ElementalProfile.of(victim).multiplier(as)
                    : EnemyTraits.of(victim).affinity().multiplier(as);
            if (EnemyTraits.has(victim) && EnemyTraits.of(victim).has(Affix.WARDED)) k *= 0.67f;
            // and the cave's weather feeds or dulls it (depths.WeatherService; still air outside the caves)
            k *= com.beyondsolo.depths.WeatherService.factor(victim, as);
            if (k != 1f) event.setAmount(event.getAmount() * k);
        }
        if (EnemyTraits.has(victim)) {
            EnemyTraits t = EnemyTraits.of(victim);
            if (element == null && t.has(Affix.WARDED) && source.is(DamageTypeTags.WITCH_RESISTANT_TO)) {
                event.setAmount(event.getAmount() * 0.67f);
            }
            // thorns: a melee strike cuts the striker
            if (t.has(Affix.THORNED) && source.getDirectEntity() instanceof LivingEntity striker && source.getDirectEntity() == source.getEntity()
                    && striker != victim && event.getAmount() > 0) {
                float back = 1 + event.getAmount() * 0.15f;
                ServerScheduler.schedule(1, () -> {
                    if (striker.isAlive()) striker.hurt(victim.damageSources().thorns(victim), back);
                });
            }
        }
    }

    @SubscribeEvent
    static void damaged(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || event.getNewDamage() <= 0) return;
        Entity src = event.getSource().getEntity();
        // a roster creature's melee hit carries its affinity (bolts and bursts apply their own)
        if (src instanceof LivingEntity attacker && event.getSource().getDirectEntity() == attacker && EnemyTraits.has(attacker)
                && !(attacker instanceof com.beyondsolo.gate.GateBossEntity)) {
            EnemyTraits t = EnemyTraits.of(attacker);
            affinityHit(attacker, victim, 1f);
            if (t.has(Affix.VAMPIRIC)) {
                attacker.heal(event.getNewDamage() * 0.25f);
                if (attacker.level() instanceof ServerLevel s) s.sendParticles(ParticleTypes.DAMAGE_INDICATOR, attacker.getX(), attacker.getY() + attacker.getBbHeight(), attacker.getZ(), 3, 0.3, 0.2, 0.3, 0.05);
            }
        }
        // frenzy below half health
        if (EnemyTraits.has(victim) && EnemyTraits.of(victim).has(Affix.FRENZIED) && !victim.getPersistentData().getBoolean(FRENZY)
                && victim.getHealth() < victim.getMaxHealth() * 0.5f && victim.isAlive()) {
            victim.getPersistentData().putBoolean(FRENZY, true);
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MobEffectInstance.INFINITE_DURATION, 1, false, true));
            victim.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, MobEffectInstance.INFINITE_DURATION, 0, false, true));
            victim.level().playSound(null, victim.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.8f, 1.5f);
            if (victim.level() instanceof ServerLevel s) EnemyFx.burst(s, victim.position().add(0, victim.getBbHeight() * 0.6, 0), 0xFF4040, 1.6f);
        }
    }

    @SubscribeEvent
    static void died(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || !EnemyTraits.has(victim) || victim instanceof com.beyondsolo.gate.GateBossEntity) return;
        EnemyTraits t = EnemyTraits.of(victim);
        Vec3 at = victim.position();
        if (t.has(Affix.VOLATILE)) {
            int color = t.affinity() == Affinity.NONE ? 0xFF8030 : t.affinity().color;
            EnemyFx.telegraphCircle(level, at, 3f, 24, color);
            float damage = 4 + 2f * (victim instanceof Mob m && Enemies.rankOf(m) != null ? Enemies.rankOf(m).tier() : 0);
            ServerScheduler.schedule(24, () -> {
                EnemyFx.burst(level, at.add(0, 0.6, 0), color, 2.6f);
                level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 2, 0.5, 0.3, 0.5, 0);
                level.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1f, 1.2f);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(3), e -> !(e instanceof Monster) && e.isAlive())) {
                    if (e.position().distanceTo(at) <= 3.2) {
                        e.hurt(level.damageSources().explosion(null, null), damage);
                        if (t.affinity() != Affinity.NONE && victim.isAlive() == false) t.affinity().onHit(victim, e, 20);
                    }
                }
            });
        }
        if (t.has(Affix.SPLITTING) && !victim.getPersistentData().getBoolean(SPLIT) && victim instanceof Mob mob) {
            PlayerRank rank = Enemies.rankOf(mob);
            int theme = victim.getPersistentData().contains(com.beyondsolo.gate.GateService.MOB_TAG)
                    ? victim.getPersistentData().getInt(com.beyondsolo.gate.GateService.MOB_TAG) : -1;
            EntityType<?> type = victim.getType();
            for (int i = 0; i < 2; i++) {
                double a = level.random.nextDouble() * Math.PI * 2;
                BlockPos p = BlockPos.containing(at.x + Math.cos(a) * 0.8, at.y, at.z + Math.sin(a) * 0.8);
                EnemyTraits child = new EnemyTraits(t.affinity(), Variant.NORMAL, 0);
                Mob m = Enemies.spawn(level, rank == null ? PlayerRank.E : rank, type, p, child, theme);
                if (m != null) {
                    m.getPersistentData().putBoolean(SPLIT, true);
                    var scale = m.getAttribute(Attributes.SCALE);
                    if (scale != null) scale.addPermanentModifier(new AttributeModifier(BeyondSolo.id("split"), -0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
                    var hp = m.getAttribute(Attributes.MAX_HEALTH);
                    if (hp != null) hp.addPermanentModifier(new AttributeModifier(BeyondSolo.id("split"), -0.6, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                    m.setHealth(m.getMaxHealth());
                    if (m instanceof Mob mm && victim.getLastHurtByMob() != null) mm.setTarget(victim.getLastHurtByMob());
                }
            }
        }
    }

    @SubscribeEvent
    static void xp(LivingExperienceDropEvent event) {
        LivingEntity e = event.getEntity();
        if (!EnemyTraits.has(e) || e instanceof com.beyondsolo.gate.GateBossEntity) return;
        event.setDroppedExperience(Math.round(event.getDroppedExperience() * EnemyTraits.of(e).variant().reward));
    }

    /** Elites and champions drop a little more: emeralds, now and then a remedy or a graded weapon. */
    @SubscribeEvent
    static void drops(LivingDropsEvent event) {
        LivingEntity e = event.getEntity();
        if (!(e.level() instanceof ServerLevel level) || !EnemyTraits.has(e) || !(e instanceof Mob mob) || e instanceof com.beyondsolo.gate.GateBossEntity) return;
        Variant v = EnemyTraits.of(e).variant();
        if (!v.special()) return;
        PlayerRank rank = Enemies.rankOf(mob);
        int tier = rank == null ? 0 : rank.tier();
        var r = level.random;
        add(event, e, new ItemStack(Items.EMERALD, 1 + r.nextInt(2) + (v == Variant.CHAMPION ? 2 + tier : 0)));
        if (r.nextFloat() < (v == Variant.CHAMPION ? 0.35f : 0.12f)) add(event, e, new ItemStack(com.beyondsolo.registry.ModItems.CLOTTING_TONIC.get()));
        if (r.nextFloat() < (v == Variant.CHAMPION ? 0.25f : 0.06f)) add(event, e, com.beyondsolo.gate.GateLoot.weapon(tier, r));
    }

    private static void add(LivingDropsEvent event, LivingEntity e, ItemStack stack) {
        event.getDrops().add(new ItemEntity(e.level(), e.getX(), e.getY() + 0.5, e.getZ(), stack));
    }

    @SubscribeEvent
    static void startTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof LivingEntity e && EnemyTraits.has(e)) {
            EnemyTraits t = EnemyTraits.of(e);
            if (t.notable()) PacketDistributor.sendToPlayer(player, new EnemyTraitsPayload(e.getId(), t.affinity().ordinal(), t.variant().ordinal(), t.affixMask()));
        }
    }

    /** Tells everyone who can see it about a creature's traits (when they are set after it appeared). */
    public static void broadcast(LivingEntity e) {
        if (e.level().isClientSide || !EnemyTraits.has(e)) return;
        EnemyTraits t = EnemyTraits.of(e);
        PacketDistributor.sendToPlayersTrackingEntity(e, new EnemyTraitsPayload(e.getId(), t.affinity().ordinal(), t.variant().ordinal(), t.affixMask()));
    }

    private EnemyHooks() {}
}
