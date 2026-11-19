package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: the master gives one of their shadows a name, from the Shadows page (an empty name takes the given
 * name back). The server cleans and checks it again ({@code ShadowPromotions.name}).
 *
 * @param shadow the record id
 * @param name   the name as typed (at most 32 characters are read)
 */
public record ShadowNamePayload(String shadow, String name) implements CustomPacketPayload {
    public static final Type<ShadowNamePayload> TYPE = new Type<>(BeyondSolo.id("shadow_name"));
    public static final StreamCodec<ByteBuf, ShadowNamePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), ShadowNamePayload::shadow,
            ByteBufCodecs.stringUtf8(32), ShadowNamePayload::name,
            ShadowNamePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
