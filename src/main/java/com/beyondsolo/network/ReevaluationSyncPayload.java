package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: whether the crystal would re-evaluate the player now, and from which level it will next.
 * The hidden rank is never sent: the client only learns it from the re-evaluation itself.
 *
 * @param available re-evaluation is open (a new milestone was reached since the last evaluation)
 * @param nextLevel the level of the next milestone after the one already used
 */
public record ReevaluationSyncPayload(boolean available, int nextLevel) implements CustomPacketPayload {
    public static final Type<ReevaluationSyncPayload> TYPE = new Type<>(BeyondSolo.id("reevaluation_sync"));
    public static final StreamCodec<ByteBuf, ReevaluationSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ReevaluationSyncPayload::available,
            ByteBufCodecs.VAR_INT, ReevaluationSyncPayload::nextLevel,
            ReevaluationSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
