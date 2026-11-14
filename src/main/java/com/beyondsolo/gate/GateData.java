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

/** Every gate the world has seen, by id, and the next free slot in the gate dimension. Kept with the overworld's data. */
public final class GateData extends SavedData {
    private static final String NAME = "beyondsolo_gates";

    private final Map<UUID, GateInstance> gates = new LinkedHashMap<>();
    private int nextSlot = 1;

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

    /** Registers a new gate standing at {@code pos} and gives its cave a slot. */
    public GateInstance create(UUID id, PlayerRank rank, long seed, ResourceKey<Level> dimension, BlockPos pos) {
        GateInstance g = new GateInstance(id, rank, seed, nextSlot++, dimension, pos);
        gates.put(id, g);
        setDirty();
        return g;
    }

    /** The open gate whose cave holds this position of the gate dimension, if any. */
    @Nullable
    public GateInstance bySlotPos(BlockPos pos) {
        int slot = Math.floorDiv(pos.getX() + GateInstance.SLOT_SPACING / 2, GateInstance.SLOT_SPACING);
        for (GateInstance g : gates.values()) if (g.slot() == slot) return g;
        return null;
    }

    private static GateData load(CompoundTag tag, HolderLookup.Provider registries) {
        GateData d = new GateData();
        d.nextSlot = Math.max(1, tag.getInt("nextSlot"));
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
        ListTag list = new ListTag();
        for (GateInstance g : gates.values()) list.add(g.save());
        tag.put("gates", list);
        return tag;
    }
}
