package com.beyondsolo.client.shadow;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.gate.GateFx;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.shadow.ShadowEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Draws a shadow: the creature it was, poured in ink ({@link ShadowTint}), with two burning eyes, a faint aura and
 * a slow ring at its feet (a magic circle for boss shadows). When called it climbs out of the ground through a
 * circle of light; when dismissed or fallen it sinks back in.
 */
public class ShadowRenderer extends EntityRenderer<ShadowEntity> {
    static final ResourceLocation GLOW = BeyondSolo.id("textures/effect/glow.png");
    static final ResourceLocation RING = BeyondSolo.id("textures/effect/ring.png");
    static final ResourceLocation CIRCLE = BeyondSolo.id("textures/effect/magic_circle.png");

    public ShadowRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.6f;
        this.shadowStrength = 1f;
    }

    @Override
    public void render(ShadowEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float time = e.tickCount + partial;
        float h = e.getBbHeight();
        float w = e.getBbWidth();
        int color = e.rank().color();

        // rising out of the ground, sinking back into it, or falling apart
        float rise = e.preview ? 1f : Mth.clamp(time / e.riseTicks(), 0f, 1f);
        float riseEase = 1 - (1 - rise) * (1 - rise) * (1 - rise);
        float sink = e.isSinking() ? Mth.clamp((e.sinkTicks() + partial) / ShadowEntity.SINK_TICKS, 0f, 1f) : 0f;
        float dead = e.deathTime > 0 ? Mth.clamp((e.deathTime + partial) / ShadowEntity.DEATH_TICKS, 0f, 1f) : 0f;
        float down = (1 - riseEase) * h * 1.05f + sink * sink * h * 1.05f + dead * dead * h * 0.8f;
        float presence = Math.min(riseEase, 1 - Math.max(sink, dead));

        // the ground under a shadow coming or going: a circle of violet light
        float gate = e.preview ? 0f : Math.max(1 - rise, Math.max(sink, dead)) ;
        if (gate > 0.01f) {
            float k = Mth.sin(Mth.clamp(gate, 0, 1) * Mth.PI * 0.5f);
            GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(CIRCLE)), pose, 0.04f, (w * 0.9f + 0.6f) * (0.6f + 0.4f * k), time * 6, color, 0.9f * k);
            GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(RING)), pose, 0.05f, (w * 0.9f + 0.6f) * (1.4f - 0.4f * k), 0, 0xFFFFFF, 0.5f * k);
            GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, 0.06f, w + 0.8f, 0, color, 0.5f * k);
        }

        LivingEntity look = e.look();
        if (look != null) {
            if (e.preview) {
                // a screen preview is never ticked: give the stand-in this frame's pose
                look.yBodyRot = look.yBodyRotO = e.yBodyRot;
                look.setYRot(e.getYRot());
                look.yRotO = e.getYRot();
                look.setXRot(e.getXRot());
                look.yHeadRot = look.yHeadRotO = e.yHeadRot;
                look.tickCount = e.tickCount;
            }
            pose.pushPose();
            pose.translate(0, -down, 0);
            float s = e.sizeScale();
            pose.scale(s, s, s);
            try {
                @SuppressWarnings("unchecked")
                EntityRenderer<LivingEntity> r = (EntityRenderer<LivingEntity>) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(look);
                float brightness = e.isBoss() ? 1.25f : 1f;
                r.render(look, yaw, partial, pose, ShadowTint.ink(buffers, brightness), light);
            } catch (RuntimeException ignored) {
                // a creature whose renderer can't draw a stand-in: the aura alone shows where it is
            }
            pose.popPose();
        }

        if (presence > 0.02f && e.isAlive()) aura(e, pose, buffers, partial, time, w, h, down, color, presence);
        if (!e.preview) super.render(e, yaw, partial, pose, buffers, light);
    }

    private void aura(ShadowEntity e, PoseStack pose, MultiBufferSource buffers, float partial, float time, float w, float h, float down, int color, float k) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        boolean boss = e.isBoss();
        // eyes: two burning points in front of its face
        float headYaw = Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot) * Mth.DEG_TO_RAD;
        float fx = -Mth.sin(headYaw), fz = Mth.cos(headYaw);
        float sx = -fz, sz = fx;
        float eyeY = e.getEyeHeight() * 0.98f - down;
        float fwd = Math.min(1.6f, w * 0.5f + 0.03f);
        float gap = Mth.clamp(w * 0.14f, 0.06f, 0.35f);
        float flicker = 0.85f + 0.15f * Mth.sin(time * 0.9f + e.getId());
        int eye = GateFx.mix(color, 0xFFFFFF, 0.35f);
        float es = (boss ? 0.13f : 0.07f) * (0.9f + 0.1f * flicker) * Math.max(1f, w * 0.6f);
        for (int side = -1; side <= 1; side += 2) {
            float x = fx * fwd + sx * gap * side;
            float z = fz * fwd + sz * gap * side;
            GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, x, eyeY, z, es, eye, k * flicker);
            GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, x, eyeY, z, es * 2.6f, color, 0.45f * k * flicker);
        }
        // a faint aura around its body and a slow ring at its feet
        GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, 0, h * 0.5f - down, 0, Math.max(w, h) * 0.85f, color, 0.13f * k);
        GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(RING)), pose, 0.03f, w * 0.8f + 0.25f, 0, color, 0.28f * k);
        if (boss) {
            GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(CIRCLE)), pose, 0.035f, w + 0.9f, time * 1.5f, color, 0.45f * k);
            GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, 0, h * 0.55f - down, 0, Math.max(w, h) * 1.2f, 0xFF4A6A, 0.08f * k);
        }
    }

    @Override
    protected boolean shouldShowName(ShadowEntity e) {
        return e.hasCustomName() && e.isCustomNameVisible() && !e.isSinking() && e.deathTime == 0;
    }

    @Override
    public ResourceLocation getTextureLocation(ShadowEntity e) {
        return GLOW;
    }
}
