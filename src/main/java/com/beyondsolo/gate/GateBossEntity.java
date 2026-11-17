package com.beyondsolo.gate;

import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.combat.Statuses;
import com.beyondsolo.enemy.EnemyFx;
import com.beyondsolo.enemy.EnemyTraits;
import com.beyondsolo.enemy.Variant;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
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
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * The boss at the end of a gate's cave. Which boss it is comes from its {@link BossKind} (the brute, the
 * broodmother, the alpha, the knight, the magus, the wyrm): its model, size, attacks ({@link BossMoves}), affinity
 * and name. Its rank (from the gate) sets its colour and strength. It waits, dormant, until someone walks into its
 * arena; then it plays its intro (the boss intro plays for everyone in the cave) and the fight starts. Big attacks
 * are warned on the floor first. Below half health it enrages: its intro again, help arrives, it attacks faster.
 * On death it plays its own death animation and the gate opens its way out.
 */
public class GateBossEntity extends Monster implements GeoEntity, DungeonBoss {
    public static final int INTRO_TICKS = 70;
    public static final int DEATH_TICKS = 70;

    private static final EntityDataAccessor<Integer> RANK = SynchedEntityData.defineId(GateBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(GateBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> INTRO = SynchedEntityData.defineId(GateBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> AWAKE = SynchedEntityData.defineId(GateBossEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> ENRAGED = SynchedEntityData.defineId(GateBossEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> ASCENDED = SynchedEntityData.defineId(GateBossEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable private UUID gateId;
    /** Server: the move being made, and how far into it. */
    @Nullable BossMoves.Move move;
    int moveTick;
    final BossMoves.State moveState = new BossMoves.State();
    long lastSummon = -10000;

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
        builder.define(KIND, 0);
        builder.define(INTRO, 0);
        builder.define(AWAKE, false);
        builder.define(ENRAGED, false);
        builder.define(ASCENDED, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (KIND.equals(key)) refreshDimensions();
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        BossKind k = kind();
        return EntityDimensions.scalable(k.width, k.height).withEyeHeight(k.height * 0.85f);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new BossFightGoal());
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16f));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    // ------------------------------------------------------------------ setup and state

    /** Makes this the boss of a gate: its kind, its rank's stats, its name and colour. */
    public void configure(GateInstance gate) {
        configure(gate.rank(), gate.bossKind(), gate.bossAscended());
        this.gateId = gate.id();
        getPersistentData().putString(GateService.ELEMENT_TAG, gate.element().id().toString());
        setCustomName(gate.bossName());
        setCustomNameVisible(false);
    }

    /**
     * Makes this a boss of a kind and rank without a gate (dungeon breaks, events): call before adding it to the
     * world. Its defeat then opens nothing; listen for its death instead.
     */
    public void configure(PlayerRank rank, BossKind kind, boolean ascended) {
        GateTheme theme = GateTheme.of(rank);
        entityData.set(RANK, rank.ordinal());
        entityData.set(KIND, kind.ordinal());
        entityData.set(ASCENDED, ascended);
        refreshDimensions();
        float hp = theme.bossHealth() * kind.healthScale * (ascended ? 1.4f : 1f);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(theme.bossDamage() * kind.damageScale * (ascended ? 1.15f : 1f));
        getAttribute(Attributes.ARMOR).setBaseValue(4 + 2 * rank.tier());
        getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(1 + rank.tier());
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(kind == BossKind.ALPHA ? 0.3 : kind == BossKind.MAGUS ? 0.26 : 0.24);
        var scale = getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.removeModifier(com.beyondsolo.BeyondSolo.id("ascended"));
            if (ascended) scale.addPermanentModifier(new AttributeModifier(com.beyondsolo.BeyondSolo.id("ascended"), 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            refreshDimensions();
        }
        // its size: most bosses come at their usual size, a few small, and now and then a giant
        com.beyondsolo.enemy.Enemies.applySize(this, com.beyondsolo.enemy.Enemies.rollSize(getRandom(), true));
        setHealth(getMaxHealth());
        this.xpReward = (40 + 40 * rank.tier()) * (ascended ? 2 : 1);
        if (kind == BossKind.MAGUS) setNoGravity(true);
        setCustomName(kind.name(rank));
        setCustomNameVisible(false);
        // its affinity: its elemental strength and weakness (see enemy.EnemyHooks)
        new EnemyTraits(kind.affinity, Variant.NORMAL, 0).save(this);
    }

    /** Client-side stand-in only (a shadow wearing this boss's shape): its rank's look, awake, no sound. */
    public void displayAs(PlayerRank rank) {
        displayAs(rank, kind());
    }

    public void displayAs(PlayerRank rank, BossKind kind) {
        entityData.set(RANK, rank.ordinal());
        entityData.set(KIND, kind.ordinal());
        entityData.set(AWAKE, true);
    }

    public PlayerRank rank() {
        int i = entityData.get(RANK);
        return PlayerRank.values()[Mth.clamp(i, 0, PlayerRank.values().length - 1)];
    }

    public BossKind kind() {
        return BossKind.byIndex(entityData.get(KIND));
    }

    public boolean isAscended() {
        return entityData.get(ASCENDED);
    }

    public GateTheme theme() {
        return GateTheme.of(rank());
    }

    /** The element its hits carry: its own for the broodmother (poison) and wyrm (fire), else its gate's realm's. */
    public DamageElement element() {
        return switch (kind()) {
            case BROODMOTHER -> DamageElement.POISON;
            case WYRM -> DamageElement.FIRE;
            default -> getPersistentData().contains(GateService.ELEMENT_TAG) ? GateService.elementOf(this) : theme().element;
        };
    }

    /** The colour of its warnings on the floor and its light. */
    public int fxColor() {
        if (isEnraged()) return 0xFF3040;
        return kind().affinity == com.beyondsolo.enemy.Affinity.NONE ? rank().color() : kind().affinity.color;
    }

    @Override
    public int barColor() {
        return isAscended() ? 0xFFC74A : rank().color();
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

    /** Wakes the boss: it plays its intro, and fights once the intro is over. */
    public void wake() {
        if (isAwake()) return;
        entityData.set(AWAKE, true);
        entityData.set(INTRO, INTRO_TICKS);
        triggerAnim("attack", "intro");
        level().playSound(null, blockPosition(), introSound(), SoundSource.HOSTILE, 2.5f, introPitch());
    }

    private SoundEvent introSound() {
        return switch (kind()) {
            case BRUTE -> SoundEvents.RAVAGER_ROAR;
            case BROODMOTHER -> SoundEvents.SPIDER_AMBIENT;
            case ALPHA -> SoundEvents.WOLF_HOWL;
            case KNIGHT -> SoundEvents.WITHER_SKELETON_AMBIENT;
            case MAGUS -> SoundEvents.EVOKER_PREPARE_SUMMON;
            case WYRM -> SoundEvents.ENDER_DRAGON_GROWL;
        };
    }

    private float introPitch() {
        return switch (kind()) {
            case BROODMOTHER -> 0.4f;
            case ALPHA -> 0.6f;
            case KNIGHT -> 0.5f;
            case MAGUS -> 0.7f;
            case WYRM -> 0.9f;
            default -> 0.6f;
        };
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
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
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
        if (kind() == BossKind.MAGUS && !level().isClientSide) hover();
        super.aiStep();
        if (!level().isClientSide && isAwake() && !isEnraged() && getHealth() < getMaxHealth() * 0.5f && isAlive()) enrage();
    }

    /** The magus floats a little over the floor. */
    private void hover() {
        double ground = getY();
        for (int i = 0; i < 6; i++) {
            if (!level().getBlockState(blockPosition().below(i + 1)).isAir()) {
                ground = blockPosition().getY() - i;
                break;
            }
        }
        double want = ground + 0.6 + 0.15 * Math.sin(tickCount * 0.08);
        Vec3 v = getDeltaMovement();
        setDeltaMovement(v.x, (want - getY()) * 0.15, v.z);
    }

    private void enrage() {
        entityData.set(ENRAGED, true);
        if (move == null) triggerAnim("attack", "intro");
        level().playSound(null, blockPosition(), introSound(), SoundSource.HOSTILE, 2.5f, introPitch() * 0.8f);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue() * 1.25);
        if (level() instanceof ServerLevel server) {
            EnemyFx.burst(server, position().add(0, getBbHeight() * 0.5, 0), 0xFF3040, getBbWidth() + 2);
            GateService.bossEnraged(server, this);
        }
    }

    // ------------------------------------------------------------------ attacks (used by BossMoves)

    float damage() {
        return (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    float buildup() {
        return theme().mobBuildup() * 2.2f;
    }

    /** Can this boss's attacks hurt {@code e}? Not other monsters, not itself. */
    boolean canHit(LivingEntity e) {
        return e != this && e.isAlive() && !(e instanceof Monster);
    }

    /** Hurts {@code e} with this boss's attack and element. Returns true if it landed. */
    boolean strike(LivingEntity e, float scale, Vec3 push, double lift) {
        if (!canHit(e)) return false;
        if (!e.hurt(damageSources().mobAttack(this), damage() * scale)) return false;
        Statuses.apply(e, element(), buildup() * Math.min(1.5f, scale + 0.3f), this);
        if (push.lengthSqr() > 0 || lift > 0) {
            e.push(push.x, lift, push.z);
            e.hurtMarked = true;
        }
        return true;
    }

    /** Everything within {@code radius} (in the front cone of {@code dotMin} if above -1). */
    List<LivingEntity> around(float radius, double dotMin) {
        Vec3 look = Vec3.directionFromRotation(0, getYRot());
        return level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius, 2, radius), e -> {
            if (!canHit(e)) return false;
            Vec3 to = e.position().subtract(position()).multiply(1, 0, 1);
            if (to.horizontalDistance() > radius + e.getBbWidth() / 2) return false;
            return dotMin <= -1 || to.normalize().dot(look) >= dotMin;
        });
    }

    void hitAround(float radius, float damageScale, boolean coneOnly) {
        for (LivingEntity e : around(radius + getBbWidth() / 2, coneOnly ? 0.2 : -2)) {
            Vec3 push = e.position().subtract(position()).multiply(1, 0, 1).normalize().scale(coneOnly ? 0.9 : 0.6);
            strike(e, damageScale, push, coneOnly ? 0.25 : 0.55);
        }
    }

    /** The ground cracks around it: dust, a ring of block shards, the sound and (for hits) a camera shake. */
    void shockwave(float radius, float damageScale, boolean hit) {
        shockwaveAt(position(), radius, damageScale, hit);
    }

    void shockwaveAt(Vec3 at, float radius, float damageScale, boolean hit) {
        if (!(level() instanceof ServerLevel server)) return;
        BlockState ground = level().getBlockState(net.minecraft.core.BlockPos.containing(at).below());
        if (ground.isAir()) ground = level().getBlockState(blockPosition().below());
        for (int i = 0; i < 36; i++) {
            double a = i * Math.PI * 2 / 36;
            double x = at.x + Math.cos(a) * radius * 0.8, z = at.z + Math.sin(a) * radius * 0.8;
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, at.y + 0.1, z, 3, 0.2, 0.1, 0.2, 0.15);
        }
        server.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 2, 0.6, 0.1, 0.6, 0);
        level().playSound(null, net.minecraft.core.BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2f, 0.6f);
        GateService.shake(server, this, hit ? 0.9f : 0.6f, radius + 14);
        if (!hit) return;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius, 2, radius), this::canHit)) {
            Vec3 to = e.position().subtract(at).multiply(1, 0, 1);
            if (to.horizontalDistance() > radius + e.getBbWidth() / 2) continue;
            strike(e, damageScale, to.normalize().scale(0.6), 0.55);
        }
    }

