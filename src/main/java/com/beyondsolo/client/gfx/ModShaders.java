package com.beyondsolo.client.gfx;

import com.beyondsolo.BeyondSolo;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/** Loads the mod's shaders: the one behind every rounded, glowing shape in the System UI, and the one for light in the world. */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class ModShaders {
    private static ShaderInstance shape;
    private static ShaderInstance light;

    @SubscribeEvent
    static void register(RegisterShadersEvent event) {
        shape = null;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), BeyondSolo.id("shape"), DefaultVertexFormat.POSITION_TEX_COLOR),
                    loaded -> shape = loaded);
        } catch (Exception e) {
            BeyondSolo.LOGGER.error("Could not load the System UI shader. The UI falls back to plain rectangles.", e);
        }
        light = null;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), BeyondSolo.id("light"), DefaultVertexFormat.POSITION_TEX_COLOR),
                    loaded -> light = loaded);
        } catch (Exception e) {
            BeyondSolo.LOGGER.error("Could not load the world light shader. Glows fall back to the vanilla shader and look harder.", e);
        }
    }

    /** Null if the shader failed to load. */
    public static ShaderInstance shape() {
        return shape;
    }

    /** The shader for glows and magic circles in the world. Falls back to a vanilla shader if ours failed to load. */
    public static ShaderInstance light() {
        return light != null ? light : GameRenderer.getPositionTexColorShader();
    }

    private ModShaders() {}
}
