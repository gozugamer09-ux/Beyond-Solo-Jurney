package com.beyondsolo.client.monarch;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.awakening.ArbiterEntity;
import com.beyondsolo.client.awakening.ArbiterRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * The Arbiter Unchained: the awakening's giant on the same bones, its stone gone dark and its seams, halo and glare
 * burning violet (crimson once enraged). Its glow mask is {@code throne_arbiter_glowmask.png}.
 */
public class ThroneArbiterRenderer extends ArbiterRenderer {
    private static final ResourceLocation TEXTURE = BeyondSolo.id("textures/entity/throne_arbiter.png");

    public ThroneArbiterRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<ArbiterEntity>(BeyondSolo.id("arbiter"), false) {
            @Override
            public ResourceLocation getTextureResource(ArbiterEntity arbiter) {
                return TEXTURE;
            }
        });
    }

    @Override
    protected int calm() {
        return 0xB57BFF;
    }

    @Override
    protected int angry() {
        return 0xFF2E6A;
    }
}
