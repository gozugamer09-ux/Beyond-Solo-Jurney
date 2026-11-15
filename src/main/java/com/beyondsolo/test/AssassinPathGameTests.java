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
import com.beyondsolo.skill.AssassinPath;
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

/** The Assassin subclasses' skill sets. Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AssassinPathGameTests {
    private static final String ARENA = "evaluation_zone";
    private static final Subclass[] ASSASSINS = {Subclass.NIGHTBLADE, Subclass.TOXIN_WEAVER, Subclass.MIRAGE_DANCER, Subclass.BOUNTY_REAPER, Subclass.ECLIPSE_SOVEREIGN};

    private static ServerPlayer assassin(GameTestHelper helper, Subclass sub) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "assassin"));
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
        SystemAPI.awaken(p, PlayerRank.E, PlayerClass.ASSASSIN);
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
    public static void assassinSubclassesHaveTwelveSkills(GameTestHelper helper) {
        for (Subclass sub : ASSASSINS) {
            List<Skill> all = Skills.forSubclass(sub);
            long actives = all.stream().filter(Skill::isActive).count();
            helper.assertTrue(all.size() >= 12, sub + " has at least 12 skills, found " + all.size());
            helper.assertTrue(actives >= 8 && all.size() - actives >= 4, sub + " has 8 actives and 4 passives, found " + actives + " actives");
            for (Skill s : all) {
                helper.assertTrue(s.element() == sub.element(), s.path() + " carries its subclass's element");
                helper.assertTrue(s.owner() == PlayerClass.ASSASSIN, s.path() + " belongs to assassins");
            }
        }
        ServerPlayer p = assassin(helper, Subclass.NIGHTBLADE);
        helper.assertTrue(!SkillAPI.knows(p, AssassinPath.SHADOW_FANG), "Shadow Fang waits for its level");
        for (int i = 0; i < 2000 && SystemAPI.get(p).level() < 50; i++) SystemAPI.grantXp(p, 20000, SystemAPI.XP_SOURCE_COMMAND);
        helper.assertTrue(Skills.forSubclass(Subclass.NIGHTBLADE).stream().allMatch(s -> SkillAPI.knows(p, s)), "everything by level 50");
        helper.assertTrue(!SkillAPI.knows(p, AssassinPath.VENOM_SPIT), "other subclasses' skills stay out");
        helper.succeed();
    }

    /** Every new active of every assassin subclass casts and hurts the creature in front, one after the other. */
    @GameTest(template = ARENA, timeoutTicks = 3000)
    public static void assassinSubclassSkillsCastAndHit(GameTestHelper helper) {
        List<Skill> queue = new ArrayList<>();
        for (Subclass sub : ASSASSINS) for (Skill s : Skills.forSubclass(sub)) if (s.isActive() && !s.advanced()) queue.add(s);
        helper.assertTrue(queue.size() == 35, "35 new assassin subclass actives, found " + queue.size());
        castNext(helper, queue, 0, new ArrayList<>());
    }

    private static void castNext(GameTestHelper helper, List<Skill> queue, int index, List<String> failed) {
        if (index >= queue.size()) {
            helper.assertTrue(failed.isEmpty(), "assassin subclass skills that failed: " + failed);
            helper.succeed();
            return;
        }
        Skill s = queue.get(index);
        ServerPlayer p = assassin(helper, s.subclass());
        SkillAPI.learn(p, s, 1, SkillEvent.Source.OTHER);
        Zombie z = zombie(helper, p, new BlockPos(3, 2, 1));
        refill(p);
        boolean cast = SkillAPI.cast(p, s);
        if (!cast) failed.add(s.path() + " did not cast");
        boolean harmless = s == AssassinPath.DUSK_SHROUD || s == AssassinPath.MIRROR_IMAGE || s == AssassinPath.MOONLESS_VEIL;
        // these land their damage late
        boolean slow = s == AssassinPath.UMBRAL_DECOY || s == AssassinPath.NEUROTOXIN || s == AssassinPath.ENDLESS_NIGHT
                || s == AssassinPath.BLACK_SUN || s == AssassinPath.WORLD_WITHOUT_LIGHT || s == AssassinPath.TEMPEST_WALTZ;
        helper.runAfterDelay(slow ? 95 : 24, () -> {
            if (cast && !harmless && z.getHealth() >= 2000) failed.add(s.path() + " did no damage");
            z.discard();
            JobAPI.reset(p);
            helper.runAfterDelay(slow ? 20 : 4, () -> castNext(helper, queue, index + 1, failed));
        });
    }

    @GameTest(template = ARENA)
    public static void assassinPassivesAndMarks(GameTestHelper helper) {
        // Dusk Shroud: the first strike out of it is stronger, the next is not
        ServerPlayer p = assassin(helper, Subclass.NIGHTBLADE);
        SkillAPI.learn(p, AssassinPath.DUSK_SHROUD, 10, SkillEvent.Source.OTHER);
        Zombie z = zombie(helper, p, new BlockPos(3, 2, 1));
        z.hurt(p.damageSources().playerAttack(p), 10);
        float normal = 2000 - z.getHealth();
        refill(p);
        helper.assertTrue(SkillAPI.cast(p, AssassinPath.DUSK_SHROUD), "Dusk Shroud casts");
        helper.assertTrue(SkillAPI.hasBuff(p, AssassinPath.BUFF_SHROUD), "shrouded");
        z.setHealth(2000);
        z.invulnerableTime = 0;
        z.hurt(p.damageSources().playerAttack(p), 10);
        float ambush = 2000 - z.getHealth();
        helper.assertTrue(ambush > normal * 1.8f, "the strike out of the shroud is much stronger: " + ambush + " vs " + normal);
        helper.assertTrue(!SkillAPI.hasBuff(p, AssassinPath.BUFF_SHROUD), "the shroud is spent");
        z.setHealth(2000);
        z.invulnerableTime = 0;
        z.hurt(p.damageSources().playerAttack(p), 10);
        helper.assertTrue(Math.abs(2000 - z.getHealth() - normal) < 0.5f, "the next strike is normal again");

        // Killer Instinct: more damage to a creature that is not hunting the player
        SkillAPI.learn(p, AssassinPath.KILLER_INSTINCT, 10, SkillEvent.Source.OTHER);
        z.setHealth(2000);
        z.invulnerableTime = 0;
        z.hurt(p.damageSources().playerAttack(p), 10);
        helper.assertTrue(2000 - z.getHealth() > normal * 1.15f, "Killer Instinct adds damage: " + (2000 - z.getHealth()) + " vs " + normal);
        z.discard();

        // attribute passives
        ServerPlayer d = assassin(helper, Subclass.MIRAGE_DANCER);
        double fall = d.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
        SkillAPI.learn(d, AssassinPath.FEATHERFALL, 4, SkillEvent.Source.OTHER);
        helper.assertTrue(Math.abs(d.getAttributeValue(Attributes.SAFE_FALL_DISTANCE) - fall - 4) < 0.01, "Featherfall 4 adds 4 blocks of safe fall");
        SkillAPI.learn(d, AssassinPath.MIRROR_IMAGE, 1, SkillEvent.Source.OTHER);
        refill(d);
        helper.assertTrue(SkillAPI.cast(d, AssassinPath.MIRROR_IMAGE) && SkillAPI.hasBuff(d, AssassinPath.BUFF_MIRROR), "Mirror Image is up");

        // Final Contract: more damage from the hunter, and the kill pays in full
        ServerPlayer b = assassin(helper, Subclass.BOUNTY_REAPER);
        SkillAPI.learn(b, AssassinPath.FINAL_CONTRACT, 1, SkillEvent.Source.OTHER);
        Zombie target = zombie(helper, b, new BlockPos(3, 2, 5));
        BlockPos near = helper.absolutePos(new BlockPos(1, 2, 5));
        b.moveTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
        zombie(helper, b, new BlockPos(3, 2, 5)).discard();
        refill(b);
        helper.assertTrue(SkillAPI.cast(b, AssassinPath.FINAL_CONTRACT), "Final Contract casts");
        helper.assertTrue(SkillAPI.get(b).cooldownLeft(AssassinPath.FINAL_CONTRACT.path(), helper.getLevel().getGameTime()) > 0, "on cooldown");
        target.invulnerableTime = 0;
        target.hurt(b.damageSources().playerAttack(b), 10);
        helper.assertTrue(2000 - target.getHealth() > normal * 1.25f, "the contract adds damage: " + (2000 - target.getHealth()) + " vs " + normal);
        b.setHealth(4);
        target.setHealth(1);
        target.invulnerableTime = 0;
        target.hurt(b.damageSources().playerAttack(b), 50);
        helper.assertTrue(!target.isAlive(), "the target falls");
        helper.assertTrue(b.getHealth() == b.getMaxHealth(), "paid in full: healed");
        helper.assertTrue(SkillAPI.get(b).cooldownLeft(AssassinPath.FINAL_CONTRACT.path(), helper.getLevel().getGameTime()) == 0, "paid in full: skills ready");
        helper.succeed();
    }
}
