package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Server -> client: the villagers (and travellers) around the player that get a marker, and which one. */
public record NpcMarkersPayload(List<Integer> entities, List<Integer> states) implements CustomPacketPayload {
    public static final Type<NpcMarkersPayload> TYPE = new Type<>(BeyondSolo.id("npc_markers"));
    public static final StreamCodec<ByteBuf, NpcMarkersPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), NpcMarkersPayload::entities,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), NpcMarkersPayload::states,
            NpcMarkersPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
