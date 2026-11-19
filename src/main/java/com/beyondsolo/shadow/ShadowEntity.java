package com.beyondsolo.shadow;

import com.beyondsolo.network.SkillFxPayload;
import com.beyondsolo.skill.SkillCombat;
import com.beyondsolo.skill.SkillElement;
import com.beyondsolo.skill.SkillFx;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

/**
 * A shadow out in the world: a creature its necromancer extracted, wearing the shape of what it was, black as ink
 * with burning eyes. It follows its master, fights whatever hurts them or whatever they hurt, and attacks monsters
 * near them. Never saved with the world: it lives only while its master is there.
 *
 * <p>It rises out of the ground when called ({@link #RISE_TICKS}) and sinks back into it when dismissed
 * ({@link #SINK_TICKS}). Mounts carry their master. Boss shadows hit the ground around them every few seconds and
 * make the shadows near them hit harder.</p>
 *
 * <p>It takes the shape of whatever creature it was, from any mod ({@link ShadowForm}, synced to the client whole):
 * that creature's size (a giant's shadow rises smaller, {@link ShadowForm#fit}), its look ({@link ShadowLook}), and how
 * it got about. A shadow of a flier flies, hovering over its master's head; one of a swimmer swims through water and
 * air alike, low over the ground; neither falls, drowns or chokes in a wall.</p>
 */
public class ShadowEntity extends PathfinderMob implements PlayerRideableJumping, OwnableEntity {
    public static final int RISE_TICKS = 30;
    public static final int MOUNT_RISE_TICKS = 16;
    public static final int SINK_TICKS = 16;
    public static final int DEATH_TICKS = 20;

