package com.beyondsolo.enemy;

import com.beyondsolo.gate.GateService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * The moves of the gate's creatures, one goal each, used by {@link GateMonsterEntity} according to its kind's
 * {@link MonsterKind.Style}. Every move that hits hard shows itself first: a circle or a strip on the ground that fills
 * until the hit lands, and the wind-up of the creature's animation, timed so that the hit lands on the animation's
 * strike. Cooldowns count game ticks, so they run at the same pace however often the goal is asked.
 */
final class MonsterGoals {
    private MonsterGoals() {}

    static float damage(Mob m) {
        return (float) m.getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    /** What a creature's moves hurt: anything alive that is not a monster. */
    static boolean foe(LivingEntity self, LivingEntity e) {
        return e != self && e.isAlive() && !(e instanceof Monster) && !e.isSpectator();
    }

    /** Foes whose feet are within {@code radius} of {@code at} (and not far above or below it). */
    static List<LivingEntity> foesNear(LivingEntity self, Vec3 at, double radius) {
        return self.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius + 1, 3, radius + 1),
                e -> foe(self, e) && horizontal(e.position(), at) <= radius + e.getBbWidth() / 2);
    }

    static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** The floor under a creature: a flyer's circle goes on the ground, not in the air. */
    static Vec3 ground(LivingEntity m) {
        BlockPos p = m.blockPosition();
        for (int i = 0; i < 10 && m.level().getBlockState(p.below()).isAir(); i++) p = p.below();
        return new Vec3(m.getX(), p.getY(), m.getZ());
    }

    static Vec3 flat(Vec3 v) {
        Vec3 f = v.multiply(1, 0, 1);
        return f.lengthSqr() < 1e-6 ? Vec3.ZERO : f.normalize();
    }

    static void face(LivingEntity m, Vec3 dir) {
        if (dir.horizontalDistanceSqr() < 1e-6) return;
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90f;
        m.setYRot(yaw);
        m.yBodyRot = yaw;
        m.yHeadRot = yaw;
    }

    private static Vec3 forward(LivingEntity m) {
        return flat(Vec3.directionFromRotation(0, m.getYRot()));
    }

    // ================================================================ charger

    /** Chargers: from a few blocks off, a wind-up (the pit ravager scrapes the ground) and then a headlong rush. */
    static final class Charge extends Goal {
        private final GateMonsterEntity m;
        private long next = 60;
        private int windup;
        private int rush;
        private Vec3 dir = Vec3.ZERO;
        private boolean hit;

        Charge(GateMonsterEntity m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = m.getTarget();
            if (m.tickCount < next || t == null || !t.isAlive()) return false;
            double d = m.distanceTo(t);
            return d > 3.5 && d < 12 && m.getSensing().hasLineOfSight(t);
        }

        @Override
        public boolean canContinueToUse() {
            return (windup > 0 || rush > 0) && m.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            windup = 14;
            rush = 0;
            hit = false;
            m.getNavigation().stop();
            m.triggerAnim("attack", m.kind().specialAnim != null ? "special" : "attack");
            m.playSound(SoundEvents.RAVAGER_STEP, 1f, m.kind() == MonsterKind.PIT_RAVAGER ? 0.8f : 1.4f);
        }

        @Override
        public void stop() {
            windup = 0;
            rush = 0;
            next = m.tickCount + 110 + m.getRandom().nextInt(60);
        }

        @Override
        public void tick() {
            LivingEntity t = m.getTarget();
            if (windup > 0) {
                if (t != null) m.getLookControl().setLookAt(t, 30, 30);
                m.setDeltaMovement(m.getDeltaMovement().multiply(0.2, 1, 0.2));
                if (--windup == 0) {
                    dir = t != null ? flat(t.position().subtract(m.position())) : forward(m);
                    if (dir == Vec3.ZERO) dir = forward(m);
                    rush = 14;
                    if (m.level() instanceof ServerLevel s) EnemyFx.telegraphLine(s, m.position(), dir, 9, 1.4f, 6, m.affinity().color);
                    if (m.kind() == MonsterKind.PIT_RAVAGER) m.playSound(SoundEvents.RAVAGER_ROAR, 0.8f, 1.2f);
                }
                return;
            }
            if (rush <= 0) return;
            rush--;
            m.setDeltaMovement(dir.x * 0.75, m.getDeltaMovement().y, dir.z * 0.75);
            face(m, dir);
            if (!hit && m.level() instanceof ServerLevel s) {
                for (LivingEntity e : s.getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(0.4), e -> foe(m, e))) {
                    hit = true;
                    if (m.kind().specialAnim != null) m.triggerAnim("attack", "attack");
                    if (e.hurt(m.damageSources().mobAttack(m), damage(m) * 1.4f)) {
                        e.push(dir.x * 1.1, 0.35, dir.z * 1.1);
                        e.hurtMarked = true;
                        EnemyHooks.affinityHit(m, e, 1.3f);
                    }
                }
            }
            if (m.horizontalCollision) rush = 0;
        }
    }

    // ================================================================ caster

    /** Casters: keep 6 to 12 blocks away and throw a bolt of their element now and then. */
    static final class Cast extends Goal {
        private final GateMonsterEntity m;
        private long next = 30;
        private int windup = -1;

        Cast(GateMonsterEntity m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = m.getTarget();
            return t != null && t.isAlive();
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
            LivingEntity t = m.getTarget();
            if (t == null) return;
            m.getLookControl().setLookAt(t, 30, 30);
            double d = m.distanceTo(t);
            boolean sees = m.getSensing().hasLineOfSight(t);
            if (windup >= 0) {
                m.getNavigation().stop();
                if (--windup <= 0) {
                    windup = -1;
                    next = m.tickCount + 45 + m.getRandom().nextInt(30);
                    if (m.level() instanceof ServerLevel s) {
                        Vec3 from = m.position().add(0, m.getBbHeight() * 0.65, 0);
                        Vec3 aim = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(from).normalize();
                        ElementOrbEntity.shoot(s, m, from, aim, 0.65f, damage(m), m.boltElement(), 0.35f, t);
                        m.playSound(m.kind() == MonsterKind.BRIMLING ? SoundEvents.BLAZE_SHOOT : SoundEvents.EVOKER_CAST_SPELL, 0.8f, 1.4f);
                    }
                }
                return;
            }
            if (!sees || d > 12) m.getNavigation().moveTo(t, 1.0);
            else if (d < 6) {
                Vec3 away = flat(m.position().subtract(t.position())).scale(4);
                m.getNavigation().moveTo(m.getX() + away.x, m.getY(), m.getZ() + away.z, 1.1);
            } else m.getNavigation().stop();
            if (m.tickCount >= next && sees && d < 16) {
                windup = 11;
                m.triggerAnim("attack", "attack");
            }
        }
    }

    /** The tusk shaman: drives its staff into the ground and mends the hurt creatures around it. */
    static final class Mend extends Goal {
        private static final int COLOR = 0x5AF0C8;
        private final GateMonsterEntity m;
        private long next = 100;
        private int windup;

        Mend(GateMonsterEntity m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private List<Mob> hurtAllies(double radius, float below) {
            return m.level().getEntitiesOfClass(Mob.class, m.getBoundingBox().inflate(radius, 4, radius),
                    e -> e.isAlive() && e instanceof Monster && EnemyTraits.has(e) && !(e instanceof com.beyondsolo.gate.GateBossEntity)
                            && e.getHealth() < e.getMaxHealth() * below);
        }

        @Override
        public boolean canUse() {
            return m.tickCount >= next && m.getTarget() != null && !hurtAllies(10, 0.7f).isEmpty();
        }

        @Override
        public boolean canContinueToUse() {
            return windup > 0 && m.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            windup = 12;
            m.getNavigation().stop();
            m.triggerAnim("attack", "special");
            m.playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 0.8f, 1.3f);
            if (m.level() instanceof ServerLevel s) EnemyFx.telegraphCircle(s, ground(m), 8f, 12, COLOR);
        }

        @Override
        public void stop() {
            windup = 0;
            next = m.tickCount + 170 + m.getRandom().nextInt(70);
        }

        @Override
        public void tick() {
            if (--windup > 0 || !(m.level() instanceof ServerLevel s)) return;
            for (Mob e : hurtAllies(8, 1f)) {
                e.heal(e.getMaxHealth() * 0.25f);
                EnemyFx.pillar(s, e.position(), COLOR, 0.6f, e.getBbHeight() + 0.8f);
                s.sendParticles(ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
            }
            EnemyFx.burst(s, m.position().add(0, 0.4, 0), COLOR, 2.2f);
            m.playSound(SoundEvents.BEACON_POWER_SELECT, 0.7f, 1.5f);
        }
    }

    /** The brimling: when something gets close or hurts it, it folds into itself and is gone, a few blocks away. */
    static final class Blink extends Goal {
        private final GateMonsterEntity m;
        private long next = 40;
        private int timer;

        Blink(GateMonsterEntity m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = m.getTarget();
            if (m.tickCount < next || t == null || !t.isAlive()) return false;
            return m.distanceTo(t) < 4 || (m.getLastHurtByMob() != null && m.tickCount - m.getLastHurtByMobTimestamp() < 10);
        }

        @Override
        public boolean canContinueToUse() {
            return timer > 0 && m.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            timer = 10;
            m.getNavigation().stop();
            m.triggerAnim("attack", "special");
        }

        @Override
        public void stop() {
            timer = 0;
            next = m.tickCount + 100 + m.getRandom().nextInt(50);
        }

        @Override
        public void tick() {
            if (--timer != 5 || !(m.level() instanceof ServerLevel s)) return;
            LivingEntity t = m.getTarget();
            Vec3 from = m.position();
            Vec3 away = t == null ? forward(m).reverse() : flat(from.subtract(t.position()));
            if (away == Vec3.ZERO) away = new Vec3(1, 0, 0);
            double base = Math.atan2(away.z, away.x);
            for (int i = 0; i < 12; i++) {
                double a = base + (m.getRandom().nextDouble() - 0.5) * 2.0;
                double d = 6 + m.getRandom().nextDouble() * 3;
                if (m.randomTeleport(from.x + Math.cos(a) * d, from.y + 2, from.z + Math.sin(a) * d, false)) {
                    s.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 0.6, from.z, 24, 0.3, 0.5, 0.3, 0.3);
                    s.sendParticles(ParticleTypes.FLAME, from.x, from.y + 0.6, from.z, 8, 0.2, 0.3, 0.2, 0.02);
                    s.sendParticles(ParticleTypes.PORTAL, m.getX(), m.getY() + 0.6, m.getZ(), 24, 0.3, 0.5, 0.3, 0.3);
                    m.level().playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, m.getSoundSource(), 0.7f, 1.4f);
                    m.playSound(SoundEvents.ENDERMAN_TELEPORT, 0.7f, 1.4f);
                    return;
                }
            }
        }
    }

    // ================================================================ bomber

    /** Bombers: fly at the target, light the fuse when close, burst. */
    static final class Dive extends Goal {
        private final GateMonsterEntity m;

        Dive(GateMonsterEntity m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = m.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return m.fuse() > 0 || canUse();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = m.getTarget();
            int fuse = m.fuse();
            if (fuse > 0) {
                m.setFuse(fuse - 1);
                m.setDeltaMovement(m.getDeltaMovement().scale(0.6));
                if (fuse - 1 <= 0) m.burst();
                return;
            }
            if (t == null) return;
            m.getLookControl().setLookAt(t, 30, 30);
            Vec3 goal = t.position().add(0, t.getBbHeight() * 0.6, 0);
            Vec3 to = goal.subtract(m.position());
            double d = to.length();
            if (d < 1.9) {
                m.setFuse(22);
                m.triggerAnim("attack", "attack");
                m.playSound(SoundEvents.CREEPER_PRIMED, 1f, 1.6f);
                return;
            }
            double speed = m.getAttributeValue(Attributes.FLYING_SPEED) * 0.9;
            m.setDeltaMovement(m.getDeltaMovement().scale(0.85).add(to.normalize().scale(speed * 0.35)));
            face(m, to);
        }
    }

    // ================================================================ brute

    /** Brutes: rear up and bring both arms (or paws) down; everything in the ring is hurt and thrown back. */
    static final class Slam extends Goal {
        private final GateMonsterEntity m;
        private final int windupTicks;
        private long next = 60;
        private int windup;
        private float radius;

        Slam(GateMonsterEntity m, int windupTicks) {
            this.m = m;
            this.windupTicks = windupTicks;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = m.getTarget();
            return m.tickCount >= next && t != null && t.isAlive() && m.onGround() && m.distanceTo(t) < 2.2 + m.getBbWidth() * 1.4;
        }

        @Override
        public boolean canContinueToUse() {
            return windup > 0 && m.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            windup = windupTicks;
            radius = 2.4f + m.getBbWidth() * 1.1f;
            m.getNavigation().stop();
            m.triggerAnim("attack", "special");
            m.playSound(switch (m.kind()) {
                case RIMECLAW -> SoundEvents.POLAR_BEAR_WARNING;
                case RUNE_COLOSSUS -> SoundEvents.IRON_GOLEM_ATTACK;
                default -> SoundEvents.PIGLIN_BRUTE_ANGRY;
            }, 1.2f, 0.7f);
            if (m.level() instanceof ServerLevel s) EnemyFx.telegraphCircle(s, ground(m), radius, windupTicks, m.affinity().color);
        }

        @Override
        public void stop() {
            windup = 0;
            next = m.tickCount + 130 + m.getRandom().nextInt(70);
        }

        @Override
        public void tick() {
            LivingEntity t = m.getTarget();
            if (t != null && windup > windupTicks / 2) m.getLookControl().setLookAt(t, 20, 20);
            m.setDeltaMovement(m.getDeltaMovement().multiply(0.3, 1, 0.3));
            if (--windup > 0 || !(m.level() instanceof ServerLevel s)) return;
            Vec3 at = ground(m);
            float dmg = damage(m) * 1.3f;
            for (LivingEntity e : foesNear(m, at, radius)) {
                float k = (float) Mth.clamp(1.15 - horizontal(e.position(), at) / radius * 0.5, 0.6, 1.0);
                if (e.hurt(m.damageSources().mobAttack(m), dmg * k)) {
                    Vec3 push = flat(e.position().subtract(at)).scale(0.9 * k);
                    e.push(push.x, 0.45 * k, push.z);
                    e.hurtMarked = true;
                    EnemyHooks.affinityHit(m, e, 1.2f);
                }
            }
            BlockState floor = m.level().getBlockState(BlockPos.containing(at).below());
            if (floor.isAir()) floor = Blocks.STONE.defaultBlockState();
            BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, floor);
            for (int i = 0; i < 24; i++) {
                double a = i * Mth.TWO_PI / 24;
                s.sendParticles(dust, at.x + Math.cos(a) * radius * 0.7, at.y + 0.1, at.z + Math.sin(a) * radius * 0.7, 3, 0.2, 0.1, 0.2, 0.15);
            }
            s.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 1, 0, 0, 0, 0);
            EnemyFx.burst(s, at.add(0, 0.3, 0), m.affinity().color, radius * 0.7f);
            m.playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 0.55f);
            GateService.shake(s, m, 0.5f + m.getBbWidth() * 0.25f, 12 + radius);
        }
    }

    // ================================================================ archer

    /** Archers: keep a bowshot away, sidestep, draw and loose. */
    static final class Shoot extends Goal {
        private final GateMonsterEntity m;
        private long next = 20;
        private int windup = -1;
        private int strafeTicks;
        private boolean left;

        Shoot(GateMonsterEntity m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = m.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            windup = -1;
            m.getMoveControl().strafe(0, 0);
        }

        @Override
        public void tick() {
            LivingEntity t = m.getTarget();
            if (t == null) return;
            m.getLookControl().setLookAt(t, 30, 30);
            double d = m.distanceTo(t);
            boolean sees = m.getSensing().hasLineOfSight(t);
            if (windup >= 0) {
                m.getNavigation().stop();
                if (--windup <= 0) {
                    windup = -1;
                    loose(t);
                    next = m.tickCount + 26 + m.getRandom().nextInt(22);
                }
                return;
            }
            if (!sees || d > 15) m.getNavigation().moveTo(t, 1.0);
            else if (d < 6) {
                Vec3 away = flat(m.position().subtract(t.position())).scale(5);
                m.getNavigation().moveTo(m.getX() + away.x, m.getY(), m.getZ() + away.z, 1.15);
            } else {
                m.getNavigation().stop();
                if (++strafeTicks > 30 && m.getRandom().nextFloat() < 0.06f) {
                    left = !left;
                    strafeTicks = 0;
                }
                m.getMoveControl().strafe(0, left ? 0.45f : -0.45f);
            }
            if (m.tickCount >= next && sees && d < 20) {
                windup = 13;
                m.triggerAnim("attack", "attack");
            }
        }

        private void loose(LivingEntity t) {
            Arrow arrow = new Arrow(m.level(), m, new ItemStack(Items.ARROW), null);
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            arrow.setBaseDamage(damage(m) * 0.55);
            if (m.affinity() == Affinity.FLAME) arrow.igniteForSeconds(5);
            double dx = t.getX() - m.getX();
            double dz = t.getZ() - m.getZ();
            double dy = t.getY(0.3333) - arrow.getY();
            double h = Math.sqrt(dx * dx + dz * dz);
            arrow.shoot(dx, dy + h * 0.2, dz, 1.6f, 14 - m.level().getDifficulty().getId() * 4);
            m.playSound(SoundEvents.SKELETON_SHOOT, 1f, 1f / (m.getRandom().nextFloat() * 0.4f + 0.8f));
            m.level().addFreshEntity(arrow);
        }
    }

    // ================================================================ skirmisher

    /** Skirmishers: from a few blocks off, a crouch and a dash straight at the target, cutting as it passes. */
    static final class Dash extends Goal {
        private final GateMonsterEntity m;
        private long next = 40;
        private int windup;
        private int dash;
        private Vec3 dir = Vec3.ZERO;
        private boolean hit;

        Dash(GateMonsterEntity m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = m.getTarget();
            if (m.tickCount < next || t == null || !t.isAlive() || !m.onGround()) return false;
            double d = m.distanceTo(t);
            return d > 3.5 && d < 10 && m.getSensing().hasLineOfSight(t);
        }

        @Override
        public boolean canContinueToUse() {
            return (windup > 0 || dash > 0) && m.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            windup = 4;
            dash = 0;
            hit = false;
            m.getNavigation().stop();
            m.triggerAnim("attack", "special");
            m.playSound(SoundEvents.BREEZE_JUMP, 0.8f, m.kind() == MonsterKind.RIME_STALKER ? 0.8f : 1.4f);
        }

        @Override
        public void stop() {
            windup = 0;
            dash = 0;
            next = m.tickCount + 70 + m.getRandom().nextInt(40);
        }

        @Override
        public void tick() {
            LivingEntity t = m.getTarget();
            if (windup > 0) {
                if (t != null) {
                    m.getLookControl().setLookAt(t, 30, 30);
                    dir = flat(t.position().subtract(m.position()));
                }
                if (--windup == 0) {
                    if (dir == Vec3.ZERO) dir = forward(m);
                    dash = 7;
                    if (m.level() instanceof ServerLevel s) EnemyFx.telegraphLine(s, m.position(), dir, 7, 1.0f, 4, m.affinity().color);
                }
                return;
            }
            if (dash <= 0) return;
            dash--;
            m.setDeltaMovement(dir.x * 0.95, m.getDeltaMovement().y, dir.z * 0.95);
            face(m, dir);
            if (m.level() instanceof ServerLevel s) {
                s.sendParticles(m.kind() == MonsterKind.RIME_STALKER ? ParticleTypes.SNOWFLAKE : ParticleTypes.CLOUD,
                        m.getX(), m.getY() + 0.3, m.getZ(), 2, 0.15, 0.2, 0.15, 0.01);
                if (!hit) {
                    for (LivingEntity e : s.getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(0.35), e -> foe(m, e))) {
                        hit = true;
                        m.triggerAnim("attack", "attack");
                        if (e.hurt(m.damageSources().mobAttack(m), damage(m) * 1.2f)) {
                            e.push(dir.x * 0.5, 0.2, dir.z * 0.5);
                            e.hurtMarked = true;
                            EnemyHooks.affinityHit(m, e, 1f);
                            m.onStrike(e);
                        }
                        m.startRetreat(16);
                        dash = Math.min(dash, 1);
                        break;
                    }
                }
            }
            if (m.horizontalCollision) dash = 0;
        }
    }

    /** Skirmishers, after a hit: back off a few blocks before coming in again. */
    static final class Retreat extends Goal {
        private final GateMonsterEntity m;

        Retreat(GateMonsterEntity m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return m.retreat() > 0 && m.getTarget() != null;
        }

        @Override
        public void stop() {
            m.startRetreat(0);
        }

        @Override
        public void tick() {
            LivingEntity t = m.getTarget();
            if (t == null) return;
            int left = m.retreat();
            m.startRetreat(left - 1);
            if (left % 5 == 0 || m.getNavigation().isDone()) {
                Vec3 away = flat(m.position().subtract(t.position()));
                if (away == Vec3.ZERO) away = forward(m).reverse();
                m.getNavigation().moveTo(m.getX() + away.x * 5, m.getY(), m.getZ() + away.z * 5, 1.25);
            }
            m.getLookControl().setLookAt(t, 30, 30);
        }
    }

    // ================================================================ swooper

    /** Swoopers: circle over the target, dive, bite and climb out again. */
    static final class Swoop extends Goal {
        private final GateMonsterEntity m;
        private int phase;
        private int timer;
        private double angle;
        private boolean hit;

        Swoop(GateMonsterEntity m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = m.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            LivingEntity t = m.getTarget();
            phase = 0;
            timer = 25 + m.getRandom().nextInt(30);
            angle = t == null ? 0 : Math.atan2(m.getZ() - t.getZ(), m.getX() - t.getX());
        }

        @Override
        public void tick() {
            LivingEntity t = m.getTarget();
            if (t == null) return;
            switch (phase) {
                case 0 -> {
                    angle += 0.09;
                    Vec3 spot = circleSpot(t);
                    m.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, 1.0);
                    m.getLookControl().setLookAt(t, 30, 30);
                    if (--timer <= 0 && m.getSensing().hasLineOfSight(t)) {
                        phase = 1;
                        timer = 30;
                        hit = false;
                        m.playSound(SoundEvents.PHANTOM_SWOOP, 0.7f, m.getVoicePitch());
                    }
                }
                case 1 -> {
                    Vec3 aim = t.position().add(0, t.getBbHeight() * 0.55, 0);
                    Vec3 to = aim.subtract(m.position().add(0, m.getBbHeight() * 0.5, 0));
                    double speed = Math.max(0.5, m.getAttributeValue(Attributes.FLYING_SPEED) * 1.7);
                    Vec3 v = to.normalize().scale(Math.min(speed, to.length() + 0.1));
                    m.setDeltaMovement(v);
                    face(m, v);
                    if (!hit && m.getBoundingBox().inflate(0.4).intersects(t.getBoundingBox())) {
                        hit = true;
                        m.doHurtTarget(t);
                        climb();
                    } else if (--timer <= 0 || m.horizontalCollision || m.verticalCollision) climb();
                }
                default -> {
                    Vec3 away = flat(m.position().subtract(t.position()));
                    if (away == Vec3.ZERO) away = new Vec3(1, 0, 0);
                    m.getMoveControl().setWantedPosition(m.getX() + away.x * 3, t.getY() + 3.5, m.getZ() + away.z * 3, 1.0);
                    if (--timer <= 0) {
                        phase = 0;
                        timer = 40 + m.getRandom().nextInt(40);
                        angle = Math.atan2(m.getZ() - t.getZ(), m.getX() - t.getX());
                    }
                }
            }
        }

        private void climb() {
            phase = 2;
            timer = 16;
        }

        /** A point on its circle round the target, lower and tighter where the cave is low or narrow. */
        private Vec3 circleSpot(LivingEntity t) {
            for (double h = 3.5; h >= 1.5; h -= 1.0) {
                for (double r = 4.5; r >= 2.5; r -= 1.0) {
                    Vec3 p = new Vec3(t.getX() + Math.cos(angle) * r, t.getY() + h, t.getZ() + Math.sin(angle) * r);
                    if (m.level().noCollision(m, m.getBoundingBox().move(p.subtract(m.position())))) return p;
                }
            }
            return t.position().add(0, 1.5, 0);
        }
    }

    // ================================================================ lunger

    /**
     * Lungers: strike from further off than a sword reaches (the deepkin's spear, the coilfang's neck) along a strip
     * shown on the ground, or leap in with both scythes (the hive reaver).
     */
    static final class Lunge extends Goal {
        private final GateMonsterEntity m;
        private final float reach;
        private final int windupTicks;
        private final boolean leap;
        private long next = 30;
        private int windup;
        private int flight;
        private Vec3 dir = Vec3.ZERO;
        private boolean hit;

        Lunge(GateMonsterEntity m, float reach, int windupTicks, boolean leap) {
            this.m = m;
            this.reach = reach;
            this.windupTicks = windupTicks;
            this.leap = leap;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        private float length() {
            return reach + m.getBbWidth() / 2;
        }

        @Override
        public boolean canUse() {
            LivingEntity t = m.getTarget();
            if (m.tickCount < next || t == null || !t.isAlive() || !m.onGround() || !m.getSensing().hasLineOfSight(t)) return false;
            double d = m.distanceTo(t);
            return leap ? d > 3 && d < 9 : d > 1.6 && d < length();
        }

        @Override
        public boolean canContinueToUse() {
            return (windup > 0 || flight > 0) && m.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            LivingEntity t = m.getTarget();
            windup = windupTicks;
            flight = 0;
            hit = false;
            m.getNavigation().stop();
            dir = t == null ? forward(m) : flat(t.position().subtract(m.position()));
            if (dir == Vec3.ZERO) dir = forward(m);
            m.triggerAnim("attack", leap ? "special" : "attack");
            m.playSound(switch (m.kind()) {
                case COILFANG -> SoundEvents.CAT_HISS;
                case HIVE_REAVER -> SoundEvents.SPIDER_AMBIENT;
                default -> SoundEvents.DROWNED_SHOOT;
            }, 1f, m.getVoicePitch());
            if (m.level() instanceof ServerLevel s) {
                float len = leap && t != null ? (float) m.distanceTo(t) + 1.5f : length() + 0.4f;
                EnemyFx.telegraphLine(s, m.position(), dir, len, leap ? 1.6f : 1.1f, windupTicks, m.affinity().color);
            }
        }

        @Override
        public void stop() {
            windup = 0;
            flight = 0;
            next = m.tickCount + (leap ? 90 : 55) + m.getRandom().nextInt(40);
        }

        @Override
        public void tick() {
            LivingEntity t = m.getTarget();
            if (windup > 0) {
                if (t != null && windup > 2) {
                    m.getLookControl().setLookAt(t, 30, 30);
                    Vec3 d = flat(t.position().subtract(m.position()));
                    if (d != Vec3.ZERO) dir = d;
                }
                m.setDeltaMovement(m.getDeltaMovement().multiply(0.2, 1, 0.2));
                if (--windup > 0) return;
                face(m, dir);
                if (leap) {
                    double d = t == null ? 5 : Math.min(8, m.distanceTo(t));
                    double push = 0.35 + d * 0.09;
                    m.setDeltaMovement(dir.x * push, 0.42, dir.z * push);
                    m.hasImpulse = true;
                    flight = 18;
                } else strikeLine();
                return;
            }
            if (flight <= 0) return;
            flight--;
            if (!hit && m.level() instanceof ServerLevel s) {
                for (LivingEntity e : s.getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(0.5), e -> foe(m, e))) {
                    hit = true;
                    m.triggerAnim("attack", "attack");
                    if (e.hurt(m.damageSources().mobAttack(m), damage(m) * 1.3f)) {
                        e.push(dir.x * 0.6, 0.25, dir.z * 0.6);
                        e.hurtMarked = true;
                        EnemyHooks.affinityHit(m, e, 1.2f);
                        m.onStrike(e);
                    }
                    break;
                }
            }
            if (flight < 14 && m.onGround()) flight = 0;
        }

        /** Everything on the strip in front of it, out to its reach. */
        private void strikeLine() {
            Vec3 from = m.position().add(0, m.getBbHeight() * 0.5, 0);
            double len = length();
            float dmg = damage(m) * 1.15f;
            for (LivingEntity e : foesNear(m, m.position(), len + 1)) {
                Vec3 rel = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(from);
                double along = rel.x * dir.x + rel.z * dir.z;
                double side = Math.abs(rel.x * dir.z - rel.z * dir.x);
                if (along < -0.3 || along > len + e.getBbWidth() / 2 || side > 0.7 + e.getBbWidth() / 2 || Math.abs(rel.y) > 2) continue;
                if (e.hurt(m.damageSources().mobAttack(m), dmg)) {
                    e.push(dir.x * 0.4, 0.15, dir.z * 0.4);
                    e.hurtMarked = true;
                    EnemyHooks.affinityHit(m, e, 1f);
                    m.onStrike(e);
                }
            }
            if (m.level() instanceof ServerLevel s) {
                for (int i = 1; i <= 4; i++) {
                    Vec3 p = from.add(dir.scale(len * i / 4));
                    s.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    // ================================================================ haunt

    /**
     * Haunts: hold still, gather, and let it go. The wailing wraith's wail saps the strength of everyone near; the
     * static drifter's discharge shocks and throws them back.
     */
    static final class Pulse extends Goal {
        private final GateMonsterEntity m;
        private final int windupTicks;
        private final float radius;
        private long next = 60;
        private int windup;

        Pulse(GateMonsterEntity m, int windupTicks, float radius) {
            this.m = m;
            this.windupTicks = windupTicks;
            this.radius = radius;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private boolean wraith() {
            return m.kind() == MonsterKind.WAILING_WRAITH;
        }

        private int color() {
            if (m.affinity() != Affinity.NONE) return m.affinity().color;
            return wraith() ? 0xC8FFE1 : 0x78FFFF;
        }

        @Override
        public boolean canUse() {
            LivingEntity t = m.getTarget();
            return m.tickCount >= next && t != null && t.isAlive() && horizontal(t.position(), m.position()) < radius + 1
                    && m.getSensing().hasLineOfSight(t);
        }

        @Override
        public boolean canContinueToUse() {
            return windup > 0 && m.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            windup = windupTicks;
            m.getNavigation().stop();
            m.triggerAnim("attack", "special");
            m.playSound(wraith() ? SoundEvents.EVOKER_PREPARE_ATTACK : SoundEvents.BEACON_POWER_SELECT, 1f, wraith() ? 0.6f : 1.6f);
            if (m.level() instanceof ServerLevel s) EnemyFx.telegraphCircle(s, ground(m), radius, windupTicks, color());
        }

        @Override
        public void stop() {
            windup = 0;
            next = m.tickCount + 110 + m.getRandom().nextInt(60);
        }

        @Override
        public void tick() {
            LivingEntity t = m.getTarget();
            if (t != null) m.getLookControl().setLookAt(t, 30, 30);
            m.setDeltaMovement(m.getDeltaMovement().scale(0.5));
            if (--windup > 0 || !(m.level() instanceof ServerLevel s)) return;
            Vec3 at = ground(m);
            for (LivingEntity e : foesNear(m, at, radius)) {
                if (wraith()) {
                    e.hurt(m.damageSources().indirectMagic(m, m), damage(m) * 0.6f);
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0), m);
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0), m);
                    EnemyHooks.affinityHit(m, e, 0.8f);
                } else if (e.hurt(m.damageSources().mobAttack(m), damage(m) * 0.9f)) {
                    Vec3 push = flat(e.position().subtract(at)).scale(0.6);
                    e.push(push.x, 0.2, push.z);
                    e.hurtMarked = true;
                    if (m.affinity() == Affinity.NONE) Affinity.STORM.onHit(m, e, 12);
                    else EnemyHooks.affinityHit(m, e, 1f);
                }
            }
            Vec3 c = m.position().add(0, m.getBbHeight() * 0.5, 0);
            for (int i = 0; i < 32; i++) {
                double a = i * Mth.TWO_PI / 32;
                s.sendParticles(wraith() ? ParticleTypes.SCULK_SOUL : ParticleTypes.ELECTRIC_SPARK,
                        c.x + Math.cos(a) * 0.8, c.y, c.z + Math.sin(a) * 0.8, 0, Math.cos(a), 0.05, Math.sin(a), wraith() ? 0.25 : 0.6);
            }
            EnemyFx.burst(s, c, color(), radius * 0.8f);
            if (wraith()) m.playSound(SoundEvents.GHAST_SCREAM, 0.7f, 0.5f);
            else m.playSound(SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6f, 1.7f);
        }
    }

    // ================================================================ turret

    /** Rooted spitters: never move; turn, swell and spit a barb of their element; bite whatever comes too close. */
    static final class Spit extends Goal {
        private final GateMonsterEntity m;
        private long next = 30;
        private int windup = -1;
        private boolean bite;

        Spit(GateMonsterEntity m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = m.getTarget();
            return t != null && t.isAlive() && m.distanceTo(t) < 20;
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
            LivingEntity t = m.getTarget();
            if (t == null) return;
            m.getLookControl().setLookAt(t, 30, 30);
            if (windup >= 0) {
                if (--windup > 0) return;
                windup = -1;
                if (bite) {
                    if (m.isWithinMeleeAttackRange(t)) m.strike(t);
                    next = m.tickCount + 20;
                } else {
                    spit(t);
                    next = m.tickCount + 34 + m.getRandom().nextInt(20);
                }
                return;
            }
            if (m.tickCount < next) return;
            if (m.isWithinMeleeAttackRange(t)) {
                bite = true;
                windup = 8;
                m.triggerAnim("attack", "attack");
            } else if (m.getSensing().hasLineOfSight(t) && m.distanceTo(t) < 16) {
                bite = false;
                windup = 9;
                m.triggerAnim("attack", "attack");
            }
        }

        private void spit(LivingEntity t) {
            if (!(m.level() instanceof ServerLevel s)) return;
            Vec3 from = m.position().add(0, m.getBbHeight() * 0.8, 0).add(forward(m).scale(m.getBbWidth() * 0.4));
            Vec3 aim = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(from).normalize();
            ElementOrbEntity orb = ElementOrbEntity.shoot(s, m, from, aim, 0.8f, damage(m), m.affinity() == Affinity.NONE ? Affinity.VENOM : m.affinity(), 0.12f, t);
            if (orb != null) orb.withSize(0.3f);
            m.playSound(SoundEvents.LLAMA_SPIT, 1f, 0.7f);
        }
    }
}
