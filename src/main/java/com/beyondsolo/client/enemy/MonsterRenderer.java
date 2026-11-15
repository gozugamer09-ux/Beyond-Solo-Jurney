package com.beyondsolo.client.enemy;

import com.beyondsolo.enemy.GateMonsterEntity;
import com.beyondsolo.enemy.MonsterKind;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** One of the gate's creatures: its GeckoLib model with glowing eyes and crystals; a bomber swells as its fuse burns. */
public class MonsterRenderer extends GeoEntityRenderer<GateMonsterEntity> {
    public MonsterRenderer(EntityRendererProvider.Context context, MonsterKind kind) {
        super(context, new EnemyModels.Monster(kind));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = kind.width * 0.55f;
    }

    @Override
    protected float getDeathMaxRotation(GateMonsterEntity e) {
        // each falls with its own death animation
        return 0f;
    }

    @Override
    public void render(GateMonsterEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(e, yaw, partial, pose, buffers, light);
    }
}
