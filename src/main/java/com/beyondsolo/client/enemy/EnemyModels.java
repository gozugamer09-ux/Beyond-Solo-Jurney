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
    /**
     * Turns the "head" bone with where the creature looks, on top of whatever its animation is doing with the head
     * (a howl, a roar, the wyrm's neck thrust), as GeckoLib's defaulted entity models do.
     *
     * <p>This runs from {@code setCustomAnimations}, right after GeckoLib has put this frame's pose on the bones:
     * the animated rotation where the running animation moves the head, otherwise its blend back to the rest pose.
     * Either way the value is fresh, so the look is simply added to it. What must not be left behind is the
     * "changed" mark that setting a rotation puts on the bone: GeckoLib skips its blend back to rest for a marked
     * bone on the next frame, so a head that no animation is moving would keep last frame's value, get the look
     * added again, and spin (faster the more creatures share the model, since they share its bones). Clearing the
     * mark keeps every frame starting from a fresh pose.
     */
    static <T extends GeoAnimatable> void turnHead(GeoModel<T> model, AnimationState<T> state, float pitchScale) {
        GeoBone head = model.getAnimationProcessor().getBone("head");
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (head == null || data == null) return;
        float pitch = Mth.clamp(data.headPitch(), -30f, 30f) * pitchScale;
        float yaw = Mth.clamp(Mth.wrapDegrees(data.netHeadYaw()), -50f, 50f) * 0.7f;
        head.setRotX(head.getRotX() + pitch * Mth.DEG_TO_RAD);
        head.setRotY(head.getRotY() + yaw * Mth.DEG_TO_RAD);
        head.resetStateChanges();
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
