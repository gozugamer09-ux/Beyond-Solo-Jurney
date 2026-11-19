package com.beyondsolo.client.story;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.story.FatherEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * The hunter's father: a man of about fifty in the wide player shape, in the long hunter's coat he walked into a gate
 * in ten years ago, with the light he carries now showing through its seams and in his eyes (a glow layer drawn at
 * full brightness, stronger while he rises into the sky). Kneeling, he is crouched with his head bowed. Skins from
 * {@code tools/gen_story_assets.py}; his name plate is drawn by {@link KinMarkers}.
 */
public class FatherRenderer extends HumanoidMobRenderer<FatherEntity, FatherRenderer.Model> {
    private static final ResourceLocation[] SKIN = new ResourceLocation[FatherEntity.LOOKS];
    private static final ResourceLocation[] GLOW = new ResourceLocation[FatherEntity.LOOKS];

    static {
        for (int i = 0; i < FatherEntity.LOOKS; i++) {
            SKIN[i] = BeyondSolo.id("textures/entity/story/father_" + i + ".png");
            GLOW[i] = BeyondSolo.id("textures/entity/story/father_" + i + "_glow.png");
        }
    }

    public FatherRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        addLayer(new Glow(this));
    }

    @Override
    protected boolean shouldShowName(FatherEntity entity) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(FatherEntity entity) {
        return SKIN[Math.floorMod(entity.look(), FatherEntity.LOOKS)];
    }

    /** The wide player model; down on one knee, head bowed, arms hanging. */
    public static class Model extends PlayerModel<FatherEntity> {
        public Model(ModelPart root) {
            super(root, false);
        }

        @Override
        public void setupAnim(FatherEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            boolean kneeling = entity.state() == FatherEntity.KNEELING;
            crouching = kneeling;
            super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            if (kneeling) {
                head.xRot = Math.max(head.xRot, 0.45f);
                hat.copyFrom(head);
                rightLeg.xRot = -1.1f;
                leftLeg.xRot = 0.55f;
                rightArm.xRot = 0.15f;
                leftArm.xRot = -0.2f;
                rightPants.copyFrom(rightLeg);
                leftPants.copyFrom(leftLeg);
                rightSleeve.copyFrom(rightArm);
                leftSleeve.copyFrom(leftArm);
            } else if (entity.state() == FatherEntity.RISING) {
                // arms a little open, as if letting go
                rightArm.zRot = 0.35f + Mth.sin(ageInTicks * 0.05f) * 0.03f;
                leftArm.zRot = -0.35f - Mth.sin(ageInTicks * 0.05f) * 0.03f;
                rightSleeve.copyFrom(rightArm);
                leftSleeve.copyFrom(leftArm);
            }
        }
    }

    /** The light in him: seams of his coat and his eyes, at full brightness. */
    static class Glow extends RenderLayer<FatherEntity, Model> {
        Glow(RenderLayerParent<FatherEntity, Model> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, FatherEntity f, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (f.isInvisible()) return;
            float pulse = 0.78f + 0.22f * Mth.sin(ageInTicks * 0.12f);
            if (f.state() == FatherEntity.RISING) pulse = 1f;
            int c = Math.round(255 * pulse);
            ResourceLocation tex = GLOW[Math.floorMod(f.look(), FatherEntity.LOOKS)];
            getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(tex)), 0xF000F0, OverlayTexture.NO_OVERLAY,
                    FastColor.ARGB32.color(255, c, c, c));
        }
    }
}
