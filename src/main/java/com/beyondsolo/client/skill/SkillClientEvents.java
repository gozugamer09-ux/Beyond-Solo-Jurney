package com.beyondsolo.client.skill;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.ClientEvents;
import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.screen.SystemTabs;
import com.beyondsolo.network.SkillActionPayload;
import com.beyondsolo.network.SkillMomentPayload;
import com.beyondsolo.skill.Skill;
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
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Client wiring for skills: the four skill keys, the Skills tab, HUD layers and world effects. */
public final class SkillClientEvents {
    public static final ResourceLocation SKILLS_TAB = BeyondSolo.id("skills");

    public static final KeyMapping[] SLOT_KEYS = {
            key(1, GLFW.GLFW_KEY_Z), key(2, GLFW.GLFW_KEY_X), key(3, GLFW.GLFW_KEY_C), key(4, GLFW.GLFW_KEY_G)
    };

    private static KeyMapping key(int n, int glfw) {
        return new KeyMapping("key.beyondsolo.skill_" + n, InputConstants.Type.KEYSYM, glfw, ClientEvents.KEY_CATEGORY);
    }

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> SystemTabs.register(SKILLS_TAB, 50, SkillsTab::new));
        }

        @SubscribeEvent
        static void keys(RegisterKeyMappingsEvent event) {
            for (KeyMapping k : SLOT_KEYS) event.register(k);
        }

        @SubscribeEvent
        static void layers(RegisterGuiLayersEvent event) {
            event.registerAboveAll(BeyondSolo.id("skill_gaze"), GazeHud::render);
            event.registerAboveAll(BeyondSolo.id("skill_bar"), SkillBar::render);
            event.registerAboveAll(BeyondSolo.id("skill_moments"), SkillOverlay::render);
        }
    }

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        @SubscribeEvent
        static void frame(RenderFrameEvent.Pre event) {
            SkillOverlay.frame();
        }

        @SubscribeEvent
        static void renderLevel(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
            SkillWorldFx.render(event.getPoseStack(), event.getCamera(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
        }

        @SubscribeEvent
        static void tick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            SkillWorldFx.tick();
            for (int i = 0; i < SLOT_KEYS.length; i++) {
                while (SLOT_KEYS[i].consumeClick()) {
                    if (mc.screen == null) pressSlot(i);
                }
            }
        }

        @SubscribeEvent
        static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientSkills.clear();
            SkillOverlay.reset();
            SkillWorldFx.clear();
            SkillBar.reset();
        }
    }

    /** A skill key: checks what the client already knows (cooldown, mana) so a refusal shows at once, then asks the server. */
    public static void pressSlot(int slot) {
        Minecraft mc = Minecraft.getInstance();
        Skill s = ClientSkills.slot(slot);
        if (s == null || mc.player == null || !ClientSystemData.get().isAwakened()) return;
        int level = ClientSkills.data().level(s.path());
        if (ClientSkills.cooldownFraction(s, 0) > 0) {
            SkillOverlay.refuse(s.path(), SkillMomentPayload.COOLDOWN);
            return;
        }
        if (!mc.player.isCreative() && ClientSystemData.get().mana() < s.manaCost(level)) {
            SkillOverlay.refuse(s.path(), SkillMomentPayload.NO_MANA);
            return;
        }
        SkillBar.casted(slot);
        PacketDistributor.sendToServer(new SkillActionPayload(SkillActionPayload.CAST, slot, ""));
    }

    private SkillClientEvents() {}
}
