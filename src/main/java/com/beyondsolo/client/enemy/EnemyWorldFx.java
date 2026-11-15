package com.beyondsolo.client.enemy;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.gate.GateFx;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.enemy.Affinity;
import com.beyondsolo.enemy.EnemyTraits;
import com.beyondsolo.enemy.Variant;
import com.beyondsolo.network.EnemyFxPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The roster's light in the world, drawn with the mod's light render type:
 * <ul>
 *   <li>warnings on the floor before a big hit: a ring, a strip or a wedge in the attacker's colour that fills from
 *   the attacker outwards and flashes as the hit lands</li>
 *   <li>bursts and pillars of light where things strike</li>
 *   <li>the aura of a notable creature: a turning ring at its feet (blue for an elite, gold for a champion) and a
 *   soft glow of its affinity</li>
 * </ul>
 * Runs on game time, so it pauses with the game and follows /tick rate.
 */
public final class EnemyWorldFx {
    private static final ResourceLocation GLOW = BeyondSolo.id("textures/effect/glow.png");
    private static final ResourceLocation RING = BeyondSolo.id("textures/effect/ring.png");
    private static final ResourceLocation CIRCLE = BeyondSolo.id("textures/effect/magic_circle.png");
    private static final ResourceLocation BEAM = BeyondSolo.id("textures/effect/beam.png");
    private static final ResourceLocation SOLID = BeyondSolo.id("textures/effect/telegraph_fill.png");
    private static final ResourceLocation EDGE = BeyondSolo.id("textures/effect/telegraph_edge.png");
    private static final ResourceLocation[] ALL = {GLOW, RING, CIRCLE, BEAM, SOLID, EDGE};

    private record Fx(EnemyFxPayload p, float start) {}

    private static final List<Fx> ACTIVE = new ArrayList<>();

    static void add(EnemyFxPayload p) {
        ACTIVE.add(new Fx(p, now(0)));
    }

    static void clear() {
        ACTIVE.clear();
    }

