package com.beyondsolo.gate;

import com.beyondsolo.Config;
import com.beyondsolo.network.GatePayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Gates also tear open while you play: once a minute each evaluated player in the overworld has a small chance of a
 * gate opening 40 to 80 blocks away (never with another open gate close by). Its rank is rolled at random, whoever
 * the player is ({@link GateService#randomRank}): an E-rank hunter can find an S-rank gate on their doorstep. The
 * player feels it open (a tremor) and is told how far away it is and which way.
 */
public final class GateSpawner {
    public static void tick(MinecraftServer server) {
        double chance = Config.GATE_SPAWN_CHANCE.get();
        if (chance <= 0) return;
        ServerLevel level = server.overworld();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            if (!SystemAPI.hasSystem(p)) {
                // not yet awakened: D-rank gates keep opening near them until they have cleared one
                if (com.beyondsolo.awakening.AwakeningService.stage(p) != com.beyondsolo.awakening.AwakeningData.Stage.DORMANT) continue;
                if (level.random.nextDouble() >= com.beyondsolo.Config.PRE_SYSTEM_GATE_CHANCE.get() || openGateNear(server, p.blockPosition(), 160)) continue;
                trySpawnNear(level, p, PlayerRank.D);
                continue;
            }
            if (!SystemAPI.isAwakened(p) || level.random.nextDouble() >= chance) continue;
            if (openGateNear(server, p.blockPosition(), 160)) continue;
            trySpawnNear(level, p);
        }
    }

    /** True if a gate that can still be entered stands within {@code range} (a broken one cannot: its boss closes it). */
    public static boolean openGateNear(MinecraftServer server, BlockPos pos, int range) {
        for (GateInstance g : GateData.get(server).all()) {
            if (g.state() == GateInstance.State.CLOSED || g.breakState().hasBroken()) continue;
            if (g.gateDimension().equals(Level.OVERWORLD) && g.gatePos().distSqr(pos) < (double) range * range) return true;
        }
        return false;
    }

    /** Opens a gate somewhere around the player. Returns null if no good spot was found. */
    public static GateBlockEntity trySpawnNear(ServerLevel level, ServerPlayer p) {
        return trySpawnNear(level, p, null);
    }

    /** As above, with the rank fixed (null: rolled at random). */
    public static GateBlockEntity trySpawnNear(ServerLevel level, ServerPlayer p, @org.jetbrains.annotations.Nullable PlayerRank fixed) {
        RandomSource r = level.random;
        for (int attempt = 0; attempt < 8; attempt++) {
            float a = r.nextFloat() * Mth.TWO_PI;
            int d = 40 + r.nextInt(41);
            BlockPos column = BlockPos.containing(p.getX() + Mth.cos(a) * d, 0, p.getZ() + Mth.sin(a) * d);
            if (!level.isLoaded(column)) continue;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (!GateFeature.goodSpot(level, top)) continue;
            PlayerRank rank = fixed != null ? fixed : GateService.randomRank(r);
            Direction facing = Direction.getNearest(p.getX() - top.getX(), 0, p.getZ() - top.getZ());
            if (facing.getAxis().isVertical()) facing = Direction.NORTH;
            GateBlockEntity gate = GateService.spawnGate(level, top, rank, facing, true);
            // the world shudders as it tears, harder the higher the gate
            PacketDistributor.sendToPlayer(p, new GatePayload(GatePayload.SHAKE, 0, -1, 0.18f + 0.05f * rank.tier(), Component.empty(), Component.empty()));
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.ALERT,
                    Component.translatable("notify.beyondsolo.gate.opened.title", rank.displayName()),
                    Component.translatable("notify.beyondsolo.gate.opened.body", d, direction(p.position(), top))));
            return gate;
        }
        return null;
    }

    /** The compass direction from {@code from} to {@code to}, as a word ("north-east"). */
    public static Component direction(net.minecraft.world.phys.Vec3 from, BlockPos to) {
        return com.beyondsolo.npc.NpcQuests.direction((float) Mth.atan2(to.getZ() + 0.5 - from.z, to.getX() + 0.5 - from.x));
    }

    private GateSpawner() {}
}
