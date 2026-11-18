package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: a quest moment to animate (accepted, completed, failed, an objective done, changed).
 *
 * @param quest    the quest as it was at that moment
 * @param detail   the fail reason ordinal for FAILED, the objective index for OBJECTIVE, 1 for a BRANCHED that paid
 *                 out the quest it replaced
 * @param previous BRANCHED: the quest it replaced (empty otherwise)
 */
public record QuestEventPayload(int kind, CompoundTag quest, int detail, CompoundTag previous) implements CustomPacketPayload {
    public static final int ACCEPTED = 0;
    public static final int COMPLETED = 1;
    public static final int FAILED = 2;
    public static final int OBJECTIVE = 3;
    /** Objectives done, the quest waits to be handed in. */
    public static final int READY = 4;
    /** The situation changed and the System rewrote the quest into another one. */
    public static final int BRANCHED = 5;

    public QuestEventPayload(int kind, CompoundTag quest, int detail) {
        this(kind, quest, detail, new CompoundTag());
    }

    public static final Type<QuestEventPayload> TYPE = new Type<>(BeyondSolo.id("quest_event"));
    public static final StreamCodec<ByteBuf, QuestEventPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, QuestEventPayload::kind,
            ByteBufCodecs.COMPOUND_TAG, QuestEventPayload::quest,
            ByteBufCodecs.VAR_INT, QuestEventPayload::detail,
            ByteBufCodecs.COMPOUND_TAG, QuestEventPayload::previous,
            QuestEventPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
