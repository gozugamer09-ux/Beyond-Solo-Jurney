package com.beyondsolo.gate;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * The plan of a gate's cave, before anything is built: a winding chain of caverns joined by tunnels, a side cave
 * or two with a cache of loot, and the boss's arena at the far end. Everything follows from the seed, so the same
 * gate always has the same cave, and the plan can be checked without a world (see the game tests).
 *
 * <p>Positions are relative to the instance's origin; the first room is the entrance and sits on (0, 0, 0)'s column.</p>
 */
public final class DungeonLayout {
    /** A cavern: an uneven ellipsoid with a flat floor at {@link #floorY()}. */
    public record Room(int index, BlockPos center, int rx, int ry, int rz, Kind kind) {
        public int floorY() {
            return center.getY() - Math.round(ry * 0.55f);
        }

        /** Rough reach from the centre, for spacing and triggers. */
        public int reach() {
            return Math.max(rx, rz);
        }
    }

    /** HIDDEN: a chamber behind a disguised wall off one of the caverns ({@code depths.HiddenRooms}). */
    public enum Kind { ENTRANCE, CAVERN, SIDE, BOSS, HIDDEN }

    /** A tunnel from one room to another, bending through {@code bend}. */
    public record Tunnel(int from, int to, BlockPos start, BlockPos bend, BlockPos end, float radius) {}

    public static final int BASE_Y = 64;
    public static final int MIN_Y = 28;
    public static final int MAX_Y = 100;

    private final List<Room> rooms;
    private final List<Tunnel> tunnels;

    private DungeonLayout(List<Room> rooms, List<Tunnel> tunnels) {
        this.rooms = rooms;
        this.tunnels = tunnels;
    }

    public List<Room> rooms() {
        return rooms;
    }

    public List<Tunnel> tunnels() {
        return tunnels;
    }

    public Room entrance() {
        return rooms.get(0);
    }

    public Room boss() {
        for (Room r : rooms) if (r.kind == Kind.BOSS) return r;
        throw new IllegalStateException("no boss room");
    }

    /** The hidden chamber, if the cave has one. */
    @org.jetbrains.annotations.Nullable
    public Room hidden() {
        for (Room r : rooms) if (r.kind == Kind.HIDDEN) return r;
        return null;
    }

    /** The tunnel into the hidden chamber (from the cavern it hides off), if there is one. */
    @org.jetbrains.annotations.Nullable
    public Tunnel hiddenTunnel() {
        Room h = hidden();
        if (h == null) return null;
        for (Tunnel t : tunnels) if (t.to() == h.index()) return t;
        return null;
    }

    /** The tunnels that open into a room (either end). */
    public List<Tunnel> tunnelsOf(int room) {
        List<Tunnel> out = new ArrayList<>();
        for (Tunnel t : tunnels) if (t.from() == room || t.to() == room) out.add(t);
        return out;
    }

    /** Builds the plan for a gate without a hidden chamber. */
    public static DungeonLayout plan(long seed, GateTheme theme) {
        return plan(seed, theme, false);
    }

    /**
     * Builds the plan for a gate. With {@code hidden}, a chamber is tucked off one of the later caverns, where
     * nothing else passes (its own random, so the rest of the cave is the same with or without it).
     */
    public static DungeonLayout plan(long seed, GateTheme theme, boolean hidden) {
        return plan(seed, theme, theme.rooms, sides(theme), hidden, false);
    }

    /**
     * The plan of a gate that hides the awakening's sealed door ({@code depths.HiddenKind.SEALED}): its chamber lies
     * off the boss's hall, deeper than anything else in the cave. The rest of the cave is the same as without it.
     */
    public static DungeonLayout planSealed(long seed, GateTheme theme) {
        return plan(seed, theme, theme.rooms, sides(theme), true, true);
    }

    /** Caverns between an awakening temple's way in and the Arbiter's hall. */
    public static final int TEMPLE_ROOMS = 1;

