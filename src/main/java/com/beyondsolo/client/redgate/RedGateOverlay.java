package com.beyondsolo.client.redgate;

import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.breaks.BreakHud;
import com.beyondsolo.client.gate.GateOverlay;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.key.RunHud;
import com.beyondsolo.network.RedGatePayload;
import com.beyondsolo.redgate.RedWorld;
import com.beyondsolo.system.PlayerRank;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.Locale;

/**
 * Red gates on screen.
 * <ul>
 *   <li><b>The warning</b>, as the hunter arrives: the screen's edges beat red, a red System window glitches in
 *   (WARNING: the gate has turned red, the way out is sealed, time runs faster here, what breaks the seal), and a ring
 *   counts down the seconds the way out stays open; as it seals, a lock snaps shut in it.</li>
 *   <li><b>Inside</b>, at the top under the boss bar: the day and the inside clock (the sun or the moon, how much faster
 *   than outside), the cold, camps taken and creatures slain, and under it a compass with the way to its master (or,
 *   once the hold breaks, the way out) and how far.</li>
 *   <li><b>Banners</b> under that for what happens: night, dawn, a raid, a blizzard and its end, a camp taken, the
 *   seal breaking, the way out opening, the edge, freezing.</li>
 *   <li><b>Felt</b>: frost creeping in from the edges as the hunter chills, crimson as night falls, flashes for a raid,
 *   the edge, the blizzard.</li>
 *   <li><b>Outside</b>, near a red gate in the world: whether it has sealed, how many are inside and their day.</li>
 *   <li><b>Back home</b>: the card that sums it up (cleared, escaped, or fallen), with days, kills, camps and XP.</li>
 * </ul>
 * Without the System the player only feels it.
 */
public final class RedGateOverlay {
    static final int RED = 0xFFFF2B3E;
    static final int RED_SOFT = 0xFFFF757D;
    static final int RED_DEEP = 0xFF6A0410;
    static final int EMBER = 0xFFFFA04C;
    static final int ICE = 0xFFB8E9FF;
    static final int PALE = 0xFFFFEAE6;
    static final int GLASS_TOP = 0xF2270509;
    static final int GLASS_BOTTOM = 0xF2110205;
    /** Degrees each side of the view the compass shows. */
    private static final float SPAN = 80;

