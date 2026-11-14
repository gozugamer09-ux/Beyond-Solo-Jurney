package com.beyondsolo.client.gate;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.gate.GateBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Draws a gate: the GeckoLib ring of floating rock (with its glowing crystals and veins) and, inside it, the
 * vortex: a dark hole with spiral arms of light turning at different speeds, a bright core, a rim of light and a
 * halo, in the rank's colour. A thin beam over it shows where it is from far away. Opening, it tears open as a
 * line of light that grows tall, then wide; closing, it collapses back to a point with a flash.
 */
public class GateRenderer extends GeoBlockRenderer<GateBlockEntity> {
    private static final ResourceLocation VORTEX = BeyondSolo.id("textures/effect/vortex.png");
    private static final ResourceLocation GLOW = BeyondSolo.id("textures/effect/glow.png");
    private static final ResourceLocation RING = BeyondSolo.id("textures/effect/ring.png");
    private static final int EXIT_COLOR = 0x9FE6FF;

    public GateRenderer(BlockEntityRendererProvider.Context context) {
        super(new GateModels.Gate());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void render(GateBlockEntity gate, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        super.render(gate, partialTick, pose, buffers, packedLight, packedOverlay);
        if (gate.getLevel() == null) return;
        float open = gate.openness(partialTick);
        float time = (gate.getLevel().getGameTime() % 24000L) + partialTick;
        int rgb = gate.isExit() ? EXIT_COLOR : gate.rank().color();
        int pale = GateFx.mix(rgb, 0xFFFFFF, 0.45f);

        // opening: a slit that grows tall first, then widens; closing runs it backwards
        float tall = Mth.clamp(open * 2.6f, 0f, 1f);
        float wide = open < 0.3f ? open / 0.3f * 0.08f : 0.08f + 0.92f * GateFx.outBack(Mth.clamp((open - 0.3f) / 0.7f, 0f, 1f));
        if (gate.isClosing()) wide = open;
        float hx = GateBlockEntity.HALF_WIDTH * wide;
        float hy = GateBlockEntity.HALF_HEIGHT * tall;
        float t0 = gate.openedAt() < 0 ? 999 : time - (gate.openedAt() % 24000L);
        float tearFlash = Mth.clamp(1 - t0 / 30f, 0f, 1f) * (gate.openedAt() < 0 ? 0 : 1);
        float closeFlash = 0;
        if (gate.isClosing()) {
            float tc = time - (gate.closingAt() % 24000L);
            closeFlash = Mth.clamp(1 - Math.abs(tc - GateBlockEntity.CLOSE_TICKS * 0.85f) / 8f, 0f, 1f);
        }

        pose.pushPose();
        pose.translate(0.5f, GateBlockEntity.CENTER_Y, 0.5f);
        Direction f = gate.facing();
        if (f.getAxis() == Direction.Axis.X) pose.mulPose(Axis.YP.rotationDegrees(90));

        if (open > 0.001f) {
            // the hole: dark, faintly tinted
            VertexConsumer dark = buffers.getBuffer(ModRenderTypes.CRYSTAL);
            ellipse(dark, pose.last().pose(), hx * 0.97f, hy * 0.97f, 0.0f, GateFx.mix(rgb, 0x000000, 0.86f), 0.93f * Mth.clamp(open * 2f, 0f, 1f));

            VertexConsumer vortex = buffers.getBuffer(ModRenderTypes.lightTexture(VORTEX));
            for (int side = 0; side < 2; side++) {
                float z = side == 0 ? 0.02f : -0.02f;
                layer(vortex, pose, z, hx, hy, time * 2.2f, GateFx.mix(rgb, 0x000000, 0.25f), 0.85f);
                layer(vortex, pose, z * 1.5f, hx * 0.82f, hy * 0.82f, -time * 3.4f + 40, rgb, 0.75f);
                layer(vortex, pose, z * 2f, hx * 0.55f, hy * 0.55f, time * 6f, pale, 0.8f);
            }
            VertexConsumer glow = buffers.getBuffer(ModRenderTypes.lightTexture(GLOW));
            float pulse = 0.5f + 0.5f * Mth.sin(time * 0.12f);
            for (int side = 0; side < 2; side++) {
                float z = side == 0 ? 0.05f : -0.05f;
                pose.pushPose();
                pose.translate(0, 0, z);
                GateFx.quad(glow, pose.last().pose(), hx * 0.42f, hy * 0.42f, 0xFFFFFF, 0.25f + 0.15f * pulse + tearFlash);
                GateFx.quad(glow, pose.last().pose(), hx * 2.0f + 0.6f, hy * 1.7f + 0.6f, rgb, 0.32f + 0.12f * pulse + 0.6f * tearFlash);
                pose.popPose();
            }
            VertexConsumer ring = buffers.getBuffer(ModRenderTypes.lightTexture(RING));
            for (int side = 0; side < 2; side++) {
                pose.pushPose();
                pose.translate(0, 0, side == 0 ? 0.03f : -0.03f);
                GateFx.quad(ring, pose.last().pose(), hx * 1.12f, hy * 1.08f, pale, 0.65f + 0.25f * pulse);
                pose.popPose();
            }
        }
        if (tearFlash > 0.01f || closeFlash > 0.01f) {
            VertexConsumer glow = buffers.getBuffer(ModRenderTypes.lightTexture(GLOW));
            float k = Math.max(tearFlash, closeFlash);
            Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            GateFx.dot(glow, pose, camera, 0, 0, 0, 1.5f + 3.5f * k, 0xFFFFFF, k);
            GateFx.dot(glow, pose, camera, 0, 0, 0, 4f + 4f * k, rgb, 0.7f * k);
        }
        pose.popPose();

        // a beam over the gate, so it can be found from far away
        if (open > 0.5f && !gate.isExit()) {
            pose.pushPose();
            pose.translate(0f, GateBlockEntity.CENTER_Y + 2.4f, 0f);
            BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, partialTick, 1f, gate.getLevel().getGameTime(), 0, 96,
                    0xFF000000 | GateFx.mix(rgb, 0xFFFFFF, 0.2f), 0.04f * open, 0.09f * open);
            pose.popPose();
        }
    }

    private static void layer(VertexConsumer out, PoseStack pose, float z, float hx, float hy, float degrees, int rgb, float alpha) {
        pose.pushPose();
        pose.translate(0, 0, z);
        pose.scale(hx, hy, 1f);
        pose.mulPose(Axis.ZP.rotationDegrees(degrees));
        GateFx.quad(out, pose.last().pose(), 1.05f, 1.05f, rgb, alpha);
        pose.popPose();
    }

    private static void ellipse(VertexConsumer out, Matrix4f m, float hx, float hy, float z, int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int n = 32;
        for (int i = 0; i < n; i++) {
            float a0 = i * Mth.TWO_PI / n, a1 = (i + 1) * Mth.TWO_PI / n;
            out.addVertex(m, 0, 0, z).setColor(r, g, b, a);
            out.addVertex(m, Mth.cos(a0) * hx, Mth.sin(a0) * hy, z).setColor(r, g, b, a);
            out.addVertex(m, Mth.cos(a1) * hx, Mth.sin(a1) * hy, z).setColor(r, g, b, a);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(GateBlockEntity gate) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 160;
    }

    @Override
    public AABB getRenderBoundingBox(GateBlockEntity gate) {
        BlockPos p = gate.getBlockPos();
        return new AABB(p.getX() - 4, p.getY() - 1, p.getZ() - 4, p.getX() + 5, p.getY() + 100, p.getZ() + 5);
    }
}
