package com.beyondsolo.client.chronicle;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.news.NewsClient;
import com.beyondsolo.client.news.NewsTicker;
import com.beyondsolo.news.Headline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * NEWS: the hunter news as the country hears it. The masthead on the left (the day, how many headlines, the latest
 * breaking story); the headlines on the right, the newest first, each with its mark, when it ran, and a red BREAKING
 * tag for the big ones.
 */
final class NewsPage implements ChronicleTab.Page {
    private static final float SIDE_W = 104;
    private static final float ROW_H = 24;

    private static float scroll, scrollTarget;
    private float listX, listY, listW, listH, maxScroll;
    private double openedAt = -10;

    @Override
    public void opened() {
        scroll = scrollTarget = 0;
        openedAt = Anim.now();
        NewsClient.markSeen();
    }

    static Component t(String key, Object... args) {
        return Component.translatable("news.beyondsolo.page." + key, args);
    }

    /** The headlines, newest first. */
    static List<Headline> newestFirst() {
        List<Headline> out = new ArrayList<>(NewsClient.headlines());
        java.util.Collections.reverse(out);
        return out;
    }

    @Override
    public void render(GuiGraphics g, float x, float y, float w, float h, int mouseX, int mouseY) {
        NewsClient.markSeen();
        List<Headline> list = newestFirst();
        side(g, list, x, y, SIDE_W, h);
        list(g, list, x + SIDE_W + 6, y, w - SIDE_W - 6, h, mouseX, mouseY);
    }

    /** The masthead: the paper's name, the day, and the last breaking story. */
    private void side(GuiGraphics g, List<Headline> list, float x, float y, float w, float h) {
        Ui.card(g, x, y, w, h, 7, NewsTicker.RED, 0.06f);
        float cx = x + w / 2f;
        SysFont.spaced(g, t("masthead").getString(), cx, y + 7, 7f, SystemTheme.TEXT, true, 1.4f, 0.5f);
        Shapes.box(x + 8, y + 17, w - 16, 0.8f).fill(Gfx.alpha(NewsTicker.RED, 0.8f)).draw(g);
        Shapes.box(x + 8, y + 19, w - 16, 0.4f).fill(Gfx.alpha(NewsTicker.RED, 0.5f)).draw(g);
        Minecraft mc = Minecraft.getInstance();
        long day = mc.level == null ? 0 : mc.level.getDayTime() / 24000L;
        SysFont.draw(g, t("day", day + 1).getString(), cx, y + 23, 4.8f, SystemTheme.TEXT_SOFT, false, 0.5f);
        SysFont.draw(g, t("count", list.size()).getString(), cx, y + 31, 4.6f, SystemTheme.TEXT_DIM, false, 0.5f);
        // the last breaking story
        Headline breaking = null;
        for (Headline hl : list) {
            if (hl.breaking()) {
                breaking = hl;
                break;
            }
        }
        float by = y + 44;
        if (breaking != null) {
            float tw = Ui.chip(g, x + 8, by, 8, t("breaking").getString(), 4.4f, NewsTicker.RED, true);
            SysFont.draw(g, ago(breaking), x + 8 + tw + 4, by + 1.6f, 4.2f, SystemTheme.TEXT_DIM, false, 0f);
            float ty = by + 12;
            int lines = 0;
            for (SysFont.Line l : SysFont.wrap(NewsClient.text(breaking), 5f, true, w - 16)) {
                if (ty + 7 > y + h - 6 || lines++ >= 7) break;
                SysFont.draw(g, l, x + 8, ty, SystemTheme.TEXT, 0f);
                ty += 5f * SysFont.LINE;
            }
        } else {
            float ty = by;
            for (SysFont.Line l : SysFont.wrap(t("quiet"), 4.6f, false, w - 16)) {
                SysFont.draw(g, l, cx, ty, SystemTheme.TEXT_DIM, 0.5f);
                ty += 4.6f * SysFont.LINE;
            }
        }
    }

