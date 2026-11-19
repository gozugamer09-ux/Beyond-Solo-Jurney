package com.beyondsolo.client.light;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.gate.GateFx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedGeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.Color;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.util.RenderUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * Seraph form on a hunter (made by tools/gen_light_assets.py, "light_seraph"): two great wings of light at their
 * shoulder blades, beating slowly, and a halo of light turning over their head. They open out of nothing as the form is
 * taken and fold away when it ends; drawn for everyone who can see the ruler (their own screen shows the light at the
 * edges of sight instead, {@link LightHud}).
 */
public class SeraphLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final Map<Integer, Form> FORMS = new HashMap<>();
    private static Renderer renderer;

    public SeraphLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    static void clear() {
        FORMS.clear();
    }

    private static Renderer renderer() {
        if (renderer == null) renderer = new Renderer();
        return renderer;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partial, float ageInTicks, float netHeadYaw, float headPitch) {
        LightClient.Glow g = LightClient.seraphOf(player.getId());
        if (g == null) {
            if (!FORMS.isEmpty()) FORMS.remove(player.getId());
            return;
        }
        if (player.isInvisible()) return;
        float k = g.strength(LightClient.now(partial));
        if (k <= 0.01f) return;
        Form form = FORMS.computeIfAbsent(player.getId(), id -> new Form());
        form.color = g.color;
        form.strength = k;
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        // the body is drawn upside down and mirrored: back to the world's way up, the model's front still forward
        pose.scale(-1, -1, 1);
        float s = 0.3f + 0.7f * outBack(k);
        pose.scale(s, s, s);
        renderer().render(pose, form, buffers, null, null, LightTexture.FULL_BRIGHT, partial);
        pose.popPose();
    }

    private static float outBack(float t) {
        float c1 = 1.70158f, c3 = c1 + 1;
        float u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }

    /** One hunter's wings and halo: their colour, how strongly they show, and their own place in the beat. */
    static final class Form implements GeoAnimatable {
        private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.beyondsolo.light_seraph.idle");
        int color = 0xFFE9A8;
        float strength;
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
            controllers.add(new AnimationController<>(this, "main", 0, state -> state.setAndContinue(IDLE)));
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }

        @Override
        public double getTick(Object object) {
            return RenderUtil.getCurrentTick();
        }
    }

    /** The wings' renderer: a body of light half seen through, its feathers' shafts and edges burning on top. */
    static final class Renderer extends GeoObjectRenderer<Form> {
        private static final ResourceLocation TEXTURE = BeyondSolo.id("textures/entity/light_seraph_main.png");
        private static final ResourceLocation LIGHT = BeyondSolo.id("textures/entity/light_seraph_main_glowmask.png");

        Renderer() {
            super(new DefaultedGeoModel<>(BeyondSolo.id("light_seraph")) {
                @Override
                protected String subtype() {
                    return "entity";
                }

                @Override
                public ResourceLocation getTextureResource(Form form) {
                    return TEXTURE;
                }
            });
            addRenderLayer(new GeoRenderLayer<>(this) {
                @Override
                public void render(PoseStack pose, Form form, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers, VertexConsumer buffer,
                                   float partial, int light, int overlay) {
                    int rgb = GateFx.mix(form.color, 0xFFFFFF, 0.35f);
                    float a = form.strength;
                    int argb = 0xFF000000 | Math.round(((rgb >> 16) & 255) * a) << 16 | Math.round(((rgb >> 8) & 255) * a) << 8 | Math.round((rgb & 255) * a);
                    RenderType eyes = RenderType.eyes(LIGHT);
                    getRenderer().reRender(model, pose, buffers, form, eyes, buffers.getBuffer(eyes), partial, LightTexture.FULL_BRIGHT, overlay, argb);
                }
            });
        }

        /** Drawn where the layer put it (on the hunter's back), not in the middle of a block as an object would be. */
        @Override
        public void preRender(PoseStack pose, Form form, BakedGeoModel model, MultiBufferSource buffers, VertexConsumer buffer, boolean isReRender,
                              float partial, int light, int overlay, int colour) {
            this.objectRenderTranslations = new Matrix4f(pose.last().pose());
            scaleModelForRender(this.scaleWidth, this.scaleHeight, pose, form, model, isReRender, partial, light, overlay);
        }

        @Override
        public RenderType getRenderType(Form form, ResourceLocation texture, MultiBufferSource buffers, float partial) {
            return RenderType.entityTranslucentEmissive(texture);
        }

        @Override
        public Color getRenderColor(Form form, float partial, int light) {
            int rgb = GateFx.mix(form.color, 0xFFFFFF, 0.55f);
            return Color.ofARGB(0.85f * form.strength, ((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
        }
    }
}
