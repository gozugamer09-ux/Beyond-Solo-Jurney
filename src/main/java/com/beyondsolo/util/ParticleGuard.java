package com.beyondsolo.util;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Vanilla particles sent from the server, kept out of the eyes of anyone standing right by them. A first-person camera
 * a step away from a burst of vanilla particles sees a wall of squares; those players get the moment from its sound and
 * from the light the client draws instead: a creature rising or crumbling at your feet, a bomber going off beside you,
 * a boss's last puff, the depths' traps and seals ({@code depths.NearFx}).
 *
 * <p>The client keeps its own particles away the same way: {@code client.render.CameraParticles}.</p>
 */
public final class ParticleGuard {
    /** How near (blocks, from the eyes) vanilla particles may come, at the nearest they can get. */
    public static final double CLOSE = 2.5;
    /** How far block bits get from where they start: the particle throws them about itself, whatever speed they are given. */
    public static final double BLOCK_BITS = 0.8;

    /** {@link ServerLevel#sendParticles}, but not to players whose eyes the burst could reach. */
    public static void send(ServerLevel level, ParticleOptions type, double x, double y, double z,
                            int count, double dx, double dy, double dz, double speed) {
        // where the burst can reach: its spread (gaussian, so 2.5 of it holds nearly all), then however far it flies
        double t = travel(type, speed);
        double rx = 2.5 * dx + t, ry = 2.5 * dy + t, rz = 2.5 * dz + t;
        for (ServerPlayer p : level.players()) {
            if (clear(p.getEyePosition(), x, y, z, rx, ry, rz)) level.sendParticles(p, type, false, x, y, z, count, dx, dy, dz, speed);
        }
    }

    /** True if a burst reaching {@code r*} around the point stays more than {@link #CLOSE} from {@code eye}. */
    static boolean clear(Vec3 eye, double x, double y, double z, double rx, double ry, double rz) {
        double ox = Math.max(0, Math.abs(eye.x - x) - rx);
        double oy = Math.max(0, Math.abs(eye.y - y) - ry);
        double oz = Math.max(0, Math.abs(eye.z - z) - rz);
        return ox * ox + oy * oy + oz * oz > CLOSE * CLOSE;
    }

    /** How far a burst's particles get from where they start (blocks). */
    public static double travel(ParticleOptions type, double speed) {
        if (type instanceof BlockParticleOption || type instanceof ItemParticleOption) return BLOCK_BITS;
        // lava pops jump up to a few blocks whatever they are given; smoke and puffs rise
        if (type == ParticleTypes.LAVA) return 2.0;
        if (type == ParticleTypes.LARGE_SMOKE || type == ParticleTypes.SMOKE || type == ParticleTypes.POOF || type == ParticleTypes.CLOUD
                || type == ParticleTypes.CAMPFIRE_COSY_SMOKE) return 1.0 + speed * 40;
        // the rest drift for about twenty ticks at up to twice the speed they are given
        return speed * 40;
    }

    private ParticleGuard() {}
}
