package com.beyondsolo.ruler;

import com.beyondsolo.worldboss.BossProjectile;
import com.beyondsolo.worldboss.WorldBossFx;
import com.beyondsolo.worldboss.WorldBossKind;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Caelistor, Ruler of a Thousand Wings: four wings of white steel and a lance of light. The sky is hers; she comes down
 * out of it to strike and goes back up into it.
 * <ul>
 *   <li><b>Sanctum, the Open Sky</b>: every few seconds a feather of light falls on one of her foes inside it (drawn on
 *   the ground first).</li>
 *   <li><b>I. The Spear</b>: the spear driven twice, one wing swept round and then the other, and a storm of feathers
 *   walked across each of her foes.</li>
 *   <li><b>II. The Gale</b>: the stoop (up and back, then down through her prey in a straight line) and the gale (a
 *   beat of all four wings that throws foes back, with feathers waiting where they land).</li>
 *   <li><b>III. A Thousand Wings</b>, her authority: she rises over the land, volleys fall on her foes wherever they
 *   stand, the shadows of her wings sweep across it, and she comes down last. Keep moving; stand in the strip between
 *   the shadows.</li>
 * </ul>
 */
public class WingsRuler extends RulerBoss {
    static final int LANCE = 1, SWEEP = 2, FEATHER = 3, DIVE = 4, GALE = 5, WINGS = 6, VOLLEY = 7, SKY = 8;

    public WingsRuler(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Override
    public WorldBossKind kind() {
        return WorldBossKind.WINGS;
    }

    @Override
    protected String authority() {
        return "thousand_wings";
    }

    @Override
    protected double preferred() {
        return 4.0;
    }

    @Override
    protected void sanctumTick(ServerLevel level, List<LivingEntity> foes, int second) {
        if (second % 3 != 0) return;
        LivingEntity e = foes.get(random.nextInt(foes.size()));
        Vec3 spot = ground(e.position());
        BossProjectile.launch(level, BossProjectile.Kind.FEATHER, spot.add(random.nextGaussian() * 2, 18, random.nextGaussian() * 2),
                spot.add(0, 0.3, 0), 20, 0, 0.9f, fxColor());
        circleStrike(spot, 1.8f, 20, 0.5f, SKY);
    }

    // ================================================================ moves

    @Override
    protected List<Move> buildMoves() {
        List<Move> m = new ArrayList<>();
        m.add(new Move("lance", 26, 0, 10, 5, 1, 0, false, this::lance));
        m.add(new Move("wing_sweep", 30, 0, 8, 4, 1, 60, false, this::wingSweep));
        m.add(new Move("feather_storm", 44, 4, 32, 4, 1, 100, false, this::featherStorm));
        m.add(new Move("dive", 50, 6, 30, 3, 2, 140, true, this::dive));
        m.add(new Move("gale", 40, 0, 14, 3, 2, 120, false, this::gale));
        m.add(new Move("thousand_wings", 150, 0, 64, 1, 3, 900, true, this::thousandWings));
        return m;
    }

    /** The spear driven forward twice: once where her prey stands, once where it stepped to. */
    private void lance(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0 || t == 12) {
            s.dir = face(target);
            strike(Shape.LINE, ground(position()), s.dir, 2.2f, 11f, 0, t == 0 ? 12 : 10, 1.0f, LANCE, fxColor());
        }
        if (t == 10) sound(SoundEvents.TRIDENT_THROW.value(), 2f, 0.9f);
        if (t == 20) sound(SoundEvents.TRIDENT_THROW.value(), 2f, 1.1f);
    }

