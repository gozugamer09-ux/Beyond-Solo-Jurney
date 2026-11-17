package com.beyondsolo.depths;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/**
 * A wall of red light across a tunnel while a lockdown holds ({@link TrapRooms}). Nothing gets through; it falls
 * away when the last wave does.
 */
public class TrapWardBlock extends TransparentBlock {
    public static final MapCodec<TrapWardBlock> CODEC = simpleCodec(TrapWardBlock::new);

    public TrapWardBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends TransparentBlock> codec() {
        return CODEC;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(6) != 0) return;
        double x = pos.getX() + random.nextDouble(), y = pos.getY() + random.nextDouble(), z = pos.getZ() + random.nextDouble();
        if (HiddenSealBlock.nearViewer(level, x, y, z)) return;
        level.addParticle(new DustParticleOptions(new Vector3f(1f, 0.32f, 0.3f), 0.6f), x, y, z, 0, 0.02, 0);
    }
}
