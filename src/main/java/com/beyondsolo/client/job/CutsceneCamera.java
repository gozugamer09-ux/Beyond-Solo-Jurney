package com.beyondsolo.client.job;

import com.beyondsolo.network.CutscenePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Takes the camera away from the player for story beats. The server sends shots ({@link CutscenePayload}); each
 * glides (or orbits) with easing, and the next shot starts from wherever the camera is, so cuts are smooth.
 * Letterbox bars slide in while it runs (drawn by {@link JobRevealOverlay#renderLetterbox}).
 *
 * <p>The camera is a client-only marker entity that is moved every frame and made the camera entity, so the player's
 * own model shows in the shot. Any system can use it: send {@code CutscenePayload.glide/orbit} shots and a
 * {@code stop()} at the end.</p>
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
    /** Game time the current shot was received, to cap how long it waits for the world to show up. */
    private static double receivedTime;
    /** 0..1, a black curtain shown while the world around the shot is still loading, faded out after. */
    private static float curtain;
    private static double curtainTime = -1;
    /** Longest wait (ticks) for the world around a shot to be drawn before it plays anyway. */
    private static final int MAX_WAIT = 80;

    public static void play(CutscenePayload p) {
        if (p.ticks() <= 0) {
            stop();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        blendFrom = active ? lastPos : (mc.player != null ? mc.player.getEyePosition() : null);
        blendYaw = active ? lastYaw : (mc.player != null ? mc.player.getYRot() : 0);
        blendPitch = active ? lastPitch : (mc.player != null ? mc.player.getXRot() : 0);
        // after a long jump (another dimension, the far end of a lane) there is nothing to blend from
        if (blendFrom != null && blendFrom.distanceTo(p.from()) > 96) blendFrom = null;
        shot = p;
        startTime = now(0);
        receivedTime = startTime;
        active = true;
    }

    public static void stop() {
        shot = null;
        active = false;
        Minecraft mc = Minecraft.getInstance();
        if (eye != null && mc.getCameraEntity() == eye && mc.player != null) mc.setCameraEntity(mc.player);
        eye = null;
    }

    /** The stand-in entity the camera follows during a shot. Never added to the level. */
    private static net.minecraft.world.entity.@Nullable Entity eye;

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

    /** 0..1, the black curtain while the shot waits for the world. Eases out on its own. */
    public static float curtain() {
        double t = now(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
        if (curtainTime < 0) curtainTime = t;
        float dt = (float) Mth.clamp(t - curtainTime, 0, 5) / 20f;
        curtainTime = t;
        boolean waiting = active && shot != null && waiting(shot);
        curtain = waiting ? 1 : Mth.clamp(curtain - dt * 2.5f, 0, 1);
        return curtain;
    }

    /** True while the sections the shot starts in and looks at are not drawn yet (capped at {@link #MAX_WAIT}). */
    private static boolean waiting(CutscenePayload s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || now(0) - receivedTime > MAX_WAIT) return false;
        Vec3 from = s.orbit() != 0 ? s.lookTo() : s.from();
        return !mc.levelRenderer.isSectionCompiled(net.minecraft.core.BlockPos.containing(from))
                || !mc.levelRenderer.isSectionCompiled(net.minecraft.core.BlockPos.containing(s.lookTo()));
    }

    private static double now(float partial) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime() + partial;
    }

    /** Places the camera for this frame (call before the frame renders). Returns {yaw, pitch}, or null when no cutscene runs. */
    public static float @Nullable [] frame(float partial) {
        if (!active || shot == null) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            stop();
            return null;
        }
        CutscenePayload s = shot;
        // hold the shot at its first frame until the world there is drawn
        if (waiting(s)) startTime = now(partial);
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
        if (eye == null || eye.level() != mc.level) {
            eye = new net.minecraft.world.entity.Marker(net.minecraft.world.entity.EntityType.MARKER, mc.level);
        }
        // no interpolation: the position is already worked out for this exact frame
        eye.setPos(pos);
        eye.xo = eye.xOld = pos.x;
        eye.yo = eye.yOld = pos.y;
        eye.zo = eye.zOld = pos.z;
        eye.setYRot(yaw);
        eye.setXRot(pitch);
        eye.yRotO = yaw;
        eye.xRotO = pitch;
        if (mc.getCameraEntity() != eye) mc.setCameraEntity(eye);
        lastPos = pos;
        lastYaw = yaw;
        lastPitch = pitch;
        return new float[]{yaw, pitch};
    }

    /** The angles worked out for this frame, or null when no cutscene runs. */
    public static float @Nullable [] angles() {
        return active && shot != null && eye != null ? new float[]{lastYaw, lastPitch} : null;
    }

    private static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }

    private CutsceneCamera() {}
}
