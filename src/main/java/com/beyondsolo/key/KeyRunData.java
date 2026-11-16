package com.beyondsolo.key;

import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.Arrays;

/**
 * A hunter's keyed-run records ({@code beyondsolo:key_runs}, kept through death): keys turned and runs cleared per
 * rank, the best clear time per rank, the longest chain, and the last run (what the GATES tab shows).
 */
public final class KeyRunData implements INBTSerializable<CompoundTag> {
    private static final int RANKS = PlayerRank.values().length;

    public final int[] turned = new int[RANKS];
    public final int[] cleared = new int[RANKS];
    /** Best clear time per rank, in ticks; -1 for none yet. */
    public final long[] best = new long[RANKS];
    /** The most runs cleared in one chain. */
    public int longestChain;
    /** The last run cleared: its rank (-1 for none), time, place in its chain, and whether it set a best time. */
    public int lastRank = -1;
    public long lastTicks = -1;
    public int lastChain;
    public boolean lastBest;

    public KeyRunData() {
        Arrays.fill(best, -1);
    }

    public int totalCleared() {
        int n = 0;
        for (int c : cleared) n += c;
        return n;
    }

    public int totalTurned() {
        int n = 0;
        for (int c : turned) n += c;
        return n;
    }

    /** Records a cleared run; true if its time is a new best for its rank. */
    public boolean clear(PlayerRank rank, long ticks, int chain) {
        int t = rank.tier();
        cleared[t]++;
        boolean newBest = ticks >= 0 && (best[t] < 0 || ticks < best[t]);
        if (newBest) best[t] = ticks;
        longestChain = Math.max(longestChain, chain);
        lastRank = t;
        lastTicks = ticks;
        lastChain = chain;
        lastBest = newBest;
        return newBest;
    }

    public void reset() {
        Arrays.fill(turned, 0);
        Arrays.fill(cleared, 0);
        Arrays.fill(best, -1);
        longestChain = 0;
        lastRank = -1;
        lastTicks = -1;
        lastChain = 0;
        lastBest = false;
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("turned", turned);
        tag.putIntArray("cleared", cleared);
        tag.putLongArray("best", best);
        tag.putInt("longestChain", longestChain);
        tag.putInt("lastRank", lastRank);
        tag.putLong("lastTicks", lastTicks);
        tag.putInt("lastChain", lastChain);
        tag.putBoolean("lastBest", lastBest);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        reset();
        copy(tag.getIntArray("turned"), turned);
        copy(tag.getIntArray("cleared"), cleared);
        long[] b = tag.getLongArray("best");
        for (int i = 0; i < Math.min(b.length, best.length); i++) best[i] = b[i];
        longestChain = tag.getInt("longestChain");
        lastRank = tag.contains("lastRank") ? tag.getInt("lastRank") : -1;
        lastTicks = tag.contains("lastTicks") ? tag.getLong("lastTicks") : -1;
        lastChain = tag.getInt("lastChain");
        lastBest = tag.getBoolean("lastBest");
    }

    private static void copy(int[] from, int[] to) {
        for (int i = 0; i < Math.min(from.length, to.length); i++) to[i] = from[i];
    }
}
