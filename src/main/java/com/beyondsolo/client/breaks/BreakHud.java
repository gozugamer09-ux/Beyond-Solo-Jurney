package com.beyondsolo.client.breaks;

import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.combat.WorldProjector;
import com.beyondsolo.client.depths.DepthsClient;
import com.beyondsolo.client.depths.DepthsIcons;
import com.beyondsolo.client.evaluation.EvaluationOverlay;
import com.beyondsolo.client.gate.GateOverlay;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.hud.HudSpace;
import com.beyondsolo.client.key.RunHud;
import com.beyondsolo.client.quest.QuestOverlay;
import com.beyondsolo.client.skill.SkillOverlay;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.network.GateSensePayload;
import com.beyondsolo.system.PlayerRank;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Dungeon breaks on screen.
 * <ul>
 *   <li>The System's banners: DUNGEON BREAK (the screen's edges throb red, a tear runs across it and the words are
 *       stamped in, shaking), a break's boss coming through (its name under a skull), and the break suppressed.</li>
 *   <li>At the top, under the boss bar: the collapse timer of the gate near the player that is coming apart (amber;
 *       red and beating with the heart for its last minute; dimmed while hunters inside hold it), or, once it has
 *       broken, how many of its monsters are out and whether its boss is. Which way it lies and how far.</li>
 *   <li>Under that, after a gate-sensing pulse: a compass across the top of the screen with every gate it sensed, in
 *       its rank's colour, with how far it is and, for one coming apart, how long it has left.</li>
 *   <li>The break felt: a red flash at the edges, harder the nearer it was; and while the player stands in a break,
 *       the edges of the screen stay faintly red.</li>
 * </ul>
 * Without the System the player only feels it: no banners, no timer, no compass.
 */
public final class BreakHud {
    private static final int RED = 0xFFFF3B3B;
    private static final int AMBER = 0xFFFFA43A;
    private static final int CRIMSON = 0xFFE0303A;
    /** Degrees each side of the view the compass shows. */
    private static final float SPAN = 80;

