package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: the vigil's ring of light on the ground, for everyone near (drawn by
 * {@code client.worldboss.WorldBossWorldFx} on the lie of the land). Sent again while the vigil lasts.
 *
 * @param id     the hunter keeping the vigil (one ring each)
 * @param x      the ring's centre
 * @param y      the ground at its centre
 * @param z      the ring's centre
 * @param radius how far it reaches
 * @param color  RGB of its light
 * @param ticks  how long it stays unless sent again (0 puts it out)
 */
public record LightRingPayload(int id, float x, float y, float z, int radius, int color, int ticks) implements CustomPacketPayload {
    public static final Type<LightRingPayload> TYPE = new Type<>(BeyondSolo.id("light_ring"));
    public static final StreamCodec<ByteBuf, LightRingPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        ByteBufCodecs.VAR_INT.encode(buf, p.id);
        buf.writeFloat(p.x);
        buf.writeFloat(p.y);
        buf.writeFloat(p.z);
        ByteBufCodecs.VAR_INT.encode(buf, p.radius);
        buf.writeInt(p.color);
        ByteBufCodecs.VAR_INT.encode(buf, p.ticks);
    }, buf -> new LightRingPayload(ByteBufCodecs.VAR_INT.decode(buf), buf.readFloat(), buf.readFloat(), buf.readFloat(),
            ByteBufCodecs.VAR_INT.decode(buf), buf.readInt(), ByteBufCodecs.VAR_INT.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
