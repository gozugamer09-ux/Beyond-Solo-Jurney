package com.beyondsolo.client.light;

import com.beyondsolo.client.ClientConfig;
import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.monarch.CrownBar;
import com.beyondsolo.client.monarch.MonarchClient;
import com.beyondsolo.client.monarch.MonarchClientEvents;
import com.beyondsolo.client.skill.ClientSkills;
import com.beyondsolo.client.skill.SkillIcons;
import com.beyondsolo.light.Mantle;
import com.beyondsolo.skill.RulerPowers;
import com.beyondsolo.skill.Skill;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.settings.KeyModifier;

import java.util.Locale;

/**
 * A ruler of light's keys over the skill bar, on the same keys a monarch's crown would use (the two roads never meet):
 * the sanctum, judgement, the seraph form and the mantle's authority, each with its key, its cooldown and, while the
 * sanctum or the seraph form lasts, how long it has left. White and gold glass with a small sun on its frame; a key the
 * light hasn't given yet is locked (the sanctum and the authority come with the Investiture).
 */
public final class LightBar {
    private static final float SIZE = 20;
    private static final float GAP = 4;
    private static final int KEYS = 4;

    private static final double[] castAt = {-10, -10, -10, -10};
    private static final double[] readyAt = {-10, -10, -10, -10};
    private static final double[] refusedAt = {-10, -10, -10, -10};
    private static final boolean[] wasCooling = new boolean[KEYS];
    private static float shown;

    static void casted(int key) {
        if (key >= 0 && key < KEYS) castAt[key] = Anim.now();
    }

    static void refusedKey(int key) {
        if (key >= 0 && key < KEYS) refusedAt[key] = Anim.now();
    }

    /** A cast was refused: the key it sits on shakes. */
    public static void refused(String path) {
        if (!LightClient.sworn()) return;
        for (int i = 0; i < KEYS; i++) {
            Skill s = RulerPowers.onKey(LightClient.mantle(), i);
            if (s != null && s.path().equals(path)) refusedAt[i] = Anim.now();
        }
    }

    static void reset() {
        shown = 0;
        java.util.Arrays.fill(wasCooling, false);
    }

    /** The colour of the bar: the mantle's, or the dawn's gold before the Investiture. */
    static int color() {
        Mantle m = LightClient.mantle();
        return Gfx.opaque(m == null ? RulerPowers.DAWN_GOLD : m.color());
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !ClientConfig.SHOW_HUD.get()) return;
        boolean on = LightClient.sworn() && !MonarchClient.crowned() && ClientSystemData.get().isAwakened() && !mc.player.isSpectator()
                && anyKnown();
        shown = Anim.approach(shown, on ? 1 : 0, 6f);
        if (shown < 0.02f) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        int color = color();
        float total = KEYS * SIZE + (KEYS - 1) * GAP;
        float x0 = g.guiWidth() / 2f + 91 + 46;
        if (x0 + total + 6 > g.guiWidth()) x0 = g.guiWidth() - total - 6;
        float y = g.guiHeight() - SIZE - 3 - (SIZE + 13) + (1 - Anim.outCubic(shown)) * 14;
        float old = Gfx.fade(shown);

        // the frame: warm glass edged in the light, a small sun riding on its top edge
        float breath = 0.5f + 0.5f * Anim.pulse(3.2f);
        Shapes.box(x0 - 4, y - 4, total + 8, SIZE + 8).radius(8)
                .fillV(Gfx.alpha(0xFF1C160A, 0.92f * shown), Gfx.alpha(0xFF0C0904, 0.92f * shown))
                .border(0.7f, Gfx.alpha(color, 0.55f + 0.2f * breath), Gfx.alpha(color, 0.12f))
                .glow(3 + 2 * breath, Gfx.alpha(color, 0.12f + 0.08f * breath)).draw(g);
        float sunX = x0 + total / 2f;
        Shapes.circle(sunX, y - 5.5f, 5).fill(0x00000000).glow(5, Gfx.alpha(color, 0.35f + 0.2f * breath)).additive().draw(g);
        Shapes.icon(Icons.SPARKLE, sunX, y - 5.5f, 8).fillV(0xFFFFFFFF, color).glow(2.5f, Gfx.alpha(color, 0.7f)).rotation(Anim.loop(9f)).draw(g);

