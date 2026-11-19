package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: an association window to open or refresh, the office ({@code association.Association#OFFICE}) or
 * the gate broker's ({@code BROKER}), with everything it shows and the answer to the last button pressed. Built by
 * {@code association.Association} and {@code association.Broker}, read by {@code client.association.AssociationClient}.
 */
public record AssociationSyncPayload(int screen, CompoundTag data) implements CustomPacketPayload {
    public static final Type<AssociationSyncPayload> TYPE = new Type<>(BeyondSolo.id("association_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AssociationSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AssociationSyncPayload::screen,
            ByteBufCodecs.COMPOUND_TAG, AssociationSyncPayload::data,
            AssociationSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
