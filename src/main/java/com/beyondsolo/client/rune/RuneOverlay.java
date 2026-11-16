package com.beyondsolo.client.rune;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.network.RuneFxPayload;
import com.beyondsolo.rune.Runes;
import com.beyondsolo.skill.Skill;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.Random;

/**
 * The absorb on the local player's screen: while the stone is held a ring fills around the crosshair with the
 * stone's glyph inside (not in the front view, where the crosshair sits on the player's chest); in first person
 * light also drifts in from the edges to the stone in the hands, and when it shatters the screen flashes in its colour
 * and streams of light pour down into the player before the System names what was learned ({@code SkillOverlay}
 * waits for it). In third person the shatter is all in the world ({@link RuneWorldFx}). A refused stone puffs grey.
 */
public final class RuneOverlay {
    /** The glyph icons (distance fields, made by tools/gen_rune_assets.py). */
    static final ResourceLocation[] GLYPHS = new ResourceLocation[Runes.GLYPHS];

    static {
        for (int i = 0; i < GLYPHS.length; i++) GLYPHS[i] = BeyondSolo.id("textures/gui/icon/rune_glyph_" + i + ".png");
    }

    /** How far down the screen the stone is held in first person (see {@code RuneClientEvents.HAND}). */
    private static final float STONE_Y = 0.725f;

    private static double shatterAt = -100;
    private static int shatterColor;
    private static int shatterGlyph;
    private static long shatterSeed;
    private static double engravedAt = -100;
    private static int engravedColor;
    private static double fizzleAt = -100;
    private static int fizzleColor;
    /** The crack flashes of the stone being held: how many have played, and when the last did. */
    private static int cracks;
    private static double crackAt = -100;

    static void shatter(RuneFxPayload p) {
        shatterAt = Anim.now();
        shatterColor = Gfx.opaque(p.color());
        shatterGlyph = Mth.clamp(p.extra() & 0xF, 0, GLYPHS.length - 1);
        shatterSeed = new Random().nextLong();
    }

    static void engraved(RuneFxPayload p) {
        engravedAt = Anim.now();
        engravedColor = Gfx.opaque(p.color());
    }

    static void fizzle(int color) {
        fizzleAt = Anim.now();
        fizzleColor = Gfx.opaque(color);
    }

    static void reset() {
        shatterAt = engravedAt = fizzleAt = crackAt = -100;
        cracks = 0;
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        float w = g.guiWidth(), h = g.guiHeight();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        boolean facing = mc.options.getCameraType().isMirrored();

        if (mc.player.isUsingItem() && Runes.isRune(mc.player.getUseItem())) {
            if (!facing) channel(g, mc.player.getUseItem(), (mc.player.getTicksUsingItem() + delta.getGameTimeDeltaPartialTick(false)) / Runes.USE_TICKS, w, h, firstPerson);
        } else {
            cracks = 0;
        }
        float s = Anim.since(shatterAt);
        if (s < 1.6f && firstPerson) shattered(g, s, w, h);
        float e = Anim.since(engravedAt);
        if (e < 1.2f) engravedFlash(g, e, w, h);
        float f = Anim.since(fizzleAt);
        if (f < 0.5f) fizzled(g, f, w, h);
    }

