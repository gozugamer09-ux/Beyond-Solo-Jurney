package com.beyondsolo.client.economy;

import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.economy.Economy;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.List;

/**
 * The gold counter on the HUD. It stays out of the way until gold changes: then a capsule slides in at the top
 * right, the number rolls to the new balance, coins spark off the icon and the change floats out under it with
 * where it came from ("+18 Monster", "-650 Purchase"). Gains of the same kind close together add up into one line.
 */
public final class GoldHud {
    public static final int GOLD = 0xFFFFC94A;
    public static final int GOLD_DEEP = 0xFFD98A1C;
    private static final float HOLD = 4.5f;

    private record Chip(double at, long amount, Economy.Reason reason) {}

    private static final List<Chip> chips = new ArrayList<>();
    private static float shown = 0;
    private static long target = 0;
    private static double changedAt = -100;
    private static float visible = 0;
    private static final float[][] sparks = new float[10][];
    private static double sparkAt = -10;

    static void snap(long total) {
        shown = total;
        target = total;
    }

    static void push(long delta, long total, Economy.Reason reason) {
        target = total;
        double now = Anim.now();
        changedAt = now;
        if (!chips.isEmpty()) {
            Chip last = chips.get(chips.size() - 1);
            if (last.reason == reason && Long.signum(last.amount) == Long.signum(delta) && now - last.at < 1.2) {
                chips.set(chips.size() - 1, new Chip(now, last.amount + delta, reason));
                if (delta > 0) burst(now);
                return;
            }
        }
        chips.add(new Chip(now, delta, reason));
        while (chips.size() > 4) chips.remove(0);
        if (delta > 0) burst(now);
    }

    private static void burst(double now) {
        sparkAt = now;
        java.util.Random r = new java.util.Random((long) (now * 1000));
        for (int i = 0; i < sparks.length; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            float speed = 14 + r.nextFloat() * 16;
            sparks[i] = new float[]{(float) Math.cos(a) * speed, (float) Math.sin(a) * speed - 8, 0.8f + r.nextFloat() * 1.2f};
        }
    }

    public static String reasonLabel(Economy.Reason r) {
        return Component.translatable("gold.beyondsolo.reason." + r.name().toLowerCase()).getString();
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !ClientSystemData.get().hasSystem()) return;
        double now = Anim.now();
        boolean active = now - changedAt < HOLD;
        visible = Anim.approach(visible, active ? 1 : 0, active ? 9f : 4f);
        // the number rolls quickly towards the new balance
        float diff = target - shown;
        shown = Math.abs(diff) < 0.5f ? target : shown + diff * Math.min(1f, Anim.dt() * 7f);
        if (visible < 0.01f) {
            chips.clear();
            return;
        }

        float sw = g.guiWidth();
        float y = 8 + effectRows(mc) * 26;
        float h = 18;
        String amount = EconomyClient.format(Math.round(shown));
        float tw = SysFont.width(amount, 8.5f, true);
        float w = tw + h + 22;
        float slide = (1 - Anim.outCubic(visible)) * (w + 12);
        float x = sw - 8 - w + slide;
        float old = Gfx.fade(Anim.clamp01(visible * 1.4f));

        float flash = 1 - Anim.clamp01(Anim.since(changedAt) / 0.6f);
        Shapes.box(x + 1, y + 2, w - 2, h - 2).radius(h / 2f).fill(0).glow(7, 0x50000000).draw(g);
        Shapes.pill(x, y, w, h).fillV(Gfx.fade(SystemTheme.GLASS_TOP, 0.9f), Gfx.fade(SystemTheme.GLASS_BOTTOM, 0.9f))
                .border(0.75f, Gfx.alpha(GOLD, 0.7f + 0.3f * flash), Gfx.alpha(GOLD, 0.2f))
                .glow(5 + 6 * flash, Gfx.alpha(GOLD, 0.12f + 0.35f * flash)).draw(g);
        // coin
        float cx = x + h / 2f + 1;
        float cy = y + h / 2f;
        float bump = 1 + 0.25f * flash * flash;
        Shapes.circle(cx, cy, 6.5f * bump).fillV(GOLD, GOLD_DEEP).border(0.75f, 0xFFFFF0C0, 0xFFB86A10).glow(3 + 4 * flash, Gfx.alpha(GOLD, 0.6f)).draw(g);
        Shapes.icon(Icons.COINS, cx, cy, 8.5f * bump).fill(0xFF5A3200).draw(g);
        // sparks off the coin
        float st = Anim.since(sparkAt);
        if (st < 0.9f) {
            for (float[] s : sparks) {
                if (s == null) continue;
                float k = st / 0.9f;
                float sx = cx + s[0] * Anim.outCubic(k);
                float sy = cy + s[1] * Anim.outCubic(k) + 18 * k * k;
                Shapes.circle(sx, sy, s[2] * (1 - k)).fill(Gfx.alpha(0xFFFFE9A0, 1 - k)).glow(2, Gfx.alpha(GOLD, 0.8f * (1 - k))).draw(g);
            }
        }
        SysFont.title(g, amount, x + h + 4, SysFont.centerY(y, h, 8.5f), 8.5f, Gfx.mix(0xFFFFF4D6, 0xFFFFFFFF, flash));
        SysFont.title(g, "G", x + h + 6 + tw, SysFont.centerY(y, h, 6.5f) + 0.5f, 6.5f, GOLD);

        // the changes, floating out under the capsule
        float cyLine = y + h + 4;
        for (int i = chips.size() - 1; i >= 0; i--) {
            Chip c = chips.get(i);
            float t = Anim.since(c.at);
            if (t > 2.6f) continue;
            float in = Anim.outBack(Anim.clamp01(t / 0.25f));
            float out = 1 - Anim.clamp01((t - 1.9f) / 0.7f);
            float a = Anim.clamp01(in) * out;
            if (a <= 0.01f) continue;
            boolean gain = c.amount > 0;
            int col = gain ? GOLD : SystemTheme.DANGER;
            String num = (gain ? "+" : "−") + EconomyClient.format(Math.abs(c.amount));
            String why = reasonLabel(c.reason);
            float nw = SysFont.width(num, 7.5f, true);
            float ww = SysFont.width(why, 5.8f, false);
            float right = sw - 10 + (1 - in) * 16;
            float o2 = Gfx.fade(a);
            SysFont.title(g, num, right - nw, cyLine, 7.5f, Gfx.mix(col, 0xFFFFFFFF, 0.2f));
            SysFont.text(g, why, right - nw - 4 - ww, cyLine + 1.5f, 5.8f, SystemTheme.TEXT_SOFT);
            Gfx.setAlpha(o2);
            cyLine += 10 * Math.min(1, a * 1.5f);
        }
        Gfx.setAlpha(old);
    }

    /** Rows of status-effect icons vanilla draws in the top right corner. */
    private static int effectRows(Minecraft mc) {
        boolean good = false, bad = false;
        for (MobEffectInstance e : mc.player.getActiveEffects()) {
            if (!e.showIcon()) continue;
            if (e.getEffect().value().isBeneficial()) good = true;
            else bad = true;
        }
        return (good ? 1 : 0) + (bad ? 1 : 0);
    }

    private GoldHud() {}
}
