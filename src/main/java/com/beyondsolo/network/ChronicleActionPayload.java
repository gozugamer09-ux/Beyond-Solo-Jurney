package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server, from the CHRONICLE page: wear a title ({@code arg}: its key; the server checks it was earned), take
 * it off, mark a title or a journal page as read ({@code arg}: its key or page id), or ask for the rankings.
 */
public record ChronicleActionPayload(int action, String arg) implements CustomPacketPayload {
    public static final int EQUIP = 0;
    public static final int UNEQUIP = 1;
    public static final int READ = 2;
    public static final int RANKINGS = 3;

    public static final Type<ChronicleActionPayload> TYPE = new Type<>(BeyondSolo.id("chronicle_action"));
    public static final StreamCodec<ByteBuf, ChronicleActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChronicleActionPayload::action,
            ByteBufCodecs.stringUtf8(256), ChronicleActionPayload::arg,
            ChronicleActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
