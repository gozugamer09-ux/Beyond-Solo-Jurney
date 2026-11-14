package com.beyondsolo.client.combat;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.ClientSystemData;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Client wiring for combat: the HUD layer, world effects, and the collapse lock on attacks and rolls. */
public final class CombatClientEvents {
    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        static void layers(RegisterGuiLayersEvent event) {
            // Under the System pop-ups, above the vanilla HUD.
            event.registerAboveAll(BeyondSolo.id("combat_hud"), CombatHud::render);
        }
    }

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        @SubscribeEvent
        static void renderLevel(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
            WorldProjector.capture(event.getModelViewMatrix(), event.getProjectionMatrix(), event.getCamera().getPosition());
            CombatWorldFx.render(event.getPoseStack(), event.getCamera(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
        }

        @SubscribeEvent
        static void tickPre(ClientTickEvent.Pre event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || !ClientSystemData.get().isCollapsed()) return;
            // A collapsed player can't swing (Better Combat reads the attack key) or roll.
            swallow(mc.options.keyAttack);
            if (ModList.get().isLoaded("combat_roll")) RollKeys.swallow();
        }

        @SubscribeEvent
        static void tickPost(ClientTickEvent.Post event) {
            ClientCombatState.tick();
            CombatWorldFx.tick();
        }

        @SubscribeEvent
        static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientCombatState.clear();
            CombatWorldFx.clear();
            FloatingNumbers.clear();
            CombatHud.reset();
        }
    }

    static void swallow(KeyMapping key) {
        while (key.consumeClick()) {
            // drop the press
        }
        key.setDown(false);
    }

    /** Kept apart so Combat Roll classes are only touched when it is installed. */
    static final class RollKeys {
        static void swallow() {
            if (net.combat_roll.client.Keybindings.roll != null) CombatClientEvents.swallow(net.combat_roll.client.Keybindings.roll);
        }
    }

    private CombatClientEvents() {}
}
