package com.beyondsolo.breaks;

import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

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

    /** The next wave is due now. */
    public static void waveDue(GateInstance g) {
        g.breakState().backdateWave(DungeonBreaks.WAVE_TICKS);
    }

    /** The quest the System gives a hunter near the break. */
    public static void giveQuest(ServerPlayer p, GateInstance g) {
        DungeonBreaks.giveQuest(p, g);
    }

    /** A hunter's share of a suppressed break. */
    public static void reward(ServerPlayer p, GateInstance g, long xp) {
        DungeonBreaks.reward(p, g, xp, Component.empty(), Component.empty());
    }

    private BreakTestAccess() {}
}
