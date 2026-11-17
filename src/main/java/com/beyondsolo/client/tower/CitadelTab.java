package com.beyondsolo.client.tower;

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
import com.beyondsolo.network.TowerActionPayload;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.tower.RushTier;
import com.beyondsolo.tower.TowerAPI;
import com.beyondsolo.tower.TowerData;
import com.beyondsolo.tower.TowerFloors;
import com.beyondsolo.tower.TowerModule;
import com.beyondsolo.tower.TowerRun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The CITADEL page of the System window. On the left the castle: its hundred floors as ten wings, lit up to the
 * checkpoint, a gatekeeper on every tenth and the lord's crown at the top; the CLIMB button (from the checkpoint, or
 * the throne again once it is won), the Endless Spire with its best floor, and the hunter's records. On the right the
 * boss rush: a row for every tier with its best time, locked until the hunter's rank (or the Sigil) opens it.
 *
 * <p>Until the key is bound the page is the citadel's sealed gate, saying where the key is sold.</p>
 */
public class CitadelTab implements SystemTab {
    static final int CRIMSON = 0xFFFF2E3F;
    static final int DEEP = 0xFF4A0710;
    static final int VOID = 0xFFB27CFF;
    private static final float LEFT_W = 198;

    private static int pressQueued = -1;
    private static int pressArg;

    private final List<Object[]> hits = new ArrayList<>();

    @Override
    public Component title() {
        return Component.translatable("screen.beyondsolo.tab.citadel");
    }

    /** Shown once the System has told the hunter of the citadel, or they hold (or have bound) its key. */
    @Override
    public boolean isVisible() {
        if (!ClientSystemData.get().hasSystem()) return false;
        TowerData d = TowerClient.data();
        return d.bound || d.told || holdsKey();
    }

    private static boolean holdsKey() {
        var player = Minecraft.getInstance().player;
        return player != null && player.getInventory().contains(new net.minecraft.world.item.ItemStack(TowerModule.CITADEL_KEY.get()));
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        TowerData d = TowerClient.data();
        if (!d.bound) {
            sealed(g, x, y, w, h);
        } else {
            castle(g, d, x, y, LEFT_W, h, mouseX, mouseY);
            rush(g, d, x + LEFT_W + 8, y, w - LEFT_W - 8, h, mouseX, mouseY);
        }
        if (pressQueued >= 0) {
            int action = pressQueued;
            pressQueued = -1;
            send(action, pressArg);
        }
    }

    /** No key bound yet: the gate, shut. */
    private void sealed(GuiGraphics g, float x, float y, float w, float h) {
        Ui.card(g, x, y, w, h, 9, CRIMSON, 0.12f);
        float cx = x + w / 2f, cy = y + 52;
        float spin = Anim.loop(24f);
        Shapes.circle(cx, cy, 30).fill(0).glow(16, Gfx.alpha(CRIMSON, 0.18f + 0.08f * Anim.pulse(3f))).draw(g);
        Shapes.ring(cx, cy, 27, 0.8f).fill(Gfx.alpha(CRIMSON, 0.55f)).dashes(24, 0.5f).rotation(spin).draw(g);
        Shapes.ring(cx, cy, 21, 0.6f).fill(Gfx.alpha(CRIMSON, 0.35f)).dashes(8, 0.6f).rotation(-spin * 1.4f).draw(g);
        Shapes.circle(cx, cy, 16).fillV(Gfx.alpha(CRIMSON, 0.25f), Gfx.alpha(DEEP, 0.4f)).border(0.7f, Gfx.alpha(CRIMSON, 0.7f)).draw(g);
        Shapes.icon(Icons.LOCK, cx, cy, 16).fill(0xFFFFD6DA).glow(3, Gfx.alpha(CRIMSON, 0.6f)).draw(g);
        SysFont.spaced(g, Component.translatable("tower.beyondsolo.tab.name").getString(), cx, cy + 38, 9f, 0xFFFFE6DE, true, 2.6f, 0.5f);
        boolean holds = holdsKey();
        Component line = holds ? Component.translatable("tower.beyondsolo.tab.sealed_use")
                : Component.translatable("tower.beyondsolo.tab.sealed", TowerAPI.KEY_RANK.displayName());
        float ty = cy + 54;
        for (SysFont.Line l : SysFont.wrap(line, 6.2f, false, w - 80)) {
            SysFont.draw(g, l, cx, ty, holds ? SystemTheme.GOLD : SystemTheme.TEXT_SOFT, 0.5f);
            ty += 6.2f * SysFont.LINE;
        }
        float hy = ty + 6;
        for (SysFont.Line l : SysFont.wrap(Component.translatable("tower.beyondsolo.tab.about"), 5.4f, false, w - 90)) {
            SysFont.draw(g, l, cx, hy, SystemTheme.TEXT_DIM, 0.5f);
            hy += 5.4f * SysFont.LINE;
        }
    }

