package com.beyondsolo.client.penalty;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.penalty.PenaltyZone;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;

/** Client wiring for the Penalty Zone: the overlay, the zone's sky and haze, and its edge in the world. */
public final class PenaltyClientEvents {
    public static final String LAYER = "penalty";

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        static void layers(RegisterGuiLayersEvent event) {
            event.registerAboveAll(BeyondSolo.id(LAYER), PenaltyOverlay::render);
        }

        /** The zone: no clouds or vanilla sky; the red fog is the sky, with the eclipse drawn over it. */
        @SubscribeEvent
        static void effects(RegisterDimensionSpecialEffectsEvent event) {
            event.register(BeyondSolo.id("penalty_zone"), new DimensionSpecialEffects(Float.NaN, true, DimensionSpecialEffects.SkyType.NONE, false, false) {
                @Override
                public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
                    return fogColor;
                }

                @Override
                public boolean isFoggyAt(int x, int y) {
                    return false;
                }

                @Override
                public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera,
                                         Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
                    PenaltyWorldFx.sky(modelViewMatrix, ticks + partialTick);
                    return true;
                }

                @Override
                public boolean renderClouds(ClientLevel level, int ticks, float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack,
                                            double camX, double camY, double camZ, Matrix4f modelViewMatrix, Matrix4f projectionMatrix) {
                    return true;
                }
            });
        }
    }

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        private static boolean inZone() {
            Minecraft mc = Minecraft.getInstance();
            return mc.level != null && mc.level.dimension().equals(PenaltyZone.DIMENSION);
        }

        /** A dark red haze: the sky itself is this colour. */
        @SubscribeEvent
        static void fogColor(ViewportEvent.ComputeFogColor event) {
            if (!inZone()) return;
            event.setRed(0.34f);
            event.setGreen(0.06f);
            event.setBlue(0.05f);
        }

        @SubscribeEvent
        static void fog(ViewportEvent.RenderFog event) {
            if (!inZone() || event.getType() != net.minecraft.world.level.material.FogType.NONE) return;
            event.setNearPlaneDistance(14f);
            event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), 96f));
            event.setCanceled(true);
        }

        @SubscribeEvent
        static void renderLevel(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
            PenaltyWorldFx.render(event.getPoseStack(), event.getCamera(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
        }

        @SubscribeEvent
        static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            PenaltyClient.clear();
        }
    }

    private PenaltyClientEvents() {}
}
