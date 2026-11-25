package com.beyondsolo.client.news;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.association.ScoutRenderer;
import com.beyondsolo.scouting.ScoutingModule;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Client wiring for the hunter news and the great guilds' scouts: the ticker, the scout's renderer, logout. */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class NewsClientEvents {
    @SubscribeEvent
    static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ScoutingModule.SCOUT.get(), ScoutRenderer::new);
    }

    @SubscribeEvent
    static void layers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(BeyondSolo.id("news_ticker"), NewsTicker::render);
    }

    @SubscribeEvent
    static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        NewsClient.reset();
        NewsTicker.reset();
    }

    private NewsClientEvents() {}
}
