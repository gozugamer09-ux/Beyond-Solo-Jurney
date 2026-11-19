package com.beyondsolo.isle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.block.state.properties.RotationSegment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds Halcyon Isle ({@link IsleWorld.Layout}) a piece at a time once the ground for each piece is loaded:
 * <ul>
 *   <li><b>The land</b>, a chunk at a time: sand beaches round a wandering coast, grass and rolling hills inland, the
 *   ground darkening into the swarm's mud and resin near the hive.</li>
 *   <li><b>The great hive</b> at the island's heart: a dome of honeycomb and mud with spires, veins of light in its
 *   skin, a mouth on its south side sealed with resin until every nest has burned, and inside it the pit down into the
 *   dark.</li>
 *   <li><b>The nests</b>: rings of resin round the place where each nest's heart beats. A burned nest is scorched black
 *   and left burning.</li>
 *   <li><b>The Association's camp</b> on the south beach: tents, fires, crates, its banners, and the circle of light the
 *   hunters arrive and leave by.</li>
 *   <li><b>The resort's ruins</b>: broken white walls and windows, palms, a cracked road from the camp to the hive.</li>
 *   <li><b>The queen's chamber</b> deep under it all: a vaulted cave of resin lit from its walls, eggs along its edges,
 *   the queen's dais at its north end, the cocoon on the wall behind her, the way up at its south end.</li>
 * </ul>
 * Everything is laid by the builder itself (the land from the sea floor up, the chamber with a shell of its own), so it
 * builds the same in the game-test overworld.
 */
final class IsleBuilder {
    private static final TicketType<ChunkPos> TICKET = TicketType.create("beyondsolo_halcyon_isle", Comparator.comparingLong(ChunkPos::toLong), 20 * 30);
    static final int FLAGS = 2 | 16;
    /** Bumped when the island's look changes: an island built by an older version is built again. */
    static final int VERSION = 2;

    enum Step { TERRAIN, HIVE, HIVE_MOUTH, NESTS, CAMP, ROAD, RUINS, CHAMBER, CHAMBER_DECOR, DONE }

    private final IsleWorld.Layout l;
    private Step step = Step.TERRAIN;
    private final List<ChunkPos> chunks = new ArrayList<>();
    private int index;

    IsleBuilder(IsleWorld.Layout layout) {
        this.l = layout;
        int reach = (int) Math.ceil(l.radius() * 1.2) + 4;
        int cx = l.center().getX(), cz = l.center().getZ();
        for (int x = (cx - reach) >> 4; x <= (cx + reach) >> 4; x++) {
            for (int z = (cz - reach) >> 4; z <= (cz + reach) >> 4; z++) chunks.add(new ChunkPos(x, z));
        }
        // the camp's end first: the landing is ready soonest
        BlockPos camp = l.camp();
        chunks.sort(Comparator.comparingDouble(c -> sq(c.getMiddleBlockX() - camp.getX()) + sq(c.getMiddleBlockZ() - camp.getZ())));
    }

    IsleWorld.Layout layout() {
        return l;
    }

    boolean done() {
        return step == Step.DONE;
    }

    /** A share of the work, if the ground for it is loaded. True once the island stands. */
    boolean tick(ServerLevel level) {
        for (int i = 0; i < 3 && step == Step.TERRAIN; i++) {
            if (!work(level, false)) break;
        }
        if (step != Step.TERRAIN) work(level, false);
        return done();
    }

    /** Everything now, loading whatever ground is missing (tests, commands, a raid that can't wait). */
    void finish(ServerLevel level) {
        for (int guard = 0; guard < 100_000 && !done(); guard++) work(level, true);
    }

    /** One piece of work. False when its ground isn't loaded yet (it has been asked for). */
    private boolean work(ServerLevel level, boolean now) {
        switch (step) {
            case TERRAIN -> {
                if (index >= chunks.size()) {
                    next(Step.HIVE);
                    return true;
                }
                ChunkPos c = chunks.get(index);
                if (!ready(level, c, now)) return false;
                land(level, c);
                index++;
            }
            case HIVE -> {
                if (!ready(level, l.center(), l.hiveRadius() + 6, now)) return false;
                hive(level);
                next(Step.HIVE_MOUTH);
            }
            case HIVE_MOUTH -> {
                if (!ready(level, l.mouth(), 10, now)) return false;
                mouth(level);
                next(Step.NESTS);
            }
            case NESTS -> {
                if (index >= l.nests().size()) {
                    next(Step.CAMP);
                    return true;
                }
                if (!ready(level, l.nests().get(index), 12, now)) return false;
                nest(level, l, index, false);
                index++;
            }
            case CAMP -> {
                if (!ready(level, l.camp(), 14, now)) return false;
                camp(level);
                next(Step.ROAD);
            }
            case ROAD -> {
                road(level, now);
                next(Step.RUINS);
            }
            case RUINS -> {
                if (index >= l.ruins().size()) {
                    next(Step.CHAMBER);
                    return true;
                }
                if (!ready(level, l.ruins().get(index), 12, now)) return false;
                ruin(level, l.ruins().get(index), l.seed() + index * 7919L);
                index++;
            }
            case CHAMBER -> {
                if (!ready(level, l.chamber(), l.chamberRadius() + 6, now)) return false;
                chamber(level);
                next(Step.CHAMBER_DECOR);
            }
            case CHAMBER_DECOR -> {
                if (!ready(level, l.chamber(), l.chamberRadius() + 6, now)) return false;
                chamberDecor(level);
                cocoon(level, l, true);
                next(Step.DONE);
            }
            case DONE -> {}
        }
        return true;
    }

