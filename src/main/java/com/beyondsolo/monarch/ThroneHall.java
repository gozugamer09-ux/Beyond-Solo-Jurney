package com.beyondsolo.monarch;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The Hall of the Empty Throne: a vast round hall under a high dome, sealed all round, where the throne trial takes place
 * ({@link ThroneTrial}). Like the job change's hall it can stand anywhere (the gate dimension, or the overworld in game
 * tests), on its own lane far from gate caves and the other halls.
 *
 * <p>Layout: a black floor with a ring of crying obsidian and gold spokes, ten pillars with soul braziers on a circle,
 * and at the north end a raised dais with the throne on it: a high back of black stone and gold with a crown of
 * amethyst. The Arbiter kneels before the dais; the hunter comes in at the south end.</p>
 */
public final class ThroneHall {
    public static final int RADIUS = 26;
    public static final int HEIGHT = 24;
    public static final int PILLAR_RING = 20;
    public static final int PILLARS = 10;
    /** Halls sit on this z line, one slot of {@link #SLOT_SPACING} per running trial along x. */
    public static final int LANE_Z = 75_000;
    public static final int SLOT_SPACING = 256;
    public static final int FLOOR_Y = 64;
    /** The dais: a half round at the north end, this far from the middle, and how high its top is. */
    public static final int DAIS_FROM = 15;
    public static final int DAIS_HEIGHT = 2;
    /** The throne's seat, from the middle (north is -z). */
    public static final int THRONE_Z = -21;

    public static BlockPos center(int slot) {
        return new BlockPos(slot * SLOT_SPACING, FLOOR_Y, LANE_Z);
    }

    /** Where the hunter stands when the trial begins (south side, facing the throne). */
    public static Vec3 start(BlockPos c) {
        return Vec3.atBottomCenterOf(c.offset(0, 0, 18));
    }

    /** Where the Arbiter kneels: before the dais. */
    public static Vec3 arbiterSpot(BlockPos c) {
        return Vec3.atBottomCenterOf(c.offset(0, 0, -9));
    }

    /** The foot of the throne, on the dais, facing south. */
    public static Vec3 throneFoot(BlockPos c) {
        return Vec3.atBottomCenterOf(c.offset(0, DAIS_HEIGHT, THRONE_Z + 2));
    }

    /** The throne's seat (the hunter sits there at the coronation). */
    public static Vec3 seat(BlockPos c) {
        return new Vec3(c.getX() + 0.5, c.getY() + DAIS_HEIGHT + 0.5, c.getZ() + THRONE_Z + 0.5);
    }

    /** The feet of the pillars, where the fallen court rises. */
    public static List<Vec3> pillarFeet(BlockPos c) {
        List<Vec3> out = new ArrayList<>();
        for (int i = 0; i < PILLARS; i++) {
            double a = Math.PI * 2 * i / PILLARS + Math.PI / PILLARS;
            // none on the dais
            if (Math.sin(a) < -0.55) continue;
            out.add(new Vec3(c.getX() + 0.5 + Math.cos(a) * (PILLAR_RING - 2), c.getY(), c.getZ() + 0.5 + Math.sin(a) * (PILLAR_RING - 2)));
        }
        return out;
    }

