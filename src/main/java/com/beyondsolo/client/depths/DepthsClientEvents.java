package com.beyondsolo.client.depths;

import com.beyondsolo.BeyondSolo;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Client wiring for the depths: the HUD layer, the world effects and the weather's air, the fog (laid over the gate's
 * own cave fog, so it listens late and on cancelled events), letting go of a cave as soon as the player is out of the
 * gate dimension, and the creature affinity lookup the elemental profiles use on this side.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class DepthsClientEvents {
    @SubscribeEvent
    static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(DepthsClient::setup);
    }

    @SubscribeEvent
    static void layers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(BeyondSolo.id("depths"), DepthsHud::render);
    }

    @SubscribeEvent
    static void renderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        WeatherFx.render(event.getPoseStack(), event.getCamera(), partial);
        DepthsWorldFx.render(event.getPoseStack(), event.getCamera(), partial);
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        // first, so nothing below draws a cave the player has already left
        DepthsClient.tick();
        WeatherFx.tick();
        DepthsWorldFx.tick();
        DepthsHud.tick();
    }

    @SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = true)
    static void fogColor(ViewportEvent.ComputeFogColor event) {
        float[] c = WeatherFx.fogColor(event.getRed(), event.getGreen(), event.getBlue());
        if (c == null) return;
        event.setRed(c[0]);
        event.setGreen(c[1]);
        event.setBlue(c[2]);
    }

    @SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = true)
    static void fog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.NONE) return;
        float far = WeatherFx.fogFar(event.getFarPlaneDistance());
        if (far < 0) return;
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), far * 0.15f));
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        DepthsClient.clear();
        DepthsHud.reset();
        DepthsWorldFx.clear();
        WeatherFx.clear();
    }

    private DepthsClientEvents() {}
}
