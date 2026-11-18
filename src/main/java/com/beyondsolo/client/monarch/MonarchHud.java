package com.beyondsolo.client.monarch;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.monarch.Dominion;
import com.beyondsolo.network.MonarchFxPayload;
import com.beyondsolo.skill.MonarchPowers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;

import java.util.Locale;

/**
 * Monarchs on screen:
 * <ul>
 *   <li><b>An authority</b>: its name stamped across the screen of everyone near, with who wields it, a flash of the
 *   crown's light and the ground shaking.</li>
 *   <li><b>A clash</b>: two domains met; who held theirs against whom.</li>
 *   <li><b>Dread</b>: everyone caught in a monarch's killing intent feels it: the edges of their sight go red and
 *   their heart pounds.</li>
 *   <li><b>A monarch's own light</b>: the crown's colour at the edges of their sight in crown form, a faint tint inside
 *   any domain.</li>
 *   <li><b>The throne waiting</b>: if the hunter looked away from the throne's question, how to answer it.</li>
 * </ul>
 */
public final class MonarchHud {
    private static final float BANNER = 3.0f;
    private static final int DREAD_RED = 0xFFE0304A;

    private static double authorityAt = -10;
    private static Component authorityName = Component.empty();
    private static Component authorityBy = Component.empty();
    private static int authorityColor = 0xFFB57BFF;

    private static double clashAt = -10;
    private static Component clashLine = Component.empty();
    private static int clashColor = 0xFFB57BFF;

    private static double dreadAt = -10;
    private static boolean dreadSelf;

    private static double crownedAt = -10;
    private static int crownColor = 0xFFFFD06A;

    private static double shakeAt = -10;
    private static float shakeStrength, shakeLength = 0.5f;

    private static float domainTint;
    private static int domainColor;

    // ================================================================ moments

    static void authority(MonarchFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        Dominion d = Dominion.byIndex(p.size());
        Entity who = mc.level == null ? null : mc.level.getEntity(p.entity());
        authorityAt = Anim.now();
        authorityName = MonarchPowers.authority(d).displayName();
        authorityBy = who == null ? d.title() : Component.translatable("ui.beyondsolo.monarch.authority.by", who.getDisplayName(), d.title());
        authorityColor = Gfx.opaque(p.color());
        float near = who == null || mc.player == null ? 0.3f : 1 - Math.min(1, mc.player.distanceTo(who) / 64f);
        shake(0.5f + 0.9f * near, 0.9f);
        play(SoundEvents.BEACON_POWER_SELECT, 0.5f, 0.7f);
        play(SoundEvents.ENDER_DRAGON_GROWL, 1.4f, 0.25f);
    }

    static void clash(MonarchFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity monarch = mc.level.getEntity(p.entity());
        Entity boss = mc.level.getEntity(p.size());
        Component a = monarch == null ? Component.translatable("ui.beyondsolo.monarch.clash.someone") : monarch.getDisplayName();
        Component b = boss == null ? Component.translatable("ui.beyondsolo.monarch.clash.monarch") : boss.getDisplayName();
        clashAt = Anim.now();
        clashLine = Component.translatable("ui.beyondsolo.monarch.clash.line", a, b);
        clashColor = Gfx.opaque(p.color());
        shake(1.1f, 0.8f);
    }

    static void dread(MonarchFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Entity monarch = mc.level.getEntity(p.entity());
        boolean self = p.entity() == mc.player.getId();
        if (!self && (monarch == null || mc.player.distanceTo(monarch) > p.size() + 4)) return;
        dreadAt = Anim.now();
        dreadSelf = self;
        shake(self ? 0.4f : 0.9f, 0.6f);
        if (!self) play(SoundEvents.WARDEN_HEARTBEAT, 0.8f, 1f);
    }

    /** The player took their crown form. */
    static void crowned(int color) {
        crownedAt = Anim.now();
        crownColor = Gfx.opaque(color);
        shake(0.5f, 0.5f);
    }

