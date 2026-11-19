package com.beyondsolo.client.association;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.economy.EconomyClient;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.network.AssociationActionPayload;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.UUID;

/**
 * The gate broker's window: the broker on the left with how many leads they will still sell today; on the right the
 * whereabouts of a gate of any rank (one the hunter has not heard of, or one about to tear open) and scouting
 * reports on the gates the hunter knows of.
 */
public class BrokerScreen extends AssociationWindow {
    private static final String[] TABS = {"whereabouts", "scouting"};
    private static final ResourceLocation[] TAB_ICONS = {Icons.COMPASS, Icons.EYE_OFF};
    /** How many things a broker may say when you walk up. */
    private static final int QUOTES = 5;

    private int tab;
    private double tabAt = Anim.now();
    private UUID picked;

    BrokerScreen() {
        super(Component.translatable("ui.beyondsolo.association.broker.title"), 440, 258, 132);
    }

    @Override
    CompoundTag data() {
        return AssociationClient.broker();
    }

    @Override
    int accent() {
        return VIOLET;
    }

    @Override
    Component role() {
        return ui("role");
    }

    private static Component ui(String key, Object... args) {
        return Component.translatable("ui.beyondsolo.association.broker." + key, args);
    }

    // ------------------------------------------------------------------ left: what the broker says

    @Override
    void under(GuiGraphics g, float x, float y, float lw, float bottom, float t) {
        CompoundTag d = data();
        Ui.divider(g, x, y, lw, Gfx.alpha(VIOLET, 0.4f));
        int quote = Math.floorMod(d.getInt("entity") * 31 + d.getInt("tipsLeft"), QUOTES);
        Shapes.icon(Icons.CHAT, x + 3, y + 9, 6).fill(Gfx.alpha(VIOLET, 0.8f)).draw(g);
        para(g, ui("quote." + quote), x + 9, y + 6, lw - 9, 4.5f, SystemTheme.TEXT_SOFT);
        // leads left today
        int left = d.getInt("tipsLeft"), per = Math.max(1, d.getInt("tipsPerDay"));
        float ly = bottom - 44;
        SysFont.spaced(g, ui("leads").getString(), x, ly, 4.4f, SystemTheme.TEXT_DIM, true, 1.1f, 0f);
        SysFont.draw(g, left + " / " + per, x + lw, ly, 4.8f, left > 0 ? Gfx.mix(VIOLET, 0xFFFFFFFF, 0.3f) : AMBER, true, 1f);
        if (per <= 12) {
            float pw = (lw - (per - 1) * 2f) / per;
            for (int i = 0; i < per; i++) {
                boolean on = i < left;
                Shapes.pill(x + i * (pw + 2), ly + 8, pw, 3).fill(on ? VIOLET : Gfx.alpha(VIOLET, 0.15f)).glow(on ? 2 : 0, Gfx.alpha(VIOLET, 0.4f)).draw(g);
            }
        } else {
            Ui.bar(g, x, ly + 8, lw, 3, (float) left / per, Gfx.shade(VIOLET, -0.3f), VIOLET);
        }
        // the hunter's purse, and the price they get
        float gy = ly + 18;
        Shapes.icon(Icons.COINS, x + 3, gy + 3.5f, 6).fill(GOLD).draw(g);
        SysFont.text(g, gold(EconomyClient.gold()), x + 9, gy + 0.5f, 5.2f, GOLD);
        if (d.getBoolean("discount")) {
            String disc = ui("discount").getString();
            float dw = SysFont.width(disc, 4.1f, true) + 7.5f * 0.9f;
            tag(g, x + lw - dw, gy, disc, GOLD, true);
        }
    }

    // ------------------------------------------------------------------ right: the tabs

