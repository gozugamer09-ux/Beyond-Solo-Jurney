package com.beyondsolo.client.shadow;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.gate.GateFx;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.network.ShadowFxPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The shadow army in the world: lingering souls, the extraction ritual (a widening circle, chains of light drawn up
 * out of the ground, a thread of mana from the necromancer's hand), the burst when a shadow answers, the soul
 * refusing or crumbling, shadows called up and sent back, and the pillar of a rank-up. Light is drawn with the
 * mod's light render type; smoke, embers and souls are vanilla particles. Runs on game time.
 */
public final class ShadowWorldFx {
    private static final ResourceLocation GLOW = ShadowRenderer.GLOW;
    private static final ResourceLocation RING = ShadowRenderer.RING;
    private static final ResourceLocation CIRCLE = ShadowRenderer.CIRCLE;
    private static final ResourceLocation BEAM = BeyondSolo.id("textures/effect/beam.png");
    private static final ResourceLocation STREAK = BeyondSolo.id("textures/effect/streak.png");
    private static final ResourceLocation[] ALL = {GLOW, RING, CIRCLE, BEAM, STREAK};

    /** A soul lingering where a creature fell. */
    static final class Soul {
        final int id;
        Vec3 pos;
        int color;
        int tries;
        String type;
        final float born;
        float ritualStart = -1;
        int ritualTicks;
        /** Set when a try fails: the circle cracks. */
        float failedAt = -100;

        Soul(int id, Vec3 pos, int color, int tries, String type, float born) {
            this.id = id;
            this.pos = pos;
            this.color = color;
            this.tries = tries;
            this.type = type;
            this.born = born;
        }

        boolean inRitual(float now) {
            return ritualStart >= 0 && now - ritualStart < ritualTicks + 2;
        }

        float ritualProgress(float now) {
            return ritualStart < 0 ? 0 : Mth.clamp((now - ritualStart) / ritualTicks, 0, 1);
        }
    }

    private record Fx(ShadowFxPayload.Kind kind, Vec3 pos, int color, float start, int entityId, int extra) {
        float life() {
            return switch (kind) {
                case EXTRACTED -> extra == 1 ? 2.4f : 1.2f;
                case FAILED -> 0.8f;
                case CRUMBLE -> 1.3f;
                case SUMMON -> 1.6f;
                case RECALL -> 1.0f;
                case RANK_UP -> 2.6f;
                case PROMOTED -> 3.4f;
                case ESSENCE -> 0.9f;
                case NAMED -> 1.4f;
                default -> 0.5f;
            };
        }
    }

    static final Map<Integer, Soul> SOULS = new HashMap<>();
    private static final List<Fx> ACTIVE = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    static float now(float partial) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime() + partial;
    }

    static void handle(ShadowFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 at = new Vec3(p.x(), p.y(), p.z());
        float t = now(0);
        switch (p.kindEnum()) {
            case SOUL -> {
                Soul s = SOULS.get(p.id());
                if (s == null) SOULS.put(p.id(), new Soul(p.id(), at, p.color(), p.extra(), p.text(), t));
                else s.tries = p.extra();
                puff(mc.level, at, 14);
            }
            case SOUL_GONE -> {
                SOULS.remove(p.id());
                puff(mc.level, at, 8);
            }
            case RITUAL -> {
                Soul s = SOULS.computeIfAbsent(p.id(), id -> new Soul(id, at, p.color(), 3, p.text(), t));
                s.ritualStart = t;
                s.ritualTicks = Math.max(1, p.extra());
            }
            case FAILED -> {
                Soul s = SOULS.get(p.id());
                if (s != null) {
                    s.ritualStart = -1;
                    s.tries = p.extra();
                    s.failedAt = t;
                }
                add(p, at, t);
                for (int i = 0; i < 20; i++) {
                    mc.level.addParticle(ParticleTypes.SMOKE, at.x + rnd(0.6), at.y + 0.2 + RANDOM.nextDouble(), at.z + rnd(0.6), rnd(0.08), 0.05, rnd(0.08));
                }
                for (int i = 0; i < 10; i++) mc.level.addParticle(ParticleTypes.CRIT, at.x + rnd(0.4), at.y + 1, at.z + rnd(0.4), rnd(0.3), 0.1, rnd(0.3));
            }
            case CRUMBLE -> {
                SOULS.remove(p.id());
                add(p, at, t);
                for (int i = 0; i < 40; i++) {
                    mc.level.addParticle(i % 2 == 0 ? ParticleTypes.ASH : ParticleTypes.WHITE_ASH, at.x + rnd(0.6), at.y + RANDOM.nextDouble() * 1.4, at.z + rnd(0.6), 0, -0.02, 0);
                }
                puff(mc.level, at, 16);
            }
            case EXTRACTED -> {
                SOULS.remove(p.id());
                add(p, at, t);
                boolean boss = p.extra() == 1;
                int n = boss ? 30 : 22;
                for (int i = 0; i < n; i++) {
                    double a = RANDOM.nextDouble() * Math.PI * 2;
                    double sp = 0.15 + RANDOM.nextDouble() * (boss ? 0.5 : 0.3);
                    mc.level.addParticle(i % 2 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, at.x, at.y + 0.3, at.z, Math.cos(a) * sp, 0.02 + RANDOM.nextDouble() * 0.06, Math.sin(a) * sp);
                }
                for (int i = 0; i < (boss ? 40 : 18); i++) {
                    mc.level.addParticle(ParticleTypes.SCULK_SOUL, at.x + rnd(0.6), at.y + 0.2, at.z + rnd(0.6), rnd(0.05), 0.12 + RANDOM.nextDouble() * 0.15, rnd(0.05));
                }
                DustParticleOptions dust = new DustParticleOptions(rgb(p.color()), 1.4f);
                for (int i = 0; i < 40; i++) {
                    double a = i / 40.0 * Math.PI * 2;
                    mc.level.addParticle(dust, at.x + Math.cos(a) * 0.8, at.y + 0.1, at.z + Math.sin(a) * 0.8, Math.cos(a) * 0.5, 0.05, Math.sin(a) * 0.5);
                }
            }
            case SUMMON -> {
                add(p, at, t);
                for (int i = 0; i < 12; i++) {
                    mc.level.addParticle(ParticleTypes.LARGE_SMOKE, at.x + rnd(0.5), at.y + 0.1, at.z + rnd(0.5), rnd(0.03), 0.04 + RANDOM.nextDouble() * 0.08, rnd(0.03));
                }
                for (int i = 0; i < 12; i++) mc.level.addParticle(ParticleTypes.REVERSE_PORTAL, at.x + rnd(0.5), at.y + 0.2, at.z + rnd(0.5), 0, 0.15, 0);
            }
            case RECALL -> {
                add(p, at, t);
                for (int i = 0; i < 24; i++) {
                    double a = i / 24.0 * Math.PI * 2;
                    mc.level.addParticle(ParticleTypes.SMOKE, at.x + Math.cos(a) * 1.0, at.y + 1.2, at.z + Math.sin(a) * 1.0, -Math.cos(a) * 0.08, -0.08, -Math.sin(a) * 0.08);
                }
            }
            case RANK_UP -> {
                add(p, at, t);
                for (int i = 0; i < 30; i++) {
                    mc.level.addParticle(ParticleTypes.END_ROD, at.x + rnd(0.8), at.y + RANDOM.nextDouble() * 0.5, at.z + rnd(0.8), 0, 0.12 + RANDOM.nextDouble() * 0.2, 0);
                }
            }
            case PROMOTED -> {
                add(p, at, t);
                // a burst of its new rank's light, sparks thrown up and out, souls rising round it
                DustParticleOptions dust = new DustParticleOptions(rgb(p.color()), 1.6f);
                for (int i = 0; i < 48; i++) {
                    double a = i / 48.0 * Math.PI * 2;
                    mc.level.addParticle(dust, at.x + Math.cos(a) * 0.6, at.y + 0.15, at.z + Math.sin(a) * 0.6, Math.cos(a) * 0.6, 0.08, Math.sin(a) * 0.6);
                }
                for (int i = 0; i < 40; i++) {
                    mc.level.addParticle(ParticleTypes.END_ROD, at.x + rnd(0.7), at.y + RANDOM.nextDouble() * 1.6, at.z + rnd(0.7), rnd(0.12), 0.1 + RANDOM.nextDouble() * 0.3, rnd(0.12));
                }
                for (int i = 0; i < 24; i++) {
                    mc.level.addParticle(ParticleTypes.SCULK_SOUL, at.x + rnd(0.9), at.y + 0.2, at.z + rnd(0.9), rnd(0.03), 0.1 + RANDOM.nextDouble() * 0.15, rnd(0.03));
                }
            }
            case ESSENCE -> {
                // the soul drifts to its master: the wisp travels (drawn in render), nothing stays behind
                SOULS.remove(p.id());
                add(p, at, t);
                for (int i = 0; i < 6; i++) mc.level.addParticle(ParticleTypes.SOUL, at.x + rnd(0.3), at.y + 0.4, at.z + rnd(0.3), 0, 0.04, 0);
            }
            case NAMED -> {
                add(p, at, t);
                for (int i = 0; i < 24; i++) {
                    double a = i / 24.0 * Math.PI * 2;
                    mc.level.addParticle(ParticleTypes.END_ROD, at.x + Math.cos(a) * 0.7, at.y + 0.1, at.z + Math.sin(a) * 0.7, 0, 0.06 + RANDOM.nextDouble() * 0.05, 0);
                }
            }
            default -> {
            }
        }
    }

    private static void add(ShadowFxPayload p, Vec3 at, float t) {
        ACTIVE.add(new Fx(p.kindEnum(), at, p.color(), t, p.id(), p.extra()));
        if (ACTIVE.size() > 64) ACTIVE.remove(0);
    }

    private static void puff(ClientLevel level, Vec3 at, int n) {
        for (int i = 0; i < n; i++) level.addParticle(ParticleTypes.SMOKE, at.x + rnd(0.4), at.y + RANDOM.nextDouble() * 0.8, at.z + rnd(0.4), 0, 0.03, 0);
    }

    private static Vector3f rgb(int c) {
        return new Vector3f(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f);
    }

    private static double rnd(double s) {
        return (RANDOM.nextDouble() - 0.5) * 2 * s;
    }

    /** Smoke and souls drifting off each soul; the ritual drawing smoke in. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float t = now(0);
        for (Soul s : SOULS.values()) {
            Vec3 at = s.pos;
            if (mc.level.getGameTime() % 3 == 0) {
                mc.level.addParticle(ParticleTypes.SMOKE, at.x + rnd(0.3), at.y + 0.1, at.z + rnd(0.3), 0, 0.04, 0);
            }
            if (mc.level.getGameTime() % 9 == 0) {
                mc.level.addParticle(ParticleTypes.SOUL, at.x + rnd(0.25), at.y + 0.3, at.z + rnd(0.25), 0, 0.03, 0);
            }
            if (s.inRitual(t)) {
                float k = s.ritualProgress(t);
                int n = 1 + (int) (k * 2.5f);
                for (int i = 0; i < n; i++) {
                    double a = RANDOM.nextDouble() * Math.PI * 2;
                    double r = 2.2 + RANDOM.nextDouble() * 1.2;
                    mc.level.addParticle(ParticleTypes.SMOKE, at.x + Math.cos(a) * r, at.y + 0.1, at.z + Math.sin(a) * r,
                            -Math.cos(a) * r * 0.06, 0.02, -Math.sin(a) * r * 0.06);
                }
                if (mc.level.getGameTime() % 2 == 0) {
                    mc.level.addParticle(ParticleTypes.SCULK_SOUL, at.x + rnd(0.4), at.y + 0.2, at.z + rnd(0.4), 0, 0.05 + 0.1 * k, 0);
                }
            }
        }
    }

    @Nullable
    static Soul nearestSoul(Vec3 from, double reach) {
        Soul best = null;
        double bd = reach * reach;
        for (Soul s : SOULS.values()) {
            double d = s.pos.distanceToSqr(from);
            if (d < bd) {
                bd = d;
                best = s;
            }
        }
        return best;
    }

    public static void render(PoseStack pose, Camera camera, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || (SOULS.isEmpty() && ACTIVE.isEmpty())) return;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 cam = camera.getPosition();
        float time = now(partial);

        for (Soul s : SOULS.values()) {
            pose.pushPose();
            pose.translate(s.pos.x - cam.x, s.pos.y - cam.y, s.pos.z - cam.z);
            float age = (time - s.born) / 20f;
            float in = Mth.clamp(age / 0.6f, 0, 1);
            float pulse = 0.8f + 0.2f * Mth.sin(time * 0.25f + s.id);
            boolean ritual = s.inRitual(time);
            float crack = Mth.clamp(1 - (time - s.failedAt) / 16f, 0, 1);
            // the wisp
            dot(buffers, pose, camera, 0, 0.7f + 0.08f * Mth.sin(time * 0.12f), 0, 0.35f * in * pulse, GateFx.mix(s.color, 0xFFFFFF, 0.3f), 0.9f * in);
            dot(buffers, pose, camera, 0, 0.7f, 0, 0.9f * in, s.color, 0.3f * in * pulse);
            flat(buffers, RING, pose, 0.04f, 0.9f * in, time * 2, s.color, 0.45f * in);
            if (crack > 0) flat(buffers, RING, pose, 0.05f, 1.2f + 1.5f * (1 - crack), 0, 0xFF5060, 0.7f * crack);
            if (ritual) ritual(buffers, pose, camera, s, time, cam, partial);
            pose.popPose();
        }

        Iterator<Fx> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Fx fx = it.next();
            float age = (time - fx.start) / 20f;
            float life = fx.life();
            if (age > life || age < -1) {
                it.remove();
                continue;
            }
            float t = Mth.clamp(age / life, 0, 1);
            float out = 1 - (1 - t) * (1 - t) * (1 - t);
            float fade = 1 - t;
            Vec3 at = fx.pos;
            if (fx.kind == ShadowFxPayload.Kind.RANK_UP || fx.kind == ShadowFxPayload.Kind.PROMOTED || fx.kind == ShadowFxPayload.Kind.NAMED) {
                Entity e = mc.level.getEntity(fx.entityId);
                if (e != null) at = e.getPosition(partial);
            }
            pose.pushPose();
            pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            int c = fx.color;
            int light = GateFx.mix(c, 0xFFFFFF, 0.5f);
            switch (fx.kind) {
                case EXTRACTED -> {
                    float big = fx.extra == 1 ? 1.8f : 1f;
                    float flash = Mth.clamp(1 - age / 0.35f, 0, 1);
                    dot(buffers, pose, camera, 0, 1f, 0, 3.5f * big * (0.4f + 0.6f * out), light, flash);
                    flat(buffers, RING, pose, 0.06f, 6f * big * out, 0, light, fade);
                    flat(buffers, RING, pose, 0.07f, 3.5f * big * out, 0, c, fade * 0.8f);
                    flat(buffers, CIRCLE, pose, 0.05f, 2.6f * big, time * 8, c, fade);
                    float pillar = Mth.clamp(1 - age / (0.8f * big), 0, 1);
                    column(buffers, pose, camera, at, (0.3f + 1.2f * out) * big, 16 * big * Math.min(1, age * 4), light, pillar);
                    column(buffers, pose, camera, at, (0.9f + 2f * out) * big, 16 * big * Math.min(1, age * 4), c, pillar * 0.6f);
                }
                case FAILED -> {
                    flat(buffers, CIRCLE, pose, 0.05f, 2.6f * (1 - 0.6f * out), -time * 12, 0xFF5060, fade * 0.8f);
                    dot(buffers, pose, camera, 0, 0.8f, 0, 1.5f * (1 - out), 0xFF6070, fade * 0.6f);
                }
                case CRUMBLE -> {
                    dot(buffers, pose, camera, 0, 0.7f - 0.5f * out, 0, 0.9f * fade, c, fade * 0.5f);
                    flat(buffers, RING, pose, 0.05f, 0.9f + 1.4f * out, 0, 0x807890, fade * 0.6f);
                }
                case SUMMON -> {
                    float k = fx.extra == 1 ? 1.6f : 1f;
                    flat(buffers, RING, pose, 0.06f, (0.6f + 2.4f * out) * k, 0, light, fade * 0.8f);
                    column(buffers, pose, camera, at, 0.5f * k * fade, 3.5f * k * out, c, fade * 0.7f);
                }
                case RECALL -> {
                    flat(buffers, CIRCLE, pose, 0.05f, 1.6f * (1 - 0.7f * out), time * 10, c, fade);
                    flat(buffers, RING, pose, 0.06f, 2.2f * (1 - out), 0, light, fade * 0.8f);
                }
                case RANK_UP -> {
                    float up = Mth.clamp(age / 0.3f, 0, 1);
                    float hold = Mth.clamp((life - age) / 0.8f, 0, 1);
                    column(buffers, pose, camera, at, 0.5f + 0.4f * Mth.sin(time * 0.4f), 22 * up, light, hold);
                    column(buffers, pose, camera, at, 1.4f, 22 * up, c, hold * 0.55f);
                    for (int i = 0; i < 3; i++) {
                        float rt = Mth.clamp((age - i * 0.35f) / 1.0f, 0, 1);
                        if (rt <= 0 || rt >= 1) continue;
                        flat(buffers, RING, pose, 0.08f + i * 0.01f, 0.5f + 5f * rt, 0, i == 1 ? light : c, 1 - rt);
                    }
                    flat(buffers, CIRCLE, pose, 0.05f, 2.2f, time * 5, c, hold * 0.9f);
                    dot(buffers, pose, camera, 0, 1.2f, 0, 2.4f, c, 0.4f * hold * (0.7f + 0.3f * Mth.sin(time * 0.6f)));
                }
                case PROMOTED -> {
                    Entity e = mc.level.getEntity(fx.entityId);
                    float eh = e != null ? e.getBbHeight() : 1.8f;
                    float up = Mth.clamp(age / 0.25f, 0, 1);
                    float hold = Mth.clamp((life - age) / 1.0f, 0, 1);
                    float flash = Mth.clamp(1 - age / 0.3f, 0, 1);
                    // the pillar it is raised in, and the flash at the moment
                    column(buffers, pose, camera, at, 0.7f + 0.3f * Mth.sin(time * 0.5f), 30 * up, light, hold);
                    column(buffers, pose, camera, at, 2.0f, 30 * up, c, hold * 0.5f);
                    dot(buffers, pose, camera, 0, eh * 0.6f, 0, 4.5f * (0.5f + 0.5f * out), 0xFFFFFF, flash);
                    // rings rolling out over the ground, and the circle it stands in
                    for (int i = 0; i < 4; i++) {
                        float rt = Mth.clamp((age - i * 0.3f) / 1.2f, 0, 1);
                        if (rt <= 0 || rt >= 1) continue;
                        flat(buffers, RING, pose, 0.08f + i * 0.01f, 0.6f + 7f * rt, 0, i % 2 == 0 ? light : c, 1 - rt);
                    }
                    flat(buffers, CIRCLE, pose, 0.05f, 2.8f, time * 4, c, hold);
                    flat(buffers, CIRCLE, pose, 0.06f, 1.6f, -time * 7, light, hold * 0.7f);
                    // rays thrown up and out from over its head, like a crown being set
                    Vec3 head = new Vec3(0, eh + 0.2, 0);
                    float rays = Mth.clamp((age - 0.15f) / 0.6f, 0, 1);
                    for (int i = 0; i < 10; i++) {
                        float a = i / 10f * Mth.TWO_PI + time * 0.02f;
                        Vec3 tip = head.add(Mth.cos(a) * 1.6f * rays, 1.4f * rays, Mth.sin(a) * 1.6f * rays);
                        streak(buffers, pose, camera, at, head, tip, 0.14f, i % 2 == 0 ? light : c, hold * 0.9f * (1 - rays * 0.3f));
                    }
                    dot(buffers, pose, camera, 0, eh * 0.55f, 0, 3f, c, 0.45f * hold * (0.7f + 0.3f * Mth.sin(time * 0.6f)));
                }
                case ESSENCE -> {
                    // a wisp flying from the soul to its master's chest, in an arc, with a short tail
                    Entity master = mc.level.getEntity(fx.extra);
                    if (master == null) break;
                    Vec3 to = master.getPosition(partial).add(0, master.getBbHeight() * 0.6, 0).subtract(at);
                    Vec3 from = new Vec3(0, 0.7, 0);
                    float k = Mth.clamp(age / (life * 0.85f), 0, 1);
                    float e1 = k * k * (3 - 2 * k);
                    float e0 = Math.max(0, e1 - 0.12f);
                    Vec3 p1 = arc(from, to, e1);
                    Vec3 p0 = arc(from, to, e0);
                    if (k < 1) {
                        streak(buffers, pose, camera, at, p0, p1, 0.16f, c, 0.85f);
                        dot(buffers, pose, camera, (float) p1.x, (float) p1.y, (float) p1.z, 0.28f, light, 0.95f);
                        dot(buffers, pose, camera, (float) p1.x, (float) p1.y, (float) p1.z, 0.7f, c, 0.4f);
                    } else {
                        float pop = Mth.clamp((age - life * 0.85f) / (life * 0.15f), 0, 1);
                        dot(buffers, pose, camera, (float) to.x, (float) to.y, (float) to.z, 0.5f + 0.6f * pop, light, 0.8f * (1 - pop));
                    }
                }
                case NAMED -> {
                    Entity e = mc.level.getEntity(fx.entityId);
                    float eh = e != null ? e.getBbHeight() : 1.8f;
                    // a ring of light rising from its feet to over its head, where the name now hangs
                    float y = eh * out + 0.1f;
                    flat(buffers, RING, pose, y, 0.9f + 0.4f * out, 0, light, fade);
                    flat(buffers, RING, pose, 0.05f, 0.6f + 2.2f * out, 0, c, fade * 0.7f);
                    dot(buffers, pose, camera, 0, eh + 0.45f, 0, 1.4f * fade, c, 0.5f * fade);
                }
                default -> {
                }
            }
            pose.popPose();
        }
        for (ResourceLocation tex : ALL) buffers.endBatch(ModRenderTypes.lightTexture(tex));
    }

    /** The ritual on a soul: circle, chains of light rising, smoke drawn in, and the thread from the caster's hand. */
    private static void ritual(MultiBufferSource buffers, PoseStack pose, Camera camera, Soul s, float time, Vec3 cam, float partial) {
        float k = s.ritualProgress(time);
        float ease = 1 - (1 - k) * (1 - k);
        int c = s.color;
        int light = GateFx.mix(c, 0xFFFFFF, 0.5f);
        float r = 0.6f + 2.2f * ease;
        flat(buffers, CIRCLE, pose, 0.05f, r, time * (3 + 9 * k), c, 0.95f);
        flat(buffers, CIRCLE, pose, 0.06f, r * 0.55f, -time * 7, light, 0.6f);
        flat(buffers, RING, pose, 0.07f, r * 1.15f, 0, light, 0.5f + 0.4f * k);
        // six chains of light drawn up out of the circle's rim, bending in to the soul
        for (int i = 0; i < 6; i++) {
            float a = i / 6f * Mth.TWO_PI + time * 0.05f;
            Vec3 rim = new Vec3(Mth.cos(a) * r, 0.05, Mth.sin(a) * r);
            Vec3 top = new Vec3(Mth.cos(a) * 0.3f, 0.8 + 1.2 * ease, Mth.sin(a) * 0.3f);
            streak(buffers, pose, camera, s.pos, rim, rim.lerp(top, Math.min(1, k * 1.6)), 0.12f, light, 0.8f * Math.min(1, k * 3));
        }
        dot(buffers, pose, camera, 0, 1f + 0.6f * ease, 0, 0.5f + 1.6f * ease, c, 0.35f + 0.4f * k);
        dot(buffers, pose, camera, 0, 1f + 0.6f * ease, 0, 0.25f + 0.5f * ease, 0xFFFFFF, 0.5f * k);
        // the thread of mana from the caster's hand
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            Vec3 hand = mc.player.getPosition(partial).add(0, mc.player.getBbHeight() * 0.62, 0)
                    .add(Vec3.directionFromRotation(0, mc.player.getViewYRot(partial) + 35).scale(0.45));
            if (mc.options.getCameraType().isFirstPerson()) {
                hand = mc.player.getEyePosition(partial).add(mc.player.getViewVector(partial).scale(0.7)).add(0, -0.35, 0);
            }
            Vec3 a = hand.subtract(s.pos);
            Vec3 b = new Vec3(0, 1f + 0.6f * ease, 0);
            if (a.distanceToSqr(b) < 15 * 15) streak(buffers, pose, camera, s.pos, a, b, 0.08f + 0.06f * Mth.sin(time * 0.8f), light, 0.75f);
        }
    }

    /** A point on the arc from a to b, rising a little over the straight line. */
    private static Vec3 arc(Vec3 a, Vec3 b, float t) {
        double lift = Math.min(2.5, a.distanceTo(b) * 0.25) * 4 * t * (1 - t);
        return a.lerp(b, t).add(0, lift, 0);
    }

    // ---- drawing helpers: each fetches its buffer right before drawing (see the gates' "Not building!" note)

    private static void dot(MultiBufferSource buffers, PoseStack pose, Camera camera, float x, float y, float z, float half, int rgb, float alpha) {
        GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, x, y, z, half, rgb, Math.min(1, alpha));
    }

    private static void flat(MultiBufferSource buffers, ResourceLocation tex, PoseStack pose, float y, float radius, float deg, int rgb, float alpha) {
        if (alpha <= 0.01f || radius <= 0.01f) return;
        GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(tex)), pose, y, radius, deg, rgb, Math.min(1, alpha));
    }

    private static void column(MultiBufferSource buffers, PoseStack pose, Camera camera, Vec3 at, float half, float height, int rgb, float alpha) {
        if (alpha <= 0.01f || height <= 0.01f) return;
        GateFx.column(buffers.getBuffer(ModRenderTypes.lightTexture(BEAM)), pose, camera, at.x, at.z, half, height, rgb, Math.min(1, alpha));
    }

    /** A camera-facing streak from a to b (relative to the pose origin, which sits at {@code origin}). */
    private static void streak(MultiBufferSource buffers, PoseStack pose, Camera camera, Vec3 origin, Vec3 a, Vec3 b, float width, int rgb, float alpha) {
        if (alpha <= 0.01f) return;
        Vec3 dir = b.subtract(a);
        if (dir.lengthSqr() < 1.0e-4) return;
        Vec3 mid = origin.add(a.add(b).scale(0.5));
        Vec3 side = dir.cross(camera.getPosition().subtract(mid));
        if (side.lengthSqr() < 1.0e-6) side = dir.cross(new Vec3(0, 1, 0));
        side = side.normalize().scale(width / 2);
        VertexConsumer out = buffers.getBuffer(ModRenderTypes.lightTexture(STREAK));
        Matrix4f m = pose.last().pose();
        int al = Mth.clamp(Math.round(Math.min(1f, alpha) * 255), 0, 255);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, bl = rgb & 0xFF;
        out.addVertex(m, (float) (a.x - side.x), (float) (a.y - side.y), (float) (a.z - side.z)).setUv(0, 1).setColor(r, g, bl, al);
        out.addVertex(m, (float) (b.x - side.x), (float) (b.y - side.y), (float) (b.z - side.z)).setUv(1, 1).setColor(r, g, bl, al);
        out.addVertex(m, (float) (b.x + side.x), (float) (b.y + side.y), (float) (b.z + side.z)).setUv(1, 0).setColor(r, g, bl, al);
        out.addVertex(m, (float) (a.x + side.x), (float) (a.y + side.y), (float) (a.z + side.z)).setUv(0, 0).setColor(r, g, bl, al);
    }

    public static void clear() {
        SOULS.clear();
        ACTIVE.clear();
    }

    private ShadowWorldFx() {}
}
