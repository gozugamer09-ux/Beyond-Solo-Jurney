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

    /** The room a trap forced by command goes in (null if no room will do). */
    @org.jetbrains.annotations.Nullable
    public static DungeonLayout.Room forcedTrapRoom(DungeonLayout layout, TrapKind kind) {
        return DepthsCommand.trapRoom(layout, kind);
    }

    /** The trap rooms a seed gives a plan at a rank, as a gate rolls them. */
    public static java.util.Map<Integer, TrapKind> rollTraps(long seed, com.beyondsolo.system.PlayerRank rank, DungeonLayout layout) {
        return GateDepths.roll(seed, rank.tier(), layout);
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

    /**
     * Hears what a player's screen is told about the cave it is in ({@code DepthsPayload.STATE}: the weather on the way
     * in, -1 on the way out), since test players drop everything sent to them. Null stops listening.
     */
    public static void listen(ServerPlayer p, @org.jetbrains.annotations.Nullable java.util.function.Consumer<com.beyondsolo.network.DepthsPayload> listener) {
        DepthsHooks.listen(p.getUUID(), listener);
    }

    /** One beat of what this player's screen is told about the cave they stand in, as the half-second tick does. */
    public static void beat(ServerPlayer p) {
        DepthsHooks.beat(p);
    }

    /**
     * What happens when a player goes through to another dimension from {@code from}. The game test server has no gate
     * dimension (caves are carved into its overworld), so a test plays the change the way out makes in the game.
     */
    public static void changedDimension(ServerPlayer p, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> from) {
        DepthsHooks.changedDimension(p, from);
    }

    /** What happens when a player comes back from a fall (test players can't be respawned). */
    public static void respawned(ServerPlayer p) {
        DepthsHooks.respawned(p);
    }

    private DepthsTestAccess() {}
}
