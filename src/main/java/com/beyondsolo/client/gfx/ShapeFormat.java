package com.beyondsolo.client.gfx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteOrder;
import java.util.function.Function;

/**
 * How {@link Shapes} reach the graphics card: every corner of a shape carries the shape's own settings (size, mode,
 * colours, border, glow...), so shapes that follow each other share one draw call instead of costing one each, which
 * is what made pages with many slots or cards slow. The shader is {@code shaders/core/shape.*}.
 */
public final class ShapeFormat {
    // The extra vertex elements take free ids from the top of the 32 the game allows, away from the low ids that
    // vanilla and other mods hand out.
    public static final VertexFormatElement RECT = element(VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC);
    public static final VertexFormatElement PARAMS = element(VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC);
    public static final VertexFormatElement EXTRA = element(VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC);
    public static final VertexFormatElement FILL_A = element(VertexFormatElement.Type.UBYTE, VertexFormatElement.Usage.COLOR);
    public static final VertexFormatElement FILL_B = element(VertexFormatElement.Type.UBYTE, VertexFormatElement.Usage.COLOR);
    public static final VertexFormatElement BORDER_A = element(VertexFormatElement.Type.UBYTE, VertexFormatElement.Usage.COLOR);
    public static final VertexFormatElement BORDER_B = element(VertexFormatElement.Type.UBYTE, VertexFormatElement.Usage.COLOR);
    public static final VertexFormatElement GLOW = element(VertexFormatElement.Type.UBYTE, VertexFormatElement.Usage.COLOR);

    /** The names are the shader's input names. */
    public static final VertexFormat FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("UV0", VertexFormatElement.UV0)
            .add("Color", VertexFormatElement.COLOR)
            .add("Rect", RECT)
            .add("Params", PARAMS)
            .add("Extra", EXTRA)
            .add("FillA", FILL_A)
            .add("FillB", FILL_B)
            .add("BorderA", BORDER_A)
            .add("BorderB", BORDER_B)
            .add("GlowColor", GLOW)
            .build();

    /**
     * How the shapes always blended: over what is behind, keeping the colour's own alpha. Like the shapes always did,
     * they also draw over everything without the depth test, and leave blending on and the depth test off behind them.
     */
    private static final RenderStateShard.TransparencyStateShard BLEND = new RenderStateShard.TransparencyStateShard("beyondsolo_shape_blend", () -> {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
    }, RenderSystem::defaultBlendFunc);

    /** Light added to what is behind (flares, energy). */
    private static final RenderStateShard.TransparencyStateShard ADD = new RenderStateShard.TransparencyStateShard("beyondsolo_shape_add", () -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableDepthTest();
    }, RenderSystem::defaultBlendFunc);

    private static final RenderType PLAIN = type("beyondsolo_shape", RenderStateShard.NO_TEXTURE, BLEND);
    private static final RenderType PLAIN_ADD = type("beyondsolo_shape_add", RenderStateShard.NO_TEXTURE, ADD);
    private static final Function<ResourceLocation, RenderType> ICON = Util.memoize(texture ->
            type("beyondsolo_shape_icon", new RenderStateShard.TextureStateShard(texture, false, false), BLEND));
    private static final Function<ResourceLocation, RenderType> ICON_ADD = Util.memoize(texture ->
            type("beyondsolo_shape_icon_add", new RenderStateShard.TextureStateShard(texture, false, false), ADD));

    /** The render type for a shape: a plain one, or one per icon texture; blended over or added as light. */
    static RenderType type(ResourceLocation texture, boolean additive) {
        if (texture == null) return additive ? PLAIN_ADD : PLAIN;
        return (additive ? ICON_ADD : ICON).apply(texture);
    }

    private static RenderType type(String name, RenderStateShard.EmptyTextureStateShard texture, RenderStateShard.TransparencyStateShard blend) {
        return RenderType.create(name, FORMAT, VertexFormat.Mode.QUADS, 4096, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(ModShaders::shape))
                        .setTextureState(texture)
                        .setTransparencyState(blend)
                        .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setCullState(RenderStateShard.NO_CULL)
                        .createCompositeState(false));
    }

    private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    static void put(BufferBuilder out, VertexFormatElement element, float a, float b, float c, float d) {
        long at = out.beginElement(element);
        if (at == -1L) return;
        MemoryUtil.memPutFloat(at, a);
        MemoryUtil.memPutFloat(at + 4L, b);
        MemoryUtil.memPutFloat(at + 8L, c);
        MemoryUtil.memPutFloat(at + 12L, d);
    }

    /** An ARGB colour, stored as the game stores vertex colours (red first). */
    static void putColor(BufferBuilder out, VertexFormatElement element, int argb) {
        long at = out.beginElement(element);
        if (at == -1L) return;
        int abgr = FastColor.ABGR32.fromArgb32(argb);
        MemoryUtil.memPutInt(at, LITTLE_ENDIAN ? abgr : Integer.reverseBytes(abgr));
    }

    /** Thrown out of the class's set-up (and caught by {@link ModShaders}, which then leaves the UI on plain boxes). */
    private static VertexFormatElement element(VertexFormatElement.Type type, VertexFormatElement.Usage usage) {
        for (int id = VertexFormatElement.MAX_COUNT - 1; id >= 0; id--) {
            if (VertexFormatElement.byId(id) == null) return VertexFormatElement.register(id, 0, type, usage, 4);
        }
        throw new IllegalStateException("No free vertex element id left for the System UI shapes");
    }

    private ShapeFormat() {}
}
