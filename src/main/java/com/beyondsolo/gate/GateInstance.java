package com.beyondsolo.gate;

import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One gate and the cave behind it: where the gate stands in the world, its rank and seed, which slot of the gate
 * dimension holds its cave, and how far the clear has got. Saved in {@link GateData}.
 */
public final class GateInstance {
    /** Instances sit this far apart along x in the gate dimension, so no two caves ever meet. */
    public static final int SLOT_SPACING = 2048;

    public enum State { OPEN, CLEARED, CLOSED }

    private final UUID id;
    private final PlayerRank rank;
    private final long seed;
    private final int slot;
    private final ResourceKey<Level> gateDimension;
    private final BlockPos gatePos;
    private State state = State.OPEN;
    private boolean built;
    private long roomsTriggered;
    private boolean bossSpawned;
    @Nullable private UUID boss;
    private boolean bossAwake;
    private boolean bossDefeated;
    @Nullable private BlockPos exitPos;
    private long clearedAt = -1;
    /** Set for an awakening trial: the one player it was opened for. Null for an ordinary gate. */
    @Nullable private UUID owner;
    /** Awakening trial: the furthest cavern the owner has reached (where they get up again after falling). */
    private int checkpoint;
    /**
     * Awakening trial opened since 0.1.1: the short temple ({@link DungeonLayout#temple}) instead of the long descent.
     * Trials from older saves keep the plan their rock was carved to.
     */
    private boolean temple;
    /**
     * Awakening trial behind a door found in a cave: where its owner wakes once it is over (in front of that cave's gate
     * in the world, kept here because the cave itself is gone by then). Null: in front of the sealed gate.
     */
    @Nullable private GateService.Home trialHome;
    /** Set by commands (and tests) to force a boss; otherwise the boss comes from the seed. */
    @Nullable private BossKind bossOverride;
    /** -1: from the seed, 0: not ascended, 1: ascended. */
    private int ascendedOverride = -1;
    /** Set for a gate opened with a dungeon key: who turned it, where the run goes home to, the run's clock. */
    @Nullable private Keyed keyed;
    /** The cave was wiped back to rock and its slot handed to another gate ({@link GateWiper}). */
    private boolean slotFreed;
    /** Weather, the hidden chamber, trap rooms and how far the party got with them ({@code depths} package). */
    private com.beyondsolo.depths.GateDepths depths = com.beyondsolo.depths.GateDepths.fresh();
    /** Its clock towards a dungeon break, and the break once it has come ({@code breaks} package). */
    private com.beyondsolo.breaks.BreakState breakState = com.beyondsolo.breaks.BreakState.fresh();

    private transient DungeonLayout layout;

    public GateInstance(UUID id, PlayerRank rank, long seed, int slot, ResourceKey<Level> gateDimension, BlockPos gatePos) {
        this.id = id;
        this.rank = rank;
        this.seed = seed;
        this.slot = slot;
        this.gateDimension = gateDimension;
        this.gatePos = gatePos.immutable();
    }

    public UUID id() { return id; }
    public PlayerRank rank() { return rank; }
    public GateTheme theme() { return owner != null ? GateTheme.TRIAL : GateTheme.of(rank); }
    /** True for the awakening trial behind a sealed gate. */
    public boolean isTrial() { return owner != null; }
    @Nullable public UUID owner() { return owner; }
    public void setOwner(@Nullable UUID owner) { this.owner = owner; this.layout = null; }
    public int checkpoint() { return checkpoint; }
    public void setCheckpoint(int room) { this.checkpoint = room; }
    public boolean temple() { return temple; }
    public void setTemple(boolean temple) { this.temple = temple; this.layout = null; }
    @Nullable public GateService.Home trialHome() { return trialHome; }
    public void setTrialHome(@Nullable GateService.Home home) { this.trialHome = home; }
    public long seed() { return seed; }
    public int slot() { return slot; }
    public ResourceKey<Level> gateDimension() { return gateDimension; }
    public BlockPos gatePos() { return gatePos; }
    public State state() { return state; }
    public void setState(State state) { this.state = state; }
    public boolean built() { return built; }
    public void setBuilt(boolean built) { this.built = built; }
    public boolean bossSpawned() { return bossSpawned; }
    public void setBossSpawned(boolean spawned) { this.bossSpawned = spawned; }
    @Nullable public UUID boss() { return boss; }
    public void setBoss(@Nullable UUID boss) { this.boss = boss; }
    public boolean bossAwake() { return bossAwake; }
    public void setBossAwake(boolean awake) { this.bossAwake = awake; }
    public boolean bossDefeated() { return bossDefeated; }
    public void setBossDefeated(boolean defeated) { this.bossDefeated = defeated; }
    @Nullable public BlockPos exitPos() { return exitPos; }
    public void setExitPos(@Nullable BlockPos pos) { this.exitPos = pos; }
    public long clearedAt() { return clearedAt; }

