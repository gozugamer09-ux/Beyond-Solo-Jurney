package com.beyondsolo.client.light;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gate.GateOverlay;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.hud.HudSpace;
import com.beyondsolo.client.skill.SkillIcons;
import com.beyondsolo.light.Mantle;
import com.beyondsolo.network.LightFxPayload;
import com.beyondsolo.skill.RulerPowers;
import com.beyondsolo.skill.Skill;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.Locale;

/**
 * The light on screen:
 * <ul>
 *   <li><b>An authority of light</b>: its name stamped across the screen of everyone near, who wields it, a flash of the
 *   mantle's light and the ground shaking.</li>
 *   <li><b>A clash</b>: a sanctum held a monarch's domain back.</li>
 *   <li><b>Judgement</b>: everyone near feels the light's eye open: gold at the edges of sight and a bell.</li>
 *   <li><b>A ruler's own light</b>: gold at the edges of their sight in seraph form, a warm tint inside any sanctum.</li>
 *   <li><b>The light's words</b>: the hum behind the chapel's wall, the first step into the chapel, the call to the
 *   Investiture.</li>
 *   <li><b>The vigil</b>: its waves, the foes left, the ring they must hold; and its failing.</li>
 *   <li><b>The Investiture</b>: the mantle taken up, the new ruler's title across the screen.</li>
 * </ul>
 */
public final class LightHud {
    private static final float BANNER = 3.0f;
    private static final float INVESTITURE = 7.2f;
    public static final int GOLD = 0xFFFFE9A8;
    private static final int DEEP = 0xFFB07A1E;
    /** The vigil's waves (as {@code light.Vigil}). */
    private static final int VIGIL_WAVES = 3;

    private static double authorityAt = -10;
    private static Component authorityName = Component.empty();
    private static Component authorityBy = Component.empty();
    private static int authorityColor = GOLD;

    private static double clashAt = -10;
    private static Component clashLine = Component.empty();
    private static int clashColor = GOLD;

    private static double judgedAt = -10;
    private static int judgedColor = GOLD;

    private static double raisedAt = -10;
    private static int raisedColor = GOLD;

    private static float sanctumTint;
    private static int sanctumColor = GOLD;

    /** The light's words: a headline and a line, over the world. */
    private static double wordsAt = -10;
    private static float wordsLength;
    private static String wordsHead = "", wordsSub = "";
    private static int wordsColor = GOLD;
    private static boolean wordsRays;

    /** The vigil as last heard: the wave (0 gathering, 1..3, 4 the last down, -2 out of the ring), what is left. */
    private static int vigilWave = Integer.MIN_VALUE;
    private static int vigilLeft, vigilTotal;
    private static double vigilAt = -10;
    private static float vigilShown, vigilBar;

    private static double investedAt = -10;
    private static Mantle invested = Mantle.VERDICT;

    // ================================================================ moments

    static void authority(LightFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        Mantle m = Mantle.byIndex(p.size());
        Entity who = mc.level == null ? null : mc.level.getEntity(p.entity());
        authorityAt = Anim.now();
        authorityName = RulerPowers.authority(m).displayName();
        authorityBy = who == null ? m.title() : Component.translatable("ui.beyondsolo.light.authority.by", who.getDisplayName(), m.title());
        authorityColor = Gfx.opaque(p.color());
        float near = who == null || mc.player == null ? 0.3f : 1 - Math.min(1, mc.player.distanceTo(who) / 64f);
        GateOverlay.addTrauma(0.35f + 0.5f * near);
        play(SoundEvents.BEACON_POWER_SELECT, 0.6f, 0.7f);
        play(SoundEvents.BELL_RESONATE, 0.8f, 1f);
    }

    static void clash(LightFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity ruler = mc.level.getEntity(p.entity());
        Entity monarch = mc.level.getEntity(p.size());
        Component a = ruler == null ? Component.translatable("ui.beyondsolo.light.clash.someone") : ruler.getDisplayName();
        Component b = monarch == null ? Component.translatable("ui.beyondsolo.light.clash.monarch") : monarch.getDisplayName();
        clashAt = Anim.now();
        clashLine = Component.translatable("ui.beyondsolo.light.clash.line", a, b);
        clashColor = Gfx.opaque(p.color());
        GateOverlay.addTrauma(0.6f);
    }

