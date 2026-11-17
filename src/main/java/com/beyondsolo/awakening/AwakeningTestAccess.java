package com.beyondsolo.awakening;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Hooks for game tests that drive the Hollow Arbiter's attacks by hand (its rings and pillars run on its own tick,
 * which a test can't time). Not for gameplay code.
 */
public final class AwakeningTestAccess {
    /** Wakes it with its rising already over, so it fights at once. */
    public static void ready(ArbiterEntity a) {
        a.finishRising();
    }

    /** A Sunder ring running out from {@code origin}, starting {@code delay} ticks from now. */
    public static void wave(ArbiterEntity a, Vec3 origin, int delay) {
        a.startWave((ServerLevel) a.level(), origin, delay);
    }

    /** A Judgment mark at {@code at} whose pillar falls {@code inTicks} from now (0: on the next attack tick). */
    public static void mark(ArbiterEntity a, Vec3 at, int inTicks) {
        a.addMark(at, a.level().getGameTime() + inTicks);
    }

    /** One tick of its rings and pillars, as its own tick runs them. */
    public static void tickAttacks(ArbiterEntity a) {
        ServerLevel level = (ServerLevel) a.level();
        a.tickWaves(level);
        a.tickMarks(level);
    }

    /** Rings and marks still waiting to strike. */
    public static int pending(ArbiterEntity a) {
        return a.pendingAttacks();
    }

    private AwakeningTestAccess() {}
}
