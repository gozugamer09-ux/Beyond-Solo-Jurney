package com.beyondsolo.ruler;

import com.beyondsolo.worldboss.BossProjectile;
import com.beyondsolo.worldboss.WorldBossFx;
import com.beyondsolo.worldboss.WorldBossKind;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
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
 * Ithrael, Ruler of the Unseen Hand: a masked one in long robes who never touches the ground, and never needs to. He
 * keeps his distance; hands no one sees do his work.
 * <ul>
 *   <li><b>Sanctum, the Still Air</b>: every few seconds the air takes hold of one of his foes inside it (drawn on the
 *   ground first): lifted, and held a moment.</li>
 *   <li><b>I. The Hand</b>: the grasp (his prey lifted, then slammed down), the press (a square of air brought down
 *   before him), and stones torn out of the ground and thrown.</li>
 *   <li><b>II. The Weight</b>: the repulse (everything close thrown away from him) and the pull (a wide ring of the land
 *   drawn in to him, then the ground at his feet pressed flat: be near him when the ring falls, then get out).</li>
 *   <li><b>III. Unseen Dominion</b>, his authority: the land round him pressed flat ring after ring, outward from him
 *   (all drawn at once, each falling in turn), then everything near him at the last. Follow the rings in, then go back
 *   out.</li>
 * </ul>
 */
public class HandRuler extends RulerBoss {
    static final int GRASP = 1, CRUSH = 2, STONE = 3, REPEL = 4, PULL = 5, DOMINION = 6, SEIZE = 7, SLAM = 8;

    public HandRuler(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Override
    public WorldBossKind kind() {
        return WorldBossKind.HAND;
    }

    @Override
    protected String authority() {
        return "unseen_dominion";
    }

    @Override
    protected double preferred() {
        return 6.0;
    }

    @Override
    protected void sanctumTick(ServerLevel level, List<LivingEntity> foes, int second) {
        if (second % 5 != 0) return;
        LivingEntity e = foes.get(random.nextInt(foes.size()));
        circleStrike(ground(e.position()), 1.8f, 24, 0.5f, SEIZE);
    }

    // ================================================================ moves

    @Override
    protected List<Move> buildMoves() {
        List<Move> m = new ArrayList<>();
        m.add(new Move("grasp", 40, 0, 20, 5, 1, 0, false, this::grasp));
        m.add(new Move("crush", 36, 0, 10, 4, 1, 40, false, this::crush));
        m.add(new Move("hurl", 46, 6, 32, 4, 1, 100, false, this::hurl));
        m.add(new Move("repel", 30, 0, 8, 3, 2, 100, false, this::repel));
        m.add(new Move("pull", 44, 8, 34, 3, 2, 160, false, this::pull));
        m.add(new Move("unseen_dominion", 140, 0, 64, 1, 3, 900, false, this::unseenDominion));
        return m;
    }

    /** An unseen hand closes on his prey (drawn on the ground) and lifts whoever it catches, then slams them down. */
    private void grasp(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            s.dir = face(target);
            s.spot = ground(target != null ? target.position() : ahead(s.dir, 8));
            circleStrike(s.spot, 3.2f, 20, 0.6f, GRASP);
            sound(SoundEvents.EVOKER_PREPARE_ATTACK, 2.5f, 0.6f);
        }
        if (t == 32 && level() instanceof ServerLevel server) {
            // whoever the hand still holds is thrown down
            for (int id : s.hit) {
                if (!(server.getEntity(id) instanceof LivingEntity e) || !canHit(e)) continue;
                if (hit(e, 0.7f, Vec3.ZERO, 0, 0)) {
                    e.setDeltaMovement(e.getDeltaMovement().x * 0.2, -1.6, e.getDeltaMovement().z * 0.2);
                    e.hurtMarked = true;
                }
                dust(ground(e.position()), 2.5f, 20);
            }
            if (!s.hit.isEmpty()) sound(SoundEvents.ANVIL_LAND, 2f, 0.5f);
        }
    }

