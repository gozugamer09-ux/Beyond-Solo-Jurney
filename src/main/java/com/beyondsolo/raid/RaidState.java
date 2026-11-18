package com.beyondsolo.raid;

import com.beyondsolo.system.PlayerClass;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A raid behind a gate, as saved with its gate ({@code gate.GateInstance}): who mustered for it and in which role,
 * the guild hunters who came along, the lieutenants that seal the boss's chamber and how far the fight with the boss
 * has got. What only matters while it runs (who the boss minds most, who dealt what) lives in {@link Raids}.
 */
public final class RaidState {
    public enum Phase {
        /** Raiders gather at the gate; the leader starts it. */
        MUSTER,
        /** Started: the raiders are taken through together when it runs out. */
        COUNTDOWN,
        /** Inside. Only its raiders may still go through. */
        RUNNING,
        CLEARED,
        /** Every raider fell or left: the gate shuts. */
        FAILED;

        public boolean open() {
            return this == MUSTER || this == COUNTDOWN || this == RUNNING;
        }
    }

    /** Where a raider is in the raid. */
    public enum Status { WAITING, INSIDE, FALLEN, LEFT }

    /** A hunter (a player) on the raid's roster. */
    public static final class Raider {
        public final UUID id;
        public String name;
        public RaidRole role;
        public Status status = Status.WAITING;
        /** Class ordinal and level when they mustered (for the briefing). */
        public int cls = -1, level;

        public Raider(UUID id, String name, RaidRole role) {
            this.id = id;
            this.name = name;
            this.role = role;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putString("name", name);
            t.putString("role", role.id());
            t.putString("status", status.name());
            t.putInt("cls", cls);
            t.putInt("level", level);
            return t;
        }

        static Raider load(CompoundTag t) {
            RaidRole role = RaidRole.byId(t.getString("role"));
            Raider r = new Raider(t.getUUID("id"), t.getString("name"), role == null ? RaidRole.STRIKER : role);
            try {
                r.status = Status.valueOf(t.getString("status"));
            } catch (IllegalArgumentException e) {
                r.status = Status.WAITING;
            }
            r.cls = t.getInt("cls");
            r.level = t.getInt("level");
            return r;
        }
    }

    /** A guild's hired hunter on the raid's roster. */
    public static final class Hired {
        public final UUID guild;
        public final UUID hunter;
        public final String name;
        public final PlayerClass cls;
        public final int level;
        public final int skin;
        public boolean fallen;
        public int kills;
        /** The hunter's body in the cave (not saved: it comes back next to a raider if the cave was unloaded). */
        @Nullable public transient UUID entity;

        public Hired(UUID guild, UUID hunter, String name, PlayerClass cls, int level, int skin) {
            this.guild = guild;
            this.hunter = hunter;
            this.name = name;
            this.cls = cls;
            this.level = level;
            this.skin = skin;
        }

        public RaidRole role() {
            return RaidRole.forClass(cls);
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("guild", guild);
            t.putUUID("hunter", hunter);
            t.putString("name", name);
            t.putString("class", cls.name());
            t.putInt("level", level);
            t.putInt("skin", skin);
            t.putBoolean("fallen", fallen);
            t.putInt("kills", kills);
            return t;
        }

        static Hired load(CompoundTag t) {
            PlayerClass cls;
            try {
                cls = PlayerClass.valueOf(t.getString("class"));
            } catch (IllegalArgumentException e) {
                cls = PlayerClass.WARRIOR;
            }
            Hired h = new Hired(t.getUUID("guild"), t.getUUID("hunter"), t.getString("name"), cls, t.getInt("level"), t.getInt("skin"));
            h.fallen = t.getBoolean("fallen");
            h.kills = t.getInt("kills");
            return h;
        }
    }

    public Phase phase = Phase.MUSTER;
    /** Who leads: the first to muster, or the officer who requested the permit. */
    @Nullable public UUID leader;
    /** The guild whose permit opened it, or null for a raid gate that opened on its own. */
    @Nullable public UUID guild;
    /** Game time the countdown ends, -1 if none is running. */
    public long countdownEnd = -1;
    /** Game time the raiders were taken through, -1 before. */
    public long startedAt = -1;
    /** Game time the boss fell, -1 before. */
    public long clearedAt = -1;
    /** The leader brings their guild's hunters along. */
    public boolean bringHunters = true;
    public final Map<UUID, Raider> raiders = new LinkedHashMap<>();
    public final List<Hired> hunters = new ArrayList<>();
    /** Lieutenants still standing (entity ids), and how many there were. */
    public final List<UUID> lieutenants = new ArrayList<>();
    public int lieutenantsTotal;
    /** How many raiders it was made for: players, and half a raider for each hired hunter, past the first. */
    public float weight;
    /** The boss has been grown for the raid. */
    public boolean bossScaled;
    /** The barrier came up at 70%, and fell again once its pylons broke. */
    public boolean barrierDone, barrierUp;
    public int pylonsTotal, pylonsLeft;

