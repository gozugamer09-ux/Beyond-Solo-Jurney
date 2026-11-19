package com.beyondsolo.client.association;

import com.beyondsolo.association.Association;
import com.beyondsolo.association.Standing;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.economy.EconomyClient;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.quest.ClientQuests;
import com.beyondsolo.network.AssociationActionPayload;
import com.beyondsolo.rival.RivalGuild;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Hunter Association's office: the officer and the hunter's license on the left; on the right the contract board
 * (bounties on the gates the office watches), gate permits (a gate of the rank you ask for, its clearing rights
 * yours), the rank exam, the hunter's standing with the national merit board, the hands for hire (porters and
 * freelance hunters, {@code rival.Hires}), and the great guilds' offers with the hunter's contract
 * ({@code scouting.Scouting}).
 */
public class AssociationScreen extends AssociationWindow {
    private static final String[] TABS = {"contracts", "permits", "exam", "standing", "hire", "guilds"};
    private static final ResourceLocation[] TAB_ICONS = {Icons.SEAL, Icons.FLAG, Icons.ID_CARD, Icons.MEDAL, Icons.HANDSHAKE, Icons.CROWN};
    /** The hired hands' colours: a porter's sand, a freelance hunter's steel blue (as over their heads). */
    private static final int SAND = 0xFFD9B26F, FREELANCE = 0xFF8FB8D8;

    private int tab;
    private double tabAt = Anim.now();

    AssociationScreen(int tab) {
        super(Component.translatable("ui.beyondsolo.association.title"), 440, 258, 132);
        this.tab = Mth.clamp(tab, 0, TABS.length - 1);
    }

    void setTab(int tab) {
        int t = Mth.clamp(tab, 0, TABS.length - 1);
        if (t != this.tab) tabAt = Anim.now();
        this.tab = t;
    }

    @Override
    CompoundTag data() {
        return AssociationClient.office();
    }

    @Override
    int accent() {
        return STEEL;
    }

    @Override
    Component role() {
        return Component.translatable("ui.beyondsolo.association.officer");
    }

    private static Component ui(String key, Object... args) {
        return Component.translatable("ui.beyondsolo.association." + key, args);
    }

    // ------------------------------------------------------------------ left: the license

    @Override
    void under(GuiGraphics g, float x, float y, float lw, float bottom, float t) {
        CompoundTag d = data();
        PlayerRank rank = rank(d.getInt("rank"));
        Standing st = Standing.byIndex(d.getInt("standing"));
        Ui.divider(g, x, y, lw, Gfx.alpha(STEEL, 0.4f));
        SysFont.spaced(g, ui("license").getString(), x, y + 5, 4.6f, SystemTheme.TEXT_DIM, true, 1.2f, 0f);
        Ui.rankEmblem(g, rank, x + 9, y + 23, 8, 0.2f + 0.1f * Anim.pulse(2.6f));
        SysFont.text(g, SysFont.fit(ui("hunter_rank", rank.displayName()).getString(), 6f, true, lw - 24), x + 21, y + 15.5f, 6f, SystemTheme.TEXT);
        SysFont.text(g, SysFont.fit(st.displayName().getString(), 5.2f, true, lw - 24), x + 21, y + 24, 5.2f, st.color);
        // merit towards the next standing
        long merit = d.getLong("merit"), from = d.getLong("from"), to = d.getLong("to");
        float my = y + 38;
        SysFont.spaced(g, ui("merit").getString(), x, my, 4.4f, SystemTheme.TEXT_SOFT, true, 1.1f, 0f);
        String mt = to < 0 ? gold(merit) : gold(merit) + " / " + gold(to);
        SysFont.draw(g, mt, x + lw, my, 4.6f, st.color, true, 1f);
        float frac = to < 0 ? 1f : (float) (merit - from) / Math.max(1, to - from);
        Ui.bar(g, x, my + 7, lw, 3f, frac, Gfx.shade(st.color, -0.3f), st.color);
        if (to >= 0) {
            Standing next = st.next();
            String nx = SysFont.fit(ui("next_standing", next == null ? "" : next.displayName()).getString(), 4.2f, false, lw);
            SysFont.text(g, nx, x, my + 13, 4.2f, SystemTheme.TEXT_DIM);
        }
        // the record
        float ry = Math.max(my + 22, bottom - 30);
        String[] keys = {"stat.contracts", "stat.clears", "stat.breaks", "stat.exams"};
        int[] vals = {d.getInt("contracts"), d.getInt("clears"), d.getInt("suppressions"), d.getInt("exams")};
        float cw = lw / 2f;
        for (int i = 0; i < keys.length; i++) {
            float cx = x + (i % 2) * cw, cy = ry + (i / 2) * 14;
            SysFont.text(g, String.valueOf(vals[i]), cx, cy, 6f, SystemTheme.TEXT);
            SysFont.text(g, SysFont.fit(ui(keys[i]).getString(), 4f, false, cw - 2), cx, cy + 7, 4f, SystemTheme.TEXT_DIM);
        }
    }

    // ------------------------------------------------------------------ right: the tabs

