package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: something done in the storage or the shop.
 *
 * @param action one of the constants below
 * @param entry  the shop entry id (BUY)
 * @param amount how many to buy (BUY), or the inventory slot (SELL, SELL_ALL)
 */
public record EconomyActionPayload(int action, String entry, int amount) implements CustomPacketPayload {
    /** Open the System storage. */
    public static final int OPEN_STORAGE = 0;
    /** Buy {@code amount} of shop entry {@code entry}. */
    public static final int BUY = 1;
    /** Sell the stack in inventory slot {@code amount}. */
    public static final int SELL = 2;
    /** Sell every stack of the same item as inventory slot {@code amount}. */
    public static final int SELL_ALL = 3;

    public static final Type<EconomyActionPayload> TYPE = new Type<>(BeyondSolo.id("economy_action"));
    public static final StreamCodec<ByteBuf, EconomyActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EconomyActionPayload::action,
            ByteBufCodecs.STRING_UTF8, EconomyActionPayload::entry,
            ByteBufCodecs.VAR_INT, EconomyActionPayload::amount,
            EconomyActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
