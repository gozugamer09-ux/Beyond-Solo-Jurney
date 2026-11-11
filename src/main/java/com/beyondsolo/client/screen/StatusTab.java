package com.beyondsolo.client.screen;

import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.network.AllocateStatPayload;
import com.beyondsolo.system.Stat;
import com.beyondsolo.system.SystemData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** The STATUS page: name, level, rank, class, bars and the five stats with [+] buttons. */
public class StatusTab implements SystemTab {
    private record PlusButton(int x, int y, Stat stat) {
        static final int SIZE = 11;

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + SIZE && my >= y && my < y + SIZE;
        }
    }

    private final List<PlusButton> buttons = new ArrayList<>();

    @Override
    public Component title() {
        return Component.translatable("tab.beyondsolo.status");
    }

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        buttons.clear();
        SystemData d = ClientSystemData.get();
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        if (!d.isAwakened()) {
            int cy = y + h / 2 - 20;
            g.drawCenteredString(font, Component.translatable("status.beyondsolo.unevaluated"), x + w / 2, cy, SystemTheme.FATIGUE);
            for (var line : font.split(Component.translatable("status.beyondsolo.unevaluated.desc"), w - 40)) {
                cy += 12;
                g.drawCenteredString(font, line, x + w / 2, cy, SystemTheme.TEXT_DIM);
            }
            return;
        }

        int col2 = x + w / 2 + 4;
        int ly = y;
        label(g, font, "status.beyondsolo.name", player.getName(), x, ly, SystemTheme.TEXT);
        label(g, font, "status.beyondsolo.level", Component.literal(String.valueOf(d.level())), col2, ly, SystemTheme.TEXT);
        ly += 12;
        label(g, font, "status.beyondsolo.class", d.playerClass().displayName(), x, ly, 0xFF000000 | d.playerClass().color());
        label(g, font, "status.beyondsolo.rank", d.rank().displayName(), col2, ly, 0xFF000000 | d.rank().color());
        ly += 12;
        Component titleText = d.title().isEmpty() ? Component.translatable("status.beyondsolo.title.none") : Component.translatable(d.title());
        label(g, font, "status.beyondsolo.title", titleText, x, ly, SystemTheme.TEXT_DIM);
        ly += 15;

        // bars
        int barW = w / 2 - 24 - 52;
        bar(g, font, "HP", player.getHealth(), player.getMaxHealth(), x, ly, barW, SystemTheme.HP, false);
        bar(g, font, "MP", d.mana(), d.maxMana(), col2, ly, barW, SystemTheme.MP, false);
        ly += 13;
        bar(g, font, "XP", d.xp(), d.xpToNextLevel(), x, ly, barW, SystemTheme.XP, false);
        bar(g, font, Component.translatable("status.beyondsolo.fatigue").getString(), d.exhaustion(), 100, col2, ly, barW, SystemTheme.FATIGUE, true);
        ly += 16;

        g.fill(x, ly, x + w, ly + 1, SystemTheme.BORDER_DIM);
        ly += 6;

        // stats in two columns
        Stat[] stats = Stat.values();
        Component hovered = null;
        for (int i = 0; i < stats.length; i++) {
            Stat s = stats[i];
            int sx = i % 2 == 0 ? x : col2;
            int sy = ly + (i / 2) * 15;
            Component name = s.displayName();
            g.drawString(font, name, sx, sy + 2, SystemTheme.ACCENT, false);
            g.drawString(font, String.valueOf(d.stat(s)), sx + 52, sy + 2, SystemTheme.TEXT, false);
            if (mouseX >= sx && mouseX < sx + 50 && mouseY >= sy && mouseY < sy + 12) hovered = s.description();
            if (d.statPoints() > 0) {
                PlusButton b = new PlusButton(sx + 80, sy, s);
                buttons.add(b);
                boolean hover = b.contains(mouseX, mouseY);
                g.fill(b.x(), b.y(), b.x() + PlusButton.SIZE, b.y() + PlusButton.SIZE, hover ? 0xA04FC3F7 : 0x504FC3F7);
                g.renderOutline(b.x(), b.y(), PlusButton.SIZE, PlusButton.SIZE, SystemTheme.BORDER);
                g.drawCenteredString(font, "+", b.x() + 6, b.y() + 2, SystemTheme.TEXT);
            }
        }
        int pointsY = ly + 3 * 15 + 2;
        Component points = Component.translatable("status.beyondsolo.points", d.statPoints());
        g.drawString(font, points, col2, pointsY - 15 + 2, d.statPoints() > 0 ? SystemTheme.FATIGUE : SystemTheme.TEXT_DIM, false);

        if (hovered != null) g.renderTooltip(font, font.split(hovered, 180), mouseX, mouseY);
    }

    private static void label(GuiGraphics g, Font font, String key, Component value, int x, int y, int valueColor) {
        Component label = Component.translatable(key);
        g.drawString(font, label, x, y, SystemTheme.TEXT_DIM, false);
        g.drawString(font, value, x + font.width(label) + 4, y, valueColor, false);
    }

    private static void bar(GuiGraphics g, Font font, String name, double value, double max, int x, int y, int w, int color, boolean percent) {
        g.drawString(font, name, x, y, SystemTheme.TEXT_DIM, false);
        int bx = x + 24;
        SystemTheme.bar(g, bx, y + 1, w, 6, (float) (max <= 0 ? 0 : value / max), color);
        String text = percent ? Math.round(value) + "%" : Math.round(value) + "/" + Math.round(max);
        g.drawString(font, text, bx + w + 3, y, SystemTheme.TEXT, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (PlusButton b : buttons) {
            if (b.contains(mouseX, mouseY)) {
                PacketDistributor.sendToServer(new AllocateStatPayload(b.stat().ordinal()));
                SystemScreen.playClick();
                return true;
            }
        }
        return false;
    }
}
