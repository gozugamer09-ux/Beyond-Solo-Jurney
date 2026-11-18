package com.beyondsolo.client.worldboss;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.gate.GateFx;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.worldboss.BossProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * What the great threats throw:
 * <ul>
 *   <li>a <b>boulder</b>: a tumbling lump of basalt with a molten heart, trailing fire</li>
 *   <li>an <b>ice spear</b>: a long crystal of ice, point first</li>
 *   <li>a <b>storm orb</b>: a ball of lightning crawling with arcs</li>
 *   <li>a <b>blood shard</b>: a spike of crystallised blood</li>
 * </ul>
 */
public class BossProjectileRenderer extends EntityRenderer<BossProjectile> {
    private static final ResourceLocation GLOW = BeyondSolo.id("textures/effect/glow.png");
    private static final ResourceLocation LINE = BeyondSolo.id("textures/effect/wb_line.png");
    private static final BlockState ROCK = Blocks.BASALT.defaultBlockState();
    private static final BlockState CORE = Blocks.MAGMA_BLOCK.defaultBlockState();
    private static final BlockState CHIP = Blocks.BLACKSTONE.defaultBlockState();

    private final BlockRenderDispatcher blocks;

    public BossProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blocks = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(BossProjectile p, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        float time = (p.tickCount + partial) % 7200;
        float s = p.scale();
        int c = p.color();
        switch (p.kind()) {
            case BOULDER -> boulder(p, pose, buffers, camera, time, s, c);
            case ICE_SPEAR -> crystal(p, pose, buffers, camera, s * 0.32f, s * 2.6f, c, 0xE8F8FF);
            case BLOOD_SHARD -> crystal(p, pose, buffers, camera, s * 0.26f, s * 1.5f, c, 0xFF8A94);
            case STORM_ORB -> orb(p, pose, buffers, camera, time, s, c, partial);
        }
        super.render(p, yaw, partial, pose, buffers, light);
    }

