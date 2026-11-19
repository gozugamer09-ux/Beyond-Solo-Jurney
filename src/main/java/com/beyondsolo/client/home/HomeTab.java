package com.beyondsolo.client.home;

import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.economy.EconomyClient;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.screen.SystemScreen;
import com.beyondsolo.client.screen.SystemTab;
import com.beyondsolo.home.HomeTier;
import com.beyondsolo.network.HomeActionPayload;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The HOME page of the System window. On the left the hunter's residence: what they own and where they are, the way
 * home (or back out, or the wait before they can go again), the next part of the home with what it asks for and the
 * button that buys it, the last answer, and four facts of the home (their rest, their mother, their best at the
 * dummies, their trophies). On the right the four parts of the home with what each holds, and the homes of their party
 * that they may visit.
 */
public class HomeTab implements SystemTab {
    static final int NIGHT = HomeHud.NIGHT;
    static final int DEEP = 0xFF0B2440;
    private static final float LEFT_W = 204;
    private static final float OUTCOME_SECONDS = 8f;

    private static int pressQueued = -1;
    @Nullable private static UUID pressHost;

    private final List<Object[]> hits = new ArrayList<>();

    @Override
    public Component title() {
        return Component.translatable("screen.beyondsolo.tab.home");
    }

    @Override
    public boolean batched() {
        return true;
    }

    @Override
    public boolean isVisible() {
        return ClientSystemData.get().hasSystem() && HomeClient.state().enabled;
    }

    @Override
    public void onOpened() {
        HomeClient.send(HomeActionPayload.REFRESH);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        HomeClient.State s = HomeClient.state();
        residence(g, s, x, y, LEFT_W, h, mouseX, mouseY);
        floors(g, s, x + LEFT_W + 8, y, w - LEFT_W - 8, h, mouseX, mouseY);
        if (pressQueued >= 0) {
            int action = pressQueued;
            UUID host = pressHost;
            pressQueued = -1;
            pressHost = null;
            send(action, host);
        }
    }

