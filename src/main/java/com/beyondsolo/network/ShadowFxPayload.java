package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: a moment of the shadow army, drawn by {@code client.shadow.ShadowWorldFx} and
 * {@code ShadowOverlay}.
 *
 * @param kind  what happened
 * @param id    the soul's id (soul kinds) or the shadow entity's id (the others)
 * @param x     where (x, y, z)
 * @param color RGB
 * @param extra tries left (SOUL, FAILED), ritual ticks (RITUAL), 1 for a boss (EXTRACTED), the new rank (RANK_UP,
 *              PROMOTED), the master's entity id (ESSENCE)
 * @param text  the creature's type id (SOUL), the shadow's {@code ShadowRecord.wireName()} (EXTRACTED, RANK_UP,
 *              PROMOTED, NAMED), so each client reads the name in its own language, or the essence gained (ESSENCE)
 */
public record ShadowFxPayload(int kind, int id, float x, float y, float z, int color, int extra, String text) implements CustomPacketPayload {
    public enum Kind {
        /** A soul lingers where a creature fell: it can be extracted. */
        SOUL,
        /** The soul faded or was used. */
        SOUL_GONE,
        /** The extraction ritual started on a soul. */
        RITUAL,
        /** The soul answered: a shadow rises. */
        EXTRACTED,
        /** The soul refused. */
        FAILED,
        /** Out of tries: the soul crumbles to nothing. */
        CRUMBLE,
        /** A shadow was called out of the ground. */
        SUMMON,
        /** A shadow went back into the dark. */
        RECALL,
        /** A shadow climbed a rank. */
        RANK_UP,
        /** Its master promoted a shadow: extra = the new rank, text = its wire name. */
        PROMOTED,
        /** A soul faded into its master's shadow as essence: id = the soul, extra = the master's entity id, text = how much. */
        ESSENCE,
        /** Its master gave a shadow a name: text = its wire name. */
        NAMED
    }

    public static final Type<ShadowFxPayload> TYPE = new Type<>(BeyondSolo.id("shadow_fx"));
    public static final StreamCodec<ByteBuf, ShadowFxPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        ByteBufCodecs.VAR_INT.encode(buf, p.kind);
        ByteBufCodecs.VAR_INT.encode(buf, p.id);
        buf.writeFloat(p.x);
        buf.writeFloat(p.y);
        buf.writeFloat(p.z);
        buf.writeInt(p.color);
        ByteBufCodecs.VAR_INT.encode(buf, p.extra);
        ByteBufCodecs.STRING_UTF8.encode(buf, p.text);
    }, buf -> new ShadowFxPayload(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), buf.readFloat(), buf.readFloat(),
            buf.readFloat(), buf.readInt(), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf)));

    public Kind kindEnum() {
        Kind[] all = Kind.values();
        return all[Math.max(0, Math.min(all.length - 1, kind))];
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
