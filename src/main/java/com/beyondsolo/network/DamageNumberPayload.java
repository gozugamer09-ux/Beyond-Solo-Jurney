package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: show a damage number over an entity. element = index in DamageElement.all(). */
public record DamageNumberPayload(int entityId, float amount, int element, boolean crit) implements CustomPacketPayload {
    public static final Type<DamageNumberPayload> TYPE = new Type<>(BeyondSolo.id("damage_number"));
    public static final StreamCodec<ByteBuf, DamageNumberPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DamageNumberPayload::entityId,
            ByteBufCodecs.FLOAT, DamageNumberPayload::amount,
            ByteBufCodecs.VAR_INT, DamageNumberPayload::element,
            ByteBufCodecs.BOOL, DamageNumberPayload::crit,
            DamageNumberPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
