package com.beyondsolo.client.render;

import com.beyondsolo.client.gfx.ModShaders;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.function.Function;

/** Render types for the mod's world effects. */
public final class ModRenderTypes {
    /** Flat-coloured, see-through triangles: the faces of a crystal. Draw far faces first. */
    public static final RenderType CRYSTAL = RenderType.create("beyondsolo_crystal",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    /** Flat-coloured triangles added to the scene as light: glows and halos. */
    public static final RenderType CRYSTAL_GLOW = RenderType.create("beyondsolo_crystal_glow",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    private static final Function<ResourceLocation, RenderType> LIGHT_TEXTURE = Util.memoize(texture -> RenderType.create("beyondsolo_light_texture",
            DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(ModShaders::light))
                    .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false)));

    /** A white-on-transparent texture added to the scene as light and tinted by the vertex colour: magic circles, soft glows. */
    public static RenderType lightTexture(ResourceLocation texture) {
        return LIGHT_TEXTURE.apply(texture);
    }

    private static final Function<ResourceLocation, RenderType> SOFT_TEXTURE = Util.memoize(texture -> RenderType.create("beyondsolo_soft_texture",
            DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(ModShaders::light))
                    .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false)));

    /**
     * Like {@link #lightTexture}, but blended over the scene instead of added to it: smoke and dust, which hide what
     * is behind them rather than light it. The texture's colour is kept (tinted by the vertex colour).
     */
    public static RenderType softTexture(ResourceLocation texture) {
        return SOFT_TEXTURE.apply(texture);
    }

    /** Premultiplied alpha: the colour is added as it is, and alpha says how much of what is behind is hidden. */
    private static final RenderStateShard.TransparencyStateShard PREMULTIPLIED = new RenderStateShard.TransparencyStateShard("beyondsolo_premultiplied", () -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }, () -> {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    });

    private static final Function<ResourceLocation, RenderType> ENERGY = Util.memoize(texture -> RenderType.create("beyondsolo_energy",
            DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(ModShaders::energy))
                    .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
                    .setTransparencyState(PREMULTIPLIED)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false)));

    /**
     * The skin of a skill effect (a blade, a beam, an orb): the texture's red channel is heat, light that runs from the
     * vertex colour's hue to white where it is hottest, and its alpha is cover, a body of the vertex colour that hides
     * what is behind it. A dark vertex colour makes dark energy with bright edges. See {@code shaders/core/energy.fsh}.
     */
    public static RenderType energy(ResourceLocation texture) {
        return ENERGY.apply(texture);
    }

    /**
     * Every fragment passes the depth test, whether the test is on or off. Vanilla's {@code NO_DEPTH_TEST} only skips
     * turning the test on, and the particle engine leaves it on, so at {@code AFTER_PARTICLES} (where the world effects
     * draw) a "no depth test" type was still hidden behind every block in front of it.
     */
    private static final RenderStateShard.LayeringStateShard PASS_DEPTH = new RenderStateShard.LayeringStateShard("beyondsolo_pass_depth",
            () -> RenderSystem.depthFunc(GL11.GL_ALWAYS), () -> RenderSystem.depthFunc(GL11.GL_LEQUAL));

    private static final Function<ResourceLocation, RenderType> LIGHT_THROUGH =Util.memoize(texture -> RenderType.create("beyondsolo_light_through",
            DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(ModShaders::light))
                    .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setLayeringState(PASS_DEPTH)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false)));

    /** Like {@link #lightTexture}, but seen through everything: what Pulse Scan shows inside the rock. */
    public static RenderType lightThroughWalls(ResourceLocation texture) {
        return LIGHT_THROUGH.apply(texture);
    }

    // Armor is drawn a hair toward the camera (view-offset layering), so anything laid over it must be too, or the
    // plate hides it. These are the eyes and energy-swirl looks with that same offset, and like the enchantment glint
    // they only draw where the plate itself was drawn (equal depth): not on the see-through parts of a wing or a crest.
    private static final Function<ResourceLocation, RenderType> ARMOR_GLOW = Util.memoize(texture -> RenderType.create("beyondsolo_armor_glow",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_EYES_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                    .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.EQUAL_DEPTH_TEST)
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .createCompositeState(false)));

    /** A black-and-white mask added to worn armor as full-bright light, tinted by the vertex colour: monarch armor's burning seams. */
    public static RenderType armorGlow(ResourceLocation texture) {
        return ARMOR_GLOW.apply(texture);
    }

    /** A texture scrolled by (u, v) and added to worn armor as light: the current running over monarch armor. */
    public static RenderType armorCurrent(ResourceLocation texture, float u, float v) {
        return RenderType.create("beyondsolo_armor_current", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, true,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_ENERGY_SWIRL_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setTexturingState(new RenderStateShard.OffsetTexturingStateShard(u, v))
                        .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setDepthTestState(RenderStateShard.EQUAL_DEPTH_TEST)
                        .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                        .createCompositeState(false));
    }

    private ModRenderTypes() {}
}