    private final class BossFightGoal extends Goal {
        private int cooldown = 30;

        BossFightGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return isAwake() && introTicks() <= 0 && getTarget() != null && getTarget().isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return (canUse() || move != null) && isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            move = null;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (move != null) {
                if (!move.mobile()) getNavigation().stop();
                move.action().tick(GateBossEntity.this, target, moveTick, moveState);
                if (move != null && ++moveTick >= move.length()) {
                    move = null;
                    cooldown = (isEnraged() ? 16 : 26) + random.nextInt(14);
                }
                return;
            }
            if (target == null) return;
            getLookControl().setLookAt(target, 30, 30);
            double dist = distanceTo(target) - getBbWidth() / 2;
            BossMoves.approach(GateBossEntity.this, target, dist);
            if (cooldown > 0) {
                cooldown--;
                return;
            }
            BossMoves.Move next = BossMoves.choose(GateBossEntity.this, target, dist);
            if (next != null) {
                move = next;
                moveTick = 0;
                moveState.reset(target);
                triggerAnim("attack", next.anim());
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
        move = null;
        if (level() instanceof ServerLevel server) GateService.bossDefeated(server, this, source);
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (level() instanceof ServerLevel server) {
            if (deathTime == 30 || deathTime == 46) shockwave(3f, 0f, false);
            if (deathTime % 4 == 0) {
                server.sendParticles(ParticleTypes.SOUL, getX(), getY() + getBbHeight() * 0.5, getZ(), 2, getBbWidth() * 0.4, 1, getBbWidth() * 0.4, 0.02);
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
        return switch (kind()) {
            case BRUTE -> SoundEvents.RAVAGER_HURT;
            case BROODMOTHER -> SoundEvents.SPIDER_HURT;
            case ALPHA -> SoundEvents.WOLF_HURT;
            case KNIGHT -> SoundEvents.IRON_GOLEM_HURT;
            case MAGUS -> SoundEvents.EVOKER_HURT;
            case WYRM -> SoundEvents.ENDER_DRAGON_HURT;
        };
    }

    @Override
    protected SoundEvent getDeathSound() {
        return switch (kind()) {
            case BRUTE -> SoundEvents.RAVAGER_DEATH;
            case BROODMOTHER -> SoundEvents.SPIDER_DEATH;
            case ALPHA -> SoundEvents.WOLF_DEATH;
            case KNIGHT -> SoundEvents.WITHER_SKELETON_DEATH;
            case MAGUS -> SoundEvents.EVOKER_DEATH;
            case WYRM -> SoundEvents.BLAZE_DEATH;
        };
    }

    @Override
    protected SoundEvent getAmbientSound() {
        if (!isAwake()) return null;
        return switch (kind()) {
            case BRUTE -> SoundEvents.RAVAGER_AMBIENT;
            case BROODMOTHER -> SoundEvents.SPIDER_AMBIENT;
            case ALPHA -> SoundEvents.WOLF_GROWL;
            case KNIGHT -> SoundEvents.WITHER_SKELETON_AMBIENT;
            case MAGUS -> SoundEvents.EVOKER_AMBIENT;
            case WYRM -> SoundEvents.BLAZE_AMBIENT;
        };
    }

    @Override
    public float getVoicePitch() {
        return introPitch() + (random.nextFloat() - 0.5f) * 0.1f;
    }

    @Override
    protected float getSoundVolume() {
        return 2f;
    }

    @Override
    public Component getDisplayName() {
        // a gate boss is named after its gate; others (the job change's Arbiter) keep the name they were given
        Component custom = getCustomName();
        return custom != null ? custom : kind().name(rank());
    }

    // ------------------------------------------------------------------ save

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Rank", entityData.get(RANK));
        tag.putString("Kind", kind().id);
        tag.putBoolean("Ascended", isAscended());
        tag.putBoolean("Awake", isAwake());
        tag.putBoolean("Enraged", isEnraged());
        if (gateId != null) tag.putUUID("Gate", gateId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(RANK, tag.getInt("Rank"));
        BossKind k = tag.contains("Kind") ? BossKind.byId(tag.getString("Kind")) : null;
        entityData.set(KIND, (k == null ? BossKind.BRUTE : k).ordinal());
        entityData.set(ASCENDED, tag.getBoolean("Ascended"));
        entityData.set(AWAKE, tag.getBoolean("Awake"));
        entityData.set(ENRAGED, tag.getBoolean("Enraged"));
        if (tag.hasUUID("Gate")) gateId = tag.getUUID("Gate");
        if (kind() == BossKind.MAGUS) setNoGravity(true);
        refreshDimensions();
    }

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> {
            String base = "animation.beyondsolo." + kind().model + ".";
            if (isDeadOrDying()) return state.setAndContinue(RawAnimation.begin().thenPlayAndHold(base + "death"));
            boolean moving = state.isMoving() || getDeltaMovement().horizontalDistanceSqr() > 0.0015;
            if (moving && isAwake()) return state.setAndContinue(RawAnimation.begin().thenLoop(base + "walk"));
            return state.setAndContinue(RawAnimation.begin().thenLoop(base + "idle"));
        }));
        AnimationController<GateBossEntity> attack = new AnimationController<>(this, "attack", 2, state -> PlayState.STOP);
        // every kind's moves are registered; each kind's model only has its own
        for (BossKind k : BossKind.values()) {
            String base = "animation.beyondsolo." + k.model + ".";
            attack.triggerableAnim(k.id + ":intro", RawAnimation.begin().thenPlay(base + k.introAnim));
            for (String anim : BossMoves.anims(k)) attack.triggerableAnim(k.id + ":" + anim, RawAnimation.begin().thenPlay(base + anim));
        }
        controllers.add(attack);
    }

    /** Plays one of this boss's own animations (by its short name: "intro", "slam", "breath"...). */
    @Override
    public void triggerAnim(@Nullable String controllerName, String animName) {
        GeoEntity.super.triggerAnim(controllerName, animName.contains(":") ? animName : kind().id + ":" + animName);
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
