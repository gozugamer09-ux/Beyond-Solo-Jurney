package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.crafting.CraftDrops;
import com.beyondsolo.crafting.CraftingModule;
import com.beyondsolo.crafting.CraftingService;
import com.beyondsolo.crafting.MonsterCoreItem;
import com.beyondsolo.crafting.SystemRecipes;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.economy.SellPrices;
import com.beyondsolo.enemy.Affinity;
import com.beyondsolo.enemy.Affix;
import com.beyondsolo.enemy.EnemyModule;
import com.beyondsolo.enemy.EnemyTraits;
import com.beyondsolo.enemy.Enemies;
import com.beyondsolo.enemy.MonsterKind;
import com.beyondsolo.enemy.Variant;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.GateLoot;
import com.beyondsolo.gear.GearModule;
import com.beyondsolo.gear.GearRolls;
import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.item.arsenal.Arsenal;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.registry.ModItems;
import com.beyondsolo.skill.SkillElement;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Monster cores, materials, mana stones, the System recipes and potions. */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CraftingGameTests {
    private static final String ARENA = "evaluation_zone";
    private static final BlockPos SPOT = new BlockPos(1, 2, 1);

    private static ServerPlayer hunter(GameTestHelper helper, PlayerRank rank) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "crafter"));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        Economy.reset(p);
        p.getInventory().clearContent();
        p.setGameMode(GameType.SURVIVAL);
        BlockPos start = helper.absolutePos(SPOT);
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        QuestTestAccess.markStarted(p);
        SystemAPI.awaken(p, rank, PlayerClass.WARRIOR);
        return p;
    }

    private static Mob creature(GameTestHelper helper, MonsterKind kind, PlayerRank rank, Variant v, Affinity a) {
        Mob mob = Enemies.spawn(helper.getLevel(), rank, EnemyModule.type(kind), helper.absolutePos(new BlockPos(3, 2, 3)),
                new EnemyTraits(a, v, v.special() ? Affix.mask(java.util.EnumSet.of(Affix.SWIFT)) : 0), -1);
        mob.setNoAi(true);
        return mob;
    }

    private static int count(ServerPlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    private static ItemStack core(PlayerRank r, Affinity a, int n) {
        return MonsterCoreItem.of(r, false, a, n);
    }

    @GameTest(template = ARENA)
    public static void elitesAlwaysLeaveACoreWithTheirAffinity(GameTestHelper helper) {
        Mob mob = creature(helper, MonsterKind.GLOOMFANG, PlayerRank.C, Variant.ELITE, Affinity.FROST);
        RandomSource r = RandomSource.create(3);
        for (int i = 0; i < 20; i++) {
            List<ItemStack> drops = CraftDrops.creatureDrops(mob, r);
            ItemStack core = drops.stream().filter(s -> s.getItem() instanceof MonsterCoreItem).findFirst().orElse(ItemStack.EMPTY);
            helper.assertTrue(!core.isEmpty(), "an elite always leaves a core");
            helper.assertTrue(((MonsterCoreItem) core.getItem()).rank() == PlayerRank.C, "the core has the creature's rank");
            helper.assertTrue(MonsterCoreItem.affinity(core) == Affinity.FROST, "the core keeps the affinity");
            helper.assertTrue(drops.stream().anyMatch(s -> s.is(CraftingModule.GLOOMFANG_FANG.get())), "an elite gloomfang always leaves a fang");
        }
        mob.discard();
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void ordinaryCreaturesSometimesLeaveACore(GameTestHelper helper) {
        Mob mob = creature(helper, MonsterKind.BROODLING, PlayerRank.E, Variant.NORMAL, Affinity.NONE);
        RandomSource r = RandomSource.create(11);
        int cores = 0;
        for (int i = 0; i < 400; i++) {
            for (ItemStack s : CraftDrops.creatureDrops(mob, r)) if (s.getItem() instanceof MonsterCoreItem) cores++;
        }
        helper.assertTrue(cores > 80 && cores < 160, "about 30% leave a core, got " + cores + "/400");
        mob.discard();
        // a plain zombie that is not a gate creature: only rarely an E-rank core
        Zombie z = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
        int wild = 0;
        for (int i = 0; i < 1000; i++) wild += CraftDrops.creatureDrops(z, r).size();
        helper.assertTrue(wild > 10 && wild < 80, "wild monsters drop a core about 4% of the time, got " + wild);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void bossLootGainsABossCore(GameTestHelper helper) {
        List<ItemStack> loot = new ArrayList<>();
        NeoForge.EVENT_BUS.post(new GateLoot.BossLootEvent(helper.getLevel(), BossKind.BRUTE, PlayerRank.B, true, loot));
        helper.assertTrue(loot.stream().anyMatch(s -> s.is(CraftingModule.BOSS_CORES.get(PlayerRank.B.tier()).get()) && s.getCount() == 2),
                "an ascended B boss leaves two B boss cores");
        helper.assertTrue(loot.stream().anyMatch(s -> s.is(CraftingModule.CORES.get(PlayerRank.B.tier()).get())), "and B cores");
        helper.assertTrue(loot.stream().anyMatch(s -> s.is(CraftingModule.MANA_STONE.get())), "and mana stones of its size");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void craftingTakesFromInventoryThenStorage(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerRank.E);
        Economy.set(p, 100);
        p.getInventory().add(new ItemStack(Items.GLASS_BOTTLE, 2));
        p.getInventory().add(core(PlayerRank.E, Affinity.NONE, 1));
        Economy.data(p).storage().set(3, core(PlayerRank.E, Affinity.NONE, 1));
        int made = CraftingService.craft(p, "healing_draught", 2);
        helper.assertTrue(made == 2, "made twice");
        helper.assertTrue(count(p, CraftingModule.HEALING_DRAUGHT.get()) == 2, "two draughts");
        helper.assertTrue(count(p, CraftingModule.CORES.get(0).get()) == 0 && Economy.data(p).storage().get(3).isEmpty(), "both cores used");
        helper.assertTrue(Economy.gold(p) == 80, "10 gold each");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void craftingRefusesRankAndMissingThings(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerRank.E);
        Economy.set(p, 1000);
        p.getInventory().add(new ItemStack(Items.GLASS_BOTTLE, 4));
        p.getInventory().add(core(PlayerRank.C, Affinity.NONE, 2));
        p.getInventory().add(new ItemStack(CraftingModule.HOLLOW_ESSENCE.get(), 2));
        helper.assertTrue(CraftingService.craft(p, "greater_healing_draught", 1) == 0, "an E-rank can't brew a C-rank potion");
        SystemAPI.setRank(p, PlayerRank.C);
        helper.assertTrue(CraftingService.craft(p, "greater_healing_draught", 3) == 0, "not enough for three");
        helper.assertTrue(count(p, Items.GLASS_BOTTLE) == 4 && count(p, CraftingModule.HOLLOW_ESSENCE.get()) == 2, "a refusal takes nothing");
        Economy.set(p, 0);
        helper.assertTrue(CraftingService.craft(p, "greater_healing_draught", 1) == 0, "no gold, no potion");
        Economy.set(p, 1000);
        helper.assertTrue(CraftingService.craft(p, "greater_healing_draught", 2) == 2, "but two works");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void plainCoresGoBeforeAffinityCores(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerRank.D);
        Economy.set(p, 1000);
        p.getInventory().add(core(PlayerRank.D, Affinity.FROST, 2));
        p.getInventory().add(core(PlayerRank.D, Affinity.NONE, 2));
        p.getInventory().add(new ItemStack(Items.GLASS_BOTTLE, 2));
        p.getInventory().add(new ItemStack(CraftingModule.ASHEN_PLATE.get(), 2));
        helper.assertTrue(CraftingService.craft(p, "draught_of_might", 2) == 2, "made");
        int frost = 0;
        for (ItemStack s : p.getInventory().items) if (MonsterCoreItem.affinity(s) == Affinity.FROST) frost += s.getCount();
        helper.assertTrue(frost == 2, "the frost cores were kept");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void elementCrystalsNeedTheirAffinity(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerRank.C);
        Economy.set(p, 1000);
        p.getInventory().add(core(PlayerRank.C, Affinity.NONE, 3));
        p.getInventory().add(new ItemStack(CraftingModule.MANA_STONE.get(), 3));
        String id = "water_infusion_crystal";
        helper.assertTrue(CraftingService.craft(p, id, 1) == 0, "a plain core won't do");
        p.getInventory().add(core(PlayerRank.C, Affinity.FROST, 1));
        helper.assertTrue(CraftingService.craft(p, id, 1) == 1, "a frostbound core will");
        helper.assertTrue(count(p, GearModule.CRYSTALS.get(SkillElement.WATER).get()) == 1, "a water crystal for the forge");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void forgedGearRollsItsStats(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerRank.C);
        Economy.set(p, 5000);
        p.getInventory().add(MonsterCoreItem.of(PlayerRank.C, true, Affinity.NONE, 1));
        p.getInventory().add(core(PlayerRank.C, Affinity.STORM, 1));
        p.getInventory().add(new ItemStack(CraftingModule.MANA_STONE.get(), 2));
        p.getInventory().add(new ItemStack(CraftingModule.ASHEN_PLATE.get(), 3));
        helper.assertTrue(CraftingService.craft(p, "thunderfang_longsword", 1) == 1, "forged");
        ItemStack sword = ItemStack.EMPTY;
        for (ItemStack s : p.getInventory().items) if (s.is(Arsenal.THUNDERFANG_LONGSWORD.get())) sword = s;
        helper.assertTrue(!sword.isEmpty() && ItemGrade.of(sword) != null && ItemGrade.of(sword).atLeast(ItemGrade.EPIC), "an epic sword");
        helper.assertTrue(GearRolls.stats(sword).rolls().size() >= 2, "with its bonus stats rolled");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void potionsAndManaStonesWork(GameTestHelper helper) {
        Zombie z = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
        z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        z.setHealth(10);
        new ItemStack(CraftingModule.GREATER_HEALING_DRAUGHT.get()).finishUsingItem(helper.getLevel(), z);
        helper.assertTrue(z.getHealth() >= 49, "greater healing heals 40%: " + z.getHealth());
        ServerPlayer p = hunter(helper, PlayerRank.C);
        SystemAPI.consumeMana(p, SystemAPI.get(p).mana());
        ItemStack stone = new ItemStack(CraftingModule.MANA_STONE.get(), 2);
        stone.finishUsingItem(helper.getLevel(), p);
        helper.assertTrue(Math.abs(SystemAPI.get(p).mana() - 40) < 0.1f, "a mana stone gives 40 mana");
        helper.assertTrue(stone.getCount() == 1, "and is used up");
        new ItemStack(CraftingModule.MANA_DRAUGHT.get()).finishUsingItem(helper.getLevel(), p);
        helper.assertTrue(SystemAPI.get(p).mana() >= 79.9f || SystemAPI.get(p).mana() >= SystemAPI.get(p).maxMana() - 0.1f, "a mana draught gives 40 more");
        SystemAPI.addExhaustion(p, 70);
        float before = SystemAPI.get(p).exhaustion();
        new ItemStack(CraftingModule.STAMINA_TONIC.get()).finishUsingItem(helper.getLevel(), p);
        helper.assertTrue(SystemAPI.get(p).exhaustion() <= before - 39.9f, "the tonic takes exhaustion away");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void veinsGiveManaStones(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerRank.C);
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        int[] sizes = {0, 1, 2};
        for (int size : sizes) {
            BlockState vein = CraftingModule.VEINS.get(size).get().defaultBlockState();
            helper.getLevel().setBlock(at, vein, 3);
            List<ItemStack> drops = Block.getDrops(vein, helper.getLevel(), at, null, p, new ItemStack(Items.IRON_PICKAXE));
            helper.assertTrue(drops.stream().anyMatch(s -> s.is(CraftingModule.stoneFor(size))), "vein " + size + " gives its stone");
        }
        helper.getLevel().removeBlock(at, false);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void coresSellByRankAndEveryRecipeIsWhole(GameTestHelper helper) {
        long last = 0;
        for (PlayerRank r : PlayerRank.values()) {
            long v = SellPrices.each(core(r, Affinity.NONE, 1));
            helper.assertTrue(v > last, "higher cores sell for more");
            helper.assertTrue(SellPrices.each(MonsterCoreItem.of(r, true, Affinity.NONE, 1)) > v, "boss cores sell for more than cores");
            last = v;
        }
        helper.assertTrue(SellPrices.each(new ItemStack(CraftingModule.EMBER_CINDER.get())) > 0, "materials sell");
        helper.assertTrue(SellPrices.each(new ItemStack(CraftingModule.HEALING_DRAUGHT.get())) > 0, "potions sell");
        for (SystemRecipes.Recipe r : SystemRecipes.all().values()) {
            helper.assertTrue(!r.display().isEmpty(), r.id() + " makes something");
            helper.assertTrue(!r.needs().isEmpty(), r.id() + " takes something");
            for (SystemRecipes.Need n : r.needs()) {
                helper.assertTrue(n.count() > 0 && n.match().test(n.icon().get()), r.id() + ": an ingredient's icon counts as itself");
            }
        }
        helper.assertTrue(SystemRecipes.get("quenching_elixir") != null && SystemRecipes.get("greater_antidote") != null,
                "the deep burn and heavy venom cures can be brewed");
        helper.assertTrue(ModItems.QUENCHING_ELIXIR.get() == SystemRecipes.get("quenching_elixir").display().getItem(), "the real elixir");
        helper.succeed();
    }
}