    private static float chipIn;
    private static float compassIn;
    private static float dreadIn;
    private static BreakClient.Status lastStatus;
    private static BreakClient.Sensed lastSensed;
    private static BreakClient.Banner kicked;

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // banners take turns with the other moments in the middle of the screen, and keep their time while the HUD is hidden
        boolean free = !GateOverlay.active() && !QuestOverlay.active() && !EvaluationOverlay.active() && !SkillOverlay.active()
                && !DepthsClient.bannerShowing();
        BreakClient.Banner b = BreakClient.banner(free);
        if (mc.options.hideGui) return;
        int w = g.guiWidth(), h = g.guiHeight();
        float partial = delta.getGameTimeDeltaPartialTick(false);
        felt(g, w, h);
        if (!ClientSystemData.get().hasSystem()) return;
        if (b != null) banner(g, w, h, b);
        float y = 6 + 34 * Anim.outCubic(GateOverlay.bossBarIn()) + 26 * RunHud.clockShown();
        y = timer(g, mc, w, y);
        compass(g, mc, w, y, partial);
    }

    // ================================================================ the break felt

    private static void felt(GuiGraphics g, int w, int h) {
        float t = BreakClient.sinceBreak();
        if (t >= 0 && t < 2.2f) {
            float felt = BreakClient.breakFelt();
            float k = 1 - Anim.outCubic(Anim.window(t, 0f, 2.2f));
            Shapes.vignette(0, 0, w, h, 0.05f, 1.15f).fill(Gfx.alpha(RED, 0.6f * felt * k)).draw(g);
            if (t < 0.2f) Shapes.box(0, 0, w, h).fill(Gfx.alpha(0xFFFFE6E0, 0.3f * felt * (1 - t / 0.2f))).draw(g);
        }
        dreadIn = Anim.approach(dreadIn, BreakClient.dread(), 1.2f);
        if (dreadIn > 0.01f) {
            float beat = 0.7f + 0.3f * Anim.pulse(2.6f);
            Shapes.vignette(0, 0, w, h, 0.4f, 1.4f).fill(Gfx.alpha(0xFF8A0A12, 0.24f * dreadIn * beat)).draw(g);
        }
    }

    // ================================================================ banners

    private static void banner(GuiGraphics g, int w, int h, BreakClient.Banner b) {
        float t = b.age();
        float exit = Anim.window(t, b.duration - 0.45f, b.duration);
        float live = 1 - Anim.outCubic(exit);
        if (kicked != b) {
            kicked = b;
            if (b.kind == BreakClient.BannerKind.BOSS_OUT) GateOverlay.addTrauma(0.1f);
        }
        switch (b.kind) {
            case BREAK -> breakBanner(g, w, h, b, t, live);
            case BOSS_OUT -> bossBanner(g, w, h, b, t, live);
            case SUPPRESSED -> suppressedBanner(g, w, h, b, t, live);
        }
    }

    /** DUNGEON BREAK: the edges throb red, a tear runs across the screen, the words are stamped in and shake. */
    private static void breakBanner(GuiGraphics g, int w, int h, BreakClient.Banner b, float t, float live) {
        int rc = SystemTheme.rank(b.rank);
        float cx = w / 2f, y = h * 0.24f;
        float throb = Math.max(0, Mth.sin(t * 7f)) * (1 - Anim.window(t, 0f, 2.4f));
        Shapes.vignette(0, 0, w, h, 0.2f, 1.25f).fill(Gfx.alpha(RED, (0.12f + 0.32f * throb) * live)).draw(g);
        float old = Gfx.fade(live);
        float bw = Math.min(w * 0.94f, 440);
        float tear = Anim.outCubic(Anim.window(t, 0.05f, 0.5f));
        // a dark band so the words read on anything, and the tear through it
        Shapes.pill(cx - bw / 2f, y - 12, bw, 48).fill(Gfx.alpha(0xFF0C0204, 0.6f * tear)).glow(20, Gfx.alpha(0xFF0C0204, 0.4f * tear)).draw(g);
        crack(g, cx, y + 9, bw / 2f * tear, (float) Math.floor(b.start * 7), 0.85f * (1 - 0.6f * Anim.window(t, 1.2f, 2.6f)));
        // the warning tag, blinking
        String warn = Component.translatable("break.beyondsolo.hud.warning").getString().toUpperCase(Locale.ROOT);
        float tagIn = Anim.window(t, 0f, 0.2f);
        if (tagIn > 0) {
            float blink = 0.55f + 0.45f * Math.abs(Mth.sin(t * 6f));
            float tagW = SysFont.spacedWidth(warn, 6f, true, 2.2f) + 26;
            float o2 = Gfx.fade(tagIn);
            Shapes.pill(cx - tagW / 2f, y - 27, tagW, 12).fill(Gfx.alpha(0xFF3A0608, 0.88f)).border(0.9f, Gfx.alpha(RED, blink)).glow(8, Gfx.alpha(RED, 0.45f * blink)).draw(g);
            Shapes.icon(DepthsIcons.WARNING, cx - tagW / 2f + 9, y - 21, 7.5f).fill(Gfx.mix(RED, 0xFFFFFFFF, 0.3f)).draw(g);
            SysFont.spaced(g, warn, cx + 5, y - 24, 6f, 0xFFFFE0E0, true, 2.2f, 0.5f);
            Gfx.setAlpha(o2);
        }
        // the words: stamped, then shaking with red and black ghosts that settle
        String word = b.title.getString().toUpperCase(Locale.ROOT);
        float size = 19f, track = 3.6f;
        float stamp = Anim.window(t, 0.1f, 0.34f);
        if (stamp > 0) {
            float s = Mth.lerp(Anim.outCubic(stamp), 2.5f, 1f);
            float settle = Anim.window(t, 0.34f, 1.5f);
            float seed = (float) Math.floor(t * 20);
            float shake = (1 - settle) * (1.5f + 3f * b.value);
            float jx = (noise(seed) - 0.5f) * shake, jy = (noise(seed + 5) - 0.5f) * shake * 0.6f;
            float ww = SysFont.spacedWidth(word, size, true, track);
            float fit = Math.min(1f, (bw - 24) / Math.max(1, ww));
            float o2 = Gfx.fade(Math.min(1f, stamp * 2));
            g.pose().pushPose();
            g.pose().translate(cx + jx, y + jy + size * 0.35f, 0);
            g.pose().scale(s * fit, s * fit, 1);
            if (settle < 1) {
                SysFont.spaced(g, word, -2.2f, -size * 0.35f, size, Gfx.alpha(0xFFFF2020, 0.6f * (1 - settle)), true, track, 0.5f);
                SysFont.spaced(g, word, 2.2f, -size * 0.35f + 0.8f, size, Gfx.alpha(0xFF000000, 0.7f * (1 - settle)), true, track, 0.5f);
            }
            SysFont.spaced(g, word, 0, -size * 0.35f, size, 0xFFFFECE8, true, track, 0.5f);
            g.pose().popPose();
            Gfx.setAlpha(o2);
        }
        // what broke: the rank and the dungeon's name
        float subIn = Anim.outCubic(Anim.window(t, 0.9f, 1.4f));
        if (subIn > 0.01f) {
            float o3 = Gfx.fade(subIn);
            float sw = SysFont.width(b.subtitle, 7f, true);
            float total = sw + 16;
            float sx = cx - total / 2f;
            Shapes.icon(Icons.of(b.rank), sx + 5, y + 27.5f, 9).fill(Gfx.mix(rc, 0xFFFFFFFF, 0.35f)).glow(3, Gfx.alpha(rc, 0.6f)).draw(g);
            SysFont.draw(g, b.subtitle, sx + 16, y + 24.5f, 7f, 0xFFFFC9C4, true, 0f);
            Gfx.setAlpha(o3);
        }
        Gfx.setAlpha(old);
    }

    /** A jagged tear across the screen, drawn out from the middle to {@code half} on each side. */
    private static void crack(GuiGraphics g, float cx, float cy, float half, float seed, float alpha) {
        if (half < 1 || alpha <= 0.01f) return;
        int n = 16;
        for (int side = -1; side <= 1; side += 2) {
            float px = cx, py = cy;
            for (int i = 1; i <= n; i++) {
                float k = i / (float) n;
                float x = cx + side * half * k;
                float y = cy + (i == n ? 0 : (noise(seed + i * 3.1f + side * 17) - 0.5f) * 10f * (1 - 0.5f * k));
                segment(g, px, py, x, y, 1.8f * (1 - 0.65f * k), Gfx.alpha(RED, alpha));
                // a splinter now and then
                if (i % 4 == 2) {
                    float bx = x + side * 6 * noise(seed + i), by = y + (noise(seed + i * 7) > 0.5f ? 1 : -1) * (4 + 4 * noise(seed + i * 2));
                    segment(g, x, y, bx, by, 0.9f * (1 - 0.5f * k), Gfx.alpha(RED, alpha * 0.8f));
                }
                px = x;
                py = y;
            }
        }
    }

    private static void segment(GuiGraphics g, float x0, float y0, float x1, float y1, float th, int color) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 0.1f) return;
        g.pose().pushPose();
        g.pose().translate(x0, y0, 0);
        g.pose().mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
        Shapes.box(0, -th / 2f, len + th * 0.5f, th).fill(Gfx.mix(color, 0xFFFFFFFF, 0.45f)).glow(3.5f, color).draw(g);
        g.pose().popPose();
    }

    /** A break's boss came through: its name under a skull, on a dark band. */
    private static void bossBanner(GuiGraphics g, int w, int h, BreakClient.Banner b, float t, float live) {
        int c = 0xFFFF5A3C;
        float cx = w / 2f, y = h * 0.25f;
        float pulse = 1 - Anim.window(t, 0f, 1.8f);
        Shapes.vignette(0, 0, w, h, 0.25f, 1.3f).fill(Gfx.alpha(0xFF5A0008, 0.3f * pulse * live)).draw(g);
        float old = Gfx.fade(live);
        float open = Anim.outCubic(Anim.window(t, 0f, 0.45f));
        float bw = Math.min(w * 0.85f, 340) * open;
        Shapes.pill(cx - bw / 2f, y - 4, bw, 36).fill(Gfx.alpha(0xFF0A0304, 0.62f)).glow(16, Gfx.alpha(0xFF0A0304, 0.4f)).draw(g);
        Ui.divider(g, cx - bw * 0.4f, y + 31, bw * 0.8f, Gfx.alpha(c, 0.75f * open));
        float badge = Anim.outBack(Anim.window(t, 0.1f, 0.5f));
        if (badge > 0.01f) {
            g.pose().pushPose();
            g.pose().translate(cx, y - 6, 0);
            g.pose().scale(badge, badge, 1);
            Ui.iconBadge(g, Icons.SKULL, 0, 0, 8.5f, c);
            g.pose().popPose();
        }
        typed(g, b.title, cx, y + 6, 14f, 0xFFFFE8E0, Anim.window(t, 0.3f, 1.0f));
        float subIn = Anim.outCubic(Anim.window(t, 0.9f, 1.3f));
        if (subIn > 0.01f) {
            float o2 = Gfx.fade(subIn);
            SysFont.draw(g, b.subtitle, cx, y + 22, 6.6f, 0xFFFFB8A8, false, 0.5f);
            Gfx.setAlpha(o2);
        }
        Gfx.setAlpha(old);
    }

    /** The break is over: the System's window, with what it paid. */
    private static void suppressedBanner(GuiGraphics g, int w, int h, BreakClient.Banner b, float t, float live) {
        int c = SystemTheme.ACCENT;
        int rc = SystemTheme.rank(b.rank);
        String tag = b.title.getString().toUpperCase(Locale.ROOT);
        String xp = b.value > 0 ? Component.translatable("break.beyondsolo.hud.xp", String.format(Locale.ROOT, "%,d", (long) b.value)).getString() : null;
        float lineSize = 7.4f;
        float lw = SysFont.width(b.subtitle, lineSize, true);
        float xpW = xp == null ? 0 : SysFont.width(xp, 6f, true) + 14;
        float pw = Math.max(190, Math.max(lw + 60, SysFont.spacedWidth(tag, 5.8f, true, 1.6f) + 40));
        float ph = xp == null ? 32 : 44;
        float cx = w / 2f, y = h * 0.24f;
        float open = Anim.outBack(Anim.window(t, 0f, 0.45f));
        float old = Gfx.fade(live);
        Shapes.vignette(0, 0, w, h, 0.35f, 1.5f).fill(Gfx.alpha(c, 0.1f * (1 - Anim.window(t, 0.4f, 2.2f)))).draw(g);
        g.pose().pushPose();
        g.pose().translate(cx, y + ph / 2f, 0);
        g.pose().scale(1, Math.max(0.02f, open), 1);
        Ui.panel(g, -pw / 2f, -ph / 2f, pw, ph, 9, c, 1f);
        g.pose().popPose();
        if (open > 0.9f) {
            float k = Anim.outCubic(Anim.window(t, 0.35f, 0.8f));
            float o2 = Gfx.fade(k);
            float tagW = SysFont.spacedWidth(tag, 5.8f, true, 1.6f) + 14;
            Shapes.pill(cx - tagW / 2f, y - 5.5f, tagW, 11).fill(Gfx.alpha(0xFF060A14, 0.9f)).border(0.75f, Gfx.alpha(c, 0.85f)).glow(5, Gfx.alpha(c, 0.4f)).draw(g);
            SysFont.spaced(g, tag, cx, y - 2.4f, 5.8f, Gfx.mix(c, 0xFFFFFFFF, 0.55f), true, 1.6f, 0.5f);
            float left = cx - pw / 2f + 18;
            Ui.iconBadge(g, DepthsIcons.CHECK, left, y + 15, 8.5f, c);
            typed(g, b.subtitle, cx + 8, y + 11, lineSize, 0xFFFFFFFF, Anim.window(t, 0.4f, 1.1f));
            if (xp != null) {
                float xk = Anim.outBack(Anim.window(t, 1.0f, 1.4f));
                if (xk > 0.01f) {
                    g.pose().pushPose();
                    g.pose().translate(cx + 8, y + 31, 0);
                    g.pose().scale(xk, xk, 1);
                    int gold = SystemTheme.GOLD;
                    Shapes.pill(-xpW / 2f, -5.5f, xpW, 11).fill(Gfx.alpha(gold, 0.16f)).border(0.7f, Gfx.alpha(gold, 0.7f)).glow(4, Gfx.alpha(gold, 0.3f)).draw(g);
                    SysFont.draw(g, xp, 0, -5.5f + SysFont.centerY(0, 11, 6f), 6f, Gfx.mix(gold, 0xFFFFFFFF, 0.35f), true, 0.5f);
                    g.pose().popPose();
                }
            }
            // the rank of the gate that fell, in the corner
            Shapes.icon(Icons.of(b.rank), cx + pw / 2f - 14, y + 12, 8).fill(Gfx.alpha(rc, 0.8f)).draw(g);
            Gfx.setAlpha(o2);
        }
        Gfx.setAlpha(old);
    }

    // ================================================================ the collapse timer

    /** The collapse timer of the gate that matters most (see {@link BreakClient#urgent}). Returns the y under it. */
    private static float timer(GuiGraphics g, Minecraft mc, int w, float y) {
        BreakClient.Status s = BreakClient.urgent();
        if (s != null) lastStatus = s;
        chipIn = Anim.approach(chipIn, s != null ? 1 : 0, s != null ? 4f : 3f);
        if (chipIn < 0.01f || lastStatus == null) return y;
        BreakClient.Status st = lastStatus;
        float in = Anim.outCubic(chipIn);
        int phase = Mth.clamp(st.phase, 1, 3);
        int c = phase == 1 ? AMBER : phase == 2 ? RED : CRIMSON;
        int rc = SystemTheme.rank(st.rank);
        boolean held = phase < 3 && st.held;
        String label = Component.translatable(phase == 1 ? "break.beyondsolo.hud.unstable" : phase == 2 ? "break.beyondsolo.hud.imminent"
                : "break.beyondsolo.hud.broken").getString().toUpperCase(Locale.ROOT);
        float secs = st.secondsLeft();
        String big = phase == 3 ? String.valueOf(st.value) : clock(secs);
        Vec3 p = mc.player.position();
        double dx = st.center().x - p.x, dz = st.center().z - p.z;
        String rankText = Component.translatable("break.beyondsolo.hud.rank", st.rank.displayName()).getString();
        String far = Component.translatable("break.beyondsolo.hud.distance", Math.round(Math.sqrt(dx * dx + dz * dz))).getString();
        String extra = phase == 3 ? Component.translatable(st.bossOut ? "break.beyondsolo.hud.boss_out" : "break.beyondsolo.hud.monsters").getString()
                : held ? Component.translatable("break.beyondsolo.hud.held").getString() : null;
        float labelW = SysFont.spacedWidth(label, 5.4f, true, 1.3f);
        float line2W = SysFont.width(rankText, 5f, true) + 5 + 8 + SysFont.width(far, 5f, true) + (extra == null ? 0 : 6 + SysFont.width(extra, 5f, true));
        float bigSize = 9f;
        float bigW = SysFont.width(big, bigSize, true) + (phase == 3 ? 11 : 0);
        float bh = 22;
        float bw = 22 + Math.max(labelW, line2W) + 12 + bigW + 10;
        float x = (w - bw) / 2f;
        y -= 14 * (1 - in);
        float old = Gfx.fade(in);
        HudSpace.claimTop(x, y, bw, bh);
        float blink = phase == 2 ? 0.5f + 0.5f * Anim.pulse(0.8f) : phase == 3 ? 0.5f + 0.5f * Anim.pulse(2.2f) : 0.5f + 0.5f * Anim.pulse(3f);
        Shapes.box(x, y, bw, bh).radius(bh / 2f).fillV(Gfx.mix(0xC00A1222, Gfx.alpha(c, 0.75f), phase == 1 ? 0.12f : 0.22f), 0xC0050912)
                .border(0.8f, Gfx.alpha(c, 0.55f + 0.4f * blink), Gfx.alpha(c, 0.3f))
                .glow(4 + 4 * blink, Gfx.alpha(c, 0.18f + 0.12f * blink)).draw(g);
        // the gate's emblem: its rank in a ring that turns faster the nearer the break
        float ix = x + 11, iy = y + bh / 2f;
        Shapes.circle(ix, iy, 7).fillV(Gfx.alpha(rc, 0.3f), Gfx.alpha(rc, 0.1f)).border(0.6f, Gfx.alpha(c, 0.85f)).draw(g);
        float spin = phase == 1 ? 7f : phase == 2 ? 1.6f : 4f;
        Shapes.ring(ix, iy, 8.6f, 0.55f).fill(Gfx.alpha(c, 0.7f)).dashes(phase == 3 ? 6 : 10, 0.5f).rotation(held ? 0 : Anim.loop(spin)).draw(g);
        Shapes.icon(Icons.of(st.rank), ix, iy, 8.5f).fill(Gfx.mix(rc, 0xFFFFFFFF, 0.55f)).glow(2, Gfx.alpha(rc, 0.6f)).draw(g);
        // the label, and on the line under it the rank, which way and how far
        float tx = x + 22;
        SysFont.spaced(g, label, tx, y + 3.8f, 5.4f, Gfx.mix(c, 0xFFFFFFFF, 0.45f), true, 1.3f, 0f);
        float ly = y + 12.6f;
        SysFont.draw(g, rankText, tx, ly, 5f, Gfx.mix(rc, 0xFFFFFFFF, 0.35f), true, 0f);
        float ax = tx + SysFont.width(rankText, 5f, true) + 5 + 3.5f;
        float bearing = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float rel = Mth.wrapDegrees(bearing - mc.player.getYRot());
        arrow(g, ax, ly + 2, rel, Gfx.mix(c, 0xFFFFFFFF, 0.4f));
        float fx = ax + 4.5f;
        SysFont.draw(g, far, fx, ly, 5f, SystemTheme.TEXT_SOFT, true, 0f);
        if (extra != null) {
            int ec = phase == 3 ? (st.bossOut ? 0xFFFF7A5A : SystemTheme.TEXT_SOFT) : SystemTheme.TEXT_DIM;
            SysFont.draw(g, extra, fx + SysFont.width(far, 5f, true) + 6, ly, 5f, ec, true, 0f);
        }
        // the time left (or the monsters out), beating with the heart for the last minute
        float bx = x + bw - 10 - bigW;
        float scale = 1;
        if (phase == 2 && !held) {
            float frac = secs - (float) Math.floor(secs);
            scale = 1 + 0.2f * frac * frac * frac * frac;
        }
        int bc = held ? SystemTheme.TEXT_DIM : Gfx.mix(c, 0xFFFFFFFF, phase == 2 ? 0.35f : 0.5f);
        g.pose().pushPose();
        g.pose().translate(bx + bigW / 2f, y + bh / 2f, 0);
        g.pose().scale(scale, scale, 1);
        float left = -bigW / 2f;
        if (phase == 3) {
            Shapes.icon(st.bossOut ? Icons.CROWN : Icons.SKULL, left + 4, 0, 8).fill(Gfx.mix(c, 0xFFFFFFFF, 0.3f)).glow(2, Gfx.alpha(c, 0.6f)).draw(g);
            left += 11;
        } else if (held) {
            Shapes.icon(Icons.HOURGLASS, left - 6, 0, 7).fill(SystemTheme.TEXT_DIM).draw(g);
        }
        SysFont.draw(g, big, left, SysFont.centerY(-bh / 2f, bh, bigSize), bigSize, bc, true, 0f);
        g.pose().popPose();
        Gfx.setAlpha(old);
        return y + (bh + 5) * in;
    }

    /** m:ss (h:mm:ss past an hour). */
    static String clock(float seconds) {
        int s = Math.max(0, (int) Math.ceil(seconds));
        if (s >= 3600) return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    /** A small arrow, pointing up for ahead and turned {@code degrees} clockwise. */
    private static void arrow(GuiGraphics g, float cx, float cy, float degrees, int color) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(degrees));
        segment(g, -2.6f, 1.6f, 0, -2.2f, 1.1f, color);
        segment(g, 0, -2.2f, 2.6f, 1.6f, 1.1f, color);
        segment(g, 0, -2.2f, 0, 2.6f, 0.9f, color);
        g.pose().popPose();
    }

    // ================================================================ the compass

    /** The gates the last pulse sensed, on a compass across the top of the screen. */
    private static void compass(GuiGraphics g, Minecraft mc, int w, float y, float partial) {
        BreakClient.Sensed s = BreakClient.sensed();
        boolean show = s != null && !s.gates().isEmpty() && s.age() < s.seconds() - 0.4f;
        if (show) lastSensed = s;
        compassIn = Anim.approach(compassIn, show ? 1 : 0, show ? 5f : 3f);
        if (compassIn < 0.01f || lastSensed == null) return;
        BreakClient.Sensed ss = lastSensed;
        float in = Anim.outCubic(compassIn);
        float bw = Math.min(w - 32, 260), bh = 14;
        float x = (w - bw) / 2f;
        y -= 8 * (1 - in);
        float old = Gfx.fade(in);
        // the nearest gates have their distance under the dial
        HudSpace.claimTop(x, y, bw, bh + 12);
        inView(g, mc, w, ss, y + bh + 16, partial);
        int ac = SystemTheme.ACCENT;
        Shapes.pill(x, y, bw, bh).fillV(0xB80A1222, 0xB8050912).border(0.7f, Gfx.alpha(ac, 0.55f), Gfx.alpha(ac, 0.2f)).glow(5, Gfx.alpha(ac, 0.18f)).draw(g);
        // the cap: the sensing mark and how many
        String count = String.valueOf(ss.gates().size());
        float capW = 14 + SysFont.width(count, 5.6f, true) + 6;
        Shapes.icon(Icons.COMPASS, x + 9, y + bh / 2f, 8).fill(Gfx.mix(ac, 0xFFFFFFFF, 0.4f)).glow(2, Gfx.alpha(ac, 0.5f)).draw(g);
        SysFont.draw(g, count, x + 15, SysFont.centerY(y, bh, 5.6f), 5.6f, SystemTheme.TEXT, true, 0f);
        Shapes.box(x + capW, y + 3, 0.7f, bh - 6).fill(Gfx.alpha(ac, 0.4f)).draw(g);
        // the dial: ticks every 15 degrees, letters at the cardinal points
        float left = x + capW + 8, right = x + bw - 8;
        float cx = (left + right) / 2f, half = (right - left) / 2f;
        float yaw = mc.player.getViewYRot(partial);
        for (int a = -180; a < 180; a += 15) {
            float rel = Mth.wrapDegrees(a - yaw);
            if (Math.abs(rel) > SPAN) continue;
            float px = cx + rel / SPAN * half;
            float edge = 1 - Anim.clamp01((Math.abs(rel) - SPAN * 0.65f) / (SPAN * 0.35f));
            if (a % 90 == 0) {
                SysFont.draw(g, cardinal(a), px, SysFont.centerY(y, bh, 5.6f), 5.6f, Gfx.alpha(SystemTheme.TEXT_SOFT, 0.9f * edge), true, 0.5f);
            } else {
                float th = a % 45 == 0 ? 5 : 3;
                Shapes.box(px - 0.4f, y + (bh - th) / 2f, 0.8f, th).fill(Gfx.alpha(SystemTheme.TEXT_DIM, 0.75f * edge)).draw(g);
            }
        }
        Shapes.box(cx - 0.5f, y - 2.5f, 1, 3.5f).fill(Gfx.alpha(SystemTheme.ACCENT_BRIGHT, 0.9f)).glow(2, Gfx.alpha(ac, 0.6f)).draw(g);
        // the gates, farthest first so the nearest draw on top; the four nearest have their distance under them
        Vec3 p = mc.player.position();
        List<GateSensePayload.Entry> gates = new ArrayList<>(ss.gates());
        gates.sort(Comparator.comparingDouble((GateSensePayload.Entry e) -> flat(p, e)).reversed());
        float ticks = BreakClient.gameTime() + partial;
        for (int i = 0; i < gates.size(); i++) {
            GateSensePayload.Entry e = gates.get(i);
            double d = flat(p, e);
            float appear = Anim.outBack(Anim.window(ss.age(), BreakWorldFx.delay(d), BreakWorldFx.delay(d) + 0.35f));
            if (appear <= 0.01f) continue;
            double dx = e.pos().getX() + 0.5 - p.x, dz = e.pos().getZ() + 0.5 - p.z;
            float rel = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)) - yaw);
            boolean edge = Math.abs(rel) > SPAN;
            float px = cx + Mth.clamp(rel / SPAN, -1, 1) * half;
            boolean labelled = i >= gates.size() - 4;
            marker(g, e, px, y + bh / 2f, d, edge, rel, appear, labelled, ss, ticks);
        }
        Gfx.setAlpha(old);
    }

    /**
     * The sensed gates in view, marked where they stand (through hills and rock), with how far and how long they have
     * left: the compass says which way, these say which one. Gates a few steps away need no mark.
     */
    private static void inView(GuiGraphics g, Minecraft mc, int w, BreakClient.Sensed ss, float top, float partial) {
        Vec3 p = mc.player.position();
        List<GateSensePayload.Entry> gates = new ArrayList<>(ss.gates());
        gates.sort(Comparator.comparingDouble((GateSensePayload.Entry e) -> flat(p, e)).reversed());
        float ticks = BreakClient.gameTime() + partial;
        int h = mc.getWindow().getGuiScaledHeight();
        for (GateSensePayload.Entry e : gates) {
            double d = flat(p, e);
            if (d < 12) continue;
            float appear = Anim.outBack(Anim.window(ss.age(), BreakWorldFx.delay(d), BreakWorldFx.delay(d) + 0.35f));
            if (appear <= 0.01f) continue;
            float[] at = WorldProjector.project(Vec3.atBottomCenterOf(e.pos()).add(0, GateBlockEntity.CENTER_Y, 0));
            if (at == null || at[0] < 10 || at[0] > w - 10 || at[1] > h - 40) continue;
            marker(g, e, at[0], Math.max(top, at[1]), d, false, 0, appear, true, ss, ticks);
        }
    }

    private static void marker(GuiGraphics g, GateSensePayload.Entry e, float px, float cy, double d, boolean edge, float rel, float appear,
                               boolean labelled, BreakClient.Sensed ss, float ticks) {
        PlayerRank rank = PlayerRank.values()[Mth.clamp(e.rank(), 0, PlayerRank.values().length - 1)];
        int c = 0xFF000000 | BreakWorldFx.color(e, ticks);
        float o = Gfx.fade(edge ? 0.6f : 1f);
        g.pose().pushPose();
        g.pose().translate(px, cy, 0);
        g.pose().scale(appear, appear, 1);
        if (e.state() == GateSensePayload.UNSTABLE || e.state() == GateSensePayload.IMMINENT || e.state() == GateSensePayload.BROKEN) {
            float ring = (ticks % 24) / 24f;
            Shapes.ring(0, 0, 5 + 5 * ring, 0.8f).fill(Gfx.alpha(RED, 0.8f * (1 - ring))).draw(g);
        }
        g.pose().pushPose();
        g.pose().mulPose(Axis.ZP.rotationDegrees(45));
        Shapes.box(-4.2f, -4.2f, 8.4f, 8.4f).radius(1.6f).fill(Gfx.alpha(0xFF060A14, 0.92f)).border(0.8f, c).glow(3.5f, Gfx.alpha(c, 0.55f)).draw(g);
        g.pose().popPose();
        Shapes.icon(Icons.of(rank), 0, 0.2f, 6.6f).fill(Gfx.mix(c, 0xFFFFFFFF, 0.6f)).draw(g);
        if (edge) {
            float side = rel > 0 ? 1 : -1;
            segment(g, side * 7.5f, -2.5f, side * 10f, 0, 1f, c);
            segment(g, side * 10f, 0, side * 7.5f, 2.5f, 1f, c);
        }
        g.pose().popPose();
        if (labelled && appear > 0.5f) {
            String far = Component.translatable("break.beyondsolo.hud.distance", Math.round(d)).getString();
            String extra = null;
            int ec = SystemTheme.TEXT_SOFT;
            if (e.ticksLeft() >= 0 && (e.state() == GateSensePayload.UNSTABLE || e.state() == GateSensePayload.IMMINENT)) {
                long left = e.ticksLeft() - Math.max(0, BreakClient.gameTime() - ss.heardAt());
                extra = clock(Math.max(0, left) / 20f);
                ec = e.state() == GateSensePayload.IMMINENT ? RED : AMBER;
            } else if (e.state() == GateSensePayload.BROKEN) {
                extra = Component.translatable("break.beyondsolo.hud.broken_short").getString();
                ec = RED;
            }
            float fw = SysFont.width(far, 4.8f, true);
            float ew = extra == null ? 0 : SysFont.width(extra, 4.8f, true) + 4;
            float lx = px - (fw + ew) / 2f, ly = cy + 9.5f;
            Shapes.pill(lx - 3, ly - 1.2f, fw + ew + 6, 7.4f).fill(Gfx.alpha(0xFF060A14, 0.55f)).draw(g);
            SysFont.draw(g, far, lx, ly, 4.8f, SystemTheme.TEXT_SOFT, true, 0f);
            if (extra != null) SysFont.draw(g, extra, lx + fw + 4, ly, 4.8f, ec, true, 0f);
        }
        Gfx.setAlpha(o);
    }

    private static double flat(Vec3 p, GateSensePayload.Entry e) {
        double dx = e.pos().getX() + 0.5 - p.x, dz = e.pos().getZ() + 0.5 - p.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** The letter for a cardinal yaw (Minecraft's: 0 faces south, 90 west). */
    private static String cardinal(int yaw) {
        String key = switch (Math.floorMod(yaw, 360)) {
            case 0 -> "s";
            case 90 -> "w";
            case 180 -> "n";
            default -> "e";
        };
        return Component.translatable("break.beyondsolo.hud.cardinal." + key).getString();
    }

    // ================================================================ helpers

    private static float noise(float seed) {
        float s = Mth.sin(seed * 12.9898f + 78.233f) * 43758.547f;
        return s - (float) Math.floor(s);
    }

    /** Text that types itself in, with a bright caret at the end while it types. */
    private static void typed(GuiGraphics g, Component text, float cx, float y, float size, int color, float k) {
        if (k <= 0) return;
        SysFont.Line line = SysFont.line(text, size, true);
        int n = SysFont.length(line);
        int shown = Math.min(n, Math.round(n * k));
        float full = line.width();
        SysFont.draw(g, SysFont.truncate(line, shown), cx - full / 2f, y, color, 0f);
        if (k < 1f) {
            float caret = cx - full / 2f + SysFont.truncate(line, shown).width() + 1;
            Shapes.box(caret, y, 1.2f, size * 0.8f).fill(Gfx.alpha(SystemTheme.ACCENT_BRIGHT, 0.9f)).glow(4, SystemTheme.ACCENT).draw(g);
        }
    }

    /** How far down the break HUD reaches from where it starts (the collapse timer and the compass): things under it move down. */
    public static float shownHeight() {
        return 27 * Anim.outCubic(chipIn) + 32 * Anim.outCubic(compassIn);
    }

    static void reset() {
        chipIn = 0;
        compassIn = 0;
        dreadIn = 0;
        lastStatus = null;
        lastSensed = null;
        kicked = null;
    }

    private BreakHud() {}
}
