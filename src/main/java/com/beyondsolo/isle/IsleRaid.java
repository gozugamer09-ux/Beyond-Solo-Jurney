package com.beyondsolo.isle;

import com.beyondsolo.raid.RaidState;
import com.beyondsolo.system.PlayerClass;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One Halcyon raid ({@link IsleRaids}), kept with the overworld's data ({@link IsleData}) so it carries on over a
 * restart: who was called and who answered, its phase, the beach waves sent, the nests burned (and the blows their
 * hearts have taken), the queen and the king, the allies from the national rankings, and the tally.
 */
public final class IsleRaid {
    public enum Phase {
        /** The call is out: hunters answer it until the muster ends. */
        MUSTER,
        /** Landed at the camp: the swarm comes down on the beach in waves. */
        BEACH,
        /** The push inland: every nest has to burn. */
        NESTS,
        /** The great hive is open: down the pit to the queen. */
        HIVE,
        /** The queen is awake. */
        QUEEN,
        /** She fell, and the king was born. */
        KING,
        CLEARED,
        FAILED;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public boolean landed() {
            return this != MUSTER && this != CLEARED && this != FAILED;
        }

        public boolean over() {
            return this == CLEARED || this == FAILED;
        }
    }

    /** Why a raid failed. */
    public enum Lost { NONE, NOBODY, FALLEN, TIME, CALLED_OFF }

    /** A hunter from the national rankings fighting alongside. */
    public static final class Ally {
        final String rival;
        final RaidState.Hired hired;
        int kills;
        /** Its body on the island (not saved: it comes back beside a hunter after a restart). */
        @Nullable transient UUID entity;

        Ally(String rival, RaidState.Hired hired) {
            this.rival = rival;
            this.hired = hired;
        }

        public String rival() { return rival; }
        public RaidState.Hired hired() { return hired; }
        public boolean fallen() { return hired.fallen; }
        public @Nullable UUID entity() { return entity; }
    }

    final UUID id;
    Phase phase = Phase.MUSTER;
    Lost lost = Lost.NONE;
    /** The island's layout seed. */
    long seed;
    long calledAt;
    long phaseAt;
    long musterEnds;
    long landedAt = -1;
    long endedAt = -1;
    /** Waves sent onto the beach so far, and when the last came (or the next is due, before the first). */
    int wave;
    long waveAt;
    /** Burned nests, and blows each nest's heart has taken. */
    boolean[] burned = new boolean[0];
    int[] blows = new int[0];
    int blowsNeeded = 1;
    @Nullable UUID queen;
    @Nullable UUID king;
    long queenFellAt = -1;
    long kingFellAt = -1;
    /** Called up by a command: ignores the rank floor, and goes out even with nobody near. */
    boolean forced;
    final Set<UUID> called = new LinkedHashSet<>();
    final Set<UUID> joined = new LinkedHashSet<>();
    final Set<UUID> declined = new HashSet<>();
    /** Carried off the island hurt: out of the raid. */
    final Set<UUID> fallen = new HashSet<>();
    /** Left of their own accord (or were away when it landed): out of the raid, unpaid. */
    final Set<UUID> left = new HashSet<>();
    final Map<UUID, String> names = new LinkedHashMap<>();
    final Map<UUID, Integer> kills = new HashMap<>();
    final List<Ally> allies = new ArrayList<>();
    int swarmSlain;

    /** Not saved: the swarm alive on the island now, and since when no hunter has stood on it. */
    final transient Set<UUID> swarm = new HashSet<>();
    transient long emptySince = -1;

    IsleRaid(UUID id) {
        this.id = id;
    }

    public UUID id() { return id; }
    public Phase phase() { return phase; }
    public Lost lost() { return lost; }
    public long seed() { return seed; }
    public long landedAt() { return landedAt; }
    public int wave() { return wave; }
    public Set<UUID> called() { return called; }
    public Set<UUID> joined() { return joined; }
    public Set<UUID> declined() { return declined; }
    public Set<UUID> fallen() { return fallen; }
    public Set<UUID> left() { return left; }
    public List<Ally> allies() { return allies; }
    public @Nullable UUID queen() { return queen; }
    public @Nullable UUID king() { return king; }
    public int swarmSlain() { return swarmSlain; }

    public int nestsBurned() {
        int n = 0;
        for (boolean b : burned) if (b) n++;
        return n;
    }

    public int nests() {
        return burned.length;
    }

    public boolean burned(int i) {
        return i >= 0 && i < burned.length && burned[i];
    }

    /** Still fighting: answered, landed, and neither carried off nor gone. */
    public boolean fighting(UUID who) {
        return joined.contains(who) && !fallen.contains(who) && !left.contains(who);
    }

    /** Paid when the island is retaken: everyone who landed and didn't walk away (the fallen too, for less). */
    public boolean paid(UUID who) {
        return joined.contains(who) && !left.contains(who);
    }

    void setNests(int n) {
        burned = new boolean[n];
        blows = new int[n];
    }

