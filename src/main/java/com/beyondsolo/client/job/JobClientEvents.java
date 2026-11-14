package com.beyondsolo.client.job;

import com.beyondsolo.BeyondSolo;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Client wiring for the job change: the trial banner, the reveal, and the cutscene camera. */
public final class JobClientEvents {
    private static final ResourceLocation TRIAL_LAYER = BeyondSolo.id("job_trial");
    private static final ResourceLocation REVEAL_LAYER = BeyondSolo.id("job_reveal");

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        static void layers(RegisterGuiLayersEvent event) {
            event.registerAboveAll(TRIAL_LAYER, TrialHud::render);
            event.registerAboveAll(REVEAL_LAYER, JobRevealOverlay::render);
        }
    }

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        @SubscribeEvent
        static void frame(RenderFrameEvent.Pre event) {
            JobRevealOverlay.frame(event.getPartialTick());
        }

        @SubscribeEvent
        static void tick(ClientTickEvent.Post event) {
            if (Minecraft.getInstance().player != null) JobRevealOverlay.tick();
        }

        /** Runs first so camera shake from other systems still adds on top. */
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        static void camera(ViewportEvent.ComputeCameraAngles event) {
            float[] look = CutsceneCamera.apply(event.getCamera(), (float) event.getPartialTick());
            if (look == null) return;
            event.setYaw(look[0]);
            event.setPitch(look[1]);
            event.setRoll(0);
        }

        /** While the camera is away, only the cutscene's own layers are drawn. */
        @SubscribeEvent
        static void hideHud(RenderGuiLayerEvent.Pre event) {
            if (!CutsceneCamera.active() && !JobRevealOverlay.active()) return;
            ResourceLocation name = event.getName();
            if (name.equals(TRIAL_LAYER) || name.equals(REVEAL_LAYER)) return;
            if (name.getPath().equals("gate_moments")) return; // camera shake and the boss intro title
            event.setCanceled(true);
        }

        @SubscribeEvent
        static void hand(RenderHandEvent event) {
            if (CutsceneCamera.active()) event.setCanceled(true);
        }

        @SubscribeEvent
        static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            JobClient.clear();
        }
    }

    private JobClientEvents() {}
}
