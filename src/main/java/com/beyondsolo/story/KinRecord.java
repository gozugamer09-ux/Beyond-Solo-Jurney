package com.beyondsolo.story;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.UUID;

/**
 * What the hunter's own story keeps ({@code beyondsolo:kin}, kept through death): how far the Long Sleep has come,
 * the village they were evaluated in, the bed their mother sleeps in, who she is in the world and her looks, the
 * Citadel souls gathered for her cure, and the days she woke and last cooked for them.
 */
public class KinRecord implements INBTSerializable<CompoundTag> {
    /** How far the Long Sleep has come ({@link LongSleep}'s stages). */
    int stage;
    /** The Evaluation Crystal of the hunter's village: her bed is found near it. */
    @Nullable ResourceKey<Level> anchorDim;
    @Nullable BlockPos anchor;
    /** The head of the bed she lies in. */
    @Nullable ResourceKey<Level> bedDim;
    @Nullable BlockPos bed;
    /** Her entity, once she has been put in the world. */
    @Nullable UUID kin;
    /** Which of her looks (-1: not chosen yet). */
    int look = -1;
    /** Souls taken in the Crimson Citadel for the Elixir of Waking (kept here, so a lost quest loses none). */
    int souls;
    /** The in-game day she woke ({@link #NOT_YET}: still asleep) and the last day she cooked for the hunter. */
    long wokeDay = NOT_YET;
    long mealDay = NOT_YET;

    /** A day that has not come (day 0 is a real day, and the day before it too). */
    public static final long NOT_YET = Long.MIN_VALUE;

    public int stage() { return stage; }
    public boolean awake() { return wokeDay != NOT_YET; }
    public @Nullable BlockPos bed() { return bed; }
    public @Nullable ResourceKey<Level> bedDimension() { return bedDim; }
    public @Nullable BlockPos anchor() { return anchor; }
    public @Nullable ResourceKey<Level> anchorDimension() { return anchorDim; }
    public @Nullable UUID kin() { return kin; }
    public int look() { return look; }
    public int souls() { return souls; }
    public long wokeDay() { return wokeDay; }
    public long mealDay() { return mealDay; }

    void reset() {
        stage = 0;
        anchorDim = null;
        anchor = null;
        bedDim = null;
        bed = null;
        kin = null;
        look = -1;
        souls = 0;
        wokeDay = NOT_YET;
        mealDay = NOT_YET;
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag t = new CompoundTag();
        t.putInt("Stage", stage);
        if (anchor != null && anchorDim != null) {
            t.put("Anchor", NbtUtils.writeBlockPos(anchor));
            t.putString("AnchorDim", anchorDim.location().toString());
        }
        if (bed != null && bedDim != null) {
            t.put("Bed", NbtUtils.writeBlockPos(bed));
            t.putString("BedDim", bedDim.location().toString());
        }
        if (kin != null) t.putUUID("Kin", kin);
        t.putInt("Look", look);
        t.putInt("Souls", souls);
        t.putLong("Woke", wokeDay);
        t.putLong("Meal", mealDay);
        return t;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag t) {
        stage = t.getInt("Stage");
        anchor = NbtUtils.readBlockPos(t, "Anchor").orElse(null);
        anchorDim = dim(t.getString("AnchorDim"));
        if (anchorDim == null) anchor = null;
        bed = NbtUtils.readBlockPos(t, "Bed").orElse(null);
        bedDim = dim(t.getString("BedDim"));
        if (bedDim == null) bed = null;
        kin = t.hasUUID("Kin") ? t.getUUID("Kin") : null;
        look = t.contains("Look") ? t.getInt("Look") : -1;
        souls = t.getInt("Souls");
        wokeDay = t.contains("Woke") ? t.getLong("Woke") : NOT_YET;
        mealDay = t.contains("Meal") ? t.getLong("Meal") : NOT_YET;
    }

    private static @Nullable ResourceKey<Level> dim(String id) {
        ResourceLocation loc = id.isEmpty() ? null : ResourceLocation.tryParse(id);
        return loc == null ? null : ResourceKey.create(Registries.DIMENSION, loc);
    }
}
