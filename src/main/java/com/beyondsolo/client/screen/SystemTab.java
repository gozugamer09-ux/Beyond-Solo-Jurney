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

    /** Return true if the click was handled. Coordinates are absolute screen coordinates. */
    default boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        return false;
    }
}
