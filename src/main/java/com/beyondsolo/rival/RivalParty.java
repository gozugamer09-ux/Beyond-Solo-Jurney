package com.beyondsolo.rival;

import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A party of the world's other hunters on its way to a gate, in it, or done with it ({@link RivalParties}). It is a
 * leader (one of the national rankings' hunters, or someone of the gate's rank) and their party, all of the gate's
 * rank; while nobody watches it is just this record and its clocks, and its hunters ({@link RivalHunterEntity}) are only
 * out in the world while someone is near enough to see them. Saved in {@link RivalData}.
 */
public final class RivalParty {
    public enum State {
        /** On its way: it reaches the gate at {@link #arriveAt}. */
        EN_ROUTE,
        /** In front of the gate, forming up: it goes in at {@link #gatherUntil}. */
        GATHERING,
        /** In the gate. */
        INSIDE,
        /** Over (see {@link #outcome}). */
        DONE
    }

    public enum Outcome {
        NONE,
        /** They cleared the gate. */
        CLEARED,
        /** They pulled out of the gate (it is open again). */
        RETREATED,
        /** Someone got there first: they moved on. */
        STOOD_DOWN,
        /** A hunter beat them to the boss. */
        BEATEN,
        /** Their gate was gone before they got anywhere (closed, broken, wiped). */
        DISBANDED
    }

    /** One hunter of the party. The first is the leader. */
    public static final class Member {
        public final UUID hunter;
        public final String name;
        public final PlayerClass cls;
        public final int level;
        public final int skin;
        /** Fell in the gate: out of the party for good. */
        public boolean down;
        /** Their body in the world (not saved). */
        @Nullable public transient UUID body;

        public Member(UUID hunter, String name, PlayerClass cls, int level, int skin) {
            this.hunter = hunter;
            this.name = name;
            this.cls = cls;
            this.level = level;
            this.skin = skin;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("hunter", hunter);
            t.putString("name", name);
            t.putString("class", cls.name());
            t.putInt("level", level);
            t.putInt("skin", skin);
            if (down) t.putBoolean("down", true);
            return t;
        }

        static Member load(CompoundTag t) {
            PlayerClass cls;
            try {
                cls = PlayerClass.valueOf(t.getString("class"));
            } catch (IllegalArgumentException e) {
                cls = PlayerClass.WARRIOR;
            }
            Member m = new Member(t.getUUID("hunter"), t.getString("name"), cls, Math.max(1, t.getInt("level")), t.getInt("skin"));
            m.down = t.getBoolean("down");
            return m;
        }
    }

    public final UUID id;
    public final UUID gate;
    public final PlayerRank rank;
    public final RivalGuild guild;
    /** The leader's id on the national rankings ({@code Rivals}), or "" for a leader who isn't on them. */
    public final String rival;
    public final List<Member> members;

    State state = State.EN_ROUTE;
    Outcome outcome = Outcome.NONE;
    final long dispatchedAt;
    /** Where they set out from (they walk from here to the gate by {@link #arriveAt}). */
    BlockPos from = BlockPos.ZERO;
    long arriveAt;
    long gatherUntil;
    long enteredAt = -1;
    long endedAt = -1;
    /** Game ticks a whole clear takes them while nobody watches. */
    int clearTicks;
    /** How far through the gate they are, 0 (the entrance) to 1 (the boss done). */
    float progress;
    /** They went in first: the association has given them the gate's clearing rights. */
    boolean holds;
    /** A hunter is in the gate with them (they followed one in, or one forced their way in): a race to the boss. */
    boolean racing;
    /** The race's tally: what they and what the hunters (and everything fighting for them) dealt the boss. */
    float rivalDamage, hunterDamage;
    /** Hunters who forced their way into the gate while they held it (each pays for it once). */
    final Set<UUID> trespassers = new LinkedHashSet<>();
    /** Hunters told of the party (they see it on the HUD while it lasts). */
    final Set<UUID> watchers = new LinkedHashSet<>();
    /** Whether the party clears its gate, rolled once when it goes in (for the time nobody watches). */
    boolean succeeds = true;

    public RivalParty(UUID id, UUID gate, PlayerRank rank, RivalGuild guild, String rival, List<Member> members, long dispatchedAt) {
        this.id = id;
        this.gate = gate;
        this.rank = rank;
        this.guild = guild;
        this.rival = rival == null ? "" : rival;
        this.members = members;
        this.dispatchedAt = dispatchedAt;
    }

