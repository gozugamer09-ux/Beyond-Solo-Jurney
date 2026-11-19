package com.beyondsolo.manapoison;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

/**
 * A purifying crystal: a cluster of pale mana-glass that grows in high-rank gates ({@link ManaCrystals}) and draws
 * the poisoning out of a hunter resting by it ({@link ManaPoisoning}). Unbreakable; it goes with the cave.
 */
public class PurifyingCrystalBlock extends Block {
    public static final MapCodec<PurifyingCrystalBlock> CODEC = simpleCodec(PurifyingCrystalBlock::new);
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 15, 13);

    public PurifyingCrystalBlock(Properties properties) {
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

    /** Pale motes drifting up off the crystal, a glint now and then. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 2; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 0.35 + random.nextDouble() * 0.6;
            double x = pos.getX() + 0.5 + Math.cos(a) * r;
            double y = pos.getY() + 0.1 + random.nextDouble() * 1.2;
            double z = pos.getZ() + 0.5 + Math.sin(a) * r;
            level.addParticle(new DustParticleOptions(new Vector3f(0.55f, 0.98f, 0.92f), 0.6f), x, y, z, 0, 0.02, 0);
        }
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4,
                    pos.getY() + 1.0 + random.nextDouble() * 0.3, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4, 0, 0.015, 0);
        }
    }
}
