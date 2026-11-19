package com.beyondsolo.manapoison;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.UUID;

/**
 * How much of a gate's thick mana a hunter carries ({@code beyondsolo:mana_poisoning}, 0..100), and the stage it has
 * reached ({@link ManaPoisoning#stage}). Death clears it (the attachment is not kept through death). What the screen
 * was last told is not saved, so a fresh record (a login, a wipe, a respawn) is told again.
 */
public final class ManaPoisonData implements INBTSerializable<CompoundTag> {
    float level;
    /** The stage it was at on the last beat (a pop-up comes as each stage is reached). */
    int stage;
    /** Whether a stage was reached since it was last clean (its going is said once it is all gone). */
    boolean sick;
    /** The gate whose thick mana was last warned about (the warning is said once a gate). */
    @Nullable UUID warnedGate;

    /** What the screen was last told: level (whole points) and flags. -1: nothing yet. */
    int sentLevel = -1;
    int sentFlags = -1;
    /** Beats until the next health drain. */
    int drainBeat;

    public float level() {
        return level;
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("level", level);
        tag.putInt("stage", stage);
        tag.putBoolean("sick", sick);
        if (warnedGate != null) tag.putUUID("warned", warnedGate);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        level = Math.max(0, Math.min(ManaPoisoning.MAX, tag.getFloat("level")));
        stage = tag.getInt("stage");
        sick = tag.getBoolean("sick");
        warnedGate = tag.hasUUID("warned") ? tag.getUUID("warned") : null;
    }
}
