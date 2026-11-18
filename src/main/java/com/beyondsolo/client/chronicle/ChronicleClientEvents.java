package com.beyondsolo.client.chronicle;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.screen.SystemTabs;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Client wiring for the chronicle: the CHRONICLE page, the TITLE ACQUIRED moment and the journal's little cards. */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class ChronicleClientEvents {
    public static final ResourceLocation CHRONICLE_TAB = BeyondSolo.id("chronicle");

    @SubscribeEvent
    static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> SystemTabs.register(CHRONICLE_TAB, 108, ChronicleTab::new));
    }

    @SubscribeEvent
    static void layers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(BeyondSolo.id("chronicle_moments"), TitleOverlay::render);
    }

    @SubscribeEvent
    static void frame(RenderFrameEvent.Pre event) {
        TitleOverlay.frame();
    }

    @SubscribeEvent
    static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        ChronicleClient.clear();
        TitleOverlay.reset();
        JournalArt.clear();
    }

    private ChronicleClientEvents() {}
}
