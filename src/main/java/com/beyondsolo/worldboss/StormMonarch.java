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
 * Thyrax, Monarch of Storms: a winged lord in storm-dark plate with a glaive of lightning. He fights from the air as
 * much as on the ground, and the sky fights with him.
 * <ul>
 *   <li><b>Domain, the Tempest</b>: lightning falls on the hunters inside, again and again (each bolt drawn on the
 *   ground first).</li>
 *   <li><b>I. Thunderhead</b>: glaive slashes and thrusts, bolts called down on every hunter at once, and a beat of his
 *   wings that throws them back.</li>
 *   <li><b>II. Eye of the Storm</b>: storm orbs that drift onto hunters and burst, and the skyfall: he rises into the
 *   air and dives onto his prey.</li>
 *   <li><b>III. Judgment</b>, his authority: the land round him is cut into squares and the lightning takes every other
 *   one, then the rest. Keep moving.</li>
 * </ul>
 * Lightning leaves no fire here; it only shocks (a moment of weakness and slowness).
 */
public class StormMonarch extends MonarchBoss {
    static final int SLASH = 1, BOLT = 2, GUST = 3, ORB = 4, SKYFALL = 5, JUDGMENT = 6, TEMPEST = 7;

    private int tempest = 60;

    public StormMonarch(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Override
    public WorldBossKind kind() {
        return WorldBossKind.STORMS;
    }

    @Override
    protected List<Habitat> armyHabitats() {
        return List.of(Habitat.STORM, Habitat.DEMON);
    }

    @Override
    protected String authority() {
        return "judgment";
    }

    @Override
    protected double preferred() {
        return 3.0;
    }

    @Override
    protected void domainTick(ServerLevel level, LivingEntity e) {
        // the Tempest strikes on its own clock (below)
    }

    @Override
    protected void afflict(LivingEntity e, float scale) {
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0), this);
        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 0), this);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel server) || !isAlive() || leaving() || introTicks() > 0 || !landed()) return;
        // the Tempest: a bolt drawn under a hunter in his domain every few seconds (faster as he angers)
        if (--tempest <= 0) {
            tempest = phase() >= 3 ? 40 : phase() == 2 ? 55 : 70;
            List<LivingEntity> in = new java.util.ArrayList<>(near(position(), DOMAIN));
            // a hunter monarch's domain keeps the storm off whoever it shelters
            in.removeIf(com.beyondsolo.monarch.MonarchDomain::shelters);
            // and so does a ruler's sanctum
            in.removeIf(com.beyondsolo.ruler.Rulers::shelters);
            if (!in.isEmpty()) {
                LivingEntity e = in.get(random.nextInt(in.size()));
                circleStrike(ground(e.position()), 2.4f, 24, 0.6f, TEMPEST);
            }
        }
    }

    // ================================================================ moves

    @Override
    protected List<Move> buildMoves() {
        List<Move> m = new ArrayList<>();
        m.add(new Move("glaive", 28, 0, 7, 5, 1, 0, false, this::glaive));
        m.add(new Move("skewer", 26, 0, 9, 4, 1, 0, false, this::skewer));
        m.add(new Move("bolt_call", 36, 0, 32, 4, 1, 60, false, this::boltCall));
        m.add(new Move("wing_gust", 30, 0, 10, 2, 1, 120, false, this::wingGust));
        m.add(new Move("storm_orbs", 50, 4, 34, 3, 2, 120, false, this::stormOrbs));
        m.add(new Move("skyfall", 64, 4, 28, 3, 2, 160, true, this::skyfall));
        m.add(new Move("judgment", 150, 0, 64, 1, 3, 900, false, this::judgment));
        return m;
    }

    private void glaive(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            s.dir = face(target);
            strike(Shape.CONE, ground(position()), s.dir, 80, 7f, 0, 14, 1.0f, SLASH, fxColor());
        }
        if (t == 12) sound(SoundEvents.PLAYER_ATTACK_SWEEP, 2f, 0.6f);
    }

    private void skewer(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            s.dir = face(target);
            strike(Shape.LINE, ground(position()), s.dir, 2.2f, 10, 0, 12, 1.0f, SLASH, fxColor());
        }
        if (t == 10) sound(SoundEvents.TRIDENT_THROW.value(), 2f, 0.7f);
    }

    private void boltCall(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            face(target);
            sound(SoundEvents.TRIDENT_THUNDER.value(), 3f, 1.2f);
            for (LivingEntity e : fighters(32)) circleStrike(ground(e.position()), 2.8f, 20, 0.9f, BOLT);
        }
    }

    private void wingGust(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            s.dir = face(target);
            strike(Shape.CONE, ground(position()), s.dir, 55, 12, 0, 16, 0.5f, GUST, fxColor());
        }
        if (t == 14) sound(SoundEvents.ENDER_DRAGON_FLAP, 4f, 0.7f);
    }

    private void stormOrbs(@Nullable LivingEntity target, int t, MoveState s) {
        if (!(level() instanceof ServerLevel server)) return;
        if (t == 0) {
            face(target);
            for (LivingEntity e : fighters(34)) s.spots.add(e.position());
            while (s.spots.size() < 3) {
                double a = random.nextDouble() * Math.PI * 2, d = 6 + random.nextDouble() * 10;
                s.spots.add(position().add(Math.cos(a) * d, 0, Math.sin(a) * d));
            }
        }
        if ((t == 8 || t == 16 || t == 24) && s.n < 3) {
            Vec3 spot = ground(s.spots.get(s.n).add(random.nextGaussian() * 1.5, 0, random.nextGaussian() * 1.5));
            Vec3 from = position().add(0, getBbHeight() + 1, 0);
            BossProjectile.launch(server, BossProjectile.Kind.STORM_ORB, from, spot.add(0, 0.8, 0), 34, 5, 1.2f, fxColor());
            circleStrike(spot, 3.2f, 34, 1.0f, ORB);
            sound(SoundEvents.BEACON_POWER_SELECT, 2f, 1.8f);
            s.n++;
        }
    }

    private void skyfall(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            s.dir = face(target);
            s.spots.add(position());
            sound(SoundEvents.ENDER_DRAGON_FLAP, 4f, 0.6f);
        }
        if (t >= 2 && t <= 20) {
            // up on his wings
            Vec3 at = s.spots.get(0).add(0, 14 * (t - 2) / 18.0, 0);
            noPhysics = true;
            setPos(at.x, at.y, at.z);
            setDeltaMovement(Vec3.ZERO);
        }
        if (t == 24) {
            s.spot = ground(target != null ? target.position() : ahead(s.dir, 10));
            circleStrike(s.spot, 6f, 20, 1.4f, SKYFALL);
            faceDir(s.spot.subtract(position()).multiply(1, 0, 1).normalize());
        }
        if (t >= 34 && t <= 44) {
            float k = (t - 34) / 10f;
            Vec3 top = s.spots.get(0).add(0, 14, 0);
            Vec3 at = top.lerp(s.spot, k * k);
            setPos(at.x, at.y, at.z);
            setDeltaMovement(Vec3.ZERO);
        }
        if (t > 20 && t < 34) {
            // he hangs in the air, choosing
            Vec3 top = s.spots.get(0).add(0, 14, 0);
            setPos(top.x, top.y, top.z);
            setDeltaMovement(Vec3.ZERO);
        }
        if (t == 44) noPhysics = false;
    }

    private void judgment(@Nullable LivingEntity target, int t, MoveState s) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 c = s.spot;
        if (t == 0) {
            s.spot = ground(position());
            c = s.spot;
            announceAuthority(server, 120);
            sound(SoundEvents.TRIDENT_THUNDER.value(), 5f, 0.5f);
            for (int i = -3; i <= 3; i++) {
                for (int j = -3; j <= 3; j++) {
                    if (((i + j) & 1) != 0) continue;
                    strike(Shape.CELL, ground(c.add(i * 6.0, 0, j * 6.0)), Vec3.ZERO, 3f, 0, 0, 50, 1.3f, JUDGMENT, fxColor());
                }
            }
        }
        if (t == 50) {
            for (int i = -3; i <= 3; i++) {
                for (int j = -3; j <= 3; j++) {
                    if (((i + j) & 1) == 0) continue;
                    strike(Shape.CELL, ground(c.add(i * 6.0, 0, j * 6.0)), Vec3.ZERO, 3f, 0, 0, 40, 1.3f, JUDGMENT, fxColor());
                }
            }
        }
        if (t == 92) circleStrike(c, 7f, 28, 1.8f, SKYFALL);
        if (t % 10 == 0 && t < 120) server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + getBbHeight(), getZ(), 20, 1, 1, 1, 0.3);
    }

    // ================================================================ when blows land

    @Override
    protected void landed(ServerLevel level, Strike s, List<LivingEntity> caught) {
        switch (s.tag()) {
            case BOLT, TEMPEST, ORB -> {
                WorldBossFx.bolt(level, s.at(), s.tag() == TEMPEST ? 0.8f : 1.1f, fxColor());
                soundAt(s.at(), SoundEvents.LIGHTNING_BOLT_IMPACT, s.tag() == TEMPEST ? 1.5f : 2.5f, 1.1f);
            }
            case GUST -> {
                for (LivingEntity e : caught) {
                    Vec3 away = e.position().subtract(position()).multiply(1, 0, 1).normalize();
                    e.push(away.x * 2.2, 0.6, away.z * 2.2);
                    e.hurtMarked = true;
                }
            }
            case SKYFALL -> {
                WorldBossFx.bolt(level, s.at(), 1.8f, fxColor());
                WorldBossFx.quake(level, s.at(), 9f, fxColor());
                wave(s.at(), 13, 1.6f, 22, 0.6f, BOLT);
                dust(s.at(), 5f, 50);
                shake(1.0f, 56);
                soundAt(s.at(), SoundEvents.LIGHTNING_BOLT_THUNDER, 4f, 0.8f);
            }
            case JUDGMENT -> {
                WorldBossFx.bolt(level, s.at(), 1.3f, fxColor());
                if (random.nextInt(3) == 0) soundAt(s.at(), SoundEvents.LIGHTNING_BOLT_IMPACT, 2.5f, Mth.nextFloat(random, 0.8f, 1.2f));
            }
            default -> {
            }
        }
    }
}
