package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client, every second: the rival parties a hunter knows of ({@code rival.RivalParties#view}): the one in
 * their gate first, then the nearest. The HUD's race chip and the marks over a gate a party holds read it.
 */
public record RivalSyncPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<RivalSyncPayload> TYPE = new Type<>(BeyondSolo.id("rival_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RivalSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, RivalSyncPayload::data,
            RivalSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