    /** The castle, the spire and the records. */
    private void castle(GuiGraphics g, TowerData d, float x, float y, float w, float h, int mouseX, int mouseY) {
        Ui.card(g, x, y, w, h, 9, CRIMSON, 0.14f);
        boolean inRun = TowerClient.inRun();
        // the header: the citadel's seal, its name, how far the hunter has come
        float sx = x + 17, sy = y + 16;
        Shapes.circle(sx, sy, 11).fillV(Gfx.alpha(CRIMSON, 0.35f), Gfx.alpha(DEEP, 0.55f)).border(0.7f, Gfx.alpha(CRIMSON, 0.85f))
                .glow(5, Gfx.alpha(CRIMSON, 0.3f)).draw(g);
        Shapes.ring(sx, sy, 13, 0.5f).fill(Gfx.alpha(CRIMSON, 0.6f)).dashes(14, 0.45f).rotation(Anim.loop(14f)).draw(g);
        Shapes.icon(Icons.CROWN, sx, sy, 12).fill(d.conquered() ? SystemTheme.GOLD : 0xFFFFE6DE).draw(g);
        SysFont.spaced(g, Component.translatable("tower.beyondsolo.tab.name").getString(), x + 34, y + 7, 7f, 0xFFFFE6DE, true, 1.8f, 0f);
        String standing = d.conquered() ? Component.translatable("tower.beyondsolo.tab.conquered", d.lordKills).getString()
                : Component.translatable("tower.beyondsolo.tab.progress", d.checkpoint, d.highest).getString();
        SysFont.text(g, SysFont.fit(standing, 5.4f, false, w - 42), x + 34, y + 17, 5.4f, d.conquered() ? SystemTheme.GOLD : SystemTheme.TEXT_SOFT);

        // the castle: ten wings of ten floors
        float ty = y + 34;
        SysFont.spaced(g, Component.translatable("tower.beyondsolo.tab.castle").getString(), x + 9, ty, 5.4f, Gfx.alpha(CRIMSON, 0.95f), true, 1.3f, 0f);
        float tx = x + 9, tw = w - 18;
        float segW = tw / 10f;
        float barY = ty + 11;
        for (int i = 0; i < 10; i++) {
            float bx = tx + i * segW;
            int top = (i + 1) * TowerFloors.KEEPER_EVERY;
            boolean done = d.checkpoint >= top;
            float part = done ? 1 : Math.max(0, Math.min(1, (d.highest - i * 10f) / 10f));
            boolean hover = Ui.inside(mouseX, mouseY, bx, barY - 4, segW, 14);
            Shapes.pill(bx + 0.8f, barY, segW - 1.6f, 5).fill(0x8C140406).border(0.5f, Gfx.alpha(CRIMSON, hover ? 0.6f : 0.25f)).draw(g);
            if (part > 0.01f) {
                Shapes.pill(bx + 0.8f, barY, Math.max(5, (segW - 1.6f) * part), 5).fillH(done ? DEEP : 0xFF5A1A20, done ? CRIMSON : 0xFF9A4048)
                        .glow(done ? 2 : 0, Gfx.alpha(CRIMSON, 0.5f)).draw(g);
            }
            // the gatekeeper at the end of each wing, the crown at the very top
            boolean lord = i == 9;
            float ix = bx + segW - 1.5f, iy = barY + 2.5f;
            Shapes.circle(ix, iy, 3.6f).fill(done ? (lord ? 0xFF3A2A08 : DEEP) : 0xFF140A0C).border(0.5f, done ? (lord ? SystemTheme.GOLD : CRIMSON) : Gfx.alpha(CRIMSON, 0.35f)).draw(g);
            Shapes.icon(lord ? Icons.CROWN : Icons.SKULL, ix, iy, 4.6f).fill(done ? (lord ? SystemTheme.GOLD : 0xFFFFD6DA) : SystemTheme.TEXT_DIM).draw(g);
            if (hover) {
                TowerFloors.Wing wing = TowerFloors.Wing.values()[i];
                String tip = Component.translatable("tower.beyondsolo.tab.wing", i * 10 + 1, top, wing.title()).getString();
                SysFont.text(g, SysFont.fit(tip, 5f, false, tw), tx, barY + 9, 5f, SystemTheme.TEXT);
            }
        }
        boolean hoverAny = Ui.inside(mouseX, mouseY, tx, barY - 4, tw, 14);
        if (!hoverAny) {
            int next = d.startFloor();
            String where = Component.translatable("tower.beyondsolo.tab.next", next, TowerFloors.wing(next).title()).getString();
            SysFont.text(g, SysFont.fit(where, 5f, false, tw), tx, barY + 9, 5f, SystemTheme.TEXT_DIM);
        }

        // CLIMB (or LEAVE while inside)
        float by = barY + 20, bh = 17;
        if (inRun) {
            button(g, x + 9, by, w - 18, bh, Component.translatable("tower.beyondsolo.tab.leave").getString(), Icons.PATH, SystemTheme.GOLD, true, mouseX, mouseY,
                    TowerActionPayload.LEAVE, 0);
        } else {
            String label = d.conquered() ? Component.translatable("tower.beyondsolo.tab.throne").getString()
                    : d.checkpoint <= 0 ? Component.translatable("tower.beyondsolo.tab.begin").getString()
                    : Component.translatable("tower.beyondsolo.tab.resume", d.startFloor()).getString();
            button(g, x + 9, by, w - 18, bh, label, Icons.FLAG, CRIMSON, true, mouseX, mouseY,
                    d.conquered() ? TowerActionPayload.THRONE : TowerActionPayload.CLIMB, 0);
        }

        // the Endless Spire
        float py = by + bh + 7, ph = 28;
        boolean open = d.conquered();
        Shapes.box(x + 9, py, w - 18, ph).radius(6).fill(Gfx.alpha(VOID, open ? 0.08f : 0.03f)).border(0.6f, Gfx.alpha(VOID, open ? 0.45f : 0.15f)).draw(g);
        Shapes.icon(open ? Icons.SPIRAL : Icons.LOCK, x + 19, py + ph / 2f, 9).fill(open ? Gfx.mix(VOID, 0xFFFFFFFF, 0.4f) : SystemTheme.TEXT_DIM).draw(g);
        SysFont.spaced(g, Component.translatable("tower.beyondsolo.spire").getString().toUpperCase(), x + 28, py + 5, 5.8f,
                open ? Gfx.mix(VOID, 0xFFFFFFFF, 0.45f) : SystemTheme.TEXT_DIM, true, 1.2f, 0f);
        String spire = !open ? Component.translatable("tower.beyondsolo.tab.spire_locked").getString()
                : d.spireBest > 0 ? Component.translatable("tower.beyondsolo.tab.spire_best", d.spireBest, d.spireRuns).getString()
                : Component.translatable("tower.beyondsolo.tab.spire_new").getString();
        SysFont.text(g, SysFont.fit(spire, 5.2f, false, w - 100), x + 28, py + 15, 5.2f, open && d.spireBest > 0 ? SystemTheme.GOLD : SystemTheme.TEXT_SOFT);
        if (open && !inRun) {
            button(g, x + w - 9 - 54, py + 6, 48, 16, Component.translatable("tower.beyondsolo.tab.enter").getString(), Icons.SPIRAL, VOID, true,
                    mouseX, mouseY, TowerActionPayload.ENDLESS, 0);
        }

        // records
        float ry = py + ph + 6, rh = h - (ry - y) - 8;
        float cw = (w - 18) / 4f;
        String[][] cells = {
                {Component.translatable("tower.beyondsolo.tab.rec.lord").getString(), String.valueOf(d.lordKills)},
                {Component.translatable("tower.beyondsolo.tab.rec.floors").getString(), String.valueOf(d.floorsCleared)},
                {Component.translatable("tower.beyondsolo.tab.rec.falls").getString(), String.valueOf(d.falls)},
                {Component.translatable("tower.beyondsolo.tab.rec.sigil").getString(),
                        Component.translatable(d.sigil ? "tower.beyondsolo.tab.rec.sigil_yes" : "tower.beyondsolo.tab.rec.sigil_no").getString()},
        };
        for (int i = 0; i < cells.length; i++) {
            float cx = x + 9 + i * cw;
            boolean sigil = i == 3 && d.sigil;
            Shapes.box(cx, ry, cw - 3, rh).radius(5).fill(Gfx.alpha(sigil ? SystemTheme.GOLD : CRIMSON, 0.07f))
                    .border(0.5f, Gfx.alpha(sigil ? SystemTheme.GOLD : CRIMSON, 0.3f)).draw(g);
            SysFont.draw(g, SysFont.fit(cells[i][0], 4.6f, true, cw - 6), cx + (cw - 3) / 2f, ry + 3, 4.6f, SystemTheme.TEXT_DIM, true, 0.5f);
            SysFont.draw(g, SysFont.fit(cells[i][1], 7f, true, cw - 6), cx + (cw - 3) / 2f, ry + rh - 10, 7f,
                    sigil ? SystemTheme.GOLD : SystemTheme.TEXT, true, 0.5f);
        }
    }

