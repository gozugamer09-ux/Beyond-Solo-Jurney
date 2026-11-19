package com.beyondsolo.home;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.worldboss.WorldBossKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the hunters' homes are: a city at night in a world of its own ({@code beyondsolo:residence},
 * data/beyondsolo/dimension/residence.json), every home the top floor of its own tower in a city of its own, far out of
 * sight of the next ({@link #SPACING}). A hunter's home is their plot ({@link HomeData}); its {@link Layout} says where
 * everything on the floor is.
 *
 * <p>The game-test server has no datapack dimensions: there the homes are the floors alone, with no tower or city under
 * them, floating over the overworld down a lane of their own ({@link #TEST_Z}).</p>
 */
public final class HomeWorld {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, BeyondSolo.id("residence"));
    /** The top of the streets. */
    public static final int GROUND_Y = 63;
    /** The home's floor (the top of its tower). */
    public static final int FLOOR_Y = 150;
    /** How far apart the homes are: each tower stands in a city of its own, out of sight of the next. */
    public static final int SPACING = 1024;
    /** How far from a home its plot reaches (its city). */
    public static final int REACH = 200;
    /** In the game-test fallback (the overworld): the lane the homes float over, their floor and how far apart they are. */
    static final int TEST_Z = -170_000;
    static final int TEST_FLOOR_Y = 120;
    static final int TEST_SPACING = 160;

    /** The level the homes are in. The game-test server has no datapack dimensions, so there it is the overworld. */
    @Nullable
    public static ServerLevel level(MinecraftServer server) {
        ServerLevel level = server.getLevel(DIMENSION);
        if (level == null && testing(server)) return server.overworld();
        return level;
    }

    static boolean testing(MinecraftServer server) {
        return server instanceof net.minecraft.gametest.framework.GameTestServer;
    }

    /** True if {@code level} is the homes' world (in tests the overworld: ask with a position, {@link #inResidence}). */
    public static boolean isResidence(Level level) {
        return level.dimension().equals(DIMENSION);
    }

    /** True if {@code pos} in {@code level} is in a home's plot (its world; in tests: the homes' lane). */
    public static boolean inResidence(Level level, BlockPos pos) {
        return plotAt(level, pos) >= 0;
    }

    /** The plot {@code pos} is in, or -1 if it is in none. */
    public static int plotAt(Level level, BlockPos pos) {
        if (isResidence(level)) {
            int i = Math.round(pos.getX() / (float) SPACING);
            if (i < 0 || Math.abs(pos.getX() - i * SPACING) > REACH || Math.abs(pos.getZ()) > REACH) return -1;
            return i;
        }
        if (!(level instanceof ServerLevel server) || !testing(server.getServer()) || level.dimension() != Level.OVERWORLD) return -1;
        if (Math.abs(pos.getZ() - TEST_Z) > 60) return -1;
        int i = Math.round(pos.getX() / (float) TEST_SPACING);
        if (i < 0 || Math.abs(pos.getX() - i * TEST_SPACING) > TEST_SPACING / 2 - 4) return -1;
        return i;
    }

    /** Where plot {@code plot}'s home is in {@code level}: the full one in its world, the floor alone in tests. */
    public static Layout plan(ServerLevel level, int plot) {
        return plan(!isResidence(level), plot);
    }

    static Layout plan(boolean small, int plot) {
        BlockPos origin = small ? new BlockPos(plot * TEST_SPACING, TEST_FLOOR_Y, TEST_Z) : new BlockPos(plot * SPACING, FLOOR_Y, 0);
        return new Layout(plot, origin, small);
    }

    // ================================================================ the floor plan

    /**
     * The parts of a home, each a box with its walls (relative to the floor's middle; y 0 is the floor, north is -z).
     * The Studio is the middle of the floor; the Family Home's wing lies west of it and the Training Hall's east (twice
     * as tall); the Penthouse adds the gallery on top of the Studio and the terrace on the west wing's roof, each with a
     * floor of its own laid over the roof below (y 7).
     */
    public enum Room {
        STUDIO(HomeTier.STUDIO, -9, 0, -10, 9, 6, 10),
        FAMILY(HomeTier.FAMILY, -26, 0, -10, -9, 6, 10),
        HALL(HomeTier.HALL, 9, 0, -10, 26, 10, 10),
        GALLERY(HomeTier.PENTHOUSE, -9, 7, -10, 9, 13, 10),
        /** Open to the sky: its top is no roof. */
        TERRACE(HomeTier.PENTHOUSE, -26, 7, -10, -9, 13, 10);

        public final HomeTier tier;
        public final int x0, y0, z0, x1, y1, z1;

        Room(HomeTier tier, int x0, int y0, int z0, int x1, int y1, int z1) {
            this.tier = tier;
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
        }

        /** Whether a relative position is inside it (not its walls, floor or ceiling). */
        public boolean inside(int x, int y, int z) {
            return x > x0 && x < x1 && y > y0 && y < y1 && z > z0 && z < z1;
        }

        /** Whether a relative position is in it, its walls, floor and ceiling too. */
        public boolean within(int x, int y, int z) {
            return x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1;
        }
    }

    /** The whole floor's footprint (the tower's), relative: x -26..26, z -10..10. */
    public static final int HALF_X = 26, HALF_Z = 10;
    /** The kinds the trophy hall has a pedestal for, west row first (north to south), then the east row. */
    public static final WorldBossKind[] TROPHIES = {
            WorldBossKind.TITAN, WorldBossKind.BEHEMOTH, WorldBossKind.STILLNESS, WorldBossKind.HUNGER, WorldBossKind.STORMS,
            WorldBossKind.QUEEN, WorldBossKind.KING, WorldBossKind.VERDICT, WorldBossKind.WINGS, WorldBossKind.HAND};
    /** How many training dummies the hall has, and which of them is armoured. */
    public static final int DUMMIES = 3;
    public static final int ARMOURED_DUMMY = 0;

    /**
     * Where everything in one hunter's home is. {@code origin}: the floor block under the middle of the Studio. Every
     * spot named here is relative to it in the comments; the methods give world positions.
     */
    public record Layout(int plot, BlockPos origin, boolean small) {
        public BlockPos at(int dx, int dy, int dz) {
            return origin.offset(dx, dy, dz);
        }

        /** The middle of the floor, for distances. */
        public Vec3 middle() {
            return Vec3.atBottomCenterOf(origin.above());
        }

        /** Where a hunter stands when they come home: the foyer, before the lift (0, 1, 7), facing the window. */
        public Vec3 arrival() {
            return new Vec3(origin.getX() + 0.5, origin.getY() + 1, origin.getZ() + 7.5);
        }

        public float arrivalYaw() {
            return 180f;
        }

        /** The lift's door in the Studio's south wall (0, 1..2, 10). */
        public BlockPos door() {
            return at(0, 1, 10);
        }

        /** The lift's call button beside it: pressing it goes back down into the world (-2, 2, 9). */
        public BlockPos button() {
            return at(-2, 2, 9);
        }

        /** The heads of the hunter's own bed (a double: two beds side by side, 8, 1, 2..3, their heads east). */
        public List<BlockPos> beds() {
            return List.of(at(8, 1, 2), at(8, 1, 3));
        }

        public Direction bedFacing() {
            return Direction.EAST;
        }

        /** The head of their mother's bed in her room in the west wing (-25, 1, 5), its head west. */
        public BlockPos motherBed() {
            return at(-25, 1, 5);
        }

        public Direction motherBedFacing() {
            return Direction.WEST;
        }

        /** The Association's desk in the study: a lectern, read from the north (-13, 1, 4). */
        public BlockPos desk() {
            return at(-13, 1, 4);
        }

        /** The bell on the writing desk that brings the gate broker (-15, 2, 7). */
        public BlockPos bell() {
            return at(-15, 2, 7);
        }

        /** Where the training dummies stand in the hall (22, 1, -4 / 0 / 4), facing the door (west). */
        public Vec3 dummy(int i) {
            return new Vec3(origin.getX() + 22.5, origin.getY() + 1, origin.getZ() + 0.5 + (i - 1) * 4);
        }

        public float dummyYaw() {
            return 90f;
        }

        /** The trophy pedestals of the gallery: a west row and an east row (x -5 / 5, z -7, -4, -1, 2, 5), on its floor. */
        public BlockPos pedestal(int i) {
            int row = i / 5, k = i % 5;
            return at(row == 0 ? -5 : 5, 8, -7 + k * 3);
        }

        /** Which way pedestal {@code i}'s statue faces: across the aisle. */
        public Direction pedestalFacing(int i) {
            return i / 5 == 0 ? Direction.EAST : Direction.WEST;
        }

        /** The steps up to the gallery (Penthouse): x 2..8 rising east along the south wall (z 8..9), the last in its floor (8, 7). */
        public List<BlockPos> stairs() {
            List<BlockPos> out = new ArrayList<>();
            for (int k = 0; k <= 6; k++) {
                for (int z = 8; z <= 9; z++) out.add(at(2 + k, 1 + k, z));
            }
            return out;
        }

        /** Relative coordinates of a world position. */
        public int rx(BlockPos p) {
            return p.getX() - origin.getX();
        }

        public int ry(BlockPos p) {
            return p.getY() - origin.getY();
        }

        public int rz(BlockPos p) {
            return p.getZ() - origin.getZ();
        }

        /** Whether a world position is within the plot (the home, its tower and its city). */
        public boolean inPlot(BlockPos p) {
            return Math.abs(rx(p)) <= REACH && Math.abs(rz(p)) <= REACH;
        }

        /** The part of the home a world position is inside (not its walls), or null. */
        @Nullable
        public Room roomAt(BlockPos p) {
            int x = rx(p), y = ry(p), z = rz(p);
            for (Room r : Room.values()) if (r.inside(x, y, z)) return r;
            return null;
        }

        /** Whether a world position is part of the home's floor plan at all (its rooms with their walls). */
        public boolean onFloor(BlockPos p) {
            int x = rx(p), y = ry(p), z = rz(p);
            for (Room r : Room.values()) if (r.within(x, y, z)) return true;
            return false;
        }

        /**
         * What nobody but an operator can break or build over in a home: the lift and its button, the beds, the desk and
         * its bell, the pedestals and the steps up. Everything else inside the rooms is the hunter's to arrange.
         */
        public boolean fixture(BlockPos p) {
            int x = rx(p), y = ry(p), z = rz(p);
            // the lift: its door and frame, the button
            if (z >= 9 && z <= 10 && x >= -2 && x <= 1 && y >= 1 && y <= 3) return true;
            // the beds (head and foot)
            if (y == 1 && (x == 7 || x == 8) && (z == 2 || z == 3)) return true;
            if (y == 1 && (x == -25 || x == -24) && z == 5) return true;
            // the desk and the bell on its writing table
            if (x == -13 && y == 1 && z == 4) return true;
            if (x == -15 && (y == 1 || y == 2) && z == 7) return true;
            // the pedestals
            for (int i = 0; i < TROPHIES.length; i++) if (p.equals(pedestal(i))) return true;
            // the steps up and the rail round the stairwell
            if (z >= 8 && z <= 9 && x >= 2 && x <= 8 && y >= 1 && y <= 7) return true;
            if (y == 8 && ((z == 7 && x >= 4 && x <= 7) || (x == 4 && z >= 7 && z <= 9))) return true;
            return false;
        }
    }

    private HomeWorld() {}
}
