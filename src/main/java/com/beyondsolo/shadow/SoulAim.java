package com.beyondsolo.shadow;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Which soul the raise key reaches, worked out the same way on both sides (the server's ritual and the client's
 * "Extract shadow" prompt): the soul under the crosshair, from a little further ({@link ShadowService#AIM_REACH});
 * otherwise the nearest in reach ({@link ShadowService#REACH}). Where souls of a whole pack lie side by side, the one
 * the necromancer looks at is the one that rises.
 *
 * <p>A soul that can't be reached yet but is plainly the one meant (looked at, or only a few steps too far) is given
 * back as {@link Pick#far}: the key then says to come closer instead of calling the army.</p>
 */
public final class SoulAim {
    /** How near the crosshair (degrees) a soul has to be to count as looked at; a near one is a bigger target. */
    public static final double CONE = 12;

    /**
     * @param soul the soul the key reaches, or null
     * @param far  with no soul in reach, the one meant but too far away, or null
     */
    public record Pick<T>(@Nullable T soul, @Nullable T far) {}

    /**
     * Picks a soul for a necromancer standing at {@code feet}, eyes at {@code eye}, looking along {@code look}.
     * {@code at} gives where each soul lies.
     */
    public static <T> Pick<T> pick(Vec3 eye, Vec3 look, Vec3 feet, Iterable<T> souls, Function<T, Vec3> at) {
        T aimed = null, near = null, far = null;
        double aimedA = Double.MAX_VALUE, nearD = Double.MAX_VALUE, farD = Double.MAX_VALUE;
        Vec3 dir = look.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : look.normalize();
        for (T s : souls) {
            Vec3 pos = at.apply(s);
            double d = pos.distanceTo(feet);
            if (d > ShadowService.NOTICE) continue;
            // the soul's body and ember lie a little over the ground
            Vec3 to = pos.add(0, 0.4, 0).subtract(eye);
            double len = to.length();
            double a = len < 1.0e-3 ? 0 : Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dir.dot(to.scale(1 / len))))));
            boolean lookedAt = a <= Math.max(CONE, Math.toDegrees(Math.atan2(0.9, len)));
            if (lookedAt && d <= ShadowService.AIM_REACH && a < aimedA) {
                aimed = s;
                aimedA = a;
            }
            if (d <= ShadowService.REACH && d < nearD) {
                near = s;
                nearD = d;
            }
            if ((lookedAt || d <= ShadowService.REACH + 4) && d < farD) {
                far = s;
                farD = d;
            }
        }
        T soul = aimed != null ? aimed : near;
        return new Pick<>(soul, soul == null ? far : null);
    }

    private SoulAim() {}
}