    /** The boss rush: a row for each tier. */
    private void rush(GuiGraphics g, TowerData d, float x, float y, float w, float h, int mouseX, int mouseY) {
        Ui.card(g, x, y, w, h, 9, CRIMSON, 0.08f);
        SysFont.spaced(g, Component.translatable("tower.beyondsolo.tab.rush").getString(), x + 9, y + 7, 6.2f, 0xFFFFE6DE, true, 1.6f, 0f);
        SysFont.text(g, SysFont.fit(Component.translatable("tower.beyondsolo.tab.rush_hint").getString(), 5f, false, w - 18), x + 9, y + 16, 5f, SystemTheme.TEXT_DIM);
        PlayerRank rank = ClientSystemData.get().rank();
        boolean inRun = TowerClient.inRun();
        float rowH = (h - 30) / RushTier.values().length;
        for (RushTier t : RushTier.values()) {
            float ry = y + 26 + t.ordinal() * rowH;
            boolean open = t.openTo(rank, d.sigil) && !inRun;
            boolean locked = !t.openTo(rank, d.sigil);
            int c = t.sovereign() ? CRIMSON : SystemTheme.rank(t.rank);
            boolean hover = open && Ui.inside(mouseX, mouseY, x + 6, ry, w - 12, rowH - 2);
            Shapes.box(x + 6, ry, w - 12, rowH - 2).radius(5).fill(Gfx.alpha(c, hover ? 0.2f : locked ? 0.03f : 0.08f))
                    .border(0.6f, Gfx.alpha(c, hover ? 0.8f : locked ? 0.15f : 0.4f)).glow(hover ? 4 : 0, Gfx.alpha(c, 0.3f)).draw(g);
            float ex = x + 15, ey = ry + (rowH - 2) / 2f;
            if (t.sovereign()) {
                Shapes.circle(ex, ey, 5.4f).fillV(Gfx.alpha(CRIMSON, 0.4f), Gfx.alpha(DEEP, 0.7f)).border(0.6f, CRIMSON).draw(g);
                Shapes.icon(Icons.CROWN, ex, ey, 7).fill(d.sigil ? SystemTheme.GOLD : SystemTheme.TEXT_DIM).draw(g);
            } else {
                Ui.rankEmblem(g, t.rank, ex, ey, 5.2f, 0);
            }
            if (locked) {
                Shapes.circle(ex, ey, 6.5f).fill(0x90060E1E).draw(g);
                Shapes.icon(Icons.LOCK, ex, ey, 6).fill(SystemTheme.TEXT_SOFT).draw(g);
            }
            String name = t.title().getString();
            SysFont.text(g, SysFont.fit(name, 5.8f, true, w - 80), x + 25, ry + 2.5f, 5.8f, locked ? SystemTheme.TEXT_DIM : Gfx.mix(c, 0xFFFFFFFF, 0.5f));
            String sub = locked ? (t.sovereign() ? Component.translatable("tower.beyondsolo.tab.rush_needs_sigil").getString()
                    : Component.translatable("tower.beyondsolo.tab.rush_needs_rank", PlayerRank.values()[Math.max(0, t.rank.ordinal() - 1)].displayName()).getString())
                    : Component.translatable("tower.beyondsolo.tab.rush_bouts", t.bouts(), d.rushClears[t.ordinal()]).getString();
            SysFont.text(g, SysFont.fit(sub, 4.8f, false, w - 80), x + 25, ry + rowH - 9.5f, 4.8f, locked ? Gfx.alpha(SystemTheme.DANGER, 0.8f) : SystemTheme.TEXT_DIM);
            long best = d.rushBest[t.ordinal()];
            String time = best >= 0 ? TowerRun.time(best) : "--:--";
            SysFont.draw(g, time, x + w - 12, SysFont.centerY(ry, rowH - 2, 6.4f), 6.4f, best >= 0 ? SystemTheme.GOLD : SystemTheme.TEXT_DIM, true, 1f);
            if (open) hits.add(new Object[]{"act", x + 6, ry, w - 12, rowH - 2, TowerActionPayload.RUSH, t.ordinal()});
        }
    }