    /** The residence: where the hunter is, the way home, the next part, the facts. */
    private void residence(GuiGraphics g, HomeClient.State s, float x, float y, float w, float h, int mouseX, int mouseY) {
        Ui.card(g, x, y, w, h, 9, NIGHT, 0.12f);
        // the seal, the name, where they are
        float sx = x + 17, sy = y + 16;
        Shapes.circle(sx, sy, 11).fillV(Gfx.alpha(NIGHT, 0.32f), Gfx.alpha(DEEP, 0.6f)).border(0.7f, Gfx.alpha(NIGHT, 0.85f))
                .glow(5, Gfx.alpha(NIGHT, 0.28f)).draw(g);
        Shapes.ring(sx, sy, 13, 0.5f).fill(Gfx.alpha(NIGHT, 0.55f)).dashes(12, 0.4f).rotation(Anim.loop(18f)).draw(g);
        Shapes.icon(Icons.HOME, sx, sy, 12).fill(s.owns() ? 0xFFEAF6FF : SystemTheme.TEXT_SOFT).draw(g);
        SysFont.spaced(g, t("tab.name").toUpperCase(Locale.ROOT), x + 34, y + 7, 7f, 0xFFE6F3FF, true, 1.8f, 0f);
        String where;
        int whereColor = SystemTheme.TEXT_SOFT;
        if (!s.guestOf.isEmpty()) {
            where = s.owns() ? t("tab.where.guest", s.tier.displayName(), s.guestOf) : t("tab.where.guest_only", s.guestOf);
        } else if (!s.owns()) {
            where = t("tab.where.none");
        } else if (s.home) {
            where = t("tab.where.home", s.tier.displayName());
            whereColor = Gfx.mix(NIGHT, 0xFFFFFFFF, 0.35f);
        } else {
            where = t("tab.where.out", s.tier.displayName());
        }
        SysFont.text(g, SysFont.fit(where, 5.4f, false, w - 42), x + 34, y + 17, 5.4f, whereColor);

        // the builders, while a part is going up
        float top = y + 33;
        if (s.building >= 0) {
            String b = t("tab.building", (int) Math.min(99, Math.floor(s.building * 100)));
            SysFont.text(g, SysFont.fit(b, 4.8f, false, w - 18), x + 9, top - 3, 4.8f, SystemTheme.GOLD);
            Ui.bar(g, x + 9, top + 4, w - 18, 1.8f, s.building, Gfx.shade(SystemTheme.GOLD, -0.3f), SystemTheme.GOLD);
            top += 8;
        }

        // the way home, the way out, or the wait
        float bh = 17;
        if (!s.world) {
            note(g, x + 9, top, w - 18, bh, Icons.LOCK, t("tab.no_world"), SystemTheme.TEXT_DIM);
        } else if (HomeClient.channeling()) {
            button(g, x + 9, top, w - 18, bh, t("tab.stay"), Icons.X, SystemTheme.DANGER, true, mouseX, mouseY, HomeActionPayload.CANCEL, null);
            Ui.bar(g, x + 20, top + bh - 3.2f, w - 40, 1.4f, HomeClient.channel(), Gfx.shade(NIGHT, -0.2f), Gfx.mix(NIGHT, 0xFFFFFFFF, 0.4f));
        } else if (s.away) {
            button(g, x + 9, top, w - 18, bh, t("tab.leave"), Icons.PATH, SystemTheme.GOLD, true, mouseX, mouseY, HomeActionPayload.LEAVE, null);
        } else if (s.owns()) {
            long wait = HomeClient.readyIn();
            String label = wait > 0 ? t("tab.wait", clock(wait)) : t("tab.go");
            button(g, x + 9, top, w - 18, bh, label, wait > 0 ? Icons.HOURGLASS : Icons.HOME, NIGHT, wait <= 0, mouseX, mouseY, HomeActionPayload.GO, null);
        } else {
            note(g, x + 9, top, w - 18, bh, Icons.CITY, t("tab.pitch"), SystemTheme.TEXT_SOFT);
        }

        // the next part of the home
        float ny = top + bh + 6, nh = 50;
        next(g, s, x + 9, ny, w - 18, nh, mouseX, mouseY);

        // the last answer, or how going home works
        float oy = ny + nh + 5;
        float since = HomeClient.sinceOutcome();
        Component outcome = HomeClient.outcome();
        if (since < OUTCOME_SECONDS && !outcome.getString().isEmpty()) {
            float k = 1 - Anim.window(since, OUTCOME_SECONDS - 1.2f, OUTCOME_SECONDS);
            float old = Gfx.fade(k);
            int c = HomeClient.outcomeOk() ? SystemTheme.GOOD : SystemTheme.DANGER;
            Shapes.icon(HomeClient.outcomeOk() ? Icons.CHECK : Icons.X, x + 13, oy + 2.6f, 5.5f).fill(c).draw(g);
            SysFont.text(g, SysFont.fit(outcome.getString(), 5f, false, w - 26), x + 18, oy, 5f, Gfx.mix(c, 0xFFFFFFFF, 0.45f));
            Gfx.setAlpha(old);
        } else {
            String hint = s.owns() ? t("tab.hint", Math.max(0, s.channel / 20)) : t("tab.hint_none");
            SysFont.text(g, SysFont.fit(hint, 4.8f, false, w - 18), x + 9, oy, 4.8f, SystemTheme.TEXT_DIM);
        }

        // the four facts
        float fy = oy + 9, fh = y + h - 8 - fy;
        facts(g, s, x + 9, fy, w - 18, fh);
    }

