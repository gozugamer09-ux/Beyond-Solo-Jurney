package com.beyondsolo.client.hud;

import com.beyondsolo.client.ClientConfig;
import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.system.Objectives;
import com.beyondsolo.system.SystemData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** The small status capsule in the top-left corner, the objective chip under it, and the collapse overlay. */
public final class StatusHud {
    private static float shownMana = -1;
    private static float shownFatigue;
    private static float fatigueVisible;
    private static float collapse;
    private static int collapseTotal;
    private static float objectiveShown;
    private static String lastObjective = "";
    private static double objectiveChangedAt = -10;
    /** Set by the evaluation reveal so the HUD does not sit on top of it. */
    private static boolean hidden;
    private static float visible = 1;

    public static void setHidden(boolean value) {
        hidden = value;
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        SystemData d = ClientSystemData.get();

        collapse = Anim.approach(collapse, d.isCollapsed() ? 1 : 0, d.isCollapsed() ? 5f : 3f);
        if (d.isCollapsed()) collapseTotal = Math.max(collapseTotal, d.collapseTicks());
        else if (collapse < 0.02f) collapseTotal = 0;
        if (collapse > 0.01f) drawCollapse(g, d);

        visible = Anim.approach(visible, hidden ? 0 : 1, 8f);
        if (!ClientConfig.SHOW_HUD.get() || mc.getDebugOverlay().showDebugScreen() || visible < 0.02f) return;
        float old = Gfx.fade(visible);
        float x = 8;
        float y = 8;

        if (d.isAwakened()) {
            float mana = d.maxMana() <= 0 ? 0 : d.mana() / d.maxMana();
            shownMana = shownMana < 0 ? mana : Anim.approach(shownMana, mana, 9f);
            shownFatigue = Anim.approach(shownFatigue, d.exhaustion() / 100f, 9f);
            fatigueVisible = Anim.approach(fatigueVisible, d.exhaustion() > 0.5f ? 1 : 0, 8f);

            float w = 118;
            float h = 30;
            int rankColor = SystemTheme.rank(d.rank());
            Shapes.box(x + 1, y + 2, w - 2, h - 2).radius(h / 2f).fill(0x00000000).glow(8, 0x50000000).draw(g);
            Shapes.pill(x, y, w, h).fillV(Gfx.fade(SystemTheme.GLASS_TOP, 0.88f), Gfx.fade(SystemTheme.GLASS_BOTTOM, 0.88f))
                    .border(0.75f, Gfx.alpha(SystemTheme.ACCENT, 0.75f), Gfx.alpha(SystemTheme.ACCENT, 0.22f))
                    .glow(6, Gfx.alpha(SystemTheme.ACCENT, 0.12f)).draw(g);
            Ui.rankEmblem(g, d.rank(), x + 15, y + 15, 9f, 0f);

            float tx = x + 31;
            float barW = w - 31 - 12;
            String level = Component.translatable("hud.beyondsolo.level", d.level()).getString();
            SysFont.title(g, level, tx, y + 5.5f, 9.5f, SystemTheme.TEXT);
            String className = SysFont.fit(d.playerClass().displayName().getString(), 7f, true, barW - SysFont.width(level, 9.5f, true) - 6);
            SysFont.draw(g, className, tx + barW, y + 7f, 7f, Gfx.mix(SystemTheme.playerClass(d.playerClass()), 0xFFFFFFFF, 0.25f), true, 1f);

            // mana, and fatigue once you have some
            float manaY = y + 17.5f - 2.2f * fatigueVisible + 1.2f;
            Ui.bar(g, tx, manaY, barW, 3.6f, shownMana, SystemTheme.MP_A, SystemTheme.MP_B);
            if (fatigueVisible > 0.03f) {
                boolean critical = d.exhaustion() >= 80;
                float oldBar = Gfx.fade(fatigueVisible);
                Ui.bar(g, tx, manaY + 5.6f, barW, 2.6f, shownFatigue,
                        critical ? SystemTheme.HP_A : SystemTheme.FATIGUE_A,
                        critical ? Gfx.mix(SystemTheme.HP_B, 0xFFFFFFFF, Anim.pulse(0.7f) * 0.5f) : SystemTheme.FATIGUE_B);
                Gfx.setAlpha(oldBar);
            }
            y += h + 6;
        }

        // objective chip
        String objective = d.objective();
        if (!objective.equals(lastObjective)) {
            if (!objective.isEmpty()) objectiveChangedAt = Anim.now();
            lastObjective = objective.isEmpty() ? lastObjective : objective;
        }
        objectiveShown = Anim.approach(objectiveShown, objective.isEmpty() ? 0 : 1, 7f);
        if (objectiveShown > 0.02f && !lastObjective.isEmpty()) {
            if (objective.isEmpty() && objectiveShown < 0.03f) lastObjective = "";
            String title = Objectives.title(lastObjective).getString();
            float tw = SysFont.width(title, 8f, true);
            float w = tw + 28;
            float h = 16;
            float fresh = 1 - Anim.outCubic(Anim.progress(objectiveChangedAt, 1.2f));
            float oldChip = Gfx.fade(objectiveShown);
            float ox = x - (1 - objectiveShown) * 10;
            Shapes.pill(ox, y, w, h).fillV(Gfx.fade(SystemTheme.GLASS_TOP, 0.85f), Gfx.fade(SystemTheme.GLASS_BOTTOM, 0.85f))
                    .border(0.75f, Gfx.alpha(SystemTheme.GOLD, 0.8f), Gfx.alpha(SystemTheme.GOLD, 0.3f))
                    .glow(5 + fresh * 8, Gfx.alpha(SystemTheme.GOLD, 0.14f + 0.4f * fresh)).draw(g);
            Shapes.icon(Icons.PIN, ox + 10.5f, y + h / 2f, 9).fill(SystemTheme.GOLD).glow(2 + fresh * 3, Gfx.alpha(SystemTheme.GOLD, 0.6f)).draw(g);
            SysFont.title(g, title, ox + 19, SysFont.centerY(y, h, 8f), 8f, Gfx.mix(SystemTheme.GOLD, 0xFFFFFFFF, 0.25f + 0.5f * fresh));
            Gfx.setAlpha(oldChip);
        }
        Gfx.setAlpha(old);
    }

