package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: the player's gold, storage size, expansions, gate clears and today's shop purchases
 * (see {@code Economy.sync}).
 */
public record EconomySyncPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<EconomySyncPayload> TYPE = new Type<>(BeyondSolo.id("economy_sync"));
    public static final StreamCodec<ByteBuf, EconomySyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, EconomySyncPayload::data,
            EconomySyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
