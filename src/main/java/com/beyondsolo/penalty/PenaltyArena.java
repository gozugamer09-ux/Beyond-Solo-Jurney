package com.beyondsolo.penalty;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The Wastes: one patch of the Penalty Zone's red desert, shaped for a sentence.
 *
 * <p>A round basin of red sand with low dunes, ringed by leaning black spires (the edge of the zone), half-buried
 * ribcages of great beasts, dead brush and skulls, and in the middle the Pillar of Judgment, a black obelisk with a
 * burning core. Hidden light blocks keep the fight readable under the eclipse. The ground is laid down too, so the
 * same builder works in the zone's own dimension and in the overworld fallback of game tests.</p>
 */
public final class PenaltyArena {
    /** The edge of the zone: past it the player is pushed back. */
    public static final int RADIUS = 30;
    /** How far out the ground is shaped. */
    public static final int OUTER = RADIUS + 14;
    public static final int FLOOR_Y = 64;
    /** Arenas sit on this z line, one slot of {@link #SLOT_SPACING} per sentence being served, along x. */
    public static final int LANE_Z = -60_000;
    public static final int SLOT_SPACING = 512;
    private static final int CLEAR_HEIGHT = 18;

    public static BlockPos center(int slot) {
        return new BlockPos(slot * SLOT_SPACING, FLOOR_Y, LANE_Z);
    }

    /** Where the player lands, south of the pillar and facing it. */
    public static Vec3 start(BlockPos c) {
        return new Vec3(c.getX() + 0.5, FLOOR_Y + 0.0, c.getZ() + 9.5);
    }

    /** The standing height at a column (top of the sand), from the heightmap. */
    public static double groundY(ServerLevel level, double x, double z) {
        int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        return Math.max(FLOOR_Y - 1, Math.min(FLOOR_Y + 4, h));
    }

    /** A dune's extra height at an offset from the centre (0..2), flat around the pillar. */
    static int dune(int dx, int dz) {
        double d = Math.sqrt(dx * dx + dz * dz);
        double n = Math.sin(dx * 0.19 + dz * 0.07) * Math.cos(dz * 0.16 - dx * 0.05) + 0.6 * Math.sin((dx + dz) * 0.11 + 1.3);
        double flat = Mth.clamp((d - 6) / 10.0, 0, 1);
        return (int) Math.round(Math.max(0, n) * 1.4 * flat);
    }

    /** Lays the ground, the spires, the bones and the pillar. About 90k block writes, without neighbour updates. */
    public static void build(ServerLevel level, BlockPos c) {
        RandomSource r = RandomSource.create(c.asLong() ^ 0x9E3779B97F4A7C15L);
        int flags = 2 | 16;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState sand = Blocks.RED_SAND.defaultBlockState();
        BlockState stone = Blocks.RED_SANDSTONE.defaultBlockState();
        BlockState smooth = Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState();
        BlockState clay = Blocks.TERRACOTTA.defaultBlockState();
        BlockState redClay = Blocks.RED_TERRACOTTA.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        // ---- ground
        for (int dx = -OUTER; dx <= OUTER; dx++) {
            for (int dz = -OUTER; dz <= OUTER; dz++) {
                if (dx * dx + dz * dz > OUTER * OUTER) continue;
                int top = FLOOR_Y - 1 + dune(dx, dz);
                double patch = Math.sin(dx * 0.33 + 2.1) * Math.sin(dz * 0.29 - 0.7) + Math.sin((dx - dz) * 0.21);
                for (int y = FLOOR_Y - 5; y <= FLOOR_Y + CLEAR_HEIGHT; y++) {
                    p.set(c.getX() + dx, y, c.getZ() + dz);
                    BlockState s;
                    if (y > top) s = air;
                    else if (y == top) s = patch > 1.25 ? redClay : patch < -1.45 ? smooth : sand;
                    else if (y >= top - 2) s = patch > 1.1 ? clay : sand;
                    else s = stone;
                    level.setBlock(p, s, flags);
                }
            }
        }

        // ---- the spires: black teeth leaning in over the edge
        int spires = 26;
        for (int i = 0; i < spires; i++) {
            double a = Math.PI * 2 * i / spires + (r.nextDouble() - 0.5) * 0.12;
            double dist = RADIUS + 1.5 + r.nextDouble() * 2.5;
            int height = 6 + r.nextInt(9) + (i % 3 == 0 ? 5 : 0);
            double lean = 1.5 + r.nextDouble() * 2;
            spire(level, c, a, dist, height, lean, r, flags);
        }
        // a few broken stumps further out, so the edge is not a perfect wall
        for (int i = 0; i < 14; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double dist = RADIUS + 6 + r.nextDouble() * 6;
            spire(level, c, a, dist, 2 + r.nextInt(4), 0.5, r, flags);
        }

        // ---- ribcages of something enormous, half sunk in the sand
        for (int i = 0; i < 5; i++) {
            double a = Math.PI * 2 * i / 5 + r.nextDouble() * 0.8;
            double dist = 12 + r.nextDouble() * 12;
            int bx = (int) Math.round(Math.cos(a) * dist), bz = (int) Math.round(Math.sin(a) * dist);
            ribcage(level, c.offset(bx, 0, bz), r.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z, 5 + r.nextInt(5), flags);
        }

        // ---- dead brush, scattered skulls, a few stones
        for (int i = 0; i < 70; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double dist = 4 + r.nextDouble() * (RADIUS - 2);
            int dx = (int) Math.round(Math.cos(a) * dist), dz = (int) Math.round(Math.sin(a) * dist);
            int y = FLOOR_Y + dune(dx, dz);
            BlockPos at = c.offset(dx, y - FLOOR_Y, dz);
            if (!level.getBlockState(at).isAir() || level.getBlockState(at.below()).isAir()) continue;
            int roll = r.nextInt(10);
            BlockState s = roll < 6 ? Blocks.DEAD_BUSH.defaultBlockState()
                    : roll < 8 ? Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, r.nextInt(16))
                    : Blocks.BLACKSTONE.defaultBlockState();
            level.setBlock(at, s, flags);
        }

