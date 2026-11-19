package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.chronicle.title.TitleService;
import com.beyondsolo.job.JobAPI;
import com.beyondsolo.job.Subclass;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.shadow.ShadowData;
import com.beyondsolo.shadow.ShadowEntity;
import com.beyondsolo.shadow.ShadowPromotions;
import com.beyondsolo.shadow.ShadowRank;
import com.beyondsolo.shadow.ShadowRecord;
import com.beyondsolo.shadow.ShadowService;
import com.beyondsolo.skill.SkillAPI;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Shadow promotions ({@link ShadowPromotions}): shadows that wait at the top of a rank for their master to promote them,
 * the essence that pays for it, names a master gives, and commanders leading the shadows near them.
 * Run with {@code ./gradlew runGameTestServer}.
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShadowPromotionGameTests {
    private static final String ARENA = "evaluation_zone";

    /** A C-rank necromancer at level 1 with an empty army. */
    private static ServerPlayer master(GameTestHelper helper) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "promoter"));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        SkillAPI.reset(p);
        JobAPI.reset(p);
        p.setGameMode(GameType.CREATIVE);
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        QuestTestAccess.markStarted(p);
        SystemAPI.awaken(p, PlayerRank.C, Subclass.NECROMANCER.base());
        JobAPI.changeJob(p, Subclass.NECROMANCER);
        ShadowService.reset(p);
        return p;
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) <= 1.0e-3f * Math.max(1f, Math.abs(b));
    }

    private static int run(ServerPlayer p, String command) throws CommandSyntaxException {
        return p.server.getCommands().getDispatcher().execute(command, p.createCommandSourceStack().withPermission(4).withSuppressedOutput());
    }

    private static boolean refused(ServerPlayer p, String command) {
        try {
            run(p, command);
            return false;
        } catch (CommandSyntaxException e) {
            return true;
        }
    }

    @GameTest(template = ARENA)
    public static void aKnightWaitsForItsMastersPromotion(GameTestHelper helper) {
        ServerPlayer p = master(helper);
        ShadowRecord r = ShadowService.give(p, EntityType.ZOMBIE, ShadowRank.KNIGHT, false);
        helper.assertTrue("level".equals(ShadowPromotions.refusal(p, r)), "a knight below level 10 isn't ready: " + ShadowPromotions.refusal(p, r));
        ShadowService.addXp(p, r, 10_000_000);
        helper.assertTrue(r.rank() == ShadowRank.KNIGHT && r.level() == ShadowRank.LEVEL_CAP && r.ready(ShadowService.top(p)),
                "a knight at the top of its rank waits: " + r.rank() + " " + r.level());
        ShadowService.addXp(p, r, 500);
        helper.assertTrue(r.xp() == 0 && r.rank() == ShadowRank.KNIGHT, "a waiting shadow takes no more XP");

        ShadowRank to = ShadowRank.CAPTAIN;
        helper.assertTrue(to.byPromotion() && !ShadowRank.KNIGHT.byPromotion(), "captains are made, knights grow");
        SystemAPI.setLevel(p, to.promotionLevel() - 1);
        ShadowPromotions.setEssence(p, to.promotionCost());
        helper.assertTrue("master".equals(ShadowPromotions.refusal(p, r)), "the master's level comes first: " + ShadowPromotions.refusal(p, r));
        helper.assertFalse(ShadowPromotions.promote(p, r.id()), "so it's refused");
        SystemAPI.setLevel(p, to.promotionLevel());
        ShadowPromotions.setEssence(p, to.promotionCost() - 1);
        helper.assertTrue("essence".equals(ShadowPromotions.refusal(p, r)), "then the essence: " + ShadowPromotions.refusal(p, r));
        helper.assertFalse(ShadowPromotions.promote(p, r.id()), "refused again");
        helper.assertTrue(r.rank() == ShadowRank.KNIGHT && ShadowService.army(p).essence() == to.promotionCost() - 1, "nothing changed");

        ShadowPromotions.addEssence(p, 11);
        float knight = ShadowService.maxHealth(r, 20);
        helper.assertTrue(ShadowPromotions.promote(p, r.id()), "promoted");
        int entry = ShadowRank.KNIGHT.entryLevel(ShadowRank.LEVEL_CAP);
        helper.assertTrue(entry > 1 && ShadowRank.CAPTAIN.power(entry) >= ShadowRank.KNIGHT.power(ShadowRank.LEVEL_CAP)
                && ShadowRank.CAPTAIN.power(entry - 1) < ShadowRank.KNIGHT.power(ShadowRank.LEVEL_CAP), "it comes in where its strength is kept");
        helper.assertTrue(r.rank() == ShadowRank.CAPTAIN && r.level() == entry && r.xp() == 0, "a captain: " + r.rank() + " " + r.level());
        helper.assertTrue(ShadowService.army(p).essence() == 10, "the promotion took its essence: " + ShadowService.army(p).essence());
        helper.assertTrue(ShadowService.maxHealth(r, 20) > knight, "stronger than the knight it was");
        helper.assertTrue("level".equals(ShadowPromotions.refusal(p, r)), "and it grows again before the next");
        ShadowService.reset(p);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void aNewRankNeverWeakensAShadow(GameTestHelper helper) {
        for (ShadowRank rank : ShadowRank.values()) {
            if (rank.isMax()) continue;
            helper.assertTrue(rank.entryLevel(1) == 1, rank + " at level 1 comes into the next at level 1");
            for (int l = 1; l <= ShadowRank.LEVEL_CAP; l++) {
                int in = rank.entryLevel(l);
                helper.assertTrue(rank.next().power(in) >= rank.power(l) - 1.0e-4f, rank + " " + l + " is never weaker as " + rank.next() + " " + in);
            }
        }
        ServerPlayer p = master(helper);
        ShadowRecord r = ShadowService.give(p, EntityType.ZOMBIE, ShadowRank.HUSK, false);
        float last = ShadowService.maxHealth(r, 20);
        for (int i = 0; i < 400 && !r.ready(ShadowService.top(p)); i++) {
            ShadowService.addXp(p, r, 37);
            float now = ShadowService.maxHealth(r, 20);
            helper.assertTrue(now >= last - 1.0e-3f, "it only grows: " + r.rank() + " " + r.level());
            last = now;
        }
        helper.assertTrue(r.rank() == ShadowRank.KNIGHT && r.ready(ShadowService.top(p)), "a knight waiting: " + r.rank() + " " + r.level());
        ShadowService.reset(p);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void marshalsTopAHuntersArmy(GameTestHelper helper) {
        ServerPlayer p = master(helper);
        ShadowRecord r = ShadowService.give(p, EntityType.ZOMBIE, ShadowRank.CAPTAIN, false);
        helper.assertTrue(ShadowPromotions.promoteFree(p, r) && r.rank() == ShadowRank.COMMANDER, "a commander");
        helper.assertFalse(TitleService.data(p).hasTitle(ShadowPromotions.MARSHAL), "no title for a commander");
        helper.assertTrue(ShadowPromotions.promoteFree(p, r) && r.rank() == ShadowRank.MARSHAL, "a marshal");
        helper.assertTrue(TitleService.data(p).hasTitle(ShadowPromotions.MARSHAL), "raising a marshal earns Raiser of Marshals");
        helper.assertFalse(ShadowPromotions.promoteFree(p, r), "a hunter's marshals go no further");
        helper.assertTrue("monarch_only".equals(ShadowPromotions.refusal(p, r)), "only a monarch's: " + ShadowPromotions.refusal(p, r));
        ShadowService.reset(p);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void soulsLeftToFadeBecomeEssence(GameTestHelper helper) {
        ServerPlayer p = master(helper);
        ShadowService.Soul near = ShadowService.addSoul(p, helper.getLevel(), p.position().add(3, 0, 0), EntityType.ZOMBIE, 40, 4, 0.25f, false, 0);
        ShadowService.Soul boss = ShadowService.addSoul(p, helper.getLevel(), p.position().add(0, 0, 3), EntityType.WARDEN, 500, 30, 0.3f, true, 4);
        ShadowService.addSoul(p, helper.getLevel(), p.position().add(ShadowPromotions.ESSENCE_REACH + 12, 0, 0), EntityType.ZOMBIE, 40, 4, 0.25f, false, 0);
        int want = ShadowPromotions.soulEssence(near.health(), false) + ShadowPromotions.soulEssence(boss.health(), true);
        helper.assertTrue(ShadowPromotions.soulEssence(boss.health(), true) == 4 * ShadowPromotions.soulEssence(boss.health(), false),
                "a boss's soul leaves four times as much");
        ShadowService.fadeSouls(p);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(ShadowService.souls().stream().noneMatch(s -> s.owner().equals(p.getUUID())), "the souls faded");
            helper.assertTrue(ShadowService.army(p).essence() == want, "the near ones drifted in as essence, the far one was lost: "
                    + ShadowService.army(p).essence() + " (want " + want + ")");

            ShadowRecord r = ShadowService.give(p, EntityType.ZOMBIE, ShadowRank.KNIGHT, false);
            int left = ShadowPromotions.releaseEssence(r);
            helper.assertTrue(left > 0 && ShadowService.release(p, r.id()), "let go");
            helper.assertTrue(ShadowService.army(p).essence() == want + left, "a shadow let go leaves a little behind");
            ShadowService.reset(p);
            helper.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void namesBindShadowsCloser(GameTestHelper helper) {
        ServerPlayer p = master(helper);
        ShadowRecord husk = ShadowService.give(p, EntityType.ZOMBIE, ShadowRank.HUSK, false);
        helper.assertTrue("too_weak".equals(ShadowPromotions.nameRefusal(p, husk, "Grum")), "a husk is too weak to hold a name");
        helper.assertFalse(ShadowPromotions.name(p, husk.id(), "Grum") || husk.named(), "and gets none");

        ShadowRecord knight = ShadowService.give(p, EntityType.ZOMBIE, ShadowRank.KNIGHT, false);
        float health = ShadowService.maxHealth(knight, 20), blows = ShadowService.damage(knight, 20);
        helper.assertTrue("unfit".equals(ShadowPromotions.nameRefusal(p, knight, "7 9")), "a name needs letters");
        helper.assertTrue("unfit".equals(ShadowPromotions.nameRefusal(p, knight, "x")), "and two of them");
        helper.assertTrue("nothing".equals(ShadowPromotions.nameRefusal(p, knight, "")), "no name to take back");
        helper.assertTrue(ShadowPromotions.name(p, knight.id(), "  Kai:|#ro  9 "), "named");
        helper.assertTrue(knight.bonded() && knight.name().equals("Kairo"), "only letters are kept: '" + knight.name() + "'");
        helper.assertTrue(near(ShadowService.maxHealth(knight, 20), health * ShadowPromotions.BOND)
                && near(ShadowService.damage(knight, 20), blows * ShadowPromotions.BOND), "a named shadow is stronger");
        helper.assertTrue(TitleService.data(p).hasTitle(ShadowPromotions.NAMER), "the first name earns Keeper of Names");

        ShadowRecord second = ShadowService.give(p, EntityType.SKELETON, ShadowRank.KNIGHT, false);
        helper.assertTrue("taken".equals(ShadowPromotions.nameRefusal(p, second, "kairo")), "two shadows can't share a name");
        helper.assertTrue(ShadowPromotions.name(p, second.id(), "Brann"), "a second name");
        helper.assertTrue(ShadowPromotions.nameSlots(p) == 2 && ShadowService.army(p).bonded() == 2, "two names at level 1");
        ShadowRecord third = ShadowService.give(p, EntityType.ZOMBIE, ShadowRank.KNIGHT, false);
        helper.assertTrue("slots".equals(ShadowPromotions.nameRefusal(p, third, "Odo")), "no third name yet");
        helper.assertTrue(ShadowPromotions.name(p, second.id(), "Brannoc") && second.name().equals("Brannoc"), "renaming takes no new place");

        // a boss's shadow keeps its own name under its master's, and is known by it again when the master's is taken back
        SystemAPI.setLevel(p, 20);
        helper.assertTrue(ShadowPromotions.nameSlots(p) == 3, "a third name at level 20: " + ShadowPromotions.nameSlots(p));
        ShadowRecord elite = ShadowService.give(p, EntityType.WARDEN, ShadowRank.CAPTAIN, true);
        String own = elite.name();
        helper.assertTrue(ShadowPromotions.name(p, elite.id(), "Morrow") && elite.bonded(), "a boss's shadow takes its master's name");
        helper.assertTrue(ShadowPromotions.name(p, elite.id(), "") && elite.name().equals(own) && !elite.bonded(),
                "taken back, it is known by its own name again: " + elite.name());
        helper.assertTrue("nothing".equals(ShadowPromotions.nameRefusal(p, elite, "")), "nothing more to take back");

        var tag = ShadowService.army(p).serializeNBT(helper.getLevel().registryAccess());
        ShadowData copy = new ShadowData();
        copy.deserializeNBT(helper.getLevel().registryAccess(), tag);
        helper.assertTrue(copy.get(knight.id()).bonded() && copy.get(knight.id()).name().equals("Kairo") && !copy.get(elite.id()).bonded()
                && copy.get(elite.id()).name().equals(own), "names survive a save");
        ShadowService.reset(p);
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void aNameStandsOverTheShadow(GameTestHelper helper) {
        ServerPlayer p = master(helper);
        ShadowRecord r = ShadowService.give(p, EntityType.ZOMBIE, ShadowRank.KNIGHT, false);
        ShadowEntity e = ShadowService.summonAt(p, r, helper.absoluteVec(new Vec3(3, 2, 3)), false);
        helper.assertTrue(e != null && !e.hasCustomName(), "a nameless shadow rose");
        helper.assertTrue(ShadowPromotions.name(p, r.id(), "Vesk"), "named while out");
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(e.hasCustomName() && e.getCustomName().getString().equals("Vesk"), "its name stands over it: " + e.getCustomName());
            helper.assertTrue(near((float) e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).getBaseValue(),
                    ShadowService.maxHealth(r, SystemAPI.get(p))), "and its health grew with the bond");
            ShadowPromotions.name(p, r.id(), "");
            helper.assertFalse(e.hasCustomName(), "taken back, the name is gone");
            ShadowService.reset(p);
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void commandersLeadTheShadowsNearThem(GameTestHelper helper) {
        ServerPlayer p = master(helper);
        ShadowRecord soldier = ShadowService.give(p, EntityType.SKELETON, ShadowRank.SOLDIER, false);
        ShadowEntity s = ShadowService.summonAt(p, soldier, helper.absoluteVec(new Vec3(2, 2, 4)), false);
        helper.assertTrue(s != null && near(ShadowService.auraBonus(s), 1f), "alone, a soldier fights as it is");
        ShadowRecord captain = ShadowService.give(p, EntityType.ZOMBIE, ShadowRank.CAPTAIN, false);
        ShadowEntity c = ShadowService.summonAt(p, captain, helper.absoluteVec(new Vec3(4, 2, 4)), false);
        helper.assertTrue(c != null && near(ShadowService.auraBonus(s), 1f), "a captain leads no one");
        ShadowPromotions.promoteFree(p, captain);
        helper.assertTrue(near(ShadowService.auraBonus(s), ShadowPromotions.LEAD), "a commander leads the shadows near it: " + ShadowService.auraBonus(s));
        helper.assertTrue(near(ShadowService.auraBonus(c), 1f), "but no one leads the commander");
        ShadowService.reset(p);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void operatorsPromoteNameAndGiveEssence(GameTestHelper helper) {
        ServerPlayer p = master(helper);
        try {
            helper.assertTrue(refused(p, "solo shadow list"), "no shadows to list");
            ShadowService.give(p, EntityType.ZOMBIE, ShadowRank.KNIGHT, false);
            helper.assertTrue(run(p, "solo shadow list") == 1, "listed");
            helper.assertTrue(run(p, "solo shadow essence set 40") == 1 && ShadowService.army(p).essence() == 40, "essence set");
            helper.assertTrue(run(p, "solo shadow essence add 5") == 1 && ShadowService.army(p).essence() == 45, "essence added");
            helper.assertTrue(run(p, "solo shadow promote 0") == 1, "promoted by command");
            ShadowRecord r = ShadowService.army(p).byIndex(0);
            helper.assertTrue(r.rank() == ShadowRank.CAPTAIN && ShadowService.army(p).essence() == 45, "for free: " + r.rank());
            helper.assertTrue(run(p, "solo shadow name 0 \"Ash Wyn\"") == 1 && r.name().equals("Ash Wyn") && r.bonded(), "named by command: " + r.name());
            helper.assertTrue(refused(p, "solo shadow name 0 \"#\""), "an unfit name is refused");
            helper.assertTrue(refused(p, "solo shadow promote 7"), "no such shadow");
        } catch (CommandSyntaxException e) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("command failed: " + e.getMessage());
        }
        ShadowService.reset(p);
        helper.succeed();
    }
}