    private static float now(float partial) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime() + partial;
    }

    public static void render(PoseStack pose, Camera camera, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 cam = camera.getPosition();
        float time = now(partial);
        boolean any = false;

        Iterator<Fx> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Fx fx = it.next();
            EnemyFxPayload p = fx.p;
            float age = time - fx.start;
            float life = p.ticks() + (p.kindEnum() == EnemyFxPayload.Kind.BURST || p.kindEnum() == EnemyFxPayload.Kind.PILLAR ? 0 : 6);
            if (age > life || age < -2) {
                it.remove();
                continue;
            }
            any = true;
            pose.pushPose();
            pose.translate(p.x() - cam.x, p.y() - cam.y + 0.06, p.z() - cam.z);
            switch (p.kindEnum()) {
                case CIRCLE -> circle(buffers, pose, p, age, time);
                case LINE -> line(buffers, pose, p, age);
                case CONE -> cone(buffers, pose, p, age);
                case BURST -> burst(buffers, pose, camera, p, age);
                case PILLAR -> pillar(buffers, pose, camera, p, age);
            }
            pose.popPose();
        }

        // auras of notable creatures
        for (var e : EnemyClient.all().int2ObjectEntrySet()) {
            Entity ent = mc.level.getEntity(e.getIntKey());
            if (!(ent instanceof LivingEntity le) || !le.isAlive() || ent.distanceToSqr(cam) > 48 * 48) continue;
            any = true;
            aura(buffers, pose, camera, cam, le, e.getValue(), time, partial);
        }
        if (any) for (ResourceLocation tex : ALL) buffers.endBatch(ModRenderTypes.lightTexture(tex));
    }

    // ------------------------------------------------------------------ warnings

    /** 0..1 how full a warning is, and the flash as it lands. */
    private static float fill(EnemyFxPayload p, float age) {
        return Mth.clamp(age / Math.max(1, p.ticks()), 0, 1);
    }

    private static float fade(EnemyFxPayload p, float age) {
        float in = Mth.clamp(age / 3f, 0, 1);
        float out = age > p.ticks() ? 1 - (age - p.ticks()) / 6f : 1;
        return in * Mth.clamp(out, 0, 1);
    }

    private static void circle(MultiBufferSource buffers, PoseStack pose, EnemyFxPayload p, float age, float time) {
        float k = fill(p, age), a = fade(p, age);
        float flash = age > p.ticks() ? 1 - (age - p.ticks()) / 6f : 0;
        int c = p.color();
        float r = p.size();
        GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(RING)), pose, 0, r * 1.06f, 0, c, 0.9f * a);
        GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(CIRCLE)), pose, 0.01f, r, time * 2, c, 0.25f * a);
        GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, 0.02f, r * (0.15f + 0.95f * k), 0, c, (0.25f + 0.35f * k) * a);
        if (flash > 0) GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, 0.03f, r * 1.4f, 0, GateFx.mix(c, 0xFFFFFF, 0.5f), flash);
    }

    /** A flat quad on the ground: from {@code from} to {@code to} metres along the direction, {@code half} wide each side. */
    private static void strip(VertexConsumer out, Matrix4f m, float dx, float dz, float from, float to, float half, int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        if (a <= 2 || to <= from) return;
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        float px = -dz * half, pz = dx * half;
        out.addVertex(m, dx * from - px, 0, dz * from - pz).setUv(0, 1).setColor(r, g, b, a);
        out.addVertex(m, dx * from + px, 0, dz * from + pz).setUv(1, 1).setColor(r, g, b, a);
        out.addVertex(m, dx * to + px, 0, dz * to + pz).setUv(1, 0).setColor(r, g, b, a);
        out.addVertex(m, dx * to - px, 0, dz * to - pz).setUv(0, 0).setColor(r, g, b, a);
    }

    private static void line(MultiBufferSource buffers, PoseStack pose, EnemyFxPayload p, float age) {
        float k = fill(p, age), a = fade(p, age);
        float flash = age > p.ticks() ? 1 - (age - p.ticks()) / 6f : 0;
        Matrix4f m = pose.last().pose();
        int c = p.color();
        float half = p.size() / 2;
        strip(buffers.getBuffer(ModRenderTypes.lightTexture(EDGE)), m, p.dx(), p.dz(), 0, p.length(), half, c, 0.85f * a);
        strip(buffers.getBuffer(ModRenderTypes.lightTexture(SOLID)), m, p.dx(), p.dz(), 0, p.length() * k, half * 0.92f, c, 0.3f * a);
        if (flash > 0) strip(buffers.getBuffer(ModRenderTypes.lightTexture(SOLID)), m, p.dx(), p.dz(), 0, p.length(), half, GateFx.mix(c, 0xFFFFFF, 0.5f), 0.7f * flash);
    }

    private static void cone(MultiBufferSource buffers, PoseStack pose, EnemyFxPayload p, float age) {
        float k = fill(p, age), a = fade(p, age);
        float flash = age > p.ticks() ? 1 - (age - p.ticks()) / 6f : 0;
        Matrix4f m = pose.last().pose();
        int c = p.color();
        float half = p.size() * Mth.DEG_TO_RAD;
        float base = (float) Math.atan2(p.dz(), p.dx());
        int segments = Math.max(6, Math.round(p.size() / 5));
        VertexConsumer fillOut = buffers.getBuffer(ModRenderTypes.lightTexture(SOLID));
        float reach = p.length() * k;
        for (int i = 0; i < segments; i++) {
            float a0 = base - half + 2 * half * i / segments, a1 = base - half + 2 * half * (i + 1) / segments;
            wedge(fillOut, m, a0, a1, reach, c, (0.28f + 0.5f * flash) * a);
            if (flash > 0) wedge(fillOut, m, a0, a1, p.length(), GateFx.mix(c, 0xFFFFFF, 0.5f), 0.5f * flash);
        }
        // the outline: both sides and the far arc
        VertexConsumer edge = buffers.getBuffer(ModRenderTypes.lightTexture(SOLID));
        for (float side : new float[]{-half, half}) {
            float ang = base + side;
            strip(edge, m, Mth.cos(ang), Mth.sin(ang), 0, p.length(), 0.06f, c, 0.9f * a);
        }
        for (int i = 0; i < segments * 2; i++) {
            float a0 = base - half + 2 * half * i / (segments * 2), a1 = base - half + 2 * half * (i + 1) / (segments * 2);
            arc(edge, m, a0, a1, p.length(), 0.12f, c, 0.9f * a);
        }
    }

    private static void wedge(VertexConsumer out, Matrix4f m, float a0, float a1, float r, int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        if (a <= 2 || r <= 0.01f) return;
        int red = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        out.addVertex(m, 0, 0, 0).setUv(0.5f, 1).setColor(red, g, b, a);
        out.addVertex(m, 0, 0, 0).setUv(0.5f, 1).setColor(red, g, b, a);
        out.addVertex(m, Mth.cos(a1) * r, 0, Mth.sin(a1) * r).setUv(1, 0).setColor(red, g, b, a);
        out.addVertex(m, Mth.cos(a0) * r, 0, Mth.sin(a0) * r).setUv(0, 0).setColor(red, g, b, a);
    }

    private static void arc(VertexConsumer out, Matrix4f m, float a0, float a1, float r, float w, int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        if (a <= 2) return;
        int red = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        out.addVertex(m, Mth.cos(a0) * (r - w), 0, Mth.sin(a0) * (r - w)).setUv(0, 1).setColor(red, g, b, a);
        out.addVertex(m, Mth.cos(a1) * (r - w), 0, Mth.sin(a1) * (r - w)).setUv(1, 1).setColor(red, g, b, a);
        out.addVertex(m, Mth.cos(a1) * r, 0, Mth.sin(a1) * r).setUv(1, 0).setColor(red, g, b, a);
        out.addVertex(m, Mth.cos(a0) * r, 0, Mth.sin(a0) * r).setUv(0, 0).setColor(red, g, b, a);
    }

    // ------------------------------------------------------------------ strikes

    private static void burst(MultiBufferSource buffers, PoseStack pose, Camera camera, EnemyFxPayload p, float age) {
        float t = Mth.clamp(age / p.ticks(), 0, 1);
        float out = 1 - (1 - t) * (1 - t);
        int c = p.color();
        GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, 0, 0, 0, p.size() * (0.5f + out), GateFx.mix(c, 0xFFFFFF, 0.4f), 1 - t);
        GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, 0, 0, 0, p.size() * (1 + 1.5f * out), c, 0.5f * (1 - t));
        pose.pushPose();
        pose.mulPose(camera.rotation());
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
        GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(RING)), pose, 0, p.size() * (0.4f + 1.6f * out), 0, c, 0.8f * (1 - t));
        pose.popPose();
    }

    private static void pillar(MultiBufferSource buffers, PoseStack pose, Camera camera, EnemyFxPayload p, float age) {
        float t = Mth.clamp(age / p.ticks(), 0, 1);
        float rise = Mth.clamp(age / 3f, 0, 1);
        int c = p.color();
        VertexConsumer b = buffers.getBuffer(ModRenderTypes.lightTexture(BEAM));
        GateFx.column(b, pose, camera, p.x(), p.z(), p.size() * (1.2f - 0.6f * t), p.length() * rise, c, 0.8f * (1 - t));
        GateFx.column(b, pose, camera, p.x(), p.z(), p.size() * 0.4f, p.length() * rise * 0.9f, 0xFFFFFF, 0.9f * (1 - t));
        GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, 0.02f, p.size() * 2f, 0, c, 0.7f * (1 - t));
    }

    // ------------------------------------------------------------------ auras

    private static void aura(MultiBufferSource buffers, PoseStack pose, Camera camera, Vec3 cam, LivingEntity e, EnemyTraits t, float time, float partial) {
        Vec3 at = e.getPosition(partial);
        float w = e.getBbWidth();
        pose.pushPose();
        pose.translate(at.x - cam.x, at.y - cam.y + 0.05, at.z - cam.z);
        int ac = t.affinity() == Affinity.NONE ? t.variant().color() : t.affinity().color;
        float pulse = 0.85f + 0.15f * Mth.sin(time * 0.15f + e.getId());
        if (t.variant().special()) {
            int vc = t.variant().color();
            float r = w * 0.75f + 0.5f;
            GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(CIRCLE)), pose, 0, r, time * (t.variant() == Variant.CHAMPION ? 3f : 1.6f), vc, 0.55f * pulse);
            GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(RING)), pose, 0.01f, r * 1.1f, 0, vc, 0.5f);
            if (t.variant() == Variant.CHAMPION) {
                GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(CIRCLE)), pose, 0.02f, r * 0.65f, -time * 4f, ac, 0.5f);
                // motes of light climbing it
                for (int i = 0; i < 6; i++) {
                    float u = (time * 0.02f + i / 6f) % 1f;
                    float ang = i * 1.05f + time * 0.03f;
                    GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, Mth.cos(ang) * r * 0.8f, u * e.getBbHeight() * 1.2f,
                            Mth.sin(ang) * r * 0.8f, 0.09f, GateFx.mix(vc, 0xFFFFFF, 0.4f), Mth.sin(u * Mth.PI));
                }
            }
        }
        if (t.affinity() != Affinity.NONE) {
            GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, 0.03f, w * 0.9f + 0.4f, 0, ac, 0.35f * pulse);
            GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, 0, e.getBbHeight() * 0.55f, 0,
                    Math.max(w, e.getBbHeight() * 0.6f) * 0.9f, ac, 0.12f * pulse);
        }
        pose.popPose();
    }

    private EnemyWorldFx() {}
}
