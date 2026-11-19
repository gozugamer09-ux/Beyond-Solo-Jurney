package com.beyondsolo.client.monarch;

import com.beyondsolo.client.ClientConfig;
import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.skill.ClientSkills;
import com.beyondsolo.client.skill.SkillIcons;
import com.beyondsolo.monarch.Dominion;
import com.beyondsolo.skill.MonarchPowers;
import com.beyondsolo.skill.Skill;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.settings.KeyModifier;

import java.util.Locale;

/**
 * A monarch's crown keys over the skill bar: the domain, dread, the crown form and their authority, each with its
 * key, its cooldown and, while a domain or the crown form lasts, how long it has left. Drawn in the crown's colour with
 * a crown on the frame; only a monarch has it.
 */
public final class CrownBar {
    private static final float SIZE = 20;
    private static final float GAP = 4;
    private static final int KEYS = 4;

    private static final double[] castAt = {-10, -10, -10, -10};
    private static final double[] readyAt = {-10, -10, -10, -10};
    private static final double[] refusedAt = {-10, -10, -10, -10};
    private static final boolean[] wasCooling = new boolean[KEYS];
    private static float shown;

    public static void casted(int key) {
        if (key >= 0 && key < KEYS) castAt[key] = Anim.now();
    }

    /** A cast was refused: the key it sits on shakes. */
    public static void refused(String path) {
        Dominion d = MonarchClient.dominion();
        if (d == null) return;
        for (int i = 0; i < KEYS; i++) {
            Skill s = MonarchPowers.onKey(d, i);
            if (s != null && s.path().equals(path)) refusedAt[i] = Anim.now();
        }
    }

    public static void reset() {
        shown = 0;
        java.util.Arrays.fill(wasCooling, false);
    }

    /** The top of the bar on screen, for what sits over it. */
    static float top(GuiGraphics g) {
        return g.guiHeight() - SIZE - 3 - (SIZE + 13) - 5;
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !ClientConfig.SHOW_HUD.get()) return;
        Dominion d = MonarchClient.dominion();
        shown = Anim.approach(shown, d != null && ClientSystemData.get().isAwakened() && !mc.player.isSpectator() ? 1 : 0, 6f);
        if (shown < 0.02f || d == null) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        int color = Gfx.opaque(d.color());

        float total = KEYS * SIZE + (KEYS - 1) * GAP;
        // over the skill bar, right of the hotbar (same place sideways)
        float x0 = g.guiWidth() / 2f + 91 + 46;
        if (x0 + total + 6 > g.guiWidth()) x0 = g.guiWidth() - total - 6;
        float y = g.guiHeight() - SIZE - 3 - (SIZE + 13) + (1 - Anim.outCubic(shown)) * 14;
        float old = Gfx.fade(shown);

        // the frame: dark glass edged in the crown's colour, a small crown riding on its top edge
        float breath = 0.5f + 0.5f * Anim.pulse(3.2f);
        Shapes.box(x0 - 4, y - 4, total + 8, SIZE + 8).radius(8)
                .fillV(Gfx.alpha(0xFF0E0A1C, 0.92f * shown), Gfx.alpha(0xFF05040C, 0.92f * shown))
                .border(0.7f, Gfx.alpha(color, 0.55f + 0.2f * breath), Gfx.alpha(color, 0.12f))
                .glow(3 + 2 * breath, Gfx.alpha(color, 0.12f + 0.08f * breath)).draw(g);
        float crownX = x0 + total / 2f;
        Shapes.icon(Icons.CROWN, crownX, y - 5.5f, 8).fillV(Gfx.mix(color, 0xFFFFFFFF, 0.6f), color)
                .glow(2.5f, Gfx.alpha(color, 0.6f)).draw(g);

