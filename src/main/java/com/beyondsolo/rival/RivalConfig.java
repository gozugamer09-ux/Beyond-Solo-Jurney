package com.beyondsolo.rival;

import com.beyondsolo.system.PlayerRank;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.function.Supplier;

/** Rival parties and hired hands (config/beyondsolo-rivals.toml). Numbers are first guesses for the balancing pass. */
public final class RivalConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue DISPATCH_CHANCE = B
            .comment("The chance that a gate opening near a hunter draws another party of hunters, racing them for it (0: never).",
                    "Never for a hunter's first gate, a raid gate, a gate that will turn red, or one the association gave to someone.")
            .defineInRange("rivals.dispatchChance", 0.35, 0.0, 1.0);
    public static final ModConfigSpec.IntValue ETA_MIN = B
            .comment("Seconds at least before a rival party reaches its gate.")
            .defineInRange("rivals.etaMinSeconds", 75, 5, 3600);
    public static final ModConfigSpec.IntValue ETA_MAX = B
            .comment("Seconds at most before a rival party reaches its gate.")
            .defineInRange("rivals.etaMaxSeconds", 150, 5, 3600);
    public static final ModConfigSpec.IntValue GATHER_SECONDS = B
            .comment("Seconds a rival party gathers in front of its gate before it goes in (a hunter who goes in meanwhile beats them to it).")
            .defineInRange("rivals.gatherSeconds", 10, 0, 600);
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> CLEAR_MINUTES = B
            .comment("Minutes a rival party takes to clear a gate of each rank, E to S (a little more or less for each party).")
            .defineList("rivals.clearMinutes", List.of(3, 4, 5, 6, 8, 10), () -> 5, o -> o instanceof Integer i && i > 0);
    public static final ModConfigSpec.DoubleValue GUILD_SUCCESS = B
            .comment("The chance a guild's party clears the gate it went into (otherwise it pulls out and the gate is open again).")
            .defineInRange("rivals.guildSuccess", 0.85, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue INDEPENDENT_SUCCESS = B
            .comment("The same for a band of independents.")
            .defineInRange("rivals.independentSuccess", 0.7, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue FOLLOW_IN_SCALE = B
            .comment("Scales each guild's nerve: the chance its party follows a hunter into a gate they reached first instead of moving on.")
            .defineInRange("rivals.followInScale", 1.0, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue RANKED_LEADER = B
            .comment("The chance a party is led by one of the hunters on the national rankings, when one of the gate's rank is free.")
            .defineInRange("rivals.rankedLeaderChance", 0.5, 0.0, 1.0);
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> TRESPASS_MERIT = B
            .comment("Association merit lost for forcing your way into a gate a rival party holds, by the gate's rank, E to S.")
            .defineList("rivals.trespassMerit", List.of(2, 4, 6, 10, 16, 24), () -> 2, o -> o instanceof Integer i && i >= 0);

    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> PORTER_FEE = B
            .comment("What a porter asks for a job, E and D.")
            .defineList("hire.porterFee", List.of(40, 90), () -> 40, o -> o instanceof Integer i && i >= 0);
    public static final ModConfigSpec.DoubleValue HUNTER_FEE = B
            .comment("What a freelance hunter asks for a job: this times the gold a clear of their rank pays (3% more for each level into the rank).")
            .defineInRange("hire.hunterFee", 0.6, 0.0, 100.0);
    public static final ModConfigSpec.IntValue MAX_HUNTERS = B
            .comment("Freelance hunters a hunter may have hired at once (and one porter).")
            .defineInRange("hire.maxHunters", 2, 0, 6);
    public static final ModConfigSpec.IntValue HIRE_MINUTES = B
            .comment("Minutes a job lasts at most: hired hands also leave once their hunter walks out of a gate they cleared together.")
            .defineInRange("hire.minutes", 20, 1, 1440);
    public static final ModConfigSpec.IntValue OFFERS = B
            .comment("Freelance hunters on an office's list each day (two porters as well).")
            .defineInRange("hire.offers", 4, 0, 8);
    public static final ModConfigSpec.IntValue PORTER_SLOTS = B
            .comment("Stacks a porter's pack holds.")
            .defineInRange("hire.porterSlots", 27, 9, 54);

    public static final ModConfigSpec SPEC = B.build();

    private static final List<Integer> CLEAR_D = List.of(3, 4, 5, 6, 8, 10), TRESPASS_D = List.of(2, 4, 6, 10, 16, 24),
            PORTER_D = List.of(40, 90);

    // safe reads: the config is not loaded in every context (early game tests)

    private static <T> T get(Supplier<T> value, T fallback) {
        try {
            T v = value.get();
            return v == null ? fallback : v;
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    private static int at(ModConfigSpec.ConfigValue<List<? extends Integer>> list, List<Integer> fallback, int i) {
        List<? extends Integer> l = get(list::get, fallback);
        if (l.isEmpty()) l = fallback;
        Integer v = i < l.size() ? l.get(i) : l.get(l.size() - 1);
        return v == null ? 0 : v;
    }

    public static double dispatchChance() { return get(DISPATCH_CHANCE::get, 0.35); }
    public static int etaMinTicks() { return get(ETA_MIN::get, 75) * 20; }
    public static int etaMaxTicks() { return Math.max(etaMinTicks(), get(ETA_MAX::get, 150) * 20); }
    public static int gatherTicks() { return get(GATHER_SECONDS::get, 10) * 20; }
    public static int clearTicks(PlayerRank r) { return at(CLEAR_MINUTES, CLEAR_D, r.tier()) * 1200; }
    public static double guildSuccess() { return get(GUILD_SUCCESS::get, 0.85); }
    public static double independentSuccess() { return get(INDEPENDENT_SUCCESS::get, 0.7); }
    public static double followInScale() { return get(FOLLOW_IN_SCALE::get, 1.0); }
    public static double rankedLeader() { return get(RANKED_LEADER::get, 0.5); }
    public static int trespassMerit(PlayerRank r) { return at(TRESPASS_MERIT, TRESPASS_D, r.tier()); }

    public static long porterFee(PlayerRank r) { return at(PORTER_FEE, PORTER_D, Math.min(1, r.tier())); }
    public static double hunterFee() { return get(HUNTER_FEE::get, 0.6); }
    public static int maxHunters() { return get(MAX_HUNTERS::get, 2); }
    public static int hireTicks() { return get(HIRE_MINUTES::get, 20) * 1200; }
    public static int offers() { return get(OFFERS::get, 4); }
    public static int porterSlots() { return get(PORTER_SLOTS::get, 27); }

    private RivalConfig() {}
}
