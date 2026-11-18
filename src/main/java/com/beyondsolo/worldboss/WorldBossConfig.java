package com.beyondsolo.worldboss;

import net.neoforged.neoforge.common.ModConfigSpec;

/** World boss and monarch settings (config/beyondsolo-worldbosses.toml). Numbers are first guesses for the balancing pass. */
public final class WorldBossConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = B
            .comment("World bosses rise in the overworld on their own, and monarchs come down out of the sky.",
                    "Off: they only come when called (commands, a Monarch's Seal).")
            .define("worldbosses.enabled", true);
    public static final ModConfigSpec.IntValue MIN_LEVEL = B
            .comment("No world boss rises until an awakened hunter in the overworld is at least this level.")
            .defineInRange("worldbosses.minLevel", 20, 1, 1000);
    public static final ModConfigSpec.IntValue FIRST_MINUTES = B
            .comment("Minutes after the first hunter reaches that level before the first world boss rises.")
            .defineInRange("worldbosses.firstMinutes", 30, 1, 10000);
    public static final ModConfigSpec.IntValue INTERVAL_MINUTES = B
            .comment("Minutes between one great threat leaving the world (slain or withdrawn) and the next coming.",
                    "Each wait is this give or take a quarter.")
            .defineInRange("worldbosses.intervalMinutes", 75, 5, 10000);
    public static final ModConfigSpec.DoubleValue MONARCH_CHANCE = B
            .comment("Once monarchs can come: the chance that the next great threat is a monarch instead of a world boss.")
            .defineInRange("worldbosses.monarchChance", 0.3, 0.0, 1.0);
    public static final ModConfigSpec.IntValue MONARCH_MIN_LEVEL = B
            .comment("Monarchs only come once an awakened hunter in the overworld is at least this level...")
            .defineInRange("worldbosses.monarchMinLevel", 40, 1, 1000);
    public static final ModConfigSpec.IntValue MONARCH_AFTER_KILLS = B
            .comment("...and once this many world bosses have been slain in this world.")
            .defineInRange("worldbosses.monarchAfterKills", 2, 0, 1000);
    public static final ModConfigSpec.IntValue DESCENT_SECONDS = B
            .comment("Seconds between a gate tearing open in the sky and its monarch landing.")
            .defineInRange("worldbosses.descentSeconds", 60, 5, 600);
    public static final ModConfigSpec.IntValue ABANDON_MINUTES = B
            .comment("A world boss or monarch no hunter comes near or hurts for this long leaves the world.")
            .defineInRange("worldbosses.abandonMinutes", 12, 1, 1000);
    public static final ModConfigSpec.IntValue MIN_DISTANCE = B
            .comment("How far from the hunter it was called for a great threat rises or lands (blocks).")
            .defineInRange("worldbosses.minDistance", 80, 24, 512);
    public static final ModConfigSpec.IntValue MAX_DISTANCE = B
            .defineInRange("worldbosses.maxDistance", 130, 32, 512);
    public static final ModConfigSpec.DoubleValue MONARCH_ARMOR_CHANCE = B
            .comment("Chance a slain monarch leaves each hunter who fought it (a tenth of its health or more) a piece of monarch armor.")
            .defineInRange("worldbosses.monarchArmorChance", 0.2, 0.0, 1.0);
    public static final ModConfigSpec.IntValue MONARCH_ARMOR_PITY = B
            .comment("A hunter who has fought this many monarchs to the end without a piece gets one for sure. 0: never.")
            .defineInRange("worldbosses.monarchArmorPity", 5, 0, 1000);
    public static final ModConfigSpec.DoubleValue SEAL_CHANCE = B
            .comment("Chance a slain world boss leaves its best hunter a Monarch's Seal (break it to call a monarch down).")
            .defineInRange("worldbosses.sealChance", 0.35, 0.0, 1.0);

    public static final ModConfigSpec SPEC = B.build();

    // safe reads: the config is not loaded in every context (early game tests)

    static boolean enabled() {
        try {
            return ENABLED.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    static int minLevel() {
        try {
            return MIN_LEVEL.get();
        } catch (IllegalStateException e) {
            return 20;
        }
    }

    static int firstTicks() {
        try {
            return FIRST_MINUTES.get() * 1200;
        } catch (IllegalStateException e) {
            return 30 * 1200;
        }
    }

    static int intervalTicks() {
        try {
            return INTERVAL_MINUTES.get() * 1200;
        } catch (IllegalStateException e) {
            return 75 * 1200;
        }
    }

    static double monarchChance() {
        try {
            return MONARCH_CHANCE.get();
        } catch (IllegalStateException e) {
            return 0.3;
        }
    }

    static int monarchMinLevel() {
        try {
            return MONARCH_MIN_LEVEL.get();
        } catch (IllegalStateException e) {
            return 40;
        }
    }

    static int monarchAfterKills() {
        try {
            return MONARCH_AFTER_KILLS.get();
        } catch (IllegalStateException e) {
            return 2;
        }
    }

    public static int descentTicks() {
        try {
            return DESCENT_SECONDS.get() * 20;
        } catch (IllegalStateException e) {
            return 60 * 20;
        }
    }

    static int abandonTicks() {
        try {
            return ABANDON_MINUTES.get() * 1200;
        } catch (IllegalStateException e) {
            return 12 * 1200;
        }
    }

    static int minDistance() {
        try {
            return MIN_DISTANCE.get();
        } catch (IllegalStateException e) {
            return 80;
        }
    }

    static int maxDistance() {
        try {
            return Math.max(minDistance() + 8, MAX_DISTANCE.get());
        } catch (IllegalStateException e) {
            return 130;
        }
    }

    static double monarchArmorChance() {
        try {
            return MONARCH_ARMOR_CHANCE.get();
        } catch (IllegalStateException e) {
            return 0.2;
        }
    }

    static int monarchArmorPity() {
        try {
            return MONARCH_ARMOR_PITY.get();
        } catch (IllegalStateException e) {
            return 5;
        }
    }

    static double sealChance() {
        try {
            return SEAL_CHANCE.get();
        } catch (IllegalStateException e) {
            return 0.35;
        }
    }

    private WorldBossConfig() {}
}