        KeyModifier shared = CrownBar.sharedModifier();
        if (shared != KeyModifier.NONE) {
            String mod = CrownBar.modifierLabel(shared);
            float mw = Ui.keycapWidth(8, mod);
            Ui.keycap(g, x0 - 8 - mw, y + SIZE / 2f - 4, 8, mod, color);
        }
        for (int i = 0; i < KEYS; i++) {
            drawSlot(g, i, RulerPowers.onKey(LightClient.mantle(), i), x0 + i * (SIZE + GAP), y, color, partial, shared != KeyModifier.NONE);
        }
        Gfx.setAlpha(old);
    }

    private static boolean anyKnown() {
        for (int i = 0; i < KEYS; i++) {
            Skill s = RulerPowers.onKey(LightClient.mantle(), i);
            if (s != null && ClientSkills.data().knows(s.path())) return true;
        }
        return false;
    }

    private static void drawSlot(GuiGraphics g, int i, Skill s, float x, float y, int light, float partial, boolean sharedMod) {
        Minecraft mc = Minecraft.getInstance();
        float cx = x + SIZE / 2f;
        float cy = y + SIZE / 2f;
        String key = CrownBar.keyLabel(MonarchClientEvents.CROWN_KEYS[i], !sharedMod);
        boolean known = s != null && ClientSkills.data().knows(s.path());
        float refused = 1 - Anim.progress(refusedAt[i], 0.4f);
        float shake = refused > 0 ? (float) Math.sin(refused * 40) * 2.2f * refused : 0;
        if (!known) {
            Shapes.box(x + shake, y, SIZE, SIZE).radius(6).fill(0x40181208).border(0.6f, Gfx.alpha(SystemTheme.TEXT_DIM, 0.5f)).draw(g);
            Shapes.icon(Icons.LOCK, cx + shake, cy, SIZE * 0.5f).fill(Gfx.alpha(SystemTheme.TEXT_DIM, 0.7f)).draw(g);
            Ui.keycap(g, x - 2, y - 5, 7, key, SystemTheme.TEXT_DIM);
            return;
        }
        int level = ClientSkills.data().level(s.path());
        int color = i == 3 ? light : Gfx.opaque(s.color());
        float cd = ClientSkills.cooldownFraction(s, partial);
        boolean cooling = cd > 0;
        if (wasCooling[i] && !cooling) readyAt[i] = Anim.now();
        wasCooling[i] = cooling;
        float mana = com.beyondsolo.rune.Runes.manaCost(com.beyondsolo.client.rune.RuneClient.data(), s, level);
        boolean noMana = ClientSystemData.get().mana() < mana && !mc.player.isCreative();
        float running = running(i, partial);

        float cast = 1 - Anim.outCubic(Anim.progress(castAt[i], 0.35f));
        float ready = 1 - Anim.progress(readyAt[i], 0.7f);
        float scale = 1 - 0.12f * cast + 0.1f * Anim.outBack(Math.min(1, ready * 1.3f)) * ready;

        g.pose().pushPose();
        g.pose().translate(cx + shake, cy, 0);
        g.pose().scale(scale, scale, 1);
        g.pose().translate(-cx, -cy, 0);

        float energy = Math.max(Math.max(cast, ready), running > 0 ? 0.55f + 0.25f * Anim.pulse(1.2f) : 0);
        Shapes.box(x, y, SIZE, SIZE).radius(6).fillV(0xE0281E0C, 0xE0120E06)
                .border(0.8f, noMana ? 0xFF4A6BC8 : Gfx.alpha(color, cooling ? 0.4f : 0.9f), Gfx.alpha(color, 0.3f))
                .glow(3 + 6 * energy, Gfx.alpha(color, (cooling ? 0.05f : 0.2f) + 0.6f * energy)).draw(g);
        float oldA = Gfx.fade(cooling || noMana ? 0.45f : 1f);
        Shapes.icon(SkillIcons.of(s), cx, cy, SIZE * 0.72f).fillV(Gfx.mix(color, 0xFFFFFFFF, 0.8f), Gfx.mix(color, 0xFFFFFFFF, 0.3f))
                .glow(1.5f + 3 * energy, Gfx.alpha(color, 0.6f)).draw(g);
        Gfx.setAlpha(oldA);

        if (cooling) {
            Shapes.box(x, y, SIZE, SIZE).radius(6).fill(0x70000000).draw(g);
            Shapes.ring(cx, cy, SIZE * 0.38f, 1.6f).fill(0x30FFFFFF).draw(g);
            Shapes.ring(cx, cy, SIZE * 0.38f, 1.6f).fillAround(Gfx.mix(color, 0xFFFFFFFF, 0.4f), color).progress(1 - cd)
                    .glow(2, Gfx.alpha(color, 0.5f)).draw(g);
            float secs = ClientSkills.cooldownSeconds(s, partial);
            String t = secs >= 10 ? String.valueOf(Math.round(secs)) : String.format(Locale.ROOT, "%.1f", secs);
            SysFont.draw(g, t, cx, SysFont.centerY(y, SIZE, 7.5f), 7.5f, SystemTheme.TEXT, true, 0.5f);
        }
        if (ready > 0) {
            Shapes.box(x, y, SIZE, SIZE).radius(6).fill(Gfx.alpha(0xFFFFFFFF, 0.35f * ready * ready)).additive().draw(g);
            Shapes.ring(cx, cy, SIZE * (0.55f + 0.5f * (1 - ready)), 1.2f).fill(Gfx.alpha(color, ready)).draw(g);
        }
        if (cast > 0) {
            Shapes.ring(cx, cy, SIZE * (0.5f + 0.7f * (1 - cast)), 1.5f * cast).fill(Gfx.alpha(Gfx.mix(color, 0xFFFFFFFF, 0.4f), cast)).draw(g);
        }
        if (refused > 0) {
            Shapes.box(x, y, SIZE, SIZE).radius(6).fill(0x00000000).border(1.2f, Gfx.alpha(SystemTheme.DANGER, refused)).draw(g);
        }
        g.pose().popPose();

        if (running > 0) {
            Shapes.box(x + 2, y + SIZE + 1.5f, SIZE - 4, 1.6f).radius(0.8f).fill(0x60000000).draw(g);
            Shapes.box(x + 2, y + SIZE + 1.5f, (SIZE - 4) * running, 1.6f).radius(0.8f)
                    .fillH(Gfx.mix(color, 0xFFFFFFFF, 0.5f), color).glow(2, Gfx.alpha(color, 0.6f)).draw(g);
        }
        Ui.keycap(g, x - 2, y - 5, 7, key, color);
        if (noMana) SysFont.draw(g, String.valueOf(Math.round(mana)), x + SIZE + 1, y - 4.5f, 5.5f, 0xFF7FA0FF, true, 1f);
    }

    /** 0..1 left of what the power on a key has running (the sanctum, the seraph form), or 0. */
    private static float running(int key, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        LightClient.Glow gl = key == 0 ? LightClient.sanctumOf(mc.player.getId()) : key == 2 ? LightClient.seraphOf(mc.player.getId()) : null;
        return gl == null ? 0 : gl.left(LightClient.now(partial));
    }

    private LightBar() {}
}