    @Override
    void content(GuiGraphics g, float x, float y, float cw, float ch, int mouseX, int mouseY, float t) {
        // the tabs (their labels shrink to fit, Spanish runs long)
        float tw = (cw - (TABS.length - 1) * 4) / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            float tx = x + i * (tw + 4);
            boolean sel = i == tab;
            boolean hover = Ui.inside(mouseX, mouseY, tx, y, tw, 15);
            int c = STEEL;
            Shapes.pill(tx, y, tw, 15).fillV(Gfx.alpha(c, sel ? 0.34f : hover ? 0.16f : 0.06f), Gfx.alpha(Gfx.shade(c, -0.4f), sel ? 0.24f : 0.06f))
                    .border(sel ? 0.9f : 0.6f, Gfx.alpha(c, sel ? 0.95f : 0.35f)).glow(sel ? 4 : 0, Gfx.alpha(c, 0.3f)).draw(g);
            String label = ui("tab." + TABS[i]).getString();
            float ls = 4.8f;
            while (ls > 3.6f && SysFont.spacedWidth(label, ls, true, 1f) + 9 > tw - 4) ls -= 0.2f;
            float lw = SysFont.spacedWidth(label, ls, true, 1f) + 9;
            float lx = tx + (tw - lw) / 2f;
            Shapes.icon(TAB_ICONS[i], lx + 3, y + 7.5f, 6).fill(sel ? SystemTheme.TEXT : SystemTheme.TEXT_SOFT).draw(g);
            SysFont.spaced(g, label, lx + 9, SysFont.centerY(y, 15, ls), ls, sel ? SystemTheme.TEXT : SystemTheme.TEXT_SOFT, true, 1f, 0f);
            if (!sel) {
                int pick = i;
                hits.add(new Hit(tx, y, tw, 15, () -> setTab(pick)));
            }
        }
        float k = Anim.outCubic(Anim.progress(tabAt, 0.25f));
        float o = Gfx.fade(k);
        float py = y + 21 + (1 - k) * 4, ph = ch - 21;
        switch (tab) {
            case Association.TAB_PERMITS -> permits(g, x, py, cw, ph, mouseX, mouseY);
            case Association.TAB_EXAM -> exam(g, x, py, cw, ph, mouseX, mouseY);
            case Association.TAB_STANDING -> standing(g, x, py, cw, ph);
            case Association.TAB_HIRE -> hire(g, x, py, cw, ph, mouseX, mouseY);
            case Association.TAB_GUILDS -> guilds(g, x, py, cw, ph, mouseX, mouseY);
            default -> contracts(g, x, py, cw, ph, mouseX, mouseY);
        }
        Gfx.setAlpha(o);
    }

    // ------------------------------------------------------------------ contracts

    private void contracts(GuiGraphics g, float x, float y, float cw, float ch, int mouseX, int mouseY) {
        CompoundTag d = data();
        SysFont.spaced(g, ui("contracts.board").getString(), x, y, 5f, SystemTheme.TEXT, true, 1.2f, 0f);
        String held = ui("contracts.held", d.getInt("holding"), d.getInt("slots")).getString();
        SysFont.draw(g, held, x + cw, y, 4.8f, d.getInt("holding") >= d.getInt("slots") ? AMBER : SystemTheme.TEXT_SOFT, true, 1f);
        float ly = y + 10;
        ListTag board = d.getList("board", Tag.TAG_COMPOUND);
        ListTag mine = d.getList("held", Tag.TAG_COMPOUND);
        Set<UUID> onBoard = new HashSet<>();
        for (int i = 0; i < board.size(); i++) onBoard.add(board.getCompound(i).getUUID("gate"));
        List<CompoundTag> rows = new ArrayList<>();
        // yours that the board doesn't show (far off, or taken at another office) first
        for (int i = 0; i < mine.size(); i++) {
            CompoundTag c = mine.getCompound(i);
            if (!c.getBoolean("permit") && !onBoard.contains(c.getUUID("gate"))) {
                CompoundTag r = c.copy();
                r.putBoolean("held", true);
                rows.add(r);
            }
        }
        for (int i = 0; i < board.size(); i++) rows.add(board.getCompound(i));
        float rowH = 27, gap = 3;
        int room = (int) ((ch - 12) / (rowH + gap));
        if (rows.isEmpty()) {
            Ui.card(g, x, ly + 2, cw, 44, 8, STEEL, 0.06f);
            Shapes.icon(Icons.COMPASS, x + 14, ly + 24, 12).fill(Gfx.alpha(STEEL, 0.7f)).draw(g);
            para(g, ui("contracts.empty", d.getInt("radius") > 0 ? d.getInt("radius") : 512), x + 28, ly + 10, cw - 36, 4.8f, SystemTheme.TEXT_SOFT);
            return;
        }
        for (int i = 0; i < Math.min(room, rows.size()); i++) {
            float k = Anim.outCubic(Anim.window(Anim.since(openedAt), 0.25f + i * 0.05f, 0.55f + i * 0.05f));
            float o = Gfx.fade(k);
            contract(g, rows.get(i), x + (1 - k) * 10, ly, cw, rowH, mouseX, mouseY);
            Gfx.setAlpha(o);
            ly += rowH + gap;
        }
        if (rows.size() > room) {
            SysFont.draw(g, ui("contracts.more", rows.size() - room).getString(), x + cw, ly - 1, 4.2f, SystemTheme.TEXT_DIM, false, 1f);
        }
    }

    private void contract(GuiGraphics g, CompoundTag c, float x, float y, float cw, float rh, int mouseX, int mouseY) {
        PlayerRank rank = rank(c.getInt("rank"));
        int kind = c.getInt("kind");
        boolean held = c.getBoolean("held");
        int tint = kind == Association.BREAK ? SystemTheme.DANGER : kind == Association.UNSTABLE ? AMBER : STEEL;
        Shapes.box(x, y, cw, rh).radius(6).fillV(Gfx.alpha(tint, held ? 0.2f : 0.1f), Gfx.alpha(Gfx.shade(tint, -0.5f), held ? 0.14f : 0.06f))
                .border(held ? 0.9f : 0.6f, Gfx.alpha(held ? SystemTheme.GOOD : tint, held ? 0.75f : 0.35f)).draw(g);
        Shapes.box(x + 1.2f, y + 3, 1.6f, rh - 6).radius(0.8f).fill(tint).draw(g);
        Ui.rankEmblem(g, rank, x + 13, y + rh / 2f, 7, kind == Association.BREAK ? 0.6f : 0.15f);
        // its dungeon, and what state it is in
        float tx = x + 25;
        String realm = AssociationClient.text(c, "realm").getString();
        // the button is as wide as its word needs (Spanish runs longer)
        float bw = Math.max(38, Math.max(SysFont.spacedWidth(ui("take").getString(), 5.4f, true, 0.8f),
                SysFont.spacedWidth(ui("drop").getString(), 5.4f, true, 0.8f)) + 22);
        float right = x + cw - bw - 8;
        String goldText = "+" + gold(c.getLong("gold"));
        float gw = c.getLong("gold") > 0 ? SysFont.width(goldText, 5.2f, true) + 9 : 0;
        String meritText = ui("merit_short", c.getLong("merit")).getString();
        float mw = SysFont.width(meritText, 4.4f, false);
        float textRight = right - Math.max(gw, mw) - 6;
        String name = SysFont.fit(realm, 5.4f, true, Math.max(30, textRight - tx - 4));
        SysFont.text(g, name, tx, y + 4.5f, 5.4f, SystemTheme.TEXT);
        float chipX = tx + SysFont.width(name, 5.4f, true) + 4;
        if (kind == Association.BREAK) {
            chipX += tag(g, chipX, y + 3.5f, ui("kind.break").getString(), SystemTheme.DANGER, true) + 2;
        } else if (kind == Association.UNSTABLE) {
            long left = c.getLong("left");
            chipX += tag(g, chipX, y + 3.5f, ui("kind.unstable", clock(left)).getString(), AMBER, true) + 2;
        }
        // the second line: how far and which way, and warnings
        String where = ui("where", c.getInt("dist"), AssociationClient.text(c, "dir")).getString();
        SysFont.text(g, where, tx, y + 15, 4.5f, SystemTheme.TEXT_SOFT);
        float wx = tx + SysFont.width(where, 4.5f, false) + 5;
        if (c.getBoolean("above") && wx < textRight - 30) wx += tag(g, wx, y + 13.5f, ui("above").getString(), SystemTheme.DANGER, false) + 2;
        if (c.getBoolean("scouted") && wx < textRight - 30) wx += tag(g, wx, y + 13.5f, ui("scouted").getString(), VIOLET, false) + 2;
        if (held && wx < textRight - 24) tag(g, wx, y + 13.5f, ui("yours").getString(), SystemTheme.GOOD, true);
        // the bounty
        if (gw > 0) {
            Shapes.icon(Icons.COINS, right - gw - 2, y + 8, 6).fill(GOLD).draw(g);
            SysFont.draw(g, goldText, right - 4, y + 5, 5.2f, GOLD, true, 1f);
        }
        SysFont.draw(g, meritText, right - 4, y + 15, 4.4f, STEEL, false, 1f);
        // take it, or give it back
        UUID gate = c.getUUID("gate");
        CompoundTag args = new CompoundTag();
        args.putUUID("gate", gate);
        if (held) {
            button(g, x + cw - bw - 5, y + (rh - 13) / 2f, bw, 13, ui("drop").getString(), Icons.X, SystemTheme.DANGER, mouseX, mouseY,
                    ui("hint.drop"), () -> AssociationClient.send(AssociationActionPayload.DROP, args));
        } else {
            boolean full = data().getInt("holding") >= data().getInt("slots");
            button(g, x + cw - bw - 5, y + (rh - 13) / 2f, bw, 13, ui("take").getString(), Icons.CHECK, SystemTheme.GOOD, mouseX, mouseY,
                    full ? ui("hint.full") : ui("hint.take", rank.displayName()), full ? null : () -> AssociationClient.send(AssociationActionPayload.ACCEPT, args));
        }
    }

    // ------------------------------------------------------------------ permits

    private void permits(GuiGraphics g, float x, float y, float cw, float ch, int mouseX, int mouseY) {
        CompoundTag d = data();
        SysFont.spaced(g, ui("permits.title").getString(), x, y, 5f, SystemTheme.TEXT, true, 1.2f, 0f);
        int left = d.getInt("permitsLeft"), perDay = d.getInt("permitsPerDay");
        SysFont.draw(g, ui("permits.left", left, perDay).getString(), x + cw, y, 4.8f, left > 0 ? SystemTheme.TEXT_SOFT : AMBER, true, 1f);
        float ty = para(g, ui("permits.desc"), x, y + 10, cw, 4.6f, SystemTheme.TEXT_SOFT) + 4;
        PlayerRank top = rank(d.getInt("permitTop"));
        long[] fees = d.getLongArray("permitFees");
        float tileW = (cw - 5 * 4) / 6f, tileH = 58;
        long gold = EconomyClient.gold();
        for (PlayerRank r : PlayerRank.values()) {
            float tx = x + r.ordinal() * (tileW + 4);
            boolean allowed = r.tier() <= top.tier();
            long fee = r.ordinal() < fees.length ? fees[r.ordinal()] : 0;
            boolean afford = gold >= fee;
            boolean enabled = allowed && left > 0 && afford;
            boolean hover = Ui.inside(mouseX, mouseY, tx, ty, tileW, tileH);
            int c = SystemTheme.rank(r);
            Shapes.box(tx, ty, tileW, tileH).radius(7).fillV(Gfx.alpha(c, enabled && hover ? 0.3f : allowed ? 0.14f : 0.04f), Gfx.alpha(Gfx.shade(c, -0.5f), allowed ? 0.12f : 0.03f))
                    .border(hover && enabled ? 1f : 0.6f, Gfx.alpha(c, allowed ? (hover ? 1f : 0.55f) : 0.2f)).glow(hover && enabled ? 5 : 0, Gfx.alpha(c, 0.35f)).draw(g);
            float old = Gfx.fade(allowed ? 1f : 0.45f);
            Ui.rankEmblem(g, r, tx + tileW / 2f, ty + 15, 9, hover && enabled ? 0.6f : 0.15f);
            SysFont.draw(g, ui("rank_gate", r.displayName()).getString(), tx + tileW / 2f, ty + 30, 4.6f, SystemTheme.TEXT, true, 0.5f);
            if (allowed) {
                Shapes.icon(Icons.COINS, tx + tileW / 2f - SysFont.width(gold(fee), 4.8f, true) / 2f - 4, ty + 43, 5.5f).fill(afford ? GOLD : SystemTheme.DANGER).draw(g);
                SysFont.draw(g, gold(fee), tx + tileW / 2f + 3, ty + 40.5f, 4.8f, afford ? GOLD : SystemTheme.DANGER, true, 0.5f);
            } else {
                Shapes.icon(Icons.LOCK, tx + tileW / 2f, ty + 44, 7).fill(SystemTheme.TEXT_DIM).draw(g);
            }
            Gfx.setAlpha(old);
            if (hover) hint = !allowed ? ui("hint.permit_rank", top.displayName()) : left <= 0 ? ui("hint.permit_none") : !afford ? ui("hint.gold", gold(fee))
                    : ui("hint.permit", r.displayName(), gold(fee));
            if (enabled) {
                CompoundTag args = new CompoundTag();
                args.putInt("rank", r.ordinal());
                hits.add(new Hit(tx, ty, tileW, tileH, () -> AssociationClient.send(AssociationActionPayload.PERMIT, args)));
            }
        }
        // the permits held
        float hy = ty + tileH + 8;
        SysFont.spaced(g, ui("permits.yours").getString(), x, hy, 4.6f, SystemTheme.TEXT_DIM, true, 1.2f, 0f);
        hy += 9;
        ListTag mine = d.getList("held", Tag.TAG_COMPOUND);
        int shown = 0;
        for (int i = 0; i < mine.size() && hy + 27 <= y + ch; i++) {
            CompoundTag c = mine.getCompound(i);
            if (!c.getBoolean("permit")) continue;
            CompoundTag r = c.copy();
            r.putBoolean("held", true);
            contract(g, r, x, hy, cw, 27, mouseX, mouseY);
            hy += 30;
            shown++;
        }
        if (shown == 0) SysFont.text(g, ui("permits.none").getString(), x, hy, 4.6f, SystemTheme.TEXT_DIM);
    }

    // ------------------------------------------------------------------ the rank exam

    private void exam(GuiGraphics g, float x, float y, float cw, float ch, int mouseX, int mouseY) {
        CompoundTag e = data().getCompound("exam");
        int next = e.getInt("next");
        if (next < 0) {
            Ui.card(g, x, y, cw, 60, 9, GOLD, 0.12f);
            Ui.rankEmblem(g, PlayerRank.S, x + 26, y + 30, 14, 0.5f);
            SysFont.spaced(g, ui("exam.title").getString(), x + 48, y + 14, 6f, SystemTheme.TEXT, true, 1.5f, 0f);
            para(g, ui("exam.top"), x + 48, y + 26, cw - 56, 4.8f, SystemTheme.TEXT_SOFT);
            return;
        }
        PlayerRank rank = rank(next);
        int rc = SystemTheme.rank(rank);
        float tx = x + 70, tw = cw - 78;
        // the card fits what it says: the emblem, or the title, the description and two rows of terms
        int descLines = SysFont.wrap(ui("exam.desc", rank.displayName()), 4.6f, false, tw).size();
        float cardH = Math.max(84, 19 + descLines * 4.6f * SysFont.LINE + 3 + 20 + 6);
        Ui.card(g, x, y, cw, cardH, 9, rc, 0.14f);
        float pulse = Anim.pulse(2.2f);
        Shapes.circle(x + 34, y + 40, 26).fill(0x00000000).glow(18 + 6 * pulse, Gfx.alpha(rc, 0.3f)).draw(g);
        Ui.rankEmblem(g, rank, x + 34, y + 40, 18, 0.4f + 0.3f * pulse);
        SysFont.draw(g, ui("exam.certify", rank.displayName()).getString(), x + 34, y + 68, 4.6f, Gfx.mix(rc, 0xFFFFFFFF, 0.4f), true, 0.5f);
        SysFont.spaced(g, ui("exam.title").getString(), tx, y + 8, 6.4f, SystemTheme.TEXT, true, 1.6f, 0f);
        float py = para(g, ui("exam.desc", rank.displayName()), tx, y + 19, tw, 4.6f, SystemTheme.TEXT_SOFT) + 3;
        ResourceLocation[] icons = {Icons.SKULL, Icons.HOURGLASS, Icons.COINS, Icons.MEDAL};
        Component[] lines = {ui("exam.req.gate", rank.displayName()), ui("exam.req.time", e.getInt("minutes")),
                ui("exam.req.fee", gold(e.getLong("fee"))), ui("exam.req.merit", e.getInt("merit"))};
        int[] colors = {rc, SystemTheme.ACCENT, GOLD, STEEL};
        for (int i = 0; i < lines.length; i++) {
            float lx = tx + (i % 2) * (tw / 2f), ly = py + (i / 2) * 10;
            Shapes.icon(icons[i], lx + 3, ly + 3, 6).fill(Gfx.mix(colors[i], 0xFFFFFFFF, 0.3f)).draw(g);
            SysFont.text(g, SysFont.fit(lines[i].getString(), 4.6f, false, tw / 2f - 12), lx + 9, ly, 4.6f, SystemTheme.TEXT);
        }
        // where it stands
        float sy = y + cardH + 6;
        String block = e.getString("block");
        if (e.contains("gate")) {
            CompoundTag gate = e.getCompound("gate");
            long left = e.getLong("deadline") - ClientQuests.gameTime();
            Ui.card(g, x, sy, cw, 32, 8, GOLD, 0.12f);
            Shapes.icon(Icons.HOURGLASS, x + 12, sy + 16, 9).fill(GOLD).draw(g);
            SysFont.spaced(g, ui("exam.underway").getString(), x + 22, sy + 6, 5f, GOLD, true, 1.2f, 0f);
            SysFont.text(g, ui("exam.underway_at", gate.getInt("dist"), AssociationClient.text(gate, "dir")).getString(), x + 22, sy + 17, 4.6f, SystemTheme.TEXT_SOFT);
            SysFont.draw(g, clock(left), x + cw - 10, sy + 10, 9f, left < 20 * 120 ? SystemTheme.DANGER : SystemTheme.TEXT, true, 1f);
        } else if (block.isEmpty()) {
            Ui.card(g, x, sy, cw, 32, 8, SystemTheme.GOOD, 0.1f);
            Shapes.icon(Icons.CHECK, x + 12, sy + 16, 9).fill(SystemTheme.GOOD).draw(g);
            paraMid(g, ui("exam.ready"), x + 22, sy + 16, cw - 120, 4.6f, SystemTheme.TEXT);
            long fee = e.getLong("fee");
            boolean afford = EconomyClient.gold() >= fee;
            button(g, x + cw - 92, sy + 8, 84, 16, ui("exam.start").getString(), Icons.ID_CARD, GOLD, mouseX, mouseY,
                    afford ? ui("hint.exam", rank.displayName(), gold(fee)) : ui("hint.gold", gold(fee)),
                    afford ? () -> AssociationClient.send(AssociationActionPayload.EXAM) : null);
        } else {
            boolean retry = block.equals("exam.retry");
            int c = retry ? AMBER : SystemTheme.TEXT_SOFT;
            Ui.card(g, x, sy, cw, 32, 8, c, 0.08f);
            Shapes.icon(retry ? Icons.HOURGLASS : Icons.LOCK, x + 12, sy + 16, 9).fill(c).draw(g);
            paraMid(g, ui("exam.block." + block.replace("exam.", ""), e.getLong("wait")), x + 22, sy + 16, cw - 30, 4.6f, SystemTheme.TEXT_SOFT);
        }
        SysFont.text(g, ui("exam.record", e.getInt("passed"), e.getInt("failed")).getString(), x, sy + 38, 4.4f, SystemTheme.TEXT_DIM);
    }

    // ------------------------------------------------------------------ standing

    private void standing(GuiGraphics g, float x, float y, float cw, float ch) {
        CompoundTag d = data();
        Standing mine = Standing.byIndex(d.getInt("standing"));
        float lw = cw * 0.47f;
        SysFont.spaced(g, ui("standing.ladder").getString(), x, y, 5f, SystemTheme.TEXT, true, 1.2f, 0f);
        float ly = y + 10;
        long merit = d.getLong("merit");
        for (Standing s : Standing.values()) {
            boolean reached = s.ordinal() <= mine.ordinal();
            boolean current = s == mine;
            float rh = 24;
            Shapes.box(x, ly, lw, rh).radius(5).fill(Gfx.alpha(s.color, current ? 0.2f : reached ? 0.08f : 0.03f))
                    .border(current ? 0.9f : 0.5f, Gfx.alpha(s.color, current ? 0.9f : reached ? 0.4f : 0.15f)).draw(g);
            Shapes.icon(reached ? Icons.CHECK : Icons.LOCK, x + 8, ly + 7, 6).fill(reached ? s.color : SystemTheme.TEXT_DIM).draw(g);
            SysFont.text(g, s.displayName().getString(), x + 15, ly + 3.5f, 5.2f, reached ? s.color : SystemTheme.TEXT_SOFT);
            SysFont.draw(g, s.merit() == 0 ? "-" : gold(s.merit()), x + lw - 5, ly + 4, 4.6f, reached ? SystemTheme.TEXT_SOFT : SystemTheme.TEXT_DIM, true, 1f);
            // what it brings, on two lines if it needs them (Spanish runs long)
            Component perk = s.ordinal() > 0 ? s.perk() : ui("standing.registered");
            int pc = s.ordinal() > 0 && reached ? SystemTheme.TEXT_SOFT : SystemTheme.TEXT_DIM;
            List<SysFont.Line> lines = SysFont.wrap(perk, 4f, false, lw - 20);
            if (lines.size() <= 1) {
                SysFont.text(g, SysFont.fit(perk.getString(), 4f, false, lw - 20), x + 15, ly + 13.5f, 4f, pc);
            } else {
                for (int i = 0; i < 2; i++) SysFont.draw(g, lines.get(i), x + 15, ly + 11.4f + i * 4f * SysFont.LINE, pc, 0f);
            }
            ly += rh + 3;
        }
        // the national merit board
        float bx = x + lw + 8, bw = cw - lw - 8;
        SysFont.spaced(g, ui("standing.national").getString(), bx, y, 5f, SystemTheme.TEXT, true, 1.2f, 0f);
        Ui.card(g, bx, y + 10, bw, ch - 10, 7, GOLD, 0.06f);
        ListTag rows = d.getList("national", Tag.TAG_COMPOUND);
        float ry = y + 14, rowH = 15;
        for (int i = 0; i < rows.size() && ry + rowH <= y + ch - 14; i++) {
            CompoundTag r = rows.getCompound(i);
            boolean me = r.getBoolean("me");
            int place = r.getInt("place");
            if (me && i > 0 && rows.getCompound(i - 1).getInt("place") < place - 1) {
                SysFont.draw(g, "...", bx + bw / 2f, ry - 2, 5f, SystemTheme.TEXT_DIM, true, 0.5f);
                ry += 5;
            }
            PlayerRank rr = rank(r.getInt("rank"));
            int rc = SystemTheme.rank(rr);
            if (me) Shapes.box(bx + 2, ry, bw - 4, rowH - 1).radius(4).fill(Gfx.alpha(SystemTheme.ACCENT, 0.18f)).border(0.6f, Gfx.alpha(SystemTheme.ACCENT, 0.7f)).draw(g);
            int pc = place == 1 ? GOLD : place <= 3 ? 0xFFD9E2EC : SystemTheme.TEXT_DIM;
            SysFont.draw(g, String.valueOf(place), bx + 12, SysFont.centerY(ry, rowH - 1, 5f), 5f, pc, true, 1f);
            Shapes.circle(bx + 19, ry + (rowH - 1) / 2f, 3.6f).fill(Gfx.alpha(rc, 0.3f)).border(0.6f, rc).draw(g);
            SysFont.draw(g, rr.name(), bx + 19, SysFont.centerY(ry, rowH - 1, 3.8f), 3.8f, Gfx.mix(rc, 0xFFFFFFFF, 0.5f), true, 0.5f);
            String m = gold(r.getLong("merit"));
            float mw = SysFont.width(m, 4.8f, true);
            String name = SysFont.fit(r.getString("name"), 4.8f, me || r.getBoolean("player"), bw - 34 - mw);
            SysFont.text(g, name, bx + 26, SysFont.centerY(ry, rowH - 1, 4.8f), 4.8f, me ? SystemTheme.TEXT : r.getBoolean("player") ? SystemTheme.ACCENT_BRIGHT : SystemTheme.TEXT_SOFT);
            SysFont.draw(g, m, bx + bw - 6, SysFont.centerY(ry, rowH - 1, 4.8f), 4.8f, me ? SystemTheme.ACCENT : SystemTheme.TEXT_SOFT, true, 1f);
            ry += rowH;
        }
        String foot = ui("standing.place", d.getInt("place"), d.getInt("hunters")).getString();
        SysFont.draw(g, foot, bx + bw / 2f, y + ch - 10, 4.4f, SystemTheme.TEXT_DIM, false, 0.5f);
    }

    // ------------------------------------------------------------------ hands for hire

    private static Component hireUi(String key, Object... args) {
        return Component.translatable("ui.beyondsolo.association.hire." + key, args);
    }

    private void hire(GuiGraphics g, float x, float y, float cw, float ch, int mouseX, int mouseY) {
        CompoundTag d = data().getCompound("hire");
        int hunters = d.getInt("hunters"), maxHunters = d.getInt("maxHunters");
        boolean porter = d.getBoolean("porter");
        int minutes = d.getInt("minutes");
        SysFont.spaced(g, hireUi("title").getString(), x, y, 5f, SystemTheme.TEXT, true, 1.2f, 0f);
        String held = hireUi("held", hunters, maxHunters).getString();
        float hw = SysFont.width(held, 4.8f, true);
        SysFont.draw(g, held, x + cw, y, 4.8f, hunters >= maxHunters ? AMBER : SystemTheme.TEXT_SOFT, true, 1f);
        Shapes.icon(Icons.PACKAGE, x + cw - hw - 9, y + 2.4f, 6).fill(porter ? SAND : SystemTheme.TEXT_DIM).draw(g);
        float ty = para(g, hireUi("desc", minutes), x, y + 10, cw, 4.6f, SystemTheme.TEXT_SOFT) + 4;

        // who is looking for work today
        ListTag offers = d.getList("offers", Tag.TAG_COMPOUND);
        int cols = Math.max(6, offers.size());
        float tileW = (cw - (cols - 1) * 4) / cols, tileH = 62;
        long gold = EconomyClient.gold();
        PlayerRank mine = rank(data().getInt("rank"));
        if (offers.isEmpty()) {
            Ui.card(g, x, ty, cw, 30, 8, SAND, 0.06f);
            paraMid(g, hireUi("none"), x + 12, ty + 15, cw - 24, 4.6f, SystemTheme.TEXT_SOFT);
        }
        for (int i = 0; i < offers.size(); i++) {
            CompoundTag o = offers.getCompound(i);
            float k = Anim.outCubic(Anim.window(Anim.since(openedAt), 0.2f + i * 0.05f, 0.5f + i * 0.05f));
            float o2 = Gfx.fade(k);
            offer(g, o, x + i * (tileW + 4), ty + (1 - k) * 8, tileW, tileH, mouseX, mouseY, porter, hunters >= maxHunters, maxHunters, mine, gold, minutes);
            Gfx.setAlpha(o2);
        }

        // the hands on the job
        float hy = ty + tileH + 8;
        SysFont.spaced(g, hireUi("yours").getString(), x, hy, 4.6f, SystemTheme.TEXT_DIM, true, 1.2f, 0f);
        hy += 9;
        ListTag hired = d.getList("hired", Tag.TAG_COMPOUND);
        float bottom = y + ch - 12;
        if (hired.isEmpty()) SysFont.text(g, hireUi("nobody").getString(), x, hy, 4.6f, SystemTheme.TEXT_DIM);
        for (int i = 0; i < hired.size() && hy + 24 <= bottom; i++) {
            hand(g, hired.getCompound(i), x, hy, cw, 24, mouseX, mouseY);
            hy += 27;
        }
        // the hunter's record against the other parties
        String record = hireUi("record", d.getInt("raceWins"), d.getInt("raceLosses"), d.getInt("trespasses")).getString();
        Shapes.icon(Icons.RACE, x + 3, bottom + 4.5f, 6).fill(SystemTheme.TEXT_DIM).draw(g);
        SysFont.text(g, SysFont.fit(record, 4.4f, false, cw - 10), x + 9, bottom + 2.5f, 4.4f, SystemTheme.TEXT_DIM);
    }

    /** One hand looking for work, as a tile: who, what they do, what they ask. A click hires them. */
    private void offer(GuiGraphics g, CompoundTag o, float x, float y, float w, float h, int mouseX, int mouseY, boolean porterHired,
                       boolean huntersFull, int maxHunters, PlayerRank mine, long gold, int minutes) {
        boolean isPorter = o.getInt("kind") == 0;
        PlayerRank r = rank(o.getInt("rank"));
        PlayerClass cls = PlayerClass.values()[Mth.clamp(o.getInt("class"), 0, PlayerClass.values().length - 1)];
        long fee = o.getLong("fee");
        String name = o.getString("name");
        Component refusal = isPorter && porterHired ? hireUi("hint.porter_already")
                : !isPorter && huntersFull ? hireUi("hint.hunters_full", maxHunters)
                : !isPorter && r.tier() > mine.tier() ? hireUi("hint.rank", r.displayName())
                : gold < fee ? ui("hint.gold", gold(fee)) : null;
        boolean enabled = refusal == null;
        boolean hover = Ui.inside(mouseX, mouseY, x, y, w, h);
        int c = isPorter ? SAND : SystemTheme.rank(r);
        Shapes.box(x, y, w, h).radius(7).fillV(Gfx.alpha(c, enabled && hover ? 0.3f : enabled ? 0.13f : 0.05f), Gfx.alpha(Gfx.shade(c, -0.5f), enabled ? 0.12f : 0.03f))
                .border(hover && enabled ? 1f : 0.6f, Gfx.alpha(c, enabled ? (hover ? 1f : 0.55f) : 0.25f)).glow(hover && enabled ? 5 : 0, Gfx.alpha(c, 0.35f)).draw(g);
        float old = Gfx.fade(enabled ? 1f : 0.55f);
        float cx = x + w / 2f;
        // their mark: a porter's pack, or the hunter's class, in a ring of their rank
        Shapes.circle(cx, y + 13, 8.5f).fillV(Gfx.alpha(c, 0.32f), Gfx.alpha(c, 0.08f)).border(0.7f, Gfx.alpha(c, 0.85f)).draw(g);
        Shapes.icon(isPorter ? Icons.PACKAGE : Icons.of(cls), cx, y + 13, 10).fill(Gfx.mix(c, 0xFFFFFFFF, 0.6f)).draw(g);
        Ui.chip(g, cx + 4, y + 17, 7, r.name(), 4.2f, SystemTheme.rank(r), true);
        SysFont.draw(g, SysFont.fit(name, 4.6f, true, w - 4), cx, y + 26, 4.6f, SystemTheme.TEXT, true, 0.5f);
        String what = isPorter ? hireUi("porter").getString() : cls.displayName().getString();
        SysFont.draw(g, SysFont.fit(what, 4f, false, w - 4), cx, y + 33.5f, 4f, isPorter ? SAND : SystemTheme.TEXT_SOFT, false, 0.5f);
        if (!isPorter) SysFont.draw(g, hireUi("level", o.getInt("level")).getString(), cx, y + 40, 4f, SystemTheme.TEXT_DIM, false, 0.5f);
        else SysFont.draw(g, hireUi("slots", o.getInt("slots")).getString(), cx, y + 40, 4f, SystemTheme.TEXT_DIM, false, 0.5f);
        String fw = gold(fee);
        boolean afford = gold >= fee;
        // under contract with Iron Bastion: less, the full fee struck through over it
        boolean cut = o.contains("was");
        int fc = !afford ? SystemTheme.DANGER : cut ? SystemTheme.GOOD : GOLD;
        Shapes.icon(Icons.COINS, cx - SysFont.width(fw, 4.8f, true) / 2f - 4, y + 52, 5.5f).fill(fc).draw(g);
        SysFont.draw(g, fw, cx + 3, y + 49.5f, 4.8f, fc, true, 0.5f);
        if (cut) {
            String was = gold(o.getLong("was"));
            float ww = SysFont.width(was, 3.8f, false);
            SysFont.draw(g, was, cx, y + 44.5f, 3.8f, SystemTheme.TEXT_DIM, false, 0.5f);
            Shapes.box(cx - ww / 2f - 0.5f, y + 46.4f, ww + 1, 0.5f).fill(SystemTheme.TEXT_DIM).draw(g);
        }
        Gfx.setAlpha(old);
        if (hover) hint = refusal != null ? refusal : isPorter ? hireUi("hint.porter", name, minutes, gold(fee))
                : hireUi("hint.hunter", name, cls.displayName(), o.getInt("level"), minutes, gold(fee));
        if (enabled) {
            CompoundTag args = new CompoundTag();
            args.putUUID("offer", o.getUUID("id"));
            hits.add(new Hit(x, y, w, h, () -> AssociationClient.send(AssociationActionPayload.HIRE, args)));
        }
    }

    /** A hand on the job: who, how long they have left, what they carry or have felled; let them go. */
    private void hand(GuiGraphics g, CompoundTag hd, float x, float y, float cw, float rh, int mouseX, int mouseY) {
        boolean isPorter = hd.getInt("kind") == 0;
        PlayerRank r = rank(hd.getInt("rank"));
        PlayerClass cls = PlayerClass.values()[Mth.clamp(hd.getInt("class"), 0, PlayerClass.values().length - 1)];
        int c = isPorter ? SAND : FREELANCE;
        Shapes.box(x, y, cw, rh).radius(6).fillV(Gfx.alpha(c, 0.12f), Gfx.alpha(Gfx.shade(c, -0.5f), 0.06f)).border(0.6f, Gfx.alpha(c, 0.45f)).draw(g);
        Shapes.box(x + 1.2f, y + 3, 1.6f, rh - 6).radius(0.8f).fill(c).draw(g);
        Shapes.circle(x + 13, y + rh / 2f, 7).fillV(Gfx.alpha(c, 0.3f), Gfx.alpha(c, 0.08f)).border(0.6f, Gfx.alpha(c, 0.8f)).draw(g);
        Shapes.icon(isPorter ? Icons.PACKAGE : Icons.of(cls), x + 13, y + rh / 2f, 8.5f).fill(Gfx.mix(c, 0xFFFFFFFF, 0.6f)).draw(g);
        float tx = x + 25;
        float bw = Math.max(40, SysFont.spacedWidth(hireUi("dismiss").getString(), 5.2f, true, 0.8f) + 22);
        float right = x + cw - bw - 10;
        String clockText = hd.getBoolean("done") ? hireUi("done").getString() : clock(hd.getLong("left"));
        float clw = SysFont.width(clockText, 6f, true);
        String name = SysFont.fit(hd.getString("name"), 5.4f, true, right - clw - 10 - tx);
        SysFont.text(g, name, tx, y + 4, 5.4f, SystemTheme.TEXT);
        Ui.chip(g, tx + SysFont.width(name, 5.4f, true) + 4, y + 3, 7.5f, r.name(), 4.2f, SystemTheme.rank(r), true);
        String line = isPorter ? hireUi("carrying", hd.getInt("carried"), hd.getInt("slots")).getString()
                : hireUi("hunter_line", cls.displayName(), hd.getInt("level"), hd.getInt("kills")).getString();
        SysFont.text(g, SysFont.fit(line, 4.5f, false, right - tx - 4), tx, y + 14, 4.5f, SystemTheme.TEXT_SOFT);
        SysFont.draw(g, clockText, right, SysFont.centerY(y, rh, 6f), 6f, hd.getBoolean("done") ? SystemTheme.GOOD
                : hd.getLong("left") < 20 * 120 ? AMBER : SystemTheme.TEXT, true, 1f);
        CompoundTag args = new CompoundTag();
        args.putUUID("hire", hd.getUUID("id"));
        button(g, x + cw - bw - 5, y + (rh - 13) / 2f, bw, 13, hireUi("dismiss").getString(), Icons.X, SystemTheme.DANGER, mouseX, mouseY,
                isPorter ? hireUi("hint.dismiss_porter", hd.getString("name")) : hireUi("hint.dismiss", hd.getString("name")),
                () -> AssociationClient.send(AssociationActionPayload.DISMISS, args));
    }

    // ------------------------------------------------------------------ the great guilds

    private static Component guildUi(String key, Object... args) {
        return Component.translatable("ui.beyondsolo.scouting." + key, args);
    }

    /** The great guilds: the hunter's contract (or who would come for them), then the offers on the table. */
    private void guilds(GuiGraphics g, float x, float y, float cw, float ch, int mouseX, int mouseY) {
        CompoundTag d = data().getCompound("guilds");
        SysFont.spaced(g, guildUi("office.title").getString(), x, y, 5f, SystemTheme.TEXT, true, 1.2f, 0f);
        String record = guildUi("office.record", d.getInt("signed"), d.getInt("declined")).getString();
        SysFont.draw(g, record, x + cw, y, 4.6f, SystemTheme.TEXT_SOFT, true, 1f);

        // the contract
        float cy = y + 10;
        CompoundTag c = d.getCompound("contract");
        float cardH = 44;
        if (c.contains("guild")) {
            RivalGuild guild = RivalGuild.byIndex(c.getInt("guild"));
            int gc = guild.color;
            Ui.card(g, x, cy, cw, cardH, 8, gc, 0.12f);
            Shapes.box(x + 1.2f, cy + 4, 1.6f, cardH - 8).radius(0.8f).fill(gc).draw(g);
            Shapes.circle(x + 15, cy + 15, 8.5f).fillV(Gfx.alpha(gc, 0.32f), Gfx.alpha(gc, 0.08f)).border(0.7f, Gfx.alpha(gc, 0.9f)).glow(3, Gfx.alpha(gc, 0.35f)).draw(g);
            Shapes.icon(Icons.GUILDS[guild.ordinal()], x + 15, cy + 15, 10).fill(Gfx.mix(gc, 0xFFFFFFFF, 0.6f)).draw(g);
            float tx = x + 29;
            String name = guild.displayName().getString();
            SysFont.text(g, name, tx, cy + 5, 6f, Gfx.mix(gc, 0xFFFFFFFF, 0.35f));
            Ui.chip(g, tx + SysFont.width(name, 6f, true) + 5, cy + 4.5f, 7.5f, guildUi("office.contract").getString(), 4.2f, gc, true);
            long owe = c.getLong("owe");
            float bw = Math.max(52, SysFont.spacedWidth(guildUi("office.walk_out").getString(), 5.2f, true, 0.8f) + 22);
            float right = x + cw - bw - 10;
            String terms = guildUi("office.terms", gold(c.getLong("bonus")), Math.round(c.getFloat("cut") * 100), gold(c.getLong("paid"))).getString();
            SysFont.text(g, SysFont.fit(terms, 4.5f, false, right - tx), tx, cy + 15, 4.5f, SystemTheme.TEXT_SOFT);
            SysFont.text(g, SysFont.fit(AssociationClient.text(c, "perk").getString(), 4.5f, false, right - tx), tx, cy + 22.5f, 4.5f, Gfx.mix(gc, 0xFFFFFFFF, 0.5f));
            long left = c.getLong("daysLeft"), days = Math.max(1, c.getLong("days"));
            Ui.bar(g, tx, cy + 32, right - tx, 3f, (float) left / days, Gfx.shade(gc, -0.3f), gc);
            SysFont.draw(g, guildUi("office.days_left", left).getString(), right, cy + 35.5f, 4.2f, SystemTheme.TEXT_DIM, false, 1f);
            button(g, x + cw - bw - 5, cy + (cardH - 14) / 2f, bw, 14, guildUi("office.walk_out").getString(), Icons.LEAVE, SystemTheme.DANGER, mouseX, mouseY,
                    EconomyClient.gold() < owe ? guildUi("office.hint.owe", gold(owe)) : guildUi("office.hint.walk_out", gold(owe), guild.displayName()),
                    EconomyClient.gold() < owe ? null : () -> AssociationClient.send(AssociationActionPayload.BREAK_CONTRACT));
        } else {
            Ui.card(g, x, cy, cw, cardH, 8, STEEL, 0.06f);
            Shapes.icon(Icons.CROWN, x + 14, cy + 15, 11).fill(Gfx.alpha(STEEL, 0.7f)).draw(g);
            SysFont.text(g, guildUi("office.no_contract").getString(), x + 26, cy + 5, 5.4f, SystemTheme.TEXT);
            SysFont.text(g, SysFont.fit(guildUi("office.who_comes").getString(), 4.4f, false, cw - 34), x + 26, cy + 14, 4.4f, SystemTheme.TEXT_SOFT);
            // who comes from which rank
            ListTag gl = d.getList("guilds", Tag.TAG_COMPOUND);
            float gx = x + 26;
            PlayerRank mine = rank(d.getInt("rank"));
            for (int i = 0; i < gl.size(); i++) {
                CompoundTag gt = gl.getCompound(i);
                RivalGuild guild = RivalGuild.byIndex(gt.getInt("guild"));
                PlayerRank from = rank(gt.getInt("from"));
                boolean wants = mine.tier() >= from.tier();
                String label = guild.displayName().getString() + " · " + from.name() + "+";
                float lw = SysFont.width(label, 4.2f, true) + 14;
                if (gx + lw > x + cw - 4) break;
                Shapes.pill(gx, cy + 25, lw, 10).fill(Gfx.alpha(guild.color, wants ? 0.22f : 0.07f)).border(0.6f, Gfx.alpha(guild.color, wants ? 0.8f : 0.35f)).draw(g);
                Shapes.icon(Icons.GUILDS[guild.ordinal()], gx + 6, cy + 30, 6).fill(Gfx.mix(guild.color, 0xFFFFFFFF, wants ? 0.5f : 0.1f)).draw(g);
                SysFont.text(g, label, gx + 11, SysFont.centerY(cy + 25, 10, 4.2f), 4.2f, wants ? SystemTheme.TEXT : SystemTheme.TEXT_DIM);
                gx += lw + 4;
            }
        }

        // the offers on the table
        float oy = cy + cardH + 7;
        SysFont.spaced(g, guildUi("office.offers").getString(), x, oy, 4.6f, SystemTheme.TEXT_DIM, true, 1.2f, 0f);
        int coming = d.getInt("coming");
        if (coming > 0) SysFont.draw(g, guildUi("office.coming", coming).getString(), x + cw, oy, 4.4f, SystemTheme.TEXT_SOFT, false, 1f);
        oy += 9;
        ListTag offers = d.getList("offers", Tag.TAG_COMPOUND);
        float rowH = 30, gap = 3;
        int room = (int) ((y + ch - oy) / (rowH + gap));
        if (offers.isEmpty()) {
            Ui.card(g, x, oy, cw, 28, 8, STEEL, 0.05f);
            paraMid(g, guildUi(coming > 0 ? "office.none_yet" : "office.none"), x + 12, oy + 14, cw - 24, 4.6f, SystemTheme.TEXT_SOFT);
            return;
        }
        long best = 0;
        for (int i = 0; i < offers.size(); i++) best = Math.max(best, offers.getCompound(i).getLong("bonus"));
        boolean contracted = c.contains("guild");
        for (int i = 0; i < Math.min(room, offers.size()); i++) {
            float k = Anim.outCubic(Anim.window(Anim.since(openedAt), 0.25f + i * 0.05f, 0.55f + i * 0.05f));
            float o = Gfx.fade(k);
            CompoundTag of = offers.getCompound(i);
            guildOffer(g, of, x + (1 - k) * 10, oy, cw, rowH, mouseX, mouseY, of.getLong("bonus") == best && offers.size() > 1, contracted ? c : null);
            Gfx.setAlpha(o);
            oy += rowH + gap;
        }
    }

    /** One guild's offer on the table: who, what it pays and asks, the clock on it, and the three answers. */
    private void guildOffer(GuiGraphics g, CompoundTag o, float x, float y, float cw, float rh, int mouseX, int mouseY, boolean best, CompoundTag contract) {
        RivalGuild guild = RivalGuild.byIndex(o.getInt("guild"));
        int gc = guild.color;
        boolean last = o.getBoolean("last");
        Shapes.box(x, y, cw, rh).radius(6).fillV(Gfx.alpha(gc, best ? 0.2f : 0.11f), Gfx.alpha(Gfx.shade(gc, -0.5f), 0.06f))
                .border(best ? 0.9f : 0.6f, Gfx.alpha(best ? GOLD : gc, best ? 0.8f : 0.4f)).glow(best ? 3 : 0, Gfx.alpha(GOLD, 0.25f)).draw(g);
        Shapes.circle(x + 13, y + rh / 2f, 8).fillV(Gfx.alpha(gc, 0.3f), Gfx.alpha(gc, 0.08f)).border(0.6f, Gfx.alpha(gc, 0.85f)).draw(g);
        Shapes.icon(Icons.GUILDS[guild.ordinal()], x + 13, y + rh / 2f, 9.5f).fill(Gfx.mix(gc, 0xFFFFFFFF, 0.6f)).draw(g);
        float tx = x + 26;
        float bw = 30, bh = 12;
        float buttons = 3 * bw + 2 * 3;
        float bx = x + cw - buttons - 5;
        String amount = gold(o.getLong("bonus"));
        float aw = SysFont.width(amount, 7f, true);
        float ax = bx - 8 - aw;
        String name = SysFont.fit(guild.displayName().getString(), 5.2f, true, ax - tx - 40);
        SysFont.text(g, name, tx, y + 4, 5.2f, Gfx.mix(gc, 0xFFFFFFFF, 0.35f));
        float tagX = tx + SysFont.width(name, 5.2f, true) + 4;
        if (o.getBoolean("renewal")) tagX += Ui.chip(g, tagX, y + 3.2f, 7, guildUi("renewal").getString(), 4f, gc, true) + 3;
        if (last) Ui.chip(g, tagX, y + 3.2f, 7, guildUi("final").getString(), 4f, AMBER, true);
        String terms = guildUi("office.offer_terms", Math.round(o.getFloat("cut") * 100), o.getInt("days"), AssociationClient.text(o, "perk")).getString();
        SysFont.text(g, SysFont.fit(terms, 4.3f, false, ax - tx - 4), tx, y + 12.5f, 4.3f, SystemTheme.TEXT_SOFT);
        String lapse = guildUi("lapses", clock(o.getLong("left"))).getString();
        SysFont.text(g, SysFont.fit(lapse, 4f, false, ax - tx - 4), tx, y + 20.5f, 4f, o.getLong("left") < 20 * 120 ? AMBER : SystemTheme.TEXT_DIM);
        Shapes.icon(Icons.COINS, ax - 5, y + rh / 2f, 7).fill(GOLD).draw(g);
        SysFont.draw(g, amount, ax, SysFont.centerY(y, rh, 7f), 7f, best ? Gfx.mix(GOLD, 0xFFFFFFFF, 0.3f) : GOLD, true, 0f);
        CompoundTag args = new CompoundTag();
        args.putUUID("offer", o.getUUID("id"));
        float by = y + (rh - bh) / 2f;
        Component signTip = contract != null ? guildUi("hint.sign_buyout", gold(o.getLong("bonus")), RivalGuild.byIndex(contract.getInt("guild")).displayName())
                : guildUi("hint.sign", gold(o.getLong("bonus")), o.getInt("days"));
        button(g, bx, by, bw, bh, guildUi("office.sign").getString(), null, GOLD, mouseX, mouseY, signTip,
                () -> AssociationClient.send(AssociationActionPayload.SIGN, args));
        button(g, bx + bw + 3, by, bw, bh, guildUi("office.more").getString(), null, STEEL, mouseX, mouseY,
                last ? guildUi("hint.final") : guildUi("hint.hold_out"), last ? null : () -> AssociationClient.send(AssociationActionPayload.HOLD_OUT, args));
        button(g, bx + 2 * (bw + 3), by, bw, bh, guildUi("office.no").getString(), null, SystemTheme.DANGER, mouseX, mouseY,
                guildUi("hint.decline"), () -> AssociationClient.send(AssociationActionPayload.DECLINE, args));
    }
}