    /** One wing swept round before her, then the other. */
    private void wingSweep(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            s.dir = face(target);
            strike(Shape.CONE, ground(position()), s.dir.yRot(0.6f), 50, 9f, 0, 12, 0.9f, SWEEP, fxColor());
        }
        if (t == 10) sound(SoundEvents.ENDER_DRAGON_FLAP, 3f, 1.1f);
        if (t == 12) strike(Shape.CONE, ground(position()), s.dir.yRot(-0.6f), 50, 9f, 0, 12, 0.9f, SWEEP, fxColor());
        if (t == 22) sound(SoundEvents.ENDER_DRAGON_FLAP, 3f, 0.9f);
    }

    /** Her wings thrown forward: feathers of light walk across each of her foes, three falls in a row through where they stand. */
    private void featherStorm(@Nullable LivingEntity target, int t, MoveState s) {
        if (!(level() instanceof ServerLevel server)) return;
        if (t == 0) {
            s.dir = face(target);
            sound(SoundEvents.ENDER_DRAGON_FLAP, 3f, 1.3f);
            for (LivingEntity e : fighters(32)) {
                if (s.spots.size() >= 4) break;
                s.spots.add(e.position());
            }
            if (s.spots.isEmpty()) s.spots.add(ahead(s.dir, 8));
        }
        if (t == 8 || t == 16 || t == 24) {
            int row = (t - 8) / 8;
            for (Vec3 at : s.spots) {
                Vec3 d = at.subtract(position()).multiply(1, 0, 1);
                d = d.lengthSqr() < 1e-4 ? s.dir : d.normalize();
                Vec3 spot = ground(at.add(d.scale((row - 1) * 3.2)));
                BossProjectile.launch(server, BossProjectile.Kind.FEATHER, position().add(0, getBbHeight() + 1, 0), spot.add(0, 0.3, 0), 16, 3,
                        1.0f, fxColor());
                circleStrike(spot, 2.0f, 16, 0.7f, FEATHER);
            }
            sound(SoundEvents.ARROW_SHOOT, 2f, 1.4f);
        }
    }

    /** The stoop: up and back on her wings, then down through her prey in a straight line and on past it. */
    private void dive(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            s.dir = face(target);
            s.spots.add(position());
            sound(SoundEvents.ENDER_DRAGON_FLAP, 4f, 1.0f);
        }
        Vec3 start = s.spots.get(0);
        Vec3 top = start.add(s.dir.scale(-4)).add(0, 10, 0);
        if (t >= 2 && t <= 14) {
            float k = (t - 2) / 12f;
            hold(start.lerp(top, k * (2 - k)));
        }
        if (t > 14 && t < 24) hold(top);
        if (t == 16) {
            // the line she comes down along: from under her, through her prey, and on
            Vec3 aim = target != null ? target.position() : ahead(s.dir, 10);
            Vec3 d = aim.subtract(top).multiply(1, 0, 1);
            if (d.lengthSqr() > 1e-4) {
                s.dir = d.normalize();
                faceDir(s.dir);
            }
            float len = (float) Mth.clamp(d.length() + 8, 10, 30);
            Vec3 from = ground(new Vec3(top.x, start.y, top.z));
            s.spot = ground(from.add(s.dir.scale(len)));
            strike(Shape.LINE, from, s.dir, 3.6f, len, 0, 16, 1.3f, DIVE, fxColor());
        }
        if (t >= 24 && t <= 32) {
            float k = (t - 24) / 8f;
            Vec3 at = top.lerp(s.spot, k);
            // steep at first, flattening out near the ground
            hold(new Vec3(at.x, Mth.lerp(1 - (1 - k) * (1 - k), top.y, s.spot.y), at.z));
            if (t == 24) sound(SoundEvents.PHANTOM_SWOOP, 4f, 0.7f);
        }
        if (t == 32) noPhysics = false;
    }

    /** A beat of all four wings: whoever stands before her is thrown back, and feathers wait where they land. */
    private void gale(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            s.dir = face(target);
            strike(Shape.CONE, ground(position()), s.dir, 60, 14f, 0, 16, 0.5f, GALE, fxColor());
        }
        if (t == 14) sound(SoundEvents.ENDER_DRAGON_FLAP, 4f, 0.6f);
    }

    /**
     * A Thousand Wings: she rises over the land and it fills with feathers. Volleys fall on each of her foes where they
     * stand (keep moving), the shadows of her wings sweep across it three times (each drawn long before it falls: stand
     * in the strip between them, or well out), and she comes down on her prey at the last.
     */
    private void thousandWings(@Nullable LivingEntity target, int t, MoveState s) {
        if (!(level() instanceof ServerLevel server)) return;
        if (t == 0) {
            s.spot = ground(position());
            s.spots.add(position());
            s.dir = face(target);
            announceAuthority(server, 130);
            sound(SoundEvents.BEACON_ACTIVATE, 5f, 0.7f);
            sound(SoundEvents.ENDER_DRAGON_FLAP, 5f, 0.5f);
        }
        Vec3 start = s.spots.get(0), top = start.add(0, 16, 0);
        if (t >= 2 && t <= 20) {
            float k = (t - 2) / 18f;
            hold(start.lerp(top, k * (2 - k)));
        }
        if (t > 20 && t < 124) hold(top);
        // the volleys: on each of her foes, every few ticks
        if (t >= 22 && t <= 100 && t % 6 == 4) {
            int n = 0;
            for (LivingEntity e : fighters(40)) {
                if (n++ >= 6) break;
                Vec3 spot = ground(e.position());
                BossProjectile.launch(server, BossProjectile.Kind.FEATHER, top.add(random.nextGaussian(), 0, random.nextGaussian()), spot.add(0, 0.3, 0),
                        16, 0, 1.0f, fxColor());
                circleStrike(spot, 2.2f, 16, 0.8f, VOLLEY);
            }
        }
        // the shadows of her wings: two broad bands across the land, a safe strip between them, turned each time
        if (t == 24 || t == 54 || t == 84) {
            int i = (t - 24) / 30;
            Vec3 across = s.dir.yRot(i * Mth.PI / 3);
            Vec3 side = new Vec3(-across.z, 0, across.x);
            for (int j = -1; j <= 1; j += 2) {
                Vec3 from = ground(s.spot.add(side.scale(j * 7)).add(across.scale(-24)));
                strike(Shape.LINE, from, across, 7f, 48f, 0, 28, 1.4f, WINGS, fxColor());
            }
            sound(SoundEvents.ENDER_DRAGON_FLAP, 5f, 0.7f);
        }
        // and she comes down where her prey is
        if (t == 112) {
            LivingEntity e = target != null && target.isAlive() ? target : nearestFoe();
            s.spot = ground(e != null ? e.position() : start);
            circleStrike(s.spot, 7f, 22, 1.8f, DIVE);
            Vec3 d = s.spot.subtract(top).multiply(1, 0, 1);
            if (d.lengthSqr() > 1e-4) faceDir(d.normalize());
        }
        if (t >= 124 && t <= 134) {
            float k = (t - 124) / 10f;
            hold(top.lerp(s.spot, k * k));
        }
        if (t == 134) noPhysics = false;
        if (t % 10 == 0 && t < 120) server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + getBbHeight(), getZ(), 16, 2, 1, 2, 0.2);
    }

    // ================================================================ when blows land

    @Override
    protected void landed(ServerLevel level, Strike s, List<LivingEntity> caught) {
        switch (s.tag()) {
            case LANCE -> WorldBossFx.burst(level, s.at().add(s.dir().scale(s.length())).add(0, 1, 0), 1.6f, fxColor());
            case SWEEP -> soundAt(s.at(), SoundEvents.PLAYER_ATTACK_SWEEP, 2f, 0.8f);
            case FEATHER, VOLLEY, SKY -> {
                level.sendParticles(ParticleTypes.END_ROD, s.at().x, s.at().y + 0.4, s.at().z, s.tag() == SKY ? 6 : 10, s.size() * 0.4, 0.2,
                        s.size() * 0.4, 0.05);
                if (s.tag() != VOLLEY || random.nextInt(3) == 0) {
                    soundAt(s.at(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.6f, Mth.nextFloat(random, 1.2f, 1.6f));
                }
            }
            case GALE -> {
                for (LivingEntity e : caught) {
                    Vec3 away = e.position().subtract(position()).multiply(1, 0, 1);
                    away = away.lengthSqr() < 1e-4 ? s.dir() : away.normalize();
                    e.push(away.x * 2.4, 0.7, away.z * 2.4);
                    e.hurtMarked = true;
                    // where the wind sets them down, the feathers are waiting
                    Vec3 spot = ground(e.position().add(away.scale(7)));
                    BossProjectile.launch(level, BossProjectile.Kind.FEATHER, position().add(0, getBbHeight() + 1, 0), spot.add(0, 0.3, 0), 20, 4,
                            1.0f, fxColor());
                    circleStrike(spot, 2.4f, 20, 0.9f, FEATHER);
                }
                dust(ground(position()).add(s.dir().scale(4)), 4f, 30);
            }
            case DIVE -> {
                Vec3 end = s.shape() == Shape.LINE ? ground(s.at().add(s.dir().scale(s.length()))) : s.at();
                if (s.shape() == Shape.LINE) {
                    for (int i = 1; i <= 3; i++) WorldBossFx.burst(level, s.at().add(s.dir().scale(s.length() * i / 4f)).add(0, 0.8, 0), 2f, fxColor());
                }
                WorldBossFx.pillar(level, end, 3f, 36f, fxColor());
                WorldBossFx.quake(level, end, 8f, fxColor());
                wave(end, 12, 1.6f, 22, 0.6f, SWEEP);
                dust(end, 5f, 50);
                shake(1.0f, 56);
                soundAt(end, SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.8f);
            }
            case WINGS -> {
                for (int i = 1; i <= 5; i++) WorldBossFx.burst(level, s.at().add(s.dir().scale(i * 8)).add(0, 0.5, 0), 2.6f, fxColor());
                soundAt(s.at().add(s.dir().scale(24)), SoundEvents.ENDER_DRAGON_FLAP, 4f, 0.5f);
            }
            default -> {
            }
        }
    }
}
