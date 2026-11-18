package com.beyondsolo.redgate;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every red gate the world has seen, by the id of its gate, with the gates that will never turn red (someone without the
 * System went through first) or were made red by a command, and the next free patch of the frozen world. Kept with the
 * overworld's data.
 */
public final class RedGateData extends SavedData {
    private static final String NAME = "beyondsolo_red_gates";
    /** Ended red gates are kept this many at most (for hunters who logged out inside one), oldest dropped first. */
    private static final int KEEP_ENDED = 64;

    private final Map<UUID, RedGate> runs = new LinkedHashMap<>();
    private final Set<UUID> defused = new HashSet<>();
    private final Set<UUID> forced = new HashSet<>();
    private int nextSlot = 1;

    public static RedGateData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(RedGateData::new, RedGateData::load, null), NAME);
    }

    @Nullable
    public RedGate get(UUID gate) {
        return runs.get(gate);
    }

    public Collection<RedGate> all() {
        return runs.values();
    }

    void add(RedGate run) {
        runs.put(run.gate, run);
        prune();
        setDirty();
    }

    int takeSlot() {
        setDirty();
        return nextSlot++;
    }

    boolean defused(UUID gate) {
        return defused.contains(gate);
    }

    /** It will never turn red (again), whatever forced it before. */
    void defuse(UUID gate) {
        boolean changed = defused.add(gate);
        if (forced.remove(gate)) changed = true;
        if (changed) setDirty();
    }

    /** Whether the gate is set to turn red when the first awakened hunter goes through. */
    public boolean forced(UUID gate) {
        return forced.contains(gate);
    }

    /** Makes a gate red (it turns when an awakened hunter is the first through it), or takes that back. */
    public void force(UUID gate, boolean on) {
        if (on ? forced.add(gate) : forced.remove(gate)) setDirty();
        if (on) defused.remove(gate);
    }

    /** Forgets what is known about a gate that is gone (its record was wiped). Runs are kept for their hunters. */
    void forgetGate(UUID gate) {
        if (defused.remove(gate) | forced.remove(gate)) setDirty();
    }

    /** Forgets every gate {@code gone} says is gone (the gate's record was wiped). */
    void forgetGone(java.util.function.Predicate<UUID> gone) {
        if (defused.removeIf(gone) | forced.removeIf(gone)) setDirty();
    }

    private void prune() {
        int ended = 0;
        for (RedGate r : runs.values()) if (r.state == RedGate.State.ENDED && r.insideNow.isEmpty()) ended++;
        var it = runs.values().iterator();
        while (ended > KEEP_ENDED && it.hasNext()) {
            RedGate r = it.next();
            if (r.state == RedGate.State.ENDED && r.insideNow.isEmpty()) {
                it.remove();
                ended--;
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (RedGate r : runs.values()) list.add(r.save());
        tag.put("runs", list);
        tag.put("defused", RedGate.uuids(defused));
        tag.put("forced", RedGate.uuids(forced));
        tag.putInt("nextSlot", nextSlot);
        return tag;
    }

    private static RedGateData load(CompoundTag tag, HolderLookup.Provider registries) {
        RedGateData d = new RedGateData();
        ListTag list = tag.getList("runs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            RedGate r = RedGate.load(list.getCompound(i));
            if (r != null) d.runs.put(r.gate, r);
        }
        RedGate.readUuids(tag.getList("defused", Tag.TAG_INT_ARRAY), d.defused);
        RedGate.readUuids(tag.getList("forced", Tag.TAG_INT_ARRAY), d.forced);
        d.nextSlot = Math.max(1, tag.getInt("nextSlot"));
        return d;
    }
}
