package com.beyondsolo.client;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.screen.SystemScreen;
import com.beyondsolo.client.screen.SystemTabs;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Dev-only: walks through the UI on its own so screenshots can be taken without a keyboard.
 * Only active when the JVM is started with -Dbeyondsolo.uiTest=true (./gradlew runClient -PuiTest).
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class UiTestDriver {
    private static final boolean ENABLED = Boolean.getBoolean("beyondsolo.uiTest");
    private static int ticks;

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!ENABLED || mc.player == null) return;
        ticks++;
        if (ticks % 100 != 0) return;
        int step = ticks / 100;
        switch (step) {
            case 2 -> ClientConfig.NOTIFICATION_MODE.set(ClientConfig.NotificationMode.FLOATING);
            case 4 -> ClientConfig.NOTIFICATION_MODE.set(ClientConfig.NotificationMode.PINNED);
            case 6 -> {
                SystemScreen.openOn(SystemTabs.STATUS);
                mc.setScreen(new SystemScreen());
            }
            case 7 -> {
                SystemScreen.openOn(SystemTabs.QUESTS);
                mc.setScreen(new SystemScreen());
            }
            case 8 -> {
                SystemScreen.openOn(SystemTabs.LOG);
                mc.setScreen(new SystemScreen());
            }
            case 9 -> {
                mc.setScreen(null);
                ClientConfig.NOTIFICATION_MODE.set(ClientConfig.NotificationMode.HIDDEN);
            }
            default -> {}
        }
        BeyondSolo.LOGGER.info("UITEST step {}", step);
    }

    private UiTestDriver() {}
}
