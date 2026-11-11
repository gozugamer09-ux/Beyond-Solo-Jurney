package com.beyondsolo.client.notify;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.notify.SystemNotification;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Layout of one pop-up window, drawn through a {@link Canvas} so the same code works
 * on the HUD (pinned) and in the world (floating).
 */
public final class NotificationPanel {
    public static final int WIDTH = 200;
    private static final int PAD = 7;
    private static final int LINE = 10;

    public interface Canvas {
        void fill(int x1, int y1, int x2, int y2, int argb);

        /** Called once between all fills and all text, so text always ends up on top. */
        default void beginText() {}

        void text(FormattedCharSequence text, int x, int y, int argb);
    }

    private final SystemNotification notification;
    private final List<FormattedCharSequence> bodyLines;
    private final int height;

    public NotificationPanel(Font font, SystemNotification notification) {
        this.notification = notification;
        this.bodyLines = font.split(notification.body(), WIDTH - PAD * 2);
        this.height = PAD + LINE + 4 + LINE + 4 + bodyLines.size() * LINE + PAD - 2;
    }

    public int height() {
        return height;
    }

    /** Draws with the top-left corner at (x, y). {@code alpha} 0-255 fades the whole window. */
    public void draw(Font font, Canvas c, int x, int y, int alpha) {
        if (alpha < 8) return;
        int accent = notification.kind().color();
        int w = WIDTH;
        int h = height;
        // body
        c.fill(x, y, x + w, y + h, SystemTheme.withAlpha(SystemTheme.BACKGROUND, alpha));
        // header strip
        c.fill(x, y, x + w, y + PAD + LINE + 1, SystemTheme.withAlpha(0x40000000 | (accent & 0xFFFFFF), alpha));
        // border
        int b = SystemTheme.withAlpha(accent, alpha);
        int bDim = SystemTheme.withAlpha(accent, alpha / 2);
        c.fill(x, y, x + w, y + 1, bDim);
        c.fill(x, y + h - 1, x + w, y + h, bDim);
        c.fill(x, y, x + 1, y + h, bDim);
        c.fill(x + w - 1, y, x + w, y + h, bDim);
        int len = 9;
        c.fill(x - 1, y - 1, x + len, y + 1, b);
        c.fill(x - 1, y - 1, x + 1, y + len, b);
        c.fill(x + w - len, y - 1, x + w + 1, y + 1, b);
        c.fill(x + w - 1, y - 1, x + w + 1, y + len, b);
        c.fill(x - 1, y + h - 1, x + len, y + h + 1, b);
        c.fill(x - 1, y + h - len, x + 1, y + h + 1, b);
        c.fill(x + w - len, y + h - 1, x + w + 1, y + h + 1, b);
        c.fill(x + w - 1, y + h - len, x + w + 1, y + h + 1, b);

        c.beginText();
        Component header = notification.kind().header();
        int ty = y + PAD;
        c.text(header.getVisualOrderText(), x + (w - font.width(header)) / 2, ty, SystemTheme.withAlpha(accent, alpha));
        ty += LINE + 4;
        c.text(notification.title().getVisualOrderText(), x + (w - font.width(notification.title())) / 2, ty,
                SystemTheme.withAlpha(SystemTheme.TEXT, alpha));
        ty += LINE + 4;
        for (FormattedCharSequence line : bodyLines) {
            c.text(line, x + (w - font.width(line)) / 2, ty, SystemTheme.withAlpha(SystemTheme.TEXT_DIM | 0xFF000000, alpha));
            ty += LINE;
        }
    }

    public static int alpha(float openAmount) {
        return Math.round(255 * openAmount);
    }

    public static SystemNotification.Kind kindOf(NotificationPanel p) {
        return p.notification.kind();
    }
}
