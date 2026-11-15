package com.beyondsolo.enemy;

import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.combat.Statuses;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * A bolt of an element, thrown by casters and bosses: a ball of light (drawn by {@code client.enemy.OrbRenderer})
 * that drifts a little towards its target, hurts what it hits, and leaves its affinity's mark (and, for bosses, the
 * gate's element buildup). It never hurts monsters.
 */
public class ElementOrbEntity extends Projectile {
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(ElementOrbEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(ElementOrbEntity.class, EntityDataSerializers.FLOAT);

    private float damage = 4;
    private Affinity affinity = Affinity.SHADE;
    @Nullable private DamageElement buildup;
    private float buildupAmount;
    private float homing;
    @Nullable private UUID target;
    private int life = 90;

    public ElementOrbEntity(EntityType<? extends ElementOrbEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    /** Throws a bolt from {@code from} along {@code dir}. */
    public static ElementOrbEntity shoot(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 dir, float speed, float damage, Affinity affinity,
                                         float homing, @Nullable LivingEntity target) {
        ElementOrbEntity orb = EnemyModule.ORB.get().create(level);
        if (orb == null) return null;
        orb.setOwner(owner);
        orb.moveTo(from.x, from.y, from.z, owner.getYRot(), 0);
        orb.setDeltaMovement(dir.normalize().scale(speed));
        orb.damage = damage;
        orb.affinity = affinity;
        orb.homing = homing;
        orb.target = target != null ? target.getUUID() : null;
        orb.entityData.set(COLOR, affinity.color);
        level.addFreshEntity(orb);
        return orb;
    }

    /** Adds the gate element's buildup on hit (bosses). */
    public ElementOrbEntity withBuildup(@Nullable DamageElement element, float amount) {
        this.buildup = element;
        this.buildupAmount = amount;
        return this;
    }

    public ElementOrbEntity withSize(float size) {
        entityData.set(SIZE, size);
        return this;
    }

    public int color() {
        return entityData.get(COLOR);
    }

    public float size() {
        return entityData.get(SIZE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(COLOR, 0xA86CF0);
        builder.define(SIZE, 0.35f);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement();
        if (!level().isClientSide) {
            if (--life <= 0) {
                fizzle();
                return;
            }
            if (homing > 0 && target != null && level() instanceof ServerLevel s && s.getEntity(target) instanceof LivingEntity t && t.isAlive()) {
                Vec3 want = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(position()).normalize().scale(v.length());
                v = v.add(want.subtract(v).scale(homing * 0.08)).normalize().scale(v.length());
                setDeltaMovement(v);
            }
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                onHit(hit);
                if (isRemoved()) return;
            }
        } else {
            int c = color();
            level().addParticle(new DustParticleOptions(new Vector3f(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f), 1.2f),
                    getX() - v.x * 0.5, getY() - v.y * 0.5, getZ() - v.z * 0.5, 0, 0, 0);
        }
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        ProjectileUtil.rotateTowardsMovement(this, 0.5f);
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return super.canHitEntity(e) && !(e instanceof Monster) && !(e instanceof ElementOrbEntity);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (result.getEntity() instanceof LivingEntity victim) {
            Entity owner = getOwner();
            LivingEntity attacker = owner instanceof LivingEntity le ? le : null;
            if (victim.hurt(damageSources().indirectMagic(this, owner), damage)) {
                if (attacker != null) affinity.onHit(attacker, victim, 18 + damage * 2);
                if (buildup != null) Statuses.apply(victim, buildup, buildupAmount, attacker);
            }
        }
        burst();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        burst();
    }

    private void burst() {
        if (level() instanceof ServerLevel s) {
            EnemyFx.burst(s, position(), color(), 1.2f);
            level().playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 0.7f, 1.4f);
        }
        discard();
    }

    private void fizzle() {
        if (level() instanceof ServerLevel s) EnemyFx.burst(s, position(), color(), 0.6f);
        discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", damage);
        tag.putInt("Affinity", affinity.ordinal());
        tag.putInt("Life", life);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("Damage");
        affinity = Affinity.byIndex(tag.getInt("Affinity"));
        life = tag.getInt("Life");
        entityData.set(COLOR, affinity.color);
    }
}
