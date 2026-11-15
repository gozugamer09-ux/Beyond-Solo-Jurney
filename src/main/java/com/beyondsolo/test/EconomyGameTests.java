package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.economy.EconomyHooks;
import com.beyondsolo.economy.EconomyModule;
import com.beyondsolo.economy.HunterData;
import com.beyondsolo.economy.HunterLicenseItem;
import com.beyondsolo.economy.LicenseInfo;
import com.beyondsolo.economy.SellPrices;
import com.beyondsolo.economy.ShopCatalog;
import com.beyondsolo.economy.ShopService;
import com.beyondsolo.economy.StorageMenu;
import com.beyondsolo.economy.SystemStorage;
import com.beyondsolo.item.arsenal.Arsenal;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Gold, storage, shop and license tests. Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EconomyGameTests {
    private static final String ARENA = "evaluation_zone";

    /** An evaluated hunter of this rank with an empty wallet, storage and inventory. */
    private static ServerPlayer hunter(GameTestHelper helper, PlayerRank rank) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "hunter"));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        Economy.reset(p);
        p.getInventory().clearContent();
        p.setGameMode(GameType.SURVIVAL);
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        QuestTestAccess.markStarted(p);
        SystemAPI.awaken(p, rank, PlayerClass.WARRIOR);
        return p;
    }

    private static int count(ServerPlayer p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    @GameTest(template = ARENA)
    public static void monstersDropGoldOnlyForTheSystem(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerRank.E);
        Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(z, p.damageSources().playerAttack(p)));
        z.discard();
        helper.assertTrue(Economy.gold(p) >= 3, "a zombie pays a few gold, got " + Economy.gold(p));

        ServerPlayer plain = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "plain"));
        SystemAPI.reset(plain);
        Economy.reset(plain);
        Zombie z2 = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(z2, plain.damageSources().playerAttack(plain)));
        z2.discard();
        helper.assertTrue(Economy.gold(plain) == 0, "no System, no gold");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void buyingTakesGoldAndGivesTheItem(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerRank.E);
        Economy.set(p, 1000);
        ShopCatalog.Entry beef = ShopCatalog.get("cooked_beef");
        long price = ShopService.price(p, beef);
        helper.assertTrue(ShopService.buy(p, "cooked_beef", 2) == ShopService.Result.OK, "bought");
        helper.assertTrue(Economy.gold(p) == 1000 - 2 * price, "two lots paid for, gold " + Economy.gold(p));
        helper.assertTrue(count(p, Items.COOKED_BEEF) == 16, "two lots of 8 beef");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void rankAndGoldGateTheShop(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerRank.E);
        Economy.set(p, 100_000);
        String rare = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Arsenal.KNIGHTSBANE.get()).getPath();
        helper.assertTrue(ShopService.buy(p, rare, 1) == ShopService.Result.RANK, "an E-rank can't buy rare weapons");
        helper.assertTrue(Economy.gold(p) == 100_000, "a refusal costs nothing");
        ServerPlayer poor = hunter(helper, PlayerRank.D);
        Economy.set(poor, 10);
        helper.assertTrue(ShopService.buy(poor, rare, 1) == ShopService.Result.GOLD, "not enough gold");
        helper.assertTrue(Economy.gold(poor) == 10, "gold untouched");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void dailyLimitsHold(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerRank.C);
        Economy.set(p, 100_000);
        helper.assertTrue(ShopService.buy(p, "skill_tome", 1) == ShopService.Result.OK, "first tome of the day");
        helper.assertTrue(ShopService.buy(p, "skill_tome", 1) == ShopService.Result.LIMIT, "one tome a day");
        helper.assertTrue(count(p, com.beyondsolo.registry.ModItems.SKILL_TOME.get()) == 1, "one tome given");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void storageGrowsWithRankAndExpansions(GameTestHelper helper) {
        ServerPlayer e = hunter(helper, PlayerRank.E);
        helper.assertTrue(SystemStorage.capacity(e) == 27, "E-rank storage is 27");
        ServerPlayer s = hunter(helper, PlayerRank.S);
        helper.assertTrue(SystemStorage.capacity(s) == 72, "S-rank storage is 72");

        Economy.set(e, 100_000);
        ShopCatalog.Entry exp = ShopCatalog.get("storage_expansion");
        long first = ShopService.price(e, exp);
        helper.assertTrue(ShopService.buy(e, "storage_expansion", 1) == ShopService.Result.OK, "expansion bought");
        helper.assertTrue(SystemStorage.capacity(e) == 36, "+9 slots");
        helper.assertTrue(ShopService.price(e, exp) == first * 2, "the next one costs double");
        for (int i = 0; i < 3; i++) ShopService.buy(e, "storage_expansion", 1);
        helper.assertTrue(ShopService.buy(e, "storage_expansion", 1) == ShopService.Result.MAXED, "four at most");
        helper.assertTrue(SystemStorage.capacity(e) == 27 + 36, "27 + 4 x 9");
        helper.assertTrue(SystemStorage.capacity(5, 99) == HunterData.MAX_SLOTS, "never past the maximum");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void storageMenuLocksAndPages(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerRank.E);
        HunterData data = Economy.data(p);
        StorageMenu small = new StorageMenu(1, p.getInventory(), data, 27);
        helper.assertTrue(small.getSlot(26).mayPlace(new ItemStack(Items.STONE)), "slot 27 is usable");
        helper.assertFalse(small.getSlot(27).mayPlace(new ItemStack(Items.STONE)), "slot 28 is locked at 27 slots");
        helper.assertTrue(small.pages() == 1, "one page");
        small.clickMenuButton(p, StorageMenu.NEXT);
        helper.assertTrue(small.page() == 0, "no second page to go to");

        StorageMenu big = new StorageMenu(2, p.getInventory(), data, 72);
        helper.assertTrue(big.pages() == 2, "two pages at 72 slots");
        big.clickMenuButton(p, StorageMenu.NEXT);
        helper.assertTrue(big.page() == 1, "second page");
        big.getSlot(3).set(new ItemStack(Items.DIAMOND, 5));
        helper.assertTrue(data.storage().get(StorageMenu.PAGE + 3).is(Items.DIAMOND), "page 2 slot 4 is storage slot 58");
        helper.assertTrue(big.getSlot(17).mayPlace(new ItemStack(Items.STONE)), "slot 72 usable");
        helper.assertFalse(big.getSlot(18).mayPlace(new ItemStack(Items.STONE)), "slot 73 locked");
        // shift-click from the inventory goes into storage
        p.getInventory().setItem(10, new ItemStack(Items.IRON_INGOT, 7));
        big.quickMoveStack(p, StorageMenu.PAGE + 1);
        helper.assertTrue(p.getInventory().getItem(10).isEmpty(), "moved out of the inventory");
        boolean found = false;
        for (ItemStack s : data.storage()) if (s.is(Items.IRON_INGOT) && s.getCount() == 7) found = true;
        helper.assertTrue(found, "into storage");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void depositAndSort(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerRank.E);
        HunterData data = Economy.data(p);
        data.storage().set(5, new ItemStack(Items.COBBLESTONE, 10));
        data.storage().set(9, new ItemStack(Items.COBBLESTONE, 20));
        data.storage().set(2, new ItemStack(Items.BONE, 3));
        p.getInventory().setItem(12, new ItemStack(Items.COBBLESTONE, 40));
        p.getInventory().setItem(3, new ItemStack(Items.COBBLESTONE, 4)); // hotbar stays
        StorageMenu menu = new StorageMenu(1, p.getInventory(), data, 27);
        menu.clickMenuButton(p, StorageMenu.DEPOSIT);
        helper.assertTrue(p.getInventory().getItem(12).isEmpty(), "matching inventory stack stacked in");
        helper.assertTrue(p.getInventory().getItem(3).getCount() == 4, "the hotbar is left alone");
        menu.clickMenuButton(p, StorageMenu.SORT);
        int stacks = 0, total = 0;
        for (int i = 0; i < 27; i++) {
            ItemStack s = data.storage().get(i);
            if (s.is(Items.COBBLESTONE)) {
                stacks++;
                total += s.getCount();
            }
        }
        helper.assertTrue(total == 70 && stacks == 2, "70 cobblestone merged into 2 stacks, got " + stacks + " / " + total);
        helper.assertFalse(data.storage().get(26).is(Items.COBBLESTONE), "sorted to the front");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void storageAndGoldAreSaved(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerRank.B);
        Economy.set(p, 4321);
        Economy.data(p).storage().set(60, new ItemStack(Items.EMERALD, 9));
        EconomyHooks.gateCleared(p, PlayerRank.C);
        CompoundTag tag = Economy.data(p).serializeNBT(helper.getLevel().registryAccess());
        HunterData copy = new HunterData();
        copy.deserializeNBT(helper.getLevel().registryAccess(), tag);
        helper.assertTrue(copy.gold() == 4321 + EconomyHooks.GATE_GOLD[PlayerRank.C.tier()], "gold saved");
        helper.assertTrue(copy.storage().get(60).is(Items.EMERALD) && copy.storage().get(60).getCount() == 9, "storage saved");
        helper.assertTrue(copy.clears(PlayerRank.C) == 1 && copy.bestClear() == PlayerRank.C, "clears saved");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void sellingPaysByGrade(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerRank.E);
        p.getInventory().setItem(9, new ItemStack(Arsenal.KNIGHTSBANE.get()));
        helper.assertTrue(ShopService.sell(p, 9, false) == ShopService.Result.OK, "sold");
        helper.assertTrue(Economy.gold(p) == SellPrices.GRADE[1], "a rare weapon pays " + SellPrices.GRADE[1] + ", got " + Economy.gold(p));
        p.getInventory().setItem(9, new ItemStack(Items.DIRT, 64));
        helper.assertTrue(ShopService.sell(p, 9, false) == ShopService.Result.NOTHING, "dirt is worth nothing");
        p.getInventory().setItem(1, new ItemStack(Items.BONE, 10));
        p.getInventory().setItem(20, new ItemStack(Items.BONE, 5));
        long before = Economy.gold(p);
        ShopService.sell(p, 1, true);
        helper.assertTrue(Economy.gold(p) == before + 15, "sell all of a kind");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void buyingToSellNeverPays(GameTestHelper helper) {
        for (ShopCatalog.Entry e : ShopCatalog.entries().values()) {
            if (e.special() != ShopCatalog.Special.NONE) continue;
            long buy = Math.round(e.price() * ShopCatalog.DEAL);
            long sell = SellPrices.of(e.display());
            helper.assertTrue(sell < buy, e.id() + " sells for " + sell + " but costs " + buy + " on sale");
        }
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void licenseIsIssuedAndKeptUpToDate(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerRank.C);
        EconomyHooks.issueLicense(p);
        helper.assertTrue(Economy.data(p).licenseIssued(), "issued");
        ItemStack card = ItemStack.EMPTY;
        for (ItemStack s : p.getInventory().items) if (s.is(EconomyModule.HUNTER_LICENSE.get())) card = s;
        helper.assertFalse(card.isEmpty(), "the card is in the inventory");
        LicenseInfo info = card.get(EconomyModule.LICENSE.get());
        helper.assertTrue(info != null && info.playerRank() == PlayerRank.C && info.clears() == 0, "C-rank, no clears");
        helper.assertTrue(info.owner().equals(p.getUUID()), "bound to its hunter");

        EconomyHooks.gateCleared(p, PlayerRank.B);
        SystemAPI.setRank(p, PlayerRank.B);
        HunterLicenseItem.refresh(p, card);
        LicenseInfo now = card.get(EconomyModule.LICENSE.get());
        helper.assertTrue(now.playerRank() == PlayerRank.B && now.clears() == 1 && now.bestRank() == PlayerRank.B, "rank and clears follow");
        helper.assertTrue(now.number().equals(info.number()), "same license number");

        // someone else's card is not rewritten
        ServerPlayer other = hunter(helper, PlayerRank.E);
        HunterLicenseItem.refresh(other, card);
        helper.assertTrue(card.get(EconomyModule.LICENSE.get()).owner().equals(p.getUUID()), "a card keeps its owner");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void gateClearsPayByRank(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerRank.D);
        EconomyHooks.gateCleared(p, PlayerRank.E);
        EconomyHooks.gateCleared(p, PlayerRank.A);
        helper.assertTrue(Economy.gold(p) == EconomyHooks.GATE_GOLD[0] + EconomyHooks.GATE_GOLD[4], "E + A gate gold");
        helper.assertTrue(Economy.data(p).totalClears() == 2, "two clears");
        helper.succeed();
    }
}
