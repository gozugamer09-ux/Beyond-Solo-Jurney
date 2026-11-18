package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestData;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestObjective;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.quest.situation.CatchBreath;
import com.beyondsolo.quest.situation.Foes;
import com.beyondsolo.quest.situation.FollowUps;
import com.beyondsolo.quest.situation.LastStand;
import com.beyondsolo.quest.situation.OverwhelmingFoe;
import com.beyondsolo.quest.situation.ProtectVillager;
import com.beyondsolo.quest.situation.Run;
import com.beyondsolo.quest.situation.Secrets;
import com.beyondsolo.quest.situation.SituationData;
import com.beyondsolo.quest.situation.SituationTestAccess;
import com.beyondsolo.quest.situation.Situations;
import com.beyondsolo.quest.situation.Streaks;
import com.beyondsolo.quest.situation.Watch;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.Progression;
import com.beyondsolo.system.SystemAPI;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Situation quests: quests the System makes up from what is happening (a far stronger creature, the edge of death, a
 * streak, a villager in trouble), that change mid-fight and leave consequences behind. Run with
 * {@code ./gradlew runGameTestServer}.
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SituationGameTests {
    private static final String ARENA = "evaluation_zone";
    private static final String PING = BeyondSolo.id("test_ping").toString();
    private static final List<Integer> PINGS = new ArrayList<>();

    static {
        // a follow-up kind of our own: "n" 2 can't happen yet the first time it is asked
        FollowUps.register(PING, (player, data) -> {
            PINGS.add(data.getInt("n"));
            return data.getInt("n") != 2 || data.getInt("tries") > 0;
        });
    }

    private static ServerPlayer player(GameTestHelper helper, PlayerRank rank) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "situated"));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        Situations.clear(p);
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        p.removeAllEffects();
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        SystemAPI.awaken(p, rank, PlayerClass.WARRIOR);
        QuestTestAccess.markStarted(p);
        return p;
    }

    private static Zombie zombie(GameTestHelper helper, int x, int z) {
        return helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(x, 2, z));
    }

    private static void kill(ServerPlayer p, LivingEntity e) {
        e.hurt(p.damageSources().playerAttack(p), 10000);
    }

    private static String stage(Quest q) {
        Run run = q == null ? null : Run.of(q);
        return run == null ? "none" : run.stage();
    }

    /** The language key of a quest's outcome line, or "" if it has none. */
    private static String outcomeKey(Quest q) {
        Component c = q.outcome();
        return c != null && c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
    }

    /** All the XP the hunter has earned (levels included), so gains add up across a level-up. */
    private static long xp(ServerPlayer p) {
        long total = SystemAPI.get(p).xp();
        for (int level = 1; level < SystemAPI.get(p).level(); level++) total += Progression.xpToNext(level);
        return total;
    }

    // ------------------------------------------------------------------ an overwhelming foe

    @GameTest(template = ARENA)
    public static void survivingTurnsIntoAnnihilateWhenTheFoeBreaks(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E);
        Zombie foe = zombie(helper, 3, 3);
        foe.setTarget(p);
        Quest survive = Situations.FOE.survive(p, foe, 3.0);
        helper.assertTrue(SituationTestAccess.begin(p, Situations.FOE, survive), "the survive quest starts");
        helper.assertTrue(survive.isForced(), "danger does not wait for an answer");
        int index = QuestEngine.data(p).all().indexOf(survive);

        // a few seconds held, then the creature breaks
        for (int i = 0; i < 3; i++) Situations.FOE.update(p, survive, Run.of(survive));
        helper.assertTrue(Situations.isRunning(p, survive), "still surviving while it is whole");
        foe.setHealth(foe.getMaxHealth() * 0.3f);
        Situations.FOE.update(p, survive, Run.of(survive));
        Quest annihilate = Situations.running(p, Situations.FOE);
        helper.assertTrue(OverwhelmingFoe.ANNIHILATE.equals(stage(annihilate)), "the quest turns into Annihilate, got " + stage(annihilate));
        helper.assertTrue(QuestEngine.data(p).all().indexOf(annihilate) == index, "the new quest takes the old one's place");
        helper.assertTrue(annihilate.rewards().statPoints() > survive.rewards().statPoints(), "finishing it pays more than surviving it");

        // what any zombie's death is worth, to tell the quest's pay apart
        long before = xp(p);
        kill(p, zombie(helper, 4, 4));
        long plain = xp(p) - before;
        before = xp(p);
        kill(p, foe);
        helper.assertTrue(!Situations.isRunning(p, annihilate), "killing it ends the quest");
        helper.assertTrue(QuestEngine.data(p).hasFinished(annihilate.template()), "and it counts as complete");
        helper.assertTrue(outcomeKey(annihilate).endsWith("outcome.annihilated"), "the System says how it ended: " + outcomeKey(annihilate));
        helper.assertTrue(annihilate.objectives().stream().filter(o -> o.display() == QuestObjective.Display.HEALTH).allMatch(o -> o.progress() == 0),
                "its health row reads empty in the completed quest");
        helper.assertTrue(xp(p) - before > plain, "annihilating it is paid (" + (xp(p) - before) + " vs " + plain + " for a plain kill)");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void fleeingAFoeBringsItBackAsANemesis(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E);
        Zombie foe = zombie(helper, 3, 3);
        foe.setTarget(p);
        float baseHealth = foe.getMaxHealth();
        Quest survive = Situations.FOE.survive(p, foe, 3.0);
        helper.assertTrue(SituationTestAccess.begin(p, Situations.FOE, survive), "the survive quest starts");

        // the player runs: far enough, for long enough, counts as getting away
        BlockPos home = p.blockPosition();
        p.moveTo(p.getX() + OverwhelmingFoe.ESCAPE_DISTANCE + 20, p.getY(), p.getZ());
        for (int i = 0; i < OverwhelmingFoe.ESCAPE_SECONDS && Situations.isRunning(p, survive); i++) {
            Situations.FOE.update(p, survive, Run.of(survive));
        }
        helper.assertTrue(QuestEngine.data(p).hasFinished(survive.template()), "getting away still counts as surviving");
        helper.assertTrue(outcomeKey(survive).endsWith("outcome.escaped_hunted"), "the System warns it will be back: " + outcomeKey(survive));
        helper.assertTrue(foe.isRemoved(), "the creature withdrew (no twin is left behind)");
        SituationData data = FollowUps.data(p);
        helper.assertTrue(data.followUps().size() == 1 && data.followUps().get(0).kind().equals(OverwhelmingFoe.NEMESIS_FOLLOW_UP),
                "its return is waiting: " + data.followUps());

        // it finds the player again, stronger (the ground around the arena loaded, as it is around a real player)
        p.moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
        ChunkPos at = new ChunkPos(home);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) helper.getLevel().getChunk(at.x + dx, at.z + dz);
        helper.assertTrue(FollowUps.hurry(p, OverwhelmingFoe.NEMESIS_FOLLOW_UP) == 1, "the return can be brought forward");
        SituationTestAccess.runFollowUps(p);
        Quest nemesis = Situations.running(p, Situations.FOE);
        helper.assertTrue(OverwhelmingFoe.NEMESIS.equals(stage(nemesis)), "it came back as a Nemesis, got " + stage(nemesis)
                + " (still waiting: " + data.followUps().stream().map(f -> f.kind() + " tries " + f.data().getInt("tries")).toList()
                + ", a spot 14-22 blocks away: " + Foes.spotAround(helper.getLevel(), p.blockPosition(), 14, 22) + ")");
        LivingEntity back = Foes.find(p, Run.of(nemesis).target());
        helper.assertTrue(back instanceof Zombie, "the same kind of creature came back");
        helper.assertTrue(back.getMaxHealth() > baseHealth, "and it grew stronger (" + back.getMaxHealth() + " > " + baseHealth + ")");
        helper.assertTrue(back.distanceTo(p) >= 6, "it arrives from a distance, not on top of the player");
        helper.assertTrue(data.followUps().isEmpty(), "nothing else is waiting");

        kill(p, back);
        helper.assertTrue(QuestEngine.data(p).hasFinished(nemesis.template()), "killing the nemesis completes it");
        helper.assertTrue(data.nemesesSlain() == 1, "the System counts it");
        helper.assertTrue(Secrets.found(p, Secrets.NEMESIS_SLAIN), "and a secret quest is revealed for it");
        helper.succeed();
    }

    // ------------------------------------------------------------------ the edge of death

    @GameTest(template = ARENA)
    public static void lastStandGivesASecondWind(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.D);
        List<Zombie> hunters = List.of(zombie(helper, 3, 1), zombie(helper, 3, 3), zombie(helper, 1, 3));
        for (Zombie z : hunters) z.setTarget(p);
        Watch w = Situations.watch(p);
        w.lastHurtByCreature = QuestEngine.gameTime(p);
        Quest stand = Situations.LAST_STAND.stand(p);
        helper.assertTrue(SituationTestAccess.begin(p, Situations.LAST_STAND, stand), "the last stand starts");

        long before = xp(p);
        for (int i = 0; i < LastStand.STAND_SECONDS; i++) Situations.LAST_STAND.update(p, stand, Run.of(stand));
        Quest wind = Situations.running(p, Situations.LAST_STAND);
        helper.assertTrue(LastStand.SECOND_WIND.equals(stage(wind)), "holding on with them still there turns into a second wind, got " + stage(wind));
        helper.assertTrue(xp(p) > before, "holding on was paid when the quest changed");
        helper.assertTrue(p.hasEffect(MobEffects.DAMAGE_BOOST) && p.hasEffect(MobEffects.REGENERATION), "the System lends strength");
        helper.assertTrue(Secrets.found(p, Secrets.UNBROKEN), "holding on to the end is a secret quest");
        helper.assertTrue(wind.objectives().get(0).required() == hunters.size(), "strike back at every hunter (up to three)");

        for (Zombie z : hunters) kill(p, z);
        helper.assertTrue(QuestEngine.data(p).hasFinished(wind.template()), "striking back completes it");
        helper.assertTrue(outcomeKey(wind).endsWith("outcome.turned_tables"), "the System says how: " + outcomeKey(wind));
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void catchingYourBreathHealsAndAHitBreaksIt(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.C);
        Watch w = Situations.watch(p);
        long now = QuestEngine.gameTime(p);

        // a blow ends the rest
        w.lastHurtByCreature = now - 200;
        w.lastHurt = now - 200;
        w.lastHit = now;
        Quest broken = Situations.CATCH_BREATH.rest(p);
        helper.assertTrue(SituationTestAccess.begin(p, Situations.CATCH_BREATH, broken), "the rest starts");
        Situations.CATCH_BREATH.update(p, broken, Run.of(broken));
        helper.assertTrue(!Situations.isRunning(p, broken) && !QuestEngine.data(p).hasFinished(broken.template()), "fighting again breaks the rest");
        helper.assertTrue(outcomeKey(broken).endsWith("outcome.rest_broken"), "the System says why: " + outcomeKey(broken));
        helper.assertTrue(!Situations.room(p, false), "a calm situation leaves a gap before the next one");
        helper.assertTrue(Situations.room(p, true), "but danger never waits for the gap");

        // quiet: it heals
        QuestEngine.data(p).setCooldown(Situations.GAP_KEY, 0);
        w.lastHit = now - 200;
        Quest rest = Situations.CATCH_BREATH.rest(p);
        helper.assertTrue(SituationTestAccess.begin(p, Situations.CATCH_BREATH, rest), "a new rest starts");
        helper.assertTrue(!Situations.room(p, false), "one calm situation at a time");
        for (int i = 0; i < CatchBreath.SECONDS; i++) Situations.CATCH_BREATH.update(p, rest, Run.of(rest));
        helper.assertTrue(QuestEngine.data(p).hasFinished(rest.template()), "resting the whole time completes it");
        helper.assertTrue(p.hasEffect(MobEffects.REGENERATION) && p.hasEffect(MobEffects.ABSORPTION), "and the System helps them back up");
        helper.succeed();
    }

    // ------------------------------------------------------------------ streaks

    @GameTest(template = ARENA)
    public static void untouchableMovesUpATierAndKeepsWhatWasPaid(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E);
        Quest first = SituationTestAccess.firstTier(p, Situations.UNTOUCHABLE);
        helper.assertTrue(SituationTestAccess.begin(p, Situations.UNTOUCHABLE, first), "the streak quest starts");
        for (int i = 0; i < Streaks.Untouchable.TIERS[0] - 2; i++) kill(p, zombie(helper, 3, 3));
        long before = xp(p);
        kill(p, zombie(helper, 3, 3));
        long plain = xp(p) - before;
        before = xp(p);
        kill(p, zombie(helper, 3, 3));
        Quest second = Situations.running(p, Situations.UNTOUCHABLE);
        helper.assertTrue("tier2".equals(stage(second)), "a clean tier moves the quest up, got " + stage(second));
        helper.assertTrue(second.objectives().get(0).required() == Streaks.Untouchable.TIERS[1], "the next tier asks for more");
        helper.assertTrue(xp(p) - before > plain, "the first tier is paid on the spot");

        kill(p, zombie(helper, 3, 3));
        long paid = xp(p);
        Zombie hitter = zombie(helper, 2, 3);
        DamageSource blow = p.damageSources().mobAttack(hitter);
        Situations.UNTOUCHABLE.playerHurt(p, blow, 2f, Situations.watch(p));
        helper.assertTrue(!Situations.isRunning(p, second), "a blow from a creature breaks the streak");
        helper.assertTrue(outcomeKey(second).endsWith("outcome.streak_banked"), "what was paid stays paid: " + outcomeKey(second));
        helper.assertTrue(xp(p) == paid, "a broken tier pays nothing");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void frenzyFadesWithoutLosingItsTiers(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E);
        Quest first = SituationTestAccess.firstTier(p, Situations.FRENZY);
        helper.assertTrue(SituationTestAccess.begin(p, Situations.FRENZY, first), "the frenzy quest starts");
        helper.assertTrue(first.deadline() > 0, "a frenzy is against the clock");
        for (int i = 0; i < Streaks.Frenzy.TIERS[0]; i++) kill(p, zombie(helper, 3, 3));
        Quest second = Situations.running(p, Situations.FRENZY);
        helper.assertTrue("tier2".equals(stage(second)), "the frenzy goes on, got " + stage(second));
        QuestTestAccess.expire(p, second);
        QuestEngine.tick(p);
        helper.assertTrue(!Situations.isRunning(p, second), "the clock ran out");
        helper.assertTrue(QuestEngine.data(p).hasFinished(second.template()), "a frenzy that fades after a tier is not a failure");
        helper.assertTrue(outcomeKey(second).endsWith("outcome.frenzy_faded"), outcomeKey(second));
        helper.succeed();
    }

    // ------------------------------------------------------------------ a villager in trouble

    @GameTest(template = ARENA)
    public static void savingAVillagerIsRemembered(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.D);
        Villager v = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(3, 2, 3));
        Zombie a = zombie(helper, 4, 3), b = zombie(helper, 3, 4);
        a.setTarget(v);
        b.setTarget(v);
        Quest q = Situations.PROTECT.protect(p, v, a);
        helper.assertTrue(q != null && SituationTestAccess.begin(p, Situations.PROTECT, q), "protecting them starts");
        helper.assertTrue(q.objectives().get(0).required() == 2, "both attackers count");
        kill(p, a);
        kill(p, b);
        helper.assertTrue(QuestEngine.data(p).hasFinished(q.template()), "driving them off saves the villager");
        helper.assertTrue(p.getInventory().countItem(Items.EMERALD) >= 1, "the villager thanks them with what they have");
        helper.assertTrue(v.getGossips().getReputation(p.getUUID(), t -> true) > 0, "and remembers it");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void aVillagersKillerIsHuntedDown(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.D);
        Villager v = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(3, 2, 3));
        Zombie killer = zombie(helper, 4, 3);
        killer.setTarget(v);
        Quest protect = Situations.PROTECT.protect(p, v, killer);
        helper.assertTrue(protect != null && SituationTestAccess.begin(p, Situations.PROTECT, protect), "protecting them starts");

        DamageSource bite = killer.damageSources().mobAttack(killer);
        v.hurt(bite, 10000);
        helper.assertTrue(!v.isAlive(), "the villager fell");
        // the fake player is not on the server's list, so it is told directly
        if (Situations.isRunning(p, protect)) Situations.PROTECT.runEntityDied(p, protect, Run.of(protect), v, bite);
        Quest avenge = Situations.running(p, Situations.PROTECT);
        helper.assertTrue(ProtectVillager.AVENGE.equals(stage(avenge)), "the quest turns to avenging them, got " + stage(avenge));
        helper.assertTrue(killer.getUUID().equals(Run.of(avenge).target()), "the killer is the target now");
        helper.assertTrue(!avenge.extra().getBoolean(Run.WARD), "and it is marked as a foe, not someone to keep alive");

        kill(p, killer);
        helper.assertTrue(QuestEngine.data(p).hasFinished(avenge.template()), "killing the killer avenges them");
        helper.assertTrue(outcomeKey(avenge).endsWith("outcome.avenged"), outcomeKey(avenge));
        helper.succeed();
    }

    // ------------------------------------------------------------------ secrets and follow-ups

    @GameTest(template = ARENA)
    public static void secretQuestsAreRevealedOnce(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E);
        kill(p, zombie(helper, 3, 3));
        helper.assertTrue(!Secrets.found(p, Secrets.GIANT_SLAYER), "a plain zombie is no giant for a new hunter");
        long before = xp(p);
        helper.assertTrue(Secrets.reveal(p, Secrets.GIANT_SLAYER), "a secret is revealed the first time");
        helper.assertTrue(Secrets.found(p, Secrets.GIANT_SLAYER), "and kept");
        helper.assertTrue(xp(p) > before, "it is paid");
        helper.assertTrue(Secrets.title(Secrets.GIANT_SLAYER).equals(SystemAPI.get(p).title()), "a hunter without a title gets one");
        helper.assertTrue(!Secrets.reveal(p, Secrets.GIANT_SLAYER), "but only once");
        Secrets.reveal(p, Secrets.VALOR);
        helper.assertTrue(Secrets.title(Secrets.GIANT_SLAYER).equals(SystemAPI.get(p).title()), "a second secret never replaces the title they carry");
        helper.assertTrue(FollowUps.data(p).secretsFound() == 2, "the System counts them");
        QuestData data = QuestEngine.data(p);
        helper.assertTrue(data.active().isEmpty(), "secrets never sit in the quest list");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void followUpsWaitTheirTurnAndRetry(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E);
        PINGS.clear();
        CompoundTag one = new CompoundTag();
        one.putInt("n", 1);
        CompoundTag two = new CompoundTag();
        two.putInt("n", 2);
        FollowUps.schedule(p, PING, 20 * 60, one);
        FollowUps.schedule(p, PING, 20 * 60, two);
        SituationTestAccess.runFollowUps(p);
        helper.assertTrue(PINGS.isEmpty(), "nothing happens before it is due");
        helper.assertTrue(FollowUps.hurry(p, PING) == 2, "both can be brought forward");
        SituationTestAccess.runFollowUps(p);
        helper.assertTrue(PINGS.equals(List.of(1)), "one at a time: " + PINGS);
        SituationTestAccess.runFollowUps(p);
        helper.assertTrue(PINGS.equals(List.of(1, 2)), "the next one is tried a second later: " + PINGS);
        helper.assertTrue(FollowUps.data(p).followUps().size() == 1, "one that can't happen yet waits to be tried again");
        FollowUps.hurry(p, PING);
        SituationTestAccess.runFollowUps(p);
        helper.assertTrue(PINGS.equals(List.of(1, 2, 2)) && FollowUps.data(p).followUps().isEmpty(), "and happens when it can: " + PINGS);
        helper.succeed();
    }
}