    /** Holding the stone: a ring that fills, the glyph, light drifting in, cracks. */
    private static void channel(GuiGraphics g, ItemStack stack, float progress, float w, float h, boolean firstPerson) {
        progress = Mth.clamp(progress, 0, 1);
        int c = Gfx.opaque(Runes.colorOf(stack));
        int light = Gfx.mix(c, 0xFFFFFFFF, 0.5f);
        float cx = w / 2f, cy = h / 2f;
        float in = Anim.outCubic(Anim.clamp01(progress * 4));

        // the edges of the view take the stone's colour
        Shapes.vignette(0, 0, w, h, 0.35f, 1.25f).fill(Gfx.alpha(c, 0.08f + 0.22f * progress)).draw(g);

        // light drifting in from the edges towards the stone in the hands
        float sx = cx, sy = h * STONE_Y;
        if (firstPerson) {
            Random r = new Random(7);
            float time = (float) Anim.now();
            for (int i = 0; i < 16; i++) {
                float ph = (time * (0.45f + r.nextFloat() * 0.25f) + r.nextFloat()) % 1f;
                float ang = r.nextFloat() * Mth.TWO_PI;
                float fx = cx + Mth.cos(ang) * w * 0.6f, fy = cy + Mth.sin(ang) * h * 0.6f;
                float k = ph * ph;
                float x = Mth.lerp(k, fx, sx), y = Mth.lerp(k, fy, sy);
                float a = Mth.sin(ph * Mth.PI) * (0.25f + 0.65f * progress);
                Shapes.circle(x, y, 1.1f + 1.2f * ph).fill(Gfx.alpha(light, a)).glow(4, Gfx.alpha(c, a * 0.8f)).additive().draw(g);
            }
            // the stone's own glow in the hands
            float pulse = 0.85f + 0.15f * Anim.pulse(0.6f);
            Shapes.circle(sx, sy, 2).fill(0).glow(34 + 40 * progress, Gfx.alpha(c, (0.12f + 0.3f * progress) * pulse)).additive().draw(g);
        }

        // the ring around the crosshair, filling
        float r = 15 - 3 * progress;
        float old = Gfx.fade(in);
        Shapes.ring(cx, cy, r, 1.1f).fill(Gfx.alpha(c, 0.25f)).draw(g);
        Shapes.ring(cx, cy, r, 1.6f).fill(light).glow(4, Gfx.alpha(c, 0.8f)).progress(progress).rotation(-0.25f).draw(g);
        Shapes.ring(cx, cy, r + 4.5f, 0.6f).fill(Gfx.alpha(c, 0.45f)).dashes(12, 0.4f).rotation((float) Anim.now() * 0.08f).draw(g);
        int glyph = Runes.glyphOf(stack);
        float gs = 9 + 2 * progress + 0.6f * Mth.sin((float) Anim.now() * 40) * progress * progress;
        Shapes.icon(GLYPHS[glyph], cx, cy, gs).fill(Gfx.alpha(light, 0.5f + 0.5f * progress)).glow(2 + 3 * progress, Gfx.alpha(c, 0.9f)).draw(g);
        // the stone's name under the ring
        Skill s = Runes.skillOf(stack);
        String name = s != null ? s.displayName().getString()
                : Component.translatable("rune.beyondsolo.overlay.sealed", Runes.gradeOf(stack).name()).getString();
        SysFont.spaced(g, name.toUpperCase(java.util.Locale.ROOT), cx, cy + r + 9, 5.6f, Gfx.alpha(light, 0.9f), true, 1.4f, 0.5f);
        SysFont.spaced(g, Component.translatable("rune.beyondsolo.overlay.absorbing").getString(), cx, cy - r - 13, 4.8f,
                Gfx.alpha(SystemTheme.TEXT_SOFT, 0.75f), true, 1.8f, 0.5f);
        Gfx.setAlpha(old);

        // the stone cracks at each third: a flash and a sharp ring
        int now = progress >= 2 / 3f ? 2 : progress >= 1 / 3f ? 1 : 0;
        if (now > cracks) {
            cracks = now;
            crackAt = Anim.now();
        }
        float ck = Anim.since(crackAt) / 0.35f;
        if (ck < 1) {
            Shapes.ring(cx, cy, r + 2 + 16 * Anim.outCubic(ck), 1.4f * (1 - ck) + 0.2f).fill(Gfx.alpha(0xFFFFFFFF, 1 - ck)).glow(4, Gfx.alpha(c, 0.7f * (1 - ck))).draw(g);
            if (firstPerson) Shapes.circle(sx, sy, 2).fill(0).glow(60, Gfx.alpha(0xFFFFFFFF, 0.3f * (1 - ck))).additive().draw(g);
        }
    }

