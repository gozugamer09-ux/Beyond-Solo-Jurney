package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.Config;
import com.beyondsolo.breaks.BreakState;
import com.beyondsolo.breaks.DungeonBreaks;
import com.beyondsolo.enemy.Affinity;
import com.beyondsolo.enemy.EnemyRoster;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.GateBlock;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.gate.RewardCacheBlockEntity;
import com.beyondsolo.network.RedGatePayload;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.redgate.RedGate;
import com.beyondsolo.redgate.RedGateConfig;
import com.beyondsolo.redgate.RedGateModule;
import com.beyondsolo.redgate.RedGateTestAccess;
import com.beyondsolo.redgate.RedGates;
import com.beyondsolo.redgate.RedRecord;
import com.beyondsolo.redgate.RedWorld;
import com.beyondsolo.registry.ModBlocks;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.UUID;

/**
 * Red gates. Run with {@code ./gradlew runGameTestServer}. The game-test server has no datapack dimensions, so the Red
 * Winter's patches are laid out in the overworld, far down one lane; test players are not in the player list and don't
 * tick, so these tests ask for some things (a fall, a beat of the cold, a raid) by hand.
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RedGateGameTests {
    private static final String ARENA = "evaluation_zone";

    private static ServerPlayer hunter(GameTestHelper helper, PlayerRank rank, String name) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        p.setGameMode(GameType.SURVIVAL);
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        if (rank != null) {
            SystemAPI.awaken(p, rank, PlayerClass.WARRIOR);
            QuestTestAccess.markStarted(p);
        }
        return p;
    }

    private static GateBlockEntity gate(GameTestHelper helper, PlayerRank rank) {
        return GateService.spawnGate(helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 3)), rank, Direction.NORTH, false);
    }

    private static GateInstance instance(GameTestHelper helper, GateBlockEntity gate) {
        GateInstance g = GateData.get(helper.getLevel().getServer()).get(gate.gateId());
        if (g == null) throw new IllegalStateException("gate was not registered");
        return g;
    }

    /** Keeps the chunks round a spot loaded and ticking (nobody real is there to keep them so). */
    private static void forceChunks(ServerLevel level, BlockPos at, int radius, boolean on) {
        ChunkPos c = new ChunkPos(at);
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) level.setChunkForced(c.x + x, c.z + z, on);
    }

    private static long progress(ServerPlayer p) {
        return SystemAPI.get(p).level() * 1_000_000_000L + SystemAPI.get(p).xp();
    }

    private static boolean hasQuest(ServerPlayer p) {
        return QuestEngine.data(p).active().stream().anyMatch(q -> RedGates.QUEST_TEMPLATE.equals(q.template()));
    }

    // ================================================================ which gates turn red

    /** Any gate can be a red gate in waiting (about one in sixteen), the same answer every time; trials and keyed gates never. */
    @GameTest(template = ARENA)
    public static void redGatesWaitInRandomGates(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int latent = 0, n = 4000;
        long latentSeed = -1, plainSeed = -1;
        for (long seed = 0; seed < n; seed++) {
            GateInstance g = new GateInstance(UUID.randomUUID(), PlayerRank.values()[(int) (seed % 6)], seed, 0, Level.OVERWORLD, BlockPos.ZERO);
            boolean a = RedGates.latent(server, g);
            helper.assertTrue(a == RedGates.latent(server, g), "the same answer every time");
            if (a) {
                latent++;
                if (latentSeed < 0) latentSeed = seed;
            } else if (plainSeed < 0) {
                plainSeed = seed;
            }
        }
        double share = latent / (double) n, chance = RedGateConfig.chance();
        helper.assertTrue(share > chance * 0.5 && share < chance * 1.6, "about " + chance + " of gates turn red: " + share);

        GateInstance trial = new GateInstance(UUID.randomUUID(), PlayerRank.D, latentSeed, 0, Level.OVERWORLD, BlockPos.ZERO);
        helper.assertTrue(RedGates.latent(server, trial), "this one would");
        trial.setOwner(UUID.randomUUID());
        helper.assertFalse(RedGates.latent(server, trial), "an awakening trial never turns red");
        GateInstance keyed = new GateInstance(UUID.randomUUID(), PlayerRank.D, latentSeed, 0, Level.OVERWORLD, BlockPos.ZERO);
        keyed.setKeyed(new GateInstance.Keyed(UUID.randomUUID(), 1, Level.OVERWORLD, Vec3.ZERO, 0f, null, false, 0L));
        helper.assertFalse(RedGates.latent(server, keyed), "nor does a keyed gate");

        GateInstance defused = new GateInstance(UUID.randomUUID(), PlayerRank.D, latentSeed, 0, Level.OVERWORLD, BlockPos.ZERO);
        RedGateTestAccess.defuse(server, defused.id());
        helper.assertFalse(RedGates.latent(server, defused), "a defused gate never turns red");
        GateInstance forced = new GateInstance(UUID.randomUUID(), PlayerRank.D, plainSeed, 0, Level.OVERWORLD, BlockPos.ZERO);
        helper.assertFalse(RedGates.latent(server, forced), "an ordinary gate");
        RedGateTestAccess.force(server, forced.id(), true);
        helper.assertTrue(RedGates.latent(server, forced), "turns red when forced (the command)");
        RedGateTestAccess.forget(server, defused.id());
        RedGateTestAccess.forget(server, forced.id());
        helper.succeed();
    }

    /** What waits behind it is a rank or two above the gate, its master one that lives at that rank, its creatures our own. */
    @GameTest(template = ARENA)
    public static void insideIsHarderAndItsOwn(GameTestHelper helper) {
        RandomSource r = RandomSource.create(3);
        for (PlayerRank rank : PlayerRank.values()) {
            int twice = 0;
            for (int i = 0; i < 400; i++) {
                PlayerRank in = RedGateTestAccess.insideRank(rank, r);
                int up = in.ordinal() - rank.ordinal();
                helper.assertTrue(rank == PlayerRank.S ? in == PlayerRank.S : up >= 1 && up <= 2, rank + " gate, " + in + " inside");
                if (up == 2) twice++;
                BossKind k = RedGateTestAccess.masterKind(in, r);
                helper.assertTrue(k.livesAt(in), k.id + " lives at " + in);
            }
            if (rank.ordinal() <= PlayerRank.B.ordinal()) helper.assertTrue(twice > 50 && twice < 160, "now and then two ranks up: " + twice);
        }
        for (PlayerRank in : PlayerRank.values()) {
            EnemyRoster.Mix mix = RedGateTestAccess.mix(in);
            helper.assertTrue(mix.affinity() == Affinity.FROST, "frost-touched");
            for (EnemyRoster.Entry e : mix.entries()) {
                String ns = BuiltInRegistries.ENTITY_TYPE.getKey(e.type().get()).getNamespace();
                helper.assertTrue(e.own() && ns.equals(BeyondSolo.MOD_ID), "no vanilla creatures in a red gate: " + ns);
            }
        }
        // what the client is told comes through whole (negative coordinates too)
        int[] v = new int[RedGatePayload.SIZE];
        for (int i = 0; i < v.length; i++) v[i] = (i * 7919) % 50_000 - 25_000;
        RedGatePayload sent = new RedGatePayload(v, Component.literal("Rimefang Alpha"));
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        RedGatePayload.STREAM_CODEC.encode(buf, sent);
        RedGatePayload got = RedGatePayload.STREAM_CODEC.decode(buf);
        helper.assertTrue(Arrays.equals(sent.v(), got.v()) && got.boss().getString().equals("Rimefang Alpha"), "the payload survives the trip");
        helper.succeed();
    }

    /** Someone without the System through first: the gate stays an ordinary one, for good. */
    @GameTest(template = ARENA)
    public static void aHunterWithoutTheSystemDefusesIt(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        long seed = 0;
        while (!RedGates.latent(server, new GateInstance(UUID.randomUUID(), PlayerRank.D, seed, 0, Level.OVERWORLD, BlockPos.ZERO))) seed++;
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        helper.getLevel().setBlock(pos, ModBlocks.GATE.get().defaultBlockState().setValue(GateBlock.FACING, Direction.NORTH), 3);
        GateBlockEntity gate = (GateBlockEntity) helper.getLevel().getBlockEntity(pos);
        GateInstance g = GateData.get(server).create(UUID.randomUUID(), PlayerRank.D, seed, helper.getLevel().dimension(), pos);
        gate.setup(g.id(), PlayerRank.D, false, -1);
        helper.assertTrue(RedGates.latent(server, g), "a red gate in waiting");

        ServerPlayer nobody = hunter(helper, null, "red_unawakened");
        helper.assertFalse(RedGates.enter(nobody, gate, g), "it doesn't turn red for them");
        helper.assertTrue(RedGateTestAccess.defused(server, g.id()) && !RedGates.latent(server, g), "and it never will");
        ServerPlayer hunter = hunter(helper, PlayerRank.D, "red_after");
        helper.assertFalse(RedGates.enter(hunter, gate, g), "not even for an awakened hunter after them");
        helper.assertTrue(RedGateTestAccess.run(server, g.id()) == null && !gate.isRed(), "an ordinary gate");
        g.setState(GateInstance.State.CLOSED);
        RedGateTestAccess.forget(server, g.id());
        helper.succeed();
    }

    // ================================================================ in and out

    /**
     * The first awakened hunter through turns it red and lands in the breach of the Red Winter; it seals behind them;
     * its way out turns them back, and so does the gate in the world turn back anyone who comes later.
     */
    @GameTest(template = ARENA, timeoutTicks = 1200)
    public static void enteringTurnsItRedAndSealsIt(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel red = RedWorld.level(server);
        ServerPlayer p = hunter(helper, PlayerRank.C, "red_first");
        GateBlockEntity gate = gate(helper, PlayerRank.C);
        GateInstance g = instance(helper, gate);
        RedGateTestAccess.force(server, g.id(), true);
        GateService.beginEnter(p, gate);
        RedGate run = RedGateTestAccess.run(server, g.id());
        helper.assertTrue(run != null && run.state() == RedGate.State.OPEN, "it turned red");
        helper.assertTrue(gate.isRed(), "and looks it");
        helper.assertTrue(run.party().contains(p.getUUID()) && RedGates.occupied(p), "on the way in");
        helper.assertTrue(run.inside.ordinal() > PlayerRank.C.ordinal(), "what waits inside is above the gate's rank");
        ServerPlayer late = hunter(helper, PlayerRank.C, "red_late");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(run.insideNow().contains(p.getUUID()), "arrived"))
                .thenExecute(() -> {
                    helper.assertTrue(RedGateTestAccess.slotAt(p.level(), p.blockPosition()) == RedGateTestAccess.slot(run), "in its patch of the Red Winter");
                    helper.assertTrue(run.exit() != null && red.getBlockEntity(run.exit()) instanceof GateBlockEntity exit && exit.isExit() && exit.isRed(),
                            "by its way out, red");
                    helper.assertTrue(p.blockPosition().closerThan(run.exit(), 6), "standing by it");
                    helper.assertTrue(hasQuest(p), "the System's emergency quest");
                    helper.assertTrue(RedGates.holding(g), "its gate's break clock is held");
                })
                .thenWaitUntil(() -> helper.assertTrue(run.state() == RedGate.State.SEALED, "sealed: " + run.state()))
                .thenExecute(() -> {
                    GateBlockEntity exit = (GateBlockEntity) red.getBlockEntity(run.exit());
                    helper.assertTrue(RedGates.exit(p, exit), "its way out answers for itself");
                    GateService.beginEnter(late, gate);
                    helper.assertFalse(run.party().contains(late.getUUID()), "a latecomer is turned away");
                })
                .thenIdle(50)
                .thenExecute(() -> {
                    helper.assertTrue(run.insideNow().contains(p.getUUID()) && run.state() == RedGate.State.SEALED, "still sealed in");
                    helper.assertTrue(RedGateTestAccess.slotAt(p.level(), p.blockPosition()) == RedGateTestAccess.slot(run), "still in the Red Winter");
                    RedGateTestAccess.abort(server, run);
                    helper.assertTrue(run.state() == RedGate.State.ENDED && g.state() == GateInstance.State.CLOSED, "ended, its gate closed");
                    helper.assertFalse(RedGates.occupied(p), "and the hunter out");
                    RedGateTestAccess.forget(server, g.id());
                })
                .thenSucceed();
    }

    /** Its master falls: the seal breaks, a red cache comes down, a way out opens; out they go, paid, and the gate closes. */
    @GameTest(template = ARENA, timeoutTicks = 1200)
    public static void theMasterFallingOpensTheWayOut(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel red = RedWorld.level(server);
        ServerPlayer p = hunter(helper, PlayerRank.C, "red_clear");
        GateBlockEntity gate = gate(helper, PlayerRank.D);
        GateInstance g = instance(helper, gate);
        RedGate run = RedGateTestAccess.startNow(p, gate, g);
        helper.assertTrue(run != null && run.insideNow().contains(p.getUUID()), "in");
        RedGateTestAccess.buildNow(server, run);
        helper.assertTrue(run.built() && run.keep() != null && !run.camps().isEmpty(), "the patch is built: the keep and its camps");
        BlockPos spot = RedGateTestAccess.bossSpot(run);
        helper.assertTrue(red.getBlockState(spot).isAir() && red.getBlockState(spot.above(2)).isAir(), "room for its master in the keep");
        forceChunks(red, spot, 1, true);
        long before = progress(p);
        RedRecord record = p.getData(RedGateModule.RECORD);
        int cleared = record.cleared();
        GateBlockEntity exit = (GateBlockEntity) red.getBlockEntity(run.exit());
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(RedGateTestAccess.jump(p, "clear", 0), "its master is there to fall");
                    helper.assertTrue(run.cleared() && run.state() == RedGate.State.RELEASED, "the seal is broken");
                    helper.assertFalse(exit.isRed(), "the breach's way out is red no more");
                })
                .thenWaitUntil(() -> {
                    boolean cache = false;
                    for (BlockPos q : BlockPos.betweenClosed(spot.offset(-8, -4, -8), spot.offset(8, 6, 8))) {
                        if (red.getBlockEntity(q) instanceof RewardCacheBlockEntity c && c.isRed()) cache = true;
                    }
                    helper.assertTrue(cache, "a red cache came down");
                })
                .thenExecute(() -> {
                    helper.assertTrue(progress(p) > before, "the clear paid");
                    helper.assertTrue(record.cleared() == cleared + 1, "and is on the hunter's record");
                })
                .thenWaitUntil(() -> helper.assertTrue(run.keepExit() != null, "a way out opened where it fell"))
                .thenExecute(() -> helper.assertTrue(RedGates.exit(p, exit), "the way out lets them go now"))
                .thenWaitUntil(() -> helper.assertFalse(run.insideNow().contains(p.getUUID()), "out"))
                .thenExecute(() -> {
                    helper.assertTrue(p.blockPosition().closerThan(g.gatePos(), 6), "back in front of the gate");
                    helper.assertTrue(run.state() == RedGate.State.ENDED && g.state() == GateInstance.State.CLOSED, "it is over and the gate closes");
                    helper.assertFalse(RedGates.holding(g), "nothing held");
                    forceChunks(red, spot, 1, false);
                    RedGateTestAccess.forget(server, g.id());
                })
                .thenSucceed();
    }

    // ================================================================ the Red Winter

    /** Time runs faster inside; at night the creatures of the snow come, our own and angrier, and go again at dawn. */
    @GameTest(template = ARENA, timeoutTicks = 1200)
    public static void nightsComeFastAndBringRaids(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel red = RedWorld.level(server);
        ServerPlayer p = hunter(helper, PlayerRank.B, "red_night");
        GateBlockEntity gate = gate(helper, PlayerRank.C);
        GateInstance g = instance(helper, gate);
        RedGate run = RedGateTestAccess.startNow(p, gate, g);
        helper.assertTrue(run != null, "in");
        BlockPos at = p.blockPosition();
        forceChunks(red, at, 3, true);
        long t0 = run.insideTime();
        long[] since = {red.getGameTime()};
        int rate = RedGateConfig.timeRate();
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    long real = red.getGameTime() - since[0];
                    long passed = run.insideTime() - t0;
                    helper.assertTrue(passed >= (real - 3) * rate && passed <= (real + 3) * rate, "the clock runs " + rate + " times faster: " + passed + " in " + real);
                    helper.assertTrue(RedGateTestAccess.jump(p, "seal", 0), "seal it now");
                })
                .thenWaitUntil(() -> helper.assertTrue(run.state() == RedGate.State.SEALED, "sealed"))
                .thenExecute(() -> helper.assertTrue(RedGateTestAccess.jump(p, "night", 0), "on to the night"))
                .thenWaitUntil(() -> helper.assertTrue(run.night(), "night falls"))
                .thenExecute(() -> {
                    int came = RedGateTestAccess.raid(server, run);
                    helper.assertTrue(came > 0, "a wave came");
                    for (UUID id : RedGateTestAccess.raiders(run)) {
                        Entity e = red.getEntity(id);
                        helper.assertTrue(e instanceof Mob m && m.getPersistentData().hasUUID(RedGates.MOB_TAG)
                                && BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).getNamespace().equals(BeyondSolo.MOD_ID)
                                && m.getTarget() == p, "one of the Red Winter's own, after the hunter: " + e);
                        helper.assertTrue(((Mob) e).getMaxHealth() > 0 && ((Mob) e).getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
                                .getModifier(BeyondSolo.id("red_gate_fury_health")) != null, "angrier than its kind outside");
                    }
                    helper.assertTrue(RedGateTestAccess.jump(p, "dawn", 0), "on to the dawn");
                })
                .thenWaitUntil(() -> {
                    for (UUID id : RedGateTestAccess.raiders(run)) helper.assertTrue(red.getEntity(id) == null, "the raiders went back into the snow");
                    helper.assertFalse(run.night(), "day");
                })
                .thenExecute(() -> {
                    helper.assertTrue(p.getData(RedGateModule.RECORD).nights() >= 1, "a night survived, on the record");
                    RedGateTestAccess.abort(server, run);
                    forceChunks(red, at, 3, false);
                    RedGateTestAccess.forget(server, g.id());
                })
                .thenSucceed();
    }

    /** Out in the open at night the cold bites; a fire close by drives it off. */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void theColdBitesAndFireWarms(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel red = RedWorld.level(server);
        ServerPlayer p = hunter(helper, PlayerRank.C, "red_cold");
        GateBlockEntity gate = gate(helper, PlayerRank.C);
        GateInstance g = instance(helper, gate);
        RedGate run = RedGateTestAccess.startNow(p, gate, g);
        helper.assertTrue(run != null, "in");
        helper.assertTrue(RedGateTestAccess.jump(p, "time", 18000), "the middle of the night");
        // well up in the open air, nothing overhead
        p.moveTo(p.getX(), p.getY() + 40, p.getZ());
        RedGateTestAccess.setChill(p, 0);
        for (int i = 0; i < 10; i++) RedGateTestAccess.cold(p);
        int cold = RedGates.chill(p);
        helper.assertTrue(cold >= 20, "chilled in the open at night: " + cold);
        BlockPos fire = p.blockPosition().below();
        red.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true), 3);
        for (int i = 0; i < 3; i++) RedGateTestAccess.cold(p);
        helper.assertTrue(RedGates.chill(p) < cold - 20, "warmed by the fire: " + RedGates.chill(p));
        red.setBlock(fire, Blocks.AIR.defaultBlockState(), 3);
        RedGateTestAccess.abort(server, run);
        helper.assertTrue(RedGates.chill(p) == 0 && p.getTicksFrozen() == 0, "home, the cold is gone");
        RedGateTestAccess.forget(server, g.id());
        helper.succeed();
    }

    /** Everyone it took falls: the red fades, it is an ordinary gate again, and one at its last minute before it breaks. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void theLastToFallFailsIt(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer p = hunter(helper, PlayerRank.C, "red_fallen");
        GateBlockEntity gate = gate(helper, PlayerRank.C);
        GateInstance g = instance(helper, gate);
        RedGate run = RedGateTestAccess.startNow(p, gate, g);
        helper.assertTrue(run != null, "in");
        int fallen = p.getData(RedGateModule.RECORD).fallen();
        RedGateTestAccess.fell(p);
        helper.assertFalse(run.insideNow().contains(p.getUUID()), "out of it");
        helper.assertFalse(hasQuest(p), "the quest failed");
        helper.assertTrue(p.getData(RedGateModule.RECORD).fallen() == fallen + 1, "a fall on the record");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(run.state() == RedGate.State.ENDED, "it failed"))
                .thenExecute(() -> {
                    helper.assertTrue(RedGateTestAccess.defused(server, g.id()) && !RedGates.latent(server, g), "it won't turn red again");
                    helper.assertFalse(gate.isRed(), "the red fades");
                    helper.assertTrue(g.state() == GateInstance.State.OPEN, "an ordinary gate again");
                    if (Config.GATE_BREAK_MINUTES.get() > 0 && DungeonBreaks.canBreak(g)) {
                        BreakState b = g.breakState();
                        helper.assertTrue(b.clockStarted() && b.breakAt() <= helper.getLevel().getGameTime() + DungeonBreaks.IMMINENT_TICKS,
                                "at its last minute before it breaks");
                    }
                    // not here, though: other tests share this world
                    g.setState(GateInstance.State.CLOSED);
                    RedGateTestAccess.forget(server, g.id());
                })
                .thenSucceed();
    }
}
