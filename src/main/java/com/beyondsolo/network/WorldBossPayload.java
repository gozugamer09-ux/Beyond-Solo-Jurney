package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: world bosses and monarchs ({@code worldboss} package), shown by {@code client.worldboss}. Every
 * message names the boss's kind ({@code WorldBossKind} ordinal), its event (one boss in the world, or one monarch's
 * descent), its entity (-1 when it has none yet) and where it is.
 *
 * <ul>
 *   <li>{@link #STATUS}: about every two seconds to every player in its world while it lives (or a monarch is on its
 *       way): phase, a = its health in thousandths, b = ticks until a monarch lands (0 once it has).</li>
 *   <li>{@link #APPEAR}: it has risen (a world boss) or landed (a monarch): the banner, for players within reach.</li>
 *   <li>{@link #DESCENT}: a gate has torn open in the sky and a monarch is coming down through it. a = ticks to land.</li>
 *   <li>{@link #PHASE}: it entered a phase (the phase field).</li>
 *   <li>{@link #DOMAIN}: a monarch laid down its domain. a = its radius in blocks.</li>
 *   <li>{@link #AUTHORITY}: a monarch is calling on its authority. a = ticks until it lands.</li>
 *   <li>{@link #SHARE}: this player's share of the damage, in thousandths (a), and their place among the hunters (b).</li>
 *   <li>{@link #SLAIN}: it fell. a = this player's share in thousandths (-1: they took no part), b = the XP it paid them.</li>
 *   <li>{@link #WITHDRAW}: it left the world without falling (no hunter came for it, or none was left).</li>
 *   <li>{@link #GONE}: forget it (its event ended).</li>
 *   <li>{@link #WAR}: a ruler is coming to make war on the monarch abroad (boss: the ruler; a = the monarch's kind
 *       ordinal; b = ticks until the ruler lands, 0 once it has).</li>
 *   <li>{@link #SENTENCE}: a ruler is coming to pass sentence on a crowned hunter (a = ticks until it lands; b = 1 for
 *       the hunter judged, 0 for everyone else).</li>
 *   <li>{@link #BLESSED}: a ruler blessed this hunter for fighting at its side (a = the blessing's seconds).</li>
 * </ul>
 * The rulers of light use the same messages; the client keeps a ruler apart from the great threat abroad by its tier.
 */
public record WorldBossPayload(int kind, int boss, long event, int entity, BlockPos pos, int phase, int a, int b) implements CustomPacketPayload {
    public static final int STATUS = 0;
    public static final int APPEAR = 1;
    public static final int DESCENT = 2;
    public static final int PHASE = 3;
    public static final int DOMAIN = 4;
    public static final int AUTHORITY = 5;
    public static final int SHARE = 6;
    public static final int SLAIN = 7;
    public static final int WITHDRAW = 8;
    public static final int GONE = 9;
    public static final int WAR = 10;
    public static final int SENTENCE = 11;
    public static final int BLESSED = 12;

    public static final Type<WorldBossPayload> TYPE = new Type<>(BeyondSolo.id("world_boss"));
    public static final StreamCodec<ByteBuf, WorldBossPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                writeVar(buf, p.kind);
                writeVar(buf, p.boss);
                buf.writeLong(p.event);
                writeVar(buf, p.entity + 1);
                buf.writeLong(p.pos.asLong());
                writeVar(buf, p.phase);
                buf.writeInt(p.a);
                buf.writeInt(p.b);
            },
            buf -> new WorldBossPayload(readVar(buf), readVar(buf), buf.readLong(), readVar(buf) - 1, BlockPos.of(buf.readLong()), readVar(buf),
                    buf.readInt(), buf.readInt()));

    private static void writeVar(ByteBuf buf, int v) {
        net.minecraft.network.codec.ByteBufCodecs.VAR_INT.encode(buf, v);
    }

    private static int readVar(ByteBuf buf) {
        return net.minecraft.network.codec.ByteBufCodecs.VAR_INT.decode(buf);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
