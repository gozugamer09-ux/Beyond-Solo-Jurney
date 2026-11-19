package com.beyondsolo.client.story;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.network.StoryLinePayload;
import com.beyondsolo.story.StoryModule;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Client wiring for the hunter's story: the scene's lines on screen, their mother's and father's renderers and name plates, visions. */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class StoryClient {
    /** The layer the scene's lines are drawn on (it stays while the HUD steps aside for a scene). */
    public static final ResourceLocation LINES = BeyondSolo.id("story_lines");
    private static final ResourceLocation MARKERS = BeyondSolo.id("kin_markers");

    public static void onLine(StoryLinePayload p) {
        StoryOverlay.onLine(p);
    }

    /** One of the story's own layers, drawn while a scene holds the screen. */
    public static boolean ownLayer(ResourceLocation name) {
        return name.equals(LINES);
    }

    @SubscribeEvent
    static void layers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(MARKERS, KinMarkers::render);
        event.registerAboveAll(LINES, StoryOverlay::render);
    }

    @SubscribeEvent
    static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(StoryModule.MOTHER.get(), KinRenderer::new);
        event.registerEntityRenderer(StoryModule.FATHER.get(), FatherRenderer::new);
        event.registerEntityRenderer(StoryModule.VISION.get(), VisionRenderer::new);
    }

    @SubscribeEvent
    static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        StoryOverlay.reset();
    }

    private StoryClient() {}
}
