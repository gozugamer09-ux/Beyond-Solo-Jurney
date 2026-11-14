package com.beyondsolo.client.gate;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.notify.NotificationManager;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.network.GatePayload;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The gate moments on screen, and the boss bar:
 * <ul>
 *   <li><b>Entering</b>: rings of light close in from the edges and spin faster, the screen is pulled towards the
 *   middle in the gate's colour, then a white flash and black until you arrive.</li>
 *   <li><b>Arriving</b>: the black lifts and the dungeon's name is drawn on a line of light, with the gate's rank.</li>
 *   <li><b>Leaving / back out</b>: the same in the System's blue, through white.</li>
 *   <li><b>Boss intro</b>: cinema bars slide in, the screen pulses red, the boss's name types itself under a BOSS
 *   tag with its rank emblem, and the camera shakes as it roars.</li>
 *   <li><b>Boss defeated</b>, then <b>dungeon cleared</b>: a flash, DEFEATED stamped over its name with shockwaves;
 *   then a gold banner with the XP counting up and a note that the way out is open.</li>
 * </ul>
 * The boss bar sits at the top of the screen while a boss is awake nearby: name, rank emblem, health with a trailing
 * damage band, a notch at half health, and a red pulse once it is enraged.
 */
public final class GateOverlay {
    private record Moment(GatePayload p, float duration) {}

    private static final Deque<Moment> QUEUE = new ArrayDeque<>();
    private static Moment current;
    private static double startedAt;
    /** Black (or white for an exit) held between going through and arriving. */
    private static float cover;
    private static boolean coverWhite;
    private static double coverSince;
    private static boolean shakePlayed;

    private static float trauma;
    private static float shakeTime;

    private static int bossId = -1;
    private static float shownHealth = 1f;
    private static float trailHealth = 1f;
    private static float barIn;
    private static double enragedAt = -100;

    // ------------------------------------------------------------------ events

    public static void handle(GatePayload p) {
        switch (p.kind()) {
            case GatePayload.SHAKE -> addTrauma(p.value());
            case GatePayload.ENRAGED -> {
                enragedAt = Anim.now();
                addTrauma(0.8f);
                sound(SoundEvents.WARDEN_ROAR, 1.3f, 0.5f);
            }
            case GatePayload.ENTER -> QUEUE.addFirst(new Moment(p, 2.3f));
            case GatePayload.EXIT -> QUEUE.addFirst(new Moment(p, 2.1f));
            case GatePayload.ARRIVE, GatePayload.RETURN -> QUEUE.addLast(new Moment(p, 4.4f));
            case GatePayload.BOSS_INTRO -> {
                bossId = p.entity();
                QUEUE.addLast(new Moment(p, 4.0f));
            }
            case GatePayload.BOSS_DEFEATED -> QUEUE.addLast(new Moment(p, 3.4f));
            case GatePayload.CLEARED -> QUEUE.addLast(new Moment(p, 4.8f));
            default -> {}
        }
        // going through cuts in front of whatever is showing
        if ((p.kind() == GatePayload.ENTER || p.kind() == GatePayload.EXIT) && current != null) {
            current = null;
        }
    }

    public static boolean active() {
        return current != null || cover > 0.01f;
    }

    public static void addTrauma(float amount) {
        trauma = Math.min(1f, trauma + amount);
    }

    public static void reset() {
        QUEUE.clear();
        current = null;
        cover = 0;
        trauma = 0;
        bossId = -1;
        NotificationManager.setHeld(false);
    }

    /** The hide key skips the moment on screen (not going through, which waits on the server). */
    public static boolean skip() {
        if (current == null) return false;
        int k = current.p.kind();
        if (k == GatePayload.ENTER || k == GatePayload.EXIT) return false;
        float t = Anim.since(startedAt);
        float end = current.duration - 0.5f;
        if (t < end) startedAt -= end - t;
        return true;
    }

