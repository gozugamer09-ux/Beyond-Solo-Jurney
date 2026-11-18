package com.beyondsolo.client.chronicle;

import com.beyondsolo.chronicle.ChronicleData;
import com.beyondsolo.chronicle.journal.CreatureFamily;
import com.beyondsolo.chronicle.journal.Journal;
import com.beyondsolo.chronicle.journal.JournalCategory;
import com.beyondsolo.chronicle.journal.JournalEntry;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.screen.SystemScreen;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.GateRealm;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * JOURNAL: the pages of a section as a grid on the left (a creature as itself, a weapon as its icon, a realm as a
 * picture; a dark shape while unknown), the page picked on the right: the creature turning slowly, what is known of it
 * (its lore once one has been slain), how many fell, and for a boss the forms it has been met in.
 */
final class JournalPage implements ChronicleTab.Page {
    private static final float GRID_W = 196;
    private static final float TILE = 28;
    private static final float GAP = 3;
    private static final float REALM_W = 58;
    private static final float REALM_H = 33;
    private static final int BOSS = 0xFFFF6A5C;

    private static JournalCategory category = JournalCategory.CREATURES;
    private static final Map<JournalCategory, String> SELECTED = new EnumMap<>(JournalCategory.class);
    private static float scroll, scrollTarget;
    private static float loreScroll, loreTarget;
    private static boolean reveal = true;
    private static @Nullable String loreFor;

    private final List<ChronicleUi.Hit> hits = new ArrayList<>();
    private float gridX, gridY, gridW, gridH, maxScroll;
    private float detailX, detailY, detailW, detailH, loreMax;
    private @Nullable JournalEntry hovered;

    @Override
    public void opened() {
        reveal = true;
    }

    /** Driver: {@code chronicle journal <category> [page]}. */
    static void show(String cat, String page) {
        for (JournalCategory c : JournalCategory.values()) if (c.id().equals(cat)) category = c;
        if (!page.isEmpty()) {
            JournalEntry e = Journal.get(page);
            if (e == null) e = Journal.get(prefix(category) + page);
            if (e != null) {
                category = e.category();
                SELECTED.put(category, e.id());
            }
        }
        reveal = true;
    }

    private static String prefix(JournalCategory c) {
        return switch (c) {
            case CREATURES -> "creature:";
            case BOSSES -> "boss:";
            case REALMS -> "realm:";
            case ARSENAL -> "arsenal:";
        };
    }

    private static int known(JournalCategory c) {
        int n = 0;
        ChronicleData data = ChronicleClient.data();
        for (JournalEntry e : Journal.of(c)) if (data.knows(e.id())) n++;
        return n;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, float x, float y, float w, float h, int mouseX, int mouseY) {
        hits.clear();
        hovered = null;
        List<JournalEntry> pages = Journal.of(category);
        String sel = SELECTED.get(category);
        JournalEntry pick = sel == null ? null : Journal.get(sel);
        if (pick == null || pick.category() != category) {
            pick = firstKnown(pages);
            if (pick != null) SELECTED.put(category, pick.id());
        }
        if (pick != null && ChronicleClient.data().knows(pick.id())) ChronicleClient.read(pick.id());

        Ui.card(g, x, y, GRID_W, h, 8);
        tabs(g, x + 6, y + 5, GRID_W - 12, mouseX, mouseY);
        gridX = x + 4;
        gridY = y + 21;
        gridW = GRID_W - 8;
        gridH = y + h - 13 - gridY;
        grid(g, pages, pick, mouseX, mouseY);
        // the name of the page under the mouse, under the grid
        JournalEntry named = hovered != null ? hovered : pick;
        if (named != null) {
            boolean knows = ChronicleClient.data().knows(named.id());
            String s = knows ? named.displayName().getString() : Component.translatable("chronicle.beyondsolo.secret").getString();
            SysFont.draw(g, SysFont.fit(s, 5.2f, true, GRID_W - 16), x + GRID_W / 2f, y + h - 9.5f, 5.2f, knows ? SystemTheme.TEXT_SOFT : SystemTheme.TEXT_DIM, true, 0.5f);
        }
        detail(g, pick, x + GRID_W + 6, y, w - GRID_W - 6, h, mouseX, mouseY);
    }

