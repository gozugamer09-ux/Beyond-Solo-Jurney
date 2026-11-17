package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.Config;
import com.beyondsolo.breaks.BreakState;
import com.beyondsolo.breaks.BreakTestAccess;
import com.beyondsolo.breaks.DungeonBreaks;
import com.beyondsolo.breaks.GateSense;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.gate.RewardCacheBlockEntity;
import com.beyondsolo.network.GateSensePayload;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.util.ParticleGuard;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Dungeon breaks and gate sensing. Run with {@code ./gradlew runGameTestServer}. Test players are not in the player
 * list, so the server's own once-a-second look never counts them as watching: these tests move a gate's clock by hand,
 * a step at a time, saying each time whether someone is inside it or near enough to see it.
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BreakGameTests {
    private static final String ARENA = "evaluation_zone";

    private static ServerPlayer player(GameTestHelper helper, PlayerRank rank) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "break_hunter"));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        p.setGameMode(GameType.SURVIVAL);
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        SystemAPI.awaken(p, rank, PlayerClass.WARRIOR);
        QuestTestAccess.markStarted(p);
        return p;
    }

    private static GateInstance gate(GameTestHelper helper, PlayerRank rank, BlockPos at) {
        GateBlockEntity gate = GateService.spawnGate(helper.getLevel(), helper.absolutePos(at), rank, Direction.NORTH, false);
        GateInstance g = GateData.get(helper.getLevel().getServer()).get(gate.gateId());
        if (g == null) throw new IllegalStateException("gate was not registered");
        return g;
    }

    private static GateInstance gate(GameTestHelper helper, PlayerRank rank) {
        return gate(helper, rank, new BlockPos(3, 2, 3));
    }

    private static GateBlockEntity block(GameTestHelper helper, GateInstance g) {
        return (GateBlockEntity) helper.getLevel().getBlockEntity(g.gatePos());
    }

    /** Takes a test's break away again: its creatures, its boss, and the gate. */
    private static void cleanUp(ServerLevel level, GateInstance g) {
        for (Mob m : DungeonBreaks.monsters(level, g)) m.discard();
        g.setState(GateInstance.State.CLOSED);
    }

    @GameTest(template = ARENA)
    public static void clockRunsThroughItsPhases(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GateData data = GateData.get(level.getServer());
        GateInstance g = gate(helper, PlayerRank.C);
        BreakState b = g.breakState();
        long now = level.getGameTime();
        helper.assertTrue(DungeonBreaks.canBreak(g), "an open gate can break");
        DungeonBreaks.step(level, data, g, now, false, true);
        helper.assertTrue(b.clockStarted() && b.phase() == BreakState.Phase.STABLE, "its clock started, and it holds");
        long minutes = Config.GATE_BREAK_MINUTES.get();
        helper.assertTrue(b.length() >= minutes * 1200 * 0.8 - 1 && b.length() <= minutes * 1200 * 1.2 + 1,
                "the configured minutes, give or take a fifth: " + b.length());
        // the last third: it comes apart in plain sight
        b.setBreakAt(now + b.length() / 3 - 20);
        DungeonBreaks.step(level, data, g, now, false, true);
        helper.assertTrue(b.phase() == BreakState.Phase.UNSTABLE && b.unstableAt() == now, "coming apart");
        helper.assertTrue(block(helper, g).isUnstable() && block(helper, g).breakPhase() == GateBlockEntity.UNSTABLE, "the gate shows it");
        // its last minute, in front of someone
        b.setBreakAt(now + DungeonBreaks.IMMINENT_TICKS - 20);
        DungeonBreaks.step(level, data, g, now, false, true);
        helper.assertTrue(b.phase() == BreakState.Phase.IMMINENT && block(helper, g).breakPhase() == GateBlockEntity.IMMINENT, "about to break");
        helper.assertTrue(b.unstableAt() == now, "it started coming apart once");
        // due: it breaks, and its first wave pours out, each one its gate's and leashed to it
        b.setBreakAt(now);
        DungeonBreaks.step(level, data, g, now, false, true);
        helper.assertTrue(b.broken() && b.brokeAt() == now && block(helper, g).isBroken(), "broken");
        helper.assertTrue(!DungeonBreaks.canBreak(g), "a gate breaks once");
        List<Mob> out = DungeonBreaks.monsters(level, g);
        helper.assertTrue(!out.isEmpty() && out.size() <= DungeonBreaks.waveSize(g), "the first wave is out: " + out.size());
        for (Mob m : out) {
            helper.assertTrue(DungeonBreaks.isOf(m, g.id()), "it came out of this gate");
            helper.assertTrue(m.hasRestriction() && m.getRestrictCenter().equals(g.gatePos()) && m.getRestrictRadius() == DungeonBreaks.LEASH,
                    "kept to its gate");
            helper.assertTrue(!(m instanceof GateBossEntity), "no boss with the first wave");
        }
        helper.assertTrue(b.waves() == 1 && !b.bossOut(), "one wave so far, no boss yet");
        // its state is saved with the gate
        BreakState saved = BreakState.load(b.save());
        helper.assertTrue(saved.phase() == b.phase() && saved.breakAt() == b.breakAt() && saved.brokeAt() == b.brokeAt()
                && saved.unstableAt() == b.unstableAt() && saved.waves() == b.waves() && saved.length() == b.length(), "saved and loaded");
        cleanUp(level, g);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void clockWaitsForHuntersAndWitnesses(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GateData data = GateData.get(level.getServer());
        GateInstance g = gate(helper, PlayerRank.D);
        BreakState b = g.breakState();
        long now = level.getGameTime();
        DungeonBreaks.step(level, data, g, now, false, true);
        long at = b.breakAt();
        // the hunters inside hold it together
        DungeonBreaks.step(level, data, g, now + 20, true, true);
        helper.assertTrue(b.breakAt() == at + DungeonBreaks.CHECK, "its clock waits while someone is inside");
        // left alone it holds at its last minute
        b.setBreakAt(now + 100);
        DungeonBreaks.step(level, data, g, now, false, false);
        helper.assertTrue(b.breakAt() == now + DungeonBreaks.IMMINENT_TICKS && !b.hasBroken(), "held at its last minute");
        b.setPhase(BreakState.Phase.IMMINENT);
        b.setBreakAt(now);
        DungeonBreaks.step(level, data, g, now, false, false);
        helper.assertTrue(!b.hasBroken() && b.breakAt() == now + DungeonBreaks.CHECK, "its last minute waits for someone to see it");
        // someone comes back: it breaks in front of them
        DungeonBreaks.step(level, data, g, now + DungeonBreaks.CHECK, false, true);
        helper.assertTrue(b.broken(), "broke with someone there");
        cleanUp(level, g);

        // cleared gates and the awakening's trials never break
        GateInstance cleared = gate(helper, PlayerRank.E, new BlockPos(1, 2, 5));
        cleared.setState(GateInstance.State.CLEARED);
        helper.assertTrue(!DungeonBreaks.canBreak(cleared), "a cleared gate never breaks");
        GateInstance trial = gate(helper, PlayerRank.D, new BlockPos(5, 2, 5));
        trial.setOwner(UUID.randomUUID());
        helper.assertTrue(!DungeonBreaks.canBreak(trial), "a trial never breaks");
        trial.setOwner(null);
        trial.setState(GateInstance.State.CLOSED);
        cleared.setState(GateInstance.State.CLOSED);
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void bossComesThroughWithTheThirdWave(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GateData data = GateData.get(level.getServer());
        GateInstance g = gate(helper, PlayerRank.C);
        BreakState b = g.breakState();
        DungeonBreaks.breakOpen(level, data, g, level.getGameTime());
        helper.assertTrue(b.waves() == 1, "the first wave came with the break");
        // nothing more until the next wave is due
        BreakTestAccess.brokenTick(level, g, true);
        helper.assertTrue(b.waves() == 1, "waves come " + DungeonBreaks.WAVE_TICKS / 20 + " seconds apart");
        BreakTestAccess.waveDue(g);
        BreakTestAccess.brokenTick(level, g, true);
        helper.assertTrue(b.waves() == 2 && !b.bossOut(), "the second wave, still no boss");
        // nobody near: nothing comes out
        BreakTestAccess.waveDue(g);
        BreakTestAccess.brokenTick(level, g, false);
        helper.assertTrue(b.waves() == 2, "a break waits for someone to see it");
        BreakTestAccess.brokenTick(level, g, true);
        helper.assertTrue(b.waves() == 3 && b.bossOut(), "the boss comes through with the third wave");
        helper.assertTrue(level.getEntity(b.boss()) instanceof GateBossEntity boss && DungeonBreaks.isOf(boss, g.id())
                && boss.kind() == g.bossKind() && boss.isAwake() && boss.hasRestriction(), "its own boss, awake and kept to its gate");
        int crowd = 0;
        for (Mob m : DungeonBreaks.monsters(level, g)) if (!(m instanceof GateBossEntity)) crowd++;
        helper.assertTrue(crowd <= DungeonBreaks.cap(g), "never more out than its cap: " + crowd);
        cleanUp(level, g);
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void bossFallingSuppressesTheBreak(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GateData data = GateData.get(level.getServer());
        GateInstance g = gate(helper, PlayerRank.E);
        BreakState b = g.breakState();
        DungeonBreaks.breakOpen(level, data, g, level.getGameTime());
        GateBossEntity boss = DungeonBreaks.bossOut(level, data, g);
        helper.assertTrue(boss != null && b.bossOut(), "its boss is out");
        int before = DungeonBreaks.monsters(level, g).size();
        helper.assertTrue(before > 1, "monsters and boss out: " + before);
        Vec3[] fell = new Vec3[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(boss.introTicks() <= 0, "its entrance is over"))
                .thenExecute(() -> {
                    fell[0] = boss.position();
                    boss.hurt(level.damageSources().generic(), 100_000f);
                    helper.assertTrue(b.phase() == BreakState.Phase.SUPPRESSED && !b.bossOut(), "the break is over");
                    helper.assertTrue(g.state() == GateInstance.State.CLOSED && g.bossDefeated(), "its gate collapses");
                    helper.assertTrue(block(helper, g) == null || block(helper, g).isClosing(), "the gate closes in the world");
                })
                // what was left of its monsters crumbles to ash
                .thenWaitUntil(() -> helper.assertTrue(DungeonBreaks.monsters(level, g).isEmpty(), "its monsters crumbled"))
                // and the boss leaves a reward cache where it fell
                .thenWaitUntil(() -> {
                    boolean cache = false;
                    BlockPos c = BlockPos.containing(fell[0]);
                    for (BlockPos p : BlockPos.betweenClosed(c.offset(-2, -3, -2), c.offset(2, 8, 2))) {
                        if (level.getBlockEntity(p) instanceof RewardCacheBlockEntity) cache = true;
                    }
                    helper.assertTrue(cache, "a reward cache where the boss fell");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void systemSendsHuntersToTheBreak(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer p = player(helper, PlayerRank.D);
        // a break within a rank of theirs is an emergency: forced on them
        GateInstance near = gate(helper, PlayerRank.C);
        BreakTestAccess.giveQuest(p, near);
        Quest q = QuestEngine.data(p).active().stream().filter(x -> DungeonBreaks.QUEST_TEMPLATE.equals(x.template())).findFirst().orElse(null);
        helper.assertTrue(q != null && q.category() == QuestCategory.EMERGENCY && q.isForced(), "an emergency quest");
        BreakTestAccess.giveQuest(p, near);
        helper.assertTrue(QuestEngine.data(p).active().stream().filter(x -> DungeonBreaks.QUEST_TEMPLATE.equals(x.template())).count() == 1,
                "one quest per break");
        // far above them it is only offered
        GateInstance far = gate(helper, PlayerRank.S, new BlockPos(1, 2, 5));
        BreakTestAccess.giveQuest(p, far);
        helper.assertTrue(QuestEngine.data(p).offers().stream().anyMatch(x -> DungeonBreaks.QUEST_TEMPLATE.equals(x.template())
                && far.id().toString().equals(x.extra().getString("gate"))), "an S-rank break is only offered to a D-rank");
        // the break's monsters count, and its boss falling finishes it, with XP for everyone who stood there
        QuestEngine.progress(p, DungeonBreaks.MOB_KEY, 2);
        int level0 = SystemAPI.get(p).level();
        long xp0 = SystemAPI.get(p).xp();
        BreakTestAccess.reward(p, near, 500);
        helper.assertTrue(q.isComplete() || !QuestEngine.data(p).active().contains(q), "the quest is done");
        helper.assertTrue(SystemAPI.get(p).level() > level0 || SystemAPI.get(p).xp() > xp0, "paid in XP");
        near.setState(GateInstance.State.CLOSED);
        far.setState(GateInstance.State.CLOSED);
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void strayMonstersArePulledBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GateData data = GateData.get(level.getServer());
        GateInstance g = gate(helper, PlayerRank.E);
        DungeonBreaks.spill(level, data, g, level.getGameTime(), 1, false);
        List<Mob> out = DungeonBreaks.monsters(level, g);
        helper.assertTrue(out.size() == 1, "one out");
        Mob m = out.get(0);
        BlockPos away = g.gatePos().offset(80, 0, 0);
        ChunkPos chunk = new ChunkPos(away);
        level.setChunkForced(chunk.x, chunk.z, true);
        m.teleportTo(away.getX() + 0.5, m.getY(), away.getZ() + 0.5);
        helper.assertTrue(DungeonBreaks.flat(m.position(), g.gatePos()) > DungeonBreaks.LEASH * 1.6, "strayed far");
        BreakTestAccess.leash(level, g);
        helper.assertTrue(DungeonBreaks.flat(m.position(), g.gatePos()) < 12, "pulled back through the rift: " + DungeonBreaks.flat(m.position(), g.gatePos()));
        level.setChunkForced(chunk.x, chunk.z, false);
        cleanUp(level, g);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void pulseSensesGatesInRange(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GateData data = GateData.get(level.getServer());
        // far from every other test's gates, so the nearest twelve are these
        BlockPos here = helper.absolutePos(new BlockPos(3, 2, 3)).offset(24000, 0, 24000);
        UUID me = UUID.randomUUID();
        GateInstance close = data.create(UUID.randomUUID(), PlayerRank.B, 1L, level.dimension(), here.offset(60, 0, 0));
        GateInstance farther = data.create(UUID.randomUUID(), PlayerRank.A, 2L, level.dimension(), here.offset(0, 0, -180));
        GateInstance shut = data.create(UUID.randomUUID(), PlayerRank.S, 3L, level.dimension(), here.offset(-40, 0, 0));
        shut.setState(GateInstance.State.CLOSED);
        GateInstance mine = data.create(UUID.randomUUID(), PlayerRank.D, 4L, level.dimension(), here.offset(0, 0, 70));
        mine.setOwner(me);
        GateInstance theirs = data.create(UUID.randomUUID(), PlayerRank.D, 5L, level.dimension(), here.offset(0, 0, 75));
        theirs.setOwner(UUID.randomUUID());
        close.breakState().start(level.getGameTime(), 6000);
        close.breakState().setPhase(BreakState.Phase.UNSTABLE);
        try {
            Vec3 at = Vec3.atCenterOf(here);
            helper.assertTrue(GateSense.range(1) == 112 && GateSense.range(10) > GateSense.range(1), "the pulse reaches further with its level");
            List<GateSensePayload.Entry> near = GateSense.collect(level.getServer(), level, at, GateSense.range(1), me);
            helper.assertTrue(has(near, close) && !has(near, farther), "the near gate, not the one out of reach");
            helper.assertTrue(!has(near, shut), "a closed gate is gone");
            helper.assertTrue(has(near, mine) && !has(near, theirs), "your own sealed gate, nobody else's");
            GateSensePayload.Entry e = near.stream().filter(x -> x.pos().equals(close.gatePos())).findFirst().orElseThrow();
            helper.assertTrue(e.rank() == PlayerRank.B.ordinal() && e.state() == GateSensePayload.UNSTABLE && e.ticksLeft() > 0 && e.ticksLeft() <= 6000,
                    "its rank, coming apart, and how long it has left");
            helper.assertTrue(near.stream().filter(x -> x.pos().equals(mine.gatePos())).findFirst().orElseThrow().state() == GateSensePayload.SEALED, "sealed");
            List<GateSensePayload.Entry> wide = GateSense.collect(level.getServer(), level, at, 400, me);
            helper.assertTrue(has(wide, close) && has(wide, farther), "a stronger pulse finds both");
            helper.assertTrue(wide.indexOf(find(wide, close)) < wide.indexOf(find(wide, farther)), "nearest first");
            helper.assertTrue(wide.size() <= GateSense.MAX, "at most " + GateSense.MAX);
        } finally {
            for (GateInstance g : List.of(close, farther, mine, theirs)) {
                g.setOwner(null);
                g.setState(GateInstance.State.CLOSED);
            }
        }
        helper.succeed();
    }

    private static boolean has(List<GateSensePayload.Entry> list, GateInstance g) {
        return find(list, g) != null;
    }

    private static GateSensePayload.Entry find(List<GateSensePayload.Entry> list, GateInstance g) {
        return list.stream().filter(x -> x.pos().equals(g.gatePos())).findFirst().orElse(null);
    }

    @GameTest(template = ARENA)
    public static void particlesStayOutOfTheCamera(GameTestHelper helper) {
        Vec3 eye = new Vec3(0, 1.62, 0);
        helper.assertTrue(!ParticleGuard.clear(eye, 0, 1.6, 1, 0.2, 0.2, 0.2), "a puff right in front of the eyes is not sent");
        helper.assertTrue(ParticleGuard.clear(eye, 0, 1.6, 8, 0.2, 0.2, 0.2), "one a few steps off is");
        helper.assertTrue(!ParticleGuard.clear(eye, 0, 1.6, 3.5, 1.5, 1.5, 1.5), "a wide spread that reaches the eyes is not");
        helper.assertTrue(ParticleGuard.clear(eye, 10, 1.6, 10, 1.5, 1.5, 1.5), "a wide one well away from them is");
        helper.succeed();
    }
}
