package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: where the player is in a Penalty Zone sentence, for the overlay, the timer and the zone's edge.
 *
 * @param phase     one of the constants ({@link #NONE} hides everything)
 * @param tick      ticks the phase has run
 * @param length    the phase's length in ticks (for the survival: the whole sentence)
 * @param remaining ticks left to survive
 * @param kills     creatures killed in the zone so far
 * @param event     a moment to show once: {@link #EV_SURGE}, {@link #EV_FINAL}, {@link #EV_ERUPT}, {@link #EV_EDGE}
 * @param cx        the zone's centre (x)
 * @param cz        the zone's centre (z)
 * @param radius    the zone's edge, in blocks
 * @param reason    what was failed (the quest's title)
 */
public record PenaltyPayload(int phase, int tick, int length, int remaining, int kills, int event, int cx, int cz, int radius,
                             Component reason) implements CustomPacketPayload {
    public static final int NONE = 0;
    public static final int WARNING = 1;
    public static final int ARRIVAL = 2;
    public static final int SURVIVE = 3;
    public static final int SURVIVED = 4;
    public static final int FAILED = 5;

    public static final int EV_NONE = 0;
    public static final int EV_SURGE = 1;
    public static final int EV_FINAL = 2;
    public static final int EV_ERUPT = 3;
    public static final int EV_EDGE = 4;

    public static final Type<PenaltyPayload> TYPE = new Type<>(BeyondSolo.id("penalty"));

    private static final StreamCodec<ByteBuf, int[]> INTS = StreamCodec.of(
            (buf, a) -> {
                ByteBufCodecs.VAR_INT.encode(buf, a.length);
                for (int v : a) ByteBufCodecs.VAR_INT.encode(buf, v);
            },
            buf -> {
                int[] a = new int[Math.min(16, ByteBufCodecs.VAR_INT.decode(buf))];
                for (int i = 0; i < a.length; i++) a[i] = ByteBufCodecs.VAR_INT.decode(buf);
                return a;
            });

    public static final StreamCodec<RegistryFriendlyByteBuf, PenaltyPayload> STREAM_CODEC = StreamCodec.composite(
            INTS.cast(), p -> new int[]{p.phase, p.tick, p.length, p.remaining, p.kills, p.event, p.cx, p.cz, p.radius},
            ComponentSerialization.TRUSTED_STREAM_CODEC, PenaltyPayload::reason,
            (a, reason) -> new PenaltyPayload(at(a, 0), at(a, 1), at(a, 2), at(a, 3), at(a, 4), at(a, 5), at(a, 6), at(a, 7), at(a, 8), reason));

    private static int at(int[] a, int i) {
        return i < a.length ? a[i] : 0;
    }

    public static PenaltyPayload none() {
        return new PenaltyPayload(NONE, 0, 0, 0, 0, EV_NONE, 0, 0, 0, Component.empty());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
