package com.beyondsolo.client.enemy;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.gate.GateFx;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.enemy.ElementOrbEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** An element bolt: a white-hot core in a halo of its colour, with a short tail of light. */
public class OrbRenderer extends EntityRenderer<ElementOrbEntity> {
    private static final ResourceLocation GLOW = BeyondSolo.id("textures/effect/glow.png");

    public OrbRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ElementOrbEntity orb, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        float s = orb.size();
        float time = orb.tickCount + partial;
        int c = orb.color();
        float pulse = 1 + 0.12f * Mth.sin(time * 0.9f);
        GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, 0, s * 0.5f, 0, s * 2.4f * pulse, c, 0.55f);
        GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, 0, s * 0.5f, 0, s * 1.0f, GateFx.mix(c, 0xFFFFFF, 0.7f), 0.95f);
        Vec3 v = orb.getDeltaMovement();
        for (int i = 1; i <= 5; i++) {
            float k = i / 5f;
            GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, (float) (-v.x * k * 2.2), (float) (s * 0.5f - v.y * k * 2.2),
                    (float) (-v.z * k * 2.2), s * (1.6f - k), c, 0.45f * (1 - k));
        }
    }

    @Override
    public ResourceLocation getTextureLocation(ElementOrbEntity orb) {
        return GLOW;
    }
}
