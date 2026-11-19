package com.beyondsolo.isle;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/** Hooks for the game tests ({@code com.beyondsolo.test.IsleGameTests}) that reach past the raid's normal flow. Not for gameplay code. */
public final class IsleTestAccess {
    /** A raid made small for a test: its nests, beach waves, allies and the blows a heart takes (null: the config's). */
    public static void shape(@Nullable Integer nests, @Nullable Integer waves, @Nullable Integer allies, @Nullable Integer blows) {
        IsleConfig.nestsOverride = nests;
        IsleConfig.wavesOverride = waves;
        IsleConfig.alliesOverride = allies;
        IsleConfig.nestHitsOverride = blows;
    }

    /** Back to the config's numbers. */
    public static void unshape() {
        shape(null, null, null, null);
    }

    /** The island planned for a seed and number of nests: the full one of its own world, or the small one of the tests. */
    public static IsleWorld.Layout plan(boolean full, long seed, int nests) {
        return IsleWorld.plan(!full, seed, nests);
    }

    /** The island a raid is on. */
    public static IsleWorld.Layout layout(MinecraftServer server, IsleRaid r) {
        ServerLevel isle = IsleWorld.level(server);
        if (isle == null) throw new IllegalStateException("no island world");
        return IsleRaids.layout(isle, r);
    }

    /** Blows each nest's heart takes in this raid, and how many nest {@code i}'s has taken. */
    public static int blowsNeeded(IsleRaid r) {
        return r.blowsNeeded;
    }

    public static int blows(IsleRaid r, int i) {
        return r.blows[i];
    }

    /** Ticks from the queen's fall to the king's birth. */
    public static int kingDelay() {
        return IsleRaids.KING_DELAY;
    }

    /** The share of their health a hunter carried off the island is left with. */
    public static float carriedHealth() {
        return IsleRaids.CARRIED_HEALTH;
    }

    /** A raid that is over sends its hunters home on the next turn, without waiting out its last minute. */
    public static void endSoon(IsleRaid r) {
        if (r.phase.over()) r.endedAt -= IsleRaids.AFTER_TICKS;
    }

    /** The chamber's eggs laid again where they are missing, as before every landing. */
    public static void relayEggs(MinecraftServer server, IsleRaid r) {
        ServerLevel isle = IsleWorld.level(server);
        if (isle == null) throw new IllegalStateException("no island world");
        IsleBuilder.chamberEggs(isle, IsleRaids.layout(isle, r));
    }

    /** Whether the island for a seed and number of nests stands. */
    public static boolean built(MinecraftServer server, long seed, int nests) {
        return IsleData.get(server).built(seed, nests);
    }

    private IsleTestAccess() {}
}