    private void boulder(BossProjectile p, PoseStack pose, MultiBufferSource buffers, Camera camera, float time, float s, int c) {
        int full = LightTexture.FULL_BRIGHT;
        pose.pushPose();
        pose.translate(0, s * 0.5f, 0);
        pose.mulPose(Axis.XP.rotationDegrees(time * 13));
        pose.mulPose(Axis.ZP.rotationDegrees(time * 7));
        // a lump of rock: a big block turned off-square, smaller ones stuck to it, a molten heart showing through
        block(pose, buffers, ROCK, s * 1.5f, 0, 0, 0, 20, 35, full);
        block(pose, buffers, CHIP, s * 0.9f, s * 0.55f, s * 0.3f, -s * 0.2f, 45, 10, full);
        block(pose, buffers, ROCK, s * 0.85f, -s * 0.5f, -s * 0.35f, s * 0.3f, 15, 60, full);
        block(pose, buffers, CORE, s * 1.05f, s * 0.1f, -s * 0.05f, s * 0.45f, 30, 20, full);
        pose.popPose();
        GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, 0, s * 0.5f, 0, s * 2.6f, c, 0.55f);
        trail(p, pose, buffers, camera, s, c, 0.5f);
    }

    /** One block's model, centred, {@code size} on a side, turned. */
    private void block(PoseStack pose, MultiBufferSource buffers, BlockState state, float size, float x, float y, float z, float rx, float ry, int light) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Axis.XP.rotationDegrees(rx));
        pose.mulPose(Axis.YP.rotationDegrees(ry));
        pose.scale(size, size, size);
        pose.translate(-0.5f, -0.5f, -0.5f);
        blocks.renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /** A long crystal, point first along its flight: six faces to the tip, six to the tail, lit on one side. */
    private void crystal(BossProjectile p, PoseStack pose, MultiBufferSource buffers, Camera camera, float radius, float length, int c, int light) {
        Vec3 d = p.heading();
        pose.pushPose();
        pose.translate(0, 0.25f, 0);
        float yaw = (float) Math.atan2(d.x, d.z);
        float pitch = (float) Math.asin(Mth.clamp(-d.y, -1, 1));
        pose.mulPose(Axis.YP.rotation(yaw));
        pose.mulPose(Axis.XP.rotation(pitch));
        Matrix4f m = pose.last().pose();
        VertexConsumer out = buffers.getBuffer(ModRenderTypes.CRYSTAL);
        int n = 6;
        float tip = length * 0.62f, tail = -length * 0.38f;
        for (int i = 0; i < n; i++) {
            float a0 = Mth.TWO_PI * i / n, a1 = Mth.TWO_PI * (i + 1) / n;
            float x0 = Mth.cos(a0) * radius, y0 = Mth.sin(a0) * radius, x1 = Mth.cos(a1) * radius, y1 = Mth.sin(a1) * radius;
            // light from above: faces turned up are brighter
            float lit = 0.55f + 0.45f * Math.max(0, Mth.sin((a0 + a1) / 2));
            int front = GateFx.mix(c, light, lit), back = GateFx.mix(c, 0x000000, 0.35f * (1 - lit));
            tri(out, m, 0, 0, tip, x0, y0, 0, x1, y1, 0, front, 0.9f);
            tri(out, m, 0, 0, tail, x1, y1, 0, x0, y0, 0, back, 0.85f);
        }
        pose.popPose();
        // its edges catch the light
        GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, 0, 0.25f, 0, length * 0.7f, c, 0.45f);
        GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, (float) d.x * length * 0.5f, 0.25f + (float) d.y * length * 0.5f,
                (float) d.z * length * 0.5f, radius * 2.2f, GateFx.mix(c, 0xFFFFFF, 0.7f), 0.8f);
        trail(p, pose, buffers, camera, radius * 2, c, 0.4f);
    }

    private static void tri(VertexConsumer out, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2,
                            int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        out.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
        out.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
        out.addVertex(m, x2, y2, z2).setColor(r, g, b, a);
    }

    /** A ball of lightning: a white heart in a halo of its colour, with arcs crawling over it. */
    private void orb(BossProjectile p, PoseStack pose, MultiBufferSource buffers, Camera camera, float time, float s, int c, float partial) {
        float pulse = 1 + 0.15f * Mth.sin(time * 1.3f);
        GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, 0, s * 0.5f, 0, s * 2.8f * pulse, c, 0.6f);
        GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, 0, s * 0.5f, 0, s * 1.0f, GateFx.mix(c, 0xFFFFFF, 0.8f), 0.95f);
        RandomSource r = RandomSource.create(p.getId() * 31L + (long) (time / 2));
        VertexConsumer out = buffers.getBuffer(ModRenderTypes.lightTexture(LINE));
        Matrix4f m = pose.last().pose();
        Vec3 eye = camera.getPosition().subtract(p.getPosition(partial));
        for (int arc = 0; arc < 4; arc++) {
            Vec3 a = randomOnSphere(r).scale(s * 0.5);
            Vec3 prev = a.add(0, s * 0.5, 0);
            for (int i = 1; i <= 4; i++) {
                Vec3 next = randomOnSphere(r).scale(s * (0.6 + 0.5 * r.nextDouble())).add(0, s * 0.5, 0);
                ribbon(out, m, prev, next, eye, 0.06f * s + 0.04f, GateFx.mix(c, 0xFFFFFF, 0.6f), 0.9f);
                prev = next;
            }
        }
        trail(p, pose, buffers, camera, s, c, 0.45f);
    }

    private static Vec3 randomOnSphere(RandomSource r) {
        double z = r.nextDouble() * 2 - 1, a = r.nextDouble() * Math.PI * 2, k = Math.sqrt(1 - z * z);
        return new Vec3(Math.cos(a) * k, z, Math.sin(a) * k);
    }

    /** A ribbon between two points in the pose's space, turned about its length towards the eye. */
    private static void ribbon(VertexConsumer out, Matrix4f m, Vec3 a, Vec3 b, Vec3 eye, float w, int rgb, float alpha) {
        Vec3 dir = b.subtract(a);
        Vec3 side = dir.cross(eye.subtract(a.add(b).scale(0.5)));
        if (side.lengthSqr() < 1e-8) return;
        side = side.normalize().scale(w);
        int al = Mth.clamp(Math.round(alpha * 255), 0, 255);
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, bl = rgb & 255;
        out.addVertex(m, (float) (a.x - side.x), (float) (a.y - side.y), (float) (a.z - side.z)).setUv(0, 0).setColor(r, g, bl, al);
        out.addVertex(m, (float) (a.x + side.x), (float) (a.y + side.y), (float) (a.z + side.z)).setUv(0, 1).setColor(r, g, bl, al);
        out.addVertex(m, (float) (b.x + side.x), (float) (b.y + side.y), (float) (b.z + side.z)).setUv(1, 1).setColor(r, g, bl, al);
        out.addVertex(m, (float) (b.x - side.x), (float) (b.y - side.y), (float) (b.z - side.z)).setUv(1, 0).setColor(r, g, bl, al);
    }

    /** A tail of light behind it along where it came from. */
    private static void trail(BossProjectile p, PoseStack pose, MultiBufferSource buffers, Camera camera, float s, int c, float alpha) {
        Vec3 d = p.heading();
        for (int i = 1; i <= 6; i++) {
            float k = i / 6f;
            GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, (float) -d.x * s * 1.3f * i, s * 0.5f + (float) -d.y * s * 1.3f * i,
                    (float) -d.z * s * 1.3f * i, s * (1.5f - k), c, alpha * (1 - k));
        }
    }

    @Override
    public ResourceLocation getTextureLocation(BossProjectile p) {
        return GLOW;
    }
}
