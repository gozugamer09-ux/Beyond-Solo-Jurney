package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: a command to the shadow army, from a key or the Shadows tab.
 *
 * @param action one of the constants below
 * @param shadow the record id it is about (empty for the keys)
 */
public record ShadowActionPayload(int action, String shadow) implements CustomPacketPayload {
    /** Raise key: extract the nearest soul if there is one, otherwise call the army out. */
    public static final int RISE = 0;
    /** Recall key: every shadow back into the dark. */
    public static final int RECALL = 1;
    /** Mount key: call the mount and ride it, or get off and send it back. */
    public static final int MOUNT = 2;
    public static final int SUMMON = 3;
    public static final int DISMISS = 4;
    public static final int RELEASE = 5;
    public static final int RIDE = 6;
    /** Leave a shadow standing guard where it stands. */
    public static final int POST = 7;
    /** Call a guard off its post (it comes back to follow, or into the dark if it is far away). */
    public static final int UNPOST = 8;
    /** Promote a shadow waiting at the top of its rank (paid in shadow essence). */
    public static final int PROMOTE = 9;
    /** Ask how a shadow looks (the server answers with a {@link ShadowLookPayload}). */
    public static final int LOOK = 10;

    public static final Type<ShadowActionPayload> TYPE = new Type<>(BeyondSolo.id("shadow_action"));
    public static final StreamCodec<ByteBuf, ShadowActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ShadowActionPayload::action,
            ByteBufCodecs.STRING_UTF8, ShadowActionPayload::shadow,
            ShadowActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
