package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.economy.ShopCatalog;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.gate.RewardCacheBlockEntity;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.tower.CitadelHalls;
import com.beyondsolo.tower.CitadelLordEntity;
import com.beyondsolo.tower.RushTier;
import com.beyondsolo.tower.TowerAPI;
import com.beyondsolo.tower.TowerData;
import com.beyondsolo.tower.TowerFloors;
import com.beyondsolo.tower.TowerModule;
import com.beyondsolo.tower.TowerRun;
import com.beyondsolo.tower.TowerTestAccess;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Crimson Citadel: its floor table, the rush tiers, the key, and runs driven by hand (the climb, a gatekeeper, the
 * lord and his Sigil, the Endless Spire, the boss rush, a fall), plus the lord's moves. Run with
 * {@code ./gradlew runGameTestServer}. On the game-test server the Citadel is the overworld, its halls far out along
 * z; test players load no chunks, so each run's halls are held loaded by the test ({@link TowerTestAccess#hold}).
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TowerGameTests {
    private static final String ARENA = "evaluation_zone";
    private static final String REFUSE = "tower.beyondsolo.refuse.";

    // ================================================================ helpers

    private static ServerPlayer player(GameTestHelper helper, PlayerRank rank, String name) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        p.setGameMode(GameType.SURVIVAL);
        p.removeAllEffects();
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        SystemAPI.awaken(p, rank, PlayerClass.WARRIOR);
        QuestTestAccess.markStarted(p);
        return p;
    }

    /** A hunter's Citadel records wiped, with the key bound. */
    private static TowerData keyed(ServerPlayer p) {
        TowerData d = TowerRun.data(p);
        d.reset();
        d.bound = true;
        return d;
    }

    /** Holds the run's halls loaded and waits until they tick. */
    private static List<ChunkPos> hold(ServerPlayer p) {
        List<ChunkPos> held = TowerTestAccess.hold(p);
        ServerLevel level = TowerRun.citadel(p.server);
        if (level != null) TestChunks.ticking(level, held);
        return held;
    }

    /** Ends the run (if any) and lets its halls go. */
    private static void end(ServerPlayer p, List<ChunkPos> held) {
        TowerRun.cancel(p);
        ServerLevel level = TowerRun.citadel(p.server);
        if (level != null) TowerTestAccess.release(level, held);
    }

    /** A test's ticks: a failure ends the run and lets its halls go before it is reported. */
    private static void each(GameTestHelper helper, ServerPlayer p, List<ChunkPos> held, Runnable tick) {
        helper.onEachTick(() -> {
            try {
                tick.run();
            } catch (RuntimeException e) {
                end(p, held);
                throw e;
            }
        });
    }

    private static boolean ours(LivingEntity e) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals(BeyondSolo.MOD_ID);
    }

    private static boolean holds(ServerPlayer p, ItemStack wanted) {
        for (ItemStack s : p.getInventory().items) if (ItemStack.isSameItem(s, wanted)) return true;
        return false;
    }

    // ================================================================ the table

    /** The castle's hundred floors: kinds, wings, ranks, gatekeepers, the lord, and the spire above. */
    @GameTest(template = ARENA)
    public static void floorTable(GameTestHelper helper) {
        int keepers = 0;
        PlayerRank last = PlayerRank.E;
        for (int f = 1; f <= TowerFloors.TOP; f++) {
            TowerFloors.Kind k = TowerFloors.kind(f);
            TowerFloors.Kind want = f == TowerFloors.TOP ? TowerFloors.Kind.LORD : f % 10 == 0 ? TowerFloors.Kind.KEEPER
                    : f % 10 == 5 ? TowerFloors.Kind.ELITE : f % 10 == 7 ? TowerFloors.Kind.SIEGE : TowerFloors.Kind.BATTLE;
            helper.assertTrue(k == want, "floor " + f + " is a " + want + " floor, not " + k);
            if (k == TowerFloors.Kind.KEEPER) keepers++;
            helper.assertTrue(TowerFloors.rank(f).ordinal() >= last.ordinal(), "the ranks only climb (floor " + f + ")");
            last = TowerFloors.rank(f);
            helper.assertTrue(TowerFloors.wing(f).ordinal() == (f - 1) / 10, "floor " + f + " is in wing " + (f - 1) / 10);
            helper.assertTrue(!TowerFloors.endless(f) && TowerFloors.healthScale(f) == 1f, "the castle is not scaled (floor " + f + ")");
        }
        helper.assertTrue(keepers == 9, "nine gatekeepers below the lord: " + keepers);
        helper.assertTrue(TowerFloors.rank(1) == PlayerRank.E && TowerFloors.rank(TowerFloors.TOP) == PlayerRank.S, "E at the gate, S at the top");
        helper.assertTrue(TowerFloors.keeper(10) == BossKind.ALPHA && TowerFloors.keeper(TowerFloors.TOP) == BossKind.KNIGHT, "the castle's keepers are set");
        helper.assertTrue(!TowerFloors.keeperAscended(50) && TowerFloors.keeperAscended(60), "the upper keepers are ascended");
        helper.assertTrue(TowerFloors.checkpointOf(37) == 30 && TowerFloors.checkpointOf(40) == 40, "checkpoints are the keepers' floors");
        helper.assertTrue(CitadelHalls.shapeFor(1) == CitadelHalls.Shape.NAVE && CitadelHalls.shapeFor(30) == CitadelHalls.Shape.ROTUNDA
                && CitadelHalls.shapeFor(TowerFloors.TOP) == CitadelHalls.Shape.THRONE, "a nave, a round keeper's hall, the throne hall");
        helper.assertTrue(TowerFloors.xp(10) > TowerFloors.xp(9) && TowerFloors.xp(TowerFloors.TOP) > TowerFloors.xp(90), "keepers and the lord pay more");

        // the spire: endless, every floor a little stronger, the wings round again, a keeper every ten
        helper.assertTrue(TowerFloors.SPIRE == TowerFloors.TOP + 1 && TowerFloors.endless(TowerFloors.SPIRE), "the spire starts above the throne");
        helper.assertTrue(TowerFloors.healthScale(101) > 1f && TowerFloors.healthScale(150) > TowerFloors.healthScale(120)
                && TowerFloors.damageScale(150) > TowerFloors.damageScale(120), "the spire keeps getting stronger");
        helper.assertTrue(TowerFloors.wing(101) == TowerFloors.Wing.OUTER_GATE && TowerFloors.wing(111) == TowerFloors.Wing.BARRACKS
                && TowerFloors.wing(201) == TowerFloors.Wing.OUTER_GATE, "the wings come round again");
        helper.assertTrue(TowerFloors.kind(110) == TowerFloors.Kind.KEEPER && TowerFloors.keeperAscended(110), "spire keepers, all ascended");
        helper.assertTrue(TowerFloors.keeper(130) == TowerFloors.keeper(130), "a spire floor's keeper is the same every climb");

        // where a climb starts
        TowerData d = new TowerData();
        helper.assertTrue(d.startFloor() == 1, "a new climb starts at the gate");
        d.checkpoint = 40;
        helper.assertTrue(d.startFloor() == 41, "above the last keeper beaten");
        d.checkpoint = TowerFloors.TOP;
        helper.assertTrue(d.startFloor() == 91, "after the lord: the last ten floors again");

        // every name a floor can show is written
        JsonObject en = lang("en_us");
        JsonObject es = lang("es_es");
        helper.assertTrue(en != null && es != null, "the Citadel's language files are there");
        List<String> keys = new ArrayList<>();
        for (int f = 10; f < TowerFloors.TOP; f += 10) keys.add("tower.beyondsolo.keeper." + f);
        for (TowerFloors.Wing w : TowerFloors.Wing.values()) keys.add("tower.beyondsolo.wing." + w.id());
        for (TowerFloors.Kind k : TowerFloors.Kind.values()) keys.add("tower.beyondsolo.kind." + k.id());
        keys.addAll(List.of("tower.beyondsolo.lord.name", "tower.beyondsolo.lord.title", "tower.beyondsolo.keeper.title", "tower.beyondsolo.spire",
                "tower.beyondsolo.rush.sovereign", "tower.beyondsolo.rush.tier", TowerRun.CONQUEROR_TITLE, TowerRun.SOVEREIGN_TITLE));
        for (String r : List.of("asleep", "no_key", "inside", "busy", "not_conquered", "sigil", "rank", "closed")) keys.add(REFUSE + r);
        for (String k : keys) helper.assertTrue(en.has(k) && es.has(k), "\"" + k + "\" is written in English and Spanish");
        helper.succeed();
    }

    private static JsonObject lang(String locale) {
        try (InputStream in = BeyondSolo.class.getResourceAsStream("/assets/beyondsolo_tower/lang/" + locale + ".json")) {
            if (in == null) return null;
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(r).getAsJsonObject();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Every rush brings the whole roster once, the last one ascended; the Sovereign rush all ascended, then the lord. */
    @GameTest(template = ARENA)
    public static void rushTiers(GameTestHelper helper) {
        RandomSource r = RandomSource.create(42);
        int kinds = BossKind.values().length;
        for (RushTier t : RushTier.values()) {
            for (int run = 0; run < 4; run++) {
                List<RushTier.Bout> order = t.order(r);
                helper.assertTrue(order.size() == t.bouts(), t.id() + ": " + t.bouts() + " bouts, not " + order.size());
                Set<BossKind> seen = EnumSet.noneOf(BossKind.class);
                for (int i = 0; i < kinds; i++) {
                    RushTier.Bout b = order.get(i);
                    helper.assertTrue(!b.lord() && seen.add(b.kind()), t.id() + ": every boss once, the lord only at the end");
                    boolean ascended = t.sovereign() || i == kinds - 1;
                    helper.assertTrue(b.ascended() == ascended, t.id() + ": bout " + (i + 1) + (ascended ? " is" : " is not") + " ascended");
                }
                if (t.sovereign()) helper.assertTrue(order.get(kinds).lord(), "the Sovereign rush ends with the lord");
            }
        }
        helper.assertTrue(RushTier.E.openTo(PlayerRank.E, false) && RushTier.D.openTo(PlayerRank.E, false), "one rank up is open");
        helper.assertTrue(!RushTier.C.openTo(PlayerRank.E, false), "two ranks up is not");
        helper.assertTrue(RushTier.S.openTo(PlayerRank.A, false) && RushTier.S.openTo(PlayerRank.S, false), "S is open to A and S hunters");
        helper.assertTrue(!RushTier.SOVEREIGN.openTo(PlayerRank.S, false) && RushTier.SOVEREIGN.openTo(PlayerRank.E, true), "the Sigil opens the Sovereign rush");
        helper.assertTrue(RushTier.byId("sovereign") == RushTier.SOVEREIGN && RushTier.byId("nope") == null, "tiers by name");

        TowerData d = new TowerData();
        helper.assertTrue(d.rushBest[RushTier.C.ordinal()] == -1, "no time yet");
        helper.assertTrue(d.rushCleared(RushTier.C, 3000), "the first clear is the best");
        helper.assertTrue(!d.rushCleared(RushTier.C, 3600), "a slower one is not");
        helper.assertTrue(d.rushCleared(RushTier.C, 2400) && d.rushBest[RushTier.C.ordinal()] == 2400, "a faster one is");
        helper.assertTrue(d.rushClears[RushTier.C.ordinal()] == 3, "every clear counted");
        helper.succeed();
    }

    /** The records survive a save, and a reset clears them. */
    @GameTest(template = ARENA)
    public static void recordsSaveAndLoad(GameTestHelper helper) {
        var provider = helper.getLevel().registryAccess();
        TowerData d = new TowerData();
        d.bound = true;
        d.checkpoint = 60;
        d.highest = 64;
        d.lordKills = 2;
        d.sigil = true;
        d.sigilDry = 1;
        d.spireBest = 133;
        d.spireRuns = 5;
        d.floorsCleared = 210;
        d.falls = 7;
        d.told = true;
        d.rushBest[RushTier.SOVEREIGN.ordinal()] = 12345;
        d.rushClears[RushTier.SOVEREIGN.ordinal()] = 2;
        CompoundTag tag = d.serializeNBT(provider);
        TowerData e = new TowerData();
        e.deserializeNBT(provider, tag);
        helper.assertTrue(e.bound && e.checkpoint == 60 && e.highest == 64 && e.lordKills == 2 && e.sigil && e.sigilDry == 1, "the castle's records");
        helper.assertTrue(e.spireBest == 133 && e.spireRuns == 5 && e.floorsCleared == 210 && e.falls == 7 && e.told, "the spire's and the counts");
        helper.assertTrue(e.rushBest[RushTier.SOVEREIGN.ordinal()] == 12345 && e.rushClears[RushTier.SOVEREIGN.ordinal()] == 2
                && e.rushBest[RushTier.E.ordinal()] == -1, "the rush records");
        helper.assertTrue(e.conquered() && e.startFloor() == 61, "what they mean");
        e.reset();
        helper.assertTrue(!e.bound && e.checkpoint == 0 && !e.sigil && e.spireBest == 0 && e.rushBest[RushTier.SOVEREIGN.ordinal()] == -1, "reset");
        helper.succeed();
    }

    // ================================================================ the key and the door

    /** The key is sold from D rank; used, it binds to an awakened hunter for good. The door says why it won't open. */
    @GameTest(template = ARENA)
    public static void keyOpensTheCitadel(GameTestHelper helper) {
        ServerPlayer civilian = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "civilian"));
        SystemAPI.reset(civilian);
        TowerRun.data(civilian).reset();
        helper.assertTrue((REFUSE + "asleep").equals(TowerRun.refusal(civilian, TowerRun.Mode.CASTLE, null)), "no System, no Citadel");
        helper.assertTrue(!TowerAPI.bind(civilian), "the key stays cold without the System");
        helper.assertTrue(!TowerRun.enter(civilian, TowerRun.Mode.CASTLE, null), "not let in");

        ShopCatalog.Entry entry = ShopCatalog.get("citadel_key");
        helper.assertTrue(entry != null && entry.minRank() == TowerAPI.KEY_RANK && entry.display().is(TowerModule.CITADEL_KEY.get()),
                "the System shop sells the key from D rank");

        ServerPlayer p = player(helper, PlayerRank.D, "keyholder");
        TowerData d = TowerRun.data(p);
        d.reset();
        helper.assertTrue((REFUSE + "no_key").equals(TowerRun.refusal(p, TowerRun.Mode.CASTLE, null)), "no key, no door");
        ItemStack key = new ItemStack(TowerModule.CITADEL_KEY.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, key);
        key.use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
        helper.assertTrue(d.bound && TowerRun.data(p).bound, "the key is bound");
        helper.assertTrue(key.isEmpty(), "and used up");
        helper.assertTrue(!TowerAPI.bind(p), "bound only once");

        helper.assertTrue(TowerRun.refusal(p, TowerRun.Mode.CASTLE, null) == null, "the climb is open");
        helper.assertTrue((REFUSE + "not_conquered").equals(TowerRun.refusal(p, TowerRun.Mode.ENDLESS, null)), "the spire waits for the lord's fall");
        helper.assertTrue(TowerRun.refusal(p, TowerRun.Mode.RUSH, RushTier.C) == null, "a rush one rank up is open");
        helper.assertTrue((REFUSE + "rank").equals(TowerRun.refusal(p, TowerRun.Mode.RUSH, RushTier.B)), "two ranks up is not");
        helper.assertTrue((REFUSE + "sigil").equals(TowerRun.refusal(p, TowerRun.Mode.RUSH, RushTier.SOVEREIGN)), "the Sovereign rush wants the Sigil");
        p.setGameMode(GameType.SPECTATOR);
        helper.assertTrue((REFUSE + "busy").equals(TowerRun.refusal(p, TowerRun.Mode.CASTLE, null)), "not while the System can't take them");
        p.setGameMode(GameType.SURVIVAL);
        helper.assertTrue(!TowerAPI.conquered(p) && !TowerAPI.hasSigil(p) && TowerAPI.spireBest(p) == 0 && !TowerAPI.busy(p), "nothing done yet");
        helper.succeed();
    }

    // ================================================================ runs

    /** The pull, the first hall and its creatures (the System's own), the seal off the stair, up to the next hall, and out by the exit sigil. */
    @GameTest(template = ARENA, timeoutTicks = 700)
    public static void climbAndLeave(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.D, "climber");
        TowerData d = keyed(p);
        Vec3 home = p.position();
        helper.assertTrue(TowerRun.enter(p, TowerRun.Mode.CASTLE, null), "goes in");
        helper.assertTrue(!TowerRun.begin(p, TowerRun.Mode.CASTLE, 1, null), "one run at a time");
        helper.assertTrue((REFUSE + "inside").equals(TowerRun.refusal(p, TowerRun.Mode.CASTLE, null)), "already inside");
        helper.assertTrue(TowerRun.phase(p) == TowerRun.Phase.PULL && TowerRun.floor(p) == 1 && TowerAPI.busy(p), "pulled in at the first floor");
        List<ChunkPos> held = hold(p);
        ServerLevel level = TowerRun.citadel(p.server);
        int[] stage = {0};
        int[] waited = {0};
        each(helper, p, held, () -> {
            TowerRun.Phase phase = TowerRun.phase(p);
            CitadelHalls.Layout hall = TowerRun.hall(p);
            switch (stage[0]) {
                case 0 -> {
                    helper.assertTrue(TowerRun.jump(p, "arrive"), "the pull can be skipped");
                    CitadelHalls.Layout first = TowerRun.hall(p);
                    helper.assertTrue(first != null && first.shape() == CitadelHalls.Shape.NAVE, "a nave for an ordinary floor");
                    helper.assertTrue(d.inside(), "marked as inside");
                    helper.assertTrue(p.position().distanceTo(first.start()) < 1.5, "standing at the hall's south end");
                    helper.assertTrue(!level.getBlockState(BlockPos.containing(first.start()).below()).isAir(), "on a floor");
                    helper.assertTrue(CitadelHalls.doorSealed(level, first), "the stair is sealed");
                    stage[0] = 1;
                }
                case 1 -> {
                    List<LivingEntity> foes = TowerRun.foes(p);
                    if (phase == TowerRun.Phase.FIGHT && !foes.isEmpty() && ++waited[0] > 12) {
                        helper.assertTrue(foes.stream().allMatch(e -> e.getTags().contains(TowerRun.FOE_TAG)), "the creatures belong to the run");
                        helper.assertTrue(foes.stream().allMatch(TowerGameTests::ours), "no vanilla creatures in the Citadel");
                        helper.assertTrue(foes.stream().allMatch(e -> hall.bounds().inflate(2).contains(e.position())), "they rose in the hall");
                        helper.assertTrue(!turnedAway(foes.get(0)), "a live run's creature loads again with its chunk");
                        helper.assertTrue(TowerRun.jump(p, "clear"), "the floor can be won");
                        helper.assertTrue(TowerRun.phase(p) == TowerRun.Phase.CLEARED, "won");
                        helper.assertTrue(TowerRun.foes(p).isEmpty(), "what was left of them is gone");
                        helper.assertTrue(!CitadelHalls.doorSealed(level, hall), "the seal is off the stair");
                        helper.assertTrue(d.highest == 1 && d.floorsCleared == 1 && d.checkpoint == 0, "floor 1 counted, no checkpoint");
                        helper.assertTrue(TowerRun.jump(p, "climb"), "up the stair");
                        stage[0] = 2;
                    } else if (++waited[0] > 300) {
                        helper.fail("no creatures rose on the first floor (phase " + phase + ")");
                    }
                }
                case 2 -> {
                    if (phase == TowerRun.Phase.ARRIVING && TowerRun.floor(p) == 2) {
                        helper.assertTrue(hall != null && hall.origin().getZ() == CitadelHalls.origin(0, 1).getZ(), "the next hall stood on the other pad");
                        helper.assertTrue(p.position().distanceTo(hall.start()) < 1.5, "the stair led straight into it");
                        helper.assertTrue(CitadelHalls.doorSealed(level, hall), "its stair sealed again");
                        helper.assertTrue(TowerRun.jump(p, "clear"), "floor 2 won");
                        helper.assertTrue(d.highest == 2 && d.floorsCleared == 2, "floor 2 counted");
                        Vec3 sigil = Vec3.atBottomCenterOf(hall.exit());
                        p.moveTo(sigil.x, sigil.y, sigil.z);
                        stage[0] = 3;
                    }
                }
                case 3 -> {
                    if (phase == TowerRun.Phase.LEAVING) stage[0] = 4;
                }
                case 4 -> {
                    if (phase == null) {
                        helper.assertTrue(p.position().distanceTo(home) < 2, "back where they were taken from");
                        helper.assertTrue(!d.inside() && !TowerRun.inRun(p), "out of the Citadel");
                        helper.assertTrue(d.startFloor() == 1, "no keeper beaten: the next climb starts at the gate again");
                        end(p, held);
                        helper.succeed();
                    }
                }
                default -> {}
            }
        });
    }

    /** A run's creature saved with the world when it stopped mid-run (a crash, a quit) is turned away when its chunk loads. */
    @GameTest(template = ARENA)
    public static void staleCreaturesAreTurnedAway(GameTestHelper helper) {
        net.minecraft.world.entity.npc.Villager stale = net.minecraft.world.entity.EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(stale != null, "a creature to load");
        helper.assertTrue(!turnedAway(stale), "an untagged creature loads as ever");
        stale.addTag(TowerRun.FOE_TAG);
        helper.assertTrue(turnedAway(stale), "one of an old run's creatures is turned away as it loads");
        net.neoforged.neoforge.event.entity.EntityJoinLevelEvent fresh = new net.neoforged.neoforge.event.entity.EntityJoinLevelEvent(stale, helper.getLevel(), false);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(fresh);
        helper.assertTrue(!fresh.isCanceled(), "a run's creature spawned now is let in");
        helper.succeed();
    }

    /** True when a creature coming back with its chunk would be turned away. */
    private static boolean turnedAway(net.minecraft.world.entity.Entity e) {
        net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event = new net.neoforged.neoforge.event.entity.EntityJoinLevelEvent(e, e.level(), true);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        return event.isCanceled();
    }

    /** A gatekeeper: a named gate boss, dormant until woken; beaten, a reward cache and a checkpoint. */
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void gatekeeperIsACheckpoint(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.C, "keeper_hunter");
        TowerData d = keyed(p);
        helper.assertTrue(TowerRun.begin(p, TowerRun.Mode.CASTLE, 10, null), "goes in at the tenth floor");
        List<ChunkPos> held = hold(p);
        ServerLevel level = TowerRun.citadel(p.server);
        TowerRun.jump(p, "arrive");
        int[] stage = {0};
        int[] waited = {0};
        each(helper, p, held, () -> {
            TowerRun.Phase phase = TowerRun.phase(p);
            switch (stage[0]) {
                case 0 -> {
                    LivingEntity boss = TowerRun.currentBoss(p);
                    if (phase == TowerRun.Phase.FIGHT && boss != null) {
                        helper.assertTrue(TowerRun.hall(p).shape() == CitadelHalls.Shape.ROTUNDA, "a round hall for a keeper");
                        helper.assertTrue(boss instanceof GateBossEntity, "the keeper is a gate boss");
                        GateBossEntity g = (GateBossEntity) boss;
                        helper.assertTrue(g.kind() == TowerFloors.keeper(10) && g.rank() == TowerFloors.rank(10), "the tenth floor's keeper, at its rank");
                        helper.assertTrue(!g.isAwake() && boss.getTags().contains(TowerRun.FOE_TAG), "dormant, and the run's");
                        helper.assertTrue(!boss.hurt(level.damageSources().generic(), 5f), "no harm before it wakes");
                        helper.assertTrue(TowerRun.jump(p, "wake") && g.isAwake(), "woken");
                        helper.assertTrue(TowerRun.jump(p, "kill"), "beaten");
                        stage[0] = 1;
                    } else if (++waited[0] > 200) {
                        helper.fail("no gatekeeper (phase " + phase + ")");
                    }
                }
                case 1 -> {
                    if (phase == TowerRun.Phase.CLEARED) {
                        helper.assertTrue(d.checkpoint == 10 && d.startFloor() == 11, "a checkpoint: the next climb starts on floor 11");
                        helper.assertTrue(d.highest == 10 && d.floorsCleared == 1, "counted");
                        BlockPos at = BlockPos.containing(TowerRun.hall(p).bossSpot()).offset(0, 0, 3);
                        boolean cache = false;
                        for (int i = 0; i < 4; i++) cache |= level.getBlockEntity(at.above(i)) instanceof RewardCacheBlockEntity;
                        helper.assertTrue(cache, "its reward cache stands where it fell");
                        end(p, held);
                        helper.assertTrue(!TowerRun.inRun(p) && !d.inside(), "home");
                        helper.succeed();
                    }
                }
                default -> {}
            }
        });
    }

    /** The lord on the hundredth floor: dormant, untouchable through his waking, beaten; the Citadel conquered and (here by pity) the Sigil. */
    @GameTest(template = ARENA, timeoutTicks = 700)
    public static void lordFallsAndTheSigilComes(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.S, "lord_hunter");
        TowerData d = keyed(p);
        d.checkpoint = 90;
        d.highest = 99;
        d.sigilDry = TowerRun.SIGIL_PITY - 1;
        helper.assertTrue(d.startFloor() == 91, "climbing from the last keeper");
        helper.assertTrue(TowerRun.begin(p, TowerRun.Mode.CASTLE, TowerFloors.TOP, null), "goes in at the throne");
        List<ChunkPos> held = hold(p);
        ServerLevel level = TowerRun.citadel(p.server);
        TowerRun.jump(p, "arrive");
        int[] stage = {0};
        int[] waited = {0};
        each(helper, p, held, () -> {
            TowerRun.Phase phase = TowerRun.phase(p);
            switch (stage[0]) {
                case 0 -> {
                    LivingEntity boss = TowerRun.currentBoss(p);
                    if (phase == TowerRun.Phase.FIGHT && boss != null) {
                        helper.assertTrue(TowerRun.hall(p).shape() == CitadelHalls.Shape.THRONE, "the throne hall");
                        helper.assertTrue(boss instanceof CitadelLordEntity, "the lord waits on the throne");
                        CitadelLordEntity lord = (CitadelLordEntity) boss;
                        helper.assertTrue(!lord.isAwake() && !lord.hurt(level.damageSources().generic(), 5f), "dormant and untouched");
                        helper.assertTrue(TowerRun.jump(p, "wake") && lord.isAwake() && lord.introTicks() > 0, "he rises");
                        helper.assertTrue(!lord.hurt(level.damageSources().generic(), 5f), "untouchable while he rises");
                        helper.assertTrue(TowerRun.jump(p, "kill"), "beaten");
                        stage[0] = 1;
                    } else if (++waited[0] > 200) {
                        helper.fail("no lord (phase " + phase + ")");
                    }
                }
                case 1 -> {
                    if (phase == TowerRun.Phase.VICTORY) {
                        helper.assertTrue(d.lordKills == 1 && d.conquered() && TowerAPI.conquered(p), "the Citadel is conquered");
                        helper.assertTrue(d.checkpoint == TowerFloors.TOP && d.highest == TowerFloors.TOP, "the top reached");
                        helper.assertTrue(d.sigil && d.sigilDry == 0 && TowerAPI.hasSigil(p), "the Sigil of Dominion (the fourth kill without it never misses)");
                        helper.assertTrue(holds(p, new ItemStack(TowerModule.SIGIL_OF_DOMINION.get())), "in their pack");
                        helper.assertTrue(TowerRun.CONQUEROR_TITLE.equals(SystemAPI.get(p).title()), "and a title");
                        end(p, held);
                        helper.assertTrue(TowerRun.refusal(p, TowerRun.Mode.ENDLESS, null) == null, "the spire is open now");
                        helper.assertTrue(TowerRun.refusal(p, TowerRun.Mode.RUSH, RushTier.SOVEREIGN) == null, "and the Sovereign rush");
                        helper.assertTrue(d.startFloor() == 91, "the throne can be challenged again from floor 91");
                        helper.succeed();
                    }
                }
                default -> {}
            }
        });
    }

    /** The Endless Spire keeps the best floor; a blow that would kill ends the run instead (home, weakened, the record kept). */
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void spireRecordAndFall(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.S, "spire_hunter");
        TowerData d = keyed(p);
        d.lordKills = 1;
        d.checkpoint = TowerFloors.TOP;
        d.highest = TowerFloors.TOP;
        Vec3 home = p.position();
        helper.assertTrue(TowerRun.refusal(p, TowerRun.Mode.ENDLESS, null) == null, "the spire is open to a conqueror");
        helper.assertTrue(TowerRun.enter(p, TowerRun.Mode.ENDLESS, null), "goes in");
        helper.assertTrue(TowerRun.floor(p) == TowerFloors.SPIRE && TowerRun.mode(p) == TowerRun.Mode.ENDLESS && d.spireRuns == 1, "the spire's first floor");
        List<ChunkPos> held = hold(p);
        int[] stage = {0};
        each(helper, p, held, () -> {
            TowerRun.Phase phase = TowerRun.phase(p);
            switch (stage[0]) {
                case 0 -> {
                    helper.assertTrue(TowerRun.jump(p, "clear"), "floor 101 won");
                    helper.assertTrue(d.spireBest == 101 && d.highest == TowerFloors.TOP, "a spire record (the castle's own stays)");
                    helper.assertTrue(TowerRun.jump(p, "climb"), "up");
                    stage[0] = 1;
                }
                case 1 -> {
                    if (phase == TowerRun.Phase.ARRIVING && TowerRun.floor(p) == 102) {
                        helper.assertTrue(TowerRun.jump(p, "clear") && d.spireBest == 102, "102");
                        helper.assertTrue(TowerRun.jump(p, "climb"), "up again");
                        stage[0] = 2;
                    }
                }
                case 2 -> {
                    if (phase == TowerRun.Phase.ARRIVING && TowerRun.floor(p) == 103) {
                        TowerRun.jump(p, "arrive");
                        stage[0] = 3;
                    }
                }
                case 3 -> {
                    if (phase == TowerRun.Phase.FIGHT) {
                        // test players can't be hurt: post the death the way a fatal blow would
                        LivingDeathEvent death = NeoForge.EVENT_BUS.post(new LivingDeathEvent(p, p.damageSources().genericKill()));
                        helper.assertTrue(death.isCanceled() && p.isAlive(), "no death in the Citadel");
                        helper.assertTrue(TowerRun.phase(p) == TowerRun.Phase.FALLEN, "the run ends as a fall");
                        stage[0] = 4;
                    }
                }
                case 4 -> {
                    if (phase == null) {
                        helper.assertTrue(p.position().distanceTo(home) < 2, "thrown home");
                        helper.assertTrue(p.hasEffect(MobEffects.WEAKNESS), "weakened");
                        helper.assertTrue(d.falls == 1 && !d.inside(), "a fall counted");
                        helper.assertTrue(d.spireBest == 102 && TowerAPI.spireBest(p) == 102, "the record kept");
                        end(p, held);
                        helper.succeed();
                    }
                }
                default -> {}
            }
        });
    }

    /** The boss rush: the whole roster one after another in the colosseum at the tier's rank, a breather between, the clock kept. */
    @GameTest(template = ARENA, timeoutTicks = 900)
    public static void bossRushBackToBack(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E, "rusher");
        TowerData d = keyed(p);
        Vec3 home = p.position();
        helper.assertTrue(TowerRun.enter(p, TowerRun.Mode.RUSH, RushTier.E), "goes in");
        List<RushTier.Bout> bouts = TowerRun.bouts(p);
        helper.assertTrue(bouts.size() == BossKind.values().length, "the whole roster");
        List<ChunkPos> held = hold(p);
        int[] stage = {0};
        int[] won = {0};
        int[] waited = {0};
        each(helper, p, held, () -> {
            TowerRun.Phase phase = TowerRun.phase(p);
            switch (stage[0]) {
                case 0 -> {
                    TowerRun.jump(p, "arrive");
                    helper.assertTrue(TowerRun.hall(p).shape() == CitadelHalls.Shape.COLOSSEUM, "in the colosseum");
                    stage[0] = 1;
                }
                case 1 -> {
                    if (phase == TowerRun.Phase.FIGHT) {
                        LivingEntity boss = TowerRun.currentBoss(p);
                        if (boss == null) {
                            if (++waited[0] > 100) helper.fail("no boss in bout " + (TowerRun.bout(p) + 1));
                            return;
                        }
                        waited[0] = 0;
                        int bout = TowerRun.bout(p);
                        helper.assertTrue(bout == won[0], "bouts in order");
                        helper.assertTrue(boss instanceof GateBossEntity g && g.kind() == bouts.get(bout).kind() && g.rank() == PlayerRank.E,
                                "bout " + (bout + 1) + " is " + bouts.get(bout).kind() + " at E rank");
                        // the first bout: wait for the boss to wake, which starts the clock
                        if (bout == 0 && TowerRun.clock(p) < 10) return;
                        helper.assertTrue(TowerRun.jump(p, "clear"), "bout " + (bout + 1) + " won");
                        won[0]++;
                        TowerRun.Phase next = TowerRun.phase(p);
                        helper.assertTrue(next == (won[0] < bouts.size() ? TowerRun.Phase.BREATHER : TowerRun.Phase.RUSH_DONE), "a breather between bouts: " + next);
                    } else if (phase == TowerRun.Phase.BREATHER) {
                        helper.assertTrue(TowerRun.jump(p, "breather"), "the breather can be cut short");
                    } else if (phase == TowerRun.Phase.RUSH_DONE) {
                        helper.assertTrue(won[0] == bouts.size(), "every bout won");
                        helper.assertTrue(d.rushClears[RushTier.E.ordinal()] == 1 && d.rushBest[RushTier.E.ordinal()] >= 10, "a clear and a time kept");
                        helper.assertTrue(d.floorsCleared == 0 && d.highest == 0, "a rush is not a climb");
                        helper.assertTrue(TowerRun.jump(p, "leave"), "out");
                        stage[0] = 2;
                    }
                }
                case 2 -> {
                    if (phase == null) {
                        helper.assertTrue(p.position().distanceTo(home) < 2 && !d.inside(), "home");
                        end(p, held);
                        helper.succeed();
                    }
                }
                default -> {}
            }
        });
    }

    // ================================================================ the lord's moves

    private static CitadelLordEntity lord(GameTestHelper helper, BlockPos at) {
        CitadelLordEntity lord = helper.spawn(TowerModule.CITADEL_LORD.get(), at);
        lord.setNoAi(true);
        return lord;
    }

    /** Something for him to swing at (not a player, so nothing else here goes after it; not a monster, so he does). */
    private static Villager dummy(GameTestHelper helper, BlockPos at) {
        Villager v = helper.spawn(EntityType.VILLAGER, at);
        v.setNoAi(true);
        v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        v.setHealth(1000);
        return v;
    }

    /**
     * Whether a blow landed; then the dummy is made whole, still and open to the next blow again (a NoAI mob keeps a
     * knockback's push forever, which would lead his decree astray; and a blow within half a second of a harder one is
     * shrugged off, as the lance's first spear was right after the leap).
     */
    private static boolean hurtAndHealed(LivingEntity e) {
        boolean hurt = e.getHealth() < e.getMaxHealth();
        e.setHealth(e.getMaxHealth());
        e.setDeltaMovement(Vec3.ZERO);
        e.invulnerableTime = 0;
        return hurt;
    }

    /** Each of his moves lands; he can't be hurt until he has risen, and can be after; below half he is enraged. */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void lordMoves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // on the arena's floor (y 2 here; the slab roof clears his crown), the dummy off to one side of the crystal
        BlockPos stand = new BlockPos(3, 2, 1);
        Vec3 standAt = Vec3.atBottomCenterOf(helper.absolutePos(stand));
        CitadelLordEntity lord = lord(helper, stand);
        Villager dummy = dummy(helper, new BlockPos(5, 2, 4));
        helper.assertTrue(!lord.isAwake() && !lord.hurt(level.damageSources().generic(), 10f), "dormant: no harm");
        lord.wake();
        helper.assertTrue(lord.introTicks() > 0 && !lord.hurt(level.damageSources().generic(), 10f), "rising: no harm");

        TowerTestAccess.forceMove(lord, CitadelLordEntity.Move.CLEAVE, dummy);
        helper.assertTrue(lord.move() == CitadelLordEntity.Move.CLEAVE, "the cleave starts");
        TowerTestAccess.finishMove(lord);
        helper.assertTrue(lord.move() == null, "and ends");
        helper.assertTrue(hurtAndHealed(dummy), "the cleave lands in front of him");
        float before = lord.getHealth();
        helper.assertTrue(lord.hurt(level.damageSources().generic(), 10f) && lord.getHealth() < before, "risen, he can be hurt");

        lord.setTarget(dummy);
        TowerTestAccess.forceMove(lord, CitadelLordEntity.Move.DECREE, dummy);
        TowerTestAccess.finishMove(lord);
        helper.assertTrue(TowerTestAccess.pendingStrikes(lord) > 0, "the decree marks the ground under what he fights");
        helper.assertTrue(!hurtAndHealed(dummy), "the marks have not struck yet");
        int[] stage = {0};
        int[] waited = {0};
        helper.onEachTick(() -> {
            switch (stage[0]) {
                case 0 -> {
                    if (++waited[0] < CitadelLordEntity.MARK_TICKS + 4) return;
                    helper.assertTrue(TowerTestAccess.pendingStrikes(lord) == 0, "the marks have struck");
                    helper.assertTrue(hurtAndHealed(dummy), "the decree's lightning hit");

                    lord.moveTo(standAt.x, standAt.y, standAt.z);
                    TowerTestAccess.forceMove(lord, CitadelLordEntity.Move.LEAP, dummy);
                    TowerTestAccess.finishMove(lord);
                    helper.assertTrue(lord.position().distanceTo(standAt) > 2, "he leapt: " + lord.position().distanceTo(standAt));
                    helper.assertTrue(lord.position().distanceTo(dummy.position()) < 1.5, "onto what he fights");
                    helper.assertTrue(hurtAndHealed(dummy), "and came down on it");

                    TowerTestAccess.forceMove(lord, CitadelLordEntity.Move.MOON, dummy);
                    TowerTestAccess.finishMove(lord);
                    helper.assertTrue(lord.bloodMoon(), "the blood moon rises");
                    lord.setHealth(lord.getMaxHealth() * 0.45f);
                    stage[0] = 1;
                }
                case 1 -> {
                    if (!lord.isEnraged()) return;
                    // the lance last: its far spears are let go with him, they would reach past this test's floor
                    lord.moveTo(standAt.x, standAt.y, standAt.z);
                    TowerTestAccess.forceMove(lord, CitadelLordEntity.Move.LANCE, dummy);
                    TowerTestAccess.finishMove(lord);
                    helper.assertTrue(hurtAndHealed(dummy), "the first spear of the lance");
                    helper.assertTrue(TowerTestAccess.pendingStrikes(lord) > 0, "the rest still coming");
                    lord.discard();
                    dummy.discard();
                    helper.succeed();
                }
                default -> {}
            }
        });
    }
}
