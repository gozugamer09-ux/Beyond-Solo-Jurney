package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: a puff of particles from a skill, drawn by the skill effects' own soft particles instead of
 * vanilla's. Carries what {@code ServerLevel.sendParticles} would have (same meaning for count, spread and speed), with
 * the vanilla particle it stands for as a {@link Look}.
 *
 * @param color the colour of a {@link Look#DUST} (RGB), else 0
 */
public record SkillParticlesPayload(Look look, int color, float x, float y, float z, int count, float dx, float dy, float dz, float speed)
        implements CustomPacketPayload {
    /** What the particles look like. Travels by ordinal: append new ones at the end. */
    public enum Look {
        /** Flames (vanilla flame). */
        FIRE,
        /** Blue soul fire. */
        SOUL_FIRE,
        /** A blast: puffs of smoke and a flash (vanilla explosion). */
        EXPLOSION,
        /** A big blast (vanilla explosion emitter). */
        BIG_EXPLOSION,
        /** Motes of white light (vanilla end rod). */
        MOTE,
        /** The glint of a hard hit (vanilla crit). */
        CRIT,
        /** Burning pops that arc and fall (vanilla lava). */
        LAVA,
        /** Electric sparks. */
        SPARK,
        /** Rising souls. */
        SOUL,
        /** A sickly green puff (vanilla sneeze). */
        SNEEZE,
        /** Green motes (vanilla happy villager). */
        HAPPY,
        /** A white puff (vanilla poof and cloud). */
        POOF,
        /** Drops of water (vanilla splash and bubble pop). */
        SPLASH,
        /** Purple wisps (vanilla portal). */
        PORTAL,
        /** Black ink smoke. */
        INK,
        /** Flakes of ice. */
        SNOW,
        /** Cyan motes (vanilla glow). */
        GLOW,
        /** A flash of light. */
        FLASH,
        /** Grey smoke. */
        SMOKE,
        /** Coloured dust: a mote if it is bright, a puff if it is dark. */
        DUST,
        /** Drops of blood. */
        BLOOD,
        /** Leaves. */
        LEAF
    }

    public static final Type<SkillParticlesPayload> TYPE = new Type<>(BeyondSolo.id("skill_particles"));
    public static final StreamCodec<ByteBuf, SkillParticlesPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                FriendlyByteBuf f = new FriendlyByteBuf(buf);
                f.writeVarInt(p.look.ordinal());
                f.writeInt(p.color);
                f.writeFloat(p.x);
                f.writeFloat(p.y);
                f.writeFloat(p.z);
                f.writeVarInt(p.count);
                f.writeFloat(p.dx);
                f.writeFloat(p.dy);
                f.writeFloat(p.dz);
                f.writeFloat(p.speed);
            },
            buf -> {
                FriendlyByteBuf f = new FriendlyByteBuf(buf);
                Look look = Look.values()[Math.floorMod(f.readVarInt(), Look.values().length)];
                return new SkillParticlesPayload(look, f.readInt(), f.readFloat(), f.readFloat(), f.readFloat(), f.readVarInt(),
                        f.readFloat(), f.readFloat(), f.readFloat(), f.readFloat());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