    /** The stone shattered: a flash, then the light pours down into the player. */
    private static void shattered(GuiGraphics g, float t, float w, float h) {
        int c = shatterColor;
        int light = Gfx.mix(c, 0xFFFFFFFF, 0.55f);
        float cx = w / 2f, cy = h / 2f;
        float flash = 1 - Anim.clamp01(t / 0.3f);
        float tail = 1 - Anim.clamp01((t - 0.5f) / 1.1f);
        Shapes.box(0, 0, w, h).fill(Gfx.alpha(Gfx.mix(c, 0xFFFFFFFF, 0.6f), 0.4f * flash * flash)).draw(g);
        Shapes.vignette(0, 0, w, h, 0.15f, 1.2f).fill(Gfx.alpha(c, 0.45f * tail)).draw(g);

        // the burst where the stone was
        float sx = cx, sy = h * STONE_Y;
        float b = Anim.outCubic(Anim.clamp01(t / 0.45f));
        if (b < 1) {
            Shapes.rays(sx, sy, 60 + 140 * b, 14).fillV(Gfx.alpha(light, 0.5f * (1 - b)), Gfx.alpha(c, 0.15f * (1 - b))).rotation((float) (shatterSeed % 100) / 100f + t * 0.05f).additive().draw(g);
            Shapes.ring(sx, sy, 10 + 110 * b, 2.5f * (1 - b) + 0.3f).fill(Gfx.alpha(light, 1 - b)).glow(6, Gfx.alpha(c, 0.6f * (1 - b))).draw(g);
        }
        // the glyph hangs in the air for a moment, then sinks into the player
        float gk = Anim.clamp01(t / 1.0f);
        float gy = cy - 6 + 40 * gk * gk;
        float ga = Anim.clamp01(t / 0.12f) * (1 - Anim.clamp01((t - 0.55f) / 0.45f));
        if (ga > 0) {
            float size = 22 + 10 * Anim.outBack(Anim.clamp01(t / 0.35f)) - 8 * gk;
            Shapes.circle(cx, gy, 2).fill(0).glow(size * 1.8f, Gfx.alpha(c, 0.35f * ga)).additive().draw(g);
            Shapes.icon(GLYPHS[shatterGlyph], cx, gy, size).fill(Gfx.alpha(light, ga)).glow(5, Gfx.alpha(c, ga)).draw(g);
        }
        // streams of light pouring from all around down into the player (who is just below the view)
        Random r = new Random(shatterSeed);
        float tx = cx, ty = h + 30;
        for (int i = 0; i < 22; i++) {
            float ang = r.nextFloat() * Mth.TWO_PI;
            float fx = cx + Mth.cos(ang) * w * 0.62f, fy = cy + Mth.sin(ang) * h * 0.62f;
            float start = 0.1f + r.nextFloat() * 0.45f;
            float k = Anim.clamp01((t - start) / 0.55f);
            if (k <= 0 || k >= 1) continue;
            float kk = k * k;
            float hx = Mth.lerp(kk, fx, tx), hy = Mth.lerp(kk, fy, ty);
            float bk = Math.max(0, kk - 0.28f);
            float bx = Mth.lerp(bk, fx, tx), by = Mth.lerp(bk, fy, ty);
            streak(g, bx, by, hx, hy, 2.2f, i % 3 == 0 ? light : c, (0.35f + 0.65f * k) * (1 - k * 0.3f));
        }
        // a warm glow rising from below as the light settles
        float settle = Anim.clamp01((t - 0.55f) / 0.3f) * (1 - Anim.clamp01((t - 0.9f) / 0.7f));
        if (settle > 0) Shapes.circle(cx, h + 20, 2).fill(0).glow(h * 0.75f, Gfx.alpha(c, 0.4f * settle)).additive().draw(g);
    }

    private static void engravedFlash(GuiGraphics g, float t, float w, float h) {
        int c = engravedColor;
        int light = Gfx.mix(c, 0xFFFFFFFF, 0.5f);
        float k = Anim.clamp01(t / 1.2f);
        Shapes.vignette(0, 0, w, h, 0.25f, 1.2f).fill(Gfx.alpha(c, 0.35f * (1 - k))).draw(g);
        float cx = w / 2f, cy = h / 2f;
        float ring = Anim.outCubic(Anim.clamp01(t / 0.6f));
        if (ring < 1) Shapes.ring(cx, cy, 120 - 100 * ring, 1.6f).fill(Gfx.alpha(light, 1 - ring)).glow(5, Gfx.alpha(c, 0.7f * (1 - ring))).draw(g);
    }

    private static void fizzled(GuiGraphics g, float t, float w, float h) {
        float k = t / 0.5f;
        float cx = w / 2f, cy = h / 2f;
        int grey = Gfx.mix(fizzleColor, 0xFF6A7080, 0.7f);
        Shapes.ring(cx, cy, 14 - 8 * Anim.outCubic(k), 1.2f).fill(Gfx.alpha(grey, 1 - k)).dashes(10, 0.5f).rotation(k * 0.2f).draw(g);
    }

    /** A line of light on the screen from (x1, y1) to (x2, y2), bright at the head. */
    private static void streak(GuiGraphics g, float x1, float y1, float x2, float y2, float width, int color, float alpha) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 0.5f || alpha <= 0.01f) return;
        g.pose().pushPose();
        g.pose().translate((x1 + x2) / 2f, (y1 + y2) / 2f, 0);
        g.pose().mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
        Shapes.box(-len / 2f, -width / 2f, len, width).radius(width / 2f).fillH(Gfx.alpha(color, 0), Gfx.alpha(color, alpha))
                .glow(3, Gfx.alpha(color, alpha * 0.5f)).additive().draw(g);
        g.pose().popPose();
    }

    private RuneOverlay() {}
}
