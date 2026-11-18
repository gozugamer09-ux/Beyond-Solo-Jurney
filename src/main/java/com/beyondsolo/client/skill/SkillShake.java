package com.beyondsolo.client.skill;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.ClientConfig;
import com.beyondsolo.client.gfx.Anim;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * The camera's answer to a heavy skill: a slam, a quake, a meteor or a bolt from the sky landing near you jolts the
 * view, harder the closer it is. Shakes add up to a ceiling and die away quickly; a slam jolts mostly up and down, a
 * sweep side to side. Off with the screen effects option. Runs on real time, so it also plays out while the game lags.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class SkillShake {
    /** Up and down (slams, landings). */
    public static final int SLAM = 1;
    /** Side to side (sweeps, charges). */
    public static final int SWEEP = 2;
    /** Every way (blasts, thunder). */
    public static final int BLAST = 0;

    private static float trauma;
    private static int style;
    private static double last = -1;
    private static float time;

    /** A jolt of {@code strength} (about 0.2 a thump, 1 the ground breaking) from a blow at {@code at}, felt to {@code reach} blocks. */
    public static void hit(Vec3 at, float strength, float reach, int kind) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float near = Mth.clamp(1 - (float) mc.player.position().distanceTo(at) / reach, 0, 1);
        float s = strength * near;
        if (s <= 0.03f) return;
        if (s > trauma * 0.6f) style = kind;
        trauma = Math.min(1f, trauma + s);
    }

    @SubscribeEvent
    static void camera(ViewportEvent.ComputeCameraAngles event) {
        double now = Anim.now();
        float dt = last < 0 ? 0 : (float) Math.min(0.1, now - last);
        last = now;
        trauma = Math.max(0, trauma - dt * 1.6f);
        time += dt;
        if (trauma <= 0.001f || !ClientConfig.SCREEN_EFFECTS.get()) return;
        float s = trauma * trauma;
        float t = time * 34f;
        float a = Mth.sin(t + 0.3f) * 0.6f + Mth.sin(t * 1.9f + 2.1f) * 0.4f;
        float b = Mth.sin(t * 1.3f + 4.2f) * 0.6f + Mth.sin(t * 2.3f + 0.7f) * 0.4f;
        float yaw = 2.2f, pitch = 2.2f, roll = 1.8f;
        if (style == SLAM) {
            yaw = 0.8f;
            pitch = 4f;
            roll = 1.2f;
        } else if (style == SWEEP) {
            yaw = 3.6f;
            pitch = 0.8f;
            roll = 2.6f;
        }
        event.setYaw(event.getYaw() + a * yaw * s);
        event.setPitch(event.getPitch() + b * pitch * s);
        event.setRoll(event.getRoll() + (a - b) * 0.5f * roll * s);
    }

    public static void clear() {
        trauma = 0;
    }

    private SkillShake() {}
}
