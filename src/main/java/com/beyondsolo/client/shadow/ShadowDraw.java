package com.beyondsolo.client.shadow;

import com.beyondsolo.shadow.ShadowLook;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the creature a shadow wears with that creature's own renderer, which may be any mod's, so that whatever goes
 * wrong in it stays inside the shadow: it draws on its own copy of the pose (a renderer that throws between a push and
 * a pop leaves the world's pose alone), and every vertex it writes is watched, so one it leaves half written (or a
 * quad it leaves short) is finished before anything else draws from the same buffer, which would otherwise fail the
 * frame. A kind that fails is told to {@link ShadowLook#broke} once, and drawn as a stand-in from then on.
 */
public final class ShadowDraw {
    private ShadowDraw() {}

    /** The renderer that draws this creature, or null if it has none. */
    @SuppressWarnings("unchecked")
    public static @Nullable EntityRenderer<LivingEntity> renderer(LivingEntity body) {
        try {
            return (EntityRenderer<LivingEntity>) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(body);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Draws {@code body} with {@code r} into {@code buffers} (any wrapping: ink, shell, outline) at the current pose.
     * False if it failed; its kind is then drawn as a stand-in from the next frame on.
     */
    public static boolean render(@Nullable EntityRenderer<LivingEntity> r, LivingEntity body, float yaw, float partial, PoseStack pose,
                                 MultiBufferSource buffers, int light) {
        if (r == null) {
            ShadowLook.broke(body.getType(), new IllegalStateException("no renderer for " + body.getType()));
            return false;
        }
        PoseStack own = new PoseStack();
        own.last().pose().set(pose.last().pose());
        own.last().normal().set(pose.last().normal());
        Watch watch = new Watch(buffers);
        try {
            r.render(body, yaw, partial, own, watch, light);
            return true;
        } catch (RuntimeException | LinkageError e) {
            watch.seal();
            ShadowLook.broke(body.getType(), e);
            return false;
        }
    }

    /** Runs a creature's own tick with its particles held back (a shadow is ink, not the creature). */
    public static void quietly(Runnable tick) {
        var particles = Minecraft.getInstance().options.particles();
        ParticleStatus was = particles.get();
        try {
            particles.set(ParticleStatus.MINIMAL);
            tick.run();
        } finally {
            particles.set(was);
        }
    }

    /** Hands out buffers that watch what is written to them. */
    private static final class Watch implements MultiBufferSource {
        private final MultiBufferSource inner;
        private final List<Watched> handed = new ArrayList<>(4);

        Watch(MultiBufferSource inner) {
            this.inner = inner;
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            Watched w = new Watched(inner.getBuffer(type), type.mode());
            handed.add(w);
            return w;
        }

        /** Finishes whatever was left half written. */
        void seal() {
            for (Watched w : handed) {
                try {
                    w.seal();
                } catch (RuntimeException ignored) {
                    // that buffer was drawn and closed already: nothing left to finish
                }
            }
        }
    }

    /** A buffer that counts the vertices written to it and which parts of the last one were given. */
    private static final class Watched implements VertexConsumer {
        private static final int COLOR = 1, UV = 2, OVERLAY = 4, LIGHT = 8, NORMAL = 16, ALL = 31;
        private final VertexConsumer out;
        private final VertexFormat.Mode mode;
        private int vertices;
        /** Which parts of the vertex being written were given (ALL: none is being written). */
        private int given = ALL;
        private float x, y, z;

        Watched(VertexConsumer out, VertexFormat.Mode mode) {
            this.out = out;
            this.mode = mode;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            out.addVertex(x, y, z);
            this.x = x;
            this.y = y;
            this.z = z;
            vertices++;
            given = 0;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            out.setColor(r, g, b, a);
            given |= COLOR;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            out.setUv(u, v);
            given |= UV;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            out.setUv1(u, v);
            given |= OVERLAY;
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            out.setUv2(u, v);
            given |= LIGHT;
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            out.setNormal(x, y, z);
            given |= NORMAL;
            return this;
        }

        @Override
        public VertexConsumer misc(VertexFormatElement element, int... rawData) {
            out.misc(element, rawData);
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
            out.addVertex(x, y, z, color, u, v, overlay, light, nx, ny, nz);
            this.x = x;
            this.y = y;
            this.z = z;
            vertices++;
            given = ALL;
        }

        /** Finishes the vertex being written, then pads a short primitive with unseen copies of the last point. */
        void seal() {
            if (vertices == 0) return;
            finish();
            if (mode.connectedPrimitives) return;
            int len = mode.primitiveLength;
            for (int left = (len - vertices % len) % len; left > 0; left--) {
                out.addVertex(x, y, z);
                given = 0;
                finish();
            }
        }

        /** Gives the vertex being written the parts it is missing (a part a format doesn't have is passed over). */
        private void finish() {
            if ((given & COLOR) == 0) out.setColor(0, 0, 0, 0);
            if ((given & UV) == 0) out.setUv(0, 0);
            if ((given & OVERLAY) == 0) out.setOverlay(OverlayTexture.NO_OVERLAY);
            if ((given & LIGHT) == 0) out.setLight(0);
            if ((given & NORMAL) == 0) out.setNormal(0, 1, 0);
            given = ALL;
        }
    }
}
