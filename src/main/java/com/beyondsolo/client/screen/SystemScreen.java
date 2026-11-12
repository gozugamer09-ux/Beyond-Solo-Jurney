package com.beyondsolo.client.screen;

import com.beyondsolo.client.ClientEvents;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/** The System window: a glass panel with tabs (Status, Quests, Log, and anything registered later). */
public class SystemScreen extends Screen {
    public static final int WIDTH = 380;
    public static final int HEIGHT = 222;
    private static final float HEADER = 30;
    private static final float TAB_H = 16;
    private static final float TAB_SIZE = 8f;
    private static final float TAB_TRACKING = 0.7f;

    private static ResourceLocation lastTab = SystemTabs.STATUS;

    private final List<SystemTabs.Entry> entries = new ArrayList<>();
    private final List<SystemTab> tabs = new ArrayList<>();
    private final List<float[]> tabRects = new ArrayList<>();
    private int selected;
    private int previous;
    private double openedAt = -1;
    private double closingAt = -1;
    private double tabChangedAt = -10;
    private float indicatorX = Float.NaN;
    private float indicatorW;

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
        previous = selected;
        if (!tabs.isEmpty()) tabs.get(selected).onOpened();
        if (openedAt < 0) {
            openedAt = Anim.now();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.6f, 0.35f));
        }
        indicatorX = Float.NaN;
    }

    private float left() {
        return (width - WIDTH) / 2f;
    }

    private float top() {
        return (height - HEIGHT) / 2f;
    }

    private boolean closing() {
        return closingAt >= 0;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBlurredBackground(partialTick);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float open = Anim.outCubic(Anim.progress(openedAt, 0.22f));
        if (closing()) open *= 1 - Anim.inCubic(Anim.progress(closingAt, 0.13f));
        renderBackground(g, mouseX, mouseY, partialTick);

        float x = left();
        float y = top();
        float oldAlpha = Gfx.setAlpha(open);

        // dim the world towards the edges so the window reads clearly
        Shapes.vignette(0, 0, width, height, 0.15f, 1.5f).fill(0xC8040912).draw(g);
        Shapes.box(0, 0, width, height).fill(0x30040912).draw(g);

        g.pose().pushPose();
        float scale = 0.95f + 0.05f * open;
        g.pose().translate(width / 2f, height / 2f, 0);
        g.pose().scale(scale, scale, 1f);
        g.pose().translate(-width / 2f, -height / 2f, 0);

        Ui.panel(g, x, y, WIDTH, HEIGHT, 11, SystemTheme.ACCENT, 1f);

        // header: mark + title on the left, tabs on the right
        float headerMid = y + HEADER / 2f + 1;
        float pulse = Anim.pulse(2.6f);
        Shapes.icon(Icons.DIAMOND, x + 17, headerMid, 9).fill(SystemTheme.ACCENT_BRIGHT).glow(3 + pulse * 1.5f, Gfx.alpha(SystemTheme.ACCENT, 0.5f + 0.3f * pulse)).draw(g);

        layoutTabs(x, y);
        float tabsLeft = tabRects.isEmpty() ? x + WIDTH : tabRects.get(0)[0];
        String title = Component.translatable("screen.beyondsolo.system.header").getString();
        if (x + 27 + SysFont.spacedWidth(title, 10.5f, true, 2.6f) < tabsLeft - 10) {
            SysFont.spaced(g, title, x + 27, SysFont.centerY(headerMid - 6, 12, 10.5f), 10.5f, SystemTheme.TEXT, true, 2.6f, 0f);
        }

        if (!tabRects.isEmpty()) {
            float[] target = tabRects.get(selected);
            if (Float.isNaN(indicatorX)) {
                indicatorX = target[0];
                indicatorW = target[2];
            }
            indicatorX = Anim.approach(indicatorX, target[0], 20f);
            indicatorW = Anim.approach(indicatorW, target[2], 20f);
            Shapes.pill(indicatorX, target[1], indicatorW, TAB_H)
                    .fillV(Gfx.alpha(SystemTheme.ACCENT, 0.34f), Gfx.alpha(SystemTheme.ACCENT_DEEP, 0.2f))
                    .border(0.75f, Gfx.alpha(SystemTheme.ACCENT, 0.95f), Gfx.alpha(SystemTheme.ACCENT, 0.4f))
                    .glow(5, Gfx.alpha(SystemTheme.ACCENT, 0.3f)).draw(g);
            for (int i = 0; i < tabs.size(); i++) {
                float[] r = tabRects.get(i);
                boolean sel = i == selected;
                boolean hover = !sel && Ui.inside(mouseX, mouseY, r[0], r[1], r[2], TAB_H);
                if (hover) Shapes.pill(r[0], r[1], r[2], TAB_H).fill(0x18FFFFFF).border(0.5f, 0x30FFFFFF).draw(g);
                int color = sel ? SystemTheme.TEXT : hover ? SystemTheme.ACCENT_BRIGHT : SystemTheme.TEXT_SOFT;
                SysFont.spaced(g, tabs.get(i).title().getString(), r[0] + r[2] / 2f, SysFont.centerY(r[1], TAB_H, TAB_SIZE), TAB_SIZE, color, true, TAB_TRACKING, 0.5f);
            }
        }

        Ui.divider(g, x + 12, y + HEADER, WIDTH - 24, Gfx.alpha(SystemTheme.ACCENT, 0.55f));

        if (!tabs.isEmpty()) {
            int cx = Math.round(x + 12);
            int cy = Math.round(y + HEADER + 8);
            int cw = WIDTH - 24;
            int ch = Math.round(HEIGHT - HEADER - 8 - 12);
            float switchT = Anim.outCubic(Anim.progress(tabChangedAt, 0.2f));
            float dir = Integer.compare(selected, previous);
            float inner = Gfx.fade(switchT);
            g.pose().pushPose();
            g.pose().translate(dir * (1 - switchT) * 10f, 0, 0);
            tabs.get(selected).render(g, font, cx, cy, cw, ch, mouseX, mouseY, partialTick);
            g.pose().popPose();
            Gfx.setAlpha(inner);
        }

        g.pose().popPose();
        Gfx.setAlpha(oldAlpha);
    }

    private void layoutTabs(float x, float y) {
        tabRects.clear();
        float total = 0;
        float[] widths = new float[tabs.size()];
        for (int i = 0; i < tabs.size(); i++) {
            widths[i] = SysFont.spacedWidth(tabs.get(i).title().getString(), TAB_SIZE, true, TAB_TRACKING) + 18;
            total += widths[i] + (i > 0 ? 4 : 0);
        }
        float tx = x + WIDTH - 12 - total;
        float ty = y + (HEADER - TAB_H) / 2f + 1;
        for (int i = 0; i < tabs.size(); i++) {
            tabRects.add(new float[]{tx, ty, widths[i]});
            tx += widths[i] + 4;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing()) return true;
        for (int i = 0; i < tabRects.size(); i++) {
            float[] r = tabRects.get(i);
            if (Ui.inside(mouseX, mouseY, r[0], r[1], r[2], TAB_H)) {
                select(i);
                return true;
            }
        }
        if (!tabs.isEmpty() && tabs.get(selected).mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void select(int index) {
        if (index == selected || index < 0 || index >= tabs.size()) return;
        previous = selected;
        selected = index;
        lastTab = entries.get(index).id();
        tabChangedAt = Anim.now();
        tabs.get(index).onOpened();
        playClick();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!tabs.isEmpty() && tabs.get(selected).mouseScrolled(mouseX, mouseY, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ClientEvents.OPEN_SYSTEM.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB && !tabs.isEmpty()) {
            int step = hasShiftDown() ? -1 : 1;
            select(Math.floorMod(selected + step, tabs.size()));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        if (closing() && Anim.progress(closingAt, 0.13f) >= 1f) Minecraft.getInstance().setScreen(null);
    }

    /** Plays the closing animation, then closes. */
    @Override
    public void onClose() {
        if (!closing()) closingAt = Anim.now();
    }

    public static void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.5f, 0.35f));
    }

    public static void playTick(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), pitch, 0.5f));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