        // a shared modifier (Alt by default) is shown once, left of the bar
        KeyModifier shared = sharedModifier();
        if (shared != KeyModifier.NONE) {
            String mod = modifierLabel(shared);
            float mw = Ui.keycapWidth(8, mod);
            Ui.keycap(g, x0 - 8 - mw, y + SIZE / 2f - 4, 8, mod, color);
        }
        for (int i = 0; i < KEYS; i++) {
            Skill s = MonarchPowers.onKey(d, i);
            if (s == null) continue;
            drawSlot(g, i, s, x0 + i * (SIZE + GAP), y, color, partial, shared != KeyModifier.NONE);
        }
        Gfx.setAlpha(old);
    }

    private static void drawSlot(GuiGraphics g, int i, Skill s, float x, float y, int crown, float partial, boolean sharedMod) {
        Minecraft mc = Minecraft.getInstance();
        float cx = x + SIZE / 2f;
        float cy = y + SIZE / 2f;
        String key = keyLabel(MonarchClientEvents.CROWN_KEYS[i], !sharedMod);
        boolean known = ClientSkills.data().knows(s.path());
        int color = i == 3 ? crown : Gfx.opaque(s.color());
        if (!known) {
            Shapes.box(x, y, SIZE, SIZE).radius(6).fill(0x40100818).border(0.6f, Gfx.alpha(SystemTheme.TEXT_DIM, 0.5f)).draw(g);
            Shapes.icon(Icons.LOCK, cx, cy, SIZE * 0.5f).fill(Gfx.alpha(SystemTheme.TEXT_DIM, 0.7f)).draw(g);
            Ui.keycap(g, x - 2, y - 5, 7, key, SystemTheme.TEXT_DIM);
            return;
        }
        int level = ClientSkills.data().level(s.path());
        float cd = ClientSkills.cooldownFraction(s, partial);
        boolean cooling = cd > 0;
        if (wasCooling[i] && !cooling) readyAt[i] = Anim.now();
        wasCooling[i] = cooling;
        float mana = com.beyondsolo.rune.Runes.manaCost(com.beyondsolo.client.rune.RuneClient.data(), s, level);
        boolean noMana = ClientSystemData.get().mana() < mana && !mc.player.isCreative();
        // what the power left running: the domain and the crown form show how long they have left
        float running = running(i, partial);

        float cast = 1 - Anim.outCubic(Anim.progress(castAt[i], 0.35f));
        float ready = 1 - Anim.progress(readyAt[i], 0.7f);
        float refused = 1 - Anim.progress(refusedAt[i], 0.4f);
        float shake = refused > 0 ? (float) Math.sin(refused * 40) * 2.2f * refused : 0;
        float scale = 1 - 0.12f * cast + 0.1f * Anim.outBack(Math.min(1, ready * 1.3f)) * ready;

        g.pose().pushPose();
        g.pose().translate(cx + shake, cy, 0);
        g.pose().scale(scale, scale, 1);
        g.pose().translate(-cx, -cy, 0);

        float energy = Math.max(Math.max(cast, ready), running > 0 ? 0.55f + 0.25f * Anim.pulse(1.2f) : 0);
        Shapes.box(x, y, SIZE, SIZE).radius(6).fillV(0xE01A1030, 0xE0080514)
                .border(0.8f, noMana ? 0xFF4A6BC8 : Gfx.alpha(color, cooling ? 0.4f : 0.9f), Gfx.alpha(color, 0.3f))
                .glow(3 + 6 * energy, Gfx.alpha(color, (cooling ? 0.05f : 0.2f) + 0.6f * energy)).draw(g);
        float oldA = Gfx.fade(cooling || noMana ? 0.45f : 1f);
        Shapes.icon(SkillIcons.of(s), cx, cy, SIZE * 0.72f).fillV(Gfx.mix(color, 0xFFFFFFFF, 0.75f), Gfx.mix(color, 0xFFFFFFFF, 0.25f))
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

        // how long the domain or the crown form has left: a bar along the slot's foot
        if (running > 0) {
            Shapes.box(x + 2, y + SIZE + 1.5f, SIZE - 4, 1.6f).radius(0.8f).fill(0x60000000).draw(g);
            Shapes.box(x + 2, y + SIZE + 1.5f, (SIZE - 4) * running, 1.6f).radius(0.8f)
                    .fillH(Gfx.mix(color, 0xFFFFFFFF, 0.5f), color).glow(2, Gfx.alpha(color, 0.6f)).draw(g);
        }
        Ui.keycap(g, x - 2, y - 5, 7, key, color);
        if (noMana) SysFont.draw(g, String.valueOf(Math.round(mana)), x + SIZE + 1, y - 4.5f, 5.5f, 0xFF7FA0FF, true, 1f);
    }

    /** 0..1 left of what the power on a key has running (the domain, the crown form), or 0. */
    private static float running(int key, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || key > 2 || key == 1) return 0;
        MonarchClient.Aura a = null;
        if (key == 0) {
            for (MonarchClient.Aura d : MonarchClient.domains()) if (d.entity == mc.player.getId()) a = d;
        } else {
            a = MonarchClient.formOf(mc.player.getId());
        }
        if (a == null || a.endedAt >= 0) return 0;
        double now = MonarchClient.now(partial);
        return (float) Math.max(0, Math.min(1, (a.until - now) / Math.max(1, a.until - a.since)));
    }

    // ================================================================ key labels

    /** The modifier every crown key shares (Alt by default), or NONE when they differ or there is none. */
    public static KeyModifier sharedModifier() {
        KeyModifier m = MonarchClientEvents.CROWN_KEYS[0].getKeyModifier();
        for (KeyMapping k : MonarchClientEvents.CROWN_KEYS) if (k.getKeyModifier() != m || k.isUnbound()) return KeyModifier.NONE;
        return m;
    }

    /** "ALT" in the player's language (NeoForge names modifiers as "ALT + %s"). */
    public static String modifierLabel(KeyModifier m) {
        String s = m.getCombinedName(InputConstants.UNKNOWN, Component::empty).getString();
        s = s.replace("+", "").trim();
        return s.isEmpty() ? m.name() : s.toUpperCase(Locale.ROOT);
    }

    /** A crown key's keycap: the key (with its modifier when the keys don't share one), a few letters at most. */
    public static String keyLabel(KeyMapping k, boolean withModifier) {
        if (k.isUnbound()) return "-";
        String key = k.getKey().getDisplayName().getString();
        if (key.length() > 3) key = key.substring(0, 3);
        if (!withModifier || k.getKeyModifier() == KeyModifier.NONE) return key;
        return modifierLabel(k.getKeyModifier()).substring(0, 1) + "+" + key;
    }

    private CrownBar() {}
}
