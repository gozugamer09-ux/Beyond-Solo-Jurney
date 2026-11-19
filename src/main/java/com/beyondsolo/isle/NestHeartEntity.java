package com.beyondsolo.isle;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
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
 * The heart of a nest: a swollen sac of resin and flesh beating in the middle of the nest's ring, the swarm's young
 * crawling out of it. It bursts after a number of blows (any blow counts as one, a hunter's, a shadow's or pet's, or an
 * allied hunter's: it is breaking them together that matters, {@link IsleConfig#nestHits}), and then the nest burns.
 * Nothing else hurts it. Never saved with the world: {@link IsleRaids} raises it again, with the blows it had taken,
 * when the nest's ground loads.
 */
public class NestHeartEntity extends Mob implements GeoEntity, Enemy {
    private static final EntityDataAccessor<Integer> BLOWS = SynchedEntityData.defineId(NestHeartEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> NEEDED = SynchedEntityData.defineId(NestHeartEntity.class, EntityDataSerializers.INT);
    /** A sweep or a volley in the same moment is one blow. */
    static final int BLOW_GAP = 4;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable private UUID raid;
    private int nest = -1;
    private long lastBlow = -100;

    public NestHeartEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        setNoAi(true);
        setNoGravity(true);
        setPersistenceRequired();
        xpReward = 0;
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 400)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(BLOWS, 0);
        b.define(NEEDED, 1);
    }

    /** Makes this the heart of nest {@code nest} of {@code raid}, with {@code blows} of {@code needed} taken already. */
    public void configure(UUID raid, int nest, int blows, int needed) {
        this.raid = raid;
        this.nest = nest;
        entityData.set(NEEDED, Math.max(1, needed));
        entityData.set(BLOWS, Mth.clamp(blows, 0, needed));
        setCustomName(Component.translatable("entity.beyondsolo.nest_heart"));
        setCustomNameVisible(false);
    }

    public @Nullable UUID raid() { return raid; }
    public int nest() { return nest; }
    public int blows() { return entityData.get(BLOWS); }
    public int needed() { return entityData.get(NEEDED); }

    /** How much of it is left, 1..0. */
    public float left() {
        return 1f - blows() / (float) Math.max(1, needed());
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) return false;
        // a command's kill: it bursts as if broken
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            burst(null);
            return true;
        }
        Entity by = source.getEntity();
        if (by == null || !IsleRaids.hunterSide(by)) return false;
        long now = level().getGameTime();
        if (now - lastBlow < BLOW_GAP) return false;
        lastBlow = now;
        int blows = Math.min(needed(), blows() + 1);
        entityData.set(BLOWS, blows);
        ServerLevel server = (ServerLevel) level();
        server.broadcastDamageEvent(this, source);
        triggerAnim("act", "struck");
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.HONEY_BLOCK.defaultBlockState()), getX(), getY() + 1.1, getZ(), 10,
                0.4, 0.4, 0.4, 0.15);
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.4f, 0.5f + 0.4f * blows / needed());
        IsleRaids.heartStruck(server, this, by, blows);
        if (blows >= needed()) burst(by);
        return true;
    }

    /** It bursts: the nest it fed burns. */
    void burst(@Nullable Entity by) {
        if (!(level() instanceof ServerLevel server) || isRemoved()) return;
        Vec3 at = position().add(0, 1, 0);
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.HONEYCOMB_BLOCK.defaultBlockState()), at.x, at.y, at.z, 70, 0.8, 0.8, 0.8, 0.3);
        server.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 40, 0.8, 0.8, 0.8, 0.12);
        server.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 3, 0.4, 0.4, 0.4, 0);
        server.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.6, at.z, 30, 0.7, 0.9, 0.7, 0.04);
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_DEATH, SoundSource.HOSTILE, 3f, 0.4f);
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.6f, 0.7f);
        discard();
        if (raid != null && nest >= 0) IsleRaids.nestBurned(server, raid, nest, by);
    }

    /** The nest broods: its young crawl out of it (the animation). */
    void brood() {
        triggerAnim("act", "brood");
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.FALLING_HONEY, getX(), getY() + 1.6, getZ(), 12, 0.6, 0.3, 0.6, 0.02);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.HOSTILE, 1.6f, 0.5f);
        }
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (level() instanceof ServerLevel server && tickCount % 4 == 0) {
            // it weeps resin, faster as it weakens
            if (random.nextFloat() < 0.5f + 0.5f * (1 - left())) {
                server.sendParticles(ParticleTypes.DRIPPING_HONEY, getX() + (random.nextDouble() - 0.5) * 1.2, getY() + 1.4 + random.nextDouble() * 0.6,
                        getZ() + (random.nextDouble() - 0.5) * 1.2, 1, 0, 0, 0, 0);
            }
        }
    }

    @Override
    public void die(DamageSource source) {
        // never dies the plain way: a blow it can't take bursts it
        burst(source.getEntity());
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {}

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && (source.getEntity() == null || !IsleRaids.hunterSide(source.getEntity()));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (raid != null) tag.putUUID("Raid", raid);
        tag.putInt("Nest", nest);
        tag.putInt("Blows", blows());
        tag.putInt("Needed", needed());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        raid = tag.hasUUID("Raid") ? tag.getUUID("Raid") : null;
        nest = tag.contains("Nest") ? tag.getInt("Nest") : -1;
        entityData.set(NEEDED, Math.max(1, tag.getInt("Needed")));
        entityData.set(BLOWS, tag.getInt("Blows"));
    }

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4,
                state -> state.setAndContinue(RawAnimation.begin().thenLoop("animation.beyondsolo.nest_heart.beat"))));
        controllers.add(new AnimationController<>(this, "act", 2, state -> PlayState.STOP)
                .triggerableAnim("struck", RawAnimation.begin().thenPlay("animation.beyondsolo.nest_heart.struck"))
                .triggerableAnim("brood", RawAnimation.begin().thenPlay("animation.beyondsolo.nest_heart.brood")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
