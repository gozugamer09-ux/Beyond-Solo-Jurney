package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.job.JobAPI;
import com.beyondsolo.job.Subclass;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.skill.Gestures;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.SkillAPI;
import com.beyondsolo.skill.SkillEvent;
import com.beyondsolo.skill.SkillWindup;
import com.beyondsolo.skill.SkillWindupTestAccess;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The wind-up before a class skill ({@link SkillWindup}): a press winds up, spends nothing until the skill goes off,
 * goes off on its tick, keeps one more press for after; what must be instant stays instant; the wind-up grows with
 * the skill; and the looks table the server winds up from before a skill's first cast is true to the skills.
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SkillWindupGameTests {
    private static final String ARENA = "evaluation_zone";

    private static ServerPlayer hunter(GameTestHelper helper, PlayerClass playerClass) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "windup"));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        SkillAPI.reset(p);
        JobAPI.reset(p);
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        p.removeAllEffects();
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        QuestTestAccess.markStarted(p);
        SystemAPI.awaken(p, PlayerRank.E, playerClass);
        return p;
    }

    /** A zombie standing still at {@code at}, with the hunter turned to face it. */
    private static Zombie zombie(GameTestHelper helper, ServerPlayer p, BlockPos at, float health) {
        Zombie z = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, at);
        z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        z.setHealth(health);
        var dodge = z.getAttribute(dev.shadowsoffire.apothic_attributes.api.ALObjects.Attributes.DODGE_CHANCE);
        if (dodge != null) {
            dodge.removeModifiers();
            dodge.setBaseValue(0);
        }
        face(p, z);
        return z;
    }

    private static void face(ServerPlayer p, Zombie z) {
        Vec3 d = z.getEyePosition().subtract(p.getEyePosition());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) (-(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG));
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.setYHeadRot(yaw);
    }

    /** A press winds up: nothing spent and nothing struck until the skill's tick, then it goes off as a cast would. */
    @GameTest(template = ARENA)
    public static void aPressWindsUpThenGoesOff(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerClass.WARRIOR);
        Zombie z = zombie(helper, p, new BlockPos(3, 2, 1), 2000);
        Skill arc = Skills.RENDING_ARC;
        SkillAPI.learn(p, arc, 1, SkillEvent.Source.OTHER);
        SystemAPI.restoreMana(p, 100_000);
        float mana = SystemAPI.get(p).mana();
        Object[] look = Gestures.look(arc);
        int ticks = Gestures.windup(arc, (String) look[0], (int) look[1]);
        helper.assertTrue(ticks >= 3 && ticks <= 6, "a light skill winds up for a fifth of a second or so, got " + ticks);
        helper.assertTrue(SkillWindup.press(p, arc), "the press is taken");
        helper.assertTrue(SkillWindup.winding(p), "winding up");
        helper.assertTrue(z.getHealth() == 2000, "nothing struck yet");
        helper.assertTrue(SystemAPI.get(p).mana() == mana, "nothing spent yet");
        helper.assertTrue(SkillAPI.get(p).cooldownLeft(arc.path(), p.level().getGameTime()) == 0, "no cooldown yet");
        helper.assertFalse(SkillWindup.press(p, arc), "the same skill again while winding up is not a second cast");
        helper.runAfterDelay(ticks + 1, () -> {
            helper.assertFalse(SkillWindup.winding(p), "it went off");
            helper.assertTrue(z.getHealth() < 2000, "the cut landed");
            helper.assertTrue(SystemAPI.get(p).mana() < mana, "the mana was spent when it went off");
            helper.assertTrue(SkillAPI.get(p).cooldownLeft(arc.path(), p.level().getGameTime()) > 0, "and the cooldown started");
            z.discard();
            helper.succeed();
        });
    }

    /** Another skill pressed while winding up waits and winds up right after the first goes off. */
    @GameTest(template = ARENA)
    public static void aPressDuringAWindupGoesOffAfter(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerClass.WARRIOR);
        Zombie z = zombie(helper, p, new BlockPos(3, 2, 1), 5000);
        SkillAPI.learn(p, Skills.RENDING_ARC, 1, SkillEvent.Source.OTHER);
        SkillAPI.learn(p, Skills.WHIRLWIND, 1, SkillEvent.Source.OTHER);
        SkillAPI.assign(p, 0, Skills.RENDING_ARC.path());
        SkillAPI.assign(p, 1, Skills.WHIRLWIND.path());
        SystemAPI.restoreMana(p, 100_000);
        helper.assertTrue(SkillWindup.pressSlot(p, 0), "the first press winds up");
        helper.assertFalse(SkillWindup.pressSlot(p, 1), "the second waits");
        long now = p.level().getGameTime();
        helper.succeedWhen(() -> {
            helper.assertTrue(SkillAPI.get(p).cooldownLeft(Skills.WHIRLWIND.path(), p.level().getGameTime()) > 0,
                    "the waiting skill went off after the first (" + (p.level().getGameTime() - now) + " ticks)");
            helper.assertTrue(SkillAPI.get(p).cooldownLeft(Skills.RENDING_ARC.path(), p.level().getGameTime()) > 0, "and the first did");
            z.discard();
        });
    }

    /** A skill that finds nothing to strike when it goes off costs nothing, as a cast that finds nothing never did. */
    @GameTest(template = ARENA)
    public static void aWindupThatFindsNothingCostsNothing(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerClass.WARRIOR);
        Skill hemorrhage = com.beyondsolo.skill.WarriorPath.HEMORRHAGE;
        SkillAPI.learn(p, hemorrhage, 1, SkillEvent.Source.OTHER);
        SystemAPI.restoreMana(p, 100_000);
        float mana = SystemAPI.get(p).mana();
        Object[] look = Gestures.look(hemorrhage);
        int ticks = Gestures.windup(hemorrhage, (String) look[0], (int) look[1]);
        helper.assertTrue(ticks > 0, "a cut winds up");
        helper.assertTrue(SkillWindup.press(p, hemorrhage), "the press is taken (nothing in reach yet)");
        helper.runAfterDelay(ticks + 1, () -> {
            helper.assertFalse(SkillWindup.winding(p), "the wind-up is over");
            helper.assertTrue(SystemAPI.get(p).mana() == mana, "nothing spent on a cut at nothing");
            helper.assertTrue(SkillAPI.get(p).cooldownLeft(hemorrhage.path(), p.level().getGameTime()) == 0, "and no cooldown");
            helper.succeed();
        });
    }

    /** Dashes, vanishes, the shared skills and the sovereigns' powers go off at once; the rest wind up by size. */
    @GameTest(template = ARENA)
    public static void windupsGrowWithTheSkillAndInstantsStayInstant(GameTestHelper helper) {
        helper.assertTrue(windup(Skills.SURGE_STEP) == 0, "a shared skill is instant");
        helper.assertTrue(Gestures.windup(Skills.BULL_RUSH, "DASH", 10) == 0, "a dash is instant");
        helper.assertTrue(Gestures.windup(Skills.SHADOWSTEP, "BLINK", 0) == 0, "a vanish is instant");
        helper.assertTrue(Gestures.windup(Skills.BULWARK, "DOME", 0) == 0, "a guard thrown up is instant");
        for (Skill s : Skills.monarchPowers()) helper.assertTrue(windup(s) == 0, s.path() + " is a sovereign's power: instant");
        int light = Gestures.windup(Skills.RENDING_ARC, "SLASH", 0);
        int ultimate = Gestures.windup(com.beyondsolo.skill.WarriorPath.SEVERING_LINE, "SLASH", 0);
        helper.assertTrue(light > 0 && light <= 6, "a light cut winds up briefly, got " + light);
        helper.assertTrue(ultimate >= 14 && ultimate <= 20, "an ultimate winds up for most of a second, got " + ultimate);
        int heavy = -1;
        for (Skill s : Skills.all()) {
            if (s.owner() == null || !s.isActive() || s.rune() || s.recast() != null || Gestures.heft(s) != 2) continue;
            heavy = Gestures.windup(s, "SLASH", 0);
            break;
        }
        helper.assertTrue(heavy > light && heavy < ultimate, "a heavy skill winds up between the two, got " + heavy);
        helper.assertTrue(Gestures.windup(Skills.RENDING_ARC, "ARROW", 0) >= Gestures.strike("bow"), "a bow is drawn before it is loosed");
        // a skill that takes a stance of its own before its blow winds up only for the rest
        int stance = Gestures.windup(com.beyondsolo.skill.WarriorPath.SEVERING_LINE, "SLASH", 0, 12);
        helper.assertTrue(stance == ultimate - 12, "an ultimate with a 12-tick stance winds up for the rest, got " + stance);
        helper.assertTrue(Gestures.windup(com.beyondsolo.skill.WarriorPath.SEVERING_LINE, "SLASH", 0, 14) >= Gestures.strike("slash"),
                "never shorter than the gesture takes to land");
        helper.assertTrue(Gestures.windup(com.beyondsolo.skill.WarriorPath.SEVERING_LINE, "SLASH", 0, Gestures.MAX_DELAY + 10) == ultimate,
                "a blow that comes much later gets a gesture of its own: the wind-up is whole");
        for (String g : List.of("slash", "slash_back", "chop", "claw", "lunge", "spin", "slam", "stomp", "ground", "roar", "power", "leap",
                "vanish", "place", "throw", "throw_side", "bow", "bow_sky", "cast", "cast_both", "raise", "channel", "burst", "pray",
                "guard", "point", "summon", "sweep")) {
            helper.assertTrue(Gestures.strike(g) >= 2 && Gestures.strike(g) <= 8, g + " lands its blow early in the gesture");
        }
        helper.succeed();
    }

    private static int windup(Skill s) {
        Object[] look = Gestures.look(s);
        return Gestures.windup(s, (String) look[0], (int) look[1]);
    }

    /**
     * Casts every class skill once and checks the gesture the server winds up for before a skill's first cast
     * ({@code beyondsolo/skill_looks.txt}) is the one the skill acts out. When one differs, the whole table as it
     * should read is in the server log, ready to paste.
     */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void theLooksTableIsTrueToTheSkills(GameTestHelper helper) {
        Map<String, Object[]> table = SkillWindupTestAccess.table();
        SkillWindupTestAccess.forgetCasts();
        List<Skill> tried = new ArrayList<>();
        Set<String> went = new HashSet<>();
        List<Zombie> zombies = new ArrayList<>();
        BlockPos spot = new BlockPos(3, 2, 1);
        for (Subclass sub : Subclass.values()) {
            ServerPlayer p = hunter(helper, sub.base());
            JobAPI.changeJob(p, sub);
            p.setGameMode(GameType.CREATIVE);
            for (Skill s : Skills.all()) {
                if (!s.isActive() || s.owner() != sub.base() || s.subclass() != null && s.subclass() != sub) continue;
                if (s.rune() || s.sovereign() || tried.contains(s)) continue;
                tried.add(s);
                if (!SkillAPI.knows(p, s)) SkillAPI.learn(p, s, 1, SkillEvent.Source.OTHER);
                SkillAPI.resetCooldowns(p);
                BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
                p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
                Zombie z = zombie(helper, p, spot, 100_000);
                SkillWindupTestAccess.clearLook(s);
                if (SkillAPI.cast(p, s)) went.add(s.path());
                // a skill that showed nothing yet keeps its creature till the end: its later blow still finds its mark
                if (SkillWindupTestAccess.cast(s) == null) zombies.add(z);
                else z.discard();
            }
        }
        // what skills show after their cast (a stance, then the cut) is known once their later blows have come
        helper.runAfterDelay(Gestures.MAX_DELAY + 5, () -> {
            zombies.forEach(Zombie::discard);
            Map<String, String> actual = new LinkedHashMap<>();
            List<String> wrong = new ArrayList<>();
            for (Skill s : tried) {
                Object[] seen = went.contains(s.path()) ? SkillWindupTestAccess.cast(s) : null;
                String line = seen == null ? "- 0" : line(seen);
                actual.put(s.path(), line);
                Object[] listed = table.get(s.path());
                String was = listed == null ? null : line(listed);
                if (!line.equals(was)) wrong.add(s.path() + " (listed " + was + ", shows " + line + ")");
            }
            SkillWindupTestAccess.forgetCasts();
            if (!wrong.isEmpty()) {
                StringBuilder b = new StringBuilder("# skill path, the first effect it shows (- none), that effect's duration, and how many\n");
                b.append("# ticks after its cast it shows it if not at once. Written from SkillWindupGameTests.theLooksTableIsTrueToTheSkills.\n");
                actual.forEach((k, v) -> b.append(k).append(' ').append(v).append('\n'));
                BeyondSolo.LOGGER.info("skill_looks.txt as it should read:\n{}", b);
            }
            helper.assertTrue(wrong.isEmpty(), wrong.size() + " skills wind up for another gesture than they show (table in the log): "
                    + String.join(", ", wrong.subList(0, Math.min(8, wrong.size()))));
            helper.assertTrue(actual.size() > 200, "every class skill was tried, got " + actual.size());
            helper.succeed();
        });
    }

    /** A line of the looks table: the look (- for none), the variant, and the delay when there is one. */
    private static String line(Object[] look) {
        String l = (((String) look[0]).isEmpty() ? "-" : (String) look[0]) + " " + look[1];
        int delay = look.length > 2 ? (int) look[2] : 0;
        return delay > 0 ? l + " " + delay : l;
    }
}
