package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: the player skipped the reveal on their screen, so the show in the world can wrap up too. */
public record SkipEvaluationPayload() implements CustomPacketPayload {
    public static final SkipEvaluationPayload INSTANCE = new SkipEvaluationPayload();
    public static final Type<SkipEvaluationPayload> TYPE = new Type<>(BeyondSolo.id("skip_evaluation"));
    public static final StreamCodec<ByteBuf, SkipEvaluationPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