    /** The next part of the home: what it is, what it holds, what it asks, and the button. */
    private void next(GuiGraphics g, HomeClient.State s, float x, float y, float w, float h, int mouseX, int mouseY) {
        HomeTier next = s.tier.next();
        if (next == null) {
            Shapes.box(x, y, w, h).radius(6).fill(Gfx.alpha(SystemTheme.GOLD, 0.07f)).border(0.6f, Gfx.alpha(SystemTheme.GOLD, 0.45f))
                    .glow(3, Gfx.alpha(SystemTheme.GOLD, 0.15f)).draw(g);
            Shapes.icon(Icons.CROWN, x + 15, y + h / 2f, 12).fill(SystemTheme.GOLD).glow(3, Gfx.alpha(SystemTheme.GOLD, 0.5f)).draw(g);
            SysFont.spaced(g, t("tab.complete").toUpperCase(Locale.ROOT), x + 28, y + h / 2f - 9, 6.4f, SystemTheme.GOLD, true, 1.3f, 0f);
            SysFont.text(g, SysFont.fit(t("tab.complete_line"), 5f, false, w - 34), x + 28, y + h / 2f + 1, 5f, SystemTheme.TEXT_SOFT);
            return;
        }
        int i = next.ordinal();
        SystemData sd = ClientSystemData.get();
        int level = s.levels[i];
        PlayerRank rank = PlayerRank.values()[Math.max(0, Math.min(PlayerRank.values().length - 1, s.ranks[i]))];
        long gold = s.gold[i];
        boolean lvOk = sd.level() >= level, rankOk = sd.rank().tier() >= rank.tier(), goldOk = EconomyClient.gold() >= gold;
        boolean can = lvOk && rankOk && goldOk && s.world && !HomeClient.channeling();
        Shapes.box(x, y, w, h).radius(6).fill(Gfx.alpha(NIGHT, 0.06f)).border(0.6f, Gfx.alpha(NIGHT, can ? 0.5f + 0.2f * Anim.pulse(2.4f) : 0.25f)).draw(g);
        SysFont.spaced(g, t(s.owns() ? "tab.next" : "tab.first").toUpperCase(Locale.ROOT), x + 7, y + 5, 4.8f, Gfx.alpha(NIGHT, 0.95f), true, 1.2f, 0f);
        SysFont.text(g, SysFont.fit(next.displayName().getString(), 7f, true, w - 76), x + 7, y + 12.5f, 7f, SystemTheme.TEXT);
        SysFont.text(g, SysFont.fit(t("tier." + next.id() + ".rooms"), 4.8f, false, w - 14), x + 7, y + 23, 4.8f, SystemTheme.TEXT_SOFT);
        // what it asks
        float cx = x + 7, cy = y + h - 12;
        cx += Ui.chip(g, cx, cy, 8, t("tab.req.level", level), 4.4f, lvOk ? SystemTheme.GOOD : SystemTheme.DANGER, false) + 3;
        cx += Ui.chip(g, cx, cy, 8, t("tab.req.rank", rank.name()), 4.4f, rankOk ? SystemTheme.GOOD : SystemTheme.DANGER, false) + 3;
        Ui.chip(g, cx, cy, 8, t("tab.req.gold", NumberFormat.getIntegerInstance(Locale.ROOT).format(gold)), 4.4f,
                goldOk ? SystemTheme.GOLD : SystemTheme.DANGER, false);
        // and the button
        String label = t(next == HomeTier.STUDIO ? "tab.buy" : "tab.upgrade");
        button(g, x + w - 62, y + 7, 56, 15, label, Icons.COINS, SystemTheme.GOLD, can, mouseX, mouseY, HomeActionPayload.BUY, null);
    }

