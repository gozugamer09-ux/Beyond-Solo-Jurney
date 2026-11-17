package com.beyondsolo.redgate;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.UnknownNullability;

/**
 * A hunter's red gates: how many they cleared (its master slain), escaped (outlasted its hold), and fell in, and the
 * most days they spent inside one. Kept through death; titles and the leaderboard can read it.
 */
public final class RedRecord implements INBTSerializable<CompoundTag> {
    private int cleared;
    private int escaped;
    private int fallen;
    private int mostDays;
    private int nights;

    public int cleared() { return cleared; }
    public int escaped() { return escaped; }
    public int fallen() { return fallen; }
    public int mostDays() { return mostDays; }
    /** Nights lived through inside red gates, all told. */
    public int nights() { return nights; }

    void addCleared(int days) {
        cleared++;
        mostDays = Math.max(mostDays, days);
    }

    void addEscaped(int days) {
        escaped++;
        mostDays = Math.max(mostDays, days);
    }

    void addFallen() { fallen++; }
    void addNight() { nights++; }

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("cleared", cleared);
        tag.putInt("escaped", escaped);
        tag.putInt("fallen", fallen);
        tag.putInt("mostDays", mostDays);
        tag.putInt("nights", nights);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        cleared = tag.getInt("cleared");
        escaped = tag.getInt("escaped");
        fallen = tag.getInt("fallen");
        mostDays = tag.getInt("mostDays");
        nights = tag.getInt("nights");
    }
}
