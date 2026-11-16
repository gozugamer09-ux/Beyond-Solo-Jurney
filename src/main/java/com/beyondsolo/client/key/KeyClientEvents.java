package com.beyondsolo.client.key;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.screen.SystemTabs;
import com.beyondsolo.key.DungeonKeys;
import com.beyondsolo.key.KeyModule;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Client wiring for dungeon keys: the seal on a key's looks (item property {@code beyondsolo:seal}: 0 plain, 1 sealed
 * to a boss, 2 ascendant), the GATES page of the System window, the key held out and turned in first person, the
 * lock of light in the world, the run clock and the re-run prompt.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class KeyClientEvents {
    public static final ResourceLocation GATES_TAB = BeyondSolo.id("gates");
    public static final ResourceLocation SEAL = BeyondSolo.id("seal");

    /**
     * First person: while it turns, the key is lifted from the hand out in front, a little right of the middle, stood
     * up with its tip leaning away towards the lock ahead, and twisted about its shaft: a slow push, a click at each
     * notch, a last hard twist. The item's own first-person transform ({@code neoforge:item/default}: turned 90
     * degrees, tilted 25, scaled 0.68) still follows this one, so it is undone step by step as the key rises.
     */
    static final IClientItemExtensions HAND = new IClientItemExtensions() {
        @Override
        public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack, float partial, float equip, float swing) {
            if (!player.isUsingItem() || player.getUseItem() != stack) return false;
            int side = arm == HumanoidArm.RIGHT ? 1 : -1;
            float p = Mth.clamp((player.getTicksUsingItem() + partial) / DungeonKeys.USE_TICKS, 0, 1);
            float r = Anim.inOutCubic(Mth.clamp(p * 4f, 0, 1));
            float time = player.tickCount + partial;
            float turn = turned(p);
            // the shudder after each notch
            float shake = 0.004f + 0.01f * notchShock(p);
            float x = Mth.lerp(r, side * 0.56f, side * 0.22f) + Mth.sin(time * 2.9f) * shake;
            float y = Mth.lerp(r, -0.52f, -0.27f) + Mth.cos(time * 3.7f) * shake + 0.008f * Mth.sin(time * 0.21f) * r;
            float z = Mth.lerp(r, -0.72f, -0.92f) - 0.05f * p;
            pose.translate(x, y, z);
            // tip away, towards the lock
            pose.mulPose(Axis.XP.rotationDegrees(-52f * r));
            // the turn, about the shaft
            pose.mulPose(Axis.YP.rotationDegrees((-42f + 84f * turn) * r));
            // stand it up: the shaft runs corner to corner on the sprite
            pose.mulPose(Axis.ZP.rotationDegrees(45f * r));
            float size = Mth.lerp(r, 1f, 0.36f / ITEM_SCALE);
            pose.scale(size, size, size);
            // undo the item's own first-person turn, tilt and offset as it rises
            pose.mulPose(Axis.ZP.rotationDegrees(-side * ITEM_TILT * r));
            pose.mulPose(Axis.YP.rotationDegrees(side * ITEM_TURN * r));
            pose.translate(-side * ITEM_OFFSET[0] * r, -ITEM_OFFSET[1] * r, -ITEM_OFFSET[2] * r);
            return true;
        }
    };
    /** The first-person transform of {@code neoforge:item/default}, which the key's layers inherit. */
    private static final float ITEM_TURN = 90, ITEM_TILT = 25, ITEM_SCALE = 0.68f;
    private static final float[] ITEM_OFFSET = {1.13f / 16, 3.2f / 16, 1.13f / 16};

    /** How far the key has turned (0..1) at this point of the hold: two notches, then the last twist. */
    static float turned(float p) {
        float[] at = {0f, 1 / 3f, 2 / 3f, 0.94f};
        float[] to = {0f, 0.3f, 0.6f, 1f};
        for (int i = 0; i < 3; i++) {
            if (p < at[i + 1]) {
                float k = (p - at[i]) / (at[i + 1] - at[i]);
                float push = 0.25f * k;
                float click = k > 0.82f ? Anim.outBack((k - 0.82f) / 0.18f) * 0.75f : 0;
                return Mth.lerp(push + click, to[i], to[i + 1]);
            }
        }
        return 1f;
    }

    /** 1 right as a notch gives, falling to 0 over a few ticks. */
    private static float notchShock(float p) {
        float best = 0;
        for (float at : new float[]{1 / 3f, 2 / 3f, 0.94f}) {
            float since = (p - at) * DungeonKeys.USE_TICKS;
            if (since >= 0 && since < 5) best = Math.max(best, 1 - since / 5f);
        }
        return best;
    }

    @SubscribeEvent
    static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            KeyClient.setup();
            SystemTabs.register(GATES_TAB, 105, GatesTab::new);
            for (DeferredHolder<Item, ?> key : KeyModule.KEYS) {
                ItemProperties.register(key.get(), SEAL, (stack, level, entity, seed) -> DungeonKeys.style(stack));
            }
        });
    }

    @SubscribeEvent
    static void extensions(RegisterClientExtensionsEvent event) {
        for (DeferredHolder<Item, ?> key : KeyModule.KEYS) event.registerItem(HAND, key.get());
    }

    @SubscribeEvent
    static void layers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(BeyondSolo.id("key_run"), RunHud::render);
    }

    @SubscribeEvent
    static void renderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        KeyWorldFx.render(event.getPoseStack(), event.getCamera(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        KeyClient.tick();
        KeyWorldFx.tick();
        KeyAnimator.tick();
    }

    @SubscribeEvent
    static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        KeyClient.clear();
        KeyWorldFx.clear();
        KeyAnimator.clear();
    }

    private KeyClientEvents() {}
}
