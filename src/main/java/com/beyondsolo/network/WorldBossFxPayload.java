package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: what a world boss or a monarch is about to do or just did, drawn by
 * {@code client.worldboss.WorldBossWorldFx}. Warnings on the ground follow the lie of the land (the overworld is not a
 * flat arena), so a ring on a hillside lies on the hill.
 *
 * <ul>
 *   <li>{@link Kind#CIRCLE}: a disc that fills until the hit lands {@code ticks} later; size = radius.</li>
 *   <li>{@link Kind#RING}: a band between {@code extra} (inner radius) and {@code size} (outer): the middle is safe.</li>
 *   <li>{@link Kind#WAVE}: a wall of light running out along the ground from the centre to {@code size} over
 *       {@code ticks}, {@code extra} thick; it can be jumped.</li>
 *   <li>{@link Kind#LINE}: a strip from the point along (dx, dz); size = width, length.</li>
 *   <li>{@link Kind#CONE}: a wedge from the point along (dx, dz); size = half-angle in degrees, length.</li>
 *   <li>{@link Kind#CELL}: a square on the ground; size = half its side.</li>
 *   <li>{@link Kind#METEOR}: something falls out of the sky onto the point, landing in {@code ticks}; size = how big.</li>
 *   <li>{@link Kind#BOLT}: lightning strikes the point now; size = how thick.</li>
 *   <li>{@link Kind#PILLAR}: a column of light (or fire) bursts out of the ground; size = radius, length = height.</li>
 *   <li>{@link Kind#BURST}: a flash of light; size = how big.</li>
 *   <li>{@link Kind#QUAKE}: cracks of light run out over the ground from a heavy blow; size = radius.</li>
 * </ul>
 */
public record WorldBossFxPayload(int kind, float x, float y, float z, float dx, float dz, float size, float length, float extra, int ticks,
                                 int color) implements CustomPacketPayload {
    public enum Kind { CIRCLE, RING, WAVE, LINE, CONE, CELL, METEOR, BOLT, PILLAR, BURST, QUAKE }

    public static final Type<WorldBossFxPayload> TYPE = new Type<>(BeyondSolo.id("world_boss_fx"));
    public static final StreamCodec<ByteBuf, WorldBossFxPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        ByteBufCodecs.VAR_INT.encode(buf, p.kind);
        buf.writeFloat(p.x);
        buf.writeFloat(p.y);
        buf.writeFloat(p.z);
        buf.writeFloat(p.dx);
        buf.writeFloat(p.dz);
        buf.writeFloat(p.size);
        buf.writeFloat(p.length);
        buf.writeFloat(p.extra);
        ByteBufCodecs.VAR_INT.encode(buf, p.ticks);
        buf.writeInt(p.color);
    }, buf -> new WorldBossFxPayload(ByteBufCodecs.VAR_INT.decode(buf), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
            buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), ByteBufCodecs.VAR_INT.decode(buf), buf.readInt()));

    public Kind kindEnum() {
        Kind[] all = Kind.values();
        return all[Math.max(0, Math.min(all.length - 1, kind))];
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
