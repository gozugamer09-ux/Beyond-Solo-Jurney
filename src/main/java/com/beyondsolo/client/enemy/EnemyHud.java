package com.beyondsolo.client.enemy;

import com.beyondsolo.client.combat.WorldProjector;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.enemy.Affinity;
import com.beyondsolo.enemy.Affix;
import com.beyondsolo.enemy.EnemyTraits;
import com.beyondsolo.enemy.Variant;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Name plates over elites and champions: a small tag ("ELITE", "CHAMPION") in the variant's colour, the creature's
 * name with its affinity ("Frostbound Gloomfang"), and its affixes, drawn with the System's own font and shapes.
 */
public final class EnemyHud {
    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        for (var entry : EnemyClient.all().int2ObjectEntrySet()) {
            EnemyTraits t = entry.getValue();
            if (!t.variant().special()) continue;
            Entity e = mc.level.getEntity(entry.getIntKey());
            if (!(e instanceof LivingEntity le) || !le.isAlive()) continue;
            double dist = mc.player.distanceTo(e);
            if (dist > 24 || !mc.player.hasLineOfSight(e)) continue;
            Vec3 pos = e.getPosition(partial).add(0, e.getBbHeight() + 1.05, 0);
            float[] p = WorldProjector.project(pos);
            if (p == null) continue;
            float scale = (float) Math.max(0.7, Math.min(1.15, 8.0 / Math.max(1, dist)));
            float old = Gfx.fade(dist > 18 ? (float) (24 - dist) / 6f : 1f);
            plate(g, le, t, p[0], p[1], scale);
            Gfx.setAlpha(old);
        }
    }

    private static void plate(GuiGraphics g, LivingEntity e, EnemyTraits t, float cx, float bottom, float scale) {
        int vc = 0xFF000000 | t.variant().color();
        int ac = 0xFF000000 | (t.affinity() == Affinity.NONE ? 0xE8ECF5 : t.affinity().color);
        MutableComponent name = Component.empty();
        if (t.affinity() != Affinity.NONE) name.append(t.affinity().adjective()).append(" ");
        name.append(e.getType().getDescription());
        float nameSize = 7.5f * scale;
        float tagSize = 5f * scale;
        float nameW = SysFont.width(name, nameSize, true);
        String tag = t.variant().title().getString().toUpperCase();
        float y = bottom - nameSize * 1.1f;
        float pad = 5 * scale;
        float boxW = nameW + pad * 2;
        float boxH = nameSize * 1.5f;
        boolean champion = t.variant() == Variant.CHAMPION;
        float glow = champion ? 4 + 2 * Anim.pulse(1.2f) : 3;
        Shapes.box(cx - boxW / 2, y - boxH * 0.2f, boxW, boxH).radius(boxH / 2).fillV(0xC0101420, 0xC0060810)
                .border(0.8f * scale, Gfx.alpha(vc, 0.8f)).glow(glow * scale, Gfx.alpha(vc, champion ? 0.55f : 0.35f)).draw(g);
        SysFont.draw(g, name, cx, y + boxH * 0.3f - nameSize * 0.45f, nameSize, ac, true, 0.5f);
        // the variant tag sits on the top edge
        float tagW = SysFont.spacedWidth(tag, tagSize, true, 1.2f) + 6 * scale;
        float ty = y - boxH * 0.2f - tagSize * 0.75f;
        Shapes.pill(cx - tagW / 2, ty, tagW, tagSize * 1.5f).fill(Gfx.alpha(Gfx.shade(vc, -0.55f), 0.95f)).border(0.6f * scale, vc).draw(g);
        SysFont.spaced(g, tag, cx, ty + tagSize * 0.25f, tagSize, vc, true, 1.2f, 0.5f);
        // affixes under the plate
        var affixes = t.affixes();
        if (!affixes.isEmpty()) {
            StringBuilder b = new StringBuilder();
            for (Affix a : affixes) {
                if (!b.isEmpty()) b.append("  ·  ");
                b.append(a.title().getString());
            }
            SysFont.draw(g, b.toString(), cx, y + boxH * 0.8f + 1.5f * scale, 5.5f * scale, 0xFFC8CEDA, false, 0.5f);
        }
    }

    private EnemyHud() {}
}
