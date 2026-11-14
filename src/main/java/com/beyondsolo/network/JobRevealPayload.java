package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: plays the job change reveal (the subclass roulette and the name card).
 *
 * @param subclass the subclass the roll landed on (ordinal)
 * @param flawless the trial was flawless (the rare chance was raised); shown on the card
 */
public record JobRevealPayload(int subclass, boolean flawless) implements CustomPacketPayload {
    public static final Type<JobRevealPayload> TYPE = new Type<>(BeyondSolo.id("job_reveal"));
    public static final StreamCodec<ByteBuf, JobRevealPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, JobRevealPayload::subclass,
            ByteBufCodecs.BOOL, JobRevealPayload::flawless,
            JobRevealPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