        // ---- the Pillar of Judgment
        BlockState brick = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState chis = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                level.setBlock(c.offset(dx, -1, dz), Math.abs(dx) == 2 || Math.abs(dz) == 2 ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : chis, flags);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) level.setBlock(c.offset(dx, 0, dz), (dx == 0 || dz == 0) ? brick : Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState(), flags);
        }
        for (int y = 1; y <= 7; y++) {
            BlockState s = y == 3 || y == 4 ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                    : y == 7 ? Blocks.MAGMA_BLOCK.defaultBlockState()
                    : y == 6 ? chis
                    : Blocks.POLISHED_BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
            level.setBlock(c.offset(0, y, 0), s, flags);
        }

        // ---- hidden light: dim enough to keep the dusk, bright enough to read the fight
        BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 11);
        for (int dx = -RADIUS; dx <= RADIUS; dx += 7) {
            for (int dz = -RADIUS; dz <= RADIUS; dz += 7) {
                if (dx * dx + dz * dz > (RADIUS - 1) * (RADIUS - 1)) continue;
                BlockPos at = c.offset(dx, 6, dz);
                if (level.getBlockState(at).isAir()) level.setBlock(at, light, flags);
            }
        }
        level.setBlock(c.offset(0, 9, 0), Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15), flags);
    }

    /** A tapering black spike from the ground, leaning towards the centre. */
    private static void spire(ServerLevel level, BlockPos c, double angle, double dist, int height, double lean, RandomSource r, int flags) {
        double ox = Math.cos(angle) * dist, oz = Math.sin(angle) * dist;
        double ix = -Math.cos(angle), iz = -Math.sin(angle);
        double base = 1.6 + r.nextDouble() * 0.9;
        BlockState[] bands = {Blocks.BLACKSTONE.defaultBlockState(), Blocks.BASALT.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(),
                Blocks.RED_TERRACOTTA.defaultBlockState(), Blocks.POLISHED_BLACKSTONE.defaultBlockState()};
        for (int y = -2; y < height; y++) {
            double k = Math.max(0, y) / (double) height;
            double rad = base * (1 - k) + 0.35;
            double cx = ox + ix * lean * k * k, cz = oz + iz * lean * k * k;
            int span = (int) Math.ceil(rad);
            BlockState s = bands[Math.floorMod(y / 3 + (int) (angle * 7), bands.length)];
            for (int dx = -span; dx <= span; dx++) {
                for (int dz = -span; dz <= span; dz++) {
                    double ex = Math.round(cx) + dx - cx, ez = Math.round(cz) + dz - cz;
                    if (ex * ex + ez * ez > rad * rad) continue;
                    level.setBlock(c.offset((int) Math.round(cx) + dx, y, (int) Math.round(cz) + dz), s, flags);
                }
            }
        }
    }

    /** A spine along one axis with ribs arching up on both sides, sunk so only the tops show. */
    private static void ribcage(ServerLevel level, BlockPos at, Direction.Axis axis, int length, int flags) {
        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
        BlockState rib = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        boolean x = axis == Direction.Axis.X;
        int[][] arch = {{1, 0}, {2, 1}, {3, 2}, {3, 3}, {2, 4}};
        for (int i = 0; i < length; i++) {
            int along = i - length / 2;
            BlockPos spine = x ? at.offset(along, -1, 0) : at.offset(0, -1, along);
            level.setBlock(spine, bone, flags);
            if (i % 2 == 1 || i == 0 || i == length - 1) continue;
            int tall = Math.min(arch.length, 3 + (i == length / 2 ? 2 : 1));
            for (int side = -1; side <= 1; side += 2) {
                for (int k = 0; k < tall; k++) {
                    int out = arch[k][0] * side, up = arch[k][1] - 1;
                    BlockPos b = x ? spine.offset(0, up + 1, out) : spine.offset(out, up + 1, 0);
                    level.setBlock(b, rib, flags);
                }
            }
        }
    }

    private PenaltyArena() {}
}
