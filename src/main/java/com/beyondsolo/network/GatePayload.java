package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: a gate moment for the overlay (going through a gate, arriving, a boss waking up or falling,
 * the gate cleared) or a camera shake.
 *
 * @param kind    one of the constants below
 * @param rank    the gate's rank (ordinal)
 * @param entity  the boss's entity id for boss moments, else -1
 * @param value   shake strength, XP for a clear, else 0
 * @param title   big line (dungeon or boss name)
 * @param subtitle small line
 */
public record GatePayload(int kind, int rank, int entity, float value, Component title, Component subtitle) implements CustomPacketPayload {
    public static final int ENTER = 0;
    public static final int ARRIVE = 1;
    public static final int EXIT = 2;
    public static final int RETURN = 3;
    public static final int BOSS_INTRO = 4;
    public static final int BOSS_DEFEATED = 5;
    public static final int CLEARED = 6;
    public static final int SHAKE = 7;
    public static final int ENRAGED = 8;

    public static final Type<GatePayload> TYPE = new Type<>(BeyondSolo.id("gate"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GatePayload::kind,
            ByteBufCodecs.VAR_INT, GatePayload::rank,
            ByteBufCodecs.VAR_INT, GatePayload::entity,
            ByteBufCodecs.FLOAT, GatePayload::value,
            ComponentSerialization.STREAM_CODEC, GatePayload::title,
            ComponentSerialization.STREAM_CODEC, GatePayload::subtitle,
            GatePayload::new);

    public static GatePayload simple(int kind, int rank) {
        return new GatePayload(kind, rank, -1, 0, Component.empty(), Component.empty());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
