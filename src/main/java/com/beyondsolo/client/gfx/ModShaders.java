package com.beyondsolo.client.gfx;

import com.beyondsolo.BeyondSolo;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/** Loads the shader behind every rounded, glowing shape in the System UI. */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class ModShaders {
    private static ShaderInstance shape;

    @SubscribeEvent
    static void register(RegisterShadersEvent event) {
        shape = null;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), BeyondSolo.id("shape"), DefaultVertexFormat.POSITION_TEX_COLOR),
                    loaded -> shape = loaded);
        } catch (Exception e) {
            BeyondSolo.LOGGER.error("Could not load the System UI shader. The UI falls back to plain rectangles.", e);
        }
    }

    /** Null if the shader failed to load. */
    public static ShaderInstance shape() {
        return shape;
    }

    private ModShaders() {}
}
