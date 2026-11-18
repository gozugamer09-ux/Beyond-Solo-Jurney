package com.beyondsolo.client.worldboss;

import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.breaks.BreakHud;
import com.beyondsolo.client.gate.GateOverlay;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.hud.HudSpace;
import com.beyondsolo.client.key.RunHud;
import com.beyondsolo.worldboss.ColossalBoss;
import com.beyondsolo.worldboss.WorldBossKind;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * The great threats on screen ({@link WorldBossClient}):
 * <ul>
 *   <li><b>The bar</b> near the boss: its tier, name and title, three phase marks, health with notches where the next
 *   phases begin, this hunter's share of the damage and place, the phase's name.</li>
 *   <li><b>A marker</b> when it is far: its name, how far, an arrow that turns to it, and a monarch's countdown to
 *   landing.</li>
 *   <li><b>A monarch's domain</b>: the edges of the screen in its colour while you stand inside it.</li>
 *   <li><b>Banners</b>: it has risen, a gate has opened in the sky (with the countdown), each phase, the domain, the
 *   authority (with the time left to get clear), the kill (share, place, XP), and its leaving.</li>
 * </ul>
 */
public final class WorldBossHud {
    private static final String[] ROMAN = {"I", "II", "III"};
    private static final int WHITE = 0xFFFFFFFF;

