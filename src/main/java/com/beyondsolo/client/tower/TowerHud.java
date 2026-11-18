package com.beyondsolo.client.tower;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gate.GateOverlay;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.network.TowerPayload;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.tower.TowerFloors;
import com.beyondsolo.tower.TowerRun;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The Crimson Citadel on screen.
 * <ul>
 *   <li><b>The pull</b>: the edges close in red, "THE CITADEL CALLS", and a fall to black as the System takes them.</li>
 *   <li><b>A floor's title card</b>: the wing's name large, the floor and what it holds under it (a gatekeeper's own
 *   name on the tenth floors).</li>
 *   <li><b>The plaque</b> at the top, under the boss bar: the floor in a crimson seal, the wing, the wave and the foes
 *   still standing, a siege's time left; in the spire the best floor to beat; in a rush the bout and the clock.</li>
 *   <li><b>Banners</b> for the moments: a wave, a checkpoint, a record, the Sigil, the blood moon, a champion, the last
 *   ten seconds of a siege, a sealed cache left behind.</li>
 *   <li><b>The climb</b>: the screen goes dark on the stair and the next floor's number rises out of it.</li>
 *   <li><b>The ends</b>: the throne taken, a rush cleared (its time stamped down), fallen, or leaving; and the exit
 *   sigil's hold around the crosshair.</li>
 * </ul>
 */
public final class TowerHud {
    static final int CRIMSON = 0xFFFF2E3F;
    static final int CRIMSON_SOFT = 0xFFFF6B76;
    static final int DEEP = 0xFF4A0710;
    static final int EMBER = 0xFFFF7A3D;
    static final int PALE = 0xFFFFE6DE;
    static final int VOID = 0xFFB27CFF;
    static final int GLASS_TOP = 0xE6220A10;
    static final int GLASS_BOTTOM = 0xE60D0306;

    private static float plaqueIn;
    private static float railDrop;
    private static float holdShown;

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int w = g.guiWidth(), h = g.guiHeight();
        float partial = delta.getGameTimeDeltaPartialTick(true);
        TowerPayload r = TowerClient.run;
        float age = TowerClient.phaseAge();
        boolean hide = mc.options.hideGui;

        boolean plaque = !hide && (r.phase() == TowerPayload.FIGHT || r.phase() == TowerPayload.CLEARED || r.phase() == TowerPayload.BREATHER
                || r.phase() == TowerPayload.RUSH_DONE || r.phase() == TowerPayload.ARRIVING && age > 2.4f);
        plaqueIn = Anim.approach(plaqueIn, plaque ? 1 : 0, plaque ? 4f : 7f);
        if (plaqueIn > 0.01f) plaque(g, w, r, partial);

        // a boss's fall plays its own DEFEATED moment first: the breather's ring and a rush's end card wait for it to finish
        float cardAge = TowerClient.cardAge();

