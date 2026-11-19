package com.beyondsolo.story;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A vision a story scene shows the hunter ({@link MainStory}): someone who is not really there. The first is Morvath,
 * the King of Night who sat on the empty throne before them, a tall figure of black armour and shadow. It does
 * nothing and nothing touches it; the scene that called it sends it away, and if the scene is lost it fades on its
 * own. Never saved with the world.
 */
public class VisionEntity extends PathfinderMob {
    /** Who it is. */
    public static final int KING = 0;
    /** How long it lasts at most (ticks), whatever happens to its scene. */
    static final int LIFE = 20 * 90;

    private static final EntityDataAccessor<Integer> WHO = SynchedEntityData.defineId(VisionEntity.class, EntityDataSerializers.INT);
    /** It is breaking apart into shadow (the client thins it out). */
    private static final EntityDataAccessor<Integer> FADING = SynchedEntityData.defineId(VisionEntity.class, EntityDataSerializers.INT);

    public VisionEntity(EntityType<? extends VisionEntity> type, Level level) {
        super(type, level);
        setInvulnerable(true);
        setNoAi(true);
        setNoGravity(true);
        setSilent(true);
        noPhysics = true;
        xpReward = 0;
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(WHO, KING);
        b.define(FADING, 0);
    }

    public int who() { return entityData.get(WHO); }
    /** Ticks it has been breaking apart (0: whole). */
    public int fading() { return entityData.get(FADING); }

    void setWho(int who) {
        entityData.set(WHO, who);
    }

    /** It starts to break apart into shadow; it is gone {@code ticks} later. */
    void fade(int ticks) {
        if (fading() == 0) entityData.set(FADING, 1);
        fadeEnd = tickCount + ticks;
    }

    private int fadeEnd = -1;

    /** Turns it to look at {@code at}. */
    public void face(Vec3 at) {
        Vec3 d = at.subtract(getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
        yRotO = yaw;
        yHeadRotO = yaw;
        yBodyRotO = yaw;
        setXRot(pitch);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (fading() > 0) entityData.set(FADING, fading() + 1);
        if (tickCount > LIFE || fadeEnd >= 0 && tickCount >= fadeEnd) {
            discard();
            return;
        }
        if (level() instanceof ServerLevel server && tickCount % 3 == 0) {
            // shadow coming off it like smoke, more of it as it breaks apart
            int n = fading() > 0 ? 6 : 1;
            server.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.2 + random.nextDouble() * 2.0, getZ(), n, 0.3, 0.4, 0.3, 0.01);
            if (fading() > 0) server.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1.2, getZ(), 3, 0.35, 0.6, 0.35, 0.02);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {}

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return null;
    }
}
