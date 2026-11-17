package com.beyondsolo.redgate;

import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One red gate: the gate in the world that turned red, the patch of the frozen world behind it, who went in, the
 * inside clock, and how far the hunters have got (camps taken, its master's fate). Saved in {@link RedGateData}.
 */
public final class RedGate {
    /**
     * OPEN: the gate has just turned red and the rest of the party can still follow. SEALED: nobody in, nobody out.
     * RELEASED: the way out is open (its master fell, or the gate's hold broke on the release day). ENDED: over; the gate
     * in the world closes.
     */
    public enum State { OPEN, SEALED, RELEASED, ENDED }

    /** An encampment of the gate's creatures out in the snow. */
    public static final class Camp {
        public final BlockPos pos;
        boolean triggered;
        boolean cleared;
        final List<UUID> mobs = new ArrayList<>();

        Camp(BlockPos pos) {
            this.pos = pos.immutable();
        }

        public boolean triggered() { return triggered; }
        public boolean cleared() { return cleared; }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.put("pos", NbtUtils.writeBlockPos(pos));
            t.putBoolean("triggered", triggered);
            t.putBoolean("cleared", cleared);
            t.put("mobs", uuids(mobs));
            return t;
        }

        static Camp load(CompoundTag t) {
            Camp c = new Camp(NbtUtils.readBlockPos(t, "pos").orElse(BlockPos.ZERO));
            c.triggered = t.getBoolean("triggered");
            c.cleared = t.getBoolean("cleared");
            readUuids(t.getList("mobs", Tag.TAG_INT_ARRAY), c.mobs);
            return c;
        }
    }

    public final UUID gate;
    /** The gate's own rank, and the rank of what waits behind it (higher). */
    public final PlayerRank rank;
    public final PlayerRank inside;
    public final long seed;
    /** Which patch of the frozen world it is (patches are {@link RedWorld#SLOT_SPACING} apart). */
    public final int slot;
    /** Where hunters come out when it lets them go: in front of the gate in the world. */
    public final GateService.Home home;
    public final BossKind bossKind;

    State state = State.OPEN;
    /** Game time the gate seals behind the party. */
    long sealAt;
    /** Game time it ended (or -1). */
    long endedAt = -1;
    /** The inside clock: ticks of the inside day cycle since its first dawn (a day is 24000). */
    long insideTime = RedWorld.START_TIME;

    /** The patch: where hunters arrive (by the sealed way out), its master's keep, the camps. Null until planned. */
    @Nullable BlockPos center;
    @Nullable BlockPos keep;
    @Nullable BlockPos exit;
    @Nullable BlockPos keepExit;
    /** The direction from the arrival to the keep, in degrees (for the layout and the arrival's facing). */
    float keepYaw;
    final List<Camp> camps = new ArrayList<>();
    boolean built;

    /** Everyone who went in, and who is in there now (logged out inside included). */
    final Set<UUID> party = new LinkedHashSet<>();
    final Set<UUID> insideNow = new LinkedHashSet<>();

    @Nullable UUID boss;
    boolean bossSpawned;
    boolean bossAwake;
    boolean bossEnrageAnswered;
    boolean cleared;
    boolean keepTriggered;
    final List<UUID> keepGuards = new ArrayList<>();
    final List<UUID> raiders = new ArrayList<>();
    int kills;

    /** The inside day whose night has been announced, and whose dawn has been. */
    int nightAnnounced;
    int dawnAnnounced = 1;
    /** Inside time the blizzard rages until (0: none), and the day its weather was last rolled for. */
    long blizzardUntil;
    long blizzardFrom;
    int weatherRolled;
    /** Ticks to the next raid wave at night. */
    int raidIn;

    RedGate(UUID gate, PlayerRank rank, PlayerRank inside, long seed, int slot, GateService.Home home, BossKind bossKind) {
        this.gate = gate;
        this.rank = rank;
        this.inside = inside;
        this.seed = seed;
        this.slot = slot;
        this.home = home;
        this.bossKind = bossKind;
    }

    public State state() { return state; }
    public boolean cleared() { return cleared; }
    public long insideTime() { return insideTime; }
    public int kills() { return kills; }
    @Nullable public BlockPos center() { return center; }
    @Nullable public BlockPos keep() { return keep; }
    @Nullable public BlockPos exit() { return exit; }
    @Nullable public BlockPos keepExit() { return keepExit; }
    @Nullable public UUID boss() { return boss; }
    public List<Camp> camps() { return camps; }
    public Set<UUID> party() { return party; }
    public Set<UUID> insideNow() { return insideNow; }
    public boolean built() { return built; }

    /** The inside day, counting from 1. */
    public int day() {
        return (int) (insideTime / 24000L) + 1;
    }

    /** Ticks into the inside day (0 is dawn, as with Minecraft's own day). */
    public int timeOfDay() {
        return (int) (insideTime % 24000L);
    }

    public boolean night() {
        int t = timeOfDay();
        return t >= RedWorld.NIGHT_START && t < RedWorld.NIGHT_END;
    }

    public boolean blizzard() {
        return blizzardUntil > insideTime && blizzardFrom <= insideTime;
    }

    /** True while the gate keeps its hunters (and holds back anything else that would happen to it). */
    public boolean holding() {
        return state != State.ENDED;
    }

    public int campsCleared() {
        int n = 0;
        for (Camp c : camps) if (c.cleared) n++;
        return n;
    }

    // ------------------------------------------------------------------ saving

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("gate", gate);
        t.putString("rank", rank.name());
        t.putString("inside", inside.name());
        t.putLong("seed", seed);
        t.putInt("slot", slot);
        CompoundTag h = new CompoundTag();
        h.putString("dim", home.dimension().location().toString());
        h.putDouble("x", home.pos().x);
        h.putDouble("y", home.pos().y);
        h.putDouble("z", home.pos().z);
        h.putFloat("yaw", home.yaw());
        t.put("home", h);
        t.putString("boss", bossKind.id);
        t.putString("state", state.name());
        t.putLong("sealAt", sealAt);
        t.putLong("endedAt", endedAt);
        t.putLong("insideTime", insideTime);
        if (center != null) t.put("center", NbtUtils.writeBlockPos(center));
        if (keep != null) t.put("keep", NbtUtils.writeBlockPos(keep));
        if (exit != null) t.put("exit", NbtUtils.writeBlockPos(exit));
        if (keepExit != null) t.put("keepExit", NbtUtils.writeBlockPos(keepExit));
        t.putFloat("keepYaw", keepYaw);
        ListTag cs = new ListTag();
        for (Camp c : camps) cs.add(c.save());
        t.put("camps", cs);
        t.putBoolean("built", built);
        t.put("party", uuids(party));
        t.put("insideNow", uuids(insideNow));
        if (boss != null) t.putUUID("bossId", boss);
        t.putBoolean("bossSpawned", bossSpawned);
        t.putBoolean("bossAwake", bossAwake);
        t.putBoolean("bossEnrageAnswered", bossEnrageAnswered);
        t.putBoolean("cleared", cleared);
        t.putBoolean("keepTriggered", keepTriggered);
        t.put("keepGuards", uuids(keepGuards));
        t.put("raiders", uuids(raiders));
        t.putInt("kills", kills);
        t.putInt("nightAnnounced", nightAnnounced);
        t.putInt("dawnAnnounced", dawnAnnounced);
        t.putLong("blizzardUntil", blizzardUntil);
        t.putLong("blizzardFrom", blizzardFrom);
        t.putInt("weatherRolled", weatherRolled);
        t.putInt("raidIn", raidIn);
        return t;
    }

    @Nullable
    static RedGate load(CompoundTag t) {
        if (!t.hasUUID("gate")) return null;
        PlayerRank rank = PlayerRank.byName(t.getString("rank"));
        PlayerRank inside = PlayerRank.byName(t.getString("inside"));
        CompoundTag h = t.getCompound("home");
        ResourceLocation dim = ResourceLocation.tryParse(h.getString("dim"));
        GateService.Home home = new GateService.Home(ResourceKey.create(Registries.DIMENSION, dim == null ? ResourceLocation.withDefaultNamespace("overworld") : dim),
                new Vec3(h.getDouble("x"), h.getDouble("y"), h.getDouble("z")), h.getFloat("yaw"));
        BossKind kind = BossKind.byId(t.getString("boss"));
        RedGate r = new RedGate(t.getUUID("gate"), rank == null ? PlayerRank.E : rank, inside == null ? PlayerRank.D : inside, t.getLong("seed"),
                t.getInt("slot"), home, kind == null ? BossKind.BRUTE : kind);
        try {
            r.state = State.valueOf(t.getString("state"));
        } catch (IllegalArgumentException e) {
            r.state = State.ENDED;
        }
        r.sealAt = t.getLong("sealAt");
        r.endedAt = t.contains("endedAt") ? t.getLong("endedAt") : -1;
        r.insideTime = t.getLong("insideTime");
        r.center = NbtUtils.readBlockPos(t, "center").orElse(null);
        r.keep = NbtUtils.readBlockPos(t, "keep").orElse(null);
        r.exit = NbtUtils.readBlockPos(t, "exit").orElse(null);
        r.keepExit = NbtUtils.readBlockPos(t, "keepExit").orElse(null);
        r.keepYaw = t.getFloat("keepYaw");
        ListTag cs = t.getList("camps", Tag.TAG_COMPOUND);
        for (int i = 0; i < cs.size(); i++) r.camps.add(Camp.load(cs.getCompound(i)));
        r.built = t.getBoolean("built");
        readUuids(t.getList("party", Tag.TAG_INT_ARRAY), r.party);
        readUuids(t.getList("insideNow", Tag.TAG_INT_ARRAY), r.insideNow);
        if (t.hasUUID("bossId")) r.boss = t.getUUID("bossId");
        r.bossSpawned = t.getBoolean("bossSpawned");
        r.bossAwake = t.getBoolean("bossAwake");
        r.bossEnrageAnswered = t.getBoolean("bossEnrageAnswered");
        r.cleared = t.getBoolean("cleared");
        r.keepTriggered = t.getBoolean("keepTriggered");
        readUuids(t.getList("keepGuards", Tag.TAG_INT_ARRAY), r.keepGuards);
        readUuids(t.getList("raiders", Tag.TAG_INT_ARRAY), r.raiders);
        r.kills = t.getInt("kills");
        r.nightAnnounced = t.getInt("nightAnnounced");
        r.dawnAnnounced = Math.max(1, t.getInt("dawnAnnounced"));
        r.blizzardUntil = t.getLong("blizzardUntil");
        r.blizzardFrom = t.getLong("blizzardFrom");
        r.weatherRolled = t.getInt("weatherRolled");
        r.raidIn = t.getInt("raidIn");
        return r;
    }

    static ListTag uuids(Iterable<UUID> ids) {
        ListTag out = new ListTag();
        for (UUID id : ids) out.add(NbtUtils.createUUID(id));
        return out;
    }

    static void readUuids(ListTag list, java.util.Collection<UUID> into) {
        for (int i = 0; i < list.size(); i++) into.add(NbtUtils.loadUUID(list.get(i)));
    }
}
