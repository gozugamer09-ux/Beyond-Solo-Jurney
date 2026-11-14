package com.beyondsolo.evaluation;

/**
 * Timing of the evaluation, in server ticks (20 per second). The server plays its world effects at these
 * moments and the client's reveal overlay follows the same script, so both stay in step.
 */
public final class EvaluationTimeline {
    /** How long the crystal scans the player before the reveal starts. */
    public static final int SCAN_TICKS = 100;

    // The rest counts from the start of the reveal.
    /** The rank roulette stops and the rank is shown. */
    public static final int RANK_LOCK = 60;
    /** The class emblem starts to draw itself. */
    public static final int CLASS_START = 124;
    /** The class is shown. */
    public static final int CLASS_LOCK = 150;
    /** The summary with the starting stats appears. */
    public static final int STATS_START = 204;
    /** The reveal is over and normal play resumes. */
    public static final int FINISH = 280;

    private EvaluationTimeline() {}
}
