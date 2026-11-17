package com.beyondsolo.gate;

import com.beyondsolo.gate.DungeonLayout.Kind;
import com.beyondsolo.gate.DungeonLayout.Room;
import com.beyondsolo.gate.DungeonLayout.Shape;
import com.beyondsolo.gate.DungeonLayout.Space;
import com.beyondsolo.gate.DungeonLayout.SpaceShape;
import com.beyondsolo.gate.DungeonLayout.Style;
import com.beyondsolo.gate.DungeonLayout.Tunnel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import static com.beyondsolo.gate.DungeonLayout.BASE_Y;
import static com.beyondsolo.gate.DungeonLayout.CEILING_LIMIT;
import static com.beyondsolo.gate.DungeonLayout.FLOOR_LIMIT;
import static com.beyondsolo.gate.DungeonLayout.MAX_Y;
import static com.beyondsolo.gate.DungeonLayout.MIN_Y;

/**
 * Lays out a dungeon of one {@link DungeonShape} for a realm: where its rooms go, how they are joined and which open
 * spaces it needs. Every shape keeps the same promises (the tests hold them to it): the entrance is the first room,
 * there is one boss room and it is the biggest, every room can be reached from the entrance, no two rooms run into
 * each other, it all stays between {@link DungeonLayout#FLOOR_LIMIT} and {@link DungeonLayout#CEILING_LIMIT} and well
 * inside its slot, and the same seed always gives the same plan.
 *
 * <p>{@link DungeonShape#CAVERNS} is the original cave and takes exactly the draws it always took, so caves of the
 * original kind (and the awakening trial) come out the same as before realms existed.</p>
 */
final class DungeonPlanner {
    private static final int[] DX = {1, 0, -1, 0};
    private static final int[] DZ = {0, 1, 0, -1};

    private final long seed;
    private final GateTheme theme;
    private final GateRealm realm;
    private final DungeonShape shape;
    private final boolean trial;
    private final int tier;
    private final List<Room> rooms = new ArrayList<>();
    private final List<Tunnel> tunnels = new ArrayList<>();
    private final List<Space> spaces = new ArrayList<>();
    /** Rooms placed ahead of time (a boss's lair the way leads to): kept clear of, added at the end. */
    private final List<Room> reserved = new ArrayList<>();

    DungeonPlanner(long seed, GateTheme theme, GateRealm realm, DungeonShape shape) {
        this.seed = seed;
        this.theme = theme;
        this.realm = realm;
        this.shape = shape;
        this.trial = theme == GateTheme.TRIAL;
        this.tier = Mth.clamp(theme.rank.tier(), 0, 5);
    }

    DungeonLayout plan(boolean hidden) {
        switch (shape) {
            case LABYRINTH -> labyrinth(random(0x1AB1L));
            case GAUNTLET -> gauntlet(random(0x6A47L));
            case SPIRE -> spire(random(0x5B12L));
            case DESCENT -> descent(random(0xDE5CL));
            case CHASM -> chasm(random(0xC4A5L));
            case HOLLOW -> hollow(random(0x4011L));
            case HIVE -> hive(random(0x41E5L));
            default -> caverns();
        }
        if (hidden && !trial) placeHidden();
        return new DungeonLayout(rooms, tunnels, spaces, realm, shape);
    }

    // ------------------------------------------------------------------ the hidden chamber

    /**
     * The hidden chamber ({@code depths}): a cave off a cavern in the back two thirds of the dungeon, behind a wall that
     * passes for rock, reached by a tunnel of its own that nothing else comes near. Its own random, so the rest of the
     * dungeon is the same with or without it. Caves of the original kind place it exactly as they did before realms
     * (caves carved then are still standing); every other dungeon also keeps it out of open spaces, clear of built
     * rooms' corners and inside its slot, and may hide it off another room on the way if no cavern has room (a chasm's
     * caverns are all islands).
     */
    private void placeHidden() {
        if (rooms.size() >= 64) return;
        RandomSource h = new LegacyRandomSource(seed * 0x9E3779B97F4A7C15L + 0x48494444L);
        boolean exact = shape == DungeonShape.CAVERNS && realm.build == GateRealm.Build.CAVE && !realm.corridors;
        List<Room> caverns = new ArrayList<>();
        for (Room room : rooms) if (room.kind() == Kind.CAVERN) caverns.add(room);
        if (!caverns.isEmpty() && hideOff(h, caverns, caverns.size() / 3, exact ? 60 : 120, exact)) return;
        if (exact) return;
        List<Room> others = new ArrayList<>();
        for (Room room : rooms) if (room.kind() != Kind.BOSS && room.kind() != Kind.CAVERN && room.shape() != Shape.ISLAND) others.add(room);
        if (!others.isEmpty()) hideOff(h, others, 0, 60, false);
    }

    private boolean hideOff(RandomSource h, List<Room> from, int first, int attempts, boolean exact) {
        for (int attempt = 0; attempt < attempts; attempt++) {
            Room at = from.get(first + h.nextInt(Math.max(1, from.size() - first)));
            float a = h.nextFloat() * Mth.TWO_PI;
            int rx = 6 + h.nextInt(2), rz = 6 + h.nextInt(2), ry = 5;
            int dist = at.reach() + Math.max(rx, rz) + 8 + h.nextInt(6);
            int dy = h.nextInt(5) - 2;
            // a cave of the original kind sat by its cavern's middle; elsewhere the chamber's floor is near the room's
            int y = exact ? Mth.clamp(at.center().getY() + dy, MIN_Y + ry + 2, MAX_Y - ry - 2)
                    : Mth.clamp(at.floorY() + Math.round(ry * 0.55f) + dy, FLOOR_LIMIT + ry + 4, CEILING_LIMIT - ry - 4);
            Room room = new Room(rooms.size(), new BlockPos(at.center().getX() + Math.round(Mth.cos(a) * dist), y,
                    at.center().getZ() + Math.round(Mth.sin(a) * dist)), rx, ry, rz, Kind.HIDDEN);
            if (!centersClear(room, 7)) continue;
            if (!exact && (!clear(room, 7) || !inSlot(room, 8) || nearSpaces(room, 4))) continue;
            Tunnel t = tube(h, at, room);
            if (!secluded(at, room, t, exact)) continue;
            rooms.add(room);
            tunnels.add(t);
            return true;
        }
        return false;
    }

    /** The original check: every room's middle keeps both rooms' reach and {@code gap} away. */
    private boolean centersClear(Room candidate, int gap) {
        for (Room o : rooms) {
            double need = o.reach() + candidate.reach() + gap;
            if (o.center().distSqr(candidate.center()) < need * need) return false;
        }
        return true;
    }

    /** True if a room stays well inside the dungeon's slot of the gate dimension. */
    private static boolean inSlot(Room room, int margin) {
        int half = GateInstance.SLOT_SPACING / 2 - 16 - margin;
        return Math.abs(room.center().getX()) + room.reach() < half && Math.abs(room.center().getZ()) + room.reach() < half;
    }

