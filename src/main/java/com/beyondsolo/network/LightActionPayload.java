package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: a ruler's crown key, the hunter's answer to the Envoy, or the mantle they take up. */
public record LightActionPayload(int action, int value) implements CustomPacketPayload {
    /** Use the power on a crown key ({@code value}: 0 sanctum, 1 judgement, 2 seraph form, 3 authority). */
    public static final int POWER = 0;
    /** The covenant answered ({@code value}: 1 sworn to the light, 0 turned away). {@code value} 2: they only walked off. */
    public static final int COVENANT = 1;
    /** The mantle taken up at the Investiture ({@code value}: the mantle). */
    public static final int CHOOSE = 2;
    /** The Investiture was skipped on their screen. */
    public static final int SKIP = 3;

    public static final Type<LightActionPayload> TYPE = new Type<>(BeyondSolo.id("light_action"));
    public static final StreamCodec<ByteBuf, LightActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LightActionPayload::action,
            ByteBufCodecs.VAR_INT, LightActionPayload::value,
            LightActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
