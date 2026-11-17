package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: turn the dungeon key in inventory slot {@code slot} (from the GATES tab), or turn a key for the
 * next run from a cleared cave (the re-run prompt; {@code slot} is -1 and the server picks the key). The server
 * checks everything again.
 */
public record KeyActionPayload(int action, int slot) implements CustomPacketPayload {
    public static final int TURN = 0;
    public static final int RERUN = 1;

    public static final Type<KeyActionPayload> TYPE = new Type<>(BeyondSolo.id("key_action"));
    public static final StreamCodec<ByteBuf, KeyActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, KeyActionPayload::action,
            ByteBufCodecs.VAR_INT, KeyActionPayload::slot,
            KeyActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
