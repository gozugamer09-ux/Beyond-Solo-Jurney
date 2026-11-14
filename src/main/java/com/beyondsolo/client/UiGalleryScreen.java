package com.beyondsolo.client;

import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.Stat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Dev-only page that draws every System UI building block, to check the shader, font and icons at a glance. */
public class UiGalleryScreen extends Screen {
    public UiGalleryScreen() {
        super(Component.literal("System UI gallery"));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF0A1220);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        Ui.panel(g, 10, 10, 190, 104, 10, SystemTheme.ACCENT, 1f);
        SysFont.spaced(g, "SYSTEM", 22, 20, 10.5f, SystemTheme.TEXT, true, 2.6f, 0f);
        Ui.bar(g, 22, 38, 150, 7, 0.62f, SystemTheme.HP_A, SystemTheme.HP_B);
        Ui.bar(g, 22, 50, 150, 7, 0.35f, SystemTheme.MP_A, SystemTheme.MP_B);
        Ui.bar(g, 22, 62, 150, 5, 0.85f, SystemTheme.XP_A, SystemTheme.XP_B);
        Ui.bar(g, 22, 72, 150, 3.5f, 0.03f, SystemTheme.FATIGUE_A, SystemTheme.FATIGUE_B);
        float cx = 22;
        cx += Ui.chip(g, cx, 84, 13, "QUEST", 7f, SystemTheme.GOLD, true) + 5;
        cx += Ui.chip(g, cx, 84, 13, "WARNING", 7f, SystemTheme.DANGER, false) + 5;
        cx += Ui.keycap(g, cx, 85, 11, "B", SystemTheme.TEXT_SOFT) + 5;
        Ui.keycap(g, cx, 85, 11, "Shift", SystemTheme.TEXT_SOFT);

        // text sizes
        float ty = 122;
        for (float size : new float[]{7, 8, 9, 10, 12, 14, 18, 24}) {
            SysFont.text(g, "Evaluation complete 0123456789", 12, ty, size, SystemTheme.TEXT);
            SysFont.title(g, "Rank S Awakened", 12 + SysFont.width("Evaluation complete 0123456789", size, false) + 8, ty, size, SystemTheme.ACCENT);
            ty += size * 1.35f;
        }

        // rings
        float t = Anim.loop(4f);
        Shapes.ring(240, 50, 26, 3).fillAround(SystemTheme.ACCENT_DEEP, SystemTheme.ACCENT_BRIGHT).progress(t).glow(6, Gfx.alpha(SystemTheme.ACCENT, 0.5f)).draw(g);
        Shapes.ring(240, 50, 33, 1).fill(Gfx.alpha(SystemTheme.ACCENT, 0.7f)).dashes(24, 0.45f).rotation(Anim.loop(20f)).draw(g);
        Shapes.icon(Icons.DIAMOND, 240, 50, 22).fill(SystemTheme.ACCENT_BRIGHT).glow(5, Gfx.alpha(SystemTheme.ACCENT, 0.6f)).draw(g);

        // rank emblems
        float rx = 290;
        for (PlayerRank rank : PlayerRank.values()) {
            Ui.rankEmblem(g, rank, rx, 30, 12, 0.3f);
            rx += 36;
        }
        Ui.rankEmblem(g, PlayerRank.S, 320, 84, 26, 1f);
        Shapes.rays(420, 84, 60, 14).fill(Gfx.alpha(SystemTheme.GOLD, 0.8f)).rotation(Anim.loop(30f)).additive().draw(g);
        Ui.rankEmblem(g, PlayerRank.A, 420, 84, 20, 0.6f);

        // icons
        float ix = 290;
        for (Stat s : Stat.values()) {
            Ui.iconBadge(g, Icons.of(s), ix, 132, 9, SystemTheme.ACCENT);
            ix += 26;
        }
        ix = 290;
        for (PlayerClass c : PlayerClass.values()) {
            Shapes.icon(Icons.of(c), ix, 164, 22).fill(SystemTheme.playerClass(c)).glow(4, Gfx.alpha(SystemTheme.playerClass(c), 0.6f)).draw(g);
            ix += 30;
        }
        ix = 290;
        for (SystemNotification.Kind k : SystemNotification.Kind.values()) {
            Ui.iconBadge(g, Icons.of(k), ix, 196, 8, k.color());
            ix += 24;
        }
        Shapes.vignette(0, 0, width, height, 0.7f, 1.4f).fill(0x90000000).draw(g);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