    /** The air before him comes down like a press: a square of it, drawn first. */
    private void crush(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            s.dir = face(target);
            s.spot = ahead(s.dir, 5.5);
            strike(Shape.CELL, s.spot, Vec3.ZERO, 4f, 0, 0, 18, 1.2f, CRUSH, fxColor());
            sound(SoundEvents.EVOKER_PREPARE_SUMMON, 2.5f, 0.6f);
        }
    }

    /** Stones torn out of the ground round him and thrown at his foes, three. */
    private void hurl(@Nullable LivingEntity target, int t, MoveState s) {
        if (!(level() instanceof ServerLevel server)) return;
        if (t == 0) {
            s.dir = face(target);
            for (LivingEntity e : fighters(34)) {
                if (s.spots.size() >= 3) break;
                s.spots.add(e.position());
            }
            while (s.spots.size() < 3) {
                double a = random.nextDouble() * Math.PI * 2, d = 6 + random.nextDouble() * 8;
                s.spots.add(position().add(Math.cos(a) * d, 0, Math.sin(a) * d));
            }
            dust(ground(position()), 4f, 40);
            sound(SoundEvents.STONE_BREAK, 3f, 0.5f);
        }
        if ((t == 12 || t == 20 || t == 28) && s.n < 3) {
            Vec3 spot = ground(s.spots.get(s.n).add(random.nextGaussian() * 1.2, 0, random.nextGaussian() * 1.2));
            // each stone hangs beside him before it flies
            Vec3 side = new Vec3(-s.dir.z, 0, s.dir.x).scale(s.n == 1 ? 0 : s.n == 0 ? -2.2 : 2.2);
            Vec3 from = position().add(side).add(0, getBbHeight() + 1.2, 0);
            BossProjectile.launch(server, BossProjectile.Kind.HURLED_STONE, from, spot.add(0, 0.6, 0), 22, 4, 1.4f, fxColor());
            circleStrike(spot, 2.8f, 22, 1.1f, STONE);
            sound(SoundEvents.ILLUSIONER_CAST_SPELL, 2f, 0.6f);
            s.n++;
        }
    }

    /** Everything close to him thrown away at once. */
    private void repel(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            face(target);
            circleStrike(ground(position()), 7f, 14, 0.7f, REPEL);
            sound(SoundEvents.WARDEN_SONIC_CHARGE, 2.5f, 1.2f);
        }
    }

    /**
     * The land round him drawn in: a wide ring of it falls first and drags whoever stands in it to him, then the ground
     * at his feet is pressed flat. Stand close when the ring falls, then get out.
     */
    private void pull(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            face(target);
            s.spot = ground(position());
            strike(Shape.RING, s.spot, Vec3.ZERO, 34f, 0, 8f, 16, 0.3f, PULL, fxColor());
            sound(SoundEvents.EVOKER_PREPARE_WOLOLO, 2.5f, 0.5f);
        }
        if (t == 16) {
            strike(Shape.CIRCLE, s.spot, Vec3.ZERO, 5.5f, 0, 0, 20, 1.3f, CRUSH, fxColor());
        }
    }

    /**
     * Unseen Dominion: the land round him pressed flat by hands no one sees, ring after ring outward from him (all drawn
     * at once, each falling in turn), then everything near him at the last. Step into a ring once it has fallen; when
     * the ground near him is drawn again, go back out past where the third ring fell.
     */
    private void unseenDominion(@Nullable LivingEntity target, int t, MoveState s) {
        if (!(level() instanceof ServerLevel server)) return;
        if (t == 0) {
            s.spot = ground(position());
            face(target);
            announceAuthority(server, 120);
            sound(SoundEvents.WARDEN_SONIC_CHARGE, 5f, 0.5f);
            sound(SoundEvents.BEACON_ACTIVATE, 4f, 0.5f);
            for (int i = 0; i < 5; i++) {
                float inner = i * 6f, outer = inner + 6f;
                if (i == 0) strike(Shape.CIRCLE, s.spot, Vec3.ZERO, outer, 0, 0, 40, 1.5f, DOMINION, fxColor());
                else strike(Shape.RING, s.spot, Vec3.ZERO, outer, 0, inner, 40 + i * 15, 1.5f, DOMINION, fxColor());
            }
        }
        if (t == 90) {
            strike(Shape.CIRCLE, s.spot, Vec3.ZERO, 12f, 0, 0, 30, 1.8f, SLAM, fxColor());
            sound(SoundEvents.WARDEN_SONIC_CHARGE, 5f, 0.4f);
        }
        if (t % 10 == 0 && t < 120) server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + getBbHeight(), getZ(), 14, 1.2, 1, 1.2, 0.15);
    }

    // ================================================================ when blows land

    @Override
    protected void landed(ServerLevel level, Strike s, List<LivingEntity> caught) {
        switch (s.tag()) {
            case GRASP -> {
                for (LivingEntity e : caught) {
                    // lifted and held: the hand slams them down a moment later (grasp, above)
                    e.setDeltaMovement(e.getDeltaMovement().x * 0.1, 1.0, e.getDeltaMovement().z * 0.1);
                    e.hurtMarked = true;
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 24, 2, false, true), this);
                    if (move != null && "grasp".equals(move.anim())) moveState.hit.add(e.getId());
                }
                level.sendParticles(ParticleTypes.END_ROD, s.at().x, s.at().y + 0.5, s.at().z, 20, s.size() * 0.4, 0.6, s.size() * 0.4, 0.08);
                soundAt(s.at(), SoundEvents.EVOKER_CAST_SPELL, 2f, 0.6f);
            }
            case SEIZE -> {
                for (LivingEntity e : caught) {
                    e.setDeltaMovement(e.getDeltaMovement().x * 0.2, 0.9, e.getDeltaMovement().z * 0.2);
                    e.hurtMarked = true;
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, true), this);
                }
                level.sendParticles(ParticleTypes.END_ROD, s.at().x, s.at().y + 0.5, s.at().z, 10, 0.6, 0.6, 0.6, 0.05);
            }
            case CRUSH, SLAM -> {
                WorldBossFx.quake(level, s.at(), s.shape() == Shape.CELL ? 6f : s.size() + 2, fxColor());
                dust(s.at(), s.shape() == Shape.CELL ? 4f : s.size(), 40);
                shake(s.tag() == SLAM ? 1.2f : 0.7f, 40);
                soundAt(s.at(), SoundEvents.ANVIL_LAND, 3f, 0.5f);
                if (s.tag() == SLAM) soundAt(s.at(), SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.6f);
            }
            case STONE -> {
                dust(s.at(), 2.8f, 30);
                soundAt(s.at(), SoundEvents.STONE_BREAK, 2.5f, 0.6f);
            }
            case REPEL -> {
                for (LivingEntity e : caught) {
                    Vec3 away = e.position().subtract(position()).multiply(1, 0, 1);
                    away = away.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, getYRot()) : away.normalize();
                    e.push(away.x * 2.8, 0.6, away.z * 2.8);
                    e.hurtMarked = true;
                }
                WorldBossFx.ring(level, s.at(), 0.5f, s.size(), 8, fxColor());
                soundAt(s.at(), SoundEvents.WARDEN_SONIC_BOOM, 2f, 1.3f);
            }
            case PULL -> {
                for (LivingEntity e : caught) {
                    Vec3 to = position().subtract(e.position()).multiply(1, 0, 1);
                    double d = to.length();
                    if (d < 1e-3) continue;
                    double pull = Math.min(2.6, d * 0.22);
                    e.push(to.x / d * pull, 0.45, to.z / d * pull);
                    e.hurtMarked = true;
                }
                soundAt(s.at(), SoundEvents.EVOKER_CAST_SPELL, 3f, 0.4f);
            }
            case DOMINION -> {
                WorldBossFx.quake(level, s.at(), s.size(), fxColor());
                // a few bursts round the ring that fell
                for (int i = 0; i < 6; i++) {
                    double a = random.nextDouble() * Math.PI * 2, r = (s.inner() + s.size()) / 2;
                    Vec3 at = ground(s.at().add(Math.cos(a) * r, 0, Math.sin(a) * r));
                    WorldBossFx.burst(level, at.add(0, 0.5, 0), 1.8f, fxColor());
                }
                shake(0.9f, 48);
                soundAt(s.at(), SoundEvents.ANVIL_LAND, 3f, 0.4f);
            }
            default -> {
            }
        }
    }
}
