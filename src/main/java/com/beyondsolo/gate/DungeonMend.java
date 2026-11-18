package com.beyondsolo.gate;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.depths.DepthsBuild;
import com.beyondsolo.depths.HiddenSealBlock;
import com.beyondsolo.gate.DungeonLayout.Kind;
import com.beyondsolo.gate.DungeonLayout.Room;
import com.beyondsolo.gate.DungeonLayout.Style;
import com.beyondsolo.gate.DungeonLayout.Tunnel;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * The last step of building a dungeon: walks it from the entrance as a player would ({@link DungeonWalk}) and makes
 * sure every room can be got to on foot, and the hidden chamber too once its wall is down, but not before.
 * <p>Now and then passages meet badly and cut a room off: a tunnel leaving a cave two blocks above the cave's floor, or
 * a corridor's corner wider than the hidden wall across it. Where a room can't be got to, the cheapest way to it is
 * opened: walking where there is a way already, otherwise digging out room to pass and laying a floor, one block of
 * height at a time (never in the middle of a corridor, {@link DungeonBuilder#inLane}). Where the hidden chamber can be
 * walked into past its wall, the gap is walled up (with more of the wall where it is beside it, so it comes down with
 * the rest).</p>
 * <p>Everything here goes by the dungeon's own layout, never by where in the world it lies, so a gate's dungeon comes
 * out the same wherever it is carved.</p>
 */
final class DungeonMend {
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    /** Spots walked at most; spots looked at for one way; ways opened or gaps walled up at most, each kind, a round. */
    private static final int WALKED = 800_000, LOOKED = 400_000, TRIES = 12;
    /** What a step costs: walking, each block dug out of the way, each block of floor laid. */
    private static final int WALK = 10, DIG = 40, FLOOR = 50;
    /** Way-finding order: cheapest first, then by place, so the same way is found wherever the dungeon lies. */
    private static final Comparator<long[]> ORDER = (a, b) -> {
        int k = Long.compare(a[0], b[0]);
        if (k == 0) k = Integer.compare(BlockPos.getX(a[1]), BlockPos.getX(b[1]));
        if (k == 0) k = Integer.compare(BlockPos.getY(a[1]), BlockPos.getY(b[1]));
        if (k == 0) k = Integer.compare(BlockPos.getZ(a[1]), BlockPos.getZ(b[1]));
        return k;
    };

    private final DungeonBuilder b;
    private final DungeonLayout layout;
    private final BlockPos origin;
    /** What was carved (layout coordinates): nothing is dug or laid outside it. */
    private final List<int[]> boxes;
    @Nullable
    private final Room hidden;
    @Nullable
    private final DepthsBuild.Mouth mouth;
    /** The hidden chamber and the way into it past its wall; and around the wall itself, where nothing is ever dug. */
    private final LongOpenHashSet behind = new LongOpenHashSet(), wall = new LongOpenHashSet();
    /** What was mended, for the log. */
    final List<String> notes = new ArrayList<>();
    private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();

    DungeonMend(DungeonBuilder b) {
        this.b = b;
        this.layout = b.layout;
        this.origin = b.gate.origin();
        this.boxes = layout.boxes(0);
        Room room = layout.hidden();
        Tunnel way = layout.hiddenTunnel();
        DepthsBuild.Mouth m = room != null && way != null && b.gate.depths().hidden(b.gate) != null ? DepthsBuild.sealMouth(b.gate) : null;
        this.hidden = m != null ? room : null;
        this.mouth = m;
        if (m != null) zones(m, way, room);
    }

    /** Rooms given up on (no way found); the stage of the round under way (rooms, chamber, gaps), the round. */
    private final IntOpenHashSet lost = new IntOpenHashSet();
    private int stage, round;
    private boolean walled;
    private long spent;

    /**
     * Walks the dungeon and mends what keeps a player from where they should get (or lets them in where they
     * shouldn't), a stage a tick (each a walk through the whole dungeon, more when something is mended): the rooms,
     * then the hidden chamber with its wall down, then the gaps round its wall. A gap walled up may have been some
     * room's way, so then it all goes round again (three times at most). Returns -1 once done, as a builder step.
     */
    int step(ServerLevel level) {
        long t0 = System.nanoTime();
        BlockPos start = b.gate.entrance();
        switch (stage) {
            case 0 -> rooms(level, start);
            case 1 -> chamber(level, start);
            default -> walled = gaps(level, start);
        }
        spent += System.nanoTime() - t0;
        // without a hidden chamber, the rooms are all there is
        stage = hidden == null ? 3 : stage + 1;
        if (stage < 3) return DungeonBuilder.BUDGET;
        stage = 0;
        if (walled && ++round < 3) return DungeonBuilder.BUDGET;
        report(level, start);
        return -1;
    }

    /** Says what was mended, if anything (and if anything is still wrong). */
    private void report(ServerLevel level, BlockPos start) {
        if (notes.isEmpty()) return;
        String problem = check(level, start);
        String what = b.realm.id() + "/" + b.gate.shape().id() + " " + b.gate.rank() + " seed " + b.gate.seed();
        if (problem == null) {
            BeyondSolo.LOGGER.info("Mended the dungeon of gate {} ({}) in {} ms: {}", b.gate.id(), what, spent / 1_000_000L, String.join("; ", notes));
        } else {
            BeyondSolo.LOGGER.warn("Couldn't mend the dungeon of gate {} ({}): {} (did: {})", b.gate.id(), what, problem, String.join("; ", notes));
        }
    }

    // ------------------------------------------------------------------ rooms

    /** Opens a way to every room that can't be got to (keeping out of the hidden chamber's secret). */
    private void rooms(ServerLevel level, BlockPos start) {
        for (int i = 0; i < TRIES; i++) {
            DungeonWalk walk = new DungeonWalk(level, false).barring(behind::contains);
            LongOpenHashSet reached = walk.reach(start, WALKED);
            Room missing = missing(reached, lost);
            if (missing == null) return;
            if (!openWay(level, walk, reached, missing, false)) {
                notes.add("found no way to room " + missing.index());
                lost.add(missing.index());
            }
        }
    }

    /** The first room (not the hidden chamber, nor one given up on) with no spot reached inside it, or null. */
    @Nullable
    private Room missing(LongOpenHashSet reached, IntOpenHashSet lost) {
        List<Room> rooms = layout.rooms();
        boolean[] got = new boolean[rooms.size()];
        int left = 0;
        for (Room r : rooms) {
            if (r.kind() == Kind.HIDDEN || lost.contains(r.index())) got[r.index()] = true;
            else left++;
        }
        for (LongIterator it = reached.iterator(); it.hasNext() && left > 0; ) {
            long p = it.nextLong();
            for (Room r : rooms) {
                if (got[r.index()] || !inside(p, r, 1.0)) continue;
                got[r.index()] = true;
                left--;
            }
        }
        for (Room r : rooms) if (!got[r.index()]) return r;
        return null;
    }

    // ------------------------------------------------------------------ the hidden chamber

    /** With its wall down, opens a way into the hidden chamber if there is none. */
    private void chamber(ServerLevel level, BlockPos start) {
        for (int i = 0; i < TRIES; i++) {
            DungeonWalk walk = new DungeonWalk(level, true);
            LongOpenHashSet reached = walk.reach(start, WALKED);
            if (anyInside(reached, hidden)) return;
            if (!openWay(level, walk, reached, hidden, true)) {
                notes.add("found no way into the hidden chamber");
                return;
            }
        }
    }

    /** Walls up every way into the hidden chamber that passes its wall; true if any was. */
    private boolean gaps(ServerLevel level, BlockPos start) {
        boolean changed = false;
        for (int i = 0; i < TRIES; i++) {
            DungeonWalk walk = new DungeonWalk(level, false);
            LongArrayList way = walk.way(start, p -> inside(p, hidden, 1.0), WALKED);
            if (way == null) break;
            // where the way gets past the wall: out the far side of its slab, or into the way on behind it
            int k = 0;
            while (k < way.size() - 1 && !behind.contains(way.getLong(k))
                    && !(wall.contains(way.getLong(k)) && along(way.getLong(k)) > DepthsBuild.SEAL_HALF)) k++;
            // the gap it went through: where it crossed the wall's slab beside the wall, out of any room
            LongArrayList gap = new LongArrayList();
            for (int j = 0; j < k; j++) {
                long p = way.getLong(j);
                if (wall.contains(p) && Math.abs(along(p)) <= DepthsBuild.SEAL_HALF && !inRoom(p)) gap.add(p);
            }
            int n = gap.isEmpty() ? 0 : widenWall(level, walk, gap);
            long at = gap.isEmpty() ? way.getLong(k) : gap.getLong(0);
            if (n == 0) n = wallUp(level, way.getLong(k));
            notes.add("walled up a way into the hidden chamber at layout " + local(at) + " (" + n + " blocks)");
            changed = true;
            if (n == 0) break;
        }
        return changed;
    }

    /**
     * Widens the hidden wall over a gap beside it: the open blocks of the wall's slab that the gap is part of (out of
     * the rooms) become part of the wall, within reach of its mouth, so they come down with the rest (past that, they
     * are plain wall).
     */
    private int widenWall(ServerLevel level, DungeonWalk walk, LongArrayList gap) {
        DepthsBuild.Mouth m = mouth;
        double half = DepthsBuild.SEAL_HALF, radius = m.radius() + DepthsBuild.SEAL_PAD + 3;
        BlockPos c = BlockPos.containing(m.center());
        int reach = DepthsBuild.wallReach(m);
        net.minecraft.util.RandomSource r = b.random;
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayFIFOQueue todo = new LongArrayFIFOQueue();
        for (int i = 0; i < gap.size(); i++) {
            for (int dy = 0; dy <= 2; dy++) {
                long p = BlockPos.offset(gap.getLong(i), 0, dy, 0);
                if (seen.add(p)) todo.enqueue(p);
            }
        }
        int n = 0;
        while (!todo.isEmpty() && n < 4000) {
            long p = todo.dequeueLong();
            Vec3 off = Vec3.atCenterOf(BlockPos.of(p)).subtract(m.center());
            double a = off.dot(m.dir());
            if (Math.abs(a) > half || off.subtract(m.dir().scale(a)).length() > radius) continue;
            if (!walk.low(p) || inRoom(p) || hasBlockEntity(level, p)) continue;
            int x = BlockPos.getX(p), y = BlockPos.getY(p), z = BlockPos.getZ(p);
            boolean near = Math.abs(x - c.getX()) <= reach && Math.abs(y - c.getY()) <= reach && Math.abs(z - c.getZ()) <= reach;
            set(level, walk, p, near ? DepthsBuild.sealState(b.realm, y, r) : b.realm.wallAt(y, r));
            n++;
            for (Direction d : Direction.values()) {
                long q = BlockPos.offset(p, d);
                if (seen.add(q)) todo.enqueue(q);
            }
        }
        return n;
    }

    /** Walls up a gap into the way on behind the wall that isn't beside the wall: the spot and the head room over it. */
    private int wallUp(ServerLevel level, long gap) {
        DungeonWalk walk = new DungeonWalk(level, false);
        net.minecraft.util.RandomSource r = b.random;
        int n = 0;
        for (int dy = 0; dy <= 2; dy++) {
            long p = BlockPos.offset(gap, 0, dy, 0);
            if (!walk.low(p) || hasBlockEntity(level, p)) continue;
            set(level, walk, p, b.realm.wallAt(BlockPos.getY(p), r));
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ opening a way

    /**
     * Finds the cheapest way on foot from any spot reached to inside a room, walking where it can and otherwise
     * digging out room to pass and laying floor, and opens it. With {@code wallDown}, the way is into the hidden
     * chamber and may only get past the wall's line through the wall; without, it keeps out of there altogether.
     */
    private boolean openWay(ServerLevel level, DungeonWalk walk, LongOpenHashSet from, Room to, boolean wallDown) {
        int tx = to.center().getX() + origin.getX(), ty = to.floorY() + origin.getY(), tz = to.center().getZ() + origin.getZ();
        Long2IntOpenHashMap cost = new Long2IntOpenHashMap();
        cost.defaultReturnValue(Integer.MAX_VALUE);
        Long2LongOpenHashMap came = new Long2LongOpenHashMap();
        PriorityQueue<long[]> todo = new PriorityQueue<>(ORDER);
        for (LongIterator it = from.iterator(); it.hasNext(); ) {
            long s = it.nextLong();
            if (!wallDown && behind.contains(s)) continue;
            cost.put(s, 0);
            todo.add(new long[]{guess(s, tx, ty, tz), s});
        }
        LongOpenHashSet done = new LongOpenHashSet();
        long goal = Long.MIN_VALUE;
        while (!todo.isEmpty() && done.size() < LOOKED) {
            long c = todo.poll()[1];
            if (!done.add(c)) continue;
            if (inside(c, to, 0.9)) {
                goal = c;
                break;
            }
            int here = cost.get(c);
            for (Direction dir : HORIZONTAL) {
                walk.step(c, dir, n -> offer(cost, came, todo, c, n, here + WALK, wallDown, tx, ty, tz));
                for (int dy = -1; dy <= 1; dy++) {
                    int extra = opening(level, walk, c, dir, dy, wallDown);
                    if (extra > 0) offer(cost, came, todo, c, BlockPos.offset(BlockPos.offset(c, dir), 0, dy, 0), here + WALK + extra, wallDown, tx, ty, tz);
                }
            }
        }
        if (goal == Long.MIN_VALUE) return false;
        LongArrayList path = new LongArrayList();
        for (long p = goal; ; p = came.get(p)) {
            path.add(0, p);
            if (!came.containsKey(p)) break;
        }
        int dug = 0, laid = 0;
        LongArrayList bared = new LongArrayList();
        for (int i = 1; i < path.size(); i++) {
            long c = path.getLong(i - 1), n = path.getLong(i);
            if (walk.steps(c, n)) continue;
            int dy = BlockPos.getY(n) - BlockPos.getY(c);
            Direction dir = DungeonBuilder.direction(BlockPos.getX(n) - BlockPos.getX(c), BlockPos.getZ(n) - BlockPos.getZ(c));
            if (dir == null || dy < -1 || dy > 1) continue;
            for (long x : clearing(c, dir, dy)) {
                if (walk.open(x)) continue;
                set(level, walk, x, Blocks.CAVE_AIR.defaultBlockState());
                bared.add(x);
                dug++;
            }
            long under = under(c, dir, dy);
            if (!walk.floor(under)) {
                set(level, walk, under, floorBlock(BlockPos.getX(under), BlockPos.getZ(under)));
                laid++;
            }
        }
        // the rock laid bare is dressed like the rest
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int i = 0; i < bared.size(); i++) {
            long x = bared.getLong(i);
            for (Direction d : Direction.values()) {
                long p = BlockPos.offset(x, d);
                at.set(BlockPos.getX(p), BlockPos.getY(p), BlockPos.getZ(p));
                if (!DungeonBuilder.isRawRock(level.getBlockState(at))) continue;
                BlockState skin = b.skin(level, q, at.getX(), at.getY(), at.getZ());
                if (skin != null) level.setBlock(at, skin, DungeonBuilder.FLAGS);
            }
        }
        notes.add("opened a way " + (to.kind() == Kind.HIDDEN ? "into the hidden chamber" : "to room " + to.index())
                + " from layout " + local(path.getLong(0)) + " (" + dug + " dug, " + laid + " laid)");
        return true;
    }

    private void offer(Long2IntOpenHashMap cost, Long2LongOpenHashMap came, PriorityQueue<long[]> todo, long c, long n, int price,
                       boolean wallDown, int tx, int ty, int tz) {
        if (!passable(c, n, wallDown) || price >= cost.get(n)) return;
        cost.put(n, price);
        came.put(n, c);
        todo.add(new long[]{price + guess(n, tx, ty, tz), n});
    }

    /** A guess at what the rest of the way costs: a step for every block across, or up. */
    private static long guess(long p, int tx, int ty, int tz) {
        int across = Math.abs(BlockPos.getX(p) - tx) + Math.abs(BlockPos.getZ(p) - tz);
        return (long) WALK * Math.max(across, ty - BlockPos.getY(p));
    }

    /** Whether a way may step from {@code c} to {@code n}: within what was carved, and clear of the hidden chamber's secret. */
    private boolean passable(long c, long n, boolean wallDown) {
        if (!carved(n)) return false;
        if (hidden == null) return true;
        boolean in = behind.contains(n);
        if (!wallDown) return !in;
        // with the wall down, the way gets past the wall's line where the wall stands, and nowhere else
        return in == behind.contains(c) || wall.contains(n) || wall.contains(c);
    }

    /**
     * What it costs, besides the walk, to open a step from {@code c} towards {@code dir}, level or a block up or
     * down: each block dug out and the floor laid. 0 if it is open already, -1 if it can't be.
     */
    private int opening(ServerLevel level, DungeonWalk walk, long c, Direction dir, int dy, boolean wallDown) {
        int extra = 0;
        for (long x : clearing(c, dir, dy)) {
            if (walk.open(x)) continue;
            if (!diggable(level, walk, x, wallDown)) return -1;
            extra += DIG;
        }
        long under = under(c, dir, dy);
        if (!walk.floor(under)) {
            if (!layable(level, walk, under, wallDown)) return -1;
            extra += FLOOR;
        }
        return extra;
    }

    /**
     * What has to be open for the step: level, the feet and head; up a block, the room to jump and the feet and head
     * up there; down a block, the feet and head before the drop and the feet below.
     */
    private static long[] clearing(long c, Direction dir, int dy) {
        long h = BlockPos.offset(c, dir);
        return switch (dy) {
            case 1 -> new long[]{BlockPos.offset(c, 0, 2, 0), BlockPos.offset(h, 0, 1, 0), BlockPos.offset(h, 0, 2, 0)};
            case 0 -> new long[]{h, BlockPos.offset(h, 0, 1, 0)};
            default -> new long[]{h, BlockPos.offset(h, 0, 1, 0), BlockPos.offset(h, 0, -1, 0)};
        };
    }

    /** What the step lands on. */
    private static long under(long c, Direction dir, int dy) {
        return BlockPos.offset(BlockPos.offset(c, dir), 0, dy - 1, 0);
    }

    /** A block that may be dug out: carved round, nothing kept in it, no liquid to let in, no secret let out. */
    private boolean diggable(ServerLevel level, DungeonWalk walk, long x, boolean wallDown) {
        if (!carved(x) || wall.contains(x)) return false;
        boolean in = behind.contains(x);
        if (in && !wallDown) return false;
        if (walk.liquid(x)) return false;
        at.set(BlockPos.getX(x), BlockPos.getY(x), BlockPos.getZ(x));
        BlockState s = level.getBlockState(at);
        if (s.hasBlockEntity() || s.getBlock() instanceof HiddenSealBlock || s.getDestroySpeed(level, at) < 0) return false;
        for (Direction d : Direction.values()) {
            long q = BlockPos.offset(x, d);
            if (walk.liquid(q)) return false;
            // no opening between the way on behind the wall and anywhere else
            if (hidden != null && !wall.contains(q) && behind.contains(q) != in && walk.low(q)) return false;
        }
        return true;
    }

    /** Somewhere a floor block may be laid: open air within what was carved (anywhere, with the wall down). */
    private boolean layable(ServerLevel level, DungeonWalk walk, long f, boolean wallDown) {
        if (!carved(f)) return false;
        if (!wallDown && (behind.contains(f) || wall.contains(f))) return false;
        // never in the middle of a corridor, where it would cap or trip the way along it
        if (b.inLane(BlockPos.getX(f) - origin.getX(), BlockPos.getY(f), BlockPos.getZ(f) - origin.getZ(), -1)) return false;
        return walk.open(f) && !hasBlockEntity(level, f);
    }

    private BlockState floorBlock(int x, int z) {
        BlockState s = b.realm.floorAt(x, z, b.random);
        // nothing that falls: it may be laid over a drop
        if (s.getBlock() instanceof FallingBlock) s = b.realm.mainWall();
        if (s.getBlock() instanceof FallingBlock) s = Blocks.STONE.defaultBlockState();
        return s;
    }

    // ------------------------------------------------------------------ where

    /**
     * Works out where the hidden chamber's secret lies: around the wall (its slab, grown), and past it (the way on,
     * a few blocks round its line, and the chamber).
     */
    private void zones(DepthsBuild.Mouth m, Tunnel t, Room room) {
        Vec3 c = m.center(), dir = m.dir();
        double half = DepthsBuild.SEAL_HALF;
        double radius = m.radius() + DepthsBuild.SEAL_PAD;
        BlockPos mc = BlockPos.containing(c);
        int reach = Mth.ceil(radius + 4);
        for (BlockPos p : BlockPos.betweenClosed(mc.offset(-reach, -reach, -reach), mc.offset(reach, reach, reach))) {
            Vec3 off = Vec3.atCenterOf(p).subtract(c);
            double a = off.dot(dir);
            if (Math.abs(a) <= half + 1.5 && off.subtract(dir.scale(a)).length() <= radius + 3) wall.add(p.asLong());
        }
        double pad = t.radius() + 2;
        int r = Mth.ceil(pad);
        int n = Math.max(16, (int) Math.ceil(t.length() * 2));
        for (int i = 0; i <= n; i++) {
            double[] a = DungeonLayout.along(t, i / (double) n);
            // a tube's points are its middle, the others' where you walk, two below theirs
            Vec3 mid = new Vec3(a[0] + origin.getX(), a[1] + origin.getY() + (t.style() == Style.TUBE ? 0 : 2), a[2] + origin.getZ());
            if (mid.subtract(c).dot(dir) <= half) continue;
            BlockPos mp = BlockPos.containing(mid);
            for (BlockPos p : BlockPos.betweenClosed(mp.offset(-r, -r, -r), mp.offset(r, r, r))) {
                Vec3 v = Vec3.atCenterOf(p);
                if (v.distanceToSqr(mid) <= pad * pad && v.subtract(c).dot(dir) > half) behind.add(p.asLong());
            }
        }
        int gx = Mth.ceil(room.rx() * 1.25) + 2, gy = Mth.ceil(room.ry() * 1.25) + 2, gz = Mth.ceil(room.rz() * 1.25) + 2;
        BlockPos rc = room.center().offset(origin);
        for (BlockPos p : BlockPos.betweenClosed(rc.offset(-gx, -gy, -gz), rc.offset(gx, gy, gz))) {
            if (inside(p.asLong(), room, 1.25)) behind.add(p.asLong());
        }
    }

    /** How far past the wall's middle a spot lies, towards the hidden chamber. */
    private double along(long p) {
        return Vec3.atCenterOf(BlockPos.of(p)).subtract(mouth.center()).dot(mouth.dir());
    }

    /** True if a spot (feet) is inside a room grown by {@code scale}, as the game tests judge it. */
    private boolean inside(long p, Room room, double scale) {
        return room.contains(BlockPos.getX(p) - origin.getX() + 0.5, BlockPos.getY(p) - origin.getY(), BlockPos.getZ(p) - origin.getZ() + 0.5, scale);
    }

    /** True if a spot is inside any room but the hidden chamber. */
    private boolean inRoom(long p) {
        for (Room r : layout.rooms()) if (r.kind() != Kind.HIDDEN && inside(p, r, 1.0)) return true;
        return false;
    }

    private boolean anyInside(LongOpenHashSet reached, Room room) {
        for (LongIterator it = reached.iterator(); it.hasNext(); ) if (inside(it.nextLong(), room, 1.0)) return true;
        return false;
    }

    /** True if a block is within what was carved. */
    private boolean carved(long p) {
        int x = BlockPos.getX(p) - origin.getX(), y = BlockPos.getY(p) - origin.getY(), z = BlockPos.getZ(p) - origin.getZ();
        for (int[] box : boxes) {
            if (x >= box[0] && x <= box[3] && y >= box[1] && y <= box[4] && z >= box[2] && z <= box[5]) return true;
        }
        return false;
    }

    private String local(long p) {
        return (BlockPos.getX(p) - origin.getX()) + ", " + (BlockPos.getY(p) - origin.getY()) + ", " + (BlockPos.getZ(p) - origin.getZ());
    }

    private boolean hasBlockEntity(ServerLevel level, long p) {
        at.set(BlockPos.getX(p), BlockPos.getY(p), BlockPos.getZ(p));
        return level.getBlockState(at).hasBlockEntity();
    }

    private void set(ServerLevel level, DungeonWalk walk, long p, BlockState s) {
        at.set(BlockPos.getX(p), BlockPos.getY(p), BlockPos.getZ(p));
        level.setBlock(at, s, DungeonBuilder.FLAGS);
        walk.forget(p);
    }

    /** What is still wrong once mended, or null. */
    @Nullable
    private String check(ServerLevel level, BlockPos start) {
        LongOpenHashSet reached = new DungeonWalk(level, false).reach(start, WALKED);
        Room missing = missing(reached, new IntOpenHashSet());
        if (missing != null) return "room " + missing.index() + " can't be got to";
        if (hidden == null) return null;
        if (anyInside(reached, hidden)) return "the hidden chamber can be walked into past its wall";
        if (!anyInside(new DungeonWalk(level, true).reach(start, WALKED), hidden)) return "the hidden chamber can't be got into with its wall down";
        return null;
    }
}
