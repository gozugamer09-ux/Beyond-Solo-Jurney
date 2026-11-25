package com.beyondsolo.scouting;

import com.beyondsolo.system.PlayerRank;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.function.Supplier;

/**
 * The great guilds' scouts, their offers and contracts, and the hunter news (config/beyondsolo-scouting.toml). The final
 * balancing pass made signing a bet rather than free money: a bonus of six gates' gold for a fourteen-day contract, so
 * a hunter who runs gates hard pays most of it back in the guild's cut, and one who runs few comes out ahead.
 */
public final class ScoutingConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue BONUS_GATES = B
            .comment("A signing bonus is worth this many clears of the hunter's rank (times the guild's own factor, a little more or less).",
                    "Named apart from the old bonusGates (10) so worlds made before the final balancing pass take the new value.")
            .defineInRange("scouting.signingBonusGates", 6.0, 0.0, 1000.0);
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> CEILING = B
            .comment("How far a guild will go when the hunter holds out: its first offer times this, for a hunter of each rank, E to S.")
            .defineList("scouting.ceiling", List.of(1.3, 1.3, 1.4, 1.4, 1.6, 2.0), () -> 1.4, o -> o instanceof Double d && d >= 1.0);
    public static final ModConfigSpec.DoubleValue RAISE_MIN = B
            .comment("When a hunter holds out (and no rival guild offers more), the guild raises its offer by at least this share...")
            .defineInRange("scouting.raiseMin", 0.1, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue RAISE_MAX = B
            .comment("... and at most this share.")
            .defineInRange("scouting.raiseMax", 0.2, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue WALK_AWAY_STEP = B
            .comment("Each time a hunter holds out with no better offer on the table, the chance the guild walks away grows by this",
                    "(on top of the guild's own patience).")
            .defineInRange("scouting.walkAwayStep", 0.12, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue DECLINE_RAISE = B
            .comment("When a hunter turns one guild down, the other guilds' offers on the table go up by this share.")
            .defineInRange("scouting.declineRaise", 0.05, 0.0, 10.0);
    public static final ModConfigSpec.IntValue FIRST_SCOUT_SECONDS = B
            .comment("Seconds after a rank-up before the first guild's offer comes.")
            .defineInRange("scouting.firstScoutSeconds", 45, 0, 3600);
    public static final ModConfigSpec.IntValue SCOUT_GAP_SECONDS = B
            .comment("Seconds between one guild's offer and the next.")
            .defineInRange("scouting.scoutGapSeconds", 40, 0, 3600);
    public static final ModConfigSpec.IntValue OFFER_MINUTES = B
            .comment("Minutes an offer stays on the table before it lapses.")
            .defineInRange("scouting.offerMinutes", 20, 1, 1440);
    public static final ModConfigSpec.IntValue SCOUT_WAIT_SECONDS = B
            .comment("Seconds a scout waits by the hunter for an answer before walking off (the offer stays on the table).")
            .defineInRange("scouting.scoutWaitSeconds", 150, 10, 3600);
    public static final ModConfigSpec.IntValue CONTRACT_DAYS = B
            .comment("Days (the world's) a contract with a guild runs.",
                    "Named apart from the old contractDays (7) so worlds made before the final balancing pass take the new value.")
            .defineInRange("scouting.contractWorldDays", 14, 1, 365);
    public static final ModConfigSpec.DoubleValue RENEWAL = B
            .comment("A guild whose contract ran its course offers to renew it: a fresh offer for the hunter's rank times this.")
            .defineInRange("scouting.renewal", 1.1, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue XP_PERK = B
            .comment("Azure Tide's contract hunters earn this much more System XP.")
            .defineInRange("scouting.xpPerk", 0.05, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue HIRE_PERK = B
            .comment("Iron Bastion's contract hunters pay this much less for hired hands at the association.")
            .defineInRange("scouting.hirePerk", 0.3, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue MERIT_PERK = B
            .comment("Sunfall's contract hunters earn this much more association merit.")
            .defineInRange("scouting.meritPerk", 0.15, 0.0, 10.0);

    public static final ModConfigSpec.IntValue NEWS_KEEP = B
            .comment("Headlines the hunter news keeps (the oldest go first).")
            .defineInRange("news.keep", 40, 5, 500);
    public static final ModConfigSpec.IntValue NEWS_AMBIENT_MINUTES = B
            .comment("Minutes between the headlines about the world's other hunters and guilds (0: none).")
            .defineInRange("news.ambientMinutes", 25, 0, 1440);

    public static final ModConfigSpec SPEC = B.build();

    private static final List<Double> CEILING_D = List.of(1.3, 1.3, 1.4, 1.4, 1.6, 2.0);

    // safe reads: the config is not loaded in every context (early game tests)

    private static <T> T get(Supplier<T> value, T fallback) {
        try {
            T v = value.get();
            return v == null ? fallback : v;
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    public static double bonusGates() { return get(BONUS_GATES::get, 6.0); }

    public static double ceiling(PlayerRank r) {
        List<? extends Double> l = get(CEILING::get, CEILING_D);
        if (l.isEmpty()) l = CEILING_D;
        Double v = r.tier() < l.size() ? l.get(r.tier()) : l.get(l.size() - 1);
        return v == null ? 1.4 : Math.max(1.0, v);
    }

    public static double raiseMin() { return get(RAISE_MIN::get, 0.1); }
    public static double raiseMax() { return Math.max(raiseMin(), get(RAISE_MAX::get, 0.2)); }
    public static double walkAwayStep() { return get(WALK_AWAY_STEP::get, 0.12); }
    public static double declineRaise() { return get(DECLINE_RAISE::get, 0.05); }
    public static int firstScoutTicks() { return get(FIRST_SCOUT_SECONDS::get, 45) * 20; }
    public static int scoutGapTicks() { return get(SCOUT_GAP_SECONDS::get, 40) * 20; }
    public static int offerTicks() { return get(OFFER_MINUTES::get, 20) * 1200; }
    public static int scoutWaitTicks() { return get(SCOUT_WAIT_SECONDS::get, 150) * 20; }
    public static int contractDays() { return get(CONTRACT_DAYS::get, 14); }
    public static double renewal() { return get(RENEWAL::get, 1.1); }
    public static double xpPerk() { return get(XP_PERK::get, 0.05); }
    public static double hirePerk() { return get(HIRE_PERK::get, 0.3); }
    public static double meritPerk() { return get(MERIT_PERK::get, 0.15); }
    public static int newsKeep() { return get(NEWS_KEEP::get, 40); }
    public static int newsAmbientTicks() { return get(NEWS_AMBIENT_MINUTES::get, 25) * 1200; }

    private ScoutingConfig() {}
}
