package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.Buildups;
import com.beyondsolo.combat.Statuses;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.SkillAPI;
import com.beyondsolo.skill.SkillData;
import com.beyondsolo.skill.SkillEvent;
import com.beyondsolo.skill.SkillTomeItem;
import com.beyondsolo.skill.Skills;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.Stat;
import com.beyondsolo.system.SystemAPI;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Skill system tests. Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SkillGameTests {
    private static final String ARENA = "evaluation_zone";
    private static final BlockPos SPOT = new BlockPos(3, 2, 1);

    private static ServerPlayer player(GameTestHelper helper, PlayerRank rank, PlayerClass playerClass) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "caster"));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        SkillAPI.reset(p);
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        p.removeAllEffects();
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        if (rank != null) {
            QuestTestAccess.markStarted(p);
            SystemAPI.awaken(p, rank, playerClass);
        }
        return p;
    }

    /** A zombie two blocks from the player, with the player turned to face it. */
    private static Zombie zombieInFront(GameTestHelper helper, ServerPlayer p) {
        Zombie z = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, SPOT);
        Vec3 d = z.getEyePosition().subtract(p.getEyePosition());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) (-(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG));
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.setYHeadRot(yaw);
        return z;
    }

    private static void refill(ServerPlayer p) {
        SystemAPI.restoreMana(p, 10000);
        SkillAPI.resetCooldowns(p);
        SystemAPI.addExhaustion(p, -100);
    }

    @GameTest(template = ARENA)
    public static void awakeningTeachesStarterSkills(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E, PlayerClass.WARRIOR);
        helper.assertTrue(SkillAPI.knows(p, Skills.SURGE_STEP), "everyone starts with Surge Step");
        helper.assertTrue(SkillAPI.knows(p, Skills.RENDING_ARC), "warriors start with Rending Arc");
        helper.assertTrue(!SkillAPI.knows(p, Skills.WAR_CRY), "War Cry comes later");
        helper.assertTrue(!SkillAPI.knows(p, Skills.ARC_BOLT), "no mage skills for a warrior");
        SkillData data = SkillAPI.get(p);
        helper.assertTrue(data.slotOf(Skills.RENDING_ARC.path()) >= 0 && data.slotOf(Skills.SURGE_STEP.path()) >= 0, "new actives go on the bar");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void higherRanksUnlockEarlier(GameTestHelper helper) {
        ServerPlayer e = player(helper, PlayerRank.E, PlayerClass.MAGE);
        ServerPlayer s = player(helper, PlayerRank.S, PlayerClass.MAGE);
        helper.assertTrue(!SkillAPI.knows(e, Skills.EMBER_BURST), "E rank waits for level 6");
        helper.assertTrue(SkillAPI.knows(s, Skills.EMBER_BURST), "S rank has Ember Burst from the start");
        helper.assertTrue(Skills.EMBER_BURST.unlockLevel(PlayerRank.C) == 4, "C rank unlocks 2 levels earlier");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void levelUpsUnlockSkillsAndGrowPassives(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E, PlayerClass.GUARDIAN);
        helper.assertTrue(!SkillAPI.knows(p, Skills.PROVOKE), "not yet");
        for (int i = 0; i < 60 && SystemAPI.get(p).level() < 12; i++) SystemAPI.grantXp(p, 2000, SystemAPI.XP_SOURCE_COMMAND);
        helper.assertTrue(SystemAPI.get(p).level() >= 12, "should be level 12 by now");
        helper.assertTrue(SkillAPI.knows(p, Skills.PROVOKE), "Provoke at 6");
        helper.assertTrue(SkillAPI.knows(p, Skills.STONESKIN), "Stoneskin at 10");
        helper.assertTrue(SkillAPI.knows(p, Skills.UNBROKEN_WILL), "Unbroken Will at 5");
        helper.assertTrue(SkillAPI.knows(p, Skills.PHANTOM_SHROUD), "Phantom Shroud at 12");
        helper.assertTrue(!SkillAPI.knows(p, Skills.AEGIS_DOME), "advanced skills never come from levels");
        helper.assertTrue(SkillAPI.level(p, Skills.UNBROKEN_WILL) > 1, "passives grow with the player's level");
        helper.assertTrue(p.getAttributeValue(Attributes.ARMOR) >= 0.5, "Stoneskin adds armor");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void castingSpendsManaAndStartsCooldown(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E, PlayerClass.WARRIOR);
        float before = SystemAPI.get(p).mana();
        helper.assertTrue(SkillAPI.cast(p, Skills.RENDING_ARC), "cast should work");
        helper.assertTrue(SystemAPI.get(p).mana() == before - Skills.RENDING_ARC.manaCost(1), "mana is spent");
        long left = SkillAPI.get(p).cooldownLeft(Skills.RENDING_ARC.path(), p.level().getGameTime());
        helper.assertTrue(left == Skills.RENDING_ARC.cooldown(1), "cooldown started: " + left);
        helper.assertTrue(!SkillAPI.cast(p, Skills.RENDING_ARC), "no cast while recharging");
        helper.assertTrue(!SkillAPI.cast(p, Skills.WAR_CRY), "can't cast what you don't know");
        SkillAPI.resetCooldowns(p);
        SystemAPI.consumeMana(p, SystemAPI.get(p).mana());
        helper.assertTrue(!SkillAPI.cast(p, Skills.RENDING_ARC), "no cast without mana");
        helper.assertTrue(SkillAPI.get(p).cooldownLeft(Skills.RENDING_ARC.path(), p.level().getGameTime()) == 0, "a refused cast starts no cooldown");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void collapsedPlayersCannotCast(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E, PlayerClass.WARRIOR);
        SystemAPI.addExhaustion(p, 100);
        helper.assertTrue(SystemAPI.get(p).isCollapsed(), "collapsed");
        helper.assertTrue(!SkillAPI.cast(p, Skills.RENDING_ARC), "no skills while collapsed");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void rendingArcHitsWhatIsInFront(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E, PlayerClass.WARRIOR);
        Zombie front = zombieInFront(helper, p);
        Zombie behind = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 3));
        // turn so that "behind" really is behind
        Vec3 toBehind = behind.position().subtract(p.position()).normalize();
        helper.assertTrue(toBehind.dot(p.getLookAngle()) < 0.3, "second zombie should be out of the arc");
        SkillAPI.cast(p, Skills.RENDING_ARC);
        helper.assertTrue(front.getHealth() < front.getMaxHealth(), "the zombie in front is hit");
        helper.assertTrue(behind.getHealth() == behind.getMaxHealth(), "the zombie behind is not");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void skillPowerGrowsWithLevelRankAndStats(GameTestHelper helper) {
        ServerPlayer weak = player(helper, PlayerRank.E, PlayerClass.MAGE);
        Zombie a = zombieInFront(helper, weak);
        SkillAPI.cast(weak, Skills.ARC_BOLT);
        float lostWeak = a.getMaxHealth() - a.getHealth();
        a.discard();

        ServerPlayer strong = player(helper, PlayerRank.A, PlayerClass.MAGE);
        SkillAPI.addLevels(strong, Skills.ARC_BOLT, 4);
        Zombie b = zombieInFront(helper, strong);
        b.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        b.setHealth(200);
        SkillAPI.cast(strong, Skills.ARC_BOLT);
        float lostStrong = 200 - b.getHealth();
        helper.assertTrue(lostWeak > 0, "bolt should hit: " + lostWeak);
        helper.assertTrue(lostStrong > lostWeak * 1.8f, "level 5 at A rank hits much harder: " + lostWeak + " vs " + lostStrong);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void usingASkillLevelsItUp(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E, PlayerClass.WARRIOR);
        int need = Skill.proficiencyToNext(1);
        for (int i = 0; i < need; i++) {
            refill(p);
            helper.assertTrue(SkillAPI.cast(p, Skills.RENDING_ARC), "cast " + i);
        }
        helper.assertTrue(SkillAPI.level(p, Skills.RENDING_ARC) == 2, "level 2 after " + need + " casts");
        helper.assertTrue(Skills.RENDING_ARC.cooldown(2) < Skills.RENDING_ARC.cooldown(1), "cooldown shrinks with level");
        SkillAPI.addLevels(p, Skills.RENDING_ARC, 50);
        helper.assertTrue(SkillAPI.level(p, Skills.RENDING_ARC) == Skill.MAX_LEVEL, "capped at max level");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void passivesChangeAttributesAndMana(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E, PlayerClass.MAGE);
        double armor = p.getAttributeValue(Attributes.ARMOR);
        float maxMana = SystemAPI.get(p).maxMana();
        SkillAPI.learn(p, Skills.STONESKIN, 4, SkillEvent.Source.COMMAND);
        SkillAPI.learn(p, Skills.ARCANE_FONT, 5, SkillEvent.Source.COMMAND);
        helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ARMOR) - armor - 2.0) < 0.001, "Stoneskin 4 = +2 armor");
        helper.assertTrue(SystemAPI.get(p).maxMana() == maxMana + 40, "Arcane Font 5 = +40 max mana");
        SkillAPI.forget(p, Skills.STONESKIN);
        SkillAPI.forget(p, Skills.ARCANE_FONT);
        helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ARMOR) - armor) < 0.001, "armor back to normal");
        helper.assertTrue(SystemAPI.get(p).maxMana() == maxMana, "max mana back to normal");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void tomesTeachFittingSkills(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E, PlayerClass.HEALER);
        int known = SkillAPI.get(p).known().size();
        helper.assertTrue(SkillTomeItem.read(p, SkillTomeItem.unread()), "an unread tome teaches something");
        helper.assertTrue(SkillAPI.get(p).known().size() == known + 1, "one new skill");
        for (Skill s : Skills.known(SkillAPI.get(p))) {
            helper.assertTrue(s.fits(PlayerClass.HEALER) && !s.advanced(), s.path() + " should fit a healer and not be advanced");
        }
        helper.assertTrue(!SkillTomeItem.read(p, SkillTomeItem.of(Skills.RENDING_ARC)), "a warrior tome is no use to a healer");
        helper.assertTrue(!SkillTomeItem.read(p, SkillTomeItem.of(Skills.SANCTUARY)), "advanced skills never come from tomes");
        ItemStack named = SkillTomeItem.of(Skills.MENDING_LIGHT);
        helper.assertTrue(SkillTomeItem.skillOf(named) == Skills.MENDING_LIGHT, "the tome remembers its skill");
        helper.assertTrue(SkillTomeItem.read(p, named), "a tome for a known skill raises it");
        helper.assertTrue(SkillAPI.level(p, Skills.MENDING_LIGHT) == 2, "Mending Light is level 2 now");
        // unread tomes run dry only when everything is mastered
        for (int i = 0; i < 200; i++) SkillTomeItem.read(p, SkillTomeItem.unread());
        helper.assertTrue(!SkillTomeItem.read(p, SkillTomeItem.unread()), "nothing left to learn");
        ServerPlayer asleep = player(helper, null, null);
        helper.assertTrue(!SkillTomeItem.read(asleep, SkillTomeItem.unread()), "no tomes before the evaluation");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void emberBurstBurnsAndMarkAmplifies(GameTestHelper helper) {
        ServerPlayer mage = player(helper, PlayerRank.E, PlayerClass.MAGE);
        SkillAPI.learn(mage, Skills.EMBER_BURST, 1, SkillEvent.Source.COMMAND);
        Zombie z = zombieInFront(helper, mage);
        SkillAPI.cast(mage, Skills.EMBER_BURST);
        helper.assertTrue(z.getHealth() < z.getMaxHealth(), "burst damage");
        helper.assertTrue(Buildups.get(z, Statuses.BURN) > 0, "burn buildup");
        z.discard();

        ServerPlayer ranger = player(helper, PlayerRank.E, PlayerClass.RANGER);
        SkillAPI.learn(ranger, Skills.HUNTERS_MARK, 5, SkillEvent.Source.COMMAND);
        Zombie plain = zombieInFront(helper, ranger);
        plain.hurt(ranger.damageSources().magic(), 4);
        float normal = plain.getMaxHealth() - plain.getHealth();
        plain.setHealth(plain.getMaxHealth());
        plain.invulnerableTime = 0;
        helper.assertTrue(SkillAPI.cast(ranger, Skills.HUNTERS_MARK), "mark the zombie in sight");
        plain.hurt(ranger.damageSources().magic(), 4);
        float marked = plain.getMaxHealth() - plain.getHealth();
        helper.assertTrue(marked > normal * 1.2f, "marked creatures take more: " + normal + " vs " + marked);
        plain.discard();
        refill(ranger);
        ranger.setXRot(-89);
        helper.assertTrue(!SkillAPI.cast(ranger, Skills.HUNTERS_MARK), "no target, no cast");
        helper.assertTrue(SkillAPI.get(ranger).cooldownLeft(Skills.HUNTERS_MARK.path(), ranger.level().getGameTime()) == 0, "and no cooldown");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void phantomShroudHidesFromMonsters(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E, PlayerClass.ASSASSIN);
        SkillAPI.learn(p, Skills.PHANTOM_SHROUD, 1, SkillEvent.Source.COMMAND);
        Zombie z = zombieInFront(helper, p);
        z.setTarget(p);
        helper.assertTrue(z.getTarget() == p, "zombie is after the player");
        helper.assertTrue(SkillAPI.cast(p, Skills.PHANTOM_SHROUD), "cast");
        helper.assertTrue(z.getTarget() == null, "the zombie lost its target");
        z.setTarget(p);
        helper.assertTrue(z.getTarget() == null, "and can't pick the player up again");
        // attacking breaks the shroud
        z.hurt(p.damageSources().playerAttack(p), 1);
        z.setTarget(p);
        helper.assertTrue(z.getTarget() == p, "after attacking, the player is seen again");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void shadowstepEmpowersTheNextStrike(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E, PlayerClass.ASSASSIN);
        Zombie z = zombieInFront(helper, p);
        z.getAttribute(Attributes.ARMOR).setBaseValue(0);
        Vec3 before = p.position();
        helper.assertTrue(SkillAPI.cast(p, Skills.SHADOWSTEP), "cast");
        helper.assertTrue(p.position().distanceTo(before) > 1, "the player moved");
        helper.assertTrue(p.distanceTo(z) < 3, "and stands by the zombie");
        z.hurt(p.damageSources().playerAttack(p), 4);
        float first = z.getMaxHealth() - z.getHealth();
        z.setHealth(z.getMaxHealth());
        z.invulnerableTime = 0;
        z.hurt(p.damageSources().playerAttack(p), 4);
        float second = z.getMaxHealth() - z.getHealth();
        helper.assertTrue(first > second * 1.2f, "only the first strike is empowered: " + first + " vs " + second);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void healerSkillsHealAndCleanse(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E, PlayerClass.HEALER);
        SkillAPI.learn(p, Skills.PURIFY, 1, SkillEvent.Source.COMMAND);
        p.setHealth(6);
        SkillAPI.cast(p, Skills.MENDING_LIGHT);
        helper.assertTrue(p.getHealth() >= 11, "healed by 5: " + p.getHealth());
        Buildups.add(p, Statuses.VENOM, 20, null);
        Buildups.add(p, Buildups.BLOOD, 50, null);
        Statuses.applyHeavyVenom(p, null);
        SkillAPI.cast(p, Skills.PURIFY);
        helper.assertTrue(Buildups.get(p, Statuses.VENOM) == 0 && Buildups.get(p, Buildups.BLOOD) == 0, "buildups are washed away");
        helper.assertTrue(p.hasEffect(com.beyondsolo.combat.ModEffects.HEAVY_VENOM), "heavy venom still needs its remedy");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void jobChangeHooksWork(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E, PlayerClass.WARRIOR);
        helper.assertTrue(SkillAPI.grantAdvanced(p).contains(Skills.EARTHSPLITTER), "the job change grant teaches the advanced skill");
        helper.assertTrue(SkillAPI.grantAdvanced(p).isEmpty(), "only once");
        // a new class: old class skills go, shared ones stay, the new class's starters arrive
        SystemAPI.awaken(p, PlayerRank.E, PlayerClass.MAGE);
        helper.assertTrue(!SkillAPI.knows(p, Skills.RENDING_ARC) && !SkillAPI.knows(p, Skills.EARTHSPLITTER), "warrior skills are gone");
        helper.assertTrue(SkillAPI.knows(p, Skills.SURGE_STEP), "shared skills stay");
        helper.assertTrue(SkillAPI.knows(p, Skills.ARC_BOLT), "mage starter arrives");
        helper.assertTrue(SkillAPI.get(p).slotOf(Skills.RENDING_ARC.path()) < 0, "and the bar no longer holds the old skill");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void skillBarAndSaving(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E, PlayerClass.WARRIOR);
        helper.assertTrue(SkillAPI.assign(p, 3, Skills.RENDING_ARC.path()), "move to slot 4");
        helper.assertTrue(SkillAPI.get(p).slotOf(Skills.RENDING_ARC.path()) == 3, "a skill sits in one slot only");
        helper.assertTrue(!SkillAPI.assign(p, 0, Skills.WAR_CRY.path()), "unknown skills can't be equipped");
        SkillAPI.learn(p, Skills.BATTLE_HARDENED, 1, SkillEvent.Source.COMMAND);
        helper.assertTrue(!SkillAPI.assign(p, 0, Skills.BATTLE_HARDENED.path()), "passives don't go on the bar");
        helper.assertTrue(SkillAPI.castSlot(p, 3), "cast from the slot");
        SkillAPI.addLevels(p, Skills.SURGE_STEP, 2);

        CompoundTag tag = SkillAPI.get(p).serializeNBT(p.registryAccess());
        SkillData copy = new SkillData();
        copy.deserializeNBT(p.registryAccess(), tag);
        helper.assertTrue(copy.level(Skills.SURGE_STEP.path()) == 3, "levels are saved");
        helper.assertTrue(copy.slot(3).equals(Skills.RENDING_ARC.path()), "the bar is saved");
        helper.assertTrue(copy.cooldownLeft(Skills.RENDING_ARC.path(), p.level().getGameTime()) > 0, "cooldowns are saved");
        helper.assertTrue(SystemAPI.get(p).stat(Stat.STR) >= Stat.BASE, "sanity");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void everySkillHasTextAndAnElement(GameTestHelper helper) {
        var lang = net.minecraft.locale.Language.getInstance();
        for (Skill s : Skills.all()) {
            helper.assertTrue(s.element() != null, s.path() + " has an element");
            helper.assertTrue(s.isActive() == (s.manaCost(1) > 0), s.path() + ": actives cost mana, passives don't");
            // descriptions must format with the numbers they are given
            String text = s.description(Skill.MAX_LEVEL, 5).getString();
            helper.assertTrue(!text.isEmpty(), s.path() + " description");
            if (lang.has("skill.beyondsolo.surge_step")) {
                helper.assertTrue(lang.has(s.translationKey()) && lang.has(s.translationKey() + ".desc") && lang.has(s.translationKey() + ".flavour"),
                        s.path() + " is missing text");
            }
        }
        for (PlayerClass c : PlayerClass.values()) {
            helper.assertTrue(Skills.advancedFor(c).size() == 1, c + " has one advanced skill");
            helper.assertTrue(Skills.forClass(c).size() >= 10, c + " has at least 10 skills");
        }
        helper.succeed();
    }
}
