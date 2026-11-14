package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: spend one stat point on the stat with this index. */
public record AllocateStatPayload(int stat) implements CustomPacketPayload {
    public static final Type<AllocateStatPayload> TYPE = new Type<>(BeyondSolo.id("allocate_stat"));
    public static final StreamCodec<ByteBuf, AllocateStatPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(AllocateStatPayload::new, AllocateStatPayload::stat);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
