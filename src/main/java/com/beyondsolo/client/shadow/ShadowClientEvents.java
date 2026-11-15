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
        }
    }

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        @SubscribeEvent
        static void tick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            ShadowWorldFx.tick();
            while (RISE.consumeClick()) if (mc.screen == null) send(ShadowActionPayload.RISE);
            while (RECALL.consumeClick()) if (mc.screen == null) send(ShadowActionPayload.RECALL);
            while (MOUNT.consumeClick()) if (mc.screen == null) send(ShadowActionPayload.MOUNT);
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

    private static void send(int action) {
        if (!ShadowClient.canRaise()) return;
        PacketDistributor.sendToServer(new ShadowActionPayload(action, ""));
    }

    private ShadowClientEvents() {}
}
