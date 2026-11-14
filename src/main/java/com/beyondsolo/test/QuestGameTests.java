package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.quest.ObjectiveType;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestBuilder;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestData;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTemplate;
import com.beyondsolo.quest.QuestTemplates;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.quest.Targets;
import com.beyondsolo.quest.trigger.QuestTriggers;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Quest engine tests. Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class QuestGameTests {
    private static final String ARENA = "evaluation_zone";

    private static ServerPlayer player(GameTestHelper helper, PlayerRank rank) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "quester"));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        p.removeAllEffects();
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        if (rank != null) {
            SystemAPI.awaken(p, rank, PlayerClass.WARRIOR);
            QuestTestAccess.markStarted(p);
        }
        return p;
    }

    private static Quest only(GameTestHelper helper, ServerPlayer p, String template) {
        for (Quest q : QuestEngine.data(p).all()) if (q.template().equals(template)) return q;
        helper.fail("no quest " + template);
        return null;
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void evaluationHandsOutSystemQuests(GameTestHelper helper) {
        ServerPlayer p = player(helper, null);
        QuestEngine.tick(p);
        helper.assertTrue(QuestEngine.data(p).all().isEmpty(), "no quests before the evaluation");
        SystemAPI.awaken(p, PlayerRank.C, PlayerClass.MAGE);
        QuestEngine.tick(p);
        QuestData data = QuestEngine.data(p);
        helper.assertTrue(data.started(), "quest line should start");
        Quest first = only(helper, p, QuestTemplates.PROOF_OF_STRENGTH);
        helper.assertTrue(first.status() == Quest.Status.ACTIVE, "first System quest is active");
        // the daily follows a few seconds later
        helper.runAfterDelay(20 * 4 + 5, () -> {
            helper.assertTrue(QuestEngine.data(p).has(QuestTemplates.DAILY), "daily quest should be handed out");
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void killsCountAndCompletionRewards(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E);
        Quest q = QuestEngine.start(p, QuestTemplates.PROOF_OF_STRENGTH, QuestTemplate.Context.NONE);
        int need = q.objectives().get(0).required();
        helper.assertTrue(need == 5, "E-rank needs 5 kills, got " + need);
        long xpBefore = SystemAPI.get(p).xp();
        int levelBefore = SystemAPI.get(p).level();
        int pointsBefore = SystemAPI.get(p).statPoints();
        for (int i = 0; i < need; i++) {
            Zombie z = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
            z.hurt(p.damageSources().playerAttack(p), 1000);
        }
        helper.assertTrue(!QuestEngine.data(p).has(QuestTemplates.PROOF_OF_STRENGTH), "quest should be complete");
        helper.assertTrue(QuestEngine.data(p).hasFinished(QuestTemplates.PROOF_OF_STRENGTH), "quest should be recorded as finished");
        helper.assertTrue(SystemAPI.get(p).level() > levelBefore || SystemAPI.get(p).xp() > xpBefore, "XP reward");
        helper.assertTrue(SystemAPI.get(p).statPoints() > pointsBefore, "stat point reward");
        helper.assertTrue(p.getInventory().countItem(Items.BREAD) == 6, "bread reward");
        // the next quest of the line starts a few seconds later
        helper.runAfterDelay(20 * 5 + 5, () -> {
            helper.assertTrue(QuestEngine.data(p).has(QuestTemplates.PROVISIONS), "next System quest should start");
            helper.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void rankScalesCountsAndRewards(GameTestHelper helper) {
        ServerPlayer e = player(helper, PlayerRank.E);
        Quest qe = QuestTemplates.create(QuestTemplates.SPAWNER_NEST, e, QuestTemplate.Context.NONE);
        ServerPlayer s = player(helper, PlayerRank.S);
        Quest qs = QuestTemplates.create(QuestTemplates.SPAWNER_NEST, s, QuestTemplate.Context.NONE);
        helper.assertTrue(qs.objectives().get(0).required() > qe.objectives().get(0).required(), "S needs more kills");
        helper.assertTrue(qs.rewards().xp() > qe.rewards().xp() * 2, "S earns much more XP");
        helper.assertTrue(qs.penalty().exhaustion() > qe.penalty().exhaustion(), "S is punished harder");
        helper.assertTrue(qs.penalty().debuffSeconds() > qe.penalty().debuffSeconds(), "S debuffs last longer");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void spawnerNestTriggerStartsEmergency(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.D);
        BlockPos spawner = new BlockPos(5, 2, 5);
        helper.setBlock(spawner, Blocks.SPAWNER);
        helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(4, 2, 4));
        helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(4, 2, 3));
        QuestData data = QuestEngine.data(p);
        helper.assertTrue(QuestTriggers.fire(p, data, QuestTriggers.SPAWNER_NEST, false), "trigger should fire");
        Quest q = only(helper, p, QuestTemplates.SPAWNER_NEST);
        helper.assertTrue(q.status() == Quest.Status.ACTIVE && q.isForced(), "emergencies start right away");
        helper.assertTrue(q.area() != null && q.area().center().equals(helper.absolutePos(spawner)), "area is the spawner");
        helper.assertTrue(q.deadline() > 0, "emergency has a clock");
        // same spawner doesn't fire twice
        QuestEngine.fail(p, q, com.beyondsolo.quest.QuestEvent.FailReason.ABANDONED);
        helper.assertTrue(!QuestTriggers.fire(p, data, QuestTriggers.SPAWNER_NEST, false), "same nest only once a day");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void areaObjectivesOnlyCountInside(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E);
        BlockPos center = helper.absolutePos(new BlockPos(3, 2, 3));
        Quest q = QuestBuilder.of("beyondsolo:test/area", QuestCategory.EMERGENCY, p).title(Component.literal("t"))
                .area(p, center, 5, 0).killInArea(Targets.HOSTILE, 2).breakInArea("minecraft:spawner", 1).build();
        QuestEngine.start(p, q);
        QuestEngine.progress(p, ObjectiveType.KILL, o -> true, 1, center.offset(40, 0, 0).getCenter());
        helper.assertTrue(q.objectives().get(0).progress() == 0, "kills far away don't count");
        QuestEngine.progress(p, ObjectiveType.KILL, o -> true, 1, center.getCenter());
        helper.assertTrue(q.objectives().get(0).progress() == 1, "kills inside count");
        // breaking the spawner inside the area (through the real block event)
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.SPAWNER);
        p.gameMode.destroyBlock(center);
        helper.assertTrue(q.objectives().get(1).done(), "spawner break should count");
        QuestEngine.progress(p, ObjectiveType.KILL, o -> true, 1, center.getCenter());
        helper.assertTrue(!QuestEngine.data(p).has("beyondsolo:test/area"), "quest done");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void timeoutFailsWithPenalty(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.B);
        Quest q = QuestEngine.start(p, QuestTemplates.AMBUSH, QuestTemplate.Context.at(p.blockPosition()));
        float before = SystemAPI.get(p).exhaustion();
        QuestTestAccess.expire(p, q);
        QuestEngine.tick(p);
        QuestData data = QuestEngine.data(p);
        helper.assertTrue(!data.has(QuestTemplates.AMBUSH), "quest should be gone");
        helper.assertTrue(data.failedCount() == 1, "counted as failed");
        helper.assertTrue(SystemAPI.get(p).exhaustion() > before + 20, "penalty adds exhaustion");
        helper.assertTrue(p.hasEffect(MobEffects.WEAKNESS) && p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "penalty debuffs");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void fleeingTheAreaFails(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E);
        Quest q = QuestEngine.start(p, QuestTemplates.SPAWNER_NEST, QuestTemplate.Context.at(p.blockPosition()));
        p.moveTo(p.getX() + 200, p.getY(), p.getZ());
        QuestEngine.tick(p);
        helper.assertTrue(!QuestEngine.data(p).has(QuestTemplates.SPAWNER_NEST), "running away fails the nest");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void offersCanBeAcceptedOrExpire(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E);
        Quest offer = QuestTemplates.create(QuestTemplates.DEEP_VEINS, p, QuestTemplate.Context.NONE);
        QuestEngine.offer(p, offer, 100);
        QuestData data = QuestEngine.data(p);
        helper.assertTrue(offer.status() == Quest.Status.OFFERED && data.offers().size() == 1, "offered, not active");
        QuestEngine.progress(p, ObjectiveType.BREAK, o -> true, 5, null);
        helper.assertTrue(offer.objectives().get(0).progress() == 0, "offers don't count progress");
        helper.assertTrue(QuestEngine.acceptLatest(p), "accept");
        helper.assertTrue(offer.status() == Quest.Status.ACTIVE && offer.deadline() > 0, "active with a clock");

        Quest other = QuestTemplates.create(QuestTemplates.DEEP_VEINS, p, QuestTemplate.Context.NONE);
        QuestEngine.offer(p, other, 1);
        helper.runAfterDelay(3, () -> {
            QuestEngine.tick(p);
            helper.assertTrue(data.find(other.id()) == null, "expired offers disappear");
            helper.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void collectAndLevelObjectivesFollowThePlayer(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E);
        Quest q = QuestEngine.start(p, QuestTemplates.PROVISIONS, QuestTemplate.Context.NONE);
        p.getInventory().add(new ItemStack(Items.IRON_INGOT, 3));
        QuestEngine.tick(p);
        helper.assertTrue(q.objectives().get(0).progress() == 3, "counts iron held, got " + q.objectives().get(0).progress());
        p.getInventory().add(new ItemStack(Items.IRON_INGOT, 10));
        p.getInventory().add(new ItemStack(Items.OAK_LOG, 32));
        QuestEngine.tick(p);
        helper.assertTrue(!QuestEngine.data(p).has(QuestTemplates.PROVISIONS), "done once everything is held");
        Quest growth = QuestEngine.start(p, QuestTemplates.GROWTH, QuestTemplate.Context.NONE);
        helper.assertTrue(growth.objectives().get(0).progress() == SystemAPI.get(p).level(), "level objective reads the level");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void dailyFailsAtDawnAndRenews(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E);
        QuestData data = QuestEngine.data(p);
        Quest daily = QuestEngine.start(p, QuestTemplates.DAILY, QuestTemplate.Context.NONE);
        long time = helper.getLevel().getDayTime();
        helper.getLevel().setDayTime(time + 24000);
        QuestEngine.tick(p);
        helper.getLevel().setDayTime(time);
        helper.assertTrue(data.find(daily.id()) == null, "yesterday's daily failed");
        helper.assertTrue(data.failedCount() >= 1, "counted as failed");
        helper.assertTrue(data.has(QuestTemplates.DAILY), "a new daily started");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void deathFailsEmergencies(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E);
        QuestEngine.start(p, QuestTemplates.AMBUSH, QuestTemplate.Context.at(p.blockPosition()));
        Quest normal = QuestEngine.start(p, QuestTemplates.DEEP_VEINS, QuestTemplate.Context.NONE);
        QuestEngine.onDeath(p);
        QuestData data = QuestEngine.data(p);
        helper.assertTrue(!data.has(QuestTemplates.AMBUSH), "emergency fails on death");
        helper.assertTrue(data.find(normal.id()) != null, "other quests survive death");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void questDataSavesAndLoads(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.A);
        Quest q = QuestEngine.start(p, QuestTemplates.SPAWNER_NEST, new QuestTemplate.Context(p.blockPosition(), "minecraft:skeleton"));
        QuestTestAccess.addProgress(p, q, 3);
        p.getInventory().clearContent();
        CompoundTag tag = QuestEngine.data(p).serializeNBT(p.registryAccess());
        QuestData copy = new QuestData();
        copy.deserializeNBT(p.registryAccess(), tag);
        Quest c = copy.find(q.id());
        helper.assertTrue(c != null, "quest survives a save");
        helper.assertTrue(c.objectives().get(0).progress() == 3 && c.objectives().get(0).target().equals("minecraft:skeleton"), "objective kept");
        helper.assertTrue(c.title().getString().equals(q.title().getString()), "title kept");
        helper.assertTrue(c.rewards().xp() == q.rewards().xp() && c.rewards().items().size() == q.rewards().items().size(), "rewards kept");
        helper.assertTrue(c.area() != null && c.area().center().equals(q.area().center()), "area kept");
        helper.assertTrue(c.deadline() == q.deadline(), "clock kept");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void customObjectivesForOtherModules(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E);
        Quest q = QuestBuilder.of("othermod:talk", QuestCategory.NPC, p).title(Component.literal("Talk"))
                .custom("othermod:talk_to_elder", 2, Component.literal("Talk to the elder")).xp(10).build();
        QuestEngine.start(p, q);
        QuestEngine.progress(p, "othermod:something_else", 5);
        helper.assertTrue(q.objectives().get(0).progress() == 0, "other keys don't count");
        QuestEngine.progress(p, "othermod:talk_to_elder", 2);
        helper.assertTrue(QuestEngine.data(p).hasFinished("othermod:talk"), "custom quest completes");
        helper.succeed();
    }

    private QuestGameTests() {}
}
