package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: the hunter news ({@code news.HunterNews}). At login every headline kept ({@code reset}); after
 * that each new one as it runs ({@code flash}: the ticker shows it). {@code headlines} is a list of
 * {@code news.Headline} tags.
 */
public record NewsPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<NewsPayload> TYPE = new Type<>(BeyondSolo.id("news"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NewsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, NewsPayload::data,
            NewsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
