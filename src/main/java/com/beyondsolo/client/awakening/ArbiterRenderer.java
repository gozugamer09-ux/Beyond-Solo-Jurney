package com.beyondsolo.client.awakening;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.awakening.ArbiterEntity;
import com.beyondsolo.client.render.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * The Hollow Arbiter: the GeckoLib model at three times its size (gold seams and the slit of its face glow), and
 * drawn as light around it: the broken halo behind its head, which wakes, turns and burns red when it is unbound;
 * the circle on the floor it kneels in; the glare of its face; and, as it dies, light breaking out of it.
 */
public class ArbiterRenderer extends GeoEntityRenderer<ArbiterEntity> {
    public static final float SCALE = 3f;
    private static final int GOLD = 0xF2D58A;
    private static final int RED = 0xFF4050;

    public ArbiterRenderer(EntityRendererProvider.Context context) {
        this(context, new DefaultedEntityGeoModel<>(BeyondSolo.id("arbiter"), false));
    }

    /** Another Arbiter on the same bones (the throne's, in its own colours: see {@link #calm} and {@link #angry}). */
    protected ArbiterRenderer(EntityRendererProvider.Context context, software.bernie.geckolib.model.GeoModel<ArbiterEntity> model) {
        super(context, model);
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        withScale(SCALE);
        this.shadowRadius = 3.2f;
    }

    /** The colour of its light while it is calm. */
    protected int calm() {
        return GOLD;
    }

    /** The colour of its light once it is enraged. */
    protected int angry() {
        return RED;
    }

    @Override
    protected float getDeathMaxRotation(ArbiterEntity arbiter) {
        return 0f;
    }

    @Override
    public boolean shouldRender(ArbiterEntity arbiter, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(ArbiterEntity a, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight) {
        // its hall is dark: the stone always catches a little light, so its shape reads from the doorway
        int lit = net.minecraft.client.renderer.LightTexture.pack(Math.max(net.minecraft.client.renderer.LightTexture.block(packedLight), 10),
                net.minecraft.client.renderer.LightTexture.sky(packedLight));
        super.render(a, entityYaw, partialTick, pose, buffers, lit);
        float time = a.tickCount + partialTick;
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        float stand = a.standing(partialTick);
        float dying = a.deathTime > 0 ? Mth.clamp((a.deathTime + partialTick) / ArbiterEntity.DEATH_TICKS, 0f, 1f) : 0f;
        float awake = a.isAwake() ? Mth.clamp((ArbiterEntity.INTRO_TICKS - a.introTicks() + partialTick) / 30f, 0f, 1f) : 0f;
        int rgb = a.isEnraged() ? angry() : calm();
        float live = 1 - dying;

        // the circle it kneels in
        float pulse = 0.5f + 0.5f * Mth.sin(time * 0.08f);
        AwakeningWorldFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(AwakeningWorldFx.CIRCLE)), pose, 0.06f, 6.2f + 1.2f * awake,
                time * (a.isEnraged() ? 2.4f : 0.5f + awake), rgb, (0.22f + 0.1f * pulse + 0.45f * awake) * live);
        if (a.isEnraged()) AwakeningWorldFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(AwakeningWorldFx.CIRCLE)), pose, 0.07f, 4f, -time * 3.5f, 0xFF8070, 0.5f * live);

        // head height: kneeling, then standing; slumping again as it dies
        float sink = Mth.clamp(dying * 1.5f, 0f, 1f);
        float headY = Mth.lerp(stand * (1 - sink * 0.6f), 5.5f, 9.1f);
        float yaw = Mth.rotLerp(partialTick, a.yBodyRotO, a.yBodyRot);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180f - yaw));
        // the halo: behind the head, upright, turning
        pose.pushPose();
        // bowed, the head hangs forward of the shoulders
        pose.translate(0, headY + 0.5f, Mth.lerp(stand * (1 - sink * 0.6f), -0.1f, 1.25f));
        float haloA = (0.25f + 0.75f * awake) * live;
        float haloR = 2.3f + 0.25f * Mth.sin(time * 0.1f) + (a.isEnraged() ? 0.5f : 0f);
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(time * (a.isEnraged() ? 6f : 1.2f + 2f * awake)));
        AwakeningWorldFx.quad(buffers.getBuffer(ModRenderTypes.lightTexture(AwakeningWorldFx.CIRCLE)), pose.last().pose(), haloR, haloR, rgb, 0.85f * haloA);
        pose.popPose();
        AwakeningWorldFx.quad(buffers.getBuffer(ModRenderTypes.lightTexture(AwakeningWorldFx.RING)), pose.last().pose(), haloR * 1.12f, haloR * 1.12f, AwakeningWorldFx.mix(rgb, 0xFFFFFF, 0.5f), 0.8f * haloA);
        AwakeningWorldFx.quad(buffers.getBuffer(ModRenderTypes.lightTexture(AwakeningWorldFx.GLOW)), pose.last().pose(), haloR * 2.1f, haloR * 2.1f, rgb, 0.3f * haloA);
        pose.popPose();
        pose.popPose();

        // the glare of its face
        if (awake > 0.01f) {
            float fx = -Mth.sin(yaw * Mth.DEG_TO_RAD) * 0.9f, fz = Mth.cos(yaw * Mth.DEG_TO_RAD) * 0.9f;
            AwakeningWorldFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(AwakeningWorldFx.GLOW)), pose, camera, fx, headY - 0.1f, fz, 1.1f + 0.2f * pulse, rgb, 0.55f * awake * live);
        }
        if (dying > 0) {
            // light breaks out of the cracks, then takes it whole
            float gather = Mth.clamp(dying * 1.4f, 0f, 1f);
            float pillar = Mth.clamp((dying - 0.6f) / 0.4f, 0f, 1f);
            AwakeningWorldFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(AwakeningWorldFx.GLOW)), pose, camera, 0, 4.5f, 0, 3f + 7f * gather, 0xFFF0C8, 0.65f * gather);
            for (int i = 0; i < 20; i++) {
                float u = (time * 0.035f + i * 0.05f) % 1f;
                float ang = i * 2.4f;
                AwakeningWorldFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(AwakeningWorldFx.GLOW)), pose, camera,
                        Mth.cos(ang) * (1f + 5f * u), 1f + (i % 5) * 1.8f + 2f * u, Mth.sin(ang) * (1f + 5f * u), 0.22f, calm(), gather * (1 - u));
            }
            if (pillar > 0) {
                AwakeningWorldFx.column(buffers.getBuffer(ModRenderTypes.lightTexture(AwakeningWorldFx.BEAM)), pose, camera, a.getX(), a.getZ(), 1.2f + 3.5f * pillar, 40f, 0xFFFFFF, Math.min(1f, pillar * 1.5f));
                AwakeningWorldFx.column(buffers.getBuffer(ModRenderTypes.lightTexture(AwakeningWorldFx.BEAM)), pose, camera, a.getX(), a.getZ(), 3f + 5f * pillar, 40f, calm(), 0.6f * pillar);
            }
        }
    }
}