    /** The braziers on top of the pillars. */
    public static List<BlockPos> pillarTops(BlockPos c) {
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < PILLARS; i++) {
            double a = Math.PI * 2 * i / PILLARS + Math.PI / PILLARS;
            out.add(c.offset((int) Math.round(Math.cos(a) * PILLAR_RING), 8, (int) Math.round(Math.sin(a) * PILLAR_RING)));
        }
        return out;
    }

    /** Carves and decorates the hall. About 90k block writes, without neighbour updates. */
    public static void build(ServerLevel level, BlockPos c) {
        BlockState shell = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState shellAccent = Blocks.CRYING_OBSIDIAN.defaultBlockState();
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
                        else if (e < 1.4 || d <= RADIUS + 1) state = (dy % 6 == 3 && d > RADIUS - 1) ? shellAccent : shell;
                        else continue;
                    }
                    level.setBlock(p, state, flags);
                }
            }
        }
        dais(level, c, flags);
        throne(level, c, flags);
        // pillars with soul braziers
        for (BlockPos top : pillarTops(c)) {
            for (int y = 0; y < 8; y++) {
                BlockState s = y == 0 ? Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState()
                        : y == 7 ? Blocks.GILDED_BLACKSTONE.defaultBlockState()
                        : y == 4 ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                        : Blocks.POLISHED_BASALT.defaultBlockState();
                level.setBlock(new BlockPos(top.getX(), c.getY() + y, top.getZ()), s, flags);
            }
            level.setBlock(top, Blocks.SOUL_CAMPFIRE.defaultBlockState(), flags);
        }
        // hidden light: a bright grid under the dome and a ring lower down, so the fight reads well
        BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);
        for (int dx = -20; dx <= 20; dx += 5) {
            for (int dz = -20; dz <= 20; dz += 5) {
                if (dx * dx + dz * dz > 21 * 21) continue;
                level.setBlock(c.offset(dx, 12, dz), light, flags);
            }
        }
        for (int i = 0; i < 20; i++) {
            double a = Math.PI * 2 * i / 20;
            BlockPos at = c.offset((int) Math.round(Math.cos(a) * 12), 4, (int) Math.round(Math.sin(a) * 12));
            if (level.getBlockState(at).isAir()) level.setBlock(at, light, flags);
        }
    }

    /** The dais: two steps up to a half round of gold-veined stone at the north end. */
    private static void dais(ServerLevel level, BlockPos c, int flags) {
        BlockState top = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        BlockState rim = Blocks.GILDED_BLACKSTONE.defaultBlockState();
        BlockState body = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= -DAIS_FROM + 2; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > RADIUS - 0.5) continue;
                // two steps: the first ring one block high, the inner part two
                int height = dz <= -DAIS_FROM - 1 ? DAIS_HEIGHT : dz <= -DAIS_FROM + 1 ? 1 : 0;
                if (Math.abs(dx) > 9 + (-DAIS_FROM - dz) * 2 && dz > -DAIS_FROM - 4) height = Math.min(height, 1);
                for (int y = 0; y < height; y++) {
                    boolean edge = y == height - 1;
                    BlockState s = edge ? (dz == -DAIS_FROM - 1 || dz == -DAIS_FROM + 1 ? rim : top) : body;
                    level.setBlock(c.offset(dx, y, dz), s, flags);
                }
            }
        }
        // a runner of amethyst up to the throne
        for (int dz = -DAIS_FROM + 1; dz >= THRONE_Z + 2; dz--) {
            int y = dz <= -DAIS_FROM - 1 ? DAIS_HEIGHT - 1 : 0;
            level.setBlock(c.offset(0, y, dz), Blocks.AMETHYST_BLOCK.defaultBlockState(), flags);
        }
    }

    /** The throne: a seat, arms, and a high back crowned with amethyst. */
    private static void throne(ServerLevel level, BlockPos c, int flags) {
        int y0 = DAIS_HEIGHT;
        BlockState stone = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState gold = Blocks.GILDED_BLACKSTONE.defaultBlockState();
        BlockState dark = Blocks.CRYING_OBSIDIAN.defaultBlockState();
        BlockState seat = Blocks.POLISHED_BLACKSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        BlockState wall = Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState();
        // the seat, with the steps to it
        level.setBlock(c.offset(0, y0, THRONE_Z), seat, flags);
        level.setBlock(c.offset(-1, y0, THRONE_Z), stone, flags);
        level.setBlock(c.offset(1, y0, THRONE_Z), stone, flags);
        level.setBlock(c.offset(-1, y0 + 1, THRONE_Z), wall, flags);
        level.setBlock(c.offset(1, y0 + 1, THRONE_Z), wall, flags);
        // the back: three wide, rising to a point, gold at the edges
        for (int y = 0; y <= 9; y++) {
            for (int dx = -2; dx <= 2; dx++) {
                int span = y < 6 ? 2 : y < 8 ? 1 : 0;
                if (Math.abs(dx) > span) continue;
                BlockState s = Math.abs(dx) == span ? gold : (y % 3 == 1 ? dark : stone);
                level.setBlock(c.offset(dx, y0 + y, THRONE_Z - 1), s, flags);
            }
        }
        // its crown
        level.setBlock(c.offset(0, y0 + 10, THRONE_Z - 1), Blocks.AMETHYST_BLOCK.defaultBlockState(), flags);
        level.setBlock(c.offset(-1, y0 + 9, THRONE_Z - 1), Blocks.AMETHYST_CLUSTER.defaultBlockState(), flags);
        level.setBlock(c.offset(1, y0 + 9, THRONE_Z - 1), Blocks.AMETHYST_CLUSTER.defaultBlockState(), flags);
        level.setBlock(c.offset(0, y0 + 11, THRONE_Z - 1), Blocks.AMETHYST_CLUSTER.defaultBlockState(), flags);
        // braziers either side of it
        for (int side = -1; side <= 1; side += 2) {
            BlockPos foot = c.offset(side * 4, y0, THRONE_Z);
            level.setBlock(foot, Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), flags);
            level.setBlock(foot.above(), Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState(), flags);
            level.setBlock(foot.above(2), Blocks.SOUL_CAMPFIRE.defaultBlockState(), flags);
        }
    }

    private static BlockState floorAt(double d, int dx, int dz, BlockState floor, BlockState inlay, BlockState gold,
                                      BlockState glow, BlockState sigil) {
        if (d <= 2.1) return sigil;
        if (d <= 4.6 && (Math.abs(dx) == Math.abs(dz) || dx == 0 || dz == 0)) return gold;
        if (Math.abs(d - 8.5) < 0.55) return inlay;
        if (Math.abs(d - PILLAR_RING) < 0.55) return glow;
        if (Math.abs(d - (RADIUS - 1.5)) < 0.55) return inlay;
        // spokes between the rings
        if (d > 9 && d < PILLAR_RING - 1 && (dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz))) return gold;
        return floor;
    }

    private ThroneHall() {}
}
