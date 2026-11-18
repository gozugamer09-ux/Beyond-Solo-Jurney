package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.depths.DepthsBuild;
import com.beyondsolo.depths.DepthsTestAccess;
import com.beyondsolo.depths.HiddenKind;
import com.beyondsolo.depths.HiddenSealBlock;
import com.beyondsolo.depths.TrapKind;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.DungeonLayout;
import com.beyondsolo.gate.DungeonLayout.Room;
import com.beyondsolo.gate.DungeonLayout.Space;
import com.beyondsolo.gate.DungeonLayout.SpaceShape;
import com.beyondsolo.gate.DungeonLayout.Tunnel;
import com.beyondsolo.gate.DungeonShape;
import com.beyondsolo.gate.DungeonWalk;
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
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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

    /**
     * Traps go where they work, in every realm and shape: a lockdown only in a room rock shuts in (never a camp out in a
     * hollow, an island or a ledge over a shaft, which get another trap), every room of the original caves is one (so
     * their traps roll as before), and a trap forced by command finds a room in every dungeon.
     */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void trapsGoWhereTheyWork(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        int lockdowns = 0, openTraps = 0;
        for (GateRealm realm : GateRealm.values()) {
            if (realm.weight == 0) continue;
            for (DungeonShape shape : realm.shapes()) {
                for (int i = 0; i < SEEDS * 3; i++) {
                    PlayerRank rank = PlayerRank.values()[realm.minRank.ordinal() + i % (realm.maxRank.ordinal() - realm.minRank.ordinal() + 1)];
                    long seed = (realm.ordinal() * 131L + shape.ordinal()) * 104729L + i * 0x5851F42D4C957F2DL;
                    DungeonLayout l = DungeonLayout.plan(seed, GateTheme.of(rank), realm, shape);
                    String where = realm.id() + "/" + shape.id() + " " + rank + " seed " + seed + ": ";
                    if (shape == DungeonShape.CAVERNS) {
                        for (Room room : l.rooms()) {
                            if (room.kind() == DungeonLayout.Kind.CAVERN && !l.enclosed(room)) problems.add(where + "cavern " + room.index() + " can't be shut");
                        }
                    }
                    for (var e : DepthsTestAccess.rollTraps(seed, rank, l).entrySet()) {
                        Room room = l.rooms().get(e.getKey());
                        boolean shut = l.enclosed(room);
                        if (e.getValue() == TrapKind.LOCKDOWN) {
                            lockdowns++;
                            if (!shut) problems.add(where + "a lockdown in room " + room.index() + ", which can't be shut");
                        } else if (!shut) {
                            openTraps++;
                        }
                    }
                    for (TrapKind kind : TrapKind.values()) {
                        Room room = DepthsTestAccess.forcedTrapRoom(l, kind);
                        if (kind == TrapKind.LOCKDOWN ? room != null && !l.enclosed(room) : room == null) {
                            problems.add(where + "a forced " + kind.id() + " goes in " + (room == null ? "no room" : "room " + room.index() + ", which can't be shut"));
                        }
                    }
                }
            }
        }
        helper.assertTrue(lockdowns > 0 && openTraps > 0, "lockdowns still turn up (" + lockdowns + "), and open rooms get other traps (" + openTraps + ")");
        if (!problems.isEmpty()) {
            helper.fail(problems.size() + " problem(s):\n  " + String.join("\n  ", problems.subList(0, Math.min(20, problems.size()))));
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

    /**
     * A realm's creatures live there: of every gate's three to five kinds at least two are at home in the realm (when
     * its rank has two), one fights up close, all are the gate's own, and the realm's ruling affinity holds.
     */
    @GameTest(template = ARENA)
    public static void realmsFavourTheirCreatures(GameTestHelper helper) {
        for (GateRealm realm : GateRealm.values()) {
            if (realm.weight == 0) continue;
            for (PlayerRank rank : PlayerRank.values()) {
                if (rank.ordinal() < realm.minRank.ordinal() || rank.ordinal() > realm.maxRank.ordinal()) continue;
                long home = com.beyondsolo.enemy.EnemyRoster.pool(rank).stream()
                        .filter(e -> com.beyondsolo.enemy.Habitat.fits(e.type().get(), realm.habitats)).count();
                for (long seed = 0; seed < 40; seed++) {
                    GateInstance g = new GateInstance(UUID.randomUUID(), rank, seed * 7919L + realm.ordinal(), 5, Level.OVERWORLD, BlockPos.ZERO);
                    g.setDungeon(realm, realm.shapeFor(seed));
                    com.beyondsolo.enemy.EnemyRoster.Mix mix = g.mix();
                    String what = realm.id() + " " + rank + ": " + com.beyondsolo.enemy.EnemyRoster.describe(mix);
                    helper.assertTrue(mix.types().size() >= 3 && mix.types().size() <= 5, what + ": three to five kinds");
                    helper.assertTrue(mix.types().stream().allMatch(t -> com.beyondsolo.enemy.EnemyRoster.kindOf(t) != null), what + ": all the gate's own");
                    helper.assertTrue(mix.types().stream().anyMatch(com.beyondsolo.enemy.EnemyRoster::isFront), what + ": one fights up close");
                    long here = mix.types().stream().filter(t -> com.beyondsolo.enemy.Habitat.fits(t, realm.habitats)).count();
                    helper.assertTrue(here >= Math.min(2, home), what + ": at least two live in the realm (" + home + " could)");
                    if (realm.affinity != null) helper.assertTrue(mix.affinity() == realm.affinity, what + ": ruled by " + realm.affinity);
                }
            }
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
        old.remove("depths");
        old.putBoolean("built", true);
        GateInstance legacy = GateInstance.load(old);
        helper.assertTrue(legacy.legacy() && legacy.realm() == GateRealm.classic(PlayerRank.B) && legacy.shape() == DungeonShape.CAVERNS,
                "an old cave stays a classic cave");
        helper.assertTrue(legacy.bossKind() == BossKind.forSeed(PlayerRank.B, 99L) && legacy.element() == GateTheme.B.element, "with its old boss and element");
        helper.assertTrue(legacy.layout().rooms().equals(classic(99L, GateTheme.B).rooms()), "and its old plan");
        helper.assertTrue(GateInstance.load(legacy.save()).legacy(), "saved again, it stays one");

        // one carved after hidden chambers came but before realms keeps its chamber where it was carved
        GateInstance chambered = new GateInstance(UUID.randomUUID(), PlayerRank.B, 99L, 5, Level.OVERWORLD, BlockPos.ZERO);
        chambered.depths().setHidden(HiddenKind.VAULT);
        CompoundTag mid = chambered.save();
        mid.remove("realm");
        mid.remove("shape");
        mid.putBoolean("built", true);
        GateInstance between = GateInstance.load(mid);
        DungeonLayout carved = DungeonLayout.plan(99L, GateTheme.B, GateRealm.classic(PlayerRank.B), DungeonShape.CAVERNS, true);
        helper.assertTrue(between.legacy() && carved.hidden() != null && between.layout().rooms().equals(carved.rooms()),
                "a cave with a hidden chamber keeps it where it was carved");
        helper.assertTrue(carved.rooms().subList(0, carved.rooms().size() - 1).equals(classic(99L, GateTheme.B).rooms()), "and the rest as it always was");

        // one that was never carved takes a realm from its seed like any other
        old.putBoolean("built", false);
        GateInstance fresh = GateInstance.load(old);
        helper.assertTrue(!fresh.legacy() && fresh.realm() == GateRealm.forSeed(PlayerRank.B, 99L)
                && fresh.shape() == fresh.realm().shapeFor(99L), "an uncarved old gate leads to a realm");
        helper.succeed();
    }

    // ------------------------------------------------------------------ carving, walking, wiping

    /**
     * A gate's dungeon comes out the same, block for block, in whichever slot of the gate dimension it is carved: all of
     * it follows from its seed. Its rock was once shaped by noise laid over the world rather than the dungeon, so one
     * seed carved other walls, floors and ledges in every slot, and in some slots a room couldn't be reached on foot
     * (the gloomshade realm test below failed now and then, always with the same seed).
     */
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void aDungeonIsTheSameInAnySlot(GameTestHelper helper) {
        // that realm test's caverns, and a hollow (its hills, its lake and its columns are shaped by noise too)
        sameInAnySlot(helper, PlayerRank.B, GateRealm.GLOOMSHADE, DungeonShape.CAVERNS, 0xD00DL + GateRealm.GLOOMSHADE.ordinal() * 104729L);
        sameInAnySlot(helper, PlayerRank.C, GateRealm.SPOREFALL_DEEP, DungeonShape.HOLLOW, 0x5EEDL + DungeonShape.HOLLOW.ordinal() * 7919L);
        helper.succeed();
    }

    /** Carves the same gate twice, in two slots, and compares every block of the two. */
    private static void sameInAnySlot(GameTestHelper helper, PlayerRank rank, GateRealm realm, DungeonShape shape, long seed) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel d = GateService.dungeon(server);
        GateInstance[] twins = new GateInstance[2];
        for (int i = 0; i < 2; i++) {
            GateInstance g = GateData.get(server).create(UUID.randomUUID(), rank, seed, helper.getLevel().dimension(), helper.absolutePos(new BlockPos(3, 2, 3)));
            g.setDungeon(realm, shape);
            g.depths().setHidden(HiddenKind.VAULT);
            g.replan();
            for (Room room : g.layout().rooms()) {
                if (room.kind() == DungeonLayout.Kind.CAVERN && room.index() >= 2) g.depths().setTrap(room.index(), TrapKind.ROCKFALL);
            }
            GateService.buildNow(server, g);
            twins[i] = g;
        }
        GateInstance a = twins[0], b = twins[1];
        String what = realm.id() + "/" + shape.id() + " " + rank + " seed " + seed + " in slots " + a.slot() + " and " + b.slot() + ": ";
        helper.assertTrue(a.slot() != b.slot(), what + "two slots");
        int compared = 0, differ = 0;
        List<String> first = new ArrayList<>();
        BlockPos.MutableBlockPos pa = new BlockPos.MutableBlockPos(), pb = new BlockPos.MutableBlockPos();
        for (int[] box : a.layout().boxes(2)) {
            for (int x = box[0]; x <= box[3]; x++) {
                for (int y = box[1]; y <= box[4]; y++) {
                    for (int z = box[2]; z <= box[5]; z++) {
                        BlockState sa = d.getBlockState(pa.set(a.origin().getX() + x, y, a.origin().getZ() + z));
                        BlockState sb = d.getBlockState(pb.set(b.origin().getX() + x, y, b.origin().getZ() + z));
                        compared++;
                        if (sa == sb) continue;
                        if (differ++ < 5) first.add("layout " + x + " " + y + " " + z + ": " + sa + " / " + sb);
                    }
                }
            }
        }
        for (GateInstance g : twins) {
            g.setState(GateInstance.State.CLOSED);
            helper.assertTrue(GateWiper.wipeNow(server, g), what + "wiped");
        }
        helper.assertTrue(differ == 0, what + differ + " of " + compared + " blocks differ, as " + String.join("; ", first));
    }

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
     * Every realm is carved, walked and wiped as well, each in one of its own shapes: what dresses a dungeon differs
     * from realm to realm (pools, pillars, roots, coffins, railings), and none of it may stand in the way. Five at a
     * time, so the dungeons aren't all carved at once.
     */
    @GameTestGenerator
    public static List<TestFunction> everyRealmIsCarvedWalkedAndWiped() {
        List<TestFunction> out = new ArrayList<>();
        int n = 0;
        for (GateRealm realm : GateRealm.values()) {
            if (realm.weight == 0) continue;
            long seed = 0xD00DL + realm.ordinal() * 104729L;
            DungeonShape shape = realm.shapeFor(seed);
            int ranks = realm.maxRank.ordinal() - realm.minRank.ordinal() + 1;
            PlayerRank rank = PlayerRank.values()[realm.minRank.ordinal() + (int) (seed % ranks)];
            out.add(new TestFunction("beyondsolo_realms_" + n++ / 5, "dungeongametests.realm_" + realm.id(), BeyondSolo.MOD_ID + ":" + ARENA,
                    900, 0, true, helper -> carveWalkWipe(helper, rank, realm, shape, seed)));
        }
        // the awakening's sealed door behind the boss, in a dungeon of every shape (of rank D or higher, as it hides)
        for (DungeonShape shape : DungeonShape.values()) {
            GateRealm realm = null;
            for (GateRealm r : GateRealm.values()) {
                if (r.weight > 0 && r.maxRank.tier() >= 1 && r.shapes().contains(shape) && (realm == null || r.ordinal() % 3 == shape.ordinal() % 3)) realm = r;
            }
            if (realm == null) continue;
            GateRealm r = realm;
            PlayerRank rank = r.minRank.tier() >= 1 ? r.minRank : PlayerRank.D;
            long seed = 0x5EA1EDL + shape.ordinal() * 6151L;
            out.add(new TestFunction("beyondsolo_sealed_" + shape.ordinal() / 4, "dungeongametests.sealed_" + shape.id(), BeyondSolo.MOD_ID + ":" + ARENA,
                    900, 0, true, helper -> carveWalkWipe(helper, rank, r, shape, seed, HiddenKind.SEALED)));
        }
        if (EXPLORE) {
            for (GateRealm realm : GateRealm.values()) {
                if (realm.weight == 0) continue;
                for (DungeonShape shape : realm.shapes()) {
                    long seed = 0xE0E0L + realm.ordinal() * 7727L + shape.ordinal() * 15485863L;
                    PlayerRank rank = (realm.ordinal() + shape.ordinal()) % 2 == 0 ? realm.maxRank : realm.minRank;
                    out.add(new TestFunction("beyondsolo_explore_" + n++ / 6, "dungeongametests.explore_" + realm.id() + "_" + shape.id(),
                            BeyondSolo.MOD_ID + ":" + ARENA, 900, 0, true, helper -> carveWalkWipe(helper, rank, realm, shape, seed)));
                }
            }
        }
        return out;
    }

    private static final boolean EXPLORE = false;

    /**
     * Dungeons that came out cut off before the builder walked them to check its work ({@code gate.DungeonMend}), found
     * by carving every realm in every layout: two corridors from one room laid along one line, the higher one's floor
     * capping the lower (now kept out of the lower one's way as they are built); a tunnel into the hidden chamber
     * leaving its cave two blocks above the floor; a corridor's corner wider than the hidden wall across it, so the
     * chamber could be walked into round the wall. Now every passage is walked along, every room is walked to, and the
     * hidden chamber only past its wall.
     */
    @GameTestGenerator
    public static List<TestFunction> brokenDungeonsAreMended() {
        record Broken(GateRealm realm, DungeonShape shape, PlayerRank rank, long seed) {}
        List<Broken> broken = List.of(
                new Broken(GateRealm.MOSSWEPT_RUINS, DungeonShape.CAVERNS, PlayerRank.C, 6364136223846895973L),
                new Broken(GateRealm.CRYSTAL_SEAM, DungeonShape.CAVERNS, PlayerRank.B, 149330L),
                new Broken(GateRealm.MOSSWEPT_RUINS, DungeonShape.LABYRINTH, PlayerRank.C, 6364136223862381836L),
                new Broken(GateRealm.SUNSCORCHED_TOMB, DungeonShape.CAVERNS, PlayerRank.B, 110695L),
                new Broken(GateRealm.IRONBOUND_KEEP, DungeonShape.LABYRINTH, PlayerRank.A, 6364136223862443652L),
                new Broken(GateRealm.INFERNAL_CITADEL, DungeonShape.GAUNTLET, PlayerRank.A, 31159691L));
        List<TestFunction> out = new ArrayList<>();
        for (int i = 0; i < broken.size(); i++) {
            Broken k = broken.get(i);
            out.add(new TestFunction("beyondsolo_mended_" + i / 3, "dungeongametests.mended_" + k.realm().id() + "_" + k.shape().id(),
                    BeyondSolo.MOD_ID + ":" + ARENA, 900, 0, true, helper -> carveWalkWipe(helper, k.rank(), k.realm(), k.shape(), k.seed())));
        }
        return out;
    }

    /**
     * Opens a gate of the realm and shape, carves its dungeon, checks you can stand at the entrance, walk every
     * passage and find the boss in its room, then closes it and checks every block of it went back to rock.
     */
    private static void carveWalkWipe(GameTestHelper helper, PlayerRank rank, GateRealm realm, DungeonShape shape) {
        // a fixed seed per shape, so a failure can be looked at again
        carveWalkWipe(helper, rank, realm, shape, 0x5EEDL + shape.ordinal() * 7919L + realm.ordinal() * 131L);
    }

    private static void carveWalkWipe(GameTestHelper helper, PlayerRank rank, GateRealm realm, DungeonShape shape, long seed) {
        carveWalkWipe(helper, rank, realm, shape, seed, HiddenKind.VAULT);
    }

    private static void carveWalkWipe(GameTestHelper helper, PlayerRank rank, GateRealm realm, DungeonShape shape, long seed, HiddenKind kind) {
        MinecraftServer server = helper.getLevel().getServer();
        GateInstance g = GateData.get(server).create(UUID.randomUUID(), rank, seed, helper.getLevel().dimension(), helper.absolutePos(new BlockPos(3, 2, 3)));
        g.setDungeon(realm, shape);
        // every dungeon carved here hides a chamber, so one is carved, sealed and opened in every realm and layout
        // (a vault off a cavern, or the awakening's sealed door behind the boss)
        g.depths().setHidden(kind);
        g.replan();
        // a loose ceiling in every cavern a trap can go in: its rock hangs over the way, and must never be in it
        for (Room room : g.layout().rooms()) {
            if (room.kind() == DungeonLayout.Kind.CAVERN && room.index() >= 2) g.depths().setTrap(room.index(), TrapKind.ROCKFALL);
        }
        ServerLevel d = GateService.dungeon(server);
        helper.assertTrue(d != null, "there is somewhere to carve");
        GateService.buildNow(server, g);
        helper.assertTrue(g.built() && g.realm() == realm && g.shape() == shape, "built as asked");
        String what = realm.id() + "/" + shape.id() + " " + rank + " seed " + seed + (kind == HiddenKind.SEALED ? " (sealed door)" : "") + ": ";
        if (kind == HiddenKind.SEALED) {
            DungeonLayout.Tunnel way = g.layout().hiddenTunnel();
            helper.assertTrue(way != null && g.layout().rooms().get(way.from()).kind() == DungeonLayout.Kind.BOSS, what + "the door's chamber lies past the boss");
        }

        BlockPos e = g.entrance();
        helper.assertTrue(low(d, e) && open(d, e.above()), what + "room to stand at the entrance " + e + around(d, e));
        helper.assertTrue(d.getBlockState(e.below()).isFaceSturdy(d, e.below(), Direction.UP),
                what + "ground at the entrance " + e + " (layout " + e.subtract(g.origin()).toShortString() + ")" + around(d, e));
        helper.assertTrue(open(d, g.bossRoomCenter().above()) && open(d, g.bossRoomCenter().above(2)), what + "the boss's room is hollow");
        helper.assertTrue(g.bossSpawned() && g.boss() != null, what + "the boss was spawned");
        UUID bossId = g.boss();
        // its chunk is handed over to the world once the chunks around it are ready, which other threads finish in
        // their own time; until then it can't be found there
        if (!TestChunks.settle(d, () -> d.getEntity(bossId) instanceof GateBossEntity)) helper.fail(what + "the boss is waiting" + bossWhere(d, g, bossId));

        DungeonLayout layout = g.layout();
        for (Tunnel t : layout.tunnels()) {
            // the way into the hidden chamber is walled off: it's walked once the wall is down
            if (t == layout.hiddenTunnel()) continue;
            int n = Math.max(8, (int) (t.length() * 2));
            for (int i = 0; i <= n; i++) {
                double[] at = DungeonLayout.along(t, i / (double) n);
                BlockPos p = g.world(BlockPos.containing(at[0], at[1], at[2]));
                helper.assertTrue(walkable(d, p, t.style() == DungeonLayout.Style.TUBE),
                        what + "the " + t.style().name().toLowerCase() + " from room " + t.from() + " to " + t.to() + " is blocked at " + p
                                + " (layout " + g.origin().subtract(p).multiply(-1).toShortString() + ")" + around(d, p));
            }
        }

        // and on foot from the entrance, every room is reached: walking, stepping or jumping up a block, dropping
        // no further than a fall that doesn't hurt
        BlockPos o = g.origin();
        Set<Long> reached = onFoot(d, e, 800_000);
        // the builder walked it by the same rules when it checked its work
        Set<Long> walked = new DungeonWalk(d, false).reach(e, 800_000);
        helper.assertTrue(walked.equals(reached), what + "the builder walks it as this test does (" + walked.size() + " and " + reached.size() + " spots)");
        boolean[] got = new boolean[layout.rooms().size()];
        BlockPos[] nearest = new BlockPos[got.length];
        double[] best = new double[got.length];
        java.util.Arrays.fill(best, Double.MAX_VALUE);
        for (long k : reached) {
            BlockPos p = BlockPos.of(k);
            double lx = p.getX() - o.getX() + 0.5, ly = p.getY() - o.getY(), lz = p.getZ() - o.getZ() + 0.5;
            for (Room r : layout.rooms()) {
                if (got[r.index()] || r.kind() == DungeonLayout.Kind.HIDDEN) continue;
                if (r.contains(lx, ly, lz, 1.0)) {
                    got[r.index()] = true;
                    continue;
                }
                double dd = sq(lx - r.center().getX()) + sq(ly - r.floorY()) + sq(lz - r.center().getZ());
                if (dd < best[r.index()]) {
                    best[r.index()] = dd;
                    nearest[r.index()] = p;
                }
            }
        }
        for (Room r : layout.rooms()) {
            if (got[r.index()] || r.kind() == DungeonLayout.Kind.HIDDEN) continue;
            BlockPos near = nearest[r.index()];
            String why = what + "room " + r.index() + " (" + r.kind() + " " + r.shape() + " at layout " + r.center().toShortString()
                    + ", floor " + r.floorY() + ") can't be reached on foot from the entrance (" + reached.size() + " spots reached"
                    + (near == null ? "" : "; the nearest is layout " + near.subtract(o).toShortString() + around(d, near)) + ")";
            // the map goes to the log: a test's message also goes into a book, which takes no more than a page
            if (near != null) BeyondSolo.LOGGER.error("{}\n{}", why, map(d, near, reached));
            helper.fail(why);
            return;
        }

        // the hidden chamber can't be walked into past its wall, and can be once the wall is down
        Room hidden = layout.hidden();
        if (hidden != null) {
            BlockPos in = inside(reached, hidden, o);
            if (in != null) {
                String why = what + "the hidden chamber can be walked into without breaking its wall, at layout " + in.subtract(o).toShortString();
                BeyondSolo.LOGGER.error("{}\n{}", why, map(d, in, reached));
                helper.fail(why);
                return;
            }
            DepthsBuild.Mouth m = DepthsBuild.sealMouth(g);
            helper.assertTrue(m != null, what + "the hidden chamber's way in has a mouth");
            BlockPos mouth = BlockPos.containing(m.center());
            int reach = (int) Math.ceil(m.radius() + 3);
            // it passes for the realm's own walls
            Set<Block> walls = new java.util.HashSet<>();
            net.minecraft.util.RandomSource rs = net.minecraft.util.RandomSource.create(7);
            for (int y = d.getMinBuildHeight(); y < d.getMaxBuildHeight(); y++) for (int i = 0; i < 12; i++) walls.add(realm.wallAt(y, rs).getBlock());
            walls.add(realm.mainWall().getBlock());
            int seals = 0;
            for (BlockPos p : BlockPos.betweenClosed(mouth.offset(-reach, -reach, -reach), mouth.offset(reach, reach, reach))) {
                BlockState seal = d.getBlockState(p);
                if (!(seal.getBlock() instanceof HiddenSealBlock)) continue;
                Block mimic = seal.getValue(HiddenSealBlock.LOOK).mimic;
                helper.assertTrue(walls.contains(mimic), what + "the hidden wall passes for " + mimic + ", not one of the realm's walls");
                d.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                seals++;
            }
            helper.assertTrue(seals >= 4, what + "a wall stands across the hidden chamber's way in: " + seals);
            Set<Long> opened = onFoot(d, e, 800_000);
            if (inside(opened, hidden, o) == null) {
                String why = what + "the hidden chamber (at layout " + hidden.center().toShortString() + ", floor " + hidden.floorY()
                        + ") can't be walked into with its wall down; the mouth is at layout " + mouth.subtract(o).toShortString();
                BeyondSolo.LOGGER.error("{}\n{}", why, map(d, mouth, opened));
                helper.fail(why);
                return;
            }
        }

        g.setState(GateInstance.State.CLOSED);
        helper.assertTrue(GateWiper.wipeNow(server, g), what + "wiped");
        // the builder walled the dungeon in rock first (the game-test world is open air), so any air left anywhere
        // near it is a part the wipe missed
        for (int[] b : layout.boxes(6)) {
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int x = b[0]; x <= b[3]; x++) {
                for (int y = b[1]; y <= b[4]; y++) {
                    for (int z = b[2]; z <= b[5]; z++) {
                        p.set(o.getX() + x, o.getY() + y, o.getZ() + z);
                        if (!d.getBlockState(p).isAir()) continue;
                        helper.fail(what + "air left after the wipe at " + p.toShortString() + " (layout " + x + " " + y + " " + z + ")");
                    }
                }
            }
        }
        helper.succeed();
    }

    /** Where the boss should be and what the world knows of it, for a failure message. */
    private static String bossWhere(ServerLevel d, GateInstance g, UUID id) {
        BlockPos c = g.bossRoomCenter();
        net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(c);
        String known;
        try {
            java.lang.reflect.Field f = ServerLevel.class.getDeclaredField("entityManager");
            f.setAccessible(true);
            known = String.valueOf(((net.minecraft.world.level.entity.PersistentEntitySectionManager<?>) f.get(d)).isLoaded(id));
        } catch (ReflectiveOperationException | RuntimeException e) {
            known = "? (" + e + ")";
        }
        return " (boss " + id + " at " + c.toShortString() + ": chunk " + cp + " loaded " + (d.getChunkSource().getChunkNow(cp.x, cp.z) != null)
                + ", its creatures loaded " + d.areEntitiesLoaded(cp.toLong()) + ", ticking " + d.isPositionEntityTicking(c)
                + ", known to the world " + known + ", bosses in sight "
                + d.getEntitiesOfClass(GateBossEntity.class, new net.minecraft.world.phys.AABB(c).inflate(48)).size() + ")";
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

    /** Somewhere a player can stand: room for them, and something under their feet (or a slab, a step, snow in the block). */
    private static boolean footing(ServerLevel d, BlockPos p) {
        if (!stand(d, p)) return false;
        if (!d.getBlockState(p).getCollisionShape(d, p).isEmpty()) return true;
        return !d.getBlockState(p.below()).getCollisionShape(d, p.below()).isEmpty();
    }

    /**
     * Every spot a player gets to on foot from {@code from} (feet positions): walking, stepping or jumping up one block
     * where there's head room, or dropping off an edge no more than three blocks (a fall that doesn't hurt). No
     * swimming. At most {@code limit} spots.
     */
    private static Set<Long> onFoot(ServerLevel d, BlockPos from, int limit) {
        Set<Long> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> todo = new java.util.ArrayDeque<>();
        seen.add(from.asLong());
        todo.add(from);
        while (!todo.isEmpty() && seen.size() < limit) {
            BlockPos c = todo.poll();
            boolean jump = open(d, c.above(2));
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos h = c.relative(dir);
                List<BlockPos> next = new ArrayList<>(2);
                if (footing(d, h)) {
                    next.add(h);
                } else if (open(d, h) && open(d, h.above())) {
                    for (int k = 1; k <= 3; k++) {
                        BlockPos q = h.below(k);
                        if (footing(d, q)) {
                            next.add(q);
                            break;
                        }
                        if (!open(d, q)) break;
                    }
                }
                if (jump && footing(d, h.above())) next.add(h.above());
                for (BlockPos q : next) if (seen.add(q.asLong())) todo.add(q);
            }
        }
        return seen;
    }

    /** A spot reached on foot inside a room, or null. */
    private static BlockPos inside(Set<Long> reached, Room room, BlockPos o) {
        for (long k : reached) {
            BlockPos p = BlockPos.of(k);
            if (room.contains(p.getX() - o.getX() + 0.5, p.getY() - o.getY(), p.getZ() - o.getZ() + 0.5, 1.0)) return p;
        }
        return null;
    }

    private static double sq(double v) {
        return v * v;
    }

    /**
     * A map seen from above around a spot, for a failure message, one character a column: {@code *} the spot,
     * {@code o} a spot reached on foot within two blocks of its height, {@code ~} water or lava, {@code |} a wall, a
     * fence or bars, {@code s} a slab, {@code /} a step, {@code #} solid at head height, {@code .} floor at the spot's
     * height, {@code ,} floor a block or two down, and a blank for a drop.
     */
    private static String map(ServerLevel d, BlockPos at, Set<Long> reached) {
        StringBuilder b = new StringBuilder();
        for (int dz = -12; dz <= 12; dz++) {
            for (int dx = -16; dx <= 16; dx++) {
                BlockPos c = at.offset(dx, 0, dz);
                char ch;
                if (dx == 0 && dz == 0) ch = '*';
                else if (reachedNear(reached, c)) ch = 'o';
                else ch = cell(d, c);
                b.append(ch);
            }
            b.append('\n');
        }
        return b.toString();
    }

    private static boolean reachedNear(Set<Long> reached, BlockPos c) {
        for (int dy = -2; dy <= 2; dy++) if (reached.contains(c.above(dy).asLong())) return true;
        return false;
    }

    private static char cell(ServerLevel d, BlockPos c) {
        BlockState s = d.getBlockState(c);
        if (!s.getFluidState().isEmpty()) return '~';
        if (s.getBlock() instanceof net.minecraft.world.level.block.FenceBlock || s.getBlock() instanceof net.minecraft.world.level.block.WallBlock
                || s.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock) return '|';
        if (s.getBlock() instanceof net.minecraft.world.level.block.SlabBlock) return 's';
        if (s.getBlock() instanceof net.minecraft.world.level.block.StairBlock) return '/';
        if (!open(d, c) && !open(d, c.above())) return '#';
        if (!open(d, c)) return '.';
        for (int k = 1; k <= 2; k++) {
            BlockState under = d.getBlockState(c.below(k));
            if (!under.getFluidState().isEmpty()) return '~';
            if (!under.getCollisionShape(d, c.below(k)).isEmpty()) return k == 1 ? '.' : ',';
        }
        return ' ';
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
