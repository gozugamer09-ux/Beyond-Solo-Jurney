package com.beyondsolo.duel;

import com.beyondsolo.item.arsenal.Arsenal;
import com.beyondsolo.network.SkillFxPayload;
import com.beyondsolo.skill.SkillFx;
import com.beyondsolo.system.Balance;
import com.beyondsolo.util.OnlinePlayers;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * A national-level hunter come for a duel ({@link Duels}): they walk up to the hunter with their challenge, wait for
 * the answer, and fight in the ring with their own moves ({@link Move}), every one shown before it lands (a circle on
 * the ground, a sight line, a mark), and at half health they show what they really are ({@link NationalHunter#unleashed}).
 * They never die: at the end of their strength they yield. Nothing outside a duel can hurt them. Never saved.
 */
public class DuelistEntity extends PathfinderMob {
    /** Walking up to the hunter; by them, waiting for an answer; the count of three; fighting; down on one knee; leaving. */
    public static final byte ARRIVING = 0, WAITING = 1, READY = 2, FIGHTING = 3, YIELDED = 4, STANDING = 5, LEAVING = 6;
    /** How near they come to the hunter, and how far off they let them get before following. */
    static final double NEAR = 2.8, FOLLOW = 3.8;
    /** A hunter further off than this is let go (the challenge stands). */
    static final double LOSE = 56;
    /** How long the change at half health takes (they shrug most of what hits them meanwhile). */
    static final int UNLEASH_TICKS = 30;

    private static final EntityDataAccessor<Integer> WHO = SynchedEntityData.defineId(DuelistEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(DuelistEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> UNLEASHED = SynchedEntityData.defineId(DuelistEntity.class, EntityDataSerializers.BOOLEAN);
    /** The move under way ({@link Move} ordinal), or -1; its count goes up with every move so a repeat shows again. */
    private static final EntityDataAccessor<Integer> MOVE = SynchedEntityData.defineId(DuelistEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MOVES = SynchedEntityData.defineId(DuelistEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> VEILED = SynchedEntityData.defineId(DuelistEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * Their moves. Each waits out its cooldown, wants the hunter between {@code min} and {@code max} blocks off, shows
     * itself for {@code windup} ticks and then lands; some come only once they are unleashed.
     */
    public enum Move {
        IRON_CHARGE(NationalHunter.Style.TITAN, 160, 18, 5, 16, false),
        QUAKE_SLAM(NationalHunter.Style.TITAN, 140, 22, 0, 6.5, false),
        IRON_SKIN(NationalHunter.Style.TITAN, 420, 6, 0, 99, false),
        TITAN_STOMP(NationalHunter.Style.TITAN, 170, 26, 0, 8, true),
        BOULDER(NationalHunter.Style.TITAN, 190, 20, 7, 24, true),

        TIDE_BOLT(NationalHunter.Style.TIDE, 34, 9, 0, 26, false),
        RIPTIDE(NationalHunter.Style.TIDE, 120, 24, 0, 24, false),
        MIST_STEP(NationalHunter.Style.TIDE, 100, 3, 0, 4.5, false),
        THUNDERCALL(NationalHunter.Style.TIDE, 70, 20, 0, 28, true),

        SUN_ARROW(NationalHunter.Style.SUN, 26, 11, 0, 30, false),
        TRIPLE_SHOT(NationalHunter.Style.SUN, 90, 14, 0, 24, false),
        SKYFALL(NationalHunter.Style.SUN, 140, 22, 0, 28, false),
        BACKFLIP(NationalHunter.Style.SUN, 80, 3, 0, 4.5, false),
        RAIN_OF_DAWN(NationalHunter.Style.SUN, 170, 20, 0, 28, true),
        SUNLANCE(NationalHunter.Style.SUN, 230, 40, 0, 32, true),

        SHADOW_STEP(NationalHunter.Style.SHADE, 100, 10, 4, 18, false),
        KNIVES(NationalHunter.Style.SHADE, 120, 12, 0, 12, false),
        SMOKE(NationalHunter.Style.SHADE, 320, 4, 0, 99, false),
        THOUSAND_CUTS(NationalHunter.Style.SHADE, 210, 20, 0, 10, true),

        /** The change at half health (any of them). */
        UNLEASH(null, 0, UNLEASH_TICKS, 0, 99, false);

        @Nullable public final NationalHunter.Style style;
        public final int cooldown, windup;
        final double min, max;
        final boolean unleashed;

        Move(@Nullable NationalHunter.Style style, int cooldown, int windup, double min, double max, boolean unleashed) {
            this.style = style;
            this.cooldown = cooldown;
            this.windup = windup;
            this.min = min;
            this.max = max;
            this.unleashed = unleashed;
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public static @Nullable Move byIndex(int i) {
            Move[] all = values();
            return i >= 0 && i < all.length ? all[i] : null;
        }
    }

    /** Something landing later where it was aimed: a geyser, a bolt from the sky, a boulder, a rain of arrows. */
    private record Impact(long at, Vec3 center, double radius, float mult, int hits, int every, SkillFxPayload.Kind fx, int fxLook,
                          boolean grounded, double launch) {}

    // ------------------------------------------------------------------ server state

    @Nullable private UUID hunter;
    @Nullable private UUID challenge;
    @Nullable private UUID duel;
    private DuelRecord.Reason why = DuelRecord.Reason.ASKED;
    /** What one of their blows deals. */
    private float power = 10;
    private int waited;
    private boolean greeted;
    private int leftAt = -1;
    /** Ticks in their current state (yielded, standing). */
    private int inState;

    @Nullable private Move move;
    private int moveAge;
    /** Where the move under way is aimed. */
    @Nullable private Vec3 aim;
    private final int[] cooldowns = new int[Move.values().length];
    private int blowCooldown, rest;
    private boolean struck;
    private final List<Impact> impacts = new ArrayList<>();
    private int ironSkinUntil, veilUntil;
    private boolean nextBlowVeiled;
    /** Strafing: which way round the hunter they circle, and until when. */
    private int strafe = 1, strafeUntil;
    private int repath;
    private long lastHitAt;

    public DuelistEntity(EntityType<? extends DuelistEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        xpReward = 0;
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 64)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(WHO, 0);
        b.define(STATE, ARRIVING);
        b.define(UNLEASHED, false);
        b.define(MOVE, -1);
        b.define(MOVES, 0);
        b.define(VEILED, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 12f, 0.9f));
    }

    // ------------------------------------------------------------------ who they are

    /** Sent for {@code hunter} with their challenge: looks, name, weapon. */
    void setup(ServerPlayer hunter, DuelRecord.Challenge c) {
        this.hunter = hunter.getUUID();
        this.challenge = c.id;
        this.why = c.why;
        entityData.set(WHO, c.who.ordinal());
        NationalHunter who = c.who;
        // a name tag of words, not a string: each player reads "national-level hunter" in their own language
        setCustomName(Component.literal("[").append(Component.translatable("duel.beyondsolo.national")).append("] ")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(who.color & 0xFFFFFF)))
                .append(Component.literal(who.rival().name()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF)))));
        setCustomNameVisible(true);
        Item weapon = weapon(who);
        if (weapon != null) setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
        setDropChance(EquipmentSlot.MAINHAND, 0f);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(who.style == NationalHunter.Style.SHADE ? 0.36 : who.style == NationalHunter.Style.TITAN ? 0.31 : 0.33);
    }

    @Nullable
    static Item weapon(NationalHunter who) {
        return switch (who.style) {
            case TITAN -> Arsenal.STORMLORD_LONGSWORD.get();
            case TIDE -> Arsenal.STARSEER_STAFF.get();
            case SUN -> Arsenal.GALEBREAKER_WARBOW.get();
            case SHADE -> Arsenal.NIGHTVEIL_KRIS.get();
        };
    }

    /** Their strength for a duel at {@code level}: a dungeon boss's made for a hunter of that level. */
    void arm(float level) {
        double health = Balance.BOSS_HEALTH * Balance.healthAt(level) * DuelConfig.health();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.max(40, health));
        setHealth(getMaxHealth());
        power = (float) Math.max(2, Balance.BOSS_DAMAGE * Balance.damageAt(level) * DuelConfig.damage());
    }

    public NationalHunter who() {
        return NationalHunter.byIndex(entityData.get(WHO));
    }

    public byte state() {
        return entityData.get(STATE);
    }

    public boolean unleashed() {
        return entityData.get(UNLEASHED);
    }

    /** The move under way, or null (the client poses them with it, the HUD calls it out). */
    @Nullable
    public Move currentMove() {
        return Move.byIndex(entityData.get(MOVE));
    }

    /** Goes up by one with every move begun. */
    public int moveCount() {
        return entityData.get(MOVES);
    }

    public boolean veiled() {
        return entityData.get(VEILED);
    }

    @Nullable public UUID hunter() { return hunter; }
    @Nullable public UUID challenge() { return challenge; }
    @Nullable public UUID duel() { return duel; }
    public float power() { return power; }
    public boolean leaving() { return state() == LEAVING; }

    void setState(byte state) {
        entityData.set(STATE, state);
        inState = 0;
        setPose(state == YIELDED ? Pose.CROUCHING : Pose.STANDING);
        if (state != FIGHTING) {
            endMove();
            impacts.clear();
            veil(false);
            setNoGravity(false);
        }
    }

    // ------------------------------------------------------------------ talking

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level().isClientSide && player instanceof ServerPlayer p) {
            getLookControl().setLookAt(p, 30f, 30f);
            Duels.talk(p, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    // ------------------------------------------------------------------ the duel

    /** Takes their place for a duel: their strength, their spot across the ring. */
    void enter(Duel d, Vec3 spot) {
        this.duel = d.id;
        arm(d.level);
        getNavigation().stop();
        moveTo(spot.x, spot.y, spot.z, getYRot(), 0);
        setState(READY);
        java.util.Arrays.fill(cooldowns, 0);
        // nothing comes at once: the first of each move waits a little
        for (Move m : Move.values()) cooldowns[m.ordinal()] = m.cooldown / 3 + random.nextInt(20);
        blowCooldown = 20;
        rest = 10;
    }

    /** The bell: they fight. */
    void fight() {
        setState(FIGHTING);
    }

    /** It's over: down on one knee, or standing over the hunter, then off they go. */
    void after(boolean yielded) {
        setState(yielded ? YIELDED : STANDING);
        calm();
        entityData.set(UNLEASHED, false);
        var scale = getAttribute(Attributes.SCALE);
        if (scale != null) scale.setBaseValue(1.0);
        setHealth(getMaxHealth());
        removeEffect(MobEffects.INVISIBILITY);
    }

    @Nullable
    private ServerPlayer hunterPlayer() {
        if (!(level() instanceof ServerLevel server) || hunter == null) return null;
        ServerPlayer p = OnlinePlayers.get(server.getServer(), hunter);
        return p != null && p.level() == level() ? p : null;
    }

    @Nullable
    private Duel duelNow() {
        if (duel == null || hunter == null) return null;
        Duel d = Duels.running(hunter);
        return d != null && d.id.equals(duel) ? d : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel level)) return;
        inState++;
        byte s = state();
        if (s == LEAVING) {
            if (inState > 20 * 15 || level.getNearestPlayer(this, 28) == null) poof();
            return;
        }
        ServerPlayer p = hunterPlayer();
        if (s == FIGHTING || s == READY) {
            Duel d = duelNow();
            if (d == null || p == null) {
                leave(p);
                return;
            }
            if (d.phase == Duel.Phase.OVER) {
                // the end of it is being settled: they stand
                getNavigation().stop();
                return;
            }
            if (s == READY) {
                getNavigation().stop();
                getLookControl().setLookAt(p, 30f, 30f);
                face(p.position());
                return;
            }
            fightTick(level, p, d);
            return;
        }
        if (s == YIELDED || s == STANDING) {
            getNavigation().stop();
            if (p != null) getLookControl().setLookAt(p, 30f, 30f);
            if (s == YIELDED && inState == 70) setPose(Pose.STANDING);
            if (inState > 20 * 6) leave(p);
            return;
        }
        // walking up with the challenge, and waiting by the hunter for the answer
        if (tickCount % 5 != 0) return;
        if (p == null || !p.isAlive() || distanceToSqr(p) > LOSE * LOSE || !Duels.stillChallenging(p, this)) {
            leave(p);
            return;
        }
        double d = distanceTo(p);
        if (d > FOLLOW) {
            if (getNavigation().isDone() || tickCount % 20 == 0) getNavigation().moveTo(p, d > 12 ? 1.15 : 1.0);
        } else {
            if (d < NEAR) getNavigation().stop();
            getLookControl().setLookAt(p, 30f, 30f);
            if (!greeted) {
                greeted = true;
                entityData.set(STATE, WAITING);
                Duels.greet(p, this);
            }
            waited += 5;
            // the hunter's window open on them: they wait as long as it takes
            if (waited > DuelConfig.waitTicks() && !Duels.talking(p, this)) {
                Duels.gaveUp(p, this);
                leave(p);
            }
        }
    }

    /** Off they go, away from the hunter. */
    void leave(@Nullable ServerPlayer from) {
        if (state() == LEAVING) return;
        setState(LEAVING);
        leftAt = tickCount;
        Vec3 away = from == null ? getLookAngle().scale(-1) : position().subtract(from.position());
        Vec3 flat = new Vec3(away.x, 0, away.z);
        flat = flat.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : flat.normalize();
        Vec3 to = position().add(flat.scale(24));
        getNavigation().moveTo(to.x, to.y, to.z, 1.0);
    }

    /** Gone in a blink of their colour. */
    void poof() {
        calm();
        if (level() instanceof ServerLevel server) {
            SkillFx.play(this, SkillFxPayload.Kind.BLINK, who().color, position(), 1f, 1);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.5f, 1.3f);
        }
        discard();
    }

    void face(Vec3 at) {
        Vec3 d = at.subtract(position());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
    }

    // ------------------------------------------------------------------ fighting

    private void fightTick(ServerLevel level, ServerPlayer p, Duel d) {
        for (int i = 0; i < cooldowns.length; i++) if (cooldowns[i] > 0) cooldowns[i]--;
        if (blowCooldown > 0) blowCooldown--;
        if (rest > 0) rest--;
        tickImpacts(level, p, d);
        NationalHunter who = who();
        // out of the ring (thrown, or chasing): back in at once
        if (!d.inside(position(), 1.5)) {
            Vec3 in = d.center.add(position().subtract(d.center).multiply(1, 0, 1).normalize().scale(d.radius - 2));
            SkillFx.play(this, SkillFxPayload.Kind.BLINK, who.color, position(), 1f, 1);
            Vec3 spot = Duels.footing(level, in);
            moveTo(spot.x, spot.y, spot.z, getYRot(), 0);
            getNavigation().stop();
        }
        // pressed to half: they show what they are
        if (!unleashed() && move != Move.UNLEASH && getHealth() <= getMaxHealth() * DuelConfig.unleashAt()) {
            endMove();
            begin(Move.UNLEASH, position());
        }
        if (veilUntil > 0 && tickCount >= veilUntil && !(who.style == NationalHunter.Style.SHADE && unleashed())) veil(false);
        if (who.style == NationalHunter.Style.SHADE && unleashed() && !veiled() && tickCount >= veilUntil) veil(true);
        if (who.style == NationalHunter.Style.TIDE && unleashed()) maelstrom(level, p, d);
        if (who.style == NationalHunter.Style.SUN && unleashed()) hover(level);
        getLookControl().setLookAt(p, 40f, 40f);

        if (move != null) {
            moveAge++;
            runMove(level, p, d);
            return;
        }
        double dist = distanceTo(p);
        if (rest <= 0) {
            Move next = pick(p, dist);
            if (next != null) {
                begin(next, p.position());
                return;
            }
        }
        // their blows (the ones who fight up close, or anyone the hunter walks into)
        double reach = reach();
        if (blowCooldown <= 0 && dist <= reach && hasLineOfSight(p)) blow(level, p, d);
        walk(p, d, dist);
    }

    private double reach() {
        double scale = getAttributeValue(Attributes.SCALE);
        return switch (who().style) {
            case TITAN -> 3.2 + (scale - 1) * 2.2;
            case SHADE -> 3.0;
            default -> 2.6;
        };
    }

    /** Where they want to be: up close, or off at their range, circling. */
    private void walk(ServerPlayer p, Duel d, double dist) {
        NationalHunter.Style style = who().style;
        if (--repath > 0) return;
        repath = 8;
        if (tickCount >= strafeUntil) {
            strafe = random.nextBoolean() ? 1 : -1;
            strafeUntil = tickCount + 50 + random.nextInt(50);
        }
        double want = switch (style) {
            case TITAN, SHADE -> 1.6;
            case TIDE -> 10;
            case SUN -> 11;
        };
        double speed = style == NationalHunter.Style.SHADE && unleashed() ? 1.35 : 1.15;
        Vec3 target;
        if (want < 3) {
            target = p.position();
        } else {
            Vec3 from = position().subtract(p.position()).multiply(1, 0, 1);
            if (from.lengthSqr() < 1e-3) from = new Vec3(1, 0, 0);
            from = from.normalize();
            double ang = strafe * 0.45;
            Vec3 turned = new Vec3(from.x * Math.cos(ang) - from.z * Math.sin(ang), 0, from.x * Math.sin(ang) + from.z * Math.cos(ang));
            target = p.position().add(turned.scale(want));
            if (Math.abs(dist - want) < 2 && random.nextFloat() < 0.5f) speed = 0.9;
        }
        // never out of the ring
        Vec3 off = target.subtract(d.center).multiply(1, 0, 1);
        if (off.length() > d.radius - 1.5) target = d.center.add(off.normalize().scale(d.radius - 1.5)).add(0, target.y - d.center.y, 0);
        getNavigation().moveTo(target.x, target.y, target.z, speed);
    }

    /** One of their blows. */
    private void blow(ServerLevel level, ServerPlayer p, Duel d) {
        NationalHunter who = who();
        float mult = switch (who.style) {
            case TITAN -> unleashed() ? 1.4f : 1.0f;
            case SHADE -> 0.7f;
            default -> 0.6f;
        };
        if (nextBlowVeiled) {
            mult *= 1.5f;
            nextBlowVeiled = false;
            veil(false);
            if (who.style == NationalHunter.Style.SHADE && unleashed()) veilUntil = tickCount + 18;
        }
        swing(InteractionHand.MAIN_HAND);
        blowCooldown = who.style == NationalHunter.Style.SHADE ? (unleashed() ? 10 : 13) : who.style == NationalHunter.Style.TITAN ? (unleashed() ? 26 : 22) : 24;
        if (hit(p, mult)) {
            Vec3 at = p.position().add(0, 1, 0);
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.3, 0.3, 0.3, 0.2);
            level.playSound(null, at.x, at.y, at.z, who.style == NationalHunter.Style.TITAN ? SoundEvents.ANVIL_LAND : SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.HOSTILE, who.style == NationalHunter.Style.TITAN ? 0.35f : 0.8f, who.style == NationalHunter.Style.TITAN ? 0.7f : 1.3f);
            double dx = p.getX() - getX(), dz = p.getZ() - getZ();
            double len = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
            p.knockback(who.style == NationalHunter.Style.TITAN ? (unleashed() ? 1.1 : 0.7) : 0.3, -dx / len, -dz / len);
            p.hurtMarked = true;
        }
    }

    /** The next move worth making now, or null. */
    @Nullable
    private Move pick(ServerPlayer p, double dist) {
        NationalHunter who = who();
        List<Move> ready = new ArrayList<>();
        for (Move m : Move.values()) {
            if (m.style != who.style || cooldowns[m.ordinal()] > 0) continue;
            if (m.unleashed && !unleashed()) continue;
            if (dist < m.min || dist > m.max) continue;
            if (m == Move.IRON_SKIN && getHealth() > getMaxHealth() * 0.7f) continue;
            if (m == Move.SMOKE && (getHealth() > getMaxHealth() * 0.8f || unleashed())) continue;
            if (m != Move.MIST_STEP && m != Move.BACKFLIP && m != Move.IRON_SKIN && m != Move.SMOKE && !hasLineOfSight(p)) continue;
            ready.add(m);
        }
        if (ready.isEmpty()) return null;
        // getting out of reach comes first for the ones who fight from afar
        for (Move m : ready) if (m == Move.MIST_STEP || m == Move.BACKFLIP) return m;
        // the big ones before the small, but not always
        Move best = null;
        for (Move m : ready) if (best == null || m.cooldown > best.cooldown) best = m;
        return random.nextFloat() < 0.65f ? best : ready.get(random.nextInt(ready.size()));
    }

    private void begin(Move m, Vec3 target) {
        move = m;
        moveAge = 0;
        struck = false;
        aim = target;
        entityData.set(MOVE, m.ordinal());
        entityData.set(MOVES, entityData.get(MOVES) + 1);
        cooldowns[m.ordinal()] = m.cooldown;
        getNavigation().stop();
        start(m);
    }

    private void endMove() {
        move = null;
        aim = null;
        moveAge = 0;
        if (entityData.get(MOVE) != -1) entityData.set(MOVE, -1);
        rest = 8 + random.nextInt(10);
    }

    /** Where a shot at the hunter should go: where they will be, about, if they keep going as they are. */
    private Vec3 lead(ServerPlayer p, int ticks, double share) {
        Vec3 v = p.getDeltaMovement().multiply(1, 0, 1);
        return p.position().add(v.scale(ticks * share));
    }

    private int color() {
        return who().color;
    }

    /** What the move shows as it begins: its warning. */
    private void start(Move m) {
        ServerPlayer p = hunterPlayer();
        if (p == null) return;
        int c = color();
        ServerLevel level = (ServerLevel) level();
        switch (m) {
            case IRON_CHARGE -> {
                aim = p.position();
                SkillFx.play(this, SkillFxPayload.Kind.ROAR, c, position().add(0, 1.2, 0), 1.4f, 0);
                SkillFx.play(this, SkillFxPayload.Kind.SIGHT, c, aim.add(0, 0.6, 0), 1f, m.windup);
                sound(SoundEvents.RAVAGER_ROAR, 0.8f, 1.2f);
            }
            case QUAKE_SLAM -> {
                aim = p.position();
                SkillFx.play(this, SkillFxPayload.Kind.ZONE, c, aim, 4.2f, m.windup + 4);
                sound(SoundEvents.IRON_GOLEM_ATTACK, 1f, 0.6f);
            }
            case IRON_SKIN -> {
                ironSkinUntil = tickCount + 70;
                SkillFx.play(this, SkillFxPayload.Kind.SHIELD, c, position().add(0, 1, 0), 1.6f, 70);
                SkillFx.play(this, SkillFxPayload.Kind.AURA, 0xC9D3DD, position(), 1.2f, 70);
                sound(SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.2f, 0.6f);
            }
            case TITAN_STOMP -> {
                aim = position();
                SkillFx.play(this, SkillFxPayload.Kind.ZONE, c, aim, 7f, m.windup + 4);
                SkillFx.play(this, SkillFxPayload.Kind.ROAR, c, position().add(0, 2, 0), 2.6f, 0);
                sound(SoundEvents.RAVAGER_ROAR, 1.2f, 0.7f);
            }
            case BOULDER -> {
                aim = lead(p, m.windup + 16, 0.4);
                SkillFx.play(this, SkillFxPayload.Kind.ZONE, c, aim, 2.8f, m.windup + 18);
                sound(SoundEvents.STONE_BREAK, 1.2f, 0.5f);
            }
            case TIDE_BOLT, THUNDERCALL, RIPTIDE -> {
                SkillFx.play(this, SkillFxPayload.Kind.CAST, c, position(), 1.6f, m.windup);
                if (m == Move.RIPTIDE) {
                    aim = lead(p, m.windup, 0.5);
                    SkillFx.play(this, SkillFxPayload.Kind.ZONE, 0x3FA0FF, aim, 3f, m.windup + 2);
                } else if (m == Move.THUNDERCALL) {
                    aim = lead(p, m.windup, 0.6);
                    SkillFx.play(this, SkillFxPayload.Kind.PILLAR, 0x9FD8FF, aim, 2.4f, m.windup);
                } else {
                    aim = p.position();
                }
                sound(SoundEvents.EVOKER_PREPARE_ATTACK, 0.8f, 1.5f);
            }
            case MIST_STEP -> {
                SkillFx.play(this, SkillFxPayload.Kind.MIST, 0xBFE4FF, position(), 3f, 60);
                sound(SoundEvents.ILLUSIONER_MIRROR_MOVE, 1f, 1.2f);
            }
            case SUN_ARROW, TRIPLE_SHOT -> {
                aim = lead(p, m.windup, 0.6);
                SkillFx.play(this, SkillFxPayload.Kind.SIGHT, c, aim.add(0, 1, 0), 1f, m.windup);
                sound(SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), 0.8f, 1.2f);
            }
            case SKYFALL -> {
                aim = lead(p, m.windup, 0.7);
                SkillFx.play(this, SkillFxPayload.Kind.ZONE, c, aim, 2.6f, m.windup + 2);
                SkillFx.play(this, SkillFxPayload.Kind.SKYSHOT, c, aim, 1.6f, m.windup);
                sound(SoundEvents.ARROW_SHOOT, 1f, 0.8f);
            }
            case BACKFLIP -> {
                Vec3 away = position().subtract(p.position()).multiply(1, 0, 1);
                away = away.lengthSqr() < 1e-3 ? getLookAngle().scale(-1).multiply(1, 0, 1) : away.normalize();
                setDeltaMovement(away.scale(1.15).add(0, 0.5, 0));
                hasImpulse = true;
                SkillFx.play(this, SkillFxPayload.Kind.DASH, c, position(), 1f, 0);
                sound(SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.6f);
            }
            case RAIN_OF_DAWN -> {
                aim = p.position();
                SkillFx.play(this, SkillFxPayload.Kind.ZONE, c, aim, 4f, m.windup + 50);
                sound(SoundEvents.BEACON_POWER_SELECT, 1f, 1.6f);
            }
            case SUNLANCE -> {
                aim = p.position();
                SkillFx.play(this, SkillFxPayload.Kind.SIGHT, c, p.position().add(0, 1, 0), 0f, m.windup);
                SkillFx.play(this, SkillFxPayload.Kind.ORB, c, position().add(0, 1.4, 0).add(getLookAngle().scale(0.8)), 0.6f, m.windup);
                sound(SoundEvents.BEACON_ACTIVATE, 1f, 1.8f);
            }
            case SHADOW_STEP -> {
                SkillFx.play(this, SkillFxPayload.Kind.VEIL, c, position(), 1.2f, m.windup);
                sound(SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, 0.8f, 1.4f);
            }
            case KNIVES -> {
                aim = lead(p, m.windup, 0.5);
                SkillFx.play(this, SkillFxPayload.Kind.SIGHT, c, aim.add(0, 1, 0), 1f, m.windup);
                sound(SoundEvents.TRIDENT_RETURN, 1f, 1.5f);
            }
            case SMOKE -> {
                SkillFx.play(this, SkillFxPayload.Kind.VEIL, c, position(), 1.6f, 50);
                veil(true);
                veilUntil = tickCount + 55;
                nextBlowVeiled = true;
                sound(SoundEvents.ILLUSIONER_CAST_SPELL, 1f, 0.8f);
            }
            case THOUSAND_CUTS -> {
                SkillFx.play(p, SkillFxPayload.Kind.MARK, c, p.position().add(0, 2.3, 0), 1.2f, m.windup + 30);
                sound(SoundEvents.WITHER_SHOOT, 0.5f, 1.8f);
            }
            case UNLEASH -> {
                SkillFx.play(this, SkillFxPayload.Kind.ROAR, c, position().add(0, 1.2, 0), 3.2f, 0);
                SkillFx.play(this, SkillFxPayload.Kind.AURA, c, position(), 1.6f, UNLEASH_TICKS + 20);
                SkillFx.play(this, SkillFxPayload.Kind.PULSE, c, position().add(0, 0.1, 0), 9f, 0);
                sound(SoundEvents.ENDER_DRAGON_GROWL, 0.9f, 1.3f);
                Duels.unleashing(p, this);
            }
        }
    }

    /** A tick of the move under way; it lands when its warning is up. */
    private void runMove(ServerLevel level, ServerPlayer p, Duel d) {
        Move m = move;
        if (m == null) return;
        int c = color();
        // a few of them keep their eyes on the hunter as they wind up
        if (m == Move.SUNLANCE && moveAge <= 25) aim = p.position();
        if (m == Move.IRON_CHARGE || m == Move.SUN_ARROW || m == Move.TRIPLE_SHOT || m == Move.KNIVES || m == Move.SUNLANCE) {
            if (aim != null) face(aim);
        } else {
            face(p.position());
        }
        switch (m) {
            case IRON_CHARGE -> {
                if (moveAge < m.windup) {
                    if (moveAge % 4 == 0) level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.1, getZ(), 3, 0.3, 0.05, 0.3, 0.02);
                    return;
                }
                if (aim == null) {
                    endMove();
                    return;
                }
                if (moveAge == m.windup) {
                    Vec3 dir = aim.subtract(position()).multiply(1, 0, 1);
                    Vec3 end = position().add(dir.normalize().scale(Math.min(18, dir.length() + 4)));
                    SkillFx.play(this, SkillFxPayload.Kind.WAVE, c, end, 2.2f, 2);
                    sound(SoundEvents.RAVAGER_STEP, 1.4f, 0.8f);
                    aim = end;
                }
                Vec3 dir = aim.subtract(position()).multiply(1, 0, 1);
                if (dir.length() < 1.2 || moveAge > m.windup + 16 || !d.inside(position(), -0.5)) {
                    setDeltaMovement(getDeltaMovement().multiply(0.2, 1, 0.2));
                    endMove();
                    return;
                }
                Vec3 v = dir.normalize().scale(unleashed() ? 1.1 : 0.95);
                setDeltaMovement(v.x, getDeltaMovement().y, v.z);
                hasImpulse = true;
                if (!struck && distanceTo(p) < 2.0 + (getAttributeValue(Attributes.SCALE) - 1)) {
                    struck = true;
                    if (hit(p, 1.6f)) {
                        p.knockback(1.6, -v.x, -v.z);
                        p.setDeltaMovement(p.getDeltaMovement().add(0, 0.35, 0));
                        push(p);
                        sound(SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, 1f, 0.6f);
                    }
                }
            }
            case QUAKE_SLAM -> {
                if (aim == null) {
                    endMove();
                    return;
                }
                if (moveAge == 6) {
                    // up, and down where the hunter stood
                    Vec3 to = aim.subtract(position());
                    double t = (m.windup - 6) / 20.0;
                    setDeltaMovement(to.x / (m.windup - 6) * 0.9, 0.95, to.z / (m.windup - 6) * 0.9);
                    hasImpulse = true;
                    sound(SoundEvents.IRON_GOLEM_DAMAGE, 1f, 0.5f);
                }
                if (moveAge >= m.windup && (onGround() || moveAge > m.windup + 12)) {
                    slam(level, position(), 4.2, 1.8f, false, 0.55, SkillFxPayload.Kind.SLAM, 4.2f);
                    endMove();
                }
            }
            case IRON_SKIN, MIST_STEP, BACKFLIP, SMOKE -> {
                if (m == Move.MIST_STEP && moveAge == m.windup) mistStep(level, p, d);
                if (m == Move.BACKFLIP && moveAge == m.windup + 4) {
                    SkillFx.play(this, SkillFxPayload.Kind.ARROW, c, p.position().add(0, 1, 0), 1f, 0);
                    if (distanceTo(p) < 6 && hasLineOfSight(p)) hit(p, 0.6f);
                }
                if (moveAge >= m.windup + 6) endMove();
            }
            case TITAN_STOMP -> {
                if (moveAge == m.windup) {
                    SkillFx.play(this, SkillFxPayload.Kind.CRACK, c, position(), 7f, 0);
                    slam(level, position(), 7, 2.2f, true, 0.4, SkillFxPayload.Kind.QUAKE, 7f);
                    endMove();
                }
            }
            case BOULDER -> {
                if (aim == null) {
                    endMove();
                    return;
                }
                if (moveAge == m.windup) {
                    swing(InteractionHand.MAIN_HAND);
                    SkillFx.play(this, SkillFxPayload.Kind.HAMMER, 0x8C8478, aim, 3.2f, 16);
                    impacts.add(new Impact(level.getGameTime() + 16, aim, 2.8, 1.6f, 1, 1, SkillFxPayload.Kind.SPLASH, 1, false, 0.3));
                    sound(SoundEvents.SNOW_GOLEM_SHOOT, 1.2f, 0.4f);
                    endMove();
                }
            }
            case TIDE_BOLT -> {
                int bolts = unleashed() ? 3 : 1;
                int first = m.windup;
                for (int i = 0; i < bolts; i++) {
                    if (moveAge == first + i * 5) {
                        Vec3 at = i == 0 && aim != null ? aim : lead(p, 4, 0.6);
                        SkillFx.play(this, SkillFxPayload.Kind.BOLT, c, at.add(0, 1, 0), 1f, 0);
                        impacts.add(new Impact(level.getGameTime() + 3, at, 1.5, 0.9f, 1, 1, SkillFxPayload.Kind.SPLASH, 0, false, 0));
                        sound(SoundEvents.TRIDENT_THROW.value(), 1f, 1.4f);
                    }
                }
                if (moveAge >= first + (bolts - 1) * 5) endMove();
            }
            case RIPTIDE -> {
                if (moveAge == m.windup && aim != null) {
                    SkillFx.play(this, SkillFxPayload.Kind.GEYSER, 0x3FA0FF, aim, 4.5f, 3);
                    blast(level, aim, 3, 1.5f, false, 1.05);
                    sound(SoundEvents.GENERIC_SPLASH, 1.4f, 0.8f);
                    endMove();
                }
            }
            case THUNDERCALL -> {
                if (moveAge == m.windup && aim != null) {
                    SkillFx.play(this, SkillFxPayload.Kind.THUNDER, 0x9FD8FF, aim, 2.4f, 0);
                    blast(level, aim, 2.4, 1.4f, false, 0.2);
                    sound(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 1.4f);
                    endMove();
                }
            }
            case SUN_ARROW -> {
                if (moveAge == m.windup && aim != null) {
                    swing(InteractionHand.MAIN_HAND);
                    SkillFx.play(this, SkillFxPayload.Kind.ARROW, c, aim.add(0, 1, 0), 1f, 0);
                    if (onLine(p, aim, 1.3)) hit(p, 0.85f);
                    sound(SoundEvents.ARROW_SHOOT, 1f, 1.3f);
                    endMove();
                }
            }
            case TRIPLE_SHOT -> {
                if (moveAge == m.windup && aim != null) {
                    swing(InteractionHand.MAIN_HAND);
                    SkillFx.play(this, SkillFxPayload.Kind.FAN, c, aim.add(0, 1, 0), 3f, 0);
                    boolean hitOne = false;
                    for (int i = -1; i <= 1 && !hitOne; i++) {
                        Vec3 dir = turn(aim.subtract(position()), i * 0.21);
                        if (onLine(p, position().add(dir), 1.0)) hitOne = hit(p, 0.7f);
                    }
                    sound(SoundEvents.CROSSBOW_SHOOT, 1f, 1.2f);
                    endMove();
                }
            }
            case SKYFALL -> {
                if (moveAge == m.windup && aim != null) {
                    SkillFx.play(this, SkillFxPayload.Kind.SPLASH, c, aim, 2.6f, 1);
                    blast(level, aim, 2.6, 1.6f, false, 0.3);
                    sound(SoundEvents.ARROW_HIT, 1.4f, 0.6f);
                    endMove();
                }
            }
            case RAIN_OF_DAWN -> {
                if (moveAge == m.windup && aim != null) {
                    SkillFx.play(this, SkillFxPayload.Kind.RAIN, c, aim, 4f, 50);
                    impacts.add(new Impact(level.getGameTime() + 6, aim, 4, 0.35f, 5, 10, SkillFxPayload.Kind.SPLASH, -1, false, 0));
                    sound(SoundEvents.ARROW_SHOOT, 1f, 0.7f);
                    endMove();
                }
            }
            case SUNLANCE -> {
                if (moveAge == m.windup && aim != null) {
                    Vec3 from = position().add(0, 1.4, 0);
                    Vec3 dir = aim.add(0, 1, 0).subtract(from);
                    Vec3 end = from.add(dir.normalize().scale(d.radius * 2.2));
                    SkillFx.play(this, SkillFxPayload.Kind.BEAM, c, end, 1.2f, 10);
                    if (onLine(p, aim, 1.25)) hit(p, 3.0f);
                    sound(SoundEvents.BEACON_DEACTIVATE, 1.4f, 1.8f);
                    endMove();
                }
            }
            case SHADOW_STEP -> {
                if (moveAge == m.windup) {
                    Vec3 back = p.getLookAngle().multiply(1, 0, 1);
                    back = back.lengthSqr() < 1e-3 ? new Vec3(0, 0, 1) : back.normalize();
                    Vec3 spot = Duels.footing(level, p.position().subtract(back.scale(1.8)));
                    if (!d.inside(spot, -0.5)) spot = Duels.footing(level, p.position().add(back.scale(1.8)));
                    SkillFx.play(this, SkillFxPayload.Kind.BLINK, c, position(), 1f, 0);
                    moveTo(spot.x, spot.y, spot.z, getYRot(), 0);
                    face(p.position());
                    sound(SoundEvents.ENDERMAN_TELEPORT, 0.8f, 1.6f);
                }
                if (moveAge == m.windup + 5) {
                    swing(InteractionHand.MAIN_HAND);
                    SkillFx.play(this, SkillFxPayload.Kind.SLASH, c, position().add(0, 1, 0), 1.4f, 0);
                    if (distanceTo(p) < 3.4) hit(p, 1.3f);
                    endMove();
                }
            }
            case KNIVES -> {
                if (moveAge == m.windup && aim != null) {
                    swing(InteractionHand.MAIN_HAND);
                    SkillFx.play(this, SkillFxPayload.Kind.FAN, c, aim.add(0, 1, 0), 5f, 0);
                    boolean hitOne = false;
                    for (int i = -2; i <= 2 && !hitOne; i++) {
                        Vec3 dir = turn(aim.subtract(position()), i * 0.22);
                        if (distanceTo(p) <= 13 && onLine(p, position().add(dir), 0.9)) hitOne = hit(p, 0.6f);
                    }
                    sound(SoundEvents.TRIDENT_THROW.value(), 1f, 1.8f);
                    endMove();
                }
            }
            case THOUSAND_CUTS -> {
                if (moveAge == m.windup) {
                    Vec3 at = p.position();
                    SkillFx.play(this, SkillFxPayload.Kind.BLINK, c, position(), 1f, 0);
                    SkillFx.play(p, SkillFxPayload.Kind.BLADES, c, at, 2.5f, 30);
                    impacts.add(new Impact(level.getGameTime() + 2, at, 2.5, 0.25f, 6, 5, SkillFxPayload.Kind.CLAW, -1, false, 0));
                    veil(true);
                    veilUntil = tickCount + 34;
                    sound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 1.8f);
                    endMove();
                }
            }
            case UNLEASH -> {
                if (moveAge == 12) transform(level);
                if (moveAge >= m.windup) {
                    endMove();
                    rest = 6;
                }
            }
        }
    }

    /** The change at half health: each their own. */
    private void transform(ServerLevel level) {
        entityData.set(UNLEASHED, true);
        int c = color();
        switch (who().style) {
            case TITAN -> {
                var scale = getAttribute(Attributes.SCALE);
                if (scale != null) scale.setBaseValue(1.65);
                SkillFx.play(this, SkillFxPayload.Kind.QUAKE, c, position(), 6f, 20);
                sound(SoundEvents.ANVIL_PLACE, 1.4f, 0.5f);
            }
            case TIDE -> {
                Duel d = duelNow();
                Vec3 at = d == null ? position() : d.center;
                int left = d == null ? 2400 : (int) Math.max(40, d.endsAt - level.getGameTime());
                SkillFx.play(this, SkillFxPayload.Kind.VORTEX, 0x3FA0FF, at, 3.2f, left);
                SkillFx.play(this, SkillFxPayload.Kind.ORBIT, 0x9FD8FF, position(), 3f, left);
                sound(SoundEvents.TRIDENT_THUNDER.value(), 1f, 1f);
            }
            case SUN -> {
                Duel d = duelNow();
                int left = d == null ? 2400 : (int) Math.max(40, d.endsAt - level.getGameTime());
                SkillFx.play(this, SkillFxPayload.Kind.WINGS, c, position(), 1.4f, left);
                SkillFx.play(this, SkillFxPayload.Kind.HALO, c, position(), 1f, left);
                sound(SoundEvents.BEACON_POWER_SELECT, 1.4f, 1.2f);
            }
            case SHADE -> {
                getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.42);
                SkillFx.play(this, SkillFxPayload.Kind.VEIL, c, position(), 2f, 30);
                veil(true);
                veilUntil = tickCount + 20;
                sound(SoundEvents.WARDEN_SONIC_CHARGE, 0.8f, 1.6f);
            }
        }
    }

    /** It's over: what their unleashed form left lasting (the maelstrom and its lights, the wings and the halo) goes out. */
    private void calm() {
        if (!unleashed() || !(level() instanceof ServerLevel)) return;
        switch (who().style) {
            case TIDE -> {
                SkillFx.play(this, SkillFxPayload.Kind.VORTEX, 0x3FA0FF, position(), 0f, 0);
                SkillFx.play(this, SkillFxPayload.Kind.ORBIT, 0x9FD8FF, position(), 0f, 0);
            }
            case SUN -> {
                SkillFx.play(this, SkillFxPayload.Kind.WINGS, color(), position(), 0f, 0);
                SkillFx.play(this, SkillFxPayload.Kind.HALO, color(), position(), 0f, 0);
            }
            default -> {
            }
        }
    }

    /** Tide, unleashed: the maelstrom in the middle of the ring drags at the hunter, and drowns them in its eye. */
    private void maelstrom(ServerLevel level, ServerPlayer p, Duel d) {
        Vec3 to = d.center.subtract(p.position()).multiply(1, 0, 1);
        double dist = to.length();
        if (dist > 2.2 && !p.isSpectator()) {
            Vec3 pull = to.normalize().scale(0.032);
            p.setDeltaMovement(p.getDeltaMovement().add(pull));
            push(p);
        } else if (dist <= 2.6 && tickCount % 20 == 0) {
            hit(p, 0.25f);
        }
    }

    /** Sun, unleashed: off the ground on wings of light. */
    private void hover(ServerLevel level) {
        if (move == Move.SUNLANCE || move == Move.UNLEASH) {
            setNoGravity(false);
            return;
        }
        setNoGravity(true);
        double ground = Duels.footing(level, position()).y;
        double want = ground + 2.1;
        Vec3 v = getDeltaMovement();
        setDeltaMovement(v.x, Mth.clamp((want - getY()) * 0.2, -0.2, 0.2), v.z);
    }

    private void mistStep(ServerLevel level, ServerPlayer p, Duel d) {
        // away from the hunter, to the far side of the ring from them
        Vec3 from = position().subtract(p.position()).multiply(1, 0, 1);
        from = from.lengthSqr() < 1e-3 ? new Vec3(1, 0, 0) : from.normalize();
        Vec3 spot = null;
        for (int i = 0; i < 8; i++) {
            Vec3 dir = turn(from, (random.nextDouble() - 0.5) * 1.6);
            Vec3 c = p.position().add(dir.scale(9 + random.nextDouble() * 2));
            if (d.inside(c, -1.5)) {
                spot = Duels.footing(level, c);
                break;
            }
        }
        if (spot == null) spot = Duels.footing(level, d.center.add(d.center.subtract(p.position()).multiply(1, 0, 1).normalize().scale(d.radius * 0.5)));
        SkillFx.play(this, SkillFxPayload.Kind.BLINK, color(), position(), 1f, 1);
        for (LivingEntity e : Duels.foes(level, p, d)) {
            if (e.distanceToSqr(this) < 3.2 * 3.2) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
        }
        moveTo(spot.x, spot.y, spot.z, getYRot(), 0);
        getNavigation().stop();
    }

    /** A blow on the ground round {@code at}: everyone of the hunter's side within {@code radius} (on their feet only, if {@code grounded}). */
    private void slam(ServerLevel level, Vec3 at, double radius, float mult, boolean grounded, double launch, SkillFxPayload.Kind fx, float size) {
        SkillFx.play(this, fx, color(), at, size, fx == SkillFxPayload.Kind.QUAKE ? 14 : 0);
        sound(SoundEvents.GENERIC_EXPLODE.value(), 0.9f, 0.6f);
        blast(level, at, radius, mult, grounded, launch);
    }

    private void blast(ServerLevel level, Vec3 at, double radius, float mult, boolean grounded, double launch) {
        ServerPlayer p = hunterPlayer();
        Duel d = duelNow();
        if (p == null || d == null) return;
        for (LivingEntity e : Duels.foes(level, p, d)) {
            double dx = e.getX() - at.x, dz = e.getZ() - at.z;
            if (dx * dx + dz * dz > radius * radius || Math.abs(e.getY() - at.y) > 3.5) continue;
            if (grounded && !e.onGround()) continue;
            if (hit(e, mult) && launch > 0) {
                e.setDeltaMovement(e.getDeltaMovement().add(0, launch, 0));
                push(e);
            }
        }
    }

    private void tickImpacts(ServerLevel level, ServerPlayer p, Duel d) {
        long now = level.getGameTime();
        List<Impact> later = new ArrayList<>();
        for (Iterator<Impact> it = impacts.iterator(); it.hasNext(); ) {
            Impact i = it.next();
            if (now < i.at()) continue;
            it.remove();
            if (i.fxLook() >= 0) SkillFx.play(this, i.fx(), color(), i.center(), (float) i.radius(), i.fxLook());
            blast(level, i.center(), i.radius(), i.mult(), i.grounded(), i.launch());
            if (i.hits() > 1) later.add(new Impact(now + i.every(), i.center(), i.radius(), i.mult(), i.hits() - 1, i.every(), i.fx(), i.fxLook(), i.grounded(), i.launch()));
        }
        impacts.addAll(later);
    }

    /** True if the hunter stands on the line from here through {@code through} (within {@code width}), in front. */
    private boolean onLine(LivingEntity e, Vec3 through, double width) {
        Vec3 from = position().multiply(1, 0, 1);
        Vec3 dir = through.multiply(1, 0, 1).subtract(from);
        if (dir.lengthSqr() < 1e-4) return e.distanceToSqr(this) < width * width;
        dir = dir.normalize();
        Vec3 to = e.position().multiply(1, 0, 1).subtract(from);
        double along = to.dot(dir);
        if (along < -0.5) return false;
        Vec3 side = to.subtract(dir.scale(along));
        return side.length() <= width + e.getBbWidth() / 2;
    }

    private static Vec3 turn(Vec3 v, double angle) {
        Vec3 flat = v.multiply(1, 0, 1);
        double cos = Math.cos(angle), sin = Math.sin(angle);
        return new Vec3(flat.x * cos - flat.z * sin, 0, flat.x * sin + flat.z * cos);
    }

    /** One of their hits on the hunter or one of theirs; true if it landed. */
    private boolean hit(LivingEntity e, float mult) {
        boolean landed = e.hurt(damageSources().mobAttack(this), power * mult);
        if (landed) setLastHurtMob(e);
        return landed;
    }

    /** A player's push only takes when their client hears of it. */
    private static void push(LivingEntity e) {
        e.hurtMarked = true;
        if (e instanceof ServerPlayer sp) sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
    }

    private void veil(boolean on) {
        if (veiled() == on) return;
        entityData.set(VEILED, on);
        if (on) addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20 * 600, 0, false, false));
        else removeEffect(MobEffects.INVISIBILITY);
        setCustomNameVisible(!on);
    }

    private void sound(SoundEvent s, float volume, float pitch) {
        level().playSound(null, getX(), getY(), getZ(), s, SoundSource.HOSTILE, volume, pitch);
    }

    // ------------------------------------------------------------------ taking blows

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (state() != FIGHTING || !Duels.mayHurtDuelist(this, source)) return false;
        if (move == Move.UNLEASH) amount *= 0.2f;
        if (tickCount < ironSkinUntil) amount *= 0.4f;
        // a veiled shade is seen once struck
        if (veiled() && !(who().style == NationalHunter.Style.SHADE && unleashed())) veil(false);
        lastHitAt = level().getGameTime();
        return super.hurt(source, amount);
    }

    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        super.actuallyHurt(source, amount);
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || state() != FIGHTING) return;
        if (getHealth() < 1f) {
            // they never fall: at the end of their strength, they yield
            setHealth(1f);
            ServerPlayer p = hunterPlayer();
            if (p != null) Duels.challengerYields(p, this);
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return false;
    }

    @Override
    public boolean isInvisibleTo(Player player) {
        // the hunter sees a veiled shade as a shimmer, not as nothing at all
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return state() == FIGHTING;
    }

    @Override
    public boolean canBeLeashed() {
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
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    /** For tests and /solo duel move|unleash: the move begins now. */
    void beginForTest(Move m) {
        ServerPlayer p = hunterPlayer();
        begin(m, p == null ? position() : p.position());
    }

    /** For tests: game time of the last blow they took. */
    long lastHitAt() {
        return lastHitAt;
    }
}
