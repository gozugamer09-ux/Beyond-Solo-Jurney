package com.beyondsolo.client.light;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.light.LightModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

/**
 * Client wiring for the light: the Envoy's and the light's shapes' renderers, the seraph form's wings on players, the
 * ruler's key bar and the light's moments on screen. (The crown keys themselves are the monarch's: a hunter walks one
 * road or the other, see {@code MonarchClientEvents#press}.)
 */
public final class LightClientEvents {
    public static final ResourceLocation BAR_LAYER = BeyondSolo.id("light_bar");
    public static final ResourceLocation HUD_LAYER = BeyondSolo.id("light_hud");

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        static void layers(RegisterGuiLayersEvent event) {
            event.registerAboveAll(BAR_LAYER, LightBar::render);
            event.registerAboveAll(HUD_LAYER, LightHud::render);
        }

        @SubscribeEvent
        static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(LightModule.ENVOY.get(), EnvoyRenderer::new);
            event.registerEntityRenderer(LightModule.CONSTRUCT.get(), LightConstructRenderer::new);
        }

        @SubscribeEvent
        static void playerLayers(EntityRenderersEvent.AddLayers event) {
            for (PlayerSkin.Model skin : event.getSkins()) {
                EntityRenderer<?> r = event.getSkin(skin);
                if (r instanceof PlayerRenderer pr) pr.addLayer(new SeraphLayer(pr));
            }
        }
    }

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        @SubscribeEvent
        static void tick(ClientTickEvent.Post event) {
            if (Minecraft.getInstance().player == null) return;
            LightClient.tick();
        }

        /**
         * While the Envoy speaks or lays its mantles out, the HUD steps aside: its words and the cards stand alone over
         * the chapel or the vigil, with no quest list or bars showing through them.
         */
        @SubscribeEvent
        static void hideHud(RenderGuiLayerEvent.Pre event) {
            net.minecraft.client.gui.screens.Screen s = Minecraft.getInstance().screen;
            if (s instanceof CovenantScreen || s instanceof MantleScreen) event.setCanceled(true);
        }

        @SubscribeEvent
        static void changedWorld(ClientPlayerNetworkEvent.Clone event) {
            LightClient.clearWorld();
        }

        @SubscribeEvent
        static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            LightClient.clear();
        }
    }

    private LightClientEvents() {}
}
