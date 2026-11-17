package com.beyondsolo.gate;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * The plan of a gate's dungeon, before anything is built: its rooms, the passages between them, and the open spaces
 * some shapes need (the void under a chasm's bridges, a descent's shaft, a hollow's great cavern). Everything follows
 * from the seed, the realm and the shape ({@link DungeonPlanner}), so the same gate always has the same dungeon, and
 * the plan can be checked without a world (see the game tests).
 *
 * <p>Positions are relative to the instance's origin; the first room is the entrance.</p>
 */
public final class DungeonLayout {
    /** What a room is for. */
    public enum Kind {
        ENTRANCE, CAVERN, SIDE, BOSS,
        /** A junction, a landing or an empty cell on the way: carved and lit, but nothing waits there. */
        PASSAGE;

        /** Monsters wait in rooms of this kind. */
        public boolean hasPack() {
            return this == CAVERN || this == SIDE;
        }
    }

    /** How a room is cut ({@link DungeonBuilder}). */
    public enum Shape {
        /** A natural cavern: an uneven ellipsoid with a flat floor. */
        CAVE,
        /** A built hall: a box with straight walls, a flat floor and a vaulted ceiling. */
        HALL,
        /** A round hall: a cylinder with a domed ceiling (tower floors, arenas). */
        ROUND,
        /** A hive cell: a smooth, egg-shaped chamber. */
        CELL,
        /** A platform standing in open space (a chasm's islands): a disc of rock, and the air above it. */
        ISLAND
    }

    /**
     * A room. {@code rx, ry, rz} are its half sizes; for halls, round halls and islands the room spans
     * {@code center.y - ry .. center.y + ry} (the floor's top block is just below), for caves and cells the floor is
     * a little below the middle.
     */
    public record Room(int index, BlockPos center, int rx, int ry, int rz, Kind kind, Shape shape) {
        public Room(int index, BlockPos center, int rx, int ry, int rz, Kind kind) {
            this(index, center, rx, ry, rz, kind, Shape.CAVE);
        }

        /** The height players stand at (the floor's top block is just below). */
        public int floorY() {
            return switch (shape) {
                case CAVE, CELL -> center.getY() - Math.round(ry * 0.55f);
                default -> center.getY() - ry;
            };
        }

        /** The highest air of the room. */
        public int topY() {
            return center.getY() + ry;
        }

        /** Rough reach from the centre, for spacing and triggers. */
        public int reach() {
            return Math.max(rx, rz);
        }

        /** Rounded rooms (caves, cells): their extent is an ellipsoid. */
        public boolean natural() {
            return shape == Shape.CAVE || shape == Shape.CELL;
        }

        /**
         * True if a point (layout coordinates) is inside the room's carved shape, grown by {@code scale} (1: its
         * edge, 1.1: a little beyond it). Halls are boxes, round halls and islands cylinders, caves and cells
         * ellipsoids (their uneven edge left out).
         */
        public boolean contains(double x, double y, double z, double scale) {
            double dx = x - center.getX(), dy = y - center.getY(), dz = z - center.getZ();
            return switch (shape) {
                case CAVE, CELL -> {
                    double a = dx / (rx * scale), b = dy / (ry * scale), c = dz / (rz * scale);
                    yield a * a + b * b + c * c < 1;
                }
                case HALL -> Math.abs(dx) < rx * scale + 0.5 && Math.abs(dz) < rz * scale + 0.5 && Math.abs(dy) < ry * scale + 0.5;
                case ROUND, ISLAND -> dx * dx + dz * dz < (rx * scale + 0.5) * (rx * scale + 0.5) && Math.abs(dy) < ry * scale + 0.5;
            };
        }
    }

    /** How a passage is cut. */
    public enum Style {
        /** A winding natural tunnel: a tube along a curve. Its points are the tube's middle, 2 above the floor. */
        TUBE,
        /** A built corridor: two straight runs with a corner ({@code bend}), square, with steps where it climbs. */
        CORRIDOR,
        /** A walkway across open space with railings, arching through {@code bend}'s height at its middle. */
        BRIDGE,
        /**
         * Stairs winding round an axis (a tower's stairwell, the ramp down a shaft's wall): {@code bend} holds the
         * axis's x and z and, as its y, how far the stairs turn in thousandths of a radian (signed);
         * {@code radius} is how far from the axis they run.
         */
        SPIRAL
    }

    /**
     * A passage from one room to another. For corridors, bridges and spirals, {@code start} and {@code end} are where
     * you stand at either end (feet height); for tubes they are the tube's middle.
     */
    public record Tunnel(int from, int to, BlockPos start, BlockPos bend, BlockPos end, float radius, Style style) {
        public Tunnel(int from, int to, BlockPos start, BlockPos bend, BlockPos end, float radius) {
            this(from, to, start, bend, end, radius, Style.TUBE);
        }

        /** A spiral's turn, in radians. */
        public double turn() {
            return bend.getY() / 1000.0;
        }

        /** About how long the passage is, in blocks. */
        public double length() {
            return switch (style) {
                case TUBE, BRIDGE -> Math.sqrt(start.distSqr(end));
                case CORRIDOR -> Math.sqrt(start.distSqr(new BlockPos(bend.getX(), start.getY(), bend.getZ())))
                        + Math.sqrt(new BlockPos(bend.getX(), end.getY(), bend.getZ()).distSqr(end)) + Math.abs(end.getY() - start.getY());
                case SPIRAL -> Math.abs(turn()) * radius + Math.abs(end.getY() - start.getY());
            };
        }

        /** How finely to step along it when carving. */
        public int samples() {
            return Math.max(8, (int) (length() * (style == Style.TUBE ? 1.6 : 2.2)));
        }
    }

    /** An open space carved on its own: no monsters wait in it, rooms and passages open onto it. */
    public enum SpaceShape {
        /** A chasm: a vast, roughly rounded void with a flat bottom where its liquid lies. */
        VOID,
        /** A round vertical shaft. */
        SHAFT,
        /** A great open cavern with a floor of gentle hills and a high domed roof. */
        HOLLOW,
        /**
         * A lake in a hollow's floor: {@code center} is on its surface (the liquid's top is just below), {@code ry} is
         * how deep it gets in the middle.
         */
        POOL
    }

    public record Space(BlockPos center, int rx, int ry, int rz, SpaceShape shape) {
        /** The bottom of the space (a chasm's liquid lies on it; a hollow's floor rolls a little around it). */
        public int floorY() {
            return shape == SpaceShape.HOLLOW ? center.getY() - Math.round(ry * 0.45f) : center.getY() - ry;
        }

        public int topY() {
            return shape == SpaceShape.POOL ? center.getY() : center.getY() + ry;
        }

        /** True if a point is inside the space, grown by {@code scale} (its uneven edge left out). */
        public boolean contains(double x, double y, double z, double scale) {
            double dx = (x - center.getX()) / (rx * scale), dz = (z - center.getZ()) / (rz * scale);
            double h = dx * dx + dz * dz;
            if (h >= 1) return false;
            double grow = (scale - 1) * ry;
            return switch (shape) {
                case SHAFT -> y >= floorY() - grow && y <= topY() + grow;
                case VOID -> y >= floorY() - grow && y <= center.getY() + ry * Math.sqrt(1 - h) * scale;
                case HOLLOW -> y >= floorY() - 2 - grow && y <= center.getY() + ry * Math.sqrt(1 - h) * scale;
                case POOL -> y >= center.getY() - ry * Math.sqrt(1 - h) * scale - grow && y <= center.getY() + grow;
            };
        }
    }

    /** The height the first cave starts at, and the band caves of the original kind keep to. */
    public static final int BASE_Y = 64;
    public static final int MIN_Y = 28;
    public static final int MAX_Y = 100;
    /** Every dungeon keeps between these heights (the gate dimension's rock runs from 1 to 127). */
    public static final int FLOOR_LIMIT = 8;
    public static final int CEILING_LIMIT = 122;

    private final List<Room> rooms;
    private final List<Tunnel> tunnels;
    private final List<Space> spaces;
    private final GateRealm realm;
    private final DungeonShape shape;

    DungeonLayout(List<Room> rooms, List<Tunnel> tunnels, List<Space> spaces, GateRealm realm, DungeonShape shape) {
        this.rooms = List.copyOf(rooms);
        this.tunnels = List.copyOf(tunnels);
        this.spaces = List.copyOf(spaces);
        this.realm = realm;
        this.shape = shape;
    }

    public List<Room> rooms() {
        return rooms;
    }

    public List<Tunnel> tunnels() {
        return tunnels;
    }

    public List<Space> spaces() {
        return spaces;
    }

    public GateRealm realm() {
        return realm;
    }

    public DungeonShape shape() {
        return shape;
    }

    public Room entrance() {
        return rooms.get(0);
    }

    public Room boss() {
        for (Room r : rooms) if (r.kind == Kind.BOSS) return r;
        throw new IllegalStateException("no boss room");
    }

    /** The passage leading into the boss's room (the last one found), or null. */
    public Tunnel bossTunnel() {
        Tunnel in = null;
        for (Tunnel t : tunnels) if (t.to() >= 0 && t.to() < rooms.size() && rooms.get(t.to()).kind() == Kind.BOSS) in = t;
        return in;
    }

    /** Builds the plan for a gate: its realm and shape from the seed (the trial always has its own). */
    public static DungeonLayout plan(long seed, GateTheme theme) {
        GateRealm realm = theme == GateTheme.TRIAL ? GateRealm.THRESHOLD : GateRealm.forSeed(theme.rank, seed);
        return plan(seed, theme, realm, theme == GateTheme.TRIAL ? DungeonShape.CAVERNS : realm.shapeFor(seed));
    }

    /** Builds the plan for a gate of a realm and shape. */
    public static DungeonLayout plan(long seed, GateTheme theme, GateRealm realm, DungeonShape shape) {
        return new DungeonPlanner(seed, theme, realm, shape).plan();
    }

    /** A point along a passage, {@code u} from 0 (its start) to 1 (its end). */
    public static double[] along(Tunnel t, double u) {
        switch (t.style()) {
            case CORRIDOR -> {
                double a = Math.sqrt(sq(t.bend().getX() - t.start().getX()) + sq(t.bend().getZ() - t.start().getZ()));
                double b = Math.sqrt(sq(t.end().getX() - t.bend().getX()) + sq(t.end().getZ() - t.bend().getZ()));
                double total = Math.max(1e-3, a + b), at = u * total;
                double y = Mth.lerp(u, t.start().getY(), t.end().getY());
                if (at <= a || b < 1e-3) {
                    double k = a < 1e-3 ? 0 : at / a;
                    return new double[]{Mth.lerp(k, t.start().getX(), t.bend().getX()), y, Mth.lerp(k, t.start().getZ(), t.bend().getZ())};
                }
                double k = (at - a) / b;
                return new double[]{Mth.lerp(k, t.bend().getX(), t.end().getX()), y, Mth.lerp(k, t.bend().getZ(), t.end().getZ())};
            }
            case BRIDGE -> {
                double mid = (t.start().getY() + t.end().getY()) / 2.0;
                double arch = t.bend().getY() - mid;
                return new double[]{Mth.lerp(u, t.start().getX(), t.end().getX()),
                        Mth.lerp(u, t.start().getY(), t.end().getY()) + arch * Math.sin(Math.PI * u),
                        Mth.lerp(u, t.start().getZ(), t.end().getZ())};
            }
            case SPIRAL -> {
                double ax = t.bend().getX(), az = t.bend().getZ();
                double a0 = Math.atan2(t.start().getZ() - az, t.start().getX() - ax);
                double a = a0 + t.turn() * u;
                return new double[]{ax + Math.cos(a) * t.radius(), Mth.lerp(u, t.start().getY(), t.end().getY()), az + Math.sin(a) * t.radius()};
            }
            default -> {
                double a = (1 - u) * (1 - u), b = 2 * (1 - u) * u, c = u * u;
                return new double[]{
                        a * t.start().getX() + b * t.bend().getX() + c * t.end().getX(),
                        a * t.start().getY() + b * t.bend().getY() + c * t.end().getY(),
                        a * t.start().getZ() + b * t.bend().getZ() + c * t.end().getZ()};
            }
        }
    }

    private static double sq(double v) {
        return v * v;
    }

    /** True if a point is inside any room or space (grown by {@code scale}). */
    public boolean inside(double x, double y, double z, double scale) {
        for (Room r : rooms) if (r.contains(x, y, z, scale)) return true;
        for (Space s : spaces) if (s.contains(x, y, z, scale)) return true;
        return false;
    }

    /**
     * True if two rooms would run into each other, with {@code gap} blocks to spare. Two rounded rooms are kept
     * apart by the distance between their middles (as caves always were); otherwise rooms that share no height
     * never meet, and those that do are compared by their footprints.
     */
    public static boolean overlaps(Room a, Room b, int gap) {
        if (a.natural() && b.natural()) {
            double need = a.reach() + b.reach() + gap;
            return a.center().distSqr(b.center()) < need * need;
        }
        int aLo = a.natural() ? a.center().getY() - a.ry() : a.floorY() - 1, aHi = a.topY();
        int bLo = b.natural() ? b.center().getY() - b.ry() : b.floorY() - 1, bHi = b.topY();
        if (aLo > bHi + gap || bLo > aHi + gap) return false;
        double dx = Math.abs(a.center().getX() - b.center().getX()), dz = Math.abs(a.center().getZ() - b.center().getZ());
        boolean aBox = a.shape() == Shape.HALL, bBox = b.shape() == Shape.HALL;
        if (aBox && bBox) return dx < a.rx() + b.rx() + gap && dz < a.rz() + b.rz() + gap;
        if (!aBox && !bBox) {
            double need = a.reach() + b.reach() + gap;
            return dx * dx + dz * dz < need * need;
        }
        Room box = aBox ? a : b, round = aBox ? b : a;
        double ex = Math.max(0, dx - box.rx()), ez = Math.max(0, dz - box.rz());
        double need = round.reach() + gap;
        return ex * ex + ez * ez < need * need;
    }

    /**
     * Every room's, passage's and space's box (min x, y, z, max x, y, z), grown by {@code margin}: what was carved.
     * Rounded rooms and the rough edged spaces are cut a little beyond their sizes, and their boxes are grown to match.
     */
    public List<int[]> boxes(int margin) {
        List<int[]> out = new ArrayList<>();
        for (Room r : rooms) {
            int lo = Math.min(r.floorY() - 1, r.center().getY() - r.ry());
            int deep = r.shape() == Shape.ISLAND ? r.floorY() - 6 : lo;
            // a cave's rough edge runs up to a tenth beyond its size
            int m = margin + (r.natural() ? 1 + Math.max(r.reach(), r.ry()) / 10 : 0);
            out.add(new int[]{r.center().getX() - r.rx() - m, deep - m, r.center().getZ() - r.rz() - m,
                    r.center().getX() + r.rx() + m, r.topY() + m, r.center().getZ() + r.rz() + m});
        }
        for (Tunnel t : tunnels) {
            int pad = Mth.ceil(t.radius()) + margin + 1;
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            int n = Math.max(8, (int) t.length());
            for (int i = 0; i <= n; i++) {
                double[] p = along(t, i / (double) n);
                minX = Math.min(minX, Mth.floor(p[0]));
                minY = Math.min(minY, Mth.floor(p[1]));
                minZ = Math.min(minZ, Mth.floor(p[2]));
                maxX = Math.max(maxX, Mth.floor(p[0]));
                maxY = Math.max(maxY, Mth.floor(p[1]));
                maxZ = Math.max(maxZ, Mth.floor(p[2]));
            }
            // walkways and stairs stand on supports below their path, and have head room above it
            int below = t.style() == Style.TUBE ? pad : pad + 3;
            int above = t.style() == Style.TUBE ? pad : pad + 4;
            int side = t.style() == Style.SPIRAL ? pad + 2 : pad;
            out.add(new int[]{minX - side, minY - below, minZ - side, maxX + side, maxY + above, maxZ + side});
        }
        for (Space s : spaces) {
            // a chasm's or hollow's edge wanders a few blocks past its size, its roof up to 3 higher, a pool's water
            // is cut 2 above its surface
            int m = margin + switch (s.shape()) {
                case VOID, HOLLOW -> 4 + Math.max(s.rx(), s.rz()) / 25;
                case SHAFT -> 1 + s.rx() / 20;
                case POOL -> 2;
            };
            out.add(new int[]{s.center().getX() - s.rx() - m, s.floorY() - 4 - m, s.center().getZ() - s.rz() - m,
                    s.center().getX() + s.rx() + m, s.topY() + m, s.center().getZ() + s.rz() + m});
        }
        return out;
    }

    /** The footprint of the whole dungeon (min x, min z, max x, max z), for keeping instances apart. */
    public int[] bounds() {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] b : boxes(3)) {
            minX = Math.min(minX, b[0]);
            minZ = Math.min(minZ, b[2]);
            maxX = Math.max(maxX, b[3]);
            maxZ = Math.max(maxZ, b[5]);
        }
        return new int[]{minX, minZ, maxX, maxZ};
    }

    /** The lowest and highest block the dungeon reaches. */
    public int[] heights() {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int[] b : boxes(0)) {
            lo = Math.min(lo, b[1]);
            hi = Math.max(hi, b[4]);
        }
        return new int[]{lo, hi};
    }
}
