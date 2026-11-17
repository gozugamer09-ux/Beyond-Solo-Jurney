package com.beyondsolo.client.key;

import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.screen.SystemScreen;
import com.beyondsolo.client.screen.SystemTab;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.key.DungeonKeys;
import com.beyondsolo.key.KeyRunData;
import com.beyondsolo.key.KeySeal;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The GATES page of the System window: the key ring (every dungeon key the player carries, a row for each kind) on
 * the left; on the right the chosen key, held in a lock of light, with the gate it opens and the TURN button (which
 * turns it without the hold and closes the window so the lock can be watched forming ahead), and under it the
 * keyed-run records by rank.
 */
public class GatesTab implements SystemTab {
    private static final float LIST_W = 150;
    private static final float ROW_H = 21;
    private static final float RECORDS_H = 58;

    /** The key kind last chosen ("rank:sigil:ascendant"). */
    private static String chosen = "";
    private static boolean pressQueued;

    private float scroll;
    private double chosenAt = -10;
    private final List<Object[]> hits = new ArrayList<>();

    /** One kind of key on the ring: where the first one sits, and how many there are. */
    private record Group(String id, ItemStack stack, int slot, int count) {
        PlayerRank rank() {
            return DungeonKeys.rankOf(stack);
        }

        int style() {
            return DungeonKeys.style(stack);
        }

        int color() {
            return 0xFF000000 | DungeonKeys.color(rank(), style());
        }
    }

    @Override
    public Component title() {
        return Component.translatable("screen.beyondsolo.tab.gates");
    }

    @Override
    public boolean isVisible() {
        return ClientSystemData.get().hasSystem();
    }

