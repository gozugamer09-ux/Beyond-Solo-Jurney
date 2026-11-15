package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.ArsenalAbilities;
import com.beyondsolo.combat.ArsenalHits;
import com.beyondsolo.combat.ModEffects;
import com.beyondsolo.gate.GateLoot;
import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.item.SystemBowItem;
import com.beyondsolo.item.SystemWeaponItem;
import com.beyondsolo.item.arsenal.Arsenal;
import com.beyondsolo.item.arsenal.ArsenalWeapon;
import com.beyondsolo.registry.ModItems;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Arsenal tests: grades, gate loot odds, traits, class-bound divine weapons and signature arts. */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArsenalGameTests {
    private static final String ARENA = "evaluation_zone";
    private static final BlockPos SPOT = new BlockPos(1, 2, 1);

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "armsmaster"));
        SystemAPI.reset(p);
        p.setGameMode(GameType.SURVIVAL);
        p.removeAllEffects();
        p.setHealth(p.getMaxHealth());
        // no lucky critical hits: the tests compare damage numbers
        var crit = p.getAttribute(dev.shadowsoffire.apothic_attributes.api.ALObjects.Attributes.CRIT_CHANCE);
        if (crit != null) {
            crit.removeModifiers();
            crit.setBaseValue(-10);
        }
        p.getCooldowns().tick();
        BlockPos start = helper.absolutePos(SPOT);
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        return p;
    }

    private static Zombie zombie(GameTestHelper helper) {
        return helper.spawnWithNoFreeWill(EntityType.ZOMBIE, SPOT);
    }

    @GameTest(template = ARENA)
    public static void everyWeaponHasAGradeAndAMoveset(GameTestHelper helper) {
        var resources = helper.getLevel().getServer().getResourceManager();
        int count = 0;
        for (var item : Arsenal.all()) {
            ItemStack stack = new ItemStack(item.get());
            helper.assertTrue(ItemGrade.of(stack) != null, item.getId() + " should carry a grade");
            helper.assertTrue(ItemGrade.of(stack) == ((ArsenalWeapon) item.get()).baseGrade(), item.getId() + " grade component matches");
            if (item.get() instanceof SystemWeaponItem) {
                var key = BeyondSolo.id("weapon_attributes/" + item.getId().getPath() + ".json");
                helper.assertTrue(resources.getResource(key).isPresent(), item.getId() + " needs a Better Combat moveset");
            }
            count++;
        }
        helper.assertTrue(count >= 45, "the arsenal should hold the 8 combat weapons plus at least 37 more, got " + count);
        for (var armor : ModItems.ARMOR) helper.assertTrue(ItemGrade.of(new ItemStack(armor.get())) != null, armor.getId() + " graded");
        helper.assertTrue(ItemGrade.of(new ItemStack(Items.DIAMOND_SWORD)) == null, "vanilla items stay ungraded");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void eachGradeHasWeaponsAndEachClassADivine(GameTestHelper helper) {
        for (ItemGrade g : ItemGrade.values()) helper.assertTrue(!Arsenal.ofGrade(g).isEmpty(), "no weapons of grade " + g);
        helper.assertTrue(Arsenal.ofGrade(ItemGrade.MYTHIC).size() >= 2, "at least two mythics");
        Set<Object> divines = new HashSet<>();
        for (PlayerClass c : PlayerClass.values()) {
            var d = Arsenal.divineFor(c);
            helper.assertTrue(d != null, "no divine weapon for " + c);
            helper.assertTrue(((ArsenalWeapon) d.get()).boundToClass(), c + "'s divine is bound to it");
            divines.add(d.get());
        }
        helper.assertTrue(divines.size() == PlayerClass.values().length, "one distinct divine per class");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void gateRankDecidesWeaponGrades(GameTestHelper helper) {
        RandomSource r = RandomSource.create(42);
        EnumSet<ItemGrade> low = EnumSet.noneOf(ItemGrade.class);
        EnumSet<ItemGrade> high = EnumSet.noneOf(ItemGrade.class);
        int mythicOrBetter = 0;
        for (int i = 0; i < 5000; i++) {
            low.add(GateLoot.rollGrade(PlayerRank.E.tier(), r));
            ItemGrade g = GateLoot.rollGrade(PlayerRank.S.tier(), r);
            high.add(g);
            if (g.atLeast(ItemGrade.MYTHIC)) mythicOrBetter++;
        }
        helper.assertTrue(low.equals(EnumSet.of(ItemGrade.COMMON, ItemGrade.RARE)), "E gates drop common and rare only: " + low);
        helper.assertFalse(high.contains(ItemGrade.COMMON), "S gates never drop common weapons");
        helper.assertTrue(high.contains(ItemGrade.DIVINE), "S gates can drop divine weapons");
        helper.assertTrue(mythicOrBetter > 200 && mythicOrBetter < 700, "mythic and divine stay scarce in S gates: " + mythicOrBetter);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void frostStacksThenFreezesAndShatters(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Arsenal.FROSTWARDEN_DAGGER.get()));
        Zombie z = zombie(helper);
        for (int i = 0; i < 3; i++) {
            z.invulnerableTime = 0;
            z.hurt(p.damageSources().playerAttack(p), 1f);
        }
        helper.assertTrue(z.hasEffect(ModEffects.FROSTBITE) && z.getEffect(ModEffects.FROSTBITE).getAmplifier() == 2, "three hits stack Frostbite III");
        z.invulnerableTime = 0;
        z.hurt(p.damageSources().playerAttack(p), 1f);
        helper.assertTrue(z.hasEffect(ModEffects.FROZEN), "the fourth hit freezes");
        Zombie fresh = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, SPOT.east());
        float plain = hit(p, fresh, 4f);
        float shattered = hit(p, z, 4f);
        helper.assertFalse(z.hasEffect(ModEffects.FROZEN), "a hit shatters the ice");
        helper.assertTrue(shattered > plain * 1.25f, "shatter hits harder: " + shattered + " vs " + plain);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void knightsbaneHitsArmorHarder(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        // Apothic's random critical hits (5% base) would make the comparison flaky
        var crit = p.getAttribute(dev.shadowsoffire.apothic_attributes.api.ALObjects.Attributes.CRIT_CHANCE);
        if (crit != null) crit.setBaseValue(-10);
        Zombie bare = zombie(helper);
        Zombie armored = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, SPOT.east());
        armored.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        armored.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        helper.runAfterDelay(2, () -> {
            // gate variants and elites can roll armor onto any zombie: make the bare one truly bare
            for (EquipmentSlot slot : EquipmentSlot.values()) if (slot.isArmor()) bare.setItemSlot(slot, ItemStack.EMPTY);
            var armor = bare.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
            if (armor != null) {
                armor.removeModifiers();
                armor.setBaseValue(0);
            }
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            float plainBare = hit(p, bare, 6f);
            float plainArmored = hit(p, armored, 6f);
            bare.setHealth(bare.getMaxHealth());
            armored.setHealth(armored.getMaxHealth());
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Arsenal.KNIGHTSBANE.get()));
            float baneBare = hit(p, bare, 6f);
            float baneArmored = hit(p, armored, 6f);
            helper.assertTrue(baneBare < plainBare * 1.1f, "little bonus on lightly armored targets: " + baneBare + " vs " + plainBare);
            helper.assertTrue(baneArmored > plainArmored * 1.2f, "Knightsbane cuts through armor: " + baneArmored + " vs " + plainArmored);
            helper.succeed();
        });
    }

    private static float hit(ServerPlayer p, Zombie z, float amount) {
        float before = z.getHealth();
        z.invulnerableTime = 0;
        z.hurt(p.damageSources().playerAttack(p), amount);
        return before - z.getHealth();
    }

    @GameTest(template = ARENA)
    public static void divineWeaponsAnswerOnlyToTheirClass(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        SystemWeaponItem ruin = Arsenal.RUINBRINGER.get();
        helper.assertTrue(ArsenalHits.refuses(p, ruin), "a player without the System is refused");
        SystemAPI.awaken(p, PlayerRank.E, PlayerClass.MAGE);
        helper.assertTrue(ArsenalHits.refuses(p, ruin), "a mage is refused by the warrior's divine");
        helper.assertFalse(ArsenalHits.refuses(p, Arsenal.EYE_OF_THE_INSATIABLE.get()), "a mage wields the mage's divine");
        helper.assertFalse(ArsenalHits.refuses(p, Arsenal.HELLSOVEREIGN_FANG.get()), "mythics are not class bound");

        ItemStack stack = new ItemStack(ruin);
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        ArsenalAbilities.unleash(p, stack, ruin);
        helper.assertFalse(p.getCooldowns().isOnCooldown(ruin), "a refused signature does not fire");
        Zombie z = zombie(helper);
        float refused = hit(p, z, 10f);
        helper.assertTrue(refused <= 10f * ArsenalHits.REFUSED_DAMAGE + 0.01f, "refused hits deal half, got " + refused);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void absoluteZeroFreezesFoesAround(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        Zombie z = zombie(helper);
        SystemWeaponItem lance = Arsenal.WINTERBORNE_REQUIEM.get();
        ItemStack stack = new ItemStack(lance);
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        ArsenalAbilities.unleash(p, stack, lance);
        helper.assertTrue(p.getCooldowns().isOnCooldown(lance), "the signature goes on cooldown at once");
        helper.assertFalse(z.hasEffect(ModEffects.FROZEN), "nothing freezes before the lance strikes the ground");
        helper.runAfterDelay(com.beyondsolo.item.arsenal.Signature.ABSOLUTE_ZERO.impactTicks() + 2, () -> {
            helper.assertTrue(z.hasEffect(ModEffects.FROZEN), "the zombie is frozen solid on the strike frame");
            helper.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void staffsRaiseSpellPowerAndBowsHitHarder(GameTestHelper helper) {
        Zombie z = zombie(helper);
        z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Arsenal.STARSEER_STAFF.get()));
        helper.runAfterDelay(2, () -> {
            double sp = z.getAttributeValue(com.beyondsolo.combat.ModAttributes.SPELL_POWER);
            helper.assertTrue(Math.abs(sp - 0.45) < 0.001, "the Starseer's Staff gives +45% spell power, got " + sp);
            SystemBowItem divine = Arsenal.STARFALL_LONGBOW.get();
            SystemBowItem common = Arsenal.HUNTERS_SHORTBOW.get();
            helper.assertTrue(divine.damageMultiplier() > common.damageMultiplier(), "higher grade bows hit harder");
            helper.assertTrue(BuiltInRegistries.ITEM.getKey(divine).getPath().equals("starfall_longbow"), "bow registered");
            helper.succeed();
        });
    }
}
