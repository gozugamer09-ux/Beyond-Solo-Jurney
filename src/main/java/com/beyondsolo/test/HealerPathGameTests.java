package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.job.JobAPI;
import com.beyondsolo.job.Subclass;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.skill.HealerPath;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The Healer subclasses' skill sets. Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HealerPathGameTests {
    private static final String ARENA = "evaluation_zone";
    private static final Subclass[] HEALERS = {Subclass.LIGHTBINDER, Subclass.GROVEKEEPER, Subclass.SPRING_ORACLE, Subclass.HEMOMANCER, Subclass.DAWN_SAINT};
    private static final SkillEvent.Source OTHER = SkillEvent.Source.OTHER;

    private static ServerPlayer healer(GameTestHelper helper, Subclass sub) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "healer"));
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
        SystemAPI.awaken(p, PlayerRank.E, PlayerClass.HEALER);
        // no lucky critical hits: the tests compare damage numbers
        var crit = p.getAttribute(dev.shadowsoffire.apothic_attributes.api.ALObjects.Attributes.CRIT_CHANCE);
        if (crit != null) {
            crit.removeModifiers();
            crit.setBaseValue(-10);
        }
        if (sub != null) JobAPI.changeJob(p, sub);
        return p;
    }

    private static void face(ServerPlayer p, LivingEntity target) {
        Vec3 d = target.getEyePosition().subtract(p.getEyePosition());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) (-(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG));
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.setYHeadRot(yaw);
    }

    private static Zombie zombie(GameTestHelper helper, ServerPlayer p, BlockPos at) {
        Zombie z = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, at);
        z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);
        z.setHealth(2000);
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

    @GameTest(template = ARENA)
    public static void healerSubclassesHaveTwelveSkills(GameTestHelper helper) {
        for (Subclass sub : HEALERS) {
            List<Skill> all = Skills.forSubclass(sub);
            long actives = all.stream().filter(Skill::isActive).count();
            helper.assertTrue(all.size() >= 12, sub + " has at least 12 skills, found " + all.size());
            helper.assertTrue(actives >= 8 && all.size() - actives >= 4, sub + " has 8 actives and 4 passives, found " + actives + " actives");
            for (Skill s : all) {
                helper.assertTrue(s.element() == sub.element(), s.path() + " carries its subclass's element");
                helper.assertTrue(s.owner() == PlayerClass.HEALER, s.path() + " belongs to healers");
            }
        }
        ServerPlayer p = healer(helper, Subclass.LIGHTBINDER);
        helper.assertTrue(!SkillAPI.knows(p, HealerPath.SUNBEAM), "Sunbeam waits for its level");
        for (int i = 0; i < 2000 && SystemAPI.get(p).level() < 50; i++) SystemAPI.grantXp(p, 20000, SystemAPI.XP_SOURCE_COMMAND);
        helper.assertTrue(Skills.forSubclass(Subclass.LIGHTBINDER).stream().allMatch(s -> SkillAPI.knows(p, s)), "everything by level 50");
        helper.assertTrue(!SkillAPI.knows(p, HealerPath.THORN_VOLLEY), "other subclasses' skills stay out");
        helper.succeed();
    }

    /** Every new active of every healer subclass casts and hurts the creature in front, one after the other. */
    @GameTest(template = ARENA, timeoutTicks = 3600)
    public static void healerSubclassSkillsCastAndHit(GameTestHelper helper) {
        List<Skill> queue = new ArrayList<>();
        for (Subclass sub : HEALERS) for (Skill s : Skills.forSubclass(sub)) if (s.isActive() && !s.advanced()) queue.add(s);
        helper.assertTrue(queue.size() == 35, "35 new healer subclass actives, found " + queue.size());
        castNext(helper, queue, 0, new ArrayList<>());
    }

    private static void castNext(GameTestHelper helper, List<Skill> queue, int index, List<String> failed) {
        if (index >= queue.size()) {
            helper.assertTrue(failed.isEmpty(), "healer subclass skills that failed: " + failed);
            helper.succeed();
            return;
        }
        Skill s = queue.get(index);
        ServerPlayer p = healer(helper, s.subclass());
        SkillAPI.learn(p, s, 1, OTHER);
        Zombie z = zombie(helper, p, new BlockPos(3, 2, 1));
        refill(p);
        boolean cast = SkillAPI.cast(p, s);
        if (!cast) failed.add(s.path() + " did not cast");
        // these only mend, shield or bless
        boolean harmless = s == HealerPath.MOTES_OF_MERCY || s == HealerPath.SECOND_SPRING || s == HealerPath.BUBBLE_WARD
                || s == HealerPath.PREMONITION || s == HealerPath.TRANSFUSION || s == HealerPath.BENISON;
        // the foretold blow lands three seconds later
        boolean slow = s == HealerPath.FATED_BLOW;
        helper.runAfterDelay(slow ? 75 : 24, () -> {
            if (cast && !harmless && z.getHealth() >= 2000) failed.add(s.path() + " did no damage");
            z.discard();
            JobAPI.reset(p);
            // their caster leaves, and what is still going on (suns, trees, rains) ends with them, so that the
            // next skill is judged on its own damage
            p.discard();
            helper.runAfterDelay(4, () -> castNext(helper, queue, index + 1, failed));
        });
    }

    /** Grasping Vines sets what it seizes down at the keeper's feet: not short of them, and not past them. */
    @GameTest(template = ARENA)
    public static void graspingVinesDragToTheFeet(GameTestHelper helper) {
        ServerPlayer p = healer(helper, Subclass.GROVEKEEPER);
        SkillAPI.learn(p, HealerPath.GRASPING_VINES, 1, OTHER);
        Zombie z = zombie(helper, p, new BlockPos(5, 2, 1));
        refill(p);
        double start = z.getX() - p.getX();
        // a creature is thrown further in the tick it appears, before it has found its feet: let it stand first
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(z.onGround(), "the zombie stands on the floor");
            helper.assertTrue(SkillAPI.cast(p, HealerPath.GRASPING_VINES), "Grasping Vines casts");
        });
        helper.runAfterDelay(35, () -> {
            double ahead = z.getX() - p.getX();
            double aside = z.getZ() - p.getZ();
            z.discard();
            helper.assertTrue(ahead > 1.1 && ahead < 2.1, "dragged from " + start + " blocks to the keeper's feet: " + ahead);
            helper.assertTrue(Math.abs(aside) < 0.5, "straight in: " + aside);
            helper.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void healerPassivesAndPrices(GameTestHelper helper) {
        // mana and attribute passives
        ServerPlayer l = healer(helper, Subclass.LIGHTBINDER);
        float mana = SystemAPI.get(l).maxMana();
        SkillAPI.learn(l, HealerPath.INNER_RADIANCE, 5, OTHER);
        helper.assertTrue(Math.abs(SystemAPI.get(l).maxMana() - mana - 20) < 0.01f, "Inner Radiance 5 adds 20 mana: " + (SystemAPI.get(l).maxMana() - mana));
        ServerPlayer g = healer(helper, Subclass.GROVEKEEPER);
        double armor = g.getAttributeValue(Attributes.ARMOR);
        float health = g.getMaxHealth();
        SkillAPI.learn(g, HealerPath.BARKSKIN, 5, OTHER);
        helper.assertTrue(Math.abs(g.getAttributeValue(Attributes.ARMOR) - armor - 3) < 0.01, "Barkskin 5 adds 3 armour");
        helper.assertTrue(Math.abs(g.getMaxHealth() - health * 1.1f) < 0.01f, "Barkskin 5 adds 10% health: " + (g.getMaxHealth() - health));
        ServerPlayer d = healer(helper, Subclass.DAWN_SAINT);
        mana = SystemAPI.get(d).maxMana();
        SkillAPI.learn(d, HealerPath.RADIANT_SOUL, 5, OTHER);
        helper.assertTrue(Math.abs(SystemAPI.get(d).maxMana() - mana - 30) < 0.01f, "Radiant Soul 5 adds 30 mana: " + (SystemAPI.get(d).maxMana() - mana));

        // Inner Radiance makes heals stronger (15% at level 5)
        ServerPlayer plain = healer(helper, Subclass.LIGHTBINDER);
        SkillAPI.learn(plain, HealerPath.SOLAR_FLARE, 1, OTHER);
        SkillAPI.learn(l, HealerPath.SOLAR_FLARE, 1, OTHER);
        refill(plain);
        refill(l);
        plain.setHealth(1);
        helper.assertTrue(SkillAPI.cast(plain, HealerPath.SOLAR_FLARE), "Solar Flare casts");
        float mended = plain.getHealth() - 1;
        l.setHealth(1);
        helper.assertTrue(SkillAPI.cast(l, HealerPath.SOLAR_FLARE), "Solar Flare casts with Inner Radiance");
        float radiant = l.getHealth() - 1;
        helper.assertTrue(mended > 0.5f && radiant > mended * 1.1f, "Inner Radiance mends more: " + radiant + " vs " + mended);

        // Afterglow: a heal sharpens the next blows (26% at level 10)
        Zombie z = zombie(helper, l, new BlockPos(3, 2, 1));
        float normal = strike(l, z, 2000);
        helper.assertTrue(normal > 1, "a plain blow lands: " + normal);
        SkillAPI.learn(l, HealerPath.AFTERGLOW, 10, OTHER);
        refill(l);
        helper.assertTrue(SkillAPI.cast(l, HealerPath.SOLAR_FLARE) && SkillAPI.hasBuff(l, HealerPath.BUFF_AFTERGLOW), "a heal starts the Afterglow");
        float glowing = strike(l, z, 2000);
        helper.assertTrue(glowing > normal * 1.2f, "Afterglow adds damage: " + glowing + " vs " + normal);
        z.discard();

        // Toxic Bloom: more against the poisoned (24% at level 10); a pig, because the undead cannot be poisoned
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(3, 2, 3));
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);
        float healthy = strike(g, pig, 2000);
        SkillAPI.learn(g, HealerPath.TOXIC_BLOOM, 10, OTHER);
        helper.assertTrue(strike(g, pig, 2000) < healthy * 1.05f, "Toxic Bloom waits for poison");
        pig.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
        float sick = strike(g, pig, 2000);
        helper.assertTrue(sick > healthy * 1.2f, "Toxic Bloom adds damage to the poisoned: " + sick + " vs " + healthy);
        pig.discard();

        // Second Sight: more against creatures near their end (38% at level 10)
        ServerPlayer o = healer(helper, Subclass.SPRING_ORACLE);
        Zombie z2 = zombie(helper, o, new BlockPos(3, 2, 5));
        normal = strike(o, z2, 2000);
        SkillAPI.learn(o, HealerPath.SECOND_SIGHT, 10, OTHER);
        helper.assertTrue(strike(o, z2, 2000) < normal * 1.05f, "Second Sight waits for the end");
        float ending = strike(o, z2, 600);
        helper.assertTrue(ending > normal * 1.3f, "Second Sight adds damage under 35% health: " + ending + " vs " + normal);

        // Sanctity: the Saint simply hits harder (20% at level 10)
        normal = strike(d, z2, 2000);
        SkillAPI.learn(d, HealerPath.SANCTITY, 10, OTHER);
        float hallowed = strike(d, z2, 2000);
        helper.assertTrue(hallowed > normal * 1.15f, "Sanctity adds damage: " + hallowed + " vs " + normal);

        // the Hemomancer pays in health: Transfusion (15% of what is left), the Crimson Lance (1), and what it buys
        ServerPlayer h = healer(helper, Subclass.HEMOMANCER);
        normal = strike(h, z2, 2000);
        SkillAPI.learn(h, HealerPath.TRANSFUSION, 10, OTHER);
        SkillAPI.learn(h, HealerPath.CRIMSON_LANCE, 1, OTHER);
        SkillAPI.learn(h, HealerPath.COAGULATE, 5, OTHER);
        SystemAPI.setManaBonus(h, 1000);
        refill(h);
        float before = h.getHealth();
        helper.assertTrue(SkillAPI.cast(h, HealerPath.TRANSFUSION) && SkillAPI.hasBuff(h, HealerPath.BUFF_BLOOD_PRICE), "Transfusion is paid");
        helper.assertTrue(Math.abs(h.getHealth() - before * 0.85f) < 0.01f, "Transfusion costs 15% of the health left: " + h.getHealth() + " of " + before);
        helper.assertTrue(SkillAPI.hasBuff(h, HealerPath.BUFF_COAGULATE), "paying health starts Coagulate");
        float priced = strike(h, z2, 2000);
        helper.assertTrue(priced > normal * 1.25f, "the price buys damage: " + priced + " vs " + normal);
        before = h.getHealth();
        face(h, z2);
        z2.setHealth(2000);
        z2.invulnerableTime = 0;
        helper.assertTrue(SkillAPI.cast(h, HealerPath.CRIMSON_LANCE), "Crimson Lance casts");
        helper.assertTrue(Math.abs(h.getHealth() - (before - 1)) < 0.01f, "the lance costs 1 health: " + h.getHealth() + " of " + before);
        helper.assertTrue(z2.getHealth() < 2000, "and runs the creature through");

        // Red Thirst (25% at level 10) and the Sanguine Pact (8% of the damage comes back)
        ServerPlayer t = healer(helper, Subclass.HEMOMANCER);
        Zombie z3 = zombie(helper, t, new BlockPos(5, 2, 3));
        normal = strike(t, z3, 2000);
        SkillAPI.learn(t, HealerPath.RED_THIRST, 10, OTHER);
        float thirsty = strike(t, z3, 900);
        helper.assertTrue(thirsty > normal * 1.2f, "Red Thirst adds damage under half health: " + thirsty + " vs " + normal);
        SkillAPI.learn(t, HealerPath.SANGUINE_PACT, 10, OTHER);
        t.setHealth(5);
        strike(t, z3, 2000);
        helper.assertTrue(t.getHealth() > 5.5f, "the Sanguine Pact gives blood back: " + t.getHealth());
        helper.succeed();
    }

    /**
     * What protects a healer and the people they bless. Test players cannot be hurt, so the hook that every blow on a
     * player goes through is asked directly what the blow would do.
     */
    @GameTest(template = ARENA)
    public static void healerGiftsTurnBlows(GameTestHelper helper) {
        long now = helper.getLevel().getGameTime();

        // Premonition: each drop swallows a blow whole (two drops at level 1); Foretold: the first blow of each creature is expected
        ServerPlayer o = healer(helper, Subclass.SPRING_ORACLE);
        Zombie foe = zombie(helper, o, new BlockPos(3, 2, 1));
        DamageSource bite = o.damageSources().mobAttack(foe);
        SkillAPI.learn(o, HealerPath.PREMONITION, 1, OTHER);
        refill(o);
        helper.assertTrue(SkillAPI.cast(o, HealerPath.PREMONITION), "Premonition casts");
        helper.assertTrue(HealerPath.whenStruck(o, SkillAPI.get(o), foe, true, bite, 6, now) < 0, "a drop swallows the first blow");
        helper.assertTrue(HealerPath.whenStruck(o, SkillAPI.get(o), foe, true, bite, 6, now) < 0, "and another the second");
        helper.assertTrue(Math.abs(HealerPath.whenStruck(o, SkillAPI.get(o), foe, true, bite, 6, now) - 6) < 0.01f, "the third lands");
        SkillAPI.learn(o, HealerPath.FORETOLD, 5, OTHER);
        float first = HealerPath.whenStruck(o, SkillAPI.get(o), foe, true, bite, 10, now);
        float second = HealerPath.whenStruck(o, SkillAPI.get(o), foe, true, bite, 10, now);
        helper.assertTrue(Math.abs(first - 6) < 0.01f && Math.abs(second - 10) < 0.01f, "Foretold takes 40% off the first blow only: " + first + ", " + second);

        // Benison (15% less under the halo), the Gilded Soul (12% at level 5) and the Avatar that cannot be killed
        ServerPlayer d = healer(helper, Subclass.DAWN_SAINT);
        SkillAPI.learn(d, HealerPath.BENISON, 1, OTHER);
        SkillAPI.learn(d, HealerPath.AVATAR_OF_DAWN, 1, OTHER);
        refill(d);
        helper.assertTrue(Math.abs(HealerPath.whenStruck(d, SkillAPI.get(d), foe, true, bite, 10, now) - 10) < 0.01f, "unblessed, the blow lands whole");
        helper.assertTrue(SkillAPI.cast(d, HealerPath.BENISON), "Benison casts");
        float blessed = HealerPath.whenStruck(d, SkillAPI.get(d), foe, true, bite, 10, now);
        helper.assertTrue(Math.abs(blessed - 8.5f) < 0.01f, "the halo takes 15% off: " + blessed);
        SkillAPI.learn(d, HealerPath.GILDED_SOUL, 5, OTHER);
        float gilded = HealerPath.whenStruck(d, SkillAPI.get(d), foe, true, bite, 10, now);
        helper.assertTrue(Math.abs(gilded - 8.5f * 0.88f) < 0.01f, "the Gilded Soul takes 12% more off: " + gilded);
        helper.assertTrue(HealerPath.whenStruck(d, SkillAPI.get(d), foe, true, bite, 1000, now) >= d.getHealth(), "a mortal blow kills a mortal");
        refill(d);
        helper.assertTrue(SkillAPI.cast(d, HealerPath.AVATAR_OF_DAWN) && SkillAPI.hasBuff(d, HealerPath.BUFF_AVATAR), "the Avatar rises");
        helper.assertTrue(HealerPath.whenStruck(d, SkillAPI.get(d), foe, true, bite, 1000, now) < d.getHealth(), "the Avatar cannot be killed");

        // Last Light and the Motes of Mercy mend their owner right after a blow
        ServerPlayer l = healer(helper, Subclass.LIGHTBINDER);
        SkillAPI.learn(l, HealerPath.LAST_LIGHT, 5, OTHER);
        SkillAPI.learn(l, HealerPath.MOTES_OF_MERCY, 1, OTHER);
        refill(l);
        helper.assertTrue(SkillAPI.cast(l, HealerPath.MOTES_OF_MERCY), "Motes of Mercy casts");
        float half = l.getMaxHealth() * 0.5f;
        l.setHealth(half);
        HealerPath.whenStruck(l, SkillAPI.get(l), foe, true, bite, l.getMaxHealth() * 0.25f, now);
        helper.assertTrue(SkillAPI.hasBuff(l, HealerPath.BUFF_LAST_LIGHT), "a blow that leaves a quarter of their health wakes the Last Light");
        helper.runAfterDelay(3, () -> {
            // 22% of their health from the light, and 3 more from a mote
            helper.assertTrue(l.getHealth() > half + l.getMaxHealth() * 0.2f + 2.5f, "light and a mote mend them: " + l.getHealth() + " from " + half);
            helper.succeed();
        });
    }
}
