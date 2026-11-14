package com.beyondsolo.client.gate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Small drawing helpers shared by the gate, boss and cache renderers (all in the current pose's units: blocks).
 * Callers fetch a buffer right before drawing into it: asking the buffer source for another render type ends the
 * one before, so a consumer kept from earlier would crash with "Not building!".
 */
final class GateFx {
    /** A textured quad in the pose's XY plane, centred on the origin, half sizes hx, hy. */
    static void quad(VertexConsumer out, Matrix4f m, float hx, float hy, int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        if (a <= 2) return;
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        out.addVertex(m, -hx, -hy, 0).setUv(0, 1).setColor(r, g, b, a);
        out.addVertex(m, hx, -hy, 0).setUv(1, 1).setColor(r, g, b, a);
        out.addVertex(m, hx, hy, 0).setUv(1, 0).setColor(r, g, b, a);
        out.addVertex(m, -hx, hy, 0).setUv(0, 0).setColor(r, g, b, a);
    }

    /** A quad from y=0 up to y=height, half width hx, that turns around the Y axis to face the camera. */
    static void column(VertexConsumer out, PoseStack pose, Camera camera, double wx, double wz, float hx, float height, int rgb, float alpha) {
        pose.pushPose();
        float yaw = (float) Math.atan2(camera.getPosition().x - wx, camera.getPosition().z - wz);
        pose.mulPose(Axis.YP.rotation(yaw));
        Matrix4f m = pose.last().pose();
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        if (a > 2) {
            out.addVertex(m, -hx, 0, 0).setUv(0, 1).setColor(r, g, b, a);
            out.addVertex(m, hx, 0, 0).setUv(1, 1).setColor(r, g, b, a);
            out.addVertex(m, hx, height, 0).setUv(1, 0).setColor(r, g, b, a);
            out.addVertex(m, -hx, height, 0).setUv(0, 0).setColor(r, g, b, a);
        }
        pose.popPose();
    }

    /** A soft dot facing the camera at an offset. */
    static void dot(VertexConsumer out, PoseStack pose, Camera camera, float x, float y, float z, float half, int rgb, float alpha) {
        if (alpha <= 0.01f) return;
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(camera.rotation());
        quad(out, pose.last().pose(), half, half, rgb, alpha);
        pose.popPose();
    }

    /** A texture lying flat (XZ plane) at height y. */
    static void flat(VertexConsumer out, PoseStack pose, float y, float radius, float degrees, int rgb, float alpha) {
        pose.pushPose();
        pose.translate(0, y, 0);
        pose.mulPose(Axis.YP.rotationDegrees(degrees));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        quad(out, pose.last().pose(), radius, radius, rgb, alpha);
        pose.popPose();
    }

    static int mix(int a, int b, float t) {
        int red = Math.round(Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
        int green = Math.round(Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
        int blue = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
        return (red << 16) | (green << 8) | blue;
    }

    static float outBack(float t) {
        float c1 = 1.70158f, c3 = c1 + 1;
        float u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }

    private GateFx() {}
}
