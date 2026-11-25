package com.beyondsolo.worldboss;

import com.beyondsolo.combat.Statuses;
import com.beyondsolo.gate.GateService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What every world boss and monarch shares ({@link WorldBossKind}):
 * <ul>
 *   <li><b>Three phases</b>, at the kind's health fractions. A phase can't be skipped: however hard it is hit, its
 *   health stops at the next phase's line until that phase has begun (a short transition: it roars, the world shakes,
 *   the banner names the phase). It takes damage all the while: no shield, no immunity, no healing.</li>
 *   <li><b>Moves</b>: each an animation and a script run on its ticks. Moves open with each phase, and the pause
 *   between them shortens as it goes.</li>
 *   <li><b>Strikes</b> on the ground, announced first ({@link WorldBossFx}): a disc, a band, a strip, a wedge, a
 *   square, and waves that run out along the ground and can be jumped. Everything it does can be seen coming.</li>
 *   <li><b>A share of the kill</b>: the health each hunter takes off it (their shadows and pets count for them) is kept,
 *   and the reward is shared by it ({@link WorldBosses#slain}). What another great threat takes off it (a ruler at war
 *   with a monarch) counts against every hunter's share: a kill the light did most of pays the hunters less.</li>
 * </ul>
 * It only ever hits hunters, what they own (shadows, pets), iron golems, its sworn {@link #foe foes} (a monarch's: the
 * rulers and their heralds) and whatever it is fighting: villagers and animals that wander into the fight are left alone.
 */
public abstract class ColossalBoss extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Boolean> AWAKE = SynchedEntityData.defineId(ColossalBoss.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> INTRO = SynchedEntityData.defineId(ColossalBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(ColossalBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SHIFT = SynchedEntityData.defineId(ColossalBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LEAVE = SynchedEntityData.defineId(ColossalBoss.class, EntityDataSerializers.INT);

    /** The shape of a strike on the ground. */
    public enum Shape { CIRCLE, RING, LINE, CONE, CELL }

    /**
     * A blow announced on the ground that lands at {@code when}. size: a circle's or a ring's outer radius, a strip's
     * width, a wedge's half-angle (degrees), a square's half side; inner: a ring's inner radius; tag: what the boss does
     * besides hurting (its own numbers, see {@link #landed}).
     */
    public record Strike(Shape shape, Vec3 at, Vec3 dir, float size, float length, float inner, long when, float scale, int tag) {}

    /** A wall of light running out along the ground from {@code center} to {@code radius}; jump it. */
    public record Wave(Vec3 center, float radius, float band, long start, int ticks, float scale, int tag, Set<Integer> hit) {}

    /**
     * A move: its animation, how long it lasts (ticks), how far its target must be (from its edge), how often it comes
     * up, the first phase it is used in, how long before it can come again (ticks), and whether it moves the boss
     * itself. {@code action} runs on each of its ticks.
     */
    public record Move(String anim, int length, float minRange, float maxRange, int weight, int minPhase, int cooldown, boolean mobile,
                       Action action) {}

    public interface Action {
        void tick(@Nullable LivingEntity target, int t, MoveState s);
    }

    /** What a move remembers between its ticks. */
    public static final class MoveState {
        public Vec3 dir = Vec3.ZERO;
        public Vec3 spot = Vec3.ZERO;
        public final List<Vec3> spots = new ArrayList<>();
        public final Set<Integer> hit = new HashSet<>();
        public int n;

        void reset(@Nullable LivingEntity target, Vec3 look) {
            spots.clear();
            hit.clear();
            n = 0;
            spot = target != null ? target.position() : Vec3.ZERO;
            dir = look;
        }
    }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Map<UUID, Float> dealt = new LinkedHashMap<>();
    /** Health taken off it by what isn't a hunter's: another great threat, its host or heralds. */
    private float foreign;
    protected final List<Strike> strikes = new ArrayList<>();
    protected final List<Wave> waves = new ArrayList<>();
    private final Map<String, Long> ready = new HashMap<>();
    @Nullable private List<Move> moves;
    @Nullable protected Move move;
    protected int moveTick;
    protected final MoveState moveState = new MoveState();
    @Nullable private Move last;
    protected int pause = 40;
    /** The event this boss belongs to ({@link WorldBossData}); null for one called up by hand outside any event. */
    @Nullable private UUID eventId;
    /** When a hunter last came near or hurt it (game time). */
    private long lastEngaged;
    /** The source of the blow being taken (a command's kill is never held back by the phase lines). */
    @Nullable private DamageSource taking;
    /** Its health as that blow began, less what blows inside it (its procs) took: what is left is the blow's own. */
    private float takingFrom;
    /** Where it rose or landed: the land it roams and keeps to. */
    @Nullable private BlockPos home;
    /** Its entrance: from where to where it moves while it plays (rising out of the earth, coming down from the sky). */
    @Nullable private Vec3 enterFrom;
    @Nullable private Vec3 enterTo;
    private int enterTicks;
    /** Where it goes as it leaves the world (down into the earth, up into the sky). */
    @Nullable private Vec3 leaveTo;
    @Nullable private Vec3 leaveFrom;

    protected ColossalBoss(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        setPersistenceRequired();
    }

    // ================================================================ what each kind says

    public abstract WorldBossKind kind();

    /** Every move it has, in every phase (made once). */
    protected abstract List<Move> buildMoves();

    /** Between moves, with a target: how it closes in (or keeps away). dist is from its edge. */
    protected abstract void approach(LivingEntity target, double dist);

    /** With nothing to fight. */
    protected void idle() {
        getNavigation().stop();
    }

    /** Ticks of its entrance (it can't be hurt, and doesn't fight, until they are over). */
    protected int introLength() {
        return 80;
    }

    /** Ticks of a phase change. */
    protected int shiftLength() {
        return 50;
    }

    /** Ticks of its death, until it is gone. */
    protected int deathLength() {
        return 100;
    }

    /** Called on the server as a phase begins (2 or 3), once its transition animation has started. */
    protected void onPhase(ServerLevel level, int phase) {}

    /** Called on the server on each tick of its entrance (t from 0). */
    protected void introTick(ServerLevel level, int t) {}

    /** Whether it passes through the land as it walks (the giants: trees and rocks don't stop them). */
    protected boolean walksThrough() {
        return false;
    }

    /** Called on the server on each tick of a phase change (t from 0). */
    protected void shiftTick(ServerLevel level, int phase, int t) {}

    /** Called on the server on each tick of its death (t from 1). */
    protected void deathTick(ServerLevel level, int t) {}

    /** Extra harm a strike of this kind does to whoever it caught (frost, a throw, a mark...). */
    protected void landed(ServerLevel level, Strike s, List<LivingEntity> caught) {}

    /** Extra harm a wave does to whoever it caught. */
    protected void waveHit(ServerLevel level, Wave w, LivingEntity e) {}

    /** What it does to anyone its blows land on, besides the damage (the element's buildup by default). */
    protected void afflict(LivingEntity e, float scale) {
        if (kind().element != null) Statuses.apply(e, kind().element, 18f * Math.min(1.6f, scale + 0.3f), this);
    }

    /** Its intro, phase and other one-off animations besides its moves'. */
    protected List<String> extraAnims() {
        return List.of("intro", "phase2", "phase3", "leave");
    }

    // ================================================================ state

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(AWAKE, false);
        builder.define(INTRO, 0);
        builder.define(PHASE, 1);
        builder.define(SHIFT, 0);
        builder.define(LEAVE, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FightGoal());
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    /** Sets its health and damage from its kind (call before adding it to the world). */
    public void setupStats() {
        WorldBossKind k = kind();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(k.health());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(k.damage());
        setHealth(getMaxHealth());
        setCustomName(k.fullName());
        setCustomNameVisible(false);
    }

    public boolean isAwake() {
        return entityData.get(AWAKE);
    }

    public int introTicks() {
        return entityData.get(INTRO);
    }

    /** 1, 2 or 3. */
    public int phase() {
        return entityData.get(PHASE);
    }

    /** Ticks left of a phase change (0 when there is none). */
    public int shiftTicks() {
        return entityData.get(SHIFT);
    }

    public boolean shifting() {
        return shiftTicks() > 0;
    }

    @Nullable
    public UUID eventId() {
        return eventId;
    }

    public void setEventId(@Nullable UUID id) {
        this.eventId = id;
    }

    public long lastEngaged() {
        return lastEngaged;
    }

    public void engaged() {
        lastEngaged = level().getGameTime();
    }

    @Nullable
    public BlockPos home() {
        return home;
    }

    public void setHome(@Nullable BlockPos home) {
        this.home = home;
    }

    /**
     * Its entrance moves it from {@code from} to {@code to} over the first {@code ticks} of its intro (eased), passing
     * through whatever is in the way. Call before {@link #wake()}.
     */
    public void entrance(Vec3 from, Vec3 to, int ticks) {
        enterFrom = from;
        enterTo = to;
        enterTicks = Math.max(1, ticks);
        setPos(from.x, from.y, from.z);
    }

    /**
     * Sets up its entrance before it is added to the world at {@code at}: by default it climbs out of the earth over the
     * first two thirds of its intro. It stands where it will be from the start, and only its model is drawn sunk into
     * the ground ({@link #buried}): the game draws nothing whose feet are in buried ground it has never had to show, so a
     * boss really moved up through the earth would only appear once it was nearly out.
     */
    public void prepareEntrance(ServerLevel level, Vec3 at) {
        setPos(at.x, at.y, at.z);
    }

    /**
     * Whether it comes out of the earth and goes back into it (drawn sunk, {@link #buried}), rather than travelling
     * through the air to get there (a monarch's descent).
     */
    protected boolean earthbound() {
        return true;
    }

    /**
     * How far below the ground it is drawn, in blocks: climbing out of the earth over the first two thirds of its
     * entrance, sinking back into it as it leaves. Always 0 for a boss that isn't {@link #earthbound}.
     */
    public float buried(float partial) {
        if (!earthbound()) return 0f;
        if (leaving()) {
            float k = Mth.clamp((leaveLength() - leaveTicks() + partial) / leaveLength(), 0f, 1f);
            return (getBbHeight() + 2) * k * k * (3 - 2 * k);
        }
        if (!isAwake() || introTicks() <= 0) return 0f;
        float rise = introLength() * 2 / 3f;
        float k = Mth.clamp((introLength() - introTicks() + partial) / rise, 0f, 1f);
        return (getBbHeight() + 1) * (1 - k * k * (3 - 2 * k));
    }

    /** Ticks left of its leaving (0: it isn't leaving). */
    public int leaveTicks() {
        return entityData.get(LEAVE);
    }

    public boolean leaving() {
        return leaveTicks() > 0;
    }

    /** How long it takes to leave the world. */
    protected int leaveLength() {
        return 80;
    }

    /**
     * Where it goes as it leaves: up into the sky (monarchs). A boss of the earth stays where it is and is drawn sinking
     * into it ({@link #buried}).
     */
    protected Vec3 leaveTarget() {
        return position();
    }

    /**
     * It leaves the world without falling: the fight stops, it goes (into the earth, up into the sky) and is gone. It
     * can't be hurt while it goes.
     */
    public void leave() {
        if (leaving() || !isAlive()) return;
        entityData.set(LEAVE, leaveLength());
        move = null;
        strikes.clear();
        waves.clear();
        leaveFrom = position();
        leaveTo = leaveTarget();
        getNavigation().stop();
        triggerAnim("attack", "leave");
    }

    /** Called on the server on each tick of its leaving (t from 0). */
    protected void leaveTick(ServerLevel level, int t) {}

    /** Starts its entrance; it fights once that is over. */
    public void wake() {
        if (isAwake()) return;
        entityData.set(AWAKE, true);
        entityData.set(INTRO, introLength());
        triggerAnim("attack", "intro");
        lastEngaged = level().getGameTime();
    }

    /** Wakes it with no entrance at all (commands, tests). */
    public void wakeNow() {
        entityData.set(AWAKE, true);
        entityData.set(INTRO, 0);
        lastEngaged = level().getGameTime();
    }

    /** The health it can't fall below in its current phase (until the next phase begins). */
    public float phaseFloor() {
        return switch (phase()) {
            case 1 -> getMaxHealth() * kind().phase2;
            case 2 -> getMaxHealth() * kind().phase3;
            default -> 0f;
        };
    }

    // ================================================================ damage and the share of it

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (!isAwake() || introTicks() > 0 || leaving()) return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        return super.isInvulnerableTo(source);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return super.hurt(source, amount);
        DamageSource outer = taking;
        float outerFrom = takingFrom;
        taking = source;
        takingFrom = getHealth();
        boolean hit;
        try {
            hit = super.hurt(source, amount);
        } finally {
            float lost = takingFrom - Math.max(0f, getHealth());
            // a killing blow was credited as it fell (die), before its fall was told
            if (taking == source && lost > 0) credit(source, lost);
            // a blow inside another (a proc of it) is its own hunter's share, not the outer blow's
            taking = outer;
            takingFrom = outerFrom - Math.max(0f, lost);
        }
        return hit;
    }

    /** Whoever is behind a blow: a hunter, or the hunter who owns the shadow, pet or arrow that struck. */
    @Nullable
    public static UUID hunterBehind(DamageSource source) {
        Entity e = source.getEntity();
        if (e instanceof Player p) return p.getUUID();
        if (e instanceof OwnableEntity o && o.getOwnerUUID() != null) return o.getOwnerUUID();
        if (source.getDirectEntity() instanceof Projectile pr && pr.getOwner() instanceof Player p) return p.getUUID();
        return null;
    }

    private void credit(DamageSource source, float lost) {
        UUID who = hunterBehind(source);
        if (who == null) {
            // another great threat's blow (or its host's): no hunter's, and it thins every hunter's share
            if (source.getEntity() instanceof LivingEntity && !(source.getEntity() instanceof Player)) foreign += lost;
            return;
        }
        dealt.merge(who, lost, Float::sum);
        engaged();
    }

    /** Health each hunter has taken off it. */
    public Map<UUID, Float> dealt() {
        return dealt;
    }

    public float totalDealt() {
        float t = 0;
        for (float v : dealt.values()) t += v;
        return t;
    }

    /** Health taken off it by others than hunters (another great threat, a host). */
    public float foreignDealt() {
        return foreign;
    }

    /** A hunter's share of the damage it has taken from hunters and from other great threats (0..1). */
    public float share(UUID who) {
        float total = totalDealt() + foreign;
        return total <= 0 ? 0 : dealt.getOrDefault(who, 0f) / total;
    }

    /** Health is held at the next phase's line until that phase begins: one huge blow can't skip a phase. */
    @Override
    public void setHealth(float health) {
        if (!level().isClientSide && isAwake() && isAlive() && health < getHealth() && phase() < 3
                && (taking == null || !taking.is(DamageTypeTags.BYPASSES_INVULNERABILITY))) {
            health = Math.max(health, phaseFloor());
        }
        super.setHealth(health);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        // its blows go through its moves; vanilla melee is not used
        return false;
    }

    // ================================================================ ticking

    @Override
    public void aiStep() {
        // a move that ended before it set its boss back down (a phase change cut it short, a command) leaves it to fall
        if (!level().isClientSide && move == null && noPhysics != walksThrough() && isAwake() && introTicks() <= 0 && !leaving()) {
            noPhysics = walksThrough();
        }
        boolean still = !isAwake() || introTicks() > 0 || shifting() || leaving();
        if (still && isAlive()) {
            setDeltaMovement(getDeltaMovement().multiply(0, enterFrom != null || leaving() ? 0 : 1, 0));
            getNavigation().stop();
        }
        super.aiStep();
        if (!(level() instanceof ServerLevel server) || !isAlive()) return;
        if (leaving()) {
            int t = leaveLength() - leaveTicks();
            entityData.set(LEAVE, leaveTicks() - 1);
            if (leaveFrom != null && leaveTo != null) {
                float k = Mth.clamp((t + 1) / (float) leaveLength(), 0, 1);
                k = k * k * (3 - 2 * k);
                Vec3 at = leaveFrom.lerp(leaveTo, k);
                noPhysics = true;
                setPos(at.x, at.y, at.z);
            }
            leaveTick(server, t);
            if (leaveTicks() <= 0) {
                discard();
                WorldBosses.left(server, this);
            }
            return;
        }
        if (isAwake() && introTicks() > 0) {
            int t = introLength() - introTicks();
            entityData.set(INTRO, introTicks() - 1);
            if (enterFrom != null && enterTo != null) {
                float k = Mth.clamp((t + 1) / (float) enterTicks, 0, 1);
                k = k * k * (3 - 2 * k);
                Vec3 at = enterFrom.lerp(enterTo, k);
                noPhysics = true;
                setPos(at.x, at.y, at.z);
                if (t + 1 >= enterTicks) {
                    enterFrom = null;
                    enterTo = null;
                    noPhysics = walksThrough();
                }
            }
            introTick(server, t);
        }
        if (shifting()) {
            int t = shiftLength() - shiftTicks();
            entityData.set(SHIFT, shiftTicks() - 1);
            shiftTick(server, phase(), t);
        }
        if (isAwake() && introTicks() <= 0 && !shifting()) {
            if (phase() == 1 && getHealth() <= getMaxHealth() * kind().phase2 + 0.01f) beginPhase(server, 2);
            else if (phase() == 2 && getHealth() <= getMaxHealth() * kind().phase3 + 0.01f) beginPhase(server, 3);
        }
        tickStrikes(server);
        tickWaves(server);
        if (tickCount % 20 == 0 && !level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(48, 24, 48),
                p -> !p.isSpectator() && !p.isCreative()).isEmpty()) engaged();
    }

    /** Starts a phase: its transition plays (it stands and roars), the world shakes and every hunter near hears of it. */
    public void beginPhase(ServerLevel level, int next) {
        if (next <= phase() || next > 3) return;
        entityData.set(PHASE, next);
        entityData.set(SHIFT, shiftLength());
        move = null;
        getNavigation().stop();
        triggerAnim("attack", "phase" + next);
        onPhase(level, next);
        WorldBosses.phaseBegan(level, this, next);
    }

    /** Sets its phase at once, for commands and tests: no transition. */
    public void forcePhase(ServerLevel level, int next) {
        next = Mth.clamp(next, 1, 3);
        if (next == phase()) return;
        entityData.set(PHASE, next);
        entityData.set(SHIFT, 0);
        float line = next == 2 ? kind().phase2 : next == 3 ? kind().phase3 : 1f;
        if (getHealth() > getMaxHealth() * line) super.setHealth(getMaxHealth() * line);
        onPhase(level, next);
    }

    // ================================================================ moves

    protected List<Move> moves() {
        if (moves == null) moves = buildMoves();
        return moves;
    }

    /** The pause between moves: shorter each phase. */
    protected int pauseAfter(Move m) {
        int base = switch (phase()) {
            case 1 -> 34;
            case 2 -> 26;
            default -> 18;
        };
        return base + random.nextInt(12);
    }

    @Nullable
    protected Move choose(LivingEntity target, double dist) {
        long now = level().getGameTime();
        List<Move> fits = new ArrayList<>();
        int total = 0;
        for (Move m : moves()) {
            if (m.minPhase() > phase() || dist < m.minRange() || dist > m.maxRange()) continue;
            if (ready.getOrDefault(m.anim(), 0L) > now) continue;
            // not the same move three times running
            int w = m == last ? Math.max(1, m.weight() / 3) : m.weight();
            fits.add(m);
            total += w;
        }
        if (fits.isEmpty()) return null;
        int roll = random.nextInt(total);
        for (Move m : fits) {
            roll -= m == last ? Math.max(1, m.weight() / 3) : m.weight();
            if (roll < 0) return m;
        }
        return fits.get(0);
    }

    /** Starts a move now (also for commands and tests). */
    public void start(Move m, @Nullable LivingEntity target) {
        move = m;
        moveTick = 0;
        last = m;
        moveState.reset(target, Vec3.directionFromRotation(0, getYRot()));
        if (m.cooldown() > 0) ready.put(m.anim(), level().getGameTime() + m.cooldown());
        triggerAnim("attack", m.anim());
    }

    /** A move by its animation's name (commands and tests). */
    @Nullable
    public Move moveNamed(String anim) {
        for (Move m : moves()) if (m.anim().equals(anim)) return m;
        return null;
    }

    public List<String> moveNames() {
        List<String> out = new ArrayList<>();
        for (Move m : moves()) out.add(m.anim());
        return out;
    }

    @Nullable
    public Move currentMove() {
        return move;
    }

    /** Runs the current move's tick (also used by tests to step a move without the goal). */
    public void stepMove() {
        if (move == null) return;
        Move m = move;
        if (!m.mobile()) {
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
        }
        m.action().tick(getTarget(), moveTick, moveState);
        if (move == m && ++moveTick >= m.length()) {
            move = null;
            pause = pauseAfter(m);
        }
    }

    private final class FightGoal extends Goal {
        FightGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return isAwake() && introTicks() <= 0 && isAlive() && !leaving();
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
            if (shifting()) return;
            if (move != null) {
                stepMove();
                return;
            }
            LivingEntity target = getTarget();
            if (target == null || !target.isAlive() || distanceToSqr(target) > 72 * 72) {
                if (target != null && !target.isAlive()) setTarget(null);
                idle();
                return;
            }
            getLookControl().setLookAt(target, 30, 30);
            double dist = Math.max(0, horizontalTo(target) - getBbWidth() / 2 - target.getBbWidth() / 2);
            approach(target, dist);
            if (pause > 0) {
                pause--;
                return;
            }
            Move next = choose(target, dist);
            if (next != null) ColossalBoss.this.start(next, target);
        }
    }

    // ================================================================ strikes

    /** Announces a blow on the ground and keeps it until it lands {@code ticks} later. */
    public Strike strike(Shape shape, Vec3 at, Vec3 dir, float size, float length, float inner, int ticks, float scale, int tag, int color) {
        Vec3 d = dir.lengthSqr() > 1e-6 ? dir.multiply(1, 0, 1).normalize() : Vec3.ZERO;
        Strike s = new Strike(shape, at, d, size, length, inner, level().getGameTime() + ticks, scale, tag);
        strikes.add(s);
        if (level() instanceof ServerLevel server) {
            switch (shape) {
                case CIRCLE -> WorldBossFx.circle(server, at, size, ticks, color);
                case RING -> WorldBossFx.ring(server, at, inner, size, ticks, color);
                case LINE -> WorldBossFx.line(server, at, d, length, size, ticks, color);
                case CONE -> WorldBossFx.cone(server, at, d, length, size, ticks, color);
                case CELL -> WorldBossFx.cell(server, at, size, ticks, color);
            }
        }
        return s;
    }

    public Strike circleStrike(Vec3 at, float radius, int ticks, float scale, int tag) {
        return strike(Shape.CIRCLE, at, Vec3.ZERO, radius, 0, 0, ticks, scale, tag, fxColor());
    }

    /** Sends a wave out along the ground. */
    public void wave(Vec3 center, float radius, float band, int ticks, float scale, int tag) {
        waves.add(new Wave(center, radius, band, level().getGameTime(), ticks, scale, tag, new HashSet<>()));
        if (level() instanceof ServerLevel server) WorldBossFx.wave(server, center, radius, band, ticks, fxColor());
    }

    private void tickStrikes(ServerLevel server) {
        if (strikes.isEmpty()) return;
        long now = server.getGameTime();
        // walk a copy: what lands may announce more
        List<Strike> due = new ArrayList<>();
        for (Iterator<Strike> it = strikes.iterator(); it.hasNext(); ) {
            Strike s = it.next();
            if (s.when() <= now) {
                due.add(s);
                it.remove();
            }
        }
        for (Strike s : due) {
            List<LivingEntity> caught = caughtBy(s);
            for (LivingEntity e : caught) {
                Vec3 away = e.position().subtract(s.at()).multiply(1, 0, 1);
                if (away.lengthSqr() < 1e-4) away = s.dir();
                hit(e, s.scale(), away, 0.6, 0.35);
            }
            landed(server, s, caught);
        }
    }

    /** Who stands in a strike's shape. */
    public List<LivingEntity> caughtBy(Strike s) {
        double reach = switch (s.shape()) {
            case LINE, CONE -> s.length() + 2;
            case CELL -> s.size() * 1.5 + 1;
            default -> s.size() + 2;
        };
        double cos = Math.cos(s.size() * Mth.DEG_TO_RAD);
        return level().getEntitiesOfClass(LivingEntity.class, new AABB(s.at(), s.at()).inflate(reach, 4, reach), e -> {
            if (!canHit(e)) return false;
            Vec3 to = e.position().subtract(s.at()).multiply(1, 0, 1);
            double d = to.length(), pad = e.getBbWidth() / 2;
            return switch (s.shape()) {
                case CIRCLE -> d <= s.size() + pad;
                case RING -> d <= s.size() + pad && d >= s.inner() - pad;
                case CELL -> Math.abs(to.x) <= s.size() + pad && Math.abs(to.z) <= s.size() + pad;
                case LINE -> {
                    double along = to.dot(s.dir());
                    double across = Math.abs(to.x * -s.dir().z + to.z * s.dir().x);
                    yield along >= -pad && along <= s.length() + pad && across <= s.size() / 2 + pad;
                }
                case CONE -> d <= s.length() + pad && (d < 1.5 || to.normalize().dot(s.dir()) >= cos);
            };
        });
    }

    private void tickWaves(ServerLevel server) {
        if (waves.isEmpty()) return;
        long now = server.getGameTime();
        for (Iterator<Wave> it = waves.iterator(); it.hasNext(); ) {
            Wave w = it.next();
            float k = (now - w.start()) / (float) Math.max(1, w.ticks());
            if (k > 1.02f) {
                it.remove();
                continue;
            }
            float r = w.radius() * Mth.clamp(k, 0, 1);
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class,
                    new AABB(w.center(), w.center()).inflate(r + w.band() + 1, 3, r + w.band() + 1), this::canHit)) {
                if (w.hit().contains(e.getId())) continue;
                double d = e.position().subtract(w.center()).horizontalDistance();
                if (Math.abs(d - r) > w.band() / 2 + e.getBbWidth() / 2) continue;
                // a wave runs along the ground: whoever is in the air when it passes is spared
                if (e.getY() - WorldBosses.groundY(server, e.getX(), e.getZ(), e.getY()) > 0.7) continue;
                w.hit().add(e.getId());
                hit(e, w.scale(), e.position().subtract(w.center()), 0.8, 0.45);
                waveHit(server, w, e);
            }
        }
    }

    // ================================================================ blows

    public float damage() {
        return (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    /** The colour of its warnings: its kind's, hotter once it is in its last phase. */
    public int fxColor() {
        return kind().color;
    }

    /**
     * A sworn foe besides the hunters: something its blows land on though it is a creature of the gates itself (a
     * monarch's: the rulers and their heralds). None by default.
     */
    protected boolean foe(LivingEntity e) {
        return false;
    }

    /** Can its blows land on {@code e}? Hunters and what they own, iron golems, its sworn foes, whatever it fights. */
    public boolean canHit(LivingEntity e) {
        if (e == this || !e.isAlive() || e instanceof ArmorStand) return false;
        if (foe(e)) return true;
        if (e instanceof Monster) return false;
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
        if (e instanceof OwnableEntity o && o.getOwnerUUID() != null) return true;
        return e instanceof IronGolem || e == getTarget();
    }

    /** Hurts {@code e} with one of its blows; pushed away along {@code away} (flat), and lifted. */
    public boolean hit(LivingEntity e, float scale, Vec3 away, double push, double lift) {
        if (!canHit(e)) return false;
        if (!e.hurt(damageSources().mobAttack(this), damage() * scale)) return false;
        afflict(e, scale);
        Vec3 d = away.multiply(1, 0, 1);
        d = d.lengthSqr() < 1e-4 ? Vec3.ZERO : d.normalize().scale(push);
        if (push > 0 || lift > 0) {
            e.push(d.x, lift, d.z);
            e.hurtMarked = true;
        }
        return true;
    }

    /** Everyone it can hit within {@code radius} of {@code at}. */
    public List<LivingEntity> near(Vec3 at, double radius) {
        return level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius, 6, radius),
                e -> canHit(e) && e.position().subtract(at).horizontalDistance() <= radius + e.getBbWidth() / 2);
    }

    /** The hunters fighting it: every player within {@code radius} it can hit, and its target. */
    public List<LivingEntity> fighters(double radius) {
        List<LivingEntity> out = new ArrayList<>(level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(radius, 32, radius), this::canHit));
        LivingEntity t = getTarget();
        if (t != null && canHit(t) && !out.contains(t) && distanceToSqr(t) < radius * radius) out.add(t);
        return out;
    }

    // ================================================================ helpers

    public double horizontalTo(Entity e) {
        return position().subtract(e.position()).horizontalDistance();
    }

    /** Turns to face {@code target} at once, and returns the flat direction to it. */
    public Vec3 face(@Nullable Entity target) {
        Vec3 d = target != null ? target.position().subtract(position()).multiply(1, 0, 1) : Vec3.ZERO;
        if (d.lengthSqr() < 1e-4) d = Vec3.directionFromRotation(0, getYRot());
        d = d.normalize();
        faceDir(d);
        return d;
    }

    public void faceDir(Vec3 d) {
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        setYRot(yaw);
        setYHeadRot(yaw);
        yBodyRot = yaw;
        yBodyRotO = yaw;
    }

    /** The ground under a point (the first solid surface near its height, leaves and logs ignored). */
    public Vec3 ground(Vec3 at) {
        if (!(level() instanceof ServerLevel server)) return at;
        return new Vec3(at.x, WorldBosses.groundY(server, at.x, at.z, at.y), at.z);
    }

    /** Where a point {@code dist} ahead of it along {@code dir} lies on the ground. */
    public Vec3 ahead(Vec3 dir, double dist) {
        return ground(position().add(dir.scale(dist)));
    }

    public void sound(SoundEvent sound, float volume, float pitch) {
        level().playSound(null, getX(), getY(), getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    public void soundAt(Vec3 at, SoundEvent sound, float volume, float pitch) {
        level().playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    public void shake(float strength, double radius) {
        if (level() instanceof ServerLevel server) GateService.shake(server, this, strength, radius);
    }

    /** Dust and broken ground round a point (for a heavy blow). */
    public void dust(Vec3 at, float radius, int count) {
        if (!(level() instanceof ServerLevel server)) return;
        BlockState ground = server.getBlockState(BlockPos.containing(at).below());
        if (ground.isAir()) ground = server.getBlockState(blockPosition().below());
        if (ground.isAir()) return;
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = radius * Math.sqrt(random.nextDouble());
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x + Math.cos(a) * r, at.y + 0.2, at.z + Math.sin(a) * r,
                    2, 0.2, 0.1, 0.2, 0.15);
        }
        server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.5, at.z, Math.max(2, count / 8), radius * 0.4, 0.3, radius * 0.4, 0.01);
    }

    // ================================================================ death

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && taking != null) {
            // the killing blow counts before the fall is told: the shares, and who did the most
            float lost = takingFrom - Math.max(0f, getHealth());
            if (lost > 0) credit(taking, lost);
            taking = null;
        }
        super.die(source);
        move = null;
        strikes.clear();
        waves.clear();
        if (level() instanceof ServerLevel server) WorldBosses.slain(server, this, source);
    }

    /** What is left of it as it goes: a puff of dust (a monarch leaves ink). */
    protected net.minecraft.core.particles.ParticleOptions remains() {
        return ParticleTypes.POOF;
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (level() instanceof ServerLevel server) {
            deathTick(server, deathTime);
            if (deathTime >= deathLength()) {
                WorldBossFx.burst(server, position().add(0, getBbHeight() * 0.4, 0), getBbHeight() * 0.6f, fxColor());
                server.sendParticles(remains(), getX(), getY() + getBbHeight() * 0.3, getZ(), 60, getBbWidth() * 0.5,
                        getBbHeight() * 0.3, getBbWidth() * 0.5, 0.05);
                remove(RemovalReason.KILLED);
            }
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        // its loot goes to the hunters who fought it (WorldBosses.slain)
    }

    @Override
    protected float getSoundVolume() {
        return 4f;
    }

    @Override
    public Component getDisplayName() {
        Component custom = getCustomName();
        return custom != null ? custom : kind().fullName();
    }

    // ================================================================ save

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Awake", isAwake());
        tag.putInt("Phase", phase());
        if (eventId != null) tag.putUUID("Event", eventId);
        tag.putLong("Engaged", lastEngaged);
        if (home != null) tag.putLong("Home", home.asLong());
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Float> e : dealt.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("Hunter", e.getKey());
            c.putFloat("Dealt", e.getValue());
            list.add(c);
        }
        tag.put("Dealt", list);
        if (foreign > 0) tag.putFloat("Foreign", foreign);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(AWAKE, tag.getBoolean("Awake"));
        entityData.set(PHASE, Mth.clamp(tag.contains("Phase") ? tag.getInt("Phase") : 1, 1, 3));
        eventId = tag.hasUUID("Event") ? tag.getUUID("Event") : null;
        lastEngaged = tag.getLong("Engaged");
        home = tag.contains("Home") ? BlockPos.of(tag.getLong("Home")) : null;
        noPhysics = walksThrough();
        foreign = tag.getFloat("Foreign");
        dealt.clear();
        for (Tag t : tag.getList("Dealt", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            if (c.hasUUID("Hunter")) dealt.put(c.getUUID("Hunter"), c.getFloat("Dealt"));
        }
    }

    // ================================================================ animation

    /** The short name of its looping animation now ("idle", "walk", ...). */
    protected String baseAnim() {
        return moved() > 0.0012 && isAwake() ? "walk" : "idle";
    }

    /** How far it moved flat since last tick, squared (on either side: the client sees it from where it is drawn). */
    protected double moved() {
        double dx = getX() - xo, dz = getZ() - zo;
        return dx * dx + dz * dz;
    }

    protected String animPrefix() {
        return "animation.beyondsolo." + kind().asset() + ".";
    }

    /** Every animation it plays: its loops, its death, its intro, phase changes and leaving, and its moves. */
    public Set<String> animNames() {
        Set<String> names = new HashSet<>(List.of("idle", "walk", "dormant", "death"));
        names.addAll(extraAnims());
        for (Move m : moves()) names.add(m.anim());
        return names;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        String base = animPrefix();
        controllers.add(new AnimationController<>(this, "main", 6, state -> {
            if (isDeadOrDying()) return state.setAndContinue(RawAnimation.begin().thenPlayAndHold(base + "death"));
            if (!isAwake()) return state.setAndContinue(RawAnimation.begin().thenLoop(base + "dormant"));
            if (leaving()) return PlayState.STOP;
            return state.setAndContinue(RawAnimation.begin().thenLoop(base + baseAnim()));
        }));
        AnimationController<ColossalBoss> attack = new AnimationController<>(this, "attack", 3, state -> PlayState.STOP);
        Set<String> names = new HashSet<>(extraAnims());
        for (Move m : moves()) names.add(m.anim());
        for (String n : names) attack.triggerableAnim(n, RawAnimation.begin().thenPlay(base + n));
        controllers.add(attack);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** Drawn while any of it is in sight: wings, raised weapons, falls and leaps reach well past its box. */
    @Override
    public AABB getBoundingBoxForCulling() {
        float reach = Math.max(getBbWidth(), getBbHeight()) * 0.9f;
        return getBoundingBox().inflate(reach, reach * 0.6f, reach).expandTowards(0, reach * 0.5f, 0);
    }

    /** Players who can see it from where they are (for banners and sounds). */
    public List<ServerPlayer> watchers(double radius) {
        if (!(level() instanceof ServerLevel server)) return List.of();
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : server.players()) if (p.distanceToSqr(this) < radius * radius) out.add(p);
        return out;
    }
}