    public static RaidState fresh(@Nullable UUID guild) {
        RaidState s = new RaidState();
        s.guild = guild;
        return s;
    }

    @Nullable
    public Raider raider(UUID id) {
        return raiders.get(id);
    }

    public boolean has(UUID player) {
        return raiders.containsKey(player);
    }

    @Nullable
    public Hired hired(UUID hunter) {
        for (Hired h : hunters) if (h.hunter.equals(hunter)) return h;
        return null;
    }

    /** Raiders and hunters on the roster. */
    public int size() {
        return raiders.size() + hunters.size();
    }

    /** The leader's name, or "?". */
    public String leaderName() {
        Raider r = leader == null ? null : raiders.get(leader);
        return r == null ? "?" : r.name;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("phase", phase.name());
        if (leader != null) t.putUUID("leader", leader);
        if (guild != null) t.putUUID("guild", guild);
        t.putLong("countdownEnd", countdownEnd);
        t.putLong("startedAt", startedAt);
        t.putLong("clearedAt", clearedAt);
        t.putBoolean("bringHunters", bringHunters);
        ListTag rs = new ListTag();
        for (Raider r : raiders.values()) rs.add(r.save());
        t.put("raiders", rs);
        ListTag hs = new ListTag();
        for (Hired h : hunters) hs.add(h.save());
        t.put("hunters", hs);
        ListTag lt = new ListTag();
        for (UUID id : lieutenants) lt.add(NbtUtils.createUUID(id));
        t.put("lieutenants", lt);
        t.putInt("lieutenantsTotal", lieutenantsTotal);
        t.putFloat("weight", weight);
        t.putBoolean("bossScaled", bossScaled);
        t.putBoolean("barrierDone", barrierDone);
        t.putBoolean("barrierUp", barrierUp);
        t.putInt("pylonsTotal", pylonsTotal);
        t.putInt("pylonsLeft", pylonsLeft);
        return t;
    }

    public static RaidState load(CompoundTag t) {
        RaidState s = new RaidState();
        try {
            s.phase = Phase.valueOf(t.getString("phase"));
        } catch (IllegalArgumentException e) {
            s.phase = Phase.MUSTER;
        }
        if (t.hasUUID("leader")) s.leader = t.getUUID("leader");
        if (t.hasUUID("guild")) s.guild = t.getUUID("guild");
        s.countdownEnd = t.contains("countdownEnd") ? t.getLong("countdownEnd") : -1;
        s.startedAt = t.contains("startedAt") ? t.getLong("startedAt") : -1;
        s.clearedAt = t.contains("clearedAt") ? t.getLong("clearedAt") : -1;
        s.bringHunters = !t.contains("bringHunters") || t.getBoolean("bringHunters");
        ListTag rs = t.getList("raiders", Tag.TAG_COMPOUND);
        for (int i = 0; i < rs.size(); i++) {
            Raider r = Raider.load(rs.getCompound(i));
            s.raiders.put(r.id, r);
        }
        ListTag hs = t.getList("hunters", Tag.TAG_COMPOUND);
        for (int i = 0; i < hs.size(); i++) s.hunters.add(Hired.load(hs.getCompound(i)));
        ListTag lt = t.getList("lieutenants", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < lt.size(); i++) s.lieutenants.add(NbtUtils.loadUUID(lt.get(i)));
        s.lieutenantsTotal = t.getInt("lieutenantsTotal");
        s.weight = t.getFloat("weight");
        s.bossScaled = t.getBoolean("bossScaled");
        s.barrierDone = t.getBoolean("barrierDone");
        s.barrierUp = t.getBoolean("barrierUp");
        s.pylonsTotal = t.getInt("pylonsTotal");
        s.pylonsLeft = t.getInt("pylonsLeft");
        // a countdown that was running when the world stopped starts again from the muster
        if (s.phase == Phase.COUNTDOWN) {
            s.phase = Phase.MUSTER;
            s.countdownEnd = -1;
        }
        return s;
    }
}
