package com.beyondsolo.worldboss;

import com.beyondsolo.enemy.Affinity;
import com.beyondsolo.enemy.Habitat;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Thurvok, the Horned Calamity: a beast the size of a house, four great horns and a back of bone plates. Where the
 * titan is slow, it charges: every charge is drawn on the ground first, then it comes down the line and throws
 * whoever is still on it.
 * <ul>
 *   <li><b>I. Stampede</b>: horn thrusts, a rearing trample that sends a wave out (jump it), a charge, a roar that
 *   throws hunters back and shakes them.</li>
 *   <li><b>II. Bloodrage</b>: red veins split its hide. It calls a pack of blood beasts to it, and gores three times in
 *   a row.</li>
 *   <li><b>III. Calamity</b>: it leaps onto its prey, and charges three times running, turning after each.</li>
 * </ul>
 */
public class BehemothBoss extends GiantBoss {
    static final int GORE = 1, TRAMPLE = 2, ROAR = 3, LEAP = 4, WAVE = 5;
    private static final double CHARGE_SPEED = 1.15;

    public BehemothBoss(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, WorldBossKind.BEHEMOTH.health)
                .add(Attributes.ATTACK_DAMAGE, WorldBossKind.BEHEMOTH.damage)
                .add(Attributes.ARMOR, 12)
                .add(Attributes.ARMOR_TOUGHNESS, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 64)
                .add(Attributes.STEP_HEIGHT, 2);
    }

    @Override
    public WorldBossKind kind() {
        return WorldBossKind.BEHEMOTH;
    }

    @Override
    protected double walkSpeed() {
        return 0.22;
    }

    @Override
    protected double reach() {
        return 3.0;
    }

    @Override
    public int fxColor() {
        return phase() >= 3 ? 0xFF1F3A : kind().color;
    }

    // ================================================================ moves

    @Override
    protected List<Move> buildMoves() {
        List<Move> m = new ArrayList<>();
        m.add(new Move("gore", 30, 0, 6, 5, 1, 0, false, this::gore));
        m.add(new Move("trample", 46, 0, 8, 4, 1, 0, false, this::trample));
        m.add(new Move("charge", 64, 7, 34, 4, 1, 100, true, (t, tick, s) -> charges(t, tick, s, 1)));
        m.add(new Move("roar", 40, 0, 12, 2, 1, 180, false, this::roar));
        m.add(new Move("bloodrage", 50, 0, 40, 3, 2, 420, false, this::bloodrage));
        m.add(new Move("gore_combo", 52, 0, 8, 4, 2, 60, false, this::goreCombo));
        m.add(new Move("leap", 60, 6, 26, 4, 3, 160, true, this::leap));
        m.add(new Move("triple_charge", 150, 7, 34, 3, 3, 280, true, (t, tick, s) -> charges(t, tick, s, 3)));
        return m;
    }

    private void gore(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            s.dir = face(target);
            strike(Shape.CONE, ground(position()), s.dir, 45, 8.5f, 0, 14, 1.0f, GORE, fxColor());
        }
        if (t == 12) sound(SoundEvents.RAVAGER_ATTACK, 3f, 0.6f);
    }

    private void goreCombo(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0 || t == 16 || t == 32) {
            s.dir = face(target);
            // alternately to the left and right of where its prey stands
            float swing = t == 16 ? 25 : t == 32 ? -25 : 0;
            Vec3 d = s.dir.yRot(swing * Mth.DEG_TO_RAD);
            strike(Shape.CONE, ground(position()), d, 50, 8.5f, 0, 12, 0.9f, GORE, fxColor());
        }
        if (t == 10 || t == 26 || t == 42) sound(SoundEvents.RAVAGER_ATTACK, 3f, 0.7f);
    }

    private void trample(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            s.dir = face(target);
            s.spot = ahead(s.dir, 3.5);
            circleStrike(s.spot, 5.5f, 18, 1.0f, TRAMPLE);
            sound(SoundEvents.RAVAGER_ROAR, 3f, 0.8f);
        }
    }

    private void roar(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            face(target);
            circleStrike(ground(position()), 10.5f, 20, 0.5f, ROAR);
        }
        if (t == 16) sound(SoundEvents.RAVAGER_ROAR, 5f, 0.45f);
    }

    private void bloodrage(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 4) sound(SoundEvents.WOLF_GROWL, 5f, 0.4f);
        if (t == 20 && level() instanceof ServerLevel server) {
            sound(SoundEvents.RAVAGER_ROAR, 5f, 0.4f);
            shake(0.6f, 48);
            MonarchArmy.call(server, this, position(), 3, Affinity.BLOOD, List.of(Habitat.BEAST), target);
        }
    }

    /** {@code count} charges, each drawn on the ground first, re-aimed at its prey after each. */
    private void charges(@Nullable LivingEntity target, int t, MoveState s, int count) {
        int each = 50;
        int k = t % each, n = t / each;
        if (n >= count) return;
        if (k == 0) {
            s.dir = face(target);
            double dist = target != null ? horizontalTo(target) : 20;
            double length = Mth.clamp(dist + 10, 16, 36);
            s.spot = position().add(s.dir.scale(length));
            s.hit.clear();
            if (level() instanceof ServerLevel server) WorldBossFx.line(server, ground(position()), s.dir, (float) length, 5, 18, fxColor());
            sound(SoundEvents.RAVAGER_ROAR, 3f, 0.7f);
        }
        if (k > 0 && k < 18) {
            // it paws the ground and lowers its horns
            if (k % 6 == 0) dust(position(), 2.5f, 10);
            if (level() instanceof ServerLevel server) settle(server);
            return;
        }
        if (k >= 18 && k < each - 2) {
            if (!stride(s.spot, CHARGE_SPEED, 2f)) {
                setDeltaMovement(0, 0, 0);
                return;
            }
            Vec3 front = position().add(s.dir.scale(getBbWidth() * 0.5));
            for (LivingEntity e : near(front, 3.2)) {
                if (!s.hit.add(e.getId())) continue;
                hit(e, 1.3f, new Vec3(-s.dir.z, 0, s.dir.x).scale(e.position().subtract(position()).dot(new Vec3(-s.dir.z, 0, s.dir.x)) >= 0 ? 1 : -1)
                        .add(s.dir), 1.6, 0.7);
            }
            if (k % 3 == 0) {
                dust(position(), 3f, 12);
                shake(0.2f, 24);
            }
            if (k % 8 == 0) sound(SoundEvents.RAVAGER_STEP, 3f, 0.5f);
        }
    }

    private void leap(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            s.dir = face(target);
            s.spot = ground(target != null ? target.position() : ahead(s.dir, 12));
            s.spots.add(position());
            circleStrike(s.spot, 7f, 32, 1.4f, LEAP);
            sound(SoundEvents.RAVAGER_ROAR, 4f, 0.6f);
        }
        if (t >= 8 && t <= 32) {
            // through the air, a high arc, landing as the strike does
            float k = (t - 8) / 24f;
            Vec3 from = s.spots.get(0);
            Vec3 at = from.lerp(s.spot, k).add(0, 10 * 4 * k * (1 - k), 0);
            setDeltaMovement(at.x - getX(), at.y - getY(), at.z - getZ());
        }
        if (t > 32) setDeltaMovement(0, 0, 0);
    }

    // ================================================================ when blows land

    @Override
    protected void landed(ServerLevel level, Strike s, List<LivingEntity> caught) {
        switch (s.tag()) {
            case GORE -> {
                for (LivingEntity e : caught) {
                    e.push(0, 0.8, 0);
                    e.hurtMarked = true;
                }
            }
            case TRAMPLE -> {
                dust(s.at(), 4.5f, 40);
                WorldBossFx.quake(level, s.at(), 6f, fxColor());
                wave(s.at(), 11, 1.6f, 20, 0.5f, WAVE);
                shake(0.6f, 40);
                soundAt(s.at(), SoundEvents.GENERIC_EXPLODE.value(), 2f, 0.6f);
            }
            case ROAR -> {
                for (LivingEntity e : caught) {
                    Vec3 away = e.position().subtract(position()).multiply(1, 0, 1).normalize();
                    e.push(away.x * 1.4, 0.4, away.z * 1.4);
                    e.hurtMarked = true;
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1), this);
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0), this);
                }
                WorldBossFx.wave(level, s.at(), 11, 2.2f, 10, fxColor());
            }
            case LEAP -> {
                dust(s.at(), 6f, 60);
                WorldBossFx.quake(level, s.at(), 10f, fxColor());
                wave(s.at(), 13, 1.8f, 24, 0.6f, WAVE);
                shake(1.1f, 56);
                soundAt(s.at(), SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.5f);
            }
            default -> {
            }
        }
    }

    // ================================================================ entrance, phases, death

    @Override
    protected void introTick(ServerLevel level, int t) {
        if (t % 4 == 0 && t < 50) {
            dust(position(), 5f, 24);
            shake(0.25f, 64);
        }
        if (t == 0) WorldBossFx.quake(level, ground(position()), 8f, fxColor());
        if (t == 56) {
            sound(SoundEvents.RAVAGER_ROAR, 5f, 0.4f);
            shake(0.9f, 80);
        }
    }

    @Override
    protected void onPhase(ServerLevel level, int phase) {
        sound(SoundEvents.RAVAGER_ROAR, 5f, phase == 3 ? 0.35f : 0.45f);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, getX(), getY() + getBbHeight() * 0.6, getZ(), 30, 2, 1, 2, 0.2);
        if (phase == 3) WorldBossFx.quake(level, ground(position()), 12f, fxColor());
    }

    private int stepTimer;

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && isAlive() && !leaving() && moved() > 0.0004 && ++stepTimer >= 12) {
            stepTimer = 0;
            sound(SoundEvents.RAVAGER_STEP, 2f, 0.5f);
            shake(0.08f, 20);
        }
    }

    @Override
    protected void deathTick(ServerLevel level, int t) {
        if (t == 1) sound(SoundEvents.RAVAGER_DEATH, 5f, 0.45f);
        if (t == 30) {
            dust(position(), 5f, 50);
            shake(0.9f, 56);
        }
    }
}
