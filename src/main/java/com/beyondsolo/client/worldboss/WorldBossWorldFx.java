package com.beyondsolo.client.worldboss;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.gate.GateFx;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.network.WorldBossFxPayload;
import com.beyondsolo.worldboss.ColossalBoss;
import com.beyondsolo.worldboss.MonarchBoss;
import com.beyondsolo.worldboss.WorldBossKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The great threats' light in the world, drawn with the mod's light render type:
 * <ul>
 *   <li><b>Warnings on the ground</b> ({@link WorldBossFxPayload}): discs, bands, strips, wedges and squares that lie on
 *   the land as it is (a hillside, a riverbank, steps of stone: {@link Terrain}), fill from the middle as the blow comes
 *   and flash as it lands; waves of light that run out along the ground, a wall you can see to jump.</li>
 *   <li><b>Blows</b>: stones falling out of the sky, lightning, pillars of light, flashes, and cracks of light that run
 *   out over the ground from a heavy blow.</li>
 *   <li><b>The threat itself</b> ({@link WorldBossClient}): a column of light over a world boss seen from far off; the
 *   gate torn open in the sky that a monarch comes down through; a monarch's domain, a ring of its light on the land
 *   round it.</li>
 * </ul>
 * Runs on game time, so it pauses with the game and follows /tick rate.
 */
public final class WorldBossWorldFx {
    private static final ResourceLocation GLOW = BeyondSolo.id("textures/effect/glow.png");
    private static final ResourceLocation RING = BeyondSolo.id("textures/effect/ring.png");
    private static final ResourceLocation CIRCLE = BeyondSolo.id("textures/effect/magic_circle.png");
    private static final ResourceLocation BEAM = BeyondSolo.id("textures/effect/beam.png");
    private static final ResourceLocation VORTEX = BeyondSolo.id("textures/effect/vortex.png");
    /** Radial profiles (v from the middle to the edge, see tools/gen_worldboss_assets.py). */
    private static final ResourceLocation RIM = BeyondSolo.id("textures/effect/wb_rim.png");
    private static final ResourceLocation FILL = BeyondSolo.id("textures/effect/wb_fill.png");
    /** A band: bright at both edges (v 0 and 1). */
    private static final ResourceLocation BAND = BeyondSolo.id("textures/effect/wb_band.png");
    /** A soft line: bright along the middle (v 0.5). */
    private static final ResourceLocation LINE = BeyondSolo.id("textures/effect/wb_line.png");
    private static final ResourceLocation CELL = BeyondSolo.id("textures/effect/wb_cell.png");
    private static final ResourceLocation CELL_FILL = BeyondSolo.id("textures/effect/wb_cell_fill.png");
    private static final ResourceLocation[] ALL = {GLOW, RING, CIRCLE, BEAM, VORTEX, RIM, FILL, BAND, LINE, CELL, CELL_FILL};

    /** One warning or blow, with the land under it. */
    private static final class Fx {
        final WorldBossFxPayload p;
        final float start;
        final Terrain land;
        final long seed;
        /** A quake's cracks: x0, z0, x1, z1 (from its middle), distance of the start from the middle, width. */
        List<float[]> cracks;

        Fx(WorldBossFxPayload p, float start) {
            this.p = p;
            this.start = start;
            this.land = new Terrain(p.y());
            this.seed = Double.doubleToLongBits(p.x() * 31.7 + p.z() * 17.3 + start);
        }
    }

    private static final List<Fx> ACTIVE = new ArrayList<>();
    private static final Terrain DOMAIN_LAND = new Terrain(64);
    private static final Terrain BEACON_LAND = new Terrain(64);

    /** The camera, for vertices given in world coordinates. */
    private static double camX, camY, camZ;
    private static Matrix4f mat = new Matrix4f();
    private static Camera cam;

    public static void onFx(WorldBossFxPayload p) {
        if (Minecraft.getInstance().level == null) return;
        if (ACTIVE.size() > 600) ACTIVE.remove(0);
        ACTIVE.add(new Fx(p, now(0)));
    }

    static void clear() {
        ACTIVE.clear();
    }

