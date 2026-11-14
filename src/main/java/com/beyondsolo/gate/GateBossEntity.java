package com.beyondsolo.gate;

import com.beyondsolo.combat.Statuses;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.UUID;

/**
 * The boss at the end of a gate's cave: a hulking brute of rock and armour, coloured, named and scaled by the gate's
 * rank, that hits with the gate's element. It waits, dormant, until someone walks into its arena; then it rears up
 * and roars (the boss intro plays for everyone in the cave) and the fight starts. Its attacks are a backhand swipe
 * and a two-fisted slam that hits everything around it; below half health it roars again, calls in help and
 * attacks faster. On death it drops to its knees and slumps (the death animation), and the gate opens its way out.
 */
public class GateBossEntity extends Monster implements GeoEntity {
    public static final int INTRO_TICKS = 70;
    public static final int DEATH_TICKS = 70;

    private static final EntityDataAccessor<Integer> RANK = SynchedEntityData.defineId(GateBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> INTRO = SynchedEntityData.defineId(GateBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> AWAKE = SynchedEntityData.defineId(GateBossEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> ENRAGED = SynchedEntityData.defineId(GateBossEntity.class, EntityDataSerializers.BOOLEAN);

    public static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.beyondsolo.gate_boss.idle");
    public static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.beyondsolo.gate_boss.walk");
    public static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.beyondsolo.gate_boss.death");
    public static final RawAnimation SLAM = RawAnimation.begin().thenPlay("animation.beyondsolo.gate_boss.slam");
    public static final RawAnimation SWIPE = RawAnimation.begin().thenPlay("animation.beyondsolo.gate_boss.swipe");
    public static final RawAnimation ROAR = RawAnimation.begin().thenPlay("animation.beyondsolo.gate_boss.roar");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable private UUID gateId;

    public GateBossEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 50;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 120)
                .add(Attributes.ATTACK_DAMAGE, 7)
                .add(Attributes.ARMOR, 6)
                .add(Attributes.ARMOR_TOUGHNESS, 2)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.STEP_HEIGHT, 1.1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(RANK, 0);
        builder.define(INTRO, 0);
        builder.define(AWAKE, false);
        builder.define(ENRAGED, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new BossFightGoal());
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16f));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    // ------------------------------------------------------------------ setup and state

    /** Makes this the boss of a gate: its rank's stats, name and colour. */
    public void configure(GateInstance gate) {
        this.gateId = gate.id();
        GateTheme theme = gate.theme();
        entityData.set(RANK, theme.rank.ordinal());
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(theme.bossHealth());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(theme.bossDamage());
        getAttribute(Attributes.ARMOR).setBaseValue(4 + 2 * theme.rank.tier());
        getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(1 + theme.rank.tier());
        setHealth(getMaxHealth());
        this.xpReward = 40 + 40 * theme.rank.tier();
        setCustomName(theme.bossName());
        setCustomNameVisible(false);
    }

    public PlayerRank rank() {
        int i = entityData.get(RANK);
        return PlayerRank.values()[Mth.clamp(i, 0, PlayerRank.values().length - 1)];
    }

    public GateTheme theme() {
        return GateTheme.of(rank());
    }

    @Nullable
    public UUID gateId() {
        return gateId;
    }

    public boolean isAwake() {
        return entityData.get(AWAKE);
    }

    public int introTicks() {
        return entityData.get(INTRO);
    }

    public boolean isEnraged() {
        return entityData.get(ENRAGED);
    }

    /** Wakes the boss: it roars, and fights once the intro is over. */
    public void wake() {
        if (isAwake()) return;
        entityData.set(AWAKE, true);
        entityData.set(INTRO, INTRO_TICKS);
        triggerAnim("attack", "roar");
        level().playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.5f, 0.6f);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (!isAwake() || introTicks() > 0) return !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
        return super.isInvulnerableTo(source);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public void aiStep() {
        if (!isAwake() || introTicks() > 0) {
            // dormant, or in its intro: stands its ground
            setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
            getNavigation().stop();
            if (!level().isClientSide && introTicks() > 0) {
                entityData.set(INTRO, introTicks() - 1);
                if (introTicks() == INTRO_TICKS - 22) shockwave(5f, 0f, false);
            }
        }
        super.aiStep();
        if (!level().isClientSide && isAwake() && !isEnraged() && getHealth() < getMaxHealth() * 0.5f && isAlive()) enrage();
    }