    /** Rest, mother, the dummies, the trophies. */
    private void facts(GuiGraphics g, HomeClient.State s, float x, float y, float w, float h) {
        float cw = w / 4f;
        int trophies = 0;
        for (int n : s.trophies) if (n > 0) trophies++;
        String rest = !s.owns() ? "-" : t(s.rested ? "tab.fact.rested" : "tab.fact.bed_ready");
        String mother;
        if (s.mother) mother = t("tab.fact.mother_here");
        else if (!s.story) mother = "-";
        else if (!s.motherAwake) mother = t("tab.fact.mother_asleep");
        else if (!s.tier.atLeast(HomeTier.FAMILY)) mother = t("tab.fact.mother_room");
        else mother = t("tab.fact.mother_coming");
        String best = s.best > 0 ? HomePlates.number(s.best) : "-";
        String hall = s.trophies.length == 0 ? "-" : trophies + "/" + s.trophies.length;
        Object[][] cells = {
                {Icons.BED, t("tab.fact.rest"), rest, s.rested ? NIGHT : SystemTheme.TEXT},
                {Icons.HEART, t("tab.fact.mother"), mother, s.mother ? 0xFFFFB3C7 : SystemTheme.TEXT},
                {Icons.FLAME, t("tab.fact.best"), best, s.best > 0 ? 0xFFFFC86E : SystemTheme.TEXT},
                {Icons.CROWN, t("tab.fact.trophies"), hall, trophies > 0 ? SystemTheme.GOLD : SystemTheme.TEXT},
        };
        for (int i = 0; i < cells.length; i++) {
            float cx = x + i * cw;
            int c = (int) cells[i][3];
            boolean lit = c != SystemTheme.TEXT;
            Shapes.box(cx, y, cw - 3, h).radius(5).fill(Gfx.alpha(lit ? c : NIGHT, lit ? 0.08f : 0.05f))
                    .border(0.5f, Gfx.alpha(lit ? c : NIGHT, lit ? 0.4f : 0.22f)).draw(g);
            float mid = cx + (cw - 3) / 2f;
            Shapes.icon((ResourceLocation) cells[i][0], cx + 7, y + 6.5f, 6.5f).fill(Gfx.alpha(lit ? c : SystemTheme.TEXT_DIM, 0.95f)).draw(g);
            SysFont.draw(g, SysFont.fit((String) cells[i][1], 4.3f, true, cw - 18), cx + 12, y + 4.2f, 4.3f, SystemTheme.TEXT_DIM, true, 0f);
            SysFont.draw(g, SysFont.fit((String) cells[i][2], 5.6f, true, cw - 8), mid, y + h - 9.5f, 5.6f, c, true, 0.5f);
        }
    }

