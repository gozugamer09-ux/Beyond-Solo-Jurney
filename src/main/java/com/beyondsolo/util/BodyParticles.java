package com.beyondsolo.util;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Particles on a player's body: everyone else sees them there, the player only a few at their feet, so none burst
 * right in front of a first-person camera (where a single particle fills a corner of the screen).
 */
public final class BodyParticles {
    /** {@code count} particles {@code y} blocks up the player's body, spread as {@code sendParticles} spreads them. */
    public static void send(ServerPlayer on, ParticleOptions type, double y, int count, double dx, double dy, double dz, double speed) {
        ServerLevel level = on.serverLevel();
        for (ServerPlayer other : level.players()) {
            if (other != on) level.sendParticles(other, type, false, on.getX(), on.getY() + y, on.getZ(), count, dx, dy, dz, speed);
        }
        level.sendParticles(on, type, false, on.getX(), on.getY() + 0.15, on.getZ(), Math.max(1, count / 2),
                Math.max(dx, 0.4) * 1.4, 0.05, Math.max(dz, 0.4) * 1.4, speed * 0.5);
    }

    private BodyParticles() {}
}
