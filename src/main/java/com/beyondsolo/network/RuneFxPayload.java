package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: a rune effect to draw. The rune stone's own moments (it shatters and its light pours into the
 * player, it fizzles, an engraving takes) and the looks of the rune arts.
 *
 * @param entityId the player (or, for TERROR and FLURRY, the creature); -1 for effects at a point
 * @param kind     which effect
 * @param color    RGB tint
 * @param x        a point the effect needs (where it lands, where it ends...)
 * @param dx       a direction (cones, lines), or zero
 * @param size     radius or length in blocks
 * @param duration how long it lasts in ticks (0 = a one-off)
 * @param extra    a small number the kind needs (the rune's glyph and grade, an engraving, strikes...)
 */
public record RuneFxPayload(int entityId, Kind kind, int color, float x, float y, float z, float dx, float dy, float dz,
                            float size, int duration, int extra) implements CustomPacketPayload {
    public enum Kind {
        /** The rune stone breaks in the player's hands and its light pours into them. extra = glyph + 16 * grade + 256 * outcome. */
        SHATTER,
        /** The rune won't answer: a puff of dust, the channel stops (to the user only). */
        FIZZLE,
        /** An engraving took: light from the rune into the player. extra = the engraving. */
        ENGRAVED,
        /** The engraving screen was refused (to the user only). */
        ENGRAVE_REFUSED,
        /** Hunter's Howl: arcs of air rolling out in front of the caster, a ring at their feet. */
        HOWL,
        /** Skittering Brood: a brood of tiny shadows pouring from the caster towards (x,y,z), size = spread. */
        SWARM,
        /** Crystal Carapace: shards thrown from the entity to (x,y,z). */
        SHARDS,
        /** Hollow Lance: a lance of hollow light from the caster to (x,y,z), wisps flowing back. */
        LANCE,
        /** Ashen Bulwark: a tower shield of ash in front of the caster for {@code duration}; 0 = it flashes (a blow landed on it). */
        WALL,
        /** Cinder Wisp: a burning wisp flying from the caster to (x,y,z) in {@code duration} ticks, then bursting (size = radius). */
        WISP,
        /** Titan's Fall: the landing, rock thrown up around (x,y,z), size = radius. */
        IMPACT,
        /** Widow's Snare: a web on the ground at (x,y,z) for {@code duration} ticks, size = radius. */
        WEB,
        /** Dread Presence: a dark wave rolling out from the caster to radius {@code size}. */
        DREAD,
        /** A creature frozen with terror for {@code duration} ticks (entity = the creature). */
        TERROR,
        /** Phantom Flurry: {@code extra} cuts on the creature (entity) over {@code duration} ticks. */
        FLURRY,
        /** Sigil Cascade: a sigil under the creature (entity) that bursts after {@code duration} ticks, size = radius. */
        SIGIL,
        /** Wyrmfire Roar: a cone of fire along (dx,dy,dz), size = length, for {@code duration} ticks. */
        BREATH,
        /** A rune crystallised out of a dying creature at (x,y,z): a column of its light. extra = glyph + 16 * grade. */
        DROPPED
    }

    public static final Type<RuneFxPayload> TYPE = new Type<>(BeyondSolo.id("rune_fx"));
    public static final StreamCodec<ByteBuf, RuneFxPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                FriendlyByteBuf f = new FriendlyByteBuf(buf);
                f.writeVarInt(p.entityId + 1);
                f.writeVarInt(p.kind.ordinal());
                f.writeInt(p.color);
                f.writeFloat(p.x);
                f.writeFloat(p.y);
                f.writeFloat(p.z);
                f.writeFloat(p.dx);
                f.writeFloat(p.dy);
                f.writeFloat(p.dz);
                f.writeFloat(p.size);
                f.writeVarInt(p.duration);
                f.writeVarInt(p.extra);
            },
            buf -> {
                FriendlyByteBuf f = new FriendlyByteBuf(buf);
                int id = f.readVarInt() - 1;
                Kind kind = Kind.values()[Math.floorMod(f.readVarInt(), Kind.values().length)];
                return new RuneFxPayload(id, kind, f.readInt(), f.readFloat(), f.readFloat(), f.readFloat(), f.readFloat(), f.readFloat(),
                        f.readFloat(), f.readFloat(), f.readVarInt(), f.readVarInt());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
