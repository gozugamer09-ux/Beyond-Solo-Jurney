package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.association.Association;
import com.beyondsolo.association.AssociationModule;
import com.beyondsolo.chronicle.ChronicleModule;
import com.beyondsolo.chronicle.ranking.RankingData;
import com.beyondsolo.chronicle.ranking.Rankings;
import com.beyondsolo.duel.Duel;
import com.beyondsolo.duel.DuelConfig;
import com.beyondsolo.duel.DuelModule;
import com.beyondsolo.duel.DuelRecord;
import com.beyondsolo.duel.DuelistEntity;
import com.beyondsolo.duel.Duels;
import com.beyondsolo.duel.NationalHunter;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.enemy.HiddenLevel;
import com.beyondsolo.news.Headline;
import com.beyondsolo.news.NewsData;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.rival.RivalGuild;
import com.beyondsolo.rival.RivalModule;
import com.beyondsolo.scouting.GuildTerms;
import com.beyondsolo.scouting.Scouting;
import com.beyondsolo.scouting.ScoutingModule;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.Progression;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import com.beyondsolo.util.OnlinePlayers;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.shadowsoffire.apothic_attributes.api.ALObjects;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Duels with the national-level hunters ({@link Duels}): how a challenge comes, answering it, the ring's rules, every way
 * a duel ends, what a win pays and where it puts a hunter on the board. Run with {@code ./gradlew runGameTestServer}.
 *
 * <p>The tests run in a batch of their own: a ring puts out every monster that stands in it, a neighbouring test's too.
 * The stand-in hunters are fake players, which take no blows and nobody walks up to (the challenger's walk and the
 * office's Accept are for real players): blows on them are dealt as the game deals them, through its damage events, and
 * the challenger is set down by hand ({@link Duels#startWith}, {@link Duels#send}).</p>
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DuelGameTests {
    private static final String ARENA = "evaluation_zone";
    private static final String BATCH = "duels";

    /** A hunter in the arena at that rank, with no gold, no merit, no guild and a clean duel record. */
    private static ServerPlayer hunter(GameTestHelper helper, String name, PlayerRank rank) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        p.removeData(AssociationModule.RECORD);
        p.removeData(RivalModule.RECORD);
        p.setGameMode(GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        SystemAPI.awaken(p, rank, PlayerClass.WARRIOR);
        // awakening at S brings one of the four to test them, and at C and up the guilds: start from nothing
        p.removeData(DuelModule.RECORD);
        p.removeData(ScoutingModule.RECORD);
        Economy.reset(p);
        QuestTestAccess.markStarted(p);
        OnlinePlayers.addTestPlayer(helper, p);
        return p;
    }

    /** The hunter's duels forgotten: a duel under way called off, the challengers home, the record clean, their row off the board. */
    private static void done(ServerPlayer p) {
        Duels.reset(p);
        Scouting.reset(p);
        RankingData.get(p.server).forget(p.getUUID());
    }

    /** A duel with them begun in the arena: the challenger set down at its far corner (the ring is marked out towards them). */
    private static Duel duel(GameTestHelper helper, ServerPlayer p, NationalHunter who) {
        BlockPos at = helper.absolutePos(new BlockPos(5, 2, 5));
        Duel d = Duels.startWith(p, who, DuelRecord.Reason.ASKED, Vec3.atBottomCenterOf(at));
        helper.assertTrue(d != null, "a duel with " + who.id + " begins");
        DuelistEntity e = Duels.duelist(p.server, d);
        helper.assertTrue(e != null, who.id + " stands in the ring");
        var dodge = e.getAttribute(ALObjects.Attributes.DODGE_CHANCE);
        if (dodge != null) dodge.setBaseValue(0);
        return d;
    }

    private static long now(ServerPlayer p) {
        return p.server.overworld().getGameTime();
    }

    private static long day(ServerPlayer p) {
        return p.server.overworld().getDayTime() / 24000L;
    }

    /** The translation key of a line ("" if it isn't one). */
    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
    }

    /** True if the news has a headline with this line about this hunter. */
    private static boolean inTheNews(MinecraftServer server, String line, String name) {
        for (Headline h : NewsData.get(server).headlines()) if (h.text().contains(line) && h.text().contains(name)) return true;
        return false;
    }

    /** True if a blow from this source never reaches the hunter (as the game asks before dealing one). */
    private static boolean blocked(ServerPlayer p, DamageSource source) {
        return NeoForge.EVENT_BUS.post(new LivingIncomingDamageEvent(p, new DamageContainer(source, 4f))).isCanceled();
    }

    /** A blow on the hunter as the game deals it: what is left of it once everything had its say. */
    private static float blow(ServerPlayer p, DamageSource source, float amount) {
        DamageContainer c = new DamageContainer(source, amount);
        LivingDamageEvent.Pre pre = NeoForge.EVENT_BUS.post(new LivingDamageEvent.Pre(p, c));
        NeoForge.EVENT_BUS.post(new LivingDamageEvent.Post(p, c));
        return pre.getNewDamage();
    }

    private static int run(ServerPlayer p, String command) throws CommandSyntaxException {
        return p.server.getCommands().getDispatcher().execute(command, p.createCommandSourceStack().withPermission(4).withSuppressedOutput());
    }

    private static boolean fails(ServerPlayer p, String command) {
        try {
            run(p, command);
            return false;
        } catch (CommandSyntaxException e) {
            return true;
        }
    }

    /** The rows of the hunters' board: the hunter's, and theirs. */
    private static int row(List<Rankings.Row> rows, ServerPlayer p) {
        for (int i = 0; i < rows.size(); i++) if (p.getUUID().equals(rows.get(i).player())) return i;
        return -1;
    }

    private static int row(List<Rankings.Row> rows, NationalHunter h) {
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).rival() && rows.get(i).name().equals(h.rival().name())) return i;
        return -1;
    }

    /** They stand just under the hunter on the board: below them, nobody weaker than the hunter in between. */
    private static boolean justUnder(List<Rankings.Row> rows, ServerPlayer p, NationalHunter h) {
        int me = row(rows, p), them = row(rows, h);
        if (me < 0 || them <= me || !rows.get(them).beaten()) return false;
        for (int i = me + 1; i < them; i++) if (rows.get(i).value() < rows.get(me).value()) return false;
        return true;
    }

    // ================================================================ how a challenge comes

    /** A new S-rank is tested once, a few minutes on, by one of the four who isn't of their guild, at the hunter's level. */
    @GameTest(template = ARENA, batch = BATCH)
    public static void aNewSRankIsTestedOnceByAnotherGuildsHunter(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "DuelTested", PlayerRank.A);
        helper.assertTrue(Scouting.sign(p, Scouting.offerNow(p, GuildTerms.of(RivalGuild.SUNFALL)).id).ok(), "signed with Sunfall");
        long now = now(p);
        SystemAPI.setRank(p, PlayerRank.S);
        DuelRecord r = Duels.record(p);
        DuelRecord.Challenge c = r.challenge();
        helper.assertTrue(c != null && c.why == DuelRecord.Reason.TEST && r.tested(), "ranking S brings a test");
        helper.assertTrue(!c.open(now) && c.openAt - now >= DuelConfig.testDelayTicks(), "a few minutes on: " + (c.openAt - now));
        helper.assertTrue(c.who != NationalHunter.AMARA_OKAFOR, "never the hunter's own guild's national-level hunter");
        helper.assertTrue(Duels.rankedS(p) == null, "a hunter is tested once");
        float mine = HiddenLevel.hunterLevel(SystemAPI.get(p));
        for (NationalHunter h : NationalHunter.values()) {
            helper.assertTrue(Duels.fightLevel(p, h, DuelRecord.Reason.TEST) <= mine + 1e-3f, "a test is fought at the hunter's level at most");
            helper.assertTrue(Duels.fightLevel(p, h, DuelRecord.Reason.ASKED) == h.rival().levelOn(day(p)), "any other duel at theirs");
        }
        Set<NationalHunter> testers = EnumSet.noneOf(NationalHunter.class);
        for (int i = 0; i < 40; i++) {
            Duels.reset(p);
            DuelRecord.Challenge t = Duels.rankedS(p);
            helper.assertTrue(t != null && t.why == DuelRecord.Reason.TEST, "tested again with a clean record");
            testers.add(t.who);
        }
        helper.assertTrue(testers.size() == 3 && !testers.contains(NationalHunter.AMARA_OKAFOR), "the other three take turns: " + testers);
        done(p);
        helper.succeed();
    }

    /** Climbing past them on the hunters' board brings each of the four for their place, once, one at a time. */
    @GameTest(template = ARENA, batch = BATCH)
    public static void climbingPastThemOnTheBoardBringsEachForTheirPlace(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "DuelClimber", PlayerRank.S);
        DuelRecord r = Duels.record(p);
        helper.assertTrue(Duels.checkPassed(p) == null, "a new S-rank is still below all four on the board");
        SystemAPI.setLevel(p, Progression.MAX_LEVEL);
        long now = now(p);
        List<NationalHunter> came = new ArrayList<>();
        for (int i = 0; i <= NationalHunter.values().length; i++) {
            DuelRecord.Challenge c = Duels.checkPassed(p);
            if (c == null) break;
            helper.assertTrue(c.why == DuelRecord.Reason.PASSED && !c.open(now) && r.passed(c.who), c.who.id + " comes for their place");
            helper.assertTrue(Duels.checkPassed(p) == null, "one challenge stands at a time");
            came.add(c.who);
            helper.assertTrue(Duels.decline(p).ok() && r.challenge() == null, "turned down");
        }
        helper.assertTrue(came.equals(List.of(NationalHunter.values())), "each of the four comes once: " + came);
        helper.assertTrue(r.declined() == 4 && inTheNews(p.server, "news.beyondsolo.duel.declined.passed", "DuelClimber"),
                "turning them down makes the news");
        done(p);
        helper.succeed();
    }

    /** Walking out on a great guild's contract brings its national-level hunter to settle it (Red Lantern has none). */
    @GameTest(template = ARENA, batch = BATCH)
    public static void walkingOutOnAGreatGuildBringsItsHunter(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "DuelWalker", PlayerRank.A);
        helper.assertTrue(Duels.walkedOut(p, RivalGuild.RED_LANTERN) == null, "Red Lantern has nobody to send");
        helper.assertTrue(Scouting.sign(p, Scouting.offerNow(p, GuildTerms.of(RivalGuild.IRON_BASTION)).id).ok(), "signed with Iron Bastion");
        Economy.set(p, 1_000_000);
        helper.assertTrue(Scouting.breakContract(p).ok(), "walked out, the bonus paid back");
        DuelRecord.Challenge c = Duels.record(p).challenge();
        helper.assertTrue(c != null && c.who == NationalHunter.VIKTOR_KAEL && c.why == DuelRecord.Reason.GRUDGE && !c.open(now(p)),
                "Iron Bastion's ace is on his way to settle it");
        helper.assertTrue(Duels.walkedOut(p, RivalGuild.AZURE_TIDE) == null, "one challenge stands at a time");
        helper.assertTrue(Duels.decline(p).ok() && inTheNews(p.server, "news.beyondsolo.duel.declined.grudge", "DuelWalker"),
                "turning him down makes the news");
        done(p);
        helper.succeed();
    }

    // ================================================================ answering it

    /** Asking at the office (from rank A), the challenger walking up, putting it off twice, turning it down. */
    @GameTest(template = ARENA, batch = BATCH)
    public static void askingAtTheOfficeAndPuttingItOff(GameTestHelper helper) {
        ServerPlayer low = hunter(helper, "DuelRookie", PlayerRank.B);
        Association.Outcome o = Duels.ask(low, NationalHunter.REN_TAKASHIRO);
        helper.assertTrue(!o.ok() && key(o.text()).endsWith("outcome.rank"), "not below rank " + DuelConfig.minRank());
        done(low);

        ServerPlayer p = hunter(helper, "DuelAsker", PlayerRank.A);
        DuelRecord r = Duels.record(p);
        o = Duels.ask(p, NationalHunter.REN_TAKASHIRO);
        DuelRecord.Challenge c = r.challenge();
        helper.assertTrue(o.ok() && c != null && c.who == NationalHunter.REN_TAKASHIRO && c.why == DuelRecord.Reason.ASKED, "asked for");
        helper.assertTrue(!c.open(now(p)) && c.openAt - now(p) <= 20 * 10, "he comes in a few seconds");
        o = Duels.ask(p, NationalHunter.HAN_SEORI);
        helper.assertTrue(!o.ok() && key(o.text()).endsWith("outcome.standing"), "one challenge at a time");
        o = Duels.accept(p);
        helper.assertTrue(!o.ok() && key(o.text()).endsWith("outcome.not_yet"), "nothing to take before he comes");
        CompoundTag office = Duels.officeTag(p);
        helper.assertTrue(office.getList("hunters", Tag.TAG_COMPOUND).size() == 4
                && office.getCompound("challenge").getInt("who") == NationalHunter.REN_TAKASHIRO.ordinal()
                && office.getLong("purse") == Duels.purse() && office.getInt("merit") == DuelConfig.merit(),
                "the office's tab: the four, the challenge, the stakes");

        // it opens: the hunter is told, and it runs out in half an hour
        c.openAt = now(p);
        Duels.tick(p);
        helper.assertTrue(c.told && c.lapseAt == now(p) + DuelConfig.challengeTicks(), "open, and told");
        DuelistEntity e = Duels.send(p, c);
        helper.assertTrue(e != null && Duels.challenger(p) == e, "he sets out for the hunter");
        helper.assertTrue(e.who() == NationalHunter.REN_TAKASHIRO && p.getUUID().equals(e.hunter()) && c.id.equals(e.challenge()),
                "Ren Takashiro, for this hunter, with this challenge");
        helper.assertTrue(!e.getMainHandItem().isEmpty() && !e.shouldBeSaved(), "his own blade in hand; never saved with the world");
        helper.assertFalse(e.hurt(p.damageSources().playerAttack(p), 5f), "nobody lays a hand on him before the bell");

        o = Duels.later(p);
        helper.assertTrue(o.ok() && c.later == 1 && !c.open(now(p)) && c.openAt - now(p) == DuelConfig.returnTicks() && e.leaving(),
                "put off: he goes, and comes back later");
        helper.assertTrue(Duels.later(p).ok() && c.later == Duels.MAX_LATER, "twice");
        o = Duels.later(p);
        helper.assertTrue(!o.ok() && key(o.text()).endsWith("outcome.no_later"), "not a third time");
        o = Duels.decline(p);
        helper.assertTrue(o.ok() && r.challenge() == null && r.declined() == 1, "turned down");
        helper.assertFalse(inTheNews(p.server, "news.beyondsolo.duel.declined", "DuelAsker"), "turning down a duel the hunter asked for is no news");
        helper.assertFalse(Duels.lapseNow(p), "nothing left to lapse");
        done(p);
        helper.succeed();
    }

    /** A challenge nobody answers runs out. */
    @GameTest(template = ARENA, batch = BATCH)
    public static void aChallengeLeftUnansweredLapses(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "DuelIgnorer", PlayerRank.A);
        DuelRecord.Challenge c = Duels.challenge(p, NationalHunter.HAN_SEORI, DuelRecord.Reason.TEST, now(p));
        Duels.tick(p);
        helper.assertTrue(c.told && c.lapseAt == now(p) + DuelConfig.challengeTicks(), "open, and told");
        c.lapseAt = now(p);
        Duels.tick(p);
        DuelRecord r = Duels.record(p);
        helper.assertTrue(r.challenge() == null && r.declined() == 0, "it lapsed, and a lapse is no refusal");
        done(p);
        helper.succeed();
    }

    // ================================================================ the duel

    /** The ring: the count of three, the bell, only their blows land on each other; the challenger yields and it is won. */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 200)
    public static void theDuelIsFoughtInTheRingAndWonWhenTheyYield(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "DuelVictor", PlayerRank.A);
        MinecraftServer server = p.server;
        Duel d = duel(helper, p, NationalHunter.VIKTOR_KAEL);
        DuelistEntity e = Duels.duelist(server, d);
        helper.assertTrue(Duels.running(p.getUUID()) == d && d.phase == Duel.Phase.COUNTDOWN && e.state() == DuelistEntity.READY,
                "the count begins, the challenger at the ready");
        helper.assertTrue(d.radius == DuelConfig.ringRadius() && d.inside(p.position(), 0) && d.inside(e.position(), 0), "both in the ring");
        helper.assertTrue(p.distanceTo(e) > d.radius * 0.8, "the challenger takes the far side: " + p.distanceTo(e));
        helper.assertTrue(d.fightAt - d.startedAt == Duels.COUNTDOWN && d.endsAt - d.fightAt == DuelConfig.timeLimitTicks(), "a count of three, then the clock");
        helper.assertTrue(e.getMaxHealth() >= 40 && e.getHealth() == e.getMaxHealth() && e.power() >= 2, "a boss's strength for their level");

        Zombie z = EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertTrue(z != null, "a zombie");
        helper.assertTrue(blocked(p, helper.getLevel().damageSources().mobAttack(e)), "nothing lands before the bell");
        helper.assertFalse(e.hurt(p.damageSources().playerAttack(p), 5f), "not on the challenger either");

        Duels.bell(server, d);
        helper.assertTrue(d.phase == Duel.Phase.FIGHT && e.state() == DuelistEntity.FIGHTING, "the bell");
        helper.assertTrue(blocked(p, helper.getLevel().damageSources().mobAttack(z)), "nobody comes between them: a third party's blow doesn't land");
        helper.assertFalse(blocked(p, helper.getLevel().damageSources().mobAttack(e)), "the challenger's does");
        helper.assertFalse(e.hurt(helper.getLevel().damageSources().mobAttack(z), 5f), "nor a third party's on the challenger");
        helper.assertTrue(e.hurt(p.damageSources().playerAttack(p), 5f) && e.getHealth() < e.getMaxHealth(), "the hunter's blows land");

        SystemData sd = SystemAPI.get(p);
        int level = sd.level();
        long xp = sd.xp();
        e.invulnerableTime = 0;
        e.hurt(p.damageSources().playerAttack(p), 1.0e7f);
        helper.assertTrue(e.isAlive() && e.getHealth() >= 1f && d.result == Duel.Result.WON && d.phase == Duel.Phase.OVER,
                "at the end of his strength he yields, he doesn't fall: " + d.result);
        helper.startSequence().thenExecuteAfter(3, () -> {
            DuelRecord r = Duels.record(p);
            NationalHunter v = NationalHunter.VIKTOR_KAEL;
            helper.assertTrue(d.settled && r.wins(v) == 1 && r.last(v) == DuelRecord.WON && r.lastDay(v) == day(p) && r.challenge() == null,
                    "a win on the record");
            helper.assertTrue(e.state() == DuelistEntity.YIELDED, "down on one knee");
            helper.assertTrue(Economy.gold(p) == Duels.purse() && d.purse == Duels.purse(), "the purse: " + Economy.gold(p));
            helper.assertTrue(Association.record(p).merit() == DuelConfig.merit() && d.merit == DuelConfig.merit(), "merit");
            helper.assertTrue(d.xp > 0 && (sd.level() > level || sd.xp() > xp), "System XP");
            helper.assertTrue(ChronicleModule.data(p).hasTitle("title.beyondsolo.duelist"), "the first win: Duelist");
            helper.assertTrue(inTheNews(server, "news.beyondsolo.duel.won.asked", "DuelVictor"), "the news has it");
            List<Rankings.Row> rows = Rankings.hunters(server);
            helper.assertTrue(justUnder(rows, p, v), "Viktor Kael stands just under the hunter on the board now");
            Duels.wipe(p);
            Association.Outcome o = Duels.ask(p, v);
            helper.assertTrue(!o.ok() && key(o.text()).endsWith("outcome.rematch"), "no rematch before " + DuelConfig.rematchDays() + " day");
            done(p);
        }).thenSucceed();
    }

    /** A blow that would kill the hunter makes them yield instead: it is lost, and nothing more lands. */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 200)
    public static void aBlowThatWouldKillTheHunterMakesThemYield(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "DuelYielder", PlayerRank.A);
        Duel d = duel(helper, p, NationalHunter.AMARA_OKAFOR);
        DuelistEntity e = Duels.duelist(p.server, d);
        Duels.bell(p.server, d);
        DamageSource hers = helper.getLevel().damageSources().mobAttack(e);
        helper.assertTrue(blow(p, hers, 3f) == 3f && d.result == null, "a blow they can take lands as it is");
        helper.assertTrue(blow(p, hers, 1.0e6f) == 0f, "the one that would have killed them lands as nothing");
        helper.assertTrue(d.result == Duel.Result.LOST && d.phase == Duel.Phase.OVER, "they yield: it is over at once");
        helper.assertTrue(blocked(p, hers), "nothing more of hers lands");
        long gold = Economy.gold(p);
        helper.startSequence().thenExecuteAfter(3, () -> {
            DuelRecord r = Duels.record(p);
            NationalHunter a = NationalHunter.AMARA_OKAFOR;
            helper.assertTrue(d.settled && r.losses(a) == 1 && r.wins(a) == 0 && r.last(a) == DuelRecord.LOST && r.challenge() == null,
                    "a loss on the record");
            helper.assertTrue(Economy.gold(p) == gold && d.purse == 0, "no purse");
            helper.assertTrue(e.state() == DuelistEntity.STANDING, "she stands over the hunter");
            helper.assertTrue(inTheNews(p.server, "news.beyondsolo.duel.lost.asked", "DuelYielder"), "the news has it");
            helper.assertTrue(!justUnder(Rankings.hunters(p.server), p, a), "a loss moves nobody on the board");
            done(p);
        }).thenSucceed();
    }

    /** Standing outside the ring for too long forfeits: a loss. */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 300)
    public static void leavingTheRingForfeits(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "DuelRunner", PlayerRank.A);
        Duel d = duel(helper, p, NationalHunter.REN_TAKASHIRO);
        Duels.bell(p.server, d);
        Vec3 away = d.center.add(d.radius + 8, 0, 0);
        p.moveTo(away.x, away.y, away.z, 0f, 0f);
        int half = DuelConfig.outsideTicks() / 2;
        helper.startSequence()
                .thenExecuteAfter(half, () -> helper.assertTrue(d.result == null && d.outside > 0, "the count outside runs: " + d.outside + " " + d.result))
                .thenExecuteAfter(half + 10, () -> {
                    DuelRecord r = Duels.record(p);
                    helper.assertTrue(d.result == Duel.Result.FORFEIT, "out of the ring too long: " + d.result);
                    helper.assertTrue(r.losses(NationalHunter.REN_TAKASHIRO) == 1 && r.forfeits() == 1, "a forfeit counts as a loss");
                    helper.assertTrue(inTheNews(p.server, "news.beyondsolo.duel.forfeit", "DuelRunner"), "the news has it");
                    done(p);
                })
                .thenSucceed();
    }

    /** The time running out is a draw; a duel called off (the hunter gone) puts nothing on the record, and the challenge stands. */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 200)
    public static void theClockRunningOutIsADrawAndCallingItOffLeavesTheChallenge(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "DuelStalemate", PlayerRank.A);
        Duel d = duel(helper, p, NationalHunter.HAN_SEORI);
        Duels.bell(p.server, d);
        d.endsAt = now(p);
        helper.startSequence().thenExecuteAfter(2, () -> {
            DuelRecord r = Duels.record(p);
            NationalHunter h = NationalHunter.HAN_SEORI, v = NationalHunter.VIKTOR_KAEL;
            helper.assertTrue(d.result == Duel.Result.DRAWN && r.draws(h) == 1 && r.last(h) == DuelRecord.DRAWN, "a draw: " + d.result);
            helper.assertTrue(inTheNews(p.server, "news.beyondsolo.duel.drawn", "DuelStalemate"), "the news has it");
            Duels.wipe(p);
            Duel again = duel(helper, p, v);
            Duels.bell(p.server, again);
            Duels.wipe(p);
            DuelRecord.Challenge c = r.challenge();
            helper.assertTrue(again.result == Duel.Result.CALLED_OFF && r.wins(v) + r.losses(v) + r.draws(v) == 0, "called off: nothing on the record");
            helper.assertTrue(c != null && c.who == v && !c.open(now(p)) && !c.told, "the challenge stands, and he comes again");
            helper.assertTrue(Duels.running(p.getUUID()) == null && Duels.challenger(p) == null, "nobody left in the ring");
            done(p);
        }).thenSucceed();
    }

    /** Beating all four makes a national-level hunter: the title, breaking news, and all four under them on the board. */
    @GameTest(template = ARENA, batch = BATCH)
    public static void beatingAllFourMakesANationalLevelHunter(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "DuelChampion", PlayerRank.A);
        DuelRecord r = Duels.record(p);
        for (NationalHunter h : NationalHunter.values()) {
            helper.assertFalse(r.national(), "not before all four");
            Duel d = duel(helper, p, h);
            Duels.bell(p.server, d);
            Duels.finish(p.server, d, Duel.Result.WON);
            helper.assertTrue(d.settled && r.wins(h) == 1, h.id + " beaten");
            Duels.wipe(p);
        }
        helper.assertTrue(r.national() && r.beatenAll() && r.totalWins() == 4, "all four beaten");
        helper.assertTrue(ChronicleModule.data(p).hasTitle("title.beyondsolo.national_level_hunter"), "National-Level Hunter");
        boolean breaking = false;
        for (Headline h : NewsData.get(p.server).headlines()) {
            if (h.text().contains("news.beyondsolo.duel.national") && h.text().contains("DuelChampion")) breaking = h.breaking();
        }
        helper.assertTrue(breaking, "breaking news");
        helper.assertTrue(Duels.officeTag(p).getBoolean("national"), "the office knows");
        List<Rankings.Row> rows = Rankings.hunters(p.server);
        for (NationalHunter h : NationalHunter.values()) helper.assertTrue(justUnder(rows, p, h), h.id + " stands under the hunter on the board");
        done(p);
        helper.succeed();
    }

    // ================================================================ commands

    @GameTest(template = ARENA, batch = BATCH)
    public static void soloDuelCommands(GameTestHelper helper) throws CommandSyntaxException {
        ServerPlayer p = hunter(helper, "DuelCommander", PlayerRank.A);
        DuelRecord r = Duels.record(p);
        run(p, "solo duel challenge han_seori grudge");
        DuelRecord.Challenge c = r.challenge();
        helper.assertTrue(c != null && c.who == NationalHunter.HAN_SEORI && c.why == DuelRecord.Reason.GRUDGE && c.told,
                "/solo duel challenge: made, open, and the hunter told");
        run(p, "solo duel info");
        helper.assertTrue(run(p, "solo duel lapse") == 1 && r.challenge() == null, "/solo duel lapse");
        run(p, "solo duel start viktor_kael");
        Duel d = Duels.running(p.getUUID());
        helper.assertTrue(d != null && d.who == NationalHunter.VIKTOR_KAEL && d.phase == Duel.Phase.COUNTDOWN, "/solo duel start");
        run(p, "solo duel bell");
        helper.assertTrue(d.phase == Duel.Phase.FIGHT, "/solo duel bell");
        DuelistEntity e = Duels.duelist(p.server, d);
        run(p, "solo duel move quake_slam");
        helper.assertTrue(e != null && e.currentMove() == DuelistEntity.Move.QUAKE_SLAM, "/solo duel move");
        helper.assertTrue(fails(p, "solo duel move sun_arrow"), "not one of his");
        run(p, "solo duel unleash");
        helper.assertTrue(e.currentMove() == DuelistEntity.Move.UNLEASH, "/solo duel unleash");
        run(p, "solo duel win");
        helper.assertTrue(d.result == Duel.Result.WON && r.wins(NationalHunter.VIKTOR_KAEL) == 1, "/solo duel win");
        helper.assertTrue(fails(p, "solo duel bell"), "no duel to ring the bell for");
        run(p, "solo duel reset");
        helper.assertTrue(Duels.record(p).totalWins() == 0 && Duels.running(p.getUUID()) == null, "/solo duel reset");
        run(p, "solo help duels");
        done(p);
        helper.succeed();
    }

    // ================================================================ every line written

    /** Every line a duel can say, show or put in the news has its English (the Spanish is checked by TranslationGameTests). */
    @GameTest(template = ARENA, batch = BATCH)
    public static void everyDuelLineIsWritten(GameTestHelper helper) {
        JsonObject en = TranslationGameTests.read("beyondsolo_duels", "en_us");
        helper.assertTrue(en != null, "the duels' English");
        String k = Duels.KEY, news = "news.beyondsolo.duel.";
        List<String> keys = new ArrayList<>();
        for (NationalHunter h : NationalHunter.values()) {
            keys.add(k + "epithet." + h.id);
            keys.add(k + "unleashed." + h.style.key());
            for (String s : List.of("begin", "unleash", "later", "declined", "won", "lost", "drawn", "forfeit")) keys.add(k + "say." + s + "." + h.id);
            for (DuelRecord.Reason why : DuelRecord.Reason.values()) keys.add(k + "greet." + why.key() + "." + h.id);
        }
        for (DuelistEntity.Move m : DuelistEntity.Move.values()) keys.add(k + "move." + m.key());
        for (DuelRecord.Reason why : DuelRecord.Reason.values()) {
            keys.add(k + "notify.coming." + why.key());
            keys.add(k + "notify.waiting." + why.key());
            keys.add(news + "won." + why.key());
            keys.add(news + "won_again." + why.key());
            keys.add(news + "lost." + why.key());
            if (why != DuelRecord.Reason.ASKED) keys.add(news + "declined." + why.key());
        }
        for (Duel.Result r : Duel.Result.values()) {
            String rk = r.name().toLowerCase(Locale.ROOT);
            for (String s : List.of("result.", "line.", "watch.")) keys.add("ui.beyondsolo.duel.hud." + s + rk);
            if (r != Duel.Result.CALLED_OFF) {
                keys.add(k + "notify.result." + rk);
                keys.add(k + "notify.result." + rk + ".body");
            }
        }
        for (String o : List.of("unregistered", "rank", "dueling", "standing", "rematch", "asked", "none", "not_yet", "not_here", "no_room",
                "accepted", "no_later", "later", "declined")) keys.add(k + "outcome." + o);
        for (String s : List.of("national", "hint", "outside", "notify.title", "notify.lapsed", "notify.national.title", "notify.national",
                "say.not_you", "say.done", "say.gave_up")) keys.add(k + s);
        keys.addAll(List.of(news + "drawn", news + "forfeit", news + "national", "entity.beyondsolo.national_hunter",
                "gold.beyondsolo.reason.duel", "chronicle.beyondsolo.rankings.beaten", "title.beyondsolo.duelist",
                "title.beyondsolo.national_level_hunter", "ui.beyondsolo.association.tab.duels"));
        List<String> missing = keys.stream().filter(x -> !en.has(x)).toList();
        helper.assertTrue(missing.isEmpty(), "lines missing: " + missing);
        helper.succeed();
    }

    private DuelGameTests() {}
}
