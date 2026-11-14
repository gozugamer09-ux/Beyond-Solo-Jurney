package com.beyondsolo.gate;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * World generation: places a gate on the surface (see {@code worldgen/placed_feature/gate.json} for how rare, and
 * {@code neoforge/biome_modifier/gates.json} for where). The gate gets its rank from how far it is from the world's
 * centre; it registers its cave the first time it ticks.
 */
public class GateFeature extends Feature<NoneFeatureConfiguration> {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, BeyondSolo.MOD_ID);
    public static final DeferredHolder<Feature<?>, GateFeature> GATE = FEATURES.register("gate", () -> new GateFeature());

    public GateFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos pos = ctx.origin();
        if (!goodSpot(level, pos)) return false;
        Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(ctx.random());
        level.setBlock(pos, ModBlocks.GATE.get().defaultBlockState().setValue(GateBlock.FACING, facing), 2);
        if (level.getBlockEntity(pos) instanceof GateBlockEntity gate) {
            gate.setup(null, GateService.worldgenRank(pos, ctx.random()), false, -1);
        }
        return true;
    }

    /** Solid, dry ground with room for the portal (3 wide, 5 tall) above it. */
    public static boolean goodSpot(LevelAccessor level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (!below.isFaceSturdy(level, pos.below(), Direction.UP) || !below.getFluidState().isEmpty()) return false;
        for (int y = 0; y < 5; y++) {
            for (int s = -1; s <= 1; s++) {
                BlockState a = level.getBlockState(pos.offset(s, y, 0));
                BlockState b = level.getBlockState(pos.offset(0, y, s));
                if (!a.canBeReplaced() || !b.canBeReplaced() || !a.getFluidState().isEmpty()) return false;
            }
        }
        return true;
    }
}
