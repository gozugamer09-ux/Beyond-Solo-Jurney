package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: open or refresh a villager's request window. See {@code NpcService.boardTag} for the fields. */
public record NpcBoardPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<NpcBoardPayload> TYPE = new Type<>(BeyondSolo.id("npc_board"));
    public static final StreamCodec<ByteBuf, NpcBoardPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, NpcBoardPayload::data, NpcBoardPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
