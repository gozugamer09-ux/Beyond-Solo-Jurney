package com.beyondsolo.home;

import com.beyondsolo.system.PlayerRank;
import net.neoforged.neoforge.common.ModConfigSpec;

/** The hunter's residence's settings (config/beyondsolo-home.toml). Numbers are first guesses for the balancing pass. */
public final class HomeConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = B
            .comment("Whether hunters can buy a residence of their own and go home to it from anywhere.")
            .define("home.enabled", true);
    public static final ModConfigSpec.IntValue CHANNEL_SECONDS = B
            .comment("Seconds a hunter stands still (and unhurt) before they are taken home or to a party member's home.")
            .defineInRange("home.channelSeconds", 3, 0, 60);
    public static final ModConfigSpec.IntValue COOLDOWN_SECONDS = B
            .comment("Seconds after reaching a home before a hunter can go home again (leaving is never held back).")
            .defineInRange("home.cooldownSeconds", 60, 0, 86400);
    public static final ModConfigSpec.IntValue REST_MINUTES = B
            .comment("Minutes Well Rested lasts, after a night in the hunter's own bed at home (once a day).")
            .defineInRange("home.restMinutes", 30, 1, 1440);
    public static final ModConfigSpec.BooleanValue VISITS = B
            .comment("Whether party members can visit each other's homes.")
            .define("home.visits", true);

    // the four tiers: what each asks for (first guesses)
    public static final ModConfigSpec.IntValue STUDIO_LEVEL = B.comment("The Studio: the level it asks for.").defineInRange("home.studio.level", 10, 1, 1000);
    public static final ModConfigSpec.IntValue STUDIO_RANK = B.comment("The Studio: the rank it asks for (0 E .. 5 S).").defineInRange("home.studio.rank", 0, 0, 5);
    public static final ModConfigSpec.LongValue STUDIO_GOLD = B.comment("The Studio: its price in gold.").defineInRange("home.studio.gold", 8_000L, 0L, 1_000_000_000L);
    public static final ModConfigSpec.IntValue FAMILY_LEVEL = B.comment("The Family Home: the level it asks for.").defineInRange("home.family.level", 25, 1, 1000);
    public static final ModConfigSpec.IntValue FAMILY_RANK = B.comment("The Family Home: the rank it asks for.").defineInRange("home.family.rank", 2, 0, 5);
    public static final ModConfigSpec.LongValue FAMILY_GOLD = B.comment("The Family Home: its price in gold.").defineInRange("home.family.gold", 30_000L, 0L, 1_000_000_000L);
    public static final ModConfigSpec.IntValue HALL_LEVEL = B.comment("The Training Hall: the level it asks for.").defineInRange("home.hall.level", 45, 1, 1000);
    public static final ModConfigSpec.IntValue HALL_RANK = B.comment("The Training Hall: the rank it asks for.").defineInRange("home.hall.rank", 3, 0, 5);
    public static final ModConfigSpec.LongValue HALL_GOLD = B.comment("The Training Hall: its price in gold.").defineInRange("home.hall.gold", 90_000L, 0L, 1_000_000_000L);
    public static final ModConfigSpec.IntValue PENTHOUSE_LEVEL = B.comment("The Penthouse: the level it asks for.").defineInRange("home.penthouse.level", 70, 1, 1000);
    public static final ModConfigSpec.IntValue PENTHOUSE_RANK = B.comment("The Penthouse: the rank it asks for.").defineInRange("home.penthouse.rank", 4, 0, 5);
    public static final ModConfigSpec.LongValue PENTHOUSE_GOLD = B.comment("The Penthouse: its price in gold.").defineInRange("home.penthouse.gold", 250_000L, 0L, 1_000_000_000L);

    public static final ModConfigSpec SPEC = B.build();

    public static boolean enabled() {
        return ENABLED.get();
    }

    public static int channelTicks() {
        return CHANNEL_SECONDS.get() * 20;
    }

    public static long cooldownTicks() {
        return COOLDOWN_SECONDS.get() * 20L;
    }

    public static int restTicks() {
        return REST_MINUTES.get() * 60 * 20;
    }

    public static boolean visits() {
        return VISITS.get();
    }

    /** The level a tier asks for. */
    public static int level(HomeTier t) {
        return switch (t) {
            case NONE -> 1;
            case STUDIO -> STUDIO_LEVEL.get();
            case FAMILY -> FAMILY_LEVEL.get();
            case HALL -> HALL_LEVEL.get();
            case PENTHOUSE -> PENTHOUSE_LEVEL.get();
        };
    }

    /** The rank a tier asks for. */
    public static PlayerRank rank(HomeTier t) {
        int r = switch (t) {
            case NONE -> 0;
            case STUDIO -> STUDIO_RANK.get();
            case FAMILY -> FAMILY_RANK.get();
            case HALL -> HALL_RANK.get();
            case PENTHOUSE -> PENTHOUSE_RANK.get();
        };
        return PlayerRank.values()[Math.max(0, Math.min(PlayerRank.values().length - 1, r))];
    }

    /** What a tier costs. */
    public static long gold(HomeTier t) {
        return switch (t) {
            case NONE -> 0;
            case STUDIO -> STUDIO_GOLD.get();
            case FAMILY -> FAMILY_GOLD.get();
            case HALL -> HALL_GOLD.get();
            case PENTHOUSE -> PENTHOUSE_GOLD.get();
        };
    }

    private HomeConfig() {}
}
