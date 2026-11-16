package com.beyondsolo.gate;

import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.combat.Statuses;
import com.beyondsolo.enemy.Affinity;
import com.beyondsolo.enemy.ElementOrbEntity;
import com.beyondsolo.enemy.Enemies;
import com.beyondsolo.enemy.EnemyFx;
import com.beyondsolo.enemy.EnemyModule;
import com.beyondsolo.enemy.EnemyTraits;
import com.beyondsolo.enemy.GateMonsterEntity;
import com.beyondsolo.enemy.MonsterKind;
import com.beyondsolo.enemy.Variant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every boss's attacks. A move is an animation (in the boss's model) and a script run on each of its ticks: it warns
 * on the floor ({@link EnemyFx} telegraphs), then hits when the animation lands. Bosses pick a move that fits how
 * far away their target is; the ones that call help in have a long cooldown.
 *
 * <table>
 *   <tr><th>Boss</th><th>Moves</th></tr>
 *   <tr><td>Brute</td><td>swipe (front), slam (all round)</td></tr>
 *   <tr><td>Broodmother</td><td>bite, venom spray (cone, poison), brood (young), leap (lands where you stood)</td></tr>
 *   <tr><td>Alpha</td><td>bite, pounce, howl (hounds join)</td></tr>
 *   <tr><td>Knight</td><td>three-strike cleave, charge (a line), rising blades (lines out of the floor)</td></tr>
 *   <tr><td>Magus</td><td>bolt volley, sigils (circles under every player), blink, summon acolytes</td></tr>
 *   <tr><td>Wyrm</td><td>fire breath (cone, held), tail sweep (all round), wing buffet (throws you back)</td></tr>
 * </table>
 */
public final class BossMoves {
    /** What a move remembers between its ticks. */
    public static final class State {
        Vec3 dir = Vec3.ZERO;
        Vec3 spot = Vec3.ZERO;
        final List<Vec3> spots = new ArrayList<>();
        final List<Vec3> dirs = new ArrayList<>();
        final Set<Integer> hit = new HashSet<>();

        void reset(@Nullable LivingEntity target) {
            spots.clear();
            dirs.clear();
            hit.clear();
            spot = target != null ? target.position() : Vec3.ZERO;
            dir = Vec3.ZERO;
        }
    }

    public interface Action {
        void tick(GateBossEntity b, @Nullable LivingEntity target, int t, State s);
    }

    /**
     * @param anim     the animation's short name in the boss's model
     * @param length   ticks
     * @param minRange its target must be at least this far (from the boss's edge)
     * @param maxRange ... and at most this far
     * @param summon   calls help in (long cooldown)
     * @param mobile   the move moves the boss itself (don't stop it)
     */
    public record Move(String anim, int length, float minRange, float maxRange, int weight, boolean summon, boolean mobile, Action action) {}

    private static final Map<BossKind, List<Move>> MOVES = new EnumMap<>(BossKind.class);

