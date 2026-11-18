package com.beyondsolo.quest.situation;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Situation quest settings (config/beyondsolo-situations.toml). Numbers are first guesses for the balancing pass. */
public final class SituationConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = B
            .comment("The System reads what is happening around a hunter and answers with quests of its own: a creature far",
                    "stronger than them hunting them, the edge of death, a flawless streak, a villager under attack, a raid,",
                    "ruins, a gate above their rank... Those quests change as the situation does. false turns them all off.")
            .define("situations.enabled", true);
    public static final ModConfigSpec.IntValue MIN_GAP_SECONDS = B
            .comment("Least time between two of the System's unprompted quests that are not emergencies (streaks, ruins, rest).",
                    "Emergencies answer danger and only wait for their own cooldowns.")
            .defineInRange("situations.minGapSeconds", 120, 0, 3600);
    public static final ModConfigSpec.BooleanValue FOLLOW_UPS = B
            .comment("What a hunter does comes back: a creature they fled from tracks them down later (a nemesis), and one that",
                    "killed them is waiting when they come back (revenge).")
            .define("situations.followUps", true);
    public static final ModConfigSpec.IntValue NEMESIS_MIN_MINUTES = B
            .comment("Soonest a creature a hunter fled from comes back for them, in minutes.")
            .defineInRange("situations.nemesisMinMinutes", 6, 1, 600);
    public static final ModConfigSpec.IntValue NEMESIS_MAX_MINUTES = B
            .comment("Latest it comes back, in minutes.")
            .defineInRange("situations.nemesisMaxMinutes", 15, 1, 600);

    public static final ModConfigSpec SPEC = B.build();

    /** Safe reads: the config is not loaded in every context (early game tests). */
    public static boolean enabled() {
        try {
            return ENABLED.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static int gapTicks() {
        try {
            return MIN_GAP_SECONDS.get() * 20;
        } catch (IllegalStateException e) {
            return 120 * 20;
        }
    }

    public static boolean followUps() {
        try {
            return FOLLOW_UPS.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    /** Ticks until a nemesis comes back, between the two settings. */
    public static long nemesisDelay(net.minecraft.util.RandomSource r) {
        int min, max;
        try {
            min = NEMESIS_MIN_MINUTES.get();
            max = Math.max(min, NEMESIS_MAX_MINUTES.get());
        } catch (IllegalStateException e) {
            min = 6;
            max = 15;
        }
        return 20L * 60 * min + (long) (r.nextFloat() * 20 * 60 * (max - min));
    }

    private SituationConfig() {}
}
