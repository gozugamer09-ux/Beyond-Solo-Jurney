package com.beyondsolo.skill;

import com.beyondsolo.network.SkillParticlesPayload;
import com.beyondsolo.network.SkillParticlesPayload.Look;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

/**
 * Particles from skills. Takes the place of {@link ServerLevel#sendParticles}: the vanilla particles the skills have
 * always thrown (flames, blasts, sparks, motes, smoke) go to the clients as {@link SkillParticlesPayload} and are
 * drawn there with the skill effects' own soft particles, so they match the rest of the effect instead of reading as
 * little squares. Anything without a look of its own (block debris, hearts) is sent as vanilla particles, as before.
 */
public final class SkillParticles {
    /** Players this close see them (vanilla's reach for particles is 32). */
    private static final double REACH = 48;

    public static <T extends ParticleOptions> int send(ServerLevel level, T type, double x, double y, double z, int count,
                                                       double dx, double dy, double dz, double speed) {
        Look look = look(type);
        if (look == null) return level.sendParticles(type, x, y, z, count, dx, dy, dz, speed);
        int color = 0;
        if (type instanceof DustParticleOptions dust) {
            Vector3f c = dust.getColor();
            color = (Math.round(c.x() * 255) << 16) | (Math.round(c.y() * 255) << 8) | Math.round(c.z() * 255);
        }
        PacketDistributor.sendToPlayersNear(level, null, x, y, z, REACH, new SkillParticlesPayload(look, color, (float) x, (float) y, (float) z,
                count, (float) dx, (float) dy, (float) dz, (float) speed));
        return count;
    }

    /** The soft look a vanilla particle is drawn with, or null to leave it vanilla. */
    static Look look(ParticleOptions type) {
        if (type instanceof DustParticleOptions) return Look.DUST;
        if (type instanceof BlockParticleOption block) {
            if (block.getState().is(Blocks.REDSTONE_BLOCK)) return Look.BLOOD;
            if (block.getState().is(BlockTags.LEAVES)) return Look.LEAF;
            return null;
        }
        if (type == ParticleTypes.FLAME || type == ParticleTypes.SMALL_FLAME) return Look.FIRE;
        if (type == ParticleTypes.SOUL_FIRE_FLAME) return Look.SOUL_FIRE;
        if (type == ParticleTypes.EXPLOSION) return Look.EXPLOSION;
        if (type == ParticleTypes.EXPLOSION_EMITTER) return Look.BIG_EXPLOSION;
        if (type == ParticleTypes.END_ROD) return Look.MOTE;
        if (type == ParticleTypes.CRIT || type == ParticleTypes.ENCHANTED_HIT) return Look.CRIT;
        if (type == ParticleTypes.LAVA) return Look.LAVA;
        if (type == ParticleTypes.ELECTRIC_SPARK) return Look.SPARK;
        if (type == ParticleTypes.SOUL) return Look.SOUL;
        if (type == ParticleTypes.SNEEZE) return Look.SNEEZE;
        if (type == ParticleTypes.HAPPY_VILLAGER) return Look.HAPPY;
        if (type == ParticleTypes.POOF || type == ParticleTypes.CLOUD) return Look.POOF;
        if (type == ParticleTypes.SPLASH || type == ParticleTypes.BUBBLE_POP) return Look.SPLASH;
        if (type == ParticleTypes.PORTAL || type == ParticleTypes.REVERSE_PORTAL) return Look.PORTAL;
        if (type == ParticleTypes.SQUID_INK) return Look.INK;
        if (type == ParticleTypes.SNOWFLAKE) return Look.SNOW;
        if (type == ParticleTypes.GLOW) return Look.GLOW;
        if (type == ParticleTypes.FLASH) return Look.FLASH;
        if (type == ParticleTypes.SMOKE || type == ParticleTypes.LARGE_SMOKE) return Look.SMOKE;
        return null;
    }

    private SkillParticles() {}
}
