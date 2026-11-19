package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: how poisoned the hunter is ({@code tenths} of a point, 0..1000) and the flags of
 * {@code manapoison.ManaPoisoning} (in poisoning mana, being purified, under Clear Breath, burst just now). Sent when
 * either changes; read by {@code client.manapoison.ManaPoisonClient}.
 */
public record ManaPoisonPayload(int tenths, int flags) implements CustomPacketPayload {
    public static final Type<ManaPoisonPayload> TYPE = new Type<>(BeyondSolo.id("mana_poisoning"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ManaPoisonPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ManaPoisonPayload::tenths,
            ByteBufCodecs.VAR_INT, ManaPoisonPayload::flags,
            ManaPoisonPayload::new);

    public float level() {
        return tenths / 10f;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