    /** The four parts of the home, and the party's homes. */
    private void floors(GuiGraphics g, HomeClient.State s, float x, float y, float w, float h, int mouseX, int mouseY) {
        Ui.card(g, x, y, w, h, 9, NIGHT, 0.07f);
        SysFont.spaced(g, t("tab.floors").toUpperCase(Locale.ROOT), x + 9, y + 7, 6.2f, 0xFFE6F3FF, true, 1.6f, 0f);
        HomeTier[] tiers = {HomeTier.STUDIO, HomeTier.FAMILY, HomeTier.HALL, HomeTier.PENTHOUSE};
        ResourceLocation[] icons = {Icons.BED, Icons.HEART, Icons.WEIGHT, Icons.CROWN};
        float rowH = 21;
        HomeTier next = s.tier.next();
        for (int i = 0; i < tiers.length; i++) {
            HomeTier t = tiers[i];
            float ry = y + 18 + i * (rowH + 1);
            boolean owned = s.tier.atLeast(t);
            boolean upcoming = t == next;
            int c = owned ? NIGHT : upcoming ? Gfx.mix(NIGHT, SystemTheme.TEXT_DIM, 0.4f) : SystemTheme.TEXT_DIM;
            float border = owned ? 0.55f : upcoming ? 0.3f + 0.25f * Anim.pulse(2.4f) : 0.14f;
            Shapes.box(x + 6, ry, w - 12, rowH).radius(5).fill(Gfx.alpha(c, owned ? 0.12f : 0.04f)).border(0.6f, Gfx.alpha(c, border))
                    .glow(owned ? 2 : 0, Gfx.alpha(NIGHT, 0.2f)).draw(g);
            float ix = x + 16, iy = ry + rowH / 2f;
            Shapes.circle(ix, iy, 6.5f).fillV(Gfx.alpha(c, owned ? 0.35f : 0.12f), Gfx.alpha(DEEP, 0.5f)).border(0.5f, Gfx.alpha(c, owned ? 0.85f : 0.35f)).draw(g);
            Shapes.icon(icons[i], ix, iy, 7).fill(owned ? 0xFFEAF6FF : SystemTheme.TEXT_DIM).draw(g);
            SysFont.text(g, SysFont.fit(t.displayName().getString(), 5.6f, true, w - 46), x + 26, ry + 3, 5.6f,
                    owned ? SystemTheme.TEXT : upcoming ? SystemTheme.TEXT_SOFT : SystemTheme.TEXT_DIM);
            SysFont.text(g, SysFont.fit(t("tier." + t.id() + ".rooms"), 4.4f, false, w - 46), x + 26, ry + rowH - 8, 4.4f,
                    owned ? SystemTheme.TEXT_SOFT : SystemTheme.TEXT_DIM);
            Shapes.icon(owned ? Icons.CHECK : Icons.LOCK, x + w - 15, iy, 6.5f).fill(owned ? SystemTheme.GOOD : Gfx.alpha(SystemTheme.TEXT_DIM, 0.7f)).draw(g);
        }

        // the party's homes
        float py = y + 18 + tiers.length * (rowH + 1) + 5;
        SysFont.spaced(g, t("tab.party").toUpperCase(Locale.ROOT), x + 9, py, 4.8f, Gfx.alpha(NIGHT, 0.95f), true, 1.2f, 0f);
        float ly = py + 9;
        if (!s.visits) {
            wrapDim(g, t("tab.party.off"), x + 9, ly, w - 18);
            return;
        }
        if (s.hosts.isEmpty()) {
            wrapDim(g, t("tab.party.none"), x + 9, ly, w - 18);
            return;
        }
        float hh = 13;
        int room = (int) Math.max(1, (y + h - 5 - ly) / (hh + 1));
        boolean free = !HomeClient.channeling() && HomeClient.readyIn() <= 0 && s.world;
        for (int i = 0; i < Math.min(room, s.hosts.size()); i++) {
            HomeClient.Host host = s.hosts.get(i);
            float hy = ly + i * (hh + 1);
            boolean has = host.tier().atLeast(HomeTier.STUDIO);
            boolean there = has && !s.guestOf.isEmpty() && s.guestOf.equals(host.name());
            Shapes.box(x + 6, hy, w - 12, hh).radius(4).fill(Gfx.alpha(NIGHT, has ? 0.07f : 0.03f)).border(0.5f, Gfx.alpha(NIGHT, has ? 0.3f : 0.12f)).draw(g);
            Shapes.circle(x + 12, hy + hh / 2f, 1.8f).fill(host.online() ? SystemTheme.GOOD : SystemTheme.TEXT_DIM).draw(g);
            SysFont.text(g, SysFont.fit(host.name(), 5f, true, w - 76), x + 17, hy + 1.6f, 5f, has ? SystemTheme.TEXT : SystemTheme.TEXT_DIM);
            String tier = has ? host.tier().displayName().getString() : t("tab.party.homeless");
            SysFont.text(g, SysFont.fit(tier, 4.2f, false, w - 76), x + 17, hy + hh - 5.6f, 4.2f, SystemTheme.TEXT_DIM);
            if (there) {
                SysFont.draw(g, t("tab.party.here"), x + w - 12, SysFont.centerY(hy, hh, 4.6f), 4.6f, NIGHT, true, 1f);
            } else if (has) {
                button(g, x + w - 52, hy + 1.5f, 44, hh - 3, t("tab.party.visit"), Icons.HOME, NIGHT, free, mouseX, mouseY, HomeActionPayload.VISIT, host.id());
            }
        }
    }

    private static void wrapDim(GuiGraphics g, String text, float x, float y, float w) {
        float ty = y;
        for (SysFont.Line l : SysFont.wrap(Component.literal(text), 4.8f, false, w)) {
            SysFont.draw(g, l, x, ty, SystemTheme.TEXT_DIM, 0f);
            ty += 4.8f * SysFont.LINE;
        }
    }

