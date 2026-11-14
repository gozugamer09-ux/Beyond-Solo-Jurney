package com.beyondsolo.client.skill;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.combat.CombatWorldFx;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.network.SkillFxPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Skill effects in the world. Like the combat effects, light is drawn with the mod's light render type (magic
 * circles, rings, glows, streaks, crescents) and vanilla particles are used for sparks, smoke, debris and embers.
 * Runs on game time, so it pauses with the game and follows /tick rate.
 *
 * <p>The caster's own effects near the face (slash crescent, aura glow) are skipped in first person; ground
 * effects, bolts and zones still show, since the player sees those in front of them.</p>
 */
public final class SkillWorldFx {
    static final ResourceLocation GLOW = CombatWorldFx.GLOW;
    static final ResourceLocation RING = CombatWorldFx.RING;
    static final ResourceLocation SIGIL = CombatWorldFx.SIGIL;
    static final ResourceLocation BURST = CombatWorldFx.BURST;
    static final ResourceLocation CIRCLE = BeyondSolo.id("textures/effect/magic_circle.png");
    static final ResourceLocation STREAK = BeyondSolo.id("textures/effect/streak.png");
    static final ResourceLocation SLASH = BeyondSolo.id("textures/effect/slash.png");
    private static final ResourceLocation[] ALL = {GLOW, RING, SIGIL, BURST, CIRCLE, STREAK, SLASH};

    private static final class Fx {
        final SkillFxPayload p;
        final float start;
        final Vec3 origin;
        final float yaw;
        final long seed;

        Fx(SkillFxPayload p, float start, Vec3 origin, float yaw, long seed) {
            this.p = p;
            this.start = start;
            this.origin = origin;
            this.yaw = yaw;
            this.seed = seed;
        }

        Vec3 target() {
            return new Vec3(p.x(), p.y(), p.z());
        }

        /** Seconds the effect lives. */
        float life() {
            float lasting = p.duration() / 20f;
            return switch (p.kind()) {
                case CAST -> 0.8f;
                case SLASH -> 0.4f;
                case DASH -> 0.6f;
                case PULSE -> 1.4f;
                case ROAR -> 1.0f;
                case SLAM -> 1.2f;
                case BLINK -> 0.7f;
                case BOLT -> 0.4f;
                case NOVA -> 0.9f;
                case METEOR -> lasting + 0.6f;
                case HEAL -> 1.2f;
                case SHOT -> 0.45f;
                case AURA, DOME, ZONE, MARK, VEIL -> Math.max(0.6f, lasting);
            };
        }
    }

