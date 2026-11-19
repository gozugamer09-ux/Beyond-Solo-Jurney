package com.beyondsolo.home;

import com.beyondsolo.home.HomeWorld.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds a hunter's home ({@link HomeWorld.Layout}) a piece at a time once the ground for each piece is loaded:
 * <ul>
 *   <li><b>The floor</b> at the top of the tower first, so the hunter can come home at once: the roof it stands on, then
 *   each part they own (the Studio; the Family Home's west wing; the Training Hall's east wing; the Penthouse's gallery,
 *   terrace and the steps up), the doorways between them, and what stands in them (the training dummies, the weapons on
 *   the hall's wall, the trophy pedestals).</li>
 *   <li><b>The city</b> round it afterwards, a chunk at a time: its streets and lamps, squares and parks, and towers of
 *   lit windows at night, the hunter's own the tallest of them.</li>
 * </ul>
 * The city and tower are laid once for a plot ({@link #VERSION}); the floor up to the tier its owner has, a new tier's
 * part added when they buy it. Everything is laid by the builder itself, so the floor builds the same in the game-test
 * overworld (there without a city or tower under it).
 */
final class HomeBuilder {
    private static final TicketType<ChunkPos> TICKET = TicketType.create("beyondsolo_residence", Comparator.comparingLong(ChunkPos::toLong), 20 * 30);
    static final int FLAGS = 2 | 16;
    /** Bumped when the city's or the tower's look changes: a plot built by an older version gets them again. */
    static final int VERSION = 1;
    /** How far out the city reaches round a home. */
    static final int CITY = 168;
    /** The streets' grid: a road every {@code BLOCK} blocks, {@code ROAD} wide, with {@code WALK} of pavement each side. */
    static final int BLOCK = 72, ROAD = 8, WALK = 3;

    enum Step { FLOOR, CITY, DONE }

    private final HomeWorld.Layout l;
    private final HomeTier from, to;
    private final boolean city;
    private final List<ChunkPos> chunks = new ArrayList<>();
    private final List<Tower> towers;
    private int index;
    private Step step = Step.FLOOR;

    /**
     * Lays the parts of tiers above {@code from} up to {@code to} (from NONE: all of them, the roof first), then the city
     * round the plot if {@code city}.
     */
    HomeBuilder(HomeWorld.Layout layout, HomeTier from, HomeTier to, boolean city) {
        this.l = layout;
        this.from = from;
        this.to = to;
        this.city = city && !layout.small();
        // nothing of the floor to lay (the city alone, or the home is already all built): straight to the city
        if (from == to) this.step = this.city ? Step.CITY : Step.DONE;
        this.towers = this.city ? towers(layout) : List.of();
        if (this.city) {
            int cx = l.origin().getX(), cz = l.origin().getZ();
            for (int x = (cx - CITY) >> 4; x <= (cx + CITY) >> 4; x++) {
                for (int z = (cz - CITY) >> 4; z <= (cz + CITY) >> 4; z++) chunks.add(new ChunkPos(x, z));
            }
            // nearest first: the hunter's own tower and the streets round it come before the far skyline
            chunks.sort(Comparator.comparingDouble(c -> sq(c.getMiddleBlockX() - cx) + sq(c.getMiddleBlockZ() - cz)));
        }
    }

    HomeWorld.Layout layout() {
        return l;
    }

    HomeTier to() {
        return to;
    }

    boolean city() {
        return city;
    }

    /** The floor is laid (the hunter can come in); the city may still be going up. */
    boolean floorDone() {
        return step != Step.FLOOR;
    }

    boolean done() {
        return step == Step.DONE;
    }

    /** How far along the whole build is, 0..1. */
    float progress() {
        if (step == Step.DONE) return 1f;
        if (step == Step.FLOOR) return 0f;
        return chunks.isEmpty() ? 1f : index / (float) chunks.size();
    }

    /** A share of the work, if the ground for it is loaded. True once everything stands. */
    boolean tick(ServerLevel level) {
        if (step == Step.FLOOR) {
            if (!ready(level, l.origin(), HomeWorld.HALF_X + 6, false)) return false;
            floor(level);
            step = city ? Step.CITY : Step.DONE;
            return done();
        }
        for (int i = 0; i < 3 && step == Step.CITY; i++) {
            if (!cityWork(level, false)) break;
        }
        return done();
    }

    /** Everything now, loading whatever ground is missing (tests, commands). */
    void finish(ServerLevel level) {
        if (step == Step.FLOOR) {
            ready(level, l.origin(), HomeWorld.HALF_X + 6, true);
            floor(level);
            step = city ? Step.CITY : Step.DONE;
        }
        for (int guard = 0; guard < 100_000 && step == Step.CITY; guard++) cityWork(level, true);
    }

    private boolean cityWork(ServerLevel level, boolean now) {
        if (index >= chunks.size()) {
            step = Step.DONE;
            return true;
        }
        ChunkPos c = chunks.get(index);
        if (!ready(level, c, now)) return false;
        cityChunk(level, c);
        index++;
        if (index >= chunks.size()) step = Step.DONE;
        return true;
    }

    private static boolean ready(ServerLevel level, ChunkPos c, boolean now) {
        if (now) {
            level.getChunk(c.x, c.z);
            return true;
        }
        if (level.getChunkSource().getChunkNow(c.x, c.z) != null) return true;
        level.getChunkSource().addRegionTicket(TICKET, c, 1, c);
        return false;
    }

    /** True once every chunk within {@code radius} blocks of {@code at} is loaded; asks for them if not. */
    static boolean ready(ServerLevel level, BlockPos at, int radius, boolean now) {
        int x0 = (at.getX() - radius) >> 4, x1 = (at.getX() + radius) >> 4;
        int z0 = (at.getZ() - radius) >> 4, z1 = (at.getZ() + radius) >> 4;
        boolean all = true;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (now) level.getChunk(x, z);
                else if (level.getChunkSource().getChunkNow(x, z) == null) all = false;
            }
        }
        if (!all) {
            ChunkPos cp = new ChunkPos(at);
            level.getChunkSource().addRegionTicket(TICKET, cp, Math.max(1, (radius >> 4) + 1), cp);
        }
        return all;
    }

    // ================================================================ the floor

    private void floor(ServerLevel level) {
        if (from == HomeTier.NONE) plate(level);
        for (HomeTier t : HomeTier.values()) {
            if (t == HomeTier.NONE || !t.atLeast(HomeTier.of(from.ordinal() + 1)) || !to.atLeast(t)) continue;
            switch (t) {
                case STUDIO -> studio(level);
                case FAMILY -> family(level);
                case HALL -> hall(level);
                case PENTHOUSE -> penthouse(level);
                default -> {}
            }
        }
        openings(level);
        fixtures(level);
    }

    /** The roof of the tower the home stands on, with a low wall round its edge where no room stands yet. */
    private void plate(ServerLevel level) {
        int hx = HomeWorld.HALF_X, hz = HomeWorld.HALF_Z;
        // what stood in an older build goes with it: its dummies, the frames on its walls, anything dropped there
        AABB box = new AABB(l.at(-hx, 0, -hz).getCenter(), l.at(hx, 15, hz).getCenter()).inflate(1);
        for (Entity e : level.getEntitiesOfClass(Entity.class, box,
                e -> e instanceof TrainingDummy || e instanceof ItemFrame || e instanceof ItemEntity)) e.discard();
        fill(level, -hx, 0, -hz, hx, 0, hz, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
        // a clear roof over it (what an older build left there is gone)
        fill(level, -hx, 1, -hz, hx, 14, hz, Blocks.AIR.defaultBlockState());
        for (int x = -hx; x <= hx; x++) {
            for (int z = -hz; z <= hz; z++) {
                if (Math.abs(x) == hx || Math.abs(z) == hz) set(level, x, 1, z, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
            }
        }
        // the roof's plant: units and vents where the wings will stand
        for (int sx : new int[]{-1, 1}) {
            fill(level, sx * 20, 1, -4, sx * 18, 2, -2, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
            fill(level, sx * 20, 3, -4, sx * 18, 3, -2, Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.BOTTOM));
            set(level, sx * 14, 1, 5, Blocks.IRON_BLOCK.defaultBlockState());
            set(level, sx * 14, 2, 5, Blocks.IRON_BARS.defaultBlockState());
        }
    }

    // ---------------------------------------------------------------- the Studio

    private void studio(ServerLevel level) {
        Room r = Room.STUDIO;
        shell(level, r, Blocks.WHITE_CONCRETE.defaultBlockState(), Blocks.DARK_OAK_PLANKS.defaultBlockState(), Blocks.SMOOTH_QUARTZ.defaultBlockState());
        // tiles in the foyer and the kitchen
        fill(level, -3, 0, 6, 3, 0, 9, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        fill(level, -8, 0, -2, -6, 0, 5, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        // the window wall: the city at night across the whole north side
        glassWall(level, -8, 8, 1, 5, -10, new int[]{-4, 4});
        // lights in the ceiling
        for (int x : new int[]{-6, -2, 2, 6}) for (int z : new int[]{-7, -3, 1, 5}) set(level, x, 6, z, Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState());

        // the lift: its door, a frame of black stone, its light, the button
        fill(level, -1, 1, 10, 1, 3, 10, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        door(level, 0, 1, 10, Blocks.IRON_DOOR, Direction.NORTH);
        set(level, 0, 4, 10, Blocks.OCHRE_FROGLIGHT.defaultBlockState());
        set(level, -2, 2, 9, Blocks.POLISHED_BLACKSTONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.WALL)
                .setValue(ButtonBlock.FACING, Direction.NORTH));
        set(level, -3, 1, 9, Blocks.POTTED_BAMBOO.defaultBlockState());
        fill(level, -1, 1, 6, 1, 1, 8, Blocks.GRAY_CARPET.defaultBlockState());
        // where the steps up will go: a bench and a plant for now
        if (!to.atLeast(HomeTier.PENTHOUSE)) {
            set(level, 3, 1, 9, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
            set(level, 4, 1, 9, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
            set(level, 6, 1, 9, Blocks.POTTED_AZALEA.defaultBlockState());
        }

        // the living room: a long sofa facing the window, a low table, lamps, plants in the corners
        fill(level, -4, 1, -8, 4, 1, -4, Blocks.LIGHT_GRAY_CARPET.defaultBlockState());
        for (int x = -3; x <= 3; x++) set(level, x, 1, -3, stairs(Blocks.POLISHED_BLACKSTONE_STAIRS, Direction.SOUTH));
        set(level, -4, 1, -3, slab(Blocks.POLISHED_BLACKSTONE_SLAB, SlabType.BOTTOM));
        set(level, 4, 1, -3, slab(Blocks.POLISHED_BLACKSTONE_SLAB, SlabType.BOTTOM));
        fill(level, -1, 1, -6, 1, 1, -5, slab(Blocks.SMOOTH_STONE_SLAB, SlabType.BOTTOM));
        set(level, -5, 1, -3, Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, Direction.UP));
        set(level, 5, 1, -3, Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, Direction.UP));
        set(level, -8, 1, -9, Blocks.POTTED_AZALEA.defaultBlockState());
        set(level, 8, 1, -9, Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState());
        set(level, -8, 1, -7, Blocks.POTTED_FERN.defaultBlockState());

        // the kitchenette along the west wall: fridge, counters, smoker, furnace, sink, a worktable; an island
        fill(level, -8, 1, -2, -8, 2, -2, Blocks.IRON_BLOCK.defaultBlockState());
        set(level, -8, 1, -1, barrel(Direction.EAST));
        set(level, -8, 1, 0, facing(Blocks.SMOKER, Direction.EAST));
        set(level, -8, 1, 1, facing(Blocks.FURNACE, Direction.EAST));
        set(level, -8, 1, 2, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        set(level, -8, 1, 3, Blocks.CRAFTING_TABLE.defaultBlockState());
        set(level, -8, 1, 4, barrel(Direction.EAST));
        fill(level, -8, 3, -1, -8, 3, 4, trapdoor(Blocks.SPRUCE_TRAPDOOR, Direction.EAST, Half.TOP));
        fill(level, -5, 1, 0, -5, 1, 3, Blocks.QUARTZ_BRICKS.defaultBlockState());

        // the bed: a double, its head to the east wall, with a lamp on a nightstand each side
        bed(level, 8, 1, 2, Direction.EAST, Blocks.LIGHT_GRAY_BED);
        bed(level, 8, 1, 3, Direction.EAST, Blocks.LIGHT_GRAY_BED);
        set(level, 8, 1, 1, barrel(Direction.UP));
        set(level, 8, 1, 4, barrel(Direction.UP));
        set(level, 8, 2, 1, Blocks.LANTERN.defaultBlockState());
        set(level, 8, 2, 4, Blocks.LANTERN.defaultBlockState());
        fill(level, 5, 1, 1, 6, 1, 4, Blocks.LIGHT_BLUE_CARPET.defaultBlockState());
        set(level, 5, 1, 6, barrel(Direction.NORTH));
        set(level, 6, 1, 6, barrel(Direction.NORTH));
        set(level, 5, 2, 6, Blocks.POTTED_WHITE_TULIP.defaultBlockState());

        // the store: two big chests along the south wall, barrels stacked by the west wall
        chests(level, -8, 1, 9, Direction.NORTH);
        chests(level, -6, 1, 9, Direction.NORTH);
        for (int y = 1; y <= 2; y++) {
            set(level, -8, y, 7, barrel(Direction.EAST));
            set(level, -8, y, 8, barrel(Direction.EAST));
        }
    }

    // ---------------------------------------------------------------- the Family Home

    private void family(ServerLevel level) {
        Room r = Room.FAMILY;
        shell(level, r, Blocks.WHITE_CONCRETE.defaultBlockState(), Blocks.DARK_OAK_PLANKS.defaultBlockState(), Blocks.SMOOTH_QUARTZ.defaultBlockState());
        fill(level, -25, 0, -9, -23, 0, -1, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        glassWall(level, -25, -10, 1, 5, -10, new int[]{-22, -18, -14});
        // windows: the mother's room west and south, the study south
        fill(level, -26, 2, 3, -26, 4, 7, Blocks.GLASS.defaultBlockState());
        fill(level, -24, 2, 10, -20, 4, 10, Blocks.GLASS.defaultBlockState());
        fill(level, -15, 2, 10, -11, 4, 10, Blocks.GLASS.defaultBlockState());
        for (int x : new int[]{-23, -19, -15, -11}) for (int z : new int[]{-7, -3, 3, 7}) set(level, x, 6, z, Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState());

        // the walls inside: kitchen and dining room north, her room and the study south
        fill(level, -25, 1, 0, -10, 5, 0, Blocks.WHITE_CONCRETE.defaultBlockState());
        fill(level, -17, 1, 1, -17, 5, 9, Blocks.WHITE_CONCRETE.defaultBlockState());
        door(level, -21, 1, 0, Blocks.DARK_OAK_DOOR, Direction.SOUTH);
        door(level, -13, 1, 0, Blocks.DARK_OAK_DOOR, Direction.SOUTH);

        // the kitchen along the west wall, and its island
        fill(level, -25, 1, -9, -25, 2, -9, Blocks.IRON_BLOCK.defaultBlockState());
        set(level, -25, 1, -8, barrel(Direction.EAST));
        set(level, -25, 1, -7, facing(Blocks.SMOKER, Direction.EAST));
        set(level, -25, 1, -6, facing(Blocks.FURNACE, Direction.EAST));
        set(level, -25, 1, -5, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        set(level, -25, 1, -4, Blocks.CRAFTING_TABLE.defaultBlockState());
        set(level, -25, 1, -3, barrel(Direction.EAST));
        set(level, -25, 1, -2, barrel(Direction.EAST));
        fill(level, -25, 3, -8, -25, 3, -2, trapdoor(Blocks.SPRUCE_TRAPDOOR, Direction.EAST, Half.TOP));
        fill(level, -22, 1, -7, -22, 1, -4, Blocks.QUARTZ_BRICKS.defaultBlockState());

        // the dining table under two hanging lamps, chairs round it
        fill(level, -18, 1, -5, -13, 1, -4, trapdoor(Blocks.DARK_OAK_TRAPDOOR, Direction.NORTH, Half.TOP));
        for (int x : new int[]{-18, -16, -14}) set(level, x, 1, -6, stairs(Blocks.DARK_OAK_STAIRS, Direction.NORTH));
        for (int x : new int[]{-17, -15, -13}) set(level, x, 1, -3, stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH));
        for (int[] c : new int[][]{{-17, -4}, {-14, -5}}) {
            set(level, c[0], 5, c[1], Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
            set(level, c[0], 4, c[1], Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
        set(level, -10, 1, -9, Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState());
        set(level, -10, 1, -1, Blocks.POTTED_FERN.defaultBlockState());

        // her room: her bed, a lamp on the nightstand on its north side (she stands up on its south side), a rug, flowers
        bed(level, -25, 1, 5, Direction.WEST, Blocks.WHITE_BED);
        set(level, -25, 1, 4, barrel(Direction.UP));
        set(level, -25, 2, 4, Blocks.LANTERN.defaultBlockState());
        fill(level, -23, 1, 3, -21, 1, 7, Blocks.PINK_CARPET.defaultBlockState());
        set(level, -25, 1, 8, Blocks.POTTED_PINK_TULIP.defaultBlockState());
        set(level, -25, 1, 2, Blocks.POTTED_LILY_OF_THE_VALLEY.defaultBlockState());
        for (int y = 1; y <= 2; y++) {
            set(level, -19, y, 9, barrel(Direction.NORTH));
            set(level, -18, y, 9, barrel(Direction.NORTH));
        }

        // the study: the Association's desk (a lectern), a writing table with the broker's bell and a lamp, shelves
        fill(level, -14, 1, 2, -12, 1, 6, Blocks.BLUE_CARPET.defaultBlockState());
        set(level, -13, 1, 4, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.NORTH));
        set(level, -15, 1, 7, Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState());
        set(level, -14, 1, 7, Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState());
        set(level, -15, 2, 7, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR).setValue(BellBlock.FACING, Direction.NORTH));
        set(level, -14, 2, 7, Blocks.LANTERN.defaultBlockState());
        set(level, -15, 1, 6, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH));
        fill(level, -10, 1, 2, -10, 3, 8, Blocks.BOOKSHELF.defaultBlockState());
        set(level, -16, 1, 1, Blocks.POTTED_BAMBOO.defaultBlockState());
    }

    // ---------------------------------------------------------------- the Training Hall

    private void hall(ServerLevel level) {
        Room r = Room.HALL;
        shell(level, r, Blocks.WHITE_CONCRETE.defaultBlockState(), Blocks.POLISHED_ANDESITE.defaultBlockState(), Blocks.SMOOTH_QUARTZ.defaultBlockState());
        glassWall(level, 10, 25, 1, 9, -10, new int[]{13, 17, 21});
        fill(level, 11, 6, 10, 24, 8, 10, Blocks.GLASS.defaultBlockState());
        for (int x = 12; x <= 24; x += 4) for (int z : new int[]{-6, -2, 2, 6}) set(level, x, 10, z, Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState());
        // the hall is twice as tall: unseen light at half its height too, so its floor isn't left in the dark
        for (int x = 12; x <= 24; x += 4) for (int z : new int[]{-6, -2, 2, 6}) set(level, x, 5, z, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15));
        // the sparring ring in the floor
        for (int x = 10; x <= 25; x++) {
            for (int z = -9; z <= 9; z++) {
                double d = Math.sqrt(sq(x - 17.5) + sq(z));
                if (d >= 6.0 && d < 7.0) set(level, x, 0, z, Blocks.WHITE_CONCRETE.defaultBlockState());
            }
        }
        // the dummies' stands: a ring of dark stone under each
        for (int i = 0; i < HomeWorld.DUMMIES; i++) {
            int z = (i - 1) * 4;
            fill(level, 21, 0, z - 1, 23, 0, z + 1, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        }
        // benches by the door, targets for the bow in the far corner
        for (int z = 2; z <= 7; z++) set(level, 10, 1, z, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST));
        set(level, 25, 2, -8, Blocks.TARGET.defaultBlockState());
        set(level, 25, 3, -8, Blocks.TARGET.defaultBlockState());
        set(level, 25, 2, -6, Blocks.TARGET.defaultBlockState());
        set(level, 10, 1, -9, Blocks.POTTED_BAMBOO.defaultBlockState());
        set(level, 10, 1, 9, Blocks.POTTED_FERN.defaultBlockState());
    }

    // ---------------------------------------------------------------- the Penthouse

    private void penthouse(ServerLevel level) {
        // the gallery: a dark hall of trophies over the Studio, its own floor laid over the Studio's roof
        Room g = Room.GALLERY;
        shell(level, g, Blocks.WHITE_CONCRETE.defaultBlockState(), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        glassWall(level, -8, 8, 8, 12, -10, new int[]{-4, 4});
        fill(level, -1, 8, -7, 1, 8, 6, Blocks.RED_CARPET.defaultBlockState());
        // the seat at its head, looking down the hall: a high back of black stone with gold at its top
        set(level, 0, 8, -8, stairs(Blocks.POLISHED_BLACKSTONE_STAIRS, Direction.NORTH));
        set(level, -1, 8, -8, slab(Blocks.POLISHED_BLACKSTONE_SLAB, SlabType.TOP));
        set(level, 1, 8, -8, slab(Blocks.POLISHED_BLACKSTONE_SLAB, SlabType.TOP));
        fill(level, -1, 8, -9, 1, 8, -9, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
        set(level, 0, 9, -9, Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
        set(level, 0, 10, -9, Blocks.GILDED_BLACKSTONE.defaultBlockState());
        set(level, -1, 9, -9, stairs(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Direction.EAST));
        set(level, 1, 9, -9, stairs(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Direction.WEST));
        // a light over the seat and over every pedestal
        set(level, 0, 12, -8, Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, Direction.DOWN));
        for (int i = 0; i < HomeWorld.TROPHIES.length; i++) {
            BlockPos p = l.pedestal(i);
            set(level, l.rx(p), 12, l.rz(p), Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, Direction.DOWN));
        }
        for (int z : new int[]{-6, 0, 6}) set(level, 0, 13, z, Blocks.OCHRE_FROGLIGHT.defaultBlockState());

        // the steps up from the foyer, the stairwell through both floors and a glass rail round it
        fill(level, 2, 1, 8, 8, 5, 9, Blocks.AIR.defaultBlockState());
        for (int k = 0; k <= 6; k++) {
            for (int z = 8; z <= 9; z++) {
                set(level, 2 + k, 1 + k, z, stairs(Blocks.DARK_OAK_STAIRS, Direction.EAST));
                if (k > 0 && k < 6) fill(level, 2 + k, 1, z, 2 + k, k, z, Blocks.WHITE_CONCRETE.defaultBlockState());
            }
        }
        fill(level, 4, 6, 8, 6, 6, 9, Blocks.AIR.defaultBlockState());
        fill(level, 5, 7, 8, 7, 7, 9, Blocks.AIR.defaultBlockState());
        fill(level, 4, 8, 7, 7, 8, 7, Blocks.GLASS.defaultBlockState());
        fill(level, 4, 8, 8, 4, 8, 9, Blocks.GLASS.defaultBlockState());

        // the terrace on the west wing's roof: a deck, a lit pool, gardens along the rail, loungers, a cherry tree
        Room t = Room.TERRACE;
        fill(level, t.x0 + 1, 8, t.z0 + 1, t.x1 - 1, 12, t.z1 - 1, Blocks.AIR.defaultBlockState());
        fill(level, t.x0, 7, t.z0, t.x1 - 1, 7, t.z1, Blocks.SPRUCE_PLANKS.defaultBlockState());
        for (int x = t.x0; x < t.x1; x++) {
            for (int z = t.z0; z <= t.z1; z++) {
                if (x != t.x0 && z != t.z0 && z != t.z1) continue;
                set(level, x, 8, z, Blocks.WHITE_CONCRETE.defaultBlockState());
                set(level, x, 9, z, Blocks.GLASS.defaultBlockState());
            }
        }
        for (int[] c : new int[][]{{t.x0, t.z0}, {t.x0, t.z1}, {t.x1 - 1, t.z0}, {t.x1 - 1, t.z1}}) set(level, c[0], 9, c[1], Blocks.LANTERN.defaultBlockState());
        // the pool
        fill(level, -22, 8, -8, -16, 8, -4, Blocks.SMOOTH_QUARTZ.defaultBlockState());
        fill(level, -21, 7, -7, -17, 7, -5, Blocks.SEA_LANTERN.defaultBlockState());
        fill(level, -21, 8, -7, -17, 8, -5, Blocks.WATER.defaultBlockState());
        // the loungers facing it
        for (int x : new int[]{-21, -19, -17}) {
            set(level, x, 8, -1, stairs(Blocks.SMOOTH_QUARTZ_STAIRS, Direction.SOUTH));
            set(level, x, 8, -2, slab(Blocks.SMOOTH_QUARTZ_SLAB, SlabType.BOTTOM));
        }
        // gardens along the south and west rails
        RandomSource rnd = RandomSource.create(l.plot() * 31L + 7);
        Block[] flowers = {Blocks.POPPY, Blocks.DANDELION, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.ALLIUM,
                Blocks.PINK_TULIP, Blocks.WHITE_TULIP, Blocks.LILY_OF_THE_VALLEY, Blocks.AZALEA, Blocks.FLOWERING_AZALEA};
        for (int x = t.x0 + 1; x < t.x1 - 1; x++) planter(level, x, t.z1 - 1, flowers, rnd);
        for (int z = 0; z < t.z1 - 1; z++) planter(level, t.x0 + 1, z, flowers, rnd);
        // the cherry tree in the corner
        set(level, -23, 8, 6, Blocks.GRASS_BLOCK.defaultBlockState());
        for (int y = 9; y <= 11; y++) set(level, -23, y, 6, Blocks.CHERRY_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
        BlockState leaves = Blocks.CHERRY_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 11; dy <= 13; dy++) {
                    double d = sq(dx) + sq(dz) + sq((dy - 12) * 1.4);
                    if (d > 5.2 || (dx == 0 && dz == 0 && dy <= 11)) continue;
                    int x = -23 + dx, z = 6 + dz;
                    if (x <= t.x0 || x >= t.x1 || z <= t.z0 || z >= t.z1) continue;
                    if (level.getBlockState(l.at(x, dy, z)).isAir()) set(level, x, dy, z, leaves);
                }
            }
        }
    }

    private void planter(ServerLevel level, int x, int z, Block[] flowers, RandomSource rnd) {
        set(level, x, 8, z, Blocks.GRASS_BLOCK.defaultBlockState());
        if (rnd.nextFloat() < 0.8f) set(level, x, 9, z, flowers[rnd.nextInt(flowers.length)].defaultBlockState());
    }

    // ---------------------------------------------------------------- between the parts

    /** The doorways between the parts the hunter owns. */
    private void openings(ServerLevel level) {
        BlockState air = Blocks.AIR.defaultBlockState();
        if (to.atLeast(HomeTier.FAMILY)) fill(level, -9, 1, -6, -9, 3, -4, air);
        if (to.atLeast(HomeTier.HALL)) fill(level, 9, 1, -6, 9, 3, -4, air);
        if (to.atLeast(HomeTier.PENTHOUSE)) fill(level, -9, 8, -1, -9, 10, 1, air);
    }

    /** What stands in the parts: the lift's button, the training dummies, the weapons on the hall's wall, the trophy pedestals. */
    private void fixtures(ServerLevel level) {
        Homes.ensureFixtures(level, l, to);
    }

    // ---------------------------------------------------------------- pieces

    /** A part's walls, floor and ceiling, its inside cleared. */
    private void shell(ServerLevel level, Room r, BlockState wall, BlockState floor, BlockState ceiling) {
        fill(level, r.x0 + 1, r.y0 + 1, r.z0 + 1, r.x1 - 1, r.y1 - 1, r.z1 - 1, Blocks.AIR.defaultBlockState());
        fill(level, r.x0, r.y0, r.z0, r.x1, r.y0, r.z1, floor);
        fill(level, r.x0, r.y1, r.z0, r.x1, r.y1, r.z1, ceiling);
        for (int y = r.y0 + 1; y < r.y1; y++) {
            for (int x = r.x0; x <= r.x1; x++) {
                set(level, x, y, r.z0, wall);
                set(level, x, y, r.z1, wall);
            }
            for (int z = r.z0; z <= r.z1; z++) {
                set(level, r.x0, y, z, wall);
                set(level, r.x1, y, z, wall);
            }
        }
    }

    /** Glass from {@code x0} to {@code x1}, {@code y0} to {@code y1} in the wall at {@code z}, white mullions at {@code posts}. */
    private void glassWall(ServerLevel level, int x0, int x1, int y0, int y1, int z, int[] posts) {
        for (int x = x0; x <= x1; x++) {
            boolean post = false;
            for (int p : posts) post |= p == x;
            fill(level, x, y0, z, x, y1, z, post ? Blocks.WHITE_CONCRETE.defaultBlockState() : Blocks.GLASS.defaultBlockState());
        }
    }

    private void bed(ServerLevel level, int x, int y, int z, Direction facing, Block bed) {
        BlockState s = bed.defaultBlockState().setValue(BedBlock.FACING, facing);
        set(level, x - facing.getStepX(), y, z - facing.getStepZ(), s.setValue(BedBlock.PART, BedPart.FOOT));
        set(level, x, y, z, s.setValue(BedBlock.PART, BedPart.HEAD));
    }

    private void door(ServerLevel level, int x, int y, int z, Block door, Direction facing) {
        BlockState s = door.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)
                .setValue(DoorBlock.OPEN, false);
        set(level, x, y, z, s.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(level, x, y + 1, z, s.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** A big chest of two halves from x to x+1, facing {@code facing} (north: its left half is the west one). */
    private void chests(ServerLevel level, int x, int y, int z, Direction facing) {
        BlockState s = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing);
        set(level, x, y, z, s.setValue(ChestBlock.TYPE, ChestType.LEFT));
        set(level, x + 1, y, z, s.setValue(ChestBlock.TYPE, ChestType.RIGHT));
    }

    private static BlockState barrel(Direction facing) {
        return Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, facing);
    }

    private static BlockState facing(Block b, Direction facing) {
        return b.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
    }

    private static BlockState stairs(Block b, Direction facing) {
        return b.defaultBlockState().setValue(StairBlock.FACING, facing);
    }

    private static BlockState slab(Block b, SlabType type) {
        return b.defaultBlockState().setValue(SlabBlock.TYPE, type);
    }

    private static BlockState trapdoor(Block b, Direction facing, Half half) {
        return b.defaultBlockState().setValue(TrapDoorBlock.FACING, facing).setValue(TrapDoorBlock.HALF, half).setValue(TrapDoorBlock.OPEN, false);
    }

    private void set(ServerLevel level, int x, int y, int z, BlockState s) {
        level.setBlock(l.at(x, y, z), s, FLAGS);
    }

    private void fill(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos o = l.origin();
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) level.setBlock(p.set(o.getX() + x, o.getY() + y, o.getZ() + z), s, FLAGS);
            }
        }
    }

    // ================================================================ the city

    /**
     * A tower of the city: its footprint (world x/z, inclusive), its roof's height, its look and its own seed. The look:
     * 0 a curtain of blue glass, 1 pale stone with rows of windows, 2 dark stone and dark glass.
     */
    record Tower(int x0, int z0, int x1, int z1, int top, int look, long seed, boolean home) {
        boolean in(int x, int z) {
            return x >= x0 && x <= x1 && z >= z0 && z <= z1;
        }

        boolean edge(int x, int z) {
            return x == x0 || x == x1 || z == z0 || z == z1;
        }
    }

    /** The towers round a plot, the hunter's own first. Each city block holds one, or a square or a park. */
    static List<Tower> towers(HomeWorld.Layout l) {
        List<Tower> out = new ArrayList<>();
        int cx = l.origin().getX(), cz = l.origin().getZ();
        out.add(new Tower(cx - HomeWorld.HALF_X, cz - HomeWorld.HALF_Z, cx + HomeWorld.HALF_X, cz + HomeWorld.HALF_Z, l.origin().getY() - 1, 3,
                l.plot() * 977L + 13, true));
        RandomSource r = RandomSource.create(l.plot() * 0x9E3779B97F4A7C15L + 42);
        int reach = CITY / BLOCK + 1;
        for (int bx = -reach; bx <= reach; bx++) {
            for (int bz = -reach; bz <= reach; bz++) {
                if (bx == 0 && bz == 0) continue;
                long seed = r.nextLong();
                RandomSource b = RandomSource.create(seed);
                if (b.nextFloat() < 0.18f) continue;
                // the block's buildable middle (inside its pavements)
                int half = BLOCK / 2 - ROAD / 2 - WALK - 2;
                int mx = cx + bx * BLOCK, mz = cz + bz * BLOCK;
                int w = 14 + b.nextInt(14), d = 14 + b.nextInt(14);
                int x0 = mx - half + b.nextInt(Math.max(1, 2 * half - w)), z0 = mz - half + b.nextInt(Math.max(1, 2 * half - d));
                double far = Math.sqrt(bx * bx + bz * bz);
                int height = 26 + b.nextInt(52);
                if (far >= 2 && b.nextFloat() < 0.3f) height = 100 + b.nextInt(60);
                if (Math.abs(x0 + w / 2 - cx) > CITY || Math.abs(z0 + d / 2 - cz) > CITY) continue;
                out.add(new Tower(x0, z0, x0 + w, z0 + d, HomeWorld.GROUND_Y + height, b.nextInt(3), seed, false));
            }
        }
        return out;
    }

    /** One chunk of the city: every column of it, its ground and whatever stands on it. */
    private void cityChunk(ServerLevel level, ChunkPos c) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int cx = l.origin().getX(), cz = l.origin().getZ();
        List<Tower> here = new ArrayList<>();
        for (Tower t : towers) {
            if (t.x1 >= c.getMinBlockX() && t.x0 <= c.getMaxBlockX() && t.z1 >= c.getMinBlockZ() && t.z0 <= c.getMaxBlockZ()) here.add(t);
        }
        for (int x = c.getMinBlockX(); x <= c.getMaxBlockX(); x++) {
            for (int z = c.getMinBlockZ(); z <= c.getMaxBlockZ(); z++) {
                int dx = x - cx, dz = z - cz;
                if (Math.abs(dx) > CITY || Math.abs(dz) > CITY) continue;
                Tower t = null;
                for (Tower o : here) if (o.in(x, z)) t = o;
                if (t != null) {
                    tower(level, p, t, x, z);
                    continue;
                }
                ground(level, p, x, z, dx, dz);
            }
        }
        // the parks' trees, whole inside this chunk
        RandomSource r = RandomSource.create(l.plot() * 31_337L ^ c.toLong());
        for (int i = 0; i < 3; i++) {
            int x = c.getMinBlockX() + 3 + r.nextInt(10), z = c.getMinBlockZ() + 3 + r.nextInt(10);
            if (zone(x - cx, z - cz) != Zone.PARK || inAnyTower(x, z, 3)) continue;
            tree(level, p, x, z, r);
        }
    }

    private boolean inAnyTower(int x, int z, int margin) {
        for (Tower t : towers) if (x >= t.x0 - margin && x <= t.x1 + margin && z >= t.z0 - margin && z <= t.z1 + margin) return true;
        return false;
    }

    enum Zone { ROAD, WALK, SQUARE, PARK }

    /** What the ground is at a column (relative to the home): roads, their pavements, and each block a square or a park. */
    Zone zone(int dx, int dz) {
        int ax = Math.floorMod(dx + BLOCK / 2, BLOCK), az = Math.floorMod(dz + BLOCK / 2, BLOCK);
        // ax, az: where in its block (0..BLOCK-1) the column is, the road down the block's edge (0 .. ROAD/2 and the far end)
        int ex = Math.min(ax, BLOCK - 1 - ax), ez = Math.min(az, BLOCK - 1 - az);
        if (ex < ROAD / 2 || ez < ROAD / 2) return Zone.ROAD;
        if (ex < ROAD / 2 + WALK || ez < ROAD / 2 + WALK) return Zone.WALK;
        int bx = Math.floorDiv(dx + BLOCK / 2, BLOCK), bz = Math.floorDiv(dz + BLOCK / 2, BLOCK);
        if (bx == 0 && bz == 0) return Zone.SQUARE;
        long h = (bx * 73_856_093L) ^ (bz * 19_349_663L) ^ (l.plot() * 83_492_791L);
        return Math.floorMod(h, 3) == 0 ? Zone.PARK : Zone.SQUARE;
    }

    private void ground(ServerLevel level, BlockPos.MutableBlockPos p, int x, int z, int dx, int dz) {
        int y = HomeWorld.GROUND_Y;
        switch (zone(dx, dz)) {
            case ROAD -> {
                // a dashed line down the middle of each road
                int ax = Math.floorMod(dx + BLOCK / 2, BLOCK), az = Math.floorMod(dz + BLOCK / 2, BLOCK);
                boolean alongX = Math.min(az, BLOCK - 1 - az) < ROAD / 2, alongZ = Math.min(ax, BLOCK - 1 - ax) < ROAD / 2;
                if (alongX && !alongZ && (az == 0 || az == BLOCK - 1) && Math.floorMod(dx, 6) < 3) {
                    level.setBlock(p.set(x, y, z), Blocks.WHITE_CONCRETE.defaultBlockState(), FLAGS);
                } else if (alongZ && !alongX && (ax == 0 || ax == BLOCK - 1) && Math.floorMod(dz, 6) < 3) {
                    level.setBlock(p.set(x, y, z), Blocks.WHITE_CONCRETE.defaultBlockState(), FLAGS);
                }
            }
            case WALK -> {
                level.setBlock(p.set(x, y, z), Blocks.SMOOTH_STONE.defaultBlockState(), FLAGS);
                // a street lamp every so often along the pavement's kerb
                int ax = Math.floorMod(dx + BLOCK / 2, BLOCK), az = Math.floorMod(dz + BLOCK / 2, BLOCK);
                int ex = Math.min(ax, BLOCK - 1 - ax), ez = Math.min(az, BLOCK - 1 - az);
                boolean kerbX = ex == ROAD / 2 && ez >= ROAD / 2, kerbZ = ez == ROAD / 2 && ex >= ROAD / 2;
                if ((kerbX && Math.floorMod(dz, 12) == 0) || (kerbZ && Math.floorMod(dx, 12) == 0)) lamp(level, p, x, z);
            }
            case SQUARE -> {
                boolean light = Math.floorMod(dx, 4) < 2 ^ Math.floorMod(dz, 4) < 2;
                level.setBlock(p.set(x, y, z), (light ? Blocks.POLISHED_ANDESITE : Blocks.SMOOTH_STONE).defaultBlockState(), FLAGS);
                if (Math.floorMod(dx, 16) == 8 && Math.floorMod(dz, 16) == 8) lamp(level, p, x, z);
            }
            case PARK -> {
                level.setBlock(p.set(x, y, z), Blocks.GRASS_BLOCK.defaultBlockState(), FLAGS);
                if (Math.floorMod(dx, 7) == 3 && Math.floorMod(dz, 9) == 4) level.setBlock(p.set(x, y + 1, z), Blocks.SHORT_GRASS.defaultBlockState(), FLAGS);
            }
        }
    }

    private void lamp(ServerLevel level, BlockPos.MutableBlockPos p, int x, int z) {
        int y = HomeWorld.GROUND_Y;
        for (int k = 1; k <= 3; k++) level.setBlock(p.set(x, y + k, z), Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState(), FLAGS);
        level.setBlock(p.set(x, y + 4, z), Blocks.OCHRE_FROGLIGHT.defaultBlockState(), FLAGS);
    }

    private void tree(ServerLevel level, BlockPos.MutableBlockPos p, int x, int z, RandomSource r) {
        int y = HomeWorld.GROUND_Y;
        int h = 3 + r.nextInt(2);
        for (int k = 1; k <= h; k++) level.setBlock(p.set(x, y + k, z), Blocks.OAK_LOG.defaultBlockState(), FLAGS);
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = h - 1; dy <= h + 1; dy++) {
                    if (sq(dx) + sq(dz) + sq(dy - h) > 5 || (dx == 0 && dz == 0 && dy <= h)) continue;
                    if (level.getBlockState(p.set(x + dx, y + dy, z + dz)).isAir()) level.setBlock(p, leaves, FLAGS);
                }
            }
        }
    }

    /** One column of a tower: its face (windows, lit or dark, between its floors and posts) or its roof. */
    private void tower(ServerLevel level, BlockPos.MutableBlockPos p, Tower t, int x, int z) {
        int base = HomeWorld.GROUND_Y + 1;
        if (!t.edge(x, z)) {
            // inside: only the roof (the home's tower's roof is the home's own floor)
            if (t.home) return;
            level.setBlock(p.set(x, t.top, z), Blocks.GRAY_CONCRETE.defaultBlockState(), FLAGS);
            // a plant room on the roof
            int mx = (t.x0 + t.x1) / 2, mz = (t.z0 + t.z1) / 2;
            if (Math.abs(x - mx) <= 2 && Math.abs(z - mz) <= 2) {
                for (int k = 1; k <= 3; k++) level.setBlock(p.set(x, t.top + k, z), Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), FLAGS);
            }
            return;
        }
        boolean corner = (x == t.x0 || x == t.x1) && (z == t.z0 || z == t.z1);
        // the place along this face (posts every fourth column), and which face
        int u = (z == t.z0 || z == t.z1) ? x - t.x0 : z - t.z0;
        int face = z == t.z0 ? 0 : z == t.z1 ? 1 : x == t.x0 ? 2 : 3;
        BlockState frame, band, dark;
        switch (t.look) {
            case 0 -> {
                frame = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
                band = Blocks.GRAY_CONCRETE.defaultBlockState();
                dark = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
            }
            case 1 -> {
                frame = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
                band = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
                dark = Blocks.GRAY_STAINED_GLASS.defaultBlockState();
            }
            case 2 -> {
                frame = Blocks.BLACK_CONCRETE.defaultBlockState();
                band = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
                dark = Blocks.BLACK_STAINED_GLASS.defaultBlockState();
            }
            default -> {
                // the hunter's own: white bands, dark glass, and a ring of light under the home
                frame = Blocks.WHITE_CONCRETE.defaultBlockState();
                band = Blocks.WHITE_CONCRETE.defaultBlockState();
                dark = Blocks.BLACK_STAINED_GLASS.defaultBlockState();
            }
        }
        RandomSource hue = RandomSource.create(t.seed);
        BlockState[] lights = {Blocks.OCHRE_FROGLIGHT.defaultBlockState(), Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState(),
                Blocks.SEA_LANTERN.defaultBlockState()};
        BlockState lit = lights[hue.nextInt(lights.length)];
        BlockState lit2 = lights[hue.nextInt(lights.length)];
        float share = t.home ? 0.3f : 0.12f + hue.nextFloat() * 0.18f;
        int top = t.top;
        for (int y = base; y <= top; y++) {
            int k = y - base;
            BlockState s;
            if (corner || u % 4 == 0) s = frame;
            else if (k % 4 == 0 || y == top) s = band;
            else {
                long h = t.seed ^ ((long) face * 1_000_003L) ^ ((long) (u / 4) * 7_919L) ^ ((long) (k / 4) * 104_729L);
                float roll = (Math.floorMod(h * 0x5DEECE66DL + 11L, 1_000_003L)) / 1_000_003f;
                s = roll < share ? (roll < share * 0.3f ? lit2 : lit) : dark;
            }
            if (t.home && y >= top - 1) s = y == top ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.WHITE_CONCRETE.defaultBlockState();
            level.setBlock(p.set(x, y, z), s, FLAGS);
        }
        if (!t.home) {
            // the roof's parapet, and a warning light on each corner
            level.setBlock(p.set(x, top + 1, z), frame, FLAGS);
            if (corner) level.setBlock(p.set(x, top + 2, z), Blocks.SHROOMLIGHT.defaultBlockState(), FLAGS);
        }
    }

    private static double sq(double v) {
        return v * v;
    }
}
