package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.armory.ArmorCatalog;
import com.beyondsolo.armory.ArmorDrops;
import com.beyondsolo.armory.ArmoryModule;
import com.beyondsolo.armory.DivinePassives;
import com.beyondsolo.armory.SetPerks;
import com.beyondsolo.combat.ModAttributes;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.enemy.Affinity;
import com.beyondsolo.enemy.Enemies;
import com.beyondsolo.enemy.EnemyModule;
import com.beyondsolo.enemy.EnemyTraits;
import com.beyondsolo.enemy.GateMonsterEntity;
import com.beyondsolo.enemy.MonsterKind;
import com.beyondsolo.enemy.Variant;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.GateLoot;
import com.beyondsolo.gear.BossLoot;
import com.beyondsolo.gear.BossSets;
import com.beyondsolo.gear.GearTooltip;
import com.beyondsolo.item.ArmorSets;
import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.item.SystemArmorItem;
import com.beyondsolo.item.arsenal.Arsenal;
import com.beyondsolo.item.arsenal.ArsenalWeapon;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.rune.Runes;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.Skills;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.Progression;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import com.mojang.authlib.GameProfile;
import dev.shadowsoffire.apothic_attributes.api.ALObjects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The armory: its class sets, levels and attunement, drops, set perks and the divine weapons' passives. */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArmoryGameTests {
    private static final String ARENA = "evaluation_zone";
    private static final BlockPos SPOT = new BlockPos(1, 2, 1);
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static ServerPlayer hunter(GameTestHelper helper, PlayerClass cls) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "armorer"));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        Economy.reset(p);
        p.getInventory().clearContent();
        for (EquipmentSlot slot : SLOTS) p.setItemSlot(slot, ItemStack.EMPTY);
        p.setGameMode(GameType.SURVIVAL);
        BlockPos start = helper.absolutePos(SPOT);
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        QuestTestAccess.markStarted(p);
        SystemAPI.awaken(p, PlayerRank.C, cls);
        return p;
    }

    private static void levelTo(ServerPlayer p, int level) {
        for (int i = 0; i < 2 * level + 20 && SystemAPI.get(p).level() < level; i++) {
            SystemData d = SystemAPI.get(p);
            SystemAPI.grantXp(p, Math.max(1, Progression.xpToNext(d.level()) - d.xp()), SystemAPI.XP_SOURCE_COMMAND);
        }
    }

    private static List<DeferredItem<SystemArmorItem>> pieces(ArmorSets.Set set) {
        List<DeferredItem<SystemArmorItem>> pieces = ArmoryModule.piecesOf(set);
        if (pieces == null || pieces.size() != 4) throw new IllegalStateException("no four pieces for " + set.name);
        return pieces;
    }

    private static void wear(LivingEntity e, ArmorSets.Set set, int count) {
        List<DeferredItem<SystemArmorItem>> pieces = pieces(set);
        for (int i = 0; i < 4; i++) e.setItemSlot(SLOTS[i], i < count ? new ItemStack(pieces.get(i).get()) : ItemStack.EMPTY);
        ArmorSets.refresh(e);
    }

    private static Zombie zombie(GameTestHelper helper, BlockPos at) {
        Zombie z = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, at);
        z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400);
        z.setHealth(400);
        // no random crits or dodges in the numbers below
        zero(z, ALObjects.Attributes.CRIT_CHANCE);
        zero(z, ALObjects.Attributes.DODGE_CHANCE);
        return z;
    }

    private static void zero(LivingEntity e, Holder<Attribute> attribute) {
        AttributeInstance inst = e.getAttribute(attribute);
        if (inst != null) inst.setBaseValue(0);
    }

    /** What a hit of 10 comes to once every listener has had its say. */
    private static float incoming(LivingEntity target, DamageSource source) {
        LivingIncomingDamageEvent event = new LivingIncomingDamageEvent(target, new DamageContainer(source, 10f));
        NeoForge.EVENT_BUS.post(event);
        return event.isCanceled() ? 0 : event.getAmount();
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 0.02f * Math.max(1f, Math.abs(b));
    }

    private static String key(Component c) {
        return c != null && c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
    }

    // ------------------------------------------------------------------ the catalogue

    @GameTest(template = ARENA)
    public static void everyClassHasASetAtEveryRank(GameTestHelper helper) {
        helper.assertTrue(ArmorCatalog.ALL.size() == 39, "39 armory sets: " + ArmorCatalog.ALL.size());
        helper.assertTrue(ArmoryModule.all().size() == 156, "four pieces each");
        List<ArmorSets.Set> sets = ArmorCatalog.classSets();
        helper.assertTrue(sets.size() == 45, "the armory's 39 and the brute's six are all class sets: " + sets.size());
        for (PlayerClass cls : PlayerClass.values()) {
            for (int tier = 0; tier <= 5; tier++) {
                final int t = tier;
                helper.assertTrue(sets.stream().anyMatch(s -> s.playerClass == cls && s.tier == t), cls + " has a set at rank " + PlayerRank.values()[t]);
            }
            long divine = ArmorCatalog.divineSets().stream().filter(s -> s.playerClass == cls).count();
            helper.assertTrue(divine == 1, cls + " has one divine set");
            ArmorSets.Set d = ArmorCatalog.divine(cls);
            helper.assertTrue(d != null && d.grade == ItemGrade.DIVINE && d.requiredLevel == 60 && d.perk != null, cls + " divine set: divine grade, level 60, a perk");
            helper.assertTrue(sets.stream().filter(s -> s.playerClass == cls).count() >= 7, cls + " has at least seven sets");
        }
        for (MonsterKind kind : MonsterKind.values()) {
            helper.assertTrue(!ArmorCatalog.forCreature(kind).isEmpty(), kind + " has a set of its own");
        }
        for (BossKind kind : BossKind.values()) {
            helper.assertTrue(!ArmorCatalog.forBoss(kind).isEmpty(), kind + " has a set of its own");
            for (PlayerRank rank : PlayerRank.values()) {
                if (rank.tier() < kind.minRank.tier() || rank.tier() > kind.maxRank.tier() || kind == BossKind.BRUTE) continue;
                helper.assertTrue(ArmorDrops.pick(ArmorCatalog.forBoss(kind), rank, null, RandomSource.create(1)) != null,
                        kind + " at " + rank + " has a set to leave");
            }
        }
        helper.assertTrue(ArmorCatalog.villagerSets().size() == 6, "a starter set per class from the villages");
        Set<PlayerClass> starters = EnumSet.noneOf(PlayerClass.class);
        for (ArmorSets.Set s : ArmorCatalog.villagerSets()) {
            starters.add(s.playerClass);
            helper.assertTrue(s.perk == null && s.grade == ItemGrade.COMMON && s.requiredLevel == 1, s.name + ": plain, common, level 1");
        }
        helper.assertTrue(starters.size() == 6, "one starter set for each class");
        for (ArmorCatalog.Def def : ArmorCatalog.ALL) {
            ArmorSets.Set s = def.set();
            helper.assertTrue(s.requiredLevel == ArmorSets.levelFor(s.tier), s.name + " needs its rank's level");
            helper.assertTrue(s.source != null && s.playerClass != null, s.name + " has a class and a source");
            if (!s.source.startsWith("villager:")) helper.assertTrue(s.perk != null, s.name + " has a perk");
            helper.assertTrue(!s.two().isEmpty() && !s.bonus().isEmpty(), s.name + " has 2- and 4-piece bonuses");
            for (Map<Holder<Attribute>, Double> m : List.of(s.two(), s.bonus())) {
                m.forEach((a, v) -> helper.assertTrue(v > 0 && v <= 3.0, s.name + " bonuses stay small: " + v));
            }
            for (DeferredItem<SystemArmorItem> piece : pieces(s)) {
                ItemStack stack = new ItemStack(piece.get());
                helper.assertTrue(piece.get().set() == s, piece.getId() + " belongs to " + s.name);
                helper.assertTrue(ItemGrade.of(stack) == s.grade, piece.getId() + " has its set's grade");
                if (s.divine()) {
                    helper.assertTrue(stack.has(DataComponents.UNBREAKABLE) && stack.getMaxStackSize() == 1, piece.getId() + " is unbreakable");
                }
            }
        }
        // boss and divine pieces get called out in a reward cache, creature pieces don't
        ItemStack bossPiece = new ItemStack(pieces(ArmorCatalog.OATHBOUND).get(0).get());
        ItemStack divinePiece = new ItemStack(pieces(ArmorCatalog.SKYWARDEN).get(1).get());
        ItemStack creaturePiece = new ItemStack(pieces(ArmorCatalog.MOONMAW).get(2).get());
        helper.assertTrue(BossLoot.isSignature(bossPiece, PlayerRank.A) && BossLoot.isSignature(divinePiece, PlayerRank.S), "boss and divine pieces are signature loot");
        helper.assertTrue(!BossLoot.isSignature(creaturePiece, PlayerRank.S), "creature pieces are not");
        helper.assertTrue(key(GearTooltip.source(ArmorCatalog.GLOOMSTALKER)).equals("tooltip.beyondsolo.set_source.creature"), "creature sets say who drops them");
        helper.assertTrue(key(GearTooltip.source(ArmorCatalog.INKWEAVE)).equals("tooltip.beyondsolo.set_source.villager.inkweave"), "village sets say who hands them out");
        helper.assertTrue(key(GearTooltip.source(ArmorCatalog.ARCHMAGUS)).equals("gear.beyondsolo.signature_of"), "boss sets name their boss");
        helper.succeed();
    }

    // ------------------------------------------------------------------ levels and attunement

    @GameTest(template = ARENA)
    public static void levelsGateWhoCanWearASet(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerClass.WARRIOR);
        helper.assertTrue(SystemAPI.get(p).level() < 10, "a fresh hunter");
        ItemStack starter = new ItemStack(pieces(ArmorCatalog.FORGEHAND).get(0).get());
        ItemStack dRank = new ItemStack(pieces(ArmorCatalog.FANGCREST).get(0).get());
        helper.assertTrue(ArmorSets.refusal(p, ArmorCatalog.FORGEHAND) == null, "the starter set goes on at once");
        helper.assertTrue(starter.getItem().canEquip(starter, EquipmentSlot.HEAD, p), "the starter helm fits");
        helper.assertTrue(key(ArmorSets.refusal(p, ArmorCatalog.FANGCREST)).equals("message.beyondsolo.armor_level"), "a D-rank set needs level 10");
        helper.assertTrue(!dRank.getItem().canEquip(dRank, EquipmentSlot.HEAD, p), "the D-rank helm won't go on");
        // right-clicking it on is refused too
        p.setItemInHand(InteractionHand.MAIN_HAND, dRank.copy());
        dRank.getItem().use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
        helper.assertTrue(p.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "no helm after a refused right click");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        // put on by other means, it comes off again on the next check
        p.setItemSlot(EquipmentSlot.HEAD, dRank.copy());
        p.tickCount = Math.floorMod(5 - p.getId(), 20);
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(p));
        helper.assertTrue(p.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "the helm is taken off");
        helper.assertTrue(p.getInventory().contains(dRank), "and put in the bag");
        // creative players wear anything
        p.setGameMode(GameType.CREATIVE);
        helper.assertTrue(ArmorSets.refusal(p, ArmorCatalog.WORLDBREAKER) == null, "creative ignores the rules");
        p.setGameMode(GameType.SURVIVAL);
        // at level 10 it fits
        levelTo(p, 10);
        helper.assertTrue(SystemAPI.get(p).level() >= 10, "levelled to 10: " + SystemAPI.get(p).level());
        helper.assertTrue(ArmorSets.refusal(p, ArmorCatalog.FANGCREST) == null && dRank.getItem().canEquip(dRank, EquipmentSlot.HEAD, p), "now it fits");
        helper.assertTrue(ArmorSets.refusal(p, ArmorCatalog.WYRMLORD) != null, "S-rank sets still need level 50");
        // divine sets: level 60 and their own class only
        ServerPlayer mage = hunter(helper, PlayerClass.MAGE);
        levelTo(mage, 60);
        helper.assertTrue(SystemAPI.get(mage).level() >= 60, "levelled to 60: " + SystemAPI.get(mage).level());
        helper.assertTrue(key(ArmorSets.refusal(mage, ArmorCatalog.WORLDBREAKER)).equals("message.beyondsolo.armor_bound"), "a warrior's divine set refuses a mage");
        helper.assertTrue(ArmorSets.refusal(mage, ArmorCatalog.STARWEAVE) == null, "the mage's own divine set fits at 60");
        helper.assertTrue(ArmorSets.refusal(mage, ArmorCatalog.FANGCREST) == null, "other classes' plain sets can be worn (without their bonuses)");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void onlyTheSetsClassDrawsOutItsBonuses(GameTestHelper helper) {
        ServerPlayer warrior = hunter(helper, PlayerClass.WARRIOR);
        ServerPlayer mage = hunter(helper, PlayerClass.MAGE);
        double spell0 = warrior.getAttributeValue(ModAttributes.SPELL_POWER);
        wear(warrior, ArmorCatalog.INKWEAVE, 2);
        wear(mage, ArmorCatalog.INKWEAVE, 2);
        helper.assertTrue(Math.abs(warrior.getAttributeValue(ModAttributes.SPELL_POWER) - spell0) < 1e-6, "a warrior in a mage's robe gets nothing");
        helper.assertTrue(Math.abs(mage.getAttributeValue(ModAttributes.SPELL_POWER) - (spell0 + 0.04)) < 1e-6, "the mage gets +4% spell power");
        wear(warrior, ArmorCatalog.INKWEAVE, 4);
        wear(mage, ArmorCatalog.INKWEAVE, 4);
        helper.assertTrue(ArmorSets.fullSet(warrior) == null && ArmorSets.fullSet(mage) == ArmorCatalog.INKWEAVE, "the full set counts for the mage only");
        helper.assertTrue(near(ModAttributes.manaFactor(mage), 0.95f) && near(ModAttributes.manaFactor(warrior), 1f), "Inkweave makes skills 5% cheaper for a mage");
        Skill s = Skills.EMBER_BURST;
        helper.assertTrue(near(Runes.manaCost(mage, s, 3), s.manaCost(3) * 0.95f), "and casting costs less");
        // skill haste: a healer in the chapel's vestments
        ServerPlayer healer = hunter(helper, PlayerClass.HEALER);
        wear(healer, ArmorCatalog.CHAPELWARD, 4);
        helper.assertTrue(near(ModAttributes.hasteFactor(healer), 0.97f), "Chapelward: 3% skill haste");
        helper.assertTrue(Runes.cooldown(healer, s, 3) == Math.max(1, Math.round(s.cooldown(3) * ModAttributes.hasteFactor(healer))), "shorter cooldowns");
        // caps
        healer.getAttribute(ModAttributes.SKILL_HASTE).setBaseValue(0.9);
        healer.getAttribute(ModAttributes.MANA_EFFICIENCY).setBaseValue(0.9);
        helper.assertTrue(near(ModAttributes.hasteFactor(healer), 1 - ModAttributes.MAX_SKILL_HASTE)
                && near(ModAttributes.manaFactor(healer), 1 - ModAttributes.MAX_MANA_EFFICIENCY), "haste and efficiency are capped");
        // taking pieces off takes the bonus away
        wear(mage, ArmorCatalog.INKWEAVE, 1);
        helper.assertTrue(Math.abs(mage.getAttributeValue(ModAttributes.SPELL_POWER) - spell0) < 1e-6, "one piece: no bonus left");
        // creatures draw out anything
        Zombie z = zombie(helper, new BlockPos(3, 2, 3));
        wear(z, ArmorCatalog.HOLLOW_HIERARCH, 4);
        helper.assertTrue(ArmorSets.fullSet(z) == ArmorCatalog.HOLLOW_HIERARCH, "a zombie wears any set to the full");
        helper.succeed();
    }

    // ------------------------------------------------------------------ drops

    @GameTest(template = ARENA)
    public static void piecesDropSlowlyFromTheirOwnCreaturesAndBosses(GameTestHelper helper) {
        RandomSource r = RandomSource.create(7);
        // weights: the gate's rank, one rank either side at half, never two above; the killer's class first
        ArmorSets.Set duskfang = ArmorCatalog.DUSKFANG;
        helper.assertTrue(ArmorDrops.weight(duskfang, PlayerRank.C, null) == 1f, "own rank");
        helper.assertTrue(ArmorDrops.weight(duskfang, PlayerRank.D, null) == 0.5f && ArmorDrops.weight(duskfang, PlayerRank.B, null) == 0.5f, "one rank off");
        helper.assertTrue(ArmorDrops.weight(duskfang, PlayerRank.E, null) == 0f, "two ranks above the gate: never");
        helper.assertTrue(ArmorDrops.weight(duskfang, PlayerRank.C, PlayerClass.ASSASSIN) == ArmorDrops.CLASS_WEIGHT, "the killer's class comes up more");
        int dusk = 0;
        for (int i = 0; i < 1000; i++) if (ArmorDrops.pick(ArmorCatalog.forCreature(MonsterKind.GLOOMFANG), PlayerRank.C, PlayerClass.ASSASSIN, r) == duskfang) dusk++;
        helper.assertTrue(dusk > 750, "an assassin mostly finds the assassin's gloomfang set: " + dusk);
        for (MonsterKind kind : MonsterKind.values()) {
            for (PlayerRank rank : PlayerRank.values()) {
                for (int i = 0; i < 40; i++) {
                    ArmorSets.Set s = ArmorDrops.pick(ArmorCatalog.forCreature(kind), rank, null, r);
                    helper.assertTrue(s == null || s.tier <= rank.tier() + 1, kind + " at " + rank + " never leaves a set two ranks up");
                }
            }
        }
        // creatures: rare, more from elites and champions, always their own kind's sets
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        List<GateMonsterEntity> spawned = new ArrayList<>();
        GateMonsterEntity plain = (GateMonsterEntity) Enemies.spawn(helper.getLevel(), PlayerRank.D, EnemyModule.type(MonsterKind.GLOOMFANG), at, EnemyTraits.NONE, -1);
        GateMonsterEntity champion = (GateMonsterEntity) Enemies.spawn(helper.getLevel(), PlayerRank.D, EnemyModule.type(MonsterKind.SHARDBACK), at,
                new EnemyTraits(Affinity.NONE, Variant.CHAMPION, 0), -1);
        spawned.add(plain);
        spawned.add(champion);
        int plainDrops = 0, championDrops = 0;
        for (int i = 0; i < 6000; i++) {
            for (ItemStack s : ArmorDrops.creatureDrops(plain, PlayerRank.D, null, r)) {
                plainDrops++;
                helper.assertTrue(s.getItem() instanceof SystemArmorItem a && ArmorCatalog.forCreature(MonsterKind.GLOOMFANG).contains(a.set()), "a gloomfang leaves gloomfang sets");
            }
        }
        for (int i = 0; i < 1000; i++) {
            for (ItemStack s : ArmorDrops.creatureDrops(champion, PlayerRank.D, null, r)) {
                championDrops++;
                helper.assertTrue(s.getItem() instanceof SystemArmorItem a && ArmorCatalog.forCreature(MonsterKind.SHARDBACK).contains(a.set()), "a shardback leaves shardback sets");
            }
        }
        helper.assertTrue(plainDrops >= 12 && plainDrops <= 70, "plain creatures: about 0.6%: " + plainDrops + " in 6000");
        helper.assertTrue(championDrops >= 60 && championDrops <= 145, "champions: about 10%: " + championDrops + " in 1000");
        spawned.forEach(m -> m.discard());
        // bosses: about half the time in an E gate, always when ascended, only their own sets
        int brute = 0;
        for (int i = 0; i < 1000; i++) {
            for (ItemStack s : ArmorDrops.bossPieces(BossKind.BRUTE, PlayerRank.E, false, r)) {
                brute++;
                helper.assertTrue(s.getItem() instanceof SystemArmorItem a && a.set() == BossSets.forRank(PlayerRank.E), "the E brute leaves its own set");
            }
        }
        helper.assertTrue(brute >= 420 && brute <= 580, "an E boss leaves a piece about half the time: " + brute);
        for (BossKind kind : BossKind.values()) {
            for (PlayerRank rank : PlayerRank.values()) {
                if (rank.tier() < kind.minRank.tier() || rank.tier() > kind.maxRank.tier()) continue;
                for (int i = 0; i < 10; i++) {
                    List<ItemStack> loot = ArmorDrops.bossPieces(kind, rank, true, r);
                    helper.assertTrue(!loot.isEmpty(), "an ascended " + kind + " always leaves a piece");
                    for (ItemStack s : loot) {
                        helper.assertTrue(s.getItem() instanceof SystemArmorItem a && (ArmorCatalog.forBoss(kind).contains(a.set()) || a.set().divine()),
                                kind + " leaves its own sets (or a divine piece)");
                    }
                }
            }
        }
        // divine pieces: about 1% from S bosses, never below A
        int divineS = 0, divineB = 0;
        for (int i = 0; i < 5000; i++) {
            if (!ArmorDrops.divinePiece(PlayerRank.S, false, r).isEmpty()) divineS++;
            if (!ArmorDrops.divinePiece(PlayerRank.B, true, r).isEmpty()) divineB++;
        }
        helper.assertTrue(divineS >= 20 && divineS <= 90 && divineB == 0, "divine pieces: ~1% at S, none at B: " + divineS + "/" + divineB);
        // the reward cache gets the boss's pieces
        List<ItemStack> cache = new ArrayList<>();
        NeoForge.EVENT_BUS.post(new GateLoot.BossLootEvent(helper.getLevel(), BossKind.KNIGHT, PlayerRank.A, true, cache));
        helper.assertTrue(cache.stream().anyMatch(s -> s.getItem() instanceof SystemArmorItem a && ArmorCatalog.forBoss(BossKind.KNIGHT).contains(a.set())),
                "an ascended knight's cache holds a knight set piece");
        // village requests: now and then the craft's own starter set
        int quest = 0;
        for (int i = 0; i < 2000; i++) {
            ItemStack s = ArmorDrops.questPiece("weaponsmith", false, r);
            if (s.isEmpty()) continue;
            quest++;
            helper.assertTrue(s.getItem() instanceof SystemArmorItem a && a.set() == ArmorCatalog.FORGEHAND, "a weaponsmith pays in Forgehand");
        }
        helper.assertTrue(quest >= 170 && quest <= 320, "about one request in eight pays a piece: " + quest);
        helper.succeed();
    }

    // ------------------------------------------------------------------ set perks

    @GameTest(template = ARENA)
    public static void setPerksChangeTheHitsTheyShould(GameTestHelper helper) {
        // Tower Guard: less from the front, nothing from behind
        Zombie guard = zombie(helper, new BlockPos(2, 2, 2));
        Zombie foe = zombie(helper, new BlockPos(2, 2, 4));
        guard.setYRot(0);
        guard.setYBodyRot(0);
        guard.setYHeadRot(0);
        DamageSource fromFoe = foe.damageSources().mobAttack(foe);
        float plain = incoming(guard, fromFoe);
        wear(guard, ArmorCatalog.ASHEN_WATCH, 4);
        SetPerks.reset(guard);
        float front = incoming(guard, fromFoe);
        helper.assertTrue(near(front, plain * (1 - SetPerks.TOWER_GUARD)), "Tower Guard: 20% less from the front: " + plain + " -> " + front);
        guard.setYRot(180);
        guard.setYBodyRot(180);
        guard.setYHeadRot(180);
        helper.assertTrue(near(incoming(guard, fromFoe), plain), "nothing off from behind");
        // Dragon's Blood: harder hits under half health
        Zombie wyrm = zombie(helper, new BlockPos(4, 2, 2));
        Zombie target = zombie(helper, new BlockPos(4, 2, 4));
        DamageSource fromWyrm = wyrm.damageSources().mobAttack(wyrm);
        float base = incoming(target, fromWyrm);
        wear(wyrm, ArmorCatalog.WYRMLORD, 4);
        SetPerks.reset(wyrm);
        helper.assertTrue(near(incoming(target, fromWyrm), base), "at full health: no change");
        wyrm.setHealth(wyrm.getMaxHealth() * 0.4f);
        helper.assertTrue(near(incoming(target, fromWyrm), base * (1 + SetPerks.DRAGONS_BLOOD)), "under half health: 20% harder");
        // Warlord's Fury: every kill a stack, +6% each
        Zombie warlord = zombie(helper, new BlockPos(6, 2, 2));
        Zombie victim = zombie(helper, new BlockPos(6, 2, 4));
        DamageSource fromWarlord = warlord.damageSources().mobAttack(warlord);
        float calm = incoming(victim, fromWarlord);
        wear(warlord, ArmorCatalog.WORLDBREAKER, 4);
        SetPerks.reset(warlord);
        for (int i = 0; i < 3; i++) NeoForge.EVENT_BUS.post(new LivingDeathEvent(zombie(helper, new BlockPos(5, 2, 5)), fromWarlord));
        helper.assertTrue(SetPerks.stacks(warlord, "fury") == 3, "three kills, three stacks");
        helper.assertTrue(near(incoming(victim, fromWarlord), calm * (1 + 3 * SetPerks.FURY_PER_STACK)), "+18% with three stacks");
        // perks need the full set: three pieces do nothing
        wear(warlord, ArmorCatalog.WORLDBREAKER, 3);
        helper.assertTrue(ArmorSets.fullSet(warlord) == null && near(incoming(victim, fromWarlord), calm), "three pieces: no perk");
        helper.succeed();
    }

    // ------------------------------------------------------------------ divine weapons

    @GameTest(template = ARENA)
    public static void divineWeaponsHaveTheirOwnPassives(GameTestHelper helper) {
        // one passive per divine weapon, all different
        Set<DivinePassives.Passive> seen = new HashSet<>();
        for (Item item : List.of(Arsenal.RUINBRINGER.get(), Arsenal.CALAMITY_FANG.get(), Arsenal.EYE_OF_THE_INSATIABLE.get(),
                Arsenal.COLOSSUS_AEGIS.get(), Arsenal.WORLD_TREE_BRANCH.get(), Arsenal.STARFALL_LONGBOW.get())) {
            DivinePassives.Passive p = DivinePassives.Passive.of((ArsenalWeapon) item);
            helper.assertTrue(p != null && seen.add(p), item + " has a passive of its own");
        }
        helper.assertTrue(DivinePassives.Passive.of((ArsenalWeapon) Arsenal.THUNDERFANG_LONGSWORD.get()) == null, "lesser weapons have none");

        // Unyielding: a killing blow leaves the Aegis's bearer at 1 health, once
        Zombie bearer = zombie(helper, new BlockPos(2, 2, 2));
        bearer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Arsenal.COLOSSUS_AEGIS.get()));
        SetPerks.reset(bearer);
        LivingDeathEvent death = new LivingDeathEvent(bearer, bearer.damageSources().generic());
        NeoForge.EVENT_BUS.post(death);
        helper.assertTrue(death.isCanceled() && bearer.getHealth() == 1f && bearer.hasEffect(MobEffects.DAMAGE_RESISTANCE), "Unyielding holds the bearer up");

        // Vanishing Edge: a kill hides the bearer and the next strike hits 80% harder
        Zombie assassin = zombie(helper, new BlockPos(4, 2, 2));
        Zombie mark = zombie(helper, new BlockPos(4, 2, 4));
        assassin.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Arsenal.CALAMITY_FANG.get()));
        SetPerks.reset(assassin);
        DamageSource fromAssassin = assassin.damageSources().mobAttack(assassin);
        float before = incoming(mark, fromAssassin);
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(zombie(helper, new BlockPos(5, 2, 5)), fromAssassin));
        helper.assertTrue(assassin.hasEffect(MobEffects.INVISIBILITY), "a kill hides the assassin");
        helper.assertTrue(near(incoming(mark, fromAssassin), before * (1 + DivinePassives.VANISHING_STRIKE)), "the next strike hits 80% harder");
        helper.assertTrue(near(incoming(mark, fromAssassin), before), "only the one strike");

        // Insatiable: kills feed it
        Zombie caster = zombie(helper, new BlockPos(6, 2, 2));
        caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Arsenal.EYE_OF_THE_INSATIABLE.get()));
        SetPerks.reset(caster);
        for (int i = 0; i < 3; i++) NeoForge.EVENT_BUS.post(new LivingDeathEvent(zombie(helper, new BlockPos(6, 2, 5)), caster.damageSources().mobAttack(caster)));
        helper.assertTrue(SetPerks.stacks(caster, "hunger") == 3, "three kills fed: " + SetPerks.stacks(caster, "hunger"));

        // Ruin: the fifth melee hit detonates
        ServerPlayer warrior = hunter(helper, PlayerClass.WARRIOR);
        warrior.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Arsenal.RUINBRINGER.get()));
        Zombie dummy = zombie(helper, new BlockPos(1, 2, 3));
        DamageSource swing = warrior.damageSources().playerAttack(warrior);
        for (int i = 0; i < DivinePassives.RUIN_MARKS - 1; i++) NeoForge.EVENT_BUS.post(new LivingDamageEvent.Post(dummy, new DamageContainer(swing, 2f)));
        helper.assertTrue(DivinePassives.ruinMarks(dummy) == DivinePassives.RUIN_MARKS - 1, "four marks: " + DivinePassives.ruinMarks(dummy));
        float hp = dummy.getHealth();
        NeoForge.EVENT_BUS.post(new LivingDamageEvent.Post(dummy, new DamageContainer(swing, 2f)));
        helper.assertTrue(DivinePassives.ruinMarks(dummy) == 0 && dummy.getHealth() < hp, "the fifth detonates: " + hp + " -> " + dummy.getHealth());
        // a mage can't draw it out
        ServerPlayer mage = hunter(helper, PlayerClass.MAGE);
        mage.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Arsenal.RUINBRINGER.get()));
        helper.assertTrue(DivinePassives.held(mage) == null && DivinePassives.held(warrior) == DivinePassives.Passive.RUIN, "only the warrior wakes Ruin");
        helper.succeed();
    }
}