    private static List<Group> groups() {
        var player = Minecraft.getInstance().player;
        if (player == null) return List.of();
        Inventory inv = player.getInventory();
        Map<String, Group> out = new LinkedHashMap<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!DungeonKeys.isKey(s)) continue;
            KeySeal seal = DungeonKeys.sealOf(s);
            String id = DungeonKeys.rankOf(s).ordinal() + ":" + seal.sigil() + ":" + seal.ascendant();
            Group g = out.get(id);
            out.put(id, g == null ? new Group(id, s.copyWithCount(1), i, s.getCount()) : new Group(id, g.stack, g.slot, g.count + s.getCount()));
        }
        List<Group> list = new ArrayList<>(out.values());
        list.sort(Comparator.comparingInt((Group g) -> g.rank().ordinal()).thenComparingInt(Group::style).thenComparing(Group::id));
        return list;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        List<Group> groups = groups();
        Group pick = null;
        for (Group gr : groups) if (gr.id.equals(chosen)) pick = gr;
        if (pick == null && !groups.isEmpty()) {
            pick = groups.get(0);
            chosen = pick.id;
        }
        ring(g, groups, x, y, h, mouseX, mouseY);
        float dx = x + LIST_W + 8;
        float dw = x + w - dx;
        detail(g, pick, dx, y, dw, h - RECORDS_H - 6, mouseX, mouseY);
        records(g, dx, y + h - RECORDS_H, dw, RECORDS_H);
        if (pressQueued) {
            pressQueued = false;
            if (pick != null) turn(pick);
        }
    }

    /** The key ring: a row for each kind of key carried. */
    private void ring(GuiGraphics g, List<Group> groups, float x, float y, float h, int mouseX, int mouseY) {
        int total = 0;
        for (Group gr : groups) total += gr.count;
        SysFont.spaced(g, Component.translatable("key.beyondsolo.tab.ring").getString(), x + 2, y + 2, 5.8f, Gfx.alpha(SystemTheme.ACCENT, 0.9f), true, 1.4f, 0f);
        if (total > 0) {
            String n = String.valueOf(total);
            float nw = SysFont.width(n, 5.8f, true) + 14;
            Shapes.pill(x + LIST_W - nw, y, nw, 9).fill(Gfx.alpha(SystemTheme.GOLD, 0.12f)).border(0.6f, Gfx.alpha(SystemTheme.GOLD, 0.5f)).draw(g);
            Shapes.icon(Icons.KEY, x + LIST_W - nw + 5.5f, y + 4.5f, 6.5f).fill(SystemTheme.GOLD).draw(g);
            SysFont.title(g, n, x + LIST_W - nw + 10, SysFont.centerY(y, 9, 5.8f), 5.8f, 0xFFFFE7A6);
        }
        float ly = y + 14;
        KeyRunData d = KeyClient.data();
        float footer = d.lastRank >= 0 && d.lastTicks >= 0 ? 12 : 0;
        float lh = h - 14 - footer;
        if (groups.isEmpty()) {
            Ui.card(g, x, ly, LIST_W, lh, 7);
            float cy = ly + 30;
            Shapes.circle(x + LIST_W / 2f, cy, 13).fill(0).glow(10, Gfx.alpha(SystemTheme.ACCENT, 0.15f)).draw(g);
            Shapes.ring(x + LIST_W / 2f, cy, 13, 0.6f).fill(Gfx.alpha(SystemTheme.ACCENT, 0.4f)).dashes(14, 0.5f).rotation(Anim.loop(20f)).draw(g);
            Shapes.icon(Icons.KEY, x + LIST_W / 2f, cy, 15).fill(Gfx.alpha(SystemTheme.TEXT_DIM, 0.9f)).draw(g);
            SysFont.draw(g, Component.translatable("key.beyondsolo.tab.empty"), x + LIST_W / 2f, cy + 20, 6.6f, SystemTheme.TEXT_SOFT, true, 0.5f);
            float ty = cy + 32;
            for (SysFont.Line line : SysFont.wrap(Component.translatable("key.beyondsolo.tab.empty_hint"), 5.4f, false, LIST_W - 16)) {
                SysFont.draw(g, line, x + LIST_W / 2f, ty, SystemTheme.TEXT_DIM, 0.5f);
                ty += 5.4f * SysFont.LINE;
            }
        } else {
            int visible = (int) (lh / ROW_H);
            int max = Math.max(0, groups.size() - visible);
            scroll = Mth.clamp(scroll, 0, max);
            int rank = ClientSystemData.get().rank().tier();
            g.enableScissor((int) x - 2, (int) ly - 1, (int) (x + LIST_W + 4), (int) (ly + lh));
            for (int i = 0; i < groups.size(); i++) {
                float ry = ly + (i - scroll) * ROW_H;
                if (ry < ly - ROW_H || ry > ly + lh) continue;
                Group gr = groups.get(i);
                int c = gr.color();
                boolean on = gr.id.equals(chosen);
                boolean hover = Ui.inside(mouseX, mouseY, x, ry, LIST_W, ROW_H - 2) && mouseY < ly + lh;
                Ui.card(g, x, ry, LIST_W, ROW_H - 2, 5, c, on ? 0.6f : hover ? 0.3f : 0.08f);
                if (on) Shapes.pill(x + 1.5f, ry + 4, 2, ROW_H - 10).fill(c).glow(2, Gfx.alpha(c, 0.7f)).draw(g);
                if (gr.style() > 0) Shapes.circle(x + 13, ry + 9.5f, 8).fill(0).glow(5, Gfx.alpha(c, 0.35f)).draw(g);
                g.renderItem(gr.stack, (int) x + 5, (int) ry + 1);
                String name = SysFont.fit(gr.stack.getHoverName().getString(), 6.2f, true, LIST_W - 52);
                SysFont.title(g, name, x + 25, ry + 3, 6.2f, Gfx.mix(c, 0xFFFFFFFF, 0.45f));
                boolean heavy = gr.rank().tier() > rank + DungeonKeys.RANK_REACH;
                String sub = heavy ? Component.translatable("key.beyondsolo.tab.too_heavy", PlayerRank.values()[Math.max(0, gr.rank().tier() - DungeonKeys.RANK_REACH)].displayName()).getString()
                        : bossLine(gr).getString();
                SysFont.text(g, SysFont.fit(sub, 5.3f, false, LIST_W - 52), x + 25, ry + 11, 5.3f, heavy ? SystemTheme.DANGER : SystemTheme.TEXT_DIM);
                String n = Component.translatable("key.beyondsolo.tab.held", gr.count).getString();
                SysFont.title(g, n, x + LIST_W - 6 - SysFont.width(n, 6.4f, true), ry + 6, 6.4f, SystemTheme.TEXT_SOFT);
                hits.add(new Object[]{"key", x, ry, LIST_W, ROW_H - 2, gr.id});
            }
            g.disableScissor();
            if (max > 0) {
                float knob = Math.max(12, lh * visible / groups.size());
                float ky = ly + (lh - knob) * scroll / max;
                Shapes.pill(x + LIST_W + 2, ky, 2, knob).fill(Gfx.alpha(SystemTheme.ACCENT, 0.5f)).draw(g);
            }
            hits.add(new Object[]{"list", x, ly, LIST_W, lh, null});
        }
        if (footer > 0) {
            PlayerRank last = PlayerRank.values()[Mth.clamp(d.lastRank, 0, PlayerRank.values().length - 1)];
            String s = Component.translatable("key.beyondsolo.tab.last", last.displayName(), KeyClient.time(d.lastTicks)).getString();
            SysFont.text(g, SysFont.fit(s, 5.3f, false, LIST_W - 4), x + 2, y + h - 8, 5.3f, d.lastBest ? Gfx.alpha(SystemTheme.GOLD, 0.9f) : SystemTheme.TEXT_DIM);
        }
    }

    /** What the gate of a key ends with. */
    private static Component bossLine(Group gr) {
        KeySeal seal = DungeonKeys.sealOf(gr.stack);
        BossKind boss = seal.boss();
        if (boss != null && boss.livesAt(gr.rank())) {
            return Component.translatable(seal.ascendant() ? "key.beyondsolo.tab.boss_ascended" : "key.beyondsolo.tab.boss", boss.name(gr.rank()));
        }
        if (seal.ascendant()) return Component.translatable("key.beyondsolo.tip.ascendant");
        return Component.translatable("key.beyondsolo.tab.opens", gr.rank().displayName());
    }

    /** The chosen key: held in a lock of light, what it opens, and the TURN button. */
    private void detail(GuiGraphics g, @Nullable Group gr, float x, float y, float w, float h, int mouseX, int mouseY) {
        int c = gr == null ? SystemTheme.ACCENT : gr.color();
        float k = Anim.outCubic(Anim.progress(chosenAt, 0.3f));
        float old = Gfx.fade(k);
        g.pose().pushPose();
        g.pose().translate((1 - k) * 8, 0, 0);
        Ui.card(g, x, y, w, h, 9, c, gr == null ? 0.05f : 0.2f);

        // the lock with the key in it
        float ex = x + 34, ey = y + 34;
        boolean turning = KeyClient.menuTurning();
        float spin = Anim.loop(turning ? 1.6f : 14f);
        Shapes.circle(ex, ey, 27).fill(0).glow(14, Gfx.alpha(c, gr == null ? 0.08f : 0.2f + 0.15f * Anim.pulse(2.4f))).draw(g);
        Shapes.ring(ex, ey, 24, 0.8f).fill(Gfx.alpha(c, 0.55f)).dashes(20, 0.5f).rotation(spin).draw(g);
        Shapes.ring(ex, ey, 18.5f, 0.6f).fill(Gfx.alpha(c, 0.35f)).dashes(8, 0.62f).rotation(-spin * 1.5f).draw(g);
        Shapes.circle(ex, ey, 14).fillV(Gfx.alpha(c, 0.2f), Gfx.alpha(Gfx.shade(c, -0.5f), 0.1f)).border(0.6f, Gfx.alpha(c, 0.5f)).draw(g);
        if (gr == null) {
            Shapes.icon(Icons.KEY, ex, ey, 18).fill(Gfx.alpha(SystemTheme.TEXT_DIM, 0.8f)).draw(g);
        } else {
            float bob = (float) Math.sin(Anim.loop(2.6f) * Math.PI * 2) * 1.0f;
            g.pose().pushPose();
            g.pose().translate(ex, ey + bob, 150);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(turning ? 90 * turnedNow() : 0));
            g.pose().scale(2.2f, 2.2f, 1);
            g.renderItem(gr.stack, -8, -8);
            g.pose().popPose();
        }

        // name and what it opens
        float tx = x + 70, tw = w - 78;
        if (gr == null) {
            SysFont.title(g, SysFont.fit(Component.translatable("key.beyondsolo.tab.empty").getString(), 7f, true, tw), tx, y + 10, 7f, SystemTheme.TEXT_SOFT);
        } else {
            SysFont.title(g, SysFont.fit(gr.stack.getHoverName().getString(), 7.2f, true, tw), tx, y + 9, 7.2f, Gfx.mix(c, 0xFFFFFFFF, 0.5f));
            Ui.rankEmblem(g, gr.rank(), tx + 5, y + 24.5f, 4.6f, 0);
            SysFont.text(g, SysFont.fit(Component.translatable("key.beyondsolo.tab.opens", gr.rank().displayName()).getString(), 6f, false, tw - 14),
                    tx + 13, y + 22, 6f, SystemTheme.TEXT);
            KeySeal seal = DungeonKeys.sealOf(gr.stack);
            BossKind boss = seal.boss();
            Component bossText = boss != null && boss.livesAt(gr.rank())
                    ? Component.translatable(seal.ascendant() ? "key.beyondsolo.tab.boss_ascended" : "key.beyondsolo.tab.boss", boss.name(gr.rank()))
                    : Component.translatable(seal.ascendant() ? "key.beyondsolo.tip.ascendant" : "key.beyondsolo.tab.boss_random");
            int bossColor = seal.ascendant() ? SystemTheme.GOLD : boss != null ? 0xFFE59BFF : SystemTheme.TEXT_SOFT;
            Shapes.icon(seal.style() > 0 ? Icons.SKULL : Icons.QUESTION, tx + 5, y + 36, 7).fill(Gfx.alpha(bossColor, 0.9f)).draw(g);
            SysFont.text(g, SysFont.fit(bossText.getString(), 5.6f, false, tw - 14), tx + 13, y + 34, 5.6f, bossColor);
        }

        // how it works
        boolean chain = KeyClient.cleared() || KeyClient.rerunShowing();
        float hy = y + 64;
        for (SysFont.Line line : limit(SysFont.wrap(Component.translatable(chain ? "key.beyondsolo.tab.how_chain" : "key.beyondsolo.tab.how"), 5.3f, false, w - 18), 3)) {
            SysFont.draw(g, line, x + 9, hy, SystemTheme.TEXT_DIM, 0f);
            hy += 5.3f * SysFont.LINE;
        }

        // the button, and why it won't turn
        if (gr != null) {
            float bw = 74, bh = 16;
            float bx = x + w - 8 - bw, by = y + h - 8 - bh;
            DungeonKeys.Refusal why = KeyClient.refusal(gr.stack);
            if (why != null) {
                Component reason = Component.translatable(why.key(), gr.rank().displayName(),
                        PlayerRank.values()[Math.max(0, gr.rank().tier() - DungeonKeys.RANK_REACH)].displayName());
                float ry = by + 1;
                List<SysFont.Line> lines = limit(SysFont.wrap(reason, 5.3f, false, w - bw - 24), 2);
                ry += (2 - lines.size()) * 3.4f;
                for (SysFont.Line line : lines) {
                    SysFont.draw(g, line, x + 9, ry, SystemTheme.DANGER, 0f);
                    ry += 5.3f * SysFont.LINE;
                }
            } else if (chain) {
                int next = KeyClient.cleared() ? KeyClient.runChain() + 1 : KeyClient.rerunChain();
                if (next > 1) {
                    String bonus = Component.translatable("key.beyondsolo.hud.rerun_chain", next, Math.round(DungeonKeys.bonusFor(next) * 100 - 100)).getString();
                    Ui.chip(g, x + 9, by + 3, 10, bonus, 5.4f, SystemTheme.GOOD, true);
                }
            }
            button(g, bx, by, bw, bh, c, why == null && !turning, turning, mouseX, mouseY);
        }
        g.pose().popPose();
        Gfx.setAlpha(old);
    }

    /** How far a menu turn has gone (0..1), for the key in the lock. */
    private static float turnedNow() {
        return KeyClientEvents.turned(Anim.clamp01(Anim.since(KeyClient.menuTurnStarted()) * 20f / DungeonKeys.USE_TICKS));
    }

    private void button(GuiGraphics g, float x, float y, float w, float h, int color, boolean enabled, boolean turning, int mouseX, int mouseY) {
        boolean hover = enabled && Ui.inside(mouseX, mouseY, x, y, w, h);
        int c = enabled || turning ? color : SystemTheme.TEXT_DIM;
        float sheen = Anim.loop(2.8f) * 1.6f - 0.3f;
        Shapes.pill(x, y, w, h).fillV(Gfx.alpha(c, hover ? 0.42f : 0.26f), Gfx.alpha(Gfx.shade(c, -0.4f), hover ? 0.3f : 0.16f))
                .border(0.9f, Gfx.alpha(Gfx.mix(c, 0xFFFFFFFF, 0.25f), enabled ? 0.95f : 0.4f), Gfx.alpha(c, 0.3f))
                .glow(enabled ? 4 + (hover ? 3 : 0) : 0, Gfx.alpha(c, 0.3f))
                .sheen(enabled ? sheen : -2, 0.18f).draw(g);
        if (turning) {
            float p = Anim.clamp01(Anim.since(KeyClient.menuTurnStarted()) * 20f / DungeonKeys.USE_TICKS);
            Shapes.pill(x, y, Math.max(h, w * p), h).fill(Gfx.alpha(c, 0.35f)).glow(3, Gfx.alpha(c, 0.5f)).draw(g);
        }
        String label = Component.translatable(turning ? "key.beyondsolo.tab.turning" : "key.beyondsolo.tab.turn").getString();
        float iw = 10;
        float lw = SysFont.spacedWidth(label, 7f, true, 1.4f);
        float lx = x + (w - lw - iw) / 2f;
        Shapes.icon(Icons.KEY, lx + 4, y + h / 2f, 8).fill(enabled || turning ? SystemTheme.TEXT : SystemTheme.TEXT_SOFT).draw(g);
        SysFont.spaced(g, label, lx + iw, SysFont.centerY(y, h, 7f), 7f, enabled || turning ? SystemTheme.TEXT : SystemTheme.TEXT_SOFT, true, 1.4f, 0f);
        if (enabled) hits.add(new Object[]{"turn", x, y, w, h, null});
    }

    /** The keyed-run records, a cell for each rank. */
    private void records(GuiGraphics g, float x, float y, float w, float h) {
        KeyRunData d = KeyClient.data();
        Ui.card(g, x, y, w, h, 8);
        SysFont.spaced(g, Component.translatable("key.beyondsolo.tab.records").getString(), x + 8, y + 6, 5.6f, Gfx.alpha(SystemTheme.ACCENT, 0.9f), true, 1.3f, 0f);
        String totals = Component.translatable("key.beyondsolo.tab.totals", d.totalTurned(), d.totalCleared(), Math.max(1, d.longestChain)).getString();
        SysFont.draw(g, SysFont.fit(totals, 5f, false, w - 66), x + w - 8, y + 6.6f, 5f, SystemTheme.TEXT_DIM, false, 1f);
        float cw = (w - 16) / 3f, ch = 19;
        for (PlayerRank r : PlayerRank.values()) {
            int i = r.ordinal();
            float cx = x + 8 + (i % 3) * cw, cy = y + 16 + (i / 3) * (ch + 2);
            int t = r.tier();
            boolean any = d.cleared[t] > 0 || d.turned[t] > 0;
            int rc = SystemTheme.rank(r);
            Shapes.box(cx, cy, cw - 3, ch).radius(5).fill(Gfx.alpha(rc, any ? 0.1f : 0.03f)).border(0.6f, Gfx.alpha(rc, any ? 0.45f : 0.15f)).draw(g);
            Ui.rankEmblem(g, r, cx + 9, cy + ch / 2f, 5.2f, 0);
            if (!any) Shapes.circle(cx + 9, cy + ch / 2f, 6.5f).fill(0x90060E1E).draw(g);
            String a = Component.translatable("key.beyondsolo.tab.cleared", d.cleared[t]).getString();
            SysFont.text(g, SysFont.fit(a, 5.4f, true, cw - 24), cx + 18, cy + 3.5f, 5.4f, any ? SystemTheme.TEXT : SystemTheme.TEXT_DIM);
            boolean best = d.best[t] >= 0;
            String b = best ? Component.translatable("key.beyondsolo.tab.best", KeyClient.time(d.best[t])).getString()
                    : Component.translatable("key.beyondsolo.tab.no_best").getString();
            SysFont.text(g, SysFont.fit(b, 5f, false, cw - 24), cx + 18, cy + 10.5f, 5f, best ? Gfx.alpha(SystemTheme.GOLD, 0.95f) : SystemTheme.TEXT_DIM);
        }
    }

    private static <T> List<T> limit(List<T> list, int n) {
        return list.size() > n ? list.subList(0, n) : list;
    }

    private static void turn(Group gr) {
        if (KeyClient.refusal(gr.stack) != null || KeyClient.menuTurning()) return;
        KeyClient.turn(gr.slot);
        SystemScreen.playClick();
        // close the window: the lock forms a few steps ahead
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) mc.screen.onClose();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Object[] hit = hits.get(i);
            if (!Ui.inside(mouseX, mouseY, (float) hit[1], (float) hit[2], (float) hit[3], (float) hit[4])) continue;
            switch ((String) hit[0]) {
                case "key" -> {
                    String id = (String) hit[5];
                    if (!id.equals(chosen)) {
                        chosen = id;
                        chosenAt = Anim.now();
                        SystemScreen.playTick(1.6f);
                    }
                }
                case "turn" -> pressQueued = true;
                default -> {
                    continue;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        for (Object[] hit : hits) {
            if ("list".equals(hit[0]) && Ui.inside(mouseX, mouseY, (float) hit[1], (float) hit[2], (float) hit[3], (float) hit[4])) {
                scroll -= (float) scrollY;
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ UI test driver

    /** Chooses the n-th kind of key on the ring (driver: {@code gates <n>}). */
    public static void show(int index) {
        List<Group> groups = groups();
        if (index >= 0 && index < groups.size()) chosen = groups.get(index).id;
    }

    /** Presses TURN (driver: {@code gatesgo}). */
    public static void press() {
        pressQueued = true;
    }
}
