package com.beyondsolo.depths;

import net.minecraft.core.BlockPos;
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
    /** How close (blocks, from the eyes) is too close for vanilla particles. */
    static final double CLOSE = 2.5;

    static void particles(ServerLevel level, ParticleOptions type, double x, double y, double z,
                          int count, double dx, double dy, double dz, double speed) {
        Vec3 at = new Vec3(x, y, z);
        double close = CLOSE + Math.max(dx, dz);
        for (ServerPlayer p : level.players()) {
            if (p.getEyePosition().distanceTo(at) > close) level.sendParticles(p, type, false, x, y, z, count, dx, dy, dz, speed);
        }
    }

    /** A block of {@code look} crumbling at {@code pos}: its break particles and sound, or just the sound up close. */
    static void crumble(ServerLevel level, BlockPos pos, BlockState look) {
        Vec3 at = Vec3.atCenterOf(pos);
        for (ServerPlayer p : level.players()) {
            double d = p.getEyePosition().distanceTo(at);
            if (d > 48) continue;
            if (d > CLOSE) p.connection.send(new ClientboundLevelEventPacket(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(look), false));
            else p.playNotifySound(look.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.9f, 0.8f);
        }
    }

    private NearFx() {}
}
