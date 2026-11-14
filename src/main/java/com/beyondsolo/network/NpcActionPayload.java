package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** Client -> server: a button in a villager's request window (accept a slot, hand in a quest, trade, close). */
public record NpcActionPayload(int action, int entity, int slot, UUID quest) implements CustomPacketPayload {
    public static final Type<NpcActionPayload> TYPE = new Type<>(BeyondSolo.id("npc_action"));
    public static final StreamCodec<ByteBuf, NpcActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, NpcActionPayload::action,
            ByteBufCodecs.VAR_INT, NpcActionPayload::entity,
            ByteBufCodecs.VAR_INT, NpcActionPayload::slot,
            UUIDUtil.STREAM_CODEC, NpcActionPayload::quest,
            NpcActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