    @Override
    void content(GuiGraphics g, float x, float y, float cw, float ch, int mouseX, int mouseY, float t) {
        float tw = (cw - 4) / 2f;
        for (int i = 0; i < TABS.length; i++) {
            float tx = x + i * (tw + 4);
            boolean sel = i == tab;
            boolean hover = Ui.inside(mouseX, mouseY, tx, y, tw, 15);
            Shapes.pill(tx, y, tw, 15).fillV(Gfx.alpha(VIOLET, sel ? 0.34f : hover ? 0.16f : 0.06f), Gfx.alpha(Gfx.shade(VIOLET, -0.4f), sel ? 0.24f : 0.06f))
                    .border(sel ? 0.9f : 0.6f, Gfx.alpha(VIOLET, sel ? 0.95f : 0.35f)).glow(sel ? 4 : 0, Gfx.alpha(VIOLET, 0.3f)).draw(g);
            String label = ui("tab." + TABS[i]).getString();
            float lw = SysFont.spacedWidth(label, 4.8f, true, 1f) + 9;
            float lx = tx + (tw - lw) / 2f;
            Shapes.icon(TAB_ICONS[i], lx + 3, y + 7.5f, 6).fill(sel ? SystemTheme.TEXT : SystemTheme.TEXT_SOFT).draw(g);
            SysFont.spaced(g, label, lx + 9, SysFont.centerY(y, 15, 4.8f), 4.8f, sel ? SystemTheme.TEXT : SystemTheme.TEXT_SOFT, true, 1f, 0f);
            if (!sel) {
                int pick = i;
                hits.add(new Hit(tx, y, tw, 15, () -> {
                    tab = pick;
                    tabAt = Anim.now();
                }));
            }
        }
        float k = Anim.outCubic(Anim.progress(tabAt, 0.25f));
        float o = Gfx.fade(k);
        float py = y + 21 + (1 - k) * 4, ph = ch - 21;
        if (tab == 1) scouting(g, x, py, cw, ph, mouseX, mouseY);
        else whereabouts(g, x, py, cw, ph, mouseX, mouseY);
        Gfx.setAlpha(o);
    }

    // ------------------------------------------------------------------ whereabouts