    static void shake(float strength, float length) {
        if (strength < shakeLeft()) return;
        shakeAt = Anim.now();
        shakeStrength = strength;
        shakeLength = length;
    }

    private static float shakeLeft() {
        float p = Anim.progress(shakeAt, shakeLength);
        return p >= 1 ? 0 : shakeStrength * (1 - p) * (1 - p);
    }

    /** Camera shake for this frame in degrees: yaw, pitch, roll (null when still). */
    static float[] shakeAngles() {
        float s = shakeLeft();
        if (s <= 0.01f) return null;
        float t = Anim.since(shakeAt) * 34f;
        return new float[]{(float) Math.sin(t * 1.3f) * s, (float) Math.cos(t * 1.7f) * s * 0.8f, (float) Math.sin(t * 0.9f) * s * 0.5f};
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    static void reset() {
        authorityAt = clashAt = dreadAt = crownedAt = shakeAt = -10;
        domainTint = 0;
    }

    // ================================================================ drawing

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth(), h = g.guiHeight();
        tints(g, w, h, partial);
        if (mc.options.hideGui) return;
        float a = Anim.since(authorityAt);
        if (a < BANNER) authorityBanner(g, w, h, a);
        float c = Anim.since(clashAt);
        if (c < BANNER && a >= BANNER) clashBanner(g, w, h, c);
        ThroneScreen.renderPrompt(g, w, h);
    }

    /** What colours the edges of sight: dread, the crown form, a domain. */
    private static void tints(GuiGraphics g, int w, int h, float partial) {
        Minecraft mc = Minecraft.getInstance();
        // standing in a domain: a faint tint of its colour
        float[] depth = new float[1];
        int dc = MonarchClient.domainColorAt(mc.player.position(), MonarchClient.now(partial), depth);
        if (depth[0] > 0) domainColor = Gfx.opaque(dc);
        domainTint = Anim.approach(domainTint, depth[0], 3f);
        if (domainTint > 0.01f) {
            Shapes.vignette(0, 0, w, h, 0.55f, 1.45f).fill(Gfx.alpha(domainColor, 0.16f * domainTint)).draw(g);
        }
        // the crown form: the crown's colour burns at the edges, breathing
        MonarchClient.Aura form = MonarchClient.formOf(mc.player.getId());
        if (form != null) {
            float s = form.strength(MonarchClient.now(partial));
            if (s > 0.01f) {
                float breath = 0.75f + 0.25f * Anim.pulse(2.2f);
                Shapes.vignette(0, 0, w, h, 0.5f, 1.3f).fill(Gfx.alpha(Gfx.opaque(form.color), 0.22f * s * breath)).additive().draw(g);
            }
        }
        float burst = 1 - Anim.progress(crownedAt, 0.8f);
        if (burst > 0) {
            Shapes.vignette(0, 0, w, h, 0.25f, 1.2f).fill(Gfx.alpha(crownColor, 0.45f * burst * burst)).additive().draw(g);
        }
        // dread: red at the edges, beating like a heart
        float dread = Anim.since(dreadAt);
        float len = dreadSelf ? 0.7f : 2.6f;
        if (dread < len) {
            float k = 1 - dread / len;
            float beat = dreadSelf ? 1 : 0.55f + 0.45f * (float) Math.pow(Math.abs(Math.sin(dread * Math.PI * 1.6)), 6);
            Shapes.vignette(0, 0, w, h, dreadSelf ? 0.45f : 0.3f, 1.25f).fill(Gfx.alpha(DREAD_RED, (dreadSelf ? 0.3f : 0.55f) * k * beat)).draw(g);
        }
    }

