package com.beyondsolo.worldboss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Something a great threat throws or shoots: a boulder, an ice spear, a storm orb, a blood shard, and the rulers' spears
 * of light, feathers of light and the stones their unseen hands throw. It flies a set path
 * (straight, or in an arc) from where it was thrown to where it lands, in a set time, so it lands exactly as the strike
 * announced on the ground does ({@link ColossalBoss#strike}); the strike does the harm, the throw is what you see coming.
 * It is never saved.
 */
public class BossProjectile extends Entity {
    public enum Kind { BOULDER, ICE_SPEAR, STORM_ORB, BLOOD_SHARD, LIGHT_SPEAR, FEATHER, HURLED_STONE, ACID_GLOB }

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(BossProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(BossProjectile.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(BossProjectile.class, EntityDataSerializers.INT);

    private Vec3 from = Vec3.ZERO;
    private Vec3 to = Vec3.ZERO;
    private int flight = 20;
    private float arc;
    private int age;

    public BossProjectile(EntityType<? extends BossProjectile> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** Throws one: from {@code from} to {@code to} in {@code ticks}, rising {@code arc} blocks at the middle of its path. */
    public static BossProjectile launch(ServerLevel level, Kind kind, Vec3 from, Vec3 to, int ticks, float arc, float scale, int color) {
        BossProjectile p = new BossProjectile(WorldBossModule.PROJECTILE.get(), level);
        p.from = from;
        p.to = to;
        p.flight = Math.max(1, ticks);
        p.arc = arc;
        p.entityData.set(KIND, kind.ordinal());
        p.entityData.set(SCALE, scale);
        p.entityData.set(COLOR, color);
        p.setPos(from.x, from.y, from.z);
        level.addFreshEntity(p);
        return p;
    }

    public Kind kind() {
        Kind[] all = Kind.values();
        return all[Mth.clamp(entityData.get(KIND), 0, all.length - 1)];
    }

    public float scale() {
        return entityData.get(SCALE);
    }

    public int color() {
        return entityData.get(COLOR);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, 0);
        builder.define(SCALE, 1f);
        builder.define(COLOR, 0xFFFFFF);
    }

    @Override
    public void tick() {
        xo = getX();
        yo = getY();
        zo = getZ();
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        age++;
        float k = Math.min(1f, age / (float) flight);
        Vec3 at = from.lerp(to, k).add(0, arc * 4 * k * (1 - k), 0);
        setPos(at.x, at.y, at.z);
        trail(server, at);
        if (age >= flight) {
            impact(server, at);
            discard();
        }
    }

    private void trail(ServerLevel level, Vec3 at) {
        switch (kind()) {
            case BOULDER -> {
                if (age % 2 == 0) level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 1, 0.3, 0.3, 0.3, 0.01);
                level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 1, 0.3, 0.3, 0.3, 0.01);
            }
            case ICE_SPEAR -> level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 2, 0.1, 0.1, 0.1, 0.01);
            case STORM_ORB -> level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 2, 0.2, 0.2, 0.2, 0.05);
            case BLOOD_SHARD -> {
                if (age % 2 == 0) level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y, at.z, 1, 0.1, 0.1, 0.1, 0.01);
            }
            case LIGHT_SPEAR, FEATHER -> {
                if (age % 2 == 0) level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0.08, 0.08, 0.08, 0.005);
            }
            case HURLED_STONE -> {
                if (age % 3 == 0) level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.4, at.z, 1, 0.4, 0.4, 0.4, 0.01);
            }
            case ACID_GLOB -> {
                level.sendParticles(ParticleTypes.DRIPPING_HONEY, at.x, at.y, at.z, 1, 0.15, 0.15, 0.15, 0.01);
                if (age % 2 == 0) level.sendParticles(ParticleTypes.ITEM_SLIME, at.x, at.y, at.z, 1, 0.15, 0.15, 0.15, 0.02);
            }
        }
    }

    private void impact(ServerLevel level, Vec3 at) {
        switch (kind()) {
            case BOULDER -> {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BASALT.defaultBlockState()), at.x, at.y + 0.5, at.z, 40,
                        0.8, 0.4, 0.8, 0.25);
                level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.5, at.z, 8, 0.6, 0.2, 0.6, 0.1);
            }
            case ICE_SPEAR -> level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), at.x,
                    at.y + 0.3, at.z, 24, 0.4, 0.3, 0.4, 0.2);
            case STORM_ORB -> level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.3, at.z, 30, 0.6, 0.4, 0.6, 0.3);
            case BLOOD_SHARD -> level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y + 0.3, at.z, 10, 0.4, 0.3, 0.4, 0.1);
            case LIGHT_SPEAR, FEATHER -> level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.3, at.z, kind() == Kind.FEATHER ? 6 : 16, 0.3, 0.3, 0.3, 0.12);
            case HURLED_STONE -> {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CALCITE.defaultBlockState()), at.x, at.y + 0.5, at.z, 36,
                        0.8, 0.4, 0.8, 0.25);
                level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.5, at.z, 10, 0.6, 0.3, 0.6, 0.1);
            }
            case ACID_GLOB -> {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.HONEY_BLOCK.defaultBlockState()), at.x, at.y + 0.3, at.z, 26,
                        0.6, 0.2, 0.6, 0.2);
                level.sendParticles(ParticleTypes.FALLING_HONEY, at.x, at.y + 0.6, at.z, 10, 0.8, 0.3, 0.8, 0.05);
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.3, at.z, 3, 0.5, 0.1, 0.5, 0.01);
            }
        }
    }

    /** Where it is going (the client turns a spear along it). */
    public Vec3 heading() {
        Vec3 d = new Vec3(getX() - xo, getY() - yo, getZ() - zo);
        return d.lengthSqr() < 1e-6 ? new Vec3(0, -1, 0) : d.normalize();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160 * 160;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    /** Not used: a throw that lands is just the strike under it. */
    BlockPos landing() {
        return BlockPos.containing(to);
    }
}
