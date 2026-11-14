package com.beyondsolo.client.gfx;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Text in the System typeface (Exo 2). Sizes are in GUI pixels: 10 is about the size of vanilla text.
 *
 * <p>Minecraft does not smooth scaled TTF glyphs, so there is one font file per pixel size
 * (see tools/gen_fonts.py) and this class picks the one that lands 1:1 on screen pixels for the
 * current GUI scale. Text with characters the typeface lacks (or with the typeface turned off in
 * the client config) falls back to the vanilla font at the same size.</p>
 *
 * <p>{@code y} is always the top of the capital letters, so a line of size {@code s} is
 * {@code s * CAP} tall. Use {@link #centerY} to centre a line in a box.</p>
 */
public final class SysFont {
    /** Height of capital letters relative to the font size. */
    public static final float CAP = 0.69f;
    /** Comfortable distance between two lines of text relative to the font size. */
    public static final float LINE = 1.28f;

    // Keep in sync with tools/gen_fonts.py.
    private static final int[] SIZES = {7, 8, 9, 10, 11, 12, 14, 16, 18, 20, 21, 22, 24, 27, 28, 30, 32, 33, 36, 40, 42, 44, 48,
            54, 56, 60, 64, 72, 80, 84, 88, 96, 112, 128};
    private static final Style[] TEXT = new Style[SIZES.length];
    private static final Style[] TITLE = new Style[SIZES.length];
    private static float contentScale = 1f;

    static {
        for (int i = 0; i < SIZES.length; i++) {
            TEXT[i] = Style.EMPTY.withFont(BeyondSolo.id("text_" + SIZES[i]));
            TITLE[i] = Style.EMPTY.withFont(BeyondSolo.id("title_" + SIZES[i]));
        }
    }

    /** A measured piece of text, ready to draw. */
    public record Line(FormattedCharSequence text, float width, float size, float scale, boolean vanilla) {}

    /**
     * Call before drawing inside a pose that is scaled by {@code scale} (and again with the returned
     * value afterwards), so the font file that matches the real on-screen size gets picked.
     */
    public static float setContentScale(float scale) {
        float previous = contentScale;
        contentScale = scale;
        return previous;
    }

    // ---------------------------------------------------------------- drawing

    public static void text(GuiGraphics g, String s, float x, float y, float size, int color) {
        draw(g, line(s, size, false), x, y, color, 0f);
    }

    public static void text(GuiGraphics g, Component c, float x, float y, float size, int color) {
        draw(g, line(c, size, false), x, y, color, 0f);
    }

    public static void title(GuiGraphics g, String s, float x, float y, float size, int color) {
        draw(g, line(s, size, true), x, y, color, 0f);
    }

    public static void title(GuiGraphics g, Component c, float x, float y, float size, int color) {
        draw(g, line(c, size, true), x, y, color, 0f);
    }

    /** {@code align}: 0 = x is the left edge, 0.5 = x is the centre, 1 = x is the right edge. */
    public static void draw(GuiGraphics g, String s, float x, float y, float size, int color, boolean bold, float align) {
        draw(g, line(s, size, bold), x, y, color, align);
    }

    public static void draw(GuiGraphics g, Component c, float x, float y, float size, int color, boolean bold, float align) {
        draw(g, line(c, size, bold), x, y, color, align);
    }

    public static void draw(GuiGraphics g, Line line, float x, float y, int color, float align) {
        float alpha = (color >>> 24) / 255f * Gfx.alpha();
        // Minecraft treats nearly transparent text colours as opaque and its text shader drops faint pixels,
        // so very faint text is simply not drawn.
        if (alpha < 0.07f || line.width() <= 0) return;
        int argb = Gfx.alpha(color, alpha);
        Font font = Minecraft.getInstance().font;
        float left = x - line.width() * align;
        float baseline = y + CAP * line.size();

        Matrix4f m = g.pose().last().pose();
        // Snap to whole screen pixels when the pose is unscaled, so glyphs stay crisp.
        if (Math.abs(m.m00() - 1f) < 1e-3f && Math.abs(m.m11() - 1f) < 1e-3f && Math.abs(m.m01()) < 1e-4f) {
            float gui = (float) Minecraft.getInstance().getWindow().getGuiScale();
            left = (Math.round((m.m30() + left) * gui) / gui) - m.m30();
            baseline = (Math.round((m.m31() + baseline) * gui) / gui) - m.m31();
        }

        g.pose().pushPose();
        g.pose().translate(left, baseline, 0);
        g.pose().scale(line.scale(), line.scale(), 1f);
        // Both the TTF fonts and the vanilla font put the baseline 7 units below the draw position.
        g.drawString(font, line.text(), 0f, -7f, argb, false);
        g.pose().popPose();
    }

    /** Draws letter-spaced text ("S Y S T E M"). {@code tracking} is the extra gap between letters in GUI pixels. */
    public static void spaced(GuiGraphics g, String s, float x, float y, float size, int color, boolean bold, float tracking, float align) {
        float total = spacedWidth(s, size, bold, tracking);
        float cursor = x - total * align;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            Line ch = line(Character.toString(cp), size, bold);
            draw(g, ch, cursor, y, color, 0f);
            cursor += ch.width() + tracking;
            i += Character.charCount(cp);
        }
    }

    public static float spacedWidth(String s, float size, boolean bold, float tracking) {
        float total = 0;
        int count = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            total += line(Character.toString(cp), size, bold).width();
            count++;
            i += Character.charCount(cp);
        }
        return total + Math.max(0, count - 1) * tracking;
    }

    // ---------------------------------------------------------------- measuring

    public static float width(String s, float size, boolean bold) {
        return line(s, size, bold).width();
    }

    public static float width(Component c, float size, boolean bold) {
        return line(c, size, bold).width();
    }

    /** The y to pass so a line of this size sits vertically centred in a box. */
    public static float centerY(float boxY, float boxHeight, float size) {
        return boxY + (boxHeight - CAP * size) / 2f;
    }

    public static Line line(String s, float size, boolean bold) {
        Font font = Minecraft.getInstance().font;
        if (useVanilla(s)) {
            FormattedCharSequence seq = FormattedCharSequence.forward(s, Style.EMPTY);
            float scale = size / 10f;
            return new Line(seq, font.getSplitter().stringWidth(seq) * scale, size, scale, true);
        }
        int i = variant(size);
        FormattedCharSequence seq = FormattedCharSequence.forward(s, (bold ? TITLE : TEXT)[i]);
        float scale = size / SIZES[i];
        return new Line(seq, font.getSplitter().stringWidth(seq) * scale, size, scale, false);
    }

    public static Line line(Component c, float size, boolean bold) {
        Font font = Minecraft.getInstance().font;
        if (useVanilla(c.getString())) {
            FormattedCharSequence seq = c.getVisualOrderText();
            float scale = size / 10f;
            return new Line(seq, font.getSplitter().stringWidth(seq) * scale, size, scale, true);
        }
        int i = variant(size);
        FormattedCharSequence seq = c.copy().withStyle((bold ? TITLE : TEXT)[i]).getVisualOrderText();
        float scale = size / SIZES[i];
        return new Line(seq, font.getSplitter().stringWidth(seq) * scale, size, scale, false);
    }

    /** Word-wraps to {@code maxWidth} GUI pixels. */
    public static List<Line> wrap(Component c, float size, boolean bold, float maxWidth) {
        Font font = Minecraft.getInstance().font;
        boolean vanilla = useVanilla(c.getString());
        FormattedText styled;
        float scale;
        if (vanilla) {
            styled = c;
            scale = size / 10f;
        } else {
            int i = variant(size);
            styled = c.copy().withStyle((bold ? TITLE : TEXT)[i]);
            scale = size / SIZES[i];
        }
        float s = scale;
        return font.split(styled, Math.max(1, (int) (maxWidth / scale))).stream()
                .map(seq -> new Line(seq, font.getSplitter().stringWidth(seq) * s, size, s, vanilla))
                .toList();
    }

    /** Number of characters in a line. */
    public static int length(Line line) {
        int[] count = {0};
        line.text().accept((index, style, codePoint) -> {
            count[0]++;
            return true;
        });
        return count[0];
    }

    /** The first {@code chars} characters of a line, for typewriter effects. Meant for left-aligned text. */
    public static Line truncate(Line line, int chars) {
        FormattedCharSequence full = line.text();
        FormattedCharSequence cut = sink -> {
            int[] count = {0};
            return full.accept((index, style, codePoint) -> count[0]++ < chars && sink.accept(index, style, codePoint));
        };
        return new Line(cut, line.width(), line.size(), line.scale(), line.vanilla());
    }

    /** Shortens text with "..." so it fits {@code maxWidth}. */
    public static String fit(String s, float size, boolean bold, float maxWidth) {
        if (width(s, size, bold) <= maxWidth) return s;
        String dots = "...";
        int end = s.length();
        while (end > 0 && width(s.substring(0, end) + dots, size, bold) > maxWidth) end--;
        return s.substring(0, end) + dots;
    }

    // ---------------------------------------------------------------- internals

    private static int variant(float size) {
        double target = size * contentScale * Minecraft.getInstance().getWindow().getGuiScale();
        int best = 0;
        double bestDiff = Double.MAX_VALUE;
        for (int i = 0; i < SIZES.length; i++) {
            double diff = Math.abs(SIZES[i] - target);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = i;
            }
        }
        return best;
    }

    private static boolean useVanilla(String s) {
        if (!ClientConfig.SYSTEM_FONT.get()) return true;
        for (int i = 0; i < s.length(); i++) {
            if (!supported(s.charAt(i))) return true;
        }
        return false;
    }

    /** The characters kept in the font subset (see UNICODES in tools/gen_fonts.py). */
    private static boolean supported(char c) {
        if (c >= 0x20 && c <= 0x7E) return true;
        if (c >= 0xA0 && c <= 0xFF) return true;
        return switch (c) {
            case 0x131, 0x152, 0x153, 0x2013, 0x2014, 0x2018, 0x2019, 0x201A, 0x201C, 0x201D, 0x201E, 0x2022, 0x2026, 0x2122, 0x2212 -> true;
            default -> false;
        };
    }

    private SysFont() {}
}
