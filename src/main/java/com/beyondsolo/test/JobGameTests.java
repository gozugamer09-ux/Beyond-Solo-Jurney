package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.job.JobAPI;
import com.beyondsolo.job.JobModule;
import com.beyondsolo.job.JobTrial;
import com.beyondsolo.job.Subclass;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.SkillAPI;
import com.beyondsolo.skill.Skills;
import com.beyondsolo.skill.SubclassSkills;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.Stat;
import com.beyondsolo.system.SystemAPI;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Job change tests. Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JobGameTests {
    private static final String ARENA = "evaluation_zone";

    private static ServerPlayer player(GameTestHelper helper, PlayerClass playerClass) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "aspirant"));
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
        SystemAPI.awaken(p, PlayerRank.C, playerClass);
        return p;
    }

    private static void face(ServerPlayer p, LivingEntity e) {
        Vec3 d = e.getEyePosition().subtract(p.getEyePosition());
        float yaw = (float) (net.minecraft.util.Mth.atan2(d.z, d.x) * net.minecraft.util.Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) (-(net.minecraft.util.Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * net.minecraft.util.Mth.RAD_TO_DEG));
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.setYHeadRot(yaw);
    }

    private static boolean hasKey(ServerPlayer p) {
        return p.getInventory().contains(new ItemStack(JobModule.ASCENSION_KEY.get()));
    }

    @GameTest(template = ARENA)
    public static void everyClassHasFiveSubclassesOneRare(GameTestHelper helper) {
        helper.assertTrue(Subclass.values().length == 30, "30 subclasses");
        for (PlayerClass base : PlayerClass.values()) {
            List<Subclass> subs = Subclass.of(base);
            helper.assertTrue(subs.size() == 5, base + " should have 5 subclasses, has " + subs.size());
            helper.assertTrue(subs.stream().filter(Subclass::rare).count() == 1, base + " should have exactly one rare subclass");
            helper.assertTrue(Subclass.rareOf(base).base() == base, "rare subclass belongs to its class");
            for (Subclass s : subs) {
                helper.assertTrue(Subclass.byName(s.key()) == s, "byName round trip for " + s);
                helper.assertTrue(!Skills.forSubclass(s).isEmpty(), s + " has starter skills");
                helper.assertTrue(Skills.forSubclass(s).stream().anyMatch(Skill::isActive), s + " has an active skill");
            }
        }
        helper.assertTrue(Subclass.NECROMANCER.base() == PlayerClass.MAGE && !Subclass.NECROMANCER.rare(),
                "the necromancer is a normal mage subclass");
        for (Subclass s : Subclass.values()) helper.assertTrue(s.canRaiseShadows() == (s == Subclass.NECROMANCER), "only necromancers raise shadows");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void rareSubclassIsRareAndOthersAreEven(GameTestHelper helper) {
        RandomSource r = RandomSource.create(42);
        for (PlayerClass base : PlayerClass.values()) {
            Subclass rare = Subclass.rareOf(base);
            Map<Subclass, Integer> count = new EnumMap<>(Subclass.class);
            int rolls = 40_000;
            for (int i = 0; i < rolls; i++) count.merge(Subclass.roll(base, r, 0.01), 1, Integer::sum);
            int rareCount = count.getOrDefault(rare, 0);
            helper.assertTrue(rareCount > 200 && rareCount < 600, base + ": rare rolled " + rareCount + " / " + rolls + ", expected about 1%");
            for (Subclass s : Subclass.of(base)) {
                if (s.rare()) continue;
                int c = count.getOrDefault(s, 0);
                helper.assertTrue(c > rolls * 0.22 && c < rolls * 0.27, s + " rolled " + c + ", expected about 24.75%");
            }
            helper.assertTrue(count.keySet().stream().allMatch(s -> s.base() == base), "rolls stay in the base class");
            for (int i = 0; i < 50; i++) helper.assertTrue(Subclass.roll(base, r, 0) != rare, "no rare roll at chance 0");
            for (int i = 0; i < 50; i++) helper.assertTrue(Subclass.roll(base, r, 1) == rare, "always rare at chance 1");
        }
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void unlockGivesKeyAndQuest(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerClass.WARRIOR);
        helper.assertTrue(!JobAPI.unlockIfReady(p), "level 1 is too low");
        helper.assertTrue(!hasKey(p), "no key yet");
        int need = JobAPI.unlockLevel();
        while (SystemAPI.get(p).level() < need) SystemAPI.grantXp(p, 100_000, BeyondSolo.id("test"));
        helper.assertTrue(JobAPI.get(p).unlocked(), "levelling up to " + need + " opens the trial");
        helper.assertTrue(hasKey(p), "the key is handed over");
        helper.assertTrue(QuestEngine.data(p).has(JobModule.QUEST_TEMPLATE), "the job change quest starts");
        helper.assertTrue(JobAPI.canAttemptTrial(p), "may attempt the trial");
        p.getInventory().clearContent();
        JobAPI.giveKey(p);
        helper.assertTrue(hasKey(p), "a lost key is given back");
        JobAPI.giveKey(p);
        helper.assertTrue(p.getInventory().countItem(JobModule.ASCENSION_KEY.get()) == 1, "never two keys");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void changeJobGrantsSkillsAndStats(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerClass.MAGE);
        JobAPI.unlock(p);
        int before = 0;
        for (Stat s : Stat.values()) before += SystemAPI.get(p).stat(s);
        int intBefore = SystemAPI.get(p).stat(Stat.INT);
        JobAPI.changeJob(p, Subclass.NECROMANCER);
        int after = 0;
        for (Stat s : Stat.values()) after += SystemAPI.get(p).stat(s);
        helper.assertTrue(after - before == Subclass.NECROMANCER.bonusPoints(), "bonus stats: " + (after - before));
        helper.assertTrue(SystemAPI.get(p).stat(Stat.INT) > intBefore, "a necromancer gains intelligence");
        helper.assertTrue(JobAPI.subclass(p) == Subclass.NECROMANCER, "subclass set");
        helper.assertTrue(JobAPI.hasChangedJob(p), "job changed");
        helper.assertTrue(SkillAPI.knows(p, SubclassSkills.SOUL_HARVEST), "learns Soul Harvest");
        helper.assertTrue(SkillAPI.knows(p, SubclassSkills.GRAVE_PACT), "learns Grave Pact");
        helper.assertTrue(!SkillAPI.knows(p, SubclassSkills.INFERNO_PILLAR), "not a pyromancer skill");
        for (Skill s : Skills.advancedFor(PlayerClass.MAGE)) helper.assertTrue(SkillAPI.knows(p, s), "advanced skill " + s.path());
        helper.assertTrue(!hasKey(p), "the key is used up");
        helper.assertTrue(!JobAPI.canAttemptTrial(p), "no second trial");

        // a rare subclass gives more
        ServerPlayer q = player(helper, PlayerClass.RANGER);
        int b2 = 0;
        for (Stat s : Stat.values()) b2 += SystemAPI.get(q).stat(s);
        JobAPI.changeJob(q, Subclass.HEAVENPIERCER);
        int a2 = 0;
        for (Stat s : Stat.values()) a2 += SystemAPI.get(q).stat(s);
        helper.assertTrue(a2 - b2 == Subclass.RARE_BONUS_POINTS, "rare bonus stats: " + (a2 - b2));
        helper.assertTrue(Skills.forSubclass(Subclass.HEAVENPIERCER).stream().allMatch(s -> SkillAPI.knows(q, s)), "all rare skills learned");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void switchingSubclassForgetsTheOldSkills(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerClass.WARRIOR);
        JobAPI.changeJob(p, Subclass.BLOODRAGER);
        helper.assertTrue(SkillAPI.knows(p, SubclassSkills.BLOOD_FRENZY), "bloodrager skill");
        JobAPI.changeJob(p, Subclass.BLADEMASTER);
        helper.assertTrue(!SkillAPI.knows(p, SubclassSkills.BLOOD_FRENZY), "old subclass skill forgotten");
        helper.assertTrue(SkillAPI.knows(p, SubclassSkills.THOUSAND_CUTS), "new subclass skill learned");
        JobAPI.reset(p);
        helper.assertTrue(!SkillAPI.knows(p, SubclassSkills.THOUSAND_CUTS), "reset forgets subclass skills");
        helper.assertTrue(JobAPI.subclass(p) == null, "reset clears the subclass");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void subclassSkillsCast(GameTestHelper helper) {
        List<String> failed = new java.util.ArrayList<>();
        for (Subclass sub : Subclass.values()) {
            ServerPlayer p = player(helper, sub.base());
            JobAPI.changeJob(p, sub);
            for (Skill s : Skills.forSubclass(sub)) {
                if (!s.isActive()) continue;
                // a fresh target in front for the skills that need one
                net.minecraft.world.entity.monster.Zombie z = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(3, 2, 1));
                BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
                p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
                face(p, z);
                SystemAPI.restoreMana(p, 100_000);
                SkillAPI.resetCooldowns(p);
                SystemAPI.addExhaustion(p, -100);
                if (!SkillAPI.cast(p, s)) failed.add(sub + ":" + s.path());
                z.discard();
            }
            JobAPI.reset(p);
        }
        helper.assertTrue(failed.isEmpty(), "these subclasses could not cast: " + failed);
        helper.succeed();
    }

    /** The whole trial, sped up: arrive, clear a wave, fell the Arbiter, take the verdict and come home changed. */
    @GameTest(template = ARENA, timeoutTicks = 1200)
    public static void trialRunsToTheEnd(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerClass.ASSASSIN);
        JobAPI.unlock(p);
        Vec3 home = p.position();
        helper.assertTrue(JobTrial.begin(p, true), "trial starts");
        helper.assertTrue(!JobTrial.begin(p, true), "only one at a time");
        helper.assertTrue(JobTrial.phase(p) == JobTrial.Phase.INTRO, "straight into the intro");
        helper.assertTrue(p.position().distanceTo(home) > 1000, "taken to the hall");
        helper.assertTrue(JobAPI.get(p).inTrial(), "marked as in the trial");

        int[] stage = {0};
        helper.onEachTick(() -> {
            JobTrial.Phase phase = JobTrial.phase(p);
            switch (stage[0]) {
                case 0 -> {
                    JobTrial.jump(p, "3", null);
                    stage[0] = 1;
                }
                case 1 -> {
                    // wave 3: kill every echo, the wave should clear
                    if (phase == JobTrial.Phase.WAVE) {
                        List<LivingEntity> foes = JobTrial.foes(p);
                        if (!foes.isEmpty()) foes.forEach(LivingEntity::kill);
                    } else if (phase == JobTrial.Phase.WAVE_BREAK) {
                        stage[0] = 2;
                    }
                }
                case 2 -> {
                    // after the last wave the Arbiter comes on his own
                    if (phase == JobTrial.Phase.BOSS) {
                        helper.assertTrue(JobTrial.flawless(p), "flawless so far");
                        JobTrial.foes(p).forEach(LivingEntity::kill);
                        stage[0] = 3;
                    }
                }
                case 3 -> {
                    if (phase == JobTrial.Phase.VERDICT) {
                        stage[0] = 4;
                    }
                }
                case 4 -> {
                    if (phase == null) {
                        Subclass got = JobAPI.subclass(p);
                        helper.assertTrue(got != null && got.base() == PlayerClass.ASSASSIN, "became an assassin subclass: " + got);
                        helper.assertTrue(!JobAPI.get(p).inTrial(), "no longer in the trial");
                        helper.assertTrue(p.position().distanceTo(home) < 2, "back where they started");
                        helper.assertTrue(Skills.forSubclass(got).stream().allMatch(s -> SkillAPI.knows(p, s)), "subclass skills learned");
                        helper.succeed();
                    } else if (phase == JobTrial.Phase.VERDICT) {
                        JobTrial.skipReveal(p);
                    }
                }
                default -> {}
            }
        });
    }

    /** Commands can force the verdict and the result. */
    @GameTest(template = ARENA, timeoutTicks = 800)
    public static void jumpToVerdictForcesResult(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerClass.HEALER);
        JobAPI.unlock(p);
        helper.assertTrue(!JobTrial.jump(p, "verdict", Subclass.DAWN_SAINT), "nothing to jump without a trial");
        JobTrial.begin(p, true);
        helper.assertTrue(JobTrial.jump(p, "verdict", Subclass.DAWN_SAINT), "jump works");
        helper.assertTrue(JobTrial.phase(p) == JobTrial.Phase.VERDICT, "at the verdict");
        helper.onEachTick(() -> {
            if (JobTrial.phase(p) == JobTrial.Phase.VERDICT) {
                JobTrial.skipReveal(p);
            } else if (JobTrial.phase(p) == null) {
                helper.assertTrue(JobAPI.subclass(p) == Subclass.DAWN_SAINT, "forced result: " + JobAPI.subclass(p));
                helper.succeed();
            }
        });
    }

    /** Dying in the trial throws the player out alive, and they have to wait to try again. */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void fallingInTheTrialSendsYouHome(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerClass.GUARDIAN);
        JobAPI.unlock(p);
        Vec3 home = p.position();
        JobTrial.begin(p, true);
        JobTrial.jump(p, "1", null);
        helper.runAfterDelay(5, () -> {
            // fake players can't be hurt, so post the death the way a fatal hit would
            var death = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                    new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(p, p.damageSources().genericKill()));
            helper.assertTrue(death.isCanceled(), "the death is cancelled");
            helper.assertTrue(p.isAlive(), "the player survives");
            helper.assertTrue(!JobTrial.inTrial(p), "trial over");
            helper.assertTrue(!JobAPI.get(p).inTrial(), "flag cleared");
            helper.assertTrue(p.position().distanceTo(home) < 2, "sent home");
            helper.assertTrue(JobAPI.subclass(p) == null, "no subclass");
            helper.assertTrue(!JobAPI.canAttemptTrial(p), "must wait before retrying");
            helper.assertTrue(hasKey(p), "keeps the key");
            helper.succeed();
        });
    }
}
