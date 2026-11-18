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
            CutsceneCamera.frame(event.getPartialTick().getGameTimeDeltaPartialTick(true));
        }

        @SubscribeEvent
        static void tick(ClientTickEvent.Post event) {
            if (Minecraft.getInstance().player != null) JobRevealOverlay.tick();
        }

        /** Runs first so camera shake from other systems still adds on top. */
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        static void camera(ViewportEvent.ComputeCameraAngles event) {
            float[] look = CutsceneCamera.angles();
            if (look == null) return;
            event.setYaw(look[0]);
            event.setPitch(look[1]);
            event.setRoll(0);
        }

        /** While the camera is away, only the cutscene's own layers are drawn. */
        @SubscribeEvent
        static void hideHud(RenderGuiLayerEvent.Pre event) {
            if (!CutsceneCamera.active() && !JobRevealOverlay.active() && !com.beyondsolo.client.monarch.CoronationOverlay.active()) return;
            ResourceLocation name = event.getName();
            if (name.equals(TRIAL_LAYER) || name.equals(REVEAL_LAYER)) return;
            if (com.beyondsolo.client.monarch.MonarchClientEvents.ownLayer(name)) return; // the throne's question and the coronation
            if (name.getPath().equals("gate_moments")) return; // camera shake and the boss intro title
            if (name.getPath().equals("penalty")) return; // the Penalty Zone's arrival card
            event.setCanceled(true);
        }

        @SubscribeEvent
        static void hand(RenderHandEvent event) {
            if (CutsceneCamera.active()) event.setCanceled(true);
        }

        /** The player's own name tag stays hidden while the camera looks at them. */
        @SubscribeEvent
        static void nameTag(net.neoforged.neoforge.client.event.RenderNameTagEvent event) {
            if (CutsceneCamera.active() && event.getEntity() == Minecraft.getInstance().player) {
                event.setCanRender(net.neoforged.neoforge.common.util.TriState.FALSE);
            }
        }

        @SubscribeEvent
        static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            JobClient.clear();
        }
    }

    private JobClientEvents() {}
}
