package com.beyondsolo.job;

/**
 * Timing of the job change reveal, in ticks from its start. The server plays its world effects at these moments
 * and the client's overlay follows the same script.
 */
public final class JobRevealTimeline {
    /** The five subclass emblems have gathered around the class emblem; the roulette starts. */
    public static final int RING = 34;
    /** The roulette stops on the result. */
    public static final int LOCK = 130;
    /** Extra time a rare result is celebrated before the name card. */
    public static final int RARE_EXTRA = 50;
    /** The name card with the description and the new skills. */
    public static int card(boolean rare) {
        return LOCK + 26 + (rare ? RARE_EXTRA : 0);
    }

    /** The reveal is over and the player is sent home. */
    public static int finish(boolean rare) {
        return card(rare) + 150;
    }

    private JobRevealTimeline() {}
}
