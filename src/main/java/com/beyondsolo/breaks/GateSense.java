package com.beyondsolo.breaks;

import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.network.GateSensePayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Sensing gates. Pulse Scan's ring of mana reaches much further for gates than for creatures: every gate within
 * {@link #range} shows to the caster for the scan's seconds, as a pillar of its rank's light seen through the ground
 * and a marker on a compass across the top of the screen, with how far it is and, for one coming apart, how long it
 * has left (the client side is {@code client.breaks}).
 */
public final class GateSense {
    /** The most gates one pulse shows (the nearest). */
    public static final int MAX = 12;

    /** How far (blocks) a Pulse Scan of this level senses gates. */
    public static int range(int level) {
        return 96 + 16 * Math.max(1, level);
    }

    /** Pulse Scan's reach for gates: shows the caster what it sensed for {@code seconds} and returns how many. */
    public static int pulse(ServerPlayer p, int level, int seconds) {
        int range = range(level);
        List<GateSensePayload.Entry> found = collect(p.server, p.serverLevel(), p.position(), range, p.getUUID());
        PacketDistributor.sendToPlayer(p, new GateSensePayload(seconds, range, found));
        return found.size();
    }

    /**
     * The gates within {@code range} blocks of a spot, nearest first (at most {@link #MAX}): open, coming apart or broken,
     * cleared and not yet closed, and {@code viewer}'s own sealed gate. A keyed gate is a door its holder turned on
     * purpose and is left out.
     */
    public static List<GateSensePayload.Entry> collect(MinecraftServer server, ServerLevel level, Vec3 at, int range, @Nullable UUID viewer) {
        long now = level.getGameTime();
        List<GateInstance> gates = new ArrayList<>();
        for (GateInstance g : GateData.get(server).all()) {
            if (!g.gateDimension().equals(level.dimension()) || g.state() == GateInstance.State.CLOSED || g.isKeyed()) continue;
            if (g.isTrial() && !Objects.equals(g.owner(), viewer)) continue;
            if (DungeonBreaks.flat(at, g.gatePos()) > range) continue;
            gates.add(g);
        }
        gates.sort(Comparator.comparingDouble(g -> DungeonBreaks.flat(at, g.gatePos())));
        List<GateSensePayload.Entry> out = new ArrayList<>();
        for (GateInstance g : gates.subList(0, Math.min(MAX, gates.size()))) {
            out.add(new GateSensePayload.Entry(g.gatePos(), g.rank().ordinal(), state(g), ticksLeft(g, now)));
        }
        return out;
    }

    /** How a gate is holding, as the pulse reads it ({@link GateSensePayload#HOLDING} .. {@link GateSensePayload#CLEARED}). */
    public static int state(GateInstance g) {
        if (g.isTrial()) return GateSensePayload.SEALED;
        if (g.state() == GateInstance.State.CLEARED) return GateSensePayload.CLEARED;
        return switch (g.breakState().phase()) {
            case UNSTABLE -> GateSensePayload.UNSTABLE;
            case IMMINENT -> GateSensePayload.IMMINENT;
            case BROKEN, SUPPRESSED -> GateSensePayload.BROKEN;
            default -> GateSensePayload.HOLDING;
        };
    }

    /** Ticks before it breaks, for a gate counting down; -1 otherwise. */
    public static int ticksLeft(GateInstance g, long now) {
        BreakState b = g.breakState();
        if (b.phase() != BreakState.Phase.UNSTABLE && b.phase() != BreakState.Phase.IMMINENT) return -1;
        return (int) Math.min(Integer.MAX_VALUE, b.left(now));
    }

    private GateSense() {}
}
