package com.beyondsolo.gate;

import com.beyondsolo.system.PlayerRank;

/**
 * What the boss bar and the boss intro need to know about a dungeon's boss: the ordinary gate boss
 * ({@link GateBossEntity}) and the awakening trial's statue ({@code awakening.ArbiterEntity}) both are one.
 */
public interface DungeonBoss {
    boolean isAwake();

    /** Ticks left of its intro (it can't be hurt and doesn't fight until 0). */
    int introTicks();

    /** In its second, harder phase. */
    boolean isEnraged();

    /** The rank emblem shown on its bar. */
    PlayerRank rank();

    /** The colour of its bar's frame and its light (RGB). */
    int barColor();

    /** Shown on the boss bar instead of the health numbers, if not null. */
    default String healthLabel() {
        return null;
    }
}
