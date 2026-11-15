package com.beyondsolo.crafting;

import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Where the dungeon builder puts mana veins, and how big they are for the gate's rank. */
public final class ManaVeins {
    /** Vein size for a gate: E/D lesser, C/B plain, A/S greater, with a chance of one size smaller. */
    public static int size(PlayerRank rank, RandomSource r) {
        int size = rank.tier() / 2;
        if (size > 0 && r.nextInt(3) == 0) size--;
        return size;
    }

    /** A small cluster (1-4 blocks) of vein set into the rock at {@code at}. */
    public static void place(ServerLevel level, BlockPos at, PlayerRank rank, RandomSource r, int flags) {
        BlockState vein = CraftingModule.VEINS.get(size(rank, r)).get().defaultBlockState();
        level.setBlock(at, vein, flags);
        int more = r.nextInt(4);
        for (int i = 0; i < more; i++) {
            BlockPos p = at.relative(Direction.getRandom(r));
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && s.isSolidRender(level, p) && !s.hasBlockEntity()) level.setBlock(p, vein, flags);
        }
    }

    private ManaVeins() {}
}
