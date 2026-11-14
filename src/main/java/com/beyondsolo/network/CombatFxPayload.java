package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: play a one-off combat effect on an entity (a pop, an ignition, a cure...). */
public record CombatFxPayload(int entityId, Kind kind, int param) implements CustomPacketPayload {
    public enum Kind {
        /** Blood buildup popped. */
        BLOOD_POP,
        /** Deep burn took hold. */
        IGNITE,
        /** A venom stack landed. param = stacks. */
        VENOM_STACK,
        /** Venom turned heavy. */
        HEAVY_VENOM,
        /** Crimson buildup burst and branded the target. */
        CRIMSON_BURST,
        /** A remedy cured something. param = DamageElement index. */
        CURE,
        /** A combat roll (dust trail). */
        ROLL
    }

    public static final Type<CombatFxPayload> TYPE = new Type<>(BeyondSolo.id("combat_fx"));
    public static final StreamCodec<ByteBuf, CombatFxPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CombatFxPayload::entityId,
            ByteBufCodecs.VAR_INT.map(i -> Kind.values()[Math.floorMod(i, Kind.values().length)], Kind::ordinal), CombatFxPayload::kind,
            ByteBufCodecs.VAR_INT, CombatFxPayload::param,
            CombatFxPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
