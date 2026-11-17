package com.beyondsolo.client.tower;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.gate.GateFx;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.tower.CitadelLordEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * The lord of the Crimson Citadel: the GeckoLib model (made by tools/gen_tower_assets.py; the seams of his plate,
 * his eyes and the edge of his halberd glow), and light round him: the glare of his eyes as he wakes, a crimson
 * heat at his back under the blood moon, and as he dies, the light breaking out of him and a column taking him.
 * No circles at his feet: his moves announce themselves on the floor.
 */
public class CitadelLordRenderer extends GeoEntityRenderer<CitadelLordEntity> {
    private static final ResourceLocation TEXTURE = BeyondSolo.id("textures/entity/citadel_lord_main.png");
    private static final ResourceLocation GLOW = BeyondSolo.id("textures/effect/glow.png");
    private static final ResourceLocation BEAM = BeyondSolo.id("textures/effect/beam.png");
    private static final int RED = 0xFF2E3F;
    private static final int HOT = 0xFF8A70;

    public CitadelLordRenderer(EntityRendererProvider.Context context) {
        super(context, new Model());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 1.3f;
    }

    static final class Model extends DefaultedEntityGeoModel<CitadelLordEntity> {
        Model() {
            super(BeyondSolo.id("citadel_lord"), true);
        }

        @Override
        public ResourceLocation getTextureResource(CitadelLordEntity lord) {
            return TEXTURE;
        }
    }

    @Override
    protected float getDeathMaxRotation(CitadelLordEntity lord) {
        return 0f;
    }

    @Override
    public boolean shouldRender(CitadelLordEntity lord, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(CitadelLordEntity lord, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight) {
        // his hall is dim: the plate always catches a little light, so his shape reads from the doorway
        int lit = LightTexture.pack(Math.max(LightTexture.block(packedLight), 7), LightTexture.sky(packedLight));
        super.render(lord, entityYaw, partialTick, pose, buffers, lit);
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        float time = lord.tickCount + partialTick;
        float risen = lord.risen(partialTick);
        float dying = lord.deathTime > 0 ? Mth.clamp((lord.deathTime + partialTick) / CitadelLordEntity.DEATH_TICKS, 0f, 1f) : 0f;
        float live = 1 - dying;
        float yaw = Mth.rotLerp(partialTick, lord.yBodyRotO, lord.yBodyRot);
        float fx = -Mth.sin(yaw * Mth.DEG_TO_RAD), fz = Mth.cos(yaw * Mth.DEG_TO_RAD);

        // the glare of his eyes
        float eyes = (0.15f + 0.85f * risen) * live;
        if (eyes > 0.02f) {
            float beat = 0.85f + 0.15f * Mth.sin(time * 0.2f);
            GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, fx * 0.42f, 3.02f, fz * 0.42f, 0.55f * beat, RED, 0.55f * eyes);
        }
        // the blood moon: heat rising off his back, embers drifting up
        if (lord.bloodMoon() && dying <= 0) {
            float pulse = 0.75f + 0.25f * Mth.sin(time * 0.15f);
            GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, -fx * 0.4f, 2.0f, -fz * 0.4f, 2.6f * pulse, RED, 0.28f);
            for (int i = 0; i < 10; i++) {
                float u = (time * 0.02f + i * 0.1f) % 1f;
                float a = i * 2.39f + time * 0.01f;
                GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, Mth.cos(a) * (0.6f + 0.5f * u), 0.4f + 3.6f * u,
                        Mth.sin(a) * (0.6f + 0.5f * u), 0.12f, HOT, 0.8f * (1 - u));
            }
        }
        if (dying > 0) {
            // light breaks out of the seams, gathers, and a column takes him
            float gather = Mth.clamp(dying * 1.6f, 0f, 1f);
            float column = Mth.clamp((dying - 0.65f) / 0.35f, 0f, 1f);
            GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, 0, 1.8f, 0, 1.5f + 4f * gather, RED, 0.5f * gather);
            for (int i = 0; i < 16; i++) {
                float u = (time * 0.03f + i * 0.0625f) % 1f;
                float a = i * 2.4f;
                GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, Mth.cos(a) * (0.5f + 2.5f * u), 0.5f + (i % 4) * 0.8f + 2f * u,
                        Mth.sin(a) * (0.5f + 2.5f * u), 0.16f, HOT, gather * (1 - u));
            }
            if (column > 0) {
                GateFx.column(buffers.getBuffer(ModRenderTypes.lightTexture(BEAM)), pose, camera, lord.getX(), lord.getZ(), 0.8f + 2.2f * column, 30f, 0xFFFFFF, Math.min(1f, column * 1.4f));
                GateFx.column(buffers.getBuffer(ModRenderTypes.lightTexture(BEAM)), pose, camera, lord.getX(), lord.getZ(), 2f + 3f * column, 30f, RED, 0.6f * column);
            }
        }
    }
}
