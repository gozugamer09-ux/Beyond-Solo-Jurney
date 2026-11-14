package com.beyondsolo.client.notify;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.notify.SystemNotification;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Layout and drawing of one pop-up window. */
public final class PopupPanel {
    public static final float WIDTH = 214;
    private static final float PAD = 10;
    private static final float TEXT_X = PAD + 6;
    private static final float TITLE_SIZE = 10.5f;
    private static final float BODY_SIZE = 8.5f;

    private final SystemNotification notification;
    private final List<SysFont.Line> titleLines;
    private final List<SysFont.Line> bodyLines;
    private final float height;

    public PopupPanel(SystemNotification notification) {
        this.notification = notification;
        float textW = WIDTH - TEXT_X - PAD;
        this.titleLines = SysFont.wrap(notification.title(), TITLE_SIZE, true, textW);
        this.bodyLines = SysFont.wrap(notification.body(), BODY_SIZE, false, textW);
        this.height = 28 + titleLines.size() * TITLE_SIZE * 1.2f + 2 + bodyLines.size() * BODY_SIZE * SysFont.LINE + 8;
    }

    public float height() {
        return height;
    }

    /**
     * Draws the pop-up with its top-left corner at (x, y), including its own enter/exit animation.
     *
     * @param hint     key name to show as "[key] hide" in the header, or null
     * @param timeLeft 0..1 for the remaining-time line, or a negative number to hide the line
     */
    public void draw(GuiGraphics g, float x, float y, NotificationManager.Popup popup, String hint, float timeLeft) {
        float appear = popup.appear();
        float exit = popup.exit();
        float alpha = Anim.outCubic(appear) * (1 - Anim.inCubic(exit));
        if (alpha <= 0.01f) return;
        float scale = Anim.lerp(0.86f, 1f, Anim.outBack(appear)) * (1 - 0.06f * exit);
        int accent = notification.kind().color();
        float w = WIDTH;
        float h = height;

        g.pose().pushPose();
        g.pose().translate(x + w / 2f, y + h / 2f, 0);
        g.pose().scale(scale, scale, 1f);
        g.pose().translate(-w / 2f, -h / 2f, 0);
        float old = Gfx.fade(alpha);

        // glass, with a flash of light and a shine sweep when it arrives
        float flash = 1 - Anim.outCubic(Anim.progress(popup.shownAt, 0.9f));
        float sweep = Anim.progress(popup.shownAt + 0.08, 0.75f);
        Shapes.box(1, 3, w - 2, h - 2).radius(9).fill(0x00000000).glow(12, 0x66000000).draw(g);
        Shapes.box(0, 0, w, h).radius(9)
                .fillV(Gfx.mix(SystemTheme.GLASS_TOP, Gfx.alpha(accent, 0.95f), 0.10f + 0.12f * flash), SystemTheme.GLASS_BOTTOM)
                .border(1f, Gfx.mix(accent, 0xFFFFFFFF, 0.15f + 0.5f * flash), Gfx.fade(accent, 0.3f))
                .glow(9 + 7 * flash, Gfx.alpha(accent, 0.2f + 0.35f * flash))
                .sheen(sweep * 1.5f - 0.25f, sweep < 1 ? 0.16f * (1 - sweep) + 0.04f : 0f)
                .draw(g);
        // accent stripe on the left
        Shapes.pill(4.5f, 10, 2f, h - 20).fillV(Gfx.mix(accent, 0xFFFFFFFF, 0.4f), accent).glow(4, Gfx.alpha(accent, 0.55f)).draw(g);

        // header: kind badge, kind name, hide hint
        Ui.iconBadge(g, Icons.of(notification.kind()), TEXT_X + 6.5f, 15, 6.5f, accent);
        SysFont.spaced(g, notification.kind().header().getString(), TEXT_X + 17, SysFont.centerY(9, 12, 6.8f), 6.8f, Gfx.mix(accent, 0xFFFFFFFF, 0.25f), true, 1.3f, 0f);
        if (hint != null) {
            String label = Component.translatable("popup.beyondsolo.hide").getString();
            float lw = SysFont.width(label, 6.2f, true);
            float kw = Ui.keycapWidth(9, hint);
            float hx = w - PAD - lw - 3 - kw;
            float oldHint = Gfx.fade(0.75f);
            Ui.keycap(g, hx, 10.5f, 9, hint, SystemTheme.TEXT_SOFT);
            SysFont.draw(g, label, hx + kw + 3, SysFont.centerY(10.5f, 9, 6.2f), 6.2f, SystemTheme.TEXT_DIM, true, 0f);
            Gfx.setAlpha(oldHint);
        }

        float ty = 27;
        for (SysFont.Line line : titleLines) {
            SysFont.draw(g, line, TEXT_X, ty, SystemTheme.TEXT, 0f);
            ty += TITLE_SIZE * 1.2f;
        }
        ty += 2;

        // the body types itself out
        int total = 0;
        for (SysFont.Line line : bodyLines) total += SysFont.length(line);
        float typeTime = Math.min(0.9f, total * 0.016f);
        int visible = popup.recalled || typeTime <= 0 ? Integer.MAX_VALUE : Math.round(total * Anim.progress(popup.shownAt + 0.16, typeTime));
        for (SysFont.Line line : bodyLines) {
            int length = SysFont.length(line);
            if (visible <= 0) break;
            SysFont.draw(g, visible >= length ? line : SysFont.truncate(line, visible), TEXT_X, ty, SystemTheme.TEXT_SOFT, 0f);
            visible -= length;
            ty += BODY_SIZE * SysFont.LINE;
        }

        if (timeLeft >= 0) {
            float full = w - TEXT_X - PAD;
            Shapes.pill(TEXT_X, h - 5.5f, full, 1.4f).fill(0x30FFFFFF).draw(g);
            if (timeLeft > 0.01f) Shapes.pill(TEXT_X, h - 5.5f, Math.max(1.4f, full * timeLeft), 1.4f).fill(Gfx.alpha(accent, 0.9f)).glow(2.5f, Gfx.alpha(accent, 0.5f)).draw(g);
        }

        Gfx.setAlpha(old);
        g.pose().popPose();
    }
}
