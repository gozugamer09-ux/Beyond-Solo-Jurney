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
 * Aurenthe, Ruler of the Verdict: a knight of white gold under a crown of light, a greatsword of light in his hands,
 * two great wings folded at his back. He judges.
 * <ul>
 *   <li><b>Sanctum, the Hall of Verdicts</b>: every few seconds a pillar of light falls on one of his foes inside it
 *   (drawn on the ground first).</li>
 *   <li><b>I. The Herald</b>: the greatsword swept and driven, an advance that runs his prey through, and pillars of
 *   light on every foe at once.</li>
 *   <li><b>II. The Scales</b>: the scales (a band round him, then the ground at his feet: out, then in), spears of
 *   light thrown at his foes, and his descent: up on his wings and down on his prey.</li>
 *   <li><b>III. The Final Verdict</b>, his authority: a cross of light cut through the land round him, then the
 *   cross turned, then the ground at his feet. Stand between the arms.</li>
 * </ul>
 */
public class VerdictRuler extends RulerBoss {
    static final int CLEAVE = 1, ADVANCE = 2, PILLAR = 3, SCALES = 4, SPEAR = 5, DESCENT = 6, VERDICT = 7, BRAND = 8;

    public VerdictRuler(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Override
    public WorldBossKind kind() {
        return WorldBossKind.VERDICT;
    }

    @Override
    protected String authority() {
        return "final_verdict";
    }

    @Override
    protected void sanctumTick(ServerLevel level, List<LivingEntity> foes, int second) {
        if (second % 4 != 0) return;
        LivingEntity e = foes.get(random.nextInt(foes.size()));
        circleStrike(ground(e.position()), 2.2f, 24, 0.6f, BRAND);
    }

    // ================================================================ moves

    @Override
    protected List<Move> buildMoves() {
        List<Move> m = new ArrayList<>();
        m.add(new Move("cleave", 30, 0, 7, 5, 1, 0, false, this::cleave));
        m.add(new Move("advance", 34, 3, 16, 4, 1, 80, true, this::advance));
        m.add(new Move("pillars", 36, 0, 32, 4, 1, 60, false, this::pillars));
        m.add(new Move("scales", 52, 0, 12, 3, 2, 140, false, this::scales));
        m.add(new Move("spears", 46, 4, 34, 3, 2, 120, false, this::spears));
        m.add(new Move("descent", 60, 6, 28, 3, 2, 160, true, this::descent));
        m.add(new Move("final_verdict", 140, 0, 64, 1, 3, 900, false, this::finalVerdict));
        return m;
    }

    private void cleave(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            s.dir = face(target);
            strike(Shape.CONE, ground(position()), s.dir, 75, 8f, 0, 14, 1.0f, CLEAVE, fxColor());
        }
        if (t == 12) sound(SoundEvents.PLAYER_ATTACK_SWEEP, 2f, 0.6f);
    }