    /** Which boss waits at the end of this gate (from its seed unless forced). */
    public BossKind bossKind() {
        if (bossOverride != null) return bossOverride;
        return BossKind.forSeed(rank, seed);
    }

    public void setBossKind(@Nullable BossKind kind) { this.bossOverride = kind; }

    /** True when the boss is an ascended one (tougher, bigger, gold title). */
    public boolean bossAscended() {
        if (isTrial()) return false;
        return ascendedOverride >= 0 ? ascendedOverride == 1 : BossKind.ascendedForSeed(seed);
    }

    public void setBossAscended(boolean ascended) { this.ascendedOverride = ascended ? 1 : 0; }

    /** True for a gate opened with a dungeon key (see {@code key.DungeonKeys}). */
    public boolean isKeyed() { return keyed != null; }
    @Nullable public Keyed keyed() { return keyed; }
    public void setKeyed(@Nullable Keyed keyed) { this.keyed = keyed; }
    public boolean slotFreed() { return slotFreed; }
    public void setSlotFreed(boolean freed) { this.slotFreed = freed; }
    public com.beyondsolo.depths.GateDepths depths() { return depths; }
    public com.beyondsolo.breaks.BreakState breakState() { return breakState; }

    /** The boss's name as the System says it. */
    public net.minecraft.network.chat.Component bossName() {
        if (isTrial()) return theme().bossName();
        net.minecraft.network.chat.Component name = bossKind().name(rank);
        return bossAscended() ? net.minecraft.network.chat.Component.translatable("gate.beyondsolo.boss_ascended", name) : name;
    }

    /** The creatures behind this gate. */
    public com.beyondsolo.enemy.EnemyRoster.Mix mix() {
        return com.beyondsolo.enemy.EnemyRoster.forSeed(rank, seed);
    }
    public void setClearedAt(long time) { this.clearedAt = time; }

    public boolean roomTriggered(int room) {
        return (roomsTriggered & (1L << room)) != 0;
    }

    public void triggerRoom(int room) {
        roomsTriggered |= 1L << room;
    }

    public DungeonLayout layout() {
        if (layout == null) {
            com.beyondsolo.depths.HiddenKind hidden = depths.hidden(this);
            if (isTrial() && temple) layout = DungeonLayout.temple(seed);
            else if (hidden == com.beyondsolo.depths.HiddenKind.SEALED) layout = DungeonLayout.planSealed(seed, theme());
            else layout = DungeonLayout.plan(seed, theme(), hidden != null);
        }
        return layout;
    }

    /** Plans the cave again (after its hidden chamber was forced). Only before it is built. */
    public void replan() {
        this.layout = null;
    }

    /** Where the cave's origin is in the gate dimension. */
    public BlockPos origin() {
        return new BlockPos(slot * SLOT_SPACING, 0, 0);
    }

    /** A layout position in the gate dimension. */
    public BlockPos world(BlockPos local) {
        return local.offset(origin());
    }

    /** Where players arrive: on the floor of the first cavern (the floor's top block is just below {@code floorY}). */
    public BlockPos entrance() {
        DungeonLayout.Room room = layout().entrance();
        return world(new BlockPos(room.center().getX(), room.floorY(), room.center().getZ()));
    }

