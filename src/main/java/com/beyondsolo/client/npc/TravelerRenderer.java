package com.beyondsolo.client.npc;

import com.beyondsolo.npc.TravelerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/** The escort traveller: a villager body in plains clothes with a cartographer's travelling kit. */
public class TravelerRenderer extends MobRenderer<TravelerEntity, VillagerModel<TravelerEntity>> {
    private static final ResourceLocation BASE = ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png");
    private static final ResourceLocation CLOTHES = ResourceLocation.withDefaultNamespace("textures/entity/villager/type/plains.png");
    private static final ResourceLocation KIT = ResourceLocation.withDefaultNamespace("textures/entity/villager/profession/cartographer.png");

    public TravelerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new VillagerModel<>(ctx.bakeLayer(ModelLayers.VILLAGER)), 0.5f);
        addLayer(new CustomHeadLayer<>(this, ctx.getModelSet(), ctx.getItemInHandRenderer()));
        addLayer(new Overlay(this, CLOTHES));
        addLayer(new Overlay(this, KIT));
    }

    /** The name plate is drawn by {@link NpcMarkers}, so the vanilla tag would double it. */
    @Override
    protected boolean shouldShowName(TravelerEntity entity) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(TravelerEntity entity) {
        return BASE;
    }

    private static final class Overlay extends RenderLayer<TravelerEntity, VillagerModel<TravelerEntity>> {
        private final ResourceLocation texture;

        Overlay(RenderLayerParent<TravelerEntity, VillagerModel<TravelerEntity>> parent, ResourceLocation texture) {
            super(parent);
            this.texture = texture;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, TravelerEntity entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.isInvisible()) return;
            renderColoredCutoutModel(getParentModel(), texture, pose, buffers, light, entity, -1);
        }
    }
}
