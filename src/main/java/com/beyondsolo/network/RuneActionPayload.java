package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: carve the engraving rune held in {@code hand} (0 main, 1 off) into the skill {@code skill}.
 * The server checks everything again.
 */
public record RuneActionPayload(int hand, String skill) implements CustomPacketPayload {
    public static final Type<RuneActionPayload> TYPE = new Type<>(BeyondSolo.id("rune_action"));
    public static final StreamCodec<ByteBuf, RuneActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RuneActionPayload::hand,
            ByteBufCodecs.stringUtf8(64), RuneActionPayload::skill,
            RuneActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
