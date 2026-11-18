package com.beyondsolo.client.worldboss;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.worldboss.ColossalBoss;
import com.beyondsolo.worldboss.WorldBossKind;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * A world boss's or a monarch's GeckoLib model (made by tools/gen_worldboss_assets.py). The Walking Mountain's skin runs
 * hotter in its last phase (its cracks pour magma).
 */
public class WorldBossModel<T extends ColossalBoss> extends GeoModel<T> {
    private final WorldBossKind kind;
    private final ResourceLocation model;
    private final ResourceLocation animation;
    private final ResourceLocation texture;
    private final ResourceLocation hot;

    public WorldBossModel(WorldBossKind kind) {
        this.kind = kind;
        String name = kind.asset();
        this.model = BeyondSolo.id("geo/entity/" + name + ".geo.json");
        this.animation = BeyondSolo.id("animations/entity/" + name + ".animation.json");
        this.texture = BeyondSolo.id("textures/entity/" + name + "_main.png");
        this.hot = BeyondSolo.id("textures/entity/" + name + "_molten.png");
    }

    @Override
    public ResourceLocation getModelResource(T boss) {
        return model;
    }

    @Override
    public ResourceLocation getTextureResource(T boss) {
        return kind == WorldBossKind.TITAN && boss.phase() >= 3 ? hot : texture;
    }

    @Override
    public ResourceLocation getAnimationResource(T boss) {
        return animation;
    }

    /**
     * Turns the "head" bone with where it looks, on top of whatever its animation does with the head (see
     * {@code EnemyModels.turnHead}: the bone's "changed" mark is cleared so a head no animation moves doesn't keep
     * last frame's turn and spin).
     */
    @Override
    public void setCustomAnimations(T boss, long instanceId, AnimationState<T> state) {
        super.setCustomAnimations(boss, instanceId, state);
        if (boss.isDeadOrDying() || boss.currentMove() != null || boss.shifting() || boss.introTicks() > 0 || boss.leaving()) return;
        GeoBone head = getAnimationProcessor().getBone("head");
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (head == null || data == null) return;
        float pitch = Mth.clamp(data.headPitch(), -25f, 25f) * 0.6f;
        float yaw = Mth.clamp(Mth.wrapDegrees(data.netHeadYaw()), -40f, 40f) * 0.6f;
        head.setRotX(head.getRotX() + pitch * Mth.DEG_TO_RAD);
        head.setRotY(head.getRotY() + yaw * Mth.DEG_TO_RAD);
        head.resetStateChanges();
    }
}
