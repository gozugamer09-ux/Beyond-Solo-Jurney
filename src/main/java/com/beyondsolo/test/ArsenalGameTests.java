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
import com.beyondsolo.item.arsenal.Signature;
import com.beyondsolo.registry.ModItems;
import com.beyondsolo.skill.SkillFeel;
import com.beyondsolo.skill.SkillFeelTestAccess;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

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
        // the monarch grade is armor only (monarch armor, from slain monarchs)
        for (ItemGrade g : ItemGrade.values()) {
            if (g != ItemGrade.MONARCH) helper.assertTrue(!Arsenal.ofGrade(g).isEmpty(), "no weapons of grade " + g);
        }
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
        helper.assertTrue(high.contains(ItemGrade.MYTHIC), "S gates can drop mythic weapons");
        // about 150 of 5000 (3%): it was about 400 (8%) before the top grades were made scarce
        helper.assertTrue(mythicOrBetter > 80 && mythicOrBetter < 240, "mythic and divine stay scarce in S gates: " + mythicOrBetter);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void divineWeaponsStayRareWhereverTheyDrop(GameTestHelper helper) {
        RandomSource r = RandomSource.create(7);
        int n = 100_000;
        int cacheDivine = 0, aDivine = 0, threatDivine = 0, threatMythic = 0;
        for (int i = 0; i < n; i++) {
            if (GateLoot.rollGrade(PlayerRank.S.tier(), r) == ItemGrade.DIVINE) cacheDivine++;
            if (GateLoot.rollGrade(PlayerRank.A.tier(), r) == ItemGrade.DIVINE) aDivine++;
            ItemGrade g = GateLoot.rollGreatThreatGrade(r);
            if (g == ItemGrade.DIVINE) threatDivine++;
            if (g == ItemGrade.MYTHIC) threatMythic++;
            // creatures never drop the top grades; an ascended boss lifts its roll only up to legendary
            helper.assertTrue(GateLoot.capped(GateLoot.rollGrade(PlayerRank.S.tier(), r), ItemGrade.LEGENDARY).tier() <= ItemGrade.LEGENDARY.tier(),
                    "capped rolls stay at or under legendary");
        }
        // S caches: 3 in 1,000 (it was 2 in 100); A caches: 5 in 10,000; the great threats: 8 in 1,000, mythic 42 in 1,000
        helper.assertTrue(cacheDivine > 220 && cacheDivine < 380, "an S cache's weapon is divine about 3 in 1,000: " + cacheDivine + " of " + n);
        helper.assertTrue(aDivine < 100, "an A cache almost never holds a divine: " + aDivine + " of " + n);
        helper.assertTrue(threatDivine > 680 && threatDivine < 920, "a great threat's weapon is divine about 8 in 1,000: " + threatDivine + " of " + n);
        helper.assertTrue(threatMythic > 3850 && threatMythic < 4550, "a great threat's weapon is mythic about 42 in 1,000: " + threatMythic + " of " + n);
        int ascendedTop = 0;
        for (int i = 0; i < 20_000; i++) {
            ItemGrade g = GateLoot.ascendedGrade(PlayerRank.S.tier(), r);
            helper.assertTrue(g.atLeast(ItemGrade.LEGENDARY), "an ascended S boss's extra weapon is at least legendary: " + g);
            if (g.atLeast(ItemGrade.MYTHIC)) ascendedTop++;
        }
        helper.assertTrue(ascendedTop < 900, "an ascended boss no longer lifts legendaries into mythics: " + ascendedTop + " of 20000");
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

    @GameTest(template = ARENA)
    public static void bowStringCreaksOncePerDraw(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        SystemBowItem bow = Arsenal.HUNTERS_SHORTBOW.get();
        ItemStack stack = new ItemStack(bow);
        int full = bow.getUseDuration(stack, p);
        int[] creaks = {0};
        Consumer<PlayLevelSoundEvent.AtPosition> listener = e -> {
            if (e.getLevel() == helper.getLevel() && e.getSound() != null
                    && e.getSound().value() == SoundEvents.CROSSBOW_LOADING_START.value()) creaks[0]++;
        };
        NeoForge.EVENT_BUS.addListener(PlayLevelSoundEvent.AtPosition.class, listener);
        try {
            for (int used = 0; used < 3; used++) bow.onUseTick(helper.getLevel(), p, stack, full - used);
            helper.assertTrue(creaks[0] == 0, "a tap on the button stays silent, heard " + creaks[0]);
            for (int used = 3; used <= 40; used++) bow.onUseTick(helper.getLevel(), p, stack, full - used);
            helper.assertTrue(creaks[0] == 1, "the string creaks once as the draw begins, heard " + creaks[0]);
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }

    /** Every weapon whose art can be unleashed or drawn, the arsenal's and the awakening trial's legendaries. */
    private static Map<Signature, ArsenalWeapon> artWeapons() {
        Map<Signature, ArsenalWeapon> out = new EnumMap<>(Signature.class);
        List<Item> items = new ArrayList<>();
        for (var item : Arsenal.all()) items.add(item.get());
        for (var item : ModItems.LEGENDARIES) items.add(item.get());
        for (Item item : items) {
            if (item instanceof ArsenalWeapon w && w.signature() != null) out.put(w.signature(), w);
        }
        return out;
    }

    /**
     * The top grades carry arts of their own grade: every legendary (the arsenal's and the trial's), mythic and divine
     * weapon has one, no art is shared, the drawn arts sit on bows and the right-click ones on everything else.
     */
    @GameTest(template = ARENA)
    public static void everyTopWeaponCarriesAnArtOfItsGrade(GameTestHelper helper) {
        Map<Signature, Integer> uses = new EnumMap<>(Signature.class);
        List<Item> items = new ArrayList<>();
        for (var item : Arsenal.all()) items.add(item.get());
        for (var item : ModItems.LEGENDARIES) items.add(item.get());
        for (Item item : items) {
            ArsenalWeapon w = (ArsenalWeapon) item;
            String name = BuiltInRegistries.ITEM.getKey(item).getPath();
            Signature sig = w.signature();
            if (!w.baseGrade().atLeast(ItemGrade.LEGENDARY)) {
                helper.assertTrue(sig == null, name + " is below legendary and has no art");
                continue;
            }
            helper.assertTrue(sig != null, name + " (" + w.baseGrade() + ") carries an art");
            helper.assertTrue(sig.grade() == w.baseGrade(), name + "'s art " + sig + " is of its own grade, " + w.baseGrade());
            helper.assertTrue(sig.drawn() == (item instanceof SystemBowItem), name + ": drawn arts belong to bows and only to bows");
            helper.assertTrue(sig.active() ? sig.impactTicks() > 0 : sig.drawTicks() > 0, sig + " has a strike frame or a draw to gather");
            uses.merge(sig, 1, Integer::sum);
        }
        for (Signature sig : Signature.values()) {
            helper.assertTrue(uses.getOrDefault(sig, 0) == 1, sig + " belongs to exactly one weapon, found " + uses.getOrDefault(sig, 0));
        }
        helper.succeed();
    }

    /**
     * Every right-click art, each in a batch of its own (their reach would cross into a neighbour's arena): it goes on
     * cooldown at once, nothing lands before its strike frame, and it reaches a zombie three blocks ahead by its last
     * beat.
     */
    @GameTestGenerator
    public static List<TestFunction> everyArtLandsOnItsStrikeFrame() {
        List<TestFunction> out = new ArrayList<>();
        for (Signature sig : Signature.values()) {
            if (!sig.active()) continue;
            out.add(new TestFunction("beyondsolo_art_" + sig.key(), "arsenalgametests.art_" + sig.key(), BeyondSolo.MOD_ID + ":" + ARENA,
                    200, 0, true, helper -> artLands(helper, sig)));
        }
        return out;
    }

    private static void artLands(GameTestHelper helper, Signature sig) {
        ArsenalWeapon w = artWeapons().get(sig);
        helper.assertTrue(w != null, "a weapon carries " + sig);
        ServerPlayer p = player(helper);
        SystemAPI.awaken(p, PlayerRank.S, w.favoredClass() != null ? w.favoredClass() : PlayerClass.WARRIOR);
        // face down the arena, along +z
        p.setYRot(0);
        p.setYHeadRot(0);
        p.setXRot(0);
        ItemStack stack = new ItemStack((Item) w);
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Zombie z = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 4));
        z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);
        z.setHealth(2000);
        BalanceLab.steady(z);
        int effects = z.getActiveEffects().size();
        ArsenalAbilities.unleash(p, stack, w);
        helper.assertTrue(p.getCooldowns().isOnCooldown(stack.getItem()), sig + " goes on cooldown at once");
        helper.assertTrue(z.getHealth() == 2000, sig + " lands nothing as it is unleashed");
        if (sig.impactTicks() > 1) {
            helper.runAfterDelay(sig.impactTicks() - 1, () -> helper.assertTrue(z.getHealth() == 2000 && z.getActiveEffects().size() == effects,
                    sig + " lands nothing before its strike frame (tick " + sig.impactTicks() + ")"));
        }
        helper.runAfterDelay(sig.impactTicks() + 24, () -> {
            helper.assertTrue(z.getHealth() < 2000, sig + " reaches a zombie three blocks ahead");
            helper.succeed();
        });
    }

    /**
     * Colossus Stance: for eight seconds the bulwark counts every blow that got past the guardian's armour, the share
     * its own resistance and shield of light kept off included, then throws a share of it back at what stands near.
     */
    @GameTest(template = ARENA, timeoutTicks = 260)
    public static void colossusBulwarkThrowsBackWhatItTook(GameTestHelper helper) {
        BalanceLab.Hunter p = BalanceLab.hunter(helper, PlayerClass.GUARDIAN, 60, false);
        SystemWeaponItem hammer = Arsenal.COLOSSUS_AEGIS.get();
        ItemStack stack = new ItemStack(hammer);
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BalanceLab.wear(p);
        BlockPos near = new BlockPos(3, 2, 1);
        Zombie z = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, near);
        z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(4000);
        z.setHealth(4000);
        BalanceLab.steady(z);
        BalanceLab.steady(p);
        ArsenalAbilities.unleash(p, stack, hammer);
        helper.assertTrue(ArsenalAbilities.bulwarkTaken(p) < 0, "no bulwark before the hammer lands");
        int impact = Signature.COLOSSUS_STANCE.impactTicks();
        float[] afterSlam = {0};
        helper.runAfterDelay(impact + 2, () -> {
            helper.assertTrue(z.getHealth() < 4000, "the hammer of light hits what stands close");
            helper.assertTrue(ArsenalAbilities.bulwarkTaken(p) == 0, "the bulwark stands, nothing taken yet");
            float hp = p.getHealth();
            for (int i = 0; i < 3; i++) {
                p.invulnerableTime = 0;
                p.hurt(z.damageSources().mobAttack(z), 10f);
            }
            float taken = ArsenalAbilities.bulwarkTaken(p);
            helper.assertTrue(taken > 6f, "the bulwark counts the blows its shield of light kept off too: " + taken);
            helper.assertTrue(p.getHealth() > hp - 15f, "while it stands the guardian shrugs most of it off: " + hp + " -> " + p.getHealth());
            // the slam threw it back: bring it in close again for the retribution
            z.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            BlockPos at = helper.absolutePos(near);
            z.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            afterSlam[0] = z.getHealth();
        });
        helper.runAfterDelay(impact + 160 + 4, () -> {
            helper.assertTrue(ArsenalAbilities.bulwarkTaken(p) < 0, "the bulwark broke outward");
            helper.assertTrue(z.getHealth() < afterSlam[0], "its retribution hit the zombie: " + afterSlam[0] + " -> " + z.getHealth());
            BalanceLab.release(p);
            helper.succeed();
        });
    }

    /**
     * A drawn art gathers only on a full draw: a short draw looses a plain arrow, a full one sends the gale with it.
     * The art keeps its own cooldown, apart from the bow's, so the bow goes on shooting.
     */
    @GameTest(template = ARENA)
    public static void drawnArtNeedsAFullDrawAndKeepsItsOwnCooldown(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        p.setYRot(0);
        p.setYHeadRot(0);
        p.setXRot(0);
        SystemBowItem bow = Arsenal.GALEBREAKER_WARBOW.get();
        Signature sig = Signature.GALE_LANCE;
        ItemStack stack = new ItemStack(bow);
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        p.getInventory().add(new ItemStack(Items.ARROW, 32));
        int full = bow.getUseDuration(stack, p);
        helper.assertTrue(ArsenalAbilities.drawReady(p, sig), "the art starts ready");
        bow.releaseUsing(stack, helper.getLevel(), p, full - (sig.drawTicks() - 6));
        helper.assertTrue(ArsenalAbilities.drawReady(p, sig), "a short draw looses a plain arrow and keeps the art");
        Zombie z = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 4));
        z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);
        z.setHealth(2000);
        BalanceLab.steady(z);
        bow.releaseUsing(stack, helper.getLevel(), p, full - (sig.drawTicks() + 2));
        helper.assertFalse(ArsenalAbilities.drawReady(p, sig), "a full draw sends the art and starts its cooldown");
        int left = ArsenalAbilities.drawCooldownLeft(p, sig);
        helper.assertTrue(left >= sig.cooldownSeconds() - 1 && left <= sig.cooldownSeconds(), "the art's own cooldown: " + left + " s");
        helper.assertFalse(p.getCooldowns().isOnCooldown(bow), "the bow itself keeps shooting");
        helper.assertTrue(z.getHealth() < 2000, "the gale drove through the zombie ahead");
        // /solo heal and /solo reset cooldowns make every art ready again, drawn or not
        p.getCooldowns().addCooldown(Arsenal.RUINBRINGER.get(), 400);
        ArsenalAbilities.resetCooldowns(p);
        helper.assertTrue(ArsenalAbilities.drawReady(p, sig), "a cooldown reset readies the drawn art");
        helper.assertFalse(p.getCooldowns().isOnCooldown(Arsenal.RUINBRINGER.get()), "a cooldown reset readies the weapons' arts");
        helper.succeed();
    }

    /**
     * An art's blows land the way a class skill's do (SkillFeel): a divine art's at the heaviest weight, a legendary's
     * solid; a flurry's next blow on the same creature only sparks, but the blow the art builds to lands in full.
     */
    @GameTest(template = ARENA)
    public static void artBlowsLandWithTheSkillsFeel(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        Zombie divine = sturdy(helper, new BlockPos(1, 2, 4));
        Zombie flurry = sturdy(helper, new BlockPos(3, 2, 4));
        Zombie barrage = sturdy(helper, new BlockPos(5, 2, 4));
        ArsenalHits.artHit(Signature.CATACLYSM_CLEAVE, p, divine, 4f);
        ArsenalHits.artHit(Signature.STORMBREAK, p, flurry, 4f);
        ArsenalHits.artHit(Signature.TITANS_BARRAGE, p, barrage, 4f);
        int big = SkillFeelTestAccess.pending(p, divine), solid = SkillFeelTestAccess.pending(p, flurry);
        helper.assertTrue(divine.getHealth() < 2000, "the art's blow did its damage");
        helper.assertTrue(big == SkillFeel.MASSIVE, "a divine art's blow weighs in at the heaviest: " + big);
        helper.assertTrue(solid == SkillFeel.MEDIUM, "a legendary art's blow is solid: " + solid);
        helper.runAfterDelay(1, () -> {
            ArsenalHits.artHit(Signature.STORMBREAK, p, flurry, 4f);
            ArsenalHits.artFinisher(Signature.TITANS_BARRAGE, p, barrage, 4f);
            int again = SkillFeelTestAccess.pending(p, flurry), finish = SkillFeelTestAccess.pending(p, barrage);
            helper.assertTrue((again & 0xFF) == SkillFeel.LIGHT && ((again >> 8) & SkillFeel.REPEAT) != 0,
                    "a flurry's next blow on the same creature only sparks: " + again);
            helper.assertTrue(finish == SkillFeel.MEDIUM, "the blow the art builds to lands in full: " + finish);
            helper.succeed();
        });
    }

    private static Zombie sturdy(GameTestHelper helper, BlockPos at) {
        Zombie z = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, at);
        z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);
        z.setHealth(2000);
        BalanceLab.steady(z);
        return z;
    }
}
