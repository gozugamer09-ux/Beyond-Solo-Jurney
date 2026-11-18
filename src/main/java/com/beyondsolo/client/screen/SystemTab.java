package com.beyondsolo.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * One page of the System window. Register new pages (Quests, Skills, Inventory, Gates...)
 * with {@link SystemTabs#register}.
 */
public interface SystemTab {
    Component title();

    /** Hide the tab, e.g. until the player is evaluated. */
    default boolean isVisible() {
        return true;
    }

    default void onOpened() {}

    /** Draw the page inside the content box (x, y, w, h). */
    void render(GuiGraphics g, Font font, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick);

    /**
     * True if the page is drawn inside a {@link com.beyondsolo.client.gfx.Shapes#batch shape batch}, so its shapes and
     * System text cost a few draw calls instead of one each. Only say true once the page calls
     * {@code Shapes.flush(g)} before every clip ({@code g.enableScissor} / {@code g.disableScissor}), shader colour,
     * blit and mob it draws, or the shapes drawn before them get clipped, tinted or covered.
     */
    default boolean batched() {
        return false;
    }

    /** Return true if the click was handled. Coordinates are absolute screen coordinates. */
    default boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        return false;
    }

    /** A key while the page is open, before the window's own keys; return true if it was handled (typing in a field). */
    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    /** A character typed while the page is open; return true if it was handled. */
    default boolean charTyped(char c, int modifiers) {
        return false;
    }
}
