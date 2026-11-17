package com.beyondsolo.depths;

import com.beyondsolo.gate.DungeonLayout;
import com.beyondsolo.gate.GateInstance;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Hooks for game tests that drive the depths by hand (test players are not in the player list, so the half-second
 * tick never sees them). Not for gameplay code.
 */
public final class DepthsTestAccess {
    /** One beat of the hidden chamber for these players (stepping in, the shrine, the lair). */
    public static void tickHidden(ServerLevel level, GateInstance g, List<ServerPlayer> players) {
        HiddenRooms.tick(level, g, players);
    }

    /** One beat of the trap rooms for these players (springing, strikes, a lockdown's waves). */
    public static void tickTraps(ServerLevel level, GateInstance g, List<ServerPlayer> players) {
        TrapRooms.tick(level, g, players, level.getServer().getTickCount());
    }

    /** The gate's creatures standing in a room. */
    public static int creaturesIn(ServerLevel level, GateInstance g, DungeonLayout.Room room) {
        return HiddenRooms.guardians(level, g, room);
    }

    /** The wave a lockdown room is on (0 before it springs). */
    public static int wave(GateInstance g, int room) {
        return g.depths().wave(room);
    }

    /** True if a spot counts as inside a room (for traps and the hidden chamber). */
    public static boolean inside(GateInstance g, DungeonLayout.Room room, Vec3 at) {
        return HiddenRooms.inside(g, room, at, 1.5f);
    }

    /** Forgets a player's knock and strike cooldowns, so one test player can knock (or strike) again at once. */
    public static void resetCooldowns(ServerPlayer p) {
        HiddenRooms.forget(p.getUUID());
        TrapRooms.forget(p.getUUID());
    }

    private DepthsTestAccess() {}
}
