package com.beyondsolo.redgate;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Red gate settings (config/beyondsolo-redgates.toml). Numbers are first guesses for the balancing pass. */
public final class RedGateConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue CHANCE = B
            .comment("Chance that a gate is a red gate in waiting: the first awakened hunter through turns it red and it seals.",
                    "Any gate of any rank can be one (keyed gates and awakening trials never are). 0 turns red gates off.")
            .defineInRange("redgates.chance", 0.06, 0.0, 1.0);
    public static final ModConfigSpec.IntValue OPEN_SECONDS = B
            .comment("Seconds a gate that has just turned red still lets the rest of a party follow before it seals.")
            .defineInRange("redgates.openSeconds", 8, 2, 120);
    public static final ModConfigSpec.IntValue TIME_RATE = B
            .comment("How much faster time runs inside a red gate than outside (8: an hour outside is eight inside).")
            .defineInRange("redgates.timeRate", 8, 1, 72);
    public static final ModConfigSpec.IntValue RELEASE_DAY = B
            .comment("Inside a red gate, the dawn of this day breaks its hold: the way out opens even with its master alive.",
                    "0 keeps the gate sealed until its master falls.")
            .defineInRange("redgates.releaseDay", 8, 0, 100);
    public static final ModConfigSpec.DoubleValue REWARD_MULTIPLIER = B
            .comment("Clearing a red gate pays this many times a gate of its inside rank (XP and gold).")
            .defineInRange("redgates.rewardMultiplier", 2.0, 1.0, 10.0);
    public static final ModConfigSpec.BooleanValue COLD = B
            .comment("The red winter's cold: out in the open at night or in a blizzard hunters chill, and freeze if they stay out.",
                    "Fires warm them; so do a roof and leather.")
            .define("redgates.cold", true);

    public static final ModConfigSpec SPEC = B.build();

    /** Safe reads: the config is not loaded in every context (early game tests). */
    public static double chance() {
        try {
            return CHANCE.get();
        } catch (IllegalStateException e) {
            return 0.06;
        }
    }

    static int openTicks() {
        try {
            return OPEN_SECONDS.get() * 20;
        } catch (IllegalStateException e) {
            return 8 * 20;
        }
    }

    public static int timeRate() {
        try {
            return TIME_RATE.get();
        } catch (IllegalStateException e) {
            return 8;
        }
    }

    public static int releaseDay() {
        try {
            return RELEASE_DAY.get();
        } catch (IllegalStateException e) {
            return 8;
        }
    }

    static double rewardMultiplier() {
        try {
            return REWARD_MULTIPLIER.get();
        } catch (IllegalStateException e) {
            return 2.0;
        }
    }

    static boolean cold() {
        try {
            return COLD.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    private RedGateConfig() {}
}
