package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: a duel with a national-level hunter ({@code duel.Duels}), to its hunter and everyone near its
 * ring: who, where the ring is, the count, the clock, how long the hunter has stood outside the ring, and at the end
 * the result. Sent when it changes and once a second.
 */
public record DuelPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<DuelPayload> TYPE = new Type<>(BeyondSolo.id("duel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DuelPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, DuelPayload::data,
            DuelPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
