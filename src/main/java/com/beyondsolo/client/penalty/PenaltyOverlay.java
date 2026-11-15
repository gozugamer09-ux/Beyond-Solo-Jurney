package com.beyondsolo.client.penalty;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.job.CutsceneCamera;
import com.beyondsolo.network.PenaltyPayload;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.List;

/**
 * Everything the Penalty Zone draws on screen:
 * <ul>
 *   <li><b>Warning</b>: the screen's edges throb red with a heartbeat, a red System window glitches in ("WARNING:
 *   penalty quest"), what was failed and what comes next type out, and a ring counts the seconds down. In the last
 *   second the screen cracks from the middle and drops to black.</li>
 *   <li><b>Arrival</b>: out of the black as the camera falls over the Wastes, then the "PENALTY ZONE" title card.</li>
 *   <li><b>Survival</b>: the timer at the top (hourglass, time left, a draining bar, creatures slain), banners for
 *   surges, the final stretch, eruptions and the edge, and a red heartbeat in the last ten seconds.</li>
 *   <li><b>The end</b>: survived, a white flash and the "PENALTY COMPLETE" seal stamping down, then light takes the
 *   screen; fallen, a red glitching "PENALTY FAILED" and a fall to black. Either fades out once the player is home.</li>
 * </ul>
 */
public final class PenaltyOverlay {
    static final int RED = 0xFFFF3B30;
    static final int RED_SOFT = 0xFFFF6A5E;
    static final int RED_DEEP = 0xFF7A0A0A;
    static final int EMBER = 0xFFFF8A4C;
    static final int GLASS_TOP = 0xF22A0709;
    static final int GLASS_BOTTOM = 0xF2120305;
    static final int PALE = 0xFFFFE8D6;

