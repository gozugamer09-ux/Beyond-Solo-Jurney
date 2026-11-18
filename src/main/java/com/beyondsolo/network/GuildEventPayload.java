package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: how a button on the GUILD page went ({@link #OUTCOME}: {@code value} 1 done, 0 refused), or a
 * guild moment for the screen: {@link #FOUNDED} (the crest is raised: {@code value} the crest, {@code text} the name),
 * {@link #LEVEL_UP} ({@code value} the new level).
 */
public record GuildEventPayload(int kind, int value, Component text) implements CustomPacketPayload {
    public static final int OUTCOME = 0, FOUNDED = 1, LEVEL_UP = 2;

    public static final Type<GuildEventPayload> TYPE = new Type<>(BeyondSolo.id("guild_event"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GuildEventPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GuildEventPayload::kind,
            ByteBufCodecs.VAR_INT, GuildEventPayload::value,
            ComponentSerialization.STREAM_CODEC, GuildEventPayload::text,
            GuildEventPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
