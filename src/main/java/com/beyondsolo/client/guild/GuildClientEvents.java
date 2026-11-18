package com.beyondsolo.client.guild;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.raid.RaidClient;
import com.beyondsolo.client.raid.RaidHud;
import com.beyondsolo.client.screen.SystemTabs;
import com.beyondsolo.guild.GuildModule;
import com.beyondsolo.raid.RaidModule;
import com.beyondsolo.raid.RaidPylonEntity;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Client wiring for guilds and raids: the GUILD page of the System window, the hired hunters' and the barrier
 * pylons' renderers, the raid HUD, and forgetting it all on logout.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class GuildClientEvents {
    public static final ResourceLocation GUILD_TAB = BeyondSolo.id("guild");

    @SubscribeEvent
    static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> SystemTabs.register(GUILD_TAB, 104, GuildTab::new));
    }

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(GuildModule.HUNTER.get(), GuildHunterRenderer::new);
        // a pylon is an end crystal in every way that shows (its beam runs into the boss)
        event.registerEntityRenderer(RaidModule.PYLON.get(),
                ctx -> (EntityRenderer<RaidPylonEntity>) (EntityRenderer<?>) new EndCrystalRenderer(ctx));
    }

    @SubscribeEvent
    static void layers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(BeyondSolo.id("raid_hud"), RaidHud::render);
    }

    @SubscribeEvent
    static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        GuildClient.reset();
        RaidClient.reset();
        RaidHud.reset();
    }

    private GuildClientEvents() {}
}
