package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: spend {@code count} stat points (as many as the player has, at most) on the stat with this index. */
public record AllocateStatPayload(int stat, int count) implements CustomPacketPayload {
    public static final Type<AllocateStatPayload> TYPE = new Type<>(BeyondSolo.id("allocate_stat"));
    public static final StreamCodec<ByteBuf, AllocateStatPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AllocateStatPayload::stat,
            ByteBufCodecs.VAR_INT, AllocateStatPayload::count,
            AllocateStatPayload::new);

    public AllocateStatPayload(int stat) {
        this(stat, 1);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
