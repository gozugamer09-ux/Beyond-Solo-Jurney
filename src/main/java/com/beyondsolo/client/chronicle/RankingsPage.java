package com.beyondsolo.client.chronicle;

import com.beyondsolo.chronicle.ranking.Rankings;
import com.beyondsolo.chronicle.ranking.Rivals;
import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.screen.SystemScreen;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.tower.RushTier;
import com.beyondsolo.tower.TowerRun;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * RANKINGS: the boards down the left (hunters by power, the Citadel, the boss rush by tier, gates cleared) with where
 * the hunter stands and who is next to pass; the board itself on the right, the hunter's own row lit.
 */
final class RankingsPage implements ChronicleTab.Page {
    private static final float SIDE_W = 104;
    private static final float ROW_H = 16;
    private static final int MEDAL_GOLD = 0xFFFFD166;
    private static final int MEDAL_SILVER = 0xFFD5E0EC;
    private static final int MEDAL_BRONZE = 0xFFE59C62;
    /** The Citadel's crimson (the Sovereign rush). */
    private static final int CRIMSON = 0xFFFF2E3F;

    enum Board {
        HUNTERS(Rankings.HUNTERS, Icons.MEDAL), CITADEL(Rankings.CITADEL, ChronicleIcons.CASTLE), RUSH(Rankings.RUSH, Icons.HOURGLASS),
        GATES(Rankings.GATES, Icons.SPIRAL);

        final String id;
        final ResourceLocation icon;

        Board(String id, ResourceLocation icon) {
            this.id = id;
            this.icon = icon;
        }

        String label() {
            return Component.translatable("chronicle.beyondsolo.board." + id).getString();
        }

        String column() {
            return Component.translatable("chronicle.beyondsolo.board." + id + ".column").getString();
        }
    }

    /** One row as the server sent it. */
    record Row(String name, boolean rival, boolean me, PlayerRank rank, int playerClass, String guild, String title, long value, long value2) {
        static Row of(CompoundTag t) {
            PlayerRank[] ranks = PlayerRank.values();
            return new Row(t.getString("n"), t.getBoolean("rv"), t.getBoolean("me"), ranks[Mth.clamp(t.getInt("r"), 0, ranks.length - 1)],
                    t.getInt("c"), t.getString("g"), t.getString("t"), t.getLong("v"), t.getLong("w"));
        }
    }

    private static Board board = Board.HUNTERS;
    private static @Nullable RushTier tier;
    private static float scroll, scrollTarget;
    private static boolean reveal = true;

    private final List<ChronicleUi.Hit> hits = new ArrayList<>();
    private float listX, listY, listW, listH, maxScroll;

    @Override
    public void opened() {
        ChronicleClient.requestRankings(false);
        reveal = true;
    }

    private static RushTier tier() {
        if (tier == null) {
            PlayerRank r = ClientSystemData.get().rank();
            tier = RushTier.values()[Math.min(r.ordinal(), RushTier.values().length - 1)];
        }
        return tier;
    }

    /** Driver: {@code chronicle rankings <board> [tier]}. */
    static void show(String b, String t) {
        for (Board x : Board.values()) if (x.id.equals(b)) board = x;
        if (!t.isEmpty()) {
            RushTier rt = RushTier.byId(t);
            if (rt != null) tier = rt;
        }
        reveal = true;
        ChronicleClient.requestRankings(true);
    }

    static List<Row> rows(Board b) {
        CompoundTag boards = ChronicleClient.boards();
        List<Row> out = new ArrayList<>();
        if (boards == null) return out;
        ListTag list = b == Board.RUSH ? boards.getCompound(Rankings.RUSH).getList(tier().id(), Tag.TAG_COMPOUND) : boards.getList(b.id, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) out.add(Row.of(list.getCompound(i)));
        return out;
    }

