package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: the player's guild as it stands (or none), the invitations waiting for them and what founding one
 * costs. Built by {@code guild.Guilds.syncTag}, read by {@code client.guild.GuildClient}. Sent on every change, at
 * login, and when a member comes or goes.
 */
public record GuildSyncPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<GuildSyncPayload> TYPE = new Type<>(BeyondSolo.id("guild_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GuildSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, GuildSyncPayload::data, GuildSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
