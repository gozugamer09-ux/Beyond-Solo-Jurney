package com.beyondsolo.depths;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

/**
 * A trap's heart: a rune-cut plinth holding a red crystal. It can't be mined; strike it (hits, arrows, spells that
 * hit blocks) {@link TrapRooms#CORE_HITS} times and it shatters, and the trap dies with it ({@link TrapRooms#hitCore}).
 */
public class TrapCoreBlock extends Block {
    public static final MapCodec<TrapCoreBlock> CODEC = simpleCodec(TrapCoreBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 4, 15), Block.box(4.5, 4, 4.5, 11.5, 15, 11.5));

    public TrapCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) TrapRooms.hitCore(sp.serverLevel(), pos, sp);
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (!level.isClientSide && projectile.getOwner() instanceof ServerPlayer sp) TrapRooms.hitCore(sp.serverLevel(), hit.getBlockPos(), sp);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) TrapRooms.coreBroken(sp.serverLevel(), pos, sp);
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Red motes rising off the crystal, a spark now and then. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.5;
        double y = pos.getY() + 0.45 + random.nextDouble() * 0.5;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.5;
        if (HiddenSealBlock.nearViewer(level, x, y, z)) return;
        level.addParticle(new DustParticleOptions(new Vector3f(1f, 0.3f, 0.28f), 0.7f), x, y, z, 0, 0.03, 0);
        if (random.nextInt(5) == 0) level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y + 0.3, z, 0, 0.05, 0);
    }
}
