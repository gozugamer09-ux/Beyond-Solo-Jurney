package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: the player's job change state.
 *
 * @param subclass subclass ordinal, or -1 before the job change
 * @param unlocked the job change trial is open to them
 */
public record JobSyncPayload(int subclass, boolean unlocked) implements CustomPacketPayload {
    public static final Type<JobSyncPayload> TYPE = new Type<>(BeyondSolo.id("job_sync"));
    public static final StreamCodec<ByteBuf, JobSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, JobSyncPayload::subclass,
            ByteBufCodecs.BOOL, JobSyncPayload::unlocked,
            JobSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
