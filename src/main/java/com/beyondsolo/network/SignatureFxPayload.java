package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.item.arsenal.Signature;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client: a weapon signature art, beat by beat. {@link #WINDUP} starts the wielder's body animation and the
 * gathering effects (for a drawn art: the bow is charged); {@link #IMPACT} is the strike frame, when the first blow
 * lands (for a drawn art: the arrow is loosed); {@link #beat(int)} the later beats that need the world again (wounds
 * opening, a star collapsing, a pillar falling on whoever still stands); {@link #PASSIVE} a small touch of the art
 * outside its cast (a mantle's burning hit, a crit arrow's star); {@link #READY} a drawn art made ready again. {@code points} carry what the effect needs to know
 * about the world: struck targets, a dash's start and end, where a star hit.
 */
public record SignatureFxPayload(int entityId, Signature signature, int phase, List<Vec3> points) implements CustomPacketPayload {
    public static final int WINDUP = 0;
    public static final int IMPACT = 1;
    public static final int PASSIVE = 2;
    /** To the wielder alone: their drawn art is ready again before its time (an operator reset its cooldown). */
    public static final int READY = -1;
    /** Beat n (1, 2, ...) after the impact. */
    private static final int BEAT = 2;

    public static int beat(int n) {
        return BEAT + n;
    }

    /** Which beat a phase is (1, 2, ...), or 0 for the wind-up, the impact and a passive touch. */
    public static int beatOf(int phase) {
        return phase > BEAT ? phase - BEAT : 0;
    }

    private static final StreamCodec<ByteBuf, Vec3> VEC = StreamCodec.of(
            (buf, v) -> {
                buf.writeFloat((float) v.x);
                buf.writeFloat((float) v.y);
                buf.writeFloat((float) v.z);
            },
            buf -> new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()));

    public static final Type<SignatureFxPayload> TYPE = new Type<>(BeyondSolo.id("signature_fx"));
    public static final StreamCodec<ByteBuf, SignatureFxPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SignatureFxPayload::entityId,
            ByteBufCodecs.VAR_INT.map(i -> Signature.values()[Math.floorMod(i, Signature.values().length)], Signature::ordinal), SignatureFxPayload::signature,
            ByteBufCodecs.VAR_INT, SignatureFxPayload::phase,
            ByteBufCodecs.collection(ArrayList::new, VEC, 32), SignatureFxPayload::points,
            SignatureFxPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
