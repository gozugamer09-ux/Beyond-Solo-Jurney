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
 * @param duration how long a lasting effect stays (ticks); 0 for one-off bursts
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
        VEIL
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
