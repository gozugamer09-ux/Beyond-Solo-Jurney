package com.beyondsolo.guild;

import com.beyondsolo.system.Balance;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.UUID;

/**
 * The hunters looking for a guild: every day each guild sees a fresh few ({@code hunters.recruits}), drawn from its id
 * and the day so everyone in it sees the same faces. A higher guild draws stronger hunters (rank weights per level
 * below), and they ask more the stronger they are ({@code hunters.hireCost}). Names are invented, never anyone's.
 */
public final class Recruits {
    private static final String[] FIRST = {
            "Aren", "Bora", "Caius", "Dahlia", "Eskel", "Fenna", "Garrick", "Hollis", "Ilsa", "Jarek", "Kesh", "Liora",
            "Maren", "Nox", "Orrin", "Pella", "Quinn", "Rhen", "Seren", "Tamsin", "Ulric", "Vesna", "Wren", "Yara",
            "Zarek", "Ilya", "Rook", "Tessa", "Doran", "Kaia", "Edda", "Mika", "Juno", "Haldor", "Lethe", "Brannoc",
            "Nadia", "Odalys", "Reva", "Sigrun", "Talon", "Vey", "Corin", "Elsbeth", "Faolan", "Greer", "Ines", "Lark"};
    private static final String[] LAST = {
            "Ashgrove", "Blackwell", "Cinderfall", "Dawnmere", "Emberly", "Frostmantle", "Greaves", "Hollowell", "Ironside",
            "Kade", "Larkspur", "Moorcroft", "Nightingale", "Oakhart", "Pyrewood", "Quarrel", "Ravenscar", "Stonebrook",
            "Thornwick", "Vale", "Winterbourne", "Yarrow", "Brightwater", "Coldharbor", "Duskwood", "Fairhallow", "Gloam",
            "Harrow", "Kestrel", "Lowmoor", "Marrow", "Northcote", "Redfern", "Saltmarsh", "Underhill", "Wolfsbane"};

    /** Rank weights E D C B A S of the recruit pool, for guild levels 1-2, 3-4, 5-6, 7-8 and 9-10. */
    private static final int[][] WEIGHTS = {
            {55, 35, 10, 0, 0, 0},
            {25, 40, 25, 10, 0, 0},
            {5, 25, 35, 25, 10, 0},
            {0, 10, 25, 35, 22, 8},
            {0, 0, 15, 35, 35, 15}};

    /** Today's pool for a guild (drawn the first time it is asked for on a day). */
    static List<GuildHunter> pool(Guild g, long day) {
        if (g.recruitDay != day) {
            g.recruits.clear();
            RandomSource r = RandomSource.create(g.id().getMostSignificantBits() ^ g.id().getLeastSignificantBits() ^ (day * 0x9E3779B97F4A7C15L));
            int n = GuildConfig.RECRUITS.get();
            for (int i = 0; i < n; i++) g.recruits.add(roll(r, g.level()));
            g.recruitDay = day;
        }
        return g.recruits;
    }

    /** A new hunter looking for a guild of this level. */
    public static GuildHunter roll(RandomSource r, int guildLevel) {
        PlayerRank rank = rollRank(r, guildLevel);
        int from = bandStart(rank), to = bandEnd(rank);
        // most of them near the start of their rank's band
        int level = from + (int) Math.floor(Math.pow(r.nextDouble(), 1.6) * (to - from + 1));
        level = Math.min(to, level);
        PlayerClass cls = PlayerClass.values()[r.nextInt(PlayerClass.values().length)];
        return new GuildHunter(new UUID(r.nextLong(), r.nextLong()), name(r), cls, level, r.nextInt(GuildHunter.SKINS.length), price(level));
    }

    /** A hunter of a given rank and class at the start of the rank (commands, tests). */
    public static GuildHunter of(RandomSource r, PlayerRank rank, PlayerClass cls) {
        int level = bandStart(rank) + 1;
        return new GuildHunter(new UUID(r.nextLong(), r.nextLong()), name(r), cls, level, r.nextInt(GuildHunter.SKINS.length), price(level));
    }

    static PlayerRank rollRank(RandomSource r, int guildLevel) {
        int[] w = WEIGHTS[Math.max(0, Math.min(WEIGHTS.length - 1, (Math.max(1, guildLevel) - 1) / 2))];
        int total = 0;
        for (int v : w) total += v;
        int roll = r.nextInt(Math.max(1, total));
        for (int i = 0; i < w.length; i++) {
            roll -= w[i];
            if (roll < 0) return PlayerRank.values()[i];
        }
        return PlayerRank.E;
    }

    /** The first level a hunter of this rank has ({@link Balance#rankFor}: two under the rank's home level). */
    public static int bandStart(PlayerRank rank) {
        return rank == PlayerRank.E ? 1 : Balance.homeLevel(rank) - 2;
    }

    /** The last level of the rank before the next one starts (the level cap for S). */
    public static int bandEnd(PlayerRank rank) {
        PlayerRank[] all = PlayerRank.values();
        return rank.ordinal() + 1 < all.length ? bandStart(all[rank.ordinal() + 1]) - 1 : Balance.MAX_LEVEL;
    }

    /** What a hunter of this level asks: their rank's price, and 3% more for each level into the rank. */
    public static long price(int level) {
        PlayerRank rank = Balance.rankFor(level);
        double base = GuildConfig.hireCost(rank.tier());
        return Math.round(base * (1 + 0.03 * Math.max(0, level - bandStart(rank))) / 10.0) * 10;
    }

    public static String name(RandomSource r) {
        return FIRST[r.nextInt(FIRST.length)] + " " + LAST[r.nextInt(LAST.length)];
    }

    private Recruits() {}
}
