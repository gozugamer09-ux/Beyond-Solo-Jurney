package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: a moment of the player's own regroup ({@link #REGROUP_START}: they stand still while they are
 * gathered, {@code ticks} long, {@code text} the member they go to; {@link #REGROUP_CANCEL}: they moved or were hit,
 * {@code text} why; {@link #REGROUP_DONE}: they are there), a member arriving by their side ({@link #REGROUP_JOINED}),
 * or how a button on the PARTY page went ({@link #OUTCOME}: {@code ticks} 1 done, 0 refused; {@code text} the line).
 */
public record PartyEventPayload(int kind, int ticks, Component text) implements CustomPacketPayload {
    public static final int REGROUP_START = 0, REGROUP_CANCEL = 1, REGROUP_DONE = 2, REGROUP_JOINED = 3, OUTCOME = 4;

    public static final Type<PartyEventPayload> TYPE = new Type<>(BeyondSolo.id("party_event"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PartyEventPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PartyEventPayload::kind,
            ByteBufCodecs.VAR_INT, PartyEventPayload::ticks,
            ComponentSerialization.STREAM_CODEC, PartyEventPayload::text,
            PartyEventPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
