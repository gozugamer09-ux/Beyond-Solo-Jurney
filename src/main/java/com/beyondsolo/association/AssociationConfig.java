package com.beyondsolo.association;

import com.beyondsolo.system.PlayerRank;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.function.Supplier;

/** The Hunter Association and the gate broker (config/beyondsolo-association.toml). Numbers are first guesses for the balancing pass. */
public final class AssociationConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue POSTS = B
            .comment("An association officer and a gate broker keep a post at every Evaluation Zone (villages from older worlds too).",
                    "Off: none come (the ones already there stay).")
            .define("association.posts", true);
    public static final ModConfigSpec.IntValue WATCH_RADIUS = B
            .comment("How far (blocks) from an office the association keeps watch: the gates its contract board lists.")
            .defineInRange("association.watchRadius", 512, 32, 8192);
    public static final ModConfigSpec.IntValue BOARD_SIZE = B
            .comment("Contracts on an office's board at most.")
            .defineInRange("association.boardSize", 6, 1, 12);
    public static final ModConfigSpec.IntValue MAX_CONTRACTS = B
            .comment("Contracts a hunter may hold at once (one more from the Recognized standing).")
            .defineInRange("association.maxContracts", 2, 1, 8);
    public static final ModConfigSpec.DoubleValue CONTRACT_GOLD = B
            .comment("A contract's bounty: this times the gold a clear of its rank pays (paid on top of the clear).")
            .defineInRange("association.contractGold", 0.6, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue UNSTABLE_BONUS = B
            .comment("Bounty and merit for a gate that is coming apart (its collapse timer showing) are this many times more.")
            .defineInRange("association.unstableBonus", 1.5, 1.0, 100.0);
    public static final ModConfigSpec.DoubleValue BREAK_BONUS = B
            .comment("Bounty and merit for a gate that has broken (its monsters out) are this many times more.")
            .defineInRange("association.breakBonus", 2.5, 1.0, 100.0);
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> CLEAR_MERIT = B
            .comment("Merit for clearing a gate, E to S (any gate, contract or not).")
            .defineList("association.clearMerit", List.of(1, 2, 3, 5, 8, 12), () -> 1, o -> o instanceof Integer i && i >= 0);
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> CONTRACT_MERIT = B
            .comment("Merit for a contract done (or a permit's gate cleared), E to S.")
            .defineList("association.contractMerit", List.of(3, 5, 8, 12, 18, 25), () -> 1, o -> o instanceof Integer i && i >= 0);
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> BREAK_MERIT = B
            .comment("Merit for having a share in putting a dungeon break down, E to S.")
            .defineList("association.breakMerit", List.of(5, 8, 12, 18, 25, 35), () -> 1, o -> o instanceof Integer i && i >= 0);
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> EXAM_MERIT = B
            .comment("Merit for passing the rank exam, by the rank it certifies, E to S.")
            .defineList("association.examMerit", List.of(0, 15, 30, 50, 80, 120), () -> 0, o -> o instanceof Integer i && i >= 0);
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> STANDINGS = B
            .comment("Merit for each standing above Registered: Recognized, Trusted, Distinguished, Elite, National.")
            .defineList("association.standings", List.of(50, 200, 600, 1500, 4000), () -> 100, o -> o instanceof Integer i && i > 0);
    public static final ModConfigSpec.IntValue PERMITS_PER_DAY = B
            .comment("Gate permits a hunter may take a day (one more from the Distinguished standing).")
            .defineInRange("association.permitsPerDay", 2, 0, 100);
    public static final ModConfigSpec.DoubleValue PERMIT_FEE = B
            .comment("A permit's fee: this times the gold a clear of its rank pays.")
            .defineInRange("association.permitFee", 0.4, 0.0, 100.0);
    public static final ModConfigSpec.IntValue PERMIT_REACH = B
            .comment("How many ranks above their own a hunter may take a permit for.")
            .defineInRange("association.permitReach", 1, 0, 5);
    public static final ModConfigSpec.DoubleValue EXAM_FEE = B
            .comment("The rank exam's fee: this times the gold a clear of the rank sought pays.")
            .defineInRange("association.examFee", 1.0, 0.0, 100.0);
    public static final ModConfigSpec.IntValue EXAM_MINUTES = B
            .comment("Minutes to clear the exam gate.")
            .defineInRange("association.examMinutes", 25, 1, 1000);
    public static final ModConfigSpec.IntValue EXAM_RETRY_DAYS = B
            .comment("In-game days after a failed exam before the next.")
            .defineInRange("association.examRetryDays", 1, 0, 100);
    public static final ModConfigSpec.DoubleValue TIP_PRICE = B
            .comment("The broker's price for a gate's whereabouts: this times the gold a clear of its rank pays.")
            .defineInRange("broker.tipPrice", 1.25, 0.0, 100.0);
    public static final ModConfigSpec.IntValue TIP_RANGE = B
            .comment("How far (blocks) from the broker the gates they know of may be.")
            .defineInRange("broker.tipRange", 1500, 64, 30000);
    public static final ModConfigSpec.IntValue TIPS_PER_DAY = B
            .comment("Gates a hunter may buy the whereabouts of a day.")
            .defineInRange("broker.tipsPerDay", 4, 0, 100);
    public static final ModConfigSpec.DoubleValue SCOUT_PRICE = B
            .comment("The broker's price for a scouting report on a gate: this times the gold a clear of its rank pays.")
            .defineInRange("broker.scoutPrice", 0.35, 0.0, 100.0);
    public static final ModConfigSpec.IntValue SCOUT_RANGE = B
            .comment("How far (blocks) from the broker a gate may be to be scouted.")
            .defineInRange("broker.scoutRange", 400, 16, 30000);

    public static final ModConfigSpec SPEC = B.build();

    private static final List<Integer> CLEAR_D = List.of(1, 2, 3, 5, 8, 12), CONTRACT_D = List.of(3, 5, 8, 12, 18, 25),
            BREAK_D = List.of(5, 8, 12, 18, 25, 35), EXAM_D = List.of(0, 15, 30, 50, 80, 120), STANDINGS_D = List.of(50, 200, 600, 1500, 4000);

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

    public static boolean posts() { return get(POSTS::get, true); }
    public static int watchRadius() { return get(WATCH_RADIUS::get, 512); }
    public static int boardSize() { return get(BOARD_SIZE::get, 6); }
    public static int maxContracts() { return get(MAX_CONTRACTS::get, 2); }
    public static double contractGold() { return get(CONTRACT_GOLD::get, 0.6); }
    public static double unstableBonus() { return get(UNSTABLE_BONUS::get, 1.5); }
    public static double breakBonus() { return get(BREAK_BONUS::get, 2.5); }
    public static int clearMerit(PlayerRank r) { return at(CLEAR_MERIT, CLEAR_D, r.tier()); }
    public static int contractMerit(PlayerRank r) { return at(CONTRACT_MERIT, CONTRACT_D, r.tier()); }
    public static int breakMerit(PlayerRank r) { return at(BREAK_MERIT, BREAK_D, r.tier()); }
    public static int examMerit(PlayerRank r) { return at(EXAM_MERIT, EXAM_D, r.tier()); }

    /** Merit for standing {@code s} (1 Recognized .. 5 National); 0 for Registered. */
    public static int standing(int s) {
        return s <= 0 ? 0 : at(STANDINGS, STANDINGS_D, s - 1);
    }

    public static int permitsPerDay() { return get(PERMITS_PER_DAY::get, 2); }
    public static double permitFee() { return get(PERMIT_FEE::get, 0.4); }
    public static int permitReach() { return get(PERMIT_REACH::get, 1); }
    public static double examFee() { return get(EXAM_FEE::get, 1.0); }
    public static int examTicks() { return get(EXAM_MINUTES::get, 25) * 1200; }
    public static int examRetryDays() { return get(EXAM_RETRY_DAYS::get, 1); }
    public static double tipPrice() { return get(TIP_PRICE::get, 1.25); }
    public static int tipRange() { return get(TIP_RANGE::get, 1500); }
    public static int tipsPerDay() { return get(TIPS_PER_DAY::get, 4); }
    public static double scoutPrice() { return get(SCOUT_PRICE::get, 0.35); }
    public static int scoutRange() { return get(SCOUT_RANGE::get, 400); }

    private AssociationConfig() {}
}
