package com.beyondsolo.combat;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.network.CombatFxPayload;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * The element buildups and what happens when each one fills up. Blood lives in {@link Buildups#BLOOD} (core)
 * and pops through {@link #bloodPop}. Every buildup works on players and mobs alike.
 */
public final class Statuses {
    /** Fire: fills from fire weapons, burning and fiery mobs. Full: Deep Burn. */
    public static final BuildupType BURN = Buildups.register(new BuildupType(BeyondSolo.id("burn"), DamageElement.FIRE,
            100f, 8f, true, Statuses::ignite));
    /** Poison: fills fast, and every time it fills it adds one Venom stack. */
    public static final BuildupType VENOM = Buildups.register(new BuildupType(BeyondSolo.id("venom"), DamageElement.POISON,
            35f, 5f, true, (target, source) -> addVenomStack(target, source)));
    /** Crimson: full = a burst of crimson damage and the Crimson Brand. */
    public static final BuildupType CRIMSON = Buildups.register(new BuildupType(BeyondSolo.id("crimson"), DamageElement.CRIMSON,
            100f, 6f, true, Statuses::crimsonBurst));

    public static final int VENOM_TICKS = 240;
    public static final int HEAVY_VENOM_TICKS = 600;
    public static final int BRAND_TICKS = 200;
    /** Venom stacks that turn into Heavy Venom. */
    public static final int HEAVY_AT = 5;

    /** Makes sure this class (and so its buildup types) is loaded. */
    public static void init() {}

    /** The buildup an element fills, or null for physical. */
    public static @Nullable BuildupType buildupFor(DamageElement element) {
        if (element == DamageElement.FIRE) return BURN;
        if (element == DamageElement.POISON) return VENOM;
        if (element == DamageElement.CRIMSON) return CRIMSON;
        if (element == DamageElement.BLOOD) return Buildups.BLOOD;
        return null;
    }

    /** Adds element buildup to a target (resistances apply inside {@link Buildups#add}). */
    public static void apply(LivingEntity target, DamageElement element, float amount, @Nullable Entity source) {
        BuildupType type = buildupFor(element);
        if (type != null && amount > 0) Buildups.add(target, type, amount, source);
    }

    static void ignite(LivingEntity target, @Nullable Entity source) {
        boolean fresh = !target.hasEffect(ModEffects.DEEP_BURN);
        target.addEffect(new MobEffectInstance(ModEffects.DEEP_BURN, MobEffectInstance.INFINITE_DURATION, 0, false, true, true), source);
        target.igniteForSeconds(3);
        CombatDamage.deal(target, DamageElement.FIRE, com.beyondsolo.system.Balance.flatDamage(source, 2f), source);
        if (fresh) {
            target.level().playSound(null, target.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 0.9f, 0.7f);
            CombatFx.play(target, CombatFxPayload.Kind.IGNITE, 0);
        }
    }

    public static void addVenomStack(LivingEntity target, @Nullable Entity source) {
        if (target.hasEffect(ModEffects.HEAVY_VENOM)) {
            // Already as bad as it gets: keep it going.
            applyHeavyVenom(target, source);
            return;
        }
        MobEffectInstance current = target.getEffect(ModEffects.VENOM);
        int stacks = current == null ? 1 : current.getAmplifier() + 2;
        if (stacks >= HEAVY_AT) {
            target.removeEffect(ModEffects.VENOM);
            applyHeavyVenom(target, source);
            return;
        }
        target.addEffect(new MobEffectInstance(ModEffects.VENOM, VENOM_TICKS, stacks - 1, false, true, true), source);
        target.level().playSound(null, target.blockPosition(), SoundEvents.SPIDER_HURT, SoundSource.HOSTILE, 0.5f, 1.6f + stacks * 0.1f);
        CombatFx.play(target, CombatFxPayload.Kind.VENOM_STACK, stacks);
    }

    /** Heavy (special) poison: bosses and the strongest weapons can apply it directly. */
    public static void applyHeavyVenom(LivingEntity target, @Nullable Entity source) {
        boolean fresh = !target.hasEffect(ModEffects.HEAVY_VENOM);
        target.addEffect(new MobEffectInstance(ModEffects.HEAVY_VENOM, HEAVY_VENOM_TICKS, 0, false, true, true), source);
        if (fresh) {
            target.level().playSound(null, target.blockPosition(), SoundEvents.WITCH_DRINK, SoundSource.HOSTILE, 1f, 0.5f);
            CombatFx.play(target, CombatFxPayload.Kind.HEAVY_VENOM, 0);
        }
    }

    static void crimsonBurst(LivingEntity target, @Nullable Entity source) {
        CombatDamage.deal(target, DamageElement.CRIMSON, Math.max(2f, target.getMaxHealth() * 0.08f * com.beyondsolo.system.Balance.dotShare(target)), source);
        target.addEffect(new MobEffectInstance(ModEffects.CRIMSON_BRAND, BRAND_TICKS, 0, false, true, true), source);
        target.level().playSound(null, target.blockPosition(), SoundEvents.WITHER_HURT, SoundSource.HOSTILE, 0.6f, 1.5f);
        CombatFx.play(target, CombatFxPayload.Kind.CRIMSON_BURST, 0);
    }

    /** The share of max health a blood pop takes. */
    public static final float POP_SHARE = 0.10f;
    /** ...from a boss, an elite or a champion: at most this (in the 0.1.0 playtest pops took 15-20% off a boss). */
    public static final float TOUGH_POP_SHARE = 0.05f;

    /** Bosses (a gate's boss, the Arbiter, the boss tags) and elite or champion creatures: a pop takes less from them. */
    public static boolean tough(LivingEntity e) {
        return e instanceof com.beyondsolo.gate.DungeonBoss
                || e instanceof com.beyondsolo.worldboss.ColossalBoss
                || e instanceof com.beyondsolo.duel.DuelistEntity
                || e.getType().is(net.neoforged.neoforge.common.Tags.EntityTypes.BOSSES)
                || e.getType().is(com.beyondsolo.shadow.ShadowModule.BOSSES)
                || com.beyondsolo.enemy.EnemyTraits.of(e).variant().special();
    }

    /**
     * The most one blood pop takes from {@code target}: 10% of its max health, 5% from a boss or an elite. It is a fixed
     * share, so the bonuses a hit would get (marks, brands, buffs, set perks) never push it past this
     * ({@link ElementalHits} caps it at the end of the damage).
     */
    public static float popCap(LivingEntity target) {
        // a great threat or a duelist: the share of a share (Balance.dotShare)
        float share = com.beyondsolo.system.Balance.dotShare(target);
        if (share < 1f) return target.getMaxHealth() * POP_SHARE * share;
        return target.getMaxHealth() * (tough(target) ? TOUGH_POP_SHARE : POP_SHARE);
    }

    /** Blood pop: a fixed share of max health ({@link #popCap}) that armor, enchantments and Resistance can't soften. Players too. */
    static void bloodPop(LivingEntity target, @Nullable Entity source) {
        CombatDamage.deal(target, DamageElement.BLOOD, popCap(target), source);
        target.level().playSound(null, target.blockPosition(), SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, SoundSource.HOSTILE, 1f, 0.6f);
        target.level().playSound(null, target.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 0.8f, 0.5f);
        CombatFx.play(target, CombatFxPayload.Kind.BLOOD_POP, 0);
    }

    private Statuses() {}
}
