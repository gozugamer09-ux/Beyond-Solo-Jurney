package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: the player's whole {@code SkillData}. */
public record SkillSyncPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<SkillSyncPayload> TYPE = new Type<>(BeyondSolo.id("skill_sync"));
    public static final StreamCodec<ByteBuf, SkillSyncPayload> STREAM_CODEC =
            ByteBufCodecs.COMPOUND_TAG.map(SkillSyncPayload::new, SkillSyncPayload::data);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