    private static @Nullable JournalEntry firstKnown(List<JournalEntry> pages) {
        for (JournalEntry e : pages) if (ChronicleClient.data().knows(e.id())) return e;
        return pages.isEmpty() ? null : pages.get(0);
    }

    /** The sections: the one open with its name, the others as an icon, each with how many of its pages are filled. */
    private void tabs(GuiGraphics g, float x, float y, float w, int mouseX, int mouseY) {
        float fh = 11, size = 5.2f;
        JournalCategory[] cats = JournalCategory.values();
        String[] labels = new String[cats.length];
        float total = 0;
        for (JournalCategory c : cats) {
            String count = known(c) + "/" + Journal.size(c);
            labels[c.ordinal()] = c == category ? c.displayName().getString() + "  " + count : count;
            total += ChronicleUi.pillWidth(fh, labels[c.ordinal()], true, size) + 3;
        }
        if (total - 3 > w) {
            for (JournalCategory c : cats) labels[c.ordinal()] = known(c) + "/" + Journal.size(c);
        }
        float px = x;
        for (JournalCategory c : cats) {
            boolean sel = c == category;
            float pw = ChronicleUi.pillWidth(fh, labels[c.ordinal()], true, size);
            boolean hover = !sel && Ui.inside(mouseX, mouseY, px, y, pw, fh);
            ChronicleUi.pill(g, px, y, fh, labels[c.ordinal()], ChronicleIcons.of(c), SystemTheme.ACCENT, sel, hover, size);
            if (fresh(c)) Shapes.circle(px + pw - 2, y + 1.2f, 2f).fill(SystemTheme.GOLD).glow(2, Gfx.alpha(SystemTheme.GOLD, 0.5f)).draw(g);
            hits.add(new ChronicleUi.Hit(px, y, pw, fh, () -> {
                if (category == c) return;
                category = c;
                reveal = true;
                SystemScreen.playClick();
            }));
            px += pw + 3;
        }
    }

    private static boolean fresh(JournalCategory c) {
        String p = prefix(c);
        for (String id : ChronicleClient.data().fresh()) if (id.startsWith(p) && ChronicleClient.isFresh(id)) return true;
        return false;
    }

    private void grid(GuiGraphics g, List<JournalEntry> pages, @Nullable JournalEntry pick, int mouseX, int mouseY) {
        boolean realms = category == JournalCategory.REALMS;
        float tw = realms ? REALM_W : TILE, th = realms ? REALM_H : TILE;
        int cols = Math.max(1, (int) ((gridW - 6 + GAP) / (tw + GAP)));
        float used = cols * (tw + GAP) - GAP;
        float ox = gridX + (gridW - 5 - used) / 2f;
        int rows = (pages.size() + cols - 1) / cols;
        maxScroll = Math.max(0, rows * (th + GAP) - GAP - gridH + 4);
        if (reveal) {
            reveal = false;
            scroll = scrollTarget = 0;
            for (int i = 0; i < pages.size(); i++) {
                if (pick != null && pages.get(i).id().equals(pick.id())) {
                    scroll = scrollTarget = Mth.clamp((i / cols) * (th + GAP) - gridH / 2f + th / 2f, 0, maxScroll);
                }
            }
        }
        scrollTarget = Mth.clamp(scrollTarget, 0, maxScroll);
        scroll = Anim.approach(scroll, scrollTarget, 18f);
        boolean inGrid = Ui.inside(mouseX, mouseY, gridX, gridY, gridW, gridH);
        ChronicleData data = ChronicleClient.data();
        boolean solid = Gfx.alpha() > 0.6f;
        g.enableScissor((int) gridX, (int) gridY, (int) Math.ceil(gridX + gridW), (int) Math.ceil(gridY + gridH));
        for (int i = 0; i < pages.size(); i++) {
            float tx = ox + (i % cols) * (tw + GAP);
            float ty = gridY + 2 + (i / cols) * (th + GAP) - scroll;
            if (ty + th < gridY || ty > gridY + gridH) continue;
            JournalEntry e = pages.get(i);
            boolean hover = inGrid && Ui.inside(mouseX, mouseY, tx, ty, tw, th);
            if (hover) hovered = e;
            tile(g, e, data.mark(e.id()), tx, ty, tw, th, pick != null && pick.id().equals(e.id()), hover, solid);
            float top = Math.max(ty, gridY), bottom = Math.min(ty + th, gridY + gridH);
            if (bottom > top) hits.add(new ChronicleUi.Hit(tx, top, tw, bottom - top, () -> pick(e)));
        }
        g.disableScissor();
        ChronicleUi.scrollbar(g, gridX + gridW - 3, gridY + 1, gridH - 2, scroll, maxScroll, SystemTheme.ACCENT);
    }

