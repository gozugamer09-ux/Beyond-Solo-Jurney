package com.beyondsolo.client.shadow;

import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.TextField;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.screen.SystemScreen;
import com.beyondsolo.client.screen.SystemTab;
import com.beyondsolo.network.ShadowActionPayload;
import com.beyondsolo.network.ShadowNamePayload;
import com.beyondsolo.shadow.ShadowEntity;
import com.beyondsolo.shadow.ShadowModule;
import com.beyondsolo.shadow.ShadowNames;
import com.beyondsolo.shadow.ShadowPromotions;
import com.beyondsolo.shadow.ShadowRank;
import com.beyondsolo.shadow.ShadowRecord;
import com.beyondsolo.shadow.ShadowService;
import com.beyondsolo.skill.SkillActions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The SHADOWS page of the System window (necromancers only): the army's storage. Left: the army, with filters (all,
 * out, ready for promotion, named) and an order (rank, kills, newest, name), each shadow's rank, level and whether it
 * is out, resting or recovering. Right: the selected shadow turning slowly in ink, its numbers, its promotion (what
 * the next rank takes, and the button), and buttons to call it, send it back, ride it, leave it on guard, or let it go
 * (click twice). The tag on its portrait gives it a name. Under the card: the army's essence and names.
 */
public class ShadowsTab implements SystemTab {
    private static final float LIST_W = 168;
    private static final float ROW_H = 20;
    /** The footer line under both columns (keys on the left, essence and names on the right). */
    private static final float FOOT = 14;

    /** Which shadows the list shows. */
    enum Filter {
        ALL, OUT, READY, NAMED;

        String label() {
            return Component.translatable("shadows.beyondsolo.filter." + name().toLowerCase(Locale.ROOT)).getString();
        }

        boolean passes(ShadowRecord r) {
            return switch (this) {
                case ALL -> true;
                case OUT -> ShadowClient.isActive(r.id()) || r.posted();
                case READY -> r.ready(ShadowClient.top());
                case NAMED -> r.named();
            };
        }
    }

    /** The order of the list. */
    enum Sort {
        RANK, KILLS, NEWEST, NAME;

        String label() {
            return Component.translatable("shadows.beyondsolo.sort." + name().toLowerCase(Locale.ROOT)).getString();
        }

        Comparator<ShadowRecord> order() {
            Comparator<ShadowRecord> byRank = (a, b) -> {
                if (a.boss() != b.boss()) return a.boss() ? -1 : 1;
                if (a.rank() != b.rank()) return b.rank().ordinal() - a.rank().ordinal();
                return b.level() - a.level();
            };
            return switch (this) {
                case RANK -> byRank;
                case KILLS -> Comparator.comparingInt(ShadowRecord::kills).reversed().thenComparing(byRank);
                case NEWEST -> Comparator.comparingLong(ShadowRecord::raisedAt).reversed().thenComparing(byRank);
                case NAME -> Comparator.comparing((ShadowRecord r) -> r.displayName().getString().toLowerCase(Locale.ROOT)).thenComparing(byRank);
            };
        }
    }

    private static @Nullable UUID selected;
    private static Filter filter = Filter.ALL;
    private static Sort sort = Sort.RANK;

    /** Each shadow's rank at the last sync: a climb of the selected one is celebrated on its card. */
    private static final Map<UUID, ShadowRank> SEEN = new HashMap<>();
    private static @Nullable UUID celebrated;
    private static double celebratedAt = -10;
    /** Essence gained since the page last showed it, and when. */
    private static int essenceGain;
    private static double essenceAt = -10;

    /** Typing a name: the field, and the shadow it is for. */
    private static final TextField NAME = new TextField(ShadowNames.MAX_GIVEN, ShadowNames::clean);
    private @Nullable UUID naming;

    private UUID shown;
    private double selectedAt = -10;
    private float scroll;
    private final List<Object[]> rows = new ArrayList<>();
    /** action, record id, x, y, w, h (action -1: the name tag, -2 save the name, -3 stop naming) */
    private final List<Object[]> buttons = new ArrayList<>();
    /** filter ordinal (or -1 for the order), x, y, w, h */
    private final List<float[]> chips = new ArrayList<>();
    private @Nullable UUID releaseArmed;
    private double releaseArmedAt;
    private @Nullable ShadowEntity preview;
    /** A tip to draw over everything, at the end of the frame: text, x, y. */
    private @Nullable String tip;
    private float tipX, tipY;

    @Override
    public Component title() {
        return Component.translatable("tab.beyondsolo.shadows");
    }

    @Override
    public boolean batched() {
        return true;
    }

    @Override
    public boolean isVisible() {
        return ClientSystemData.get().isAwakened() && ShadowClient.canRaise();
    }

    /** A new copy of the army arrived: a climb of the selected shadow, and essence gained, show on the page. */
    static void synced(int essenceBefore, int essenceAfter) {
        boolean first = SEEN.isEmpty();
        for (ShadowRecord r : ShadowClient.shadows()) {
            ShadowRank before = SEEN.put(r.id(), r.rank());
            if (!first && before != null && r.rank().ordinal() > before.ordinal() && r.id().equals(selected)) {
                celebrated = r.id();
                celebratedAt = Anim.now();
            }
        }
        if (essenceAfter > essenceBefore && !first) {
            essenceGain = Anim.since(essenceAt) < 2.5f ? essenceGain + (essenceAfter - essenceBefore) : essenceAfter - essenceBefore;
            essenceAt = Anim.now();
        }
    }

    private static List<ShadowRecord> sorted() {
        List<ShadowRecord> list = new ArrayList<>(ShadowClient.shadows());
        list.sort(sort.order());
        return list;
    }

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        rows.clear();
        buttons.clear();
        chips.clear();
        tip = null;
        List<ShadowRecord> all = sorted();
        long now = ShadowClient.gameTime();

