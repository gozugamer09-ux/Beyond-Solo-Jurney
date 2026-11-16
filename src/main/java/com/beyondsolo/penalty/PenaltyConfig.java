package com.beyondsolo.penalty;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Penalty Zone settings (config/beyondsolo-penalty.toml). Numbers are first guesses for the balancing pass. */
public final class PenaltyConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = B
            .comment("Failing (or giving up) the daily quest sends the player to the Penalty Zone.",
                    "When off, the daily's usual fatigue, weakness and slowness apply instead.")
            .define("penalty.enabled", true);
    public static final ModConfigSpec.IntValue SURVIVE_SECONDS = B
            .comment("How long the player has to survive in the Penalty Zone.")
            .defineInRange("penalty.surviveSeconds", 180, 20, 3600);
    public static final ModConfigSpec.IntValue WARNING_SECONDS = B
            .comment("Seconds of warning before the player is pulled in.")
            .defineInRange("penalty.warningSeconds", 10, 3, 120);
    public static final ModConfigSpec.IntValue COLLAPSE_DEBUFF_SECONDS = B
            .comment("Falling in the zone: the player collapses at home and stays weak and slow this long.")
            .defineInRange("penalty.failDebuffSeconds", 90, 0, 3600);

    public static final ModConfigSpec SPEC = B.build();

    /** Safe reads: the config is not loaded in every context (early game tests). */
    static boolean enabled() {
        try {
            return ENABLED.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    static int surviveTicks() {
        try {
            return SURVIVE_SECONDS.get() * 20;
        } catch (IllegalStateException e) {
            return 180 * 20;
        }
    }

    static int warningTicks() {
        try {
            return WARNING_SECONDS.get() * 20;
        } catch (IllegalStateException e) {
            return 200;
        }
    }

    static int failDebuffTicks() {
        try {
            return COLLAPSE_DEBUFF_SECONDS.get() * 20;
        } catch (IllegalStateException e) {
            return 90 * 20;
        }
    }

    private PenaltyConfig() {}
}
