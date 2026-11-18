package com.beyondsolo.client.chronicle;

import com.beyondsolo.chronicle.ChronicleData;
import com.beyondsolo.chronicle.title.PerkLine;
import com.beyondsolo.chronicle.title.TitleCategory;
import com.beyondsolo.chronicle.title.TitleDef;
import com.beyondsolo.chronicle.title.TitleTier;
import com.beyondsolo.chronicle.title.Titles;
import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.screen.SystemScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * TITLES: every title on the left (lit when earned, with how far along the rest are; a secret one shows only as
 * "???"), the one picked on the right (what earns it, its perks while worn, and WEAR / TAKE OFF).
 */
final class TitlesPage implements ChronicleTab.Page {
    private static final float LIST_W = 194;
    private static final float ROW_H = 18;

    private static @Nullable TitleCategory filter;
    private static @Nullable String selected;
    private static float scroll, scrollTarget;
    private static boolean reveal = true;

    private final List<ChronicleUi.Hit> hits = new ArrayList<>();
    private float listX, listY, listW, listH, maxScroll;

    @Override
    public void opened() {
        if (lookup(selected) == null) {
            String worn = ClientSystemData.get().title();
            selected = lookup(worn) != null ? worn : null;
        }
        reveal = true;
    }

    private static List<TitleDef> shown() {
        List<TitleDef> out = new ArrayList<>();
        for (TitleDef d : Titles.all()) if (filter == null || d.category() == filter) out.add(d);
        // titles another feature gave without registering them: kept and wearable, listed last
        for (String key : ChronicleClient.data().titles().keySet()) {
            if (Titles.byKey(key) != null) continue;
            TitleDef d = lookup(key);
            if (d != null && (filter == null || d.category() == filter)) out.add(d);
        }
        return out;
    }

    /** A registered title, or an earned one that has no entry (a System title with no perks). */
    private static @Nullable TitleDef lookup(@Nullable String key) {
        if (key == null || key.isEmpty()) return null;
        TitleDef d = Titles.byKey(key);
        if (d != null || !key.startsWith(TitleDef.PREFIX) || !ChronicleClient.data().hasTitle(key)) return d;
        return new TitleDef(key.substring(TitleDef.PREFIX.length()), TitleCategory.SYSTEM, TitleTier.RARE, null, 0, List.of(), false);
    }

