package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: the raid the player is on the roster of, as it stands (an empty tag: none). The muster and its
 * roster for the briefing; inside, every raider's and hunter's health, the lieutenants and pylons left, the boss's
 * health and the next cataclysm, for the raid HUD. {@code dungeon} and {@code boss} name the cave and its boss. Built by
 * {@code raid.Raids.syncTag}, read by {@code client.raid.RaidClient}.
 */
public record RaidSyncPayload(CompoundTag data, Component dungeon, Component boss) implements CustomPacketPayload {
    public static final Type<RaidSyncPayload> TYPE = new Type<>(BeyondSolo.id("raid_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RaidSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, RaidSyncPayload::data,
            ComponentSerialization.STREAM_CODEC, RaidSyncPayload::dungeon,
            ComponentSerialization.STREAM_CODEC, RaidSyncPayload::boss,
            RaidSyncPayload::new);

    public static RaidSyncPayload none() {
        return new RaidSyncPayload(new CompoundTag(), Component.empty(), Component.empty());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
