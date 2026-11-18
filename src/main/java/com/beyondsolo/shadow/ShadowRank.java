package com.beyondsolo.shadow;

import net.minecraft.network.chat.Component;

/**
 * The ranks a shadow climbs, from a husk barely holding its shape to a marshal of the army, and past it, for a crowned
 * monarch's shadows only, a regent. A shadow levels up from 1 to {@link #LEVEL_CAP} inside its rank; filling the last
 * level raises it to the next rank at level 1, up to the highest its master's shadows can reach ({@link #top}).
 */
public enum ShadowRank {
    HUSK(0x8C86A8, 1.00f),
    SOLDIER(0x9B8CE0, 1.25f),
    VANGUARD(0x6FA0FF, 1.55f),
    KNIGHT(0x5AD8FF, 1.90f),
    CAPTAIN(0xC07CFF, 2.40f),
    COMMANDER(0xFF6AD5, 3.00f),
    MARSHAL(0xFFD06A, 3.80f),
    /** Above the marshals: only a crowned monarch's shadows climb this far (a shadow keeps it if the crown is lost). */
    REGENT(0xFF4D7A, 4.80f);

    public static final int LEVEL_CAP = 10;

    private final int color;
    private final float power;

    ShadowRank(int color, float power) {
        this.color = color;
        this.power = power;
    }

    /** RGB. */
    public int color() { return color; }

    /** Multiplier on the shadow's health and damage. */
    public float power() { return power; }

    /** The very top rank (only a monarch's shadows reach it). */
    public boolean isMax() { return this == REGENT; }

    /** The highest rank a master's shadows can climb to: a regent for a crowned monarch's, a marshal for anyone else's. */
    public static ShadowRank top(boolean monarch) {
        return monarch ? REGENT : MARSHAL;
    }

    public ShadowRank next() {
        return isMax() ? this : values()[ordinal() + 1];
    }

    public String key() { return name().toLowerCase(); }

    public Component displayName() {
        return Component.translatable("shadow_rank.beyondsolo." + key());
    }

    public static ShadowRank byOrdinal(int i) {
        ShadowRank[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, i))];
    }
}
