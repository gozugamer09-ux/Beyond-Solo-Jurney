package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.Buildups;
import com.beyondsolo.registry.ModBlocks;
import com.beyondsolo.system.Exhaustion;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.Progression;
import com.beyondsolo.system.Stat;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import com.mojang.authlib.GameProfile;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.UUID;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests. Run with {@code ./gradlew runGameTestServer}. They use the Evaluation Zone
 * structure as their test arena.
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SystemGameTests {
    private static final String ARENA = "evaluation_zone";
    /** Test structures sit on a one-block plate, so template y=1 is relative y=2. */
    private static final BlockPos CRYSTAL = new BlockPos(3, 2, 3);

    private static ServerPlayer player(GameTestHelper helper) {
        // A fake player ignores outgoing packets, so the System can sync to it safely.
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "tester"));
        SystemAPI.reset(p);
        p.setGameMode(GameType.SURVIVAL);
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        return p;
    }

    @GameTest(template = ARENA)
    public static void structureHasCrystal(GameTestHelper helper) {
        helper.assertBlockPresent(ModBlocks.EVALUATION_CRYSTAL.get(), CRYSTAL);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void awakenGivesStatsAndBuffs(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        double baseHealth = p.getMaxHealth();
        SystemAPI.awaken(p, PlayerRank.S, PlayerClass.GUARDIAN);
        SystemData d = SystemAPI.get(p);
        helper.assertTrue(d.isAwakened(), "should be awakened");
        int total = 0;
        for (Stat s : Stat.values()) total += d.stat(s) - Stat.BASE;
        helper.assertTrue(total == PlayerRank.S.bonusStatPoints(), "bonus points should all be spent, got " + total);
        helper.assertTrue(d.stat(Stat.VIT) > d.stat(Stat.INT), "guardian should favour VIT");
        helper.assertTrue(p.getMaxHealth() > baseHealth, "VIT should raise max health");
        helper.assertTrue(p.getAttributeValue(Attributes.ARMOR) > 0, "VIT should give armor");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void xpLevelsUpAndPointsRaiseStats(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        SystemAPI.awaken(p, PlayerRank.E, PlayerClass.WARRIOR);
        SystemData d = SystemAPI.get(p);
        SystemAPI.grantXp(p, Progression.xpToNext(1) + Progression.xpToNext(2), SystemAPI.XP_SOURCE_COMMAND);
        helper.assertTrue(d.level() == 3, "expected level 3, got " + d.level());
        helper.assertTrue(d.statPoints() == 6, "expected 6 points, got " + d.statPoints());
        double damage = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
        int str = d.stat(Stat.STR);
        helper.assertTrue(SystemAPI.allocateStat(p, Stat.STR), "allocation should work");
        helper.assertTrue(d.stat(Stat.STR) == str + 1, "STR should go up");
        helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) > damage, "STR should raise damage");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void noXpBeforeEvaluation(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        SystemAPI.grantXp(p, 10_000, SystemAPI.XP_SOURCE_COMMAND);
        helper.assertTrue(SystemAPI.get(p).level() == 1, "unevaluated players should not level");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void exhaustionCollapsesAndSleepRecovers(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        SystemAPI.awaken(p, PlayerRank.E, PlayerClass.WARRIOR);
        SystemAPI.addExhaustion(p, 150);
        SystemData d = SystemAPI.get(p);
        helper.assertTrue(d.isCollapsed(), "should collapse at 100%");
        helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) == 0, "collapsed players can't move");
        int ticks = d.collapseTicks();
        helper.assertTrue(ticks >= 30 * 20 && ticks <= 45 * 20, "collapse should last 30-45s, got " + ticks);
        Exhaustion.recover(p, 0);
        helper.assertTrue(!d.isCollapsed() && p.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0, "should recover");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void bloodBuildupPopsForTenPercent(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        float max = zombie.getMaxHealth();
        Buildups.add(zombie, Buildups.BLOOD, 60, null);
        helper.assertTrue(zombie.getHealth() == max, "no pop below the threshold");
        Buildups.add(zombie, Buildups.BLOOD, 60, null);
        helper.assertTrue(Math.abs(zombie.getHealth() - max * 0.9f) < 0.01f, "pop should take 10%, health " + zombie.getHealth());
        helper.assertTrue(Buildups.get(zombie, Buildups.BLOOD) == 0, "buildup resets after pop");
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void crystalEvaluatesPlayer(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        BlockPos crystal = helper.absolutePos(CRYSTAL);
        p.moveTo(crystal.getX() + 0.5, crystal.getY(), crystal.getZ() - 1.5);
        com.beyondsolo.evaluation.EvaluationTestAccess.interact(p, crystal);
        helper.assertTrue(!SystemAPI.isAwakened(p), "evaluation takes a few seconds");
        helper.succeedWhen(() -> helper.assertTrue(SystemAPI.isAwakened(p), "player should be evaluated"));
    }

    private SystemGameTests() {}
}
