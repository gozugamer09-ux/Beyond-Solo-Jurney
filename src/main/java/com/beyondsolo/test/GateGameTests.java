package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.Buildups;
import com.beyondsolo.gate.DungeonLayout;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateLoot;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.gate.GateTheme;
import com.beyondsolo.gate.RewardCacheBlockEntity;
import com.beyondsolo.quest.ObjectiveType;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestObjective;
import com.beyondsolo.quest.QuestTemplate;
import com.beyondsolo.quest.QuestTemplates;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.registry.ModBlocks;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** Gate and dungeon tests. Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GateGameTests {
    private static final String ARENA = "evaluation_zone";

    private static ServerPlayer player(GameTestHelper helper, PlayerRank rank) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "gate_hunter"));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        p.setGameMode(GameType.SURVIVAL);
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        SystemAPI.awaken(p, rank, PlayerClass.WARRIOR);
        QuestTestAccess.markStarted(p);
        return p;
    }

    private static GateInstance instance(GameTestHelper helper, PlayerRank rank) {
        GateBlockEntity gate = GateService.spawnGate(helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 3)), rank, Direction.NORTH, false);
        GateInstance g = GateData.get(helper.getLevel().getServer()).get(gate.gateId());
        if (g == null) throw new IllegalStateException("gate was not registered");
        return g;
    }

    private static ServerLevel dungeon(GameTestHelper helper) {
        ServerLevel d = GateService.dungeon(helper.getLevel().getServer());
        helper.assertTrue(d != null, "the gate dimension exists");
        return d;
    }

    @GameTest(template = ARENA)
    public static void layoutIsDeterministicAndSpaced(GameTestHelper helper) {
        for (GateTheme theme : GateTheme.values()) {
            DungeonLayout a = DungeonLayout.plan(1234L + theme.ordinal(), theme);
            DungeonLayout b = DungeonLayout.plan(1234L + theme.ordinal(), theme);
            helper.assertTrue(a.rooms().equals(b.rooms()), "same seed, same cave");
            helper.assertTrue(a.rooms().get(0).kind() == DungeonLayout.Kind.ENTRANCE, "starts at the entrance");
            helper.assertTrue(a.boss().rx() > a.entrance().rx(), "the boss arena is the biggest room");
            helper.assertTrue(a.tunnels().size() == a.rooms().size() - 1, "every room but the first is joined by one tunnel");
            long main = a.rooms().stream().filter(r -> r.kind() != DungeonLayout.Kind.SIDE).count();
            helper.assertTrue(main == theme.rooms + 1, "rooms for rank " + theme.rank + ": " + main);
            for (DungeonLayout.Room r1 : a.rooms()) {
                helper.assertTrue(r1.center().getY() - r1.ry() >= DungeonLayout.MIN_Y - 2 && r1.center().getY() + r1.ry() <= DungeonLayout.MAX_Y + 2, "inside the rock");
                for (DungeonLayout.Room r2 : a.rooms()) {
                    if (r1 == r2) continue;
                    double min = r1.reach() + r2.reach();
                    helper.assertTrue(r1.center().distSqr(r2.center()) >= min * min, "rooms don't overlap");
                }
            }
            int[] bounds = a.bounds();
            helper.assertTrue(bounds[2] - bounds[0] < GateInstance.SLOT_SPACING / 2 && bounds[3] - bounds[1] < GateInstance.SLOT_SPACING, "fits in its slot");
        }
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void worldgenRankGrowsWithDistance(GameTestHelper helper) {
        RandomSource r = RandomSource.create(5);
        for (int i = 0; i < 200; i++) {
            helper.assertTrue(GateService.worldgenRank(new BlockPos(100, 70, 100), r).tier() <= 3, "near the centre gates are low");
            helper.assertTrue(GateService.worldgenRank(new BlockPos(7000, 70, 0), r).tier() >= 3, "far out gates are high");
        }
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void placedGateRegistersOnTick(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos, ModBlocks.GATE.get());
        GateBlockEntity gate = (GateBlockEntity) helper.getBlockEntity(pos);
        gate.setRank(PlayerRank.B);
        helper.succeedWhen(() -> {
            helper.assertTrue(gate.gateId() != null, "the gate got an id");
            GateInstance g = GateData.get(helper.getLevel().getServer()).get(gate.gateId());
            helper.assertTrue(g != null && g.rank() == PlayerRank.B, "and an instance of its rank");
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void caveIsCarvedWithBoss(GameTestHelper helper) {
        GateInstance g = instance(helper, PlayerRank.C);
        ServerLevel d = dungeon(helper);
        GateService.buildNow(helper.getLevel().getServer(), g);
        helper.assertTrue(g.built(), "built");
        BlockPos e = g.entrance();
        helper.assertTrue(d.getBlockState(e).isAir() && d.getBlockState(e.above()).isAir(), "room to stand at the entrance");
        helper.assertTrue(!d.getBlockState(e.below()).isAir(), "ground at the entrance");
        helper.assertTrue(d.getBlockState(g.bossRoomCenter().above(2)).isAir(), "the arena is hollow");
        // the tunnel to the next room is open halfway along
        DungeonLayout.Tunnel t = g.layout().tunnels().get(0);
        double[] mid = DungeonLayout.along(t, 0.5);
        BlockPos m = g.world(BlockPos.containing(mid[0], mid[1], mid[2]));
        helper.assertTrue(d.getBlockState(m).isAir(), "tunnels are dug");
        helper.assertTrue(g.bossSpawned() && g.boss() != null, "the boss is waiting");
        helper.assertTrue(d.getEntity(g.boss()) instanceof GateBossEntity boss && !boss.isAwake()
                && Math.abs(boss.getMaxHealth() - GateTheme.C.bossHealth()) < 0.01f, "dormant, with its rank's health");
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void bossDefeatOpensExitAndCache(GameTestHelper helper) {
        GateInstance g = instance(helper, PlayerRank.E);
        ServerLevel d = dungeon(helper);
        GateService.buildNow(helper.getLevel().getServer(), g);
        ChunkPos chunk = new ChunkPos(g.bossRoomCenter());
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) d.setChunkForced(chunk.x + x, chunk.z + z, true);
        GateBossEntity boss = (GateBossEntity) d.getEntity(g.boss());
        helper.assertTrue(boss.isInvulnerableTo(d.damageSources().generic()), "can't be hurt while dormant");
        GateService.wakeBossNow(d, g);
        helper.assertTrue(g.bossAwake() && boss.isAwake(), "woken");
        // the far-off chunks can take a while to start ticking under load, so wait on the boss rather than a clock
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(boss.introTicks() <= 0, "the intro is over: " + boss.introTicks()))
                .thenExecute(() -> {
                    boss.hurt(d.damageSources().generic(), 10_000f);
                    helper.assertTrue(g.bossDefeated() && g.state() == GateInstance.State.CLEARED, "the gate is cleared: hp " + boss.getHealth());
                })
                .thenWaitUntil(() -> helper.assertTrue(g.exitPos() != null && d.getBlockEntity(g.exitPos()) instanceof GateBlockEntity exit && exit.isExit(), "the way out opened"))
                .thenWaitUntil(() -> {
                    boolean cache = false;
                    BlockPos c = g.bossRoomCenter();
                    for (BlockPos p : BlockPos.betweenClosed(c.offset(-8, -3, -8), c.offset(8, 4, 8))) {
                        if (d.getBlockEntity(p) instanceof RewardCacheBlockEntity) cache = true;
                    }
                    helper.assertTrue(cache, "the reward cache came down");
                })
                .thenExecute(() -> {
                    for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) d.setChunkForced(chunk.x + x, chunk.z + z, false);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void enteringStartsGateQuest(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.D);
        GateInstance g = instance(helper, PlayerRank.D);
        helper.assertTrue(GateService.arrive(p, g), "went through");
        helper.assertTrue(GateService.isDungeon(p.level()), "in the gate dimension");
        helper.assertTrue(p.blockPosition().closerThan(g.entrance(), 3), "at the entrance");
        Quest q = QuestEngine.data(p).active().stream().filter(x -> GateService.QUEST_TEMPLATE.equals(x.template())).findFirst().orElse(null);
        helper.assertTrue(q != null, "the gate quest started");
        QuestEngine.progress(p, GateService.BOSS_KEY, 1);
        QuestObjective boss = q.objectives().stream().filter(o -> o.target().equals(GateService.BOSS_KEY)).findFirst().orElseThrow();
        helper.assertTrue(boss.done(), "boss objective counts");
        // a second trip does not hand out a second quest
        GateService.arrive(p, g);
        helper.assertTrue(QuestEngine.data(p).active().stream().filter(x -> GateService.QUEST_TEMPLATE.equals(x.template())).count() == 1, "one quest per gate");
        GateService.leave(p, g);
        helper.assertTrue(p.level() == helper.getLevel(), "back in the world");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void trialOfDepthsNeedsAGate(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E);
        Quest q = QuestTemplates.create(QuestTemplates.TRIAL_OF_DEPTHS, p, QuestTemplate.Context.NONE);
        helper.assertTrue(q != null, "made");
        QuestObjective gate = q.objectives().stream().filter(o -> o.type() == ObjectiveType.CUSTOM && o.target().equals(GateService.CLEAR_KEY)).findFirst().orElse(null);
        helper.assertTrue(gate != null, "clear a gate is part of the trial");
        QuestEngine.start(p, q);
        QuestEngine.progress(p, GateService.CLEAR_KEY, 1);
        helper.assertTrue(gate.done(), "a clear counts");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void gateMonstersAreScaledAndElemental(GameTestHelper helper) {
        GateInstance g = instance(helper, PlayerRank.A);
        Mob mob = GateService.spawnMonster(helper.getLevel(), g, EntityType.ZOMBIE, helper.absolutePos(new BlockPos(1, 2, 1)));
        helper.assertTrue(mob != null && mob.getPersistentData().contains(GateService.MOB_TAG), "tagged as a gate monster");
        helper.assertTrue(mob.getMaxHealth() > 20 * (1 + GateTheme.A.mobHealthBonus()) - 0.5, "tougher by rank: " + mob.getMaxHealth());
        Zombie target = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 1));
        mob.doHurtTarget(target);
        helper.assertTrue(Buildups.get(target, com.beyondsolo.combat.Statuses.BURN) > 0, "an A-rank gate's monsters burn");
        mob.discard();
        target.discard();
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void lootScalesWithRank(GameTestHelper helper) {
        RandomSource r = RandomSource.create(9);
        int low = 0, high = 0;
        for (int i = 0; i < 20; i++) {
            low += GateLoot.roll(PlayerRank.E, r).stream().mapToInt(ItemStack::getCount).sum();
            high += GateLoot.roll(PlayerRank.S, r).stream().mapToInt(ItemStack::getCount).sum();
        }
        helper.assertTrue(high > low * 2, "S caches hold much more than E caches");
        List<ItemStack> s = GateLoot.roll(PlayerRank.S, r);
        helper.assertTrue(s.stream().anyMatch(st -> st.is(net.minecraft.world.item.Items.NETHERITE_SCRAP)), "S caches have netherite");
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void clearedGateClosesInWorld(GameTestHelper helper) {
        GateInstance g = instance(helper, PlayerRank.E);
        g.setState(GateInstance.State.CLOSED);
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.AIR, pos));
    }
}
