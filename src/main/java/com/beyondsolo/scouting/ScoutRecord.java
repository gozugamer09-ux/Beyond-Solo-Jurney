package com.beyondsolo.scouting;

import com.beyondsolo.rival.RivalGuild;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A hunter's dealings with the great guilds ({@code beyondsolo:scouting}, kept through death): the offers on the table
 * (and the ones on their way), the contract they are under, the rank each guild last came for, and the round of
 * offers a rank-up brought (which guilds came, which were turned down).
 */
public final class ScoutRecord implements INBTSerializable<CompoundTag> {
    /** An offer from a guild: a signing bonus for a contract of {@link #days} days, the guild taking {@link #cut} of gate gold. */
    public static final class Offer {
        public final UUID id;
        public final RivalGuild guild;
        /** The rank the hunter had when the guild came for them. */
        public final PlayerRank rank;
        public long bonus;
        /** The bonus first put on the table, and the most the guild will go to. */
        public final long first, ceiling;
        public final float cut;
        public final int days;
        /** Times the hunter held out and the guild raised. */
        public int raises;
        /** The guild will not go higher. */
        public boolean last;
        /** When it is put to the hunter (a scout comes, or word of it), and when it lapses (0 until then). */
        public long openAt, lapseAt;
        /** The scout came in person; the hunter was told it is on the table. */
        public boolean visited, told;
        /** A guild whose contract ran its course, asking for another. */
        public final boolean renewal;
        /** The scout who brings it, and their look. */
        public final String scout;
        public final int look;

        public Offer(UUID id, RivalGuild guild, PlayerRank rank, long bonus, long ceiling, float cut, int days, long openAt,
                     boolean renewal, String scout, int look) {
            this(id, guild, rank, bonus, bonus, ceiling, cut, days, openAt, renewal, scout, look);
        }

        Offer(UUID id, RivalGuild guild, PlayerRank rank, long bonus, long first, long ceiling, float cut, int days, long openAt,
              boolean renewal, String scout, int look) {
            this.id = id;
            this.guild = guild;
            this.rank = rank;
            this.bonus = bonus;
            this.first = first;
            this.ceiling = ceiling;
            this.cut = cut;
            this.days = days;
            this.openAt = openAt;
            this.renewal = renewal;
            this.scout = scout;
            this.look = look;
        }

        /** On the table now (not still on its way). */
        public boolean open(long now) {
            return now >= openAt;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putString("guild", guild.name());
            t.putString("rank", rank.name());
            t.putLong("bonus", bonus);
            t.putLong("first", first);
            t.putLong("ceiling", ceiling);
            t.putFloat("cut", cut);
            t.putInt("days", days);
            t.putInt("raises", raises);
            t.putBoolean("last", last);
            t.putLong("openAt", openAt);
            t.putLong("lapseAt", lapseAt);
            t.putBoolean("visited", visited);
            t.putBoolean("told", told);
            t.putBoolean("renewal", renewal);
            t.putString("scout", scout);
            t.putInt("look", look);
            return t;
        }

        @Nullable
        static Offer load(CompoundTag t) {
            if (!t.hasUUID("id")) return null;
            RivalGuild guild = guildByName(t.getString("guild"));
            PlayerRank rank = PlayerRank.byName(t.getString("rank"));
            if (guild == null || rank == null) return null;
            Offer o = new Offer(t.getUUID("id"), guild, rank, t.getLong("bonus"), t.getLong("first"), t.getLong("ceiling"), t.getFloat("cut"),
                    t.getInt("days"), t.getLong("openAt"), t.getBoolean("renewal"), t.getString("scout"), t.getInt("look"));
            o.raises = t.getInt("raises");
            o.last = t.getBoolean("last");
            o.lapseAt = t.getLong("lapseAt");
            o.visited = t.getBoolean("visited");
            o.told = t.getBoolean("told");
            return o;
        }
    }

