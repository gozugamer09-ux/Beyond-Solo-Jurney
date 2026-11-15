package com.beyondsolo.client.gear;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.screen.SystemTabs;
import com.beyondsolo.gear.GearTooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Client wiring for gear: the FORGE tab of the System window, and the local wearer for set tooltips. */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class GearClientEvents {
    public static final ResourceLocation FORGE_TAB = BeyondSolo.id("forge");

    @SubscribeEvent
    static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            SystemTabs.register(FORGE_TAB, 125, ForgeTab::new);
            GearTooltip.clientWearer = () -> Minecraft.getInstance().player;
        });
    }

    private GearClientEvents() {}
}
