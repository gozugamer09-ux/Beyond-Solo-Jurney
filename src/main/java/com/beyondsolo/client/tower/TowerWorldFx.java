package com.beyondsolo.client.tower;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.gate.GateFx;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.network.TowerPayload;
import com.beyondsolo.tower.TowerRun;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The Crimson Citadel's light in the world:
 * <ul>
 *   <li>the <b>seal</b> on the stair door: a curtain of crimson light in the doorway while the floor is being fought,
 *   with a lock burning in it; once the floor is won it parts, and warm light spills out of the stair</li>
 *   <li>the <b>exit sigil</b> on the floor by the entrance: dark while the floor is fought, lit gold once it is won
 *   (and in a rush once the last boss falls); standing on it fills it, and a column rises as they are let out</li>
 *   <li>the <b>sky</b> (see {@link #sky}): a pale moon low over the castle, red haze under it; under the blood moon
 *   it burns crimson and the haze rises</li>
 * </ul>
 */
public final class TowerWorldFx {
    private static final ResourceLocation GLOW = BeyondSolo.id("textures/effect/glow.png");
    private static final ResourceLocation RING = BeyondSolo.id("textures/effect/ring.png");
    private static final ResourceLocation BEAM = BeyondSolo.id("textures/effect/beam.png");
    private static final ResourceLocation CIRCLE = BeyondSolo.id("textures/effect/magic_circle.png");
    private static final ResourceLocation LOCK = BeyondSolo.id("textures/effect/key_lock.png");
    private static final int CRIMSON = 0xFF2E3F;
    private static final int GOLD = 0xFFD166;
    private static final int WARM = 0xFFB070;

    /** How open the door's seal looks (0 sealed .. 1 parted), eased so it parts rather than vanishes. */
    private static float parted;
    private static float exitLit;

    public static void render(PoseStack pose, Camera camera, float partial) {
        Minecraft mc = Minecraft.getInstance();
        TowerPayload r = TowerClient.run;
        if (mc.level == null || mc.player == null || !TowerClient.inRun() || r.phase() == TowerPayload.PULL) return;
        if (r.exit().equals(BlockPos.ZERO)) return;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 cam = camera.getPosition();
        float time = mc.level.getGameTime() + partial;
        float dt = Math.min(0.1f, com.beyondsolo.client.gfx.Anim.dt());
        boolean open = r.has(TowerPayload.F_DOOR_OPEN);
        parted = Mth.clamp(parted + (open ? dt * 1.2f : -dt * 3f), 0, 1);
        boolean lit = r.has(TowerPayload.F_EXIT);
        exitLit = Mth.clamp(exitLit + (lit ? dt * 1.5f : -dt * 3f), 0, 1);

        if (!r.door().equals(BlockPos.ZERO)) door(buffers, pose, camera, cam, r, time);
        exit(buffers, pose, camera, cam, r, time);
        buffers.endBatch(ModRenderTypes.lightTexture(GLOW));
        buffers.endBatch(ModRenderTypes.lightTexture(BEAM));
    }

    /** The stair door: a curtain of light that parts once the floor is won, then light out of the stair. */
    private static void door(MultiBufferSource.BufferSource buffers, PoseStack pose, Camera camera, Vec3 cam, TowerPayload r, float time) {
        BlockPos d = r.door();
        float seal = 1 - parted;
        pose.pushPose();
        // the doorway is 3 wide and 4 high, its foot's middle at the door; the curtain hangs on the hall side
        pose.translate(d.getX() + 0.5 - cam.x, d.getY() - cam.y, d.getZ() + 1.03 - cam.z);
        Matrix4f m = pose.last().pose();
        if (seal > 0.01f) {
            VertexConsumer out = buffers.getBuffer(ModRenderTypes.lightTexture(GLOW));
            // strips of light, each wavering on its own; parting from the middle outwards
            int strips = 12;
            for (int i = 0; i < strips; i++) {
                float u0 = i / (float) strips, u1 = (i + 1) / (float) strips;
                float mid = (u0 + u1) / 2 - 0.5f;
                float shift = Math.signum(mid) * parted * 1.6f;
                float x0 = -1.5f + u0 * 3f + shift, x1 = -1.5f + u1 * 3f + shift;
                float flick = 0.75f + 0.25f * Mth.sin(time * 0.21f + i * 1.7f);
                float a = (0.32f + 0.14f * flick) * seal;
                quadXY(out, m, x0, 0, x1, 4f + 0.25f * Mth.sin(time * 0.1f + i), CRIMSON, a);
            }
            // the frame's edges burn brighter
            quadXY(out, m, -1.62f, 0, -1.38f, 4f, 0xFF6070, 0.6f * seal);
            quadXY(out, m, 1.38f, 0, 1.62f, 4f, 0xFF6070, 0.6f * seal);
            pose.pushPose();
            pose.translate(0, 2.1f, 0.03f);
            float beat = 0.8f + 0.2f * Mth.sin(time * 0.25f);
            GateFx.quad(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose.last().pose(), 1.2f * beat, 1.2f * beat, CRIMSON, 0.5f * seal);
            GateFx.quad(buffers.getBuffer(ModRenderTypes.lightTexture(LOCK)), pose.last().pose(), 0.55f, 0.55f, 0xFFD0D4, 0.9f * seal);
            buffers.endBatch(ModRenderTypes.lightTexture(LOCK));
            pose.popPose();
        }
        if (parted > 0.01f) {
            // warm light spilling out of the doorway, brightest at its foot
            VertexConsumer out = buffers.getBuffer(ModRenderTypes.lightTexture(GLOW));
            float pulse = 0.85f + 0.15f * Mth.sin(time * 0.12f);
            pose.pushPose();
            pose.translate(0, 1.6f, -0.6f);
            GateFx.quad(out, pose.last().pose(), 2.4f * pulse, 2.2f * pulse, WARM, 0.35f * parted);
            pose.popPose();
        }
        pose.popPose();
        if (parted > 0.01f && r.has(TowerPayload.F_STAIR)) {
            // a column of light at the top of the stair: the way on
            BlockPos s = r.stair();
            pose.pushPose();
            pose.translate(s.getX() + 0.5 - cam.x, s.getY() - cam.y, s.getZ() + 0.5 - cam.z);
            float pulse = 0.8f + 0.2f * Mth.sin(time * 0.15f);
            GateFx.column(buffers.getBuffer(ModRenderTypes.lightTexture(BEAM)), pose, camera, s.getX() + 0.5, s.getZ() + 0.5, 0.9f * pulse, 6f, GOLD, 0.55f * parted);
            GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, 0.05f, 1.6f, 0, GOLD, 0.5f * parted);
            pose.popPose();
        }
    }

    /** The exit sigil on the floor. */
    private static void exit(MultiBufferSource.BufferSource buffers, PoseStack pose, Camera camera, Vec3 cam, TowerPayload r, float time) {
        BlockPos e = r.exit();
        float hold = r.hold() > 0 ? Mth.clamp(r.hold() / (float) TowerRun.EXIT_HOLD, 0, 1) : 0;
        pose.pushPose();
        pose.translate(e.getX() + 0.5 - cam.x, e.getY() - cam.y, e.getZ() + 0.5 - cam.z);
        // always faintly there, so the way out is known; gold once it is lit
        int rgb = GateFx.mix(0x6A1A20, GOLD, exitLit);
        float a = 0.25f + 0.55f * exitLit;
        GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(CIRCLE)), pose, 0.03f, 1.55f + 0.1f * hold, time * (0.4f + 2.5f * hold), rgb, a);
        buffers.endBatch(ModRenderTypes.lightTexture(CIRCLE));
        GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(RING)), pose, 0.04f, 1.75f, -time * 0.6f, rgb, 0.4f * exitLit + 0.5f * hold);
        buffers.endBatch(ModRenderTypes.lightTexture(RING));
        if (exitLit > 0.01f) {
            float pulse = 0.85f + 0.15f * Mth.sin(time * 0.18f);
            GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, 0.05f, 2.6f * pulse, 0, GOLD, 0.35f * exitLit);
            GateFx.column(buffers.getBuffer(ModRenderTypes.lightTexture(BEAM)), pose, camera, e.getX() + 0.5, e.getZ() + 0.5,
                    0.45f + 0.9f * hold, 2.2f + 6f * hold, GOLD, (0.25f + 0.5f * hold) * exitLit);
        }
        pose.popPose();
    }

    /** A quad in the pose's XY plane from (x0, y0) to (x1, y1), the glow texture's middle column: soft at the top. */
    private static void quadXY(VertexConsumer out, Matrix4f m, float x0, float y0, float x1, float y1, int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        if (a <= 2) return;
        int red = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        out.addVertex(m, x0, y0, 0).setUv(0.5f, 0.5f).setColor(red, g, b, a);
        out.addVertex(m, x1, y0, 0).setUv(0.5f, 0.5f).setColor(red, g, b, a);
        out.addVertex(m, x1, y1, 0).setUv(0.5f, 0.05f).setColor(red, g, b, a);
        out.addVertex(m, x0, y1, 0).setUv(0.5f, 0.05f).setColor(red, g, b, a);
    }

    /**
     * The Citadel's sky (its dimension's special effects; the clear colour under it is the fog's): a pale moon low in
     * the north with a red haze round it. Under the blood moon it turns crimson and the haze climbs.
     */
    public static void sky(Matrix4f modelView, float time) {
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean blood = TowerClient.bloodMoon();
        PoseStack pose = new PoseStack();
        pose.mulPose(modelView);
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.mulPose(Axis.XP.rotationDegrees(14));
        pose.translate(0, 0, -10);
        pose.scale(0.1f, 0.1f, 0.1f);
        Matrix4f m = pose.last().pose();
        float breathe = 1 + 0.03f * Mth.sin(time * 0.02f);
        int haze = blood ? 0xC0101A : 0x5A0A12;
        int moon = blood ? 0xFF3A3A : 0xE8DCD0;
        GateFx.quad(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), m, 90 * breathe, 90 * breathe, haze, blood ? 0.75f : 0.5f);
        GateFx.quad(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), m, 34 * breathe, 34 * breathe, moon, blood ? 0.9f : 0.55f);
        GateFx.quad(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), m, 13, 13, GateFx.mix(moon, 0xFFFFFF, 0.5f), 1f);
        buffers.endBatch(ModRenderTypes.lightTexture(GLOW));
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(time * 0.03f));
        GateFx.quad(buffers.getBuffer(ModRenderTypes.lightTexture(RING)), pose.last().pose(), 16f, 16f, moon, blood ? 0.9f : 0.5f);
        pose.popPose();
        buffers.endBatch(ModRenderTypes.lightTexture(RING));
    }

    static void reset() {
        parted = 0;
        exitLit = 0;
    }

    private TowerWorldFx() {}
}
