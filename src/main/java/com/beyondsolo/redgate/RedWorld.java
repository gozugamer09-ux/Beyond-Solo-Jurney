package com.beyondsolo.redgate;

import com.beyondsolo.BeyondSolo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The world behind a red gate: the Red Winter, a frozen land in its own dimension (snowfields, spruce woods, ice spires,
 * seas frozen over; terrain from {@code data/beyondsolo/worldgen/noise_settings/red_winter.json}). Each red gate gets
 * its own patch of it, {@link #SLOT_SPACING} apart: where its hunters arrive (by the way out, sealed), the keep of the
 * land's master about a hundred blocks off, and camps of its creatures in between ({@link RedWorldBuilder}). Past
 * {@link #EDGE} blocks the blizzard drives them back.
 *
 * <p>The dimension's sky follows the overworld's clock on the server (Minecraft keeps one clock for every dimension),
 * so the red gate keeps its own ({@link RedGate#insideTime}) and the client shows that one.</p>
 */
public final class RedWorld {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, BeyondSolo.id("red_gate"));
    public static final int SLOT_SPACING = 4096;
    /** In the game-test fallback (the overworld) patches lie along this z line, far from everything else. */
    static final int TEST_LANE_Z = 120_000;
    /** The edge of a patch, from where hunters arrive. */
    public static final int EDGE = 200;
    /** The inside clock starts at early morning of day 1; night falls at dusk and lifts at dawn. */
    public static final long START_TIME = 1000;
    public static final int NIGHT_START = 13000;
    public static final int NIGHT_END = 23000;
    static final int CAMPS = 5;

    /** Where a patch goes: the arrival, the keep, the camps (each at its standing height), the direction to the keep. */
    record Plan(BlockPos center, BlockPos keep, List<BlockPos> camps, float keepYaw) {}

    /** The level red gates lead into. The game-test server has no datapack dimensions, so there it is the overworld. */
    @Nullable
    public static ServerLevel level(MinecraftServer server) {
        ServerLevel level = server.getLevel(DIMENSION);
        if (level == null && server instanceof net.minecraft.gametest.framework.GameTestServer) return server.overworld();
        return level;
    }

    /** True if {@code level} is the Red Winter (in tests: the overworld, so ask with a position, see {@link #inPatch}). */
    public static boolean isRedWorld(Level level) {
        return level.dimension().equals(DIMENSION);
    }

    /** The slot of the patch a position lies in, or -1. */
    static int slotAt(Level level, BlockPos pos) {
        boolean test = !level.dimension().equals(DIMENSION);
        if (test && Math.abs(pos.getZ() - TEST_LANE_Z) > 4096) return -1;
        int slot = Math.floorDiv(pos.getX() + SLOT_SPACING / 2, SLOT_SPACING);
        return slot >= 1 ? slot : -1;
    }

    /** True if {@code pos} in {@code level} lies in a red gate's patch. */
    public static boolean inPatch(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return isRedWorld(level);
        return level(server.getServer()) == server && slotAt(level, pos) >= 1;
    }

    // ------------------------------------------------------------------ planning a patch

    /**
     * Finds where a patch goes: the first spot near the slot's line with dry land for the arrival and a fairly level
     * stretch for the keep (camps where the land allows). Reads the terrain from the generator, so nothing has to be
     * generated yet. Falls back to the slot's own spot (the builders lay their own ground).
     */
    static Plan plan(ServerLevel level, int slot, long seed) {
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        RandomState rs = level.getChunkSource().randomState();
        int sea = gen.getSeaLevel();
        RandomSource r = RandomSource.create(seed ^ 0x7ED_6A7EL);
        boolean test = !level.dimension().equals(DIMENSION);
        int baseX = slot * SLOT_SPACING;
        int baseZ = test ? TEST_LANE_Z : 0;
        Terrain t = new Terrain(level, gen, rs, sea);
        for (int k = 0; k < 12; k++) {
            int cz = baseZ + (k == 0 ? 0 : (k % 2 == 1 ? 1 : -1) * ((k + 1) / 2) * 600);
            int cx = baseX + (k == 0 ? 0 : r.nextInt(801) - 400);
            Plan p = tryPlan(t, cx, cz, r);
            if (p != null) return p;
        }
        // nowhere dry enough: build anyway (the builders raise their own ground)
        int y = Math.max(sea + 1, t.raw(baseX, baseZ));
        BlockPos c = new BlockPos(baseX, y, baseZ);
        List<BlockPos> camps = new ArrayList<>();
        for (int i = 0; i < CAMPS; i++) {
            double a = i * Math.PI * 2 / CAMPS + 0.4;
            camps.add(new BlockPos(baseX + (int) (Math.cos(a) * 60), y, baseZ + (int) (Math.sin(a) * 60)));
        }
        return new Plan(c, new BlockPos(baseX, y, baseZ - 115), camps, 180f);
    }

    @Nullable
    private static Plan tryPlan(Terrain t, int cx, int cz, RandomSource r) {
        Integer a = t.site(cx, cz, 7, 6, 4);
        if (a == null) return null;
        BlockPos center = new BlockPos(cx, a, cz);

        // the keep: the levellest dry stretch about a hundred blocks out
        BlockPos keep = null;
        int bestSpread = Integer.MAX_VALUE;
        float keepYaw = 0;
        double start = r.nextDouble() * Math.PI * 2;
        for (int i = 0; i < 12 && bestSpread > 3; i++) {
            double ang = start + i * Math.PI * 2 / 12;
            for (int d : new int[]{110, 124}) {
                int kx = cx + (int) Math.round(Math.cos(ang) * d), kz = cz + (int) Math.round(Math.sin(ang) * d);
                int[] spread = new int[1];
                Integer ky = t.site(kx, kz, 15, 8, 10, spread);
                if (ky == null || spread[0] >= bestSpread) continue;
                bestSpread = spread[0];
                keep = new BlockPos(kx, ky, kz);
                keepYaw = (float) (Math.toDegrees(Math.atan2(kz - cz, kx - cx)) - 90.0);
            }
        }
        if (keep == null) return null;

        // camps: dry clearings spread around, not crowding each other or the keep
        List<BlockPos> camps = new ArrayList<>();
        double cstart = r.nextDouble() * Math.PI * 2;
        for (int i = 0; i < 18 && camps.size() < CAMPS; i++) {
            double ang = cstart + i * Math.PI * 2 / 18 * 7;
            int d = 44 + r.nextInt(44);
            int x = cx + (int) Math.round(Math.cos(ang) * d), z = cz + (int) Math.round(Math.sin(ang) * d);
            if (keep.distSqr(new BlockPos(x, keep.getY(), z)) < 40 * 40) continue;
            boolean crowded = false;
            for (BlockPos o : camps) if (o.distSqr(new BlockPos(x, o.getY(), z)) < 34 * 34) crowded = true;
            if (crowded) continue;
            Integer y = t.site(x, z, 7, 6, 5);
            if (y != null) camps.add(new BlockPos(x, y, z));
        }
        if (camps.size() < 3) return null;
        return new Plan(center, keep, camps, keepYaw);
    }

    /** The generator's terrain, sampled without generating anything. */
    private record Terrain(ServerLevel level, ChunkGenerator gen, RandomState rs, int sea) {
        static final int WET = Integer.MIN_VALUE;

        /** Standing height at a column (top of the ground), or {@link #WET} under water. */
        int at(int x, int z) {
            int floor = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, rs);
            int top = gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, rs);
            return top > floor ? WET : floor;
        }

        int raw(int x, int z) {
            return gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, rs);
        }

        @Nullable
        Integer site(int x, int z, int radius, int points, int maxSpread) {
            return site(x, z, radius, points, maxSpread, new int[1]);
        }

        /** The standing height of a dry, fairly level spot, or null. Its spread (highest minus lowest) goes in {@code spread[0]}. */
        @Nullable
        Integer site(int x, int z, int radius, int points, int maxSpread, int[] spread) {
            int h = at(x, z);
            if (h == WET || h < sea + 1 || h > sea + 72) return null;
            int lo = h, hi = h;
            for (int i = 0; i < points; i++) {
                double a = i * Math.PI * 2 / points;
                int s = at(x + (int) Math.round(Math.cos(a) * radius), z + (int) Math.round(Math.sin(a) * radius));
                if (s == WET) return null;
                lo = Math.min(lo, s);
                hi = Math.max(hi, s);
                if (hi - lo > maxSpread) return null;
            }
            spread[0] = hi - lo;
            return Mth.floor((lo + hi) / 2.0 + 0.5);
        }
    }

    private RedWorld() {}
}
