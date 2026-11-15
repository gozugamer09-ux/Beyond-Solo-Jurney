package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: the player's shadow army.
 *
 * @param data     the saved army ({@code ShadowData} NBT) plus {@code active} (record ids out in the world),
 *                 {@code cap} (army size), {@code limit} (how many can be out at once) and {@code canRaise}
 */
public record ShadowSyncPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<ShadowSyncPayload> TYPE = new Type<>(BeyondSolo.id("shadow_sync"));
    public static final StreamCodec<ByteBuf, ShadowSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, ShadowSyncPayload::data,
            ShadowSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
