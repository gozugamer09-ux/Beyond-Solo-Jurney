package com.beyondsolo.client.key;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.combat.CombatWorldFx;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.key.DungeonKeys;
import com.beyondsolo.network.KeyFxPayload;
import com.beyondsolo.system.PlayerRank;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A key's moments in the world. While a key turns, a lock of light forms in the air where its gate will open: a
 * tumbler ring around a lock face with a keyhole at its heart, a thread of light from the key to the keyhole, motes
 * drawn in. The keyhole turns with the key in two notches and a last twist; the lock cracks at each notch, and when
 * the key has turned it bursts (the gate tears open behind it, drawn by the gate itself). Let go early and it fades.
 * A key coming back to its holder glints and rises out of them. Light is drawn with the mod's light render type;
 * vanilla particles do sparks and shards. Runs on game time.
 */
public final class KeyWorldFx {
    static final ResourceLocation GLOW = CombatWorldFx.GLOW;
    static final ResourceLocation RING = CombatWorldFx.RING;
    static final ResourceLocation BURST = CombatWorldFx.BURST;
    static final ResourceLocation STREAK = BeyondSolo.id("textures/effect/streak.png");
    static final ResourceLocation LOCK = BeyondSolo.id("textures/effect/key_lock.png");
    static final ResourceLocation HOLE = BeyondSolo.id("textures/effect/key_hole.png");
    static final ResourceLocation TUMBLER = BeyondSolo.id("textures/effect/key_ring.png");
    static final ResourceLocation[] CRACKS = {
            BeyondSolo.id("textures/effect/key_crack_1.png"),
            BeyondSolo.id("textures/effect/key_crack_2.png"),
            BeyondSolo.id("textures/effect/key_crack_3.png")};
    private static final List<ResourceLocation> ALL = List.of(GLOW, RING, BURST, STREAK, LOCK, HOLE, TUMBLER, CRACKS[0], CRACKS[1], CRACKS[2]);

    /** Half the size of the lock face, in blocks. */
    private static final float LOCK_HALF = 1.05f;
    /** How long a lock takes to fade, and to burst. */
    private static final float FADE = 0.45f, BURST_TIME = 0.75f;

    private static final class Lock {
        final int entity;
        final Vec3 at;
        final float yaw;
        final int color;
        final int style;
        final float start;
        final int duration;
        /** Game time the lock gave (opened) or faded (cancelled), -1 while turning. */
        float end = -1;
        boolean opened;
        int cracks;

        Lock(int entity, Vec3 at, float yaw, int color, int style, float start, int duration) {
            this.entity = entity;
            this.at = at;
            this.yaw = yaw;
            this.color = color;
            this.style = style;
            this.start = start;
            this.duration = Math.max(1, duration);
        }

        float progress(float time) {
            float until = end >= 0 ? Math.min(time, end) : time;
            return Mth.clamp((until - start) / duration, 0, 1);
        }
    }

    /** A key coming back to its holder. */
    private record Glint(int entity, Vec3 at, int color, float start) {}

