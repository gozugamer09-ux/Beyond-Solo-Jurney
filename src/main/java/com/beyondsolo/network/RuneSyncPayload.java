package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: the player's rune state (engravings, runes absorbed), as {@code rune.RuneData} NBT. */
public record RuneSyncPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<RuneSyncPayload> TYPE = new Type<>(BeyondSolo.id("rune_sync"));
    public static final StreamCodec<ByteBuf, RuneSyncPayload> STREAM_CODEC =
            ByteBufCodecs.COMPOUND_TAG.map(RuneSyncPayload::new, RuneSyncPayload::data);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
