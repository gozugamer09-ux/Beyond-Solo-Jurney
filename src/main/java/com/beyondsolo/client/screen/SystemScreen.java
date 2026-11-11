package com.beyondsolo.client.screen;

import com.beyondsolo.client.SystemTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** The System window: a holographic panel with tabs (Status, Quests, Log, and anything registered later). */
public class SystemScreen extends Screen {
    public static final int WIDTH = 330;
    public static final int HEIGHT = 200;
    private static final int TAB_H = 14;

    private static ResourceLocation lastTab = SystemTabs.STATUS;

    private final List<SystemTabs.Entry> entries = new ArrayList<>();
    private final List<SystemTab> tabs = new ArrayList<>();
    private int selected;
    private long openedAt;

    public SystemScreen() {
        super(Component.translatable("screen.beyondsolo.system"));
    }

    public static ResourceLocation lastTab() {
        return lastTab;
    }

    public static void openOn(ResourceLocation tab) {
        lastTab = tab;
    }

    @Override
    protected void init() {
        super.init();
        entries.clear();
        tabs.clear();
        for (SystemTabs.Entry e : SystemTabs.entries()) {
            SystemTab tab = e.factory().get();
            if (!tab.isVisible()) continue;
            entries.add(e);
            tabs.add(tab);
        }
        selected = 0;
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).id().equals(lastTab)) selected = i;
        if (!tabs.isEmpty()) tabs.get(selected).onOpened();
        openedAt = System.currentTimeMillis();
    }

    private int left() { return (width - WIDTH) / 2; }
    private int top() { return (height - HEIGHT) / 2; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        float open = Math.min(1f, (System.currentTimeMillis() - openedAt) / 180f);
        int x = left();
        int y = top();
        int alpha = Math.round(255 * open);

        SystemTheme.frame(g, x, y, WIDTH, HEIGHT, SystemTheme.BORDER, alpha);

        // title bar
        Component title = Component.translatable("screen.beyondsolo.system.header");
        g.fill(x + 1, y + 1, x + WIDTH - 1, y + 17, SystemTheme.withAlpha(SystemTheme.GLOW, alpha));
        g.drawCenteredString(font, title, x + WIDTH / 2, y + 5, SystemTheme.ACCENT);

        // tabs
        int tx = x + 6;
        int ty = y + 20;
        for (int i = 0; i < tabs.size(); i++) {
            Component name = tabs.get(i).title();
            int tw = font.width(name) + 12;
            boolean sel = i == selected;
            boolean hover = mouseX >= tx && mouseX < tx + tw && mouseY >= ty && mouseY < ty + TAB_H;
            g.fill(tx, ty, tx + tw, ty + TAB_H, sel ? 0x704FC3F7 : hover ? 0x404FC3F7 : 0x20FFFFFF);
            if (sel) g.fill(tx, ty + TAB_H - 1, tx + tw, ty + TAB_H, SystemTheme.BORDER);
            g.drawString(font, name, tx + 6, ty + 3, sel ? SystemTheme.TEXT : SystemTheme.TEXT_DIM, false);
            tx += tw + 3;
        }
        g.fill(x + 6, ty + TAB_H + 2, x + WIDTH - 6, ty + TAB_H + 3, SystemTheme.BORDER_DIM);

        if (!tabs.isEmpty()) {
            int cx = x + 10;
            int cy = ty + TAB_H + 8;
            tabs.get(selected).render(g, font, cx, cy, WIDTH - 20, HEIGHT - (cy - y) - 8, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int tx = left() + 6;
        int ty = top() + 20;
        for (int i = 0; i < tabs.size(); i++) {
            int tw = font.width(tabs.get(i).title()) + 12;
            if (mouseX >= tx && mouseX < tx + tw && mouseY >= ty && mouseY < ty + TAB_H) {
                if (i != selected) {
                    selected = i;
                    lastTab = entries.get(i).id();
                    tabs.get(i).onOpened();
                    playClick();
                }
                return true;
            }
            tx += tw + 3;
        }
        if (!tabs.isEmpty() && tabs.get(selected).mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!tabs.isEmpty() && tabs.get(selected).mouseScrolled(mouseX, mouseY, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (com.beyondsolo.client.ClientEvents.OPEN_SYSTEM.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    static void playClick() {
        net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.4f));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
