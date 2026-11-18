package com.beyondsolo.tower;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The halls of the Crimson Citadel. Every hall is worked out block by block from its shape, its wing's palette and
 * where it stands, so the same floor always looks the same and the build can be spread over a few ticks
 * ({@link Job}). The shell is solid all round (with barred windows onto the crimson sky), so a hall can stand
 * anywhere: in the Citadel's own dimension, or the overworld in game tests.
 *
 * <ul>
 *   <li>{@link Shape#NAVE}: a long vaulted hall, two rows of pillars with banners, hanging lanterns. Ordinary,
 *       elite and siege floors.</li>
 *   <li>{@link Shape#ROTUNDA}: a round domed hall with a ring of pillars, where a gatekeeper waits.</li>
 *   <li>{@link Shape#THRONE}: the lord's hall, long and high, a dais with his throne at the far end.</li>
 *   <li>{@link Shape#COLOSSEUM}: the boss rush's arena, open to the sky, tiers of stone around it.</li>
 * </ul>
 *
 * <p>The player comes in at the south end (the way back is barred) and the stair onwards leaves from the north wall,
 * sealed with light (barrier blocks the client draws over) until the floor is won. The exit sigil, a small inlay near
 * the start, lights up then too.</p>
 */
public final class CitadelHalls {
    public enum Shape { NAVE, ROTUNDA, THRONE, COLOSSEUM }

    /** Halls stand on this height, each run in its own slot along x, two pads per slot for the halls and one for the colosseum. */
    public static final int FLOOR_Y = 100;
    public static final int LANE_Z = 90_000;
    public static final int SLOT_SPACING = 1024;
    public static final int PAD_SPACING = 160;
    public static final int COLOSSEUM_PAD = 2;
    /** Steps on the stair out of a hall. */
    static final int STEPS = 7;

    /** The box every hall is built in, around its origin (cleared of whatever stood there before). */
    static final int BOX_X = 18, BOX_Z_MIN = -37, BOX_Z_MAX = 25, BOX_Y_MIN = -4, BOX_Y_MAX = 20;
    static final int COL_R = 20, COL_BOX = 32, COL_Y_MAX = 14;

    /** Blocks written per tick by a {@link Job}. */
    public static final int BUDGET = 12_000;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState BARRIER = Blocks.BARRIER.defaultBlockState();
    private static final BlockState LIGHT = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);
    private static final BlockState LIGHT_SOFT = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 11);

    /**
     * Where everything in a built hall is (world coordinates).
     *
     * @param door      the stair door: the middle of the sealed row, at the door's foot (null for the colosseum)
     * @param stairBox  reaching it climbs to the next floor (null for the colosseum)
     * @param stairTop  the landing at the top of the stair (for its light)
     * @param exit      the exit sigil, on the floor
     * @param bossSpot  where a gatekeeper, the lord or a rush's boss waits
     * @param spawns    where creatures may come out
     * @param bounds    the inside of the hall (creatures that leave it are brought back)
     * @param box       everything the build touches
     */
    public record Layout(Shape shape, BlockPos origin, Vec3 start, float startYaw, @Nullable BlockPos door, @Nullable AABB stairBox,
                         BlockPos stairTop, BlockPos exit, Vec3 bossSpot, List<Vec3> spawns, AABB bounds, AABB box) {}

    // ================================================================ where

    public static BlockPos origin(int slot, int pad) {
        return new BlockPos(slot * SLOT_SPACING, FLOOR_Y, LANE_Z + pad * PAD_SPACING);
    }

    public static Shape shapeFor(int floor) {
        return switch (TowerFloors.kind(floor)) {
            case KEEPER -> Shape.ROTUNDA;
            case LORD -> Shape.THRONE;
            default -> Shape.NAVE;
        };
    }

    // ---------------------------------------------------------------- each shape's measures

    /** The nave: interior x -12..12, z -18..18; straight walls 6 high, then the vault up to 13. */
    private static final int NAVE_W = 12, NAVE_S = 18, NAVE_N = -18, NAVE_WALL = 5, NAVE_H = 13;
    private static final int[] NAVE_PILLARS_Z = {-14, -8, -2, 4, 10};
    private static final int[] NAVE_WINDOWS_Z = {-11, -5, 1, 7, 13};
    /** The rotunda: radius 17, walls 5 high, a dome up to 15. */
    private static final int ROT_R = 17, ROT_WALL = 4, ROT_H = 15, ROT_PILLAR_R = 13;
    /** The throne hall: interior x -14..14, z -22..20, walls 8 high, the vault up to 17; the dais from z -15 north. */
    private static final int THR_W = 14, THR_S = 20, THR_N = -22, THR_WALL = 7, THR_H = 17, THR_DAIS = 2;
    private static final int[] THR_PILLARS_Z = {-12, -5, 2, 9, 16};
    private static final int[] THR_WINDOWS_Z = {-9, -2, 5, 12};

    public static Layout layout(Shape shape, BlockPos o) {
        return switch (shape) {
            case NAVE -> hallLayout(shape, o, NAVE_S, NAVE_N, 0, new BlockPos(-9, 0, 15), new Vec3(0, 0, -8), naveSpawns());
            case ROTUNDA -> hallLayout(shape, o, ROT_R - 3, -ROT_R, 0, new BlockPos(-10, 0, 10), new Vec3(0, 0, -6), ringSpawns(new int[]{8, 11}, 30));
            case THRONE -> hallLayout(shape, o, THR_S - 3, THR_N, THR_DAIS, new BlockPos(-11, 0, 17), new Vec3(0, THR_DAIS, -17), throneSpawns());
            case COLOSSEUM -> {
                Vec3 start = Vec3.atBottomCenterOf(o.offset(0, 0, 15));
                List<Vec3> spawns = new ArrayList<>();
                for (Vec3 s : ringSpawns(new int[]{11, 15}, 30)) spawns.add(s.add(o.getX() + 0.5, o.getY(), o.getZ() + 0.5));
                AABB bounds = new AABB(o.getX() - COL_R, o.getY() - 1, o.getZ() - COL_R, o.getX() + COL_R + 1, o.getY() + 12, o.getZ() + COL_R + 1);
                AABB box = new AABB(o.getX() - COL_BOX, o.getY() + BOX_Y_MIN, o.getZ() - COL_BOX, o.getX() + COL_BOX + 1, o.getY() + COL_Y_MAX + 1, o.getZ() + COL_BOX + 1);
                yield new Layout(shape, o, start, 180f, null, null, o.offset(0, 0, -8), o.offset(0, 0, 11),
                        Vec3.atBottomCenterOf(o.offset(0, 0, -8)), spawns, bounds, box);
            }
        };
    }

    private static Layout hallLayout(Shape shape, BlockPos o, int startZ, int north, int base, BlockPos exit, Vec3 boss, List<Vec3> spawnOffsets) {
        Vec3 start = Vec3.atBottomCenterOf(o.offset(0, 0, startZ));
        BlockPos door = o.offset(0, base, north - 1);
        int landing = north - 3 - STEPS;
        AABB stair = new AABB(o.getX() - 1, o.getY() + base + STEPS - 2, o.getZ() + landing - 1,
                o.getX() + 2, o.getY() + base + STEPS + 4, o.getZ() + landing + 3);
        BlockPos top = o.offset(0, base + STEPS, landing);
        List<Vec3> spawns = new ArrayList<>();
        for (Vec3 s : spawnOffsets) spawns.add(s.add(o.getX() + 0.5, o.getY(), o.getZ() + 0.5));
        int halfW = shape == Shape.THRONE ? THR_W : shape == Shape.ROTUNDA ? ROT_R : NAVE_W;
        int south = shape == Shape.THRONE ? THR_S : shape == Shape.ROTUNDA ? ROT_R : NAVE_S;
        int height = shape == Shape.THRONE ? THR_H : shape == Shape.ROTUNDA ? ROT_H : NAVE_H;
        AABB bounds = new AABB(o.getX() - halfW, o.getY() - 1, o.getZ() + north, o.getX() + halfW + 1, o.getY() + height, o.getZ() + south + 1);
        AABB box = new AABB(o.getX() - BOX_X, o.getY() + BOX_Y_MIN, o.getZ() + BOX_Z_MIN, o.getX() + BOX_X + 1, o.getY() + BOX_Y_MAX + 1, o.getZ() + BOX_Z_MAX + 1);
        return new Layout(shape, o, start, 180f, door, stair, top, o.offset(exit), Vec3.atBottomCenterOf(o).add(boss), spawns, bounds, box);
    }

    private static List<Vec3> naveSpawns() {
        List<Vec3> out = new ArrayList<>();
        for (int x : new int[]{-10, -5, 0, 5, 10}) {
            for (int z = -15; z <= 7; z += 5) {
                if (Math.abs(x) == 5 && z == -15) continue;
                out.add(new Vec3(x, 0, z));
            }
        }
        return out;
    }

    private static List<Vec3> throneSpawns() {
        List<Vec3> out = new ArrayList<>();
        for (int x : new int[]{-11, -5, 0, 5, 11}) {
            for (int z = -12; z <= 10; z += 5) out.add(new Vec3(x, 0, z));
        }
        return out;
    }

    /** Points on rings, all the way round but the south (where the player comes in). */
    private static List<Vec3> ringSpawns(int[] radii, int stepDegrees) {
        List<Vec3> out = new ArrayList<>();
        for (int r : radii) {
            for (int a = 0; a < 360; a += stepDegrees) {
                if (a > 55 && a < 125) continue; // +z is south: keep the start clear
                double rad = Math.toRadians(a);
                out.add(new Vec3(Math.round(Math.cos(rad) * r), 0, Math.round(Math.sin(rad) * r)));
            }
        }
        return out;
    }

    // ================================================================ palettes

    /**
     * A wing's materials.
     *
     * @param carpet the runner down the middle (null: bare floor)
     * @param glow   a block that gives light, set into the floor and the walls
     * @param niche  stands against the walls between the windows (oriented by {@link #oriented})
     * @param banner the wall banner hung on the pillars (null: none)
     */
    record Palette(BlockState floor, BlockState floorAlt, BlockState inlay, @Nullable BlockState carpet, BlockState wall, BlockState wallAlt,
                   BlockState trim, BlockState pillar, BlockState cap, BlockState ceiling, BlockState stairs, BlockState lantern,
                   BlockState glow, BlockState window, BlockState niche, @Nullable BlockState banner) {
        Palette spire() {
            // the spire: the same halls, gone over to the void (crying obsidian, amethyst, violet light)
            return new Palette(floor, floorAlt, Blocks.CRYING_OBSIDIAN.defaultBlockState(), Blocks.PURPLE_CARPET.defaultBlockState(), wall,
                    Blocks.OBSIDIAN.defaultBlockState(), trim, pillar, Blocks.AMETHYST_BLOCK.defaultBlockState(), ceiling, stairs,
                    Blocks.SOUL_LANTERN.defaultBlockState(), Blocks.CRYING_OBSIDIAN.defaultBlockState(), window,
                    Blocks.AMETHYST_CLUSTER.defaultBlockState(), Blocks.PURPLE_WALL_BANNER.defaultBlockState());
        }
    }

    static Palette palette(TowerFloors.Wing wing, boolean spire) {
        Palette p = switch (wing) {
            case OUTER_GATE -> new Palette(b(Blocks.STONE_BRICKS), b(Blocks.MOSSY_STONE_BRICKS), b(Blocks.CHISELED_STONE_BRICKS), b(Blocks.RED_CARPET),
                    b(Blocks.STONE_BRICKS), b(Blocks.CRACKED_STONE_BRICKS), b(Blocks.POLISHED_ANDESITE), b(Blocks.STONE_BRICKS), b(Blocks.CHISELED_STONE_BRICKS),
                    b(Blocks.DEEPSLATE_BRICKS), b(Blocks.STONE_BRICK_STAIRS), b(Blocks.LANTERN), b(Blocks.SHROOMLIGHT),
                    b(Blocks.IRON_BARS), b(Blocks.CAMPFIRE), b(Blocks.RED_WALL_BANNER));
            case BARRACKS -> new Palette(b(Blocks.SPRUCE_PLANKS), b(Blocks.DARK_OAK_PLANKS), b(Blocks.STRIPPED_SPRUCE_WOOD), b(Blocks.BROWN_CARPET),
                    b(Blocks.DEEPSLATE_BRICKS), b(Blocks.DEEPSLATE_TILES), b(Blocks.STRIPPED_DARK_OAK_WOOD), b(Blocks.STRIPPED_SPRUCE_WOOD), b(Blocks.DARK_OAK_PLANKS),
                    b(Blocks.SPRUCE_PLANKS), b(Blocks.SPRUCE_STAIRS), b(Blocks.LANTERN), b(Blocks.OCHRE_FROGLIGHT),
                    b(Blocks.IRON_BARS), b(Blocks.BARREL), b(Blocks.BLACK_WALL_BANNER));
            case OSSUARY -> new Palette(b(Blocks.DEEPSLATE_TILES), b(Blocks.CRACKED_DEEPSLATE_TILES), b(Blocks.BONE_BLOCK), null,
                    b(Blocks.DEEPSLATE_BRICKS), b(Blocks.BONE_BLOCK), b(Blocks.POLISHED_DEEPSLATE), b(Blocks.BONE_BLOCK), b(Blocks.CHISELED_DEEPSLATE),
                    b(Blocks.DEEPSLATE_TILES), b(Blocks.DEEPSLATE_TILE_STAIRS), b(Blocks.SOUL_LANTERN), b(Blocks.SOUL_SOIL),
                    b(Blocks.IRON_BARS), b(Blocks.SKELETON_WALL_SKULL), b(Blocks.WHITE_WALL_BANNER));
            case FORGE -> new Palette(b(Blocks.POLISHED_BLACKSTONE), b(Blocks.POLISHED_BLACKSTONE_BRICKS), b(Blocks.GILDED_BLACKSTONE), null,
                    b(Blocks.POLISHED_BLACKSTONE_BRICKS), b(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS), b(Blocks.CHISELED_POLISHED_BLACKSTONE), b(Blocks.POLISHED_BASALT),
                    b(Blocks.CHISELED_POLISHED_BLACKSTONE), b(Blocks.BLACKSTONE), b(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS), b(Blocks.LANTERN), b(Blocks.MAGMA_BLOCK),
                    b(Blocks.IRON_BARS), b(Blocks.MAGMA_BLOCK), b(Blocks.ORANGE_WALL_BANNER));
            case ARCHIVE -> new Palette(b(Blocks.DARK_PRISMARINE), b(Blocks.PRISMARINE_BRICKS), b(Blocks.SEA_LANTERN), b(Blocks.CYAN_CARPET),
                    b(Blocks.PRISMARINE_BRICKS), b(Blocks.DARK_PRISMARINE), b(Blocks.DARK_OAK_PLANKS), b(Blocks.STRIPPED_DARK_OAK_WOOD), b(Blocks.DARK_OAK_PLANKS),
                    b(Blocks.DARK_PRISMARINE), b(Blocks.DARK_PRISMARINE_STAIRS), b(Blocks.SOUL_LANTERN), b(Blocks.SEA_LANTERN),
                    b(Blocks.IRON_BARS), b(Blocks.BOOKSHELF), b(Blocks.CYAN_WALL_BANNER));
            case CLOISTER -> new Palette(b(Blocks.POLISHED_DIORITE), b(Blocks.SNOW_BLOCK), b(Blocks.BLUE_ICE), b(Blocks.LIGHT_BLUE_CARPET),
                    b(Blocks.PACKED_ICE), b(Blocks.BLUE_ICE), b(Blocks.POLISHED_DIORITE), b(Blocks.CALCITE), b(Blocks.POLISHED_DIORITE),
                    b(Blocks.PACKED_ICE), b(Blocks.POLISHED_DIORITE_STAIRS), b(Blocks.SOUL_LANTERN), b(Blocks.SEA_LANTERN),
                    b(Blocks.IRON_BARS), b(Blocks.PACKED_ICE), b(Blocks.LIGHT_BLUE_WALL_BANNER));
            case GARDEN -> new Palette(b(Blocks.MOSS_BLOCK), b(Blocks.ROOTED_DIRT), b(Blocks.MUD_BRICKS), b(Blocks.MOSS_CARPET),
                    b(Blocks.MUD_BRICKS), b(Blocks.PACKED_MUD), b(Blocks.MANGROVE_PLANKS), b(Blocks.MANGROVE_LOG), b(Blocks.STRIPPED_MANGROVE_WOOD),
                    b(Blocks.MANGROVE_ROOTS), b(Blocks.MUD_BRICK_STAIRS), b(Blocks.LANTERN), b(Blocks.SHROOMLIGHT),
                    b(Blocks.MANGROVE_ROOTS), b(Blocks.SHROOMLIGHT), b(Blocks.GREEN_WALL_BANNER));
            case COURT -> new Palette(b(Blocks.SMOOTH_QUARTZ), b(Blocks.QUARTZ_BRICKS), b(Blocks.GOLD_BLOCK), b(Blocks.RED_CARPET),
                    b(Blocks.QUARTZ_BRICKS), b(Blocks.CHISELED_QUARTZ_BLOCK), b(Blocks.SMOOTH_QUARTZ), b(Blocks.QUARTZ_PILLAR), b(Blocks.CHISELED_QUARTZ_BLOCK),
                    b(Blocks.QUARTZ_BRICKS), b(Blocks.QUARTZ_STAIRS), b(Blocks.LANTERN), b(Blocks.GLOWSTONE),
                    b(Blocks.IRON_BARS), b(Blocks.GOLD_BLOCK), b(Blocks.YELLOW_WALL_BANNER));
            case RAMPARTS -> new Palette(b(Blocks.WAXED_OXIDIZED_CUT_COPPER), b(Blocks.WAXED_WEATHERED_CUT_COPPER), b(Blocks.WAXED_OXIDIZED_CHISELED_COPPER), null,
                    b(Blocks.DEEPSLATE_BRICKS), b(Blocks.WAXED_OXIDIZED_CUT_COPPER), b(Blocks.WAXED_OXIDIZED_COPPER), b(Blocks.WAXED_OXIDIZED_COPPER),
                    b(Blocks.WAXED_OXIDIZED_CHISELED_COPPER), b(Blocks.TINTED_GLASS), b(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS), b(Blocks.LANTERN),
                    b(Blocks.WAXED_COPPER_BULB).setValue(CopperBulbBlock.LIT, true), b(Blocks.IRON_BARS), b(Blocks.LIGHTNING_ROD), b(Blocks.LIGHT_GRAY_WALL_BANNER));
            case THRONE -> new Palette(b(Blocks.POLISHED_BLACKSTONE_BRICKS), b(Blocks.RED_NETHER_BRICKS), b(Blocks.CRYING_OBSIDIAN), b(Blocks.RED_CARPET),
                    b(Blocks.RED_NETHER_BRICKS), b(Blocks.NETHER_BRICKS), b(Blocks.POLISHED_BLACKSTONE), b(Blocks.STRIPPED_CRIMSON_HYPHAE),
                    b(Blocks.CHISELED_POLISHED_BLACKSTONE), b(Blocks.NETHER_BRICKS), b(Blocks.RED_NETHER_BRICK_STAIRS), b(Blocks.LANTERN), b(Blocks.SHROOMLIGHT),
                    b(Blocks.RED_STAINED_GLASS), b(Blocks.SOUL_CAMPFIRE), b(Blocks.RED_WALL_BANNER));
        };
        return spire ? p.spire() : p;
    }

    private static BlockState b(Block block) {
        return block.defaultBlockState();
    }

    // ================================================================ building

    /**
     * A hall being built a slice at a time: each {@link #step} writes up to {@link #BUDGET} blocks (without
     * neighbour updates) and the first step clears the box of whatever was left in it. Done when {@link #done()}.
     */
    public static final class Job {
        final ServerLevel level;
        public final Layout layout;
        final Palette pal;
        final boolean spire;
        final int x0, x1, y0, y1, z0, z1;
        private int x, z;
        private boolean started;
        private boolean done;

        Job(ServerLevel level, Layout layout, Palette pal, boolean spire) {
            this.level = level;
            this.layout = layout;
            this.pal = pal;
            this.spire = spire;
            AABB box = layout.box();
            x0 = Mth.floor(box.minX);
            x1 = Mth.floor(box.maxX) - 1;
            y0 = Mth.floor(box.minY);
            y1 = Mth.floor(box.maxY) - 1;
            z0 = Mth.floor(box.minZ);
            z1 = Mth.floor(box.maxZ) - 1;
            x = x0;
            z = z0;
        }

        public boolean done() {
            return done;
        }

        /** Writes the next slice; true once the hall is finished. */
        public boolean step(int budget) {
            if (done) return true;
            if (!started) {
                started = true;
                for (Entity e : level.getEntitiesOfClass(Entity.class, layout.box(), e -> !(e instanceof Player))) e.discard();
            }
            BlockPos o = layout.origin();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            int written = 0;
            int column = y1 - y0 + 1;
            while (x <= x1) {
                while (z <= z1) {
                    for (int y = y0; y <= y1; y++) {
                        BlockState s = blockAt(layout.shape(), pal, spire, x - o.getX(), y - o.getY(), z - o.getZ());
                        p.set(x, y, z);
                        if (level.getBlockState(p) != s) level.setBlock(p, s, 2 | 16);
                    }
                    written += column;
                    z++;
                    if (written >= budget) return false;
                }
                z = z0;
                x++;
            }
            done = true;
            return true;
        }

        /** Builds the rest at once. */
        public void finish() {
            while (!step(Integer.MAX_VALUE)) {
                // keep going
            }
        }
    }

    public static Job job(ServerLevel level, Layout layout, TowerFloors.Wing wing, boolean spire) {
        return new Job(level, layout, palette(wing, spire), spire);
    }

    /** Builds a hall at once (commands and tests). */
    public static Layout build(ServerLevel level, Shape shape, BlockPos origin, TowerFloors.Wing wing, boolean spire) {
        Layout l = layout(shape, origin);
        job(level, l, wing, spire).finish();
        return l;
    }

    /** Takes the seal off the stair door. */
    public static void openDoor(ServerLevel level, Layout l) {
        setDoor(level, l, AIR);
    }

    public static void sealDoor(ServerLevel level, Layout l) {
        setDoor(level, l, BARRIER);
    }

    private static void setDoor(ServerLevel level, Layout l, BlockState state) {
        if (l.door() == null) return;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy < 4; dy++) level.setBlock(l.door().offset(dx, dy, 0), state, 2 | 16);
        }
    }

    public static boolean doorSealed(ServerLevel level, Layout l) {
        return l.door() != null && level.getBlockState(l.door().above()).is(Blocks.BARRIER);
    }

    // ================================================================ the halls, block by block

    /** The block at an offset from a hall's origin (its floor's middle, at standing height). */
    static BlockState blockAt(Shape shape, Palette pal, boolean spire, int dx, int dy, int dz) {
        return switch (shape) {
            case NAVE -> nave(pal, spire, dx, dy, dz);
            case ROTUNDA -> rotunda(pal, spire, dx, dy, dz);
            case THRONE -> throne(pal, spire, dx, dy, dz);
            case COLOSSEUM -> colosseum(dx, dy, dz);
        };
    }

    // ---------------------------------------------------------------- the nave

    private static boolean naveInside(int ax, int dy) {
        if (dy < 0 || ax > NAVE_W) return false;
        if (dy <= NAVE_WALL) return true;
        double u = ax / (NAVE_W + 0.5), v = (dy - NAVE_WALL) / (double) (NAVE_H - NAVE_WALL);
        return u * u + v * v < 1;
    }

    private static BlockState nave(Palette pal, boolean spire, int dx, int dy, int dz) {
        int ax = Math.abs(dx);
        BlockState c = corridor(pal, dx, dy, dz, NAVE_N, 0);
        if (c != null) return c;
        boolean inPlan = ax <= NAVE_W && dz >= NAVE_N && dz <= NAVE_S;
        if (inPlan && dy == -1) return floor(pal, dx, dz, NAVE_W, NAVE_N, NAVE_S, new int[]{-9, 15});
        if (inPlan && naveInside(ax, dy)) {
            // pillars, two rows
            if (ax == 8 && contains(NAVE_PILLARS_Z, dz)) {
                boolean top = !naveInside(ax, dy + 1);
                return dy == 0 || top || dy == NAVE_WALL ? pal.cap() : pal.pillar();
            }
            // banners on the pillars' inner faces, facing the middle
            if (ax == 7 && dy == 4 && contains(NAVE_PILLARS_Z, dz) && pal.banner() != null) {
                return pal.banner().setValue(WallBannerBlock.FACING, dx > 0 ? Direction.WEST : Direction.EAST);
            }
            // the lanterns, hung down the middle of the vault
            if (dx == 0 && (dz == -12 || dz == -4 || dz == 4 || dz == 12)) {
                if (dy >= 9) return Blocks.CHAIN.defaultBlockState();
                if (dy == 8) return pal.lantern().setValue(LanternBlock.HANGING, true);
            }
            // under each window: something against the wall
            if (ax == NAVE_W && dy == 0 && contains(NAVE_WINDOWS_Z, dz)) return oriented(pal.niche(), dx > 0 ? Direction.WEST : Direction.EAST);
            if (dy == 0 && pal.carpet() != null && ax <= 1 && dz > NAVE_N && dz < NAVE_S - 1) return pal.carpet();
            if (spire && dy == 0 && ax == NAVE_W && Math.floorMod(dz, 4) == 2) {
                return Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP);
            }
            // hidden light: a grid under the vault, and along the walls
            if (dy == 7 && (dx == 0 || ax == 6) && Math.floorMod(dz, 6) == 3) return LIGHT;
            if (dy == 2 && ax == NAVE_W - 1 && Math.floorMod(dz, 6) == 0) return LIGHT_SOFT;
            return AIR;
        }
        // the shell
        if (ax <= NAVE_W + 2 && dz >= NAVE_N - 2 && dz <= NAVE_S + 2 && dy >= -3 && dy <= NAVE_H + 2) {
            // the way in, barred behind them
            if (dz == NAVE_S + 1 && ax <= 1 && dy >= 0 && dy <= 3) return bars(true);
            if (dz == NAVE_S + 2 && ax <= 1 && dy >= 0 && dy <= 3) return AIR;
            // barred windows onto the sky
            if (dy >= 2 && dy <= 4 && contains(NAVE_WINDOWS_Z, dz)) {
                if (ax == NAVE_W + 1) return bars(false);
                if (ax == NAVE_W + 2) return AIR;
            }
            if (dy >= 0 && dy <= NAVE_WALL && ax == NAVE_W + 1 && dz > NAVE_N && dz < NAVE_S) {
                if (dy == 0) return pal.trim();
                if (dy == NAVE_WALL) return pal.trim();
                if (dy == 3 && Math.floorMod(dz, 6) == 4) return pal.glow();
            }
            return shellBlock(pal, dx, dy, dz);
        }
        return AIR;
    }

    // ---------------------------------------------------------------- the rotunda

    private static boolean rotundaInside(double d, int dy) {
        if (dy < 0 || d > ROT_R + 0.5) return false;
        if (dy <= ROT_WALL) return true;
        double u = d / (ROT_R + 0.5), v = (dy - ROT_WALL) / (double) (ROT_H - ROT_WALL);
        return u * u + v * v < 1;
    }

    private static BlockState rotunda(Palette pal, boolean spire, int dx, int dy, int dz) {
        BlockState c = corridor(pal, dx, dy, dz, -ROT_R, 0);
        if (c != null) return c;
        double d = Math.sqrt(dx * dx + dz * dz);
        int ax = Math.abs(dx);
        if (d <= ROT_R + 0.5 && dy == -1) {
            // the exit sigil's inlay
            if (Math.abs(dx + 10) <= 1 && Math.abs(dz - 10) <= 1) return dx == -10 && dz == 10 ? pal.glow() : pal.inlay();
            if (d < 2.2) return pal.glow();
            if (d < 3.6 && (ax == Math.abs(dz) || dx == 0 || dz == 0)) return pal.inlay();
            if (Math.abs(d - 6.5) < 0.55 || Math.abs(d - ROT_PILLAR_R) < 0.55 || Math.abs(d - (ROT_R - 0.5)) < 0.6) return pal.inlay();
            if (d > 7 && d < ROT_PILLAR_R - 1 && (dx == 0 || dz == 0 || ax == Math.abs(dz))) return pal.trim();
            return ((Math.floorDiv(dx, 2) + Math.floorDiv(dz, 2)) & 1) == 0 ? pal.floor() : pal.floorAlt();
        }
        if (rotundaInside(d, dy)) {
            // a ring of pillars
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 4 * i + Math.PI / 8;
                if (dx == (int) Math.round(Math.cos(a) * ROT_PILLAR_R) && dz == (int) Math.round(Math.sin(a) * ROT_PILLAR_R)) {
                    boolean top = !rotundaInside(d, dy + 1);
                    if (top && dy > 6) return pal.glow();
                    return dy == 0 || top || dy == ROT_WALL ? pal.cap() : pal.pillar();
                }
            }
            // a chandelier over the middle, lanterns on chains around it
            if (dx == 0 && dz == 0 && dy >= 10) return Blocks.CHAIN.defaultBlockState();
            if (dx == 0 && dz == 0 && dy == 9) return pal.lantern().setValue(LanternBlock.HANGING, true);
            if ((ax == 7 && dz == 0 || dx == 0 && Math.abs(dz) == 7)) {
                if (dy >= 11) return Blocks.CHAIN.defaultBlockState();
                if (dy == 10) return pal.lantern().setValue(LanternBlock.HANGING, true);
            }
            if (spire && dy == 0 && d > ROT_R - 1 && d <= ROT_R + 0.5 && Math.floorMod(dx * 7 + dz * 13, 9) == 0) {
                return Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP);
            }
            if (dy == 7 && Math.floorMod(dx, 5) == 0 && Math.floorMod(dz, 5) == 0 && d < 14) return LIGHT;
            if (dy == 2 && d > 15 && d < 16.5 && Math.floorMod(dx + dz, 5) == 0) return LIGHT_SOFT;
            return AIR;
        }
        if (d <= ROT_R + 2.5 && dy >= -3 && dy <= ROT_H + 2) {
            // the way in, barred
            if (dz > 0 && ax <= 1 && dy >= 0 && dy <= 3) {
                if (dz == ROT_R + 1) return bars(true);
                if (dz == ROT_R + 2) return AIR;
            }
            // eight windows between the pillars
            if (dy >= 1 && dy <= 3 && d > ROT_R + 0.5) {
                double ang = Math.atan2(dz, dx);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    if (i == 2 || i == 6) continue; // not over the doors
                    double diff = Math.abs(Mth.wrapDegrees(Math.toDegrees(ang - a)));
                    if (diff < 3.5) {
                        if (d <= ROT_R + 1.5) return bars(Math.abs(Math.sin(a)) > Math.abs(Math.cos(a)));
                        return AIR;
                    }
                }
            }
            if (dy == 0 && d <= ROT_R + 1.5) return pal.trim();
            if (dy == ROT_WALL && d <= ROT_R + 1.5) return pal.trim();
            return shellBlock(pal, dx, dy, dz);
        }
        return AIR;
    }

    // ---------------------------------------------------------------- the throne hall

    private static boolean throneInside(int ax, int dy) {
        if (dy < 0 || ax > THR_W) return false;
        if (dy <= THR_WALL) return true;
        double u = ax / (THR_W + 0.5), v = (dy - THR_WALL) / (double) (THR_H - THR_WALL);
        return u * u + v * v < 1;
    }

    private static BlockState throne(Palette pal, boolean spire, int dx, int dy, int dz) {
        int ax = Math.abs(dx);
        BlockState c = corridor(pal, dx, dy, dz, THR_N, THR_DAIS);
        if (c != null) return c;
        boolean inPlan = ax <= THR_W && dz >= THR_N && dz <= THR_S;
        if (inPlan && dy == -1) return floor(pal, dx, dz, THR_W, THR_N, THR_S, new int[]{-11, 17});
        if (inPlan && throneInside(ax, dy)) {
            // the dais: a step, then the platform the throne stands on
            int dais = dz <= -17 ? 2 : dz <= -15 ? 1 : 0;
            if (dy < dais && ax <= 10) {
                if (dy == dais - 1 && (dz == -15 || dz == -17) ) return pal.trim();
                return dy == dais - 1 ? pal.floor() : pal.wall();
            }
            if (dy < dais && ax <= 12) return dy == dais - 1 ? pal.inlay() : pal.wall();
            // the throne
            if (dz == -20 && ax <= 1 && dy >= THR_DAIS && dy <= THR_DAIS + 4) return dy == THR_DAIS + 4 ? pal.glow() : pal.cap();
            if (dz == -20 && dx == 0 && dy == THR_DAIS + 5) return pal.glow();
            if (dz == -19 && dx == 0 && dy == THR_DAIS) return pal.stairs().setValue(StairBlock.FACING, Direction.NORTH);
            if (dz == -19 && ax == 1 && dy == THR_DAIS) return pal.cap();
            if (dz == -19 && ax == 1 && dy == THR_DAIS + 1) return Blocks.GILDED_BLACKSTONE.defaultBlockState();
            // braziers on the dais
            if (dz == -18 && ax == 9 && dy == THR_DAIS) return Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, false);
            // pillars
            if (ax == 9 && contains(THR_PILLARS_Z, dz)) {
                boolean top = !throneInside(ax, dy + 1);
                return dy == 0 || top || dy == THR_WALL ? pal.cap() : pal.pillar();
            }
            if (ax == 8 && dy == 5 && contains(THR_PILLARS_Z, dz) && pal.banner() != null) {
                return pal.banner().setValue(WallBannerBlock.FACING, dx > 0 ? Direction.WEST : Direction.EAST);
            }
            if (dx == 0 && (dz == -8 || dz == 2 || dz == 12)) {
                if (dy >= 11) return Blocks.CHAIN.defaultBlockState();
                if (dy == 10) return pal.lantern().setValue(LanternBlock.HANGING, true);
            }
            if (ax == THR_W && dy == 0 && contains(THR_WINDOWS_Z, dz)) return oriented(pal.niche(), dx > 0 ? Direction.WEST : Direction.EAST);
            if (dy == dais && pal.carpet() != null && ax <= 1 && dz > -19 && dz < THR_S - 1) return pal.carpet();
            if (spire && dy == 0 && ax == THR_W && Math.floorMod(dz, 4) == 2) {
                return Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP);
            }
            if (dy == 9 && (dx == 0 || ax == 7) && Math.floorMod(dz, 6) == 3) return LIGHT;
            if (dy == 3 && ax == THR_W - 1 && Math.floorMod(dz, 6) == 0) return LIGHT_SOFT;
            return AIR;
        }
        if (ax <= THR_W + 2 && dz >= THR_N - 2 && dz <= THR_S + 2 && dy >= -3 && dy <= THR_H + 2) {
            if (dz == THR_S + 1 && ax <= 1 && dy >= 0 && dy <= 3) return bars(true);
            if (dz == THR_S + 2 && ax <= 1 && dy >= 0 && dy <= 3) return AIR;
            // tall windows of blood-red glass
            if (dy >= 3 && dy <= 6 && contains(THR_WINDOWS_Z, dz)) {
                if (ax == THR_W + 1) return pal.window();
                if (ax == THR_W + 2) return AIR;
            }
            if (dy >= 0 && dy <= THR_WALL && ax == THR_W + 1 && dz > THR_N && dz < THR_S) {
                if (dy == 0 || dy == THR_WALL) return pal.trim();
            }
            return shellBlock(pal, dx, dy, dz);
        }
        return AIR;
    }

    // ---------------------------------------------------------------- the colosseum

    private static BlockState colosseum(int dx, int dy, int dz) {
        double d = Math.sqrt(dx * dx + dz * dz);
        int ax = Math.abs(dx);
        BlockState floor = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState floorAlt = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        BlockState red = Blocks.RED_NETHER_BRICKS.defaultBlockState();
        BlockState wall = Blocks.NETHER_BRICKS.defaultBlockState();
        BlockState trim = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
        if (d <= COL_R + 0.5) {
            if (dy < -1) return wall;
            if (dy == -1) {
                if (d < 2.2) return Blocks.CRYING_OBSIDIAN.defaultBlockState();
                if (Math.abs(d - 6) < 0.55 || Math.abs(d - 12.5) < 0.55 || Math.abs(d - (COL_R - 1)) < 0.6) return red;
                if (Math.abs(dx) <= 1 && Math.abs(dz - 11) <= 1) return dx == 0 && dz == 11 ? Blocks.SHROOMLIGHT.defaultBlockState() : trim;
                if (d > 6.5 && d < 12 && (dx == 0 || dz == 0)) return trim;
                return ((Math.floorDiv(dx, 2) + Math.floorDiv(dz, 2)) & 1) == 0 ? floor : floorAlt;
            }
            if (dy == 5 && Math.abs(d - 15) < 0.6 && Math.floorMod(dx + dz, 4) == 0) return LIGHT;
            if (dy == 8 && Math.floorMod(dx, 6) == 0 && Math.floorMod(dz, 6) == 0 && d < 13) return LIGHT;
            return AIR;
        }
        if (d <= COL_R + 2.5) {
            if (dy > 6) return AIR;
            if (dy < -1) return wall;
            // the gate they came in by, barred
            if (dz > 0 && ax <= 1 && dy >= 0 && dy <= 3) return d <= COL_R + 1.5 ? bars(true) : AIR;
            if (dy == 6) {
                // crenellations, and braziers along the top
                double ang = Math.toDegrees(Math.atan2(dz, dx));
                if (d <= COL_R + 1.5 && Math.floorMod((int) Math.round(ang), 45) <= 1) return Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, false);
                return d > COL_R + 1.5 && Math.floorMod((int) Math.round(ang / 6), 2) == 0 ? red : AIR;
            }
            if (dy == 4 && d <= COL_R + 1.5) {
                double ang = Math.toDegrees(Math.atan2(dz, dx));
                if (Math.floorMod((int) Math.round(ang) + 15, 30) <= 1 && !(dz > 0 && ax <= 3)) return Blocks.SHROOMLIGHT.defaultBlockState();
            }
            if (dy == 0 || dy == 5) return trim;
            return dy % 2 == 0 ? red : wall;
        }
        if (d <= COL_R + 10.5) {
            // tiers of seats climbing away from the wall
            int tier = (int) ((d - COL_R - 2.5) / 2);
            int top = 6 + 2 * tier;
            if (dy > top) return AIR;
            if (dy < -1) return wall;
            if (dy == top) return tier % 2 == 0 ? Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState() : Blocks.RED_NETHER_BRICK_SLAB.defaultBlockState();
            return dy >= top - 1 ? (tier % 2 == 0 ? floor : red) : wall;
        }
        return AIR;
    }

    // ---------------------------------------------------------------- shared parts

    /**
     * The stair out of a hall whose northmost row inside is {@code north}, from height {@code base}: through the shell
     * (sealed in its first row), then {@link #STEPS} steps up to a landing, walled and roofed. Null where it isn't.
     */
    @Nullable
    private static BlockState corridor(Palette pal, int dx, int dy, int dz, int north, int base) {
        int ax = Math.abs(dx);
        if (dz > north - 1 || ax > 2) return null;
        int door = north - 1;
        int firstStep = north - 3;
        int landing = firstStep - STEPS;
        if (dz >= north - 2) {
            // through the shell: a doorway 3 wide and 4 high, sealed in its first row
            if (ax <= 1 && dy >= base && dy < base + 4) return dz == door ? BARRIER : AIR;
            if (ax == 2 && dy >= base && dy < base + 4) return pal.trim();
            if (ax <= 1 && dy == base + 4) return pal.cap();
            return null;
        }
        if (dz < landing - 2) return null;
        int i = firstStep - dz; // 0 at the first step
        int floorTop = i < STEPS ? base + i : base + STEPS; // where you stand
        int roof = floorTop + 5;
        if (dz == landing - 2) return dy >= -3 && dy <= roof ? pal.wall() : AIR;
        if (ax == 2) return dy >= -3 && dy <= roof ? (dy == floorTop + 4 || dy == floorTop ? pal.trim() : pal.wall()) : AIR;
        if (dy < -3) return AIR;
        if (dy == roof) return pal.ceiling();
        if (dy > roof) return AIR;
        if (i < STEPS) {
            if (dy == base + i) return pal.stairs().setValue(StairBlock.FACING, Direction.NORTH);
            if (dy < base + i) return pal.wall();
        } else {
            if (dy == floorTop - 1) return dx == 0 && dz == landing ? pal.glow() : pal.inlay();
            if (dy < floorTop - 1) return pal.wall();
        }
        if (dx == 0 && dy == floorTop + 3 && Math.floorMod(dz, 2) == 0) return LIGHT_SOFT;
        return AIR;
    }

    /** A hall's floor: a checkerboard of two stones, an inlay border, the runner's edges and the exit sigil's inlay. */
    private static BlockState floor(Palette pal, int dx, int dz, int halfW, int north, int south, int[] exit) {
        int ax = Math.abs(dx);
        if (Math.abs(dx - exit[0]) <= 1 && Math.abs(dz - exit[1]) <= 1) return dx == exit[0] && dz == exit[1] ? pal.glow() : pal.inlay();
        if (ax == halfW || dz == north || dz == south) return pal.inlay();
        if (ax == 2) return pal.trim();
        if (ax <= 1) return pal.floorAlt();
        return ((Math.floorDiv(dx, 2) + Math.floorDiv(dz, 2)) & 1) == 0 ? pal.floor() : pal.floorAlt();
    }

    /** The shell's stone: bands of the second stone every few rows, the vault in its own. */
    private static BlockState shellBlock(Palette pal, int dx, int dy, int dz) {
        if (dy < 0) return pal.wall();
        if (dy > 9) return pal.ceiling();
        if (Math.floorMod(dy, 4) == 3) return pal.wallAlt();
        return pal.wall();
    }

    private static BlockState bars(boolean alongX) {
        BlockState s = Blocks.IRON_BARS.defaultBlockState();
        return alongX ? s.setValue(CrossCollisionBlock.EAST, true).setValue(CrossCollisionBlock.WEST, true)
                : s.setValue(CrossCollisionBlock.NORTH, true).setValue(CrossCollisionBlock.SOUTH, true);
    }

    /** Turns a block that hangs on or faces from a wall the right way (skulls, banners, campfires...). */
    static BlockState oriented(BlockState s, Direction facing) {
        if (s.hasProperty(WallSkullBlock.FACING)) return s.setValue(WallSkullBlock.FACING, facing);
        if (s.hasProperty(CampfireBlock.SIGNAL_FIRE)) return s.setValue(CampfireBlock.SIGNAL_FIRE, false);
        if (s.hasProperty(net.minecraft.world.level.block.BarrelBlock.FACING)) return s.setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.UP);
        if (s.hasProperty(AmethystClusterBlock.FACING)) return s.setValue(AmethystClusterBlock.FACING, Direction.UP);
        if (s.hasProperty(net.minecraft.world.level.block.LightningRodBlock.FACING)) return s.setValue(net.minecraft.world.level.block.LightningRodBlock.FACING, Direction.UP);
        return s;
    }

    private static boolean contains(int[] values, int v) {
        for (int x : values) if (x == v) return true;
        return false;
    }

    private CitadelHalls() {}
}
