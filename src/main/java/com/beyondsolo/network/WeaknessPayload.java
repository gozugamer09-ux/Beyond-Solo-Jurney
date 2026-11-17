package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * Server -> client: the weaknesses a player has found ({@code depths.WeaknessLog}), each "profile key|element".
 * {@code reset} replaces what the client knows (on login); otherwise the entries are added.
 */
public record WeaknessPayload(boolean reset, List<String> entries) implements CustomPacketPayload {
    public static final Type<WeaknessPayload> TYPE = new Type<>(BeyondSolo.id("weaknesses"));
    public static final StreamCodec<ByteBuf, WeaknessPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, WeaknessPayload::reset,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(4096)), WeaknessPayload::entries,
            WeaknessPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
