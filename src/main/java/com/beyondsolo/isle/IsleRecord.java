package com.beyondsolo.isle;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.UUID;

/**
 * A hunter's record of the Halcyon raids ({@code beyondsolo:halcyon}, kept through death): raids landed on and won,
 * nests burned, the queens and kings they had a share in felling, their fastest retaking. While they are on the island
 * it also keeps which raid they came with and where they were taken from, so they are sent back there whatever happens
 * (a raid that ended while they were away, a restart).
 */
public class IsleRecord implements INBTSerializable<CompoundTag> {
    int raids;
    int cleared;
    int nests;
    int queens;
    int kings;
    /** Ticks from the landing to the king's fall, the best of them (0: none yet). */
    long best;
    /** The raid they are on the island with (null: they aren't). */
    @Nullable UUID raid;
    @Nullable ResourceKey<Level> homeDim;
    @Nullable Vec3 home;
    float homeYaw;

    public int raids() { return raids; }
    public int cleared() { return cleared; }
    public int nests() { return nests; }
    public int queens() { return queens; }
    public int kings() { return kings; }
    public long best() { return best; }
    public @Nullable UUID raid() { return raid; }

    void away(UUID raid, ResourceKey<Level> dim, Vec3 at, float yaw) {
        this.raid = raid;
        this.homeDim = dim;
        this.home = at;
        this.homeYaw = yaw;
    }

    void back() {
        raid = null;
        homeDim = null;
        home = null;
        homeYaw = 0;
    }

    void reset() {
        raids = cleared = nests = queens = kings = 0;
        best = 0;
        back();
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag t = new CompoundTag();
        t.putInt("Raids", raids);
        t.putInt("Cleared", cleared);
        t.putInt("Nests", nests);
        t.putInt("Queens", queens);
        t.putInt("Kings", kings);
        t.putLong("Best", best);
        if (raid != null) t.putUUID("Raid", raid);
        if (homeDim != null && home != null) {
            t.putString("HomeDim", homeDim.location().toString());
            t.putDouble("HomeX", home.x);
            t.putDouble("HomeY", home.y);
            t.putDouble("HomeZ", home.z);
            t.putFloat("HomeYaw", homeYaw);
        }
        return t;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag t) {
        raids = t.getInt("Raids");
        cleared = t.getInt("Cleared");
        nests = t.getInt("Nests");
        queens = t.getInt("Queens");
        kings = t.getInt("Kings");
        best = t.getLong("Best");
        raid = t.hasUUID("Raid") ? t.getUUID("Raid") : null;
        ResourceLocation dim = t.contains("HomeDim") ? ResourceLocation.tryParse(t.getString("HomeDim")) : null;
        homeDim = dim == null ? null : ResourceKey.create(Registries.DIMENSION, dim);
        home = homeDim == null ? null : new Vec3(t.getDouble("HomeX"), t.getDouble("HomeY"), t.getDouble("HomeZ"));
        homeYaw = t.getFloat("HomeYaw");
    }
}
