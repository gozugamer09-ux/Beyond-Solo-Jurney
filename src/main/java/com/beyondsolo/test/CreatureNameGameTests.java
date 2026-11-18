package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.enemy.Affinity;
import com.beyondsolo.enemy.Affix;
import com.beyondsolo.enemy.Enemies;
import com.beyondsolo.enemy.EnemyModule;
import com.beyondsolo.enemy.EnemyRoster;
import com.beyondsolo.enemy.EnemyTraits;
import com.beyondsolo.enemy.HiddenLevel;
import com.beyondsolo.enemy.HiddenLevel.Threat;
import com.beyondsolo.enemy.MonsterKind;
import com.beyondsolo.enemy.Variant;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.registry.ModEntities;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.Progression;
import com.beyondsolo.system.Stat;
import com.beyondsolo.system.SystemAPI;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Creatures' hidden levels and the colour of their names: what the level follows, where each gate rank's creatures
 * sit, how a hunter's own level is counted and which colour a pair of levels gives.
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CreatureNameGameTests {
    private static final String ARENA = "evaluation_zone";

    /** A plain creature of a kind and rank at its kind's own size, its level worked out after that. */
    private static Mob creature(GameTestHelper helper, MonsterKind kind, PlayerRank rank, EnemyTraits traits) {
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        Mob m = Enemies.spawn(helper.getLevel(), rank, EnemyModule.type(kind), at, traits, -1);
        helper.assertTrue(m != null, kind + " spawns");
        Enemies.applySize(m, 1f);
        m.setNoAi(true);
        HiddenLevel.refresh(m);
        return m;
    }

    private static int level(GameTestHelper helper, MonsterKind kind, PlayerRank rank, Variant variant) {
        Mob m = creature(helper, kind, rank, new EnemyTraits(Affinity.NONE, variant, 0));
        int level = HiddenLevel.of(m);
        m.discard();
        return level;
    }

    private static ServerPlayer hunter(GameTestHelper helper, PlayerRank rank) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "namereader"));
        SystemAPI.reset(p);
        p.setGameMode(GameType.SURVIVAL);
        SystemAPI.awaken(p, rank, PlayerClass.WARRIOR);
        return p;
    }

    /** The level follows strength: a higher gate, a bigger variant, a bigger body, a boss, all stand higher. */
    @GameTest(template = ARENA)
    public static void levelFollowsStrength(GameTestHelper helper) {
        int e = level(helper, MonsterKind.GLOOMFANG, PlayerRank.E, Variant.NORMAL);
        int c = level(helper, MonsterKind.GLOOMFANG, PlayerRank.C, Variant.NORMAL);
        int s = level(helper, MonsterKind.GLOOMFANG, PlayerRank.S, Variant.NORMAL);
        helper.assertTrue(e < c && c < s, "a higher gate's creature stands higher: E " + e + ", C " + c + ", S " + s);
        int veteran = level(helper, MonsterKind.TUSKBRUTE, PlayerRank.B, Variant.VETERAN);
        int normal = level(helper, MonsterKind.TUSKBRUTE, PlayerRank.B, Variant.NORMAL);
        int elite = level(helper, MonsterKind.TUSKBRUTE, PlayerRank.B, Variant.ELITE);
        int champion = level(helper, MonsterKind.TUSKBRUTE, PlayerRank.B, Variant.CHAMPION);
        helper.assertTrue(normal < veteran && veteran < elite && elite < champion,
                "variants stand in order: " + normal + " < " + veteran + " < " + elite + " < " + champion);
        helper.assertTrue(level(helper, MonsterKind.BROODLING, PlayerRank.D, Variant.NORMAL) < level(helper, MonsterKind.SHARDBACK, PlayerRank.D, Variant.NORMAL),
                "a broodling is a lesser thing than a shardback");

        Mob small = creature(helper, MonsterKind.RIMECLAW, PlayerRank.A, EnemyTraits.NONE);
        int usual = HiddenLevel.of(small);
        Enemies.applySize(small, 2f);
        int giant = HiddenLevel.refresh(small);
        helper.assertTrue(giant > usual, "a giant stands higher than one of its usual size: " + usual + " -> " + giant);
        small.discard();

        GateBossEntity boss = ModEntities.GATE_BOSS.get().create(helper.getLevel());
        helper.assertTrue(boss != null, "boss made");
        boss.configure(PlayerRank.C, BossKind.BRUTE, false);
        Enemies.applySize(boss, 1f);
        int bossLevel = HiddenLevel.compute(boss);
        boss.configure(PlayerRank.C, BossKind.BRUTE, true);
        Enemies.applySize(boss, 1f);
        int ascended = HiddenLevel.compute(boss);
        int pack = level(helper, MonsterKind.TUSKBRUTE, PlayerRank.C, Variant.CHAMPION);
        helper.assertTrue(bossLevel > level(helper, MonsterKind.TUSKBRUTE, PlayerRank.C, Variant.NORMAL), "a boss stands over its gate's creatures");
        helper.assertTrue(ascended > bossLevel, "an ascended boss stands over a plain one: " + bossLevel + " -> " + ascended + " (champion " + pack + ")");
        helper.succeed();
    }

    /**
     * Where each gate rank's typical plain creature sits: about the level its rank's armour can first be worn at (E 1,
     * D 10, C 20, B 30, A 40, S 50). When the balancing pass moves creatures' numbers, move HiddenLevel's curve with
     * them (or this line), so the colours keep meaning the same.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void typicalCreatureOfARankSitsAtItsGearLevel(GameTestHelper helper) {
        StringBuilder found = new StringBuilder();
        boolean ok = true;
        for (PlayerRank rank : PlayerRank.values()) {
            double sum = 0;
            int weight = 0;
            for (EnemyRoster.Entry entry : EnemyRoster.pool(rank)) {
                MonsterKind kind = EnemyRoster.kindOf(entry.type().get());
                Mob m = creature(helper, kind, rank, EnemyTraits.NONE);
                sum += HiddenLevel.power(m) * entry.weight();
                weight += entry.weight();
                m.discard();
            }
            int typical = HiddenLevel.levelFor(sum / weight);
            int gear = rank == PlayerRank.E ? 1 : rank.tier() * 10;
            found.append(rank).append(' ').append(typical).append(" (gear ").append(gear).append(")  ");
            if (Math.abs(typical - gear) > 4) ok = false;
        }
        helper.assertTrue(ok, "each rank's typical creature should sit near its gear level: " + found);
        helper.succeed();
    }

    /** A hunter's side counts every stat point they have, three to a level: rank bonus and unspent points included. */
    @GameTest(template = ARENA)
    public static void hunterLevelCountsEveryPoint(GameTestHelper helper) {
        ServerPlayer e = hunter(helper, PlayerRank.E);
        helper.assertTrue(Math.abs(HiddenLevel.hunterLevel(SystemAPI.get(e)) - 1f) < 0.01f, "a fresh E-rank hunter stands at 1");
        long xp = 0;
        for (int l = 1; l < 10; l++) xp += Progression.xpToNext(l);
        SystemAPI.grantXp(e, xp, SystemAPI.XP_SOURCE_COMMAND);
        helper.assertTrue(SystemAPI.get(e).level() == 10, "level 10 now");
        float unspent = HiddenLevel.hunterLevel(SystemAPI.get(e));
        SystemAPI.allocateStats(e, Stat.STR, 9);
        float spent = HiddenLevel.hunterLevel(SystemAPI.get(e));
        helper.assertTrue(Math.abs(unspent - spent) < 0.01f, "spending points changes nothing: " + unspent + " vs " + spent);
        helper.assertTrue(Math.abs(spent - SystemAPI.get(e).level()) < 0.01f, "with only level points a hunter stands at their level: " + spent);

        ServerPlayer s = hunter(helper, PlayerRank.S);
        float fresh = HiddenLevel.hunterLevel(SystemAPI.get(s));
        helper.assertTrue(Math.abs(fresh - (1 + PlayerRank.S.bonusStatPoints() / HiddenLevel.POINTS_PER_LEVEL)) < 0.01f,
                "an S-rank's bonus points count: " + fresh);
        helper.succeed();
    }

    /** White below, orange around and a little above, red far above; never back down as a creature gets stronger. */
    @GameTest(template = ARENA)
    public static void namesColourByStanding(GameTestHelper helper) {
        helper.assertTrue(Threat.of(1, 1f) == Threat.EVEN, "the same level is orange");
        helper.assertTrue(Threat.of(30, 30f) == Threat.EVEN && Threat.of(150, 150f) == Threat.EVEN, "the same level is orange at any level");
        helper.assertTrue(Threat.of(33, 30f) == Threat.EVEN, "a little above is orange");
        helper.assertTrue(Threat.of(1, 10f) == Threat.WEAKER && Threat.of(20, 40f) == Threat.WEAKER, "far below is white");
        helper.assertTrue(Threat.of(28, 30f) == Threat.EVEN, "a step below is still about your level");
        helper.assertTrue(Threat.of(30, 10f) == Threat.FAR_STRONGER && Threat.of(80, 40f) == Threat.FAR_STRONGER, "far above is red");
        for (int h = 1; h <= 300; h += 7) {
            Threat last = Threat.WEAKER;
            for (int c = 1; c <= 1000; c++) {
                Threat t = Threat.of(c, h);
                helper.assertTrue(t.ordinal() >= last.ordinal(), "colours climb with the creature: hunter " + h + ", creature " + c);
                last = t;
            }
            helper.assertTrue(last == Threat.FAR_STRONGER, "a far stronger creature shows red to a level " + h);
        }
        for (int l = 1; l <= 600; l++) {
            helper.assertTrue(HiddenLevel.levelFor(HiddenLevel.powerAt(l)) == l, "level " + l + " is its own hunter's even match");
        }
        helper.succeed();
    }

    /** What a fresh hunter reads at the gates: an E gate's lot is no worse than orange, its champions and its boss are red. */
    @GameTest(template = ARENA)
    public static void freshHunterReadsTheGates(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerRank.E);
        for (EnemyRoster.Entry entry : EnemyRoster.pool(PlayerRank.E)) {
            Mob m = creature(helper, EnemyRoster.kindOf(entry.type().get()), PlayerRank.E, EnemyTraits.NONE);
            helper.assertTrue(HiddenLevel.threat(m, p) != Threat.FAR_STRONGER, "an E gate's plain " + EntityType.getKey(m.getType()) + " is no red to a fresh hunter");
            m.discard();
        }
        Mob champion = creature(helper, MonsterKind.GLOOMFANG, PlayerRank.E, new EnemyTraits(Affinity.FROST, Variant.CHAMPION, 0));
        helper.assertTrue(HiddenLevel.threat(champion, p) == Threat.FAR_STRONGER, "an E gate's champion is red to a fresh hunter");
        Mob far = creature(helper, MonsterKind.PIT_RAVAGER, PlayerRank.A, EnemyTraits.NONE);
        helper.assertTrue(HiddenLevel.threat(far, p) == Threat.FAR_STRONGER, "an A gate's creature is red to a fresh hunter");

        ServerPlayer veteran = hunter(helper, PlayerRank.E);
        SystemAPI.addStatPoints(veteran, Math.round(HiddenLevel.POINTS_PER_LEVEL * 59));
        helper.assertTrue(HiddenLevel.threat(champion, veteran) == Threat.WEAKER, "an E gate's champion is white to a level 60");
        helper.assertTrue(HiddenLevel.threat(far, veteran) != Threat.FAR_STRONGER, "an A gate's plain creature is no red to a level 60");
        champion.discard();
        far.discard();
        helper.succeed();
    }

    /** Who has a hidden level: the roster's creatures and the bosses; not a plain zombie, not a player. */
    @GameTest(template = ARENA)
    public static void onlyTheSystemsCreaturesHaveOne(GameTestHelper helper) {
        Mob m = creature(helper, MonsterKind.GUTTERKIN, PlayerRank.D, EnemyTraits.NONE);
        helper.assertTrue(HiddenLevel.has(m), "a gate creature has a hidden level");
        GateBossEntity boss = ModEntities.GATE_BOSS.get().create(helper.getLevel());
        helper.assertTrue(boss != null && HiddenLevel.has(boss), "a boss has a hidden level");
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 4));
        helper.assertTrue(!HiddenLevel.has(zombie), "an overworld zombie has none");
        helper.assertTrue(!HiddenLevel.has(hunter(helper, PlayerRank.C)), "a hunter has none");
        m.discard();
        zombie.discard();
        helper.succeed();
    }

    /** The level is kept once worked out, and worked out again when something changes the creature after it appeared. */
    @GameTest(template = ARENA)
    public static void levelIsKeptAndRefreshed(GameTestHelper helper) {
        Mob m = creature(helper, MonsterKind.HIVE_REAVER, PlayerRank.B, EnemyTraits.NONE);
        int before = HiddenLevel.of(m);
        var hp = m.getAttribute(Attributes.MAX_HEALTH);
        hp.addPermanentModifier(new AttributeModifier(BeyondSolo.id("test_fury"), 3.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        helper.assertTrue(HiddenLevel.of(m) == before, "the kept level holds until it is refreshed");
        int after = HiddenLevel.refresh(m);
        helper.assertTrue(after > before && HiddenLevel.of(m) == after, "refreshing raises it: " + before + " -> " + after);
        m.discard();
        helper.succeed();
    }

    /** A splitting creature's halves are weaker than it was, and know it. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void splitHalvesStandLower(GameTestHelper helper) {
        Mob parent = creature(helper, MonsterKind.SHARDBACK, PlayerRank.C, new EnemyTraits(Affinity.NONE, Variant.ELITE, Affix.mask(EnumSet.of(Affix.SPLITTING))));
        int whole = HiddenLevel.of(parent);
        parent.kill();
        helper.runAfterDelay(2, () -> {
            var halves = helper.getLevel().getEntitiesOfClass(Mob.class, parent.getBoundingBox().inflate(4),
                    e -> e != parent && e.getType() == parent.getType() && e.isAlive());
            helper.assertTrue(halves.size() == 2, "two halves, found " + halves.size());
            for (Mob half : halves) {
                helper.assertTrue(HiddenLevel.of(half) < whole, "a half stands lower than the whole: " + HiddenLevel.of(half) + " vs " + whole);
                half.discard();
            }
            helper.succeed();
        });
    }
}