    private static void drawCollapse(GuiGraphics g, SystemData d) {
        int w = g.guiWidth();
        int h = g.guiHeight();
        float old = Gfx.fade(collapse);
        float pulse = Anim.pulse(1.9f);
        Shapes.box(0, 0, w, h).fill(0x66140206).draw(g);
        Shapes.vignette(0, 0, w, h, 0.25f, 1.25f).fill(Gfx.alpha(0xFF8A0A18, 0.72f + 0.14f * pulse)).draw(g);

        float cx = w / 2f;
        float cy = h / 2f - 4;
        float total = Math.max(1, collapseTotal);
        float left = Math.min(1f, d.collapseTicks() / total);
        int seconds = (d.collapseTicks() + 19) / 20;

        SysFont.spaced(g, Component.translatable("hud.beyondsolo.collapsed").getString(), cx, cy - 62, 16, SystemTheme.DANGER, true, 5f, 0.5f);
        Shapes.circle(cx, cy, 27).fillV(0xB0200810, 0xB00C0206).glow(18 + pulse * 6, Gfx.alpha(SystemTheme.DANGER, 0.22f)).draw(g);
        Shapes.ring(cx, cy, 30, 2.4f).fill(0x40FFFFFF).draw(g);
        Shapes.ring(cx, cy, 30, 2.4f).fillAround(0xFFFFB0A0, SystemTheme.DANGER).progress(left).glow(5, Gfx.alpha(SystemTheme.DANGER, 0.6f)).draw(g);
        Shapes.ring(cx, cy, 37, 0.75f).fill(Gfx.alpha(SystemTheme.DANGER, 0.5f)).dashes(36, 0.4f).rotation(-Anim.loop(26f)).draw(g);
        SysFont.draw(g, String.valueOf(seconds), cx, SysFont.centerY(cy - 10, 20, 20), 20, SystemTheme.TEXT, true, 0.5f);
        SysFont.draw(g, Component.translatable("hud.beyondsolo.collapsed.hint"), cx, cy + 50, 8.5f, SystemTheme.TEXT_SOFT, false, 0.5f);
        Gfx.setAlpha(old);
    }

    public static void reset() {
        shownMana = -1;
        shownFatigue = 0;
        fatigueVisible = 0;
        collapse = 0;
        collapseTotal = 0;
        objectiveShown = 0;
        lastObjective = "";
        hidden = false;
        visible = 1;
    }

    private StatusHud() {}
}
