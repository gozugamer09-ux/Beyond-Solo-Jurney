package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: the Halcyon raid ({@code isle} package), shown by {@code client.isle}. One message, its {@code kind}
 * field saying what it is:
 * <ul>
 *   <li>{@code state}: the raid as this hunter sees it (about once a second while they are called or on it): phase,
 *   countdowns, waves, nests, hunters and allies standing, where the nests are.</li>
 *   <li>{@code banner}: a moment in the middle of the screen (the call, the landing, a wave, a nest burning, the hive
 *   opening, the king's birth, the island retaken or lost).</li>
 *   <li>{@code result}: the raid is over: what it paid this hunter, and the hunters' tally.</li>
 *   <li>{@code clear}: forget it.</li>
 * </ul>
 */
public record IslePayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<IslePayload> TYPE = new Type<>(BeyondSolo.id("isle"));
    public static final StreamCodec<ByteBuf, IslePayload> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG.map(IslePayload::new, IslePayload::data);

    public String kind() {
        return data.getString("kind");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