    public static void frame() {
        float dt = Anim.dt();
        trauma = Math.max(0f, trauma - dt * 1.1f);
        shakeTime += dt;

        if (current != null && Anim.since(startedAt) >= current.duration) {
            int k = current.p.kind();
            if (k == GatePayload.ENTER || k == GatePayload.EXIT) {
                cover = 1f;
                coverWhite = k == GatePayload.EXIT;
                coverSince = Anim.now();
            }
            current = null;
            NotificationManager.setHeld(false);
        }
        if (current == null && !QUEUE.isEmpty()) {
            current = QUEUE.pollFirst();
            startedAt = Anim.now();
            shakePlayed = false;
            NotificationManager.setHeld(true);
            int k = current.p.kind();
            if (k == GatePayload.ARRIVE || k == GatePayload.RETURN) {
                // the cover lifts as the arrival plays
                coverSince = Anim.now();
            }
            switch (k) {
                case GatePayload.ENTER -> sound(SoundEvents.PORTAL_TRIGGER, 1.6f, 0.35f);
                case GatePayload.EXIT -> sound(SoundEvents.PORTAL_TRIGGER, 2.0f, 0.3f);
                case GatePayload.ARRIVE -> sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7f, 0.8f);
                case GatePayload.RETURN -> sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 0.8f);
                case GatePayload.BOSS_INTRO -> sound(SoundEvents.RAID_HORN.value(), 0.6f, 0.4f);
                case GatePayload.BOSS_DEFEATED -> sound(SoundEvents.ENDER_DRAGON_GROWL, 1.6f, 0.35f);
                case GatePayload.CLEARED -> sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.6f);
                default -> {}
            }
        }
        if (current == null && cover > 0 && Anim.since(coverSince) > 12f) cover = 0;
    }

    private static void sound(SoundEvent s, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
    }

    /** Camera shake offsets in degrees: yaw, pitch, roll. */
    public static float[] shake() {
        float s = trauma * trauma;
        if (s <= 0.0005f) return new float[]{0, 0, 0};
        float t = shakeTime * 28f;
        return new float[]{3f * s * noise(t, 1.3f), 2.4f * s * noise(t, 7.1f), 4f * s * noise(t, 3.7f)};
    }

    private static float noise(float t, float seed) {
        return Mth.sin(t + seed) * 0.6f + Mth.sin(t * 1.7f + seed * 2.3f) * 0.4f;
    }

    // ------------------------------------------------------------------ drawing

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int w = g.guiWidth(), h = g.guiHeight();
        if (!mc.options.hideGui) bossBar(g, mc, w);

        if (current != null) {
            Moment m = current;
            float t = Anim.since(startedAt);
            switch (m.p.kind()) {
                case GatePayload.ENTER -> through(g, w, h, t, m, false);
                case GatePayload.EXIT -> through(g, w, h, t, m, true);
                case GatePayload.ARRIVE -> arrive(g, w, h, t, m, false);
                case GatePayload.RETURN -> arrive(g, w, h, t, m, true);
                case GatePayload.BOSS_INTRO -> bossIntro(g, w, h, t, m);
                case GatePayload.BOSS_DEFEATED -> defeated(g, w, h, t, m);
                case GatePayload.CLEARED -> cleared(g, w, h, t, m);
                default -> {}
            }
        } else if (cover > 0.01f) {
            Shapes.box(0, 0, w, h).fill(coverWhite ? 0xFFF4FBFF : 0xFF000000).draw(g);
            float dots = Anim.since(coverSince);
            for (int i = 0; i < 3; i++) {
                float a = 0.3f + 0.7f * Math.max(0, Mth.sin(dots * 5f - i * 0.8f));
                Shapes.circle(w / 2f - 8 + i * 8, h / 2f, 1.6f).fill(Gfx.alpha(coverWhite ? SystemTheme.ACCENT_DEEP : SystemTheme.ACCENT, a)).draw(g);
            }
        }
    }

    private static int rankColor(Moment m) {
        return SystemTheme.rank(PlayerRank.values()[Mth.clamp(m.p.rank(), 0, 5)]);
    }

    /** Going through a gate: light closes in and swallows the screen. */
    private static void through(GuiGraphics g, int w, int h, float t, Moment m, boolean exit) {
        int color = exit ? SystemTheme.ACCENT : rankColor(m);
        float k = Anim.clamp01(t / m.duration);
        float cx = w / 2f, cy = h / 2f;
        float diag = Mth.sqrt(w * w + h * h) / 2f;

        // the edges darken and tint, closing in
        float inner = Mth.lerp(Anim.inCubic(k), 0.75f, 0.0f);
        Shapes.vignette(0, 0, w, h, inner, inner + 0.6f).fill(Gfx.alpha(Gfx.mix(color, 0xFF000000, exit ? 0.2f : 0.55f), 0.35f + 0.65f * k)).draw(g);
        // rings converging and spinning faster
        for (int i = 0; i < 5; i++) {
            float phase = (t * (0.6f + 1.4f * k) + i / 5f) % 1f;
            float r = diag * (1 - phase) * (1.1f - 0.5f * k);
            float a = Mth.sin(phase * Mth.PI) * (0.25f + 0.6f * k);
            Shapes.ring(cx, cy, r, 1.2f + 2.5f * k).fill(Gfx.alpha(Gfx.mix(color, 0xFFFFFFFF, 0.35f), a)).dashes(24 - i * 2, 0.55f)
                    .rotation(t * (0.2f + 1.2f * k) * (i % 2 == 0 ? 1 : -1)).draw(g);
        }
        Shapes.rays(cx, cy, diag * (0.4f + 0.6f * k), 18).fill(Gfx.alpha(color, 0.15f + 0.35f * k)).rotation(t * 0.4f).additive().draw(g);
        // the pull at the middle
        float core = 20 + 140 * Anim.inCubic(k);
        Shapes.circle(cx, cy, core).fill(Gfx.alpha(exit ? 0xFFFFFFFF : 0xFF000000, 0.4f * k)).glow(core * 0.8f, Gfx.alpha(color, 0.6f * k)).draw(g);

        // label
        float textIn = Anim.outCubic(Anim.window(t, 0.15f, 0.6f)) * (1 - Anim.window(t, 1.6f, 1.95f));
        if (textIn > 0.01f) {
            float old = Gfx.fade(textIn);
            String label = Component.translatable(exit ? "gate.beyondsolo.leaving" : "gate.beyondsolo.overlay.entering").getString().toUpperCase();
            SysFont.spaced(g, label, cx, cy - 22 - 6 * (1 - textIn), 7f, Gfx.mix(color, 0xFFFFFFFF, 0.6f), true, 2.2f, 0.5f);
            if (!exit) typed(g, m.p.title(), cx, cy - 8, 14f, 0xFFFFFFFF, Anim.window(t, 0.3f, 1.2f));
            Gfx.setAlpha(old);
        }

        // the flash into the other side
        float flash = Anim.window(t, 1.75f, m.duration);
        if (flash > 0) {
            float white = Mth.sin(Math.min(1f, flash * 1.4f) * Mth.PI);
            Shapes.box(0, 0, w, h).fill(Gfx.alpha(exit ? 0xFFF4FBFF : 0xFF000000, Anim.inCubic(flash))).draw(g);
            Shapes.box(0, 0, w, h).fill(Gfx.alpha(0xFFFFFFFF, 0.85f * white * (exit ? 0.3f : 1f))).draw(g);
        }
    }

    /** Coming out on the other side: the cover lifts and the place is named. */
    private static void arrive(GuiGraphics g, int w, int h, float t, Moment m, boolean back) {
        int color = back ? SystemTheme.ACCENT : rankColor(m);
        float lift = 1 - Anim.outCubic(Anim.window(t, 0.1f, 1.1f));
        if (lift > 0.01f) Shapes.box(0, 0, w, h).fill(Gfx.alpha(back ? 0xFFF4FBFF : 0xFF000000, lift)).draw(g);
        cover = 0;
        float exitK = Anim.window(t, m.duration - 0.6f, m.duration);
        float live = 1 - Anim.outCubic(exitK);
        float cx = w / 2f, y = h * 0.3f;

        Shapes.vignette(0, 0, w, h, 0.3f, 1.4f).fill(Gfx.alpha(color, 0.18f * live * (1 - Anim.window(t, 1.5f, 3f)))).draw(g);
        float line = Anim.outQuint(Anim.window(t, 0.5f, 1.3f));
        float old = Gfx.fade(live);
        float lw = Math.min(w * 0.7f, 300) * line;
        Ui.divider(g, cx - lw / 2f, y + 22, lw, Gfx.alpha(color, 0.9f));
        Shapes.pill(cx - lw * 0.15f, y + 21.25f, lw * 0.3f, 1.5f).fill(Gfx.mix(color, 0xFFFFFFFF, 0.6f)).glow(6, Gfx.alpha(color, 0.8f)).draw(g);

        float chipIn = Anim.outBack(Anim.window(t, 0.8f, 1.2f));
        if (chipIn > 0.01f) {
            float o2 = Gfx.fade(Math.min(1f, chipIn));
            String tag = Component.translatable(back ? "gate.beyondsolo.overlay.cleared" : "gate.beyondsolo.overlay.dungeon").getString();
            if (back) tag = m.p.title().getString().toUpperCase();
            float tw = SysFont.spacedWidth(tag, 6.5f, true, 1.6f) + 14;
            Shapes.pill(cx - tw / 2f, y - 4, tw, 11).fill(Gfx.alpha(color, 0.18f)).border(0.75f, Gfx.alpha(color, 0.8f)).glow(6, Gfx.alpha(color, 0.35f)).draw(g);
            SysFont.spaced(g, tag, cx, y - 1, 6.5f, Gfx.mix(color, 0xFFFFFFFF, 0.5f), true, 1.6f, 0.5f);
            Gfx.setAlpha(o2);
        }
        Component name = back ? m.p.subtitle() : m.p.title();
        typed(g, name, cx, y + 8, 13f, 0xFFFFFFFF, Anim.window(t, 1.0f, 1.9f));
        if (!back) {
            float subIn = Anim.outCubic(Anim.window(t, 1.6f, 2.2f));
            if (subIn > 0.01f) {
                float o3 = Gfx.fade(subIn);
                float sw = SysFont.width(m.p.subtitle(), 7f, true);
                Ui.rankEmblem(g, PlayerRank.values()[Mth.clamp(m.p.rank(), 0, 5)], cx - sw / 2f - 9, y + 31.5f, 5.5f, 1 - subIn);
                SysFont.draw(g, m.p.subtitle(), cx + 4, y + 28, 7f, Gfx.mix(color, 0xFFFFFFFF, 0.4f), true, 0.5f);
                Gfx.setAlpha(o3);
            }
        }
        Gfx.setAlpha(old);
    }

    private static void bossIntro(GuiGraphics g, int w, int h, float t, Moment m) {
        int color = rankColor(m);
        float in = Anim.outCubic(Anim.window(t, 0f, 0.5f));
        float out = Anim.inCubic(Anim.window(t, m.duration - 0.6f, m.duration));
        float bars = in * (1 - out);
        float bh = h * 0.11f * bars;
        Shapes.box(0, 0, w, bh).fill(0xF0000000).draw(g);
        Shapes.box(0, h - bh, w, bh).fill(0xF0000000).draw(g);
        Shapes.box(0, bh - 1, w * bars, 1).fill(Gfx.alpha(color, 0.8f)).draw(g);
        Shapes.box(w * (1 - bars), h - bh, w * bars, 1).fill(Gfx.alpha(color, 0.8f)).draw(g);
        float pulse = 0.5f + 0.5f * Mth.sin(t * 7f);
        Shapes.vignette(0, 0, w, h, 0.2f, 1.3f).fill(Gfx.alpha(0xFFB01226, (0.25f + 0.2f * pulse) * bars)).draw(g);
        if (!shakePlayed && t > 1.1f) {
            shakePlayed = true;
            addTrauma(0.85f);
        }

        float cx = w / 2f, y = h - bh - 46;
        float old = Gfx.fade(1 - out);
        float tagIn = Anim.outBack(Anim.window(t, 0.6f, 1.0f));
        if (tagIn > 0.01f) {
            float o2 = Gfx.fade(Math.min(1f, tagIn));
            String tag = Component.translatable("gate.beyondsolo.overlay.boss").getString();
            float tw = SysFont.spacedWidth(tag, 7f, true, 2.4f) + 16;
            g.pose().pushPose();
            g.pose().translate(cx, y, 0);
            g.pose().scale(tagIn, tagIn, 1);
            Shapes.pill(-tw / 2f, -6, tw, 12).fill(Gfx.alpha(0xFFB01226, 0.5f)).border(0.75f, 0xFFFF5D6C).glow(8, 0x90FF2040).draw(g);
            SysFont.spaced(g, tag, 0, -3.2f, 7f, 0xFFFFE0E0, true, 2.4f, 0.5f);
            g.pose().popPose();
            Gfx.setAlpha(o2);
        }
        float nameK = Anim.window(t, 0.9f, 1.9f);
        float nw = SysFont.width(m.p.title(), 18f, true);
        if (nameK > 0) {
            Ui.rankEmblem(g, PlayerRank.values()[Mth.clamp(m.p.rank(), 0, 5)], cx - nw / 2f - 16, y + 20, 9f, 1 - Anim.window(t, 1.2f, 2.4f));
            Shapes.box(cx - nw / 2f - 30, y + 10, nw + 60, 22).radius(11).fill(0x00000000).glow(18, Gfx.alpha(color, 0.25f * Anim.outCubic(nameK))).draw(g);
        }
        typed(g, m.p.title(), cx + 6, y + 11, 18f, 0xFFFFFFFF, nameK);
        float subIn = Anim.outCubic(Anim.window(t, 1.8f, 2.4f));
        if (subIn > 0.01f) {
            float o3 = Gfx.fade(subIn);
            SysFont.draw(g, m.p.subtitle(), cx, y + 34, 7.5f, Gfx.mix(color, 0xFFFFFFFF, 0.35f), true, 0.5f);
            Gfx.setAlpha(o3);
        }
        Gfx.setAlpha(old);
    }

    private static void defeated(GuiGraphics g, int w, int h, float t, Moment m) {
        int color = rankColor(m);
        float flash = 1 - Anim.outCubic(Anim.window(t, 0f, 0.7f));
        Shapes.box(0, 0, w, h).fill(Gfx.alpha(0xFFFFFFFF, 0.55f * flash)).draw(g);
        float exitK = Anim.window(t, m.duration - 0.5f, m.duration);
        float old = Gfx.fade(1 - Anim.outCubic(exitK));
        float cx = w / 2f, cy = h * 0.32f;
        SysFont.draw(g, m.p.title(), cx, cy - 18, 9f, Gfx.mix(color, 0xFFFFFFFF, 0.5f), true, 0.5f);
        float stamp = Anim.window(t, 0.35f, 0.65f);
        if (stamp > 0) {
            float s = Mth.lerp(Anim.outCubic(stamp), 2.6f, 1f);
            if (stamp >= 1f && t < 0.75f) addTrauma(0.04f);
            for (int i = 0; i < 2; i++) {
                float wave = Anim.window(t, 0.62f + i * 0.12f, 1.5f + i * 0.12f);
                if (wave > 0 && wave < 1) Shapes.ring(cx, cy + 4, 20 + 110 * Anim.outCubic(wave), 2.5f * (1 - wave)).fill(Gfx.alpha(SystemTheme.GOLD, 1 - wave)).draw(g);
            }
            g.pose().pushPose();
            g.pose().translate(cx, cy + 4, 0);
            g.pose().scale(s, s, 1);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-4));
            float o2 = Gfx.fade(Math.min(1f, stamp * 1.5f));
            String word = Component.translatable("gate.beyondsolo.overlay.defeated").getString();
            float ww = SysFont.spacedWidth(word, 20f, true, 3f) + 26;
            Shapes.box(-ww / 2f, -15, ww, 30).radius(4).fill(Gfx.alpha(0xFF1A0E02, 0.55f)).border(1.5f, SystemTheme.GOLD).glow(14, 0x80FFD166).draw(g);
            SysFont.spaced(g, word, 0, -7, 20f, 0xFFFFE6A8, true, 3f, 0.5f);
            Gfx.setAlpha(o2);
            g.pose().popPose();
        }
        Gfx.setAlpha(old);
    }

    private static void cleared(GuiGraphics g, int w, int h, float t, Moment m) {
        int gold = SystemTheme.GOLD;
        float exitK = Anim.window(t, m.duration - 0.6f, m.duration);
        float live = 1 - Anim.outCubic(exitK);
        float cx = w / 2f, y = h * 0.26f;
        Shapes.vignette(0, 0, w, h, 0.2f, 1.4f).fill(Gfx.alpha(gold, 0.16f * live * (1 - Anim.window(t, 1.2f, 3.5f)))).draw(g);
        float old = Gfx.fade(live);
        float open = Anim.outBack(Anim.window(t, 0f, 0.55f));
        float pw = 230, ph = 64;
        g.pose().pushPose();
        g.pose().translate(cx, y + ph / 2f, 0);
        g.pose().scale(1, Math.max(0.02f, open), 1);
        Shapes.rays(0, 0, 150, 20).fill(Gfx.alpha(gold, 0.18f * Math.min(1f, open))).rotation(t * 0.05f).additive().draw(g);
        Ui.panel(g, -pw / 2f, -ph / 2f, pw, ph, 10, gold, 1f);
        g.pose().popPose();
        if (open > 0.95f) {
            String head = Component.translatable("gate.beyondsolo.overlay.cleared").getString();
            float hk = Anim.outCubic(Anim.window(t, 0.45f, 0.9f));
            float o2 = Gfx.fade(hk);
            SysFont.spaced(g, head, cx, y + 10 - 4 * (1 - hk), 11f, 0xFFFFE6A8, true, 2.6f, 0.5f);
            Gfx.setAlpha(o2);
            SysFont.draw(g, m.p.title(), cx, y + 27, 7.5f, Gfx.alpha(SystemTheme.TEXT_SOFT, hk), false, 0.5f);
            float count = Anim.outCubic(Anim.window(t, 0.9f, 2.2f));
            if (count > 0) {
                long xp = Math.round(m.p.value() * count);
                Ui.chip(g, cx - 28, y + 40, 12, Component.translatable("gate.beyondsolo.overlay.xp", xp).getString(), 7f, SystemTheme.GOOD, true);
            }
            float hint = Anim.outCubic(Anim.window(t, 2.3f, 2.8f));
            if (hint > 0.01f) {
                SysFont.draw(g, Component.translatable("gate.beyondsolo.overlay.exit_hint"), cx, y + ph + 8, 7f, Gfx.alpha(SystemTheme.ACCENT_BRIGHT, hint), false, 0.5f);
            }
        }
        Gfx.setAlpha(old);
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

    // ------------------------------------------------------------------ boss bar

    private static void bossBar(GuiGraphics g, Minecraft mc, int w) {
        GateBossEntity boss = null;
        if (bossId >= 0 && mc.level != null) {
            Entity e = mc.level.getEntity(bossId);
            if (e instanceof GateBossEntity b && b.distanceTo(mc.player) < 64) boss = b;
        }
        if (boss == null && mc.level != null && mc.level.dimension().equals(GateService.DIMENSION)) {
            for (GateBossEntity b : mc.level.getEntitiesOfClass(GateBossEntity.class, mc.player.getBoundingBox().inflate(40))) {
                if (b.isAwake()) {
                    boss = b;
                    bossId = b.getId();
                }
            }
        }
        boolean show = boss != null && boss.isAwake() && boss.introTicks() <= 0 && (boss.isAlive() || boss.deathTime < 20)
                && (current == null || current.p.kind() != GatePayload.BOSS_INTRO);
        barIn = Anim.approach(barIn, show ? 1f : 0f, 3.5f);
        if (barIn <= 0.01f || boss == null) return;
        float hp = Mth.clamp(boss.getHealth() / boss.getMaxHealth(), 0f, 1f);
        shownHealth = Anim.approach(shownHealth, hp, 2.5f);
        if (hp > trailHealth) trailHealth = hp;
        trailHealth = Anim.approach(trailHealth, shownHealth, 0.45f);

        int color = SystemTheme.rank(boss.rank());
        boolean enraged = boss.isEnraged();
        float bw = Math.min(260, w * 0.55f), x = (w - bw) / 2f, y = 8 - 20 * (1 - Anim.outCubic(barIn));
        float old = Gfx.fade(barIn);
        float rage = enraged ? 0.5f + 0.5f * Mth.sin((float) Anim.now() * 6f) : 0f;
        Shapes.box(x - 4, y - 2, bw + 8, 25).radius(8).fillV(0xC00A1222, 0xC0050912)
                .border(0.75f, Gfx.alpha(enraged ? 0xFFFF4050 : color, 0.6f + 0.4f * rage))
                .glow(8, Gfx.alpha(enraged ? 0xFFFF2040 : color, 0.2f + 0.3f * rage)).draw(g);
        Ui.rankEmblem(g, boss.rank(), x + 7, y + 8, 5.5f, 0);
        SysFont.draw(g, boss.getDisplayName(), x + 17, y + 3.5f, 8f, 0xFFFFFFFF, true, 0f);
        if (enraged) {
            float since = Anim.since(enragedAt);
            float pop = since < 0.5f ? Anim.outBack(Anim.window(since, 0, 0.4f)) : 1f;
            String tag = Component.translatable("gate.beyondsolo.overlay.enraged").getString();
            g.pose().pushPose();
            g.pose().translate(x + bw - 2, y + 7.5f, 0);
            g.pose().scale(pop, pop, 1);
            float tw = SysFont.width(tag, 6f, true) + 8;
            Shapes.pill(-tw, -5, tw, 10).fill(Gfx.alpha(0xFFB01226, 0.6f)).border(0.6f, 0xFFFF5D6C).draw(g);
            SysFont.draw(g, tag, -tw / 2f, -2.2f, 6f, 0xFFFFE0E0, true, 0.5f);
            g.pose().popPose();
        } else {
            SysFont.draw(g, Math.round(boss.getHealth()) + " / " + Math.round(boss.getMaxHealth()), x + bw, y + 4.5f, 6f, SystemTheme.TEXT_SOFT, false, 1f);
        }
        float by = y + 15;
        Shapes.pill(x, by, bw, 5).fill(SystemTheme.TRACK).border(0.5f, SystemTheme.TRACK_BORDER).draw(g);
        if (trailHealth > shownHealth + 0.002f) {
            Shapes.pill(x, by, Math.max(5, bw * trailHealth), 5).fill(0xD0FFE6A8).draw(g);
        }
        if (shownHealth > 0.003f) {
            Shapes.pill(x, by, Math.max(5, bw * shownHealth), 5).fillH(enraged ? 0xFF9A0F20 : SystemTheme.HP_A, enraged ? 0xFFFF4D5E : SystemTheme.HP_B)
                    .glow(4, Gfx.alpha(SystemTheme.HP_B, 0.5f)).sheen(Anim.loop(2.6f) * 1.6f - 0.3f, 0.2f).draw(g);
        }
        Shapes.box(x + bw * 0.5f - 0.5f, by - 1, 1, 7).fill(0xB0FFFFFF).draw(g);
        Gfx.setAlpha(old);
    }

    private GateOverlay() {}
}