    // ------------------------------------------------------------------ saving

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("phase", phase.name());
        t.putString("lost", lost.name());
        t.putLong("seed", seed);
        t.putLong("called", calledAt);
        t.putLong("phaseAt", phaseAt);
        t.putLong("muster", musterEnds);
        t.putLong("landed", landedAt);
        t.putLong("ended", endedAt);
        t.putInt("wave", wave);
        t.putLong("waveAt", waveAt);
        byte[] b = new byte[burned.length];
        for (int i = 0; i < b.length; i++) b[i] = (byte) (burned[i] ? 1 : 0);
        t.putByteArray("burned", b);
        t.putIntArray("blows", blows);
        t.putInt("needed", blowsNeeded);
        if (queen != null) t.putUUID("queen", queen);
        if (king != null) t.putUUID("king", king);
        t.putLong("queenFell", queenFellAt);
        t.putLong("kingFell", kingFellAt);
        t.putBoolean("forced", forced);
        t.put("calledList", ids(called));
        t.put("joinedList", ids(joined));
        t.put("declinedList", ids(declined));
        t.put("fallenList", ids(fallen));
        t.put("leftList", ids(left));
        ListTag names = new ListTag();
        for (Map.Entry<UUID, String> e : this.names.entrySet()) {
            CompoundTag n = new CompoundTag();
            n.putUUID("id", e.getKey());
            n.putString("name", e.getValue());
            n.putInt("kills", kills.getOrDefault(e.getKey(), 0));
            names.add(n);
        }
        t.put("names", names);
        ListTag allies = new ListTag();
        for (Ally a : this.allies) {
            CompoundTag c = new CompoundTag();
            c.putString("rival", a.rival);
            c.putUUID("guild", a.hired.guild);
            c.putUUID("hunter", a.hired.hunter);
            c.putString("name", a.hired.name);
            c.putString("cls", a.hired.cls.name());
            c.putInt("level", a.hired.level);
            c.putInt("skin", a.hired.skin);
            c.putBoolean("fallen", a.hired.fallen);
            c.putInt("kills", a.kills);
            allies.add(c);
        }
        t.put("allies", allies);
        t.putInt("slain", swarmSlain);
        return t;
    }

    @Nullable
    static IsleRaid load(CompoundTag t) {
        if (!t.hasUUID("id")) return null;
        IsleRaid r = new IsleRaid(t.getUUID("id"));
        r.phase = parse(Phase.class, t.getString("phase"), Phase.FAILED);
        r.lost = parse(Lost.class, t.getString("lost"), Lost.NONE);
        r.seed = t.getLong("seed");
        r.calledAt = t.getLong("called");
        r.phaseAt = t.getLong("phaseAt");
        r.musterEnds = t.getLong("muster");
        r.landedAt = t.getLong("landed");
        r.endedAt = t.getLong("ended");
        r.wave = t.getInt("wave");
        r.waveAt = t.getLong("waveAt");
        byte[] b = t.getByteArray("burned");
        r.burned = new boolean[b.length];
        for (int i = 0; i < b.length; i++) r.burned[i] = b[i] != 0;
        int[] blows = t.getIntArray("blows");
        r.blows = new int[b.length];
        System.arraycopy(blows, 0, r.blows, 0, Math.min(blows.length, b.length));
        r.blowsNeeded = Math.max(1, t.getInt("needed"));
        r.queen = t.hasUUID("queen") ? t.getUUID("queen") : null;
        r.king = t.hasUUID("king") ? t.getUUID("king") : null;
        r.queenFellAt = t.getLong("queenFell");
        r.kingFellAt = t.getLong("kingFell");
        r.forced = t.getBoolean("forced");
        read(t.getList("calledList", Tag.TAG_INT_ARRAY), r.called);
        read(t.getList("joinedList", Tag.TAG_INT_ARRAY), r.joined);
        read(t.getList("declinedList", Tag.TAG_INT_ARRAY), r.declined);
        read(t.getList("fallenList", Tag.TAG_INT_ARRAY), r.fallen);
        read(t.getList("leftList", Tag.TAG_INT_ARRAY), r.left);
        for (Tag x : t.getList("names", Tag.TAG_COMPOUND)) {
            CompoundTag n = (CompoundTag) x;
            if (!n.hasUUID("id")) continue;
            r.names.put(n.getUUID("id"), n.getString("name"));
            if (n.getInt("kills") > 0) r.kills.put(n.getUUID("id"), n.getInt("kills"));
        }
        for (Tag x : t.getList("allies", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            if (!c.hasUUID("hunter") || !c.hasUUID("guild")) continue;
            RaidState.Hired h = new RaidState.Hired(c.getUUID("guild"), c.getUUID("hunter"), c.getString("name"),
                    parse(PlayerClass.class, c.getString("cls"), PlayerClass.WARRIOR), Math.max(1, c.getInt("level")), c.getInt("skin"));
            h.fallen = c.getBoolean("fallen");
            Ally a = new Ally(c.getString("rival"), h);
            a.kills = c.getInt("kills");
            r.allies.add(a);
        }
        r.swarmSlain = t.getInt("slain");
        return r;
    }

    private static ListTag ids(Set<UUID> set) {
        ListTag l = new ListTag();
        for (UUID u : set) l.add(NbtUtils.createUUID(u));
        return l;
    }

    private static void read(ListTag l, Set<UUID> into) {
        for (Tag x : l) into.add(NbtUtils.loadUUID(x));
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
