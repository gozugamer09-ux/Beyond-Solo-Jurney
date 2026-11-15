package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: craft a System recipe {@code times} times, or (empty recipe id) send me what my System storage
 * holds so the CRAFT tab can count it.
 */
public record CraftActionPayload(String recipe, int times) implements CustomPacketPayload {
    public static final Type<CraftActionPayload> TYPE = new Type<>(BeyondSolo.id("craft_action"));
    public static final StreamCodec<ByteBuf, CraftActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CraftActionPayload::recipe,
            ByteBufCodecs.VAR_INT, CraftActionPayload::times,
            CraftActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
