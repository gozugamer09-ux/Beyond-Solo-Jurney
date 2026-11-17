package com.beyondsolo.client.tower;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.screen.SystemTabs;
import com.beyondsolo.tower.TowerModule;
import com.beyondsolo.tower.TowerRun;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;

/**
 * Client wiring for the Crimson Citadel: the CITADEL page, the lord's renderer, the HUD, the light in the world, and
 * the citadel's own sky and haze (red-black, turning crimson under the blood moon).
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class TowerClientEvents {
    public static final ResourceLocation CITADEL_TAB = BeyondSolo.id("citadel");

    @SubscribeEvent
    static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> SystemTabs.register(CITADEL_TAB, 107, CitadelTab::new));
    }

    @SubscribeEvent
    static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(TowerModule.CITADEL_LORD.get(), CitadelLordRenderer::new);
    }

    @SubscribeEvent
    static void layers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(BeyondSolo.id("tower"), TowerHud::render);
    }

    /** The citadel: no clouds and no vanilla sky; its moon and haze are drawn over the fog's colour. */
    @SubscribeEvent
    static void effects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(TowerRun.DIMENSION.location(), new DimensionSpecialEffects(Float.NaN, true, DimensionSpecialEffects.SkyType.NONE, false, false) {
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
                TowerWorldFx.sky(modelViewMatrix, ticks + partialTick);
                return true;
            }

            @Override
            public boolean renderClouds(ClientLevel level, int ticks, float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack,
                                        double camX, double camY, double camZ, Matrix4f modelViewMatrix, Matrix4f projectionMatrix) {
                return true;
            }
        });
    }

    private static boolean inCitadel() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.dimension().equals(TowerRun.DIMENSION);
    }

    private static float moonShown;

    /** Red-black haze; under the blood moon, crimson. */
    @SubscribeEvent
    static void fogColor(ViewportEvent.ComputeFogColor event) {
        if (!inCitadel()) return;
        float target = TowerClient.bloodMoon() ? 1f : 0f;
        moonShown += (target - moonShown) * 0.02f;
        float m = moonShown;
        event.setRed(0.09f + 0.26f * m);
        event.setGreen(0.025f + 0.01f * m);
        event.setBlue(0.035f + 0.01f * m);
    }

    @SubscribeEvent
    static void fog(ViewportEvent.RenderFog event) {
        if (!inCitadel() || event.getType() != net.minecraft.world.level.material.FogType.NONE) return;
        event.setNearPlaneDistance(24f - 8f * moonShown);
        event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), 110f - 30f * moonShown));
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        TowerClient.tick();
    }

    @SubscribeEvent
    static void renderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        TowerWorldFx.render(event.getPoseStack(), event.getCamera(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    @SubscribeEvent
    static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        TowerClient.clear();
        TowerHud.reset();
        TowerWorldFx.reset();
        moonShown = 0;
    }

    private TowerClientEvents() {}
}
