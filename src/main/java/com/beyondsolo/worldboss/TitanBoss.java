package com.beyondsolo.worldboss;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
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
 * Ghorrath, the Walking Mountain: a giant of basalt and magma twelve blocks tall, a mountain ridge on its back. Slow,
 * and every blow it throws is announced on the ground long before it lands; what it lacks in speed it makes up for in
 * reach and in what it does to the land.
 * <ul>
 *   <li><b>I. The Mountain Walks</b>: a stomp, a backhanded sweep, a two-fisted slam that sends a wave out along the
 *   ground (jump it), and boulders torn out of the earth for those who keep away.</li>
 *   <li><b>II. Molten Heart</b>: its cracks run with magma. Rains of molten rock out of the ridge on its back, and a
 *   fissure that runs out along the ground spitting magma.</li>
 *   <li><b>III. Cataclysm</b>: the Earthbreaker: it raises both arms and breaks the ground under it. Get out of the
 *   middle, and jump the three waves that run out after you. Its stomps come in pairs.</li>
 * </ul>
 */
public class TitanBoss extends GiantBoss {
    static final int STOMP = 1, SWEEP = 2, SLAM = 3, BOULDER = 4, METEOR = 5, FISSURE = 6, BREAKER = 7, WAVE = 8;

    public TitanBoss(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, WorldBossKind.TITAN.health())
                .add(Attributes.ATTACK_DAMAGE, WorldBossKind.TITAN.damage())
                .add(Attributes.ARMOR, 14)
                .add(Attributes.ARMOR_TOUGHNESS, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 64)
                .add(Attributes.STEP_HEIGHT, 3);
    }

    @Override
    public WorldBossKind kind() {
        return WorldBossKind.TITAN;
    }

    @Override
    protected double walkSpeed() {
        return 0.13;
    }

    @Override
    protected double reach() {
        return 4.0;
    }

    @Override
    protected int introLength() {
        return 90;
    }

    @Override
    protected int shiftLength() {
        return 60;
    }

    @Override
    protected int deathLength() {
        return 110;
    }

    @Override
    public int fxColor() {
        return phase() >= 3 ? 0xFF5A1E : kind().color;
    }

    // ================================================================ moves

    @Override
    protected List<Move> buildMoves() {
        List<Move> m = new ArrayList<>();
        m.add(new Move("stomp", 40, 0, 9, 5, 1, 0, false, this::stomp));
        m.add(new Move("sweep", 44, 0, 12, 4, 1, 0, false, this::sweep));
        m.add(new Move("slam", 56, 2, 15, 3, 1, 140, false, this::slam));
        m.add(new Move("hurl", 50, 10, 48, 4, 1, 80, false, this::hurl));
        m.add(new Move("magma_rain", 70, 0, 40, 3, 2, 220, false, this::magmaRain));
        m.add(new Move("fissure", 50, 4, 30, 4, 2, 100, false, this::fissure));
        m.add(new Move("earthbreaker", 96, 0, 24, 3, 3, 320, false, this::earthbreaker));
        return m;
    }

    private void stomp(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            s.dir = face(target);
            Vec3 side = new Vec3(-s.dir.z, 0, s.dir.x);
            s.spot = ground(position().add(s.dir.scale(3.2)).add(side.scale(1.3)));
            circleStrike(s.spot, 6.5f, 22, 1.0f, STOMP);
            if (phase() >= 3) circleStrike(ground(position().add(s.dir.scale(3.2)).subtract(side.scale(1.3)).add(s.dir.scale(4))), 6.5f, 34, 1.0f, STOMP);
            sound(SoundEvents.RAVAGER_STEP, 3f, 0.5f);
        }
    }

    private void sweep(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            s.dir = face(target);
            strike(Shape.CONE, ground(position()), s.dir, 70, 13, 0, 24, 0.9f, SWEEP, fxColor());
        }
        if (t == 20) sound(SoundEvents.PLAYER_ATTACK_SWEEP, 3f, 0.4f);
    }

    private void slam(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            s.dir = face(target);
            strike(Shape.LINE, ground(position()), s.dir, 5, 16, 0, 26, 1.2f, SLAM, fxColor());
        }
    }

    private void hurl(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            s.dir = face(target);
            dust(ahead(s.dir, 3), 2.5f, 20);
            sound(SoundEvents.GRAVEL_BREAK, 3f, 0.5f);
        }
        if (t == 18 && level() instanceof ServerLevel server) {
            Vec3 at = target != null ? target.position().add(target.getDeltaMovement().multiply(8, 0, 8)) : ahead(s.dir, 20);
            s.spot = ground(at);
            Vec3 hand = position().add(0, getBbHeight() * 0.85, 0).add(new Vec3(-s.dir.z, 0, s.dir.x).scale(-2));
            BossProjectile.launch(server, BossProjectile.Kind.BOULDER, hand, s.spot.add(0, 0.6, 0), 26, 9, 2.2f, fxColor());
            circleStrike(s.spot, 4.0f, 26, 1.0f, BOULDER);
            sound(SoundEvents.ENDER_DRAGON_FLAP, 3f, 0.5f);
        }
    }

    private void magmaRain(@Nullable LivingEntity target, int t, MoveState s) {
        if (!(level() instanceof ServerLevel server)) return;
        if (t == 0) {
            sound(SoundEvents.BLAZE_SHOOT, 4f, 0.4f);
            for (LivingEntity e : fighters(40)) s.spots.add(e.position());
            for (int i = s.spots.size(); i < 8; i++) {
                double a = random.nextDouble() * Math.PI * 2, d = 6 + random.nextDouble() * 22;
                s.spots.add(position().add(Math.cos(a) * d, 0, Math.sin(a) * d));
            }
            server.sendParticles(ParticleTypes.LAVA, getX(), getY() + getBbHeight() * 0.9, getZ(), 30, 1.5, 0.5, 1.5, 0.2);
        }
        if (t >= 10 && t <= 52 && (t - 10) % 5 == 0 && s.n < s.spots.size() + 3) {
            Vec3 base = s.spots.get(s.n % s.spots.size());
            // later stones fall near the first, where a hunter may have moved to
            Vec3 spot = s.n < s.spots.size() ? base : base.add(random.nextGaussian() * 4, 0, random.nextGaussian() * 4);
            spot = ground(spot);
            WorldBossFx.meteor(server, spot, 1.6f, 30, fxColor());
            circleStrike(spot, 3.2f, 30, 0.9f, METEOR);
            s.n++;
        }
    }

    private void fissure(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) s.dir = face(target);
        if (t >= 12 && t <= 40 && (t - 12) % 4 == 0) {
            int n = (t - 12) / 4;
            Vec3 at = ahead(s.dir, 4 + n * 3.4);
            circleStrike(at, 2.6f, 12, 0.9f, FISSURE);
            if (n == 0) {
                sound(SoundEvents.GENERIC_EXPLODE.value(), 2.5f, 0.5f);
                shake(0.5f, 40);
            }
        }
    }

    private void earthbreaker(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            face(target);
            circleStrike(ground(position()), 10f, 62, 1.7f, BREAKER);
            sound(SoundEvents.WARDEN_ROAR, 4f, 0.5f);
        }
        if (t == 12 || t == 30 || t == 48) {
            wave(ground(position()), 24, 1.8f, 30, 0.7f, WAVE);
            shake(0.4f, 48);
            sound(SoundEvents.GENERIC_EXPLODE.value(), 2f, 0.4f);
        }
    }

    // ================================================================ when blows land

    @Override
    protected void landed(ServerLevel level, Strike s, List<LivingEntity> caught) {
        switch (s.tag()) {
            case STOMP -> {
                dust(s.at(), 5f, 50);
                WorldBossFx.quake(level, s.at(), 7.5f, fxColor());
                shake(0.6f, 48);
                soundAt(s.at(), SoundEvents.GENERIC_EXPLODE.value(), 2.5f, 0.45f);
            }
            case SWEEP -> {
                for (LivingEntity e : caught) {
                    Vec3 away = e.position().subtract(position()).multiply(1, 0, 1).normalize();
                    e.push(away.x * 1.6, 0.5, away.z * 1.6);
                    e.hurtMarked = true;
                }
            }
            case SLAM -> {
                Vec3 end = s.at().add(s.dir().scale(s.length() * 0.6));
                dust(end, 5f, 60);
                WorldBossFx.quake(level, ground(end), 9f, fxColor());
                wave(ground(end), 14, 1.8f, 26, 0.6f, WAVE);
                shake(0.9f, 56);
                soundAt(end, SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.4f);
            }
            case BOULDER, METEOR -> {
                dust(s.at(), s.size(), 24);
                WorldBossFx.burst(level, s.at().add(0, 0.6, 0), s.size(), fxColor());
                level.sendParticles(ParticleTypes.LAVA, s.at().x, s.at().y + 0.3, s.at().z, 6, 0.8, 0.1, 0.8, 0.1);
                for (LivingEntity e : caught) e.igniteForSeconds(3);
                soundAt(s.at(), SoundEvents.GENERIC_EXPLODE.value(), 1.8f, 0.7f);
            }
            case FISSURE -> {
                WorldBossFx.pillar(level, s.at(), 1.6f, 6f, fxColor());
                level.sendParticles(ParticleTypes.LAVA, s.at().x, s.at().y + 0.3, s.at().z, 8, 0.6, 0.2, 0.6, 0.1);
                for (LivingEntity e : caught) {
                    e.push(0, 0.6, 0);
                    e.hurtMarked = true;
                    e.igniteForSeconds(2);
                }
                soundAt(s.at(), SoundEvents.BLAZE_SHOOT, 1.6f, 0.6f);
            }
            case BREAKER -> {
                dust(s.at(), 9f, 120);
                WorldBossFx.quake(level, s.at(), 14f, fxColor());
                WorldBossFx.burst(level, s.at().add(0, 1, 0), 8f, fxColor());
                shake(1.5f, 72);
                soundAt(s.at(), SoundEvents.GENERIC_EXPLODE.value(), 4f, 0.35f);
            }
            default -> {
            }
        }
    }

    // ================================================================ entrance, phases, death

    @Override
    protected void introTick(ServerLevel level, int t) {
        if (t % 4 == 0 && t < 60) {
            dust(position(), 6f, 30);
            shake(0.3f, 72);
        }
        if (t == 0) WorldBossFx.quake(level, ground(position()), 10f, fxColor());
        if (t == 62) {
            sound(SoundEvents.WARDEN_ROAR, 5f, 0.45f);
            shake(1.0f, 96);
        }
    }

    @Override
    protected void onPhase(ServerLevel level, int phase) {
        Vec3 at = ground(position());
        if (phase == 2) {
            // its heart melts: magma breaks out round it
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3;
                WorldBossFx.pillar(level, ground(at.add(Math.cos(a) * 6, 0, Math.sin(a) * 6)), 1.4f, 7f, fxColor());
            }
        } else if (phase == 3) {
            WorldBossFx.quake(level, at, 16f, fxColor());
        }
        sound(SoundEvents.WARDEN_ROAR, 5f, phase == 3 ? 0.4f : 0.5f);
    }

    @Override
    protected void shiftTick(ServerLevel level, int phase, int t) {
        if (t % 6 == 0) {
            level.sendParticles(ParticleTypes.LAVA, getX(), getY() + getBbHeight() * 0.7, getZ(), 6, 1.5, 2, 1.5, 0.1);
            shake(0.25f, 48);
        }
    }

    private int stepTimer;

    @Override
    public void aiStep() {
        super.aiStep();
        // its footfalls: heavy, and felt
        if (!level().isClientSide && isAlive() && !leaving() && moved() > 0.0004 && ++stepTimer >= 22) {
            stepTimer = 0;
            sound(SoundEvents.RAVAGER_STEP, 2.5f, 0.35f);
            dust(position(), 2.5f, 8);
            shake(0.12f, 28);
        }
    }

    @Override
    protected void deathTick(ServerLevel level, int t) {
        if (t == 1) sound(SoundEvents.WARDEN_DEATH, 5f, 0.4f);
        if (t == 42) {
            Vec3 d = Vec3.directionFromRotation(0, getYRot());
            for (int i = 0; i < 4; i++) dust(ground(position().add(d.scale(2 + i * 2.5))), 3.5f, 30);
            shake(1.2f, 72);
            sound(SoundEvents.GENERIC_EXPLODE.value(), 4f, 0.4f);
        }
        if (t > 60 && t % 8 == 0) {
            level.sendParticles(ParticleTypes.LAVA, getX(), getY() + 1, getZ(), 4, 3, 1, 3, 0.1);
            dust(position().add(Mth.nextDouble(random, -4, 4), 0, Mth.nextDouble(random, -4, 4)), 2f, 12);
        }
    }

    @Override
    protected List<String> extraAnims() {
        return List.of("intro", "phase2", "phase3", "leave");
    }
}
