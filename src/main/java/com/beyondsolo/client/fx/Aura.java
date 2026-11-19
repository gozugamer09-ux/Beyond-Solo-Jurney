package com.beyondsolo.client.fx;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.render.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The aura of a figure drawn the way the series draws power (see the art style guide in the project files): dark power
 * is a black body with a <b>burning edge</b>; <b>black flame</b> stands on its outline, whole at its foot and tearing
 * into tongues that burn only along their edges, over a low haze of its light; black <b>wisps</b> tear off its head and
 * shoulders like ink in water; its <b>eyes</b> are white-hot points that leave a comet's tail; a shadow comes and goes
 * through a <b>pool</b> of ink with a thin hot rim. A figure of light (a ruler, a herald) burns in flame of light
 * instead: white-gold, and no ink at all.
 *
 * <p>The edge and the flame follow an {@link Outline} read off the figure's own model as it was drawn, so they hug any
 * creature, vanilla or ours, from any side. The flame is a curtain on each stretch of the outline, drawn by the flame
 * shader ({@link ModRenderTypes#flame}), which lifts noise through it and tears it into tongues. Everything else is
 * drawn with the energy shader ({@link ModRenderTypes#energy}): the pictures' heat runs from the hue to white, their
 * cover is a body of the vertex colour; a dark vertex colour with the hue's direction gives ink with a hot rim. The
 * pictures come from {@code tools/gen_series_fx.py}.</p>
 *
 * <p>Wisps and eye trails remember where they were from frame to frame ({@link State}, one per figure); wisps are left
 * in the world where they tore off, so a figure on the move trails them.</p>
 */
public final class Aura {
    public static final ResourceLocation FLAME = tex("flame");
    public static final ResourceLocation SEAM = tex("seam");
    public static final ResourceLocation WISP = tex("wisp");
    public static final ResourceLocation POOL = tex("pool");
    public static final ResourceLocation SPLASH = tex("splash");
    public static final ResourceLocation SHAFT = tex("shaft");
    public static final ResourceLocation EDGE = tex("edge");
    public static final ResourceLocation TRAIL = tex("trail");
    public static final ResourceLocation SPARK = tex("spark");
    public static final ResourceLocation FEATHER = tex("feather");
    public static final ResourceLocation HALO = tex("halo");
    public static final ResourceLocation VEIN = tex("vein");

    /** The darkest ink: shadow bodies, monarch plate, the pools (art style: blue-black, never grey). */
    public static final int INK = 0x07080F;

    private static ResourceLocation tex(String name) {
        return BeyondSolo.id("textures/effect/series/" + name + ".png");
    }

    /** How a figure's aura burns. Build one per look and keep it: drawing reads it every frame. */
    public static final class Style {
        int hue = 0x9B5BFF;
        int second = -1;
        float rim = 0.6f;
        float rimWidth = 0.07f;
        float seams;
        float flame = 0.3f;
        float glow = 0.5f;
        float lean = 0.8f;
        float crest = 1f;
        float crown;
        boolean light;
        float wisps;
        float wispLength = 0.7f;

        /** The colour the edge, the flame and the eyes burn in (the shaders run it to white where it is hottest). */
        public Style hue(int rgb) {
            this.hue = rgb;
            return this;
        }

        /** How bright the light through the seams inside the outline is (an arm against the body, plates of armour). */
        public Style seams(float strength) {
            this.seams = strength;
            return this;
        }

        /** A second, fainter edge in another colour just outside the first (a regent's double edge); -1 for none. */
        public Style second(int rgb) {
            this.second = rgb;
            return this;
        }

        /** How bright the burning edge is (0: none) and how wide its light reaches, in blocks. */
        public Style rim(float strength, float width) {
            this.rim = strength;
            this.rimWidth = width;
            return this;
        }

        /** Flame up to {@code height} blocks tall on a man-sized figure (0: none). */
        public Style flame(float height) {
            this.flame = height;
            return this;
        }

        /** How strong the low haze of the hue under black flame is (0: none); for flame of light, how hard it burns. */
        public Style glow(float strength) {
            this.glow = strength;
            return this;
        }

        /** Flame on the top edges (head, shoulders, a crest) is this much taller. */
        public Style crest(float times) {
            this.crest = times;
            return this;
        }

        /**
         * A crown of black flame: the flame on the top edges (a head, a crest) burns in tall separate spikes, 0 (none)
         * to 1 (a regent's).
         */
        public Style crown(float spikes) {
            this.crown = spikes;
            return this;
        }

        /** How far the flame on the sides leans out from the body (0: straight up). */
        public Style lean(float lean) {
            this.lean = lean;
            return this;
        }

        /** Flame of light instead of black flame: a ruler's, a herald's. */
        public Style light() {
            this.light = true;
            return this;
        }

        /** Ink wisps torn off a second (0: none), each about {@code length} blocks long. */
        public Style wisps(float perSecond, float length) {
            this.wisps = perSecond;
            this.wispLength = length;
            return this;
        }

        public Style copy() {
            Style s = new Style();
            s.hue = hue;
            s.second = second;
            s.rim = rim;
            s.rimWidth = rimWidth;
            s.seams = seams;
            s.flame = flame;
            s.glow = glow;
            s.lean = lean;
            s.crest = crest;
            s.crown = crown;
            s.light = light;
            s.wisps = wisps;
            s.wispLength = wispLength;
            return s;
        }

        public int hue() {
            return hue;
        }

        public float flameHeight() {
            return flame;
        }
    }

    // ------------------------------------------------------------------ the edge and the flame

    /**
     * Draws the flame, the burning edge and the light in the seams of a figure whose outline was just found
     * ({@link Outline#solveWorld}, {@link Outline#solveScreen}). Everything is placed where the model was drawn, so no
     * pose is applied again.
     *
     * @param time   ticks, for the flicker
     * @param alpha  0..1, the figure's presence (rising, sinking, fading): a weak flame is a short one
     * @param unit   one block in the units the model was drawn in (1 in the world; a screen portrait's scale)
     * @param up     the world's up in that space, unit length
     * @param drift  where the flame is blown, in blocks per tick (the figure's motion, turned back)
     * @param detail 0..1: far away, the small stretches of the outline go without flame
     * @param size   how much bigger than a man the figure is: taller flame and wider edges
     * @param seed   the figure's own number, so two figures never flicker as one
     * @param day    0..1, how much daylight the figure stands in ({@link #daylight}): by day black flame burns low and the
     *               haze under it fades, at night they burn full
     */
    public static void edge(Outline o, MultiBufferSource buffers, Style s, float time, float alpha, float unit, Vec3 up, Vec3 drift,
                            float detail, float size, int seed, float day) {
        if (alpha <= 0.01f || o.edges() == 0) return;
        float wide = Math.max(1f, (float) Math.sqrt(size));
        if (s.flame > 0.01f && detail > 0.05f) {
            if (s.light) {
                curtains(o, buffers, s, s.hue, alpha * Math.max(0.3f, s.glow), s.flame, time, unit, up, drift, detail, size, seed);
            } else {
                // a low haze of the light first, then black flame over it: a dark aura that still burns at night
                float haze = alpha * s.glow * (1 - 0.6f * day);
                if (haze > 0.01f) curtains(o, buffers, s, s.hue, haze, s.flame * 0.6f, time, unit, up, drift, detail, size, seed + 3);
                curtains(o, buffers, s, dark(s.hue, 1 - (0.1f - 0.065f * day)), alpha, s.flame, time, unit, up, drift, detail, size, seed);
            }
        }
        if (s.rim > 0.01f) {
            rims(o, buffers, s.hue, s.rim * alpha, s.rimWidth * unit * wide, unit, 0f, time, seed);
            if (s.second >= 0) rims(o, buffers, s.second, s.rim * alpha * 0.55f, s.rimWidth * unit * 2.4f * wide, unit, 0.02f, time, seed + 7);
        }
        if (s.seams > 0.01f) seams(o, buffers, s.hue, s.seams * alpha, s.rimWidth * unit * wide * 0.6f, unit, time, seed);
    }

    /**
     * The burning edge, the flame and the ink wisps off a figure drawn in the world (camera-relative) from the outline
     * just recorded of it: finer up close, blown back by its motion, lower by day. Nothing past 72 blocks.
     *
     * @param size how much bigger than a man the figure is
     */
    public static void burnWorld(Outline o, MultiBufferSource buffers, Style s, Entity e, float partial, float time, float alpha, float size,
                                 int packedLight) {
        if (alpha <= 0.01f) return;
        o.solveWorld();
        double dist = Math.sqrt(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(e.getPosition(partial)));
        if (dist > 72) return;
        float detail = (float) Mth.clamp(1 - (dist - 24) / 40, 0, 1);
        // blown back by its own motion
        Vec3 drift = new Vec3(e.xo - e.getX(), 0, e.zo - e.getZ());
        if (drift.lengthSqr() > 0.25) drift = drift.normalize().scale(0.5);
        edge(o, buffers, s, time, alpha, 1f, UP, drift, Math.max(detail, 0.15f), size, e.getId(), daylight(packedLight, partial));
        if (detail > 0.1f) wisps(state(e.getId()), o, buffers, s, time, alpha * detail, drift, size, e.getId());
    }

    private static final Vec3 UP = new Vec3(0, 1, 0);

    /**
     * How much daylight a figure stands in, 0 (night, a cave, a screen) to 1 (open sky at noon), from the sky's
     * brightness and the sky light where it stands ({@code packedLight} as the renderer got it).
     */
    public static float daylight(int packedLight, float partial) {
        var level = Minecraft.getInstance().level;
        if (level == null) return 0f;
        float sky = (level.getSkyDarken(partial) - 0.2f) / 0.8f;
        return Mth.clamp(sky * LightTexture.sky(packedLight) / 15f, 0f, 1f);
    }

    /**
     * Flame standing on the outline: on each stretch of it that faces up or out (nothing burns downward, under an arm or
     * a foot) a curtain rising from just inside the body's edge, straight up off the top and leaning out on the sides,
     * blown back by the figure's motion and laid flat toward the eye. The flame shader tears it into tongues. A curtain
     * runs a little past both ends of its stretch and dies away there, so neighbouring stretches meet without a seam.
     */
    private static void curtains(Outline o, MultiBufferSource buffers, Style s, int rgb, float alpha, float height, float time, float unit,
                                 Vec3 up, Vec3 drift, float detail, float size, int seed) {
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        if (a <= 2 || !ModRenderTypes.flameReady()) return;
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        VertexConsumer vc = buffers.getBuffer(ModRenderTypes.flame(FLAME));
        float seconds = time / 20f;
        float ux = (float) up.x, uy = (float) up.y, uz = (float) up.z;
        float fx = (float) drift.x * 3, fy = (float) drift.y * 3, fz = (float) drift.z * 3;
        // a bigger figure burns in bigger tongues
        float scale = (float) Math.sqrt(size);
        float shortest = (1 - detail) * 0.15f * unit;
        float pull = 0.04f * unit, inset = 0.02f * unit;
        for (int i = 0; i < o.edges(); i++) {
            if (!o.outer(i)) continue;
            float mx = o.edge(i, 6), my = o.edge(i, 7), mz = o.edge(i, 8);
            float mUp = mx * ux + my * uy + mz * uz;
            if (mUp < -0.35f) continue;
            float len = o.edge(i, 9);
            if (len < shortest || len < 1.0e-4f * unit) continue;
            float ax = o.edge(i, 0), ay = o.edge(i, 1), az = o.edge(i, 2);
            float bx = o.edge(i, 3), by = o.edge(i, 4), bz = o.edge(i, 5);
            float dx = (bx - ax) / len, dy = (by - ay) / len, dz = (bz - az) / len;
            float crest = mUp > 0.6f ? s.crest : 1f + (s.crest - 1f) * Math.max(0f, mUp) * 0.5f;
            // a crown: on the top edges the waves under the flame are sharper, so it stands in tall separate spikes
            float spikes = mUp > 0.6f ? s.crown : 0f;
            crest *= 1f + 0.3f * spikes;
            float breath = 0.85f + 0.15f * Mth.sin(seconds * (1.1f + 0.9f * hash(seed, i)) + hash(seed, i + 977) * 6.28f);
            float h = height * unit * size * crest * breath;
            // the way it burns: up, out from the body on the sides, blown back by the motion
            float side = s.lean * (1 - Math.max(0f, mUp));
            float ex = ux + mx * side + fx, ey = uy + my * side + fy, ez = uz + mz * side + fz;
            float el = (float) Math.sqrt(ex * ex + ey * ey + ez * ez);
            if (el < 1.0e-5f) continue;
            ex /= el;
            ey /= el;
            ez /= el;
            float cx = (ax + bx) * 0.5f, cy = (ay + by) * 0.5f, cz = (az + bz) * 0.5f;
            float wx = o.ortho() ? o.eyeX() : o.eyeX() - cx, wy = o.ortho() ? o.eyeY() : o.eyeY() - cy, wz = o.ortho() ? o.eyeZ() : o.eyeZ() - cz;
            float wl = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
            if (wl < 1.0e-5f) continue;
            wx /= wl;
            wy /= wl;
            wz /= wl;
            // laid flat across the eye's line: flame that rises toward the eye looks short, as it should
            float ew = ex * wx + ey * wy + ez * wz;
            ex = (ex - wx * ew) * h;
            ey = (ey - wy * ew) * h;
            ez = (ez - wz * ew) * h;
            if (ex * ex + ey * ey + ez * ez < 1.0e-6f * unit * unit) continue;
            // its foot: a hair inside the outline and toward the eye, so it grows out of the body's very edge
            float px = ax + wx * pull - mx * inset, py = ay + wy * pull - my * inset, pz = az + wz * pull - mz * inset;
            float ext = Math.max(0.03f * unit, Math.min(0.1f * unit * scale, len * 0.3f));
            float u0 = hash(seed ^ 0x51ED270B, i) * 16f;
            float k = 1f / (unit * scale);
            // how tall the curtain stands in the same blocks of noise as u runs in, so the noise keeps its shape
            float tall = (float) Math.sqrt(ex * ex + ey * ey + ez * ez) * k;
            float ph1 = hash(seed ^ 0x2C1B3C6D, i) * 6.28f, ph2 = hash(seed ^ 0x7F4A7C15, i) * 6.28f;
            // the curtain in narrow columns, its strength rising and falling along it: separate tongues, tall where
            // it is strong, low between, that grow, die and drift as the waves under them run along the outline
            float span = len + 2 * ext;
            int n = Math.max(2, (int) Math.ceil(span / (0.08f * unit * scale / Math.max(0.35f, detail))));
            float step = span / n;
            float s0 = -ext;
            int a0 = 0;
            for (int j = 1; j <= n; j++) {
                float s1 = -ext + j * step;
                int a1 = 0;
                if (j < n) {
                    float ends = Mth.clamp(Math.min(s1 + ext, len + ext - s1) / ext, 0f, 1f);
                    float x = u0 + s1 * k;
                    float wave = 0.5f + 0.32f * Mth.sin(x * 19f + seconds * 1.7f + ph1) + 0.22f * Mth.sin(x * 37f - seconds * 2.3f + ph2);
                    wave = Mth.clamp(wave, 0f, 1f);
                    float peak = (float) Math.pow(wave, 1.5f + 2.5f * spikes);
                    a1 = Math.round(a * ends * (0.3f - 0.18f * spikes + (0.7f + 0.18f * spikes) * peak));
                }
                strip(vc, px, py, pz, dx, dy, dz, ex, ey, ez, s0, s1, u0, k, tall, r, g, b, a0, a1);
                s0 = s1;
                a0 = a1;
            }
        }
    }

    /** One stretch of a curtain: from {@code s0} to {@code s1} along (dx, dy, dz) from p, rising by e; u runs along it. */
    private static void strip(VertexConsumer vc, float px, float py, float pz, float dx, float dy, float dz, float ex, float ey, float ez,
                              float s0, float s1, float u0, float k, float tall, int r, int g, int b, int a0, int a1) {
        if (s1 <= s0) return;
        float x0 = px + dx * s0, y0 = py + dy * s0, z0 = pz + dz * s0;
        float x1 = px + dx * s1, y1 = py + dy * s1, z1 = pz + dz * s1;
        flameVertex(vc, x0, y0, z0, u0 + s0 * k, 0, r, g, b, a0, tall);
        flameVertex(vc, x1, y1, z1, u0 + s1 * k, 0, r, g, b, a1, tall);
        flameVertex(vc, x1 + ex, y1 + ey, z1 + ez, u0 + s1 * k, 1, r, g, b, a1, tall);
        flameVertex(vc, x0 + ex, y0 + ey, z0 + ez, u0 + s0 * k, 1, r, g, b, a0, tall);
    }

    /**
     * A vertex of flame ({@link ModRenderTypes#flame}): v runs from 0 at the foot to 1 at the top, and {@code tall} is
     * how tall the curtain stands in the blocks of noise u runs in. It rides in the normal's x (see flame.vsh).
     */
    private static void flameVertex(VertexConsumer vc, float x, float y, float z, float u, float v, int r, int g, int b, int a, float tall) {
        vc.addVertex(x, y, z).setUv(u, v).setColor(r, g, b, a).setNormal((float) Math.sqrt(Mth.clamp(tall / 16f, 0f, 1f)), 0f, 0f);
    }

    /**
     * A ring of flame standing on a circle {@code radius} blocks round the pose's up axis at height {@code y} (a crown
     * of black flame over a head, a ring round a throne), leaning out by {@code lean} of its height. Drawn as real
     * curtains round the circle, the far side first, so it reads the same from any side and on a screen.
     *
     * @param screen true on a screen (an orthographic portrait looking down -z), false in the world (eye at the origin)
     */
    public static void ring(PoseStack pose, MultiBufferSource buffers, float y, float radius, float height, float lean, int rgb, float alpha,
                            float time, int seed, boolean screen) {
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        if (a <= 2 || radius <= 0.001f || height <= 0.001f || !ModRenderTypes.flameReady()) return;
        int segs = 20;
        Matrix4f m = pose.last().pose();
        float seconds = time / 20f;
        float[] fx = new float[segs + 1], fy = new float[segs + 1], fz = new float[segs + 1];
        float[] tx = new float[segs + 1], ty = new float[segs + 1], tz = new float[segs + 1];
        int[] al = new int[segs + 1];
        float[] tall = new float[segs + 1];
        // u runs 2.2 blocks of noise to the block round the circle; the height goes in the same blocks
        float perBlock = 2.2f;
        org.joml.Vector3f v = new org.joml.Vector3f();
        for (int j = 0; j <= segs; j++) {
            float ang = j * Mth.TWO_PI / segs;
            float c = Mth.cos(ang), sn = Mth.sin(ang);
            int jj = j % segs;
            float beat = 0.7f + 0.3f * Mth.sin(seconds * (1.3f + 0.8f * hash(seed, jj)) + hash(seed, jj + 31) * 6.28f);
            float hh = height * beat;
            m.transformPosition(c * radius, y, sn * radius, v);
            fx[j] = v.x();
            fy[j] = v.y();
            fz[j] = v.z();
            m.transformPosition(c * (radius + lean * hh), y + hh, sn * (radius + lean * hh), v);
            tx[j] = v.x();
            ty[j] = v.y();
            tz[j] = v.z();
            al[j] = Mth.clamp(Math.round(alpha * (0.75f + 0.25f * beat) * 255), 0, 255);
            tall[j] = hh * (float) Math.sqrt(1 + lean * lean) * perBlock;
        }
        // far side first: nothing here writes depth, so what is drawn last is in front
        Integer[] order = new Integer[segs];
        float[] depth = new float[segs];
        for (int j = 0; j < segs; j++) {
            order[j] = j;
            float mx = (fx[j] + fx[j + 1]) * 0.5f, my = (fy[j] + fy[j + 1]) * 0.5f, mz = (fz[j] + fz[j + 1]) * 0.5f;
            depth[j] = screen ? -mz : mx * mx + my * my + mz * mz;
        }
        java.util.Arrays.sort(order, (p, q) -> Float.compare(depth[q], depth[p]));
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        float arc = Mth.TWO_PI * radius / segs * perBlock;
        float u0 = hash(seed, 7) * 16f;
        VertexConsumer vc = buffers.getBuffer(ModRenderTypes.flame(FLAME));
        for (int j : order) {
            float ua = u0 + j * arc, ub = u0 + (j + 1) * arc;
            flameVertex(vc, fx[j], fy[j], fz[j], ua, 0, r, g, b, al[j], tall[j]);
            flameVertex(vc, fx[j + 1], fy[j + 1], fz[j + 1], ub, 0, r, g, b, al[j + 1], tall[j + 1]);
            flameVertex(vc, tx[j + 1], ty[j + 1], tz[j + 1], ub, 1, r, g, b, al[j + 1], tall[j + 1]);
            flameVertex(vc, tx[j], ty[j], tz[j], ua, 1, r, g, b, al[j], tall[j]);
        }
    }

    /**
     * A wall of flame standing on a closed line of feet ({@code n} points, x y z in {@code feet}, the first not repeated,
     * before {@code m}): the rim of a domain on uneven land. It stands {@code height} blocks tall, leans away from
     * (cx, cz) by {@code lean} of its height, and burns in tongues {@code tongue} blocks wide; the far side is drawn first.
     */
    public static void wall(Matrix4f m, MultiBufferSource buffers, float[] feet, int n, float cx, float cz, float height, float lean,
                            float tongue, int rgb, float alpha, float time, int seed) {
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        if (a <= 2 || n < 3 || height <= 0.01f || !ModRenderTypes.flameReady()) return;
        float seconds = time / 20f;
        float perBlock = 1f / Math.max(0.2f, tongue);
        float[] fx = new float[n + 1], fy = new float[n + 1], fz = new float[n + 1];
        float[] tx = new float[n + 1], ty = new float[n + 1], tz = new float[n + 1], us = new float[n + 1], tall = new float[n + 1];
        int[] al = new int[n + 1];
        org.joml.Vector3f v = new org.joml.Vector3f();
        float run = 0;
        for (int j = 0; j <= n; j++) {
            int jj = j % n;
            float x = feet[jj * 3], y = feet[jj * 3 + 1], z = feet[jj * 3 + 2];
            if (j > 0) {
                int pj = (j - 1) % n;
                float ddx = x - feet[pj * 3], ddz = z - feet[pj * 3 + 2];
                run += (float) Math.sqrt(ddx * ddx + ddz * ddz);
            }
            float ox = x - cx, oz = z - cz;
            float ol = (float) Math.sqrt(ox * ox + oz * oz);
            if (ol > 1.0e-4f) {
                ox /= ol;
                oz /= ol;
            }
            float beat = 0.65f + 0.35f * Mth.sin(seconds * (1.1f + 0.7f * hash(seed, jj)) + hash(seed, jj + 31) * 6.28f);
            float hh = height * beat;
            m.transformPosition(x, y, z, v);
            fx[j] = v.x();
            fy[j] = v.y();
            fz[j] = v.z();
            m.transformPosition(x + ox * lean * hh, y + hh, z + oz * lean * hh, v);
            tx[j] = v.x();
            ty[j] = v.y();
            tz[j] = v.z();
            us[j] = run * perBlock;
            tall[j] = hh * (float) Math.sqrt(1 + lean * lean) * perBlock;
            al[j] = Mth.clamp(Math.round(alpha * (0.7f + 0.3f * beat) * 255), 0, 255);
        }
        Integer[] order = new Integer[n];
        float[] depth = new float[n];
        for (int j = 0; j < n; j++) {
            order[j] = j;
            float mx = (fx[j] + fx[j + 1]) * 0.5f, my = (fy[j] + fy[j + 1]) * 0.5f, mz = (fz[j] + fz[j + 1]) * 0.5f;
            depth[j] = mx * mx + my * my + mz * mz;
        }
        java.util.Arrays.sort(order, (p, q) -> Float.compare(depth[q], depth[p]));
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        float u0 = hash(seed, 7) * 16f;
        VertexConsumer vc = buffers.getBuffer(ModRenderTypes.flame(FLAME));
        for (int j : order) {
            float ua = u0 + us[j], ub = u0 + us[j + 1];
            flameVertex(vc, fx[j], fy[j], fz[j], ua, 0, r, g, b, al[j], tall[j]);
            flameVertex(vc, fx[j + 1], fy[j + 1], fz[j + 1], ub, 0, r, g, b, al[j + 1], tall[j + 1]);
            flameVertex(vc, tx[j + 1], ty[j + 1], tz[j + 1], ub, 1, r, g, b, al[j + 1], tall[j + 1]);
            flameVertex(vc, tx[j], ty[j], tz[j], ua, 1, r, g, b, al[j], tall[j]);
        }
    }

    /**
     * A hot thread along the outer outline, pulled a hair toward the eye so it lies over the body's very edge; each
     * stretch breathes on its own beat, like a flame's foot, and the soles burn low.
     */
    private static void rims(Outline o, MultiBufferSource buffers, int rgb, float alpha, float width, float unit, float out, float time,
                             int seed) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        VertexConsumer vc = buffers.getBuffer(ModRenderTypes.energy(EDGE));
        float seconds = time / 20f;
        for (int i = 0; i < o.edges(); i++) {
            if (!o.outer(i)) continue;
            float beat = 0.68f + 0.32f * Mth.sin(seconds * (3.1f + 2.3f * hash(seed, i * 3 + 1)) + hash(seed, i) * 6.28f);
            float under = o.edge(i, 7) < -0.7f ? 0.45f : 1f;
            int a = Mth.clamp(Math.round(alpha * beat * under * 255), 0, 255);
            if (a <= 2) continue;
            thread(vc, o, i, width * 0.5f, 0.03f * unit, out * unit, 0, 1, r, g, b, a);
        }
    }

    /**
     * Light showing through the seams inside the outline (an arm against the body, the plates of armour): a thread broken
     * into bright stretches and dark gaps that slowly crawls along the seam, never a whole line.
     */
    private static void seams(Outline o, MultiBufferSource buffers, int rgb, float alpha, float width, float unit, float time, int seed) {
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        if (a <= 2) return;
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        VertexConsumer vc = buffers.getBuffer(ModRenderTypes.energy(SEAM));
        float crawl = time / 20f * 0.12f;
        for (int i = 0; i < o.edges(); i++) {
            if (o.outer(i)) continue;
            float u0 = hash(seed ^ 0x6A09E667, i) * 8f + crawl;
            thread(vc, o, i, width * 0.5f, 0.03f * unit, 0f, u0, u0 + o.edge(i, 9) / unit * 0.6f, r, g, b, a);
        }
    }

    /** A thread {@code half} wide each way along edge {@code i}, facing the eye, pulled toward it and pushed {@code out}. */
    private static void thread(VertexConsumer vc, Outline o, int i, float half, float pull, float out, float uA, float uB, int r, int g, int b,
                               int a) {
        float ax = o.edge(i, 0), ay = o.edge(i, 1), az = o.edge(i, 2);
        float bx = o.edge(i, 3), by = o.edge(i, 4), bz = o.edge(i, 5);
        float mx = o.edge(i, 6), my = o.edge(i, 7), mz = o.edge(i, 8);
        float len = o.edge(i, 9);
        if (len < 1.0e-6f) return;
        float dx = (bx - ax) / len, dy = (by - ay) / len, dz = (bz - az) / len;
        float cx = (ax + bx) * 0.5f, cy = (ay + by) * 0.5f, cz = (az + bz) * 0.5f;
        float wx = o.ortho() ? o.eyeX() : o.eyeX() - cx, wy = o.ortho() ? o.eyeY() : o.eyeY() - cy, wz = o.ortho() ? o.eyeZ() : o.eyeZ() - cz;
        float wl = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
        if (wl < 1.0e-5f) return;
        wx /= wl;
        wy /= wl;
        wz /= wl;
        // across the edge, facing the eye, pointing out of the body
        float sx = dy * wz - dz * wy, sy = dz * wx - dx * wz, sz = dx * wy - dy * wx;
        float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1.0e-5f) return;
        if (sx * mx + sy * my + sz * mz < 0) sl = -sl;
        sx /= sl;
        sy /= sl;
        sz /= sl;
        float ox = wx * pull + sx * out, oy = wy * pull + sy * out, oz = wz * pull + sz * out;
        // a little longer than the edge, so the threads of two edges meet at the corner
        float ext = Math.min(half, len * 0.25f);
        float pax = ax - dx * ext + ox, pay = ay - dy * ext + oy, paz = az - dz * ext + oz;
        float pbx = bx + dx * ext + ox, pby = by + dy * ext + oy, pbz = bz + dz * ext + oz;
        vertex(vc, pax - sx * half, pay - sy * half, paz - sz * half, uA, 1, r, g, b, a);
        vertex(vc, pbx - sx * half, pby - sy * half, pbz - sz * half, uB, 1, r, g, b, a);
        vertex(vc, pbx + sx * half, pby + sy * half, pbz + sz * half, uB, 0, r, g, b, a);
        vertex(vc, pax + sx * half, pay + sy * half, paz + sz * half, uA, 0, r, g, b, a);
    }

    // ------------------------------------------------------------------ what a figure remembers

    private static final Int2ObjectMap<State> STATES = new Int2ObjectOpenHashMap<>();
    private static long frame;
    private static long lastSweep;

    /** The memory of the figure with this number (an entity id); made on first use, forgotten when unseen a while. */
    public static State state(int id) {
        State s = STATES.get(id);
        if (s == null) {
            s = new State();
            STATES.put(id, s);
        }
        s.seen = frame;
        return s;
    }

    /** Call once a frame: forgets figures that have not been drawn for a few seconds. */
    public static void tickFrame() {
        frame++;
        if (frame - lastSweep < 200) return;
        lastSweep = frame;
        STATES.values().removeIf(s -> frame - s.seen > 400);
    }

    /** Forgets everything (a new world). */
    public static void clear() {
        STATES.clear();
    }

    /** Wisps (in the world, where they tore off) and the eyes' trail, kept from frame to frame. */
    public static final class State {
        private static final int WISP = 10;
        private static final int MAX_WISPS = 24;
        private static final int TRAIL = 14;
        private final float[] wisps = new float[WISP * MAX_WISPS];
        private int wispCount;
        private float nextWisp = -1;
        private final float[] eyes = new float[TRAIL * 6];
        private final float[] eyeTime = new float[TRAIL];
        private int eyeCount;
        private float lastTime = Float.NaN;
        long seen;

        private State() {}
    }

    /**
     * Tears ink wisps off the top of the figure's outline (head, shoulders) and draws every wisp still alive: black
     * threads streaming up and away like ink in water, thick at the source. World only (never on a screen).
     */
    public static void wisps(State st, Outline o, MultiBufferSource buffers, Style s, float time, float alpha, Vec3 drift, float size, int seed) {
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        // time ran backwards (a new figure on an old number): start over
        if (!Float.isNaN(st.lastTime) && time < st.lastTime - 1) {
            st.wispCount = 0;
            st.eyeCount = 0;
            st.nextWisp = -1;
        }
        st.lastTime = time;
        if (s.wisps > 0.001f && alpha > 0.2f && o.edges() > 0) {
            if (st.nextWisp < 0) st.nextWisp = time + hash(seed, (int) time) * 20f / s.wisps;
            int guard = 0;
            while (time >= st.nextWisp && guard++ < 4) {
                spawnWisp(st, o, s, cam, st.nextWisp, drift, size, seed);
                st.nextWisp += (0.5f + hash(seed ^ 0x51ED27, (int) (st.nextWisp * 7))) * 20f / s.wisps;
            }
            if (time > st.nextWisp) st.nextWisp = time;
        }
        if (st.wispCount == 0) return;
        int rgb = dark(s.hue, 0.9f);
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        VertexConsumer vc = buffers.getBuffer(ModRenderTypes.energy(WISP));
        int live = 0;
        float[] w = st.wisps;
        for (int i = 0; i < st.wispCount; i++) {
            int o0 = i * State.WISP;
            float born = w[o0 + 3], life = w[o0 + 4];
            float age = (time - born) / life;
            if (age >= 1 || age < -0.05f) continue;
            if (live != i) System.arraycopy(w, o0, w, live * State.WISP, State.WISP);
            int l0 = live * State.WISP;
            live++;
            age = Math.max(0, age);
            float x = (float) (w[l0] - cam.x), y = (float) (w[l0 + 1] - cam.y), z = (float) (w[l0 + 2] - cam.z);
            int variant = (int) w[l0 + 5];
            float length = w[l0 + 6] * (0.35f + 0.65f * (float) Math.sqrt(age));
            float width = w[l0 + 7];
            float bx = w[l0 + 8], bz = w[l0 + 9];
            // the source drifts up and away as the wisp ages
            y += age * 0.45f;
            x += bx * age * 0.6f;
            z += bz * age * 0.6f;
            // ink stays ink until it is nearly gone (a fading wisp would only turn grey)
            float a = alpha * Mth.clamp(age / 0.1f, 0, 1) * (1 - age * age);
            int al = Mth.clamp(Math.round(a * 255), 0, 255);
            if (al <= 2) continue;
            ribbonWisp(vc, x, y, z, length, width, bx, bz, variant, age, born, r, g, b, al);
        }
        st.wispCount = live;
    }

    private static void spawnWisp(State st, Outline o, Style s, Vec3 cam, float born, Vec3 drift, float size, int seed) {
        // a top edge, picked by a hash of the moment it tears off
        int n = o.edges();
        int pick = -1;
        for (int tries = 0; tries < 6 && pick < 0; tries++) {
            int i = (int) (hash(seed ^ 0x3C6EF372, (int) (born * 13) + tries * 101) * n) % n;
            if (o.edge(i, 7) > 0.3f && o.outer(i)) pick = i;
        }
        if (pick < 0) return;
        if (st.wispCount >= State.MAX_WISPS) {
            // the oldest goes
            System.arraycopy(st.wisps, State.WISP, st.wisps, 0, State.WISP * (State.MAX_WISPS - 1));
            st.wispCount--;
        }
        float t = hash(seed ^ 0x1B873593, (int) (born * 31));
        float x = o.edge(pick, 0) + (o.edge(pick, 3) - o.edge(pick, 0)) * t;
        float y = o.edge(pick, 1) + (o.edge(pick, 4) - o.edge(pick, 1)) * t;
        float z = o.edge(pick, 2) + (o.edge(pick, 5) - o.edge(pick, 2)) * t;
        float h = hash(seed ^ 0x2545F491, (int) (born * 17));
        // blown back by the motion, or off the shoulder it tore from
        float bx = (float) -drift.x * 8 + o.edge(pick, 6) * 0.5f + (h - 0.5f) * 0.4f;
        float bz = (float) -drift.z * 8 + o.edge(pick, 8) * 0.5f + (hash(seed, (int) (born * 5)) - 0.5f) * 0.4f;
        int i = st.wispCount * State.WISP;
        float[] w = st.wisps;
        w[i] = (float) (cam.x + x);
        w[i + 1] = (float) (cam.y + y);
        w[i + 2] = (float) (cam.z + z);
        w[i + 3] = born;
        w[i + 4] = 18 + 16 * h;
        w[i + 5] = (int) (h * 4) & 3;
        w[i + 6] = s.wispLength * (float) Math.sqrt(size) * (0.7f + 0.6f * t);
        w[i + 7] = 0.17f * (float) Math.sqrt(size) * (0.8f + 0.4f * h);
        w[i + 8] = bx;
        w[i + 9] = bz;
        st.wispCount++;
    }

    /** One wisp: a ribbon rising from its source, swaying, its tail blown along (bx, bz). */
    private static void ribbonWisp(VertexConsumer vc, float x, float y, float z, float length, float width, float bx, float bz,
                                   int variant, float age, float born, int r, int g, int b, int a) {
        int segs = 6;
        float v0 = variant / 4f, v1 = v0 + 0.25f;
        float px = 0, py = 0, pz = 0, sxp = 0, syp = 0, szp = 0;
        for (int j = 0; j <= segs; j++) {
            float k = j / (float) segs;
            float sway = 0.12f * k * Mth.sin(born * 0.7f + k * 4f + age * 5f);
            float cx = x + bx * k * k * length * 0.8f + sway;
            float cy = y + k * length;
            float cz = z + bz * k * k * length * 0.8f - sway * 0.5f;
            // across the ribbon, facing the eye (the camera sits at the origin)
            float dx = bx * k * 1.6f * length * 0.8f, dy = length, dz = bz * k * 1.6f * length * 0.8f;
            float sx = dy * -cz - dz * -cy, sy = dz * -cx - dx * -cz, sz = dx * -cy - dy * -cx;
            float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1.0e-6f) {
                sx = 1;
                sy = 0;
                sz = 0;
                sl = 1;
            }
            float half = width * 0.5f * (1 - 0.3f * k) / sl;
            sx *= half;
            sy *= half;
            sz *= half;
            if (j > 0) {
                float u0 = (j - 1) / (float) segs, u1 = k;
                vertex(vc, px - sxp, py - syp, pz - szp, u0, v1, r, g, b, a);
                vertex(vc, cx - sx, cy - sy, cz - sz, u1, v1, r, g, b, a);
                vertex(vc, cx + sx, cy + sy, cz + sz, u1, v0, r, g, b, a);
                vertex(vc, px + sxp, py + syp, pz + szp, u0, v0, r, g, b, a);
            }
            px = cx;
            py = cy;
            pz = cz;
            sxp = sx;
            syp = sy;
            szp = sz;
        }
    }

    // ------------------------------------------------------------------ eyes

    /**
     * Two burning eyes at {@code left} and {@code right} (camera-relative, in the world): white-hot points with a halo of
     * the hue, and from each a comet's tail made of where the eye was a moment ago, rising and blown back, so a figure
     * that turns or charges draws streaks with its eyes. {@code back} is the way the tail streams when it stands still.
     */
    public static void eyes(State st, MultiBufferSource buffers, Vec3 left, Vec3 right, Vec3 back, int hue, float size, float alpha,
                            float tail, float time) {
        if (alpha <= 0.01f) return;
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        // remember where the eyes were (world), a few samples a tick
        if (st.eyeCount == 0 || time - st.eyeTime[0] >= 0.34f || time < st.eyeTime[0]) {
            if (st.eyeCount > 0 && time < st.eyeTime[0]) st.eyeCount = 0;
            int keep = Math.min(st.eyeCount, State.TRAIL - 1);
            System.arraycopy(st.eyes, 0, st.eyes, 6, keep * 6);
            System.arraycopy(st.eyeTime, 0, st.eyeTime, 1, keep);
            st.eyes[0] = (float) (cam.x + left.x);
            st.eyes[1] = (float) (cam.y + left.y);
            st.eyes[2] = (float) (cam.z + left.z);
            st.eyes[3] = (float) (cam.x + right.x);
            st.eyes[4] = (float) (cam.y + right.y);
            st.eyes[5] = (float) (cam.z + right.z);
            st.eyeTime[0] = time;
            st.eyeCount = keep + 1;
        }
        int r = hue >> 16 & 0xFF, g = hue >> 8 & 0xFF, b = hue & 0xFF;
        if (tail > 0.01f) {
            VertexConsumer vc = buffers.getBuffer(ModRenderTypes.energy(TRAIL));
            for (int e = 0; e < 2; e++) {
                Vec3 head = e == 0 ? left : right;
                trail(vc, st, e, head, back, cam, time, size * 1.1f, tail, r, g, b, alpha);
            }
        }
        VertexConsumer vc = buffers.getBuffer(ModRenderTypes.energy(SPARK));
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        for (int e = 0; e < 2; e++) {
            Vec3 at = e == 0 ? left : right;
            billboard(vc, (float) at.x, (float) at.y, (float) at.z, size * 2.2f, r, g, b, a, 0);
        }
    }

    private static void trail(VertexConsumer vc, State st, int eye, Vec3 head, Vec3 back, Vec3 cam, float time, float width, float tail,
                              int r, int g, int b, float alpha) {
        int n = st.eyeCount;
        float span = 7f; // ticks of history the tail shows
        float[] px = new float[n + 1], py = new float[n + 1], pz = new float[n + 1], age = new float[n + 1];
        px[0] = (float) head.x;
        py[0] = (float) head.y;
        pz[0] = (float) head.z;
        age[0] = 0;
        int m = 1;
        for (int i = 0; i < n; i++) {
            float ag = (time - st.eyeTime[i]) / span;
            if (ag >= 1) break;
            if (ag < 0) continue;
            int o = i * 6 + eye * 3;
            // where the eye was, risen and blown back with age: a streak even when the figure stands still
            float drift = ag * tail;
            px[m] = (float) (st.eyes[o] - cam.x + back.x * drift);
            py[m] = (float) (st.eyes[o + 1] - cam.y + back.y * drift + drift * 0.35f);
            pz[m] = (float) (st.eyes[o + 2] - cam.z + back.z * drift);
            age[m] = ag;
            m++;
        }
        if (m < 2) {
            // no history yet: a short streak straight back
            px[1] = (float) (head.x + back.x * tail * 0.4f);
            py[1] = (float) (head.y + back.y * tail * 0.4f + tail * 0.14f);
            pz[1] = (float) (head.z + back.z * tail * 0.4f);
            age[1] = 1;
            m = 2;
        }
        float px0 = 0, py0 = 0, pz0 = 0, sx0 = 0, sy0 = 0, sz0 = 0, u0 = 0;
        for (int j = 0; j < m; j++) {
            float dx = px[Math.min(m - 1, j + 1)] - px[Math.max(0, j - 1)];
            float dy = py[Math.min(m - 1, j + 1)] - py[Math.max(0, j - 1)];
            float dz = pz[Math.min(m - 1, j + 1)] - pz[Math.max(0, j - 1)];
            float sx = dy * -pz[j] - dz * -py[j], sy = dz * -px[j] - dx * -pz[j], sz = dx * -py[j] - dy * -px[j];
            float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1.0e-6f) continue;
            float half = width * 0.5f * (1 - age[j] * 0.8f) / sl;
            sx *= half;
            sy *= half;
            sz *= half;
            float u = age[j];
            if (j > 0) {
                int a0 = Mth.clamp(Math.round(alpha * (1 - u0) * 255), 0, 255);
                int a1 = Mth.clamp(Math.round(alpha * (1 - u) * 255), 0, 255);
                vertex(vc, px0 - sx0, py0 - sy0, pz0 - sz0, u0, 1, r, g, b, a0);
                vertex(vc, px[j] - sx, py[j] - sy, pz[j] - sz, u, 1, r, g, b, a1);
                vertex(vc, px[j] + sx, py[j] + sy, pz[j] + sz, u, 0, r, g, b, a1);
                vertex(vc, px0 + sx0, py0 + sy0, pz0 + sz0, u0, 0, r, g, b, a0);
            }
            px0 = px[j];
            py0 = py[j];
            pz0 = pz[j];
            sx0 = sx;
            sy0 = sy;
            sz0 = sz;
            u0 = u;
        }
    }

    // ------------------------------------------------------------------ pools

    /**
     * A pool of ink on the ground under the pose's origin (the figure's feet): a ragged black disc {@code radius}
     * blocks wide with a thin rim of the hue, slowly turning; {@code boil} (0..1) churns a second pool inside it.
     */
    public static void pool(PoseStack pose, MultiBufferSource buffers, float y, float radius, int hue, float alpha, float time, float boil) {
        if (alpha <= 0.01f || radius <= 0.01f) return;
        int rgb = dark(hue, 0.9f);
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        VertexConsumer vc = buffers.getBuffer(ModRenderTypes.energy(POOL));
        Matrix4f m = pose.last().pose();
        flatQuad(vc, m, y, radius, time * 0.4f, r, g, b, Mth.clamp(Math.round(alpha * 255), 0, 255));
        if (boil > 0.01f) {
            float k = 0.55f + 0.1f * Mth.sin(time * 0.5f);
            flatQuad(vc, m, y + 0.004f, radius * k, -time * 1.3f + 40, r, g, b, Mth.clamp(Math.round(alpha * boil * 255), 0, 255));
        }
    }

    /**
     * Ink thrown out over the ground under the pose's origin: a ragged ring whose front stands {@code radius} blocks out,
     * thick at the front and trailing off inward in streaks, with a faint edge of the hue. Grow the radius and fade it
     * out for a blast; {@code degrees} turns it, so two blasts never look alike.
     */
    public static void splash(PoseStack pose, MultiBufferSource buffers, float y, float radius, int hue, float alpha, float degrees) {
        if (alpha <= 0.01f || radius <= 0.01f) return;
        int rgb = dark(hue, 0.9f);
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        VertexConsumer vc = buffers.getBuffer(ModRenderTypes.energy(SPLASH));
        // the texture's front sits at 0.86 of its half-width
        flatQuad(vc, pose.last().pose(), y, radius / 0.86f, degrees, r, g, b, Mth.clamp(Math.round(alpha * 255), 0, 255));
    }

    // ------------------------------------------------------------------ the light (rulers, heralds, the light's hunters)

    /**
     * A broken halo (thin rings, segments, spokes) standing upright at (x, y, z) of the pose, square to the way a figure
     * facing {@code yaw} (degrees, as an entity's) looks, so it stands behind its head; {@code radius} blocks, turned
     * {@code turn} degrees in its own plane. Light, never a disc: the picture is all heat and no cover.
     */
    public static void halo(PoseStack pose, MultiBufferSource buffers, float x, float y, float z, float yaw, float radius, float turn, int hue,
                            float alpha) {
        if (alpha <= 0.01f || radius <= 0.01f) return;
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(turn));
        quad(buffers.getBuffer(ModRenderTypes.energy(HALO)), pose.last().pose(), radius, hue, alpha);
        pose.popPose();
    }

    /** A broken halo lying level at height y of the pose (a ruler's seal on the ground), turned {@code turn} degrees. */
    public static void seal(PoseStack pose, MultiBufferSource buffers, float y, float radius, float turn, int hue, float alpha) {
        if (alpha <= 0.01f || radius <= 0.01f) return;
        int r = hue >> 16 & 0xFF, g = hue >> 8 & 0xFF, b = hue & 0xFF;
        flatQuad(buffers.getBuffer(ModRenderTypes.energy(HALO)), pose.last().pose(), y, radius, turn, r, g, b, Mth.clamp(Math.round(alpha * 255), 0, 255));
    }

    /**
     * Wings of light on a figure facing {@code yaw} (degrees, as an entity's): on each side a fan of {@code n} long
     * blade-like feathers of light (a white spine, the hue at their edges, fading to nothing at the tips), rooted
     * between its shoulders at height {@code y} of the pose and {@code back} blocks behind it. {@code span} is the
     * longest feather; {@code open} runs from 0 (folded down its back) to 1 (spread wide); {@code beat} (-1..1) lifts
     * and lowers them a little.
     */
    public static void wings(PoseStack pose, MultiBufferSource buffers, float y, float back, float yaw, float span, int n, float open, float beat,
                             int hue, float alpha) {
        if (alpha <= 0.01f || span <= 0.01f || n <= 0) return;
        pose.pushPose();
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-yaw));
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffers.getBuffer(ModRenderTypes.energy(FEATHER));
        int r = hue >> 16 & 0xFF, g = hue >> 8 & 0xFF, b = hue & 0xFF;
        for (int side = -1; side <= 1; side += 2) {
            for (int j = 0; j < n; j++) {
                float k = n == 1 ? 1 : j / (float) (n - 1);
                // spread: from a little below level (the lowest) to high over the shoulder (the longest, the
                // primaries); folded, they all lie down along the back
                float spread = Mth.lerp(k, -18f, 68f), folded = -78f + 6f * k;
                float elev = (Mth.lerp(open, folded, spread) + 7f * beat * (0.4f + 0.6f * k)) * Mth.DEG_TO_RAD;
                float sweep = (16f + 22f * (1 - open) + 10f * (1 - k)) * Mth.DEG_TO_RAD;
                float dx = side * Mth.cos(elev) * Mth.cos(sweep), dy = Mth.sin(elev), dz = -Mth.cos(elev) * Mth.sin(sweep);
                float len = span * (0.55f + 0.45f * k) * (0.92f + 0.08f * hash(j, side + 7));
                float half = len * 0.13f;
                // the roots run along the arm of the wing, out and up from between the shoulders
                float rx = side * (0.12f + 0.07f * j), ry = y + 0.04f * j, rz = -back - 0.03f * j;
                // across the feather, in the wing's plane
                float wx = dy, wy = -dx, wl = Mth.sqrt(wx * wx + wy * wy);
                if (wl < 1e-4f) continue;
                wx *= half / wl;
                wy *= half / wl;
                float tx = rx + dx * len, ty = ry + dy * len, tz = rz + dz * len;
                int a = Mth.clamp(Math.round(alpha * (0.75f + 0.25f * k) * 255), 0, 255);
                vc.addVertex(m, rx - wx, ry - wy, rz).setUv(0, 1).setColor(r, g, b, a);
                vc.addVertex(m, rx + wx, ry + wy, rz).setUv(1, 1).setColor(r, g, b, a);
                vc.addVertex(m, tx + wx, ty + wy, tz).setUv(1, 0).setColor(r, g, b, a);
                vc.addVertex(m, tx - wx, ty - wy, tz).setUv(0, 0).setColor(r, g, b, a);
            }
        }
        pose.popPose();
    }

    /**
     * A glint of light at (x, y, z) of the pose facing the camera: a hot point with four short rays, {@code size} blocks
     * across (a mote of light, a herald's light leaving it). Never a soft ball.
     */
    public static void glint(PoseStack pose, MultiBufferSource buffers, float x, float y, float z, float size, int hue, float alpha, float roll) {
        if (alpha <= 0.01f || size <= 0.001f) return;
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(cam.rotation());
        pose.mulPose(com.mojang.math.Axis.ZP.rotation(roll));
        quad(buffers.getBuffer(ModRenderTypes.energy(SPARK)), pose.last().pose(), size * 0.5f, hue, alpha);
        pose.popPose();
    }

    /** A square picture in the pose's XY plane, centred on its origin, {@code half} to each side. */
    private static void quad(VertexConsumer vc, Matrix4f m, float half, int hue, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        if (a <= 2) return;
        int r = hue >> 16 & 0xFF, g = hue >> 8 & 0xFF, b = hue & 0xFF;
        vc.addVertex(m, -half, -half, 0).setUv(0, 1).setColor(r, g, b, a);
        vc.addVertex(m, half, -half, 0).setUv(1, 1).setColor(r, g, b, a);
        vc.addVertex(m, half, half, 0).setUv(1, 0).setColor(r, g, b, a);
        vc.addVertex(m, -half, half, 0).setUv(0, 0).setColor(r, g, b, a);
    }

    private static void flatQuad(VertexConsumer vc, Matrix4f m, float y, float half, float degrees, int r, int g, int b, int a) {
        float rad = degrees * Mth.DEG_TO_RAD;
        float c = Mth.cos(rad) * half, s = Mth.sin(rad) * half;
        vc.addVertex(m, -c + s, y, -s - c).setUv(0, 0).setColor(r, g, b, a);
        vc.addVertex(m, -c - s, y, -s + c).setUv(0, 1).setColor(r, g, b, a);
        vc.addVertex(m, c - s, y, s + c).setUv(1, 1).setColor(r, g, b, a);
        vc.addVertex(m, c + s, y, s - c).setUv(1, 0).setColor(r, g, b, a);
    }

    // ------------------------------------------------------------------ helpers

    /** A point of light facing the camera (camera-relative, in the world), {@code size} blocks across. */
    public static void billboard(VertexConsumer vc, float x, float y, float z, float size, int r, int g, int b, int a, float roll) {
        // the camera's own right and up, from its rotation
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        org.joml.Vector3f left = cam.getLeftVector(), up = cam.getUpVector();
        float h = size * 0.5f;
        float cr = Mth.cos(roll), sr = Mth.sin(roll);
        float rx = (-left.x() * cr + up.x() * sr) * h, ry = (-left.y() * cr + up.y() * sr) * h, rz = (-left.z() * cr + up.z() * sr) * h;
        float ux = (up.x() * cr + left.x() * sr) * h, uy = (up.y() * cr + left.y() * sr) * h, uz = (up.z() * cr + left.z() * sr) * h;
        vertex(vc, x - rx - ux, y - ry - uy, z - rz - uz, 0, 1, r, g, b, a);
        vertex(vc, x + rx - ux, y + ry - uy, z + rz - uz, 1, 1, r, g, b, a);
        vertex(vc, x + rx + ux, y + ry + uy, z + rz + uz, 1, 0, r, g, b, a);
        vertex(vc, x - rx + ux, y - ry + uy, z - rz + uz, 0, 0, r, g, b, a);
    }

    /**
     * The colour {@code rgb} darkened {@code amount} of the way to black, keeping its direction: drawn with the energy
     * shader it is a body of ink whose light still burns in the hue.
     */
    public static int dark(int rgb, float amount) {
        float k = 1 - amount;
        int r = Math.max(1, Math.round((rgb >> 16 & 0xFF) * k)), g = Math.max(1, Math.round((rgb >> 8 & 0xFF) * k)),
                b = Math.max(1, Math.round((rgb & 0xFF) * k));
        return r << 16 | g << 8 | b;
    }

    /** Mixes two colours: 0 is {@code a}, 1 is {@code b}. */
    public static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0, 1);
        int r = Math.round((a >> 16 & 0xFF) * (1 - t) + (b >> 16 & 0xFF) * t);
        int g = Math.round((a >> 8 & 0xFF) * (1 - t) + (b >> 8 & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return r << 16 | g << 8 | bl;
    }

    static void vertex(VertexConsumer vc, float x, float y, float z, float u, float v, int r, int g, int b, int a) {
        vc.addVertex(x, y, z).setUv(u, v).setColor(r, g, b, a);
    }

    /** 0..1, the same for the same two numbers. */
    static float hash(int a, int b) {
        int h = a * 0x27D4EB2D ^ b * 0x165667B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    private Aura() {}
}
