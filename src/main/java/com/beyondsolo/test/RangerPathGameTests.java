package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.job.JobAPI;
import com.beyondsolo.job.Subclass;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.skill.RangerPath;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.SkillAPI;
import com.beyondsolo.skill.SkillData;
import com.beyondsolo.skill.SkillEvent;
import com.beyondsolo.skill.Skills;
import com.beyondsolo.skill.SubclassActions;
import com.beyondsolo.skill.SubclassSkills;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.mojang.authlib.GameProfile;
import dev.shadowsoffire.apothic_attributes.api.ALObjects;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The Ranger subclasses' skill sets. Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RangerPathGameTests {
    private static final String ARENA = "evaluation_zone";
    private static final Subclass[] RANGERS = {Subclass.SHARPSHOOTER, Subclass.WILDCALLER, Subclass.SNARE_ARTISAN, Subclass.STORM_ARCHER, Subclass.HEAVENPIERCER};
    private static final SkillEvent.Source OTHER = SkillEvent.Source.OTHER;
    /**
     * How far above its arena {@link #rangerSubclassSkillsCastAndHit} stands. A ranger reaches thirty and forty
     * blocks, the arenas of the other tests stand a dozen blocks away with open sides, and their creatures are to
     * be left alone: up here there is nothing else within reach, and nothing else reaches up here.
     */
    private static final int SKY = 48;

    private static ServerPlayer ranger(GameTestHelper helper, Subclass sub) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ranger"));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        SkillAPI.reset(p);
        JobAPI.reset(p);
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        p.removeAllEffects();
        stand(helper, p, new BlockPos(1, 2, 1));
        QuestTestAccess.markStarted(p);
        SystemAPI.awaken(p, PlayerRank.E, PlayerClass.RANGER);
        // no lucky critical hits: the tests compare damage numbers
        AttributeInstance crit = p.getAttribute(ALObjects.Attributes.CRIT_CHANCE);
        if (crit != null) {
            crit.removeModifiers();
            crit.setBaseValue(-10);
        }
        if (sub != null) JobAPI.changeJob(p, sub);
        return p;
    }

    private static void stand(GameTestHelper helper, ServerPlayer p, BlockPos on) {
        BlockPos at = helper.absolutePos(on);
        p.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
    }

    private static void look(ServerPlayer p, Vec3 at) {
        Vec3 d = at.subtract(p.getEyePosition());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) (-(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG));
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.setYHeadRot(yaw);
    }

    private static void face(ServerPlayer p, LivingEntity target) {
        look(p, target.getEyePosition());
    }

    private static <T extends LivingEntity> T sturdy(T creature) {
        creature.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);
        creature.setHealth(2000);
        return creature;
    }

    private static Zombie zombie(GameTestHelper helper, ServerPlayer p, BlockPos at) {
        Zombie z = sturdy(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, at));
        face(p, z);
        return z;
    }

    private static void refill(ServerPlayer p) {
        SystemAPI.restoreMana(p, 100_000);
        SkillAPI.resetCooldowns(p);
        SystemAPI.addExhaustion(p, -100);
    }

    /** What a plain blow of 10 from the player takes off a creature that starts at {@code health}. */
    private static float strike(ServerPlayer p, LivingEntity target, float health) {
        target.setHealth(health);
        target.invulnerableTime = 0;
        target.hurt(p.damageSources().playerAttack(p), 10);
        return health - target.getHealth();
    }

    /** What a bite of 10 from a beast takes off a creature at full health. */
    private static float bite(LivingEntity beast, LivingEntity target) {
        target.setHealth(2000);
        target.invulnerableTime = 0;
        target.hurt(beast.damageSources().mobAttack(beast), 10);
        return 2000 - target.getHealth();
    }

    /** The player kills a creature with one blow. */
    private static void kill(GameTestHelper helper, ServerPlayer p, LivingEntity victim) {
        victim.setHealth(1);
        victim.invulnerableTime = 0;
        victim.hurt(p.damageSources().playerAttack(p), 50);
        helper.assertTrue(!victim.isAlive(), "the blow kills");
    }

    private static float mana(ServerPlayer p) {
        return SystemAPI.get(p).mana();
    }

    @GameTest(template = ARENA)
    public static void rangerSubclassesHaveTwelveSkills(GameTestHelper helper) {
        for (Subclass sub : RANGERS) {
            List<Skill> all = Skills.forSubclass(sub);
            long actives = all.stream().filter(Skill::isActive).count();
            helper.assertTrue(all.size() >= 12, sub + " has at least 12 skills, found " + all.size());
            helper.assertTrue(actives >= 8 && all.size() - actives >= 4, sub + " has 8 actives and 4 passives, found " + actives + " actives");
            for (Skill s : all) {
                helper.assertTrue(s.element() == sub.element(), s.path() + " carries its subclass's element");
                helper.assertTrue(s.owner() == PlayerClass.RANGER, s.path() + " belongs to rangers");
            }
        }
        ServerPlayer p = ranger(helper, Subclass.SHARPSHOOTER);
        helper.assertTrue(!SkillAPI.knows(p, RangerPath.RICOCHET), "Ricochet waits for its level");
        for (int i = 0; i < 2000 && SystemAPI.get(p).level() < 50; i++) SystemAPI.grantXp(p, 20000, SystemAPI.XP_SOURCE_COMMAND);
        helper.assertTrue(Skills.forSubclass(Subclass.SHARPSHOOTER).stream().allMatch(s -> SkillAPI.knows(p, s)), "everything by level 50");
        helper.assertTrue(!SkillAPI.knows(p, RangerPath.HAWK_STRIKE), "other subclasses' skills stay out");
        helper.succeed();
    }

    /** Every new active of every ranger subclass casts and hurts the creature in front, one after the other. */
    @GameTest(template = ARENA, timeoutTicks = 3600)
    public static void rangerSubclassSkillsCastAndHit(GameTestHelper helper) {
        List<Skill> queue = new ArrayList<>();
        for (Subclass sub : RANGERS) for (Skill s : Skills.forSubclass(sub)) if (s.isActive() && !s.advanced()) queue.add(s);
        helper.assertTrue(queue.size() == 35, "35 new ranger subclass actives, found " + queue.size());
        castNext(helper, queue, 0, new ArrayList<>());
    }

    private static void castNext(GameTestHelper helper, List<Skill> queue, int index, List<String> failed) {
        if (index >= queue.size()) {
            helper.assertTrue(failed.isEmpty(), "ranger subclass skills that failed: " + failed);
            helper.succeed();
            return;
        }
        Skill s = queue.get(index);
        ServerLevel level = helper.getLevel();
        ServerPlayer p = ranger(helper, s.subclass());
        stand(helper, p, new BlockPos(1, 2 + SKY, 1));
        SkillAPI.learn(p, s, 1, OTHER);
        // a husk, because the sun is on it up here, and weightless, because there is nothing to stand on
        LivingEntity target = sturdy(helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(3, 2 + SKY, 1)));
        target.setNoGravity(true);
        face(p, target);
        refill(p);
        boolean cast = SkillAPI.cast(p, s);
        if (!cast) failed.add(s.path() + " did not cast");
        // these only ready the hunter or the pack
        boolean harmless = s == RangerPath.TAKE_AIM || s == RangerPath.RALLYING_HOWL || s == RangerPath.LIVE_WIRE || s == RangerPath.ASCEND;
        // a test player never comes down from a leap: the pounce gives up waiting after three seconds and lands
        boolean slow = s == RangerPath.POUNCE;
        helper.runAfterDelay(slow ? 75 : 24, () -> {
            if (cast && !harmless && target.getHealth() >= 2000) failed.add(s.path() + " did no damage");
            target.discard();
            JobAPI.reset(p);
            // their hunter leaves, and what is still going on (storms, halos, hunts) ends with them, so that the
            // next skill is judged on its own damage
            p.discard();
            // the rocks of a deadfall would fall all the way down to the arenas
            AABB below = new AABB(p.blockPosition()).inflate(12, 0, 12).expandTowards(0, 12, 0).expandTowards(0, -SKY - 8, 0);
            for (FallingBlockEntity rock : level.getEntitiesOfClass(FallingBlockEntity.class, below)) rock.discard();
            helper.runAfterDelay(4, () -> castNext(helper, queue, index + 1, failed));
        });
    }

    @GameTest(template = ARENA)
    public static void sharpshooterAimsAndFinishes(GameTestHelper helper) {
        long now = helper.getLevel().getGameTime();

        // Marksman's Eye: 15% critical chance and 30% critical damage at level 10
        ServerPlayer eye = ranger(helper, Subclass.SHARPSHOOTER);
        AttributeInstance chance = eye.getAttribute(ALObjects.Attributes.CRIT_CHANCE);
        chance.setBaseValue(0.05);
        double chanceBefore = chance.getValue(), critBefore = eye.getAttributeValue(ALObjects.Attributes.CRIT_DAMAGE);
        SkillAPI.learn(eye, RangerPath.MARKSMANS_EYE, 10, OTHER);
        helper.assertTrue(Math.abs(chance.getValue() - chanceBefore - 0.15) < 1.0e-3, "Marksman's Eye 10 adds 15% critical chance: " + (chance.getValue() - chanceBefore));
        double critAfter = eye.getAttributeValue(ALObjects.Attributes.CRIT_DAMAGE);
        helper.assertTrue(Math.abs(critAfter - critBefore - 0.30) < 1.0e-3, "Marksman's Eye 10 adds 30% critical damage: " + (critAfter - critBefore));

        // Patient Hunter: 5% for each second stood still at level 10, up to 25%, and gone with the first step
        ServerPlayer p = ranger(helper, Subclass.SHARPSHOOTER);
        Zombie z = zombie(helper, p, new BlockPos(3, 2, 1));
        float normal = strike(p, z, 2000);
        helper.assertTrue(normal > 1, "a plain blow lands: " + normal);
        SkillAPI.learn(p, RangerPath.PATIENT_HUNTER, 10, OTHER);
        SkillData data = SkillAPI.get(p);
        helper.assertTrue(strike(p, z, 2000) < normal * 1.02f, "Patient Hunter waits for stillness");
        // the first second only notes where they stand
        for (int i = 0; i < 4; i++) RangerPath.tickSecond(p, data);
        float three = strike(p, z, 2000);
        helper.assertTrue(three > normal * 1.13f && three < normal * 1.17f, "three seconds still, 15% more: " + three + " vs " + normal);
        for (int i = 0; i < 6; i++) RangerPath.tickSecond(p, data);
        float full = strike(p, z, 2000);
        helper.assertTrue(full > normal * 1.23f && full < normal * 1.27f, "it stops at 25%: " + full + " vs " + normal);
        p.moveTo(p.getX(), p.getY(), p.getZ() + 1);
        RangerPath.tickSecond(p, data);
        helper.assertTrue(strike(p, z, 2000) < normal * 1.02f, "a step and it is gone");

        // Take Aim: the next shot hits 46% harder at level 1, and the aim is spent on it
        ServerPlayer plain = ranger(helper, Subclass.SHARPSHOOTER);
        SkillAPI.learn(plain, RangerPath.PINNING_SHOT, 1, OTHER);
        refill(plain);
        face(plain, z);
        z.setHealth(2000);
        helper.assertTrue(SkillAPI.cast(plain, RangerPath.PINNING_SHOT), "Pinning Shot casts");
        float shot = 2000 - z.getHealth();
        helper.assertTrue(shot > 1, "Pinning Shot lands: " + shot);
        helper.assertTrue(RangerPath.caught(z, now), "and pins what it hits");
        ServerPlayer aimed = ranger(helper, Subclass.SHARPSHOOTER);
        SkillAPI.learn(aimed, RangerPath.TAKE_AIM, 1, OTHER);
        SkillAPI.learn(aimed, RangerPath.PINNING_SHOT, 1, OTHER);
        refill(aimed);
        face(aimed, z);
        z.setHealth(2000);
        helper.assertTrue(SkillAPI.cast(aimed, RangerPath.TAKE_AIM) && SkillAPI.hasBuff(aimed, RangerPath.BUFF_TAKE_AIM), "Take Aim casts");
        helper.assertTrue(z.getHealth() >= 2000, "taking aim hurts nobody");
        helper.assertTrue(SkillAPI.cast(aimed, RangerPath.PINNING_SHOT), "Pinning Shot casts after Take Aim");
        float aimedShot = 2000 - z.getHealth();
        helper.assertTrue(aimedShot > shot * 1.4f, "the aimed shot hits harder: " + aimedShot + " vs " + shot);
        helper.assertTrue(!SkillAPI.hasBuff(aimed, RangerPath.BUFF_TAKE_AIM), "and the aim is spent");

        // a skill's arrow is a projectile like one from the bow: Hawk Focus (24% at level 10, 6% at level 1) counts it
        ServerPlayer hawk = ranger(helper, Subclass.SHARPSHOOTER);
        SkillAPI.learn(hawk, RangerPath.PINNING_SHOT, 1, OTHER);
        // every sharpshooter has it at level 1 from the day they took the job
        helper.assertTrue(SkillAPI.addLevels(hawk, SubclassSkills.HAWK_FOCUS, 9) == 9, "Hawk Focus rises to level 10");
        refill(hawk);
        face(hawk, z);
        z.setHealth(2000);
        helper.assertTrue(SkillAPI.cast(hawk, RangerPath.PINNING_SHOT), "Pinning Shot casts with Hawk Focus");
        float focused = 2000 - z.getHealth();
        helper.assertTrue(focused > shot * 1.12f, "Hawk Focus counts a skill's arrow: " + focused + " vs " + shot);

        // Clean Kill: a kill gives 8 mana back at level 5 and has the Deadeye Shot ready again
        ServerPlayer c = ranger(helper, Subclass.SHARPSHOOTER);
        SkillAPI.learn(c, SubclassSkills.DEADEYE_SHOT, 1, OTHER);
        SkillAPI.learn(c, RangerPath.CLEAN_KILL, 5, OTHER);
        refill(c);
        // at its feet, so that the shot ends in the floor and not in some other test's creature
        look(c, z.position().add(0, 0.4, 0));
        z.setHealth(2000);
        helper.assertTrue(SkillAPI.cast(c, SubclassSkills.DEADEYE_SHOT) && z.getHealth() < 2000, "Deadeye Shot casts and lands");
        helper.assertTrue(SkillAPI.get(c).cooldownLeft(SubclassSkills.DEADEYE_SHOT.path(), now) > 0, "Deadeye Shot is on cooldown");
        float before = mana(c);
        kill(helper, c, helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(5, 2, 1)));
        helper.assertTrue(SkillAPI.get(c).cooldownLeft(SubclassSkills.DEADEYE_SHOT.path(), now) == 0, "a kill has the Deadeye Shot ready again");
        helper.assertTrue(mana(c) >= before + 8 - 0.01f, "and gives mana back: " + mana(c) + " from " + before);
        z.discard();

        // Killshot: two and a half times as hard on a creature under 35% of its health
        ServerPlayer k = ranger(helper, Subclass.SHARPSHOOTER);
        SkillAPI.learn(k, RangerPath.KILLSHOT, 1, OTHER);
        Zombie mark = zombie(helper, k, new BlockPos(3, 2, 5));
        refill(k);
        helper.assertTrue(SkillAPI.cast(k, RangerPath.KILLSHOT), "Killshot casts");
        helper.assertTrue(mark.getHealth() >= 2000, "it takes a breath to line up");
        helper.runAfterDelay(8, () -> {
            float whole = 2000 - mark.getHealth();
            helper.assertTrue(whole > 1, "Killshot lands: " + whole);
            mark.setHealth(600);
            refill(k);
            face(k, mark);
            helper.assertTrue(SkillAPI.cast(k, RangerPath.KILLSHOT), "Killshot casts again");
            helper.runAfterDelay(8, () -> {
                float ending = 600 - mark.getHealth();
                mark.discard();
                helper.assertTrue(ending > whole * 2.2f, "Killshot ends the wounded: " + ending + " vs " + whole);
                helper.succeed();
            });
        });
    }

    @GameTest(template = ARENA)
    public static void wildcallerRunsWithThePack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer p = ranger(helper, Subclass.WILDCALLER);
        SkillAPI.learn(p, SubclassSkills.CALL_OF_THE_PACK, 1, OTHER);
        Zombie z = zombie(helper, p, new BlockPos(3, 2, 1));
        float normal = strike(p, z, 2000);
        refill(p);
        helper.assertTrue(SkillAPI.cast(p, SubclassSkills.CALL_OF_THE_PACK), "Call of the Pack casts");
        List<Wolf> pack = level.getEntitiesOfClass(Wolf.class, p.getBoundingBox().inflate(4),
                w -> p.getUUID().equals(w.getOwnerUUID()) && w.getTags().contains(SubclassActions.SUMMON_TAG));
        helper.assertTrue(pack.size() == 2, "two wolves answer at level 1, found " + pack.size());
        Wolf wolf = pack.get(0);

        // Pack Tactics: the pack bites 35% harder at level 10, and the hunter hits what it bit 15% harder. A wolf
        // does not find a test player to call its owner, so the bite is put to the hook that the pack's bites go through.
        long now = level.getGameTime();
        helper.assertTrue(bite(wolf, z) > 1, "a wolf's bite lands");
        helper.assertTrue(Math.abs(RangerPath.packBite(p, z, 10, now) - 10) < 0.01f, "the pack bites as ever without Pack Tactics");
        helper.assertTrue(strike(p, z, 2000) < normal * 1.02f, "and leaves no mark");
        SkillAPI.learn(p, RangerPath.PACK_TACTICS, 10, OTHER);
        float packBite = RangerPath.packBite(p, z, 10, now);
        helper.assertTrue(Math.abs(packBite - 13.5f) < 0.01f, "Pack Tactics makes the pack bite harder: " + packBite);
        float marked = strike(p, z, 2000);
        helper.assertTrue(marked > normal * 1.12f, "and the hunter hits what the pack bit harder: " + marked + " vs " + normal);

        // Alpha: 5% for each beast at the hunter's side at level 10
        Zombie other = sturdy(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(5, 2, 1)));
        float alone = strike(p, other, 2000);
        SkillAPI.learn(p, RangerPath.ALPHA, 10, OTHER);
        float led = strike(p, other, 2000);
        helper.assertTrue(led > alone * 1.08f && led < alone * 1.12f, "two wolves, 10% more: " + led + " vs " + alone);

        // Wild Heart: the hunter (1.5 at level 10) and the pack (3) mend every second
        SkillAPI.learn(p, RangerPath.WILD_HEART, 10, OTHER);
        p.setHealth(5);
        wolf.setHealth(4);
        RangerPath.tickSecond(p, SkillAPI.get(p));
        helper.assertTrue(p.getHealth() > 6 && wolf.getHealth() > 6, "the Wild Heart mends hunter and pack: " + p.getHealth() + ", " + wolf.getHealth());

        // Rallying Howl: the pack is roused and stays ten seconds longer, and what stands near is cowed. Howled
        // from the middle of the arena, so that it does not carry into the next one.
        long expires = wolf.getPersistentData().getLong(SubclassActions.SUMMON_EXPIRES);
        SkillAPI.learn(p, RangerPath.RALLYING_HOWL, 1, OTHER);
        stand(helper, p, new BlockPos(3, 2, 2));
        refill(p);
        helper.assertTrue(SkillAPI.cast(p, RangerPath.RALLYING_HOWL), "Rallying Howl casts");
        helper.assertTrue(p.hasEffect(MobEffects.DAMAGE_BOOST) && wolf.hasEffect(MobEffects.DAMAGE_BOOST), "hunter and pack are roused");
        helper.assertTrue(wolf.getPersistentData().getLong(SubclassActions.SUMMON_EXPIRES) == expires + 200, "the pack stays ten seconds longer");
        helper.assertTrue(z.hasEffect(MobEffects.WEAKNESS), "and their prey is cowed");

        pack.forEach(Wolf::discard);
        z.discard();
        other.discard();
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void snareArtisanSetsTraps(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        // Patient Trapper: traps and snares hit 40% harder at level 10
        ServerPlayer t = ranger(helper, Subclass.SNARE_ARTISAN);
        SkillAPI.learn(t, RangerPath.SPRING_SNARE, 1, OTHER);
        Zombie hung = zombie(helper, t, new BlockPos(3, 2, 1));
        refill(t);
        helper.assertTrue(SkillAPI.cast(t, RangerPath.SPRING_SNARE), "Spring Snare casts");
        float snare = 2000 - hung.getHealth();
        helper.assertTrue(snare > 1 && RangerPath.caught(hung, level.getGameTime()), "the noose bites and holds: " + snare);
        SkillAPI.learn(t, RangerPath.PATIENT_TRAPPER, 10, OTHER);
        refill(t);
        hung.setHealth(2000);
        helper.assertTrue(SkillAPI.cast(t, RangerPath.SPRING_SNARE), "Spring Snare casts again");
        float patient = 2000 - hung.getHealth();
        helper.assertTrue(patient > snare * 1.3f, "the Patient Trapper's snare bites harder: " + patient + " vs " + snare);
        hung.discard();

        // Jaw Trap: lies armed, closes on what stands in it and holds it
        ServerPlayer p = ranger(helper, Subclass.SNARE_ARTISAN);
        SkillAPI.learn(p, RangerPath.JAW_TRAP, 1, OTHER);
        SkillAPI.learn(p, RangerPath.SCAVENGER, 5, OTHER);
        SkillAPI.learn(p, RangerPath.KILLING_GROUND, 10, OTHER);
        SkillData data = SkillAPI.get(p);
        Zombie z = zombie(helper, p, new BlockPos(3, 2, 1));
        DamageSource blow = p.damageSources().mobAttack(z);
        float free = RangerPath.whenStruck(p, data, z, blow, 10, level.getGameTime());
        helper.assertTrue(Math.abs(free - 10) < 0.01f, "a free creature hits as hard as ever: " + free);
        refill(p);
        helper.assertTrue(SkillAPI.cast(p, RangerPath.JAW_TRAP) && RangerPath.trapsSet(p) == 1, "Jaw Trap is set");
        helper.assertTrue(!RangerPath.caught(z, level.getGameTime()), "it is not armed at once");
        helper.runAfterDelay(16, () -> {
            long now = level.getGameTime();
            helper.assertTrue(z.getHealth() < 2000 && RangerPath.caught(z, now), "the jaws close on what stands in them: " + z.getHealth());
            helper.assertTrue(RangerPath.trapsSet(p) == 0, "and the trap is spent");
            // Killing Ground: what is caught hits 20% softer at level 10
            float held = RangerPath.whenStruck(p, data, z, blow, 10, now);
            helper.assertTrue(Math.abs(held - 8) < 0.01f, "the caught hit softer: " + held);
            // Scavenger: what dies in a trap gives 7 mana back at level 5 and has the next trap ready
            helper.assertTrue(data.cooldownLeft(RangerPath.JAW_TRAP.path(), now) > 0, "Jaw Trap is on cooldown");
            float before = mana(p);
            kill(helper, p, z);
            helper.assertTrue(data.cooldownLeft(RangerPath.JAW_TRAP.path(), now) == 0, "a kill in a trap has the next one ready");
            helper.assertTrue(mana(p) >= before + 7 - 0.01f, "and gives mana back: " + mana(p) + " from " + before);

            // a hunter keeps three set: the fourth takes up the oldest. Each is laid at their own feet.
            BlockPos[] spots = {new BlockPos(5, 2, 1), new BlockPos(5, 2, 5), new BlockPos(1, 2, 5), new BlockPos(3, 2, 5)};
            for (BlockPos spot : spots) {
                stand(helper, p, spot);
                p.setXRot(90);
                refill(p);
                helper.assertTrue(SkillAPI.cast(p, RangerPath.JAW_TRAP), "Jaw Trap is set at " + spot);
            }
            helper.assertTrue(RangerPath.trapsSet(p) == 3, "three traps lie set, found " + RangerPath.trapsSet(p));
            stand(helper, p, new BlockPos(1, 2, 1));
            Zombie first = sturdy(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, spots[0]));
            Zombie last = sturdy(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, spots[3]));
            helper.runAfterDelay(16, () -> {
                long then = level.getGameTime();
                helper.assertTrue(!RangerPath.caught(first, then), "the oldest trap was taken up");
                helper.assertTrue(RangerPath.caught(last, then) && last.getHealth() < 2000, "the newest one bites: " + last.getHealth());
                helper.assertTrue(RangerPath.trapsSet(p) == 2, "two are left, found " + RangerPath.trapsSet(p));
                first.discard();
                last.discard();

                // Tripwire: strung across the way the hunter faces, it throws down what stands in it
                SkillAPI.learn(p, RangerPath.TRIPWIRE, 1, OTHER);
                Zombie walker = zombie(helper, p, new BlockPos(3, 2, 1));
                refill(p);
                helper.assertTrue(SkillAPI.cast(p, RangerPath.TRIPWIRE), "Tripwire casts");
                helper.assertTrue(!RangerPath.caught(walker, then), "the wire takes a moment to pull tight");
                helper.runAfterDelay(12, () -> {
                    boolean tripped = walker.getHealth() < 2000 && RangerPath.caught(walker, level.getGameTime());
                    walker.discard();
                    helper.assertTrue(tripped, "the wire trips what stands in it: " + walker.getHealth());
                    helper.succeed();
                });
            });
        });
    }

    /** The rocks of a Deadfall really fall, and are gone when they land: no block and no item is left behind. */
    @GameTest(template = ARENA)
    public static void deadfallLeavesNothingBehind(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer p = ranger(helper, Subclass.SNARE_ARTISAN);
        SkillAPI.learn(p, RangerPath.DEADFALL, 1, OTHER);
        Zombie z = zombie(helper, p, new BlockPos(3, 2, 1));
        // the arena and the open ground around it, short of the arenas next door
        BlockPos low = helper.absolutePos(new BlockPos(-3, 1, -3)), high = helper.absolutePos(new BlockPos(9, 7, 9));
        AABB around = AABB.encapsulatingFullBlocks(low, high);
        int before = blocks(level, low, high);
        refill(p);
        helper.assertTrue(SkillAPI.cast(p, RangerPath.DEADFALL), "Deadfall casts");
        int rocks = level.getEntitiesOfClass(FallingBlockEntity.class, around).size();
        helper.assertTrue(rocks > 0, "rocks come down");
        helper.runAfterDelay(40, () -> {
            float health = z.getHealth();
            z.discard();
            helper.assertTrue(health < 2000, "the deadfall buries what stands under it: " + health);
            helper.assertTrue(level.getEntitiesOfClass(FallingBlockEntity.class, around).isEmpty(), "no rock is left in the air");
            helper.assertTrue(blocks(level, low, high) == before, "no rock is left lying: " + blocks(level, low, high) + " blocks, " + before + " before");
            helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, around).isEmpty(), "and none was dropped as an item");
            helper.succeed();
        });
    }

    private static int blocks(ServerLevel level, BlockPos low, BlockPos high) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(low, high)) if (!level.getBlockState(pos).isAir()) count++;
        return count;
    }

    @GameTest(template = ARENA)
    public static void stormArcherLeavesStatic(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer p = ranger(helper, Subclass.STORM_ARCHER);
        Zombie z = zombie(helper, p, new BlockPos(3, 2, 1));
        Zombie behind = sturdy(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(5, 2, 1)));
        float normal = strike(p, z, 2000);
        SkillAPI.learn(p, RangerPath.CONDUCTIVE, 10, OTHER);
        helper.assertTrue(strike(p, z, 2000) < normal * 1.02f, "Conductive waits for static");

        // Storm Battery: lightning that strikes gives 2 mana back at level 10. Two archers flash through the same
        // two creatures, one of them with the battery.
        ServerPlayer dry = ranger(helper, Subclass.STORM_ARCHER);
        SkillAPI.learn(dry, RangerPath.FLASH_STEP, 1, OTHER);
        refill(dry);
        SystemAPI.consumeMana(dry, 10);
        face(dry, z);
        z.setHealth(2000);
        double start = dry.getX();
        Vec3 drift = z.getDeltaMovement();
        helper.assertTrue(SkillAPI.cast(dry, RangerPath.FLASH_STEP), "Flash Step casts");
        helper.assertTrue(dry.getX() > start + 3, "and carries the archer forward: " + (dry.getX() - start));
        helper.assertTrue(z.getHealth() < 2000 && behind.getHealth() < 2000, "through everything on the way");
        // lightning jolts, it does not shove: a storm that struck its victims away would strike each of them once
        helper.assertTrue(z.getDeltaMovement().distanceToSqr(drift) < 1e-6, "and leaves them where they stood: " + z.getDeltaMovement() + " from " + drift);
        SkillAPI.learn(p, RangerPath.FLASH_STEP, 1, OTHER);
        SkillAPI.learn(p, RangerPath.STORM_BATTERY, 10, OTHER);
        refill(p);
        SystemAPI.consumeMana(p, 10);
        face(p, z);
        helper.assertTrue(SkillAPI.cast(p, RangerPath.FLASH_STEP), "Flash Step casts with the battery");
        helper.assertTrue(Math.abs(mana(p) - mana(dry) - 2) < 0.01f, "the Storm Battery fills by 2: " + mana(p) + " vs " + mana(dry));

        // Conductive: 20% more at level 10 against what the archer's lightning has struck
        float charged = strike(p, z, 2000);
        helper.assertTrue(charged > normal * 1.15f, "static makes the blow worse: " + charged + " vs " + normal);

        // Chain Reaction: a creature that dies with static on it passes the charge to the nearest one
        SkillAPI.learn(p, RangerPath.CHAIN_REACTION, 1, OTHER);
        float before = z.getHealth();
        kill(helper, p, behind);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(z.getHealth() < before - 1, "the charge leaps on: " + z.getHealth() + " from " + before);

            // Live Wire: a spark follows whatever the archer hits
            SkillAPI.learn(p, RangerPath.LIVE_WIRE, 1, OTHER);
            refill(p);
            helper.assertTrue(SkillAPI.cast(p, RangerPath.LIVE_WIRE) && SkillAPI.hasBuff(p, RangerPath.BUFF_LIVE_WIRE), "Live Wire casts");
            strike(p, z, 2000);
            float left = z.getHealth();
            helper.runAfterDelay(3, () -> {
                float sparked = z.getHealth();
                z.discard();
                helper.assertTrue(sparked < left - 0.5f, "a spark follows the blow: " + sparked + " from " + left);
                helper.succeed();
            });
        });
    }

    @GameTest(template = ARENA)
    public static void heavenpiercerRidesTheWind(GameTestHelper helper) {
        long now = helper.getLevel().getGameTime();
        ServerPlayer p = ranger(helper, Subclass.HEAVENPIERCER);
        SkillData data = SkillAPI.get(p);
        Zombie z = zombie(helper, p, new BlockPos(3, 2, 1));
        float normal = strike(p, z, 2000);

        // Sky's Grace: no fall hurts, and off the ground (where a test player always is) every blow is 20% harder at level 10
        DamageSource fall = p.damageSources().fall();
        helper.assertTrue(Math.abs(RangerPath.whenStruck(p, data, null, fall, 10, now) - 10) < 0.01f, "a fall hurts the graceless");
        SkillAPI.learn(p, RangerPath.SKYS_GRACE, 10, OTHER);
        helper.assertTrue(RangerPath.whenStruck(p, data, null, fall, 10, now) < 0, "the sky looks after its own");
        float graced = strike(p, z, 2000);
        helper.assertTrue(graced > normal * 1.18f && graced < normal * 1.22f, "20% more in the air: " + graced + " vs " + normal);

        // Unerring: another 20% at level 10, and 10% critical chance
        AttributeInstance chance = p.getAttribute(ALObjects.Attributes.CRIT_CHANCE);
        chance.setBaseValue(0.05);
        double chanceBefore = chance.getValue();
        SkillAPI.learn(p, RangerPath.UNERRING, 10, OTHER);
        helper.assertTrue(Math.abs(chance.getValue() - chanceBefore - 0.10) < 1.0e-3, "Unerring 10 adds 10% critical chance: " + (chance.getValue() - chanceBefore));
        chance.setBaseValue(-10);
        float sure = strike(p, z, 2000);
        helper.assertTrue(sure > normal * 1.38f && sure < normal * 1.42f, "40% more with Unerring: " + sure + " vs " + normal);

        // Killing Wind: 5% more for each kill at level 10, and the wind at their back
        SkillAPI.learn(p, RangerPath.KILLING_WIND, 10, OTHER);
        kill(helper, p, helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(5, 2, 5)));
        kill(helper, p, helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(5, 2, 5)));
        float windy = strike(p, z, 2000);
        helper.assertTrue(windy > sure * 1.06f, "two kills, 10% more: " + windy + " vs " + sure);
        helper.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SPEED), "and the wind at their back");

        // Ascend: 28% more while the wind carries them at level 1, and no weight once they are up
        SkillAPI.learn(p, RangerPath.ASCEND, 1, OTHER);
        refill(p);
        AttributeInstance gravity = p.getAttribute(Attributes.GRAVITY);
        helper.assertTrue(SkillAPI.cast(p, RangerPath.ASCEND) && SkillAPI.hasBuff(p, RangerPath.BUFF_SKYBORNE), "Ascend casts");
        float risen = strike(p, z, 2000);
        helper.assertTrue(risen > windy * 1.12f, "the skyborne hit harder: " + risen + " vs " + windy);
        helper.assertTrue(gravity.getValue() > 0.01, "they have their weight while they rise");
        z.discard();
        helper.runAfterDelay(14, () -> {
            helper.assertTrue(Math.abs(gravity.getValue()) < 1.0e-6, "once up, the wind carries them: " + gravity.getValue());
            // the wind lets go when its time is up, whatever became of the skill that called it
            SkillAPI.clearBuff(p, RangerPath.BUFF_SKYBORNE);
            RangerPath.tickSecond(p, data);
            helper.assertTrue(gravity.getValue() > 0.01, "and lets go when it is over: " + gravity.getValue());
            helper.succeed();
        });
    }
}
