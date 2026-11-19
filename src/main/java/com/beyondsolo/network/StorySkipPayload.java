package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: the hunter pressed the hide key to skip the story scene on their screen. */
public record StorySkipPayload() implements CustomPacketPayload {
    public static final StorySkipPayload INSTANCE = new StorySkipPayload();
    public static final Type<StorySkipPayload> TYPE = new Type<>(BeyondSolo.id("story_skip"));
    public static final StreamCodec<ByteBuf, StorySkipPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
