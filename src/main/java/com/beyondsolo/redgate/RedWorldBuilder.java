package com.beyondsolo.redgate;

import com.beyondsolo.gate.GateBlock;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;

import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Shapes a red gate's patch of the Red Winter, a step per tick once the ground under each part has been generated:
 * <ul>
 *   <li><b>The breach</b>, where hunters arrive: a ring of leaning black stones round a floor of cracked tiles, red veins
 *   running out of it like blood frozen in the snow, two campfires, and the way out, a gate burning red and sealed.</li>
 *   <li><b>The keep</b> of the land's master, about a hundred blocks off: a round wall of ice and deepslate with six
 *   towers lit cold, a courtyard paved round a red sigil, a dais and throne, and behind it an ice spire with a red
 *   light in its crown that can be seen from the breach.</li>
 *   <li><b>Camps</b> of its creatures out in the snow: hide tents, a fire, a skull totem, bones.</li>
 * </ul>
 * The ground is cleared and laid by the builder itself, so the same builder works in the overworld of game tests.
 */
final class RedWorldBuilder {
    private static final TicketType<ChunkPos> TICKET = TicketType.create("beyondsolo_red_winter", Comparator.comparingLong(ChunkPos::toLong), 20 * 30);
    private static final int FLAGS = 2 | 16;

    enum Step { PLAN, ARRIVAL, KEEP_GROUND, KEEP_WALLS, KEEP_THRONE, CAMPS, DONE }

    private final RedGate run;
    private Step step = Step.PLAN;
    private int camp;

    RedWorldBuilder(RedGate run) {
        this.run = run;
        // picked up again after a restart: the plan (and the breach, if it stands) are kept
        if (run.center != null && run.keep != null) step = run.exit == null ? Step.ARRIVAL : Step.KEEP_GROUND;
    }

    RedGate run() {
        return run;
    }

    /** True once hunters can be let in (the breach is built). */
    boolean arrivalReady() {
        return step.ordinal() > Step.ARRIVAL.ordinal();
    }

    boolean done() {
        return step == Step.DONE;
    }

    /** One step of work, if the ground for it is ready. True when the whole patch is done. */
    boolean tick(ServerLevel level) {
        work(level, false);
        return step == Step.DONE;
    }

    /** Everything now, generating whatever ground is missing (tests, commands). */
    void finish(ServerLevel level) {
        for (int guard = 0; guard < 64 && step != Step.DONE; guard++) work(level, true);
    }

    /** The plan and the breach now, generating their ground if it isn't there yet (someone is waiting to arrive). */
    void finishArrival(ServerLevel level) {
        for (int guard = 0; guard < 4 && !arrivalReady(); guard++) work(level, true);
    }

    private void work(ServerLevel level, boolean now) {
        switch (step) {
            case PLAN -> {
                RedWorld.Plan plan = RedWorld.plan(level, run.slot, run.seed);
                run.center = plan.center();
                run.keep = plan.keep();
                run.keepYaw = plan.keepYaw();
                run.camps.clear();
                for (BlockPos c : plan.camps()) run.camps.add(new RedGate.Camp(c));
                step = Step.ARRIVAL;
            }
            case ARRIVAL -> {
                if (!ready(level, run.center, 23, now)) return;
                run.exit = arrival(level, run, run.center, front(run));
                step = Step.KEEP_GROUND;
            }
            case KEEP_GROUND -> {
                if (!ready(level, run.keep, 43, now)) return;
                keepGround(level, run.keep, keepFront(run), run.seed);
                step = Step.KEEP_WALLS;
            }
            case KEEP_WALLS -> {
                if (!ready(level, run.keep, 26, now)) return;
                keepWalls(level, run.keep, keepFront(run), run.seed);
                step = Step.KEEP_THRONE;
            }
            case KEEP_THRONE -> {
                if (!ready(level, run.keep, 26, now)) return;
                keepThrone(level, run.keep, keepFront(run), run.seed);
                step = Step.CAMPS;
            }
            case CAMPS -> {
                if (camp >= run.camps.size()) {
                    step = Step.DONE;
                    run.built = true;
                    return;
                }
                RedGate.Camp at = run.camps.get(camp);
                if (at.triggered) {
                    // hunters have been there already (the build was picked up after a restart): left as it is
                    camp++;
                    return;
                }
                BlockPos c = at.pos;
                if (!ready(level, c, 19, now)) return;
                camp(level, c, run.seed + camp * 7919L);
                camp++;
            }
            case DONE -> {}
        }
    }

