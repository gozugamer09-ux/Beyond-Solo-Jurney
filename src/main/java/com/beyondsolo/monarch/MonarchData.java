package com.beyondsolo.monarch;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

/**
 * A hunter's road to the throne and their reign: whether the System has called them, their tries at the throne, and,
 * once crowned, what they rule and what they have done as a monarch. Kept through death. Change it through
 * {@link MonarchAPI}.
 */
public final class MonarchData implements INBTSerializable<CompoundTag> {
    private @Nullable Dominion dominion;
    private long crownedAt;
    /** The System has called them to the throne (the quest was given). */
    private boolean called;
    /** No new try before this game time (after a failed one). */
    private long retryAt;
    private int attempts;
    /** Inside the throne trial right now: where they came from. */
    private boolean inTrial;
    private String returnDimension = "";
    private Vec3 returnPos = Vec3.ZERO;
    private float returnYaw;
    /** Monarchs from beyond the gates that fell while this one fought them, crowned. */
    private int monarchsSlain;
    /** Times their domain held back another monarch's. */
    private int clashes;
    /** Times they used their authority. */
    private int authorities;

    public boolean crowned() { return dominion != null; }
    public @Nullable Dominion dominion() { return dominion; }
    public long crownedAt() { return crownedAt; }
    public boolean called() { return called; }
    public long retryAt() { return retryAt; }
    public int attempts() { return attempts; }
    public boolean inTrial() { return inTrial; }
    public @Nullable ResourceLocation returnDimension() { return returnDimension.isEmpty() ? null : ResourceLocation.tryParse(returnDimension); }
    public Vec3 returnPos() { return returnPos; }
    public float returnYaw() { return returnYaw; }
    public int monarchsSlain() { return monarchsSlain; }
    public int clashes() { return clashes; }
    public int authorities() { return authorities; }

    void crown(@Nullable Dominion dominion, long at) {
        this.dominion = dominion;
        this.crownedAt = dominion == null ? 0 : at;
    }

    void setCalled(boolean called) { this.called = called; }
    void setRetryAt(long retryAt) { this.retryAt = retryAt; }
    void addAttempt() { attempts++; }
    void addMonarchSlain() { monarchsSlain++; }
    void addClash() { clashes++; }
    void addAuthority() { authorities++; }

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
        dominion = null;
        crownedAt = 0;
        called = false;
        retryAt = 0;
        attempts = 0;
        inTrial = false;
        monarchsSlain = 0;
        clashes = 0;
        authorities = 0;
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        if (dominion != null) tag.putString("dominion", dominion.id());
        tag.putLong("crownedAt", crownedAt);
        tag.putBoolean("called", called);
        tag.putLong("retryAt", retryAt);
        tag.putInt("attempts", attempts);
        tag.putBoolean("inTrial", inTrial);
        tag.putString("returnDimension", returnDimension);
        tag.putDouble("returnX", returnPos.x);
        tag.putDouble("returnY", returnPos.y);
        tag.putDouble("returnZ", returnPos.z);
        tag.putFloat("returnYaw", returnYaw);
        tag.putInt("monarchsSlain", monarchsSlain);
        tag.putInt("clashes", clashes);
        tag.putInt("authorities", authorities);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        dominion = tag.contains("dominion") ? Dominion.byId(tag.getString("dominion")) : null;
        crownedAt = tag.getLong("crownedAt");
        called = tag.getBoolean("called");
        retryAt = tag.getLong("retryAt");
        attempts = tag.getInt("attempts");
        inTrial = tag.getBoolean("inTrial");
        returnDimension = tag.getString("returnDimension");
        returnPos = new Vec3(tag.getDouble("returnX"), tag.getDouble("returnY"), tag.getDouble("returnZ"));
        returnYaw = tag.getFloat("returnYaw");
        monarchsSlain = tag.getInt("monarchsSlain");
        clashes = tag.getInt("clashes");
        authorities = tag.getInt("authorities");
    }
}
