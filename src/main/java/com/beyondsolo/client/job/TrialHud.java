package com.beyondsolo.client.job;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.network.JobTrialPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The trial's on-screen banner: a big title card when the player arrives, a wave counter with a progress bar,
 * a small line under the boss bar during the Arbiter fight, and a "trial failed" card.
 */
public final class TrialHud {
    private static final int VIOLET = 0xFFB18CFF;
    private static final int VIOLET_DEEP = 0xFF6A3FD0;

    private static int phase = JobTrialPayload.NONE;
    private static Component title = Component.empty();
    private static Component subtitle = Component.empty();
    private static int progress, goal;
    private static float shownProgress;
    /** When the current phase (or title) began, in {@link Anim#now()} seconds. */
    private static double since;
    private static float shown;
    private static String lastTitle = "";

    public static void handle(JobTrialPayload p) {
        String t = p.title().getString();
        if (p.phase() != phase || !t.equals(lastTitle)) since = Anim.now();
        if (p.phase() != JobTrialPayload.NONE || phase != JobTrialPayload.FAILED) phase = p.phase();
        lastTitle = t;
        title = p.title();
        subtitle = p.subtitle();
        progress = p.progress();
        goal = p.goal();
    }

    public static void reset() {
        phase = JobTrialPayload.NONE;
        shown = 0;
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        float age = Anim.since(since);
        if (phase == JobTrialPayload.FAILED && age > 5f) phase = JobTrialPayload.NONE;
        boolean visible = phase != JobTrialPayload.NONE && phase != JobTrialPayload.VERDICT;
        shown = Anim.approach(shown, visible ? 1 : 0, 6f);
        if (shown < 0.01f) return;
        int w = g.guiWidth();
        int h = g.guiHeight();
        float old = Gfx.fade(shown);
        switch (phase) {
            case JobTrialPayload.INTRO -> titleCard(g, w, h, age, VIOLET, VIOLET_DEEP);
            case JobTrialPayload.FAILED -> titleCard(g, w, h, age, SystemTheme.DANGER, 0xFF8A0A18);
            case JobTrialPayload.WAVE -> wave(g, w, age);
            case JobTrialPayload.BOSS -> bossLine(g, w);
            default -> {}
        }
        Gfx.setAlpha(old);
    }

    /** Big centred title with lines drawing out to the sides and the subtitle typed in. */
    private static void titleCard(GuiGraphics g, int w, int h, float age, int accent, int deep) {
        float in = Anim.outCubic(Anim.window(age, 0.2f, 1.0f));
        float out = 1 - Anim.window(age, 4.6f, 5.2f);
        float a = in * out;
        if (a <= 0.01f) return;
        float cy = h * 0.3f;
        Shapes.vignette(0, 0, w, h, 0.45f, 1.25f).fill(Gfx.alpha(deep, 0.35f * a)).draw(g);
        float old = Gfx.fade(a);
        String head = Component.translatable("job.beyondsolo.trial.system").getString();
        SysFont.spaced(g, head, w / 2f, cy - 26, 7f, Gfx.mix(accent, 0xFFFFFFFF, 0.3f), true, 3.2f, 0.5f);
        float size = 22 + 4 * (1 - in);
        String t = title.getString();
        float tw = SysFont.spacedWidth(t, size, true, 2.5f);
        SysFont.spaced(g, t, w / 2f, cy - 12, size, 0xFFFFFFFF, true, 2.5f, 0.5f);
        float line = (w * 0.36f) * Anim.outCubic(Anim.window(age, 0.5f, 1.5f));
        Shapes.box(w / 2f - tw / 2f - 10 - line, cy - 1.5f, line, 1).fillH(Gfx.alpha(accent, 0), accent).glow(3, Gfx.alpha(accent, 0.5f)).draw(g);
        Shapes.box(w / 2f + tw / 2f + 10, cy - 1.5f, line, 1).fillH(accent, Gfx.alpha(accent, 0)).glow(3, Gfx.alpha(accent, 0.5f)).draw(g);
        Shapes.icon(Icons.SEAL, w / 2f - tw / 2f - 6, cy - 1, 7).fill(accent).glow(3, Gfx.alpha(accent, 0.6f)).rotation(Anim.loop(6f)).draw(g);
        Shapes.icon(Icons.SEAL, w / 2f + tw / 2f + 6, cy - 1, 7).fill(accent).glow(3, Gfx.alpha(accent, 0.6f)).rotation(-Anim.loop(6f)).draw(g);
        SysFont.Line sub = SysFont.line(subtitle.getString(), 9f, false);
        int chars = (int) Math.min(SysFont.length(sub), Math.max(0, age - 1.1f) * 40f);
        SysFont.draw(g, SysFont.truncate(sub, chars), w / 2f, cy + 14, SystemTheme.TEXT, 0.5f);
        Gfx.setAlpha(old);
    }

    /** "Wave 2 / 3" with a bar of defeated echoes, at the top of the screen. */
    private static void wave(GuiGraphics g, int w, float age) {
        float in = Anim.outBack(Anim.window(age, 0, 0.45f));
        float pw = 200, ph = 32;
        float x = (w - pw) / 2f, y = 6 - 10 * (1 - in);
        Shapes.box(x, y, pw, ph).radius(9).fillV(SystemTheme.GLASS_TOP, SystemTheme.GLASS_BOTTOM)
                .border(0.75f, Gfx.fade(VIOLET, 0.9f), Gfx.fade(VIOLET, 0.25f)).glow(8, Gfx.alpha(VIOLET_DEEP, 0.3f)).draw(g);
        Shapes.icon(Icons.SKULL, x + 13, y + ph / 2f, 12).fill(VIOLET).glow(3, Gfx.alpha(VIOLET, 0.5f)).draw(g);
        SysFont.spaced(g, title.getString(), x + 24, y + 5, 8.5f, 0xFFFFFFFF, true, 1.5f, 0f);
        String count = progress + " / " + goal;
        SysFont.draw(g, count, x + pw - 8, y + 6, 7.5f, SystemTheme.TEXT_SOFT, true, 1f);
        SysFont.text(g, SysFont.fit(subtitle.getString(), 6.5f, false, pw - 34), x + 24, y + 15.5f, 6.5f, SystemTheme.TEXT_DIM);
        float frac = goal <= 0 ? 0 : Mth.clamp(progress / (float) goal, 0, 1);
        shownProgress = Anim.approach(shownProgress, frac, 6f);
        float bx = x + 24, bw = pw - 32, by = y + ph - 7;
        Shapes.pill(bx, by, bw, 3.5f).fill(SystemTheme.TRACK).draw(g);
        if (shownProgress > 0.01f) {
            Shapes.pill(bx, by, Math.max(3.5f, bw * shownProgress), 3.5f).fillH(VIOLET_DEEP, VIOLET).glow(3, Gfx.alpha(VIOLET, 0.6f))
                    .sheen(Anim.loop(2.2f) * 1.6f - 0.3f, 0.25f).draw(g);
        }
    }

    /** A short line under the boss bar: whether the fight is still flawless. */
    private static void bossLine(GuiGraphics g, int w) {
        String s = subtitle.getString();
        if (s.isEmpty()) return;
        float tw = SysFont.width(s, 6.5f, false) + 16;
        float y = 38;
        Shapes.pill((w - tw) / 2f, y, tw, 11).fill(0xB00A1222).border(0.6f, Gfx.alpha(VIOLET, 0.7f)).draw(g);
        SysFont.draw(g, s, w / 2f, y + 2.6f, 6.5f, VIOLET, false, 0.5f);
    }

    private TrialHud() {}
}