    private void whereabouts(GuiGraphics g, float x, float y, float cw, float ch, int mouseX, int mouseY) {
        CompoundTag d = data();
        SysFont.spaced(g, ui("whereabouts.title").getString(), x, y, 5f, SystemTheme.TEXT, true, 1.2f, 0f);
        int left = d.getInt("tipsLeft");
        SysFont.draw(g, ui("whereabouts.left", left, d.getInt("tipsPerDay")).getString(), x + cw, y, 4.8f, left > 0 ? SystemTheme.TEXT_SOFT : AMBER, true, 1f);
        float ty = para(g, ui("whereabouts.desc"), x, y + 10, cw, 4.6f, SystemTheme.TEXT_SOFT) + 4;
        ListTag tips = d.getList("tips", Tag.TAG_COMPOUND);
        int n = Math.max(1, tips.size());
        float tileW = (cw - (n - 1) * 4) / n, tileH = 64;
        long purse = EconomyClient.gold();
        for (int i = 0; i < tips.size(); i++) {
            CompoundTag c = tips.getCompound(i);
            PlayerRank r = rank(c.getInt("rank"));
            long price = c.getLong("price");
            boolean known = c.getBoolean("known");
            boolean afford = purse >= price;
            boolean enabled = left > 0 && afford;
            float tx = x + i * (tileW + 4);
            boolean hover = Ui.inside(mouseX, mouseY, tx, ty, tileW, tileH);
            int rc = SystemTheme.rank(r);
            Shapes.box(tx, ty, tileW, tileH).radius(7).fillV(Gfx.alpha(rc, enabled && hover ? 0.3f : 0.13f), Gfx.alpha(Gfx.shade(rc, -0.5f), 0.1f))
                    .border(hover && enabled ? 1f : 0.6f, Gfx.alpha(rc, hover ? 1f : 0.5f)).glow(hover && enabled ? 5 : 0, Gfx.alpha(rc, 0.35f)).draw(g);
            float old = Gfx.fade(enabled ? 1f : 0.6f);
            Ui.rankEmblem(g, r, tx + tileW / 2f, ty + 15, 9, hover && enabled ? 0.6f : 0.15f);
            SysFont.draw(g, Component.translatable("ui.beyondsolo.association.rank_gate", r.displayName()).getString(), tx + tileW / 2f, ty + 30, 4.6f,
                    SystemTheme.TEXT, true, 0.5f);
            // whether they know of one, or only of one about to open
            String what = ui(known ? "whereabouts.known" : "whereabouts.rumour").getString();
            SysFont.spaced(g, SysFont.fit(what, 3.9f, true, tileW - 6), tx + tileW / 2f, ty + 39, 3.9f, known ? Gfx.mix(VIOLET, 0xFFFFFFFF, 0.35f) : AMBER, true, 0.9f, 0.5f);
            String p = gold(price);
            float pw = SysFont.width(p, 4.8f, true);
            Shapes.icon(Icons.COINS, tx + tileW / 2f - pw / 2f - 4, ty + 53, 5.5f).fill(afford ? GOLD : SystemTheme.DANGER).draw(g);
            SysFont.draw(g, p, tx + tileW / 2f + 3, ty + 50.5f, 4.8f, afford ? GOLD : SystemTheme.DANGER, true, 0.5f);
            Gfx.setAlpha(old);
            if (hover) hint = left <= 0 ? ui("hint.none_left") : !afford ? Component.translatable("ui.beyondsolo.association.hint.gold", gold(price))
                    : ui(known ? "hint.tip_known" : "hint.tip_fresh", r.displayName(), gold(price));
            if (enabled) {
                CompoundTag args = new CompoundTag();
                args.putInt("rank", r.ordinal());
                hits.add(new Hit(tx, ty, tileW, tileH, () -> AssociationClient.send(AssociationActionPayload.TIP, args)));
            }
        }
        // how a lead works
        float fy = ty + tileH + 8;
        int noteLines = SysFont.wrap(ui("whereabouts.note"), 4.6f, false, cw - 34).size();
        float fh = Math.min(Math.max(26, noteLines * 4.6f * SysFont.LINE + 14), y + ch - fy);
        if (fh < 20) return;
        Ui.card(g, x, fy, cw, fh, 8, VIOLET, 0.07f);
        Shapes.icon(Icons.PIN, x + 13, fy + fh / 2f, 10).fill(Gfx.alpha(VIOLET, 0.85f)).glow(3, Gfx.alpha(VIOLET, 0.4f)).draw(g);
        paraMid(g, ui("whereabouts.note"), x + 26, fy + fh / 2f, cw - 34, 4.6f, SystemTheme.TEXT_SOFT);
    }

    // ------------------------------------------------------------------ scouting

    private void scouting(GuiGraphics g, float x, float y, float cw, float ch, int mouseX, int mouseY) {
        CompoundTag d = data();
        SysFont.spaced(g, ui("scouting.title").getString(), x, y, 5f, SystemTheme.TEXT, true, 1.2f, 0f);
        SysFont.draw(g, ui("scouting.range", d.getInt("scoutRange")).getString(), x + cw, y, 4.6f, SystemTheme.TEXT_DIM, false, 1f);
        ListTag gates = d.getList("gates", Tag.TAG_COMPOUND);
        float ly = y + 10;
        if (gates.isEmpty()) {
            Ui.card(g, x, ly + 2, cw, 44, 8, VIOLET, 0.06f);
            Shapes.icon(Icons.EYE_OFF, x + 14, ly + 24, 12).fill(Gfx.alpha(VIOLET, 0.7f)).draw(g);
            para(g, ui("scouting.empty", d.getInt("scoutRange")), x + 28, ly + 10, cw - 36, 4.8f, SystemTheme.TEXT_SOFT);
            return;
        }
        CompoundTag sel = null;
        for (int i = 0; i < gates.size(); i++) if (gates.getCompound(i).getUUID("gate").equals(picked)) sel = gates.getCompound(i);
        if (sel == null) {
            sel = gates.getCompound(0);
            picked = sel.getUUID("gate");
        }
        float listW = Math.round(cw * 0.45f);
        float rowH = 24, gap = 3;
        for (int i = 0; i < gates.size() && ly + rowH <= y + ch; i++) {
            CompoundTag c = gates.getCompound(i);
            float k = Anim.outCubic(Anim.window(Anim.since(openedAt), 0.25f + i * 0.05f, 0.55f + i * 0.05f));
            float o = Gfx.fade(k);
            row(g, c, x + (1 - k) * 10, ly, listW, rowH, c == sel, mouseX, mouseY);
            Gfx.setAlpha(o);
            ly += rowH + gap;
        }
        report(g, sel, x + listW + 6, y + 10, cw - listW - 6, ch - 10, mouseX, mouseY);
    }

