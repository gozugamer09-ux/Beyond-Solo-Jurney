package com.beyondsolo.system;

/** Level curve. The numbers live in {@link Balance} with the rest of the power curve. */
public final class Progression {
    public static final int MAX_LEVEL = Balance.MAX_LEVEL;

    /** XP needed to go from {@code level} to {@code level + 1}. Level 1 needs 102. */
    public static long xpToNext(int level) {
        return Balance.xpToNext(level);
    }

    private Progression() {}
}
