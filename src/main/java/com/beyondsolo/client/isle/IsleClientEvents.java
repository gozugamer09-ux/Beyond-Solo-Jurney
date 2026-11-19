package com.beyondsolo.client.isle;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.worldboss.ColossalBossRenderer;
import com.beyondsolo.isle.IsleModule;
import com.beyondsolo.worldboss.WorldBossKind;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Client wiring of the Halcyon raid: the hive's queen, king and nest hearts, and the raid's HUD. */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class IsleClientEvents {
    @SubscribeEvent
    static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(IsleModule.QUEEN.get(), ctx -> new ColossalBossRenderer<>(ctx, WorldBossKind.QUEEN));
        event.registerEntityRenderer(IsleModule.KING.get(), ctx -> new ColossalBossRenderer<>(ctx, WorldBossKind.KING));
        event.registerEntityRenderer(IsleModule.NEST_HEART.get(), NestHeartRenderer::new);
    }

    @SubscribeEvent
    static void layers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(BeyondSolo.id("isle"), IsleHud::render);
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        IsleClient.tick();
    }

    @SubscribeEvent
    static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        IsleClient.clear();
        IsleHud.reset();
    }

    private IsleClientEvents() {}
}
