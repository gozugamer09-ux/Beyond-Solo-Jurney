package com.beyondsolo.client.skill;

import com.beyondsolo.client.render.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The bigger pieces skill effects are built from, all drawn with {@link Skins}: a blast, a shockwave running over the
 * ground, the ground split by glowing cracks, a scorch mark and smoke, a fountain of the element, power burning off a
 * body, a column of light, sound made visible, a barrier of hexes, a whirlwind and a sword of light.
 *
 * <p>Positions are relative to the pose's origin, which sits at {@code origin} in the world; {@code time} is the
 * game time in ticks (it turns and scrolls things), {@code age} seconds since the effect began.</p>
 */
final class SkinFx {

    // ---- blasts ----

    /**
     * An explosion at {@code at}, {@code age} seconds after it went off: a white flash and hard spikes at once, a ball
     * of the element swelling and burning away, a shockwave over the ground at {@code ground} (null: high in the air,
     * none), a scorch mark and smoke where it burned. {@code size} is its reach in blocks. {@code fill} 1 is a solid
     * fireball, 0 a hollow dome of force bright at its edges (a blast round the caster). Whatever is left of it (smoke,
     * the scorch) is gone by {@code end} seconds.
     */
    static void blast(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, Vec3 at, Vec3 ground, float size, float age,
                      float end, float fill, Skins.Look look, int c, long seed, float strength) {
        strength *= Mth.clamp((end - age) / 0.2f, 0, 1);
        if (age < 0 || strength <= 0.01f || size <= 0.01f) return;
        int body = look.body(c), light = look.light(c);
        float k = Mth.clamp(age / 0.6f, 0, 1);
        float o = easeOut(k);
        if (ground != null) {
            if (scorches(look)) scorch(pose, buffers, ground, size * 0.7f, strength * fadeOut(age, 1.0f, 0.6f), seed);
            shock(pose, buffers, camera, origin, ground, size * (0.3f + 1.0f * easeOut(age / 0.45f)), 0.35f + 0.3f * size,
                    strength * fadeOut(age, 0.12f, 0.45f), light, seed);
        }
        // the ball of the element
        float r = size * (0.28f + 0.6f * o);
        float a = strength * (1 - k) * (1 - 0.4f * k);
        float rim = Mth.lerp(fill, 0.95f, 0.3f);
        Skins.sphere(pose, buffers, camera, origin, ModRenderTypes.energy(look.skin()), at, r, 10, 18, a, body,
                (seed & 15) * 0.07f + age * 0.3f, 2, -age * 0.7f, 1, rim);
        if (fill > 0.3f) {
            Skins.sphere(pose, buffers, camera, origin, ModRenderTypes.energy(Skins.PLASMA), at, r * 0.72f, 8, 14, a * fill * (1 - k) * 0.7f, body,
                    age * 0.5f, 2, age * 0.4f, 1, -0.5f);
        }
        Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.ORB), at, r * (0.5f + 0.3f * fill), light, a * (0.3f + 0.35f * fill) * (1 - k), age * 70);
        // the flash
        if (age < 0.15f) {
            float f = 1 - age / 0.15f;
            Skins.spikes(pose, buffers, camera, at, seed, 12, size * (0.9f + 0.9f * (1 - f)), 0.25f + size * 0.12f, light, strength * f);
            Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), at, size * (0.6f + 0.5f * (1 - f)), 0xFFFFFF, strength * f, seed % 90);
        }
        if (smokes(look)) smoke(pose, buffers, camera, at, size, age, seed, look == Skins.Look.FIRE ? 0x5A4C40 : 0x9A8C78, strength);
    }

    /** A shockwave on the ground round {@code centre}: a short wall of light running out, a crisp line at its foot. */
    static void shock(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, Vec3 centre, float radius, float height, float alpha,
                      int rgb, long seed) {
        if (alpha <= 0.01f || radius <= 0.05f) return;
        Skins.wall(pose, buffers, camera, origin, centre.add(0, 0.02, 0), radius, height, alpha * 0.8f, rgb, (seed & 31) / 31f,
                Math.max(2, Math.round(radius * 1.3f)), Skins.FLAT);
        Skins.ring(pose, buffers, ModRenderTypes.energy(Skins.BEAM), centre.add(0, 0.04, 0), radius, 0.16f + radius * 0.05f, rgb, alpha,
                0, Math.max(2, radius * 1.5f));
    }

    /** A burnt patch on the ground. */
    static void scorch(PoseStack pose, MultiBufferSource buffers, Vec3 at, float half, float alpha, long seed) {
        Skins.decal(pose, buffers, ModRenderTypes.softTexture(Skins.SCORCH), at.add(0, 0.02, 0), half, 0xFFFFFF, alpha * 0.85f, seed % 360);
    }

    /** Puffs of smoke (or dust) rolling up and out of {@code at} after a blast of {@code size}, tinted {@code tint}. */
    static void smoke(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 at, float size, float age, long seed, int tint, float strength) {
        RandomSource r = RandomSource.create(seed * 31 + 7);
        for (int i = 0; i < 7; i++) {
            float delay = 0.06f + r.nextFloat() * 0.15f;
            float life = 0.9f + r.nextFloat() * 0.7f;
            double ang = r.nextDouble() * Math.PI * 2;
            double lift = 0.2 + r.nextDouble() * 0.8;
            float roll = r.nextFloat() * 360;
            float t = (age - delay) / life;
            if (t <= 0 || t >= 1) continue;
            float go = easeOut(t);
            Vec3 p = at.add(Math.cos(ang) * size * 0.55 * go, size * (0.15 + 0.45 * lift) * go + t * 0.6, Math.sin(ang) * size * 0.55 * go);
            float a = strength * 0.6f * Mth.clamp(t / 0.12f, 0, 1) * (1 - t) * (1 - t);
            Skins.billboard(pose, buffers, camera, ModRenderTypes.softTexture(Skins.SMOKE), p, size * (0.3f + 0.4f * go), tint, a, roll + t * 50);
        }
    }

    // ---- the ground ----

    /**
     * The ground split open round {@code centre} out to {@code reach}: {@code arms} crooked cracks glowing with the
     * element's light, {@code grow} (0..1) of the way out, light rising {@code rise} high off them.
     */
    static void cracks(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, Vec3 centre, long seed, float reach, int arms,
                       float grow, float glow, float rise, Skins.Look look, int c, float time) {
        if (glow <= 0.01f || grow <= 0.001f) return;
        RandomSource r = RandomSource.create(seed);
        int body = look.body(c), light = look.light(c);
        float width = 0.24f + reach * 0.035f;
        for (int i = 0; i < arms; i++) {
            double ang = i * Math.PI * 2 / arms + r.nextDouble() * 0.7;
            List<Vec3> pts = new ArrayList<>();
            Vec3 at = centre.add(0, 0.035, 0);
            pts.add(at);
            int segs = 7;
            float far = reach * (0.7f + 0.3f * r.nextFloat());
            for (int s = 1; s <= segs; s++) {
                ang += (r.nextDouble() - 0.5) * 0.75;
                double step = far / segs * (0.75 + r.nextDouble() * 0.5);
                at = at.add(Math.cos(ang) * step, 0, Math.sin(ang) * step);
                pts.add(at);
            }
            int fork = 2 + r.nextInt(3);
            double forkTurn = (r.nextBoolean() ? 1 : -1) * (0.6 + r.nextDouble() * 0.5);
            crack(pose, buffers, pts, grow, width, glow, rise, body, light, time + i * 13);
            // a branch off its middle
            if (fork < pts.size() - 1) {
                List<Vec3> br = new ArrayList<>();
                Vec3 b = pts.get(fork);
                br.add(b);
                double ba = ang + forkTurn;
                for (int s = 0; s < 3; s++) {
                    ba += (r.nextDouble() - 0.5) * 0.6;
                    b = b.add(Math.cos(ba) * far / segs * 0.8, 0, Math.sin(ba) * far / segs * 0.8);
                    br.add(b);
                }
                float late = fork / (float) segs;
                crack(pose, buffers, br, Mth.clamp((grow - late) / (1 - late), 0, 1), width * 0.6f, glow, rise * 0.6f, body, light, time + i * 7);
            }
        }
    }

    /** One crack along a line of points (not a star of them), as {@link #cracks} draws each of its arms. */
    static void fault(PoseStack pose, MultiBufferSource buffers, List<Vec3> pts, float grow, float width, float glow, float rise,
                      Skins.Look look, int c, float time) {
        crack(pose, buffers, pts, grow, width, glow, rise, look.body(c), look.light(c), time);
    }

    /** One crack along {@code pts}, {@code grow} of it shown: molten in the middle, light standing up off it. */
    private static void crack(PoseStack pose, MultiBufferSource buffers, List<Vec3> pts, float grow, float width, float glow, float rise,
                              int body, int light, float time) {
        if (grow <= 0.001f || pts.size() < 2) return;
        double total = 0;
        for (int i = 1; i < pts.size(); i++) total += pts.get(i).distanceTo(pts.get(i - 1));
        double show = total * grow;
        List<Vec3> keep = new ArrayList<>();
        keep.add(pts.get(0));
        double run = 0;
        for (int i = 1; i < pts.size() && run < show; i++) {
            Vec3 a = pts.get(i - 1), b = pts.get(i);
            double l = a.distanceTo(b);
            if (run + l > show) {
                keep.add(a.lerp(b, (show - run) / l));
                run = show;
            } else {
                keep.add(b);
                run += l;
            }
        }
        int n = keep.size();
        if (n < 2) return;
        Vec3[] left = new Vec3[n], right = new Vec3[n], top = new Vec3[n], foot = new Vec3[n];
        float[] u = new float[n], al = new float[n], up = new float[n];
        double along = 0;
        for (int i = 0; i < n; i++) {
            Vec3 p = keep.get(i);
            Vec3 d = keep.get(Math.min(n - 1, i + 1)).subtract(keep.get(Math.max(0, i - 1)));
            Vec3 side = new Vec3(-d.z, 0, d.x);
            side = side.lengthSqr() < 1.0e-8 ? new Vec3(1, 0, 0) : side.normalize();
            if (i > 0) along += p.distanceTo(keep.get(i - 1));
            float k = (float) (along / total);
            // wide at the heart, thinning to nothing at its far end
            float w = width * (1 - 0.8f * k) * (i == n - 1 && grow < 1 ? 0.6f : 1);
            left[i] = p.add(side.scale(w / 2));
            right[i] = p.subtract(side.scale(w / 2));
            u[i] = (float) along * 0.8f + time * 0.01f;
            al[i] = glow * Math.min(1, (1 - k) * 3 + 0.15f);
            top[i] = p.add(0, rise * (1 - 0.7f * k), 0);
            foot[i] = p;
            up[i] = al[i] * 0.55f;
        }
        Skins.strip(pose, buffers, ModRenderTypes.energy(Skins.FISSURE), left, right, u, al, body);
        Skins.strip(pose, buffers, ModRenderTypes.energy(Skins.FISSURE), left, right, u, al, light, 0.2f, 0.8f);
        if (rise > 0.02f) Skins.strip(pose, buffers, ModRenderTypes.energy(Skins.WALL), top, foot, u, up, light, 0.015f, 0.985f);
    }

    // ---- fountains, columns, flames ----

    /**
     * A fountain of the element bursting out of the ground at {@code base}: a column {@code height} tall shooting up
     * and sinking back, a crown of jets arching out of its top, spray, and a shock at its foot.
     */
    static void geyser(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, Vec3 base, float height, float width, float age,
                       float life, Skins.Look look, int c, long seed, float time) {
        float up = easeOut(age / 0.18f);
        float a = Mth.clamp(age / 0.04f, 0, 1) * fadeOut(age, life * 0.55f, life * 0.45f);
        if (a <= 0.01f) return;
        int body = look.body(c), light = look.light(c);
        float sink = 1 - 0.45f * Mth.clamp((age - life * 0.5f) / (life * 0.5f), 0, 1);
        float h = height * up * sink;
        Vec3 top = base.add(0, h, 0);
        boolean air = look == Skins.Look.AIR;
        // the column: thick at its foot, opening a little where it breaks at the top
        Skins.tube(pose, buffers, camera, origin, ModRenderTypes.energy(look.skin()), base.add(0, -0.1, 0), top, 12, 8,
                k -> width * (0.7f + 0.45f * k * k), k -> Math.min(1, (1 - k) * 4 + 0.25f), a * (air ? 0.7f : 1f), body,
                (seed & 7) * 0.13f, 2, air ? 0.8f : 0.15f, -time * 0.1f, 0.3f, 0.45f);
        Skins.line(pose, buffers, camera, origin, ModRenderTypes.energy(Skins.BEAM), base, top.add(0, -h * 0.05, 0), 6, width * 0.9f,
                k -> 1 - 0.5f * k, a * (air ? 0.5f : 0.9f), k -> 1 - k * 0.5f, light, -time * 0.3f, 0.4f);
        // the crown: jets arching out of the top and falling away
        RandomSource r = RandomSource.create(seed);
        int jets = 7;
        for (int j = 0; j < jets; j++) {
            double ang = j * Math.PI * 2 / jets + r.nextDouble() * 0.6;
            float spread = width * (1.6f + 1.4f * r.nextFloat()) + height * 0.18f;
            float lift = height * (0.12f + 0.15f * r.nextFloat());
            float reach = Mth.clamp((age - 0.08f) / 0.4f, 0, 1);
            if (reach <= 0) continue;
            int m = 8;
            Vec3[] pts = new Vec3[m + 1];
            float[] w = new float[m + 1], al = new float[m + 1];
            for (int i = 0; i <= m; i++) {
                float s = i / (float) m * reach;
                pts[i] = top.add(Math.cos(ang) * spread * s, lift * (2 * s - 2.2 * s * s) - h * 0.1 * s, Math.sin(ang) * spread * s);
                w[i] = width * 0.55f * (1 - 0.6f * s);
                al[i] = a * 0.85f * (1 - s * s);
            }
            Skins.ribbon(pose, buffers, camera, origin, ModRenderTypes.energy(look.skin()), pts, w, al, body, -time * 0.05f + j, 0.6f);
        }
        // spray thrown out of it
        for (int i = 0; i < 10; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            float v = 0.6f + r.nextFloat();
            float tt = Mth.clamp(age / life, 0, 1);
            Vec3 p = top.add(Math.cos(ang) * width * 3 * v * tt, height * 0.3f * v * tt - height * 0.9f * tt * tt, Math.sin(ang) * width * 3 * v * tt);
            if (p.y < base.y) continue;
            Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), p, 0.12f + 0.08f * v, light, a * (1 - tt), i * 37);
        }
        shock(pose, buffers, camera, origin, base, width * (1.3f + 2.2f * easeOut(age / 0.35f)), 0.3f + width * 0.4f, a * fadeOut(age, 0.12f, 0.45f), light, seed);
    }

    /**
     * Power burning off a body standing at {@code feet}: tongues of the element's light rising round it from the
     * ground to above the head, swaying and licking upwards. {@code height} is the body's height, {@code radius} how
     * far round it they stand.
     */
    static void flames(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, Vec3 feet, float height, float radius, float alpha,
                       Skins.Look look, int c, long seed, float time) {
        if (alpha <= 0.01f) return;
        int body = look.body(c), light = look.light(c);
        RandomSource r = RandomSource.create(seed);
        int n = 10;
        for (int i = 0; i < n; i++) {
            float ang = i * Mth.TWO_PI / n + r.nextFloat() * 0.5f;
            float tall = height * (0.7f + 0.6f * r.nextFloat());
            float phase = r.nextFloat() * 10;
            float rr = radius * (0.85f + 0.3f * r.nextFloat());
            float thick = 0.32f + 0.18f * r.nextFloat();
            // each tongue grows and shrinks on its own beat
            float beat = 0.72f + 0.28f * Mth.sin(time * 0.33f + phase);
            int m = 8;
            Vec3[] pts = new Vec3[m + 1];
            float[] w = new float[m + 1], al = new float[m + 1];
            float cs = Mth.cos(ang), sn = Mth.sin(ang);
            for (int j = 0; j <= m; j++) {
                float k = j / (float) m;
                float sway = 0.14f * k * k * Mth.sin(time * 0.28f + phase + k * 3);
                // they lean in a little towards the head
                float in = rr * (1 - 0.35f * k);
                pts[j] = feet.add(cs * in - sn * sway, 0.05 + k * tall * beat, sn * in + cs * sway);
                w[j] = thick * (float) Math.pow(1 - k, 0.7) + 0.02f;
                al[j] = alpha * Mth.clamp(k / 0.12f, 0, 1) * (float) Math.pow(1 - k, 0.5);
            }
            Skins.ribbon(pose, buffers, camera, origin, ModRenderTypes.energy(Skins.AURA), pts, w, al, i % 3 == 0 ? light : body,
                    -time * 0.05f + phase, 0.7f);
        }
    }

    /**
     * A column of light standing on {@code base}, {@code height} tall and {@code radius} thick: a shell of the element
     * pouring down or up it, a white-hot core and a glow round it.
     */
    static void column(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, Vec3 base, float height, float radius, float alpha,
                       Skins.Look look, int c, float time) {
        if (alpha <= 0.01f || radius <= 0.01f) return;
        int body = look.body(c), light = look.light(c);
        Vec3 top = base.add(0, height, 0);
        Skins.Profile fadeUp = k -> 1 - 0.75f * k * k;
        Skins.line(pose, buffers, camera, origin, ModRenderTypes.lightTexture(SkillWorldFx.STREAK), base, top, 8, radius * 5f, Skins.FLAT,
                alpha * 0.45f, fadeUp, light, 0, 0);
        Skins.tube(pose, buffers, camera, origin, ModRenderTypes.energy(look.skin()), base.add(0, -0.2, 0), top, 14, 10,
                k -> radius * (1 + 0.1f * Mth.sin(time * 0.5f + k * 9)), fadeUp, alpha, body, time * 0.01f, 2, 0.1f, -time * 0.14f, 0.2f, 0.55f);
        Skins.line(pose, buffers, camera, origin, ModRenderTypes.energy(Skins.BEAM), base, top, 10, radius * 1.4f, Skins.FLAT, alpha, fadeUp,
                light, -time * 0.3f, 0.3f);
        Skins.line(pose, buffers, camera, origin, ModRenderTypes.energy(Skins.BEAM), base, top, 10, radius * 0.5f, Skins.FLAT, alpha, fadeUp,
                0xFFFFFF, -time * 0.45f, 0.4f);
    }

    // ---- shells, barriers, whirlwinds, swords ----

    /** Sound made visible: {@code n} shells of moving air running out of {@code at} one after another, bright only at their edges. */
    static void shells(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, Vec3 at, float reach, float age, int n, float gap,
                       Skins.Look look, int c, float strength) {
        for (int i = 0; i < n; i++) {
            float k = Mth.clamp((age - i * gap) / 0.6f, 0, 1);
            if (k <= 0 || k >= 1) continue;
            float o = easeOut(k);
            Skins.sphere(pose, buffers, camera, origin, ModRenderTypes.energy(Skins.WIND), at, 0.5f + reach * o, 10, 20,
                    strength * (1 - k) * (1 - k * 0.3f), i % 2 == 0 ? look.light(c) : look.body(c), i * 0.37f + age * 0.2f, 3, 0.25f * i, 2, 1f);
        }
    }

    /**
     * A barrier of hexes round {@code centre}, {@code radius} big: a ball of the element's light, bright at its edges
     * where it is seen side-on; on the ground (its lower half under it) a dome with a line of light round its foot.
     */
    static void barrier(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, Vec3 centre, float radius, float alpha, Vec3 ground,
                        Skins.Look look, int c, float time) {
        if (alpha <= 0.01f || radius <= 0.05f) return;
        int body = look.body(c), light = look.light(c);
        int around = Math.max(4, Math.round(radius * 3.2f));
        Skins.sphere(pose, buffers, camera, origin, ModRenderTypes.energy(Skins.PLASMA), centre, radius * 0.995f, 12, 22, alpha * 0.35f, body,
                time * 0.004f, 2, -time * 0.005f, 1, 0.75f);
        Skins.sphere(pose, buffers, camera, origin, ModRenderTypes.energy(Skins.HEX), centre, radius, 14, 24, alpha, light,
                time * 0.0015f, around, time * 0.002f, around / 2f, 0.85f);
        if (ground != null) {
            double dy = ground.y - centre.y;
            if (Math.abs(dy) < radius) {
                float foot = (float) Math.sqrt(radius * radius - dy * dy);
                Skins.ring(pose, buffers, ModRenderTypes.energy(Skins.BEAM), ground.add(0, 0.04, 0), foot, 0.2f + foot * 0.04f, light, alpha,
                        time * 0.01f, Math.max(2, foot * 1.5f));
            }
        }
    }

    /** A whirlwind standing on {@code base}: a funnel of turning air, narrow at the foot, a body of the element in it and bits caught up. */
    static void tornado(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, Vec3 base, float radius, float height, float alpha,
                        Skins.Look look, int c, long seed, float time) {
        if (alpha <= 0.01f || radius <= 0.01f) return;
        int body = look.body(c), light = look.light(c);
        Vec3 top = base.add(0, height, 0);
        Skins.Profile ends = k -> Math.min(1, k / 0.06f) * Math.min(1, (1 - k) / 0.3f);
        Skins.Profile funnel = k -> radius * (0.12f + 0.55f * k * k + 0.25f * k) * (1 + 0.06f * Mth.sin(time * 0.4f + k * 7));
        // the body of the element turning in it
        Skins.tube(pose, buffers, camera, origin, ModRenderTypes.energy(look.skin()), base, top, 16, 10, k -> funnel.at(k) * 0.8f, ends,
                alpha * 0.4f, body, -time * 0.05f, 2, 0.6f, -time * 0.02f, 0.25f, 0.7f);
        // the wind: two sheets of air streaming round, the outer slower
        for (int layer = 0; layer < 2; layer++) {
            float grow = layer == 0 ? 1f : 0.82f;
            Skins.tube(pose, buffers, camera, origin, ModRenderTypes.energy(Skins.WIND), base, top, 18, 12, k -> funnel.at(k) * grow, ends,
                    alpha * (layer == 0 ? 0.55f : 0.75f), layer == 0 ? body : light, -time * (0.06f + 0.05f * layer), 4, 0.35f, 0, 0.6f, 0.85f);
        }
        // bits caught up in it
        RandomSource r = RandomSource.create(seed);
        for (int i = 0; i < 14; i++) {
            float speed = 0.1f + 0.08f * r.nextFloat();
            float ang = r.nextFloat() * Mth.TWO_PI - time * speed;
            float y = (r.nextFloat() + time * 0.008f * (1 + r.nextFloat())) % 1f;
            float rr = funnel.at(y) * (1.0f + 0.2f * r.nextFloat());
            Vec3 p = base.add(Mth.cos(ang) * rr, y * height, Mth.sin(ang) * rr);
            Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), p, 0.1f + 0.08f * r.nextFloat(), light,
                    alpha * Math.min(1, (1 - y) * 3), i * 41);
        }
    }

    /**
     * A sword of light standing point down on {@code tip}: a blade {@code len} long and {@code width} wide with a
     * white-hot edge-to-edge core, its cross-guard along {@code across} and a short grip above it.
     */
    static void sword(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, Vec3 tip, float len, float width, Vec3 across,
                      float alpha, Skins.Look look, int c, float time) {
        if (alpha <= 0.01f) return;
        int body = look.body(c), light = look.light(c);
        Vec3 guard = tip.add(0, len, 0);
        Skins.Profile blade = k -> k < 0.14f ? 0.15f + 0.85f * k / 0.14f : 1 - 0.18f * (k - 0.14f);
        Skins.line(pose, buffers, camera, origin, ModRenderTypes.lightTexture(SkillWorldFx.STREAK), tip, guard, 6, width * 3.2f, blade, alpha * 0.5f,
                Skins.FLAT, light, 0, 0);
        Skins.line(pose, buffers, camera, origin, ModRenderTypes.energy(Skins.BEAM), tip, guard, 10, width, blade, alpha, Skins.FLAT, body,
                -time * 0.2f, 0.25f);
        Skins.line(pose, buffers, camera, origin, ModRenderTypes.energy(Skins.BEAM), tip, guard, 10, width * 0.42f, blade, alpha, Skins.FLAT,
                0xFFFFFF, -time * 0.3f, 0.3f);
        Vec3 side = across.normalize().scale(width * 1.5f);
        Skins.line(pose, buffers, camera, origin, ModRenderTypes.energy(Skins.BEAM), guard.subtract(side), guard.add(side), 4, width * 0.5f,
                k -> 0.6f + 0.4f * Mth.sin(k * Mth.PI), alpha, Skins.FLAT, light, 0, 0);
        Vec3 pommel = guard.add(0, len * 0.2f, 0);
        Skins.line(pose, buffers, camera, origin, ModRenderTypes.energy(Skins.BEAM), guard, pommel, 3, width * 0.32f, Skins.FLAT, alpha, Skins.FLAT,
                light, 0, 0);
        Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), pommel, width * 0.9f, light, alpha, time * 3);
    }

    // ---- small things ----

    static float easeOut(float k) {
        k = Mth.clamp(k, 0, 1);
        return 1 - (1 - k) * (1 - k) * (1 - k);
    }

    /** 1 until {@code start} seconds, then down to 0 over {@code span}. */
    static float fadeOut(float age, float start, float span) {
        return Mth.clamp(1 - (age - start) / Math.max(0.01f, span), 0, 1);
    }

    /** Whether a blast of this element leaves the ground burnt. */
    static boolean scorches(Skins.Look look) {
        return switch (look) {
            case FIRE, EARTH, PHYSICAL, LIGHTNING, SHADOW -> true;
            default -> false;
        };
    }

    /** Whether a blast of this element throws up smoke or dust. */
    static boolean smokes(Skins.Look look) {
        return look == Skins.Look.FIRE || look == Skins.Look.EARTH || look == Skins.Look.PHYSICAL;
    }

    private SkinFx() {}
}
