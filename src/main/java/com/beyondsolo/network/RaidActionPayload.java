package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: a button of the raid briefing. {@link #JOIN} the muster of the raid gate in {@code args.gate},
 * {@link #LEAVE} it, pick a {@link #ROLE} ({@code args.role}), {@link #START} it (the leader), {@link #BRING} the
 * guild's hunters or not ({@code args.on}).
 */
public record RaidActionPayload(int action, CompoundTag args) implements CustomPacketPayload {
    public static final int JOIN = 0, LEAVE = 1, ROLE = 2, START = 3, BRING = 4;

    public static final Type<RaidActionPayload> TYPE = new Type<>(BeyondSolo.id("raid_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RaidActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RaidActionPayload::action,
            ByteBufCodecs.COMPOUND_TAG, RaidActionPayload::args,
            RaidActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
