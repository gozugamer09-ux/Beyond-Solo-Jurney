package com.beyondsolo.tower;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.Arrays;

/**
 * A hunter's Crimson Citadel ({@code beyondsolo:citadel}, kept through death): whether the key is bound to them, how
 * far up the castle they are safe from (the last gatekeeper beaten), the lord's falls and the Sigil of Dominion, the
 * best floor reached in the Endless Spire, the boss rush records, and while inside, where to send them back to.
 * The client gets a copy (the CITADEL page of the System window draws from it).
 */
public final class TowerData implements INBTSerializable<CompoundTag> {
    public static final int TIERS = RushTier.values().length;

    /** The Key to the Crimson Citadel has been bound: the door opens for them. */
    public boolean bound;
    /** The last gatekeeper floor beaten (0 none, 10..90, 100 once the lord has fallen): a climb starts above it. */
    public int checkpoint;
    /** The highest castle floor ever reached. */
    public int highest;
    public int lordKills;
    /** They have held the Sigil of Dominion (kept even if the item is lost). */
    public boolean sigil;
    /** Lord kills since the last sigil (or ever): the pity count. */
    public int sigilDry;
    /** The highest floor of the Endless Spire cleared (0 for none). */
    public int spireBest;
    public int spireRuns;
    public int floorsCleared;
    public int falls;
    /** Best boss rush time per tier in ticks (-1 none) and clears per tier. */
    public final long[] rushBest = new long[TIERS];
    public final int[] rushClears = new int[TIERS];
    /** The System has told them where the key is sold. */
    public boolean told;

    private boolean inside;
    private String returnDimension = "";
    private Vec3 returnPos = Vec3.ZERO;
    private float returnYaw;

    public TowerData() {
        Arrays.fill(rushBest, -1);
    }

    public boolean inside() { return inside; }
    public @Nullable ResourceLocation returnDimension() { return returnDimension.isEmpty() ? null : ResourceLocation.tryParse(returnDimension); }
    public Vec3 returnPos() { return returnPos; }
    public float returnYaw() { return returnYaw; }

    /** The lord has fallen to them at least once: the throne can be challenged again and the spire climbed. */
    public boolean conquered() {
        return lordKills > 0;
    }

    /** The floor a castle climb starts on: just above the checkpoint (the last ten floors again once the lord is beaten). */
    public int startFloor() {
        if (checkpoint >= TowerFloors.TOP) return TowerFloors.TOP - TowerFloors.KEEPER_EVERY + 1;
        return checkpoint + 1;
    }

    void enter(ResourceLocation dimension, Vec3 pos, float yaw) {
        inside = true;
        returnDimension = dimension.toString();
        returnPos = pos;
        returnYaw = yaw;
    }

    void leave() {
        inside = false;
    }

    /** Records a rush clear; true if the time is the tier's best. */
    public boolean rushCleared(RushTier tier, long ticks) {
        int t = tier.ordinal();
        rushClears[t]++;
        boolean best = ticks >= 0 && (rushBest[t] < 0 || ticks < rushBest[t]);
        if (best) rushBest[t] = ticks;
        return best;
    }

    public void reset() {
        CompoundTag empty = new CompoundTag();
        deserializeNBT(null, empty);
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("bound", bound);
        tag.putInt("checkpoint", checkpoint);
        tag.putInt("highest", highest);
        tag.putInt("lordKills", lordKills);
        tag.putBoolean("sigil", sigil);
        tag.putInt("sigilDry", sigilDry);
        tag.putInt("spireBest", spireBest);
        tag.putInt("spireRuns", spireRuns);
        tag.putInt("floorsCleared", floorsCleared);
        tag.putInt("falls", falls);
        tag.putLongArray("rushBest", rushBest);
        tag.putIntArray("rushClears", rushClears);
        tag.putBoolean("told", told);
        tag.putBoolean("inside", inside);
        tag.putString("returnDimension", returnDimension);
        tag.putDouble("returnX", returnPos.x);
        tag.putDouble("returnY", returnPos.y);
        tag.putDouble("returnZ", returnPos.z);
        tag.putFloat("returnYaw", returnYaw);
        return tag;
    }

    @Override
    public void deserializeNBT(@Nullable HolderLookup.Provider provider, CompoundTag tag) {
        bound = tag.getBoolean("bound");
        checkpoint = tag.getInt("checkpoint");
        highest = tag.getInt("highest");
        lordKills = tag.getInt("lordKills");
        sigil = tag.getBoolean("sigil");
        sigilDry = tag.getInt("sigilDry");
        spireBest = tag.getInt("spireBest");
        spireRuns = tag.getInt("spireRuns");
        floorsCleared = tag.getInt("floorsCleared");
        falls = tag.getInt("falls");
        Arrays.fill(rushBest, -1);
        Arrays.fill(rushClears, 0);
        long[] best = tag.getLongArray("rushBest");
        int[] clears = tag.getIntArray("rushClears");
        for (int i = 0; i < TIERS; i++) {
            if (i < best.length) rushBest[i] = best[i];
            if (i < clears.length) rushClears[i] = clears[i];
        }
        told = tag.getBoolean("told");
        inside = tag.getBoolean("inside");
        returnDimension = tag.getString("returnDimension");
        returnPos = new Vec3(tag.getDouble("returnX"), tag.getDouble("returnY"), tag.getDouble("returnZ"));
        returnYaw = tag.getFloat("returnYaw");
    }
}
