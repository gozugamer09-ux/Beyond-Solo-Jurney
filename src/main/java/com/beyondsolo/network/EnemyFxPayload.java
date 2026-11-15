package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: a warning on the ground before a big attack lands (a circle, a line or a cone that fills up
 * until the hit), or a burst of light where something struck. Drawn by {@code client.enemy.EnemyWorldFx}.
 *
 * @param kind   see {@link Kind}
 * @param x      where (the centre of a circle, the start of a line or cone)
 * @param dx     the direction of a line or cone (horizontal, normalised)
 * @param size   a circle's radius, a line's width, a cone's half-angle in degrees, a burst's size
 * @param length a line's or cone's length
 * @param ticks  how long until the hit lands
 * @param color  RGB
 */
public record EnemyFxPayload(int kind, float x, float y, float z, float dx, float dz, float size, float length, int ticks, int color)
        implements CustomPacketPayload {
    public enum Kind { CIRCLE, LINE, CONE, BURST, PILLAR }

    public static final Type<EnemyFxPayload> TYPE = new Type<>(BeyondSolo.id("enemy_fx"));
    public static final StreamCodec<ByteBuf, EnemyFxPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        ByteBufCodecs.VAR_INT.encode(buf, p.kind);
        buf.writeFloat(p.x);
        buf.writeFloat(p.y);
        buf.writeFloat(p.z);
        buf.writeFloat(p.dx);
        buf.writeFloat(p.dz);
        buf.writeFloat(p.size);
        buf.writeFloat(p.length);
        ByteBufCodecs.VAR_INT.encode(buf, p.ticks);
        buf.writeInt(p.color);
    }, buf -> new EnemyFxPayload(ByteBufCodecs.VAR_INT.decode(buf), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
            buf.readFloat(), buf.readFloat(), buf.readFloat(), ByteBufCodecs.VAR_INT.decode(buf), buf.readInt()));

    public Kind kindEnum() {
        Kind[] all = Kind.values();
        return all[Math.max(0, Math.min(all.length - 1, kind))];
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
