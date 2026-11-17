package com.beyondsolo.gate;

import net.minecraft.server.level.ServerPlayer;

/**
 * Hooks for game tests that drive gates by hand (test players are not in the player list, so the half-second watch
 * never sees them). Not for gameplay code.
 */
public final class GateTestAccess {
    /** One beat of the watch for this player: the packs and the boss wake as they come near, checkpoints are kept. */
    public static void watch(ServerPlayer player) {
        GateService.watch(player);
    }

    private GateTestAccess() {}
}
