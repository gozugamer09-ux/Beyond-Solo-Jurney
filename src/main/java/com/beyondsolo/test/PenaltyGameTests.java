package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.penalty.PenaltyArena;
import com.beyondsolo.penalty.PenaltyZone;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestEvent;
import com.beyondsolo.quest.QuestTemplate;
import com.beyondsolo.quest.QuestTemplates;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** Penalty Zone tests. Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PenaltyGameTests {
    private static final String ARENA = "evaluation_zone";

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "convict"));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        p.setGameMode(GameType.SURVIVAL);
        p.removeAllEffects();
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        QuestTestAccess.markStarted(p);
        SystemAPI.awaken(p, PlayerRank.C, PlayerClass.WARRIOR);
        return p;
    }

    /** Fake players load no chunks, so the Wastes are held loaded for the test (creatures would be out of reach otherwise). */
    private static void hold(ServerPlayer p, boolean on) {
        BlockPos c = PenaltyZone.center(p);
        if (c == null) return;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) p.serverLevel().setChunkForced((c.getX() >> 4) + dx, (c.getZ() >> 4) + dz, on);
        }
    }

    private static void clearSentence(ServerPlayer p) {
        hold(p, false);
        clearSentence0(p);
    }

    private static void clearSentence0(ServerPlayer p) {
        PenaltyZone.cancel(p);
        PenaltyZone.data(p).deserializeNBT(p.server.registryAccess(), new net.minecraft.nbt.CompoundTag());
    }

    /** Failing the daily owes a sentence instead of the fatigue and debuffs. */
    @GameTest(template = ARENA)
    public static void failedDailySentencesInsteadOfDebuffs(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        clearSentence(p);
        Quest daily = QuestEngine.start(p, QuestTemplates.DAILY, QuestTemplate.Context.NONE);
        helper.assertTrue(daily != null, "daily started");
        helper.assertTrue(daily.penalty().zone(), "the daily's penalty is the zone");
        float before = SystemAPI.get(p).exhaustion();
        QuestEngine.fail(p, daily, QuestEvent.FailReason.DAY_ENDED);
        helper.assertTrue(PenaltyZone.data(p).pending(), "a sentence is owed");
        helper.assertTrue(PenaltyZone.data(p).reason().getString().equals(daily.title().getString()), "the reason is the quest");
        helper.assertTrue(!p.hasEffect(MobEffects.WEAKNESS) && !p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "no debuffs instead");
        helper.assertTrue(SystemAPI.get(p).exhaustion() == before, "no fatigue instead");
        helper.assertTrue(PenaltyZone.tryStart(p), "the warning starts once free");
        helper.assertTrue(PenaltyZone.phase(p) == PenaltyZone.Phase.WARNING, "warned first");
        helper.assertTrue(PenaltyZone.busy(p) && !PenaltyZone.inZone(p), "busy but still at home");
        clearSentence(p);
        helper.succeed();
    }

    /** Giving up the daily is no way out either. */
    @GameTest(template = ARENA)
    public static void abandonedDailyAlsoSentences(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        clearSentence(p);
        Quest daily = QuestEngine.start(p, QuestTemplates.DAILY, QuestTemplate.Context.NONE);
        helper.assertTrue(QuestEngine.abandon(p, daily.id()), "the daily can be abandoned");
        helper.assertTrue(PenaltyZone.data(p).pending(), "a sentence is owed");
        clearSentence(p);
        helper.succeed();
    }

    /** A player who can't be taken right now (here: a spectator) keeps the sentence until they can. */
    @GameTest(template = ARENA)
    public static void sentenceWaitsUntilFree(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        clearSentence(p);
        helper.assertTrue(PenaltyZone.sentence(p, Component.literal("test")), "sentenced");
        p.setGameMode(GameType.SPECTATOR);
        helper.assertTrue(!PenaltyZone.tryStart(p), "not while occupied");
        helper.assertTrue(PenaltyZone.data(p).pending(), "still owed");
        p.setGameMode(GameType.SURVIVAL);
        helper.assertTrue(PenaltyZone.tryStart(p), "starts once free");
        clearSentence(p);
        helper.succeed();
    }

    /** No System, no Penalty Zone: the caller falls back to the ordinary penalty. */
    @GameTest(template = ARENA)
    public static void noSystemNoZone(GameTestHelper helper) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "civilian"));
        SystemAPI.reset(p);
        helper.assertTrue(!PenaltyZone.sentence(p, Component.literal("test")), "refused without the System");
        helper.succeed();
    }

    /** The whole sentence, sped up: pulled in, the Wastes are laid out, creatures rise, time runs out, home again. */
    @GameTest(template = ARENA, timeoutTicks = 900)
    public static void survivingSendsYouHome(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        clearSentence(p);
        Vec3 home = p.position();
        helper.assertTrue(PenaltyZone.sentence(p, Component.literal("Daily Conditioning")), "sentenced");
        helper.assertTrue(PenaltyZone.begin(p, true), "straight in");
        hold(p, true);
        helper.assertTrue(!PenaltyZone.begin(p, true), "only one at a time");
        helper.assertTrue(PenaltyZone.phase(p) == PenaltyZone.Phase.ARRIVAL, "arriving");
        BlockPos c = PenaltyZone.center(p);
        helper.assertTrue(c != null && p.position().distanceTo(home) > 1000, "taken to the Wastes");
        helper.assertTrue(PenaltyZone.data(p).inZone(), "marked as inside");
        helper.assertTrue(p.level().getBlockState(c.above(3)).is(Blocks.CRYING_OBSIDIAN), "the pillar stands in the middle");
        helper.assertTrue(p.level().getBlockState(c.offset(0, -1, 9)).is(Blocks.RED_SAND)
                || p.level().getBlockState(c.offset(0, -1, 9)).is(Blocks.RED_TERRACOTTA)
                || p.level().getBlockState(c.offset(0, -1, 9)).is(Blocks.SMOOTH_RED_SANDSTONE), "red ground underfoot");
        helper.assertTrue(p.position().distanceTo(Vec3.atBottomCenterOf(c)) < PenaltyArena.RADIUS, "inside the edge");

        int[] stage = {0};
        int[] waited = {0};
        helper.onEachTick(() -> {
            PenaltyZone.Phase phase = PenaltyZone.phase(p);
            switch (stage[0]) {
                case 0 -> {
                    PenaltyZone.jump(p, "fight", 0);
                    stage[0] = 1;
                }
                case 1 -> {
                    if (phase == PenaltyZone.Phase.SURVIVE && ++waited[0] > 80) {
                        List<LivingEntity> foes = PenaltyZone.foes(p);
                        helper.assertTrue(!foes.isEmpty(), "creatures rose from the sand");
                        helper.assertTrue(foes.stream().allMatch(e -> e.getTags().contains(PenaltyZone.FOE_TAG)), "they belong to the zone");
                        helper.assertTrue(foes.stream().allMatch(e -> e.position().distanceTo(Vec3.atBottomCenterOf(c)) < PenaltyArena.RADIUS + 4),
                                "they rose inside the edge");
                        foes.get(0).kill();
                        PenaltyZone.jump(p, "time", 1);
                        stage[0] = 2;
                    }
                }
                case 2 -> {
                    if (phase == PenaltyZone.Phase.SURVIVED) stage[0] = 3;
                }
                case 3 -> {
                    if (phase == null) {
                        helper.assertTrue(p.position().distanceTo(home) < 2, "back where they were taken from");
                        helper.assertTrue(!PenaltyZone.data(p).inZone(), "no longer inside");
                        helper.assertTrue(!PenaltyZone.data(p).pending(), "the sentence is served");
                        helper.assertTrue(PenaltyZone.data(p).served() == 1, "counted as served");
                        helper.assertTrue(PenaltyZone.foes(p).isEmpty(), "no creatures left behind");
                        helper.assertTrue(!SystemAPI.get(p).isCollapsed(), "not collapsed");
                        helper.succeed();
                    }
                }
                default -> {}
            }
        });
    }

    /** A blow that would kill ends it instead: home, alive, collapsed and weak. */
    @GameTest(template = ARENA, timeoutTicks = 500)
    public static void fallingSendsYouHomeCollapsed(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        clearSentence(p);
        Vec3 home = p.position();
        PenaltyZone.sentence(p, Component.literal("Daily Conditioning"));
        PenaltyZone.begin(p, true);
        hold(p, true);
        PenaltyZone.jump(p, "fight", 0);
        int[] stage = {0};
        helper.onEachTick(() -> {
            PenaltyZone.Phase phase = PenaltyZone.phase(p);
            switch (stage[0]) {
                case 0 -> {
                    if (phase == PenaltyZone.Phase.SURVIVE) {
                        // fake players can't be hurt, so post the death the way a fatal hit would
                        LivingDeathEvent death = NeoForge.EVENT_BUS.post(new LivingDeathEvent(p, p.damageSources().genericKill()));
                        helper.assertTrue(death.isCanceled(), "the death is cancelled");
                        helper.assertTrue(p.isAlive(), "alive");
                        helper.assertTrue(PenaltyZone.phase(p) == PenaltyZone.Phase.FAILED, "the sentence fails");
                        stage[0] = 1;
                    }
                }
                case 1 -> {
                    if (phase == null) {
                        helper.assertTrue(p.position().distanceTo(home) < 2, "thrown home");
                        helper.assertTrue(SystemAPI.get(p).isCollapsed(), "collapsed");
                        helper.assertTrue(p.hasEffect(MobEffects.WEAKNESS), "weakened");
                        helper.assertTrue(!PenaltyZone.data(p).pending(), "the sentence is over");
                        helper.assertTrue(PenaltyZone.data(p).failed() == 1, "counted as failed");
                        helper.succeed();
                    }
                }
                default -> {}
            }
        });
    }

    /** Sand eruptions hit what stands in them, creatures harder than the player. */
    @GameTest(template = ARENA, timeoutTicks = 500)
    public static void eruptionsAndSurges(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        clearSentence(p);
        PenaltyZone.sentence(p, Component.literal("Daily Conditioning"));
        PenaltyZone.begin(p, true);
        hold(p, true);
        PenaltyZone.jump(p, "fight", 0);
        int[] stage = {0};
        int[] wait = {0};
        helper.onEachTick(() -> {
            if (PenaltyZone.phase(p) != PenaltyZone.Phase.SURVIVE) return;
            switch (stage[0]) {
                case 0 -> {
                    helper.assertTrue(PenaltyZone.jump(p, "surge", 5), "a surge can be called");
                    helper.assertTrue(PenaltyZone.jump(p, "erupt", 0), "eruptions can be called");
                    stage[0] = 1;
                }
                case 1 -> {
                    if (++wait[0] > 40) {
                        helper.assertTrue(PenaltyZone.foes(p).size() >= 5, "the surge brought a ring of creatures: " + PenaltyZone.foes(p).size());
                        clearSentence(p);
                        helper.succeed();
                    }
                }
                default -> {}
            }
        });
    }

    /** The daily is the training: four fixed drills, counted by what the player does, and finishing restores them. */
    @GameTest(template = ARENA)
    public static void dailyTrainingDrills(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        clearSentence(p);
        Quest daily = QuestEngine.start(p, QuestTemplates.DAILY, QuestTemplate.Context.NONE);
        helper.assertTrue(daily.objectives().size() == 4, "run, jumps, squats, strikes");
        helper.assertTrue(daily.objectives().get(0).required() == QuestTemplates.TRAIN_RUN, "the run is the same at every rank");
        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.LivingEvent.LivingJumpEvent(p));
        helper.assertTrue(daily.objectives().get(1).progress() == 1, "a jump counts");
        p.setHealth(4f);
        QuestEngine.progress(p, com.beyondsolo.quest.ObjectiveType.TRAVEL, o -> true, QuestTemplates.TRAIN_RUN, p.position());
        QuestEngine.progress(p, QuestTemplates.TRAIN_JUMP, QuestTemplates.TRAIN_JUMPS);
        QuestEngine.progress(p, QuestTemplates.TRAIN_SQUAT, QuestTemplates.TRAIN_SQUATS);
        helper.assertTrue(QuestEngine.data(p).find(daily.id()) != null, "not done without the strikes");
        QuestEngine.progress(p, QuestTemplates.TRAIN_STRIKE, QuestTemplates.TRAIN_STRIKES);
        helper.assertTrue(QuestEngine.data(p).find(daily.id()) == null, "training complete");
        helper.assertTrue(p.getHealth() == p.getMaxHealth(), "restored in full");
        helper.assertTrue(!PenaltyZone.data(p).pending(), "no sentence for finished training");
        helper.succeed();
    }
}
