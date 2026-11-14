package com.beyondsolo.gate;

import com.beyondsolo.Config;
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

/**
 * Gates also tear open while you play: once a minute each evaluated player in the overworld has a small chance of a
 * gate opening 40 to 80 blocks away (never with another open gate close by). Its rank is near the player's own,
 * sometimes a step above.
 */
public final class GateSpawner {
    public static void tick(MinecraftServer server) {
        double chance = Config.GATE_SPAWN_CHANCE.get();
        if (chance <= 0) return;
        ServerLevel level = server.overworld();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !SystemAPI.isAwakened(p) || level.random.nextDouble() >= chance) continue;
            if (openGateNear(server, p.blockPosition(), 160)) continue;
            trySpawnNear(level, p);
        }
    }

    public static boolean openGateNear(MinecraftServer server, BlockPos pos, int range) {
        for (GateInstance g : GateData.get(server).all()) {
            if (g.state() != GateInstance.State.CLOSED && g.gateDimension().equals(Level.OVERWORLD) && g.gatePos().distSqr(pos) < (double) range * range) return true;
        }
        return false;
    }

    /** Opens a gate somewhere around the player. Returns null if no good spot was found. */
    public static GateBlockEntity trySpawnNear(ServerLevel level, ServerPlayer p) {
        RandomSource r = level.random;
        for (int attempt = 0; attempt < 8; attempt++) {
            float a = r.nextFloat() * Mth.TWO_PI;
            int d = 40 + r.nextInt(41);
            BlockPos column = BlockPos.containing(p.getX() + Mth.cos(a) * d, 0, p.getZ() + Mth.sin(a) * d);
            if (!level.isLoaded(column)) continue;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (!GateFeature.goodSpot(level, top)) continue;
            int tier = SystemAPI.get(p).rank().tier() + (r.nextInt(10) < 3 ? 1 : 0) - (r.nextInt(10) == 0 ? 1 : 0);
            PlayerRank rank = PlayerRank.values()[Mth.clamp(tier, 0, PlayerRank.values().length - 1)];
            Direction facing = Direction.getNearest(p.getX() - top.getX(), 0, p.getZ() - top.getZ());
            if (facing.getAxis().isVertical()) facing = Direction.NORTH;
            GateBlockEntity gate = GateService.spawnGate(level, top, rank, facing, true);
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.ALERT,
                    Component.translatable("notify.beyondsolo.gate.opened.title", rank.displayName()),
                    Component.translatable("notify.beyondsolo.gate.opened.body", d)));
            return gate;
        }
        return null;
    }

    private GateSpawner() {}
}
