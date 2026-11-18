package com.beyondsolo.worldboss;

import com.beyondsolo.enemy.Habitat;
import com.beyondsolo.network.WorldBossPayload;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A monarch: a ruler from beyond the gates, stronger than anything else that walks the world, and still something that
 * can be beaten: no immunity, no second life, no healing. What makes it a monarch:
 * <ul>
 *   <li><b>Its descent</b>: it comes down out of a gate torn open in the sky ({@link WorldBosses#begin}), lands with a
 *   blow that shakes the land, and lays down its domain.</li>
 *   <li><b>Its domain</b>: a circle of its own rule round it ({@link #DOMAIN} blocks) where the world bends to it (each
 *   monarch's own: cold that freezes, a hunger that bleeds, a storm that strikes).</li>
 *   <li><b>Its presence</b>: hunters below level {@link #PRESSURE_LEVEL} near it are weighed down (slower, weaker);
 *   a crowned hunter never is, and a crowned hunter's domain shelters whoever stands in it from this one's
 *   ({@link com.beyondsolo.monarch.MonarchDomain#shelters}).</li>
 *   <li><b>Its army</b>: S-rank creatures of its affinity, called up round it as it lands and again as it angers; they
 *   crumble when it falls.</li>
 *   <li><b>Its authority</b>: in its last phase, its greatest power, named on every hunter's screen and given time to
 *   be escaped.</li>
 * </ul>
 * Monarchs walk (they are not giants): paths, leaps, blinks.
 */
public abstract class MonarchBoss extends ColossalBoss {
    /** The radius of a monarch's domain. */
    public static final int DOMAIN = 28;
    /** Hunters below this level near a monarch feel its pressure. */
    /** Below an S-rank gate's home level on the power curve, a monarch's presence weighs a hunter down. */
    public static final int PRESSURE_LEVEL = com.beyondsolo.system.Balance.homeLevel(com.beyondsolo.system.PlayerRank.S);
    /** How far its presence weighs on the weak. */
    public static final double PRESSURE_RANGE = 18;
    /** How high above its landing its gate opens. */
    public static final int GATE_HEIGHT = 46;

    private boolean landed;

    protected MonarchBoss(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes()
                // each monarch's own numbers are set from its kind as it is made (setupStats)
                .add(Attributes.MAX_HEALTH, WorldBossKind.HUNGER.health())
                .add(Attributes.ATTACK_DAMAGE, WorldBossKind.HUNGER.damage())
                .add(Attributes.ARMOR, 16)
                .add(Attributes.ARMOR_TOUGHNESS, 8)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 64)
                .add(Attributes.STEP_HEIGHT, 1.6)
                .add(Attributes.SAFE_FALL_DISTANCE, 64);
    }

    /** Its army's creatures: where they come from. */
    protected abstract List<Habitat> armyHabitats();

    /** Its domain's hold on one hunter inside it, once a second. */
    protected abstract void domainTick(ServerLevel level, LivingEntity e);

    /** Its authority: the move it uses in its last phase. */
    protected abstract String authority();

    @Override
    protected int introLength() {
        return 110;
    }

    @Override
    protected int shiftLength() {
        return 46;
    }

    @Override
    protected int deathLength() {
        return 100;
    }

    /** It comes down out of its gate: from high above its landing, over most of its entrance. */
    @Override
    public void prepareEntrance(ServerLevel level, Vec3 at) {
        entrance(at.add(0, GATE_HEIGHT, 0), at, introLength() - 30);
    }

    /** It travels through the air, down from its gate and back up into the sky. */
    @Override
    protected boolean earthbound() {
        return false;
    }

    /** It leaves back up into the sky. */
    @Override
    protected Vec3 leaveTarget() {
        return position().add(0, GATE_HEIGHT, 0);
    }

    @Override
    protected int leaveLength() {
        return 90;
    }

    public boolean landed() {
        return landed;
    }

    @Override
    protected void introTick(ServerLevel level, int t) {
        int land = introLength() - 30;
        if (t < land && t % 3 == 0) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + getBbHeight() / 2, getZ(), 8, 0.6, 1.2, 0.6, 0.05);
        }
        if (t == land - 1) touchDown(level);
    }

    /** It lands: the blow, its domain, its army. */
    protected void touchDown(ServerLevel level) {
        landed = true;
        Vec3 at = ground(position());
        WorldBossFx.quake(level, at, 14f, fxColor());
        WorldBossFx.burst(level, at.add(0, 1, 0), 6f, fxColor());
        WorldBossFx.ring(level, at, 0.5f, 9f, 12, fxColor());
        dust(at, 6f, 80);
        shake(1.4f, 96);
        soundAt(at, SoundEvents.GENERIC_EXPLODE.value(), 4f, 0.5f);
        soundAt(at, SoundEvents.WITHER_SPAWN, 4f, 0.8f);
        for (LivingEntity e : near(at, 7)) hit(e, 0.6f, e.position().subtract(at), 1.4, 0.6);
        // the domain unfolds, and the army answers
        for (ServerPlayer p : watchers(WorldBossFx.RANGE)) {
            PacketDistributor.sendToPlayer(p, new WorldBossPayload(WorldBossPayload.DOMAIN, kind().ordinal(), eventKey(level), getId(),
                    blockPosition(), phase(), DOMAIN, 0));
        }
        callArmy(level, 4);
        // the banner, now it is here
        WorldBossData.Event ev = WorldBosses.eventOf(level, this);
        if (ev != null) {
            for (ServerPlayer p : level.players()) {
                PacketDistributor.sendToPlayer(p, new WorldBossPayload(WorldBossPayload.APPEAR, kind().ordinal(), WorldBosses.eventKey(ev), getId(),
                        blockPosition(), 1, 0, 0));
            }
        }
    }

    protected long eventKey(ServerLevel level) {
        WorldBossData.Event ev = WorldBosses.eventOf(level, this);
        return ev != null ? WorldBosses.eventKey(ev) : 0;
    }

    /** Calls {@code count} of its soldiers round it. */
    protected void callArmy(ServerLevel level, int count) {
        MonarchArmy.call(level, this, position(), count, kind().affinity, armyHabitats(), getTarget());
    }

    @Override
    protected void onPhase(ServerLevel level, int phase) {
        if (phase == 2) callArmy(level, 3);
        if (phase == 3) {
            // its authority comes first thing in its last phase
            Move m = moveNamed(authority());
            if (m != null) pendingAuthority = true;
        }
    }

    private boolean pendingAuthority;

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel server) || !isAlive() || leaving()) return;
        if (pendingAuthority && !shifting() && move == null && introTicks() <= 0) {
            pendingAuthority = false;
            Move m = moveNamed(authority());
            if (m != null) start(m, getTarget());
        }
        if (landed || introTicks() <= 0) {
            landed = true;
            if (tickCount % 20 == 0) {
                for (LivingEntity e : near(position(), DOMAIN)) {
                    // a hunter monarch's domain holds this one back from whoever it shelters
                    if (!com.beyondsolo.monarch.MonarchDomain.shelters(e)) domainTick(server, e);
                }
                for (Player p : server.getEntitiesOfClass(Player.class, getBoundingBox().inflate(PRESSURE_RANGE), this::canHit)) {
                    if (SystemAPI.get(p).level() >= PRESSURE_LEVEL || com.beyondsolo.monarch.MonarchAPI.crowned(p)) continue;
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, true, false), this);
                    p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, true, false), this);
                }
            }
        }
    }

    /** Tells every hunter near that its authority is coming, and when it lands. */
    protected void announceAuthority(ServerLevel level, int ticks) {
        for (ServerPlayer p : watchers(WorldBossFx.RANGE)) {
            PacketDistributor.sendToPlayer(p, new WorldBossPayload(WorldBossPayload.AUTHORITY, kind().ordinal(), eventKey(level), getId(),
                    blockPosition(), phase(), ticks, 0));
        }
        shake(0.8f, 72);
    }

    /** Between moves: it closes in on foot, and keeps a little apart from its prey. */
    @Override
    protected void approach(LivingEntity target, double dist) {
        if (dist > preferred()) getNavigation().moveTo(target, dist > 12 ? 1.35 : 1.0);
        else getNavigation().stop();
    }

    /** The distance it likes to fight from (from its edge). */
    protected double preferred() {
        return 2.5;
    }

    /** Moves it to a point at once (a blink, a leap's landing). */
    protected void blinkTo(Vec3 at) {
        teleportTo(at.x, at.y, at.z);
        getNavigation().stop();
    }

    @Override
    protected void deathTick(ServerLevel level, int t) {
        if (t == 1) {
            sound(SoundEvents.WITHER_DEATH, 4f, 0.6f);
            shake(1.0f, 80);
        }
        if (t % 5 == 0) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + getBbHeight() / 2, getZ(), 12, 0.5, 1, 0.5, 0.1);
        }
    }

    @Override
    protected void leaveTick(ServerLevel level, int t) {
        if (t % 3 == 0) level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + getBbHeight() / 2, getZ(), 10, 0.5, 1, 0.5, 0.08);
    }

    @Nullable
    protected LivingEntity nearestFighter() {
        LivingEntity best = null;
        double d = Double.MAX_VALUE;
        for (LivingEntity e : fighters(40)) {
            double x = distanceToSqr(e);
            if (x < d) {
                d = x;
                best = e;
            }
        }
        return best;
    }
}
