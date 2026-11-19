package com.beyondsolo.client.rival;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.guild.GuildHunterRenderer;
import com.beyondsolo.guild.GuildHunterEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * A hired porter: someone in work clothes (one of {@link #LOOKS} people, skins from {@code tools/gen_rival_assets.py})
 * under a great pack on a wooden frame: a canvas sack with a flap and a pocket, a bedroll on top, straps over the
 * shoulders, a lantern swinging from one rail and a pot from the other. The lantern's glass glows in the dark.
 */
public class PorterRenderer extends GuildHunterRenderer {
    public static final int LOOKS = 3;
    private static final ResourceLocation[] SKINS = new ResourceLocation[LOOKS];
    static final ResourceLocation PACK = BeyondSolo.id("textures/entity/rival/porter_pack.png");
    static final ResourceLocation PACK_GLOW = BeyondSolo.id("textures/entity/rival/porter_pack_glow.png");

    static {
        for (int i = 0; i < LOOKS; i++) SKINS[i] = BeyondSolo.id("textures/entity/rival/porter_" + i + ".png");
    }

    public PorterRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        addLayer(new Pack(this));
    }

    @Override
    public ResourceLocation getTextureLocation(GuildHunterEntity entity) {
        return SKINS[Math.floorMod(entity.skin(), LOOKS)];
    }

    /**
     * The pack, in the body's space (x towards the porter's left, y down from the shoulders, z towards the back; the
     * body is the box (-4, 0, -2, 8, 12, 4)). Keep in sync with {@code PACK_BOXES} in {@code tools/gen_rival_assets.py},
     * which paints the 64x64 texture box by box.
     */
    static final class Pack extends RenderLayer<GuildHunterEntity, PlayerModel<GuildHunterEntity>> {
        private final ModelPart root;
        private final ModelPart lantern;
        private final ModelPart pot;

        Pack(RenderLayerParent<GuildHunterEntity, PlayerModel<GuildHunterEntity>> parent) {
            super(parent);
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition r = mesh.getRoot();
            r.addOrReplaceChild("sack", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5f, 0.5f, 2.2f, 9, 11, 5), PartPose.ZERO);
            r.addOrReplaceChild("flap", CubeListBuilder.create().texOffs(28, 0).addBox(-4f, 0.3f, 7.2f, 8, 5, 1), PartPose.ZERO);
            r.addOrReplaceChild("pocket", CubeListBuilder.create().texOffs(28, 6).addBox(-3f, 6.8f, 7.2f, 6, 4, 2), PartPose.ZERO);
            r.addOrReplaceChild("bedroll", CubeListBuilder.create().texOffs(0, 16).addBox(-5.5f, -2.4f, 4.8f, 11, 3, 3), PartPose.ZERO);
            r.addOrReplaceChild("rail_right", CubeListBuilder.create().texOffs(56, 0).addBox(-5.4f, -1.5f, 4.2f, 1, 14, 1), PartPose.ZERO);
            r.addOrReplaceChild("rail_left", CubeListBuilder.create().texOffs(56, 0).mirror().addBox(4.4f, -1.5f, 4.2f, 1, 14, 1), PartPose.ZERO);
            r.addOrReplaceChild("strap_right", CubeListBuilder.create().texOffs(0, 24).addBox(-3f, -0.4f, -2.6f, 1, 8, 1)
                    .texOffs(4, 24).addBox(-3f, -0.6f, -2.6f, 1, 1, 5), PartPose.ZERO);
            r.addOrReplaceChild("strap_left", CubeListBuilder.create().texOffs(0, 24).mirror().addBox(2f, -0.4f, -2.6f, 1, 8, 1)
                    .texOffs(4, 24).mirror().addBox(2f, -0.6f, -2.6f, 1, 1, 5), PartPose.ZERO);
            r.addOrReplaceChild("lantern", CubeListBuilder.create().texOffs(46, 0).addBox(-1f, 0f, -1f, 2, 4, 2), PartPose.offset(-6.4f, 1.2f, 5.2f));
            r.addOrReplaceChild("pot", CubeListBuilder.create().texOffs(28, 12).addBox(-1.5f, 0f, -1.5f, 3, 2, 3), PartPose.offset(7f, 4f, 5.2f));
            // UV space 64x64; the texture is drawn at twice that (128x128) for finer cloth and stitching
            root = LayerDefinition.create(mesh, 64, 64).bakeRoot();
            lantern = root.getChild("lantern");
            pot = root.getChild("pot");
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, GuildHunterEntity e, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (e.isInvisible()) return;
            root.copyFrom(getParentModel().body);
            // what hangs off the frame swings with the stride (and a little at rest)
            float sway = Mth.sin(ageInTicks * 0.08f) * 0.04f;
            lantern.zRot = Mth.sin(limbSwing * 0.6662f) * 0.45f * limbSwingAmount + sway;
            lantern.xRot = Mth.cos(limbSwing * 0.6662f) * 0.25f * limbSwingAmount;
            pot.zRot = -Mth.sin(limbSwing * 0.6662f + 0.8f) * 0.35f * limbSwingAmount - sway;
            pot.xRot = Mth.cos(limbSwing * 0.6662f + 0.8f) * 0.2f * limbSwingAmount;
            root.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(PACK)), light, LivingEntityRenderer.getOverlayCoords(e, 0f), -1);
            // the lantern's glass, at full brightness, breathing like a flame
            float flame = 0.82f + 0.12f * Mth.sin(ageInTicks * 0.31f) + 0.06f * Mth.sin(ageInTicks * 0.83f + 1.7f);
            int c = Math.round(255 * Mth.clamp(flame, 0f, 1f));
            root.render(pose, buffers.getBuffer(RenderType.eyes(PACK_GLOW)), 0xF000F0, OverlayTexture.NO_OVERLAY, FastColor.ARGB32.color(255, c, c, c));
        }
    }
}
