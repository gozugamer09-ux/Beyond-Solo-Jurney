package com.beyondsolo.client.depths;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.evaluation.EvaluationOverlay;
import com.beyondsolo.client.gate.GateOverlay;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.key.RunHud;
import com.beyondsolo.client.quest.QuestOverlay;
import com.beyondsolo.client.skill.SkillOverlay;
import com.beyondsolo.depths.GateWeather;
import com.beyondsolo.depths.HiddenKind;
import com.beyondsolo.depths.TrapKind;
import com.beyondsolo.skill.SkillElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The depths on screen.
 * <ul>
 *   <li>The System's banners: the cave's weather on the way in (what it favours and dulls, what it pays), a trap
 *       springing (red, with the hint), a lockdown's waves, a trap beaten, a hidden dungeon found (violet, and the
 *       words glitch into place), and stepping into the chamber.</li>
 *   <li>At the top, under the boss bar and the run clock: the weather chip, and while a trap works on the player,
 *       the trap chip (break its core, or the wave count).</li>
 *   <li>Under the crosshair, on a creature whose weaknesses the player has found: WEAK and their elements.</li>
 * </ul>
 */
public final class DepthsHud {
    private static float stripIn;
    private static float trapIn;
    private static float weakIn;
    private static List<SkillElement> lastWeak = List.of();
    private static int lookId = -1;
    private static DepthsClient.Banner kicked;

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        int w = g.guiWidth(), h = g.guiHeight();
        // the System's banners take turns with the other moments in the middle of the screen
        boolean free = !GateOverlay.active() && !QuestOverlay.active() && !EvaluationOverlay.active() && !SkillOverlay.active();
        DepthsClient.Banner b = DepthsClient.banner(free, QuestOverlay.waiting());
        if (b != null) banner(g, w, h, b);
        strip(g, w);
        weaknesses(g, mc, w, h);
    }

    // ================================================================ banners

    private static void banner(GuiGraphics g, int w, int h, DepthsClient.Banner b) {
        float t = b.age();
        float exit = Anim.window(t, b.duration - 0.45f, b.duration);
        float live = 1 - Anim.outCubic(exit);
        if (kicked != b) {
            kicked = b;
            if (b.kind == DepthsClient.BannerKind.TRAP) GateOverlay.addTrauma(0.22f);
            if (b.kind == DepthsClient.BannerKind.HIDDEN) GateOverlay.addTrauma(0.12f);
        }
        switch (b.kind) {
            case HIDDEN -> hidden(g, w, h, b, t, live);
            case TRAP -> trap(g, w, h, b, t, live);
            case WAVE -> wave(g, w, h, b, t, live);
            default -> plain(g, w, h, b, t, live);
        }
    }

    /** The System's window: a tag over a glass panel with an icon, the title and a line under it. */
    private static void plain(GuiGraphics g, int w, int h, DepthsClient.Banner b, float t, float live) {
        int c = Gfx.opaque(b.color);
        String tag;
        net.minecraft.resources.ResourceLocation icon;
        switch (b.kind) {
            case WEATHER -> {
                tag = Component.translatable("depths.beyondsolo.hud.condition").getString();
                icon = DepthsIcons.weather(GateWeather.byIndex(b.which));
            }
            case HIDDEN_ENTER -> {
                tag = Component.translatable("depths.beyondsolo.hud.chamber").getString();
                icon = DepthsIcons.hidden(HiddenKind.byIndex(b.which));
            }
            default -> {
                tag = b.title.getString().toUpperCase(Locale.ROOT);
                icon = DepthsIcons.CHECK;
            }
        }
        Component title = b.kind == DepthsClient.BannerKind.TRAP_DONE ? b.subtitle : b.title;
        Component line = b.kind == DepthsClient.BannerKind.TRAP_DONE ? Component.empty() : b.subtitle;
        float titleSize = 11f, lineSize = 6.4f;
        float tw = SysFont.width(title, titleSize, true);
        List<SysFont.Line> lines = line.getString().isEmpty() ? List.of() : SysFont.wrap(line, lineSize, false, 230);
        float lw = 0;
        for (SysFont.Line l : lines) lw = Math.max(lw, l.width());
        List<String[]> effects = b.kind == DepthsClient.BannerKind.WEATHER ? effects(GateWeather.byIndex(b.which)) : List.of();
        float ew = effectsWidth(effects);
        float pw = Math.max(170, Math.max(tw, Math.max(lw, ew)) + 50);
        float ph = 30 + lines.size() * lineSize * SysFont.LINE + (effects.isEmpty() ? 0 : 17);
        float cx = w / 2f, y = h * 0.24f;
        float open = Anim.outBack(Anim.window(t, 0f, 0.45f));
        float old = Gfx.fade(live);
        Shapes.vignette(0, 0, w, h, 0.35f, 1.5f).fill(Gfx.alpha(c, 0.12f * (1 - Anim.window(t, 0.4f, 2.2f)))).draw(g);
        g.pose().pushPose();
        g.pose().translate(cx, y + ph / 2f, 0);
        g.pose().scale(1, Math.max(0.02f, open), 1);
        Ui.panel(g, -pw / 2f, -ph / 2f, pw, ph, 9, c, 1f);
        g.pose().popPose();
        if (open > 0.9f) {
            float k = Anim.outCubic(Anim.window(t, 0.35f, 0.8f));
            float o2 = Gfx.fade(k);
            // the tag on the top edge
            float tagW = SysFont.spacedWidth(tag, 5.6f, true, 1.6f) + 14;
            Shapes.pill(cx - tagW / 2f, y - 5.5f, tagW, 11).fill(Gfx.alpha(0xFF060A14, 0.9f)).border(0.75f, Gfx.alpha(c, 0.85f)).glow(5, Gfx.alpha(c, 0.4f)).draw(g);
            SysFont.spaced(g, tag, cx, y - 2.3f, 5.6f, Gfx.mix(c, 0xFFFFFFFF, 0.55f), true, 1.6f, 0.5f);
            // icon, title, line
            float left = cx - pw / 2f + 18;
            Ui.iconBadge(g, icon, left, y + 15, 8.5f, c);
            float textX = left + 16;
            float textW = pw - 50;
            typed(g, title, textX + textW / 2f, y + 9, titleSize, 0xFFFFFFFF, Anim.window(t, 0.4f, 1.1f));
            float ly = y + 23;
            for (SysFont.Line l : lines) {
                SysFont.draw(g, l, textX + (textW - l.width()) / 2f, ly, Gfx.alpha(SystemTheme.TEXT_SOFT, Anim.window(t, 0.9f, 1.3f)), 0f);
                ly += lineSize * SysFont.LINE;
            }
            if (!effects.isEmpty()) {
                float ek = Anim.outBack(Anim.window(t, 1.2f, 1.6f));
                if (ek > 0.01f) drawEffects(g, effects, textX + textW / 2f - ew / 2f, ly + 1.5f, ek);
            }
            Gfx.setAlpha(o2);
        }
        Gfx.setAlpha(old);
    }

    /** What a weather does, as chips: {text, colour, icon element ordinal or -1}. */
    private static List<String[]> effects(GateWeather w) {
        List<String[]> out = new ArrayList<>();
        if (w.boosted != null) {
            out.add(new String[]{"+" + Math.round(w.boost * 100) + "% " + w.boosted.displayName().getString(), String.valueOf(w.boosted.color()), String.valueOf(w.boosted.ordinal())});
        }
        if (w.dulled != null) {
            out.add(new String[]{"-" + Math.round(w.dull * 100) + "% " + w.dulled.displayName().getString(), String.valueOf(0x9FB3C8), String.valueOf(w.dulled.ordinal())});
        }
        if (w.reward > 1f) {
            out.add(new String[]{Component.translatable("depths.beyondsolo.hud.reward", Math.round(w.reward * 100 - 100)).getString(),
                    String.valueOf(SystemTheme.GOLD & 0xFFFFFF), "-1"});
        }
        return out;
    }

    /** The size of the words on a weather banner's chips. */
    private static final float CHIP_TEXT = 6.1f;

    private static float effectsWidth(List<String[]> effects) {
        float w = 0;
        for (String[] e : effects) w += chipWidth(e) + 4;
        return Math.max(0, w - 4);
    }

    private static float chipWidth(String[] e) {
        return SysFont.width(e[0], CHIP_TEXT, true) + 11 + (Integer.parseInt(e[2]) >= 0 ? 9 : 0);
    }

    private static void drawEffects(GuiGraphics g, List<String[]> effects, float x, float y, float k) {
        for (String[] e : effects) {
            int color = Gfx.opaque(Integer.parseInt(e[1]));
            int element = Integer.parseInt(e[2]);
            float cw = chipWidth(e);
            g.pose().pushPose();
            g.pose().translate(x + cw / 2f, y + 5.5f, 0);
            g.pose().scale(k, k, 1);
            Shapes.pill(-cw / 2f, -5.5f, cw, 11).fill(Gfx.alpha(color, 0.18f)).border(0.7f, Gfx.alpha(color, 0.7f)).draw(g);
            float tx = -cw / 2f + 5.5f;
            if (element >= 0) {
                Shapes.icon(DepthsIcons.element(SkillElement.values()[element]), tx + 3.5f, 0, 7.2f).fill(Gfx.mix(color, 0xFFFFFFFF, 0.25f)).draw(g);
                tx += 9;
            }
            SysFont.draw(g, e[0], tx, -5.5f + SysFont.centerY(0, 11, CHIP_TEXT), CHIP_TEXT, Gfx.mix(color, 0xFFFFFFFF, 0.45f), true, 0f);
            g.pose().popPose();
            x += cw + 4;
        }
    }

    /** HIDDEN DUNGEON: violet light, and the words glitch into place over a flare of rays. */
    private static void hidden(GuiGraphics g, int w, int h, DepthsClient.Banner b, float t, float live) {
        int c = Gfx.opaque(b.color);
        HiddenKind kind = HiddenKind.byIndex(b.which);
        int kc = Gfx.opaque(kind.color);
        float cx = w / 2f, y = h * 0.25f;
        float pulse = 1 - Anim.window(t, 0f, 1.6f);
        Shapes.vignette(0, 0, w, h, 0.15f, 1.3f).fill(Gfx.alpha(c, (0.12f + 0.25f * pulse) * live)).draw(g);
        float old = Gfx.fade(live);
        float flare = Anim.outCubic(Anim.window(t, 0.05f, 0.7f));
        Shapes.rays(cx, y + 8, 40 + 130 * flare, 24).fill(Gfx.alpha(c, 0.22f * (1 - 0.5f * Anim.window(t, 1f, 3f)))).rotation(t * 0.04f).additive().draw(g);
        Shapes.circle(cx, y + 8, 30 + 20 * flare).fill(0).glow(36, Gfx.alpha(c, 0.3f * flare)).draw(g);
        // a band so the words read on anything
        float bw = Math.min(w * 0.85f, 360);
        Shapes.pill(cx - bw / 2f, y - 8, bw, 46).fill(Gfx.alpha(0xFF07040F, 0.5f * flare)).glow(20, Gfx.alpha(0xFF07040F, 0.35f * flare)).draw(g);
        // the words: torn copies in red and cyan jitter around the real ones and settle
        String word = b.title.getString().toUpperCase(Locale.ROOT);
        float size = 17f, track = 3.2f;
        float settle = Anim.window(t, 0.15f, 1.1f);
        float shown = Anim.clamp01(t / 0.2f);
        if (shown > 0) {
            float jitter = (1 - settle) * 6f;
            float seed = (float) Math.floor(t * 18);
            float jx = (noise(seed) - 0.5f) * jitter, jy = (noise(seed + 7) - 0.5f) * jitter * 0.4f;
            if (settle < 1) {
                SysFont.spaced(g, word, cx + jx - 1.5f, y + jy, size, Gfx.alpha(0xFFFF3B6B, 0.55f * (1 - settle)), true, track, 0.5f);
                SysFont.spaced(g, word, cx - jx + 1.5f, y - jy, size, Gfx.alpha(0xFF3BE8FF, 0.55f * (1 - settle)), true, track, 0.5f);
            }
            // blinks off for a frame now and then while it settles
            boolean blink = settle < 0.8f && noise(seed + 3) > 0.82f;
            if (!blink) SysFont.spaced(g, word, cx, y, size, Gfx.alpha(0xFFF2E8FF, shown), true, track, 0.5f);
            // a scanline sweeping down through it
            float scan = Anim.window(t, 0.1f, 0.9f);
            if (scan > 0 && scan < 1) {
                float sw = SysFont.spacedWidth(word, size, true, track) + 30;
                Shapes.box(cx - sw / 2f, y - 4 + scan * (size + 8), sw, 1.2f).fill(Gfx.alpha(c, 0.8f)).glow(5, Gfx.alpha(c, 0.6f)).draw(g);
            }
        }
        // the chamber's name, with the hidden mark
        float subIn = Anim.outCubic(Anim.window(t, 1.0f, 1.5f));
        if (subIn > 0.01f) {
            float o2 = Gfx.fade(subIn);
            float sw = SysFont.width(b.subtitle, 8f, true);
            float total = sw + 16;
            float sx = cx - total / 2f;
            Shapes.icon(DepthsIcons.hidden(kind), sx + 5, y + 28.5f, 9).fill(Gfx.mix(kc, 0xFFFFFFFF, 0.3f)).glow(3, Gfx.alpha(kc, 0.6f)).draw(g);
            SysFont.draw(g, b.subtitle, sx + 16, y + 25, 8f, Gfx.mix(kc, 0xFFFFFFFF, 0.35f), true, 0f);
            Ui.divider(g, cx - bw * 0.35f, y + 37, bw * 0.7f, Gfx.alpha(c, 0.7f * subIn));
            Gfx.setAlpha(o2);
        }
        Gfx.setAlpha(old);
    }

    /** A trap springs: the edges of the screen flash red, a warning tag blinks over its name and hint. */
    private static void trap(GuiGraphics g, int w, int h, DepthsClient.Banner b, float t, float live) {
        int red = 0xFFFF4A4A;
        int c = Gfx.opaque(b.color);
        float flash = Math.max(0, Mth.sin(t * 9f)) * (1 - Anim.window(t, 0f, 1.4f));
        Shapes.vignette(0, 0, w, h, 0.25f, 1.25f).fill(Gfx.alpha(red, (0.1f + 0.3f * flash) * live)).draw(g);
        float cx = w / 2f, y = h * 0.24f;
        float old = Gfx.fade(live);
        float stamp = Anim.window(t, 0f, 0.3f);
        float s = Mth.lerp(Anim.outCubic(stamp), 1.8f, 1f);
        String tag = Component.translatable("depths.beyondsolo.hud.trap").getString();
        float tagW = SysFont.spacedWidth(tag, 7f, true, 2.4f) + 26;
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(s, s, 1);
        float blink = 0.6f + 0.4f * Math.abs(Mth.sin(t * 6f));
        float o2 = Gfx.fade(Math.min(1f, stamp * 2));
        Shapes.pill(-tagW / 2f, -6.5f, tagW, 13).fill(Gfx.alpha(0xFF3A0608, 0.85f)).border(0.9f, Gfx.alpha(red, blink)).glow(8, Gfx.alpha(red, 0.45f * blink)).draw(g);
        Shapes.icon(DepthsIcons.WARNING, -tagW / 2f + 9, 0, 8).fill(Gfx.mix(red, 0xFFFFFFFF, 0.3f)).draw(g);
        SysFont.spaced(g, tag, 5, -3.4f, 7f, 0xFFFFE0E0, true, 2.4f, 0.5f);
        Gfx.setAlpha(o2);
        g.pose().popPose();
        typed(g, b.title, cx, y + 11, 13f, Gfx.mix(c, 0xFFFFFFFF, 0.55f), Anim.window(t, 0.25f, 0.8f));
        float hintIn = Anim.outCubic(Anim.window(t, 0.7f, 1.1f));
        if (hintIn > 0.01f) {
            float o3 = Gfx.fade(hintIn);
            Shapes.pill(cx - 120, y + 27, 240, 12).fill(Gfx.alpha(0xFF0A0505, 0.45f)).glow(8, Gfx.alpha(0xFF0A0505, 0.3f)).draw(g);
            SysFont.draw(g, b.subtitle, cx, y + 29.5f, 6.6f, 0xFFFFC9C4, false, 0.5f);
            Gfx.setAlpha(o3);
        }
        Gfx.setAlpha(old);
    }

    /** A lockdown's wave: stamped in, struck through with red. */
    private static void wave(GuiGraphics g, int w, int h, DepthsClient.Banner b, float t, float live) {
        int red = 0xFFFF5A4A;
        float cx = w / 2f, y = h * 0.27f;
        float stamp = Anim.window(t, 0f, 0.25f);
        float s = Mth.lerp(Anim.outCubic(stamp), 2.2f, 1f);
        float old = Gfx.fade(live * Math.min(1f, stamp * 2));
        String word = b.title.getString().toUpperCase(Locale.ROOT);
        float ww = SysFont.spacedWidth(word, 14f, true, 3f);
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(s, s, 1);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-3));
        float slash = Anim.outCubic(Anim.window(t, 0.15f, 0.45f));
        Shapes.box(-ww / 2f - 14, 2, (ww + 28) * slash, 2.2f).fill(Gfx.alpha(red, 0.9f)).glow(6, Gfx.alpha(red, 0.6f)).draw(g);
        SysFont.spaced(g, word, 0, -6, 14f, 0xFFFFE6E0, true, 3f, 0.5f);
        g.pose().popPose();
        Gfx.setAlpha(old);
    }

    // ================================================================ the strip at the top

    private static void strip(GuiGraphics g, int w) {
        GateWeather weather = DepthsClient.weather();
        boolean showWeather = weather != null && weather != GateWeather.CLEAR && !DepthsClient.weatherBannerUp() && !GateOverlay.active();
        stripIn = Anim.approach(stripIn, showWeather ? 1 : 0, 4f);
        TrapKind trap = DepthsClient.trap();
        trapIn = Anim.approach(trapIn, trap != null ? 1 : 0, 5f);
        if (stripIn < 0.01f && trapIn < 0.01f) return;
        float y = 6 + 34 * Anim.outCubic(GateOverlay.bossBarIn()) + 26 * RunHud.clockShown();
        if (stripIn > 0.01f && weather != null) {
            weatherChip(g, w, y, weather);
            y += 16 * Anim.outCubic(stripIn);
        }
        if (trapIn > 0.01f && trap != null) trapChip(g, w, y, trap);
    }

    private static void weatherChip(GuiGraphics g, int w, float y, GateWeather weather) {
        int c = Gfx.opaque(weather.color);
        String name = weather.title().getString();
        float in = Anim.outCubic(stripIn);
        float nameW = SysFont.width(name, 5.8f, true);
        String up = weather.boosted != null ? "+" + Math.round(weather.boost * 100) + "%" : null;
        String down = weather.dulled != null ? "-" + Math.round(weather.dull * 100) + "%" : null;
        float upW = up == null ? 0 : 8 + SysFont.width(up, 5f, true) + 5;
        float downW = down == null ? 0 : 8 + SysFont.width(down, 5f, true) + 5;
        float bw = 16 + nameW + 7 + upW + downW + 4;
        float bh = 13;
        float x = (w - bw) / 2f;
        y -= 8 * (1 - in);
        float old = Gfx.fade(in);
        float breathe = 0.5f + 0.5f * Anim.pulse(3.2f);
        Shapes.pill(x, y, bw, bh).fillV(0xC00A1222, 0xC0050912).border(0.7f, Gfx.alpha(c, 0.55f + 0.2f * breathe), Gfx.alpha(c, 0.25f))
                .glow(4 + 2 * breathe, Gfx.alpha(c, 0.16f)).draw(g);
        Shapes.icon(DepthsIcons.weather(weather), x + 8, y + bh / 2f, 8.5f).fill(Gfx.mix(c, 0xFFFFFFFF, 0.35f)).glow(2, Gfx.alpha(c, 0.5f)).draw(g);
        float tx = x + 16;
        SysFont.draw(g, name, tx, SysFont.centerY(y, bh, 5.8f), 5.8f, Gfx.mix(c, 0xFFFFFFFF, 0.6f), true, 0f);
        tx += nameW + 7;
        if (up != null) {
            int ec = Gfx.opaque(weather.boosted.color());
            Shapes.icon(DepthsIcons.element(weather.boosted), tx + 3, y + bh / 2f, 6.5f).fill(ec).draw(g);
            SysFont.draw(g, up, tx + 8, SysFont.centerY(y, bh, 5f), 5f, Gfx.mix(ec, 0xFFFFFFFF, 0.3f), true, 0f);
            tx += upW;
        }
        if (down != null) {
            int ec = Gfx.mix(Gfx.opaque(weather.dulled.color()), 0xFF6F859C, 0.5f);
            Shapes.icon(DepthsIcons.element(weather.dulled), tx + 3, y + bh / 2f, 6.5f).fill(ec).draw(g);
            SysFont.draw(g, down, tx + 8, SysFont.centerY(y, bh, 5f), 5f, SystemTheme.TEXT_DIM, true, 0f);
        }
        Gfx.setAlpha(old);
    }

    private static void trapChip(GuiGraphics g, int w, float y, TrapKind trap) {
        int red = 0xFFFF4A4A;
        int c = Gfx.opaque(trap.color);
        float in = Anim.outBack(trapIn);
        String name = trap.title().getString();
        String what = trap == TrapKind.LOCKDOWN
                ? (DepthsClient.waves() > 0 ? Component.translatable("depths.beyondsolo.hud.wave", DepthsClient.wave(), DepthsClient.waves()).getString()
                : Component.translatable("depths.beyondsolo.hud.sealed").getString())
                : Component.translatable("depths.beyondsolo.hud.core").getString();
        float nameW = SysFont.width(name, 5.8f, true);
        float whatW = SysFont.width(what, 5.2f, true);
        float bw = 16 + nameW + 6 + whatW + 9;
        float bh = 13;
        float x = (w - bw) / 2f;
        float blink = 0.5f + 0.5f * Anim.pulse(0.9f);
        float old = Gfx.fade(Math.min(1f, trapIn));
        g.pose().pushPose();
        g.pose().translate(w / 2f, y + bh / 2f, 0);
        g.pose().scale(0.85f + 0.15f * in, 0.85f + 0.15f * in, 1);
        g.pose().translate(-w / 2f, -(y + bh / 2f), 0);
        Shapes.pill(x, y, bw, bh).fillV(0xC8240608, 0xC8120304).border(0.8f, Gfx.alpha(red, 0.55f + 0.4f * blink), Gfx.alpha(red, 0.3f))
                .glow(4 + 3 * blink, Gfx.alpha(red, 0.22f)).draw(g);
        Shapes.icon(DepthsIcons.trap(trap), x + 8, y + bh / 2f, 8.5f).fill(Gfx.mix(c, 0xFFFFFFFF, 0.3f)).draw(g);
        SysFont.draw(g, name, x + 16, SysFont.centerY(y, bh, 5.8f), 5.8f, 0xFFFFE0DC, true, 0f);
        SysFont.draw(g, what, x + 16 + nameW + 6, SysFont.centerY(y, bh, 5.2f), 5.2f, Gfx.alpha(0xFFFFB0A8, 0.7f + 0.3f * blink), true, 0f);
        g.pose().popPose();
        Gfx.setAlpha(old);
    }

    // ================================================================ weaknesses

    /** Every tick: the creature under the crosshair, out to 24 blocks (past the reach of a hand). */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        lookId = -1;
        if (mc.player == null || mc.level == null || DepthsClient.knownCount() == 0) return;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1);
        Vec3 end = eye.add(look.scale(24));
        HitResult block = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        AABB box = mc.player.getBoundingBox().expandTowards(look.scale(24)).inflate(1);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(mc.player, eye, end, box,
                e -> e instanceof LivingEntity && !(e instanceof Player) && e.isAlive() && !e.isSpectator() && !e.isInvisible(), eye.distanceToSqr(end));
        if (hit != null) lookId = hit.getEntity().getId();
    }

    private static void weaknesses(GuiGraphics g, Minecraft mc, int w, int h) {
        Entity e = lookId >= 0 && mc.level != null ? mc.level.getEntity(lookId) : null;
        List<SkillElement> known = e instanceof LivingEntity l ? DepthsClient.knownWeaknesses(l) : List.of();
        if (!known.isEmpty()) lastWeak = known;
        weakIn = Anim.approach(weakIn, known.isEmpty() ? 0 : 1, known.isEmpty() ? 5f : 9f);
        if (weakIn < 0.01f || lastWeak.isEmpty()) return;
        String label = Component.translatable("depths.beyondsolo.hud.weak").getString();
        float labelW = SysFont.spacedWidth(label, 4.8f, true, 1.2f);
        float iconStep = 9;
        float bw = labelW + 6 + iconStep * lastWeak.size() + 4;
        float x = w / 2f - bw / 2f, y = h / 2f + 9 + 3 * (1 - weakIn);
        float old = Gfx.fade(weakIn);
        int gold = 0xFFFFC857;
        Shapes.pill(x - 4, y - 1, bw + 8, 10).fill(Gfx.alpha(0xFF060A14, 0.45f)).draw(g);
        SysFont.spaced(g, label, x, y + SysFont.centerY(0, 8, 4.8f) - 0.5f, 4.8f, gold, true, 1.2f, 0f);
        float ix = x + labelW + 6 + 4;
        for (SkillElement el : lastWeak) {
            int c = Gfx.opaque(el.color());
            Shapes.icon(DepthsIcons.element(el), ix, y + 4, 7.5f).fill(Gfx.mix(c, 0xFFFFFFFF, 0.2f)).glow(2.5f, Gfx.alpha(c, 0.7f)).draw(g);
            ix += iconStep;
        }
        Gfx.setAlpha(old);
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

    static void reset() {
        stripIn = 0;
        trapIn = 0;
        weakIn = 0;
        lastWeak = List.of();
        lookId = -1;
        kicked = null;
    }

    private DepthsHud() {}
}
