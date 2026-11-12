package com.beyondsolo.client.notify;

import com.beyondsolo.client.ClientConfig;
import com.beyondsolo.client.ClientEvents;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Draws the pop-ups on the HUD. Floating mode keeps them at the side of your view, drifting as you turn
 * and bobbing gently; pinned mode stacks them at the top centre; hidden mode shows only an unread badge.
 */
public final class NotificationRenderer {
    private static final float GAP = 6;
    /** How far the floating windows trail behind the camera, in GUI pixels per degree. */
    private static final float DRIFT = 3.4f;

    private static float lagYaw = Float.NaN;
    private static float lagPitch;
    private static float badge;

    public static void renderHud(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui) return;
        ClientConfig.NotificationMode mode = ClientConfig.NOTIFICATION_MODE.get();
        List<NotificationManager.Popup> active = NotificationManager.active();
        int guiW = g.guiWidth();
        int guiH = g.guiHeight();

        // unread badge (hidden mode)
        boolean showBadge = mode == ClientConfig.NotificationMode.HIDDEN && NotificationManager.unread() > 0 && active.isEmpty();
        badge = Anim.approach(badge, showBadge ? 1 : 0, 12f);
        if (badge > 0.02f) drawBadge(g, guiW);

        if (active.isEmpty()) {
            lagYaw = Float.NaN;
            return;
        }

        boolean floating = mode == ClientConfig.NotificationMode.FLOATING;
        float pt = delta.getGameTimeDeltaPartialTick(false);
        float driftX = 0;
        float driftY = 0;
        if (floating) {
            float yaw = player.getViewYRot(pt);
            float pitch = player.getViewXRot(pt);
            if (Float.isNaN(lagYaw)) {
                lagYaw = yaw;
                lagPitch = pitch;
            }
            float k = 1 - (float) Math.exp(-5.5f * Anim.dt());
            lagYaw += Mth.wrapDegrees(yaw - lagYaw) * k;
            lagPitch += (pitch - lagPitch) * k;
            driftX = Mth.clamp(Mth.wrapDegrees(lagYaw - yaw) * DRIFT, -70f, 70f);
            driftY = Mth.clamp((lagPitch - pitch) * DRIFT, -45f, 45f);
        }

        float baseX = floating ? guiW - PopupPanel.WIDTH - 16 : (guiW - PopupPanel.WIDTH) / 2f;
        float y = floating ? Math.max(26, guiH * 0.17f) : 12;
        String key = ClientEvents.DISMISS.getTranslatedKeyMessage().getString();
        if (key.length() > 6) key = null;
        boolean keep = ClientConfig.KEEP_POPUPS.get();

        NotificationManager.Popup last = null;
        for (NotificationManager.Popup p : active) if (!p.hiding()) last = p;

        int index = 0;
        for (NotificationManager.Popup p : active) {
            PopupPanel panel = new PopupPanel(p.notification);
            p.y = Float.isNaN(p.y) ? y : Anim.approach(p.y, y, 14f);
            float px = baseX;
            float py = p.y;
            float appear = Anim.outCubic(p.appear());
            if (floating) {
                double t = Anim.now();
                px += driftX + (float) Math.cos(t * 0.9 + index * 1.7) * 1.4f + (1 - appear) * 26f + p.exit() * 20f;
                py += driftY + (float) Math.sin(t * 1.25 + index * 1.1) * 2.4f;
            } else {
                py += (1 - appear) * -14f - p.exit() * 8f;
            }
            float timeLeft = keep || p.hiding() ? -1 : p.remaining();
            panel.draw(g, px, py, p, p == last ? key : null, timeLeft);
            // a pop-up that is leaving gives its space back gradually
            y += (panel.height() + GAP) * (1 - Anim.inOutCubic(p.exit()));
            index++;
        }
    }

    private static void drawBadge(GuiGraphics g, int guiW) {
        String text = Component.translatable("hud.beyondsolo.unread", NotificationManager.unread()).getString();
        String key = ClientEvents.DISMISS.getTranslatedKeyMessage().getString();
        boolean showKey = key.length() <= 6;
        float tw = SysFont.width(text, 8f, true);
        float kw = showKey ? Ui.keycapWidth(10, key) + 5 : 0;
        float w = 22 + tw + kw + 8;
        float h = 18;
        float x = guiW - w - 8 + (1 - badge) * 12;
        float y = 8;
        float old = Gfx.fade(badge);
        float pulse = Anim.pulse(1.8f);
        Shapes.pill(x, y, w, h).fillV(SystemTheme.GLASS_TOP, SystemTheme.GLASS_BOTTOM)
                .border(0.75f, Gfx.alpha(SystemTheme.ACCENT, 0.9f), Gfx.alpha(SystemTheme.ACCENT, 0.35f))
                .glow(5 + pulse * 3, Gfx.alpha(SystemTheme.ACCENT, 0.2f + 0.15f * pulse)).draw(g);
        Shapes.icon(Icons.BELL, x + 11, y + h / 2f, 10).fill(SystemTheme.ACCENT_BRIGHT).glow(2, Gfx.alpha(SystemTheme.ACCENT, 0.6f)).draw(g);
        SysFont.title(g, text, x + 19, SysFont.centerY(y, h, 8f), 8f, SystemTheme.TEXT);
        if (showKey) Ui.keycap(g, x + 19 + tw + 5, y + 4, 10, key, SystemTheme.TEXT_SOFT);
        Gfx.setAlpha(old);
    }

    public static void reset() {
        lagYaw = Float.NaN;
        badge = 0;
    }

    private NotificationRenderer() {}
}
