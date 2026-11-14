package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.Buildups;
import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.combat.ModAttributes;
import com.beyondsolo.combat.ModEffects;
import com.beyondsolo.combat.Statuses;
import com.beyondsolo.item.ArmorSets;
import com.beyondsolo.registry.ModItems;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.Stat;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Combat tests: statuses and their cures, buildups from weapons, resistances, armor sets and exhaustion. */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombatGameTests {
    private static final String ARENA = "evaluation_zone";
    private static final BlockPos SPOT = new BlockPos(1, 2, 1);

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "fighter"));
        SystemAPI.reset(p);
        p.setGameMode(GameType.SURVIVAL);
        p.removeAllEffects();
        p.setHealth(p.getMaxHealth());
        BlockPos start = helper.absolutePos(SPOT);
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        return p;
    }

    private static Zombie zombie(GameTestHelper helper) {
        return helper.spawnWithNoFreeWill(EntityType.ZOMBIE, SPOT);
    }

    @GameTest(template = ARENA)
    public static void burnBuildupTurnsIntoDeepBurn(GameTestHelper helper) {
        Zombie z = zombie(helper);
        Statuses.apply(z, DamageElement.FIRE, 60, null);
        helper.assertFalse(z.hasEffect(ModEffects.DEEP_BURN), "no deep burn below the threshold");
        Statuses.apply(z, DamageElement.FIRE, 60, null);
        helper.assertTrue(z.hasEffect(ModEffects.DEEP_BURN), "deep burn at the threshold");
        helper.assertTrue(z.getEffect(ModEffects.DEEP_BURN).isInfiniteDuration(), "deep burn never wears off");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void deepBurnIgnoresMilkAndTotemButElixirCures(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        Statuses.apply(p, DamageElement.FIRE, 200, null);
        helper.assertTrue(p.hasEffect(ModEffects.DEEP_BURN), "player should burn");
        p.removeEffectsCuredBy(EffectCures.MILK);
        p.removeEffectsCuredBy(EffectCures.PROTECTED_BY_TOTEM);
        helper.assertTrue(p.hasEffect(ModEffects.DEEP_BURN), "milk and totems must not cure deep burn");
        ItemStack elixir = new ItemStack(ModItems.QUENCHING_ELIXIR.get());
        ItemStack left = elixir.finishUsingItem(helper.getLevel(), p);
        helper.assertFalse(p.hasEffect(ModEffects.DEEP_BURN), "the elixir cures deep burn");
        helper.assertTrue(p.hasEffect(ModEffects.FIRE_WARD), "the elixir wards against fire");
        helper.assertTrue(left.is(net.minecraft.world.item.Items.GLASS_BOTTLE), "the bottle comes back");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void venomStacksThenTurnsHeavy(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        for (int i = 1; i <= 4; i++) {
            Statuses.apply(p, DamageElement.POISON, Statuses.VENOM.threshold(), null);
            helper.assertTrue(p.hasEffect(ModEffects.VENOM) && p.getEffect(ModEffects.VENOM).getAmplifier() == i - 1,
                    "expected " + i + " venom stacks");
        }
        Statuses.apply(p, DamageElement.POISON, Statuses.VENOM.threshold(), null);
        helper.assertFalse(p.hasEffect(ModEffects.VENOM), "venom turns heavy at 5 stacks");
        helper.assertTrue(p.hasEffect(ModEffects.HEAVY_VENOM), "heavy venom at 5 stacks");
        p.removeEffectsCuredBy(EffectCures.MILK);
        new ItemStack(ModItems.ANTIDOTE.get()).finishUsingItem(helper.getLevel(), p);
        helper.assertTrue(p.hasEffect(ModEffects.HEAVY_VENOM), "milk and a plain antidote can't cure heavy venom");
        new ItemStack(ModItems.GREATER_ANTIDOTE.get()).finishUsingItem(helper.getLevel(), p);
        helper.assertFalse(p.hasEffect(ModEffects.HEAVY_VENOM), "a greater antidote cures heavy venom");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void lightVenomCuredByMilk(GameTestHelper helper) {
        Zombie z = zombie(helper);
        Statuses.addVenomStack(z, null);
        z.removeEffectsCuredBy(EffectCures.MILK);
        helper.assertFalse(z.hasEffect(ModEffects.VENOM), "milk cures light venom");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void crimsonBurstBrandsAndAmplifiesDamage(GameTestHelper helper) {
        Zombie z = zombie(helper);
        Statuses.apply(z, DamageElement.CRIMSON, 100, null);
        helper.assertTrue(z.hasEffect(ModEffects.CRIMSON_BRAND), "crimson burst brands the target");
        z.setHealth(z.getMaxHealth());
        z.invulnerableTime = 0;
        z.hurt(z.damageSources().generic(), 4f);
        float taken = z.getMaxHealth() - z.getHealth();
        helper.assertTrue(Math.abs(taken - 5f) < 0.01f, "branded targets take 25% more, took " + taken);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void bloodPopIgnoresArmor(GameTestHelper helper) {
        // (Fake players can't take damage, so the pop itself is checked on an armoured zombie; the buildup that
        // leads to it lands on players the same way, see resistanceShrinksBuildup and the player tests above.)
        Zombie z = zombie(helper);
        z.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.DUSK_CHESTPLATE.get()));
        z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.KNIGHT_HELM.get()));
        helper.runAfterDelay(2, () -> {
            float max = z.getMaxHealth();
            z.setHealth(max);
            Statuses.apply(z, DamageElement.BLOOD, 120, null); // the helm's 8% blood resistance shrinks it to 110
            helper.assertTrue(Math.abs(z.getHealth() - max * 0.9f) < 0.01f, "pop should take 10% through armor, health " + z.getHealth() + "/" + max);
            helper.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void buildupLandsOnPlayers(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        Statuses.apply(p, DamageElement.BLOOD, 30, null);
        helper.assertTrue(Math.abs(Buildups.get(p, Buildups.BLOOD) - 30f) < 0.01f, "players build up blood like mobs");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void weaponHitsBuildUpTheirElement(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.EMBERBRAND.get()));
        Zombie z = zombie(helper);
        z.hurt(p.damageSources().playerAttack(p), 3f);
        float burn = Buildups.get(z, Statuses.BURN);
        helper.assertTrue(burn >= 20f, "an Emberbrand hit should add burn buildup, got " + burn);
        helper.assertTrue(z.isOnFire(), "an Emberbrand hit sets the target alight");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void resistanceShrinksBuildup(GameTestHelper helper) {
        Zombie z = zombie(helper);
        z.getAttribute(ModAttributes.BLOOD_RESISTANCE).addPermanentModifier(
                new AttributeModifier(ResourceLocation.fromNamespaceAndPath("beyondsolo", "test"), 0.5, AttributeModifier.Operation.ADD_VALUE));
        Statuses.apply(z, DamageElement.BLOOD, 40, null);
        float blood = Buildups.get(z, Buildups.BLOOD);
        helper.assertTrue(Math.abs(blood - 20f) < 0.01f, "50% resistance halves buildup, got " + blood);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void fullArmorSetGivesItsBonus(GameTestHelper helper) {
        Zombie z = zombie(helper);
        z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.KNIGHT_HELM.get()));
        z.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.KNIGHT_CUIRASS.get()));
        z.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.KNIGHT_GREAVES.get()));
        helper.runAfterDelay(2, () -> {
            double three = z.getAttributeValue(ModAttributes.BLOOD_RESISTANCE);
            helper.assertTrue(Math.abs(three - 0.24) < 1e-6, "three pieces: 3 x 8%, got " + three);
            z.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.KNIGHT_SABATONS.get()));
            helper.runAfterDelay(2, () -> {
                double four = z.getAttributeValue(ModAttributes.BLOOD_RESISTANCE);
                helper.assertTrue(Math.abs(four - 0.52) < 1e-6, "full set: 4 x 8% + 20%, got " + four);
                helper.assertTrue(ArmorSets.weight(z) > 0.5f, "knight plate is heavy");
                helper.succeed();
            });
        });
    }

    @GameTest(template = ARENA)
    public static void statsFeedCombat(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        SystemAPI.awaken(p, PlayerRank.E, PlayerClass.WARRIOR);
        SystemAPI.setStat(p, Stat.VIT, 60);
        SystemAPI.setStat(p, Stat.INT, 40);
        helper.assertTrue(p.getAttributeValue(ModAttributes.FIRE_RESISTANCE) > 0.15, "VIT raises element resistance");
        helper.assertTrue(p.getAttributeValue(ModAttributes.ELEMENTAL_POWER) > 0.25, "INT raises elemental power");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void exhaustionResistanceSlowsFatigue(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        SystemAPI.awaken(p, PlayerRank.E, PlayerClass.WARRIOR);
        SystemData d = SystemAPI.get(p);
        float vitResist = (float) p.getAttributeValue(ModAttributes.EXHAUSTION_RESISTANCE);
        SystemAPI.addExhaustion(p, 10);
        float plain = d.exhaustion();
        helper.assertTrue(Math.abs(plain - 10 * (1 - vitResist)) < 0.01f, "expected " + 10 * (1 - vitResist) + " got " + plain);
        p.getAttribute(ModAttributes.EXHAUSTION_RESISTANCE).addPermanentModifier(
                new AttributeModifier(ResourceLocation.fromNamespaceAndPath("beyondsolo", "test"), 0.5, AttributeModifier.Operation.ADD_VALUE));
        SystemAPI.addExhaustion(p, 10);
        float resisted = d.exhaustion() - plain;
        helper.assertTrue(resisted < plain * 0.6f, "50% more resistance should cut the gain, got " + resisted);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void everyWeaponHasBetterCombatAttributes(GameTestHelper helper) {
        var resources = helper.getLevel().getServer().getResourceManager();
        for (var weapon : ModItems.WEAPONS) {
            ResourceLocation file = BeyondSolo.id("weapon_attributes/" + weapon.getId().getPath() + ".json");
            helper.assertTrue(resources.getResource(file).isPresent(), "missing " + file);
        }
        helper.succeed();
    }

    private CombatGameTests() {}
}