    /** A contract with a guild: what it paid, what it takes, and the days it runs (the world's days). */
    public record Contract(RivalGuild guild, PlayerRank rank, long bonus, float cut, long fromDay, long untilDay, long signedAt) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("guild", guild.name());
            t.putString("rank", rank.name());
            t.putLong("bonus", bonus);
            t.putFloat("cut", cut);
            t.putLong("fromDay", fromDay);
            t.putLong("untilDay", untilDay);
            t.putLong("signedAt", signedAt);
            return t;
        }

        @Nullable
        static Contract load(CompoundTag t) {
            RivalGuild guild = guildByName(t.getString("guild"));
            PlayerRank rank = PlayerRank.byName(t.getString("rank"));
            if (guild == null || rank == null) return null;
            return new Contract(guild, rank, t.getLong("bonus"), t.getFloat("cut"), t.getLong("fromDay"), t.getLong("untilDay"), t.getLong("signedAt"));
        }

        /** Days it has left (0 on its last day). */
        public long daysLeft(long day) {
            return Math.max(0, untilDay - day);
        }
    }

    final List<Offer> offers = new ArrayList<>();
    @Nullable Contract contract;
    /** Gate gold the guild has taken under the contract now running. */
    long paid;
    /** Contracts signed, offers turned down, offers raised by holding out, offers that lapsed, guilds that walked away. */
    int signed, declined, raised, lapsed, walked;
    /** The biggest signing bonus taken. */
    long best;
    /** The highest rank each guild came for (by the guild's ordinal; -1: never). */
    final int[] cameFor = new int[RivalGuild.values().length];
    /** The rank-up the offers on the table came with, the guilds that came, and those turned down. */
    @Nullable PlayerRank round;
    final Set<RivalGuild> roundGuilds = EnumSet.noneOf(RivalGuild.class);
    final Set<RivalGuild> roundDeclined = EnumSet.noneOf(RivalGuild.class);

    public ScoutRecord() {
        Arrays.fill(cameFor, -1);
    }

    public List<Offer> offers() { return offers; }
    @Nullable public Contract contract() { return contract; }
    public long paid() { return paid; }
    public int signed() { return signed; }
    public int declined() { return declined; }
    public int raised() { return raised; }
    public int lapsed() { return lapsed; }
    public int walked() { return walked; }
    public long best() { return best; }

    @Nullable
    public Offer offer(UUID id) {
        for (Offer o : offers) if (o.id.equals(id)) return o;
        return null;
    }

    /** The guild's offer still on the table or on its way, or null. */
    @Nullable
    public Offer offerFrom(RivalGuild guild) {
        for (Offer o : offers) if (o.guild == guild) return o;
        return null;
    }

    /** The offers on the table now. */
    public List<Offer> open(long now) {
        List<Offer> out = new ArrayList<>();
        for (Offer o : offers) if (o.open(now)) out.add(o);
        return out;
    }

    /** True if the hunter is under contract with that guild. */
    public boolean contractedTo(RivalGuild guild) {
        return contract != null && contract.guild == guild;
    }

    @Nullable
    static RivalGuild guildByName(String name) {
        for (RivalGuild g : RivalGuild.values()) if (g.name().equals(name)) return g;
        return null;
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag t = new CompoundTag();
        ListTag list = new ListTag();
        for (Offer o : offers) list.add(o.save());
        t.put("offers", list);
        if (contract != null) t.put("contract", contract.save());
        t.putLong("paid", paid);
        t.putInt("signed", signed);
        t.putInt("declined", declined);
        t.putInt("raised", raised);
        t.putInt("lapsed", lapsed);
        t.putInt("walked", walked);
        t.putLong("best", best);
        t.putIntArray("cameFor", cameFor);
        if (round != null) t.putString("round", round.name());
        t.putIntArray("roundGuilds", roundGuilds.stream().mapToInt(Enum::ordinal).toArray());
        t.putIntArray("roundDeclined", roundDeclined.stream().mapToInt(Enum::ordinal).toArray());
        return t;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag t) {
        offers.clear();
        for (Tag e : t.getList("offers", Tag.TAG_COMPOUND)) {
            Offer o = Offer.load((CompoundTag) e);
            if (o != null) offers.add(o);
        }
        contract = t.contains("contract") ? Contract.load(t.getCompound("contract")) : null;
        paid = t.getLong("paid");
        signed = t.getInt("signed");
        declined = t.getInt("declined");
        raised = t.getInt("raised");
        lapsed = t.getInt("lapsed");
        walked = t.getInt("walked");
        best = t.getLong("best");
        Arrays.fill(cameFor, -1);
        int[] came = t.getIntArray("cameFor");
        for (int i = 0; i < Math.min(came.length, cameFor.length); i++) cameFor[i] = came[i];
        round = t.contains("round") ? PlayerRank.byName(t.getString("round")) : null;
        roundGuilds.clear();
        for (int i : t.getIntArray("roundGuilds")) roundGuilds.add(RivalGuild.byIndex(i));
        roundDeclined.clear();
        for (int i : t.getIntArray("roundDeclined")) roundDeclined.add(RivalGuild.byIndex(i));
    }
}
