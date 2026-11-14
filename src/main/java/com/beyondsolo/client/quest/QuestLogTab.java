package com.beyondsolo.client.quest;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.screen.QuestsTab;
import com.beyondsolo.client.screen.SystemScreen;
import com.beyondsolo.client.screen.SystemTab;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestObjective;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The QUESTS page of the System window: open offers and active quests on the left, the selected quest on the
 * right with its objectives, rewards, penalty and buttons (accept / decline an offer, abandon a quest).
 * Before there are any quests it shows the story objective, like the core placeholder did.
 */
public class QuestLogTab implements SystemTab {
    private static final float LIST_W = 132;
    private static final float ROW_H = 28;

    private static UUID selected;
    private final QuestsTab placeholder = new QuestsTab();
    private float indicatorY = Float.NaN;
    private double selectedAt = -10;
    private UUID shownDetail;
    private double confirmUntil = -1;
    // click areas, set while drawing
    private final List<Object[]> rows = new ArrayList<>();
    private float[] acceptBtn, declineBtn, abandonBtn;

    @Override
    public Component title() {
        int offers = ClientQuests.offers().size();
        return offers > 0 ? Component.translatable("tab.beyondsolo.quests.new", offers) : Component.translatable("tab.beyondsolo.quests");
    }

    private static List<Quest> entries() {
        List<Quest> list = new ArrayList<>(ClientQuests.offers());
        list.addAll(ClientQuests.active());
        return list;
    }

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        List<Quest> list = entries();
        rows.clear();
        acceptBtn = declineBtn = abandonBtn = null;
        if (list.isEmpty()) {
            placeholder.render(g, font, x, y, w, h, mouseX, mouseY, partialTick);
            return;
        }
        Quest sel = null;
        for (Quest q : list) if (q.id().equals(selected)) sel = q;
        if (sel == null) {
            sel = list.get(0);
            selected = sel.id();
        }
        if (!sel.id().equals(shownDetail)) {
            shownDetail = sel.id();
            selectedAt = Anim.now();
            confirmUntil = -1;
        }

        // ---- list
        float ly = y;
        int index = 0;
        boolean offersHeader = false;
        boolean activeHeader = false;
        float selY = y;
        for (Quest q : list) {
            boolean offer = q.status() == Quest.Status.OFFERED;
            if (offer && !offersHeader) {
                offersHeader = true;
                ly = header(g, x, ly, Component.translatable("quests.beyondsolo.offers", ClientQuests.offers().size()).getString(), SystemTheme.GOLD);
            } else if (!offer && !activeHeader) {
                activeHeader = true;
                ly = header(g, x, ly, Component.translatable("quests.beyondsolo.active", ClientQuests.active().size()).getString(), SystemTheme.ACCENT);
            }
            if (ly + ROW_H > y + h - 12) break;
            if (q.id().equals(selected)) selY = ly;
            drawRow(g, q, x, ly, mouseX, mouseY, q.id().equals(selected), index);
            rows.add(new Object[]{q.id(), x, ly});
            ly += ROW_H + 3;
            index++;
        }
        indicatorY = Float.isNaN(indicatorY) ? selY : Anim.approach(indicatorY, selY, 18f);
        Shapes.pill(x - 4, indicatorY + 5, 2, ROW_H - 10).fillV(SystemTheme.ACCENT_BRIGHT, SystemTheme.ACCENT).glow(4, Gfx.alpha(SystemTheme.ACCENT, 0.7f)).draw(g);

        var data = ClientQuests.data();
        String footer = Component.translatable("quests.beyondsolo.footer", data.completedCount(), data.failedCount()).getString();
        SysFont.text(g, footer, x + 2, y + h - 8, 6.5f, SystemTheme.TEXT_DIM);

