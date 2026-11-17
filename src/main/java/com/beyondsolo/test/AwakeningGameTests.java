package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.awakening.ArbiterEntity;
import com.beyondsolo.awakening.AwakeningData;
import com.beyondsolo.awakening.AwakeningService;
import com.beyondsolo.awakening.AwakeningTestAccess;
import com.beyondsolo.depths.DepthsBuild;
import com.beyondsolo.depths.DepthsTestAccess;
import com.beyondsolo.depths.HiddenKind;
import com.beyondsolo.depths.HiddenRooms;
import com.beyondsolo.depths.HiddenSealBlock;
import com.beyondsolo.evaluation.EvaluationManager;
import com.beyondsolo.gate.DungeonBuilder;
import com.beyondsolo.gate.DungeonLayout;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.gate.GateTestAccess;
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
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

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

    /** A D-rank gate (in the arena) that hides the sealed door for {@code p}: the first of a few whose seed has room for it. */
    private static GateInstance doorGate(GameTestHelper helper, ServerPlayer p) {
        for (int i = 0; i < 8; i++) {
            GateInstance g = gate(helper, PlayerRank.D);
            AwakeningService.enteringGate(p, g);
            if (g.depths().hidden(g) == HiddenKind.SEALED) return g;
        }
        throw new IllegalStateException("no D-rank cave had room for the sealed door");
    }

    @GameTest(template = ARENA)
    public static void theFirstDRankGateHidesTheDoor(GameTestHelper helper) {
        ServerPlayer p = newcomer(helper);
        GateInstance e = gate(helper, PlayerRank.E);
        AwakeningService.enteringGate(p, e);
        helper.assertTrue(e.depths().hidden(e) != HiddenKind.SEALED, "an E-rank gate hides no door");
        ServerPlayer hunter = newcomer(helper);
        SystemAPI.awaken(hunter, PlayerRank.D, PlayerClass.WARRIOR);
        GateInstance other = gate(helper, PlayerRank.D);
        AwakeningService.enteringGate(hunter, other);
        helper.assertTrue(other.depths().hidden(other) != HiddenKind.SEALED, "nor a gate someone with the System walks into");
        GateInstance d = doorGate(helper, p);
        helper.assertTrue(AwakeningService.holdsTheDoor(d), "a D-rank gate hides it for a player without the System");
        DungeonLayout.Tunnel way = d.layout().hiddenTunnel();
        helper.assertTrue(way != null && d.layout().rooms().get(way.from()).kind() == DungeonLayout.Kind.BOSS, "past the boss's hall");
        DungeonLayout plain = DungeonLayout.plan(d.seed(), d.theme(), d.realm(), d.shape(), false);
        helper.assertTrue(d.layout().rooms().subList(0, plain.rooms().size()).equals(plain.rooms()), "the rest of the cave is as its seed made it");

        ServerLevel level = helper.getLevel();
        AwakeningService.gateCleared(level, e, List.of(p));
        helper.assertTrue(AwakeningService.stage(p) == AwakeningData.Stage.DORMANT, "an E-rank clear calls nobody");
        AwakeningService.gateCleared(level, d, List.of(p, hunter));
        helper.assertTrue(AwakeningService.stage(p) == AwakeningData.Stage.CALLED, "the fall of its boss calls them");
        helper.assertTrue(AwakeningService.stage(hunter) != AwakeningData.Stage.CALLED, "those with the System are left alone");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void theDoorsChamberFitsBehindEveryBoss(GameTestHelper helper) {
        int placed = 0, tried = 0;
        for (GateTheme theme : List.of(GateTheme.D, GateTheme.C, GateTheme.B, GateTheme.A, GateTheme.S)) {
            for (long seed = 0; seed < 30; seed++) {
                long s = 7919L * seed + theme.ordinal();
                DungeonLayout with = DungeonLayout.planSealed(s, theme);
                tried++;
                DungeonLayout.Room h = with.hidden();
                if (h == null) continue;
                placed++;
                helper.assertTrue(with.tunnelsOf(h.index()).size() == 1, "one way in");
                helper.assertTrue(Math.abs(h.floorY() - with.boss().floorY()) <= 2, "about level with the hall: " + h.floorY() + " vs " + with.boss().floorY());
                for (DungeonLayout.Room o : with.rooms()) {
                    if (o == h) continue;
                    double min = o.reach() + h.reach();
                    helper.assertTrue(o.center().distSqr(h.center()) >= min * min, "it overlaps no room");
                }
                int[] b = with.bounds();
                helper.assertTrue(b[2] - b[0] < GateInstance.SLOT_SPACING / 2 && b[3] - b[1] < GateInstance.SLOT_SPACING, "still fits in its slot");
            }
        }
        helper.assertTrue(placed * 100 >= tried * 95, "nearly every cave has room for it: " + placed + "/" + tried);

        // and the temple behind the door is short: in, an antechamber, the Arbiter's hall; no side caves
        DungeonLayout temple = DungeonLayout.temple(42L);
        helper.assertTrue(temple.rooms().equals(DungeonLayout.temple(42L).rooms()), "same seed, same temple");
        helper.assertTrue(temple.rooms().size() == DungeonLayout.TEMPLE_ROOMS + 2 && temple.rooms().get(1).kind() == DungeonLayout.Kind.CAVERN
                && temple.rooms().stream().noneMatch(r -> r.kind() == DungeonLayout.Kind.SIDE), "a short way down: " + temple.rooms().size());
        helper.assertTrue(temple.boss().center().getY() < temple.entrance().center().getY(), "down");
        helper.succeed();
    }

    /**
     * The whole road as the playtest asked for it: the door hides in the first D-rank cave, its wall gives nothing to
     * knocks, breaks when the boss falls, the cave's way out won't take them, through the door is the temple (the
     * Arbiter's tenets, nothing else alive but the Arbiter) and the door shuts behind them; they wake in front of the
     * gate they first went into, with everything they carried.
     */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void theDoorOpensInTheCaveOnceItsBossFalls(GameTestHelper helper) {
        ServerPlayer p = newcomer(helper);
        p.getInventory().add(new ItemStack(Items.DIAMOND, 3));
        GateInstance cave = doorGate(helper, p);
        ServerLevel dungeon = GateService.dungeon(helper.getLevel().getServer());
        GateService.buildNow(helper.getLevel().getServer(), cave);
        helper.assertTrue(cave.built() && cave.layout().hidden() != null, "built, with the door's chamber");

        DepthsBuild.Mouth mouth = DepthsBuild.sealMouth(cave);
        BlockPos m = BlockPos.containing(mouth.center());
        int reach = (int) Math.ceil(mouth.radius() + 3);
        List<BlockPos> wall = new java.util.ArrayList<>();
        for (BlockPos q : BlockPos.betweenClosed(m.offset(-reach, -reach, -reach), m.offset(reach, reach, reach))) {
            if (dungeon.getBlockState(q).getBlock() instanceof HiddenSealBlock) wall.add(q.immutable());
        }
        helper.assertTrue(wall.size() >= 4, "a wall stands across the way past the hall: " + wall.size());
        for (int i = 0; i < 4; i++) {
            DepthsTestAccess.resetCooldowns(p);
            HiddenRooms.knock(p, wall.get(0));
        }
        helper.assertTrue(!cave.depths().hiddenOpen(), "it gives nothing to knocks");

        // the boss falls with them inside
        AwakeningService.gateCleared(dungeon, cave, List.of(p));
        DungeonLayout.Room chamber = cave.layout().hidden();
        BlockPos floor = DepthsBuild.floorCenter(dungeon, cave, chamber);
        p.moveTo(floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5);
        AwakeningService.revealDoors(dungeon, cave, List.of(p));
        AwakeningData d = AwakeningService.data(p);
        helper.assertTrue(cave.depths().hiddenOpen(), "the wall breaks");
        helper.assertTrue(d.sealedPos() != null && dungeon.getBlockEntity(d.sealedPos()) instanceof GateBlockEntity door && door.isSealed(),
                "their sealed gate stands in the chamber");
        helper.assertTrue(DepthsTestAccess.inside(cave, chamber, Vec3.atCenterOf(d.sealedPos())), "in the hidden chamber, inside the cave");
        GateInstance trial = GateData.get(helper.getLevel().getServer()).get(d.trial());
        helper.assertTrue(trial != null && trial.isTrial() && trial.temple() && p.getUUID().equals(trial.owner()), "their own trial, a temple");
        Vec3 home = ((GateBlockEntity) helper.getLevel().getBlockEntity(cave.gatePos())).front();
        helper.assertTrue(trial.trialHome() != null && trial.trialHome().pos().distanceTo(home) < 0.01, "they will wake where they went in");

        // the way out won't take them
        helper.assertTrue(AwakeningService.heldBack(p), "held back");
        GateService.openExit(dungeon, cave, cave.bossRoomCenter(), Direction.NORTH);
        GateService.beginExit(p, (GateBlockEntity) dungeon.getBlockEntity(cave.exitPos()));
        helper.assertTrue(!GateService.inTransit(p), "the exit turns them back");

        // through the door: it shuts behind them
        BlockPos doorPos = d.sealedPos();
        helper.assertTrue(GateService.arrive(p, trial), "through the door");
        helper.assertTrue(AwakeningService.stage(p) == AwakeningData.Stage.TRIAL, "in the trial");
        helper.assertTrue(dungeon.getBlockEntity(doorPos) instanceof GateBlockEntity shut && shut.isClosing(), "the door closes behind them");

        // the antechamber: the Arbiter's tenets on a stele
        DungeonLayout.Room ante = trial.layout().rooms().get(1);
        BlockPos ac = trial.world(ante.center());
        int signs = 0, tenets = 0;
        for (BlockPos q : BlockPos.betweenClosed(ac.offset(-ante.rx(), -ante.ry(), -ante.rz()), ac.offset(ante.rx(), ante.ry(), ante.rz()))) {
            if (!(dungeon.getBlockEntity(q) instanceof SignBlockEntity sign)) continue;
            signs++;
            if (sign.getFrontText().getMessage(1, false).getContents() instanceof TranslatableContents t && t.getKey().startsWith(DungeonBuilder.TENET)) tenets++;
        }
        helper.assertTrue(signs == 4 && tenets == 3, "a heading and three tenets: " + signs + " signs, " + tenets + " tenets");

        // walking down wakes nothing: the Arbiter is all there is
        p.moveTo(ac.getX() + 0.5, trial.world(new BlockPos(0, ante.floorY(), 0)).getY(), ac.getZ() + 0.5);
        GateTestAccess.watch(p);
        AABB temple = new AABB(trial.entrance()).inflate(400, 80, 400);
        List<Mob> others = dungeon.getEntitiesOfClass(Mob.class, temple, mob -> !(mob instanceof ArbiterEntity));
        helper.assertTrue(others.isEmpty(), "no creatures in the temple: " + others);

        // the awakening: they wake in front of the gate they first went into, with everything they carried
        AwakeningService.begin(p, trial, false);
        AwakeningService.finishNow(p);
        helper.assertTrue(SystemAPI.hasSystem(p) && AwakeningService.stage(p) == AwakeningData.Stage.DONE, "the System is theirs");
        helper.assertTrue(p.position().multiply(1, 0, 1).distanceTo(home.multiply(1, 0, 1)) < 1.5, "awake in front of the gate: " + p.position() + " vs " + home);
        helper.assertTrue(p.getInventory().countItem(Items.DIAMOND) == 3, "nothing was lost");
        helper.assertTrue(trial.state() != GateInstance.State.OPEN, "the trial is over");
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

    /**
     * Regression for the 0.1.0 playtest crash: a ring or a pillar that fells the challenger ends the fight in the
     * middle of the Arbiter's attack tick (the trial refuses the death, the Arbiter passes its verdict and clears every
     * ring and mark). Walking the rings while that happened threw ConcurrentModificationException and stopped the
     * server. A cow stands in for the challenger here (test players can't be hurt): the moment it is hit, the verdict
     * is passed, as the fall of a real challenger would.
     */
    @GameTest(template = ARENA)
    public static void aFallMidStrikeEndsTheFightCleanly(GameTestHelper helper) {
        ArbiterEntity rings = helper.spawn(com.beyondsolo.registry.ModEntities.ARBITER.get(), new BlockPos(5, 2, 5));
        ArbiterEntity pillars = helper.spawn(com.beyondsolo.registry.ModEntities.ARBITER.get(), new BlockPos(1, 2, 1));
        AwakeningTestAccess.ready(rings);
        AwakeningTestAccess.ready(pillars);
        Cow first = helper.spawn(EntityType.COW, new BlockPos(1, 2, 5));
        Cow second = helper.spawn(EntityType.COW, new BlockPos(5, 2, 1));
        Consumer<LivingIncomingDamageEvent> falls = e -> {
            if (e.getEntity() == first) rings.passVerdict();
            if (e.getEntity() == second) pillars.passVerdict();
        };
        NeoForge.EVENT_BUS.addListener(falls);
        try {
            // two rings from where the first cow stands: the first ring fells it, and the second must not be walked
            AwakeningTestAccess.wave(rings, first.position(), 0);
            AwakeningTestAccess.wave(rings, first.position(), 0);
            // a pillar not due yet, then two due on the second cow
            AwakeningTestAccess.mark(pillars, second.position(), 40);
            AwakeningTestAccess.mark(pillars, second.position(), 0);
            AwakeningTestAccess.mark(pillars, second.position(), 0);
            AwakeningTestAccess.tickAttacks(rings);
            AwakeningTestAccess.tickAttacks(pillars);
        } catch (ConcurrentModificationException e) {
            helper.fail("the fight ending mid-strike broke the Arbiter's attack tick: " + e);
        } finally {
            NeoForge.EVENT_BUS.unregister(falls);
        }
        helper.assertTrue(first.isDeadOrDying() && second.isDeadOrDying(), "both were struck down");
        helper.assertTrue(rings.isVerdict() && AwakeningTestAccess.pending(rings) == 0, "the verdict stops every ring");
        helper.assertTrue(pillars.isVerdict() && AwakeningTestAccess.pending(pillars) == 0, "and every pillar");
        // and with the fight over, its next tick strikes nothing
        AwakeningTestAccess.wave(rings, first.position(), 0);
        Cow third = helper.spawn(EntityType.COW, new BlockPos(1, 2, 5));
        AwakeningTestAccess.tickAttacks(rings);
        helper.assertTrue(third.getHealth() == third.getMaxHealth(), "a judged Arbiter strikes no one");
        rings.discard();
        pillars.discard();
        third.discard();
        helper.succeed();
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
