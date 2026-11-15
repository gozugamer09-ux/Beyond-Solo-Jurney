package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.enemy.Affinity;
import com.beyondsolo.enemy.Affix;
import com.beyondsolo.enemy.Enemies;
import com.beyondsolo.enemy.EnemyHooks;
import com.beyondsolo.enemy.EnemyModule;
import com.beyondsolo.enemy.EnemyRoster;
import com.beyondsolo.enemy.EnemyTraits;
import com.beyondsolo.enemy.MonsterKind;
import com.beyondsolo.enemy.Variant;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.gate.GateLoot;
import com.beyondsolo.gate.GateTheme;
import com.beyondsolo.registry.ModEntities;
import com.beyondsolo.skill.SkillElement;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** Enemy roster and boss pool tests. Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EnemyGameTests {
    private static final String ARENA = "evaluation_zone";

    @GameTest(template = ARENA)
    public static void rosterIsDeterministicAndMixed(GameTestHelper helper) {
        for (PlayerRank rank : PlayerRank.values()) {
            for (long seed = 0; seed < 40; seed++) {
                EnemyRoster.Mix a = EnemyRoster.forSeed(rank, seed);
                EnemyRoster.Mix b = EnemyRoster.forSeed(rank, seed);
                helper.assertTrue(a.equals(b), "the same seed holds the same creatures at " + rank);
                long own = a.entries().stream().filter(EnemyRoster.Entry::own).count();
                helper.assertTrue(own >= 2, "at least two of the gate's own creatures at " + rank + ": " + EnemyRoster.describe(a));
                helper.assertTrue(a.entries().size() >= 3, "three or more kinds at " + rank);
            }
        }
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void bossesVaryAndKeepToTheirRanks(GameTestHelper helper) {
        for (PlayerRank rank : PlayerRank.values()) {
            EnumSet<BossKind> seen = EnumSet.noneOf(BossKind.class);
            for (long seed = 0; seed < 300; seed++) {
                BossKind k = BossKind.forSeed(rank, seed);
                helper.assertTrue(k.livesAt(rank), k + " lives at " + rank);
                helper.assertTrue(k == BossKind.forSeed(rank, seed), "the same gate keeps its boss");
                seen.add(k);
            }
            helper.assertTrue(seen.size() >= 3, "at least three different bosses at " + rank + ": " + seen);
        }
        int ascended = 0;
        for (long seed = 0; seed < 1200; seed++) if (BossKind.ascendedForSeed(seed)) ascended++;
        helper.assertTrue(ascended > 50 && ascended < 160, "about one gate in twelve is ascended: " + ascended);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void everyCreatureSpawnsWithItsTraits(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        List<Mob> spawned = new ArrayList<>();
        for (MonsterKind kind : MonsterKind.values()) {
            EnemyTraits traits = new EnemyTraits(Affinity.FROST, Variant.ELITE, Affix.mask(EnumSet.of(Affix.ARMORED, Affix.SWIFT)));
            Mob plain = Enemies.spawn(level, PlayerRank.E, EnemyModule.type(kind), at, EnemyTraits.NONE, -1);
            Mob elite = Enemies.spawn(level, PlayerRank.B, EnemyModule.type(kind), at, traits, -1);
            helper.assertTrue(plain != null && elite != null, kind + " spawns");
            spawned.add(plain);
            spawned.add(elite);
            EnemyTraits back = EnemyTraits.of(elite);
            helper.assertTrue(back.equals(traits), kind + " keeps its traits: " + back);
            helper.assertTrue(elite.getMaxHealth() > plain.getMaxHealth() * 1.5f, kind + " elite at B is much tougher: " + elite.getMaxHealth() + " vs " + plain.getMaxHealth());
            helper.assertTrue(elite.getAttributeValue(Attributes.ARMOR) > plain.getAttributeValue(Attributes.ARMOR), kind + " armoured affix adds armour");
            helper.assertTrue(Enemies.rankOf(elite) == PlayerRank.B, kind + " remembers its rank");
        }
        // vanilla creatures take traits too
        Mob zombie = Enemies.spawn(level, PlayerRank.C, EntityType.ZOMBIE, at, new EnemyTraits(Affinity.FLAME, Variant.VETERAN, 0), -1);
        helper.assertTrue(zombie != null && EnemyTraits.of(zombie).affinity() == Affinity.FLAME, "a zombie can be emberborn");
        spawned.add(zombie);
        spawned.forEach(Mob::discard);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void affinityDecidesElementalDamage(GameTestHelper helper) {
        Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        helper.assertTrue(EnemyHooks.skillMultiplier(z, SkillElement.WATER) == 1f, "an ordinary zombie has no weakness");
        new EnemyTraits(Affinity.FLAME, Variant.NORMAL, 0).save(z);
        helper.assertTrue(EnemyHooks.skillMultiplier(z, SkillElement.WATER) == 1.5f, "water hurts a flame creature more");
        helper.assertTrue(EnemyHooks.skillMultiplier(z, SkillElement.FIRE) == 0.5f, "fire hurts it less");
        helper.assertTrue(EnemyHooks.skillMultiplier(z, SkillElement.LIGHTNING) == 1f, "other elements as usual");
        new EnemyTraits(Affinity.FLAME, Variant.NORMAL, Affix.mask(EnumSet.of(Affix.WARDED))).save(z);
        helper.assertTrue(EnemyHooks.skillMultiplier(z, SkillElement.WATER) < 1.5f, "a warded creature shrugs off some of it");
        for (Affinity a : Affinity.values()) {
            if (a == Affinity.NONE) continue;
            helper.assertTrue(a.multiplier(a.weakness) > 1f && a.multiplier(a.element) < 1f, a + " has a weakness and a strength");
        }
        z.discard();
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void everyBossConfigures(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (BossKind kind : BossKind.values()) {
            for (boolean ascended : new boolean[]{false, true}) {
                GateBossEntity boss = ModEntities.GATE_BOSS.get().create(level);
                helper.assertTrue(boss != null, "a boss");
                boss.configure(kind.maxRank, kind, ascended);
                helper.assertTrue(boss.kind() == kind && boss.rank() == kind.maxRank && boss.isAscended() == ascended, kind + " keeps its kind");
                float hp = GateTheme.of(kind.maxRank).bossHealth() * kind.healthScale * (ascended ? 1.4f : 1f);
                helper.assertTrue(Math.abs(boss.getMaxHealth() - hp) < 0.01f, kind + " has its health: " + boss.getMaxHealth() + " vs " + hp);
                float w = kind.width * (ascended ? 1.15f : 1f);
                helper.assertTrue(Math.abs(boss.getBbWidth() - w) < 0.05f, kind + " has its own size: " + boss.getBbWidth() + " vs " + w);
                helper.assertTrue(EnemyTraits.of(boss).affinity() == kind.affinity, kind + " has its affinity");
                helper.assertTrue(boss.getCustomName() != null && !boss.getCustomName().getString().startsWith("gate."), kind + " has a name: " + boss.getCustomName());
                boss.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void everyBossFightsAndDies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<GateBossEntity> bosses = new ArrayList<>();
        int i = 0;
        int[][] spots = {{2, 2}, {4, 2}, {2, 4}, {4, 4}, {3, 3}, {3, 1}};
        for (BossKind kind : BossKind.values()) {
            GateBossEntity boss = ModEntities.GATE_BOSS.get().create(level);
            BlockPos at = helper.absolutePos(new BlockPos(spots[i][0], 2, spots[i][1]));
            boss.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
            boss.configure(kind.minRank, kind, false);
            level.addFreshEntity(boss);
            boss.wake();
            bosses.add(boss);
            i++;
        }
        // something to fight: its moves run against a zombie for a while
        helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 4));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    for (GateBossEntity b : bosses) helper.assertTrue(b.introTicks() <= 0, b.kind() + " intro over: " + b.introTicks() + " alive " + b.isAlive() + " removed " + b.isRemoved() + " at " + b.blockPosition());
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    for (GateBossEntity b : bosses) {
                        b.hurt(level.damageSources().generic(), 100_000f);
                        helper.assertTrue(!b.isAlive(), b.kind() + " is defeated: hp " + b.getHealth());
                    }
                })
                .thenWaitUntil(() -> {
                    for (GateBossEntity b : bosses) helper.assertTrue(b.isRemoved(), b.kind() + " plays out its death");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void everyBossHasSignatureLoot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        for (BossKind kind : BossKind.values()) {
            List<ItemStack> loot = GateLoot.signature(level, kind, kind.maxRank, false, at);
            helper.assertTrue(!loot.isEmpty(), kind + " drops its signature loot");
            List<ItemStack> asc = GateLoot.signature(level, kind, kind.maxRank, true, at);
            helper.assertTrue(asc.size() > loot.size() - 4, kind + " ascended drops more");
        }
        helper.succeed();
    }

    private EnemyGameTests() {}
}
