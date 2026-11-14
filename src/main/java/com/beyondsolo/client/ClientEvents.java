package com.beyondsolo.client;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.evaluation.EvaluationOverlay;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.hud.StatusHud;
import com.beyondsolo.client.notify.NotificationManager;
import com.beyondsolo.client.notify.NotificationRenderer;
import com.beyondsolo.client.render.EvaluationCrystalRenderer;
import com.beyondsolo.client.screen.DefaultTabs;
import com.beyondsolo.client.screen.SystemScreen;
import com.beyondsolo.client.screen.SystemTabs;
import com.beyondsolo.client.quest.ClientQuests;
import com.beyondsolo.client.quest.QuestLogTab;
import com.beyondsolo.client.quest.QuestOverlay;
import com.beyondsolo.client.quest.QuestTracker;
import com.beyondsolo.registry.ModBlockEntities;
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
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
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
    /** Accepts the newest quest offer. */
    public static final KeyMapping ACCEPT_QUEST = new KeyMapping("key.beyondsolo.accept_quest",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, KEY_CATEGORY);

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                DefaultTabs.register();
                SystemTabs.register(SystemTabs.QUESTS, 100, QuestLogTab::new);
            });
        }

        @SubscribeEvent
        static void keys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_SYSTEM);
            event.register(NOTIFICATION_MODE);
            event.register(DISMISS);
            event.register(ACCEPT_QUEST);
        }

        @SubscribeEvent
        static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(ModBlockEntities.EVALUATION_CRYSTAL.get(), EvaluationCrystalRenderer::new);
        }

        @SubscribeEvent
        static void layers(RegisterGuiLayersEvent event) {
            // over-head villager markers sit under everything else of ours
            event.registerAboveAll(BeyondSolo.id("npc_markers"), com.beyondsolo.client.npc.NpcMarkers::render);
            event.registerAboveAll(BeyondSolo.id("status_hud"), StatusHud::render);
            event.registerAboveAll(BeyondSolo.id("quest_tracker"), QuestTracker::render);
            event.registerAboveAll(BeyondSolo.id("notifications"), NotificationRenderer::renderHud);
            event.registerAboveAll(BeyondSolo.id("quest_moments"), QuestOverlay::render);
            event.registerAboveAll(BeyondSolo.id("evaluation"), EvaluationOverlay::render);
        }
    }

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        @SubscribeEvent
        static void frame(RenderFrameEvent.Pre event) {
            Anim.frame();
            EvaluationOverlay.frame(event.getPartialTick());
            NotificationManager.frame();
            QuestOverlay.frame();
        }

        @SubscribeEvent
        static void tick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            EvaluationOverlay.tick();
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
            while (ACCEPT_QUEST.consumeClick()) {
                if (!ClientQuests.acceptLatest()) {
                    mc.player.displayClientMessage(Component.translatable("message.beyondsolo.no_offer"), true);
                }
            }
        }

        @SubscribeEvent
        static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientSystemData.clear();
            NotificationManager.clearAll();
            NotificationRenderer.reset();
            StatusHud.reset();
            EvaluationOverlay.reset();
            ClientQuests.clear();
        }
    }

    /** What the hide key does right now: skips the evaluation reveal if it is playing, otherwise hides or recalls pop-ups. */
    public static void hideKeyPressed() {
        if (EvaluationOverlay.skip()) return;
        if (com.beyondsolo.client.job.JobRevealOverlay.skip()) return;
        if (QuestOverlay.skip()) return;
        if (com.beyondsolo.client.skill.SkillOverlay.skip()) return;
        if (com.beyondsolo.client.gate.GateOverlay.skip()) return;
        NotificationManager.toggleHidden();
    }

    private ClientEvents() {}
}
