package com.beyondsolo.client.reward;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.network.RewardBoxPayload;
import com.beyondsolo.reward.BoxReward;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.util.List;

/**
 * The random boxes on screen: a box opened plays its moment ({@link BoxOverlay}); a blessed box's three offers open
 * the pick ({@link BlessedBoxScreen}).
 */
public final class RewardClient {
    public static void onBox(RewardBoxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        HolderLookup.Provider provider = mc.level.registryAccess();
        if (p.mode() == RewardBoxPayload.OFFER) {
            List<BoxReward> offers = com.beyondsolo.reward.RewardBoxes.offers(p, provider);
            if (offers.isEmpty()) return;
            mc.setScreen(new BlessedBoxScreen(p.hand(), p.seed(), p.rank(), offers));
            return;
        }
        BoxOverlay.opened(new BoxOverlay.Opened(p.kind(), p.rank(), p.reward(provider), p.curse(), p.detail()));
    }

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class Events {
        @SubscribeEvent
        static void layers(RegisterGuiLayersEvent event) {
            event.registerAboveAll(BeyondSolo.id("box_moments"), BoxOverlay::render);
        }

        @SubscribeEvent
        static void frame(RenderFrameEvent.Pre event) {
            BoxOverlay.frame();
        }

        @SubscribeEvent
        static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            BoxOverlay.reset();
        }
    }

    private RewardClient() {}
}
