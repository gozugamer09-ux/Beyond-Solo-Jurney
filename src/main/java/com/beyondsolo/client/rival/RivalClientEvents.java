package com.beyondsolo.client.rival;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.rival.RivalModule;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Client wiring for rival parties and hired hands: the rival hunters' and the porters' renderers, the HUD, logout. */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class RivalClientEvents {
    @SubscribeEvent
    static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(RivalModule.RIVAL.get(), RivalHunterRenderer::new);
        event.registerEntityRenderer(RivalModule.PORTER.get(), PorterRenderer::new);
    }

    @SubscribeEvent
    static void layers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(BeyondSolo.id("rival_hud"), RivalHud::render);
    }

    @SubscribeEvent
    static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        RivalClient.clear();
        RivalHud.reset();
    }

    private RivalClientEvents() {}
}
