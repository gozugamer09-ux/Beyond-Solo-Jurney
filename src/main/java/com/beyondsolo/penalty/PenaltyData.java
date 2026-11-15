package com.beyondsolo.penalty;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

/**
 * A player's Penalty Zone state: a sentence waiting to be served, whether they are inside right now (and where to
 * send them back to), and how many they have served or failed. Kept through death.
 */
public final class PenaltyData implements INBTSerializable<CompoundTag> {
    /** A sentence is owed: the zone starts as soon as the player is free (not in a gate, a trial...). */
    private boolean pending;
    /** What the player failed (the quest's title), for the warning. */
    private Component reason = Component.empty();
    /** Not before this game time (a short grace after logging back in). */
    private long notBefore;
    private boolean inZone;
    private String returnDimension = "";
    private Vec3 returnPos = Vec3.ZERO;
    private float returnYaw;
    private int served;
    private int failed;

    public boolean pending() { return pending; }
    public Component reason() { return reason; }
    public long notBefore() { return notBefore; }
    public boolean inZone() { return inZone; }
    public @Nullable ResourceLocation returnDimension() { return returnDimension.isEmpty() ? null : ResourceLocation.tryParse(returnDimension); }
    public Vec3 returnPos() { return returnPos; }
    public float returnYaw() { return returnYaw; }
    public int served() { return served; }
    public int failed() { return failed; }

    void sentence(Component reason) {
        this.pending = true;
        this.reason = reason;
    }

    void clearSentence() {
        pending = false;
        reason = Component.empty();
    }

    void setNotBefore(long time) { this.notBefore = time; }

    void enter(ResourceLocation dimension, Vec3 pos, float yaw) {
        inZone = true;
        returnDimension = dimension.toString();
        returnPos = pos;
        returnYaw = yaw;
    }

    void leave() { inZone = false; }
    void addServed() { served++; }
    void addFailed() { failed++; }

    void reset() {
        pending = false;
        reason = Component.empty();
        notBefore = 0;
        inZone = false;
        served = 0;
        failed = 0;
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("pending", pending);
        tag.putString("reason", Component.Serializer.toJson(reason, provider));
        tag.putLong("notBefore", notBefore);
        tag.putBoolean("inZone", inZone);
        tag.putString("returnDimension", returnDimension);
        tag.putDouble("returnX", returnPos.x);
        tag.putDouble("returnY", returnPos.y);
        tag.putDouble("returnZ", returnPos.z);
        tag.putFloat("returnYaw", returnYaw);
        tag.putInt("served", served);
        tag.putInt("failed", failed);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        pending = tag.getBoolean("pending");
        Component r = null;
        try {
            if (!tag.getString("reason").isEmpty()) r = Component.Serializer.fromJson(tag.getString("reason"), provider);
        } catch (RuntimeException ignored) {
        }
        reason = r == null ? Component.empty() : r;
        notBefore = tag.getLong("notBefore");
        inZone = tag.getBoolean("inZone");
        returnDimension = tag.getString("returnDimension");
        returnPos = new Vec3(tag.getDouble("returnX"), tag.getDouble("returnY"), tag.getDouble("returnZ"));
        returnYaw = tag.getFloat("returnYaw");
        served = tag.getInt("served");
        failed = tag.getInt("failed");
    }
}
