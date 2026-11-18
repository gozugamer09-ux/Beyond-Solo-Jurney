package com.beyondsolo.worldboss;

import com.beyondsolo.network.WorldBossFxPayload;
import com.beyondsolo.network.WorldBossFxPayload.Kind;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sends a world boss's warnings and blows ({@link WorldBossFxPayload}) to everyone who can see them. The fights are
 * big, so the reach is long.
 */
public final class WorldBossFx {
    public static final double RANGE = 160;

    private static void send(ServerLevel level, Kind kind, Vec3 at, Vec3 dir, float size, float length, float extra, int ticks, int color) {
        Vec3 d = dir.lengthSqr() > 1e-6 ? dir.multiply(1, 0, 1).normalize() : Vec3.ZERO;
        PacketDistributor.sendToPlayersNear(level, null, at.x, at.y, at.z, RANGE, new WorldBossFxPayload(kind.ordinal(), (float) at.x, (float) at.y,
                (float) at.z, (float) d.x, (float) d.z, size, length, extra, ticks, color));
    }

    public static void circle(ServerLevel level, Vec3 at, float radius, int ticks, int color) {
        send(level, Kind.CIRCLE, at, Vec3.ZERO, radius, 0, 0, ticks, color);
    }

    public static void ring(ServerLevel level, Vec3 at, float inner, float outer, int ticks, int color) {
        send(level, Kind.RING, at, Vec3.ZERO, outer, 0, inner, ticks, color);
    }

    public static void wave(ServerLevel level, Vec3 at, float radius, float band, int ticks, int color) {
        send(level, Kind.WAVE, at, Vec3.ZERO, radius, 0, band, ticks, color);
    }

    public static void line(ServerLevel level, Vec3 from, Vec3 dir, float length, float width, int ticks, int color) {
        send(level, Kind.LINE, from, dir, width, length, 0, ticks, color);
    }

    public static void cone(ServerLevel level, Vec3 from, Vec3 dir, float length, float halfAngle, int ticks, int color) {
        send(level, Kind.CONE, from, dir, halfAngle, length, 0, ticks, color);
    }

    public static void cell(ServerLevel level, Vec3 at, float half, int ticks, int color) {
        send(level, Kind.CELL, at, Vec3.ZERO, half, 0, 0, ticks, color);
    }

    public static void meteor(ServerLevel level, Vec3 at, float size, int ticks, int color) {
        send(level, Kind.METEOR, at, Vec3.ZERO, size, 0, 0, ticks, color);
    }

    public static void bolt(ServerLevel level, Vec3 at, float size, int color) {
        send(level, Kind.BOLT, at, Vec3.ZERO, size, 0, 0, 8, color);
    }

    public static void pillar(ServerLevel level, Vec3 at, float radius, float height, int color) {
        send(level, Kind.PILLAR, at, Vec3.ZERO, radius, height, 0, 18, color);
    }

    public static void burst(ServerLevel level, Vec3 at, float size, int color) {
        send(level, Kind.BURST, at, Vec3.ZERO, size, 0, 0, 12, color);
    }

    public static void quake(ServerLevel level, Vec3 at, float radius, int color) {
        send(level, Kind.QUAKE, at, Vec3.ZERO, radius, 0, 0, 40, color);
    }

    private WorldBossFx() {}
}
