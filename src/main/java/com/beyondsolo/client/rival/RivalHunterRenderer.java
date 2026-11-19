package com.beyondsolo.client.rival;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.guild.GuildHunterRenderer;
import com.beyondsolo.guild.GuildHunterEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * A hunter of a rival party: a guild's hunter in every way ({@link GuildHunterRenderer}: a person in their class's armour
 * with their class's weapon), with their guild's band on the left arm, over the armour, in the guild's colour. The band's
 * cloth comes from {@code tools/gen_rival_assets.py} in greys, tinted here.
 */
public class RivalHunterRenderer extends GuildHunterRenderer {
    static final ResourceLocation BAND = BeyondSolo.id("textures/entity/rival/guild_band.png");

    public RivalHunterRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        addLayer(new Band(this));
    }

    /** The band: a ring of cloth around the upper arm, standing just off a chestplate's sleeve. */
    static final class Band extends RenderLayer<GuildHunterEntity, PlayerModel<GuildHunterEntity>> {
        private final ModelPart band;

        Band(RenderLayerParent<GuildHunterEntity, PlayerModel<GuildHunterEntity>> parent) {
            super(parent);
            MeshDefinition mesh = new MeshDefinition();
            // the arm's own box is (-1, -2, -2, 4, 12, 4); a chestplate stands 1 off it
            mesh.getRoot().addOrReplaceChild("band", CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-1f, 0.6f, -2f, 4, 3, 4, new CubeDeformation(1.15f, 0.1f, 1.15f)), PartPose.ZERO);
            // UV space 32x16; the texture is drawn at twice that
            band = LayerDefinition.create(mesh, 32, 16).bakeRoot().getChild("band");
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, GuildHunterEntity e, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (e.isInvisible()) return;
            band.copyFrom(getParentModel().leftArm);
            band.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(BAND)), light,
                    LivingEntityRenderer.getOverlayCoords(e, 0f), 0xFF000000 | e.guildColor());
        }
    }
}
