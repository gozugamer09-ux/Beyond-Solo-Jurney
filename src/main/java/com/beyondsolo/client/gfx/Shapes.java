package com.beyondsolo.client.gfx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Smooth vector shapes for the System UI: rounded boxes, pills, circles, rings/arcs and icons,
 * each with optional gradient, border, glow and sheen. Usage:
 *
 * <pre>Shapes.box(x, y, w, h).radius(8).fillV(top, bottom).border(1, color).glow(10, glowColor).draw(g);</pre>
 *
 * Coordinates are GUI pixels and follow the current {@code g.pose()} transform.
 * Colours are ARGB. Everything is multiplied by {@link Gfx#alpha()}.
 */
public final class Shapes {
    private static final int MODE_BOX = 0;
    private static final int MODE_RING = 1;
    private static final int MODE_ICON = 2;
    private static final int MODE_VIGNETTE = 3;
    private static final int MODE_RAYS = 4;

    private static final Shape SHAPE = new Shape();

    public static Shape box(float x, float y, float w, float h) {
        return SHAPE.begin(MODE_BOX, x + w / 2f, y + h / 2f, w / 2f, h / 2f);
    }

    /** A box whose short side is fully rounded. */
    public static Shape pill(float x, float y, float w, float h) {
        return box(x, y, w, h).radius(Math.min(w, h) / 2f);
    }

    public static Shape circle(float cx, float cy, float radius) {
        return SHAPE.begin(MODE_BOX, cx, cy, radius, radius).radius(radius);
    }

    /** A circle outline. {@code radius} is measured to the middle of the line. */
    public static Shape ring(float cx, float cy, float radius, float thickness) {
        Shape s = SHAPE.begin(MODE_RING, cx, cy, radius, radius);
        s.thickness = thickness;
        return s;
    }

    /** An icon from {@link Icons}, centred on (cx, cy). {@code size} is the icon's nominal box. */
    public static Shape icon(ResourceLocation texture, float cx, float cy, float size) {
        float half = size * Icons.TEXTURE_SIZE / Icons.CONTENT_SIZE / 2f;
        Shape s = SHAPE.begin(MODE_ICON, cx, cy, half, half);
        s.texture = texture;
        return s;
    }

    /**
     * Tints the edges of a rectangle and leaves the middle clear. {@code inner} and {@code outer} are where the
     * tint starts and reaches full strength, as a fraction of the half size (1 = the middle of each edge).
     */
    public static Shape vignette(float x, float y, float w, float h, float inner, float outer) {
        Shape s = SHAPE.begin(MODE_VIGNETTE, x + w / 2f, y + h / 2f, w / 2f, h / 2f);
        s.progress = inner;
        s.rotation = outer;
        return s;
    }

    /** Rays of light fanning out from (cx, cy). Use {@link Shape#rotation} to turn them and {@link Shape#additive}. */
    public static Shape rays(float cx, float cy, float radius, int count) {
        Shape s = SHAPE.begin(MODE_RAYS, cx, cy, radius, radius);
        s.dashCount = count;
        return s;
    }

    public static final class Shape {
        private int mode;
        private float cx, cy, hw, hh;
        private float radius;
        private float thickness;
        private ResourceLocation texture;
        private int fillA, fillB;
        private int gradient; // 0 none, 1 vertical, 2 horizontal, 3 around the ring
        private float borderWidth;
        private int borderA, borderB;
        private float glowSize;
        private int glowColor;
        private float sheenPos, sheenStrength;
        private float progress, rotation, dashCount, dashFill;
        private boolean additive;

        private Shape begin(int mode, float cx, float cy, float hw, float hh) {
            this.mode = mode;
            this.cx = cx;
            this.cy = cy;
            this.hw = hw;
            this.hh = hh;
            radius = 0;
            thickness = 0;
            texture = null;
            fillA = fillB = 0xFFFFFFFF;
            gradient = 0;
            borderWidth = 0;
            borderA = borderB = 0;
            glowSize = 0;
            glowColor = 0;
            sheenPos = -1;
            sheenStrength = 0;
            progress = 1;
            rotation = 0;
            dashCount = 0;
            dashFill = 1;
            additive = false;
            return this;
        }

        public Shape radius(float r) {
            radius = r;
            return this;
        }

        public Shape fill(int argb) {
            fillA = fillB = argb;
            gradient = 0;
            return this;
        }

        /** Top-to-bottom gradient. */
        public Shape fillV(int top, int bottom) {
            fillA = top;
            fillB = bottom;
            gradient = 1;
            return this;
        }

        /** Left-to-right gradient. */
        public Shape fillH(int left, int right) {
            fillA = left;
            fillB = right;
            gradient = 2;
            return this;
        }

        /** Ring only: gradient from the start of the arc to its end. */
        public Shape fillAround(int start, int end) {
            fillA = start;
            fillB = end;
            gradient = 3;
            return this;
        }

        public Shape border(float width, int argb) {
            borderWidth = width;
            borderA = borderB = argb;
            return this;
        }

        public Shape border(float width, int top, int bottom) {
            borderWidth = width;
            borderA = top;
            borderB = bottom;
            return this;
        }

        public Shape glow(float size, int argb) {
            glowSize = size;
            glowColor = argb;
            return this;
        }

        /** A soft diagonal highlight at {@code position} 0..1 across the shape. Animate the position for a shine sweep. */
        public Shape sheen(float position, float strength) {
            sheenPos = position;
            sheenStrength = strength;
            return this;
        }

        /** Ring only: draw just this fraction of the circle, clockwise from the top (plus {@link #rotation}). */
        public Shape progress(float fraction) {
            progress = Mth.clamp(fraction, 0f, 1f);
            return this;
        }

        /** Ring only: where the arc and the dashes start, in turns (1 = full circle). */
        public Shape rotation(float turns) {
            rotation = turns;
            return this;
        }

        /** Ring only: split the ring into {@code count} dashes, each covering {@code fill} of its slot. */
        public Shape dashes(int count, float fill) {
            dashCount = count;
            dashFill = fill;
            return this;
        }

        /** Add light instead of covering what is behind. Good for flares and energy effects. */
        public Shape additive() {
            additive = true;
            return this;
        }

        public void draw(GuiGraphics g) {
            float alpha = Gfx.alpha();
            if (alpha <= 0.004f || hw <= 0 || hh <= 0) return;
            ShaderInstance shader = ModShaders.shape();
            if (shader == null) {
                if (mode == MODE_BOX) {
                    g.fill(Math.round(cx - hw), Math.round(cy - hh), Math.round(cx + hw), Math.round(cy + hh), Gfx.fade(fillA, alpha));
                }
                return;
            }
            g.flush();

            float pad = mode == MODE_BOX || mode == MODE_RING ? glowSize + 1.5f : 0;
            float extent = mode == MODE_RING ? thickness / 2f : 0;
            float qx = hw + extent + pad;
            float qy = hh + extent + pad;

            float rectZ = switch (mode) {
                case MODE_ICON -> 2f * Icons.SPREAD * (2f * hw) / Icons.TEXTURE_SIZE;
                default -> radius;
            };
            set(shader, "Rect", hw, hh, rectZ, mode);
            setColor(shader, "FillA", fillA);
            setColor(shader, "FillB", fillB);
            switch (gradient) {
                case 1 -> set(shader, "Grad", 0, 1f / (2f * (hh + extent)), 0, 0);
                case 2 -> set(shader, "Grad", 1f / (2f * (hw + extent)), 0, 0, 0);
                case 3 -> set(shader, "Grad", 0, 0, 1, 0);
                default -> set(shader, "Grad", 0, 0, 0, 0);
            }
            setColor(shader, "BorderA", borderA);
            setColor(shader, "BorderB", borderB);
            setColor(shader, "GlowColor", glowColor);
            set(shader, "Params", mode == MODE_RING ? thickness : borderWidth, glowSize, sheenPos, sheenStrength);
            set(shader, "Extra", progress, rotation, dashCount, dashFill);

            RenderSystem.setShader(() -> shader);
            if (mode == MODE_ICON && texture != null) RenderSystem.setShaderTexture(0, texture);
            RenderSystem.enableBlend();
            if (additive) {
                RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                        GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            } else {
                RenderSystem.defaultBlendFunc();
            }
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();

            Matrix4f m = g.pose().last().pose();
            int vertexColor = Gfx.alpha(0xFFFFFF, alpha);
            BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            bb.addVertex(m, cx - qx, cy - qy, 0).setUv(-qx, -qy).setColor(vertexColor);
            bb.addVertex(m, cx - qx, cy + qy, 0).setUv(-qx, qy).setColor(vertexColor);
            bb.addVertex(m, cx + qx, cy + qy, 0).setUv(qx, qy).setColor(vertexColor);
            bb.addVertex(m, cx + qx, cy - qy, 0).setUv(qx, -qy).setColor(vertexColor);
            BufferUploader.drawWithShader(bb.buildOrThrow());

            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
        }

        private static void set(ShaderInstance shader, String name, float a, float b, float c, float d) {
            AbstractUniform u = shader.safeGetUniform(name);
            u.set(a, b, c, d);
        }

        private static void setColor(ShaderInstance shader, String name, int argb) {
            set(shader, name, ((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f, (argb >>> 24) / 255f);
        }
    }

    private Shapes() {}
}
