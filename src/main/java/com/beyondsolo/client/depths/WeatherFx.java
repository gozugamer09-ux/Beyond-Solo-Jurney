package com.beyondsolo.client.depths;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.combat.CombatWorldFx;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.depths.GateWeather;
import com.beyondsolo.gate.GateService;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The air of a cave with weather, around the camera: drifting wisps in the mist, embers rising in the heat, snow driven
 * sideways in the frost gale, sparks and far flashes in the mana storm, spores, a red haze, dim motes in the dark,
 * dust shaken down in the tremors. Plus the fog: each weather tints it and closes it in (the darkness to a few blocks),
 * and a storm bolt lights it up for a moment. Everything fades in and out with the weather. Motes are drawn as light
 * and never closer than a couple of blocks to the camera.
 */
public final class WeatherFx {
    static final ResourceLocation GLOW = CombatWorldFx.GLOW;
    static final ResourceLocation STREAK = BeyondSolo.id("textures/effect/streak.png");
    static final ResourceLocation WISP = BeyondSolo.id("textures/effect/depths_wisp.png");
    private static final RandomSource RANDOM = RandomSource.create();

    private static final class Mote {
        Vec3 pos;
        Vec3 vel;
        final float born;
        final float life;
        final float size;
        final int color;
        final ResourceLocation tex;
        final float alpha;
        final float roll;

        Mote(Vec3 pos, Vec3 vel, float born, float life, float size, int color, ResourceLocation tex, float alpha) {
            this.pos = pos;
            this.vel = vel;
            this.born = born;
            this.life = life;
            this.size = size;
            this.color = color;
            this.tex = tex;
            this.alpha = alpha;
            this.roll = RANDOM.nextFloat() * 360;
        }
    }

    private static final List<Mote> MOTES = new ArrayList<>();
    /** How much of the weather is showing (0..1): it fades in on the way in and out on the way out. */
    private static float shown;
    @Nullable private static GateWeather last;
    private static float flash;
    private static int nextAmbientFlash = 80;

