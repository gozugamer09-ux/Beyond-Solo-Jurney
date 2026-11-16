package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.crafting.CraftingModule;
import com.beyondsolo.crafting.CraftingService;
import com.beyondsolo.crafting.SystemRecipes;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.economy.EconomyHooks;
import com.beyondsolo.economy.SellPrices;
import com.beyondsolo.economy.ShopCatalog;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.gate.GateWiper;
import com.beyondsolo.key.DungeonKeys;
import com.beyondsolo.key.KeyDrops;
import com.beyondsolo.key.KeyRunData;
import com.beyondsolo.key.KeySeal;
import com.beyondsolo.key.KeyedRuns;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** Dungeon key tests: turning a key, keyed runs, chains, refunds, records, drops and the cave wiper. */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KeyGameTests {
    private static final String ARENA = "evaluation_zone";

    /** An awakened hunter at the arena's north edge, facing south (into the arena). The arena's floor is at y 1. */
    private static ServerPlayer hunter(GameTestHelper helper, PlayerRank rank) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "key_hunter"));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        p.getInventory().clearContent();
        DungeonKeys.data(p).reset();
        p.setGameMode(GameType.SURVIVAL);
        BlockPos start = helper.absolutePos(new BlockPos(3, 2, 0));
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0f, 0f);
        SystemAPI.awaken(p, rank, PlayerClass.WARRIOR);
        QuestTestAccess.markStarted(p);
        return p;
    }

    /** Empties the arena down to its floor (walls, crystal, roof), so a gate (five tall, three wide) fits in it. */
    private static void clearArena(GameTestHelper helper) {
        for (int x = 0; x < 7; x++) for (int y = 2; y < 7; y++) for (int z = 0; z < 7; z++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
    }

    /** Three steps ahead of the hunter, facing back at them (where a key turned from the start spot opens its gate). */
    private static DungeonKeys.Spot ahead(GameTestHelper helper) {
        return new DungeonKeys.Spot(helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 3)), Direction.NORTH);
    }

    private static @Nullable GateInstance keyedOf(MinecraftServer server, ServerPlayer p) {
        GateInstance found = null;
        for (GateInstance g : GateData.get(server).all()) {
            if (g.isKeyed() && g.keyed().owner.equals(p.getUUID()) && (found == null || g.keyed().openedAt >= found.keyed().openedAt)) found = g;
        }
        return found;
    }

    private static BossKind bossAt(PlayerRank rank, @Nullable BossKind not) {
        for (BossKind k : BossKind.values()) if (k.livesAt(rank) && k != not) return k;
        throw new IllegalStateException("no boss lives at " + rank);
    }

    private static int keys(ServerPlayer p, PlayerRank rank, KeySeal seal) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) {
            if (DungeonKeys.isKey(s) && DungeonKeys.rankOf(s) == rank && DungeonKeys.sealOf(s).equals(seal)) n += s.getCount();
        }
        return n;
    }

    // ------------------------------------------------------------------ turning a key

    @GameTest(template = ARENA)
    public static void turningAKeyOpensAKeyedGateAhead(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerRank.C);
        clearArena(helper);
        MinecraftServer server = helper.getLevel().getServer();
        p.setItemInHand(InteractionHand.MAIN_HAND, DungeonKeys.key(PlayerRank.C));
        ItemStack held = p.getMainHandItem();
        Vec3 turnedAt = p.position();
        helper.assertTrue(DungeonKeys.begin(p, held), "the key starts to turn");
        helper.assertTrue(keyedOf(server, p) == null, "nothing opens until it has turned");
        helper.assertTrue(DungeonKeys.finish(p, held), "it turns");
        held.consume(1, p);
        GateInstance g = keyedOf(server, p);
        helper.assertTrue(g != null && g.rank() == PlayerRank.C, "a C-rank keyed gate opened");
        helper.assertTrue(g.gatePos().equals(helper.absolutePos(new BlockPos(3, 2, 3))), "three steps ahead: " + g.gatePos());
        helper.assertTrue(helper.getLevel().getBlockEntity(g.gatePos()) instanceof GateBlockEntity door && door.isKeyed()
                && door.facing() == Direction.NORTH && g.id().equals(door.gateId()), "its door stands there, facing its holder");
        helper.assertTrue(g.keyed().chain == 1 && g.keyed().returnPos.distanceTo(turnedAt) < 0.01
                && g.keyed().returnDim.equals(helper.getLevel().dimension()), "its way out leads back to where the key turned");
        helper.assertTrue(!g.keyed().entered() && !g.keyed().doorClosed && g.state() == GateInstance.State.OPEN, "open, waiting");
        helper.assertTrue(p.getMainHandItem().isEmpty(), "the key is used up");
        helper.assertTrue(DungeonKeys.data(p).turned[PlayerRank.C.tier()] == 1, "and counted");
        helper.assertTrue(DungeonKeys.refusal(p, DungeonKeys.key(PlayerRank.C)) == DungeonKeys.Refusal.WAITING,
                "a second key waits until the first gate is used");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void keysRefuseWhereTheyShould(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerRank.E);
        helper.assertTrue(DungeonKeys.refusal(p, DungeonKeys.key(PlayerRank.B)) == DungeonKeys.Refusal.RANK, "a B key is too heavy for an E-rank");
        helper.assertTrue(DungeonKeys.refusal(p, DungeonKeys.key(PlayerRank.D)) == null, "one rank above is fine");
        ServerPlayer sleeper = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "sleeper"));
        SystemAPI.reset(sleeper);
        helper.assertTrue(DungeonKeys.refusal(sleeper, DungeonKeys.key(PlayerRank.E)) == DungeonKeys.Refusal.ASLEEP, "only the awakened turn keys");
        // the arena as it stands has no room for a gate (its crystal, its walls, its roof)
        helper.assertTrue(DungeonKeys.findSpot(p) == null, "no room in the arena");
        helper.assertTrue(!DungeonKeys.begin(p, DungeonKeys.key(PlayerRank.E)), "so the key won't turn");
        clearArena(helper);
        DungeonKeys.Spot spot = DungeonKeys.findSpot(p);
        helper.assertTrue(spot != null && spot.pos().equals(helper.absolutePos(new BlockPos(3, 2, 3))) && spot.facing() == Direction.NORTH,
                "with the arena cleared, three steps ahead: " + (spot == null ? null : spot.pos()));
        helper.assertTrue(DungeonKeys.begin(p, DungeonKeys.key(PlayerRank.E)), "now it turns");
        helper.assertTrue(!DungeonKeys.begin(p, DungeonKeys.key(PlayerRank.E)), "one key at a time");
        DungeonKeys.cancel(p);
        helper.assertTrue(keyedOf(helper.getLevel().getServer(), p) == null, "let go early, nothing opens");
        helper.assertTrue(DungeonKeys.begin(p, DungeonKeys.key(PlayerRank.E)), "and it can be turned again");
        DungeonKeys.cancel(p);
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void turningFromTheMenuTakesTheSameTime(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerRank.E);
        clearArena(helper);
        MinecraftServer server = helper.getLevel().getServer();
        p.getInventory().setItem(5, DungeonKeys.key(PlayerRank.E).copyWithCount(2));
        DungeonKeys.turnFromInventory(p, 5);
        helper.assertTrue(DungeonKeys.turning(p), "turning");
        helper.assertTrue(keyedOf(server, p) == null, "the lock only forms at first");
        helper.runAfterDelay(DungeonKeys.USE_TICKS + 3, () -> {
            helper.assertTrue(!DungeonKeys.turning(p), "done turning");
            helper.assertTrue(keyedOf(server, p) != null, "the gate opened");
            helper.assertTrue(p.getInventory().getItem(5).getCount() == 1, "one key used");
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void walkingOffStopsAMenuTurn(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerRank.E);
        clearArena(helper);
        MinecraftServer server = helper.getLevel().getServer();
        p.getInventory().setItem(2, DungeonKeys.key(PlayerRank.E));
        DungeonKeys.turnFromInventory(p, 2);
        p.moveTo(p.getX() + 6, p.getY(), p.getZ());
        helper.runAfterDelay(DungeonKeys.USE_TICKS + 3, () -> {
            helper.assertTrue(keyedOf(server, p) == null, "no gate");
            helper.assertTrue(p.getInventory().getItem(2).getCount() == 1, "and the key is kept");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ keyed runs

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void clearedCavesChainTheNextRun(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerRank.D);
        clearArena(helper);
        MinecraftServer server = helper.getLevel().getServer();
        Vec3 home = p.position();
        GateBlockEntity door = DungeonKeys.open(p, DungeonKeys.key(PlayerRank.D), ahead(helper));
        GateInstance first = GateData.get(server).get(door.gateId());
        helper.assertTrue(GateService.arrive(p, first), "through the door");
        helper.assertTrue(DungeonKeys.host(p) == first, "standing in its cave");
        helper.assertTrue(first.keyed().inside.contains(p.getUUID()) && first.keyed().clockStarted(), "the run's clock started");
        Quest quest = QuestEngine.data(p).active().stream().filter(q -> GateService.QUEST_TEMPLATE.equals(q.template())
                && first.id().toString().equals(q.extra().getString("gate"))).findFirst().orElse(null);
        helper.assertTrue(quest != null && quest.rewards().statPoints() == 0 && quest.rewards().xp() > 0,
                "a keyed run's quest pays XP but no stat points");
        helper.assertTrue(DungeonKeys.refusal(p, DungeonKeys.key(PlayerRank.D)) == DungeonKeys.Refusal.UNCLEARED, "not while its boss stands");

        // the boss falls: the next key opens right here, as the second run of a chain
        first.setState(GateInstance.State.CLEARED);
        helper.assertTrue(DungeonKeys.refusal(p, DungeonKeys.key(PlayerRank.D)) == null, "a cleared cave can open the next");
        DungeonKeys.Spot spot = DungeonKeys.findSpot(p);
        if (spot == null) spot = new DungeonKeys.Spot(p.serverLevel(), p.blockPosition().relative(p.getDirection(), 3), p.getDirection().getOpposite());
        GateBlockEntity door2 = DungeonKeys.open(p, DungeonKeys.key(PlayerRank.D), spot);
        GateInstance second = GateData.get(server).get(door2.gateId());
        helper.assertTrue(second.keyed().chain == 2, "the second run of a chain");
        helper.assertTrue(second.keyed().returnPos.distanceTo(home) < 0.01 && second.keyed().returnDim.equals(helper.getLevel().dimension()),
                "a chain still goes home to where it began");
        helper.assertTrue(Math.abs(KeyedRuns.bonus(second) - 1.1f) < 1e-4 && Math.abs(KeyedRuns.bonus(first) - 1f) < 1e-4, "+10% for the second run");
        helper.assertTrue(Math.abs(DungeonKeys.bonusFor(40) - (1 + DungeonKeys.CHAIN_BONUS * DungeonKeys.CHAIN_MAX)) < 1e-4, "the bonus stops growing");

        // the second run's way out leads home
        GateService.leave(p, second);
        helper.assertTrue(p.level() == helper.getLevel() && p.position().distanceToSqr(home) < 4, "home, from the end of a chain: " + p.position());
        helper.assertTrue(!second.keyed().inside.contains(p.getUUID()), "out of the run");
        first.keyed().inside.clear();
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void doorsSealBehindTheParty(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerRank.E);
        clearArena(helper);
        MinecraftServer server = helper.getLevel().getServer();
        GateBlockEntity door = DungeonKeys.open(p, DungeonKeys.key(PlayerRank.E), ahead(helper));
        GateInstance g = GateData.get(server).get(door.gateId());
        long now = server.overworld().getGameTime();
        g.keyed().firstEntryAt = now - DungeonKeys.SEAL_TICKS + 60;
        g.keyed().inside.add(p.getUUID());
        KeyedRuns.tick(server, 20);
        helper.assertTrue(!g.keyed().doorClosed && !door.isClosing(), "open a while, for the party to follow");
        g.keyed().firstEntryAt = now - DungeonKeys.SEAL_TICKS;
        KeyedRuns.tick(server, 20);
        helper.assertTrue(g.keyed().doorClosed && door.isClosing(), "then the door shuts behind them");
        helper.assertTrue(g.state() == GateInstance.State.OPEN, "the run goes on inside");
        g.keyed().inside.clear();
        KeyedRuns.tick(server, 20);
        helper.assertTrue(g.state() == GateInstance.State.CLOSED, "with nobody left inside, the run is over");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void anUnusedGateGivesItsKeyBack(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerRank.C);
        clearArena(helper);
        MinecraftServer server = helper.getLevel().getServer();
        BossKind boss = bossAt(PlayerRank.C, null);
        GateBlockEntity door = DungeonKeys.open(p, DungeonKeys.sealed(PlayerRank.C, boss, false), ahead(helper));
        GateInstance g = GateData.get(server).get(door.gateId());
        helper.assertTrue(g.bossKind() == boss, "a sealed key's gate ends with its boss");
        // a hunter who is not online does not wait out their gate (fake players never are)
        KeyedRuns.tick(server, DungeonKeys.UNUSED_TICKS);
        helper.assertTrue(g.keyed().waited == 0 && g.state() == GateInstance.State.OPEN, "the wait only counts while its holder is here");
        KeyedRuns.giveUp(server, g, p);
        helper.assertTrue(g.state() == GateInstance.State.CLOSED && g.keyed().refunded && g.keyed().doorClosed, "given up");
        helper.assertTrue(door.isClosing(), "the door closes");
        helper.assertTrue(keys(p, PlayerRank.C, KeySeal.of(boss, false)) == 1, "the key came back, seal and all");
        helper.assertTrue(DungeonKeys.waiting(p) == null, "and the next key can turn");
        // an ascendant key's boss always comes crowned
        GateBlockEntity crowned = DungeonKeys.open(p, DungeonKeys.sealed(PlayerRank.C, boss, true),
                new DungeonKeys.Spot(helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 6)), Direction.NORTH));
        GateInstance c = GateData.get(server).get(crowned.gateId());
        helper.assertTrue(c.bossKind() == boss && c.bossAscended(), "an ascendant key's boss comes ascended");
        KeyedRuns.giveUp(server, c, p);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void recordsKeepTheBestTimes(GameTestHelper helper) {
        KeyRunData d = new KeyRunData();
        helper.assertTrue(d.clear(PlayerRank.D, 2400, 1), "the first clear is a best");
        helper.assertTrue(!d.clear(PlayerRank.D, 3000, 2), "a slower one is not");
        helper.assertTrue(d.clear(PlayerRank.D, 1800, 3), "a faster one is");
        int t = PlayerRank.D.tier();
        helper.assertTrue(d.best[t] == 1800 && d.cleared[t] == 3 && d.longestChain == 3 && d.lastTicks == 1800 && d.lastBest, "kept");
        CompoundTag tag = d.serializeNBT(helper.getLevel().registryAccess());
        KeyRunData e = new KeyRunData();
        e.deserializeNBT(helper.getLevel().registryAccess(), tag);
        helper.assertTrue(e.best[t] == 1800 && e.cleared[t] == 3 && e.longestChain == 3 && e.best[0] < 0, "saved and loaded");

        // a cleared keyed run: the clock runs from the first arrival to the boss's fall
        ServerPlayer p = hunter(helper, PlayerRank.D);
        clearArena(helper);
        MinecraftServer server = helper.getLevel().getServer();
        GateBlockEntity door = DungeonKeys.open(p, DungeonKeys.key(PlayerRank.D), ahead(helper));
        GateInstance g = GateData.get(server).get(door.gateId());
        g.keyed().runStartedAt = server.overworld().getGameTime() - 1234;
        KeyedRuns.cleared(helper.getLevel(), g, List.of(p));
        helper.assertTrue(g.keyed().clearTicks == 1234, "timed: " + g.keyed().clearTicks);
        helper.assertTrue(DungeonKeys.data(p).best[t] == 1234 && DungeonKeys.data(p).cleared[t] == 1, "into the hunter's records");

        // the chain bonus on the gold
        long before = Economy.gold(p);
        EconomyHooks.gateCleared(p, PlayerRank.D, 1f);
        long base = Economy.gold(p) - before;
        before = Economy.gold(p);
        EconomyHooks.gateCleared(p, PlayerRank.D, 1.2f);
        helper.assertTrue(Economy.gold(p) - before == Math.round(base * 1.2), "a chained run pays more gold");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void theRerunPromptPicksTheSameSealFirst(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerRank.C);
        clearArena(helper);
        BossKind boss = bossAt(PlayerRank.C, null);
        BossKind other = bossAt(PlayerRank.C, boss);
        GateBlockEntity door = DungeonKeys.open(p, DungeonKeys.sealed(PlayerRank.C, boss, false), ahead(helper));
        GateInstance host = GateData.get(helper.getLevel().getServer()).get(door.gateId());
        var inv = p.getInventory();
        inv.setItem(1, DungeonKeys.key(PlayerRank.B));
        inv.setItem(3, DungeonKeys.key(PlayerRank.C));
        inv.setItem(5, DungeonKeys.sealed(PlayerRank.C, other, false));
        inv.setItem(7, DungeonKeys.sealed(PlayerRank.C, boss, false));
        helper.assertTrue(DungeonKeys.rerunSlot(p, host) == 7, "the same boss again first");
        inv.setItem(7, ItemStack.EMPTY);
        helper.assertTrue(DungeonKeys.rerunSlot(p, host) == 3, "then a plain key");
        inv.setItem(3, ItemStack.EMPTY);
        helper.assertTrue(DungeonKeys.rerunSlot(p, host) == 5, "then any of the rank");
        inv.setItem(5, ItemStack.EMPTY);
        helper.assertTrue(DungeonKeys.rerunSlot(p, host) == -1, "never another rank's");
        helper.succeed();
    }

    // ------------------------------------------------------------------ where keys come from

    @GameTest(template = ARENA)
    public static void bossesDropKeys(GameTestHelper helper) {
        RandomSource r = RandomSource.create(42);
        BossKind boss = bossAt(PlayerRank.B, null);
        int plain = 0, sealed = 0, ascendant = 0;
        for (int i = 0; i < 2000; i++) {
            for (ItemStack s : KeyDrops.bossKeys(boss, PlayerRank.B, false, r)) {
                helper.assertTrue(DungeonKeys.rankOf(s) == PlayerRank.B, "keys of the boss's rank");
                switch (DungeonKeys.style(s)) {
                    case 0 -> plain++;
                    case 1 -> {
                        sealed++;
                        helper.assertTrue(DungeonKeys.sealOf(s).boss() == boss, "sealed with that boss");
                    }
                    default -> ascendant++;
                }
            }
        }
        helper.assertTrue(plain > 450 && plain < 750, "a plain key about 30% of the time: " + plain);
        helper.assertTrue(sealed > 100 && sealed < 230 && ascendant == 0, "a sealed one about 8%: " + sealed + " / " + ascendant);
        int crowned = 0, sealedAscended = 0;
        for (int i = 0; i < 2000; i++) {
            for (ItemStack s : KeyDrops.bossKeys(boss, PlayerRank.B, true, r)) {
                if (DungeonKeys.style(s) == 2) crowned++;
                if (DungeonKeys.style(s) >= 1) sealedAscended++;
            }
        }
        helper.assertTrue(sealedAscended > 650 && sealedAscended < 950 && crowned > 250 && crowned < 550,
                "an ascended boss seals far more often, half of them ascendant: " + sealedAscended + " / " + crowned);
        // a gate creature that is not one: no keys at all
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
        int wild = 0;
        for (int i = 0; i < 500; i++) wild += KeyDrops.creatureKeys(zombie, r).size();
        helper.assertTrue(wild == 0, "wild monsters carry no keys");
        zombie.discard();
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void keysAreSoldForgedAndBought(GameTestHelper helper) {
        // the shop sells E to C keys, a few a day
        for (String id : List.of("dungeon_key_e", "dungeon_key_d", "dungeon_key_c")) {
            ShopCatalog.Entry e = ShopCatalog.get(id);
            helper.assertTrue(e != null && DungeonKeys.isKey(e.display()) && e.dailyLimit() > 0, "the shop sells " + id);
        }
        helper.assertTrue(ShopCatalog.get("dungeon_key_b") == null, "but no higher");
        // and buys them back, sealed and ascendant ones for more
        long plain = SellPrices.each(DungeonKeys.key(PlayerRank.C));
        helper.assertTrue(plain == DungeonKeys.SELL[PlayerRank.C.tier()], "a C key sells for " + plain);
        helper.assertTrue(SellPrices.each(DungeonKeys.sealed(PlayerRank.C, bossAt(PlayerRank.C, null), false)) == Math.round(plain * 1.5), "sealed");
        helper.assertTrue(SellPrices.each(DungeonKeys.sealed(PlayerRank.C, null, true)) == plain * 2, "ascendant");
        // every rank can be forged around a boss core
        for (PlayerRank rank : PlayerRank.values()) {
            SystemRecipes.Recipe recipe = SystemRecipes.get("forge_key_" + rank.name().toLowerCase());
            helper.assertTrue(recipe != null && recipe.category() == SystemRecipes.Category.KEYS && recipe.minRank() == rank
                    && DungeonKeys.rankOf(recipe.display()) == rank, "a forge recipe for " + rank);
        }
        ServerPlayer p = hunter(helper, PlayerRank.D);
        Economy.set(p, 1000);
        int t = PlayerRank.D.tier();
        p.getInventory().add(new ItemStack(CraftingModule.BOSS_CORES.get(t).get()));
        p.getInventory().add(new ItemStack(CraftingModule.CORES.get(t).get(), 4));
        p.getInventory().add(new ItemStack(CraftingModule.MANA_STONE.get(), 1));
        p.getInventory().add(new ItemStack(Items.GOLD_INGOT, 2));
        helper.assertTrue(CraftingService.craft(p, "forge_key_s", 1) == 0, "an S key is beyond a D-rank");
        helper.assertTrue(CraftingService.craft(p, "forge_key_d", 1) == 1, "a D key forged");
        helper.assertTrue(keys(p, PlayerRank.D, KeySeal.of(null, false)) == 1, "in the bag");
        helper.assertTrue(Economy.gold(p) == 1000 - com.beyondsolo.key.KeyRecipes.GOLD[t], "for its gold");
        helper.assertTrue(!p.getInventory().contains(new ItemStack(CraftingModule.BOSS_CORES.get(t).get())), "the boss core went into it");
        helper.succeed();
    }

    // ------------------------------------------------------------------ the wiper

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void closedCavesAreWipedAndTheirSlotsReused(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GateBlockEntity gate = GateService.spawnGate(helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 3)), PlayerRank.E, Direction.NORTH, false);
        GateData data = GateData.get(server);
        GateInstance g = data.get(gate.gateId());
        ServerLevel d = GateService.dungeon(server);
        GateService.buildNow(server, g);
        BlockPos e = g.entrance();
        helper.assertTrue(d.getBlockState(e).isAir(), "carved");
        Mob mob = GateService.spawnMonster(d, g, EntityType.ZOMBIE, e);
        helper.assertTrue(mob != null && g.id().equals(mob.getPersistentData().getUUID(GateWiper.GATE_ID_TAG)), "its monsters know their gate");
        int slot = g.slot();
        helper.assertTrue(!GateWiper.wipeNow(server, g), "an open cave is left alone");
        g.setState(GateInstance.State.CLOSED);
        helper.assertTrue(GateWiper.wipeNow(server, g), "a closed one is wiped");
        helper.assertTrue(!d.getBlockState(e).isAir() && !d.getBlockState(g.bossRoomCenter()).isAir(), "rock again where the cave was");
        helper.assertTrue(mob.isRemoved(), "nothing left lying in it");
        helper.assertTrue(data.get(g.id()) == null && g.slotFreed(), "its record is forgotten");
        helper.assertTrue(data.freeSlots().contains(slot), "its slot is free again");
        int next = data.freeSlots().first();
        GateInstance n = data.create(UUID.randomUUID(), PlayerRank.D, 7L, helper.getLevel().dimension(), helper.absolutePos(new BlockPos(3, 2, 3)));
        helper.assertTrue(n.slot() == next && !data.freeSlots().contains(next), "the next gate takes a freed slot");
        // (another test's cave may have been wiped meanwhile and freed a lower slot, which is taken first)
        if (n.slot() == slot) helper.assertTrue(data.bySlotPos(e) == n, "and the cave space is its now");
        data.remove(n.id());
        data.freeSlot(n.slot());
        helper.succeed();
    }
}
