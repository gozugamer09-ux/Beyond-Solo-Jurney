package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.chronicle.ranking.Rival;
import com.beyondsolo.chronicle.ranking.Rivals;
import com.beyondsolo.enemy.Enemies;
import com.beyondsolo.enemy.EnemyModule;
import com.beyondsolo.enemy.EnemyRoster;
import com.beyondsolo.enemy.EnemyTraits;
import com.beyondsolo.enemy.HiddenLevel;
import com.beyondsolo.enemy.MonsterKind;
import com.beyondsolo.gear.BonusStat;
import com.beyondsolo.quest.RankScaling;
import com.beyondsolo.shadow.ShadowRank;
import com.beyondsolo.shadow.ShadowRecord;
import com.beyondsolo.shadow.ShadowService;
import com.beyondsolo.system.Balance;
import com.beyondsolo.system.BalanceHooks;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.Progression;
import com.beyondsolo.system.Stat;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import com.mojang.authlib.GameProfile;
import dev.shadowsoffire.apothic_attributes.api.ALObjects;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * The power curve ({@link Balance}): health and damage that grow with levels and points, creatures that keep pace by
 * rank and level, and the old flat numbers (heals, hazards, armour) that keep their share of the bigger pools.
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BalanceGameTests {
    private static final String ARENA = "evaluation_zone";

    private static ServerPlayer player(GameTestHelper helper, PlayerClass cls) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "balance"));
        SystemAPI.reset(p);
        p.setGameMode(GameType.SURVIVAL);
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        SystemAPI.awaken(p, PlayerRank.E, cls);
        return p;
    }

    /** Levels a fresh hunter up to {@code level} (three points a level, none spent). */
    private static void levelTo(ServerPlayer p, int level) {
        long xp = 0;
        for (int l = SystemAPI.get(p).level(); l < level; l++) xp += Progression.xpToNext(l);
        SystemAPI.grantXp(p, xp, SystemAPI.XP_SOURCE_COMMAND);
    }

    private static boolean near(double a, double b, double tolerance) {
        return Math.abs(a - b) <= tolerance;
    }

    @GameTest(template = ARENA)
    public static void healthGrowsWithLevelsAndVitality(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerClass.WARRIOR);
        helper.assertTrue(near(p.getMaxHealth(), 20, 0.01), "a fresh hunter has the vanilla 20 health: " + p.getMaxHealth());
        levelTo(p, 11);
        SystemData d = SystemAPI.get(p);
        helper.assertTrue(d.level() == 11 && d.statPoints() == 30, "level 11 with 30 points: " + d.level() + " " + d.statPoints());
        helper.assertTrue(near(p.getMaxHealth(), 60, 0.01), "four health a level: " + p.getMaxHealth());
        helper.assertTrue(near(p.getHealth(), p.getMaxHealth(), 0.01), "a level up fills the new pool: " + p.getHealth());
        SystemAPI.allocateStats(p, Stat.VIT, 10);
        helper.assertTrue(near(p.getMaxHealth(), 90, 0.01), "ten VIT points are +50%: " + p.getMaxHealth());
        // and every level makes every blow 3% harder
        var levelDamage = p.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(BeyondSolo.id("stat/level_damage"));
        helper.assertTrue(levelDamage != null && near(levelDamage.amount(), 0.3, 1e-9), "+30% damage at level 11");
        double before = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
        SystemAPI.allocateStats(p, Stat.STR, 10);
        helper.assertTrue(near(p.getAttributeValue(Attributes.ATTACK_DAMAGE), before * 2, 0.001),
                "ten STR points double the blow: " + p.getAttributeValue(Attributes.ATTACK_DAMAGE));
        SystemAPI.reset(p);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void levelsGoToOneHundred(GameTestHelper helper) {
        helper.assertTrue(Balance.xpToNext(1) == 102, "level 1 needs 102: " + Balance.xpToNext(1));
        long at74 = Balance.xpToNext(74);
        helper.assertTrue(at74 > 95_000 && at74 < 110_000, "about 102,000 at level 74: " + at74);
        for (int l = 1; l < Balance.MAX_LEVEL; l++) {
            helper.assertTrue(Balance.xpToNext(l + 1) > Balance.xpToNext(l), "each level costs more than the last at " + l);
        }
        ServerPlayer p = player(helper, PlayerClass.MAGE);
        SystemAPI.grantXp(p, Long.MAX_VALUE / 4, SystemAPI.XP_SOURCE_COMMAND);
        SystemData d = SystemAPI.get(p);
        helper.assertTrue(d.level() == Balance.MAX_LEVEL && Progression.MAX_LEVEL == 100, "capped at 100: " + d.level());
        helper.assertTrue(d.statPoints() == 3 * 99, "three points a level: " + d.statPoints());
        helper.assertTrue(near(p.getMaxHealth(), 20 + 4 * 99, 0.01), "416 health at the cap before VIT: " + p.getMaxHealth());
        SystemAPI.reset(p);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void creaturesKeepPaceByRankAndLevel(GameTestHelper helper) {
        helper.assertTrue(Balance.health(PlayerRank.E) == 1 && Balance.damage(PlayerRank.E) == 1, "E is the baseline");
        helper.assertTrue(Balance.health(PlayerRank.S) > 50 && Balance.damage(PlayerRank.S) > 60, "S creatures are giants");
        for (PlayerRank r : PlayerRank.values()) {
            int home = Balance.homeLevel(r);
            helper.assertTrue(near(Balance.healthAt(home), Balance.health(r), 1e-6), r + " health by level matches its rank");
            helper.assertTrue(near(Balance.damageAt(home), Balance.damage(r), 1e-6), r + " damage by level matches its rank");
            helper.assertTrue(Balance.rankFor(home) == r, r + " is the rank made for level " + home + ": " + Balance.rankFor(home));
            helper.assertTrue(near(Balance.bossHealth(r), Balance.BOSS_HEALTH * Balance.health(r), 0.5), r + " boss health");
        }
        for (int l = 1; l < 100; l++) {
            helper.assertTrue(Balance.healthAt(l + 1) > Balance.healthAt(l) && Balance.damageAt(l + 1) > Balance.damageAt(l),
                    "the curve only climbs, at " + l);
        }
        helper.assertTrue(Balance.healthAt(120) > Balance.healthAt(100), "and on past the cap");
        for (double l = 1; l <= 150; l += 0.5) {
            helper.assertTrue(near(Balance.levelForStrength(Balance.strengthAt(l)), l, 1e-6), "strength reads back as its level, at " + l);
        }

        // the higher ranks' pools hold heavier kinds; each is evened out so its typical creature lands on the curve
        helper.assertTrue(near(Enemies.poolScale(PlayerRank.E), 1, 1e-9), "E's pool is the baseline");
        helper.assertTrue(EnemyRoster.basePower(PlayerRank.S) > EnemyRoster.basePower(PlayerRank.E) * 2, "an S pool is much heavier");
        helper.assertTrue(near(Enemies.rankHealth(PlayerRank.S) * EnemyRoster.basePower(PlayerRank.S),
                Balance.health(PlayerRank.S) * EnemyRoster.basePower(PlayerRank.E), 1e-6), "S's numbers are evened out by its pool");
        // and a kill is still worth the health the rank gave it before that
        Mob gloomfang = Enemies.spawn(helper.getLevel(), PlayerRank.S, EnemyModule.type(MonsterKind.GLOOMFANG),
                helper.absolutePos(new BlockPos(2, 2, 2)), EnemyTraits.NONE, -1);
        helper.assertTrue(gloomfang != null, "an S gloomfang");
        helper.assertTrue(near(Enemies.killWorth(gloomfang), gloomfang.getMaxHealth() / Enemies.poolScale(PlayerRank.S), 0.5),
                "its kill is worth its rank's full health: " + Enemies.killWorth(gloomfang));
        gloomfang.discard();

        // a gate's creature carries its rank's growth, and the growth can be read back
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        float base = zombie.getMaxHealth();
        Enemies.strength(zombie, Balance.health(PlayerRank.C), Balance.damage(PlayerRank.C));
        helper.assertTrue(near(zombie.getMaxHealth(), base * Balance.health(PlayerRank.C), 0.05), "C-rank health: " + zombie.getMaxHealth());
        helper.assertTrue(near(Enemies.healthGrowth(zombie), Balance.health(PlayerRank.C), 1e-6), "growth read back");
        zombie.discard();
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void flatHealsKeepTheirShare(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerClass.GUARDIAN);
        levelTo(p, 11);
        SystemData d = SystemAPI.get(p);
        // 60 health now, where it was 20 before (VIT is left alone: it also raises healing received)
        double scale = Balance.legacyScale(d);
        helper.assertTrue(near(scale, 3, 1e-6), "pool grew 3 times: " + scale);
        p.setHealth(10);
        p.heal(2);
        helper.assertTrue(near(p.getHealth(), 10 + 2 * scale, 0.01), "a flat heal grows with the pool: " + p.getHealth());
        float now = p.getHealth();
        Balance.healRaw(p, 5);
        helper.assertTrue(near(p.getHealth(), now + 5, 0.01), "a sized heal is left alone: " + p.getHealth());
        helper.assertTrue(!BalanceHooks.inBlow(), "no blow is being worked out");
        SystemAPI.reset(p);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void vanillaHazardsAreTheOnesThatGrow(GameTestHelper helper) {
        var level = helper.getLevel();
        ServerPlayer p = player(helper, PlayerClass.WARRIOR);
        Zombie plain = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        Zombie gate = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 1));
        gate.getPersistentData().putInt(Enemies.RANK_KEY, PlayerRank.B.ordinal());
        helper.assertTrue(BalanceHooks.sizedForVanilla(level.damageSources().fall()), "a fall is sized for 20 health");
        helper.assertTrue(BalanceHooks.sizedForVanilla(level.damageSources().mobAttack(plain)), "so is a creature the System never grew");
        helper.assertTrue(!BalanceHooks.sizedForVanilla(level.damageSources().mobAttack(gate)), "a gate's creature already grew");
        helper.assertTrue(!BalanceHooks.sizedForVanilla(level.damageSources().playerAttack(p)), "and so did a hunter");
        helper.assertTrue(!BalanceHooks.sizedForVanilla(com.beyondsolo.combat.CombatDamage.source(plain, com.beyondsolo.combat.DamageElement.POISON, null)),
                "the System's own elements grow on their own");
        plain.discard();
        gate.discard();
        SystemAPI.reset(p);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void armourKeepsItsShareAgainstBigBlows(GameTestHelper helper) {
        Zombie attacker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        Zombie small = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 2, 1));
        Zombie big = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(5, 2, 1));
        // no lucky blows: Apothic gives every creature a chance of a critical hit (half again, after armour) and of a dodge
        for (Zombie z : new Zombie[]{attacker, small, big}) {
            var crit = z.getAttribute(ALObjects.Attributes.CRIT_CHANCE);
            if (crit != null) crit.setBaseValue(0);
            var dodge = z.getAttribute(ALObjects.Attributes.DODGE_CHANCE);
            if (dodge != null) dodge.setBaseValue(0);
        }
        for (Zombie z : new Zombie[]{small, big}) {
            z.getAttribute(Attributes.ARMOR).setBaseValue(14);
            z.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(4);
        }
        big.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        big.setHealth(1000);
        small.setHealth(small.getMaxHealth());
        // the same blow, fifty times bigger on a body fifty times bigger
        small.hurt(helper.getLevel().damageSources().mobAttack(attacker), 8);
        big.hurt(helper.getLevel().damageSources().mobAttack(attacker), 400);
        float smallShare = (20 - small.getHealth()) / 20;
        float bigShare = (1000 - big.getHealth()) / 1000;
        helper.assertTrue(smallShare > 0 && smallShare < 8 / 20f, "armour blunts the small blow: " + smallShare);
        helper.assertTrue(near(bigShare, smallShare, 0.002), "and the big one just as much: " + bigShare + " vs " + smallShare);
        attacker.discard();
        small.discard();
        big.discard();
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void skillPowerFollowsTheClassLean(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerClass.WARRIOR);
        SystemData d = SystemAPI.get(p);
        helper.assertTrue(near(Balance.power(d), 1, 1e-9), "power 1 for a fresh hunter: " + Balance.power(d));
        levelTo(p, 11);
        helper.assertTrue(near(Balance.power(d), 1.3, 1e-9), "+3% a level: " + Balance.power(d));
        SystemAPI.allocateStats(p, Stat.INT, 10);
        helper.assertTrue(near(Balance.power(d), 1.3, 1e-9), "a warrior's INT adds no power: " + Balance.power(d));
        SystemAPI.allocateStats(p, Stat.STR, 10);
        helper.assertTrue(near(Balance.power(d), 1.3 * 1.3, 1e-9), "STR, the warrior's main stat, +3% a point: " + Balance.power(d));
        helper.assertTrue(near(Balance.powerFrom(d, Stat.STR), 0.3, 1e-9) && Balance.powerFrom(d, Stat.INT) == 0, "per stat, for the status screen");
        // SENSE drives bows: +10% a point and the level bonus
        SystemAPI.allocateStats(p, Stat.SENSE, 5);
        helper.assertTrue(near(Balance.rangedPower(d), 1.3 * 1.5, 1e-9), "five SENSE points are +50% with a bow: " + Balance.rangedPower(d));
        SystemAPI.reset(p);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void summonsAndShadowsKeepPaceWithTheirMaster(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerClass.MAGE);
        SystemData d = SystemAPI.get(p);
        helper.assertTrue(Balance.summonHealth(d) == 1 && Balance.summonDamage(d) == 1, "a fresh hunter's summons are as they were made");
        ShadowRecord r = ShadowService.give(p, EntityType.ZOMBIE, ShadowRank.SOLDIER, false);
        float plainHp = ShadowService.maxHealth(r, d.stat(Stat.INT));
        helper.assertTrue(near(ShadowService.maxHealth(r, d), plainHp, 0.01), "same numbers beside a fresh master");
        levelTo(p, 41);
        SystemAPI.allocateStats(p, Stat.INT, 60);
        helper.assertTrue(Balance.summonHealth(d) > 5 && Balance.summonDamage(d) > 5,
                "a B-rank master's summons grow: " + Balance.summonHealth(d) + " " + Balance.summonDamage(d));
        helper.assertTrue(near(ShadowService.maxHealth(r, d), ShadowService.maxHealth(r, d.stat(Stat.INT)) * Balance.summonHealth(d), 0.5),
                "and so do the shadows: " + ShadowService.maxHealth(r, d));
        helper.assertTrue(near(ShadowService.damage(r, d), ShadowService.damage(r, d.stat(Stat.INT)) * Balance.summonDamage(d), 0.05),
                "shadow blows too: " + ShadowService.damage(r, d));

        // a soul is sized as the creature was before the System grew it (+40% a rank, the old way)
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        float base = zombie.getMaxHealth();
        Enemies.strength(zombie, Balance.health(PlayerRank.A), Balance.damage(PlayerRank.A));
        zombie.getPersistentData().putInt(Enemies.RANK_KEY, PlayerRank.A.ordinal());
        float[] soul = ShadowService.soulSize(zombie);
        helper.assertTrue(near(soul[0], base * (1 + 0.4f * PlayerRank.A.tier()), 0.05), "an A-rank soul is an old A-rank body: " + soul[0]);
        zombie.discard();
        ShadowService.reset(p);
        SystemAPI.reset(p);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void rewardsKeepTheirShareOfALevel(GameTestHelper helper) {
        // quests grow with how much a level costs
        helper.assertTrue(RankScaling.xp(100, 0, 1) == Math.round(100 * Balance.xpGrowth(1)), "a level-1 quest");
        helper.assertTrue(RankScaling.xp(100, 0, 50) == Math.round(100 * (1 + 0.04 * 49) * 2.0), "a level-50 quest: " + RankScaling.xp(100, 0, 50));
        // gear that rolled flat max health before keeps it as a share of the pool
        helper.assertTrue(near(BonusStat.MAX_HEALTH.amount(1.5f), 0.03, 1e-6), "an old +1.5 roll is +3%");
        helper.assertTrue(near(BonusStat.MAX_HEALTH.amount(0.03f), 0.03, 1e-6), "a new roll is what it says");
        helper.assertTrue(BonusStat.ARMOR.amount(0.8f) == 0.8f, "other stats are left alone");
        helper.succeed();
    }

    /** The rival hunters stand near their ranks' home levels and stay under the cap, so a hunter at the cap can top the board. */
    @GameTest(template = ARENA)
    public static void aHunterAtTheCapCanTopTheRankings(GameTestHelper helper) {
        for (Rival r : Rivals.ALL) {
            float last = r.levelOn(1_000_000L);
            helper.assertTrue(last < Balance.MAX_LEVEL, r.name() + " stays under the level cap: " + last);
            helper.assertTrue(Math.abs(r.levelOn(0) - Balance.homeLevel(r.rank())) <= 12, r.name() + " starts near their rank's home level: " + r.levelOn(0));
        }
        ServerPlayer p = player(helper, PlayerClass.WARRIOR);
        levelTo(p, Balance.MAX_LEVEL);
        float standing = HiddenLevel.hunterLevel(SystemAPI.get(p));
        helper.assertTrue(standing >= Balance.MAX_LEVEL, "a hunter at the cap stands at it: " + standing);
        SystemAPI.reset(p);
        helper.succeed();
    }

    private BalanceGameTests() {}
}
