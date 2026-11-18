package com.beyondsolo.awakening;

import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.combat.Statuses;
import com.beyondsolo.gate.DungeonBoss;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.network.AwakeningPayload;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.BlockPos;
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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * The Hollow Arbiter: the giant at the end of the awakening trial. A hooded, faceless colossus of cracked white
 * stone mended with gold, kneeling in chains behind its planted greatsword, a broken halo turning behind its head.
 * It wakes when its challenger walks into the hall, tears free and stands.
 *
 * <p>It is meant to win. It has a mountain of health and armour and every blow can kill someone the System has not
 * touched yet, but everything it does is announced, so it can be beaten by someone who never gets hit:</p>
 * <ul>
 *   <li><b>Cleave</b>: a slow sweep of the sword through everything in front of it.</li>
 *   <li><b>Sunder</b>: the sword comes down and a ring runs out along the floor; jump it.</li>
 *   <li><b>Judgment</b>: it raises its open hand, circles of light mark the floor under its challengers, and
 *   pillars of light fall on them a moment later.</li>
 * </ul>
 * Under half health its halo burns red: it is faster, sunders twice and judges three times over.
 */
public class ArbiterEntity extends Monster implements GeoEntity, DungeonBoss {
    public static final int INTRO_TICKS = 120;
    public static final int DEATH_TICKS = 110;
    public static final float MARK_RADIUS = 2.8f;
    public static final int MARK_TICKS = 32;
    public static final float WAVE_REACH = 17f;
    public static final float WAVE_SPEED = 0.8f;

