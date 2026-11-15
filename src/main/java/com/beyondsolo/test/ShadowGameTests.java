package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.job.JobAPI;
import com.beyondsolo.job.Subclass;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.shadow.ShadowData;
import com.beyondsolo.shadow.ShadowEntity;
import com.beyondsolo.shadow.ShadowRank;
import com.beyondsolo.shadow.ShadowRecord;
import com.beyondsolo.shadow.ShadowService;
import com.beyondsolo.skill.SkillAPI;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** Shadow army tests. Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShadowGameTests {
    private static final String ARENA = "evaluation_zone";

    /** A C-rank mage with a subclass (a necromancer unless another is given), army emptied. */
    private static ServerPlayer player(GameTestHelper helper, Subclass subclass) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "raiser"));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        SkillAPI.reset(p);
        JobAPI.reset(p);
        p.setGameMode(GameType.CREATIVE);
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        QuestTestAccess.markStarted(p);
        SystemAPI.awaken(p, PlayerRank.C, subclass.base());
        JobAPI.changeJob(p, subclass);
        ShadowService.reset(p);
        return p;
    }

    private static List<ShadowService.Soul> souls(ServerPlayer p) {
        return ShadowService.souls().stream().filter(s -> s.owner().equals(p.getUUID())).toList();
    }

    private static void kill(GameTestHelper helper, ServerPlayer p, Zombie z) {
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(z, p.damageSources().playerAttack(p)));
        z.discard();
    }

    private static List<ShadowEntity> shadowsOf(GameTestHelper helper, ServerPlayer p) {
        return helper.getLevel().getEntitiesOfClass(ShadowEntity.class, p.getBoundingBox().inflate(16),
                e -> p.getUUID().equals(e.getOwnerUUID()) && e.isAlive());
    }

    @GameTest(template = ARENA)
    public static void onlyNecromancersLeaveSouls(GameTestHelper helper) {
        ServerPlayer pyro = player(helper, Subclass.PYROMANCER);
        kill(helper, pyro, helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2)));
        helper.assertTrue(souls(pyro).isEmpty(), "a pyromancer's kill leaves no soul");
        helper.assertFalse(ShadowService.canRaise(pyro), "only necromancers raise shadows");

        ServerPlayer necro = player(helper, Subclass.NECROMANCER);
        helper.assertTrue(ShadowService.canRaise(necro), "necromancers raise shadows");
        kill(helper, necro, helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2)));
        helper.assertTrue(souls(necro).size() == 1, "a necromancer's kill leaves a soul");
        helper.assertTrue(souls(necro).get(0).tries() == ShadowService.TRIES, "three tries");
        ShadowService.reset(necro);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void becomingANecromancerGivesASteed(GameTestHelper helper) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "steed"));
        SystemAPI.reset(p);
        JobAPI.reset(p);
        ShadowService.reset(p);
        QuestTestAccess.markStarted(p);
        SystemAPI.awaken(p, PlayerRank.C, PlayerClass.MAGE);
        JobAPI.changeJob(p, Subclass.NECROMANCER);
        ShadowData army = ShadowService.army(p);
        helper.assertTrue(army.size() == 1 && army.byIndex(0).isMount() && army.byIndex(0).named(), "a named shadow steed");
        ShadowService.reset(p);
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void extractionRaisesAShadow(GameTestHelper helper) {
        ServerPlayer p = player(helper, Subclass.NECROMANCER);
        kill(helper, p, helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 1)));
        ShadowService.forceNext(p, true);
        helper.assertTrue(ShadowService.startExtraction(p), "a soul is in reach");
        helper.assertTrue(souls(p).get(0).inRitual(), "the ritual runs");
        helper.runAfterDelay(ShadowService.RITUAL_TICKS + 6, () -> {
            ShadowData army = ShadowService.army(p);
            helper.assertTrue(army.size() == 1, "one shadow in the army, got " + army.size());
            ShadowRecord r = army.byIndex(0);
            helper.assertTrue(r.type().getPath().equals("zombie") && r.role() == ShadowRecord.Role.WARRIOR, "a zombie warrior");
            helper.assertTrue(r.rank() == ShadowRank.HUSK, "weak creatures start as husks");
            helper.assertTrue(souls(p).isEmpty(), "the soul was used");
            helper.assertTrue(army.activeCount() == 1 && shadowsOf(helper, p).size() == 1, "it rose where it fell");
            ShadowEntity e = shadowsOf(helper, p).get(0);
            helper.assertTrue(e.isAlliedTo(p) && !e.canFight(p), "it never fights its master");
            ShadowService.reset(p);
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 260)
    public static void aSoulRefusesThreeTimesThenCrumbles(GameTestHelper helper) {
        ServerPlayer p = player(helper, Subclass.NECROMANCER);
        kill(helper, p, helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 1)));
        int step = ShadowService.RITUAL_TICKS + 4;
        ShadowService.forceNext(p, false);
        ShadowService.startExtraction(p);
        helper.runAfterDelay(step, () -> {
            helper.assertTrue(souls(p).size() == 1 && souls(p).get(0).tries() == 2, "two tries left");
            ShadowService.forceNext(p, false);
            ShadowService.startExtraction(p);
            helper.runAfterDelay(step, () -> {
                ShadowService.forceNext(p, false);
                ShadowService.startExtraction(p);
                helper.runAfterDelay(step, () -> {
                    helper.assertTrue(souls(p).isEmpty(), "the soul crumbled");
                    helper.assertTrue(ShadowService.army(p).size() == 0, "nothing was raised");
                    ShadowService.reset(p);
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void bossSoulsBecomeNamedElites(GameTestHelper helper) {
        ServerPlayer p = player(helper, Subclass.NECROMANCER);
        ShadowService.addSoul(p, helper.getLevel(), p.position(), EntityType.WARDEN, 500, 30, 0.3f, true, 4);
        ShadowService.forceNext(p, true);
        ShadowService.startExtraction(p);
        helper.runAfterDelay(ShadowService.RITUAL_TICKS + 6, () -> {
            ShadowRecord r = ShadowService.army(p).byIndex(0);
            helper.assertTrue(r != null && r.boss() && r.named(), "a named boss shadow");
            helper.assertTrue(r.rank() == ShadowRank.COMMANDER, "an A-rank boss starts as a commander: " + (r == null ? null : r.rank()));
            ShadowService.reset(p);
            helper.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void shadowsLevelAndRankUp(GameTestHelper helper) {
        ServerPlayer p = player(helper, Subclass.NECROMANCER);
        ShadowRecord r = ShadowService.give(p, EntityType.ZOMBIE, ShadowRank.HUSK, false);
        float hp = ShadowService.maxHealth(r, 20);
        ShadowService.addXp(p, r, r.xpToNext());
        helper.assertTrue(r.level() == 2 && r.rank() == ShadowRank.HUSK, "one level");
        ShadowService.addXp(p, r, 5000);
        helper.assertTrue(r.rank().ordinal() > ShadowRank.HUSK.ordinal(), "climbed a rank");
        helper.assertTrue(ShadowService.maxHealth(r, 20) > hp, "stronger after ranking up");
        ShadowService.addXp(p, r, 10_000_000);
        helper.assertTrue(r.maxed() && r.rank() == ShadowRank.MARSHAL, "marshal at the top");
        ShadowService.reset(p);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void rolesComeFromTheCreature(GameTestHelper helper) {
        ServerPlayer p = player(helper, Subclass.NECROMANCER);
        helper.assertTrue(ShadowService.give(p, EntityType.HORSE, null, false).isMount(), "horses become mounts");
        helper.assertTrue(ShadowService.give(p, EntityType.SKELETON, null, false).role() == ShadowRecord.Role.CASTER, "skeletons cast");
        helper.assertTrue(ShadowService.give(p, EntityType.ZOMBIE, null, false).role() == ShadowRecord.Role.WARRIOR, "zombies fight up close");
        ShadowService.reset(p);
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 160)
    public static void summonRecallAndFall(GameTestHelper helper) {
        ServerPlayer p = player(helper, Subclass.NECROMANCER);
        ShadowService.give(p, EntityType.ZOMBIE, ShadowRank.SOLDIER, false);
        ShadowService.give(p, EntityType.SKELETON, ShadowRank.SOLDIER, false);
        helper.assertTrue(ShadowService.summonAll(p) == 2, "both answer");
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(shadowsOf(helper, p).size() == 2, "two shadows stand in the world");
            ShadowService.recallAll(p);
            helper.assertTrue(ShadowService.army(p).activeCount() == 0, "none out");
            helper.runAfterDelay(ShadowEntity.SINK_TICKS + 4, () -> {
                helper.assertTrue(shadowsOf(helper, p).isEmpty(), "they sank");
                ShadowService.summonAll(p);
                helper.runAfterDelay(ShadowEntity.RISE_TICKS + 6, () -> {
                    ShadowEntity e = shadowsOf(helper, p).get(0);
                    UUID rid = e.recordId();
                    e.hurt(p.damageSources().genericKill(), Float.MAX_VALUE);
                    ShadowRecord r = ShadowService.army(p).get(rid);
                    helper.assertTrue(r.recovering(helper.getLevel().getGameTime()), "a fallen shadow recovers");
                    helper.assertFalse(ShadowService.army(p).isActive(rid), "and is no longer out");
                    helper.assertTrue(ShadowService.army(p).size() == 2, "but stays in the army");
                    ShadowService.reset(p);
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = ARENA)
    public static void theArmyIsSaved(GameTestHelper helper) {
        ServerPlayer p = player(helper, Subclass.NECROMANCER);
        ShadowRecord r = ShadowService.give(p, EntityType.WARDEN, ShadowRank.CAPTAIN, true);
        ShadowService.addXp(p, r, 25);
        var tag = ShadowService.army(p).serializeNBT(helper.getLevel().registryAccess());
        ShadowData copy = new ShadowData();
        copy.deserializeNBT(helper.getLevel().registryAccess(), tag);
        ShadowRecord c = copy.byIndex(0);
        helper.assertTrue(c.id().equals(r.id()) && c.rank() == r.rank() && c.level() == r.level() && c.xp() == r.xp()
                && c.boss() && c.name().equals(r.name()), "the record survives a save");
        ShadowService.reset(p);
        helper.succeed();
    }
}
