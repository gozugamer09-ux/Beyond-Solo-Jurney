package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: the rankings boards ({@code chronicle.ranking.Rankings#boards}), for the RANKINGS section. */
public record RankingsPayload(CompoundTag boards) implements CustomPacketPayload {
    public static final Type<RankingsPayload> TYPE = new Type<>(BeyondSolo.id("rankings"));
    public static final StreamCodec<ByteBuf, RankingsPayload> STREAM_CODEC =
            ByteBufCodecs.COMPOUND_TAG.map(RankingsPayload::new, RankingsPayload::boards);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
