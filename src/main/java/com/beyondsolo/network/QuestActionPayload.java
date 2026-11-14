package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** Client -> server: accept, decline or abandon a quest. ACCEPT_LATEST ignores the id. */
public record QuestActionPayload(int action, UUID quest) implements CustomPacketPayload {
    public static final int ACCEPT = 0;
    public static final int DECLINE = 1;
    public static final int ABANDON = 2;
    public static final int ACCEPT_LATEST = 3;
    public static final UUID NONE = new UUID(0, 0);

    public static final Type<QuestActionPayload> TYPE = new Type<>(BeyondSolo.id("quest_action"));
    public static final StreamCodec<ByteBuf, QuestActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, QuestActionPayload::action,
            UUIDUtil.STREAM_CODEC, QuestActionPayload::quest,
            QuestActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
