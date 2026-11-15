package com.beyondsolo.client.enemy;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.enemy.GateMonsterEntity;
import com.beyondsolo.enemy.MonsterKind;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/** GeckoLib models of the roster (made by tools/gen_enemy_assets.py): the gate's creatures and every boss kind. */
public final class EnemyModels {
    /** Turns the "head" bone with where the creature looks, as GeckoLib's defaulted entity models do. */
    static <T extends GeoAnimatable> void turnHead(GeoModel<T> model, AnimationState<T> state, float pitchScale) {
        GeoBone head = model.getAnimationProcessor().getBone("head");
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (head == null || data == null) return;
        // Add to this frame's animated pose when an animation moved the head; otherwise start from the rest pose.
        // (Adding to an unanimated head stacks frame after frame through GeckoLib's reset blend: the head spun.)
        float baseX = head.hasRotationChanged() ? head.getRotX() : head.getInitialSnapshot().getRotX();
        float baseY = head.hasRotationChanged() ? head.getRotY() : head.getInitialSnapshot().getRotY();
        float pitch = Mth.clamp(data.headPitch(), -30f, 30f) * pitchScale;
        float yaw = Mth.clamp(data.netHeadYaw(), -50f, 50f) * 0.7f;
        head.setRotX(baseX + pitch * Mth.DEG_TO_RAD);
        head.setRotY(baseY + yaw * Mth.DEG_TO_RAD);
        head.markRotationAsChanged();
    }

    /** One of the gate's creatures; its texture follows its affinity. */
    public static final class Monster extends GeoModel<GateMonsterEntity> {
        private final MonsterKind kind;
        private final ResourceLocation model;
        private final ResourceLocation animation;
        private final ResourceLocation[] textures;

        public Monster(MonsterKind kind) {
            this.kind = kind;
            this.model = BeyondSolo.id("geo/entity/" + kind.id + ".geo.json");
            this.animation = BeyondSolo.id("animations/entity/" + kind.id + ".animation.json");
            var all = com.beyondsolo.enemy.Affinity.values();
            this.textures = new ResourceLocation[all.length];
            for (var a : all) textures[a.ordinal()] = BeyondSolo.id("textures/entity/" + kind.id + "_" + a.texture + ".png");
        }

        @Override
        public ResourceLocation getModelResource(GateMonsterEntity e) {
            return model;
        }

        @Override
        public ResourceLocation getTextureResource(GateMonsterEntity e) {
            return textures[e.affinity().ordinal()];
        }

        @Override
        public ResourceLocation getAnimationResource(GateMonsterEntity e) {
            return animation;
        }

        @Override
        public void setCustomAnimations(GateMonsterEntity e, long instanceId, AnimationState<GateMonsterEntity> state) {
            super.setCustomAnimations(e, instanceId, state);
            if (!e.isDeadOrDying()) turnHead(this, state, kind == MonsterKind.EMBERWISP ? 0.5f : 1f);
        }
    }

    /** A gate boss: the model, animations and rank texture of its kind. */
    public static final class Boss extends GeoModel<GateBossEntity> {
        private static final ResourceLocation[][] TEXTURES = new ResourceLocation[BossKind.values().length][PlayerRank.values().length];

        static {
            for (BossKind k : BossKind.values()) {
                for (PlayerRank r : PlayerRank.values()) {
                    TEXTURES[k.ordinal()][r.ordinal()] = BeyondSolo.id("textures/entity/" + k.model + "_" + r.name().toLowerCase() + ".png");
                }
            }
        }

        @Override
        public ResourceLocation getModelResource(GateBossEntity b) {
            return BeyondSolo.id("geo/entity/" + b.kind().model + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(GateBossEntity b) {
            return TEXTURES[b.kind().ordinal()][b.rank().ordinal()];
        }

        @Override
        public ResourceLocation getAnimationResource(GateBossEntity b) {
            return BeyondSolo.id("animations/entity/" + b.kind().model + ".animation.json");
        }

        @Override
        public void setCustomAnimations(GateBossEntity b, long instanceId, AnimationState<GateBossEntity> state) {
            super.setCustomAnimations(b, instanceId, state);
            if (b.isAwake() && b.introTicks() <= 0 && !b.isDeadOrDying()) turnHead(this, state, 0.6f);
        }
    }

    private EnemyModels() {}
}
