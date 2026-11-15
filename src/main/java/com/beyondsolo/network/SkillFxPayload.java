package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: a skill effect to draw in the world.
 *
 * @param entityId the caster (or, for MARK, the marked creature)
 * @param kind     which effect
 * @param color    RGB tint (usually the skill's colour)
 * @param x        a second point the effect needs (bolt end, dash start, meteor landing...)
 * @param size     radius or length in blocks
 * @param duration how long a lasting effect stays (ticks); 0 for one-off bursts. A few one-off kinds use it to pick a look
 */
public record SkillFxPayload(int entityId, Kind kind, int color, float x, float y, float z, float size, int duration)
        implements CustomPacketPayload {
    public enum Kind {
        /** The magic circle that flares under every cast. */
        CAST,
        /** A crescent of light swept in front of the caster. */
        SLASH,
        /** Afterimages from (x,y,z) to the caster. */
        DASH,
        /** A wave rolling out along the ground. */
        PULSE,
        /** Shock rings at chest height and a flash (war cry, provoke). */
        ROAR,
        /** A ground slam: cracked ring, debris, dust. */
        SLAM,
        /** Vanished at (x,y,z), appeared at the caster. */
        BLINK,
        /** A coloured aura around the caster for a while (buffs). */
        AURA,
        /** A bolt of light from the caster's hand to (x,y,z). */
        BOLT,
        /** A ring of fire rushing out from the caster. */
        NOVA,
        /** A burning star falling onto (x,y,z). */
        METEOR,
        /** A shield bubble around the caster for a while. */
        DOME,
        /** A column of light and rising sparks (heals, cures). */
        HEAL,
        /** A lasting circle on the ground at (x,y,z) (sanctuary, arrow rain). */
        ZONE,
        /** A sigil hovering over the marked creature. */
        MARK,
        /** A streak of light shot from the caster to (x,y,z). */
        SHOT,
        /** Smoke and shadow swallowing the caster. */
        VEIL,
        /** Something rolling along the ground from the caster to (x,y,z), {@code size} wide. Duration picks the look: 0 fire, 1 rock spikes, 2 a charge's wake. */
        WAVE,
        /** Blades of light circling the caster for {@code duration} ticks. */
        SPIN,
        /** Streaks thrown in a fan from the caster towards (x,y,z). */
        FAN,
        /** A burst at (x,y,z). Duration picks the look: 0 water, 1 an impact. */
        SPLASH,
        /** A whirlwind standing at (x,y,z) for {@code duration} ticks. */
        VORTEX,
        /** A column from the sky onto (x,y,z); duration is the warning before it lands (0 = a lightning strike at once). */
        PILLAR,
        /** A ring of petals thrown out from the caster. */
        BLOSSOM,
        /** Thorns and chains of shadow holding the ground at (x,y,z) for {@code duration} ticks. */
        BIND,
        /** Streaks of light raining onto the circle at (x,y,z) for {@code duration} ticks. */
        RAIN,
        /** The element a creature is carrying: a mote circling it for {@code duration} ticks (entity = the creature). */
        ELEMENT,
        /** An element reaction at (x,y,z); duration is the {@code SkillReaction} ordinal (entity = the creature). */
        REACTION,
        /** A giant blade of light falling onto (x,y,z); size = blade length, duration = ticks until it lands. */
        CLEAVE,
        /** A war banner of light standing at (x,y,z) for {@code duration} ticks; size = the radius it commands. */
        BANNER,
        /** Spectral blades circling the entity at radius {@code size} for {@code duration} ticks. */
        BLADES,
        /** Glowing fissures tearing outwards over the ground from (x,y,z) to radius {@code size}. */
        CRACK,
        /** A fountain bursting up from (x,y,z) to height {@code size}. */
        GEYSER,
        /** Shaking ground: rings rolling outwards from (x,y,z) to radius {@code size} for {@code duration} ticks. */
        QUAKE
    }

    public static final Type<SkillFxPayload> TYPE = new Type<>(BeyondSolo.id("skill_fx"));
    public static final StreamCodec<ByteBuf, SkillFxPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                FriendlyByteBuf f = new FriendlyByteBuf(buf);
                f.writeVarInt(p.entityId);
                f.writeVarInt(p.kind.ordinal());
                f.writeInt(p.color);
                f.writeFloat(p.x);
                f.writeFloat(p.y);
                f.writeFloat(p.z);
                f.writeFloat(p.size);
                f.writeVarInt(p.duration);
            },
            buf -> {
                FriendlyByteBuf f = new FriendlyByteBuf(buf);
                int id = f.readVarInt();
                Kind kind = Kind.values()[Math.floorMod(f.readVarInt(), Kind.values().length)];
                return new SkillFxPayload(id, kind, f.readInt(), f.readFloat(), f.readFloat(), f.readFloat(), f.readFloat(), f.readVarInt());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
