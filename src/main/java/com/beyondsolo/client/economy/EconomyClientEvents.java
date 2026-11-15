package com.beyondsolo.client.economy;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.ClientEvents;
import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.screen.SystemTabs;
import com.beyondsolo.economy.EconomyModule;
import com.beyondsolo.economy.LicenseInfo;
import com.beyondsolo.network.EconomyActionPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Client wiring for the economy: the Storage and Shop tabs, the storage key and window, the gold HUD, the license. */
public final class EconomyClientEvents {
    public static final ResourceLocation STORAGE_TAB = BeyondSolo.id("storage");
    public static final ResourceLocation SHOP_TAB = BeyondSolo.id("shop");

    /** Opens the System storage. */
    public static final KeyMapping OPEN_STORAGE = new KeyMapping("key.beyondsolo.open_storage", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, ClientEvents.KEY_CATEGORY);

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                SystemTabs.register(STORAGE_TAB, 110, StorageTab::new);
                SystemTabs.register(SHOP_TAB, 120, ShopTab::new);
                // the card's picture follows the holder's rank (0 = blank, then E..S)
                ItemProperties.register(EconomyModule.HUNTER_LICENSE.get(), BeyondSolo.id("rank"), (stack, level, entity, seed) -> {
                    LicenseInfo info = stack.get(EconomyModule.LICENSE.get());
                    return info == null ? 0f : (info.rank() + 1) / 10f;
                });
            });
        }

        @SubscribeEvent
        static void keys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_STORAGE);
        }

        @SubscribeEvent
        static void screens(RegisterMenuScreensEvent event) {
            event.register(EconomyModule.STORAGE_MENU.get(), StorageScreen::new);
        }

        @SubscribeEvent
        static void layers(RegisterGuiLayersEvent event) {
            event.registerAboveAll(BeyondSolo.id("gold_hud"), GoldHud::render);
        }
    }

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        @SubscribeEvent
        static void tick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            while (OPEN_STORAGE.consumeClick()) {
                if (mc.screen == null && ClientSystemData.get().hasSystem()) {
                    PacketDistributor.sendToServer(new EconomyActionPayload(EconomyActionPayload.OPEN_STORAGE, "", 0));
                }
            }
        }

        @SubscribeEvent
        static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            EconomyClient.clear();
        }
    }

    private EconomyClientEvents() {}
}
