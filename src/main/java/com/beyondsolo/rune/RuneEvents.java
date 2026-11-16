package com.beyondsolo.rune;

import com.beyondsolo.BeyondSolo;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Keeps the client's copy of the rune data fresh, and registers {@code /system rune}. */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class RuneEvents {
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) Runes.sync(p);
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) Runes.sync(p);
    }

    @SubscribeEvent
    static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) Runes.sync(p);
    }

    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        RuneCommand.register(event.getDispatcher());
    }

    private RuneEvents() {}
}
