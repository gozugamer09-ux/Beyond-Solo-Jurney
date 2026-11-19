package com.beyondsolo.home;

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
 * A hunter's home ({@code beyondsolo:residence}, kept through death): how much of it they own, where they were before
 * they came home (or to a party member's home) and go back to, whose home they are visiting, the day they last slept
 * well in their own bed, whether their mother has moved in, their best at the dummies.
 */
public class HomeRecord implements INBTSerializable<CompoundTag> {
    int tier;
    /** Where they were before they came in, and go back to when they leave. */
    @Nullable ResourceKey<Level> awayDim;
    @Nullable Vec3 away;
    float awayYaw;
    /** The hunter whose home they are in as a guest (null: their own, or none). */
    @Nullable UUID visiting;
    /** Game time they may next go home ({@link HomeConfig#cooldownTicks}). */
    long readyAt;
    /** The in-game day they last woke Well Rested ({@link #NEVER}: never). */
    long restDay = NEVER;
    /** Their mother lives here now (taken away, she finds a bed in her village again: {@code story.Kin}). */
    boolean motherHere;
    /** The most damage a second they have dealt a training dummy, over ten seconds. */
    float bestDps;
    /** The tiers they have walked into at least once (bits), for each one's welcome. */
    int welcomed;

    /** A day that has not come. */
    public static final long NEVER = Long.MIN_VALUE;

    public HomeTier tier() {
        return HomeTier.of(tier);
    }

    public boolean owns() {
        return tier > 0;
    }

    public @Nullable UUID visiting() {
        return visiting;
    }

    public long restDay() {
        return restDay;
    }

    public boolean motherHere() {
        return motherHere;
    }

    public float bestDps() {
        return bestDps;
    }

    public long readyAt() {
        return readyAt;
    }

    public @Nullable ResourceKey<Level> awayDimension() {
        return awayDim;
    }

    public @Nullable Vec3 away() {
        return away;
    }

    void setAway(ResourceKey<Level> dim, Vec3 at, float yaw) {
        awayDim = dim;
        away = at;
        awayYaw = yaw;
    }

    void clearAway() {
        awayDim = null;
        away = null;
        awayYaw = 0;
        visiting = null;
    }

    void reset() {
        tier = 0;
        clearAway();
        readyAt = 0;
        restDay = NEVER;
        motherHere = false;
        bestDps = 0;
        welcomed = 0;
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag t = new CompoundTag();
        t.putInt("Tier", tier);
        if (awayDim != null && away != null) {
            t.putString("AwayDim", awayDim.location().toString());
            t.putDouble("AwayX", away.x);
            t.putDouble("AwayY", away.y);
            t.putDouble("AwayZ", away.z);
            t.putFloat("AwayYaw", awayYaw);
        }
        if (visiting != null) t.putUUID("Visiting", visiting);
        t.putLong("ReadyAt", readyAt);
        if (restDay != NEVER) t.putLong("RestDay", restDay);
        t.putBoolean("MotherHere", motherHere);
        t.putFloat("BestDps", bestDps);
        t.putInt("Welcomed", welcomed);
        return t;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag t) {
        tier = t.getInt("Tier");
        ResourceLocation dim = t.contains("AwayDim") ? ResourceLocation.tryParse(t.getString("AwayDim")) : null;
        awayDim = dim == null ? null : ResourceKey.create(Registries.DIMENSION, dim);
        away = awayDim == null ? null : new Vec3(t.getDouble("AwayX"), t.getDouble("AwayY"), t.getDouble("AwayZ"));
        awayYaw = t.getFloat("AwayYaw");
        visiting = t.hasUUID("Visiting") ? t.getUUID("Visiting") : null;
        readyAt = t.getLong("ReadyAt");
        restDay = t.contains("RestDay") ? t.getLong("RestDay") : NEVER;
        motherHere = t.getBoolean("MotherHere");
        bestDps = t.getFloat("BestDps");
        welcomed = t.getInt("Welcomed");
    }
}