    /** "Day 12" or "5 min ago". */
    static String ago(Headline hl) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level == null ? hl.time() : mc.level.getGameTime();
        long minutes = Math.max(0, (now - hl.time()) / 1200);
        if (minutes < 1) return t("just_now").getString();
        if (minutes < 60) return t("minutes_ago", minutes).getString();
        return t("day", hl.day() + 1).getString();
    }

    private void list(GuiGraphics g, List<Headline> list, float x, float y, float w, float h, int mouseX, int mouseY) {
        Ui.card(g, x, y, w, h, 8);
        listX = x + 3;
        listY = y + 3;
        listW = w - 6;
        listH = h - 6;
        if (list.isEmpty()) {
            SysFont.draw(g, t("empty").getString(), x + w / 2f, y + h / 2f - 3, 5.2f, SystemTheme.TEXT_DIM, false, 0.5f);
            return;
        }
        maxScroll = Math.max(0, list.size() * ROW_H - listH + 2);
        scrollTarget = Mth.clamp(scrollTarget, 0, maxScroll);
        scroll = Anim.approach(scroll, scrollTarget, 18f);
        Shapes.flush(g);
        g.enableScissor((int) listX, (int) listY, (int) Math.ceil(listX + listW), (int) Math.ceil(listY + listH));
        float rw = listW - (maxScroll > 0 ? 6 : 2);
        float since = Anim.since(openedAt);
        for (int i = 0; i < list.size(); i++) {
            float ry = listY + 1 + i * ROW_H - scroll;
            if (ry + ROW_H < listY || ry > listY + listH) continue;
            float k = Anim.outCubic(Anim.clamp01((since - i * 0.04f) / 0.3f));
            float old = Gfx.fade(k);
            row(g, list.get(i), listX + 1 + (1 - k) * 8, ry, rw, i);
            Gfx.setAlpha(old);
        }
        Shapes.flush(g);
        g.disableScissor();
        ChronicleUi.scrollbar(g, listX + listW - 3, listY + 1, listH - 2, scroll, maxScroll, SystemTheme.ACCENT);
    }

    private static void row(GuiGraphics g, Headline hl, float x, float y, float w, int i) {
        float h = ROW_H - 2;
        int c = hl.accent() | 0xFF000000;
        if (hl.breaking()) {
            Shapes.box(x, y, w, h).radius(5).fillH(Gfx.alpha(NewsTicker.RED, 0.16f), 0x06FFFFFF).border(0.6f, Gfx.alpha(NewsTicker.RED, 0.55f)).draw(g);
        } else if (i % 2 == 0) {
            Shapes.box(x, y, w, h).radius(5).fill(0x08FFFFFF).draw(g);
        }
        Shapes.box(x + 1.2f, y + 3, 1.4f, h - 6).radius(0.7f).fill(hl.breaking() ? NewsTicker.RED : c).draw(g);
        Shapes.circle(x + 12, y + h / 2f, 6.5f).fillV(Gfx.alpha(c, 0.3f), Gfx.alpha(c, 0.06f)).border(0.6f, Gfx.alpha(c, 0.85f)).draw(g);
        Shapes.icon(NewsTicker.icon(hl.kind()), x + 12, y + h / 2f, 7.5f).fill(Gfx.mix(c, 0xFFFFFFFF, 0.55f)).draw(g);
        float tx = x + 23;
        String when = ago(hl);
        float ww = SysFont.width(when, 4.2f, false);
        SysFont.draw(g, when, x + w - 5, y + 3, 4.2f, SystemTheme.TEXT_DIM, false, 1f);
        float room = w - (tx - x) - ww - 10;
        float ty = y + 3.6f;
        float lx = tx;
        if (hl.breaking()) lx += Ui.chip(g, tx, y + 2.2f, 7, t("breaking").getString(), 4f, NewsTicker.RED, true) + 3;
        // two lines at most: the second one cut short with "..." if it runs on
        String full = NewsClient.text(hl).getString();
        List<SysFont.Line> lines = SysFont.wrap(Component.literal(full), 5f, false, room - (lx - tx));
        if (lines.isEmpty()) return;
        SysFont.draw(g, lines.get(0), lx, ty, SystemTheme.TEXT, 0f);
        if (lines.size() > 1) {
            String rest = full.substring(Math.min(full.length(), SysFont.length(lines.get(0)))).trim();
            SysFont.draw(g, SysFont.fit(rest, 5f, false, room), tx, ty + 5f * SysFont.LINE, 5f, SystemTheme.TEXT, false, 0f);
        }
    }

    @Override
    public boolean click(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    public boolean scroll(double mouseX, double mouseY, double amount) {
        if (!Ui.inside(mouseX, mouseY, listX, listY, listW, listH)) return false;
        scrollTarget = Mth.clamp(scrollTarget - (float) amount * ROW_H * 1.5f, 0, maxScroll);
        return true;
    }
}