    private void button(GuiGraphics g, float x, float y, float w, float h, String label, ResourceLocation icon, int color, boolean enabled,
                        int mouseX, int mouseY, int action, int arg) {
        boolean hover = enabled && Ui.inside(mouseX, mouseY, x, y, w, h);
        int c = enabled ? color : SystemTheme.TEXT_DIM;
        float sheen = Anim.loop(3.2f) * 1.6f - 0.3f;
        Shapes.pill(x, y, w, h).fillV(Gfx.alpha(c, hover ? 0.42f : 0.26f), Gfx.alpha(Gfx.shade(c, -0.4f), hover ? 0.3f : 0.16f))
                .border(0.9f, Gfx.alpha(Gfx.mix(c, 0xFFFFFFFF, 0.25f), enabled ? 0.95f : 0.4f), Gfx.alpha(c, 0.3f))
                .glow(enabled ? 4 + (hover ? 3 : 0) : 0, Gfx.alpha(c, 0.3f))
                .sheen(enabled ? sheen : -2, 0.16f).draw(g);
        float size = 6.4f;
        String text = SysFont.fit(label, size, true, w - 22);
        float lw = SysFont.spacedWidth(text, size, true, 1.1f);
        float lx = x + (w - lw - 10) / 2f;
        Shapes.icon(icon, lx + 3.5f, y + h / 2f, 7.5f).fill(enabled ? SystemTheme.TEXT : SystemTheme.TEXT_SOFT).draw(g);
        SysFont.spaced(g, text, lx + 10, SysFont.centerY(y, h, size), size, enabled ? SystemTheme.TEXT : SystemTheme.TEXT_SOFT, true, 1.1f, 0f);
        if (enabled) hits.add(new Object[]{"act", x, y, w, h, action, arg});
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Object[] hit = hits.get(i);
            if (!Ui.inside(mouseX, mouseY, (float) hit[1], (float) hit[2], (float) hit[3], (float) hit[4])) continue;
            if ("act".equals(hit[0])) {
                pressQueued = (int) hit[5];
                pressArg = (int) hit[6];
                return true;
            }
        }
        return false;
    }

    private static void send(int action, int arg) {
        PacketDistributor.sendToServer(new TowerActionPayload(action, arg));
        SystemScreen.playClick();
        // close the window: the pull (or the way out) plays on the screen
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) mc.screen.onClose();
    }

    // ------------------------------------------------------------------ UI test driver

    /** Presses a button of the page (driver: {@code citadel <action> [arg]}). */
    public static void press(int action, int arg) {
        pressQueued = action;
        pressArg = arg;
    }
}
