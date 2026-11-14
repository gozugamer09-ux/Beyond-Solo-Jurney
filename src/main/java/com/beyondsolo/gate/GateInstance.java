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
    public GateTheme theme() { return GateTheme.of(rank); }
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
    public void setClearedAt(long time) { this.clearedAt = time; }

    public boolean roomTriggered(int room) {
        return (roomsTriggered & (1L << room)) != 0;
    }

    public void triggerRoom(int room) {
        roomsTriggered |= 1L << room;
    }

    public DungeonLayout layout() {
        if (layout == null) layout = DungeonLayout.plan(seed, theme());
        return layout;
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
        return g;
    }
}
