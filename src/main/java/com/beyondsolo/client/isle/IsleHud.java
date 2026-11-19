package com.beyondsolo.client.isle;

import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.breaks.BreakClient;
import com.beyondsolo.client.breaks.BreakHud;
import com.beyondsolo.client.combat.WorldProjector;
import com.beyondsolo.client.depths.DepthsClient;
import com.beyondsolo.client.evaluation.EvaluationOverlay;
import com.beyondsolo.client.gate.GateOverlay;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.hud.HudSpace;
import com.beyondsolo.client.key.RunHud;
import com.beyondsolo.client.quest.QuestOverlay;
import com.beyondsolo.client.skill.SkillOverlay;
import com.beyondsolo.worldboss.WorldBossKind;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * The Halcyon raid on screen.
 * <ul>
 *   <li><b>The raid's chip</b> at the top (under the boss bar and a break's timer): while the Association calls, its
 *   countdown and who has answered; on the island, what the raid is doing now (the beach's waves, the nests left to
 *   burn and which way the nearest lies, the hive, the queen, the king's birth), how long is left, and the hunters,
 *   allies and kills; after, the island retaken or lost and the way home.</li>
 *   <li><b>Marks in the world</b>: every nest still standing (its heart's blows filling a bar), the pit into the hive
 *   once it opens, and in the queen's chamber the ring that takes a crouching hunter back up.</li>
 *   <li><b>The System's moments</b> in the middle of the screen: the call, the landing, each wave, the push inland, a
 *   nest burned, the hive opening, the queen's fall stirring the cocoon, the island retaken or lost, and the raid's
 *   report (what it paid, the tally).</li>
 * </ul>
 * The queen's and the king's bars and banners are the great threats' ({@code WorldBossHud}). Without the System the
 * hunter sees none of this.
 */
public final class IsleHud {
    /** The hive's amber (the queen's colour), the king's venom, embers, and the call's alarm. */
    static final int AMBER = 0xFF000000 | WorldBossKind.QUEEN.color;
    static final int VENOM = 0xFF000000 | WorldBossKind.KING.color;
    static final int EMBER = 0xFFFF6A2A;
    static final int ALARM = 0xFFFF4A3A;
    private static final int WHITE = 0xFFFFFFFF;

    private static float chipIn;
    private static float shown;
    private static IsleClient.State last;
    private static IsleClient.Banner kicked;

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        boolean free = !GateOverlay.active() && !QuestOverlay.active() && !EvaluationOverlay.active() && !SkillOverlay.active()
                && !DepthsClient.bannerShowing() && !BreakClient.bannerShowing() && !com.beyondsolo.client.worldboss.WorldBossClient.bannerShowing();
        IsleClient.Banner b = IsleClient.banner(free);
        if (mc.options.hideGui || !ClientSystemData.get().hasSystem()) {
            shown = 0;
            return;
        }
        int w = g.guiWidth(), h = g.guiHeight();
        float partial = delta.getGameTimeDeltaPartialTick(false);
        IsleClient.State s = IsleClient.state();
        if (s != null && s.landed()) marks(g, mc, s, partial);
        float y = 6 + 34 * Anim.outCubic(GateOverlay.bossBarIn()) + 26 * RunHud.clockShown() + BreakHud.shownHeight();
        shown = chip(g, mc, w, y, s, partial);
        if (b != null) banner(g, w, h, b);
    }

    /** How much of the top of the screen the raid's chip takes (the great threats' bars go under it). */
    public static float shownHeight() {
        return shown;
    }

    static void reset() {
        chipIn = 0;
        shown = 0;
        last = null;
        kicked = null;
    }

    static String upper(Component c) {
        return c.getString().toUpperCase(Locale.ROOT);
    }

    static Component t(String key, Object... args) {
        return Component.translatable("isle.beyondsolo.hud." + key, args);
    }

    // ================================================================ the chip

    /** The raid's chip at the top; returns the height it takes. */
    private static float chip(GuiGraphics g, Minecraft mc, int w, float y, IsleClient.State live, float partial) {
        boolean show = live != null && (live.you != 3 && live.you != 6);
        if (show) last = live;
        chipIn = Anim.approach(chipIn, show ? 1 : 0, show ? 4f : 3f);
        if (chipIn < 0.01f || last == null) return 0;
        IsleClient.State s = last;
        float in = Anim.outCubic(chipIn);
        boolean muster = s.phase.equals("muster");
        boolean king = s.phase.equals("king");
        int c = s.phase.equals("failed") ? ALARM : king ? VENOM : muster ? ALARM : AMBER;

        // what it says: a label, a line under it, and a clock on the right
        String label;
        String line;
        String clock;
        boolean urgent = false;
        BlockPos toward = null;
        switch (s.phase) {
            case "muster" -> {
                label = upper(t("call"));
                line = (s.you == 2 ? t("call.going", s.joined, s.called) : s.you == 1 ? t("call.answer", s.rank.displayName())
                        : t("call.open", s.rank.displayName())).getString();
                clock = clock(s.now(s.muster));
                urgent = s.now(s.muster) < 20 * 20;
            }
            case "beach" -> {
                label = upper(t("beach"));
                line = t("beach.line", Math.max(1, s.wave), Math.max(1, s.waves), s.swarm).getString();
                clock = clock(s.now(s.left));
            }
            case "nests" -> {
                label = upper(t("nests"));
                line = t("nests.line", s.burnedCount(), s.nests.length).getString();
                clock = clock(s.now(s.left));
                toward = nearestNest(mc, s);
            }
            case "hive" -> {
                label = upper(t("hive"));
                line = t("hive.line").getString();
                clock = clock(s.now(s.left));
                if (!below(mc, s)) toward = s.descent;
            }
            case "queen" -> {
                label = upper(t("queen"));
                line = WorldBossKind.QUEEN.fullName().getString();
                clock = clock(s.now(s.left));
                if (!below(mc, s)) toward = s.descent;
            }
            case "king" -> {
                int born = s.now(s.kingIn);
                label = upper(t(born >= 0 ? "stirs" : "king"));
                line = born >= 0 ? t("stirs.line", WorldBossKind.KING.bossName(), clock(born)).getString() : WorldBossKind.KING.fullName().getString();
                clock = clock(s.now(s.left));
                urgent = born >= 0;
                if (!below(mc, s)) toward = s.descent;
            }
            case "cleared" -> {
                label = upper(t("cleared"));
                line = t("home", clock(s.now(s.home))).getString();
                clock = clock(s.now(s.home));
            }
            default -> {
                label = upper(t("failed"));
                line = t("home", clock(s.now(s.home))).getString();
                clock = clock(s.now(s.home));
            }
        }
        String tally = s.landed() || s.over()
                ? t("tally", s.fighting, s.allies, s.alliesTotal, s.kills).getString()
                : t("call.answered", s.joined, s.called).getString();
        if (s.landed() && s.left >= 0 && s.now(s.left) < 20 * 60 * 5) urgent = true;

        float labelSize = 5.6f, lineSize = 5.2f, tallySize = 4.8f, bigSize = 9.5f;
        float labelW = SysFont.spacedWidth(label, labelSize, true, 1.4f);
        float lineW = SysFont.width(line, lineSize, true) + (toward != null ? 14 : 0);
        float tallyW = SysFont.width(tally, tallySize, false);
        float pips = s.landed() && s.nests.length > 0 ? s.nests.length * 7 + 6 : 0;
        float bigW = SysFont.width(clock, bigSize, true);
        float bh = 30;
        float textW = Math.max(labelW + pips, Math.max(lineW, tallyW));
        float bw = 26 + textW + 14 + bigW + 12;
        float x = (w - bw) / 2f;
        y -= 14 * (1 - in);
        float old = Gfx.fade(in);
        HudSpace.claimTop(x, y, bw, bh);
        float blink = urgent ? 0.5f + 0.5f * Anim.pulse(0.9f) : 0.5f + 0.5f * Anim.pulse(3f);
        Shapes.box(x, y, bw, bh).radius(9).fillV(Gfx.mix(0xC00A1222, Gfx.alpha(c, 0.75f), 0.16f), 0xC0050912)
                .border(0.8f, Gfx.alpha(c, 0.55f + 0.35f * blink), Gfx.alpha(c, 0.25f))
                .glow(4 + 4 * blink, Gfx.alpha(c, 0.16f + 0.12f * blink)).draw(g);

        // the emblem: the hive's comb in a turning ring (the call: an alarm bell)
        float ix = x + 13, iy = y + bh / 2f;
        Shapes.circle(ix, iy, 8.5f).fillV(Gfx.alpha(c, 0.32f), Gfx.alpha(c, 0.08f)).border(0.6f, Gfx.alpha(c, 0.85f)).draw(g);
        Shapes.ring(ix, iy, 10.2f, 0.55f).fill(Gfx.alpha(c, 0.7f)).dashes(muster ? 8 : 6, 0.5f).rotation(Anim.loop(muster ? 2.5f : 6f)).draw(g);
        ResourceLocation icon = muster ? Icons.BELL : s.over() ? (s.phase.equals("cleared") ? Icons.FLAG : Icons.HEART_BREAK)
                : king || s.phase.equals("queen") ? Icons.CROWN : s.phase.equals("nests") ? Icons.FLAME : Icons.HEXAGON;
        Shapes.icon(icon, ix, iy, 9).fill(Gfx.mix(c, WHITE, 0.5f)).glow(2, Gfx.alpha(c, 0.6f)).draw(g);

        // the label, and the nests as pips after it
        float tx = x + 26;
        SysFont.spaced(g, label, tx, y + 4, labelSize, Gfx.mix(c, WHITE, 0.45f), true, 1.4f, 0f);
        if (pips > 0) {
            float px = tx + labelW + 8;
            for (int i = 0; i < s.nests.length; i++) {
                boolean burned = i < s.burned.length && s.burned[i];
                float fill = burned ? 1 : (i < s.blows.length ? Mth.clamp(s.blows[i] / (float) s.needed, 0, 1) : 0);
                float cx = px + i * 7 + 2.5f, cy = y + 6.4f;
                Shapes.icon(Icons.HEXAGON, cx, cy, 6).fill(burned ? Gfx.alpha(0xFF2A1A12, 0.95f) : Gfx.alpha(AMBER, 0.85f))
                        .glow(burned ? 0 : 2, Gfx.alpha(AMBER, 0.4f)).draw(g);
                if (burned) Shapes.icon(Icons.FLAME, cx, cy - 0.3f, 4.2f).fill(EMBER).glow(2, Gfx.alpha(EMBER, 0.6f)).draw(g);
                else if (fill > 0) Shapes.box(cx - 2.5f, cy + 3.6f, 5 * fill, 0.9f).fill(EMBER).draw(g);
            }
        }
        // the line: what to do now (and which way, for a nest or the pit)
        float ly = y + 13.2f;
        SysFont.draw(g, line, tx, ly, lineSize, SystemTheme.TEXT, true, 0f);
        if (toward != null) {
            Vec3 me = mc.player.getPosition(partial);
            double dx = toward.getX() + 0.5 - me.x, dz = toward.getZ() + 0.5 - me.z;
            float bearing = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float rel = Mth.wrapDegrees(bearing - mc.player.getViewYRot(partial));
            float ax = tx + SysFont.width(line, lineSize, true) + 7;
            arrow(g, ax, ly + 2, rel, Gfx.mix(c, WHITE, 0.4f));
            String far = Math.round(Math.sqrt(dx * dx + dz * dz)) + "m";
            SysFont.draw(g, far, ax + 5, ly + 0.4f, 4.6f, SystemTheme.TEXT_SOFT, false, 0f);
        }
        SysFont.draw(g, tally, tx, y + 21.4f, tallySize, SystemTheme.TEXT_DIM, false, 0f);

        // the clock, beating for the last seconds of the call and the king's birth
        float bx = x + bw - 12 - bigW;
        float scale = 1;
        if (urgent) {
            float frac = (float) (Anim.now() % 1.0);
            scale = 1 + 0.16f * (1 - frac) * (1 - frac) * (1 - frac);
        }
        g.pose().pushPose();
        g.pose().translate(bx + bigW / 2f, y + bh / 2f, 0);
        g.pose().scale(scale, scale, 1);
        SysFont.draw(g, clock, -bigW / 2f, SysFont.centerY(-bh / 2f, bh, bigSize), bigSize, Gfx.mix(c, WHITE, urgent ? 0.3f : 0.55f), true, 0f);
        g.pose().popPose();
        Gfx.setAlpha(old);
        return (bh + 5) * in;
    }

    /** Whether the hunter is down in the queen's chamber (well below the hive's mouth). */
    private static boolean below(Minecraft mc, IsleClient.State s) {
        return mc.player.getY() < s.mouth.getY() - 8;
    }

    /** The nearest nest still standing, or null. */
    private static BlockPos nearestNest(Minecraft mc, IsleClient.State s) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < s.nests.length; i++) {
            if (i < s.burned.length && s.burned[i]) continue;
            double d = mc.player.distanceToSqr(Vec3.atCenterOf(s.nests[i]));
            if (d < bd) {
                bd = d;
                best = s.nests[i];
            }
        }
        return best;
    }

    // ================================================================ marks in the world

    private static void marks(GuiGraphics g, Minecraft mc, IsleClient.State s, float partial) {
        Vec3 me = mc.player.getPosition(partial);
        boolean down = below(mc, s);
        if (!down && (s.phase.equals("beach") || s.phase.equals("nests"))) {
            for (int i = 0; i < s.nests.length; i++) {
                if (i < s.burned.length && s.burned[i]) continue;
                BlockPos n = s.nests[i];
                double d = me.distanceTo(Vec3.atCenterOf(n));
                if (d < 6) continue;
                float blows = i < s.blows.length ? Mth.clamp(s.blows[i] / (float) s.needed, 0, 1) : 0;
                mark(g, new Vec3(n.getX() + 0.5, n.getY() + 4.5, n.getZ() + 0.5), t("mark.nest", i + 1).getString(), d, AMBER, Icons.HEXAGON, blows);
            }
        }
        if (s.hiveOpen() && !down) {
            double d = me.distanceTo(Vec3.atCenterOf(s.descent));
            if (d > 3) mark(g, new Vec3(s.descent.getX() + 0.5, s.mouth.getY() + 2.5, s.descent.getZ() + 0.5), t("mark.pit").getString(), d,
                    s.phase.equals("king") ? VENOM : AMBER, Icons.ARROW, -1);
        }
        if (down && (s.hiveOpen() || s.over())) {
            Vec3 ring = new Vec3(s.arrival.getX() + 0.5, s.arrival.getY() + 1.6, s.arrival.getZ() + 0.5);
            double d = me.distanceTo(ring);
            if (d < 26 && d > 1.5) mark(g, ring, t("mark.climb").getString(), d, SystemTheme.ACCENT, Icons.ARROW, -1);
        }
    }

    /** A mark over a place in the world: an icon, its name and how far, and a bar under it ({@code fill} < 0: none). */
    private static void mark(GuiGraphics g, Vec3 at, String name, double dist, int c, ResourceLocation icon, float fill) {
        float[] p = WorldProjector.project(at);
        if (p == null) return;
        float near = (float) Mth.clamp((dist - 4) / 10.0, 0.35, 1.0);
        float old = Gfx.fade(near);
        float x = p[0], y = p[1];
        float bob = Mth.sin((float) Anim.now() * 2.4f) * 1.2f;
        y += bob;
        Shapes.circle(x, y, 6.5f).fillV(Gfx.alpha(0xFF0A0C14, 0.82f), Gfx.alpha(0xFF050610, 0.82f)).border(0.8f, Gfx.alpha(c, 0.9f))
                .glow(5, Gfx.alpha(c, 0.35f)).draw(g);
        if (icon == Icons.ARROW) {
            // the arrow points down: into the pit, or onto the ring
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(180));
            Shapes.icon(icon, 0, 0, 7.5f).fill(Gfx.mix(c, WHITE, 0.4f)).draw(g);
            g.pose().popPose();
        } else {
            Shapes.icon(icon, x, y, 7.5f).fill(Gfx.mix(c, WHITE, 0.4f)).glow(2, Gfx.alpha(c, 0.6f)).draw(g);
        }
        String far = Math.round(dist) + "m";
        String text = name + "  " + far;
        float tw = SysFont.width(text, 5f, true);
        Shapes.pill(x - tw / 2f - 4, y + 8, tw + 8, 9).fill(Gfx.alpha(0xFF06070E, 0.72f)).draw(g);
        SysFont.draw(g, name, x - tw / 2f, y + 9.6f, 5f, Gfx.mix(c, WHITE, 0.5f), true, 0f);
        SysFont.draw(g, far, x - tw / 2f + SysFont.width(name + "  ", 5f, true), y + 9.6f, 5f, SystemTheme.TEXT_SOFT, false, 0f);
        if (fill >= 0) {
            float bw = 26;
            Ui.bar(g, x - bw / 2f, y + 19, bw, 2.4f, fill, EMBER, 0xFFFFD08A);
        }
        Gfx.setAlpha(old);
    }

    /** A small arrow, pointing up for ahead and turned {@code degrees} clockwise. */
    private static void arrow(GuiGraphics g, float cx, float cy, float degrees, int color) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(degrees));
        for (int side = -1; side <= 1; side += 2) {
            g.pose().pushPose();
            g.pose().translate(0, -2.6f, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(side * 40));
            Shapes.box(-0.55f, 0, 1.1f, 3.6f).radius(0.55f).fill(color).draw(g);
            g.pose().popPose();
        }
        Shapes.box(-0.5f, -2.2f, 1f, 5f).radius(0.5f).fill(color).draw(g);
        g.pose().popPose();
    }

    // ================================================================ the System's moments

    private static void banner(GuiGraphics g, int w, int h, IsleClient.Banner b) {
        float t = b.age();
        float live = 1 - Anim.outCubic(Anim.window(t, b.duration - 0.45f, b.duration));
        if (kicked != b) {
            kicked = b;
            if (b.id.equals("hive") || b.id.equals("lost")) GateOverlay.addTrauma(0.12f);
            if (b.id.equals("wave")) GateOverlay.addTrauma(0.06f);
        }
        switch (b.id) {
            case "call" -> call(g, w, h, b, t, live);
            case "result" -> IsleReport.render(g, w, h, b, t, live);
            default -> moment(g, w, h, b, t, live);
        }
    }

    /** The Association's call: the screen's edges throb, an alert tag blinks, and the island's name is stamped in. */
    private static void call(GuiGraphics g, int w, int h, IsleClient.Banner b, float t, float live) {
        float throb = Math.max(0, Mth.sin(t * 6f)) * (1 - Anim.window(t, 0f, 3f));
        Shapes.vignette(0, 0, w, h, 0.25f, 1.3f).fill(Gfx.alpha(ALARM, (0.08f + 0.22f * throb) * live)).draw(g);
        float old = Gfx.fade(live);
        float cx = w / 2f, y = h * 0.22f;
        float pw = Math.min(w * 0.9f, 300), ph = 74;
        float open = Anim.outBack(Anim.window(t, 0f, 0.45f));
        g.pose().pushPose();
        g.pose().translate(cx, y + ph / 2f, 0);
        g.pose().scale(1, Math.max(0.02f, open), 1);
        Ui.panel(g, -pw / 2f, -ph / 2f, pw, ph, 10, ALARM, 1f);
        g.pose().popPose();
        if (open > 0.9f) {
            String tag = upper(t("banner.call.tag"));
            float blink = 0.55f + 0.45f * Math.abs(Mth.sin(t * 5f));
            float tagW = SysFont.spacedWidth(tag, 5.8f, true, 1.8f) + 26;
            Shapes.pill(cx - tagW / 2f, y - 6, tagW, 12).fill(Gfx.alpha(0xFF2A0606, 0.92f)).border(0.9f, Gfx.alpha(ALARM, blink))
                    .glow(7, Gfx.alpha(ALARM, 0.45f * blink)).draw(g);
            Shapes.icon(Icons.EXCLAIM, cx - tagW / 2f + 9, y, 7).fill(Gfx.mix(ALARM, WHITE, 0.3f)).draw(g);
            SysFont.spaced(g, tag, cx + 5, y - 2.8f, 5.8f, 0xFFFFE2DC, true, 1.8f, 0.5f);
            float stamp = Anim.window(t, 0.25f, 0.5f);
            if (stamp > 0) {
                float s = Mth.lerp(Anim.outCubic(stamp), 2.2f, 1f);
                String word = upper(t("banner.call"));
                float o2 = Gfx.fade(Math.min(1f, stamp * 2));
                g.pose().pushPose();
                g.pose().translate(cx, y + 18, 0);
                g.pose().scale(s, s, 1);
                SysFont.spaced(g, word, 0, -6, 16f, WHITE, true, 3f, 0.5f);
                g.pose().popPose();
                Gfx.setAlpha(o2);
            }
            float sub = Anim.outCubic(Anim.window(t, 0.8f, 1.3f));
            if (sub > 0.01f) {
                float o3 = Gfx.fade(sub);
                SysFont.draw(g, b.b == 1 ? t("banner.call.sub.one") : t("banner.call.sub", b.b), cx, y + 34, 6.6f, 0xFFFFD9CF, false, 0.5f);
                String answer = t("banner.call.answer", clock(Math.max(0, b.a - (int) (t * 20)))).getString();
                float aw = SysFont.width(answer, 6.2f, true) + 16;
                Shapes.pill(cx - aw / 2f, y + 45, aw, 12).fill(Gfx.alpha(AMBER, 0.14f)).border(0.7f, Gfx.alpha(AMBER, 0.7f)).draw(g);
                SysFont.draw(g, answer, cx, y + 47.6f, 6.2f, Gfx.mix(AMBER, WHITE, 0.4f), true, 0.5f);
                Gfx.setAlpha(o3);
            }
        }
        Gfx.setAlpha(old);
    }

    /** The other moments: a tag, words stamped in under it, and a line of what it means. */
    private static void moment(GuiGraphics g, int w, int h, IsleClient.Banner b, float t, float live) {
        String id = b.id;
        int c = switch (id) {
            case "wave", "lost" -> ALARM;
            case "nest" -> EMBER;
            case "stirs" -> VENOM;
            case "retaken" -> SystemTheme.GOLD;
            default -> AMBER;
        };
        Component tag = switch (id) {
            case "wave" -> t("banner.wave.tag", b.a, b.b);
            case "nest" -> t("banner.nest.tag", b.a, b.b);
            default -> t("banner." + id + ".tag");
        };
        Component title = t("banner." + id);
        Component sub = switch (id) {
            // a hunter who landed alone is told "you"
            case "landing" -> b.a <= 1 ? (b.b > 0 ? t("banner.landing.sub.you.allies", b.b) : t("banner.landing.sub.you"))
                    : b.b > 0 ? t("banner.landing.sub.allies", b.a, b.b) : t("banner.landing.sub", b.a);
            case "push" -> b.a == 1 ? t("banner.push.sub.one") : t("banner.push.sub", b.a);
            case "nest" -> b.b - b.a > 0 ? t("banner.nest.sub", b.b - b.a) : t("banner.nest.sub.last");
            case "stirs" -> t("banner.stirs.sub", WorldBossKind.KING.bossName());
            case "retaken" -> t("banner.retaken.sub", clock(b.a));
            case "lost" -> t("banner.lost.sub." + switch (b.a) {
                case 1 -> "nobody";
                case 2 -> "fallen";
                case 3 -> "time";
                case 4 -> "called_off";
                default -> "none";
            });
            default -> t("banner." + id + ".sub");
        };
        // the edges: red for a wave, embers for a nest, a dark hush for the hive, gold for the island retaken
        float edge = 1 - Anim.window(t, 0.2f, 2.4f);
        Shapes.vignette(0, 0, w, h, 0.3f, 1.35f).fill(Gfx.alpha(id.equals("hive") ? 0xFF0A0602 : c, (id.equals("hive") ? 0.45f : 0.16f) * edge * live)).draw(g);
        float old = Gfx.fade(live);
        float cx = w / 2f, y = h * (id.equals("wave") || id.equals("nest") ? 0.3f : 0.24f);
        String tagText = upper(tag);
        String word = upper(title);
        boolean big = id.equals("hive") || id.equals("retaken") || id.equals("lost") || id.equals("landing");
        float size = big ? 18f : 13f, track = big ? 3.4f : 2.6f;
        float ww = SysFont.spacedWidth(word, size, true, track);
        float bw = Math.min(w * 0.94f, Math.max(240, ww + 70));
        float tear = Anim.outCubic(Anim.window(t, 0.05f, 0.45f));
        Shapes.pill(cx - bw / 2f * tear, y - 10, bw * tear, big ? 52 : 42).fill(Gfx.alpha(0xFF07040A, 0.62f)).glow(18, Gfx.alpha(0xFF07040A, 0.4f * tear)).draw(g);
        Ui.divider(g, cx - bw * 0.42f * tear, y + (big ? 39 : 30), bw * 0.84f * tear, Gfx.alpha(c, 0.75f));
        if (id.equals("retaken") && tear > 0.5f) {
            Shapes.rays(cx, y + 14, 150, 20).fill(Gfx.alpha(c, 0.14f * live)).rotation((float) (Anim.now() * 0.03)).additive().draw(g);
        }
        // the tag
        float tagIn = Anim.outBack(Anim.window(t, 0.1f, 0.4f));
        if (tagIn > 0.01f) {
            float tagW = SysFont.spacedWidth(tagText, 5.6f, true, 1.6f) + 16;
            g.pose().pushPose();
            g.pose().translate(cx, y - 10, 0);
            g.pose().scale(tagIn, tagIn, 1);
            Shapes.pill(-tagW / 2f, -6, tagW, 12).fill(Gfx.alpha(0xFF0A0610, 0.92f)).border(0.8f, Gfx.alpha(c, 0.9f)).glow(6, Gfx.alpha(c, 0.4f)).draw(g);
            SysFont.spaced(g, tagText, 0, -2.8f, 5.6f, Gfx.mix(c, WHITE, 0.55f), true, 1.6f, 0.5f);
            g.pose().popPose();
        }
        // the words, stamped
        float stamp = Anim.window(t, 0.2f, 0.45f);
        if (stamp > 0) {
            float s = Mth.lerp(Anim.outCubic(stamp), 2.2f, 1f);
            float fit = Math.min(1f, (bw - 20) / Math.max(1, ww));
            float o2 = Gfx.fade(Math.min(1f, stamp * 2));
            float settle = Anim.window(t, 0.45f, 1.2f);
            float shake = id.equals("wave") || id.equals("lost") ? (1 - settle) * 2.2f : 0;
            float jx = (Mth.sin(t * 70f) * 0.5f) * shake, jy = (Mth.cos(t * 53f) * 0.5f) * shake;
            g.pose().pushPose();
            g.pose().translate(cx + jx, y + 9 + jy, 0);
            g.pose().scale(s * fit, s * fit, 1);
            if (settle < 1) SysFont.spaced(g, word, 1.8f, -size * 0.35f + 0.6f, size, Gfx.alpha(c, 0.55f * (1 - settle)), true, track, 0.5f);
            SysFont.spaced(g, word, 0, -size * 0.35f, size, WHITE, true, track, 0.5f);
            g.pose().popPose();
            Gfx.setAlpha(o2);
        }
        // what it means
        float subIn = Anim.outCubic(Anim.window(t, 0.7f, 1.2f));
        if (subIn > 0.01f) {
            float o3 = Gfx.fade(subIn);
            SysFont.draw(g, sub, cx, y + (big ? 26 : 20), 6.6f, Gfx.mix(c, WHITE, 0.6f), false, 0.5f);
            Gfx.setAlpha(o3);
        }
        Gfx.setAlpha(old);
    }

    /** m:ss from ticks. */
    static String clock(int ticks) {
        if (ticks < 0) return "-:--";
        int s = (int) Math.ceil(ticks / 20.0);
        if (s >= 3600) return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    private IsleHud() {}
}
