package com.beyondsolo.client.party;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.screen.SystemTabs;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Client wiring for parties: the PARTY page of the System window, the party HUD, and forgetting it all on logout. */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class PartyClientEvents {
    public static final ResourceLocation PARTY_TAB = BeyondSolo.id("party");

    @SubscribeEvent
    static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> SystemTabs.register(PARTY_TAB, 103, PartyTab::new));
    }

    @SubscribeEvent
    static void layers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(BeyondSolo.id("party_hud"), PartyHud::render);
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        PartyClient.tick();
    }

    @SubscribeEvent
    static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PartyClient.reset();
    }

    private PartyClientEvents() {}
}
