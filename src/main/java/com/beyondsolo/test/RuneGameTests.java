package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.crafting.CraftingModule;
import com.beyondsolo.crafting.CraftingService;
import com.beyondsolo.crafting.SystemRecipes;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.economy.SellPrices;
import com.beyondsolo.economy.ShopCatalog;
import com.beyondsolo.enemy.MonsterKind;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.GateLoot;
import com.beyondsolo.job.JobAPI;
import com.beyondsolo.job.Subclass;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.rune.Engraving;
import com.beyondsolo.rune.RuneDrops;
import com.beyondsolo.rune.RuneStoneItem;
import com.beyondsolo.rune.Runes;
import com.beyondsolo.skill.RuneArts;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.SkillAPI;
import com.beyondsolo.skill.SkillEvent;
import com.beyondsolo.skill.Skills;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Rune stones, the rune arts and engravings. Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RuneGameTests {
    private static final String ARENA = "evaluation_zone";

    private static ServerPlayer hunter(GameTestHelper helper, PlayerRank rank, PlayerClass c) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "runecarver"));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        SkillAPI.reset(p);
        JobAPI.reset(p);
        Economy.reset(p);
        Runes.reset(p);
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        p.removeAllEffects();
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        QuestTestAccess.markStarted(p);
        SystemAPI.awaken(p, rank, c);
        // no lucky critical hits: the tests compare damage numbers
        var crit = p.getAttribute(dev.shadowsoffire.apothic_attributes.api.ALObjects.Attributes.CRIT_CHANCE);
        if (crit != null) {
            crit.removeModifiers();
            crit.setBaseValue(-10);
        }
        return p;
    }

    private static Zombie zombie(GameTestHelper helper, ServerPlayer p, BlockPos at) {
        Zombie z = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, at);
        z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);
        z.setHealth(2000);
        Vec3 d = z.getEyePosition().subtract(p.getEyePosition());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) (-(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG));
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.setYHeadRot(yaw);
        return z;
    }

    private static void refill(ServerPlayer p) {
        SystemAPI.restoreMana(p, 100_000);
        SkillAPI.resetCooldowns(p);
        SystemAPI.addExhaustion(p, -100);
    }

    private static Skill skill(String path) {
        Skill s = Skills.get(path);
        if (s == null) throw new IllegalStateException("no skill " + path);
        return s;
    }

    // ------------------------------------------------------------------ the rune arts

    @GameTest(template = ARENA)
    public static void runeArtsAreTheirOwnFamily(GameTestHelper helper) {
        List<Skill> arts = Skills.runeArts();
        helper.assertTrue(arts.size() == 14, "14 rune arts, found " + arts.size());
        for (Skill s : arts) {
            helper.assertTrue(s.rune() && s.owner() == null && s.subclass() == null && !s.advanced(), s.path() + " is a rune art for anyone");
            helper.assertTrue(s.element() != null, s.path() + " has an element");
            for (PlayerClass c : PlayerClass.values()) {
                helper.assertTrue(!Skills.forClass(c).contains(s), s.path() + " is no " + c + " skill");
                helper.assertTrue(s.fits(c, null), s.path() + " fits a " + c);
            }
        }
        helper.assertTrue(Skills.runeArts().stream().filter(Skill::castCircle).count() == 1, "only Sigil Cascade draws a circle");
        // every gate creature and boss carries one; the deep runes come from no creature
        Set<Skill> carried = new HashSet<>();
        for (MonsterKind k : MonsterKind.values()) {
            helper.assertTrue(RuneArts.of(k) != null, k + " carries a rune");
            carried.add(RuneArts.of(k));
        }
        for (BossKind k : BossKind.values()) {
            helper.assertTrue(RuneArts.of(k) != null, k + " carries a rune");
            carried.add(RuneArts.of(k));
        }
        for (Skill s : RuneArts.deep()) helper.assertTrue(!carried.contains(s), s.path() + " is a deep rune");
        helper.assertTrue(carried.size() + RuneArts.deep().size() == arts.size(), "every rune art comes from somewhere");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void classChangeKeepsRuneArts(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerRank.C, PlayerClass.WARRIOR);
        SkillAPI.learn(p, RuneArts.HOLLOW_LANCE, 3, SkillEvent.Source.RUNE);
        // a new class (awakening again) and a job change both sort the skills out again
        SystemAPI.awaken(p, PlayerRank.C, PlayerClass.MAGE);
        SkillAPI.refreshForClass(p);
        helper.assertTrue(SkillAPI.level(p, RuneArts.HOLLOW_LANCE) == 3, "a rune art stays through a new class");
        JobAPI.changeJob(p, Subclass.PYROMANCER);
        helper.assertTrue(SkillAPI.level(p, RuneArts.HOLLOW_LANCE) == 3, "and through a job change");
        helper.succeed();
    }

    // ------------------------------------------------------------------ absorbing

    @GameTest(template = ARENA)
    public static void absorbingTeachesThenRaises(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerRank.D, PlayerClass.WARRIOR);
        helper.assertTrue(Runes.absorb(p, Runes.of(RuneArts.HUNTERS_HOWL)), "the first stone takes");
        helper.assertTrue(SkillAPI.level(p, RuneArts.HUNTERS_HOWL) == 1, "and teaches the art at level 1");
        helper.assertTrue(Runes.absorb(p, Runes.of(RuneArts.HUNTERS_HOWL)), "a second one takes too");
        helper.assertTrue(SkillAPI.level(p, RuneArts.HUNTERS_HOWL) == 1 + Runes.RAISE_LEVELS, "and raises it by " + Runes.RAISE_LEVELS);
        helper.assertTrue(Runes.data(p).absorbed() == 2, "two stones absorbed");
        // a class rune of the player's own class
        Skill own = Skills.forClass(PlayerClass.WARRIOR).stream().filter(s -> s.owner() == PlayerClass.WARRIOR && s.grade().tier() <= PlayerRank.D.tier() && !SkillAPI.knows(p, s))
                .findFirst().orElseThrow();
        helper.assertTrue(Runes.absorb(p, Runes.of(own)), "a warrior's rune takes on a warrior");
        helper.assertTrue(SkillAPI.knows(p, own), "and teaches " + own.path());
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void absorbingRefusesWhatDoesNotFit(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerRank.E, PlayerClass.WARRIOR);
        // reach: an E-rank can take E and D stones, not C
        helper.assertTrue(Runes.refusal(p, Runes.of(RuneArts.HOLLOW_LANCE)) == Runes.Refusal.RANK, "a C stone is too strong for an E-rank");
        helper.assertTrue(Runes.refusal(p, Runes.of(RuneArts.CINDER_WISP)) == null, "a D stone is within reach");
        helper.assertTrue(Runes.rankNeeded(PlayerRank.S) == PlayerRank.A, "an S stone needs an A-rank");
        // another class's rune
        Skill mage = Skills.forClass(PlayerClass.MAGE).stream().filter(s -> s.owner() == PlayerClass.MAGE && s.grade() == PlayerRank.E).findFirst().orElseThrow();
        helper.assertTrue(Runes.refusal(p, Runes.of(mage)) == Runes.Refusal.CLASS, "a mage's rune won't answer a warrior");
        helper.assertTrue(!Runes.absorb(p, Runes.of(mage)) && !SkillAPI.knows(p, mage), "and teaches nothing");
        // mastered
        SkillAPI.learn(p, RuneArts.SKITTERING_BROOD, Skill.MAX_LEVEL, SkillEvent.Source.OTHER);
        helper.assertTrue(Runes.refusal(p, Runes.of(RuneArts.SKITTERING_BROOD)) == Runes.Refusal.MASTERED, "a mastered skill takes no more");
        // asleep
        ServerPlayer sleeper = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "sleeper"));
        SystemAPI.reset(sleeper);
        helper.assertTrue(Runes.refusal(sleeper, Runes.of(RuneArts.SKITTERING_BROOD)) == Runes.Refusal.ASLEEP, "the unawakened can't absorb");
        helper.assertTrue(Runes.data(p).absorbed() == 0, "nothing was absorbed");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void sealedStonesSettleOnSomethingThatFits(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerRank.C, PlayerClass.GUARDIAN);
        RandomSource r = RandomSource.create(5);
        for (PlayerRank grade : new PlayerRank[]{PlayerRank.E, PlayerRank.D, PlayerRank.C}) {
            for (int i = 0; i < 60; i++) {
                Skill s = Runes.roll(p, grade, r);
                helper.assertTrue(s != null, "a sealed " + grade + " stone settles on something");
                helper.assertTrue(s.fits(PlayerClass.GUARDIAN, null) && !s.advanced(), s.path() + " fits a guardian");
                helper.assertTrue(s.grade().tier() <= grade.tier(), s.path() + " is no higher than " + grade);
            }
        }
        int before = SkillAPI.get(p).known().size();
        helper.assertTrue(Runes.absorb(p, Runes.sealed(PlayerRank.C)), "a sealed stone takes");
        helper.assertTrue(SkillAPI.get(p).known().size() == before + 1
                || SkillAPI.get(p).known().size() == before, "it taught a skill or raised one");
        helper.assertTrue(Runes.data(p).absorbed() == 1, "one stone absorbed");
        // nothing left to give: every skill in reach mastered
        for (Runes.Pick k : Runes.pool(PlayerClass.GUARDIAN, null, SkillAPI.get(p), PlayerRank.E)) {
            SkillAPI.learn(p, k.skill(), Skill.MAX_LEVEL, SkillEvent.Source.OTHER);
            SkillAPI.addLevels(p, k.skill(), Skill.MAX_LEVEL);
        }
        helper.assertTrue(Runes.refusal(p, Runes.sealed(PlayerRank.E)) == Runes.Refusal.NOTHING, "an empty seal is refused");
        helper.succeed();
    }

    // ------------------------------------------------------------------ casting

    /** Every active rune art casts and acts on the creature in front, one after the other. */
    @GameTest(template = ARENA, timeoutTicks = 2400)
    public static void everyRuneArtActs(GameTestHelper helper) {
        List<Skill> queue = new ArrayList<>(Skills.runeArts().stream().filter(Skill::isActive).toList());
        helper.assertTrue(queue.size() == 11, "11 active rune arts, found " + queue.size());
        castNext(helper, queue, 0, new ArrayList<>());
    }

    private static void castNext(GameTestHelper helper, List<Skill> queue, int index, List<String> failed) {
        if (index >= queue.size()) {
            helper.assertTrue(failed.isEmpty(), "rune arts that failed: " + failed);
            helper.succeed();
            return;
        }
        Skill s = queue.get(index);
        ServerPlayer p = hunter(helper, PlayerRank.S, PlayerClass.WARRIOR);
        SkillAPI.learn(p, s, 1, SkillEvent.Source.RUNE);
        Zombie z = zombie(helper, p, new BlockPos(3, 2, 1));
        refill(p);
        boolean cast = SkillAPI.cast(p, s);
        if (!cast) failed.add(s.path() + " did not cast");
        // a leap lands late (a fake player never touches down: the leap gives up after 3 seconds)
        int wait = s == RuneArts.TITANS_FALL ? 75 : s == RuneArts.WIDOWS_SNARE || s == RuneArts.SIGIL_CASCADE ? 32 : 22;
        helper.runAfterDelay(wait, () -> {
            if (cast) {
                if (s == RuneArts.HUNTERS_HOWL) {
                    if (!p.hasEffect(MobEffects.MOVEMENT_SPEED) || !z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) failed.add(s.path() + " no howl");
                } else if (s == RuneArts.ASHEN_BULWARK) {
                    if (!SkillAPI.hasBuff(p, RuneArts.BUFF_BULWARK)) failed.add(s.path() + " no shield");
                } else if (s == RuneArts.DREAD_PRESENCE) {
                    if (!z.hasEffect(MobEffects.WEAKNESS)) failed.add(s.path() + " no dread");
                } else if (z.getHealth() >= 2000) {
                    failed.add(s.path() + " did no damage");
                }
            }
            z.discard();
            helper.runAfterDelay(4, () -> castNext(helper, queue, index + 1, failed));
        });
    }

    @GameTest(template = ARENA)
    public static void guardsBluntBlows(GameTestHelper helper) {
        // fake players can't be hurt, so the blows go straight to the damage event the guards listen to
        ServerPlayer p = hunter(helper, PlayerRank.C, PlayerClass.WARRIOR);
        Zombie z = zombie(helper, p, new BlockPos(3, 2, 1));
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400);
        p.setHealth(400);
        // a blow from the front, as it is
        float plain = incoming(p, p.damageSources().mobAttack(z), 20);
        helper.assertTrue(plain > 0, "the blow lands");
        // behind the ashen shield
        SkillAPI.learn(p, RuneArts.ASHEN_BULWARK, 1, SkillEvent.Source.RUNE);
        refill(p);
        helper.assertTrue(SkillAPI.cast(p, RuneArts.ASHEN_BULWARK), "the shield goes up");
        float zombieBefore = z.getHealth();
        float shielded = incoming(p, p.damageSources().mobAttack(z), 20);
        float expected = plain * (1 - RuneArts.bulwarkBlock(1) / 100f);
        helper.assertTrue(Math.abs(shielded - expected) < 0.05f, "the shield takes " + RuneArts.bulwarkBlock(1) + "%: " + shielded + " vs " + expected);
        helper.assertTrue(z.getHealth() < zombieBefore, "and throws some of it back");
        // a blow from behind gets past it
        Zombie behind = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 3));
        behind.moveTo(p.getX() - p.getLookAngle().x * 3, p.getY(), p.getZ() - p.getLookAngle().z * 3);
        helper.assertTrue(Math.abs(incoming(p, p.damageSources().mobAttack(behind), 20) - plain) < 0.05f, "not from behind");
        behind.discard();
        SkillAPI.clearBuff(p, RuneArts.BUFF_BULWARK);
        // Iron Resolve, only when low
        SkillAPI.learn(p, RuneArts.IRON_RESOLVE, 1, SkillEvent.Source.RUNE);
        p.setHealth(400);
        helper.assertTrue(Math.abs(incoming(p, p.damageSources().mobAttack(z), 20) - plain) < 0.05f, "no resolve at full health");
        p.setHealth(100);
        float low = incoming(p, p.damageSources().mobAttack(z), 20);
        float resolved = plain * (1 - RuneArts.resolveBlock(1) / 100f);
        helper.assertTrue(Math.abs(low - resolved) < 0.05f, "resolve takes " + RuneArts.resolveBlock(1) + "% when low: " + low + " vs " + resolved);
        // Crystal Carapace hardens the skin
        double armor = p.getAttributeValue(Attributes.ARMOR);
        SkillAPI.learn(p, RuneArts.CRYSTAL_CARAPACE, 5, SkillEvent.Source.RUNE);
        helper.assertTrue(p.getAttributeValue(Attributes.ARMOR) > armor + 2.9, "carapace armour");
        helper.succeed();
    }

    /** What is left of a blow after the damage event's listeners (the rune guards among them). */
    private static float incoming(ServerPlayer p, DamageSource source, float amount) {
        LivingIncomingDamageEvent event = new LivingIncomingDamageEvent(p, new DamageContainer(source, amount));
        NeoForge.EVENT_BUS.post(event);
        return event.isCanceled() ? 0 : event.getAmount();
    }

    // ------------------------------------------------------------------ engravings

    @GameTest(template = ARENA)
    public static void engravingsChangeTheirSkill(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerRank.C, PlayerClass.WARRIOR);
        Skill s = RuneArts.HOLLOW_LANCE;
        Skill passive = RuneArts.CRYSTAL_CARAPACE;
        SkillAPI.learn(p, s, 4, SkillEvent.Source.RUNE);
        SkillAPI.learn(p, passive, 1, SkillEvent.Source.RUNE);
        int cd = Runes.cooldown(p, s, 4);
        float mana = Runes.manaCost(p, s, 4);
        helper.assertTrue(cd == s.cooldown(4) && mana == s.manaCost(4), "plain numbers before");

        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Engraving.HASTE.item(), 2));
        helper.assertTrue(!Runes.engrave(p, 0, passive.path()), "a passive takes no engraving");
        helper.assertTrue(!Runes.engrave(p, 0, "war_cry"), "nor a skill the player doesn't know");
        helper.assertTrue(p.getMainHandItem().getCount() == 2, "refusals use nothing up");
        helper.assertTrue(Runes.engrave(p, 0, s.path()), "haste goes on");
        helper.assertTrue(p.getMainHandItem().getCount() == 1, "the rune is used up");
        helper.assertTrue(Runes.cooldown(p, s, 4) == Math.round(s.cooldown(4) * Engraving.HASTE.cooldown), "shorter cooldown");
        helper.assertTrue(!Runes.engrave(p, 0, s.path()), "the same engraving twice is refused");

        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Engraving.THRIFT.item()));
        helper.assertTrue(Runes.engrave(p, 0, s.path()), "thrift replaces haste");
        helper.assertTrue(Runes.engraving(p, s) == Engraving.THRIFT, "one engraving per skill");
        helper.assertTrue(Runes.cooldown(p, s, 4) == s.cooldown(4), "the haste is gone");
        helper.assertTrue(Math.abs(Runes.manaCost(p, s, 4) - s.manaCost(4) * Engraving.THRIFT.mana) < 1e-4, "cheaper");
        // a cast pays the engraved cost
        zombie(helper, p, new BlockPos(3, 2, 1));
        refill(p);
        float before = SystemAPI.get(p).mana();
        helper.assertTrue(SkillAPI.cast(p, s), "cast");
        float spent = before - SystemAPI.get(p).mana();
        helper.assertTrue(Math.abs(spent - s.manaCost(4) * Engraving.THRIFT.mana) < 0.6f, "paid the thrifty price, paid " + spent);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void mightHitsHarder(GameTestHelper helper) {
        Skill s = RuneArts.HOLLOW_LANCE;
        ServerPlayer plain = hunter(helper, PlayerRank.C, PlayerClass.MAGE);
        SkillAPI.learn(plain, s, 1, SkillEvent.Source.RUNE);
        Zombie a = zombie(helper, plain, new BlockPos(3, 2, 1));
        refill(plain);
        helper.assertTrue(SkillAPI.cast(plain, s), "cast");
        float base = 2000 - a.getHealth();
        a.discard();
        ServerPlayer mighty = hunter(helper, PlayerRank.C, PlayerClass.MAGE);
        SkillAPI.learn(mighty, s, 1, SkillEvent.Source.RUNE);
        mighty.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Engraving.MIGHT.item()));
        helper.assertTrue(Runes.engrave(mighty, 0, s.path()), "might goes on");
        helper.runAfterDelay(25, () -> {
            Zombie b = zombie(helper, mighty, new BlockPos(3, 2, 1));
            refill(mighty);
            helper.assertTrue(SkillAPI.cast(mighty, s), "cast again");
            float hit = 2000 - b.getHealth();
            helper.assertTrue(base > 0 && Math.abs(hit / base - Engraving.MIGHT.damage) < 0.03f,
                    "might multiplies the hit: " + hit + " vs " + base);
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ where they come from and go

    @GameTest(template = ARENA)
    public static void runeDropsFollowTheirRules(GameTestHelper helper) {
        RandomSource r = RandomSource.create(9);
        helper.assertTrue(Math.abs(RuneDrops.reach(0.04f, PlayerRank.B, PlayerRank.D) - 0.01f) < 1e-6, "two grades above: a quarter of the chance");
        helper.assertTrue(RuneDrops.reach(0.04f, PlayerRank.D, PlayerRank.B) == 0.04f, "below the creature's rank: as is");
        for (int i = 0; i < 40; i++) {
            List<ItemStack> runes = RuneDrops.bossRunes(BossKind.WYRM, PlayerRank.S, true, r);
            helper.assertTrue(runes.stream().anyMatch(s -> Runes.isSealed(s) && Runes.gradeOf(s) == PlayerRank.S), "an ascended boss always leaves a sealed rune");
            for (ItemStack s : runes) {
                Skill k = Runes.skillOf(s);
                helper.assertTrue(k == null || k == RuneArts.WYRMFIRE_ROAR || RuneArts.deep().contains(k), "only its own art or a deep rune");
            }
        }
        for (int i = 0; i < 40; i++) {
            for (ItemStack s : RuneDrops.bossRunes(BossKind.BRUTE, PlayerRank.C, false, r)) {
                Skill k = Runes.skillOf(s);
                helper.assertTrue(k == null || k == RuneArts.TITANS_FALL, "no deep runes below rank " + RuneDrops.DEEP_FROM);
            }
        }
        // the gate's reward cache gets them too
        List<ItemStack> loot = new ArrayList<>();
        NeoForge.EVENT_BUS.post(new GateLoot.BossLootEvent(helper.getLevel(), BossKind.MAGUS, PlayerRank.A, true, loot));
        helper.assertTrue(loot.stream().anyMatch(Runes::isRune), "the boss cache holds a rune");
        // creatures outside the gate roster leave none
        Zombie z = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 2, 1));
        for (int i = 0; i < 50; i++) helper.assertTrue(RuneDrops.creatureRunes(z, r).isEmpty(), "a zombie carries no rune");
        // class runes: class skills only
        for (int i = 0; i < 50; i++) {
            Skill k = RuneDrops.classSkill(PlayerRank.B, r);
            helper.assertTrue(k != null && k.owner() != null && k.subclass() == null && !k.rune() && k.grade().tier() <= PlayerRank.B.tier(),
                    "a class rune holds a class skill");
        }
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void runesSellCraftAndShop(GameTestHelper helper) {
        // selling
        for (PlayerRank g : PlayerRank.values()) {
            helper.assertTrue(SellPrices.of(Runes.sealed(g)) == Runes.SELL[g.tier()], g + " stone sells for " + Runes.SELL[g.tier()]);
        }
        helper.assertTrue(SellPrices.of(Runes.of(RuneArts.WYRMFIRE_ROAR)) == Runes.SELL[PlayerRank.S.tier()], "an inscribed stone by its grade");
        helper.assertTrue(SellPrices.of(new ItemStack(Engraving.MIGHT.item())) == Runes.ENGRAVING_SELL, "engraving runes");
        // the shop
        helper.assertTrue(ShopCatalog.get("sealed_rune") != null, "the shop sells a sealed rune");
        for (Engraving e : Engraving.values()) helper.assertTrue(ShopCatalog.get("rune_of_" + e.id()) != null, "and the rune of " + e.id());
        // names
        helper.assertTrue(Runes.of(RuneArts.TITANS_FALL).getItem() instanceof RuneStoneItem, "a rune stone");
        helper.assertTrue(Runes.glyphOf(Runes.sealed(PlayerRank.E)) == Runes.SEALED_GLYPH, "a sealed stone shows the seal");
        helper.assertTrue(Runes.glyphOf(Runes.of(RuneArts.CINDER_WISP)) == RuneArts.CINDER_WISP.element().ordinal(), "an inscribed one its element");
        // crafting: fusing takes sealed stones only, never inscribed ones
        ServerPlayer p = hunter(helper, PlayerRank.C, PlayerClass.WARRIOR);
        Economy.set(p, 10_000);
        helper.assertTrue(SystemRecipes.of(SystemRecipes.Category.RUNES).size() == 1 + 5 + Engraving.values().length, "the RUNES page");
        p.getInventory().add(Runes.of(RuneArts.HUNTERS_HOWL));
        p.getInventory().add(Runes.of(RuneArts.CINDER_WISP));
        p.getInventory().add(Runes.sealed(PlayerRank.D));
        p.getInventory().add(new ItemStack(CraftingModule.CORES.get(PlayerRank.C.tier()).get(), 2));
        p.getInventory().add(new ItemStack(CraftingModule.MANA_STONE.get(), 2));
        helper.assertTrue(CraftingService.craft(p, "fuse_rune_d", 1) == 0, "two inscribed stones don't count");
        p.getInventory().add(Runes.sealed(PlayerRank.D));
        p.getInventory().add(Runes.sealed(PlayerRank.D));
        helper.assertTrue(CraftingService.craft(p, "fuse_rune_d", 1) == 1, "three sealed ones fuse");
        int sealedC = 0, inscribed = 0, sealedD = 0;
        for (ItemStack s : p.getInventory().items) {
            if (!Runes.isRune(s)) continue;
            if (Runes.isSealed(s) && Runes.gradeOf(s) == PlayerRank.C) sealedC += s.getCount();
            else if (Runes.isSealed(s)) sealedD += s.getCount();
            else inscribed += s.getCount();
        }
        helper.assertTrue(sealedC == 1 && sealedD == 0 && inscribed == 2, "a sealed C stone, the inscribed ones kept: " + sealedC + "/" + sealedD + "/" + inscribed);
        helper.succeed();
    }

    private RuneGameTests() {}
}
