package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: a dungeon break ({@code breaks} package). Every kind carries the gate's rank and where it stands;
 * title and subtitle are the banner's words (empty where there is no banner).
 *
 * <ul>
 *   <li>{@link #STATUS}: a gate near the player coming apart or broken, about once a second while they are near.
 *       phase: 1 unstable, 2 about to break, 3 broken. a = ticks left before it breaks (unstable, about to break) or
 *       its monsters out (broken); b = 1 once its boss is out.</li>
 *   <li>{@link #BREAK}: it broke. a = how hard this player feels it (0-100).</li>
 *   <li>{@link #BOSS_OUT}: its boss came through. a = the boss's entity id.</li>
 *   <li>{@link #SPILL}: a wave came out of it. a = how many.</li>
 *   <li>{@link #CRUMBLE}: one of its monsters turns to ash as the break ends. a = the creature's entity id, b = its
 *       height in tenths of a block; pos = where it stood.</li>
 *   <li>{@link #SUPPRESSED}: its boss fell and the gate collapsed. a = the XP it paid this player.</li>
 * </ul>
 */
public record BreakPayload(int kind, int rank, int phase, BlockPos pos, int a, int b, Component title, Component subtitle) implements CustomPacketPayload {
    public static final int STATUS = 0;
    public static final int BREAK = 1;
    public static final int BOSS_OUT = 2;
    public static final int SPILL = 3;
    public static final int CRUMBLE = 4;
    public static final int SUPPRESSED = 5;

    public static final Type<BreakPayload> TYPE = new Type<>(BeyondSolo.id("dungeon_break"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BreakPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.kind);
                buf.writeVarInt(p.rank);
                buf.writeVarInt(p.phase);
                buf.writeBlockPos(p.pos);
                buf.writeVarInt(p.a);
                buf.writeVarInt(p.b);
                ComponentSerialization.STREAM_CODEC.encode(buf, p.title);
                ComponentSerialization.STREAM_CODEC.encode(buf, p.subtitle);
            },
            buf -> new BreakPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(),
                    ComponentSerialization.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf)));

    public static BreakPayload of(int kind, int rank, int phase, BlockPos pos, int a, int b) {
        return new BreakPayload(kind, rank, phase, pos, a, b, Component.empty(), Component.empty());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
