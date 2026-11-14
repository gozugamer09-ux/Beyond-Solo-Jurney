package com.beyondsolo.client.combat;

import com.beyondsolo.client.ClientConfig;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.network.DamageNumberPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Damage numbers that pop out of a hit, rise and fade. Element hits take the element's colour,
 * critical hits come out bigger and gold.
 */
public final class FloatingNumbers {
    private record Number(Vec3 at, float dx, String text, int color, boolean crit, boolean element, double start) {}

    private static final float LIFE = 1.1f;
    private static final List<Number> NUMBERS = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    public static void add(DamageNumberPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientConfig.DAMAGE_NUMBERS.get() || mc.level == null) return;
        Entity e = mc.level.getEntity(p.entityId());
        if (e == null) return;
        DamageElement element = DamageElement.byIndex(p.element());
        boolean isElement = element != DamageElement.PHYSICAL;
        int color = p.crit() ? 0xFFFFD166 : isElement ? Gfx.mix(0xFF000000 | element.color(), 0xFFFFFFFF, 0.2f) : 0xFFF4F7FA;
        float amount = p.amount();
        String text = amount < 9.95f ? String.format("%.1f", amount) : String.valueOf(Math.round(amount));
        Vec3 at = new Vec3(e.getX() + (RANDOM.nextFloat() - 0.5f) * e.getBbWidth() * 0.8f,
                e.getY() + e.getBbHeight() * (0.65f + RANDOM.nextFloat() * 0.25f),
                e.getZ() + (RANDOM.nextFloat() - 0.5f) * e.getBbWidth() * 0.8f);
        NUMBERS.add(new Number(at, (RANDOM.nextFloat() - 0.5f) * 14f, text, color, p.crit(), isElement, Anim.now()));
        if (NUMBERS.size() > 60) NUMBERS.remove(0);
    }

    public static void render(GuiGraphics g) {
        if (NUMBERS.isEmpty()) return;
        Iterator<Number> it = NUMBERS.iterator();
        while (it.hasNext()) {
            Number n = it.next();
            float t = Anim.progress(n.start, LIFE);
            if (t >= 1) {
                it.remove();
                continue;
            }
            float rise = Anim.outCubic(t);
            float[] p = WorldProjector.project(n.at.add(0, rise * 0.7, 0));
            if (p == null) continue;
            double dist = WorldProjector.camera().distanceTo(n.at);
            float distScale = (float) Math.max(0.55, Math.min(1.15, 6.0 / Math.max(1, dist)));
            float pop = Anim.outBackStrong(Anim.clamp01(t / 0.22f));
            float size = (n.crit ? 13f : n.element ? 10.5f : 9.5f) * distScale * (0.4f + 0.6f * pop) * (n.crit ? 1f + 0.25f * (1 - Anim.clamp01(t / 0.3f)) : 1f);
            float alpha = 1 - Anim.clamp01((t - 0.6f) / 0.4f);
            float x = p[0] + n.dx * rise * distScale;
            float y = p[1];
            float old = Gfx.fade(alpha);
            String label = n.crit ? n.text + "!" : n.text;
            float w = SysFont.width(label, size, true);
            Shapes.pill(x - w / 2f - size * 0.35f, y - size * 0.55f, w + size * 0.7f, size * 1.1f)
                    .fill(0x00000000).glow(size * 0.7f, Gfx.alpha(n.color, n.crit ? 0.45f : 0.25f)).draw(g);
            SysFont.draw(g, label, x + 0.6f, y - size * 0.36f + 0.7f, size, 0xC0000000, true, 0.5f);
            SysFont.draw(g, label, x, y - size * 0.36f, size, n.color, true, 0.5f);
            Gfx.setAlpha(old);
        }
    }

    public static void clear() {
        NUMBERS.clear();
    }

    private FloatingNumbers() {}
}
