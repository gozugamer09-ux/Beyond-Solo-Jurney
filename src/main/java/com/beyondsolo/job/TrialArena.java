package com.beyondsolo.job;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The Hall of Ascension: a sealed round hall under a dome, carved where the trial takes place. The shell is solid
 * all round, so it can stand anywhere (in the gate dimension, or the overworld in game tests).
 *
 * <p>Layout: a dark floor with two rings of inlay and a sigil in the middle, eight pillars with braziers on a
 * circle, the dome lit by hidden light blocks. Echoes rise from the pillars; the Arbiter waits at the north end.</p>
 */
public final class TrialArena {
    public static final int RADIUS = 20;
    public static final int HEIGHT = 16;
    public static final int PILLAR_RING = 15;
    public static final int PILLARS = 8;
    /** Arenas sit on this z line, one slot of {@link #SLOT_SPACING} per running trial along x. */
    public static final int LANE_Z = 60_000;
    public static final int SLOT_SPACING = 256;
    public static final int FLOOR_Y = 64;

    public static BlockPos center(int slot) {
        return new BlockPos(slot * SLOT_SPACING, FLOOR_Y, LANE_Z);
    }

    /** Where the player stands when the trial begins (south side, facing north). */
    public static Vec3 start(BlockPos c) {
        return Vec3.atBottomCenterOf(c.offset(0, 0, 11));
    }

    /** Where the Arbiter waits. */
    public static Vec3 bossSpot(BlockPos c) {
        return Vec3.atBottomCenterOf(c.offset(0, 0, -11));
    }

    /** The tops of the pillars, where echoes rise from. */
    public static List<Vec3> pillarFeet(BlockPos c) {
        List<Vec3> out = new ArrayList<>();
        for (int i = 0; i < PILLARS; i++) {
            double a = Math.PI * 2 * i / PILLARS + Math.PI / PILLARS;
            out.add(new Vec3(c.getX() + 0.5 + Math.cos(a) * (PILLAR_RING - 2), c.getY(), c.getZ() + 0.5 + Math.sin(a) * (PILLAR_RING - 2)));
        }
        return out;
    }

    public static List<BlockPos> pillarTops(BlockPos c) {
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < PILLARS; i++) {
            double a = Math.PI * 2 * i / PILLARS + Math.PI / PILLARS;
            out.add(c.offset((int) Math.round(Math.cos(a) * PILLAR_RING), 6, (int) Math.round(Math.sin(a) * PILLAR_RING)));
        }
        return out;
    }

    /** Carves and decorates the hall. About 60k block writes, without neighbour updates. */
    public static void build(ServerLevel level, BlockPos c) {
        BlockState shell = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState shellAccent = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState base = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockState floor = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        BlockState inlay = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
        BlockState gold = Blocks.GILDED_BLACKSTONE.defaultBlockState();
        BlockState glow = Blocks.CRYING_OBSIDIAN.defaultBlockState();
        BlockState sigil = Blocks.AMETHYST_BLOCK.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int outer = RADIUS + 3;
        int flags = 2 | 16;
        for (int dx = -outer; dx <= outer; dx++) {
            for (int dz = -outer; dz <= outer; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > outer + 0.5) continue;
                for (int dy = -3; dy <= HEIGHT + 4; dy++) {
                    p.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    BlockState state;
                    if (dy < 0) {
                        if (dy == -1 && d <= RADIUS) state = floorAt(d, dx, dz, floor, inlay, gold, glow, sigil);
                        else state = base;
                    } else {
                        double e = (d / RADIUS) * (d / RADIUS) + (dy / (double) HEIGHT) * (dy / (double) HEIGHT);
                        if (d <= RADIUS && e < 1) state = air;
                        else if (e < 1.45 || d <= RADIUS + 1) state = (dy % 5 == 2 && d > RADIUS - 1) ? shellAccent : shell;
                        else continue;
                    }
                    level.setBlock(p, state, flags);
                }
            }
        }
        // pillars with braziers
        for (BlockPos top : pillarTops(c)) {
            for (int y = 0; y < 6; y++) {
                BlockState s = y == 0 ? Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState()
                        : y == 5 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                        : Blocks.POLISHED_BASALT.defaultBlockState();
                level.setBlock(new BlockPos(top.getX(), c.getY() + y, top.getZ()), s, flags);
            }
            level.setBlock(top, Blocks.SOUL_CAMPFIRE.defaultBlockState(), flags);
        }
        // hidden light so the hall is never pitch black
        BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 12);
        for (int dx = -15; dx <= 15; dx += 6) {
            for (int dz = -15; dz <= 15; dz += 6) {
                if (dx * dx + dz * dz > 17 * 17) continue;
                level.setBlock(c.offset(dx, 9, dz), light, flags);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) level.setBlock(c.offset(dx, 3, dz), light, flags);
        }
    }

    private static BlockState floorAt(double d, int dx, int dz, BlockState floor, BlockState inlay, BlockState gold,
                                      BlockState glow, BlockState sigil) {
        if (d <= 1.6) return sigil;
        if (d <= 3.6 && (Math.abs(dx) == Math.abs(dz) || dx == 0 || dz == 0)) return gold;
        if (Math.abs(d - 6.5) < 0.55) return inlay;
        if (Math.abs(d - PILLAR_RING + 0.0) < 0.55) return glow;
        if (Math.abs(d - (RADIUS - 1.5)) < 0.55) return inlay;
        // spokes between the rings
        if (d > 7 && d < PILLAR_RING - 1 && (dx == 0 || dz == 0)) return gold;
        return floor;
    }

    private TrialArena() {}
}
