package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: a moment of the light. The sanctum and the seraph form go to everyone who can see the ruler (they
 * are drawn round them for everyone); the covenant, the call, the vigil and the Investiture only to the hunter.
 *
 * @param kind   one of the constants
 * @param entity the ruler's (or the Envoy's) entity id
 * @param color  RGB of their light
 * @param size   a sanctum's radius, a mantle's ordinal, a wave; see each kind
 * @param ticks  how long it lasts (0 ends it)
 */
public record LightFxPayload(int kind, int entity, int color, int size, int ticks) implements CustomPacketPayload {
    /** A sanctum of light rises round the ruler for {@code ticks} (0: it fades); {@code size} its radius. */
    public static final int SANCTUM = 0;
    /** Seraph form: wings of light and a halo on them for {@code ticks} (0: they fold away). */
    public static final int SERAPH = 1;
    /** Judgement: the light's eye opening over everything near ({@code size}: how far). */
    public static final int JUDGEMENT = 2;
    /** An authority: its name on the screens of everyone near ({@code size}: the mantle). */
    public static final int AUTHORITY = 3;
    /** Their sanctum held a monarch's domain back ({@code size}: the monarch's entity id). */
    public static final int CLASH = 4;
    /** The Envoy speaks: the covenant on the hunter's screen ({@code entity}: the Envoy; {@code size}: 1 if they turned it away before). */
    public static final int COVENANT = 5;
    /** The light calls the hunter to the Investiture. */
    public static final int CALLED = 6;
    /**
     * The vigil: {@code size} is the wave (0 the light gathering, 1 to 3 a wave, 4 the last one down, -1 it failed,
     * -2 they left the ring); {@code ticks} the foes left (while gathering: ticks to the first wave; out of the ring:
     * ticks before it fails).
     */
    public static final int VIGIL = 7;
    /** The Envoy asks the hunter which mantle to take up ({@code size}: the one their class leans to; {@code ticks}: how long it waits). */
    public static final int CHOOSE = 8;
    /** The mantle is theirs: the Investiture on their screen ({@code size}: the mantle). */
    public static final int INVESTITURE = 9;
    /** The chapel is near: its hidden wall hums on their screen ({@code ticks}: how long). {@code size} 1: the wall just came down. */
    public static final int HUM = 10;
    /** The first step into the chapel: the light's words on their screen ({@code entity}: the Envoy). */
    public static final int ENTER = 11;

    public static final Type<LightFxPayload> TYPE = new Type<>(BeyondSolo.id("light_fx"));
    public static final StreamCodec<ByteBuf, LightFxPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LightFxPayload::kind,
            ByteBufCodecs.VAR_INT, LightFxPayload::entity,
            ByteBufCodecs.INT, LightFxPayload::color,
            ByteBufCodecs.VAR_INT, LightFxPayload::size,
            ByteBufCodecs.VAR_INT, LightFxPayload::ticks,
            LightFxPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
