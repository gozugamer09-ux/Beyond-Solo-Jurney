package com.beyondsolo.client.shadow;

import com.beyondsolo.BeyondSolo;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * The energy that flows over a promoted shadow (a knight and higher): wraps a buffer source so a creature's model,
 * drawn again a little larger, comes out as wisps of its rank's light scrolling over it (the charged creeper's swirl,
 * with the shadow's own picture, {@code tools/gen_shadow_assets.py}). Parts drawn in any other vertex format (lines,
 * plain colour) are left out of the shell. The light follows the creature's skin: a model painted on a finer skin
 * (our creatures' 512-pixel ones) spreads each face over less of it, so the veins are drawn finer there
 * ({@link #detail}), and look the same size on every creature.
 */
final class ShadowShell implements MultiBufferSource {
    static final ResourceLocation TEX = BeyondSolo.id("textures/entity/shadow/shell.png");
    /** Skin width -> how much finer the veins are drawn on it. */
    private static final Map<ResourceLocation, Float> DETAIL = new HashMap<>();

    private final MultiBufferSource inner;
    private final RenderType type;
    private final float r, g, b;
    private final float detail;

    /**
     * @param time     ticks, for the scroll
     * @param speed    how fast the wisps flow (1 = the creeper's pace)
     * @param rgb      the light's colour
     * @param strength how bright (0..1)
     */
    ShadowShell(MultiBufferSource inner, float time, float speed, int rgb, float strength, float detail) {
        this.inner = inner;
        this.detail = detail;
        this.type = RenderType.energySwirl(TEX, (time * 0.01f * speed) % 1f, (time * 0.0065f * speed) % 1f);
        this.r = ((rgb >> 16) & 0xFF) / 255f * strength;
        this.g = ((rgb >> 8) & 0xFF) / 255f * strength;
        this.b = (rgb & 0xFF) / 255f * strength;
    }

    @Override
    public VertexConsumer getBuffer(RenderType t) {
        if (t.format() != DefaultVertexFormat.NEW_ENTITY) return NOTHING;
        return new Shelled(inner.getBuffer(type), r, g, b, detail);
    }

    /**
     * How much finer than on a vanilla 64-pixel skin the veins are drawn on this skin: 1 up to 128 pixels wide, then
     * in step with its width, at most 4. Read once from the picture's own header.
     */
    static float detail(ResourceLocation skin) {
        Float known = DETAIL.get(skin);
        if (known != null) return known;
        float k = 1f;
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(skin);
            if (res.isPresent()) {
                try (InputStream in = res.get().open()) {
                    byte[] head = in.readNBytes(24);
                    // a PNG: the width is the first number of the IHDR chunk
                    if (head.length == 24 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
                        int width = ((head[16] & 0xFF) << 24) | ((head[17] & 0xFF) << 16) | ((head[18] & 0xFF) << 8) | (head[19] & 0xFF);
                        k = Math.max(1f, Math.min(4f, width / 128f));
                    }
                }
            }
        } catch (Exception ignored) {
            // an odd skin: plain veins
        }
        DETAIL.put(skin, k);
        return k;
    }

    /** The shell's vertices: the rank's light for a colour, the skin's place scaled to the vein size. */
    private record Shelled(VertexConsumer out, float r, float g, float b, float k) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            out.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            out.setColor(Math.round(red * r), Math.round(green * g), Math.round(blue * b), alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            out.setUv(u * k, v * k);
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
            int tinted = FastColor.ARGB32.color(FastColor.ARGB32.alpha(color), Math.round(FastColor.ARGB32.red(color) * r),
                    Math.round(FastColor.ARGB32.green(color) * g), Math.round(FastColor.ARGB32.blue(color) * b));
            out.addVertex(x, y, z, tinted, u * k, v * k, overlay, light, nx, ny, nz);
        }
    }

    /** Takes vertices and draws nothing. */
    private static final VertexConsumer NOTHING = new VertexConsumer() {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    };
}