    private void next(Step s) {
        step = s;
        index = 0;
    }

    // ------------------------------------------------------------------ chunks

    private static boolean ready(ServerLevel level, ChunkPos c, boolean now) {
        if (now) {
            level.getChunk(c.x, c.z);
            return true;
        }
        if (level.getChunkSource().getChunkNow(c.x, c.z) != null) return true;
        level.getChunkSource().addRegionTicket(TICKET, c, 1, c);
        return false;
    }

    /** True once every chunk within {@code radius} blocks of {@code at} is loaded; asks for them if not. */
    static boolean ready(ServerLevel level, BlockPos at, int radius, boolean now) {
        int x0 = (at.getX() - radius) >> 4, x1 = (at.getX() + radius) >> 4;
        int z0 = (at.getZ() - radius) >> 4, z1 = (at.getZ() + radius) >> 4;
        boolean all = true;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (now) level.getChunk(x, z);
                else if (level.getChunkSource().getChunkNow(x, z) == null) all = false;
            }
        }
        if (!all) {
            ChunkPos cp = new ChunkPos(at);
            level.getChunkSource().addRegionTicket(TICKET, cp, Math.max(1, (radius >> 4) + 1), cp);
        }
        return all;
    }

    // ------------------------------------------------------------------ the land

    /** Lays one chunk of the island: its ground from the sea floor up, the sea round it, a little growth. */
    private void land(ServerLevel level, ChunkPos c) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        RandomSource r = RandomSource.create(l.seed() ^ c.toLong());
        int hx = l.center().getX(), hz = l.center().getZ();
        for (int x = c.getMinBlockX(); x <= c.getMaxBlockX(); x++) {
            for (int z = c.getMinBlockZ(); z <= c.getMaxBlockZ(); z++) {
                double d = IsleWorld.norm(l, x, z);
                if (d >= 1.0) {
                    if (!l.small() && d < 1.3) sea(level, p, x, z);
                    continue;
                }
                int top = IsleWorld.groundAt(l, x, z);
                boolean beach = IsleWorld.beach(l, x, z);
                double toHive = Math.hypot(x - hx, z - hz);
                BlockState surface = surface(x, z, beach, toHive, r);
                for (int y = l.base(); y <= top; y++) {
                    BlockState s = y == top ? surface
                            : y >= top - 3 ? (beach ? st(Blocks.SAND) : y == top - 1 && toHive < l.hiveRadius() + 10 ? st(Blocks.PACKED_MUD) : st(Blocks.DIRT))
                            : beach ? st(Blocks.SANDSTONE) : st(Blocks.STONE);
                    put(level, p.set(x, y, z), s);
                }
                // whatever an older island left standing over it
                int clear = top + (toHive < l.hiveRadius() + 6 ? l.hiveHeight() + 22 : 16);
                for (int y = top + 1; y <= clear; y++) {
                    p.set(x, y, z);
                    if (!level.getBlockState(p).isAir()) level.setBlock(p, st(Blocks.AIR), FLAGS);
                }
                // a little growth
                float roll = r.nextFloat();
                p.set(x, top + 1, z);
                if (surface.is(Blocks.GRASS_BLOCK) && roll < 0.12f) {
                    level.setBlock(p, roll < 0.02f ? st(Blocks.FERN) : roll < 0.025f ? st(Blocks.DEAD_BUSH) : st(Blocks.SHORT_GRASS), FLAGS);
                } else if (beach && roll < 0.008f) {
                    level.setBlock(p, st(Blocks.DEAD_BUSH), FLAGS);
                } else if (beach && roll < 0.012f && d > 0.9) {
                    // driftwood washed up
                    level.setBlock(p, st(Blocks.STRIPPED_OAK_LOG).setValue(RotatedPillarBlock.AXIS, r.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z), FLAGS);
                }
            }
        }
    }

    /** The open sea round the island: water to the surface, air over it. */
    private void sea(ServerLevel level, BlockPos.MutableBlockPos p, int x, int z) {
        for (int y = l.base(); y <= IsleWorld.SEA_Y; y++) {
            p.set(x, y, z);
            BlockState s = level.getBlockState(p);
            if (!s.is(Blocks.WATER)) level.setBlock(p, st(Blocks.WATER), FLAGS);
        }
        for (int y = IsleWorld.SEA_Y + 1; y <= IsleWorld.SEA_Y + 16; y++) {
            p.set(x, y, z);
            if (!level.getBlockState(p).isAir()) level.setBlock(p, st(Blocks.AIR), FLAGS);
        }
    }

    /** The top block of a column: sand at the beach, grass inland, mud and resin creeping out from the hive. */
    private BlockState surface(int x, int z, boolean beach, double toHive, RandomSource r) {
        if (beach) return st(Blocks.SAND);
        double k = n(x, z, l.seed());
        double creep = l.hiveRadius() + (l.small() ? 5 : 12) + 3 * k;
        if (toHive < creep) {
            double v = n(x * 2, z * 2, l.seed() + 5);
            return v > 0.6 ? st(Blocks.HONEYCOMB_BLOCK) : v > -0.1 ? st(Blocks.PACKED_MUD) : v > -0.7 ? st(Blocks.MUD) : st(Blocks.BROWN_TERRACOTTA);
        }
        for (BlockPos nest : l.nests()) {
            double dn = Math.hypot(x - nest.getX(), z - nest.getZ());
            if (dn < (l.small() ? 6 : 11) + 2 * k) return k > 0.5 ? st(Blocks.PACKED_MUD) : st(Blocks.MUD);
        }
        if (k > 1.05) return st(Blocks.COARSE_DIRT);
        if (k < -1.15) return st(Blocks.PODZOL);
        if (r.nextFloat() < 0.03f) return st(Blocks.ROOTED_DIRT);
        return st(Blocks.GRASS_BLOCK);
    }

    // ------------------------------------------------------------------ the great hive

    /** The level the hive stands on (its mouth's ground). */
    private int hiveBase() {
        return l.mouth().getY();
    }

    /** How high the hive's dome stands over its base at a distance from its middle. */
    private double domeHeight(double r, int x, int z) {
        double k = Math.min(1, r / l.hiveRadius());
        double bumps = (1 - k) * 2.2 * n(x * 3, z * 3, l.seed() + 11);
        return l.hiveHeight() * Math.pow(Math.max(0, 1 - k * k), 0.55) + bumps;
    }

    private void hive(ServerLevel level) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        RandomSource r = RandomSource.create(l.seed() ^ 0xB1EEL);
        int cx = l.center().getX(), cz = l.center().getZ(), base = hiveBase(), R = l.hiveRadius();
        for (int dx = -R - 1; dx <= R + 1; dx++) {
            for (int dz = -R - 1; dz <= R + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > R + 0.5) continue;
                int x = cx + dx, z = cz + dz;
                int ground = IsleWorld.groundAt(l, x, z);
                int top = base + (int) Math.round(domeHeight(d, x, z));
                for (int y = Math.min(ground, base) + 1; y <= top; y++) {
                    boolean skin = y >= top - 1 || d > R - 2;
                    put(level, p.set(x, y, z), skin ? hiveSkin(x, y, z, r) : st(Blocks.PACKED_MUD));
                }
                if (top < ground) continue;
                // the hive's ground round it flattened to its base
                put(level, p.set(x, base, z), st(Blocks.PACKED_MUD));
            }
        }
        // spires: towers of resin rising out of the dome, each lit at its tip (none over the mouth)
        int spires = l.small() ? 2 : 5;
        for (int i = 0; i < spires; i++) {
            double a = Math.toRadians(-150 + 300.0 * i / Math.max(1, spires - 1)) + (r.nextDouble() - 0.5) * 0.3;
            double far = R * (0.35 + 0.25 * r.nextDouble());
            int sx = cx + (int) Math.round(Math.sin(a) * far), sz = cz - (int) Math.round(Math.cos(a) * far);
            int foot = base + (int) Math.round(domeHeight(far, sx, sz)) - 1;
            int tall = l.small() ? 4 + r.nextInt(3) : 10 + r.nextInt(8);
            double r0 = l.small() ? 1.4 : 2.6;
            int lean = r.nextInt(3) - 1;
            for (int h = 0; h <= tall; h++) {
                double rad = r0 * (1 - h / (double) (tall + 1)) + 0.4;
                int ox = h > tall / 2 ? lean : 0;
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        if (dx * dx + dz * dz > rad * rad) continue;
                        put(level, p.set(sx + dx + ox, foot + h, sz + dz), hiveSkin(sx + dx, foot + h, sz + dz, r));
                    }
                }
            }
            put(level, p.set(sx + lean, foot + tall + 1, sz), st(Blocks.OCHRE_FROGLIGHT));
        }
    }

    /** The hive's skin: honeycomb and mud, with veins of light running through it. */
    private BlockState hiveSkin(int x, int y, int z, RandomSource r) {
        double vein = Math.sin(x * 0.45 + y * 0.6) + Math.cos(z * 0.41 - y * 0.35);
        if (Math.abs(vein) < 0.07) return st(Blocks.OCHRE_FROGLIGHT);
        double v = n(x * 2 + y, z * 2 - y, l.seed() + 3);
        if (v > 0.35) return st(Blocks.HONEYCOMB_BLOCK);
        if (v > -0.25) return st(Blocks.MUD_BRICKS);
        if (v > -0.8) return st(Blocks.PACKED_MUD);
        return r.nextInt(6) == 0 ? st(Blocks.SHROOMLIGHT) : st(Blocks.BROWN_TERRACOTTA);
    }

    /** The mouth's opening: half its width, and its height. */
    private int mouthHalf() {
        return l.small() ? 1 : 2;
    }

    private int mouthHeight() {
        return l.small() ? 3 : 5;
    }

    /** The mouth on the hive's south side, the throat behind it and the pit down into the dark, then the seal over it. */
    private void mouth(ServerLevel level) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int cx = l.center().getX(), base = hiveBase();
        int half = mouthHalf(), high = mouthHeight();
        int outer = l.mouth().getZ() + 2, inner = l.descent().getZ() - 2;
        // an arch of mud brick standing proud of the dome round the opening
        for (int dx = -half - 2; dx <= half + 2; dx++) {
            for (int y = base + 1; y <= base + high + 2; y++) {
                boolean frame = Math.abs(dx) > half || y > base + high;
                if (!frame) continue;
                for (int z = outer - 1; z <= outer; z++) put(level, p.set(cx + dx, y, z), (y + dx) % 4 == 0 ? st(Blocks.OCHRE_FROGLIGHT) : st(Blocks.MUD_BRICKS));
            }
        }
        // the throat: a passage in, lit dimly
        for (int z = inner; z <= outer; z++) {
            for (int dx = -half; dx <= half; dx++) {
                put(level, p.set(cx + dx, base, z), st(Blocks.PACKED_MUD));
                for (int y = base + 1; y <= base + high; y++) put(level, p.set(cx + dx, y, z), st(Blocks.AIR));
            }
            if ((z - inner) % 3 == 1) {
                put(level, p.set(cx - half - 1, base + high - 1, z), st(Blocks.SHROOMLIGHT));
                put(level, p.set(cx + half + 1, base + high - 1, z), st(Blocks.SHROOMLIGHT));
            }
        }
        // the hollow round the pit
        int room = l.small() ? 3 : 5;
        BlockPos d = l.descent();
        for (int dx = -room; dx <= room; dx++) {
            for (int dz = -room; dz <= room; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > room + 0.4) continue;
                int ceil = base + high + (int) Math.round((room - dist) * 0.5);
                put(level, p.set(d.getX() + dx, base, d.getZ() + dz), st(Blocks.PACKED_MUD));
                for (int y = base + 1; y <= ceil; y++) put(level, p.set(d.getX() + dx, y, d.getZ() + dz), st(Blocks.AIR));
            }
        }
        // the pit: black, ringed with light
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean ring = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                if (ring) {
                    put(level, p.set(d.getX() + dx, base, d.getZ() + dz), (dx + dz) % 2 == 0 ? st(Blocks.OCHRE_FROGLIGHT) : st(Blocks.MUD_BRICKS));
                    continue;
                }
                for (int y = base - 3; y <= base; y++) put(level, p.set(d.getX() + dx, y, d.getZ() + dz), st(Blocks.AIR));
                put(level, p.set(d.getX() + dx, base - 4, d.getZ() + dz), st(Blocks.BLACK_CONCRETE));
                for (int y = base - 4; y <= base; y++) {
                    for (Direction side : Direction.Plane.HORIZONTAL) {
                        BlockPos q = new BlockPos(d.getX() + dx, y, d.getZ() + dz).relative(side);
                        if (Math.abs(q.getX() - d.getX()) > 1 || Math.abs(q.getZ() - d.getZ()) > 1) put(level, q, st(Blocks.BLACKSTONE));
                    }
                }
            }
        }
        seal(level, l, true);
    }

    /** Seals the hive's mouth with resin ({@code shut}), or tears the seal away. */
    static void seal(ServerLevel level, IsleWorld.Layout l, boolean shut) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        RandomSource r = RandomSource.create(l.seed() ^ 0x5EA1L);
        int cx = l.center().getX(), base = l.mouth().getY();
        int half = l.small() ? 1 : 2, high = l.small() ? 3 : 5;
        int outer = l.mouth().getZ() + 2;
        for (int z = outer - 1; z <= outer; z++) {
            for (int dx = -half; dx <= half; dx++) {
                for (int y = base + 1; y <= base + high; y++) {
                    p.set(cx + dx, y, z);
                    if (shut) {
                        float roll = r.nextFloat();
                        put(level, p, roll < 0.45f ? st(Blocks.HONEYCOMB_BLOCK) : roll < 0.75f ? st(Blocks.PACKED_MUD) : roll < 0.95f ? st(Blocks.MUD_BRICKS)
                                : st(Blocks.OCHRE_FROGLIGHT));
                    } else if (!level.getBlockState(p).isAir()) {
                        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(p)), p.getX() + 0.5, p.getY() + 0.5,
                                p.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.1);
                        level.setBlock(p, st(Blocks.AIR), FLAGS);
                    }
                }
            }
        }
        if (!shut) {
            level.playSound(null, l.mouth(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 3f, 0.5f);
            level.playSound(null, l.mouth(), SoundEvents.SLIME_SQUISH, SoundSource.BLOCKS, 3f, 0.4f);
        }
    }

    // ------------------------------------------------------------------ the nests

    private static int nestInner(IsleWorld.Layout l) {
        return l.small() ? 3 : 5;
    }

    /** Raises a nest's ring of resin ({@code burned}: scorched black and burning). */
    static void nest(ServerLevel level, IsleWorld.Layout l, int i, boolean burned) {
        BlockPos n = l.nests().get(i);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        RandomSource r = RandomSource.create(l.seed() ^ (0x7E57L + i * 31L));
        int inner = nestInner(l);
        double outer = inner + (l.small() ? 1.6 : 2.6);
        int high = l.small() ? 3 : 5;
        // the openings: one towards the hive, one away from it
        double toHive = Math.atan2(l.center().getX() - n.getX(), -(l.center().getZ() - n.getZ()));
        int reach = (int) Math.ceil(outer) + 1;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > outer + 0.5) continue;
                int x = n.getX() + dx, z = n.getZ() + dz;
                if (d <= inner + 0.5) {
                    // its floor, flattened to the nest's ground
                    put(level, p.set(x, n.getY(), z), burned ? (r.nextInt(3) == 0 ? st(Blocks.BLACKSTONE) : st(Blocks.MUD)) : floorOf(r));
                    for (int y = n.getY() + 1; y <= n.getY() + high + 2; y++) put(level, p.set(x, y, z), st(Blocks.AIR));
                    if (!burned && d > inner - 1.2 && r.nextInt(5) == 0) {
                        put(level, p.set(x, n.getY() + 1, z), clutch(r));
                    }
                    continue;
                }
                double a = Math.atan2(dx, -dz);
                double off1 = Math.abs(Mth.wrapDegrees(Math.toDegrees(a - toHive)));
                double off2 = Math.abs(Mth.wrapDegrees(Math.toDegrees(a - toHive - Math.PI)));
                double gap = Math.toDegrees(Math.atan2(l.small() ? 1.2 : 1.8, d));
                boolean open = off1 < gap || off2 < gap;
                put(level, p.set(x, n.getY(), z), st(Blocks.PACKED_MUD));
                int h = high - (d > outer - 0.6 ? 1 : 0) + (r.nextInt(3) == 0 ? 1 : 0);
                for (int y = n.getY() + 1; y <= n.getY() + high + 2; y++) {
                    p.set(x, y, z);
                    if (open || y > n.getY() + h) {
                        put(level, p, st(Blocks.AIR));
                        continue;
                    }
                    put(level, p, burned ? scorched(r) : nestWall(r, y == n.getY() + h));
                }
                // a burned nest smoulders on here and there (embers that neither spread nor go out)
                if (burned && !open && r.nextInt(7) == 0) {
                    put(level, p.set(x, n.getY() + h, z), st(Blocks.MAGMA_BLOCK));
                    put(level, p.set(x, n.getY() + h + 1, z), st(Blocks.CAMPFIRE));
                }
            }
        }
    }

    private static BlockState floorOf(RandomSource r) {
        float roll = r.nextFloat();
        return roll < 0.5f ? st(Blocks.MUD) : roll < 0.8f ? st(Blocks.PACKED_MUD) : st(Blocks.HONEYCOMB_BLOCK);
    }

    private static BlockState nestWall(RandomSource r, boolean top) {
        float roll = r.nextFloat();
        if (top && roll < 0.12f) return st(Blocks.OCHRE_FROGLIGHT);
        return roll < 0.5f ? st(Blocks.HONEYCOMB_BLOCK) : roll < 0.78f ? st(Blocks.PACKED_MUD) : st(Blocks.MUD_BRICKS);
    }

    private static BlockState scorched(RandomSource r) {
        float roll = r.nextFloat();
        return roll < 0.45f ? st(Blocks.BLACKSTONE) : roll < 0.7f ? st(Blocks.COAL_BLOCK) : roll < 0.85f ? st(Blocks.MAGMA_BLOCK) : st(Blocks.BASALT);
    }

    // ------------------------------------------------------------------ the camp

    private void camp(ServerLevel level) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        RandomSource r = RandomSource.create(l.seed() ^ 0xCA4FL);
        BlockPos c = l.camp();
        int y = c.getY();
        int R = l.small() ? 6 : 10;
        // a level patch of beach, boards laid at its middle
        for (int dx = -R - 2; dx <= R + 2; dx++) {
            for (int dz = -R - 2; dz <= R + 6; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz * 0.6);
                if (d > R + 2.5) continue;
                int x = c.getX() + dx, z = c.getZ() + dz;
                if (!l.land(x, z)) continue;
                for (int yy = y - 2; yy < y; yy++) put(level, p.set(x, yy, z), st(Blocks.SANDSTONE));
                put(level, p.set(x, y, z), d < 3.2 ? st(Blocks.SPRUCE_PLANKS) : (x * 7 + z * 3) % 9 == 0 ? st(Blocks.SMOOTH_SANDSTONE) : st(Blocks.SAND));
                for (int yy = y + 1; yy <= y + 8; yy++) put(level, p.set(x, yy, z), st(Blocks.AIR));
            }
        }
        // tents: white canvas over a ridge
        int[][] tents = l.small() ? new int[][]{{-4, -1}, {4, -1}} : new int[][]{{-7, -2}, {7, -2}, {-6, 4}, {6, 4}};
        for (int[] t : tents) tent(level, c.offset(t[0], 0, t[1]), Direction.SOUTH);
        // fires, crates and the briefing lectern
        for (int side = -1; side <= 1; side += 2) {
            BlockPos f = c.offset(side * 3, 1, -2);
            level.setBlock(f, st(Blocks.CAMPFIRE), 3);
            put(level, f.offset(side, 0, 2), st(Blocks.BARREL));
            put(level, f.offset(side * 2, 0, 2), st(Blocks.BARREL));
        }
        put(level, c.offset(0, 1, 2), st(Blocks.LECTERN));
        // the Association's banners and lanterns on posts
        for (int side = -1; side <= 1; side += 2) {
            BlockPos post = c.offset(side * (R - 2), 1, -R + 3);
            put(level, post, st(Blocks.SPRUCE_FENCE));
            put(level, post.above(), st(Blocks.SPRUCE_FENCE));
            level.setBlock(post.above(2), st(Blocks.LIGHT_BLUE_BANNER).setValue(BannerBlock.ROTATION, RotationSegment.convertToSegment(Direction.NORTH)), 3);
            BlockPos lamp = c.offset(side * (R - 3), 1, 3);
            put(level, lamp, st(Blocks.SPRUCE_FENCE));
            put(level, lamp.above(), st(Blocks.LANTERN).setValue(LanternBlock.HANGING, false));
        }
        // the circle of light the hunters arrive and leave by
        BlockPos o = l.circle();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > 3.3) continue;
                p.set(o.getX() + dx, o.getY(), o.getZ() + dz);
                put(level, p, d > 2.2 ? st(Blocks.SEA_LANTERN) : d > 1.2 ? st(Blocks.SMOOTH_QUARTZ) : st(Blocks.CHISELED_QUARTZ_BLOCK));
                for (int yy = 1; yy <= 4; yy++) put(level, p.set(o.getX() + dx, o.getY() + yy, o.getZ() + dz), st(Blocks.AIR));
            }
        }
        for (int[] k : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
            BlockPos at = o.offset(k[0], 1, k[1]);
            put(level, at.below(), st(Blocks.POLISHED_DEEPSLATE));
            put(level, at, st(Blocks.POLISHED_DEEPSLATE_WALL));
            put(level, at.above(), st(Blocks.END_ROD));
        }
    }

    /** A tent of white canvas, its open end towards {@code facing}. */
    private static void tent(ServerLevel level, BlockPos at, Direction facing) {
        Direction right = facing.getClockWise();
        for (int depth = 0; depth < 4; depth++) {
            BlockPos row = at.relative(facing.getOpposite(), depth);
            put(level, row.relative(right, -1).above(), st(Blocks.WHITE_WOOL));
            put(level, row.relative(right, 1).above(), st(Blocks.WHITE_WOOL));
            put(level, row.above(2), st(Blocks.WHITE_WOOL));
            put(level, row.above(), depth == 3 ? st(Blocks.WHITE_WOOL) : st(Blocks.AIR));
            put(level, row, st(Blocks.SPRUCE_PLANKS));
        }
        put(level, at.above().relative(facing.getOpposite(), 1), st(Blocks.WHITE_CARPET));
    }

    // ------------------------------------------------------------------ the road and the ruins

    /** A cracked road from the camp to the hive's mouth. */
    private void road(ServerLevel level, boolean now) {
        BlockPos a = l.camp(), b = l.mouth();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        RandomSource r = RandomSource.create(l.seed() ^ 0x40ADL);
        double len = Math.hypot(b.getX() - a.getX(), b.getZ() - a.getZ());
        int from = l.small() ? 6 : 10;
        for (int s = from; s < len - 3; s++) {
            double k = s / len;
            double wobble = Math.sin(s * 0.09 + l.seed() % 7) * (l.small() ? 0.5 : 3.0);
            int x = (int) Math.round(Mth.lerp(k, a.getX(), b.getX()) + wobble), z = (int) Math.round(Mth.lerp(k, a.getZ(), b.getZ()));
            for (int w = -1; w <= 1; w++) {
                int xx = x + w;
                if (!l.land(xx, z)) continue;
                if (!now && level.getChunkSource().getChunkNow(xx >> 4, z >> 4) == null) continue;
                int y = IsleWorld.groundAt(l, xx, z);
                if (Math.hypot(xx - l.center().getX(), z - l.center().getZ()) < l.hiveRadius() + 1) continue;
                float roll = r.nextFloat();
                if (roll < 0.12f) continue;
                p.set(xx, y, z);
                put(level, p, roll < 0.55f ? st(Blocks.GRAY_CONCRETE) : roll < 0.8f ? st(Blocks.CRACKED_STONE_BRICKS) : st(Blocks.ANDESITE));
                p.set(xx, y + 1, z);
                if (!level.getBlockState(p).isAir() && level.getBlockState(p).canBeReplaced()) level.setBlock(p, st(Blocks.AIR), FLAGS);
            }
        }
    }

    /** A ruin of the resort: broken white walls and windows, a palm beside it, the swarm's resin creeping over it. */
    private void ruin(ServerLevel level, BlockPos at, long seed) {
        RandomSource r = RandomSource.create(seed);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int w = l.small() ? 5 : 7 + r.nextInt(5), d = l.small() ? 4 : 6 + r.nextInt(4);
        int high = 3 + r.nextInt(l.small() ? 2 : 4);
        int x0 = at.getX() - w / 2, z0 = at.getZ() - d / 2;
        int y = at.getY();
        BlockState wall = r.nextBoolean() ? st(Blocks.WHITE_CONCRETE) : st(Blocks.SMOOTH_QUARTZ);
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < d; dz++) {
                int x = x0 + dx, z = z0 + dz;
                boolean edge = dx == 0 || dz == 0 || dx == w - 1 || dz == d - 1;
                put(level, p.set(x, y, z), r.nextInt(6) == 0 ? st(Blocks.CRACKED_STONE_BRICKS) : st(Blocks.POLISHED_DIORITE));
                // the walls stand broken: a ragged top, holes, a few windows left
                int top = high - (int) Math.floor(Math.max(0, n(x * 3, z * 3, seed) + 1) * 1.6);
                for (int yy = 1; yy <= high + 2; yy++) {
                    p.set(x, y + yy, z);
                    if (!edge || yy > top) {
                        put(level, p, st(Blocks.AIR));
                        continue;
                    }
                    boolean window = yy == 2 && ((dx + dz) % 3 == 1);
                    if (window) put(level, p, r.nextInt(3) == 0 ? st(Blocks.AIR) : st(Blocks.GLASS_PANE));
                    else put(level, p, r.nextInt(9) == 0 ? st(Blocks.HONEYCOMB_BLOCK) : r.nextInt(7) == 0 ? st(Blocks.LIGHT_GRAY_CONCRETE) : wall);
                }
            }
        }
        // a doorway, rubble, cobwebs
        put(level, p.set(x0 + w / 2, y + 1, z0), st(Blocks.AIR));
        put(level, p.set(x0 + w / 2, y + 2, z0), st(Blocks.AIR));
        for (int i = 0; i < 3; i++) {
            int x = x0 + 1 + r.nextInt(Math.max(1, w - 2)), z = z0 + 1 + r.nextInt(Math.max(1, d - 2));
            put(level, p.set(x, y + 1, z), r.nextBoolean() ? st(Blocks.COBWEB) : st(Blocks.WHITE_CONCRETE));
        }
        // a palm, leaning
        int px = x0 - 2 + (r.nextBoolean() ? 0 : w + 3), pz = z0 + r.nextInt(Math.max(1, d));
        if (l.land(px, pz)) palm(level, new BlockPos(px, IsleWorld.groundAt(l, px, pz), pz), r);
    }

    private void palm(ServerLevel level, BlockPos foot, RandomSource r) {
        int tall = l.small() ? 4 : 5 + r.nextInt(3);
        Direction lean = Direction.Plane.HORIZONTAL.getRandomDirection(r);
        BlockPos at = foot;
        for (int h = 1; h <= tall; h++) {
            at = foot.above(h);
            if (h > tall * 2 / 3) at = at.relative(lean);
            put(level, at, st(Blocks.JUNGLE_LOG));
        }
        BlockState leaf = st(Blocks.JUNGLE_LEAVES).setValue(LeavesBlock.PERSISTENT, true);
        put(level, at.above(), leaf);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            for (int k = 1; k <= 3; k++) {
                BlockPos q = at.above(k == 3 ? 0 : 1).relative(dir, k);
                put(level, q, leaf);
            }
        }
    }

    // ------------------------------------------------------------------ the queen's chamber

    /** How high the chamber's vault stands over its floor at a distance from its middle. */
    private int ceilingAt(double r) {
        double k = Math.min(1, r / (l.chamberRadius() + 1.0));
        return Math.max(4, (int) Math.round(l.chamberHeight() * Math.sqrt(Math.max(0, 1 - k * k)) + 2));
    }

    private void chamber(ServerLevel level) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        RandomSource r = RandomSource.create(l.seed() ^ 0xC4A3BE2L);
        BlockPos c = l.chamber();
        int f = c.getY(), R = l.chamberRadius(), shell = 3;
        for (int dx = -R - shell; dx <= R + shell; dx++) {
            for (int dz = -R - shell; dz <= R + shell + 8; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                int x = c.getX() + dx, z = c.getZ() + dz;
                boolean tunnel = Math.abs(dx) <= 2 + shell && dz > 0 && dz <= R + 8;
                if (d > R + shell + 0.5 && !tunnel) continue;
                int ceil = d <= R + 0.5 ? f + ceilingAt(d) : f;
                int roof = f + l.chamberHeight() + 2 + shell;
                for (int y = f - shell; y <= roof; y++) {
                    p.set(x, y, z);
                    boolean open = d <= R + 0.5 && y > f && y <= ceil;
                    // the passage out to the south, where the way up is
                    boolean passage = Math.abs(dx) <= 2 && dz > 0 && dz <= R + 4 && y > f && y <= f + 5;
                    if (open || passage) {
                        put(level, p, st(Blocks.AIR));
                    } else if (d > R + shell + 0.5 && !(Math.abs(dx) <= 2 + shell && dz <= R + 4 + shell)) {
                        // past the shell and away from the passage: nothing to lay
                    } else {
                        put(level, p, y == f ? chamberFloor(x, z, r) : chamberWall(x, y, z, r));
                    }
                }
            }
        }
        // the dais: a low round platform of mud brick under the queen, ringed with light
        BlockPos dais = l.dais();
        int dr = l.small() ? 4 : 8;
        for (int dx = -dr; dx <= dr; dx++) {
            for (int dz = -dr; dz <= dr; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > dr + 0.4) continue;
                // ringed with light at its rim, and again half way in so that she is seen
                put(level, p.set(dais.getX() + dx, dais.getY(), dais.getZ() + dz),
                        d > dr - 0.8 ? ((dx + dz) % 3 == 0 ? st(Blocks.OCHRE_FROGLIGHT) : st(Blocks.MUD_BRICK_SLAB))
                                : d < 2 ? st(Blocks.CHISELED_TUFF_BRICKS)
                                : Math.abs(d - dr * 0.55) < 0.45 && (dx + dz) % 2 == 0 ? st(Blocks.OCHRE_FROGLIGHT) : st(Blocks.MUD_BRICKS));
            }
        }
        // the way up: a ring of light at the chamber's south end
        BlockPos a = l.arrival();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean ring = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                put(level, p.set(a.getX() + dx, a.getY(), a.getZ() + dz), ring ? st(Blocks.OCHRE_FROGLIGHT) : st(Blocks.SMOOTH_SANDSTONE));
            }
        }
    }

    private BlockState chamberFloor(int x, int z, RandomSource r) {
        double v = n(x * 2, z * 2, l.seed() + 21);
        if (v > 0.9) return st(Blocks.HONEYCOMB_BLOCK);
        if (r.nextInt(70) == 0) return st(Blocks.OCHRE_FROGLIGHT);
        return v > -0.2 ? st(Blocks.PACKED_MUD) : v > -0.9 ? st(Blocks.MUD) : st(Blocks.BROWN_TERRACOTTA);
    }

    private BlockState chamberWall(int x, int y, int z, RandomSource r) {
        int roll = r.nextInt(100);
        if (roll < 4) return st(Blocks.OCHRE_FROGLIGHT);
        if (roll < 5) return st(Blocks.SHROOMLIGHT);
        if (roll < 6) return st(Blocks.VERDANT_FROGLIGHT);
        double v = n(x * 2 + y, z * 2 - y, l.seed() + 13);
        return v > 0.3 ? st(Blocks.HONEYCOMB_BLOCK) : v > -0.4 ? st(Blocks.MUD_BRICKS) : st(Blocks.PACKED_MUD);
    }

    /** Eggs along the walls, resin dripping from the vault, a throne of resin behind the dais. */
    private void chamberDecor(ServerLevel level) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        RandomSource r = RandomSource.create(l.seed() ^ 0xDEC0L);
        BlockPos c = l.chamber();
        int f = c.getY(), R = l.chamberRadius();
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > R) continue;
                int x = c.getX() + dx, z = c.getZ() + dz;
                if (Math.abs(dx) <= 3 && dz > R - 6) continue;
                // resin dripping from the vault
                int ceil = f + ceilingAt(d);
                if (ceil - f > 7 && r.nextInt(30) == 0) {
                    int len = 1 + r.nextInt(3);
                    for (int k = 0; k < len; k++) {
                        DripstoneThickness t = k == len - 1 ? DripstoneThickness.TIP : k == 0 && len > 2 ? DripstoneThickness.BASE : DripstoneThickness.MIDDLE;
                        if (len == 1) t = DripstoneThickness.TIP;
                        put(level, p.set(x, ceil - k, z), st(Blocks.POINTED_DRIPSTONE)
                                .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN).setValue(PointedDripstoneBlock.THICKNESS, t));
                    }
                }
            }
        }
        // the throne: resin piled up behind the dais against the north wall, crowned with light
        BlockPos dais = l.dais();
        int tw = l.small() ? 2 : 4;
        for (int dx = -tw; dx <= tw; dx++) {
            int h = (l.small() ? 4 : 8) - Math.abs(dx);
            for (int y = 1; y <= h; y++) {
                put(level, p.set(dais.getX() + dx, dais.getY() + y, dais.getZ() - (l.small() ? 4 : 8)),
                        y == h ? st(Blocks.OCHRE_FROGLIGHT) : (y + dx) % 3 == 0 ? st(Blocks.HONEYCOMB_BLOCK) : st(Blocks.MUD_BRICKS));
            }
        }
        chamberEggs(level, l);
    }

    /**
     * The clutches of eggs along the chamber's walls, laid again before every landing where a fight trampled them. The
     * same clutch on the same spot every time: each spot's roll is made whether or not it gets one.
     */
    static void chamberEggs(ServerLevel level, IsleWorld.Layout l) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        RandomSource r = RandomSource.create(l.seed() ^ 0xE665L);
        BlockPos c = l.chamber();
        int f = c.getY(), R = l.chamberRadius();
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > R || d <= R - 4) continue;
                // the way in stays clear
                if (Math.abs(dx) <= 3 && dz > R - 6) continue;
                BlockState egg = clutch(r);
                if (r.nextInt(9) != 0) continue;
                p.set(c.getX() + dx, f + 1, c.getZ() + dz);
                BlockState was = level.getBlockState(p);
                if (was.isAir() || was.is(Blocks.TURTLE_EGG)) put(level, p, egg);
            }
        }
    }

    /**
     * A clutch of the swarm's eggs. Turtle eggs: they hatch only on sand, and none is laid on sand (sniffer eggs, used
     * before, hatched into sniffers after twenty minutes).
     */
    private static BlockState clutch(RandomSource r) {
        return st(Blocks.TURTLE_EGG).setValue(TurtleEggBlock.EGGS, 1 + r.nextInt(4)).setValue(TurtleEggBlock.HATCH, r.nextInt(3));
    }

    /** The cocoon on the wall behind the queen ({@code whole}), or burst open (the king has come out of it). */
    static void cocoon(ServerLevel level, IsleWorld.Layout l, boolean whole) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        RandomSource r = RandomSource.create(l.seed() ^ 0xC0C0L);
        BlockPos at = l.cocoon();
        int rad = l.small() ? 1 : 2;
        int tall = l.small() ? 4 : 7;
        for (int dx = -rad; dx <= rad; dx++) {
            for (int dz = -rad; dz <= rad; dz++) {
                for (int y = 1; y <= tall; y++) {
                    double bulge = rad + 0.5 - Math.abs(y - tall / 2.0) / tall * 1.4;
                    if (Math.sqrt(dx * dx + dz * dz) > bulge) continue;
                    p.set(at.getX() + dx, at.getY() + y, at.getZ() + dz);
                    if (whole) {
                        float roll = r.nextFloat();
                        put(level, p, roll < 0.5f ? st(Blocks.HONEYCOMB_BLOCK) : roll < 0.75f ? st(Blocks.OCHRE_FROGLIGHT) : roll < 0.9f
                                ? st(Blocks.PEARLESCENT_FROGLIGHT) : st(Blocks.MUD_BRICKS));
                    } else {
                        BlockState was = level.getBlockState(p);
                        if (!was.isAir()) {
                            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, was), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5,
                                    10, 0.4, 0.4, 0.4, 0.2);
                            level.setBlock(p, st(Blocks.AIR), FLAGS);
                        }
                    }
                }
            }
        }
        if (!whole) {
            // its husk left in shreds on the floor
            for (int i = 0; i < 6; i++) {
                int dx = r.nextInt(rad * 2 + 3) - rad - 1, dz = r.nextInt(rad * 2 + 3) - rad - 1;
                p.set(at.getX() + dx, at.getY() + 1, at.getZ() + dz);
                if (level.getBlockState(p).isAir()) put(level, p, r.nextBoolean() ? st(Blocks.HONEYCOMB_BLOCK) : st(Blocks.MUD_BRICK_SLAB));
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void put(ServerLevel level, BlockPos p, BlockState s) {
        level.setBlock(p, s, FLAGS);
    }

    private static BlockState st(Block b) {
        return b.defaultBlockState();
    }

    private static double sq(double v) {
        return v * v;
    }

    /** Smooth noise for ground and walls, about -1.5..1.5. */
    static double n(int x, int z, long seed) {
        double s = (seed & 1023) * 0.37;
        return Math.sin(x * 0.31 + s) * Math.cos(z * 0.27 - s * 0.5) + 0.5 * Math.sin((x + z) * 0.19 + s * 1.3);
    }
}
