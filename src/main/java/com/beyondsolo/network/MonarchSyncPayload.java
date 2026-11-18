package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: the player's monarch state.
 *
 * @param dominion what they rule (a {@code Dominion} ordinal), or -1 before they are crowned
 * @param called   the System has called them to the throne
 * @param minLevel the level the throne asks for
 */
public record MonarchSyncPayload(int dominion, boolean called, int minLevel) implements CustomPacketPayload {
    public static final Type<MonarchSyncPayload> TYPE = new Type<>(BeyondSolo.id("monarch_sync"));
    public static final StreamCodec<ByteBuf, MonarchSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MonarchSyncPayload::dominion,
            ByteBufCodecs.BOOL, MonarchSyncPayload::called,
            ByteBufCodecs.VAR_INT, MonarchSyncPayload::minLevel,
            MonarchSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
