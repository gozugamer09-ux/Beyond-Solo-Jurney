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
import org.jetbrains.annotations.Nullable;

/**
 * First join: drop the player somewhere random (everyone after the first beside where the first one landed, with
 * {@code spawn.together}) and give them the "find a village" objective. Then watch for them entering a village and
 * point them to the Evaluation Zone.
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
            ServerLevel level = player.serverLevel();
            BlockPos spot = newPlayerSpot(level, player.getRandom());
            if (spot != null) {
                player.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, player.getYRot(), player.getXRot());
                player.setRespawnPosition(level.dimension(), spot, 0f, true, false);
            }
        }
        // With the awakening trial off, the System is there from the first moment (AwakeningService grants it on
        // login, before this runs or right after); with it on, the welcome waits for the awakening.
        if (!data.isAwakened() && (data.hasSystem() || !com.beyondsolo.Config.AWAKENING_TRIAL.get())) systemIntro(player, 60);
    }

    /**
     * The System's first words to a player it has just bound to: the welcome, and the way to a village and its
     * Evaluation Zone. {@code delay} ticks from now, so it doesn't play behind a loading screen or the awakening.
     */
    public static void systemIntro(ServerPlayer player, int delay) {
        ServerScheduler.schedule(delay, () -> {
            if (player.isRemoved() || player.hasDisconnected() || SystemAPI.isAwakened(player) || !SystemAPI.hasSystem(player)) return;
            Notifications.send(player, SystemNotification.of(SystemNotification.Kind.INFO,
                    Component.translatable("notify.beyondsolo.welcome.title"),
                    Component.translatable("notify.beyondsolo.welcome.body")));
            SystemAPI.setObjective(player, Objectives.FIND_VILLAGE);
        });
    }

    /**
     * A fresh start (the wipe command): the player is put down where a new player would be ({@link #newPlayerSpot}:
     * beside the world's first landing with {@code spawn.together}, else a new random spot in the overworld), which
     * becomes their respawn point (or at world spawn when random spawns are off or none was found).
     */
    public static void startOver(ServerPlayer player) {
        ServerLevel world = player.server.overworld();
        BlockPos spot = Config.RANDOM_SPAWN.get() ? newPlayerSpot(world, player.getRandom()) : null;
        if (spot != null) {
            player.setRespawnPosition(world.dimension(), spot, 0f, true, false);
        } else {
            player.setRespawnPosition(Level.OVERWORLD, null, 0f, false, false);
            spot = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, world.getSharedSpawnPos());
        }
        player.stopRiding();
        player.teleportTo(world, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, player.getYRot(), 0f);
        // fake players (tests) ignore same-level teleports
        if (player.distanceToSqr(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5) > 1) {
            player.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, player.getYRot(), 0f);
        }
        player.fallDistance = 0;
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        SystemAPI.markFirstJoinHandled(player);
    }

    /**
     * Where a new player lands in the overworld: friends start together, so with {@code spawn.together} it is beside
     * the world's first landing (see {@link LandingData}; the first new player's random spot becomes it), else a random
     * spot. Null if none was found.
     */
    @Nullable
    static BlockPos newPlayerSpot(ServerLevel level, RandomSource random) {
        LandingData landing = LandingData.get(level.getServer());
        BlockPos spot = Config.SPAWN_TOGETHER.get() ? landing.near(level, random) : null;
        if (spot == null) {
            spot = findRandomSpawn(level, random);
            if (spot != null && landing.landing() == null) landing.set(spot);
        }
        return spot;
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
            if (!LandingData.roomToStand(level, pos)) continue;
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
