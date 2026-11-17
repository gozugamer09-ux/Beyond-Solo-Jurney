package com.beyondsolo.depths;

import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.gate.DungeonLayout;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateTheme;
import com.beyondsolo.gate.RewardCacheBlockEntity;
import com.beyondsolo.registry.ModBlocks;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Builds the depths into a freshly carved cave (the last step of {@code gate.DungeonBuilder}): the hidden wall across
 * the way into the hidden chamber, the chamber's dressing (a vault's gilded plinth and cache, a shrine's altar, a
 * lair's bones), and each trap room's core, vents or loose ceiling. Where things are follows from the seed and the
 * plan alone, so the services find them again without anything saved ({@link #sealMouth}, {@link #ventSpots}).
 */
public final class DepthsBuild {
    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    /** Where a tunnel leaves a room: just past the room's rough edge, and the way out. World positions. */
    public record Mouth(Vec3 center, Vec3 dir, float radius) {}

    public static void build(ServerLevel level, GateInstance g) {
        GateDepths d = g.depths();
        DungeonLayout layout = g.layout();
        RandomSource r = new LegacyRandomSource(g.seed() ^ 0x4445505448533131L);
        HiddenKind hidden = d.hidden(g);
        DungeonLayout.Room room = layout.hidden();
        if (hidden != null && room != null) {
            seal(level, g, r);
            dressHidden(level, g, room, hidden, r);
        }
        d.traps(g).forEach((index, kind) -> dressTrap(level, g, layout.rooms().get(index), kind, r));
    }

    // ------------------------------------------------------------------ mouths and walls

    /** Where {@code t} leaves {@code room} (either end of it). */
    public static Mouth mouth(GateInstance g, DungeonLayout.Tunnel t, DungeonLayout.Room room) {
        boolean forward = t.from() == room.index();
        double len = Math.sqrt(t.start().distSqr(t.end()));
        int steps = Math.max(16, (int) (len * 6));
        double u0 = forward ? 1 : 0;
        for (int i = 0; i <= steps; i++) {
            double u = forward ? i / (double) steps : 1 - i / (double) steps;
            // just past the furthest the room's noisy edge reaches (its shape is less than 1.21 inside)
            if (!DungeonLayout.within(DungeonLayout.along(t, u), room, 1.12)) {
                u0 = u;
                break;
            }
        }
        double[] a = DungeonLayout.along(t, Mth.clamp(u0 - 0.02, 0, 1));
        double[] b = DungeonLayout.along(t, Mth.clamp(u0 + 0.02, 0, 1));
        Vec3 dir = new Vec3(b[0] - a[0], b[1] - a[1], b[2] - a[2]).normalize();
        if (!forward) dir = dir.scale(-1);
        double[] c = DungeonLayout.along(t, u0);
        BlockPos o = g.origin();
        return new Mouth(new Vec3(c[0] + o.getX(), c[1] + o.getY(), c[2] + o.getZ()), dir, t.radius());
    }

    /** The mouth of the way into the hidden chamber (where its wall stands), or null. */
    @Nullable
    public static Mouth sealMouth(GateInstance g) {
        DungeonLayout.Tunnel t = g.layout().hiddenTunnel();
        if (t == null) return null;
        return mouth(g, t, g.layout().rooms().get(t.from()));
    }

    /**
     * Fills the open part of a slab across a tunnel at a mouth: blocks within {@code radius} of its middle and
     * {@code half} of its plane. Returns what was placed.
     */
    static List<BlockPos> fillSlab(ServerLevel level, Mouth m, float radius, float half, Function<BlockPos, BlockState> what, Predicate<BlockState> over) {
        List<BlockPos> out = new ArrayList<>();
        int reach = Mth.ceil(radius + half + 1);
        BlockPos c = BlockPos.containing(m.center());
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = -reach; x <= reach; x++) {
            for (int y = -reach; y <= reach; y++) {
                for (int z = -reach; z <= reach; z++) {
                    p.set(c.getX() + x, c.getY() + y, c.getZ() + z);
                    Vec3 off = Vec3.atCenterOf(p).subtract(m.center());
                    double along = off.dot(m.dir());
                    if (Math.abs(along) > half) continue;
                    if (off.subtract(m.dir().scale(along)).length() > radius) continue;
                    if (!over.test(level.getBlockState(p))) continue;
                    BlockPos at = p.immutable();
                    level.setBlock(at, what.apply(at), FLAGS);
                    out.add(at);
                }
            }
        }
        return out;
    }

    /** Open, or nothing that matters: air, the invisible lights, lichen and the like. */
    static boolean open(BlockState s) {
        return s.isAir() || s.is(Blocks.LIGHT) || s.canBeReplaced();
    }

    /** The hidden wall: a plug of rock dressed as the cave's own walls, flush with the cavern's side. */
    static void seal(ServerLevel level, GateInstance g, RandomSource r) {
        Mouth m = sealMouth(g);
        if (m == null) return;
        GateTheme theme = g.theme();
        fillSlab(level, m, m.radius() + 1.1f, 1.25f, at -> sealState(theme, r), DepthsBuild::open);
    }

    /** A hidden-wall block that passes for one of the theme's walls. */
    static BlockState sealState(GateTheme theme, RandomSource r) {
        HiddenSealBlock.Look look = null;
        for (int i = 0; i < 4 && look == null; i++) look = HiddenSealBlock.Look.of(theme.wall(r));
        if (look == null) look = HiddenSealBlock.Look.of(theme.mainWall());
        if (look == null) look = HiddenSealBlock.Look.STONE;
        return DepthsModule.HIDDEN_SEAL.get().defaultBlockState().setValue(HiddenSealBlock.LOOK, look);
    }

    // ------------------------------------------------------------------ the hidden chamber

    /** The rank of a hidden cache: one above the gate's (an S gate's stays S). */
    public static PlayerRank cacheRank(GateInstance g, HiddenKind kind) {
        if (kind == HiddenKind.SHRINE) return g.rank();
        return PlayerRank.values()[Math.min(PlayerRank.values().length - 1, g.rank().tier() + 1)];
    }

    /** The middle of a room's floor (the air block standing on it), in the world. */
    public static BlockPos floorCenter(ServerLevel level, GateInstance g, DungeonLayout.Room room) {
        BlockPos c = g.world(room.center());
        return floorAt(level, c.getX(), c.getZ(), g.world(new BlockPos(0, room.floorY(), 0)).getY());
    }

    /** The first open block standing on solid floor at a column, searched around {@code y}. */
    public static BlockPos floorAt(ServerLevel level, int x, int z, int y) {
        for (int dy = 3; dy >= -4; dy--) {
            BlockPos q = new BlockPos(x, y + dy, z);
            if (open(level.getBlockState(q)) && !open(level.getBlockState(q.below()))) return q;
        }
        return new BlockPos(x, y, z);
    }

    static void dressHidden(ServerLevel level, GateInstance g, DungeonLayout.Room room, HiddenKind kind, RandomSource r) {
        BlockPos center = floorCenter(level, g, room);
        GateTheme theme = g.theme();
        switch (kind) {
            case VAULT -> {
                // a gilded plinth with the cache on it, candles around, chains with lanterns from the roof
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        BlockPos p = center.offset(x, -1, z);
                        level.setBlock(p, (x == 0 && z == 0) ? Blocks.GILDED_BLACKSTONE.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), FLAGS);
                    }
                }
                placeCache(level, center, cacheRank(g, kind), -1);
                ring(level, center, 3.2f, 8, r, at -> Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 1 + r.nextInt(4)).setValue(CandleBlock.LIT, true));
                for (int i = 0; i < 3; i++) {
                    BlockPos at = scatter(level, g, room, center, r, 0.75f);
                    if (at != null) level.setBlock(at.below(), Blocks.RAW_GOLD_BLOCK.defaultBlockState(), FLAGS);
                }
                hang(level, g, room, center, r, 5, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            }
            case SHRINE -> {
                // an altar of pale stone under soul light, four columns round it, the cache before it
                for (int x = -2; x <= 2; x++) {
                    for (int z = -2; z <= 2; z++) level.setBlock(center.offset(x, -1, z), (Math.abs(x) == 2 || Math.abs(z) == 2)
                            ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : Blocks.CALCITE.defaultBlockState(), FLAGS);
                }
                level.setBlock(center, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState(), FLAGS);
                level.setBlock(center.above(), Blocks.SOUL_LANTERN.defaultBlockState(), FLAGS);
                for (int i = 0; i < 4; i++) {
                    Direction dir = Direction.from2DDataValue(i);
                    Direction side = dir.getClockWise();
                    BlockPos base = center.relative(dir, 3).relative(side, 3);
                    for (int k = 0; k < 3; k++) placeIfOpen(level, base.above(k), Blocks.QUARTZ_PILLAR.defaultBlockState());
                    placeIfOpen(level, base.above(3), Blocks.SOUL_CAMPFIRE.defaultBlockState());
                }
                BlockPos before = floorAt(level, center.getX() + 2, center.getZ(), center.getY());
                placeCache(level, before, cacheRank(g, kind), -1);
                ring(level, center, 4.5f, 10, r, at -> Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 2 + r.nextInt(3)).setValue(CandleBlock.LIT, true));
            }
            case LAIR -> {
                // bones, cobwebs and skulls; the guardians come when someone steps in ({@link HiddenRooms})
                for (int i = 0; i < 14; i++) {
                    BlockPos at = scatter(level, g, room, center, r, 0.9f);
                    if (at == null) continue;
                    int roll = r.nextInt(10);
                    BlockState s = roll < 4 ? Blocks.BONE_BLOCK.defaultBlockState()
                            : roll < 7 ? Blocks.COBWEB.defaultBlockState()
                            : Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, r.nextInt(16));
                    level.setBlock(at, s, FLAGS);
                }
                hang(level, g, room, center, r, 4, Blocks.COBWEB.defaultBlockState());
                level.setBlock(center.below(), theme.accent(r), FLAGS);
            }
            case SEALED -> dressSealed(level, g, room, center, r);
        }
    }

    // ------------------------------------------------------------------ the sealed door's chamber

    /** Where a sealed door stands and the way it faces (towards whoever comes in). */
    public record Door(BlockPos pos, Direction facing) {}

    /** The way into the sealed door's chamber, flat: from where its passage bends towards the chamber's middle. */
    private static Vec3 inward(GateInstance g, DungeonLayout.Room room) {
        DungeonLayout.Tunnel t = g.layout().hiddenTunnel();
        BlockPos c = g.world(room.center());
        Vec3 in = t == null ? Vec3.ZERO : Vec3.atCenterOf(c).subtract(Vec3.atCenterOf(g.world(t.bend()))).multiply(1, 0, 1);
        return in.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : in.normalize();
    }

    /**
     * Where the {@code index}th sealed door stands ({@code awakening.AwakeningService}): at the far end of the chamber from
     * the way in, on the dais, facing back towards the way in. More than one (a party of players without the System)
     * stand side by side, four blocks apart.
     */
    public static Door sealedDoor(ServerLevel level, GateInstance g, int index) {
        DungeonLayout.Room room = g.layout().hidden();
        BlockPos c = g.world(room.center());
        Vec3 in = inward(g, room);
        Vec3 side = new Vec3(-in.z, 0, in.x);
        int slot = (index + 1) / 2 * (index % 2 == 1 ? 1 : -1);
        double along = room.reach() * 0.42, across = slot * 4.0;
        int x = Mth.floor(c.getX() + 0.5 + in.x * along + side.x * across);
        int z = Mth.floor(c.getZ() + 0.5 + in.z * along + side.z * across);
        BlockPos at = floorAt(level, x, z, floorCenter(level, g, room).getY());
        return new Door(at, Direction.getNearest(-in.x, 0, -in.z));
    }

    /**
     * The sealed door's chamber, dressed like the temple behind it: a dark tiled floor with a line of gold from the way
     * in to the dais where the door will stand, white columns down both sides with chains and soul lanterns from the
     * roof, and soul fire either side of the dais. The door itself only opens once the boss has fallen.
     */
    static void dressSealed(ServerLevel level, GateInstance g, DungeonLayout.Room room, BlockPos center, RandomSource r) {
        BlockPos c = g.world(room.center());
        Vec3 in = inward(g, room);
        Vec3 side = new Vec3(-in.z, 0, in.x);
        int rx = room.rx(), rz = room.rz();
        double dais = room.reach() * 0.42;
        for (int x = -rx; x <= rx; x++) {
            for (int z = -rz; z <= rz; z++) {
                double d = Math.sqrt(x * x / (double) (rx * rx) + z * z / (double) (rz * rz));
                if (d > 0.9) continue;
                BlockPos top = floorAt(level, c.getX() + x, c.getZ() + z, center.getY());
                if (Math.abs(top.getY() - center.getY()) > 2 || open(level.getBlockState(top.below()))) continue;
                double along = (x + 0.5) * in.x + (z + 0.5) * in.z, across = (x + 0.5) * side.x + (z + 0.5) * side.z;
                BlockState s;
                if (Math.abs(along - dais) < 1.6 && Math.abs(across) < 3.6) {
                    s = Math.abs(across) > 2.6 ? Blocks.GILDED_BLACKSTONE.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
                } else if (Math.abs(across) < 0.75 && along < dais) {
                    s = Blocks.GOLD_BLOCK.defaultBlockState();
                } else {
                    s = (Math.floorMod(x + z, 2) == 0 ? Blocks.POLISHED_DEEPSLATE : Blocks.DEEPSLATE_TILES).defaultBlockState();
                }
                level.setBlock(top.below(), s, FLAGS);
                // nothing left lying on the tiles (lichen, bone, a stalagmite stub)
                if (!level.getBlockState(top).isAir() && !level.getBlockState(top).is(Blocks.LIGHT)) level.setBlock(top, Blocks.CAVE_AIR.defaultBlockState(), FLAGS);
            }
        }
        // columns down both sides of the way to the dais, up to the roof
        for (int sideSign = -1; sideSign <= 1; sideSign += 2) {
            for (double along : new double[]{-room.reach() * 0.3, room.reach() * 0.12}) {
                double across = sideSign * rx * 0.62;
                int x = Mth.floor(c.getX() + 0.5 + in.x * along + side.x * across);
                int z = Mth.floor(c.getZ() + 0.5 + in.z * along + side.z * across);
                BlockPos base = floorAt(level, x, z, center.getY());
                for (int k = 0; k < room.ry() * 2 + 4; k++) {
                    BlockPos p = base.above(k);
                    if (k > 1 && !open(level.getBlockState(p))) break;
                    level.setBlock(p, k == 0 || k % 5 == 4 ? Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState() : Blocks.QUARTZ_PILLAR.defaultBlockState(), FLAGS);
                }
            }
            // soul fire beside the dais
            double across = sideSign * 5.5;
            int x = Mth.floor(c.getX() + 0.5 + in.x * dais + side.x * across);
            int z = Mth.floor(c.getZ() + 0.5 + in.z * dais + side.z * across);
            BlockPos fire = floorAt(level, x, z, center.getY());
            if (open(level.getBlockState(fire)) && open(level.getBlockState(fire.above()))) {
                level.setBlock(fire, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState(), FLAGS);
                level.setBlock(fire.above(), Blocks.SOUL_CAMPFIRE.defaultBlockState(), FLAGS);
            }
        }
        hang(level, g, room, center, r, 4, Blocks.CHAIN.defaultBlockState());
        hang(level, g, room, center, r, 3, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    /** A reward cache of the hidden chamber. {@code droppedAt} -1: already standing there. */
    static void placeCache(ServerLevel level, BlockPos at, PlayerRank rank, long droppedAt) {
        level.setBlock(at, ModBlocks.REWARD_CACHE.get().defaultBlockState(), 3);
        if (level.getBlockEntity(at) instanceof RewardCacheBlockEntity cache) cache.setupHidden(rank, droppedAt);
    }

    // ------------------------------------------------------------------ trap rooms

    /** Where a trap room's core stands: out towards the room's edge, on the far side from where the seed says. */
    public static BlockPos coreSpot(ServerLevel level, GateInstance g, DungeonLayout.Room room) {
        RandomSource r = new LegacyRandomSource(g.seed() * 131 + room.index() * 7L);
        BlockPos c = g.world(room.center());
        int floor = g.world(new BlockPos(0, room.floorY(), 0)).getY();
        float a = r.nextFloat() * Mth.TWO_PI;
        for (float k = 0.68f; k > 0.2f; k -= 0.08f) {
            BlockPos at = floorAt(level, c.getX() + Math.round(Mth.cos(a) * room.rx() * k), c.getZ() + Math.round(Mth.sin(a) * room.rz() * k), floor);
            if (open(level.getBlockState(at)) && open(level.getBlockState(at.above()))) return at;
        }
        return floorAt(level, c.getX(), c.getZ(), floor);
    }

    /** The vents of a vent trap: six around the room's middle (columns; the floor is found at each). */
    public static List<int[]> ventSpots(GateInstance g, DungeonLayout.Room room) {
        List<int[]> out = new ArrayList<>();
        BlockPos c = g.world(room.center());
        float turn = (g.seed() & 0xFF) / 255f * Mth.TWO_PI;
        for (int i = 0; i < 6; i++) {
            float a = turn + i * Mth.TWO_PI / 6;
            float k = i % 2 == 0 ? 0.55f : 0.32f;
            out.add(new int[]{c.getX() + Math.round(Mth.cos(a) * room.rx() * k), c.getZ() + Math.round(Mth.sin(a) * room.rz() * k)});
        }
        return out;
    }

    /** The floor block a vent of this gate is set into. */
    static BlockState ventBlock(GateTheme theme) {
        if (theme.element == DamageElement.FIRE) return Blocks.MAGMA_BLOCK.defaultBlockState();
        if (theme.element == DamageElement.POISON) return Blocks.SCULK.defaultBlockState();
        return Blocks.CRYING_OBSIDIAN.defaultBlockState();
    }

    static void dressTrap(ServerLevel level, GateInstance g, DungeonLayout.Room room, TrapKind kind, RandomSource r) {
        int floor = g.world(new BlockPos(0, room.floorY(), 0)).getY();
        switch (kind) {
            case VENTS -> {
                for (int[] v : ventSpots(g, room)) {
                    BlockPos at = floorAt(level, v[0], v[1], floor);
                    level.setBlock(at.below(), ventBlock(g.theme()), FLAGS);
                }
            }
            case ROCKFALL -> {
                // a cracked, sagging roof
                BlockPos c = g.world(room.center());
                hang(level, g, room, new BlockPos(c.getX(), floor, c.getZ()), r, 9, g.theme().mainWall());
            }
            case SPIKES -> {
                // the floor is scored: accent stone in the cracks
                BlockPos c = g.world(room.center());
                for (int i = 0; i < 10; i++) {
                    BlockPos at = scatter(level, g, room, new BlockPos(c.getX(), floor, c.getZ()), r, 0.8f);
                    if (at != null) level.setBlock(at.below(), g.theme().accent(r), FLAGS);
                }
            }
            default -> {
            }
        }
        // the core goes in last: a floor crack or a vent set into the floor right under it used to overwrite it
        if (kind.hasCore()) {
            BlockPos core = coreSpot(level, g, room);
            level.setBlock(core.below(), g.theme().accent(r), FLAGS);
            level.setBlock(core, DepthsModule.TRAP_CORE.get().defaultBlockState(), FLAGS);
        }
    }

    // ------------------------------------------------------------------ small helpers

    private static void placeIfOpen(ServerLevel level, BlockPos p, BlockState s) {
        if (open(level.getBlockState(p))) level.setBlock(p, s, FLAGS);
    }

    /** Things on the floor in a ring around a point. */
    private static void ring(ServerLevel level, BlockPos center, float radius, int n, RandomSource r, Function<BlockPos, BlockState> what) {
        for (int i = 0; i < n; i++) {
            float a = i * Mth.TWO_PI / n + r.nextFloat() * 0.3f;
            BlockPos at = floorAt(level, center.getX() + Math.round(Mth.cos(a) * radius), center.getZ() + Math.round(Mth.sin(a) * radius), center.getY());
            if (open(level.getBlockState(at)) && level.getBlockState(at).isAir()) level.setBlock(at, what.apply(at), FLAGS);
        }
    }

    /** A free floor spot in a room, or null. */
    @Nullable
    private static BlockPos scatter(ServerLevel level, GateInstance g, DungeonLayout.Room room, BlockPos center, RandomSource r, float spread) {
        for (int attempt = 0; attempt < 6; attempt++) {
            float a = r.nextFloat() * Mth.TWO_PI, d = (0.3f + r.nextFloat() * 0.7f) * spread;
            BlockPos at = floorAt(level, center.getX() + Math.round(Mth.cos(a) * d * room.rx()), center.getZ() + Math.round(Mth.sin(a) * d * room.rz()), center.getY());
            if (level.getBlockState(at).isAir() && !level.getBlockState(at.below()).is(Blocks.LIGHT)) return at;
        }
        return null;
    }

    /** Things hanging from the roof (a column of {@code s} from the ceiling, or one block if it hangs by itself). */
    private static void hang(ServerLevel level, GateInstance g, DungeonLayout.Room room, BlockPos center, RandomSource r, int n, BlockState s) {
        for (int i = 0; i < n; i++) {
            float a = r.nextFloat() * Mth.TWO_PI, d = r.nextFloat() * 0.65f;
            BlockPos p = new BlockPos(center.getX() + Math.round(Mth.cos(a) * d * room.rx()), center.getY() + 2, center.getZ() + Math.round(Mth.sin(a) * d * room.rz()));
            for (int up = 1; up < room.ry() * 2 + 4; up++) {
                BlockPos q = p.above(up);
                if (open(level.getBlockState(q))) continue;
                int len = s.getBlock() instanceof LanternBlock || s.is(Blocks.COBWEB) ? 1 : 1 + r.nextInt(3);
                for (int k = 1; k <= len; k++) placeIfOpen(level, q.below(k), s);
                break;
            }
        }
    }

    private DepthsBuild() {}
}
