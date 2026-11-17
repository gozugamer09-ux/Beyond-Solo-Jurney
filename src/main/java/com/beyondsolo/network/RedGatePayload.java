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
 * Server -> client: a red gate as one player sees it, from inside (the clock, the cold, the way to its master) or from
 * outside (a red gate standing near them, with hunters trapped in it), plus one-off moments for the overlay.
 *
 * @param v    the numbers, by the {@code I_*} indices below (a short array keeps the codec simple)
 * @param boss the master of the red gate's land (its name), or the summary's place name
 */
public record RedGatePayload(int[] v, Component boss) implements CustomPacketPayload {
    public static final int NONE = 0;
    public static final int INSIDE = 1;
    public static final int OUTSIDE = 2;

    /** {@link #I_PHASE}: the gate has turned red and still lets the party follow; sealed; the way out open; over. */
    public static final int OPEN = 0;
    public static final int SEALED = 1;
    public static final int RELEASED = 2;
    public static final int ENDED = 3;

    public static final int EV_NONE = 0;
    /** Arrived inside: the red warning window. */
    public static final int EV_WARNING = 1;
    public static final int EV_SEALED = 2;
    public static final int EV_NIGHT = 3;
    public static final int EV_DAWN = 4;
    public static final int EV_RAID = 5;
    public static final int EV_BLIZZARD = 6;
    public static final int EV_CALM = 7;
    public static final int EV_CAMP = 8;
    /** The red gate's hold broke on the release day: the way out is open, its master still alive. */
    public static final int EV_RELEASED = 9;
    /** Its master fell: the way out is open. */
    public static final int EV_CLEARED = 10;
    public static final int EV_EDGE = 11;
    /** Back home: the summary card (cleared, escaped, or fallen inside). */
    public static final int EV_SUMMARY_CLEARED = 12;
    public static final int EV_SUMMARY_ESCAPED = 13;
    public static final int EV_SUMMARY_FAILED = 14;
    /** Outside, nearby: a gate has just turned red. */
    public static final int EV_TURNED = 15;
    public static final int EV_FREEZING = 16;

    public static final int I_MODE = 0;
    public static final int I_PHASE = 1;
    public static final int I_RANK = 2;
    public static final int I_INSIDE_RANK = 3;
    public static final int I_DAY = 4;
    public static final int I_TIME = 5;
    public static final int I_RATE = 6;
    public static final int I_BLIZZARD = 7;
    public static final int I_CHILL = 8;
    public static final int I_PARTY = 9;
    public static final int I_KILLS = 10;
    public static final int I_RELEASE_DAY = 11;
    public static final int I_TARGET_X = 12;
    public static final int I_TARGET_Z = 13;
    public static final int I_FLAGS = 14;
    public static final int I_CX = 15;
    public static final int I_CZ = 16;
    public static final int I_RADIUS = 17;
    public static final int I_EVENT = 18;
    public static final int I_CAMPS = 19;
    public static final int I_CAMPS_TOTAL = 20;
    public static final int I_XP = 21;
    public static final int I_SEAL_LEFT = 22;
    public static final int SIZE = 23;

    /** {@link #I_FLAGS} bits. */
    public static final int F_BOSS_ALIVE = 1;
    public static final int F_CLEARED = 2;
    public static final int F_BOSS_AWAKE = 4;
    public static final int F_NIGHT = 8;
    public static final int F_WARM = 16;
    /** Nothing to point at yet (the patch is still being planned). */
    public static final int F_NO_TARGET = 32;

    public static final Type<RedGatePayload> TYPE = new Type<>(BeyondSolo.id("red_gate"));

    private static final StreamCodec<ByteBuf, int[]> INTS = StreamCodec.of(
            (buf, a) -> {
                ByteBufCodecs.VAR_INT.encode(buf, a.length);
                for (int x : a) ByteBufCodecs.VAR_INT.encode(buf, x);
            },
            buf -> {
                int[] a = new int[Math.min(64, ByteBufCodecs.VAR_INT.decode(buf))];
                for (int i = 0; i < a.length; i++) a[i] = ByteBufCodecs.VAR_INT.decode(buf);
                return a;
            });

    public static final StreamCodec<RegistryFriendlyByteBuf, RedGatePayload> STREAM_CODEC = StreamCodec.composite(
            INTS.cast(), RedGatePayload::v,
            ComponentSerialization.TRUSTED_STREAM_CODEC, RedGatePayload::boss,
            RedGatePayload::new);

    public int get(int i) {
        return i < v.length ? v[i] : 0;
    }

    public int mode() {
        return get(I_MODE);
    }

    public int event() {
        return get(I_EVENT);
    }

    public boolean flag(int bit) {
        return (get(I_FLAGS) & bit) != 0;
    }

    /** Nothing to show: hides the HUD (with a summary card when {@code event} is one). */
    public static RedGatePayload none(int event) {
        int[] v = new int[SIZE];
        v[I_EVENT] = event;
        return new RedGatePayload(v, Component.empty());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