    /**
     * The plan of an awakening temple (a trial opened since 0.1.1, see {@code GateInstance#temple}): a short way down
     * from the door to an antechamber and on to the Arbiter's hall. No side caves: nothing in there but the Arbiter.
     */
    public static DungeonLayout temple(long seed) {
        return plan(seed, GateTheme.TRIAL, TEMPLE_ROOMS + 1, 0, false, false);
    }

    private static int sides(GateTheme theme) {
        return 1 + (theme.rank.tier() >= 3 ? 1 : 0);
    }

    private static DungeonLayout plan(long seed, GateTheme theme, int main, int sides, boolean hidden, boolean offBoss) {
        RandomSource r = new LegacyRandomSource(seed);
        List<Room> rooms = new ArrayList<>();
        List<Tunnel> tunnels = new ArrayList<>();

        rooms.add(new Room(0, new BlockPos(0, BASE_Y, 0), 8, 6, 8, Kind.ENTRANCE));
        float heading = r.nextFloat() * Mth.TWO_PI;
        for (int i = 1; i <= main; i++) {
            Room prev = rooms.get(rooms.size() - 1);
            boolean boss = i == main;
            boolean trial = theme == GateTheme.TRIAL;
            int rx = boss ? (trial ? 23 : 17) : 8 + r.nextInt(5);
            int rz = boss ? (trial ? 23 : 17) : 8 + r.nextInt(5);
            int ry = boss ? (trial ? 15 : 11) : 6 + r.nextInt(3);
            Room room = null;
            for (int attempt = 0; attempt < 24 && room == null; attempt++) {
                float turn = (r.nextFloat() - 0.5f) * (attempt < 12 ? 1.4f : 3.2f);
                float a = heading + turn;
                int dist = prev.reach() + Math.max(rx, rz) + 9 + r.nextInt(8);
                // the trial is a descent: every cavern lies a little deeper than the last
                int dy = trial ? -1 - r.nextInt(4) : r.nextInt(11) - 5;
                int y = Mth.clamp(prev.center().getY() + dy, MIN_Y + ry, MAX_Y - ry);
                BlockPos c = new BlockPos(prev.center().getX() + Math.round(Mth.cos(a) * dist), y, prev.center().getZ() + Math.round(Mth.sin(a) * dist));
                Room candidate = new Room(rooms.size(), c, rx, ry, rz, boss ? Kind.BOSS : Kind.CAVERN);
                if (clear(rooms, candidate, 5)) {
                    room = candidate;
                    heading = a;
                }
            }
            if (room == null) {
                // boxed in: go straight up the heading, further out
                int dist = prev.reach() + Math.max(rx, rz) + 24;
                room = new Room(rooms.size(), prev.center().offset(Math.round(Mth.cos(heading) * dist), 0, Math.round(Mth.sin(heading) * dist)),
                        rx, ry, rz, boss ? Kind.BOSS : Kind.CAVERN);
            }
            rooms.add(room);
            tunnels.add(tunnel(r, prev, room));
        }

        // side caves off the middle rooms, each with a cache of loot
        int mainCount = rooms.size();
        for (int s = 0; s < sides; s++) {
            Room from = rooms.get(1 + r.nextInt(Math.max(1, mainCount - 2)));
            for (int attempt = 0; attempt < 16; attempt++) {
                float a = r.nextFloat() * Mth.TWO_PI;
                int dist = from.reach() + 6 + 9 + r.nextInt(5);
                int y = Mth.clamp(from.center().getY() + r.nextInt(7) - 3, MIN_Y + 5, MAX_Y - 5);
                Room side = new Room(rooms.size(), from.center().offset(Math.round(Mth.cos(a) * dist), y - from.center().getY(), Math.round(Mth.sin(a) * dist)),
                        6, 5, 6, Kind.SIDE);
                if (clear(rooms, side, 4)) {
                    rooms.add(side);
                    tunnels.add(tunnel(r, from, side));
                    break;
                }
            }
        }
        if (hidden) placeHidden(seed, rooms, tunnels, offBoss);
        return new DungeonLayout(List.copyOf(rooms), List.copyOf(tunnels));
    }

