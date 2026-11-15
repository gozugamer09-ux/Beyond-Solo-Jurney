package com.beyondsolo.evaluation;

import com.beyondsolo.system.Stat;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.UnknownNullability;

/**
 * What the crystal remembers about one player between evaluations: the milestone they were last read at, their
 * stats at that moment (the re-evaluation shows growth since then), the highest hidden rank the System already
 * hinted at, and the stat points they spent by hand (an Elixir of Rebirth gives exactly those back).
 * Server only; the client hears just "available" and "next level" (see {@link Reevaluation#sync}).
 */
public final class EvaluationRecord implements INBTSerializable<CompoundTag> {
    /** No evaluation recorded yet (or a save from before re-evaluation). */
    static final int NONE = Integer.MIN_VALUE;
    /** Milestone of the last evaluation, or {@link #NONE}. Can be -1 after {@code /system reeval unlock} at level 1-9. */
    int lastMilestone = NONE;
    final int[] snapshot = new int[Stat.values().length];
    /** Ordinal of the highest rank already hinted at (or held), -1 = none. */
    int hinted = -1;
    final int[] allocated = new int[Stat.values().length];
    int reevaluations;

    public EvaluationRecord() {
        java.util.Arrays.fill(snapshot, Stat.BASE);
    }

    public int lastMilestone() { return lastMilestone; }
    public int snapshot(Stat stat) { return snapshot[stat.ordinal()]; }
    public int allocated(Stat stat) { return allocated[stat.ordinal()]; }
    public int reevaluations() { return reevaluations; }

    void reset() {
        lastMilestone = NONE;
        java.util.Arrays.fill(snapshot, Stat.BASE);
        java.util.Arrays.fill(allocated, 0);
        hinted = -1;
        reevaluations = 0;
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("lastMilestone", lastMilestone);
        tag.putIntArray("snapshot", snapshot);
        tag.putInt("hinted", hinted);
        tag.putIntArray("allocated", allocated);
        tag.putInt("reevaluations", reevaluations);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        lastMilestone = tag.contains("lastMilestone") ? tag.getInt("lastMilestone") : NONE;
        copy(tag.getIntArray("snapshot"), snapshot, Stat.BASE);
        hinted = tag.contains("hinted") ? tag.getInt("hinted") : -1;
        copy(tag.getIntArray("allocated"), allocated, 0);
        reevaluations = tag.getInt("reevaluations");
    }

    private static void copy(int[] from, int[] into, int fallback) {
        for (int i = 0; i < into.length; i++) into[i] = i < from.length ? from[i] : fallback;
    }
}
