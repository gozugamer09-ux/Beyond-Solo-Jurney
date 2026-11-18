package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: a button on the GUILD page. What the action needs travels in {@code args}: {@code target} (a
 * player, a hunter, a recruit, a guild), {@code name}, {@code tag}, {@code crest}, {@code color}, {@code amount},
 * {@code rank}. The server checks everything again.
 */
public record GuildActionPayload(int action, CompoundTag args) implements CustomPacketPayload {
    public static final int FOUND = 0, INVITE = 1, ACCEPT = 2, DECLINE = 3, LEAVE = 4, KICK = 5, PROMOTE = 6, DEMOTE = 7,
            TRANSFER = 8, DISBAND = 9, DEPOSIT = 10, WITHDRAW = 11, HIRE = 12, DISMISS = 13, PERMIT = 14;

    public static final Type<GuildActionPayload> TYPE = new Type<>(BeyondSolo.id("guild_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GuildActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GuildActionPayload::action,
            ByteBufCodecs.COMPOUND_TAG, GuildActionPayload::args,
            GuildActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
