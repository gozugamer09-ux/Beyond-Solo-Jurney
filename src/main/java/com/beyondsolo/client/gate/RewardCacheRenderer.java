package com.beyondsolo.client.gate;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.gate.RewardCacheBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * The reward cache's reveal: a column of light comes down first, the chest falls through it and lands with a bounce
 * and a ring of light racing over the floor; it hovers a soft glow while it waits; opened, the lid swings up
 * (GeckoLib animation) and a burst of light and rising motes pour out. Near the end it shrinks away.
 */
public class RewardCacheRenderer extends GeoBlockRenderer<RewardCacheBlockEntity> {
    private static final ResourceLocation BEAM = BeyondSolo.id("textures/effect/beam.png");
    private static final ResourceLocation GLOW = BeyondSolo.id("textures/effect/glow.png");
    private static final ResourceLocation RING = BeyondSolo.id("textures/effect/ring.png");
    private static final ResourceLocation CIRCLE = BeyondSolo.id("textures/effect/magic_circle.png");

    public RewardCacheRenderer(BlockEntityRendererProvider.Context context) {
        super(new GateModels.Cache());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void render(RewardCacheBlockEntity cache, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (cache.getLevel() == null) return;
        float now = cache.getLevel().getGameTime() + partialTick;
        float landed = cache.landed(partialTick);
        float sinceLand = cache.droppedAt() < 0 ? 999 : now - cache.droppedAt() - RewardCacheBlockEntity.DROP_TICKS;
        float sinceOpen = cache.openedAt() < 0 ? -1 : now - cache.openedAt();
        float fadeOut = sinceOpen < 0 ? 1f : Mth.clamp((RewardCacheBlockEntity.LINGER_TICKS - sinceOpen) / 20f, 0f, 1f);
        int gold = SystemTheme.GOLD & 0xFFFFFF;
        int blue = SystemTheme.ACCENT & 0xFFFFFF;

        // the fall: eased in, with a small bounce on landing
        float fall = (1 - landed);
        float y = 8f * fall * fall;
        if (sinceLand >= 0 && sinceLand < 8) y += 0.18f * Mth.sin(sinceLand / 8f * Mth.PI);

        pose.pushPose();
        pose.translate(0, y, 0);
        float s = fadeOut;
        if (s < 1f) {
            pose.translate(0.5f, 0, 0.5f);
            pose.scale(s, s, s);
            pose.translate(-0.5f, 0, -0.5f);
        }
        if (s > 0.02f) super.render(cache, partialTick, pose, buffers, packedLight, packedOverlay);
        pose.popPose();

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        BlockPos p = cache.getBlockPos();
        pose.pushPose();
        pose.translate(0.5f, 0f, 0.5f);
        // the column of light it comes down in, fading once it has landed
        float beam = sinceLand < 0 ? Math.min(1f, (now - cache.droppedAt()) / 8f) : Mth.clamp(1 - sinceLand / 40f, 0f, 1f);
        if (beam > 0.01f) {
            VertexConsumer b = buffers.getBuffer(ModRenderTypes.lightTexture(BEAM));
            GateFx.column(b, pose, camera, p.getX() + 0.5, p.getZ() + 0.5, 1.1f, 24f, blue, 0.75f * beam);
            GateFx.column(b, pose, camera, p.getX() + 0.5, p.getZ() + 0.5, 0.45f, 24f, 0xFFFFFF, 0.8f * beam);
        }
        VertexConsumer glow = buffers.getBuffer(ModRenderTypes.lightTexture(GLOW));
        VertexConsumer ring = buffers.getBuffer(ModRenderTypes.lightTexture(RING));
        VertexConsumer circle = buffers.getBuffer(ModRenderTypes.lightTexture(CIRCLE));
        if (sinceLand >= 0) {
            // landing ring racing out over the floor
            if (sinceLand < 18) {
                float k = sinceLand / 18f;
                GateFx.flat(ring, pose, 0.04f, 0.6f + 4.5f * (float) Math.pow(k, 0.7), 0, GateFx.mix(blue, 0xFFFFFF, 0.4f), 1 - k);
            }
            // waiting: a slow circle under it and a soft glow
            float wait = sinceOpen < 0 ? 1f : Mth.clamp(1 - sinceOpen / 30f, 0f, 1f);
            GateFx.flat(circle, pose, 0.03f, 1.25f, now * 0.8f, blue, 0.55f * wait * fadeOut);
            GateFx.dot(glow, pose, camera, 0, 0.6f, 0, 1.0f + 0.1f * Mth.sin(now * 0.15f), blue, 0.35f * wait * fadeOut);
        }
        if (sinceOpen >= 0) {
            // the burst when the lid opens, and motes of gold rising out
            float burst = Mth.clamp((sinceOpen - 8) / 10f, 0f, 1f) * Mth.clamp(1 - (sinceOpen - 18) / 30f, 0f, 1f);
            GateFx.dot(glow, pose, camera, 0, 1.0f, 0, 0.6f + 2.4f * burst, 0xFFFFFF, burst);
            GateFx.dot(glow, pose, camera, 0, 1.0f, 0, 1.4f + 3.5f * burst, gold, 0.8f * burst);
            VertexConsumer b = buffers.getBuffer(ModRenderTypes.lightTexture(BEAM));
            GateFx.column(b, pose, camera, p.getX() + 0.5, p.getZ() + 0.5, 0.7f, 7f, gold, 0.8f * burst);
            float rise = Mth.clamp((sinceOpen - 10) / 10f, 0f, 1f) * fadeOut;
            for (int i = 0; i < 14; i++) {
                float life = 30 + (i * 7) % 13;
                float u = ((sinceOpen + i * 5.3f) % life) / life;
                float a = i * 2.4f + u * 1.2f;
                float r = 0.2f + 0.5f * u;
                GateFx.dot(glow, pose, camera, Mth.cos(a) * r, 0.8f + 2.2f * u, Mth.sin(a) * r, 0.05f + 0.03f * (i % 3),
                        GateFx.mix(gold, 0xFFFFFF, 0.4f), rise * Mth.sin(u * Mth.PI));
            }
        }
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(RewardCacheBlockEntity cache) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(RewardCacheBlockEntity cache) {
        BlockPos p = cache.getBlockPos();
        return new AABB(p.getX() - 5, p.getY() - 1, p.getZ() - 5, p.getX() + 6, p.getY() + 26, p.getZ() + 6);
    }
}
