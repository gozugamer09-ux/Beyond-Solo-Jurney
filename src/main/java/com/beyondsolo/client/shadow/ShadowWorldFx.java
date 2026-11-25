package com.beyondsolo.client.shadow;

import com.beyondsolo.client.fx.Aura;
import com.beyondsolo.client.gate.GateFx;
import com.beyondsolo.client.skill.FxParticles;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.network.ShadowFxPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The shadow army in the world, drawn the way the series draws it (the art style guide in the project files): ink and
 * black flame with a burning edge, never circles or pillars of light, which belong to the other side. A soul lingers
 * as a small pool of ink with a black flame on it and an ember of its light; the extraction ritual spreads the pool,
 * raises a ring of black flame and draws ink tendrils up to the soul while a hot thread runs from the necromancer's
 * hand; the shadow answering bursts the pool outward with an eruption of black flame; a refused try splashes the ink
 * with a red rim, a crumbling soul dries to ash. Shadows called up and sent back splash and close their pools; a
 * rank-up or a promotion raises a pillar of black flame (a promotion sets a crown of it over the head); essence flies
 * to its master as a streak of the soul's light. Pools, flame and streaks come from {@link Aura}; smoke and sparks
 * are the skill effects' own particles ({@link FxParticles}), the smoke ink-dark. Runs on game time.
 */