    private static float now(float partial) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime() + partial;
    }

    private static float life(WorldBossFxPayload p) {
        return switch (p.kindEnum()) {
            case CIRCLE, RING, LINE, CONE, CELL -> p.ticks() + 8;
            case WAVE -> p.ticks() + 4;
            case METEOR -> p.ticks() + 1;
            default -> p.ticks();
        };
    }

    public static void render(PoseStack pose, Camera camera, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 c = camera.getPosition();
        camX = c.x;
        camY = c.y;
        camZ = c.z;
        cam = camera;
        mat = pose.last().pose();
        float time = now(partial);
        boolean any = false;

        Iterator<Fx> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Fx fx = it.next();
            float age = time - fx.start;
            if (age > life(fx.p) || age < -2) {
                it.remove();
                continue;
            }
            any = true;
            switch (fx.p.kindEnum()) {
                case CIRCLE -> circle(buffers, fx, age, time);
                case RING -> ring(buffers, fx, age);
                case WAVE -> wave(buffers, fx, age);
                case LINE -> line(buffers, fx, age);
                case CONE -> cone(buffers, fx, age);
                case CELL -> cell(buffers, fx, age);
                case METEOR -> meteor(buffers, fx, age);
                case BOLT -> bolt(buffers, fx, age);
                case PILLAR -> pillar(buffers, fx, age);
                case BURST -> burst(buffers, fx, age);
                case QUAKE -> quake(buffers, fx, age);
            }
        }
        any |= threat(buffers, time, partial);
        if (any) for (ResourceLocation tex : ALL) buffers.endBatch(ModRenderTypes.lightTexture(tex));
    }

    private static VertexConsumer buf(MultiBufferSource buffers, ResourceLocation tex) {
        return buffers.getBuffer(ModRenderTypes.lightTexture(tex));
    }

    // ================================================================ warnings on the ground

    /** 0..1 how full a warning is. */
    private static float fill(WorldBossFxPayload p, float age) {
        return Mth.clamp(age / Math.max(1, p.ticks()), 0, 1);
    }

    /** Its opacity: in over 3 ticks, out over the 8 after it lands. */
    private static float fade(WorldBossFxPayload p, float age) {
        float in = Mth.clamp(age / 3f, 0, 1);
        float out = age > p.ticks() ? 1 - (age - p.ticks()) / 8f : 1;
        return in * Mth.clamp(out, 0, 1);
    }

    /** The flash as it lands (1 at the moment, gone 8 ticks later). */
    private static float flash(WorldBossFxPayload p, float age) {
        return age > p.ticks() ? Mth.clamp(1 - (age - p.ticks()) / 8f, 0, 1) : 0;
    }

    /** It beats faster as the blow comes. */
    private static float urgency(float k, float time) {
        return 0.82f + 0.18f * Mth.sin(time * (0.35f + 0.9f * k * k));
    }

    private static void circle(MultiBufferSource buffers, Fx fx, float age, float time) {
        WorldBossFxPayload p = fx.p;
        float k = fill(p, age), a = fade(p, age), f = flash(p, age);
        int c = p.color();
        float r = p.size();
        float beat = urgency(k, time);
        annulus(buf(buffers, RIM), fx.land, p.x(), p.z(), 0, r, 0, Mth.TWO_PI, 0.05f, c, 0.85f * a * beat);
        if (r > 2.5f) flatOnLand(buf(buffers, CIRCLE), fx.land, p.x(), p.z(), r * 0.92f, (time * 1.5f) % 360, 0.06f, c, 0.16f * a);
        if (k > 0.01f) annulus(buf(buffers, FILL), fx.land, p.x(), p.z(), 0, r * k, 0, Mth.TWO_PI, 0.07f, c, (0.32f + 0.3f * k) * a);
        if (f > 0) annulus(buf(buffers, FILL), fx.land, p.x(), p.z(), 0, r * (1 + 0.15f * (1 - f)), 0, Mth.TWO_PI, 0.08f, GateFx.mix(c, 0xFFFFFF, 0.55f), 0.9f * f);
    }

    private static void ring(MultiBufferSource buffers, Fx fx, float age) {
        WorldBossFxPayload p = fx.p;
        float k = fill(p, age), a = fade(p, age), f = flash(p, age);
        int c = p.color();
        float inner = p.extra(), outer = p.size();
        annulus(buf(buffers, BAND), fx.land, p.x(), p.z(), inner, outer, 0, Mth.TWO_PI, 0.05f, c, 0.8f * a);
        // the band fills from its inner edge outwards
        if (k > 0.01f) annulus(buf(buffers, FILL), fx.land, p.x(), p.z(), inner, inner + (outer - inner) * k, 0, Mth.TWO_PI, 0.07f, c, (0.3f + 0.3f * k) * a);
        if (f > 0) annulus(buf(buffers, FILL), fx.land, p.x(), p.z(), inner, outer, 0, Mth.TWO_PI, 0.08f, GateFx.mix(c, 0xFFFFFF, 0.55f), 0.9f * f);
    }

    private static void wave(MultiBufferSource buffers, Fx fx, float age) {
        WorldBossFxPayload p = fx.p;
        float k = Mth.clamp(age / Math.max(1, p.ticks()), 0, 1);
        float a = Mth.clamp(age / 2f, 0, 1) * (age > p.ticks() ? Mth.clamp(1 - (age - p.ticks()) / 4f, 0, 1) : 1);
        int c = p.color();
        float r = p.size() * k, band = Math.max(0.6f, p.extra());
        if (r < 0.3f) return;
        annulus(buf(buffers, BAND), fx.land, p.x(), p.z(), Math.max(0, r - band / 2), r + band / 2, 0, Mth.TWO_PI, 0.06f, c, 0.95f * a);
        annulus(buf(buffers, FILL), fx.land, p.x(), p.z(), Math.max(0, r - band * 1.6f), r, 0, Mth.TWO_PI, 0.07f, c, 0.35f * a);
        // a low wall of light where it runs: jump it
        wall(buf(buffers, BEAM), fx.land, p.x(), p.z(), r, 1.5f, c, 0.75f * a);
        wall(buf(buffers, BEAM), fx.land, p.x(), p.z(), r, 0.7f, GateFx.mix(c, 0xFFFFFF, 0.6f), 0.6f * a);
    }

    private static void line(MultiBufferSource buffers, Fx fx, float age) {
        WorldBossFxPayload p = fx.p;
        float k = fill(p, age), a = fade(p, age), f = flash(p, age);
        int c = p.color();
        float half = p.size() / 2, len = p.length();
        strip(buf(buffers, BAND), fx.land, p.x(), p.z(), p.dx(), p.dz(), 0, len, half, 0.05f, c, 0.8f * a, true);
        // the far end
        strip(buf(buffers, LINE), fx.land, p.x() + p.dx() * len, p.z() + p.dz() * len, -p.dz(), p.dx(), -half, half, 0.35f, 0.06f, c, 0.9f * a, true);
        if (k > 0.01f) strip(buf(buffers, FILL), fx.land, p.x(), p.z(), p.dx(), p.dz(), 0, len * k, half * 0.94f, 0.07f, c, (0.32f + 0.3f * k) * a, false);
        if (f > 0) strip(buf(buffers, FILL), fx.land, p.x(), p.z(), p.dx(), p.dz(), 0, len, half, 0.08f, GateFx.mix(c, 0xFFFFFF, 0.55f), 0.85f * f, false);
    }

    private static void cone(MultiBufferSource buffers, Fx fx, float age) {
        WorldBossFxPayload p = fx.p;
        float k = fill(p, age), a = fade(p, age), f = flash(p, age);
        int c = p.color();
        float half = p.size() * Mth.DEG_TO_RAD, len = p.length();
        float base = (float) Math.atan2(p.dz(), p.dx());
        annulus(buf(buffers, RIM), fx.land, p.x(), p.z(), 0, len, base - half, base + half, 0.05f, c, 0.85f * a);
        for (float side : new float[]{-half, half}) {
            float ang = base + side;
            strip(buf(buffers, LINE), fx.land, p.x(), p.z(), Mth.cos(ang), Mth.sin(ang), 0, len, 0.3f, 0.06f, c, 0.9f * a, true);
        }
        if (k > 0.01f) annulus(buf(buffers, FILL), fx.land, p.x(), p.z(), 0, len * k, base - half, base + half, 0.07f, c, (0.32f + 0.3f * k) * a);
        if (f > 0) annulus(buf(buffers, FILL), fx.land, p.x(), p.z(), 0, len, base - half, base + half, 0.08f, GateFx.mix(c, 0xFFFFFF, 0.55f), 0.85f * f);
    }

    private static void cell(MultiBufferSource buffers, Fx fx, float age) {
        WorldBossFxPayload p = fx.p;
        float k = fill(p, age), a = fade(p, age), f = flash(p, age);
        int c = p.color();
        float half = p.size();
        square(buf(buffers, CELL), fx.land, p.x(), p.z(), half, 1f, 0.05f, c, 0.9f * a);
        if (k > 0.01f) square(buf(buffers, CELL_FILL), fx.land, p.x(), p.z(), half, k, 0.07f, c, (0.3f + 0.35f * k) * a);
        if (f > 0) square(buf(buffers, CELL_FILL), fx.land, p.x(), p.z(), half, 1f, 0.08f, GateFx.mix(c, 0xFFFFFF, 0.55f), 0.9f * f);
    }

    // ================================================================ blows

    /** A stone of fire falling out of the sky onto the point, slanting in from one side. */
    private static void meteor(MultiBufferSource buffers, Fx fx, float age) {
        WorldBossFxPayload p = fx.p;
        float k = Mth.clamp(age / Math.max(1, p.ticks()), 0, 1);
        float fall = (float) Math.pow(k, 1.6);
        RandomSource r = RandomSource.create(fx.seed);
        float ang = r.nextFloat() * Mth.TWO_PI;
        double sx = p.x() + Mth.cos(ang) * 20, sy = p.y() + 44, sz = p.z() + Mth.sin(ang) * 20;
        double x = Mth.lerp(fall, sx, p.x()), y = Mth.lerp(fall, sy, p.y() + 0.5), z = Mth.lerp(fall, sz, p.z());
        Vec3 dir = new Vec3(p.x() - sx, p.y() - sy, p.z() - sz).normalize();
        int c = p.color();
        float s = p.size();
        float in = Mth.clamp(age / 4f, 0, 1);
        // the tail of fire behind it, then its glow and its white-hot heart
        ribbon(buf(buffers, LINE), x, y, z, x - dir.x * s * 9, y - dir.y * s * 9, z - dir.z * s * 9, s * 1.3f, s * 0.2f, c, 0.75f * in);
        ribbon(buf(buffers, LINE), x, y, z, x - dir.x * s * 5, y - dir.y * s * 5, z - dir.z * s * 5, s * 0.5f, s * 0.1f, GateFx.mix(c, 0xFFFFFF, 0.7f), 0.8f * in);
        billboard(buf(buffers, GLOW), x, y, z, s * 3.2f, 0, c, 0.65f * in);
        billboard(buf(buffers, GLOW), x, y, z, s * 1.2f, 0, GateFx.mix(c, 0xFFFFFF, 0.75f), 0.95f * in);
        for (int i = 1; i <= 6; i++) {
            float b = i / 6f;
            double tx = x - dir.x * s * 1.6 * i + Mth.sin(age * 0.9f + i) * 0.25, ty = y - dir.y * s * 1.6 * i, tz = z - dir.z * s * 1.6 * i;
            billboard(buf(buffers, GLOW), tx, ty, tz, s * (1.5f - b), 0, c, 0.45f * (1 - b) * in);
        }
        // its shadow of light on the ground grows as it comes
        flatOnLand(buf(buffers, GLOW), fx.land, p.x(), p.z(), s * (1.5f + 2.5f * k), 0, 0.09f, c, 0.35f * k);
    }

    /** Lightning onto the point: a jagged bolt from high above that flickers, forking. */
    private static void bolt(MultiBufferSource buffers, Fx fx, float age) {
        WorldBossFxPayload p = fx.p;
        float k = Mth.clamp(age / Math.max(1, p.ticks()), 0, 1);
        int frame = (int) (age / 2);
        RandomSource r = RandomSource.create(fx.seed + frame * 341L);
        float flicker = age < 2 ? 1 : 0.55f + 0.45f * r.nextFloat();
        float a = (1 - k * k) * flicker;
        int c = p.color(), hot = GateFx.mix(c, 0xFFFFFF, 0.8f);
        float s = Math.max(0.4f, p.size());
        double topX = p.x() + (r.nextFloat() - 0.5f) * 8, topY = p.y() + 42, topZ = p.z() + (r.nextFloat() - 0.5f) * 8;
        double[] pts = jagged(r, topX, topY, topZ, p.x(), p.y(), p.z(), 14, 1.6 * s);
        strand(buffers, pts, s * 1.5f, c, 0.55f * a);
        strand(buffers, pts, s * 0.3f, hot, a);
        for (int b = 0; b < 3; b++) {
            int from = 3 + r.nextInt(8);
            double fx0 = pts[from * 3], fy0 = pts[from * 3 + 1], fz0 = pts[from * 3 + 2];
            double ang = r.nextDouble() * Math.PI * 2;
            double len = 4 + r.nextDouble() * 6;
            double[] fork = jagged(r, fx0, fy0, fz0, fx0 + Math.cos(ang) * len, fy0 - len * 0.9, fz0 + Math.sin(ang) * len, 5, 0.9 * s);
            strand(buffers, fork, s * 0.8f, c, 0.4f * a);
            strand(buffers, fork, s * 0.16f, hot, 0.8f * a);
        }
        billboard(buf(buffers, GLOW), p.x(), p.y() + 0.8, p.z(), s * 5, 0, c, 0.6f * a);
        flatOnLand(buf(buffers, GLOW), fx.land, p.x(), p.z(), s * 4.5f, 0, 0.09f, hot, 0.8f * (1 - k));
    }

    /** A jagged path between two points: n segments, pushed aside most in the middle. */
    private static double[] jagged(RandomSource r, double x0, double y0, double z0, double x1, double y1, double z1, int n, double spread) {
        double[] out = new double[(n + 1) * 3];
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            double side = (i == 0 || i == n) ? 0 : spread * Math.sin(t * Math.PI) * 0.8 + spread * 0.3;
            out[i * 3] = Mth.lerp(t, x0, x1) + (r.nextDouble() - 0.5) * 2 * side;
            out[i * 3 + 1] = Mth.lerp(t, y0, y1);
            out[i * 3 + 2] = Mth.lerp(t, z0, z1) + (r.nextDouble() - 0.5) * 2 * side;
        }
        return out;
    }

    private static void strand(MultiBufferSource buffers, double[] pts, float width, int rgb, float alpha) {
        VertexConsumer out = buf(buffers, LINE);
        for (int i = 0; i + 5 < pts.length; i += 3) {
            ribbon(out, pts[i], pts[i + 1], pts[i + 2], pts[i + 3], pts[i + 4], pts[i + 5], width, width, rgb, alpha);
        }
    }

    private static void pillar(MultiBufferSource buffers, Fx fx, float age) {
        WorldBossFxPayload p = fx.p;
        float t = Mth.clamp(age / Math.max(1, p.ticks()), 0, 1);
        float rise = Mth.clamp(age / 3f, 0, 1);
        int c = p.color();
        float h = p.length() * rise;
        column(buf(buffers, BEAM), p.x(), p.y(), p.z(), p.size() * (1.3f - 0.6f * t), h, c, 0.85f * (1 - t));
        column(buf(buffers, BEAM), p.x(), p.y(), p.z(), p.size() * 0.45f, h * 0.92f, GateFx.mix(c, 0xFFFFFF, 0.8f), 0.9f * (1 - t));
        flatOnLand(buf(buffers, GLOW), fx.land, p.x(), p.z(), p.size() * 2.4f, 0, 0.09f, c, 0.75f * (1 - t));
        billboard(buf(buffers, GLOW), p.x(), p.y() + h, p.z(), p.size() * 1.6f, 0, c, 0.5f * (1 - t));
    }

    private static void burst(MultiBufferSource buffers, Fx fx, float age) {
        WorldBossFxPayload p = fx.p;
        float t = Mth.clamp(age / Math.max(1, p.ticks()), 0, 1);
        float out = 1 - (1 - t) * (1 - t);
        int c = p.color();
        billboard(buf(buffers, GLOW), p.x(), p.y(), p.z(), p.size() * (0.5f + out), 0, GateFx.mix(c, 0xFFFFFF, 0.5f), 1 - t);
        billboard(buf(buffers, GLOW), p.x(), p.y(), p.z(), p.size() * (1 + 1.6f * out), 0, c, 0.5f * (1 - t));
        billboard(buf(buffers, RING), p.x(), p.y(), p.z(), p.size() * (0.4f + 1.8f * out), 0, c, 0.8f * (1 - t));
    }

    /** Cracks of light run out over the ground from a heavy blow, white-hot, then cool into its colour and fade. */
    private static void quake(MultiBufferSource buffers, Fx fx, float age) {
        WorldBossFxPayload p = fx.p;
        if (fx.cracks == null) fx.cracks = cracks(RandomSource.create(fx.seed), p.size());
        float life = Math.max(1, p.ticks());
        float reach = p.size() * outCubic(Mth.clamp(age / 9f, 0, 1));
        float cool = Mth.clamp(age / 14f, 0, 1);
        float a = 1 - (float) Math.pow(age / life, 2);
        int c = p.color();
        int hot = GateFx.mix(0xFFFFFF, c, cool);
        VertexConsumer glow = buf(buffers, LINE);
        for (float[] s : fx.cracks) {
            if (s[4] > reach) continue;
            // a crack just reached burns brightest
            float fresh = Mth.clamp(1 - (reach - s[4]) / 4f, 0, 1);
            strip(glow, fx.land, p.x() + s[0], p.z() + s[1], s[2] - s[0], s[3] - s[1], 0, 1, s[5] * 1.8f, 0.07f, c, (0.55f + 0.3f * fresh) * a, true, true);
        }
        VertexConsumer core = buf(buffers, LINE);
        for (float[] s : fx.cracks) {
            if (s[4] > reach) continue;
            strip(core, fx.land, p.x() + s[0], p.z() + s[1], s[2] - s[0], s[3] - s[1], 0, 1, s[5] * 0.6f, 0.08f, hot, a, true, true);
        }
        float flashK = Mth.clamp(1 - age / 16f, 0, 1);
        if (flashK > 0) annulus(buf(buffers, FILL), fx.land, p.x(), p.z(), 0, Math.max(0.5f, reach * 0.7f), 0, Mth.TWO_PI, 0.06f, c, 0.35f * flashK);
    }

    /** A web of cracks out from the middle to about {@code radius}. Each segment: x0, z0, x1, z1, distance, width. */
    private static List<float[]> cracks(RandomSource r, float radius) {
        List<float[]> out = new ArrayList<>();
        int n = 7 + r.nextInt(4);
        for (int i = 0; i < n; i++) {
            float ang = (i + r.nextFloat() * 0.6f) / n * Mth.TWO_PI;
            crack(r, out, 0.4f * Mth.cos(ang), 0.4f * Mth.sin(ang), ang, radius * (0.6f + 0.4f * r.nextFloat()), 0.42f, 2);
        }
        return out;
    }

    private static void crack(RandomSource r, List<float[]> out, float x, float z, float ang, float reach, float width, int branches) {
        float d = Mth.sqrt(x * x + z * z);
        int guard = 0;
        while (d < reach && guard++ < 60) {
            float step = 0.9f + r.nextFloat() * 0.8f;
            ang += (r.nextFloat() - 0.5f) * 0.7f;
            float nx = x + Mth.cos(ang) * step, nz = z + Mth.sin(ang) * step;
            float w = width * Math.max(0.25f, 1 - d / reach);
            out.add(new float[]{x, z, nx, nz, d, w});
            if (branches > 0 && guard > 2 && r.nextFloat() < 0.22f) {
                float side = r.nextBoolean() ? 0.7f : -0.7f;
                crack(r, out, nx, nz, ang + side, Math.min(reach, d + (reach - d) * 0.6f), w * 0.8f, branches - 1);
            }
            x = nx;
            z = nz;
            d = Mth.sqrt(x * x + z * z);
        }
    }

    // ================================================================ the threat itself

    /** The beacon over a world boss, a monarch's gate in the sky, its domain on the land. */
    private static boolean threat(MultiBufferSource buffers, float time, float partial) {
        WorldBossClient.Tracked t = WorldBossClient.tracked();
        Minecraft mc = Minecraft.getInstance();
        if (t == null || mc.player == null) return false;
        ColossalBoss boss = t.boss();
        int c = t.kind.color;
        boolean drew = false;
        Vec3 at = boss != null ? boss.getPosition(partial) : Vec3.atBottomCenterOf(t.pos);
        double dist = mc.player.position().subtract(at).horizontalDistance();
        float ended = t.endedAt < 0 ? 0 : com.beyondsolo.client.gfx.Anim.since(t.endedAt);

        // the beacon: seen from afar, it fades as hunters close in
        if (!t.descending() && !t.leaving && !t.slain) {
            float far = Mth.clamp((float) (dist - 36) / 50f, 0, 1);
            if (far > 0.01f) {
                BEACON_LAND.around((float) at.y);
                float ground = BEACON_LAND.at(at.x, at.z);
                float pulse = 0.85f + 0.15f * Mth.sin(time * 0.08f);
                column(buf(buffers, BEAM), at.x, ground, at.z, 3.2f, 240, c, 0.32f * far * pulse);
                column(buf(buffers, BEAM), at.x, ground, at.z, 0.9f, 220, GateFx.mix(c, 0xFFFFFF, 0.7f), 0.55f * far);
                drew = true;
            }
        }

        if (t.kind.monarch()) {
            float open = gateOpen(t, boss, ended);
            if (open > 0.01f) {
                gate(buffers, t, open, time);
                drew = true;
            }
            if (boss != null && boss.isAlive() && t.domain > 0 && !t.leaving && boss.introTicks() <= 31) {
                float in = boss.introTicks() > 0 ? Mth.clamp((31 - boss.introTicks()) / 12f, 0, 1) : 1;
                domain(buffers, boss.getPosition(partial), t.domain, c, in, time);
                drew = true;
            }
        }
        return drew;
    }

    /** How open a monarch's gate is: open while it comes down, closing once it lands; open again as it leaves. */
    private static float gateOpen(WorldBossClient.Tracked t, ColossalBoss boss, float ended) {
        if (t.leaving) return outCubic(Mth.clamp(ended / 1.2f, 0, 1)) * (1 - Mth.clamp((ended - 5f) / 1.5f, 0, 1));
        if (t.slain) return 0;
        if (t.descending()) return outCubic(Mth.clamp(com.beyondsolo.client.gfx.Anim.since(t.since) / 2.5f, 0, 1));
        if (boss != null && boss.introTicks() > 0) return Mth.clamp((boss.introTicks() - 8) / 22f, 0, 1);
        return 0;
    }

    private static void gate(MultiBufferSource buffers, WorldBossClient.Tracked t, float open, float time) {
        int c = t.kind.color;
        double x = t.anchor.getX() + 0.5, z = t.anchor.getZ() + 0.5;
        double y = t.anchor.getY() + MonarchBoss.GATE_HEIGHT + 4.5;
        float spin = time % 7200;
        float breathe = 0.9f + 0.1f * Mth.sin(time * 0.06f);
        flat(buf(buffers, GLOW), x, y + 0.2, z, 30 * open, 0, c, 0.45f * open);
        flat(buf(buffers, VORTEX), x, y, z, 11.5f * open * breathe, spin * 1.6f, GateFx.mix(c, 0xFFFFFF, 0.15f), 0.95f * open);
        flat(buf(buffers, VORTEX), x, y - 0.05, z, 7f * open, -spin * 2.4f, 0xFFFFFF, 0.45f * open);
        flat(buf(buffers, CIRCLE), x, y + 0.05, z, 15.5f * open, -spin * 0.4f, c, 0.8f * open);
        flat(buf(buffers, RING), x, y + 0.1, z, 17f * open, 0, GateFx.mix(c, 0xFFFFFF, 0.4f), 0.9f * open);
        // the sky torn round it: jagged rents of light that flicker
        RandomSource r = RandomSource.create(t.event ^ ((long) (time / 3)) * 7919L);
        VertexConsumer tear = buf(buffers, LINE);
        for (int i = 0; i < 12; i++) {
            float ang = i / 12f * Mth.TWO_PI + Mth.sin(i * 1.7f) * 0.2f;
            float len = (6 + (i * 37 % 9)) * open;
            float r0 = 15.5f * open;
            double px = x + Mth.cos(ang) * r0, pz = z + Mth.sin(ang) * r0;
            for (int s = 0; s < 4; s++) {
                float step = len / 4;
                float a2 = ang + (r.nextFloat() - 0.5f) * 0.6f;
                double nx = px + Mth.cos(a2) * step, nz = pz + Mth.sin(a2) * step;
                flatRibbon(tear, px, y, pz, nx, y, nz, 0.7f * (1 - s / 4f) + 0.15f, GateFx.mix(c, 0xFFFFFF, 0.3f), 0.85f * open * (0.6f + 0.4f * r.nextFloat()));
                px = nx;
                pz = nz;
            }
        }
        // a shaft of light down to the ground as the monarch comes
        int land = t.landIn();
        float shaft = t.descending() ? Mth.clamp(1 - land / 80f, 0, 1) : open;
        if (shaft > 0.01f) {
            double ground = t.anchor.getY();
            column(buf(buffers, BEAM), x, ground, z, 3.5f * shaft, (float) (y - ground), c, 0.35f * shaft * open);
            column(buf(buffers, BEAM), x, ground, z, 1.1f * shaft, (float) (y - ground), GateFx.mix(c, 0xFFFFFF, 0.7f), 0.45f * shaft * open);
        }
    }

    /** A monarch's domain: a ring of its light on the land round it, a faint wall at its edge, sigils turning on it. */
    private static void domain(MultiBufferSource buffers, Vec3 at, int radius, int c, float in, float time) {
        DOMAIN_LAND.around((float) at.y);
        float r = radius * outCubic(in);
        float pulse = 0.8f + 0.2f * Mth.sin(time * 0.1f);
        annulus(buf(buffers, BAND), DOMAIN_LAND, at.x, at.z, r - 0.7f, r + 0.7f, 0, Mth.TWO_PI, 0.05f, c, 0.6f * pulse * in);
        wall(buf(buffers, BEAM), DOMAIN_LAND, at.x, at.z, r, 3.2f, c, 0.22f * in);
        float turn = (time % 7200) * 0.004f;
        for (int i = 0; i < 8; i++) {
            float ang = turn + i * Mth.TWO_PI / 8;
            double sx = at.x + Mth.cos(ang) * r, sz = at.z + Mth.sin(ang) * r;
            flatOnLand(buf(buffers, CIRCLE), DOMAIN_LAND, sx, sz, 1.7f, (time * 3 + i * 45) % 360, 0.06f, c, 0.6f * in);
        }
    }

    // ================================================================ drawing

    private static float outCubic(float t) {
        float u = 1 - t;
        return 1 - u * u * u;
    }

    private static void vtx(VertexConsumer out, double x, double y, double z, float u, float v, int rgb, int a) {
        out.addVertex(mat, (float) (x - camX), (float) (y - camY), (float) (z - camZ)).setUv(u, v)
                .setColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, a);
    }

    private static int alpha(float a) {
        return Mth.clamp(Math.round(a * 255), 0, 255);
    }

    /**
     * Ground round (x, z) from radius r0 to r1 and angle a0 to a1 (radians), lying on the land; the texture's v runs from
     * 0 at r0 to 1 at r1.
     */
    private static void annulus(VertexConsumer out, Terrain land, double x, double z, float r0, float r1, float a0, float a1, float lift, int rgb,
                                float alpha) {
        int a = alpha(alpha);
        if (a <= 2 || r1 <= r0 + 0.01f) return;
        int segs = Mth.clamp(Mth.ceil((a1 - a0) * r1 / 0.9f), 6, 200);
        int rings = Mth.clamp(Mth.ceil((r1 - r0) / 1.1f), 1, 40);
        float[] cos = new float[segs + 1], sin = new float[segs + 1];
        for (int j = 0; j <= segs; j++) {
            float ang = a0 + (a1 - a0) * j / segs;
            cos[j] = Mth.cos(ang);
            sin[j] = Mth.sin(ang);
        }
        float[] prev = new float[segs + 1], next = new float[segs + 1];
        for (int j = 0; j <= segs; j++) prev[j] = land.at(x + cos[j] * r0, z + sin[j] * r0) + lift;
        for (int i = 0; i < rings; i++) {
            float ra = r0 + (r1 - r0) * i / rings, rb = r0 + (r1 - r0) * (i + 1) / rings;
            float va = i / (float) rings, vb = (i + 1) / (float) rings;
            for (int j = 0; j <= segs; j++) next[j] = land.at(x + cos[j] * rb, z + sin[j] * rb) + lift;
            for (int j = 0; j < segs; j++) {
                vtx(out, x + cos[j] * ra, prev[j], z + sin[j] * ra, 0, va, rgb, a);
                vtx(out, x + cos[j + 1] * ra, prev[j + 1], z + sin[j + 1] * ra, 1, va, rgb, a);
                vtx(out, x + cos[j + 1] * rb, next[j + 1], z + sin[j + 1] * rb, 1, vb, rgb, a);
                vtx(out, x + cos[j] * rb, next[j], z + sin[j] * rb, 0, vb, rgb, a);
            }
            float[] tmp = prev;
            prev = next;
            next = tmp;
        }
    }

    private static void strip(VertexConsumer out, Terrain land, double x, double z, float dx, float dz, float s0, float s1, float half, float lift,
                              int rgb, float alpha, boolean across) {
        strip(out, land, x, z, dx, dz, s0, s1, half, lift, rgb, alpha, across, false);
    }

    /**
     * A strip of ground from (x, z) along (dx, dz), from s0 to s1 along it and {@code half} to each side, lying on the
     * land. The texture's v runs across it (0 on the left edge, 1 on the right) or along it (0 at s0, 1 at s1).
     * {@code raw}: (dx, dz) is not a unit direction but the strip itself (s from 0 to 1 covers it).
     */
    private static void strip(VertexConsumer out, Terrain land, double x, double z, float dx, float dz, float s0, float s1, float half, float lift,
                              int rgb, float alpha, boolean across, boolean raw) {
        int a = alpha(alpha);
        if (a <= 2 || s1 <= s0) return;
        float len = raw ? Mth.sqrt(dx * dx + dz * dz) * (s1 - s0) : s1 - s0;
        if (len < 0.01f) return;
        float ux = raw ? dx / Mth.sqrt(dx * dx + dz * dz) : dx, uz = raw ? dz / Mth.sqrt(dx * dx + dz * dz) : dz;
        float px = -uz, pz = ux;
        int n = Mth.clamp(Mth.ceil(len / 1.1f), 1, 80);
        int m = Mth.clamp(Mth.ceil(half * 2 / 1.1f), 1, 16);
        double ox = x + ux * (raw ? 0 : s0), oz = z + uz * (raw ? 0 : s0);
        for (int i = 0; i < n; i++) {
            float sa = len * i / n, sb = len * (i + 1) / n;
            for (int j = 0; j < m; j++) {
                float wa = -half + 2 * half * j / m, wb = -half + 2 * half * (j + 1) / m;
                double x1 = ox + ux * sa + px * wa, z1 = oz + uz * sa + pz * wa;
                double x2 = ox + ux * sa + px * wb, z2 = oz + uz * sa + pz * wb;
                double x3 = ox + ux * sb + px * wb, z3 = oz + uz * sb + pz * wb;
                double x4 = ox + ux * sb + px * wa, z4 = oz + uz * sb + pz * wa;
                float va = across ? j / (float) m : i / (float) n, vb = across ? (j + 1) / (float) m : i / (float) n;
                float vc = across ? (j + 1) / (float) m : (i + 1) / (float) n, vd = across ? j / (float) m : (i + 1) / (float) n;
                vtx(out, x1, land.at(x1, z1) + lift, z1, 0, va, rgb, a);
                vtx(out, x2, land.at(x2, z2) + lift, z2, 1, vb, rgb, a);
                vtx(out, x3, land.at(x3, z3) + lift, z3, 1, vc, rgb, a);
                vtx(out, x4, land.at(x4, z4) + lift, z4, 0, vd, rgb, a);
            }
        }
    }

    /** A square of ground round (x, z), {@code half} to each side, lying on the land; its texture drawn at {@code scale} of it. */
    private static void square(VertexConsumer out, Terrain land, double x, double z, float half, float scale, float lift, int rgb, float alpha) {
        int a = alpha(alpha);
        if (a <= 2 || scale < 0.01f) return;
        int n = Mth.clamp(Mth.ceil(half * 2 / 1.1f), 1, 32);
        float size = half * 2 * scale;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                float lx0 = -half + 2 * half * i / n, lx1 = -half + 2 * half * (i + 1) / n;
                float lz0 = -half + 2 * half * j / n, lz1 = -half + 2 * half * (j + 1) / n;
                corner(out, land, x, z, lx0, lz0, size, lift, rgb, a);
                corner(out, land, x, z, lx1, lz0, size, lift, rgb, a);
                corner(out, land, x, z, lx1, lz1, size, lift, rgb, a);
                corner(out, land, x, z, lx0, lz1, size, lift, rgb, a);
            }
        }
    }

    private static void corner(VertexConsumer out, Terrain land, double x, double z, float lx, float lz, float size, float lift, int rgb, int a) {
        vtx(out, x + lx, land.at(x + lx, z + lz) + lift, z + lz, 0.5f + lx / size, 0.5f + lz / size, rgb, a);
    }

    /** A texture lying on the land round (x, z), turned {@code degrees}. */
    private static void flatOnLand(VertexConsumer out, Terrain land, double x, double z, float radius, float degrees, float lift, int rgb, float alpha) {
        int a = alpha(alpha);
        if (a <= 2 || radius < 0.01f) return;
        int n = Mth.clamp(Mth.ceil(radius * 2 / 1.1f), 1, 32);
        float rad = degrees * Mth.DEG_TO_RAD;
        float cs = Mth.cos(rad), sn = Mth.sin(rad);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                float[][] cs4 = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
                for (float[] q : cs4) {
                    float lx = -radius + 2 * radius * q[0] / n, lz = -radius + 2 * radius * q[1] / n;
                    float u = 0.5f + (lx * cs + lz * sn) / (2 * radius), v = 0.5f + (-lx * sn + lz * cs) / (2 * radius);
                    vtx(out, x + lx, land.at(x + lx, z + lz) + lift, z + lz, u, v, rgb, a);
                }
            }
        }
    }

    /** A low wall of light standing round (x, z) at radius r, from the land up {@code height}, brightest at its foot. */
    private static void wall(VertexConsumer out, Terrain land, double x, double z, float r, float height, int rgb, float alpha) {
        int a = alpha(alpha);
        if (a <= 2 || r < 0.2f) return;
        int segs = Mth.clamp(Mth.ceil(Mth.TWO_PI * r / 0.9f), 8, 220);
        double px = x + r, pz = z;
        float ph = land.at(px, pz);
        for (int j = 1; j <= segs; j++) {
            float ang = Mth.TWO_PI * j / segs;
            double nx = x + Mth.cos(ang) * r, nz = z + Mth.sin(ang) * r;
            float nh = land.at(nx, nz);
            vtx(out, px, ph + 0.05, pz, 0.5f, 1, rgb, a);
            vtx(out, nx, nh + 0.05, nz, 0.5f, 1, rgb, a);
            vtx(out, nx, nh + height, nz, 0.5f, 0, rgb, a);
            vtx(out, px, ph + height, pz, 0.5f, 0, rgb, a);
            px = nx;
            pz = nz;
            ph = nh;
        }
    }

    /** A texture lying flat (level) at (x, y, z), turned {@code degrees}. */
    private static void flat(VertexConsumer out, double x, double y, double z, float radius, float degrees, int rgb, float alpha) {
        int a = alpha(alpha);
        if (a <= 2 || radius < 0.01f) return;
        float rad = degrees * Mth.DEG_TO_RAD;
        float cs = Mth.cos(rad) * radius, sn = Mth.sin(rad) * radius;
        vtx(out, x - cs + sn, y, z - sn - cs, 0, 0, rgb, a);
        vtx(out, x + cs + sn, y, z + sn - cs, 1, 0, rgb, a);
        vtx(out, x + cs - sn, y, z + sn + cs, 1, 1, rgb, a);
        vtx(out, x - cs - sn, y, z - sn + cs, 0, 1, rgb, a);
    }

    /** A soft quad facing the camera. */
    private static void billboard(VertexConsumer out, double x, double y, double z, float half, float degrees, int rgb, float alpha) {
        int a = alpha(alpha);
        if (a <= 2 || half < 0.01f) return;
        Vector3f left = cam.getLeftVector(), up = cam.getUpVector();
        float rad = degrees * Mth.DEG_TO_RAD, cs = Mth.cos(rad), sn = Mth.sin(rad);
        float lx = (left.x() * cs + up.x() * sn) * half, ly = (left.y() * cs + up.y() * sn) * half, lz = (left.z() * cs + up.z() * sn) * half;
        float ux = (up.x() * cs - left.x() * sn) * half, uy = (up.y() * cs - left.y() * sn) * half, uz = (up.z() * cs - left.z() * sn) * half;
        vtx(out, x + lx - ux, y + ly - uy, z + lz - uz, 0, 1, rgb, a);
        vtx(out, x - lx - ux, y - ly - uy, z - lz - uz, 1, 1, rgb, a);
        vtx(out, x - lx + ux, y - ly + uy, z - lz + uz, 1, 0, rgb, a);
        vtx(out, x + lx + ux, y + ly + uy, z + lz + uz, 0, 0, rgb, a);
    }

    /** A ribbon between two points that turns about its length to face the camera; v runs across it. */
    private static void ribbon(VertexConsumer out, double x0, double y0, double z0, double x1, double y1, double z1, float w0, float w1, int rgb,
                               float alpha) {
        int a = alpha(alpha);
        if (a <= 2) return;
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        double mx = (x0 + x1) / 2 - camX, my = (y0 + y1) / 2 - camY, mz = (z0 + z1) / 2 - camZ;
        // the side: across the ribbon and across the line of sight
        double sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
        double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1e-6) return;
        sx /= sl;
        sy /= sl;
        sz /= sl;
        vtx(out, x0 - sx * w0, y0 - sy * w0, z0 - sz * w0, 0, 0, rgb, a);
        vtx(out, x0 + sx * w0, y0 + sy * w0, z0 + sz * w0, 0, 1, rgb, a);
        vtx(out, x1 + sx * w1, y1 + sy * w1, z1 + sz * w1, 1, 1, rgb, a);
        vtx(out, x1 - sx * w1, y1 - sy * w1, z1 - sz * w1, 1, 0, rgb, a);
    }

    /** A ribbon lying level between two points (the rents in the sky round a gate); v runs across it. */
    private static void flatRibbon(VertexConsumer out, double x0, double y0, double z0, double x1, double y1, double z1, float w, int rgb, float alpha) {
        int a = alpha(alpha);
        if (a <= 2) return;
        double dx = x1 - x0, dz = z1 - z0;
        double l = Math.sqrt(dx * dx + dz * dz);
        if (l < 1e-6) return;
        double px = -dz / l * w, pz = dx / l * w;
        vtx(out, x0 - px, y0, z0 - pz, 0, 0, rgb, a);
        vtx(out, x0 + px, y0, z0 + pz, 0, 1, rgb, a);
        vtx(out, x1 + px, y1, z1 + pz, 1, 1, rgb, a);
        vtx(out, x1 - px, y1, z1 - pz, 1, 0, rgb, a);
    }

    /** A column of light standing at (x, z) from y up {@code height}, turning to face the camera; brightest at its foot. */
    private static void column(VertexConsumer out, double x, double y, double z, float half, float height, int rgb, float alpha) {
        int a = alpha(alpha);
        if (a <= 2 || height <= 0.01f) return;
        double dx = camX - x, dz = camZ - z;
        double l = Math.sqrt(dx * dx + dz * dz);
        if (l < 1e-4) {
            dx = 1;
            l = 1;
        }
        double px = -dz / l * half, pz = dx / l * half;
        vtx(out, x - px, y, z - pz, 0, 1, rgb, a);
        vtx(out, x + px, y, z + pz, 1, 1, rgb, a);
        vtx(out, x + px, y + height, z + pz, 1, 0, rgb, a);
        vtx(out, x - px, y + height, z - pz, 0, 0, rgb, a);
    }

    /** The kind's colour, for callers that only know the kind. */
    static int color(WorldBossKind kind) {
        return kind.color;
    }

    private WorldBossWorldFx() {}
}
