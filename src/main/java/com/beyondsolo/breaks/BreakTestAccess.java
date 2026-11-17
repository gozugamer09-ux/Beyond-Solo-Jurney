package com.beyondsolo.breaks;

import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import net.minecraft.server.level.ServerLevel;

/**
 * Hooks for game tests that drive a break by hand (test players are not in the player list, so the server's own
 * once-a-second look never counts them as watching). Not for gameplay code.
 */
public final class BreakTestAccess {
    /** One second of a broken gate, as if someone were (or were not) near to see it. */
    public static void brokenTick(ServerLevel world, GateInstance g, boolean watched) {
        DungeonBreaks.brokenTick(world, GateData.get(world.getServer()), g, watched);
    }

    /** Pulls the break's strays back towards their gate. */
    public static void leash(ServerLevel world, GateInstance g) {
        DungeonBreaks.leash(world, g, DungeonBreaks.monsters(world, g));
    }

    private BreakTestAccess() {}
}
