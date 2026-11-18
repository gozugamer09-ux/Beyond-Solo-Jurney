package com.beyondsolo.client.gfx;

import com.beyondsolo.BeyondSolo;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/**
 * Loads the mod's shaders: the one behind every rounded, glowing shape in the System UI, the one for light in the world
 * and the one for the skins of skill effects.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class ModShaders {
    private static ShaderInstance shape;
    private static ShaderInstance light;
    private static ShaderInstance energy;

    @SubscribeEvent
    static void register(RegisterShadersEvent event) {
        shape = null;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), BeyondSolo.id("shape"), ShapeFormat.FORMAT),
                    loaded -> shape = loaded);
        } catch (Exception | LinkageError e) {
            // LinkageError: the shapes' vertex format could not be set up (see ShapeFormat)
            BeyondSolo.LOGGER.error("Could not load the System UI shader. The UI falls back to plain rectangles.", e);
        }
        light = null;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), BeyondSolo.id("light"), DefaultVertexFormat.POSITION_TEX_COLOR),
                    loaded -> light = loaded);
        } catch (Exception e) {
            BeyondSolo.LOGGER.error("Could not load the world light shader. Glows fall back to the vanilla shader and look harder.", e);
        }
        energy = null;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), BeyondSolo.id("energy"), DefaultVertexFormat.POSITION_TEX_COLOR),
                    loaded -> energy = loaded);
        } catch (Exception e) {
            BeyondSolo.LOGGER.error("Could not load the skill skin shader. Skill effects fall back to plain light.", e);
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

    /**
     * The shader for the skins of skill effects (blades, beams, orbs, shells): heat and cover in one texture, drawn
     * with premultiplied alpha. Falls back to {@link #light()} if it failed to load.
     */
    public static ShaderInstance energy() {
        return energy != null ? energy : light();
    }

    private ModShaders() {}
}
