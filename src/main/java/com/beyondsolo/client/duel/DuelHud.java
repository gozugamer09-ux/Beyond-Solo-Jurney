package com.beyondsolo.client.duel;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.breaks.BreakHud;
import com.beyondsolo.client.gate.GateOverlay;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.hud.HudSpace;
import com.beyondsolo.client.key.RunHud;
import com.beyondsolo.duel.Duel;
import com.beyondsolo.duel.DuelistEntity;
import com.beyondsolo.duel.NationalHunter;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * A duel on screen ({@link DuelClient}).
 *
 * <p>At the top, the two of them face to face: the hunter on the left in the System's blue, the challenger on the
 * right in their own colour, each with their health running out toward the outside, the clock between them, and under
 * the challenger what the country calls them, the form they take when pressed, and the move they are about to make.
 * Someone watching sees the same bar with the hunter's name.</p>
 *
 * <p>In the middle, for the hunter: the count of three and FIGHT, the moment the challenger shows what they really
 * are, the warning to get back in the ring with the seconds left, and how it ended, with what it paid.</p>
 */
public final class DuelHud {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int HUNTER = SystemTheme.ACCENT;
    /** How long the count's FIGHT, the unleashing and the result hold the middle of the screen (seconds). */
    private static final float FIGHT_SECONDS = 1.2f, UNLEASH_SECONDS = 2.6f, RESULT_SECONDS = 6f;

