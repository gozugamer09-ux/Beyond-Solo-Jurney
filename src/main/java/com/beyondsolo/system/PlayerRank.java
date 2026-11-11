package com.beyondsolo.system;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

/**
 * Rank from the evaluation. Higher ranks start with more bonus stat points.
 * Later systems (gates, quests) read the rank to scale difficulty and rewards.
 */
public enum PlayerRank {
    E(0x9E9E9E, 0, 40),
    D(0x7CC576, 5, 25),
    C(0x4FA3F7, 10, 17),
    B(0xB57CF7, 18, 10),
    A(0xF7B84F, 30, 6),
    S(0xF75A4F, 45, 2);

    private final int color;
    private final int bonusStatPoints;
    private final int evaluationWeight;

    PlayerRank(int color, int bonusStatPoints, int evaluationWeight) {
        this.color = color;
        this.bonusStatPoints = bonusStatPoints;
        this.evaluationWeight = evaluationWeight;
    }

    public int color() {
        return color;
    }

    public int bonusStatPoints() {
        return bonusStatPoints;
    }

    /** 0 for E up to 5 for S. Handy for scaling quest and gate difficulty. */
    public int tier() {
        return ordinal();
    }

    public Component displayName() {
        return Component.translatable("rank.beyondsolo." + name().toLowerCase());
    }

    public static PlayerRank roll(RandomSource random) {
        int total = 0;
        for (PlayerRank r : values()) total += r.evaluationWeight;
        int pick = random.nextInt(total);
        for (PlayerRank r : values()) {
            pick -= r.evaluationWeight;
            if (pick < 0) return r;
        }
        return E;
    }

    public static PlayerRank byName(String name) {
        for (PlayerRank r : values()) if (r.name().equalsIgnoreCase(name)) return r;
        return null;
    }
}
