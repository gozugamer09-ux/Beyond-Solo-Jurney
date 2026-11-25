package com.beyondsolo.client.worldboss;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.armor.ArmorModels;
import com.beyondsolo.client.ruler.HeraldRenderer;
import com.beyondsolo.item.SystemArmorItem;
import com.beyondsolo.ruler.RulerModule;
import com.beyondsolo.worldboss.MonarchArmor;
import com.beyondsolo.worldboss.WorldBossKind;
import com.beyondsolo.worldboss.WorldBossModule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Client wiring of the great threats and the rulers of light: renderers, the HUD, the light in the world, and monarch
 * armor's look.
 */
public final class WorldBossClientEvents {
    /** Monarch armor is worn with its set's own model (the plate and its extra parts, see {@link ArmorModels}). */
    static final IClientItemExtensions MONARCH_ARMOR = new IClientItemExtensions() {
        @Override
        public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
            if (!(stack.getItem() instanceof SystemArmorItem item)) return original;
            HumanoidModel<LivingEntity> model = ArmorModels.model(item.set().name, slot == EquipmentSlot.LEGS);
            return model != null ? model : original;
        }
    };

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(WorldBossModule.TITAN.get(), ctx -> new ColossalBossRenderer<>(ctx, WorldBossKind.TITAN));
            event.registerEntityRenderer(WorldBossModule.BEHEMOTH.get(), ctx -> new ColossalBossRenderer<>(ctx, WorldBossKind.BEHEMOTH));
            event.registerEntityRenderer(WorldBossModule.STILLNESS.get(), ctx -> new ColossalBossRenderer<>(ctx, WorldBossKind.STILLNESS));
            event.registerEntityRenderer(WorldBossModule.HUNGER.get(), ctx -> new ColossalBossRenderer<>(ctx, WorldBossKind.HUNGER));
            event.registerEntityRenderer(WorldBossModule.STORMS.get(), ctx -> new ColossalBossRenderer<>(ctx, WorldBossKind.STORMS));
            event.registerEntityRenderer(WorldBossModule.PROJECTILE.get(), BossProjectileRenderer::new);
            event.registerEntityRenderer(RulerModule.VERDICT.get(), ctx -> new ColossalBossRenderer<>(ctx, WorldBossKind.VERDICT));
            event.registerEntityRenderer(RulerModule.WINGS.get(), ctx -> new ColossalBossRenderer<>(ctx, WorldBossKind.WINGS));
            event.registerEntityRenderer(RulerModule.HAND.get(), ctx -> new ColossalBossRenderer<>(ctx, WorldBossKind.HAND));
            event.registerEntityRenderer(RulerModule.HERALD.get(), HeraldRenderer::new);
        }

        @SubscribeEvent
        static void hud(RegisterGuiLayersEvent event) {
            event.registerAboveAll(BeyondSolo.id("world_boss"), WorldBossHud::render);
        }

        /** The aura over monarch armor, on players and on armor stands. */
        @SubscribeEvent
        static void layers(EntityRenderersEvent.AddLayers event) {
            for (PlayerSkin.Model skin : event.getSkins()) {
                EntityRenderer<?> r = event.getSkin(skin);
                if (r instanceof PlayerRenderer pr) pr.addLayer(new MonarchAuraLayer<>(pr));
            }
            if (event.getRenderer(EntityType.ARMOR_STAND) instanceof ArmorStandRenderer stand) stand.addLayer(new MonarchAuraLayer<>(stand));
        }

        /** A monarch piece's icon glows in the colour of its last wearer's aura. */
        @SubscribeEvent
        static void itemColors(RegisterColorHandlersEvent.Item event) {
            Item[] items = WorldBossModule.armor().stream().map(d -> (Item) d.get()).toArray(Item[]::new);
            event.register((stack, layer) -> layer == 1 ? 0xFF000000 | MonarchArmor.aura(stack) : -1, items);
        }

        @SubscribeEvent
        static void extensions(RegisterClientExtensionsEvent event) {
            Item[] items = WorldBossModule.armor().stream().map(d -> (Item) d.get()).toArray(Item[]::new);
            event.registerItem(MONARCH_ARMOR, items);
        }
    }

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        @SubscribeEvent
        static void renderLevel(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
            WorldBossWorldFx.render(event.getPoseStack(), event.getCamera(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
        }

        @SubscribeEvent
        static void tick(ClientTickEvent.Post event) {
            WorldBossClient.tick();
            WorldBossWorldFx.breathe();
        }

        @SubscribeEvent
        static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            WorldBossClient.clear();
            WorldBossWorldFx.clear();
            WorldBossHud.reset();
        }

        /** A monarch piece names the aura it holds: its wearer's own. */
        @SubscribeEvent
        static void tooltip(ItemTooltipEvent event) {
            ItemStack stack = event.getItemStack();
            if (!MonarchArmor.is(stack)) return;
            int aura = MonarchArmor.aura(stack);
            event.getToolTip().add(1, Component.literal("◈ ").append(Component.translatable("tooltip.beyondsolo.monarch_aura"))
                    .withStyle(s -> s.withColor(TextColor.fromRgb(aura)).withItalic(true)));
            if (event.getFlags().isAdvanced()) {
                event.getToolTip().add(Component.literal(String.format("#%06X", aura)).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    private WorldBossClientEvents() {}
}
