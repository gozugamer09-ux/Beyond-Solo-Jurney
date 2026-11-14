package com.beyondsolo.registry;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.evaluation.EvaluationCrystalBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BeyondSolo.MOD_ID);

    public static final Supplier<BlockEntityType<EvaluationCrystalBlockEntity>> EVALUATION_CRYSTAL = BLOCK_ENTITIES.register("evaluation_crystal",
            () -> BlockEntityType.Builder.of(EvaluationCrystalBlockEntity::new, ModBlocks.EVALUATION_CRYSTAL.get()).build(null));

    public static final Supplier<BlockEntityType<com.beyondsolo.gate.GateBlockEntity>> GATE = BLOCK_ENTITIES.register("gate",
            () -> BlockEntityType.Builder.of(com.beyondsolo.gate.GateBlockEntity::new, ModBlocks.GATE.get()).build(null));

    public static final Supplier<BlockEntityType<com.beyondsolo.gate.RewardCacheBlockEntity>> REWARD_CACHE = BLOCK_ENTITIES.register("reward_cache",
            () -> BlockEntityType.Builder.of(com.beyondsolo.gate.RewardCacheBlockEntity::new, ModBlocks.REWARD_CACHE.get()).build(null));

    private ModBlockEntities() {}
}
