package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: the player's quests (a saved {@code QuestData}) plus the server's game time. */
public record QuestSyncPayload(CompoundTag data, long gameTime) implements CustomPacketPayload {
    public static final Type<QuestSyncPayload> TYPE = new Type<>(BeyondSolo.id("sync_quests"));
    public static final StreamCodec<ByteBuf, QuestSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, QuestSyncPayload::data,
            ByteBufCodecs.VAR_LONG, QuestSyncPayload::gameTime,
            QuestSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
