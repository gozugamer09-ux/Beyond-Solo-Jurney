package com.beyondsolo.client.shadow;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.ClientEvents;
import com.beyondsolo.client.screen.SystemTabs;
import com.beyondsolo.network.ShadowActionPayload;
import com.beyondsolo.shadow.ShadowModule;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Client wiring for the shadow army: keys, the Shadows tab, the renderer, HUD and world effects. */
public final class ShadowClientEvents {
    public static final ResourceLocation SHADOWS_TAB = BeyondSolo.id("shadows");

    /** Extract the soul in reach, or call the army out. */
    public static final KeyMapping RISE = new KeyMapping("key.beyondsolo.shadow_rise", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, ClientEvents.KEY_CATEGORY);
    /** Every shadow back into the dark. */
    public static final KeyMapping RECALL = new KeyMapping("key.beyondsolo.shadow_recall", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, ClientEvents.KEY_CATEGORY);
    /** Call the shadow mount and ride it (again to get off). */
    public static final KeyMapping MOUNT = new KeyMapping("key.beyondsolo.shadow_mount", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, ClientEvents.KEY_CATEGORY);

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> SystemTabs.register(SHADOWS_TAB, 60, ShadowsTab::new));
        }

        @SubscribeEvent
        static void keys(RegisterKeyMappingsEvent event) {
            event.register(RISE);
            event.register(RECALL);
            event.register(MOUNT);
        }

        @SubscribeEvent
        static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ShadowModule.SHADOW.get(), ShadowRenderer::new);
        }

        @SubscribeEvent
        static void layers(RegisterGuiLayersEvent event) {
            event.registerAboveAll(BeyondSolo.id("shadow_hud"), ShadowHud::render);
            // the guard posts' marks are registered with the other world marks, under the pop-ups (ClientEvents)
        }
    }

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        @SubscribeEvent
        static void tick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            ShadowWorldFx.tick();
            // once a press: a key held down repeats its clicks (the ritual takes two seconds, long enough for that),
            // and each would be another call
            if (pressed(RISE, 0) && mc.screen == null) send(ShadowActionPayload.RISE);
            if (pressed(RECALL, 1) && mc.screen == null) send(ShadowActionPayload.RECALL);
            if (pressed(MOUNT, 2) && mc.screen == null) send(ShadowActionPayload.MOUNT);
        }

        @SubscribeEvent
        static void renderLevel(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
            ShadowWorldFx.render(event.getPoseStack(), event.getCamera(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
        }

        @SubscribeEvent
        static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            ShadowClient.clear();
        }
    }

    /** Whether each key was held at the end of the last tick. */
    private static final boolean[] HELD = new boolean[3];

    /** True once for each press of the key: its clicks are used up, and while it stays held its repeats are not. */
    private static boolean pressed(KeyMapping key, int slot) {
        boolean clicked = false;
        while (key.consumeClick()) clicked = true;
        boolean fresh = clicked && !HELD[slot];
        HELD[slot] = key.isDown();
        return fresh;
    }

    private static void send(int action) {
        if (!ShadowClient.canRaise()) return;
        PacketDistributor.sendToServer(new ShadowActionPayload(action, ""));
    }

    private ShadowClientEvents() {}
}
