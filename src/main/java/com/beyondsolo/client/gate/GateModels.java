package com.beyondsolo.client.gate;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.gate.RewardCacheBlockEntity;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/** The GeckoLib models (made by tools/gen_gate_assets.py). Gates and bosses pick their rank's texture. */
public final class GateModels {
    private static final ResourceLocation[] GATE_TEXTURES = textures("textures/block/gate_");
    private static final ResourceLocation[] BOSS_TEXTURES = textures("textures/entity/gate_boss_");

    private static ResourceLocation[] textures(String prefix) {
        ResourceLocation[] out = new ResourceLocation[PlayerRank.values().length];
        for (PlayerRank r : PlayerRank.values()) out[r.ordinal()] = BeyondSolo.id(prefix + r.name().toLowerCase() + ".png");
        return out;
    }

    public static final class Gate extends DefaultedBlockGeoModel<GateBlockEntity> {
        public Gate() {
            super(BeyondSolo.id("gate"));
        }

        @Override
        public ResourceLocation getTextureResource(GateBlockEntity gate) {
            // the way out of a cave is always the System's own blue
            return GATE_TEXTURES[gate.isExit() ? PlayerRank.C.ordinal() : gate.rank().ordinal()];
        }
    }

    public static final class Boss extends DefaultedEntityGeoModel<GateBossEntity> {
        public Boss() {
            super(BeyondSolo.id("gate_boss"), true);
        }

        @Override
        public ResourceLocation getTextureResource(GateBossEntity boss) {
            return BOSS_TEXTURES[boss.rank().ordinal()];
        }
    }

    public static final class Cache extends DefaultedBlockGeoModel<RewardCacheBlockEntity> {
        public Cache() {
            super(BeyondSolo.id("reward_cache"));
        }
    }

    private GateModels() {}
}
