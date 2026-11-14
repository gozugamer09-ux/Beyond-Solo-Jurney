package com.beyondsolo.client.awakening;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.registry.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Client wiring for the awakening trial: the Arbiter's renderer, the overlay, the attack telegraphs, the fall. */
public final class AwakeningClientEvents {
    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.ARBITER.get(), ArbiterRenderer::new);
        }

        /** Registered last, so the awakening covers every other layer of ours. */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        static void layers(RegisterGuiLayersEvent event) {
            event.registerAboveAll(BeyondSolo.id("awakening"), AwakeningOverlay::render);
        }
    }

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        @SubscribeEvent
        static void frame(RenderFrameEvent.Pre event) {
            AwakeningOverlay.frame();
        }

        @SubscribeEvent
        static void renderLevel(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
            AwakeningWorldFx.render(event.getPoseStack(), event.getCamera(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
        }

        @SubscribeEvent
        static void camera(ViewportEvent.ComputeCameraAngles event) {
            float[] fall = AwakeningOverlay.cameraFall();
            if (fall == null) return;
            event.setRoll(event.getRoll() + fall[0]);
            event.setPitch(event.getPitch() + fall[1]);
        }

        /** The world changes behind the black: the awakening is drawn over the "loading terrain" screen too. */
        @SubscribeEvent
        static void overLoading(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event) {
            if (!AwakeningOverlay.awakening() || !(event.getScreen() instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen)) return;
            var g = event.getGuiGraphics();
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0xFF000000);
            AwakeningOverlay.render(g, net.minecraft.client.Minecraft.getInstance().getTimer());
            g.pose().popPose();
        }

        @SubscribeEvent
        static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            AwakeningOverlay.reset();
        }
    }

    private AwakeningClientEvents() {}
}