    /**
     * The hidden chamber: off a cavern in the back two thirds of the cave (or, {@code offBoss}, off the boss's hall),
     * clear of every other room and tunnel.
     */
    private static void placeHidden(long seed, List<Room> rooms, List<Tunnel> tunnels, boolean offBoss) {
        RandomSource h = new LegacyRandomSource(seed * 0x9E3779B97F4A7C15L + 0x48494444L);
        if (offBoss) {
            placeOffBoss(h, rooms, tunnels);
            return;
        }
        List<Room> caverns = new ArrayList<>();
        for (Room room : rooms) if (room.kind == Kind.CAVERN) caverns.add(room);
        if (caverns.isEmpty()) return;
        int first = caverns.size() / 3;
        for (int attempt = 0; attempt < 60; attempt++) {
            Room from = caverns.get(first + h.nextInt(Math.max(1, caverns.size() - first)));
            float a = h.nextFloat() * Mth.TWO_PI;
            int rx = 6 + h.nextInt(2), rz = 6 + h.nextInt(2), ry = 5;
            int dist = from.reach() + Math.max(rx, rz) + 8 + h.nextInt(6);
            int y = Mth.clamp(from.center().getY() + h.nextInt(5) - 2, MIN_Y + ry + 2, MAX_Y - ry - 2);
            Room room = new Room(rooms.size(), new BlockPos(from.center().getX() + Math.round(Mth.cos(a) * dist), y,
                    from.center().getZ() + Math.round(Mth.sin(a) * dist)), rx, ry, rz, Kind.HIDDEN);
            if (!clear(rooms, room, 7)) continue;
            Tunnel t = tunnel(h, from, room);
            if (!secluded(rooms, tunnels, from, room, t)) continue;
            rooms.add(room);
            tunnels.add(t);
            return;
        }
    }

    /** Size of the sealed door's chamber: wider and taller than the others, a sealed gate stands in it. */
    public static final int SEALED_RX = 8, SEALED_RY = 6;

    /**
     * The sealed door's chamber: past the boss's hall, on the far side from the way in (wider round it if that fails),
     * at the end of a short passage nothing else comes near, its floor about level with the hall's.
     */
    private static void placeOffBoss(RandomSource h, List<Room> rooms, List<Tunnel> tunnels) {
        Room boss = null;
        for (Room room : rooms) if (room.kind == Kind.BOSS) boss = room;
        if (boss == null) return;
        Tunnel in = null;
        for (Tunnel t : tunnels) if (t.to() == boss.index()) in = t;
        float back = in == null ? h.nextFloat() * Mth.TWO_PI
                : (float) Math.atan2(boss.center().getZ() - in.start().getZ(), boss.center().getX() - in.start().getX());
        int r = SEALED_RX, ry = SEALED_RY;
        for (int attempt = 0; attempt < 80; attempt++) {
            float spread = Math.min(Mth.PI, 0.45f + attempt * 0.05f);
            float a = back + (h.nextFloat() - 0.5f) * 2 * spread;
            int dist = boss.reach() + r + 8 + h.nextInt(5);
            int y = Mth.clamp(boss.floorY() + Math.round(ry * 0.55f) + h.nextInt(3) - 1, MIN_Y + ry + 2, MAX_Y - ry - 2);
            Room room = new Room(rooms.size(), new BlockPos(boss.center().getX() + Math.round(Mth.cos(a) * dist), y,
                    boss.center().getZ() + Math.round(Mth.sin(a) * dist)), r, ry, r, Kind.HIDDEN);
            if (!clear(rooms, room, 7)) continue;
            Tunnel t = tunnel(h, boss, room);
            if (!secluded(rooms, tunnels, boss, room, t)) continue;
            rooms.add(room);
            tunnels.add(t);
            return;
        }
    }

