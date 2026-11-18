package com.beyondsolo.chronicle.ranking;

import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.tower.RushTier;

/**
 * One of the world's other hunters on the rankings (not a player). Until the hunter association and the national
 * rankings come, a fixed roster stands in: each with a rank, a class, a guild, and records that grow a little with the
 * days of the world, so a hunter has someone to climb past.
 *
 * @param level      where they stand on the hunter scale ({@code HiddenLevel.hunterLevel}) on the first day
 * @param growth     levels a day they keep climbing, up to {@link #GROWTH_CAP} more than their first level
 * @param floor      the highest Citadel floor they have reached
 * @param spire      the best Endless Spire floor
 * @param rushSeconds best boss rush time per tier in seconds (0 for none), E to the Sovereign rush
 * @param gates      gates cleared on the first day; ~{@code gates / 40} more a day
 */
public record Rival(String id, String name, PlayerRank rank, PlayerClass playerClass, String guild, float level, float growth,
                    int floor, int spire, int[] rushSeconds, int gates) {

    /** How far past their first level a rival climbs at most: 30% (the best of them ends just under the level cap). */
    public static final float GROWTH_CAP = 0.3f;

    /** Their level on a day of the world. */
    public float levelOn(long day) {
        return level + Math.min(level * GROWTH_CAP, growth * Math.max(0, day));
    }

    public int gatesOn(long day) {
        return gates + (int) Math.min(gates, Math.max(0, day) * Math.max(1, gates / 40));
    }

    /** Their best time in a boss rush tier, in ticks (-1 for none): their rank's pace, a little off it for each one. */
    public long rushTicks(RushTier tier) {
        int i = tier.ordinal();
        if (i >= rushSeconds.length || rushSeconds[i] <= 0) return -1;
        int spread = Math.floorMod(id.hashCode() * 31 + i * 7, 37) - 18;
        return Math.max(60, rushSeconds[i] + spread) * 20L;
    }
}
