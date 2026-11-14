package com.beyondsolo.registry;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.evaluation.EvaluationCrystalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BeyondSolo.MOD_ID);

    /** The appraiser in every Evaluation Zone. Unbreakable so villages keep theirs. */
    public static final DeferredBlock<EvaluationCrystalBlock> EVALUATION_CRYSTAL = BLOCKS.register("evaluation_crystal",
            () -> new EvaluationCrystalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(-1.0f, 3_600_000.0f)
                    .noLootTable()
                    .lightLevel(state -> 12)
                    .sound(SoundType.AMETHYST)
                    .pushReaction(PushReaction.BLOCK)
                    .noOcclusion()));

    /** A gate: the anchor block of a portal into a dungeon. Unbreakable; it closes by itself once its cave is cleared. */
    public static final DeferredBlock<com.beyondsolo.gate.GateBlock> GATE = BLOCKS.register("gate",
            () -> new com.beyondsolo.gate.GateBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(-1.0f, 3_600_000.0f)
                    .noLootTable()
                    .lightLevel(state -> 13)
                    .sound(SoundType.AMETHYST)
                    .pushReaction(PushReaction.BLOCK)
                    .noOcclusion()));

    /** The reward chest that comes down after a gate's boss falls. */
    public static final DeferredBlock<com.beyondsolo.gate.RewardCacheBlock> REWARD_CACHE = BLOCKS.register("reward_cache",
            () -> new com.beyondsolo.gate.RewardCacheBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(-1.0f, 3_600_000.0f)
                    .noLootTable()
                    .lightLevel(state -> 10)
                    .sound(SoundType.METAL)
                    .pushReaction(PushReaction.BLOCK)
                    .noOcclusion()));

    private ModBlocks() {}
}
