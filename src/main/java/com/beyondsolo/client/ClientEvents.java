package com.beyondsolo.client;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.hud.StatusHud;
import com.beyondsolo.client.notify.NotificationManager;
import com.beyondsolo.client.notify.NotificationRenderer;
import com.beyondsolo.client.screen.DefaultTabs;
import com.beyondsolo.client.screen.SystemScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.lwjgl.glfw.GLFW;

/** Client wiring: key bindings, HUD layers, per-frame updates. */
public final class ClientEvents {
    public static final String KEY_CATEGORY = "key.categories.beyondsolo";
    public static final KeyMapping OPEN_SYSTEM = new KeyMapping("key.beyondsolo.open_system",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, KEY_CATEGORY);
    public static final KeyMapping NOTIFICATION_MODE = new KeyMapping("key.beyondsolo.notification_mode",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, KEY_CATEGORY);
    /** Hides the pop-ups on screen, or brings the latest ones back when none are showing. */
    public static final KeyMapping DISMISS = new KeyMapping("key.beyondsolo.dismiss",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, KEY_CATEGORY);

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(DefaultTabs::register);
        }

        @SubscribeEvent
        static void keys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_SYSTEM);
            event.register(NOTIFICATION_MODE);
            event.register(DISMISS);
        }

        @SubscribeEvent
        static void layers(RegisterGuiLayersEvent event) {
            event.registerAboveAll(BeyondSolo.id("status_hud"), StatusHud::render);
            event.registerAboveAll(BeyondSolo.id("notifications"), NotificationRenderer::renderHud);
        }
    }

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        @SubscribeEvent
        static void frame(RenderFrameEvent.Pre event) {
            Anim.frame();
            NotificationManager.frame();
        }

        @SubscribeEvent
        static void tick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            while (OPEN_SYSTEM.consumeClick()) {
                if (mc.screen == null) mc.setScreen(new SystemScreen());
            }
            while (NOTIFICATION_MODE.consumeClick()) {
                ClientConfig.NotificationMode mode = ClientConfig.cycleMode();
                mc.player.displayClientMessage(Component.translatable("message.beyondsolo.mode_changed",
                        Component.translatable("mode.beyondsolo." + mode.name().toLowerCase())), true);
            }
            while (DISMISS.consumeClick()) {
                hideKeyPressed();
            }
        }

        @SubscribeEvent
        static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientSystemData.clear();
            NotificationManager.clearAll();
            NotificationRenderer.reset();
            StatusHud.reset();
        }
    }

    /** What the hide key does right now. */
    public static void hideKeyPressed() {
        NotificationManager.toggleHidden();
    }

    private ClientEvents() {}
}
