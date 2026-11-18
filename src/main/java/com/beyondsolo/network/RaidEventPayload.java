package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: a raid moment. {@link #BRIEF} opens the briefing (the player walked into a raid gate);
 * {@link #BANNER} puts a line across the screen ({@code title}, {@code text}, {@code data.color}, {@code data.icon});
 * {@link #CATACLYSM} warns that one lands in {@code data.ticks}; {@link #RESULTS} shows how the raid went
 * ({@code data}: who dealt what, the best of them, what it paid); {@link #OUTCOME} answers a briefing button
 * ({@code data.ok}).
 */
public record RaidEventPayload(int kind, CompoundTag data, Component title, Component text) implements CustomPacketPayload {
    public static final int BRIEF = 0, BANNER = 1, CATACLYSM = 2, RESULTS = 3, OUTCOME = 4;

    public static final Type<RaidEventPayload> TYPE = new Type<>(BeyondSolo.id("raid_event"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RaidEventPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RaidEventPayload::kind,
            ByteBufCodecs.COMPOUND_TAG, RaidEventPayload::data,
            ComponentSerialization.STREAM_CODEC, RaidEventPayload::title,
            ComponentSerialization.STREAM_CODEC, RaidEventPayload::text,
            RaidEventPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
