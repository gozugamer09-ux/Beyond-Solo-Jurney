package com.beyondsolo.client.job;

import com.beyondsolo.network.CutscenePayload;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Takes the camera away from the player for story beats. The server sends shots ({@link CutscenePayload}); each
 * glides (or orbits) with easing, and the next shot starts from wherever the camera is, so cuts are smooth.
 * Letterbox bars slide in while it runs (drawn by {@link JobRevealOverlay#renderLetterbox}).
 *
 * <p>Any system can use it: send {@code CutscenePayload.glide/orbit} shots and a {@code stop()} at the end.</p>
 */
public final class CutsceneCamera {
    private static @Nullable CutscenePayload shot;
    /** Game time (with partial ticks) the shot started. */
    private static double startTime;
    /** Where the camera was when the shot started, to blend into it. */
    private static @Nullable Vec3 blendFrom;
    private static float blendYaw, blendPitch;
    private static Vec3 lastPos = Vec3.ZERO;
    private static float lastYaw, lastPitch;
    private static boolean active;
    /** 0..1, how far the letterbox bars are in. */
    private static float bars;
    private static double barsTime = -1;

    public static void play(CutscenePayload p) {
        if (p.ticks() <= 0) {
            stop();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        blendFrom = active ? lastPos : (mc.player != null ? mc.player.getEyePosition() : null);
        blendYaw = active ? lastYaw : (mc.player != null ? mc.player.getYRot() : 0);
        blendPitch = active ? lastPitch : (mc.player != null ? mc.player.getXRot() : 0);
        shot = p;
        startTime = now(0);
        active = true;
    }

    public static void stop() {
        shot = null;
        active = false;
    }

    public static boolean active() {
        return active;
    }

    /** 0..1, for the letterbox. Eases in and out on its own. */
    public static float bars() {
        double t = now(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
        if (barsTime < 0) barsTime = t;
        float dt = (float) Mth.clamp(t - barsTime, 0, 5) / 20f;
        barsTime = t;
        bars = Mth.clamp(bars + (active && shot != null && shot.letterbox() ? dt * 2.5f : -dt * 2.5f), 0, 1);
        return bars;
    }

    private static double now(float partial) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime() + partial;
    }

    /** Places the camera for this frame. Returns {yaw, pitch}, or null when no cutscene runs. */
    public static float @Nullable [] apply(Camera camera, float partial) {
        if (!active || shot == null) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            stop();
            return null;
        }
        CutscenePayload s = shot;
        float t = (float) Mth.clamp((now(partial) - startTime) / s.ticks(), 0, 1);
        float e = smooth(t);
        Vec3 pos;
        Vec3 look;
        if (s.orbit() != 0) {
            double radius = Math.sqrt(s.from().x * s.from().x + s.from().z * s.from().z);
            double a = Math.atan2(s.from().z, s.from().x) + s.orbit() * Math.PI * 2 * t;
            pos = s.lookTo().add(Math.cos(a) * radius, s.from().y, Math.sin(a) * radius);
            look = s.lookTo();
        } else {
            pos = s.from().lerp(s.to(), e);
            look = s.lookFrom().lerp(s.lookTo(), e);
        }
        Vec3 d = look.subtract(pos);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        // blend in from where the camera was over the first 12 ticks of a shot
        float blend = blendFrom == null ? 1 : smooth((float) Mth.clamp((now(partial) - startTime) / 12.0, 0, 1));
        if (blend < 1 && blendFrom != null) {
            pos = blendFrom.lerp(pos, blend);
            yaw = blendYaw + Mth.wrapDegrees(yaw - blendYaw) * blend;
            pitch = Mth.lerp(blend, blendPitch, pitch);
        }
        camera.setPosition(pos);
        camera.detached = true;
        lastPos = pos;
        lastYaw = yaw;
        lastPitch = pitch;
        return new float[]{yaw, pitch};
    }

    private static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }

    private CutsceneCamera() {}
}
