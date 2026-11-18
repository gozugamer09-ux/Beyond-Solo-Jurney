package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: the hunter's chronicle ({@code chronicle.ChronicleData} NBT) with how far along each title not yet
 * earned is ({@code progress}: title key -> value), for the CHRONICLE page.
 */
public record ChronicleSyncPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<ChronicleSyncPayload> TYPE = new Type<>(BeyondSolo.id("chronicle_sync"));
    public static final StreamCodec<ByteBuf, ChronicleSyncPayload> STREAM_CODEC =
            ByteBufCodecs.COMPOUND_TAG.map(ChronicleSyncPayload::new, ChronicleSyncPayload::data);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
