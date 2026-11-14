package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.awakening.ArbiterEntity;
import com.beyondsolo.awakening.AwakeningData;
import com.beyondsolo.awakening.AwakeningService;
import com.beyondsolo.evaluation.EvaluationManager;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.gate.GateTheme;
import com.beyondsolo.item.LegendaryWeaponItem;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** The awakening trial: no System at the start, the sealed gate, the Arbiter, and what a fall or a kill gives. */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AwakeningGameTests {
    private static final String ARENA = "evaluation_zone";
    private static final BlockPos CRYSTAL = new BlockPos(3, 2, 3);

    /** A brand new player: nothing bound, nothing evaluated. */
    private static ServerPlayer newcomer(GameTestHelper helper) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "newcomer"));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        AwakeningService.reset(p);
        p.setGameMode(GameType.SURVIVAL);
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        p.getInventory().clearContent();
        return p;
    }

    private static GateInstance gate(GameTestHelper helper, PlayerRank rank) {
        GateBlockEntity gate = GateService.spawnGate(helper.getLevel(), helper.absolutePos(new BlockPos(5, 2, 5)), rank, Direction.NORTH, false);
        return GateData.get(helper.getLevel().getServer()).get(gate.gateId());
    }

    private static GateInstance trial(GameTestHelper helper, ServerPlayer p) {
        GateBlockEntity gate = GateService.spawnSealedGate(helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 5)), Direction.NORTH, p);
        return GateData.get(helper.getLevel().getServer()).get(gate.gateId());
    }

    private static void force(ServerLevel d, GateInstance g, boolean on) {
        ChunkPos chunk = new ChunkPos(g.bossRoomCenter());
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) d.setChunkForced(chunk.x + x, chunk.z + z, on);
    }

    @GameTest(template = ARENA)
    public static void newPlayersHaveNoSystem(GameTestHelper helper) {
        ServerPlayer p = newcomer(helper);
        helper.assertTrue(!SystemAPI.hasSystem(p), "no System at the start");
        helper.assertTrue(AwakeningService.stage(p) == AwakeningData.Stage.DORMANT, "not called yet");
        com.beyondsolo.evaluation.EvaluationTestAccess.interact(p, helper.absolutePos(CRYSTAL));
        helper.assertTrue(!EvaluationManager.isBeingEvaluated(p), "the crystal does not answer them");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void evaluationStillBindsTheSystem(GameTestHelper helper) {
        ServerPlayer p = newcomer(helper);
        SystemAPI.grantSystem(p);
        helper.assertTrue(SystemAPI.hasSystem(p) && !SystemAPI.get(p).isAwakened(), "bound but not evaluated");
        com.beyondsolo.evaluation.EvaluationTestAccess.interact(p, helper.absolutePos(CRYSTAL));
        helper.assertTrue(EvaluationManager.isBeingEvaluated(p), "the crystal answers once the System is bound");
        EvaluationManager.skip(p);
        ServerPlayer old = newcomer(helper);
        SystemAPI.awaken(old, PlayerRank.C, PlayerClass.MAGE);
        helper.assertTrue(SystemAPI.hasSystem(old), "anyone evaluated has the System");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void firstDRankClearCalls(GameTestHelper helper) {
        ServerPlayer p = newcomer(helper);
        AwakeningService.gateCleared(gate(helper, PlayerRank.E), List.of(p));
        helper.assertTrue(AwakeningService.stage(p) == AwakeningData.Stage.DORMANT, "an E-rank clear is not enough");
        ServerPlayer hunter = newcomer(helper);
        SystemAPI.awaken(hunter, PlayerRank.D, PlayerClass.WARRIOR);
        GateInstance d = gate(helper, PlayerRank.D);
        AwakeningService.gateCleared(d, List.of(p, hunter));
        helper.assertTrue(AwakeningService.stage(p) == AwakeningData.Stage.CALLED, "a D-rank clear calls them");
        helper.assertTrue(AwakeningService.stage(hunter) != AwakeningData.Stage.CALLED, "those with the System are left alone");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void sealedGateIsPersonal(GameTestHelper helper) {
        ServerPlayer p = newcomer(helper);
        helper.assertTrue(AwakeningService.call(p), "called");
        AwakeningData d = AwakeningService.data(p);
        helper.assertTrue(d.sealedPos() != null && d.trial() != null, "a sealed gate opened");
        helper.assertTrue(helper.getLevel().getBlockEntity(d.sealedPos()) instanceof GateBlockEntity gate && gate.isSealed(), "sealed");
        GateInstance g = GateData.get(helper.getLevel().getServer()).get(d.trial());
        helper.assertTrue(g != null && g.isTrial() && p.getUUID().equals(g.owner()) && g.theme() == GateTheme.TRIAL, "their own trial");
        ServerPlayer other = newcomer(helper);
        GateService.beginEnter(other, (GateBlockEntity) helper.getLevel().getBlockEntity(d.sealedPos()));
        helper.assertTrue(!GateService.inTransit(other), "nobody else can go through");
        AwakeningService.reset(p);
        helper.assertTrue(!(helper.getLevel().getBlockEntity(d.sealedPos() == null ? helper.absolutePos(CRYSTAL) : d.sealedPos()) instanceof GateBlockEntity), "reset closes it");
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void trialHoldsTheArbiter(GameTestHelper helper) {
        ServerPlayer p = newcomer(helper);
        GateInstance g = trial(helper, p);
        ServerLevel d = GateService.dungeon(helper.getLevel().getServer());
        GateService.buildNow(helper.getLevel().getServer(), g);
        helper.assertTrue(g.built() && g.boss() != null, "built, with its master");
        helper.assertTrue(d.getEntity(g.boss()) instanceof ArbiterEntity a && !a.isAwake() && a.isInvulnerableTo(d.damageSources().generic()), "the Arbiter kneels, untouchable");
        helper.assertTrue(g.layout().boss().center().getY() < g.layout().entrance().center().getY(), "the way leads down");
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void fallingGrantsTheSystemAndKeepsItems(GameTestHelper helper) {
        ServerPlayer p = newcomer(helper);
        p.getInventory().add(new ItemStack(Items.DIAMOND, 7));
        GateInstance g = trial(helper, p);
        ServerLevel d = GateService.dungeon(helper.getLevel().getServer());
        helper.assertTrue(GateService.arrive(p, g), "went through");
        helper.assertTrue(AwakeningService.stage(p) == AwakeningData.Stage.TRIAL && AwakeningService.trialAt(p) == g, "in the trial");
        // a fall on the way down is refused and they stand up again
        helper.assertTrue(CommonHooks.onLivingDeath(p, d.damageSources().generic()), "death is refused");
        helper.assertTrue(!AwakeningService.inSequence(p) && !SystemAPI.hasSystem(p), "not yet");
        // in the hall, before the woken Arbiter
        force(d, g, true);
        GateService.wakeBossNow(d, g);
        BlockPos c = g.bossRoomCenter();
        p.moveTo(c.getX() + 6.5, c.getY(), c.getZ() + 0.5);
        helper.assertTrue(CommonHooks.onLivingDeath(p, d.damageSources().generic()), "death is refused again");
        helper.assertTrue(AwakeningService.inSequence(p) && AwakeningService.stage(p) == AwakeningData.Stage.AWAKENING, "the awakening begins");
        helper.assertTrue(AwakeningService.arbiter(d, g).isVerdict(), "the Arbiter passes its verdict");
        AwakeningService.finishNow(p);
        force(d, g, false);
        helper.assertTrue(SystemAPI.hasSystem(p) && AwakeningService.stage(p) == AwakeningData.Stage.DONE, "the System is theirs");
        helper.assertTrue(!SystemAPI.get(p).isAwakened(), "evaluation still lies ahead");
        helper.assertTrue(p.getInventory().countItem(Items.DIAMOND) == 7, "nothing was lost");
        helper.assertTrue(p.level() == helper.getLevel(), "back in the world");
        helper.assertTrue(g.state() != GateInstance.State.OPEN, "the trial is over");
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 800)
    public static void slayingTheArbiterIsRewarded(GameTestHelper helper) {
        ServerPlayer p = newcomer(helper);
        GateInstance g = trial(helper, p);
        ServerLevel d = GateService.dungeon(helper.getLevel().getServer());
        helper.assertTrue(GateService.arrive(p, g), "went through");
        force(d, g, true);
        ArbiterEntity a = AwakeningService.arbiter(d, g);
        GateService.wakeBossNow(d, g);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(a.introTicks() <= 0, "it has risen: " + a.introTicks()))
                .thenExecute(() -> {
                    a.hurt(d.damageSources().generic(), 100_000f);
                    helper.assertTrue(g.bossDefeated(), "slain: " + a.getHealth());
                    helper.assertTrue(CommonHooks.onLivingDeath(p, d.damageSources().generic()), "still nobody dies here");
                    helper.assertTrue(AwakeningService.inSequence(p), "the awakening begins");
                    AwakeningService.finishNow(p);
                    force(d, g, false);
                    helper.assertTrue(SystemAPI.hasSystem(p), "the System is theirs");
                    helper.assertTrue(AwakeningService.SLAYER_TITLE.equals(SystemAPI.get(p).title()), "and the title");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void legendariesAreLegendary(GameTestHelper helper) {
        RandomSource r = RandomSource.create(3);
        java.util.Set<net.minecraft.world.item.Item> seen = new java.util.HashSet<>();
        for (int i = 0; i < 80; i++) {
            ItemStack s = AwakeningService.rollLegendary(r);
            helper.assertTrue(s.getItem() instanceof LegendaryWeaponItem && !s.isDamageableItem(), "an unbreakable legendary");
            seen.add(s.getItem());
        }
        helper.assertTrue(seen.size() == 4, "any of the four can drop");
        helper.succeed();
    }
}
