package com.beyondsolo.redgate;

import com.beyondsolo.enemy.EnemyRoster;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Hooks for game tests that drive a red gate by hand (test players are not in the player list and don't tick, so
 * some things have to be asked for). Not for gameplay code.
 */
public final class RedGateTestAccess {
    @Nullable
    public static RedGate run(MinecraftServer server, UUID gate) {
        return RedGateData.get(server).get(gate);
    }

    /** The gate turns red for the next awakened hunter through it ({@code on}), or stops doing so. */
    public static void force(MinecraftServer server, UUID gate, boolean on) {
        RedGateData.get(server).force(gate, on);
    }

    public static boolean defused(MinecraftServer server, UUID gate) {
        return RedGateData.get(server).defused(gate);
    }

    public static void defuse(MinecraftServer server, UUID gate) {
        RedGateData.get(server).defuse(gate);
    }

    /** Forgets whether a gate was forced or defused. */
    public static void forget(MinecraftServer server, UUID gate) {
        RedGateData.get(server).forgetGate(gate);
    }

    /** The gate turns red with {@code p} the first through, and takes them straight into the breach. */
    @Nullable
    public static RedGate startNow(ServerPlayer p, GateBlockEntity gate, GateInstance g) {
        return RedGates.startNow(p, gate, g);
    }

    public static void buildNow(MinecraftServer server, RedGate run) {
        RedGates.buildNow(server, run);
    }

    /** See {@link RedGates#jump}. */
    public static boolean jump(ServerPlayer p, String to, int n) {
        return RedGates.jump(p, to, n);
    }

    /** A wave of the night now, round one of the hunters inside. Returns how many came. */
    public static int raid(MinecraftServer server, RedGate run) {
        ServerLevel level = RedWorld.level(server);
        return level == null ? 0 : RedGates.raid(level, run, RedGates.insidePlayers(server, level, run));
    }

    public static List<UUID> raiders(RedGate run) {
        return run.raiders;
    }

    /** The hunter falls in the red gate (test players can't die). */
    public static void fell(ServerPlayer p) {
        RedGates.playerFell(p);
    }

    /** One beat of the cold for this hunter (the server gives one every half second). */
    public static void cold(ServerPlayer p) {
        RedGate run = RedGates.runOf(p.server, p);
        ServerLevel level = RedWorld.level(p.server);
        if (run != null && level != null) RedGates.cold(level, run, p);
    }

    public static void setChill(ServerPlayer p, int chill) {
        RedGates.setChill(p, chill);
    }

    public static void abort(MinecraftServer server, RedGate run) {
        RedGates.abort(server, run);
    }

    public static PlayerRank insideRank(PlayerRank rank, RandomSource r) {
        return RedGates.insideRank(rank, r);
    }

    public static BossKind masterKind(PlayerRank inside, RandomSource r) {
        return RedGates.masterKind(inside, r);
    }

    public static EnemyRoster.Mix mix(PlayerRank inside) {
        return RedGates.mix(inside);
    }

    public static int slot(RedGate run) {
        return run.slot;
    }

    /** The slot of the patch a position lies in, or -1. */
    public static int slotAt(Level level, BlockPos pos) {
        return RedWorld.slotAt(level, pos);
    }

    public static BlockPos bossSpot(RedGate run) {
        return RedWorldBuilder.bossSpot(run);
    }

    private RedGateTestAccess() {}
}
