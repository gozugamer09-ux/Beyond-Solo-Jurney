package com.beyondsolo.client.ruler;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.gate.GateFx;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.ruler.HeraldEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * A herald of light (made by tools/gen_ruler_assets.py): its model with its light glowing, a faint glow of the light
 * round it, and as it dies, its light going out of it in a rising haze. It falls to its knees with its own animation.
 */
public class HeraldRenderer extends GeoEntityRenderer<HeraldEntity> {
    private static final ResourceLocation GLOW = BeyondSolo.id("textures/effect/glow.png");
    private static final int LIGHT = 0xFFE7A0;

    public HeraldRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<HeraldEntity>(BeyondSolo.id("ruler_herald"), true) {
            private final ResourceLocation texture = BeyondSolo.id("textures/entity/ruler_herald_main.png");

            @Override
            public ResourceLocation getTextureResource(HeraldEntity herald) {
                return texture;
            }
        });
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.6f;
    }

    @Override
    protected float getDeathMaxRotation(HeraldEntity herald) {
        // it falls to its knees with its own animation
        return 0f;
    }

    @Override
    public void render(HeraldEntity herald, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(herald, yaw, partial, pose, buffers, light);
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        float time = (herald.tickCount + partial) % 7200;
        float h = herald.getBbHeight();
        float dying = herald.deathTime > 0 ? Mth.clamp((herald.deathTime + partial) / 20f, 0f, 1f) : 0f;
        float pulse = 0.8f + 0.2f * Mth.sin(time * 0.15f);
        GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, 0, h * 0.55f, 0, h * 0.55f, LIGHT, 0.1f * pulse * (1 - dying));
        if (dying > 0) {
            for (int i = 0; i < 6; i++) {
                float u = (dying + i / 6f) % 1f;
                float ang = i * 2.1f + time * 0.05f;
                GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, Mth.cos(ang) * 0.4f, h * (0.2f + 0.9f * u),
                        Mth.sin(ang) * 0.4f, 0.18f, LIGHT, 0.7f * (1 - u) * dying);
            }
        }
    }
}
