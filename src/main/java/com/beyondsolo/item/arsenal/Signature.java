package com.beyondsolo.item.arsenal;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * The signature art of a mythic or divine weapon: right-click to unleash it (bows: it is part of every full draw).
 * The effects live in {@link com.beyondsolo.combat.ArsenalAbilities}.
 */
public enum Signature {
    INFERNAL_REND(30, 0xFF5A1E),
    ABSOLUTE_ZERO(35, 0x9ED8FF),
    HEAVENS_VERDICT(40, 0xB8E2FF),
    CATACLYSM_CLEAVE(25, 0xFF7A2E),
    DRAGONS_WRATH(18, 0xE0306E),
    DEVOURING_STAR(14, 0x9B7BFF),
    COLOSSUS_STANCE(45, 0xFFC85A),
    VERDANT_BLESSING(50, 0x8CE05A),
    STARFALL(0, 0xFFF0A8);

    private final int cooldownSeconds;
    private final int color;

    Signature(int cooldownSeconds, int color) {
        this.cooldownSeconds = cooldownSeconds;
        this.color = color;
    }

    public int cooldownSeconds() {
        return cooldownSeconds;
    }

    public int color() {
        return color;
    }

    /**
     * Ticks from activation to the strike frame of the wielder's body animation: the damage lands then.
     * Matches the animations made by {@code tools/gen_signature_anims.py}.
     */
    public int impactTicks() {
        return switch (this) {
            case INFERNAL_REND -> 8;
            case ABSOLUTE_ZERO, CATACLYSM_CLEAVE -> 10;
            case HEAVENS_VERDICT -> 14;
            case DRAGONS_WRATH -> 5;
            case DEVOURING_STAR, COLOSSUS_STANCE -> 9;
            case VERDANT_BLESSING -> 12;
            case STARFALL -> 0;
        };
    }

    /** False for the bow's passive signature. */
    public boolean active() {
        return cooldownSeconds > 0;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("signature.beyondsolo." + key());
    }

    public Component description() {
        return Component.translatable("signature.beyondsolo." + key() + ".desc");
    }
}
