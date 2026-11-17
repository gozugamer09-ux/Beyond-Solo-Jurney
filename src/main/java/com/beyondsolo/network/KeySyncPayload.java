package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: the player's keyed-run records, as {@code key.KeyRunData} NBT. */
public record KeySyncPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<KeySyncPayload> TYPE = new Type<>(BeyondSolo.id("key_sync"));
    public static final StreamCodec<ByteBuf, KeySyncPayload> STREAM_CODEC =
            ByteBufCodecs.COMPOUND_TAG.map(KeySyncPayload::new, KeySyncPayload::data);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
