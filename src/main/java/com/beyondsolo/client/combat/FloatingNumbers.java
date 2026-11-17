package com.beyondsolo.client.combat;

import com.beyondsolo.client.ClientConfig;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.enemy.ElementalProfile;
import com.beyondsolo.skill.SkillElement;
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
 * critical hits come out bigger and gold. A skill or infusion hit says how it landed over its number: WEAK (bigger,
 * gold), RESIST (smaller, grey), IMMUNE (no number at all).
 */
public final class FloatingNumbers {
    private record Number(Vec3 at, float dx, String text, int color, boolean crit, boolean element, double start,
                          @org.jetbrains.annotations.Nullable String label, int labelColor, float scale) {}

    private static final float LIFE = 1.1f;
    private static final List<Number> NUMBERS = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    public static void add(DamageNumberPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientConfig.DAMAGE_NUMBERS.get() || mc.level == null) return;
        Entity e = mc.level.getEntity(p.entityId());
        if (e == null) return;
        DamageElement element = DamageElement.byIndex(p.element());
        SkillElement skill = p.skillElement() >= 0 && p.skillElement() < SkillElement.values().length ? SkillElement.values()[p.skillElement()] : null;
        boolean isElement = element != DamageElement.PHYSICAL || (skill != null && skill != SkillElement.PHYSICAL);
        int elementColor = skill != null && skill != SkillElement.PHYSICAL ? skill.color() : element.color();
        int color = p.crit() ? 0xFFFFD166 : isElement ? Gfx.mix(0xFF000000 | elementColor, 0xFFFFFFFF, 0.2f) : 0xFFF4F7FA;
        float amount = p.amount();
        String text = amount < 9.95f ? String.format("%.1f", amount) : String.valueOf(Math.round(amount));
        // how a skill's element landed
        String label = null;
        int labelColor = 0;
        float scale = 1f;
        if (p.rating() >= 0) {
            ElementalProfile.Rating rating = ElementalProfile.Rating.byIndex(p.rating());
            if (rating != ElementalProfile.Rating.NEUTRAL) label = rating.label().getString();
            switch (rating) {
                case WEAK_SEVERE -> { labelColor = 0xFFFF8A3D; scale = 1.3f; }
                case WEAK -> { labelColor = 0xFFFFC857; scale = 1.15f; }
                case RESIST -> { labelColor = 0xFF9FB3C8; scale = 0.85f; }
                case RESIST_STRONG -> { labelColor = 0xFF8496AA; scale = 0.75f; }
                case IMMUNE -> { labelColor = 0xFF8A96A3; text = ""; }
                default -> { }
            }
        }
        Vec3 at = new Vec3(e.getX() + (RANDOM.nextFloat() - 0.5f) * e.getBbWidth() * 0.8f,
                e.getY() + e.getBbHeight() * (0.65f + RANDOM.nextFloat() * 0.25f),
                e.getZ() + (RANDOM.nextFloat() - 0.5f) * e.getBbWidth() * 0.8f);
        NUMBERS.add(new Number(at, (RANDOM.nextFloat() - 0.5f) * 14f, text, color, p.crit(), isElement, Anim.now(), label, labelColor, scale));
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
            float size = (n.crit ? 13f : n.element ? 10.5f : 9.5f) * n.scale * distScale * (0.4f + 0.6f * pop) * (n.crit ? 1f + 0.25f * (1 - Anim.clamp01(t / 0.3f)) : 1f);
            float alpha = 1 - Anim.clamp01((t - 0.6f) / 0.4f);
            float x = p[0] + n.dx * rise * distScale;
            float y = p[1];
            float old = Gfx.fade(alpha);
            String label = n.crit && !n.text.isEmpty() ? n.text + "!" : n.text;
            if (!label.isEmpty()) {
                float w = SysFont.width(label, size, true);
                Shapes.pill(x - w / 2f - size * 0.35f, y - size * 0.55f, w + size * 0.7f, size * 1.1f)
                        .fill(0x00000000).glow(size * 0.7f, Gfx.alpha(n.color, n.crit ? 0.45f : 0.25f)).draw(g);
                SysFont.draw(g, label, x + 0.6f, y - size * 0.36f + 0.7f, size, 0xC0000000, true, 0.5f);
                SysFont.draw(g, label, x, y - size * 0.36f, size, n.color, true, 0.5f);
            }
            if (n.label != null) {
                // the word over the number pops a little later and harder than the number
                float wpop = Anim.outBackStrong(Anim.clamp01((t - 0.04f) / 0.2f));
                float ls = Math.max(4.2f, size * 0.52f) * (0.6f + 0.4f * wpop) / Math.max(0.75f, n.scale);
                float ly = label.isEmpty() ? y - ls * 0.5f : y - size * 0.62f - ls * 1.05f;
                float lw = SysFont.spacedWidth(n.label, ls, true, ls * 0.12f);
                Shapes.pill(x - lw / 2f - ls * 0.5f, ly - ls * 0.2f, lw + ls, ls * 1.25f).fill(Gfx.alpha(0xFF070A12, 0.5f))
                        .glow(ls * 0.6f, Gfx.alpha(n.labelColor, 0.35f)).draw(g);
                SysFont.spaced(g, n.label, x, ly, ls, n.labelColor, true, ls * 0.12f, 0.5f);
            }
            Gfx.setAlpha(old);
        }
    }

    public static void clear() {
        NUMBERS.clear();
    }

    private FloatingNumbers() {}
}
