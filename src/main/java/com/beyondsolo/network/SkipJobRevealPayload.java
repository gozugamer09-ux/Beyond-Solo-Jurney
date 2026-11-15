package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: the player skipped the job change reveal, so the trial can send them home sooner. */
public record SkipJobRevealPayload() implements CustomPacketPayload {
    public static final SkipJobRevealPayload INSTANCE = new SkipJobRevealPayload();
    public static final Type<SkipJobRevealPayload> TYPE = new Type<>(BeyondSolo.id("skip_job_reveal"));
    public static final StreamCodec<ByteBuf, SkipJobRevealPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
