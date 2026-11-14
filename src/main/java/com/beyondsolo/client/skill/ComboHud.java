package com.beyondsolo.client.skill;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.combat.WorldProjector;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.SkillCombat;
import com.beyondsolo.skill.SkillReaction;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The combo counter (right of the crosshair: the chain, its bonus, and the time left to add to it) and the names
 * of element reactions, which pop out of the creature they went off on.
 */
public final class ComboHud {
    private static final float WINDOW = SkillCombat.COMBO_WINDOW / 20f;
    private static final float LABEL_TIME = 1.5f;

    private record Label(SkillReaction reaction, Vec3 at, double start) {}

    private static final List<Label> LABELS = new ArrayList<>();
    private static int count;
    private static int color = 0xFFFFFFFF;
    private static double comboAt = -100;
    private static float shown;

    /** The server says a cast chained: {@code chain} skills in a row now. */
    public static void combo(Skill skill, int chain) {
        count = chain;
        color = Gfx.opaque(skill.color());
        comboAt = Anim.now();
    }

    public static void reaction(SkillReaction reaction, Vec3 at) {
        LABELS.add(new Label(reaction, at, Anim.now()));
        if (LABELS.size() > 12) LABELS.remove(0);
    }

    public static void reset() {
        LABELS.clear();
        count = 0;
        comboAt = -100;
        shown = 0;
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        labels(g);

        float t = Anim.since(comboAt);
        boolean live = count >= 2 && t < WINDOW;
        shown = Anim.approach(shown, live ? 1 : 0, live ? 10f : 5f);
        if (shown < 0.02f) return;
        float old = Gfx.fade(shown);
        float cx = g.guiWidth() / 2f + 74;
        float cy = g.guiHeight() / 2f + 6;
        float pop = Anim.outBack(Anim.clamp01(t / 0.25f));
        float left = Anim.clamp01(1 - t / WINDOW);
        int light = Gfx.mix(color, 0xFFFFFFFF, 0.45f);
        boolean max = count >= SkillCombat.COMBO_MAX;

        // the time left to chain another skill, as a ring that drains
        Shapes.ring(cx, cy, 17, 1.2f).fill(Gfx.alpha(0xFF0A1220, 0.55f)).draw(g);
        Shapes.ring(cx, cy, 17, 1.2f).fill(Gfx.alpha(left < 0.25f ? SystemTheme.DANGER : color, 0.95f)).progress(left).draw(g);
        if (t < 0.4f) Shapes.ring(cx, cy, 17 + 12 * Anim.outCubic(t / 0.4f), 1.6f * (1 - t / 0.4f)).fill(Gfx.alpha(light, 1 - t / 0.4f)).draw(g);
        Shapes.circle(cx, cy, 14).fill(Gfx.alpha(0xFF050A14, 0.5f)).draw(g);

        String n = "x" + count;
        float size = 13f * (0.7f + 0.3f * pop);
        SysFont.draw(g, n, cx, cy - size * 0.55f, size, max ? 0xFFFFE27A : 0xFFFFFFFF, true, 0.5f);
        String head = Component.translatable(max ? "skills.beyondsolo.combo.max" : "skills.beyondsolo.combo").getString();
        SysFont.spaced(g, head, cx, cy - 29, 5.5f, light, true, 1.4f, 0.5f);
        String bonus = "+" + Math.round(SkillCombat.COMBO_STEP * 100 * (count - 1)) + "%";
        SysFont.draw(g, bonus, cx, cy + 21, 6.5f, light, true, 0.5f);
        Gfx.setAlpha(old);
    }

    private static void labels(GuiGraphics g) {
        Iterator<Label> it = LABELS.iterator();
        while (it.hasNext()) {
            Label l = it.next();
            float t = Anim.since(l.start);
            if (t >= LABEL_TIME) {
                it.remove();
                continue;
            }
            float[] at = WorldProjector.project(l.at.add(0, 0.6 + t * 0.7, 0));
            if (at == null) continue;
            float in = Anim.outBack(Anim.clamp01(t / 0.22f));
            float out = Anim.clamp01((t - (LABEL_TIME - 0.4f)) / 0.4f);
            float old = Gfx.fade(Anim.clamp01(t / 0.1f) * (1 - out));
            int c = Gfx.opaque(l.reaction.color());
            String name = l.reaction.displayName().getString().toUpperCase();
            float size = 9.5f * (0.6f + 0.4f * in);
            float w = SysFont.spacedWidth(name, size, true, 1.2f);
            Shapes.box(at[0] - w / 2f - 7, at[1] - 3, w + 14, size + 5).radius((size + 5) / 2f)
                    .fill(Gfx.alpha(0xFF050A14, 0.55f)).border(0.7f, Gfx.alpha(c, 0.9f), Gfx.alpha(c, 0.3f)).draw(g);
            SysFont.spaced(g, name, at[0], at[1], size, Gfx.mix(c, 0xFFFFFFFF, 0.35f), true, 1.2f, 0.5f);
            Gfx.setAlpha(old);
        }
    }

    private ComboHud() {}
}
