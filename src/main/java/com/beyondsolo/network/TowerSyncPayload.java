package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: the player's Crimson Citadel records, as {@code tower.TowerData} NBT (for the CITADEL page). */
public record TowerSyncPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<TowerSyncPayload> TYPE = new Type<>(BeyondSolo.id("tower_sync"));
    public static final StreamCodec<ByteBuf, TowerSyncPayload> STREAM_CODEC =
            ByteBufCodecs.COMPOUND_TAG.map(TowerSyncPayload::new, TowerSyncPayload::data);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