    public BlockPos bossRoomCenter() {
        DungeonLayout.Room room = layout().boss();
        return world(new BlockPos(room.center().getX(), room.floorY(), room.center().getZ()));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("rank", rank.name());
        tag.putLong("seed", seed);
        tag.putInt("slot", slot);
        tag.putString("dim", gateDimension.location().toString());
        tag.put("pos", NbtUtils.writeBlockPos(gatePos));
        tag.putString("state", state.name());
        tag.putBoolean("built", built);
        tag.putLong("rooms", roomsTriggered);
        tag.putBoolean("bossSpawned", bossSpawned);
        if (boss != null) tag.putUUID("boss", boss);
        tag.putBoolean("bossAwake", bossAwake);
        tag.putBoolean("bossDefeated", bossDefeated);
        if (exitPos != null) tag.put("exit", NbtUtils.writeBlockPos(exitPos));
        tag.putLong("clearedAt", clearedAt);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putInt("checkpoint", checkpoint);
        tag.putBoolean("temple", temple);
        if (trialHome != null) {
            CompoundTag home = new CompoundTag();
            home.putString("dim", trialHome.dimension().location().toString());
            home.putDouble("x", trialHome.pos().x);
            home.putDouble("y", trialHome.pos().y);
            home.putDouble("z", trialHome.pos().z);
            home.putFloat("yaw", trialHome.yaw());
            tag.put("trialHome", home);
        }
        if (bossOverride != null) tag.putString("bossKind", bossOverride.id);
        tag.putInt("ascended", ascendedOverride);
        if (keyed != null) tag.put("keyed", keyed.save());
        tag.putBoolean("slotFreed", slotFreed);
        tag.put("depths", depths.save());
        tag.put("break", breakState.save());
        return tag;
    }