    /** A line in place of a button: why there isn't one. */
    private static void note(GuiGraphics g, float x, float y, float w, float h, ResourceLocation icon, String text, int color) {
        Shapes.pill(x, y, w, h).fill(Gfx.alpha(NIGHT, 0.05f)).border(0.6f, Gfx.alpha(NIGHT, 0.2f)).draw(g);
        Shapes.icon(icon, x + 9, y + h / 2f, 7).fill(Gfx.alpha(color, 0.9f)).draw(g);
        SysFont.text(g, SysFont.fit(text, 5f, false, w - 22), x + 16, SysFont.centerY(y, h, 5f), 5f, color);
    }

    private void button(GuiGraphics g, float x, float y, float w, float h, String label, ResourceLocation icon, int color, boolean enabled,
                        int mouseX, int mouseY, int action, @Nullable UUID host) {
        boolean hover = enabled && Ui.inside(mouseX, mouseY, x, y, w, h);
        int c = enabled ? color : SystemTheme.TEXT_DIM;
        float sheen = Anim.loop(3.2f) * 1.6f - 0.3f;
        Shapes.pill(x, y, w, h).fillV(Gfx.alpha(c, hover ? 0.42f : 0.26f), Gfx.alpha(Gfx.shade(c, -0.4f), hover ? 0.3f : 0.16f))
                .border(0.9f, Gfx.alpha(Gfx.mix(c, 0xFFFFFFFF, 0.25f), enabled ? 0.95f : 0.4f), Gfx.alpha(c, 0.3f))
                .glow(enabled ? 4 + (hover ? 3 : 0) : 0, Gfx.alpha(c, 0.3f))
                .sheen(enabled ? sheen : -2, 0.16f).draw(g);
        float size = h >= 15 ? 6.2f : 4.8f;
        String text = SysFont.fit(label, size, true, w - 20);
        float lw = SysFont.spacedWidth(text, size, true, 1.1f);
        float iconSize = h >= 15 ? 7.5f : 5.6f;
        float lx = x + (w - lw - iconSize - 2) / 2f;
        Shapes.icon(icon, lx + iconSize / 2f, y + h / 2f, iconSize).fill(enabled ? SystemTheme.TEXT : SystemTheme.TEXT_SOFT).draw(g);
        SysFont.spaced(g, text, lx + iconSize + 2, SysFont.centerY(y, h, size), size, enabled ? SystemTheme.TEXT : SystemTheme.TEXT_SOFT, true, 1.1f, 0f);
        if (enabled) hits.add(new Object[]{"act", x, y, w, h, action, host});
    }

    private static String t(String key, Object... args) {
        return Component.translatable("home.beyondsolo." + key, args).getString();
    }

    /** "0:42" for a wait in ticks. */
    static String clock(long ticks) {
        long s = (ticks + 19) / 20;
        return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Object[] hit = hits.get(i);
            if (!Ui.inside(mouseX, mouseY, (float) hit[1], (float) hit[2], (float) hit[3], (float) hit[4])) continue;
            if ("act".equals(hit[0])) {
                pressQueued = (int) hit[5];
                pressHost = (UUID) hit[6];
                return true;
            }
        }
        return false;
    }

    private static void send(int action, @Nullable UUID host) {
        if (action == HomeActionPayload.VISIT && host != null) HomeClient.visit(host);
        else HomeClient.send(action);
        SystemScreen.playClick();
        // going back out plays on the screen; going home closes the window itself once the gathering starts
        if (action == HomeActionPayload.LEAVE) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) mc.screen.onClose();
        }
    }

    // ------------------------------------------------------------------ UI test driver

    /** Presses a button of the page (driver: {@code homego <action>}). */
    public static void press(int action) {
        pressQueued = action;
        pressHost = null;
    }
}
