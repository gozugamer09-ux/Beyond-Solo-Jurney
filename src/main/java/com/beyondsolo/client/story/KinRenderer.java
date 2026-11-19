package com.beyondsolo.client.story;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.story.KinEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * The hunter's mother: a woman in a player's (slim) shape, in a long dress and a knitted shawl. Asleep her eyes are
 * shut; the vanilla sleeping pose lays her in her bed. Sitting up she sits with her legs along the mattress. Skins from
 * {@code tools/gen_story_assets.py}; her name plate is drawn by {@link KinMarkers}.
 */
public class KinRenderer extends HumanoidMobRenderer<KinEntity, KinRenderer.Model> {
    private static final ResourceLocation[] AWAKE = new ResourceLocation[KinEntity.LOOKS];
    private static final ResourceLocation[] ASLEEP = new ResourceLocation[KinEntity.LOOKS];

    static {
        for (int i = 0; i < KinEntity.LOOKS; i++) {
            AWAKE[i] = BeyondSolo.id("textures/entity/story/mother_" + i + ".png");
            ASLEEP[i] = BeyondSolo.id("textures/entity/story/mother_" + i + "_asleep.png");
        }
    }

    public KinRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model(ctx.bakeLayer(ModelLayers.PLAYER_SLIM)), 0.45f);
    }

    @Override
    protected boolean shouldShowName(KinEntity entity) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(KinEntity entity) {
        int look = Math.floorMod(entity.look(), KinEntity.LOOKS);
        return entity.state() == KinEntity.SLEEPING ? ASLEEP[look] : AWAKE[look];
    }

    /** The slim player model, sitting (legs out in front) while she sits up in bed. */
    public static class Model extends PlayerModel<KinEntity> {
        public Model(ModelPart root) {
            super(root, true);
        }

        @Override
        public void setupAnim(KinEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            riding = entity.state() == KinEntity.SITTING;
            super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            if (riding) {
                // hands resting in her lap
                rightArm.xRot = -0.55f;
                leftArm.xRot = -0.55f;
                rightArm.yRot = 0.25f;
                leftArm.yRot = -0.25f;
                rightSleeve.copyFrom(rightArm);
                leftSleeve.copyFrom(leftArm);
            }
        }
    }
}
