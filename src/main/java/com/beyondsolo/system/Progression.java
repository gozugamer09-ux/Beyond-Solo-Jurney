package com.beyondsolo.system;

/** Level curve. Kept in one place so it is easy to tune. */
public final class Progression {
    public static final int MAX_LEVEL = 999;

    /** XP needed to go from {@code level} to {@code level + 1}. Level 1 needs 100. */
    public static long xpToNext(int level) {
        return Math.round(100 * Math.pow(level, 1.4));
    }

    private Progression() {}
}
