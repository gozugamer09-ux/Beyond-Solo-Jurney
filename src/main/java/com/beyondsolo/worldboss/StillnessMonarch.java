package com.beyondsolo.worldboss;

import com.beyondsolo.enemy.Habitat;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Veyrith, Monarch of Stillness: a queen of ice with a crystal spear. Where she rules, everything slows, and what stays
 * too long freezes.
 * <ul>
 *   <li><b>Domain, Stillness</b>: hunters inside are slowed, and the cold creeps into them (the frost on the screen);
 *   frozen through, it bites.</li>
 *   <li><b>I. The Still Court</b>: spear thrusts and sweeps, a blink through the snow to strike from behind, ice spears
 *   thrown at those who keep away.</li>
 *   <li><b>II. Winter's Edge</b>: the ground round her freezes in squares (stand between them), and the frozen lance, a
 *   long line of ice.</li>
 *   <li><b>III. Absolute Zero</b>, her authority: she rises, and everything near her freezes solid. Get out of the
 *   circle before it closes.</li>
 * </ul>
 * Her blows don't burn or bleed: they chill and slow.
 */
public class StillnessMonarch extends MonarchBoss {
    static final int THRUST = 1, SWEEP = 2, SPEAR = 3, FIELD = 4, LANCE = 5, ZERO = 6;

