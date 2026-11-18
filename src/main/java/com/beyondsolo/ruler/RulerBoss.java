package com.beyondsolo.ruler;

import com.beyondsolo.monarch.MonarchAPI;
import com.beyondsolo.monarch.MonarchDomain;
import com.beyondsolo.network.WorldBossPayload;
import com.beyondsolo.util.OnlinePlayers;
import com.beyondsolo.worldboss.ColossalBoss;
import com.beyondsolo.worldboss.MonarchBoss;
import com.beyondsolo.worldboss.WorldBossData;
import com.beyondsolo.worldboss.WorldBossFx;
import com.beyondsolo.worldboss.WorldBossKind;
import com.beyondsolo.worldboss.WorldBosses;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A ruler of light ({@link WorldBossKind.Tier#RULER}): the monarchs' enemy since before the gates, as strong as one of
 * them and as beatable (no immunity, no second life, no healing). What makes it a ruler:
 * <ul>
 *   <li><b>Its descent</b>: it comes down through a rift of light torn in the sky ({@link Rulers}), lands, and lays down
 *   its sanctum.</li>
 *   <li><b>Its sanctum</b>: a circle of its light ({@link #SANCTUM} blocks). Its foes inside are laid bare (they glow),
 *   the shadows and soldiers they bring burn in it, and each ruler's own judgement falls on them; a monarch's domain and
 *   presence don't reach whoever else stands in it ({@link Rulers#shelters}).</li>
 *   <li><b>Its foes, and only its foes</b>: monarchs and their armies, hunters who took a crown, the hunter it came to
 *   judge, and anyone who raised a hand against it or its heralds (and what they own). Every other hunter it leaves be:
 *   its blows pass through them. Siding with it, or against it, is the hunter's choice.</li>
 *   <li><b>Its host</b>: heralds of light, called round it as it lands and as it angers ({@link RulerHost}).</li>
 *   <li><b>Its authority</b>: in its last phase, named on every hunter's screen and given time to be escaped.</li>
 * </ul>
 */
public abstract class RulerBoss extends ColossalBoss {
    /** The radius of a ruler's sanctum. */
    public static final int SANCTUM = 26;
    /** What the shadows of its foes lose in its sanctum each second (a share of their health). */
    static final float SHADOW_BURN = 0.02f;
    /** What a monarch's soldiers lose in its sanctum each second. */
    static final float ARMY_BURN = 0.01f;

    private boolean landed;
    private boolean pendingAuthority;
    /** Hunters who struck it or its heralds: its foes from then on. */
    private final Set<UUID> aggressors = new HashSet<>();
    /** When it last stayed each hunter's blow with a warning (game time): a second blow soon after is a choice. */
    private final Map<UUID, Long> warned = new HashMap<>();
    /** Why it came ({@link Rulers#WAR}, {@link Rulers#SENTENCE}, ...) and whom for. */
    private int reason;
    @Nullable private UUID quarry;
    /** Ticks until it rises back into the sky once its work is done (-1: not yet). */
    private int farewell = -1;

    protected RulerBoss(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes()
                // each ruler's own numbers are set from its kind as it is made (setupStats)
                .add(Attributes.MAX_HEALTH, WorldBossKind.VERDICT.health())
                .add(Attributes.ATTACK_DAMAGE, WorldBossKind.VERDICT.damage())
                .add(Attributes.ARMOR, 16)
                .add(Attributes.ARMOR_TOUGHNESS, 8)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 64)
                .add(Attributes.STEP_HEIGHT, 1.6)
                .add(Attributes.SAFE_FALL_DISTANCE, 64);
    }

    /** Its judgement on the foes inside its sanctum, once a second ({@code second} counts up). */
    protected abstract void sanctumTick(ServerLevel level, List<LivingEntity> foes, int second);

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

    @Override
    public void prepareEntrance(ServerLevel level, Vec3 at) {
        entrance(at.add(0, MonarchBoss.GATE_HEIGHT, 0), at, introLength() - 30);
    }

    @Override
    protected boolean earthbound() {
        return false;
    }

    @Override
    protected Vec3 leaveTarget() {
        return position().add(0, MonarchBoss.GATE_HEIGHT, 0);
    }

    @Override
    protected int leaveLength() {
        return 90;
    }

    public boolean landed() {
        return landed;
    }

    // ================================================================ why it came

    public int reason() {
        return reason;
    }

    @Nullable
    public UUID quarry() {
        return quarry;
    }

    /** Sets why it came and whom for (before it lands). */
    public void setQuarry(int reason, @Nullable UUID quarry) {
        this.reason = reason;
        this.quarry = quarry;
    }

    /** What it came for, if it is still in the world: the monarch it makes war on, the hunter it judges. */
    @Nullable
    public LivingEntity quarryEntity() {
        if (quarry == null || !(level() instanceof ServerLevel server)) return null;
        if (reason == Rulers.SENTENCE) {
            ServerPlayer p = OnlinePlayers.get(server.getServer(), quarry);
            return p != null && p.level() == level() && p.isAlive() ? p : null;
        }
        Entity e = server.getEntity(quarry);
        return e instanceof LivingEntity l && l.isAlive() ? l : null;
    }

    /** A hunter struck it or its heralds: they are its foe from now on. */
    public void aggressor(UUID who) {
        aggressors.add(who);
    }

    public boolean isAggressor(UUID who) {
        return aggressors.contains(who);
    }

    /** When it last stayed this hunter's blow with a warning (game time), or -1. */
    long warnedAt(UUID who) {
        Long t = warned.get(who);
        return t == null ? -1 : t;
    }

    void warn(UUID who, long now) {
        warned.put(who, now);
    }

    void unwarn(UUID who) {
        warned.remove(who);
    }

    /** What it came for is done (the monarch it fought has fallen): it rises back into the sky a little after. */
    public void victory() {
        if (farewell < 0) farewell = 100;
        quarry = null;
    }

    // ================================================================ friend and foe

    /**
     * Its foes: monarchs and their soldiers, hunters who took a crown, the hunter it came to judge, whoever struck it
     * or its heralds, and what any of them own (shadows, pets). Never its own kind.
     */
    @Override
    protected boolean foe(LivingEntity e) {
        if (e == this || !e.isAlive() || Rulers.light(e)) return false;
        if (e instanceof MonarchBoss) return true;
        if (e.getPersistentData().hasUUID(WorldBosses.ARMY_TAG)) return true;
        if (e instanceof Player p) return hunterFoe(p.getUUID(), p);
        if (e instanceof OwnableEntity o && o.getOwnerUUID() != null) {
            Player owner = level() instanceof ServerLevel server ? OnlinePlayers.get(server.getServer(), o.getOwnerUUID())
                    : level().getPlayerByUUID(o.getOwnerUUID());
            return hunterFoe(o.getOwnerUUID(), owner);
        }
        return e == getTarget();
    }

    /** Whether {@code e} is one of its foes (for its heralds, and for the hooks that keep its friends out of its way). */
    public boolean isFoe(LivingEntity e) {
        return foe(e);
    }

    /** Whether it stands against this hunter (or their shadows and pets): crowned, judged, or one who struck it. */
    public boolean holdsAgainst(UUID id, @Nullable Player p) {
        return hunterFoe(id, p);
    }

    private boolean hunterFoe(UUID id, @Nullable Player p) {
        if (aggressors.contains(id)) return true;
        if (reason == Rulers.SENTENCE && id.equals(quarry)) return true;
        return p != null && MonarchAPI.crowned(p);
    }

    /** Its blows land only on its foes: every other hunter they pass through. */
    @Override
    public boolean canHit(LivingEntity e) {
        if (e == this || !e.isAlive() || e instanceof ArmorStand) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        return foe(e);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // it doesn't hunt hunters: what it came for first, then whoever strikes it, then the nearest of its foes
        targetSelector.removeAllGoals(g -> true);
        targetSelector.addGoal(1, new QuarryGoal());
        targetSelector.addGoal(2, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                LivingEntity by = getLastHurtByMob();
                return by != null && foe(by) && super.canUse();
            }
        });
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, false, false, this::foe));
    }

    /** What it came for comes first, while it is in reach. */
    private final class QuarryGoal extends Goal {
        QuarryGoal() {
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            LivingEntity q = quarryEntity();
            return q != null && getTarget() != q && canHit(q) && distanceToSqr(q) < 72 * 72;
        }

        @Override
        public void start() {
            setTarget(quarryEntity());
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity q = quarryEntity();
            return q != null && getTarget() == q && canHit(q) && distanceToSqr(q) < 80 * 80;
        }
    }

    /** Its light on whoever its blows land on: laid bare (they glow), and a crowned hunter weighed down by it. */
    @Override
    protected void afflict(LivingEntity e, float scale) {
        e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false), this);
        if (e instanceof Player p && MonarchAPI.crowned(p)) p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0), this);
    }

    // ================================================================ coming down

    @Override
    protected void introTick(ServerLevel level, int t) {
        int land = introLength() - 30;
        if (t < land && t % 3 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + getBbHeight() / 2, getZ(), 6, 0.6, 1.2, 0.6, 0.04);
        }
        if (t == land - 1) touchDown(level);
    }

    /** It lands: its light breaks over the ground, its sanctum unfolds, and its host comes down round it. */
    protected void touchDown(ServerLevel level) {
        landed = true;
        Vec3 at = ground(position());
        WorldBossFx.quake(level, at, 11f, fxColor());
        WorldBossFx.burst(level, at.add(0, 1, 0), 6f, fxColor());
        WorldBossFx.ring(level, at, 0.5f, 9f, 12, fxColor());
        WorldBossFx.pillar(level, at, 2.6f, 48f, fxColor());
        dust(at, 5f, 50);
        shake(1.1f, 96);
        soundAt(at, SoundEvents.BEACON_ACTIVATE, 4f, 0.5f);
        soundAt(at, SoundEvents.BELL_BLOCK, 4f, 0.5f);
        for (LivingEntity e : near(at, 7)) hit(e, 0.6f, e.position().subtract(at), 1.4, 0.6);
        for (ServerPlayer p : watchers(WorldBossFx.RANGE)) {
            PacketDistributor.sendToPlayer(p, new WorldBossPayload(WorldBossPayload.DOMAIN, kind().ordinal(), eventKey(level), getId(),
                    blockPosition(), phase(), SANCTUM, 0));
        }
        callHost(level, 4);
        WorldBossData.Event ev = WorldBosses.eventOf(level, this);
        if (ev != null) {
            for (ServerPlayer p : level.players()) {
                PacketDistributor.sendToPlayer(p, new WorldBossPayload(WorldBossPayload.APPEAR, kind().ordinal(), WorldBosses.eventKey(ev), getId(),
                        blockPosition(), 1, 0, 0));
            }
        }
        Rulers.landed(level, this);
    }

    protected long eventKey(ServerLevel level) {
        WorldBossData.Event ev = WorldBosses.eventOf(level, this);
        return ev != null ? WorldBosses.eventKey(ev) : 0;
    }

    /** Calls {@code count} heralds round it. */
    protected void callHost(ServerLevel level, int count) {
        RulerHost.call(level, this, position(), count, getTarget());
    }

    @Override
    protected void onPhase(ServerLevel level, int phase) {
        if (phase == 2) callHost(level, 3);
        if (phase == 3 && moveNamed(authority()) != null) pendingAuthority = true;
    }

    // ================================================================ ticking

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel server) || !isAlive() || leaving()) return;
        Rulers.track(this);
        if (pendingAuthority && !shifting() && move == null && introTicks() <= 0) {
            pendingAuthority = false;
            Move m = moveNamed(authority());
            if (m != null) start(m, getTarget());
        }
        if (landed || introTicks() <= 0) {
            landed = true;
            if (tickCount % 20 == 0) sanctum(server);
            if (tickCount % 5 == 0) {
                double a = random.nextDouble() * Math.PI * 2;
                server.sendParticles(ParticleTypes.END_ROD, getX() + Math.cos(a) * SANCTUM, getY() + 0.4, getZ() + Math.sin(a) * SANCTUM, 1, 0, 0.3, 0, 0.01);
            }
        }
        if (farewell > 0 && --farewell == 0) {
            // its work is done; if no foe stands against it, it goes
            if (fighters(40).isEmpty()) leave();
            else farewell = -1;
        }
    }

    /** Once a second: its foes inside are laid bare, their shadows and soldiers burn, and its own judgement falls. */
    private void sanctum(ServerLevel server) {
        List<LivingEntity> foes = new java.util.ArrayList<>(near(position(), SANCTUM));
        // a crowned hunter's domain holds the sanctum back from whoever it shelters
        foes.removeIf(MonarchDomain::shelters);
        for (LivingEntity e : foes) {
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false), this);
            float burn = e instanceof OwnableEntity && !(e instanceof Player) ? SHADOW_BURN
                    : e.getPersistentData().hasUUID(WorldBosses.ARMY_TAG) ? ARMY_BURN : 0f;
            if (burn > 0) {
                e.hurt(damageSources().indirectMagic(this, this), e.getMaxHealth() * burn);
                server.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 2, 0.2, 0.3, 0.2, 0.02);
            }
        }
        if (!foes.isEmpty()) sanctumTick(server, foes, tickCount / 20);
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
        return 3.0;
    }

    /** Holds it at a point in the air (or on the ground) for a tick of a move: no physics, no fall. */
    protected void hold(Vec3 at) {
        noPhysics = true;
        setPos(at.x, at.y, at.z);
        setDeltaMovement(Vec3.ZERO);
    }

    /** Moves it to a point at once. */
    protected void blinkTo(Vec3 at) {
        teleportTo(at.x, at.y, at.z);
        getNavigation().stop();
    }

    @Override
    protected void deathTick(ServerLevel level, int t) {
        if (t == 1) {
            sound(SoundEvents.BEACON_DEACTIVATE, 4f, 0.5f);
            sound(SoundEvents.BELL_RESONATE, 3f, 0.6f);
            shake(1.0f, 80);
        }
        if (t % 4 == 0) level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + getBbHeight() / 2, getZ(), 10, 0.5, 1, 0.5, 0.08);
    }

    @Override
    protected void leaveTick(ServerLevel level, int t) {
        if (t % 3 == 0) level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + getBbHeight() / 2, getZ(), 8, 0.5, 1, 0.5, 0.06);
    }

    /** The nearest of its foes in reach, or null. */
    @Nullable
    protected LivingEntity nearestFoe() {
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

    /** Its foes in reach: hunters it stands against, and any monarch, soldier or shadow within {@code radius}. */
    @Override
    public List<LivingEntity> fighters(double radius) {
        List<LivingEntity> out = super.fighters(radius);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius, 24, radius),
                e -> !(e instanceof Player) && canHit(e))) {
            if (!out.contains(e)) out.add(e);
        }
        return out;
    }

    // ================================================================ save

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Landed", landed);
        tag.putInt("Reason", reason);
        if (quarry != null) tag.putUUID("Quarry", quarry);
        ListTag list = new ListTag();
        for (UUID id : aggressors) list.add(NbtUtils.createUUID(id));
        tag.put("Aggressors", list);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        landed = tag.getBoolean("Landed");
        reason = tag.getInt("Reason");
        quarry = tag.hasUUID("Quarry") ? tag.getUUID("Quarry") : null;
        aggressors.clear();
        for (Tag t : tag.getList("Aggressors", Tag.TAG_INT_ARRAY)) aggressors.add(NbtUtils.loadUUID(t));
    }
}
