package com.beyondsolo.client.home;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.home.TrainingDummy;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * A training dummy (made by tools/gen_home_assets.py): its model swaying on its post and rocking under blows, in its
 * armour if it is the armoured one. What it is taking is drawn over it by {@link HomePlates}, not by a name tag.
 */
public class TrainingDummyRenderer extends GeoEntityRenderer<TrainingDummy> {
    public TrainingDummyRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<TrainingDummy>(BeyondSolo.id("training_dummy"), false) {
            private final ResourceLocation texture = BeyondSolo.id("textures/entity/training_dummy_main.png");

            @Override
            public ResourceLocation getTextureResource(TrainingDummy dummy) {
                return texture;
            }
        });
        this.shadowRadius = 0.45f;
    }

    @Override
    public void preRender(PoseStack pose, TrainingDummy dummy, BakedGeoModel model, MultiBufferSource buffers, VertexConsumer buffer,
                          boolean isReRender, float partial, int light, int overlay, int colour) {
        super.preRender(pose, dummy, model, buffers, buffer, isReRender, partial, light, overlay, colour);
        boolean armoured = dummy.armoured();
        model.getBone("armour_body").ifPresent(b -> b.setHidden(!armoured));
        model.getBone("armour_head").ifPresent(b -> b.setHidden(!armoured));
    }

    @Override
    public int getPackedOverlay(TrainingDummy dummy, float u, float partialTick) {
        // straw doesn't flush red under every blow: it only rocks
        return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(false));
    }

    @Override
    protected float getDeathMaxRotation(TrainingDummy dummy) {
        return 0f;
    }

    @Override
    public boolean shouldShowName(TrainingDummy dummy) {
        return false;
    }
}