    static void judgement(LightFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Entity ruler = mc.level.getEntity(p.entity());
        boolean self = p.entity() == mc.player.getId();
        if (!self && (ruler == null || mc.player.distanceTo(ruler) > p.size() + 4)) return;
        judgedAt = Anim.now();
        judgedColor = Gfx.opaque(p.color());
        GateOverlay.addTrauma(self ? 0.25f : 0.4f);
        if (!self) play(SoundEvents.BELL_BLOCK, 1.4f, 0.8f);
    }

    /** The player raised a sanctum or took seraph form. */
    static void raised(int color, boolean seraph) {
        raisedAt = Anim.now();
        raisedColor = Gfx.opaque(color);
        GateOverlay.addTrauma(seraph ? 0.45f : 0.25f);
    }

    /** The hidden wall hums (or just came down). */
    static void hum(boolean opened) {
        words(tr(opened ? "chapel.opened.title" : "chapel.hum.title"), tr(opened ? "chapel.opened.body" : "chapel.hum.body"), GOLD,
                opened ? 5.5f : 6.5f, opened);
        if (!opened) play(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6f, 0.8f);
    }

    /** The first step into the chapel. */
    static void enter() {
        words(tr("chapel.enter.title"), tr("chapel.enter.body"), GOLD, 7f, true);
        play(SoundEvents.BELL_RESONATE, 1.2f, 0.6f);
    }

    /** The light calls them to the Investiture. */
    static void called() {
        words(tr("called.title"), tr("called.body"), GOLD, 7.5f, true);
        GateOverlay.addTrauma(0.4f);
        play(SoundEvents.BELL_RESONATE, 0.7f, 1f);
        play(SoundEvents.BEACON_ACTIVATE, 1.4f, 0.7f);
    }

    static void vigil(int wave, int left) {
        double now = Anim.now();
        if (wave == -1) {
            vigilWave = Integer.MIN_VALUE;
            words(tr("vigil.failed.title"), tr("vigil.failed.body"), SystemTheme.DANGER, 6f, false);
            play(SoundEvents.BEACON_DEACTIVATE, 0.6f, 1f);
            return;
        }
        if (wave == VIGIL_WAVES + 1) {
            // the last wave down: the dark is held back
            vigilWave = Integer.MIN_VALUE;
            words(tr("vigil.held.title"), tr("vigil.held.body"), GOLD, 4f, true);
            return;
        }
        if (wave >= 1 && wave != vigilWave) {
            vigilTotal = left;
            play(SoundEvents.WARDEN_HEARTBEAT, 0.7f, 1f);
        }
        if (wave >= 1 && left > vigilTotal) vigilTotal = left;
        if (wave == 0 && vigilWave == Integer.MIN_VALUE) play(SoundEvents.BEACON_AMBIENT, 1.2f, 1f);
        vigilWave = wave;
        vigilLeft = left;
        vigilAt = now;
    }

