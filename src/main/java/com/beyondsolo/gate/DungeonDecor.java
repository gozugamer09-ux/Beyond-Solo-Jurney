package com.beyondsolo.gate;

import com.beyondsolo.crafting.ManaVeins;
import com.beyondsolo.gate.DungeonLayout.Kind;
import com.beyondsolo.gate.DungeonLayout.Room;
import com.beyondsolo.gate.DungeonLayout.Shape;
import com.beyondsolo.gate.DungeonLayout.Space;
import com.beyondsolo.gate.DungeonLayout.SpaceShape;
import com.beyondsolo.gate.DungeonLayout.Style;
import com.beyondsolo.gate.DungeonLayout.Tunnel;
import com.beyondsolo.gate.GateRealm.Decor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dresses a dungeon once {@link DungeonBuilder} has cut it: lights set into the walls and a soft fill light, what the
 * realm scatters on its floors, its decorations ({@link Decor}), the boss's arena, the caches in side rooms, mana
 * veins, and the lights and timbers of the passages. Things only go into air (a light or a vein may take the place of
 * a wall block), and nothing that stands in the way goes where a passage runs through a room, where players arrive,
 * or where the boss waits.
 */
final class DungeonDecor {
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final int FLAGS = DungeonBuilder.FLAGS;

    private final DungeonBuilder b;
    private final GateInstance gate;
    private final DungeonLayout layout;
    private final GateRealm realm;
    private final boolean trial;
    private final int ox, oz;
    /** Points along every passage's walking line (world x, z and about the feet's height). */
    private final List<int[]> paths = new ArrayList<>();
    /** Where players arrive and where the boss waits (world x, z and floor height): both kept clear. */
    private final int[] arrival, lair;

    /** Somewhere to dress: a room, or the floor of an open space (world x and z). */
    private record Area(int cx, int cz, int rx, int rz, int floor, int high, boolean box, @Nullable Room room, @Nullable Space space) {
        int reach() {
            return Math.max(rx, rz);
        }
    }

    DungeonDecor(DungeonBuilder b) {
        this.b = b;
        this.gate = b.gate;
        this.layout = b.layout;
        this.realm = b.realm;
        this.trial = b.theme == GateTheme.TRIAL;
        this.ox = gate.origin().getX();
        this.oz = gate.origin().getZ();
        for (Tunnel t : layout.tunnels()) {
            int n = Math.max(2, (int) Math.ceil(t.length()));
            for (int i = 0; i <= n; i++) {
                double[] at = DungeonLayout.along(t, i / (double) n);
                int feet = Mth.floor(at[1]) - (t.style() == Style.TUBE ? 2 : 0);
                paths.add(new int[]{b.wx(at[0]), b.wz(at[2]), feet});
            }
        }
        Room e = layout.entrance(), boss = layout.boss();
        this.arrival = new int[]{e.center().getX() + ox, e.center().getZ() + oz, e.floorY()};
        this.lair = new int[]{boss.center().getX() + ox, boss.center().getZ() + oz, boss.floorY()};
    }

    // ================================================================== rooms

    void room(ServerLevel level, Room room) {
        RandomSource r = new LegacyRandomSource(gate.seed() * 31 + room.index());
        BlockPos c = gate.world(room.center());
        int floor = room.floorY();
        Area a = new Area(c.getX(), c.getZ(), room.rx(), room.rz(), floor, room.topY() - floor, room.shape() == Shape.HALL, room, null);
        Kind kind = room.kind();

        lights(level, room, c, floor, r);
        fillLight(level, room, c, floor);
        if (kind == Kind.SIDE) cache(level, c, floor, r);
        if (!trial && (kind == Kind.CAVERN || kind == Kind.SIDE || kind == Kind.HIDDEN)) veins(level, room, c, floor, r);
        if (trial) {
            trial(level, room, c, floor, r);
        } else if (kind == Kind.BOSS) {
            arena(level, room, c, floor, r);
        }
        scatter(level, a, r, Math.round((10 + r.nextInt(8)) * size(a)));
        // a hidden chamber is dressed for what it holds (depths.DepthsBuild): none of the realm's things stand in it
        if (kind == Kind.HIDDEN) return;
        decorations(level, a, r, switch (kind) {
            case BOSS -> 0.6f;
            case PASSAGE -> 0.55f;
            case ENTRANCE -> 0.8f;
            default -> 1f;
        });
    }

    /** Lights set into the walls: sconces along a hall's walls, round a round hall, lamps on an island, else here and there. */
    private void lights(ServerLevel level, Room room, BlockPos c, int floor, RandomSource r) {
        switch (room.shape()) {
            case HALL -> {
                int y = floor + Math.min(3, Math.max(2, room.topY() - floor - 3));
                for (Direction d : HORIZONTAL) {
                    boolean xWall = d.getAxis() == Direction.Axis.X;
                    int reach = xWall ? room.rx() : room.rz(), run = xWall ? room.rz() : room.rx();
                    Direction along = xWall ? Direction.SOUTH : Direction.EAST;
                    for (int k = -run + 2; k <= run - 2; k += 5) {
                        BlockPos wall = c.atY(y).relative(d, reach + 1).relative(along, k);
                        if (level.getBlockState(wall.relative(d.getOpposite())).isAir()) wallLight(level, wall);
                    }
                }
                roofLights(level, room, c, floor);
            }
            case ROUND -> {
                int n = Math.max(6, Math.round(Mth.TWO_PI * (room.rx() + 1) / 5f));
                for (int i = 0; i < n; i++) {
                    float ang = i * Mth.TWO_PI / n;
                    BlockPos hit = march(level, new BlockPos(c.getX(), floor + 3, c.getZ()), Mth.cos(ang), 0, Mth.sin(ang), room.rx() + 4);
                    if (hit != null) wallLight(level, hit);
                }
                roofLights(level, room, c, floor);
            }
            case ISLAND -> islandLamps(level, room, c, floor, r);
            default -> {
                int lights = room.kind() == Kind.BOSS ? 18 : 6 + room.reach() / 2 + r.nextInt(4);
                for (int i = 0; i < lights; i++) {
                    float ang = r.nextFloat() * Mth.TWO_PI;
                    float up = room.kind() == Kind.BOSS ? (i % 3) * 0.35f : r.nextFloat() * 0.8f;
                    BlockPos from = new BlockPos(c.getX(), floor + 1 + Math.round(up * room.ry()), c.getZ());
                    BlockPos hit = march(level, from, Mth.cos(ang), 0, Mth.sin(ang), room.reach() + 6);
                    if (hit != null) wallLight(level, hit);
                }
            }
        }
    }

    /** Built rooms without chandeliers get lights set into the roof, in a loose grid. */
    private void roofLights(ServerLevel level, Room room, BlockPos c, int floor) {
        if (realm.has(Decor.CHAINS) || room.reach() < 6) return;
        for (int dx = -room.rx() + 3; dx <= room.rx() - 3; dx += 6) {
            for (int dz = -room.rz() + 3; dz <= room.rz() - 3; dz += 6) {
                if (room.shape() == Shape.ROUND && dx * dx + dz * dz > (room.rx() - 2) * (room.rx() - 2)) continue;
                BlockPos hit = march(level, new BlockPos(c.getX() + dx, floor + 2, c.getZ() + dz), 0, 1, 0, room.ry() * 2 + 4);
                if (hit != null) wallLight(level, hit);
            }
        }
    }

    /** Islands have nothing to set a light into: lamps stand round their rim instead. */
    private void islandLamps(ServerLevel level, Room room, BlockPos c, int floor, RandomSource r) {
        int n = 3 + room.rx() / 3;
        float turn = r.nextFloat() * Mth.TWO_PI;
        for (int i = 0; i < n; i++) {
            float ang = turn + i * Mth.TWO_PI / n;
            BlockPos p = new BlockPos(c.getX() + Math.round(Mth.cos(ang) * (room.rx() - 1.4f)), floor, c.getZ() + Math.round(Mth.sin(ang) * (room.rz() - 1.4f)));
            if (!level.getBlockState(p).isAir() || !standable(level, p.below()) || !open(p, 2.0)) continue;
            if (realm.built()) {
                b.place(level, p, b.railing());
                b.place(level, p.above(), realm.lantern);
            } else {
                b.place(level, p, realm.spike);
                b.place(level, p.above(), realm.light);
            }
        }
    }

    /** Soft light from nowhere, so no corner is pitch black (never so bright it melts the realm's snow). */
    private void fillLight(ServerLevel level, Room room, BlockPos c, int floor) {
        int lvl = room.kind() == Kind.BOSS ? 12 : 9;
        if (snowy()) lvl = Math.min(lvl, 11);
        BlockState fill = Blocks.LIGHT.defaultBlockState().setValue(BlockStateProperties.LEVEL, lvl);
        fill(level, new BlockPos(c.getX(), floor + 3, c.getZ()), fill);
        int ring = 4 + room.reach() / 5;
        for (int i = 0; i < ring; i++) {
            float ang = i * Mth.TWO_PI / ring + 0.6f;
            fill(level, new BlockPos(c.getX() + Math.round(Mth.cos(ang) * room.rx() * 0.55f), floor + 2, c.getZ() + Math.round(Mth.sin(ang) * room.rz() * 0.55f)), fill);
        }
    }

