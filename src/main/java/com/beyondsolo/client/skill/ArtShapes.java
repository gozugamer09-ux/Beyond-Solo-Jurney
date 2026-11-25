package com.beyondsolo.client.skill;

import com.beyondsolo.client.render.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Shapes the weapon arts need beyond the skill skins: crystal spikes (ice, thorns, dark glass), a block of energy (a
 * hammer's head of light), a bent ribbon (branches, roots, coils), a helix of wind, crossed cuts, jaws of fire.
 * Positions are relative to the pose's origin, which sits at {@code origin} in the world, as in {@link Skins}.
 */
final class ArtShapes {

    // ------------------------------------------------------------------ crystal spikes

    /** Crystal spikes gathered first and drawn together: all faces in one batch, then all glows in another. */
    static final class Crystals {
        private final List<float[]> faces = new ArrayList<>();
        private final List<float[]> glows = new ArrayList<>();

        /**
         * A four-sided spike from {@code base} to {@code tip}, {@code half} wide at its foot, turned {@code turn}
         * radians about its own axis: faces {@code body} (the lit ones {@code lit}), the point {@code point}, and a
         * thin glow of {@code glow} round it.
         */
        Crystals add(Vec3 base, Vec3 tip, float half, int body, int lit, int point, int glow, float alpha, float turn) {
            if (alpha <= 0.01f || half <= 0.005f) return this;
            Vec3 axis = tip.subtract(base);
            double len = axis.length();
            if (len < 0.02) return this;
            Vec3 a = axis.scale(1 / len);
            Vec3 ref = Math.abs(a.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
            Vec3 u = a.cross(ref).normalize();
            Vec3 v = a.cross(u).normalize();
            Vec3[] ring = new Vec3[4];
            for (int i = 0; i < 4; i++) {
                double ang = turn + i * Math.PI / 2;
                ring[i] = base.add(u.scale(Math.cos(ang) * half)).add(v.scale(Math.sin(ang) * half));
            }
            // a waist a third of the way up makes it a crystal and not a cone
            Vec3 mid = base.add(axis.scale(0.3));
            Vec3[] waist = new Vec3[4];
            for (int i = 0; i < 4; i++) waist[i] = mid.add(ring[i].subtract(base).scale(1.25));
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                int col = i % 2 == 0 ? lit : body;
                tri(faces, ring[i], ring[j], waist[j], col, alpha * 0.9f, col, alpha * 0.9f, col, alpha * 0.9f);
                tri(faces, ring[i], waist[j], waist[i], col, alpha * 0.9f, col, alpha * 0.9f, col, alpha * 0.9f);
                tri(faces, waist[i], waist[j], tip, col, alpha * 0.9f, col, alpha * 0.9f, point, alpha);
            }
            float g = alpha * 0.35f;
            Vec3 gtip = tip.add(a.scale(0.08));
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                Vec3 w0 = mid.add(waist[i].subtract(mid).scale(1.35)), w1 = mid.add(waist[j].subtract(mid).scale(1.35));
                tri(glows, w0, w1, gtip, glow, g, glow, g, point, g * 1.2f);
            }
            return this;
        }

        private static void tri(List<float[]> out, Vec3 a, Vec3 b, Vec3 c, int ca, float aa, int cb, float ab, int cc, float ac) {
            out.add(new float[]{(float) a.x, (float) a.y, (float) a.z, ca, aa});
            out.add(new float[]{(float) b.x, (float) b.y, (float) b.z, cb, ab});
            out.add(new float[]{(float) c.x, (float) c.y, (float) c.z, cc, ac});
        }

        void draw(PoseStack pose, MultiBufferSource buffers) {
            Matrix4f m = pose.last().pose();
            if (!faces.isEmpty()) {
                VertexConsumer out = buffers.getBuffer(ModRenderTypes.CRYSTAL);
                for (float[] v : faces) put(out, m, v);
            }
            if (!glows.isEmpty()) {
                VertexConsumer out = buffers.getBuffer(ModRenderTypes.CRYSTAL_GLOW);
                for (float[] v : glows) put(out, m, v);
            }
        }

        private static void put(VertexConsumer out, Matrix4f m, float[] v) {
            int rgb = (int) v[3];
            int a = Mth.clamp(Math.round(Math.min(1f, v[4]) * 255), 0, 255);
            out.addVertex(m, v[0], v[1], v[2]).setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, a);
        }
    }

    // ------------------------------------------------------------------ blocks and bent ribbons

    /**
     * A block of energy centred on {@code c}, its half sides along {@code ax}, {@code ay} and {@code az}: each face
     * shows the whole picture once, so a skin with a bright edge (the wall, the hexes) outlines it.
     */
    static void box(PoseStack pose, MultiBufferSource buffers, RenderType type, Vec3 c, Vec3 ax, Vec3 ay, Vec3 az, int rgb, float alpha) {
        if (alpha <= 0.01f) return;
        VertexConsumer out = buffers.getBuffer(type);
        Matrix4f m = pose.last().pose();
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        Vec3[][] faces = {
                {ax, ay, az}, {ax.scale(-1), ay, az.scale(-1)}, {az, ay, ax.scale(-1)}, {az.scale(-1), ay, ax},
                {ay, az, ax}, {ay.scale(-1), az, ax.scale(-1)}};
        for (Vec3[] f : faces) {
            Vec3 n = f[0], up = f[1], side = f[2];
            Vec3 o = c.add(n);
            Vec3 p0 = o.subtract(side).subtract(up), p1 = o.add(side).subtract(up), p2 = o.add(side).add(up), p3 = o.subtract(side).add(up);
            Skins.vertex(out, m, p0.x, p0.y, p0.z, 0, 1, r, g, b, alpha);
            Skins.vertex(out, m, p1.x, p1.y, p1.z, 1, 1, r, g, b, alpha);
            Skins.vertex(out, m, p2.x, p2.y, p2.z, 1, 0, r, g, b, alpha);
            Skins.vertex(out, m, p3.x, p3.y, p3.z, 0, 0, r, g, b, alpha);
        }
    }

    /** Points along a curve from a through the pull of {@code ctrl} to b (a quadratic Bezier), {@code n + 1} of them. */
    static Vec3[] curve(Vec3 a, Vec3 ctrl, Vec3 b, int n) {
        Vec3[] pts = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            double k = i / (double) n;
            Vec3 ab = a.lerp(ctrl, k), bc = ctrl.lerp(b, k);
            pts[i] = ab.lerp(bc, k);
        }
        return pts;
    }

    /**
     * A ribbon along {@code pts} shown only up to {@code grow} (0..1) of its length, {@code w0} wide at its start and
     * {@code w1} at its end, fading towards its tip by {@code tipFade}.
     */
    static void bent(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, RenderType type, Vec3[] pts, float grow,
                     float w0, float w1, float alpha, float tipFade, int rgb, float u0, float uPerBlock) {
        if (grow <= 0.01f || alpha <= 0.01f) return;
        int n = pts.length - 1;
        float upto = grow * n;
        int last = Math.min(n, (int) Math.ceil(upto));
        if (last < 1) return;
        Vec3[] p = new Vec3[last + 1];
        float[] w = new float[last + 1], a = new float[last + 1];
        for (int i = 0; i <= last; i++) {
            float k = i / (float) n;
            p[i] = i == last && upto < last ? pts[i - 1].lerp(pts[i], upto - (last - 1)) : pts[i];
            float along = Math.min(k, grow);
            w[i] = Mth.lerp(along, w0, w1);
            a[i] = alpha * (1 - tipFade * (along / Math.max(0.01f, grow)) * (along / Math.max(0.01f, grow)));
        }
        Skins.ribbon(pose, buffers, camera, origin, type, p, w, a, rgb, u0, uPerBlock);
    }

    /**
     * A coil of {@code turns} turns round the line from a to b, {@code radius} out (following {@code radiusAlong}),
     * turned by {@code phase} radians.
     */
    static Vec3[] helix(Vec3 a, Vec3 b, double radius, Skins.Profile radiusAlong, double turns, double phase, int n) {
        Vec3 axis = b.subtract(a);
        Vec3 dir = axis.normalize();
        Vec3 ref = Math.abs(dir.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = dir.cross(ref).normalize(), v = dir.cross(u).normalize();
        Vec3[] pts = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            float k = i / (float) n;
            double ang = phase + k * turns * Math.PI * 2;
            double r = radius * radiusAlong.at(k);
            pts[i] = a.add(axis.scale(k)).add(u.scale(Math.cos(ang) * r)).add(v.scale(Math.sin(ang) * r));
        }
        return pts;
    }

    /** A jagged line from a to b on the ground plane (a fissure's path), the same for the same seed. */
    static List<Vec3> jagged(Vec3 a, Vec3 b, long seed, int segs, double stray) {
        RandomSource r = RandomSource.create(seed);
        Vec3 d = b.subtract(a);
        Vec3 side = new Vec3(-d.z, 0, d.x).normalize();
        List<Vec3> pts = new ArrayList<>();
        for (int i = 0; i <= segs; i++) {
            double k = i / (double) segs;
            double off = i == 0 || i == segs ? 0 : (r.nextDouble() - 0.5) * 2 * stray;
            pts.add(a.add(d.scale(k)).add(side.scale(off)));
        }
        return pts;
    }

    private ArtShapes() {}
}
