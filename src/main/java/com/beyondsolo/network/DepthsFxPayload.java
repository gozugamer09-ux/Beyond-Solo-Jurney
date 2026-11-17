package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: a moment of the depths in the world, drawn by {@code client.depths.DepthsWorldFx}.
 *
 * <ul>
 *   <li>{@link #KNOCK}: a knock on a hidden wall (x y z: the block, a: knocks so far, b: knocks needed).</li>
 *   <li>{@link #REVEAL}: Pulse Scan shows what is hidden (x y z: the scan's middle, a: its radius, b: seconds).</li>
 *   <li>{@link #OPEN}: a hidden wall crumbles (x y z: the middle of the wall).</li>
 *   <li>{@link #BOLT}: a mana storm's bolt comes down (x y z: where it lands).</li>
 *   <li>{@link #SHATTER}: a trap core breaks (x y z: the core).</li>
 *   <li>{@link #CORE_HIT}: a trap core is struck but holds (x y z: the core, a: hits so far, b: hits needed).</li>
 *   <li>{@link #BLESS}: the shrine's blessing comes down on someone (x y z: their feet).</li>
 * </ul>
 */
public record DepthsFxPayload(int kind, float x, float y, float z, float a, float b, int color) implements CustomPacketPayload {
    public static final int KNOCK = 0;
    public static final int REVEAL = 1;
    public static final int OPEN = 2;
    public static final int BOLT = 3;
    public static final int SHATTER = 4;
    public static final int CORE_HIT = 5;
    public static final int BLESS = 6;

    public static final Type<DepthsFxPayload> TYPE = new Type<>(BeyondSolo.id("depths_fx"));
    public static final StreamCodec<FriendlyByteBuf, DepthsFxPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.kind);
                buf.writeFloat(p.x);
                buf.writeFloat(p.y);
                buf.writeFloat(p.z);
                buf.writeFloat(p.a);
                buf.writeFloat(p.b);
                buf.writeInt(p.color);
            },
            buf -> new DepthsFxPayload(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
