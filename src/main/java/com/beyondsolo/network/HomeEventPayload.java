package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: a moment of going home. {@link #CHANNEL_START}: the hunter stands still while they are taken,
 * {@code ticks} long, {@code text} where to; {@link #CHANNEL_CANCEL}: they moved or were hit, {@code text} why;
 * {@link #ARRIVED}: they are home (or at a party member's), {@code text} whose and {@code ticks} the tier;
 * {@link #LEFT}: they went back out into the world; {@link #OUTCOME}: how a button on the HOME page went
 * ({@code ticks} 1 done, 0 refused; {@code text} the line).
 */
public record HomeEventPayload(int kind, int ticks, Component text) implements CustomPacketPayload {
    public static final int CHANNEL_START = 0, CHANNEL_CANCEL = 1, ARRIVED = 2, LEFT = 3, OUTCOME = 4;

    public static final Type<HomeEventPayload> TYPE = new Type<>(BeyondSolo.id("home_event"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HomeEventPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HomeEventPayload::kind,
            ByteBufCodecs.VAR_INT, HomeEventPayload::ticks,
            ComponentSerialization.STREAM_CODEC, HomeEventPayload::text,
            HomeEventPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
