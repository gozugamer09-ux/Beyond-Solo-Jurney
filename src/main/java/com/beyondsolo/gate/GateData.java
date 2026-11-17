package com.beyondsolo.gate;

import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Every gate the world knows, by id, and which slots of the gate dimension are taken. Kept with the overworld's data.
 * Once a closed gate's cave has been wiped back to rock ({@link GateWiper}), its slot goes into {@link #freeSlots} and
 * the next gate takes it, so the gate dimension (and the save) stops growing however many gates are run.
 */
public final class GateData extends SavedData {
    private static final String NAME = "beyondsolo_gates";
    /** Caves stay within this distance of z = 0 (the job change trial's halls are far away, on z = 60 000). */
    public static final int LANE_HALF_WIDTH = 8192;

    private final Map<UUID, GateInstance> gates = new LinkedHashMap<>();
    private int nextSlot = 1;
    private final java.util.TreeSet<Integer> freeSlots = new java.util.TreeSet<>();

    public static GateData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(GateData::new, GateData::load, null), NAME);
    }

    @Nullable
    public GateInstance get(UUID id) {
        return gates.get(id);
    }

    public Collection<GateInstance> all() {
        return gates.values();
    }

    /** Registers a new gate standing at {@code pos} and gives its cave a slot: a freed one if there is one. */
    public GateInstance create(UUID id, PlayerRank rank, long seed, ResourceKey<Level> dimension, BlockPos pos) {
        Integer free = freeSlots.pollFirst();
        while (free != null && inUse(free)) free = freeSlots.pollFirst();
        GateInstance g = new GateInstance(id, rank, seed, free != null ? free : nextSlot++, dimension, pos);
        gates.put(id, g);
        setDirty();
        return g;
    }

    /** The slot of the gate dimension a position lies in, or -1 outside the lane caves are carved along. */
    public static int slotAt(BlockPos pos) {
        if (Math.abs(pos.getZ()) > LANE_HALF_WIDTH) return -1;
        return Math.floorDiv(pos.getX() + GateInstance.SLOT_SPACING / 2, GateInstance.SLOT_SPACING);
    }

    /**
     * The gate whose cave holds this position of the gate dimension, if any: the one using the slot now (a slot that
     * was wiped and handed on belongs to its new gate), else the closed one still waiting to be wiped.
     */
    @Nullable
    public GateInstance bySlotPos(BlockPos pos) {
        int slot = slotAt(pos);
        if (slot < 0) return null;
        GateInstance closed = null;
        for (GateInstance g : gates.values()) {
            if (g.slot() != slot || g.slotFreed()) continue;
            if (g.state() != GateInstance.State.CLOSED) return g;
            closed = g;
        }
        return closed;
    }

    private boolean inUse(int slot) {
        for (GateInstance g : gates.values()) if (g.slot() == slot && !g.slotFreed()) return true;
        return false;
    }

    /** A wiped cave's slot can be used again. */
    public void freeSlot(int slot) {
        if (slot > 0 && slot < nextSlot) freeSlots.add(slot);
        setDirty();
    }

    /** Slots waiting for a new gate (tests, commands). */
    public java.util.SortedSet<Integer> freeSlots() {
        return java.util.Collections.unmodifiableSortedSet(freeSlots);
    }

    /** Forgets a gate whose cave has been wiped. */
    public void remove(UUID id) {
        if (gates.remove(id) != null) setDirty();
    }

    private static GateData load(CompoundTag tag, HolderLookup.Provider registries) {
        GateData d = new GateData();
        d.nextSlot = Math.max(1, tag.getInt("nextSlot"));
        for (int slot : tag.getIntArray("freeSlots")) if (slot > 0 && slot < d.nextSlot) d.freeSlots.add(slot);
        ListTag list = tag.getList("gates", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            GateInstance g = GateInstance.load(list.getCompound(i));
            d.gates.put(g.id(), g);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("nextSlot", nextSlot);
        tag.putIntArray("freeSlots", freeSlots.stream().mapToInt(Integer::intValue).toArray());
        ListTag list = new ListTag();
        for (GateInstance g : gates.values()) list.add(g.save());
        tag.put("gates", list);
        return tag;
    }
}
