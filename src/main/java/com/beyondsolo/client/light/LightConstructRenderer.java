package com.beyondsolo.client.light;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.gate.GateFx;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.light.LightConstruct;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.Color;

/**
 * The light given a shape (made by tools/gen_light_assets.py, one model per {@link LightConstruct.Form}): a body of
 * light you can half see through, white at its heart and tinted with the ruler's colour, its lines and edges burning on
 * top, and a bloom of light round it. It falls out of the sky as its form says (a lance or a blade hurled point first,
 * the hand let down slowly and then closing), the sun rises over the fight, and the choir stands at its ruler's back.
 */
public class LightConstructRenderer extends GeoEntityRenderer<LightConstruct> {
    private static final ResourceLocation GLOW = BeyondSolo.id("textures/effect/glow.png");
    private static final int FORMS = LightConstruct.Form.values().length;
    private static final ResourceLocation[] GEO = new ResourceLocation[FORMS];
    private static final ResourceLocation[] TEX = new ResourceLocation[FORMS];
    private static final ResourceLocation[] LIGHT = new ResourceLocation[FORMS];
    private static final ResourceLocation[] ANIM = new ResourceLocation[FORMS];
    /** How high the sun stands over the spot it rose from. */
    static final float SUN_HEIGHT = 9f;

    static {
        for (LightConstruct.Form f : LightConstruct.Form.values()) {
            String n = "light_" + f.id();
            GEO[f.ordinal()] = BeyondSolo.id("geo/entity/" + n + ".geo.json");
            TEX[f.ordinal()] = BeyondSolo.id("textures/entity/" + n + "_main.png");
            LIGHT[f.ordinal()] = BeyondSolo.id("textures/entity/" + n + "_main_glowmask.png");
            ANIM[f.ordinal()] = BeyondSolo.id("animations/entity/" + n + ".animation.json");
        }
    }

    public LightConstructRenderer(EntityRendererProvider.Context context) {
        super(context, new GeoModel<>() {
            @Override
            public ResourceLocation getModelResource(LightConstruct c) {
                return GEO[c.form().ordinal()];
            }

            @Override
            public ResourceLocation getTextureResource(LightConstruct c) {
                return TEX[c.form().ordinal()];
            }

            @Override
            public ResourceLocation getAnimationResource(LightConstruct c) {
                return ANIM[c.form().ordinal()];
            }
        });
        addRenderLayer(new Burn(this));
        this.shadowRadius = 0;
    }

    @Override
    public RenderType getRenderType(LightConstruct c, ResourceLocation texture, MultiBufferSource buffers, float partial) {
        return RenderType.entityTranslucentEmissive(texture);
    }

    @Override
    public Color getRenderColor(LightConstruct c, float partial, int light) {
        int rgb = GateFx.mix(c.color(), 0xFFFFFF, 0.5f);
        return Color.ofARGB(0.9f * c.alpha(partial), ((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
    }

    /** How far over its spot it is this frame. */
    static float lift(LightConstruct c, float partial) {
        float k = c.landing(partial);
        LightConstruct.Form f = c.form();
        return switch (f) {
            // let down slowly, then still
            case HAND -> f.drop * (1 - k) * (1 - k);
            // rising over the fight
            case SUN -> SUN_HEIGHT * (0.25f + 0.75f * outCubic(Mth.clamp(c.age(partial) / 24f, 0, 1)));
            case CHOIR -> 0;
            // hurled down point first: faster and faster
            default -> f.drop * (1 - k * k);
        };
    }

    @Override
    public void render(LightConstruct c, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float a = c.alpha(partial);
        if (a <= 0.01f) return;
        Entity owner = c.owner();
        pose.pushPose();
        if (owner != null) {
            // stand at its ruler's back this very frame, turned with them
            Vec3 d = owner.getPosition(partial).subtract(c.getPosition(partial));
            pose.translate(d.x, d.y, d.z);
            if (owner instanceof LivingEntity le) yaw = Mth.rotLerp(partial, le.yBodyRotO, le.yBodyRot);
        }
        float y = lift(c, partial);
        pose.translate(0, y, 0);
        float s = c.scale();
        pose.scale(s, s, s);
        super.render(c, yaw, partial, pose, buffers, LightTexture.FULL_BRIGHT);
        bloom(c, partial, pose, buffers, a);
        pose.popPose();
    }

    /** Soft light round it: where it burns brightest, and on the ground it strikes. */
    private static void bloom(LightConstruct c, float partial, PoseStack pose, MultiBufferSource buffers, float a) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        VertexConsumer glow = buffers.getBuffer(ModRenderTypes.lightTexture(GLOW));
        int col = c.color();
        int white = GateFx.mix(col, 0xFFFFFF, 0.6f);
        float t = c.age(partial);
        float pulse = 0.85f + 0.15f * Mth.sin(t * 0.3f);
        float landed = c.landing(partial) >= 1 ? 1 : 0;
        float impact = landed * Math.max(0, 1 - (t - c.fall()) / 10f);
        switch (c.form()) {
            case SPEAR, FEATHER -> {
                GateFx.dot(glow, pose, camera, 0, 1.2f, 0, 1.4f, col, 0.35f * a * pulse);
                if (landed > 0) GateFx.dot(glow, pose, camera, 0, 0.15f, 0, 1.6f + 2.5f * impact, white, (0.3f + 0.6f * impact) * a);
            }
            case BLADE -> {
                GateFx.dot(glow, pose, camera, 0, 6f, 0, 5f, col, 0.3f * a * pulse);
                GateFx.dot(glow, pose, camera, 0, 11f, 0, 3f, white, 0.35f * a * pulse);
                if (landed > 0) GateFx.dot(glow, pose, camera, 0, 0.3f, 0, 5f + 8f * impact, white, (0.35f + 0.6f * impact) * a);
            }
            case HAND -> {
                GateFx.dot(glow, pose, camera, 0, 3f, 0, 5f, col, 0.3f * a * pulse);
                if (landed > 0) GateFx.dot(glow, pose, camera, 0, 0.3f, 0, 4f + 5f * impact, white, (0.3f + 0.5f * impact) * a);
            }
            case SUN -> {
                GateFx.dot(glow, pose, camera, 0, 0, 0, 6f * pulse, white, 0.55f * a);
                GateFx.dot(glow, pose, camera, 0, 0, 0, 13f, col, 0.3f * a);
                GateFx.dot(glow, pose, camera, 0, 0, 0, 24f, col, 0.12f * a);
            }
            case CHOIR -> GateFx.dot(glow, pose, camera, 0, 1.4f, 0.4f, 3.2f, col, 0.25f * a * pulse);
        }
    }

    private static float outCubic(float t) {
        float u = 1 - t;
        return 1 - u * u * u;
    }

    /** Its lines and edges burning on top of it: the glow mask added as light, fading with it. */
    private static final class Burn extends GeoRenderLayer<LightConstruct> {
        Burn(LightConstructRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack pose, LightConstruct c, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers, VertexConsumer buffer,
                           float partial, int light, int overlay) {
            float a = c.alpha(partial);
            int rgb = GateFx.mix(c.color(), 0xFFFFFF, 0.35f);
            int argb = 0xFF000000 | Math.round(((rgb >> 16) & 255) * a) << 16 | Math.round(((rgb >> 8) & 255) * a) << 8 | Math.round((rgb & 255) * a);
            RenderType eyes = RenderType.eyes(LIGHT[c.form().ordinal()]);
            getRenderer().reRender(model, pose, buffers, c, eyes, buffers.getBuffer(eyes), partial, LightTexture.FULL_BRIGHT, overlay, argb);
        }
    }
}
