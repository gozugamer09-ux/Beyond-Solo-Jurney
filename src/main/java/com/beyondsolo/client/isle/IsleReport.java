package com.beyondsolo.client.isle;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.Locale;

/**
 * The raid's report, after the island is retaken or lost: a System window with the time, this hunter's kills, the swarm
 * slain, the nests burned and the allies who fell, and what it paid (XP, gold, merit), each line counting up in turn.
 */
final class IsleReport {
    private static final int WHITE = 0xFFFFFFFF;

    static void render(GuiGraphics g, int w, int h, IsleClient.Banner b, float t, float live) {
        IsleClient.Result r = b.result;
        if (r == null) return;
        int c = r.won() ? SystemTheme.GOLD : IsleHud.ALARM;
        float old = Gfx.fade(live);
        float cx = w / 2f, y = h * 0.2f;
        float pw = 236, ph = r.won() ? 122 : 96;
        // the room it holds: what else sits there (a fallen creature's soul to extract, another mod's tooltip) moves under it
        if (live > 0.05f) com.beyondsolo.client.hud.HudSpace.claimTop(cx - pw / 2f, y - 6, pw, ph + 6);
        float open = Anim.outBack(Anim.window(t, 0f, 0.45f));
        g.pose().pushPose();
        g.pose().translate(cx, y + ph / 2f, 0);
        g.pose().scale(1, Math.max(0.02f, open), 1);
        Ui.panel(g, -pw / 2f, -ph / 2f, pw, ph, 10, c, 1f);
        g.pose().popPose();
        if (open < 0.9f) {
            Gfx.setAlpha(old);
            return;
        }
        // the heading
        String head = IsleHud.upper(IsleHud.t(r.won() ? "report.won" : "report.lost"));
        float hk = Anim.outCubic(Anim.window(t, 0.3f, 0.7f));
        float o1 = Gfx.fade(hk);
        float tagW = SysFont.spacedWidth(head, 6f, true, 1.8f) + 18;
        Shapes.pill(cx - tagW / 2f, y - 6, tagW, 12).fill(Gfx.alpha(0xFF060A14, 0.92f)).border(0.8f, Gfx.alpha(c, 0.9f)).glow(5, Gfx.alpha(c, 0.4f)).draw(g);
        SysFont.spaced(g, head, cx, y - 2.6f, 6f, Gfx.mix(c, WHITE, 0.55f), true, 1.8f, 0.5f);
        Gfx.setAlpha(o1);
        Component name = IsleHud.t("report.title");
        SysFont.draw(g, name, cx, y + 11, 8.5f, WHITE, true, 0.5f);
        if (r.fallen()) SysFont.draw(g, IsleHud.t("report.fallen"), cx, y + 22, 5.6f, SystemTheme.TEXT_DIM, false, 0.5f);

        // the tally: two columns of four lines, each counting up in turn
        float left = cx - pw / 2f + 18, right = cx + 8;
        float ly = y + 32;
        line(g, t, 0, left, ly, Icons.HOURGLASS, IsleHud.t("report.time"), IsleHud.clock((int) Math.min(Integer.MAX_VALUE, r.time())), r.best() ? IsleHud.t("report.best") : null);
        line(g, t, 1, right, ly, Icons.SKULL, IsleHud.t("report.kills"), count(r.kills(), t, 1), null);
        line(g, t, 2, left, ly + 12, Icons.FLAME, IsleHud.t("report.nests"), String.valueOf(r.nests()), null);
        line(g, t, 3, right, ly + 12, Icons.HEXAGON, IsleHud.t("report.slain"), count(r.slain(), t, 3), null);
        line(g, t, 4, left, ly + 24, Icons.HANDSHAKE, IsleHud.t("report.allies"), (r.allies() - r.alliesFell()) + "/" + r.allies(), null);
        if (!r.won()) {
            line(g, t, 5, right, ly + 24, Icons.HEART_BREAK, IsleHud.t("report.why"), IsleHud.t("report.lost." + r.lost()).getString(), null);
        }
        // what it paid
        if (r.won()) {
            float pk = Anim.outBack(Anim.window(t, 1.9f, 2.3f));
            if (pk > 0.01f) {
                float run = Anim.outCubic(Anim.window(t, 2.0f, 3.4f));
                String xp = IsleHud.t("report.xp", String.format(Locale.ROOT, "%,d", Math.round(r.xp() * run))).getString();
                String gold = IsleHud.t("report.gold", String.format(Locale.ROOT, "%,d", Math.round(r.gold() * run))).getString();
                String merit = IsleHud.t("report.merit", String.format(Locale.ROOT, "%,d", Math.round(r.merit() * run))).getString();
                float chipH = 12, size = 6.6f;
                float total = SysFont.width(xp, size, true) + SysFont.width(gold, size, true) + SysFont.width(merit, size, true) + chipH * 2.7f + 8;
                g.pose().pushPose();
                g.pose().translate(cx, y + 74, 0);
                g.pose().scale(pk, pk, 1);
                float x = -total / 2f;
                x += Ui.chip(g, x, 0, chipH, xp, size, SystemTheme.GOOD, true) + 4;
                x += Ui.chip(g, x, 0, chipH, gold, size, SystemTheme.GOLD, true) + 4;
                Ui.chip(g, x, 0, chipH, merit, size, SystemTheme.ACCENT, true);
                g.pose().popPose();
                float bk = Anim.outCubic(Anim.window(t, 2.6f, 3.0f));
                if (bk > 0.01f) {
                    float o2 = Gfx.fade(bk);
                    SysFont.draw(g, IsleHud.t("report.box"), cx, y + 92, 5.6f, SystemTheme.TEXT_SOFT, false, 0.5f);
                    Gfx.setAlpha(o2);
                }
            }
        }
        Gfx.setAlpha(old);
    }

    /** One line of the tally: an icon, what it is, and its number (with a mark after, such as a new best). */
    private static void line(GuiGraphics g, float t, int i, float x, float y, ResourceLocation icon, Component what, String value,
                             Component mark) {
        float k = Anim.outCubic(Anim.window(t, 0.6f + i * 0.18f, 0.9f + i * 0.18f));
        if (k <= 0.01f) return;
        float o = Gfx.fade(k);
        x -= 6 * (1 - k);
        Shapes.icon(icon, x + 3, y + 2.6f, 6.5f).fill(Gfx.alpha(SystemTheme.ACCENT_BRIGHT, 0.9f)).draw(g);
        SysFont.draw(g, what, x + 10, y, 5.6f, SystemTheme.TEXT_SOFT, false, 0f);
        float vx = x + 10 + SysFont.width(what, 5.6f, false) + 5;
        SysFont.draw(g, value, vx, y, 5.6f, WHITE, true, 0f);
        if (mark != null) {
            float mx = vx + SysFont.width(value, 5.6f, true) + 4;
            float mw = SysFont.width(mark, 4.6f, true) + 8;
            Shapes.pill(mx, y - 1, mw, 8).fill(Gfx.alpha(SystemTheme.GOLD, 0.2f)).border(0.6f, Gfx.alpha(SystemTheme.GOLD, 0.8f)).draw(g);
            SysFont.draw(g, mark, mx + 4, y + 0.6f, 4.6f, SystemTheme.GOLD, true, 0f);
        }
        Gfx.setAlpha(o);
    }

    /** A number counting up over its line's moment. */
    private static String count(int n, float t, int i) {
        float k = Anim.outCubic(Anim.window(t, 0.7f + i * 0.18f, 1.5f + i * 0.18f));
        return String.valueOf(Math.round(n * Mth.clamp(k, 0, 1)));
    }

    private IsleReport() {}
}
