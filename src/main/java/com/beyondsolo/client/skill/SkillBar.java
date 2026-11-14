package com.beyondsolo.client.skill;

import com.beyondsolo.client.ClientConfig;
import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.SkillData;
import com.beyondsolo.system.SystemData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The skill bar next to the hotbar: four slots with their keys, cooldown sweeps and seconds, a flash when a
 * skill comes off cooldown, a pulse on cast, a shake when a cast is refused, and dimmed icons without the mana.
 */
public final class SkillBar {
    private static final float SIZE = 20;
    private static final float GAP = 4;

    private static final double[] castAt = {-10, -10, -10, -10};
    private static final double[] readyAt = {-10, -10, -10, -10};
    private static final double[] refusedAt = {-10, -10, -10, -10};
    private static final boolean[] wasCooling = new boolean[SkillData.SLOTS];
    private static float shown;

    public static void casted(int slot) {
        if (slot >= 0 && slot < 4) castAt[slot] = Anim.now();
    }

    public static void refused(String path) {
        int slot = ClientSkills.data().slotOf(path);
        if (slot >= 0) refusedAt[slot] = Anim.now();
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !ClientConfig.SHOW_HUD.get()) return;
        SystemData sys = ClientSystemData.get();
        boolean any = false;
        for (int i = 0; i < SkillData.SLOTS; i++) any |= ClientSkills.slot(i) != null;
        shown = Anim.approach(shown, sys.isAwakened() && any && !mc.player.isSpectator() ? 1 : 0, 6f);
        if (shown < 0.02f) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);

        float total = SkillData.SLOTS * SIZE + (SkillData.SLOTS - 1) * GAP;
        float x0 = g.guiWidth() / 2f + 91 + 46;
        // leave room for the attack indicator and Combat Roll's dodge icon right of the hotbar
        if (x0 + total + 6 > g.guiWidth()) x0 = g.guiWidth() - total - 6;
        float y = g.guiHeight() - SIZE - 3 + (1 - Anim.outCubic(shown)) * 14;
        float old = Gfx.fade(shown);

        Shapes.box(x0 - 4, y - 4, total + 8, SIZE + 8).radius(8)
                .fillV(Gfx.fade(SystemTheme.GLASS_TOP, 0.9f * shown), Gfx.fade(SystemTheme.GLASS_BOTTOM, 0.9f * shown))
                .border(0.6f, Gfx.alpha(SystemTheme.ACCENT, 0.4f), Gfx.alpha(SystemTheme.ACCENT, 0.1f)).draw(g);

        for (int i = 0; i < SkillData.SLOTS; i++) {
            float x = x0 + i * (SIZE + GAP);
            drawSlot(g, i, x, y, sys, partial);
        }
        Gfx.setAlpha(old);
    }

    private static void drawSlot(GuiGraphics g, int i, float x, float y, SystemData sys, float partial) {
        Skill s = ClientSkills.slot(i);
        String key = SkillClientEvents.SLOT_KEYS[i].getTranslatedKeyMessage().getString();
        if (key.length() > 3) key = key.substring(0, 3);
        float cx = x + SIZE / 2f;
        float cy = y + SIZE / 2f;
        if (s == null) {
            Shapes.box(x, y, SIZE, SIZE).radius(6).fill(0x40081020).border(0.6f, Gfx.alpha(SystemTheme.TEXT_DIM, 0.5f)).draw(g);
            SysFont.draw(g, "+", cx, SysFont.centerY(y, SIZE, 9), 9, Gfx.alpha(SystemTheme.TEXT_DIM, 0.7f), true, 0.5f);
            Ui.keycap(g, x - 2, y - 5, 7, key, SystemTheme.TEXT_DIM);
            return;
        }
        int level = ClientSkills.data().level(s.path());
        float cd = ClientSkills.cooldownFraction(s, partial);
        boolean cooling = cd > 0;
        if (wasCooling[i] && !cooling) readyAt[i] = Anim.now();
        wasCooling[i] = cooling;
        boolean noMana = sys.mana() < s.manaCost(level) && !Minecraft.getInstance().player.isCreative();

        float cast = 1 - Anim.outCubic(Anim.progress(castAt[i], 0.35f));
        float ready = 1 - Anim.progress(readyAt[i], 0.7f);
        float refused = 1 - Anim.progress(refusedAt[i], 0.4f);
        float shake = refused > 0 ? (float) Math.sin(refused * 40) * 2.2f * refused : 0;
        float scale = 1 - 0.12f * cast + 0.1f * Anim.outBack(Math.min(1, ready * 1.3f)) * ready;

        g.pose().pushPose();
        g.pose().translate(cx + shake, cy, 0);
        g.pose().scale(scale, scale, 1);
        g.pose().translate(-cx, -cy, 0);

        int color = Gfx.opaque(s.color());
        float energy = Math.max(cast, ready);
        Shapes.box(x, y, SIZE, SIZE).radius(6).fillV(0xE0122038, 0xE0060C18)
                .border(0.8f, noMana ? 0xFF4A6BC8 : Gfx.alpha(color, cooling ? 0.4f : 0.9f), Gfx.alpha(color, 0.3f))
                .glow(3 + 6 * energy, Gfx.alpha(color, (cooling ? 0.05f : 0.2f) + 0.6f * energy)).draw(g);
        float iconAlpha = cooling || noMana ? 0.45f : 1f;
        float oldA = Gfx.fade(iconAlpha);
        Shapes.icon(SkillIcons.of(s), cx, cy, SIZE * 0.72f).fillV(Gfx.mix(color, 0xFFFFFFFF, 0.75f), Gfx.mix(color, 0xFFFFFFFF, 0.25f))
                .glow(1.5f + 3 * energy, Gfx.alpha(color, 0.6f)).draw(g);
        Gfx.setAlpha(oldA);

        if (cooling) {
            Shapes.box(x, y, SIZE, SIZE).radius(6).fill(0x70000000).draw(g);
            Shapes.ring(cx, cy, SIZE * 0.38f, 1.6f).fill(0x30FFFFFF).draw(g);
            Shapes.ring(cx, cy, SIZE * 0.38f, 1.6f).fillAround(Gfx.mix(color, 0xFFFFFFFF, 0.4f), color).progress(1 - cd)
                    .glow(2, Gfx.alpha(color, 0.5f)).draw(g);
            float secs = ClientSkills.cooldownSeconds(s, partial);
            String t = secs >= 10 ? String.valueOf(Math.round(secs)) : String.format(java.util.Locale.ROOT, "%.1f", secs);
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

        // key, level and mana cost
        Ui.keycap(g, x - 2, y - 5, 7, key, color);
        SysFont.draw(g, String.valueOf(level), x + SIZE - 1.5f, y + SIZE - 6.5f, 5.5f, Gfx.mix(color, 0xFFFFFFFF, 0.6f), true, 1f);
        if (noMana) {
            SysFont.draw(g, String.valueOf(Math.round(s.manaCost(level))), x + SIZE + 1, y - 4.5f, 5.5f, 0xFF7FA0FF, true, 1f);
        }
    }

    public static void reset() {
        shown = 0;
        java.util.Arrays.fill(wasCooling, false);
    }

    private SkillBar() {}
}
