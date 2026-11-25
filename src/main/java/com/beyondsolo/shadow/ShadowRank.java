package com.beyondsolo.shadow;

import net.minecraft.network.chat.Component;

/**
 * The ranks a shadow climbs, from a husk barely holding its shape to a marshal of the army, and past it, for a crowned
 * monarch's shadows only, a regent. A shadow levels up from 1 to {@link #LEVEL_CAP} inside its rank. Up to a knight,
 * filling the last level raises it to the next rank by itself; from a knight on the shadow waits at the top of its rank
 * until its master promotes it ({@link #byPromotion}, {@link ShadowPromotions}), up to the highest its master's shadows
 * can reach ({@link #top}). Each rank is a real step up: about 30% stronger than the one below, more than all the
 * levels inside a rank add (+18% at level 10), so a shadow comes into its new rank at level 1 and is stronger for it
 * ({@link #entryLevel} keeps that true whatever the table says).
 */
public enum ShadowRank {
    HUSK(0x8C86A8, 1.00f, 0, 0),
    SOLDIER(0x9B8CE0, 1.30f, 0, 0),
    VANGUARD(0x6FA0FF, 1.68f, 0, 0),
    KNIGHT(0x5AD8FF, 2.18f, 0, 0),
    CAPTAIN(0xC07CFF, 2.82f, 30, 150),
    COMMANDER(0xFF6AD5, 3.65f, 45, 400),
    MARSHAL(0xFFD06A, 4.73f, 60, 1000),
    /** Above the marshals: only a crowned monarch's shadows climb this far (a shadow keeps it if the crown is lost). */
    REGENT(0xFF4D7A, 6.12f, 80, 2500);

    public static final int LEVEL_CAP = 10;
    /** How much stronger each level inside a rank makes a shadow (+18% at level 10): less than a rank's step (~30%). */
    public static final float LEVEL_GROWTH = 0.02f;

    private final int color;
    private final float power;
    private final int promotionLevel;
    private final int promotionCost;

    ShadowRank(int color, float power, int promotionLevel, int promotionCost) {
        this.color = color;
        this.power = power;
        this.promotionLevel = promotionLevel;
        this.promotionCost = promotionCost;
    }

    /** True for a rank a shadow only reaches when its master promotes it (captain and up). */
    public boolean byPromotion() { return promotionCost > 0; }

    /** The master's level a promotion to this rank needs (0: none, the shadow climbs to it by itself). First guess. */
    public int promotionLevel() { return promotionLevel; }

    /** The shadow essence a promotion to this rank costs (0: none). First guess. */
    public int promotionCost() { return promotionCost; }

    /** RGB. */
    public int color() { return color; }

    /** Multiplier on the shadow's health and damage at level 1 of this rank. */
    public float power() { return power; }

    /** Multiplier on the shadow's health and damage at a level of this rank. */
    public float power(int level) {
        return power * (1 + LEVEL_GROWTH * (Math.max(1, Math.min(LEVEL_CAP, level)) - 1));
    }

    /**
     * The level a shadow at {@code level} of this rank comes into the next one at: the lowest that is at least as strong,
     * so a rank never makes it weaker. With a rank's step bigger than its levels' growth, that is always level 1.
     */
    public int entryLevel(int level) {
        ShadowRank to = next();
        float was = power(level);
        for (int l = 1; l < LEVEL_CAP; l++) if (to.power(l) >= was - 1.0e-4f) return l;
        return LEVEL_CAP;
    }

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
