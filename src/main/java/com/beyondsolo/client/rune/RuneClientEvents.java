package com.beyondsolo.client.rune;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.rune.Engraving;
import com.beyondsolo.rune.RuneModule;
import com.beyondsolo.rune.Runes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Client wiring for runes: the stone's looks (which stone, glyph and cracks, put together by {@link RuneStoneModel};
 * the glyph and the cracks glow in the rune's colour), the raised stone in first person, the world effects, the
 * on-screen absorb and the body poses.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class RuneClientEvents {
    /** First person: while absorbing, the stone is raised to the middle of the view, trembling and turning. */
    static final IClientItemExtensions HAND = new IClientItemExtensions() {
        @Override
        public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack, float partial, float equip, float swing) {
            if (!player.isUsingItem() || player.getUseItem() != stack) return false;
            int side = arm == HumanoidArm.RIGHT ? 1 : -1;
            float p = Mth.clamp((player.getTicksUsingItem() + partial) / Runes.USE_TICKS, 0, 1);
            float raise = Anim.outCubic(Mth.clamp(p * 3.5f, 0, 1));
            pose.translate(side * 0.56f * (1 - 0.8f * raise), -0.52f + 0.17f * raise, -0.72f + 0.04f * raise);
            float time = player.tickCount + partial;
            float shake = 0.014f * p * p;
            pose.translate(Mth.sin(time * 2.9f) * shake, Mth.cos(time * 3.7f) * shake, 0);
            pose.mulPose(Axis.YP.rotationDegrees(side * 12 * (1 - raise) + p * p * 70));
            pose.mulPose(Axis.ZP.rotationDegrees(side * -8 * raise));
            return true;
        }
    };

    @SubscribeEvent
    static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(RuneClient::setup);
    }

    @SubscribeEvent
    static void models(ModelEvent.RegisterAdditional event) {
        RuneStoneModel.parts().forEach(event::register);
    }

    @SubscribeEvent
    static void baked(ModelEvent.ModifyBakingResult event) {
        RuneStoneModel.install(event.getModels());
    }

    @SubscribeEvent
    static void colors(RegisterColorHandlersEvent.Item event) {
        // tint 1 is the glyph's halo, 2 its white-hot core, 3 the light in the cracks; the stone keeps its own colours
        event.register((stack, tint) -> switch (tint) {
            case 1, 3 -> 0xFF000000 | Runes.colorOf(stack);
            case 2 -> Gfx.mix(0xFF000000 | Runes.colorOf(stack), 0xFFFFFFFF, 0.65f);
            default -> 0xFFFFFFFF;
        }, RuneModule.RUNE_STONE.get());
        for (Engraving e : Engraving.values()) {
            event.register((stack, tint) -> switch (tint) {
                case 1 -> 0xFF000000 | e.color;
                case 2 -> Gfx.mix(0xFF000000 | e.color, 0xFFFFFFFF, 0.65f);
                default -> 0xFFFFFFFF;
            }, RuneModule.ENGRAVINGS.get(e).get());
        }
    }

    @SubscribeEvent
    static void extensions(RegisterClientExtensionsEvent event) {
        event.registerItem(HAND, RuneModule.RUNE_STONE.get());
    }

    @SubscribeEvent
    static void layers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(BeyondSolo.id("rune_moments"), RuneOverlay::render);
    }

    @SubscribeEvent
    static void renderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        RuneWorldFx.render(event.getPoseStack(), event.getCamera(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        RuneWorldFx.tick();
        RuneAnimator.tick();
    }

    @SubscribeEvent
    static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        RuneClient.clear();
        RuneWorldFx.clear();
        RuneAnimator.clear();
        RuneOverlay.reset();
    }

    private RuneClientEvents() {}
}
