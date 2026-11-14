package com.beyondsolo.client.gate;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.gate.GateBossEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * The gate boss: the GeckoLib model (glowing eyes and crystals), a magic circle of the rank's colour under its
 * feet that flares when it wakes and burns red when it is enraged, and, as it dies, light gathering on it and a
 * pillar of light that takes it.
 */
public class GateBossRenderer extends GeoEntityRenderer<GateBossEntity> {
    private static final ResourceLocation CIRCLE = BeyondSolo.id("textures/effect/magic_circle.png");
    private static final ResourceLocation GLOW = BeyondSolo.id("textures/effect/glow.png");
    private static final ResourceLocation BEAM = BeyondSolo.id("textures/effect/beam.png");

    public GateBossRenderer(EntityRendererProvider.Context context) {
        super(context, new GateModels.Boss());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 1.2f;
    }

    @Override
    protected float getDeathMaxRotation(GateBossEntity boss) {
        // it falls with its own death animation, not by tipping over
        return 0f;
    }

    @Override
    public void render(GateBossEntity boss, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight) {
        super.render(boss, entityYaw, partialTick, pose, buffers, packedLight);
        float time = boss.tickCount + partialTick;
        int rgb = boss.isEnraged() ? 0xFF3040 : boss.rank().color();
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        VertexConsumer circle = buffers.getBuffer(ModRenderTypes.lightTexture(CIRCLE));
        VertexConsumer glow = buffers.getBuffer(ModRenderTypes.lightTexture(GLOW));

        float intro = boss.introTicks() > 0 ? boss.introTicks() / (float) GateBossEntity.INTRO_TICKS : 0f;
        float flare = intro > 0 ? Mth.sin((1 - intro) * Mth.PI) : 0f;
        float dying = boss.deathTime > 0 ? Mth.clamp((boss.deathTime + partialTick) / GateBossEntity.DEATH_TICKS, 0f, 1f) : 0f;
        float circleAlpha = boss.isAwake() ? 0.55f + 0.35f * flare : 0.25f;
        circleAlpha *= 1 - dying;
        GateFx.flat(circle, pose, 0.05f, 2.4f + 1.2f * flare, time * (boss.isEnraged() ? 3f : 1.2f), rgb, circleAlpha);
        if (boss.isEnraged()) GateFx.flat(circle, pose, 0.06f, 1.5f, -time * 4f, 0xFF7060, 0.5f * (1 - dying));
        if (flare > 0.01f) GateFx.dot(glow, pose, camera, 0, 2.2f, 0, 2.5f + 2f * flare, rgb, 0.45f * flare);

        if (dying > 0) {
            // light gathers on it, then a pillar of light takes it
            float gather = Mth.clamp(dying * 1.6f, 0f, 1f);
            float pillar = Mth.clamp((dying - 0.55f) / 0.45f, 0f, 1f);
            GateFx.dot(glow, pose, camera, 0, 1.6f, 0, 1.2f + 2.5f * gather, GateFx.mix(boss.rank().color(), 0xFFFFFF, 0.5f), 0.6f * gather);
            for (int i = 0; i < 16; i++) {
                float u = ((time * 0.04f + i * 0.0625f) % 1f);
                float far = 1 - u * u;
                float a = i * 2.4f + u * 2f;
                GateFx.dot(glow, pose, camera, Mth.cos(a) * 3f * far, 0.3f + 3f * (i % 4) / 4f * far + 1.2f * u, Mth.sin(a) * 3f * far,
                        0.08f, GateFx.mix(boss.rank().color(), 0xFFFFFF, 0.6f), gather * u);
            }
            if (pillar > 0) {
                VertexConsumer b = buffers.getBuffer(ModRenderTypes.lightTexture(BEAM));
                GateFx.column(b, pose, camera, boss.getX(), boss.getZ(), 0.4f + 1.6f * pillar, 30f, 0xFFFFFF, Math.min(1f, pillar * 1.4f));
                GateFx.column(b, pose, camera, boss.getX(), boss.getZ(), 1.2f + 2.4f * pillar, 30f, boss.rank().color(), 0.6f * pillar);
            }
        }
    }
}
