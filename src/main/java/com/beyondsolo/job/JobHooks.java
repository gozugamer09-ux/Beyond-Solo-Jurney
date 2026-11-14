package com.beyondsolo.job;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.system.SystemEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Opens the job change at the level milestone and keeps the client in sync. */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class JobHooks {
    @SubscribeEvent
    static void onLevelUp(SystemEvent.LevelUp event) {
        JobAPI.unlockIfReady(event.getPlayer());
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            JobAPI.sync(p);
            JobAPI.unlockIfReady(p);
        }
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            JobAPI.sync(p);
            JobAPI.giveKey(p);
        }
    }

    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        JobCommand.register(event.getDispatcher());
    }

    private JobHooks() {}
}
