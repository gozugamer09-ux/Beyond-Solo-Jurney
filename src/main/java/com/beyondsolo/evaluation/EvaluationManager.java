package com.beyondsolo.evaluation;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Runs the evaluation: a few seconds of "scanning" with particles while the player stays near
 * the crystal, then a random rank and class.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class EvaluationManager {
    private static final int SCAN_TICKS = 80;
    private static final double MAX_DISTANCE_SQ = 6 * 6;

    private record Scan(ServerPlayer player, BlockPos crystal, int startTick) {}

    private static final Map<UUID, Scan> SCANS = new HashMap<>();

    static void interact(ServerPlayer player, BlockPos crystal) {
        SystemData data = SystemAPI.get(player);
        if (data.isAwakened()) {
            Notifications.send(player, SystemNotification.of(SystemNotification.Kind.INFO,
                    Component.translatable("notify.beyondsolo.already_evaluated.title"),
                    Component.translatable("notify.beyondsolo.already_evaluated.body",
                            data.rank().displayName(), data.playerClass().displayName())));
            return;
        }
        if (SCANS.containsKey(player.getUUID())) return;
        SCANS.put(player.getUUID(), new Scan(player, crystal, player.server.getTickCount()));
        player.serverLevel().playSound(null, crystal, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1f, 1.4f);
        Notifications.send(player, new SystemNotification(SystemNotification.Kind.INFO,
                Component.translatable("notify.beyondsolo.scan.title"),
                Component.translatable("notify.beyondsolo.scan.body"), SCAN_TICKS));
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (SCANS.isEmpty()) return;
        int now = event.getServer().getTickCount();
        Iterator<Map.Entry<UUID, Scan>> it = SCANS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Scan> entry = it.next();
            Scan scan = entry.getValue();
            ServerPlayer player = scan.player();
            if (player.isRemoved() || player.hasDisconnected()) {
                it.remove();
                continue;
            }
            if (player.distanceToSqr(scan.crystal().getCenter()) > MAX_DISTANCE_SQ) {
                it.remove();
                Notifications.send(player, SystemNotification.of(SystemNotification.Kind.ALERT,
                        Component.translatable("notify.beyondsolo.scan_interrupted.title"),
                        Component.translatable("notify.beyondsolo.scan_interrupted.body")));
                continue;
            }
            int elapsed = now - scan.startTick();
            ServerLevel level = player.serverLevel();
            if (elapsed % 4 == 0) {
                double angle = elapsed * 0.35;
                level.sendParticles(ParticleTypes.END_ROD,
                        player.getX() + Math.cos(angle) * 0.9, player.getY() + (elapsed % 40) / 20.0, player.getZ() + Math.sin(angle) * 0.9,
                        1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1, player.getZ(), 6, 0.4, 0.6, 0.4, 0.5);
            }
            if (elapsed >= SCAN_TICKS) {
                it.remove();
                finish(player);
            }
        }
    }

    private static void finish(ServerPlayer player) {
        PlayerRank rank = PlayerRank.roll(player.getRandom());
        PlayerClass playerClass = PlayerClass.roll(player.getRandom());
        SystemAPI.awaken(player, rank, playerClass);

        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 0.8f);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(), 40, 0.5, 0.8, 0.5, 0.3);

        Notifications.send(player, new SystemNotification(SystemNotification.Kind.LEVEL_UP,
                Component.translatable("notify.beyondsolo.evaluated.title"),
                Component.translatable("notify.beyondsolo.evaluated.body", rank.displayName(), playerClass.displayName()), 200));
        Notifications.send(player, new SystemNotification(SystemNotification.Kind.INFO,
                Component.translatable("notify.beyondsolo.system_online.title"),
                Component.translatable("notify.beyondsolo.system_online.body",
                        Component.keybind("key.beyondsolo.open_system"), Component.keybind("key.beyondsolo.notification_mode")), 240));
    }

    private EvaluationManager() {}
}
