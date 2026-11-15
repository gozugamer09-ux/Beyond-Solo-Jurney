package com.beyondsolo.skill;

import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.combat.Statuses;
import com.beyondsolo.network.SkillFxPayload;
import com.beyondsolo.network.SkillMomentPayload;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Combos and element reactions.
 *
 * <p><b>Combos.</b> Casting a different skill within {@link #COMBO_WINDOW} ticks of the last one builds a chain
 * (up to {@link #COMBO_MAX}); every link adds {@link #COMBO_STEP} to skill damage. Casting the same skill twice
 * in a row keeps the chain where it is; letting the window run out drops it.</p>
 *
 * <p><b>Reactions.</b> A skill hit leaves its element on the creature for {@link #AURA_TICKS}. A hit with another
 * element that reacts with it ({@link SkillReaction}) sets the reaction off and uses the element up; otherwise
 * the new element replaces the old one. Physical hits leave nothing and react with nothing.</p>
 *
 * <p>Server thread only; nothing here is saved.</p>
 */
public final class SkillCombat {
    public static final int COMBO_WINDOW = 100;
    public static final int COMBO_MAX = 6;
    public static final float COMBO_STEP = 0.08f;
    public static final int AURA_TICKS = 160;

    private static final class Chain {
        int count;
        String last = "";
        long until;
    }

    private record Aura(SkillElement element, long until) {}

    private static final Map<UUID, Chain> CHAINS = new HashMap<>();
    private static final Map<UUID, Chain> BEFORE = new HashMap<>();
    private static final Map<LivingEntity, Aura> AURAS = new WeakHashMap<>();

    // ================================================================ combos

    /** The chain as it stands (0 when it has run out). */
    public static int combo(Player player) {
        Chain c = CHAINS.get(player.getUUID());
        return c == null || c.until <= player.level().getGameTime() ? 0 : c.count;
    }

    /** Skill damage multiplier from the chain: 1 for the first skill, more for every link after it. */
    public static float comboMultiplier(Player player) {
        return 1f + COMBO_STEP * Math.max(0, combo(player) - 1);
    }

    /** A cast is starting: move the chain on. {@link #undo} takes it back if the cast finds no target. */
    static void begin(ServerPlayer player, Skill skill) {
        long now = player.level().getGameTime();
        Chain c = CHAINS.computeIfAbsent(player.getUUID(), k -> new Chain());
        Chain copy = new Chain();
        copy.count = c.count;
        copy.last = c.last;
        copy.until = c.until;
        BEFORE.put(player.getUUID(), copy);
        if (c.until <= now) c.count = 1;
        else if (!c.last.equals(skill.path())) c.count = Math.min(COMBO_MAX, c.count + 1);
        c.last = skill.path();
        c.until = now + COMBO_WINDOW;
    }

    static void undo(ServerPlayer player) {
        Chain old = BEFORE.remove(player.getUUID());
        if (old != null) CHAINS.put(player.getUUID(), old);
    }

    /** The cast went off: show the chain. */
    static void commit(ServerPlayer player, Skill skill) {
        BEFORE.remove(player.getUUID());
        int count = combo(player);
        if (count >= 2) {
            SkillAPI.moment(player, SkillMomentPayload.COMBO, skill, count);
            NeoForge.EVENT_BUS.post(new SkillEvent.Combo(player, skill, count));
        }
    }

    public static void clear(Player player) {
        CHAINS.remove(player.getUUID());
        BEFORE.remove(player.getUUID());
    }

    // ================================================================ elements

    /** The element a creature is carrying right now, if any. */
    public static @Nullable SkillElement aura(LivingEntity target) {
        Aura a = AURAS.get(target);
        if (a == null) return null;
        if (a.until <= target.level().getGameTime()) {
            AURAS.remove(target);
            return null;
        }
        return a.element;
    }

    /** Leaves an element on a creature without a hit (rain, zones, tests). */
    public static void setAura(LivingEntity target, SkillElement element) {
        if (element == SkillElement.PHYSICAL) return;
        AURAS.put(target, new Aura(element, target.level().getGameTime() + AURA_TICKS));
        SkillFx.play(target, SkillFxPayload.Kind.ELEMENT, element.color(), target.position(), target.getBbHeight(), AURA_TICKS);
    }

    /**
     * A skill hit of this element is about to land: sets off a reaction if the creature carries an element that
     * reacts, otherwise leaves this one on it. Returns the damage to deal.
     */
    public static float strike(ServerPlayer player, LivingEntity target, SkillElement element, float damage) {
        // gate creatures' elemental strengths and weaknesses (enemy.Affinity) and warded affixes
        damage *= com.beyondsolo.enemy.EnemyHooks.skillMultiplier(target, element);
        if (element == SkillElement.PHYSICAL) return damage;
        SkillElement carried = aura(target);
        SkillReaction reaction = carried == null ? null : SkillReaction.of(carried, element);
        if (reaction == null) {
            if (carried != element) setAura(target, element);
            else AURAS.put(target, new Aura(element, target.level().getGameTime() + AURA_TICKS));
            return damage;
        }
        AURAS.remove(target);
        float attuned = 1f + 0.05f * SkillAPI.level(player, Skills.ELEMENTAL_ATTUNEMENT);
        float boosted = damage * (1f + reaction.bonus() * attuned);
        react(player, target, reaction, damage * attuned);
        SkillFx.play(target, SkillFxPayload.Kind.REACTION, reaction.color(), target.position().add(0, target.getBbHeight() * 0.6, 0),
                2.2f, reaction.ordinal());
        NeoForge.EVENT_BUS.post(new SkillEvent.Reaction(player, target, reaction));
        return boosted;
    }

    /** The side effects of a reaction. {@code power} is the damage of the hit that set it off. */
    private static void react(ServerPlayer p, LivingEntity target, SkillReaction reaction, float power) {
        ServerLevel level = p.serverLevel();
        Vec3 at = target.position().add(0, target.getBbHeight() * 0.5, 0);
        switch (reaction) {
            case SURGE -> {
                for (LivingEntity e : near(p, target, 3.5)) {
                    splash(p, e, power * 0.6f);
                    stun(e, 20);
                }
                stun(target, 20);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 40, 1.2, 0.8, 1.2, 0.4);
                level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 30, 1.0, 0.5, 1.0, 0.2);
                sound(level, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6f, 1.5f);
                sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.5f, 1.4f);
            }
            case FIRESTORM -> {
                Statuses.apply(target, DamageElement.FIRE, 50, p);
                target.igniteForSeconds(4);
                for (LivingEntity e : near(p, target, 3.5)) {
                    Statuses.apply(e, DamageElement.FIRE, 35, p);
                    e.igniteForSeconds(3);
                    splash(p, e, power * 0.3f);
                }
                level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 60, 1.4, 1.0, 1.4, 0.08);
                sound(level, at, SoundEvents.FIRECHARGE_USE, 1f, 0.6f);
            }
            case STEAM -> {
                for (LivingEntity e : nearAnd(p, target, 3)) {
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                    e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                }
                target.clearFire();
                level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 40, 1.2, 0.8, 1.2, 0.03);
                sound(level, at, SoundEvents.FIRE_EXTINGUISH, 1f, 0.9f);
            }
            case WILDFIRE -> {
                Statuses.apply(target, DamageElement.FIRE, 70, p);
                target.igniteForSeconds(6);
                level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 30, 0.5, 0.8, 0.5, 0.04);
                sound(level, at, SoundEvents.BLAZE_SHOOT, 0.8f, 0.8f);
            }
            case MAGMA -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2));
                target.igniteForSeconds(4);
                level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 16, 0.5, 0.4, 0.5, 0);
                sound(level, at, SoundEvents.LAVA_POP, 1f, 0.7f);
            }
            case FROST -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 4));
                target.setTicksFrozen(target.getTicksRequiredToFreeze() + 80);
                target.clearFire();
                level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 40, 0.6, 0.8, 0.6, 0.03);
                sound(level, at, SoundEvents.GLASS_BREAK, 0.8f, 1.5f);
            }
            case OVERGROWTH -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 9));
                p.heal(2);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y, at.z, 24, 0.6, 0.8, 0.6, 0);
                sound(level, at, SoundEvents.BIG_DRIPLEAF_TILT_UP, 1f, 0.7f);
            }
            case ECLIPSE -> {
                level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 24, 0.4, 0.6, 0.4, 0.1);
                sound(level, at, SoundEvents.WARDEN_SONIC_BOOM, 0.4f, 1.6f);
            }
            case TEMPEST -> {
                int jumps = 0;
                for (LivingEntity e : near(p, target, 6)) {
                    if (jumps++ >= 3) break;
                    splash(p, e, power * 0.5f);
                    SkillFx.play(target, SkillFxPayload.Kind.BOLT, reaction.color(), e.position().add(0, e.getBbHeight() * 0.5, 0), 0.3f, 0);
                }
                sound(level, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.5f, 1.8f);
            }
            case SANDSTORM -> {
                for (LivingEntity e : nearAnd(p, target, 4)) {
                    e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
                }
                level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 50, 1.6, 0.8, 1.6, 0.05);
                sound(level, at, SoundEvents.SAND_BREAK, 1f, 0.6f);
            }
            case OVERLOAD -> {
                for (LivingEntity e : nearAnd(p, target, 3)) {
                    Vec3 away = e.position().subtract(p.position()).multiply(1, 0, 1);
                    if (away.lengthSqr() > 1.0e-4) {
                        away = away.normalize();
                        e.setDeltaMovement(away.x * 0.9, 0.45, away.z * 0.9);
                        e.hurtMarked = true;
                    }
                    if (e != target) splash(p, e, power * 0.4f);
                }
                level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, 0.4, 0.2, 0.4, 0);
                sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.7f, 1.2f);
            }
            case BLIGHT -> {
                Statuses.apply(target, DamageElement.POISON, 40, p);
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
                level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, at.x, at.y, at.z, 30, 0.5, 0.8, 0.5, 0);
                sound(level, at, SoundEvents.SCULK_BLOCK_SPREAD, 1f, 0.7f);
            }
            case SHATTER -> {
                stun(target, 30);
                level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 30, 0.5, 0.8, 0.5, 0.4);
                sound(level, at, SoundEvents.STONE_BREAK, 1f, 0.6f);
                sound(level, at, SoundEvents.ANVIL_LAND, 0.4f, 1.4f);
            }
            case RADIANCE -> {
                for (Player ally : level.getEntitiesOfClass(Player.class, new AABB(at, at).inflate(5), Player::isAlive)) ally.heal(3);
                p.heal(3);
                level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 30, 0.6, 0.8, 0.6, 0.08);
                sound(level, at, SoundEvents.BEACON_POWER_SELECT, 0.6f, 1.8f);
            }
            case RESONANCE -> {
                SystemAPI.restoreMana(p, 6);
                level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y + 0.5, at.z, 40, 0.5, 0.8, 0.5, 0.6);
                sound(level, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 1.2f);
            }
        }
    }

    /** Other creatures near the target that the player may hit. */
    private static List<LivingEntity> near(ServerPlayer p, LivingEntity target, double radius) {
        return p.level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(radius),
                e -> e != target && SkillActions.canHit(p, e));
    }

    private static List<LivingEntity> nearAnd(ServerPlayer p, LivingEntity target, double radius) {
        List<LivingEntity> list = new java.util.ArrayList<>(near(p, target, radius));
        list.add(target);
        return list;
    }

    /** Reaction damage to bystanders: carries no element, so reactions never set each other off. */
    private static void splash(ServerPlayer p, LivingEntity e, float damage) {
        e.invulnerableTime = 0;
        e.hurt(p.damageSources().indirectMagic(p, p), damage);
    }

    static void stun(LivingEntity e, int ticks) {
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 9, false, false));
        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 3, false, false));
        e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
        if (e instanceof net.minecraft.world.entity.Mob m) m.getNavigation().stop();
    }

    private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private SkillCombat() {}
}
