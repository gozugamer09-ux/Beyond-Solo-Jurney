package com.beyondsolo.gate;

import com.beyondsolo.gate.DungeonLayout.Room;
import com.beyondsolo.gate.DungeonLayout.Shape;
import com.beyondsolo.gate.DungeonLayout.Space;
import com.beyondsolo.gate.DungeonLayout.SpaceShape;
import com.beyondsolo.gate.DungeonLayout.Style;
import com.beyondsolo.gate.DungeonLayout.Tunnel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Builds a gate's dungeon into the gate dimension (which starts as solid rock), a slice at a time, so a big one is
 * spread over a few seconds of ticks instead of stalling the server:
 * <ol>
 *     <li>the open spaces are cut (a chasm's void, a descent's shaft, a hollow and its lake), then the rooms (uneven
 *     caverns, built halls with coved ceilings, round halls with domes, smooth hive cells) and the passages (winding
 *     tubes, square corridors, the headroom of bridges, the groove of winding stairs);</li>
 *     <li>every rock face is dressed in the realm's blocks: floors, walls with their trim, ceilings;</li>
 *     <li>the structures go in: the chasm's sea and the lake, islands on their pillars, bridge decks with railings,
 *     steps in corridors, the walkway of winding stairs, terraces for camps in a hollow;</li>
 *     <li>then the dressing ({@link DungeonDecor}): lights, the realm's decorations, the boss's arena, caches, mana
 *     veins, and walls and fences are joined up to their neighbours.</li>
 * </ol>
 */
public final class DungeonBuilder {
    /** Blocks looked at per tick. A big S-rank chasm is about two million. */
    public static final int BUDGET = 30_000;
    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private interface Step {
        /** Does some work; returns the number of blocks looked at, or -1 when finished. */
        int run(ServerLevel level);
    }

    final GateInstance gate;
    final DungeonLayout layout;
    final GateTheme theme;
    final GateRealm realm;
    final RandomSource random;
    private final int ox, oz;
    private final Deque<Step> steps = new ArrayDeque<>();
    /** Walls, fences and bars placed: they are joined to their neighbours once everything is in. */
    private final List<BlockPos> joins = new ArrayList<>();
    private final DungeonDecor decor;

    public DungeonBuilder(GateInstance gate) {
        this.gate = gate;
        this.layout = gate.layout();
        this.theme = gate.theme();
        this.realm = layout.realm();
        this.random = new LegacyRandomSource(gate.seed() ^ 0x5DEECE66DL);
        this.ox = gate.origin().getX();
        this.oz = gate.origin().getZ();
        this.decor = new DungeonDecor(this);
        for (Space s : layout.spaces()) steps.add(new SpaceStep(s));
        for (Room room : layout.rooms()) steps.add(new RoomStep(room));
        for (Tunnel t : layout.tunnels()) steps.add(new TunnelStep(t));
        for (int[] box : layout.boxes(2)) steps.add(new SkinStep(box));
        steps.add(once(this::liquids));
        for (Room room : layout.rooms()) {
            if (room.shape() == Shape.ISLAND) steps.add(once(level -> island(level, room)));
            else steps.add(once(level -> {
                terrace(level, room);
                ledge(level, room);
            }));
        }
        for (Tunnel t : layout.tunnels()) steps.add(once(level -> walkway(level, t)));
        for (Space s : layout.spaces()) steps.add(once(level -> decor.space(level, s)));
        for (Room room : layout.rooms()) steps.add(once(level -> decor.room(level, room)));
        for (Tunnel t : layout.tunnels()) steps.add(once(level -> decor.passage(level, t)));
        steps.add(once(this::joinUp));
        // the hidden chamber's wall and dressing, the trap rooms' cores and vents
        steps.add(once(level -> com.beyondsolo.depths.DepthsBuild.build(level, gate)));
    }

    private static Step once(Consumer<ServerLevel> work) {
        return level -> {
            work.accept(level);
            return -1;
        };
    }

    public GateInstance gate() {
        return gate;
    }

