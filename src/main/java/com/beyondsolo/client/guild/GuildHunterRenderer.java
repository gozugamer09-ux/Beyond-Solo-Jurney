package com.beyondsolo.client.guild;

import com.beyondsolo.guild.GuildHunter;
import com.beyondsolo.guild.GuildHunterEntity;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * A guild's hired hunter: a person in their class's armour with their class's weapon, one of the game's own player
 * faces (so a raid looks like a crowd of hunters, not of creatures), the guild's tag over their head.
 */
public class GuildHunterRenderer extends HumanoidMobRenderer<GuildHunterEntity, PlayerModel<GuildHunterEntity>> {
    private static final ResourceLocation[] SKINS = new ResourceLocation[GuildHunter.SKINS.length];

    static {
        for (int i = 0; i < SKINS.length; i++) {
            SKINS[i] = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/" + GuildHunter.SKINS[i] + ".png");
        }
    }

    public GuildHunterRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(GuildHunterEntity entity) {
        return SKINS[Math.floorMod(entity.skin(), SKINS.length)];
    }
}
