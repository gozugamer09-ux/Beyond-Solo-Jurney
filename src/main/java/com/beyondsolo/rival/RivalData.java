package com.beyondsolo.rival;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The rival parties abroad and what they have done, kept with the overworld's data: the parties on their way to a
 * gate, in one or lately done with one, and the gates each hunter of the national rankings has cleared on top of what
 * the rankings already count for them.
 */
public final class RivalData extends SavedData {
    private static final String NAME = "beyondsolo_rivals";

    private final Map<UUID, RivalParty> parties = new LinkedHashMap<>();
    private final Map<String, Integer> clears = new HashMap<>();

    public static RivalData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(RivalData::new, RivalData::load, null), NAME);
    }

    public Collection<RivalParty> parties() {
        return parties.values();
    }

    @Nullable
    public RivalParty party(UUID id) {
        return parties.get(id);
    }

    /** The party still going for a gate (on its way, gathering or in it), or null. */
    @Nullable
    public RivalParty forGate(UUID gate) {
        for (RivalParty p : parties.values()) if (p.active() && p.gate.equals(gate)) return p;
        return null;
    }

    /** The active parties, in the order they were sent. */
    public List<RivalParty> active() {
        List<RivalParty> out = new ArrayList<>();
        for (RivalParty p : parties.values()) if (p.active()) out.add(p);
        return out;
    }

    void add(RivalParty p) {
        parties.put(p.id, p);
        setDirty();
    }

    void remove(UUID id) {
        if (parties.remove(id) != null) setDirty();
    }

    /** Gates a ranked rival has cleared in this world (on top of their record on the rankings). */
    public int clears(String rival) {
        return clears.getOrDefault(rival, 0);
    }

    void addClear(String rival) {
        if (rival == null || rival.isEmpty()) return;
        clears.merge(rival, 1, Integer::sum);
        setDirty();
    }

    /** True if this ranked rival leads a party that is still going. */
    public boolean busy(String rival) {
        for (RivalParty p : parties.values()) if (p.active() && p.rival.equals(rival)) return true;
        return false;
    }

    /** Forgets everything (commands). */
    void clear() {
        parties.clear();
        clears.clear();
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (RivalParty p : parties.values()) list.add(p.save());
        tag.put("parties", list);
        CompoundTag c = new CompoundTag();
        for (var e : clears.entrySet()) c.putInt(e.getKey(), e.getValue());
        tag.put("clears", c);
        return tag;
    }

    private static RivalData load(CompoundTag tag, HolderLookup.Provider provider) {
        RivalData d = new RivalData();
        for (Tag e : tag.getList("parties", Tag.TAG_COMPOUND)) {
            RivalParty p = RivalParty.load((CompoundTag) e);
            if (p != null) d.parties.put(p.id, p);
        }
        CompoundTag c = tag.getCompound("clears");
        for (String k : c.getAllKeys()) d.clears.put(k, c.getInt(k));
        return d;
    }
}
