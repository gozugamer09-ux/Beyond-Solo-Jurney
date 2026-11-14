package com.beyondsolo.quest;

/**
 * How quests grow with the player's rank (tier 0 = E ... 5 = S) and level. Higher ranks get more to do,
 * bigger rewards and harsher penalties. NPC and gate quests should use the same curves.
 */
public final class RankScaling {
    /** Kill / break / gather counts: E x1, C x1.6, S x2.5. */
    public static int count(int base, int tier) {
        return Math.max(1, Math.round(base * (1 + 0.3f * tier)));
    }

    /** XP: E x1, S x3.5, plus 4% per level. */
    public static long xp(long base, int tier, int level) {
        return Math.round(base * (1 + 0.5 * tier) * (1 + 0.04 * Math.max(0, level - 1)));
    }

    /** Stat points: one more for every two ranks. */
    public static int statPoints(int base, int tier) {
        return base <= 0 ? 0 : base + tier / 2;
    }

    /** Failure: E loses {@code exhaustion}, S about twice as much and twenty seconds more of debuffs. */
    public static QuestPenalty penalty(float exhaustion, int debuffSeconds, int tier) {
        return new QuestPenalty(exhaustion * (1 + 0.18f * tier), debuffSeconds + 4 * tier);
    }

    private RankScaling() {}
}