    /** The direction from the breach towards the keep (the way the arrivals face). */
    static Direction front(RedGate run) {
        return Direction.fromYRot(run.keepYaw);
    }

    /** The direction from the keep towards the breach (its open side). */
    static Direction keepFront(RedGate run) {
        return front(run).getOpposite();
    }

    /** Where the master of the land stands, in its courtyard before the throne. */
    static BlockPos bossSpot(RedGate run) {
        return run.keep.relative(keepFront(run).getOpposite(), 3);
    }

    /** The red light in the crown of the ice spire behind the throne. */
    static BlockPos spireTop(RedGate run) {
        return run.keep.relative(front(run), 14).above(26);
    }

    // ------------------------------------------------------------------ chunks

    /** True once every chunk within {@code radius} blocks of {@code c} is loaded; asks for them if not. */
    private static boolean ready(ServerLevel level, BlockPos c, int radius, boolean now) {
        int x0 = (c.getX() - radius) >> 4, x1 = (c.getX() + radius) >> 4;
        int z0 = (c.getZ() - radius) >> 4, z1 = (c.getZ() + radius) >> 4;
        boolean all = true;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (now) level.getChunk(x, z);
                else if (level.getChunkSource().getChunkNow(x, z) == null) all = false;
            }
        }
        if (!all) {
            ChunkPos cp = new ChunkPos(c);
            level.getChunkSource().addRegionTicket(TICKET, cp, Math.max(1, (radius >> 4) + 1), cp);
        }
        return all;
    }

    // ------------------------------------------------------------------ ground

    /**
     * Clears a round site to {@code height} blocks above its standing level (trees around it too) and lays its floor
     * ({@code floor} gives the top block for an offset), filling any dip under it down to the ground. Where the ground
     * falls away round it, a snowy slope of rock up to {@code skirt} blocks wide carries the site, so it stands on a hill
     * rather than on a pillar.
     */
    private static void site(ServerLevel level, BlockPos c, int radius, int height, int skirt, BiFunction<Integer, Integer, BlockState> floor) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        int y = c.getY();
        int outer = radius + Math.max(5, skirt);
        for (int dx = -outer; dx <= outer; dx++) {
            for (int dz = -outer; dz <= outer; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > outer + 0.5) continue;
                int x = c.getX() + dx, z = c.getZ() + dz;
                if (d > radius + 0.5) {
                    if (d <= radius + 5.5) {
                        // trees and spikes leaning over the edge go too
                        for (int yy = y; yy < y + height + 14; yy++) {
                            p.set(x, yy, z);
                            BlockState s = level.getBlockState(p);
                            if (s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES)) level.setBlock(p, air, FLAGS);
                        }
                    }
                    if (d <= radius + skirt + 0.5) {
                        // the slope: a block lower for every step out, give or take a lump
                        double lump = 1.1 * (Mth.sin(x * 0.29f) + Mth.cos(z * 0.33f)) + 0.6 * ((x * 73856093 ^ z * 19349663) & 7) / 7.0;
                        slope(level, p, x, z, y - 1 - (int) Math.floor((d - radius) * 0.9 + Math.max(0, lump)));
                    }
                    continue;
                }
                for (int yy = y; yy < y + height + (d > radius - 3 ? 14 : 0) + 1; yy++) {
                    p.set(x, yy, z);
                    BlockState s = level.getBlockState(p);
                    if (!s.isAir() && (yy < y + height || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES))) level.setBlock(p, air, FLAGS);
                }
                p.set(x, y - 1, z);
                level.setBlock(p, floor.apply(dx, dz), FLAGS);
                for (int yy = y - 2; yy > y - 26; yy--) {
                    p.set(x, yy, z);
                    BlockState s = level.getBlockState(p);
                    boolean wet = !s.getFluidState().isEmpty();
                    if (!(s.isAir() || s.canBeReplaced() || wet || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(Blocks.POWDER_SNOW))) break;
                    level.setBlock(p, wet ? Blocks.PACKED_ICE.defaultBlockState() : yy > y - 4 ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.STONE.defaultBlockState(), FLAGS);
                }
            }
        }
    }

    /** Raises a column's ground to {@code top} (snow over rock, ice under water), if it lies lower than that. */
    private static void slope(ServerLevel level, BlockPos.MutableBlockPos p, int x, int z, int top) {
        p.set(x, top, z);
        if (!fillable(level.getBlockState(p))) return;
        int ground = top;
        for (int bottom = top - 26; ground > bottom; ground--) {
            p.set(x, ground - 1, z);
            if (!fillable(level.getBlockState(p))) break;
        }
        for (int yy = ground; yy <= top; yy++) {
            p.set(x, yy, z);
            boolean wet = !level.getBlockState(p).getFluidState().isEmpty();
            BlockState s = yy == top ? Blocks.SNOW_BLOCK.defaultBlockState()
                    : wet ? Blocks.PACKED_ICE.defaultBlockState()
                    : yy >= top - 2 ? ((x + yy + z) % 5 == 0 ? Blocks.STONE.defaultBlockState() : Blocks.SNOW_BLOCK.defaultBlockState())
                    : ((x * 3 + yy * 7 + z) & 7) == 0 ? Blocks.ANDESITE.defaultBlockState() : Blocks.STONE.defaultBlockState();
            level.setBlock(p, s, FLAGS);
        }
    }

    /** Air, water, ice, snow, plants and trees: what a slope may be laid over. */
    private static boolean fillable(BlockState s) {
        return s.isAir() || s.canBeReplaced() || !s.getFluidState().isEmpty() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS)
                || s.is(Blocks.ICE) || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.SNOW);
    }

    /** A little fresh snow on bare ground here and there. */
    private static void dust(ServerLevel level, BlockPos c, int radius, RandomSource r, float chance) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius || r.nextFloat() > chance) continue;
                p.set(c.getX() + dx, c.getY(), c.getZ() + dz);
                if (!level.getBlockState(p).isAir()) continue;
                BlockState below = level.getBlockState(p.below());
                if (!below.is(Blocks.SNOW_BLOCK) && !below.is(Blocks.STONE)) continue;
                level.setBlock(p, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + r.nextInt(2)), FLAGS);
            }
        }
    }

    private static void set(ServerLevel level, BlockPos p, BlockState s) {
        level.setBlock(p, s, FLAGS);
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState s) {
        level.setBlock(new BlockPos(x, y, z), s, FLAGS);
    }

    private static BlockState st(Block b) {
        return b.defaultBlockState();
    }

    /** Smooth noise for floor patterns, about -1..1. */
    private static double n(int x, int z, long seed) {
        double s = (seed & 1023) * 0.37;
        return Math.sin(x * 0.31 + s) * Math.cos(z * 0.27 - s * 0.5) + 0.5 * Math.sin((x + z) * 0.19 + s * 1.3);
    }

    // ------------------------------------------------------------------ the breach

    /** Lays the breach and stands the sealed way out in it; returns the gate's position. */
    static BlockPos arrival(ServerLevel level, RedGate run, BlockPos a, Direction front) {
        RandomSource r = RandomSource.create(run.seed ^ 0xB2EAC4L);
        long seed = run.seed;
        site(level, a, 10, 10, 12, (dx, dz) -> {
            double d = Math.sqrt(dx * dx + dz * dz);
            double k = n(a.getX() + dx, a.getZ() + dz, seed);
            if (d <= 4.6) return k > 0.6 ? st(Blocks.CRACKED_DEEPSLATE_TILES) : k > -0.3 ? st(Blocks.DEEPSLATE_TILES) : st(Blocks.POLISHED_DEEPSLATE);
            if (d <= 6 && k > 0.9) return st(Blocks.COBBLED_DEEPSLATE);
            return k < -1.0 ? st(Blocks.PACKED_ICE) : st(Blocks.SNOW_BLOCK);
        });
        // red veins running out from under the gate, like blood frozen into the snow
        int y = a.getY() - 1;
        for (int i = 0; i < 7; i++) {
            double ang = i * Math.PI * 2 / 7 + r.nextDouble() * 0.5;
            double x = a.getX() + 0.5, z = a.getZ() + 0.5;
            int len = 4 + r.nextInt(7);
            for (int s = 0; s < len; s++) {
                ang += (r.nextDouble() - 0.5) * 0.6;
                x += Math.cos(ang);
                z += Math.sin(ang);
                BlockPos p = BlockPos.containing(x, y, z);
                set(level, p, s < len / 2 ? st(Blocks.NETHER_WART_BLOCK) : r.nextBoolean() ? st(Blocks.RED_NETHER_BRICKS) : st(Blocks.NETHER_WART_BLOCK));
            }
        }
        // standing stones, leaning out, with the side towards the keep left open
        Direction right = front.getClockWise();
        for (int i = 0; i < 9; i++) {
            double ang = i * Math.PI * 2 / 9 + 0.2;
            double ox = Math.cos(ang), oz = Math.sin(ang);
            if (ox * front.getStepX() + oz * front.getStepZ() > 0.8) continue;
            int sx = a.getX() + (int) Math.round(ox * 7.5), sz = a.getZ() + (int) Math.round(oz * 7.5);
            int h = 2 + r.nextInt(3);
            BlockState stone = r.nextBoolean() ? st(Blocks.BLACKSTONE) : r.nextBoolean() ? st(Blocks.POLISHED_BLACKSTONE) : st(Blocks.DEEPSLATE);
            int lx = 0, lz = 0;
            for (int yy = 0; yy < h; yy++) {
                if (yy >= 2 && r.nextInt(3) == 0) {
                    lx += (int) Math.signum(Math.round(ox));
                    lz += (int) Math.signum(Math.round(oz));
                }
                set(level, sx + lx, a.getY() + yy, sz + lz, stone);
            }
            BlockPos top = new BlockPos(sx + lx, a.getY() + h, sz + lz);
            if (i % 3 == 0) set(level, top, st(Blocks.RED_CANDLE).setValue(CandleBlock.CANDLES, 1 + r.nextInt(3)).setValue(CandleBlock.LIT, true));
            else set(level, top, st(Blocks.SNOW).setValue(SnowLayerBlock.LAYERS, 2));
        }
        // two fires to warm the arrivals, one each side
        for (int side = -1; side <= 1; side += 2) {
            BlockPos f = a.relative(right, 5 * side).relative(front, 1);
            set(level, f.below(), st(Blocks.COBBLED_DEEPSLATE));
            level.setBlock(f, st(Blocks.CAMPFIRE), 3);
            set(level, f.relative(front.getOpposite(), 2), st(Blocks.SPRUCE_LOG).setValue(RotatedPillarBlock.AXIS, right.getAxis()));
        }
        dust(level, a, 10, r, 0.18f);

        // the way out: right behind the arrivals, burning red, sealed
        BlockPos gatePos = a.relative(front.getOpposite(), 2);
        level.setBlock(gatePos, ModBlocks.GATE.get().defaultBlockState().setValue(GateBlock.FACING, front), 3);
        if (level.getBlockEntity(gatePos) instanceof GateBlockEntity gate) {
            gate.setup(run.gate, run.inside, true, level.getGameTime());
            gate.setRed(level.getGameTime());
        }
        return gatePos;
    }

    // ------------------------------------------------------------------ the keep

    private static void keepGround(ServerLevel level, BlockPos c, Direction front, long seed) {
        site(level, c, 22, 30, 20, (dx, dz) -> {
            double d = Math.sqrt(dx * dx + dz * dz);
            int x = c.getX() + dx, z = c.getZ() + dz;
            double k = n(x, z, seed);
            if (d > 19.5) return k > 0.8 ? st(Blocks.PACKED_ICE) : st(Blocks.SNOW_BLOCK);
            if (d <= 1.6) return st(Blocks.CHISELED_DEEPSLATE);
            if (d >= 4.4 && d < 5.6) return st(Blocks.NETHER_WART_BLOCK);
            double ang = Math.atan2(dz, dx);
            double spoke = Math.abs(Math.sin(ang * 4));
            if (d >= 5.6 && d < 9 && spoke < 0.12) return st(Blocks.RED_NETHER_BRICKS);
            if (d < 4.4) return st(Blocks.POLISHED_DEEPSLATE);
            return k > 0.75 ? st(Blocks.PACKED_ICE) : k > -0.2 ? st(Blocks.POLISHED_DEEPSLATE) : st(Blocks.DEEPSLATE_TILES);
        });
    }

    private static void keepWalls(ServerLevel level, BlockPos c, Direction front, long seed) {
        RandomSource r = RandomSource.create(seed ^ 0x6EE9L);
        double open = Math.atan2(front.getStepZ(), front.getStepX());
        int y = c.getY();
        // the wall: a ring of ice on a deepslate footing, crenellated, open on the breach's side
        for (int dx = -19; dx <= 19; dx++) {
            for (int dz = -19; dz <= 19; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < 16.6 || d >= 18.6) continue;
                double ang = Math.atan2(dz, dx);
                double off = Math.abs(Mth.wrapDegrees(Math.toDegrees(ang - open))) * Math.PI / 180.0 * d;
                if (off < 3.0) continue;
                int x = c.getX() + dx, z = c.getZ() + dz;
                for (int h = 0; h < 7; h++) {
                    BlockState s = h < 2 ? st(Blocks.DEEPSLATE_BRICKS) : r.nextInt(10) == 0 ? st(Blocks.BLUE_ICE) : st(Blocks.PACKED_ICE);
                    set(level, x, y + h, z, s);
                }
                set(level, x, y + 7, z, st(Blocks.POLISHED_BLACKSTONE_BRICKS));
                if (d >= 17.6 && ((int) Math.round(Math.toDegrees(ang) / 7.5)) % 2 == 0) set(level, x, y + 8, z, st(Blocks.POLISHED_BLACKSTONE_BRICKS));
            }
        }
        // six towers, the opening between two of them, lit with cold light
        for (int i = 0; i < 6; i++) {
            double ang = open + Math.toRadians(30 + 60 * i);
            int tx = c.getX() + (int) Math.round(Math.cos(ang) * 18), tz = c.getZ() + (int) Math.round(Math.sin(ang) * 18);
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > 2.7) continue;
                    for (int h = 0; h < 12; h++) {
                        BlockState s = h % 4 == 0 ? st(Blocks.DEEPSLATE_TILES) : st(Blocks.PACKED_ICE);
                        set(level, tx + dx, y + h, tz + dz, s);
                    }
                    set(level, tx + dx, y + 12, tz + dz, d > 1.8 ? st(Blocks.SEA_LANTERN) : st(Blocks.POLISHED_BLACKSTONE));
                    if (d <= 1.8) set(level, tx + dx, y + 13, tz + dz, st(Blocks.POLISHED_BLACKSTONE_BRICKS));
                }
            }
            set(level, tx, y + 14, tz, st(Blocks.BLUE_ICE));
            set(level, tx, y + 15, tz, st(Blocks.BLUE_ICE));
            set(level, tx, y + 16, tz, st(Blocks.PACKED_ICE));
        }
        // red banners either side of the opening
        Direction right = front.getClockWise();
        for (int side = -1; side <= 1; side += 2) {
            BlockPos b = c.relative(front, 17).relative(right, 4 * side).above(8);
            set(level, b.below(), st(Blocks.POLISHED_BLACKSTONE_BRICKS));
            level.setBlock(b, st(Blocks.RED_BANNER).setValue(BannerBlock.ROTATION, RotationSegment.convertToSegment(front)), 3);
        }
    }

    private static void keepThrone(ServerLevel level, BlockPos c, Direction front, long seed) {
        RandomSource r = RandomSource.create(seed ^ 0x7A20E1L);
        Direction back = front.getOpposite();
        Direction right = front.getClockWise();
        int y = c.getY();
        // the dais: three steps up to the throne, on the far side from the opening
        BlockPos base = c.relative(back, 9);
        int[][] steps = {{3, 4}, {2, 3}, {1, 2}};
        for (int s = 0; s < steps.length; s++) {
            int half = steps[s][0], depth = steps[s][1];
            for (int w = -half; w <= half; w++) {
                for (int dd = 0; dd < depth; dd++) {
                    BlockPos p = base.relative(right, w).relative(back, dd + s).above(s);
                    set(level, p, s == 0 ? st(Blocks.POLISHED_BLACKSTONE_BRICKS) : st(Blocks.POLISHED_BLACKSTONE));
                }
            }
        }
        BlockPos seat = base.relative(back, 3).above(3);
        set(level, seat, st(Blocks.POLISHED_BLACKSTONE_STAIRS).setValue(StairBlock.FACING, back));
        for (int h = 0; h < 4; h++) set(level, seat.relative(back).above(h), h == 3 ? st(Blocks.CHISELED_QUARTZ_BLOCK) : st(Blocks.BLUE_ICE));
        set(level, seat.relative(right), st(Blocks.CHISELED_QUARTZ_BLOCK));
        set(level, seat.relative(right.getOpposite()), st(Blocks.CHISELED_QUARTZ_BLOCK));
        for (int side = -1; side <= 1; side += 2) {
            BlockPos l = seat.relative(right, 2 * side);
            set(level, l, st(Blocks.POLISHED_BLACKSTONE_WALL));
            set(level, l.above(), st(Blocks.SOUL_LANTERN));
        }

        // the spire behind it: ice tapering up to a red light that can be seen from the breach
        BlockPos sp = c.relative(back, 14);
        int height = 28;
        for (int h = 0; h < height; h++) {
            double rad = Mth.lerp(h / (double) height, 2.8, 0.6);
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > rad) continue;
                    BlockState s = d < rad - 1.2 ? st(Blocks.BLUE_ICE) : h % 7 == 3 ? st(Blocks.DEEPSLATE_TILES) : st(Blocks.PACKED_ICE);
                    set(level, sp.getX() + dx, y + h, sp.getZ() + dz, s);
                }
            }
        }
        BlockPos crown = sp.above(height - 3);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            set(level, crown.relative(d), st(Blocks.RED_STAINED_GLASS));
            set(level, crown.relative(d).above(), st(Blocks.RED_STAINED_GLASS));
        }
        set(level, crown, st(Blocks.SHROOMLIGHT));
        set(level, crown.above(), st(Blocks.SHROOMLIGHT));
        set(level, crown.above(2), st(Blocks.RED_STAINED_GLASS));
        set(level, crown.above(3), st(Blocks.BLUE_ICE));

        // cold fires round the courtyard, and the bones of those who came before
        for (int i = 0; i < 4; i++) {
            double ang = Math.toRadians(45 + 90 * i);
            BlockPos f = c.offset((int) Math.round(Math.cos(ang) * 10), 0, (int) Math.round(Math.sin(ang) * 10));
            set(level, f.below(), st(Blocks.POLISHED_BLACKSTONE));
            level.setBlock(f, st(Blocks.SOUL_CAMPFIRE), 3);
        }
        for (int i = 0; i < 9; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double d = 7 + r.nextDouble() * 8;
            BlockPos b = c.offset((int) Math.round(Math.cos(ang) * d), 0, (int) Math.round(Math.sin(ang) * d));
            if (!level.getBlockState(b).isAir() || b.distSqr(base) < 30) continue;
            if (r.nextInt(3) == 0) {
                level.setBlock(b, st(Blocks.SKELETON_SKULL).setValue(SkullBlock.ROTATION, r.nextInt(16)), 3);
            } else {
                set(level, b, st(Blocks.BONE_BLOCK).setValue(RotatedPillarBlock.AXIS, r.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z));
                if (r.nextBoolean()) set(level, b.above(), st(Blocks.SNOW).setValue(SnowLayerBlock.LAYERS, 1));
            }
        }
        dust(level, c, 22, r, 0.05f);
    }

    // ------------------------------------------------------------------ a camp

    static void camp(ServerLevel level, BlockPos c, long seed) {
        RandomSource r = RandomSource.create(seed ^ 0xCA3F11L);
        site(level, c, 9, 8, 9, (dx, dz) -> {
            double d = Math.sqrt(dx * dx + dz * dz);
            double k = n(c.getX() + dx, c.getZ() + dz, seed);
            if (d <= 2.2) return st(Blocks.SPRUCE_PLANKS);
            if (d <= 6 && k > 0.5) return st(Blocks.COARSE_DIRT);
            return k < -0.9 ? st(Blocks.PACKED_ICE) : st(Blocks.SNOW_BLOCK);
        });
        int y = c.getY();
        // the fire, and logs to sit on either side of it
        level.setBlock(c, st(Blocks.CAMPFIRE), 3);
        for (int side = -1; side <= 1; side += 2) {
            for (int i = -1; i <= 1; i++) set(level, c.getX() + i, y, c.getZ() + 3 * side, st(Blocks.SPRUCE_LOG).setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        }
        // hide tents round it
        double a0 = r.nextDouble() * Math.PI * 2;
        BlockState[] hides = {st(Blocks.WHITE_WOOL), st(Blocks.LIGHT_GRAY_WOOL), st(Blocks.BROWN_WOOL)};
        for (int i = 0; i < 3; i++) {
            double ang = a0 + i * Math.PI * 2 / 3;
            Direction out = Direction.fromYRot(Math.toDegrees(ang) - 90);
            Direction side = out.getClockWise();
            BlockPos t = c.relative(out, 5);
            BlockState hide = hides[i % hides.length];
            for (int l = 0; l < 4; l++) {
                BlockPos row = t.relative(out, l);
                for (int h = 0; h < 2; h++) {
                    set(level, row.relative(side, -1).above(h), hide);
                    set(level, row.relative(side, 1).above(h), hide);
                }
                set(level, row.above(2), st(Blocks.SPRUCE_LOG).setValue(RotatedPillarBlock.AXIS, out.getAxis()));
                if (l == 3) {
                    set(level, row, hide);
                    set(level, row.above(), hide);
                }
            }
            set(level, t.relative(out).below(), st(Blocks.BROWN_WOOL));
        }
        // a totem with a skull on it, a lantern post, and bones
        double ta = a0 + Math.PI / 3;
        BlockPos totem = c.offset((int) Math.round(Math.cos(ta) * 4), 0, (int) Math.round(Math.sin(ta) * 4));
        for (int h = 0; h < 3; h++) set(level, totem.above(h), st(Blocks.STRIPPED_SPRUCE_LOG));
        level.setBlock(totem.above(3), st(Blocks.SKELETON_SKULL).setValue(SkullBlock.ROTATION, r.nextInt(16)), 3);
        set(level, totem.above(2).relative(Direction.Plane.HORIZONTAL.getRandomDirection(r)), st(Blocks.RED_WOOL));
        double la = a0 + Math.PI;
        BlockPos post = c.offset((int) Math.round(Math.cos(la) * 4), 0, (int) Math.round(Math.sin(la) * 4));
        if (level.getBlockState(post).isAir()) {
            set(level, post, st(Blocks.SPRUCE_FENCE));
            set(level, post.above(), st(Blocks.SPRUCE_FENCE));
            set(level, post.above(2), st(Blocks.LANTERN));
        }
        for (int i = 0; i < 3; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            BlockPos b = c.offset((int) Math.round(Math.cos(ang) * 7), 0, (int) Math.round(Math.sin(ang) * 7));
            if (!level.getBlockState(b).isAir()) continue;
            if (i == 0) level.setBlock(b, st(Blocks.SKELETON_SKULL).setValue(SkullBlock.ROTATION, r.nextInt(16)), 3);
            else set(level, b, st(Blocks.BONE_BLOCK).setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
        }
        dust(level, c, 9, r, 0.2f);
    }

    /** Where a camp's stash goes once it is taken (by the fire, between the logs). */
    static BlockPos stashSpot(BlockPos camp) {
        return camp.offset(2, 0, -2);
    }

    /** The keep's guard posts: round the courtyard. */
    static List<BlockPos> guardSpots(RedGate run) {
        Direction front = keepFront(run);
        Direction right = front.getClockWise();
        BlockPos c = run.keep;
        return List.of(c.relative(front, 8).relative(right, 4), c.relative(front, 8).relative(right, -4), c.relative(right, 9),
                c.relative(right, -9), c.relative(front, 4).relative(right, 7), c.relative(front, 4).relative(right, -7));
    }
}