    public State state() { return state; }
    public Outcome outcome() { return outcome; }
    public BlockPos from() { return from; }
    public long arriveAt() { return arriveAt; }
    public long gatherUntil() { return gatherUntil; }
    public long enteredAt() { return enteredAt; }
    public int clearTicks() { return clearTicks; }
    public float progress() { return progress; }
    public boolean holds() { return holds; }
    public boolean racing() { return racing; }
    public float rivalDamage() { return rivalDamage; }
    public float hunterDamage() { return hunterDamage; }
    public Set<UUID> watchers() { return watchers; }
    public boolean active() { return state != State.DONE; }

    public Member leader() {
        return members.get(0);
    }

    /** "Kwon Dae-ho". */
    public String leaderName() {
        return leader().name;
    }

    /** The members still standing. */
    public List<Member> standing() {
        List<Member> out = new ArrayList<>();
        for (Member m : members) if (!m.down) out.add(m);
        return out;
    }

    /** "Kwon Dae-ho's party" in the reader's language. */
    public Component partyName() {
        return Component.translatable("rival.beyondsolo.party", leaderName());
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putUUID("gate", gate);
        t.putString("rank", rank.name());
        t.putString("guild", guild.name());
        t.putString("rival", rival);
        ListTag list = new ListTag();
        for (Member m : members) list.add(m.save());
        t.put("members", list);
        t.putString("state", state.name());
        t.putString("outcome", outcome.name());
        t.putLong("dispatched", dispatchedAt);
        t.putLong("from", from.asLong());
        t.putLong("arrive", arriveAt);
        t.putLong("gather", gatherUntil);
        t.putLong("entered", enteredAt);
        t.putLong("ended", endedAt);
        t.putInt("clearTicks", clearTicks);
        t.putFloat("progress", progress);
        t.putBoolean("holds", holds);
        t.putBoolean("racing", racing);
        t.putFloat("rivalDamage", rivalDamage);
        t.putFloat("hunterDamage", hunterDamage);
        t.putBoolean("succeeds", succeeds);
        ListTag tr = new ListTag();
        for (UUID u : trespassers) tr.add(NbtUtils.createUUID(u));
        t.put("trespassers", tr);
        ListTag w = new ListTag();
        for (UUID u : watchers) w.add(NbtUtils.createUUID(u));
        t.put("watchers", w);
        return t;
    }

    @Nullable
    static RivalParty load(CompoundTag t) {
        if (!t.hasUUID("id") || !t.hasUUID("gate")) return null;
        List<Member> members = new ArrayList<>();
        for (Tag e : t.getList("members", Tag.TAG_COMPOUND)) members.add(Member.load((CompoundTag) e));
        if (members.isEmpty()) return null;
        PlayerRank rank = PlayerRank.byName(t.getString("rank"));
        RivalGuild guild;
        try {
            guild = RivalGuild.valueOf(t.getString("guild"));
        } catch (IllegalArgumentException e) {
            guild = RivalGuild.INDEPENDENT;
        }
        RivalParty p = new RivalParty(t.getUUID("id"), t.getUUID("gate"), rank == null ? PlayerRank.E : rank, guild, t.getString("rival"),
                members, t.getLong("dispatched"));
        try {
            p.state = State.valueOf(t.getString("state"));
        } catch (IllegalArgumentException e) {
            p.state = State.DONE;
        }
        try {
            p.outcome = Outcome.valueOf(t.getString("outcome"));
        } catch (IllegalArgumentException e) {
            p.outcome = Outcome.NONE;
        }
        p.from = BlockPos.of(t.getLong("from"));
        p.arriveAt = t.getLong("arrive");
        p.gatherUntil = t.getLong("gather");
        p.enteredAt = t.getLong("entered");
        p.endedAt = t.getLong("ended");
        p.clearTicks = Math.max(20, t.getInt("clearTicks"));
        p.progress = t.getFloat("progress");
        p.holds = t.getBoolean("holds");
        p.racing = t.getBoolean("racing");
        p.rivalDamage = t.getFloat("rivalDamage");
        p.hunterDamage = t.getFloat("hunterDamage");
        p.succeeds = !t.contains("succeeds") || t.getBoolean("succeeds");
        for (Tag e : t.getList("trespassers", Tag.TAG_INT_ARRAY)) p.trespassers.add(NbtUtils.loadUUID(e));
        for (Tag e : t.getList("watchers", Tag.TAG_INT_ARRAY)) p.watchers.add(NbtUtils.loadUUID(e));
        return p;
    }
}