    /** A side room's cache: a chest on a plinth of the realm's accent. */
    private void cache(ServerLevel level, BlockPos c, int floor, RandomSource r) {
        BlockPos at = new BlockPos(c.getX(), floor, c.getZ());
        level.setBlock(at.below(), realm.accent(r), FLAGS);
        level.setBlock(at, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.Plane.HORIZONTAL.getRandomDirection(r)), FLAGS);
        if (level.getBlockEntity(at) instanceof RandomizableContainerBlockEntity chest) chest.setLootTable(sideLoot(), r.nextLong());
    }

    private ResourceKey<LootTable> sideLoot() {
        return switch (b.theme) {
            case E, D, TRIAL -> BuiltInLootTables.SIMPLE_DUNGEON;
            case C -> BuiltInLootTables.ABANDONED_MINESHAFT;
            case B -> BuiltInLootTables.ANCIENT_CITY;
            case A -> BuiltInLootTables.BASTION_OTHER;
            case S -> ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.withDefaultNamespace("chests/bastion_treasure"));
        };
    }

    /** Mana veins in the walls of caverns and side rooms, and plenty in a hidden chamber. */
    private void veins(ServerLevel level, Room room, BlockPos c, int floor, RandomSource r) {
        int veins = room.kind() == Kind.HIDDEN ? 3 + r.nextInt(2) : 1 + r.nextInt(room.kind() == Kind.SIDE ? 3 : 2);
        for (int i = 0; i < veins; i++) {
            float ang = r.nextFloat() * Mth.TWO_PI;
            BlockPos hit = march(level, new BlockPos(c.getX(), floor + 1 + r.nextInt(3), c.getZ()), Mth.cos(ang), 0, Mth.sin(ang), room.reach() + 6);
            if (hit != null && rock(level, hit)) ManaVeins.place(level, hit, gate.rank(), r, FLAGS);
        }
    }

    // ------------------------------------------------------------------ the boss's arena

    private void arena(ServerLevel level, Room room, BlockPos c, int floor, RandomSource r) {
        switch (room.shape()) {
            case HALL -> {
                // two rows of pillars down the hall, floor to roof
                boolean alongX = room.rx() >= room.rz();
                int along = alongX ? room.rx() : room.rz(), across = alongX ? room.rz() : room.rx();
                int off = Math.max(4, Math.round(across * 0.62f));
                for (int s = -1; s <= 1; s += 2) {
                    for (int k = -(along - 3); k <= along - 3; k += 5) {
                        BlockPos p = new BlockPos(c.getX() + (alongX ? k : s * off), floor, c.getZ() + (alongX ? s * off : k));
                        if (airy(level.getBlockState(p)) && clearOfPaths(p, 2.5)) column(level, p, 40);
                    }
                }
            }
            case ROUND -> {
                for (int i = 0; i < 8; i++) {
                    float ang = i * Mth.TWO_PI / 8 + 0.2f;
                    BlockPos p = new BlockPos(c.getX() + Math.round(Mth.cos(ang) * room.rx() * 0.72f), floor, c.getZ() + Math.round(Mth.sin(ang) * room.rz() * 0.72f));
                    if (airy(level.getBlockState(p)) && clearOfPaths(p, 2.5)) column(level, p, 40);
                }
            }
            default -> {
                // a ring of standing stones round the arena, each crowned with light
                for (int i = 0; i < 8; i++) {
                    float ang = i * Mth.TWO_PI / 8 + 0.2f;
                    int px = c.getX() + Math.round(Mth.cos(ang) * room.rx() * 0.72f);
                    int pz = c.getZ() + Math.round(Mth.sin(ang) * room.rz() * 0.72f);
                    if (!clearOfPaths(new BlockPos(px, floor, pz), 2.0)) continue;
                    int h = 5 + r.nextInt(3);
                    for (int k = 0; k < h; k++) {
                        BlockState s = k == h - 1 ? realm.light : (k % 3 == 2 ? realm.accent(r) : realm.spike);
                        level.setBlock(new BlockPos(px, floor + k, pz), s, FLAGS);
                    }
                }
            }
        }
        if (realm.has(Decor.BRAZIERS)) {
            BlockState fire = (realm.has(Decor.SOUL_FIRE) ? Blocks.SOUL_CAMPFIRE : Blocks.CAMPFIRE).defaultBlockState();
            for (int i = 0; i < 6; i++) {
                float ang = i * Mth.TWO_PI / 6 + 0.55f;
                BlockPos p = new BlockPos(c.getX() + Math.round(Mth.cos(ang) * room.rx() * 0.84f), floor, c.getZ() + Math.round(Mth.sin(ang) * room.rz() * 0.84f));
                if (!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir() || !standable(level, p.below()) || !open(p, 2.5)) continue;
                b.place(level, p, b.railing());
                b.place(level, p.above(), fire);
            }
        }
        if (realm.has(Decor.THRONE) && (room.shape() == Shape.HALL || room.shape() == Shape.ROUND)) throne(level, room, c, floor, r);
    }

    /**
     * A throne on a dais at the far end from the way in, facing it: steps up, a seat between two lamp posts, a high
     * back crowned with light, banners on the wall behind and a carpet running down to it.
     */
    private void throne(ServerLevel level, Room room, BlockPos c, int floor, RandomSource r) {
        Tunnel in = layout.bossTunnel();
        double dx = 1, dz = 0;
        if (in != null) {
            BlockPos door = in.end();
            if (Math.abs(door.getX() - room.center().getX()) + Math.abs(door.getZ() - room.center().getZ()) < 3) door = in.start();
            dx = door.getX() - room.center().getX();
            dz = door.getZ() - room.center().getZ();
        }
        Direction toDoor = Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? Direction.EAST : Direction.WEST) : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
        Direction back = toDoor.getOpposite(), side = back.getClockWise();
        int reach = back.getAxis() == Direction.Axis.X ? room.rx() : room.rz();
        BlockPos seat = new BlockPos(c.getX(), floor, c.getZ()).relative(back, Math.max(4, reach - 2));
        for (int s = -2; s <= 2; s++) {
            for (int d = 0; d <= 1; d++) b.place(level, seat.relative(side, s).relative(back, d), realm.deck);
            b.place(level, seat.relative(side, s).relative(toDoor), b.stairs(back));
        }
        BlockPos chair = seat.above();
        b.place(level, chair, b.stairs(back));
        BlockPos rest = chair.relative(back);
        b.place(level, rest, realm.accent(r));
        b.place(level, rest.above(), realm.accent(r));
        b.place(level, rest.above(2), realm.light);
        for (int s = -1; s <= 1; s += 2) {
            b.place(level, chair.relative(side, s), b.slab());
            BlockPos post = chair.relative(side, 2 * s);
            b.place(level, post, b.railing());
            b.place(level, post.above(), realm.lantern);
            if (realm.banner != null) {
                BlockPos banner = chair.relative(back, 2).relative(side, 2 * s).above(2);
                if (level.getBlockState(banner).isAir() && rock(level, banner.relative(back))) {
                    level.setBlock(banner, realm.banner.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, toDoor), FLAGS);
                }
            }
        }
        if (realm.carpet != null && realm.has(Decor.CARPET)) {
            BlockState carpet = realm.carpet.defaultBlockState();
            for (int k = 2; k <= reach * 2; k++) {
                BlockPos p = seat.relative(toDoor, k);
                if (!room.contains(p.getX() - ox, p.getY(), p.getZ() - oz, 1.0)) break;
                for (int s = -1; s <= 1; s++) {
                    BlockPos q = p.relative(side, s);
                    if (level.getBlockState(q).isAir() && standable(level, q.below())) level.setBlock(q, carpet, FLAGS);
                }
            }
        }
    }

    /**
     * The awakening trial: chains hang from every roof, and the last hall is the Arbiter's: a smooth dark floor
     * inlaid with two rings of gold round where it kneels, broken columns of white stone round the edge (some snapped
     * off, some reaching the roof), and soul fire in braziers between them.
     */
    private void trial(ServerLevel level, Room room, BlockPos c, int floor, RandomSource r) {
        int chains = room.kind() == Kind.BOSS ? 14 : 3 + r.nextInt(3);
        for (int i = 0; i < chains; i++) {
            float a = r.nextFloat() * Mth.TWO_PI, d = 0.25f + r.nextFloat() * 0.6f;
            BlockPos top = march(level, new BlockPos(c.getX() + Math.round(Mth.cos(a) * d * room.rx()), floor + 3, c.getZ() + Math.round(Mth.sin(a) * d * room.rz())), 0, 1, 0, room.ry() * 2 + 4);
            if (top == null) continue;
            int h = 2 + r.nextInt(room.kind() == Kind.BOSS ? 8 : 4);
            for (int k = 1; k <= h; k++) {
                BlockPos p = top.below(k);
                if (p.getY() <= floor + 4) break;
                placeIfAir(level, p, k == h && r.nextBoolean() ? Blocks.SOUL_LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true) : Blocks.CHAIN.defaultBlockState());
            }
        }
        if (room.kind() != Kind.BOSS) return;

        // the floor: dark polished stone, two gold rings and gold spokes
        int rx = room.rx(), rz = room.rz();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = -rx; x <= rx; x++) {
            for (int z = -rz; z <= rz; z++) {
                float d = Mth.sqrt((x * x) / (float) (rx * rx) + (z * z) / (float) (rz * rz));
                if (d > 0.93f) continue;
                p.set(c.getX() + x, floor - 1, c.getZ() + z);
                if (level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir()) continue;
                float ring1 = Math.abs(d - 0.3f), ring2 = Math.abs(d - 0.62f);
                double ang = Math.atan2(z, x);
                boolean spoke = d > 0.3f && d < 0.62f && Math.abs(Math.sin(ang * 4)) < 0.06;
                BlockState s = ring1 < 0.022f || ring2 < 0.018f || spoke ? Blocks.GOLD_BLOCK.defaultBlockState()
                        : d < 0.3f ? Blocks.POLISHED_BLACKSTONE.defaultBlockState()
                        : (Math.floorMod(x + z, 2) == 0 ? Blocks.POLISHED_DEEPSLATE : Blocks.DEEPSLATE_TILES).defaultBlockState();
                level.setBlock(p, s, FLAGS);
            }
        }
        // broken white columns, chained to each other
        int columns = 10;
        for (int i = 0; i < columns; i++) {
            float a = i * Mth.TWO_PI / columns + 0.15f;
            int px = c.getX() + Math.round(Mth.cos(a) * rx * 0.8f);
            int pz = c.getZ() + Math.round(Mth.sin(a) * rz * 0.8f);
            boolean whole = i % 3 == 0;
            int h = whole ? room.ry() * 2 + 4 : 4 + r.nextInt(7);
            for (int k = 0; k < h; k++) {
                BlockPos at = new BlockPos(px, floor + k, pz);
                BlockState here = level.getBlockState(at);
                if (k > 2 && !here.isAir() && !here.is(Blocks.LIGHT)) break;
                BlockState s = k == 0 || k % 6 == 5 ? Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState() : Blocks.QUARTZ_PILLAR.defaultBlockState();
                level.setBlock(at, s, FLAGS);
                if (!whole && k == h - 1) {
                    // a snapped top: a few chips round its foot
                    for (Direction dir : Direction.Plane.HORIZONTAL) if (r.nextInt(3) == 0) placeIfAir(level, at.relative(dir).below(k), Blocks.CALCITE.defaultBlockState());
                }
            }
            // a brazier of soul fire between this column and the next
            float bz = a + Mth.PI / columns;
            BlockPos br = new BlockPos(c.getX() + Math.round(Mth.cos(bz) * rx * 0.74f), floor, c.getZ() + Math.round(Mth.sin(bz) * rz * 0.74f));
            if (level.getBlockState(br).isAir()) {
                level.setBlock(br, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState(), FLAGS);
                b.join(br);
                placeIfAir(level, br.above(), Blocks.SOUL_CAMPFIRE.defaultBlockState());
            }
        }
        BlockState fill = Blocks.LIGHT.defaultBlockState().setValue(BlockStateProperties.LEVEL, 11);
        for (int i = 0; i < 6; i++) {
            float a = i * Mth.TWO_PI / 6;
            placeIfAir(level, new BlockPos(c.getX() + Math.round(Mth.cos(a) * rx * 0.45f), floor + 4, c.getZ() + Math.round(Mth.sin(a) * rz * 0.45f)), fill);
        }
    }

    // ================================================================== open spaces

    void space(ServerLevel level, Space s) {
        RandomSource r = new LegacyRandomSource(gate.seed() * 43 + layout.spaces().indexOf(s) * 977L);
        switch (s.shape()) {
            case VOID -> chasm(level, s, r);
            case SHAFT -> shaft(level, s, r);
            case HOLLOW -> hollow(level, s, r);
            default -> {}
        }
    }

    /** A chasm: lights round its walls, stalactites hanging from its roof, a faint glow over the void. */
    private void chasm(ServerLevel level, Space s, RandomSource r) {
        int cx = s.center().getX() + ox, cz = s.center().getZ() + oz;
        int lights = 24 + s.rx();
        for (int i = 0; i < lights; i++) {
            float ang = r.nextFloat() * Mth.TWO_PI;
            int y = s.floorY() + 8 + r.nextInt(Math.max(1, s.ry()));
            BlockPos from = new BlockPos(cx + Math.round(Mth.cos(ang) * s.rx() * 0.75f), y, cz + Math.round(Mth.sin(ang) * s.rz() * 0.75f));
            if (!level.getBlockState(from).isAir()) continue;
            BlockPos hit = march(level, from, Mth.cos(ang), 0, Mth.sin(ang), s.rx() / 2 + 12);
            if (hit != null) wallLight(level, hit);
        }
        int spikes = 16 + s.rx() / 2;
        for (int i = 0; i < spikes; i++) {
            float ang = r.nextFloat() * Mth.TWO_PI, d = Mth.sqrt(r.nextFloat()) * 0.85f;
            BlockPos from = new BlockPos(cx + Math.round(Mth.cos(ang) * d * s.rx()), s.center().getY() + 6, cz + Math.round(Mth.sin(ang) * d * s.rz()));
            if (!level.getBlockState(from).isAir()) continue;
            BlockPos top = march(level, from, 0, 1, 0, s.ry() + 10);
            if (top == null) continue;
            int h = 2 + r.nextInt(6);
            int k = 1;
            for (; k <= h; k++) {
                BlockPos p = top.below(k);
                if (p.getY() <= from.getY()) break;
                b.place(level, p, realm.spike);
            }
            if (realm.has(Decor.END_RODS) && r.nextInt(3) == 0) b.place(level, top.below(k), rod(Direction.DOWN));
        }
        BlockState glow = Blocks.LIGHT.defaultBlockState().setValue(BlockStateProperties.LEVEL, 9);
        int y = DungeonLayout.BASE_Y + 3;
        for (int x = cx - s.rx(); x <= cx + s.rx(); x += 16) {
            for (int z = cz - s.rz(); z <= cz + s.rz(); z += 16) {
                if (s.contains(x - ox, y, z - oz, 0.9) && !b.inRoom(x - ox, y, z - oz, 1.2)) fill(level, new BlockPos(x, y, z), glow);
            }
        }
    }

    /** A shaft: lights up its walls between the turns of the ramp, chains with lanterns hanging down the middle. */
    private void shaft(ServerLevel level, Space s, RandomSource r) {
        int cx = s.center().getX() + ox, cz = s.center().getZ() + oz;
        for (int y = s.floorY() + 6; y <= s.topY() - 4; y += 6) {
            float turn = r.nextFloat() * Mth.TWO_PI;
            for (int k = 0; k < 3; k++) {
                float ang = turn + k * Mth.TWO_PI / 3;
                BlockPos hit = march(level, new BlockPos(cx, y, cz), Mth.cos(ang), 0, Mth.sin(ang), s.rx() + 6);
                if (hit != null) wallLight(level, hit);
            }
        }
        BlockState lamp = hanging(realm.lantern);
        int chains = 3 + r.nextInt(3);
        for (int i = 0; i < chains; i++) {
            float ang = r.nextFloat() * Mth.TWO_PI, d = 0.25f + r.nextFloat() * 0.4f;
            BlockPos from = new BlockPos(cx + Math.round(Mth.cos(ang) * d * s.rx()), s.topY() - 2, cz + Math.round(Mth.sin(ang) * d * s.rz()));
            if (!level.getBlockState(from).isAir()) continue;
            BlockPos top = march(level, from, 0, 1, 0, 20);
            if (top == null) continue;
            int len = 4 + r.nextInt(10);
            for (int k = 1; k <= len; k++) b.place(level, top.below(k), Blocks.CHAIN.defaultBlockState());
            b.place(level, top.below(len + 1), lamp);
        }
    }

    /** A hollow: great columns of rock, lights round its walls and over its floor, stalactites, the realm's dressing. */
    private void hollow(ServerLevel level, Space s, RandomSource r) {
        int cx = s.center().getX() + ox, cz = s.center().getZ() + oz;
        Area a = new Area(cx, cz, s.rx(), s.rz(), s.floorY(), s.ry(), false, null, s);
        columns(level, s, a, r);
        int lights = 30 + s.rx();
        for (int i = 0; i < lights; i++) {
            BlockPos g = spot(level, a, r, 0.72f, 0.95f);
            if (g == null) continue;
            BlockPos from = g.above(2 + r.nextInt(8));
            if (!level.getBlockState(from).isAir()) continue;
            float ang = (float) Math.atan2(g.getZ() - cz, g.getX() - cx);
            BlockPos hit = march(level, from, Mth.cos(ang), 0, Mth.sin(ang), 24);
            if (hit != null) wallLight(level, hit);
        }
        BlockState glow = Blocks.LIGHT.defaultBlockState().setValue(BlockStateProperties.LEVEL, 10);
        for (int x = cx - s.rx() + 6; x <= cx + s.rx(); x += 12) {
            for (int z = cz - s.rz() + 6; z <= cz + s.rz(); z += 12) {
                int[] span = b.span(s, x, z);
                if (span == null) continue;
                int y = span[0] + 4;
                if (y < span[1] && !b.inRoom(x - ox, y, z - oz, 1.1)) fill(level, new BlockPos(x, y, z), glow);
            }
        }
        hanging(level, a, r, 18 + s.rx() / 2, realm.spike, 7);
        scatter(level, a, r, s.rx() * s.rz() / 14);
        decorations(level, a, r, 1f);
    }

    /** Great columns of rock from a hollow's floor to its roof, flared at both ends. */
    private void columns(ServerLevel level, Space s, Area a, RandomSource r) {
        int n = 2 + s.rx() / 16;
        for (int i = 0; i < n; i++) {
            float rad = 1.6f + r.nextFloat() * 1.2f;
            BlockPos g = null;
            for (int t = 0; t < 6 && g == null; t++) {
                BlockPos p = spot(level, a, r, 0.25f, 0.7f);
                if (p != null && open(p, rad + 4) && !b.inRoom(p.getX() - ox, p.getY(), p.getZ() - oz, 1.6)) g = p;
            }
            if (g == null) continue;
            BlockPos roof = march(level, g, 0, 1, 0, 90);
            if (roof == null) continue;
            int bottom = g.getY() - 1, top = roof.getY();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int y = bottom; y <= top; y++) {
                float flare = 1.4f * Math.max(0, 1 - (y - bottom) / 3f) + 1.8f * Math.max(0, 1 - (top - y) / 4f);
                float rr = rad + flare + (b.noise(g.getX(), y, g.getZ()) - 0.5f) * 0.8f;
                int reach = Mth.ceil(rr);
                for (int dx = -reach; dx <= reach; dx++) {
                    for (int dz = -reach; dz <= reach; dz++) {
                        if (dx * dx + dz * dz > rr * rr) continue;
                        p.set(g.getX() + dx, y, g.getZ() + dz);
                        if (airy(level.getBlockState(p))) level.setBlock(p, y == bottom ? realm.floor(r) : realm.wall(r), FLAGS);
                    }
                }
            }
        }
    }

    // ================================================================== passages

    void passage(ServerLevel level, Tunnel t) {
        RandomSource r = new LegacyRandomSource(gate.seed() * 53 + t.from() * 131L + t.to() * 7L + t.start().asLong());
        switch (t.style()) {
            case TUBE -> tube(level, t, r);
            case CORRIDOR -> corridor(level, t, r);
            case BRIDGE -> bridge(level, t);
            case SPIRAL -> spiral(level, t);
        }
    }

    /** Long tunnels get a light now and then, and the realm's webs and roots. */
    private void tube(ServerLevel level, Tunnel t, RandomSource r) {
        double len = t.length();
        if (len < 14) return;
        int n = (int) (len / 11);
        for (int i = 1; i <= n; i++) {
            double u = i / (double) (n + 1);
            double[] at = DungeonLayout.along(t, u);
            if (b.inRoom(at[0], at[1], at[2], 1.1)) continue;
            double[] ahead = DungeonLayout.along(t, Math.min(1, u + 0.02));
            double dx = ahead[0] - at[0], dz = ahead[2] - at[2], l = Math.sqrt(dx * dx + dz * dz);
            if (l < 1e-6) continue;
            int sgn = (i & 1) == 0 ? 1 : -1;
            BlockPos from = new BlockPos(b.wx(at[0]), Mth.floor(at[1]), b.wz(at[2]));
            if (!level.getBlockState(from).isAir()) continue;
            BlockPos hit = march(level, from, (float) (-dz / l) * sgn, 0, (float) (dx / l) * sgn, Mth.ceil(t.radius()) + 4);
            if (hit != null) wallLight(level, hit);
            if (realm.has(Decor.COBWEBS) && r.nextInt(3) == 0) {
                BlockPos up = march(level, from, (float) (dz / l) * sgn * 0.7f, 0.7f, (float) (-dx / l) * sgn * 0.7f, 8);
                Direction f = up == null ? null : face(level, up, from);
                if (f != null && up.relative(f).getY() >= from.getY()) level.setBlock(up.relative(f), Blocks.COBWEB.defaultBlockState(), FLAGS);
            }
            if (realm.has(Decor.ROOTS) && r.nextBoolean()) {
                BlockPos top = march(level, from, 0, 1, 0, 8);
                if (top != null && Blocks.HANGING_ROOTS.defaultBlockState().canSurvive(level, top.below()) && level.getBlockState(top.below()).isAir()) {
                    level.setBlock(top.below(), Blocks.HANGING_ROOTS.defaultBlockState(), FLAGS);
                }
            }
        }
    }

    /** Corridors: lights in alternate walls, timber frames in a mine, webs up in the corners. */
    private void corridor(ServerLevel level, Tunnel t, RandomSource r) {
        List<int[]> path = DungeonBuilder.corridorPath(t);
        int w = DungeonBuilder.corridorHalf(t);
        int height = w >= 2 ? 5 : 4;
        boolean frames = realm.has(Decor.SUPPORTS);
        for (int i = 2; i < path.size() - 2; i++) {
            int[] cell = path.get(i);
            int x = cell[0], z = cell[1], feet = cell[2];
            if (b.inRoom(x, feet + 1, z, 1.15)) continue;
            if (x == t.bend().getX() && z == t.bend().getZ()) continue;
            int px = -cell[4], pz = cell[3];
            if (frames && i % 5 == 0) {
                frame(level, x, z, feet, px, pz, w, height, r);
            } else if (i % 6 == 3) {
                int s = (i / 6) % 2 == 0 ? 1 : -1;
                BlockPos wall = new BlockPos(x + px * s * (w + 1) + ox, feet + 2, z + pz * s * (w + 1) + oz);
                BlockPos inside = new BlockPos(x + px * s * w + ox, feet + 2, z + pz * s * w + oz);
                if (level.getBlockState(inside).isAir()) wallLight(level, wall);
            }
            if (realm.has(Decor.COBWEBS) && r.nextInt(9) == 0) {
                int s = r.nextBoolean() ? 1 : -1;
                BlockPos web = new BlockPos(x + px * s * w + ox, feet + height - 2, z + pz * s * w + oz);
                if (level.getBlockState(web).isAir()) level.setBlock(web, Blocks.COBWEB.defaultBlockState(), FLAGS);
            }
        }
    }

    /** A mine's timber frame: two posts in the walls and a beam across the roof, now and then a lantern under it. */
    private void frame(ServerLevel level, int x, int z, int feet, int px, int pz, int w, int height, RandomSource r) {
        BlockState post = Blocks.SPRUCE_LOG.defaultBlockState();
        BlockState beam = Blocks.SPRUCE_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, px != 0 ? Direction.Axis.X : Direction.Axis.Z);
        for (int s = -1; s <= 1; s += 2) {
            for (int y = feet; y < feet + height - 1; y++) {
                BlockPos p = new BlockPos(x + px * s * (w + 1) + ox, y, z + pz * s * (w + 1) + oz);
                if (rock(level, p)) level.setBlock(p, post, FLAGS);
            }
        }
        BlockPos middle = null;
        for (int o = -w - 1; o <= w + 1; o++) {
            int cx = x + px * o + ox, cz = z + pz * o + oz;
            for (int y = feet + 2; y <= feet + height + 1; y++) {
                BlockPos p = new BlockPos(cx, y, cz);
                if (level.getBlockState(p).isAir()) continue;
                if (rock(level, p)) level.setBlock(p, beam, FLAGS);
                if (o == 0) middle = p;
                break;
            }
        }
        if (middle != null && r.nextInt(3) == 0 && middle.getY() - 1 >= feet + 3) placeIfAir(level, middle.below(), hanging(realm.lantern));
    }

    /** Lanterns on a bridge's railing posts, every few steps, side to side. */
    private void bridge(ServerLevel level, Tunnel t) {
        int n = Math.max(1, (int) (t.length() / 6));
        double[] perp = DungeonBuilder.perpendicular(t);
        for (int i = 1; i < n; i++) {
            double[] at = DungeonLayout.along(t, i / (double) n);
            if (b.inRoom(at[0], at[1] + 1, at[2], 1.0)) continue;
            int s = (i & 1) == 0 ? 1 : -1;
            int x = b.wx(at[0] + perp[0] * 2 * s), z = b.wz(at[2] + perp[1] * 2 * s);
            int base = Mth.floor(at[1]);
            for (int y = base + 1; y >= base - 2; y--) {
                BlockPos p = new BlockPos(x, y, z);
                if (DungeonBuilder.joins(level.getBlockState(p))) {
                    b.place(level, p.above(), realm.lantern);
                    break;
                }
            }
        }
    }

    /** Lights in the outer wall of winding stairs. */
    private void spiral(ServerLevel level, Tunnel t) {
        int n = Math.max(2, (int) (t.length() / 7));
        double ax = t.bend().getX(), az = t.bend().getZ();
        for (int i = 1; i < n; i++) {
            double[] at = DungeonLayout.along(t, i / (double) n);
            if (b.inRoom(at[0], at[1] + 1, at[2], 1.05)) continue;
            double dx = at[0] - ax, dz = at[2] - az, l = Math.sqrt(dx * dx + dz * dz);
            if (l < 1e-6) continue;
            BlockPos from = new BlockPos(b.wx(at[0]), Mth.floor(at[1]) + 2, b.wz(at[2]));
            if (!level.getBlockState(from).isAir()) continue;
            BlockPos hit = march(level, from, (float) (dx / l), 0, (float) (dz / l), 5);
            if (hit != null) wallLight(level, hit);
        }
    }

    // ================================================================== the realm's dressing

    /** Everything the realm decorates with, scaled to the area ({@code amount} 1: as much as a full room gets). */
    private void decorations(ServerLevel level, Area a, RandomSource r, float amount) {
        float n = size(a) * amount;
        Room room = a.room;
        Kind kind = room != null ? room.kind() : null;
        boolean island = room != null && room.shape() == Shape.ISLAND;
        boolean roof = !island;
        boolean natural = room == null || room.natural() || (island && !realm.built());

        // what shapes the room first: pillars, channels, the carpet; then what hangs on the walls
        if (realm.has(Decor.PILLARS) && room != null && kind != Kind.BOSS && kind != Kind.PASSAGE) pillars(level, room, a);
        if (realm.has(Decor.CHANNELS) && room != null && room.shape() == Shape.HALL && kind != Kind.PASSAGE) channels(level, room, a);
        if (realm.has(Decor.CARPET) && room != null && kind != Kind.BOSS) carpet(level, room, a);
        if (realm.has(Decor.BANNERS) && room != null) banners(level, room, a);
        if (realm.has(Decor.COPPER) && room != null) copper(level, room, a, r);

        if (realm.has(Decor.STALAGMITES) && natural) {
            if (kind != Kind.BOSS && kind != Kind.ENTRANCE && !island) spikes(level, a, r, count(r, 3, 3, n));
            if (roof) hanging(level, a, r, count(r, 4, 4, n), realm.spike, room == null ? 7 : 3);
        }
        if (realm.has(Decor.ROOTS) && roof) roots(level, a, r, count(r, 4, 5, n));
        if (realm.has(Decor.GLOW_BERRIES) && roof) glowBerries(level, a, r, count(r, 2, 3, n));
        if (realm.has(Decor.MOSS)) moss(level, a, r, count(r, 1, 2, n));
        if (realm.has(Decor.AZALEA)) azalea(level, a, r, count(r, 1, 2, n));
        if (realm.has(Decor.POOLS) && kind != Kind.ENTRANCE && kind != Kind.BOSS) pools(level, a, r, count(r, 0, 2, n), Blocks.WATER.defaultBlockState());
        if (realm.has(Decor.LAVA) && kind != Kind.ENTRANCE && kind != Kind.BOSS) pools(level, a, r, count(r, 0, 2, n), Blocks.LAVA.defaultBlockState());
        if (realm.has(Decor.ICE)) ice(level, a, r, n, roof);
        if (realm.has(Decor.CRYSTALS)) crystals(level, a, r, n, roof);
        if (realm.has(Decor.MUSHROOMS) && kind != Kind.PASSAGE) mushrooms(level, a, r, n);
        if (realm.has(Decor.BASALT)) basalt(level, a, r, count(r, 1, 1, n));
        if (realm.has(Decor.BONES)) bones(level, a, r, count(r, 2, 3, n));
        if (realm.has(Decor.COBWEBS) && roof) cobwebs(level, a, r, count(r, 3, 4, n));
        if (realm.has(Decor.CHAINS) && roof) chains(level, a, r, count(r, 2, 2, n));
        if (realm.has(Decor.BRAZIERS) && kind != Kind.BOSS) braziers(level, a, r, count(r, 1, 2, n));
        if (realm.has(Decor.SOUL_FIRE)) soulFire(level, a, r, count(r, 1, 2, n));
        if (realm.has(Decor.COFFINS) && room != null && kind != Kind.BOSS && kind != Kind.ENTRANCE) coffins(level, a, r, count(r, 1, 1, n));
        if (realm.has(Decor.URNS)) urns(level, a, r, count(r, 2, 2, n));
        if (realm.has(Decor.SCULK)) sculk(level, a, r, count(r, 1, 1, n));
        if (realm.has(Decor.EGGS)) eggs(level, a, r, n, roof);
        if (realm.has(Decor.CORAL)) coral(level, a, r, n);
        if (realm.has(Decor.END_RODS)) endRods(level, a, r, count(r, 3, 4, n), roof);
        if (realm.has(Decor.NETHER_PLANTS)) netherPlants(level, a, r, n, roof);
    }

    private static int count(RandomSource r, int base, int spread, float n) {
        return Math.round((base + r.nextInt(spread + 1)) * n);
    }

    /** How much an area takes, against an ordinary room of 17 by 17. */
    private static float size(Area a) {
        float s = a.rx * a.rz / 64f;
        return a.space != null ? Math.min(6f, s * 0.35f) : Mth.clamp(s, 0.35f, 2.5f);
    }

    /** How far out from the middle things that stand in the way may go: not near the boss or where players arrive. */
    private static float inner(Area a) {
        if (a.room == null) return 0.1f;
        return switch (a.room.kind()) {
            case BOSS -> 0.7f;
            case ENTRANCE -> 0.5f;
            default -> 0.2f;
        };
    }

    private static float outer(Area a) {
        return a.box ? 0.88f : 0.82f;
    }

    private void spikes(ServerLevel level, Area a, RandomSource r, int n) {
        int tallest = a.space != null ? 6 : 4;
        for (int i = 0; i < n; i++) {
            BlockPos at = standing(level, a, r, inner(a), outer(a) - 0.08f, 2.5);
            if (at == null) continue;
            int h = 1 + r.nextInt(tallest);
            for (int k = 0; k < h; k++) b.place(level, at.above(k), k == h - 1 && r.nextInt(3) == 0 ? realm.accent(r) : realm.spike);
        }
    }

    /** Things hanging from the roof (stalactites, icicles), never low enough to walk into. */
    private void hanging(ServerLevel level, Area a, RandomSource r, int n, BlockState block, int longest) {
        for (int i = 0; i < n; i++) {
            BlockPos g = spot(level, a, r, 0f, 0.8f);
            if (g == null) continue;
            BlockPos top = march(level, g, 0, 1, 0, 90);
            if (top == null) continue;
            int h = 1 + r.nextInt(longest);
            for (int k = 1; k <= h; k++) {
                BlockPos p = top.below(k);
                if (p.getY() <= g.getY() + 3) break;
                b.place(level, p, block);
            }
        }
    }

    private void roots(ServerLevel level, Area a, RandomSource r, int n) {
        BlockState roots = Blocks.HANGING_ROOTS.defaultBlockState();
        for (int i = 0; i < n; i++) {
            BlockPos[] spot = underRoof(level, a, r);
            if (spot == null) continue;
            BlockPos under = spot[1];
            if (roots.canSurvive(level, under)) level.setBlock(under, roots, FLAGS);
            for (Direction d : HORIZONTAL) {
                BlockPos q = under.relative(d);
                if (r.nextInt(3) == 0 && level.getBlockState(q).isAir() && roots.canSurvive(level, q)) level.setBlock(q, roots, FLAGS);
            }
        }
    }

    private void glowBerries(ServerLevel level, Area a, RandomSource r, int n) {
        for (int i = 0; i < n; i++) {
            BlockPos[] spot = underRoof(level, a, r);
            if (spot == null) continue;
            int len = Math.min(2 + r.nextInt(4), spot[1].getY() - spot[0].getY() - 2);
            vine(level, spot[1], len, Blocks.CAVE_VINES_PLANT, Blocks.CAVE_VINES, r, true);
        }
    }

    /** Patches of moss over the floor, with moss carpet here and there. */
    private void moss(ServerLevel level, Area a, RandomSource r, int n) {
        for (int i = 0; i < n; i++) {
            BlockPos g = spot(level, a, r, 0.1f, 0.9f);
            if (g == null) continue;
            int rad = 2 + r.nextInt(2);
            for (int dx = -rad; dx <= rad; dx++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    if (dx * dx + dz * dz > rad * rad + r.nextInt(3)) continue;
                    BlockPos p = ground(level, g.getX() + dx, g.getY(), g.getZ() + dz, 2);
                    if (p == null || !rock(level, p.below())) continue;
                    level.setBlock(p.below(), Blocks.MOSS_BLOCK.defaultBlockState(), FLAGS);
                    if (r.nextInt(3) == 0) level.setBlock(p, Blocks.MOSS_CARPET.defaultBlockState(), FLAGS);
                }
            }
        }
    }

    /** Azalea bushes growing through the floor on a bed of moss. */
    private void azalea(ServerLevel level, Area a, RandomSource r, int n) {
        for (int i = 0; i < n; i++) {
            BlockPos at = standing(level, a, r, Math.max(inner(a), 0.35f), outer(a), 2.5);
            if (at == null || !rock(level, at.below())) continue;
            level.setBlock(at.below(), Blocks.MOSS_BLOCK.defaultBlockState(), FLAGS);
            level.setBlock(at, (r.nextBoolean() ? Blocks.FLOWERING_AZALEA : Blocks.AZALEA).defaultBlockState(), FLAGS);
            for (Direction d : HORIZONTAL) {
                BlockPos q = at.relative(d);
                if (r.nextBoolean() && level.getBlockState(q).isAir() && rock(level, q.below())) {
                    level.setBlock(q.below(), Blocks.MOSS_BLOCK.defaultBlockState(), FLAGS);
                    level.setBlock(q, Blocks.MOSS_CARPET.defaultBlockState(), FLAGS);
                }
            }
        }
    }

    /** Small pools sunk into the floor, banked all round so they never spill. */
    private void pools(ServerLevel level, Area a, RandomSource r, int n, BlockState liquid) {
        boolean lava = liquid.is(Blocks.LAVA);
        for (int i = 0; i < n; i++) {
            BlockPos g = standing(level, a, r, Math.max(inner(a), 0.35f), outer(a) - 0.05f, lava ? 3.5 : 2.5);
            if (g == null) continue;
            float rad = 1.2f + r.nextFloat() * 1.3f;
            int y = g.getY() - 1;
            Set<Long> cells = new HashSet<>();
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (dx * dx + dz * dz > rad * rad) continue;
                    BlockPos p = new BlockPos(g.getX() + dx, y, g.getZ() + dz);
                    if (!rock(level, p) || !level.getBlockState(p.above()).isAir() || level.getBlockState(p.below()).isAir()) continue;
                    if (lava && !open(p.above(), 2.5)) continue;
                    cells.add(p.asLong());
                }
            }
            // drop any cell not banked all round by rock or another cell of the pool, until none is left open
            boolean changed = true;
            while (changed && !cells.isEmpty()) {
                changed = false;
                for (Long k : List.copyOf(cells)) {
                    BlockPos p = BlockPos.of(k);
                    for (Direction d : HORIZONTAL) {
                        BlockPos q = p.relative(d);
                        if (cells.contains(q.asLong())) continue;
                        BlockState s = level.getBlockState(q);
                        if (s.isAir() || !s.getFluidState().isEmpty() || !s.isSolidRender(level, q)) {
                            cells.remove(k);
                            changed = true;
                            break;
                        }
                    }
                }
            }
            for (long k : cells) level.setBlock(BlockPos.of(k), liquid, FLAGS);
        }
    }

    /** Spires of packed ice with a blue foot, and icicles. */
    private void ice(ServerLevel level, Area a, RandomSource r, float n, boolean roof) {
        BlockState packed = Blocks.PACKED_ICE.defaultBlockState(), blue = Blocks.BLUE_ICE.defaultBlockState();
        int spires = count(r, 1, 2, n);
        for (int i = 0; i < spires; i++) {
            BlockPos at = standing(level, a, r, inner(a), outer(a) - 0.08f, 2.5);
            if (at == null) continue;
            int h = 2 + r.nextInt(a.space != null ? 6 : 4);
            for (int k = 0; k < h; k++) b.place(level, at.above(k), k == 0 && r.nextBoolean() ? blue : packed);
            for (Direction d : HORIZONTAL) {
                BlockPos q = at.relative(d);
                if (r.nextInt(3) == 0 && level.getBlockState(q).isAir() && standable(level, q.below()) && open(q, 2.0)) b.place(level, q, packed);
            }
        }
        if (roof) hanging(level, a, r, count(r, 3, 3, n), packed, 3);
    }

    /** Amethyst: clusters on the walls and the roof, and points of crystal standing up out of the floor. */
    private void crystals(ServerLevel level, Area a, RandomSource r, float n, boolean roof) {
        int wall = count(r, 5, 5, n);
        for (int i = 0; i < wall; i++) {
            BlockPos from = inside(level, a, r);
            if (from == null) continue;
            float ang = r.nextFloat() * Mth.TWO_PI;
            BlockPos hit = march(level, from, Mth.cos(ang), (r.nextFloat() - 0.3f) * 0.6f, Mth.sin(ang), a.reach() + 10);
            if (hit != null) attach(level, hit, from, bud(r));
        }
        int stands = count(r, 0, 2, n);
        for (int i = 0; i < stands; i++) {
            BlockPos at = standing(level, a, r, inner(a), outer(a) - 0.05f, 2.5);
            if (at == null) continue;
            int h = 1 + r.nextInt(3);
            for (int k = 0; k < h; k++) b.place(level, at.above(k), Blocks.AMETHYST_BLOCK.defaultBlockState());
            b.place(level, at.above(h), Blocks.AMETHYST_CLUSTER.defaultBlockState());
        }
        if (roof) {
            int hang = count(r, 2, 3, n);
            for (int i = 0; i < hang; i++) {
                BlockPos[] spot = underRoof(level, a, r);
                if (spot != null) level.setBlock(spot[1], bud(r).setValue(BlockStateProperties.FACING, Direction.DOWN), FLAGS);
            }
        }
    }

    private static BlockState bud(RandomSource r) {
        return (r.nextInt(3) == 0 ? Blocks.LARGE_AMETHYST_BUD : Blocks.AMETHYST_CLUSTER).defaultBlockState();
    }

    /** Giant mushrooms (a red dome or a broad brown cap on a pale stem) with shroomlights under the rim. */
    private void mushrooms(ServerLevel level, Area a, RandomSource r, float n) {
        int big = count(r, 1, 2, n);
        for (int i = 0; i < big; i++) {
            BlockPos at = standing(level, a, r, Math.max(inner(a), 0.3f), outer(a) - 0.12f, 3.5);
            if (at == null) continue;
            BlockPos roof = march(level, at, 0, 1, 0, 40);
            int room = roof == null ? 12 : roof.getY() - at.getY();
            int stem = Math.min(3 + r.nextInt(4), room - 3);
            if (stem < 3) continue;
            for (int k = 0; k < stem; k++) b.place(level, at.above(k), Blocks.MUSHROOM_STEM.defaultBlockState());
            BlockPos cap = at.above(stem);
            if (r.nextBoolean()) {
                BlockState red = Blocks.RED_MUSHROOM_BLOCK.defaultBlockState();
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        int d2 = dx * dx + dz * dz;
                        if (d2 <= 5) b.place(level, cap.offset(dx, 0, dz), red);
                        if (d2 <= 2) b.place(level, cap.offset(dx, 1, dz), red);
                        if (d2 >= 4 && d2 <= 5) b.place(level, cap.offset(dx, -1, dz), r.nextInt(5) == 0 ? Blocks.SHROOMLIGHT.defaultBlockState() : red);
                    }
                }
            } else {
                BlockState brown = Blocks.BROWN_MUSHROOM_BLOCK.defaultBlockState();
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        int d2 = dx * dx + dz * dz;
                        if (d2 <= 10) b.place(level, cap.offset(dx, 0, dz), d2 >= 8 && r.nextInt(6) == 0 ? Blocks.SHROOMLIGHT.defaultBlockState() : brown);
                    }
                }
            }
        }
        int small = count(r, 2, 3, n);
        for (int i = 0; i < small; i++) {
            BlockPos g = spot(level, a, r, 0.1f, 0.9f);
            if (g == null) continue;
            BlockState s = (r.nextBoolean() ? Blocks.BROWN_MUSHROOM : Blocks.RED_MUSHROOM).defaultBlockState();
            if (s.canSurvive(level, g)) level.setBlock(g, s, FLAGS);
        }
    }

    /** Clusters of basalt columns, magma seeping round their feet. */
    private void basalt(ServerLevel level, Area a, RandomSource r, int n) {
        BlockState column = Blocks.BASALT.defaultBlockState();
        for (int i = 0; i < n; i++) {
            BlockPos at = standing(level, a, r, Math.max(inner(a), 0.35f), outer(a) - 0.05f, 3.0);
            if (at == null) continue;
            int cols = 3 + r.nextInt(4);
            for (int k = 0; k < cols; k++) {
                BlockPos g = k == 0 ? at : ground(level, at.getX() + r.nextInt(3) - 1, at.getY(), at.getZ() + r.nextInt(3) - 1, 2);
                if (g == null || !open(g, 2.0)) continue;
                int h = 1 + r.nextInt(4);
                for (int y = 0; y < h; y++) b.place(level, g.above(y), column);
            }
            for (int k = 0; k < 3; k++) {
                BlockPos q = at.offset(r.nextInt(5) - 2, -1, r.nextInt(5) - 2);
                if (rock(level, q) && level.getBlockState(q.above()).isAir()) level.setBlock(q, Blocks.MAGMA_BLOCK.defaultBlockState(), FLAGS);
            }
        }
    }

    /** Bones by the walls: a heap, a skull, a pair of ribs. */
    private void bones(ServerLevel level, Area a, RandomSource r, int n) {
        for (int i = 0; i < n; i++) {
            BlockPos at = standing(level, a, r, Math.max(inner(a), 0.45f), outer(a), 2.0);
            if (at == null) continue;
            switch (r.nextInt(3)) {
                case 0 -> {
                    b.place(level, at, bone(Direction.Axis.Y));
                    if (r.nextBoolean()) b.place(level, at.above(), skull(r));
                }
                case 1 -> b.place(level, at, skull(r));
                default -> {
                    Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(r);
                    b.place(level, at, bone(d.getAxis()));
                    BlockPos q = at.relative(d);
                    if (level.getBlockState(q).isAir() && standable(level, q.below()) && open(q, 2.0)) b.place(level, q, bone(d.getAxis()));
                }
            }
        }
    }

    private static BlockState bone(Direction.Axis axis) {
        return Blocks.BONE_BLOCK.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
    }

    private static BlockState skull(RandomSource r) {
        return (r.nextInt(6) == 0 ? Blocks.WITHER_SKELETON_SKULL : Blocks.SKELETON_SKULL).defaultBlockState()
                .setValue(BlockStateProperties.ROTATION_16, r.nextInt(16));
    }

    /** Webs up where the walls meet the roof. */
    private void cobwebs(ServerLevel level, Area a, RandomSource r, int n) {
        for (int i = 0; i < n; i++) {
            BlockPos from = inside(level, a, r);
            if (from == null) continue;
            float ang = r.nextFloat() * Mth.TWO_PI;
            BlockPos hit = march(level, from, Mth.cos(ang) * 0.8f, 0.6f, Mth.sin(ang) * 0.8f, a.reach() + a.high + 4);
            if (hit == null) continue;
            Direction f = face(level, hit, from);
            if (f == null) continue;
            BlockPos web = hit.relative(f);
            if (web.getY() >= a.floor + 3) level.setBlock(web, Blocks.COBWEB.defaultBlockState(), FLAGS);
        }
    }

    /** Chains down from the roof with a lantern on the end, and in a built hall, chandeliers down its middle. */
    private void chains(ServerLevel level, Area a, RandomSource r, int n) {
        BlockState lamp = hanging(realm.lantern);
        for (int i = 0; i < n; i++) {
            BlockPos g = spot(level, a, r, 0.15f, 0.8f);
            if (g == null) continue;
            chain(level, g, 1 + r.nextInt(6), r.nextInt(3) > 0 ? lamp : null);
        }
        Room room = a.room;
        if (room != null && room.shape() == Shape.HALL && room.kind() != Kind.PASSAGE) {
            boolean alongX = room.rx() >= room.rz();
            int along = alongX ? room.rx() : room.rz();
            for (int k = -along + 4; k <= along - 4; k += 7) {
                BlockPos g = ground(level, a.cx + (alongX ? k : 0), a.floor, a.cz + (alongX ? 0 : k), 2);
                if (g != null) chain(level, g, 99, lamp);
            }
        }
    }

    /** A chain from the roof over a spot, as long as it may be (its end stays well over head), and a lantern on it. */
    private void chain(ServerLevel level, BlockPos ground, int length, @Nullable BlockState lamp) {
        BlockPos top = march(level, ground, 0, 1, 0, 60);
        if (top == null) return;
        int room = top.getY() - ground.getY();
        if (room < 6) return;
        int len = Math.max(1, Math.min(length, room - 5));
        for (int k = 1; k <= len; k++) b.place(level, top.below(k), Blocks.CHAIN.defaultBlockState());
        if (lamp != null) b.place(level, top.below(len + 1), lamp);
    }

    /** Pillars: a colonnade down a hall, a ring in a round hall, floor to roof. */
    private void pillars(ServerLevel level, Room room, Area a) {
        switch (room.shape()) {
            case HALL -> {
                boolean alongX = room.rx() >= room.rz();
                int along = alongX ? room.rx() : room.rz(), across = alongX ? room.rz() : room.rx();
                if (across < 5) return;
                int off = across - 2;
                for (int s = -1; s <= 1; s += 2) {
                    for (int k = -(along - 2); k <= along - 2; k += 4) {
                        BlockPos p = new BlockPos(a.cx + (alongX ? k : s * off), a.floor, a.cz + (alongX ? s * off : k));
                        if (airy(level.getBlockState(p)) && standable(level, p.below()) && open(p, 2.5)) column(level, p, 40);
                    }
                }
            }
            case ROUND -> {
                if (room.rx() < 7) return;
                int n = Math.max(6, Math.round(Mth.TWO_PI * (room.rx() - 2) / 6f));
                for (int i = 0; i < n; i++) {
                    float ang = i * Mth.TWO_PI / n;
                    BlockPos p = new BlockPos(a.cx + Math.round(Mth.cos(ang) * (room.rx() - 2)), a.floor, a.cz + Math.round(Mth.sin(ang) * (room.rz() - 2)));
                    if (airy(level.getBlockState(p)) && standable(level, p.below()) && open(p, 2.5)) column(level, p, 40);
                }
            }
            default -> {}
        }
    }

    /** A pillar from the floor to the roof: a base and a capital of the realm's cap block, its body between. */
    private void column(ServerLevel level, BlockPos foot, int max) {
        int h = 0;
        while (h < max && airy(level.getBlockState(foot.above(h)))) h++;
        if (h >= max) h = 8;
        for (int k = 0; k < h; k++) level.setBlock(foot.above(k), k == 0 || k == h - 1 ? realm.pillarCap : realm.pillar, FLAGS);
    }

    /** Channels of the realm's liquid down both sides of a hall, kept away from its doors. */
    private void channels(ServerLevel level, Room room, Area a) {
        boolean alongX = room.rx() >= room.rz();
        int along = alongX ? room.rx() : room.rz(), across = alongX ? room.rz() : room.rx();
        if (across < 5) return;
        BlockState liquid = realm.liquid.defaultBlockState();
        boolean lava = realm.liquid == Blocks.LAVA;
        for (int s = -1; s <= 1; s += 2) {
            for (int k = -(along - 2); k <= along - 2; k++) {
                BlockPos p = new BlockPos(a.cx + (alongX ? k : s * (across - 1)), a.floor - 1, a.cz + (alongX ? s * (across - 1) : k));
                if (!level.getBlockState(p.above()).isAir() || !rock(level, p) || level.getBlockState(p.below()).isAir()) continue;
                if (!clearOfPaths(p.above(), lava ? 3.5 : 2.0)) continue;
                boolean banked = true;
                for (Direction d : HORIZONTAL) {
                    BlockState q = level.getBlockState(p.relative(d));
                    if (q.isAir()) banked = false;
                }
                if (banked) level.setBlock(p, liquid, FLAGS);
            }
        }
    }

    /** A runner down a hall's length, a round rug in a round hall. */
    private void carpet(ServerLevel level, Room room, Area a) {
        if (realm.carpet == null) return;
        BlockState carpet = realm.carpet.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        switch (room.shape()) {
            case HALL -> {
                boolean alongX = room.rx() >= room.rz();
                int along = alongX ? room.rx() : room.rz();
                int w = (alongX ? room.rz() : room.rx()) >= 6 ? 1 : 0;
                for (int k = -along; k <= along; k++) {
                    for (int o = -w; o <= w; o++) {
                        p.set(a.cx + (alongX ? k : o), a.floor, a.cz + (alongX ? o : k));
                        if (level.getBlockState(p).isAir() && standable(level, p.below())) level.setBlock(p, carpet, FLAGS);
                    }
                }
            }
            case ROUND -> {
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        if (dx * dx + dz * dz > 10) continue;
                        p.set(a.cx + dx, a.floor, a.cz + dz);
                        if (level.getBlockState(p).isAir() && standable(level, p.below())) level.setBlock(p, carpet, FLAGS);
                    }
                }
            }
            default -> {}
        }
    }

    /** Banners high on the walls of halls. */
    private void banners(ServerLevel level, Room room, Area a) {
        if (realm.banner == null || !(room.shape() == Shape.HALL || room.shape() == Shape.ROUND)) return;
        int y = a.floor + Math.min(5, room.topY() - a.floor - 2);
        if (y < a.floor + 3) return;
        int n = 4 + room.reach() / 3;
        BlockPos from = new BlockPos(a.cx, y, a.cz);
        for (int i = 0; i < n; i++) {
            float ang = (i + 0.5f) * Mth.TWO_PI / n;
            BlockPos hit = march(level, from, Mth.cos(ang), 0, Mth.sin(ang), room.reach() + 4);
            if (hit == null || !rock(level, hit)) continue;
            Direction f = face(level, hit, from);
            if (f == null || f.getAxis().isVertical()) continue;
            BlockPos at = hit.relative(f);
            if (!level.getBlockState(at).isAir() || !level.getBlockState(at.below()).isAir()) continue;
            level.setBlock(at, realm.banner.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f), FLAGS);
        }
    }

    /** Copper: a grate down the middle of a hall (a ring of it in a round one), lightning rods jutting from the walls. */
    private void copper(ServerLevel level, Room room, Area a, RandomSource r) {
        BlockState grate = Blocks.WAXED_OXIDIZED_COPPER_GRATE.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        if (room.shape() == Shape.HALL) {
            boolean alongX = room.rx() >= room.rz();
            int along = alongX ? room.rx() : room.rz();
            for (int k = -along + 1; k <= along - 1; k++) {
                p.set(a.cx + (alongX ? k : 0), a.floor - 1, a.cz + (alongX ? 0 : k));
                if (rock(level, p) && level.getBlockState(p.above()).isAir()) level.setBlock(p, grate, FLAGS);
            }
        } else if (room.shape() == Shape.ROUND) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    int d2 = dx * dx + dz * dz;
                    if (d2 < 7 || d2 > 12) continue;
                    p.set(a.cx + dx, a.floor - 1, a.cz + dz);
                    if (rock(level, p) && level.getBlockState(p.above()).isAir()) level.setBlock(p, grate, FLAGS);
                }
            }
        }
        int coils = 2 + r.nextInt(3);
        for (int i = 0; i < coils; i++) {
            float ang = r.nextFloat() * Mth.TWO_PI;
            BlockPos from = new BlockPos(a.cx, a.floor + 2 + r.nextInt(3), a.cz);
            BlockPos hit = march(level, from, Mth.cos(ang), 0, Mth.sin(ang), room.reach() + 4);
            if (hit == null || !rock(level, hit)) continue;
            Direction f = face(level, hit, from);
            if (f == null || f.getAxis().isVertical()) continue;
            placeIfAir(level, hit.relative(f), Blocks.LIGHTNING_ROD.defaultBlockState().setValue(BlockStateProperties.FACING, f));
        }
    }

    /** Fire in a raised bowl: a post of the realm's railing with a campfire on it. */
    private void braziers(ServerLevel level, Area a, RandomSource r, int n) {
        BlockState fire = (realm.has(Decor.SOUL_FIRE) ? Blocks.SOUL_CAMPFIRE : Blocks.CAMPFIRE).defaultBlockState();
        for (int i = 0; i < n; i++) {
            BlockPos at = standing(level, a, r, Math.max(inner(a), 0.55f), outer(a), 2.5);
            if (at == null || !level.getBlockState(at.above(2)).isAir()) continue;
            b.place(level, at, b.railing());
            b.place(level, at.above(), fire);
        }
    }

    /** Pits of soul fire, ringed with low stones in built halls. */
    private void soulFire(ServerLevel level, Area a, RandomSource r, int n) {
        for (int i = 0; i < n; i++) {
            BlockPos at = standing(level, a, r, Math.max(inner(a), 0.5f), outer(a), 3.0);
            if (at == null || !rock(level, at.below())) continue;
            level.setBlock(at.below(), Blocks.SOUL_SOIL.defaultBlockState(), FLAGS);
            level.setBlock(at, Blocks.SOUL_FIRE.defaultBlockState(), FLAGS);
            if (!realm.built()) continue;
            for (Direction d : HORIZONTAL) {
                BlockPos q = at.relative(d);
                if (level.getBlockState(q).isAir() && standable(level, q.below()) && open(q, 1.5)) b.place(level, q, b.slab());
            }
        }
    }

    /** Sarcophagi along the walls: two blocks long, a lid on top. */
    private void coffins(ServerLevel level, Area a, RandomSource r, int n) {
        for (int i = 0; i < n; i++) {
            float ang = r.nextFloat() * Mth.TWO_PI;
            BlockPos from = new BlockPos(a.cx, a.floor, a.cz);
            BlockPos hit = march(level, from, Mth.cos(ang), 0, Mth.sin(ang), a.reach() + 4);
            if (hit == null) continue;
            Direction out = face(level, hit, from);
            if (out == null || out.getAxis().isVertical()) continue;
            BlockPos head = hit.relative(out), foot = head.relative(out.getClockWise());
            if (!fits(level, head) || !fits(level, foot)) continue;
            for (BlockPos p : List.of(head, foot)) {
                level.setBlock(p, realm.deck, FLAGS);
                b.place(level, p.above(), b.slab());
            }
        }
    }

    private boolean fits(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && standable(level, p.below()) && open(p, 2.0);
    }

    /** Pots by the walls. */
    private void urns(ServerLevel level, Area a, RandomSource r, int n) {
        for (int i = 0; i < n; i++) {
            BlockPos at = standing(level, a, r, Math.max(inner(a), 0.6f), outer(a) + 0.05f, 2.0);
            if (at == null) continue;
            level.setBlock(at, urn(r), FLAGS);
            Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(r);
            BlockPos q = at.relative(d);
            if (r.nextInt(3) == 0 && level.getBlockState(q).isAir() && standable(level, q.below()) && open(q, 2.0)) level.setBlock(q, urn(r), FLAGS);
        }
    }

    private static BlockState urn(RandomSource r) {
        if (r.nextInt(4) == 0) return Blocks.POTTED_DEAD_BUSH.defaultBlockState();
        return Blocks.DECORATED_POT.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.Plane.HORIZONTAL.getRandomDirection(r));
    }

    /** Sculk spreading over the floor in patches, veins of it up the walls. */
    private void sculk(ServerLevel level, Area a, RandomSource r, int n) {
        for (int i = 0; i < n; i++) {
            BlockPos g = spot(level, a, r, 0.1f, 0.9f);
            if (g == null) continue;
            int rad = 2 + r.nextInt(2);
            for (int dx = -rad; dx <= rad; dx++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    if (dx * dx + dz * dz > rad * rad + r.nextInt(3)) continue;
                    BlockPos p = ground(level, g.getX() + dx, g.getY(), g.getZ() + dz, 2);
                    if (p != null && rock(level, p.below())) level.setBlock(p.below(), Blocks.SCULK.defaultBlockState(), FLAGS);
                }
            }
        }
        int veins = Math.round(n * 4);
        for (int i = 0; i < veins; i++) {
            BlockPos from = inside(level, a, r);
            if (from == null) continue;
            float ang = r.nextFloat() * Mth.TWO_PI;
            BlockPos hit = march(level, from, Mth.cos(ang), 0, Mth.sin(ang), a.reach() + 8);
            if (hit == null || !rock(level, hit)) continue;
            Direction f = face(level, hit, from);
            if (f == null) continue;
            BlockState vein = Blocks.SCULK_VEIN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(f.getOpposite()), true);
            if (vein.canSurvive(level, hit.relative(f))) placeIfAir(level, hit.relative(f), vein);
        }
    }

    /** A brood: clutches of eggs by the walls, glowing egg sacs in the walls and roof, honey underfoot. */
    private void eggs(ServerLevel level, Area a, RandomSource r, float n, boolean roof) {
        int clutches = count(r, 2, 3, n);
        for (int i = 0; i < clutches; i++) {
            BlockPos at = standing(level, a, r, Math.max(inner(a), 0.55f), outer(a) + 0.05f, 2.0);
            if (at == null) continue;
            level.setBlock(at, egg(r), FLAGS);
            for (Direction d : HORIZONTAL) {
                BlockPos q = at.relative(d);
                if (r.nextInt(3) == 0 && level.getBlockState(q).isAir() && standable(level, q.below()) && open(q, 1.5)) level.setBlock(q, egg(r), FLAGS);
            }
        }
        int sacs = count(r, 2, 3, n);
        for (int i = 0; i < sacs; i++) {
            BlockPos from = inside(level, a, r);
            if (from == null) continue;
            float ang = r.nextFloat() * Mth.TWO_PI;
            BlockPos hit = march(level, from, Mth.cos(ang), roof ? r.nextFloat() * 0.8f : 0, Mth.sin(ang), a.reach() + a.high + 6);
            if (hit == null || !rock(level, hit)) continue;
            level.setBlock(hit, froglight(r), FLAGS);
            for (int k = 0; k < 2; k++) {
                BlockPos q = hit.relative(Direction.getRandom(r));
                if (rock(level, q)) level.setBlock(q, froglight(r), FLAGS);
            }
        }
        if (a.room == null || a.room.kind() != Kind.BOSS) {
            int honey = count(r, 0, 2, n);
            for (int i = 0; i < honey; i++) {
                BlockPos g = standing(level, a, r, Math.max(inner(a), 0.6f), outer(a), 2.5);
                if (g != null && rock(level, g.below())) level.setBlock(g.below(), Blocks.HONEY_BLOCK.defaultBlockState(), FLAGS);
            }
        }
    }

    private static BlockState egg(RandomSource r) {
        return Blocks.TURTLE_EGG.defaultBlockState().setValue(BlockStateProperties.EGGS, 1 + r.nextInt(4));
    }

    private static BlockState froglight(RandomSource r) {
        return switch (r.nextInt(3)) {
            case 0 -> Blocks.OCHRE_FROGLIGHT.defaultBlockState();
            case 1 -> Blocks.VERDANT_FROGLIGHT.defaultBlockState();
            default -> Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState();
        };
    }

    private static final Block[] CORAL_BLOCKS = {Blocks.DEAD_BRAIN_CORAL_BLOCK, Blocks.DEAD_TUBE_CORAL_BLOCK, Blocks.DEAD_HORN_CORAL_BLOCK,
            Blocks.DEAD_FIRE_CORAL_BLOCK, Blocks.DEAD_BUBBLE_CORAL_BLOCK};
    private static final Block[] CORAL_SPRIGS = {Blocks.DEAD_BRAIN_CORAL, Blocks.DEAD_TUBE_CORAL, Blocks.DEAD_HORN_CORAL, Blocks.DEAD_FIRE_CORAL,
            Blocks.DEAD_BUBBLE_CORAL, Blocks.DEAD_BRAIN_CORAL_FAN, Blocks.DEAD_TUBE_CORAL_FAN, Blocks.DEAD_HORN_CORAL_FAN};
    private static final Block[] CORAL_WALL_FANS = {Blocks.DEAD_BRAIN_CORAL_WALL_FAN, Blocks.DEAD_TUBE_CORAL_WALL_FAN, Blocks.DEAD_HORN_CORAL_WALL_FAN,
            Blocks.DEAD_FIRE_CORAL_WALL_FAN, Blocks.DEAD_BUBBLE_CORAL_WALL_FAN};

    /** Dead coral: clumps by the walls, sprigs and fans over the floor, fans on the walls. */
    private void coral(ServerLevel level, Area a, RandomSource r, float n) {
        int clumps = count(r, 1, 2, n);
        for (int i = 0; i < clumps; i++) {
            BlockPos at = standing(level, a, r, Math.max(inner(a), 0.6f), outer(a) + 0.05f, 2.0);
            if (at == null) continue;
            b.place(level, at, CORAL_BLOCKS[r.nextInt(CORAL_BLOCKS.length)].defaultBlockState());
            if (r.nextBoolean()) b.place(level, at.above(), dry(CORAL_SPRIGS[r.nextInt(CORAL_SPRIGS.length)].defaultBlockState()));
        }
        int sprigs = count(r, 3, 4, n);
        for (int i = 0; i < sprigs; i++) {
            BlockPos g = spot(level, a, r, 0.1f, 0.9f);
            if (g == null || near(arrival, g.getX(), g.getY(), g.getZ(), 1.5)) continue;
            BlockState s = dry(CORAL_SPRIGS[r.nextInt(CORAL_SPRIGS.length)].defaultBlockState());
            if (s.canSurvive(level, g)) level.setBlock(g, s, FLAGS);
        }
        int fans = count(r, 2, 3, n);
        for (int i = 0; i < fans; i++) {
            BlockPos from = inside(level, a, r);
            if (from == null) continue;
            float ang = r.nextFloat() * Mth.TWO_PI;
            BlockPos hit = march(level, from, Mth.cos(ang), 0, Mth.sin(ang), a.reach() + 8);
            if (hit == null || !rock(level, hit)) continue;
            Direction f = face(level, hit, from);
            if (f == null || f.getAxis().isVertical()) continue;
            BlockState fan = dry(CORAL_WALL_FANS[r.nextInt(CORAL_WALL_FANS.length)].defaultBlockState()).setValue(BlockStateProperties.HORIZONTAL_FACING, f);
            if (fan.canSurvive(level, hit.relative(f))) placeIfAir(level, hit.relative(f), fan);
        }
    }

    /** End rods hanging from the roof and standing on the floor, glowing like motes. */
    private void endRods(ServerLevel level, Area a, RandomSource r, int n, boolean roof) {
        for (int i = 0; i < n; i++) {
            if (roof && r.nextBoolean()) {
                BlockPos[] spot = underRoof(level, a, r);
                if (spot != null) level.setBlock(spot[1], rod(Direction.DOWN), FLAGS);
            } else {
                BlockPos g = standing(level, a, r, Math.max(inner(a), 0.3f), outer(a), 1.5);
                if (g != null) level.setBlock(g, rod(Direction.UP), FLAGS);
            }
        }
    }

    private static BlockState rod(Direction facing) {
        return Blocks.END_ROD.defaultBlockState().setValue(BlockStateProperties.FACING, facing);
    }

    /** The crimson growth: weeping vines and shroomlights in the roof, fungus and roots on the nylium. */
    private void netherPlants(ServerLevel level, Area a, RandomSource r, float n, boolean roof) {
        if (roof) {
            int vines = count(r, 3, 4, n);
            for (int i = 0; i < vines; i++) {
                BlockPos[] spot = underRoof(level, a, r);
                if (spot == null) continue;
                int len = Math.min(1 + r.nextInt(5), spot[1].getY() - spot[0].getY() - 2);
                vine(level, spot[1], len, Blocks.WEEPING_VINES_PLANT, Blocks.WEEPING_VINES, r, false);
            }
            int lamps = count(r, 1, 2, n);
            for (int i = 0; i < lamps; i++) {
                BlockPos[] spot = underRoof(level, a, r);
                if (spot == null) continue;
                BlockPos roofBlock = spot[1].above();
                if (!rock(level, roofBlock)) continue;
                level.setBlock(roofBlock, Blocks.SHROOMLIGHT.defaultBlockState(), FLAGS);
                for (Direction d : HORIZONTAL) {
                    BlockPos q = roofBlock.relative(d);
                    if (r.nextBoolean() && rock(level, q)) level.setBlock(q, Blocks.NETHER_WART_BLOCK.defaultBlockState(), FLAGS);
                }
            }
        }
        int plants = count(r, 4, 5, n);
        for (int i = 0; i < plants; i++) {
            BlockPos g = spot(level, a, r, 0.1f, 0.9f);
            if (g == null || near(arrival, g.getX(), g.getY(), g.getZ(), 1.5)) continue;
            BlockState s = (r.nextInt(3) == 0 ? Blocks.CRIMSON_FUNGUS : Blocks.CRIMSON_ROOTS).defaultBlockState();
            if (s.canSurvive(level, g)) level.setBlock(g, s, FLAGS);
        }
    }

    /** A vine hanging from the roof: its body, and a grown head at the bottom (so it never grows on). */
    private static void vine(ServerLevel level, BlockPos under, int len, Block body, Block head, RandomSource r, boolean berries) {
        int room = 0;
        while (room < len && level.getBlockState(under.below(room)).isAir()) room++;
        for (int k = 0; k < room; k++) {
            BlockState s = (k == room - 1 ? head : body).defaultBlockState();
            if (k == room - 1) s = s.setValue(BlockStateProperties.AGE_25, 25);
            if (berries) s = s.setValue(BlockStateProperties.BERRIES, r.nextInt(3) == 0);
            level.setBlock(under.below(k), s, FLAGS);
        }
    }

    // ================================================================== the scatter

    private void scatter(ServerLevel level, Area a, RandomSource r, int n) {
        if (realm.scatter == null) return;
        boolean snow = snowy();
        for (int i = 0; i < n; i++) {
            BlockPos p = spot(level, a, r, 0.05f, 0.92f);
            if (p == null || near(arrival, p.getX(), p.getY(), p.getZ(), 1.5)) continue;
            BlockState s = scatterState(r);
            if (!s.canSurvive(level, p) || (snow && bright(level, p))) continue;
            level.setBlock(p, s, FLAGS);
        }
    }

    private BlockState scatterState(RandomSource r) {
        BlockState s = realm.scatter;
        if (s.getBlock() instanceof MultifaceBlock) s = s.setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true);
        if (s.hasProperty(BlockStateProperties.CANDLES)) s = s.setValue(BlockStateProperties.CANDLES, 1 + r.nextInt(4));
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) s = s.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.Plane.HORIZONTAL.getRandomDirection(r));
        return dry(s);
    }

    private boolean snowy() {
        return realm.scatter != null && realm.scatter.is(Blocks.SNOW);
    }

    /** True if a strong light is near enough to a spot to melt snow there. */
    private static boolean bright(ServerLevel level, BlockPos p) {
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int e = level.getBlockState(q.setWithOffset(p, dx, dy, dz)).getLightEmission();
                    if (e - (Math.abs(dx) + Math.abs(dy) + Math.abs(dz)) > 11) return true;
                }
            }
        }
        return false;
    }

    private static BlockState dry(BlockState s) {
        return s.hasProperty(BlockStateProperties.WATERLOGGED) ? s.setValue(BlockStateProperties.WATERLOGGED, false) : s;
    }

    private static BlockState hanging(BlockState lantern) {
        return lantern.hasProperty(BlockStateProperties.HANGING) ? lantern.setValue(BlockStateProperties.HANGING, true) : lantern;
    }

    // ================================================================== finding places

    /**
     * A spot on the floor of an area, {@code lo..hi} of the way from its middle to its edge: the air just over the
     * floor (world position), or null if none was found. Halls reach into their corners; in an open space, never in a
     * room or the lake.
     */
    @Nullable
    private BlockPos spot(ServerLevel level, Area a, RandomSource r, float lo, float hi) {
        for (int attempt = 0; attempt < 8; attempt++) {
            float ang = r.nextFloat() * Mth.TWO_PI, d = lo + r.nextFloat() * (hi - lo);
            float ux = Mth.cos(ang), uz = Mth.sin(ang);
            if (a.box) {
                float k = Math.max(Math.abs(ux), Math.abs(uz));
                ux /= k;
                uz /= k;
            }
            int x = a.cx + Math.round(ux * d * a.rx), z = a.cz + Math.round(uz * d * a.rz);
            int guess = a.floor, range = 3;
            if (a.space != null) {
                int[] span = b.span(a.space, x, z);
                if (span == null) continue;
                guess = span[0];
                range = 5;
                if (b.inRoom(x - ox, guess + 1, z - oz, 1.2) || inPool(x, z)) continue;
            }
            BlockPos p = ground(level, x, guess, z, range);
            if (p != null) return p;
        }
        return null;
    }

    /** A spot for something that stands in the way: off the passages, with room over it. */
    @Nullable
    private BlockPos standing(ServerLevel level, Area a, RandomSource r, float lo, float hi, double gap) {
        for (int attempt = 0; attempt < 4; attempt++) {
            BlockPos p = spot(level, a, r, lo, hi);
            if (p != null && open(p, gap) && level.getBlockState(p.above()).isAir()) return p;
        }
        return null;
    }

    /** A point in the open air of an area to look out from, a couple of blocks over its floor. */
    @Nullable
    private BlockPos inside(ServerLevel level, Area a, RandomSource r) {
        if (a.space == null) {
            BlockPos p = new BlockPos(a.cx, a.floor + 1 + r.nextInt(Math.max(1, Math.min(a.high - 1, 5))), a.cz);
            return level.getBlockState(p).isAir() || level.getBlockState(p).is(Blocks.LIGHT) ? p : null;
        }
        BlockPos g = spot(level, a, r, 0.2f, 0.8f);
        return g == null ? null : g.above(1 + r.nextInt(4));
    }

    /** A spot on the floor and the air just under the roof over it, or null. */
    @Nullable
    private BlockPos[] underRoof(ServerLevel level, Area a, RandomSource r) {
        BlockPos g = spot(level, a, r, 0f, 0.85f);
        if (g == null) return null;
        BlockPos top = march(level, g, 0, 1, 0, 90);
        if (top == null || top.getY() <= g.getY() + 3) return null;
        BlockPos under = top.below();
        return level.getBlockState(under).isAir() ? new BlockPos[]{g, under} : null;
    }

    /** The air just over the floor in a column, looked for from {@code guess} up and down; null if there is none. */
    @Nullable
    private static BlockPos ground(ServerLevel level, int x, int guess, int z, int range) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = range; dy >= -range; dy--) {
            int y = guess + dy;
            if (!level.getBlockState(p.set(x, y, z)).isAir()) continue;
            if (standable(level, p.set(x, y - 1, z))) return new BlockPos(x, y, z);
        }
        return null;
    }

    /** A block something can stand on. */
    private static boolean standable(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return !s.isAir() && s.getFluidState().isEmpty() && !s.is(Blocks.LIGHT) && s.isFaceSturdy(level, p, Direction.UP);
    }

    private boolean inPool(int x, int z) {
        for (Space s : layout.spaces()) {
            if (s.shape() == SpaceShape.POOL && s.contains(x - ox, s.center().getY() - 1, z - oz, 1.35)) return true;
        }
        return false;
    }

    /** True if something standing at a spot stays out of the way: off the passages, clear of the arrival and the boss. */
    private boolean open(BlockPos p, double gap) {
        if (near(arrival, p.getX(), p.getY(), p.getZ(), 3.5) || near(lair, p.getX(), p.getY(), p.getZ(), 4.5)) return false;
        return clearOfPaths(p, gap);
    }

    private boolean clearOfPaths(BlockPos p, double gap) {
        double g2 = gap * gap;
        for (int[] q : paths) {
            if (Math.abs(q[2] - p.getY()) > 6) continue;
            double dx = q[0] - p.getX(), dz = q[1] - p.getZ();
            if (dx * dx + dz * dz < g2) return false;
        }
        return true;
    }

    private static boolean near(int[] at, int x, int y, int z, double d) {
        if (Math.abs(at[2] - y) > 6) return false;
        double dx = at[0] - x, dz = at[1] - z;
        return dx * dx + dz * dz < d * d;
    }

    // ================================================================== blocks

    /** True for a plain solid block of a wall, floor or roof (one a light or a vein may be set into). */
    private static boolean rock(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return !s.isAir() && s.getFluidState().isEmpty() && !s.hasBlockEntity() && !s.is(Blocks.LIGHT) && s.isSolidRender(level, p);
    }

    /** Sets the realm's light into a wall (never into a walkway: there is always something over a wall). */
    private void wallLight(ServerLevel level, BlockPos p) {
        if (rock(level, p) && !level.getBlockState(p.above()).isAir()) level.setBlock(p, realm.light, FLAGS);
    }

    private static boolean airy(BlockState s) {
        return s.isAir() || s.is(Blocks.LIGHT);
    }

    private static void fill(ServerLevel level, BlockPos p, BlockState light) {
        if (level.getBlockState(p).isAir()) level.setBlock(p, light, FLAGS);
    }

    private void placeIfAir(ServerLevel level, BlockPos p, BlockState state) {
        if (!level.getBlockState(p).isAir()) return;
        level.setBlock(p, state, FLAGS);
        if (DungeonBuilder.joins(state)) b.join(p);
    }

    /** Sets something on the face of a block that looks toward a point (an amethyst cluster on a wall, say). */
    private void attach(ServerLevel level, BlockPos hit, BlockPos from, BlockState state) {
        if (!rock(level, hit)) return;
        Direction f = face(level, hit, from);
        if (f == null) return;
        BlockState s = state.setValue(BlockStateProperties.FACING, f);
        if (s.canSurvive(level, hit.relative(f))) placeIfAir(level, hit.relative(f), s);
    }

    /** The open face of a block nearest a point, or null if it has none. */
    @Nullable
    private static Direction face(ServerLevel level, BlockPos hit, BlockPos from) {
        Direction best = null;
        double bestD = Double.MAX_VALUE;
        for (Direction d : Direction.values()) {
            BlockPos q = hit.relative(d);
            if (!level.getBlockState(q).isAir()) continue;
            double dist = q.distSqr(from);
            if (dist < bestD) {
                bestD = dist;
                best = d;
            }
        }
        return best;
    }

    /** Walks from {@code from} in a direction and returns the first block that is not air (or invisible light), or null. */
    @Nullable
    private static BlockPos march(ServerLevel level, BlockPos from, float dx, float dy, float dz, int max) {
        for (int i = 1; i <= max; i++) {
            BlockPos p = BlockPos.containing(from.getX() + 0.5 + dx * i, from.getY() + 0.5 + dy * i, from.getZ() + 0.5 + dz * i);
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && !s.is(Blocks.LIGHT)) return p;
        }
        return null;
    }
}