    /** The authority's name stamped across the screen. */
    private static void authorityBanner(GuiGraphics g, int w, int h, float t) {
        float in = Anim.outCubic(Anim.window(t, 0, 0.25f));
        float out = Anim.window(t, BANNER - 0.5f, BANNER);
        float live = in * (1 - out);
        int c = authorityColor;
        int light = Gfx.mix(c, 0xFFFFFFFF, 0.55f);
        float cy = h * 0.27f;
        float old = Gfx.fade(live);

        // the flash it comes in with
        float flash = 1 - Anim.window(t, 0, 0.45f);
        if (flash > 0) Shapes.box(0, 0, w, h).fill(Gfx.alpha(c, 0.28f * flash * flash)).additive().draw(g);
        // a dark band, fading at both ends
        float bw = w * (0.35f + 0.65f * Anim.outCubic(Anim.window(t, 0, 0.4f)));
        float bh = 46;
        Shapes.box(w / 2f - bw / 2f, cy - bh / 2f, bw / 2f, bh).fillH(0x00000000, Gfx.alpha(0xFF04020A, 0.78f)).draw(g);
        Shapes.box(w / 2f, cy - bh / 2f, bw / 2f, bh).fillH(Gfx.alpha(0xFF04020A, 0.78f), 0x00000000).draw(g);
        // light lines along its edges, running out from the middle
        float run = Anim.outCubic(Anim.window(t, 0.05f, 0.6f));
        for (float ly : new float[]{cy - bh / 2f, cy + bh / 2f - 1}) {
            float lw = bw * 0.5f * run;
            Shapes.box(w / 2f - lw, ly, lw, 1).fillH(0x00000000, Gfx.alpha(light, 0.9f)).glow(3, Gfx.alpha(c, 0.5f)).draw(g);
            Shapes.box(w / 2f, ly, lw, 1).fillH(Gfx.alpha(light, 0.9f), 0x00000000).glow(3, Gfx.alpha(c, 0.5f)).draw(g);
        }
        // "[ AUTHORITY ]"
        String head = Component.translatable("ui.beyondsolo.monarch.authority.header").getString();
        SysFont.spaced(g, head, w / 2f, cy - bh / 2f + 5, 5.5f, Gfx.alpha(light, 0.9f), true, 2.6f, 0.5f);
        // the name, stamped: big, then settling
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
        // who wields it
        float by = Anim.window(t, 0.35f, 0.7f);
        float oldB = Gfx.fade(by);
        SysFont.draw(g, SysFont.fit(authorityBy.getString(), 6.2f, false, w - 60), w / 2f, cy + bh / 2f - 11, 6.2f, Gfx.mix(c, 0xFFFFFFFF, 0.65f), false, 0.5f);
        Gfx.setAlpha(oldB);
        Gfx.setAlpha(old);
    }

    /** Two domains met. */
    private static void clashBanner(GuiGraphics g, int w, int h, float t) {
        float in = Anim.outBack(Anim.window(t, 0, 0.35f));
        float out = Anim.window(t, BANNER - 0.4f, BANNER);
        float live = Anim.clamp01(t / 0.2f) * (1 - out);
        int c = clashColor;
        String head = Component.translatable("ui.beyondsolo.monarch.clash.header").getString();
        String line = SysFont.fit(clashLine.getString(), 6.4f, false, w - 80);
        float bw = Math.max(SysFont.spacedWidth(head, 8f, true, 2.4f), SysFont.width(line, 6.4f, false)) + 44;
        float bh = 30;
        float x = w / 2f - bw / 2f;
        float y = h * 0.27f - bh / 2f - (1 - in) * 10;
        float old = Gfx.fade(live);
        Ui.panel(g, x, y, bw, bh, 9, c, 0.9f);
        Shapes.icon(Icons.CROWN, x + 14, y + bh / 2f, 11).fillV(Gfx.mix(c, 0xFFFFFFFF, 0.6f), c).glow(3, Gfx.alpha(c, 0.6f)).draw(g);
        SysFont.spaced(g, head, x + bw / 2f + 6, y + 6, 8f, Gfx.mix(c, 0xFFFFFFFF, 0.5f), true, 2.4f, 0.5f);
        SysFont.draw(g, line, x + bw / 2f + 6, y + 18, 6.4f, SystemTheme.TEXT, false, 0.5f);
        Gfx.setAlpha(old);
    }

    private MonarchHud() {}
}
