package com.beyondsolo.client.awakening;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.awakening.ArbiterEntity;
import com.beyondsolo.client.render.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * The Arbiter's attacks, drawn as light on the floor so they can be read and dodged: judgment marks (a circle that
 * tightens and reddens until a pillar of light falls on it) and the sunder's ring running out from the impact.
 * Game time, so it follows /tick rate.
 */
public final class AwakeningWorldFx {
    static final ResourceLocation CIRCLE = BeyondSolo.id("textures/effect/magic_circle.png");
    static final ResourceLocation GLOW = BeyondSolo.id("textures/effect/glow.png");
    static final ResourceLocation RING = BeyondSolo.id("textures/effect/ring.png");
    static final ResourceLocation BEAM = BeyondSolo.id("textures/effect/beam.png");
    private static final int GOLD = 0xF2D58A;
    private static final int RED = 0xFF4050;
    private static final float STRIKE_TICKS = 14f;

    private record Mark(Vec3 at, float start, float delay) {}
    private record Wave(Vec3 at, float start, float reach) {}

    private static final List<Mark> MARKS = new ArrayList<>();
    private static final List<Wave> WAVES = new ArrayList<>();

    private static float now(float partial) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime() + partial;
    }

    public static void mark(double x, double y, double z, float delay) {
        MARKS.add(new Mark(new Vec3(x, y, z), now(0), delay));
    }

    public static void wave(double x, double y, double z, float reach) {
        WAVES.add(new Wave(new Vec3(x, y, z), now(0), reach));
    }

    public static void clear() {
        MARKS.clear();
        WAVES.clear();
    }

    public static void render(PoseStack pose, Camera camera, float partial) {
        if (MARKS.isEmpty() && WAVES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 cam = camera.getPosition();
        float time = now(partial);
        MARKS.removeIf(m -> time - m.start > m.delay + STRIKE_TICKS || time < m.start - 5);
        WAVES.removeIf(w -> (time - w.start) * ArbiterEntity.WAVE_SPEED > w.reach || time < w.start - 5);

        for (Mark m : MARKS) {
            float age = time - m.start;
            float k = Mth.clamp(age / m.delay, 0f, 1f);
            pose.pushPose();
            pose.translate(m.at.x - cam.x, m.at.y - cam.y, m.at.z - cam.z);
            float r = ArbiterEntity.MARK_RADIUS;
            if (age < m.delay) {
                int color = mix(GOLD, RED, k * k);
                float pop = Mth.clamp(age / 4f, 0f, 1f);
                flat(buffers.getBuffer(ModRenderTypes.lightTexture(CIRCLE)), pose, 0.06f, r * 1.1f * pop, time * (2f + 10f * k), color, 0.9f);
                // an outer ring closing in on the circle: when it touches, the pillar falls
                flat(buffers.getBuffer(ModRenderTypes.lightTexture(RING)), pose, 0.07f, r * (2.4f - 1.4f * k), 0, color, 0.35f + 0.5f * k);
                flat(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, 0.05f, r * 1.3f, 0, color, 0.25f + 0.45f * k * k);
                column(buffers.getBuffer(ModRenderTypes.lightTexture(BEAM)), pose, camera, m.at.x, m.at.z, 0.12f + 0.1f * k, 26f, color, 0.12f + 0.25f * k);
            } else {
                float s = (age - m.delay) / STRIKE_TICKS;
                float fade = 1 - s;
                column(buffers.getBuffer(ModRenderTypes.lightTexture(BEAM)), pose, camera, m.at.x, m.at.z, r * (0.9f + 0.5f * s), 30f, 0xFFFFFF, fade);
                column(buffers.getBuffer(ModRenderTypes.lightTexture(BEAM)), pose, camera, m.at.x, m.at.z, r * (1.5f + 0.8f * s), 30f, RED, 0.7f * fade);
                flat(buffers.getBuffer(ModRenderTypes.lightTexture(RING)), pose, 0.08f, r * (1f + 2.2f * s), 0, 0xFFE0C0, fade);
                flat(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, 0.06f, r * 2.2f, 0, 0xFFFFFF, 0.8f * fade * fade);
            }
            pose.popPose();
        }
        for (Wave w : WAVES) {
            float age = time - w.start;
            if (age < 0) continue;
            float r = age * ArbiterEntity.WAVE_SPEED;
            float fade = 1 - Mth.clamp((r - w.reach * 0.75f) / (w.reach * 0.25f), 0f, 1f);
            pose.pushPose();
            pose.translate(w.at.x - cam.x, w.at.y - cam.y, w.at.z - cam.z);
            flat(buffers.getBuffer(ModRenderTypes.lightTexture(RING)), pose, 0.09f, r + 0.3f, 0, 0xFFF0C8, 0.95f * fade);
            flat(buffers.getBuffer(ModRenderTypes.lightTexture(RING)), pose, 0.45f, r, 0, GOLD, 0.6f * fade);
            flat(buffers.getBuffer(ModRenderTypes.lightTexture(RING)), pose, 0.12f, Math.max(0.1f, r - 1.2f), 0, RED, 0.45f * fade);
            if (age < 8) flat(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, 0.1f, 5f, 0, 0xFFFFFF, 0.8f * (1 - age / 8f));
            pose.popPose();
        }
        for (ResourceLocation t : new ResourceLocation[]{CIRCLE, GLOW, RING, BEAM}) buffers.endBatch(ModRenderTypes.lightTexture(t));
    }

    // ------------------------------------------------------------------ helpers (pose units: blocks)

    static void quad(VertexConsumer out, Matrix4f m, float hx, float hy, int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        if (a <= 2) return;
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        out.addVertex(m, -hx, -hy, 0).setUv(0, 1).setColor(r, g, b, a);
        out.addVertex(m, hx, -hy, 0).setUv(1, 1).setColor(r, g, b, a);
        out.addVertex(m, hx, hy, 0).setUv(1, 0).setColor(r, g, b, a);
        out.addVertex(m, -hx, hy, 0).setUv(0, 0).setColor(r, g, b, a);
    }

    /** A texture lying flat at height y, visible from above and below. */
    static void flat(VertexConsumer out, PoseStack pose, float y, float radius, float degrees, int rgb, float alpha) {
        pose.pushPose();
        pose.translate(0, y, 0);
        pose.mulPose(Axis.YP.rotationDegrees(degrees));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        quad(out, pose.last().pose(), radius, radius, rgb, alpha);
        pose.popPose();
    }

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

    static void dot(VertexConsumer out, PoseStack pose, Camera camera, float x, float y, float z, float half, int rgb, float alpha) {
        if (alpha <= 0.01f) return;
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(camera.rotation());
        quad(out, pose.last().pose(), half, half, rgb, alpha);
        pose.popPose();
    }

    static int mix(int a, int b, float t) {
        int red = Math.round(Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
        int green = Math.round(Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
        int blue = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
        return (red << 16) | (green << 8) | blue;
    }

    private AwakeningWorldFx() {}
}
