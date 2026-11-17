package com.beyondsolo.client.combat;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.item.arsenal.Arsenal;
import com.beyondsolo.item.arsenal.ArsenalWeapon;
import com.beyondsolo.item.arsenal.WeaponKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Gauntlets (the "fist" weapons) are worn round the forearm and fist, not held like a picture of a fist.
 *
 * <p>The item model does most of it: for the two third-person hands it is a 3D gauntlet drawn in the arm's own space
 * ({@code tools/gen_gauntlets.py}), so wherever the game hangs a held item on an arm, the gauntlet wraps that arm and
 * follows it through every animation. This class adds what a model cannot:
 * <ul>
 *   <li>the fit: {@code beyondsolo:armored} picks the wider model when the wearer's chestplate covers the arm;</li>
 *   <li>the pair: a gauntlet in one hand with the other hand empty shows on both arms (a layer on the player);</li>
 *   <li>the stance: the arm hangs and swings like an empty one instead of holding the gauntlet out;</li>
 *   <li>first person: the game draws a held item without its arm, so the arm is drawn here with the gauntlet on it,
 *       and the second arm with it.</li>
 * </ul>
 */
public final class WornGauntlets {
    public static final ResourceLocation ARMORED = BeyondSolo.id("armored");

    private WornGauntlets() {}

    public static boolean isGauntlet(ItemStack stack) {
        return stack.getItem() instanceof ArsenalWeapon w && w.kind() == WeaponKind.FIST;
    }

    /** The gauntlet both arms wear: one held in either hand while the other hand is empty. Empty if there is none. */
    public static ItemStack pairOf(LivingEntity entity) {
        ItemStack main = entity.getMainHandItem();
        ItemStack off = entity.getOffhandItem();
        if (isGauntlet(main) && off.isEmpty()) return main;
        if (isGauntlet(off) && main.isEmpty()) return off;
        return ItemStack.EMPTY;
    }

    /** The arm of the empty hand, which wears the second gauntlet of a {@link #pairOf pair}. */
    public static HumanoidArm spareArm(LivingEntity entity) {
        return entity.getMainHandItem().isEmpty() ? entity.getMainArm() : entity.getMainArm().getOpposite();
    }

    /**
     * 1 when the wearer's chestplate covers the arm, so the wider model that clears it is used. Not while the
     * animation library draws the local player's arms for a first-person attack: armor is left out of that pass,
     * and the snug fit belongs on a bare arm.
     */
    private static float armored(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
        if (entity == null || !(entity.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof ArmorItem)) return 0f;
        return FirstPersonMode.isFirstPersonPass() && entity == Minecraft.getInstance().getCameraEntity() ? 0f : 1f;
    }

    private static List<Item> gauntlets() {
        List<Item> out = new ArrayList<>();
        for (var item : Arsenal.everything()) {
            if (item.get() instanceof ArsenalWeapon w && w.kind() == WeaponKind.FIST) out.add(item.get());
        }
        return out;
    }

