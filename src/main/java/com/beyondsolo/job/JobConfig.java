package com.beyondsolo.job;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Job change settings (config/beyondsolo-jobs.toml). */
public final class JobConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue UNLOCK_LEVEL = B
            .comment("Player level at which the job change trial opens.")
            .defineInRange("jobChange.unlockLevel", 20, 1, 999);
    public static final ModConfigSpec.DoubleValue RARE_CHANCE = B
            .comment("Chance (0..1) that the job change gives the class's extremely rare subclass.")
            .defineInRange("jobChange.rareChance", 0.01, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue FLAWLESS_MULTIPLIER = B
            .comment("The rare chance is multiplied by this when the trial's final guardian never landed a hit.")
            .defineInRange("jobChange.flawlessMultiplier", 2.0, 1.0, 100.0);
    public static final ModConfigSpec.IntValue RETRY_MINUTES = B
            .comment("Real minutes before a failed trial can be attempted again.")
            .defineInRange("jobChange.retryMinutes", 10, 0, 10_000);

    public static final ModConfigSpec SPEC = B.build();

    private JobConfig() {}
}
