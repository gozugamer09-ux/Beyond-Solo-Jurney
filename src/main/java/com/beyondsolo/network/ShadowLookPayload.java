package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: how one of the player's shadows looks (the creature's saved data it keeps, {@code ShadowForm#look}),
 * asked for by the Shadows tab ({@link ShadowActionPayload#LOOK}). Kept out of the army's own sync, which goes out with
 * every bit of XP a shadow earns, because a look can run to a few kilobytes.
 *
 * @param shadow the shadow's record id
 * @param look   its look (empty: the kind's own)
 */
public record ShadowLookPayload(String shadow, CompoundTag look) implements CustomPacketPayload {
    public static final Type<ShadowLookPayload> TYPE = new Type<>(BeyondSolo.id("shadow_look"));
    public static final StreamCodec<ByteBuf, ShadowLookPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ShadowLookPayload::shadow,
            ByteBufCodecs.COMPOUND_TAG, ShadowLookPayload::look,
            ShadowLookPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
