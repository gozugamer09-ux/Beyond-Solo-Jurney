package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: the hunter took offer {@code index} (0..2) of the blessed box in {@code hand} (0 main, 1 off), whose
 * offers were shown from {@code seed}. The server checks the box is still there with that seed
 * ({@code reward.RewardBoxes#pick}).
 */
public record RewardBoxPickPayload(int hand, long seed, int index) implements CustomPacketPayload {
    public static final Type<RewardBoxPickPayload> TYPE = new Type<>(BeyondSolo.id("reward_box_pick"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RewardBoxPickPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RewardBoxPickPayload::hand,
            ByteBufCodecs.VAR_LONG, RewardBoxPickPayload::seed,
            ByteBufCodecs.VAR_INT, RewardBoxPickPayload::index,
            RewardBoxPickPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