    static void investiture(Mantle m) {
        investedAt = Anim.now();
        invested = m;
        vigilWave = Integer.MIN_VALUE;
        GateOverlay.addTrauma(1f);
        play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 0.8f);
        play(SoundEvents.BEACON_POWER_SELECT, 0.6f, 1f);
        play(SoundEvents.BELL_RESONATE, 0.5f, 1f);
    }

    /** The Investiture is on screen: a ruler power's own pop-up waits (the card shows it). */
    public static boolean investing() {
        return Anim.since(investedAt) < INVESTITURE;
    }

    private static void words(String head, String sub, int color, float length, boolean rays) {
        wordsAt = Anim.now();
        wordsHead = head;
        wordsSub = sub;
        wordsColor = color;
        wordsLength = length;
        wordsRays = rays;
    }

    private static String tr(String key, Object... args) {
        return Component.translatable("ui.beyondsolo.light." + key, args).getString();
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    static void reset() {
        authorityAt = clashAt = judgedAt = raisedAt = wordsAt = investedAt = vigilAt = -10;
        vigilWave = Integer.MIN_VALUE;
        sanctumTint = vigilShown = 0;
    }

    /** An authority's, a clash's or the Investiture's banner is across the screen: new pop-ups wait for it. */
    public static boolean bannerUp() {
        return Anim.since(authorityAt) < BANNER || Anim.since(clashAt) < BANNER || investing();
    }

    // ================================================================ drawing

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth(), h = g.guiHeight();
        tints(g, w, h, partial);
        if (mc.options.hideGui) return;
        float i = Anim.since(investedAt);
        if (i < INVESTITURE) {
            investitureCard(g, w, h, i);
            return;
        }
        float a = Anim.since(authorityAt);
        if (a < BANNER) authorityBanner(g, w, h, a);
        float c = Anim.since(clashAt);
        if (c < BANNER && a >= BANNER) clashBanner(g, w, h, c);
        float t = Anim.since(wordsAt);
        if (t < wordsLength) wordsMoment(g, w, h, t);
        vigilPanel(g, w);
        MantleScreen.renderPrompt(g, w, h);
    }

    /** What colours the edges of sight: a sanctum, the seraph form, judgement. */
    private static void tints(GuiGraphics g, int w, int h, float partial) {
        Minecraft mc = Minecraft.getInstance();
        float[] depth = new float[1];
        int sc = LightClient.sanctumColorAt(mc.player.position(), LightClient.now(partial), depth);
        if (depth[0] > 0) sanctumColor = Gfx.opaque(sc);
        sanctumTint = Anim.approach(sanctumTint, depth[0], 3f);
        if (sanctumTint > 0.01f) {
            float breath = 0.8f + 0.2f * Anim.pulse(3.4f);
            Shapes.vignette(0, 0, w, h, 0.55f, 1.45f).fill(Gfx.alpha(sanctumColor, 0.14f * sanctumTint * breath)).additive().draw(g);
        }
        LightClient.Glow form = LightClient.seraphOf(mc.player.getId());
        if (form != null) {
            float s = form.strength(LightClient.now(partial));
            if (s > 0.01f) {
                float breath = 0.75f + 0.25f * Anim.pulse(2.6f);
                Shapes.vignette(0, 0, w, h, 0.5f, 1.3f).fill(Gfx.alpha(Gfx.opaque(form.color), 0.2f * s * breath)).additive().draw(g);
            }
        }
        float burst = 1 - Anim.progress(raisedAt, 0.8f);
        if (burst > 0) Shapes.vignette(0, 0, w, h, 0.25f, 1.2f).fill(Gfx.alpha(raisedColor, 0.42f * burst * burst)).additive().draw(g);
        float judged = 1 - Anim.progress(judgedAt, 1.4f);
        if (judged > 0) {
            Shapes.vignette(0, 0, w, h, 0.3f, 1.2f).fill(Gfx.alpha(judgedColor, 0.4f * judged * judged)).additive().draw(g);
            float flash = 1 - Anim.progress(judgedAt, 0.25f);
            if (flash > 0) Shapes.box(0, 0, w, h).fill(Gfx.alpha(0xFFFFFFFF, 0.25f * flash)).additive().draw(g);
        }
    }

    /** The authority's name stamped across the screen, under a crown of rays. */
    private static void authorityBanner(GuiGraphics g, int w, int h, float t) {
        float in = Anim.outCubic(Anim.window(t, 0, 0.25f));
        float out = Anim.window(t, BANNER - 0.5f, BANNER);
        float live = in * (1 - out);
        int c = authorityColor;
        int light = Gfx.mix(c, 0xFFFFFFFF, 0.6f);
        float cy = h * 0.27f;
        float old = Gfx.fade(live);
        float flash = 1 - Anim.window(t, 0, 0.45f);
        if (flash > 0) Shapes.box(0, 0, w, h).fill(Gfx.alpha(light, 0.32f * flash * flash)).additive().draw(g);
        // rays standing up out of the band, like dawn over the screen's edge
        float rise = Anim.outCubic(Anim.window(t, 0, 0.7f));
        Shapes.rays(w / 2f, cy, w * 0.55f * rise, 18).fill(Gfx.alpha(c, 0.16f * live)).rotation(t * 0.02f).additive().draw(g);
        float bw = w * (0.35f + 0.65f * Anim.outCubic(Anim.window(t, 0, 0.4f)));
        float bh = 46;
        Shapes.box(w / 2f - bw / 2f, cy - bh / 2f, bw / 2f, bh).fillH(0x00000000, Gfx.alpha(0xFF0C0904, 0.74f)).draw(g);
        Shapes.box(w / 2f, cy - bh / 2f, bw / 2f, bh).fillH(Gfx.alpha(0xFF0C0904, 0.74f), 0x00000000).draw(g);
        float run = Anim.outCubic(Anim.window(t, 0.05f, 0.6f));
        for (float ly : new float[]{cy - bh / 2f, cy + bh / 2f - 1}) {
            float lw = bw * 0.5f * run;
            Shapes.box(w / 2f - lw, ly, lw, 1).fillH(0x00000000, Gfx.alpha(light, 0.95f)).glow(3, Gfx.alpha(c, 0.6f)).draw(g);
            Shapes.box(w / 2f, ly, lw, 1).fillH(Gfx.alpha(light, 0.95f), 0x00000000).glow(3, Gfx.alpha(c, 0.6f)).draw(g);
        }
        String head = Component.translatable("ui.beyondsolo.light.authority.header").getString();
        SysFont.spaced(g, head, w / 2f, cy - bh / 2f + 5, 5.5f, Gfx.alpha(light, 0.95f), true, 2.6f, 0.5f);
        float stamp = Anim.window(t, 0.08f, 0.32f);
        float scale = 1 + 0.6f * (1 - Anim.outCubic(stamp));
        String name = authorityName.getString().toUpperCase(Locale.ROOT);
        float track = 2.4f * Math.max(0, name.codePointCount(0, name.length()) - 1);
        float unit = SysFont.spacedWidth(name, 10, true, 0) / 10f;
        float size = Math.max(8, Math.min(17, (w - 40 - track) / Math.max(0.01f, unit)));
        g.pose().pushPose();
        g.pose().translate(w / 2f, cy + 2, 0);
        g.pose().scale(scale, scale, 1);
        float oldS = Gfx.fade(Math.min(1, stamp * 2));
        SysFont.spaced(g, name, 0, -size * 0.5f, size, 0xFFFFFFFF, true, 2.4f, 0.5f);
        Gfx.setAlpha(oldS);
        g.pose().popPose();
        float by = Anim.window(t, 0.35f, 0.7f);
        float oldB = Gfx.fade(by);
        SysFont.draw(g, SysFont.fit(authorityBy.getString(), 6.2f, false, w - 60), w / 2f, cy + bh / 2f - 11, 6.2f, light, false, 0.5f);
        Gfx.setAlpha(oldB);
        Gfx.setAlpha(old);
    }

    /** A sanctum held a monarch's domain back. */
    private static void clashBanner(GuiGraphics g, int w, int h, float t) {
        float in = Anim.outBack(Anim.window(t, 0, 0.35f));
        float out = Anim.window(t, BANNER - 0.4f, BANNER);
        float live = Anim.clamp01(t / 0.2f) * (1 - out);
        int c = clashColor;
        String head = Component.translatable("ui.beyondsolo.light.clash.header").getString();
        String line = SysFont.fit(clashLine.getString(), 6.4f, false, w - 80);
        float bw = Math.max(SysFont.spacedWidth(head, 8f, true, 2.4f), SysFont.width(line, 6.4f, false)) + 44;
        float bh = 30;
        float x = w / 2f - bw / 2f;
        float y = h * 0.27f - bh / 2f - (1 - in) * 10;
        float old = Gfx.fade(live);
        Ui.panel(g, x, y, bw, bh, 9, c, 0.9f);
        Shapes.icon(Icons.SPARKLE, x + 14, y + bh / 2f, 11).fillV(0xFFFFFFFF, c).glow(3, Gfx.alpha(c, 0.7f)).rotation(Anim.loop(5f)).draw(g);
        SysFont.spaced(g, head, x + bw / 2f + 6, y + 6, 8f, Gfx.mix(c, 0xFFFFFFFF, 0.5f), true, 2.4f, 0.5f);
        SysFont.draw(g, line, x + bw / 2f + 6, y + 18, 6.4f, SystemTheme.TEXT, false, 0.5f);
        Gfx.setAlpha(old);
    }

    /** The light's words: a spaced headline and a quiet line under it, on a soft band, with rays behind when it is the light itself speaking. */
    private static void wordsMoment(GuiGraphics g, int w, int h, float t) {
        float in = Anim.outCubic(Anim.window(t, 0.15f, 0.9f));
        float out = Anim.window(t, wordsLength - 0.9f, wordsLength);
        float a = in * (1 - out);
        if (a <= 0.01f) return;
        int c = wordsColor;
        float cx = w / 2f, y = h * 0.3f;
        if (wordsRays) {
            Shapes.vignette(0, 0, w, h, 0.4f, 1.35f).fill(Gfx.alpha(c, 0.12f * a)).additive().draw(g);
            Shapes.rays(cx, y + 8, w * 0.45f * Anim.outCubic(Anim.window(t, 0, 1.4f)), 20).fill(Gfx.alpha(c, 0.08f * a)).rotation(t * 0.015f)
                    .additive().draw(g);
        }
        float old = Gfx.fade(a);
        float bw = Math.min(w * 0.86f, Math.max(SysFont.spacedWidth(wordsHead, 11f, true, 3f), SysFont.width(wordsSub, 7.5f, false)) + 70);
        Shapes.pill(cx - bw / 2f, y - 10, bw, 46).fill(Gfx.alpha(0xFF0A0804, 0.55f)).glow(26, Gfx.alpha(0xFF0A0804, 0.4f)).draw(g);
        SysFont.spaced(g, wordsHead, cx, y - 2 * (1 - in), 11f, 0xFFFFFFFF, true, 3f + 2f * (1 - in), 0.5f);
        float lw = Math.min(bw - 40, 190) * Anim.outQuint(in);
        Shapes.pill(cx - lw / 2f, y + 14.5f, lw, 0.9f).fill(Gfx.alpha(c, 0.95f)).glow(5, Gfx.alpha(c, 0.7f)).draw(g);
        Shapes.icon(Icons.SPARKLE, cx, y + 15, 5 * Anim.outBack(in)).fill(0xFFFFFFFF).glow(3, Gfx.alpha(c, 0.8f)).rotation(t * 0.3f).draw(g);
        if (!wordsSub.isEmpty()) {
            SysFont.draw(g, SysFont.fit(wordsSub, 7.5f, false, w - 30), cx, y + 20, 7.5f, Gfx.mix(c, 0xFFFFFFFF, 0.55f), false, 0.5f);
        }
        Gfx.setAlpha(old);
    }

    /** The vigil at the top of the screen: the wave, the dark left to hold back, the ring they must keep. */
    private static void vigilPanel(GuiGraphics g, int w) {
        boolean live = vigilWave != Integer.MIN_VALUE && Anim.since(vigilAt) < 40;
        vigilShown = Anim.approach(vigilShown, live ? 1 : 0, 6f);
        if (vigilShown < 0.02f) return;
        float in = Anim.outBack(vigilShown);
        float pw = 214, ph = 34;
        float x = (w - pw) / 2f, y = 6 - 10 * (1 - in);
        HudSpace.claimTop(x, y, pw, ph);
        boolean outside = vigilWave == -2;
        int c = outside ? SystemTheme.DANGER : GOLD;
        float old = Gfx.fade(vigilShown);
        float pulse = Anim.pulse(outside ? 0.6f : 2.4f);
        Shapes.box(x, y, pw, ph).radius(9).fillV(Gfx.alpha(0xFF161006, 0.9f), Gfx.alpha(0xFF0A0703, 0.92f))
                .border(0.8f, Gfx.alpha(c, 0.7f + 0.25f * pulse), Gfx.alpha(c, 0.2f)).glow(6 + 4 * pulse, Gfx.alpha(c, 0.22f + 0.1f * pulse)).draw(g);
        Shapes.icon(Icons.SPARKLE, x + 14, y + ph / 2f, 12).fillV(0xFFFFFFFF, c).glow(3, Gfx.alpha(c, 0.7f)).rotation(Anim.loop(8f)).draw(g);
        String head, sub, count = "";
        float frac;
        if (outside) {
            float secs = Math.max(0, vigilLeft / 20f - Anim.since(vigilAt));
            head = tr("vigil.outside");
            sub = tr("vigil.outside.body", String.format(Locale.ROOT, "%.0f", Math.ceil(secs)));
            frac = vigilLeft <= 0 ? 0 : secs / (vigilLeft / 20f);
        } else if (vigilWave == 0) {
            float secs = Math.max(0, vigilLeft / 20f - Anim.since(vigilAt));
            head = tr("vigil.gather");
            sub = tr("vigil.gather.body");
            count = String.format(Locale.ROOT, "%.0f", Math.ceil(secs));
            frac = vigilLeft <= 0 ? 0 : 1 - secs / (vigilLeft / 20f);
        } else {
            head = tr("vigil.wave", vigilWave, VIGIL_WAVES);
            sub = vigilLeft == 0 ? tr("vigil.between") : tr("vigil.hold");
            count = vigilLeft == 0 ? "" : tr("vigil.left", vigilLeft);
            frac = vigilTotal <= 0 ? 1 : 1 - vigilLeft / (float) vigilTotal;
        }
        SysFont.spaced(g, head.toUpperCase(Locale.ROOT), x + 25, y + 5, 8f, 0xFFFFFFFF, true, 1.5f, 0f);
        if (!count.isEmpty()) SysFont.draw(g, count, x + pw - 8, y + 6, 7.5f, Gfx.mix(c, 0xFFFFFFFF, 0.4f), true, 1f);
        SysFont.text(g, SysFont.fit(sub, 6.3f, false, pw - 34), x + 25, y + 15.5f, 6.3f, outside ? Gfx.mix(c, 0xFFFFFFFF, 0.5f) : SystemTheme.TEXT_SOFT);
        vigilBar = Anim.approach(vigilBar, Mth.clamp(frac, 0, 1), 6f);
        float bx = x + 25, bw = pw - 33, by = y + ph - 7;
        Shapes.pill(bx, by, bw, 3.5f).fill(SystemTheme.TRACK).draw(g);
        if (vigilBar > 0.01f) {
            Shapes.pill(bx, by, Math.max(3.5f, bw * vigilBar), 3.5f).fillH(outside ? 0xFF8A0A18 : DEEP, c).glow(3, Gfx.alpha(c, 0.6f))
                    .sheen(Anim.loop(2.2f) * 1.6f - 0.3f, 0.25f).draw(g);
        }
        Gfx.setAlpha(old);
    }

    /**
     * The Investiture: light floods the screen in the mantle's colour, rays turning behind it; INVESTITURE, then the
     * new ruler's title stamped under it, their authority, and a line of what it means.
     */
    private static void investitureCard(GuiGraphics g, int w, int h, float t) {
        Mantle m = invested;
        int c = Gfx.opaque(m.color());
        int light = Gfx.mix(c, 0xFFFFFFFF, 0.6f);
        float cx = w / 2f, cy = h * 0.4f;
        float live = 1 - Anim.window(t, INVESTITURE - 1.0f, INVESTITURE);
        float flash = 1 - Anim.outCubic(Anim.window(t, 0, 1.1f));
        Shapes.box(0, 0, w, h).fill(Gfx.alpha(0xFFFFF8E8, 0.85f * flash)).draw(g);
        Shapes.vignette(0, 0, w, h, 0.3f, 1.3f).fill(Gfx.alpha(c, 0.3f * live)).additive().draw(g);
        Shapes.box(0, 0, w, h).fill(Gfx.alpha(0xFF0A0703, 0.35f * live * Anim.window(t, 0.3f, 1.2f))).draw(g);
        float rays = Anim.outCubic(Anim.window(t, 0.2f, 1.4f));
        Shapes.rays(cx, cy, Math.max(w, h) * 0.85f * rays, 28).fill(Gfx.alpha(c, 0.2f * live)).rotation(t * 0.012f).additive().draw(g);
        Shapes.rays(cx, cy, 170 * rays, 12).fill(Gfx.alpha(0xFFFFFFFF, 0.1f * live)).rotation(-t * 0.03f).additive().draw(g);
        for (int i = 0; i < 3; i++) {
            float u = Anim.window(t, 0.5f + i * 0.16f, 1.8f + i * 0.16f);
            if (u > 0 && u < 1) Shapes.ring(cx, cy, 28 + 240 * Anim.outCubic(u), 3.5f * (1 - u)).fill(Gfx.alpha(light, (1 - u) * live)).draw(g);
        }
        // the emblem: the authority's sign in a halo
        float born = Anim.outBackStrong(Anim.window(t, 0.45f, 1.05f));
        if (born > 0) {
            float old = Gfx.fade(live);
            float r = 22 * born;
            float ey = cy - 30;
            Shapes.circle(cx, ey, r * 1.2f).fill(Gfx.alpha(0xFF120C04, 0.85f)).glow(34, Gfx.alpha(c, 0.55f)).draw(g);
            Shapes.ring(cx, ey, r * 1.55f, 1.4f).fill(Gfx.alpha(light, 0.85f)).dashes(24, 0.55f).rotation(t * 0.05f).draw(g);
            Shapes.ring(cx, ey, r * 1.32f, 2f).fill(c).dashes(8, 0.8f).rotation(-t * 0.08f).draw(g);
            Shapes.ring(cx, ey, r * 1.85f, 0.8f).fill(Gfx.alpha(c, 0.6f)).draw(g);
            Shapes.icon(MantleScreen.icon(m), cx, ey, r * 1.35f).fillV(0xFFFFFFFF, light).glow(6, Gfx.alpha(c, 0.9f)).draw(g);
            Gfx.setAlpha(old);
        }
        float head = Anim.outCubic(Anim.window(t, 0.9f, 1.4f)) * live;
        if (head > 0.01f) {
            float old = Gfx.fade(head);
            SysFont.spaced(g, tr("investiture.header"), cx, cy + 6, 7f, light, true, 3.6f + 3f * (1 - head), 0.5f);
            Gfx.setAlpha(old);
        }
        float stamp = Anim.window(t, 1.3f, 1.6f);
        if (stamp > 0) {
            float s = Mth.lerp(Anim.outCubic(stamp), 2.6f, 1f);
            float old = Gfx.fade(Math.min(1f, stamp * 1.6f) * live);
            String title = m.title().getString().toUpperCase(Locale.ROOT);
            float size = Math.min(19, (w - 40) / Math.max(1, SysFont.spacedWidth(title, 1, true, 0)) * 0.8f);
            g.pose().pushPose();
            g.pose().translate(cx, cy + 26, 0);
            g.pose().scale(s, s, 1);
            SysFont.spaced(g, title, 0, -size / 2f, size, 0xFFFFFFFF, true, 3f, 0.5f);
            g.pose().popPose();
            Gfx.setAlpha(old);
            if (stamp >= 1 && t < 1.7f) GateOverlay.addTrauma(0.05f);
        }
        float sub = Anim.outCubic(Anim.window(t, 2.0f, 2.6f)) * live;
        if (sub > 0.01f) {
            float old = Gfx.fade(sub);
            float lw = 220 * Anim.outQuint(sub);
            Shapes.pill(cx - lw / 2f, cy + 40, lw, 1f).fill(light).glow(6, Gfx.alpha(c, 0.8f)).draw(g);
            Skill auth = RulerPowers.authority(m);
            String a = Component.translatable("ui.beyondsolo.light.investiture.authority", auth.displayName()).getString();
            float aw = SysFont.width(a, 7.5f, true);
            Shapes.icon(SkillIcons.of(auth), cx - aw / 2f - 8, cy + 50, 10).fillV(0xFFFFFFFF, light).glow(3, Gfx.alpha(c, 0.8f)).draw(g);
            SysFont.draw(g, a, cx + 4, cy + 46, 7.5f, light, true, 0.5f);
            SysFont.draw(g, SysFont.fit(tr("investiture.body"), 7f, false, w - 40), cx, cy + 60, 7f, SystemTheme.TEXT_SOFT, false, 0.5f);
            Gfx.setAlpha(old);
        }
    }

    private LightHud() {}
}
