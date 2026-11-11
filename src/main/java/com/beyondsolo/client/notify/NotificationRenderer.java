package com.beyondsolo.client.notify;

import com.beyondsolo.client.ClientConfig;
import com.beyondsolo.client.SystemTheme;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;

/** Draws pop-ups: floating in the world beside the player, pinned to the HUD, or as an unread badge. */
public final class NotificationRenderer {
    /** World units per GUI pixel for floating windows. */
    private static final float WORLD_SCALE = 0.0055f;
    private static final double FLOAT_DISTANCE = 1.9;
    private static final float FLOAT_ANGLE = 32f;

    private static float smoothedYaw = Float.NaN;
    private static long lastFrameNanos;

    // ---------------- HUD (pinned + hidden badge) ----------------

    public static void renderHud(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        ClientConfig.NotificationMode mode = ClientConfig.NOTIFICATION_MODE.get();
        Font font = mc.font;

        if (mode == ClientConfig.NotificationMode.HIDDEN) {
            int unread = NotificationManager.unread();
            if (unread > 0) {
                Component badge = Component.translatable("hud.beyondsolo.unread", unread);
                int w = font.width(badge) + 10;
                int x = g.guiWidth() - w - 6;
                SystemTheme.frame(g, x, 6, w, 14, SystemTheme.BORDER, 255);
                g.drawString(font, badge, x + 5, 9, SystemTheme.ACCENT, false);
            }
            return;
        }

        boolean floating = mode == ClientConfig.NotificationMode.FLOATING && !mc.options.getCameraType().isMirrored();
        if (floating && mc.screen == null) return; // drawn in the world instead
        long now = mc.player.tickCount;
        float pt = delta.getGameTimeDeltaPartialTick(false);
        GuiCanvas canvas = new GuiCanvas(g, font);
        int y = 10;
        for (NotificationManager.Active a : NotificationManager.active()) {
            NotificationPanel panel = new NotificationPanel(font, a.notification);
            float open = a.openAmount(now, pt);
            int x = (g.guiWidth() - NotificationPanel.WIDTH) / 2;
            int slide = Math.round((1 - open) * -12);
            panel.draw(font, canvas, x, y + slide, NotificationPanel.alpha(open));
            y += Math.round((panel.height() + 6) * Math.min(1, open * 1.5f));
        }
    }

    private record GuiCanvas(GuiGraphics g, Font font) implements NotificationPanel.Canvas {
        @Override
        public void fill(int x1, int y1, int x2, int y2, int argb) {
            g.fill(x1, y1, x2, y2, argb);
        }

        @Override
        public void text(FormattedCharSequence text, int x, int y, int argb) {
            g.drawString(font, text, x, y, argb, false);
        }
    }

    // ---------------- World (floating) ----------------

    public static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || mc.screen != null) return;
        if (ClientConfig.NOTIFICATION_MODE.get() != ClientConfig.NotificationMode.FLOATING) return;
        if (mc.options.getCameraType().isMirrored()) return; // front view: HUD fallback
        List<NotificationManager.Active> active = NotificationManager.active();
        if (active.isEmpty()) {
            smoothedYaw = Float.NaN;
            return;
        }

        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float yaw = player.getViewYRot(pt);
        long nowNanos = System.nanoTime();
        float dt = lastFrameNanos == 0 ? 0.016f : Math.min(0.1f, (nowNanos - lastFrameNanos) / 1e9f);
        lastFrameNanos = nowNanos;
        // The windows lag behind your head a little, so they drift around you as you turn.
        if (Float.isNaN(smoothedYaw)) smoothedYaw = yaw;
        smoothedYaw += Mth.wrapDegrees(yaw - smoothedYaw) * Math.min(1f, dt * 4f);

        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vec3 eye = player.getEyePosition(pt);
        Font font = mc.font;
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        long now = player.tickCount;
        double bob = Math.sin((player.tickCount + pt) * 0.06) * 0.03;

        double rad = Math.toRadians(smoothedYaw + FLOAT_ANGLE);
        double dx = -Math.sin(rad) * FLOAT_DISTANCE;
        double dz = Math.cos(rad) * FLOAT_DISTANCE;
        double stackY = 0.35;

        for (NotificationManager.Active a : active) {
            NotificationPanel panel = new NotificationPanel(font, a.notification);
            float open = a.openAmount(now, pt);
            double heightWorld = panel.height() * WORLD_SCALE;

            pose.pushPose();
            pose.translate(eye.x + dx - cam.x, eye.y + stackY + bob - cam.y, eye.z + dz - cam.z);
            pose.mulPose(camera.rotation());
            float s = WORLD_SCALE * (0.85f + 0.15f * open);
            pose.scale(s, -s, s);
            WorldCanvas canvas = new WorldCanvas(pose.last().pose(), buffers, font);
            panel.draw(font, canvas, -NotificationPanel.WIDTH / 2, -panel.height() / 2, NotificationPanel.alpha(open));
            canvas.finish();
            pose.popPose();

            stackY -= (heightWorld + 0.08) * Math.min(1, open * 1.5f);
        }
    }

    private static final class WorldCanvas implements NotificationPanel.Canvas {
        private final Matrix4f matrix;
        private final MultiBufferSource.BufferSource buffers;
        private final Font font;
        private VertexConsumer fills;

        WorldCanvas(Matrix4f matrix, MultiBufferSource.BufferSource buffers, Font font) {
            this.matrix = matrix;
            this.buffers = buffers;
            this.font = font;
        }

        @Override
        public void fill(int x1, int y1, int x2, int y2, int argb) {
            if (fills == null) fills = buffers.getBuffer(RenderType.textBackgroundSeeThrough());
            // See-through render types skip the depth test, so later fills simply draw on top.
            quad(fills, x1, y1, x2, y2, 0, argb);
        }

        @Override
        public void beginText() {
            buffers.endBatch(RenderType.textBackgroundSeeThrough());
            fills = null;
        }

        @Override
        public void text(FormattedCharSequence text, int x, int y, int argb) {
            font.drawInBatch(text, x, y, argb, false, matrix, buffers, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
        }

        void finish() {
            buffers.endBatch();
        }

        private void quad(VertexConsumer vc, float x1, float y1, float x2, float y2, float z, int argb) {
            int light = LightTexture.FULL_BRIGHT;
            // Both windings so the quad shows whichever way culling is set up.
            vc.addVertex(matrix, x1, y1, z).setColor(argb).setLight(light);
            vc.addVertex(matrix, x1, y2, z).setColor(argb).setLight(light);
            vc.addVertex(matrix, x2, y2, z).setColor(argb).setLight(light);
            vc.addVertex(matrix, x2, y1, z).setColor(argb).setLight(light);
            vc.addVertex(matrix, x2, y1, z).setColor(argb).setLight(light);
            vc.addVertex(matrix, x2, y2, z).setColor(argb).setLight(light);
            vc.addVertex(matrix, x1, y2, z).setColor(argb).setLight(light);
            vc.addVertex(matrix, x1, y1, z).setColor(argb).setLight(light);
        }
    }

    private NotificationRenderer() {}
}
