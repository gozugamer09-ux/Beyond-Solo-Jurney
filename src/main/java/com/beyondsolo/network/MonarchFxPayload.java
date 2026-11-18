package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: a monarch's moment. Most go to everyone who can see the monarch (their domain and crown form are
 * drawn round them for everyone); {@link #CHOOSE} and {@link #CORONATION} only to the monarch themselves.
 *
 * @param kind   one of the constants
 * @param entity the monarch's entity id
 * @param color  RGB of their dominion
 * @param size   a domain's radius; for {@link #CHOOSE} and {@link #CORONATION} the dominion's ordinal
 * @param ticks  how long it lasts (0 ends it)
 */
public record MonarchFxPayload(int kind, int entity, int color, int size, int ticks) implements CustomPacketPayload {
    /** A domain unfolds round the monarch for {@code ticks} (0: it folds away). */
    public static final int DOMAIN = 0;
    /** Crown form: the monarch's aura takes shape round them for {@code ticks} (0: it fades). */
    public static final int CROWN = 1;
    /** Dread: the monarch's will, a shudder through everything near. */
    public static final int DREAD = 2;
    /** Their authority: the name of it on the screens of everyone near, and the light of it. */
    public static final int AUTHORITY = 3;
    /** Their domain held another monarch's back. */
    public static final int CLASH = 4;
    /** The throne asks the hunter which crown to take ({@code size}: the one their class leans to; {@code ticks}: how long it waits). */
    public static final int CHOOSE = 5;
    /** The crown is theirs: the coronation on their screen ({@code size}: the dominion). */
    public static final int CORONATION = 6;

    public static final Type<MonarchFxPayload> TYPE = new Type<>(BeyondSolo.id("monarch_fx"));
    public static final StreamCodec<ByteBuf, MonarchFxPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MonarchFxPayload::kind,
            ByteBufCodecs.VAR_INT, MonarchFxPayload::entity,
            ByteBufCodecs.INT, MonarchFxPayload::color,
            ByteBufCodecs.VAR_INT, MonarchFxPayload::size,
            ByteBufCodecs.VAR_INT, MonarchFxPayload::ticks,
            MonarchFxPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
