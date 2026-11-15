package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: something done in the System forge.
 *
 * @param action REFINE or INFUSE
 * @param slot   the inventory slot of the gear (0-35 inventory, 36-39 armor, 40 off hand)
 * @param arg    REFINE: 1 to use a ward seal. INFUSE: the element's ordinal
 */
public record ForgeActionPayload(int action, int slot, int arg) implements CustomPacketPayload {
    public static final int REFINE = 0;
    public static final int INFUSE = 1;

    public static final Type<ForgeActionPayload> TYPE = new Type<>(BeyondSolo.id("forge_action"));
    public static final StreamCodec<ByteBuf, ForgeActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ForgeActionPayload::action,
            ByteBufCodecs.VAR_INT, ForgeActionPayload::slot,
            ByteBufCodecs.VAR_INT, ForgeActionPayload::arg,
            ForgeActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
