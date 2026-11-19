package com.beyondsolo.client.ruler;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.fx.Aura;
import com.beyondsolo.client.fx.Outline;
import com.beyondsolo.ruler.HeraldEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * A herald of light (made by tools/gen_ruler_assets.py): its model with its light glowing, a little flame of light
 * burning off its outline (a ruler's, smaller: see {@link Aura}), and as it dies, its light going out of it in glints
 * rising off it. It falls to its knees with its own animation.
 */
public class HeraldRenderer extends GeoEntityRenderer<HeraldEntity> {
    private static final int LIGHT = 0xFFE7A0;
    private static final Aura.Style STYLE = new Aura.Style().hue(LIGHT).light().rim(0.35f, 0.04f).seams(0.2f).flame(0.14f).crest(1.4f).glow(0.35f)
            .lean(0.3f);
    /** Its own outline: a shadow's stand-in may be drawing this very model into the shared one. */
    private final Outline outline = Outline.create();

    public HeraldRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<HeraldEntity>(BeyondSolo.id("ruler_herald"), true) {
            private final ResourceLocation texture = BeyondSolo.id("textures/entity/ruler_herald_main.png");

            @Override
            public ResourceLocation getTextureResource(HeraldEntity herald) {
                return texture;
            }
        });
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.6f;
    }

    @Override
    protected float getDeathMaxRotation(HeraldEntity herald) {
        // it falls to its knees with its own animation
        return 0f;
    }

    @Override
    public void render(HeraldEntity herald, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        // only the herald itself burns: a shadow's stand-in drawn with this renderer is ink and nothing more
        boolean real = herald.level().getEntity(herald.getId()) == herald;
        boolean burns = real && !herald.isInvisible();
        MultiBufferSource drawn = buffers;
        if (burns) {
            RenderType body = getRenderType(herald, getTextureLocation(herald), buffers, partial);
            drawn = outline.reset().tap(buffers, type -> type == body);
        }
        super.render(herald, yaw, partial, pose, drawn, light);
        if (!real) return;
        float time = (herald.tickCount + partial) % 7200;
        float h = herald.getBbHeight();
        float dying = herald.deathTime > 0 ? Mth.clamp((herald.deathTime + partial) / 20f, 0f, 1f) : 0f;
        if (burns && outline.quads() > 0) Aura.burnWorld(outline, buffers, STYLE, herald, partial, time, 1 - dying, Math.max(0.6f, h / 1.9f), light);
        if (dying > 0) {
            for (int i = 0; i < 6; i++) {
                float u = (dying + i / 6f) % 1f;
                float ang = i * 2.1f + time * 0.05f;
                Aura.glint(pose, buffers, Mth.cos(ang) * 0.4f, h * (0.2f + 0.9f * u), Mth.sin(ang) * 0.4f, 0.4f, LIGHT, 0.9f * (1 - u) * dying, i);
            }
        }
    }
}
