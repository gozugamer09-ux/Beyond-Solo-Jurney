package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.association.Association;
import com.beyondsolo.chronicle.ChronicleModule;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.quest.situation.Run;
import com.beyondsolo.story.Kin;
import com.beyondsolo.story.KinEntity;
import com.beyondsolo.story.KinRecord;
import com.beyondsolo.story.LongSleep;
import com.beyondsolo.story.StoryConfig;
import com.beyondsolo.story.StoryModule;
import com.beyondsolo.story.StoryScenes;
import com.beyondsolo.story.StoryTestAccess;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.tower.CitadelLordEntity;
import com.beyondsolo.tower.TowerModule;
import com.beyondsolo.util.OnlinePlayers;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * The Long Sleep: the hunter's mother in a bed of their home village, the visit at level 5, the cure found at 25, its
 * three ingredients, the elixir brewed, her waking, her days after. Scenes are skipped ({@link StoryScenes#skip}): what
 * they change in the world still happens, only without the show. Every test that puts her in a bed makes its own bed
 * next to the plot's crystal, so it is the nearest one (another plot's beds are further off).
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LongSleepGameTests {
    private static final String ARENA = "evaluation_zone";
    private static final BlockPos CRYSTAL = new BlockPos(3, 2, 3);

    /** An evaluated hunter in the plot, at level 1, the plot's village their home; the story untold. */
    private static ServerPlayer hunter(GameTestHelper helper, String name) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        Economy.reset(p);
        ChronicleModule.data(p).reset();
        p.removeData(StoryModule.RECORD);
        p.removeAllEffects();
        p.getInventory().clearContent();
        p.setGameMode(GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 2));
        p.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        SystemAPI.awaken(p, PlayerRank.E, PlayerClass.WARRIOR);
        QuestTestAccess.markStarted(p);
        OnlinePlayers.addTestPlayer(helper, p);
        Kin.evaluatedAt(p, helper.absolutePos(CRYSTAL));
        return p;
    }

    /** A bed in the plot (its foot here, its head the way it faces); the head's absolute position. */
    private static BlockPos bed(GameTestHelper helper, BlockPos foot, Direction facing) {
        BlockState s = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, facing);
        helper.setBlock(foot, s.setValue(BedBlock.PART, BedPart.FOOT));
        helper.setBlock(foot.relative(facing), s.setValue(BedBlock.PART, BedPart.HEAD));
        return helper.absolutePos(foot.relative(facing));
    }

    /** Her own bed, beside the crystal: the head at (1, 2, 5), room to stand at (2, 2, 5). */
    private static BlockPos herBed(GameTestHelper helper) {
        return bed(helper, new BlockPos(1, 2, 4), Direction.SOUTH);
    }

    private static KinEntity mother(GameTestHelper helper, ServerPlayer p) {
        KinEntity k = Kin.bringHome(p);
        if (k == null) throw new IllegalStateException("she could not be put in her bed");
        return k;
    }

    private static int count(ServerPlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    private static boolean has(ServerPlayer p, String template) {
        return QuestEngine.data(p).has(template);
    }

    private static Quest active(ServerPlayer p, String template) {
        for (Quest q : QuestEngine.data(p).active()) if (q.template().equals(template)) return q;
        return null;
    }

    private static int run(ServerPlayer p, String command) throws CommandSyntaxException {
        return p.server.getCommands().getDispatcher().execute(command, p.createCommandSourceStack().withPermission(4).withSuppressedOutput());
    }

    // ================================================================ her bed

    /** Her bed is the village's nearest free one: not one slept in, not another hunter's mother's. */
    @GameTest(template = ARENA)
    public static void herBedIsTheNearestFreeOne(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos crystal = helper.absolutePos(CRYSTAL);
        BlockPos near = bed(helper, new BlockPos(3, 2, 4), Direction.WEST);
        BlockPos far = bed(helper, new BlockPos(5, 2, 4), Direction.SOUTH);
        helper.assertTrue(near.equals(Kin.findBed(level, crystal, 8)), "the nearest bed: " + Kin.findBed(level, crystal, 8));
        level.setBlock(near, level.getBlockState(near).setValue(BedBlock.OCCUPIED, true), 3);
        helper.assertTrue(far.equals(Kin.findBed(level, crystal, 8)), "not one somebody sleeps in");
        // another hunter's mother up and about beside the other one: it is hers all the same
        ServerPlayer other = hunter(helper, "OtherHunter");
        KinRecord r = Kin.record(other);
        KinEntity k = null;
        try {
            LongSleep.step(other, LongSleep.AWAKE);
            BlockPos keep = far;
            helper.assertTrue(level.getBlockState(keep).getBlock() instanceof BedBlock, "the far bed stands");
            k = Kin.bringHome(other);
            helper.assertTrue(k != null && keep.equals(k.bed()) && keep.equals(r.bed()), "the other mother takes the free bed");
            helper.assertTrue(k.state() == KinEntity.STANDING && !level.getBlockState(keep).getValue(BedBlock.OCCUPIED),
                    "she is up, her bed not slept in");
            helper.assertTrue(Kin.findBed(level, crystal, 8) == null, "no bed is free now: " + Kin.findBed(level, crystal, 8));
        } finally {
            if (k != null) k.discard();
        }
        helper.succeed();
    }

    /** No bed in the whole village: one is made for her near the crystal, with room to stand beside it. */
    @GameTest(template = ARENA)
    public static void aBedIsMadeWhenTheVillageHasNone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // the plot's roof off, so there is open ground under the sky
        for (int y = 5; y <= 6; y++) {
            for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
        }
        BlockPos crystal = helper.absolutePos(CRYSTAL);
        BlockPos made = Kin.makeBed(level, crystal);
        helper.assertTrue(made != null, "a bed is made");
        BlockState s = level.getBlockState(made);
        helper.assertTrue(s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD, "its head: " + s);
        helper.assertTrue(made.distSqr(crystal) <= 13 * 13, "near the crystal: " + made);
        helper.assertTrue(Math.abs(made.getY() - crystal.getY()) <= 4, "on the village's ground");
        helper.assertTrue(Kin.besideBed(level, made) != null, "with room to stand beside it");
        helper.succeed();
    }

    /** Near home she is put in her bed (asleep, her look chosen); gone, she is put back. She can't be hurt. */
    @GameTest(template = ARENA)
    public static void sheSleepsInHerBedNearHome(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer p = hunter(helper, "HomeComer");
        BlockPos head = herBed(helper);
        for (int i = 0; i < Kin.MISSES - 1; i++) StoryTestAccess.tick(p);
        helper.assertTrue(Kin.find(p) == null, "not before she has been missed a few times (her entity may still be loading)");
        StoryTestAccess.tick(p);
        KinRecord r = Kin.record(p);
        KinEntity k = Kin.find(p);
        helper.assertTrue(k != null, "she is put in her bed");
        try {
            helper.assertTrue(head.equals(r.bed()) && head.equals(k.bed()), "the bed beside the crystal: " + r.bed());
            helper.assertTrue(k.state() == KinEntity.SLEEPING && k.isSleeping(), "asleep");
            helper.assertTrue(level.getBlockState(head).getValue(BedBlock.OCCUPIED), "the bed is slept in");
            helper.assertTrue(r.look() >= 0 && r.look() < KinEntity.LOOKS && k.look() == r.look(), "her look is chosen: " + r.look());
            helper.assertTrue(k.isOwnedBy(p) && k.getUUID().equals(r.kin()), "she is this hunter's");
            float health = k.getHealth();
            k.hurt(level.damageSources().generic(), 50f);
            helper.assertTrue(k.isAlive() && k.getHealth() == health, "nothing hurts her");
            // gone (a command, a wipe): put back, the same look
            int look = r.look();
            k.discard();
            helper.assertFalse(level.getBlockState(head).getValue(BedBlock.OCCUPIED), "her bed is free when she is gone");
            for (int i = 0; i < Kin.MISSES; i++) StoryTestAccess.tick(p);
            k = Kin.find(p);
            helper.assertTrue(k != null && k.look() == look && head.equals(k.bed()), "she is back, as she was");
        } finally {
            if (k != null) k.discard();
        }
        helper.succeed();
    }

    // ================================================================ the story

    /** At level 5 the System sends the hunter to her; the visit at her bedside completes it. */
    @GameTest(template = ARENA)
    public static void theVisitComesAtLevelFive(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "FirstVisit");
        BlockPos head = herBed(helper);
        KinEntity k = mother(helper, p);
        try {
            SystemAPI.setLevel(p, StoryConfig.visitLevel() - 1);
            StoryTestAccess.tick(p);
            helper.assertTrue(Kin.record(p).stage() == LongSleep.UNTOLD && !has(p, LongSleep.VISIT_QUEST), "not yet");
            SystemAPI.setLevel(p, StoryConfig.visitLevel());
            StoryTestAccess.tick(p);
            Quest visit = active(p, LongSleep.VISIT_QUEST);
            helper.assertTrue(visit != null && Kin.record(p).stage() == LongSleep.VISIT, "the System sends them to see her");
            helper.assertTrue(visit.category() == QuestCategory.STORY, "a story quest");
            helper.assertTrue(LongSleep.HOME.equals(visit.extra().getString(Association.WAYPOINT_KIND))
                    && head.equals(Run.waypoint(visit, helper.getLevel().dimension())), "it points at her bed");
            helper.assertFalse(QuestEngine.abandon(p, visit.id()), "a story quest can't be given up");
            StoryTestAccess.talk(p, k);
            helper.assertTrue(LongSleep.VISIT_SCENE.equals(StoryScenes.playingId(p)), "the visit plays at her bedside");
            StoryScenes.skip(p);
            helper.assertTrue(Kin.record(p).stage() == LongSleep.VISITED && !has(p, LongSleep.VISIT_QUEST), "the visit is done");
            helper.assertTrue(k.state() == KinEntity.SLEEPING, "she sleeps on");
            StoryTestAccess.talk(p, k);
            helper.assertTrue(LongSleep.TALK_SCENE.equals(StoryScenes.playingId(p)), "after: a quiet thought by her bed");
            StoryScenes.skip(p);
        } finally {
            k.discard();
        }
        helper.succeed();
    }

    /** At level 25 (in the open, not mid-fight in the Citadel) the System finds the cure; a lost cure quest comes back. */
    @GameTest(template = ARENA)
    public static void theCureIsFoundAtLevelTwentyFive(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "CureFinder");
        LongSleep.step(p, LongSleep.VISITED);
        SystemAPI.setLevel(p, StoryConfig.cureLevel() - 1);
        StoryTestAccess.storyTick(p);
        helper.assertTrue(Kin.record(p).stage() == LongSleep.VISITED && !has(p, LongSleep.CURE_QUEST), "not yet");
        SystemAPI.setLevel(p, StoryConfig.cureLevel());
        StoryTestAccess.storyTick(p);
        helper.assertTrue(Kin.record(p).stage() == LongSleep.SEEKING, "the cure is found");
        helper.assertTrue(LongSleep.REVEAL_SCENE.equals(StoryScenes.playingId(p)), "the System tells it");
        StoryScenes.skip(p);
        Quest cure = active(p, LongSleep.CURE_QUEST);
        helper.assertTrue(cure != null && cure.objectives().size() == 3, "the cure's quest: souls, dew, the chalice");
        helper.assertTrue(cure.objectives().get(0).required() == StoryConfig.souls(), "the souls it needs");
        LongSleep.setSouls(p, 120);
        QuestEngine.drop(p, cure);
        helper.assertFalse(has(p, LongSleep.CURE_QUEST), "the quest is lost (a wiped log)");
        StoryTestAccess.storyTick(p);
        cure = active(p, LongSleep.CURE_QUEST);
        helper.assertTrue(cure != null && cure.objectives().get(0).progress() == 120, "given again, no soul lost");
        helper.succeed();
    }

    /** Souls in the Citadel, dew from a high gate's hidden cache, the Crimson King's chalice: with all three it is brewed. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void theElixirIsBrewedFromItsThreeIngredients(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer p = hunter(helper, "Brewer");
        Zombie dead = EntityType.ZOMBIE.create(level);
        BlockPos cache = helper.absolutePos(new BlockPos(4, 2, 4));
        PlayerRank high = PlayerRank.S;
        for (PlayerRank rank : PlayerRank.values()) {
            if (rank.tier() > StoryConfig.dewRank().tier() && rank.tier() < high.tier()) high = rank;
        }
        // nothing before the cure is sought
        LongSleep.soul(p, dead);
        LongSleep.lordFallen(p);
        helper.assertTrue(Kin.record(p).souls() == 0 && count(p, StoryModule.CRIMSON_CHALICE.get()) == 0
                && !LongSleep.dewFor(p, high), "nothing is gathered before the cure is sought");
        LongSleep.step(p, LongSleep.SEEKING);
        Quest cure = active(p, LongSleep.CURE_QUEST);
        helper.assertTrue(cure != null, "the cure is sought");
        // the souls
        LongSleep.soul(p, dead);
        helper.assertTrue(Kin.record(p).souls() == 1 && cure.objectives().get(0).progress() == 1, "a creature's soul: one");
        CitadelLordEntity lord = TowerModule.CITADEL_LORD.get().create(level);
        LongSleep.soul(p, lord);
        helper.assertTrue(Kin.record(p).souls() == 1 + StoryConfig.lordSouls(), "the Crimson King's: " + Kin.record(p).souls());
        LongSleep.setSouls(p, StoryConfig.souls() + 50);
        helper.assertTrue(cure.objectives().get(0).done() && Kin.record(p).souls() == StoryConfig.souls(), "all the souls, no more");
        // the dew: only from a cache above the dew's rank, for a hunter who has none yet
        helper.assertFalse(LongSleep.dewFor(p, StoryConfig.dewRank()), "not from a cache of the dew's own rank");
        helper.assertTrue(LongSleep.dewFor(p, high), "from a higher cache");
        List<ItemStack> dew = LongSleep.dew(level, cache, high);
        helper.assertTrue(!dew.isEmpty() && dew.get(0).is(StoryModule.ELDER_ROOT_DEW.get()), "the cache holds it: " + dew);
        p.getInventory().add(dew.get(0));
        helper.assertFalse(LongSleep.dewFor(p, high), "not twice");
        // the chalice
        LongSleep.lordFallen(p);
        LongSleep.lordFallen(p);
        helper.assertTrue(count(p, StoryModule.CRIMSON_CHALICE.get()) == 1, "the Crimson King's chalice, once");
        // all three: the System brews it
        QuestEngine.tick(p);
        helper.assertFalse(has(p, LongSleep.CURE_QUEST), "the cure's quest is done");
        helper.assertTrue(Kin.record(p).stage() == LongSleep.BREWED, "the elixir is brewed");
        helper.assertTrue(count(p, StoryModule.ELIXIR.get()) == 1, "the elixir in hand");
        helper.assertTrue(count(p, StoryModule.ELDER_ROOT_DEW.get()) == 0 && count(p, StoryModule.CRIMSON_CHALICE.get()) == 0,
                "the dew and the chalice went into it");
        helper.succeedWhen(() -> helper.assertTrue(has(p, LongSleep.WAKING_QUEST), "then: give it to her"));
    }

    /** The elixir wakes her: she sits up in bed that day, the title is earned; the next day she is up beside it. */
    @GameTest(template = ARENA)
    public static void theElixirWakesHer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer p = hunter(helper, "Waker");
        BlockPos head = herBed(helper);
        LongSleep.step(p, LongSleep.BREWED);
        helper.assertTrue(count(p, StoryModule.ELIXIR.get()) == 1 && has(p, LongSleep.WAKING_QUEST), "the elixir and the quest");
        KinEntity k = mother(helper, p);
        try {
            helper.assertTrue(k.state() == KinEntity.SLEEPING, "still asleep");
            int points = SystemAPI.get(p).statPoints();
            StoryTestAccess.talk(p, k);
            helper.assertTrue(LongSleep.WAKING_SCENE.equals(StoryScenes.playingId(p)), "the waking plays");
            StoryScenes.skip(p);
            long today = QuestEngine.day(p);
            KinRecord r = Kin.record(p);
            helper.assertTrue(count(p, StoryModule.ELIXIR.get()) == 0, "the elixir is given");
            helper.assertTrue(r.stage() == LongSleep.AWAKE && r.wokeDay() == today, "she is awake, today");
            helper.assertTrue(k.state() == KinEntity.SITTING && head.equals(k.bed()), "sitting up in bed");
            helper.assertTrue(!has(p, LongSleep.WAKING_QUEST) && ChronicleModule.data(p).hasTitle(LongSleep.TITLE), "the promise kept");
            helper.assertTrue(SystemAPI.get(p).statPoints() >= points + 3, "its stat points");
            StoryTestAccess.talk(p, k);
            helper.assertTrue(LongSleep.TALK_SCENE.equals(StoryScenes.playingId(p)), "the day she wakes she rests (no stew yet)");
            StoryScenes.skip(p);
            // the next day
            StoryTestAccess.setWokeDay(p, today - 1);
            StoryTestAccess.tick(p);
            helper.assertTrue(k.state() == KinEntity.STANDING && !k.isSleeping(), "up and about");
            BlockPos spot = Kin.besideBed(level, head);
            helper.assertTrue(spot != null && k.position().distanceTo(Vec3.atBottomCenterOf(spot)) < 0.6, "beside her bed: " + k.position());
            helper.assertFalse(level.getBlockState(head).getValue(BedBlock.OCCUPIED), "her bed made");
        } finally {
            k.discard();
        }
        helper.succeed();
    }

    /** Once she is up she cooks for them once a day; her stew is Home Comfort. */
    @GameTest(template = ARENA)
    public static void sheCooksOnceADay(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "Fed");
        herBed(helper);
        LongSleep.step(p, LongSleep.AWAKE);
        KinEntity k = mother(helper, p);
        try {
            helper.assertTrue(k.state() == KinEntity.STANDING, "up and about");
            StoryTestAccess.talk(p, k);
            helper.assertTrue(LongSleep.MEAL_SCENE.equals(StoryScenes.playingId(p)), "she has cooked");
            StoryScenes.skip(p);
            helper.assertTrue(count(p, StoryModule.HOME_STEW.get()) == 1 && Kin.record(p).mealDay() == QuestEngine.day(p), "her stew");
            StoryTestAccess.talk(p, k);
            helper.assertTrue(LongSleep.TALK_SCENE.equals(StoryScenes.playingId(p)), "then just a word");
            StoryScenes.skip(p);
            helper.assertTrue(count(p, StoryModule.HOME_STEW.get()) == 1, "one stew a day");
            ItemStack stew = ItemStack.EMPTY;
            for (ItemStack s : p.getInventory().items) if (s.is(StoryModule.HOME_STEW.get())) stew = s;
            stew.finishUsingItem(helper.getLevel(), p);
            helper.assertTrue(p.hasEffect(StoryModule.HOME_COMFORT), "Home Comfort");
            // the next day, another
            StoryTestAccess.setMealDay(p, QuestEngine.day(p) - 1);
            StoryTestAccess.talk(p, k);
            helper.assertTrue(LongSleep.MEAL_SCENE.equals(StoryScenes.playingId(p)), "she cooks again the next day");
            StoryScenes.skip(p);
        } finally {
            k.discard();
        }
        helper.succeed();
    }

    /** Somebody else's mother: a word on the action bar, nothing of their story. */
    @GameTest(template = ARENA)
    public static void anotherHuntersMotherIsNotYours(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "HerSon");
        ServerPlayer stranger = hunter(helper, "Stranger");
        herBed(helper);
        LongSleep.step(p, LongSleep.BREWED);
        KinEntity k = mother(helper, p);
        try {
            ItemStack elixir = new ItemStack(StoryModule.ELIXIR.get());
            stranger.getInventory().add(elixir);
            StoryTestAccess.talk(stranger, k);
            helper.assertTrue(!StoryScenes.playing(stranger) && k.state() == KinEntity.SLEEPING, "a stranger can't wake her");
            helper.assertTrue(Kin.record(p).stage() == LongSleep.BREWED, "her hunter's story is untouched");
        } finally {
            k.discard();
        }
        helper.succeed();
    }

    // ================================================================ /solo story

    @GameTest(template = ARENA)
    public static void theStoryCommandsWork(GameTestHelper helper) throws CommandSyntaxException {
        ServerPlayer p = hunter(helper, "StoryOp");
        p.removeData(StoryModule.RECORD);
        herBed(helper);
        helper.assertTrue(run(p, "solo story home") == 1 && helper.absolutePos(CRYSTAL).equals(Kin.record(p).anchor()), "home is the crystal");
        helper.assertTrue(run(p, "solo story mother") == 1, "mother puts her in her bed");
        KinEntity k = Kin.find(p);
        helper.assertTrue(k != null, "she is there");
        try {
            helper.assertTrue(run(p, "solo story step visit") == 1 && has(p, LongSleep.VISIT_QUEST), "step visit");
            helper.assertTrue(run(p, "solo story scene visit") == 1 && LongSleep.VISIT_SCENE.equals(StoryScenes.playingId(p)), "scene visit");
            StoryScenes.skip(p);
            helper.assertTrue(run(p, "solo story step cure") == 1 && has(p, LongSleep.CURE_QUEST), "step cure");
            helper.assertTrue(run(p, "solo story souls 300") == 1 && Kin.record(p).souls() == 300, "souls");
            helper.assertTrue(run(p, "solo story give chalice") == 1 && count(p, StoryModule.CRIMSON_CHALICE.get()) == 1, "give");
            helper.assertTrue(run(p, "solo story info") == 1, "info");
            helper.assertTrue(run(p, "solo story step awake") == 1 && Kin.find(p).state() == KinEntity.STANDING, "step awake");
            helper.assertTrue(!has(p, LongSleep.CURE_QUEST), "the story's old quests go");
            helper.assertTrue(run(p, "solo story reset") == 1, "reset");
            KinRecord r = Kin.record(p);
            helper.assertTrue(r.stage() == LongSleep.UNTOLD && r.souls() == 0 && Kin.find(p).state() == KinEntity.SLEEPING,
                    "from the start: she sleeps again");
            try {
                run(p, "solo story step nowhere");
                helper.fail("an unknown stage");
            } catch (CommandSyntaxException expected) {
                // not a stage
            }
        } finally {
            KinEntity m = Kin.find(p);
            if (m != null) m.discard();
        }
        helper.succeed();
    }
}
