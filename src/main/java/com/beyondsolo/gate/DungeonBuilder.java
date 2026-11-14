package com.beyondsolo.gate;

import com.beyondsolo.BeyondSolo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Carves a gate's cave into the gate dimension (which starts as solid rock), a slice at a time, so a big cave is
 * spread over a second or two of ticks instead of stalling the server: rooms are hollowed out of the rock with an
 * uneven, noisy edge and a flat floor, tunnels are dug along curves between them, every surface gets the theme's
 * rock, and then the lights, crystals, stalagmites and side-cave chests go in.
 */
public final class DungeonBuilder {
    /** Blocks looked at per tick. A big S-rank cave is about 250 000. */
    public static final int BUDGET = 30_000;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private interface Step {
        /** Does some work; returns the number of blocks looked at, or -1 when finished. */
        int run(ServerLevel level);
    }

    private final GateInstance gate;
    private final DungeonLayout layout;
    private final GateTheme theme;
    private final Deque<Step> steps = new ArrayDeque<>();
    private final RandomSource random;

    public DungeonBuilder(GateInstance gate) {
        this.gate = gate;
        this.layout = gate.layout();
        this.theme = gate.theme();
        this.random = new LegacyRandomSource(gate.seed() ^ 0x5DEECE66DL);
        for (DungeonLayout.Room room : layout.rooms()) steps.add(new RoomStep(room));
        for (DungeonLayout.Tunnel t : layout.tunnels()) steps.add(new TunnelStep(t));
        for (DungeonLayout.Room room : layout.rooms()) steps.add(level -> {
            decorate(level, room);
            return -1;
        });
    }

    public GateInstance gate() {
        return gate;
    }

