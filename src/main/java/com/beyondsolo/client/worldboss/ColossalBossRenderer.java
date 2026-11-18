package com.beyondsolo.client.worldboss;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.gate.GateFx;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.worldboss.ColossalBoss;
import com.beyondsolo.worldboss.MonarchBoss;
import com.beyondsolo.worldboss.WorldBossKind;
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
 * A world boss, a monarch or a ruler of light: its GeckoLib model with its glowing parts, and its light round it. A
 * monarch or a ruler walks on a slowly turning sigil of its colour once it has landed, and a soft glow of its power hangs
 * on it; in its last phase both burn brighter. A ruler wears a halo of light. As one dies, light gathers on it and a
 * pillar of light takes it.
 */
public class ColossalBossRenderer<T extends ColossalBoss> extends GeoEntityRenderer<T> {
    private static final ResourceLocation CIRCLE = BeyondSolo.id("textures/effect/magic_circle.png");
    private static final ResourceLocation GLOW = BeyondSolo.id("textures/effect/glow.png");
    private static final ResourceLocation BEAM = BeyondSolo.id("textures/effect/beam.png");
    private static final ResourceLocation RING = BeyondSolo.id("textures/effect/ring.png");

    private final WorldBossKind kind;

    public ColossalBossRenderer(EntityRendererProvider.Context context, WorldBossKind kind) {
        super(context, new WorldBossModel<>(kind));
        this.kind = kind;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = switch (kind) {
            case TITAN -> 3.2f;
            case BEHEMOTH -> 2.8f;
            default -> 0.9f;
        };
        this.shadowStrength = 0.8f;
    }

    @Override
    protected float getDeathMaxRotation(T boss) {
        // each falls with its own death animation
        return 0f;
    }

    @Override
    public void render(T boss, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        // climbing out of the earth or sinking back into it: drawn sunk into the ground, which hides what is below
        float buried = boss.buried(partial);
        if (buried > 0) {
            pose.pushPose();
            pose.translate(0, -buried, 0);
        }
        super.render(boss, yaw, partial, pose, buffers, light);
        if (buried > 0) pose.popPose();
        float time = (boss.tickCount + partial) % 7200;
        int rgb = boss.fxColor();
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        float h = boss.getBbHeight(), w = boss.getBbWidth();
        float dying = boss.deathTime > 0 ? Mth.clamp((boss.deathTime + partial) / 90f, 0f, 1f) : 0f;
        boolean last = boss.phase() >= 3;

        if (kind.sovereign() && boss.isAwake()) {
            // the sigil it walks on, once it has landed
            float landed = boss.introTicks() > 0 ? Mth.clamp((31 - boss.introTicks()) / 10f, 0, 1) : 1;
            float a = landed * (1 - dying) * (boss.leaving() ? 0 : 1);
            if (a > 0.01f && kind.ruler()) {
                // a ruler of light wears a halo: a ring of white light over its head, and a second, fainter, in its last phase
                float hy = h + 0.35f + 0.08f * Mth.sin(time * 0.07f);
                int white = GateFx.mix(rgb, 0xFFFFFF, 0.6f);
                GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(RING)), pose, hy, 0.75f, 0, white, 0.9f * a);
                GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, hy - 0.02f, 1.3f, 0, rgb, 0.4f * a);
                if (last) GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(RING)), pose, hy + 0.25f, 1.1f, 0, rgb, 0.45f * a);
            }
            if (a > 0.01f) {
                float r = 2.6f + (last ? 0.6f : 0);
                GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(CIRCLE)), pose, 0.06f, r, time * (last ? 2.6f : 1.2f), rgb, 0.6f * a);
                GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(CIRCLE)), pose, 0.07f, r * 0.6f, -time * 2f, GateFx.mix(rgb, 0xFFFFFF, 0.4f), 0.4f * a);
                GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, 0.08f, r * 1.5f, 0, rgb, 0.35f * a);
            }
            // the weight of its power: a haze of its light round it
            float pulse = 0.8f + 0.2f * Mth.sin(time * 0.12f);
            GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, 0, h * 0.55f, 0, h * (last ? 1.0f : 0.8f),
                    rgb, (last ? 0.2f : 0.12f) * pulse * (1 - dying));
            // coming down out of its gate: a trail of light above it
            if (boss.introTicks() > 31) {
                VertexConsumer b = buffers.getBuffer(ModRenderTypes.lightTexture(BEAM));
                GateFx.column(b, pose, camera, boss.getX(), boss.getZ(), 1.2f, MonarchBoss.GATE_HEIGHT * 0.5f, rgb, 0.4f);
            }
        } else if (!kind.sovereign() && boss.isAwake() && last) {
            // a world boss in its last phase: its own light spills out of it
            float pulse = 0.75f + 0.25f * Mth.sin(time * 0.2f);
            GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, 0.1f, w * 1.3f, 0, rgb, 0.45f * pulse * (1 - dying));
            GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, 0, h * 0.5f, 0, h * 0.75f, rgb, 0.12f * pulse * (1 - dying));
        }

        if (dying > 0) {
            float gather = Mth.clamp(dying * 1.6f, 0f, 1f);
            float pillar = Mth.clamp((dying - 0.5f) / 0.5f, 0f, 1f);
            int hot = GateFx.mix(rgb, 0xFFFFFF, 0.55f);
            GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, 0, h * 0.45f, 0, w * 0.6f + h * 0.4f * gather, hot, 0.6f * gather);
            for (int i = 0; i < 24; i++) {
                float u = (time * 0.035f + i / 24f) % 1f;
                float far = 1 - u * u;
                float ang = i * 2.4f + u * 2f;
                float rad = (w * 1.2f + 2f) * far;
                GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, Mth.cos(ang) * rad, h * (0.15f + 0.8f * (i % 5) / 5f) * far + h * 0.3f * u,
                        Mth.sin(ang) * rad, 0.12f + 0.02f * h, hot, gather * u);
            }
            if (pillar > 0) {
                VertexConsumer b = buffers.getBuffer(ModRenderTypes.lightTexture(BEAM));
                GateFx.column(b, pose, camera, boss.getX(), boss.getZ(), w * 0.2f + w * 0.5f * pillar, h * 6f, 0xFFFFFF, Math.min(1f, pillar * 1.4f));
                GateFx.column(b, pose, camera, boss.getX(), boss.getZ(), w * 0.5f + w * 0.9f * pillar, h * 6f, rgb, 0.6f * pillar);
            }
        }
    }

    @Override
    public boolean shouldShowName(T boss) {
        return false;
    }
}
