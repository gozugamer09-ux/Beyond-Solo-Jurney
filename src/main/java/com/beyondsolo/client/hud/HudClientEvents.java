package com.beyondsolo.client.hud;

import com.beyondsolo.BeyondSolo;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.Set;

/**
 * Client wiring for the System HUD: its two layers sit where the vanilla pieces they replace were (so chat, titles
 * and other mods' layers stay above them as before), and the vanilla pieces are switched off while it is on.
 */
public final class HudClientEvents {
    /** The vanilla layers the System HUD draws itself. Spectators keep vanilla's (see {@link VitalsHud#active()}). */
    private static final Set<ResourceLocation> REPLACED = Set.of(
            VanillaGuiLayers.HOTBAR, VanillaGuiLayers.JUMP_METER, VanillaGuiLayers.EXPERIENCE_BAR,
            VanillaGuiLayers.PLAYER_HEALTH, VanillaGuiLayers.ARMOR_LEVEL, VanillaGuiLayers.FOOD_LEVEL,
            VanillaGuiLayers.VEHICLE_HEALTH, VanillaGuiLayers.AIR_LEVEL, VanillaGuiLayers.EXPERIENCE_LEVEL,
            VanillaGuiLayers.EFFECTS);

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        static void layers(RegisterGuiLayersEvent event) {
            event.registerAbove(VanillaGuiLayers.HOTBAR, BeyondSolo.id("vitals"), VitalsHud::render);
            event.registerAbove(VanillaGuiLayers.EFFECTS, BeyondSolo.id("effects"), EffectsHud::render);
        }
    }

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        /** Before any layer draws: the room our pieces took last frame is given back (see {@link HudSpace}). */
        @SubscribeEvent(receiveCanceled = true)
        static void frame(RenderGuiEvent.Pre event) {
            HudSpace.newFrame();
        }

        @SubscribeEvent
        static void hideVanilla(RenderGuiLayerEvent.Pre event) {
            if (!VitalsHud.active()) return;
            ResourceLocation name = event.getName();
            // AppleSkin draws its hunger and health previews over vanilla's rows, which are gone (the System's
            // hunger bar shows what the food in hand would give itself)
            if (REPLACED.contains(name) || name.getNamespace().equals("appleskin")) event.setCanceled(true);
        }

        @SubscribeEvent
        static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            VitalsHud.reset();
            EffectsHud.reset();
        }
    }

    private HudClientEvents() {}
}
