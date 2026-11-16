package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: a keyed run, for the run clock, the cleared banner and the re-run prompt.
 *
 * @param kind    one of the constants below
 * @param rank    the run's rank (ordinal); for {@link #RERUN}, the key's rank
 * @param chain   the run's place in its chain (1 for the first)
 * @param started game time the run's clock started ({@link #START})
 * @param ticks   the clear time ({@link #CLEARED})
 * @param best    the best time for the rank, -1 for none ({@link #CLEARED})
 * @param flags   {@link #NEW_BEST}; for {@link #RERUN}, the key's style (0 plain, 1 sealed, 2 ascendant)
 * @param label   the dungeon's name ({@link #START}), or the key's name ({@link #RERUN})
 */
public record KeyRunPayload(int kind, int rank, int chain, long started, long ticks, long best, int flags, Component label)
        implements CustomPacketPayload {
    /** A keyed run is under way around this player (or they came back to it). */
    public static final int START = 0;
    /** The boss fell: the run's time. */
    public static final int CLEARED = 1;
    /** The player is out of the run. */
    public static final int END = 2;
    /** A key of this rank can be turned for the next run, right here. */
    public static final int RERUN = 3;

    public static final int NEW_BEST = 1;

    public static final Type<KeyRunPayload> TYPE = new Type<>(BeyondSolo.id("key_run"));
    public static final StreamCodec<RegistryFriendlyByteBuf, KeyRunPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.kind);
                buf.writeVarInt(p.rank);
                buf.writeVarInt(p.chain);
                buf.writeVarLong(p.started);
                buf.writeLong(p.ticks);
                buf.writeLong(p.best);
                buf.writeVarInt(p.flags);
                ComponentSerialization.STREAM_CODEC.encode(buf, p.label);
            },
            buf -> new KeyRunPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readLong(),
                    buf.readLong(), buf.readVarInt(), ComponentSerialization.STREAM_CODEC.decode(buf)));

    public static KeyRunPayload end() {
        return new KeyRunPayload(END, 0, 0, 0, 0, -1, 0, Component.empty());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
