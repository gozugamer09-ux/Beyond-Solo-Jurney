package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: how a forge action went, for the forge's animation.
 *
 * @param action  ForgeActionPayload.REFINE or INFUSE
 * @param outcome Enhancement.Outcome ordinal (INFUSE: SUCCESS or INVALID)
 * @param slot    the gear's slot
 * @param before  level before (INFUSE: old element ordinal, -1 for none)
 * @param after   level after (INFUSE: new element ordinal)
 * @param reason  why it was refused, as a lang key ("" if it went ahead)
 */
public record ForgeResultPayload(int action, int outcome, int slot, int before, int after, String reason) implements CustomPacketPayload {
    public static final Type<ForgeResultPayload> TYPE = new Type<>(BeyondSolo.id("forge_result"));
    public static final StreamCodec<ByteBuf, ForgeResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ForgeResultPayload::action,
            ByteBufCodecs.VAR_INT, ForgeResultPayload::outcome,
            ByteBufCodecs.VAR_INT, ForgeResultPayload::slot,
            ByteBufCodecs.INT, ForgeResultPayload::before,
            ByteBufCodecs.INT, ForgeResultPayload::after,
            ByteBufCodecs.STRING_UTF8, ForgeResultPayload::reason,
            ForgeResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
