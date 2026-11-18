package com.beyondsolo.client.raid;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.breaks.BreakHud;
import com.beyondsolo.client.economy.GoldHud;
import com.beyondsolo.client.gate.GateOverlay;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.hud.EffectsHud;
import com.beyondsolo.client.hud.HudSpace;
import com.beyondsolo.client.key.RunHud;
import com.beyondsolo.raid.RaidRole;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The raid on the HUD. Out at the gate: a capsule at the top for the muster (how many have mustered, the countdown).
 * Inside: the raid frames on the right (every raider and hired hunter, coloured by role, with their health), the
 * objective under the boss bar (the lieutenants, the barrier's pylons, the boss), banners across the screen when the
 * fight turns, the cataclysm's warning while it gathers, and the results board when it is over.
 */
public final class RaidHud {
    private static final float FRAME_W = 98;
    private static final float FRAME_H = 13;
    private static final int RAID = 0xFFB98BFF;
    private static final int GOLD = 0xFFFFC247;
    private static final int DOOM = 0xFFFF3348;
    /** Seconds the results board stays up. */
    private static final float RESULTS_FOR = RaidClient.RESULTS_SECONDS;

    private static float shown, musterShown, objectiveIn;
    private static float top = Float.NaN;
    private static final Map<UUID, Float> HEALTH = new HashMap<>();

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;
        int w = g.guiWidth(), h = g.guiHeight();
        boolean inside = RaidClient.inside() && inCave(mc);
        shown = Anim.approach(shown, inside ? 1 : 0, 6f);
        musterShown = Anim.approach(musterShown, RaidClient.mustering() ? 1 : 0, 6f);
        objectiveIn = Anim.approach(objectiveIn, inside && RaidClient.phase().equals("RUNNING") ? 1 : 0, 6f);
        if (musterShown > 0.02f) muster(g, w);
        if (shown > 0.02f) {
            float old = Gfx.fade(shown);
            frames(g, w, h);
            objective(g, w);
            Gfx.setAlpha(old);
        }
        cataclysm(g, w, h);
        banner(g, w, h);
        results(g, w, h);
    }

    /** True while the player is in a gate's cave (the raid HUD is for inside). */
    private static boolean inCave(Minecraft mc) {
        return mc.level != null && mc.level.dimension().equals(com.beyondsolo.gate.GateService.DIMENSION);
    }

    /**
     * Where the raid's pieces at the top centre start: under the gate boss's bar, the run clock and the break timer
     * while they show, as the other pieces there stack.
     */
    private static float stackTop() {
        return 6 + 34 * Anim.outCubic(GateOverlay.bossBarIn()) + 26 * RunHud.clockShown() + BreakHud.shownHeight();
    }

    /** How far down the raid's objective chip reaches at the top centre: the depths' weather and trap chips sit under it. */
    public static float objectiveHeight() {
        return 16 * Anim.outCubic(objectiveIn);
    }

    /** True while the raid frames stand where the party frames would (the party HUD keeps out of the way). */
    public static boolean framesShown() {
        return shown > 0.02f;
    }

    // ------------------------------------------------------------------ the muster

    private static void muster(GuiGraphics g, int w) {
        CompoundTag t = RaidClient.data();
        PlayerRank rank = PlayerRank.values()[Mth.clamp(t.getInt("rank"), 0, PlayerRank.values().length - 1)];
        int raiders = t.getList("raiders", Tag.TAG_COMPOUND).size();
        long countdown = RaidClient.ticks("countdown");
        String title = Component.translatable("ui.beyondsolo.raid.hud.muster", rank.displayName()).getString();
        String sub = countdown >= 0 ? Component.translatable("ui.beyondsolo.raid.hud.starting", (countdown + 19) / 20).getString()
                : RaidClient.leading() ? Component.translatable("ui.beyondsolo.raid.hud.you_start").getString()
                : Component.translatable("ui.beyondsolo.raid.hud.waiting", t.getString("leaderName")).getString();
        String count = raiders + " / " + t.getInt("maxSize");
        float tw = Math.max(SysFont.spacedWidth(title, 6f, true, 1.2f), SysFont.width(sub, 5f, false)) + SysFont.width(count, 6f, true) + 46;
        float x = (w - tw) / 2f, y = stackTop() - 16 * (1 - Anim.outCubic(musterShown));
        float old = Gfx.fade(musterShown);
        boolean hot = countdown >= 0;
        float pulse = hot ? Anim.pulse(0.5f) : Anim.pulse(2.4f);
        Shapes.box(x, y, tw, 22).radius(8).fillV(Gfx.fade(SystemTheme.GLASS_TOP, 0.9f), Gfx.fade(SystemTheme.GLASS_BOTTOM, 0.9f))
                .border(0.8f, Gfx.alpha(hot ? GOLD : RAID, 0.6f + 0.35f * pulse), Gfx.alpha(RAID, 0.2f))
                .glow(5 + 3 * pulse, Gfx.alpha(hot ? GOLD : RAID, 0.25f)).draw(g);
        Ui.rankEmblem(g, rank, x + 12, y + 11, 6.5f, 0.2f * pulse);
        SysFont.spaced(g, title, x + 23, y + 3.5f, 6f, SystemTheme.TEXT, true, 1.2f, 0f);
        SysFont.text(g, sub, x + 23, y + 12.5f, 5f, hot ? Gfx.mix(GOLD, 0xFFFFFFFF, 0.3f) : SystemTheme.TEXT_SOFT);
        SysFont.draw(g, count, x + tw - 8, SysFont.centerY(y, 22, 6f), 6f, Gfx.mix(RAID, 0xFFFFFFFF, 0.4f), true, 1f);
        Gfx.setAlpha(old);
        HudSpace.claimTop(x, y, tw, 22);
    }

    // ------------------------------------------------------------------ frames

    private record Row(UUID id, String name, int cls, RaidRole role, float hp, boolean hunter, boolean down, boolean leader, boolean me) {}

    private static List<Row> rows() {
        CompoundTag t = RaidClient.data();
        UUID leader = t.hasUUID("leader") ? t.getUUID("leader") : null;
        UUID me = RaidClient.me();
        List<Row> out = new ArrayList<>();
        ListTag raiders = t.getList("raiders", Tag.TAG_COMPOUND);
        for (int i = 0; i < raiders.size(); i++) {
            CompoundTag r = raiders.getCompound(i);
            UUID id = r.getUUID("id");
            String status = r.getString("status");
            boolean down = status.equals("FALLEN") || status.equals("LEFT") || !r.getBoolean("online");
            RaidRole role = RaidRole.byId(r.getString("role"));
            out.add(new Row(id, r.getString("name"), r.getInt("cls"), role == null ? RaidRole.STRIKER : role, r.getFloat("hp"), false, down,
                    id.equals(leader), id.equals(me)));
        }
        ListTag hunters = t.getList("hunters", Tag.TAG_COMPOUND);
        for (int i = 0; i < hunters.size(); i++) {
            CompoundTag r = hunters.getCompound(i);
            RaidRole role = RaidRole.byId(r.getString("role"));
            out.add(new Row(r.getUUID("id"), r.getString("name"), r.getInt("cls"), role == null ? RaidRole.STRIKER : role, r.getFloat("hp"), true,
                    r.getBoolean("fallen"), false, false));
        }
        return out;
    }

    private static void frames(GuiGraphics g, int w, int h) {
        List<Row> rows = rows();
        if (rows.isEmpty()) return;
        float x = w - 8 - FRAME_W + (1 - Anim.outCubic(shown)) * 18;
        float want = Math.max(Math.max(56, h * 0.3f), Math.max(EffectsHud.bottom() + 9, GoldHud.bottom() + 6));
        top = Float.isNaN(top) ? want : Anim.approach(top, want, 8f);
        float y = top;
        // a header: the raid's rank and how many are still standing
        int standing = 0;
        for (Row r : rows) if (!r.down()) standing++;
        CompoundTag t = RaidClient.data();
        PlayerRank rank = PlayerRank.values()[Mth.clamp(t.getInt("rank"), 0, PlayerRank.values().length - 1)];
        String head = Component.translatable("ui.beyondsolo.raid.hud.frames", rank.displayName()).getString();
        SysFont.spaced(g, head, x + 2, y, 5f, Gfx.mix(RAID, 0xFFFFFFFF, 0.4f), true, 1.2f, 0f);
        SysFont.draw(g, standing + "/" + rows.size(), x + FRAME_W - 2, y, 5f, SystemTheme.TEXT_SOFT, true, 1f);
        y += 8;
        int room = Math.max(1, (int) ((h - 46 - y) / (FRAME_H + 2)));
        for (int i = 0; i < rows.size(); i++) {
            if (i == room - 1 && rows.size() > room) {
                String more = Component.translatable("ui.beyondsolo.raid.hud.more", rows.size() - i).getString();
                SysFont.draw(g, more, x + FRAME_W - 2, y + 2, 4.6f, SystemTheme.TEXT_DIM, false, 1f);
                break;
            }
            frame(g, rows.get(i), x, y);
            y += FRAME_H + 2;
        }
    }

    private static void frame(GuiGraphics g, Row r, float x, float y) {
        int cc = r.cls() >= 0 && r.cls() < PlayerClass.values().length ? SystemTheme.playerClass(PlayerClass.values()[r.cls()]) : RAID;
        int tint = r.down() ? SystemTheme.TEXT_DIM : cc;
        float hp = r.down() ? 0 : Mth.clamp(r.hp(), 0, 1);
        float drawn = HEALTH.containsKey(r.id()) ? Anim.approach(HEALTH.get(r.id()), hp, 8f) : hp;
        HEALTH.put(r.id(), drawn);
        boolean low = !r.down() && hp < 0.3f;
        float danger = low ? Anim.pulse(0.8f) : 0;
        Shapes.box(x, y, FRAME_W, FRAME_H).radius(5).fillV(Gfx.fade(SystemTheme.GLASS_TOP, r.hunter() ? 0.7f : 0.84f), Gfx.fade(SystemTheme.GLASS_BOTTOM, 0.84f))
                .border(0.6f, Gfx.alpha(low ? SystemTheme.DANGER : r.me() ? SystemTheme.ACCENT : tint, 0.45f + 0.35f * danger))
                .glow(low ? 3 + 3 * danger : 0, Gfx.alpha(SystemTheme.DANGER, 0.3f)).draw(g);
        // the role stripe on the left edge
        Shapes.box(x + 1.2f, y + 2, 1.6f, FRAME_H - 4).radius(0.8f).fill(r.down() ? SystemTheme.TEXT_DIM : r.role().color).draw(g);
        float bx = x + 9, by = y + FRAME_H / 2f;
        ResourceLocation icon = r.cls() >= 0 && r.cls() < PlayerClass.values().length ? Icons.of(PlayerClass.values()[r.cls()]) : Icons.QUESTION;
        Shapes.icon(r.down() ? Icons.SKULL : icon, bx, by, 7f).fill(r.down() ? SystemTheme.TEXT_DIM : Gfx.mix(tint, 0xFFFFFFFF, 0.5f)).draw(g);
        float tx = x + 15;
        String name = SysFont.fit(r.name(), 4.8f, true, FRAME_W - 22 - (r.leader() ? 7 : 0) - (r.hunter() ? 9 : 0));
        SysFont.text(g, name, tx, y + 1.8f, 4.8f, r.down() ? SystemTheme.TEXT_DIM : r.hunter() ? SystemTheme.TEXT_SOFT : SystemTheme.TEXT);
        float after = tx + SysFont.width(name, 4.8f, true) + 2;
        if (r.leader()) Shapes.icon(Icons.CROWN, after + 2.5f, y + 4f, 5f).fill(SystemTheme.GOLD).draw(g);
        if (r.hunter()) Shapes.icon(Icons.HANDSHAKE, x + FRAME_W - 6, y + 4.2f, 5f).fill(Gfx.alpha(GOLD, 0.8f)).draw(g);
        if (!r.down()) {
            Ui.bar(g, tx, y + 8.6f, FRAME_W - 15 - 4, 2.2f, drawn, low ? SystemTheme.DANGER : SystemTheme.HP_A,
                    low ? Gfx.mix(SystemTheme.HP_B, 0xFFFFFFFF, 0.5f * danger) : SystemTheme.HP_B);
        } else {
            Shapes.pill(tx, y + 8.6f, FRAME_W - 15 - 4, 2.2f).fill(SystemTheme.TRACK).draw(g);
        }
    }

    // ------------------------------------------------------------------ the objective

    private static void objective(GuiGraphics g, int w) {
        CompoundTag t = RaidClient.data();
        String phase = RaidClient.phase();
        if (!phase.equals("RUNNING")) return;
        int ltLeft = t.getInt("lieutenantsLeft"), ltAll = t.getInt("lieutenants");
        String text;
        ResourceLocation icon;
        int c;
        if (ltLeft > 0) {
            text = Component.translatable("ui.beyondsolo.raid.hud.lieutenants", ltAll - ltLeft, ltAll).getString();
            icon = Icons.LOCK;
            c = RAID;
        } else if (t.getBoolean("barrier")) {
            text = Component.translatable("ui.beyondsolo.raid.hud.pylons", t.getInt("pylonsLeft")).getString();
            icon = Icons.HEXAGON;
            c = 0xFFB98BFF;
        } else if (t.getInt("cataclysm") >= 0) {
            text = Component.translatable("ui.beyondsolo.raid.hud.cataclysm_next", (RaidClient.ticks("cataclysm") + 19) / 20).getString();
            icon = Icons.EXCLAIM;
            c = DOOM;
        } else if (t.getBoolean("bossAwake")) {
            text = Component.translatable("ui.beyondsolo.raid.hud.boss", Math.round(t.getFloat("bossHp") * 100)).getString();
            icon = Icons.SKULL;
            c = GOLD;
        } else {
            text = Component.translatable("ui.beyondsolo.raid.hud.chamber_open").getString();
            icon = Icons.PATH;
            c = GOLD;
        }
        String time = clock(RaidClient.elapsed());
        float tw = SysFont.width(text, 5.4f, true) + SysFont.width(time, 4.6f, false) + 30;
        float x = (w - tw) / 2f;
        float y = stackTop();
        Shapes.pill(x, y, tw, 12).fill(Gfx.alpha(0xFF060A14, 0.82f)).border(0.6f, Gfx.alpha(c, 0.7f)).glow(3, Gfx.alpha(c, 0.25f)).draw(g);
        Shapes.icon(icon, x + 8, y + 6, 6.5f).fill(Gfx.mix(c, 0xFFFFFFFF, 0.4f)).draw(g);
        SysFont.draw(g, text, x + 15, SysFont.centerY(y, 12, 5.4f), 5.4f, SystemTheme.TEXT, true, 0f);
        SysFont.draw(g, time, x + tw - 7, SysFont.centerY(y, 12, 4.6f), 4.6f, SystemTheme.TEXT_DIM, false, 1f);
        HudSpace.claimTop(x, y, tw, 12);
    }

    // ------------------------------------------------------------------ moments

    /** The cataclysm gathering: the edges of the screen burn red, and a bar runs down to when it lands. */
    private static void cataclysm(GuiGraphics g, int w, int h) {
        float k = RaidClient.cataclysm();
        if (k < 0 || !RaidClient.inside()) return;
        float pulse = Anim.pulse(k > 0.75f ? 0.25f : 0.6f);
        float strength = 0.25f + 0.45f * k + 0.15f * pulse;
        Shapes.vignette(0, 0, w, h, 0.35f, 1.4f).fill(Gfx.alpha(0xFF6A0010, Mth.clamp(strength, 0, 0.85f))).draw(g);
        float bw = 170, bx = (w - bw) / 2f, by = h * 0.3f;
        Shapes.box(bx, by, bw, 26).radius(8).fillV(Gfx.alpha(0xFF2A0610, 0.9f), Gfx.alpha(0xFF12040A, 0.9f))
                .border(0.9f, Gfx.alpha(DOOM, 0.7f + 0.3f * pulse)).glow(6 + 4 * pulse, Gfx.alpha(DOOM, 0.35f)).draw(g);
        Shapes.icon(Icons.EXCLAIM, bx + 12, by + 13, 10).fill(Gfx.mix(DOOM, 0xFFFFFFFF, 0.4f)).glow(3, Gfx.alpha(DOOM, 0.7f)).draw(g);
        String title = Component.translatable("ui.beyondsolo.raid.hud.cataclysm").getString();
        SysFont.spaced(g, title, bx + 22, by + 4, 6.4f, 0xFFFFE6E6, true, 1.6f, 0f);
        String sub = Component.translatable("ui.beyondsolo.raid.hud.stand_in_light", String.format(Locale.ROOT, "%.1f", RaidClient.cataclysmSeconds())).getString();
        SysFont.text(g, SysFont.fit(sub, 5f, false, bw - 30), bx + 22, by + 13.5f, 5f, 0xFFFFC0C0);
        Ui.bar(g, bx + 22, by + 21, bw - 30, 1.8f, 1 - k, 0xFFFF6A3A, 0xFFFFE08A);
    }

    /** A line across the screen when the fight turns. */
    private static void banner(GuiGraphics g, int w, int h) {
        if (!RaidClient.bannerUp() || RaidClient.cataclysm() >= 0) return;
        float t = RaidClient.sinceBanner(), end = RaidClient.BANNER_SECONDS;
        float k = (1 - Anim.window(t, end - 0.6f, end)) * Anim.outCubic(Anim.window(t, 0f, 0.25f));
        int c = 0xFF000000 | RaidClient.bannerColor();
        float cy = h * 0.27f;
        float old = Gfx.fade(k);
        float spread = Anim.outCubic(Anim.window(t, 0f, 0.5f));
        Shapes.box(w / 2f - w * 0.32f * spread, cy - 13, w * 0.64f * spread, 30).fillH(0x00000000, Gfx.alpha(0xFF060A14, 0.75f)).draw(g);
        Shapes.box(w / 2f, cy - 13, w * 0.32f * spread, 30).fillH(Gfx.alpha(0xFF060A14, 0.75f), 0x00000000).draw(g);
        Shapes.box(w / 2f - w * 0.3f * spread, cy - 13, w * 0.6f * spread, 0.8f).fillH(0x00000000, c).draw(g);
        Shapes.box(w / 2f - w * 0.3f * spread, cy + 16.2f, w * 0.6f * spread, 0.8f).fillH(c, 0x00000000).draw(g);
        String title = RaidClient.bannerTitle().getString().toUpperCase(Locale.ROOT);
        float size = Math.min(11f, (w * 0.6f) / Math.max(1, SysFont.spacedWidth(title, 1f, true, 2.4f)));
        SysFont.spaced(g, title, w / 2f, cy - 9, size, Gfx.mix(c, 0xFFFFFFFF, 0.55f), true, 2.4f, 0.5f);
        String sub = RaidClient.bannerText().getString();
        if (!sub.isEmpty()) SysFont.draw(g, SysFont.fit(sub, 5.6f, false, w * 0.6f), w / 2f, cy + 5, 5.6f, SystemTheme.TEXT_SOFT, false, 0.5f);
        Gfx.setAlpha(old);
    }

    /** The results board: who did what, the time, the best of them, and what the raid paid. */
    private static void results(GuiGraphics g, int w, int h) {
        CompoundTag r = RaidClient.results();
        if (r == null) return;
        float t = RaidClient.sinceResults();
        if (t > RESULTS_FOR) {
            RaidClient.closeResults();
            return;
        }
        RaidClient.boardFrame(t);
        boolean ok = r.getBoolean("cleared");
        int c = ok ? GOLD : DOOM;
        float k = (1 - Anim.window(t, RESULTS_FOR - 0.6f, RESULTS_FOR)) * Anim.outCubic(Anim.window(t, 0f, 0.35f));
        ListTag lines = r.getList("lines", Tag.TAG_COMPOUND);
        int n = Math.min(10, lines.size());
        float bw = 236, bh = 50 + n * 10 + (ok ? 12 : 0);
        float bx = (w - bw) / 2f, by = Math.max(40, h * 0.42f - bh / 2f) + (1 - k) * 10;
        float old = Gfx.fade(k);
        Shapes.box(bx, by, bw, bh).radius(10).fillV(Gfx.fade(SystemTheme.GLASS_TOP, 0.95f), Gfx.fade(SystemTheme.GLASS_BOTTOM, 0.95f))
                .border(1f, c, Gfx.alpha(c, 0.35f)).glow(10, Gfx.alpha(c, 0.3f)).draw(g);
        PlayerRank rank = PlayerRank.values()[Mth.clamp(r.getInt("rank"), 0, PlayerRank.values().length - 1)];
        Ui.rankEmblem(g, rank, bx + 18, by + 16, 9, ok ? 0.5f : 0f);
        SysFont.spaced(g, RaidClient.resultsTitle().getString().toUpperCase(Locale.ROOT), bx + 33, by + 7, 8f, Gfx.mix(c, 0xFFFFFFFF, 0.5f), true, 2f, 0f);
        SysFont.text(g, SysFont.fit(RaidClient.resultsDungeon().getString(), 5f, false, bw - 90), bx + 33, by + 18, 5f, SystemTheme.TEXT_SOFT);
        SysFont.draw(g, clock(r.getLong("ticks")), bx + bw - 10, by + 8, 7f, SystemTheme.TEXT, true, 1f);
        SysFont.draw(g, Component.translatable("ui.beyondsolo.raid.results.time").getString(), bx + bw - 10, by + 17, 4.4f, SystemTheme.TEXT_DIM, false, 1f);
        Ui.divider(g, bx + 10, by + 28, bw - 20, Gfx.alpha(c, 0.4f));

        double most = 1;
        for (int i = 0; i < n; i++) most = Math.max(most, lines.getCompound(i).getDouble("dealt"));
        String mvp = r.getString("mvp");
        float ly = by + 33;
        for (int i = 0; i < n; i++) {
            CompoundTag l = lines.getCompound(i);
            RaidRole role = RaidRole.byId(l.getString("role"));
            int rc = role == null ? RAID : role.color;
            boolean hunter = l.getBoolean("hunter");
            boolean fallen = l.getBoolean("fallen");
            Shapes.circle(bx + 14, ly + 3.4f, 2f).fill(rc).draw(g);
            String name = SysFont.fit(l.getString("name"), 5f, true, 70);
            SysFont.text(g, name, bx + 19, ly, 5f, fallen ? SystemTheme.TEXT_DIM : hunter ? SystemTheme.TEXT_SOFT : SystemTheme.TEXT);
            float after = bx + 19 + SysFont.width(name, 5f, true) + 3;
            if (!mvp.isEmpty() && l.getString("name").equals(mvp) && !hunter) {
                Shapes.icon(Icons.CROWN, after + 3, ly + 2.6f, 6f).fill(SystemTheme.GOLD).glow(2, Gfx.alpha(SystemTheme.GOLD, 0.6f)).draw(g);
                after += 8;
            }
            if (hunter && l.getInt("levels") > 0) {
                SysFont.text(g, Component.translatable("ui.beyondsolo.raid.results.levels", l.getInt("levels")).getString(), after, ly + 0.6f, 4.2f, SystemTheme.GOOD);
            } else if (fallen) {
                SysFont.text(g, Component.translatable("ui.beyondsolo.raid.results.fallen").getString(), after, ly + 0.6f, 4.2f, 0xFFFF8A8A);
            }
            float barX = bx + 112, barW = bw - 112 - 48;
            double dealt = l.getDouble("dealt");
            Ui.bar(g, barX, ly + 2.2f, barW, 2.4f, (float) (dealt / most), Gfx.shade(rc, -0.3f), Gfx.mix(rc, 0xFFFFFFFF, 0.3f));
            SysFont.draw(g, compact(dealt), bx + bw - 10, ly + 0.4f, 4.8f, SystemTheme.TEXT_SOFT, false, 1f);
            ly += 10;
        }
        if (ok) {
            String pay = Component.translatable("ui.beyondsolo.raid.results.paid", fmtScale(r.getFloat("scale")), r.getInt("caches"),
                    NumberFormat.getIntegerInstance(Locale.ROOT).format(r.getLong("guildXp"))).getString();
            Ui.divider(g, bx + 10, ly + 1, bw - 20, Gfx.alpha(c, 0.25f));
            SysFont.draw(g, SysFont.fit(pay, 4.8f, false, bw - 20), bx + bw / 2f, ly + 5, 4.8f, Gfx.mix(GOLD, 0xFFFFFFFF, 0.3f), false, 0.5f);
        }
        Gfx.setAlpha(old);
    }

    private static String fmtScale(float s) {
        return Math.abs(s - Math.round(s)) < 0.05f ? Integer.toString(Math.round(s)) : String.format(Locale.ROOT, "%.1f", s);
    }

    /** 980, 12.4k, 1.2M. */
    static String compact(double v) {
        if (v < 1000) return Long.toString(Math.round(v));
        if (v < 1_000_000) return String.format(Locale.ROOT, "%.1fk", v / 1000);
        return String.format(Locale.ROOT, "%.1fM", v / 1_000_000);
    }

    static String clock(long ticks) {
        long sec = Math.max(0, ticks) / 20;
        return sec / 60 + ":" + String.format(Locale.ROOT, "%02d", sec % 60);
    }

    /** Forgets the frames' smoothing (logged out). */
    public static void reset() {
        HEALTH.clear();
        top = Float.NaN;
        shown = musterShown = 0;
    }

    private RaidHud() {}
}
