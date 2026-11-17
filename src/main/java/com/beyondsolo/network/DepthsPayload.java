package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: the depths of the cave the player is in ({@code depths} package).
 *
 * <ul>
 *   <li>{@link #STATE}: a = weather ordinal (-1: not in a cave), b = the gate's rank ordinal. Sent on the way in and out.</li>
 *   <li>{@link #TRAP}: a trap room sprung around the player. a = trap kind, b = how far the room reaches from its
 *       middle, x y z = the room's middle, title/subtitle = name and hint.</li>
 *   <li>{@link #TRAP_DONE}: the trap is beaten. a = trap kind, b = room index.</li>
 *   <li>{@link #WAVE}: a lockdown's wave. a = wave, b = waves in all.</li>
 *   <li>{@link #HIDDEN}: a hidden chamber was found. a = its kind, b = the gate's rank, x y z = the wall that opened,
 *       title/subtitle = the System's words and the chamber's name.</li>
 *   <li>{@link #HIDDEN_ENTER}: stepping into it (the shrine's blessing, the lair waking). a = its kind, b = the gate's
 *       rank, x y z = the chamber's floor.</li>
 * </ul>
 */
public record DepthsPayload(int kind, int a, int b, float x, float y, float z, Component title, Component subtitle) implements CustomPacketPayload {
    public static final int STATE = 0;
    public static final int TRAP = 1;
    public static final int TRAP_DONE = 2;
    public static final int WAVE = 3;
    public static final int HIDDEN = 4;
    public static final int HIDDEN_ENTER = 5;

    public static final Type<DepthsPayload> TYPE = new Type<>(BeyondSolo.id("depths"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DepthsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.kind);
                buf.writeVarInt(p.a);
                buf.writeVarInt(p.b);
                buf.writeFloat(p.x);
                buf.writeFloat(p.y);
                buf.writeFloat(p.z);
                ComponentSerialization.STREAM_CODEC.encode(buf, p.title);
                ComponentSerialization.STREAM_CODEC.encode(buf, p.subtitle);
            },
            buf -> new DepthsPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    ComponentSerialization.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf)));

    public static DepthsPayload state(int weather, int rank) {
        return new DepthsPayload(STATE, weather, rank, 0, 0, 0, Component.empty(), Component.empty());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
