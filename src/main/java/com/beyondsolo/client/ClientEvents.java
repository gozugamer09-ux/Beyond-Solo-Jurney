package com.beyondsolo.client;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.notify.NotificationManager;
import com.beyondsolo.client.notify.NotificationRenderer;
import com.beyondsolo.client.screen.SystemScreen;
import com.beyondsolo.client.screen.SystemTabs;
import com.beyondsolo.system.Objectives;
import com.beyondsolo.system.SystemData;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.glfw.GLFW;

/** Client wiring: key bindings, HUD layers, pop-up rendering. */
public final class ClientEvents {
    public static final String KEY_CATEGORY = "key.categories.beyondsolo";
    public static final KeyMapping OPEN_SYSTEM = new KeyMapping("key.beyondsolo.open_system",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, KEY_CATEGORY);
    public static final KeyMapping NOTIFICATION_MODE = new KeyMapping("key.beyondsolo.notification_mode",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, KEY_CATEGORY);
    public static final KeyMapping DISMISS = new KeyMapping("key.beyondsolo.dismiss",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, KEY_CATEGORY);

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(SystemTabsAccess::registerDefaults);
        }

        @SubscribeEvent
        static void keys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_SYSTEM);
            event.register(NOTIFICATION_MODE);
            event.register(DISMISS);
        }

        @SubscribeEvent
        static void layers(RegisterGuiLayersEvent event) {
            event.registerAboveAll(BeyondSolo.id("status_hud"), ClientEvents::renderStatusHud);
            event.registerAboveAll(BeyondSolo.id("notifications"), NotificationRenderer::renderHud);
        }
    }

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        @SubscribeEvent
        static void tick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            NotificationManager.tick(mc.player.tickCount);
            while (OPEN_SYSTEM.consumeClick()) {
                if (mc.screen == null) mc.setScreen(new SystemScreen());
            }
            while (NOTIFICATION_MODE.consumeClick()) {
                ClientConfig.NotificationMode mode = ClientConfig.cycleMode();
                mc.player.displayClientMessage(Component.translatable("message.beyondsolo.mode_changed",
                        Component.translatable("mode.beyondsolo." + mode.name().toLowerCase())), true);
            }
            while (DISMISS.consumeClick()) {
                NotificationManager.dismissAll();
            }
        }

        @SubscribeEvent
        static void renderLevel(RenderLevelStageEvent event) {
            NotificationRenderer.renderWorld(event);
        }

        @SubscribeEvent
        static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientSystemData.clear();
            NotificationManager.clearAll();
        }
    }

    /** Top-left mini status: level, mana, fatigue, objective. Plus the collapse overlay. */
    static void renderStatusHud(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;
        SystemData d = ClientSystemData.get();
        Font font = mc.font;

        if (d.isCollapsed()) {
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0x70200000);
            Component title = Component.translatable("hud.beyondsolo.collapsed");
            Component time = Component.translatable("hud.beyondsolo.collapsed.time", (d.collapseTicks() + 19) / 20);
            g.pose().pushPose();
            g.pose().translate(g.guiWidth() / 2f, g.guiHeight() / 2f - 30, 0);
            g.pose().scale(2f, 2f, 1f);
            g.drawCenteredString(font, title, 0, 0, 0xFFEF5350);
            g.pose().popPose();
            g.drawCenteredString(font, time, g.guiWidth() / 2, g.guiHeight() / 2 - 6, SystemTheme.TEXT);
        }

        if (!ClientConfig.SHOW_HUD.get()) return;
        int x = 6;
        int y = 6;
        if (d.isAwakened()) {
            Component lv = Component.translatable("hud.beyondsolo.level", d.level(), d.rank().displayName());
            g.drawString(font, lv, x, y, SystemTheme.ACCENT, true);
            y += 11;
            g.drawString(font, "MP", x, y, SystemTheme.TEXT_DIM, true);
            SystemTheme.bar(g, x + 18, y + 2, 60, 4, d.maxMana() <= 0 ? 0 : d.mana() / d.maxMana(), SystemTheme.MP);
            y += 9;
            if (d.exhaustion() > 0.5f) {
                int color = d.exhaustion() >= 80 ? 0xFFEF5350 : SystemTheme.FATIGUE;
                g.drawString(font, "FT", x, y, SystemTheme.TEXT_DIM, true);
                SystemTheme.bar(g, x + 18, y + 2, 60, 4, d.exhaustion() / 100f, color);
                g.drawString(font, Math.round(d.exhaustion()) + "%", x + 82, y, color, true);
                y += 9;
            }
            y += 3;
        }
        if (!d.objective().isEmpty()) {
            Component obj = Component.literal("◆ ").append(Objectives.title(d.objective()));
            g.drawString(font, obj, x, y, 0xFFFFD54F, true);
        }
    }

    /** Tiny bridge so the package-private default registration can run from here. */
    private static final class SystemTabsAccess {
        static void registerDefaults() {
            com.beyondsolo.client.screen.DefaultTabs.register();
        }
    }

    private ClientEvents() {}
}