    private static final EntityDataAccessor<String> SOURCE = SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> RANK = SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LEVEL = SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ROLE = SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BOSS_RANK = SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.INT);
    /** 0, or how many ticks it has been sinking. */
    private static final EntityDataAccessor<Integer> SINK = SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.INT);
    /** The shape of the creature it was ({@link ShadowForm#save}, look and all), or empty (the kind's own size). */
    private static final EntityDataAccessor<CompoundTag> FORM = SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private static final net.minecraft.core.particles.DustParticleOptions WISP =
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.32f, 0.2f, 0.55f), 0.9f);

    private @Nullable UUID recordId;
    /** Where it stands guard, if it was left to (server side; the record keeps it too). */
    private @Nullable Vec3 post;
    private int skillCooldown = 80;
    private int boltCooldown = 20;
    private float jumpPending;
    /** Flying or swimming (server side): no gravity, flies to where it goes. */
    private boolean airborne;
    /** Where an airborne shadow is flying straight to (its place over its master or its post), or null. */
    private @Nullable Vec3 station;
    private double stationSpeed = 1;
    /** The form read from {@link #FORM} (both sides), and the tag it was read from. */
    private @Nullable ShadowForm form;
    private @Nullable CompoundTag formTag;

    /** Client only: the creature it looks like, posed like it each tick, and what it was made from. */
    private @Nullable ShadowLook look;
    private String lookSource = "";
    private @Nullable CompoundTag lookForm;
    /** Client only: shown in a screen, not in the world (no rising out of the ground). */
    public boolean preview;

    public ShadowEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        xpReward = 0;
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.ATTACK_DAMAGE, 3)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.ARMOR, 0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0)
                .add(Attributes.JUMP_STRENGTH, 0.6)
                .add(Attributes.STEP_HEIGHT, 0.6)
                .add(Attributes.FLYING_SPEED, 0.13);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(SOURCE, "minecraft:zombie");
        b.define(OWNER, Optional.empty());
        b.define(RANK, 0);
        b.define(LEVEL, 1);
        b.define(ROLE, 0);
        b.define(BOSS_RANK, -1);
        b.define(SINK, 0);
        b.define(FORM, new CompoundTag());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this) {
            @Override
            public boolean canUse() {
                return !airborne && super.canUse();
            }
        });
        goalSelector.addGoal(1, new BossSkillGoal());
        goalSelector.addGoal(2, new CasterGoal());
        goalSelector.addGoal(3, new MeleeGoal());
        goalSelector.addGoal(5, new FollowMasterGoal());
        goalSelector.addGoal(5, new GuardPostGoal());
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new MasterTargetGoal());
    }

    // ------------------------------------------------------------------ setup

    /** Gives this shadow the shape and strength of a record, for its master. */
    public void configure(ShadowRecord r, ServerPlayer owner) {
        recordId = r.id();
        entityData.set(SOURCE, r.type().toString());
        entityData.set(OWNER, Optional.of(owner.getUUID()));
        entityData.set(RANK, r.rank().ordinal());
        entityData.set(LEVEL, r.level());
        entityData.set(ROLE, r.role().ordinal());
        entityData.set(BOSS_RANK, r.boss() ? r.bossRank() : -1);
        ShadowForm f = r.form();
        entityData.set(FORM, f == null ? new CompoundTag() : f.save(true));
        applyMovement(r.isMount() ? ShadowForm.Movement.WALK : r.movement());
        applyStats(r, com.beyondsolo.system.SystemAPI.get(owner));
        setHealth(getMaxHealth());
        nameFrom(r);
        refreshDimensions();
    }

    /** Takes to the air (a flier or a swimmer) or to its feet: how it moves, and how it finds its way. */
    private void applyMovement(ShadowForm.Movement m) {
        boolean air = m.airborne();
        if (air == airborne) return;
        airborne = air;
        setNoGravity(air);
        if (air) {
            moveControl = new FlyingMoveControl(this, 20, true);
            FlyingPathNavigation nav = new FlyingPathNavigation(this, level());
            nav.setCanOpenDoors(false);
            nav.setCanFloat(true);
            nav.setCanPassDoors(true);
            navigation = nav;
        } else {
            moveControl = new MoveControl(this);
            navigation = createNavigation(level());
        }
    }

    /** A named shadow shows its name over its head; one its master named shows it in its rank's colour. */
    private void nameFrom(ShadowRecord r) {
        if (!r.named()) {
            setCustomName(null);
            setCustomNameVisible(false);
            return;
        }
        net.minecraft.network.chat.MutableComponent name = r.displayName().copy();
        if (r.bonded()) name = name.withStyle(s -> s.withColor(r.rank().color()));
        setCustomName(name);
        setCustomNameVisible(true);
    }

    /** Rank, level or name changed: new numbers, same share of health. */
    public void refresh(ShadowRecord r, ServerPlayer owner) {
        float share = getHealth() / Math.max(1, getMaxHealth());
        entityData.set(RANK, r.rank().ordinal());
        entityData.set(LEVEL, r.level());
        applyStats(r, com.beyondsolo.system.SystemAPI.get(owner));
        setHealth(Math.max(1, getMaxHealth() * share));
        nameFrom(r);
        refreshDimensions();
    }

    private void applyStats(ShadowRecord r, com.beyondsolo.system.SystemData master) {
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(ShadowService.maxHealth(r, master));
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(ShadowService.damage(r, master));
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(ShadowService.speed(r));
        // in the air it keeps the pace it would have on its feet
        getAttribute(Attributes.FLYING_SPEED).setBaseValue(ShadowService.speed(r) * 0.45);
        getAttribute(Attributes.ARMOR).setBaseValue(2 * r.rank().ordinal() + (r.boss() ? 4 : 0));
        getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(r.boss() ? 0.8 : 0.1 * r.rank().ordinal());
        getAttribute(Attributes.STEP_HEIGHT).setBaseValue(r.isMount() ? 1.1 : 0.6);
        getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(r.isMount() ? 0.9 : 0.42);
    }

    // ------------------------------------------------------------------ state

    public @Nullable UUID recordId() { return recordId; }
    /** The spot it guards, or null while it follows its master. */
    public @Nullable Vec3 post() { return post; }
    public boolean posted() { return post != null; }
    /** Leaves it to guard a spot (null: back to following its master). */
    public void setPost(@Nullable Vec3 post) {
        this.post = post;
        if (post == null) getNavigation().stop();
    }
    public String source() { return entityData.get(SOURCE); }
    public ShadowRank rank() { return ShadowRank.byOrdinal(entityData.get(RANK)); }
    public int shadowLevel() { return entityData.get(LEVEL); }
    public ShadowRecord.Role role() { return ShadowRecord.Role.byOrdinal(entityData.get(ROLE)); }
    public boolean isMount() { return role() == ShadowRecord.Role.MOUNT; }
    public boolean isBoss() { return entityData.get(BOSS_RANK) >= 0; }
    public int bossRank() { return entityData.get(BOSS_RANK); }
    public int sinkTicks() { return entityData.get(SINK); }
    public boolean isSinking() { return sinkTicks() > 0; }

    /** The shape of the creature it was, or null (the kind's own size, on its feet). */
    public @Nullable ShadowForm form() {
        if (entityData == null) return null;
        CompoundTag t = entityData.get(FORM);
        if (t != formTag) {
            formTag = t;
            form = t.isEmpty() ? null : ShadowForm.load(t);
        }
        return form;
    }

    /** How it gets about: walks, flies or swims (a steed always walks). */
    public ShadowForm.Movement movement() {
        ShadowForm f = form();
        return f == null || isMount() ? ShadowForm.Movement.WALK : f.move();
    }

    /** In the air (server side): a flier's or a swimmer's shadow. */
    public boolean airborne() { return airborne; }

    public int riseTicks() {
        return isMount() ? MOUNT_RISE_TICKS : RISE_TICKS;
    }

    public boolean isRising() {
        return !preview && tickCount < riseTicks();
    }

    /** Rising, sinking, fallen or carrying someone: not fighting on its own. */
    public boolean isBusy() {
        return isRising() || isSinking() || isDeadOrDying() || getControllingPassenger() != null;
    }

    @Override
    public @Nullable UUID getOwnerUUID() {
        return entityData.get(OWNER).orElse(null);
    }

    /** Client-side previews set their look directly. */
    public void setLook(String source, ShadowRank rank, int bossRank, ShadowRecord.Role role) {
        setLook(source, rank, bossRank, role, null);
    }

    /** Client-side previews set their look directly, in the creature's own shape ({@code form} null: the kind's). */
    public void setLook(String source, ShadowRank rank, int bossRank, ShadowRecord.Role role, @Nullable ShadowForm form) {
        entityData.set(SOURCE, source);
        entityData.set(RANK, rank.ordinal());
        entityData.set(BOSS_RANK, bossRank);
        entityData.set(ROLE, role.ordinal());
        // (an equal form leaves the one it has: no new stand-in each frame)
        entityData.set(FORM, form == null ? new CompoundTag() : form.save(true));
        refreshDimensions();
    }

    public @Nullable ServerPlayer master() {
        UUID id = getOwnerUUID();
        if (id == null || !(level() instanceof ServerLevel server)) return null;
        return ShadowService.masterOf(server, id);
    }

    /** Goes back into its master's shadow. */
    public void startSinking() {
        if (isSinking() || level().isClientSide) return;
        ejectPassengers();
        entityData.set(SINK, 1);
        getNavigation().stop();
        setTarget(null);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SOURCE.equals(key) || RANK.equals(key) || BOSS_RANK.equals(key) || FORM.equals(key)) refreshDimensions();
    }

    /**
     * It has the size of the creature it was (its size roll too), a little bigger for each rank; anything larger than a
     * shadow stands rises smaller, in proportion ({@link ShadowForm#fit}).
     */
    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        ShadowForm f = shape();
        return f == null ? super.getDefaultDimensions(pose) : f.dimensions().scale(sizeScale());
    }

    /** Its form, or for a shadow without one, its kind's registered size on its feet (null: no kind known). */
    private @Nullable ShadowForm shape() {
        ShadowForm f = form();
        if (f != null || entityData == null) return f;
        EntityType<?> t = EntityType.byString(source()).orElse(null);
        return t == null || t == getType() ? null : ShadowForm.ofKind(t);
    }

    public float sizeScale() {
        return 1f + 0.03f * rank().ordinal();
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        if (isSinking()) {
            int s = sinkTicks() + 1;
            entityData.set(SINK, s);
            if (s >= SINK_TICKS) {
                ShadowService.gone(this);
                discard();
            }
            return;
        }
        if (skillCooldown > 0) skillCooldown--;
        if (boltCooldown > 0) boltCooldown--;
        // the master has gone (logged out, another dimension, died): back into the dark
        if (tickCount % 10 == 0 && (master() == null || !ShadowService.owns(master(), this))) {
            ShadowService.gone(this);
            discard();
        }
    }

    private void clientTick() {
        if (look() != null && look != null) look.follow(this);
        if (preview || isSinking() || isDeadOrDying()) return;
        // wisps of violet dark always drift off it
        if (tickCount % (isBoss() ? 3 : 6) == 0) {
            double w = getBbWidth() * 0.5;
            level().addParticle(WISP, getX() + (random.nextDouble() - 0.5) * 2 * w,
                    getY() + random.nextDouble() * getBbHeight() * 0.8, getZ() + (random.nextDouble() - 0.5) * 2 * w, 0, 0.015, 0);
        }
    }

    /**
     * Client only: the creature this shadow looks like, made from its form ({@link ShadowLook}) once and posed every
     * tick; a stand-in of its size for a creature that can't be drawn as itself.
     */
    public @Nullable LivingEntity look() {
        String src = source();
        CompoundTag f = entityData.get(FORM);
        if (look == null || !lookSource.equals(src) || lookForm != f || look.stale()) {
            lookSource = src;
            lookForm = f;
            look = ShadowLook.make(level(), src, form(), bossRank());
            look.body().setPos(getX(), getY(), getZ());
        }
        return look.body();
    }

    /**
     * Client only: how much larger than the creature in {@link #look()} is (itself) it is drawn, before its rank's
     * growth: its fit for a creature larger than a shadow stands, and a stand-in's growth to the creature's size.
     */
    public float lookScale() {
        if (look() == null || look == null) return 1f;
        ShadowForm f = shape();
        return (f == null ? 1f : f.fit()) * look.scale();
    }

    // ------------------------------------------------------------------ fighting

    /** Whether this shadow may fight a creature: never its master, players, or its master's allies. */
    public boolean canFight(@Nullable LivingEntity e) {
        if (e == null || !e.isAlive() || e == this) return false;
        if (e instanceof Player) return false;
        UUID owner = getOwnerUUID();
        if (e instanceof ShadowEntity) return false;
        if (e instanceof OwnableEntity o && owner != null && owner.equals(o.getOwnerUUID())) return false;
        if (e instanceof TamableAnimal t && t.isTame()) return false;
        // the light, unless its master stands against it
        if (com.beyondsolo.ruler.Rulers.spares(owner, e)) return false;
        return !e.isInvulnerable();
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return canFight(target) && super.canAttack(target);
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        UUID owner = getOwnerUUID();
        if (owner != null) {
            if (other.getUUID().equals(owner)) return true;
            if (other instanceof ShadowEntity s && owner.equals(s.getOwnerUUID())) return true;
        }
        return super.isAlliedTo(other);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!(target instanceof LivingEntity victim)) return super.doHurtTarget(target);
        swing(InteractionHand.MAIN_HAND);
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * ShadowService.auraBonus(this);
        return strike(victim, dmg, true);
    }

    /** A hit of shadow: leaves (or reacts with) the shadow element through the master's skills. */
    boolean strike(LivingEntity victim, float dmg, boolean knock) {
        ServerPlayer owner = master();
        if (owner != null) dmg = SkillCombat.strike(owner, victim, SkillElement.SHADOW, dmg);
        boolean hit = victim.hurt(damageSources().mobAttack(this), dmg);
        if (hit && knock) {
            double dx = victim.getX() - getX(), dz = victim.getZ() - getZ();
            double len = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
            victim.knockback(0.25 + 0.05 * rank().ordinal(), -dx / len, -dz / len);
        }
        if (hit) setLastHurtMob(victim);
        return hit;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isRising() || isSinking()) return false;
        // shadows pass through the dark: a wall or a crowd doesn't choke them
        if (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.DROWN)) return false;
        Entity by = source.getEntity();
        if (by != null && isAlliedTo(by)) return false;
        return super.hurt(source, amount);
    }

    // ------------------------------------------------------------------ flying and swimming

    /**
     * A flier's or a swimmer's shadow flies where its move control sends it, as the vanilla fliers do: its own pace in
     * the air, slower through water for a flier and through lava for both. Ridden or on its feet, as usual.
     */
    @Override
    public void travel(Vec3 input) {
        if (!airborne || getControllingPassenger() != null) {
            super.travel(input);
            return;
        }
        if (isControlledByLocalInstance()) {
            boolean swimmer = movement() == ShadowForm.Movement.SWIM;
            float drag = isInLava() ? 0.5f : isInWater() && !swimmer ? 0.8f : 0.91f;
            moveRelative(isInLava() || isInWater() && !swimmer ? getSpeed() * 0.6f : getSpeed(), input);
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(drag));
        }
        calculateEntityAnimation(false);
    }

    @Override
    public boolean isPushedByFluid() {
        return !airborne && super.isPushedByFluid();
    }

    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return false; // a shadow doesn't breathe
    }

    /**
     * Sends an airborne shadow to its place: straight there when it is near and nothing is in the way (a path keeps to
     * the blocks' grid and would leave a large flier well off to one side), along a path otherwise.
     */
    private void flyTo(Vec3 spot, double speed) {
        Vec3 from = getBoundingBox().getCenter();
        Vec3 to = spot.add(0, getBbHeight() * 0.5, 0);
        boolean clear = distanceToSqr(spot) < 16 * 16 && level().clip(new net.minecraft.world.level.ClipContext(from, to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this))
                .getType() == net.minecraft.world.phys.HitResult.Type.MISS;
        if (clear) {
            getNavigation().stop();
            station = spot;
            stationSpeed = speed;
        } else {
            station = null;
            getNavigation().moveTo(spot.x, spot.y, spot.z, speed);
        }
    }

    /**
     * Each tick on its way: straight on toward its place (the move control forgets a wanted spot after one tick),
     * slowing as it comes in so it doesn't shoot past.
     */
    private void keepFlying() {
        if (station == null || arrived()) return;
        double slow = Mth.clamp(Math.sqrt(distanceToSqr(station)) / 3, 0.3, 1);
        getMoveControl().setWantedPosition(station.x, station.y, station.z, stationSpeed * slow);
    }

    /** At its place: near the spot it flies straight to, or at the end of its path. */
    private boolean arrived() {
        return station != null ? distanceToSqr(station) < 0.7 * 0.7 : getNavigation().isDone();
    }

    /** Stops flying to its place: it hangs where it got to. */
    private void land() {
        if (station != null) setDeltaMovement(getDeltaMovement().scale(0.25));
        station = null;
        getNavigation().stop();
    }

    /**
     * A flier or a swimmer that is not in its place over this spot (looked at every half second, not every tick): it
     * goes there, rather than sitting on the ground where it rose.
     */
    private boolean offStation(Vec3 over) {
        if (!airborne || (tickCount + getId()) % 10 != 0) return false;
        return distanceToSqr(hoverOver(over)) > 1.5 * 1.5;
    }

    /**
     * Where an airborne shadow keeps to over a spot: a flier a couple of blocks over it, a swimmer just over the ground,
     * each a little off to its own side so they don't crowd one spot; lower under a ceiling, and right over the spot
     * when its side is walled in. A walker: the spot itself.
     */
    Vec3 hoverOver(Vec3 at) {
        if (!airborne) return at;
        double up = movement() == ShadowForm.Movement.FLY ? 2.2 : 0.6;
        double a = getId() * 2.39996;
        double side = 1.2 + getBbWidth() * 0.5;
        Vec3 off = new Vec3(at.x + Math.cos(a) * side, at.y, at.z + Math.sin(a) * side);
        EntityDimensions size = getDimensions(getPose());
        for (Vec3 to : new Vec3[]{off.add(0, up, 0), off.add(0, up * 0.5, 0), at.add(0, up, 0), at.add(0, up * 0.5, 0), off}) {
            if (level().noCollision(this, size.makeBoundingBox(to))) return to;
        }
        return at;
    }

    // ------------------------------------------------------------------ riding

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (isMount() && player.getUUID().equals(getOwnerUUID()) && !player.isSecondaryUseActive() && !isBusy() && getPassengers().isEmpty()) {
            if (!level().isClientSide) player.startRiding(this);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        if (isMount() && getFirstPassenger() instanceof Player p && p.getUUID().equals(getOwnerUUID())) return p;
        return super.getControllingPassenger();
    }

    @Override
    protected void tickRidden(Player player, Vec3 input) {
        super.tickRidden(player, input);
        setRot(player.getYRot(), player.getXRot() * 0.5f);
        yRotO = yBodyRot = yHeadRot = getYRot();
        if (jumpPending > 0 && onGround()) {
            Vec3 v = getDeltaMovement();
            double jump = getAttributeValue(Attributes.JUMP_STRENGTH) * (0.55 + 0.45 * jumpPending);
            setDeltaMovement(v.x, jump, v.z);
            hasImpulse = true;
            jumpPending = 0;
        }
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travel) {
        float side = player.xxa * 0.5f;
        float forward = player.zza;
        if (forward <= 0) forward *= 0.25f;
        return new Vec3(side, 0, forward);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return (float) getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    @Override
    public void onPlayerJump(int power) {
        if (!isMount()) return;
        jumpPending = power >= 90 ? 1f : 0.4f + 0.6f * power / 90f;
    }

    @Override
    public boolean canJump() {
        return isMount();
    }

    @Override
    public void handleStartJump(int power) {}

    @Override
    public void handleStopJump() {}

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false; // shadows (and whoever rides one) don't feel a fall
    }

    // ------------------------------------------------------------------ death, saving, sounds

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide) ShadowService.fallen(this);
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (!level().isClientSide && deathTime >= DEATH_TICKS) {
            ((ServerLevel) level()).sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + getBbHeight() * 0.5, getZ(), 16,
                    getBbWidth() * 0.4, getBbHeight() * 0.3, getBbWidth() * 0.4, 0.02);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
        // a shadow has nothing to drop
    }

    @Override
    protected int getBaseExperienceReward() {
        return 0;
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
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Source", source());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Source")) entityData.set(SOURCE, tag.getString("Source"));
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WITHER_SKELETON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SOUL_ESCAPE.value();
    }

    @Override
    protected float getSoundVolume() {
        return 0.6f;
    }

    @Override
    public float getVoicePitch() {
        return 0.6f + random.nextFloat() * 0.1f;
    }

    // ------------------------------------------------------------------ goals

    private final class MeleeGoal extends MeleeAttackGoal {
        MeleeGoal() {
            super(ShadowEntity.this, 1.25, true);
        }

        @Override
        public boolean canUse() {
            return role() != ShadowRecord.Role.CASTER && !isBusy() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !isBusy() && super.canContinueToUse();
        }
    }

    /** Keeps its distance and throws bolts of shadow. */
    private final class CasterGoal extends Goal {
        CasterGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return role() == ShadowRecord.Role.CASTER && !isBusy() && getTarget() != null && getTarget().isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (t == null) return;
            getLookControl().setLookAt(t, 30, 30);
            double d = distanceToSqr(t);
            boolean sees = getSensing().hasLineOfSight(t);
            if (d > 12 * 12 || !sees) getNavigation().moveTo(t, 1.15);
            else if (d < 4 * 4) {
                Vec3 away = position().subtract(t.position()).normalize().scale(4);
                getNavigation().moveTo(getX() + away.x, getY(), getZ() + away.z, 1.2);
            } else getNavigation().stop();
            if (boltCooldown <= 0 && sees && d < 16 * 16) {
                boltCooldown = 30;
                swing(InteractionHand.MAIN_HAND);
                Vec3 from = getEyePosition();
                SkillFx.play(t, SkillFxPayload.Kind.BOLT, rank().color(), from, 0.25f, 0);
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.NEUTRAL, 0.8f, 0.6f);
                strike(t, (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.9f * ShadowService.auraBonus(ShadowEntity.this), false);
            }
        }
    }

    /** A boss shadow's own skill: it brings its weight down on everything around it. */
    private final class BossSkillGoal extends Goal {
        private int windup;

        BossSkillGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            return isBoss() && !isBusy() && skillCooldown <= 0 && t != null && t.isAlive() && distanceToSqr(t) < 5 * 5;
        }

        @Override
        public boolean canContinueToUse() {
            return windup > 0 && !isBusy();
        }

        @Override
        public void start() {
            windup = 14;
            getNavigation().stop();
            SkillFx.play(ShadowEntity.this, SkillFxPayload.Kind.CAST, rank().color(), position(), 3.5f, 0);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.NEUTRAL, 0.8f, 1.2f);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (--windup > 0) return;
            skillCooldown = 160;
            swing(InteractionHand.MAIN_HAND);
            float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.5f;
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(4, 1.5, 4), ShadowEntity.this::canFight)) {
                if (e instanceof Enemy || e == getTarget() || e.getLastHurtByMob() != null) strike(e, dmg, true);
            }
            SkillFx.play(ShadowEntity.this, SkillFxPayload.Kind.SLAM, rank().color(), position(), 4.5f, 0);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 0.7f, 0.6f);
        }
    }

    /** Stays close to its master; slips through the dark to them when left too far behind. */
    private final class FollowMasterGoal extends Goal {
        private int recompute;

        FollowMasterGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            ServerPlayer m = master();
            if (m == null || isBusy() || m.isSpectator() || post != null) return false;
            if (getTarget() != null && distanceToSqr(m) < 20 * 20) return false;
            // a flier keeps its place over its master's head, however close it rose
            return distanceToSqr(m) > (isMount() ? 4 * 4 : 5 * 5) || offStation(m.position());
        }

        @Override
        public boolean canContinueToUse() {
            ServerPlayer m = master();
            if (m == null || post != null || isBusy()) return false;
            if (getTarget() != null && distanceToSqr(m) <= 20 * 20) return false;
            // a flier goes all the way to its place; one on its feet stops at its master's side
            return airborne ? !arrived() : !getNavigation().isDone() && distanceToSqr(m) > 2.5 * 2.5;
        }

        @Override
        public void start() {
            recompute = 0;
        }

        @Override
        public void stop() {
            land();
        }

        @Override
        public void tick() {
            ServerPlayer m = master();
            if (m == null) return;
            getLookControl().setLookAt(m, 10, getMaxHeadXRot());
            if (--recompute <= 0) {
                recompute = 10;
                if (distanceToSqr(m) > 22 * 22) {
                    ShadowService.slipTo(ShadowEntity.this, m);
                } else if (airborne) {
                    flyTo(hoverOver(m.position()), m.isSprinting() ? 1.5 : 1.2);
                } else {
                    getNavigation().moveTo(m, m.isSprinting() ? 1.5 : 1.2);
                }
            }
            keepFlying();
        }
    }

    /** Stands at the spot it guards: walks back to it after a fight, slips through the dark when far from it. */
    private final class GuardPostGoal extends Goal {
        private int recompute;

        GuardPostGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return post != null && !isBusy() && getTarget() == null && (distanceToSqr(post) > (airborne ? 4.0 * 4.0 : 2.5 * 2.5) || offStation(post));
        }

        @Override
        public boolean canContinueToUse() {
            if (post == null || isBusy() || getTarget() != null) return false;
            return airborne ? !arrived() : distanceToSqr(post) > 1.2 * 1.2;
        }

        @Override
        public void start() {
            recompute = 0;
        }

        @Override
        public void stop() {
            land();
        }

        @Override
        public void tick() {
            Vec3 at = post;
            if (at == null) return;
            if (--recompute <= 0) {
                recompute = 10;
                if (distanceToSqr(at) > GUARD_SLIP * GUARD_SLIP || getNavigation().isStuck()) {
                    ServerLevel level = (ServerLevel) level();
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.5, getZ(), 10, 0.3, 0.4, 0.3, 0.01);
                    teleportTo(at.x, at.y, at.z);
                    station = null;
                    getNavigation().stop();
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 10, 0.3, 0.4, 0.3, 0.01);
                } else if (airborne) {
                    flyTo(hoverOver(at), 1.1);
                } else {
                    getNavigation().moveTo(at.x, at.y, at.z, 1.1);
                }
            }
            keepFlying();
        }
    }

    /** How far from its post a guard goes after something. */
    public static final double GUARD_REACH = 14;
    /** How far from its post a guard can be before it slips back through the dark. */
    private static final double GUARD_SLIP = 24;

    /**
     * Picks what to fight: whatever hurt its master, then whatever its master hurt, then monsters near its master.
     * A guard picks from around its post instead.
     */
    private final class MasterTargetGoal extends TargetGoal {
        private int scan;

        MasterTargetGoal() {
            super(ShadowEntity.this, false);
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (isBusy()) return false;
            LivingEntity current = getTarget();
            if (current != null && current.isAlive() && canFight(current)) return false;
            if (--scan > 0) return false;
            scan = 10;
            LivingEntity pick = pick();
            if (pick == null) return false;
            targetMob = pick;
            return true;
        }

        private @Nullable LivingEntity pick() {
            ServerPlayer m = master();
            if (m == null) return null;
            Vec3 guard = post;
            if (guard != null) return guarding(m, guard);
            LivingEntity by = m.getLastHurtByMob();
            if (canFight(by) && m.tickCount - m.getLastHurtByMobTimestamp() < 200) return by;
            LivingEntity hit = m.getLastHurtMob();
            if (canFight(hit) && m.tickCount - m.getLastHurtMobTimestamp() < 200) return hit;
            LivingEntity ownBy = getLastHurtByMob();
            if (canFight(ownBy)) return ownBy;
            // monsters near its master, or near itself
            TargetingConditions cond = TargetingConditions.forCombat().range(18).ignoreLineOfSight().selector(e -> e instanceof Enemy && canFight(e));
            LivingEntity near = level().getNearestEntity(Mob.class, cond, ShadowEntity.this, m.getX(), m.getY(), m.getZ(), m.getBoundingBox().inflate(16, 6, 16));
            if (near != null) return near;
            return level().getNearestEntity(Mob.class, cond, ShadowEntity.this, getX(), getY(), getZ(), getBoundingBox().inflate(12, 5, 12));
        }

        /** What a guard fights: whatever hurts it, whatever hurts its master near the post, then monsters near the post. */
        private @Nullable LivingEntity guarding(ServerPlayer m, Vec3 guard) {
            double reach = GUARD_REACH * GUARD_REACH;
            LivingEntity ownBy = getLastHurtByMob();
            if (canFight(ownBy) && ownBy.distanceToSqr(guard) < reach) return ownBy;
            LivingEntity by = m.getLastHurtByMob();
            if (canFight(by) && m.tickCount - m.getLastHurtByMobTimestamp() < 200 && by.distanceToSqr(guard) < reach) return by;
            TargetingConditions cond = TargetingConditions.forCombat().range(GUARD_REACH + 4).ignoreLineOfSight()
                    .selector(e -> e instanceof Enemy && canFight(e) && e.distanceToSqr(guard) < reach);
            return level().getNearestEntity(Mob.class, cond, ShadowEntity.this, guard.x, guard.y, guard.z,
                    new net.minecraft.world.phys.AABB(guard, guard).inflate(GUARD_REACH, 6, GUARD_REACH));
        }

        @Override
        public void start() {
            setTarget(targetMob);
            super.start();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity t = getTarget();
            ServerPlayer m = master();
            if (isBusy() || t == null || !t.isAlive() || !canFight(t) || m == null) return false;
            Vec3 guard = post;
            if (guard != null) return t.distanceToSqr(guard) < (GUARD_REACH + 6) * (GUARD_REACH + 6);
            return t.distanceToSqr(m) < 28 * 28;
        }
    }
}