    private static void pick(JournalEntry e) {
        if (e.id().equals(SELECTED.get(e.category()))) return;
        SELECTED.put(e.category(), e.id());
        SystemScreen.playTick(1.7f);
    }

    /** The colour a page takes: its family's, a boss's red, a realm's light, the arsenal's gold. */
    private static int tint(JournalEntry e) {
        if (e.family() != null) return e.family().color();
        if (e.category() == JournalCategory.BOSSES) return BOSS;
        if (e.realm() != null) return e.realm().glow != 0 ? Gfx.opaque(e.realm().glow) : Gfx.opaque(Gfx.mix(e.realm().fogColor | 0xFF000000, 0xFFFFFFFF, 0.35f));
        if (e.category() == JournalCategory.REALMS) return 0xFFFF5468;
        return SystemTheme.GOLD;
    }

    private static void tile(GuiGraphics g, JournalEntry e, @Nullable ChronicleData.Mark mark, float x, float y, float w, float h, boolean sel,
                             boolean hover, boolean solid) {
        boolean known = mark != null;
        int c = tint(e);
        float r = 4;
        if (e.category() == JournalCategory.REALMS) {
            Shapes.box(x, y, w, h).radius(r).fill(0xFF050A14).draw(g);
            JournalArt.drawPicture(g, e, x + 1, y + 1, w - 2, h - 2, known ? (hover || sel ? 1f : 0.82f) : 0.13f);
            if (!known) Shapes.icon(Icons.QUESTION, x + w / 2f, y + h / 2f, 10).fill(Gfx.alpha(SystemTheme.TEXT_DIM, 0.8f)).draw(g);
            Shapes.box(x, y, w, h).radius(r).fill(0).border(sel ? 1f : 0.6f, sel ? SystemTheme.ACCENT : known ? Gfx.alpha(c, hover ? 0.9f : 0.45f) : 0x30FFFFFF)
                    .glow(sel ? 4 : 0, Gfx.alpha(SystemTheme.ACCENT, 0.4f)).draw(g);
        } else {
            Shapes.box(x, y, w, h).radius(r).fillV(known ? Gfx.alpha(c, hover || sel ? 0.26f : 0.16f) : 0x50101A2A, known ? Gfx.alpha(Gfx.shade(c, -0.6f), 0.3f) : 0x50060B14)
                    .border(sel ? 1f : 0.6f, sel ? SystemTheme.ACCENT : known ? Gfx.alpha(c, hover ? 0.9f : 0.4f) : 0x26FFFFFF)
                    .glow(sel ? 4 : 0, Gfx.alpha(SystemTheme.ACCENT, 0.4f)).draw(g);
            if (e.category() == JournalCategory.ARSENAL) {
                if (solid) JournalArt.drawItem(g, e, x + w / 2f, y + h / 2f, w * 0.68f, known);
            } else if (solid) {
                float turn = hover ? (float) Math.sin(Anim.now() * 1.4) * 0.9f : 0.55f;
                if (!JournalArt.drawEntity(g, e, mark, x + 1, y + 2, x + w - 1, y + h - 1, 0.78f, turn, known)) {
                    familyIcon(g, e, x + w / 2f, y + h / 2f, w * 0.5f, known);
                }
            }
        }
        if (known && ChronicleClient.isFresh(e.id())) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 250);
            Shapes.circle(x + w - 3.5f, y + 3.5f, 2.2f).fill(SystemTheme.GOLD).glow(2.5f, Gfx.alpha(SystemTheme.GOLD, 0.4f + 0.3f * Anim.pulse(1.4f))).draw(g);
            g.pose().popPose();
        }
    }

    private static void familyIcon(GuiGraphics g, JournalEntry e, float cx, float cy, float size, boolean known) {
        int c = tint(e);
        Shapes.icon(e.family() != null ? ChronicleIcons.of(e.family()) : ChronicleIcons.of(e.category()), cx, cy, size)
                .fill(known ? Gfx.mix(c, 0xFFFFFFFF, 0.4f) : SystemTheme.TEXT_DIM).draw(g);
    }

    // ------------------------------------------------------------------ the page picked

    private void detail(GuiGraphics g, @Nullable JournalEntry e, float x, float y, float w, float h, int mouseX, int mouseY) {
        detailX = x;
        detailY = y;
        detailW = w;
        detailH = h;
        if (e == null) {
            Ui.card(g, x, y, w, h, 8);
            return;
        }
        ChronicleData.Mark mark = ChronicleClient.data().mark(e.id());
        boolean known = mark != null;
        int c = tint(e);
        Ui.card(g, x, y, w, h, 8, c, known ? 0.14f : 0.03f);

        // the picture
        float px = x + 6, py = y + 6, pw = w - 12, ph = 64;
        boolean solid = Gfx.alpha() > 0.6f;
        if (e.category() == JournalCategory.REALMS) {
            Shapes.box(px, py, pw, ph).radius(5).fill(0xFF050A14).draw(g);
            JournalArt.drawPicture(g, e, px + 1, py + 1, pw - 2, ph - 2, known ? 1f : 0.12f);
            if (!known) Shapes.icon(Icons.QUESTION, px + pw / 2f, py + ph / 2f, 18).fill(Gfx.alpha(SystemTheme.TEXT_DIM, 0.8f)).draw(g);
            Shapes.box(px, py, pw, ph).radius(5).fill(0).border(0.75f, known ? Gfx.alpha(c, 0.7f) : 0x30FFFFFF).draw(g);
        } else {
            Shapes.box(px, py, pw, ph).radius(5).fillV(Gfx.alpha(c, known ? 0.10f : 0.03f), 0x60050A14).border(0.6f, Gfx.alpha(c, known ? 0.4f : 0.15f)).draw(g);
            float cx = px + pw / 2f;
            // light on the floor under it
            Shapes.box(cx - 30, py + ph - 9, 60, 6).radius(3).fill(0).glow(10, Gfx.alpha(c, known ? 0.35f : 0.1f)).draw(g);
            if (e.category() == JournalCategory.ARSENAL) {
                Shapes.circle(cx, py + ph / 2f, 4).fill(0).glow(26, Gfx.alpha(c, known ? 0.3f : 0.08f)).draw(g);
                if (solid) JournalArt.drawItem(g, e, cx, py + ph / 2f, 44, known);
            } else if (solid) {
                float turn = (float) Math.sin(Anim.now() * 0.55) * 0.95f;
                if (!JournalArt.drawEntity(g, e, mark, px + 2, py + 3, px + pw - 2, py + ph - 3, 0.8f, turn, known)) {
                    familyIcon(g, e, cx, py + ph / 2f, 26, known);
                }
            }
        }

        // the name and what it is
        float ty = py + ph + 5;
        g.pose().pushPose();
        g.pose().translate(0, 0, 250);
        Component name = known ? e.displayName() : Component.translatable("chronicle.beyondsolo.secret");
        SysFont.draw(g, SysFont.fit(name.getString(), 7.4f, true, w - 12), x + w / 2f, ty, 7.4f, known ? Gfx.mix(c, 0xFFFFFFFF, 0.55f) : SystemTheme.TEXT_SOFT, true, 0.5f);
        ty += 7.4f * SysFont.LINE + 1;
        List<String> chips = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        chipsFor(e, chips, colors);
        float chipH = 7.6f, chipSize = 4.4f, total = 0;
        for (String s : chips) total += SysFont.width(s, chipSize, true) + chipH * 0.9f + 3;
        float cx0 = x + (w - (total - 3)) / 2f;
        for (int i = 0; i < chips.size(); i++) cx0 += Ui.chip(g, cx0, ty, chipH, chips.get(i), chipSize, colors.get(i), known) + 3;
        ty += chipH + 4;

        // the bottom: when it was first met, how many fell, a boss's forms
        float by = y + h - 9;
        boolean slayable = e.category() == JournalCategory.CREATURES || e.category() == JournalCategory.BOSSES;
        if (known) {
            String first = Component.translatable("chronicle.beyondsolo.journal.first_" + e.category().id(), ChronicleUi.day(mark.seen)).getString();
            if (slayable) {
                String slain = Component.translatable("chronicle.beyondsolo.journal.slain", mark.kills).getString();
                SysFont.draw(g, SysFont.fit(slain, 5f, true, w / 2f - 8), x + 8, by, 5f, mark.kills > 0 ? SystemTheme.GOLD : SystemTheme.TEXT_DIM, true, 0f);
                SysFont.draw(g, SysFont.fit(first, 4.8f, false, w / 2f - 8), x + w - 8, by + 0.2f, 4.8f, SystemTheme.TEXT_DIM, false, 1f);
            } else {
                SysFont.draw(g, SysFont.fit(first, 4.8f, false, w - 16), x + w / 2f, by + 0.2f, 4.8f, SystemTheme.TEXT_DIM, false, 0.5f);
            }
        }
        float loreBottom = by - 4;
        if (e.boss() != null) {
            loreBottom = forms(g, e, mark, x, by - 18, w, mouseX, mouseY) - 3;
        }

        // what is known of it
        Component lore;
        int loreColor = SystemTheme.TEXT_SOFT;
        if (!known) {
            lore = Component.translatable("chronicle.beyondsolo.journal.hint." + e.category().id());
            loreColor = SystemTheme.TEXT_DIM;
        } else if (slayable && mark.kills <= 0) {
            lore = Component.translatable("chronicle.beyondsolo.journal.hint.slay");
            loreColor = SystemTheme.TEXT_DIM;
        } else {
            lore = lore(e);
        }
        if (!e.id().equals(loreFor)) {
            loreFor = e.id();
            loreScroll = loreTarget = 0;
        }
        float ls = 5.2f, lh = ls * SysFont.LINE;
        List<SysFont.Line> lines = SysFont.wrap(lore, ls, false, w - 18);
        float areaH = loreBottom - ty;
        loreMax = Math.max(0, lines.size() * lh - areaH);
        loreTarget = Mth.clamp(loreTarget, 0, loreMax);
        loreScroll = Anim.approach(loreScroll, loreTarget, 18f);
        g.pose().popPose();
        g.enableScissor((int) x, (int) ty, (int) (x + w), (int) Math.max(ty, loreBottom));
        g.pose().pushPose();
        g.pose().translate(0, 0, 250);
        float ly = ty - loreScroll;
        for (SysFont.Line l : lines) {
            if (ly + lh > ty - 1 && ly < loreBottom) SysFont.draw(g, l, x + 9, ly, loreColor, 0f);
            ly += lh;
        }
        g.pose().popPose();
        g.disableScissor();
        if (loreMax > 0) ChronicleUi.scrollbar(g, x + w - 4, ty, Math.max(4, areaH), loreScroll, loreMax, c);
    }

    /** The lore line: a weapon's own, else the chronicle's. */
    private static Component lore(JournalEntry e) {
        for (String key : e.loreKeys()) if (I18n.exists(key)) return Component.translatable(key);
        return Component.translatable("chronicle.beyondsolo.journal.no_lore");
    }

    private static void chipsFor(JournalEntry e, List<String> chips, List<Integer> colors) {
        switch (e.category()) {
            case CREATURES -> {
                CreatureFamily f = e.family();
                if (f != null) {
                    chips.add(f.displayName().getString());
                    colors.add(f.color());
                }
            }
            case BOSSES -> {
                chips.add(Component.translatable("chronicle.beyondsolo.journal.boss").getString());
                colors.add(BOSS);
            }
            case REALMS -> {
                GateRealm r = e.realm();
                if (r != null) {
                    chips.add(r.minRank == r.maxRank ? r.minRank.name() : r.minRank.name() + "-" + r.maxRank.name());
                    colors.add(Gfx.opaque(r.minRank.color()));
                }
                chips.add(Component.translatable(r != null ? "chronicle.beyondsolo.journal.realm" : "chronicle.beyondsolo.journal.place").getString());
                colors.add(SystemTheme.ACCENT);
            }
            case ARSENAL -> {
                chips.add(Component.translatable("chronicle.beyondsolo.journal.weapon").getString());
                colors.add(SystemTheme.GOLD);
            }
        }
    }

    /** A boss's forms: a rank emblem for every rank it lives at, lit once met there, and its ascended form. Returns the top. */
    private static float forms(GuiGraphics g, JournalEntry e, @Nullable ChronicleData.Mark mark, float x, float y, float w, int mouseX, int mouseY) {
        BossKind kind = e.boss();
        List<PlayerRank> ranks = new ArrayList<>();
        for (PlayerRank r : PlayerRank.values()) if (kind.livesAt(r)) ranks.add(r);
        float step = 13;
        float total = (ranks.size() + 1) * step;
        float ex = x + (w - total) / 2f + step / 2f;
        float ey = y + 5;
        int forms = mark == null ? 0 : mark.forms;
        Component label = null;
        for (PlayerRank r : ranks) {
            boolean met = (forms & (1 << JournalEntry.formBit(r))) != 0;
            boolean hover = Ui.inside(mouseX, mouseY, ex - 6, ey - 6, 12, 12);
            if (met) {
                Ui.rankEmblem(g, r, ex, ey, 4.4f, hover ? 0.6f : 0);
            } else {
                Shapes.circle(ex, ey, 4.4f).fill(0x80060B16).border(0.5f, 0x30FFFFFF).draw(g);
                SysFont.draw(g, r.name(), ex, SysFont.centerY(ey - 4.4f, 8.8f, 4.6f), 4.6f, SystemTheme.TEXT_DIM, true, 0.5f);
            }
            if (hover) label = met ? kind.name(r) : Component.translatable("chronicle.beyondsolo.journal.form_unknown", r.name());
            ex += step;
        }
        boolean ascended = (forms & (1 << JournalEntry.ASCENDED_BIT)) != 0;
        boolean hoverA = Ui.inside(mouseX, mouseY, ex - 6, ey - 6, 12, 12);
        Shapes.circle(ex, ey, 4.4f).fill(ascended ? 0xC0301008 : 0x80060B16).border(0.6f, ascended ? SystemTheme.GOLD : 0x30FFFFFF)
                .glow(ascended ? 3 : 0, Gfx.alpha(SystemTheme.GOLD, 0.4f)).draw(g);
        Shapes.icon(Icons.CROWN, ex, ey, 5.6f).fill(ascended ? SystemTheme.GOLD : SystemTheme.TEXT_DIM).draw(g);
        if (hoverA) label = Component.translatable(ascended ? "chronicle.beyondsolo.journal.ascended" : "chronicle.beyondsolo.journal.ascended_unknown");
        if (label == null) {
            int met = Integer.bitCount(forms & 0x3F);
            label = Component.translatable("chronicle.beyondsolo.journal.forms", met, ranks.size());
        }
        SysFont.draw(g, SysFont.fit(label.getString(), 4.6f, false, w - 12), x + w / 2f, ey + 7, 4.6f, SystemTheme.TEXT_DIM, false, 0.5f);
        return y;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean click(double mouseX, double mouseY, int button) {
        return button == 0 && ChronicleUi.click(hits, mouseX, mouseY);
    }

    @Override
    public boolean scroll(double mouseX, double mouseY, double amount) {
        if (Ui.inside(mouseX, mouseY, gridX, gridY, gridW, gridH) && maxScroll > 0) {
            float th = category == JournalCategory.REALMS ? REALM_H : TILE;
            scrollTarget = Mth.clamp(scrollTarget - (float) amount * (th + GAP), 0, maxScroll);
            return true;
        }
        if (Ui.inside(mouseX, mouseY, detailX, detailY, detailW, detailH) && loreMax > 0) {
            loreTarget = Mth.clamp(loreTarget - (float) amount * 12, 0, loreMax);
            return true;
        }
        return false;
    }
}