        // ---- detail
        float dx = x + LIST_W + 10;
        float dw = w - LIST_W - 10;
        float k = Anim.outCubic(Anim.progress(selectedAt, 0.28f));
        float old = Gfx.fade(k);
        g.pose().pushPose();
        g.pose().translate((1 - k) * 10, 0, 0);
        drawDetail(g, sel, dx, y, dw, h, mouseX, mouseY);
        g.pose().popPose();
        Gfx.setAlpha(old);
    }

    private static float header(GuiGraphics g, float x, float y, String text, int color) {
        SysFont.spaced(g, text, x + 2, y + 1, 6.2f, color, true, 1.3f, 0f);
        return y + 11;
    }

    private void drawRow(GuiGraphics g, Quest q, float x, float y, int mouseX, int mouseY, boolean isSelected, int index) {
        int color = q.category().color();
        boolean hover = Ui.inside(mouseX, mouseY, x, y, LIST_W, ROW_H);
        boolean offer = q.status() == Quest.Status.OFFERED;
        float pulse = offer ? Anim.pulse(1.2f) : 0;
        Ui.card(g, x, y, LIST_W, ROW_H, 7, color, isSelected ? 0.55f : hover ? 0.32f : 0.14f + 0.15f * pulse);
        if (isSelected) Shapes.box(x, y, LIST_W, ROW_H).radius(7).fill(0x00000000).border(0.9f, Gfx.alpha(color, 0.8f)).glow(4, Gfx.alpha(color, 0.25f)).draw(g);
        Ui.iconBadge(g, QuestText.icon(q.category()), x + 13, y + ROW_H / 2f, 7.5f, color);
        float tx = x + 25;
        float right = 20;
        SysFont.title(g, SysFont.fit(q.title().getString(), 8f, true, LIST_W - 25 - right), tx, y + 6, 8f, SystemTheme.TEXT);
        long left = ClientQuests.ticksLeft(q);
        String sub = offer ? Component.translatable("quests.beyondsolo.row.offer").getString()
                : q.category().displayName().getString();
        if (left >= 0) sub += "  ·  " + QuestText.clock(left);
        SysFont.text(g, SysFont.fit(sub, 6.5f, false, LIST_W - 25 - right), tx, y + 17, 6.5f, offer ? Gfx.mix(SystemTheme.GOLD, 0xFFFFFFFF, 0.2f) : SystemTheme.TEXT_DIM);
        // a small ring with the overall progress (or a "!" for an offer)
        float rx = x + LIST_W - 11;
        float ry = y + ROW_H / 2f;
        if (offer) {
            Shapes.circle(rx, ry, 5.5f).fill(Gfx.alpha(SystemTheme.GOLD, 0.25f + 0.25f * pulse)).border(0.75f, SystemTheme.GOLD).glow(2 + 3 * pulse, Gfx.alpha(SystemTheme.GOLD, 0.5f)).draw(g);
            SysFont.draw(g, "!", rx, SysFont.centerY(ry - 4, 8, 7f), 7f, 0xFFFFF4D0, true, 0.5f);
        } else {
            Shapes.ring(rx, ry, 5.5f, 1.4f).fill(SystemTheme.TRACK).draw(g);
            if (q.fraction() > 0.01f) Shapes.ring(rx, ry, 5.5f, 1.4f).fillAround(Gfx.mix(color, 0xFFFFFFFF, 0.3f), color).progress(q.fraction()).glow(2, Gfx.alpha(color, 0.5f)).draw(g);
        }
    }

    private void drawDetail(GuiGraphics g, Quest q, float x, float y, float w, float h, int mouseX, int mouseY) {
        int color = q.category().color();
        Ui.card(g, x, y, w, h, 9, color, 0.22f);
        float px = x + 10;
        float pw = w - 20;
        float cy = y + 8;

        // chips and clock
        float chipX = px;
        chipX += Ui.chip(g, chipX, cy, 10, q.category().displayName().getString(), 6f, color, true) + 4;
        String rank = Component.translatable("quest.beyondsolo.rank_chip", "EDCBAS".charAt(Mth.clamp(q.rankTier(), 0, 5)) + "").getString();
        Ui.chip(g, chipX, cy, 10, rank, 6f, SystemTheme.TEXT_SOFT, false);
        long left = ClientQuests.ticksLeft(q);
        if (left >= 0) {
            boolean urgent = left < 20 * 30;
            int cc = urgent ? Gfx.mix(SystemTheme.DANGER, 0xFFFFFFFF, 0.35f * Anim.pulse(0.6f)) : SystemTheme.TEXT_SOFT;
            String clock = QuestText.clock(left);
            float cw = SysFont.width(clock, 7.5f, true);
            SysFont.draw(g, clock, px + pw, SysFont.centerY(cy, 10, 7.5f), 7.5f, cc, true, 1f);
            Shapes.icon(Icons.HOURGLASS, px + pw - cw - 6, cy + 5, 8).fill(cc).draw(g);
        }
        cy += 15;

        // title and description
        SysFont.title(g, SysFont.fit(q.title().getString(), 12f, true, pw), px, cy, 12f, SystemTheme.TEXT);
        cy += 15;
        List<SysFont.Line> desc = SysFont.wrap(q.description(), 7.5f, false, pw);
        int lines = Math.min(3, desc.size());
        for (int i = 0; i < lines; i++) {
            SysFont.draw(g, desc.get(i), px, cy, SystemTheme.TEXT_SOFT, 0f);
            cy += 7.5f * SysFont.LINE;
        }
        cy += 4;

        // objectives
        SysFont.spaced(g, Component.translatable("quests.beyondsolo.objectives").getString(), px, cy, 6f, SystemTheme.ACCENT, true, 1.4f, 0f);
        cy += 9;
        for (QuestObjective o : q.objectives()) {
            boolean done = o.done();
            if (done) Shapes.icon(Icons.CHECK, px + 4, cy + 3.5f, 7.5f).fill(SystemTheme.GOOD).glow(2, Gfx.alpha(SystemTheme.GOOD, 0.5f)).draw(g);
            else Shapes.icon(QuestText.icon(o.type()), px + 4, cy + 3.5f, 7.5f).fill(Gfx.alpha(SystemTheme.TEXT_SOFT, 0.9f)).draw(g);
            String count = o.progress() + " / " + o.required();
            float cw = SysFont.width(count, 7f, true);
            SysFont.text(g, SysFont.fit(o.label().getString(), 7.5f, false, pw - 16 - cw - 6), px + 12, cy, 7.5f, done ? SystemTheme.GOOD : SystemTheme.TEXT);
            SysFont.draw(g, count, px + pw, cy + 0.5f, 7f, done ? SystemTheme.GOOD : SystemTheme.TEXT_SOFT, true, 1f);
            Ui.bar(g, px + 12, cy + 9, pw - 12, 2f, o.fraction(), done ? 0xFF3FAF6A : SystemTheme.MP_A, done ? SystemTheme.GOOD : SystemTheme.MP_B);
            cy += 14;
        }
        cy += 2;

        // rewards
        List<QuestText.Reward> rewards = QuestText.rewards(q);
        if (!rewards.isEmpty()) {
            SysFont.spaced(g, Component.translatable("quest.beyondsolo.rewards").getString(), px, cy, 6f, SystemTheme.GOLD, true, 1.4f, 0f);
            cy += 9;
            float rx = px;
            for (QuestText.Reward r : rewards) {
                String label = QuestText.rewardLabel(r, 1f);
                float rw = SysFont.width(label, 6.5f, true) + 18;
                if (rx + rw > px + pw) break;
                Shapes.pill(rx, cy, rw, 11).fill(Gfx.alpha(r.color(), 0.16f)).border(0.75f, Gfx.alpha(r.color(), 0.6f)).draw(g);
                Shapes.icon(r.icon(), rx + 6.5f, cy + 5.5f, 7).fill(Gfx.mix(r.color(), 0xFFFFFFFF, 0.3f)).draw(g);
                SysFont.title(g, label, rx + 12.5f, SysFont.centerY(cy, 11, 6.5f), 6.5f, SystemTheme.TEXT);
                rx += rw + 3;
            }
            cy += 15;
        }
        if (!q.penalty().isNone()) {
            Shapes.icon(Icons.of(com.beyondsolo.notify.SystemNotification.Kind.ALERT), px + 4, cy + 3.5f, 7.5f).fill(SystemTheme.DANGER).draw(g);
            SysFont.text(g, SysFont.fit(Component.translatable("quests.beyondsolo.on_fail", QuestText.penaltyLine(q)).getString(), 7f, false, pw - 12),
                    px + 12, cy, 7f, Gfx.mix(SystemTheme.DANGER, 0xFFFFFFFF, 0.35f));
        }

        // buttons
        float by = y + h - 19;
        float right = px + pw;
        if (q.status() == Quest.Status.OFFERED) {
            String acc = Component.translatable("quests.beyondsolo.accept").getString();
            String dec = Component.translatable("quests.beyondsolo.decline").getString();
            float aw = SysFont.width(acc, 7.5f, true) + 22;
            float dw = SysFont.width(dec, 7.5f, true) + 18;
            acceptBtn = new float[]{right - aw, by, aw, 14};
            declineBtn = new float[]{right - aw - 5 - dw, by, dw, 14};
            button(g, acceptBtn, acc, SystemTheme.GOLD, true, mouseX, mouseY);
            button(g, declineBtn, dec, SystemTheme.TEXT_SOFT, false, mouseX, mouseY);
        } else if (ClientQuests.canAbandon(q)) {
            boolean confirm = Anim.now() < confirmUntil;
            String ab = Component.translatable(confirm ? "quests.beyondsolo.abandon.confirm" : "quests.beyondsolo.abandon").getString();
            float aw = SysFont.width(ab, 7.5f, true) + 18;
            abandonBtn = new float[]{right - aw, by, aw, 14};
            button(g, abandonBtn, ab, SystemTheme.DANGER, confirm, mouseX, mouseY);
        }
    }

    private static void button(GuiGraphics g, float[] r, String text, int color, boolean filled, int mouseX, int mouseY) {
        boolean hover = Ui.inside(mouseX, mouseY, r[0], r[1], r[2], r[3]);
        Shapes.pill(r[0], r[1], r[2], r[3]).fillV(Gfx.alpha(color, (filled ? 0.38f : 0.08f) + (hover ? 0.15f : 0)), Gfx.alpha(color, filled ? 0.18f : 0.04f))
                .border(0.9f, Gfx.alpha(color, hover ? 1f : 0.7f)).glow(hover ? 6 : 2, Gfx.alpha(color, hover ? 0.4f : 0.15f)).draw(g);
        SysFont.draw(g, text, r[0] + r[2] / 2f, SysFont.centerY(r[1], r[3], 7.5f), 7.5f, hover ? 0xFFFFFFFF : Gfx.mix(color, 0xFFFFFFFF, 0.45f), true, 0.5f);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (acceptBtn != null && Ui.inside(mouseX, mouseY, acceptBtn[0], acceptBtn[1], acceptBtn[2], acceptBtn[3])) {
            ClientQuests.accept(selected);
            SystemScreen.playClick();
            return true;
        }
        if (declineBtn != null && Ui.inside(mouseX, mouseY, declineBtn[0], declineBtn[1], declineBtn[2], declineBtn[3])) {
            ClientQuests.decline(selected);
            SystemScreen.playClick();
            return true;
        }
        if (abandonBtn != null && Ui.inside(mouseX, mouseY, abandonBtn[0], abandonBtn[1], abandonBtn[2], abandonBtn[3])) {
            if (Anim.now() < confirmUntil) {
                ClientQuests.abandon(selected);
                confirmUntil = -1;
            } else {
                confirmUntil = Anim.now() + 3;
            }
            SystemScreen.playClick();
            return true;
        }
        for (Object[] row : rows) {
            float rx = (float) row[1];
            float ry = (float) row[2];
            if (Ui.inside(mouseX, mouseY, rx, ry, LIST_W, ROW_H)) {
                if (!row[0].equals(selected)) {
                    selected = (UUID) row[0];
                    SystemScreen.playClick();
                }
                return true;
            }
        }
        return false;
    }

    /** Dev: select the first quest of a list (used by the UI test driver). */
    public static void select(UUID id) {
        selected = id;
    }
}
