package com.beyondsolo.client.isle;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.gate.GateFx;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.isle.NestHeartEntity;
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
 * A nest's heart (made by tools/gen_isle_assets.py): its model beating, its veins glowing, a sickly amber glow round it
 * that beats faster as it takes blows, and as it bursts, a spray of light going out of it.
 */
public class NestHeartRenderer extends GeoEntityRenderer<NestHeartEntity> {
    private static final ResourceLocation GLOW = BeyondSolo.id("textures/effect/glow.png");
    private static final int AMBER = 0xFFB43C;

    public NestHeartRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<NestHeartEntity>(BeyondSolo.id("nest_heart"), false) {
            private final ResourceLocation texture = BeyondSolo.id("textures/entity/nest_heart_main.png");

            @Override
            public ResourceLocation getTextureResource(NestHeartEntity heart) {
                return texture;
            }
        });
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.9f;
    }

    @Override
    protected float getDeathMaxRotation(NestHeartEntity heart) {
        return 0f;
    }

    @Override
    public void render(NestHeartEntity heart, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(heart, yaw, partial, pose, buffers, light);
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        float time = (heart.tickCount + partial) % 7200;
        float h = heart.getBbHeight();
        float hurt = 1 - Mth.clamp(heart.left(), 0, 1);
        float rate = 0.18f + 0.3f * hurt;
        float beat = Mth.sin(time * rate);
        float pulse = 0.75f + 0.25f * beat * beat;
        float dying = heart.deathTime > 0 ? Mth.clamp((heart.deathTime + partial) / 20f, 0f, 1f) : 0f;
        GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, 0, h * 0.45f, 0, h * (0.75f + 0.25f * hurt), AMBER,
                (0.14f + 0.12f * hurt) * pulse * (1 - dying));
        if (dying > 0) {
            for (int i = 0; i < 8; i++) {
                float u = (dying + i / 8f) % 1f;
                float ang = i * 0.785f;
                float r = 0.3f + 1.6f * u;
                GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, Mth.cos(ang) * r, h * 0.45f + 0.6f * u,
                        Mth.sin(ang) * r, 0.22f, AMBER, 0.8f * (1 - u) * dying);
            }
        }
    }
}