    /** The hunter's place on the hunters' board and how many are on it ({0, n} when not on it). */
    static int[] myPlace() {
        List<Row> rows = rows(Board.HUNTERS);
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).me()) return new int[]{i + 1, rows.size()};
        return new int[]{0, rows.size()};
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, float x, float y, float w, float h, int mouseX, int mouseY) {
        hits.clear();
        List<Row> rows = rows(board);
        side(g, rows, x, y, SIDE_W, h, mouseX, mouseY);
        list(g, rows, x + SIDE_W + 6, y, w - SIDE_W - 6, h, mouseX, mouseY);
    }

    /** The boards, then where the hunter stands. */
    private void side(GuiGraphics g, List<Row> rows, float x, float y, float w, float h, int mouseX, int mouseY) {
        float by = y;
        for (Board b : Board.values()) {
            boolean sel = b == board;
            boolean hover = !sel && Ui.inside(mouseX, mouseY, x, by, w, 15);
            int c = SystemTheme.ACCENT;
            if (sel) {
                Shapes.box(x, by, w, 15).radius(5).fillH(Gfx.alpha(c, 0.32f), Gfx.alpha(SystemTheme.ACCENT_DEEP, 0.12f))
                        .border(0.75f, Gfx.alpha(c, 0.95f), Gfx.alpha(c, 0.35f)).glow(4, Gfx.alpha(c, 0.25f)).draw(g);
            } else {
                Shapes.box(x, by, w, 15).radius(5).fill(hover ? 0x1CFFFFFF : 0x0EFFFFFF).border(0.5f, hover ? Gfx.alpha(c, 0.55f) : 0x22FFFFFF).draw(g);
            }
            int tc = sel ? SystemTheme.TEXT : hover ? SystemTheme.ACCENT_BRIGHT : SystemTheme.TEXT_SOFT;
            Shapes.icon(b.icon, x + 9, by + 7.5f, 8).fill(sel ? SystemTheme.ACCENT_BRIGHT : tc).draw(g);
            SysFont.spaced(g, SysFont.fit(b.label(), 5.6f, true, w - 24), x + 17, SysFont.centerY(by, 15, 5.6f), 5.6f, tc, true, 0.8f, 0f);
            Board target = b;
            hits.add(new ChronicleUi.Hit(x, by, w, 15, () -> {
                if (board == target) return;
                board = target;
                reveal = true;
                ChronicleClient.requestRankings(false);
                SystemScreen.playClick();
            }));
            by += 18;
        }

        // where the hunter stands
        float sy = by + 1, sh = y + h - sy;
        Ui.card(g, x, sy, w, sh, 7, SystemTheme.GOLD, 0.06f);
        float cx = x + w / 2f;
        SysFont.spaced(g, Component.translatable("chronicle.beyondsolo.rankings.you").getString(), cx, sy + 5, 4.6f, SystemTheme.TEXT_DIM, true, 1.1f, 0.5f);
        CompoundTag boards = ChronicleClient.boards();
        if (boards == null) return;
        int me = -1;
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).me()) me = i;
        Row mine = me >= 0 ? rows.get(me) : null;
        boolean noTime = board == Board.RUSH && mine != null && mine.value() < 0;
        if (mine == null || noTime) {
            String s = Component.translatable(board == Board.RUSH ? "chronicle.beyondsolo.rankings.no_time" : "chronicle.beyondsolo.rankings.not_listed").getString();
            float ty = sy + 18;
            for (SysFont.Line l : SysFont.wrap(Component.literal(s), 5f, false, w - 12)) {
                SysFont.draw(g, l, cx, ty, SystemTheme.TEXT_SOFT, 0.5f);
                ty += 5f * SysFont.LINE;
            }
        } else {
            int place = me + 1;
            int pc = medal(place, SystemTheme.TEXT);
            String big = "#" + place;
            SysFont.draw(g, big, cx, sy + 13, 14f, pc, true, 0.5f);
            if (place <= 3) {
                float bw = SysFont.width(big, 14f, true);
                Shapes.icon(place == 1 ? Icons.CROWN : Icons.MEDAL, cx - bw / 2f - 7, sy + 18, 8).fill(pc).glow(2, Gfx.alpha(pc, 0.5f)).draw(g);
            }
            String of = Component.translatable("chronicle.beyondsolo.rankings.of", rows.size()).getString();
            SysFont.draw(g, of, cx, sy + 26, 4.8f, SystemTheme.TEXT_DIM, false, 0.5f);
            String value = board.column() + " " + value(mine);
            SysFont.draw(g, SysFont.fit(value, 5.6f, true, w - 10), cx, sy + 34, 5.6f, SystemTheme.GOLD, true, 0.5f);
            // who is next to pass
            float ny = sy + 44;
            if (me == 0) {
                SysFont.draw(g, SysFont.fit(Component.translatable("chronicle.beyondsolo.rankings.lead").getString(), 5f, false, w - 10), cx, ny, 5f,
                        SystemTheme.GOOD, false, 0.5f);
            } else {
                Row next = rows.get(me - 1);
                String who = Component.translatable("chronicle.beyondsolo.rankings.next", next.name()).getString();
                SysFont.draw(g, SysFont.fit(who, 5f, false, w - 10), cx, ny, 5f, SystemTheme.TEXT_SOFT, false, 0.5f);
                String gap = board == Board.RUSH
                        ? Component.translatable("chronicle.beyondsolo.rankings.gap_time", TowerRun.time(Math.max(20, mine.value() - next.value() + 20))).getString()
                        : Component.translatable("chronicle.beyondsolo.rankings.gap", TitlesPage.num(Math.max(1, next.value() - mine.value() + 1))).getString();
                SysFont.draw(g, SysFont.fit(gap, 4.8f, false, w - 10), cx, ny + 7, 4.8f, SystemTheme.TEXT_DIM, false, 0.5f);
            }
        }
        String day = Component.translatable("chronicle.beyondsolo.rankings.day", boards.getLong("day") + 1).getString();
        SysFont.draw(g, SysFont.fit(day, 4.4f, false, w - 10), cx, sy + sh - 8, 4.4f, Gfx.alpha(SystemTheme.TEXT_DIM, 0.8f), false, 0.5f);
    }

    private static int medal(int place, int otherwise) {
        return switch (place) {
            case 1 -> MEDAL_GOLD;
            case 2 -> MEDAL_SILVER;
            case 3 -> MEDAL_BRONZE;
            default -> otherwise;
        };
    }

    private static String value(Row r) {
        return switch (board) {
            case RUSH -> r.value() < 0 ? "--:--" : TowerRun.time(r.value());
            default -> TitlesPage.num(r.value());
        };
    }

    /** The board: a header (the rush's tiers), then a row a hunter. */
    private void list(GuiGraphics g, List<Row> rows, float x, float y, float w, float h, int mouseX, int mouseY) {
        Ui.card(g, x, y, w, h, 8);
        float hy = y + 4, hh = 11;
        if (board == Board.RUSH) {
            float tx = x + 6;
            for (RushTier t : RushTier.values()) {
                boolean sel = t == tier();
                int c = t.sovereign() ? CRIMSON : SystemTheme.rank(t.rank);
                String label = t.sovereign() ? "" : t.rank.name();
                float tw = ChronicleUi.pillWidth(hh, label, t.sovereign(), 5.4f);
                boolean hover = !sel && Ui.inside(mouseX, mouseY, tx, hy, tw, hh);
                ChronicleUi.pill(g, tx, hy, hh, label, t.sovereign() ? Icons.CROWN : null, c, sel, hover, 5.4f);
                hits.add(new ChronicleUi.Hit(tx, hy, tw, hh, () -> {
                    if (tier == t) return;
                    tier = t;
                    reveal = true;
                    SystemScreen.playTick(1.6f);
                }));
                tx += tw + 2.5f;
            }
            String name = tier().title().getString();
            float room = x + w - 8 - tx - 4;
            if (room > 20) SysFont.draw(g, SysFont.fit(name, 5f, true, room), x + w - 8, SysFont.centerY(hy, hh, 5f), 5f, SystemTheme.TEXT_SOFT, true, 1f);
        } else {
            SysFont.spaced(g, Component.translatable("chronicle.beyondsolo.board.hunter").getString(), x + 26, SysFont.centerY(hy, hh, 4.6f), 4.6f,
                    SystemTheme.TEXT_DIM, true, 1.1f, 0f);
            SysFont.spaced(g, board.column(), x + w - 8, SysFont.centerY(hy, hh, 4.6f), 4.6f, SystemTheme.TEXT_DIM, true, 1.1f, 1f);
        }
        Ui.divider(g, x + 6, hy + hh + 2, w - 12, Gfx.alpha(SystemTheme.ACCENT, 0.3f));

        listX = x + 3;
        listY = hy + hh + 4;
        listW = w - 6;
        listH = y + h - 3 - listY;
        if (ChronicleClient.boards() == null) {
            float cx = x + w / 2f, cy = listY + listH / 2f - 6;
            Shapes.ring(cx, cy, 7, 1f).fill(Gfx.alpha(SystemTheme.ACCENT, 0.8f)).dashes(3, 0.6f).rotation(Anim.loop(1.2f)).draw(g);
            SysFont.draw(g, Component.translatable("chronicle.beyondsolo.rankings.loading").getString(), cx, cy + 12, 5.2f, SystemTheme.TEXT_SOFT, false, 0.5f);
            return;
        }
        maxScroll = Math.max(0, rows.size() * ROW_H - listH + 2);
        if (reveal) {
            reveal = false;
            scroll = scrollTarget = 0;
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).me()) scroll = scrollTarget = Mth.clamp(i * ROW_H - listH / 2f + ROW_H / 2f, 0, maxScroll);
            }
        }
        scrollTarget = Mth.clamp(scrollTarget, 0, maxScroll);
        scroll = Anim.approach(scroll, scrollTarget, 18f);
        boolean inList = Ui.inside(mouseX, mouseY, listX, listY, listW, listH);
        Shapes.flush(g);
        g.enableScissor((int) listX, (int) listY, (int) Math.ceil(listX + listW), (int) Math.ceil(listY + listH));
        float rw = listW - (maxScroll > 0 ? 6 : 2);
        for (int i = 0; i < rows.size(); i++) {
            float ry = listY + 1 + i * ROW_H - scroll;
            if (ry + ROW_H < listY || ry > listY + listH) continue;
            row(g, rows.get(i), i + 1, listX + 1, ry, rw, inList && Ui.inside(mouseX, mouseY, listX + 1, ry, rw, ROW_H - 1.5f));
        }
        Shapes.flush(g);
        g.disableScissor();
        ChronicleUi.scrollbar(g, listX + listW - 3, listY + 1, listH - 2, scroll, maxScroll, SystemTheme.ACCENT);
        if (rows.isEmpty()) {
            SysFont.draw(g, Component.translatable("chronicle.beyondsolo.rankings.empty").getString(), x + w / 2f, listY + 20, 5.2f, SystemTheme.TEXT_DIM, false, 0.5f);
        }
    }

    private static void row(GuiGraphics g, Row r, int place, float x, float y, float w, boolean hover) {
        float h = ROW_H - 1.5f;
        boolean none = board == Board.RUSH && r.value() < 0;
        if (r.me()) {
            float glow = 0.5f + 0.5f * Anim.pulse(2.6f);
            Shapes.box(x, y, w, h).radius(5).fillH(Gfx.alpha(SystemTheme.ACCENT, 0.26f), Gfx.alpha(SystemTheme.ACCENT_DEEP, 0.08f))
                    .border(0.7f, Gfx.alpha(SystemTheme.ACCENT, 0.9f), Gfx.alpha(SystemTheme.ACCENT, 0.3f)).glow(3 + 2 * glow, Gfx.alpha(SystemTheme.ACCENT, 0.25f)).draw(g);
        } else if (hover) {
            Shapes.box(x, y, w, h).radius(5).fill(0x12FFFFFF).border(0.5f, 0x22FFFFFF).draw(g);
        } else if (place % 2 == 0) {
            Shapes.box(x, y, w, h).radius(5).fill(0x08FFFFFF).draw(g);
        }
        float cy = y + h / 2f;
        // the place: a medal for the first three
        int mc = medal(place, 0);
        if (mc != 0 && !none) {
            Shapes.circle(x + 9, cy, 5.6f).fillV(Gfx.alpha(mc, 0.45f), Gfx.alpha(Gfx.shade(mc, -0.5f), 0.55f)).border(0.6f, mc).glow(2.5f, Gfx.alpha(mc, 0.35f)).draw(g);
            SysFont.draw(g, String.valueOf(place), x + 9, SysFont.centerY(cy - 5.6f, 11.2f, 5.6f), 5.6f, 0xFFFFFFFF, true, 0.5f);
        } else {
            SysFont.draw(g, none ? "-" : String.valueOf(place), x + 9, SysFont.centerY(y, h, 5.6f), 5.6f, r.me() ? SystemTheme.TEXT : SystemTheme.TEXT_DIM, true, 0.5f);
        }
        Ui.rankEmblem(g, r.rank(), x + 23, cy, 4.4f, 0);

        // the name, and the guild (a rival) or the title worn (a player)
        float nx = x + 31;
        float vw = 40;
        float room = w - (nx - x) - vw - 14;
        String name = SysFont.fit(r.name(), 6f, true, room - (r.me() ? 16 : 0));
        int nameColor = r.me() ? SystemTheme.TEXT : none ? SystemTheme.TEXT_DIM : r.rival() ? Gfx.mix(SystemTheme.TEXT_SOFT, SystemTheme.TEXT, 0.4f) : SystemTheme.TEXT;
        SysFont.draw(g, name, nx, y + 1.8f, 6f, nameColor, true, 0f);
        if (r.me()) {
            float nw = SysFont.width(name, 6f, true);
            String you = Component.translatable("chronicle.beyondsolo.rankings.you_short").getString();
            float yw = SysFont.width(you, 4.2f, true) + 5;
            Shapes.pill(nx + nw + 3, y + 1.6f, yw, 6.4f).fill(Gfx.alpha(SystemTheme.ACCENT, 0.35f)).border(0.5f, SystemTheme.ACCENT).draw(g);
            SysFont.draw(g, you, nx + nw + 3 + yw / 2f, SysFont.centerY(y + 1.6f, 6.4f, 4.2f), 4.2f, SystemTheme.TEXT, true, 0.5f);
        }
        String sub;
        int subColor = SystemTheme.TEXT_DIM;
        if (r.rival()) {
            sub = Rivals.guildName(r.guild()).getString();
        } else if (!r.title().isEmpty()) {
            sub = Component.translatable(r.title()).getString();
            subColor = Gfx.alpha(ChronicleClient.titleColor(r.title()), 0.9f);
        } else {
            sub = Component.translatable("chronicle.beyondsolo.rankings.player").getString();
        }
        SysFont.draw(g, SysFont.fit(sub, 4.6f, false, room), nx, y + h - 6.2f, 4.6f, subColor, false, 0f);

        // the class, then the number
        PlayerClass[] classes = PlayerClass.values();
        PlayerClass pc = classes[Mth.clamp(r.playerClass(), 0, classes.length - 1)];
        Shapes.icon(Icons.of(pc), x + w - vw - 8, cy, 7).fill(Gfx.alpha(Gfx.opaque(pc.color()), r.me() ? 1f : 0.75f)).draw(g);
        String v = value(r);
        int vc = none ? SystemTheme.TEXT_DIM : r.me() ? SystemTheme.GOLD : SystemTheme.TEXT;
        if (board == Board.CITADEL && r.value2() > 0) {
            SysFont.draw(g, v, x + w - 4, y + 1.6f, 6f, vc, true, 1f);
            String spire = Component.translatable("chronicle.beyondsolo.board.citadel.spire", r.value2()).getString();
            SysFont.draw(g, SysFont.fit(spire, 4.2f, false, vw), x + w - 4, y + h - 5.8f, 4.2f, Gfx.alpha(0xFFB27CFF, 0.95f), false, 1f);
        } else {
            SysFont.draw(g, v, x + w - 4, SysFont.centerY(y, h, 6.2f), 6.2f, vc, true, 1f);
        }
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
