package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: a quest moment to animate (accepted, completed, failed, an objective done).
 *
 * @param quest  the quest as it was at that moment
 * @param detail the fail reason ordinal for FAILED, the objective index for OBJECTIVE
 */
public record QuestEventPayload(int kind, CompoundTag quest, int detail) implements CustomPacketPayload {
    public static final int ACCEPTED = 0;
    public static final int COMPLETED = 1;
    public static final int FAILED = 2;
    public static final int OBJECTIVE = 3;
    /** Objectives done, the quest waits to be handed in. */
    public static final int READY = 4;

    public static final Type<QuestEventPayload> TYPE = new Type<>(BeyondSolo.id("quest_event"));
    public static final StreamCodec<ByteBuf, QuestEventPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, QuestEventPayload::kind,
            ByteBufCodecs.COMPOUND_TAG, QuestEventPayload::quest,
            ByteBufCodecs.VAR_INT, QuestEventPayload::detail,
            QuestEventPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