    /** A storm bolt lit the cave up (strength by how close it was). */
    static void flash(Vec3 at, float strength) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        float k = (float) Mth.clamp(1 - cam.distanceTo(at) / 48, 0, 1);
        flash = Math.max(flash, strength * k);
    }

    @Nullable
    private static GateWeather active() {
        GateWeather w = DepthsClient.weather();
        return w == null || w == GateWeather.CLEAR ? null : w;
    }

    private static boolean inCaves() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.dimension().equals(GateService.DIMENSION);
    }

    // ================================================================ ticking

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !inCaves()) {
            // out of the caves the air stays behind at once: no motes fading out, no far thunder, no dust in the world
            clear();
            return;
        }
        GateWeather w = active();
        if (w != null) last = w;
        shown = Mth.approach(shown, w != null ? 1 : 0, 1 / 40f);
        flash *= 0.8f;
        float time = level.getGameTime();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();

        Iterator<Mote> it = MOTES.iterator();
        while (it.hasNext()) {
            Mote m = it.next();
            m.pos = m.pos.add(m.vel);
            if (time - m.born > m.life || m.pos.distanceToSqr(cam) > 24 * 24) it.remove();
        }
        if (last == null || shown <= 0.01f) {
            if (shown <= 0.01f) MOTES.clear();
            return;
        }
        int target = Math.round(count(last) * shown);
        for (int i = 0; i < 6 && MOTES.size() < target; i++) spawn(level, last, cam, time);

        if (last == GateWeather.STORM && --nextAmbientFlash <= 0) {
            // a bolt somewhere far off: the fog lights up and the rock rumbles
            nextAmbientFlash = 120 + RANDOM.nextInt(200);
            flash = Math.max(flash, 0.35f + 0.25f * RANDOM.nextFloat());
            level.playLocalSound(cam.x, cam.y, cam.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 0.12f * shown, 1.5f + RANDOM.nextFloat() * 0.3f, false);
        }
        if (last == GateWeather.TREMORS && RANDOM.nextInt(6) == 0) {
            // grit shaken down from the roof, somewhere near
            Vec3 at = cam.add(rnd(9), 0, rnd(9));
            if (at.distanceToSqr(cam) > 9) {
                BlockPos roof = roof(level, BlockPos.containing(at));
                if (roof != null) {
                    BlockState s = level.getBlockState(roof);
                    level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, s), at.x, roof.getY() - 0.05, at.z, 0, 0, 0);
                }
            }
        }
    }

    private static int count(GateWeather w) {
        return switch (w) {
            case MIST -> 34;
            case SCORCHING -> 60;
            case BLIZZARD -> 110;
            case STORM -> 40;
            case MIASMA -> 70;
            case CRIMSON -> 60;
            case DARKNESS -> 26;
            case TREMORS -> 30;
            default -> 0;
        };
    }

    private static void spawn(ClientLevel level, GateWeather w, Vec3 cam, float time) {
        Vec3 off = new Vec3(rnd(1), rnd(0.6), rnd(1));
        if (off.lengthSqr() < 1.0e-3) return;
        double dist = 2.5 + RANDOM.nextDouble() * (w == GateWeather.MIST ? 12 : 13);
        Vec3 at = cam.add(off.normalize().scale(dist));
        if (!level.getBlockState(BlockPos.containing(at)).isAir()) return;
        Mote m = switch (w) {
            case MIST -> new Mote(at, new Vec3(rnd(0.01), rnd(0.002), rnd(0.01)), time, 90 + RANDOM.nextInt(60), 1.6f + RANDOM.nextFloat() * 1.8f,
                    mix(w.color, 0xFFFFFF, 0.2f), WISP, 0.13f);
            case SCORCHING -> new Mote(at, new Vec3(rnd(0.006), 0.018 + RANDOM.nextDouble() * 0.03, rnd(0.006)), time, 50 + RANDOM.nextInt(40),
                    0.04f + RANDOM.nextFloat() * 0.06f, RANDOM.nextBoolean() ? 0xFF8A3A : 0xFFC85A, GLOW, 0.9f);
            case BLIZZARD -> new Mote(at, new Vec3(0.18 + RANDOM.nextDouble() * 0.08, -0.05 - RANDOM.nextDouble() * 0.04, 0.06 + rnd(0.03)), time,
                    30 + RANDOM.nextInt(20), 0.035f + RANDOM.nextFloat() * 0.03f, 0xF4FAFF, STREAK, 0.8f);
            case STORM -> new Mote(at, new Vec3(rnd(0.02), rnd(0.01), rnd(0.02)), time, 30 + RANDOM.nextInt(40), 0.03f + RANDOM.nextFloat() * 0.04f,
                    RANDOM.nextInt(3) == 0 ? 0xFFFFFF : w.color, GLOW, 1f);
            case MIASMA -> new Mote(at, new Vec3(rnd(0.006), -0.004 - RANDOM.nextDouble() * 0.006, rnd(0.006)), time, 90 + RANDOM.nextInt(60),
                    0.05f + RANDOM.nextFloat() * 0.07f, RANDOM.nextInt(3) == 0 ? 0xD8F07A : w.color, GLOW, 0.75f);
            case CRIMSON -> RANDOM.nextInt(6) == 0
                    ? new Mote(at, new Vec3(rnd(0.008), 0.004, rnd(0.008)), time, 100 + RANDOM.nextInt(60), 1.4f + RANDOM.nextFloat(), w.color, WISP, 0.08f)
                    : new Mote(at, new Vec3(rnd(0.004), 0.01 + RANDOM.nextDouble() * 0.012, rnd(0.004)), time, 70 + RANDOM.nextInt(50),
                    0.04f + RANDOM.nextFloat() * 0.05f, RANDOM.nextBoolean() ? w.color : 0xFF7A6A, GLOW, 0.85f);
            case DARKNESS -> new Mote(at, new Vec3(rnd(0.006), rnd(0.004), rnd(0.006)), time, 80 + RANDOM.nextInt(60), 0.03f + RANDOM.nextFloat() * 0.03f,
                    w.color, GLOW, 0.55f);
            case TREMORS -> new Mote(at, new Vec3(rnd(0.004), -0.008 - RANDOM.nextDouble() * 0.01, rnd(0.004)), time, 60 + RANDOM.nextInt(40),
                    0.025f + RANDOM.nextFloat() * 0.025f, w.color, GLOW, 0.6f);
            default -> null;
        };
        if (m != null) MOTES.add(m);
    }

    @Nullable
    private static BlockPos roof(ClientLevel level, BlockPos from) {
        for (int i = 1; i < 14; i++) {
            if (!level.getBlockState(from.above(i)).isAir()) return from.above(i);
        }
        return null;
    }

    // ================================================================ drawing

    static void render(PoseStack pose, Camera camera, float partial) {
        if (MOTES.isEmpty() || shown <= 0.01f) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 cam = camera.getPosition();
        float time = mc.level.getGameTime() + partial;
        for (ResourceLocation tex : List.of(WISP, GLOW, STREAK)) {
            VertexConsumer out = null;
            for (Mote m : MOTES) {
                if (m.tex != tex) continue;
                Vec3 p = m.pos.add(m.vel.scale(partial));
                double d2 = p.distanceToSqr(cam);
                // fade out near the camera rather than pop
                float near = (float) Mth.clamp((Math.sqrt(d2) - 2.0) / 1.5, 0, 1);
                float age = time - m.born;
                float life = Mth.clamp(age / 12f, 0, 1) * Mth.clamp((m.life - age) / 16f, 0, 1);
                float a = m.alpha * life * near * shown;
                if (a <= 0.01f) continue;
                if (out == null) out = buffers.getBuffer(ModRenderTypes.lightTexture(tex));
                pose.pushPose();
                pose.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
                if (tex == STREAK) {
                    streak(pose, out, camera, p, m.vel.scale(-3.5), m.size, m.color, a);
                } else {
                    pose.mulPose(camera.rotation());
                    pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(m.roll + age * 0.2f));
                    sprite(out, pose.last().pose(), m.size, m.color, a);
                }
                pose.popPose();
            }
            if (out != null) buffers.endBatch(ModRenderTypes.lightTexture(tex));
        }
    }

    // ================================================================ the fog

    private static float tintStrength(GateWeather w) {
        return switch (w) {
            case MIST -> 0.75f;
            case SCORCHING -> 0.5f;
            case BLIZZARD -> 0.7f;
            case STORM -> 0.55f;
            case MIASMA -> 0.6f;
            case CRIMSON -> 0.7f;
            case DARKNESS -> 0.92f;
            case TREMORS -> 0.45f;
            default -> 0f;
        };
    }

    /** The fog colour with the weather's tint (and a storm's flash) laid over it. */
    static float[] fogColor(float r, float g, float b) {
        if (last == null || shown <= 0.01f || !inCaves()) return null;
        float k = tintStrength(last) * shown;
        int c = last.fogColor;
        r = Mth.lerp(k, r, ((c >> 16) & 0xFF) / 255f);
        g = Mth.lerp(k, g, ((c >> 8) & 0xFF) / 255f);
        b = Mth.lerp(k, b, (c & 0xFF) / 255f);
        if (flash > 0.01f) {
            int f = last.color;
            r = Mth.lerp(flash * 0.6f, r, ((f >> 16) & 0xFF) / 255f);
            g = Mth.lerp(flash * 0.6f, g, ((f >> 8) & 0xFF) / 255f);
            b = Mth.lerp(flash * 0.6f, b, (f & 0xFF) / 255f);
        }
        return new float[]{r, g, b};
    }

    /** How far the fog lets you see with this weather, given what it would be without it; -1 to leave it. */
    static float fogFar(float far) {
        if (last == null || shown <= 0.01f || last.fogFar <= 0 || !inCaves()) return -1;
        float target = last.fogFar + 10 * flash;
        return Mth.lerp(shown, far, Math.min(far, target));
    }

    // ================================================================ helpers

    private static double rnd(double s) {
        return (RANDOM.nextDouble() - 0.5) * 2 * s;
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
        int g = Math.round(Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
        int bl = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
        return (r << 16) | (g << 8) | bl;
    }

    private static void streak(PoseStack pose, VertexConsumer out, Camera camera, Vec3 at, Vec3 tail, float width, int rgb, float alpha) {
        Vec3 view = camera.getPosition().subtract(at);
        Vec3 side = tail.cross(view);
        if (side.lengthSqr() < 1.0e-8) return;
        side = side.normalize().scale(width / 2);
        Matrix4f m = pose.last().pose();
        int al = Mth.clamp(Math.round(Math.min(1f, alpha) * 255), 0, 255);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, bl = rgb & 0xFF;
        out.addVertex(m, (float) -side.x, (float) -side.y, (float) -side.z).setUv(0, 1).setColor(r, g, bl, al);
        out.addVertex(m, (float) (tail.x - side.x), (float) (tail.y - side.y), (float) (tail.z - side.z)).setUv(1, 1).setColor(r, g, bl, al);
        out.addVertex(m, (float) (tail.x + side.x), (float) (tail.y + side.y), (float) (tail.z + side.z)).setUv(1, 0).setColor(r, g, bl, al);
        out.addVertex(m, (float) side.x, (float) side.y, (float) side.z).setUv(0, 0).setColor(r, g, bl, al);
    }

    private static void sprite(VertexConsumer out, Matrix4f m, float half, int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        if (a <= 1) return;
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        out.addVertex(m, -half, -half, 0).setUv(0, 1).setColor(red, green, blue, a);
        out.addVertex(m, half, -half, 0).setUv(1, 1).setColor(red, green, blue, a);
        out.addVertex(m, half, half, 0).setUv(1, 0).setColor(red, green, blue, a);
        out.addVertex(m, -half, half, 0).setUv(0, 0).setColor(red, green, blue, a);
    }

    static void clear() {
        MOTES.clear();
        shown = 0;
        last = null;
        flash = 0;
    }

    private WeatherFx() {}
}
