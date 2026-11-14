package com.beyondsolo.client.screen;

import com.beyondsolo.client.ClientConfig;
import com.beyondsolo.client.ClientEvents;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.notify.NotificationManager;
import com.beyondsolo.notify.SystemNotification;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/** LOG page: every pop-up you received, newest first, plus the pop-up display options. */
public class LogTab implements SystemTab {
    private static final float ROW_H = 16;
    private static final float SEGMENT_W = 46;
    private static final float SWITCH_W = 22;

    private float scroll;
    private float scrollTarget;
    private float maxScroll;
    private float segmentX = Float.NaN;
    private float knob = -1;
    // click areas, set while drawing
    private float modeX, modeY, switchX, switchY, switchHitW;

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
        List<NotificationManager.Entry> history = NotificationManager.history();

        // ---- options row
        SysFont.spaced(g, Component.translatable("log.beyondsolo.header").getString(), x, SysFont.centerY(y, ROW_H, 6.5f), 6.5f, SystemTheme.TEXT_DIM, true, 1.2f, 0f);

        // "stay open" switch on the far right
        boolean keep = ClientConfig.KEEP_POPUPS.get();
        if (knob < 0) knob = keep ? 1 : 0;
        knob = Anim.approach(knob, keep ? 1 : 0, 18f);
        String keepLabel = Component.translatable("log.beyondsolo.keep").getString();
        float labelW = SysFont.width(keepLabel, 7f, true);
        switchX = x + w - SWITCH_W;
        switchY = y + (ROW_H - 11) / 2f;
        switchHitW = SWITCH_W + labelW + 6;
        boolean overSwitch = Ui.inside(mouseX, mouseY, switchX - labelW - 6, y, switchHitW, ROW_H);
        SysFont.title(g, keepLabel, switchX - labelW - 6, SysFont.centerY(y, ROW_H, 7f), 7f, overSwitch ? SystemTheme.TEXT : SystemTheme.TEXT_SOFT);
        int on = Gfx.mix(0xFF2A3D55, SystemTheme.ACCENT, knob);
        Shapes.pill(switchX, switchY, SWITCH_W, 11).fill(Gfx.alpha(on, 0.35f + 0.25f * knob)).border(0.75f, Gfx.alpha(on, 0.9f)).glow(4 * knob, Gfx.alpha(SystemTheme.ACCENT, 0.3f)).draw(g);
        Shapes.circle(switchX + 5.5f + knob * (SWITCH_W - 11), switchY + 5.5f, 3.6f).fill(Gfx.mix(SystemTheme.TEXT_SOFT, 0xFFFFFFFF, knob)).draw(g);

        // display mode: three segments with a sliding highlight
        ClientConfig.NotificationMode mode = ClientConfig.NOTIFICATION_MODE.get();
        ClientConfig.NotificationMode[] modes = ClientConfig.NotificationMode.values();
        float modeW = SEGMENT_W * modes.length;
        modeX = switchX - labelW - 6 - 12 - modeW;
        modeY = y + (ROW_H - 14) / 2f;
        Shapes.pill(modeX, modeY, modeW, 14).fill(SystemTheme.TRACK).border(0.5f, SystemTheme.TRACK_BORDER).draw(g);
        float targetX = modeX + mode.ordinal() * SEGMENT_W;
        segmentX = Float.isNaN(segmentX) ? targetX : Anim.approach(segmentX, targetX, 20f);
        Shapes.pill(segmentX + 1, modeY + 1, SEGMENT_W - 2, 12).fillV(Gfx.alpha(SystemTheme.ACCENT, 0.4f), Gfx.alpha(SystemTheme.ACCENT_DEEP, 0.25f))
                .border(0.75f, Gfx.alpha(SystemTheme.ACCENT, 0.9f)).glow(4, Gfx.alpha(SystemTheme.ACCENT, 0.25f)).draw(g);
        for (ClientConfig.NotificationMode m : modes) {
            float sx = modeX + m.ordinal() * SEGMENT_W;
            boolean hover = Ui.inside(mouseX, mouseY, sx, modeY, SEGMENT_W, 14);
            int color = m == mode ? SystemTheme.TEXT : hover ? SystemTheme.ACCENT_BRIGHT : SystemTheme.TEXT_DIM;
            SysFont.draw(g, Component.translatable("mode.beyondsolo." + m.name().toLowerCase()).getString(), sx + SEGMENT_W / 2f, SysFont.centerY(modeY, 14, 7f), 7f, color, true, 0.5f);
        }