    private static final EntityDataAccessor<Integer> INTRO = SynchedEntityData.defineId(ArbiterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> AWAKE = SynchedEntityData.defineId(ArbiterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> UNBOUND = SynchedEntityData.defineId(ArbiterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> VERDICT = SynchedEntityData.defineId(ArbiterEntity.class, EntityDataSerializers.BOOLEAN);

    private static final String A = "animation.beyondsolo.arbiter.";
    public static final RawAnimation DORMANT = RawAnimation.begin().thenLoop(A + "dormant");
    public static final RawAnimation RISE = RawAnimation.begin().thenPlay(A + "rise").thenLoop(A + "idle");
    public static final RawAnimation IDLE = RawAnimation.begin().thenLoop(A + "idle");
    public static final RawAnimation WALK = RawAnimation.begin().thenLoop(A + "walk");
    public static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold(A + "death");
    public static final RawAnimation VERDICT_ANIM = RawAnimation.begin().thenPlayAndHold(A + "verdict");
    public static final RawAnimation CLEAVE = RawAnimation.begin().thenPlay(A + "cleave");
    public static final RawAnimation SUNDER = RawAnimation.begin().thenPlay(A + "sunder");
    public static final RawAnimation JUDGMENT = RawAnimation.begin().thenPlay(A + "judgment");

    private record Wave(Vec3 origin, long started) {}
    private record Mark(Vec3 at, long strikesAt) {}

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final List<Wave> waves = new ArrayList<>();
    private final List<Mark> marks = new ArrayList<>();
    @Nullable private UUID gateId;

    public ArbiterEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1500)
                .add(Attributes.ATTACK_DAMAGE, 26)
                .add(Attributes.ARMOR, 20)
                .add(Attributes.ARMOR_TOUGHNESS, 12)
                .add(Attributes.MOVEMENT_SPEED, 0.21)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 80)
                .add(Attributes.STEP_HEIGHT, 2.1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(INTRO, 0);
        builder.define(AWAKE, false);
        builder.define(UNBOUND, false);
        builder.define(VERDICT, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FightGoal());
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // a challenger already falling into their awakening is past judging: it turns to the next (a party's trial)
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false,
                e -> !(e instanceof Player p && AwakeningService.inSequence(p))));
    }

    // ------------------------------------------------------------------ state

    public void setGate(UUID gate) {
        this.gateId = gate;
    }

    @Nullable
    public UUID gateId() {
        return gateId;
    }

    @Override
    public boolean isAwake() {
        return entityData.get(AWAKE);
    }

    @Override
    public int introTicks() {
        return entityData.get(INTRO);
    }

    /** Second phase: under half health. */
    @Override
    public boolean isEnraged() {
        return entityData.get(UNBOUND);
    }

    /** Standing over a fallen challenger: it has stopped fighting. */
    public boolean isVerdict() {
        return entityData.get(VERDICT);
    }

    @Override
    public PlayerRank rank() {
        return PlayerRank.S;
    }

    @Override
    public int barColor() {
        return isEnraged() ? 0xFF4050 : 0xF2D58A;
    }

    @Override
    public String healthLabel() {
        return "? ? ?";
    }

    /** 0 kneeling .. 1 standing (for the renderer's halo and lights). */
    public float standing(float partialTick) {
        if (!isAwake()) return 0f;
        float t = INTRO_TICKS - introTicks() + (introTicks() > 0 ? partialTick : 0);
        return Mth.clamp((t - 40f) / 50f, 0f, 1f);
    }

    public void wake() {
        if (isAwake()) return;
        entityData.set(AWAKE, true);
        entityData.set(INTRO, INTRO_TICKS);
        level().playSound(null, blockPosition(), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 3f, 0.5f);
        level().playSound(null, blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 3f, 0.5f);
    }

    /**
     * Its challenger has fallen: it raises its sword over them and holds. This can happen in the middle of one of its
     * own attacks (the blow that fells them), so the attacks check {@link #fighting()} after every strike.
     */
    public void passVerdict() {
        entityData.set(VERDICT, true);
        getNavigation().stop();
        setTarget(null);
        waves.clear();
        marks.clear();
        level().playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 3f, 0.5f);
    }

    /** Still fighting: alive and no verdict passed (a strike can end the fight halfway through an attack). */
    private boolean fighting() {
        return isAlive() && !isVerdict();
    }

    // for AwakeningTestAccess: tests drive the attacks by hand

    /** Done rising: awake and fighting at once. */
    void finishRising() {
        entityData.set(AWAKE, true);
        entityData.set(INTRO, 0);
    }

    void addMark(Vec3 at, long strikesAt) {
        marks.add(new Mark(at, strikesAt));
    }

    int pendingAttacks() {
        return waves.size() + marks.size();
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (!isAwake() || introTicks() > 0 || isVerdict()) return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        return super.isInvulnerableTo(source);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // arrows and thrown things barely scratch stone: it has to be fought
        if (source.is(DamageTypeTags.IS_PROJECTILE)) amount *= 0.4f;
        return super.hurt(source, amount);
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
    public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect) {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void aiStep() {
        if (!level().isClientSide && getTarget() instanceof Player p && AwakeningService.inSequence(p)) setTarget(null);
        if (!isAwake() || introTicks() > 0 || isVerdict()) {
            setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
            getNavigation().stop();
            if (!level().isClientSide && introTicks() > 0) {
                int t = INTRO_TICKS - introTicks();
                entityData.set(INTRO, introTicks() - 1);
                if (level() instanceof ServerLevel server) {
                    if (t == 18 || t == 26) {
                        level().playSound(null, blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 3f, 0.4f);
                        GateService.shake(server, this, 0.5f, 60);
                    }
                    if (t == 62) dust(6f, 60, 0.7f);
                    if (t == 96) {
                        // the sword comes free of the floor
                        level().playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 2f, 0.4f);
                        level().playSound(null, blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 3f, 0.5f);
                        dust(8f, 80, 1f);
                    }
                }
            }
        }
        super.aiStep();
        if (level() instanceof ServerLevel server && isAlive()) {
            if (isAwake() && !isEnraged() && getHealth() < getMaxHealth() * 0.5f) unbind(server);
            tickWaves(server);
            tickMarks(server);
        }
    }

    private void unbind(ServerLevel server) {
        entityData.set(UNBOUND, true);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.27);
        level().playSound(null, blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 3f, 0.4f);
        dust(9f, 90, 1f);
    }

    private void dust(float radius, int count, float shake) {
        if (!(level() instanceof ServerLevel server)) return;
        BlockState ground = level().getBlockState(blockPosition().below());
        if (ground.isAir()) ground = net.minecraft.world.level.block.Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count;
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), getX() + Math.cos(a) * radius, getY() + 0.1, getZ() + Math.sin(a) * radius, 3, 0.3, 0.1, 0.3, 0.2);
        }
        server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 0.3, getZ(), 30, radius * 0.5, 0.2, radius * 0.5, 0.02);
        GateService.shake(server, this, shake, radius + 50);
    }

    // ------------------------------------------------------------------ attacks

    private float damage(float scale) {
        return (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * scale;
    }

    private boolean strike(LivingEntity e, float scale, Vec3 from, double push, double lift) {
        if (!e.hurt(damageSources().mobAttack(this), damage(scale))) return false;
        Statuses.apply(e, DamageElement.CRIMSON, 30f, this);
        Vec3 dir = e.position().subtract(from).multiply(1, 0, 1);
        dir = dir.lengthSqr() < 1.0E-4 ? Vec3.ZERO : dir.normalize().scale(push);
        e.push(dir.x, lift, dir.z);
        e.hurtMarked = true;
        return true;
    }

    private List<LivingEntity> victims(Vec3 around, double radius) {
        return level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(around, around).inflate(radius, 4, radius),
                e -> e != this && e.isAlive() && !(e instanceof Monster));
    }

    /** The sweep: everything in a wide arc in front of it. */
    private void cleave() {
        Vec3 look = Vec3.directionFromRotation(0, yBodyRot);
        for (LivingEntity e : victims(position(), 11)) {
            if (!fighting()) break;
            Vec3 to = e.position().subtract(position());
            if (to.horizontalDistance() > 10.5 || e.getY() > getY() + 4.5) continue;
            if (to.horizontalDistance() > 2.5 && to.multiply(1, 0, 1).normalize().dot(look) < 0.05) continue;
            strike(e, 1f, position(), 1.6, 0.45);
        }
        level().playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 3f, 0.4f);
        if (level() instanceof ServerLevel server) GateService.shake(server, this, 0.5f, 50);
    }

    /** The sword comes down in front of it and a ring runs out along the floor. */
    private void sunder() {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 look = Vec3.directionFromRotation(0, yBodyRot);
        Vec3 impact = position().add(look.scale(6.5));
        for (LivingEntity e : victims(impact, 4.2)) {
            if (!fighting()) break;
            if (e.position().subtract(impact).horizontalDistance() <= 4.2) strike(e, 1.25f, impact, 1.2, 0.7);
        }
        BlockState ground = level().getBlockState(BlockPos.containing(impact).below());
        if (ground.isAir()) ground = net.minecraft.world.level.block.Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), impact.x, impact.y + 0.2, impact.z, 120, 2.2, 0.3, 2.2, 0.4);
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, impact.x, impact.y + 0.5, impact.z, 1, 0, 0, 0, 0);
        level().playSound(null, BlockPos.containing(impact), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3f, 0.5f);
        level().playSound(null, BlockPos.containing(impact), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 3f, 0.5f);
        GateService.shake(server, this, 1f, 60);
        // the blow itself may have felled its challenger: then no ring follows
        if (!fighting()) return;
        startWave(server, impact, 0);
        if (isEnraged()) startWave(server, impact, 12);
    }

    void startWave(ServerLevel server, Vec3 origin, int delay) {
        waves.add(new Wave(origin, server.getGameTime() + delay));
        com.beyondsolo.util.ServerScheduler.schedule(delay, () -> {
            if (!isAlive()) return;
            for (ServerPlayer p : server.players()) {
                if (p.distanceToSqr(this) < 96 * 96) PacketDistributor.sendToPlayer(p, new AwakeningPayload(AwakeningPayload.WAVE, WAVE_REACH, origin.x, origin.y, origin.z));
            }
        });
    }

    /**
     * The ring catches whoever is standing on the floor as it passes: jump it.
     *
     * <p>A strike can end the fight then and there: the challenger falls, the trial refuses their death and the
     * Arbiter passes its verdict, which clears every ring. So the rings are walked as a copy, and the walk stops as
     * soon as the fight is over (walking the list itself crashed the server in the 0.1.0 playtest).</p>
     */
    void tickWaves(ServerLevel server) {
        long now = server.getGameTime();
        waves.removeIf(w -> (now - w.started) * WAVE_SPEED > WAVE_REACH);
        for (Wave w : List.copyOf(waves)) {
            float age = now - w.started;
            if (age < 0) continue;
            float r = age * WAVE_SPEED;
            for (LivingEntity e : victims(w.origin, r + 2)) {
                if (!fighting()) return;
                double d = e.position().subtract(w.origin).horizontalDistance();
                if (d < r - 1.1 || d > r + 0.4) continue;
                if (e.getY() > w.origin.y + 0.7 || !e.onGround() && e.getY() > w.origin.y + 0.35) continue;
                strike(e, 0.65f, w.origin, 0.5, 0.75);
            }
        }
    }

    /** Marks the floor under every challenger (and, unbound, twice more around them). */
    private void judge() {
        if (!(level() instanceof ServerLevel server)) return;
        List<Vec3> spots = new ArrayList<>();
        for (ServerPlayer p : server.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(70, 30, 70), p -> p.isAlive() && !p.isSpectator() && !p.isCreative())) {
            // lead them a little: where they are going, not where they stand
            Vec3 v = p.getKnownMovement().multiply(1, 0, 1).scale(10);
            spots.add(floor(server, p.position().add(v)));
            if (isEnraged()) {
                for (int i = 0; i < 2; i++) {
                    float a = random.nextFloat() * Mth.TWO_PI;
                    spots.add(floor(server, p.position().add(Mth.cos(a) * 5.5, 0, Mth.sin(a) * 5.5)));
                }
            }
        }
        long at = server.getGameTime() + MARK_TICKS;
        for (Vec3 s : spots) {
            marks.add(new Mark(s, at));
            for (ServerPlayer p : server.players()) {
                if (p.distanceToSqr(this) < 96 * 96) PacketDistributor.sendToPlayer(p, new AwakeningPayload(AwakeningPayload.MARK, MARK_TICKS, s.x, s.y, s.z));
            }
        }
        level().playSound(null, blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 3f, 0.5f);
    }

    private static Vec3 floor(ServerLevel level, Vec3 at) {
        BlockPos p = BlockPos.containing(at);
        for (int i = 0; i < 8 && level.getBlockState(p.below()).isAir(); i++) p = p.below();
        for (int i = 0; i < 4 && !level.getBlockState(p).isAir(); i++) p = p.above();
        return new Vec3(at.x, p.getY(), at.z);
    }

    /** The pillars fall on the marks that are due. As with the rings, a strike can end the fight: then the rest go. */
    void tickMarks(ServerLevel server) {
        long now = server.getGameTime();
        List<Mark> due = new ArrayList<>();
        marks.removeIf(m -> {
            if (now < m.strikesAt) return false;
            due.add(m);
            return true;
        });
        for (Mark m : due) {
            if (!fighting()) return;
            for (LivingEntity e : victims(m.at, MARK_RADIUS + 1)) {
                if (!fighting()) return;
                if (e.position().subtract(m.at).horizontalDistance() <= MARK_RADIUS && Math.abs(e.getY() - m.at.y) < 12) strike(e, 1.15f, m.at, 0.4, 0.5);
            }
            server.sendParticles(ParticleTypes.END_ROD, m.at.x, m.at.y + 0.3, m.at.z, 40, 1.2, 0.2, 1.2, 0.15);
            server.playSound(null, BlockPos.containing(m.at), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.HOSTILE, 2f, 1.4f);
            for (ServerPlayer p : server.getEntitiesOfClass(ServerPlayer.class, new net.minecraft.world.phys.AABB(m.at, m.at).inflate(14))) {
                PacketDistributor.sendToPlayer(p, new com.beyondsolo.network.GatePayload(com.beyondsolo.network.GatePayload.SHAKE, 0, -1, 0.35f, Component.empty(), Component.empty()));
            }
        }
    }

    private final class FightGoal extends Goal {
        private int cooldown = 30;
        private int windup = -1;
        private int attack;
        private int farTicks;

        FightGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return isAwake() && introTicks() <= 0 && !isVerdict() && getTarget() != null && getTarget().isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return !isVerdict() && (canUse() || windup >= 0);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            windup = -1;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (windup >= 0) {
                getNavigation().stop();
                if (--windup == 0) {
                    switch (attack) {
                        case 0 -> cleave();
                        case 1 -> sunder();
                        default -> judge();
                    }
                    windup = -1;
                    cooldown = (isEnraged() ? 26 : 44) + random.nextInt(16);
                }
                return;
            }
            if (target == null) return;
            getLookControl().setLookAt(target, 12, 30);
            double dist = distanceTo(target);
            if (dist > 7.5) getNavigation().moveTo(target, 1.0);
            else getNavigation().stop();
            farTicks = dist > 13 ? farTicks + 1 : 0;
            if (cooldown > 0) cooldown--;
            if (cooldown > 0) return;
            float roll = random.nextFloat();
            if (dist > 13) {
                // out of reach: judge them, sooner the longer they stay away
                if (farTicks < 30 && roll < 0.5f) return;
                attack = 2;
            } else if (dist > 9.5) {
                attack = roll < 0.6f ? 1 : 2;
            } else {
                attack = roll < 0.45f ? 0 : roll < 0.8f ? 1 : 2;
            }
            // face the target squarely before committing
            Vec3 to = target.position().subtract(position());
            float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
            triggerAnim("attack", attack == 0 ? "cleave" : attack == 1 ? "sunder" : "judgment");
            windup = attack == 0 ? 24 : attack == 1 ? 30 : 22;
            level().playSound(null, blockPosition(), attack == 2 ? SoundEvents.BEACON_ACTIVATE : SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 3f, 0.4f);
        }
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        return false;
    }

    // ------------------------------------------------------------------ death

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server) slain(server, source);
    }

    /** It has fallen: the awakening trial's ending. The throne's Arbiter ({@code ThroneArbiterEntity}) has its own. */
    protected void slain(ServerLevel server, DamageSource source) {
        AwakeningService.arbiterSlain(server, this, source);
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (level() instanceof ServerLevel server) {
            if (deathTime == 1) level().playSound(null, blockPosition(), SoundEvents.WARDEN_DEATH, SoundSource.HOSTILE, 3f, 0.5f);
            if (deathTime == 34 || deathTime == 62 || deathTime == 84) dust(5f + deathTime / 12f, 60, 0.8f);
            if (deathTime % 3 == 0) {
                server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 4, getZ(), 4, 1.6, 3, 1.6, 0.05);
                server.sendParticles(ParticleTypes.WHITE_ASH, getX(), getY() + 4, getZ(), 12, 2, 3, 2, 0.02);
            }
            if (deathTime >= DEATH_TICKS) {
                server.sendParticles(ParticleTypes.POOF, getX(), getY() + 3, getZ(), 120, 2, 3, 2, 0.08);
                server.sendParticles(ParticleTypes.FLASH, getX(), getY() + 4, getZ(), 1, 0, 0, 0, 0);
                remove(RemovalReason.KILLED);
            }
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.DEEPSLATE_BREAK;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WARDEN_DEATH;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isAwake() ? SoundEvents.WARDEN_HEARTBEAT : null;
    }

    @Override
    protected float getSoundVolume() {
        return 3f;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("entity.beyondsolo.arbiter");
    }

    // ------------------------------------------------------------------ save

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Awake", isAwake());
        tag.putBoolean("Unbound", isEnraged());
        if (gateId != null) tag.putUUID("Gate", gateId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(AWAKE, tag.getBoolean("Awake"));
        entityData.set(UNBOUND, tag.getBoolean("Unbound"));
        if (tag.hasUUID("Gate")) gateId = tag.getUUID("Gate");
    }

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 6, state -> {
            if (isDeadOrDying()) return state.setAndContinue(DEATH);
            if (!isAwake()) return state.setAndContinue(DORMANT);
            if (isVerdict()) return state.setAndContinue(VERDICT_ANIM);
            if (introTicks() > 0) return state.setAndContinue(RISE);
            if (state.isMoving()) return state.setAndContinue(WALK);
            return state.setAndContinue(IDLE);
        }));
        controllers.add(new AnimationController<>(this, "attack", 3, state -> PlayState.STOP)
                .triggerableAnim("cleave", CLEAVE)
                .triggerableAnim("sunder", SUNDER)
                .triggerableAnim("judgment", JUDGMENT));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
