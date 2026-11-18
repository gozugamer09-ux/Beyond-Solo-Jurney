package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * Client -> server: a button on the PARTY page. {@code target} is the player the action is about (the one to invite,
 * the inviter to answer, the member to remove, promote or regroup with); unused for leaving and disbanding. The server
 * checks everything again.
 */
public record PartyActionPayload(int action, UUID target) implements CustomPacketPayload {
    public static final int INVITE = 0, ACCEPT = 1, DECLINE = 2, LEAVE = 3, KICK = 4, PROMOTE = 5, DISBAND = 6, REGROUP = 7;

    public static final Type<PartyActionPayload> TYPE = new Type<>(BeyondSolo.id("party_action"));
    public static final StreamCodec<ByteBuf, PartyActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PartyActionPayload::action,
            UUIDUtil.STREAM_CODEC, PartyActionPayload::target,
            PartyActionPayload::new);

    public static PartyActionPayload of(int action) {
        return new PartyActionPayload(action, new UUID(0, 0));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
