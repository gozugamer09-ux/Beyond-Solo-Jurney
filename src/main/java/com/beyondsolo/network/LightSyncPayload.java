package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: the player's road in the light.
 *
 * @param sworn    they woke sworn to the light
 * @param mantle   the mantle they took up (a {@code Mantle} ordinal), or -1 before the Investiture
 * @param called   the light has called them to the Investiture
 * @param minLevel the level the Investiture asks for
 */
public record LightSyncPayload(boolean sworn, int mantle, boolean called, int minLevel) implements CustomPacketPayload {
    public static final Type<LightSyncPayload> TYPE = new Type<>(BeyondSolo.id("light_sync"));
    public static final StreamCodec<ByteBuf, LightSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, LightSyncPayload::sworn,
            ByteBufCodecs.VAR_INT, LightSyncPayload::mantle,
            ByteBufCodecs.BOOL, LightSyncPayload::called,
            ByteBufCodecs.VAR_INT, LightSyncPayload::minLevel,
            LightSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
