package com.beyondsolo.home;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A pedestal in the trophy hall of a hunter's Penthouse: a plinth of black stone banded with gold, one for every great
 * threat. Once the hunter has slain that one, a likeness of it stands on top ({@link TrophyPedestalBlockEntity}, drawn
 * by {@code client.home.TrophyPedestalRenderer}) with its name and how many times; until then it stands empty under a
 * question mark. Only the builder places them, and only an operator can break one.
 */
public class TrophyPedestalBlock extends BaseEntityBlock {
    public static final MapCodec<TrophyPedestalBlock> CODEC = simpleCodec(TrophyPedestalBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Shapes.join(Block.box(1, 0, 1, 15, 3, 15),
            Shapes.join(Block.box(3, 3, 3, 13, 12, 13), Block.box(1.5, 12, 1.5, 14.5, 15, 14.5), BooleanOp.OR), BooleanOp.OR);

    public TrophyPedestalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrophyPedestalBlockEntity(pos, state);
    }
}
