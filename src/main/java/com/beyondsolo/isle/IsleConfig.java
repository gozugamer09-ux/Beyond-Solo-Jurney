package com.beyondsolo.isle;

import com.beyondsolo.system.PlayerRank;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.function.Supplier;

/** The Halcyon raid's settings (config/beyondsolo-isle.toml). Numbers are first guesses for the balancing pass. */
public final class IsleConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = B
            .comment("Whether the Hunter Association calls hunters to retake Halcyon Isle from the swarm every so often.")
            .define("isle.enabled", true);
    public static final ModConfigSpec.IntValue MIN_RANK = B
            .comment("The lowest rank the Association calls (0 E, 1 D, 2 C, 3 B, 4 A, 5 S).")
            .defineInRange("isle.minRank", 3, 0, 5);
    public static final ModConfigSpec.IntValue CALL_MINUTES = B
            .comment("Minutes between the Association's calls, give or take a quarter (only while a hunter it would call is online).")
            .defineInRange("isle.callMinutes", 90, 5, 10000);
    public static final ModConfigSpec.IntValue FIRST_CALL_MINUTES = B
            .comment("Minutes from the first time a hunter it would call is online to the first call.")
            .defineInRange("isle.firstCallMinutes", 25, 1, 10000);
    public static final ModConfigSpec.IntValue MUSTER_SECONDS = B
            .comment("Seconds the call stays open: hunters who accept it in that time are taken to the island together.")
            .defineInRange("isle.musterSeconds", 150, 10, 3600);
    public static final ModConfigSpec.IntValue NESTS = B
            .comment("Nests on the island: every one has to burn before the great hive opens.")
            .defineInRange("isle.nests", 5, 1, 8);
    public static final ModConfigSpec.IntValue BEACH_WAVES = B
            .comment("Waves of the swarm that come down on the landing before the push inland.")
            .defineInRange("isle.beachWaves", 3, 0, 10);
    public static final ModConfigSpec.IntValue SWARM_RANK = B
            .comment("The rank of the swarm's creatures (0 E .. 5 S); the queen's royal guard is always S.")
            .defineInRange("isle.swarmRank", 4, 0, 5);
    public static final ModConfigSpec.IntValue SWARM_CAP = B
            .comment("Most creatures of the swarm alive on the island at once.")
            .defineInRange("isle.swarmCap", 26, 4, 200);
    public static final ModConfigSpec.IntValue NEST_SECONDS = B
            .comment("Seconds between broods of a nest while hunters are near it.")
            .defineInRange("isle.nestSeconds", 7, 1, 120);
    public static final ModConfigSpec.IntValue NEST_HITS = B
            .comment("Blows a nest's heart takes before it bursts, for one hunter; each hunter more adds nestHitsPerHunter.")
            .defineInRange("isle.nestHits", 14, 1, 500);
    public static final ModConfigSpec.IntValue NEST_HITS_PER_HUNTER = B
            .comment("Blows more a nest's heart takes for each hunter in the raid beyond the first.")
            .defineInRange("isle.nestHitsPerHunter", 4, 0, 100);
    public static final ModConfigSpec.IntValue ALLIES = B
            .comment("Hunters from the national rankings who fight alongside.")
            .defineInRange("isle.allies", 5, 0, 8);
    public static final ModConfigSpec.DoubleValue ALLY_ROYAL_DAMAGE = B
            .comment("How hard the allies' blows land on the queen and the king (1 = in full): they keep the swarm off the hunters,",
                    "but the royalty is the hunters' to bring down.")
            .defineInRange("isle.allyRoyalDamage", 0.1, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue REWARD_SCALE = B
            .comment("Retaking the island pays this many times an S-rank gate's clear (XP and gold), besides each boss's own share.")
            .defineInRange("isle.rewardScale", 3.0, 0.0, 100.0);
    public static final ModConfigSpec.IntValue TIME_LIMIT_MINUTES = B
            .comment("Minutes the hunters have, from the landing, before the Association calls them back and the raid is lost.")
            .defineInRange("isle.timeLimitMinutes", 60, 5, 1000);

    public static final ModConfigSpec SPEC = B.build();

    /** Test and command overrides: they win over the file while set (null: the file). */
    static Integer nestsOverride, wavesOverride, alliesOverride, nestHitsOverride;

    /** A safe read: the config is not loaded in every context (early game tests). */
    private static <T> T get(Supplier<T> value, T fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    public static boolean enabled() {
        return get(ENABLED::get, true);
    }

    public static PlayerRank minRank() {
        return PlayerRank.values()[Math.max(0, Math.min(5, get(MIN_RANK::get, 3)))];
    }

    static long callTicks() {
        return get(CALL_MINUTES::get, 90) * 1200L;
    }

    static long firstCallTicks() {
        return get(FIRST_CALL_MINUTES::get, 25) * 1200L;
    }

    public static int musterTicks() {
        return get(MUSTER_SECONDS::get, 150) * 20;
    }

    public static int nests() {
        return nestsOverride != null ? nestsOverride : get(NESTS::get, 5);
    }

    public static int beachWaves() {
        return wavesOverride != null ? wavesOverride : get(BEACH_WAVES::get, 3);
    }

    public static PlayerRank swarmRank() {
        return PlayerRank.values()[Math.max(0, Math.min(5, get(SWARM_RANK::get, 4)))];
    }

    static int swarmCap() {
        return get(SWARM_CAP::get, 26);
    }

    static int nestTicks() {
        return get(NEST_SECONDS::get, 7) * 20;
    }

    /** Blows a nest's heart takes for a raid of {@code hunters}. */
    public static int nestHits(int hunters) {
        if (nestHitsOverride != null) return nestHitsOverride;
        return get(NEST_HITS::get, 14) + get(NEST_HITS_PER_HUNTER::get, 4) * Math.max(0, hunters - 1);
    }

    public static int allies() {
        return alliesOverride != null ? alliesOverride : get(ALLIES::get, 5);
    }

    /** What share of an ally's blow lands on the queen or the king. */
    public static float allyRoyalDamage() {
        return (float) (double) get(ALLY_ROYAL_DAMAGE::get, 0.1);
    }

    static double rewardScale() {
        return get(REWARD_SCALE::get, 3.0);
    }

    public static long timeLimitTicks() {
        return get(TIME_LIMIT_MINUTES::get, 60) * 1200L;
    }

    private IsleConfig() {}
}