        // ---- message list
        int top = y + 22;
        int bottom = y + h;
        if (history.isEmpty()) {
            Shapes.icon(Icons.BELL, x + w / 2f, top + 40, 24).fill(Gfx.alpha(SystemTheme.TEXT_DIM, 0.55f)).draw(g);
            SysFont.draw(g, Component.translatable("log.beyondsolo.empty"), x + w / 2f, top + 60, 8.5f, SystemTheme.TEXT_DIM, false, 0.5f);
            return;
        }

        scroll = Anim.approach(scroll, scrollTarget, 16f);
        float listW = w - 8;
        g.flush();
        g.enableScissor(x - 2, top, x + w, bottom);
        float ly = top - scroll;
        float content = 0;
        for (NotificationManager.Entry entry : history) {
            SystemNotification n = entry.notification();
            List<SysFont.Line> lines = SysFont.wrap(n.body(), 8f, false, listW - 34);
            float cardH = 20 + lines.size() * 8f * SysFont.LINE + 4;
            if (ly + cardH > top - 4 && ly < bottom + 4) {
                int accent = n.kind().color();
                boolean hover = Ui.inside(mouseX, mouseY, x, Math.max(ly, top), listW, Math.min(cardH, bottom - Math.max(ly, top)));
                Ui.card(g, x, ly, listW, cardH, 7, accent, hover ? 0.3f : 0.12f);
                Ui.iconBadge(g, Icons.of(n.kind()), x + 13, ly + 13, 7.5f, accent);
                SysFont.title(g, SysFont.fit(n.title().getString(), 9f, true, listW - 34 - 44), x + 26, ly + 6.5f, 9f, SystemTheme.TEXT);
                SysFont.draw(g, age(entry.receivedAt()), x + listW - 8, ly + 7.5f, 6.5f, SystemTheme.TEXT_DIM, false, 1f);
                float by = ly + 19;
                for (SysFont.Line line : lines) {
                    SysFont.draw(g, line, x + 26, by, SystemTheme.TEXT_SOFT, 0f);
                    by += 8f * SysFont.LINE;
                }
            }
            ly += cardH + 4;
            content += cardH + 4;
        }
        g.flush();
        g.disableScissor();

        float view = bottom - top;
        maxScroll = Math.max(0, content - 4 - view);
        scrollTarget = Mth.clamp(scrollTarget, 0, maxScroll);
        if (maxScroll > 0) {
            float trackH = view;
            float thumbH = Math.max(18, trackH * view / (content - 4));
            float thumbY = top + (trackH - thumbH) * (scroll / maxScroll);
            Shapes.pill(x + w - 3, top, 3, trackH).fill(SystemTheme.TRACK).draw(g);
            Shapes.pill(x + w - 3, thumbY, 3, thumbH).fill(Gfx.alpha(SystemTheme.ACCENT, 0.8f)).glow(3, Gfx.alpha(SystemTheme.ACCENT, 0.3f)).draw(g);
        }

        // hint for the hide key
        String key = ClientEvents.DISMISS.getTranslatedKeyMessage().getString();
        if (key.length() <= 6 && maxScroll <= 0 && content + 16 < view) {
            float hy = bottom - 11;
            String hint = Component.translatable("log.beyondsolo.hint").getString();
            float kw = Ui.keycapWidth(10, key);
            float total = kw + 5 + SysFont.width(hint, 7f, false);
            float hx = x + (w - total) / 2f;
            Ui.keycap(g, hx, hy, 10, key, SystemTheme.TEXT_DIM);
            SysFont.text(g, hint, hx + kw + 5, SysFont.centerY(hy, 10, 7f), 7f, SystemTheme.TEXT_DIM);
        }
    }

    private static String age(long receivedAt) {
        long seconds = Math.max(0, (System.currentTimeMillis() - receivedAt) / 1000);
        if (seconds < 60) return Component.translatable("log.beyondsolo.age.now").getString();
        if (seconds < 3600) return Component.translatable("log.beyondsolo.age.minutes", seconds / 60).getString();
        return Component.translatable("log.beyondsolo.age.hours", seconds / 3600).getString();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ClientConfig.NotificationMode[] modes = ClientConfig.NotificationMode.values();
        if (Ui.inside(mouseX, mouseY, modeX, modeY, SEGMENT_W * modes.length, 14)) {
            int index = Mth.clamp((int) ((mouseX - modeX) / SEGMENT_W), 0, modes.length - 1);
            if (modes[index] != ClientConfig.NOTIFICATION_MODE.get()) {
                ClientConfig.setMode(modes[index]);
                SystemScreen.playClick();
            }
            return true;
        }
        if (Ui.inside(mouseX, mouseY, switchX + SWITCH_W - switchHitW, switchY - 3, switchHitW, 17)) {
            ClientConfig.toggleKeepPopups();
            SystemScreen.playClick();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        scrollTarget = Mth.clamp(scrollTarget - (float) scrollY * 28f, 0, maxScroll);
        return true;
    }
}
