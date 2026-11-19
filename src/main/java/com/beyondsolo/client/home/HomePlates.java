package com.beyondsolo.client.home;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.combat.WorldProjector;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.home.TrainingDummy;
import com.beyondsolo.worldboss.WorldBossKind;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What floats over the things of a home, drawn like the System's names over creatures (under the HUD): over a training
 * dummy, a meter of the bout it is taking (damage a second, the last blow, the whole bout and how long), or its name while
 * it stands idle under the crosshair; over a trophy, the great threat's name and how many times the owner has slain it,
 * or a question mark over an empty pedestal.
 */
public final class HomePlates {
    /** How far a dummy's meter and a trophy's name are read. */
    private static final double DUMMY_RANGE = 18, TROPHY_RANGE = 11;
    /** A meter stays up this long (ticks) after the last blow, then fades. */
    private static final int LINGER = TrainingDummy.BOUT_GAP + 70;

    private record Seen(BlockPos pos, @Nullable WorldBossKind kind, int slain) {}

    /** The pedestals drawn in the world this frame (their renderer tells us). */
    private static final List<Seen> PEDESTALS = new ArrayList<>();

    static void pedestal(BlockPos pos, @Nullable WorldBossKind kind, int slain) {
        if (PEDESTALS.size() < 64) PEDESTALS.add(new Seen(pos.immutable(), kind, slain));
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.level == null || mc.player == null || mc.options.hideGui) return;
            float partial = delta.getGameTimeDeltaPartialTick(false);
            long now = mc.level.getGameTime();
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof TrainingDummy d && d.isAlive() && mc.player.distanceToSqr(d) < DUMMY_RANGE * DUMMY_RANGE) dummy(g, mc, d, now, partial);
            }
            for (Seen s : PEDESTALS) trophy(g, mc, s);
        } finally {
            PEDESTALS.clear();
        }
    }

    // ------------------------------------------------------------------ the dummies

    private static void dummy(GuiGraphics g, Minecraft mc, TrainingDummy d, long now, float partial) {
        // when it last took a blow, as this client saw it
        if (d.clientHits < 0) {
            d.clientHits = d.hits();
            d.clientChanged = now - 10_000;
        } else if (d.hits() != d.clientHits) {
            d.clientHits = d.hits();
            d.clientChanged = now;
        }
        long age = now - d.clientChanged;
        boolean meter = d.hits() > 0 && age < LINGER;
        boolean named = !meter && mc.crosshairPickEntity == d;
        if (!meter && !named) return;
        double dist = mc.player.distanceTo(d);
        if (!mc.player.hasLineOfSight(d)) return;
        Vec3 at = d.getPosition(partial).add(0, d.getBbHeight() + 0.45, 0);
        float[] p = WorldProjector.project(at);
        if (p == null) return;
        float scale = (float) Math.max(0.75, Math.min(1.2, 7.0 / Math.max(1, dist)));
        float fade = dist > DUMMY_RANGE - 5 ? (float) (DUMMY_RANGE - dist) / 5f : 1f;
        int accent = d.armoured() ? 0xFF9FB4C8 : 0xFFFFC86E;
        if (named) {
            float old = Gfx.fade(fade);
            String name = Component.translatable(d.armoured() ? "entity.beyondsolo.training_dummy.armoured" : "entity.beyondsolo.training_dummy").getString();
            float size = 6.2f * scale;
            float w = SysFont.width(name, size, true) + 12 * scale;
            Shapes.pill(p[0] - w / 2, p[1] - 12 * scale, w, 10 * scale).fill(Gfx.alpha(0xFF060A14, 0.7f)).border(0.6f, Gfx.alpha(accent, 0.55f)).draw(g);
            SysFont.draw(g, name, p[0], SysFont.centerY(p[1] - 12 * scale, 10 * scale, size), size, SystemTheme.TEXT, true, 0.5f);
            Gfx.setAlpha(old);
            return;
        }
        // the meter fades once the bout has been over a while
        float out = age > LINGER - 25 ? (LINGER - age) / 25f : 1f;
        float pop = age < 4 ? 1 + 0.08f * (4 - age) / 4f : 1f;
        float old = Gfx.fade(Math.max(0, fade * out));
        boolean live = age < TrainingDummy.BOUT_GAP;
        float big = 9.5f * scale * pop, small = 5.0f * scale;
        String dps = number(d.dps());
        String unit = Component.translatable("home.beyondsolo.plate.dps").getString();
        String line = Component.translatable("home.beyondsolo.plate.line", number(d.last()), number(d.total()),
                String.format(Locale.ROOT, "%.1f", Math.max(1, d.boutTicks()) / 20f)).getString();
        float wBig = SysFont.width(dps, big, true) + 3 * scale + SysFont.width(unit, small * 1.1f, true);
        float wLine = SysFont.width(line, small, false);
        float w = Math.max(wBig, wLine) + 16 * scale;
        float h = 25 * scale;
        float x = p[0] - w / 2, y = p[1] - h - 2 * scale;
        Shapes.box(x, y, w, h).radius(6 * scale).fillV(Gfx.fade(SystemTheme.GLASS_TOP, 0.82f), Gfx.fade(SystemTheme.GLASS_BOTTOM, 0.82f))
                .border(0.7f, Gfx.alpha(accent, live ? 0.85f : 0.45f), Gfx.alpha(accent, 0.2f)).glow(live ? 5 : 2, Gfx.alpha(accent, live ? 0.3f : 0.12f)).draw(g);
        float bx = p[0] - wBig / 2;
        SysFont.draw(g, dps, bx, y + 3.5f * scale, big, live ? Gfx.mix(accent, 0xFFFFFFFF, 0.55f) : SystemTheme.TEXT_SOFT, true, 0f);
        SysFont.draw(g, unit, bx + SysFont.width(dps, big, true) + 3 * scale, y + 3.5f * scale + big * 0.36f, small * 1.1f,
                Gfx.alpha(accent, 0.9f), true, 0f);
        SysFont.draw(g, line, p[0], y + h - 8.5f * scale, small, SystemTheme.TEXT_DIM, false, 0.5f);
        Gfx.setAlpha(old);
    }

    /** "1,234", "12.5", "0.8": whole numbers once there is a lot of it. */
    static String number(float v) {
        if (v >= 100) return String.format(Locale.ROOT, "%,.0f", v);
        return String.format(Locale.ROOT, "%.1f", v);
    }

    // ------------------------------------------------------------------ the trophies

    private static void trophy(GuiGraphics g, Minecraft mc, Seen s) {
        Vec3 base = Vec3.atBottomCenterOf(s.pos);
        double dist = mc.player.position().distanceTo(base);
        if (dist > TROPHY_RANGE) return;
        boolean slain = s.kind != null && s.slain > 0;
        Vec3 at = base.add(0, TrophyPedestalRenderer.TOP + (slain ? TrophyPedestalRenderer.STATUE + 0.3 : 0.55), 0);
        float[] p = WorldProjector.project(at);
        if (p == null) return;
        float scale = (float) Math.max(0.7, Math.min(1.1, 6.0 / Math.max(1, dist)));
        float fade = dist > TROPHY_RANGE - 3 ? (float) (TROPHY_RANGE - dist) / 3f : 1f;
        float old = Gfx.fade(fade);
        if (!slain) {
            float size = 11f * scale;
            int c = Gfx.alpha(0xFFB9A8E8, 0.55f + 0.2f * Anim.pulse(2.6f));
            SysFont.draw(g, "?", p[0], p[1] - size * 0.8f, size, c, true, 0.5f);
            Gfx.setAlpha(old);
            return;
        }
        int color = Gfx.mix(0xFF000000 | s.kind.color, 0xFFFFFFFF, 0.55f);
        String name = s.kind.bossName().getString();
        String count = Component.translatable("home.beyondsolo.plate.slain", s.slain).getString();
        float size = 6.4f * scale, small = 5.0f * scale;
        float w = Math.max(SysFont.width(name, size, true), SysFont.width(count, small, true)) + 14 * scale;
        float h = 17 * scale;
        float x = p[0] - w / 2, y = p[1] - h;
        Shapes.box(x, y, w, h).radius(5 * scale).fill(Gfx.alpha(0xFF070A12, 0.72f)).border(0.6f, Gfx.alpha(0xFF000000 | s.kind.color, 0.6f)).draw(g);
        SysFont.draw(g, name, p[0], y + 2.5f * scale, size, color, true, 0.5f);
        SysFont.draw(g, count, p[0], y + h - 7f * scale, small, SystemTheme.GOLD, true, 0.5f);
        Gfx.setAlpha(old);
    }

    private HomePlates() {}
}