        switch (r.phase()) {
            case TowerPayload.PULL -> pull(g, w, h, age, r);
            case TowerPayload.ARRIVING -> arriving(g, w, h, age, r);
            case TowerPayload.FIGHT -> {
                if (r.has(TowerPayload.F_MOON)) moonEdges(g, w, h);
            }
            case TowerPayload.CLEARED -> {
                if (!hide) cleared(g, w, h, age, r);
            }
            case TowerPayload.CLIMBING -> climbing(g, w, h, age, r);
            case TowerPayload.VICTORY -> victory(g, w, h, age);
            case TowerPayload.BREATHER -> {
                if (!hide) breather(g, w, h, age, cardAge, r, partial);
            }
            case TowerPayload.RUSH_DONE -> rushDone(g, w, h, cardAge, r, partial);
            case TowerPayload.FALLEN -> fallen(g, w, h, age, r);
            case TowerPayload.LEAVING -> leaving(g, w, h, age);
            default -> afterglow(g, w, h);
        }
        if (!hide && TowerClient.inRun()) {
            banner(g, w, plaqueBottom());
            exitHold(g, w, h, r);
        }
    }

    // ================================================================ the plaque

    /** Under the boss bar when there is one, and under the player's status rail (burns, venom) when it reaches this far. */
    private static float plaqueTop() {
        return 6 + 34 * Math.max(Anim.outCubic(GateOverlay.bossBarIn()), Anim.outCubic(railDrop));
    }

    private static float plaqueBottom() {
        return plaqueTop() + 30 * Anim.outCubic(plaqueIn) + 6;
    }

    private static void plaque(GuiGraphics g, int w, TowerPayload r, float partial) {
        boolean rush = r.mode() == TowerPayload.RUSH;
        boolean spire = r.mode() == TowerPayload.ENDLESS;
        int accent = spire ? VOID : CRIMSON;
        float in = Anim.outCubic(plaqueIn);
        float y = plaqueTop() - 16 * (1 - in);
        float bh = 26;

        // the left: what the place is called; the right: how the fight stands
        String head = rush ? r.title().getString().toUpperCase() : Component.translatable("tower.beyondsolo.hud.floor", r.floor()).getString();
        String sub = rush ? Component.translatable("tower.beyondsolo.hud.bout", r.bout(), Math.max(1, r.bouts())).getString()
                : spire ? Component.translatable("tower.beyondsolo.spire").getString() : r.title().getString();
        String status = status(r, partial);
        String best = bestLine(r);
        float headW = SysFont.spacedWidth(head, 6.2f, true, 1.3f);
        float subW = SysFont.width(sub, 5.4f, false);
        float bestW = best == null ? 0 : SysFont.width(best, 5f, true) + 9;
        float leftW = Math.max(headW, subW + (bestW > 0 ? bestW + 4 : 0));
        float statusW = status == null ? 0 : SysFont.width(status, rush || TowerClient.siege() ? 9f : 7f, true);
        float bw = 28 + leftW + (status == null ? 10 : 18 + statusW + 10);
        float x = (w - bw) / 2f;
        boolean crowded = com.beyondsolo.client.combat.CombatHud.railEnd() > x - 4;
        railDrop = Anim.approach(railDrop, crowded ? 1 : 0, 6f);

        float old = Gfx.fade(Math.min(1, plaqueIn * 1.3f));
        boolean won = r.phase() == TowerPayload.CLEARED || r.phase() == TowerPayload.RUSH_DONE;
        int edge = won ? SystemTheme.GOLD : accent;
        Shapes.box(x, y, bw, bh).radius(bh / 2f).fillV(GLASS_TOP, GLASS_BOTTOM)
                .border(0.8f, Gfx.alpha(edge, 0.85f), Gfx.alpha(edge, 0.25f)).glow(6, Gfx.alpha(edge, won ? 0.35f : 0.2f)).draw(g);

        // the seal: the floor's number (or the bout's) in a turning crimson ring
        float sx = x + 13, sy = y + bh / 2f;
        Shapes.circle(sx, sy, 9.5f).fillV(Gfx.alpha(accent, 0.38f), Gfx.alpha(DEEP, 0.6f)).border(0.7f, Gfx.alpha(accent, 0.9f)).draw(g);
        Shapes.ring(sx, sy, 11.2f, 0.5f).fill(Gfx.alpha(accent, 0.55f)).dashes(12, 0.45f).rotation(Anim.loop(9f)).draw(g);
        int kind = r.kind();
        if (rush || kind == TowerFloors.Kind.KEEPER.ordinal() || kind == TowerFloors.Kind.LORD.ordinal()) {
            Shapes.icon(kind == TowerFloors.Kind.LORD.ordinal() ? Icons.CROWN : Icons.SKULL, sx, sy, 10).fill(PALE).glow(2, Gfx.alpha(accent, 0.7f)).draw(g);
        } else {
            String n = String.valueOf(r.floor());
            float size = n.length() >= 3 ? 6f : 7.5f;
            SysFont.draw(g, n, sx, SysFont.centerY(sy - 5, 10, size), size, PALE, true, 0.5f);
        }

        float tx = x + 27;
        SysFont.spaced(g, head, tx, y + 4.5f, 6.2f, Gfx.mix(accent, 0xFFFFFFFF, 0.55f), true, 1.3f, 0f);
        SysFont.draw(g, sub, tx, y + 14.5f, 5.4f, SystemTheme.TEXT_SOFT, false, 0f);
        if (best != null) {
            boolean record = r.has(TowerPayload.F_RECORD);
            Ui.chip(g, tx + subW + 4, y + 13.25f, 7.5f, best, 5f, record ? SystemTheme.GOLD : SystemTheme.TEXT_DIM, record);
        }
        if (status != null) {
            float rx = x + bw - 10;
            Shapes.box(rx - statusW - 8, y + 5, 0.75f, bh - 10).fill(Gfx.alpha(edge, 0.35f)).draw(g);
            boolean big = rush || TowerClient.siege();
            int c = won ? 0xFFFFE6A8 : TowerClient.siege() && TowerClient.clockNow(partial) < 200 && r.phase() == TowerPayload.FIGHT ? CRIMSON_SOFT : SystemTheme.TEXT;
            SysFont.draw(g, status, rx, SysFont.centerY(y, bh, big ? 9f : 7f), big ? 9f : 7f, c, true, 1f);
        }
        Gfx.setAlpha(old);
    }

    /** The right of the plaque: the clock, a siege's time, the wave and the foes, or "cleared". */
    private static String status(TowerPayload r, float partial) {
        if (r.mode() == TowerPayload.RUSH) return TowerRun.time((long) TowerClient.clockNow(partial));
        if (r.phase() == TowerPayload.CLEARED) return Component.translatable("tower.beyondsolo.hud.cleared_short").getString();
        if (r.phase() != TowerPayload.FIGHT) return null;
        if (TowerClient.siege()) return TowerRun.time((long) TowerClient.clockNow(partial));
        int kind = r.kind();
        if (kind == TowerFloors.Kind.KEEPER.ordinal() || kind == TowerFloors.Kind.LORD.ordinal()) {
            return r.foes() > 1 ? Component.translatable("tower.beyondsolo.hud.foes", r.foes()).getString() : null;
        }
        if (r.waves() > 1) return Component.translatable("tower.beyondsolo.hud.wave_foes", r.wave(), r.waves(), r.foes()).getString();
        return Component.translatable("tower.beyondsolo.hud.foes", r.foes()).getString();
    }

    @org.jetbrains.annotations.Nullable
    private static String bestLine(TowerPayload r) {
        if (r.has(TowerPayload.F_RECORD) && r.mode() != TowerPayload.CASTLE) return Component.translatable("tower.beyondsolo.hud.record").getString();
        if (r.mode() == TowerPayload.ENDLESS && r.best() > 0) return Component.translatable("tower.beyondsolo.hud.best_floor", r.best()).getString();
        if (r.mode() == TowerPayload.RUSH && r.best() >= 0) return Component.translatable("tower.beyondsolo.hud.best_time", TowerRun.time(r.best())).getString();
        return null;
    }

    // ================================================================ moments

    private static void pull(GuiGraphics g, int w, int h, float age, TowerPayload r) {
        float total = Math.max(0.5f, r.length() / 20f);
        float k = Mth.clamp(age / total, 0, 1);
        Shapes.vignette(0, 0, w, h, 0.62f - 0.4f * k, 1.25f).fill(Gfx.alpha(DEEP, 0.3f + 0.6f * k)).draw(g);
        float a = Anim.outCubic(Anim.window(age, 0.1f, 0.6f)) * (1 - Anim.window(k, 0.82f, 0.95f));
        if (a > 0.01f) {
            float old = Gfx.fade(a);
            float cy = h * 0.32f;
            String t = Component.translatable("tower.beyondsolo.hud.pull").getString();
            float size = 16 + 3 * (1 - Anim.outCubic(Anim.window(age, 0.1f, 0.8f)));
            SysFont.spaced(g, Component.translatable("tower.beyondsolo.hud.system").getString(), w / 2f, cy - 18, 6.5f, CRIMSON_SOFT, true, 3f, 0.5f);
            SysFont.spaced(g, t, w / 2f, cy - 8, size, 0xFFFFFFFF, true, 3f, 0.5f);
            SysFont.draw(g, sub(r), w / 2f, cy + 14, 7.5f, SystemTheme.TEXT_SOFT, false, 0.5f);
            Gfx.setAlpha(old);
        }
        // the System's ring closing round the middle of the screen
        float ring = Anim.window(age, 0.3f, total);
        Shapes.ring(w / 2f, h / 2f, Math.max(w, h) * 0.6f * (1 - Anim.inOutCubic(ring)) + 12, 1.6f).fill(Gfx.alpha(CRIMSON, 0.7f * ring))
                .glow(8, Gfx.alpha(CRIMSON, 0.4f * ring)).draw(g);
        float black = Anim.window(k, 0.85f, 1f);
        if (black > 0) g.fill(0, 0, w, h, Gfx.alpha(0xFF000000, black));
    }

    private static String sub(TowerPayload r) {
        if (r.mode() == TowerPayload.RUSH) return Component.translatable("tower.beyondsolo.hud.pull.rush", r.title()).getString();
        if (r.mode() == TowerPayload.ENDLESS) return Component.translatable("tower.beyondsolo.hud.pull.spire").getString();
        return Component.translatable("tower.beyondsolo.hud.pull.floor", r.floor()).getString();
    }

    /** Out of the black, then the floor's title card. */
    private static void arriving(GuiGraphics g, int w, int h, float age, TowerPayload r) {
        float lift = 1 - Anim.window(age, 0.05f, 0.7f);
        if (lift > 0) g.fill(0, 0, w, h, Gfx.alpha(0xFF000000, lift));
        boolean rush = r.mode() == TowerPayload.RUSH;
        String over = rush ? Component.translatable("tower.beyondsolo.hud.rush_head").getString()
                : r.mode() == TowerPayload.ENDLESS ? Component.translatable("tower.beyondsolo.hud.floor", r.floor()).getString()
                : Component.translatable("tower.beyondsolo.hud.floor", r.floor()).getString();
        titleCard(g, w, h, age - 0.3f, over, r.title().getString(), r.subtitle().getString(), r.mode() == TowerPayload.ENDLESS ? VOID : CRIMSON,
                r.kind() == TowerFloors.Kind.LORD.ordinal() ? Icons.CROWN : r.kind() == TowerFloors.Kind.KEEPER.ordinal() || rush ? Icons.SKULL : Icons.FLAG);
    }

    /** Big centred title between two lines drawing out, the line above it spaced out, the subtitle typed in. */
    private static void titleCard(GuiGraphics g, int w, int h, float age, String over, String title, String subtitle, int accent, ResourceLocation icon) {
        if (age <= 0) return;
        float in = Anim.outCubic(Anim.window(age, 0, 0.7f));
        float out = 1 - Anim.window(age, 2.4f, 3.0f);
        float a = in * out;
        if (a <= 0.01f) return;
        float cy = h * 0.3f;
        float old = Gfx.fade(a);
        SysFont.spaced(g, over.toUpperCase(), w / 2f, cy - 27, 7f, Gfx.mix(accent, 0xFFFFFFFF, 0.35f), true, 3.2f, 0.5f);
        String t = title.toUpperCase();
        float size = 20 + 5 * (1 - in);
        float tw = Math.min(w - 40, SysFont.spacedWidth(t, size, true, 2.6f));
        Shapes.box(w / 2f - tw / 2f - 28, cy - 15, tw + 56, 28).radius(14).fill(Gfx.alpha(DEEP, 0.5f)).glow(16, Gfx.alpha(DEEP, 0.4f)).draw(g);
        SysFont.spaced(g, t, w / 2f, cy - 12, size, 0xFFFFFFFF, true, 2.6f, 0.5f);
        float line = (w * 0.3f) * Anim.outCubic(Anim.window(age, 0.25f, 1.2f));
        Shapes.box(w / 2f - tw / 2f - 14 - line, cy - 1.5f, line, 1).fillH(Gfx.alpha(accent, 0), accent).glow(3, Gfx.alpha(accent, 0.5f)).draw(g);
        Shapes.box(w / 2f + tw / 2f + 14, cy - 1.5f, line, 1).fillH(accent, Gfx.alpha(accent, 0)).glow(3, Gfx.alpha(accent, 0.5f)).draw(g);
        Shapes.icon(icon, w / 2f - tw / 2f - 8, cy - 1, 8).fill(accent).glow(3, Gfx.alpha(accent, 0.6f)).draw(g);
        Shapes.icon(icon, w / 2f + tw / 2f + 8, cy - 1, 8).fill(accent).glow(3, Gfx.alpha(accent, 0.6f)).draw(g);
        SysFont.Line sub = SysFont.line(subtitle, 8.5f, false);
        int chars = (int) Math.min(SysFont.length(sub), Math.max(0, age - 0.7f) * 45f);
        SysFont.draw(g, SysFont.truncate(sub, chars), w / 2f, cy + 15, SystemTheme.TEXT, 0.5f);
        Gfx.setAlpha(old);
    }

    /** The floor is won: a stamp, then a line saying where to go. */
    private static void cleared(GuiGraphics g, int w, int h, float age, TowerPayload r) {
        if (age < 3.2f) stamp(g, w, h * 0.3f, age, Component.translatable("tower.beyondsolo.hud.cleared").getString(), SystemTheme.GOLD, -4);
        float a = Anim.window(age, 1.0f, 1.5f);
        if (a <= 0) return;
        float old = Gfx.fade(a * (0.75f + 0.25f * Anim.pulse(2.4f)));
        String line = Component.translatable(r.has(TowerPayload.F_STAIR) ? "tower.beyondsolo.hud.cleared.sub" : "tower.beyondsolo.hud.cleared.exit").getString();
        float lw = SysFont.width(line, 6.5f, false) + 24;
        float y = h - 92;
        Shapes.box((w - lw) / 2f, y, lw, 15).radius(7.5f).fill(0xC0140406).border(0.6f, Gfx.alpha(SystemTheme.GOLD, 0.6f)).draw(g);
        Shapes.icon(Icons.PATH, (w - lw) / 2f + 9, y + 7.5f, 7).fill(SystemTheme.GOLD).draw(g);
        SysFont.draw(g, line, w / 2f + 5, y + 4, 6.5f, PALE, false, 0.5f);
        Gfx.setAlpha(old);
    }

    /** A word stamped down at a slant, with a ring running out as it lands. */
    private static void stamp(GuiGraphics g, int w, float cy, float age, String text, int color, float tilt) {
        float st = Anim.window(age, 0.05f, 0.45f);
        if (st <= 0) return;
        float s = 2.2f - 1.2f * Anim.outBack(st);
        float a = Mth.clamp(st * 2, 0, 1) * (1 - Anim.window(age, 2.6f, 3.2f));
        float old = Gfx.fade(a);
        float land = Anim.window(age, 0.4f, 1.2f);
        if (land > 0 && land < 1) {
            Shapes.ring(w / 2f, cy, 24 + 90 * Anim.outCubic(land), 2f * (1 - land) + 0.4f).fill(Gfx.alpha(color, 0.7f * (1 - land))).draw(g);
        }
        g.pose().pushPose();
        g.pose().translate(w / 2f, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(tilt));
        g.pose().scale(s, s, 1);
        float tw = SysFont.spacedWidth(text, 15, true, 2.4f);
        Shapes.box(-tw / 2f - 14, -14, tw + 28, 28).radius(5).fill(0xB0140A04).border(1.4f, color).glow(9, Gfx.alpha(color, 0.4f)).draw(g);
        Shapes.box(-tw / 2f - 10, -10.5f, tw + 20, 21).radius(3).fill(0).border(0.5f, Gfx.alpha(color, 0.65f)).draw(g);
        SysFont.setContentScale(s);
        SysFont.spaced(g, text, 0, -5.5f, 15, Gfx.mix(color, 0xFFFFFFFF, 0.6f), true, 2.4f, 0.5f);
        SysFont.setContentScale(1);
        g.pose().popPose();
        Gfx.setAlpha(old);
    }

    /** Up the stair: dark, the next floor's number rising out of it, light again. */
    private static void climbing(GuiGraphics g, int w, int h, float age, TowerPayload r) {
        float total = Math.max(0.5f, r.length() / 20f);
        float dark = Anim.window(age, 0f, total * 0.4f) * (1 - Anim.window(age, total * 0.62f, total));
        if (dark > 0) g.fill(0, 0, w, h, Gfx.alpha(0xFF000000, Anim.inOutCubic(dark)));
        float a = Anim.window(age, total * 0.25f, total * 0.45f) * (1 - Anim.window(age, total * 0.75f, total));
        if (a <= 0.01f) return;
        float old = Gfx.fade(a);
        float rise = Anim.outCubic(Anim.window(age, total * 0.25f, total * 0.6f));
        int next = r.floor() + 1;
        float cy = h / 2f + 10 * (1 - rise);
        int accent = TowerFloors.endless(next) ? VOID : CRIMSON;
        SysFont.spaced(g, Component.translatable("tower.beyondsolo.hud.ascending").getString(), w / 2f, cy - 26, 6.5f, Gfx.mix(accent, 0xFFFFFFFF, 0.3f), true, 3f, 0.5f);
        SysFont.spaced(g, Component.translatable("tower.beyondsolo.hud.floor", next).getString(), w / 2f, cy - 14, 22, 0xFFFFFFFF, true, 2.4f, 0.5f);
        Shapes.box(w / 2f - 40 * rise, cy + 13, 80 * rise, 1).fillH(Gfx.alpha(accent, 0), accent).draw(g);
        Gfx.setAlpha(old);
    }

    /** The lord has fallen: light, the line, the crown. */
    private static void victory(GuiGraphics g, int w, int h, float age) {
        float flash = 1 - Anim.window(age, 0, 0.8f);
        if (flash > 0) g.fill(0, 0, w, h, Gfx.alpha(0xFFFFF0E6, 0.7f * flash));
        float in = Anim.outCubic(Anim.window(age, 0.4f, 1.2f));
        float out = 1 - Anim.window(age, 7.0f, 8.0f);
        float a = in * out;
        if (a <= 0.01f) return;
        float cy = h * 0.3f;
        float old = Gfx.fade(a);
        float spin = Anim.loop(30f);
        Shapes.rays(w / 2f, cy, 120, 18).fill(Gfx.alpha(SystemTheme.GOLD, 0.12f)).rotation(spin).draw(g);
        Shapes.circle(w / 2f, cy - 30, 13).fill(Gfx.alpha(DEEP, 0.7f)).border(1, SystemTheme.GOLD).glow(10, Gfx.alpha(SystemTheme.GOLD, 0.45f)).draw(g);
        Shapes.icon(Icons.CROWN, w / 2f, cy - 30, 15).fill(0xFFFFF1C8).draw(g);
        String t = Component.translatable("tower.beyondsolo.hud.victory").getString();
        SysFont.spaced(g, t, w / 2f, cy - 8, 20, 0xFFFFF1C8, true, 2.6f, 0.5f);
        float tw = SysFont.spacedWidth(t, 20, true, 2.6f);
        float line = (tw + 40) * Anim.outCubic(Anim.window(age, 1.0f, 1.8f));
        Shapes.box(w / 2f - line / 2f, cy + 15, line, 1).fillH(Gfx.alpha(CRIMSON, 0), CRIMSON).glow(3, Gfx.alpha(CRIMSON, 0.6f)).draw(g);
        SysFont.Line sub = SysFont.line(Component.translatable("tower.beyondsolo.hud.victory.sub").getString(), 8.5f, false);
        int chars = (int) Math.min(SysFont.length(sub), Math.max(0, age - 1.5f) * 40f);
        SysFont.draw(g, SysFont.truncate(sub, chars), w / 2f, cy + 21, PALE, 0.5f);
        Gfx.setAlpha(old);
    }

    /** Between bouts: a ring counting down, the bout to come, a little health back. */
    private static void breather(GuiGraphics g, int w, int h, float age, float shownFor, TowerPayload r, float partial) {
        float total = Math.max(1, r.length() / 20f);
        float left = Math.max(0, total - age);
        float in = Anim.outBack(Anim.window(shownFor, 0.1f, 0.6f));
        if (in <= 0.01f) return;
        float old = Gfx.fade(Mth.clamp(in, 0, 1));
        float cx = w / 2f, cy = h * 0.3f;
        int sec = (int) Math.ceil(left);
        Shapes.circle(cx, cy, 20).fill(0xC0140406).glow(10, Gfx.alpha(EMBER, 0.25f)).draw(g);
        Shapes.ring(cx, cy, 18, 2f).fill(Gfx.alpha(EMBER, 0.18f)).draw(g);
        Shapes.ring(cx, cy, 18, 2f).fill(EMBER).progress(left / total).glow(4, Gfx.alpha(EMBER, 0.7f)).draw(g);
        SysFont.draw(g, String.valueOf(sec), cx, cy - 7, 14, 0xFFFFFFFF, true, 0.5f);
        SysFont.spaced(g, Component.translatable("tower.beyondsolo.hud.breather").getString(), cx, cy + 25, 8, PALE, true, 2.4f, 0.5f);
        SysFont.draw(g, Component.translatable("tower.beyondsolo.hud.breather.sub", Math.min(r.bout() + 1, r.bouts()), r.bouts()).getString(),
                cx, cy + 37, 6.5f, SystemTheme.TEXT_SOFT, false, 0.5f);
        Gfx.setAlpha(old);
    }

    /** The last boss of a rush has fallen: the clock stamped down, the best time under it. */
    private static void rushDone(GuiGraphics g, int w, int h, float age, TowerPayload r, float partial) {
        if (age > 6) return;
        stamp(g, w, h * 0.27f, age, Component.translatable("tower.beyondsolo.hud.rush_done").getString(), SystemTheme.GOLD, -5);
        float a = Anim.window(age, 0.9f, 1.4f) * (1 - Anim.window(age, 5.2f, 6f));
        if (a <= 0) return;
        float old = Gfx.fade(a);
        String time = TowerRun.time(r.clock());
        SysFont.draw(g, time, w / 2f, h * 0.27f + 24, 16, 0xFFFFE6A8, true, 0.5f);
        String line = r.has(TowerPayload.F_RECORD) ? Component.translatable("tower.beyondsolo.hud.record").getString()
                : r.best() >= 0 ? Component.translatable("tower.beyondsolo.hud.best_time", TowerRun.time(r.best())).getString() : "";
        if (!line.isEmpty()) {
            SysFont.spaced(g, line, w / 2f, h * 0.27f + 44, 7, r.has(TowerPayload.F_RECORD) ? SystemTheme.GOLD : SystemTheme.TEXT_SOFT, true, 2f, 0.5f);
        }
        Gfx.setAlpha(old);
    }

    /** A blow that would have killed them: red, "FALLEN", where the climb picks up again, black. */
    private static void fallen(GuiGraphics g, int w, int h, float age, TowerPayload r) {
        float flash = 1 - Anim.window(age, 0, 0.5f);
        Shapes.vignette(0, 0, w, h, 0.3f, 1.1f).fill(Gfx.alpha(DEEP, 0.65f + 0.3f * flash)).draw(g);
        if (flash > 0) g.fill(0, 0, w, h, Gfx.alpha(CRIMSON, 0.3f * flash));
        float cy = h * 0.36f;
        float in = Anim.outCubic(Anim.window(age, 0.1f, 0.5f));
        float old = Gfx.fade(in);
        String t = Component.translatable("tower.beyondsolo.hud.fallen").getString();
        SysFont.spaced(g, t, w / 2f, cy - 10, 22, 0xFFFFFFFF, true, 3f, 0.5f);
        float tw = SysFont.spacedWidth(t, 22, true, 3f);
        float strike = Anim.outCubic(Anim.window(age, 0.5f, 0.9f));
        if (strike > 0) Shapes.box(w / 2f - tw / 2f - 6, cy - 1, (tw + 12) * strike, 2).fill(CRIMSON).glow(4, Gfx.alpha(CRIMSON, 0.7f)).draw(g);
        String key = r.mode() == TowerPayload.RUSH ? "tower.beyondsolo.hud.fallen.rush"
                : r.mode() == TowerPayload.ENDLESS ? "tower.beyondsolo.hud.fallen.spire" : "tower.beyondsolo.hud.fallen.castle";
        int resume = r.mode() == TowerPayload.ENDLESS ? Math.max(r.best(), 0) : TowerClient.DATA.startFloor();
        SysFont.Line sub = SysFont.line(Component.translatable(key, resume).getString(), 8f, false);
        int chars = (int) Math.min(SysFont.length(sub), Math.max(0, age - 0.8f) * 45f);
        SysFont.draw(g, SysFont.truncate(sub, chars), w / 2f, cy + 14, SystemTheme.TEXT, 0.5f);
        Gfx.setAlpha(old);
        float black = Anim.window(age, 2.6f, 4f);
        if (black > 0) g.fill(0, 0, w, h, Gfx.alpha(0xFF000000, black));
    }

    private static void leaving(GuiGraphics g, int w, int h, float age) {
        float total = 2f;
        float k = Mth.clamp(age / total, 0, 1);
        Shapes.vignette(0, 0, w, h, 0.6f - 0.4f * k, 1.25f).fill(Gfx.alpha(DEEP, 0.2f + 0.5f * k)).draw(g);
        float a = Anim.outCubic(Anim.window(age, 0, 0.4f)) * (1 - Anim.window(k, 0.8f, 0.95f));
        if (a > 0.01f) {
            float old = Gfx.fade(a);
            SysFont.spaced(g, Component.translatable("tower.beyondsolo.hud.leaving").getString(), w / 2f, h * 0.36f, 12, PALE, true, 3f, 0.5f);
            Gfx.setAlpha(old);
        }
        float black = Anim.window(k, 0.75f, 1f);
        if (black > 0) g.fill(0, 0, w, h, Gfx.alpha(0xFF000000, black));
    }

    /** Home again: the black the run ended on fades away. */
    private static void afterglow(GuiGraphics g, int w, int h) {
        if (TowerClient.endedFrom != TowerPayload.LEAVING && TowerClient.endedFrom != TowerPayload.FALLEN && TowerClient.endedFrom != TowerPayload.PULL) return;
        float age = Anim.since(TowerClient.endedAt);
        if (age > 1.6f) return;
        float a = 1 - Anim.window(age, 0.3f, 1.6f);
        if (a > 0) g.fill(0, 0, w, h, Gfx.alpha(0xFF000000, a));
    }

    /** The blood moon: the edges of the screen stained, beating slowly. */
    private static void moonEdges(GuiGraphics g, int w, int h) {
        float beat = 0.5f + 0.5f * Mth.sin((float) Anim.now() * 2.2f);
        Shapes.vignette(0, 0, w, h, 0.55f, 1.3f).fill(Gfx.alpha(DEEP, 0.28f + 0.12f * beat)).draw(g);
    }

    // ================================================================ banners and the exit

    private static void banner(GuiGraphics g, int w, float y) {
        float age = Anim.since(TowerClient.eventAt);
        int ev = TowerClient.event;
        float life = switch (ev) {
            case TowerPayload.EV_WAVE, TowerPayload.EV_SIEGE_END, TowerPayload.EV_CHAMPION -> 2.2f;
            case TowerPayload.EV_CACHE -> 2.6f;
            case TowerPayload.EV_CHECKPOINT, TowerPayload.EV_RECORD, TowerPayload.EV_MOON -> 3.6f;
            case TowerPayload.EV_SIGIL -> 5f;
            default -> 0;
        };
        if (life == 0 || age > life) return;
        TowerPayload r = TowerClient.run;
        String title, sub;
        int color;
        switch (ev) {
            case TowerPayload.EV_WAVE -> {
                title = r.mode() == TowerPayload.RUSH ? Component.translatable("tower.beyondsolo.ev.bout", r.bout(), Math.max(1, r.bouts())).getString()
                        : Component.translatable("tower.beyondsolo.ev.wave", r.wave(), r.waves()).getString();
                sub = "";
                color = CRIMSON;
            }
            case TowerPayload.EV_SIEGE_END -> {
                title = Component.translatable("tower.beyondsolo.ev.siege_end").getString();
                sub = "";
                color = EMBER;
            }
            case TowerPayload.EV_CHAMPION -> {
                title = Component.translatable("tower.beyondsolo.ev.champion").getString();
                sub = "";
                color = EMBER;
            }
            case TowerPayload.EV_CACHE -> {
                title = Component.translatable("tower.beyondsolo.ev.cache").getString();
                sub = Component.translatable("tower.beyondsolo.ev.cache.sub").getString();
                color = SystemTheme.GOLD;
            }
            case TowerPayload.EV_CHECKPOINT -> {
                title = Component.translatable("tower.beyondsolo.ev.checkpoint").getString();
                sub = Component.translatable("tower.beyondsolo.ev.checkpoint.sub", TowerClient.DATA.startFloor()).getString();
                color = SystemTheme.GOLD;
            }
            case TowerPayload.EV_RECORD -> {
                title = Component.translatable("tower.beyondsolo.ev.record").getString();
                sub = r.mode() == TowerPayload.RUSH ? Component.translatable("tower.beyondsolo.ev.record.rush").getString()
                        : Component.translatable("tower.beyondsolo.ev.record.spire", r.floor()).getString();
                color = SystemTheme.GOLD;
            }
            case TowerPayload.EV_MOON -> {
                title = Component.translatable("tower.beyondsolo.ev.moon").getString();
                sub = Component.translatable("tower.beyondsolo.ev.moon.sub").getString();
                color = CRIMSON;
            }
            default -> {
                title = Component.translatable("tower.beyondsolo.ev.sigil").getString();
                sub = Component.translatable("tower.beyondsolo.ev.sigil.sub").getString();
                color = CRIMSON;
            }
        }
        float in = Anim.outBack(Anim.window(age, 0, 0.35f));
        float out = 1 - Anim.window(age, life - 0.4f, life);
        float a = Mth.clamp(in, 0, 1) * out;
        if (a <= 0.01f) return;
        float old = Gfx.fade(a);
        boolean big = !sub.isEmpty();
        float size = big ? 9.5f : 7.5f;
        float tw = SysFont.spacedWidth(title, size, true, 2f);
        float bw = Math.max(tw, big ? SysFont.width(sub, 6.5f, false) : 0) + 34;
        float bh = big ? 26 : 15;
        float sx = 0.7f + 0.3f * in;
        g.pose().pushPose();
        g.pose().translate(w / 2f, y + bh / 2f, 0);
        g.pose().scale(sx, 1, 1);
        g.pose().translate(-w / 2f, -(y + bh / 2f), 0);
        Shapes.box((w - bw) / 2f, y, bw, bh).radius(bh / 2f).fill(0xD0140406).border(0.8f, Gfx.alpha(color, 0.9f)).glow(8, Gfx.alpha(color, 0.35f)).draw(g);
        if (ev == TowerPayload.EV_SIGIL) {
            Shapes.rays(w / 2f, y + bh / 2f, bw * 0.7f, 14).fill(Gfx.alpha(CRIMSON, 0.14f * a)).rotation(Anim.loop(20f)).draw(g);
        }
        g.pose().popPose();
        SysFont.spaced(g, title, w / 2f, y + (big ? 4 : 3.5f), size, Gfx.mix(color, 0xFFFFFFFF, 0.4f), true, 2f, 0.5f);
        if (big) SysFont.draw(g, sub, w / 2f, y + 15.5f, 6.5f, SystemTheme.TEXT_SOFT, false, 0.5f);
        Gfx.setAlpha(old);
    }

    /** Standing on the exit sigil: a ring round the crosshair fills, and they are let out when it closes. */
    private static void exitHold(GuiGraphics g, int w, int h, TowerPayload r) {
        float target = r.hold() > 0 ? Mth.clamp(r.hold() / (float) TowerRun.EXIT_HOLD, 0, 1) : 0;
        holdShown = target == 0 ? Anim.approach(holdShown, 0, 6f) : Anim.approach(holdShown, target, 3f);
        if (holdShown <= 0.01f) return;
        float cx = w / 2f, cy = h / 2f;
        float old = Gfx.fade(Math.min(1, holdShown * 4));
        Shapes.ring(cx, cy, 12, 1.6f).fill(Gfx.alpha(SystemTheme.GOLD, 0.18f)).draw(g);
        Shapes.ring(cx, cy, 12, 1.6f).fill(SystemTheme.GOLD).progress(holdShown).glow(3, Gfx.alpha(SystemTheme.GOLD, 0.6f)).draw(g);
        SysFont.spaced(g, Component.translatable("tower.beyondsolo.hud.exit_hold").getString(), cx, cy + 17, 6, PALE, true, 1.8f, 0.5f);
        Gfx.setAlpha(old);
    }

    static void reset() {
        plaqueIn = 0;
        holdShown = 0;
    }

    /** The rank a floor's creatures are (the plaque's emblem in the CITADEL page uses the same). */
    static PlayerRank rankOf(int floor) {
        return TowerFloors.rank(Math.max(1, floor));
    }

    private TowerHud() {}
}
