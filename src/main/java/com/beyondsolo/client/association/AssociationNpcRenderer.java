package com.beyondsolo.client.association;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.association.AssociationNpc;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * The association's officer and the gate broker: a person in a player's shape, the officer in the association's
 * navy with the gold badge, the broker in a long dark coat and a scarf. The skins come from
 * {@code tools/gen_association_assets.py}; the name plate is drawn by {@link AssociationMarkers}.
 */
public class AssociationNpcRenderer extends HumanoidMobRenderer<AssociationNpc, PlayerModel<AssociationNpc>> {
    private static final ResourceLocation[] OFFICER = new ResourceLocation[AssociationNpc.OFFICER_LOOKS];
    private static final ResourceLocation[] BROKER = new ResourceLocation[AssociationNpc.BROKER_LOOKS];

    static {
        for (int i = 0; i < OFFICER.length; i++) OFFICER[i] = BeyondSolo.id("textures/entity/association/officer_" + i + ".png");
        for (int i = 0; i < BROKER.length; i++) BROKER[i] = BeyondSolo.id("textures/entity/association/broker_" + i + ".png");
    }

    public AssociationNpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    protected boolean shouldShowName(AssociationNpc entity) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(AssociationNpc entity) {
        ResourceLocation[] looks = entity.role() == AssociationNpc.Role.BROKER ? BROKER : OFFICER;
        return looks[Math.floorMod(entity.look(), looks.length)];
    }
}
