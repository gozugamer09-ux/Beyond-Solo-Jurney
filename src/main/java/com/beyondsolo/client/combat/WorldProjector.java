package com.beyondsolo.client.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Turns world positions into GUI coordinates, so things that float over entities (meters, damage numbers)
 * can be drawn with the same smooth UI toolkit as the rest of the System. The matrices are captured each frame
 * while the level renders.
 */
public final class WorldProjector {
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static Vec3 camera = Vec3.ZERO;
    private static boolean ready;

    public static void capture(Matrix4f modelView, Matrix4f projection, Vec3 cameraPos) {
        VIEW_PROJECTION.set(projection).mul(modelView);
        camera = cameraPos;
        ready = true;
    }

    public static Vec3 camera() {
        return camera;
    }

    /** {x, y} in GUI pixels, or null if the point is behind the camera. */
    public static float[] project(Vec3 world) {
        if (!ready) return null;
        Vector4f v = new Vector4f((float) (world.x - camera.x), (float) (world.y - camera.y), (float) (world.z - camera.z), 1f);
        VIEW_PROJECTION.transform(v);
        if (v.w <= 0.05f) return null;
        float nx = v.x / v.w;
        float ny = v.y / v.w;
        if (Math.abs(nx) > 1.3f || Math.abs(ny) > 1.3f) return null;
        var window = Minecraft.getInstance().getWindow();
        return new float[]{(nx * 0.5f + 0.5f) * window.getGuiScaledWidth(), (0.5f - ny * 0.5f) * window.getGuiScaledHeight()};
    }

    private WorldProjector() {}
}
