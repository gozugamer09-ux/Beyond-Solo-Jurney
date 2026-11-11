package com.beyondsolo.client.screen;

import com.beyondsolo.client.ClientConfig;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.notify.NotificationManager;
import com.beyondsolo.notify.SystemNotification;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

/** LOG page: every pop-up you received, newest first, plus the notification display mode switch. */
public class LogTab implements SystemTab {
    private int scroll;
    private int modeX, modeY, modeW;

    @Override
    public Component title() {
        int unread = NotificationManager.unread();
        return unread > 0 ? Component.translatable("tab.beyondsolo.log.unread", unread) : Component.translatable("tab.beyondsolo.log");
    }

    @Override
    public void onOpened() {
        NotificationManager.markAllRead();
    }

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        NotificationManager.markAllRead();
        // mode switch
        Component mode = Component.translatable("log.beyondsolo.mode",
                Component.translatable("mode.beyondsolo." + ClientConfig.NOTIFICATION_MODE.get().name().toLowerCase()));
        modeW = font.width(mode) + 10;
        modeX = x + w - modeW;
        modeY = y;
        boolean hover = mouseX >= modeX && mouseX < modeX + modeW && mouseY >= modeY && mouseY < modeY + 13;
        g.fill(modeX, modeY, modeX + modeW, modeY + 13, hover ? 0x704FC3F7 : 0x304FC3F7);
        g.renderOutline(modeX, modeY, modeW, 13, SystemTheme.BORDER_DIM);
        g.drawString(font, mode, modeX + 5, modeY + 3, SystemTheme.TEXT, false);
        g.drawString(font, Component.translatable("log.beyondsolo.header"), x, y + 3, SystemTheme.TEXT_DIM, false);

        List<SystemNotification> history = NotificationManager.history();
        int top = y + 18;
        int bottom = y + h;
        if (history.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("log.beyondsolo.empty"), x + w / 2, top + 30, SystemTheme.TEXT_DIM);
            return;
        }
        scroll = Mth.clamp(scroll, 0, Math.max(0, history.size() - 1));
        g.enableScissor(x, top, x + w, bottom);
        int ly = top;
        for (int i = scroll; i < history.size() && ly < bottom; i++) {
            SystemNotification n = history.get(i);
            g.fill(x, ly, x + 2, ly + 9, n.kind().color());
            g.drawString(font, n.title(), x + 6, ly, SystemTheme.TEXT, false);
            ly += 10;
            for (FormattedCharSequence line : font.split(n.body(), w - 10)) {
                g.drawString(font, line, x + 6, ly, SystemTheme.TEXT_DIM, false);
                ly += 10;
            }
            ly += 4;
        }
        g.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= modeX && mouseX < modeX + modeW && mouseY >= modeY && mouseY < modeY + 13) {
            ClientConfig.cycleMode();
            SystemScreen.playClick();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        scroll -= (int) Math.signum(scrollY);
        if (scroll < 0) scroll = 0;
        return true;
    }
}
