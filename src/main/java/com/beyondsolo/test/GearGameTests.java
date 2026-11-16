package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.armory.ArmorDrops;
import com.beyondsolo.combat.ModAttributes;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.economy.SellPrices;
import com.beyondsolo.economy.ShopCatalog;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.GateLoot;
import com.beyondsolo.gear.BonusStat;
import com.beyondsolo.gear.BonusStats;
import com.beyondsolo.gear.BossLoot;
import com.beyondsolo.gear.BossSets;
import com.beyondsolo.gear.Enhancement;
import com.beyondsolo.gear.ForgeService;
import com.beyondsolo.gear.GearKind;
import com.beyondsolo.gear.GearModule;
import com.beyondsolo.gear.GearRolls;
import com.beyondsolo.gear.Infusions;
import com.beyondsolo.gear.SetEffects;
import com.beyondsolo.item.ArmorSets;
import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.item.SystemArmorItem;
import com.beyondsolo.item.arsenal.Arsenal;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.registry.ModItems;
import com.beyondsolo.skill.SkillCombat;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** Item grades, bonus stats, set bonuses, boss loot, enhancement, the forge and infusions. */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GearGameTests {
    private static final String ARENA = "evaluation_zone";
    private static final BlockPos SPOT = new BlockPos(1, 2, 1);

    private static ServerPlayer hunter(GameTestHelper helper) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "smith"));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        Economy.reset(p);
        p.getInventory().clearContent();
        p.setGameMode(GameType.SURVIVAL);
        BlockPos start = helper.absolutePos(SPOT);
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        QuestTestAccess.markStarted(p);
        SystemAPI.awaken(p, PlayerRank.C, PlayerClass.WARRIOR);
        return p;
    }

    private static Zombie zombie(GameTestHelper helper) {
        Zombie z = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
        z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400);
        z.setHealth(400);
        return z;
    }

    @GameTest(template = ARENA)
    public static void dropsRollMoreStatsAtHigherGrades(GameTestHelper helper) {
        RandomSource r = RandomSource.create(7);
        for (ItemGrade grade : ItemGrade.values()) {
            for (int i = 0; i < 20; i++) {
                ItemStack sword = GearRolls.roll(new ItemStack(Arsenal.KNIGHTSBANE.get()), grade, r);
                BonusStats s = GearRolls.stats(sword);
                ItemGrade got = ItemGrade.of(sword);
                helper.assertTrue(got.tier() >= ItemGrade.RARE.tier(), "a weapon never drops below its own grade");
                if (grade.tier() > ItemGrade.RARE.tier()) {
                    helper.assertTrue(s.rolls().size() == GearRolls.COUNT[grade.tier()], grade + " rolls " + GearRolls.COUNT[grade.tier()] + ", got " + s.rolls().size());
                }
                for (BonusStats.Roll roll : s.rolls()) {
                    helper.assertTrue(roll.stat().fits(GearKind.MELEE), roll.stat() + " fits a melee weapon");
                    helper.assertTrue(roll.value() >= roll.stat().min * GearRolls.gradeScale(got) - 1e-3
                            && roll.value() <= roll.stat().max * GearRolls.gradeScale(got) + 1e-3, "value in range: " + roll);
                }
                helper.assertTrue(s.rolls().stream().map(BonusStats.Roll::stat).distinct().count() == s.rolls().size(), "no stat twice");
            }
        }
        ItemStack coat = GearRolls.roll(new ItemStack(ModItems.HUNTER_COAT.get()), ItemGrade.LEGENDARY, r);
        for (BonusStats.Roll roll : GearRolls.stats(coat).rolls()) helper.assertTrue(roll.stat().fits(GearKind.ARMOR), "armor rolls armor stats");
        ItemStack bow = GearRolls.roll(new ItemStack(Arsenal.WINDRUNNER_LONGBOW.get()), ItemGrade.EPIC, r);
        for (BonusStats.Roll roll : GearRolls.stats(bow).rolls()) helper.assertTrue(roll.stat().fits(GearKind.BOW), "bows roll bow stats: " + roll);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void bonusStatsAndEnhancementBecomeAttributes(GameTestHelper helper) {
        Zombie z = zombie(helper);
        ItemStack sword = new ItemStack(Arsenal.SILVERMOON_RAPIER.get());
        sword.set(GearModule.BONUS_STATS.get(), new BonusStats(List.of(new BonusStats.Roll(BonusStat.ELEMENTAL_POWER, 0.2f))));
        ItemStack helm = new ItemStack(ModItems.KNIGHT_HELM.get());
        helm.set(GearModule.BONUS_STATS.get(), new BonusStats(List.of(new BonusStats.Roll(BonusStat.MAX_HEALTH, 4f))));
        double plainArmor = 0;
        z.setItemSlot(EquipmentSlot.HEAD, helm);
        helper.runAfterDelay(2, () -> {
            double baseDamage = z.getAttributeValue(Attributes.ATTACK_DAMAGE);
            double armor = z.getAttributeValue(Attributes.ARMOR);
            helper.assertTrue(z.getAttributeValue(Attributes.MAX_HEALTH) >= 404 - 1e-6, "+4 max health from the helm's roll");
            z.setItemInHand(InteractionHand.MAIN_HAND, sword);
            ItemStack worn = z.getItemBySlot(EquipmentSlot.HEAD);
            Enhancement.set(worn, 4);
            z.setItemSlot(EquipmentSlot.HEAD, worn.copy());
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(Math.abs(z.getAttributeValue(ModAttributes.ELEMENTAL_POWER) - 0.2) < 1e-4, "elemental power from the sword's roll");
                double unrefined = z.getAttributeValue(Attributes.ATTACK_DAMAGE);
                helper.assertTrue(unrefined > baseDamage, "the sword adds damage");
                helper.assertTrue(z.getAttributeValue(Attributes.ARMOR) >= armor + 2 - 1e-6, "+4 helm: +2 armor");
                ItemStack held = z.getMainHandItem().copy();
                Enhancement.set(held, 10);
                z.setItemInHand(InteractionHand.MAIN_HAND, held);
                helper.runAfterDelay(2, () -> {
                    double refined = z.getAttributeValue(Attributes.ATTACK_DAMAGE);
                    helper.assertTrue(Math.abs(refined - unrefined * 1.5) < 0.01, "+10: +50% damage, " + unrefined + " -> " + refined);
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = ARENA)
    public static void enhancementOddsAndDowngrades(GameTestHelper helper) {
        RandomSource r = RandomSource.create(11);
        // +0 -> +1 never fails
        for (int i = 0; i < 50; i++) {
            ItemStack s = new ItemStack(Arsenal.GOBLIN_SHIV.get());
            helper.assertTrue(Enhancement.attempt(s, r, false) == Enhancement.Outcome.SUCCESS && Enhancement.level(s) == 1, "+1 always works");
        }
        int safeFails = 0, drops = 0, wards = 0, tops = 0;
        for (int i = 0; i < 300; i++) {
            ItemStack low = new ItemStack(Arsenal.GOBLIN_SHIV.get());
            Enhancement.set(low, 4);
            if (Enhancement.attempt(low, r, false) == Enhancement.Outcome.FAILED) {
                safeFails++;
                helper.assertTrue(Enhancement.level(low) == 4, "a safe failure keeps the level");
            }
            ItemStack high = new ItemStack(Arsenal.GOBLIN_SHIV.get());
            Enhancement.set(high, 9);
            Enhancement.Outcome o = Enhancement.attempt(high, r, false);
            if (o == Enhancement.Outcome.DOWNGRADED) {
                drops++;
                helper.assertTrue(Enhancement.level(high) == 8, "a risky failure drops a level");
            } else {
                helper.assertTrue(o == Enhancement.Outcome.SUCCESS && Enhancement.level(high) == 10, "or it reaches +10");
                tops++;
            }
            ItemStack warded = new ItemStack(Arsenal.GOBLIN_SHIV.get());
            Enhancement.set(warded, 9);
            if (Enhancement.attempt(warded, r, true) == Enhancement.Outcome.WARDED) {
                wards++;
                helper.assertTrue(Enhancement.level(warded) == 9, "a ward seal keeps the level");
            }
        }
        helper.assertTrue(safeFails > 20 && safeFails < 100, "+5 fails about 30% of the time: " + safeFails);
        helper.assertTrue(tops > 30 && tops < 100, "+10 succeeds about 20% of the time: " + tops);
        helper.assertTrue(drops > 200 && wards > 200, "drops " + drops + ", wards " + wards);
        ItemStack max = new ItemStack(Arsenal.GOBLIN_SHIV.get());
        Enhancement.set(max, 10);
        helper.assertTrue(Enhancement.attempt(max, r, false) == Enhancement.Outcome.MAXED, "+10 is the top");
        helper.assertTrue(Enhancement.attempt(new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD), r, false) == Enhancement.Outcome.INVALID, "vanilla gear can't be refined");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void theForgeChargesAndRefuses(GameTestHelper helper) {
        ServerPlayer p = hunter(helper);
        ItemStack sword = new ItemStack(Arsenal.KNIGHTSBANE.get());
        p.getInventory().setItem(0, sword);
        helper.assertTrue(ForgeService.refine(p, 0, false) == Enhancement.Outcome.INVALID, "no stones, no gold: refused");
        p.getInventory().setItem(1, new ItemStack(GearModule.REFINING_STONE.get(), 10));
        helper.assertTrue(ForgeService.refine(p, 0, false) == Enhancement.Outcome.INVALID, "no gold: refused");
        long cost = Enhancement.gold(ItemGrade.RARE, 0);
        Economy.add(p, cost * 100, Economy.Reason.COMMAND);
        long before = Economy.gold(p);
        helper.assertTrue(ForgeService.refine(p, 0, false) == Enhancement.Outcome.SUCCESS, "+1");
        helper.assertTrue(Enhancement.level(p.getInventory().getItem(0)) == 1, "the stack in the slot is refined");
        helper.assertTrue(Economy.gold(p) == before - cost, "gold paid");
        helper.assertTrue(ForgeService.count(p.getInventory(), GearModule.REFINING_STONE.get()) == 9, "one stone used");
        helper.assertTrue(ForgeService.refine(p, 5, false) == Enhancement.Outcome.INVALID, "an empty slot is refused");

        // a ward seal is only used up when it saves the gear
        Enhancement.set(p.getInventory().getItem(0), 9);
        p.getInventory().setItem(2, new ItemStack(GearModule.WARD_SEAL.get(), 1));
        p.getInventory().setItem(1, new ItemStack(GearModule.REFINING_STONE.get(), 64));
        boolean sawWard = false;
        for (int i = 0; i < 40 && !sawWard; i++) {
            Enhancement.set(p.getInventory().getItem(0), 9);
            Enhancement.Outcome o = ForgeService.refine(p, 0, true);
            if (o == Enhancement.Outcome.WARDED) {
                sawWard = true;
                helper.assertTrue(ForgeService.count(p.getInventory(), GearModule.WARD_SEAL.get()) == 0, "the seal broke");
                helper.assertTrue(Enhancement.level(p.getInventory().getItem(0)) == 9, "and the level held");
            } else {
                helper.assertTrue(ForgeService.count(p.getInventory(), GearModule.WARD_SEAL.get()) == 1, "a success keeps the seal");
            }
        }
        helper.assertTrue(sawWard, "a +10 try failed at least once in 40");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void infusedHitsSetOffReactions(GameTestHelper helper) {
        ServerPlayer p = hunter(helper);
        ItemStack sword = new ItemStack(Arsenal.SILVERMOON_RAPIER.get());
        p.getInventory().setItem(0, sword);
        p.getInventory().setItem(1, new ItemStack(GearModule.CRYSTALS.get(SkillElement.WATER).get()));
        Economy.add(p, 10_000, Economy.Reason.COMMAND);
        helper.assertTrue(ForgeService.infuse(p, 0, SkillElement.WATER.ordinal()), "infused");
        helper.assertTrue(Infusions.of(p.getInventory().getItem(0)) == SkillElement.WATER, "water on the blade");
        helper.assertTrue(ForgeService.count(p.getInventory(), GearModule.CRYSTALS.get(SkillElement.WATER).get()) == 0, "crystal used");
        helper.assertTrue(!ForgeService.infuse(p, 0, SkillElement.WATER.ordinal()), "the same element twice is refused");
        p.getInventory().setItem(3, new ItemStack(ModItems.KNIGHT_HELM.get()));
        helper.assertTrue(!ForgeService.infuse(p, 3, SkillElement.FIRE.ordinal()), "armor can't be infused");

        // a plain zombie: the hit leaves water
        Zombie dry = zombie(helper);
        float plain = Infusions.strike(p, dry, SkillElement.WATER, 10f);
        helper.assertTrue(SkillCombat.aura(dry) == SkillElement.WATER, "the hit leaves its element");
        // a zombie carrying lightning: water sets off a surge
        Zombie charged = zombie(helper);
        SkillCombat.setAura(charged, SkillElement.LIGHTNING);
        float surged = Infusions.strike(p, charged, SkillElement.WATER, 10f);
        helper.assertTrue(SkillCombat.aura(charged) == null, "the reaction uses the element up");
        helper.assertTrue(surged > plain * 1.4f, "the reaction hits harder: " + plain + " vs " + surged);
        helper.assertTrue(charged.getHealth() < 400, "and hurts");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void twoAndFourPieceSetBonuses(GameTestHelper helper) {
        Zombie z = zombie(helper);
        List<? extends net.neoforged.neoforge.registries.DeferredItem<SystemArmorItem>> pieces = GearModule.SET_PIECES.get("stonehide");
        double armor0 = z.getAttributeValue(Attributes.ARMOR);
        z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(pieces.get(0).get()));
        z.setItemSlot(EquipmentSlot.CHEST, new ItemStack(pieces.get(1).get()));
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(ArmorSets.pieces(z, BossSets.STONEHIDE) == 2, "two pieces");
            helper.assertTrue(z.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 0.1 - 1e-6, "2-piece knockback resistance");
            helper.assertTrue(!ArmorSets.hasPerk(z, "unbroken"), "no perk yet");
            z.setItemSlot(EquipmentSlot.LEGS, new ItemStack(pieces.get(2).get()));
            z.setItemSlot(EquipmentSlot.FEET, new ItemStack(pieces.get(3).get()));
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(ArmorSets.hasPerk(z, "unbroken"), "four pieces: Unbroken");
                helper.assertTrue(z.getAttributeValue(Attributes.ARMOR_TOUGHNESS) >= 2 - 1e-6, "4-piece toughness");
                // Unbroken: a hit that takes it under 30% hardens it
                z.setHealth(z.getMaxHealth() * 0.35f);
                z.hurt(z.damageSources().generic(), z.getMaxHealth() * 0.1f);
                helper.assertTrue(z.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE), "Unbroken triggered");
                helper.succeed();
            });
        });
    }

    @GameTest(template = ARENA)
    public static void everyBossHasItsSignatureTable(GameTestHelper helper) {
        RandomSource r = RandomSource.create(3);
        for (PlayerRank rank : PlayerRank.values()) {
            BossLoot.Table t = BossLoot.table(rank);
            helper.assertTrue(t.rank() == rank && BossSets.forRank(rank) == t.set(), "table for " + rank);
            int weapons = 0;
            for (int i = 0; i < 60; i++) {
                List<ItemStack> loot = GateLoot.roll(rank, r);
                for (ItemStack s : loot) {
                    for (BossLoot.Entry e : t.weapons()) if (s.is(e.item().get())) weapons++;
                    // set pieces only come from the armory's per-kill chance now, never from the rank's table
                    helper.assertFalse(s.getItem() instanceof SystemArmorItem a && a.set().signature(), rank + " table rolls no set pieces");
                }
                helper.assertTrue(loot.stream().anyMatch(s -> s.is(GearModule.REFINING_STONE.get())), "refining stones");
            }
            helper.assertTrue(weapons >= 8 && weapons <= 45, rank + " signature weapons about a third of the time: " + weapons);
            // the brute still leaves its rank's own set: an ascended one always does
            List<ItemStack> pieces = ArmorDrops.bossPieces(BossKind.BRUTE, rank, true, r);
            helper.assertTrue(pieces.stream().anyMatch(s -> s.getItem() instanceof SystemArmorItem a && a.set() == t.set()),
                    rank + " brute leaves a " + t.set().name + " piece");
            for (ItemStack s : pieces) {
                if (s.getItem() instanceof SystemArmorItem a && a.set() == t.set()) {
                    helper.assertTrue(ItemGrade.of(s).tier() >= t.set().grade.tier(), "set piece at least its grade");
                    helper.assertTrue(BossLoot.isSignature(s, rank), "and called out");
                }
            }
        }
        helper.assertTrue(GearModule.bossArmor().size() == 24, "six sets of four");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void refinedGearSellsForMoreButNeverPaysBack(GameTestHelper helper) {
        ItemStack plain = new ItemStack(Arsenal.THUNDERFANG_LONGSWORD.get());
        long base = SellPrices.each(plain);
        ItemStack rolled = GearRolls.roll(plain.copy(), ItemGrade.EPIC, RandomSource.create(1));
        helper.assertTrue(SellPrices.each(rolled) > base, "rolled stats add value");
        for (int level = 0; level < Enhancement.MAX; level++) {
            ItemStack a = plain.copy();
            Enhancement.set(a, level);
            ItemStack b = plain.copy();
            Enhancement.set(b, level + 1);
            long gain = SellPrices.each(b) - SellPrices.each(a);
            helper.assertTrue(Enhancement.gold(ItemGrade.EPIC, level) > gain, "a try costs more than the level sells for at +" + level);
        }
        helper.assertTrue(ShopCatalog.get("refining_stone") != null && ShopCatalog.get("ward_seal") != null, "the shop sells forge materials");
        for (Item crystal : GearModule.CRYSTALS.values().stream().map(c -> (Item) c.get()).toList()) {
            helper.assertTrue(SellPrices.each(new ItemStack(crystal)) > 0, "crystals sell");
        }
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void setsCarryTheirElement(GameTestHelper helper) {
        Zombie wearer = zombie(helper);
        for (var piece : GearModule.SET_PIECES.get("emberwrought")) {
            SystemArmorItem a = piece.get();
            wearer.setItemSlot(a.getEquipmentSlot(), new ItemStack(a));
        }
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(SetEffects.innateElement(wearer) == SkillElement.FIRE, "Emberwrought hits carry fire");
            helper.assertTrue(wearer.getAttributeValue(ModAttributes.FIRE_RESISTANCE) >= 0.65 - 1e-6, "and burns barely touch it");
            helper.succeed();
        });
    }
}
