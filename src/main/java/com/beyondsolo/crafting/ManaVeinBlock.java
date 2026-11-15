package com.beyondsolo.crafting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A seam of mana crystal in a gate cave's wall. It glows and now and then breathes out a mote of light; mining it
 * (a pickaxe) gives mana stones of its size: lesser veins give shards, greater veins greater stones.
 * Drops are in {@code data/beyondsolo/loot_table/blocks/<vein>.json} (fortune works).
 */
public class ManaVeinBlock extends Block {
    private final int size;

    public ManaVeinBlock(int size, Properties props) {
        super(props);
        this.size = size;
    }

    public int size() {
        return size;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
        if (r.nextInt(5 - size) != 0) return;
        double x = pos.getX() + 0.5 + (r.nextDouble() - 0.5) * 1.3;
        double y = pos.getY() + 0.5 + (r.nextDouble() - 0.5) * 1.3;
        double z = pos.getZ() + 0.5 + (r.nextDouble() - 0.5) * 1.3;
        if (!level.getBlockState(BlockPos.containing(x, y, z)).isAir()) return;
        level.addParticle(size == 2 ? ParticleTypes.END_ROD : ParticleTypes.GLOW, x, y, z, 0, 0.015, 0);
    }
}
