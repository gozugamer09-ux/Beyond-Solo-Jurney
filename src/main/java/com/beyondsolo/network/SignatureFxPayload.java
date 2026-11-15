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
 * Server -> client: a weapon signature art, in two beats. {@link #WINDUP} starts the wielder's body animation and the
 * gathering effects; {@link #IMPACT} is the strike frame, when the damage lands. {@code points} carry what the
 * effect needs to know about the world: struck targets, a dash's start and end, where a star hit.
 */
public record SignatureFxPayload(int entityId, Signature signature, int phase, List<Vec3> points) implements CustomPacketPayload {
    public static final int WINDUP = 0;
    public static final int IMPACT = 1;

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
