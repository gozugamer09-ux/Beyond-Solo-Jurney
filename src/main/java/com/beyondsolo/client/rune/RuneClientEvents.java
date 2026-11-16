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
    /**
     * First person: while absorbing, the stone is lifted from the hand to the middle of the view, a little low, and
     * turned to face the eye, where it trembles and swells with light until it shatters. The item's own first-person
     * transform ({@code neoforge:item/default}: turned 90 degrees, tilted 25, scaled 0.68) still follows this one, so
     * it is undone step by step as the stone rises.
     */
    static final IClientItemExtensions HAND = new IClientItemExtensions() {
        @Override
        public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack, float partial, float equip, float swing) {
            if (!player.isUsingItem() || player.getUseItem() != stack) return false;
            int side = arm == HumanoidArm.RIGHT ? 1 : -1;
            float p = Mth.clamp((player.getTicksUsingItem() + partial) / Runes.USE_TICKS, 0, 1);
            float r = Anim.inOutCubic(Mth.clamp(p * 3f, 0, 1));
            float time = player.tickCount + partial;
            // where it is held: from the hand (vanilla's held-item spot) to the middle of the view
            float x = Mth.lerp(r, side * 0.56f, 0);
            float y = Mth.lerp(r, -0.52f, -0.255f) + 0.012f * Mth.sin(time * 0.21f) * r;
            float z = Mth.lerp(r, -0.72f, -0.8f) + 0.06f * p * p * p;
            float shake = 0.006f * p * p;
            pose.translate(x + Mth.sin(time * 2.9f) * shake, y + Mth.cos(time * 3.7f) * shake, z);
            // a slow sway, then a tremble as it cracks
            float tremble = 5f * p * p;
            pose.mulPose(Axis.YP.rotationDegrees(side * 9f * Mth.sin(time * 0.17f) * r + Mth.sin(time * 3.3f) * tremble));
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.cos(time * 2.6f) * tremble * 0.6f));
            // about a third of the view high, swelling a little before it bursts
            float size = Mth.lerp(r, 1f, 0.33f * (1 + 0.1f * p * p * p) / ITEM_SCALE);
            pose.scale(size, size, size);
            // undo the item's own first-person turn, tilt and offset as it rises
            pose.mulPose(Axis.ZP.rotationDegrees(-side * ITEM_TILT * r));
            pose.mulPose(Axis.YP.rotationDegrees(side * ITEM_TURN * r));
            pose.translate(-side * ITEM_OFFSET[0] * r, -ITEM_OFFSET[1] * r, -ITEM_OFFSET[2] * r);
            return true;
        }
    };
    /** The first-person transform of {@code neoforge:item/default}, which the stone's parts inherit. */
    private static final float ITEM_TURN = 90, ITEM_TILT = 25, ITEM_SCALE = 0.68f;
    private static final float[] ITEM_OFFSET = {1.13f / 16, 3.2f / 16, 1.13f / 16};

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