        // ---- header: army size and how many are out
        SysFont.spaced(g, Component.translatable("shadows.beyondsolo.army").getString(), x + 2, y + 1, 6.2f, 0xFFB79CFF, true, 1.3f, 0f);
        String counts = Component.translatable("shadows.beyondsolo.counts", all.size(), ShadowClient.capacity(),
                ShadowClient.used(), ShadowClient.limit()).getString();
        SysFont.draw(g, counts, x + LIST_W, y + 1, 6.2f, SystemTheme.TEXT_SOFT, false, 1f);
        footer(g, x + w, y + h - FOOT + 2);

        if (all.isEmpty()) {
            float cy = y + h / 2f - 20;
            Shapes.icon(Icons.SKULL, x + LIST_W / 2, cy, 26).fill(0x60B79CFF).glow(4, 0x309B7BE0).draw(g);
            List<SysFont.Line> lines = SysFont.wrap(Component.translatable("shadows.beyondsolo.empty"), 7f, false, LIST_W - 10);
            for (int i = 0; i < lines.size(); i++) SysFont.draw(g, lines.get(i), x + LIST_W / 2, cy + 20 + i * 9, SystemTheme.TEXT_SOFT, 0.5f);
            keysHint(g, x, y + h - 12);
            return;
        }

        // ---- filters and order
        filterRow(g, all, x, y + 12, mouseX, mouseY);
        List<ShadowRecord> shownList = new ArrayList<>();
        for (ShadowRecord r : all) if (filter.passes(r)) shownList.add(r);

        ShadowRecord sel = null;
        for (ShadowRecord r : all) if (r.id().equals(selected)) sel = r;
        if (sel == null || !shownList.contains(sel) && !shownList.isEmpty()) {
            sel = shownList.isEmpty() ? all.get(0) : shownList.get(0);
            selected = sel.id();
        }
        if (!sel.id().equals(shown)) {
            shown = sel.id();
            selectedAt = Anim.now();
            if (naming != null && !naming.equals(sel.id())) stopNaming();
        }

        // ---- the list
        float ly = y + 25;
        float lh = h - 25 - FOOT;
        int visible = (int) (lh / ROW_H);
        int maxScroll = Math.max(0, shownList.size() - visible);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        Shapes.flush(g);
        g.enableScissor(x, (int) ly, (int) (x + LIST_W + 2), (int) (ly + lh));
        for (int i = 0; i < shownList.size(); i++) {
            float ry = ly + (i - scroll) * ROW_H;
            if (ry < ly - ROW_H || ry > ly + lh) continue;
            row(g, shownList.get(i), x, ry, ly, lh, now, mouseX, mouseY);
        }
        if (shownList.isEmpty()) {
            List<SysFont.Line> lines = SysFont.wrap(Component.translatable("shadows.beyondsolo.filter.none"), 6.6f, false, LIST_W - 20);
            for (int i = 0; i < lines.size(); i++) SysFont.draw(g, lines.get(i), x + LIST_W / 2, ly + 20 + i * 8.5f, SystemTheme.TEXT_DIM, 0.5f);
        }
        Shapes.flush(g);
        g.disableScissor();
        if (maxScroll > 0) {
            float knob = Math.max(12, lh * visible / shownList.size());
            float ky = ly + (lh - knob) * scroll / maxScroll;
            Shapes.pill(x + LIST_W + 3, ky, 2, knob).fill(0x809B7BE0).draw(g);
        }
        keysHint(g, x, y + h - 12);