    /** A gate in the scouting list. */
    private void row(GuiGraphics g, CompoundTag c, float x, float y, float w, float h, boolean sel, int mouseX, int mouseY) {
        PlayerRank r = rank(c.getInt("rank"));
        int rc = SystemTheme.rank(r);
        boolean hover = Ui.inside(mouseX, mouseY, x, y, w, h);
        boolean scouted = c.contains("report");
        int edge = sel ? VIOLET : rc;
        Shapes.box(x, y, w, h).radius(6).fillV(Gfx.alpha(edge, sel ? 0.22f : hover ? 0.12f : 0.06f), Gfx.alpha(Gfx.shade(edge, -0.5f), sel ? 0.14f : 0.04f))
                .border(sel ? 0.9f : 0.5f, Gfx.alpha(edge, sel ? 0.9f : hover ? 0.55f : 0.3f)).glow(sel ? 3 : 0, Gfx.alpha(VIOLET, 0.3f)).draw(g);
        Ui.rankEmblem(g, r, x + 11, y + h / 2f, 6.5f, sel ? 0.5f : 0.15f);
        float tx = x + 21;
        float right = x + w - 5;
        float sw;
        if (scouted) {
            Shapes.icon(Icons.CHECK, right - 3, y + 7.5f, 6).fill(SystemTheme.GOOD).draw(g);
            sw = 8;
        } else {
            String p = gold(c.getLong("price"));
            sw = SysFont.width(p, 4.6f, true) + 8;
            Shapes.icon(Icons.COINS, right - sw + 3, y + 7.5f, 5.5f).fill(GOLD).draw(g);
            SysFont.draw(g, p, right, y + 5, 4.6f, GOLD, true, 1f);
        }
        String realm = SysFont.fit(AssociationClient.text(c, "realm").getString(), 5f, true, right - tx - sw - 3);
        SysFont.text(g, realm, tx, y + 4.5f, 5f, SystemTheme.TEXT);
        String where = Component.translatable("ui.beyondsolo.association.where", c.getInt("dist"), AssociationClient.text(c, "dir")).getString();
        SysFont.text(g, SysFont.fit(where, 4.3f, false, right - tx), tx, y + 14, 4.3f, SystemTheme.TEXT_SOFT);
        if (!sel) {
            UUID id = c.getUUID("gate");
            hits.add(new Hit(x, y, w, h, () -> picked = id));
        }
    }

