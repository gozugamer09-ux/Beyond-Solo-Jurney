package com.beyondsolo.tower;

import com.beyondsolo.gate.BossKind;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

import java.util.Locale;

/**
 * What every floor of the Crimson Citadel holds, worked out from its number alone (no world needed, so tests and the
 * System window ask the same table the climb uses).
 *
 * <ul>
 *   <li>Floors 1 to {@link #TOP} are the castle: ten wings of ten floors, each wing its own hall style. A gatekeeper
 *       guards every tenth floor (beating one is a checkpoint) and the lord waits on the last.</li>
 *   <li>Past the castle the stair climbs on into the Endless Spire: the wings come round again, every floor a little
 *       stronger than the last ({@link #healthScale}, {@link #damageScale}), with a gatekeeper every ten floors.</li>
 *   <li>Between gatekeepers: ordinary floors (clear the hall, two to four waves), elite floors on the fives (a
 *       champion and its elites) and siege floors on the sevens (hold the hall while it keeps sending more).</li>
 * </ul>
 *
 * <p>All numbers are first guesses for the balancing pass.</p>
 */
public final class TowerFloors {
    /** The castle's last floor: the lord's. */
    public static final int TOP = 100;
    public static final int KEEPER_EVERY = 10;
    /** The first floor of the Endless Spire. */
    public static final int SPIRE = TOP + 1;
    /** How long a siege floor must be held (ticks). */
    public static final int SIEGE_TICKS = 20 * 40;

    public enum Kind {
        /** Clear the hall: a few waves. */
        BATTLE,
        /** A champion and its elites. */
        ELITE,
        /** Hold the hall until the time runs out. */
        SIEGE,
        /** A gatekeeper: one of the gate bosses, named, on every tenth floor. */
        KEEPER,
        /** The lord of the Citadel, on its last floor. */
        LORD;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The ten wings of the castle, each with its own look ({@code CitadelHalls}). */
    public enum Wing {
        OUTER_GATE, BARRACKS, OSSUARY, FORGE, ARCHIVE, CLOISTER, GARDEN, COURT, RAMPARTS, THRONE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public Component title() {
            return Component.translatable("tower.beyondsolo.wing." + id());
        }
    }

    /** The castle's gatekeepers, floors 10 to 90: which boss, ascended or not. */
    private static final BossKind[] KEEPERS = {BossKind.ALPHA, BossKind.BRUTE, BossKind.BROODMOTHER, BossKind.KNIGHT, BossKind.MAGUS,
            BossKind.ALPHA, BossKind.BROODMOTHER, BossKind.KNIGHT, BossKind.WYRM};

    public static boolean endless(int floor) {
        return floor > TOP;
    }

    public static Kind kind(int floor) {
        if (floor == TOP) return Kind.LORD;
        if (floor % KEEPER_EVERY == 0) return Kind.KEEPER;
        if (floor % KEEPER_EVERY == 5) return Kind.ELITE;
        if (floor % KEEPER_EVERY == 7) return Kind.SIEGE;
        return Kind.BATTLE;
    }

    /** Which wing a floor is in. The spire goes round the wings again, ten floors each. */
    public static Wing wing(int floor) {
        int f = Math.max(1, floor);
        int i = endless(f) ? ((f - SPIRE) / KEEPER_EVERY) % 10 : Math.min(9, (f - 1) / KEEPER_EVERY);
        return Wing.values()[i];
    }

    /** The rank of a floor's creatures and gatekeeper. */
    public static PlayerRank rank(int floor) {
        if (floor <= 10) return PlayerRank.E;
        if (floor <= 20) return PlayerRank.D;
        if (floor <= 35) return PlayerRank.C;
        if (floor <= 50) return PlayerRank.B;
        if (floor <= 70) return PlayerRank.A;
        return PlayerRank.S;
    }

    /** How much tougher than their rank the spire's creatures are (1 in the castle). */
    public static float healthScale(int floor) {
        return endless(floor) ? 1f + 0.05f * (floor - TOP) : 1f;
    }

    public static float damageScale(int floor) {
        return endless(floor) ? 1f + 0.03f * (floor - TOP) : 1f;
    }

    /** Waves on an ordinary floor. */
    public static int waves(int floor) {
        return 2 + (floor >= 30 ? 1 : 0) + (floor >= 70 ? 1 : 0);
    }

    /** Creatures in one wave. */
    public static int waveSize(int floor) {
        return Math.min(8, 3 + floor / 15);
    }

    /** Creatures a siege keeps in the hall at once. */
    public static int siegeCrowd(int floor) {
        return Math.min(9, 4 + floor / 20);
    }

    /** A creature on this floor rolls a leader's variant this often (veterans, elites, champions climb with the floors). */
    public static float leaderChance(int floor) {
        return Math.min(0.45f, 0.08f + floor * 0.0035f);
    }

    /** The boss guarding a gatekeeper floor. */
    public static BossKind keeper(int floor) {
        if (floor == TOP) return BossKind.KNIGHT;
        if (!endless(floor)) return KEEPERS[Math.max(0, Math.min(KEEPERS.length - 1, floor / KEEPER_EVERY - 1))];
        return BossKind.values()[RandomSource.create(floor * 7919L + 0xC17ADE1L).nextInt(BossKind.values().length)];
    }

    public static boolean keeperAscended(int floor) {
        return endless(floor) || floor >= 60;
    }

    /** A castle gatekeeper's own name ("Garmhal, Hound of the Outer Gate"); the spire's go by their kind's name. */
    public static Component keeperName(int floor) {
        if (floor == TOP) return Component.translatable("tower.beyondsolo.lord.name");
        if (!endless(floor)) return Component.translatable("tower.beyondsolo.keeper." + floor);
        return keeper(floor).name(rank(floor));
    }

    /** The line under a gatekeeper's name: "Gatekeeper of the 30th Floor". */
    public static Component keeperTitle(int floor) {
        if (floor == TOP) return Component.translatable("tower.beyondsolo.lord.title");
        return Component.translatable("tower.beyondsolo.keeper.title", floor);
    }

    /** The checkpoint a floor's climb is safe from once its gatekeeper is beaten (castle floors only). */
    public static int checkpointOf(int floor) {
        return floor - floor % KEEPER_EVERY;
    }

    /** System XP for clearing a floor (a gatekeeper's is a whole gate's worth, the lord's three S-rank gates'). */
    public static long xp(int floor) {
        PlayerRank r = rank(floor);
        long gate = 300L * (1L + r.tier()) * (1L + r.tier());
        return switch (kind(floor)) {
            case LORD -> gate * 3;
            case KEEPER -> Math.round(gate * healthScale(floor));
            case ELITE, SIEGE -> Math.round(gate / 8.0 * healthScale(floor));
            default -> Math.round(gate / 12.0 * healthScale(floor));
        };
    }

    /** Gold for clearing a floor. */
    public static long gold(int floor) {
        long gate = com.beyondsolo.economy.EconomyHooks.GATE_GOLD[rank(floor).tier()];
        return switch (kind(floor)) {
            case LORD -> gate * 3;
            case KEEPER -> Math.round(gate * healthScale(floor));
            case ELITE, SIEGE -> Math.round(gate / 6.0 * healthScale(floor));
            default -> Math.round(gate / 10.0 * healthScale(floor));
        };
    }

    /** "Floor 37" with an ordinal where the language wants one. */
    public static Component floorLabel(int floor) {
        return Component.translatable("tower.beyondsolo.floor", floor);
    }

    private TowerFloors() {}
}