    public static GateInstance load(CompoundTag tag) {
        PlayerRank rank = PlayerRank.byName(tag.getString("rank"));
        GateInstance g = new GateInstance(tag.getUUID("id"), rank == null ? PlayerRank.E : rank, tag.getLong("seed"), tag.getInt("slot"),
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("dim"))),
                NbtUtils.readBlockPos(tag, "pos").orElse(BlockPos.ZERO));
        try {
            g.state = State.valueOf(tag.getString("state"));
        } catch (IllegalArgumentException e) {
            g.state = State.OPEN;
        }
        g.built = tag.getBoolean("built");
        g.roomsTriggered = tag.getLong("rooms");
        g.bossSpawned = tag.getBoolean("bossSpawned");
        if (tag.hasUUID("boss")) g.boss = tag.getUUID("boss");
        g.bossAwake = tag.getBoolean("bossAwake");
        g.bossDefeated = tag.getBoolean("bossDefeated");
        g.exitPos = NbtUtils.readBlockPos(tag, "exit").orElse(null);
        g.clearedAt = tag.getLong("clearedAt");
        if (tag.hasUUID("owner")) g.owner = tag.getUUID("owner");
        g.checkpoint = tag.getInt("checkpoint");
        g.temple = tag.getBoolean("temple");
        if (tag.contains("trialHome")) {
            CompoundTag home = tag.getCompound("trialHome");
            g.trialHome = new GateService.Home(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(home.getString("dim"))),
                    new net.minecraft.world.phys.Vec3(home.getDouble("x"), home.getDouble("y"), home.getDouble("z")), home.getFloat("yaw"));
        }
        if (tag.contains("bossKind")) g.bossOverride = BossKind.byId(tag.getString("bossKind"));
        g.ascendedOverride = tag.contains("ascended") ? tag.getInt("ascended") : -1;
        if (tag.contains("keyed")) g.keyed = Keyed.load(tag.getCompound("keyed"));
        g.slotFreed = tag.getBoolean("slotFreed");
        g.depths = tag.contains("depths") ? com.beyondsolo.depths.GateDepths.load(tag.getCompound("depths")) : com.beyondsolo.depths.GateDepths.legacy();
        // a gate saved before breaks existed starts its clock the next time it is looked at
        g.breakState = tag.contains("break") ? com.beyondsolo.breaks.BreakState.load(tag.getCompound("break")) : com.beyondsolo.breaks.BreakState.fresh();
        return g;
    }

    /**
     * A run through a gate opened with a dungeon key. The gate in the world is only a door: it waits for its holder
     * (and anyone with them), seals itself behind the first one through, and the way out leads back to where the key
     * was turned ({@link #returnPos}), even when the run was opened from inside another cleared cave (a chain).
     */
    public static final class Keyed {
        /** A time that has not come yet (any real game time, even one counted back from, is greater). */
        public static final long NEVER = Long.MIN_VALUE;

        public final UUID owner;
        /** 1 for a run opened in the world, one more for each run opened straight from the last one's cleared cave. */
        public final int chain;
        public final ResourceKey<Level> returnDim;
        public final net.minecraft.world.phys.Vec3 returnPos;
        public final float returnYaw;
        /** The key's seal: the boss it was bound to (or null), and whether that boss comes ascended. */
        @Nullable public final BossKind sigil;
        public final boolean ascendant;
        /** Game time the gate opened in the world. */
        public final long openedAt;
        /** Ticks the gate has stood unused while its holder was online (it gives up after a while, see key.KeyedRuns). */
        public int waited;
        /** Game time the first player went through, {@link #NEVER} until then. The door seals a little after. */
        public long firstEntryAt = NEVER;
        /** Game time the run's clock started (the first arrival inside), {@link #NEVER} until then. */
        public long runStartedAt = NEVER;
        /** Ticks from the first arrival to the boss's fall, -1 until cleared. */
        public long clearTicks = -1;
        /** The door in the world has been closed (sealed behind the party, or given up). */
        public boolean doorClosed;
        /** The key went back to its holder because nobody went through. */
        public boolean refunded;
        /** Players inside the cave right now, those who logged out in there included. */
        public final java.util.Set<UUID> inside = new java.util.HashSet<>();

        public Keyed(UUID owner, int chain, ResourceKey<Level> returnDim, net.minecraft.world.phys.Vec3 returnPos, float returnYaw,
                     @Nullable BossKind sigil, boolean ascendant, long openedAt) {
            this.owner = owner;
            this.chain = Math.max(1, chain);
            this.returnDim = returnDim;
            this.returnPos = returnPos;
            this.returnYaw = returnYaw;
            this.sigil = sigil;
            this.ascendant = ascendant;
            this.openedAt = openedAt;
        }

        /** True once anyone has gone through the door. */
        public boolean entered() {
            return firstEntryAt != NEVER;
        }

        /** True once the run's clock is running (someone arrived inside). */
        public boolean clockStarted() {
            return runStartedAt != NEVER;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("owner", owner);
            t.putInt("chain", chain);
            t.putString("dim", returnDim.location().toString());
            t.putDouble("x", returnPos.x);
            t.putDouble("y", returnPos.y);
            t.putDouble("z", returnPos.z);
            t.putFloat("yaw", returnYaw);
            if (sigil != null) t.putString("sigil", sigil.id);
            t.putBoolean("ascendant", ascendant);
            t.putLong("openedAt", openedAt);
            t.putInt("waited", waited);
            if (firstEntryAt != NEVER) t.putLong("firstEntry", firstEntryAt);
            if (runStartedAt != NEVER) t.putLong("runStarted", runStartedAt);
            t.putLong("clearTicks", clearTicks);
            t.putBoolean("doorClosed", doorClosed);
            t.putBoolean("refunded", refunded);
            net.minecraft.nbt.ListTag in = new net.minecraft.nbt.ListTag();
            for (UUID id : inside) in.add(NbtUtils.createUUID(id));
            t.put("inside", in);
            return t;
        }

        static Keyed load(CompoundTag t) {
            Keyed k = new Keyed(t.getUUID("owner"), t.getInt("chain"),
                    ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString("dim"))),
                    new net.minecraft.world.phys.Vec3(t.getDouble("x"), t.getDouble("y"), t.getDouble("z")), t.getFloat("yaw"),
                    t.contains("sigil") ? BossKind.byId(t.getString("sigil")) : null, t.getBoolean("ascendant"), t.getLong("openedAt"));
            k.waited = t.getInt("waited");
            k.firstEntryAt = t.contains("firstEntry") ? t.getLong("firstEntry") : NEVER;
            k.runStartedAt = t.contains("runStarted") ? t.getLong("runStarted") : NEVER;
            k.clearTicks = t.contains("clearTicks") ? t.getLong("clearTicks") : -1;
            k.doorClosed = t.getBoolean("doorClosed");
            k.refunded = t.getBoolean("refunded");
            net.minecraft.nbt.ListTag in = t.getList("inside", net.minecraft.nbt.Tag.TAG_INT_ARRAY);
            for (int i = 0; i < in.size(); i++) k.inside.add(NbtUtils.loadUUID(in.get(i)));
            return k;
        }
    }
}
