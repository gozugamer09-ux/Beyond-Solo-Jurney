package com.beyondsolo.client.light;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.gate.GateFx;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.light.EnvoyEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.Color;

/**
 * The Envoy of Dawn (made by tools/gen_light_assets.py): its model lit by its own light (it is never in shadow), its
 * halo, mask lines and the edges of its wings burning, a soft sun of light behind it and motes rising round it. As it
 * goes back up into the light it grows brighter and fades away.
 */
public class EnvoyRenderer extends GeoEntityRenderer<EnvoyEntity> {
    private static final ResourceLocation GLOW = BeyondSolo.id("textures/effect/glow.png");
    private static final int LIGHT = 0xFFE9A8;

    public EnvoyRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<EnvoyEntity>(BeyondSolo.id("light_envoy"), true) {
            private final ResourceLocation texture = BeyondSolo.id("textures/entity/light_envoy_main.png");

            @Override
            public ResourceLocation getTextureResource(EnvoyEntity envoy) {
                return texture;
            }
        });
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.0f;
    }

    /** How much of it is still here as it leaves (1 until its last half second). */
    private static float presence(EnvoyEntity envoy, float partial) {
        if (!envoy.leaving()) return 1;
        float t = envoy.leftFor() + partial;
        return Mth.clamp((EnvoyEntity.LEAVE_TICKS - t) / 14f, 0, 1);
    }

    @Override
    public RenderType getRenderType(EnvoyEntity envoy, ResourceLocation texture, MultiBufferSource buffers, float partial) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public Color getRenderColor(EnvoyEntity envoy, float partial, int light) {
        return Color.ofARGB(presence(envoy, partial), 1f, 1f, 1f);
    }

    @Override
    public void render(EnvoyEntity envoy, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        // it is lit by its own light
        super.render(envoy, yaw, partial, pose, buffers, LightTexture.FULL_BRIGHT);
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        float time = (envoy.tickCount + partial) % 7200;
        float h = envoy.getBbHeight();
        float here = presence(envoy, partial);
        float rising = envoy.leaving() ? Mth.clamp((envoy.leftFor() + partial) / EnvoyEntity.LEAVE_TICKS, 0, 1) : 0;
        float pulse = 0.85f + 0.15f * Mth.sin(time * 0.08f);
        VertexConsumer glow = buffers.getBuffer(ModRenderTypes.lightTexture(GLOW));
        int white = GateFx.mix(LIGHT, 0xFFFFFF, 0.6f);
        // a soft sun behind it, and its halo's light
        GateFx.dot(glow, pose, camera, 0, h * 0.62f, 0, h * 0.75f * pulse, LIGHT, (0.16f + 0.4f * rising) * here);
        GateFx.dot(glow, pose, camera, 0, h * 0.86f, 0, h * 0.3f, white, (0.35f + 0.4f * rising) * here);
        // motes of light drifting up round it
        for (int i = 0; i < 8; i++) {
            float u = ((time * 0.012f) + i / 8f) % 1f;
            float ang = i * 2.39f + time * 0.01f;
            float r = 0.6f + 0.35f * Mth.sin(i * 1.7f);
            GateFx.dot(glow, pose, camera, Mth.cos(ang) * r, h * (0.05f + 1.0f * u), Mth.sin(ang) * r, 0.09f, white, 0.8f * Mth.sin(u * Mth.PI) * here);
        }
    }
}
