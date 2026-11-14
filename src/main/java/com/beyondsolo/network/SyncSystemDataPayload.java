package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: the player's full System state. */
public record SyncSystemDataPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<SyncSystemDataPayload> TYPE = new Type<>(BeyondSolo.id("sync_system"));
    public static final StreamCodec<ByteBuf, SyncSystemDataPayload> STREAM_CODEC =
            ByteBufCodecs.COMPOUND_TAG.map(SyncSystemDataPayload::new, SyncSystemDataPayload::data);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
