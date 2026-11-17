package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.DungeonLayout;
import com.beyondsolo.gate.DungeonLayout.Room;
import com.beyondsolo.gate.DungeonLayout.Space;
import com.beyondsolo.gate.DungeonLayout.SpaceShape;
import com.beyondsolo.gate.DungeonLayout.Tunnel;
import com.beyondsolo.gate.DungeonShape;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateRealm;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.gate.GateTheme;
import com.beyondsolo.gate.GateWiper;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The dungeons behind the gates: every realm in every shape plans a whole dungeon (one boss, every room reachable,
 * nothing running into anything, inside its slot and the rock), caves of the original kind come out exactly as they
 * did before realms, and a dungeon of each shape is carved, walked and wiped away again.
 * Run with {@code ./gradlew runGameTestServer}.
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DungeonGameTests {
    private static final String ARENA = "evaluation_zone";
    /** Seeds tried for every realm and shape. */
    private static final int SEEDS = 10;

    /**
     * Fingerprints of the original caves of each rank (E, D, C, B, A, S and the awakening trial), 40 seeds each, taken
     * from the cave planner as it was before realms and shapes. Caves carved back then are still standing in old
     * worlds, and players arrive in them by their plan.
     */
    private static final long[] CLASSIC = {-8412963397973536926L, -6918842222607439227L, 241880292254044398L, 3550771947339347707L,
            7190791709917790386L, -5948811242513304154L, -2467833532430319719L};

    // ------------------------------------------------------------------ plans

    @GameTest(template = ARENA)
    public static void classicCavesComeOutAsBefore(GameTestHelper helper) {
        for (GateTheme theme : GateTheme.values()) {
            long h = 17;
            for (int i = 0; i < 40; i++) h = fingerprint(h, classic(i * 0x9E3779B97F4A7C15L + 12345L, theme));
            helper.assertTrue(h == CLASSIC[theme.ordinal()], "the " + theme + " caves are laid out as they were (" + h + ")");
        }
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void everyRealmAndShapePlansAWholeDungeon(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        int plans = 0;
        for (GateRealm realm : GateRealm.values()) {
            for (DungeonShape shape : DungeonShape.values()) {
                for (int i = 0; i < SEEDS; i++) {
                    PlayerRank rank = PlayerRank.values()[realm.minRank.ordinal() + i % (realm.maxRank.ordinal() - realm.minRank.ordinal() + 1)];
                    long seed = (realm.ordinal() * 131L + shape.ordinal()) * 7919L + i * 0x5851F42D4C957F2DL;
                    DungeonLayout a = DungeonLayout.plan(seed, GateTheme.of(rank), realm, shape);
                    DungeonLayout b = DungeonLayout.plan(seed, GateTheme.of(rank), realm, shape);
                    String where = realm.id() + "/" + shape.id() + " " + rank + " seed " + seed + ": ";
                    if (!a.rooms().equals(b.rooms()) || !a.tunnels().equals(b.tunnels()) || !a.spaces().equals(b.spaces())) {
                        problems.add(where + "the same seed planned two different dungeons");
                    }
                    for (String p : problems(a)) problems.add(where + p);
                    plans++;
                }
            }
        }
        if (!problems.isEmpty()) {
            helper.fail(problems.size() + " problem(s) in " + plans + " plans:\n  " + String.join("\n  ", problems.subList(0, Math.min(20, problems.size()))));
        }
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void gatesPickManyRealmsAndShapes(GameTestHelper helper) {
        for (PlayerRank rank : PlayerRank.values()) {
            Set<GateRealm> seen = EnumSet.noneOf(GateRealm.class);
            for (long seed = 0; seed < 4000; seed++) {
                GateRealm realm = GateRealm.forSeed(rank, seed);
                helper.assertTrue(realm.livesAt(rank) && realm != GateRealm.THRESHOLD, realm + " is not a realm of rank " + rank);
                seen.add(realm);
            }
            helper.assertTrue(seen.size() >= 4 && seen.containsAll(GateRealm.forRank(rank)),
                    "every realm of rank " + rank + " turns up: " + seen + " of " + GateRealm.forRank(rank));
        }
        for (GateRealm realm : GateRealm.values()) {
            Set<DungeonShape> seen = EnumSet.noneOf(DungeonShape.class);
            for (long seed = 0; seed < 2000; seed++) seen.add(realm.shapeFor(seed));
            helper.assertTrue(seen.equals(realm.shapes()), realm + " takes each of its shapes and no other: " + seen);
        }
        Set<DungeonShape> all = EnumSet.noneOf(DungeonShape.class);
        for (GateRealm realm : GateRealm.values()) if (realm != GateRealm.THRESHOLD) all.addAll(realm.shapes());
        helper.assertTrue(all.size() == DungeonShape.values().length, "every shape is some realm's: " + all);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void realmsFavourTheirBosses(GameTestHelper helper) {
        int hive = 0, plain = 0;
        Set<BossKind> kinds = EnumSet.noneOf(BossKind.class);
        for (long seed = 0; seed < 3000; seed++) {
            BossKind k = BossKind.forSeed(PlayerRank.C, seed, GateRealm.CHITIN_HIVE);
            helper.assertTrue(k.livesAt(PlayerRank.C), k + " does not fight at rank C");
            kinds.add(k);
            if (k == BossKind.BROODMOTHER) hive++;
            if (BossKind.forSeed(PlayerRank.C, seed) == BossKind.BROODMOTHER) plain++;
        }
        // at rank C a hive's broodmother is 21 of 42 weights (brute, knight and magus 3 each, the alpha 12)
        helper.assertTrue(hive > plain * 2 && hive > 1300, "about half of all hives end with their broodmother: " + hive + " to " + plain);
        for (BossKind k : BossKind.values()) {
            if (k.livesAt(PlayerRank.C)) helper.assertTrue(kinds.contains(k), "any boss of the rank can still wait there: " + k);
        }
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void realmAndShapeAreSaved(GameTestHelper helper) {
        GateInstance g = new GateInstance(UUID.randomUUID(), PlayerRank.B, 99L, 5, Level.OVERWORLD, BlockPos.ZERO);
        g.setDungeon(GateRealm.STORMSPIRE, DungeonShape.SPIRE);
        GateInstance back = GateInstance.load(g.save());
        helper.assertTrue(back.realm() == GateRealm.STORMSPIRE && back.shape() == DungeonShape.SPIRE && !back.legacy(), "kept");
        helper.assertTrue(back.layout().rooms().equals(g.layout().rooms()), "and so is its plan");

        // a cave carved before realms existed keeps the look it was carved with
        CompoundTag old = g.save();
        old.remove("realm");
        old.remove("shape");
        old.putBoolean("built", true);
        GateInstance legacy = GateInstance.load(old);
        helper.assertTrue(legacy.legacy() && legacy.realm() == GateRealm.classic(PlayerRank.B) && legacy.shape() == DungeonShape.CAVERNS,
                "an old cave stays a classic cave");
        helper.assertTrue(legacy.bossKind() == BossKind.forSeed(PlayerRank.B, 99L) && legacy.element() == GateTheme.B.element, "with its old boss and element");
        helper.assertTrue(legacy.layout().rooms().equals(classic(99L, GateTheme.B).rooms()), "and its old plan");
        helper.assertTrue(GateInstance.load(legacy.save()).legacy(), "saved again, it stays one");

        // one that was never carved takes a realm from its seed like any other
        old.putBoolean("built", false);
        GateInstance fresh = GateInstance.load(old);
        helper.assertTrue(!fresh.legacy() && fresh.realm() == GateRealm.forSeed(PlayerRank.B, 99L)
                && fresh.shape() == fresh.realm().shapeFor(99L), "an uncarved old gate leads to a realm");
        helper.succeed();
    }

    // ------------------------------------------------------------------ carving, walking, wiping

    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void cavernsAreCarvedWalkedAndWiped(GameTestHelper helper) {
        carveWalkWipe(helper, PlayerRank.D, GateRealm.ROOTBOUND_DEN, DungeonShape.CAVERNS);
    }

    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void labyrinthIsCarvedWalkedAndWiped(GameTestHelper helper) {
        carveWalkWipe(helper, PlayerRank.C, GateRealm.BONE_CATACOMBS, DungeonShape.LABYRINTH);
    }

    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void gauntletIsCarvedWalkedAndWiped(GameTestHelper helper) {
        carveWalkWipe(helper, PlayerRank.A, GateRealm.IRONBOUND_KEEP, DungeonShape.GAUNTLET);
    }

    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void spireIsCarvedWalkedAndWiped(GameTestHelper helper) {
        carveWalkWipe(helper, PlayerRank.S, GateRealm.STORMSPIRE, DungeonShape.SPIRE);
    }

    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void descentIsCarvedWalkedAndWiped(GameTestHelper helper) {
        carveWalkWipe(helper, PlayerRank.B, GateRealm.CRYSTAL_SEAM, DungeonShape.DESCENT);
    }

    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void chasmIsCarvedWalkedAndWiped(GameTestHelper helper) {
        carveWalkWipe(helper, PlayerRank.A, GateRealm.INFERNAL_CITADEL, DungeonShape.CHASM);
    }

    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void hollowIsCarvedWalkedAndWiped(GameTestHelper helper) {
        carveWalkWipe(helper, PlayerRank.C, GateRealm.SPOREFALL_DEEP, DungeonShape.HOLLOW);
    }

    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void hiveIsCarvedWalkedAndWiped(GameTestHelper helper) {
        carveWalkWipe(helper, PlayerRank.B, GateRealm.CHITIN_HIVE, DungeonShape.HIVE);
    }

    /**
     * Opens a gate of the realm and shape, carves its dungeon, checks you can stand at the entrance, walk every
     * passage and find the boss in its room, then closes it and checks every block of it went back to rock.
     */
    private static void carveWalkWipe(GameTestHelper helper, PlayerRank rank, GateRealm realm, DungeonShape shape) {
        MinecraftServer server = helper.getLevel().getServer();
        // a fixed seed per shape, so a failure can be looked at again
        long seed = 0x5EEDL + shape.ordinal() * 7919L + realm.ordinal() * 131L;
        GateInstance g = GateData.get(server).create(UUID.randomUUID(), rank, seed, helper.getLevel().dimension(), helper.absolutePos(new BlockPos(3, 2, 3)));
        g.setDungeon(realm, shape);
        ServerLevel d = GateService.dungeon(server);
        helper.assertTrue(d != null, "there is somewhere to carve");
        GateService.buildNow(server, g);
        helper.assertTrue(g.built() && g.realm() == realm && g.shape() == shape, "built as asked");
        String what = realm.id() + "/" + shape.id() + " " + rank + " seed " + seed + ": ";

        BlockPos e = g.entrance();
        helper.assertTrue(low(d, e) && open(d, e.above()), what + "room to stand at the entrance " + e + around(d, e));
        helper.assertTrue(d.getBlockState(e.below()).isFaceSturdy(d, e.below(), Direction.UP), what + "ground at the entrance");
        helper.assertTrue(open(d, g.bossRoomCenter().above()) && open(d, g.bossRoomCenter().above(2)), what + "the boss's room is hollow");
        helper.assertTrue(g.bossSpawned() && g.boss() != null && d.getEntity(g.boss()) instanceof GateBossEntity, what + "the boss is waiting");

        DungeonLayout layout = g.layout();
        for (Tunnel t : layout.tunnels()) {
            int n = Math.max(8, (int) (t.length() * 2));
            for (int i = 0; i <= n; i++) {
                double[] at = DungeonLayout.along(t, i / (double) n);
                BlockPos p = g.world(BlockPos.containing(at[0], at[1], at[2]));
                helper.assertTrue(walkable(d, p, t.style() == DungeonLayout.Style.TUBE),
                        what + "the " + t.style().name().toLowerCase() + " from room " + t.from() + " to " + t.to() + " is blocked at " + p
                                + " (layout " + g.origin().subtract(p).multiply(-1).toShortString() + ")" + around(d, p));
            }
        }

        g.setState(GateInstance.State.CLOSED);
        helper.assertTrue(GateWiper.wipeNow(server, g), what + "wiped");
        // the builder walled the dungeon in rock first (the game-test world is open air), so any air left anywhere near
        // it is a part the wipe missed
        BlockPos o = g.origin();
        for (int[] b : layout.boxes(6)) {
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int x = b[0]; x <= b[3]; x++) {
                for (int y = b[1]; y <= b[4]; y++) {
                    for (int z = b[2]; z <= b[5]; z++) {
                        p.set(o.getX() + x, o.getY() + y, o.getZ() + z);
                        if (!d.getBlockState(p).isAir()) continue;
                        helper.fail(what + "air left after the wipe at " + p.toShortString() + " (layout " + x + " " + y + " " + z + ")");
                        return;
                    }
                }
            }
        }
        helper.succeed();
    }

    /** Nothing to bump into (air, a light, a cobweb, hanging roots...). */
    private static boolean open(ServerLevel d, BlockPos p) {
        BlockState s = d.getBlockState(p);
        return s.getCollisionShape(d, p).isEmpty() && s.getFluidState().isEmpty();
    }

    /** Something you stand in, not against: nothing, or something low (a carpet, a slab, a step, snow). */
    private static boolean low(ServerLevel d, BlockPos p) {
        BlockState s = d.getBlockState(p);
        var shape = s.getCollisionShape(d, p);
        return s.getFluidState().isEmpty() && (shape.isEmpty() || shape.max(net.minecraft.core.Direction.Axis.Y) <= 0.5);
    }

    /** Room for a player with their feet in {@code p}. */
    private static boolean stand(ServerLevel d, BlockPos p) {
        if (!low(d, p) || !open(d, p.above())) return false;
        var shape = d.getBlockState(p).getCollisionShape(d, p);
        // standing on a slab or a step the head reaches into the second block up
        return shape.isEmpty() || shape.max(net.minecraft.core.Direction.Axis.Y) <= 0.0625 || open(d, p.above(2));
    }

    /**
     * A tube's middle must be open; elsewhere (corridors with steps, stairs, bridges with an arch) a player must fit
     * on the path or right beside it, within a block of its height.
     */
    private static boolean walkable(ServerLevel d, BlockPos p, boolean tube) {
        if (tube) return open(d, p) || open(d, p.above()) || open(d, p.below());
        for (BlockPos c : new BlockPos[]{p, p.east(), p.west(), p.north(), p.south()}) {
            for (int dy = -1; dy <= 1; dy++) if (stand(d, c.above(dy))) return true;
        }
        return false;
    }

    /** What is around a spot, for a failure message. */
    private static String around(ServerLevel d, BlockPos p) {
        StringBuilder b = new StringBuilder(":");
        for (int dy = -1; dy <= 2; dy++) {
            b.append(" [").append(dy).append("]");
            for (BlockPos c : new BlockPos[]{p, p.east(), p.west(), p.north(), p.south()}) {
                String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(d.getBlockState(c.above(dy)).getBlock()).getPath();
                b.append(' ').append(name);
            }
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ what makes a plan whole

    /** The original cave of a rank (or the awakening trial's). */
    private static DungeonLayout classic(long seed, GateTheme theme) {
        if (theme == GateTheme.TRIAL) return DungeonLayout.plan(seed, theme);
        return DungeonLayout.plan(seed, theme, GateRealm.classic(theme.rank), DungeonShape.CAVERNS);
    }

    private static long fingerprint(long h, DungeonLayout l) {
        h = h * 31 + l.rooms().size();
        for (Room m : l.rooms()) {
            for (int v : new int[]{m.index(), m.center().getX(), m.center().getY(), m.center().getZ(), m.rx(), m.ry(), m.rz(), m.kind().ordinal()}) {
                h = h * 31 + v;
            }
        }
        h = h * 31 + l.tunnels().size();
        for (Tunnel t : l.tunnels()) {
            for (int v : new int[]{t.from(), t.to(), t.start().getX(), t.start().getY(), t.start().getZ(), t.bend().getX(), t.bend().getY(),
                    t.bend().getZ(), t.end().getX(), t.end().getY(), t.end().getZ(), Float.floatToIntBits(t.radius())}) {
                h = h * 31 + v;
            }
        }
        return h;
    }

    /** What is wrong with a plan (nothing, for a good one). */
    static List<String> problems(DungeonLayout l) {
        List<String> out = new ArrayList<>();
        List<Room> rooms = l.rooms();
        if (rooms.isEmpty() || rooms.get(0).kind() != DungeonLayout.Kind.ENTRANCE) out.add("it does not start at an entrance");
        if (rooms.size() > 64) out.add(rooms.size() + " rooms, more than a gate can keep track of");
        int bosses = 0;
        for (int i = 0; i < rooms.size(); i++) {
            Room r = rooms.get(i);
            if (r.index() != i) out.add("room " + i + " thinks it is room " + r.index());
            if (r.kind() == DungeonLayout.Kind.BOSS) bosses++;
            if (i > 0 && r.kind() == DungeonLayout.Kind.ENTRANCE) out.add("a second entrance, room " + i);
        }
        if (bosses != 1) {
            out.add(bosses + " boss rooms");
            return out;
        }
        Room boss = l.boss();
        for (Room r : rooms) {
            if (r != boss && r.rx() * r.rz() > boss.rx() * boss.rz()) out.add("room " + r.index() + " (" + r.kind() + ") is bigger than the boss's");
        }
        // nothing runs into anything
        for (int i = 0; i < rooms.size(); i++) {
            for (int j = i + 1; j < rooms.size(); j++) {
                if (DungeonLayout.overlaps(rooms.get(i), rooms.get(j), 1)) out.add("rooms " + i + " and " + j + " run into each other");
            }
        }
        // inside the rock and the slot
        for (Room r : rooms) {
            int lo = r.natural() ? r.center().getY() - r.ry() : r.floorY() - 1;
            if (lo < DungeonLayout.FLOOR_LIMIT || r.topY() > DungeonLayout.CEILING_LIMIT) out.add("room " + r.index() + " leaves the rock (" + lo + ".." + r.topY() + ")");
        }
        for (Space s : l.spaces()) {
            if (s.floorY() - 4 < 1 || s.topY() + 6 > 127) out.add("a " + s.shape() + " leaves the rock (" + s.floorY() + ".." + s.topY() + ")");
        }
        for (Tunnel t : l.tunnels()) {
            if (t.from() < 0 || t.from() >= rooms.size() || t.to() < 0 || t.to() >= rooms.size()) {
                out.add("a passage to a room that is not there: " + t.from() + " to " + t.to());
                return out;
            }
            for (int i = 0; i <= 16; i++) {
                double y = DungeonLayout.along(t, i / 16.0)[1];
                if (y < DungeonLayout.FLOOR_LIMIT || y > DungeonLayout.CEILING_LIMIT) {
                    out.add("the passage from " + t.from() + " to " + t.to() + " leaves the rock at y " + Mth.floor(y));
                    break;
                }
            }
        }
        int[] b = l.bounds();
        int half = GateInstance.SLOT_SPACING / 2 - 16;
        if (b[0] < -half || b[2] > half || b[1] < -half * 4 || b[3] > half * 4) out.add("it does not fit in its slot: " + b[0] + ".." + b[2] + ", " + b[1] + ".." + b[3]);
        // every room can be reached from the entrance, along passages or across the open spaces rooms open onto
        int[] group = new int[rooms.size()];
        for (int i = 0; i < group.length; i++) group[i] = i;
        for (Tunnel t : l.tunnels()) join(group, t.from(), t.to());
        for (Space s : l.spaces()) {
            if (s.shape() == SpaceShape.POOL) continue;
            int first = -1;
            for (Room r : rooms) {
                if (!opensOnto(r, s)) continue;
                if (first < 0) first = r.index();
                else join(group, first, r.index());
            }
        }
        for (Room r : rooms) {
            if (find(group, r.index()) != find(group, 0)) out.add("room " + r.index() + " (" + r.kind() + ") can't be reached");
        }
        return out;
    }

    /** True if some of a room's floor lies inside an open space (a hollow's camp, the entrance on a chasm's ledge). */
    private static boolean opensOnto(Room r, Space s) {
        int y = r.floorY() + 1;
        for (int i = -2; i <= 2; i++) {
            for (int k = -2; k <= 2; k++) {
                double x = r.center().getX() + r.rx() * 1.1 * i / 2, z = r.center().getZ() + r.rz() * 1.1 * k / 2;
                if (s.contains(x, y, z, 1.05)) return true;
            }
        }
        return false;
    }

    private static int find(int[] group, int i) {
        while (group[i] != i) i = group[i] = group[group[i]];
        return i;
    }

    private static void join(int[] group, int a, int b) {
        group[find(group, a)] = find(group, b);
    }

    private DungeonGameTests() {}
}