    private static float hudIn;
    private static float outIn;
    private static float shownCold;

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int w = g.guiWidth(), h = g.guiHeight();
        float partial = delta.getGameTimeDeltaPartialTick(false);
        felt(g, w, h);
        if (!ClientSystemData.get().hasSystem()) return;
        RedGateClient.Banner b = RedGateClient.banner();
        if (!mc.options.hideGui) {
            boolean passing = GateOverlay.passing();
            float y = 6 + 34 * Anim.outCubic(GateOverlay.bossBarIn()) + 26 * RunHud.clockShown() + BreakHud.shownHeight();
            y = inside(g, mc, w, y, partial, passing);
            y = outside(g, w, y, passing);
            if (b != null) banner(g, w, y + 2, b);
        }
        if (RedGateClient.warningUp()) warning(g, w, h, partial);
        summary(g, mc, w, h);
    }

    // ================================================================ felt

    private static void felt(GuiGraphics g, int w, int h) {
        // the cold creeps in from the edges (vanilla's frost rim comes on top once frozen through)
        shownCold = Anim.approach(shownCold, RedGateClient.chill(), 2f);
        if (shownCold > 0.02f) {
            float k = shownCold * shownCold;
            Shapes.vignette(0, 0, w, h, 0.6f - 0.3f * shownCold, 1.35f).fill(Gfx.alpha(0xFFD4F1FF, 0.3f * k)).draw(g);
        }
        // night falls: the edges go crimson, then settle
        float na = Anim.since(RedGateClient.nightAt);
        if (na < 4.5f) {
            float k = Anim.window(na, 0f, 0.9f) * (1 - Anim.window(na, 1.8f, 4.5f));
            Shapes.vignette(0, 0, w, h, 0.3f, 1.3f).fill(Gfx.alpha(RED_DEEP, 0.5f * k)).draw(g);
        }
        float t = Anim.since(RedGateClient.flashAt);
        switch (RedGateClient.flash) {
            case RedGatePayload.EV_WARNING -> {
                if (t < 4.5f) {
                    float beat = heartbeat(t * 1.15f) * (1 - Anim.window(t, 2.5f, 4.5f));
                    Shapes.vignette(0, 0, w, h, 0.4f, 1.2f).fill(Gfx.alpha(RED_DEEP, 0.3f + 0.4f * beat)).draw(g);
                    if (t < 0.25f) Shapes.box(0, 0, w, h).fill(Gfx.alpha(RED, 0.3f * (1 - t / 0.25f))).draw(g);
                }
            }
            case RedGatePayload.EV_TURNED -> {
                if (t < 2.6f) {
                    float beat = heartbeat(t * 1.3f) * (1 - Anim.window(t, 1.2f, 2.6f));
                    Shapes.vignette(0, 0, w, h, 0.25f, 1.25f).fill(Gfx.alpha(RED, 0.15f + 0.35f * beat)).draw(g);
                }
            }
            case RedGatePayload.EV_SEALED, RedGatePayload.EV_RAID -> {
                if (t < 1.4f) {
                    float k = 1 - Anim.outCubic(t / 1.4f);
                    Shapes.vignette(0, 0, w, h, 0.25f, 1.15f).fill(Gfx.alpha(RED, 0.5f * k)).draw(g);
                }
            }
            case RedGatePayload.EV_EDGE, RedGatePayload.EV_BLIZZARD -> {
                if (t < 1.6f) {
                    float k = 1 - Anim.outCubic(t / 1.6f);
                    Shapes.vignette(0, 0, w, h, 0.2f, 1.1f).fill(Gfx.alpha(0xFFF2FBFF, 0.55f * k)).draw(g);
                }
            }
            case RedGatePayload.EV_RELEASED -> {
                if (t < 2.2f) {
                    float k = 1 - Anim.outCubic(t / 2.2f);
                    Shapes.vignette(0, 0, w, h, 0.3f, 1.3f).fill(Gfx.alpha(SystemTheme.ACCENT, 0.35f * k)).draw(g);
                }
            }
            default -> {}
        }
    }

    /** A heartbeat: two quick thumps per period, then rest. */
    private static float heartbeat(float t) {
        float f = t - (float) Math.floor(t);
        float a = Math.max(0, 1 - Math.abs(f - 0.08f) / 0.08f);
        float b = Math.max(0, 1 - Math.abs(f - 0.28f) / 0.1f) * 0.7f;
        return Math.max(a, b);
    }

    // ================================================================ the warning

    private static void warning(GuiGraphics g, int w, int h, float partial) {
        float age = Anim.since(RedGateClient.warningAt);
        float total = RedGateClient.WARNING_SECONDS;
        float in = Anim.outBack(Anim.window(age, 0.1f, 0.6f));
        float out = Anim.window(age, total - 0.6f, total);
        float a = Mth.clamp(in * 1.4f, 0, 1) * (1 - Anim.outCubic(out));
        if (a <= 0.01f) return;
        boolean release = RedGateClient.releaseDay > 0;
        float pw = Math.min(w - 24, 286), ph = release ? 108 : 96;
        float x = (w - pw) / 2f, y = h * 0.18f - 20 * Anim.inCubic(out);
        float beat = heartbeat(age * 1.15f) * (1 - Anim.window(age, 3f, 5f));
        float glitch = age < 0.75f ? 1 - age / 0.75f : 0;
        RandomSource r = RandomSource.create((long) (Anim.now() * 30));
        float jx = glitch > 0 ? (r.nextFloat() - 0.5f) * 10 * glitch : 0;
        float old = Gfx.fade(a);
        g.pose().pushPose();
        g.pose().translate(w / 2f + jx, y + ph / 2f, 0);
        float sc = (0.85f + 0.15f * in) * (1 - 0.12f * out);
        g.pose().scale(sc, sc, 1);
        g.pose().translate(-w / 2f, -(y + ph / 2f), 0);
        if (glitch > 0.15f) {
            // colour-split ghosts of the frame
            Shapes.box(x - 3 * glitch, y, pw, ph).radius(8).fill(0).border(1, Gfx.alpha(0xFF30E0FF, 0.35f * glitch)).draw(g);
            Shapes.box(x + 3 * glitch, y, pw, ph).radius(8).fill(0).border(1, Gfx.alpha(RED, 0.5f * glitch)).draw(g);
        }
        Shapes.box(x + 1, y + 3, pw - 2, ph - 2).radius(8).fill(0).glow(14, 0x80000000).draw(g);
        Shapes.box(x, y, pw, ph).radius(8).fillV(GLASS_TOP, GLASS_BOTTOM)
                .border(1f, Gfx.fade(RED, 0.95f), Gfx.fade(RED, 0.3f)).glow(10, Gfx.alpha(RED, 0.25f + 0.3f * beat)).draw(g);
        // frost along the bottom of the window: this one is from the Red Winter
        Shapes.box(x + 1, y + ph - 14, pw - 2, 13).radius(7).fillV(Gfx.alpha(ICE, 0f), Gfx.alpha(ICE, 0.08f)).draw(g);

        // header
        float hy = y + 10;
        Shapes.circle(x + 18, hy + 5, 8).fill(Gfx.alpha(RED, 0.25f)).border(0.8f, RED).glow(4 + 4 * beat, Gfx.alpha(RED, 0.6f)).draw(g);
        Shapes.icon(Icons.EXCLAIM, x + 18, hy + 5, 10).fill(PALE).draw(g);
        SysFont.spaced(g, tr("redgate.beyondsolo.warning.title"), x + 32, hy - 1, 11f, 0xFFFFFFFF, true, 2.6f, 0f);
        SysFont.spaced(g, tr("redgate.beyondsolo.warning.tag"), x + pw - 10, hy + 1.5f, 6.5f, RED_SOFT, true, 1.6f, 1f);
        float line = (pw - 20) * Anim.outCubic(Anim.window(age, 0.35f, 0.95f));
        Shapes.box(x + 10, hy + 17, line, 0.8f).fillH(RED, Gfx.alpha(RED, 0.05f)).glow(2, Gfx.alpha(RED, 0.5f)).draw(g);

        // what the System has to say, typed in line by line
        float ty = hy + 25;
        float tx = x + 12;
        typed(g, tr("redgate.beyondsolo.warning.turned"), tx, ty, 7.6f, SystemTheme.TEXT, age, 0.8f);
        typed(g, tr("redgate.beyondsolo.warning.sealed"), tx, ty + 12, 7f, SystemTheme.TEXT_SOFT, age, 1.6f);
        typed(g, Component.translatable("redgate.beyondsolo.warning.time", RedGateClient.rate).getString(), tx, ty + 23, 7f, SystemTheme.TEXT_SOFT, age, 2.4f);
        typed(g, Component.translatable("redgate.beyondsolo.warning.goal", RedGateClient.boss).getString(), tx, ty + 37, 8f, EMBER, age, 3.3f);
        if (release) {
            typed(g, Component.translatable("redgate.beyondsolo.warning.release", RedGateClient.releaseDay).getString(), tx, ty + 49, 7f, SystemTheme.TEXT_SOFT, age, 4.3f);
        }

        // the way out: a ring counting down while it is open, a lock snapping shut as it seals
        seal(g, x + pw - 30, hy + 22 + (ph - 32) / 2f, age, partial);
        g.pose().popPose();
        Gfx.setAlpha(old);
    }

    private static void seal(GuiGraphics g, float cx, float cy, float age, float partial) {
        float k = Anim.outBack(Anim.window(age, 0.9f, 1.4f));
        if (k <= 0.01f) return;
        float old = Gfx.fade(Mth.clamp(k, 0, 1));
        boolean sealed = RedGateClient.phase != RedGatePayload.OPEN;
        Shapes.circle(cx, cy, 15).fill(0xC00E0204).draw(g);
        if (!sealed) {
            float left = RedGateClient.sealSeconds(partial);
            float frac = Mth.clamp(left / Math.max(1, RedGateClient.sealTotal), 0, 1);
            float sub = left - (float) Math.floor(left);
            float tick = sub > 0.8f ? (sub - 0.8f) / 0.2f : 0;
            Shapes.ring(cx, cy, 13.5f, 1.8f).fill(Gfx.alpha(RED, 0.16f)).draw(g);
            Shapes.ring(cx, cy, 13.5f, 1.8f).fill(RED).progress(frac).glow(3, Gfx.alpha(RED, 0.7f)).draw(g);
            Shapes.ring(cx, cy, 17.5f, 0.6f).fill(Gfx.alpha(RED, 0.55f)).dashes(12, 0.45f).rotation(Anim.loop(6f)).draw(g);
            Shapes.icon(Icons.CHAIN, cx, cy - 3.5f, 9 + 2 * tick).fill(Gfx.mix(PALE, RED_SOFT, 0.3f)).draw(g);
            SysFont.draw(g, String.valueOf((int) Math.ceil(left)), cx, cy + 2.5f, 7f, 0xFFFFFFFF, true, 0.5f);
            SysFont.spaced(g, tr("redgate.beyondsolo.warning.sealing"), cx, cy + 19, 5f, RED_SOFT, true, 1.3f, 0.5f);
        } else {
            // sealed before this hunter got here: shut already
            float st = RedGateClient.sealedAt < RedGateClient.warningAt ? 9 : Anim.since(RedGateClient.sealedAt);
            float pop = Anim.outBack(Anim.window(st, 0f, 0.35f));
            if (st < 0.9f) {
                float b = st / 0.9f;
                Shapes.ring(cx, cy, 15 + 22 * Anim.outCubic(b), 2.4f * (1 - b) + 0.4f).fill(Gfx.alpha(RED, 0.9f * (1 - b))).draw(g);
            }
            Shapes.circle(cx, cy, 14).fillV(Gfx.alpha(RED, 0.55f), Gfx.alpha(RED_DEEP, 0.85f)).border(1.1f, RED)
                    .glow(5 + 8 * (1 - Mth.clamp(st, 0, 1)), Gfx.alpha(RED, 0.6f)).draw(g);
            Shapes.icon(Icons.LOCK, cx, cy, 13 * pop).fill(PALE).glow(3, Gfx.alpha(RED, 0.8f)).draw(g);
            SysFont.spaced(g, tr("redgate.beyondsolo.warning.sealed_tag"), cx, cy + 19, 5f, RED_SOFT, true, 1.3f, 0.5f);
        }
        Gfx.setAlpha(old);
    }

    // ================================================================ inside: the clock, the cold, the way

    private static float inside(GuiGraphics g, Minecraft mc, int w, float y, float partial, boolean passing) {
        boolean show = RedGateClient.inside() && !passing && Anim.since(RedGateClient.warningAt) > RedGateClient.WARNING_SECONDS - 0.5f;
        hudIn = Anim.approach(hudIn, show ? 1 : 0, show ? 3.5f : 4f);
        if (hudIn < 0.01f || !RedGateClient.inside()) return y;
        float in = Anim.outCubic(hudIn);
        y -= 14 * (1 - in);
        float old = Gfx.fade(in);
        boolean open = RedGateClient.phase == RedGatePayload.RELEASED || RedGateClient.flag(RedGatePayload.F_CLEARED);
        int c = open ? SystemTheme.ACCENT : RED;
        int day = RedGateClient.dayNow(partial);
        int tod = RedGateClient.timeOfDayNow(partial);
        boolean night = tod >= RedWorld.NIGHT_START && tod < RedWorld.NIGHT_END;

        String label = Component.translatable("redgate.beyondsolo.hud.day", day).getString().toUpperCase(Locale.ROOT);
        String of = RedGateClient.releaseDay > 0 && RedGateClient.phase == RedGatePayload.SEALED ? " / " + RedGateClient.releaseDay : "";
        String state = open ? tr("redgate.beyondsolo.hud.open").toUpperCase(Locale.ROOT) : null;
        String clock = hhmm(tod);
        String rate = "×" + RedGateClient.rate;
        String cold = tr("redgate.beyondsolo.hud.cold").toUpperCase(Locale.ROOT);
        String camps = RedGateClient.camps + "/" + RedGateClient.campsTotal;
        String kills = String.valueOf(RedGateClient.kills);

        float labelW = SysFont.spacedWidth(label, 5.4f, true, 1.3f) + (of.isEmpty() ? 0 : SysFont.width(of, 5.4f, true) + 1)
                + (state == null ? 0 : 6 + SysFont.spacedWidth(state, 5.4f, true, 1.3f));
        float clockW = SysFont.width(clock, 10f, true) + 4 + SysFont.width(rate, 5.6f, true) + 6;
        float blockW = Math.max(labelW, clockW);
        float coldW = 42;
        float statsW = 11 + SysFont.width(camps, 6.4f, true) + 9 + 11 + SysFont.width(kills, 6.4f, true);
        float bh = 26;
        float bw = 26 + blockW + 10 + coldW + 10 + statsW + 12;
        float x = (w - bw) / 2f;
        Shapes.box(x, y, bw, bh).radius(bh / 2f).fillV(GLASS_TOP, GLASS_BOTTOM)
                .border(0.8f, Gfx.alpha(c, 0.85f), Gfx.alpha(c, 0.3f)).glow(7, Gfx.alpha(c, 0.22f)).draw(g);

        // the dial: the sun or the moon, turning with the inside day
        float dx = x + 13, dy = y + bh / 2f;
        float dayFrac = tod / 24000f;
        Shapes.circle(dx, dy, 9).fillV(Gfx.alpha(night ? RED_DEEP : 0xFF5A3A12, 0.75f), 0xC0080204).border(0.6f, Gfx.alpha(c, 0.7f)).draw(g);
        Shapes.ring(dx, dy, 10.6f, 0.9f).fill(Gfx.alpha(c, 0.18f)).draw(g);
        Shapes.ring(dx, dy, 10.6f, 0.9f).fill(Gfx.alpha(c, 0.85f)).progress(dayFrac).draw(g);
        if (night) {
            Shapes.icon(Icons.MOON, dx, dy, 10).fill(Gfx.mix(RED_SOFT, 0xFFFFFFFF, 0.35f)).glow(3, Gfx.alpha(RED, 0.6f)).draw(g);
        } else {
            Shapes.rays(dx, dy, 7.5f, 8).fill(Gfx.alpha(0xFFFFD27A, 0.55f)).rotation(Anim.loop(20f)).draw(g);
            Shapes.circle(dx, dy, 3.4f).fill(0xFFFFE6A8).glow(3, Gfx.alpha(0xFFFFB347, 0.8f)).draw(g);
        }

        // the day and the clock
        float tx = x + 26;
        float lx = tx;
        SysFont.spaced(g, label, lx, y + 4, 5.4f, Gfx.mix(c, 0xFFFFFFFF, 0.45f), true, 1.3f, 0f);
        lx += SysFont.spacedWidth(label, 5.4f, true, 1.3f);
        if (!of.isEmpty()) {
            SysFont.draw(g, of, lx + 1, y + 4, 5.4f, SystemTheme.TEXT_DIM, true, 0f);
            lx += SysFont.width(of, 5.4f, true) + 1;
        }
        if (state != null) {
            SysFont.spaced(g, state, lx + 6, y + 4, 5.4f, Gfx.mix(SystemTheme.ACCENT, 0xFFFFFFFF, 0.3f), true, 1.3f, 0f);
        }
        SysFont.draw(g, clock, tx, y + 11.5f, 10f, 0xFFFFFFFF, true, 0f);
        float rx = tx + SysFont.width(clock, 10f, true) + 4;
        float rw = SysFont.width(rate, 5.6f, true) + 6;
        Shapes.pill(rx, y + 13.5f, rw, 8).fill(Gfx.alpha(c, 0.18f)).border(0.6f, Gfx.alpha(c, 0.6f)).draw(g);
        SysFont.draw(g, rate, rx + rw / 2f, y + 14.6f, 5.6f, Gfx.mix(c, 0xFFFFFFFF, 0.5f), true, 0.5f);

        // the cold
        float cx = tx + blockW + 10;
        Shapes.box(cx - 5, y + 5, 0.7f, bh - 10).fill(Gfx.alpha(c, 0.3f)).draw(g);
        float chill = RedGateClient.chill();
        boolean warm = RedGateClient.flag(RedGatePayload.F_WARM);
        boolean freezing = chill >= 0.7f;
        float blink = freezing ? 0.5f + 0.5f * Anim.pulse(0.7f) : 0;
        int cc = warm ? EMBER : Gfx.mix(ICE, 0xFFFFFFFF, 0.6f * blink);
        Shapes.icon(warm ? Icons.FLAME : Icons.SNOWFLAKE, cx + 5, y + bh / 2f, 9 + 1.5f * blink).fill(cc)
                .glow(2 + 3 * blink, Gfx.alpha(warm ? EMBER : ICE, 0.6f)).draw(g);
        SysFont.spaced(g, cold, cx + 12, y + 5, 4.8f, warm ? Gfx.mix(EMBER, 0xFFFFFFFF, 0.3f) : SystemTheme.TEXT_SOFT, true, 1.1f, 0f);
        float barX = cx + 12, barW = coldW - 14, barY = y + 14;
        Shapes.pill(barX, barY, barW, 3.6f).fill(0x8C0A1626).border(0.4f, Gfx.alpha(ICE, 0.3f)).draw(g);
        if (chill > 0.01f) {
            Shapes.pill(barX, barY, Math.max(3.6f, barW * chill), 3.6f).fillH(0xFF5FB7F0, freezing ? 0xFFFFFFFF : ICE)
                    .glow(2 + 2 * blink, Gfx.alpha(ICE, 0.6f)).sheen(Anim.loop(2.4f) * 1.6f - 0.3f, 0.25f).draw(g);
        }

        // camps taken, creatures slain
        float sx = cx + coldW + 10;
        Shapes.box(sx - 5, y + 5, 0.7f, bh - 10).fill(Gfx.alpha(c, 0.3f)).draw(g);
        float my = SysFont.centerY(y, bh, 6.4f);
        Shapes.icon(Icons.FLAG, sx + 4, y + bh / 2f, 8).fill(SystemTheme.GOLD).glow(2, Gfx.alpha(SystemTheme.GOLD, 0.4f)).draw(g);
        SysFont.draw(g, camps, sx + 10, my, 6.4f, SystemTheme.TEXT, true, 0f);
        float kx = sx + 11 + SysFont.width(camps, 6.4f, true) + 9;
        float pop = Anim.since(RedGateClient.killPop) < 0.35f ? 1 - Anim.since(RedGateClient.killPop) / 0.35f : 0;
        Shapes.icon(Icons.SKULL, kx + 4, y + bh / 2f, 8 + 2 * pop).fill(Gfx.mix(SystemTheme.TEXT_SOFT, RED_SOFT, pop)).draw(g);
        SysFont.draw(g, kills, kx + 10, my, 6.4f + pop, SystemTheme.TEXT, true, 0f);
        float below = y + bh + 4;
        float yNext = compass(g, mc, w, below, partial, open, in);
        Gfx.setAlpha(old);
        return y + (yNext - y) * in;
    }

    /** The way to its master (or out) on a compass under the clock. Returns the y under it. */
    private static float compass(GuiGraphics g, Minecraft mc, int w, float y, float partial, boolean open, float in) {
        float sealT = RedGateClient.phase == RedGatePayload.OPEN ? RedGateClient.sealSeconds(partial) : 0;
        if (RedGateClient.flag(RedGatePayload.F_NO_TARGET) || mc.player == null) return sealChip(g, w, y, sealT);
        int c = open ? SystemTheme.ACCENT : RED;
        float bw = Math.min(w - 40, 196), bh = 11;
        float x = (w - bw) / 2f;
        Shapes.pill(x, y, bw, bh).fillV(0xA8160407, 0xA80A0203).border(0.6f, Gfx.alpha(c, 0.45f), Gfx.alpha(c, 0.15f)).draw(g);
        float left = x + 8, right = x + bw - 8;
        float cx = (left + right) / 2f, half = (right - left) / 2f;
        float yaw = mc.player.getViewYRot(partial);
        for (int a = -180; a < 180; a += 15) {
            float rel = Mth.wrapDegrees(a - yaw);
            if (Math.abs(rel) > SPAN) continue;
            float px = cx + rel / SPAN * half;
            float edge = 1 - Anim.clamp01((Math.abs(rel) - SPAN * 0.65f) / (SPAN * 0.35f));
            if (a % 90 == 0) {
                SysFont.draw(g, cardinal(a), px, SysFont.centerY(y, bh, 5f), 5f, Gfx.alpha(SystemTheme.TEXT_SOFT, 0.9f * edge), true, 0.5f);
            } else {
                float th = a % 45 == 0 ? 4.5f : 2.5f;
                Shapes.box(px - 0.4f, y + (bh - th) / 2f, 0.8f, th).fill(Gfx.alpha(SystemTheme.TEXT_DIM, 0.7f * edge)).draw(g);
            }
        }
        Shapes.box(cx - 0.5f, y - 2.5f, 1, 3.5f).fill(Gfx.alpha(Gfx.mix(c, 0xFFFFFFFF, 0.5f), 0.9f)).draw(g);
        // the target
        double dx = RedGateClient.targetX + 0.5 - mc.player.getX(), dz = RedGateClient.targetZ + 0.5 - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        float rel = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)) - yaw);
        boolean edge = Math.abs(rel) > SPAN;
        float px = cx + Mth.clamp(rel / SPAN, -1, 1) * half;
        float my = y + bh / 2f;
        boolean awake = RedGateClient.flag(RedGatePayload.F_BOSS_AWAKE) && !open;
        if (awake || dist < 30) {
            float ring = Anim.loop(1.2f);
            Shapes.ring(px, my, 5 + 5 * ring, 0.8f).fill(Gfx.alpha(c, 0.8f * (1 - ring))).draw(g);
        }
        g.pose().pushPose();
        g.pose().translate(px, my, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(45));
        Shapes.box(-4.4f, -4.4f, 8.8f, 8.8f).radius(1.6f).fill(Gfx.alpha(0xFF0C0204, 0.92f)).border(0.8f, c).glow(3.5f, Gfx.alpha(c, 0.6f)).draw(g);
        g.pose().popPose();
        ResourceLocation icon = open ? Icons.SPIRAL : Icons.CROWN;
        Shapes.icon(icon, px, my + 0.2f, 6.4f).fill(Gfx.mix(c, 0xFFFFFFFF, 0.55f)).draw(g);
        if (edge) {
            float side = rel > 0 ? 1 : -1;
            segment(g, px + side * 7.5f, my - 2.5f, px + side * 10f, my, 1f, c);
            segment(g, px + side * 10f, my, px + side * 7.5f, my + 2.5f, 1f, c);
        }
        // what it is and how far, under the marker
        String what = tr(open ? "redgate.beyondsolo.hud.way_out" : "redgate.beyondsolo.hud.master");
        String far = Component.translatable("break.beyondsolo.hud.distance", Math.round(dist)).getString();
        float ww = SysFont.width(what, 4.8f, true), fw = SysFont.width(far, 4.8f, true);
        float lw = ww + 4 + fw;
        float lx = Mth.clamp(px - lw / 2f, x, x + bw - lw);
        float ly = y + bh + 2.5f;
        Shapes.pill(lx - 3, ly - 1.2f, lw + 6, 7.4f).fill(Gfx.alpha(0xFF0C0204, 0.6f)).draw(g);
        SysFont.draw(g, what, lx, ly, 4.8f, Gfx.mix(c, 0xFFFFFFFF, 0.4f), true, 0f);
        SysFont.draw(g, far, lx + ww + 4, ly, 4.8f, SystemTheme.TEXT_SOFT, true, 0f);
        return sealChip(g, w, y + bh + 12, sealT);
    }

    /** While the gate is still open: how long until it seals. */
    private static float sealChip(GuiGraphics g, int w, float y, float seconds) {
        if (seconds <= 0) return y;
        String s = Component.translatable("redgate.beyondsolo.hud.seals_in", (int) Math.ceil(seconds)).getString().toUpperCase(Locale.ROOT);
        float sw = SysFont.spacedWidth(s, 5.2f, true, 1.2f) + 22;
        float blink = 0.5f + 0.5f * Anim.pulse(0.6f);
        Shapes.pill((w - sw) / 2f, y, sw, 11).fill(Gfx.alpha(0xFF2A0508, 0.9f)).border(0.8f, Gfx.alpha(RED, 0.5f + 0.5f * blink)).glow(4, Gfx.alpha(RED, 0.4f * blink)).draw(g);
        Shapes.icon(Icons.CHAIN, (w - sw) / 2f + 8, y + 5.5f, 7).fill(RED_SOFT).draw(g);
        SysFont.spaced(g, s, w / 2f + 5, y + 3, 5.2f, PALE, true, 1.2f, 0.5f);
        return y + 15;
    }

    // ================================================================ outside: a red gate in the world

    private static float outside(GuiGraphics g, int w, float y, boolean passing) {
        boolean show = RedGateClient.outside() && !passing;
        outIn = Anim.approach(outIn, show ? 1 : 0, show ? 4f : 3f);
        if (outIn < 0.01f || RedGateClient.mode != RedGatePayload.OUTSIDE && !show && outIn < 0.5f) return y;
        float in = Anim.outCubic(outIn);
        y -= 12 * (1 - in);
        float old = Gfx.fade(in);
        int phase = RedGateClient.phase;
        PlayerRank rank = rank(RedGateClient.rank);
        String head = tr("redgate.beyondsolo.hud.red_gate").toUpperCase(Locale.ROOT);
        String state = switch (phase) {
            case RedGatePayload.OPEN -> Component.translatable("redgate.beyondsolo.hud.seals_in", (int) Math.ceil(RedGateClient.sealSeconds(0))).getString();
            case RedGatePayload.SEALED -> tr("redgate.beyondsolo.hud.sealed");
            default -> tr("redgate.beyondsolo.hud.open");
        };
        state = state.toUpperCase(Locale.ROOT);
        int tod = RedGateClient.time;
        boolean night = tod >= RedWorld.NIGHT_START && tod < RedWorld.NIGHT_END;
        String line = Component.translatable("redgate.beyondsolo.hud.outside", RedGateClient.party, RedGateClient.day, hhmm(tod)).getString();
        float headW = SysFont.spacedWidth(head, 5.6f, true, 1.4f) + 6 + SysFont.spacedWidth(state, 5.6f, true, 1.4f);
        float bw = 24 + Math.max(headW, SysFont.width(line, 5.4f, false) + 10) + 14;
        float bh = 22;
        float x = (w - bw) / 2f;
        float blink = phase == RedGatePayload.OPEN ? 0.5f + 0.5f * Anim.pulse(0.6f) : 0.5f + 0.5f * Anim.pulse(2.4f);
        Shapes.box(x, y, bw, bh).radius(bh / 2f).fillV(GLASS_TOP, GLASS_BOTTOM)
                .border(0.8f, Gfx.alpha(RED, 0.55f + 0.4f * blink), Gfx.alpha(RED, 0.25f)).glow(4 + 4 * blink, Gfx.alpha(RED, 0.2f + 0.12f * blink)).draw(g);
        float ix = x + 11, iy = y + bh / 2f;
        Shapes.circle(ix, iy, 7).fillV(Gfx.alpha(RED, 0.35f), Gfx.alpha(RED_DEEP, 0.6f)).border(0.6f, RED).draw(g);
        Shapes.ring(ix, iy, 8.6f, 0.55f).fill(Gfx.alpha(RED, 0.7f)).dashes(8, 0.5f).rotation(Anim.loop(5f)).draw(g);
        Shapes.icon(Icons.of(rank), ix, iy, 8.5f).fill(PALE).glow(2, Gfx.alpha(RED, 0.6f)).draw(g);
        float tx = x + 23;
        SysFont.spaced(g, head, tx, y + 4, 5.6f, Gfx.mix(RED, 0xFFFFFFFF, 0.4f), true, 1.4f, 0f);
        SysFont.spaced(g, state, tx + SysFont.spacedWidth(head, 5.6f, true, 1.4f) + 6, y + 4, 5.6f,
                phase == RedGatePayload.RELEASED ? Gfx.mix(SystemTheme.ACCENT, 0xFFFFFFFF, 0.3f) : PALE, true, 1.4f, 0f);
        Shapes.icon(night ? Icons.MOON : Icons.HOURGLASS, tx + 3, y + 15, 6.5f).fill(night ? RED_SOFT : SystemTheme.TEXT_DIM).draw(g);
        SysFont.draw(g, line, tx + 9, y + 12.5f, 5.4f, SystemTheme.TEXT_SOFT, false, 0f);
        Gfx.setAlpha(old);
        return y + (bh + 4) * in;
    }

    // ================================================================ banners

    private static void banner(GuiGraphics g, int w, float y, RedGateClient.Banner b) {
        float age = b.age();
        float life = b.duration();
        String title, sub = "";
        int color;
        ResourceLocation icon;
        switch (b.event) {
            case RedGatePayload.EV_SEALED -> {
                title = tr("redgate.beyondsolo.banner.sealed");
                sub = tr("redgate.beyondsolo.banner.sealed.sub");
                color = RED;
                icon = Icons.LOCK;
            }
            case RedGatePayload.EV_TURNED -> {
                title = tr("redgate.beyondsolo.banner.turned");
                sub = tr("redgate.beyondsolo.banner.turned.sub");
                color = RED;
                icon = Icons.EXCLAIM;
            }
            case RedGatePayload.EV_NIGHT -> {
                title = tr("redgate.beyondsolo.banner.night");
                sub = tr("redgate.beyondsolo.banner.night.sub");
                color = RED_SOFT;
                icon = Icons.MOON;
            }
            case RedGatePayload.EV_DAWN -> {
                title = Component.translatable("redgate.beyondsolo.banner.dawn", b.a).getString();
                sub = tr("redgate.beyondsolo.banner.dawn.sub");
                color = EMBER;
                icon = Icons.STAR;
            }
            case RedGatePayload.EV_RAID -> {
                title = tr("redgate.beyondsolo.banner.raid");
                sub = tr("redgate.beyondsolo.banner.raid.sub");
                color = RED;
                icon = Icons.SKULL;
            }
            case RedGatePayload.EV_BLIZZARD -> {
                title = tr("redgate.beyondsolo.banner.blizzard");
                sub = tr("redgate.beyondsolo.banner.blizzard.sub");
                color = ICE;
                icon = Icons.SNOWFLAKE;
            }
            case RedGatePayload.EV_CALM -> {
                title = tr("redgate.beyondsolo.banner.calm");
                color = ICE;
                icon = Icons.SNOWFLAKE;
            }
            case RedGatePayload.EV_CAMP -> {
                title = tr("redgate.beyondsolo.banner.camp");
                sub = Component.translatable("redgate.beyondsolo.banner.camp.sub", b.a, b.b).getString();
                color = SystemTheme.GOLD;
                icon = Icons.FLAG;
            }
            case RedGatePayload.EV_RELEASED -> {
                title = tr("redgate.beyondsolo.banner.released");
                sub = tr("redgate.beyondsolo.banner.released.sub");
                color = SystemTheme.ACCENT;
                icon = Icons.SPIRAL;
            }
            case RedGatePayload.EV_CLEARED -> {
                title = tr("redgate.beyondsolo.banner.cleared");
                color = SystemTheme.ACCENT;
                icon = Icons.SPIRAL;
            }
            case RedGatePayload.EV_EDGE -> {
                title = tr("redgate.beyondsolo.banner.edge");
                color = ICE;
                icon = Icons.SNOWFLAKE;
            }
            case RedGatePayload.EV_FREEZING -> {
                title = tr("redgate.beyondsolo.banner.freezing");
                sub = tr("redgate.beyondsolo.banner.freezing.sub");
                color = ICE;
                icon = Icons.SNOWFLAKE;
            }
            default -> {
                return;
            }
        }
        title = title.toUpperCase(Locale.ROOT);
        float in = Anim.outBack(Anim.window(age, 0, 0.35f));
        float out = 1 - Anim.window(age, life - 0.4f, life);
        float a = Mth.clamp(in, 0, 1) * out;
        if (a <= 0.01f) return;
        float old = Gfx.fade(a);
        boolean big = !sub.isEmpty();
        float size = big ? 9.5f : 7.2f;
        float tw = SysFont.spacedWidth(title, size, true, 2f);
        float bw = Math.max(tw + 22, big ? SysFont.width(sub, 6.4f, false) + 14 : 0) + 30;
        float bh = big ? 27 : 15;
        float sx = 0.7f + 0.3f * in;
        g.pose().pushPose();
        g.pose().translate(w / 2f, y + bh / 2f, 0);
        g.pose().scale(sx, 1, 1);
        g.pose().translate(-w / 2f, -(y + bh / 2f), 0);
        Shapes.box((w - bw) / 2f, y, bw, bh).radius(bh / 2f).fill(0xD8140406).border(0.8f, Gfx.alpha(color, 0.9f)).glow(8, Gfx.alpha(color, 0.35f)).draw(g);
        g.pose().popPose();
        float tx = w / 2f + 11;
        float iy = y + (big ? 8.5f : bh / 2f);
        Shapes.icon(icon, tx - tw / 2f - 12, iy, big ? 9 : 7.5f).fill(Gfx.mix(color, 0xFFFFFFFF, 0.3f)).glow(3, Gfx.alpha(color, 0.6f)).draw(g);
        SysFont.spaced(g, title, tx, y + (big ? 4 : 4), size, Gfx.mix(color, 0xFFFFFFFF, 0.4f), true, 2f, 0.5f);
        if (big) SysFont.draw(g, sub, w / 2f, y + 16.5f, 6.4f, SystemTheme.TEXT_SOFT, false, 0.5f);
        Gfx.setAlpha(old);
    }

    // ================================================================ back home: the card

    private static void summary(GuiGraphics g, Minecraft mc, int w, int h) {
        int s = RedGateClient.summary;
        if (s == RedGatePayload.EV_NONE) return;
        if (RedGateClient.summaryAt < 0) {
            // after the way back (or the death screen) has played out
            if (mc.screen != null || GateOverlay.active() || RedGateClient.inside()) return;
            RedGateClient.summaryAt = Anim.now();
        }
        float age = Anim.since(RedGateClient.summaryAt);
        float total = RedGateClient.SUMMARY_SECONDS;
        if (age > total) {
            RedGateClient.summary = RedGatePayload.EV_NONE;
            return;
        }
        boolean cleared = s == RedGatePayload.EV_SUMMARY_CLEARED, fallen = s == RedGatePayload.EV_SUMMARY_FAILED;
        int c = cleared ? SystemTheme.GOLD : fallen ? RED : SystemTheme.ACCENT;
        float in = Anim.outBack(Anim.window(age, 0f, 0.45f));
        float out = Anim.window(age, total - 0.5f, total);
        float a = Mth.clamp(in, 0, 1) * (1 - out);
        if (a <= 0.01f) return;
        String tag = tr("redgate.beyondsolo.summary.tag").toUpperCase(Locale.ROOT);
        String title = tr(cleared ? "redgate.beyondsolo.summary.cleared" : fallen ? "redgate.beyondsolo.summary.fallen" : "redgate.beyondsolo.summary.escaped")
                .toUpperCase(Locale.ROOT);
        String place = Component.translatable("redgate.beyondsolo.summary.place", rank(RedGateClient.sRank).displayName(),
                rank(RedGateClient.sInside).displayName()).getString();
        String[][] rows = {
                {tr("redgate.beyondsolo.summary.days"), String.valueOf(RedGateClient.sDay)},
                {tr("redgate.beyondsolo.summary.kills"), String.valueOf(RedGateClient.sKills)},
                {tr("redgate.beyondsolo.summary.camps"), RedGateClient.sCamps + "/" + RedGateClient.sCampsTotal},
                {tr("redgate.beyondsolo.summary.xp"), RedGateClient.sXp > 0 ? "+" + String.format(Locale.ROOT, "%,d", RedGateClient.sXp) : "-"}};
        float pw = 220, ph = cleared ? 108 : 96;
        float x = (w - pw) / 2f, y = h * 0.2f;
        float old = Gfx.fade(a);
        g.pose().pushPose();
        g.pose().translate(w / 2f, y + ph / 2f, 0);
        g.pose().scale(1, Math.max(0.02f, in), 1);
        g.pose().translate(-w / 2f, -(y + ph / 2f), 0);
        Ui.panel(g, x, y, pw, ph, 9, c, 1f);
        g.pose().popPose();
        if (in > 0.85f) {
            float tagW = SysFont.spacedWidth(tag, 5.6f, true, 1.6f) + 14;
            Shapes.pill(w / 2f - tagW / 2f, y - 5.5f, tagW, 11).fill(Gfx.alpha(0xFF14060A, 0.92f)).border(0.75f, Gfx.alpha(RED, 0.85f)).glow(5, Gfx.alpha(RED, 0.4f)).draw(g);
            SysFont.spaced(g, tag, w / 2f, y - 2.3f, 5.6f, PALE, true, 1.6f, 0.5f);
            float tk = Anim.outCubic(Anim.window(age, 0.3f, 0.8f));
            float o2 = Gfx.fade(tk);
            Ui.iconBadge(g, cleared ? Icons.CROWN : fallen ? Icons.SKULL : Icons.RUN, x + 22, y + 21, 9, c);
            SysFont.spaced(g, title, x + 38, y + 12, 12f, Gfx.mix(c, 0xFFFFFFFF, 0.35f), true, 2.4f, 0f);
            SysFont.draw(g, place, x + 38, y + 25, 6.2f, SystemTheme.TEXT_SOFT, false, 0f);
            Gfx.setAlpha(o2);
            Ui.divider(g, x + 14, y + 36, (pw - 28) * Anim.outCubic(Anim.window(age, 0.5f, 1.0f)), Gfx.alpha(c, 0.7f));
            float ry = y + 42;
            for (int i = 0; i < rows.length; i++) {
                float rk = Anim.outCubic(Anim.window(age, 0.7f + i * 0.18f, 1.0f + i * 0.18f));
                if (rk <= 0.01f) continue;
                float o3 = Gfx.fade(rk);
                float rx = x + 16 + (i % 2) * (pw / 2f - 8);
                float yy = ry + (i / 2) * 22 + 4 * (1 - rk);
                SysFont.draw(g, rows[i][0].toUpperCase(Locale.ROOT), rx, yy, 5f, SystemTheme.TEXT_DIM, true, 0f);
                SysFont.draw(g, rows[i][1], rx, yy + 7, 9f, i == 3 && RedGateClient.sXp > 0 ? SystemTheme.GOLD : 0xFFFFFFFF, true, 0f);
                Gfx.setAlpha(o3);
            }
            if (cleared) {
                float bk = Anim.outCubic(Anim.window(age, 1.5f, 2.0f));
                if (bk > 0.01f) {
                    float o4 = Gfx.fade(bk);
                    String fell = Component.translatable("redgate.beyondsolo.summary.master", RedGateClient.sBoss).getString();
                    SysFont.draw(g, SysFont.fit(fell, 6.2f, false, pw - 28), w / 2f, y + ph - 13, 6.2f, Gfx.mix(SystemTheme.GOLD, 0xFFFFFFFF, 0.3f), false, 0.5f);
                    Gfx.setAlpha(o4);
                }
            }
        }
        Gfx.setAlpha(old);
    }

    // ================================================================ helpers

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private static PlayerRank rank(int ordinal) {
        return PlayerRank.values()[Mth.clamp(ordinal, 0, PlayerRank.values().length - 1)];
    }

    /** The inside clock as a time of day (0 ticks is six in the morning). */
    static String hhmm(int ticks) {
        int t = Math.floorMod(ticks, 24000);
        int hour = (t / 1000 + 6) % 24;
        int min = (t % 1000) * 60 / 1000;
        return String.format(Locale.ROOT, "%02d:%02d", hour, min);
    }

    /** Text that types itself in, with a red caret while it types. */
    private static void typed(GuiGraphics g, String s, float x, float y, float size, int color, float age, float from) {
        if (age < from) return;
        SysFont.Line line = SysFont.line(s, size, false);
        int chars = (int) Math.min(SysFont.length(line), (age - from) * 48f);
        SysFont.draw(g, SysFont.truncate(line, chars), x, y, color, 0f);
        if (chars < SysFont.length(line) && (int) (Anim.now() * 6) % 2 == 0) {
            float cw = SysFont.truncate(line, chars).width();
            Shapes.box(x + cw + 1, y, 3, size * 0.8f).fill(RED).draw(g);
        }
    }

    private static void segment(GuiGraphics g, float x0, float y0, float x1, float y1, float th, int color) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 0.1f) return;
        g.pose().pushPose();
        g.pose().translate(x0, y0, 0);
        g.pose().mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
        Shapes.box(0, -th / 2f, len + th * 0.5f, th).fill(Gfx.mix(color, 0xFFFFFFFF, 0.45f)).glow(2.5f, color).draw(g);
        g.pose().popPose();
    }

    /** The letter for a cardinal yaw (Minecraft's: 0 faces south, 90 west). */
    private static String cardinal(int yaw) {
        String key = switch (Math.floorMod(yaw, 360)) {
            case 0 -> "s";
            case 90 -> "w";
            case 180 -> "n";
            default -> "e";
        };
        return tr("break.beyondsolo.hud.cardinal." + key);
    }

    static void reset() {
        hudIn = 0;
        outIn = 0;
        shownCold = 0;
    }

    private RedGateOverlay() {}
}
