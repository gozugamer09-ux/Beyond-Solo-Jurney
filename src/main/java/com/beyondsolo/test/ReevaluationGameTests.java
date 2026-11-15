package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.evaluation.EvaluationManager;
import com.beyondsolo.evaluation.EvaluationTestAccess;
import com.beyondsolo.evaluation.Reevaluation;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.Progression;
import com.beyondsolo.system.Stat;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Stat points, hidden rank thresholds and re-evaluation. */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReevaluationGameTests {
    private static final String ARENA = "evaluation_zone";
    private static final BlockPos CRYSTAL = new BlockPos(3, 2, 3);
    /** Scan plus reveal, with room to spare. */
    private static final int EVALUATION_TICKS = 100 + 280 + 40;

    private static ServerPlayer player(GameTestHelper helper, PlayerRank rank) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "reeval"));
        SystemAPI.reset(p);
        Reevaluation.reset(p);
        p.setGameMode(GameType.SURVIVAL);
        BlockPos crystal = helper.absolutePos(CRYSTAL);
        p.moveTo(crystal.getX() + 0.5, crystal.getY(), crystal.getZ() - 1.5);
        SystemAPI.awaken(p, rank, PlayerClass.WARRIOR);
        return p;
    }

    private static void levelTo(ServerPlayer p, int level) {
        long xp = 0;
        for (int l = SystemAPI.get(p).level(); l < level; l++) xp += Progression.xpToNext(l);
        SystemAPI.grantXp(p, xp, SystemAPI.XP_SOURCE_COMMAND);
    }

    /** Puts the player's stat total at {@code total} by raising STR (not as spent points). */
    private static void setTotal(ServerPlayer p, int total) {
        SystemData d = SystemAPI.get(p);
        int now = Reevaluation.statTotal(d);
        SystemAPI.setStat(p, Stat.STR, d.stat(Stat.STR) + (total - now));
    }

    @GameTest(template = ARENA)
    public static void thresholdsMapStatTotalsToRanks(GameTestHelper helper) {
        helper.assertTrue(Reevaluation.earnedRank(Stat.BASE * 5) == PlayerRank.E, "base stats are E");
        PlayerRank last = PlayerRank.E;
        for (PlayerRank r : PlayerRank.values()) {
            if (r == PlayerRank.E) continue;
            int t = Reevaluation.threshold(r);
            helper.assertTrue(t > Reevaluation.threshold(last), "thresholds should rise: " + r);
            helper.assertTrue(Reevaluation.earnedRank(t) == r, "total " + t + " should be " + r);
            helper.assertTrue(Reevaluation.earnedRank(t - 1) == last, "total " + (t - 1) + " should be " + last);
            last = r;
        }
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void crossingAThresholdStaysHidden(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E);
        setTotal(p, Reevaluation.threshold(PlayerRank.C));
        helper.assertTrue(Reevaluation.trueRank(p) == PlayerRank.C, "true rank should be C, got " + Reevaluation.trueRank(p));
        helper.assertTrue(SystemAPI.get(p).rank() == PlayerRank.E, "shown rank must not change before a re-evaluation");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void reevaluationOpensOncePerMilestone(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E);
        int m = Reevaluation.milestoneSize();
        helper.assertTrue(!Reevaluation.isAvailable(p), "not available right after the first evaluation");
        helper.assertTrue(Reevaluation.nextLevel(p) == m, "first re-evaluation at level " + m + ", got " + Reevaluation.nextLevel(p));
        levelTo(p, m - 1);
        helper.assertTrue(!Reevaluation.isAvailable(p), "not available before the milestone");
        levelTo(p, m);
        helper.assertTrue(Reevaluation.isAvailable(p), "available at the milestone");
        helper.assertTrue(SystemAPI.get(p).statPoints() == (m - 1) * 3, "3 points per level, got " + SystemAPI.get(p).statPoints());
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = EVALUATION_TICKS)
    public static void crystalRevealsHiddenRank(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E);
        int m = Reevaluation.milestoneSize();
        levelTo(p, m);
        // two thresholds crossed: a re-evaluation can jump more than one rank
        setTotal(p, Reevaluation.threshold(PlayerRank.C) + 3);
        int str = SystemAPI.get(p).stat(Stat.STR);
        EvaluationTestAccess.interact(p, helper.absolutePos(CRYSTAL));
        helper.assertTrue(EvaluationManager.isBeingEvaluated(p), "the crystal should start a re-evaluation");
        helper.succeedWhen(() -> {
            helper.assertTrue(!EvaluationManager.isBeingEvaluated(p), "still running");
            SystemData d = SystemAPI.get(p);
            helper.assertTrue(d.rank() == PlayerRank.C, "rank should rise to C, got " + d.rank());
            helper.assertTrue(d.playerClass() == PlayerClass.WARRIOR, "class stays");
            helper.assertTrue(d.stat(Stat.STR) == str, "a re-evaluation must not touch stats");
            helper.assertTrue(!Reevaluation.isAvailable(p), "milestone used up");
            helper.assertTrue(Reevaluation.nextLevel(p) == 2 * m, "next at " + 2 * m + ", got " + Reevaluation.nextLevel(p));
            helper.assertTrue(Reevaluation.record(p).reevaluations() == 1, "counted once");
        });
    }

    @GameTest(template = ARENA, timeoutTicks = EVALUATION_TICKS)
    public static void reevaluationNeverLowersRank(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.B);
        Reevaluation.unlock(p);
        helper.assertTrue(Reevaluation.isAvailable(p), "unlock opens it");
        helper.assertTrue(Reevaluation.trueRank(p) == PlayerRank.B, "low stats keep the evaluated rank");
        EvaluationManager.beginReevaluation(p, helper.absolutePos(CRYSTAL), null);
        helper.succeedWhen(() -> {
            helper.assertTrue(!EvaluationManager.isBeingEvaluated(p), "still running");
            helper.assertTrue(SystemAPI.get(p).rank() == PlayerRank.B, "rank must stay B, got " + SystemAPI.get(p).rank());
            helper.assertTrue(!Reevaluation.isAvailable(p), "the attempt still uses the milestone");
        });
    }

    @GameTest(template = ARENA)
    public static void lockedCrystalDoesNotStartAScan(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.D);
        EvaluationTestAccess.interact(p, helper.absolutePos(CRYSTAL));
        helper.assertTrue(!EvaluationManager.isBeingEvaluated(p), "no re-evaluation before the milestone");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void bulkSpendAndRebirthRefund(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.D);
        SystemData d = SystemAPI.get(p);
        SystemAPI.addStatPoints(p, 7);
        int agi = d.stat(Stat.AGI);
        int vit = d.stat(Stat.VIT);
        helper.assertTrue(SystemAPI.allocateStats(p, Stat.AGI, 5) == 5, "spends 5");
        helper.assertTrue(SystemAPI.allocateStats(p, Stat.VIT, 100) == 2, "spends only what is left");
        helper.assertTrue(d.stat(Stat.AGI) == agi + 5 && d.stat(Stat.VIT) == vit + 2, "stats rose");
        helper.assertTrue(d.statPoints() == 0, "no points left");
        // a stat reward is not a spent point: it stays
        SystemAPI.setStat(p, Stat.INT, d.stat(Stat.INT) + 4);
        int intl = d.stat(Stat.INT);
        int back = Reevaluation.refundAllocated(p);
        helper.assertTrue(back == 7, "7 points come back, got " + back);
        helper.assertTrue(d.stat(Stat.AGI) == agi && d.stat(Stat.VIT) == vit, "spent points removed");
        helper.assertTrue(d.stat(Stat.INT) == intl, "rewards stay");
        helper.assertTrue(d.statPoints() == 7, "points back to spend");
        helper.assertTrue(Reevaluation.refundAllocated(p) == 0, "nothing twice");
        helper.succeed();
    }

    private ReevaluationGameTests() {}
}
