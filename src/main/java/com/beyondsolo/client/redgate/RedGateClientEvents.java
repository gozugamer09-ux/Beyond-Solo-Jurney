package com.beyondsolo.client.redgate;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.redgate.RedWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Client wiring for red gates: the overlay, and how the Red Winter looks: an overworld sky that follows the inside
 * clock, snow always falling (a white-out in a blizzard), and a red cast on its nights.
 */
public final class RedGateClientEvents {
    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        /** After the gate's moments, so the warning draws over the black lifting after going through. */
        @SubscribeEvent(priority = EventPriority.LOW)
        static void layers(RegisterGuiLayersEvent event) {
            event.registerAboveAll(BeyondSolo.id("red_gates"), RedGateOverlay::render);
        }

        /** The Red Winter: an overworld sky and clouds (its clock is the red gate's own, see {@link RedGateClient#applyWorld}). */
        @SubscribeEvent
        static void effects(RegisterDimensionSpecialEffectsEvent event) {
            event.register(BeyondSolo.id("red_gate"), new DimensionSpecialEffects(192f, true, DimensionSpecialEffects.SkyType.NORMAL, false, false) {
                @Override
                public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
                    return fogColor.multiply(brightness * 0.94f + 0.06f, brightness * 0.94f + 0.06f, brightness * 0.91f + 0.09f);
                }

                @Override
                public boolean isFoggyAt(int x, int y) {
                    return false;
                }
            });
        }
    }

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        private static boolean inRed() {
            Minecraft mc = Minecraft.getInstance();
            return mc.level != null && mc.level.dimension().equals(RedWorld.DIMENSION);
        }

        @SubscribeEvent
        static void tick(ClientTickEvent.Post event) {
            RedGateClient.tick();
            RedGateClient.applyWorld(0);
        }

        /** Every frame too, so the sky moves smoothly at the inside clock's pace. */
        @SubscribeEvent
        static void frame(RenderFrameEvent.Pre event) {
            RedGateClient.frame();
            RedGateClient.applyWorld(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        }

        /** A blizzard whites the air out (greys it at night); the nights have a red cast. */
        @SubscribeEvent(priority = EventPriority.HIGH)
        static void fogColor(ViewportEvent.ComputeFogColor event) {
            if (!inRed() || !RedGateClient.inside()) return;
            float partial = (float) event.getPartialTick();
            float storm = RedGateClient.storm();
            float n = RedGateClient.night(partial);
            float r = event.getRed(), g = event.getGreen(), b = event.getBlue();
            float k = storm * 0.85f;
            r += (Mth.lerp(n, 0.80f, 0.19f) - r) * k;
            g += (Mth.lerp(n, 0.84f, 0.21f) - g) * k;
            b += (Mth.lerp(n, 0.88f, 0.25f) - b) * k;
            float red = n * 0.42f * (1 - 0.6f * storm);
            r += (0.30f - r) * red;
            g += (0.035f - g) * red;
            b += (0.05f - b) * red;
            event.setRed(r);
            event.setGreen(g);
            event.setBlue(b);
        }

        /** The blizzard closes in: near the hunter the world goes white. */
        @SubscribeEvent
        static void fog(ViewportEvent.RenderFog event) {
            if (!inRed() || !RedGateClient.inside() || event.getType() != FogType.NONE) return;
            float storm = RedGateClient.storm();
            float n = RedGateClient.night((float) event.getPartialTick());
            float far = event.getFarPlaneDistance();
            // a light winter haze always, more at night; in a blizzard, thirty-odd blocks
            float haze = far * (1 - 0.18f * n);
            float target = Mth.lerp(storm, haze, 30f);
            event.setNearPlaneDistance(Mth.lerp(storm, event.getNearPlaneDistance(), 0f));
            event.setFarPlaneDistance(Math.min(far, target));
            event.setCanceled(true);
        }

        @SubscribeEvent
        static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            RedGateClient.clear();
            RedGateOverlay.reset();
        }
    }

    private RedGateClientEvents() {}
}
