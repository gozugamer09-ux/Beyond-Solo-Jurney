package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: a button on the CITADEL page. Climb the castle (from the last checkpoint), challenge the throne
 * again, climb the Endless Spire, enter a boss rush ({@code arg}: the tier's ordinal) or leave. The server checks
 * everything again. Also {@link #ARRIVED}: the hall is on screen (the arrival's title card waits for it).
 */
public record TowerActionPayload(int action, int arg) implements CustomPacketPayload {
    public static final int CLIMB = 0;
    public static final int THRONE = 1;
    public static final int ENDLESS = 2;
    public static final int RUSH = 3;
    public static final int LEAVE = 4;
    public static final int ARRIVED = 5;

    public static final Type<TowerActionPayload> TYPE = new Type<>(BeyondSolo.id("tower_action"));
    public static final StreamCodec<ByteBuf, TowerActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TowerActionPayload::action,
            ByteBufCodecs.VAR_INT, TowerActionPayload::arg,
            TowerActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
