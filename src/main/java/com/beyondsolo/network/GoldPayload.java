package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: gold changed.
 *
 * @param delta  how much (negative for a spend)
 * @param total  the new balance
 * @param reason {@code Economy.Reason} ordinal
 */
public record GoldPayload(long delta, long total, int reason) implements CustomPacketPayload {
    public static final Type<GoldPayload> TYPE = new Type<>(BeyondSolo.id("gold"));
    public static final StreamCodec<ByteBuf, GoldPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, GoldPayload::delta,
            ByteBufCodecs.VAR_LONG, GoldPayload::total,
            ByteBufCodecs.VAR_INT, GoldPayload::reason,
            GoldPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
