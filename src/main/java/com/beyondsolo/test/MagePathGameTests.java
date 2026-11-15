package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.job.JobAPI;
import com.beyondsolo.job.Subclass;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.SkillAPI;
import com.beyondsolo.skill.SkillEvent;
import com.beyondsolo.skill.Skills;
import com.beyondsolo.skill.MagePath;
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
import java.util.List;
import java.util.UUID;

/** The Mage subclasses' skill sets. Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MagePathGameTests {
    private static final String ARENA = "evaluation_zone";
    private static final Subclass[] MAGES = {Subclass.PYROMANCER, Subclass.STORMCALLER, Subclass.TIDECALLER, Subclass.NECROMANCER, Subclass.STARBORN_ARCHON};

    private static ServerPlayer mage(GameTestHelper helper, Subclass sub) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "mage"));
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
        SystemAPI.awaken(p, PlayerRank.E, PlayerClass.MAGE);
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
    public static void mageSubclassesHaveTwelveSkills(GameTestHelper helper) {
        for (Subclass sub : MAGES) {
            List<Skill> all = Skills.forSubclass(sub);
            long actives = all.stream().filter(Skill::isActive).count();
            helper.assertTrue(all.size() >= 12, sub + " has at least 12 skills, found " + all.size());
            helper.assertTrue(actives >= 8 && all.size() - actives >= 4, sub + " has 8 actives and 4 passives, found " + actives + " actives");
            for (Skill s : all) {
                helper.assertTrue(s.element() == sub.element(), s.path() + " carries its subclass's element");
                helper.assertTrue(s.owner() == PlayerClass.MAGE, s.path() + " belongs to mages");
            }
        }
        ServerPlayer p = mage(helper, Subclass.PYROMANCER);
        helper.assertTrue(!SkillAPI.knows(p, MagePath.FIRE_LANCE), "Fire Lance waits for its level");
        for (int i = 0; i < 2000 && SystemAPI.get(p).level() < 50; i++) SystemAPI.grantXp(p, 20000, SystemAPI.XP_SOURCE_COMMAND);
        helper.assertTrue(Skills.forSubclass(Subclass.PYROMANCER).stream().allMatch(s -> SkillAPI.knows(p, s)), "everything by level 50");
        helper.assertTrue(!SkillAPI.knows(p, MagePath.THUNDER_SPEAR), "other subclasses' skills stay out");
        helper.succeed();
    }

    /** Every new active of every mage subclass casts and hurts the creature in front, one after the other. */
    @GameTest(template = ARENA, timeoutTicks = 3000)
    public static void mageSubclassSkillsCastAndHit(GameTestHelper helper) {
        List<Skill> queue = new ArrayList<>();
        for (Subclass sub : MAGES) for (Skill s : Skills.forSubclass(sub)) if (s.isActive() && !s.advanced()) queue.add(s);
        helper.assertTrue(queue.size() == 35, "35 new mage subclass actives, found " + queue.size());
        castNext(helper, queue, 0, new ArrayList<>());
    }

    private static void castNext(GameTestHelper helper, List<Skill> queue, int index, List<String> failed) {
        if (index >= queue.size()) {
            helper.assertTrue(failed.isEmpty(), "mage subclass skills that failed: " + failed);
            helper.succeed();
            return;
        }
        Skill s = queue.get(index);
        ServerPlayer p = mage(helper, s.subclass());
        SkillAPI.learn(p, s, 1, SkillEvent.Source.OTHER);
        // the wall of flame stands four blocks ahead
        Zombie z = zombie(helper, p, new BlockPos(s == MagePath.FLAME_WALL ? 5 : 3, 2, 1));
        refill(p);
        boolean cast = SkillAPI.cast(p, s);
        if (!cast) failed.add(s.path() + " did not cast");
        boolean harmless = s == MagePath.HEALING_RAIN || s == MagePath.ASTRAL_AEGIS || s == MagePath.CURSE_OF_FRAILTY;
        // these land their damage late
        boolean slow = s == MagePath.LIVING_BOMB || s == MagePath.SUN_FALL || s == MagePath.JUDGMENT_OF_THUNDER || s == MagePath.REQUIEM
                || s == MagePath.PHOENIX_DIVE || s == MagePath.EVENT_HORIZON || s == MagePath.CONSTELLATION || s == MagePath.METEOR_SHOWER;
        helper.runAfterDelay(slow ? 95 : 24, () -> {
            if (cast && !harmless && z.getHealth() >= 2000) failed.add(s.path() + " did no damage");
            z.discard();
            JobAPI.reset(p);
            helper.runAfterDelay(slow ? 20 : 4, () -> castNext(helper, queue, index + 1, failed));
        });
    }

    @GameTest(template = ARENA)
    public static void magePassivesAndCurses(GameTestHelper helper) {
        // Zenith: more damage, always
        ServerPlayer p = mage(helper, Subclass.STARBORN_ARCHON);
        Zombie z = zombie(helper, p, new BlockPos(3, 2, 1));
        z.hurt(p.damageSources().playerAttack(p), 10);
        float normal = 2000 - z.getHealth();
        SkillAPI.learn(p, MagePath.ZENITH, 10, SkillEvent.Source.OTHER);
        z.setHealth(2000);
        z.invulnerableTime = 0;
        z.hurt(p.damageSources().playerAttack(p), 10);
        helper.assertTrue(2000 - z.getHealth() > normal * 1.25f, "Zenith adds damage: " + (2000 - z.getHealth()) + " vs " + normal);

        // mana passives and the aegis
        float mana = SystemAPI.get(p).maxMana();
        SkillAPI.learn(p, MagePath.STELLAR_MIND, 4, SkillEvent.Source.OTHER);
        helper.assertTrue(Math.abs(SystemAPI.get(p).maxMana() - mana - 20) < 0.01f, "Stellar Mind 4 adds 20 mana: " + (SystemAPI.get(p).maxMana() - mana));
        SkillAPI.learn(p, MagePath.ASTRAL_AEGIS, 1, SkillEvent.Source.OTHER);
        refill(p);
        helper.assertTrue(SkillAPI.cast(p, MagePath.ASTRAL_AEGIS) && SkillAPI.hasBuff(p, MagePath.BUFF_AEGIS), "Astral Aegis is up");
        z.discard();

        ServerPlayer t = mage(helper, Subclass.TIDECALLER);
        float tide = SystemAPI.get(t).maxMana();
        SkillAPI.learn(t, MagePath.HIGH_TIDE, 5, SkillEvent.Source.OTHER);
        helper.assertTrue(Math.abs(SystemAPI.get(t).maxMana() - tide - 20) < 0.01f, "High Tide 5 adds 20 mana");

        // Bone Armor and the Curse of Frailty
        ServerPlayer n = mage(helper, Subclass.NECROMANCER);
        double armor = n.getAttributeValue(Attributes.ARMOR);
        SkillAPI.learn(n, MagePath.BONE_ARMOR, 5, SkillEvent.Source.OTHER);
        helper.assertTrue(Math.abs(n.getAttributeValue(Attributes.ARMOR) - armor - 3) < 0.01, "Bone Armor 5 adds 3 armor");
        Zombie c = zombie(helper, n, new BlockPos(3, 2, 1));
        c.hurt(n.damageSources().playerAttack(n), 10);
        float before = 2000 - c.getHealth();
        SkillAPI.learn(n, MagePath.CURSE_OF_FRAILTY, 10, SkillEvent.Source.OTHER);
        SystemAPI.setManaBonus(n, 1000);
        refill(n);
        helper.assertTrue(SkillAPI.cast(n, MagePath.CURSE_OF_FRAILTY), "Curse of Frailty casts");
        c.setHealth(2000);
        c.invulnerableTime = 0;
        c.hurt(n.damageSources().playerAttack(n), 10);
        helper.assertTrue(2000 - c.getHealth() > before * 1.25f, "the cursed take more: " + (2000 - c.getHealth()) + " vs " + before);

        // Pyromania: burning foes take more
        ServerPlayer f = mage(helper, Subclass.PYROMANCER);
        Zombie b = zombie(helper, f, new BlockPos(3, 2, 5));
        b.hurt(f.damageSources().playerAttack(f), 10);
        float cold = 2000 - b.getHealth();
        SkillAPI.learn(f, MagePath.PYROMANIA, 10, SkillEvent.Source.OTHER);
        b.setHealth(2000);
        b.igniteForSeconds(5);
        b.invulnerableTime = 0;
        b.hurt(f.damageSources().playerAttack(f), 10);
        helper.assertTrue(2000 - b.getHealth() > cold * 1.15f, "Pyromania adds damage to the burning: " + (2000 - b.getHealth()) + " vs " + cold);
        helper.succeed();
    }
}
