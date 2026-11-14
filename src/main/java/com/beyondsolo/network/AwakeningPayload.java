package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: a moment of the awakening trial for the overlay, or an effect of the Arbiter's attacks to draw
 * in the world.
 *
 * @param kind  one of the constants below
 * @param value AWAKEN: 1 if the Arbiter was slain; MARK: ticks until the pillar strikes; WAVE: how far the ring runs
 * @param x     world position (MARK, WAVE, TETHER: the gate)
 */
public record AwakeningPayload(int kind, float value, double x, double y, double z) implements CustomPacketPayload {
    /** The sealed gate tore open for you. */
    public static final int CALLED = 0;
    /** You walked too far and the gate pulled you back. */
    public static final int TETHER = 1;
    /** You stepped into the trial. */
    public static final int TRIAL_ARRIVE = 2;
    /** You fell before reaching the Arbiter and got up again at the last cavern. */
    public static final int REVIVE = 3;
    /** You fell to the Arbiter (or killed it): the System binds to you. */
    public static final int AWAKEN = 4;
    /** A judgment mark on the floor; a pillar of light strikes it after {@code value} ticks. */
    public static final int MARK = 5;
    /** A shockwave ring running out along the floor from here. */
    public static final int WAVE = 6;
    /** The Arbiter was killed. */
    public static final int SLAIN = 7;

    public static final Type<AwakeningPayload> TYPE = new Type<>(BeyondSolo.id("awakening"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AwakeningPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AwakeningPayload::kind,
            ByteBufCodecs.FLOAT, AwakeningPayload::value,
            ByteBufCodecs.DOUBLE, AwakeningPayload::x,
            ByteBufCodecs.DOUBLE, AwakeningPayload::y,
            ByteBufCodecs.DOUBLE, AwakeningPayload::z,
            AwakeningPayload::new);

    public static AwakeningPayload of(int kind) {
        return new AwakeningPayload(kind, 0, 0, 0, 0);
    }

    public static AwakeningPayload of(int kind, float value) {
        return new AwakeningPayload(kind, value, 0, 0, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
