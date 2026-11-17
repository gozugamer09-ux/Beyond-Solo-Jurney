package com.beyondsolo.depths;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Vanilla particles sent from the server, kept out of the eyes of anyone standing right by them: a first-person camera
 * a step away from a burst of vanilla particles sees a wall of squares, so those players get the moment from their own
 * client effects (and its sound) instead.
 */
final class NearFx {
    /** How near (blocks, from the eyes) vanilla particles may come, at the nearest they can get. */
    static final double CLOSE = 2.5;
    /** How far block bits get from where they start: the particle throws them about itself, whatever speed they are given. */
    static final double BLOCK_BITS = 0.8;

    static void particles(ServerLevel level, ParticleOptions type, double x, double y, double z,
                          int count, double dx, double dy, double dz, double speed) {
        // where the burst can reach: its spread (gaussian, so 2.5 of it holds nearly all), then however far it flies
        double t = travel(type, speed);
        double rx = 2.5 * dx + t, ry = 2.5 * dy + t, rz = 2.5 * dz + t;
        for (ServerPlayer p : level.players()) {
            Vec3 eye = p.getEyePosition();
            double ox = Math.max(0, Math.abs(eye.x - x) - rx);
            double oy = Math.max(0, Math.abs(eye.y - y) - ry);
            double oz = Math.max(0, Math.abs(eye.z - z) - rz);
            if (ox * ox + oy * oy + oz * oz > CLOSE * CLOSE) level.sendParticles(p, type, false, x, y, z, count, dx, dy, dz, speed);
        }
    }

    /** How far a burst's particles get from where they start (blocks). */
    static double travel(ParticleOptions type, double speed) {
        if (type instanceof BlockParticleOption) return BLOCK_BITS;
        // the rest drift for about twenty ticks at up to twice the speed they are given
        return speed * 40;
    }

    /** A block of {@code look} crumbling at {@code pos}: its break particles and sound, or just the sound up close. */
    static void crumble(ServerLevel level, BlockPos pos, BlockState look) {
        Vec3 at = Vec3.atCenterOf(pos);
        for (ServerPlayer p : level.players()) {
            double d = p.getEyePosition().distanceTo(at);
            if (d > 48) continue;
            // the bits start anywhere in the block (up to 0.9 from its middle) and fly a little
            if (d > CLOSE + 0.9 + BLOCK_BITS) p.connection.send(new ClientboundLevelEventPacket(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(look), false));
            else p.playNotifySound(look.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.9f, 0.8f);
        }
    }

    private NearFx() {}
}
