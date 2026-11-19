package com.beyondsolo.light;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * The Envoy of Dawn: the light's messenger, a tall being of white and gold with three pairs of wings, a halo of light
 * and its face hidden behind a mask of gold. It never fights and nothing can hurt it (it isn't a foe; it is a voice).
 * <ul>
 *   <li>In the chapel hidden off an awakening temple's antechamber ({@link Chapel}) it waits by the altar for a hunter
 *   without the System; spoken to, it offers the covenant ({@link Covenant}).</li>
 *   <li>At a vigil ({@link Vigil}) it comes down over the hunter keeping it, prays while they hold the dark back, and at
 *   the end lays the mantle they take up on them.</li>
 * </ul>
 * Animations ({@code animation.beyondsolo.light_envoy.}): "main" plays idle and leave; "act" plays "talk" (it speaks,
 * one hand open) and "bless" (its wings and arms open wide).
 */
public class EnvoyEntity extends PathfinderMob implements GeoEntity {
    /** In its chapel, or over a vigil. */
    public static final int CHAPEL = 0, VIGIL = 1;
    /** Ticks it takes to go back up into the light. */
    public static final int LEAVE_TICKS = 40;

    private static final String BASE = "animation.beyondsolo.light_envoy.";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(BASE + "idle");
    private static final RawAnimation LEAVE = RawAnimation.begin().thenPlayAndHold(BASE + "leave");

    private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(EnvoyEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> LEAVING = SynchedEntityData.defineId(EnvoyEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** The trial whose chapel it waits in. */
    @Nullable private UUID gate;
    /** The hunter whose vigil it watches over. */
    @Nullable private UUID ward;

    public EnvoyEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        // its wings reach far past its body: never skipped as out of sight while they show
        this.noCulling = true;
        setInvulnerable(true);
        setNoGravity(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 24);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MODE, (byte) CHAPEL);
        builder.define(LEAVING, -1);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 16, 1f));
    }

    // ================================================================ what it is here for

    public int mode() { return entityData.get(MODE); }

    public void setMode(int mode) { entityData.set(MODE, (byte) mode); }

    public @Nullable UUID gate() { return gate; }

    public void setGate(@Nullable UUID gate) { this.gate = gate; }

    public @Nullable UUID ward() { return ward; }

    public void setWard(@Nullable UUID ward) { this.ward = ward; }

    /** Whether it is going back up into the light. */
    public boolean leaving() { return entityData.get(LEAVING) >= 0; }

    /** Ticks since it began to leave (client and server). */
    public int leftFor() {
        int at = entityData.get(LEAVING);
        return at < 0 ? 0 : tickCount - at;
    }

    /** It goes back up into the light (gone after {@link #LEAVE_TICKS}). */
    public void leave() {
        if (leaving() || level().isClientSide) return;
        entityData.set(LEAVING, tickCount);
        playSound(SoundEvents.BEACON_DEACTIVATE, 1.2f, 1.4f);
        playSound(SoundEvents.ENDER_DRAGON_FLAP, 1f, 1.5f);
    }

    /** It speaks: one hand open, a chime. */
    public void speak() {
        triggerAnim("act", "talk");
        playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 1.2f);
    }

    /** It blesses: wings and arms open wide. */
    public void bless() {
        triggerAnim("act", "bless");
        playSound(SoundEvents.BEACON_POWER_SELECT, 1.4f, 1.3f);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || leaving()) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) {
            if (mode() == CHAPEL) Covenant.speak(sp, this);
            else Vigil.spokenTo(sp, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    // ================================================================ its time

    @Override
    public void aiStep() {
        super.aiStep();
        setDeltaMovement(Vec3.ZERO);
        if (!(level() instanceof ServerLevel server)) return;
        if (leaving()) {
            if (leftFor() % 4 == 0) {
                server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + getBbHeight() * (0.4 + leftFor() / (double) LEAVE_TICKS), getZ(),
                        6, 0.5, 0.6, 0.5, 0.04);
            }
            if (leftFor() >= LEAVE_TICKS) {
                server.sendParticles(ParticleTypes.FLASH, getX(), getY() + getBbHeight() + 1, getZ(), 1, 0, 0, 0, 0);
                discard();
            }
            return;
        }
        if (tickCount % 6 == 0) {
            server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + getBbHeight() * 0.6, getZ(), 1, 0.8, 0.6, 0.8, 0.01);
        }
        if (tickCount % 40 == 0) {
            if (mode() == CHAPEL && !Chapel.stillStands(server, this)) leave();
            if (mode() == VIGIL && !Vigil.watching(server, this)) leave();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // it isn't a foe: only what removes anything at all (the void, a command) touches it
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {}

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return SoundEvents.AMETHYST_BLOCK_CHIME;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    @Override
    protected float getSoundVolume() {
        return 0.7f;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Mode", (byte) mode());
        if (gate != null) tag.putUUID("Gate", gate);
        if (ward != null) tag.putUUID("Ward", ward);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setMode(tag.getByte("Mode"));
        gate = tag.hasUUID("Gate") ? tag.getUUID("Gate") : null;
        ward = tag.hasUUID("Ward") ? tag.getUUID("Ward") : null;
        setInvulnerable(true);
        setNoGravity(true);
    }

    // ================================================================ its animations

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 6, state -> state.setAndContinue(leaving() ? LEAVE : IDLE)));
        AnimationController<EnvoyEntity> act = new AnimationController<>(this, "act", 4, state -> PlayState.STOP);
        act.triggerableAnim("talk", RawAnimation.begin().thenPlay(BASE + "talk"));
        act.triggerableAnim("bless", RawAnimation.begin().thenPlay(BASE + "bless"));
        controllers.add(act);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