public final class ShadowWorldFx {
    /** The smoke of the shadow side: blue-black, never grey. */
    private static final int INK_SMOKE = 0x0C0B18;

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
        int c = p.color();
        switch (p.kindEnum()) {
            case SOUL -> {
                Soul s = SOULS.get(p.id());
                if (s == null) SOULS.put(p.id(), new Soul(p.id(), at, c, p.extra(), p.text(), t));
                else s.tries = p.extra();
                FxParticles.smoke(at.x, at.y + 0.2, at.z, 0.3, 6, INK_SMOKE, 0.3f, 0.03f);
            }
            case SOUL_GONE -> {
                SOULS.remove(p.id());
                FxParticles.smoke(at.x, at.y + 0.2, at.z, 0.3, 5, INK_SMOKE, 0.28f, 0.03f);
            }
            case RITUAL -> {
                Soul s = SOULS.computeIfAbsent(p.id(), id -> new Soul(id, at, c, 3, p.text(), t));
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
                // the ink splashes out and the ember sputters red
                FxParticles.smokeRing(at.x, at.y + 0.1, at.z, 14, INK_SMOKE, 0.32f, 0.14f);
                FxParticles.sparks(at.x, at.y + 0.8, at.z, 10, 0.22f, 0xFF5060, 0.02f);
            }
            case CRUMBLE -> {
                SOULS.remove(p.id());
                add(p, at, t);
                for (int i = 0; i < 40; i++) {
                    mc.level.addParticle(i % 2 == 0 ? ParticleTypes.ASH : ParticleTypes.WHITE_ASH, at.x + rnd(0.6), at.y + RANDOM.nextDouble() * 1.4,
                            at.z + rnd(0.6), 0, -0.02, 0);
                }
                FxParticles.smoke(at.x, at.y + 0.3, at.z, 0.4, 10, INK_SMOKE, 0.32f, 0.02f);
            }
            case EXTRACTED -> {
                SOULS.remove(p.id());
                add(p, at, t);
                boolean boss = p.extra() == 1;
                // the pool bursts: ink thrown out over the ground, black smoke boiling up, sparks of its light
                FxParticles.smokeRing(at.x, at.y + 0.15, at.z, boss ? 30 : 22, INK_SMOKE, boss ? 0.6f : 0.45f, boss ? 0.32f : 0.22f);
                FxParticles.smoke(at.x, at.y + 0.5, at.z, 0.5, boss ? 22 : 14, INK_SMOKE, boss ? 0.6f : 0.45f, 0.08f);
                FxParticles.motes(at.x, at.y + 0.6, at.z, 0.6, boss ? 24 : 14, c, 0.06f);
                FxParticles.sparks(at.x, at.y + 0.9, at.z, boss ? 20 : 12, 0.3f, c, 0.01f);
            }
            case SUMMON -> {
                add(p, at, t);
                FxParticles.smoke(at.x, at.y + 0.2, at.z, 0.5, 12, INK_SMOKE, 0.45f, 0.07f);
                FxParticles.smokeRing(at.x, at.y + 0.1, at.z, 10, INK_SMOKE, 0.3f, 0.12f);
            }
            case RECALL -> {
                add(p, at, t);
                // ink drawn down into the closing pool
                for (int i = 0, n = FxParticles.count(18); i < n; i++) {
                    double a = i * Math.PI * 2 / n;
                    FxParticles.add(FxParticles.Tex.SMOKE, at.x + Math.cos(a) * 1.0, at.y + 1.2, at.z + Math.sin(a) * 1.0, -Math.cos(a) * 0.05, -0.06,
                                    -Math.sin(a) * 0.05)
                            .frame(RANDOM.nextInt(16)).blend().life(18).size(0.3f, 0.12f).color(INK_SMOKE).alpha(0.5f).fade(0.15f, 0.5f);
                }
            }
            case RANK_UP -> {
                add(p, at, t);
                FxParticles.smoke(at.x, at.y + 0.3, at.z, 0.6, 20, INK_SMOKE, 0.55f, 0.12f);
                FxParticles.sparks(at.x, at.y + 1.0, at.z, 24, 0.3f, c, 0.01f);
            }
            case PROMOTED -> {
                add(p, at, t);
                // a blast of ink over the ground, black smoke rolling up the pillar, sparks of the new rank's light
                FxParticles.smokeRing(at.x, at.y + 0.15, at.z, 30, INK_SMOKE, 0.6f, 0.35f);
                FxParticles.smoke(at.x, at.y + 0.6, at.z, 0.7, 28, INK_SMOKE, 0.65f, 0.16f);
                FxParticles.sparks(at.x, at.y + 1.2, at.z, 40, 0.4f, c, 0.01f);
                FxParticles.motes(at.x, at.y + 0.8, at.z, 0.9, 24, c, 0.08f);
            }
            case ESSENCE -> {
                // the soul drifts to its master: the streak travels (drawn in render), nothing stays behind
                SOULS.remove(p.id());
                add(p, at, t);
                FxParticles.motes(at.x, at.y + 0.4, at.z, 0.3, 6, c, 0.03f);
            }
            case NAMED -> {
                add(p, at, t);
                FxParticles.sparks(at.x, at.y + 0.2, at.z, 20, 0.18f, c, 0f);
                FxParticles.smoke(at.x, at.y + 0.2, at.z, 0.5, 8, INK_SMOKE, 0.35f, 0.08f);
            }
            default -> {
            }
        }
    }

    private static void add(ShadowFxPayload p, Vec3 at, float t) {
        ACTIVE.add(new Fx(p.kindEnum(), at, p.color(), t, p.id(), p.extra()));
        if (ACTIVE.size() > 64) ACTIVE.remove(0);
    }

    private static double rnd(double s) {
        return (RANDOM.nextDouble() - 0.5) * 2 * s;
    }

    /** Ink smoke and embers drifting off each soul; the ritual drawing smoke in. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float t = now(0);
        long tick = mc.level.getGameTime();
        for (Soul s : SOULS.values()) {
            Vec3 at = s.pos;
            if (tick % 4 == 0) FxParticles.smoke(at.x, at.y + 0.15, at.z, 0.2, 1, INK_SMOKE, 0.2f, 0.035f);
            if (tick % 9 == 0) FxParticles.motes(at.x, at.y + 0.4, at.z, 0.2, 1, s.color, 0.025f);
            if (s.inRitual(t)) {
                float k = s.ritualProgress(t);
                for (int i = 0, n = 1 + (int) (k * 2.5f); i < n; i++) {
                    double a = RANDOM.nextDouble() * Math.PI * 2;
                    double r = 2.2 + RANDOM.nextDouble() * 1.2;
                    FxParticles.add(FxParticles.Tex.SMOKE, at.x + Math.cos(a) * r, at.y + 0.1, at.z + Math.sin(a) * r, -Math.cos(a) * r * 0.06, 0.015,
                                    -Math.sin(a) * r * 0.06)
                            .frame(RANDOM.nextInt(16)).blend().life(16).size(0.25f, 0.4f).color(INK_SMOKE).alpha(0.45f).fade(0.2f, 0.6f).drag(0.94f);
                }
                if (tick % 2 == 0) FxParticles.motes(at.x, at.y + 0.3, at.z, 0.4, 1, s.color, 0.05f + 0.1f * k);
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
            // a small pool of ink, a black flame on it and the ember of the soul over it
            if (!ritual) {
                Aura.pool(pose, buffers, 0.03f, 0.55f * in, s.color, 0.85f * in, time, 0.35f);
                flame(pose, buffers, 0.02f, 0.2f, 0.6f * in * pulse, s.color, 0.85f * in, time, s.id);
            }
            spark(buffers, pose, camera, 0, 0.7f + 0.08f * Mth.sin(time * 0.12f), 0, 0.22f * in * pulse, s.color, 0.9f * in);
            // a try refused: the ink splashes out with a red edge
            if (crack > 0) Aura.splash(pose, buffers, 0.035f, 0.8f + 1.6f * (1 - crack * crack), 0xFF5060, 0.85f * crack, s.id * 53f);
            if (ritual) ritual(buffers, pose, camera, s, time, partial);
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
            switch (fx.kind) {
                case EXTRACTED -> {
                    float big = fx.extra == 1 ? 1.8f : 1f;
                    float flash = Mth.clamp(1 - age / 0.35f, 0, 1);
                    float rise = Mth.clamp(age / 0.25f, 0, 1);
                    // the pool bursts outward, ink thrown out over the ground, and a column of black flame erupts out of it
                    Aura.pool(pose, buffers, 0.03f, (0.8f + 1.4f * out) * big, c, fade, time, 1f - t);
                    float throwOut = Mth.clamp(age / 0.8f, 0, 1);
                    if (throwOut < 1) {
                        float ease = 1 - (1 - throwOut) * (1 - throwOut);
                        Aura.splash(pose, buffers, 0.035f, (0.8f + 2.2f * ease) * big, c, (1 - throwOut * throwOut) * 0.9f, fx.entityId * 37f);
                    }
                    flame(pose, buffers, 0.02f, (0.45f + 0.35f * out) * big, 2.6f * big * rise * (1 - 0.5f * t), c, fade, time, fx.entityId);
                    spark(buffers, pose, camera, 0, 1f, 0, 1.2f * big * (0.5f + 0.5f * out), c, flash);
                }
                case FAILED -> {
                    Aura.pool(pose, buffers, 0.03f, 2.2f * (1 - 0.6f * out), 0xFF5060, fade * 0.8f, time, 0.5f);
                    spark(buffers, pose, camera, 0, 0.8f, 0, 0.6f * (1 - out), 0xFF6070, fade * 0.7f);
                }
                case CRUMBLE -> {
                    // the pool dries to ash: it greys and shrinks away
                    Aura.pool(pose, buffers, 0.03f, 0.9f * (1 - 0.5f * out), 0x807890, fade * 0.6f, time, 0f);
                    spark(buffers, pose, camera, 0, 0.7f - 0.5f * out, 0, 0.3f * fade, c, fade * 0.5f);
                }
                case SUMMON -> {
                    float k = fx.extra == 1 ? 1.6f : 1f;
                    Aura.pool(pose, buffers, 0.03f, (0.6f + 1.4f * out) * k, c, fade * 0.85f, time, 1f);
                    flame(pose, buffers, 0.02f, 0.45f * k, 1.6f * k * (1 - t), c, fade, time, fx.entityId);
                }
                case RECALL -> {
                    Aura.pool(pose, buffers, 0.03f, 1.4f * (1 - 0.7f * out), c, fade, time, 1f);
                    flame(pose, buffers, 0.02f, 0.5f * (1 - 0.5f * out), 1.3f * (1 - out), c, fade, time, fx.entityId);
                }
                case RANK_UP -> {
                    float up = Mth.clamp(age / 0.3f, 0, 1);
                    float hold = Mth.clamp((life - age) / 0.8f, 0, 1);
                    // a pillar of black flame from the pool it stands in, and a blast of ink over the ground
                    Aura.pool(pose, buffers, 0.03f, 1.3f, c, hold, time, 0.6f);
                    float blast = Mth.clamp(age / 1.0f, 0, 1);
                    if (blast < 1) {
                        float ease = 1 - (1 - blast) * (1 - blast);
                        Aura.splash(pose, buffers, 0.035f, 1.0f + 2.6f * ease, c, (1 - blast * blast) * 0.9f, fx.entityId * 37f);
                    }
                    flame(pose, buffers, 0.02f, 0.6f + 0.05f * Mth.sin(time * 0.4f), 3.6f * up, c, hold, time, fx.entityId);
                }
                case PROMOTED -> {
                    Entity e = mc.level.getEntity(fx.entityId);
                    float eh = e != null ? e.getBbHeight() : 1.8f;
                    float up = Mth.clamp(age / 0.25f, 0, 1);
                    float hold = Mth.clamp((life - age) / 1.0f, 0, 1);
                    float flash = Mth.clamp(1 - age / 0.3f, 0, 1);
                    // the pillar of black flame it is raised in, ink blasting out over the ground twice
                    Aura.pool(pose, buffers, 0.03f, 2.0f, c, hold, time, 0.8f);
                    for (int i = 0; i < 2; i++) {
                        float rt = Mth.clamp((age - i * 0.45f) / 1.2f, 0, 1);
                        if (rt <= 0 || rt >= 1) continue;
                        float ease = 1 - (1 - rt) * (1 - rt);
                        Aura.splash(pose, buffers, 0.035f + i * 0.004f, 1.2f + 3.6f * ease, c, (1 - rt * rt) * 0.9f, fx.entityId * 37f + i * 140f);
                    }
                    flame(pose, buffers, 0.02f, 0.85f + 0.08f * Mth.sin(time * 0.5f), 5.2f * up, c, hold, time, fx.entityId);
                    // a crown of black flame set over its head
                    float crown = Mth.clamp((age - 0.15f) / 0.6f, 0, 1);
                    flame(pose, buffers, eh + 0.05f, 0.32f, 0.9f * crown, c, hold, time, fx.entityId + 5);
                    spark(buffers, pose, camera, 0, eh * 0.6f, 0, 1.6f * (0.5f + 0.5f * out), c, flash);
                }
                case ESSENCE -> {
                    // a streak of the soul's light flying from the soul to its master's chest, in an arc
                    Entity master = mc.level.getEntity(fx.extra);
                    if (master == null) break;
                    Vec3 to = master.getPosition(partial).add(0, master.getBbHeight() * 0.6, 0).subtract(at);
                    Vec3 from = new Vec3(0, 0.7, 0);
                    float k = Mth.clamp(age / (life * 0.85f), 0, 1);
                    float e1 = k * k * (3 - 2 * k);
                    float e0 = Math.max(0, e1 - 0.15f);
                    Vec3 p1 = arc(from, to, e1);
                    Vec3 p0 = arc(from, to, e0);
                    if (k < 1) {
                        streak(buffers, pose, camera, at, p1, p0, 0.2f, c, 0.9f);
                        spark(buffers, pose, camera, (float) p1.x, (float) p1.y, (float) p1.z, 0.3f, c, 0.95f);
                    } else {
                        float pop = Mth.clamp((age - life * 0.85f) / (life * 0.15f), 0, 1);
                        spark(buffers, pose, camera, (float) to.x, (float) to.y, (float) to.z, 0.3f + 0.4f * pop, c, 0.9f * (1 - pop));
                    }
                }
                case NAMED -> {
                    Entity e = mc.level.getEntity(fx.entityId);
                    float eh = e != null ? e.getBbHeight() : 1.8f;
                    // a ring of black flame rising from its feet to over its head, where the name now hangs
                    flame(pose, buffers, eh * out + 0.05f, 0.55f, 0.4f, c, fade, time, fx.entityId);
                    spark(buffers, pose, camera, 0, eh + 0.45f, 0, 0.5f * fade, c, 0.8f * fade);
                }
                default -> {
                }
            }
            pose.popPose();
        }
        buffers.endBatch(ModRenderTypes.flame(Aura.FLAME));
        for (var tex : new net.minecraft.resources.ResourceLocation[]{Aura.POOL, Aura.SPLASH, Aura.SPARK, Aura.TRAIL}) {
            buffers.endBatch(ModRenderTypes.energy(tex));
        }
    }

    /**
     * The ritual on a soul: the pool spreading under it, a ring of black flame rising round it, ink smoke drawn in to it
     * (see {@link #tick}) and the hot thread from the caster's hand.
     */
    private static void ritual(MultiBufferSource buffers, PoseStack pose, Camera camera, Soul s, float time, float partial) {
        float k = s.ritualProgress(time);
        float ease = 1 - (1 - k) * (1 - k);
        int c = s.color;
        float r = 0.6f + 2.2f * ease;
        Aura.pool(pose, buffers, 0.03f, r, c, 0.95f, time, 0.5f + 0.5f * k);
        flame(pose, buffers, 0.02f, 0.3f + 0.25f * ease, 0.6f + 1.4f * ease, c, 0.9f, time, s.id);
        spark(buffers, pose, camera, 0, 1f + 0.6f * ease, 0, 0.18f + 0.3f * ease, c, 0.6f + 0.4f * k);
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
            if (a.distanceToSqr(b) < 15 * 15) streak(buffers, pose, camera, s.pos, b, a, 0.07f + 0.04f * Mth.sin(time * 0.8f), c, 0.8f);
        }
    }

    /** A point on the arc from a to b, rising a little over the straight line. */
    private static Vec3 arc(Vec3 a, Vec3 b, float t) {
        double lift = Math.min(2.5, a.distanceTo(b) * 0.25) * 4 * t * (1 - t);
        return a.lerp(b, t).add(0, lift, 0);
    }

    // ---- drawing helpers: each fetches its buffer right before drawing (see the gates' "Not building!" note)

    /** Black flame in a ring {@code radius} round the pose's origin, {@code height} tall, over a low haze of its light. */
    private static void flame(PoseStack pose, MultiBufferSource buffers, float y, float radius, float height, int rgb, float alpha, float time,
                              int seed) {
        if (alpha <= 0.01f || height <= 0.02f) return;
        Aura.ring(pose, buffers, y, radius * 0.9f, height * 0.6f, 0.1f, rgb, alpha * 0.55f, time, seed + 11, false);
        Aura.ring(pose, buffers, y, radius, height, 0.15f, Aura.dark(rgb, 0.9f), alpha, time, seed, false);
    }

    /** A hot point of light with four short glints, facing the camera. */
    private static void spark(MultiBufferSource buffers, PoseStack pose, Camera camera, float x, float y, float z, float half, int rgb, float alpha) {
        if (alpha <= 0.01f || half <= 0.005f) return;
        GateFx.dot(buffers.getBuffer(ModRenderTypes.energy(Aura.SPARK)), pose, camera, x, y, z, half, rgb, Math.min(1, alpha));
    }

    /** A streak of light from a (hot) to b (its tail), facing the camera (relative to the pose origin at {@code origin}). */
    private static void streak(MultiBufferSource buffers, PoseStack pose, Camera camera, Vec3 origin, Vec3 a, Vec3 b, float width, int rgb, float alpha) {
        ribbon(buffers, pose, camera, origin, a, b, width, rgb, alpha);
    }

    private static void ribbon(MultiBufferSource buffers, PoseStack pose, Camera camera, Vec3 origin, Vec3 a, Vec3 b, float width, int rgb, float alpha) {
        if (alpha <= 0.01f) return;
        Vec3 dir = b.subtract(a);
        if (dir.lengthSqr() < 1.0e-4) return;
        Vec3 mid = origin.add(a.add(b).scale(0.5));
        Vec3 side = dir.cross(camera.getPosition().subtract(mid));
        if (side.lengthSqr() < 1.0e-6) side = dir.cross(new Vec3(0, 1, 0));
        side = side.normalize().scale(width / 2);
        VertexConsumer out = buffers.getBuffer(ModRenderTypes.energy(Aura.TRAIL));
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
