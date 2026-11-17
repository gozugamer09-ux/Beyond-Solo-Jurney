package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;

/**
 * Server -> client: a dungeon key's moments in the world.
 *
 * @param kind     one of the constants below
 * @param entity   the player turning the key, or -1
 * @param x        where the lock forms: the middle of the gate to come
 * @param yaw      which way the gate will face (degrees, {@code Direction.toYRot})
 * @param rank     the key's rank (ordinal)
 * @param style    0 plain, 1 sealed to a boss, 2 ascendant
 * @param duration ticks the turn takes ({@link #TURN})
 */
public record KeyFxPayload(int kind, int entity, double x, double y, double z, float yaw, int rank, int style, int duration)
        implements CustomPacketPayload {
    /** A key is being turned: a lock of light forms in the air ahead and cracks. */
    public static final int TURN = 0;
    /** The turn stopped short: the lock fades. */
    public static final int CANCEL = 1;
    /** The lock gave: a burst, and the gate tears open there. */
    public static final int OPEN = 2;
    /** The key won't turn (sent to its holder: stop the hold the client started). */
    public static final int REFUSE = 3;
    /** A key came back to its holder (its gate was never entered). */
    public static final int REFUND = 4;

    public static final Type<KeyFxPayload> TYPE = new Type<>(BeyondSolo.id("key_fx"));
    public static final StreamCodec<ByteBuf, KeyFxPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.kind);
                buf.writeInt(p.entity);
                buf.writeDouble(p.x);
                buf.writeDouble(p.y);
                buf.writeDouble(p.z);
                buf.writeFloat(p.yaw);
                buf.writeByte(p.rank);
                buf.writeByte(p.style);
                buf.writeShort(p.duration);
            },
            buf -> new KeyFxPayload(buf.readByte(), buf.readInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(),
                    buf.readByte(), buf.readByte(), buf.readShort()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
