package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client: the gates a Pulse Scan sensed ({@code breaks.GateSense}): where each stands, its rank and how it
 * is holding, shown for {@code seconds}. {@code range} is how far the pulse reached (blocks).
 */
public record GateSensePayload(int seconds, int range, List<Entry> gates) implements CustomPacketPayload {
    /** One gate. state: {@link #HOLDING} .. {@link #CLEARED}; ticksLeft: before it breaks (-1 when not counting down). */
    public record Entry(BlockPos pos, int rank, int state, int ticksLeft) {}

    public static final int HOLDING = 0, UNSTABLE = 1, IMMINENT = 2, BROKEN = 3, SEALED = 4, CLEARED = 5;

    public static final Type<GateSensePayload> TYPE = new Type<>(BeyondSolo.id("gate_sense"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GateSensePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.seconds);
                buf.writeVarInt(p.range);
                buf.writeVarInt(p.gates.size());
                for (Entry e : p.gates) {
                    buf.writeBlockPos(e.pos);
                    buf.writeVarInt(e.rank);
                    buf.writeVarInt(e.state);
                    buf.writeVarInt(e.ticksLeft);
                }
            },
            buf -> {
                int seconds = buf.readVarInt(), range = buf.readVarInt(), n = buf.readVarInt();
                List<Entry> gates = new ArrayList<>(Math.min(n, 64));
                for (int i = 0; i < n; i++) gates.add(new Entry(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
                return new GateSensePayload(seconds, range, gates);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