    /** True if a cave room (or the rock round it, {@code margin} deep) reaches into an open space. */
    private boolean nearSpaces(Room room, int margin) {
        for (Space s : spaces) {
            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j++) {
                    for (int k = -1; k <= 1; k++) {
                        double x = room.center().getX() + i * (room.rx() + margin), y = room.center().getY() + j * (room.ry() + margin);
                        double z = room.center().getZ() + k * (room.rz() + margin);
                        if (s.contains(x, y, z, 1.05)) return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * True if nothing else reaches the hidden chamber or its tunnel: no other passage comes near either, and the tunnel
     * passes through no room but the one it leaves (nor, away from caves of the original kind, near an open space).
     */
    private boolean secluded(Room from, Room hidden, Tunnel t, boolean exact) {
        int n = exact ? 24 : 48;
        for (int i = 0; i <= n; i++) {
            double[] p = DungeonLayout.along(t, i / (double) n);
            for (Room o : rooms) {
                if (o == from) continue;
                if (exact) {
                    double dx = (p[0] - o.center().getX()) / (o.rx() + t.radius() + 3);
                    double dy = (p[1] - o.center().getY()) / (o.ry() + t.radius() + 3);
                    double dz = (p[2] - o.center().getZ()) / (o.rz() + t.radius() + 3);
                    if (dx * dx + dy * dy + dz * dz < 1) return false;
                } else if (gap(o, p) < t.radius() + 3) {
                    return false;
                }
            }
            boolean pInFrom = DungeonLayout.within(p, from, 1.35);
            // (a room on the rim of a chasm or by a shaft may itself open onto the space: the tunnel leaves it inside)
            if (!exact && !DungeonLayout.within(p, from, 1.0)) {
                for (Space s : spaces) {
                    double grow = 1 + (t.radius() + 4) / Math.max(1, Math.min(s.rx(), s.rz()));
                    if (s.contains(p[0], p[1], p[2], grow)) return false;
                }
            }
            for (Tunnel other : tunnels) {
                boolean shares = other.from() == from.index() || other.to() == from.index();
                // still inside the room it leaves, the tunnel is the room itself
                if (!exact && shares && DungeonLayout.within(p, from, 1.0)) continue;
                int m = exact ? 24 : Math.max(24, (int) (other.length() * 1.5));
                for (int j = 0; j <= m; j++) {
                    double[] q = middle(other, j / (double) m, exact);
                    // where both are still inside the room they leave, they are the room itself
                    if (shares && pInFrom && DungeonLayout.within(q, from, 1.35)) continue;
                    double need = t.radius() + halfWidth(other) + 3 + (exact || other.style() == Style.TUBE ? 0 : 1.5);
                    double ddx = p[0] - q[0], ddy = p[1] - q[1], ddz = p[2] - q[2];
                    if (ddx * ddx + ddy * ddy + ddz * ddz < need * need) return false;
                }
            }
        }
        for (Tunnel other : tunnels) {
            int m = exact ? 24 : Math.max(24, (int) (other.length() * 1.5));
            for (int j = 0; j <= m; j++) {
                double[] q = middle(other, j / (double) m, exact);
                double grow = halfWidth(other) + 4 + (exact || other.style() == Style.TUBE ? 0 : 1.5);
                double dx = (q[0] - hidden.center().getX()) / (hidden.rx() + grow);
                double dy = (q[1] - hidden.center().getY()) / (hidden.ry() + grow);
                double dz = (q[2] - hidden.center().getZ()) / (hidden.rz() + grow);
                if (dx * dx + dy * dy + dz * dz < 1) return false;
            }
        }
        return true;
    }

    /** How far a passage's opening reaches either side of its middle (a winding stair's radius is its distance from its axis). */
    private static double halfWidth(Tunnel t) {
        return switch (t.style()) {
            case SPIRAL -> 1.5;
            case BRIDGE -> 2;
            default -> t.radius();
        };
    }

    /** The middle of a passage's opening at a point: a tube's own points, two above the walkway of the others. */
    private static double[] middle(Tunnel t, double u, boolean exact) {
        double[] q = DungeonLayout.along(t, u);
        if (!exact && t.style() != Style.TUBE) q[1] += 2;
        return q;
    }

    /** About how far a point is from a room's carved shape (0 inside it). */
    private static double gap(Room o, double[] p) {
        double dx = p[0] - o.center().getX(), dy = p[1] - o.center().getY(), dz = p[2] - o.center().getZ();
        switch (o.shape()) {
            case HALL -> {
                double gx = Math.max(0, Math.abs(dx) - o.rx() - 0.5), gy = Math.max(0, Math.abs(dy) - o.ry() - 0.5), gz = Math.max(0, Math.abs(dz) - o.rz() - 0.5);
                return Math.sqrt(gx * gx + gy * gy + gz * gz);
            }
            case ROUND, ISLAND -> {
                double gh = Math.max(0, Math.sqrt(dx * dx + dz * dz) - o.rx() - 0.5), gy = Math.max(0, Math.abs(dy) - o.ry() - 0.5);
                return Math.sqrt(gh * gh + gy * gy);
            }
            default -> {
                double k = Math.sqrt(sq(dx / o.rx()) + sq(dy / o.ry()) + sq(dz / o.rz()));
                if (k <= 1) return 0;
                // the ellipsoid's own radius toward the point is (distance / k)
                return Math.sqrt(dx * dx + dy * dy + dz * dz) * (1 - 1 / k);
            }
        }
    }

    private RandomSource random(long salt) {
        return new LegacyRandomSource(seed * 0x9E3779B97F4A7C15L + salt);
    }

    /** Rooms where monsters wait, on the way to the boss (about as many as a cave of the rank has caverns). */
    private int packs() {
        return Math.max(3, theme.rooms - 1);
    }

    /** Side rooms with a cache of loot. */
    private int sides() {
        return 1 + (tier >= 3 ? 1 : 0);
    }

    /** How this realm cuts an ordinary room. */
    private Shape roomShape() {
        return switch (realm.build) {
            case BUILT -> Shape.HALL;
            case HIVE -> Shape.CELL;
            default -> Shape.CAVE;
        };
    }

    /** True if passages between rooms are square corridors (built realms, mines) rather than winding tubes. */
    private boolean corridors() {
        return realm.build == GateRealm.Build.BUILT || realm.corridors;
    }

    /** A room standing on {@code floor} (the height players stand at), indexed as the next room. */
    private Room room(int x, int floor, int z, int rx, int ry, int rz, Kind kind, Shape s) {
        int cy = switch (s) {
            case CAVE, CELL -> floor + Math.round(ry * 0.55f);
            default -> floor + ry;
        };
        return new Room(rooms.size(), new BlockPos(x, cy, z), rx, ry, rz, kind, s);
    }

    /** The same room under another index (a lair reserved ahead of time takes its real place). */
    private static Room renumber(Room r, int index) {
        return new Room(index, r.center(), r.rx(), r.ry(), r.rz(), r.kind(), r.shape());
    }

    /** True if {@code candidate} keeps {@code gap} blocks from every room placed or reserved, and inside the heights. */
    private boolean clear(Room candidate, int gap) {
        int lo = candidate.natural() ? candidate.center().getY() - candidate.ry() : candidate.floorY() - 1;
        if (lo < FLOOR_LIMIT + 3 || candidate.topY() > CEILING_LIMIT - 3) return false;
        for (Room o : rooms) if (DungeonLayout.overlaps(o, candidate, gap)) return false;
        for (Room o : reserved) if (DungeonLayout.overlaps(o, candidate, gap)) return false;
        return true;
    }

    private int[] bossSize(Shape s) {
        return switch (s) {
            case HALL -> new int[]{16, 7, 16};
            case ROUND -> new int[]{15, 7, 15};
            case CELL -> new int[]{15, 10, 15};
            default -> new int[]{17, 11, 17};
        };
    }

    // ------------------------------------------------------------------ passages

    /**
     * The passage between two rooms in the realm's way: a winding tube, or a square corridor. Either takes the same
     * draws, so a cave's plan does not depend on how its passages are cut.
     */
    private Tunnel link(RandomSource r, Room a, Room b) {
        if (!corridors()) return tube(r, a, b);
        float side = r.nextFloat();
        r.nextInt(3);
        float width = r.nextFloat();
        return corridor(a, b, side < 0.5f, width < 0.35f ? 2.5f : 1.5f);
    }

    /** The original tunnel: a tube from floor to floor, bent sideways through its middle. */
    private static Tunnel tube(RandomSource r, Room a, Room b) {
        BlockPos start = new BlockPos(a.center().getX(), a.floorY() + 2, a.center().getZ());
        BlockPos end = new BlockPos(b.center().getX(), b.floorY() + 2, b.center().getZ());
        int dx = end.getX() - start.getX();
        int dz = end.getZ() - start.getZ();
        float len = Mth.sqrt(dx * dx + dz * dz);
        float side = (r.nextFloat() - 0.5f) * 0.5f * len;
        BlockPos bend = new BlockPos(Math.round((start.getX() + end.getX()) / 2f - dz / Math.max(1f, len) * side),
                Math.round((start.getY() + end.getY()) / 2f) + r.nextInt(3) - 1,
                Math.round((start.getZ() + end.getZ()) / 2f + dx / Math.max(1f, len) * side));
        return new Tunnel(a.index(), b.index(), start, bend, end, 2.3f + r.nextFloat() * 0.9f);
    }

    /** A square corridor from door to door, turning once; it takes the other turn if the first runs through a room. */
    private Tunnel corridor(Room a, Room b, boolean xFirst, float half) {
        Tunnel first = corridorVia(a, b, xFirst, half);
        if (!throughRooms(first)) return first;
        Tunnel other = corridorVia(a, b, !xFirst, half);
        return throughRooms(other) ? first : other;
    }

    private static Tunnel corridorVia(Room a, Room b, boolean xFirst, float half) {
        double ax = a.center().getX(), az = a.center().getZ(), bx = b.center().getX(), bz = b.center().getZ();
        double kx = xFirst ? bx : ax, kz = xFirst ? az : bz;
        double la = Math.abs(kx - ax) + Math.abs(kz - az), total = la + Math.abs(bx - kx) + Math.abs(bz - kz);
        // from where the path leaves the first room's floor to where it reaches the second's
        double s0 = 0, s1 = total;
        for (double s = 0; s <= total; s += 0.5) {
            double[] p = onPath(ax, az, kx, kz, bx, bz, la, s);
            if (!a.contains(p[0], a.floorY() + 1, p[1], 1)) {
                s0 = Math.max(0, s - 1);
                break;
            }
        }
        for (double s = total; s >= 0; s -= 0.5) {
            double[] p = onPath(ax, az, kx, kz, bx, bz, la, s);
            if (!b.contains(p[0], b.floorY() + 1, p[1], 1)) {
                s1 = Math.min(total, s + 1);
                break;
            }
        }
        if (s1 < s0) s0 = s1 = (s0 + s1) / 2;
        double[] p0 = onPath(ax, az, kx, kz, bx, bz, la, s0), p1 = onPath(ax, az, kx, kz, bx, bz, la, s1);
        BlockPos start = new BlockPos((int) Math.round(p0[0]), a.floorY(), (int) Math.round(p0[1]));
        BlockPos end = new BlockPos((int) Math.round(p1[0]), b.floorY(), (int) Math.round(p1[1]));
        BlockPos bend;
        if (s0 < la && s1 > la) bend = new BlockPos((int) kx, (start.getY() + end.getY()) / 2, (int) kz);
        else bend = s1 <= la ? end : start;
        return new Tunnel(a.index(), b.index(), start, bend, end, half, Style.CORRIDOR);
    }

    private static double[] onPath(double ax, double az, double kx, double kz, double bx, double bz, double la, double s) {
        if (s <= la) {
            double k = la < 1e-6 ? 0 : s / la;
            return new double[]{ax + (kx - ax) * k, az + (kz - az) * k};
        }
        double lb = Math.abs(bx - kx) + Math.abs(bz - kz);
        double k = lb < 1e-6 ? 1 : (s - la) / lb;
        return new double[]{kx + (bx - kx) * k, kz + (bz - kz) * k};
    }

    /** True if a passage runs through a room it does not join. */
    private boolean throughRooms(Tunnel t) {
        int n = Math.max(8, (int) t.length());
        for (int i = 0; i <= n; i++) {
            double[] p = DungeonLayout.along(t, i / (double) n);
            for (Room o : rooms) {
                if (o.index() == t.from() || o.index() == t.to()) continue;
                if (o.contains(p[0], p[1] + 1, p[2], 1.25)) return true;
            }
            for (Room o : reserved) if (o.contains(p[0], p[1] + 1, p[2], 1.25)) return true;
        }
        return false;
    }

    /**
     * True if a passage runs through a room it does not join, or into another passage (away from the rooms the two
     * share, where they are the room itself).
     */
    private boolean blocked(Tunnel t) {
        if (throughRooms(t)) return true;
        int n = Math.max(8, (int) t.length());
        for (Tunnel o : tunnels) {
            int m = Math.max(8, (int) o.length());
            double need = t.radius() + o.radius() + 3;
            for (int i = 0; i <= n; i++) {
                double[] p = DungeonLayout.along(t, i / (double) n);
                for (int j = 0; j <= m; j++) {
                    double[] q = DungeonLayout.along(o, j / (double) m);
                    double dx = p[0] - q[0], dy = p[1] - q[1], dz = p[2] - q[2];
                    if (dx * dx + dy * dy + dz * dz >= need * need) continue;
                    if (inShared(t, o, p) && inShared(t, o, q)) continue;
                    return true;
                }
            }
        }
        return false;
    }

    /** True if a point lies well inside a room both passages join. */
    private boolean inShared(Tunnel a, Tunnel b, double[] p) {
        for (int index : new int[]{a.from(), a.to()}) {
            if (index != b.from() && index != b.to()) continue;
            if (index >= 0 && index < rooms.size() && rooms.get(index).contains(p[0], p[1] + 1, p[2], 1.5)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ caverns

    /** The original: a winding chain of caverns, a side cave or two, the boss at the far end. */
    private void caverns() {
        RandomSource r = new LegacyRandomSource(seed);
        Shape s = roomShape();
        boolean hall = s == Shape.HALL;
        rooms.add(new Room(0, new BlockPos(0, BASE_Y, 0), 8, hall ? 4 : 6, 8, Kind.ENTRANCE, s));
        float heading = r.nextFloat() * Mth.TWO_PI;
        int main = theme.rooms;
        for (int i = 1; i <= main; i++) {
            Room prev = rooms.get(rooms.size() - 1);
            boolean boss = i == main;
            int rx = boss ? (trial ? 23 : 17) : 8 + r.nextInt(5);
            int rz = boss ? (trial ? 23 : 17) : 8 + r.nextInt(5);
            int ry = boss ? (trial ? 15 : 11) : 6 + r.nextInt(3);
            // built halls are lower than caverns
            if (hall) ry = boss ? 7 : ry - 2;
            Room room = null;
            for (int attempt = 0; attempt < 24 && room == null; attempt++) {
                float turn = (r.nextFloat() - 0.5f) * (attempt < 12 ? 1.4f : 3.2f);
                float a = heading + turn;
                int dist = prev.reach() + Math.max(rx, rz) + 9 + r.nextInt(8);
                // the trial is a descent: every cavern lies a little deeper than the last
                int dy = trial ? -1 - r.nextInt(4) : r.nextInt(11) - 5;
                int y = Mth.clamp(prev.center().getY() + dy, MIN_Y + ry, MAX_Y - ry);
                BlockPos c = new BlockPos(prev.center().getX() + Math.round(Mth.cos(a) * dist), y, prev.center().getZ() + Math.round(Mth.sin(a) * dist));
                Room candidate = new Room(rooms.size(), c, rx, ry, rz, boss ? Kind.BOSS : Kind.CAVERN, s);
                if (clearOld(candidate, 5)) {
                    room = candidate;
                    heading = a;
                }
            }
            if (room == null) {
                // boxed in: go straight up the heading, further out
                int dist = prev.reach() + Math.max(rx, rz) + 24;
                room = new Room(rooms.size(), prev.center().offset(Math.round(Mth.cos(heading) * dist), 0, Math.round(Mth.sin(heading) * dist)),
                        rx, ry, rz, boss ? Kind.BOSS : Kind.CAVERN, s);
            }
            rooms.add(room);
            tunnels.add(link(r, prev, room));
        }

        // side caves off the middle rooms, each with a cache of loot
        int sides = 1 + (theme.rank.tier() >= 3 ? 1 : 0);
        int mainCount = rooms.size();
        for (int k = 0; k < sides; k++) {
            Room from = rooms.get(1 + r.nextInt(Math.max(1, mainCount - 2)));
            for (int attempt = 0; attempt < 16; attempt++) {
                float a = r.nextFloat() * Mth.TWO_PI;
                int dist = from.reach() + 6 + 9 + r.nextInt(5);
                int y = Mth.clamp(from.center().getY() + r.nextInt(7) - 3, MIN_Y + 5, MAX_Y - 5);
                Room side = new Room(rooms.size(), from.center().offset(Math.round(Mth.cos(a) * dist), y - from.center().getY(), Math.round(Mth.sin(a) * dist)),
                        6, hall ? 4 : 5, 6, Kind.SIDE, s);
                if (clearOld(side, 4)) {
                    rooms.add(side);
                    tunnels.add(link(r, from, side));
                    break;
                }
            }
        }
    }

    /** The original spacing test (no height limits: the original caves kept to their band by themselves). */
    private boolean clearOld(Room candidate, int gap) {
        for (Room o : rooms) if (DungeonLayout.overlaps(o, candidate, gap)) return false;
        return true;
    }

    // ------------------------------------------------------------------ labyrinth

    /** A branching maze on a grid: dead ends with caches, a few loops, the boss beyond the far edge. */
    private void labyrinth(RandomSource r) {
        int[][] grids = {{4, 3}, {4, 4}, {5, 4}, {5, 5}, {6, 5}, {6, 6}};
        int w = grids[tier][0], h = grids[tier][1], n = w * h;
        Shape s = roomShape();
        boolean natural = s != Shape.HALL;
        int step = 23;
        int fx = r.nextBoolean() ? 1 : -1, fz = r.nextBoolean() ? 1 : -1;
        boolean swap = r.nextBoolean();

        // the maze: a random walk that backs up when it is stuck, then a few walls knocked through for loops
        boolean[][] linked = new boolean[n][n];
        boolean[] seen = new boolean[n];
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(0);
        seen[0] = true;
        int[] options = new int[4];
        while (!stack.isEmpty()) {
            int c = stack.peek();
            int count = 0;
            for (int d = 0; d < 4; d++) {
                int nx = c % w + DX[d], nz = c / w + DZ[d];
                if (nx >= 0 && nz >= 0 && nx < w && nz < h && !seen[nx + nz * w]) options[count++] = nx + nz * w;
            }
            if (count == 0) {
                stack.pop();
                continue;
            }
            int o = options[r.nextInt(count)];
            linked[c][o] = linked[o][c] = true;
            seen[o] = true;
            stack.push(o);
        }
        int loops = 1 + n / 8;
        for (int tries = 0; loops > 0 && tries < 80; tries++) {
            int c = r.nextInt(n), d = r.nextInt(2);
            int nx = c % w + DX[d], nz = c / w + DZ[d];
            if (nx >= w || nz >= h) continue;
            int o = nx + nz * w;
            if (linked[c][o]) continue;
            linked[c][o] = linked[o][c] = true;
            loops--;
        }
        int[] depth = distances(linked, n);

        // the boss lies beyond the far edges, past the cell there that is deepest in
        int attach = -1;
        for (int c = 1; c < n; c++) {
            boolean edge = c % w == w - 1 || c / w == h - 1;
            if (edge && (attach < 0 || depth[c] > depth[attach])) attach = c;
        }
        boolean outX = attach % w == w - 1 && (attach / w != h - 1 || r.nextBoolean());

        Kind[] kinds = new Kind[n];
        Arrays.fill(kinds, Kind.PASSAGE);
        kinds[0] = Kind.ENTRANCE;
        // dead ends hold the caches, the deepest first
        List<Integer> ends = new ArrayList<>();
        for (int c = 1; c < n; c++) if (c != attach && degree(linked, c) == 1) ends.add(c);
        ends.sort((a, b) -> depth[a] != depth[b] ? depth[b] - depth[a] : a - b);
        int sides = sides() + (n >= 25 ? 1 : 0);
        for (int i = 0; i < Math.min(sides, ends.size()); i++) kinds[ends.get(i)] = Kind.SIDE;
        // and the caverns are spread evenly along the way in
        List<Integer> rest = new ArrayList<>();
        for (int c = 1; c < n; c++) if (kinds[c] == Kind.PASSAGE) rest.add(c);
        rest.sort((a, b) -> depth[a] != depth[b] ? depth[a] - depth[b] : a - b);
        int caverns = Math.min(rest.size(), packs());
        for (int i = 0; i < caverns; i++) kinds[rest.get((int) ((i + 0.5) * rest.size() / caverns))] = Kind.CAVERN;

        Room[] cells = new Room[n];
        for (int c = 0; c < n; c++) {
            int gx = c % w, gz = c / w;
            int u = gx * step, v = gz * step;
            int x = fx * (swap ? v : u), z = fz * (swap ? u : v);
            if (natural && c != 0) {
                x += r.nextInt(5) - 2;
                z += r.nextInt(5) - 2;
            }
            // the maze sinks a little, row by row
            int floor = BASE_Y - gz * 2 + (natural && c != 0 ? r.nextInt(3) - 1 : 0);
            int[] size = cellSize(kinds[c], s, r);
            cells[c] = room(x, floor, z, size[0], size[1], size[2], kinds[c], s);
            rooms.add(cells[c]);
        }
        for (int c = 0; c < n; c++) {
            for (int o = c + 1; o < n; o++) if (linked[c][o]) tunnels.add(link(r, cells[c], cells[o]));
        }

        Room from = cells[attach];
        int[] bs = bossSize(s);
        int gap = Math.max(from.reach(), 9) + Math.max(bs[0], bs[2]) + 8;
        int wx = outX ? (swap ? 0 : fx) : (swap ? fx : 0);
        int wz = outX ? (swap ? fz : 0) : (swap ? 0 : fz);
        Room boss = room(from.center().getX() + wx * gap, from.floorY() - 2, from.center().getZ() + wz * gap, bs[0], bs[1], bs[2], Kind.BOSS, s);
        rooms.add(boss);
        tunnels.add(link(r, from, boss));
    }

    private static int[] cellSize(Kind kind, Shape s, RandomSource r) {
        boolean hall = s == Shape.HALL;
        return switch (kind) {
            case ENTRANCE -> new int[]{7, hall ? 4 : 5, 7};
            case CAVERN -> new int[]{7 + r.nextInt(3), hall ? 4 : 5 + r.nextInt(2), 7 + r.nextInt(3)};
            case SIDE -> new int[]{6, hall ? 3 : 5, 6};
            default -> new int[]{hall ? 4 : 5, hall ? 3 : 4, hall ? 4 : 5};
        };
    }

    /** Steps from the first cell to every cell of a maze. */
    private static int[] distances(boolean[][] linked, int n) {
        int[] d = new int[n];
        Arrays.fill(d, -1);
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(0);
        d[0] = 0;
        while (!queue.isEmpty()) {
            int c = queue.poll();
            for (int o = 0; o < n; o++) {
                if (!linked[c][o] || d[o] >= 0) continue;
                d[o] = d[c] + 1;
                queue.add(o);
            }
        }
        return d;
    }

    private static int degree(boolean[][] linked, int c) {
        int k = 0;
        for (boolean b : linked[c]) if (b) k++;
        return k;
    }

    // ------------------------------------------------------------------ gauntlet

    /** Great halls one after another, each behind a gatehouse, each a fight of its own; the boss in the last. */
    private void gauntlet(RandomSource r) {
        Shape s = roomShape();
        boolean natural = s != Shape.HALL;
        Shape small = natural ? Shape.CAVE : Shape.HALL;
        int halls = packs();
        int dir = r.nextInt(4);
        // the run heads one way and drifts to one side, never back on itself, so it can't box itself in
        int ahead = dir, aside = (dir + (r.nextBoolean() ? 1 : 3)) & 3;
        rooms.add(room(0, BASE_Y, 0, 7, natural ? 5 : 4, 7, Kind.ENTRANCE, small));
        Room prev = rooms.get(0);
        List<Room> great = new ArrayList<>();
        for (int i = 0; i <= halls; i++) {
            boolean boss = i == halls;
            boolean round = !boss && !natural && r.nextInt(10) < 3;
            int along = boss ? 16 : round ? 10 + r.nextInt(3) : 11 + r.nextInt(4);
            int across = boss ? 14 : round ? along : 8 + r.nextInt(3);
            int ry = boss ? 7 : natural ? 7 : 5 + r.nextInt(2);
            int rise = r.nextInt(7) - 3;
            boolean turnFirst = r.nextInt(10) < 3;
            boolean left = r.nextBoolean();
            int[] turns = turnFirst ? new int[]{left ? 1 : 3, 0, left ? 3 : 1} : new int[]{0, left ? 1 : 3, left ? 3 : 1};
            Shape hs = boss ? small : round ? Shape.ROUND : s;
            boolean placed = false;
            for (int extra = 0; extra <= 24 && !placed; extra += 4) {
                for (int t : turns) {
                    int d2 = (dir + t) & 3;
                    if (d2 != ahead && d2 != aside) continue;
                    int gr = natural ? 4 : 3;
                    int out = extent(prev, dir) + 6 + gr + extra;
                    int gx = prev.center().getX() + DX[dir] * out, gz = prev.center().getZ() + DZ[dir] * out;
                    Room gate = room(gx, clampFloor(prev.floorY() + rise / 2), gz, gr, natural ? 4 : 3, gr, Kind.PASSAGE, small);
                    if (!clear(gate, 4)) continue;
                    rooms.add(gate);
                    int hx = DX[d2] != 0 ? along : across, hz = DX[d2] != 0 ? across : along;
                    int dist = gr + 6 + along + extra;
                    Room hall = room(gx + DX[d2] * dist, clampFloor(prev.floorY() + rise), gz + DZ[d2] * dist, hx, ry, hz,
                            boss ? Kind.BOSS : Kind.CAVERN, hs);
                    if (!clear(hall, 5)) {
                        rooms.remove(rooms.size() - 1);
                        continue;
                    }
                    rooms.add(hall);
                    tunnels.add(link(r, prev, gate));
                    tunnels.add(link(r, gate, hall));
                    if (!boss) great.add(hall);
                    prev = hall;
                    dir = d2;
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                // hemmed in: on ahead, well clear
                int out = extent(prev, ahead) + 40;
                int hx = DX[ahead] != 0 ? along : across, hz = DX[ahead] != 0 ? across : along;
                Room hall = room(prev.center().getX() + DX[ahead] * out, prev.floorY(), prev.center().getZ() + DZ[ahead] * out, hx, ry, hz,
                        boss ? Kind.BOSS : Kind.CAVERN, hs);
                rooms.add(hall);
                tunnels.add(link(r, prev, hall));
                if (!boss) great.add(hall);
                prev = hall;
            }
        }
        // a cache or two beside the halls
        for (int k = 0; k < sides() && !great.isEmpty(); k++) {
            Room from = great.get(r.nextInt(great.size()));
            int start = r.nextInt(4);
            for (int t = 0; t < 4; t++) {
                int d = (start + t) & 3;
                int dist = extent(from, d) + 6 + 6;
                Room side = room(from.center().getX() + DX[d] * dist, from.floorY(), from.center().getZ() + DZ[d] * dist, 6, natural ? 5 : 3, 6, Kind.SIDE, small);
                if (!clear(side, 4)) continue;
                rooms.add(side);
                Tunnel way = link(r, from, side);
                if (blocked(way)) {
                    rooms.remove(rooms.size() - 1);
                    continue;
                }
                tunnels.add(way);
                break;
            }
        }
    }

    private static int extent(Room room, int dir) {
        return DX[dir] != 0 ? room.rx() : room.rz();
    }

    private static int clampFloor(int floor) {
        return Mth.clamp(floor, 40, 86);
    }

    // ------------------------------------------------------------------ spire

    /**
     * A tower climbed floor by floor: round halls stacked one over another, each joined to the next by stairs winding
     * once round a turret on the tower's side, the boss on the broad top floor. Higher ranks add rooms beside some
     * floors.
     */
    private void spire(RandomSource r) {
        int floors = Mth.clamp(theme.rooms - 1, 3, 6);
        int annexes = Math.max(0, packs() - floors);
        int radius = 9 + r.nextInt(2);
        int ry = 4, rise = 2 * ry + 4, bossRy = 7;
        int height = rise * (floors + 1) + 2 * bossRy + 1;
        int base = Mth.clamp(BASE_Y - height / 2, FLOOR_LIMIT + 10, CEILING_LIMIT - 4 - height);
        float phi = r.nextFloat() * Mth.TWO_PI;
        int sense = r.nextBoolean() ? 1 : -1;
        List<Room> stack = new ArrayList<>();
        rooms.add(room(0, base, 0, radius, ry, radius, Kind.ENTRANCE, Shape.ROUND));
        stack.add(rooms.get(0));
        for (int k = 1; k <= floors + 1; k++) {
            boolean boss = k == floors + 1;
            Room below = stack.get(k - 1);
            int f = base + rise * k;
            Room floor = boss ? room(0, f, 0, radius + 4, bossRy, radius + 4, Kind.BOSS, Shape.ROUND)
                    : room(0, f, 0, radius, ry, radius, Kind.CAVERN, Shape.ROUND);
            rooms.add(floor);
            stack.add(floor);
            float a = phi + k * 2.3f + (r.nextFloat() - 0.5f) * 0.5f;
            tunnels.add(stairwell(below, floor, a, radius, sense));
        }
        // rooms beside the floors (more monsters at the higher ranks), and the caches
        List<Kind> extra = new ArrayList<>();
        for (int i = 0; i < annexes; i++) extra.add(Kind.CAVERN);
        for (int i = 0; i < sides(); i++) extra.add(Kind.SIDE);
        for (Kind kind : extra) {
            boolean done = false;
            for (int attempt = 0; attempt < 24 && !done; attempt++) {
                Room floor = stack.get(1 + r.nextInt(floors));
                int d = r.nextInt(4);
                int ar = kind == Kind.SIDE ? 6 : 7 + r.nextInt(2);
                int dist = radius + 7 + ar;
                Room annex = room(DX[d] * dist, floor.floorY(), DZ[d] * dist, ar, kind == Kind.SIDE ? 3 : 4, ar, kind, Shape.HALL);
                if (!clear(annex, 3)) continue;
                rooms.add(annex);
                Tunnel way = corridor(floor, annex, true, 1.5f);
                if (blocked(way) || nearSpirals(annex)) {
                    rooms.remove(rooms.size() - 1);
                    continue;
                }
                tunnels.add(way);
                done = true;
            }
        }
    }

    /** Stairs from one floor to the next, winding once round a turret just outside the tower's wall. */
    private static Tunnel stairwell(Room below, Room above, float angle, int towerR, int sense) {
        float rs = 4f;
        double d = towerR + 1.5 + rs;
        int ax = (int) Math.round(Math.cos(angle) * d), az = (int) Math.round(Math.sin(angle) * d);
        int sx = Math.round(ax - Mth.cos(angle) * rs), sz = Math.round(az - Mth.sin(angle) * rs);
        BlockPos start = new BlockPos(sx, below.floorY(), sz);
        BlockPos end = new BlockPos(sx, above.floorY(), sz);
        int turn = Math.round(sense * Mth.TWO_PI * 1000);
        return new Tunnel(below.index(), above.index(), start, new BlockPos(ax, turn, az), end, rs, Style.SPIRAL);
    }

    /** True if a room would cut into a winding stair. */
    private boolean nearSpirals(Room room) {
        for (Tunnel t : tunnels) {
            if (t.style() != Style.SPIRAL) continue;
            int n = Math.max(8, (int) t.length());
            for (int i = 0; i <= n; i++) {
                double[] p = DungeonLayout.along(t, i / (double) n);
                if (room.contains(p[0], p[1] + 1, p[2], 1.35)) return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ descent

    /**
     * A deep round shaft with a ramp cut into its wall, winding down from the entrance at the top; caves open off the
     * ramp on the way down, it ends on a landing over the pool at the bottom, and the boss's arena lies beyond.
     */
    private void descent(RandomSource r) {
        Shape s = roomShape();
        boolean built = s == Shape.HALL;
        Shape ledge = built ? Shape.ROUND : s;
        int shaftR = 7 + r.nextInt(3);
        float ramp = shaftR + 1.5f;
        int drop = 40 + 4 * tier;
        int pitch = 11 + r.nextInt(3);
        double total = Mth.TWO_PI * drop / (double) pitch;
        int sense = r.nextBoolean() ? 1 : -1;
        int top = 96 + r.nextInt(5);
        int bottom = top - drop - 4;
        double a0 = r.nextDouble() * Mth.TWO_PI;
        int shaftTop = top + 6;
        int span = shaftTop - bottom + ((shaftTop - bottom) & 1);
        spaces.add(new Space(new BlockPos(0, bottom + span / 2, 0), shaftR, span / 2, shaftR, SpaceShape.SHAFT));

        // the entrance on the rim, the landing at the foot of the ramp and the arena beyond it are fixed first
        int er = 7;
        double ed = shaftR + 3 + er - 2;
        rooms.add(room((int) Math.round(Math.cos(a0) * ed), top, (int) Math.round(Math.sin(a0) * ed), er, built ? 4 : 5, er, Kind.ENTRANCE, ledge));
        double endA = a0 + sense * total;
        int lr = 5;
        double ld = shaftR + 3 + lr - 2;
        int landFloor = top - drop;
        Room landing = new Room(-1, new BlockPos((int) Math.round(Math.cos(endA) * ld), landFloor + (ledge == Shape.ROUND ? 3 : Math.round(3 * 0.55f)),
                (int) Math.round(Math.sin(endA) * ld)), lr, 3, lr, Kind.PASSAGE, ledge);
        Shape arenaShape = built ? Shape.HALL : s;
        int[] bs = bossSize(arenaShape);
        double bd = ld + lr + 8 + reachToward(arenaShape, bs[0], bs[2], endA);
        Room arena = room((int) Math.round(Math.cos(endA) * bd), landFloor, (int) Math.round(Math.sin(endA) * bd), bs[0], bs[1], bs[2], Kind.BOSS, arenaShape);
        reserved.add(landing);
        reserved.add(renumber(arena, -2));

        // caves off the ramp, spread down it
        int n = packs();
        double prevPhi = 0;
        Room prev = rooms.get(0);
        List<Room> ledges = new ArrayList<>();
        for (int k = 1; k <= n; k++) {
            double want = total * k / (n + 1);
            Room cave = null;
            double at = want;
            for (int attempt = 0; attempt < 16 && cave == null; attempt++) {
                double phi = want + (attempt == 0 ? (r.nextDouble() - 0.5) * 0.6 : (attempt % 2 == 0 ? 1 : -1) * 0.3 * ((attempt + 1) / 2));
                if (phi < prevPhi + 0.8 || phi > total - 0.8) continue;
                int cr = 6 + r.nextInt(2);
                double theta = a0 + sense * phi;
                double cd = shaftR + 3 + cr - 2;
                int floor = (int) Math.round(top - drop * phi / total);
                Room c = room((int) Math.round(Math.cos(theta) * cd), floor, (int) Math.round(Math.sin(theta) * cd), cr, built ? 4 : 5, cr, Kind.CAVERN, ledge);
                if (clear(c, 3)) {
                    cave = c;
                    at = phi;
                }
            }
            if (cave == null) continue;
            rooms.add(cave);
            ledges.add(cave);
            tunnels.add(ramp(prev, cave, prevPhi, at, a0, sense, ramp, top, drop, total));
            prev = cave;
            prevPhi = at;
        }
        reserved.clear();
        landing = renumber(landing, rooms.size());
        rooms.add(landing);
        tunnels.add(ramp(prev, landing, prevPhi, total, a0, sense, ramp, top, drop, total));
        arena = renumber(arena, rooms.size());
        rooms.add(arena);
        tunnels.add(link(r, landing, arena));

        // caches deeper in behind some of the caves
        for (int k = 0; k < sides() && !ledges.isEmpty(); k++) {
            for (int attempt = 0; attempt < 16; attempt++) {
                Room from = ledges.get(r.nextInt(ledges.size()));
                double out = Math.atan2(from.center().getZ(), from.center().getX()) + (r.nextDouble() - 0.5) * 1.2;
                double dist = Math.sqrt(from.center().getX() * (double) from.center().getX() + from.center().getZ() * (double) from.center().getZ())
                        + from.reach() + 6 + 9;
                Room side = room((int) Math.round(Math.cos(out) * dist), from.floorY() + r.nextInt(3) - 1, (int) Math.round(Math.sin(out) * dist),
                        6, built ? 3 : 5, 6, Kind.SIDE, built ? Shape.HALL : s);
                if (!clear(side, 4)) continue;
                rooms.add(side);
                Tunnel way = link(r, from, side);
                if (blocked(way)) {
                    rooms.remove(rooms.size() - 1);
                    continue;
                }
                tunnels.add(way);
                break;
            }
        }
    }

    /** A stretch of the ramp round the shaft's wall, from {@code phiA} to {@code phiB} of its whole turn. */
    private static Tunnel ramp(Room from, Room to, double phiA, double phiB, double a0, int sense, float radius, int top, int drop, double total) {
        double ta = a0 + sense * phiA, tb = a0 + sense * phiB;
        BlockPos start = new BlockPos((int) Math.round(Math.cos(ta) * radius), (int) Math.round(top - drop * phiA / total), (int) Math.round(Math.sin(ta) * radius));
        BlockPos end = new BlockPos((int) Math.round(Math.cos(tb) * radius), (int) Math.round(top - drop * phiB / total), (int) Math.round(Math.sin(tb) * radius));
        // the turn is measured from where the rounded start actually lies
        double startA = Math.atan2(start.getZ(), start.getX());
        double turn = sense * (phiB - phiA) + wrap(ta - startA);
        return new Tunnel(from.index(), to.index(), start, new BlockPos(0, (int) Math.round(turn * 1000), 0), end, radius, Style.SPIRAL);
    }

    private static double wrap(double a) {
        while (a > Math.PI) a -= Mth.TWO_PI;
        while (a < -Math.PI) a += Mth.TWO_PI;
        return a;
    }

    // ------------------------------------------------------------------ chasm

    /**
     * A vast chasm with a sea of water or lava far below: islands of rock stand in it, joined by bridges, from the
     * ledge the entrance opens on to the boss's lair in the far wall. A side island or two holds a cache.
     */
    private void chasm(RandomSource r) {
        Shape s = roomShape() == Shape.CELL ? Shape.CAVE : roomShape();
        boolean built = s == Shape.HALL;
        int islands = packs();
        int voidR = 30 + 4 * islands;
        double a0 = r.nextDouble() * Mth.TWO_PI;
        double hx = Math.cos(a0), hz = Math.sin(a0);
        int vy = 70, vry = 30;
        int vcx = (int) Math.round(hx * (voidR + 6)), vcz = (int) Math.round(hz * (voidR + 6));
        spaces.add(new Space(new BlockPos(vcx, vy, vcz), voidR, vry, voidR, SpaceShape.VOID));
        int floor0 = BASE_Y;
        rooms.add(room(0, floor0, 0, 8, built ? 4 : 6, 8, Kind.ENTRANCE, s));
        int bossFloor = floor0 + r.nextInt(5) - 2;
        // the way leads across to the far side
        double gx = vcx + hx * (voidR - 3), gz = vcz + hz * (voidR - 3);

        Room prev = rooms.get(0);
        List<Room> chain = new ArrayList<>();
        for (int i = 0; i < islands; i++) {
            int left = islands - i;
            int ir = 6 + r.nextInt(3);
            Room island = null;
            for (int attempt = 0; attempt < 80 && island == null; attempt++) {
                double px = prev.center().getX(), pz = prev.center().getZ();
                double toGoal = Math.atan2(gz - pz, gx - px);
                double a = toGoal + (r.nextDouble() - 0.5) * (attempt < 40 ? 2.6 : 1.4);
                double len = prev.reach() + ir + 9 + r.nextInt(6);
                double cx = px + Math.cos(a) * len, cz = pz + Math.sin(a) * len;
                if (sq(cx - vcx) + sq(cz - vcz) > sq(voidR - ir - 5)) continue;
                double rem = Math.sqrt(sq(cx - gx) + sq(cz - gz));
                if (rem > (left - 1) * 26 + 22) continue;
                int f = left == 1 ? Mth.clamp(bossFloor + r.nextInt(5) - 2, prev.floorY() - 3, prev.floorY() + 3)
                        : Mth.clamp(prev.floorY() + r.nextInt(5) - 2, floor0 - 5, floor0 + 4);
                island = islandIfFree(r, prev, cx, f, cz, ir);
            }
            // nowhere at random: the free spot nearest the way on, or no more islands if the chasm is full
            if (island == null) island = stepToward(r, prev, ir, gx, gz, vcx, vcz, voidR);
            if (island == null) break;
            rooms.add(island);
            tunnels.add(bridge(r, prev, island));
            chain.add(island);
            prev = island;
        }

        // the boss's lair, cut into the chasm's far wall out beyond the last island, its floor's edge on the drop
        int[] bs = bossSize(s);
        double la = Math.atan2(prev.center().getZ() - vcz, prev.center().getX() - vcx);
        if (prev == rooms.get(0)) la = a0;
        la = a0 + Mth.clamp(wrap(la - a0), -1.0, 1.0);
        Room lair = null;
        Tunnel way = null;
        // swing toward the far side, and further into the wall, until it is clear of everything and the bridge to it
        // crosses nothing
        for (int push = 0; push <= 9 && lair == null; push += 3) {
            for (int k = 0; k <= 12 && lair == null; k++) {
                double a = la + (a0 - la) * k / 12.0;
                Room cand = lairAt(vcx, vcz, voidR - 3 + reachToward(s, bs[0], bs[2], a) + push, a, bossFloor, bs, s);
                if (!clear(cand, 3)) continue;
                rooms.add(cand);
                Tunnel b = bridge(r, prev, cand);
                rooms.remove(rooms.size() - 1);
                if (blocked(b)) continue;
                lair = cand;
                way = b;
            }
        }
        if (lair == null) {
            lair = lairAt(vcx, vcz, voidR + 6 + reachToward(s, bs[0], bs[2], a0), a0, bossFloor, bs, s);
            rooms.add(lair);
            way = bridge(r, prev, lair);
            rooms.remove(rooms.size() - 1);
        }
        rooms.add(lair);
        tunnels.add(way);

        // side islands with a cache
        for (int k = 0; k < sides() && !chain.isEmpty(); k++) {
            for (int attempt = 0; attempt < 40; attempt++) {
                Room from = chain.get(r.nextInt(Math.max(1, chain.size() - 1)));
                double a = r.nextDouble() * Mth.TWO_PI;
                int ir = 5 + r.nextInt(2);
                double len = from.reach() + ir + 9 + r.nextInt(5);
                double cx = from.center().getX() + Math.cos(a) * len, cz = from.center().getZ() + Math.sin(a) * len;
                if (sq(cx - vcx) + sq(cz - vcz) > sq(voidR - ir - 5)) continue;
                Room side = room((int) Math.round(cx), Mth.clamp(from.floorY() + r.nextInt(5) - 2, floor0 - 6, floor0 + 5), (int) Math.round(cz),
                        ir, 4, ir, Kind.SIDE, Shape.ISLAND);
                if (!clear(side, 7) || lineHits(from, side)) continue;
                rooms.add(side);
                Tunnel b = bridge(r, from, side);
                if (blocked(b)) {
                    rooms.remove(rooms.size() - 1);
                    continue;
                }
                tunnels.add(b);
                break;
            }
        }
    }

    private Room lairAt(int cx, int cz, double d, double a, int floor, int[] bs, Shape s) {
        return room((int) Math.round(cx + Math.cos(a) * d), floor, (int) Math.round(cz + Math.sin(a) * d), bs[0], bs[1], bs[2], Kind.BOSS, s);
    }

    /** How far a room reaches from its middle in a direction: a hall's box reaches further toward its corners. */
    private static double reachToward(Shape s, int rx, int rz, double a) {
        if (s != Shape.HALL) return Math.max(rx, rz);
        double c = Math.abs(Math.cos(a)), sn = Math.abs(Math.sin(a));
        return Math.min(c < 1e-6 ? Double.MAX_VALUE : rx / c, sn < 1e-6 ? Double.MAX_VALUE : rz / sn);
    }

    /** An island at a spot, if it keeps clear of everything and a bridge can reach it from {@code prev}; else null. */
    private Room islandIfFree(RandomSource r, Room prev, double cx, int floor, double cz, int ir) {
        Room cand = room((int) Math.round(cx), floor, (int) Math.round(cz), ir, 4, ir, Kind.CAVERN, Shape.ISLAND);
        if (!clear(cand, 7) || lineHits(prev, cand)) return null;
        rooms.add(cand);
        Tunnel b = bridge(r, prev, cand);
        rooms.remove(rooms.size() - 1);
        return blocked(b) ? null : cand;
    }

    /** The free spot for an island nearest the way toward the far side, looking all round {@code prev}; or null. */
    private Room stepToward(RandomSource r, Room prev, int ir, double gx, double gz, int vcx, int vcz, int voidR) {
        double px = prev.center().getX(), pz = prev.center().getZ();
        double toGoal = Math.atan2(gz - pz, gx - px);
        for (int k = 0; k <= 32; k++) {
            double a = toGoal + (k % 2 == 0 ? 1 : -1) * ((k + 1) / 2) * (Math.PI / 16);
            for (int extra = 0; extra <= 12; extra += 4) {
                double len = prev.reach() + ir + 9 + extra;
                double cx = px + Math.cos(a) * len, cz = pz + Math.sin(a) * len;
                if (sq(cx - vcx) + sq(cz - vcz) > sq(voidR - ir - 5)) continue;
                Room island = islandIfFree(r, prev, cx, prev.floorY(), cz, ir);
                if (island != null) return island;
            }
        }
        return null;
    }

    /** True if the straight way between two rooms passes over another. */
    private boolean lineHits(Room a, Room b) {
        double ax = a.center().getX(), az = a.center().getZ(), bx = b.center().getX(), bz = b.center().getZ();
        double dx = bx - ax, dz = bz - az, len2 = Math.max(1e-6, dx * dx + dz * dz);
        List<Room> all = new ArrayList<>(rooms);
        all.addAll(reserved);
        for (Room o : all) {
            if (o.index() == a.index() || o.index() == b.index()) continue;
            double ox = o.center().getX(), oz = o.center().getZ();
            double t = Mth.clamp(((ox - ax) * dx + (oz - az) * dz) / len2, 0, 1);
            double px = ax + dx * t - ox, pz = az + dz * t - oz;
            if (px * px + pz * pz < sq(o.reach() + 3)) return true;
        }
        return false;
    }

    /** A bridge over open space, from the edge of one room's floor to the other's, arching a little. */
    private Tunnel bridge(RandomSource r, Room a, Room b) {
        double dx = b.center().getX() - a.center().getX(), dz = b.center().getZ() - a.center().getZ();
        double len = Math.max(1e-6, Math.sqrt(dx * dx + dz * dz));
        dx /= len;
        dz /= len;
        double[] p = lip(a, dx, dz), q = lip(b, -dx, -dz);
        BlockPos start = new BlockPos(Mth.floor(p[0]), a.floorY(), Mth.floor(p[1]));
        BlockPos end = new BlockPos(Mth.floor(q[0]), b.floorY(), Mth.floor(q[1]));
        int arch = (int) Math.round(Math.sqrt(start.distSqr(end)) / 14.0) + r.nextInt(2);
        BlockPos bend = new BlockPos((start.getX() + end.getX()) / 2, Math.round((start.getY() + end.getY()) / 2f) + arch, (start.getZ() + end.getZ()) / 2);
        return new Tunnel(a.index(), b.index(), start, bend, end, 1.5f, Style.BRIDGE);
    }

    /** Where a room's floor ends going out from its middle: an island's rim, or the cliff where a ledge meets the drop. */
    private double[] lip(Room room, double dx, double dz) {
        double cx = room.center().getX(), cz = room.center().getZ();
        if (room.shape() == Shape.ISLAND) return new double[]{cx + dx * (room.rx() - 1), cz + dz * (room.rx() - 1)};
        int fy = room.floorY();
        double last = 0;
        for (double d = 0; d <= room.reach() + 24; d += 0.5) {
            double x = cx + dx * d, z = cz + dz * d;
            if (!room.contains(x, fy + 1, z, 1) || inSpace(x, fy - 1, z)) break;
            last = d;
        }
        return new double[]{cx + dx * last, cz + dz * last};
    }

    private boolean inSpace(double x, double y, double z) {
        for (Space s : spaces) if (s.contains(x, y, z, 1)) return true;
        return false;
    }

    private static double sq(double v) {
        return v * v;
    }

    // ------------------------------------------------------------------ hollow

    /**
     * One vast open cavern with a floor of low hills, a lake, pillars (dressed in by the builder) and camps of
     * monsters across it; side caves in its flanks, and the boss's lair down a tunnel beyond the far end.
     */
    private void hollow(RandomSource r) {
        Shape s = realm.build == GateRealm.Build.BUILT ? Shape.HALL : Shape.CAVE;
        int camps = packs();
        int along = 40 + 3 * tier, across = 30 + 2 * tier, hry = 24 + tier;
        int dir = r.nextInt(4);
        int ux = DX[dir], uz = DZ[dir], vx = -uz, vz = ux;
        int hx = ux * (along + 4), hz = uz * (along + 4);
        Space hollow = new Space(new BlockPos(hx, BASE_Y, hz), ux != 0 ? along : across, hry, ux != 0 ? across : along, SpaceShape.HOLLOW);
        spaces.add(hollow);
        int ground = hollow.floorY();
        rooms.add(room(0, ground + 2, 0, 8, 6, 8, Kind.ENTRANCE, s));
        int[] bs = bossSize(s);
        int lairD = along + 8 + Math.max(bs[0], bs[2]);
        Room lair = room(hx + ux * lairD, ground + 1, hz + uz * lairD, bs[0], bs[1], bs[2], Kind.BOSS, s);
        reserved.add(renumber(lair, -1));

        // camps spread from the near end to the far end
        List<Room> camp = new ArrayList<>();
        for (int i = 0; i < camps; i++) {
            double t = -0.72 + 1.44 * (i + 0.5) / camps;
            for (int attempt = 0; attempt < 40; attempt++) {
                double ta = t + (r.nextDouble() - 0.5) * (1.44 / camps);
                double tb = (r.nextDouble() - 0.5) * 1.2;
                if (ta * ta + tb * tb > 0.62) continue;
                int cr = 7 + r.nextInt(3);
                int x = hx + (int) Math.round(ux * ta * along + vx * tb * across);
                int z = hz + (int) Math.round(uz * ta * along + vz * tb * across);
                Room c = room(x, ground + 1 + r.nextInt(4), z, cr, 5, cr, Kind.CAVERN, s);
                if (!clear(c, 6)) continue;
                rooms.add(c);
                camp.add(c);
                break;
            }
        }
        // a lake somewhere clear of the camps
        for (int attempt = 0; attempt < 40; attempt++) {
            double ta = (r.nextDouble() - 0.5) * 1.1, tb = (r.nextDouble() - 0.5) * 1.1;
            if (ta * ta + tb * tb > 0.3) continue;
            int lx = hx + (int) Math.round(ux * ta * along + vx * tb * across);
            int lz = hz + (int) Math.round(uz * ta * along + vz * tb * across);
            int lrx = 7 + r.nextInt(6), lrz = 7 + r.nextInt(6);
            boolean ok = true;
            for (Room c : rooms) {
                double need = c.reach() + Math.max(lrx, lrz) + 3;
                if (sq(c.center().getX() - lx) + sq(c.center().getZ() - lz) < need * need) ok = false;
            }
            if (!ok) continue;
            spaces.add(new Space(new BlockPos(lx, ground, lz), lrx, 3 + r.nextInt(2), lrz, SpaceShape.POOL));
            break;
        }
        // the boss's lair, down a tunnel from the camp furthest in
        Room far = rooms.get(0);
        double best = Double.NEGATIVE_INFINITY;
        for (Room c : camp) {
            double k = (c.center().getX() - hx) * ux + (c.center().getZ() - hz) * uz;
            if (k > best) {
                best = k;
                far = c;
            }
        }
        reserved.clear();
        lair = renumber(lair, rooms.size());
        rooms.add(lair);
        tunnels.add(link(r, far, lair));

        // caches in the flanks
        for (int k = 0; k < sides() && !camp.isEmpty(); k++) {
            for (int attempt = 0; attempt < 20; attempt++) {
                Room from = camp.get(r.nextInt(camp.size()));
                int sgn = r.nextBoolean() ? 1 : -1;
                double off = (from.center().getX() - hx) * ux + (from.center().getZ() - hz) * uz;
                int dist = across + 12;
                int x = hx + (int) Math.round(ux * off) + vx * sgn * dist, z = hz + (int) Math.round(uz * off) + vz * sgn * dist;
                Room side = room(x, from.floorY() + r.nextInt(3), z, 6, 5, 6, Kind.SIDE, s);
                if (!clear(side, 4)) continue;
                rooms.add(side);
                Tunnel way = link(r, from, side);
                if (throughRooms(way)) {
                    rooms.remove(rooms.size() - 1);
                    continue;
                }
                tunnels.add(way);
                break;
            }
        }
    }

    // ------------------------------------------------------------------ hive

    /**
     * A cluster of round cells grown off each other in every direction (up and down a little too), joined by short
     * wide tubes; caches in some of the dead ends, and the queen's chamber grown off the deepest cell, away from the
     * rest.
     */
    private void hive(RandomSource r) {
        Shape s = Shape.CELL;
        List<Kind> grow = new ArrayList<>();
        for (int i = 0; i < packs(); i++) grow.add(Kind.CAVERN);
        for (int i = 0; i < 1 + theme.rooms / 3; i++) grow.add(Kind.PASSAGE);
        for (int i = grow.size() - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            Kind t = grow.get(i);
            grow.set(i, grow.get(j));
            grow.set(j, t);
        }
        for (int i = 0; i < sides(); i++) grow.add(Kind.SIDE);
        rooms.add(room(0, BASE_Y, 0, 7, 6, 7, Kind.ENTRANCE, s));
        int[] depth = new int[64], links = new int[64];
        for (Kind kind : grow) {
            for (int attempt = 0; attempt < 60; attempt++) {
                Room parent = pickParent(r, depth, links, kind == Kind.SIDE);
                if (parent == null) break;
                int cr = kind == Kind.CAVERN ? 7 + r.nextInt(3) : kind == Kind.SIDE ? 6 : 5;
                int cry = kind == Kind.CAVERN ? 6 + r.nextInt(2) : 5;
                double yaw = r.nextDouble() * Mth.TWO_PI, pitch = (r.nextDouble() - 0.5) * 0.7;
                double d = parent.reach() + cr + 4 + r.nextInt(5);
                int x = parent.center().getX() + (int) Math.round(Math.cos(yaw) * Math.cos(pitch) * d);
                int z = parent.center().getZ() + (int) Math.round(Math.sin(yaw) * Math.cos(pitch) * d);
                int floor = parent.floorY() + (int) Math.round(Math.sin(pitch) * d);
                if (floor < 36 || floor > 92) continue;
                Room c = room(x, floor, z, cr, cry, cr, kind, s);
                if (!clear(c, 4)) continue;
                rooms.add(c);
                Tunnel t = widen(tube(r, parent, c), 0.5f);
                if (blocked(t)) {
                    rooms.remove(rooms.size() - 1);
                    continue;
                }
                tunnels.add(t);
                depth[c.index()] = depth[parent.index()] + 1;
                links[c.index()]++;
                links[parent.index()]++;
                break;
            }
        }
        // the queen's chamber, off the deepest cell, on the far side from the rest
        double mx = 0, mz = 0;
        for (Room c : rooms) {
            mx += c.center().getX();
            mz += c.center().getZ();
        }
        mx /= rooms.size();
        mz /= rooms.size();
        List<Room> order = new ArrayList<>(rooms);
        order.sort((a, b) -> depth[a.index()] != depth[b.index()] ? depth[b.index()] - depth[a.index()] : a.index() - b.index());
        int[] bs = bossSize(s);
        for (Room from : order) {
            if (from.kind() == Kind.SIDE) continue;
            double out = Math.atan2(from.center().getZ() - mz, from.center().getX() - mx);
            for (int attempt = 0; attempt < 30; attempt++) {
                double yaw = out + (r.nextDouble() - 0.5) * (attempt < 15 ? 1.4 : 4.0);
                double d = from.reach() + bs[0] + 5 + r.nextInt(4);
                int x = from.center().getX() + (int) Math.round(Math.cos(yaw) * d);
                int z = from.center().getZ() + (int) Math.round(Math.sin(yaw) * d);
                Room boss = room(x, Mth.clamp(from.floorY() - 2 - r.nextInt(3), 40, 80), z, bs[0], bs[1], bs[2], Kind.BOSS, s);
                if (!clear(boss, 5)) continue;
                rooms.add(boss);
                Tunnel t = widen(tube(r, from, boss), 0.6f);
                if (blocked(t)) {
                    rooms.remove(rooms.size() - 1);
                    continue;
                }
                tunnels.add(t);
                return;
            }
        }
        // a hive with no room left anywhere: the chamber goes far out, down a long tube
        Room from = order.get(0);
        double out = Math.atan2(from.center().getZ() - mz, from.center().getX() - mx);
        double d = from.reach() + bs[0] + 40;
        Room boss = room(from.center().getX() + (int) Math.round(Math.cos(out) * d), Mth.clamp(from.floorY(), 40, 80),
                from.center().getZ() + (int) Math.round(Math.sin(out) * d), bs[0], bs[1], bs[2], Kind.BOSS, s);
        rooms.add(boss);
        tunnels.add(widen(tube(r, from, boss), 0.6f));
    }

    /** A cell to grow the next one off: deeper cells with few ways out are likelier (a cache's cell, never the entrance). */
    private Room pickParent(RandomSource r, int[] depth, int[] links, boolean forCache) {
        int total = 0;
        int[] weight = new int[rooms.size()];
        for (Room c : rooms) {
            int i = c.index();
            if (c.kind() == Kind.SIDE || links[i] >= (i == 0 ? 2 : 3) || (forCache && i == 0)) continue;
            weight[i] = (1 + depth[i]) * (1 + depth[i]);
            total += weight[i];
        }
        if (total == 0) return null;
        int roll = r.nextInt(total);
        for (Room c : rooms) {
            roll -= weight[c.index()];
            if (roll < 0) return c;
        }
        return null;
    }

    private static Tunnel widen(Tunnel t, float by) {
        return new Tunnel(t.from(), t.to(), t.start(), t.bend(), t.end(), t.radius() + by, t.style());
    }
}
