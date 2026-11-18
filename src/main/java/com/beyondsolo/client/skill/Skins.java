package com.beyondsolo.client.skill;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.render.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The skins of skill effects: shapes with a body and light of their own, drawn with the energy shader
 * ({@link ModRenderTypes#energy}). A blade is a crescent with a white-hot edge, a beam a tube of moving energy with a
 * hot core and coils round it, an orb a ball of churning light, a blast a shell that runs out and burns away,
 * lightning a forked bolt. The skill's element picks the skin ({@link Look}): fire burns, water flows, shadow is dark
 * with bright edges, lightning crackles. The pictures come from {@code tools/gen_skill_skins.py}.
 *
 * <p>Positions are relative to the pose's origin, which sits at {@code origin} in the world. Like the rest of the
 * skill effects, every helper fetches its buffer right before it draws and is done with it before the next one is
 * fetched (the shared buffer is drawn and reset whenever the render type changes), so effects draw in the order
 * they are asked for: bodies first, then the light added over them.</p>
 */
final class Skins {
    static final ResourceLocation BLADE = skin("blade");
    static final ResourceLocation BEAM = skin("beam");
    static final ResourceLocation BOLT = skin("lightning");
    static final ResourceLocation PLASMA = skin("plasma");
    static final ResourceLocation FIRE = skin("fire");
    static final ResourceLocation WATER = skin("water");
    static final ResourceLocation WIND = skin("wind");
    /** Four hexes across, two rows of two down: 4 by 2 * sqrt 3 units. */
    static final ResourceLocation HEX = skin("hex");
    static final ResourceLocation WALL = skin("wall");
    static final ResourceLocation FISSURE = skin("fissure");
    static final ResourceLocation RAY = skin("ray");
    static final ResourceLocation AURA = skin("aura");
    static final ResourceLocation ORB = skin("orb");
    static final ResourceLocation FLARE = skin("flare");
    /** A burnt patch: its own dark colour, blended over the ground (not an energy skin). */
    static final ResourceLocation SCORCH = skin("scorch");
    /** A puff of smoke or dust: its own grey, blended over the world (tint it darker for soot). */
    static final ResourceLocation SMOKE = skin("smoke");
    static final ResourceLocation[] ENERGY = {BLADE, BEAM, BOLT, PLASMA, FIRE, WATER, WIND, HEX, WALL, FISSURE, RAY, AURA, ORB, FLARE};

    private static ResourceLocation skin(String name) {
        return BeyondSolo.id("textures/effect/skin/" + name + ".png");
    }

    /** Draws every batch still open: the energy skins, then the light added over them. */
    static void flush(MultiBufferSource.BufferSource buffers) {
        for (ResourceLocation tex : ENERGY) buffers.endBatch(ModRenderTypes.energy(tex));
        buffers.endBatch(ModRenderTypes.softTexture(SCORCH));
        buffers.endBatch(ModRenderTypes.softTexture(SMOKE));
    }

    /**
     * How a skill's element is skinned: the picture its bodies scroll, and the colours of body and light. Same order
     * as {@code SkillElement}, which the server sends; an effect of no skill guesses from its colour.
     */
    enum Look {
        PHYSICAL, FIRE, WATER, LIGHTNING, AIR, EARTH, SHADOW, LIGHT, NATURE, ARCANE;

        static Look of(int element, int color) {
            Look[] all = values();
            return element >= 0 && element < all.length ? all[element] : guess(color);
        }

        /** The element an effect of no skill most likely shows, from the hue of its colour. */
        static Look guess(int color) {
            float r = ((color >> 16) & 0xFF) / 255f, g = ((color >> 8) & 0xFF) / 255f, b = (color & 0xFF) / 255f;
            float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
            float sat = max <= 0 ? 0 : (max - min) / max;
            if (sat < 0.2f) return max > 0.8f ? LIGHT : PHYSICAL;
            float hue = hue(r, g, b, max, min);
            if (max < 0.45f && (hue > 250 || hue < 20)) return SHADOW;
            if (hue < 15 || hue >= 330) return PHYSICAL;
            if (hue < 45) return FIRE;
            if (hue < 68) return LIGHT;
            if (hue < 150) return NATURE;
            if (hue < 180) return AIR;
            if (hue < 212) return sat < 0.35f ? LIGHTNING : WATER;
            if (hue < 250) return ARCANE;
            return SHADOW;
        }

        private static float hue(float r, float g, float b, float max, float min) {
            float d = max - min;
            if (d <= 0) return 0;
            float h;
            if (max == r) h = ((g - b) / d) % 6;
            else if (max == g) h = (b - r) / d + 2;
            else h = (r - g) / d + 4;
            h *= 60;
            return h < 0 ? h + 360 : h;
        }

        /** The picture a body of this element scrolls: flames, water, wind, raw energy. */
        ResourceLocation skin() {
            return switch (this) {
                case FIRE -> Skins.FIRE;
                case WATER -> Skins.WATER;
                case AIR -> WIND;
                default -> PLASMA;
            };
        }

        /** The colour of a body of colour {@code c}. Shadow is dark, so only its edges shine. */
        int body(int c) {
            return switch (this) {
                case SHADOW -> SkillWorldFx.mix(c, 0x05020A, 0.8f);
                case FIRE -> SkillWorldFx.mix(c, 0xFF4410, 0.25f);
                case LIGHT -> SkillWorldFx.mix(c, 0xFFF0C0, 0.3f);
                case LIGHTNING -> SkillWorldFx.mix(c, 0xD8ECFF, 0.25f);
                case EARTH -> SkillWorldFx.mix(c, 0x5A3A1A, 0.35f);
                default -> c;
            };
        }

        /** The colour of its light: edges, cores, glows. */
        int light(int c) {
            return switch (this) {
                case SHADOW -> SkillWorldFx.mix(c, 0xC890FF, 0.35f);
                case FIRE -> SkillWorldFx.mix(c, 0xFFC860, 0.45f);
                case EARTH -> SkillWorldFx.mix(c, 0xFFB050, 0.4f);
                default -> SkillWorldFx.mix(c, 0xFFFFFF, 0.35f);
            };
        }

        boolean dark() {
            return this == SHADOW;
        }
    }

    // ---- the shapes ----

    /** A profile along a shape, k from 0 (its start) to 1 (its end). */
    @FunctionalInterface
    interface Profile {
        float at(float k);
    }

    static final Profile FLAT = k -> 1;

    /**
     * A ribbon through {@code pts}, turned to face the camera, {@code width} and {@code alpha} at each point. u runs
     * along it: {@code uPerBlock} above 0 tiles the picture that many times a block from {@code u0}, 0 stretches it
     * once over the whole length. A ribbon fades where it comes close to the eye, so one passing through the camera
     * does not smear across the view.
     */
    static void ribbon(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, RenderType type,
                       Vec3[] pts, float[] width, float[] alpha, int rgb, float u0, float uPerBlock) {
        int n = pts.length;
        if (n < 2) return;
        double total = 0;
        for (int i = 1; i < n; i++) total += pts[i].distanceTo(pts[i - 1]);
        if (total < 1.0e-4) return;
        Vec3 eye = camera.getPosition().subtract(origin);
        Vec3[] side = new Vec3[n];
        float[] u = new float[n];
        float[] a = new float[n];
        Vec3 last = null;
        double run = 0;
        boolean any = false;
        for (int i = 0; i < n; i++) {
            Vec3 t = pts[Math.min(n - 1, i + 1)].subtract(pts[Math.max(0, i - 1)]);
            Vec3 view = eye.subtract(pts[i]);
            Vec3 s = t.cross(view);
            if (s.lengthSqr() < 1.0e-12) s = t.cross(new Vec3(0, 1, 0));
            if (s.lengthSqr() < 1.0e-12) s = last != null ? last : new Vec3(1, 0, 0);
            s = s.normalize();
            if (last != null && s.dot(last) < 0) s = s.scale(-1);
            last = s;
            side[i] = s.scale(width[i] / 2);
            if (i > 0) run += pts[i].distanceTo(pts[i - 1]);
            u[i] = uPerBlock > 0 ? (float) (u0 + run * uPerBlock) : (float) (u0 + run / total);
            a[i] = alpha[i] * (float) Mth.clamp((view.length() - 0.25) / (0.45 + width[i]), 0, 1);
            any |= a[i] > 0.004f;
        }
        if (!any) return;
        VertexConsumer out = buffers.getBuffer(type);
        Matrix4f m = pose.last().pose();
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        for (int i = 0; i < n - 1; i++) {
            if (a[i] <= 0.004f && a[i + 1] <= 0.004f) continue;
            Vec3 p0 = pts[i], p1 = pts[i + 1], s0 = side[i], s1 = side[i + 1];
            vertex(out, m, p0.x - s0.x, p0.y - s0.y, p0.z - s0.z, u[i], 1, r, g, b, a[i]);
            vertex(out, m, p1.x - s1.x, p1.y - s1.y, p1.z - s1.z, u[i + 1], 1, r, g, b, a[i + 1]);
            vertex(out, m, p1.x + s1.x, p1.y + s1.y, p1.z + s1.z, u[i + 1], 0, r, g, b, a[i + 1]);
            vertex(out, m, p0.x + s0.x, p0.y + s0.y, p0.z + s0.z, u[i], 0, r, g, b, a[i]);
        }
    }

    /** {@link #ribbon} along a straight line from a to b in {@code n} pieces, its width and alpha following profiles. */
    static void line(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, RenderType type, Vec3 a, Vec3 b, int n,
                     float width, Profile widthAlong, float alpha, Profile alphaAlong, int rgb, float u0, float uPerBlock) {
        n = Math.max(1, n);
        Vec3[] pts = new Vec3[n + 1];
        float[] w = new float[n + 1], al = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            float k = i / (float) n;
            pts[i] = a.lerp(b, k);
            w[i] = width * widthAlong.at(k);
            al[i] = alpha * alphaAlong.at(k);
        }
        ribbon(pose, buffers, camera, origin, type, pts, w, al, rgb, u0, uPerBlock);
    }

    /**
     * A strip between two edges: {@code a[i]} and {@code b[i]} are its two sides at station i, {@code u[i]} its u
     * there, v 0 along a and 1 along b. For shapes that keep their own plane: blades, cracks on the ground, walls.
     */
    static void strip(PoseStack pose, MultiBufferSource buffers, RenderType type, Vec3[] a, Vec3[] b, float[] u, float[] alpha, int rgb) {
        strip(pose, buffers, type, a, b, u, alpha, rgb, 0, 1);
    }

    /** {@link #strip} with v running from {@code va} along a to {@code vb} along b. */
    static void strip(PoseStack pose, MultiBufferSource buffers, RenderType type, Vec3[] a, Vec3[] b, float[] u, float[] alpha, int rgb,
                      float va, float vb) {
        int n = a.length;
        if (n < 2) return;
        VertexConsumer out = buffers.getBuffer(type);
        Matrix4f m = pose.last().pose();
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, bl = rgb & 0xFF;
        for (int i = 0; i < n - 1; i++) {
            if (alpha[i] <= 0.004f && alpha[i + 1] <= 0.004f) continue;
            vertex(out, m, a[i].x, a[i].y, a[i].z, u[i], va, r, g, bl, alpha[i]);
            vertex(out, m, a[i + 1].x, a[i + 1].y, a[i + 1].z, u[i + 1], va, r, g, bl, alpha[i + 1]);
            vertex(out, m, b[i + 1].x, b[i + 1].y, b[i + 1].z, u[i + 1], vb, r, g, bl, alpha[i + 1]);
            vertex(out, m, b[i].x, b[i].y, b[i].z, u[i], vb, r, g, bl, alpha[i]);
        }
    }

    /**
     * A crescent in the pose's XZ plane round its origin, the way a blade cuts: its outer edge on {@code radius}, from
     * angle {@code from} (the tail) to {@code to} (the head), in degrees from +z towards +x. It is {@code width} deep
     * at its widest, a little behind the head, and comes to a point at both ends. u runs from {@code u0} at the tail
     * to {@code u0 + uSpan} at the head, v from 0 on the outer edge to 1 on the inner; alpha rises from the tail as
     * {@code k^tailFade}.
     */
    static void crescent(PoseStack pose, MultiBufferSource buffers, RenderType type, float radius, float width, float from, float to,
                         int rgb, float alpha, float tailFade, float u0, float uSpan) {
        if (alpha <= 0.004f || Math.abs(to - from) < 0.5f || width <= 0.005f) return;
        int n = Mth.clamp((int) (Math.abs(to - from) / 4), 6, 48);
        Vec3[] outer = new Vec3[n + 1], inner = new Vec3[n + 1];
        float[] u = new float[n + 1], al = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            float k = i / (float) n;
            float ang = Mth.lerp(k, from, to) * Mth.DEG_TO_RAD;
            float deep = width * (float) Math.pow(Math.max(0, Mth.sin(Mth.PI * (float) Math.pow(k, 0.72))), 0.75);
            float s = Mth.sin(ang), c = Mth.cos(ang);
            outer[i] = new Vec3(s * radius, 0, c * radius);
            inner[i] = new Vec3(s * (radius - deep), 0, c * (radius - deep));
            u[i] = u0 + uSpan * k;
            al[i] = alpha * (tailFade > 0 ? (float) Math.pow(k, tailFade) : 1);
        }
        strip(pose, buffers, type, outer, inner, u, al, rgb);
    }

    /**
     * A tube from {@code from} to {@code to}: {@code sides} round, {@code rings} along, its radius following
     * {@code radius} (k 0 at from, 1 at to) and its alpha {@code alongAlpha}. u goes {@code uTurns} times round
     * from {@code u0} (turned by {@code twist} turns from one end to the other); v runs along, from {@code v0} at
     * {@code from} by {@code vPerBlock} a block. {@code rim} above 0 brightens the edges seen side-on (a shell of
     * energy), below 0 the middle (a solid core).
     */
    static void tube(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, RenderType type, Vec3 from, Vec3 to,
                     int sides, int rings, Profile radius, Profile alongAlpha, float alpha, int rgb, float u0, float uTurns, float twist,
                     float v0, float vPerBlock, float rim) {
        Vec3 axis = to.subtract(from);
        double len = axis.length();
        if (len < 1.0e-4 || alpha <= 0.004f) return;
        Vec3 d = axis.scale(1 / len);
        Vec3 e1 = d.cross(Math.abs(d.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
        Vec3 e2 = d.cross(e1);
        Vec3 eye = camera.getPosition().subtract(origin);
        Vec3[][] pos = new Vec3[rings + 1][sides + 1];
        float[][] al = new float[rings + 1][sides + 1];
        for (int k = 0; k <= rings; k++) {
            float kk = k / (float) rings;
            Vec3 c = from.add(axis.scale(kk));
            float rr = radius.at(kk);
            float ak = alpha * alongAlpha.at(kk);
            for (int j = 0; j <= sides; j++) {
                double th = (j / (double) sides + twist * kk) * Math.PI * 2;
                Vec3 nrm = e1.scale(Math.cos(th)).add(e2.scale(Math.sin(th)));
                Vec3 p = c.add(nrm.scale(rr));
                pos[k][j] = p;
                al[k][j] = ak * fresnel(nrm, eye.subtract(p), rim) * near(eye, p, rr);
            }
        }
        VertexConsumer out = buffers.getBuffer(type);
        Matrix4f m = pose.last().pose();
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        for (int k = 0; k < rings; k++) {
            float va = (float) (v0 + vPerBlock * len * k / rings), vb = (float) (v0 + vPerBlock * len * (k + 1) / rings);
            if (vPerBlock == 0) {
                va = v0 + k / (float) rings;
                vb = v0 + (k + 1) / (float) rings;
            }
            for (int j = 0; j < sides; j++) {
                if (al[k][j] + al[k][j + 1] + al[k + 1][j] + al[k + 1][j + 1] <= 0.01f) continue;
                float ua = u0 + uTurns * j / sides, ub = u0 + uTurns * (j + 1) / sides;
                Vec3 p00 = pos[k][j], p01 = pos[k][j + 1], p10 = pos[k + 1][j], p11 = pos[k + 1][j + 1];
                vertex(out, m, p00.x, p00.y, p00.z, ua, va, r, g, b, al[k][j]);
                vertex(out, m, p01.x, p01.y, p01.z, ub, va, r, g, b, al[k][j + 1]);
                vertex(out, m, p11.x, p11.y, p11.z, ub, vb, r, g, b, al[k + 1][j + 1]);
                vertex(out, m, p10.x, p10.y, p10.z, ua, vb, r, g, b, al[k + 1][j]);
            }
        }
    }

    /**
     * A ball at {@code c}: {@code lat} rings from pole to pole, {@code lon} round. The picture is wrapped once round
     * ({@code uTurns} times) and once from pole to pole ({@code vTurns}), offset by u0 and v0 so it can churn.
     * {@code rim} as for {@link #tube}: above 0 a shell bright at its edges, below 0 a solid middle.
     */
    static void sphere(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, RenderType type, Vec3 c, float radius,
                       int lat, int lon, float alpha, int rgb, float u0, float uTurns, float v0, float vTurns, float rim) {
        if (alpha <= 0.004f || radius <= 0.005f) return;
        Vec3 eye = camera.getPosition().subtract(origin);
        Vec3[][] pos = new Vec3[lat + 1][lon + 1];
        float[][] al = new float[lat + 1][lon + 1];
        for (int i = 0; i <= lat; i++) {
            double phi = Math.PI * i / lat;
            double y = Math.cos(phi), ring = Math.sin(phi);
            for (int j = 0; j <= lon; j++) {
                double th = Math.PI * 2 * j / lon;
                Vec3 nrm = new Vec3(Math.cos(th) * ring, y, Math.sin(th) * ring);
                Vec3 p = c.add(nrm.scale(radius));
                pos[i][j] = p;
                al[i][j] = alpha * fresnel(nrm, eye.subtract(p), rim) * near(eye, p, radius);
            }
        }
        VertexConsumer out = buffers.getBuffer(type);
        Matrix4f m = pose.last().pose();
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        for (int i = 0; i < lat; i++) {
            float va = v0 + vTurns * i / lat, vb = v0 + vTurns * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                if (al[i][j] + al[i][j + 1] + al[i + 1][j] + al[i + 1][j + 1] <= 0.01f) continue;
                float ua = u0 + uTurns * j / lon, ub = u0 + uTurns * (j + 1) / lon;
                Vec3 p00 = pos[i][j], p01 = pos[i][j + 1], p10 = pos[i + 1][j], p11 = pos[i + 1][j + 1];
                vertex(out, m, p00.x, p00.y, p00.z, ua, va, r, g, b, al[i][j]);
                vertex(out, m, p01.x, p01.y, p01.z, ub, va, r, g, b, al[i][j + 1]);
                vertex(out, m, p11.x, p11.y, p11.z, ub, vb, r, g, b, al[i + 1][j + 1]);
                vertex(out, m, p10.x, p10.y, p10.z, ua, vb, r, g, b, al[i + 1][j]);
            }
        }
    }

    /**
     * A curved plate facing {@code fwd} with its middle at {@code centre}: a piece of a ball's shell {@code radius}
     * across its rim, bulging {@code bulge} degrees towards {@code fwd} (a shield, a wave of force). The picture is laid
     * over it flat, {@code tiles} across; {@code rim} as for {@link #tube}; it thins out towards its edge.
     */
    static void cap(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, RenderType type, Vec3 centre, Vec3 fwd, float radius,
                    float bulge, float alpha, int rgb, float tiles, float u0, float v0, float rim) {
        if (alpha <= 0.004f || radius <= 0.01f || fwd.lengthSqr() < 1.0e-8) return;
        Vec3 f = fwd.normalize();
        Vec3 sx = f.cross(new Vec3(0, 1, 0));
        if (sx.lengthSqr() < 1.0e-6) sx = new Vec3(1, 0, 0);
        sx = sx.normalize();
        Vec3 sy = sx.cross(f);
        float th0 = Mth.clamp(bulge, 5, 85) * Mth.DEG_TO_RAD;
        float ball = radius / Mth.sin(th0);
        Vec3 mid = centre.subtract(f.scale(ball));
        Vec3 eye = camera.getPosition().subtract(origin);
        int rings = 6, segs = 28;
        Vec3[][] pos = new Vec3[rings + 1][segs + 1];
        float[][] al = new float[rings + 1][segs + 1], us = new float[rings + 1][segs + 1], vs = new float[rings + 1][segs + 1];
        for (int i = 0; i <= rings; i++) {
            float th = th0 * i / rings;
            for (int j = 0; j <= segs; j++) {
                float ph = Mth.TWO_PI * j / segs;
                float px = Mth.sin(th) * Mth.cos(ph), py = Mth.sin(th) * Mth.sin(ph);
                Vec3 n = f.scale(Mth.cos(th)).add(sx.scale(px)).add(sy.scale(py));
                Vec3 p = mid.add(n.scale(ball));
                pos[i][j] = p;
                float edge = i == rings ? 0.45f : 1;
                al[i][j] = alpha * edge * fresnel(n, eye.subtract(p), rim) * near(eye, p, radius);
                us[i][j] = u0 + px * ball / (2 * radius) * tiles;
                vs[i][j] = v0 + py * ball / (2 * radius) * tiles;
            }
        }
        VertexConsumer out = buffers.getBuffer(type);
        Matrix4f m = pose.last().pose();
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        for (int i = 0; i < rings; i++) {
            for (int j = 0; j < segs; j++) {
                if (al[i][j] + al[i][j + 1] + al[i + 1][j] + al[i + 1][j + 1] <= 0.01f) continue;
                vertex(out, m, pos[i][j].x, pos[i][j].y, pos[i][j].z, us[i][j], vs[i][j], r, g, b, al[i][j]);
                vertex(out, m, pos[i][j + 1].x, pos[i][j + 1].y, pos[i][j + 1].z, us[i][j + 1], vs[i][j + 1], r, g, b, al[i][j + 1]);
                vertex(out, m, pos[i + 1][j + 1].x, pos[i + 1][j + 1].y, pos[i + 1][j + 1].z, us[i + 1][j + 1], vs[i + 1][j + 1], r, g, b, al[i + 1][j + 1]);
                vertex(out, m, pos[i + 1][j].x, pos[i + 1][j].y, pos[i + 1][j].z, us[i + 1][j], vs[i + 1][j], r, g, b, al[i + 1][j]);
            }
        }
    }

    /**
     * A wall standing round {@code centre} on the ground: radius {@code radius}, {@code height} tall, the wall picture
     * (bright at its foot) going {@code uTurns} times round from {@code u0}.
     */
    static void wall(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, Vec3 centre, float radius, float height,
                     float alpha, int rgb, float u0, float uTurns, Profile alongAlpha) {
        if (height <= 0.01f || radius <= 0.01f) return;
        int sides = Mth.clamp((int) (radius * 9), 16, 64);
        // the picture's foot (v 1) on the ground, its top (v 0) at the top, kept off the very edges so the tiling
        // does not bleed the bright foot into the top
        tube(pose, buffers, camera, origin, ModRenderTypes.energy(WALL), centre, centre.add(0, height, 0), sides, 1, k -> radius,
                alongAlpha, alpha, rgb, u0, uTurns, 0, 0.985f, -0.97f / height, 0.15f);
    }

    /**
     * A ring lying flat round {@code centre}: the band from {@code radius - width / 2} to {@code radius + width / 2},
     * v across it (0 inside, 1 outside), so a picture bright across its middle (the beam) draws a crisp line.
     */
    static void ring(PoseStack pose, MultiBufferSource buffers, RenderType type, Vec3 centre, float radius, float width, int rgb, float alpha,
                     float u0, float uTurns) {
        if (alpha <= 0.004f || radius <= 0.01f || width <= 0.005f) return;
        int n = Mth.clamp((int) (radius * 10), 20, 72);
        Vec3[] in = new Vec3[n + 1], out = new Vec3[n + 1];
        float[] u = new float[n + 1], al = new float[n + 1];
        float r0 = Math.max(0, radius - width / 2), r1 = radius + width / 2;
        for (int i = 0; i <= n; i++) {
            double th = Math.PI * 2 * i / n;
            double cs = Math.cos(th), sn = Math.sin(th);
            in[i] = centre.add(cs * r0, 0, sn * r0);
            out[i] = centre.add(cs * r1, 0, sn * r1);
            u[i] = u0 + uTurns * i / n;
            al[i] = alpha;
        }
        strip(pose, buffers, type, in, out, u, al, rgb);
    }

    /**
     * A round plate in the pose's XY plane (facing +z) of radius {@code radius}: the picture laid over it flat,
     * {@code tiles} across, shifted by {@code u0}; alpha {@code alpha} in the middle, {@code edge} times that at the rim.
     */
    static void disk(PoseStack pose, MultiBufferSource buffers, RenderType type, float radius, int rgb, float alpha, float edge, float tiles,
                     float u0, float v0) {
        if (alpha <= 0.004f || radius <= 0.01f) return;
        int n = 28, rings = 4;
        VertexConsumer out = buffers.getBuffer(type);
        Matrix4f m = pose.last().pose();
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        for (int k = 0; k < rings; k++) {
            float ra = radius * k / rings, rb = radius * (k + 1) / rings;
            float aa = alpha * Mth.lerp(k / (float) rings, 1, edge), ab = alpha * Mth.lerp((k + 1) / (float) rings, 1, edge);
            for (int i = 0; i < n; i++) {
                double t0 = Math.PI * 2 * i / n, t1 = Math.PI * 2 * (i + 1) / n;
                float x0a = (float) Math.cos(t0) * ra, y0a = (float) Math.sin(t0) * ra, x1a = (float) Math.cos(t1) * ra, y1a = (float) Math.sin(t1) * ra;
                float x0b = (float) Math.cos(t0) * rb, y0b = (float) Math.sin(t0) * rb, x1b = (float) Math.cos(t1) * rb, y1b = (float) Math.sin(t1) * rb;
                float s = tiles / (2 * radius);
                vertex(out, m, x0a, y0a, 0, u0 + x0a * s, v0 + y0a * s, r, g, b, aa);
                vertex(out, m, x0b, y0b, 0, u0 + x0b * s, v0 + y0b * s, r, g, b, ab);
                vertex(out, m, x1b, y1b, 0, u0 + x1b * s, v0 + y1b * s, r, g, b, ab);
                vertex(out, m, x1a, y1a, 0, u0 + x1a * s, v0 + y1a * s, r, g, b, aa);
            }
        }
    }

    /** A picture facing the camera at {@code at}, {@code half} from its middle to an edge, turned by {@code roll} degrees. */
    static void billboard(PoseStack pose, MultiBufferSource buffers, Camera camera, RenderType type, Vec3 at, float half, int rgb, float alpha, float roll) {
        if (alpha <= 0.004f || half <= 0.002f) return;
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(camera.rotation());
        if (roll != 0) pose.mulPose(Axis.ZP.rotationDegrees(roll));
        quad(buffers.getBuffer(type), pose.last().pose(), half, rgb, alpha);
        pose.popPose();
    }

    /** A picture lying flat at {@code at} (a little above it), turned by {@code roll} degrees about the vertical. */
    static void decal(PoseStack pose, MultiBufferSource buffers, RenderType type, Vec3 at, float half, int rgb, float alpha, float roll) {
        if (alpha <= 0.004f || half <= 0.002f) return;
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(Axis.YP.rotationDegrees(roll));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        quad(buffers.getBuffer(type), pose.last().pose(), half, rgb, alpha);
        pose.popPose();
    }

    /**
     * A burst of hard spikes of light out of {@code at} in the plane of the screen, the way a blow lands in a comic:
     * {@code rays} of them, up to {@code length} long and {@code width} wide at the root.
     */
    static void spikes(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 at, long seed, int rays, float length, float width,
                       int rgb, float alpha) {
        if (alpha <= 0.004f || length <= 0.01f) return;
        Vector3f cl = camera.getLeftVector(), cu = camera.getUpVector();
        Vec3 left = new Vec3(cl.x(), cl.y(), cl.z()), up = new Vec3(cu.x(), cu.y(), cu.z());
        RandomSource r = RandomSource.create(seed);
        VertexConsumer out = buffers.getBuffer(ModRenderTypes.energy(RAY));
        Matrix4f m = pose.last().pose();
        int red = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        float turn = r.nextFloat() * Mth.TWO_PI;
        for (int i = 0; i < rays; i++) {
            float ang = turn + (i + (r.nextFloat() - 0.5f) * 0.7f) * Mth.TWO_PI / rays;
            float len = length * (i % 2 == 0 ? 0.65f + 0.35f * r.nextFloat() : 0.3f + 0.3f * r.nextFloat());
            Vec3 dir = left.scale(Mth.cos(ang)).add(up.scale(Mth.sin(ang)));
            Vec3 across = left.scale(-Mth.sin(ang)).add(up.scale(Mth.cos(ang))).scale(width * (0.6f + 0.4f * len / length) / 2);
            Vec3 tip = at.add(dir.scale(len));
            Vec3 root = at.add(dir.scale(len * 0.04));
            vertex(out, m, root.x - across.x, root.y - across.y, root.z - across.z, 0, 1, red, g, b, alpha);
            vertex(out, m, tip.x, tip.y, tip.z, 1, 1, red, g, b, alpha);
            vertex(out, m, tip.x, tip.y, tip.z, 1, 0, red, g, b, alpha);
            vertex(out, m, root.x + across.x, root.y + across.y, root.z + across.z, 0, 0, red, g, b, alpha);
        }
    }

    /**
     * A bolt of lightning from a to b: the line broken again and again into a crooked path ({@code jag} how far it
     * strays, as a share of each piece), forking now and then ({@code forks} the chance at each bend). Re-rolled
     * whenever {@code seed} changes. Drawn as a colour sheath with a white-hot core, a glow round it.
     */
    static void lightning(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, Vec3 a, Vec3 b, long seed, float width,
                          int rgb, float alpha, float jag, float forks) {
        if (alpha <= 0.004f) return;
        RandomSource r = RandomSource.create(seed);
        List<Vec3> path = crooked(a, b, r, jag);
        List<List<Vec3>> branches = new ArrayList<>();
        Vec3 dir = b.subtract(a);
        for (int i = 2; i < path.size() - 2; i++) {
            if (r.nextFloat() >= forks) continue;
            Vec3 at = path.get(i);
            double rest = b.distanceTo(at);
            Vec3 off = new Vec3(r.nextDouble() - 0.5, r.nextDouble() - 0.5, r.nextDouble() - 0.5).normalize();
            Vec3 end = at.add(dir.normalize().scale(rest * (0.25 + 0.3 * r.nextDouble()))).add(off.scale(rest * (0.2 + 0.25 * r.nextDouble())));
            branches.add(crooked(at, end, r, jag * 1.1f));
        }
        int glow = SkillWorldFx.mix(rgb, 0xFFFFFF, 0.2f);
        // the glow round it, then the bolt, then its forks, thinner
        bolt(pose, buffers, camera, origin, ModRenderTypes.lightTexture(SkillWorldFx.STREAK), path, width * 3.2f, glow, alpha * 0.45f);
        bolt(pose, buffers, camera, origin, ModRenderTypes.energy(BOLT), path, width, rgb, alpha);
        for (List<Vec3> br : branches) bolt(pose, buffers, camera, origin, ModRenderTypes.energy(BOLT), br, width * 0.55f, rgb, alpha * 0.85f);
    }

    private static void bolt(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, RenderType type, List<Vec3> path,
                             float width, int rgb, float alpha) {
        int n = path.size();
        Vec3[] pts = path.toArray(new Vec3[0]);
        float[] w = new float[n], al = new float[n];
        for (int i = 0; i < n; i++) {
            float k = i / (float) (n - 1);
            // thinning towards the far end, and a little at the start
            w[i] = width * (0.55f + 0.45f * (1 - k)) * Math.min(1, 0.5f + k * 6);
            al[i] = alpha;
        }
        ribbon(pose, buffers, camera, origin, type, pts, w, al, rgb, 0, 1.5f);
    }

    /** A crooked path from a to b by breaking each piece at its middle and pushing the middle aside. */
    static List<Vec3> crooked(Vec3 a, Vec3 b, RandomSource r, float jag) {
        List<Vec3> pts = new ArrayList<>();
        pts.add(a);
        pts.add(b);
        double len = a.distanceTo(b);
        int depth = Mth.clamp((int) Math.ceil(Math.log(Math.max(1, len / 0.35)) / Math.log(2)), 2, 6);
        for (int d = 0; d < depth; d++) {
            List<Vec3> next = new ArrayList<>(pts.size() * 2);
            for (int i = 0; i < pts.size() - 1; i++) {
                Vec3 p = pts.get(i), q = pts.get(i + 1);
                Vec3 seg = q.subtract(p);
                double l = seg.length();
                Vec3 mid = p.add(seg.scale(0.5));
                Vec3 rnd = new Vec3(r.nextDouble() - 0.5, r.nextDouble() - 0.5, r.nextDouble() - 0.5);
                // pushed aside, across the piece
                Vec3 across = rnd.subtract(seg.scale(rnd.dot(seg) / Math.max(1.0e-9, l * l)));
                next.add(p);
                next.add(mid.add(across.scale(jag * l * 1.6)));
            }
            next.add(pts.get(pts.size() - 1));
            pts = next;
        }
        return pts;
    }

    /**
     * A spear of energy flying along {@code dir} with its point at {@code head}: {@code length} long, {@code width}
     * at its thickest, a little behind the point, thinning to the tail; and the trail it leaves, {@code trail} long.
     */
    static void lance(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, Vec3 head, Vec3 dir, float length, float width,
                      float trail, Look look, int c, float alpha, float scroll) {
        if (alpha <= 0.004f) return;
        Vec3 d = dir.normalize();
        Vec3 tail = head.subtract(d.scale(length));
        int body = look.body(c), light = look.light(c);
        if (trail > 0.05f) {
            Vec3 end = tail.subtract(d.scale(trail));
            line(pose, buffers, camera, origin, ModRenderTypes.energy(look.skin()), end, tail, 8, width * 1.3f, k -> 0.25f + 0.75f * k,
                    alpha * 0.7f, k -> k * k, body, scroll, 0.6f);
            line(pose, buffers, camera, origin, ModRenderTypes.lightTexture(SkillWorldFx.STREAK), end, tail, 6, width * 2.4f, k -> 0.3f + 0.7f * k,
                    alpha * 0.4f, k -> k, light, 0, 0);
        }
        Profile spear = k -> k < 0.82f ? 0.35f + 0.65f * k / 0.82f : (1 - k) / 0.18f;
        line(pose, buffers, camera, origin, ModRenderTypes.energy(BEAM), tail, head, 8, width, spear, alpha, FLAT, body, scroll * 2, 0);
        line(pose, buffers, camera, origin, ModRenderTypes.energy(BEAM), tail, head, 8, width * 0.45f, spear, alpha, FLAT, light, scroll * 2, 0);
    }

    // ---- low level ----

    /** How much of a surface with normal {@code n} shows seen along {@code toEye}: see {@link #tube}'s rim. */
    private static float fresnel(Vec3 n, Vec3 toEye, float rim) {
        if (rim == 0) return 1;
        double l = toEye.length();
        float f = l < 1.0e-6 ? 1 : (float) Math.abs(n.dot(toEye) / l);
        if (rim > 0) {
            float edge = 0.18f + 1.25f * (float) Math.pow(1 - f, 1.6);
            return Mth.lerp(rim, 1, edge);
        }
        return Mth.lerp(-rim, 1, f);
    }

    /** A shell the camera is inside of or right against fades, so a blast round the caster does not fill the view. */
    private static float near(Vec3 eye, Vec3 p, float size) {
        double d = eye.distanceTo(p);
        return (float) Mth.clamp((d - 0.3) / (0.6 + size * 0.2), 0, 1);
    }

    private static void quad(VertexConsumer out, Matrix4f m, float half, int rgb, float alpha) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        vertex(out, m, -half, -half, 0, 0, 1, r, g, b, alpha);
        vertex(out, m, half, -half, 0, 1, 1, r, g, b, alpha);
        vertex(out, m, half, half, 0, 1, 0, r, g, b, alpha);
        vertex(out, m, -half, half, 0, 0, 0, r, g, b, alpha);
    }

    static void vertex(VertexConsumer out, Matrix4f m, double x, double y, double z, float u, float v, int r, int g, int b, float a) {
        out.addVertex(m, (float) x, (float) y, (float) z).setUv(u, v).setColor(r, g, b, Mth.clamp(Math.round(a * 255), 0, 255));
    }

    private Skins() {}
}
