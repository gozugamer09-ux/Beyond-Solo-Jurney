package com.beyondsolo.spawn;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.Config;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.system.Objectives;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import com.beyondsolo.util.ServerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * First join: drop the player somewhere random and give them the "find a village" objective.
 * Then watch for them entering a village and point them to the Evaluation Zone.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class FirstSpawnHandler {
    /** Players who already played longer than this when the mod was added are left where they are. */
    private static final int NEW_PLAYER_MAX_TICKS = 20 * 60;

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        SystemData data = SystemAPI.get(player);
        if (data.firstJoinHandled()) return;
        SystemAPI.markFirstJoinHandled(player);

        boolean newPlayer = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) < NEW_PLAYER_MAX_TICKS;
        if (Config.RANDOM_SPAWN.get() && newPlayer && player.level().dimension() == Level.OVERWORLD) {
            BlockPos spot = findRandomSpawn(player.serverLevel(), player.getRandom());
            if (spot != null) {
                ServerLevel level = player.serverLevel();
                player.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, player.getYRot(), player.getXRot());
                player.setRespawnPosition(level.dimension(), spot, 0f, true, false);
            }
        }
        if (!data.isAwakened()) {
            // Wait a moment so the pop-ups don't play behind the loading screen.
            ServerScheduler.schedule(60, () -> {
                if (player.isRemoved() || SystemAPI.isAwakened(player)) return;
                Notifications.send(player, SystemNotification.of(SystemNotification.Kind.INFO,
                        Component.translatable("notify.beyondsolo.welcome.title"),
                        Component.translatable("notify.beyondsolo.welcome.body")));
                SystemAPI.setObjective(player, Objectives.FIND_VILLAGE);
            });
        }
    }

    /** Picks a random dry surface spot between the configured min and max distance from world spawn. */
    public static BlockPos findRandomSpawn(ServerLevel level, RandomSource random) {
        BlockPos center = level.getSharedSpawnPos();
        int min = Config.RANDOM_SPAWN_MIN.get();
        int max = Math.max(min + 1, Config.RANDOM_SPAWN_MAX.get());
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int dist = min + random.nextInt(max - min);
            int x = center.getX() + Mth.floor(Math.cos(angle) * dist);
            int z = center.getZ() + Mth.floor(Math.sin(angle) * dist);
            if (!level.getWorldBorder().isWithinBounds(x, z)) continue;

            // Biome check is cheap (no chunk generation), so filter out water first.
            Holder<Biome> biome = level.getBiome(new BlockPos(x, level.getSeaLevel(), z));
            if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_RIVER)) continue;

            level.getChunk(x >> 4, z >> 4); // generate it
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (y <= level.getMinBuildHeight() + 1) continue;
            if (!level.getFluidState(pos.below()).isEmpty() || !level.getFluidState(pos).isEmpty()) continue;
            if (!level.getBlockState(pos.below()).isSolid()) continue;
            return pos;
        }
        BeyondSolo.LOGGER.warn("Could not find a random spawn spot, using world spawn");
        return null;
    }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 40 != 0) return;
        SystemData data = SystemAPI.get(player);
        if (!data.objective().equals(Objectives.FIND_VILLAGE)) return;
        ServerLevel level = player.serverLevel();
        if (level.structureManager().getStructureWithPieceAt(player.blockPosition(), StructureTags.VILLAGE).isValid()) {
            SystemAPI.setObjective(player, Objectives.FIND_EVALUATION_ZONE);
        }
    }

    private FirstSpawnHandler() {}
}
