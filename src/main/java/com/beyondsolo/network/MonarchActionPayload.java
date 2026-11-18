package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: a monarch's crown key, their pick at the throne, or skipping the coronation. */
public record MonarchActionPayload(int action, int value) implements CustomPacketPayload {
    /** Use the power on a crown key ({@code value}: 0 domain, 1 dread, 2 crown form, 3 authority). */
    public static final int POWER = 0;
    /** The crown picked at the throne ({@code value}: the dominion). */
    public static final int CHOOSE = 1;
    /** The coronation was skipped on their screen. */
    public static final int SKIP = 2;

    public static final Type<MonarchActionPayload> TYPE = new Type<>(BeyondSolo.id("monarch_action"));
    public static final StreamCodec<ByteBuf, MonarchActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MonarchActionPayload::action,
            ByteBufCodecs.VAR_INT, MonarchActionPayload::value,
            MonarchActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
