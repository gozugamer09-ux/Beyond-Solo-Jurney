package com.beyondsolo.client.association;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.association.AssociationModule;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Client wiring for the Hunter Association: the officer's and the broker's renderers, their marks, logout. */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class AssociationClientEvents {
    @SubscribeEvent
    static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AssociationModule.OFFICER.get(), AssociationNpcRenderer::new);
        event.registerEntityRenderer(AssociationModule.BROKER.get(), AssociationNpcRenderer::new);
    }

    @SubscribeEvent
    static void layers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(BeyondSolo.id("association_markers"), AssociationMarkers::render);
    }

    @SubscribeEvent
    static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        AssociationClient.reset();
    }

    private AssociationClientEvents() {}
}
