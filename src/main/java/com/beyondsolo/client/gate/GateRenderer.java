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
 * line of light that grows tall, then wide; closing, it collapses back to a point with a flash. A gate opened by a
 * dungeon key has a keyhole of light at its heart and a tumbler ring turning in the swirl, and no beam (its holder
 * turned it right in front of themselves).
 *
 * <p>A gate coming apart before its dungeon break ({@code breaks.DungeonBreaks}) shows it: its light runs red, its
 * swirl turns faster, it flickers, and cracks of red light spread out across the air around it, further the nearer the
 * break (in its last minute it trembles). Broken, it is torn wider, red through and through, and its beam stands
 * thick and red so the break can be seen from far away.</p>
 */
public class GateRenderer extends GeoBlockRenderer<GateBlockEntity> {
    private static final ResourceLocation VORTEX = BeyondSolo.id("textures/effect/vortex.png");
    private static final ResourceLocation GLOW = BeyondSolo.id("textures/effect/glow.png");
    private static final ResourceLocation RING = BeyondSolo.id("textures/effect/ring.png");
    private static final int EXIT_COLOR = 0x9FE6FF;
    private static final int SEALED_COLOR = 0xF2D58A;
    private static final ResourceLocation CIRCLE = BeyondSolo.id("textures/effect/magic_circle.png");
    /** A keyed gate's looks: the keyhole at its heart and the tumbler ring turning around it. */
    private static final ResourceLocation KEY_HOLE = BeyondSolo.id("textures/effect/key_hole.png");
    private static final ResourceLocation KEY_RING = BeyondSolo.id("textures/effect/key_ring.png");
    /** Cracks spreading through the air around a gate coming apart, in three stages. */
    private static final ResourceLocation[] CRACKS = {
            BeyondSolo.id("textures/effect/key_crack_1.png"),
            BeyondSolo.id("textures/effect/key_crack_2.png"),
            BeyondSolo.id("textures/effect/key_crack_3.png")};
    private static final int BREAK_RED = 0xFF2A22;
    /** A red gate's colour: blood, darker than a break's ({@code redgate.RedGates}). */
    private static final int RED_GATE = 0xC8102E;

