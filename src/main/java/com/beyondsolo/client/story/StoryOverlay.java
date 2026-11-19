package com.beyondsolo.client.story;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.job.CutsceneCamera;
import com.beyondsolo.network.StoryLinePayload;
import com.beyondsolo.network.StorySkipPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A story scene on the hunter's screen ({@code story.StoryScenes}): its lines at the bottom, typed out one after the
 * other (who speaks over their words in their colour, the hunter's own thoughts in italics, what happens told
 * quietly, the System in one of its own windows), a dip to black now and then, and while a held scene plays, bars closing in and the HUD out of the way
 * ({@code client.job.JobClientEvents#hideHud}). The hide key skips it.
 */
public final class StoryOverlay {
    /** Characters typed a second. */
    private static final float TYPING = 46f;
    private static final int THOUGHT_COLOR = 0xFFCFE2FF;
    private static final int NARRATION_COLOR = 0xFFE8E2D8;

    private record Shown(int style, Component speaker, Component text, int color, double at, float seconds) {}

    private static boolean active;
    private static boolean cinematic;
    private static double began = -100;
    /** 0..1: how far the scene's own bars are in. */
    private static float bars;
    @Nullable private static Shown line;
    @Nullable private static Shown leaving;
    private static double leftAt;
    private static double blinkAt = -100;
    private static float blinkSeconds;

    public static void onLine(StoryLinePayload p) {
        double now = Anim.now();
        switch (p.mode()) {
            case StoryLinePayload.BEGIN -> {
                active = true;
                cinematic = p.style() == 1;
                began = now;
                leave(now);
            }
            case StoryLinePayload.END -> {
                active = false;
                cinematic = false;
                leave(now);
            }
            case StoryLinePayload.BLINK -> {
                blinkAt = now;
                blinkSeconds = Math.max(0.1f, p.ticks() / 20f);
            }
            default -> {
                leave(now);
                line = new Shown(p.style(), p.speaker(), p.text(), p.color(), now, p.ticks() / 20f);
            }
        }
    }

    private static void leave(double now) {
        if (line != null) {
            leaving = line;
            leftAt = now;
        }
        line = null;
    }

    /** A scene is playing. */
    public static boolean active() {
        return active;
    }

    /** A held scene is playing: bars in, HUD out of the way. */
    public static boolean cinematic() {
        return active && cinematic;
    }

    /** The hide key: skips the scene (the server still does what it changes). */
    public static boolean skip() {
        if (!active) return false;
        PacketDistributor.sendToServer(StorySkipPayload.INSTANCE);
        return true;
    }

    public static void reset() {
        active = false;
        cinematic = false;
        line = null;
        leaving = null;
        bars = 0;
        blinkAt = -100;
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double now = Anim.now();
        int w = g.guiWidth(), h = g.guiHeight();
        bars = Anim.approach(bars, cinematic() ? 1 : 0, 3.5f);
        boolean blinking = now - blinkAt < blinkSeconds;
        if (bars <= 0.002f && line == null && leaving == null && !blinking && !active) return;

        // the scene's own bars, wherever the camera's are not in already
        float cam = Anim.inOutCubic(CutsceneCamera.bars());
        float b = Anim.inOutCubic(bars);
        if (b > cam + 0.002f) {
            float bh = h * 0.11f * b;
            Shapes.box(0, 0, w, bh).fill(0xFF000000).draw(g);
            Shapes.box(0, h - bh, w, bh).fill(0xFF000000).draw(g);
        }
        float barH = h * 0.11f * Math.max(b, cam);
        // the lines sit just over the lower bar, or over the hotbar when nothing holds the screen
        float bottom = barH > 4 ? h - barH - 9 : h - 64;

        if (leaving != null) {
            float a = 1 - Anim.clamp01((float) (now - leftAt) / 0.25f);
            if (a <= 0) leaving = null;
            else drawLine(g, leaving, w, bottom, a, now, true);
        }
        if (line != null) {
            float age = (float) (now - line.at());
            float a = Anim.clamp01(age / 0.2f);
            // a line nothing followed (the scene's end lost on the way) still goes in the end
            float over = age - (line.seconds() * 1.5f + 1f);
            if (over > 0) a *= 1 - Anim.clamp01(over / 0.4f);
            if (over > 0.4f) line = null;
            else drawLine(g, line, w, bottom, a, now, false);
        }

        if (active && now - began > 0.8) {
            float a = Anim.clamp01((float) (now - began - 0.8) / 0.5f);
            String hint = Component.translatable("story.beyondsolo.skip", Component.keybind("key.beyondsolo.dismiss")).getString();
            float y = barH > 14 ? h - barH / 2f - 2.5f : h - 12;
            SysFont.draw(g, hint, w - 10, y, 6.5f, Gfx.alpha(0xFFFFFFFF, 0.55f * a), false, 1f);
        }

        if (blinking) {
            float t = (float) (now - blinkAt) / blinkSeconds;
            float a = t < 0.35f ? t / 0.35f : t > 0.65f ? (1 - t) / 0.35f : 1;
            Shapes.box(0, 0, w, h).fill(Gfx.alpha(0xFF000000, Anim.clamp01(a))).draw(g);
        }
    }

    private static void drawLine(GuiGraphics g, Shown s, int w, float bottom, float a, double now, boolean done) {
        boolean speech = s.style() == StoryLinePayload.SPEECH;
        boolean thought = s.style() == StoryLinePayload.THOUGHT;
        boolean system = s.style() == StoryLinePayload.SYSTEM;
        float size = s.style() == StoryLinePayload.NARRATION ? 8.5f : 9.5f;
        float maxW = Math.min(w * 0.64f, 400);
        Component text = thought ? s.text().copy().withStyle(st -> st.withItalic(true)) : s.text();
        List<SysFont.Line> lines = SysFont.wrap(text, size, false, maxW);
        if (lines.isEmpty()) return;
        float lh = size * SysFont.LINE;
        float textH = (lines.size() - 1) * lh + size * SysFont.CAP;
        float nameH = speech || system ? 10 : 0;
        float widest = 0;
        for (SysFont.Line l : lines) widest = Math.max(widest, l.width());
        float cx = w / 2f;
        float panelW = Math.max(widest + 34, 150);
        float panelH = textH + nameH + 16;
        float top = bottom - panelH;
        float old = Gfx.fade(a);

        int color = speech ? Gfx.mix(s.color(), 0xFFFFFFFF, 0.82f) : system ? SystemTheme.TEXT : thought ? THOUGHT_COLOR : NARRATION_COLOR;
        if (system) {
            // one of the System's own windows: blue glass, a flash and a shine as it opens
            int accent = SystemTheme.ACCENT;
            float flash = done ? 0 : 1 - Anim.outCubic(Anim.clamp01((float) (now - s.at()) / 0.8f));
            float sweep = done ? 1 : Anim.clamp01((float) (now - s.at() - 0.06) / 0.7f);
            Shapes.box(cx - panelW / 2f, top, panelW, panelH).radius(4)
                    .fillV(Gfx.mix(SystemTheme.GLASS_TOP, Gfx.alpha(accent, 0.95f), 0.08f + 0.12f * flash), SystemTheme.GLASS_BOTTOM)
                    .border(1f, Gfx.mix(accent, 0xFFFFFFFF, 0.15f + 0.5f * flash), Gfx.fade(accent, 0.3f))
                    .glow(9 + 7 * flash, Gfx.alpha(accent, 0.22f + 0.35f * flash))
                    .sheen(sweep * 1.5f - 0.25f, sweep < 1 ? 0.16f * (1 - sweep) + 0.04f : 0f)
                    .draw(g);
            String who = s.speaker().getString().toUpperCase(java.util.Locale.ROOT);
            float ww = SysFont.spacedWidth(who, 6.2f, true, 1.3f);
            float x0 = cx - (ww + 11) / 2f;
            float ny = top + 7;
            Ui.iconBadge(g, Icons.EXCLAIM, x0 + 4.5f, ny + 2.2f, 4.5f, accent);
            SysFont.spaced(g, who, x0 + 11, ny, 6.2f, Gfx.mix(accent, 0xFFFFFFFF, 0.25f), true, 1.3f, 0f);
        } else {
            // a soft dark panel the words stand on, whatever is behind
            Shapes.box(cx - panelW / 2f, top, panelW, panelH).radius(9).fill(Gfx.alpha(0xFF05070C, 0.52f))
                    .glow(16, Gfx.alpha(0xFF05070C, 0.32f)).draw(g);
        }
        if (speech) {
            String who = s.speaker().getString().toUpperCase(java.util.Locale.ROOT);
            float ww = SysFont.spacedWidth(who, 6.2f, true, 1f);
            float ny = top + 7;
            SysFont.spaced(g, who, cx, ny, 6.2f, Gfx.opaque(s.color()), true, 1f, 0.5f);
            // a small mark either side of the name
            Shapes.box(cx - ww / 2f - 11, ny + 1.7f, 6, 0.8f).fill(Gfx.alpha(s.color(), 0.7f)).draw(g);
            Shapes.box(cx + ww / 2f + 5, ny + 1.7f, 6, 0.8f).fill(Gfx.alpha(s.color(), 0.7f)).draw(g);
        } else if (!thought && !system) {
            // told quietly: thin rules beside it
            float my = top + panelH / 2f;
            Shapes.box(cx - panelW / 2f + 6, my, 9, 0.7f).fill(Gfx.alpha(NARRATION_COLOR, 0.45f)).draw(g);
            Shapes.box(cx + panelW / 2f - 15, my, 9, 0.7f).fill(Gfx.alpha(NARRATION_COLOR, 0.45f)).draw(g);
        }

        // typed out
        int left = done ? Integer.MAX_VALUE : (int) ((now - s.at()) * TYPING);
        float y = top + 8 + nameH;
        for (SysFont.Line l : lines) {
            int n = SysFont.length(l);
            if (left <= 0) break;
            SysFont.draw(g, left >= n ? l : SysFont.truncate(l, left), cx, y, color, 0.5f);
            left -= n;
            y += lh;
        }
        Gfx.setAlpha(old);
    }

    private StoryOverlay() {}
}
