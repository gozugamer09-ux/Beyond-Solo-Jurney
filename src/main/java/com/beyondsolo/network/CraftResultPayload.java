package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: how a craft went, for the CRAFT tab's animation.
 *
 * @param recipe the recipe id
 * @param made   how many times it was made (0 if refused)
 * @param reason why it was refused, as a lang key ("" if it went ahead)
 */
public record CraftResultPayload(String recipe, int made, String reason) implements CustomPacketPayload {
    public static final Type<CraftResultPayload> TYPE = new Type<>(BeyondSolo.id("craft_result"));
    public static final StreamCodec<ByteBuf, CraftResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CraftResultPayload::recipe,
            ByteBufCodecs.VAR_INT, CraftResultPayload::made,
            ByteBufCodecs.STRING_UTF8, CraftResultPayload::reason,
            CraftResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
