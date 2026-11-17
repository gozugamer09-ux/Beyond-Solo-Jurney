package com.beyondsolo.client.render;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ScalableParticleOptionsBase;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Vanilla particles the client spawns, kept out of a first-person camera's face. A burst of vanilla particles a step
 * from the eyes is a wall of squares: a creature catching fire at arm's length, a burn's embers, a death puff. Each
 * particle that could come that close is left out; the moment keeps its light, its sound and the rest of its particles.
 * In third person every particle is drawn as before.
 *
 * <p>Particles the server sends are kept away the same way by {@link com.beyondsolo.util.ParticleGuard}.</p>
 */
public final class CameraParticles {
    /** How near (blocks) a vanilla particle may come to a first-person camera. */
    public static final double CLOSE = 2.0;
    /** How many ticks' worth of the speed it is given a particle travels: vanilla slows most of them by 4% a tick. */
    private static final double GLIDE = 25;

    /** {@link ClientLevel#addParticle}, unless the particle could come near a first-person camera. */
    public static void add(ClientLevel level, ParticleOptions type, double x, double y, double z, double vx, double vy, double vz) {
        if (clear(type, x, y, z, vx, vy, vz)) level.addParticle(type, x, y, z, vx, vy, vz);
    }

    /** True if a particle starting here with this speed stays more than {@link #CLOSE} from a first-person camera. */
    public static boolean clear(ParticleOptions type, double x, double y, double z, double vx, double vy, double vz) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.getCameraType().isFirstPerson()) return true;
        Camera camera = mc.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) return true;
        return clear(camera.getPosition(), type, x, y, z, vx, vy, vz);
    }

    /** True if the particle's path (where it starts, then along its speed) stays more than {@link #CLOSE} from {@code eye}. */
    public static boolean clear(Vec3 eye, ParticleOptions type, double x, double y, double z, double vx, double vy, double vz) {
        double glide = drifts(type) ? 0 : GLIDE;
        double room = CLOSE + wander(type);
        double px = eye.x - x, py = eye.y - y, pz = eye.z - z;
        double lx = vx * glide, ly = vy * glide, lz = vz * glide;
        double len = lx * lx + ly * ly + lz * lz;
        double t = len < 1e-9 ? 0 : Math.max(0, Math.min(1, (px * lx + py * ly + pz * lz) / len));
        double dx = px - lx * t, dy = py - ly * t, dz = pz - lz * t;
        return dx * dx + dy * dy + dz * dz > room * room;
    }

    /** Dust takes only its direction from the speed it is given, and drifts a little way whatever it is. */
    private static boolean drifts(ParticleOptions type) {
        return type instanceof ScalableParticleOptionsBase;
    }

    /** How far a particle strays from its path by itself (blocks): bits thrown about, pops that jump, smoke that rises. */
    private static double wander(ParticleOptions type) {
        if (type instanceof BlockParticleOption || type instanceof ItemParticleOption || type == ParticleTypes.ITEM_SNOWBALL) return 0.8;
        // a lava pop jumps up to a few blocks, whatever it is given
        if (type == ParticleTypes.LAVA) return 2.0;
        if (type == ParticleTypes.LARGE_SMOKE || type == ParticleTypes.SMOKE || type == ParticleTypes.POOF || type == ParticleTypes.CLOUD
                || type == ParticleTypes.CAMPFIRE_COSY_SMOKE) return 1.0;
        if (drifts(type)) return 0.5;
        return 0.3;
    }

    /** A creature's death puff (vanilla's, twenty puffs about its body), without the puffs that would reach a first-person camera. */
    public static void poof(LivingEntity e) {
        if (!(e.level() instanceof ClientLevel level)) return;
        RandomSource r = e.getRandom();
        for (int i = 0; i < 20; i++) {
            add(level, ParticleTypes.POOF, e.getRandomX(1.0), e.getRandomY(), e.getRandomZ(1.0),
                    r.nextGaussian() * 0.02, r.nextGaussian() * 0.02, r.nextGaussian() * 0.02);
        }
    }

    private CameraParticles() {}
}
