package com.beyondsolo.client.home;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.home.HomeTier;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * The home on the HUD: the gathering while a hunter goes home (a ring filling, "stand still"), a red word if it breaks,
 * and as they come in, the home's name over the room for a few seconds ("THE RESIDENCE · Penthouse · Welcome home", or
 * whose home they are a guest in).
 */
public final class HomeHud {
    /** The residence's colour: the pale blue of the city's night. */
    public static final int NIGHT = 0xFF9FD3FF;

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;
        int w = g.guiWidth(), h = g.guiHeight();
        if (HomeClient.channeling()) {
            channel(g, w, h);
            return;
        }
        int kind = HomeClient.flashKind();
        float t = HomeClient.sinceFlash();
        if (kind == HomeClient.FLASH_BROKEN && t < 2.6f) broken(g, w, h, t);
        else if (kind == HomeClient.FLASH_ARRIVED && t < 4.2f) arrived(g, w, t);
    }

    /** Gathering: a card low in the middle of the screen with a ring filling round the spiral. */
    private static void channel(GuiGraphics g, int w, int h) {
        float k = HomeClient.channel();
        float bw = 150, bh = 26;
        float x = w / 2f - bw / 2f, y = h * 0.64f;
        Shapes.box(x, y, bw, bh).radius(8).fillV(Gfx.fade(SystemTheme.GLASS_TOP, 0.88f), Gfx.fade(SystemTheme.GLASS_BOTTOM, 0.88f))
                .border(0.8f, Gfx.alpha(NIGHT, 0.8f), Gfx.alpha(NIGHT, 0.25f)).glow(6 + 4 * k, Gfx.alpha(NIGHT, 0.25f + 0.2f * k)).draw(g);
        Shapes.ring(x + 13, y + bh / 2f, 7, 0.9f).fill(Gfx.alpha(NIGHT, 0.25f)).draw(g);
        Shapes.ring(x + 13, y + bh / 2f, 7, 0.9f).fill(Gfx.mix(NIGHT, 0xFFFFFFFF, 0.3f)).progress(k).glow(2, Gfx.alpha(NIGHT, 0.6f)).draw(g);
        Shapes.icon(Icons.MOON, x + 13, y + bh / 2f, 7).fill(Gfx.mix(NIGHT, 0xFFFFFFFF, 0.5f)).draw(g);
        Component to = HomeClient.channelTo();
        boolean own = to.getString().isEmpty() || to.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents;
        String title = (own ? Component.translatable("home.beyondsolo.hud.going")
                : Component.translatable("home.beyondsolo.hud.going_visit", to)).getString().toUpperCase(Locale.ROOT);
        SysFont.spaced(g, SysFont.fit(title, 5.8f, true, bw - 34), x + 25, y + 5, 5.8f, SystemTheme.TEXT, true, 1.1f, 0f);
        SysFont.text(g, Component.translatable("home.beyondsolo.hud.stand_still").getString(), x + 25, y + 14.5f, 4.8f, SystemTheme.TEXT_SOFT);
        Ui.bar(g, x + 25, y + bh - 4.5f, bw - 33, 1.8f, k, Gfx.shade(NIGHT, -0.2f), Gfx.mix(NIGHT, 0xFFFFFFFF, 0.4f));
    }

    /** The gathering broke: why, in red, for a moment. */
    private static void broken(GuiGraphics g, int w, int h, float t) {
        float k = (1 - Anim.window(t, 2.0f, 2.6f)) * Anim.outCubic(Anim.window(t, 0f, 0.2f));
        String text = SysFont.fit(HomeClient.flashText().getString(), 6f, true, w - 60);
        float tw = SysFont.width(text, 6f, true) + 26;
        float cx = w / 2f, y = h * 0.64f;
        float old = Gfx.fade(k);
        Shapes.pill(cx - tw / 2f, y, tw, 14).fill(Gfx.alpha(0xFF060A14, 0.85f)).border(0.8f, Gfx.alpha(SystemTheme.DANGER, 0.85f))
                .glow(6, Gfx.alpha(SystemTheme.DANGER, 0.35f)).draw(g);
        Shapes.icon(Icons.X, cx - tw / 2f + 9, y + 7, 7).fill(Gfx.mix(SystemTheme.DANGER, 0xFFFFFFFF, 0.4f)).draw(g);
        SysFont.draw(g, text, cx + 5, SysFont.centerY(y, 14, 6f), 6f, SystemTheme.TEXT, true, 0.5f);
        Gfx.setAlpha(old);
    }

    /** Coming in: the home's name over the room, rising in and fading out. */
    private static void arrived(GuiGraphics g, int w, float t) {
        float in = Anim.outCubic(Anim.window(t, 0.15f, 0.6f));
        float out = 1 - Anim.window(t, 3.4f, 4.2f);
        float k = in * out;
        if (k <= 0.01f) return;
        Component host = HomeClient.flashText();
        boolean guest = !host.getString().isEmpty();
        HomeTier tier = HomeClient.flashTier();
        String caption = Component.translatable(guest ? "home.beyondsolo.hud.guest" : "home.beyondsolo.hud.caption").getString().toUpperCase(Locale.ROOT);
        String title = guest ? Component.translatable("home.beyondsolo.hud.whose", host).getString() : tier.displayName().getString();
        String sub = Component.translatable(guest ? "home.beyondsolo.hud.guest_line" : "home.beyondsolo.hud.welcome").getString();
        float cx = w / 2f;
        float y = g.guiHeight() * 0.2f + 6 * (1 - in);
        float old = Gfx.fade(k);
        // the caption between two thin lines
        float cs = 5.4f;
        float cw = SysFont.spacedWidth(caption, cs, true, 2.2f);
        SysFont.spaced(g, caption, cx, y, cs, Gfx.mix(NIGHT, 0xFFFFFFFF, 0.3f), true, 2.2f, 0.5f);
        float line = 34 * Anim.outCubic(Anim.window(t, 0.3f, 1.0f));
        Shapes.box(cx - cw / 2f - 8 - line, y + cs * 0.4f, line, 0.7f).fillH(Gfx.alpha(NIGHT, 0f), Gfx.alpha(NIGHT, 0.8f)).draw(g);
        Shapes.box(cx + cw / 2f + 8, y + cs * 0.4f, line, 0.7f).fillH(Gfx.alpha(NIGHT, 0.8f), Gfx.alpha(NIGHT, 0f)).draw(g);
        // the name, big, with the night's light behind it
        float ts = 15f;
        String fitted = SysFont.fit(title, ts, true, w - 40);
        float tw = SysFont.width(fitted, ts, true);
        Shapes.box(cx - tw / 2f - 18, y + 9, tw + 36, ts * 1.1f).radius(ts * 0.55f).fill(Gfx.alpha(0xFF04070F, 0.35f))
                .glow(14, Gfx.alpha(NIGHT, 0.18f)).draw(g);
        SysFont.draw(g, fitted, cx, y + 10, ts, SystemTheme.TEXT, true, 0.5f);
        SysFont.draw(g, sub, cx, y + 12 + ts * 1.15f, 6f, SystemTheme.TEXT_SOFT, false, 0.5f);
        Gfx.setAlpha(old);
    }

    private HomeHud() {}
}