    /** Driver: {@code chronicle titles <id|category>}. */
    static void show(String what) {
        for (TitleCategory c : TitleCategory.values()) {
            if (c.id().equals(what)) {
                filter = c;
                scroll = scrollTarget = 0;
                return;
            }
        }
        if (what.equals("all")) {
            filter = null;
            return;
        }
        TitleDef d = Titles.byId(what);
        if (d != null) {
            selected = d.key();
            if (filter != null && d.category() != filter) filter = null;
            reveal = true;
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, float x, float y, float w, float h, int mouseX, int mouseY) {
        hits.clear();
        List<TitleDef> list = shown();
        if (lookup(selected) == null || !contains(list, selected)) selected = list.isEmpty() ? null : list.get(0).key();
        TitleDef pick = lookup(selected);
        if (pick != null && ChronicleClient.data().hasTitle(pick.key())) ChronicleClient.read(pick.key());

        Ui.card(g, x, y, LIST_W, h, 8);
        filters(g, x + 6, y + 5, LIST_W - 12, mouseX, mouseY);
        listX = x + 4;
        listY = y + 21;
        listW = LIST_W - 8;
        listH = y + h - 4 - listY;
        rows(g, list, mouseX, mouseY);
        detail(g, pick, x + LIST_W + 6, y, w - LIST_W - 6, h, mouseX, mouseY);
    }

    private static boolean contains(List<TitleDef> list, String key) {
        for (TitleDef d : list) if (d.key().equals(key)) return true;
        return false;
    }

    /** ALL and one pill a category (icons); the name of the one under the mouse (or picked) beside them. */
    private void filters(GuiGraphics g, float x, float y, float w, int mouseX, int mouseY) {
        float fh = 11;
        String all = Component.translatable("chronicle.beyondsolo.filter.all").getString();
        float aw = ChronicleUi.pillWidth(fh, all, false, 5.2f);
        boolean hoverAll = filter != null && Ui.inside(mouseX, mouseY, x, y, aw, fh);
        ChronicleUi.pill(g, x, y, fh, all, null, SystemTheme.ACCENT, filter == null, hoverAll, 5.2f);
        hits.add(new ChronicleUi.Hit(x, y, aw, fh, () -> setFilter(null)));
        float px = x + aw + 3;
        Component named = filter == null ? Component.translatable("chronicle.beyondsolo.filter.all_titles") : filter.displayName();
        for (TitleCategory c : TitleCategory.values()) {
            float cw = ChronicleUi.pillWidth(fh, "", true, 5.2f);
            boolean sel = c == filter;
            boolean hover = Ui.inside(mouseX, mouseY, px, y, cw, fh);
            if (hover) named = c.displayName();
            ChronicleUi.pill(g, px, y, fh, "", ChronicleIcons.of(c), SystemTheme.ACCENT, sel, hover && !sel, 5.2f);
            hits.add(new ChronicleUi.Hit(px, y, cw, fh, () -> setFilter(c)));
            px += cw + 3;
        }
        String label = named.getString();
        float room = x + w - px - 2;
        if (room > 16) SysFont.draw(g, SysFont.fit(label, 5.2f, true, room), x + w, SysFont.centerY(y, fh, 5.2f), 5.2f, SystemTheme.TEXT_SOFT, true, 1f);
    }

    private static void setFilter(@Nullable TitleCategory c) {
        if (filter == c) return;
        filter = c;
        scroll = scrollTarget = 0;
        reveal = true;
        SystemScreen.playClick();
    }

    private void rows(GuiGraphics g, List<TitleDef> list, int mouseX, int mouseY) {
        ChronicleData data = ChronicleClient.data();
        String worn = ClientSystemData.get().title();
        maxScroll = Math.max(0, list.size() * ROW_H - listH + 2);
        if (reveal) {
            reveal = false;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).key().equals(selected)) {
                    scrollTarget = Mth.clamp(i * ROW_H - listH / 2f + ROW_H / 2f, 0, maxScroll);
                    scroll = scrollTarget;
                }
            }
        }
        scrollTarget = Mth.clamp(scrollTarget, 0, maxScroll);
        scroll = Anim.approach(scroll, scrollTarget, 18f);
        boolean inList = Ui.inside(mouseX, mouseY, listX, listY, listW, listH);
        g.enableScissor((int) listX, (int) listY, (int) Math.ceil(listX + listW), (int) Math.ceil(listY + listH));
        float rw = listW - (maxScroll > 0 ? 6 : 2);
        for (int i = 0; i < list.size(); i++) {
            float ry = listY + 1 + i * ROW_H - scroll;
            if (ry + ROW_H < listY || ry > listY + listH) continue;
            TitleDef d = list.get(i);
            row(g, d, data, worn, listX + 1, ry, rw, inList && Ui.inside(mouseX, mouseY, listX + 1, ry, rw, ROW_H - 2));
            if (ry + ROW_H - 2 > listY && ry < listY + listH) {
                float top = Math.max(ry, listY), bottom = Math.min(ry + ROW_H - 2, listY + listH);
                hits.add(new ChronicleUi.Hit(listX + 1, top, rw, bottom - top, () -> pick(d)));
            }
        }
        g.disableScissor();
        ChronicleUi.scrollbar(g, listX + listW - 3, listY + 1, listH - 2, scroll, maxScroll, SystemTheme.ACCENT);
    }

    private static void pick(TitleDef d) {
        if (d.key().equals(selected)) return;
        selected = d.key();
        SystemScreen.playTick(1.7f);
    }

    private static void row(GuiGraphics g, TitleDef d, ChronicleData data, String worn, float x, float y, float w, boolean hover) {
        boolean earned = data.hasTitle(d.key());
        boolean wearing = earned && d.key().equals(worn);
        boolean secret = d.secret() && !earned;
        boolean sel = d.key().equals(selected);
        int tc = d.tier().color();
        float h = ROW_H - 2;
        if (sel) {
            Shapes.box(x, y, w, h).radius(5).fillH(Gfx.alpha(tc, 0.24f), Gfx.alpha(tc, 0.04f)).border(0.6f, Gfx.alpha(tc, 0.8f), Gfx.alpha(tc, 0.25f)).draw(g);
        } else if (hover) {
            Shapes.box(x, y, w, h).radius(5).fill(0x12FFFFFF).border(0.5f, 0x22FFFFFF).draw(g);
        }
        float cx = x + 9, cy = y + h / 2f;
        if (earned) {
            Shapes.circle(cx, cy, 6).fillV(Gfx.alpha(tc, 0.42f), Gfx.alpha(Gfx.shade(tc, -0.55f), 0.55f)).border(0.6f, tc)
                    .glow(wearing ? 3 + Anim.pulse(2.4f) : 0, Gfx.alpha(tc, 0.5f)).draw(g);
        } else {
            Shapes.circle(cx, cy, 6).fill(0x50060E1E).border(0.5f, 0x30FFFFFF).draw(g);
        }
        Shapes.icon(secret ? Icons.QUESTION : ChronicleIcons.of(d.category()), cx, cy, 7)
                .fill(earned ? Gfx.mix(tc, 0xFFFFFFFF, 0.55f) : SystemTheme.TEXT_DIM).draw(g);

        // the right side: WORN, NEW, or how far along
        float right = x + w - 3;
        if (wearing) {
            String s = Component.translatable("chronicle.beyondsolo.worn").getString();
            float cw = SysFont.width(s, 4.4f, true) + 6;
            Shapes.pill(right - cw, cy - 3.5f, cw, 7).fill(Gfx.alpha(SystemTheme.ACCENT, 0.25f)).border(0.5f, SystemTheme.ACCENT).draw(g);
            SysFont.draw(g, s, right - cw / 2f, SysFont.centerY(cy - 3.5f, 7, 4.4f), 4.4f, SystemTheme.TEXT, true, 0.5f);
            right -= cw + 3;
        } else if (earned && ChronicleClient.isFresh(d.key())) {
            String s = Component.translatable("chronicle.beyondsolo.new").getString();
            float cw = SysFont.width(s, 4.4f, true) + 5;
            ChronicleUi.fresh(g, right - cw, cy - 3.5f);
            right -= cw + 3;
        } else if (!earned && !secret && d.feat() != null && d.goal() > 0) {
            float f = ChronicleClient.progress(d) / (float) d.goal();
            String s = Math.min(99, (int) Math.floor(f * 100)) + "%";
            SysFont.draw(g, s, right, SysFont.centerY(y, h, 5.4f), 5.4f, f > 0 ? SystemTheme.TEXT_SOFT : SystemTheme.TEXT_DIM, true, 1f);
            right -= SysFont.width("100%", 5.4f, true) + 3;
        }

        float nx = x + 19;
        float room = right - nx;
        String name = secret ? Component.translatable("chronicle.beyondsolo.secret").getString() : d.displayName().getString();
        SysFont.draw(g, SysFont.fit(name, 6.2f, true, room), nx, y + 2.3f, 6.2f, earned ? Gfx.mix(tc, 0xFFFFFFFF, 0.4f) : SystemTheme.TEXT_SOFT, true, 0f);
        float sy = y + h - 6.6f;
        if (earned) {
            String tier = d.tier().displayName().getString();
            SysFont.draw(g, SysFont.fit(tier, 4.6f, true, room), nx, sy, 4.6f, Gfx.alpha(tc, 0.85f), true, 0f);
        } else if (secret) {
            SysFont.draw(g, SysFont.fit(Component.translatable("chronicle.beyondsolo.secret.row").getString(), 4.6f, false, room), nx, sy, 4.6f, SystemTheme.TEXT_DIM, false, 0f);
        } else if (d.feat() != null && d.goal() > 0) {
            long have = ChronicleClient.progress(d);
            float bw = Math.min(46, room * 0.45f);
            Ui.bar(g, nx, sy + 0.6f, bw, 2.4f, have / (float) d.goal(), Gfx.alpha(tc, 0.6f), tc);
            String s = num(have) + " / " + num(d.goal());
            SysFont.draw(g, SysFont.fit(s, 4.6f, false, room - bw - 4), nx + bw + 4, sy, 4.6f, SystemTheme.TEXT_DIM, false, 0f);
        } else {
            SysFont.draw(g, SysFont.fit(Component.translatable("chronicle.beyondsolo.deed").getString(), 4.6f, false, room), nx, sy, 4.6f, SystemTheme.TEXT_DIM, false, 0f);
        }
    }

    /** The title picked: its crest, what earns it, its perks, and WEAR / TAKE OFF. */
    private void detail(GuiGraphics g, @Nullable TitleDef d, float x, float y, float w, float h, int mouseX, int mouseY) {
        if (d == null) {
            Ui.card(g, x, y, w, h, 8);
            return;
        }
        ChronicleData data = ChronicleClient.data();
        boolean earned = data.hasTitle(d.key());
        boolean secret = d.secret() && !earned;
        boolean wearing = earned && d.key().equals(ClientSystemData.get().title());
        int tc = d.tier().color();
        Ui.card(g, x, y, w, h, 8, tc, earned ? 0.18f : 0.05f);
        float cx = x + w / 2f;

        // the crest
        float cy = y + 21;
        if (earned) {
            Shapes.rays(cx, cy, 30, 12).fillV(Gfx.alpha(Gfx.mix(tc, 0xFFFFFFFF, 0.4f), 0.16f), Gfx.alpha(tc, 0.0f)).rotation(Anim.loop(60f)).additive().draw(g);
            Shapes.ring(cx, cy, 16.5f, 0.6f).fill(Gfx.alpha(tc, 0.65f)).dashes(22, 0.45f).rotation(Anim.loop(18f)).draw(g);
        } else {
            Shapes.ring(cx, cy, 16.5f, 0.5f).fill(0x26FFFFFF).dashes(22, 0.45f).draw(g);
        }
        Shapes.circle(cx, cy, 13).fillV(earned ? Gfx.alpha(tc, 0.5f) : 0x70142236, earned ? Gfx.alpha(Gfx.shade(tc, -0.55f), 0.7f) : 0x70060B16)
                .border(0.8f, earned ? tc : 0x40FFFFFF).glow(earned ? 6 + 2 * Anim.pulse(3f) : 0, Gfx.alpha(tc, 0.35f)).draw(g);
        Shapes.icon(secret ? Icons.QUESTION : ChronicleIcons.of(d.category()), cx, cy, 14)
                .fill(earned ? Gfx.mix(tc, 0xFFFFFFFF, 0.65f) : SystemTheme.TEXT_DIM).glow(earned ? 2 : 0, Gfx.alpha(tc, 0.6f)).draw(g);
        if (!earned) {
            Shapes.circle(cx + 10, cy + 9, 4.6f).fill(0xF0081120).border(0.5f, 0x50FFFFFF).draw(g);
            Shapes.icon(Icons.LOCK, cx + 10, cy + 9, 5).fill(SystemTheme.TEXT_SOFT).draw(g);
        }

        // the name, the tier and where it comes from
        float ty = cy + 18;
        Component name = secret ? Component.translatable("chronicle.beyondsolo.secret") : d.displayName();
        List<SysFont.Line> nameLines = SysFont.wrap(name, 8f, true, w - 14);
        for (int i = 0; i < Math.min(2, nameLines.size()); i++) {
            SysFont.draw(g, nameLines.get(i), cx, ty, earned ? Gfx.mix(tc, 0xFFFFFFFF, 0.45f) : SystemTheme.TEXT, 0.5f);
            ty += 8f * SysFont.LINE;
        }
        ty += 1;
        String tier = d.tier().displayName().getString();
        String cat = d.category().displayName().getString();
        float chipH = 8, chipSize = 4.6f;
        float tw = SysFont.width(tier, chipSize, true) + chipH * 0.9f;
        float cw = SysFont.width(cat, chipSize, true) + chipH * 0.9f;
        float chipX = cx - (tw + cw + 3) / 2f;
        Ui.chip(g, chipX, ty, chipH, tier, chipSize, tc, earned);
        Ui.chip(g, chipX + tw + 3, ty, chipH, cat, chipSize, SystemTheme.ACCENT, false);
        ty += chipH + 5;

        // under it, top down: what earns it, how far along (or when it was earned), its perks; the button at the bottom
        float by = y + h - 19;
        List<PerkLine> perks = secret ? List.of() : d.perks();
        float perkH = perks.isEmpty() ? 0 : 8 + perks.size() * 8.2f;
        float progH = earned || secret ? 9 : d.feat() != null && d.goal() > 0 ? 13 : 0;
        float bottomTop = by - 4 - perkH - progH;

        // what earns it
        Component desc = secret ? Component.translatable("chronicle.beyondsolo.secret.hint")
                : I18n.exists(d.key() + ".desc") ? d.description() : Component.translatable("chronicle.beyondsolo.given");
        float ls = 5.6f;
        int fits = Math.max(1, (int) ((bottomTop - ty - 2) / (ls * SysFont.LINE)));
        List<SysFont.Line> lines = SysFont.wrap(desc, ls, false, w - 16);
        for (int i = 0; i < Math.min(fits, lines.size()); i++) {
            SysFont.draw(g, lines.get(i), cx, ty, SystemTheme.TEXT_SOFT, 0.5f);
            ty += ls * SysFont.LINE;
        }

        float py = Math.min(ty + 6, bottomTop);
        Ui.divider(g, cx - 40, py - 3, 80, Gfx.alpha(tc, earned ? 0.45f : 0.2f));
        if (earned) {
            Long at = data.titles().get(d.key());
            String s = Component.translatable("chronicle.beyondsolo.earned_on", ChronicleUi.day(at == null ? 0 : at)).getString();
            SysFont.draw(g, SysFont.fit(s, 5f, false, w - 14), cx, py + 1, 5f, Gfx.mix(tc, 0xFFFFFFFF, 0.3f), false, 0.5f);
        } else if (secret) {
            SysFont.draw(g, SysFont.fit(Component.translatable("chronicle.beyondsolo.secret.row").getString(), 5f, false, w - 14), cx, py + 1, 5f,
                    SystemTheme.TEXT_DIM, false, 0.5f);
        } else if (d.feat() != null && d.goal() > 0) {
            long have = ChronicleClient.progress(d);
            Ui.bar(g, x + 10, py + 1, w - 20, 3.2f, have / (float) d.goal(), Gfx.alpha(tc, 0.55f), tc);
            String s = num(have) + " / " + num(d.goal());
            SysFont.draw(g, s, cx, py + 6, 4.8f, SystemTheme.TEXT_SOFT, true, 0.5f);
        }

        if (!perks.isEmpty()) {
            float ky = py + progH + 4;
            SysFont.spaced(g, Component.translatable("chronicle.beyondsolo.perks").getString(), x + 9, ky, 4.6f, SystemTheme.TEXT_DIM, true, 1.1f, 0f);
            ky += 8;
            for (PerkLine p : perks) {
                int pc = earned ? SystemTheme.GOOD : SystemTheme.TEXT_SOFT;
                Shapes.icon(ChronicleIcons.of(p.perk()), x + 13, ky + 2.6f, 6.4f).fill(earned ? Gfx.mix(pc, 0xFFFFFFFF, 0.2f) : SystemTheme.TEXT_DIM).draw(g);
                SysFont.draw(g, SysFont.fit(p.describe().getString(), 5.2f, false, w - 30), x + 20, ky, 5.2f, pc, false, 0f);
                ky += 8.2f;
            }
        }

        // WEAR / TAKE OFF
        float bx = x + 9, bw = w - 18, bh = 14;
        if (earned) {
            boolean hover = Ui.inside(mouseX, mouseY, bx, by, bw, bh);
            String label = Component.translatable(wearing ? "chronicle.beyondsolo.unequip" : "chronicle.beyondsolo.equip").getString();
            ChronicleUi.button(g, bx, by, bw, bh, label, wearing ? Icons.EYE_OFF : Icons.CROWN, wearing ? SystemTheme.TEXT_SOFT : tc, true, hover);
            hits.add(new ChronicleUi.Hit(bx, by, bw, bh, () -> {
                ChronicleClient.equip(wearing ? "" : d.key());
                SystemScreen.playClick();
            }));
        } else {
            ChronicleUi.button(g, bx, by, bw, bh, Component.translatable("chronicle.beyondsolo.locked").getString(), Icons.LOCK, SystemTheme.TEXT_DIM, false, false);
        }
    }

    static String num(long n) {
        return n >= 10000 ? String.format(java.util.Locale.ROOT, "%,d", n) : String.valueOf(n);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean click(double mouseX, double mouseY, int button) {
        return button == 0 && ChronicleUi.click(hits, mouseX, mouseY);
    }

    @Override
    public boolean scroll(double mouseX, double mouseY, double amount) {
        if (!Ui.inside(mouseX, mouseY, listX, listY, listW, listH) || maxScroll <= 0) return false;
        scrollTarget = Mth.clamp(scrollTarget - (float) amount * ROW_H * 1.5f, 0, maxScroll);
        return true;
    }
}