    private void enrage() {
        entityData.set(ENRAGED, true);
        triggerAnim("attack", "roar");
        level().playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.5f, 0.45f);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
        if (level() instanceof ServerLevel server) GateService.bossEnraged(server, this);
    }

    // ------------------------------------------------------------------ attacks

    private void hitAround(float radius, float damageScale, boolean coneOnly) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 look = Vec3.directionFromRotation(0, getYRot());
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * damageScale;
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius, 1.5, radius), e -> e != this && e.isAlive())) {
            if (e instanceof GateBossEntity || (e instanceof Monster && !(e instanceof GateBossEntity))) continue;
            Vec3 to = e.position().subtract(position());
            if (to.horizontalDistance() > radius + e.getBbWidth() / 2) continue;
            if (coneOnly && to.normalize().dot(look) < 0.2) continue;
            if (e.hurt(damageSources().mobAttack(this), damage)) {
                Statuses.apply(e, theme().element, theme().mobBuildup() * 2.2f, this);
                Vec3 push = to.multiply(1, 0, 1).normalize().scale(coneOnly ? 0.9 : 0.6);
                e.push(push.x, coneOnly ? 0.25 : 0.55, push.z);
                e.hurtMarked = true;
            }
        }
    }

    /** The ground cracks around it: dust, a ring of block shards, the sound and (for hits) a camera shake. */
    private void shockwave(float radius, float damageScale, boolean hit) {
        if (!(level() instanceof ServerLevel server)) return;
        BlockState ground = level().getBlockState(blockPosition().below());
        for (int i = 0; i < 36; i++) {
            double a = i * Math.PI * 2 / 36;
            double x = getX() + Math.cos(a) * radius * 0.8, z = getZ() + Math.sin(a) * radius * 0.8;
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, getY() + 0.1, z, 3, 0.2, 0.1, 0.2, 0.15);
        }
        server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 2, 0.6, 0.1, 0.6, 0);
        level().playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2f, 0.6f);
        GateService.shake(server, this, hit ? 0.9f : 0.6f, radius + 14);
        if (hit) hitAround(radius, damageScale, false);
    }

    private final class BossFightGoal extends Goal {
        private int cooldown = 30;
        /** Ticks until the current attack lands, or -1. */
        private int windup = -1;
        private boolean slam;

        BossFightGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return isAwake() && introTicks() <= 0 && getTarget() != null && getTarget().isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse() || windup >= 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (windup >= 0) {
                getNavigation().stop();
                if (--windup == 0) {
                    if (slam) shockwave(4.6f, 1.25f, true);
                    else {
                        hitAround(4.2f, 1f, true);
                        level().playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.4f, 0.5f);
                    }
                    windup = -1;
                    cooldown = (isEnraged() ? 22 : 34) + random.nextInt(14);
                }
                return;
            }
            if (target == null) return;
            getLookControl().setLookAt(target, 30, 30);
            double dist = distanceTo(target);
            if (dist > 3.6) getNavigation().moveTo(target, isEnraged() ? 1.25 : 1.0);
            else getNavigation().stop();
            if (cooldown > 0) cooldown--;
            if (cooldown <= 0 && dist < 4.4) {
                slam = random.nextFloat() < (isEnraged() ? 0.5f : 0.35f);
                triggerAnim("attack", slam ? "slam" : "swipe");
                windup = slam ? 14 : 9;
                level().playSound(null, blockPosition(), slam ? SoundEvents.WARDEN_ATTACK_IMPACT : SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 1.2f, 0.5f);
            }
        }
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        // its own attacks go through the fight goal; vanilla melee is not used
        return false;
    }

    // ------------------------------------------------------------------ death

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server) GateService.bossDefeated(server, this, source);
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (level() instanceof ServerLevel server) {
            if (deathTime == 30 || deathTime == 46) shockwave(3f, 0f, false);
            if (deathTime % 4 == 0) {
                server.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1.5, getZ(), 2, 0.8, 1, 0.8, 0.02);
            }
            if (deathTime >= DEATH_TICKS) {
                server.sendParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 40, 1, 1.2, 1, 0.05);
                remove(RemovalReason.KILLED);
            }
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        // the boss's loot is the reward cache
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isAwake() ? SoundEvents.RAVAGER_AMBIENT : null;
    }

    @Override
    protected float getSoundVolume() {
        return 2f;
    }

    @Override
    public Component getDisplayName() {
        // a gate boss is named after its gate; others (the job change's Arbiter) keep the name they were given
        Component custom = getCustomName();
        return custom != null ? custom : theme().bossName();
    }

    // ------------------------------------------------------------------ save

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Rank", entityData.get(RANK));
        tag.putBoolean("Awake", isAwake());
        tag.putBoolean("Enraged", isEnraged());
        if (gateId != null) tag.putUUID("Gate", gateId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(RANK, tag.getInt("Rank"));
        entityData.set(AWAKE, tag.getBoolean("Awake"));
        entityData.set(ENRAGED, tag.getBoolean("Enraged"));
        if (tag.hasUUID("Gate")) gateId = tag.getUUID("Gate");
    }

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> {
            if (isDeadOrDying()) return state.setAndContinue(DEATH);
            if (state.isMoving() && isAwake()) return state.setAndContinue(WALK);
            return state.setAndContinue(IDLE);
        }));
        controllers.add(new AnimationController<>(this, "attack", 2, state -> software.bernie.geckolib.animation.PlayState.STOP)
                .triggerableAnim("slam", SLAM)
                .triggerableAnim("swipe", SWIPE)
                .triggerableAnim("roar", ROAR));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** Used by the boss bar on the client to find the boss. */
    public AABB arena() {
        return getBoundingBox().inflate(40);
    }
}
