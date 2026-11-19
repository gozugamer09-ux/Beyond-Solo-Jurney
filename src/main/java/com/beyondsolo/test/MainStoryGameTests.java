package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.chronicle.ChronicleModule;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.job.JobAPI;
import com.beyondsolo.job.Subclass;
import com.beyondsolo.monarch.Dominion;
import com.beyondsolo.monarch.MonarchAPI;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.ruler.Rulers;
import com.beyondsolo.story.FatherEntity;
import com.beyondsolo.story.Kin;
import com.beyondsolo.story.KinEntity;
import com.beyondsolo.story.LongSleep;
import com.beyondsolo.story.MainFights;
import com.beyondsolo.story.MainRecord;
import com.beyondsolo.story.MainScenes;
import com.beyondsolo.story.MainStory;
import com.beyondsolo.story.StoryModule;
import com.beyondsolo.story.StoryScenes;
import com.beyondsolo.story.StoryTestAccess;
import com.beyondsolo.story.VisionEntity;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.tower.TowerAPI;
import com.beyondsolo.util.OnlinePlayers;
import com.beyondsolo.worldboss.ColossalBoss;
import com.beyondsolo.worldboss.MonarchBoss;
import com.beyondsolo.worldboss.WorldBossData;
import com.beyondsolo.worldboss.WorldBossKind;
import com.beyondsolo.worldboss.WorldBossModule;
import com.beyondsolo.worldboss.WorldBosses;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The System's own story ({@link MainStory}): the prologue once the hunter is calm, chapters done later told in their
 * scenes and chapters done before closed quietly, the truth at the throne with its vision, the monarchs the story waits
 * for and their father fighting beside the hunter, the Last War and the light making war on its monarchs, and home.
 * Fake players don't tick, so each test runs the story's once-a-second check itself ({@link MainStory#tick}); scenes are
 * skipped ({@link StoryScenes#skip}): what they change still happens, only without the show. The tests that bring
 * monarchs into the world are in a batch of their own, since only one great threat is ever abroad.
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MainStoryGameTests {
    private static final String ARENA = "evaluation_zone";
    /** A great threat at a time: each fight test has a batch of its own. */
    private static final String FIGHTS = "main_story_fights", WAR = "main_story_war";
    private static final BlockPos CRYSTAL = new BlockPos(3, 2, 3);

    /** An evaluated hunter in the plot at level 1, their quest log started, never hurt; the story untold. */
    private static ServerPlayer hunter(GameTestHelper helper, String name) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        Economy.reset(p);
        JobAPI.reset(p);
        ChronicleModule.data(p).reset();
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

    private static Quest active(ServerPlayer p, String template) {
        for (Quest q : QuestEngine.data(p).active()) if (q.template().equals(template)) return q;
        return null;
    }

    private static int run(ServerPlayer p, String command) throws CommandSyntaxException {
        return p.server.getCommands().getDispatcher().execute(command, p.createCommandSourceStack().withPermission(4).withSuppressedOutput());
    }

    /** A monarch of the event, held still in the plot (it still ticks and lands at once), its fight the event's. */
    private static MonarchBoss monarch(GameTestHelper helper, WorldBossData.Event ev) {
        ServerLevel level = helper.getLevel();
        ColossalBoss boss = WorldBossModule.type(ev.kind).create(level);
        if (!(boss instanceof MonarchBoss m)) throw new IllegalStateException(ev.kind + " is no monarch");
        BlockPos at = helper.absolutePos(new BlockPos(5, 2, 5));
        m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        m.setupStats();
        m.setNoAi(true);
        m.setEventId(ev.id);
        level.addFreshEntity(m);
        m.wakeNow();
        ev.setEntity(m.getUUID());
        ev.setLandAt(0);
        return m;
    }

    /** The monarch falls by the hunter's hand. */
    private static void fell(GameTestHelper helper, ServerPlayer p, MonarchBoss m) {
        ServerLevel level = helper.getLevel();
        m.hurt(level.damageSources().playerAttack(p), 40f);
        m.forcePhase(level, 3);
        m.invulnerableTime = 0;
        m.hurt(level.damageSources().generic(), m.getMaxHealth() * 3);
    }

    // ================================================================ the beginning

    /** The prologue waits until the hunter's quest log has started and they have been calm a while; then Chapter I. */
    @GameTest(template = ARENA)
    public static void thePrologueWaitsForACalmMoment(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "PrologueHearer");
        MainRecord r = MainStory.record(p);
        for (int i = 0; i < MainStory.PROLOGUE_CALM - 1; i++) MainStory.tick(p);
        helper.assertFalse(StoryScenes.playing(p), "not before they have been calm a while");
        p.hurtTime = 8;
        MainStory.tick(p);
        p.hurtTime = 0;
        for (int i = 0; i < MainStory.PROLOGUE_CALM - 1; i++) MainStory.tick(p);
        helper.assertFalse(StoryScenes.playing(p), "being hurt starts the wait over");
        MainStory.tick(p);
        helper.assertTrue(MainScenes.PROLOGUE_SCENE.equals(StoryScenes.playingId(p)), "then the prologue plays: " + StoryScenes.playingId(p));
        helper.assertTrue(r.chapter() == MainStory.UNTOLD, "the story is still untold while it plays");
        StoryScenes.skip(p);
        Quest q = active(p, MainStory.PLAYER_QUEST);
        helper.assertTrue(r.chapter() == MainStory.PLAYER && q != null, "skipped, Chapter I begins with its quest");
        helper.assertTrue(q.category() == QuestCategory.STORY && !QuestEngine.abandon(p, q.id()), "a story quest, not to be given up");
        helper.assertTrue(q.objectives().size() == 2, "a level and gates: " + q.objectives().size());

        // a hunter whose quest log hasn't started yet hears nothing
        ServerPlayer fresh = hunter(helper, "PrologueTooSoon");
        QuestEngine.reset(fresh);
        for (int i = 0; i < MainStory.PROLOGUE_CALM + 5; i++) MainStory.tick(fresh);
        helper.assertFalse(StoryScenes.playing(fresh), "no prologue before the System's first quests");

        // back from a long session: the game's saved "last hurt" time is ahead of their new tick count, and means nothing
        ServerPlayer back = hunter(helper, "PrologueAfterRelog");
        back.tickCount = 90_000;
        back.setLastHurtByMob(null);
        back.tickCount = 200;
        for (int i = 0; i < MainStory.PROLOGUE_CALM; i++) MainStory.tick(back);
        helper.assertTrue(MainScenes.PROLOGUE_SCENE.equals(StoryScenes.playingId(back)), "a hunter back from a long session hears it too");
        StoryScenes.skip(back);
        helper.succeed();
    }

    /** A chapter finished in the story's own time owes its scene, told once the hunter is calm; then the next chapter. */
    @GameTest(template = ARENA)
    public static void aChapterDoneLaterIsToldInItsScene(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "GrowingPlayer");
        MainRecord r = MainStory.record(p);
        MainStory.step(p, MainStory.PLAYER);
        StoryTestAccess.givenLongAgo(p);
        SystemAPI.setLevel(p, MainStory.PLAYER_LEVEL);
        for (int i = 0; i < MainStory.PLAYER_GATES; i++) Economy.countClear(p, PlayerRank.E);
        MainStory.tick(p);
        QuestEngine.tick(p);
        helper.assertTrue(active(p, MainStory.PLAYER_QUEST) == null, "Chapter I is done");
        helper.assertTrue(r.chapter() == MainStory.PLAYER && r.owed() == MainStory.PLAYER, "it owes its scene");
        MainStory.tick(p);
        helper.assertFalse(StoryScenes.playing(p), "not at once: the dust settles first");
        StoryTestAccess.owedNow(p);
        MainStory.tick(p);
        helper.assertTrue(MainScenes.STATUE_SCENE.equals(StoryScenes.playingId(p)), "the statue smiles at them: " + StoryScenes.playingId(p));
        StoryScenes.skip(p);
        helper.assertTrue(r.chapter() == MainStory.ARISE && r.owed() == 0 && active(p, MainStory.ARISE_QUEST) != null, "on to Chapter II");
        helper.succeed();
    }

    /** A hunter far along already: the chapters they finished before the story reached them close at once, untold. */
    @GameTest(template = ARENA)
    public static void chaptersDoneBeforeCloseQuietly(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "FarAlong");
        MainRecord r = MainStory.record(p);
        JobAPI.changeJob(p, Subclass.BLADEMASTER);
        TowerAPI.data(p).lordKills = 1;
        MainStory.step(p, MainStory.ARISE);
        MainStory.tick(p);
        helper.assertTrue(r.chapter() == MainStory.CITADEL && r.owed() == 0, "the job was changed long ago: on at once");
        MainStory.tick(p);
        helper.assertTrue(r.chapter() == MainStory.THRONE && r.owed() == 0, "the Crimson King fell long ago: on at once");
        MainStory.tick(p);
        helper.assertFalse(StoryScenes.playing(p), "no scene for chapters lived before");
        helper.assertTrue(active(p, MainStory.THRONE_QUEST) != null, "the throne waits");
        helper.succeed();
    }

    /** The empty throne answered: the King of Night's vision tells the truth, and the hunter is his heir. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void theThroneTellsTheTruth(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "ThroneHeir");
        MainRecord r = MainStory.record(p);
        MainStory.step(p, MainStory.THRONE);
        MonarchAPI.crown(p, Dominion.NIGHT);
        MainStory.tick(p);
        helper.assertTrue(r.owed() == MainStory.THRONE, "crowned: the throne owes its truth (never told quietly)");
        StoryTestAccess.owedNow(p);
        MainStory.tick(p);
        helper.assertTrue(MainScenes.TRUTH_SCENE.equals(StoryScenes.playingId(p)), "the truth is told: " + StoryScenes.playingId(p));
        helper.startSequence()
                .thenExecuteAfter(70, () -> {
                    Entity v = MainScenes.visionOf(p);
                    helper.assertTrue(v instanceof VisionEntity, "the King of Night stands before them");
                    helper.assertTrue(v.distanceTo(p) < 5.5, "a few steps away: " + v.distanceTo(p));
                    helper.assertFalse(v.hurt(helper.getLevel().damageSources().playerAttack(p), 10f), "a vision can't be struck");
                })
                .thenExecute(() -> {
                    Entity v = MainScenes.visionOf(p);
                    StoryScenes.skip(p);
                    helper.assertTrue(MainScenes.visionOf(p) == null && (v == null || v.isRemoved()), "the vision is gone with the scene");
                    helper.assertTrue(r.chapter() == MainStory.FATHER && active(p, MainStory.FATHER_QUEST) != null, "Chapter V begins");
                    helper.assertTrue(r.nextFight() > QuestEngine.gameTime(p), "and its monarch comes after a breath");
                    helper.assertTrue(ChronicleModule.data(p).hasTitle(MainStory.HEIR), "Heir to the Empty Throne is earned");
                })
                .thenSucceed();
    }

    // ================================================================ the fights

    /**
     * The Vessel of Light: Khorvane abroad near the hunter is the chapter's monarch; once it has landed their father
     * comes down beside them and stays; its fall counts, and he stays for the talk after, then goes back into the sky.
     */
    @GameTest(template = ARENA, batch = FIGHTS, timeoutTicks = 420)
    public static void theirFatherFightsTheFirstMonarchBesideThem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WorldBosses.stop(level);
        ServerPlayer p = hunter(helper, "VesselChild");
        p.setGameMode(GameType.CREATIVE);
        MainRecord r = MainStory.record(p);
        MainStory.step(p, MainStory.FATHER);
        helper.assertTrue("none".equals(MainFights.callNow(hunter(helper, "NoMonarchYet"))), "a hunter early in the story waits for no monarch");
        AtomicReference<MonarchBoss> boss = new AtomicReference<>();
        // another great threat abroad: the story waits for it to go
        WorldBossData.Event other = WorldBosses.begin(level, WorldBossKind.STORMS, helper.absolutePos(new BlockPos(5, 2, 5)), true);
        helper.assertTrue(other != null, "a great threat can be called");
        helper.assertTrue("busy".equals(MainFights.callNow(p)), "not while another is abroad");
        WorldBosses.stop(level);
        WorldBossData.Event ev = WorldBosses.begin(level, WorldBossKind.HUNGER, helper.absolutePos(new BlockPos(5, 2, 5)), true);
        helper.assertTrue(ev != null, "Khorvane is called");
        MainStory.tick(p);
        helper.assertTrue(ev.id.equals(r.fight()), "the monarch the chapter waits for, abroad near them, is their fight");
        helper.assertTrue(MainFights.father(p) == null, "their father waits for it to land");
        boss.set(monarch(helper, ev));
        helper.startSequence()
                .thenExecuteAfter(3, () -> {
                    helper.assertTrue(boss.get().landed(), "it has landed");
                    helper.assertTrue(MainFights.monarchLanded(level, boss.get()), "the story answers its own monarch");
                    helper.assertTrue(WorldBossData.get(level.getServer()).ruler() == null, "no ruler comes down to it: the light answers through him");
                    MainStory.tick(p);
                    helper.assertTrue(MainFights.father(p) == null, "their father lets its landing have its moment");
                })
                .thenExecuteAfter(MainFights.ARRIVE_AFTER + 2, () -> {
                    MainStory.tick(p);
                    FatherEntity f = MainFights.father(p);
                    helper.assertTrue(f != null && f.isOwnedBy(p), "their father comes down beside them");
                    helper.assertTrue(r.fatherMet() && MainScenes.ARRIVES_SCENE.equals(StoryScenes.playingId(p)), "the first time, with a word or two");
                    helper.assertTrue(f.state() == FatherEntity.FIGHTING && f.getMaxHealth() > 20, "a fighter, as strong as his child's level makes him");
                    float health = f.getHealth();
                    f.hurt(level.damageSources().generic(), f.getMaxHealth() * 5);
                    helper.assertTrue(f.isAlive() && f.getHealth() >= f.getMaxHealth() * 0.29f, "the light holds him up: " + f.getHealth() + " of " + health);
                    StoryScenes.skip(p);
                })
                .thenExecuteAfter(25, () -> {
                    helper.assertTrue(MainFights.father(p) != null, "he stays while the monarch is near");
                    fell(helper, p, boss.get());
                    helper.assertTrue(boss.get().isDeadOrDying(), "Khorvane falls");
                    helper.assertTrue(r.warWon(2) && r.owed() == MainStory.FATHER, "it counts, and the chapter owes its talk");
                    helper.assertTrue(active(p, MainStory.FATHER_QUEST) == null, "Chapter V is done");
                    StoryTestAccess.owedNow(p);
                    MainStory.tick(p);
                    helper.assertTrue(MainScenes.FATHER_SCENE.equals(StoryScenes.playingId(p)), "the talk: " + StoryScenes.playingId(p));
                })
                .thenExecuteAfter(25, () -> {
                    helper.assertTrue(MainFights.father(p) != null, "he stays for the talk");
                    StoryScenes.skip(p);
                    helper.assertTrue(MainFights.father(p) == null, "then he goes back into the sky");
                    helper.assertTrue(r.chapter() == MainStory.LAST_WAR && active(p, MainStory.WAR_QUEST) != null, "the Last War is next");
                    helper.assertTrue(r.fight() == null && r.nextFight() > QuestEngine.gameTime(p), "its first monarch comes after a while");
                })
                .thenExecute(() -> {
                    if (boss.get() != null && !boss.get().isRemoved()) boss.get().discard();
                    WorldBosses.stop(level);
                })
                .thenSucceed();
    }

    /**
     * The Last War: Veyrith comes, the light makes war on her, their father fights beside the hunter and stays between
     * the monarchs; Thyrax follows; when both have fallen their father gives the last of his light, and home is next.
     */
    @GameTest(template = ARENA, batch = WAR, timeoutTicks = 560)
    public static void theLastWarEndsInLight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WorldBosses.stop(level);
        Rulers.stop(level);
        ServerPlayer p = hunter(helper, "LastWarChild");
        p.setGameMode(GameType.CREATIVE);
        MainRecord r = MainStory.record(p);
        MainStory.step(p, MainStory.LAST_WAR);
        AtomicReference<MonarchBoss> boss = new AtomicReference<>();
        WorldBossData.Event ev = WorldBosses.begin(level, WorldBossKind.STILLNESS, helper.absolutePos(new BlockPos(5, 2, 5)), true);
        helper.assertTrue(ev != null, "Veyrith is called");
        MainStory.tick(p);
        helper.assertTrue(ev.id.equals(r.fight()), "she is their fight");
        boss.set(monarch(helper, ev));
        helper.startSequence()
                .thenExecuteAfter(3, () -> {
                    MainFights.monarchLanded(level, boss.get());
                    WorldBossData.Event war = WorldBossData.get(level.getServer()).ruler();
                    helper.assertTrue(war != null && war.reason() == Rulers.WAR && boss.get().getUUID().equals(war.quarry()),
                            "the light comes down to make war on her");
                    MainStory.tick(p);
                    helper.assertTrue(MainFights.father(p) == null, "their father lets her landing have its moment");
                })
                .thenExecuteAfter(MainFights.ARRIVE_AFTER + 2, () -> {
                    MainStory.tick(p);
                    helper.assertTrue(MainFights.father(p) == null, "nor the ruler's");
                })
                .thenExecuteAfter(MainFights.RULER_BANNER, () -> {
                    MainStory.tick(p);
                    helper.assertTrue(MainFights.father(p) != null, "then he comes down beside them");
                    helper.assertTrue(MainScenes.ARRIVES_SCENE.equals(StoryScenes.playingId(p)), "a promise kept");
                    StoryScenes.skip(p);
                    fell(helper, p, boss.get());
                    helper.assertTrue(r.warWon(0) && !r.warWon(1) && r.owed() == 0, "Veyrith's fall counts; the war goes on");
                    helper.assertTrue(MainScenes.NEXT_SCENE.equals(StoryScenes.playingId(p)), "Thyrax speaks: " + StoryScenes.playingId(p));
                    helper.assertTrue(r.nextFight() > QuestEngine.gameTime(p), "and comes after a little while");
                    StoryScenes.skip(p);
                    boss.get().discard();
                    Rulers.stop(level);
                })
                .thenExecuteAfter(25, () -> {
                    MainStory.tick(p);
                    helper.assertTrue(MainFights.father(p) != null, "between the monarchs he stays");
                    WorldBossData.Event storms = WorldBosses.begin(level, WorldBossKind.STORMS, helper.absolutePos(new BlockPos(5, 2, 5)), true);
                    helper.assertTrue(storms != null, "Thyrax is called");
                    MainStory.tick(p);
                    helper.assertTrue(storms.id.equals(r.fight()), "he is their fight");
                    boss.set(monarch(helper, storms));
                })
                .thenExecuteAfter(3, () -> {
                    fell(helper, p, boss.get());
                    helper.assertTrue(r.warWon(0) && r.warWon(1), "both monarchs have fallen");
                    helper.assertTrue(r.owed() == MainStory.LAST_WAR && active(p, MainStory.WAR_QUEST) == null, "the Last War is won");
                    helper.assertTrue(MainFights.father(p) != null, "he stays for the end");
                    StoryTestAccess.owedNow(p);
                    MainStory.tick(p);
                    helper.assertTrue(MainScenes.LAST_LIGHT_SCENE.equals(StoryScenes.playingId(p)), "his last light: " + StoryScenes.playingId(p));
                    StoryScenes.skip(p);
                    helper.assertTrue(MainFights.father(p) == null && r.fatherGone(), "he is gone");
                    helper.assertTrue(r.chapter() == MainStory.HOME && active(p, MainStory.HOME_QUEST) != null, "home is next");
                })
                .thenExecute(() -> {
                    if (boss.get() != null && !boss.get().isRemoved()) boss.get().discard();
                    WorldBosses.stop(level);
                    Rulers.stop(level);
                })
                .thenSucceed();
    }

    // ================================================================ home

    /** Home: by their mother the story's last scene plays; then the System's last words, and the story is over. */
    @GameTest(template = ARENA)
    public static void homeEndsTheStory(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "HomeAtLast");
        MainRecord r = MainStory.record(p);
        BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
        helper.setBlock(new BlockPos(1, 2, 4), bed.setValue(BedBlock.PART, BedPart.FOOT));
        helper.setBlock(new BlockPos(1, 2, 5), bed.setValue(BedBlock.PART, BedPart.HEAD));
        KinEntity k = Kin.bringHome(p);
        helper.assertTrue(k != null, "their mother is in her bed");
        try {
            MainStory.step(p, MainStory.HOME);
            Quest q = active(p, MainStory.HOME_QUEST);
            helper.assertTrue(q != null && LongSleep.HOME.equals(q.extra().getString(com.beyondsolo.association.Association.WAYPOINT_KIND)),
                    "the epilogue points them home");
            StoryTestAccess.talk(p, k);
            helper.assertTrue(MainScenes.HOME_SCENE.equals(StoryScenes.playingId(p)), "at her side, the last scene plays: " + StoryScenes.playingId(p));
            StoryScenes.skip(p);
            helper.assertTrue(active(p, MainStory.HOME_QUEST) == null && r.owed() == MainStory.HOME, "home");
            StoryTestAccess.owedNow(p);
            MainStory.tick(p);
            helper.assertTrue(MainScenes.END_SCENE.equals(StoryScenes.playingId(p)), "the System's last words: " + StoryScenes.playingId(p));
            StoryScenes.skip(p);
            helper.assertTrue(r.chapter() == MainStory.DONE && r.endedDay() == QuestEngine.day(p), "the story is over");
            helper.assertTrue(ChronicleModule.data(p).hasTitle(MainStory.BEYOND), "Beyond the System is earned");
            StoryTestAccess.talk(p, k);
            helper.assertFalse(MainScenes.HOME_SCENE.equals(StoryScenes.playingId(p)), "after, she is only their mother again");
            StoryScenes.skip(p);
        } finally {
            k.discard();
        }
        helper.succeed();
    }

    // ================================================================ keeping the story

    /** A chapter's quest that goes missing is given again; the Long Sleep moving on leaves the System's story be. */
    @GameTest(template = ARENA)
    public static void theStoryKeepsItsQuests(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "StoryKeeper");
        MainStory.step(p, MainStory.CITADEL);
        Quest q = active(p, MainStory.CITADEL_QUEST);
        helper.assertTrue(q != null, "Chapter III's quest");
        QuestEngine.drop(p, q);
        MainStory.tick(p);
        helper.assertTrue(active(p, MainStory.CITADEL_QUEST) != null, "lost, it is given again");
        LongSleep.step(p, LongSleep.SEEKING);
        helper.assertTrue(active(p, MainStory.CITADEL_QUEST) != null && active(p, LongSleep.CURE_QUEST) != null,
                "the Long Sleep's quests and the System's story's side by side");
        helper.succeed();
    }

    /** Operators move the System's story, play its scenes, call their father and start it over. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void operatorsMoveTheStory(GameTestHelper helper) throws CommandSyntaxException {
        ServerPlayer p = hunter(helper, "StoryOperator");
        MainRecord r = MainStory.record(p);
        run(p, "solo story main chapter father");
        helper.assertTrue(r.chapter() == MainStory.FATHER && active(p, MainStory.FATHER_QUEST) != null, "moved to Chapter V");
        helper.assertTrue(run(p, "solo story main info") > 0, "where it is");
        run(p, "solo story main scene arise");
        helper.assertTrue(MainScenes.ARISE_SCENE.equals(StoryScenes.playingId(p)), "a scene plays");
        StoryScenes.skip(p);
        helper.assertTrue(r.chapter() == MainStory.FATHER, "a scene played out of turn changes nothing");
        run(p, "solo story main father");
        helper.assertTrue(MainFights.father(p) != null, "their father is called down");
        helper.startSequence()
                .thenExecuteAfter(25, () -> {
                    helper.assertTrue(MainFights.father(p) != null, "called, he stays though no monarch is near");
                    try {
                        run(p, "solo story main father");
                        helper.assertTrue(MainFights.father(p) == null, "again, and he goes");
                        run(p, "solo story main chapter player");
                        boolean refused = false;
                        try {
                            run(p, "solo story main fight");
                        } catch (CommandSyntaxException e) {
                            refused = true;
                        }
                        helper.assertTrue(refused, "Chapter I waits for no monarch");
                        run(p, "solo story main reset");
                        helper.assertTrue(r.chapter() == MainStory.UNTOLD && active(p, MainStory.PLAYER_QUEST) == null, "the story starts over");
                    } catch (CommandSyntaxException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .thenSucceed();
    }

    private MainStoryGameTests() {}
}