    /**
     * True if nothing else reaches the hidden chamber or its tunnel: no other tunnel comes near either, and the tunnel
     * passes through no room but the one it leaves.
     */
    private static boolean secluded(List<Room> rooms, List<Tunnel> tunnels, Room from, Room hidden, Tunnel t) {
        for (int i = 0; i <= 24; i++) {
            double[] p = along(t, i / 24.0);
            for (Room o : rooms) {
                if (o == from) continue;
                double dx = (p[0] - o.center().getX()) / (o.rx() + t.radius() + 3);
                double dy = (p[1] - o.center().getY()) / (o.ry() + t.radius() + 3);
                double dz = (p[2] - o.center().getZ()) / (o.rz() + t.radius() + 3);
                if (dx * dx + dy * dy + dz * dz < 1) return false;
            }
            boolean pInFrom = within(p, from, 1.35);
            for (Tunnel other : tunnels) {
                boolean shares = other.from() == from.index() || other.to() == from.index();
                for (int j = 0; j <= 24; j++) {
                    double[] q = along(other, j / 24.0);
                    // where both are still inside the room they leave, they are the room itself
                    if (shares && pInFrom && within(q, from, 1.35)) continue;
                    double need = t.radius() + other.radius() + 3;
                    double ddx = p[0] - q[0], ddy = p[1] - q[1], ddz = p[2] - q[2];
                    if (ddx * ddx + ddy * ddy + ddz * ddz < need * need) return false;
                }
            }
        }
        for (Tunnel other : tunnels) {
            for (int j = 0; j <= 24; j++) {
                double[] q = along(other, j / 24.0);
                double dx = (q[0] - hidden.center().getX()) / (hidden.rx() + other.radius() + 4);
                double dy = (q[1] - hidden.center().getY()) / (hidden.ry() + other.radius() + 4);
                double dz = (q[2] - hidden.center().getZ()) / (hidden.rz() + other.radius() + 4);
                if (dx * dx + dy * dy + dz * dz < 1) return false;
            }
        }
        return true;
    }

    /** True if {@code p} is inside the room's ellipsoid scaled by {@code scale}. */
    public static boolean within(double[] p, Room room, double scale) {
        double dx = (p[0] - room.center().getX()) / room.rx();
        double dy = (p[1] - room.center().getY()) / room.ry();
        double dz = (p[2] - room.center().getZ()) / room.rz();
        return dx * dx + dy * dy + dz * dz < scale * scale;
    }

    private static boolean clear(List<Room> rooms, Room candidate, int gap) {
        for (Room o : rooms) {
            double need = o.reach() + candidate.reach() + gap;
            if (o.center().distSqr(candidate.center()) < need * need) return false;
        }
        return true;
    }

    private static Tunnel tunnel(RandomSource r, Room a, Room b) {
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

    /** A point along a tunnel's curve, {@code t} from 0 to 1. */
    public static double[] along(Tunnel t, double u) {
        double a = (1 - u) * (1 - u), b = 2 * (1 - u) * u, c = u * u;
        return new double[]{
                a * t.start().getX() + b * t.bend().getX() + c * t.end().getX(),
                a * t.start().getY() + b * t.bend().getY() + c * t.end().getY(),
                a * t.start().getZ() + b * t.bend().getZ() + c * t.end().getZ()};
    }

    /** The footprint of the whole cave (min x, min z, max x, max z), for keeping instances apart. */
    public int[] bounds() {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Room room : rooms) {
            minX = Math.min(minX, room.center().getX() - room.rx() - 3);
            maxX = Math.max(maxX, room.center().getX() + room.rx() + 3);
            minZ = Math.min(minZ, room.center().getZ() - room.rz() - 3);
            maxZ = Math.max(maxZ, room.center().getZ() + room.rz() + 3);
        }
        return new int[]{minX, minZ, maxX, maxZ};
    }
}
