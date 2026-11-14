package com.beyondsolo.skill;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * What happens when a skill's element lands on a creature still carrying another element. The pairs work both
 * ways round. {@code bonus} is the extra damage on the hit that sets it off (0.5 = +50%); the rest of each
 * reaction is in {@link SkillCombat}.
 */
public enum SkillReaction {
    /** Lightning through water: bursts and stuns everything close. */
    SURGE(SkillElement.WATER, SkillElement.LIGHTNING, 0.5f, 0x8FD8FF),
    /** Air feeds fire: burns hotter and spreads. */
    FIRESTORM(SkillElement.AIR, SkillElement.FIRE, 0.6f, 0xFFA040),
    /** Fire on water: a scalding cloud that blinds and weakens. */
    STEAM(SkillElement.FIRE, SkillElement.WATER, 0.3f, 0xE0F0FF),
    /** Fire in the green: a deep, lasting burn. */
    WILDFIRE(SkillElement.FIRE, SkillElement.NATURE, 0.4f, 0xFFC040),
    /** Fire and stone: molten rock clings and slows. */
    MAGMA(SkillElement.FIRE, SkillElement.EARTH, 0.4f, 0xFF6A2A),
    /** Wind over water: frozen in place. */
    FROST(SkillElement.WATER, SkillElement.AIR, 0.2f, 0xBFEFFF),
    /** Water on growth: roots hold the creature and the caster is mended. */
    OVERGROWTH(SkillElement.WATER, SkillElement.NATURE, 0.2f, 0x6FE08A),
    /** Light meets shadow: one enormous blow. */
    ECLIPSE(SkillElement.LIGHT, SkillElement.SHADOW, 1.0f, 0xD0B0FF),
    /** Lightning rides the wind: arcs to others nearby. */
    TEMPEST(SkillElement.LIGHTNING, SkillElement.AIR, 0.3f, 0xC8F0FF),
    /** Wind and earth: a blinding, choking cloud. */
    SANDSTORM(SkillElement.EARTH, SkillElement.AIR, 0.2f, 0xE0C890),
    /** Lightning and fire: a blast that throws everything back. */
    OVERLOAD(SkillElement.LIGHTNING, SkillElement.FIRE, 0.4f, 0xFFD060),
    /** Shadow in the green: rot and venom. */
    BLIGHT(SkillElement.SHADOW, SkillElement.NATURE, 0.3f, 0x9AC04A),
    /** Lightning into stone: it cracks apart. */
    SHATTER(SkillElement.EARTH, SkillElement.LIGHTNING, 0.5f, 0xD8C8A0),
    /** Light and fire: a flare that mends allies around it. */
    RADIANCE(SkillElement.LIGHT, SkillElement.FIRE, 0.3f, 0xFFF0B0),
    /** Arcane with anything else: the mana rings and some of it returns. */
    RESONANCE(SkillElement.ARCANE, null, 0.3f, 0x9FB4FF);

    private final SkillElement a;
    private final @Nullable SkillElement b;
    private final float bonus;
    private final int color;

    SkillReaction(SkillElement a, @Nullable SkillElement b, float bonus, int color) {
        this.a = a;
        this.b = b;
        this.bonus = bonus;
        this.color = color;
    }

    public float bonus() { return bonus; }
    public int color() { return color; }

    public Component displayName() {
        return Component.translatable("skill_reaction.beyondsolo." + name().toLowerCase());
    }

    /** The reaction of two different elements, or null if they do not react. Physical never reacts. */
    public static @Nullable SkillReaction of(SkillElement first, SkillElement second) {
        if (first == second || first == SkillElement.PHYSICAL || second == SkillElement.PHYSICAL) return null;
        for (SkillReaction r : values()) {
            if (r.b == null) continue;
            if ((r.a == first && r.b == second) || (r.a == second && r.b == first)) return r;
        }
        return first == SkillElement.ARCANE || second == SkillElement.ARCANE ? RESONANCE : null;
    }
}
