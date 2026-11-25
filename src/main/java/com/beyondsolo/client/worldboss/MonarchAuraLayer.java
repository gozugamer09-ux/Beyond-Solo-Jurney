package com.beyondsolo.client.worldboss;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.armor.ArmorModels;
import com.beyondsolo.client.fx.Aura;
import com.beyondsolo.client.fx.Outline;
import com.beyondsolo.client.monarch.MonarchClient;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.item.SystemArmorItem;
import com.beyondsolo.monarch.Dominion;
import com.beyondsolo.worldboss.MonarchArmor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Monarch armor is made of its wearer's own aura, and a monarch in crown form burns with it, the way the series draws
 * dark power: black plate whose edge burns. Over the worn plate (drawn by the armor layer) its seams breathe in the
 * wearer's colour. Then the figure's outline (the body and the plate on it, read off the models as they stand this
 * frame) burns: a hot rim, flame licking up off the shoulders and the helm's crest, ink wisps streaming back, more the
 * more pieces are worn. In crown form the aura erupts: tall flame, a crown of spikes on the head, burning eyes that
 * trail, each crown in its own element ({@link #crownLook}).
 */
public class MonarchAuraLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final Map<String, ResourceLocation> GLOW = new HashMap<>();
    /** Only its format matters: the outline keeps the shape of what is drawn into it and draws nothing. */
    private static final RenderType SHAPE = RenderType.entityCutoutNoCull(BeyondSolo.id("textures/effect/series/edge.png"));
    /** Its own outline: a figure in a shadow's stand-in can carry this layer while the shared outline is being filled. */
    private static final Outline OUTLINE = Outline.create();
    private static final Long2ObjectMap<Aura.Style> LOOKS = new Long2ObjectOpenHashMap<>();

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
        int pieces = 0, aura = MonarchArmor.DEFAULT_AURA;
        HumanoidModel<LivingEntity>[] worn = new HumanoidModel[SLOTS.length];
        for (int i = 0; i < SLOTS.length; i++) {
            EquipmentSlot slot = SLOTS[i];
            ItemStack stack = entity.getItemBySlot(slot);
            if (!MonarchArmor.is(stack) || !(stack.getItem() instanceof SystemArmorItem item)) continue;
            String set = item.set().name;
            boolean inner = slot == EquipmentSlot.LEGS;
            HumanoidModel<LivingEntity> model = ArmorModels.model(set, inner);
            if (model == null) continue;
            ((HumanoidModel) getParentModel()).copyPropertiesTo(model);
            show(model, slot);
            pieces++;
            aura = MonarchArmor.aura(stack);
            worn[i] = model;
            float r = ((aura >> 16) & 255) / 255f, g = ((aura >> 8) & 255) / 255f, b = (aura & 255) / 255f;
            // the light in the seams breathes, a little out of step piece to piece; the plate itself stays black
            float breath = 0.5f + 0.18f * Mth.sin(time * 0.11f + slot.ordinal() * 0.9f);
            model.renderToBuffer(pose, buffers.getBuffer(ModRenderTypes.armorGlow(glow(set, inner))), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    FastColor.ARGB32.colorFromFloat(1f, r * breath, g * breath, b * breath));
        }
        MonarchClient.Aura form = MonarchClient.formOf(entity.getId());
        float crown = form == null ? 0f : form.strength(MonarchClient.now(partial));
        if (pieces == 0 && crown <= 0.005f) return;

        // the outline: the body, and the plate over it
        OUTLINE.reset();
        VertexConsumer shape = OUTLINE.record().getBuffer(SHAPE);
        // an armor stand's own sticks and base plate are no part of the figure
        if (!(entity instanceof ArmorStand)) getParentModel().renderToBuffer(pose, shape, light, OverlayTexture.NO_OVERLAY, -1);
        for (HumanoidModel<LivingEntity> model : worn) {
            if (model != null) model.renderToBuffer(pose, shape, light, OverlayTexture.NO_OVERLAY, -1);
        }
        if (OUTLINE.quads() == 0) return;
        float size = Math.max(0.6f, entity.getBbHeight() / 1.9f);
        if (crown > 0.005f) {
            Aura.burnWorld(OUTLINE, buffers, crownLook(form.dominion(), form.color), entity, partial, ageInTicks, crown, size, light);
            eyes(pose, buffers, entity, partial, ageInTicks, form.color, crown);
        }
        if (pieces > 0 && crown < 0.995f) {
            Aura.burnWorld(OUTLINE, buffers, armorLook(pieces, aura), entity, partial, ageInTicks, 1 - crown, size, light);
        }
    }

    /**
     * Monarch armor's own burning: a rim from the first piece, flame from the second, rising off the shoulders and the
     * helm's crest and taller with every piece, ink wisps streaming off a whole regalia.
     */
    private static Aura.Style armorLook(int pieces, int hue) {
        long key = (long) pieces << 32 | (hue & 0xFFFFFFFFL);
        Aura.Style s = LOOKS.get(key);
        if (s == null) {
            s = new Aura.Style().hue(hue).rim(0.42f + 0.06f * pieces, 0.055f).seams(0.06f * pieces)
                    .flame(pieces >= 2 ? 0.16f + 0.07f * pieces : 0f).crest(1f + 0.15f * pieces).glow(0.4f)
                    .wisps(pieces >= 3 ? 0.35f * pieces : 0f, 0.65f);
            LOOKS.put(key, s);
        }
        return s;
    }

    /**
     * A crown form: the wearer's aura erupting. The Night crown burns black with a crown of spikes; Winter, Cinders and
     * Thunder burn in their element's own light (pale frost, fire that sheds black smoke, storm-white crackling at the
     * seams). The element's breath round them (mist, embers, sparks) is {@code MonarchClient}'s.
     */
    private static Aura.Style crownLook(Dominion d, int hue) {
        long key = (long) (16 + d.ordinal()) << 32 | (hue & 0xFFFFFFFFL);
        Aura.Style s = LOOKS.get(key);
        if (s == null) {
            s = switch (d) {
                case NIGHT -> new Aura.Style().hue(hue).rim(0.85f, 0.09f).seams(0.4f).flame(0.95f).crest(1.9f).crown(1f).glow(0.75f).wisps(2.4f, 1f);
                case WINTER -> new Aura.Style().hue(hue).light().rim(0.75f, 0.075f).seams(0.3f).flame(0.62f).crest(1.6f).crown(0.6f).glow(0.55f);
                case CINDERS -> new Aura.Style().hue(hue).light().rim(0.8f, 0.08f).seams(0.35f).flame(0.9f).crest(1.8f).crown(0.5f).glow(0.8f)
                        .wisps(1.8f, 1.1f);
                case THUNDER -> new Aura.Style().hue(hue).light().rim(0.9f, 0.075f).seams(0.6f).flame(0.55f).crest(1.5f).crown(0.4f).glow(0.6f);
            };
            LOOKS.put(key, s);
        }
        return s;
    }

    /** Two white-hot points before the face, trailing back, while the crown form burns. */
    private void eyes(PoseStack pose, MultiBufferSource buffers, T entity, float partial, float time, int hue, float k) {
        HumanoidModel<T> model = getParentModel();
        if (!model.head.visible) return;
        pose.pushPose();
        model.head.translateAndRotate(pose);
        Matrix4f m = pose.last().pose();
        // on the face (the head's front is -z in the model, its eyes four pixels down from its top)
        Vector3f l = m.transformPosition(new Vector3f(-2.2f / 16f, -4.2f / 16f, -4.6f / 16f), new Vector3f());
        Vector3f r = m.transformPosition(new Vector3f(2.2f / 16f, -4.2f / 16f, -4.6f / 16f), new Vector3f());
        pose.popPose();
        float yaw = Mth.rotLerp(partial, entity.yHeadRotO, entity.yHeadRot) * Mth.DEG_TO_RAD;
        Vec3 back = new Vec3(Mth.sin(yaw), 0, -Mth.cos(yaw));
        float flicker = 0.85f + 0.15f * Mth.sin(time * 0.9f + entity.getId());
        Aura.eyes(Aura.state(entity.getId()), buffers, new Vec3(l.x(), l.y(), l.z()), new Vec3(r.x(), r.y(), r.z()), back, hue, 0.05f,
                k * flicker, 0.34f, time);
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