        // ---- detail
        float dx = x + LIST_W + 10;
        float dw = w - LIST_W - 10;
        float k = Anim.outCubic(Anim.progress(selectedAt, 0.28f));
        float old = Gfx.fade(k);
        g.pose().pushPose();
        g.pose().translate((1 - k) * 10, 0, 0);
        detail(g, sel, dx, y, dw, h - FOOT, now, mouseX, mouseY);
        g.pose().popPose();
        Gfx.setAlpha(old);
        if (tip != null) drawTip(g, x, x + w);
    }

    // ---------------------------------------------------------------- header, footer and list

    private void filterRow(GuiGraphics g, List<ShadowRecord> all, float x, float y, int mouseX, int mouseY) {
        int ready = 0;
        for (ShadowRecord r : all) if (r.ready(ShadowClient.top())) ready++;
        String[] labels = new String[Filter.values().length];
        for (Filter f : Filter.values()) {
            labels[f.ordinal()] = f == Filter.READY && ready > 0 ? f.label() + " " + ready : f.label();
        }
        String order = Component.translatable("shadows.beyondsolo.sort", sort.label()).getString();
        // a smaller hand when a language needs more room than the column has
        float size = 5.6f;
        for (; size > 4.4f; size -= 0.3f) {
            float total = SysFont.width(order, size, true) + 10 * 0.9f;
            for (String l : labels) total += SysFont.width(l, size, true) + 10 * 0.9f + 3;
            if (total <= LIST_W) break;
        }
        float cx = x;
        for (Filter f : Filter.values()) {
            boolean on = filter == f;
            int c = f == Filter.READY && ready > 0 ? SystemTheme.GOLD : 0xFFB79CFF;
            float cw = SysFont.width(labels[f.ordinal()], size, true) + 10 * 0.9f;
            boolean hover = Ui.inside(mouseX, mouseY, cx, y, cw, 10);
            Ui.chip(g, cx, y, 10, labels[f.ordinal()], size, on ? c : hover ? Gfx.mix(c, SystemTheme.TEXT_SOFT, 0.4f) : Gfx.mix(c, SystemTheme.TEXT_DIM, 0.55f), on);
            chips.add(new float[]{f.ordinal(), cx, y, cw, 10});
            cx += cw + 3;
        }
        float ow = SysFont.width(order, size, true) + 10 * 0.9f;
        float ox = x + LIST_W - ow;
        boolean hover = Ui.inside(mouseX, mouseY, ox, y, ow, 10);
        Ui.chip(g, ox, y, 10, order, size, hover ? SystemTheme.TEXT : SystemTheme.TEXT_SOFT, false);
        chips.add(new float[]{-1, ox, y, ow, 10});
    }

    private void row(GuiGraphics g, ShadowRecord r, float x, float ry, float ly, float lh, long now, int mouseX, int mouseY) {
        boolean isSel = r.id().equals(selected);
        boolean hover = Ui.inside(mouseX, mouseY, x, ry, LIST_W, ROW_H - 2) && mouseY >= ly && mouseY <= ly + lh;
        int c = Gfx.opaque(r.boss() ? 0xFF4A6A : r.rank().color());
        Ui.card(g, x, ry, LIST_W, ROW_H - 2, 5, c, isSel ? 0.55f : hover ? 0.3f : 0.1f);
        Shapes.pill(x + 2, ry + 3, 2, ROW_H - 8).fill(c).glow(2, Gfx.alpha(c, 0.6f)).draw(g);
        Shapes.icon(r.isMount() ? Icons.RUN : r.role() == ShadowRecord.Role.CASTER ? Icons.SPARKLE : r.boss() ? Icons.CROWN : Icons.SKULL,
                x + 12, ry + (ROW_H - 2) / 2, 9).fill(Gfx.mix(c, 0xFFFFFFFF, 0.4f)).draw(g);
        // a name its master gave it reads in its rank's colour
        String name = SysFont.fit(r.displayName().getString(), 7f, true, LIST_W - 80);
        SysFont.title(g, name, x + 20, ry + 2.5f, 7f, r.bonded() ? Gfx.mix(c, 0xFFFFFFFF, 0.55f) : SystemTheme.TEXT);
        String sub = r.rank().displayName().getString() + "  ·  " + Component.translatable("shadows.beyondsolo.lv", r.level()).getString();
        SysFont.text(g, sub, x + 20, ry + 10.5f, 5.8f, Gfx.mix(c, 0xFFFFFFFF, 0.3f));
        // status
        String status;
        int sc;
        if (r.posted()) {
            status = Component.translatable("shadows.beyondsolo.on_guard").getString();
            sc = ShadowClient.isActive(r.id()) ? SystemTheme.GOLD : Gfx.mix(SystemTheme.GOLD, 0xFF808080, 0.4f);
        } else if (ShadowClient.isActive(r.id())) {
            status = Component.translatable("shadows.beyondsolo.out").getString();
            sc = SystemTheme.GOOD;
        } else if (r.recovering(now)) {
            status = Component.translatable("shadows.beyondsolo.recovering", (r.recoverUntil() - now) / 20 + 1).getString();
            sc = SystemTheme.DANGER;
        } else {
            status = Component.translatable("shadows.beyondsolo.resting").getString();
            sc = SystemTheme.TEXT_DIM;
        }
        SysFont.draw(g, status, x + LIST_W - 5, ry + 6, 6f, sc, true, 1f);
        float sw = SysFont.width(status, 6f, true);
        if (ShadowClient.isActive(r.id())) Shapes.circle(x + LIST_W - 7 - sw - 2, ry + 8.5f, 1.6f)
                .fill(sc).glow(2 + 2 * Anim.pulse(1.4f), Gfx.alpha(sc, 0.7f)).draw(g);
        // waiting for its promotion: a golden arrow
        if (r.ready(ShadowClient.top())) {
            float ax = x + LIST_W - 5 - sw - (ShadowClient.isActive(r.id()) ? 10 : 6);
            Shapes.icon(Icons.ARROW, ax, ry + 8.5f, 8).fill(SystemTheme.GOLD).glow(1.5f + 1.5f * Anim.pulse(1.1f), Gfx.alpha(SystemTheme.GOLD, 0.7f)).draw(g);
        }
        rows.add(new Object[]{r.id(), ry, x});
    }

    private void keysHint(GuiGraphics g, float x, float y) {
        float kx = x + 2;
        kx += Ui.keycap(g, kx, y, 9, key(ShadowClientEvents.RISE), 0xFF9B7BE0) + 3;
        String a = Component.translatable("shadows.beyondsolo.key.rise").getString();
        SysFont.text(g, a, kx, y + 1.5f, 6f, SystemTheme.TEXT_SOFT);
        kx += SysFont.width(a, 6f, false) + 7;
        kx += Ui.keycap(g, kx, y, 9, key(ShadowClientEvents.RECALL), 0xFF9B7BE0) + 3;
        String b = Component.translatable("shadows.beyondsolo.key.recall").getString();
        SysFont.text(g, b, kx, y + 1.5f, 6f, SystemTheme.TEXT_SOFT);
        kx += SysFont.width(b, 6f, false) + 7;
        kx += Ui.keycap(g, kx, y, 9, key(ShadowClientEvents.MOUNT), 0xFF9B7BE0) + 3;
        SysFont.text(g, Component.translatable("shadows.beyondsolo.key.mount").getString(), kx, y + 1.5f, 6f, SystemTheme.TEXT_SOFT);
    }

    /** Under the card, right-aligned: the army's essence (with what just came in) and its names. */
    private void footer(GuiGraphics g, float right, float y) {
        float fx = right - 2;
        String names = Component.translatable("shadows.beyondsolo.names", ShadowClient.bonded(), ShadowClient.nameSlots()).getString();
        fx -= SysFont.width(names, 6.2f, false);
        SysFont.text(g, names, fx, y + 1, 6.2f, SystemTheme.TEXT_SOFT);
        Shapes.icon(Icons.TAG, fx - 6, y + 4.5f, 8).fill(0xFFB79CFF).draw(g);
        fx -= 18;
        String ess = Component.translatable("shadows.beyondsolo.essence", SkillActions.f(ShadowClient.essence())).getString();
        float gain = Anim.since(essenceAt);
        int ec = gain < 0.6f ? Gfx.mix(0xFFE8DCFF, 0xFFFFFFFF, 1 - gain / 0.6f) : 0xFFD8C8FF;
        fx -= SysFont.width(ess, 6.2f, true);
        SysFont.draw(g, ess, fx, y + 1, 6.2f, ec, true, 0f);
        Shapes.icon(Icons.DIAMOND, fx - 6, y + 4.5f, 8).fill(0xFFC9A6FF).glow(gain < 1.2f ? 4 * (1 - gain / 1.2f) : 0, 0xC0B07CFF).draw(g);
        // what just came in, rising and fading
        if (gain < 1.8f && essenceGain > 0) {
            float a = Anim.clamp01((1.8f - gain) / 0.6f);
            float o = Gfx.fade(a);
            SysFont.draw(g, "+" + essenceGain, fx + 4, y - 6 - gain * 6, 6.4f, 0xFFE2CCFF, true, 0f);
            Gfx.setAlpha(o);
        }
    }

    private static String key(net.minecraft.client.KeyMapping k) {
        String s = k.getTranslatedKeyMessage().getString().toUpperCase();
        return s.length() > 3 ? s.substring(0, 3) : s;
    }

    // ---------------------------------------------------------------- the card

    private void detail(GuiGraphics g, ShadowRecord r, float x, float y, float w, float h, long now, int mouseX, int mouseY) {
        int c = Gfx.opaque(r.boss() ? 0xFF4A6A : r.rank().color());
        int rc = Gfx.opaque(r.rank().color());
        Ui.card(g, x, y, w, h, 9, c, 0.22f);
        float px = x + 10;
        float pw = w - 20;
        ShadowRank top = ShadowClient.top();

        // the shadow itself, turning slowly
        float boxW = 70, boxH = 80;
        float bx = px - 2, by = y + 8;
        Shapes.box(bx, by, boxW, boxH).radius(7).fillV(0xC0100820, 0xE0040208).border(0.75f, Gfx.alpha(c, 0.5f)).draw(g);
        Shapes.circle(bx + boxW / 2, by + boxH - 12, 24).fill(0).glow(16, Gfx.alpha(c, 0.25f)).draw(g);
        celebration(g, r, bx, by, boxW, boxH, true);
        drawPreview(g, r, (int) bx, (int) by, (int) (bx + boxW), (int) (by + boxH));
        celebration(g, r, bx, by, boxW, boxH, false);
        nameTag(g, r, bx + boxW - 8, by + 8, mouseX, mouseY);

        float tx = bx + boxW + 8;
        float tw = x + w - 10 - tx;
        float cy = y + 9;
        // chips while they fit: rank, elite, named, role
        float chipX = tx;
        chipX += Ui.chip(g, chipX, cy, 10, r.rank().displayName().getString().toUpperCase(), 6f, rc, true) + 4;
        if (r.boss()) chipX = chipIfRoom(g, chipX, cy, tx + tw, Component.translatable("shadows.beyondsolo.elite").getString(), SystemTheme.GOLD, true);
        if (r.bonded()) chipX = chipIfRoom(g, chipX, cy, tx + tw, Component.translatable("shadows.beyondsolo.named").getString(), 0xFFE2CCFF, false);
        chipIfRoom(g, chipX, cy, tx + tw, r.role().displayName().getString(), SystemTheme.TEXT_SOFT, false);
        cy += 14;

        if (r.id().equals(naming)) {
            cy = nameField(g, r, tx, cy, tw, mouseX, mouseY);
        } else {
            // a long name steps down a size rather than lose its end ("Gralric, la Novena Campana")
            float nameSize = 10.5f;
            List<SysFont.Line> nameLines = SysFont.wrap(r.displayName(), nameSize, true, tw);
            if (nameLines.size() > 2) {
                nameSize = 8.5f;
                nameLines = SysFont.wrap(r.displayName(), nameSize, true, tw);
            }
            int nc = r.bonded() ? Gfx.mix(rc, 0xFFFFFFFF, 0.6f) : SystemTheme.TEXT;
            for (int i = 0; i < Math.min(3, nameLines.size()); i++) {
                SysFont.draw(g, nameLines.get(i), tx, cy, nc, 0f);
                cy += nameSize * SysFont.LINE;
            }
            cy += 2;
        }
        // level and xp; at the top of a rank its master promotes it out of, the bar waits in gold
        boolean max = r.maxed(top);
        boolean ready = r.ready(top);
        String levelText = Component.translatable("shadows.beyondsolo.level", r.level(), ShadowRank.LEVEL_CAP).getString();
        SysFont.title(g, levelText, tx, cy, 7.5f, Gfx.mix(c, 0xFFFFFFFF, 0.4f));
        String xp = max ? Component.translatable("shadows.beyondsolo.max").getString()
                : ready ? Component.translatable("shadows.beyondsolo.ready").getString() : r.xp() + " / " + r.xpToNext();
        // what is left of the line beside the level: the short word when the long one won't fit, a smaller hand if need be
        float room = tw - SysFont.width(levelText, 7.5f, true) - 4;
        if (ready && SysFont.width(xp, 6.2f, true) > room) xp = Component.translatable("shadows.beyondsolo.ready.short").getString();
        float xs = 6.2f;
        while (xs > 5.0f && SysFont.width(xp, xs, ready) > room) xs -= 0.4f;
        SysFont.draw(g, xp, tx + tw, cy + 0.5f + (6.2f - xs) * 0.5f, xs, ready ? SystemTheme.GOLD : SystemTheme.TEXT_SOFT, ready, 1f);
        if (ready) Ui.bar(g, tx, cy + 10, tw, 2.6f, 1, Gfx.mix(SystemTheme.GOLD, 0xFF000000, 0.25f), Gfx.mix(SystemTheme.GOLD, 0xFFFFFFFF, 0.2f * Anim.pulse(1.2f)));
        else Ui.bar(g, tx, cy + 10, tw, 2.6f, max ? 1 : r.xp() / (float) r.xpToNext(), Gfx.mix(c, 0xFF000000, 0.2f), c);
        cy += 17;
        var master = ClientSystemData.get();
        float sx = tx;
        sx += stat(g, sx, cy, Icons.HEART, SkillActions.f(ShadowService.maxHealth(r, master)), SystemTheme.HP_B) + 4;
        sx += stat(g, sx, cy, Icons.FLAME, SkillActions.f(ShadowService.damage(r, master)), 0xFFFFB070) + 4;
        // big health pools and long words: the kills go under the others when the row runs out
        String kills = Component.translatable("shadows.beyondsolo.kills", r.kills()).getString();
        if (sx + statWidth(kills) > tx + tw) {
            sx = tx;
            cy += 12;
        }
        stat(g, sx, cy, Icons.MEDAL, kills, SystemTheme.GOLD);
        cy += 15;

        // under the portrait, from the bottom up: the buttons, its promotion, its post, then what it does in what is left
        float bY = y + h - 20;
        float bottom = bY - 4;
        boolean promoting = r.rank().ordinal() >= ShadowRank.KNIGHT.ordinal() && r.rank().ordinal() < top.ordinal();
        boolean capped = r.rank() == ShadowRank.MARSHAL && top == ShadowRank.MARSHAL;
        if (promoting) {
            bottom -= 25;
            promotion(g, r, px, bottom + 2, pw, ready, mouseX, mouseY);
        } else if (capped) {
            List<SysFont.Line> cl = SysFont.wrap(Component.translatable("shadows.beyondsolo.promote.capped"), 5.8f, false, pw);
            bottom -= Math.min(2, cl.size()) * 5.8f * SysFont.LINE + 2;
            float ly = bottom + 2;
            for (int i = 0; i < Math.min(2, cl.size()); i++) {
                SysFont.draw(g, cl.get(i), px, ly, SystemTheme.TEXT_DIM, 0f);
                ly += 5.8f * SysFont.LINE;
            }
        }
        float dy = Math.max(cy, by + boxH + 5);
        // standing guard: where, and how far
        if (r.posted()) {
            Minecraft mc = Minecraft.getInstance();
            Component where;
            if (mc.player != null && mc.level != null && r.postedIn(mc.level.dimension())) {
                where = Component.translatable("shadows.beyondsolo.guarding", Math.round(Math.sqrt(mc.player.distanceToSqr(r.post()))));
            } else {
                where = Component.translatable("shadows.beyondsolo.guarding.elsewhere");
            }
            // two lines above the buttons: a smaller hand when a language needs more
            float gs = 6.4f;
            List<SysFont.Line> gl = SysFont.wrap(where, gs, false, pw);
            if (gl.size() > 2) {
                gs = 5.6f;
                gl = SysFont.wrap(where, gs, false, pw);
            }
            int n = Math.min(2, gl.size());
            float top2 = bottom - n * gs * SysFont.LINE;
            if (top2 >= dy) {
                float gy = top2;
                for (int i = 0; i < n; i++) {
                    SysFont.draw(g, gl.get(i), px, gy, SystemTheme.GOLD, 0f);
                    gy += gs * SysFont.LINE;
                }
                bottom = top2 - 1;
            }
        }
        // what it does (and whether it leads the shadows round it), in the lines that are left
        String what = r.boss() ? "shadows.beyondsolo.does.boss" : "shadows.beyondsolo.does." + r.role().key();
        Component does = r.rank().ordinal() >= ShadowRank.COMMANDER.ordinal()
                ? Component.translatable(what).append(" ").append(Component.translatable("shadows.beyondsolo.leads", Math.round((ShadowPromotions.LEAD - 1) * 100)))
                : Component.translatable(what);
        List<SysFont.Line> lines = SysFont.wrap(does, 6.4f, false, pw);
        int fit = 0;
        while (fit < Math.min(3, lines.size()) && dy + (fit + 1) * 6.4f * SysFont.LINE <= bottom + 1) fit++;
        for (int i = 0; i < fit; i++) {
            if (i == fit - 1 && fit < lines.size()) {
                // more than there is room for: the last line that fits ends in dots
                SysFont.draw(g, SysFont.fit(plain(lines.get(i)).replaceAll("[\\s,;:.]+$", "") + "...", 6.4f, false, pw), px, dy, 6.4f, SystemTheme.TEXT_SOFT, false, 0f);
            } else {
                SysFont.draw(g, lines.get(i), px, dy, SystemTheme.TEXT_SOFT, 0f);
            }
            dy += 6.4f * SysFont.LINE;
        }

        // buttons
        float bx2 = px;
        boolean out = ShadowClient.isActive(r.id());
        boolean recovering = r.recovering(now);
        if (out) bx2 += button(g, bx2, bY, "shadows.beyondsolo.btn.dismiss", ShadowActionPayload.DISMISS, r, 0xFF9B7BE0, true, mouseX, mouseY) + 4;
        else bx2 += button(g, bx2, bY, "shadows.beyondsolo.btn.summon", ShadowActionPayload.SUMMON, r, 0xFFB79CFF, !recovering, mouseX, mouseY) + 4;
        if (r.isMount()) bx2 += button(g, bx2, bY, "shadows.beyondsolo.btn.ride", ShadowActionPayload.RIDE, r, 0xFF6FA0FF, !recovering, mouseX, mouseY) + 4;
        // leave it to guard where it stands, or take it off its post
        else if (r.posted()) bx2 += button(g, bx2, bY, "shadows.beyondsolo.btn.unpost", ShadowActionPayload.UNPOST, r, SystemTheme.GOLD, true, mouseX, mouseY) + 4;
        else if (out) bx2 += button(g, bx2, bY, "shadows.beyondsolo.btn.post", ShadowActionPayload.POST, r, SystemTheme.GOLD, true, mouseX, mouseY) + 4;
        boolean armed = r.id().equals(releaseArmed) && Anim.since(releaseArmedAt) < 3;
        float rw = buttonWidth(armed ? "shadows.beyondsolo.btn.confirm" : "shadows.beyondsolo.btn.release");
        float rx = x + w - 10 - rw;
        button(g, rx, bY, armed ? "shadows.beyondsolo.btn.confirm" : "shadows.beyondsolo.btn.release",
                ShadowActionPayload.RELEASE, r, SystemTheme.DANGER, true, mouseX, mouseY);
        // what letting it go leaves behind
        if (armed || Ui.inside(mouseX, mouseY, rx, bY, rw, 13)) {
            String back = Component.translatable("shadows.beyondsolo.release.essence", ShadowPromotions.releaseEssence(r)).getString();
            SysFont.draw(g, back, rx + rw, bY - 7.5f, 5.6f, 0xFFD8C8FF, false, 1f);
        }
    }

    private static float chipIfRoom(GuiGraphics g, float x, float y, float right, String text, int color, boolean filled) {
        if (x + SysFont.width(text, 6f, true) + 9 > right) return x;
        return x + Ui.chip(g, x, y, 10, text, 6f, color, filled) + 4;
    }

    /**
     * The promotion strip: the rank it would rise to, what that takes (its own level, the master's level, the essence),
     * each lit green when met, and the button.
     */
    private void promotion(GuiGraphics g, ShadowRecord r, float x, float y, float w, boolean ready, int mouseX, int mouseY) {
        ShadowRank to = r.rank().next();
        int tc = Gfx.opaque(to.color());
        int masterLevel = ClientSystemData.get().level();
        boolean lvOk = r.level() >= ShadowRank.LEVEL_CAP;
        boolean masterOk = masterLevel >= to.promotionLevel();
        boolean essOk = ShadowClient.essence() >= to.promotionCost();
        boolean can = lvOk && masterOk && essOk;
        float h = 23;
        Shapes.box(x - 3, y, w + 6, h).radius(5).fillV(Gfx.alpha(tc, can ? 0.20f : 0.10f), Gfx.alpha(tc, 0.04f))
                .border(0.6f, Gfx.alpha(tc, can ? 0.75f : 0.35f)).glow(can ? 3 + 2 * Anim.pulse(1.3f) : 0, Gfx.alpha(tc, 0.35f)).draw(g);
        String head = Component.translatable("shadows.beyondsolo.promotion", to.displayName().getString().toUpperCase()).getString();
        SysFont.spaced(g, head, x, y + 3, 5.8f, Gfx.mix(tc, 0xFFFFFFFF, 0.45f), true, 1f, 0f);
        // the button, on the right of the strip
        String key = "shadows.beyondsolo.btn.promote";
        float bw = buttonWidth(key);
        // what it takes, left of the button: a smaller hand when a language's words run into it
        String[] needs = {Component.translatable("shadows.beyondsolo.need.level", ShadowRank.LEVEL_CAP).getString(),
                Component.translatable("shadows.beyondsolo.need.master", to.promotionLevel()).getString(),
                Component.translatable("shadows.beyondsolo.need.essence", SkillActions.f(to.promotionCost())).getString()};
        float ns = 5.4f;
        while (ns > 4.4f && needsWidth(needs, ns) > w - bw - 4) ns -= 0.2f;
        float rx = x;
        float ry = y + 12;
        rx += need(g, rx, ry, needs[0], lvOk, ns) + 3;
        rx += need(g, rx, ry, needs[1], masterOk, ns) + 3;
        need(g, rx, ry, needs[2], essOk, ns);
        button(g, x + w - bw, y + 5, key, ShadowActionPayload.PROMOTE, r, tc, can, mouseX, mouseY);
        if (!can && Ui.inside(mouseX, mouseY, x + w - bw, y + 5, bw, 13)) {
            setTip(Component.translatable(!lvOk ? "shadows.beyondsolo.why.level" : !masterOk ? "shadows.beyondsolo.why.master" : "shadows.beyondsolo.why.essence",
                    to.promotionLevel(), SkillActions.f(to.promotionCost() - ShadowClient.essence())).getString(), mouseX, mouseY);
        }
    }

    private static float need(GuiGraphics g, float x, float y, String text, boolean met, float size) {
        int c = met ? SystemTheme.GOOD : 0xFFFF8A94;
        float w = SysFont.width(text, size, true) + 11;
        Shapes.pill(x, y, w, 8.5f).fill(Gfx.alpha(c, 0.10f)).border(0.5f, Gfx.alpha(c, 0.55f)).draw(g);
        Shapes.icon(met ? Icons.CHECK : Icons.X, x + 4.2f, y + 4.25f, 5.5f).fill(c).draw(g);
        SysFont.draw(g, text, x + 8, SysFont.centerY(y, 8.5f, size), size, Gfx.mix(c, 0xFFFFFFFF, 0.35f), true, 0f);
        return w;
    }

    /** How wide the promotion's needs are in a row at a text size (pills and the gaps between them). */
    private static float needsWidth(String[] needs, float size) {
        float w = 0;
        for (String n : needs) w += SysFont.width(n, size, true) + 11 + 3;
        return w - 3;
    }

    /** The tag on the portrait's corner: name the shadow (dim, with the reason, when it can't take one). */
    private void nameTag(GuiGraphics g, ShadowRecord r, float cx, float cy, int mouseX, int mouseY) {
        boolean nameable = r.boss() || r.rank().ordinal() >= ShadowRank.KNIGHT.ordinal();
        boolean room = r.bonded() || ShadowClient.bonded() < ShadowClient.nameSlots();
        boolean can = nameable && room;
        boolean hover = Ui.inside(mouseX, mouseY, cx - 6, cy - 6, 12, 12);
        int c = can ? 0xFFE2CCFF : 0xFF6F6880;
        Shapes.circle(cx, cy, 5.5f).fill(hover && can ? 0xE02A1A48 : 0xC0140A24).border(0.6f, Gfx.alpha(c, hover ? 1f : 0.6f))
                .glow(hover && can ? 4 : 0, Gfx.alpha(c, 0.4f)).draw(g);
        Shapes.icon(Icons.TAG, cx, cy, 7).fill(c).draw(g);
        if (hover) {
            String t = !nameable ? Component.translatable("shadows.beyondsolo.name.too_weak").getString()
                    : !room ? Component.translatable("shadows.beyondsolo.name.slots", ShadowClient.nameSlots()).getString()
                    : Component.translatable(r.bonded() ? "shadows.beyondsolo.name.rename" : "shadows.beyondsolo.name.give").getString();
            setTip(t, mouseX, mouseY);
        }
        if (can) buttons.add(new Object[]{-1, r.id(), cx - 6, cy - 6, 12f, 12f});
    }

    /** Typing a name: the field, what a name may be, and the keep / cancel buttons. Returns where the card goes on. */
    private float nameField(GuiGraphics g, ShadowRecord r, float x, float y, float w, int mouseX, int mouseY) {
        int rc = Gfx.opaque(r.rank().color());
        NAME.render(g, x, y, w, 12, Component.translatable("shadows.beyondsolo.name.placeholder").getString(), rc, mouseX, mouseY);
        y += 14;
        String typed = NAME.value().strip();
        boolean ok = typed.isEmpty() ? r.bonded() : ShadowNames.fit(typed);
        String hint = typed.isEmpty() && r.bonded() ? Component.translatable("shadows.beyondsolo.name.take_back").getString()
                : Component.translatable("shadows.beyondsolo.name.rules", ShadowNames.MIN_GIVEN, ShadowNames.MAX_GIVEN).getString();
        SysFont.text(g, SysFont.fit(hint, 5.4f, false, w), x, y, 5.4f, ok || typed.isEmpty() ? SystemTheme.TEXT_DIM : 0xFFFF8A94);
        y += 8;
        float bx = x;
        bx += smallButton(g, bx, y, "shadows.beyondsolo.name.keep", -2, r, rc, ok, mouseX, mouseY) + 3;
        smallButton(g, bx, y, "shadows.beyondsolo.name.cancel", -3, r, SystemTheme.TEXT_SOFT, true, mouseX, mouseY);
        return y + 13;
    }

    private float smallButton(GuiGraphics g, float x, float y, String key, int action, ShadowRecord r, int color, boolean enabled, int mx, int my) {
        String text = Component.translatable(key).getString();
        float w = SysFont.width(text, 6f, true) + 10;
        float h = 10;
        boolean hover = enabled && Ui.inside(mx, my, x, y, w, h);
        int c = enabled ? color : 0xFF4A5468;
        Shapes.box(x, y, w, h).radius(3).fill(Gfx.alpha(c, hover ? 0.35f : 0.15f)).border(0.6f, Gfx.alpha(c, hover ? 1f : 0.6f)).draw(g);
        SysFont.draw(g, text, x + w / 2, SysFont.centerY(y, h, 6f), 6f, enabled ? Gfx.mix(c, 0xFFFFFFFF, 0.5f) : SystemTheme.TEXT_DIM, true, 0.5f);
        if (enabled) buttons.add(new Object[]{action, r.id(), x, y, w, h});
        return w;
    }

    /** A climb of the shown shadow: rays and a flash behind the portrait, the new rank stamped across it. */
    private static void celebration(GuiGraphics g, ShadowRecord r, float bx, float by, float bw, float bh, boolean behind) {
        if (!r.id().equals(celebrated)) return;
        float t = Anim.since(celebratedAt);
        if (t > 2.4f) return;
        int c = Gfx.opaque(r.rank().color());
        float in = Anim.outCubic(Anim.clamp01(t / 0.4f));
        float out = Anim.clamp01((2.4f - t) / 0.6f);
        float cx = bx + bw / 2, cy = by + bh * 0.55f;
        if (behind) {
            Shapes.rays(cx, cy, 52 * in, 14).fill(Gfx.alpha(c, 0.30f * out)).rotation(Anim.loop(9f)).draw(g);
            Shapes.circle(cx, cy, 10 + 22 * in).fill(0).glow(18, Gfx.alpha(c, 0.55f * out)).draw(g);
            return;
        }
        float flash = Anim.clamp01(1 - t / 0.35f);
        if (flash > 0) Shapes.box(bx, by, bw, bh).radius(7).fill(Gfx.alpha(0xFFFFFFFF, 0.45f * flash)).draw(g);
        Shapes.ring(cx, cy, 8 + 34 * in, 1.2f).fill(Gfx.alpha(c, 0.8f * (1 - in))).draw(g);
        float old = Gfx.fade(out);
        float sy = by + bh - 17;
        Shapes.box(bx + 3, sy, bw - 6, 13).radius(3).fill(0xD0100820).border(0.6f, c).glow(4, Gfx.alpha(c, 0.5f)).draw(g);
        SysFont.spaced(g, r.rank().displayName().getString().toUpperCase(), cx, sy + 3.5f, 6.4f, Gfx.mix(c, 0xFFFFFFFF, 0.5f), true, 1.4f, 0.5f);
        Gfx.setAlpha(old);
    }

    private void setTip(String text, float mx, float my) {
        tip = text;
        tipX = mx;
        tipY = my;
    }

    /** A wrapped line's text as a plain string. */
    private static String plain(SysFont.Line line) {
        StringBuilder b = new StringBuilder();
        line.text().accept((index, style, codePoint) -> {
            b.appendCodePoint(codePoint);
            return true;
        });
        return b.toString();
    }

    private void drawTip(GuiGraphics g, float left, float right) {
        List<SysFont.Line> lines = SysFont.wrap(Component.literal(tip), 6f, false, 130);
        float w = 0;
        for (SysFont.Line l : lines) w = Math.max(w, l.width());
        w = Math.min(136, Math.max(w, 40)) + 8;
        float h = lines.size() * 6f * SysFont.LINE + 5;
        float x = Mth.clamp(tipX - w / 2, left, right - w);
        float y = tipY + 10;
        Shapes.box(x, y, w, h).radius(3).fill(0xF00A0614).border(0.6f, 0xC09B7BE0).draw(g);
        for (int i = 0; i < lines.size(); i++) SysFont.draw(g, lines.get(i), x + 4, y + 3 + i * 6f * SysFont.LINE, SystemTheme.TEXT, 0f);
    }

    private void drawPreview(GuiGraphics g, ShadowRecord r, int x0, int y0, int x1, int y1) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (preview == null || preview.level() != mc.level) {
            preview = ShadowModule.SHADOW.get().create(mc.level);
            if (preview == null) return;
            preview.preview = true;
        }
        preview.setLook(r.type().toString(), r.rank(), r.boss() ? r.bossRank() : -1, r.role());
        preview.tickCount = (int) (Anim.now() * 20);
        float big = Math.max(preview.getBbHeight(), preview.getBbWidth() * 0.9f);
        int scale = (int) Mth.clamp((y1 - y0) * 0.6f / Math.max(0.5f, big * ShadowRenderer.tierScale(r.rank())), 6, 60);
        float turn = (float) Math.sin(Anim.now() * 0.6) * 0.9f;
        try {
            Shapes.flush(g);
            InventoryScreen.renderEntityInInventoryFollowsAngle(g, x0, y0, x1, y1 - 4, scale, 0.0625f, turn, -0.15f, preview);
        } catch (RuntimeException ignored) {
            // some creatures can't be drawn outside a world; the card still shows its numbers
        }
    }

    private static float statWidth(String text) {
        return SysFont.width(text, 6.5f, true) + 17;
    }

    private static float stat(GuiGraphics g, float x, float y, net.minecraft.resources.ResourceLocation icon, String text, int color) {
        float w = statWidth(text);
        Shapes.pill(x, y, w, 10).fill(Gfx.alpha(color, 0.1f)).border(0.6f, Gfx.alpha(color, 0.5f)).draw(g);
        Shapes.icon(icon, x + 6, y + 5, 7).fill(color).draw(g);
        SysFont.draw(g, text, x + 11.5f, SysFont.centerY(y, 10, 6.5f), 6.5f, Gfx.mix(color, 0xFFFFFFFF, 0.3f), true, 0f);
        return w;
    }

    private static float buttonWidth(String key) {
        return SysFont.width(Component.translatable(key).getString(), 6.8f, true) + 16;
    }

    private float button(GuiGraphics g, float x, float y, String key, int action, ShadowRecord r, int color, boolean enabled, int mx, int my) {
        String text = Component.translatable(key).getString();
        float w = buttonWidth(key);
        float h = 13;
        boolean hover = enabled && Ui.inside(mx, my, x, y, w, h);
        int c = enabled ? color : 0xFF4A5468;
        Shapes.box(x, y, w, h).radius(4).fillV(Gfx.alpha(c, hover ? 0.4f : 0.22f), Gfx.alpha(c, hover ? 0.2f : 0.08f))
                .border(0.75f, Gfx.alpha(c, hover ? 1f : 0.65f)).glow(hover ? 5 : 0, Gfx.alpha(c, 0.4f)).draw(g);
        SysFont.draw(g, text, x + w / 2, SysFont.centerY(y, h, 6.8f), 6.8f, enabled ? Gfx.mix(c, 0xFFFFFFFF, 0.5f) : SystemTheme.TEXT_DIM, true, 0.5f);
        if (enabled) buttons.add(new Object[]{action, r.id(), x, y, w, h});
        return w;
    }

    // ---------------------------------------------------------------- input

    private void startNaming(ShadowRecord r) {
        naming = r.id();
        NAME.set(r.bonded() ? r.name() : "");
        NAME.focus(true);
    }

    private void stopNaming() {
        naming = null;
        NAME.focus(false);
    }

    private void keepName() {
        if (naming == null) return;
        PacketDistributor.sendToServer(new ShadowNamePayload(naming.toString(), NAME.value().strip()));
        stopNaming();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (naming != null && NAME.click(mouseX, mouseY)) return true;
        for (Object[] b : buttons) {
            if (!Ui.inside(mouseX, mouseY, (float) b[2], (float) b[3], (float) b[4], (float) b[5])) continue;
            int action = (int) b[0];
            UUID id = (UUID) b[1];
            SystemScreen.playClick();
            switch (action) {
                case -1 -> {
                    for (ShadowRecord r : ShadowClient.shadows()) if (r.id().equals(id)) startNaming(r);
                    return true;
                }
                case -2 -> {
                    keepName();
                    return true;
                }
                case -3 -> {
                    stopNaming();
                    return true;
                }
                default -> {
                }
            }
            if (action == ShadowActionPayload.RELEASE && !(id.equals(releaseArmed) && Anim.since(releaseArmedAt) < 3)) {
                releaseArmed = id;
                releaseArmedAt = Anim.now();
                return true;
            }
            releaseArmed = null;
            PacketDistributor.sendToServer(new ShadowActionPayload(action, id.toString()));
            if (action == ShadowActionPayload.RIDE) Minecraft.getInstance().setScreen(null);
            return true;
        }
        for (float[] c : chips) {
            if (!Ui.inside(mouseX, mouseY, c[1], c[2], c[3], c[4])) continue;
            if (c[0] < 0) sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
            else filter = Filter.values()[(int) c[0]];
            scroll = 0;
            SystemScreen.playClick();
            return true;
        }
        for (Object[] r : rows) {
            float ry = (float) r[1];
            if (mouseY >= ry && mouseY < ry + ROW_H - 2 && mouseX >= (float) r[2] && mouseX <= (float) r[2] + LIST_W) {
                UUID id = (UUID) r[0];
                if (!id.equals(selected)) {
                    selected = id;
                    SystemScreen.playClick();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (naming == null) return false;
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            String typed = NAME.value().strip();
            boolean bonded = false;
            for (ShadowRecord r : ShadowClient.shadows()) if (r.id().equals(naming)) bonded = r.bonded();
            if (typed.isEmpty() ? bonded : ShadowNames.fit(typed)) keepName();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            stopNaming();
            return true;
        }
        if (!NAME.focused()) return false;
        return NAME.keyPressed(keyCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        return naming != null && NAME.charTyped(c);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        scroll -= (float) scrollY;
        return true;
    }
}