    static {
        MOVES.put(BossKind.BRUTE, List.of(
                new Move("swipe", 20, 0, 4.4f, 6, false, false, (b, t, i, s) -> {
                    if (i == 0) sound(b, SoundEvents.IRON_GOLEM_ATTACK, 1.2f, 0.5f);
                    if (i == 9) {
                        b.hitAround(4.2f, 1f, true);
                        sound(b, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.5f);
                    }
                }),
                new Move("slam", 30, 0, 4.4f, 4, false, false, (b, t, i, s) -> {
                    if (i == 0) {
                        sound(b, SoundEvents.WARDEN_ATTACK_IMPACT, 1.2f, 0.5f);
                        circle(b, b.position(), 4.6f + b.getBbWidth() / 2, 14);
                    }
                    if (i == 14) b.shockwave(4.6f, 1.25f, true);
                })));

        MOVES.put(BossKind.BROODMOTHER, List.of(
                new Move("bite", 20, 0, 3.8f, 6, false, false, (b, t, i, s) -> {
                    if (i == 0) {
                        s.dir = face(b, t);
                        cone(b, s.dir, 4.6f, 55, 9);
                        sound(b, SoundEvents.SPIDER_AMBIENT, 1.5f, 0.5f);
                    }
                    if (i == 9) {
                        for (LivingEntity e : inCone(b, b.position(), s.dir, 4.6f + b.getBbWidth() / 2, 55)) b.strike(e, 1f, s.dir.scale(0.7), 0.3);
                        sound(b, SoundEvents.PLAYER_ATTACK_STRONG, 1.2f, 0.6f);
                    }
                }),
                new Move("spray", 32, 1.5f, 9, 4, false, false, (b, t, i, s) -> {
                    if (i == 0) {
                        s.dir = face(b, t);
                        cone(b, s.dir, 8.5f, 35, 8);
                        sound(b, SoundEvents.SPIDER_STEP, 1.5f, 0.4f);
                    }
                    if (i >= 8 && i <= 26) {
                        sprayParticles(b, s.dir, 8.5f, 35, 0x8CE850);
                        if (i % 3 == 2) {
                            for (LivingEntity e : inCone(b, b.position(), s.dir, 8.5f + b.getBbWidth() / 2, 35)) {
                                e.invulnerableTime = 0;
                                if (b.strike(e, 0.22f, Vec3.ZERO, 0)) Statuses.apply(e, DamageElement.POISON, 10, b);
                            }
                            sound(b, SoundEvents.SLIME_SQUISH, 1f, 0.5f);
                        }
                    }
                }),
                new Move("brood", 40, 0, 30, 3, true, false, (b, t, i, s) -> {
                    if (i == 0) sound(b, SoundEvents.TURTLE_EGG_CRACK, 1.5f, 0.5f);
                    if (i == 20) {
                        int n = 2 + (b.isEnraged() ? 1 : 0) + (b.rank().tier() >= 4 ? 1 : 0);
                        summon(b, MonsterKind.BROODLING, Affinity.VENOM, n, 6);
                        sound(b, SoundEvents.SLIME_BLOCK_BREAK, 1.5f, 0.6f);
                    }
                }),
                new Move("leap", 28, 5, 16, 3, false, true, (b, t, i, s) -> {
                    if (i == 0) circle(b, s.spot, 3.6f, 20);
                    if (i == 7) launch(b, s.spot, 13);
                    if (i == 20) b.shockwaveAt(b.position(), 3.6f, 1.3f, true);
                })));

        MOVES.put(BossKind.ALPHA, List.of(
                new Move("bite", 16, 0, 3.4f, 6, false, false, (b, t, i, s) -> {
                    if (i == 0) {
                        s.dir = face(b, t);
                        sound(b, SoundEvents.WOLF_GROWL, 1.5f, 0.5f);
                    }
                    if (i == 7) {
                        for (LivingEntity e : inCone(b, b.position(), s.dir, 3.8f + b.getBbWidth() / 2, 55)) b.strike(e, 1f, s.dir.scale(0.6), 0.25);
                        sound(b, SoundEvents.PLAYER_ATTACK_STRONG, 1.2f, 0.7f);
                    }
                }),
                new Move("pounce", 24, 4, 13, 4, false, true, (b, t, i, s) -> {
                    if (i == 0) {
                        circle(b, s.spot, 3f, 17);
                        sound(b, SoundEvents.WOLF_GROWL, 1.5f, 0.4f);
                    }
                    if (i == 6) launch(b, s.spot, 11);
                    if (i == 17) b.shockwaveAt(b.position(), 3f, 1.2f, true);
                }),
                new Move("howl", 48, 0, 30, 2, true, false, (b, t, i, s) -> {
                    if (i == 10) sound(b, SoundEvents.WOLF_HOWL, 3f, 0.6f);
                    if (i == 14) {
                        summon(b, MonsterKind.GLOOMFANG, Affinity.FROST, 2 + (b.isEnraged() ? 1 : 0), 5);
                        b.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0));
                        if (b.level() instanceof ServerLevel sl) EnemyFx.burst(sl, b.position().add(0, 2, 0), Affinity.FROST.color, 4f);
                    }
                })));

        MOVES.put(BossKind.KNIGHT, List.of(
                new Move("cleave", 40, 0, 4.6f, 6, false, false, (b, t, i, s) -> {
                    if (i == 0 || i == 12) {
                        s.dir = face(b, t);
                        cone(b, s.dir, 5f, i == 0 ? 60 : 80, 9);
                    }
                    if (i == 9 || i == 19) {
                        for (LivingEntity e : inCone(b, b.position(), s.dir, 5f + b.getBbWidth() / 2, i == 9 ? 60 : 80)) b.strike(e, 0.8f, s.dir.scale(0.6), 0.2);
                        sound(b, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.6f);
                    }
                    if (i == 22) circle(b, b.position(), 4.4f, 9);
                    if (i == 31) b.shockwave(4.4f, 1.3f, true);
                }),
                new Move("charge", 28, 4, 14, 4, false, true, (b, t, i, s) -> {
                    if (i == 0) {
                        s.dir = face(b, t);
                        line(b, b.position(), s.dir, 12, 2.4f, 7);
                        sound(b, SoundEvents.WITHER_SKELETON_AMBIENT, 1.5f, 0.5f);
                    }
                    if (i >= 7 && i <= 21) {
                        b.setDeltaMovement(s.dir.x * 0.85, b.getDeltaMovement().y, s.dir.z * 0.85);
                        faceDir(b, s.dir);
                        for (LivingEntity e : b.level().getEntitiesOfClass(LivingEntity.class, b.getBoundingBox().inflate(0.7), b::canHit)) {
                            if (s.hit.add(e.getId())) b.strike(e, 1.4f, s.dir.scale(1.3), 0.45);
                        }
                        if (i % 3 == 0) sound(b, SoundEvents.RAVAGER_STEP, 1.2f, 0.7f);
                    }
                    if (i == 22) b.setDeltaMovement(Vec3.ZERO);
                }),
                new Move("rising", 36, 2, 16, 4, false, false, (b, t, i, s) -> {
                    if (i == 0) {
                        Vec3 d = face(b, t);
                        s.dirs.add(d);
                        if (b.isEnraged()) {
                            s.dirs.add(d.yRot(25 * Mth.DEG_TO_RAD));
                            s.dirs.add(d.yRot(-25 * Mth.DEG_TO_RAD));
                        }
                        for (Vec3 dir : s.dirs) line(b, b.position(), dir, 15, 1.8f, 16);
                    }
                    if (i == 16) {
                        sound(b, SoundEvents.EVOKER_FANGS_ATTACK, 2f, 0.6f);
                        b.shockwave(2.5f, 0f, false);
                        for (Vec3 dir : s.dirs) {
                            for (int k = 1; k <= 15; k++) {
                                Vec3 p = b.position().add(dir.scale(k));
                                if (k % 2 == 1 && b.level() instanceof ServerLevel sl) EnemyFx.pillar(sl, p, b.fxColor(), 0.7f, 3.2f);
                                for (LivingEntity e : b.level().getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(1.0, 2, 1.0), b::canHit)) {
                                    if (s.hit.add(e.getId())) b.strike(e, 1.15f, Vec3.ZERO, 0.75);
                                }
                            }
                        }
                    }
                })));

        MOVES.put(BossKind.MAGUS, List.of(
                new Move("cast", 24, 2, 22, 6, false, false, (b, t, i, s) -> {
                    if (i == 0) sound(b, SoundEvents.EVOKER_PREPARE_ATTACK, 1.5f, 1.2f);
                    if (i == 11 && t != null && b.level() instanceof ServerLevel sl) {
                        int n = b.isEnraged() ? 5 : 3;
                        Vec3 from = b.position().add(0, b.getBbHeight() * 0.62, 0);
                        Vec3 aim = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(from).normalize();
                        for (int k = 0; k < n; k++) {
                            float yaw = (k - (n - 1) / 2f) * 12f;
                            ElementOrbEntity orb = ElementOrbEntity.shoot(sl, b, from, aim.yRot(yaw * Mth.DEG_TO_RAD), 0.7f, b.damage() * 0.65f,
                                    b.kind().affinity, 0.25f, t);
                            if (orb != null) orb.withBuildup(b.element(), b.buildup() * 0.4f).withSize(0.5f);
                        }
                        sound(b, SoundEvents.EVOKER_CAST_SPELL, 1.5f, 1.1f);
                    }
                }),
                new Move("sigil", 36, 0, 24, 4, false, false, (b, t, i, s) -> {
                    if (i == 0) {
                        for (Player p : b.level().getEntitiesOfClass(Player.class, b.getBoundingBox().inflate(24), b::canHit)) s.spots.add(p.position());
                        if (t != null && !(t instanceof Player)) s.spots.add(t.position());
                        if (s.spots.isEmpty()) s.spots.add(s.spot);
                        if (b.isEnraged() && t != null) s.spots.add(t.position().add(b.getRandom().nextGaussian() * 3, 0, b.getRandom().nextGaussian() * 3));
                        for (Vec3 p : s.spots) circle(b, p, 2.7f, 24);
                        sound(b, SoundEvents.EVOKER_PREPARE_SUMMON, 1.5f, 1.3f);
                    }
                    if (i == 24) {
                        for (Vec3 p : s.spots) {
                            if (b.level() instanceof ServerLevel sl) EnemyFx.pillar(sl, p, b.fxColor(), 2.4f, 7f);
                            for (LivingEntity e : b.level().getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(2.7, 2.5, 2.7), b::canHit)) {
                                if (e.position().subtract(p).horizontalDistance() <= 2.7 + e.getBbWidth() / 2 && s.hit.add(e.getId())) b.strike(e, 1.1f, Vec3.ZERO, 0.6);
                            }
                        }
                        sound(b, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.2f, 1.4f);
                    }
                }),
                new Move("blink", 16, 0, 4, 5, false, true, (b, t, i, s) -> {
                    if (i == 0 && b.level() instanceof ServerLevel sl) EnemyFx.burst(sl, b.position().add(0, 2, 0), b.fxColor(), 2.5f);
                    if (i == 7 && t != null) blinkAway(b, t);
                }),
                new Move("summon", 32, 0, 30, 2, true, false, (b, t, i, s) -> {
                    if (i == 0) sound(b, SoundEvents.EVOKER_PREPARE_SUMMON, 1.5f, 0.8f);
                    if (i == 14) {
                        summon(b, MonsterKind.HOLLOW_ACOLYTE, b.kind().affinity, 2, 3);
                        b.shockwave(3f, 0f, false);
                    }
                })));

        MOVES.put(BossKind.WYRM, List.of(
                new Move("breath", 52, 0, 9.5f, 5, false, false, (b, t, i, s) -> {
                    if (i == 0) {
                        s.dir = face(b, t);
                        cone(b, s.dir, 10f, 30, 16);
                        sound(b, SoundEvents.ENDER_DRAGON_GROWL, 1.5f, 1.2f);
                    }
                    if (i >= 16 && i <= 44) {
                        if (t != null) {
                            // it sweeps the breath slowly after its target
                            Vec3 want = t.position().subtract(b.position()).multiply(1, 0, 1).normalize();
                            s.dir = s.dir.add(want.subtract(s.dir).scale(0.04)).normalize();
                        }
                        faceDir(b, s.dir);
                        breathParticles(b, s.dir, 10f, 30);
                        if (i % 4 == 0) {
                            for (LivingEntity e : inCone(b, b.position(), s.dir, 10f + b.getBbWidth() / 2, 30)) {
                                e.invulnerableTime = 0;
                                if (b.strike(e, 0.22f, Vec3.ZERO, 0)) e.igniteForSeconds(3);
                            }
                            sound(b, SoundEvents.BLAZE_SHOOT, 1.2f, 0.6f);
                        }
                    }
                }),
                new Move("tail_sweep", 30, 0, 5.5f, 4, false, false, (b, t, i, s) -> {
                    if (i == 0) circle(b, b.position(), 5.8f, 14);
                    if (i == 14) {
                        for (LivingEntity e : b.around(5.8f, -2)) {
                            Vec3 push = e.position().subtract(b.position()).multiply(1, 0, 1).normalize().scale(1.3);
                            b.strike(e, 1.1f, push, 0.5);
                        }
                        sound(b, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 2f, 0.4f);
                        b.shockwave(5.8f, 0f, false);
                    }
                }),
                new Move("buffet", 32, 0, 8, 3, false, false, (b, t, i, s) -> {
                    if (i == 0) {
                        s.dir = face(b, t);
                        cone(b, s.dir, 8.5f, 50, 15);
                    }
                    if (i == 15) {
                        for (LivingEntity e : inCone(b, b.position(), s.dir, 8.5f + b.getBbWidth() / 2, 50)) {
                            if (b.strike(e, 0.6f, s.dir.scale(2.1), 0.55)) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                        }
                        if (b.level() instanceof ServerLevel sl) {
                            for (int k = 0; k < 40; k++) {
                                Vec3 p = b.position().add(s.dir.scale(2 + b.getRandom().nextDouble() * 6)).add(b.getRandom().nextGaussian() * 1.5, 0.5 + b.getRandom().nextDouble() * 2, b.getRandom().nextGaussian() * 1.5);
                                sl.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 1, s.dir.x, 0, s.dir.z, 0.4);
                            }
                        }
                        sound(b, SoundEvents.ENDER_DRAGON_FLAP, 2f, 0.6f);
                    }
                })));
    }

    public static List<Move> movesFor(BossKind kind) {
        return MOVES.getOrDefault(kind, List.of());
    }

    /** The short names of a kind's attack animations. */
    public static List<String> anims(BossKind kind) {
        List<String> out = new ArrayList<>();
        for (Move m : movesFor(kind)) out.add(m.anim());
        return out;
    }

    /** Picks a move that fits the distance; summons only every 20 s (12 s enraged). */
    @Nullable
    static Move choose(GateBossEntity b, LivingEntity target, double dist) {
        long now = b.level().getGameTime();
        boolean canSummon = now - b.lastSummon > (b.isEnraged() ? 240 : 400);
        List<Move> fits = new ArrayList<>();
        int total = 0;
        for (Move m : movesFor(b.kind())) {
            if (dist < m.minRange() || dist > m.maxRange()) continue;
            if (m.summon() && !canSummon) continue;
            fits.add(m);
            total += m.weight();
        }
        if (fits.isEmpty()) return null;
        int roll = b.getRandom().nextInt(total);
        for (Move m : fits) {
            roll -= m.weight();
            if (roll < 0) {
                if (m.summon()) b.lastSummon = now;
                return m;
            }
        }
        return fits.get(0);
    }

    /** Between moves: close in (the magus keeps its distance instead). */
    static void approach(GateBossEntity b, LivingEntity target, double dist) {
        double speed = b.isEnraged() ? 1.25 : 1.0;
        if (b.kind() == BossKind.MAGUS) {
            if (dist > 11) b.getNavigation().moveTo(target, speed);
            else if (dist < 5) {
                Vec3 away = b.position().subtract(target.position()).multiply(1, 0, 1).normalize().scale(5);
                b.getNavigation().moveTo(b.getX() + away.x, b.getY(), b.getZ() + away.z, speed * 1.1);
            } else b.getNavigation().stop();
            return;
        }
        double reach = b.kind() == BossKind.BROODMOTHER || b.kind() == BossKind.WYRM ? 3.0 : 3.2;
        if (dist > reach) b.getNavigation().moveTo(target, speed);
        else b.getNavigation().stop();
    }

    // ------------------------------------------------------------------ helpers

    private static void sound(GateBossEntity b, SoundEvent sound, float volume, float pitch) {
        b.level().playSound(null, b.blockPosition(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    /** Turns to face the target and returns the horizontal direction to it. */
    private static Vec3 face(GateBossEntity b, @Nullable LivingEntity t) {
        Vec3 d = t != null ? t.position().subtract(b.position()).multiply(1, 0, 1) : Vec3.directionFromRotation(0, b.getYRot());
        if (d.lengthSqr() < 1e-4) d = Vec3.directionFromRotation(0, b.getYRot());
        d = d.normalize();
        faceDir(b, d);
        return d;
    }

    private static void faceDir(GateBossEntity b, Vec3 d) {
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        b.setYRot(yaw);
        b.setYHeadRot(yaw);
        b.yBodyRot = yaw;
    }

    private static void circle(GateBossEntity b, Vec3 at, float r, int ticks) {
        if (b.level() instanceof ServerLevel sl) EnemyFx.telegraphCircle(sl, at, r, ticks, b.fxColor());
    }

    private static void cone(GateBossEntity b, Vec3 dir, float length, float halfDeg, int ticks) {
        if (b.level() instanceof ServerLevel sl) EnemyFx.telegraphCone(sl, b.position(), dir, length + b.getBbWidth() / 2, halfDeg, ticks, b.fxColor());
    }

    private static void line(GateBossEntity b, Vec3 from, Vec3 dir, float length, float width, int ticks) {
        if (b.level() instanceof ServerLevel sl) EnemyFx.telegraphLine(sl, from, dir, length, width, ticks, b.fxColor());
    }

    static List<LivingEntity> inCone(GateBossEntity b, Vec3 from, Vec3 dir, float range, float halfDeg) {
        double cos = Math.cos(halfDeg * Mth.DEG_TO_RAD);
        return b.level().getEntitiesOfClass(LivingEntity.class, new AABB(from, from).inflate(range, 3, range), e -> {
            if (!b.canHit(e)) return false;
            Vec3 to = e.position().subtract(from).multiply(1, 0, 1);
            double d = to.length();
            if (d > range + e.getBbWidth() / 2) return false;
            return d < b.getBbWidth() / 2 + 0.5 || to.normalize().dot(dir) >= cos;
        });
    }

    /** Jumps so as to come down on {@code spot} after about {@code flight} ticks. */
    private static void launch(GateBossEntity b, Vec3 spot, int flight) {
        Vec3 d = spot.subtract(b.position()).multiply(1, 0, 1);
        double drag = (1 - 0.91) / (1 - Math.pow(0.91, flight));
        b.setDeltaMovement(d.x * drag, 0.62, d.z * drag);
        b.hurtMarked = true;
        faceDir(b, d.normalize());
        sound(b, SoundEvents.RAVAGER_STEP, 1.5f, 0.5f);
    }

    private static void blinkAway(GateBossEntity b, LivingEntity t) {
        if (!(b.level() instanceof ServerLevel sl)) return;
        for (int tries = 0; tries < 12; tries++) {
            double a = b.getRandom().nextDouble() * Math.PI * 2;
            double r = 7 + b.getRandom().nextDouble() * 3;
            BlockPos p = BlockPos.containing(t.getX() + Math.cos(a) * r, t.getY() + 3, t.getZ() + Math.sin(a) * r);
            for (int dy = 0; dy < 8; dy++, p = p.below()) {
                if (!sl.getBlockState(p.below()).isAir() && sl.getBlockState(p).isAir() && sl.getBlockState(p.above()).isAir()
                        && sl.getBlockState(p.above(2)).isAir() && sl.getBlockState(p.above(3)).isAir()) {
                    b.teleportTo(p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5);
                    faceDir(b, t.position().subtract(b.position()).multiply(1, 0, 1).normalize());
                    EnemyFx.burst(sl, b.position().add(0, 2, 0), b.fxColor(), 2.5f);
                    sound(b, SoundEvents.ENDERMAN_TELEPORT, 1.5f, 0.7f);
                    return;
                }
            }
        }
    }

    /** Calls in {@code count} creatures around the boss, keeping no more than {@code cap} of that kind near it. */
    private static void summon(GateBossEntity b, MonsterKind kind, Affinity affinity, int count, int cap) {
        if (!(b.level() instanceof ServerLevel sl)) return;
        int near = sl.getEntitiesOfClass(GateMonsterEntity.class, b.getBoundingBox().inflate(24), m -> m.kind() == kind && m.isAlive()).size();
        int n = Math.min(count, cap - near);
        int theme = b.gateId() != null ? b.theme().ordinal() : -1;
        for (int k = 0; k < n; k++) {
            double a = (k + b.getRandom().nextDouble() * 0.5) / Math.max(1, n) * Math.PI * 2;
            double r = b.getBbWidth() / 2 + 1.5;
            BlockPos p = BlockPos.containing(b.getX() + Math.cos(a) * r, b.getY(), b.getZ() + Math.sin(a) * r);
            for (int dy = 0; dy < 4 && !sl.getBlockState(p).isAir(); dy++) p = p.above();
            var m = Enemies.spawn(sl, b.rank(), EnemyModule.type(kind), p, new EnemyTraits(affinity, Variant.NORMAL, 0), theme);
            if (m != null && b.getTarget() != null) m.setTarget(b.getTarget());
        }
    }

    private static void sprayParticles(GateBossEntity b, Vec3 dir, float range, float halfDeg, int color) {
        if (!(b.level() instanceof ServerLevel sl)) return;
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f), 1.6f);
        for (int k = 0; k < 10; k++) {
            double ang = (b.getRandom().nextDouble() * 2 - 1) * halfDeg * Mth.DEG_TO_RAD;
            double d = b.getBbWidth() / 2 + b.getRandom().nextDouble() * range;
            Vec3 p = b.position().add(dir.yRot((float) ang).scale(d)).add(0, 0.6 + b.getRandom().nextDouble() * 1.2, 0);
            sl.sendParticles(dust, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0);
            if (k % 3 == 0) sl.sendParticles(ParticleTypes.ITEM_SLIME, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.05);
        }
    }

    private static void breathParticles(GateBossEntity b, Vec3 dir, float range, float halfDeg) {
        if (!(b.level() instanceof ServerLevel sl)) return;
        // where the wyrm's open jaws are while its neck is thrust out (see the breath animation in gen_enemy_assets.py)
        Vec3 mouth = b.position().add(dir.scale(b.getBbWidth() * 1.05)).add(0, b.getBbHeight() * 0.36, 0);
        for (int k = 0; k < 8; k++) {
            double ang = (b.getRandom().nextDouble() * 2 - 1) * halfDeg * Mth.DEG_TO_RAD;
            Vec3 v = dir.yRot((float) ang).add(0, -0.12, 0).normalize();
            double speed = 0.35 + b.getRandom().nextDouble() * 0.45;
            sl.sendParticles(ParticleTypes.FLAME, mouth.x, mouth.y, mouth.z, 0, v.x, v.y, v.z, speed);
            if (k % 3 == 0) sl.sendParticles(ParticleTypes.LARGE_SMOKE, mouth.x, mouth.y, mouth.z, 0, v.x, v.y + 0.1, v.z, speed * 0.6);
        }
    }

    private BossMoves() {}
}
