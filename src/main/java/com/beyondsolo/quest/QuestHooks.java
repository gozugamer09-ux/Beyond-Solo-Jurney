package com.beyondsolo.quest;

import com.beyondsolo.BeyondSolo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Feeds game events into the quest engine: kills, broken blocks, walking, death, login. */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class QuestHooks {
    /** Where each player was at the last travel sample, and the part of a block walked since. */
    private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();
    private static final Map<UUID, Double> WALKED = new HashMap<>();

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        if (victim instanceof ServerPlayer player) {
            QuestEngine.onDeath(player);
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer killer) {
            QuestEngine.progress(killer, ObjectiveType.KILL, o -> Targets.entity(o.target(), victim), 1, victim.position());
        }
    }

    @SubscribeEvent
    static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player)) return;
        QuestEngine.progress(player, ObjectiveType.BREAK, o -> Targets.block(o.target(), event.getState()), 1, Vec3.atCenterOf(event.getPos()));
    }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        int age = player.tickCount + Math.floorMod(player.getUUID().hashCode(), 20);
        if (age % 10 == 0) sampleTravel(player);
        if (age % 20 == 0) QuestEngine.tick(player);
    }

    /** Counts blocks walked or run on foot (not riding, flying, swimming far or being teleported). */
    private static void sampleTravel(ServerPlayer player) {
        Vec3 now = player.position();
        Vec3 last = LAST_POS.put(player.getUUID(), now);
        if (last == null || player.isPassenger() || player.isFallFlying() || player.getAbilities().flying || player.isSpectator()) return;
        double dist = Math.sqrt((now.x - last.x) * (now.x - last.x) + (now.z - last.z) * (now.z - last.z));
        if (dist > 8) return; // a teleport, not a walk
        double total = WALKED.getOrDefault(player.getUUID(), 0.0) + dist;
        int blocks = (int) total;
        WALKED.put(player.getUUID(), total - blocks);
        if (blocks > 0) QuestEngine.progress(player, ObjectiveType.TRAVEL, o -> true, blocks, now);
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) QuestEngine.sync(player);
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) QuestEngine.sync(player);
    }

    @SubscribeEvent
    static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LAST_POS.remove(player.getUUID());
            QuestEngine.sync(player);
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_POS.remove(event.getEntity().getUUID());
        WALKED.remove(event.getEntity().getUUID());
    }

    private QuestHooks() {}
}
