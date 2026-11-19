package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.awakening.AwakeningService;
import com.beyondsolo.chronicle.ChronicleModule;
import com.beyondsolo.chronicle.title.Titles;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.home.HomeAdmin;
import com.beyondsolo.home.HomeConfig;
import com.beyondsolo.home.HomeJournal;
import com.beyondsolo.home.HomeModule;
import com.beyondsolo.home.HomeRecord;
import com.beyondsolo.home.HomeTestAccess;
import com.beyondsolo.home.HomeTier;
import com.beyondsolo.home.HomeWorld;
import com.beyondsolo.home.Homes;
import com.beyondsolo.home.TrainingDummy;
import com.beyondsolo.home.TrophyPedestalBlockEntity;
import com.beyondsolo.party.Parties;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.story.Kin;
import com.beyondsolo.story.KinEntity;
import com.beyondsolo.story.KinRecord;
import com.beyondsolo.story.LongSleep;
import com.beyondsolo.story.StoryModule;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.util.OnlinePlayers;
import com.beyondsolo.worldboss.WorldBossModule;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The hunter's residence: the floor plan sound for any plot, buying each part with level, rank and gold, going home by
 * standing still and back to the very spot, the gathering broken by a step or a blow, a party member's visit and its
 * end, the home holding against strangers, blasts and its own walls, a night's rest, the dummies that take every blow
 * and never fall, the trophy hall, the mother moving in and out, and a fall caught.
 *
 * <p>The game-test server has no datapack dimensions, so the homes are their floors alone, floating over the overworld
 * down a lane of their own ({@link HomeWorld#level}). Fake players don't tick or load chunks: the tests keep a home's
 * ground loaded themselves and run a hunter's tick in a home with {@link HomeTestAccess#tick}.</p>
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HomeGameTests {
    private static final String ARENA = "evaluation_zone";

    /** An awakened hunter of {@code rank} at {@code level} (or a player the System hasn't awakened, for null), with no home yet. */
    private static ServerPlayer hunter(GameTestHelper helper, String name, @Nullable PlayerRank rank, int level) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        AwakeningService.reset(p);
        Economy.reset(p);
        ChronicleModule.data(p).reset();
        p.removeData(HomeModule.RECORD);
        p.removeData(StoryModule.RECORD);
        p.removeAllEffects();
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        if (rank != null) {
            SystemAPI.awaken(p, rank, PlayerClass.WARRIOR);
            QuestTestAccess.markStarted(p);
            SystemAPI.setLevel(p, level);
        }
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        OnlinePlayers.addTestPlayer(helper, p);
        return p;
    }

    private static String key(Homes.Outcome out) {
        return out.text().getContents() instanceof TranslatableContents t ? t.getKey().substring(t.getKey().lastIndexOf('.') + 1) : "";
    }

    private static HomeRecord record(ServerPlayer p) {
        return Homes.record(p);
    }

    private static HomeWorld.Layout layout(ServerPlayer p) {
        HomeWorld.Layout l = HomeTestAccess.layout(p);
        if (l == null) throw new IllegalStateException(p.getGameProfile().getName() + " has no plot");
        return l;
    }

    /** The chunks of a home's floor, forced on (true) or let go. */
    private static List<ChunkPos> force(ServerLevel level, HomeWorld.Layout l, boolean on, @Nullable List<ChunkPos> before) {
        List<ChunkPos> chunks = before != null ? before : new ArrayList<>();
        if (before == null) {
            BlockPos o = l.origin();
            for (int x = (o.getX() - HomeWorld.HALF_X - 2) >> 4; x <= (o.getX() + HomeWorld.HALF_X + 2) >> 4; x++) {
                for (int z = (o.getZ() - HomeWorld.HALF_Z - 2) >> 4; z <= (o.getZ() + HomeWorld.HALF_Z + 2) >> 4; z++) chunks.add(new ChunkPos(x, z));
            }
        }
        for (ChunkPos c : chunks) level.setChunkForced(c.x, c.z, on);
        return chunks;
    }

    private static boolean broken(ServerLevel level, BlockPos pos, Player p) {
        BlockState state = level.getBlockState(pos);
        return !NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, p)).isCanceled();
    }

    // ------------------------------------------------------------------ the plan

    /**
     * For any plot the floor plan holds together: the foyer, the beds and the lift's button in the Studio, the mother's
     * bed, the desk and the bell in the Family Home's wing, the dummies in the hall and the pedestals in the gallery; what
     * the home is built round can't be broken, the room round it can; and the plots lie apart.
     */
    @GameTest(template = ARENA)
    public static void theFloorPlanHoldsTogether(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (boolean small : new boolean[]{true, false}) {
            for (int plot : new int[]{0, 1, 7, 40}) {
                HomeWorld.Layout l = HomeTestAccess.plan(small, plot);
                String at = (small ? "test" : "full") + " plot " + plot + ": ";
                check(problems, at + "the foyer", l, BlockPos.containing(l.arrival()), HomeWorld.Room.STUDIO, false);
                check(problems, at + "the lift's button", l, l.button(), HomeWorld.Room.STUDIO, true);
                for (BlockPos bed : l.beds()) check(problems, at + "their bed", l, bed, HomeWorld.Room.STUDIO, true);
                check(problems, at + "her bed", l, l.motherBed(), HomeWorld.Room.FAMILY, true);
                check(problems, at + "the desk", l, l.desk(), HomeWorld.Room.FAMILY, true);
                check(problems, at + "the bell", l, l.bell(), HomeWorld.Room.FAMILY, true);
                for (int i = 0; i < HomeWorld.DUMMIES; i++) check(problems, at + "dummy " + i, l, BlockPos.containing(l.dummy(i)), HomeWorld.Room.HALL, false);
                for (int i = 0; i < HomeWorld.TROPHIES.length; i++) check(problems, at + "pedestal " + i, l, l.pedestal(i), HomeWorld.Room.GALLERY, true);
                check(problems, at + "the living room", l, l.at(3, 1, -3), HomeWorld.Room.STUDIO, false);
                if (small && HomeWorld.plotAt(helper.getLevel(), l.origin()) != plot) problems.add(at + "its floor isn't in its plot");
                HomeWorld.Layout next = HomeTestAccess.plan(small, plot + 1);
                if (next.origin().getX() - l.origin().getX() < 2 * HomeWorld.HALF_X + 20) problems.add(at + "the next plot is too close");
            }
        }
        helper.assertTrue(problems.isEmpty(), problems.size() + " problem(s): " + String.join("; ", problems.subList(0, Math.min(8, problems.size()))));
        helper.succeed();
    }

    private static void check(List<String> problems, String what, HomeWorld.Layout l, BlockPos pos, HomeWorld.Room room, boolean fixture) {
        if (l.roomAt(pos) != room) problems.add(what + " is in " + l.roomAt(pos) + ", not " + room);
        if (l.fixture(pos) != fixture) problems.add(what + (fixture ? " can be broken" : " can't be touched"));
        if (!l.inPlot(pos)) problems.add(what + " is outside the plot");
    }

    // ------------------------------------------------------------------ owning it

    /**
     * Each part of the home asks for its level, rank and gold in turn, and the System's awakening first; buying the
     * first earns A Place to Return, the Penthouse Skyline Sovereign; past the Penthouse there is nothing to buy.
     */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void eachPartAsksForLevelRankAndGold(GameTestHelper helper) {
        ServerPlayer plain = hunter(helper, "home_plain", null, 1);
        helper.assertTrue(key(Homes.buy(plain)).equals("unawakened"), "a player the System hasn't awakened buys nothing: " + key(Homes.buy(plain)));
        ServerPlayer p = hunter(helper, "home_buyer", PlayerRank.E, 1);
        helper.assertTrue(key(Homes.buy(p)).equals("level"), "the Studio asks for its level first");
        SystemAPI.setLevel(p, HomeConfig.level(HomeTier.STUDIO));
        helper.assertTrue(key(Homes.buy(p)).equals("gold"), "then its price");
        Economy.set(p, HomeConfig.gold(HomeTier.STUDIO) + 5);
        Homes.Outcome bought = Homes.buy(p);
        helper.assertTrue(bought.ok() && key(bought).equals("bought"), "bought: " + key(bought));
        helper.assertTrue(record(p).tier() == HomeTier.STUDIO && Economy.gold(p) == 5, "the Studio is theirs, paid for: " + Economy.gold(p));
        helper.assertTrue(ChronicleModule.data(p).hasTitle(HomeJournal.HOMEOWNER), "A Place to Return");
        SystemAPI.setLevel(p, HomeConfig.level(HomeTier.FAMILY));
        Economy.set(p, HomeConfig.gold(HomeTier.FAMILY));
        helper.assertTrue(key(Homes.buy(p)).equals("rank"), "the Family Home asks for rank " + HomeConfig.rank(HomeTier.FAMILY).name());
        SystemAPI.setRank(p, HomeConfig.rank(HomeTier.FAMILY));
        Homes.Outcome grew = Homes.buy(p);
        helper.assertTrue(grew.ok() && key(grew).equals("upgraded") && record(p).tier() == HomeTier.FAMILY && Economy.gold(p) == 0, "upgraded: " + key(grew));
        helper.assertTrue(!ChronicleModule.data(p).hasTitle(HomeJournal.SKYLINE), "the Penthouse's title waits for it");
        HomeAdmin.give(p, HomeTier.PENTHOUSE);
        helper.assertTrue(record(p).tier() == HomeTier.PENTHOUSE && ChronicleModule.data(p).hasTitle(HomeJournal.SKYLINE), "Skyline Sovereign");
        helper.assertTrue(key(Homes.buy(p)).equals("top"), "nothing left to buy");
        helper.assertTrue(Titles.byKey(HomeJournal.HOMEOWNER) != null && Titles.byKey(HomeJournal.SKYLINE) != null, "both titles are in the chronicle");
        HomeAdmin.give(p, HomeTier.NONE);
        helper.assertTrue(!record(p).owns(), "and an operator can take it away");
        helper.succeed();
    }

    // ------------------------------------------------------------------ going home

    /**
     * Going home: the hunter stands still a moment and is in the foyer of their own home; leaving takes them back to the
     * very spot they left; then they wait before they can go again, out there too, until the wait is over.
     */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void goingHomeAndBackToTheSameSpot(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "home_goer", PlayerRank.E, 10);
        helper.assertTrue(key(Homes.goHome(p)).equals("none"), "no home yet");
        HomeAdmin.give(p, HomeTier.STUDIO);
        Vec3 start = p.position();
        Homes.Outcome going = Homes.goHome(p);
        helper.assertTrue(going.ok() && key(going).equals("going") && Homes.channeling(p), "they gather: " + key(going));
        helper.assertTrue(key(Homes.goHome(p)).equals("already"), "once");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(Homes.inOwnHome(p), "home after standing still"))
                .thenExecute(() -> {
                    HomeWorld.Layout l = layout(p);
                    helper.assertTrue(p.position().distanceTo(l.arrival()) < 1.5, "in the foyer: " + p.position());
                    helper.assertTrue(!Homes.channeling(p) && HomeWorld.plotAt(p.level(), p.blockPosition()) == l.plot(), "on their own plot");
                    helper.assertTrue(p.level().getBlockState(l.button()).is(Blocks.POLISHED_BLACKSTONE_BUTTON), "the lift's button is there");
                    helper.assertTrue(key(Homes.goHome(p)).equals("wait"), "they wait before going again");
                    Homes.Outcome left = Homes.leave(p);
                    helper.assertTrue(left.ok() && key(left).equals("left"), "they leave: " + key(left));
                    helper.assertTrue(Math.hypot(p.getX() - start.x, p.getZ() - start.z) < 0.6 && !Homes.inAnyHome(p), "back where they stood: " + p.position());
                    helper.assertTrue(key(Homes.leave(p)).equals("not_home"), "and nowhere to leave now");
                    helper.assertTrue(key(Homes.goHome(p)).equals("wait"), "the wait holds out here too");
                    HomeAdmin.ready(p);
                    helper.assertTrue(Homes.goHome(p).ok(), "until it is over");
                    Homes.cancel(p, null);
                })
                .thenSucceed();
    }

    /** A step away or a blow breaks the gathering, and the hunter stays where they are. */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void aStepOrABlowBreaksTheGathering(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "home_restless", PlayerRank.E, 10);
        HomeAdmin.give(p, HomeTier.STUDIO);
        Vec3 start = p.position();
        helper.assertTrue(Homes.goHome(p).ok(), "they gather");
        p.moveTo(start.x + 2, start.y, start.z);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!Homes.channeling(p), "a step breaks it"))
                .thenExecute(() -> {
                    helper.assertTrue(!Homes.inAnyHome(p), "and they stay");
                    p.moveTo(start.x, start.y, start.z);
                    helper.assertTrue(Homes.goHome(p).ok(), "they gather again");
                    p.setHealth(p.getHealth() - 2);
                })
                .thenWaitUntil(() -> helper.assertTrue(!Homes.channeling(p), "a blow breaks it"))
                .thenExecute(() -> helper.assertTrue(!Homes.inAnyHome(p), "and they stay"))
                .thenSucceed();
    }

    /**
     * A party member visits: not before they are in the same party; then into the host's home, where they touch nothing;
     * once they leave the party, the next look sends them back out to where they came from.
     */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void aPartyMemberVisits(GameTestHelper helper) {
        ServerPlayer host = hunter(helper, "home_host", PlayerRank.E, 10);
        ServerPlayer guest = hunter(helper, "home_guest", PlayerRank.E, 10);
        guest.moveTo(guest.getX() + 2, guest.getY(), guest.getZ());
        Vec3 start = guest.position();
        helper.assertTrue(key(Homes.visit(guest, host.getUUID())).equals("not_party"), "only a party member visits");
        Parties.invite(host, guest);
        Parties.accept(guest, host.getUUID());
        helper.assertTrue(key(Homes.visit(guest, host.getUUID())).equals("host_none"), "and only a home there is");
        HomeAdmin.give(host, HomeTier.STUDIO);
        helper.assertTrue(Homes.visit(guest, host.getUUID()).ok(), "they gather");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(Homes.inAnyHome(guest), "in the host's home"))
                .thenExecute(() -> {
                    HomeWorld.Layout l = layout(host);
                    ServerLevel level = (ServerLevel) guest.level();
                    helper.assertTrue(HomeWorld.plotAt(level, guest.blockPosition()) == l.plot() && host.getUUID().equals(record(guest).visiting()),
                            "a guest of theirs");
                    helper.assertTrue(!Homes.inOwnHome(guest) && Homes.hostOf(guest).getString().equals("home_host"),
                            "at home_host's: " + Homes.hostOf(guest).getString());
                    BlockPos inside = l.at(3, 1, -3);
                    helper.assertTrue(!broken(level, inside, guest), "a guest builds and breaks nothing");
                    helper.assertTrue(Homes.mayEdit(host, level, inside), "the host may");
                    CanPlayerSleepEvent sleep = new CanPlayerSleepEvent(guest, l.beds().get(0), null);
                    NeoForge.EVENT_BUS.post(sleep);
                    helper.assertTrue(sleep.getProblem() == Player.BedSleepingProblem.OTHER_PROBLEM, "nor sleeps in the host's bed");
                    Parties.leave(guest);
                    HomeTestAccess.tick(guest);
                    helper.assertTrue(!Homes.inAnyHome(guest) && Math.hypot(guest.getX() - start.x, guest.getZ() - start.z) < 0.6,
                            "out of the party, out of the home: " + guest.position());
                    helper.assertTrue(record(guest).visiting() == null, "the visit is over");
                    Parties.disband(host);
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------ keeping it

    /**
     * The home holds: the owner arranges the rooms they own but not their walls, the lift or a wing they don't own yet;
     * a stranger changes nothing; no blast takes a block of it; an operator in creative may do anything.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void theHomeHolds(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "home_keeper", PlayerRank.E, 10);
        ServerPlayer stranger = hunter(helper, "home_stranger", PlayerRank.E, 10);
        HomeAdmin.give(p, HomeTier.STUDIO);
        helper.assertTrue(HomeAdmin.go(p), "home");
        HomeWorld.Layout l = layout(p);
        ServerLevel level = (ServerLevel) p.level();
        BlockPos inside = l.at(3, 1, -3), wall = l.at(0, 3, -10), west = l.at(-15, 1, 0);
        helper.assertTrue(broken(level, inside, p), "the owner arranges their rooms");
        helper.assertTrue(!broken(level, wall, p), "but not the walls");
        helper.assertTrue(!broken(level, l.button(), p), "nor the lift");
        helper.assertTrue(!broken(level, west, p), "nor a wing that isn't theirs yet");
        helper.assertTrue(!broken(level, inside, stranger), "a stranger changes nothing");
        Explosion blast = new Explosion(level, null, inside.getX() + 0.5, inside.getY() + 0.5, inside.getZ() + 0.5, 4f, false, Explosion.BlockInteraction.DESTROY);
        blast.getToBlow().add(inside);
        blast.getToBlow().add(wall);
        NeoForge.EVENT_BUS.post(new ExplosionEvent.Detonate(level, blast, new ArrayList<Entity>()));
        helper.assertTrue(blast.getToBlow().isEmpty(), "a blast takes nothing of it");
        p.setGameMode(GameType.CREATIVE);
        helper.assertTrue(broken(level, wall, p), "an operator in creative may");
        p.setGameMode(GameType.SURVIVAL);
        Homes.leave(p);
        helper.succeed();
    }

    /** Anyone who falls out of a home is put back at its door. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void aFallIsCaught(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "home_faller", PlayerRank.E, 10);
        HomeAdmin.give(p, HomeTier.STUDIO);
        helper.assertTrue(HomeAdmin.go(p), "home");
        HomeWorld.Layout l = layout(p);
        p.moveTo(l.arrival().x + 6, l.origin().getY() - 30, l.arrival().z - 20);
        HomeTestAccess.tick(p);
        helper.assertTrue(p.position().distanceTo(l.arrival()) < 1.5 && Homes.inOwnHome(p), "back at the door: " + p.position());
        Homes.leave(p);
        helper.succeed();
    }

    // ------------------------------------------------------------------ the rooms

    /**
     * A night in their own bed: health, mana and fatigue back and Well Rested, but Well Rested only once a day; nobody
     * else sleeps in it.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void aNightInTheirOwnBed(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "home_sleeper", PlayerRank.E, 10);
        ServerPlayer stranger = hunter(helper, "home_tired", PlayerRank.E, 10);
        HomeAdmin.give(p, HomeTier.STUDIO);
        helper.assertTrue(HomeAdmin.go(p), "home");
        HomeWorld.Layout l = layout(p);
        p.setHealth(p.getMaxHealth() / 3);
        HomeTestAccess.rest(p);
        helper.assertTrue(p.getHealth() >= p.getMaxHealth() - 0.01f, "health back: " + p.getHealth());
        helper.assertTrue(p.hasEffect(HomeModule.WELL_RESTED) && record(p).restDay() == QuestEngine.day(p), "Well Rested");
        p.removeEffect(HomeModule.WELL_RESTED);
        HomeTestAccess.rest(p);
        helper.assertTrue(!p.hasEffect(HomeModule.WELL_RESTED), "once a day");
        HomeAdmin.rest(p);
        helper.assertTrue(p.hasEffect(HomeModule.WELL_RESTED), "unless an operator says so");
        CanPlayerSleepEvent sleep = new CanPlayerSleepEvent(stranger, l.beds().get(0), null);
        NeoForge.EVENT_BUS.post(sleep);
        helper.assertTrue(sleep.getProblem() == Player.BedSleepingProblem.OTHER_PROBLEM, "nobody else sleeps in it");
        CanPlayerSleepEvent own = new CanPlayerSleepEvent(p, l.beds().get(0), null);
        NeoForge.EVENT_BUS.post(own);
        helper.assertTrue(own.getProblem() == null, "its owner does: " + own.getProblem());
        Homes.leave(p);
        helper.succeed();
    }

    /**
     * A training dummy takes every blow and never falls (not even to a blow bigger than its health); armour takes its
     * share on the armoured one; and a bout long enough is the hunter's best.
     */
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void theDummiesNeverFall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer p = hunter(helper, "home_trainee", PlayerRank.E, 10);
        TrainingDummy plain = dummy(helper, new BlockPos(3, 2, 3), false);
        TrainingDummy armoured = dummy(helper, new BlockPos(5, 2, 3), true);
        plain.hurt(level.damageSources().playerAttack(p), TrainingDummy.HEALTH * 3);
        helper.assertTrue(plain.isAlive() && plain.getHealth() > 0 && plain.hits() == 1, "a blow bigger than its health leaves it standing");
        // the same blow on both (a creature's, so no hunter's crit lands on one of them only)
        DamageSource blow = level.damageSources().source(DamageTypes.MOB_ATTACK);
        plain.invulnerableTime = 0;
        plain.hurt(blow, 40f);
        armoured.hurt(blow, 40f);
        helper.assertTrue(plain.last() > 0 && armoured.last() > 0 && armoured.last() < plain.last() * 0.9f,
                "armour takes its share: " + armoured.last() + " of " + plain.last());
        helper.assertTrue(plain.total() > TrainingDummy.HEALTH && plain.dps() > 0, "the bout counts: " + plain.total());
        int[] blows = {0};
        helper.startSequence()
                .thenIdle(TrainingDummy.BOUT_GAP + 5)
                .thenExecute(() -> {
                    helper.assertTrue(!plain.fighting(), "the bout is over");
                    helper.assertTrue(record(p).bestDps() == 0, "too short a bout is nobody's best");
                })
                .thenWaitUntil(() -> {
                    // a blow every ten ticks for eight seconds: a real bout
                    if (blows[0] <= 16 && level.getGameTime() % 10 == 0) {
                        plain.invulnerableTime = 0;
                        plain.hurt(level.damageSources().playerAttack(p), 20f);
                        blows[0]++;
                    }
                    helper.assertTrue(blows[0] > 16, "still striking");
                })
                .thenWaitUntil(() -> helper.assertTrue(record(p).bestDps() > 0, "the bout over, it is their best"))
                .thenExecute(() -> {
                    // the best is the bout's damage over its length: seventeen blows ten ticks apart, 160 ticks
                    float best = record(p).bestDps(), total = plain.total();
                    helper.assertTrue(best >= total * 20f / 175f && best <= total * 20f / 145f, "the bout's damage a second: " + best + " of " + total);
                    helper.assertTrue(plain.isAlive() && armoured.isAlive(), "and the dummies still stand");
                    plain.discard();
                    armoured.discard();
                })
                .thenSucceed();
    }

    private static TrainingDummy dummy(GameTestHelper helper, BlockPos at, boolean armoured) {
        TrainingDummy d = HomeModule.DUMMY.get().create(helper.getLevel());
        if (d == null) throw new IllegalStateException("no dummy");
        BlockPos pos = helper.absolutePos(at);
        d.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        d.configure(-1, 0, armoured);
        helper.getLevel().addFreshEntity(d);
        return d;
    }

    /** The Training Hall has its three dummies (one armoured) on their stands, and no more however often it is looked over. */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void theHallHasItsDummies(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "home_hall", PlayerRank.E, 10);
        HomeAdmin.give(p, HomeTier.HALL);
        HomeWorld.Layout l = layout(p);
        ServerLevel level = HomeWorld.level(p.server);
        List<ChunkPos> chunks = force(level, l, true, null);
        helper.assertTrue(TestChunks.ticking(level, chunks), "the home's ground is loaded");
        helper.assertTrue(HomeAdmin.go(p), "home");
        AABB hall = new AABB(l.at(9, 0, -10).getCenter(), l.at(26, 10, 10).getCenter());
        Runnable stands = () -> {
            List<TrainingDummy> all = level.getEntitiesOfClass(TrainingDummy.class, hall);
            helper.assertTrue(all.size() == HomeWorld.DUMMIES, "the dummies stand in the hall: " + all.size());
            int armoured = 0;
            for (TrainingDummy d : all) {
                if (d.armoured()) armoured++;
                helper.assertTrue(d.plot() == l.plot() && d.position().distanceTo(l.dummy(d.slot())) < 0.3, "dummy " + d.slot() + " on its stand");
            }
            helper.assertTrue(armoured == 1, "one of them armoured: " + armoured);
        };
        helper.startSequence()
                // the creatures of ground that has just come to tick are read in from disk by other threads a turn or
                // more later; until then no dummy is put up (it would stand beside the one saved there)
                .thenWaitUntil(() -> helper.assertTrue(chunks.stream().allMatch(c -> level.areEntitiesLoaded(c.toLong())), "the hall's creatures are loaded"))
                // the hunter's next look over the home puts them up
                .thenExecute(() -> HomeTestAccess.tick(p))
                .thenExecute(stands)
                .thenExecute(() -> Homes.ensureDummies(level, l))
                .thenExecute(stands)
                // they look each other over every two seconds: none of the three goes
                .thenIdle(45)
                .thenExecute(stands)
                .thenExecute(() -> {
                    Homes.leave(p);
                    force(level, l, false, chunks);
                })
                .thenSucceed();
    }

    /** The Penthouse's trophy hall: a pedestal for each great threat, each showing how many times its owner slew it. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void theTrophyHallKeepsTheirKills(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "home_slayer", PlayerRank.E, 10);
        for (var k : HomeWorld.TROPHIES) p.getData(WorldBossModule.RECORD.get()).setSlain(k, 0);
        HomeAdmin.give(p, HomeTier.PENTHOUSE);
        helper.assertTrue(HomeAdmin.go(p), "home");
        HomeWorld.Layout l = layout(p);
        ServerLevel level = (ServerLevel) p.level();
        for (int i = 0; i < HomeWorld.TROPHIES.length; i++) {
            helper.assertTrue(level.getBlockEntity(l.pedestal(i)) instanceof TrophyPedestalBlockEntity be && be.kind() == HomeWorld.TROPHIES[i] && be.slain() == 0,
                    "pedestal " + i + " waits for " + HomeWorld.TROPHIES[i].id);
        }
        HomeAdmin.trophies(p, 2);
        for (int i = 0; i < HomeWorld.TROPHIES.length; i++) {
            helper.assertTrue(level.getBlockEntity(l.pedestal(i)) instanceof TrophyPedestalBlockEntity be && be.slain() == 2,
                    "pedestal " + i + " shows its kills");
        }
        HomeAdmin.trophies(p, 0);
        Homes.leave(p);
        helper.succeed();
    }

    /**
     * Their mother, awake, moves into her room once the Family Home is theirs and they come home; if the home is taken
     * away she leaves it (and finds a bed in her village again).
     */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void theirMotherMovesIn(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "home_child", PlayerRank.E, 30);
        Kin.evaluatedAt(p, helper.absolutePos(new BlockPos(3, 2, 3)));
        LongSleep.step(p, LongSleep.AWAKE);
        HomeAdmin.give(p, HomeTier.STUDIO);
        helper.assertTrue(HomeAdmin.go(p), "home");
        helper.assertTrue(!record(p).motherHere(), "no room for her in the Studio");
        Homes.leave(p);
        HomeAdmin.give(p, HomeTier.FAMILY);
        HomeAdmin.ready(p);
        HomeWorld.Layout l = layout(p);
        ServerLevel level = HomeWorld.level(p.server);
        List<ChunkPos> chunks = force(level, l, true, null);
        helper.assertTrue(TestChunks.ticking(level, chunks), "the home's ground is loaded");
        helper.assertTrue(HomeAdmin.go(p), "home again");
        KinRecord kin = Kin.record(p);
        helper.assertTrue(record(p).motherHere() && l.motherBed().equals(kin.bed()), "she moves into her room: " + kin.bed());
        KinEntity k = Kin.find(p);
        helper.assertTrue(k != null && k.blockPosition().distManhattan(l.motherBed()) <= 3, "and there she is");
        HomeAdmin.give(p, HomeTier.NONE);
        helper.assertTrue(!record(p).motherHere() && Kin.record(p).bed() == null && Kin.find(p) == null, "the home taken away, she leaves it");
        helper.assertTrue(!Homes.inAnyHome(p), "and so do they");
        force(level, l, false, chunks);
        helper.succeed();
    }

    /** The game-test server has no residence world: its homes float over the overworld, far down a lane of their own. */
    @GameTest(template = ARENA)
    public static void testHomesFloatOverTheOverworld(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(HomeWorld.level(server) == server.overworld(), "the overworld stands in for the residence");
        helper.assertTrue(HomeWorld.plotAt(helper.getLevel(), helper.absolutePos(BlockPos.ZERO)) < 0, "and the arena is in no plot");
        helper.succeed();
    }
}
