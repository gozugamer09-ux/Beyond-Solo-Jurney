package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: the hunter's home as the HOME page shows it ({@code home} package, shown by {@code client.home}):
 * how much of it they own and what the next part asks for, whether they are in it (or a guest in someone's), the time
 * before they can go home again, their mother, their rest, their best at the dummies, the great threats they have slain,
 * and the party members whose homes they may visit.
 */
public record HomeSyncPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<HomeSyncPayload> TYPE = new Type<>(BeyondSolo.id("home_sync"));
    public static final StreamCodec<ByteBuf, HomeSyncPayload> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG.map(HomeSyncPayload::new, HomeSyncPayload::data);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
