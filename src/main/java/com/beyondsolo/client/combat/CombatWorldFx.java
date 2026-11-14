package com.beyondsolo.client.combat;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.network.CombatFxPayload;
import com.beyondsolo.network.CombatStatePayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Combat effects in the world. Light (rings, sigils, glows) is drawn with the mod's light render type;
 * vanilla particles are only used for bursts and embers, where their look fits. Runs on game time, so it pauses
 * with the game and follows /tick rate. The player's own effects are skipped in first person (the screen
 * flash in {@link CombatHud} carries them).
 */
public final class CombatWorldFx {
    public static final ResourceLocation GLOW = BeyondSolo.id("textures/effect/glow.png");
    public static final ResourceLocation RING = BeyondSolo.id("textures/effect/ring.png");
    public static final ResourceLocation SIGIL = BeyondSolo.id("textures/effect/brand_sigil.png");
    public static final ResourceLocation BURST = BeyondSolo.id("textures/effect/burst.png");

    private record Burst(int entityId, Vec3 fallback, CombatFxPayload.Kind kind, int param, float start) {}

    private static final List<Burst> BURSTS = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private static float now(float partial) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime() + partial;
    }

    public static void play(CombatFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(p.entityId());
        if (e == null) return;
        if (e == mc.player) CombatHud.selfFx(p.kind(), p.param());
        BURSTS.add(new Burst(p.entityId(), e.position(), p.kind(), p.param(), now(0)));
        if (BURSTS.size() > 64) BURSTS.remove(0);
        if (e == mc.player && mc.options.getCameraType().isFirstPerson()) return;
        spawnBurstParticles(mc.level, e, p.kind(), p.param());
    }

    private static int elementColor(int index) {
        return DamageElement.byIndex(index).color();
    }

    private static void spawnBurstParticles(ClientLevel level, Entity e, CombatFxPayload.Kind kind, int param) {
        double x = e.getX();
        double y = e.getY() + e.getBbHeight() * 0.6;
        double z = e.getZ();
        switch (kind) {
            case BLOOD_POP -> {
                burst(level, new DustParticleOptions(new Vector3f(0.62f, 0.02f, 0.05f), 1.6f), x, y, z, 34, 0.35);
                burst(level, new DustParticleOptions(new Vector3f(0.95f, 0.15f, 0.12f), 0.9f), x, y, z, 18, 0.5);
                for (int i = 0; i < 6; i++) level.addParticle(ParticleTypes.DAMAGE_INDICATOR, x, y, z, rnd(0.3), 0.2, rnd(0.3));
            }
            case IGNITE -> {
                burst(level, ParticleTypes.FLAME, x, y, z, 26, 0.18);
                burst(level, ParticleTypes.LAVA, x, y, z, 5, 0.1);
                burst(level, ParticleTypes.LARGE_SMOKE, x, y + 0.3, z, 6, 0.05);
            }
            case VENOM_STACK -> burst(level, new DustParticleOptions(new Vector3f(0.49f, 0.81f, 0.23f), 1.0f), x, y, z, 8 + param * 3, 0.15);
            case HEAVY_VENOM -> {
                burst(level, new DustParticleOptions(new Vector3f(0.25f, 0.5f, 0.08f), 1.8f), x, y, z, 30, 0.25);
                burst(level, ParticleTypes.SNEEZE, x, y, z, 10, 0.08);
            }
            case CRIMSON_BURST -> {
                burst(level, ParticleTypes.CRIMSON_SPORE, x, y, z, 40, 0.6);
                burst(level, new DustParticleOptions(new Vector3f(0.76f, 0.09f, 0.36f), 1.5f), x, y, z, 24, 0.4);
            }
            case CURE -> {
                int c = elementColor(param);
                Vector3f rgb = new Vector3f(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f).lerp(new Vector3f(1, 1, 1), 0.4f);
                for (int i = 0; i < 24; i++) {
                    double a = i / 24.0 * Math.PI * 2;
                    level.addParticle(new DustParticleOptions(rgb, 1.0f), x + Math.cos(a) * 0.6, e.getY() + 0.1, z + Math.sin(a) * 0.6, 0, 0.12, 0);
                }
                burst(level, ParticleTypes.END_ROD, x, y, z, 6, 0.06);
            }
            case ROLL -> burst(level, ParticleTypes.POOF, x, e.getY() + 0.1, z, 8, 0.05);
        }
    }

    private static double rnd(double spread) {
        return (RANDOM.nextDouble() - 0.5) * 2 * spread;
    }

    private static void burst(ClientLevel level, ParticleOptions type, double x, double y, double z, int count, double speed) {
        for (int i = 0; i < count; i++) {
            level.addParticle(type, x + rnd(0.2), y + rnd(0.3), z + rnd(0.2), rnd(speed), rnd(speed) + speed * 0.5, rnd(speed));
        }
    }

    /** Ambient particles for ongoing statuses, a few per tick. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        for (var entry : ClientCombatState.all().int2ObjectEntrySet()) {
            Entity e = mc.level.getEntity(entry.getIntKey());
            CombatStatePayload d = entry.getValue().data;
            if (!(e instanceof LivingEntity) || d == null) continue;
            if (e == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            double w = e.getBbWidth() * 0.6;
            if (d.has(CombatStatePayload.DEEP_BURN) && RANDOM.nextFloat() < 0.45f) {
                mc.level.addParticle(RANDOM.nextFloat() < 0.7f ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME,
                        e.getX() + rnd(w), e.getY() + RANDOM.nextDouble() * e.getBbHeight(), e.getZ() + rnd(w), 0, 0.03, 0);
                if (RANDOM.nextFloat() < 0.15f) mc.level.addParticle(ParticleTypes.SMOKE, e.getX() + rnd(w), e.getY() + e.getBbHeight(), e.getZ() + rnd(w), 0, 0.04, 0);
            }
            if (d.has(CombatStatePayload.HEAVY_VENOM) && RANDOM.nextFloat() < 0.4f) {
                mc.level.addParticle(new DustParticleOptions(new Vector3f(0.3f, 0.55f, 0.1f), 1.2f),
                        e.getX() + rnd(w), e.getY() + RANDOM.nextDouble() * e.getBbHeight(), e.getZ() + rnd(w), 0, 0.02, 0);
            } else if (d.venomStacks() > 0 && RANDOM.nextFloat() < 0.08f * d.venomStacks()) {
                mc.level.addParticle(new DustParticleOptions(new Vector3f(0.49f, 0.81f, 0.23f), 0.7f),
                        e.getX() + rnd(w), e.getY() + RANDOM.nextDouble() * e.getBbHeight(), e.getZ() + rnd(w), 0, 0.02, 0);
            }
            if (d.has(CombatStatePayload.CRIMSON_BRAND) && RANDOM.nextFloat() < 0.2f) {
                mc.level.addParticle(ParticleTypes.CRIMSON_SPORE, e.getX() + rnd(w), e.getY() + RANDOM.nextDouble() * e.getBbHeight(), e.getZ() + rnd(w), 0, 0, 0);
            }
        }
    }

    /** Draws the light part of every effect. Called once per frame after particles. */
    public static void render(PoseStack pose, Camera camera, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 cam = camera.getPosition();
        float time = now(partial);
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();

        // ongoing statuses
        for (var entry : ClientCombatState.all().int2ObjectEntrySet()) {
            Entity e = mc.level.getEntity(entry.getIntKey());
            CombatStatePayload d = entry.getValue().data;
            if (!(e instanceof LivingEntity) || d == null || (e == mc.player && firstPerson)) continue;
            Vec3 at = e.getPosition(partial);
            float size = Math.max(0.6f, e.getBbWidth());
            pose.pushPose();
            pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            if (d.has(CombatStatePayload.CRIMSON_BRAND)) {
                float pulse = 0.75f + 0.25f * Mth.sin(time * 0.25f);
                disc(pose, buffers, SIGIL, 0.03f, size * 1.25f, time * 3f, 0xE0306E, 0.85f * pulse);
                disc(pose, buffers, GLOW, 0.02f, size * 1.6f, 0, 0xC2185B, 0.45f * pulse);
                dot(pose, buffers, camera, 0, e.getBbHeight() + 0.35f, 0, 0.28f, 0xFF4D8A, 0.6f * pulse);
            }
            if (d.has(CombatStatePayload.DEEP_BURN)) {
                float flicker = 0.7f + 0.2f * Mth.sin(time * 0.9f) + 0.1f * Mth.sin(time * 2.3f);
                dot(pose, buffers, camera, 0, e.getBbHeight() * 0.5f, 0, size * 0.95f, 0xFF6A1A, 0.5f * flicker);
                disc(pose, buffers, GLOW, 0.02f, size * 1.1f, 0, 0xFF4A10, 0.4f * flicker);
            }
            if (d.has(CombatStatePayload.HEAVY_VENOM)) {
                float pulse = 0.7f + 0.3f * Mth.sin(time * 0.15f);
                dot(pose, buffers, camera, 0, e.getBbHeight() * 0.5f, 0, size * 0.9f, 0x5BA01E, 0.45f * pulse);
                disc(pose, buffers, GLOW, 0.02f, size * 1.2f, 0, 0x3E7A12, 0.5f * pulse);
            }
            pose.popPose();
        }

        // one-off bursts
        Iterator<Burst> it = BURSTS.iterator();
        while (it.hasNext()) {
            Burst b = it.next();
            float age = (time - b.start) / 20f; // seconds of game time
            float life = switch (b.kind) {
                case CRIMSON_BURST, HEAVY_VENOM -> 1.2f;
                case IGNITE, CURE, BLOOD_POP -> 0.9f;
                default -> 0.5f;
            };
            if (age >= life || age < -1) {
                it.remove();
                continue;
            }
            Entity e = mc.level.getEntity(b.entityId);
            if (e == mc.player && firstPerson) continue;
            Vec3 at = e != null ? e.getPosition(partial) : b.fallback;
            float h = e != null ? e.getBbHeight() : 1.8f;
            float size = e != null ? Math.max(0.6f, e.getBbWidth()) : 0.6f;
            float t = Mth.clamp(age / life, 0, 1);
            float out = 1 - (1 - t) * (1 - t) * (1 - t);
            float fade = 1 - t;
            pose.pushPose();
            pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            switch (b.kind) {
                case BLOOD_POP -> {
                    dotTex(pose, buffers, camera, BURST, 0, h * 0.6f, 0, size * (0.9f + 1.8f * out), 0xE01A2A, fade * 1.3f, b.start * 37);
                    dot(pose, buffers, camera, 0, h * 0.6f, 0, size * (0.7f + 1.2f * out), 0xFF2030, fade);
                    ringAt(pose, buffers, h * 0.55f, size * (0.5f + 2.8f * out), 0xD3172A, fade);
                    ringAt(pose, buffers, 0.05f, size * (0.3f + 2.0f * out), 0x8B0000, fade * 0.8f);
                }
                case IGNITE -> {
                    ringAt(pose, buffers, 0.05f, size * (0.3f + 2.6f * out), 0xFF7A2E, fade);
                    dot(pose, buffers, camera, 0, h * 0.5f, 0, size * (0.5f + 1.2f * out), 0xFF9A3A, fade * 0.9f);
                }
                case VENOM_STACK -> ringAt(pose, buffers, h * 0.5f, size * (0.3f + 1.0f * out), 0x7CCF3A, fade * 0.8f);
                case HEAVY_VENOM -> {
                    ringAt(pose, buffers, 0.05f, size * (0.4f + 2.4f * out), 0x4E9A1E, fade);
                    disc(pose, buffers, SIGIL, 0.04f, size * (0.6f + 0.8f * out), -time * 2, 0x6BC02A, fade * 0.8f);
                }
                case CRIMSON_BURST -> {
                    disc(pose, buffers, SIGIL, 0.05f, size * (0.4f + 1.6f * out), time * 6, 0xFF3D7A, fade * 1.3f);
                    ringAt(pose, buffers, 0.06f, size * (0.6f + 3.2f * out), 0xE0306E, fade);
                    dotTex(pose, buffers, camera, BURST, 0, h * 0.55f, 0, size * (0.5f + 1.0f * out), 0xFF4D8A, fade, b.start * 11);
                }
                case CURE -> {
                    int c = elementColor(b.param);
                    int light = mixRgb(c, 0xFFFFFF, 0.45f);
                    ringAt(pose, buffers, 0.05f + h * out, size * (1.1f - 0.4f * out), light, fade);
                    disc(pose, buffers, GLOW, 0.03f, size * 1.4f, 0, c, fade * 0.7f);
                }
                case ROLL -> ringAt(pose, buffers, 0.05f, size * (0.4f + 1.2f * out), 0xBFE6FF, fade * 0.5f);
            }
            pose.popPose();
        }
        buffers.endBatch(ModRenderTypes.lightTexture(GLOW));
        buffers.endBatch(ModRenderTypes.lightTexture(RING));
        buffers.endBatch(ModRenderTypes.lightTexture(SIGIL));
        buffers.endBatch(ModRenderTypes.lightTexture(BURST));
    }

    private static int mixRgb(int a, int b, float t) {
        int r = Math.round(Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
        int g = Math.round(Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
        int bl = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
        return (r << 16) | (g << 8) | bl;
    }

    private static void ringAt(PoseStack pose, MultiBufferSource buffers, float y, float radius, int rgb, float alpha) {
        disc(pose, buffers, RING, y, radius, 0, rgb, alpha);
    }

    private static void disc(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture, float y, float radius, float degrees, int rgb, float alpha) {
        if (alpha <= 0.01f) return;
        pose.pushPose();
        pose.translate(0, y, 0);
        pose.mulPose(Axis.YP.rotationDegrees(degrees));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        sprite(buffers.getBuffer(ModRenderTypes.lightTexture(texture)), pose.last().pose(), radius, rgb, Math.min(1f, alpha));
        pose.popPose();
    }

    private static void dot(PoseStack pose, MultiBufferSource buffers, Camera camera, float x, float y, float z, float half, int rgb, float alpha) {
        dotTex(pose, buffers, camera, GLOW, x, y, z, half, rgb, alpha, 0);
    }

    private static void dotTex(PoseStack pose, MultiBufferSource buffers, Camera camera, ResourceLocation texture,
                               float x, float y, float z, float half, int rgb, float alpha, float roll) {
        if (alpha <= 0.01f) return;
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(camera.rotation());
        if (roll != 0) pose.mulPose(Axis.ZP.rotationDegrees(roll % 360));
        sprite(buffers.getBuffer(ModRenderTypes.lightTexture(texture)), pose.last().pose(), half, rgb, Math.min(1f, alpha));
        pose.popPose();
    }

    private static void sprite(VertexConsumer out, Matrix4f m, float half, int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        out.addVertex(m, -half, -half, 0).setUv(0, 1).setColor(red, green, blue, a);
        out.addVertex(m, half, -half, 0).setUv(1, 1).setColor(red, green, blue, a);
        out.addVertex(m, half, half, 0).setUv(1, 0).setColor(red, green, blue, a);
        out.addVertex(m, -half, half, 0).setUv(0, 0).setColor(red, green, blue, a);
    }

    public static void clear() {
        BURSTS.clear();
    }

    private CombatWorldFx() {}
}
