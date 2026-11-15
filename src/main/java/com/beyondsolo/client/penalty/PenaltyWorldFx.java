package com.beyondsolo.client.penalty;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.gate.GateFx;
import com.beyondsolo.client.render.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The Penalty Zone in the world, drawn as light:
 * <ul>
 *   <li>the <b>edge</b>: a curtain of red light all the way round the basin, faint from afar and burning brighter
 *   (with embers rising up it) as the player comes near</li>
 *   <li>the <b>eclipse</b> in the sky: a black sun with a burning corona over the pillar, and a red haze on the
 *   horizon (see {@link #sky})</li>
 * </ul>
 */
public final class PenaltyWorldFx {
    private static final ResourceLocation GLOW = BeyondSolo.id("textures/effect/glow.png");
    private static final ResourceLocation RING = BeyondSolo.id("textures/effect/ring.png");
    private static final int SEGMENTS = 96;
    private static final float WALL = 9f;
    private static final float BASE_Y = 62.5f;

    /** The edge curtain. Called after particles while the player is in the zone. */
    public static void render(PoseStack pose, Camera camera, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !PenaltyClient.inside() || PenaltyClient.radius <= 0) return;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 cam = camera.getPosition();
        float time = mc.level.getGameTime() + partial;
        double cx = PenaltyClient.cx + 0.5, cz = PenaltyClient.cz + 0.5;
        float r = PenaltyClient.radius;
        Vec3 me = mc.player.getPosition(partial);
        double myDist = Math.sqrt((me.x - cx) * (me.x - cx) + (me.z - cz) * (me.z - cz));
        float near = Mth.clamp((float) (myDist - (r - 9)) / 9f, 0, 1);
        float myAngle = (float) Math.atan2(me.z - cz, me.x - cx);

        pose.pushPose();
        pose.translate(cx - cam.x, BASE_Y - cam.y, cz - cam.z);
        Matrix4f m = pose.last().pose();
        VertexConsumer out = buffers.getBuffer(ModRenderTypes.lightTexture(GLOW));
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / SEGMENTS, a1 = Mth.TWO_PI * (i + 1) / SEGMENTS;
            float mid = (a0 + a1) / 2;
            // brighter on the side the player is pressing against
            float facing = Mth.clamp(1 - Math.abs(Mth.wrapDegrees((mid - myAngle) * Mth.RAD_TO_DEG)) / 70f, 0, 1);
            float shimmer = 0.85f + 0.15f * Mth.sin(time * 0.15f + i * 0.7f);
            float alpha = (0.16f + 0.55f * near * facing) * shimmer;
            float height = WALL * (0.75f + 0.25f * Mth.sin(time * 0.05f + i * 1.3f)) + 3 * near * facing;
            wall(out, m, r, a0, a1, height, 0xFF3020, alpha);
        }
        pose.popPose();

        // embers rising up the curtain near the player
        if (near > 0.05f) {
            VertexConsumer dots = buffers.getBuffer(ModRenderTypes.lightTexture(GLOW));
            for (int i = 0; i < 40; i++) {
                float seed = i * 12.9898f;
                float a = myAngle + (frac(Mth.sin(seed) * 437.58f) - 0.5f) * 1.1f;
                float speed = 0.04f + 0.05f * frac(seed * 0.31f);
                float rise = frac(time * speed / WALL + frac(seed * 0.77f)) * WALL * 1.2f;
                float x = (float) (cx + Mth.cos(a) * r - cam.x), z = (float) (cz + Mth.sin(a) * r - cam.z);
                float fade = (1 - rise / (WALL * 1.2f)) * near;
                GateFx.dot(dots, pose, camera, x, (float) (BASE_Y + 0.5 + rise - cam.y), z, 0.12f + 0.1f * frac(seed), 0xFF8A4C, fade);
            }
        }
        buffers.endBatch(ModRenderTypes.lightTexture(GLOW));
    }

    /** One panel of the curtain: bright at the foot, fading upwards (the glow texture's centre to its top edge). */
    private static void wall(VertexConsumer out, Matrix4f m, float r, float a0, float a1, float height, int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        if (a <= 2) return;
        int red = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        float x0 = Mth.cos(a0) * r, z0 = Mth.sin(a0) * r, x1 = Mth.cos(a1) * r, z1 = Mth.sin(a1) * r;
        out.addVertex(m, x0, 0, z0).setUv(0.5f, 0.5f).setColor(red, g, b, a);
        out.addVertex(m, x1, 0, z1).setUv(0.5f, 0.5f).setColor(red, g, b, a);
        out.addVertex(m, x1, height, z1).setUv(0.5f, 0.02f).setColor(red, g, b, a);
        out.addVertex(m, x0, height, z0).setUv(0.5f, 0.02f).setColor(red, g, b, a);
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    /**
     * The sky of the zone (called by the dimension's special effects; the clear colour under it is the red fog).
     * A dark sun ringed by a burning corona hangs to the north, over the pillar.
     */
    public static void sky(Matrix4f modelView, float time) {
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = new PoseStack();
        pose.mulPose(modelView);
        pose.mulPose(Axis.YP.rotationDegrees(-8));
        pose.mulPose(Axis.XP.rotationDegrees(24));
        // close to the eye (nearer than the haze begins), so the fog leaves the black disc black
        pose.translate(0, 0, -10);
        pose.scale(0.1f, 0.1f, 0.1f);
        Matrix4f m = pose.last().pose();
        float breathe = 1 + 0.03f * Mth.sin(time * 0.02f);
        // the haze and the corona
        GateFx.quad(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), m, 80 * breathe, 80 * breathe, 0x9A1208, 0.6f);
        GateFx.quad(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), m, 30 * breathe, 30 * breathe, 0xFF5A24, 0.85f);
        buffers.endBatch(ModRenderTypes.lightTexture(GLOW));
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(time * 0.05f));
        GateFx.quad(buffers.getBuffer(ModRenderTypes.lightTexture(RING)), pose.last().pose(), 19.5f, 19.5f, 0xFFB070, 0.95f);
        pose.popPose();
        buffers.endBatch(ModRenderTypes.lightTexture(RING));
        // the black disc
        VertexConsumer disc = buffers.getBuffer(ModRenderTypes.CRYSTAL);
        int n = 48;
        float rad = 14.2f;
        for (int i = 0; i < n; i++) {
            float a0 = Mth.TWO_PI * i / n, a1 = Mth.TWO_PI * (i + 1) / n;
            disc.addVertex(m, 0, 0, 0.1f).setColor(6, 1, 3, 255);
            disc.addVertex(m, Mth.cos(a0) * rad, Mth.sin(a0) * rad, 0.1f).setColor(16, 3, 5, 255);
            disc.addVertex(m, Mth.cos(a1) * rad, Mth.sin(a1) * rad, 0.1f).setColor(16, 3, 5, 255);
        }
        buffers.endBatch(ModRenderTypes.CRYSTAL);
        // a thin bright rim
        GateFx.quad(buffers.getBuffer(ModRenderTypes.lightTexture(RING)), m, 15.4f, 15.4f, 0xFFE0C0, 0.6f);
        buffers.endBatch(ModRenderTypes.lightTexture(RING));
    }

    private PenaltyWorldFx() {}
}
