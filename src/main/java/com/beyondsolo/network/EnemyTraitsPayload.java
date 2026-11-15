package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: a gate creature's affinity, variant and affixes ({@code enemy.EnemyTraits}), sent when a player
 * starts seeing it. The client draws its aura and name plate from this.
 */
public record EnemyTraitsPayload(int entity, int affinity, int variant, int affixes) implements CustomPacketPayload {
    public static final Type<EnemyTraitsPayload> TYPE = new Type<>(BeyondSolo.id("enemy_traits"));
    public static final StreamCodec<ByteBuf, EnemyTraitsPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        ByteBufCodecs.VAR_INT.encode(buf, p.entity);
        ByteBufCodecs.VAR_INT.encode(buf, p.affinity);
        ByteBufCodecs.VAR_INT.encode(buf, p.variant);
        ByteBufCodecs.VAR_INT.encode(buf, p.affixes);
    }, buf -> new EnemyTraitsPayload(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
