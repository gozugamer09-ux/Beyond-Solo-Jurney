package com.beyondsolo.isle;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Halcyon raids, kept with the overworld's data: when the Association calls next, the raid under way (if any), and
 * which island stands in the island's world (its layout seed, its nests and the builder's version: a different one is
 * built again before the next landing). Also a count of raids called and won, and what is owed to hunters who were
 * away when an island they fought for was retaken (paid when they come back).
 */
public final class IsleData extends SavedData {
    private static final String NAME = "beyondsolo_isle";

    /** Game time of the next call (-1: not planned yet: the clock starts once a hunter it would call is online). */
    long nextCallAt = -1;
    @Nullable IsleRaid raid;
    /** The island that stands: built by this builder version, for this seed and number of nests (0: none). */
    int builtVersion;
    long builtSeed;
    int builtNests;
    int called;
    int won;
    /** What a retaking owes hunters who were offline when it ended ({@link IsleRaids#payOut}), by hunter. */
    final Map<UUID, CompoundTag> unpaid = new HashMap<>();

    public static IsleData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(IsleData::new, IsleData::load, null), NAME);
    }

    @Nullable
    public IsleRaid raid() {
        return raid;
    }

    public long nextCallAt() {
        return nextCallAt;
    }

    public int called() {
        return called;
    }

    public int won() {
        return won;
    }

    boolean built(long seed, int nests) {
        return builtVersion == IsleBuilder.VERSION && builtSeed == seed && builtNests == nests;
    }

    void setBuilt(long seed, int nests) {
        builtVersion = IsleBuilder.VERSION;
        builtSeed = seed;
        builtNests = nests;
        setDirty();
    }

    void forgetIsland() {
        builtVersion = 0;
        setDirty();
    }

    private static IsleData load(CompoundTag t, HolderLookup.Provider provider) {
        IsleData d = new IsleData();
        d.nextCallAt = t.contains("next") ? t.getLong("next") : -1;
        d.raid = t.contains("raid") ? IsleRaid.load(t.getCompound("raid")) : null;
        d.builtVersion = t.getInt("builtVersion");
        d.builtSeed = t.getLong("builtSeed");
        d.builtNests = t.getInt("builtNests");
        d.called = t.getInt("calledCount");
        d.won = t.getInt("wonCount");
        for (Tag e : t.getList("unpaid", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) e;
            if (c.hasUUID("id")) d.unpaid.put(c.getUUID("id"), c.getCompound("owed"));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag t, HolderLookup.Provider provider) {
        t.putLong("next", nextCallAt);
        if (raid != null) t.put("raid", raid.save());
        t.putInt("builtVersion", builtVersion);
        t.putLong("builtSeed", builtSeed);
        t.putInt("builtNests", builtNests);
        t.putInt("calledCount", called);
        t.putInt("wonCount", won);
        ListTag owed = new ListTag();
        unpaid.forEach((id, tag) -> {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", id);
            c.put("owed", tag);
            owed.add(c);
        });
        t.put("unpaid", owed);
        return t;
    }
}
