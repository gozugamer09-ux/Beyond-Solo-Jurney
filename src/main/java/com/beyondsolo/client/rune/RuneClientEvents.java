package com.beyondsolo.client.rune;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.rune.Engraving;
import com.beyondsolo.rune.RuneModule;
import com.beyondsolo.rune.Runes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Client wiring for runes: the stone's looks (which stone, glyph and cracks: item properties read by the models from
 * {@code tools/gen_rune_assets.py}; the glyph and the cracks glow in the rune's colour), the raised stone in first
 * person, the world effects, the on-screen absorb and the body poses.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class RuneClientEvents {
    /** The stone's look for its model: grade and glyph in one number ({@code (grade * glyphs + glyph + 0.5) / 100}). */
    public static final net.minecraft.resources.ResourceLocation LOOK = BeyondSolo.id("rune_look");
    /** How far a held stone has cracked (0..1 of the hold). */
    public static final net.minecraft.resources.ResourceLocation CRACK = BeyondSolo.id("crack");

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
        event.enqueueWork(() -> {
            RuneClient.setup();
            ItemProperties.register(RuneModule.RUNE_STONE.get(), LOOK, (stack, level, entity, seed) ->
                    (Runes.gradeOf(stack).tier() * Runes.GLYPHS + Runes.glyphOf(stack) + 0.5f) / 100f);
            ItemProperties.register(RuneModule.RUNE_STONE.get(), CRACK, (stack, level, entity, seed) ->
                    entity != null && entity.isUsingItem() && entity.getUseItem() == stack
                            ? Mth.clamp(entity.getTicksUsingItem() / (float) Runes.USE_TICKS, 0, 1) : 0);
        });
    }

    @SubscribeEvent
    static void colors(RegisterColorHandlersEvent.Item event) {
        // layer 1 is the glyph's glow, layer 3 the light in the cracks; the rest keeps its own colours
        event.register((stack, tint) -> tint == 1 || tint == 3 ? 0xFF000000 | Runes.colorOf(stack) : 0xFFFFFFFF, RuneModule.RUNE_STONE.get());
        for (Engraving e : Engraving.values()) {
            event.register((stack, tint) -> tint == 1 ? 0xFF000000 | e.color : 0xFFFFFFFF, RuneModule.ENGRAVINGS.get(e).get());
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
