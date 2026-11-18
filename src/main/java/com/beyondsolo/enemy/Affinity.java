package com.beyondsolo.enemy;

import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.combat.Statuses;
import com.beyondsolo.skill.SkillElement;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * The element a gate creature is steeped in. It tints the creature (its texture, its aura), decides what its hits
 * leave behind, and sets its elemental strength and weakness: skills of its own element do half damage, skills of
 * the element it is weak to do half again as much. {@link #NONE} is an ordinary creature.
 *
 * <p>Hits: flame, venom, blood and shade add the matching buildup ({@link DamageElement}); frost slows; storm
 * sparks (a little extra damage that ignores armour and a short stagger).
 */
public enum Affinity {
    NONE("plain", 0xCDD2E1, null, null, null),
    FLAME("flame", 0xFF8030, SkillElement.FIRE, SkillElement.WATER, DamageElement.FIRE),
    FROST("frost", 0x78CDFF, SkillElement.WATER, SkillElement.FIRE, null),
    VENOM("venom", 0x8CE850, SkillElement.NATURE, SkillElement.FIRE, DamageElement.POISON),
    STORM("storm", 0xB2A8FF, SkillElement.LIGHTNING, SkillElement.EARTH, null),
    SHADE("shade", 0xA86CF0, SkillElement.SHADOW, SkillElement.LIGHT, DamageElement.CRIMSON),
    BLOOD("blood", 0xE42C38, SkillElement.SHADOW, SkillElement.LIGHT, DamageElement.BLOOD);

    /** Texture suffix (textures/entity/&lt;monster&gt;_&lt;texture&gt;.png). */
    public final String texture;
    public final int color;
    @Nullable public final SkillElement element;
    @Nullable public final SkillElement weakness;
    @Nullable public final DamageElement buildup;

    Affinity(String texture, int color, @Nullable SkillElement element, @Nullable SkillElement weakness, @Nullable DamageElement buildup) {
        this.texture = texture;
        this.color = color;
        this.element = element;
        this.weakness = weakness;
        this.buildup = buildup;
    }

    public static Affinity byIndex(int i) {
        Affinity[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, i))];
    }

    /** How much a skill of this element hurts a creature of this affinity. */
    public float multiplier(@Nullable SkillElement skill) {
        if (skill == null || this == NONE) return 1f;
        if (skill == weakness) return 1.5f;
        if (skill == element) return 0.5f;
        return 1f;
    }

    /**
     * True while a storm spark is landing. The spark is a hit of its own, and everything that answers a creature's
     * hit (its affinity mark above all) must leave it alone: a spark that set off another spark used to chain without
     * end and kill whatever it touched in one blow, whatever its health (playtest 0.1.6).
     */
    private static int sparkDepth;

    public static boolean inSpark() {
        return sparkDepth > 0;
    }

    /** What this creature's hit leaves on its target, on top of the hit itself. {@code power} scales the buildup. */
    public void onHit(LivingEntity attacker, LivingEntity target, float power) {
        if (!target.isAlive()) return;
        switch (this) {
            case FROST -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, power > 20 ? 1 : 0), attacker);
                target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze() + 40, target.getTicksFrozen() + 40));
            }
            case STORM -> {
                // one spark per hit, never a spark off a spark
                if (inSpark()) return;
                sparkDepth++;
                try {
                    target.invulnerableTime = 0;
                    target.hurt(attacker.damageSources().indirectMagic(attacker, attacker), 1f + power * 0.06f);
                } finally {
                    sparkDepth--;
                }
                if (target.isAlive()) target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 3), attacker);
            }
            case NONE -> {
            }
            default -> {
                if (buildup != null) Statuses.apply(target, buildup, power, attacker);
            }
        }
    }

    /** "Frostbound", "Venomous" and so on: put in front of a creature's name. */
    public Component adjective() {
        return Component.translatable("enemy.beyondsolo.affinity." + name().toLowerCase());
    }

    /** The affinity a hit's damage element belongs to, if any (for weapon hits). */
    @Nullable
    public static SkillElement skillElementOf(@Nullable DamageElement e) {
        if (e == null) return null;
        if (e == DamageElement.FIRE) return SkillElement.FIRE;
        if (e == DamageElement.POISON) return SkillElement.NATURE;
        if (e == DamageElement.CRIMSON || e == DamageElement.BLOOD) return SkillElement.SHADOW;
        return null;
    }

    static boolean is(Entity e, Affinity a) {
        return e instanceof LivingEntity le && EnemyTraits.of(le).affinity() == a;
    }
}
