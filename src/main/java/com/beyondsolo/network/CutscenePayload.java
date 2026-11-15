package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server -> client: one camera shot of a cutscene. The camera glides from {@code from} to {@code to} while it turns
 * from looking at {@code lookFrom} to looking at {@code lookTo}, over {@code ticks}. With {@code orbit} != 0 it instead
 * circles {@code lookTo}, starting at the offset {@code from} from it (its distance and height are kept), turning
 * {@code orbit} times around.
 * A shot with {@code ticks == 0} ends the cutscene and gives the camera back to the player.
 *
 * <p>Any system can use it for story beats (job change, awakening, boss intros): see {@code CutsceneCamera}.</p>
 */
public record CutscenePayload(Vec3 from, Vec3 to, Vec3 lookFrom, Vec3 lookTo, int ticks, float orbit, boolean letterbox)
        implements CustomPacketPayload {
    public static final Type<CutscenePayload> TYPE = new Type<>(BeyondSolo.id("cutscene"));
    public static final StreamCodec<ByteBuf, CutscenePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                FriendlyByteBuf f = new FriendlyByteBuf(buf);
                f.writeVec3(p.from);
                f.writeVec3(p.to);
                f.writeVec3(p.lookFrom);
                f.writeVec3(p.lookTo);
                f.writeVarInt(p.ticks);
                f.writeFloat(p.orbit);
                f.writeBoolean(p.letterbox);
            },
            buf -> {
                FriendlyByteBuf f = new FriendlyByteBuf(buf);
                return new CutscenePayload(f.readVec3(), f.readVec3(), f.readVec3(), f.readVec3(), f.readVarInt(), f.readFloat(), f.readBoolean());
            });

    public static CutscenePayload glide(Vec3 from, Vec3 to, Vec3 lookFrom, Vec3 lookTo, int ticks) {
        return new CutscenePayload(from, to, lookFrom, lookTo, ticks, 0, true);
    }

    public static CutscenePayload orbit(Vec3 center, double radius, double height, float startTurns, float turns, int ticks) {
        double a = startTurns * Math.PI * 2;
        Vec3 from = new Vec3(Math.cos(a) * radius, height, Math.sin(a) * radius);
        return new CutscenePayload(from, Vec3.ZERO, center, center, ticks, turns, true);
    }

    public static CutscenePayload stop() {
        return new CutscenePayload(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 0, 0, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
