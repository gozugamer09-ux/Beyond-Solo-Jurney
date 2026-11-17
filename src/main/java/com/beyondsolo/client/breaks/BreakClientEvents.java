package com.beyondsolo.client.breaks;

import com.beyondsolo.BeyondSolo;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Client wiring for dungeon breaks: the HUD layer, the world effects, the heartbeat and the clocks, and the air
 * turning red around a break.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class BreakClientEvents {
    /** The fog's colour in the thick of a break, and how far towards it the air goes. */
    private static final float[] RED_AIR = {0.32f, 0.03f, 0.04f};
    private static final float RED_AIR_MAX = 0.42f;

    @SubscribeEvent
    static void layers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(BeyondSolo.id("breaks"), BreakHud::render);
    }

    @SubscribeEvent
    static void renderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        BreakWorldFx.render(event.getPoseStack(), event.getCamera(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        BreakClient.tick();
        BreakWorldFx.tick();
    }

    /** Near a broken gate the air turns red. */
    @SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = true)
    static void fogColor(ViewportEvent.ComputeFogColor event) {
        float k = BreakClient.dread() * RED_AIR_MAX;
        if (k <= 0.001f) return;
        event.setRed(event.getRed() + (RED_AIR[0] - event.getRed()) * k);
        event.setGreen(event.getGreen() + (RED_AIR[1] - event.getGreen()) * k);
        event.setBlue(event.getBlue() + (RED_AIR[2] - event.getBlue()) * k);
    }

    @SubscribeEvent
    static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        BreakClient.clear();
        BreakWorldFx.clear();
        BreakHud.reset();
    }

    private BreakClientEvents() {}
}