    /** A worn gauntlet does not change how the arm is carried. */
    private static final IClientItemExtensions WORN = new IClientItemExtensions() {
        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            return HumanoidModel.ArmPose.EMPTY;
        }
    };

    /**
     * The second gauntlet of a pair, on the arm of the empty hand. It is a {@link PlayerItemInHandLayer} so that it
     * is drawn exactly like the first one: the animation library bends it with the elbow, moves it with its item
     * tracks and keeps it (item layers only) when it draws the player's own body for a first-person attack.
     *
     * <p>In that first-person pass Better Combat hides the arms unless its options say otherwise, which leaves a
     * punch as two hollow gauntlets in the air. An arm that wears a gauntlet is drawn here instead.</p>
     */
    static final class SecondGauntlet extends PlayerItemInHandLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        SecondGauntlet(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, ItemInHandRenderer items) {
            super(parent, items);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float headYaw, float headPitch) {
            ItemStack pair = pairOf(player);
            if (!pair.isEmpty()) {
                HumanoidArm arm = spareArm(player);
                ItemDisplayContext context = arm == HumanoidArm.RIGHT ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                        : ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
                pose.pushPose();
                renderArmWithItem(player, pair, context, arm, pose, buffers, light);
                pose.popPose();
            }
            if (FirstPersonMode.isFirstPersonPass() && player == Minecraft.getInstance().getCameraEntity() && !player.isInvisible()) {
                PlayerModel<AbstractClientPlayer> model = getParentModel();
                boolean both = !pair.isEmpty();
                boolean rightHanded = player.getMainArm() == HumanoidArm.RIGHT;
                boolean right = both || isGauntlet(rightHanded ? player.getMainHandItem() : player.getOffhandItem());
                boolean left = both || isGauntlet(rightHanded ? player.getOffhandItem() : player.getMainHandItem());
                ResourceLocation skin = player.getSkin().texture();
                if (right) {
                    drawHidden(model.rightArm, model.rightSleeve, player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE), skin, pose, buffers, light);
                }
                if (left) {
                    drawHidden(model.leftArm, model.leftSleeve, player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE), skin, pose, buffers, light);
                }
            }
        }

        private static void drawHidden(ModelPart arm, ModelPart sleeve, boolean sleeved, ResourceLocation skin, PoseStack pose,
                                       MultiBufferSource buffers, int light) {
            if (arm.visible) return;       // the options show the arms: the model has drawn it
            arm.visible = true;
            arm.render(pose, buffers.getBuffer(RenderType.entitySolid(skin)), light, OverlayTexture.NO_OVERLAY);
            arm.visible = false;
            if (!sleeved) return;
            sleeve.visible = true;
            sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucent(skin)), light, OverlayTexture.NO_OVERLAY);
            sleeve.visible = false;
        }
    }

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                for (Item item : gauntlets()) ItemProperties.register(item, ARMORED, WornGauntlets::armored);
            });
        }

        @SubscribeEvent
        static void extensions(RegisterClientExtensionsEvent event) {
            for (Item item : gauntlets()) event.registerItem(WORN, item);
        }

        @SubscribeEvent
        static void layers(EntityRenderersEvent.AddLayers event) {
            for (PlayerSkin.Model skin : event.getSkins()) {
                if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                    renderer.addLayer(new SecondGauntlet(renderer, event.getContext().getItemInHandRenderer()));
                }
            }
        }
    }

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        /** What the main hand showed this frame (the game swaps the shown item at the bottom of the re-equip dip). */
        private static ItemStack shownMain = ItemStack.EMPTY;
        private static float mainEquip;

        /**
         * First person. The game calls this once for each hand; a hand that holds a gauntlet, and the empty hand
         * beside it, is drawn here as the bare arm wearing the gauntlet instead of an item floating in view.
         */
        @SubscribeEvent
        static void hand(RenderHandEvent event) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null) return;
            boolean main = event.getHand() == InteractionHand.MAIN_HAND;
            ItemStack shown = event.getItemStack();
            float equip = event.getEquipProgress();
            if (main) {
                shownMain = shown;
                mainEquip = equip;
            }
            ItemStack worn;
            if (isGauntlet(shown)) {
                worn = shown;
            } else if (!shown.isEmpty()) {
                return;
            } else if (main) {
                worn = player.getOffhandItem();
            } else {
                worn = shownMain;
                equip = mainEquip;      // the pair comes up and goes down together
            }
            if (!isGauntlet(worn)) return;
            event.setCanceled(true);
            if (player.isScoping()) return;
            if (!(mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) return;
            HumanoidArm arm = main ? player.getMainArm() : player.getMainArm().getOpposite();
            PoseStack pose = event.getPoseStack();
            pose.pushPose();
            armTransform(pose, arm, equip, event.getSwingProgress());
            drawArm(renderer, player, arm, pose, event.getMultiBufferSource(), event.getPackedLight());
            drawGauntlet(mc, renderer, player, worn, arm, pose, event.getMultiBufferSource(), event.getPackedLight());
            pose.popPose();
        }

        /** Where the game puts the empty first-person arm ({@code ItemInHandRenderer.renderPlayerArm}). */
        private static void armTransform(PoseStack pose, HumanoidArm arm, float equip, float swing) {
            float side = arm == HumanoidArm.LEFT ? -1f : 1f;
            float root = Mth.sqrt(swing);
            float dx = -0.3f * Mth.sin(root * Mth.PI);
            float dy = 0.4f * Mth.sin(root * Mth.TWO_PI);
            float dz = -0.4f * Mth.sin(swing * Mth.PI);
            pose.translate(side * (dx + 0.64000005f), dy - 0.6f + equip * -0.6f, dz - 0.71999997f);
            pose.mulPose(Axis.YP.rotationDegrees(side * 45f));
            float twist = Mth.sin(swing * swing * Mth.PI);
            float turn = Mth.sin(root * Mth.PI);
            pose.mulPose(Axis.YP.rotationDegrees(side * turn * 70f));
            pose.mulPose(Axis.ZP.rotationDegrees(side * twist * -20f));
            pose.translate(side * -1f, 3.6f, 3.5f);
            pose.mulPose(Axis.ZP.rotationDegrees(side * 120f));
            pose.mulPose(Axis.XP.rotationDegrees(200f));
            pose.mulPose(Axis.YP.rotationDegrees(side * -135f));
            pose.translate(side * 5.6f, 0f, 0f);
        }

        private static void drawArm(PlayerRenderer renderer, LocalPlayer player, HumanoidArm arm, PoseStack pose,
                                    MultiBufferSource buffers, int light) {
            if (player.isInvisible()) {
                // no arm to draw, but the gauntlet still needs the arm where the game would have drawn it
                PlayerModel<AbstractClientPlayer> model = renderer.getModel();
                model.attackTime = 0f;
                model.crouching = false;
                model.swimAmount = 0f;
                model.setupAnim(player, 0f, 0f, 0f, 0f, 0f);
                (arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm).xRot = 0f;
            } else if (arm == HumanoidArm.RIGHT) {
                renderer.renderRightHand(pose, buffers, light, player);
            } else {
                renderer.renderLeftHand(pose, buffers, light, player);
            }
        }

        /** The gauntlet on the arm just drawn, hung the way the held-item layer hangs an item on an arm. */
        private static void drawGauntlet(Minecraft mc, PlayerRenderer renderer, LocalPlayer player, ItemStack stack, HumanoidArm arm,
                                         PoseStack pose, MultiBufferSource buffers, int light) {
            boolean left = arm == HumanoidArm.LEFT;
            ItemDisplayContext context = left ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
            pose.pushPose();
            renderer.getModel().translateToHand(arm, pose);
            pose.mulPose(Axis.XP.rotationDegrees(-90f));
            pose.mulPose(Axis.YP.rotationDegrees(180f));
            pose.translate((left ? -1 : 1) / 16f, 0.125f, -0.625f);
            mc.getItemRenderer().renderStatic(player, stack, context, left, pose, buffers, player.level(), light,
                    OverlayTexture.NO_OVERLAY, player.getId() + context.ordinal());
            pose.popPose();
        }
    }
}
