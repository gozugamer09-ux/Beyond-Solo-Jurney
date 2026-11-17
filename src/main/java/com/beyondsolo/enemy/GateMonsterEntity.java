package com.beyondsolo.enemy;

import com.beyondsolo.util.ParticleGuard;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.monster.Monster;
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

/**
 * One of the gate's own creatures (see {@link MonsterKind}). How it fights depends on its kind's style
 * ({@link MonsterGoals} has the moves): hounds and swarms bite and leap, chargers barrel into you, casters keep their
 * distance and throw bolts, guards hide behind a shield, bombers fly at you and burst, brutes slam the ground, archers
 * shoot, skirmishers dash in and back out, swoopers dive from above, lungers strike from out of reach, haunts drift
 * and send out pulses, shamblers get back up once, and turrets never move. Its affinity is synced so the client picks
 * the right texture.
 *
 * <p>Animations: "main" plays idle, walk, death (and a shambler's fall and rise); "attack" plays the triggered ones,
 * "attack" (its strike) and "special" (its second move: a slam, a dash, a block, a wail ...).</p>
 */
public class GateMonsterEntity extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Integer> AFFINITY = SynchedEntityData.defineId(GateMonsterEntity.class, EntityDataSerializers.INT);
    /** Ticks left of a bomber's fuse (0: none). */
    private static final EntityDataAccessor<Integer> FUSE = SynchedEntityData.defineId(GateMonsterEntity.class, EntityDataSerializers.INT);
    /** Ticks left of a shambler lying on the ground before it gets back up (0: standing). */
    private static final EntityDataAccessor<Integer> DOWNED = SynchedEntityData.defineId(GateMonsterEntity.class, EntityDataSerializers.INT);
    /** How long a shambler stays down: its "revive" animation, the fall and the rise. */
    public static final int DOWN_TICKS = 60;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final MonsterKind kind;
    private final RawAnimation idle;
    private final RawAnimation walk;
    private final RawAnimation death;
    @Nullable private final RawAnimation rise;
    /** A shambler that has already got back up once stays down the next time. */
    private boolean risen;
    /** A skirmisher's ticks of backing off after a hit. */
    private int retreat;

    public GateMonsterEntity(EntityType<? extends Monster> type, Level level, MonsterKind kind) {
        super(type, level);
        this.kind = kind;
        String base = "animation.beyondsolo." + kind.id + ".";
        this.idle = RawAnimation.begin().thenLoop(base + "idle");
        this.walk = RawAnimation.begin().thenLoop(base + "walk");
        this.death = RawAnimation.begin().thenPlayAndHold(base + "death");
        this.rise = kind.style == MonsterKind.Style.SHAMBLER && kind.specialAnim != null ? RawAnimation.begin().thenPlay(base + kind.specialAnim) : null;
        this.xpReward = 6 + (int) (kind.health / 4);
        if (kind.flies()) {
            this.moveControl = new FlyingMoveControl(this, 12, true);
            setNoGravity(true);
        }
        addKindGoals();
    }

    public static AttributeSupplier.Builder attributes(MonsterKind kind) {
        double steady = switch (kind.style) {
            case CHARGER -> 0.6;
            case GUARD -> 0.4;
            case BRUTE -> kind.health >= 100 ? 0.9 : 0.5;
            case TURRET -> 1.0;
            default -> 0;
        };
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, kind.health)
                .add(Attributes.ATTACK_DAMAGE, kind.damage)
                .add(Attributes.MOVEMENT_SPEED, kind.speed)
                .add(Attributes.FLYING_SPEED, kind.speed * 1.4)
                .add(Attributes.ARMOR, kind.armor)
                .add(Attributes.FOLLOW_RANGE, 28)
                .add(Attributes.STEP_HEIGHT, kind.height >= 2.4f ? 1.1 : 0.6)
                .add(Attributes.KNOCKBACK_RESISTANCE, steady);
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

    void setFuse(int ticks) {
        entityData.set(FUSE, ticks);
    }

    /** Ticks left before a downed shambler gets up again (0: it is on its feet). */
    public int downed() {
        return entityData.get(DOWNED);
    }

    /** Whether a shambler has already got back up once. */
    public boolean hasRisen() {
        return risen;
    }

    int retreat() {
        return retreat;
    }

    void startRetreat(int ticks) {
        retreat = Math.max(0, ticks);
    }

    /** The element of a caster's bolts: its affinity, or its kind's own when it has none. */
    Affinity boltElement() {
        if (affinity() != Affinity.NONE) return affinity();
        return switch (kind) {
            case BRIMLING -> Affinity.FLAME;
            case TUSK_SHAMAN -> Affinity.STORM;
            default -> Affinity.SHADE;
        };
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(AFFINITY, 0);
        builder.define(FUSE, 0);
        builder.define(DOWNED, 0);
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
                goalSelector.addGoal(2, new MonsterGoals.Charge(this));
                goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, true));
            }
            case CASTER -> {
                if (kind.has(MonsterKind.Quirk.MENDS)) goalSelector.addGoal(1, new MonsterGoals.Mend(this));
                if (kind.has(MonsterKind.Quirk.BLINKS)) goalSelector.addGoal(1, new MonsterGoals.Blink(this));
                goalSelector.addGoal(2, new MonsterGoals.Cast(this));
            }
            case GUARD -> goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, true));
            case BOMBER -> goalSelector.addGoal(2, new MonsterGoals.Dive(this));
            case BRUTE -> {
                goalSelector.addGoal(2, new MonsterGoals.Slam(this, switch (kind) {
                    case RIMECLAW -> 17;
                    case RUNE_COLOSSUS -> 20;
                    default -> 16;
                }));
                goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, true));
            }
            case ARCHER -> goalSelector.addGoal(2, new MonsterGoals.Shoot(this));
            case SKIRMISHER -> {
                goalSelector.addGoal(1, new MonsterGoals.Retreat(this));
                goalSelector.addGoal(2, new MonsterGoals.Dash(this));
                goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2, true));
            }
            case SWOOPER -> goalSelector.addGoal(2, new MonsterGoals.Swoop(this));
            case LUNGER -> {
                goalSelector.addGoal(2, switch (kind) {
                    case HIVE_REAVER -> new MonsterGoals.Lunge(this, 0, 6, true);
                    case COILFANG -> new MonsterGoals.Lunge(this, 4.0f, 6, false);
                    default -> new MonsterGoals.Lunge(this, 4.5f, 8, false);
                });
                goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, true));
            }
            case HAUNT -> {
                goalSelector.addGoal(2, new MonsterGoals.Pulse(this, 14, kind == MonsterKind.WAILING_WRAITH ? 5f : 4f));
                goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, true));
            }
            case SHAMBLER -> goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, true));
            case TURRET -> goalSelector.addGoal(2, new MonsterGoals.Spit(this));
        }
        if (kind.flies()) {
            if (kind.style != MonsterKind.Style.BOMBER) goalSelector.addGoal(6, new WaterAvoidingRandomFlyingGoal(this, 0.8));
        } else if (!kind.rooted()) {
            goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        }
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10f));
        if (!kind.rooted()) goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ------------------------------------------------------------------ combat

    @Override
    public boolean doHurtTarget(Entity target) {
        triggerAnim("attack", "attack");
        return strike(target);
    }

    /** A melee hit without starting the strike animation (the move that calls it has already started one). */
    boolean strike(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity t) {
            onStrike(t);
            if (kind.style == MonsterKind.Style.SKIRMISHER) startRetreat(14);
        }
        return hit;
    }

    /** What a kind's strikes leave besides the hit: the drone's and the serpent's venom, the drifter's shock. */
    void onStrike(LivingEntity t) {
        switch (kind) {
            case HIVE_DRONE -> t.addEffect(new MobEffectInstance(MobEffects.POISON, 50, 0), this);
            case COILFANG -> t.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0), this);
            case STATIC_DRIFTER -> {
                if (affinity() == Affinity.NONE) Affinity.STORM.onHit(this, t, 8);
            }
            default -> {
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // the gate's creatures never hurt one another: a stray arrow is not a reason to start a fight
        if (source.getEntity() instanceof GateMonsterEntity other && other != this && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        // a shield (or a crab's claws) takes most of a hit from the front
        if (kind.style == MonsterKind.Style.GUARD && !source.is(DamageTypeTags.BYPASSES_ARMOR) && source.getSourcePosition() != null) {
            Vec3 to = source.getSourcePosition().subtract(position()).multiply(1, 0, 1).normalize();
            Vec3 facing = Vec3.directionFromRotation(0, yBodyRot);
            if (to.dot(facing) > 0.45) {
                amount *= 0.4f;
                triggerAnim("attack", "special");
                level().playSound(null, blockPosition(), kind == MonsterKind.CRAGCRAWLER ? SoundEvents.SHULKER_HURT_CLOSED : SoundEvents.SHIELD_BLOCK,
                        SoundSource.HOSTILE, 0.9f, 0.9f + random.nextFloat() * 0.2f);
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        // a shambler goes down the first time and gets back up, unless it is finished while it lies there
        if (rise != null && !risen && !level().isClientSide && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            risen = true;
            setHealth(1f);
            entityData.set(DOWNED, DOWN_TICKS);
            getNavigation().stop();
            setDeltaMovement(0, getDeltaMovement().y, 0);
            level().playSound(null, blockPosition(), SoundEvents.ZOMBIE_INFECT, SoundSource.HOSTILE, 1f, 0.6f);
            return;
        }
        entityData.set(DOWNED, 0);
        // a flyer drops out of the air
        if (kind.flies()) setNoGravity(false);
        super.die(source);
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || downed() > 0;
    }

    @Override
    public boolean isPushable() {
        return !kind.rooted() && super.isPushable();
    }

    @Override
    public boolean isPushedByFluid() {
        return !kind.rooted() && super.isPushedByFluid();
    }

    @Override
    public void knockback(double strength, double x, double z) {
        if (!kind.rooted()) super.knockback(strength, x, z);
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
            clientFx();
            return;
        }
        int down = downed();
        if (down > 0 && isAlive()) {
            entityData.set(DOWNED, down - 1);
            setDeltaMovement(0, getDeltaMovement().y, 0);
            if (down - 1 == 0) {
                // back on its feet, half mended
                setHealth(Math.max(getHealth(), getMaxHealth() * 0.5f));
                level().playSound(null, blockPosition(), SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, 1.2f, 0.6f);
                if (level() instanceof ServerLevel s) {
                    ParticleGuard.send(s, ParticleTypes.SOUL, getX(), getY() + 1, getZ(), 12, 0.3, 0.6, 0.3, 0.03);
                    EnemyFx.burst(s, position().add(0, 1, 0), affinity() == Affinity.NONE ? 0x96FFE1 : affinity().color, 1.4f);
                }
            }
        }
    }

    private void clientFx() {
        if (isDeadOrDying()) return;
        boolean plain = affinity() == Affinity.NONE;
        switch (kind) {
            case EMBERWISP -> {
                if (random.nextInt(3) == 0) {
                    level().addParticle(plain || affinity() == Affinity.FLAME ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME,
                            getX() + (random.nextDouble() - 0.5) * 0.4, getY() + 0.7, getZ() + (random.nextDouble() - 0.5) * 0.4, 0, 0.03, 0);
                }
            }
            case STATIC_DRIFTER -> {
                if (random.nextInt(5) == 0) {
                    level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX() + (random.nextDouble() - 0.5) * 0.8, getY() + random.nextDouble() * getBbHeight(),
                            getZ() + (random.nextDouble() - 0.5) * 0.8, 0, 0, 0);
                }
            }
            case WAILING_WRAITH -> {
                if (random.nextInt(7) == 0) {
                    level().addParticle(ParticleTypes.SOUL, getX() + (random.nextDouble() - 0.5) * 0.6, getY() + 0.2, getZ() + (random.nextDouble() - 0.5) * 0.6, 0, -0.02, 0);
                }
            }
            case GRAVE_THRALL -> {
                if (downed() > 0 && random.nextInt(2) == 0) {
                    level().addParticle(ParticleTypes.SOUL, getX() + (random.nextDouble() - 0.5) * 1.2, getY() + 0.2, getZ() + (random.nextDouble() - 0.5) * 1.2, 0, 0.04, 0);
                }
            }
            default -> {
            }
        }
    }

    /** A bomber's burst: hurts everything close, then it is gone. */
    void burst() {
        if (!(level() instanceof ServerLevel server) || !isAlive()) return;
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 1, 0, 0, 0, 0);
        ParticleGuard.send(server, affinity() == Affinity.NONE || affinity() == Affinity.FLAME ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME,
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
    @Nullable
    protected SoundEvent getAmbientSound() {
        return switch (kind) {
            case GLOOMFANG -> SoundEvents.WOLF_GROWL;
            case BROODLING -> SoundEvents.SPIDER_AMBIENT;
            case SHARDBACK -> SoundEvents.AMETHYST_BLOCK_CHIME;
            case HOLLOW_ACOLYTE -> SoundEvents.EVOKER_AMBIENT;
            case ASHEN_SENTINEL -> SoundEvents.ARMOR_EQUIP_IRON.value();
            case EMBERWISP -> SoundEvents.BLAZE_BURN;
            case GUTTERKIN, TUSK_SHAMAN -> SoundEvents.PIGLIN_AMBIENT;
            case GRAVE_THRALL -> downed() > 0 ? null : SoundEvents.ZOMBIE_AMBIENT;
            case BONE_ARCHER -> SoundEvents.SKELETON_AMBIENT;
            case RIME_STALKER -> SoundEvents.STRAY_AMBIENT;
            case TUSKBRUTE -> SoundEvents.PIGLIN_BRUTE_AMBIENT;
            case DEEPKIN -> SoundEvents.DROWNED_AMBIENT;
            case DUSKWING -> SoundEvents.PHANTOM_AMBIENT;
            case HIVE_DRONE -> SoundEvents.BEE_LOOP_AGGRESSIVE;
            case BRIMLING -> SoundEvents.VEX_AMBIENT;
            case WAILING_WRAITH -> SoundEvents.GHAST_AMBIENT;
            case STATIC_DRIFTER -> SoundEvents.GUARDIAN_AMBIENT;
            case RIMECLAW -> SoundEvents.POLAR_BEAR_AMBIENT;
            case PIT_RAVAGER -> SoundEvents.RAVAGER_AMBIENT;
            case COILFANG -> SoundEvents.CAT_HISS;
            case CRAGCRAWLER -> SoundEvents.SILVERFISH_AMBIENT;
            case HIVE_REAVER -> SoundEvents.SPIDER_AMBIENT;
            case RUNE_COLOSSUS, THORN_SPITTER -> null;
        };
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return switch (kind) {
            case GLOOMFANG -> SoundEvents.WOLF_HURT;
            case BROODLING, HIVE_REAVER -> SoundEvents.SPIDER_HURT;
            case SHARDBACK -> SoundEvents.AMETHYST_BLOCK_HIT;
            case HOLLOW_ACOLYTE -> SoundEvents.EVOKER_HURT;
            case ASHEN_SENTINEL, RUNE_COLOSSUS -> SoundEvents.IRON_GOLEM_HURT;
            case EMBERWISP -> SoundEvents.BLAZE_HURT;
            case GUTTERKIN, TUSK_SHAMAN -> SoundEvents.PIGLIN_HURT;
            case GRAVE_THRALL -> SoundEvents.ZOMBIE_HURT;
            case BONE_ARCHER -> SoundEvents.SKELETON_HURT;
            case RIME_STALKER -> SoundEvents.STRAY_HURT;
            case TUSKBRUTE -> SoundEvents.PIGLIN_BRUTE_HURT;
            case DEEPKIN -> SoundEvents.DROWNED_HURT;
            case DUSKWING -> SoundEvents.PHANTOM_HURT;
            case HIVE_DRONE -> SoundEvents.BEE_HURT;
            case BRIMLING, WAILING_WRAITH -> SoundEvents.VEX_HURT;
            case STATIC_DRIFTER -> SoundEvents.GUARDIAN_HURT;
            case RIMECLAW -> SoundEvents.POLAR_BEAR_HURT;
            case PIT_RAVAGER -> SoundEvents.RAVAGER_HURT;
            case COILFANG -> SoundEvents.SILVERFISH_HURT;
            case CRAGCRAWLER -> SoundEvents.SHULKER_HURT_CLOSED;
            case THORN_SPITTER -> SoundEvents.GRASS_HIT;
        };
    }

    @Override
    protected SoundEvent getDeathSound() {
        return switch (kind) {
            case GLOOMFANG -> SoundEvents.WOLF_DEATH;
            case BROODLING, HIVE_REAVER -> SoundEvents.SPIDER_DEATH;
            case SHARDBACK -> SoundEvents.AMETHYST_BLOCK_BREAK;
            case HOLLOW_ACOLYTE -> SoundEvents.EVOKER_DEATH;
            case ASHEN_SENTINEL, RUNE_COLOSSUS -> SoundEvents.IRON_GOLEM_DEATH;
            case EMBERWISP -> SoundEvents.BLAZE_DEATH;
            case GUTTERKIN, TUSK_SHAMAN -> SoundEvents.PIGLIN_DEATH;
            case GRAVE_THRALL -> SoundEvents.ZOMBIE_DEATH;
            case BONE_ARCHER -> SoundEvents.SKELETON_DEATH;
            case RIME_STALKER -> SoundEvents.STRAY_DEATH;
            case TUSKBRUTE -> SoundEvents.PIGLIN_BRUTE_DEATH;
            case DEEPKIN -> SoundEvents.DROWNED_DEATH;
            case DUSKWING -> SoundEvents.PHANTOM_DEATH;
            case HIVE_DRONE -> SoundEvents.BEE_DEATH;
            case BRIMLING -> SoundEvents.VEX_DEATH;
            case WAILING_WRAITH -> SoundEvents.GHAST_DEATH;
            case STATIC_DRIFTER -> SoundEvents.GUARDIAN_DEATH;
            case RIMECLAW -> SoundEvents.POLAR_BEAR_DEATH;
            case PIT_RAVAGER -> SoundEvents.RAVAGER_DEATH;
            case COILFANG -> SoundEvents.SILVERFISH_DEATH;
            case CRAGCRAWLER -> SoundEvents.SHULKER_DEATH;
            case THORN_SPITTER -> SoundEvents.GRASS_BREAK;
        };
    }

    @Override
    protected float getSoundVolume() {
        return switch (kind) {
            case BROODLING -> 0.6f;
            case HIVE_DRONE, WAILING_WRAITH -> 0.5f;
            case RUNE_COLOSSUS -> 1.3f;
            default -> 1f;
        };
    }

    @Override
    public float getVoicePitch() {
        float k = switch (kind) {
            case BROODLING -> 1.5f;
            case GUTTERKIN, STATIC_DRIFTER -> 1.4f;
            case DUSKWING -> 1.3f;
            case GRAVE_THRALL, TUSK_SHAMAN, BRIMLING, HIVE_REAVER -> 0.8f;
            case TUSKBRUTE, HIVE_DRONE, WAILING_WRAITH, RIMECLAW -> 0.7f;
            case COILFANG, RUNE_COLOSSUS -> 0.6f;
            case CRAGCRAWLER -> 0.5f;
            default -> 1f;
        };
        return super.getVoicePitch() * k;
    }

    // ------------------------------------------------------------------ save

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Affinity", entityData.get(AFFINITY));
        if (risen) tag.putBoolean("Risen", true);
        if (downed() > 0) tag.putInt("Downed", downed());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(AFFINITY, tag.getInt("Affinity"));
        risen = tag.getBoolean("Risen");
        entityData.set(DOWNED, tag.getInt("Downed"));
    }

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        String base = "animation.beyondsolo." + kind.id + ".";
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            if (isDeadOrDying()) return state.setAndContinue(death);
            if (rise != null && downed() > 0) return state.setAndContinue(rise);
            if (state.isMoving() || (kind.flies() && getDeltaMovement().horizontalDistanceSqr() > 0.002)) return state.setAndContinue(walk);
            return state.setAndContinue(idle);
        }));
        AnimationController<GateMonsterEntity> attack = new AnimationController<>(this, "attack", 2, state -> PlayState.STOP);
        attack.triggerableAnim("attack", RawAnimation.begin().thenPlay(base + kind.attackAnim));
        if (kind.specialAnim != null && rise == null) attack.triggerableAnim("special", RawAnimation.begin().thenPlay(base + kind.specialAnim));
        controllers.add(attack);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** Its death puff, without the puffs that would fill a first-person camera standing over it. */
    @Override
    public void handleEntityEvent(byte id) {
        if (id == 60 && level().isClientSide()) {
            com.beyondsolo.client.render.CameraParticles.poof(this);
            return;
        }
        super.handleEntityEvent(id);
    }
}
