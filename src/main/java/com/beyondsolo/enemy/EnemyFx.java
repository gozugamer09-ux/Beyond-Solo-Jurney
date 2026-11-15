package com.beyondsolo.enemy;

import com.beyondsolo.network.EnemyFxPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends the roster's world effects ({@link EnemyFxPayload}) to everyone near. */
public final class EnemyFx {
    private static final double RANGE = 64;

    private static void send(ServerLevel level, Vec3 at, EnemyFxPayload.Kind kind, Vec3 dir, float size, float length, int ticks, int color) {
        PacketDistributor.sendToPlayersNear(level, null, at.x, at.y, at.z, RANGE,
                new EnemyFxPayload(kind.ordinal(), (float) at.x, (float) at.y, (float) at.z, (float) dir.x, (float) dir.z, size, length, ticks, color));
    }

    /** A circle on the ground that fills until the hit lands {@code ticks} later. */
    public static void telegraphCircle(ServerLevel level, Vec3 center, float radius, int ticks, int color) {
        send(level, center, EnemyFxPayload.Kind.CIRCLE, Vec3.ZERO, radius, 0, ticks, color);
    }

    /** A strip on the ground from {@code start} along {@code dir}. */
    public static void telegraphLine(ServerLevel level, Vec3 start, Vec3 dir, float length, float width, int ticks, int color) {
        Vec3 d = dir.multiply(1, 0, 1).normalize();
        send(level, start, EnemyFxPayload.Kind.LINE, d, width, length, ticks, color);
    }

    /** A wedge on the ground from {@code start} along {@code dir}, {@code halfAngle} degrees each side. */
    public static void telegraphCone(ServerLevel level, Vec3 start, Vec3 dir, float length, float halfAngle, int ticks, int color) {
        Vec3 d = dir.multiply(1, 0, 1).normalize();
        send(level, start, EnemyFxPayload.Kind.CONE, d, halfAngle, length, ticks, color);
    }

    /** A burst of light. */
    public static void burst(ServerLevel level, Vec3 at, int color, float size) {
        send(level, at, EnemyFxPayload.Kind.BURST, Vec3.ZERO, size, 0, 10, color);
    }

    /** A column of light shooting up (rising blades, sigils going off). */
    public static void pillar(ServerLevel level, Vec3 at, int color, float size, float height) {
        send(level, at, EnemyFxPayload.Kind.PILLAR, Vec3.ZERO, size, height, 14, color);
    }

    private EnemyFx() {}
}
