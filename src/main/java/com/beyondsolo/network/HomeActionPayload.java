package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: a button on the HOME page. {@link #VISIT} names the party member in {@code args} ({@code host});
 * the server checks everything again.
 */
public record HomeActionPayload(int action, CompoundTag args) implements CustomPacketPayload {
    public static final int REFRESH = 0, BUY = 1, GO = 2, LEAVE = 3, VISIT = 4, CANCEL = 5;

    public static final Type<HomeActionPayload> TYPE = new Type<>(BeyondSolo.id("home_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HomeActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HomeActionPayload::action,
            ByteBufCodecs.COMPOUND_TAG, HomeActionPayload::args,
            HomeActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
