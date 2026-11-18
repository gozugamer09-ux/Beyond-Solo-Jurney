package com.beyondsolo.client.worldboss;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.armor.ArmorModels;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.item.SystemArmorItem;
import com.beyondsolo.worldboss.MonarchArmor;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Monarch armor is made of its wearer's own aura: over the worn plate (drawn by the armor layer) its seams, crest and
 * edges burn in the wearer's colour, breathing, and a slow current of that light runs over the whole of it.
 */
public class MonarchAuraLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final ResourceLocation CURRENT = BeyondSolo.id("textures/models/armor/monarch_current.png");
    private static final Map<String, ResourceLocation> GLOW = new HashMap<>();

    public MonarchAuraLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    private static ResourceLocation glow(String set, boolean inner) {
        return GLOW.computeIfAbsent(set + (inner ? "_2" : "_1"), k -> BeyondSolo.id("textures/models/armor/" + set + "_aura_" + (inner ? 2 : 1) + ".png"));
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount, float partial,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) return;
        float time = (ageInTicks % 7200);
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!MonarchArmor.is(stack) || !(stack.getItem() instanceof SystemArmorItem item)) continue;
            String set = item.set().name;
            boolean inner = slot == EquipmentSlot.LEGS;
            HumanoidModel<LivingEntity> model = ArmorModels.model(set, inner);
            if (model == null) continue;
            ((HumanoidModel) getParentModel()).copyPropertiesTo(model);
            show(model, slot);
            int aura = MonarchArmor.aura(stack);
            float r = ((aura >> 16) & 255) / 255f, g = ((aura >> 8) & 255) / 255f, b = (aura & 255) / 255f;
            // the light breathes, a little out of step piece to piece
            float breath = 0.78f + 0.22f * Mth.sin(time * 0.11f + slot.ordinal() * 0.9f);
            model.renderToBuffer(pose, buffers.getBuffer(ModRenderTypes.armorGlow(glow(set, inner))), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    FastColor.ARGB32.colorFromFloat(1f, r * breath, g * breath, b * breath));
            float flow = (time * 0.006f) % 1f;
            model.renderToBuffer(pose, buffers.getBuffer(ModRenderTypes.armorCurrent(CURRENT, flow, flow * 0.6f)), LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, FastColor.ARGB32.colorFromFloat(1f, r * 0.45f, g * 0.45f, b * 0.45f));
        }
    }

    /** The parts a piece covers, as the armor layer shows them. */
    private static void show(HumanoidModel<?> model, EquipmentSlot slot) {
        model.setAllVisible(false);
        switch (slot) {
            case HEAD -> {
                model.head.visible = true;
                model.hat.visible = true;
            }
            case CHEST -> {
                model.body.visible = true;
                model.rightArm.visible = true;
                model.leftArm.visible = true;
            }
            case LEGS -> {
                model.body.visible = true;
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            case FEET -> {
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            default -> {
            }
        }
    }
}