    public StillnessMonarch(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Override
    public WorldBossKind kind() {
        return WorldBossKind.STILLNESS;
    }

    @Override
    protected List<Habitat> armyHabitats() {
        return List.of(Habitat.FROST, Habitat.KNIGHT);
    }

    @Override
    protected String authority() {
        return "absolute_zero";
    }

    @Override
    protected void domainTick(ServerLevel level, LivingEntity e) {
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0, true, false), this);
        // the cold creeps in; standing in it long enough freezes through (vanilla's freezing does the rest)
        e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze() + 40, e.getTicksFrozen() + (phase() >= 3 ? 24 : 14)));
    }

    @Override
    protected void afflict(LivingEntity e, float scale) {
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, scale >= 1.2f ? 2 : 1), this);
        e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze() + 40, e.getTicksFrozen() + Math.round(40 * scale)));
    }

    // ================================================================ moves

    @Override
    protected List<Move> buildMoves() {
        List<Move> m = new ArrayList<>();
        m.add(new Move("thrust", 26, 0, 7, 5, 1, 0, false, this::thrust));
        m.add(new Move("sweep", 30, 0, 5, 4, 1, 0, false, this::sweep));
        m.add(new Move("blink", 30, 3, 30, 3, 1, 100, true, this::blink));
        m.add(new Move("ice_spears", 44, 6, 40, 4, 1, 70, false, this::iceSpears));
        m.add(new Move("glacial_field", 56, 0, 20, 3, 2, 170, false, this::glacialField));
        m.add(new Move("frozen_lance", 56, 0, 30, 3, 2, 140, false, this::frozenLance));
        m.add(new Move("absolute_zero", 130, 0, 64, 1, 3, 900, false, this::absoluteZero));
        return m;
    }

    private void thrust(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            s.dir = face(target);
            strike(Shape.LINE, ground(position()), s.dir, 2.4f, 9, 0, 12, 1.0f, THRUST, fxColor());
        }
        if (t == 10) sound(SoundEvents.TRIDENT_THROW.value(), 2f, 0.8f);
    }

    private void sweep(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            s.dir = face(target);
            strike(Shape.CONE, ground(position()), s.dir, 65, 6.5f, 0, 14, 0.9f, SWEEP, fxColor());
        }
        if (t == 12) sound(SoundEvents.PLAYER_ATTACK_SWEEP, 2f, 0.7f);
    }

    private void blink(@Nullable LivingEntity target, int t, MoveState s) {
        if (!(level() instanceof ServerLevel server) || target == null) return;
        if (t == 0) {
            server.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + 2, getZ(), 60, 0.6, 1.2, 0.6, 0.08);
            sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 3f, 1.6f);
        }
        if (t == 6) {
            // out of the snow behind her prey
            Vec3 back = target.position().subtract(target.getLookAngle().multiply(1, 0, 1).normalize().scale(3.5));
            blinkTo(ground(back));
            server.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + 2, getZ(), 60, 0.6, 1.2, 0.6, 0.08);
            s.dir = face(target);
            strike(Shape.LINE, ground(position()), s.dir, 2.4f, 8, 0, 12, 1.1f, THRUST, fxColor());
        }
    }

    private void iceSpears(@Nullable LivingEntity target, int t, MoveState s) {
        if (!(level() instanceof ServerLevel server)) return;
        if (t == 0) {
            s.dir = face(target);
            for (LivingEntity e : fighters(40)) s.spots.add(e.position());
            if (s.spots.isEmpty() && target != null) s.spots.add(target.position());
        }
        if ((t == 10 || t == 18 || t == 26) && !s.spots.isEmpty()) {
            Vec3 aim = s.spots.get(s.n % s.spots.size());
            if (s.n > 0) aim = aim.add(random.nextGaussian() * 2.5, 0, random.nextGaussian() * 2.5);
            Vec3 spot = ground(aim);
            Vec3 hand = position().add(0, getBbHeight() * 0.85, 0);
            int flight = Mth.clamp((int) (hand.distanceTo(spot) / 1.4), 10, 26);
            BossProjectile.launch(server, BossProjectile.Kind.ICE_SPEAR, hand, spot.add(0, 0.3, 0), flight, 2, 1.4f, fxColor());
            circleStrike(spot, 2.4f, flight, 0.9f, SPEAR);
            sound(SoundEvents.TRIDENT_THROW.value(), 2f, 1.2f);
            s.n++;
        }
    }

    private void glacialField(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            face(target);
            sound(SoundEvents.GLASS_BREAK, 3f, 0.5f);
            // a checker of squares round her: every other one freezes
            Vec3 c = position();
            for (int i = -2; i <= 2; i++) {
                for (int j = -2; j <= 2; j++) {
                    if (((i + j) & 1) != 0) continue;
                    Vec3 at = ground(c.add(i * 5.0, 0, j * 5.0));
                    strike(Shape.CELL, at, Vec3.ZERO, 2.5f, 0, 0, 30, 1.0f, FIELD, fxColor());
                }
            }
        }
        if (t == 28) {
            Vec3 c = position();
            for (int i = -2; i <= 2; i++) {
                for (int j = -2; j <= 2; j++) {
                    if (((i + j) & 1) == 0) continue;
                    strike(Shape.CELL, ground(c.add(i * 5.0, 0, j * 5.0)), Vec3.ZERO, 2.5f, 0, 0, 26, 1.0f, FIELD, fxColor());
                }
            }
        }
    }

    private void frozenLance(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            s.dir = face(target);
            strike(Shape.LINE, ground(position()), s.dir, 3.2f, 30, 0, 26, 1.3f, LANCE, fxColor());
            sound(SoundEvents.BEACON_ACTIVATE, 3f, 1.5f);
        }
    }

    private void absoluteZero(@Nullable LivingEntity target, int t, MoveState s) {
        if (!(level() instanceof ServerLevel server)) return;
        if (t == 0) {
            announceAuthority(server, 100);
            circleStrike(ground(position()), 22f, 100, 2.4f, ZERO);
            sound(SoundEvents.BEACON_POWER_SELECT, 5f, 0.5f);
        }
        if (t < 100 && t % 4 == 0) {
            server.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + 2, getZ(), 30, 8, 2, 8, 0.02);
            if (t % 20 == 0) shake(0.3f, 48);
        }
    }

    // ================================================================ when blows land

    @Override
    protected void landed(ServerLevel level, Strike s, List<LivingEntity> caught) {
        switch (s.tag()) {
            case SPEAR -> WorldBossFx.burst(level, s.at().add(0, 0.5, 0), 2f, fxColor());
            case FIELD -> {
                level.sendParticles(ParticleTypes.SNOWFLAKE, s.at().x, s.at().y + 0.5, s.at().z, 20, 1.5, 0.4, 1.5, 0.05);
                WorldBossFx.pillar(level, s.at(), 1.6f, 2.5f, fxColor());
                soundAt(s.at(), SoundEvents.GLASS_BREAK, 1.2f, 0.8f);
            }
            case LANCE -> {
                for (int i = 0; i < 6; i++) {
                    Vec3 at = ground(s.at().add(s.dir().scale(4 + i * 4.5)));
                    WorldBossFx.pillar(level, at, 1.4f, 4f, fxColor());
                }
                soundAt(s.at().add(s.dir().scale(12)), SoundEvents.GLASS_BREAK, 3f, 0.5f);
            }
            case ZERO -> {
                WorldBossFx.burst(level, s.at().add(0, 2, 0), 14f, fxColor());
                WorldBossFx.quake(level, s.at(), 22f, fxColor());
                shake(1.4f, 80);
                soundAt(s.at(), SoundEvents.GLASS_BREAK, 5f, 0.3f);
                for (LivingEntity e : caught) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 9), this);
                    e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 70, 2), this);
                    e.setTicksFrozen(e.getTicksRequiredToFreeze() + 120);
                }
            }
            default -> {
            }
        }
    }
}
