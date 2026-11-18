package com.beyondsolo.client.gfx;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
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
 *
 * <p>Inside a {@link #batch} shapes (and {@link SysFont} text) that follow each other are drawn together in one draw
 * call; outside one each shape is drawn at once, as it always was. The System window frame, the storage window, the
 * System pages that say {@code batched()} and the mod's HUD layers draw inside one.</p>
 */
public final class Shapes {
    private static final int MODE_BOX = 0;
    private static final int MODE_RING = 1;
    private static final int MODE_ICON = 2;
    private static final int MODE_VIGNETTE = 3;
    private static final int MODE_RAYS = 4;

    private static final Shape SHAPE = new Shape();
    private static long drawn;
    private static long batches;
    /** Open batches; shapes are only collected while this is above zero. */
    private static int batchDepth;
    /** The buffer the shapes being collected go into, or null when no run of shapes is open. */
    private static VertexConsumer run;

    /**
     * Collects the shapes and {@link SysFont} text drawn until the returned batch is closed, so the ones that follow
     * each other cost one draw call together instead of one each. Items, vanilla text and anything else drawn through
     * {@code g} in between keep their order: what was collected so far is drawn before them. Use it as
     * {@code try (var b = Shapes.batch(g)) { ... }}.
     *
     * <p>What is collected is drawn later, with the clip and shader colour of that moment. So inside a batch call
     * {@link #flush} before anything that changes them or draws straight away: {@code g.enableScissor} /
     * {@code g.disableScissor}, {@code RenderSystem.setShaderColor} / {@code g.setColor}, {@code g.blit},
     * {@code InventoryScreen.renderEntityInInventory...} (they clip). Without it the shapes so far get clipped,
     * tinted or drawn over the blit. Batches nest; the outermost one draws what is left when it closes.</p>
     */
    public static Batch batch(GuiGraphics g) {
        begin();
        return new Batch(g);
    }

    /** True inside a {@link #batch}: shapes and System text are being collected. */
    public static boolean batching() {
        return batchDepth > 0;
    }

    /**
     * Draws what a {@link #batch} collected so far. Call it inside a batch before clipping, changing the shader
     * colour, blitting or drawing a mob (see {@link #batch}). Does nothing outside a batch.
     */
    public static void flush(GuiGraphics g) {
        if (batchDepth > 0) finishRun(g);
    }

    /** Opens a batch that {@link #end} closes (for code that cannot use try-with-resources). */
    public static void begin() {
        batchDepth++;
    }

    /** Closes a batch opened with {@link #begin}. */
    public static void end(GuiGraphics g) {
        if (batchDepth <= 0) return;
        if (--batchDepth == 0) finishRun(g);
    }

    private static void finishRun(GuiGraphics g) {
        run = null;
        g.flush();
    }

    public static final class Batch implements AutoCloseable {
        private final GuiGraphics g;
        private boolean closed;

        private Batch(GuiGraphics g) {
            this.g = g;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            end(g);
        }
    }

    /** Shapes drawn since the last call (for the UI test driver's frame timer). */
    public static long drawn() {
        long n = drawn;
        drawn = 0;
        return n;
    }

    /** Draw calls the shapes took since the last call (for the UI test driver's frame timer). */
    public static long batches() {
        long n = batches;
        batches = 0;
        return n;
    }

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
            if (ModShaders.shape() == null) {
                if (mode == MODE_BOX) {
                    g.fill(Math.round(cx - hw), Math.round(cy - hh), Math.round(cx + hw), Math.round(cy + hh), Gfx.fade(fillA, alpha));
                }
                return;
            }
            RenderType type = ShapeFormat.type(mode == MODE_ICON ? texture : null, additive);
            MultiBufferSource.BufferSource source = g.bufferSource();
            VertexConsumer out = source.getBuffer(type);
            if (out != run) {
                // A new run of shapes: whatever was drawn before it (models, items) goes first, as before a lone shape.
                g.flush();
                out = source.getBuffer(type);
                run = out;
                batches++;
            }
            if (!(out instanceof BufferBuilder buffer)) {
                // someone wrapped the buffers: nothing to write the shape's settings into
                run = null;
                return;
            }

            float pad = mode == MODE_BOX || mode == MODE_RING ? glowSize + 1.5f : 0;
            float extent = mode == MODE_RING ? thickness / 2f : 0;
            float qx = hw + extent + pad;
            float qy = hh + extent + pad;
            float rectZ = mode == MODE_ICON ? 2f * Icons.SPREAD * (2f * hw) / Icons.TEXTURE_SIZE : radius;
            float code = mode + 8 * gradient;
            float border = mode == MODE_RING ? thickness : borderWidth;
            int vertexColor = Gfx.alpha(0xFFFFFF, alpha);
            Matrix4f m = g.pose().last().pose();
            corner(buffer, m, -qx, -qy, vertexColor, rectZ, code, border);
            corner(buffer, m, -qx, qy, vertexColor, rectZ, code, border);
            corner(buffer, m, qx, qy, vertexColor, rectZ, code, border);
            corner(buffer, m, qx, -qy, vertexColor, rectZ, code, border);
            drawn++;

            if (batchDepth == 0) {
                // not batching: draw it now, as it always was
                source.endBatch(type);
                run = null;
            }
        }

        private void corner(BufferBuilder out, Matrix4f m, float u, float v, int vertexColor, float rectZ, float code, float border) {
            out.addVertex(m, cx + u, cy + v, 0).setUv(u, v).setColor(vertexColor);
            ShapeFormat.put(out, ShapeFormat.RECT, hw, hh, rectZ, code);
            ShapeFormat.put(out, ShapeFormat.PARAMS, border, glowSize, sheenPos, sheenStrength);
            ShapeFormat.put(out, ShapeFormat.EXTRA, progress, rotation, dashCount, dashFill);
            ShapeFormat.putColor(out, ShapeFormat.FILL_A, fillA);
            ShapeFormat.putColor(out, ShapeFormat.FILL_B, fillB);
            ShapeFormat.putColor(out, ShapeFormat.BORDER_A, borderA);
            ShapeFormat.putColor(out, ShapeFormat.BORDER_B, borderB);
            ShapeFormat.putColor(out, ShapeFormat.GLOW, glowColor);
        }
    }

    private Shapes() {}
}