    private static float barIn;
    private static float markIn;
    private static float domainIn;
    private static float shownHealth = 1f;
    private static float trailHealth = 1f;
    private static long barEvent;
    private static WorldBossClient.Tracked lastTracked;
    private static WorldBossClient.Banner kicked;

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        WorldBossClient.Banner b = WorldBossClient.banner(!WorldBossClient.othersShowing());
        if (mc.options.hideGui) return;
        int w = g.guiWidth(), h = g.guiHeight();
        float partial = delta.getGameTimeDeltaPartialTick(false);
        WorldBossClient.Tracked t = WorldBossClient.tracked();
        if (t != null) lastTracked = t;
        domain(g, mc, w, h, t);
        if (b != null) banner(g, mc, w, h, b);
        float y = 6 + 34 * Anim.outCubic(GateOverlay.bossBarIn()) + 26 * RunHud.clockShown() + BreakHud.shownHeight();
        bar(g, mc, w, y, t);
        if (ClientSystemData.get().hasSystem()) marker(g, mc, w, y + 38 * Anim.outCubic(barIn), t, partial);
    }

    /** The tier's colour with full opacity. */
    private static int tier(WorldBossKind k) {
        return 0xFF000000 | k.tier.color;
    }

    private static int color(WorldBossKind k) {
        return 0xFF000000 | k.color;
    }

    private static String upper(Component c) {
        return c.getString().toUpperCase(Locale.ROOT);
    }

    // ================================================================ the bar

    /** Whether the boss is near enough, and far enough into its fight, for its bar. */
    private static boolean barShown(Minecraft mc, WorldBossClient.Tracked t) {
        if (t == null) return false;
        ColossalBoss boss = t.boss();
        if (boss == null) return t.slain && t.endedAt >= 0 && Anim.since(t.endedAt) < 2.5f && barIn > 0.5f;
        if (boss.distanceTo(mc.player) > 96 || !boss.isAwake()) return false;
        if (boss.isDeadOrDying()) return boss.deathTime < 50;
        return boss.introTicks() <= 0 && !boss.leaving();
    }

    private static void bar(GuiGraphics g, Minecraft mc, int w, float y, WorldBossClient.Tracked live) {
        boolean show = barShown(mc, live);
        barIn = Anim.approach(barIn, show ? 1f : 0f, show ? 3.5f : 2.5f);
        WorldBossClient.Tracked t = live != null ? live : lastTracked;
        if (barIn <= 0.01f || t == null) return;
        if (barEvent != t.event) {
            barEvent = t.event;
            shownHealth = trailHealth = t.healthNow();
        }
        WorldBossKind k = t.kind;
        ColossalBoss boss = t.boss();
        float hp = Mth.clamp(t.healthNow(), 0, 1);
        shownHealth = Anim.approach(shownHealth, hp, 2.5f);
        if (hp > trailHealth) trailHealth = hp;
        trailHealth = Anim.approach(trailHealth, shownHealth, 0.4f);
        int phase = t.phaseNow();
        boolean shifting = boss != null && boss.shifting();
        int tc = tier(k), kc = color(k);

        float in = Anim.outCubic(barIn);
        float bw = Math.min(300, w * 0.6f), x = (w - bw) / 2f;
        y -= 22 * (1 - in);
        float old = Gfx.fade(in);
        // our bars at the top centre: an overlay there (Jade's tooltip) goes under them
        HudSpace.claimTop(x - 6, y - 3, bw + 12, 36);
        float surge = shifting ? 0.5f + 0.5f * Mth.sin((float) Anim.now() * 9f) : 0f;
        Shapes.box(x - 6, y - 3, bw + 12, 36).radius(9).fillV(0xD00A0C18, 0xD0050610)
                .border(0.9f, Gfx.alpha(tc, 0.75f + 0.25f * surge), Gfx.alpha(tc, 0.25f))
                .glow(9 + 5 * surge, Gfx.alpha(k.monarch() ? kc : tc, 0.22f + 0.3f * surge)).draw(g);
        // the tier tag, then its name and title
        String tag = upper(k.tier.label());
        float tagW = SysFont.spacedWidth(tag, 5f, true, 1.6f) + 10;
        Shapes.pill(x, y + 1.5f, tagW, 9).fill(Gfx.alpha(tc, 0.28f)).border(0.7f, Gfx.alpha(tc, 0.85f)).glow(4, Gfx.alpha(tc, 0.35f)).draw(g);
        SysFont.spaced(g, tag, x + tagW / 2f, SysFont.centerY(y + 1.5f, 9, 5f), 5f, Gfx.mix(tc, WHITE, 0.55f), true, 1.6f, 0.5f);
        float nx = x + tagW + 5;
        // its name in the colour of how it stands against this hunter (white, orange, red), like every System creature
        int nameColor = boss != null ? com.beyondsolo.client.enemy.EnemyHud.nameColor(boss, WHITE) : WHITE;
        SysFont.draw(g, k.bossName(), nx, y + 2, 8.5f, nameColor, true, 0f);
        float nameW = SysFont.width(k.bossName(), 8.5f, true);
        String title = SysFont.fit(k.title().getString(), 6f, false, Math.max(10, bw - tagW - nameW - 48));
        SysFont.draw(g, title, nx + nameW + 4, y + 4, 6f, Gfx.mix(kc, SystemTheme.TEXT_SOFT, 0.55f), false, 0f);
        // the phases: a mark for each, lit as it comes
        for (int i = 0; i < 3; i++) {
            float px = x + bw - 4 - (2 - i) * 9, py = y + 6;
            boolean lit = i < phase;
            g.pose().pushPose();
            g.pose().translate(px, py, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(45));
            float s = i == phase - 1 ? 3.6f + 0.6f * surge : 3f;
            Shapes.box(-s, -s, s * 2, s * 2).radius(0.8f).fill(lit ? Gfx.mix(kc, WHITE, 0.25f) : 0x40000000)
                    .border(0.7f, Gfx.alpha(lit ? kc : SystemTheme.TEXT_DIM, 0.9f)).glow(lit ? 3 : 0, Gfx.alpha(kc, 0.6f)).draw(g);
            g.pose().popPose();
        }
        // the health, with notches where the next phases begin
        float by = y + 14, bh = 6;
        Shapes.pill(x, by, bw, bh).fill(SystemTheme.TRACK).border(0.5f, SystemTheme.TRACK_BORDER).draw(g);
        if (trailHealth > shownHealth + 0.002f) Shapes.pill(x, by, Math.max(bh, bw * trailHealth), bh).fill(0xD0FFE6C8).draw(g);
        if (shownHealth > 0.003f) {
            int a = Gfx.shade(kc, -0.35f), b2 = Gfx.mix(kc, WHITE, 0.25f);
            Shapes.pill(x, by, Math.max(bh, bw * shownHealth), bh).fillH(a, b2).glow(4 + 3 * surge, Gfx.alpha(kc, 0.55f))
                    .sheen(Anim.loop(2.8f) * 1.6f - 0.3f, 0.22f).draw(g);
        }
        for (float line : new float[]{k.phase2, k.phase3}) {
            boolean passed = hp <= line + 0.001f;
            Shapes.box(x + bw * line - 0.6f, by - 1.5f, 1.2f, bh + 3).fill(passed ? 0x50FFFFFF : 0xE0FFFFFF).glow(passed ? 0 : 2, Gfx.alpha(tc, 0.6f)).draw(g);
        }
        // this hunter's share, and the phase's name
        float ly = by + bh + 3.5f;
        if (t.share >= 0) {
            String share = Component.translatable("worldboss.beyondsolo.hud.share", String.format(Locale.ROOT, "%.1f%%", t.share * 100)).getString();
            SysFont.draw(g, share, x, ly, 5.6f, SystemTheme.TEXT_SOFT, false, 0f);
            if (t.place > 0) {
                float sw = SysFont.width(share, 5.6f, false);
                String place = Component.translatable("worldboss.beyondsolo.hud.place", t.place).getString();
                SysFont.draw(g, place, x + sw + 4, ly, 5.6f, t.place == 1 ? SystemTheme.GOLD : SystemTheme.TEXT, true, 0f);
            }
        }
        String phaseText = ROMAN[Mth.clamp(phase, 1, 3) - 1] + "  " + k.phaseName(phase).getString();
        SysFont.draw(g, phaseText, x + bw, ly, 5.6f, Gfx.mix(kc, WHITE, 0.35f), true, 1f);
        Gfx.setAlpha(old);
    }

    // ================================================================ far off

    private static void marker(GuiGraphics g, Minecraft mc, int w, float y, WorldBossClient.Tracked t, float partial) {
        boolean show = t != null && !t.slain && !t.leaving && !barShown(mc, t);
        markIn = Anim.approach(markIn, show ? 1 : 0, show ? 4f : 3f);
        if (markIn < 0.01f || t == null) return;
        WorldBossKind k = t.kind;
        float in = Anim.outCubic(markIn);
        ColossalBoss boss = t.boss();
        Vec3 at = boss != null ? boss.getPosition(partial) : Vec3.atBottomCenterOf(t.pos);
        Vec3 me = mc.player.getPosition(partial);
        double dx = at.x - me.x, dz = at.z - me.z;
        int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        float bearing = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float rel = Mth.wrapDegrees(bearing - mc.player.getViewYRot(partial));
        int kc = color(k), tc = tier(k);

        String name = k.bossName().getString();
        String far = Component.translatable("worldboss.beyondsolo.hud.meters", dist).getString();
        String lands = t.descending() ? Component.translatable("worldboss.beyondsolo.hud.lands", clock(t.landIn())).getString() : null;
        float size = 6.2f, hgt = 15;
        float nw = SysFont.width(name, size, true), fw = SysFont.width(far, size, false);
        float lw = lands == null ? 0 : SysFont.width(lands, size, true) + 10;
        float mw = 12 + nw + 8 + fw + 8 + lw + 16;
        float x = (w - mw) / 2f;
        y -= 6 * (1 - in);
        float old = Gfx.fade(in);
        HudSpace.claimTop(x, y, mw, hgt);
        float pulse = 0.6f + 0.4f * Anim.pulse(t.descending() ? 1.0f : 2.2f);
        Shapes.pill(x, y, mw, hgt).fillV(0xC00A0C18, 0xC0050610).border(0.8f, Gfx.alpha(tc, 0.8f), Gfx.alpha(tc, 0.3f))
                .glow(5, Gfx.alpha(kc, 0.25f * pulse)).draw(g);
        Shapes.icon(k.monarch() ? Icons.CROWN : Icons.SKULL, x + 8, y + hgt / 2f, 8).fill(Gfx.mix(kc, WHITE, 0.3f)).glow(2, Gfx.alpha(kc, 0.6f)).draw(g);
        float cx = x + 14;
        SysFont.draw(g, name, cx, SysFont.centerY(y, hgt, size), size, WHITE, true, 0f);
        cx += nw + 8;
        SysFont.draw(g, far, cx, SysFont.centerY(y, hgt, size), size, SystemTheme.TEXT_SOFT, false, 0f);
        cx += fw + 8;
        if (lands != null) {
            Shapes.box(cx - 4, y + 3, 0.7f, hgt - 6).fill(Gfx.alpha(tc, 0.5f)).draw(g);
            SysFont.draw(g, lands, cx + 2, SysFont.centerY(y, hgt, size), size, Gfx.mix(kc, WHITE, 0.4f), true, 0f);
            cx += lw;
        }
        arrow(g, x + mw - 9, y + hgt / 2f, rel, Gfx.mix(kc, WHITE, 0.35f));
        Gfx.setAlpha(old);
    }

    /** An arrow turned {@code degrees} from straight up (where the player looks). */
    private static void arrow(GuiGraphics g, float cx, float cy, float degrees, int color) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(degrees));
        for (int side = -1; side <= 1; side += 2) {
            g.pose().pushPose();
            g.pose().translate(0, -3.2f, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(side * 40));
            Shapes.box(-0.7f, 0, 1.4f, 4.6f).radius(0.7f).fill(color).glow(2, Gfx.alpha(color, 0.5f)).draw(g);
            g.pose().popPose();
        }
        Shapes.box(-0.6f, -2.6f, 1.2f, 6f).radius(0.6f).fill(color).draw(g);
        g.pose().popPose();
    }

    private static String clock(int ticks) {
        int s = Math.max(0, (ticks + 19) / 20);
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    // ================================================================ the domain

    private static void domain(GuiGraphics g, Minecraft mc, int w, int h, WorldBossClient.Tracked t) {
        boolean inside = false;
        int kc = 0xFFFFFFFF;
        if (t != null && t.kind.monarch() && t.domain > 0 && !t.leaving && !t.slain) {
            ColossalBoss boss = t.boss();
            if (boss != null && boss.isAlive() && boss.introTicks() <= 31) {
                inside = boss.position().subtract(mc.player.position()).horizontalDistance() <= t.domain;
            }
            kc = color(t.kind);
        }
        domainIn = Anim.approach(domainIn, inside ? 1 : 0, inside ? 1.5f : 2.5f);
        if (domainIn < 0.01f || t == null) return;
        float beat = 0.75f + 0.25f * Anim.pulse(3.2f);
        Shapes.vignette(0, 0, w, h, 0.35f, 1.35f).fill(Gfx.alpha(color(t.kind), 0.2f * domainIn * beat)).draw(g);
    }

    // ================================================================ banners

    private static void banner(GuiGraphics g, Minecraft mc, int w, int h, WorldBossClient.Banner b) {
        float t = b.age();
        float live = 1 - Anim.outCubic(Anim.window(t, b.duration - 0.5f, b.duration));
        if (kicked != b) {
            kicked = b;
            double d = mc.player.position().distanceTo(Vec3.atCenterOf(b.pos));
            float near = (float) Mth.clamp(1 - d / 140, 0, 1);
            switch (b.kind) {
                case PHASE -> GateOverlay.addTrauma(0.25f * near);
                case AUTHORITY -> GateOverlay.addTrauma(0.35f * near);
                case APPEAR -> GateOverlay.addTrauma(0.3f * near);
                default -> {
                }
            }
        }
        switch (b.kind) {
            case APPEAR -> appear(g, mc, w, h, b, t, live);
            case DESCENT -> descent(g, mc, w, h, b, t, live);
            case PHASE -> phase(g, w, h, b, t, live);
            case DOMAIN -> domainBanner(g, w, h, b, t, live);
            case AUTHORITY -> authority(g, w, h, b, t, live);
            case SLAIN -> slain(g, w, h, b, t, live);
            case WITHDRAW -> withdraw(g, w, h, b, t, live);
        }
    }

    /** "112 blocks to the north-east", from where the player stands. */
    private static Component where(Minecraft mc, BlockPos at) {
        double dx = at.getX() + 0.5 - mc.player.getX(), dz = at.getZ() + 0.5 - mc.player.getZ();
        int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        int octant = Math.floorMod((int) Math.round(yaw / 45.0), 8);
        String[] names = {"south", "south_west", "west", "north_west", "north", "north_east", "east", "south_east"};
        return Component.translatable("worldboss.beyondsolo.where", dist, Component.translatable("worldboss.beyondsolo.dir." + names[octant]));
    }

    /** Black bars closing in at the top and bottom, edged in a colour. */
    private static float letterbox(GuiGraphics g, int w, int h, float t, float live, int color, float depth) {
        float in = Anim.outCubic(Anim.window(t, 0f, 0.5f)) * live;
        float bh = h * depth * in;
        Shapes.box(0, 0, w, bh).fill(0xEE000000).draw(g);
        Shapes.box(0, h - bh, w, bh).fill(0xEE000000).draw(g);
        Shapes.box(0, bh - 1, w * in, 1).fill(Gfx.alpha(color, 0.85f)).glow(4, Gfx.alpha(color, 0.5f)).draw(g);
        Shapes.box(w * (1 - in), h - bh, w * in, 1).fill(Gfx.alpha(color, 0.85f)).glow(4, Gfx.alpha(color, 0.5f)).draw(g);
        return bh;
    }

    /** A tag in a pill that pops in. */
    private static void tag(GuiGraphics g, String text, float cx, float y, float k, int color, float size) {
        if (k <= 0.01f) return;
        float tw = SysFont.spacedWidth(text, size, true, 2.2f) + 16;
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(k, k, 1);
        float o = Gfx.fade(Math.min(1f, k));
        Shapes.pill(-tw / 2f, -size * 0.9f, tw, size * 1.8f).fill(Gfx.alpha(Gfx.shade(color, -0.45f), 0.6f)).border(0.8f, color).glow(8, Gfx.alpha(color, 0.45f)).draw(g);
        SysFont.spaced(g, text, 0, -size * 0.45f, size, Gfx.mix(color, WHITE, 0.6f), true, 2.2f, 0.5f);
        Gfx.setAlpha(o);
        g.pose().popPose();
    }

    /** Text that types itself in, with a bright caret while it types. */
    private static void typed(GuiGraphics g, Component text, float cx, float y, float size, int color, float k, int caret) {
        if (k <= 0) return;
        SysFont.Line line = SysFont.line(text, size, true);
        int n = SysFont.length(line);
        int shown = Math.min(n, Math.round(n * k));
        float full = line.width();
        SysFont.draw(g, SysFont.truncate(line, shown), cx - full / 2f, y, color, 0f);
        if (k < 1f) {
            float cx2 = cx - full / 2f + SysFont.truncate(line, shown).width() + 1;
            Shapes.box(cx2, y, 1.4f, size * 0.8f).fill(Gfx.alpha(caret, 0.95f)).glow(4, caret).draw(g);
        }
    }

    /** It has risen (a world boss) or come down (a monarch): its name and title, and where it is. */
    private static void appear(GuiGraphics g, Minecraft mc, int w, int h, WorldBossClient.Banner b, float t, float live) {
        WorldBossKind k = b.boss;
        int tc = tier(k), kc = color(k);
        float bh = letterbox(g, w, h, t, live, kc, 0.1f);
        float pulse = 0.5f + 0.5f * Mth.sin(t * 6f);
        Shapes.vignette(0, 0, w, h, 0.2f, 1.3f).fill(Gfx.alpha(kc, (0.18f + 0.14f * pulse) * live * (1 - Anim.window(t, 1.5f, 4f)))).draw(g);
        float old = Gfx.fade(live);
        float cx = w / 2f, y = h - bh - 64;
        String head = upper(Component.translatable(k.monarch() ? "worldboss.beyondsolo.banner.descended" : "worldboss.beyondsolo.banner.risen"));
        tag(g, head, cx, y, Anim.outBack(Anim.window(t, 0.4f, 0.8f)), tc, 6.5f);
        float nameK = Anim.window(t, 0.8f, 1.8f);
        if (nameK > 0) {
            float nw = SysFont.width(k.bossName(), 22f, true);
            Shapes.box(cx - nw / 2f - 30, y + 12, nw + 60, 26).radius(13).fill(0).glow(20, Gfx.alpha(kc, 0.3f * Anim.outCubic(nameK))).draw(g);
        }
        typed(g, k.bossName(), cx, y + 14, 22f, WHITE, nameK, kc);
        float sub = Anim.outCubic(Anim.window(t, 1.6f, 2.2f));
        if (sub > 0.01f) {
            float o2 = Gfx.fade(sub);
            SysFont.draw(g, k.title(), cx, y + 40, 9f, Gfx.mix(kc, WHITE, 0.4f), true, 0.5f);
            SysFont.draw(g, where(mc, b.pos), cx, y + 53, 7f, SystemTheme.TEXT_SOFT, false, 0.5f);
            Gfx.setAlpha(o2);
        }
        Gfx.setAlpha(old);
    }

    /** A gate has torn open in the sky: the tear across the screen, the monarch's name, the time until it lands. */
    private static void descent(GuiGraphics g, Minecraft mc, int w, int h, WorldBossClient.Banner b, float t, float live) {
        WorldBossKind k = b.boss;
        int tc = tier(k), kc = color(k);
        float cx = w / 2f, y = h * 0.22f;
        Shapes.vignette(0, 0, w, h, 0.25f, 1.3f).fill(Gfx.alpha(tc, 0.22f * live * (1 - Anim.window(t, 2f, 5f)))).draw(g);
        float old = Gfx.fade(live);
        float bw = Math.min(w * 0.94f, 420);
        float tear = Anim.outCubic(Anim.window(t, 0.05f, 0.6f));
        Shapes.pill(cx - bw / 2f, y - 14, bw, 66).fill(Gfx.alpha(0xFF06030C, 0.62f * tear)).glow(20, Gfx.alpha(0xFF06030C, 0.4f * tear)).draw(g);
        // the rent in the sky: two edges of light parting
        float gap = 3 + 3 * Anim.outCubic(Anim.window(t, 0.3f, 1.2f));
        for (int side = -1; side <= 1; side += 2) {
            Shapes.box(cx - bw / 2f * tear, y + 6 + side * gap, bw * tear, 1).fill(Gfx.alpha(Gfx.mix(kc, WHITE, 0.5f), 0.9f))
                    .glow(5, Gfx.alpha(kc, 0.7f)).draw(g);
        }
        Shapes.box(cx - bw / 2f * tear, y + 6 - gap, bw * tear, gap * 2).fill(Gfx.alpha(kc, 0.18f)).draw(g);
        String head = upper(Component.translatable("worldboss.beyondsolo.banner.gate"));
        tag(g, head, cx, y - 20, Anim.outBack(Anim.window(t, 0.1f, 0.4f)), tc, 6f);
        float nameIn = Anim.outCubic(Anim.window(t, 0.6f, 1.2f));
        if (nameIn > 0.01f) {
            float o2 = Gfx.fade(nameIn);
            SysFont.draw(g, k.fullName(), cx, y + 15, 10f, WHITE, true, 0.5f);
            Gfx.setAlpha(o2);
        }
        float rest = Anim.outCubic(Anim.window(t, 1.0f, 1.5f));
        if (rest > 0.01f) {
            float o3 = Gfx.fade(rest);
            int left = b.left(b.a);
            String lands = Component.translatable("worldboss.beyondsolo.hud.lands", clock(left)).getString();
            SysFont.draw(g, lands, cx, y + 30, 8f, Gfx.mix(kc, WHITE, 0.45f), true, 0.5f);
            SysFont.draw(g, where(mc, b.pos), cx, y + 42, 6.5f, SystemTheme.TEXT_SOFT, false, 0.5f);
            Gfx.setAlpha(o3);
        }
        Gfx.setAlpha(old);
    }

    /** A phase begins: its number stamped in, its name under it. */
    private static void phase(GuiGraphics g, int w, int h, WorldBossClient.Banner b, float t, float live) {
        WorldBossKind k = b.boss;
        int kc = color(k);
        float flash = 1 - Anim.outCubic(Anim.window(t, 0f, 0.6f));
        Shapes.vignette(0, 0, w, h, 0.1f, 1.2f).fill(Gfx.alpha(kc, 0.4f * flash * live)).draw(g);
        float old = Gfx.fade(live);
        float cx = w / 2f, cy = h * 0.3f;
        float stamp = Anim.window(t, 0.05f, 0.35f);
        if (stamp > 0) {
            float s = Mth.lerp(Anim.outCubic(stamp), 2.4f, 1f);
            String word = upper(Component.translatable("worldboss.beyondsolo.hud.phase", ROMAN[Mth.clamp(b.phase, 1, 3) - 1]));
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            g.pose().scale(s, s, 1);
            float o2 = Gfx.fade(Math.min(1f, stamp * 1.6f));
            float ww = SysFont.spacedWidth(word, 15f, true, 3f);
            Shapes.box(-ww / 2f - 40, -2, 30, 1).fillH(Gfx.alpha(kc, 0f), kc).draw(g);
            Shapes.box(ww / 2f + 10, -2, 30, 1).fillH(kc, Gfx.alpha(kc, 0f)).draw(g);
            SysFont.spaced(g, word, 0, -7, 15f, Gfx.mix(kc, WHITE, 0.6f), true, 3f, 0.5f);
            Gfx.setAlpha(o2);
            g.pose().popPose();
        }
        float sub = Anim.outCubic(Anim.window(t, 0.4f, 0.9f));
        if (sub > 0.01f) {
            float o3 = Gfx.fade(sub);
            SysFont.draw(g, b.boss.phaseName(b.phase), cx, cy + 14, 9f, WHITE, true, 0.5f);
            Gfx.setAlpha(o3);
        }
        Gfx.setAlpha(old);
    }

    /** A monarch lays down its domain: its name, and what it does to whoever stands in it. */
    private static void domainBanner(GuiGraphics g, int w, int h, WorldBossClient.Banner b, float t, float live) {
        WorldBossKind k = b.boss;
        int kc = color(k);
        float old = Gfx.fade(live);
        float cx = w / 2f, y = h * 0.66f;
        float open = Anim.outCubic(Anim.window(t, 0f, 0.5f));
        float bw = Math.min(w * 0.8f, 300) * open;
        Shapes.pill(cx - bw / 2f, y - 8, bw, 40).fill(Gfx.alpha(0xFF05060E, 0.55f)).glow(14, Gfx.alpha(kc, 0.18f)).draw(g);
        Ui.divider(g, cx - bw / 2f, y - 8, bw, Gfx.alpha(kc, 0.8f));
        Ui.divider(g, cx - bw / 2f, y + 32, bw, Gfx.alpha(kc, 0.8f));
        tag(g, upper(Component.translatable("worldboss.beyondsolo.hud.domain")), cx, y - 8, Anim.outBack(Anim.window(t, 0.2f, 0.5f)), kc, 5.5f);
        float nameIn = Anim.outCubic(Anim.window(t, 0.35f, 0.9f));
        if (nameIn > 0.01f) {
            float o2 = Gfx.fade(nameIn);
            SysFont.spaced(g, upper(k.domainName()), cx, y + 3, 12f, Gfx.mix(kc, WHITE, 0.5f), true, 2.4f, 0.5f);
            SysFont.draw(g, Component.translatable("worldboss.beyondsolo." + k.id + ".domain.hint"), cx, y + 20, 6.5f, SystemTheme.TEXT_SOFT, false, 0.5f);
            Gfx.setAlpha(o2);
        }
        Gfx.setAlpha(old);
    }

    /** A monarch calls on its authority: its name, burning, and the seconds left to get clear. */
    private static void authority(GuiGraphics g, int w, int h, WorldBossClient.Banner b, float t, float live) {
        WorldBossKind k = b.boss;
        int kc = color(k);
        int left = b.left(b.a);
        float throb = Math.max(0, Mth.sin(t * (6f + 6f * (1 - Mth.clamp(left / 100f, 0, 1)))));
        Shapes.vignette(0, 0, w, h, 0.05f, 1.15f).fill(Gfx.alpha(0xFF000000, 0.55f * live)).draw(g);
        Shapes.vignette(0, 0, w, h, 0.25f, 1.3f).fill(Gfx.alpha(kc, (0.2f + 0.25f * throb) * live)).draw(g);
        float old = Gfx.fade(live);
        float cx = w / 2f, cy = h * 0.27f;
        tag(g, upper(Component.translatable("worldboss.beyondsolo.hud.authority")), cx, cy - 16, Anim.outBack(Anim.window(t, 0f, 0.3f)), kc, 6f);
        float stamp = Anim.window(t, 0.15f, 0.45f);
        if (stamp > 0) {
            float s = Mth.lerp(Anim.outCubic(stamp), 2.2f, 1f);
            String name = upper(k.authorityName());
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            g.pose().scale(s, s, 1);
            float o2 = Gfx.fade(Math.min(1f, stamp * 1.6f));
            float nw = SysFont.spacedWidth(name, 18f, true, 3f);
            Shapes.box(-nw / 2f - 24, -4, nw + 48, 26).radius(13).fill(0).glow(22, Gfx.alpha(kc, 0.35f + 0.2f * throb)).draw(g);
            SysFont.spaced(g, name, 0, 0, 18f, Gfx.mix(kc, WHITE, 0.65f), true, 3f, 0.5f);
            Gfx.setAlpha(o2);
            g.pose().popPose();
        }
        float rest = Anim.outCubic(Anim.window(t, 0.5f, 0.9f));
        if (rest > 0.01f) {
            float o3 = Gfx.fade(rest);
            SysFont.draw(g, Component.translatable("worldboss.beyondsolo." + k.id + ".authority.hint"), cx, cy + 26, 8f, WHITE, true, 0.5f);
            if (left > 0) {
                String secs = String.format(Locale.ROOT, "%.1f", left / 20f);
                float pop = 1 + 0.12f * throb;
                g.pose().pushPose();
                g.pose().translate(cx, cy + 44, 0);
                g.pose().scale(pop, pop, 1);
                SysFont.draw(g, secs, 0, 0, 14f, left < 30 ? SystemTheme.DANGER : Gfx.mix(kc, WHITE, 0.5f), true, 0.5f);
                g.pose().popPose();
            }
            Gfx.setAlpha(o3);
        }
        Gfx.setAlpha(old);
    }

    /** It fell: the kill, this hunter's share and place, and the XP it paid them. */
    private static void slain(GuiGraphics g, int w, int h, WorldBossClient.Banner b, float t, float live) {
        WorldBossKind k = b.boss;
        boolean took = b.a >= 0;
        int gold = k.monarch() ? tier(k) : SystemTheme.GOLD;
        float flash = 1 - Anim.outCubic(Anim.window(t, 0f, 0.8f));
        Shapes.box(0, 0, w, h).fill(Gfx.alpha(WHITE, (took ? 0.5f : 0.25f) * flash)).draw(g);
        Shapes.vignette(0, 0, w, h, 0.2f, 1.4f).fill(Gfx.alpha(gold, 0.18f * live * (1 - Anim.window(t, 1.5f, 4f)))).draw(g);
        float old = Gfx.fade(live);
        float cx = w / 2f, y = h * 0.24f;
        float open = Anim.outBack(Anim.window(t, 0.15f, 0.7f));
        float pw = took ? 250 : 210, ph = took ? 72 : 46;
        g.pose().pushPose();
        g.pose().translate(cx, y + ph / 2f, 0);
        g.pose().scale(1, Math.max(0.02f, open), 1);
        Shapes.rays(0, 0, 170, 22).fill(Gfx.alpha(gold, 0.2f * Math.min(1f, open))).rotation(t * 0.04f).additive().draw(g);
        Ui.panel(g, -pw / 2f, -ph / 2f, pw, ph, 10, gold, 1f);
        g.pose().popPose();
        if (open > 0.95f) {
            float hk = Anim.outCubic(Anim.window(t, 0.6f, 1.0f));
            float o2 = Gfx.fade(hk);
            String head = upper(Component.translatable(k.monarch() ? "worldboss.beyondsolo.banner.slain.monarch" : "worldboss.beyondsolo.banner.slain.world"));
            SysFont.spaced(g, head, cx, y + 9 - 4 * (1 - hk), 11f, Gfx.mix(gold, WHITE, 0.55f), true, 2.6f, 0.5f);
            Gfx.setAlpha(o2);
            Component sub = took ? k.fullName() : Component.translatable("worldboss.beyondsolo.banner.fallen", k.fullName());
            SysFont.draw(g, sub, cx, y + 26, 7f, Gfx.alpha(SystemTheme.TEXT_SOFT, hk), false, 0.5f);
            float count = Anim.outCubic(Anim.window(t, 1.1f, 2.6f));
            if (took && count > 0) {
                String share = Component.translatable("worldboss.beyondsolo.hud.share", String.format(Locale.ROOT, "%.1f%%", b.a / 10f)).getString();
                String xp = Component.translatable("worldboss.beyondsolo.banner.xp", Math.round(b.b * count)).getString();
                String place = b.place > 0 ? Component.translatable("worldboss.beyondsolo.hud.place", b.place).getString() : null;
                float chipH = 12, size = 7f;
                float sw = SysFont.width(share, size, true) + chipH * 0.9f;
                float xw = SysFont.width(xp, size, true) + chipH * 0.9f;
                float pwd = place == null ? 0 : SysFont.width(place, size, true) + chipH * 0.9f + 4;
                float x = cx - (sw + 4 + xw + pwd) / 2f;
                x += Ui.chip(g, x, y + 42, chipH, share, size, SystemTheme.ACCENT, false) + 4;
                if (place != null) x += Ui.chip(g, x, y + 42, chipH, place, size, b.place == 1 ? SystemTheme.GOLD : SystemTheme.ACCENT_BRIGHT, b.place == 1) + 4;
                Ui.chip(g, x, y + 42, chipH, xp, size, SystemTheme.GOOD, true);
            }
        }
        Gfx.setAlpha(old);
    }

    /** It left the world without falling. */
    private static void withdraw(GuiGraphics g, int w, int h, WorldBossClient.Banner b, float t, float live) {
        WorldBossKind k = b.boss;
        int kc = color(k);
        float old = Gfx.fade(live * Anim.outCubic(Anim.window(t, 0f, 0.6f)));
        float cx = w / 2f, y = h * 0.28f;
        Ui.divider(g, cx - 110, y - 4, 220, Gfx.alpha(kc, 0.7f));
        SysFont.spaced(g, upper(Component.translatable("worldboss.beyondsolo.banner.gone")), cx, y + 2, 10f, Gfx.mix(kc, WHITE, 0.45f), true, 2.6f, 0.5f);
        SysFont.draw(g, Component.translatable(k.monarch() ? "worldboss.beyondsolo.notify.ascended" : "worldboss.beyondsolo.notify.sank", k.fullName()),
                cx, y + 17, 7f, SystemTheme.TEXT_SOFT, false, 0.5f);
        Ui.divider(g, cx - 110, y + 29, 220, Gfx.alpha(kc, 0.7f));
        Gfx.setAlpha(old);
    }

    static void reset() {
        barIn = 0;
        markIn = 0;
        domainIn = 0;
        lastTracked = null;
        kicked = null;
    }

    private WorldBossHud() {}
}
