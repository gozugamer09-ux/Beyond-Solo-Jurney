package com.beyondsolo.client.chronicle;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Pieces the three CHRONICLE sections share: clickable areas, pill tabs, buttons, scrollbars. */
final class ChronicleUi {
    /** A clickable area and what it does. */
    record Hit(float x, float y, float w, float h, Runnable action) {
        boolean contains(double mx, double my) {
            return Ui.inside(mx, my, x, y, w, h);
        }
    }

    static boolean click(List<Hit> hits, double mx, double my) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.contains(mx, my)) {
                h.action().run();
                return true;
            }
        }
        return false;
    }

    /** A small pill to pick a section or a filter: lit when selected. Returns its width. */
    static float pill(GuiGraphics g, float x, float y, float h, String label, @Nullable ResourceLocation icon, int color, boolean selected, boolean hover,
                      float size) {
        float tw = label.isEmpty() ? 0 : SysFont.spacedWidth(label, size, true, 0.9f);
        float iconW = icon == null ? 0 : h * 0.75f + (label.isEmpty() ? 0 : 2.5f);
        float w = tw + iconW + h * 0.9f;
        if (selected) {
            Shapes.pill(x, y, w, h).fillV(Gfx.alpha(color, 0.34f), Gfx.alpha(Gfx.shade(color, -0.4f), 0.22f))
                    .border(0.75f, Gfx.alpha(color, 0.95f), Gfx.alpha(color, 0.4f)).glow(4, Gfx.alpha(color, 0.28f)).draw(g);
        } else {
            Shapes.pill(x, y, w, h).fill(hover ? 0x18FFFFFF : 0x0CFFFFFF).border(0.6f, hover ? Gfx.alpha(color, 0.6f) : 0x26FFFFFF).draw(g);
        }
        int text = selected ? SystemTheme.TEXT : hover ? Gfx.mix(color, 0xFFFFFFFF, 0.45f) : SystemTheme.TEXT_SOFT;
        float cx = x + h * 0.45f;
        if (icon != null) {
            Shapes.icon(icon, cx + h * 0.375f, y + h / 2f, h * 0.75f).fill(selected ? Gfx.mix(color, 0xFFFFFFFF, 0.5f) : text).draw(g);
            cx += iconW;
        }
        if (!label.isEmpty()) SysFont.spaced(g, label, cx, SysFont.centerY(y, h, size), size, text, true, 0.9f, 0f);
        return w;
    }

    /** The width {@link #pill} will take. */
    static float pillWidth(float h, String label, boolean icon, float size) {
        float tw = label.isEmpty() ? 0 : SysFont.spacedWidth(label, size, true, 0.9f);
        float iconW = !icon ? 0 : h * 0.75f + (label.isEmpty() ? 0 : 2.5f);
        return tw + iconW + h * 0.9f;
    }

    /** A call to action, with a slow shine when it can be pressed. */
    static void button(GuiGraphics g, float x, float y, float w, float h, String label, @Nullable ResourceLocation icon, int color, boolean enabled, boolean hover) {
        int c = enabled ? color : SystemTheme.TEXT_DIM;
        float sheen = Anim.loop(3.2f) * 1.6f - 0.3f;
        Shapes.pill(x, y, w, h).fillV(Gfx.alpha(c, hover ? 0.42f : 0.26f), Gfx.alpha(Gfx.shade(c, -0.4f), hover ? 0.3f : 0.16f))
                .border(0.9f, Gfx.alpha(Gfx.mix(c, 0xFFFFFFFF, 0.25f), enabled ? 0.95f : 0.4f), Gfx.alpha(c, 0.3f))
                .glow(enabled ? 4 + (hover ? 3 : 0) : 0, Gfx.alpha(c, 0.3f))
                .sheen(enabled ? sheen : -2, 0.16f).draw(g);
        float size = 6.2f;
        String text = SysFont.fit(label, size, true, w - 22);
        float lw = SysFont.spacedWidth(text, size, true, 1.1f);
        float iw = icon == null ? 0 : 10;
        float lx = x + (w - lw - iw) / 2f;
        if (icon != null) Shapes.icon(icon, lx + 3.5f, y + h / 2f, 7.5f).fill(enabled ? SystemTheme.TEXT : SystemTheme.TEXT_SOFT).draw(g);
        SysFont.spaced(g, text, lx + iw, SysFont.centerY(y, h, size), size, enabled ? SystemTheme.TEXT : SystemTheme.TEXT_SOFT, true, 1.1f, 0f);
    }

    /** A thin scrollbar at the right edge of a list. */
    static void scrollbar(GuiGraphics g, float x, float top, float height, float scroll, float max, int color) {
        if (max <= 0) return;
        float thumbH = Math.max(14, height * height / (height + max));
        float thumbY = top + (height - thumbH) * (scroll / max);
        Shapes.pill(x, top, 2, height).fill(0x22FFFFFF).draw(g);
        Shapes.pill(x, thumbY, 2, thumbH).fill(Gfx.alpha(color, 0.75f)).draw(g);
    }

    /** "NEW" on something not looked at yet. */
    static void fresh(GuiGraphics g, float x, float y) {
        String s = net.minecraft.network.chat.Component.translatable("chronicle.beyondsolo.new").getString();
        float w = SysFont.width(s, 4.4f, true) + 5;
        Shapes.pill(x, y, w, 7).fill(Gfx.alpha(SystemTheme.GOLD, 0.3f + 0.15f * Anim.pulse(1.4f))).border(0.5f, SystemTheme.GOLD).draw(g);
        SysFont.draw(g, s, x + w / 2f, SysFont.centerY(y, 7, 4.4f), 4.4f, 0xFFFFF4D6, true, 0.5f);
    }

    /** The day of the world a game time falls on (the first day is 1). */
    static long day(long gameTime) {
        return gameTime / 24000L + 1;
    }

    private ChronicleUi() {}
}