    /** Drawn back, then a step that runs the length of the strip and the blade through whoever stands on it. */
    private void advance(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            s.dir = face(target);
            float len = target != null ? (float) Mth.clamp(horizontalTo(target) + 2, 6, 14) : 12f;
            s.spots.add(position());
            s.spot = ground(position().add(s.dir.scale(len - 1.5)));
            strike(Shape.LINE, ground(position()), s.dir, 2.8f, len, 0, 14, 1.1f, ADVANCE, fxColor());
            sound(SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 2f, 0.6f);
        }
        if (t >= 14 && t <= 18) {
            float k = (t - 14) / 4f;
            Vec3 at = s.spots.get(0).lerp(s.spot, k * (2 - k));
            noPhysics = true;
            setPos(at.x, at.y, at.z);
            setDeltaMovement(Vec3.ZERO);
            if (t == 14) sound(SoundEvents.TRIDENT_RIPTIDE_1.value(), 2f, 0.8f);
            if (t == 18) noPhysics = false;
        }
    }

    /** His sword raised: a pillar of light on each of his foes at once. */
    private void pillars(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            face(target);
            sound(SoundEvents.BEACON_POWER_SELECT, 3f, 0.6f);
            int n = 0;
            for (LivingEntity e : fighters(32)) {
                if (n++ >= 6) break;
                circleStrike(ground(e.position()), 2.6f, 22, 0.9f, PILLAR);
            }
        }
    }

    /** The scales: a band of light round him, then the ground at his feet. Out, then in. */
    private void scales(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            face(target);
            s.spot = ground(position());
            strike(Shape.RING, s.spot, Vec3.ZERO, 12f, 0, 5f, 20, 1.0f, SCALES, fxColor());
            sound(SoundEvents.BELL_BLOCK, 3f, 0.8f);
        }
        if (t == 20) {
            strike(Shape.CIRCLE, s.spot, Vec3.ZERO, 5.5f, 0, 0, 20, 1.2f, SCALES, fxColor());
            sound(SoundEvents.BELL_BLOCK, 3f, 0.6f);
        }
    }

    /** Spears of light made in his left hand and thrown at his foes, three. */
    private void spears(@Nullable LivingEntity target, int t, MoveState s) {
        if (!(level() instanceof ServerLevel server)) return;
        if (t == 0) {
            face(target);
            for (LivingEntity e : fighters(34)) {
                if (s.spots.size() >= 3) break;
                s.spots.add(e.position());
            }
            while (s.spots.size() < 3) {
                double a = random.nextDouble() * Math.PI * 2, d = 6 + random.nextDouble() * 8;
                s.spots.add(position().add(Math.cos(a) * d, 0, Math.sin(a) * d));
            }
        }
        if ((t == 10 || t == 18 || t == 26) && s.n < 3) {
            Vec3 spot = ground(s.spots.get(s.n).add(random.nextGaussian() * 1.2, 0, random.nextGaussian() * 1.2));
            Vec3 from = position().add(0, getBbHeight() + 0.6, 0);
            BossProjectile.launch(server, BossProjectile.Kind.LIGHT_SPEAR, from, spot.add(0, 0.5, 0), 22, 3, 1.3f, fxColor());
            circleStrike(spot, 2.6f, 22, 1.0f, SPEAR);
            sound(SoundEvents.TRIDENT_THROW.value(), 2f, 0.6f);
            s.n++;
        }
    }

    /** Up on his wings, a moment hanging there, and down on his prey with the sword. */
    private void descent(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            s.dir = face(target);
            s.spots.add(position());
            sound(SoundEvents.ENDER_DRAGON_FLAP, 4f, 0.8f);
        }
        Vec3 top = s.spots.get(0).add(0, 12, 0);
        if (t >= 2 && t <= 18) {
            Vec3 at = s.spots.get(0).add(0, 12 * (t - 2) / 16.0, 0);
            noPhysics = true;
            setPos(at.x, at.y, at.z);
            setDeltaMovement(Vec3.ZERO);
        }
        if (t > 18 && t < 30) {
            setPos(top.x, top.y, top.z);
            setDeltaMovement(Vec3.ZERO);
        }
        if (t == 22) {
            s.spot = ground(target != null ? target.position() : ahead(s.dir, 10));
            circleStrike(s.spot, 6f, 18, 1.4f, DESCENT);
            Vec3 d = s.spot.subtract(position()).multiply(1, 0, 1);
            if (d.lengthSqr() > 1e-4) faceDir(d.normalize());
        }
        if (t >= 30 && t <= 40) {
            float k = (t - 30) / 10f;
            Vec3 at = top.lerp(s.spot, k * k);
            setPos(at.x, at.y, at.z);
            setDeltaMovement(Vec3.ZERO);
        }
        if (t == 40) noPhysics = false;
    }

    /**
     * The Final Verdict: a cross of light cut out from him along the land, then the cross turned an eighth, then the
     * ground at his feet. The arms are drawn long before they fall: stand between them, then step onto where they were.
     */
    private void finalVerdict(@Nullable LivingEntity target, int t, MoveState s) {
        if (!(level() instanceof ServerLevel server)) return;
        if (t == 0) {
            s.spot = ground(position());
            s.dir = face(target);
            announceAuthority(server, 110);
            sound(SoundEvents.BEACON_ACTIVATE, 5f, 0.4f);
            for (int i = 0; i < 4; i++) {
                strike(Shape.LINE, s.spot, s.dir.yRot(i * Mth.HALF_PI), 5f, 34f, 0, 50, 1.6f, VERDICT, fxColor());
            }
        }
        if (t == 50) {
            for (int i = 0; i < 4; i++) {
                strike(Shape.LINE, s.spot, s.dir.yRot(i * Mth.HALF_PI + Mth.PI / 4), 5f, 34f, 0, 30, 1.6f, VERDICT, fxColor());
            }
            sound(SoundEvents.BELL_BLOCK, 4f, 0.5f);
        }
        if (t == 80) {
            strike(Shape.CIRCLE, s.spot, Vec3.ZERO, 8f, 0, 0, 30, 1.8f, DESCENT, fxColor());
            sound(SoundEvents.BELL_BLOCK, 4f, 0.4f);
        }
        if (t % 10 == 0 && t < 120) server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + getBbHeight(), getZ(), 16, 1, 1, 1, 0.2);
    }

    // ================================================================ when blows land

    @Override
    protected void landed(ServerLevel level, Strike s, List<LivingEntity> caught) {
        switch (s.tag()) {
            case PILLAR, BRAND, SPEAR -> {
                WorldBossFx.pillar(level, s.at(), s.tag() == BRAND ? 1.6f : 2.4f, 28f, fxColor());
                soundAt(s.at(), SoundEvents.BEACON_POWER_SELECT, s.tag() == BRAND ? 1.5f : 2.5f, 1.4f);
            }
            case SCALES -> soundAt(s.at(), SoundEvents.ANVIL_LAND, 2f, 1.6f);
            case DESCENT -> {
                WorldBossFx.pillar(level, s.at(), 3.4f, 40f, fxColor());
                WorldBossFx.quake(level, s.at(), 9f, fxColor());
                wave(s.at(), 13, 1.6f, 22, 0.6f, ADVANCE);
                dust(s.at(), 5f, 50);
                shake(1.0f, 56);
                soundAt(s.at(), SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.7f);
            }
            case VERDICT -> {
                for (int i = 1; i <= 4; i++) WorldBossFx.burst(level, s.at().add(s.dir().scale(i * 8)), 3f, fxColor());
                if (random.nextInt(2) == 0) soundAt(s.at(), SoundEvents.BEACON_POWER_SELECT, 3f, Mth.nextFloat(random, 0.6f, 0.9f));
            }
            default -> {
            }
        }
    }
}
