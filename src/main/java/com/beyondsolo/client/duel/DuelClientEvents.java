package com.beyondsolo.client.duel;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.duel.DuelModule;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Client wiring for the duels with the national-level hunters: their renderer, the duel HUD, logout. The ring is drawn
 * with the other warnings on the land ({@code client.worldboss.WorldBossWorldFx}).
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class DuelClientEvents {
    @SubscribeEvent
    static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DuelModule.DUELIST.get(), DuelistRenderer::new);
    }

    @SubscribeEvent
    static void layers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(BeyondSolo.id("duel_hud"), DuelHud::render);
    }

    @SubscribeEvent
    static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        DuelClient.reset();
        DuelHud.reset();
    }

    private DuelClientEvents() {}
}