    public GateRenderer(BlockEntityRendererProvider.Context context) {
        super(new GateModels.Gate());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void render(GateBlockEntity gate, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        super.render(gate, partialTick, pose, buffers, packedLight, packedOverlay);
        if (gate.getLevel() == null) return;
        float open = gate.openness(partialTick);
        long now = gate.getLevel().getGameTime();
        float time = (now % 24000L) + partialTick;
        int rgb = gate.isExit() ? EXIT_COLOR : gate.isSealed() ? SEALED_COLOR : gate.rank().color();
        // coming apart, its light runs red; broken, it is red through and through. How far gone it looks: plain to
        // see from the moment it starts (a third of the way), and worse until it breaks
        boolean broken = gate.isBroken();
        float look = broken ? 1f : gate.isUnstable() ? 0.3f + 0.7f * gate.instability(partialTick) : 0f;
        if (broken) rgb = GateFx.mix(rgb, BREAK_RED, 0.85f);
        else if (look > 0) rgb = GateFx.mix(rgb, BREAK_RED, 0.1f + 0.45f * look);
        // a red gate, with hunters trapped behind it: deep blood red, its swirl slowed to a heavy beat
        float red = gate.redness(partialTick);
        if (red > 0.001f) rgb = GateFx.mix(rgb, RED_GATE, red);
        int pale = GateFx.mix(rgb, 0xFFFFFF, 0.45f);
        float spin = (1 + 1.2f * look + (broken ? 0.6f : 0)) * (1 - 0.55f * red);
        long seed = gate.getBlockPos().asLong();
        float flicker = 1;
        if (broken) flicker = 0.75f + 0.25f * noise((float) Math.floor(time * 0.5f) + seed % 97);
        else if (look > 0 && noise((float) Math.floor(time * 0.35f) + seed % 89) > 0.78f) flicker = 1 - 0.5f * look;

        // opening: a slit that grows tall first, then widens; closing runs it backwards
        float tall = Mth.clamp(open * 2.6f, 0f, 1f);
        float wide = open < 0.3f ? open / 0.3f * 0.08f : 0.08f + 0.92f * GateFx.outBack(Mth.clamp((open - 0.3f) / 0.7f, 0f, 1f));
        if (gate.isClosing()) wide = open;
        float torn = broken ? 1.14f : 1f;
        float hx = GateBlockEntity.HALF_WIDTH * wide * torn;
        float hy = GateBlockEntity.HALF_HEIGHT * tall * torn;
        // ticks since it tore open, counted on the whole clock: counted on the time of day, a gate opened late in one
        // day read as "just opened" all through the next morning, and its flash stayed lit
        float t0 = gate.openedAt() < 0 ? 999 : GateBlockEntity.ticksSince(now, gate.openedAt(), partialTick);
        float tearFlash = Mth.clamp(1 - t0 / 30f, 0f, 1f);
        float closeFlash = 0;
        if (gate.isClosing()) {
            float tc = GateBlockEntity.ticksSince(now, gate.closingAt(), partialTick);
            closeFlash = Mth.clamp(1 - Math.abs(tc - GateBlockEntity.CLOSE_TICKS * 0.85f) / 8f, 0f, 1f);
        }

        pose.pushPose();
        pose.translate(0.5f, GateBlockEntity.CENTER_Y, 0.5f);
        if (gate.breakPhase() == GateBlockEntity.IMMINENT) {
            // its last minute: it trembles
            float j = 0.03f + 0.05f * look;
            pose.translate((noise(time * 0.9f + 3) - 0.5f) * j, (noise(time * 0.9f + 11) - 0.5f) * j, (noise(time * 0.9f + 19) - 0.5f) * j);
        }
        Direction f = gate.facing();
        if (f.getAxis() == Direction.Axis.X) pose.mulPose(Axis.YP.rotationDegrees(90));

        if (open > 0.001f) {
            // the hole: dark, faintly tinted
            VertexConsumer dark = buffers.getBuffer(ModRenderTypes.CRYSTAL);
            ellipse(dark, pose.last().pose(), hx * 0.97f, hy * 0.97f, 0.0f, GateFx.mix(rgb, 0x000000, 0.86f), 0.93f * Mth.clamp(open * 2f, 0f, 1f));

            VertexConsumer vortex = buffers.getBuffer(ModRenderTypes.lightTexture(VORTEX));
            for (int side = 0; side < 2; side++) {
                float z = side == 0 ? 0.02f : -0.02f;
                layer(vortex, pose, z, hx, hy, time * 2.2f * spin, GateFx.mix(rgb, 0x000000, broken ? 0.45f : 0.25f), 0.85f * flicker);
                layer(vortex, pose, z * 1.5f, hx * 0.82f, hy * 0.82f, -time * 3.4f * spin + 40, rgb, 0.75f * flicker);
                layer(vortex, pose, z * 2f, hx * 0.55f, hy * 0.55f, time * 6f * spin, pale, 0.8f * flicker);
            }
            VertexConsumer glow = buffers.getBuffer(ModRenderTypes.lightTexture(GLOW));
            float pulse = 0.5f + 0.5f * Mth.sin(time * 0.12f);
            for (int side = 0; side < 2; side++) {
                float z = side == 0 ? 0.05f : -0.05f;
                pose.pushPose();
                pose.translate(0, 0, z);
                GateFx.quad(glow, pose.last().pose(), hx * 0.42f, hy * 0.42f, broken ? 0xFFB0A0 : 0xFFFFFF, (0.25f + 0.15f * pulse) * flicker + tearFlash);
                GateFx.quad(glow, pose.last().pose(), hx * 2.0f + 0.6f, hy * 1.7f + 0.6f, rgb, (0.32f + 0.12f * pulse + 0.2f * look) * flicker + 0.6f * tearFlash);
                if (look > 0) {
                    // a red haze bleeding out around it, throbbing faster the nearer it is to breaking
                    float throb = 0.7f + 0.3f * Mth.sin(time * (0.15f + 0.45f * look));
                    GateFx.quad(glow, pose.last().pose(), hx * 2.4f + 0.9f, hy * 2.0f + 0.9f, BREAK_RED, (0.16f + 0.3f * look) * throb * flicker);
                }
                if (red > 0.001f) {
                    // a red gate's heartbeat: a dark red haze swelling round it and falling back
                    float beat = heartbeat(time / 20f * 1.1f);
                    float swell = 1 + 0.08f * beat;
                    GateFx.quad(glow, pose.last().pose(), (hx * 2.5f + 1.0f) * swell, (hy * 2.1f + 1.0f) * swell, RED_GATE, (0.14f + 0.32f * beat) * red);
                }
                pose.popPose();
            }
            VertexConsumer ring = buffers.getBuffer(ModRenderTypes.lightTexture(RING));
            for (int side = 0; side < 2; side++) {
                pose.pushPose();
                pose.translate(0, 0, side == 0 ? 0.03f : -0.03f);
                GateFx.quad(ring, pose.last().pose(), hx * 1.12f, hy * 1.08f, pale, (0.65f + 0.25f * pulse) * flicker);
                if (red > 0.001f) {
                    // its seal: a second rim of red light just outside the first, beating with it
                    float beat = heartbeat(time / 20f * 1.1f);
                    GateFx.quad(ring, pose.last().pose(), hx * (1.3f + 0.04f * beat), hy * (1.25f + 0.04f * beat), GateFx.mix(RED_GATE, 0xFFFFFF, 0.2f), (0.35f + 0.4f * beat) * red);
                }
                pose.popPose();
            }
            if (look > 0) {
                // cracks of red light spreading out across the air around it
                int stage = broken ? 2 : look < 0.5f ? 0 : look < 0.8f ? 1 : 2;
                VertexConsumer crack = buffers.getBuffer(ModRenderTypes.lightTexture(CRACKS[stage]));
                float size = GateBlockEntity.HALF_WIDTH * wide * (1.0f + 0.9f * look) * (broken ? 1.3f : 1f);
                float roll = (seed & 0xFF) * 1.4f;
                float glint = 0.8f + 0.2f * Mth.sin(time * (0.15f + 0.35f * look));
                for (int side = 0; side < 2; side++) {
                    pose.pushPose();
                    pose.translate(0, 0, side == 0 ? 0.06f : -0.06f);
                    pose.mulPose(Axis.ZP.rotationDegrees(side == 0 ? roll : -roll));
                    GateFx.quad(crack, pose.last().pose(), size, size, GateFx.mix(BREAK_RED, 0xFFFFFF, 0.3f), (0.4f + 0.55f * look) * glint * flicker);
                    pose.popPose();
                }
            }
            if (gate.isKeyed()) {
                // a door opened by a key: the tumbler ring turning slowly in the swirl, and the keyhole at its heart
                VertexConsumer tumbler = buffers.getBuffer(ModRenderTypes.lightTexture(KEY_RING));
                for (int side = 0; side < 2; side++) {
                    float z = side == 0 ? 0.07f : -0.07f;
                    layer(tumbler, pose, z, hx * 0.72f, hx * 0.72f, time * 0.9f, pale, 0.55f + 0.2f * pulse);
                    layer(tumbler, pose, z * 1.2f, hx * 0.98f, hy * 0.92f, -time * 0.5f + 22, rgb, 0.35f);
                }
                VertexConsumer hole = buffers.getBuffer(ModRenderTypes.lightTexture(KEY_HOLE));
                float beat = 0.75f + 0.25f * Mth.sin(time * 0.2f);
                for (int side = 0; side < 2; side++) {
                    pose.pushPose();
                    pose.translate(0, 0, side == 0 ? 0.09f : -0.09f);
                    GateFx.quad(hole, pose.last().pose(), hx * 0.42f, hx * 0.42f, GateFx.mix(rgb, 0xFFFFFF, 0.7f), (0.55f + 0.3f * beat) * Mth.clamp(open * 1.5f, 0f, 1f));
                    pose.popPose();
                }
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

        if (gate.isSealed() && open > 0.05f) {
            // the sealed gate's hold on its player: a wide circle on the ground that nothing of theirs leaves, and
            // a tighter one turning the other way under the gate
            float beat = 0.6f + 0.4f * Mth.sin(time * 0.16f);
            pose.pushPose();
            pose.translate(0.5f, 0f, 0.5f);
            GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(RING)), pose, 0.08f, com.beyondsolo.client.ClientSystemData.tetherRadius() * open, 0, rgb, 0.5f * beat * open);
            GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(CIRCLE)), pose, 0.1f, 4.2f * open, -time * 1.4f, rgb, 0.7f * open);
            GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, 0.06f, 6f * open, 0, rgb, 0.3f * beat * open);
            pose.popPose();
        }

        // a beam over the gate, so it can be found from far away (a broken gate's stands thick and red)
        if (open > 0.5f && !gate.isExit() && !gate.isKeyed()) {
            float inner = broken ? 0.11f : 0.04f + 0.03f * look + 0.04f * red;
            float outer = broken ? 0.24f : 0.09f + 0.06f * look + 0.07f * red;
            pose.pushPose();
            pose.translate(0f, GateBlockEntity.CENTER_Y + 2.4f, 0f);
            BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, partialTick, 1f, gate.getLevel().getGameTime(), 0, broken ? 192 : 96,
                    0xFF000000 | GateFx.mix(rgb, 0xFFFFFF, broken ? 0.05f : 0.2f), inner * open, outer * open);
            pose.popPose();
        }
    }

    /** A heartbeat: two quick thumps a beat, then rest (0..1). */
    private static float heartbeat(float t) {
        float f = t - (float) Math.floor(t);
        float a = Math.max(0, 1 - Math.abs(f - 0.08f) / 0.08f);
        float b = Math.max(0, 1 - Math.abs(f - 0.28f) / 0.1f) * 0.7f;
        return Math.max(a, b);
    }

    private static float noise(float seed) {
        float s = Mth.sin(seed * 12.9898f + 78.233f) * 43758.547f;
        return s - (float) Math.floor(s);
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
        int r = gate.isSealed() ? 70 : 4;
        return new AABB(p.getX() - r, p.getY() - 1, p.getZ() - r, p.getX() + r + 1, p.getY() + (gate.isBroken() ? 200 : 100), p.getZ() + r + 1);
    }
}
