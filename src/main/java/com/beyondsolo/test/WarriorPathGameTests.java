package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.job.JobAPI;
import com.beyondsolo.job.Subclass;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.SkillAPI;
import com.beyondsolo.skill.SkillElement;
import com.beyondsolo.skill.SkillEvent;
import com.beyondsolo.skill.Skills;
import com.beyondsolo.skill.WarriorPath;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The Warrior subclasses' skill sets. Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WarriorPathGameTests {
    private static final String ARENA = "evaluation_zone";
    private static final Subclass[] WARRIORS = {Subclass.BLOODRAGER, Subclass.WARLORD, Subclass.BLADEMASTER, Subclass.JUGGERNAUT, Subclass.WORLDBREAKER};

    private static ServerPlayer warrior(GameTestHelper helper, Subclass sub) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "warrior"));
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
        SystemAPI.awaken(p, PlayerRank.E, PlayerClass.WARRIOR);
        // no lucky critical hits: the tests compare damage numbers
        var crit = p.getAttribute(dev.shadowsoffire.apothic_attributes.api.ALObjects.Attributes.CRIT_CHANCE);
        if (crit != null) {
            crit.removeModifiers();
            crit.setBaseValue(-10);
        }
        if (sub != null) JobAPI.changeJob(p, sub);
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

    private static void learnAll(ServerPlayer p, Subclass sub) {
        for (Skill s : Skills.forSubclass(sub)) SkillAPI.learn(p, s, 1, SkillEvent.Source.OTHER);
    }

    @GameTest(template = ARENA)
    public static void warriorSubclassesHaveTwelveSkills(GameTestHelper helper) {
        for (Subclass sub : WARRIORS) {
            List<Skill> all = Skills.forSubclass(sub);
            long actives = all.stream().filter(Skill::isActive).count();
            helper.assertTrue(all.size() >= 12, sub + " has at least 12 skills, found " + all.size());
            helper.assertTrue(actives >= 8 && all.size() - actives >= 4, sub + " has 8 actives and 4 passives, found " + actives + " actives");
            for (Skill s : all) {
                helper.assertTrue(s.element() != null && s.element() != SkillElement.PHYSICAL, s.path() + " carries its subclass's element");
                helper.assertTrue(s.owner() == PlayerClass.WARRIOR, s.path() + " belongs to warriors");
            }
            helper.assertTrue(Skills.startersFor(sub).size() == (sub.rare() ? 3 : 2), sub + " starters");
        }
        // base class lists and tomes stay free of subclass skills
        helper.assertTrue(Skills.forClass(PlayerClass.WARRIOR).stream().allMatch(s -> s.subclass() == null), "class list has no subclass skills");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void subclassSkillsUnlockWithLevels(GameTestHelper helper) {
        ServerPlayer p = warrior(helper, Subclass.BLADEMASTER);
        helper.assertTrue(Skills.startersFor(Subclass.BLADEMASTER).stream().allMatch(s -> SkillAPI.knows(p, s)), "starters at the job change");
        helper.assertTrue(!SkillAPI.knows(p, WarriorPath.IAIDO), "Iaido waits for its level");
        for (int i = 0; i < 400 && SystemAPI.get(p).level() < 30; i++) SystemAPI.grantXp(p, 5000, SystemAPI.XP_SOURCE_COMMAND);
        helper.assertTrue(SystemAPI.get(p).level() >= 30, "reached level 30");
        helper.assertTrue(SkillAPI.knows(p, WarriorPath.IAIDO) && SkillAPI.knows(p, WarriorPath.CRESCENT_WAVE) && SkillAPI.knows(p, WarriorPath.KEEN_EDGE),
                "the first subclass skills unlock on the way to level 30");
        helper.assertTrue(!SkillAPI.knows(p, WarriorPath.SEVERING_LINE), "the ultimate is still locked");
        helper.assertTrue(!SkillAPI.knows(p, WarriorPath.CRIMSON_CLEAVE) && !SkillAPI.knows(p, WarriorPath.SEISMIC_STOMP), "other subclasses' skills stay out");
        for (int i = 0; i < 2000 && SystemAPI.get(p).level() < 50; i++) SystemAPI.grantXp(p, 20000, SystemAPI.XP_SOURCE_COMMAND);
        helper.assertTrue(Skills.forSubclass(Subclass.BLADEMASTER).stream().allMatch(s -> SkillAPI.knows(p, s)), "everything by level 50");

        // a warrior without a subclass gets none of it, whatever the level
        ServerPlayer q = warrior(helper, null);
        for (int i = 0; i < 2000 && SystemAPI.get(q).level() < 50; i++) SystemAPI.grantXp(q, 20000, SystemAPI.XP_SOURCE_COMMAND);
        helper.assertTrue(Skills.all().stream().noneMatch(s -> s.subclass() != null && SkillAPI.knows(q, s)), "no subclass, no subclass skills");
        // a late job change catches up at once
        JobAPI.changeJob(q, Subclass.JUGGERNAUT);
        helper.assertTrue(Skills.forSubclass(Subclass.JUGGERNAUT).stream().allMatch(s -> SkillAPI.knows(q, s)), "late job change learns everything already passed");
        helper.succeed();
    }

    /** Every new active of every warrior subclass casts and hurts the creature in front, one after the other. */
    @GameTest(template = ARENA, timeoutTicks = 2400)
    public static void warriorSubclassSkillsCastAndHit(GameTestHelper helper) {
        List<Skill> queue = new ArrayList<>();
        for (Subclass sub : WARRIORS) for (Skill s : Skills.forSubclass(sub)) if (s.isActive() && !s.advanced()) queue.add(s);
        helper.assertTrue(queue.size() == 35, "35 new warrior subclass actives, found " + queue.size());
        castNext(helper, queue, 0, new ArrayList<>());
    }

    private static void castNext(GameTestHelper helper, List<Skill> queue, int index, List<String> failed) {
        if (index >= queue.size()) {
            helper.assertTrue(failed.isEmpty(), "warrior subclass skills that failed: " + failed);
            helper.succeed();
            return;
        }
        Skill s = queue.get(index);
        ServerPlayer p = warrior(helper, s.subclass());
        SkillAPI.learn(p, s, 1, SkillEvent.Source.OTHER);
        Zombie z = zombie(helper, p, new BlockPos(3, 2, 1));
        refill(p);
        boolean cast = SkillAPI.cast(p, s);
        if (!cast) failed.add(s.path() + " did not cast");
        boolean harmless = s == WarriorPath.LAST_STAND || s == WarriorPath.RALLYING_CRY || s == WarriorPath.SHIELD_WALL
                || s == WarriorPath.STONE_SKIN || s == WarriorPath.BULWARK_TAUNT;
        // leaps land late for a player that never moves, and End of Ages gathers for a while
        boolean slow = s == WarriorPath.FALLING_PETAL || s == WarriorPath.MOUNTAINFALL || s == WarriorPath.END_OF_AGES;
        helper.runAfterDelay(slow ? 75 : 24, () -> {
            if (cast && !harmless && z.getHealth() >= 2000) failed.add(s.path() + " did no damage");
            z.discard();
            JobAPI.reset(p);
            // let anything still scheduled for this caster run out before the next one
            helper.runAfterDelay(slow ? 100 : 4, () -> castNext(helper, queue, index + 1, failed));
        });
    }

    @GameTest(template = ARENA)
    public static void warriorPassivesAndMarks(GameTestHelper helper) {
        // attribute passives
        ServerPlayer p = warrior(helper, Subclass.BLOODRAGER);
        double health = p.getMaxHealth();
        SkillAPI.learn(p, WarriorPath.THICK_BLOOD, 5, SkillEvent.Source.OTHER);
        helper.assertTrue(Math.abs(p.getMaxHealth() - health * 1.125f) < 0.01, "Thick Blood 5 adds 12.5% max health: " + (p.getMaxHealth() - health));

        // Last Stand refuses death
        SkillAPI.learn(p, WarriorPath.LAST_STAND, 1, SkillEvent.Source.OTHER);
        refill(p);
        helper.assertTrue(SkillAPI.cast(p, WarriorPath.LAST_STAND), "Last Stand casts");
        helper.assertTrue(SkillAPI.hasBuff(p, WarriorPath.BUFF_LAST_STAND), "Last Stand is up");
        LivingDeathEvent death = new LivingDeathEvent(p, p.damageSources().generic());
        NeoForge.EVENT_BUS.post(death);
        helper.assertTrue(death.isCanceled() && p.getHealth() == 1, "Last Stand keeps the Bloodrager alive at 1 health");

        // Execution Order: everyone's hits on the marked creature are stronger
        ServerPlayer w = warrior(helper, Subclass.WARLORD);
        SkillAPI.learn(w, WarriorPath.EXECUTION_ORDER, 10, SkillEvent.Source.OTHER);
        Zombie plain = zombie(helper, w, new BlockPos(3, 2, 3));
        plain.hurt(w.damageSources().playerAttack(w), 10);
        float normal = 2000 - plain.getHealth();
        plain.discard();
        Zombie marked = zombie(helper, w, new BlockPos(3, 2, 1));
        refill(w);
        helper.assertTrue(SkillAPI.cast(w, WarriorPath.EXECUTION_ORDER), "Execution Order casts");
        helper.assertTrue(marked.hasEffect(MobEffects.GLOWING), "the marked creature glows");
        marked.hurt(p.damageSources().playerAttack(p), 10);
        float more = 2000 - marked.getHealth();
        helper.assertTrue(more > normal * 1.3f, "a marked creature takes more from anyone: " + more + " vs " + normal);

        // Bloodscent: more damage to the wounded
        ServerPlayer b = warrior(helper, Subclass.BLOODRAGER);
        Zombie hurt = zombie(helper, b, new BlockPos(3, 2, 5));
        hurt.setHealth(900);
        hurt.hurt(b.damageSources().playerAttack(b), 10);
        float before = 900 - hurt.getHealth();
        hurt.setHealth(900);
        hurt.invulnerableTime = 0;
        SkillAPI.learn(b, WarriorPath.BLOODSCENT, 10, SkillEvent.Source.OTHER);
        hurt.hurt(b.damageSources().playerAttack(b), 10);
        float after = 900 - hurt.getHealth();
        helper.assertTrue(after > before * 1.15f, "Bloodscent adds damage against wounded foes: " + after + " vs " + before);
        plain.discard();
        marked.discard();
        hurt.discard();
        helper.succeed();
    }

    /** Hemorrhage keeps bleeding, and Gravity Well drags foes in. */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void hemorrhageBleedsAndGravityPulls(GameTestHelper helper) {
        ServerPlayer p = warrior(helper, Subclass.BLOODRAGER);
        SkillAPI.learn(p, WarriorPath.HEMORRHAGE, 1, SkillEvent.Source.OTHER);
        Zombie z = zombie(helper, p, new BlockPos(3, 2, 1));
        refill(p);
        helper.assertTrue(SkillAPI.cast(p, WarriorPath.HEMORRHAGE), "Hemorrhage casts");
        float first = 2000 - z.getHealth();
        helper.assertTrue(first > 0, "the cut lands");

        ServerPlayer w = warrior(helper, Subclass.WORLDBREAKER);
        BlockPos far = helper.absolutePos(new BlockPos(1, 2, 5));
        w.moveTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
        SkillAPI.learn(w, WarriorPath.GRAVITY_WELL, 1, SkillEvent.Source.OTHER);
        Zombie g = zombie(helper, w, new BlockPos(5, 2, 5));
        double startDistance = g.distanceTo(w);
        refill(w);
        helper.assertTrue(SkillAPI.cast(w, WarriorPath.GRAVITY_WELL), "Gravity Well casts");

        helper.runAfterDelay(115, () -> {
            float total = 2000 - z.getHealth();
            helper.assertTrue(total > first * 1.5f, "Hemorrhage kept bleeding: " + total + " after " + first);
            helper.assertTrue(g.distanceTo(w) < startDistance - 1, "Gravity Well dragged the foe in: " + g.distanceTo(w) + " from " + startDistance);
            z.discard();
            g.discard();
            helper.succeed();
        });
    }
}