    private static float barIn;
    @Nullable private static DuelClient.View shown;
    @Nullable private static UUID shownId;
    private static float mineHp = 1, mineTrail = 1, theirHp = 1, theirTrail = 1;
    private static int count = -1;
    private static double countAt = -10;
    private static boolean unleashed;
    private static double unleashAt = -10;
    private static int moves = -1;
    @Nullable private static DuelistEntity.Move move;
    private static double moveAt = -10;
    private static boolean bellRung;

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        DuelClient.View v = DuelClient.focus();
        if (v != null) {
            if (!v.id().equals(shownId)) begin(v);
            shown = v;
        }
        barIn = Anim.approach(barIn, v != null ? 1 : 0, v != null ? 4f : 2.5f);
        if (shown == null) return;
        if (barIn < 0.01f && v == null) {
            shown = null;
            shownId = null;
            return;
        }
        DuelClient.View d = shown;
        int w = g.guiWidth(), h = g.guiHeight();
        DuelistEntity e = d.entity() instanceof DuelistEntity x ? x : null;
        track(mc, d, e);
        float y = 6 + 34 * Anim.outCubic(GateOverlay.bossBarIn()) + 26 * RunHud.clockShown() + BreakHud.shownHeight();
        bar(g, mc, d, e, w, y);
        if (d.mine()) {
            countdown(g, d, w, h);
            outside(g, d, w, h);
        }
        unleash(g, d, e, w, h);
        result(g, d, w, h);
    }

    /** True while one of the moments of the hunter's own duel holds the middle of the screen (other moments wait). */
    public static boolean holdsScreen() {
        DuelClient.View d = shown;
        if (d == null || !d.mine()) return false;
        if (d.phase() == Duel.Phase.COUNTDOWN) return true;
        if (d.bellAt >= 0 && Anim.since(d.bellAt) < FIGHT_SECONDS) return true;
        if (Anim.since(unleashAt) < UNLEASH_SECONDS) return true;
        return d.overAt >= 0 && d.result() != null && d.result() != Duel.Result.CALLED_OFF && Anim.since(d.overAt) < RESULT_SECONDS;
    }

    static void reset() {
        shown = null;
        shownId = null;
        barIn = 0;
    }

    private static void begin(DuelClient.View v) {
        shownId = v.id();
        mineHp = mineTrail = theirHp = theirTrail = 1;
        count = -1;
        countAt = -10;
        unleashed = false;
        unleashAt = -10;
        moves = -1;
        move = null;
        moveAt = -10;
        bellRung = v.phase() != Duel.Phase.COUNTDOWN;
    }

    /** What changed since the last frame: the count, the challenger unleashed, a new move, the bell. */
    private static void track(Minecraft mc, DuelClient.View d, @Nullable DuelistEntity e) {
        if (d.phase() == Duel.Phase.COUNTDOWN) {
            int n = Mth.clamp((int) Math.ceil(d.toBell() / 20.0), 1, 3);
            // the count and the bell are heard in the world (the server rings them)
            if (n != count) {
                count = n;
                countAt = Anim.now();
            }
        } else if (!bellRung && d.phase() == Duel.Phase.FIGHT) {
            bellRung = true;
        }
        if (e != null) {
            if (e.unleashed() && !unleashed && d.phase() == Duel.Phase.FIGHT) unleashAt = Anim.now();
            unleashed = e.unleashed();
            int n = e.moveCount();
            if (n != moves) {
                DuelistEntity.Move m = e.currentMove();
                if (moves >= 0 && m != null && m != DuelistEntity.Move.UNLEASH) {
                    move = m;
                    moveAt = Anim.now();
                }
                moves = n;
            }
        }
    }

    // ================================================================ the bar at the top

    private static void bar(GuiGraphics g, Minecraft mc, DuelClient.View d, @Nullable DuelistEntity e, int w, float y) {
        NationalHunter who = d.who();
        int hc = 0xFF000000 | who.color;
        float in = Anim.outCubic(barIn);
        float bw = Math.min(320, w * 0.62f), x = (w - bw) / 2f;
        y -= 24 * (1 - in);
        float old = Gfx.fade(in);
        HudSpace.claimTop(x - 6, y - 3, bw + 12, 38);
        boolean fighting = d.phase() == Duel.Phase.FIGHT;
        float urgent = fighting && d.left() < 20 * 20 ? 0.5f + 0.5f * Mth.sin((float) Anim.now() * 8f) : 0f;
        Shapes.box(x - 6, y - 3, bw + 12, 38).radius(9).fillV(0xD00A0C18, 0xD0050610)
                .border(0.9f, Gfx.alpha(hc, 0.7f), Gfx.alpha(HUNTER, 0.5f)).glow(9, Gfx.alpha(hc, 0.2f)).draw(g);

        // the clock between them, a mark of crossed blades over it
        float cx = x + bw / 2f;
        String clock = switch (d.phase()) {
            case COUNTDOWN -> clock(d.tag.getLong("endsAt") - d.tag.getLong("fightAt"));
            case FIGHT -> clock(Math.max(0, d.left()));
            case OVER -> "--:--";
        };
        int clockColor = urgent > 0 ? Gfx.mix(SystemTheme.TEXT, SystemTheme.DANGER, urgent) : SystemTheme.TEXT;
        Shapes.icon(Icons.DUEL, cx, y + 4.5f, 8).fill(Gfx.alpha(Gfx.mix(hc, WHITE, 0.5f), 0.9f)).draw(g);
        SysFont.draw(g, clock, cx, y + 10, 7.5f, clockColor, true, 0.5f);

        // the names
        float half = bw / 2f - 18;
        String left = d.mine() ? mc.player.getGameProfile().getName() : d.tag.getString("hunterName");
        String right = who.displayName().getString();
        SysFont.draw(g, SysFont.fit(left, 6.5f, true, half - 4), x, y + 1, 6.5f, Gfx.mix(HUNTER, WHITE, 0.6f), true, 0f);
        SysFont.draw(g, SysFont.fit(right, 6.5f, true, half - 4), x + bw, y + 1, 6.5f, Gfx.mix(hc, WHITE, 0.55f), true, 1f);

        // the health of each, running out toward the outside
        LivingEntity hunter = hunter(mc, d);
        float mine = hunter == null ? mineHp : Mth.clamp(hunter.getHealth() / Math.max(1f, hunter.getMaxHealth()), 0, 1);
        float theirs = e == null ? theirHp : Mth.clamp(e.getHealth() / Math.max(1f, e.getMaxHealth()), 0, 1);
        if (d.phase() == Duel.Phase.COUNTDOWN) theirs = 1;
        mineHp = Anim.approach(mineHp, mine, 3f);
        theirHp = Anim.approach(theirHp, theirs, 3f);
        if (mine > mineTrail) mineTrail = mine;
        if (theirs > theirTrail) theirTrail = theirs;
        mineTrail = Anim.approach(mineTrail, mineHp, 0.5f);
        theirTrail = Anim.approach(theirTrail, theirHp, 0.5f);
        float by = y + 11, bh = 6;
        health(g, x, by, half, bh, mineHp, mineTrail, HUNTER, false);
        health(g, x + bw - half, by, half, bh, theirHp, theirTrail, hc, true);

        // under the bars: who the hunter is to them, and the challenger's title, form and move
        float ly = by + bh + 4;
        String tag = d.mine() ? Component.translatable("ui.beyondsolo.duel.hud.you").getString()
                : Component.translatable("ui.beyondsolo.duel.hud.hunter").getString();
        SysFont.spaced(g, tag, x, ly, 4.6f, SystemTheme.TEXT_SOFT, true, 1.2f, 0f);
        float since = Anim.since(moveAt);
        if (move != null && since < 1.8f && fighting) {
            float k = 1 - Anim.window(since, 1.4f, 1.8f);
            float o = Gfx.fade(k);
            String mv = Component.translatable("duel.beyondsolo.move." + move.key()).getString().toUpperCase(Locale.ROOT);
            float pop = 1 + 0.25f * (1 - Anim.outCubic(Anim.clamp01(since / 0.2f)));
            float mw = SysFont.spacedWidth(mv, 4.8f, true, 1.2f) + 14;
            float mx = x + bw - mw;
            Shapes.pill(mx, ly - 1.8f, mw, 8.6f).fill(Gfx.alpha(hc, 0.3f)).border(0.6f, Gfx.alpha(hc, 0.9f)).glow(3 * pop, Gfx.alpha(hc, 0.4f)).draw(g);
            Shapes.icon(Icons.EXCLAIM, mx + 5, ly + 2.5f, 5).fill(WHITE).draw(g);
            SysFont.spaced(g, mv, mx + 9, ly, 4.8f, WHITE, true, 1.2f, 0f);
            Gfx.setAlpha(o);
        } else {
            String title = unleashed && e != null ? who.unleashed().getString().toUpperCase(Locale.ROOT) : who.epithet().getString();
            int tc = unleashed ? Gfx.mix(hc, WHITE, 0.3f + 0.2f * Anim.pulse(1.2f)) : Gfx.mix(hc, SystemTheme.TEXT_SOFT, 0.5f);
            SysFont.draw(g, SysFont.fit(title, 4.8f, unleashed, half), x + bw, ly, 4.8f, tc, unleashed, 1f);
        }
        Gfx.setAlpha(old);
    }

    /** One of the two health bars; {@code right}: it runs out toward the right edge (the challenger's). */
    private static void health(GuiGraphics g, float x, float y, float w, float h, float hp, float trail, int c, boolean right) {
        Shapes.pill(x, y, w, h).fill(SystemTheme.TRACK).border(0.5f, SystemTheme.TRACK_BORDER).draw(g);
        if (trail > hp + 0.002f) {
            float tw = Math.max(h, w * trail);
            Shapes.pill(right ? x + w - tw : x, y, tw, h).fill(0xD0FFE6C8).draw(g);
        }
        if (hp > 0.003f) {
            float hw = Math.max(h, w * hp);
            int a = Gfx.shade(c, -0.35f), b = Gfx.mix(c, WHITE, 0.3f);
            Shapes.pill(right ? x + w - hw : x, y, hw, h).fillH(right ? b : a, right ? a : b).glow(4, Gfx.alpha(c, 0.5f))
                    .sheen(Anim.loop(2.8f) * 1.6f - 0.3f, 0.2f).draw(g);
        }
    }

    @Nullable
    private static LivingEntity hunter(Minecraft mc, DuelClient.View d) {
        if (d.mine()) return mc.player;
        if (mc.level == null || !d.tag.hasUUID("hunter")) return null;
        Player p = mc.level.getPlayerByUUID(d.tag.getUUID("hunter"));
        return p;
    }

    // ================================================================ the middle of the screen

    /** The count of three, then FIGHT. */
    private static void countdown(GuiGraphics g, DuelClient.View d, int w, int h) {
        NationalHunter who = d.who();
        int hc = 0xFF000000 | who.color;
        float cx = w / 2f, cy = h * 0.34f;
        if (d.phase() == Duel.Phase.COUNTDOWN && count > 0) {
            float t = Anim.since(countAt);
            float stamp = Anim.outCubic(Anim.clamp01(t / 0.22f));
            float s = Mth.lerp(stamp, 2.2f, 1f);
            float fade = 1 - Anim.window(t, 0.75f, 1f);
            float old = Gfx.fade(Math.min(1f, t / 0.08f) * fade);
            float ring = Anim.outCubic(Anim.clamp01(t / 0.6f));
            Shapes.ring(cx, cy, 16 + 22 * ring, 1.2f).fill(Gfx.alpha(hc, 0.8f * (1 - ring))).glow(4, Gfx.alpha(hc, 0.4f * (1 - ring))).draw(g);
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            g.pose().scale(s, s, 1);
            SysFont.draw(g, String.valueOf(count), 0, -11, 22f, Gfx.mix(hc, WHITE, 0.65f), true, 0.5f);
            g.pose().popPose();
            Gfx.setAlpha(old);
            float sub = Anim.outCubic(Anim.clamp01(Anim.since(d.seenAt) / 0.4f));
            float o2 = Gfx.fade(sub);
            String vs = Component.translatable("ui.beyondsolo.duel.hud.versus", who.displayName()).getString().toUpperCase(Locale.ROOT);
            SysFont.spaced(g, vs, cx, cy + 20, 6f, SystemTheme.TEXT, true, 2f, 0.5f);
            Gfx.setAlpha(o2);
            return;
        }
        if (d.bellAt < 0) return;
        float t = Anim.since(d.bellAt);
        if (t > FIGHT_SECONDS) return;
        float flash = 1 - Anim.outCubic(Anim.clamp01(t / 0.5f));
        Shapes.vignette(0, 0, w, h, 0.1f, 1.2f).fill(Gfx.alpha(hc, 0.35f * flash)).draw(g);
        float stamp = Anim.outCubic(Anim.clamp01(t / 0.18f));
        float s = Mth.lerp(stamp, 2.6f, 1f);
        float fade = 1 - Anim.window(t, FIGHT_SECONDS - 0.35f, FIGHT_SECONDS);
        float old = Gfx.fade(Math.min(1f, t / 0.06f) * fade);
        String word = Component.translatable("ui.beyondsolo.duel.hud.fight").getString().toUpperCase(Locale.ROOT);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(s, s, 1);
        float ww = SysFont.spacedWidth(word, 18f, true, 3f);
        Shapes.box(-ww / 2f - 44, -1, 34, 1.2f).fillH(Gfx.alpha(hc, 0f), hc).draw(g);
        Shapes.box(ww / 2f + 10, -1, 34, 1.2f).fillH(hc, Gfx.alpha(hc, 0f)).draw(g);
        SysFont.spaced(g, word, 0, -9, 18f, Gfx.mix(hc, WHITE, 0.7f), true, 3f, 0.5f);
        g.pose().popPose();
        Gfx.setAlpha(old);
    }

    /** Outside the ring: back in, with the seconds left before it is a forfeit. */
    private static void outside(GuiGraphics g, DuelClient.View d, int w, int h) {
        int out = d.tag.getInt("outside"), max = Math.max(1, d.tag.getInt("outsideMax"));
        if (out <= 0 || d.phase() != Duel.Phase.FIGHT) return;
        float danger = Mth.clamp(out / (float) max, 0, 1);
        float pulse = 0.5f + 0.5f * Mth.sin((float) Anim.now() * (6 + 10 * danger));
        Shapes.vignette(0, 0, w, h, 0.25f, 1.6f).fill(Gfx.alpha(SystemTheme.DANGER, (0.18f + 0.3f * danger) * (0.6f + 0.4f * pulse))).draw(g);
        float cx = w / 2f, cy = h * 0.62f;
        String head = Component.translatable("ui.beyondsolo.duel.hud.outside").getString().toUpperCase(Locale.ROOT);
        SysFont.spaced(g, head, cx, cy, 7f, Gfx.mix(SystemTheme.DANGER, WHITE, 0.3f + 0.3f * pulse), true, 1.8f, 0.5f);
        float left = Math.max(0, (max - out) / 20f);
        String secs = Component.translatable("ui.beyondsolo.duel.hud.outside.left", String.format(Locale.ROOT, "%.1f", left)).getString();
        SysFont.draw(g, secs, cx, cy + 11, 9f, WHITE, true, 0.5f);
    }

    /** The challenger shows what they really are: their colour over the screen and the name of the form. */
    private static void unleash(GuiGraphics g, DuelClient.View d, @Nullable DuelistEntity e, int w, int h) {
        float t = Anim.since(unleashAt);
        if (t > UNLEASH_SECONDS || d.phase() == Duel.Phase.OVER) return;
        NationalHunter who = d.who();
        int hc = 0xFF000000 | who.color;
        float live = 1 - Anim.window(t, UNLEASH_SECONDS - 0.5f, UNLEASH_SECONDS);
        float flash = 1 - Anim.outCubic(Anim.clamp01(t / 0.7f));
        Shapes.vignette(0, 0, w, h, 0.05f, 1.1f).fill(Gfx.alpha(hc, (0.25f + 0.35f * flash) * live)).draw(g);
        float old = Gfx.fade(live);
        float cx = w / 2f, cy = h * 0.28f;
        float band = Anim.outCubic(Anim.clamp01(t / 0.3f));
        float bw = w * band;
        Shapes.box(cx - bw / 2f, cy - 13, bw, 34).fillH(Gfx.alpha(hc, 0f), Gfx.alpha(hc, 0.32f)).draw(g);
        Shapes.box(cx - bw / 2f, cy - 13, bw, 34).fillH(Gfx.alpha(hc, 0.32f), Gfx.alpha(hc, 0f)).draw(g);
        Shapes.box(cx - bw / 2f, cy - 13, bw, 0.8f).fillH(Gfx.alpha(hc, 0f), hc).draw(g);
        Shapes.box(cx - bw / 2f, cy + 21, bw, 0.8f).fillH(hc, Gfx.alpha(hc, 0f)).draw(g);
        float k = Anim.outCubic(Anim.window(t, 0.12f, 0.45f));
        float o2 = Gfx.fade(k);
        String line = Component.translatable("ui.beyondsolo.duel.hud.unleashes", who.displayName()).getString().toUpperCase(Locale.ROOT);
        SysFont.spaced(g, line, cx, cy - 8, 5.2f, SystemTheme.TEXT, true, 2f, 0.5f);
        float s = Mth.lerp(k, 1.6f, 1f);
        g.pose().pushPose();
        g.pose().translate(cx, cy + 6, 0);
        g.pose().scale(s, s, 1);
        String form = who.unleashed().getString().toUpperCase(Locale.ROOT);
        SysFont.spaced(g, form, 0, -6.5f, 13f, Gfx.mix(hc, WHITE, 0.6f), true, 2.5f, 0.5f);
        g.pose().popPose();
        Gfx.setAlpha(o2);
        Gfx.setAlpha(old);
    }

    /** How it ended: the word, the line under it, and what a win paid. */
    private static void result(GuiGraphics g, DuelClient.View d, int w, int h) {
        Duel.Result r = d.result();
        if (r == null || d.overAt < 0) return;
        float t = Anim.since(d.overAt);
        if (t > RESULT_SECONDS) return;
        NationalHunter who = d.who();
        int hc = 0xFF000000 | who.color;
        boolean mine = d.mine();
        String hunter = d.tag.getString("hunterName");
        int c = switch (r) {
            case WON -> mine ? SystemTheme.GOLD : HUNTER;
            case LOST -> mine ? SystemTheme.DANGER : hc;
            case DRAWN -> 0xFF8FB4FF;
            case FORFEIT -> 0xFFFF9F43;
            case CALLED_OFF -> SystemTheme.TEXT_DIM;
        };
        String key = r.name().toLowerCase(Locale.ROOT);
        String word = mine ? Component.translatable("ui.beyondsolo.duel.hud.result." + key).getString()
                : r == Duel.Result.WON ? Component.translatable("ui.beyondsolo.duel.hud.wins", hunter).getString()
                : r == Duel.Result.LOST ? Component.translatable("ui.beyondsolo.duel.hud.wins", who.displayName()).getString()
                : Component.translatable("ui.beyondsolo.duel.hud.result." + key).getString();
        Component line = mine ? Component.translatable("ui.beyondsolo.duel.hud.line." + key, who.displayName())
                : Component.translatable("ui.beyondsolo.duel.hud.watch." + key, hunter, who.displayName());
        float live = 1 - Anim.window(t, RESULT_SECONDS - 0.6f, RESULT_SECONDS);
        float flash = 1 - Anim.outCubic(Anim.clamp01(t / 0.8f));
        if (mine && r == Duel.Result.WON) Shapes.vignette(0, 0, w, h, 0.1f, 1.3f).fill(Gfx.alpha(c, 0.3f * flash)).draw(g);
        float old = Gfx.fade(live);
        float cx = w / 2f, cy = h * 0.3f;
        float stamp = Anim.outCubic(Anim.clamp01(t / 0.25f));
        float s = Mth.lerp(stamp, 2.2f, 1f);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(s, s, 1);
        String big = word.toUpperCase(Locale.ROOT);
        float size = mine ? 17f : 12f;
        float ww = SysFont.spacedWidth(big, size, true, 3f);
        float o1 = Gfx.fade(Math.min(1f, t / 0.08f));
        Shapes.box(-ww / 2f - 46, -1, 36, 1.2f).fillH(Gfx.alpha(c, 0f), c).draw(g);
        Shapes.box(ww / 2f + 10, -1, 36, 1.2f).fillH(c, Gfx.alpha(c, 0f)).draw(g);
        SysFont.spaced(g, big, 0, -size * 0.5f, size, Gfx.mix(c, WHITE, 0.45f), true, 3f, 0.5f);
        Gfx.setAlpha(o1);
        g.pose().popPose();
        float sub = Anim.outCubic(Anim.window(t, 0.3f, 0.7f));
        float o2 = Gfx.fade(sub);
        SysFont.draw(g, line, cx, cy + 14, 6.5f, SystemTheme.TEXT, false, 0.5f);
        if (mine && r == Duel.Result.WON) {
            // what it paid
            String[] parts = {
                    Component.translatable("ui.beyondsolo.duel.hud.reward.gold", gold(d.tag.getLong("purse"))).getString(),
                    Component.translatable("ui.beyondsolo.duel.hud.reward.merit", gold(d.tag.getInt("merit"))).getString(),
                    Component.translatable("ui.beyondsolo.duel.hud.reward.xp", gold(d.tag.getLong("xp"))).getString()};
            int[] colors = {SystemTheme.GOLD, 0xFF8FB4FF, SystemTheme.GOOD};
            net.minecraft.resources.ResourceLocation[] icons = {Icons.COINS, Icons.MEDAL, Icons.STAR};
            float total = 0;
            float[] widths = new float[3];
            for (int i = 0; i < 3; i++) {
                widths[i] = SysFont.width(parts[i], 5.6f, true) + 18;
                total += widths[i] + (i > 0 ? 5 : 0);
            }
            float px = cx - total / 2f, py = cy + 25;
            for (int i = 0; i < 3; i++) {
                float k = Anim.outCubic(Anim.window(t, 0.5f + i * 0.12f, 0.8f + i * 0.12f));
                float o3 = Gfx.fade(k);
                Shapes.pill(px, py + (1 - k) * 4, widths[i], 11).fill(Gfx.alpha(colors[i], 0.18f)).border(0.6f, Gfx.alpha(colors[i], 0.8f)).draw(g);
                Shapes.icon(icons[i], px + 7, py + 5.5f + (1 - k) * 4, 7).fill(colors[i]).draw(g);
                SysFont.draw(g, parts[i], px + 13, SysFont.centerY(py + (1 - k) * 4, 11, 5.6f), 5.6f, Gfx.mix(colors[i], WHITE, 0.4f), true, 0f);
                Gfx.setAlpha(o3);
                px += widths[i] + 5;
            }
            if (d.tag.getBoolean("national")) {
                float k = Anim.outCubic(Anim.window(t, 1.1f, 1.6f));
                float o4 = Gfx.fade(k);
                String nat = Component.translatable("ui.beyondsolo.duel.hud.national").getString().toUpperCase(Locale.ROOT);
                float s2 = Mth.lerp(k, 1.5f, 1f);
                g.pose().pushPose();
                g.pose().translate(cx, cy + 46, 0);
                g.pose().scale(s2, s2, 1);
                float nw = SysFont.spacedWidth(nat, 8f, true, 2.2f) + 24;
                Shapes.pill(-nw / 2f, -7, nw, 14).fillH(Gfx.alpha(SystemTheme.GOLD, 0.15f), Gfx.alpha(SystemTheme.GOLD, 0.35f))
                        .border(0.8f, SystemTheme.GOLD).glow(6, Gfx.alpha(SystemTheme.GOLD, 0.5f)).draw(g);
                SysFont.spaced(g, nat, 0, -2.8f, 8f, Gfx.mix(SystemTheme.GOLD, WHITE, 0.5f), true, 2.2f, 0.5f);
                g.pose().popPose();
                Gfx.setAlpha(o4);
            }
        }
        Gfx.setAlpha(o2);
        Gfx.setAlpha(old);
    }

    private static String clock(long ticks) {
        long s = Math.max(0, ticks) / 20;
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    private static String gold(long amount) {
        return NumberFormat.getIntegerInstance(Locale.ROOT).format(amount);
    }

    private DuelHud() {}
}
