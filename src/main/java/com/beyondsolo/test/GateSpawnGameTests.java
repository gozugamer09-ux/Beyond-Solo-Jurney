package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.awakening.AwakeningData;
import com.beyondsolo.awakening.AwakeningService;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateSpawner;
import com.beyondsolo.gate.GateSpawner.Outcome;
import com.beyondsolo.gate.GateSpawner.Rules;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Gates opening around players while they play ({@link GateSpawner}). In the 0.1.1 playtest no gate came in more than
 * ten minutes: a world-gen gate out of sight within 160 blocks (an E-rank one, of no use to a player without the System)
 * stopped every new one without a word, and nothing ever came for sure. Each minute is run by hand here: test players are
 * not in the player list the spawner walks. Each test plays on its own patch of land, far from every other test's gates.
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GateSpawnGameTests {
    private static final String ARENA = "evaluation_zone";

    /** A hunter with no luck at all: only the guarantee (every 3 quiet minutes) brings their gates, 2 at most around them. */
    private static final Rules HUNTERS = new Rules(0, 3, 2);
    /** The same for a player without the System and their D-rank gates, one at a time. */
    private static final Rules NEWCOMERS = new Rules(0, 3, 1);

    /** A gate opened before 0.1.2 never came for sure: the playtest's ten quiet minutes. Now it comes at the first minute. */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void aHuntersGatesComeWithoutLuck(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Land land = Land.claim(helper, -21000, 21000);
        ServerPlayer p = player(helper, land, "hunter", 0f);
        SystemAPI.awaken(p, PlayerRank.E, PlayerClass.WARRIOR);
        RandomSource r = RandomSource.create(7);
        try {
            p.tickCount = 0;
            helper.assertTrue(GateSpawner.minute(level, p, HUNTERS, null, r) == Outcome.WAITING, "nothing tears open while they are still loading in");
            helper.assertTrue(GateSpawner.quiet(p) < 0, "and nothing was counted");
            p.tickCount = GateSpawner.SETTLE_TICKS;

            // the first gate comes at once, in front of them, close enough to watch it open
            helper.assertTrue(GateSpawner.minute(level, p, HUNTERS, null, r) == Outcome.OPENED, "the first gate opens at the first minute");
            List<GateInstance> gates = land.gates(level);
            helper.assertTrue(gates.size() == 1, "one gate: " + gates.size());
            GateInstance first = gates.get(0);
            double d = dist(p, first);
            helper.assertTrue(d >= GateSpawner.VIEW_MIN - 1 && d <= GateSpawner.VIEW_MAX + 1, "close by: " + d);
            helper.assertTrue(inFront(p, first, 40), "in front of them");
            helper.assertTrue(level.getBlockEntity(first.gatePos()) instanceof GateBlockEntity, "standing in the world");
            helper.assertTrue(first.gateDimension().equals(Level.OVERWORLD) && first.state() == GateInstance.State.OPEN, "open, out here");
            helper.assertTrue(GateSpawner.knows(p, first) && GateSpawner.quiet(p) == 0, "they were told, and the wait starts over");

            // without luck, the next comes when the guarantee is up: two quiet minutes, then a gate
            helper.assertTrue(GateSpawner.minute(level, p, HUNTERS, null, r) == Outcome.WAITING, "minute 1");
            helper.assertTrue(GateSpawner.minute(level, p, HUNTERS, null, r) == Outcome.WAITING, "minute 2");
            helper.assertTrue(GateSpawner.minute(level, p, HUNTERS, null, r) == Outcome.OPENED, "minute 3: a second gate");
            gates = land.gates(level);
            helper.assertTrue(gates.size() == 2, "two gates: " + gates.size());
            GateInstance second = gates.get(0) == first ? gates.get(1) : gates.get(0);
            double d2 = dist(p, second);
            helper.assertTrue(d2 >= GateSpawner.RING_MIN - 1 && d2 <= GateSpawner.RING_MAX + 1, "further out: " + d2);
            helper.assertTrue(Math.sqrt(GateSpawner.flatDistSqr(first.gatePos().getCenter(), second.gatePos())) >= GateSpawner.SPACING, "not on top of the first");

            // two open around them is enough, however long they wait
            for (int i = 0; i < 10; i++) helper.assertTrue(GateSpawner.minute(level, p, HUNTERS, null, r) == Outcome.FULL, "no third gate");
            helper.assertTrue(land.gates(level).size() == 2, "still two");

            // one cleared and closed: the next comes as soon as the guarantee is up again
            first.setState(GateInstance.State.CLOSED);
            boolean opened = false;
            for (int i = 0; i < HUNTERS.guarantee() && !opened; i++) opened = GateSpawner.minute(level, p, HUNTERS, null, r) == Outcome.OPENED;
            helper.assertTrue(opened, "a new gate within the guarantee");
        } finally {
            land.release(level);
        }
        helper.succeed();
    }

    /**
     * The playtest's own case: world generation put a gate out of sight within 160 blocks. Before 0.1.2 it held back every
     * new gate and nobody was told; now it is pointed out (once), and it no longer stops a second one.
     */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void aGateOutOfSightIsPointedOutNotWaitedOn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Land land = Land.claim(helper, -23000, 21000);
        ServerPlayer p = player(helper, land, "hunter", 0f);
        SystemAPI.awaken(p, PlayerRank.D, PlayerClass.MAGE);
        GateSpawner.setQuiet(p, 0);
        RandomSource r = RandomSource.create(11);
        try {
            GateInstance hidden = land.record(level, PlayerRank.E, 150, 10);
            helper.assertTrue(!GateSpawner.knows(p, hidden), "they have not seen it");
            helper.assertTrue(GateSpawner.minute(level, p, HUNTERS, null, r) == Outcome.POINTED, "it is pointed out");
            helper.assertTrue(GateSpawner.knows(p, hidden), "now they know of it");
            helper.assertTrue(land.gates(level).size() == 1, "nothing new opened over it");

            // a gate they know of (and may not want: any rank opens) no longer stops the next one
            helper.assertTrue(GateSpawner.minute(level, p, HUNTERS, null, r) == Outcome.WAITING, "minute 1");
            helper.assertTrue(GateSpawner.minute(level, p, HUNTERS, null, r) == Outcome.WAITING, "minute 2");
            helper.assertTrue(GateSpawner.minute(level, p, HUNTERS, null, r) == Outcome.OPENED, "minute 3: a gate opens beside the known one");
            helper.assertTrue(GateSpawner.minute(level, p, HUNTERS, null, r) == Outcome.FULL, "then two are enough");

            // gates that are not theirs to take are not counted: a broken one, a keyed run, someone's sealed trial
            GateInstance broken = land.record(level, PlayerRank.A, -120, 40);
            broken.breakState().start(level.getGameTime(), 6000);
            broken.breakState().setPhase(com.beyondsolo.breaks.BreakState.Phase.BROKEN);
            GateInstance sealed = land.record(level, PlayerRank.D, 60, -100);
            sealed.setOwner(UUID.randomUUID());
            List<GateInstance> counted = GateSpawner.openGatesNear(level.getServer(), p.blockPosition(), GateSpawner.NEAR, null);
            helper.assertTrue(!counted.contains(broken) && !counted.contains(sealed) && counted.contains(hidden), "only gates still to be taken count");
            sealed.setOwner(null);
        } finally {
            land.release(level);
        }
        helper.succeed();
    }

    /** A player without the System: their very first gate is a D-rank one, torn open in front of them within a minute or so. */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void aNewcomerWatchesTheirFirstGateOpen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Land land = Land.claim(helper, -21000, 23000);
        ServerPlayer p = player(helper, land, "newcomer", 90f);
        RandomSource r = RandomSource.create(3);
        try {
            helper.assertTrue(!SystemAPI.hasSystem(p) && AwakeningService.stage(p) == AwakeningData.Stage.DORMANT, "no System yet");
            // gates that can't call them don't hold theirs back: an E-rank one counts for nothing, an S-rank one would kill them
            GateInstance e = land.record(level, PlayerRank.E, 110, 30);
            GateInstance s = land.record(level, PlayerRank.S, -100, -60);
            helper.assertTrue(GateSpawner.minute(level, p, null, NEWCOMERS, r) == Outcome.OPENED, "their first gate opens at the first minute");
            helper.assertTrue(!GateSpawner.knows(p, e) && !GateSpawner.knows(p, s), "the others are not theirs to be shown");
            GateInstance mine = land.gates(level).stream().filter(g -> g != e && g != s).findFirst().orElse(null);
            helper.assertTrue(mine != null && mine.rank() == PlayerRank.D, "a D-rank gate, the kind that can call them");
            double d = dist(p, mine);
            helper.assertTrue(d >= GateSpawner.VIEW_MIN - 1 && d <= GateSpawner.VIEW_MAX + 1, "close by: " + d);
            helper.assertTrue(inFront(p, mine, 40), "in front of them (they look west)");
            // one D-rank gate at a time is theirs
            for (int i = 0; i < 5; i++) helper.assertTrue(GateSpawner.minute(level, p, null, NEWCOMERS, r) == Outcome.FULL, "no second one while it stands");
            // once a gate has called them, the spawner leaves them to their door
            AwakeningService.data(p).setStage(AwakeningData.Stage.CALLED);
            helper.assertTrue(GateSpawner.minute(level, p, null, NEWCOMERS, r) == Outcome.SKIPPED, "called: none");
            AwakeningService.data(p).setStage(AwakeningData.Stage.DORMANT);
        } finally {
            land.release(level);
        }
        helper.succeed();
    }

    /** A D-rank gate already near a player without the System is shown to them (a feeling, not the System), and the next follows. */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void aNewcomerIsShownTheDRankGateNearby(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Land land = Land.claim(helper, -23000, 23000);
        ServerPlayer p = player(helper, land, "newcomer", 0f);
        RandomSource r = RandomSource.create(5);
        try {
            GateInstance d = land.record(level, PlayerRank.D, -130, 20);
            helper.assertTrue(GateSpawner.minute(level, p, null, NEWCOMERS, r) == Outcome.POINTED, "shown the gate that can call them");
            helper.assertTrue(GateSpawner.minute(level, p, null, NEWCOMERS, r) == Outcome.FULL, "and left to go to it");
            // it was taken by someone else (or broke): within the guarantee the next one opens
            d.setState(GateInstance.State.CLOSED);
            helper.assertTrue(GateSpawner.minute(level, p, null, NEWCOMERS, r) == Outcome.WAITING, "minute 1");
            helper.assertTrue(GateSpawner.minute(level, p, null, NEWCOMERS, r) == Outcome.WAITING, "minute 2");
            helper.assertTrue(GateSpawner.minute(level, p, null, NEWCOMERS, r) == Outcome.OPENED, "minute 3: a new D-rank gate");
            helper.assertTrue(land.gates(level).stream().allMatch(g -> g.rank() == PlayerRank.D), "D-rank");
        } finally {
            land.release(level);
        }
        helper.succeed();
    }

    /**
     * Someone the System has bound but not evaluated: gates turn them away, so none open for them, however lucky; once
     * they have waited as long as a gate would take, they are told why. Evaluated, their wait counts: a gate comes at once.
     */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void unevaluatedPlayersAreToldGatesWontTakeThem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Land land = Land.claim(helper, -25000, 21000);
        ServerPlayer p = player(helper, land, "unregistered", 0f);
        SystemAPI.grantSystem(p);
        Rules lucky = new Rules(1, 3, 2);
        RandomSource r = RandomSource.create(9);
        try {
            helper.assertTrue(SystemAPI.hasSystem(p) && !SystemAPI.isAwakened(p), "bound, not evaluated");
            helper.assertTrue(GateSpawner.minute(level, p, lucky, NEWCOMERS, r) == Outcome.LOCKED && !GateSpawner.toldLocked(p), "minute 1");
            helper.assertTrue(GateSpawner.minute(level, p, lucky, NEWCOMERS, r) == Outcome.LOCKED && !GateSpawner.toldLocked(p), "minute 2");
            helper.assertTrue(GateSpawner.minute(level, p, lucky, NEWCOMERS, r) == Outcome.LOCKED && GateSpawner.toldLocked(p), "minute 3: told why");
            helper.assertTrue(land.gates(level).isEmpty(), "no gate opened for them");
            SystemAPI.awaken(p, PlayerRank.E, PlayerClass.RANGER);
            helper.assertTrue(GateSpawner.minute(level, p, HUNTERS, null, r) == Outcome.OPENED, "evaluated: their wait was long enough, a gate opens");
        } finally {
            land.release(level);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    /** A test player standing in the middle of the land, looking {@code yaw} (0 south, 90 west), without the System. */
    private static ServerPlayer player(GameTestHelper helper, Land land, String name, float yaw) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        AwakeningService.reset(p);
        p.setGameMode(GameType.SURVIVAL);
        p.moveTo(land.center().getX() + 0.5, land.center().getY(), land.center().getZ() + 0.5, yaw, 0f);
        p.tickCount = GateSpawner.SETTLE_TICKS;
        return p;
    }

    private static double dist(ServerPlayer p, GateInstance g) {
        return Math.sqrt(GateSpawner.flatDistSqr(p.position(), g.gatePos()));
    }

    /** True if the gate lies within {@code degrees} of where the player looks. */
    private static boolean inFront(ServerPlayer p, GateInstance g, float degrees) {
        double dx = g.gatePos().getX() + 0.5 - p.getX(), dz = g.gatePos().getZ() + 0.5 - p.getZ();
        float look = GateSpawner.lookAngle(p);
        double cos = (dx * Mth.cos(look) + dz * Mth.sin(look)) / Math.sqrt(dx * dx + dz * dz);
        return cos >= Math.cos(Math.toRadians(degrees));
    }

    /** A patch of flat land far from the other tests (their gates are near the origin, gate caves along z = 0), loaded. */
    private record Land(BlockPos center, List<ChunkPos> chunks) {
        /** Chunks loaded each way: the spawner looks up to {@link GateSpawner#RING_MAX} (80) blocks out. */
        private static final int RADIUS = 5;

        static Land claim(GameTestHelper helper, int dx, int dz) {
            ServerLevel level = helper.getLevel();
            ChunkPos c = new ChunkPos(helper.absolutePos(BlockPos.ZERO).offset(dx, 0, dz));
            List<ChunkPos> chunks = new ArrayList<>();
            for (int x = -RADIUS; x <= RADIUS; x++) {
                for (int z = -RADIUS; z <= RADIUS; z++) chunks.add(new ChunkPos(c.x + x, c.z + z));
            }
            for (ChunkPos cp : chunks) level.setChunkForced(cp.x, cp.z, true);
            for (ChunkPos cp : chunks) level.getChunk(cp.x, cp.z);
            TestChunks.settle(level, () -> chunks.stream().allMatch(cp -> level.getChunkSource().hasChunk(cp.x, cp.z)));
            BlockPos center = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getMiddleBlockPosition(0));
            // nobody else's gates here (a gate left over from a test run before)
            for (GateInstance g : GateSpawner.openGatesNear(level.getServer(), center, 400, null)) g.setState(GateInstance.State.CLOSED);
            return new Land(center, chunks);
        }

        /** A gate's record without a gate in the world: what a world-gen gate out of sight is to the spawner. */
        GateInstance record(ServerLevel level, PlayerRank rank, int dx, int dz) {
            return GateData.get(level.getServer()).create(UUID.randomUUID(), rank, dx * 31L + dz, Level.OVERWORLD, center.offset(dx, 0, dz));
        }

        /** The gates open on this land. */
        List<GateInstance> gates(ServerLevel level) {
            return GateSpawner.openGatesNear(level.getServer(), center, GateSpawner.NEAR + 40, null);
        }

        /** Closes every gate on it, takes them out of the world and lets the land unload. */
        void release(ServerLevel level) {
            for (GateInstance g : GateData.get(level.getServer()).all()) {
                if (!g.gateDimension().equals(Level.OVERWORLD) || GateSpawner.flatDistSqr(center.getCenter(), g.gatePos()) > 300 * 300) continue;
                g.setState(GateInstance.State.CLOSED);
                if (level.getBlockEntity(g.gatePos()) instanceof GateBlockEntity) level.removeBlock(g.gatePos(), false);
            }
            for (ChunkPos cp : chunks) level.setChunkForced(cp.x, cp.z, false);
        }
    }

    private GateSpawnGameTests() {}
}
