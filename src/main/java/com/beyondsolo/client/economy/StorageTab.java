package com.beyondsolo.client.economy;

import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.network.EconomyActionPayload;
import com.beyondsolo.client.screen.SystemTab;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The STORAGE page of the System window. Its slots need a real container window, so picking the tab asks the
 * server to open {@link StorageScreen}, which draws the same window around the storage; this page only shows
 * for the moment that takes.
 */
public class StorageTab implements SystemTab {
    private double openedAt = -10;

    @Override
    public Component title() {
        return Component.translatable("tab.beyondsolo.storage");
    }

    @Override
    public boolean isVisible() {
        return ClientSystemData.get().hasSystem();
    }

    @Override
    public void onOpened() {
        openedAt = Anim.now();
        EconomyClient.send(EconomyActionPayload.OPEN_STORAGE, "", 0);
    }

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        float cx = x + w / 2f;
        float cy = y + h / 2f - 6;
        float t = Anim.loop(1.2f);
        Shapes.ring(cx, cy, 14, 1.5f).fill(Gfx.alpha(SystemTheme.ACCENT, 0.8f)).progress(0.3f).rotation(t).glow(3, Gfx.alpha(SystemTheme.ACCENT, 0.5f)).draw(g);
        Shapes.icon(Icons.VAULT, cx, cy, 13).fill(SystemTheme.ACCENT_BRIGHT).draw(g);
        if (Anim.since(openedAt) > 0.4f) {
            SysFont.draw(g, Component.translatable("storage.beyondsolo.opening"), cx, cy + 22, 7f, SystemTheme.TEXT_SOFT, false, 0.5f);
        }
    }
}
