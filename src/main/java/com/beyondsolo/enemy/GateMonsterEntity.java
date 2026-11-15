package com.beyondsolo.enemy;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;

/**
 * One of the gate's own creatures (see {@link MonsterKind}). How it fights depends on its kind's style: hounds and
 * swarms bite and leap, chargers barrel into you, casters keep their distance and throw bolts, guards hide behind a
 * shield, bombers fly at you and burst. Its affinity is synced so the client picks the right texture.
 */
public class GateMonsterEntity extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Integer> AFFINITY = SynchedEntityData.defineId(GateMonsterEntity.class, EntityDataSerializers.INT);
    /** Ticks left of a bomber's fuse (0: none). */
    private static final EntityDataAccessor<Integer> FUSE = SynchedEntityData.defineId(GateMonsterEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final MonsterKind kind;
    private final RawAnimation idle;
    private final RawAnimation walk;
    private final RawAnimation death;

    public GateMonsterEntity(EntityType<? extends Monster> type, Level level, MonsterKind kind) {
        super(type, level);
        this.kind = kind;
        String base = "animation.beyondsolo." + kind.id + ".";
        this.idle = RawAnimation.begin().thenLoop(base + "idle");
        this.walk = RawAnimation.begin().thenLoop(base + "walk");
        this.death = RawAnimation.begin().thenPlayAndHold(base + "death");
        this.xpReward = 6 + (int) (kind.health / 4);
        if (kind.flies()) {
            this.moveControl = new FlyingMoveControl(this, 12, true);
            setNoGravity(true);
        }
        addKindGoals();
    }

    public static AttributeSupplier.Builder attributes(MonsterKind kind) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, kind.health)
                .add(Attributes.ATTACK_DAMAGE, kind.damage)
                .add(Attributes.MOVEMENT_SPEED, kind.speed)
                .add(Attributes.FLYING_SPEED, kind.speed * 1.4)
                .add(Attributes.ARMOR, kind.armor)
                .add(Attributes.FOLLOW_RANGE, 28)
                .add(Attributes.KNOCKBACK_RESISTANCE, kind.style == MonsterKind.Style.CHARGER ? 0.6 : kind.style == MonsterKind.Style.GUARD ? 0.4 : 0);
    }

    public MonsterKind kind() {
        return kind;
    }

    public Affinity affinity() {
        return Affinity.byIndex(entityData.get(AFFINITY));
    }

    public void setAffinity(Affinity a) {
        entityData.set(AFFINITY, a.ordinal());
    }

    public int fuse() {
        return entityData.get(FUSE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(AFFINITY, 0);
        builder.define(FUSE, 0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        // the kind isn't set yet when this runs; flying ones get their navigation in addKindGoals
        return super.createNavigation(level);
    }

    @Override
    protected void registerGoals() {
        // the goals depend on the kind, which is set after the super constructor: see addKindGoals
    }

    private void addKindGoals() {
        if (kind.flies()) {
            FlyingPathNavigation nav = new FlyingPathNavigation(this, level());
            nav.setCanOpenDoors(false);
            nav.setCanFloat(true);
            this.navigation = nav;
        }
        goalSelector.addGoal(0, new FloatGoal(this));
        switch (kind.style) {
            case HOUND -> {
                goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.42f));
                goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.15, true));
            }
            case SWARM -> {
                goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.3f));
                goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, true));
            }
            case CHARGER -> {
                goalSelector.addGoal(2, new ChargeGoal());
                goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, true));
            }
            case CASTER -> goalSelector.addGoal(2, new CastGoal());
            case GUARD -> goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, true));
            case BOMBER -> goalSelector.addGoal(2, new DiveGoal());
        }
        if (!kind.flies()) goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ------------------------------------------------------------------ combat

    @Override
    public boolean doHurtTarget(Entity target) {
        triggerAnim("attack", "attack");
        return super.doHurtTarget(target);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // a sentinel's shield takes most of a hit from the front
        if (kind.style == MonsterKind.Style.GUARD && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR) && source.getSourcePosition() != null) {
            Vec3 to = source.getSourcePosition().subtract(position()).multiply(1, 0, 1).normalize();
            Vec3 facing = Vec3.directionFromRotation(0, yBodyRot);
            if (to.dot(facing) > 0.45) {
                amount *= 0.4f;
                triggerAnim("attack", "block");
                level().playSound(null, blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 0.9f, 0.9f + random.nextFloat() * 0.2f);
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return !kind.flies() && super.causeFallDamage(distance, multiplier, source);
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos) {
        if (!kind.flies()) super.checkFallDamage(y, onGround, state, pos);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (kind == MonsterKind.EMBERWISP && random.nextInt(3) == 0) {
                level().addParticle(affinity() == Affinity.NONE || affinity() == Affinity.FLAME ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME,
                        getX() + (random.nextDouble() - 0.5) * 0.4, getY() + 0.7, getZ() + (random.nextDouble() - 0.5) * 0.4, 0, 0.03, 0);
            }
        }
    }

    /** A bomber's burst: hurts everything close, then it is gone. */
    void burst() {
        if (!(level() instanceof ServerLevel server) || !isAlive()) return;
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 1, 0, 0, 0, 0);
        server.sendParticles(affinity() == Affinity.NONE || affinity() == Affinity.FLAME ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME,
                getX(), getY() + 0.3, getZ(), 30, 0.6, 0.6, 0.6, 0.12);
        level().playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.9f, 1.5f);
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(2.8), e -> e != this && e.isAlive() && !(e instanceof Monster))) {
            float k = (float) (1 - e.distanceTo(this) / 3.6);
            if (k <= 0) continue;
            if (e.hurt(damageSources().mobAttack(this), damage * k)) {
                Affinity a = affinity() == Affinity.NONE ? Affinity.FLAME : affinity();
                a.onHit(this, e, 25 * k);
                Vec3 push = e.position().subtract(position()).normalize().scale(0.8 * k);
                e.push(push.x, 0.3 * k, push.z);
                e.hurtMarked = true;
            }
        }
        // its burst is its death: it counts as killed by whoever lit it last
        DamageSource src = getLastDamageSource() != null ? getLastDamageSource() : damageSources().generic();
        setHealth(0);
        die(src);
    }

    // ------------------------------------------------------------------ goals

    /** Chargers: from a few blocks off, a short wind-up and then a headlong rush. */
    private final class ChargeGoal extends Goal {
        private int cooldown = 60;
        private int windup;
        private int rush;
        private Vec3 dir = Vec3.ZERO;
        private boolean hit;

        ChargeGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            LivingEntity t = getTarget();
            if (t == null || !t.isAlive()) return false;
            double d = distanceTo(t);
            return d > 3.5 && d < 12 && hasLineOfSight(t);
        }

        @Override
        public boolean canContinueToUse() {
            return windup > 0 || rush > 0;
        }

        @Override
        public void start() {
            windup = 14;
            rush = 0;
            hit = false;
            getNavigation().stop();
            triggerAnim("attack", "attack");
            level().playSound(null, blockPosition(), SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 1f, 1.4f);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (windup > 0) {
                if (t != null) getLookControl().setLookAt(t, 30, 30);
                if (--windup == 0) {
                    Vec3 to = t != null ? t.position().subtract(position()) : Vec3.directionFromRotation(0, getYRot());
                    dir = to.multiply(1, 0, 1).normalize();
                    rush = 14;
                    if (level() instanceof ServerLevel s) EnemyFx.telegraphLine(s, position(), dir, 9, 1.4f, 6, affinity().color);
                }
                return;
            }
            if (rush > 0) {
                rush--;
                setDeltaMovement(dir.x * 0.75, getDeltaMovement().y, dir.z * 0.75);
                setYRot((float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90f);
                yBodyRot = getYRot();
                if (!hit && level() instanceof ServerLevel s) {
                    for (LivingEntity e : s.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.4), e -> e != GateMonsterEntity.this && !(e instanceof Monster))) {
                        hit = true;
                        if (e.hurt(damageSources().mobAttack(GateMonsterEntity.this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.4f)) {
                            e.push(dir.x * 1.1, 0.35, dir.z * 1.1);
                            e.hurtMarked = true;
                            EnemyHooks.affinityHit(GateMonsterEntity.this, e, 1.3f);
                        }
                    }
                }
                if (rush == 0) cooldown = 100 + random.nextInt(60);
            }
        }
    }

    /** Casters: keep 6 to 12 blocks away and throw a bolt of their element now and then. */
    private final class CastGoal extends Goal {
        private int cooldown = 30;
        private int windup = -1;

        CastGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (t == null) return;
            getLookControl().setLookAt(t, 30, 30);
            double d = distanceTo(t);
            boolean sees = hasLineOfSight(t);
            if (windup >= 0) {
                getNavigation().stop();
                if (--windup == 0 && level() instanceof ServerLevel s) {
                    Vec3 from = position().add(0, getBbHeight() * 0.65, 0);
                    Vec3 aim = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(from).normalize();
                    ElementOrbEntity.shoot(s, GateMonsterEntity.this, from, aim, 0.65f, (float) getAttributeValue(Attributes.ATTACK_DAMAGE),
                            affinity() == Affinity.NONE ? Affinity.SHADE : affinity(), 0.35f, t);
                    level().playSound(null, blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 0.8f, 1.4f);
                    windup = -1;
                    cooldown = 45 + random.nextInt(30);
                }
                return;
            }
            if (!sees || d > 12) getNavigation().moveTo(t, 1.0);
            else if (d < 6) {
                Vec3 away = position().subtract(t.position()).multiply(1, 0, 1).normalize().scale(4);
                getNavigation().moveTo(getX() + away.x, getY(), getZ() + away.z, 1.1);
            } else getNavigation().stop();
            if (cooldown > 0) cooldown--;
            if (cooldown <= 0 && sees && d < 16) {
                windup = 11;
                triggerAnim("attack", "attack");
            }
        }
    }

    /** Bombers: fly at the target, light the fuse when close, burst. */
    private final class DiveGoal extends Goal {
        DiveGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            int fuse = fuse();
            if (fuse > 0) {
                entityData.set(FUSE, fuse - 1);
                setDeltaMovement(getDeltaMovement().scale(0.6));
                if (fuse - 1 <= 0) burst();
                return;
            }
            if (t == null) return;
            getLookControl().setLookAt(t, 30, 30);
            Vec3 goal = t.position().add(0, t.getBbHeight() * 0.6, 0);
            Vec3 to = goal.subtract(position());
            double d = to.length();
            if (d < 1.9) {
                entityData.set(FUSE, 22);
                triggerAnim("attack", "attack");
                level().playSound(null, blockPosition(), SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 1f, 1.6f);
                return;
            }
            double speed = getAttributeValue(Attributes.FLYING_SPEED) * 0.9;
            Vec3 v = getDeltaMovement().scale(0.85).add(to.normalize().scale(speed * 0.35));
            setDeltaMovement(v);
            setYRot((float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f);
            yBodyRot = getYRot();
        }
    }

    // ------------------------------------------------------------------ death

    @Override
    protected void tickDeath() {
        deathTime++;
        if (deathTime >= kind.deathTicks && !level().isClientSide && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    // ------------------------------------------------------------------ sounds

    @Override
    protected SoundEvent getAmbientSound() {
        return switch (kind) {
            case GLOOMFANG -> SoundEvents.WOLF_GROWL;
            case BROODLING -> SoundEvents.SPIDER_AMBIENT;
            case SHARDBACK -> SoundEvents.AMETHYST_BLOCK_CHIME;
            case HOLLOW_ACOLYTE -> SoundEvents.EVOKER_AMBIENT;
            case ASHEN_SENTINEL -> SoundEvents.ARMOR_EQUIP_IRON.value();
            case EMBERWISP -> SoundEvents.BLAZE_BURN;
        };
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return switch (kind) {
            case GLOOMFANG -> SoundEvents.WOLF_HURT;
            case BROODLING -> SoundEvents.SPIDER_HURT;
            case SHARDBACK -> SoundEvents.AMETHYST_BLOCK_HIT;
            case HOLLOW_ACOLYTE -> SoundEvents.EVOKER_HURT;
            case ASHEN_SENTINEL -> SoundEvents.IRON_GOLEM_HURT;
            case EMBERWISP -> SoundEvents.BLAZE_HURT;
        };
    }

    @Override
    protected SoundEvent getDeathSound() {
        return switch (kind) {
            case GLOOMFANG -> SoundEvents.WOLF_DEATH;
            case BROODLING -> SoundEvents.SPIDER_DEATH;
            case SHARDBACK -> SoundEvents.AMETHYST_BLOCK_BREAK;
            case HOLLOW_ACOLYTE -> SoundEvents.EVOKER_DEATH;
            case ASHEN_SENTINEL -> SoundEvents.IRON_GOLEM_DEATH;
            case EMBERWISP -> SoundEvents.BLAZE_DEATH;
        };
    }

    @Override
    protected float getSoundVolume() {
        return kind == MonsterKind.BROODLING ? 0.6f : 1f;
    }

    @Override
    public float getVoicePitch() {
        return kind == MonsterKind.BROODLING ? 1.6f : super.getVoicePitch();
    }

    // ------------------------------------------------------------------ save

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Affinity", entityData.get(AFFINITY));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(AFFINITY, tag.getInt("Affinity"));
    }

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        String base = "animation.beyondsolo." + kind.id + ".";
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            if (isDeadOrDying()) return state.setAndContinue(death);
            if (state.isMoving() || (kind.flies() && getDeltaMovement().horizontalDistanceSqr() > 0.002)) return state.setAndContinue(walk);
            return state.setAndContinue(idle);
        }));
        AnimationController<GateMonsterEntity> attack = new AnimationController<>(this, "attack", 2, state -> PlayState.STOP);
        attack.triggerableAnim("attack", RawAnimation.begin().thenPlay(base + kind.attackAnim));
        if (kind == MonsterKind.ASHEN_SENTINEL) attack.triggerableAnim("block", RawAnimation.begin().thenPlay(base + "block"));
        controllers.add(attack);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