    /** What waits behind the picked gate, or the offer to find out. */
    private void report(GuiGraphics g, CompoundTag c, float x, float y, float w, float h, int mouseX, int mouseY) {
        PlayerRank r = rank(c.getInt("rank"));
        int rc = SystemTheme.rank(r);
        Ui.card(g, x, y, w, h, 8, VIOLET, 0.1f);
        Ui.rankEmblem(g, r, x + 14, y + 14, 8, 0.3f);
        String realm = SysFont.fit(AssociationClient.text(c, "realm").getString(), 5.6f, true, w - 32);
        SysFont.text(g, realm, x + 27, y + 6, 5.6f, SystemTheme.TEXT);
        String where = Component.translatable("ui.beyondsolo.association.where", c.getInt("dist"), AssociationClient.text(c, "dir")).getString();
        SysFont.text(g, SysFont.fit(where, 4.4f, false, w - 32), x + 27, y + 16, 4.4f, SystemTheme.TEXT_SOFT);
        float cy = y + 30;
        Ui.divider(g, x + 6, cy - 3, w - 12, Gfx.alpha(VIOLET, 0.35f));
        if (!c.contains("report")) {
            // not scouted yet
            float py = para(g, ui("scouting.offer", r.displayName()), x + 8, cy + 3, w - 16, 4.6f, SystemTheme.TEXT_SOFT) + 4;
            if (c.getBoolean("above")) py = para(g, ui("scouting.above"), x + 8, py, w - 16, 4.4f, SystemTheme.DANGER) + 4;
            long price = c.getLong("price");
            boolean afford = EconomyClient.gold() >= price;
            CompoundTag args = new CompoundTag();
            args.putUUID("gate", c.getUUID("gate"));
            float bw = Math.min(w - 16, 92);
            button(g, x + (w - bw) / 2f, Math.max(py + 2, y + h - 24), bw, 16, ui("scout", gold(price)).getString(), Icons.EYE_OFF, VIOLET, mouseX, mouseY,
                    afford ? ui("hint.scout", r.displayName(), gold(price)) : Component.translatable("ui.beyondsolo.association.hint.gold", gold(price)),
                    afford ? () -> AssociationClient.send(AssociationActionPayload.SCOUT, args) : null);
            return;
        }
        CompoundTag rep = c.getCompound("report");
        String hidden = AssociationClient.text(rep, "hidden").getString();
        long left = rep.getLong("left");
        ResourceLocation[] icons = {Icons.SPIRAL, Icons.PATH, Icons.CROWN, Icons.SPARKLE, Icons.MOON, Icons.KEY, Icons.HEXAGON, Icons.HOURGLASS};
        String[] labels = {"realm", "shape", "boss", "element", "weather", "hidden", "rooms", "holds"};
        String[] values = {
                AssociationClient.text(rep, "realm").getString(),
                AssociationClient.text(rep, "shape").getString(),
                AssociationClient.text(rep, "boss").getString(),
                AssociationClient.text(rep, "element").getString(),
                AssociationClient.text(rep, "weather").getString(),
                hidden.isEmpty() ? ui("report.no_hidden").getString() : hidden,
                ui("report.rooms_value", rep.getInt("rooms")).getString(),
                left < 0 ? ui("report.steady").getString() : clock(left)};
        int[] colors = {rc, SystemTheme.TEXT, SystemTheme.DANGER, SystemTheme.ACCENT, 0xFF9FB2C8, GOLD, SystemTheme.TEXT, left < 0 ? SystemTheme.GOOD : AMBER};
        float lh = Math.min(13, (y + h - 6 - cy) / labels.length);
        for (int i = 0; i < labels.length; i++) {
            float ly = cy + i * lh;
            boolean valueDim = i == 5 && hidden.isEmpty();
            Shapes.icon(icons[i], x + 11, ly + lh / 2f, 6).fill(Gfx.mix(colors[i], 0xFFFFFFFF, 0.25f)).draw(g);
            String label = ui("report." + labels[i]).getString();
            SysFont.text(g, SysFont.fit(label, 4.2f, false, 44), x + 18, SysFont.centerY(ly, lh, 4.2f), 4.2f, SystemTheme.TEXT_DIM);
            float vx = x + 64;
            float maxV = x + w - 7 - vx;
            if (i == 2 && rep.getBoolean("ascended")) {
                String asc = ui("report.ascended").getString();
                float aw = SysFont.width(asc, 3.8f, true) + 6.5f * 0.9f;
                maxV -= aw + 3;
                Ui.chip(g, x + w - 7 - aw, ly + (lh - 6.5f) / 2f, 6.5f, asc, 3.8f, SystemTheme.DANGER, true);
            }
            SysFont.text(g, SysFont.fit(values[i], 4.6f, true, maxV), vx, SysFont.centerY(ly, lh, 4.6f), 4.6f, valueDim ? SystemTheme.TEXT_DIM : SystemTheme.TEXT);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab != 1) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        // step the picked gate up or down the list
        ListTag gates = data().getList("gates", Tag.TAG_COMPOUND);
        if (gates.isEmpty()) return false;
        int at = 0;
        for (int i = 0; i < gates.size(); i++) if (gates.getCompound(i).getUUID("gate").equals(picked)) at = i;
        at = Mth.clamp(at - (int) Math.signum(scrollY), 0, gates.size() - 1);
        picked = gates.getCompound(at).getUUID("gate");
        return true;
    }
}
