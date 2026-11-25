package com.beyondsolo.client.association;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.scouting.ScoutEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * A great guild's scout: a person in a player's shape in a sharp suit (the skins come from
 * {@code tools/gen_scout_assets.py}), with their guild's band on the left arm in the guild's colour (the rival
 * parties' band, {@code tools/gen_rival_assets.py}). Their name and guild show over their head.
 */
public class ScoutRenderer extends HumanoidMobRenderer<ScoutEntity, PlayerModel<ScoutEntity>> {
    private static final ResourceLocation[] LOOKS = new ResourceLocation[ScoutEntity.LOOKS];
    static final ResourceLocation BAND = BeyondSolo.id("textures/entity/rival/guild_band.png");

    static {
        for (int i = 0; i < LOOKS.length; i++) LOOKS[i] = BeyondSolo.id("textures/entity/scouting/scout_" + i + ".png");
    }

    public ScoutRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        addLayer(new Band(this));
    }

    @Override
    protected boolean shouldShowName(ScoutEntity entity) {
        // over their head out in the world, not on the portrait in their window (the window writes it under it)
        return !AssociationWindow.drawingPortrait() && super.shouldShowName(entity);
    }

    @Override
    public ResourceLocation getTextureLocation(ScoutEntity entity) {
        return LOOKS[Math.floorMod(entity.look(), LOOKS.length)];
    }

    /** The guild's band: a ring of cloth around the upper arm, over the suit's sleeve. */
    static final class Band extends RenderLayer<ScoutEntity, PlayerModel<ScoutEntity>> {
        private final ModelPart band;

        Band(RenderLayerParent<ScoutEntity, PlayerModel<ScoutEntity>> parent) {
            super(parent);
            MeshDefinition mesh = new MeshDefinition();
            // the arm's own box is (-1, -2, -2, 4, 12, 4); a sleeve stands a quarter off it
            mesh.getRoot().addOrReplaceChild("band", CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-1f, 0.6f, -2f, 4, 3, 4, new CubeDeformation(0.42f, 0.1f, 0.42f)), PartPose.ZERO);
            band = LayerDefinition.create(mesh, 32, 16).bakeRoot().getChild("band");
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, ScoutEntity e, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (e.isInvisible()) return;
            band.copyFrom(getParentModel().leftArm);
            band.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(BAND)), light,
                    LivingEntityRenderer.getOverlayCoords(e, 0f), 0xFF000000 | e.guild().color);
        }
    }
}
