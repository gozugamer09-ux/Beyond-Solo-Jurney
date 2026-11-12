package com.beyondsolo.client.render;

import com.beyondsolo.client.gfx.ModShaders;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

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

    private ModRenderTypes() {}
}
