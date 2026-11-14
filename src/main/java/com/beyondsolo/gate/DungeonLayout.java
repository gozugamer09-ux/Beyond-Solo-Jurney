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

    public enum Kind { ENTRANCE, CAVERN, SIDE, BOSS }

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

    /** Builds the plan for a gate. */
    public static DungeonLayout plan(long seed, GateTheme theme) {
        RandomSource r = new LegacyRandomSource(seed);
        List<Room> rooms = new ArrayList<>();
        List<Tunnel> tunnels = new ArrayList<>();

        rooms.add(new Room(0, new BlockPos(0, BASE_Y, 0), 8, 6, 8, Kind.ENTRANCE));
        float heading = r.nextFloat() * Mth.TWO_PI;
        int main = theme.rooms;
        for (int i = 1; i <= main; i++) {
            Room prev = rooms.get(rooms.size() - 1);
            boolean boss = i == main;
            int rx = boss ? 17 : 8 + r.nextInt(5);
            int rz = boss ? 17 : 8 + r.nextInt(5);
            int ry = boss ? 11 : 6 + r.nextInt(3);
            Room room = null;
            for (int attempt = 0; attempt < 24 && room == null; attempt++) {
                float turn = (r.nextFloat() - 0.5f) * (attempt < 12 ? 1.4f : 3.2f);
                float a = heading + turn;
                int dist = prev.reach() + Math.max(rx, rz) + 9 + r.nextInt(8);
                int y = Mth.clamp(prev.center().getY() + r.nextInt(11) - 5, MIN_Y + ry, MAX_Y - ry);
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
        int sides = 1 + (theme.rank.tier() >= 3 ? 1 : 0);
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
        return new DungeonLayout(List.copyOf(rooms), List.copyOf(tunnels));
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