    private static final List<Fx> ACTIVE = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private static float now(float partial) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime() + partial;
    }

    public static void play(SkillFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(p.entityId());
        Vec3 origin = e != null ? e.position() : new Vec3(p.x(), p.y(), p.z());
        float yaw = e != null ? e.getYRot() : 0;
        Fx fx = new Fx(p, now(0), origin, yaw, RANDOM.nextLong());
        ACTIVE.add(fx);
        if (ACTIVE.size() > 96) ACTIVE.remove(0);
        burstParticles(mc.level, fx, e);
    }

    private static Vector3f rgb(int color, float towardsWhite) {
        return new Vector3f(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f)
                .lerp(new Vector3f(1, 1, 1), towardsWhite);
    }

    private static double rnd(double s) {
        return (RANDOM.nextDouble() - 0.5) * 2 * s;
    }

    private static void burstParticles(ClientLevel level, Fx fx, Entity e) {
        SkillFxPayload p = fx.p;
        Vec3 o = fx.origin;
        DustParticleOptions dust = new DustParticleOptions(rgb(p.color(), 0.2f), 1.1f);
        boolean self = e == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType().isFirstPerson();
        switch (p.kind()) {
            case CAST -> {
                for (int i = 0; i < 14; i++) {
                    double a = i / 14.0 * Math.PI * 2;
                    level.addParticle(dust, o.x + Math.cos(a) * 0.9, o.y + 0.1, o.z + Math.sin(a) * 0.9, 0, 0.06, 0);
                }
            }
            case SLASH -> {
                if (e == null) break;
                Vec3 look = Vec3.directionFromRotation(0, fx.yaw);
                Vec3 side = new Vec3(-look.z, 0, look.x);
                for (int i = -2; i <= 2; i++) {
                    Vec3 at = o.add(look.scale(2.0 - Math.abs(i) * 0.25)).add(side.scale(i * 0.8)).add(0, 1.1, 0);
                    level.addParticle(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 0, 0, 0);
                    level.addParticle(dust, at.x, at.y, at.z, look.x * 0.2, 0.02, look.z * 0.2);
                }
            }
            case DASH -> {
                for (int i = 0; i < 10; i++) level.addParticle(ParticleTypes.CLOUD, p.x() + rnd(0.3), p.y() + 0.1, p.z() + rnd(0.3), rnd(0.05), 0.02, rnd(0.05));
            }
            case PULSE -> {
                for (int i = 0; i < 40; i++) {
                    double a = i / 40.0 * Math.PI * 2;
                    level.addParticle(ParticleTypes.END_ROD, o.x + Math.cos(a) * 1.2, o.y + 0.2, o.z + Math.sin(a) * 1.2, Math.cos(a) * 0.5, 0.01, Math.sin(a) * 0.5);
                }
            }
            case ROAR -> {
                for (int i = 0; i < 24; i++) {
                    double a = i / 24.0 * Math.PI * 2;
                    level.addParticle(dust, o.x + Math.cos(a) * 0.6, o.y + 1.2, o.z + Math.sin(a) * 0.6, Math.cos(a) * 0.6, 0.02, Math.sin(a) * 0.6);
                }
                if (!self) level.addParticle(ParticleTypes.FLASH, o.x, o.y + 1.2, o.z, 0, 0, 0);
            }
            case SLAM -> {
                Vec3 at = fx.target();
                BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
                ParticleOptions debris = ground.isAir() ? ParticleTypes.POOF : new BlockParticleOption(ParticleTypes.BLOCK, ground);
                for (int i = 0; i < 70; i++) {
                    double a = RANDOM.nextDouble() * Math.PI * 2;
                    double r = RANDOM.nextDouble() * p.size();
                    level.addParticle(debris, at.x + Math.cos(a) * r, at.y + 0.1, at.z + Math.sin(a) * r, Math.cos(a) * 0.2, 0.4 + RANDOM.nextDouble() * 0.4, Math.sin(a) * 0.2);
                }
                for (int i = 0; i < 16; i++) level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x + rnd(2), at.y + 0.2, at.z + rnd(2), 0, 0.03, 0);
            }
            case BLINK -> {
                for (Vec3 at : new Vec3[]{fx.target(), o}) {
                    for (int i = 0; i < 24; i++) level.addParticle(ParticleTypes.PORTAL, at.x + rnd(0.4), at.y + RANDOM.nextDouble() * 1.8, at.z + rnd(0.4), rnd(0.6), rnd(0.3), rnd(0.6));
                    for (int i = 0; i < 8; i++) level.addParticle(ParticleTypes.LARGE_SMOKE, at.x + rnd(0.3), at.y + RANDOM.nextDouble() * 1.6, at.z + rnd(0.3), 0, 0.02, 0);
                }
            }
            case BOLT -> {
                Vec3 at = fx.target();
                for (int i = 0; i < 16; i++) level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, rnd(0.4), rnd(0.4), rnd(0.4));
            }
            case NOVA -> {
                for (int i = 0; i < 60; i++) {
                    double a = i / 60.0 * Math.PI * 2;
                    double sp = 0.25 + RANDOM.nextDouble() * 0.15;
                    level.addParticle(i % 3 == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME, o.x + Math.cos(a) * 0.6, o.y + 0.3, o.z + Math.sin(a) * 0.6,
                            Math.cos(a) * sp, 0.02, Math.sin(a) * sp);
                }
                for (int i = 0; i < 8; i++) level.addParticle(ParticleTypes.LAVA, o.x, o.y + 0.5, o.z, 0, 0, 0);
            }
            case HEAL -> {
                for (int i = 0; i < 18; i++) {
                    level.addParticle(i % 3 == 0 ? ParticleTypes.HAPPY_VILLAGER : dust, o.x + rnd(0.5), o.y + RANDOM.nextDouble() * 2, o.z + rnd(0.5), 0, 0.08, 0);
                }
                for (int i = 0; i < 6; i++) level.addParticle(ParticleTypes.END_ROD, o.x + rnd(0.4), o.y + 0.2, o.z + rnd(0.4), 0, 0.12, 0);
            }
            case SHOT -> {
                Vec3 from = e != null ? e.getEyePosition() : o;
                Vec3 to = fx.target();
                Vec3 d = to.subtract(from);
                int n = (int) Math.min(40, d.length() * 1.5);
                for (int i = 0; i < n; i++) {
                    Vec3 at = from.add(d.scale(i / (double) n));
                    level.addParticle(ParticleTypes.CRIT, at.x, at.y, at.z, rnd(0.05), rnd(0.05), rnd(0.05));
                }
            }
            case VEIL -> {
                for (int i = 0; i < 40; i++) level.addParticle(i % 2 == 0 ? ParticleTypes.SQUID_INK : ParticleTypes.LARGE_SMOKE,
                        o.x + rnd(0.5), o.y + RANDOM.nextDouble() * 1.9, o.z + rnd(0.5), rnd(0.08), 0.02, rnd(0.08));
            }
            case MARK -> {
                if (e != null) for (int i = 0; i < 12; i++) level.addParticle(dust, e.getX() + rnd(0.4), e.getY() + e.getBbHeight() + 0.5, e.getZ() + rnd(0.4), 0, 0.05, 0);
            }
            default -> {}
        }
    }

    /** Lasting effects drop a few particles every tick. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        float time = now(0);
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        for (Fx fx : ACTIVE) {
            float age = (time - fx.start) / 20f;
            if (age > fx.life()) continue;
            Entity e = mc.level.getEntity(fx.p.entityId());
            DustParticleOptions dust = new DustParticleOptions(rgb(fx.p.color(), 0.25f), 0.9f);
            switch (fx.p.kind()) {
                case AURA -> {
                    if (e == null || (e == mc.player && firstPerson)) break;
                    if (RANDOM.nextFloat() < 0.6f) mc.level.addParticle(dust, e.getX() + rnd(0.45), e.getY() + RANDOM.nextDouble() * e.getBbHeight(), e.getZ() + rnd(0.45), 0, 0.04, 0);
                }
                case VEIL -> {
                    if (e == null || RANDOM.nextFloat() > 0.35f) break;
                    mc.level.addParticle(ParticleTypes.SMOKE, e.getX() + rnd(0.35), e.getY() + 0.1, e.getZ() + rnd(0.35), 0, 0.01, 0);
                }
                case ZONE -> {
                    double r = fx.p.size();
                    for (int i = 0; i < 2; i++) {
                        double a = RANDOM.nextDouble() * Math.PI * 2;
                        double d = Math.sqrt(RANDOM.nextDouble()) * r;
                        mc.level.addParticle(RANDOM.nextFloat() < 0.5f ? ParticleTypes.END_ROD : dust,
                                fx.p.x() + Math.cos(a) * d, fx.p.y() + 0.1, fx.p.z() + Math.sin(a) * d, 0, 0.05 + RANDOM.nextDouble() * 0.05, 0);
                    }
                }
                case DOME -> {
                    Vec3 c = domeCenter(fx, e, 0);
                    double r = fx.p.size();
                    if (RANDOM.nextFloat() < 0.5f) {
                        double a = RANDOM.nextDouble() * Math.PI * 2;
                        double b = RANDOM.nextDouble() * Math.PI / 2;
                        mc.level.addParticle(dust, c.x + Math.cos(a) * Math.cos(b) * r, c.y + Math.sin(b) * r, c.z + Math.sin(a) * Math.cos(b) * r, 0, 0, 0);
                    }
                }
                case METEOR -> {
                    float k = Mth.clamp(age / (fx.p.duration() / 20f), 0, 1);
                    if (k < 1) {
                        Vec3 at = meteorAt(fx, k);
                        mc.level.addParticle(ParticleTypes.FLAME, at.x + rnd(0.2), at.y + rnd(0.2), at.z + rnd(0.2), 0, 0.05, 0);
                        mc.level.addParticle(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.3, at.z, 0, 0.02, 0);
                    }
                }
                case MARK -> {
                    if (e != null && RANDOM.nextFloat() < 0.25f) mc.level.addParticle(dust, e.getX() + rnd(0.3), e.getY() + e.getBbHeight() + 0.4, e.getZ() + rnd(0.3), 0, -0.02, 0);
                }
                default -> {}
            }
        }
    }

    private static Vec3 domeCenter(Fx fx, Entity e, float partial) {
        // a small dome follows its caster (Bulwark), a big one stays where it was raised (Aegis Dome)
        if (fx.p.size() < 3 && e != null) return e.getPosition(partial);
        return fx.target();
    }

    private static Vec3 meteorAt(Fx fx, float k) {
        Vec3 land = fx.target();
        Vec3 from = land.add(4, 22, 2.5);
        return from.lerp(land, k * k);
    }

    /** Draws the light part of every effect. Called once per frame after particles. */
    public static void render(PoseStack pose, Camera camera, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ACTIVE.isEmpty()) return;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 cam = camera.getPosition();
        float time = now(partial);
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();

        Iterator<Fx> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Fx fx = it.next();
            float age = (time - fx.start) / 20f;
            float life = fx.life();
            if (age >= life + 0.05f || age < -1) {
                it.remove();
                continue;
            }
            SkillFxPayload p = fx.p;
            Entity e = mc.level.getEntity(p.entityId());
            boolean self = e == mc.player && firstPerson;
            Vec3 at = e != null ? e.getPosition(partial) : fx.origin;
            float t = Mth.clamp(age / life, 0, 1);
            float out = 1 - (1 - t) * (1 - t) * (1 - t);
            float fade = 1 - t;
            // lasting effects fade in and out at the ends instead of over their whole life
            float hold = Mth.clamp(age / 0.3f, 0, 1) * Mth.clamp((life - age) / 0.5f, 0, 1);
            int c = p.color();
            int light = mix(c, 0xFFFFFF, 0.5f);

            pose.pushPose();
            pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            switch (p.kind()) {
                case CAST -> {
                    float k = Mth.clamp(age / 0.25f, 0, 1);
                    disc(pose, buffers, CIRCLE, 0.04f, p.size() * (0.5f + 0.5f * k), time * 4, c, fade * 1.1f);
                    disc(pose, buffers, RING, 0.05f, p.size() * (0.6f + 0.9f * out), 0, light, fade * 0.8f);
                }
                case SLASH -> {
                    if (self) break;
                    float sweep = Mth.lerp(Mth.clamp(age / 0.16f, 0, 1), -55, 50);
                    slash(pose, buffers, fx.yaw, sweep, 1.1f, p.size(), c, fade * 1.2f);
                    slash(pose, buffers, fx.yaw, sweep - 8, 1.1f, p.size() * 0.92f, 0xFFFFFF, fade * 0.7f);
                }
                case DASH -> {
                    Vec3 start = fx.target().subtract(at);
                    for (int i = 0; i < 7; i++) {
                        float k = i / 7f;
                        Vec3 g = start.scale(1 - k);
                        dot(pose, buffers, camera, (float) g.x, (float) g.y + 1f, (float) g.z, 0.55f + 0.25f * k, c, fade * (0.3f + 0.5f * k));
                    }
                    pose.pushPose();
                    pose.translate(start.x, start.y, start.z);
                    disc(pose, buffers, RING, 0.06f, 0.6f + 1.6f * out, 0, light, fade);
                    pose.popPose();
                }
                case PULSE -> {
                    disc(pose, buffers, RING, 0.1f, p.size() * out, 0, light, fade * 1.2f);
                    float k2 = Mth.clamp((age - 0.25f) / (life - 0.25f), 0, 1);
                    if (k2 > 0) disc(pose, buffers, RING, 0.1f, p.size() * 0.7f * (1 - (1 - k2) * (1 - k2)), 0, c, (1 - k2) * 0.9f);
                    disc(pose, buffers, GLOW, 0.05f, 2.5f * (1 - t), 0, c, fade * 0.6f);
                }
                case ROAR -> {
                    for (int i = 0; i < 3; i++) {
                        float k = Mth.clamp((age - i * 0.12f) / 0.7f, 0, 1);
                        if (k <= 0 || k >= 1) continue;
                        float o = 1 - (1 - k) * (1 - k);
                        disc(pose, buffers, RING, 1.2f + i * 0.15f, 0.6f + p.size() * 0.75f * o, 0, i == 1 ? light : c, (1 - k) * 1.1f);
                    }
                    disc(pose, buffers, RING, 0.06f, p.size() * out, 0, c, fade * 0.8f);
                    if (!self) dot(pose, buffers, camera, 0, 1.3f, 0, 1.4f * (1 - t * 0.5f), c, fade * 0.6f);
                }
                case SLAM -> {
                    Vec3 g = fx.target().subtract(at);
                    pose.translate(g.x, g.y, g.z);
                    disc(pose, buffers, SIGIL, 0.05f, p.size() * (0.4f + 0.6f * out), time * 2, c, fade * 1.3f);
                    disc(pose, buffers, RING, 0.08f, p.size() * 1.3f * out, 0, light, fade * 1.2f);
                    disc(pose, buffers, GLOW, 0.04f, p.size() * 0.8f, 0, c, fade * 0.7f);
                    dotTex(pose, buffers, camera, BURST, 0, 0.5f, 0, 1.2f + 2f * out, light, fade, fx.seed % 360);
                }
                case BLINK -> {
                    Vec3 from = fx.target().subtract(at);
                    dot(pose, buffers, camera, (float) from.x, (float) from.y + 1f, (float) from.z, 1.2f * (1 - out * 0.6f), c, fade);
                    pose.pushPose();
                    pose.translate(from.x, from.y, from.z);
                    disc(pose, buffers, RING, 0.05f, 0.4f + 1.4f * out, 0, c, fade);
                    pose.popPose();
                    if (!self) dot(pose, buffers, camera, 0, 1f, 0, 1.3f * (1 - out), light, fade);
                    disc(pose, buffers, RING, 0.05f, 0.4f + 1.4f * out, 0, light, fade);
                    streak(pose, buffers, camera, at, from.add(0, 1, 0), new Vec3(0, 1, 0), 0.25f * fade, c, fade * 0.7f);
                }
                case AURA -> {
                    if (self) {
                        disc(pose, buffers, RING, 0.05f, 0.9f + 0.08f * Mth.sin(time * 0.3f), time * 3, c, hold * 0.6f);
                        break;
                    }
                    disc(pose, buffers, RING, 0.05f, 0.9f + 0.08f * Mth.sin(time * 0.3f), time * 3, c, hold * 0.8f);
                    disc(pose, buffers, GLOW, 0.04f, 1.2f, 0, c, hold * 0.4f);
                    dot(pose, buffers, camera, 0, 1f, 0, 1.0f, c, hold * (0.18f + 0.08f * Mth.sin(time * 0.25f)));
                }
                case BOLT -> {
                    Vec3 from = (e != null ? e.getEyePosition(partial).add(e.getViewVector(partial).scale(0.6)).add(0, -0.35, 0) : fx.origin).subtract(at);
                    Vec3 to = fx.target().subtract(at);
                    float flick = 0.75f + 0.25f * Mth.sin(time * 3.1f);
                    zigzag(pose, buffers, camera, at, from, to, fx.seed + (long) (time / 2), 0.32f * flick, c, fade * 1.3f);
                    zigzag(pose, buffers, camera, at, from, to, fx.seed + (long) (time / 2), 0.12f, 0xFFFFFF, fade);
                    dotTex(pose, buffers, camera, BURST, (float) to.x, (float) to.y, (float) to.z, 0.6f + 0.9f * out, light, fade, fx.seed % 360);
                    dot(pose, buffers, camera, (float) from.x, (float) from.y, (float) from.z, 0.35f, light, fade);
                }
                case NOVA -> {
                    disc(pose, buffers, RING, 0.15f, p.size() * out, 0, c, fade * 1.3f);
                    disc(pose, buffers, RING, 0.6f, p.size() * 0.85f * out, 0, mix(c, 0xFFE0A0, 0.4f), fade * 0.9f);
                    disc(pose, buffers, GLOW, 0.1f, p.size() * 0.9f * out, 0, c, fade * 0.6f);
                    dot(pose, buffers, camera, 0, 0.9f, 0, 1.6f * (1 - t), mix(c, 0xFFFFFF, 0.3f), fade * 0.8f);
                }
                case METEOR -> {
                    Vec3 land = fx.target().subtract(at);
                    float fall = p.duration() / 20f;
                    pose.pushPose();
                    pose.translate(land.x, land.y, land.z);
                    if (age < fall) {
                        float k = age / fall;
                        disc(pose, buffers, RING, 0.06f, p.size() * (1.2f - 0.6f * k), -time * 4, c, 0.5f + 0.5f * k);
                        disc(pose, buffers, GLOW, 0.05f, p.size() * 0.8f, 0, c, 0.3f + 0.4f * k);
                    } else {
                        float k = Mth.clamp((age - fall) / 0.6f, 0, 1);
                        float o = 1 - (1 - k) * (1 - k);
                        disc(pose, buffers, RING, 0.08f, p.size() * (0.5f + 1.3f * o), 0, light, 1 - k);
                        dotTex(pose, buffers, camera, BURST, 0, 0.6f, 0, 1f + 2f * o, mix(c, 0xFFFFFF, 0.3f), (1 - k) * 1.2f, fx.seed % 360);
                    }
                    pose.popPose();
                    if (age < fall) {
                        float k = age / fall;
                        Vec3 rock = meteorAt(fx, k).subtract(at);
                        Vec3 tail = meteorAt(fx, Math.max(0, k - 0.18f)).subtract(at);
                        streak(pose, buffers, camera, at, tail, rock, 0.7f, c, 0.9f);
                        dot(pose, buffers, camera, (float) rock.x, (float) rock.y, (float) rock.z, 0.9f, mix(c, 0xFFFFFF, 0.4f), 1.2f);
                        dot(pose, buffers, camera, (float) rock.x, (float) rock.y, (float) rock.z, 1.8f, c, 0.6f);
                    }
                }
                case DOME -> {
                    Vec3 cc = domeCenter(fx, e, partial).subtract(at);
                    pose.translate(cc.x, cc.y, cc.z);
                    float r = p.size() * (0.7f + 0.3f * Mth.clamp(age / 0.3f, 0, 1));
                    boolean big = p.size() >= 3;
                    float a = hold * (self && !big ? 0.45f : 1f);
                    for (int i = 0; i < 3; i++) {
                        pose.pushPose();
                        pose.translate(0, big ? 0 : 0.9f, 0);
                        pose.mulPose(Axis.YP.rotationDegrees(time * 2 + i * 60));
                        pose.mulPose(Axis.ZP.rotationDegrees(90));
                        disc(pose, buffers, RING, 0, r * 1.05f, 0, i == 0 ? light : c, a * 0.55f);
                        pose.popPose();
                    }
                    disc(pose, buffers, RING, 0.06f, r, -time * 2, light, a * 0.9f);
                    if (big) disc(pose, buffers, CIRCLE, 0.04f, r * 0.95f, time * 1.5f, c, a * 0.7f);
                    if (!self || big) dot(pose, buffers, camera, 0, big ? r * 0.35f : 0.9f, 0, r * 1.15f, c, a * 0.22f);
                }
                case HEAL -> {
                    if (!self) streak(pose, buffers, camera, at, new Vec3(0, -0.1, 0), new Vec3(0, 3.2, 0), 0.9f * (1 - t * 0.5f), c, fade * 0.9f);
                    float rise = (float) Math.min(1, age / 0.9f);
                    disc(pose, buffers, RING, 0.05f + 1.9f * rise, 0.8f - 0.3f * rise, 0, light, fade);
                    disc(pose, buffers, CIRCLE, 0.04f, 0.9f + 0.3f * out, time * 3, c, fade * 0.9f);
                }
                case ZONE -> {
                    Vec3 cc = fx.target().subtract(at);
                    pose.translate(cc.x, cc.y, cc.z);
                    float grow = 1 - (float) Math.pow(1 - Mth.clamp(age / 0.5f, 0, 1), 3);
                    disc(pose, buffers, CIRCLE, 0.05f, p.size() * grow, time * 1.2f, c, hold * 0.95f);
                    disc(pose, buffers, RING, 0.07f, p.size() * grow * (1.02f + 0.03f * Mth.sin(time * 0.3f)), 0, light, hold * 0.7f);
                    disc(pose, buffers, GLOW, 0.03f, p.size() * 1.1f * grow, 0, c, hold * 0.35f);
                }
                case MARK -> {
                    if (e == null) break;
                    float h = e.getBbHeight() + 0.75f;
                    float pulse = 0.8f + 0.2f * Mth.sin(time * 0.35f);
                    pose.pushPose();
                    pose.translate(0, h, 0);
                    pose.mulPose(camera.rotation());
                    pose.mulPose(Axis.ZP.rotationDegrees(time * 4));
                    sprite(buffers.getBuffer(ModRenderTypes.lightTexture(SIGIL)), pose.last().pose(), 0.45f * pulse, c, hold);
                    pose.popPose();
                    disc(pose, buffers, RING, 0.05f, Math.max(0.6f, e.getBbWidth()) * (1.1f + 0.1f * pulse), -time * 3, c, hold * 0.8f);
                }
                case SHOT -> {
                    Vec3 from = (e != null ? e.getEyePosition(partial).add(0, -0.2, 0) : fx.origin).subtract(at);
                    Vec3 to = fx.target().subtract(at);
                    float k = Mth.clamp(age / 0.08f, 0, 1);
                    Vec3 head = from.lerp(to, k);
                    streak(pose, buffers, camera, at, from, head, 0.28f * fade, c, fade * 1.2f);
                    streak(pose, buffers, camera, at, from, head, 0.1f, 0xFFFFFF, fade);
                    dot(pose, buffers, camera, (float) head.x, (float) head.y, (float) head.z, 0.45f, light, fade);
                    if (!self) {
                        pose.pushPose();
                        pose.translate(from.x, from.y, from.z);
                        Vec3 d = to.subtract(from).normalize();
                        pose.mulPose(Axis.YP.rotation((float) Math.atan2(d.x, d.z)));
                        pose.mulPose(Axis.XP.rotation((float) -Math.asin(d.y)));
                        sprite(buffers.getBuffer(ModRenderTypes.lightTexture(RING)), pose.last().pose(), 0.3f + 0.9f * out, light, fade);
                        pose.popPose();
                    }
                }
                case VEIL -> {
                    disc(pose, buffers, SIGIL, 0.04f, 1.1f, -time * 2, c, hold * 0.5f);
                    if (age < 0.6f) disc(pose, buffers, RING, 0.06f, 0.4f + 2.2f * (age / 0.6f), 0, c, 1 - age / 0.6f);
                }
            }
            pose.popPose();
        }
        for (ResourceLocation tex : ALL) buffers.endBatch(ModRenderTypes.lightTexture(tex));
    }

    // ---- drawing helpers (each fetches its buffer right before drawing; see the gates' "Not building!" note) ----

    private static int mix(int a, int b, float t) {
        int r = Math.round(Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
        int g = Math.round(Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
        int bl = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
        return (r << 16) | (g << 8) | bl;
    }

    private static void disc(PoseStack pose, MultiBufferSource buffers, ResourceLocation tex, float y, float radius, float degrees, int rgb, float alpha) {
        if (alpha <= 0.01f || radius <= 0.01f) return;
        pose.pushPose();
        pose.translate(0, y, 0);
        pose.mulPose(Axis.YP.rotationDegrees(degrees));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        sprite(buffers.getBuffer(ModRenderTypes.lightTexture(tex)), pose.last().pose(), radius, rgb, Math.min(1f, alpha));
        pose.popPose();
    }

    private static void dot(PoseStack pose, MultiBufferSource buffers, Camera camera, float x, float y, float z, float half, int rgb, float alpha) {
        dotTex(pose, buffers, camera, GLOW, x, y, z, half, rgb, alpha, 0);
    }

    private static void dotTex(PoseStack pose, MultiBufferSource buffers, Camera camera, ResourceLocation tex,
                               float x, float y, float z, float half, int rgb, float alpha, float roll) {
        if (alpha <= 0.01f) return;
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(camera.rotation());
        if (roll != 0) pose.mulPose(Axis.ZP.rotationDegrees(roll));
        sprite(buffers.getBuffer(ModRenderTypes.lightTexture(tex)), pose.last().pose(), half, rgb, Math.min(1f, alpha));
        pose.popPose();
    }

    /** The crescent, flat in front of the caster, swept around them and slightly tilted. */
    private static void slash(PoseStack pose, MultiBufferSource buffers, float yaw, float sweep, float height, float radius, int rgb, float alpha) {
        if (alpha <= 0.01f) return;
        pose.pushPose();
        pose.translate(0, height, 0);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(Axis.ZP.rotationDegrees(-18));
        pose.mulPose(Axis.YP.rotationDegrees(sweep));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        sprite(buffers.getBuffer(ModRenderTypes.lightTexture(SLASH)), pose.last().pose(), radius, rgb, Math.min(1f, alpha));
        pose.popPose();
    }

    /**
     * A streak of light from a to b (relative to the current pose origin, which sits at {@code origin} in the
     * world), turned to face the camera.
     */
    private static void streak(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, Vec3 a, Vec3 b, float width, int rgb, float alpha) {
        if (alpha <= 0.01f || width <= 0.005f) return;
        Vec3 dir = b.subtract(a);
        if (dir.lengthSqr() < 1.0e-4) return;
        Vec3 mid = origin.add(a.add(b).scale(0.5));
        Vec3 view = camera.getPosition().subtract(mid);
        Vec3 side = dir.cross(view);
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

    /** A jagged bolt: the line broken into segments that jump sideways, re-rolled every couple of ticks. */
    private static void zigzag(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, Vec3 a, Vec3 b, long seed, float width, int rgb, float alpha) {
        RandomSource r = RandomSource.create(seed);
        int n = Math.max(3, (int) (b.subtract(a).length() / 1.6));
        Vec3 prev = a;
        for (int i = 1; i <= n; i++) {
            Vec3 next = a.lerp(b, i / (double) n);
            if (i < n) next = next.add((r.nextDouble() - 0.5) * 0.6, (r.nextDouble() - 0.5) * 0.6, (r.nextDouble() - 0.5) * 0.6);
            streak(pose, buffers, camera, origin, prev, next, width, rgb, alpha);
            prev = next;
        }
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
        ACTIVE.clear();
    }

    private SkillWorldFx() {}
}
