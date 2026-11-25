package com.beyondsolo.client.shadow;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FastColor;

/**
 * Draws any creature as a shadow: wraps a buffer source so every vertex colour is multiplied by blue-black ink (the
 * series' darks are blue-black, never grey or violet-brown), which keeps the shape and only a cold trace of the
 * texture: the silhouette is everything, and its burning edge is drawn over it ({@code client.fx.Aura}). Glowing
 * layers (a creature's eyes and glow marks, energy) burn in the shadow's own light instead: light showing through the
 * seams of the ink.
 */
public final class ShadowTint implements MultiBufferSource {
    private final MultiBufferSource inner;
    private final float r, g, b;
    private final float alpha;
    private final float lr, lg, lb;

    public ShadowTint(MultiBufferSource inner, float r, float g, float b, float alpha, int light) {
        this.inner = inner;
        this.r = r;
        this.g = g;
        this.b = b;
        this.alpha = alpha;
        this.lr = ((light >> 16) & 0xFF) / 255f;
        this.lg = ((light >> 8) & 0xFF) / 255f;
        this.lb = (light & 0xFF) / 255f;
    }

    /** The usual ink: blue-black (art style {@code #0E1020} on a lit face), its glowing layers in {@code light}. */
    public static ShadowTint ink(MultiBufferSource inner, float brightness, int light) {
        return new ShadowTint(inner, 0.055f * brightness, 0.064f * brightness, 0.13f * brightness, 1f, light);
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        VertexConsumer out = inner.getBuffer(type);
        String name = type.toString();
        if (name.contains("eyes") || name.contains("energy") || name.contains("beyondsolo_light")) {
            return new Tinted(out, lr, lg, lb, 1f);
        }
        return new Tinted(out, r, g, b, alpha);
    }

    private record Tinted(VertexConsumer out, float r, float g, float b, float a) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            out.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int al) {
            out.setColor(Math.round(red * r), Math.round(green * g), Math.round(blue * b), Math.round(al * a));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            out.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            out.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            out.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            out.setNormal(x, y, z);
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
            int tinted = FastColor.ARGB32.color(Math.round(FastColor.ARGB32.alpha(color) * a), Math.round(FastColor.ARGB32.red(color) * r),
                    Math.round(FastColor.ARGB32.green(color) * g), Math.round(FastColor.ARGB32.blue(color) * b));
            out.addVertex(x, y, z, tinted, u, v, overlay, light, nx, ny, nz);
        }
    }
}
