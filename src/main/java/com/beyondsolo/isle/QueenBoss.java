package com.beyondsolo.isle;

import com.beyondsolo.worldboss.BossProjectile;
import com.beyondsolo.worldboss.WorldBossFx;
import com.beyondsolo.worldboss.WorldBossKind;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Vessa, Mother of the Hive: the swollen queen deep under Halcyon Isle, who laid the swarm that took it. Too heavy to
 * chase anyone far, she fights from her dais with acid and her brood, and crushes whoever comes close.
 * <ul>
 *   <li><b>I. The Mother</b>: globs of acid spat across the chamber (each drawn on the ground first), a slam of her
 *   forelegs, and her eggs hatching round her.</li>
 *   <li><b>II. The Royal Guard</b>: her guard comes out of the walls (S-rank reavers), and she shrieks: a wave runs out
 *   from her (jump it) that leaves whoever it catches reeling.</li>
 *   <li><b>III. The Last Brood</b>: acid rains from the vault, and she drags her whole body across the chamber.</li>
 * </ul>
 * When she falls, the cocoon on the wall behind her breaks ({@link IsleRaids}).
 */
public class QueenBoss extends HiveBoss {
    static final int SPIT = 1, SLAM = 2, RAIN = 3, CRUSH = 4, SHRIEK = 5;

    public QueenBoss(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Override
    public WorldBossKind kind() {
        return WorldBossKind.QUEEN;
    }

    @Override
    protected double preferred() {
        return 7;
    }

    @Override
    protected int introLength() {
        return 100;
    }

    @Override
    protected int deathLength() {
        return 110;
    }

    @Override
    public int fxColor() {
        return phase() >= 3 ? 0xFF7A1E : kind().color;
    }

    @Override
    protected void approach(LivingEntity target, double dist) {
        // she won't leave her dais far behind
        if (home() != null && position().distanceTo(Vec3.atBottomCenterOf(home())) > 11) {
            getNavigation().moveTo(home().getX() + 0.5, home().getY() + 1, home().getZ() + 0.5, 0.9);
            return;
        }
        super.approach(target, dist);
    }

    // ================================================================ moves

    @Override
    protected List<Move> buildMoves() {
        List<Move> m = new ArrayList<>();
        m.add(new Move("spit", 40, 3, 44, 5, 1, 0, false, this::spit));
        m.add(new Move("slam", 30, 0, 7, 5, 1, 0, false, this::slam));
        m.add(new Move("brood", 50, 0, 64, 2, 1, 380, false, this::brood));
        m.add(new Move("shriek", 44, 0, 26, 3, 2, 240, false, this::shriek));
        m.add(new Move("guard", 56, 0, 64, 2, 2, 900, false, this::guard));
        m.add(new Move("acid_rain", 64, 0, 44, 3, 3, 260, false, this::acidRain));
        m.add(new Move("crush", 50, 2, 22, 3, 3, 180, true, this::crush));
        return m;
    }

    /** Where her jaws are on the model (tools/gen_isle_assets.py): about 1.9 blocks before her middle, 2.4 up. */
    private Vec3 mouth() {
        Vec3 look = Vec3.directionFromRotation(0, getYRot());
        return position().add(look.scale(getBbWidth() * 0.46)).add(0, getBbHeight() * 0.53, 0);
    }

    /** Globs of acid at up to three hunters (two volleys in her last phase). */
    private void spit(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            face(target);
            sound(SoundEvents.LLAMA_SPIT, 3f, 0.4f);
        }
        if (t == 8 || (t == 24 && phase() >= 3)) {
            if (!(level() instanceof ServerLevel server)) return;
            List<LivingEntity> foes = fighters(44);
            if (foes.isEmpty() && target != null) foes = List.of(target);
            int n = Math.min(foes.size(), phase() >= 2 ? 3 : 2);
            for (int i = 0; i < n; i++) {
                LivingEntity e = foes.get((i + t) % foes.size());
                // a little ahead of where they run
                Vec3 lead = e.getDeltaMovement().multiply(8, 0, 8);
                Vec3 spot = ground(e.position().add(lead));
                circleStrike(spot, 2.6f, 22, 0.8f, SPIT);
                BossProjectile.launch(server, BossProjectile.Kind.ACID_GLOB, mouth(), spot, 22, 4f, 1.3f, fxColor());
            }
            sound(SoundEvents.LLAMA_SPIT, 4f, 0.5f);
            sound(SoundEvents.SLIME_SQUISH, 3f, 0.4f);
        }
    }

