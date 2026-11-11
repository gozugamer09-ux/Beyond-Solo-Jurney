package com.beyondsolo.client;

import net.minecraft.client.gui.GuiGraphics;

/** Colours and frame drawing shared by the System window and pop-ups (blue holographic look). */
public final class SystemTheme {
    public static final int BACKGROUND = 0xE0071322;
    public static final int BACKGROUND_SOFT = 0xB0102438;
    public static final int BORDER = 0xFF4FC3F7;
    public static final int BORDER_DIM = 0x804FC3F7;
    public static final int GLOW = 0x304FC3F7;
    public static final int TEXT = 0xFFE3F2FD;
    public static final int TEXT_DIM = 0xFF90A4AE;
    public static final int ACCENT = 0xFF81D4FA;
    public static final int HP = 0xFFE53935;
    public static final int MP = 0xFF1E88E5;
    public static final int XP = 0xFF26C6DA;
    public static final int FATIGUE = 0xFFFFA726;

    /** Window with a glowing border and corner brackets. */
    public static void frame(GuiGraphics g, int x, int y, int w, int h, int border, int alpha) {
        g.fill(x, y, x + w, y + h, withAlpha(BACKGROUND, alpha));
        int glow = withAlpha(border, (int) (alpha * 0.19f));
        g.fill(x - 1, y - 1, x + w + 1, y, glow);
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, glow);
        g.renderOutline(x, y, w, h, withAlpha(border, (int) (alpha * 0.5f)));
        int c = withAlpha(border, alpha);
        int len = Math.min(10, Math.min(w, h) / 3);
        // corner brackets
        g.fill(x - 1, y - 1, x + len, y + 1, c);
        g.fill(x - 1, y - 1, x + 1, y + len, c);
        g.fill(x + w - len, y - 1, x + w + 1, y + 1, c);
        g.fill(x + w - 1, y - 1, x + w + 1, y + len, c);
        g.fill(x - 1, y + h - 1, x + len, y + h + 1, c);
        g.fill(x - 1, y + h - len, x + 1, y + h + 1, c);
        g.fill(x + w - len, y + h - 1, x + w + 1, y + h + 1, c);
        g.fill(x + w - 1, y + h - len, x + w + 1, y + h + 1, c);
    }

    public static void bar(GuiGraphics g, int x, int y, int w, int h, float fraction, int color) {
        fraction = Math.max(0, Math.min(1, fraction));
        g.fill(x, y, x + w, y + h, 0xA0000000);
        g.fill(x, y, x + Math.round(w * fraction), y + h, color);
        g.renderOutline(x - 1, y - 1, w + 2, h + 2, BORDER_DIM);
    }

    public static int withAlpha(int color, int alpha) {
        int a = Math.max(0, Math.min(255, ((color >>> 24) * alpha) / 255));
        return (a << 24) | (color & 0xFFFFFF);
    }

    private SystemTheme() {}
}
