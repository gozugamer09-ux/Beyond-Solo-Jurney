package com.beyondsolo.guild;

import com.beyondsolo.economy.Economy;
import com.beyondsolo.economy.EconomyHooks;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.server.level.ServerPlayer;

/**
 * What a guild's level gives (all from {@link GuildConfig}, first guesses for the balancing pass): room for more
 * members and hired hunters, a little more gold and System XP for every member, raid permits of higher ranks and a
 * discount on them. Level 1 to {@link GuildConfig#MAX_LEVEL}; the XP for each level grows as {@code n^1.7}.
 */
public final class GuildPerks {
    /** The guild level a raid permit of each rank E D C B A S needs. */
    private static final int[] PERMIT_LEVEL = {1, 1, 1, 3, 5, 7};

    public static int memberCap(int level) {
        return GuildConfig.BASE_MEMBERS.get() + GuildConfig.MEMBERS_PER_LEVEL.get() * (clamp(level) - 1);
    }

    /** Hunters a guild can keep hired: its base, one more every second level, and one more at the top. */
    public static int rosterCap(int level) {
        int l = clamp(level);
        return GuildConfig.BASE_ROSTER.get() + (l - 1) / 2 + (l >= GuildConfig.MAX_LEVEL ? 1 : 0);
    }

    public static double goldBonus(int level) {
        return GuildConfig.GOLD_PER_LEVEL.get() * (clamp(level) - 1);
    }

    public static double xpBonus(int level) {
        return GuildConfig.XP_PER_LEVEL.get() * (clamp(level) - 1);
    }

    /** The guild level a raid permit of this rank needs. */
    public static int permitLevel(PlayerRank rank) {
        return PERMIT_LEVEL[rank.ordinal()];
    }

    /** The highest rank of raid permit a guild of this level may request. */
    public static PlayerRank permitRank(int level) {
        PlayerRank best = PlayerRank.E;
        for (PlayerRank r : PlayerRank.values()) if (clamp(level) >= permitLevel(r)) best = r;
        return best;
    }

    public static double permitDiscount(int level) {
        return Math.min(0.9, GuildConfig.PERMIT_DISCOUNT.get() * (clamp(level) - 1));
    }

    /** Gold from the treasury a raid permit of this rank costs a guild of this level. */
    public static long permitPrice(int level, PlayerRank rank) {
        double base = EconomyHooks.GATE_GOLD[rank.tier()] * (double) GuildConfig.PERMIT_PRICE.get();
        return Math.max(0, Math.round(base * (1 - permitDiscount(level))));
    }

    /** Guild XP from this level to the next (0 at the top). */
    public static long xpToNext(int level) {
        if (level >= GuildConfig.MAX_LEVEL) return 0;
        double raw = GuildConfig.LEVEL_XP.get() * Math.pow(Math.max(1, level), 1.7);
        return Math.max(1, Math.round(raw / 50.0) * 50);
    }

    /** Guild XP for a member's clear of a gate of this rank. */
    public static long gateXp(PlayerRank rank) {
        long t = rank.tier() + 1L;
        return GuildConfig.GATE_XP.get() * t * t;
    }

    /** Guild XP for each member in a raid of this rank that was cleared. */
    public static long raidXp(PlayerRank rank) {
        return gateXp(rank) * GuildConfig.RAID_XP_MULTIPLIER.get();
    }

    /** The guild's gold perk on gold a member earns (kills, quests, gates, raids and the Citadel only). */
    public static long gold(ServerPlayer p, long amount, Economy.Reason reason) {
        if (amount <= 0) return amount;
        if (reason != Economy.Reason.KILL && reason != Economy.Reason.QUEST && reason != Economy.Reason.GATE
                && reason != Economy.Reason.TOWER && reason != Economy.Reason.RAID) return amount;
        Guild g = Guilds.of(p);
        if (g == null) return amount;
        double bonus = goldBonus(g.level());
        return bonus <= 0 ? amount : Math.round(amount * (1.0 + bonus));
    }

    private static int clamp(int level) {
        return Math.max(1, Math.min(GuildConfig.MAX_LEVEL, level));
    }

    private GuildPerks() {}
}