    private static float shownTimer;
    private static float shownBar = 1;

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int w = g.guiWidth(), h = g.guiHeight();
        float partial = delta.getGameTimeDeltaPartialTick(true);
        float age = PenaltyClient.phaseAge();
        switch (PenaltyClient.phase) {
            case PenaltyPayload.WARNING -> warning(g, w, h, age);
            case PenaltyPayload.ARRIVAL -> arrival(g, w, h, age);
            case PenaltyPayload.SURVIVE -> survive(g, w, h, age, partial, mc.options.hideGui);
            case PenaltyPayload.SURVIVED -> survived(g, w, h, age);
            case PenaltyPayload.FAILED -> failed(g, w, h, age);
            default -> afterglow(g, w, h);
        }
        if (PenaltyClient.phase != PenaltyPayload.SURVIVE) {
            shownTimer = Anim.approach(shownTimer, 0, 6f);
        }
    }

    // ================================================================ warning

    private static void warning(GuiGraphics g, int w, int h, float age) {
        float total = Math.max(1, PenaltyClient.length / 20f);
        float left = Math.max(0, total - age);
        float k = Mth.clamp(age / total, 0, 1);

        // the edges throb with a heartbeat that quickens
        float rate = 1.1f + 1.6f * k;
        float beat = heartbeat(age * rate);
        Shapes.vignette(0, 0, w, h, 0.42f - 0.12f * k, 1.2f).fill(Gfx.alpha(RED_DEEP, 0.35f + 0.35f * beat + 0.2f * k)).draw(g);
        Shapes.box(2, 2, w - 4, h - 4).radius(6).fill(0).border(1.2f, Gfx.alpha(RED, 0.25f + 0.5f * beat)).glow(10, Gfx.alpha(RED, 0.25f * beat)).draw(g);

        // the window glitches in
        float in = Anim.outBack(Anim.window(age, 0.05f, 0.55f));
        float pw = 250, ph = 98;
        float x = (w - pw) / 2f, y = h * 0.22f;
        float glitch = age < 0.6f ? (1 - age / 0.6f) : (left < 1.3f ? 1 - left / 1.3f : 0);
        RandomSource r = RandomSource.create((long) (Anim.now() * 30));
        float jx = glitch > 0 ? (r.nextFloat() - 0.5f) * 10 * glitch : 0;
        float old = Gfx.fade(Mth.clamp(in * 1.4f, 0, 1));
        g.pose().pushPose();
        g.pose().translate(w / 2f + jx, y + ph / 2f, 0);
        float sc = 0.85f + 0.15f * in;
        g.pose().scale(sc, sc, 1);
        g.pose().translate(-w / 2f, -(y + ph / 2f), 0);
        if (glitch > 0.15f) {
            // colour-split ghosts of the frame
            Shapes.box(x - 3 * glitch, y, pw, ph).radius(8).fill(0).border(1, Gfx.alpha(0xFF30E0FF, 0.35f * glitch)).draw(g);
            Shapes.box(x + 3 * glitch, y, pw, ph).radius(8).fill(0).border(1, Gfx.alpha(RED, 0.5f * glitch)).draw(g);
        }
        Shapes.box(x, y, pw, ph).radius(8).fillV(GLASS_TOP, GLASS_BOTTOM)
                .border(1f, Gfx.fade(RED, 0.95f), Gfx.fade(RED, 0.3f)).glow(10, Gfx.alpha(RED, 0.25f + 0.25f * beat)).draw(g);
        // header
        float hy = y + 10;
        Shapes.circle(x + 18, hy + 5, 8).fill(Gfx.alpha(RED, 0.25f)).border(0.8f, RED).glow(4 + 4 * beat, Gfx.alpha(RED, 0.6f)).draw(g);
        Shapes.icon(Icons.EXCLAIM, x + 18, hy + 5, 10).fill(PALE).draw(g);
        SysFont.spaced(g, Component.translatable("penalty.beyondsolo.warning").getString(), x + 32, hy - 1, 11f, 0xFFFFFFFF, true, 2.6f, 0f);
        SysFont.spaced(g, Component.translatable("penalty.beyondsolo.penalty_quest").getString(), x + pw - 10, hy + 1.5f, 6.5f, RED_SOFT, true, 1.6f, 1f);
        float line = (pw - 20) * Anim.outCubic(Anim.window(age, 0.3f, 0.9f));
        Shapes.box(x + 10, hy + 17, line, 0.8f).fillH(RED, Gfx.alpha(RED, 0.05f)).glow(2, Gfx.alpha(RED, 0.5f)).draw(g);
        // body lines type in one after the other
        String failed = Component.translatable("penalty.beyondsolo.warning.failed", PenaltyClient.reason).getString();
        String moved = Component.translatable("penalty.beyondsolo.warning.moved").getString();
        String goal = Component.translatable("penalty.beyondsolo.warning.goal", clock(PenaltyClient.remaining)).getString();
        float ty = hy + 24;
        typed(g, failed, x + 12, ty, 7.5f, SystemTheme.TEXT, age, 0.7f);
        typed(g, moved, x + 12, ty + 12, 7.5f, SystemTheme.TEXT_SOFT, age, 1.5f);
        typed(g, goal, x + 12, ty + 26, 8f, EMBER, age, 2.4f);
        Gfx.setAlpha(old);
        g.pose().popPose();

        // the countdown ring under the window
        float ring = Anim.outBack(Anim.window(age, 0.6f, 1.1f));
        if (ring > 0.01f) {
            float rcx = w / 2f, rcy = y + ph + 34;
            int sec = (int) Math.ceil(left);
            float sub = left - (float) Math.floor(left);
            float pop = sub > 0.82f ? (sub - 0.82f) / 0.18f : 0;
            float o2 = Gfx.fade(ring);
            Shapes.circle(rcx, rcy, 21).fill(0xC0100304).glow(10, Gfx.alpha(RED, 0.25f + 0.4f * beat)).draw(g);
            Shapes.ring(rcx, rcy, 19, 2.2f).fill(Gfx.alpha(RED, 0.18f)).draw(g);
            Shapes.ring(rcx, rcy, 19, 2.2f).fill(RED).progress(left / total).glow(4, Gfx.alpha(RED, 0.7f)).draw(g);
            Shapes.ring(rcx, rcy, 24 + 6 * pop, 0.8f).fill(Gfx.alpha(RED, 0.6f * pop)).draw(g);
            float size = 17 + 6 * pop;
            SysFont.draw(g, String.valueOf(Math.max(0, sec)), rcx, rcy - size * SysFont.CAP / 2f - 1, size, 0xFFFFFFFF, true, 0.5f);
            Gfx.setAlpha(o2);
        }

        // the last second: the screen cracks, then black
        if (left < 1.1f) cracks(g, w, h, Mth.clamp((1.1f - left) / 0.8f, 0, 1));
        float black = Mth.clamp((0.45f - left) / 0.4f, 0, 1);
        if (black > 0) g.fill(0, 0, w, h, Gfx.alpha(0xFF000000, black));
    }

    /** A heartbeat: two quick thumps per period, then rest. */
    private static float heartbeat(float t) {
        float f = t - (float) Math.floor(t);
        float a = Math.max(0, 1 - Math.abs(f - 0.08f) / 0.08f);
        float b = Math.max(0, 1 - Math.abs(f - 0.28f) / 0.1f) * 0.7f;
        return Math.max(a, b);
    }

    /** Jagged cracks running out from the middle of the screen. */
    private static void cracks(GuiGraphics g, int w, int h, float k) {
        RandomSource r = RandomSource.create(7331);
        float cx = w / 2f, cy = h / 2f;
        float reach = Math.max(w, h) * 0.75f * Anim.outCubic(k);
        for (int i = 0; i < 11; i++) {
            float ang = (float) (i / 11.0 * Math.PI * 2 + r.nextFloat() * 0.4);
            float x = cx, y = cy;
            float walked = 0;
            float seg = 18 + r.nextFloat() * 22;
            while (walked < reach) {
                float a = ang + (r.nextFloat() - 0.5f) * 0.9f;
                float len = Math.min(seg, reach - walked);
                float nx = x + Mth.cos(a) * len, ny = y + Mth.sin(a) * len;
                float thick = 1.6f * (1 - walked / (Math.max(w, h) * 0.8f)) + 0.4f;
                line(g, x, y, nx, ny, thick + 2.5f, Gfx.alpha(RED, 0.35f));
                line(g, x, y, nx, ny, thick, Gfx.alpha(PALE, 0.95f));
                x = nx;
                y = ny;
                walked += len;
                seg = 14 + r.nextFloat() * 26;
                if (r.nextFloat() < 0.18f) ang += (r.nextFloat() - 0.5f) * 1.4f;
            }
        }
        Shapes.circle(cx, cy, 30 + 60 * k).fill(Gfx.alpha(0xFFFFFFFF, 0)).glow(30, Gfx.alpha(RED, 0.5f * k)).draw(g);
    }

    private static void line(GuiGraphics g, float x0, float y0, float x1, float y1, float thick, int color) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f) return;
        g.pose().pushPose();
        g.pose().translate(x0, y0, 0);
        g.pose().mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
        Shapes.box(0, -thick / 2f, len, thick).radius(thick / 2f).fill(color).draw(g);
        g.pose().popPose();
    }

    // ================================================================ arrival

    private static void arrival(GuiGraphics g, int w, int h, float age) {
        // black until the camera has the Wastes on screen, then it lifts
        float curtain = CutsceneCamera.curtain();
        if (curtain > 0.5f && age < 6) PenaltyClient.phaseStart = Anim.now() - Math.min(age, 0.2f);
        float lift = 1 - Anim.window(age, 0.2f, 1.2f);
        if (lift > 0) g.fill(0, 0, w, h, Gfx.alpha(0xFF000000, lift));
        // the edges stay stained red as the dust settles
        Shapes.vignette(0, 0, w, h, 0.5f, 1.3f).fill(Gfx.alpha(RED_DEEP, 0.4f * (1 - Anim.window(age, 1, 5)))).draw(g);
        titleCard(g, w, h, age - 1.4f, Component.translatable("penalty.beyondsolo.zone").getString(),
                Component.translatable("penalty.beyondsolo.zone.sub").getString(), RED, RED_DEEP);
    }

    /** Big centred title with lines drawing out to the sides and the subtitle typed in. */
    private static void titleCard(GuiGraphics g, int w, int h, float age, String title, String subtitle, int accent, int deep) {
        if (age <= 0) return;
        float in = Anim.outCubic(Anim.window(age, 0, 0.8f));
        float out = 1 - Anim.window(age, 4.6f, 5.4f);
        float a = in * out;
        if (a <= 0.01f) return;
        float cy = h * 0.3f;
        float old = Gfx.fade(a);
        String head = Component.translatable("penalty.beyondsolo.system").getString();
        SysFont.spaced(g, head, w / 2f, cy - 28, 7f, Gfx.mix(accent, 0xFFFFFFFF, 0.3f), true, 3.2f, 0.5f);
        float size = 24 + 6 * (1 - in);
        float tw = SysFont.spacedWidth(title, size, true, 3f);
        // a red smear behind the title
        Shapes.box(w / 2f - tw / 2f - 30, cy - 16, tw + 60, 30).radius(15).fill(Gfx.alpha(deep, 0.45f)).glow(18, Gfx.alpha(deep, 0.35f)).draw(g);
        SysFont.spaced(g, title, w / 2f, cy - 13, size, 0xFFFFFFFF, true, 3f, 0.5f);
        float line = (w * 0.34f) * Anim.outCubic(Anim.window(age, 0.3f, 1.3f));
        Shapes.box(w / 2f - tw / 2f - 12 - line, cy - 1.5f, line, 1).fillH(Gfx.alpha(accent, 0), accent).glow(3, Gfx.alpha(accent, 0.5f)).draw(g);
        Shapes.box(w / 2f + tw / 2f + 12, cy - 1.5f, line, 1).fillH(accent, Gfx.alpha(accent, 0)).glow(3, Gfx.alpha(accent, 0.5f)).draw(g);
        Shapes.icon(Icons.SKULL, w / 2f - tw / 2f - 7, cy - 1, 8).fill(accent).glow(3, Gfx.alpha(accent, 0.6f)).draw(g);
        Shapes.icon(Icons.SKULL, w / 2f + tw / 2f + 7, cy - 1, 8).fill(accent).glow(3, Gfx.alpha(accent, 0.6f)).draw(g);
        SysFont.Line sub = SysFont.line(subtitle, 9f, false);
        int chars = (int) Math.min(SysFont.length(sub), Math.max(0, age - 0.9f) * 40f);
        SysFont.draw(g, SysFont.truncate(sub, chars), w / 2f, cy + 16, SystemTheme.TEXT, 0.5f);
        Gfx.setAlpha(old);
    }

    // ================================================================ survival

    private static void survive(GuiGraphics g, int w, int h, float age, float partial, boolean hideGui) {
        float left = PenaltyClient.remainingNow(partial);
        float leftSec = left / 20f;
        float total = Math.max(1, PenaltyClient.length);
        // last ten seconds: the heart beats on the screen's edges
        if (leftSec <= 10.5f) {
            float beat = heartbeat(Anim.since(PenaltyClient.phaseStart) * 1.6f);
            Shapes.vignette(0, 0, w, h, 0.5f, 1.25f).fill(Gfx.alpha(RED_DEEP, 0.25f + 0.3f * beat)).draw(g);
        }
        // the edge: a red flash
        float edgeAge = Anim.since(PenaltyClient.eventAt);
        if (PenaltyClient.event == PenaltyPayload.EV_EDGE && edgeAge < 1.2f) {
            Shapes.vignette(0, 0, w, h, 0.35f, 1.15f).fill(Gfx.alpha(RED, 0.55f * (1 - edgeAge / 1.2f))).draw(g);
        }
        if (hideGui) return;

        shownTimer = Anim.approach(shownTimer, 1, 5f);
        float in = Anim.outBack(Mth.clamp(shownTimer, 0, 1));
        float pw = 176, ph = 42;
        float x = (w - pw) / 2f, y = 5 - 14 * (1 - in);
        float old = Gfx.fade(Mth.clamp(shownTimer * 1.3f, 0, 1));
        boolean urgent = leftSec <= 10.5f;
        int accent = urgent ? Gfx.mix(RED, 0xFFFFFFFF, 0.25f * heartbeat(Anim.since(PenaltyClient.phaseStart) * 1.6f)) : RED;
        Shapes.box(x, y, pw, ph).radius(10).fillV(GLASS_TOP, GLASS_BOTTOM)
                .border(0.9f, Gfx.fade(accent, 0.95f), Gfx.fade(accent, 0.25f)).glow(9, Gfx.alpha(RED, urgent ? 0.45f : 0.25f)).draw(g);
        // hourglass, turning over each second
        float secFrac = leftSec - (float) Math.floor(leftSec);
        float flip = secFrac > 0.85f ? Anim.inOutCubic((secFrac - 0.85f) / 0.15f) * 0.5f : 0;
        Shapes.circle(x + 18, y + ph / 2f, 11).fill(Gfx.alpha(RED, 0.16f)).border(0.7f, Gfx.alpha(RED, 0.8f)).draw(g);
        Shapes.icon(Icons.HOURGLASS, x + 18, y + ph / 2f, 13).fill(PALE).glow(3, Gfx.alpha(RED, 0.6f)).rotation(flip).draw(g);
        // label and time
        SysFont.spaced(g, Component.translatable("penalty.beyondsolo.zone").getString(), x + 34, y + 6, 6.5f, RED_SOFT, true, 2f, 0f);
        String time = clock(left);
        float pulse = urgent && secFrac > 0.75f ? (secFrac - 0.75f) / 0.25f : 0;
        float size = 17 + 4 * pulse;
        SysFont.draw(g, time, x + 34, y + 14 - 2 * pulse, size, urgent ? Gfx.mix(0xFFFFFFFF, RED_SOFT, 0.5f) : 0xFFFFFFFF, true, 0f);
        // creatures slain
        float popAge = Anim.since(PenaltyClient.killPop);
        float kp = popAge < 0.35f ? 1 - popAge / 0.35f : 0;
        Shapes.icon(Icons.SKULL, x + pw - 34, y + 19, 10 + 3 * kp).fill(Gfx.mix(SystemTheme.TEXT_SOFT, EMBER, kp)).draw(g);
        SysFont.draw(g, String.valueOf(PenaltyClient.kills), x + pw - 26, y + 15, 9f + 2 * kp, 0xFFFFFFFF, true, 0f);
        SysFont.spaced(g, Component.translatable("penalty.beyondsolo.slain").getString(), x + pw - 10, y + 6, 5.5f, SystemTheme.TEXT_DIM, true, 1.4f, 1f);
        // the bar drains
        float frac = Mth.clamp(left / total, 0, 1);
        shownBar = Anim.approach(shownBar, frac, 8f);
        float bx = x + 34, bw = pw - 44, by = y + ph - 8;
        Shapes.pill(bx, by, bw, 3.5f).fill(0x8C1A0406).draw(g);
        if (shownBar > 0.005f) {
            Shapes.pill(bx, by, Math.max(3.5f, bw * shownBar), 3.5f).fillH(RED_DEEP, urgent ? RED : EMBER).glow(3, Gfx.alpha(RED, 0.6f))
                    .sheen(Anim.loop(2.0f) * 1.6f - 0.3f, 0.25f).draw(g);
        }
        Gfx.setAlpha(old);

        banner(g, w, y + ph + 8);
    }

    /** One-off banners under the timer. */
    private static void banner(GuiGraphics g, int w, float y) {
        float age = Anim.since(PenaltyClient.eventAt);
        int ev = PenaltyClient.event;
        float life = switch (ev) {
            case PenaltyPayload.EV_SURGE, PenaltyPayload.EV_FINAL -> 3.4f;
            case PenaltyPayload.EV_ERUPT, PenaltyPayload.EV_EDGE -> 1.8f;
            default -> 0;
        };
        if (age > life || life == 0) return;
        String title, sub;
        int color;
        switch (ev) {
            case PenaltyPayload.EV_SURGE -> {
                title = Component.translatable("penalty.beyondsolo.surge").getString();
                sub = Component.translatable("penalty.beyondsolo.surge.sub").getString();
                color = RED;
            }
            case PenaltyPayload.EV_FINAL -> {
                title = Component.translatable("penalty.beyondsolo.final").getString();
                sub = Component.translatable("penalty.beyondsolo.final.sub").getString();
                color = SystemTheme.GOLD;
            }
            case PenaltyPayload.EV_ERUPT -> {
                title = Component.translatable("penalty.beyondsolo.erupt").getString();
                sub = "";
                color = EMBER;
            }
            default -> {
                title = Component.translatable("penalty.beyondsolo.edge").getString();
                sub = "";
                color = RED_SOFT;
            }
        }
        float in = Anim.outBack(Anim.window(age, 0, 0.35f));
        float out = 1 - Anim.window(age, life - 0.4f, life);
        float a = Mth.clamp(in, 0, 1) * out;
        if (a <= 0.01f) return;
        float old = Gfx.fade(a);
        boolean big = !sub.isEmpty();
        float size = big ? 10f : 7.5f;
        float tw = SysFont.spacedWidth(title, size, true, 2f);
        float bw = Math.max(tw, sub.isEmpty() ? 0 : SysFont.width(sub, 6.5f, false)) + 30;
        float bh = big ? 26 : 15;
        float sx = 0.7f + 0.3f * in;
        g.pose().pushPose();
        g.pose().translate(w / 2f, y + bh / 2f, 0);
        g.pose().scale(sx, 1, 1);
        g.pose().translate(-w / 2f, -(y + bh / 2f), 0);
        Shapes.box((w - bw) / 2f, y, bw, bh).radius(bh / 2f).fill(0xD0140406).border(0.8f, Gfx.alpha(color, 0.9f)).glow(8, Gfx.alpha(color, 0.35f)).draw(g);
        g.pose().popPose();
        SysFont.spaced(g, title, w / 2f, y + (big ? 4 : 3.5f), size, Gfx.mix(color, 0xFFFFFFFF, 0.35f), true, 2f, 0.5f);
        if (big) SysFont.draw(g, sub, w / 2f, y + 16, 6.5f, SystemTheme.TEXT_SOFT, false, 0.5f);
        Gfx.setAlpha(old);
    }

    // ================================================================ the end

    private static void survived(GuiGraphics g, int w, int h, float age) {
        float flash = 1 - Anim.window(age, 0, 0.6f);
        if (flash > 0) g.fill(0, 0, w, h, Gfx.alpha(0xFFFFF4E8, 0.85f * flash));
        float cy = h * 0.34f;
        // the seal stamps down
        float st = Anim.window(age, 0.25f, 0.75f);
        if (st > 0) {
            float s = 2.4f - 1.4f * Anim.outBack(st);
            float a = Mth.clamp(st * 2, 0, 1) * (1 - Anim.window(age, 4.2f, 4.8f));
            float old = Gfx.fade(a);
            float land = Anim.window(age, 0.7f, 1.5f);
            if (land > 0 && land < 1) {
                Shapes.ring(w / 2f, cy, 30 + 120 * Anim.outCubic(land), 2.5f * (1 - land) + 0.5f).fill(Gfx.alpha(PALE, 0.8f * (1 - land))).draw(g);
                Shapes.rays(w / 2f, cy, 90 + 40 * land, 16).fill(Gfx.alpha(SystemTheme.GOLD, 0.35f * (1 - land))).rotation(land * 0.1f).draw(g);
            }
            g.pose().pushPose();
            g.pose().translate(w / 2f, cy, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(-5));
            g.pose().scale(s, s, 1);
            String t = Component.translatable("penalty.beyondsolo.complete").getString();
            float tw = SysFont.spacedWidth(t, 18, true, 2.5f);
            Shapes.box(-tw / 2f - 16, -17, tw + 32, 34).radius(6).fill(0xB0140A04).border(1.6f, SystemTheme.GOLD).glow(10, Gfx.alpha(SystemTheme.GOLD, 0.45f)).draw(g);
            Shapes.box(-tw / 2f - 12, -13, tw + 24, 26).radius(4).fill(0).border(0.6f, Gfx.alpha(SystemTheme.GOLD, 0.7f)).draw(g);
            SysFont.setContentScale(s);
            SysFont.spaced(g, t, 0, -6.5f, 18, 0xFFFFF1C8, true, 2.5f, 0.5f);
            SysFont.setContentScale(1);
            g.pose().popPose();
            Gfx.setAlpha(old);
        }
        float subA = Anim.window(age, 1.3f, 1.8f) * (1 - Anim.window(age, 4.2f, 4.8f));
        if (subA > 0) {
            float old = Gfx.fade(subA);
            SysFont.draw(g, Component.translatable("penalty.beyondsolo.complete.sub", PenaltyClient.kills).getString(), w / 2f, cy + 30, 8.5f, PALE, false, 0.5f);
            Gfx.setAlpha(old);
        }
        // the light takes the screen
        float white = Anim.window(age, 3.4f, 5.0f);
        if (white > 0) g.fill(0, 0, w, h, Gfx.alpha(0xFFFFFFFF, Anim.inOutCubic(white)));
    }

    private static void failed(GuiGraphics g, int w, int h, float age) {
        float flash = 1 - Anim.window(age, 0, 0.5f);
        Shapes.vignette(0, 0, w, h, 0.3f, 1.1f).fill(Gfx.alpha(RED_DEEP, 0.6f + 0.3f * flash)).draw(g);
        if (flash > 0) g.fill(0, 0, w, h, Gfx.alpha(RED, 0.35f * flash));
        float cy = h * 0.36f;
        float in = Anim.outCubic(Anim.window(age, 0.1f, 0.5f));
        float glitch = Math.max(0, 1 - age / 1.2f);
        RandomSource r = RandomSource.create((long) (Anim.now() * 24));
        String t = Component.translatable("penalty.beyondsolo.failed").getString();
        float size = 22;
        float jx = (r.nextFloat() - 0.5f) * 8 * glitch;
        float old = Gfx.fade(in);
        if (glitch > 0.05f) {
            SysFont.spaced(g, t, w / 2f - 3 * glitch + jx, cy - 10, size, Gfx.alpha(0xFF30E0FF, 0.5f * glitch), true, 3f, 0.5f);
            SysFont.spaced(g, t, w / 2f + 3 * glitch + jx, cy - 10, size, Gfx.alpha(RED, 0.7f * glitch), true, 3f, 0.5f);
        }
        SysFont.spaced(g, t, w / 2f + jx, cy - 10, size, 0xFFFFFFFF, true, 3f, 0.5f);
        float tw = SysFont.spacedWidth(t, size, true, 3f);
        float strike = Anim.outCubic(Anim.window(age, 0.6f, 1.0f));
        if (strike > 0) Shapes.box(w / 2f - tw / 2f - 6, cy - 1, (tw + 12) * strike, 2).fill(RED).glow(4, Gfx.alpha(RED, 0.7f)).draw(g);
        SysFont.Line sub = SysFont.line(Component.translatable("penalty.beyondsolo.failed.sub").getString(), 8.5f, false);
        int chars = (int) Math.min(SysFont.length(sub), Math.max(0, age - 1.0f) * 40f);
        SysFont.draw(g, SysFont.truncate(sub, chars), w / 2f, cy + 14, SystemTheme.TEXT, 0.5f);
        Gfx.setAlpha(old);
        float black = Anim.window(age, 2.4f, 3.5f);
        if (black > 0) g.fill(0, 0, w, h, Gfx.alpha(0xFF000000, black));
    }

    /** Home again: the white (or black) the sentence ended on fades away. */
    private static void afterglow(GuiGraphics g, int w, int h) {
        float age = Anim.since(PenaltyClient.endedAt);
        if (age > 1.6f) return;
        int color = PenaltyClient.endedFrom == PenaltyPayload.SURVIVED ? 0xFFFFFFFF : 0xFF000000;
        float a = 1 - Anim.window(age, 0.4f, 1.6f);
        if (a > 0) g.fill(0, 0, w, h, Gfx.alpha(color, a));
    }

    // ================================================================ helpers

    private static void typed(GuiGraphics g, String s, float x, float y, float size, int color, float age, float from) {
        if (age < from) return;
        SysFont.Line line = SysFont.line(s, size, false);
        int chars = (int) Math.min(SysFont.length(line), (age - from) * 45f);
        SysFont.draw(g, SysFont.truncate(line, chars), x, y, color, 0f);
        if (chars < SysFont.length(line) && (int) (Anim.now() * 6) % 2 == 0) {
            float cw = SysFont.width(s.substring(0, Math.min(s.length(), chars)), size, false);
            Shapes.box(x + cw + 1, y, 3, size * 0.8f).fill(RED).draw(g);
        }
    }

    static String clock(float ticks) {
        int s = (int) Math.ceil(ticks / 20f);
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    private PenaltyOverlay() {}
}
