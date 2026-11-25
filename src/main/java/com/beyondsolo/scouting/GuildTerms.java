package com.beyondsolo.scouting;

import com.beyondsolo.rival.RivalGuild;
import com.beyondsolo.system.PlayerRank;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * How each great guild deals with a hunter it wants: the rank from which it sends a scout, how much it puts on the
 * table (a factor on the signing bonus), the share of the hunter's gate gold it takes while they are under contract,
 * what being one of theirs brings, and its patience with a hunter who holds out for more (the chance it walks away
 * the first time). Red Lantern scouts anyone hungry from rank C and pays the most up front, taking the most after;
 * Azure Tide, the guild of the nation's strongest hunter, only comes for an S-rank.
 */
public record GuildTerms(RivalGuild guild, PlayerRank from, double factor, float cut, Perk perk, double patience) {
    /** What a contract brings on top of the bonus. */
    public enum Perk {
        /** Nothing: the money is up front. */
        NONE,
        /** More System XP ({@code scouting.xpPerk}). */
        XP,
        /** Hired hands at the association cost less ({@code scouting.hirePerk}). */
        HIRES,
        /** More association merit ({@code scouting.meritPerk}). */
        MERIT
    }

    public static final List<GuildTerms> ALL = List.of(
            new GuildTerms(RivalGuild.RED_LANTERN, PlayerRank.C, 1.35, 0.20f, Perk.NONE, 0.30),
            new GuildTerms(RivalGuild.IRON_BASTION, PlayerRank.B, 1.0, 0.15f, Perk.HIRES, 0.10),
            new GuildTerms(RivalGuild.SUNFALL, PlayerRank.A, 1.1, 0.12f, Perk.MERIT, 0.18),
            new GuildTerms(RivalGuild.AZURE_TIDE, PlayerRank.S, 1.2, 0.10f, Perk.XP, 0.20));

    /** The terms of a great guild, or null (independents sign nobody). */
    @Nullable
    public static GuildTerms of(RivalGuild guild) {
        for (GuildTerms t : ALL) if (t.guild == guild) return t;
        return null;
    }

    /** The guilds that want a hunter of this rank, the hungriest (the lowest {@link #from}) first. */
    public static List<GuildTerms> interested(PlayerRank rank) {
        List<GuildTerms> out = new ArrayList<>();
        for (GuildTerms t : ALL) if (rank.tier() >= t.from.tier()) out.add(t);
        return out;
    }
}
