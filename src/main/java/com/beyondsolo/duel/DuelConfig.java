package com.beyondsolo.duel;

import com.beyondsolo.system.PlayerRank;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.function.Supplier;

/** Duels with the national-level hunters (config/beyondsolo-duels.toml). Numbers are first guesses for the balancing pass. */
public final class DuelConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue HEALTH = B
            .comment("A national-level hunter's health: a dungeon boss's made for a hunter of their level, times this.")
            .defineInRange("duel.health", 1.0, 0.01, 100.0);
    public static final ModConfigSpec.DoubleValue DAMAGE = B
            .comment("What one of their blows deals: a dungeon boss's of their level, times this (their moves deal a share or a multiple of it).")
            .defineInRange("duel.damage", 1.0, 0.01, 100.0);
    public static final ModConfigSpec.DoubleValue UNLEASH_AT = B
            .comment("The share of their health at which they show what they really are (their second form).")
            .defineInRange("duel.unleashAt", 0.5, 0.0, 1.0);
    public static final ModConfigSpec.IntValue RING_RADIUS = B
            .comment("The radius of the ring a duel is fought in (blocks).")
            .defineInRange("duel.ringRadius", 13, 6, 40);
    public static final ModConfigSpec.IntValue TIME_LIMIT_SECONDS = B
            .comment("How long a duel may run; at the end of it, a draw.")
            .defineInRange("duel.timeLimitSeconds", 180, 30, 3600);
    public static final ModConfigSpec.IntValue OUTSIDE_SECONDS = B
            .comment("Seconds a hunter may stand outside the ring before they have forfeited.")
            .defineInRange("duel.outsideSeconds", 5, 1, 60);
    public static final ModConfigSpec.DoubleValue PURSE_GATES = B
            .comment("A won duel pays this many clears' gold of an S-rank gate.")
            .defineInRange("duel.purseGates", 4.0, 0.0, 1000.0);
    public static final ModConfigSpec.IntValue MERIT = B
            .comment("Association merit for a won duel.")
            .defineInRange("duel.merit", 250, 0, 100000);
    public static final ModConfigSpec.DoubleValue XP_SHARE = B
            .comment("System XP for a won duel: this share of what the hunter's next level takes.")
            .defineInRange("duel.xpShare", 0.12, 0.0, 10.0);
    public static final ModConfigSpec.IntValue REMATCH_DAYS = B
            .comment("World days before the same national-level hunter duels a hunter again.")
            .defineInRange("duel.rematchDays", 1, 0, 365);
    public static final ModConfigSpec.IntValue TEST_DELAY_MINUTES = B
            .comment("Minutes after a hunter reaches rank S before a national-level hunter comes to test them (after the guilds' scouts).")
            .defineInRange("duel.testDelayMinutes", 6, 0, 1440);
    public static final ModConfigSpec.IntValue CHALLENGE_MINUTES = B
            .comment("Minutes a challenge stands before it lapses.")
            .defineInRange("duel.challengeMinutes", 30, 1, 1440);
    public static final ModConfigSpec.IntValue RETURN_MINUTES = B
            .comment("Minutes before a challenger the hunter put off (\"Not now\") comes back.")
            .defineInRange("duel.returnMinutes", 5, 0, 1440);
    public static final ModConfigSpec.IntValue WAIT_SECONDS = B
            .comment("Seconds a challenger waits by the hunter for an answer before walking off (the challenge stands).")
            .defineInRange("duel.waitSeconds", 150, 10, 3600);
    public static final ModConfigSpec.ConfigValue<String> MIN_RANK = B
            .comment("The lowest rank that may ask a national-level hunter for a duel at the association (E, D, C, B, A or S).")
            .define("duel.minRank", "A", o -> o instanceof String s && parseRank(s) != null);

    public static final ModConfigSpec SPEC = B.build();

    // safe reads: the config is not loaded in every context (early game tests)

    private static <T> T get(Supplier<T> value, T fallback) {
        try {
            T v = value.get();
            return v == null ? fallback : v;
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    static PlayerRank parseRank(String s) {
        for (PlayerRank r : PlayerRank.values()) if (r.name().equalsIgnoreCase(s.trim())) return r;
        return null;
    }

    public static double health() { return get(HEALTH::get, 1.0); }
    public static double damage() { return get(DAMAGE::get, 1.0); }
    public static float unleashAt() { return (float) (double) get(UNLEASH_AT::get, 0.5); }
    public static int ringRadius() { return get(RING_RADIUS::get, 13); }
    public static int timeLimitTicks() { return get(TIME_LIMIT_SECONDS::get, 180) * 20; }
    public static int outsideTicks() { return get(OUTSIDE_SECONDS::get, 5) * 20; }
    public static double purseGates() { return get(PURSE_GATES::get, 4.0); }
    public static int merit() { return get(MERIT::get, 250); }
    public static double xpShare() { return get(XP_SHARE::get, 0.12); }
    public static int rematchDays() { return get(REMATCH_DAYS::get, 1); }
    public static int testDelayTicks() { return get(TEST_DELAY_MINUTES::get, 6) * 1200; }
    public static int challengeTicks() { return get(CHALLENGE_MINUTES::get, 30) * 1200; }
    public static int returnTicks() { return get(RETURN_MINUTES::get, 5) * 1200; }
    public static int waitTicks() { return get(WAIT_SECONDS::get, 150) * 20; }

    public static PlayerRank minRank() {
        PlayerRank r = parseRank(get(MIN_RANK::get, "A"));
        return r == null ? PlayerRank.A : r;
    }

    private DuelConfig() {}
}