    private static final List<Lock> LOCKS = new ArrayList<>();
    private static final List<Glint> GLINTS = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private static float now(float partial) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime() + partial;
    }

    static void play(KeyFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        PlayerRank rank = PlayerRank.values()[Mth.clamp(p.rank(), 0, PlayerRank.values().length - 1)];
        int color = DungeonKeys.color(rank, p.style());
        Vec3 at = new Vec3(p.x(), p.y(), p.z());
        float time = now(0);
        switch (p.kind()) {
            case KeyFxPayload.TURN -> {
                for (Lock l : LOCKS) if (l.entity == p.entity() && l.end < 0) l.end = time;
                LOCKS.add(new Lock(p.entity(), at, p.yaw(), color, p.style(), time, p.duration()));
                if (LOCKS.size() > 24) LOCKS.remove(0);
            }
            case KeyFxPayload.CANCEL -> {
                Lock l = turning(p.entity());
                if (l == null) return;
                l.end = time;
                for (int i = 0; i < 10; i++) {
                    Vec3 o = inPlane(l, rnd(LOCK_HALF), rnd(LOCK_HALF));
                    mc.level.addParticle(dust(l.color, 0.3f, 0.8f), o.x, o.y, o.z, 0, -0.02, 0);
                }
            }
            case KeyFxPayload.OPEN -> {
                Lock l = turning(p.entity());
                if (l == null) {
                    // the turn was never seen here (came into view late): the burst alone
                    l = new Lock(p.entity(), at, p.yaw(), color, p.style(), time - DungeonKeys.USE_TICKS, DungeonKeys.USE_TICKS);
                    l.cracks = 3;
                    LOCKS.add(l);
                }
                l.end = time;
                l.opened = true;
                shatter(mc.level, l);
            }
            case KeyFxPayload.REFUND -> {
                GLINTS.add(new Glint(p.entity(), at, color, time));
                Entity e = p.entity() >= 0 ? mc.level.getEntity(p.entity()) : null;
                Vec3 c = e != null ? e.position().add(0, e.getBbHeight() * 0.55, 0) : at.add(0, 1, 0);
                if (!selfView(e)) {
                    for (int i = 0; i < 16; i++) {
                        mc.level.addParticle(dust(color, 0.4f, 0.7f), c.x + rnd(0.5), c.y + rnd(0.7), c.z + rnd(0.5), 0, 0.03, 0);
                    }
                }
                for (int i = 0; i < 6; i++) {
                    mc.level.addParticle(ParticleTypes.END_ROD, c.x + rnd(0.6), c.y + rnd(0.4), c.z + rnd(0.6), rnd(0.02), 0.04 + RANDOM.nextDouble() * 0.04, rnd(0.02));
                }
            }
            case KeyFxPayload.REFUSE -> {
                Entity e = p.entity() >= 0 ? mc.level.getEntity(p.entity()) : null;
                if (e == null || selfView(e)) return;
                Vec3 h = handAt(e, 1);
                for (int i = 0; i < 4; i++) mc.level.addParticle(ParticleTypes.SMOKE, h.x + rnd(0.1), h.y + rnd(0.1), h.z + rnd(0.1), 0, 0.02, 0);
            }
            default -> {
            }
        }
    }

    private static @Nullable Lock turning(int entity) {
        for (int i = LOCKS.size() - 1; i >= 0; i--) {
            Lock l = LOCKS.get(i);
            if (l.entity == entity && l.end < 0) return l;
        }
        return null;
    }

    // ================================================================ particles

    private static Vector3f rgb(int color, float towardsWhite) {
        return new Vector3f(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f)
                .lerp(new Vector3f(1, 1, 1), towardsWhite);
    }

    private static DustParticleOptions dust(int color, float towardsWhite, float size) {
        return new DustParticleOptions(rgb(color, towardsWhite), size);
    }

    private static double rnd(double s) {
        return (RANDOM.nextDouble() - 0.5) * 2 * s;
    }

    /** True for the local player seen from their own eyes. */
    private static boolean selfView(@Nullable Entity e) {
        Minecraft mc = Minecraft.getInstance();
        return e != null && e == mc.player && mc.options.getCameraType().isFirstPerson();
    }

    /** A point on the lock's face: {@code side} along it, {@code up} up it. */
    private static Vec3 inPlane(Lock l, double side, double up) {
        double rad = Math.toRadians(l.yaw);
        // the face's own sideways direction (the facing turned a quarter)
        double sx = Math.cos(rad), sz = Math.sin(rad);
        return l.at.add(sx * side, up, sz * side);
    }

    /** Where a player holds a key they are turning. */
    static Vec3 handAt(Entity e, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (e == mc.player && mc.options.getCameraType().isFirstPerson()) {
            Camera cam = mc.gameRenderer.getMainCamera();
            Vector3f look = cam.getLookVector();
            Vector3f up = cam.getUpVector();
            Vector3f left = cam.getLeftVector();
            return cam.getPosition().add(look.x * 0.95, look.y * 0.95, look.z * 0.95).add(left.x * -0.3, left.y * -0.3, left.z * -0.3)
                    .add(up.x * -0.05, up.y * -0.05, up.z * -0.05);
        }
        float bodyYaw = e instanceof Player pl ? Mth.rotLerp(partial, pl.yBodyRotO, pl.yBodyRot) : e.getYRot();
        Vec3 fwd = Vec3.directionFromRotation(0, bodyYaw);
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        return e.getPosition(partial).add(0, e.getBbHeight() * 0.74, 0).add(fwd.scale(0.62)).add(right.scale(0.34));
    }

    /** The cracks spitting sparks as the key passes each notch. */
    private static void crackBurst(ClientLevel level, Lock l, int stage) {
        int n = 6 + stage * 4;
        for (int i = 0; i < n; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            double r = LOCK_HALF * (0.3 + 0.6 * RANDOM.nextDouble());
            Vec3 o = inPlane(l, Math.cos(a) * r, Math.sin(a) * r);
            Vec3 v = o.subtract(l.at).normalize().scale(0.05 + 0.05 * RANDOM.nextDouble());
            level.addParticle(stage == 3 ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK, o.x, o.y, o.z, v.x, v.y + 0.02, v.z);
        }
        for (int i = 0; i < 3 + stage * 2; i++) {
            Vec3 o = inPlane(l, rnd(LOCK_HALF * 0.8), rnd(LOCK_HALF * 0.8));
            level.addParticle(dust(l.color, 0.5f, 0.6f), o.x, o.y, o.z, 0, -0.03, 0);
        }
    }

    /** The lock gives: shards of light thrown out across its face, and a ring of sparks. */
    private static void shatter(ClientLevel level, Lock l) {
        double rad = Math.toRadians(l.yaw);
        Vec3 toward = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
        for (int i = 0; i < 26; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            double r = LOCK_HALF * RANDOM.nextDouble();
            Vec3 o = inPlane(l, Math.cos(a) * r, Math.sin(a) * r);
            Vec3 v = o.subtract(l.at).normalize().scale(0.12 + 0.12 * RANDOM.nextDouble()).add(toward.scale(0.05 * RANDOM.nextDouble()));
            level.addParticle(dust(l.color, 0.25f + 0.5f * RANDOM.nextFloat(), 0.9f + RANDOM.nextFloat() * 0.6f), o.x, o.y, o.z, v.x, v.y, v.z);
        }
        for (int i = 0; i < 18; i++) {
            double a = i / 18.0 * Math.PI * 2;
            Vec3 o = inPlane(l, Math.cos(a) * LOCK_HALF * 1.1, Math.sin(a) * LOCK_HALF * 1.1);
            Vec3 v = o.subtract(l.at).normalize().scale(0.14);
            level.addParticle(ParticleTypes.END_ROD, o.x, o.y, o.z, v.x, v.y, v.z);
        }
        level.addParticle(ParticleTypes.FLASH, l.at.x, l.at.y, l.at.z, 0, 0, 0);
    }

    // ================================================================ ticking and drawing

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float time = now(0);
        for (Lock l : LOCKS) {
            if (l.end >= 0) continue;
            float p = l.progress(time);
            int stage = p >= 0.94f ? 3 : p >= 2 / 3f ? 2 : p >= 1 / 3f ? 1 : 0;
            while (l.cracks < stage) {
                l.cracks++;
                crackBurst(mc.level, l, l.cracks);
            }
            // motes drawn towards the keyhole
            if (RANDOM.nextInt(2) == 0) {
                double a = RANDOM.nextDouble() * Math.PI * 2;
                double r = LOCK_HALF * (1.4 + RANDOM.nextDouble());
                Vec3 o = inPlane(l, Math.cos(a) * r, Math.sin(a) * r);
                Vec3 v = l.at.subtract(o).scale(0.06);
                mc.level.addParticle(dust(l.color, 0.6f, 0.5f), o.x, o.y, o.z, v.x, v.y, v.z);
            }
        }
    }

    static void render(PoseStack pose, Camera camera, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || (LOCKS.isEmpty() && GLINTS.isEmpty())) return;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 cam = camera.getPosition();
        float time = now(partial);
        boolean any = false;

        Iterator<Lock> it = LOCKS.iterator();
        while (it.hasNext()) {
            Lock l = it.next();
            float age = (time - l.start) / 20f;
            float endAge = l.end < 0 ? -1 : (time - l.end) / 20f;
            if (age > l.duration / 20f + 8 || endAge > (l.opened ? BURST_TIME : FADE) + 0.05f || age < -2) {
                it.remove();
                continue;
            }
            if (l.at.distanceToSqr(cam) > 96 * 96) continue;
            any = true;
            Entity e = mc.level.getEntity(l.entity);
            pose.pushPose();
            pose.translate(l.at.x - cam.x, l.at.y - cam.y, l.at.z - cam.z);
            lock(pose, buffers, camera, l, e, partial, time, age, endAge);
            pose.popPose();
        }

        Iterator<Glint> gi = GLINTS.iterator();
        while (gi.hasNext()) {
            Glint g = gi.next();
            float age = (time - g.start()) / 20f;
            if (age > 1.4f || age < -1) {
                gi.remove();
                continue;
            }
            any = true;
            Entity e = mc.level.getEntity(g.entity());
            if (selfView(e)) continue;
            Vec3 base = e != null ? e.getPosition(partial).add(0, e.getBbHeight() * 0.6, 0) : g.at().add(0, 1.1, 0);
            float k = age / 1.4f;
            float rise = 1 - (1 - k) * (1 - k);
            float a = Mth.clamp(age / 0.15f, 0, 1) * (1 - k);
            pose.pushPose();
            pose.translate(base.x - cam.x, base.y - cam.y + 0.9f * rise, base.z - cam.z);
            dot(pose, buffers, camera, GLOW, 0.5f + 0.4f * rise, g.color(), 0.8f * a, 0);
            dot(pose, buffers, camera, HOLE, 0.22f, mix(g.color(), 0xFFFFFF, 0.6f), a, 0);
            dot(pose, buffers, camera, RING, 0.3f + 0.9f * rise, mix(g.color(), 0xFFFFFF, 0.3f), 0.6f * (1 - k), 0);
            pose.popPose();
        }

        if (any) for (ResourceLocation tex : ALL) buffers.endBatch(ModRenderTypes.lightTexture(tex));
    }

    private static void lock(PoseStack pose, MultiBufferSource buffers, Camera camera, Lock l, @Nullable Entity e, float partial,
                             float time, float age, float endAge) {
        float p = l.progress(time);
        int c = l.color;
        int pale = mix(c, 0xFFFFFF, 0.5f);
        int hot = mix(c, 0xFFFFFF, 0.25f + 0.6f * p);
        float form = Anim.outBack(Mth.clamp(age / 0.35f, 0, 1));
        float alpha = Mth.clamp(age / 0.2f, 0, 1);
        float scale = form;
        float burst = 0;
        if (endAge >= 0) {
            if (l.opened) {
                burst = Mth.clamp(endAge / BURST_TIME, 0, 1);
                scale = 1 + 0.55f * (1 - (1 - burst) * (1 - burst));
                alpha *= 1 - Mth.clamp(endAge / 0.28f, 0, 1);
            } else {
                float f = Mth.clamp(endAge / FADE, 0, 1);
                scale *= 1 - 0.35f * f;
                alpha *= 1 - f;
            }
        }
        float turn = KeyClientEvents.turned(p);
        // a shudder right after each notch
        float since = 1;
        if (l.cracks > 0 && l.end < 0) {
            float[] at = {1 / 3f, 2 / 3f, 0.94f};
            float notch = at[Math.min(2, l.cracks - 1)];
            since = Mth.clamp((p - notch) * l.duration / 6f, 0, 1);
        }
        float shudder = (1 - since) * 0.05f;
        float sx = Mth.sin(time * 2.7f) * shudder, sy = Mth.cos(time * 3.3f) * shudder;
        float breathe = 1 + 0.03f * Mth.sin(time * 0.35f);

        // the thread of light from the key to the keyhole, while the key turns
        if (e != null && l.end < 0 && alpha > 0.01f) {
            Vec3 hand = handAt(e, partial);
            Vec3 cam = camera.getPosition();
            Vec3 origin = l.at;
            Vec3 a = hand.subtract(origin);
            float w = 0.05f + 0.05f * p;
            streak(pose, buffers, camera, origin, a, Vec3.ZERO, w * 2.4f, c, (0.15f + 0.25f * p) * alpha);
            streak(pose, buffers, camera, origin, a, Vec3.ZERO, w, pale, (0.35f + 0.45f * p) * alpha);
            // a bead of light running along it
            float run = (time * 0.09f) % 1f;
            Vec3 bead = a.scale(1 - run);
            dotAt(pose, buffers, camera, GLOW, bead, 0.12f + 0.05f * p, pale, 0.8f * alpha * Mth.sin(run * Mth.PI));
            if (cam.distanceToSqr(hand) > 1.5) dotAt(pose, buffers, camera, GLOW, a, 0.18f, pale, 0.5f * alpha);
        }

        pose.pushPose();
        pose.translate(sx, sy, 0);
        pose.mulPose(Axis.YP.rotationDegrees(-l.yaw));
        float s = scale * breathe;
        // a soft glow behind it all
        face(pose, buffers, GLOW, 0, LOCK_HALF * 2.3f * s, 0, c, (0.3f + 0.3f * p) * alpha + 0.5f * (1 - burst) * (burst > 0 ? 1 : 0));
        // the tumbler ring, turning with the key
        face(pose, buffers, TUMBLER, 0.01f, LOCK_HALF * 1.3f * s, -90 * turn + 6 * Mth.sin(time * 0.05f), pale, 0.8f * alpha);
        // the lock's face, and its cracks
        face(pose, buffers, LOCK, 0.02f, LOCK_HALF * s, 0, c, 0.95f * alpha);
        for (int i = 0; i < Math.min(3, l.cracks); i++) {
            face(pose, buffers, CRACKS[i], 0.03f, LOCK_HALF * s, 0, hot, Math.min(1f, 0.6f + 0.2f * i + 0.4f * p) * alpha);
        }
        // the keyhole, white-hot by the end, turning with the key
        face(pose, buffers, HOLE, 0.04f, LOCK_HALF * 0.52f * s, -90 * turn, hot, (0.55f + 0.45f * p) * alpha);
        face(pose, buffers, GLOW, 0.05f, LOCK_HALF * (0.35f + 0.35f * p) * s, 0, 0xFFFFFF, (0.25f + 0.5f * p * p) * alpha);
        pose.popPose();

        if (l.opened && endAge >= 0) {
            float k = Mth.clamp(endAge / BURST_TIME, 0, 1);
            float out = 1 - (1 - k) * (1 - k) * (1 - k);
            float flash = Mth.clamp(1 - endAge / 0.3f, 0, 1);
            dot(pose, buffers, camera, GLOW, 1.2f + 2.6f * out, 0xFFFFFF, flash, 0);
            dot(pose, buffers, camera, GLOW, 2.5f + 2.5f * out, c, 0.8f * (1 - k), 0);
            dot(pose, buffers, camera, BURST, 1.2f + 2.8f * out, pale, 0.9f * (1 - k), l.start % 360);
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(-l.yaw));
            face(pose, buffers, RING, 0, LOCK_HALF * (1 + 3.2f * out), 0, pale, 0.9f * (1 - k));
            face(pose, buffers, RING, 0, LOCK_HALF * (0.6f + 2.0f * out), 0, c, 0.7f * (1 - k));
            pose.popPose();
        }
    }

    // ================================================================ drawing helpers

    private static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0, 1);
        int r = Math.round(Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
        int g = Math.round(Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
        int bl = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
        return (r << 16) | (g << 8) | bl;
    }

    /** A texture standing in the lock's plane (the pose already turned to it), {@code z} towards the player. */
    private static void face(PoseStack pose, MultiBufferSource buffers, ResourceLocation tex, float z, float half, float roll, int rgb, float alpha) {
        if (alpha <= 0.01f || half <= 0.005f) return;
        pose.pushPose();
        pose.translate(0, 0, z);
        if (roll != 0) pose.mulPose(Axis.ZP.rotationDegrees(roll));
        sprite(buffers.getBuffer(ModRenderTypes.lightTexture(tex)), pose.last().pose(), half, rgb, Math.min(1f, alpha));
        pose.popPose();
    }

    private static void dot(PoseStack pose, MultiBufferSource buffers, Camera camera, ResourceLocation tex, float half, int rgb, float alpha, float roll) {
        dotAt(pose, buffers, camera, tex, Vec3.ZERO, half, rgb, alpha, roll);
    }

    private static void dotAt(PoseStack pose, MultiBufferSource buffers, Camera camera, ResourceLocation tex, Vec3 at, float half, int rgb, float alpha) {
        dotAt(pose, buffers, camera, tex, at, half, rgb, alpha, 0);
    }

    private static void dotAt(PoseStack pose, MultiBufferSource buffers, Camera camera, ResourceLocation tex, Vec3 at, float half, int rgb, float alpha, float roll) {
        if (alpha <= 0.01f || half <= 0.005f) return;
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(camera.rotation());
        if (roll != 0) pose.mulPose(Axis.ZP.rotationDegrees(roll));
        sprite(buffers.getBuffer(ModRenderTypes.lightTexture(tex)), pose.last().pose(), half, rgb, Math.min(1f, alpha));
        pose.popPose();
    }

    /** A streak of light from a to b (relative to the pose origin, which sits at {@code origin}), turned to the camera. */
    private static void streak(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, Vec3 a, Vec3 b, float width, int rgb, float alpha) {
        if (alpha <= 0.01f || width <= 0.005f) return;
        Vec3 dir = b.subtract(a);
        if (dir.lengthSqr() < 1.0e-4) return;
        Vec3 mid = origin.add(a.add(b).scale(0.5));
        Vec3 view = camera.getPosition().subtract(mid);
        Vec3 side = dir.cross(view);
        if (side.lengthSqr() < 1.0e-6) side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0e-6) return;
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
        LOCKS.clear();
        GLINTS.clear();
    }

    private KeyWorldFx() {}
}
