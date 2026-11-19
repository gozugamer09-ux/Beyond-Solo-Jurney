package com.beyondsolo.client.story;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.story.VisionEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
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
import org.jetbrains.annotations.Nullable;

/**
 * A vision a story scene shows ({@link VisionEntity}): Morvath, the King of Night, a head taller than a man, in black
 * plate with a crown of iron spikes. Drawn see-through (the shadow he is made of), with his eyes and the cracks in his
 * armour burning violet at full brightness; he thins away as he breaks apart. Skins from
 * {@code tools/gen_story_assets.py}.
 */
public class VisionRenderer extends HumanoidMobRenderer<VisionEntity, PlayerModel<VisionEntity>> {
    private static final ResourceLocation KING = BeyondSolo.id("textures/entity/story/king_of_night.png");
    private static final ResourceLocation KING_GLOW = BeyondSolo.id("textures/entity/story/king_of_night_glow.png");
    /** How long it takes to break apart (ticks; the entity is gone after its own). */
    private static final float FADE = 60f;

    public VisionRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0f);
        addLayer(new Body(this));
        addLayer(new Glow(this));
    }

    @Override
    public ResourceLocation getTextureLocation(VisionEntity entity) {
        return KING;
    }

    /** The body itself is drawn see-through by {@link Body}. */
    @Override
    protected @Nullable RenderType getRenderType(VisionEntity entity, boolean visible, boolean translucent, boolean glowing) {
        return null;
    }

    @Override
    protected boolean shouldShowName(VisionEntity entity) {
        return false;
    }

    @Override
    protected void scale(VisionEntity entity, PoseStack pose, float partialTick) {
        pose.scale(1.15f, 1.15f, 1.15f);
    }

    /** How much of it is left (1: whole, 0: gone). */
    static float left(VisionEntity v, float partialTick) {
        if (v.fading() <= 0) return 1f;
        return 1f - Mth.clamp((v.fading() + partialTick) / FADE, 0f, 1f);
    }

    static class Body extends RenderLayer<VisionEntity, PlayerModel<VisionEntity>> {
        Body(RenderLayerParent<VisionEntity, PlayerModel<VisionEntity>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, VisionEntity v, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            float a = 0.86f * left(v, partialTick) * (0.94f + 0.06f * Mth.sin(ageInTicks * 0.15f));
            getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(KING)), light, OverlayTexture.NO_OVERLAY,
                    FastColor.ARGB32.color(Math.round(255 * a), 255, 255, 255));
        }
    }

    static class Glow extends RenderLayer<VisionEntity, PlayerModel<VisionEntity>> {
        Glow(RenderLayerParent<VisionEntity, PlayerModel<VisionEntity>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, VisionEntity v, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            float k = left(v, partialTick) * (0.8f + 0.2f * Mth.sin(ageInTicks * 0.2f));
            int c = Math.round(255 * k);
            getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(KING_GLOW)), 0xF000F0, OverlayTexture.NO_OVERLAY,
                    FastColor.ARGB32.color(255, c, c, c));
        }
    }
}
