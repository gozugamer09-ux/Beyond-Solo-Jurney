package com.beyondsolo.client.gate;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.registry.ModBlockEntities;
import com.beyondsolo.registry.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Client wiring for gates: renderers, the overlay, camera shake, and how the gate dimension looks. */
public final class GateClientEvents {
    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(ModBlockEntities.GATE.get(), GateRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.REWARD_CACHE.get(), RewardCacheRenderer::new);
            event.registerEntityRenderer(ModEntities.GATE_BOSS.get(), GateBossRenderer::new);
        }

        @SubscribeEvent
        static void layers(RegisterGuiLayersEvent event) {
            event.registerAboveAll(BeyondSolo.id("gate_moments"), GateOverlay::render);
        }

        /** The gate dimension: no sky, no clouds, its own dark fog. */
        @SubscribeEvent
        static void effects(RegisterDimensionSpecialEffectsEvent event) {
            event.register(BeyondSolo.id("gate_depths"), new DimensionSpecialEffects(Float.NaN, false, DimensionSpecialEffects.SkyType.NONE, false, false) {
                @Override
                public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
                    return fogColor;
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
        @SubscribeEvent
        static void frame(RenderFrameEvent.Pre event) {
            GateOverlay.frame();
        }

        @SubscribeEvent
        static void camera(ViewportEvent.ComputeCameraAngles event) {
            float[] s = GateOverlay.shake();
            if (s[0] == 0 && s[1] == 0 && s[2] == 0) return;
            event.setYaw(event.getYaw() + s[0]);
            event.setPitch(event.getPitch() + s[1]);
            event.setRoll(event.getRoll() + s[2]);
        }

        /** Thick, close fog in a gate's cave. */
        @SubscribeEvent
        static void fog(ViewportEvent.RenderFog event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || !mc.level.dimension().equals(GateService.DIMENSION)) return;
            event.setNearPlaneDistance(2f);
            event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), 44f));
            event.setCanceled(true);
        }

        @SubscribeEvent
        static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            GateOverlay.reset();
        }
    }

    private GateClientEvents() {}
}
