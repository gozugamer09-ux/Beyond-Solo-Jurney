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
