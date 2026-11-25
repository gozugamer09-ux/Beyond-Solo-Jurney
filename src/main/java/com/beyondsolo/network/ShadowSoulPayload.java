package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client, right after a soul's {@link ShadowFxPayload.Kind#SOUL}: what fell there, so the client can draw the
 * creature in ink, lying in its pool, until its shadow rises (or the soul fades), and whose soul it is, so the "Extract
 * shadow" prompt only offers a player their own.
 *
 * @param soul     the soul's id (as in its {@link ShadowFxPayload})
 * @param bossRank the boss's rank, for a boss without a saved look, or -1
 * @param mine     whether the soul is the receiving player's own
 * @param life     how long (ticks) the soul lingers before it fades, unless a ritual holds it
 * @param form     the fallen creature's form, look included ({@code ShadowForm#save(true)})
 */
public record ShadowSoulPayload(int soul, int bossRank, boolean mine, int life, CompoundTag form) implements CustomPacketPayload {
    public static final Type<ShadowSoulPayload> TYPE = new Type<>(BeyondSolo.id("shadow_soul"));
    public static final StreamCodec<ByteBuf, ShadowSoulPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ShadowSoulPayload::soul,
            ByteBufCodecs.VAR_INT, ShadowSoulPayload::bossRank,
            ByteBufCodecs.BOOL, ShadowSoulPayload::mine,
            ByteBufCodecs.VAR_INT, ShadowSoulPayload::life,
            ByteBufCodecs.COMPOUND_TAG, ShadowSoulPayload::form,
            ShadowSoulPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
