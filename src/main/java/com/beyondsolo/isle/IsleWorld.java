package com.beyondsolo.isle;

import com.beyondsolo.BeyondSolo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Halcyon Isle: an island resort the swarm took years ago, in a world of its own ({@code beyondsolo:halcyon_isle}, open
 * sea to every horizon, data/beyondsolo/dimension/halcyon_isle.json). One island, built once by {@link IsleBuilder} and
 * set back up before every raid: the Association's camp on the south beach, the ruins of the resort, the swarm's nests,
 * the great hive at the island's heart, and deep under it, in the rock below the sea floor, the queen's chamber.
 *
 * <p>The game-test server has no datapack dimensions: there the island is a small one, floating high over the
 * overworld far down a lane of its own ({@link #TEST_CENTER}).</p>
 */
public final class IsleWorld {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, BeyondSolo.id("halcyon_isle"));
    /** The sea's surface (the top of the water) in the island's world. */
    public static final int SEA_Y = 62;
    /** The island's middle in its world. */
    static final BlockPos CENTER = new BlockPos(0, SEA_Y, 0);
    /** In the game-test fallback (the overworld): the small island's middle. */
    static final BlockPos TEST_CENTER = new BlockPos(0, SEA_Y + 40, -150_000);

    /**
     * Where everything on the island is. {@code radius}: the coast's mean distance from {@code center}; the coast itself
     * wanders by {@link #coast}. {@code base}: the lowest level the island's ground is laid from (the sea floor). Every
     * spot is the top block of the ground there: whoever stands on it stands one block above.
     */
    public record Layout(BlockPos center, int radius, int base, long seed, boolean small,
                         BlockPos camp, BlockPos circle, List<BlockPos> nests, List<BlockPos> ruins,
                         int hiveRadius, int hiveHeight, BlockPos mouth, BlockPos descent,
                         BlockPos chamber, int chamberRadius, int chamberHeight, BlockPos dais, BlockPos arrival, BlockPos cocoon) {
        /** The way the camp faces: north, up the island to the hive. */
        public float campYaw() {
            return 180f;
        }

        /** The ground's top block at a column (sea level and below for the open sea). */
        public int groundAt(int x, int z) {
            return IsleWorld.groundAt(this, x, z);
        }

        /** Whether a column is part of the island (inside its coast). */
        public boolean land(int x, int z) {
            return norm(this, x, z) < 1.0;
        }

        /** Whether a position lies on the island or in the sea round it, or in its chamber. */
        public boolean contains(BlockPos p) {
            int dx = p.getX() - center.getX(), dz = p.getZ() - center.getZ();
            int reach = radius + 64;
            return dx * dx + dz * dz <= reach * reach && p.getY() > chamber.getY() - 40;
        }

        /** Whether a position is in the queen's chamber (or the tunnel into it). */
        public boolean inChamber(BlockPos p) {
            int dx = p.getX() - chamber.getX(), dz = p.getZ() - chamber.getZ();
            int r = chamberRadius + 8;
            return dx * dx + dz * dz <= r * r && p.getY() >= chamber.getY() - 4 && p.getY() <= chamber.getY() + chamberHeight + 4;
        }
    }

    /** The level the island is in. The game-test server has no datapack dimensions, so there it is the overworld. */
    @Nullable
    public static ServerLevel level(MinecraftServer server) {
        ServerLevel level = server.getLevel(DIMENSION);
        if (level == null && server instanceof net.minecraft.gametest.framework.GameTestServer) return server.overworld();
        return level;
    }

    /** True if {@code level} is the island's world (in tests the overworld: ask with a position, {@link #onIsle}). */
    public static boolean isIsle(Level level) {
        return level.dimension().equals(DIMENSION);
    }

    /** True if {@code pos} in {@code level} is on Halcyon Isle (its world; in tests: the small island's lane). */
    public static boolean onIsle(Level level, BlockPos pos) {
        if (isIsle(level)) return true;
        if (!(level instanceof ServerLevel server) || !(server.getServer() instanceof net.minecraft.gametest.framework.GameTestServer)) return false;
        if (level.dimension() != Level.OVERWORLD) return false;
        return Math.abs(pos.getZ() - TEST_CENTER.getZ()) < 400 && Math.abs(pos.getX() - TEST_CENTER.getX()) < 400;
    }

    // ================================================================ planning

    /** The island's layout in {@code level} for a seed and number of nests: the full island in its world, the small one in tests. */
    public static Layout plan(ServerLevel level, long seed, int nests) {
        return plan(!isIsle(level), seed, nests);
    }

    /** The full island in its own world, or the small one of the game tests. */
    static Layout plan(boolean small, long seed, int nests) {
        return small ? plan(TEST_CENTER, 30, TEST_CENTER.getY() - 8, seed, nests, true) : plan(CENTER, 84, SEA_Y - 11, seed, nests, false);
    }

    static Layout plan(BlockPos center, int radius, int base, long seed, int count, boolean small) {
        RandomSource r = RandomSource.create(seed ^ 0x15_1E_C0A57L);
        int cx = center.getX(), cz = center.getZ();
        int hiveR = small ? 8 : 22;
        int hiveH = small ? 10 : 26;
        Layout probe = new Layout(center, radius, base, seed, small, center, center, List.of(), List.of(), hiveR, hiveH, center, center,
                center, 1, 1, center, center, center);

        // the camp: near the south beach, its back to the sea (measured from where the coast runs there, so its circle of
        // light is always on land)
        double south = radius * coast(seed, Math.PI);
        int campZ = cz + (int) Math.round(south * (small ? 0.62 : 0.74));
        BlockPos camp = new BlockPos(cx, groundAt(probe, cx, campZ), campZ);
        BlockPos circle = new BlockPos(cx, camp.getY(), campZ + (small ? 4 : 7));

        // the nests: round the hive, none on the camp's side of the island
        List<BlockPos> nests = new ArrayList<>();
        double start = r.nextDouble() * 0.4;
        // south (+z) is the camp's: angles measured from north (-z) clockwise, the nests spread over 290 degrees
        double span = Math.toRadians(290);
        for (int i = 0; i < count; i++) {
            double a = Math.toRadians(-145) + span * ((i + 0.5) / count) + (r.nextDouble() - 0.5) * (span / count) * 0.35 + start * 0.1;
            double d = radius * (small ? 0.6 : 0.52 + 0.14 * r.nextDouble());
            int x = cx + (int) Math.round(Math.sin(a) * d);
            int z = cz - (int) Math.round(Math.cos(a) * d);
            nests.add(new BlockPos(x, groundAt(probe, x, z), z));
        }

        // the ruins of the resort: between the camp and the hive, and along the coast
        List<BlockPos> ruins = new ArrayList<>();
        int want = small ? 1 : 7;
        for (int i = 0; i < 60 && ruins.size() < want; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = radius * (0.32 + 0.48 * r.nextDouble());
            int x = cx + (int) Math.round(Math.sin(a) * d);
            int z = cz - (int) Math.round(Math.cos(a) * d);
            if (!probe.land(x, z) || norm(probe, x, z) > 0.8) continue;
            BlockPos at = new BlockPos(x, groundAt(probe, x, z), z);
            if (tooClose(at, nests, small ? 8 : 18) || tooClose(at, ruins, small ? 8 : 24) || tooClose(at, List.of(camp), small ? 10 : 22)) continue;
            if (Math.hypot(x - cx, z - cz) < hiveR + (small ? 6 : 12)) continue;
            ruins.add(at);
        }

        // the great hive: the mouth on its south side, the pit down into the dark a little way in
        int gy = groundAt(probe, cx, cz + hiveR);
        BlockPos mouth = new BlockPos(cx, gy, cz + hiveR - 1);
        BlockPos descent = new BlockPos(cx, gy, cz + hiveR - (small ? 4 : 7));

        // the queen's chamber: deep in the rock under the island (in tests, a vault hanging under the small island)
        int chamberR = small ? 13 : 24;
        int chamberH = small ? 9 : 15;
        int floor = small ? base - chamberH - 10 : 28;
        BlockPos chamber = new BlockPos(cx, floor, cz);
        BlockPos dais = new BlockPos(cx, floor + 1, cz - chamberR + (small ? 5 : 8));
        BlockPos arrival = new BlockPos(cx, floor, cz + chamberR - (small ? 3 : 4));
        BlockPos cocoon = new BlockPos(cx + (small ? 6 : 11), floor, cz - chamberR + (small ? 4 : 7));
        return new Layout(center, radius, base, seed, small, camp, circle, List.copyOf(nests), List.copyOf(ruins), hiveR, hiveH, mouth, descent,
                chamber, chamberR, chamberH, dais, arrival, cocoon);
    }

    private static boolean tooClose(BlockPos at, List<BlockPos> others, int min) {
        for (BlockPos o : others) {
            int dx = o.getX() - at.getX(), dz = o.getZ() - at.getZ();
            if (dx * dx + dz * dz < min * min) return true;
        }
        return false;
    }

    // ================================================================ the land's shape

    /** How far out a column is: 0 at the middle, 1 on the coast (which wanders with the angle). */
    static double norm(Layout l, int x, int z) {
        double dx = x - l.center().getX(), dz = z - l.center().getZ();
        double a = Math.atan2(dx, -dz);
        return Math.sqrt(dx * dx + dz * dz) / (l.radius() * coast(l.seed(), a));
    }

    /** The coast's distance at an angle, as a share of the mean radius. */
    static double coast(long seed, double a) {
        double p1 = (seed & 0xFF) / 40.0, p2 = ((seed >> 8) & 0xFF) / 40.0, p3 = ((seed >> 16) & 0xFF) / 40.0;
        return 1 + 0.11 * Math.sin(3 * a + p1) + 0.07 * Math.sin(5 * a + p2) + 0.04 * Math.sin(9 * a + p3);
    }

    /** Rolling ground, 0..1. */
    static double hills(long seed, int x, int z) {
        double p1 = ((seed >> 24) & 0xFF) / 30.0, p2 = ((seed >> 32) & 0xFF) / 30.0;
        double v = 0.5 + 0.25 * Math.sin(x * 0.052 + p1) * Math.cos(z * 0.047 + p2) + 0.17 * Math.sin((x + z) * 0.031 + p2)
                + 0.08 * Math.sin(x * 0.17 + z * 0.13 + p1);
        return Mth.clamp(v, 0, 1);
    }

    /** The top block of the ground at a column: the sea's surface or below off the coast, sand at the beach, hills inland. */
    static int groundAt(Layout l, int x, int z) {
        double d = norm(l, x, z);
        if (d >= 1.0) return l.small() ? l.base() : SEA_Y - 1;
        int sea = l.small() ? l.center().getY() : SEA_Y;
        double coast = Mth.clamp((1 - d) / 0.16, 0, 1);
        double inland = Mth.clamp((0.86 - d) / 0.5, 0, 1);
        double hill = hills(l.seed(), x, z);
        return sea + (int) Math.round(1 + coast * 1.6 + inland * (l.small() ? 2 + 2 * hill : 3 + 6 * hill));
    }

    /** Whether a column of the island is beach (sand to the top). */
    static boolean beach(Layout l, int x, int z) {
        return norm(l, x, z) > 0.84;
    }

    private IsleWorld() {}
}
