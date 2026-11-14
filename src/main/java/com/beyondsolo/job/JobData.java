package com.beyondsolo.job;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

/**
 * A player's job change state: their subclass, whether the trial is open to them, and where to send them back to
 * if they are inside it. Kept through death. Change it through {@link JobAPI}.
 */
public final class JobData implements INBTSerializable<CompoundTag> {
    private @Nullable Subclass subclass;
    private long changedAt;
    /** The trial has been offered (the level milestone was reached). */
    private boolean unlocked;
    /** No new attempt before this game time (after a failed one). */
    private long retryAt;
    private int attempts;
    /** Inside the trial right now: where they came from. */
    private boolean inTrial;
    private String returnDimension = "";
    private Vec3 returnPos = Vec3.ZERO;
    private float returnYaw;

    public @Nullable Subclass subclass() { return subclass; }
    public boolean changed() { return subclass != null; }
    public long changedAt() { return changedAt; }
    public boolean unlocked() { return unlocked; }
    public long retryAt() { return retryAt; }
    public int attempts() { return attempts; }
    public boolean inTrial() { return inTrial; }
    public @Nullable ResourceLocation returnDimension() { return returnDimension.isEmpty() ? null : ResourceLocation.tryParse(returnDimension); }
    public Vec3 returnPos() { return returnPos; }
    public float returnYaw() { return returnYaw; }

    void setSubclass(@Nullable Subclass subclass, long at) {
        this.subclass = subclass;
        this.changedAt = at;
    }

    void setUnlocked(boolean unlocked) { this.unlocked = unlocked; }
    void setRetryAt(long retryAt) { this.retryAt = retryAt; }
    void addAttempt() { attempts++; }

    void enterTrial(ResourceLocation dimension, Vec3 pos, float yaw) {
        inTrial = true;
        returnDimension = dimension.toString();
        returnPos = pos;
        returnYaw = yaw;
    }

    void leaveTrial() {
        inTrial = false;
    }

    void reset() {
        subclass = null;
        changedAt = 0;
        unlocked = false;
        retryAt = 0;
        attempts = 0;
        inTrial = false;
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        if (subclass != null) tag.putString("subclass", subclass.name());
        tag.putLong("changedAt", changedAt);
        tag.putBoolean("unlocked", unlocked);
        tag.putLong("retryAt", retryAt);
        tag.putInt("attempts", attempts);
        tag.putBoolean("inTrial", inTrial);
        tag.putString("returnDimension", returnDimension);
        tag.putDouble("returnX", returnPos.x);
        tag.putDouble("returnY", returnPos.y);
        tag.putDouble("returnZ", returnPos.z);
        tag.putFloat("returnYaw", returnYaw);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        subclass = Subclass.byName(tag.getString("subclass"));
        changedAt = tag.getLong("changedAt");
        unlocked = tag.getBoolean("unlocked");
        retryAt = tag.getLong("retryAt");
        attempts = tag.getInt("attempts");
        inTrial = tag.getBoolean("inTrial");
        returnDimension = tag.getString("returnDimension");
        returnPos = new Vec3(tag.getDouble("returnX"), tag.getDouble("returnY"), tag.getDouble("returnZ"));
        returnYaw = tag.getFloat("returnYaw");
    }
}
