package com.beyondsolo.client.home;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.screen.SystemTabs;
import com.beyondsolo.home.HomeModule;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Client wiring of the hunter's home: the HOME page of the System window, the training dummies and the trophy
 * pedestals, what floats over them, the going-home HUD, and forgetting it all on logout.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class HomeClientEvents {
    public static final ResourceLocation HOME_TAB = BeyondSolo.id("home");

    @SubscribeEvent
    static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> SystemTabs.register(HOME_TAB, 109, HomeTab::new));
    }

    @SubscribeEvent
    static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(HomeModule.DUMMY.get(), TrainingDummyRenderer::new);
        event.registerBlockEntityRenderer(HomeModule.PEDESTAL_ENTITY.get(), TrophyPedestalRenderer::new);
    }

    @SubscribeEvent
    static void layers(RegisterGuiLayersEvent event) {
        event.registerBelowAll(BeyondSolo.id("home_plates"), HomePlates::render);
        event.registerAboveAll(BeyondSolo.id("home_hud"), HomeHud::render);
    }

    @SubscribeEvent
    static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        HomeClient.reset();
        TrophyPedestalRenderer.clear();
    }

    private HomeClientEvents() {}
}
