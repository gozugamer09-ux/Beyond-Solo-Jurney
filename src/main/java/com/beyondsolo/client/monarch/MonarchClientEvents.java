package com.beyondsolo.client.monarch;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.ClientEvents;
import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.skill.ClientSkills;
import com.beyondsolo.client.skill.SkillOverlay;
import com.beyondsolo.monarch.Dominion;
import com.beyondsolo.monarch.MonarchModule;
import com.beyondsolo.network.MonarchActionPayload;
import com.beyondsolo.network.SkillMomentPayload;
import com.beyondsolo.skill.MonarchPowers;
import com.beyondsolo.skill.Skill;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Client wiring for the monarch path: the crown keys (Alt + the skill keys by default; a hunter sworn to the light uses
 * them for the light's powers instead), the crown bar, the monarch HUD and the coronation, the Arbiter Unchained's
 * renderer and the camera's shudder.
 */
public final class MonarchClientEvents {
    public static final ResourceLocation CROWN_BAR_LAYER = BeyondSolo.id("monarch_crown_bar");
    public static final ResourceLocation HUD_LAYER = BeyondSolo.id("monarch_hud");
    public static final ResourceLocation CORONATION_LAYER = BeyondSolo.id("monarch_coronation");
    /** Under every other layer: the dark of a Night monarch's eclipse falls on the world, not on the HUD. */
    public static final ResourceLocation ECLIPSE_LAYER = BeyondSolo.id("monarch_eclipse");

    /** The crown keys: the domain, dread, the crown form and the crown's authority. */
    public static final KeyMapping[] CROWN_KEYS = {
            key(1, GLFW.GLFW_KEY_Z), key(2, GLFW.GLFW_KEY_X), key(3, GLFW.GLFW_KEY_C), key(4, GLFW.GLFW_KEY_G)
    };

    private static KeyMapping key(int n, int glfw) {
        return new KeyMapping("key.beyondsolo.crown_" + n, KeyConflictContext.IN_GAME, KeyModifier.ALT, InputConstants.Type.KEYSYM, glfw,
                ClientEvents.KEY_CATEGORY);
    }

    /**
     * The crown key a monarch's or a ruler's power sits on (the domain or sanctum, dread or judgement, the crown or seraph
     * form, then any authority), or -1.
     */
    public static int crownKeyOf(Skill s) {
        if (s.ruler()) {
            return s == com.beyondsolo.skill.RulerPowers.SANCTUM ? 0 : s == com.beyondsolo.skill.RulerPowers.JUDGEMENT ? 1
                    : s == com.beyondsolo.skill.RulerPowers.SERAPH ? 2 : 3;
        }
        if (!s.monarch()) return -1;
        return s == MonarchPowers.DOMAIN ? 0 : s == MonarchPowers.DREAD ? 1 : s == MonarchPowers.CROWN ? 2 : 3;
    }

    /** Whether one of the monarch's layers (drawn even while the camera is away). */
    public static boolean ownLayer(ResourceLocation name) {
        return name.equals(HUD_LAYER) || name.equals(CORONATION_LAYER);
    }

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        static void keys(RegisterKeyMappingsEvent event) {
            for (KeyMapping k : CROWN_KEYS) event.register(k);
        }

        @SubscribeEvent
        static void layers(RegisterGuiLayersEvent event) {
            event.registerAboveAll(CROWN_BAR_LAYER, CrownBar::render);
            event.registerAboveAll(HUD_LAYER, MonarchHud::render);
            event.registerAboveAll(CORONATION_LAYER, CoronationOverlay::render);
            event.registerBelowAll(ECLIPSE_LAYER, MonarchHud::renderEclipse);
        }

        @SubscribeEvent
        static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(MonarchModule.THRONE_ARBITER.get(), ThroneArbiterRenderer::new);
        }
    }

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        @SubscribeEvent
        static void frame(RenderFrameEvent.Pre event) {
            CoronationOverlay.frame(event.getPartialTick());
        }

        @SubscribeEvent
        static void tick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            CoronationOverlay.tick();
            MonarchClient.tick();
            for (int i = 0; i < CROWN_KEYS.length; i++) {
                while (CROWN_KEYS[i].consumeClick()) {
                    if (mc.screen == null) press(i);
                }
            }
        }

        /** After the cutscene camera (which runs first), so the shudder adds on top. */
        @SubscribeEvent(priority = EventPriority.LOW)
        static void camera(ViewportEvent.ComputeCameraAngles event) {
            float[] s = MonarchHud.shakeAngles();
            if (s == null) return;
            event.setYaw(event.getYaw() + s[0]);
            event.setPitch(event.getPitch() + s[1]);
            event.setRoll(event.getRoll() + s[2]);
        }

        @SubscribeEvent
        static void changedWorld(ClientPlayerNetworkEvent.Clone event) {
            MonarchClient.clearWorld();
        }

        @SubscribeEvent
        static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            MonarchClient.clear();
        }
    }

    /** A crown key: what the client already knows (crowned, cooldown, mana) shows at once; then the server is asked. */
    static void press(int key) {
        Minecraft mc = Minecraft.getInstance();
        Dominion d = MonarchClient.dominion();
        // a hunter sworn to the light walks the other road: the same keys hold the light's powers
        if (d == null && com.beyondsolo.client.light.LightClient.sworn()) {
            com.beyondsolo.client.light.LightClient.press(key);
            return;
        }
        if (d == null || mc.player == null || !ClientSystemData.get().isAwakened()) return;
        Skill s = MonarchPowers.onKey(d, key);
        if (s == null || !ClientSkills.data().knows(s.path())) return;
        int level = ClientSkills.data().level(s.path());
        if (ClientSkills.cooldownFraction(s, 0) > 0) {
            SkillOverlay.refuse(s.path(), SkillMomentPayload.COOLDOWN);
            return;
        }
        if (!mc.player.isCreative() && ClientSystemData.get().mana() < com.beyondsolo.rune.Runes.manaCost(com.beyondsolo.client.rune.RuneClient.data(), s, level)
                * com.beyondsolo.combat.ModAttributes.manaFactor(mc.player)) {
            SkillOverlay.refuse(s.path(), SkillMomentPayload.NO_MANA);
            return;
        }
        CrownBar.casted(key);
        PacketDistributor.sendToServer(new MonarchActionPayload(MonarchActionPayload.POWER, key));
    }

    private MonarchClientEvents() {}
}
