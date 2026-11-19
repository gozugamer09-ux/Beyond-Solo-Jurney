package com.beyondsolo.home;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A training dummy in the hunter's Training Hall (made by tools/gen_home_assets.py): a figure of straw and canvas bound
 * to a post, that takes every blow, skill and burn a hunter has like a creature would (its kind counts as a foe, so
 * skills find it) and never falls. Over its head it shows what it is taking: damage a second over the bout, the last
 * blow, and the whole bout. A bout starts with the first blow and ends after {@link #BOUT_GAP} of quiet; each hunter's
 * best ten-second bout is kept on their home's record. One stands armoured ({@link #armoured}) to test blows against
 * armour.
 */
public class TrainingDummy extends Mob implements GeoEntity, Enemy {
    private static final EntityDataAccessor<Float> DPS = SynchedEntityData.defineId(TrainingDummy.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LAST = SynchedEntityData.defineId(TrainingDummy.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TOTAL = SynchedEntityData.defineId(TrainingDummy.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> BOUT_TICKS = SynchedEntityData.defineId(TrainingDummy.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HITS = SynchedEntityData.defineId(TrainingDummy.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ARMOURED = SynchedEntityData.defineId(TrainingDummy.class, EntityDataSerializers.BOOLEAN);
    /** Quiet (ticks) that ends a bout. */
    public static final int BOUT_GAP = 60;
    /** A best is only kept for a bout this long (ticks), damage a second measured over it. */
    public static final int BEST_TICKS = 200;
    /** Its health: refilled every tick, so a share of it (some burns) means the same as on a creature of some size. */
    public static final float HEALTH = 2000f;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private long boutStart = -1, lastHit = -1, lastSound = -10;
    private float boutTotal;
    /** In this bout, what each hunter dealt it (the best is theirs alone: their shadows' blows are not). */
    private final Map<UUID, Float> byHunter = new HashMap<>();
    /** The plot of the home it stands in (-1: placed by an operator elsewhere). */
    private int plot = -1;
    private int slot = -1;
    /** Client side only: the blows this client last saw it take, and when (its meter over it, client.home.HomePlates). */
    public int clientHits = -1;
    public long clientChanged;
    /** The hunter who struck it last this bout: a burn or a bleed with nobody behind it is theirs. */
    @org.jetbrains.annotations.Nullable private UUID lastHunter;

    public TrainingDummy(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        setNoAi(true);
        setPersistenceRequired();
        xpReward = 0;
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0)
                .add(Attributes.ARMOR, 0)
                .add(Attributes.ARMOR_TOUGHNESS, 0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DPS, 0f);
        b.define(LAST, 0f);
        b.define(TOTAL, 0f);
        b.define(BOUT_TICKS, 0);
        b.define(HITS, 0);
        b.define(ARMOURED, false);
    }

    /** Sets it up as dummy {@code slot} of plot {@code plot}'s hall, armoured or not. */
    public void configure(int plot, int slot, boolean armoured) {
        this.plot = plot;
        this.slot = slot;
        setArmoured(armoured);
    }

    public void setArmoured(boolean armoured) {
        entityData.set(ARMOURED, armoured);
        AttributeInstance armor = getAttribute(Attributes.ARMOR), tough = getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (armor != null) armor.setBaseValue(armoured ? 20 : 0);
        if (tough != null) tough.setBaseValue(armoured ? 12 : 0);
        setCustomName(Component.translatable(armoured ? "entity.beyondsolo.training_dummy.armoured" : "entity.beyondsolo.training_dummy"));
        setCustomNameVisible(false);
    }

    public boolean armoured() { return entityData.get(ARMOURED); }
    public float dps() { return entityData.get(DPS); }
    public float last() { return entityData.get(LAST); }
    public float total() { return entityData.get(TOTAL); }
    public int boutTicks() { return entityData.get(BOUT_TICKS); }
    public int hits() { return entityData.get(HITS); }
    public int plot() { return plot; }
    public int slot() { return slot; }

    /** True while a bout is on (a blow in the last {@link #BOUT_GAP} ticks). */
    public boolean fighting() {
        return lastHit >= 0 && level().getGameTime() - lastHit < BOUT_GAP;
    }

    /** It took {@code amount} (after armour and the rest) from {@code source}: the bout counts it. */
    void took(DamageSource source, float amount) {
        if (!(level() instanceof ServerLevel server) || amount <= 0) return;
        long now = server.getGameTime();
        if (lastHit < 0 || now - lastHit >= BOUT_GAP) {
            boutStart = now;
            boutTotal = 0;
            byHunter.clear();
            lastHunter = null;
            entityData.set(HITS, 0);
        }
        lastHit = now;
        boutTotal += amount;
        // a hunter's own blows, arrows and skills; what burns or bleeds with nobody behind it is the last one's who struck
        Entity by = source.getEntity();
        if (by instanceof ServerPlayer p) {
            byHunter.merge(p.getUUID(), amount, Float::sum);
            lastHunter = p.getUUID();
        } else if (by == null && lastHunter != null) {
            byHunter.merge(lastHunter, amount, Float::sum);
        }
        entityData.set(LAST, amount);
        entityData.set(TOTAL, boutTotal);
        entityData.set(HITS, hits() + 1);
        refresh(now);
        triggerAnim("act", "struck");
        server.sendParticles(ParticleTypes.CRIT, getX(), getY() + 1.3, getZ(), 4, 0.25, 0.3, 0.25, 0.1);
        if (now - lastSound >= 3) {
            lastSound = now;
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOL_HIT, SoundSource.NEUTRAL, 0.9f, 0.8f + random.nextFloat() * 0.3f);
        }
    }

    /** The bout's damage a second: over its length so far (a second at least). */
    private void refresh(long now) {
        long span = Math.max(20, now - boutStart);
        entityData.set(DPS, boutTotal * 20f / span);
        entityData.set(BOUT_TICKS, (int) Math.min(Integer.MAX_VALUE, now - boutStart));
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (!(level() instanceof ServerLevel server)) return;
        // it never falls: what it lost is back at once
        if (getHealth() < getMaxHealth()) setHealth(getMaxHealth());
        long now = server.getGameTime();
        // two on one stand (its chunk's entities loading late, a rebuild): the one made first stays
        if (plot >= 0 && (tickCount + getId()) % 40 == 0) {
            for (TrainingDummy o : server.getEntitiesOfClass(TrainingDummy.class, getBoundingBox().inflate(4),
                    d -> d != this && d.isAlive() && d.plot == plot && d.slot == slot)) {
                if (o.getUUID().compareTo(getUUID()) < 0) {
                    discard();
                    return;
                }
            }
        }
        if (lastHit >= 0) {
            if (now - lastHit < BOUT_GAP) {
                if (now % 5 == 0) refresh(now);
            } else if (boutStart >= 0) {
                // the bout is over: a long enough one may be a hunter's best
                long span = lastHit - boutStart;
                if (span + BOUT_GAP >= BEST_TICKS) {
                    for (Map.Entry<UUID, Float> e : byHunter.entrySet()) {
                        ServerPlayer p = com.beyondsolo.util.OnlinePlayers.get(server.getServer(), e.getKey());
                        if (p != null) Homes.bout(p, this, e.getValue() * 20f / Math.max(20, span));
                    }
                }
                boutStart = -1;
                byHunter.clear();
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // a command's kill leaves it standing, and nothing it does to itself counts
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && source.getEntity() == null) return false;
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        // never falls: a blow bigger than all its health only fills it up again
        setHealth(getMaxHealth());
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    /** Its own thud plays when it is struck (a few a second at most): no creature's cry. */
    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public void push(Entity entity) {}

    @Override
    public void knockback(double strength, double x, double z) {}

    @Override
    public boolean canBeLeashed() {
        return false;
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
    public boolean isPersistenceRequired() {
        return true;
    }

    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {}

    @Override
    protected void doPush(Entity entity) {}

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Plot", plot);
        tag.putInt("Slot", slot);
        tag.putBoolean("Armoured", armoured());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        plot = tag.contains("Plot") ? tag.getInt("Plot") : -1;
        slot = tag.contains("Slot") ? tag.getInt("Slot") : -1;
        setArmoured(tag.getBoolean("Armoured"));
    }

    /** Nobody but an operator moves it: a hunter's hand does nothing to it. */
    @Override
    public net.minecraft.world.InteractionResult mobInteract(Player player, net.minecraft.world.InteractionHand hand) {
        return net.minecraft.world.InteractionResult.PASS;
    }

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4,
                state -> state.setAndContinue(RawAnimation.begin().thenLoop("animation.beyondsolo.training_dummy.idle"))));
        controllers.add(new AnimationController<>(this, "act", 1, state -> PlayState.STOP)
                .triggerableAnim("struck", RawAnimation.begin().thenPlay("animation.beyondsolo.training_dummy.struck")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