    /** Her forelegs come down on whoever is in front of her. */
    private void slam(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            s.dir = face(target);
            strike(Shape.CONE, ground(position()), s.dir, 55, 8.5f, 0, 16, 1.1f, SLAM, fxColor());
            sound(SoundEvents.SPIDER_AMBIENT, 4f, 0.35f);
        }
    }

    /** Eggs hatch round her. */
    private void brood(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) sound(SoundEvents.SILVERFISH_AMBIENT, 4f, 0.4f);
        if (t == 22 && level() instanceof ServerLevel server) {
            IsleRaids.brood(server, this, 2 + phase(), target);
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.HONEYCOMB_BLOCK.defaultBlockState()), getX(), getY() + 0.8, getZ(), 40,
                    2.5, 0.4, 2.5, 0.2);
            sound(SoundEvents.TURTLE_EGG_HATCH, 4f, 0.5f);
        }
    }

    /** A shriek that runs out along the floor: jump it, or reel. */
    private void shriek(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            face(target);
            sound(SoundEvents.SPIDER_AMBIENT, 4f, 0.5f);
        }
        if (t == 14) {
            sound(SoundEvents.ENDER_DRAGON_GROWL, 5f, 1.45f);
            sound(SoundEvents.PHANTOM_SWOOP, 4f, 0.5f);
            shake(0.5f, 40);
            wave(ground(position()), 24, 2.2f, 28, 0.6f, SHRIEK);
        }
    }

    /** Her royal guard comes out of the walls. */
    private void guard(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            face(target);
            sound(SoundEvents.ENDER_DRAGON_GROWL, 4f, 1.2f);
        }
        if (t == 26 && level() instanceof ServerLevel server) {
            IsleRaids.royalGuard(server, this, phase() >= 3 ? 3 : 2, target);
            shake(0.4f, 40);
        }
    }

    /** Acid falls from the vault round the hunters. */
    private void acidRain(@Nullable LivingEntity target, int t, MoveState s) {
        if (!(level() instanceof ServerLevel server)) return;
        if (t == 0) {
            face(target);
            sound(SoundEvents.ENDER_DRAGON_GROWL, 4f, 1.6f);
        }
        if (t >= 6 && t <= 54 && t % 6 == 0) {
            List<LivingEntity> foes = fighters(44);
            if (foes.isEmpty()) return;
            LivingEntity e = foes.get(random.nextInt(foes.size()));
            double a = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * 4;
            Vec3 spot = ground(e.position().add(Math.cos(a) * r, 0, Math.sin(a) * r));
            circleStrike(spot, 2.4f, 20, 0.7f, RAIN);
            BossProjectile.launch(server, BossProjectile.Kind.ACID_GLOB, spot.add(0, 11, 0), spot, 20, 0f, 1.1f, fxColor());
        }
    }

    /** She drags her whole body down a line across the chamber. */
    private void crush(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            s.dir = face(target);
            double run = clearRun(s.dir, 18);
            s.spot = runEnd(s.dir, run);
            s.spots.add(position());
            strike(Shape.LINE, ground(position()), s.dir, getBbWidth() + 1f, (float) run + getBbWidth() / 2, 0, 20, 1.3f, CRUSH, fxColor());
            sound(SoundEvents.RAVAGER_ROAR, 4f, 0.6f);
        }
        if (t == 20) getNavigation().stop();
        if (t >= 20 && t <= 30 && !s.spots.isEmpty()) {
            float k = (t - 20) / 10f;
            Vec3 at = s.spots.get(0).lerp(s.spot, k * k * (3 - 2 * k));
            setDeltaMovement(at.x - getX(), 0, at.z - getZ());
            if (t % 3 == 0) {
                dust(position(), 3f, 16);
                shake(0.3f, 30);
            }
        }
        if (t > 30) setDeltaMovement(0, getDeltaMovement().y, 0);
    }

    // ================================================================ when blows land

    @Override
    protected void landed(ServerLevel level, Strike s, List<LivingEntity> caught) {
        switch (s.tag()) {
            case SLAM -> {
                dust(s.at().add(s.dir().scale(4)), 3.5f, 30);
                WorldBossFx.quake(level, s.at().add(s.dir().scale(4)), 4f, fxColor());
                shake(0.5f, 30);
                soundAt(s.at(), SoundEvents.GENERIC_EXPLODE.value(), 1.6f, 0.6f);
            }
            case SPIT, RAIN -> {
                for (LivingEntity e : caught) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), this);
                soundAt(s.at(), SoundEvents.SLIME_SQUISH, 1.6f, 0.4f);
            }
            case CRUSH -> {
                for (LivingEntity e : caught) {
                    e.push(0, 0.7, 0);
                    e.hurtMarked = true;
                }
                soundAt(s.at(), SoundEvents.GENERIC_EXPLODE.value(), 2f, 0.5f);
            }
            default -> {
            }
        }
    }

    @Override
    protected void waveHit(ServerLevel level, Wave w, LivingEntity e) {
        if (w.tag() == SHRIEK) {
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2), this);
            e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0), this);
        }
    }

    // ================================================================ waking, phases, death

    @Override
    protected void introTick(ServerLevel level, int t) {
        if (t == 0) sound(SoundEvents.SILVERFISH_AMBIENT, 5f, 0.3f);
        if (t % 5 == 0 && t < 60) level.sendParticles(ParticleTypes.FALLING_HONEY, getX(), getY() + getBbHeight(), getZ(), 6, 2, 0.5, 2, 0.02);
        if (t == 50) {
            sound(SoundEvents.ENDER_DRAGON_GROWL, 6f, 1.25f);
            shake(0.9f, 60);
            WorldBossFx.quake(level, ground(position()), 9f, fxColor());
        }
    }

    @Override
    protected void onPhase(ServerLevel level, int phase) {
        sound(SoundEvents.ENDER_DRAGON_GROWL, 6f, phase == 3 ? 1.1f : 1.3f);
        level.sendParticles(ParticleTypes.FALLING_HONEY, getX(), getY() + getBbHeight() * 0.8, getZ(), 40, 2.5, 1, 2.5, 0.05);
        if (phase == 2) IsleRaids.royalGuard(level, this, 2, getTarget());
        if (phase == 3) WorldBossFx.quake(level, ground(position()), 11f, fxColor());
    }

    @Override
    protected void deathTick(ServerLevel level, int t) {
        super.deathTick(level, t);
        if (t == 1) sound(SoundEvents.ENDER_DRAGON_GROWL, 6f, 0.9f);
        if (t == 40) sound(SoundEvents.SILVERFISH_DEATH, 5f, 0.3f);
        if (t % 4 == 0) {
            level.sendParticles(ParticleTypes.FALLING_HONEY, getX() + Mth.nextDouble(random, -2, 2), getY() + 2, getZ() + Mth.nextDouble(random, -2, 2), 3, 0.3,
                    0.3, 0.3, 0.02);
        }
    }

    @Override
    protected String baseAnim() {
        return moved() > 0.0008 && isAwake() ? "walk" : "idle";
    }
}
