package com.beyondsolo.tower;

import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.combat.Statuses;
import com.beyondsolo.enemy.EnemyFx;
import com.beyondsolo.gate.DungeonBoss;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.util.ParticleGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Vharos, lord of the Crimson Citadel: a horned king in black plate seamed with crimson fire, a ragged mantle at his
 * back and a long crimson halberd in his hands. He waits before his throne at the top of the castle (floor
 * {@link TowerFloors#TOP}) and is the last bout of the Sovereign boss rush.
 *
 * <p>Everything he does is announced on the floor first, so he can be beaten without being hit:</p>
 * <ul>
 *   <li><b>Cleave</b>: a wide sweep of the halberd through everything in front of him.</li>
 *   <li><b>Lance</b>: he draws back and thrusts, and spears of crimson light burst out of the floor along a line
 *   (three lines once he is enraged).</li>
 *   <li><b>Decree</b>: he closes his fist and the floor under his challenger is marked; lightning falls on the mark a
 *   moment later (three marks once he is enraged).</li>
 *   <li><b>Leap</b>: he leaps onto his challenger and comes down halberd first.</li>
 * </ul>
 * Under half health he is enraged: faster, with shorter breaths between blows. Under a third he calls the
 * <b>blood moon</b>: the sky over the Citadel turns red, two champions of his court come out of the light at his side
 * ({@link TowerRun#lordGuards}), and blood lightning keeps falling on marked ground until he dies.
 *
 * <p>He can be hurt from the moment his intro ends: no shields, no healing, no second life.</p>
 */
public class CitadelLordEntity extends Monster implements GeoEntity, DungeonBoss {
    public static final int INTRO_TICKS = 80;
    public static final int DEATH_TICKS = 100;
    public static final float ENRAGE_AT = 0.5f;
    public static final float MOON_AT = 0.3f;
    public static final int RED = 0xFF2E3F;
    public static final int HOT = 0xFF8A70;
    public static final float MARK_RADIUS = 2.6f;
    public static final int MARK_TICKS = 26;
    public static final float LEAP_RADIUS = 4.5f;

    private static final EntityDataAccessor<Boolean> AWAKE = SynchedEntityData.defineId(CitadelLordEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> INTRO = SynchedEntityData.defineId(CitadelLordEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ENRAGED = SynchedEntityData.defineId(CitadelLordEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> MOON = SynchedEntityData.defineId(CitadelLordEntity.class, EntityDataSerializers.BOOLEAN);

    private static final String A = "animation.beyondsolo.citadel_lord.";
    public static final RawAnimation DORMANT = RawAnimation.begin().thenLoop(A + "dormant");
    public static final RawAnimation INTRO_ANIM = RawAnimation.begin().thenPlay(A + "intro").thenLoop(A + "idle");
    public static final RawAnimation IDLE = RawAnimation.begin().thenLoop(A + "idle");
    public static final RawAnimation WALK = RawAnimation.begin().thenLoop(A + "walk");
    public static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold(A + "death");
    public static final RawAnimation CLEAVE = RawAnimation.begin().thenPlay(A + "cleave");
    public static final RawAnimation LANCE = RawAnimation.begin().thenPlay(A + "lance");
    public static final RawAnimation DECREE = RawAnimation.begin().thenPlay(A + "decree");
    public static final RawAnimation LEAP = RawAnimation.begin().thenPlay(A + "leap");
    public static final RawAnimation MOON_ANIM = RawAnimation.begin().thenPlay(A + "moon");

    /** His moves: the animation each plays and how long it lasts (ticks; the animations are made to match). */
    public enum Move {
        CLEAVE("cleave", 30), LANCE("lance", 40), DECREE("decree", 30), LEAP("leap", 44), MOON("moon", 60);

        public final String anim;
        public final int length;

        Move(String anim, int length) {
            this.anim = anim;
            this.length = length;
        }
    }

    /** A blow waiting to land: a decree's lightning, a spear of the lance, a bolt of the blood moon. */
    private record Strike(Vec3 at, float radius, long when, float scale, int kind) {}

    private static final int SPEAR = 0, MARK = 1, BOLT = 2;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final List<Strike> strikes = new ArrayList<>();
    /** Who a lance has already run through (each is hit once per lance, however many spears they stand in). */
    private final Set<UUID> speared = new HashSet<>();
    @Nullable private Move move;
    private int moveTick;
    private float moveYaw;
    private Vec3 aim = Vec3.ZERO;
    private Vec3 leapFrom = Vec3.ZERO;
    private Vec3 leapTo = Vec3.ZERO;
    @Nullable private Move last, beforeLast;
    private boolean moonCalled;
    private int boltIn = 60;

    public CitadelLordEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes()
                // the hundredth floor: well past an S-rank gate's boss (see system.Balance)
                .add(Attributes.MAX_HEALTH, 60000)
                .add(Attributes.ATTACK_DAMAGE, 520)
                .add(Attributes.ARMOR, 18)
                .add(Attributes.ARMOR_TOUGHNESS, 10)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 64)
                .add(Attributes.STEP_HEIGHT, 1.1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(AWAKE, false);
        builder.define(INTRO, 0);
        builder.define(ENRAGED, false);
        builder.define(MOON, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FightGoal());
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    // ------------------------------------------------------------------ state

    @Override
    public boolean isAwake() {
        return entityData.get(AWAKE);
    }

    @Override
    public int introTicks() {
        return entityData.get(INTRO);
    }

    @Override
    public boolean isEnraged() {
        return entityData.get(ENRAGED);
    }

    /** The blood moon is up: his last stand. */
    public boolean bloodMoon() {
        return entityData.get(MOON);
    }

    @Override
    public PlayerRank rank() {
        return PlayerRank.S;
    }

    @Override
    public int barColor() {
        return RED;
    }

    /** The move he is in the middle of (null between moves). */
    @Nullable
    public Move move() {
        return move;
    }

    /** 0 standing dormant .. 1 fully risen (for the renderer's light). */
    public float risen(float partialTick) {
        if (!isAwake()) return 0f;
        float t = INTRO_TICKS - introTicks() + (introTicks() > 0 ? partialTick : 0);
        return Mth.clamp(t / 40f, 0f, 1f);
    }

    /** Wakes him: his intro plays, and he fights once it is over. */
    public void wake() {
        if (isAwake()) return;
        entityData.set(AWAKE, true);
        entityData.set(INTRO, INTRO_TICKS);
        level().playSound(null, blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 3f, 0.5f);
        level().playSound(null, blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 3f, 0.5f);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (!isAwake() || introTicks() > 0) return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        return com.beyondsolo.combat.CombatDamage.spared(this, source, super.isInvulnerableTo(source));
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
    public boolean doHurtTarget(Entity target) {
        // his blows go through his moves; vanilla melee is not used
        return false;
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void aiStep() {
        if (!isAwake() || introTicks() > 0) {
            setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
            getNavigation().stop();
            if (!level().isClientSide && introTicks() > 0) introTick(INTRO_TICKS - introTicks());
        }
        super.aiStep();
        if (level() instanceof ServerLevel server && isAlive()) {
            if (isAwake() && introTicks() <= 0) {
                if (!isEnraged() && getHealth() < getMaxHealth() * ENRAGE_AT) enrage(server);
                if (bloodMoon() && --boltIn <= 0) bolt(server);
            }
            tickStrikes(server);
        }
    }

    private void introTick(int t) {
        entityData.set(INTRO, introTicks() - 1);
        if (!(level() instanceof ServerLevel server)) return;
        if (t == 10) {
            // his eyes and seams light up
            level().playSound(null, blockPosition(), SoundEvents.BLAZE_AMBIENT, SoundSource.HOSTILE, 2f, 0.4f);
            server.sendParticles(dust(RED, 1.4f), getX(), getY() + 2.6, getZ(), 30, 0.6, 0.8, 0.6, 0);
        }
        if (t == 40) {
            // the halberd's butt struck on the floor
            level().playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 2f, 0.5f);
            ring(position(), 5f, 0.7f);
        }
        if (t == 58) {
            level().playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3f, 0.5f);
            level().playSound(null, blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 2f, 0.7f);
            EnemyFx.burst(server, position().add(0, 2.2, 0), RED, 4f);
            GateService.shake(server, this, 0.8f, 60);
        }
    }

    private void enrage(ServerLevel server) {
        entityData.set(ENRAGED, true);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.34);
        level().playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3f, 0.4f);
        EnemyFx.burst(server, position().add(0, getBbHeight() * 0.5, 0), RED, 5f);
        GateService.shake(server, this, 0.7f, 50);
    }

    // ------------------------------------------------------------------ blows

    private float damage(float scale) {
        return (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * scale;
    }

    private boolean canHit(LivingEntity e) {
        return e != this && e.isAlive() && !(e instanceof Monster) && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    private boolean strike(LivingEntity e, float scale, Vec3 from, double push, double lift) {
        if (!canHit(e)) return false;
        if (!e.hurt(damageSources().mobAttack(this), damage(scale))) return false;
        Statuses.apply(e, DamageElement.CRIMSON, 22f * scale, this);
        Vec3 dir = e.position().subtract(from).multiply(1, 0, 1);
        dir = dir.lengthSqr() < 1.0E-4 ? Vec3.ZERO : dir.normalize().scale(push);
        e.push(dir.x, lift, dir.z);
        e.hurtMarked = true;
        return true;
    }

    private List<LivingEntity> near(Vec3 at, double radius, double height) {
        return level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius, height, radius), this::canHit);
    }

    private Vec3 look() {
        return Vec3.directionFromRotation(0, moveYaw);
    }

    /** The sweep: everything in a wide arc in front of him. */
    private void cleave(ServerLevel server) {
        Vec3 look = look();
        float reach = 7.5f + getBbWidth() / 2;
        for (LivingEntity e : near(position(), reach + 1, 3)) {
            Vec3 to = e.position().subtract(position()).multiply(1, 0, 1);
            if (to.horizontalDistance() > reach + e.getBbWidth() / 2) continue;
            if (to.horizontalDistance() > 1.5 && to.normalize().dot(look) < 0.26) continue;
            strike(e, 1f, position(), 1.4, 0.4);
        }
        // the arc of the blade, in light
        for (int i = -6; i <= 6; i++) {
            float a = moveYaw * Mth.DEG_TO_RAD + i * 0.2f;
            Vec3 d = new Vec3(-Mth.sin(a), 0, Mth.cos(a));
            for (float r = 3f; r <= 7f; r += 2f) {
                Vec3 p = position().add(d.scale(r)).add(0, 1.2, 0);
                server.sendParticles(dust(i % 2 == 0 ? RED : HOT, 1.2f), p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0);
            }
        }
        server.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + look.x * 3, getY() + 1.4, getZ() + look.z * 3, 3, 1.2, 0.2, 1.2, 0);
        level().playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 3f, 0.5f);
        level().playSound(null, blockPosition(), SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.HOSTILE, 1.5f, 0.6f);
        GateService.shake(server, this, 0.4f, 40);
        if (isEnraged()) spears(server, position(), look, 3, 4f, 2);
    }

    /** Spears of light burst out of the floor one after another along a line from {@code from}. */
    private void spears(ServerLevel server, Vec3 from, Vec3 dir, int count, float start, int delay) {
        long now = server.getGameTime();
        for (int i = 0; i < count; i++) {
            Vec3 at = floor(server, from.add(dir.scale(start + i * 2.0)));
            strikes.add(new Strike(at, 1.5f, now + delay + i, 0.9f, SPEAR));
        }
    }

    private void lance(ServerLevel server) {
        speared.clear();
        int lines = isEnraged() ? 3 : 1;
        for (int l = 0; l < lines; l++) {
            float yaw = moveYaw + (l == 0 ? 0 : l == 1 ? -24 : 24);
            spears(server, position(), Vec3.directionFromRotation(0, yaw), 8, 2.5f, l * 2);
        }
        level().playSound(null, blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.HOSTILE, 3f, 0.5f);
        level().playSound(null, blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 2f, 0.6f);
    }

    private void lanceTelegraph(ServerLevel server) {
        int lines = isEnraged() ? 3 : 1;
        for (int l = 0; l < lines; l++) {
            float yaw = moveYaw + (l == 0 ? 0 : l == 1 ? -24 : 24);
            Vec3 dir = Vec3.directionFromRotation(0, yaw);
            EnemyFx.telegraphLine(server, position().add(dir.scale(1.5)), dir, 17f, 2.8f, 20 + l * 2, RED);
        }
    }

    /** Marks the floor under his challengers (where they are going, not where they stand): every hunter in the hall, and whatever he is fighting. */
    private void decree(ServerLevel server) {
        long at = server.getGameTime() + MARK_TICKS;
        List<LivingEntity> marked = new ArrayList<>(server.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(48, 20, 48), this::canHit));
        LivingEntity target = getTarget();
        if (target != null && canHit(target) && !marked.contains(target) && distanceToSqr(target) < 48 * 48) marked.add(target);
        for (LivingEntity e : marked) {
            Vec3 lead = e.getKnownMovement().multiply(1, 0, 1).scale(8);
            // where they are going, but no further than a few strides (a knockback is not a plan)
            if (lead.lengthSqr() > 4.0 * 4.0) lead = lead.normalize().scale(4.0);
            mark(server, floor(server, e.position().add(lead)), at, MARK, 1f);
            if (isEnraged()) {
                for (int i = 0; i < 2; i++) {
                    float a = random.nextFloat() * Mth.TWO_PI;
                    mark(server, floor(server, e.position().add(Mth.cos(a) * 4.5, 0, Mth.sin(a) * 4.5)), at + 4 + i * 4, MARK, 1f);
                }
            }
        }
        level().playSound(null, blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.5f, 0.5f);
    }

    private void mark(ServerLevel server, Vec3 at, long when, int kind, float scale) {
        float radius = kind == BOLT ? 2.2f : MARK_RADIUS;
        strikes.add(new Strike(at, radius, when, scale, kind));
        EnemyFx.telegraphCircle(server, at.add(0, 0.05, 0), radius, (int) (when - server.getGameTime()), RED);
    }

    /** Blood lightning on marked ground, near his challenger, all through the blood moon. */
    private void bolt(ServerLevel server) {
        boltIn = 50 + random.nextInt(30);
        LivingEntity target = getTarget();
        if (target == null || !canHit(target)) return;
        float a = random.nextFloat() * Mth.TWO_PI;
        float r = random.nextFloat() * 3f;
        mark(server, floor(server, target.position().add(Mth.cos(a) * r, 0, Mth.sin(a) * r)), server.getGameTime() + 22, BOLT, 0.7f);
    }

    /** The blows that are due land. A strike can end a fight (the challenger falls): the list is walked as a copy. */
    private void tickStrikes(ServerLevel server) {
        if (strikes.isEmpty()) return;
        long now = server.getGameTime();
        List<Strike> due = new ArrayList<>();
        strikes.removeIf(s -> {
            if (now < s.when) return false;
            due.add(s);
            return true;
        });
        for (Strike s : due) {
            for (LivingEntity e : near(s.at, s.radius + 1, s.kind == SPEAR ? 3 : 10)) {
                if (e.position().subtract(s.at).horizontalDistance() > s.radius + e.getBbWidth() / 2) continue;
                if (s.kind == SPEAR && !speared.add(e.getUUID())) continue;
                strike(e, s.scale, s.at, s.kind == SPEAR ? 0.3 : 0.4, s.kind == SPEAR ? 0.65 : 0.45);
            }
            switch (s.kind) {
                case SPEAR -> {
                    EnemyFx.pillar(server, s.at, RED, 0.7f, 3.6f);
                    server.sendParticles(ParticleTypes.CRIT, s.at.x, s.at.y + 0.6, s.at.z, 6, 0.3, 0.5, 0.3, 0.2);
                    if (now % 2 == 0) server.playSound(null, BlockPos.containing(s.at), SoundEvents.TRIDENT_HIT_GROUND, SoundSource.HOSTILE, 1.4f, 0.5f);
                }
                case MARK -> {
                    EnemyFx.pillar(server, s.at, RED, 1.3f, 14f);
                    server.sendParticles(dust(HOT, 1.6f), s.at.x, s.at.y + 0.4, s.at.z, 24, 1.1, 0.2, 1.1, 0);
                    server.playSound(null, BlockPos.containing(s.at), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.HOSTILE, 2f, 1.2f);
                    GateService.shake(server, this, 0.35f, 30);
                }
                default -> {
                    EnemyFx.pillar(server, s.at, RED, 1.0f, 24f);
                    server.sendParticles(dust(RED, 1.8f), s.at.x, s.at.y + 0.4, s.at.z, 18, 0.9, 0.2, 0.9, 0);
                    server.playSound(null, BlockPos.containing(s.at), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1.4f, 0.7f);
                    GateService.shake(server, this, 0.3f, 24);
                }
            }
        }
    }

    /** He comes down: a ring of crimson out of the floor, and (enraged) spears all round. */
    private void land(ServerLevel server) {
        for (LivingEntity e : near(position(), LEAP_RADIUS + 1, 3)) {
            if (e.position().subtract(position()).horizontalDistance() <= LEAP_RADIUS + e.getBbWidth() / 2) strike(e, 1.2f, position(), 1.1, 0.6);
        }
        ring(position(), LEAP_RADIUS, 1f);
        EnemyFx.burst(server, position().add(0, 0.4, 0), RED, LEAP_RADIUS);
        level().playSound(null, blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 3f, 0.6f);
        level().playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5f, 0.6f);
        if (isEnraged()) {
            for (int i = 0; i < 8; i++) {
                float a = i * Mth.TWO_PI / 8;
                Vec3 d = new Vec3(Mth.cos(a), 0, Mth.sin(a));
                spears(server, position(), d, 2, 5.5f, 6);
            }
        }
    }

    private void callMoon(ServerLevel server) {
        moonCalled = true;
        entityData.set(MOON, true);
        boltIn = 40;
        EnemyFx.pillar(server, position(), RED, 2.4f, 40f);
        EnemyFx.burst(server, position().add(0, 3, 0), RED, 7f);
        level().playSound(null, blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 3f, 0.4f);
        level().playSound(null, blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 3f, 0.4f);
        GateService.shake(server, this, 1f, 70);
        TowerRun.lordGuards(server, this);
    }

    /** Block dust in a ring on the floor and a camera shake (no damage). */
    private void ring(Vec3 at, float radius, float shake) {
        if (!(level() instanceof ServerLevel server)) return;
        BlockState ground = level().getBlockState(BlockPos.containing(at).below());
        if (ground.isAir()) ground = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        for (int i = 0; i < 36; i++) {
            double a = i * Math.PI * 2 / 36;
            ParticleGuard.send(server, new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x + Math.cos(a) * radius * 0.85, at.y + 0.1,
                    at.z + Math.sin(a) * radius * 0.85, 3, 0.2, 0.1, 0.2, 0.15);
        }
        GateService.shake(server, this, shake, radius + 30);
    }

    private static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f), size);
    }

    /** The floor under a spot (down a few blocks if it is over a drop, up if it is in a wall). */
    private static Vec3 floor(ServerLevel level, Vec3 at) {
        BlockPos p = BlockPos.containing(at);
        for (int i = 0; i < 6 && level.getBlockState(p.below()).isAir(); i++) p = p.below();
        for (int i = 0; i < 4 && !level.getBlockState(p).isAir() && !level.getBlockState(p).getCollisionShape(level, p).isEmpty(); i++) p = p.above();
        return new Vec3(at.x, p.getY(), at.z);
    }

    // ------------------------------------------------------------------ the fight

    private void startMove(Move m, LivingEntity target) {
        move = m;
        moveTick = 0;
        beforeLast = last;
        last = m;
        Vec3 to = target.position().subtract(position());
        moveYaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
        aim = target.position();
        face();
        getNavigation().stop();
        triggerAnim("attack", m.anim);
        if (!(level() instanceof ServerLevel server)) return;
        switch (m) {
            case CLEAVE -> {
                EnemyFx.telegraphCone(server, position().add(0, 0.05, 0), look(), 7.5f + getBbWidth() / 2, 75f, 16, RED);
                level().playSound(null, blockPosition(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 2f, 0.5f);
            }
            case LANCE -> {
                lanceTelegraph(server);
                level().playSound(null, blockPosition(), SoundEvents.TRIDENT_RETURN, SoundSource.HOSTILE, 2.5f, 0.5f);
            }
            case DECREE -> level().playSound(null, blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 2.5f, 0.5f);
            case LEAP -> {
                leapFrom = position();
                leapTo = floor(server, aim);
                EnemyFx.telegraphCircle(server, leapTo.add(0, 0.05, 0), LEAP_RADIUS, 26, RED);
                level().playSound(null, blockPosition(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 2.5f, 0.5f);
            }
            case MOON -> level().playSound(null, blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 3f, 0.4f);
        }
    }

    private void face() {
        setYRot(moveYaw);
        yBodyRot = moveYaw;
        yHeadRot = moveYaw;
    }

    private void tickMove() {
        if (move == null || !(level() instanceof ServerLevel server)) return;
        int t = ++moveTick;
        face();
        if (move != Move.LEAP || t < 10 || t > 26) getNavigation().stop();
        switch (move) {
            case CLEAVE -> {
                if (t == 16) cleave(server);
            }
            case LANCE -> {
                if (t == 20) lance(server);
            }
            case DECREE -> {
                if (t == 8) decree(server);
            }
            case LEAP -> {
                if (t == 10) {
                    setNoGravity(true);
                    level().playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 2f, 0.6f);
                }
                if (t >= 10 && t <= 26) {
                    float u = (t - 10) / 16f;
                    Vec3 p = leapFrom.lerp(leapTo, u).add(0, Mth.sin(u * Mth.PI) * 5.5, 0);
                    setDeltaMovement(Vec3.ZERO);
                    setPos(p);
                    if (t % 2 == 0) server.sendParticles(dust(RED, 1.6f), getX(), getY() + 1.5, getZ(), 6, 0.4, 0.8, 0.4, 0);
                }
                if (t == 26) {
                    setNoGravity(false);
                    if (!level().noCollision(this)) {
                        // landed in something: step back to where he leapt from
                        setPos(leapFrom);
                    }
                    land(server);
                }
            }
            case MOON -> {
                if (t == 20) callMoon(server);
            }
        }
        if (move != null && t >= move.length) {
            move = null;
            setNoGravity(false);
            cooldown = (bloodMoon() ? 14 : isEnraged() ? 18 : 30) + random.nextInt(14);
        }
    }

    private int cooldown = 24;

    @Nullable
    private Move choose(double dist) {
        if (!moonCalled && getHealth() < getMaxHealth() * MOON_AT) return Move.MOON;
        float r = random.nextFloat();
        Move m;
        if (dist <= 5) m = r < 0.55f ? Move.CLEAVE : r < 0.85f ? Move.LANCE : Move.DECREE;
        else if (dist <= 12) m = r < 0.45f ? Move.LANCE : r < 0.7f ? Move.DECREE : Move.LEAP;
        else m = r < 0.45f ? Move.LEAP : r < 0.8f ? Move.DECREE : Move.LANCE;
        // never the same move three times running
        if (m == last && m == beforeLast) m = m == Move.LANCE ? Move.DECREE : Move.LANCE;
        if (m == Move.CLEAVE && dist > 5) m = Move.LANCE;
        return m;
    }

    private final class FightGoal extends Goal {
        FightGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return isAwake() && introTicks() <= 0 && getTarget() != null && getTarget().isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return isAlive() && (canUse() || move != null);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            if (move == Move.LEAP) setNoGravity(false);
            move = null;
        }

        @Override
        public void tick() {
            if (move != null) {
                tickMove();
                return;
            }
            LivingEntity target = getTarget();
            if (target == null) return;
            getLookControl().setLookAt(target, 30, 30);
            double dist = distanceTo(target) - getBbWidth() / 2;
            if (dist > 3.5) getNavigation().moveTo(target, 1.0);
            else getNavigation().stop();
            if (cooldown > 0) {
                cooldown--;
                return;
            }
            Move next = choose(dist);
            if (next != null) startMove(next, target);
        }
    }

    // ------------------------------------------------------------------ for TowerTestAccess

    /** Starts a move against his target at once (tests drive his moves by hand). */
    void forceMove(Move m, LivingEntity target) {
        if (introTicks() > 0) entityData.set(INTRO, 0);
        entityData.set(AWAKE, true);
        startMove(m, target);
    }

    /** Runs the move he is in to its end, tick by tick. */
    void finishMove() {
        while (move != null && isAlive()) {
            tickMove();
            if (level() instanceof ServerLevel server) tickStrikes(server);
        }
    }

    int pendingStrikes() {
        return strikes.size();
    }

    // ------------------------------------------------------------------ death

    @Override
    public void die(DamageSource source) {
        super.die(source);
        move = null;
        strikes.clear();
        setNoGravity(false);
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (level() instanceof ServerLevel server) {
            if (deathTime == 1) {
                level().playSound(null, blockPosition(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 1.6f, 0.6f);
                GateService.shake(server, this, 0.8f, 60);
            }
            if (deathTime == 30) {
                // down on one knee, the halberd driven into the floor
                level().playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 2f, 0.4f);
                ring(position(), 3.5f, 0.5f);
            }
            if (deathTime % 3 == 0) {
                server.sendParticles(dust(RED, 1.2f), getX(), getY() + 1.6, getZ(), 4, 0.6, 1.2, 0.6, 0);
                ParticleGuard.send(server, ParticleTypes.ASH, getX(), getY() + 1.8, getZ(), 10, 0.8, 1.2, 0.8, 0.02);
            }
            if (deathTime == 70) EnemyFx.pillar(server, position(), RED, 1.8f, 30f);
            if (deathTime >= DEATH_TICKS) {
                ParticleGuard.send(server, ParticleTypes.POOF, getX(), getY() + 1.5, getZ(), 60, 0.8, 1.4, 0.8, 0.05);
                EnemyFx.burst(server, position().add(0, 1.5, 0), RED, 5f);
                level().playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.4f, 0.5f);
                remove(RemovalReason.KILLED);
            }
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        // his reward is the Citadel's: the cache, and sometimes the Sigil of Dominion (TowerRun)
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.NETHERITE_BLOCK_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITHER_DEATH;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected float getSoundVolume() {
        return 2f;
    }

    @Override
    public float getVoicePitch() {
        return 0.6f;
    }

    @Override
    public Component getDisplayName() {
        return hasCustomName() ? super.getDisplayName() : Component.translatable("tower.beyondsolo.lord.name");
    }

    // ------------------------------------------------------------------ save

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Awake", isAwake());
        tag.putBoolean("Enraged", isEnraged());
        tag.putBoolean("Moon", bloodMoon());
        tag.putBoolean("MoonCalled", moonCalled);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(AWAKE, tag.getBoolean("Awake"));
        entityData.set(ENRAGED, tag.getBoolean("Enraged"));
        entityData.set(MOON, tag.getBoolean("Moon"));
        moonCalled = tag.getBoolean("MoonCalled");
    }

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> {
            if (isDeadOrDying()) return state.setAndContinue(DEATH);
            if (!isAwake()) return state.setAndContinue(DORMANT);
            if (introTicks() > 0) return state.setAndContinue(INTRO_ANIM);
            boolean moving = state.isMoving() || getDeltaMovement().horizontalDistanceSqr() > 0.0015;
            return state.setAndContinue(moving ? WALK : IDLE);
        }));
        controllers.add(new AnimationController<>(this, "attack", 2, state -> PlayState.STOP)
                .triggerableAnim("cleave", CLEAVE)
                .triggerableAnim("lance", LANCE)
                .triggerableAnim("decree", DECREE)
                .triggerableAnim("leap", LEAP)
                .triggerableAnim("moon", MOON_ANIM));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
