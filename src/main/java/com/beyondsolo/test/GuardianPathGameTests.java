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
import com.beyondsolo.skill.GuardianPath;
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

/** The Guardian subclasses' skill sets. Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GuardianPathGameTests {
    private static final String ARENA = "evaluation_zone";
    private static final Subclass[] GUARDIANS = {Subclass.PALADIN, Subclass.IRONWALL_SENTINEL, Subclass.SPELLWARD_WARDEN, Subclass.VENGEANCE_KNIGHT, Subclass.EVERLASTING_COLOSSUS};

    private static ServerPlayer guardian(GameTestHelper helper, Subclass sub) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "guardian"));
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
        SystemAPI.awaken(p, PlayerRank.E, PlayerClass.GUARDIAN);
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
    public static void guardianSubclassesHaveTwelveSkills(GameTestHelper helper) {
        for (Subclass sub : GUARDIANS) {
            List<Skill> all = Skills.forSubclass(sub);
            long actives = all.stream().filter(Skill::isActive).count();
            helper.assertTrue(all.size() >= 12, sub + " has at least 12 skills, found " + all.size());
            helper.assertTrue(actives >= 8 && all.size() - actives >= 4, sub + " has 8 actives and 4 passives, found " + actives + " actives");
            for (Skill s : all) {
                helper.assertTrue(s.element() == sub.element(), s.path() + " carries its subclass's element");
                helper.assertTrue(s.owner() == PlayerClass.GUARDIAN, s.path() + " belongs to guardians");
                // shield and hammer work: only the ultimates may draw a casting circle
                helper.assertTrue(!s.castCircle() || s.cooldown(1) >= 900, s.path() + " casts without a circle");
            }
        }
        ServerPlayer p = guardian(helper, Subclass.PALADIN);
        helper.assertTrue(!SkillAPI.knows(p, GuardianPath.HAMMER_OF_DAWN), "Hammer of Dawn waits for its level");
        for (int i = 0; i < 2000 && SystemAPI.get(p).level() < 50; i++) SystemAPI.grantXp(p, 20000, SystemAPI.XP_SOURCE_COMMAND);
        helper.assertTrue(Skills.forSubclass(Subclass.PALADIN).stream().allMatch(s -> SkillAPI.knows(p, s)), "everything by level 50");
        helper.assertTrue(!SkillAPI.knows(p, GuardianPath.SHIELD_SLAM), "other subclasses' skills stay out");
        helper.succeed();
    }

    /** Every new active of every guardian subclass casts and hurts the creature in front, one after the other. */
    @GameTest(template = ARENA, timeoutTicks = 3600)
    public static void guardianSubclassSkillsCastAndHit(GameTestHelper helper) {
        List<Skill> queue = new ArrayList<>();
        for (Subclass sub : GUARDIANS) for (Skill s : Skills.forSubclass(sub)) if (s.isActive() && !s.advanced()) queue.add(s);
        helper.assertTrue(queue.size() == 35, "35 new guardian subclass actives, found " + queue.size());
        castNext(helper, queue, 0, new ArrayList<>());
    }

    private static void castNext(GameTestHelper helper, List<Skill> queue, int index, List<String> failed) {
        if (index >= queue.size()) {
            helper.assertTrue(failed.isEmpty(), "guardian subclass skills that failed: " + failed);
            helper.succeed();
            return;
        }
        Skill s = queue.get(index);
        ServerPlayer p = guardian(helper, s.subclass());
        SkillAPI.learn(p, s, 1, SkillEvent.Source.OTHER);
        // the wall and the aimed skills land four blocks ahead
        boolean far = s == GuardianPath.RAMPART || s == GuardianPath.BINDING_GLYPH || s == GuardianPath.FUNERAL_PYRE || s == GuardianPath.MONOLITH
                || s == GuardianPath.VENGEFUL_LEAP;
        Zombie z = zombie(helper, p, new BlockPos(far ? 5 : 3, 2, 1));
        refill(p);
        boolean cast = SkillAPI.cast(p, s);
        if (!cast) failed.add(s.path() + " did not cast");
        boolean harmless = s == GuardianPath.SHIELD_OF_FAITH || s == GuardianPath.LAY_ON_HANDS || s == GuardianPath.BRACE
                || s == GuardianPath.MIRROR_WARD || s == GuardianPath.BEDROCK_SKIN || s == GuardianPath.WRATH;
        // these land their damage late
        boolean slow = s == GuardianPath.DAWNBREAKER || s == GuardianPath.EYE_FOR_AN_EYE || s == GuardianPath.ARCANUM || s == GuardianPath.CITADEL
                || s == GuardianPath.DAY_OF_RECKONING || s == GuardianPath.VENGEFUL_LEAP || s == GuardianPath.PILLARS_OF_CREATION
                || s == GuardianPath.CONSECRATE || s == GuardianPath.NULL_FIELD;
        helper.runAfterDelay(slow ? 115 : 24, () -> {
            if (cast && !harmless && z.getHealth() >= 2000) failed.add(s.path() + " did no damage");
            z.discard();
            JobAPI.reset(p);
            helper.runAfterDelay(slow ? 20 : 4, () -> castNext(helper, queue, index + 1, failed));
        });
    }

    @GameTest(template = ARENA)
    public static void guardianPassivesAndStances(GameTestHelper helper) {
        // Titanic Frame and Heavy Plating
        ServerPlayer c = guardian(helper, Subclass.EVERLASTING_COLOSSUS);
        float health = c.getMaxHealth();
        SkillAPI.learn(c, GuardianPath.TITANIC_FRAME, 5, SkillEvent.Source.OTHER);
        helper.assertTrue(Math.abs(c.getMaxHealth() - health * 1.25f) < 0.01f, "Titanic Frame 5 adds 25% health: " + (c.getMaxHealth() - health));
        ServerPlayer i = guardian(helper, Subclass.IRONWALL_SENTINEL);
        double armor = i.getAttributeValue(Attributes.ARMOR);
        SkillAPI.learn(i, GuardianPath.HEAVY_PLATING, 5, SkillEvent.Source.OTHER);
        helper.assertTrue(Math.abs(i.getAttributeValue(Attributes.ARMOR) - armor - 4) < 0.01, "Heavy Plating 5 adds 4 armor");
        SkillAPI.learn(i, GuardianPath.BRACE, 1, SkillEvent.Source.OTHER);
        refill(i);
        helper.assertTrue(SkillAPI.cast(i, GuardianPath.BRACE) && SkillAPI.hasBuff(i, GuardianPath.BUFF_BRACE), "Brace is up");

        // Mana Weave and the Mirror Ward
        ServerPlayer w = guardian(helper, Subclass.SPELLWARD_WARDEN);
        float mana = SystemAPI.get(w).maxMana();
        SkillAPI.learn(w, GuardianPath.MANA_WEAVE, 5, SkillEvent.Source.OTHER);
        helper.assertTrue(Math.abs(SystemAPI.get(w).maxMana() - mana - 20) < 0.01f, "Mana Weave 5 adds 20 mana: " + (SystemAPI.get(w).maxMana() - mana));
        SkillAPI.learn(w, GuardianPath.MIRROR_WARD, 1, SkillEvent.Source.OTHER);
        refill(w);
        helper.assertTrue(SkillAPI.cast(w, GuardianPath.MIRROR_WARD) && SkillAPI.hasBuff(w, GuardianPath.BUFF_MIRROR_WARD), "Mirror Ward is up");

        // Wrath, Blood Debt and the Searing Brand
        ServerPlayer k = guardian(helper, Subclass.VENGEANCE_KNIGHT);
        Zombie z = zombie(helper, k, new BlockPos(3, 2, 1));
        z.hurt(k.damageSources().playerAttack(k), 10);
        float normal = 2000 - z.getHealth();
        SkillAPI.learn(k, GuardianPath.BLOOD_DEBT, 10, SkillEvent.Source.OTHER);
        k.setHealth(k.getMaxHealth() * 0.2f);
        z.setHealth(2000);
        z.invulnerableTime = 0;
        z.hurt(k.damageSources().playerAttack(k), 10);
        float debt = 2000 - z.getHealth();
        helper.assertTrue(debt > normal * 1.25f, "Blood Debt adds damage when hurt: " + debt + " vs " + normal);
        k.setHealth(k.getMaxHealth());
        SkillAPI.learn(k, GuardianPath.WRATH, 10, SkillEvent.Source.OTHER);
        SystemAPI.setManaBonus(k, 1000);
        refill(k);
        helper.assertTrue(SkillAPI.cast(k, GuardianPath.WRATH) && SkillAPI.hasBuff(k, GuardianPath.BUFF_WRATH), "Wrath is up");
        z.setHealth(2000);
        z.invulnerableTime = 0;
        z.hurt(k.damageSources().playerAttack(k), 10);
        float wrath = 2000 - z.getHealth();
        helper.assertTrue(wrath > normal * 1.45f, "Wrath adds damage: " + wrath + " vs " + normal);
        SkillAPI.learn(k, GuardianPath.SEARING_BRAND, 10, SkillEvent.Source.OTHER);
        refill(k);
        helper.assertTrue(SkillAPI.cast(k, GuardianPath.SEARING_BRAND), "Searing Brand casts");
        z.setHealth(2000);
        z.invulnerableTime = 0;
        z.clearFire();
        z.hurt(k.damageSources().playerAttack(k), 10);
        helper.assertTrue(2000 - z.getHealth() > wrath * 1.1f, "the branded take more: " + (2000 - z.getHealth()) + " vs " + wrath);
        helper.succeed();
    }
}
