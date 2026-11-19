package com.beyondsolo.client.fx;

import com.beyondsolo.BeyondSolo;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Keeps the auras' memory ({@link Aura#state}) tidy: counts frames, forgets figures no longer drawn, empties on leaving a world. */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class FxClientEvents {
    @SubscribeEvent
    static void frame(RenderFrameEvent.Pre event) {
        Aura.tickFrame();
    }

    @SubscribeEvent
    static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        Aura.clear();
    }

    private FxClientEvents() {}
}
