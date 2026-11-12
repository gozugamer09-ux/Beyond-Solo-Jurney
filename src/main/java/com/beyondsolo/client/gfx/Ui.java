package com.beyondsolo.client.gfx;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** The building blocks every System surface shares: glass panels, cards, bars, badges, buttons. */
public final class Ui {
    /** The main glass panel: rounded, lit from the top, with a soft glow in the accent colour. */
    public static void panel(GuiGraphics g, float x, float y, float w, float h, float radius, int accent, float glow) {
        // soft drop shadow
        Shapes.box(x + 1, y + 3, w - 2, h - 2).radius(radius).fill(0x00000000).glow(14, 0x70000000).draw(g);
        Shapes.box(x, y, w, h).radius(radius)
                .fillV(SystemTheme.GLASS_TOP, SystemTheme.GLASS_BOTTOM)
                .border(1f, Gfx.fade(accent, 0.95f), Gfx.fade(accent, 0.28f))
                .glow(11, Gfx.fade(accent, 0.26f * glow))
                .draw(g);
        // light bar on the top edge
        float bar = Math.min(54, w * 0.3f);
        Shapes.pill(x + (w - bar) / 2f, y - 0.75f, bar, 1.5f).fill(Gfx.mix(accent, 0xFFFFFFFF, 0.55f)).glow(5, Gfx.fade(accent, 0.75f * glow)).draw(g);
    }

    /** An inner card on top of a panel. */
    public static void card(GuiGraphics g, float x, float y, float w, float h, float radius) {
        Shapes.box(x, y, w, h).radius(radius).fillV(SystemTheme.CARD_TOP, SystemTheme.CARD_BOTTOM)
                .border(0.75f, SystemTheme.CARD_BORDER, Gfx.fade(SystemTheme.CARD_BORDER, 0.35f)).draw(g);
    }

    public static void card(GuiGraphics g, float x, float y, float w, float h, float radius, int tint, float strength) {
        Shapes.box(x, y, w, h).radius(radius)
                .fillV(Gfx.mix(SystemTheme.CARD_TOP, Gfx.alpha(tint, 0.45f), strength), Gfx.mix(SystemTheme.CARD_BOTTOM, Gfx.alpha(tint, 0.2f), strength))
                .border(0.75f, Gfx.mix(SystemTheme.CARD_BORDER, Gfx.alpha(tint, 0.7f), strength), Gfx.mix(Gfx.fade(SystemTheme.CARD_BORDER, 0.35f), Gfx.alpha(tint, 0.3f), strength))
                .draw(g);
    }

    /** A capsule progress bar with a gradient fill, a glow at the tip and a slow shine. */
    public static void bar(GuiGraphics g, float x, float y, float w, float h, float fraction, int colorA, int colorB) {
        fraction = Mth.clamp(fraction, 0f, 1f);
        Shapes.pill(x, y, w, h).fill(SystemTheme.TRACK).border(0.5f, SystemTheme.TRACK_BORDER).draw(g);
        if (fraction <= 0.004f) return;
        float fw = Math.max(h, w * fraction);
        float fadeIn = Mth.clamp(w * fraction / h, 0f, 1f);
        float old = Gfx.fade(fadeIn);
        float sheen = Anim.loop(3.2f) * 1.6f - 0.3f;
        Shapes.pill(x, y, fw, h).fillH(colorA, colorB).glow(h * 0.9f, Gfx.fade(colorB, 0.45f)).sheen(sheen, 0.22f).draw(g);
        Gfx.setAlpha(old);
    }

    /** A round badge with an icon: tinted disc, coloured rim, icon in the middle. */
    public static void iconBadge(GuiGraphics g, ResourceLocation icon, float cx, float cy, float radius, int color) {
        Shapes.circle(cx, cy, radius).fillV(Gfx.alpha(color, 0.30f), Gfx.alpha(color, 0.12f)).border(0.75f, Gfx.alpha(color, 0.75f), Gfx.alpha(color, 0.35f)).draw(g);
        Shapes.icon(icon, cx, cy, radius * 1.25f).fill(Gfx.mix(color, 0xFFFFFFFF, 0.35f)).glow(radius * 0.35f, Gfx.alpha(color, 0.5f)).draw(g);
    }

    /** A small label in a pill. Returns the width used. */
    public static float chip(GuiGraphics g, float x, float y, float h, String text, float size, int color, boolean filled) {
        float tw = SysFont.width(text, size, true);
        float w = tw + h * 0.9f;
        Shapes.pill(x, y, w, h).fill(Gfx.alpha(color, filled ? 0.22f : 0.08f)).border(0.75f, Gfx.alpha(color, 0.6f)).draw(g);
        SysFont.draw(g, text, x + w / 2f, SysFont.centerY(y, h, size), size, Gfx.mix(color, 0xFFFFFFFF, 0.3f), true, 0.5f);
        return w;
    }

    /** A keyboard key hint such as [B]. Returns the width used. */
    public static float keycap(GuiGraphics g, float x, float y, float h, String key, int color) {
        float size = h * 0.68f;
        float w = Math.max(h, SysFont.width(key, size, true) + h * 0.6f);
        Shapes.box(x, y, w, h).radius(h * 0.3f).fillV(Gfx.alpha(color, 0.18f), Gfx.alpha(color, 0.06f)).border(0.75f, Gfx.alpha(color, 0.65f), Gfx.alpha(color, 0.3f)).draw(g);
        SysFont.draw(g, key, x + w / 2f, SysFont.centerY(y, h, size), size, Gfx.mix(color, 0xFFFFFFFF, 0.4f), true, 0.5f);
        return w;
    }

    public static float keycapWidth(float h, String key) {
        return Math.max(h, SysFont.width(key, h * 0.68f, true) + h * 0.6f);
    }

    /** A hairline that fades out towards both ends. */
    public static void divider(GuiGraphics g, float x, float y, float w, int color) {
        Shapes.box(x, y, w / 2f, 0.75f).fillH(Gfx.fade(color, 0f), color).draw(g);
        Shapes.box(x + w / 2f, y, w / 2f, 0.75f).fillH(color, Gfx.fade(color, 0f)).draw(g);
    }

    /**
     * The rank emblem: a dark disc with the rank letter, a coloured rim and a slowly turning dashed ring.
     * {@code energy} 0..1 makes it glow harder (used right after a rank is revealed).
     */
    public static void rankEmblem(GuiGraphics g, PlayerRank rank, float cx, float cy, float radius, float energy) {
        int color = SystemTheme.rank(rank);
        float t = Anim.loop(18f);
        Shapes.circle(cx, cy, radius).fillV(0xF0142238, 0xF0070D1A).border(Math.max(0.75f, radius * 0.08f), color, Gfx.fade(color, 0.45f))
                .glow(radius * (0.55f + energy * 0.6f), Gfx.alpha(color, 0.35f + 0.35f * energy)).draw(g);
        Shapes.ring(cx, cy, radius * 1.22f, Math.max(0.6f, radius * 0.06f)).fill(Gfx.alpha(color, 0.75f)).dashes(18, 0.45f).rotation(t).draw(g);
        Shapes.icon(Icons.of(rank), cx, cy + radius * 0.02f, radius * 1.5f).fillV(Gfx.mix(color, 0xFFFFFFFF, 0.75f), Gfx.mix(color, 0xFFFFFFFF, 0.2f))
                .glow(radius * 0.3f, Gfx.alpha(color, 0.7f)).draw(g);
    }

    /** True if the point is inside the rectangle. */
    public static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private Ui() {}
}
