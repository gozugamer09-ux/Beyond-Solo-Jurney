package com.beyondsolo.ruler;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * A herald of light: a lesser being in white plate with a spear of light and a crown of crystal, called down round a
 * ruler as it lands and as it angers ({@link RulerHost}). It fights its ruler's foes and only them (monarchs and their
 * soldiers, crowned hunters, the hunter judged, whoever struck the ruler or a herald): its spear passes everyone else
 * by. As strong as an S-rank gate's creatures; it fades when its ruler falls or leaves, and when it is left without one.
 *
 * <p>Animations ({@code animation.beyondsolo.ruler_herald.}): "main" plays idle, walk and death; "attack" plays
 * "attack" (a thrust of its spear).</p>
 */
public class HeraldEntity extends Monster implements GeoEntity {
    private static final String BASE = "animation.beyondsolo.ruler_herald.";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(BASE + "idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(BASE + "walk");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold(BASE + "death");
    /** How long it lasts without its ruler in the world (its land unloaded, or the ruler gone) before it fades. */
    private static final int ORPHAN_TICKS = 100;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable private UUID ruler;
    private int orphan;

    public HeraldEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 20;
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes()
                // an S-rank creature's strength is added as it is called (RulerHost)
                .add(Attributes.MAX_HEALTH, 40)
                .add(Attributes.ATTACK_DAMAGE, 8)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ARMOR, 8)
                .add(Attributes.ARMOR_TOUGHNESS, 2)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                LivingEntity by = getLastHurtByMob();
                return by != null && foe(by) && super.canUse();
            }
        });
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false, this::foe));
    }

    // ================================================================ its ruler

    /** Its ruler, if it is in the world and alive. */
    @Nullable
    public RulerBoss ruler() {
        if (ruler == null || !(level() instanceof ServerLevel server)) return null;
        Entity e = server.getEntity(ruler);
        return e instanceof RulerBoss r && r.isAlive() ? r : null;
    }

    @Nullable
    public UUID rulerId() {
        return ruler;
    }

    public void setRuler(RulerBoss r) {
        this.ruler = r.getUUID();
    }

    /** Its ruler's foes are its foes; with no ruler, it has none. */
    public boolean foe(LivingEntity e) {
        if (e == this || !e.isAlive() || Rulers.light(e)) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        RulerBoss r = ruler();
        return r != null && r.isFoe(e);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return foe(target) && super.canAttack(target);
    }

    /** Fire and lava can't touch it, but the System's fire element does (light-made, it fears shadow, not flame). */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return com.beyondsolo.combat.CombatDamage.spared(this, source, super.isInvulnerableTo(source));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!(target instanceof LivingEntity l) || !foe(l)) return false;
        triggerAnim("attack", "attack");
        return super.doHurtTarget(target);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel server) || !isAlive()) return;
        if (tickCount % 20 == 0) {
            // without its ruler it doesn't last
            if (ruler() == null) {
                orphan += 20;
                if (orphan >= ORPHAN_TICKS) fade(server);
            } else {
                orphan = 0;
            }
        }
        if (tickCount % 8 == 0) {
            server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + getBbHeight() + 0.2, getZ(), 1, 0.2, 0.05, 0.2, 0.005);
        }
    }

    /** It goes back to the light: a flash, and it is gone. */
    public void fade(ServerLevel server) {
        server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + getBbHeight() / 2, getZ(), 24, 0.3, 0.7, 0.3, 0.06);
        server.sendParticles(ParticleTypes.FLASH, getX(), getY() + getBbHeight() / 2, getZ(), 1, 0, 0, 0, 0);
        playSound(SoundEvents.BEACON_DEACTIVATE, 1.2f, 1.6f);
        discard();
    }

    // ================================================================ the rest

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.AMETHYST_BLOCK_CHIME;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.AMETHYST_BLOCK_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BEACON_DEACTIVATE;
    }

    @Override
    protected float getSoundVolume() {
        return 0.8f;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ruler != null) tag.putUUID("Ruler", ruler);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ruler = tag.hasUUID("Ruler") ? tag.getUUID("Ruler") : null;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            if (isDeadOrDying()) return state.setAndContinue(DEATH);
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }));
        AnimationController<HeraldEntity> attack = new AnimationController<>(this, "attack", 2, state -> PlayState.STOP);
        attack.triggerableAnim("attack", RawAnimation.begin().thenPlay(BASE + "attack"));
        controllers.add(attack);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