    /**
     * Fills the cave's footprint with rock first. Only needed where the cave is not cut from solid rock (the
     * game-test fallback, see {@link GateService#dungeon}).
     */
    public DungeonBuilder walledIn() {
        Deque<Step> fill = new ArrayDeque<>();
        int[] b = layout.bounds();
        BlockPos o = gate.origin();
        int minY = DungeonLayout.MIN_Y - 16, maxY = DungeonLayout.MAX_Y + 16;
        int[] x = {b[0] - 6 + o.getX()};
        int maxX = b[2] + 6 + o.getX();
        fill.add(level -> {
            if (x[0] > maxX) return -1;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int y = minY; y <= maxY; y++) {
                for (int z = b[1] - 6 + o.getZ(); z <= b[3] + 6 + o.getZ(); z++) {
                    p.set(x[0], y, z);
                    if (level.getBlockState(p).isAir()) level.setBlock(p, Blocks.STONE.defaultBlockState(), FLAGS);
                }
            }
            x[0]++;
            return (maxY - minY) * (b[3] - b[1] + 12);
        });
        fill.addAll(steps);
        steps.clear();
        steps.addAll(fill);
        return this;
    }

    /** Works for one tick. Returns true once the whole cave is built. */
    public boolean tick(ServerLevel level) {
        int spent = 0;
        while (!steps.isEmpty() && spent < BUDGET) {
            int n = steps.peekFirst().run(level);
            if (n < 0) {
                steps.pollFirst();
                spent += 200;
            } else {
                spent += n;
            }
        }
        return steps.isEmpty();
    }

    /** Builds everything right away (tests and commands). */
    public void finish(ServerLevel level) {
        while (!tick(level)) {
            // keep going
        }
    }

    // ------------------------------------------------------------------ rooms

    private final class RoomStep implements Step {
        private final DungeonLayout.Room room;
        private final BlockPos c;
        private int x;
        private final int maxX;

        RoomStep(DungeonLayout.Room room) {
            this.room = room;
            this.c = gate.world(room.center());
            this.x = c.getX() - room.rx() - 3;
            this.maxX = c.getX() + room.rx() + 3;
        }

        @Override
        public int run(ServerLevel level) {
            if (x > maxX) return -1;
            int floor = c.getY() - (room.center().getY() - room.floorY());
            int n = 0;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int y = c.getY() - room.ry() - 3; y <= c.getY() + room.ry() + 3; y++) {
                for (int z = c.getZ() - room.rz() - 3; z <= c.getZ() + room.rz() + 3; z++) {
                    n++;
                    p.set(x, y, z);
                    boolean inside = inside(x, y, z) && y >= floor;
                    if (inside) {
                        level.setBlock(p, Blocks.CAVE_AIR.defaultBlockState(), FLAGS);
                        continue;
                    }
                    float f = shape(x, y, z);
                    if (f > 1.5f) continue;
                    boolean airAbove = inside(x, y + 1, z) && y + 1 >= floor;
                    level.setBlock(p, airAbove ? theme.floor(random) : theme.wall(random), FLAGS);
                }
            }
            x++;
            return n;
        }

        private float shape(int x, int y, int z) {
            float dx = (x - c.getX()) / (float) room.rx();
            float dy = (y - c.getY()) / (float) room.ry();
            float dz = (z - c.getZ()) / (float) room.rz();
            return dx * dx + dy * dy + dz * dz - 0.42f * (noise(x, y, z) - 0.5f);
        }

        private boolean inside(int x, int y, int z) {
            return shape(x, y, z) < 1f;
        }
    }

    // ------------------------------------------------------------------ tunnels

    private final class TunnelStep implements Step {
        private final DungeonLayout.Tunnel t;
        private final int samples;
        private int i;
        private boolean shell;

        TunnelStep(DungeonLayout.Tunnel t) {
            this.t = t;
            this.samples = Math.max(8, (int) (Math.sqrt(t.start().distSqr(t.end())) * 1.6));
        }

        @Override
        public int run(ServerLevel level) {
            if (i > samples) {
                if (shell) return -1;
                shell = true;
                i = 0;
            }
            double[] at = DungeonLayout.along(t, i / (double) samples);
            BlockPos o = gate.origin();
            double cx = at[0] + o.getX(), cy = at[1] + o.getY(), cz = at[2] + o.getZ();
            float r = t.radius() + 0.4f * (noise((int) cx, (int) cy, (int) cz) - 0.5f);
            int reach = Mth.ceil(r + 2);
            int n = 0;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int x = Mth.floor(cx) - reach; x <= Mth.floor(cx) + reach; x++) {
                for (int y = Mth.floor(cy) - reach; y <= Mth.floor(cy) + reach; y++) {
                    for (int z = Mth.floor(cz) - reach; z <= Mth.floor(cz) + reach; z++) {
                        n++;
                        double dx = x + 0.5 - cx, dy = (y + 0.5 - cy) * 1.15, dz = z + 0.5 - cz;
                        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        p.set(x, y, z);
                        if (!shell) {
                            // a flat floor: never dig below the line the tunnel's feet walk on
                            if (d < r && y + 0.5 >= cy - r * 0.62) level.setBlock(p, Blocks.CAVE_AIR.defaultBlockState(), FLAGS);
                        } else if (d < r + 1.6) {
                            BlockState here = level.getBlockState(p);
                            if (here.isAir() || !isRawRock(here)) continue;
                            boolean airAbove = level.getBlockState(p.above()).isAir();
                            level.setBlock(p, airAbove ? theme.floor(random) : theme.wall(random), FLAGS);
                        }
                    }
                }
            }
            i++;
            return n;
        }
    }

    private static boolean isRawRock(BlockState s) {
        return s.is(Blocks.STONE) || s.is(Blocks.DEEPSLATE);
    }

    // ------------------------------------------------------------------ decoration

    private void decorate(ServerLevel level, DungeonLayout.Room room) {
        RandomSource r = new LegacyRandomSource(gate.seed() * 31 + room.index());
        BlockPos c = gate.world(room.center());
        int floor = gate.world(new BlockPos(0, room.floorY(), 0)).getY();

        // light set into the walls: march out from the middle until we hit rock
        int lights = room.kind() == DungeonLayout.Kind.BOSS ? 18 : 7 + r.nextInt(4);
        for (int i = 0; i < lights; i++) {
            float a = r.nextFloat() * Mth.TWO_PI;
            float up = room.kind() == DungeonLayout.Kind.BOSS ? (i % 3) * 0.35f : r.nextFloat() * 0.8f;
            BlockPos hit = march(level, new BlockPos(c.getX(), floor + 1 + Math.round(up * room.ry()), c.getZ()), Mth.cos(a), 0, Mth.sin(a), room.reach() + 6);
            if (hit != null) level.setBlock(hit, theme.light.defaultBlockState(), FLAGS);
        }
        // soft fill light so the cave is never pitch black
        BlockState fill = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, room.kind() == DungeonLayout.Kind.BOSS ? 12 : 9);
        placeIfAir(level, new BlockPos(c.getX(), floor + 3, c.getZ()), fill);
        for (int i = 0; i < 4; i++) {
            float a = i * Mth.HALF_PI + 0.6f;
            placeIfAir(level, new BlockPos(c.getX() + Math.round(Mth.cos(a) * room.rx() * 0.55f), floor + 2, c.getZ() + Math.round(Mth.sin(a) * room.rz() * 0.55f)), fill);
        }

        // things on the floor, stalagmites and stalactites
        int scatter = 12 + r.nextInt(10);
        for (int i = 0; i < scatter; i++) {
            BlockPos at = floorSpot(level, c, room, floor, r, 0.85f);
            if (at != null) level.setBlock(at, scatterState(), FLAGS);
        }
        if (room.kind() != DungeonLayout.Kind.BOSS && room.kind() != DungeonLayout.Kind.ENTRANCE) {
            int spikes = 3 + r.nextInt(4);
            for (int i = 0; i < spikes; i++) {
                BlockPos at = floorSpot(level, c, room, floor, r, 0.7f);
                if (at == null) continue;
                int h = 1 + r.nextInt(4);
                for (int k = 0; k < h; k++) placeIfAir(level, at.above(k), k == h - 1 && r.nextInt(3) == 0 ? theme.accent(r) : theme.mainWall());
            }
        }
        int hanging = 4 + r.nextInt(5);
        for (int i = 0; i < hanging; i++) {
            float a = r.nextFloat() * Mth.TWO_PI, d = r.nextFloat() * 0.7f;
            BlockPos top = march(level, new BlockPos(c.getX() + Math.round(Mth.cos(a) * d * room.rx()), floor + 2, c.getZ() + Math.round(Mth.sin(a) * d * room.rz())), 0, 1, 0, room.ry() * 2 + 4);
            if (top == null) continue;
            int h = 1 + r.nextInt(3);
            for (int k = 1; k <= h; k++) placeIfAir(level, top.below(k), theme.mainWall());
        }

        switch (room.kind()) {
            case BOSS -> {
                // a ring of pillars around the arena, each crowned with light
                for (int i = 0; i < 8; i++) {
                    float a = i * Mth.TWO_PI / 8 + 0.2f;
                    int px = c.getX() + Math.round(Mth.cos(a) * room.rx() * 0.72f);
                    int pz = c.getZ() + Math.round(Mth.sin(a) * room.rz() * 0.72f);
                    int h = 5 + r.nextInt(3);
                    for (int k = 0; k < h; k++) {
                        BlockState s = k == h - 1 ? theme.light.defaultBlockState() : (k % 3 == 2 ? theme.accent(r) : theme.mainWall());
                        level.setBlock(new BlockPos(px, floor + k, pz), s, FLAGS);
                    }
                }
            }
            case SIDE -> {
                BlockPos at = new BlockPos(c.getX(), floor, c.getZ());
                level.setBlock(at.below(), theme.accent(r), FLAGS);
                level.setBlock(at, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(r)), FLAGS);
                if (level.getBlockEntity(at) instanceof RandomizableContainerBlockEntity chest) {
                    chest.setLootTable(sideLoot(), r.nextLong());
                }
            }
            default -> {}
        }
    }

    private ResourceKey<LootTable> sideLoot() {
        return switch (theme) {
            case E, D -> BuiltInLootTables.SIMPLE_DUNGEON;
            case C -> BuiltInLootTables.ABANDONED_MINESHAFT;
            case B -> BuiltInLootTables.ANCIENT_CITY;
            case A -> BuiltInLootTables.BASTION_OTHER;
            case S -> ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.withDefaultNamespace("chests/bastion_treasure"));
        };
    }

    private BlockState scatterState() {
        BlockState s = theme.scatter.defaultBlockState();
        if (theme.scatter instanceof MultifaceBlock) s = s.setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true);
        return s;
    }

    private BlockPos floorSpot(ServerLevel level, BlockPos c, DungeonLayout.Room room, int floor, RandomSource r, float spread) {
        for (int attempt = 0; attempt < 6; attempt++) {
            float a = r.nextFloat() * Mth.TWO_PI, d = (0.25f + r.nextFloat() * 0.75f) * spread;
            BlockPos p = new BlockPos(c.getX() + Math.round(Mth.cos(a) * d * room.rx()), floor, c.getZ() + Math.round(Mth.sin(a) * d * room.rz()));
            for (int dy = 2; dy >= -2; dy--) {
                BlockPos q = p.above(dy);
                if (level.getBlockState(q).isAir() && !level.getBlockState(q.below()).isAir() && !level.getBlockState(q.below()).is(Blocks.LIGHT)) {
                    return q;
                }
            }
        }
        return null;
    }

    private static void placeIfAir(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos).isAir()) level.setBlock(pos, state, FLAGS);
    }

    /** Walks from {@code from} in a direction and returns the first solid block, or null. */
    private static BlockPos march(ServerLevel level, BlockPos from, float dx, float dy, float dz, int max) {
        for (int i = 1; i <= max; i++) {
            BlockPos p = BlockPos.containing(from.getX() + 0.5 + dx * i, from.getY() + 0.5 + dy * i, from.getZ() + 0.5 + dz * i);
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && !s.is(Blocks.LIGHT)) return p;
        }
        return null;
    }

    // ------------------------------------------------------------------ noise

    /** Smooth 3D value noise in 0..1 with a feature size of about 5 blocks, seeded by the gate. */
    private float noise(int x, int y, int z) {
        float fx = x / 5f, fy = y / 4f, fz = z / 5f;
        int x0 = Mth.floor(fx), y0 = Mth.floor(fy), z0 = Mth.floor(fz);
        float tx = smooth(fx - x0), ty = smooth(fy - y0), tz = smooth(fz - z0);
        float v = 0;
        for (int i = 0; i < 8; i++) {
            int ix = i & 1, iy = (i >> 1) & 1, iz = (i >> 2) & 1;
            float w = (ix == 1 ? tx : 1 - tx) * (iy == 1 ? ty : 1 - ty) * (iz == 1 ? tz : 1 - tz);
            v += w * hash(x0 + ix, y0 + iy, z0 + iz);
        }
        return v;
    }

    private static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }

    private float hash(int x, int y, int z) {
        long h = gate.seed() ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 40) / (float) (1L << 24);
    }

    /** All rooms of a cave, in world positions (tests). */
    public static List<BlockPos> roomCenters(GateInstance gate) {
        List<BlockPos> out = new ArrayList<>();
        for (DungeonLayout.Room room : gate.layout().rooms()) out.add(gate.world(room.center()));
        return out;
    }
}
