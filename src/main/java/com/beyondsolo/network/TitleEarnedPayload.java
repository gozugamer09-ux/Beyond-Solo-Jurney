package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: a title was earned (its lang key): the TITLE ACQUIRED moment. {@code worn}: it was put on. */
public record TitleEarnedPayload(String key, boolean worn) implements CustomPacketPayload {
    public static final Type<TitleEarnedPayload> TYPE = new Type<>(BeyondSolo.id("title_earned"));
    public static final StreamCodec<ByteBuf, TitleEarnedPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, TitleEarnedPayload::key,
            ByteBufCodecs.BOOL, TitleEarnedPayload::worn,
            TitleEarnedPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