    /**
     * Fills the dungeon's footprint with rock first. Only needed where it is not cut from solid rock (the game-test
     * fallback, see {@link GateService#dungeon}).
     */
    public DungeonBuilder walledIn() {
        Deque<Step> fill = new ArrayDeque<>();
        int[] b = layout.bounds();
        int[] h = layout.heights();
        int minY = h[0] - 6, maxY = h[1] + 6;
        int[] x = {b[0] - 6 + ox};
        int maxX = b[2] + 6 + ox;
        fill.add(level -> {
            if (x[0] > maxX) return -1;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int y = minY; y <= maxY; y++) {
                for (int z = b[1] - 6 + oz; z <= b[3] + 6 + oz; z++) {
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

    /** Works for one tick. Returns true once the whole dungeon is built. */
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

    // ------------------------------------------------------------------ helpers

    BlockPos world(BlockPos local) {
        return local.offset(ox, 0, oz);
    }

    int wx(double localX) {
        return Mth.floor(localX + 0.5) + ox;
    }

    int wz(double localZ) {
        return Mth.floor(localZ + 0.5) + oz;
    }

    static void carve(ServerLevel level, BlockPos p) {
        BlockState here = level.getBlockState(p);
        if (!here.isAir()) level.setBlock(p, Blocks.CAVE_AIR.defaultBlockState(), FLAGS);
    }

    static boolean isRawRock(BlockState s) {
        return s.is(Blocks.STONE) || s.is(Blocks.DEEPSLATE);
    }

    /** Remembers a block that joins up with its neighbours (walls, fences, bars). */
    void join(BlockPos p) {
        joins.add(p.immutable());
    }

    /** Sets a block where there is air (or liquid), and remembers it if it joins up. */
    void place(ServerLevel level, BlockPos p, BlockState state) {
        BlockState here = level.getBlockState(p);
        if (!here.isAir() && here.getFluidState().isEmpty()) return;
        level.setBlock(p, state, FLAGS);
        if (joins(state)) join(p);
    }

    /** Sets a block whatever is there, and remembers it if it joins up. */
    void put(ServerLevel level, BlockPos p, BlockState state) {
        level.setBlock(p, state, FLAGS);
        if (joins(state)) join(p);
    }

    static boolean joins(BlockState s) {
        return s.getBlock() instanceof net.minecraft.world.level.block.WallBlock
                || s.getBlock() instanceof net.minecraft.world.level.block.FenceBlock
                || s.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock;
    }

    BlockState railing() {
        return realm.railing.defaultBlockState();
    }

    BlockState slab() {
        return realm.slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    BlockState stairs(Direction facing) {
        return realm.stairs.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM);
    }

    /** True if a point (layout coordinates) is inside any room. */
    boolean inRoom(double x, double y, double z, double scale) {
        for (Room r : layout.rooms()) if (r.contains(x, y, z, scale)) return true;
        return false;
    }

    // ------------------------------------------------------------------ open spaces

    private final class SpaceStep implements Step {
        private final Space s;
        private final int cx, cz, maxX;
        private int x;

        SpaceStep(Space s) {
            this.s = s;
            this.cx = s.center().getX() + ox;
            this.cz = s.center().getZ() + oz;
            this.x = cx - s.rx() - 2;
            this.maxX = cx + s.rx() + 2;
        }

        @Override
        public int run(ServerLevel level) {
            if (x > maxX) return -1;
            int n = 0;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int z = cz - s.rz() - 2; z <= cz + s.rz() + 2; z++) {
                int[] span = span(s, x, z);
                n++;
                if (span == null) continue;
                for (int y = span[0]; y <= span[1]; y++) {
                    n++;
                    carve(level, p.set(x, y, z));
                }
            }
            x++;
            return n;
        }
    }

    /** The air of a space in one column (world x and z): {lowest, highest}, or null where it does not reach. */
    int[] span(Space s, int x, int z) {
        double dx = (x - s.center().getX() - ox) / (double) s.rx(), dz = (z - s.center().getZ() - oz) / (double) s.rz();
        double h = dx * dx + dz * dz;
        double edge = noise(x / 6.0, 0.5, z / 6.0) - 0.5;
        switch (s.shape()) {
            case SHAFT -> {
                if (h > 1 + (realm.built() ? 0 : edge * 0.15)) return null;
                return new int[]{s.floorY(), s.topY()};
            }
            case VOID -> {
                double k = h + edge * 0.12;
                if (k >= 1) return null;
                int top = s.center().getY() + (int) Math.round(s.ry() * Math.sqrt(1 - k) + edge * 6);
                return new int[]{s.floorY(), top};
            }
            case HOLLOW -> {
                double k = h + edge * 0.12;
                if (k >= 1) return null;
                int ground = hollowGround(s, x, z, k);
                int top = s.center().getY() + (int) Math.round(s.ry() * Math.sqrt(1 - k) + edge * 6);
                return top <= ground ? null : new int[]{ground, top};
            }
            case POOL -> {
                if (h >= 1) return null;
                int bottom = s.center().getY() - (int) Math.round(s.ry() * Math.sqrt(1 - h));
                return new int[]{bottom, s.center().getY() + 2};
            }
        }
        return null;
    }

    /** A hollow's floor in a column: low hills and bumps, rising up into the walls. */
    int hollowGround(Space s, int x, int z, double k) {
        double hills = noise(x / 13.0, 3.5, z / 13.0) - 0.5;
        double bumps = noise(x / 4.0, 9.5, z / 4.0) - 0.5;
        int g = s.floorY() + (int) Math.round(hills * 7 + bumps * 2);
        if (k > 0.6) g += (int) Math.round((k - 0.6) * 15);
        return g;
    }

    // ------------------------------------------------------------------ rooms

    private final class RoomStep implements Step {
        private final Room room;
        private final BlockPos c;
        private final int floor, pad, maxX;
        private int x;

        RoomStep(Room room) {
            this.room = room;
            this.c = world(room.center());
            this.floor = room.floorY();
            this.pad = room.natural() ? 3 : 1;
            this.x = c.getX() - room.rx() - pad;
            this.maxX = c.getX() + room.rx() + pad;
        }

        @Override
        public int run(ServerLevel level) {
            if (x > maxX) return -1;
            int n = 0;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int z = c.getZ() - room.rz() - pad; z <= c.getZ() + room.rz() + pad; z++) {
                for (int y = floor; y <= c.getY() + room.ry() + pad; y++) {
                    n++;
                    if (inside(x, y, z)) carve(level, p.set(x, y, z));
                }
            }
            x++;
            return n;
        }

        private boolean inside(int x, int y, int z) {
            int dxi = x - c.getX(), dzi = z - c.getZ();
            switch (room.shape()) {
                case CAVE, CELL -> {
                    float dx = dxi / (float) room.rx(), dy = (y - c.getY()) / (float) room.ry(), dz = dzi / (float) room.rz();
                    float rough = room.shape() == Shape.CAVE ? 0.42f : 0.12f;
                    return dx * dx + dy * dy + dz * dz - rough * (noise(x, y, z) - 0.5f) < 1f;
                }
                case HALL -> {
                    int top = c.getY() + room.ry();
                    int ax = Math.abs(dxi), az = Math.abs(dzi);
                    if (ax > room.rx() || az > room.rz() || y > top) return false;
                    // a coved ceiling: the top two courses step in
                    if (y == top) return ax <= room.rx() - 2 && az <= room.rz() - 2;
                    if (y == top - 1) return ax <= room.rx() - 1 && az <= room.rz() - 1;
                    return true;
                }
                default -> {
                    double d2 = dxi * (double) dxi + dzi * (double) dzi;
                    double r = room.rx() + 0.5;
                    if (d2 > r * r || y > c.getY() + room.ry()) return false;
                    int wallTop = c.getY() + room.ry() - 3;
                    if (y <= wallTop || room.shape() == Shape.ISLAND) return true;
                    // a dome
                    double t = (y - wallTop) / 3.5;
                    double rr = r * Math.sqrt(Math.max(0, 1 - t * t));
                    return d2 <= rr * rr;
                }
            }
        }
    }

    // ------------------------------------------------------------------ passages

    private final class TunnelStep implements Step {
        private final Tunnel t;
        private final List<int[]> path;
        private final int samples;
        private int i;

        TunnelStep(Tunnel t) {
            this.t = t;
            this.path = t.style() == Style.CORRIDOR ? corridorPath(t) : null;
            this.samples = path != null ? path.size() - 1 : t.samples();
        }

        @Override
        public int run(ServerLevel level) {
            if (i > samples) return -1;
            int n = switch (t.style()) {
                case TUBE -> tubeAt(level, i / (double) samples);
                case CORRIDOR -> corridorAt(level, path.get(i), i > 0 ? path.get(i - 1) : null);
                case BRIDGE -> bridgeAt(level, i / (double) samples);
                case SPIRAL -> spiralAt(level, i / (double) samples);
            };
            i++;
            return Math.max(1, n);
        }

        /** A natural tunnel: a noisy tube with a flat floor (never dug below the line its feet walk on). */
        private int tubeAt(ServerLevel level, double u) {
            double[] at = DungeonLayout.along(t, u);
            double cx = at[0] + ox, cy = at[1], cz = at[2] + oz;
            float r = t.radius() + 0.4f * (noise((int) cx, (int) cy, (int) cz) - 0.5f);
            int reach = Mth.ceil(r + 1);
            int n = 0;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int x = Mth.floor(cx) - reach; x <= Mth.floor(cx) + reach; x++) {
                for (int y = Mth.floor(cy) - reach; y <= Mth.floor(cy) + reach; y++) {
                    for (int z = Mth.floor(cz) - reach; z <= Mth.floor(cz) + reach; z++) {
                        n++;
                        double dx = x + 0.5 - cx, dy = (y + 0.5 - cy) * 1.15, dz = z + 0.5 - cz;
                        if (dx * dx + dy * dy + dz * dz < r * r && y + 0.5 >= cy - r * 0.62) carve(level, p.set(x, y, z));
                    }
                }
            }
            return n;
        }

        /** A built corridor: a square section with an arched top, its floor stepping where it climbs. */
        private int corridorAt(ServerLevel level, int[] cell, int[] prev) {
            int w = corridorHalf(t);
            int height = w >= 2 ? 5 : 4;
            int lo = cell[2], hi = cell[2] + height - 1;
            if (prev != null) {
                lo = Math.min(lo, prev[2]);
                hi = Math.max(hi, prev[2] + height - 1);
            }
            boolean corner = cell[0] == t.bend().getX() && cell[1] == t.bend().getZ();
            int px = -cell[4], pz = cell[3];
            int n = 0;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int a = -w; a <= w; a++) {
                for (int b = corner ? -w : 0; b <= (corner ? w : 0); b++) {
                    int x = cell[0] + (corner ? a : px * a) + ox, z = cell[1] + (corner ? b : pz * a) + oz;
                    for (int y = lo; y <= hi; y++) {
                        n++;
                        if (y == hi && (Math.abs(a) == w || Math.abs(b) == w) && w >= 1) continue;
                        carve(level, p.set(x, y, z));
                    }
                }
            }
            return n;
        }

        /** Head room over a bridge, where it passes through rock near its ends. */
        private int bridgeAt(ServerLevel level, double u) {
            double[] at = DungeonLayout.along(t, u);
            double[] perp = perpendicular(t);
            int n = 0;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (double o = -2; o <= 2; o += 0.5) {
                int x = wx(at[0] + perp[0] * o), z = wz(at[2] + perp[1] * o);
                int base = Mth.floor(at[1]);
                for (int y = base; y <= base + 3; y++) {
                    n++;
                    carve(level, p.set(x, y, z));
                }
            }
            return n;
        }

        /** The groove of winding stairs: a band round the axis, cut from the walkway up. */
        private int spiralAt(ServerLevel level, double u) {
            double[] at = DungeonLayout.along(t, u);
            int n = 0;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int lx = Mth.floor(at[0]) - 3; lx <= Mth.floor(at[0]) + 4; lx++) {
                for (int lz = Mth.floor(at[2]) - 3; lz <= Mth.floor(at[2]) + 4; lz++) {
                    n++;
                    double s = spiralSurface(t, lx, lz, u);
                    if (Double.isNaN(s)) continue;
                    int base = Mth.floor(s);
                    for (int y = base; y <= base + 4; y++) carve(level, p.set(lx + ox, y, lz + oz));
                }
            }
            return n;
        }
    }

    /** Half the width of a corridor, in whole blocks beside its middle (1: three wide, 2: five wide). */
    public static int corridorHalf(Tunnel t) {
        return Math.max(1, (int) Math.floor(t.radius() - 0.5));
    }

    /**
     * The cells along a corridor's middle (layout coordinates), in order: {x, z, feet height, step x, step z}. Its
     * legs run straight along an axis, and its floor climbs evenly from one end to the other.
     */
    static List<int[]> corridorPath(Tunnel t) {
        List<int[]> out = new ArrayList<>();
        int[][] legs = {{t.start().getX(), t.start().getZ(), t.bend().getX(), t.bend().getZ()},
                {t.bend().getX(), t.bend().getZ(), t.end().getX(), t.end().getZ()}};
        int la = Math.max(Math.abs(legs[0][2] - legs[0][0]), Math.abs(legs[0][3] - legs[0][1]));
        int lb = Math.max(Math.abs(legs[1][2] - legs[1][0]), Math.abs(legs[1][3] - legs[1][1]));
        int total = Math.max(1, la + lb);
        for (int leg = 0; leg < 2; leg++) {
            int[] l = legs[leg];
            int steps = leg == 0 ? la : lb;
            if (steps == 0) continue;
            int dx = Integer.signum(l[2] - l[0]), dz = Integer.signum(l[3] - l[1]);
            for (int j = leg == 1 && la > 0 ? 1 : 0; j <= steps; j++) {
                int x = l[0] + Math.round((l[2] - l[0]) * j / (float) steps);
                int z = l[1] + Math.round((l[3] - l[1]) * j / (float) steps);
                int at = (leg == 0 ? 0 : la) + j;
                int feet = Math.round(Mth.lerp(at / (float) total, t.start().getY(), t.end().getY()));
                out.add(new int[]{x, z, feet, dx, dz});
            }
        }
        if (out.isEmpty()) out.add(new int[]{t.start().getX(), t.start().getZ(), t.start().getY(), 1, 0});
        return out;
    }

    /** Across a bridge (unit vector, layout x and z). */
    static double[] perpendicular(Tunnel t) {
        double dx = t.end().getX() - t.start().getX(), dz = t.end().getZ() - t.start().getZ();
        double len = Math.max(1e-6, Math.sqrt(dx * dx + dz * dz));
        return new double[]{-dz / len, dx / len};
    }

    /**
     * The height of a winding stair's walkway over a cell (layout x, z), the turn of it nearest to {@code u}; NaN if
     * the cell is not on the walkway (too near or too far from the axis, or past either end).
     */
    static double spiralSurface(Tunnel t, double x, double z, double u) {
        double ax = t.bend().getX(), az = t.bend().getZ();
        double rho = Math.sqrt((x - ax) * (x - ax) + (z - az) * (z - az));
        if (Math.abs(rho - t.radius()) > 1.5) return Double.NaN;
        double turn = t.turn();
        if (Math.abs(turn) < 1e-6) return Double.NaN;
        double a0 = Math.atan2(t.start().getZ() - az, t.start().getX() - ax);
        double d = Mth.wrapDegrees((Math.atan2(z - az, x - ax) - (a0 + turn * u)) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        double uc = u + d / turn;
        if (uc < -0.002 || uc > 1.002) return Double.NaN;
        return Mth.lerp(Mth.clamp(uc, 0, 1), t.start().getY(), t.end().getY());
    }

    // ------------------------------------------------------------------ the realm's skin

    /** Dresses the bare rock round everything carved: floors where there is air above, ceilings, walls. */
    private final class SkinStep implements Step {
        private final int[] b;
        private int x;

        SkinStep(int[] box) {
            this.b = box;
            this.x = box[0] + ox;
        }

        @Override
        public int run(ServerLevel level) {
            if (x > b[3] + ox) return -1;
            int n = 0;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(), q = new BlockPos.MutableBlockPos();
            int lo = Math.max(b[1], level.getMinBuildHeight() + 1), hi = Math.min(b[4], level.getMaxBuildHeight() - 3);
            for (int z = b[2] + oz; z <= b[5] + oz; z++) {
                for (int y = lo; y <= hi; y++) {
                    n++;
                    if (!isRawRock(level.getBlockState(p.set(x, y, z)))) continue;
                    BlockState skin = skin(level, q, x, y, z);
                    if (skin != null) level.setBlock(p, skin, FLAGS);
                }
            }
            x++;
            return n;
        }
    }

    private BlockState skin(ServerLevel level, BlockPos.MutableBlockPos q, int x, int y, int z) {
        if (air(level, q.set(x, y + 1, z))) return realm.floorAt(x, z, random);
        if (air(level, q.set(x, y - 1, z))) return realm.ceiling(random);
        for (Direction d : HORIZONTAL) if (air(level, q.set(x + d.getStepX(), y, z + d.getStepZ()))) return realm.wallAt(y, random);
        // a second course behind the first, so a scratch in the wall does not show bare stone
        if (air(level, q.set(x, y + 2, z)) || air(level, q.set(x, y - 2, z))) return realm.wall(random);
        for (Direction d : HORIZONTAL) if (air(level, q.set(x + 2 * d.getStepX(), y, z + 2 * d.getStepZ()))) return realm.wall(random);
        return null;
    }

    private static boolean air(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).isAir();
    }

    // ------------------------------------------------------------------ structures

    /** The chasm's sea, the sump at the foot of a shaft, the hollow's lake. */
    private void liquids(ServerLevel level) {
        BlockState liquid = realm.liquid.defaultBlockState();
        for (Space s : layout.spaces()) {
            switch (s.shape()) {
                case VOID, SHAFT -> fillLiquid(level, s, s.floorY(), s.floorY() + 2, liquid);
                case POOL -> fillLiquid(level, s, s.floorY(), s.center().getY() - 1, realm.liquid == Blocks.LAVA && !realm.has(GateRealm.Decor.LAVA)
                        ? Blocks.WATER.defaultBlockState() : liquid);
                default -> {}
            }
        }
    }

    private void fillLiquid(ServerLevel level, Space s, int lo, int hi, BlockState liquid) {
        int cx = s.center().getX() + ox, cz = s.center().getZ() + oz;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(), q = new BlockPos.MutableBlockPos();
        for (int x = cx - s.rx() - 1; x <= cx + s.rx() + 1; x++) {
            for (int z = cz - s.rz() - 1; z <= cz + s.rz() + 1; z++) {
                double dx = (x - cx) / (double) s.rx(), dz = (z - cz) / (double) s.rz();
                if (dx * dx + dz * dz > 1.15) continue;
                for (int y = lo; y <= hi; y++) {
                    if (!level.getBlockState(p.set(x, y, z)).isAir()) continue;
                    if (!s.contains(x - ox, y, z - oz, 1.05) && s.shape() != SpaceShape.POOL) continue;
                    level.setBlock(p, liquid, FLAGS);
                    // never let it spill: bank up any open side
                    for (Direction d : HORIZONTAL) {
                        q.set(x + d.getStepX(), y, z + d.getStepZ());
                        if (level.getBlockState(q).isAir() && !s.contains(q.getX() - ox, y, q.getZ() - oz, 1.0)) {
                            level.setBlock(q, realm.floor(random), FLAGS);
                        }
                    }
                }
            }
        }
    }

    /** An island standing in the chasm: a platform of the realm's floor, its underside narrowing into a pillar. */
    private void island(ServerLevel level, Room room) {
        RandomSource r = new LegacyRandomSource(gate.seed() * 37 + room.index());
        int cx = room.center().getX() + ox, cz = room.center().getZ() + oz, top = room.floorY() - 1;
        int rx = room.rx();
        double pillar = Math.max(2.2, rx * 0.38);
        boolean built = realm.built();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -rx - 2; dx <= rx + 2; dx++) {
            for (int dz = -rx - 2; dz <= rx + 2; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                double edge = rx + 0.5 + (built ? 0 : (noise(cx + dx, top, cz + dz) - 0.5) * 1.8);
                if (d > edge) continue;
                put(level, p.set(cx + dx, top, cz + dz), realm.floorAt(cx + dx, cz + dz, r));
                int depth = 1 + (int) Math.round((edge - d) * (built ? 0.8 : 1.4));
                for (int k = 1; k <= depth; k++) place(level, p.set(cx + dx, top - k, cz + dz), realm.wall(r));
                if (d > pillar + (built ? 0 : (noise(cx + dx, top - 20, cz + dz) - 0.5) * 1.5)) continue;
                for (int y = top - depth - 1; y > level.getMinBuildHeight() + 1; y--) {
                    BlockState here = level.getBlockState(p.set(cx + dx, y, cz + dz));
                    if (!here.isAir() && here.getFluidState().isEmpty()) break;
                    level.setBlock(p, built && (y - top) % 6 == 0 ? realm.pillarCap : built ? realm.pillar : realm.wall(r), FLAGS);
                }
            }
        }
        // a rim: a balustrade on built islands, a few boulders on natural ones; open where bridges come in
        List<BlockPos> mouths = new ArrayList<>();
        for (Tunnel t : layout.tunnels()) {
            if (t.from() == room.index()) mouths.add(t.start());
            if (t.to() == room.index()) mouths.add(t.end());
        }
        int around = (int) Math.round(Mth.TWO_PI * rx);
        for (int i = 0; i < around; i++) {
            double a = i * Mth.TWO_PI / around;
            int x = room.center().getX() + (int) Math.round(Math.cos(a) * (rx - 0.2)), z = room.center().getZ() + (int) Math.round(Math.sin(a) * (rx - 0.2));
            boolean open = false;
            for (BlockPos m : mouths) if (Math.abs(m.getX() - x) <= 2 && Math.abs(m.getZ() - z) <= 2) open = true;
            if (open) continue;
            if (built) place(level, p.set(x + ox, top + 1, z + oz), railing());
            else if (r.nextInt(7) == 0) place(level, p.set(x + ox, top + 1, z + oz), realm.spike);
        }
    }

    /** A camp in a hollow stands on a low flat rise of its own, sloping off at the edge. */
    private void terrace(ServerLevel level, Room room) {
        Space hollow = null;
        for (Space s : layout.spaces()) {
            if (s.shape() == SpaceShape.HOLLOW && s.contains(room.center().getX(), s.floorY() + 1, room.center().getZ(), 1.25)) hollow = s;
        }
        if (hollow == null) return;
        RandomSource r = new LegacyRandomSource(gate.seed() * 41 + room.index());
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -room.rx() - 1; dx <= room.rx() + 1; dx++) {
            for (int dz = -room.rz() - 1; dz <= room.rz() + 1; dz++) {
                double d = Math.sqrt(sq(dx / (double) room.rx()) + sq(dz / (double) room.rz()));
                if (d > 1.05) continue;
                int lx = room.center().getX() + dx, lz = room.center().getZ() + dz;
                if (!hollow.contains(lx, hollow.floorY(), lz, 1.0)) continue;
                int top = room.floorY() - 1 - (d > 0.7 ? (int) Math.round((d - 0.7) * 12) : 0);
                for (int y = top; y >= top - 14; y--) {
                    BlockState here = level.getBlockState(p.set(lx + ox, y, lz + oz));
                    if (!here.isAir() && here.getFluidState().isEmpty()) break;
                    level.setBlock(p, y == top ? realm.floorAt(lx + ox, lz + oz, r) : realm.wall(r), FLAGS);
                }
            }
        }
    }

    /**
     * A room on the rim of a chasm keeps its whole floor: where the chasm ate into it, the floor runs on out over the
     * drop as a ledge a few blocks thick, so the bridges that leave the room leave from its floor.
     */
    private void ledge(ServerLevel level, Room room) {
        List<Space> voids = new ArrayList<>();
        for (Space s : layout.spaces()) if (s.shape() == SpaceShape.VOID) voids.add(s);
        if (voids.isEmpty()) return;
        RandomSource r = new LegacyRandomSource(gate.seed() * 43 + room.index());
        int top = room.floorY() - 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -room.rx() - 1; dx <= room.rx() + 1; dx++) {
            for (int dz = -room.rz() - 1; dz <= room.rz() + 1; dz++) {
                int lx = room.center().getX() + dx, lz = room.center().getZ() + dz;
                if (!room.contains(lx, room.floorY() + 0.5, lz, 1.0)) continue;
                int x = lx + ox, z = lz + oz;
                if (!level.getBlockState(p.set(x, top, z)).isAir()) continue;
                boolean eaten = false;
                for (Space s : voids) {
                    int[] span = span(s, x, z);
                    if (span != null && span[0] <= top && span[1] >= top) eaten = true;
                }
                if (!eaten) continue;
                level.setBlock(p, realm.floorAt(x, z, r), FLAGS);
                for (int k = 1; k <= 3; k++) place(level, p.set(x, top - k, z), realm.wall(r));
            }
        }
    }

    private static double sq(double v) {
        return v * v;
    }

    /** What a passage stands on: steps in a corridor, a bridge's deck and railings, the treads of winding stairs. */
    private void walkway(ServerLevel level, Tunnel t) {
        switch (t.style()) {
            case CORRIDOR -> corridorSteps(level, t);
            case BRIDGE -> bridgeDeck(level, t);
            case SPIRAL -> spiralTreads(level, t);
            default -> {}
        }
    }

    private void corridorSteps(ServerLevel level, Tunnel t) {
        List<int[]> path = corridorPath(t);
        int w = corridorHalf(t);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < path.size(); i++) {
            int[] cell = path.get(i);
            int px = -cell[4], pz = cell[3];
            // a floor under every cell (it may cross open space)
            for (int o = -w; o <= w; o++) {
                int x = cell[0] + px * o, z = cell[1] + pz * o;
                if (inRoom(x, cell[2] + 1, z, 1.0)) continue;
                place(level, p.set(x + ox, cell[2] - 1, z + oz), realm.deck);
            }
            if (i == 0) continue;
            int[] prev = path.get(i - 1);
            if (cell[2] == prev[2]) continue;
            boolean up = cell[2] > prev[2];
            int[] at = up ? cell : prev;
            int y = Math.min(cell[2], prev[2]);
            Direction travel = direction(cell[0] - prev[0], cell[1] - prev[1]);
            if (travel == null) continue;
            Direction facing = up ? travel : travel.getOpposite();
            int apx = -at[4], apz = at[3];
            for (int o = -w; o <= w; o++) {
                int x = at[0] + apx * o, z = at[1] + apz * o;
                if (inRoom(x, y + 1, z, 1.0)) continue;
                put(level, p.set(x + ox, y, z + oz), stairs(facing));
            }
        }
    }

    static Direction direction(int dx, int dz) {
        if (dx > 0) return Direction.EAST;
        if (dx < 0) return Direction.WEST;
        if (dz > 0) return Direction.SOUTH;
        if (dz < 0) return Direction.NORTH;
        return null;
    }

    private void bridgeDeck(ServerLevel level, Tunnel t) {
        double[] perp = perpendicular(t);
        int n = Math.max(8, (int) Math.ceil(t.length() * 2.5));
        Map<Long, double[]> cells = new LinkedHashMap<>();
        for (int i = 0; i <= n; i++) {
            double[] at = DungeonLayout.along(t, i / (double) n);
            for (double o = -2; o <= 2; o += 0.5) {
                int x = Mth.floor(at[0] + perp[0] * o + 0.5), z = Mth.floor(at[2] + perp[1] * o + 0.5);
                long key = BlockPos.asLong(x, 0, z);
                boolean edge = Math.abs(o) > 1.25;
                double[] had = cells.get(key);
                if (had == null || (had[1] > 0 && !edge)) cells.put(key, new double[]{at[1], edge ? 1 : 0});
            }
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (Map.Entry<Long, double[]> e : cells.entrySet()) {
            int x = BlockPos.getX(e.getKey()), z = BlockPos.getZ(e.getKey());
            double s = e.getValue()[0];
            boolean edge = e.getValue()[1] > 0;
            if (inRoom(x, s + 1, z, 1.0) && !onIsland(x, z)) continue;
            int base = Mth.floor(s);
            boolean half = s - base >= 0.5;
            if (edge) {
                int top = half ? base : base - 1;
                put(level, p.set(x + ox, top, z + oz), realm.deck);
                place(level, p.set(x + ox, top + 1, z + oz), railing());
            } else if (half) {
                put(level, p.set(x + ox, base, z + oz), slab());
                place(level, p.set(x + ox, base - 1, z + oz), realm.deck);
            } else {
                put(level, p.set(x + ox, base - 1, z + oz), realm.deck);
            }
        }
    }

    private boolean onIsland(double x, double z) {
        for (Room r : layout.rooms()) {
            if (r.shape() != Shape.ISLAND) continue;
            double dx = x - r.center().getX(), dz = z - r.center().getZ();
            if (dx * dx + dz * dz <= (r.rx() + 1) * (r.rx() + 1)) return true;
        }
        return false;
    }

    private void spiralTreads(ServerLevel level, Tunnel t) {
        int n = t.samples();
        Map<Long, Double> walk = new LinkedHashMap<>();
        for (int i = 0; i <= n; i++) {
            double u = i / (double) n;
            double[] at = DungeonLayout.along(t, u);
            for (int lx = Mth.floor(at[0]) - 3; lx <= Mth.floor(at[0]) + 4; lx++) {
                for (int lz = Mth.floor(at[2]) - 3; lz <= Mth.floor(at[2]) + 4; lz++) {
                    double s = spiralSurface(t, lx, lz, u);
                    if (!Double.isNaN(s)) walk.putIfAbsent(BlockPos.asLong(lx, Mth.floor(s), lz), s);
                }
            }
        }
        Set<Long> columns = new HashSet<>();
        for (long k : walk.keySet()) columns.add(BlockPos.asLong(BlockPos.getX(k), 0, BlockPos.getZ(k)));
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(), q = new BlockPos.MutableBlockPos();
        boolean built = realm.built();
        for (Map.Entry<Long, Double> e : walk.entrySet()) {
            int x = BlockPos.getX(e.getKey()), z = BlockPos.getZ(e.getKey());
            double s = e.getValue();
            if (inRoom(x, s + 1, z, 1.0)) continue;
            int base = Mth.floor(s);
            boolean half = s - base >= 0.5;
            if (half) {
                put(level, p.set(x + ox, base, z + oz), slab());
                place(level, p.set(x + ox, base - 1, z + oz), realm.deck);
            } else if (built || level.getBlockState(p.set(x + ox, base - 1, z + oz)).isAir()) {
                put(level, p.set(x + ox, base - 1, z + oz), realm.deck);
            }
            // a railing on any side that drops away
            for (Direction d : HORIZONTAL) {
                int nx = x + d.getStepX(), nz = z + d.getStepZ();
                if (columns.contains(BlockPos.asLong(nx, 0, nz))) continue;
                int top = half ? base : base - 1;
                q.set(nx + ox, top + 1, nz + oz);
                if (!level.getBlockState(q).isAir() || !level.getBlockState(q.below()).isAir() || !level.getBlockState(q.below(2)).isAir()) continue;
                put(level, q.below(), realm.deck);
                place(level, q, railing());
            }
        }
    }

    /** Joins every wall, fence and bar placed to whatever is beside it now. */
    private void joinUp(ServerLevel level) {
        for (BlockPos p : joins) {
            BlockState s = level.getBlockState(p);
            if (!joins(s)) continue;
            BlockState joined = Block.updateFromNeighbourShapes(s, level, p);
            if (joined != s) level.setBlock(p, joined, FLAGS);
        }
        joins.clear();
    }

    // ------------------------------------------------------------------ noise

    /** Smooth 3D value noise in 0..1 with a feature size of about 5 blocks (4 up and down), seeded by the gate. */
    float noise(int x, int y, int z) {
        return noise(x / 5.0, y / 4.0, z / 5.0);
    }

    /** Smooth 3D value noise in 0..1, one feature per unit. */
    float noise(double fx, double fy, double fz) {
        int x0 = Mth.floor(fx), y0 = Mth.floor(fy), z0 = Mth.floor(fz);
        float tx = smooth((float) (fx - x0)), ty = smooth((float) (fy - y0)), tz = smooth((float) (fz - z0));
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

    /** All rooms of a dungeon, in world positions (tests). */
    public static List<BlockPos> roomCenters(GateInstance gate) {
        List<BlockPos> out = new ArrayList<>();
        for (Room room : gate.layout().rooms()) out.add(gate.world(room.center()));
        return out;
    }
}
