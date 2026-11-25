package com.beyondsolo.client.skill;

import com.beyondsolo.client.ClientConfig;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.skill.Skill;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * The hunter's own ultimate, called across their screen: a dark band slashes in at a slant with the element's light
 * along its edges and streaks racing through it, "ULTIMATE" over the skill's name stamped down in it, and it is gone
 * in a little over a second, above the crosshair. A sovereign's power is named by its own banner instead. Drawn with
 * the {@code combat.screenEffects} option.
 */
public final class SkillCutIn {
    /** Seconds it takes to slide away. */
    private static final float OUT = 0.3f;

    private static double at = -10;
    private static float life = 1.35f;
    private static Component name = Component.empty();
    private static int color = 0xFFFFFFFF;

    /** Calls the skill across the screen; it starts sliding away as the blow comes, {@code release} seconds from now. */
    static void show(Skill skill, float release) {
        at = Anim.now();
        life = Math.max(0.9f, Math.min(1.6f, release + OUT - 0.05f));
        name = skill.displayName();
        color = 0xFF000000 | skill.color();
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        float t = Anim.since(at);
        if (t < 0 || t >= life || !ClientConfig.SCREEN_EFFECTS.get()) return;
        int w = g.guiWidth(), h = g.guiHeight();
        float in = Anim.outCubic(Anim.window(t, 0, 0.16f));
        float out = Anim.window(t, life - OUT, life);
        float slide = -(1 - in) * w * 0.7f + Anim.inCubic(out) * w * 0.7f;
        float cy = h * 0.3f;
        float bh = 36;
        float half = w * 0.75f;
        int light = Gfx.mix(color, 0xFFFFFFFF, 0.55f);

        g.pose().pushPose();
        g.pose().translate(w / 2f + slide, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(-4f));
        float old = Gfx.fade(1 - out * 0.5f);

        // the band, dark at its heart and fading out at both ends
        Shapes.box(-half, -bh / 2f, half, bh).fillH(0x00000000, Gfx.alpha(0xFF040208, 0.86f)).draw(g);
        Shapes.box(0, -bh / 2f, half, bh).fillH(Gfx.alpha(0xFF040208, 0.86f), 0x00000000).draw(g);
        // the element's light along its edges
        for (float ly : new float[]{-bh / 2f - 1, bh / 2f}) {
            Shapes.box(-half, ly, half, 1.5f).fillH(0x00000000, Gfx.alpha(light, 0.95f)).glow(4, Gfx.alpha(color, 0.6f)).draw(g);
            Shapes.box(0, ly, half, 1.5f).fillH(Gfx.alpha(light, 0.95f), 0x00000000).glow(4, Gfx.alpha(color, 0.6f)).draw(g);
        }
        // streaks racing through it
        for (int i = 0; i < 12; i++) {
            float seed = (i * 0.61803f) % 1f;
            float y = -bh / 2f + 3 + seed * (bh - 6);
            float len = 30 + 70 * ((i * 0.7548f) % 1f);
            float x = ((t * (900 + 500 * seed) + seed * 1700) % (half * 2 + len)) - half - len;
            Shapes.box(x, y, len, 0.8f).fillH(0x00FFFFFF, Gfx.alpha(i % 3 == 0 ? light : 0xFFFFFFFF, 0.35f)).draw(g);
        }
        // "ULTIMATE", then the name stamped down under it
        String head = Component.translatable("ui.beyondsolo.skill.ultimate").getString().toUpperCase(Locale.ROOT);
        SysFont.spaced(g, head, 0, -bh / 2f + 4, 5.5f, Gfx.alpha(light, 0.95f), true, 3f, 0.5f);
        float stamp = Anim.window(t, 0.05f, 0.26f);
        float scale = 1 + 0.7f * (1 - Anim.outCubic(stamp));
        String title = name.getString().toUpperCase(Locale.ROOT);
        float unit = SysFont.spacedWidth(title, 10, true, 0) / 10f;
        float track = 2.2f * Math.max(0, title.codePointCount(0, title.length()) - 1);
        float size = Math.max(8, Math.min(17, (w * 0.8f - track) / Math.max(0.01f, unit)));
        g.pose().pushPose();
        g.pose().translate(0, 4, 0);
        g.pose().scale(scale, scale, 1);
        float oldS = Gfx.fade(Math.min(1, stamp * 2.5f));
        // a coloured shadow knocked off behind the name, then the name
        SysFont.spaced(g, title, 1.2f, -size * 0.5f + 1.2f, size, Gfx.alpha(color, 0.85f), true, 2.2f, 0.5f);
        SysFont.spaced(g, title, 0, -size * 0.5f, size, 0xFFFFFFFF, true, 2.2f, 0.5f);
        Gfx.setAlpha(oldS);
        g.pose().popPose();

        Gfx.setAlpha(old);
        g.pose().popPose();
    }

    private SkillCutIn() {}
}
