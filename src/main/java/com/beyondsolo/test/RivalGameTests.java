package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.association.Association;
import com.beyondsolo.association.AssociationData;
import com.beyondsolo.association.AssociationModule;
import com.beyondsolo.chronicle.ChronicleModule;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.guild.GuildHunterEntity;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.redgate.RedGates;
import com.beyondsolo.rival.Hire;
import com.beyondsolo.rival.Hires;
import com.beyondsolo.rival.RivalConfig;
import com.beyondsolo.rival.RivalData;
import com.beyondsolo.rival.RivalGuild;
import com.beyondsolo.rival.RivalHunterEntity;
import com.beyondsolo.rival.RivalModule;
import com.beyondsolo.rival.RivalParties;
import com.beyondsolo.rival.RivalParty;
import com.beyondsolo.rival.RivalRecord;
import com.beyondsolo.rival.RivalTestAccess;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.util.OnlinePlayers;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The world's other hunters ({@link RivalParties}) and the hands for hire ({@link Hires}). Run with
 * {@code ./gradlew runGameTestServer}. The gates here are registered with no block ({@link GateData#create}) and closed
 * again at the end; the parties are driven by hand ({@link RivalTestAccess}) and kept out of sight, so their hunters
 * never step into another test's plot. A hunter "in" a gate stands at its cave's origin (on the test server the gate
 * level is the overworld).
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RivalGameTests {
    private static final String ARENA = "evaluation_zone";
    private static final BlockPos CRYSTAL = new BlockPos(3, 2, 3);

    /** A hunter in the plot at that rank (null: not evaluated), with no gold and clean records. */
    private static ServerPlayer hunter(GameTestHelper helper, String name, @Nullable PlayerRank rank) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        Economy.reset(p);
        p.removeData(AssociationModule.RECORD);
        p.removeData(RivalModule.RECORD);
        p.setGameMode(GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 2));
        p.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        if (rank != null) SystemAPI.awaken(p, rank, PlayerClass.WARRIOR);
        QuestTestAccess.markStarted(p);
        OnlinePlayers.addTestPlayer(helper, p);
        return p;
    }

    /** An open gate of that rank standing in the plot (registered, with no block to walk into). */
    private static GateInstance gate(GameTestHelper helper, PlayerRank rank, BlockPos at) {
        MinecraftServer server = helper.getLevel().getServer();
        // a gate that will turn red is no party's to go for (RivalParties.eligible): the tests' gates never will
        long seed = helper.getLevel().random.nextLong();
        while (RedGates.latent(server, new GateInstance(UUID.randomUUID(), rank, seed, 0, Level.OVERWORLD, BlockPos.ZERO))) seed++;
        return GateData.get(server).create(UUID.randomUUID(), rank, seed, Level.OVERWORLD, helper.absolutePos(at));
    }

    /** A party sent for the gate, out of sight. */
    private static RivalParty send(GameTestHelper helper, GateInstance g, @Nullable RivalGuild guild) {
        RivalParty p = RivalParties.dispatch(helper.getLevel().getServer(), g, guild, null, helper.getLevel().random);
        if (p == null) throw new IllegalStateException("no party was sent for the gate");
        RivalTestAccess.unseen(p);
        return p;
    }

    /** The hunter steps into the gate's cave. */
    private static void into(ServerPlayer p, GateInstance g) {
        ServerLevel dungeon = GateService.dungeon(p.server);
        BlockPos o = g.origin();
        if (p.level() == dungeon) p.moveTo(o.getX() + 0.5, o.getY() + 1, o.getZ() + 0.5, 0f, 0f);
        else p.teleportTo(dungeon, o.getX() + 0.5, o.getY() + 1, o.getZ() + 0.5, 0f, 0f);
    }

    /** The test's parties forgotten, its gates closed and gone. */
    private static void tidy(GameTestHelper helper, List<RivalParty> parties, GateInstance... gates) {
        MinecraftServer server = helper.getLevel().getServer();
        for (RivalParty p : parties) RivalTestAccess.drop(server, p);
        for (GateInstance g : gates) {
            g.setState(GateInstance.State.CLOSED);
            GateData.get(server).remove(g.id());
        }
    }

    /** What the hunter's HUD is told of the party, or null. */
    @Nullable
    private static CompoundTag shown(ServerPlayer p, RivalParty party) {
        MinecraftServer server = p.server;
        ListTag list = RivalParties.view(server, RivalData.get(server), p, RivalParties.now(server)).getList("parties", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            if (list.getCompound(i).getUUID("id").equals(party.id)) return list.getCompound(i);
        }
        return null;
    }

    private static int run(ServerPlayer p, String command) throws CommandSyntaxException {
        return p.server.getCommands().getDispatcher().execute(command, p.createCommandSourceStack().withPermission(4).withSuppressedOutput());
    }

    // ================================================================ parties and their gates

    /** A party sets out for an open gate; first in, the gate is theirs: the association keeps off it and hunters are turned away. */
    @GameTest(template = ARENA)
    public static void aPartyThatGoesInFirstHoldsTheGate(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer p = hunter(helper, "Bystander", PlayerRank.C);
        GateInstance g = gate(helper, PlayerRank.D, new BlockPos(5, 2, 5));
        helper.assertTrue(RivalParties.eligible(server, g), "an open gate nobody has claimed is fair game");
        RivalParty party = send(helper, g, RivalGuild.SUNFALL);
        helper.assertTrue(party.state() == RivalParty.State.EN_ROUTE && party.guild == RivalGuild.SUNFALL && party.rank == PlayerRank.D,
                "a Sunfall party of the gate's rank sets out");
        helper.assertTrue(party.members.size() >= 3 && party.members.size() <= 5, "three to five of them: " + party.members.size());
        helper.assertTrue(party.arriveAt() > RivalParties.now(server), "they're on their way, not there yet");
        helper.assertTrue(party.watchers().contains(p.getUUID()), "the hunter near the gate hears of it");
        CompoundTag hud = shown(p, party);
        helper.assertTrue(hud != null && hud.getInt("state") == RivalParty.State.EN_ROUTE.ordinal() && hud.getLong("left") > 0,
                "the hunter's HUD counts down to their arrival");
        helper.assertFalse(RivalParties.eligible(server, g), "a gate a party is after isn't another party's to go for");
        helper.assertTrue(RivalParties.dispatch(server, g, null, null, helper.getLevel().random) == null, "no second party is sent for it");

        RivalParties.arriveNow(server, party);
        helper.assertTrue(party.state() == RivalParty.State.GATHERING, "at the gate they gather");
        RivalParties.enterNow(server, party);
        helper.assertTrue(party.state() == RivalParty.State.INSIDE && party.holds() && !party.racing(), "first in, the gate is theirs");
        helper.assertTrue(RivalParties.holds(server, g.id()), "they hold it");
        helper.assertFalse(Association.contractable(server, g), "the association posts no contract on a gate a party holds");
        helper.assertTrue(RivalParties.refusal(p, g) != null, "a hunter walking up to it is turned away");
        p.setShiftKeyDown(true);
        helper.assertTrue(RivalParties.refusal(p, g) == null, "crouching, a hunter may force their way in");
        p.setShiftKeyDown(false);

        RivalParties.finishNow(server, party, true);
        helper.assertTrue(party.outcome() == RivalParty.Outcome.CLEARED && g.bossDefeated() && g.state() == GateInstance.State.CLEARED,
                "they clear it: its boss is gone and the gate with it");
        helper.assertFalse(RivalParties.holds(server, g.id()) || RivalParties.eligible(server, g), "a cleared gate is nobody's to go for");
        tidy(helper, List.of(party), g);
        helper.succeed();
    }

    /** Beaten to their gate, a party turns back, unless it's bold enough to follow; a gate's boss falling first sends it home too. */
    @GameTest(template = ARENA)
    public static void aPartyBeatenToItsGateStandsDownUnlessItIsBold(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer p = hunter(helper, "FirstIn", PlayerRank.C);
        GateInstance careful = gate(helper, PlayerRank.D, new BlockPos(5, 2, 5));
        GateInstance contested = gate(helper, PlayerRank.D, new BlockPos(1, 2, 5));
        GateInstance done = gate(helper, PlayerRank.D, new BlockPos(5, 2, 1));

        RivalParty bastion = send(helper, careful, RivalGuild.IRON_BASTION);
        RivalTestAccess.arrived(p, careful, 1f);
        helper.assertTrue(bastion.state() == RivalParty.State.DONE && bastion.outcome() == RivalParty.Outcome.STOOD_DOWN,
                "a hunter got in first: they stand down (" + bastion.outcome() + ")");
        helper.assertTrue(RivalParties.partyFor(server, careful.id()) == null, "the gate is no party's any more");

        RivalParty lantern = send(helper, contested, RivalGuild.RED_LANTERN);
        RivalTestAccess.arrived(p, contested, 0f);
        helper.assertTrue(lantern.state() == RivalParty.State.EN_ROUTE && lantern.racing(), "the bold ones come on anyway");
        RivalParties.enterNow(server, lantern);
        helper.assertTrue(lantern.state() == RivalParty.State.INSIDE && lantern.racing() && !lantern.holds(),
                "in after a hunter, it's a race, not a claim");
        helper.assertTrue(RivalParties.refusal(p, contested) == null && !RivalParties.holds(server, contested.id()), "nobody is turned away from a race");

        RivalParty late = send(helper, done, null);
        helper.assertFalse(RivalParties.bossFell(helper.getLevel(), done, helper.getLevel().damageSources().generic()),
                "a boss that fell before they got there isn't theirs");
        helper.assertTrue(late.outcome() == RivalParty.Outcome.STOOD_DOWN, "too late, they turn back: " + late.outcome());
        tidy(helper, List.of(bastion, lantern, late), careful, contested, done);
        helper.succeed();
    }

    /** A gate the association gives a hunter (a permit) is left alone: a party gathering at it moves on. */
    @GameTest(template = ARENA)
    public static void aPartyLeavesAGateTheAssociationGaveAway(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer p = hunter(helper, "PermitHolder", PlayerRank.C);
        GateInstance g = gate(helper, PlayerRank.D, new BlockPos(5, 2, 5));
        RivalParty party = send(helper, g, RivalGuild.AZURE_TIDE);
        RivalParties.arriveNow(server, party);
        AssociationData.get(server).put(g.id(), new AssociationData.Claim(AssociationData.Kind.PERMIT, p.getUUID(), p.getGameProfile().getName(),
                g.rank(), RivalParties.now(server)));
        RivalTestAccess.advance(server, party);
        helper.assertTrue(party.outcome() == RivalParty.Outcome.STOOD_DOWN, "the association gave it away: they move on (" + party.outcome() + ")");
        helper.assertFalse(RivalParties.eligible(server, g), "a gate held for a hunter is no party's to go for");
        AssociationData.get(server).remove(g.id());
        tidy(helper, List.of(party), g);
        helper.succeed();
    }

    /** With nobody watching, a party's clock runs: it clears its gate, or is beaten back and leaves it open for anyone. */
    @GameTest(template = ARENA)
    public static void aPartyAloneClearsItsGateOrIsBeatenBack(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        hunter(helper, "FarAway", PlayerRank.C);
        GateInstance strong = gate(helper, PlayerRank.E, new BlockPos(5, 2, 5));
        GateInstance weak = gate(helper, PlayerRank.E, new BlockPos(1, 2, 5));

        RivalParty sunfall = send(helper, strong, RivalGuild.SUNFALL);
        RivalParties.enterNow(server, sunfall);
        helper.assertTrue(sunfall.clearTicks() >= 20 * 30, "a clear takes them a while: " + sunfall.clearTicks() + " ticks");
        RivalTestAccess.progress(sunfall, 0.999f, true);
        RivalTestAccess.advance(server, sunfall);
        helper.assertTrue(sunfall.outcome() == RivalParty.Outcome.CLEARED && strong.bossDefeated() && strong.state() == GateInstance.State.CLEARED,
                "at the end of their clock they have cleared it (" + sunfall.outcome() + ")");

        RivalParty band = send(helper, weak, RivalGuild.INDEPENDENT);
        RivalParties.enterNow(server, band);
        RivalTestAccess.progress(band, 0.5499f, false);
        RivalTestAccess.advance(server, band);
        helper.assertTrue(band.outcome() == RivalParty.Outcome.RETREATED, "halfway through, they are beaten back (" + band.outcome() + ")");
        helper.assertTrue(weak.state() == GateInstance.State.OPEN && !weak.bossDefeated() && RivalParties.eligible(server, weak),
                "the gate is open to anyone again");
        tidy(helper, List.of(sunfall, band), strong, weak);
        helper.succeed();
    }

    /** In a race, whoever dealt the boss most takes it; three races won bring their title. */
    @GameTest(template = ARENA)
    public static void theBossGoesToWhoeverDealtItMost(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        ServerPlayer p = hunter(helper, "Racer", PlayerRank.C);
        float[][] tallies = {{30, 70}, {80, 20}, {10, 90}, {45, 55}};
        List<RivalParty> parties = new ArrayList<>();
        List<GateInstance> gates = new ArrayList<>();
        for (int i = 0; i < tallies.length; i++) {
            GateInstance g = gate(helper, PlayerRank.C, new BlockPos(1 + i, 2, 5));
            gates.add(g);
            into(p, g);
            RivalParty party = send(helper, g, RivalGuild.SUNFALL);
            parties.add(party);
            RivalParties.enterNow(server, party);
            helper.assertTrue(party.racing() && !party.holds(), "in after the hunter, it's a race");
            RivalTestAccess.tally(party, tallies[i][0], tallies[i][1]);
            CompoundTag hud = shown(p, party);
            helper.assertTrue(hud != null && hud.getBoolean("here") && Math.abs(hud.getFloat("share") - tallies[i][0] / 100f) < 1e-3,
                    "the HUD shows the race in the hunter's gate, and their share of it");
            boolean expected = tallies[i][0] > tallies[i][1];
            boolean theirs = RivalParties.bossFell(level, g, level.damageSources().generic());
            helper.assertTrue(theirs == expected, "race " + i + ": the boss goes to " + (expected ? "the party" : "the hunter"));
            helper.assertTrue(party.outcome() == (expected ? RivalParty.Outcome.CLEARED : RivalParty.Outcome.BEATEN), "race " + i + ": " + party.outcome());
        }
        RivalRecord r = Hires.record(p);
        helper.assertTrue(r.raceWins() == 3 && r.raceLosses() == 1, "three won, one lost: " + r.raceWins() + "/" + r.raceLosses());
        helper.assertTrue(ChronicleModule.data(p).hasTitle("title.beyondsolo.gate_racer"), "three races won bring the Gate Racer title");
        tidy(helper, parties, gates.toArray(GateInstance[]::new));
        helper.succeed();
    }

    /** Forcing your way into a gate a party holds costs merit once; take its boss from them anyway and it's yours. */
    @GameTest(template = ARENA)
    public static void forcingYourWayIntoAHeldGateCostsMerit(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        ServerPlayer p = hunter(helper, "ClaimJumper", PlayerRank.B);
        Association.setMerit(p, 500);
        GateInstance g = gate(helper, PlayerRank.B, new BlockPos(5, 2, 5));
        RivalParty party = send(helper, g, RivalGuild.AZURE_TIDE);
        RivalParties.enterNow(server, party);
        helper.assertTrue(party.holds(), "first in, they hold it");
        p.setShiftKeyDown(true);
        helper.assertTrue(RivalParties.refusal(p, g) == null, "crouching, the hunter forces their way in");
        p.setShiftKeyDown(false);
        into(p, g);
        RivalParties.arrived(p, g);
        long cost = RivalConfig.trespassMerit(PlayerRank.B);
        helper.assertTrue(cost > 0 && Association.record(p).merit() == 500 - cost, "the association docks their merit: " + Association.record(p).merit());
        helper.assertTrue(Hires.record(p).trespasses() == 1 && party.racing(), "a trespass on their record, and the race is on");
        CompoundTag hud = shown(p, party);
        helper.assertTrue(hud != null && hud.getBoolean("trespasser"), "their HUD marks them a trespasser");
        RivalParties.arrived(p, g);
        helper.assertTrue(Association.record(p).merit() == 500 - cost && Hires.record(p).trespasses() == 1, "it costs them once");
        helper.assertTrue(RivalParties.refusal(p, g) == null, "once in, they may come and go");

        RivalTestAccess.tally(party, 40, 60);
        helper.assertFalse(RivalParties.bossFell(level, g, level.damageSources().generic()), "the hunter dealt the boss most");
        helper.assertTrue(party.outcome() == RivalParty.Outcome.BEATEN, "the party was beaten to it: " + party.outcome());
        helper.assertTrue(ChronicleModule.data(p).hasTitle("title.beyondsolo.claim_jumper"), "a boss taken from a party that held its gate: Claim Jumper");
        tidy(helper, List.of(party), g);
        helper.succeed();
    }

    /** A party gathering where a hunter can see it stands in front of its gate, every one of them, in the guild's colour. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void aPartyShowsItselfToAHunterNearby(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        hunter(helper, "Onlooker", PlayerRank.C);
        GateInstance g = gate(helper, PlayerRank.D, new BlockPos(3, 2, 6));
        RivalParty party = RivalParties.dispatch(server, g, RivalGuild.RED_LANTERN, null, level.random);
        helper.assertTrue(party != null, "a party is sent");
        RivalParties.arriveNow(server, party);
        AABB around = new AABB(g.gatePos()).inflate(16, 8, 16);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(level.getEntitiesOfClass(RivalHunterEntity.class, around, e -> party.id.equals(e.party())).size()
                        == party.members.size(), "all of them are there"))
                .thenExecute(() -> {
                    List<RivalHunterEntity> there = level.getEntitiesOfClass(RivalHunterEntity.class, around, e -> party.id.equals(e.party()));
                    helper.assertTrue(there.stream().filter(RivalHunterEntity::leader).count() == 1, "one of them leads");
                    helper.assertTrue(there.stream().allMatch(e -> (e.guildColor() & 0xFFFFFF) == (RivalGuild.RED_LANTERN.color & 0xFFFFFF)),
                            "each in the guild's colour");
                    helper.assertTrue(there.stream().allMatch(e -> e.distanceToSqr(Vec3.atCenterOf(g.gatePos())) < 12 * 12), "in front of the gate");
                    tidy(helper, List.of(party), g);
                    helper.assertTrue(level.getEntitiesOfClass(RivalHunterEntity.class, around, e -> party.id.equals(e.party())).isEmpty(),
                            "forgotten, they are gone");
                })
                .thenSucceed();
    }

    // ================================================================ hands for hire

    /** The office's list: two porters and some hunters of the hunter's rank, the same all day; a hired one leaves it. */
    @GameTest(template = ARENA)
    public static void theOfficeListsTheSameHandsAllDay(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "Employer", PlayerRank.C);
        Economy.set(p, 1_000_000);
        BlockPos office = helper.absolutePos(CRYSTAL);
        List<Hires.Offer> list = Hires.offers(p, office);
        helper.assertTrue(list.size() == 2 + RivalConfig.offers(), "two porters and " + RivalConfig.offers() + " hunters: " + list.size());
        helper.assertTrue(list.get(0).kind() == Hire.Kind.PORTER && list.get(0).rank() == PlayerRank.E
                && list.get(1).kind() == Hire.Kind.PORTER && list.get(1).rank() == PlayerRank.D, "an E and a D porter");
        for (Hires.Offer o : list.subList(2, list.size())) {
            helper.assertTrue(o.kind() == Hire.Kind.HUNTER && (o.rank() == PlayerRank.C || o.rank() == PlayerRank.D),
                    "a hunter of the hunter's rank or one below: " + o.rank());
            helper.assertTrue(o.fee() > 0 && o.fee() == Hires.fee(o.kind(), o.level()), "their fee: " + o.fee());
        }
        helper.assertTrue(Hires.offers(p, office).equals(list), "the same faces all day");
        helper.assertFalse(Hires.offers(p, office.offset(64, 0, 0)).get(0).id().equals(list.get(0).id()), "another office has other faces");
        Hires.Offer hunter = list.get(2);
        helper.assertTrue(Hires.hire(p, office, hunter.id()).ok(), "a hunter signs on");
        helper.assertTrue(Economy.gold(p) == 1_000_000 - hunter.fee(), "paid up front");
        helper.assertTrue(Hires.offers(p, office).stream().noneMatch(o -> o.id().equals(hunter.id())), "hired, they are off the list");
        helper.assertFalse(Hires.hire(p, office, hunter.id()).ok(), "and can't be hired twice");
        Hires.wipe(p);
        helper.succeed();
    }

    /** A porter picks up what falls near their hunter and hands it all over when let go; one porter at a time. */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void aPorterCarriesWhatFallsAndHandsItOver(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer p = hunter(helper, "PackOwner", PlayerRank.D);
        Economy.set(p, 1000);
        BlockPos office = helper.absolutePos(CRYSTAL);
        List<Hires.Offer> list = Hires.offers(p, office);
        Hires.Offer porter = list.get(0), other = list.get(1);
        helper.assertTrue(Hires.hire(p, office, porter.id()).ok(), "the porter signs on");
        helper.assertTrue(Economy.gold(p) == 1000 - porter.fee(), "for their fee: " + porter.fee());
        helper.assertFalse(Hires.hire(p, office, other.id()).ok(), "one porter at a time");
        helper.assertTrue(Economy.gold(p) == 1000 - porter.fee(), "a refused hire costs nothing");
        Hire h = Hires.record(p).porter();
        helper.assertTrue(h != null && h.pack().size() == RivalConfig.porterSlots(), "a pack of " + RivalConfig.porterSlots() + " slots");
        BlockPos drop = helper.absolutePos(new BlockPos(4, 2, 5));
        ItemEntity loot = new ItemEntity(level, drop.getX() + 0.5, drop.getY() + 0.1, drop.getZ() + 0.5, new ItemStack(Items.DIAMOND, 3));
        loot.setDeltaMovement(Vec3.ZERO);
        loot.setNoPickUpDelay();
        level.addFreshEntity(loot);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(h.carried() > 0, "the porter picks up what fell"))
                .thenExecute(() -> {
                    helper.assertTrue(!loot.isAlive(), "nothing is left on the ground");
                    int before = p.getInventory().countItem(Items.DIAMOND);
                    helper.assertTrue(Hires.dismiss(p, h.id).ok(), "let go");
                    helper.assertTrue(p.getInventory().countItem(Items.DIAMOND) - before == 3, "the pack is handed over");
                    helper.assertTrue(Hires.record(p).hires().isEmpty(), "nobody is on the job");
                })
                .thenSucceed();
    }

    /** Hunters sign on up to the limit and fight for the hunter who hired them; the job ends with a gate cleared together. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void hiredHuntersFollowTheirHunterUntilTheGateIsDone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer p = hunter(helper, "Captain", PlayerRank.C);
        Economy.set(p, 1_000_000);
        BlockPos office = helper.absolutePos(CRYSTAL);
        List<Hires.Offer> hunters = Hires.offers(p, office).stream().filter(o -> o.kind() == Hire.Kind.HUNTER).toList();
        int max = RivalConfig.maxHunters();
        helper.assertTrue(hunters.size() > max, "more hunters on the list than may be hired at once");
        long paid = 0;
        for (int i = 0; i < max; i++) {
            helper.assertTrue(Hires.hire(p, office, hunters.get(i).id()).ok(), "hunter " + (i + 1) + " signs on");
            paid += hunters.get(i).fee();
        }
        helper.assertFalse(Hires.hire(p, office, hunters.get(max).id()).ok(), "no more than " + max + " at once");
        helper.assertTrue(Economy.gold(p) == 1_000_000 - paid, "each paid their fee");
        List<GuildHunterEntity> bodies = level.getEntitiesOfClass(GuildHunterEntity.class, p.getBoundingBox().inflate(12), e -> e.cause() == Hires.CAUSE);
        helper.assertTrue(bodies.size() == max, "they step out beside the hunter: " + bodies.size());
        helper.assertTrue(bodies.stream().allMatch(e -> Hires.CAUSE.leader(e) == p), "and follow the hunter who hired them");
        GateInstance g = gate(helper, PlayerRank.C, new BlockPos(5, 2, 5));
        Hires.gateCleared(level, g, List.of(p));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(Hires.record(p).hires().isEmpty(), "with the gate behind them, the job is done"))
                .thenExecute(() -> tidy(helper, List.of(), g))
                .thenSucceed();
    }

    // ================================================================ commands

    /** /solo rival and /solo hire drive it all by hand; /solo wipe lets every hand go, the pack handed over. */
    @GameTest(template = ARENA)
    public static void soloRivalAndHireCommands(GameTestHelper helper) throws CommandSyntaxException {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer p = hunter(helper, "Operator", PlayerRank.C);
        GateInstance g = gate(helper, PlayerRank.D, new BlockPos(2, 2, 2));
        helper.assertTrue(run(p, "solo rival send iron_bastion") == 1, "a party is sent");
        RivalParty party = RivalParties.nearest(server, p.position());
        helper.assertTrue(party != null && party.guild == RivalGuild.IRON_BASTION, "an Iron Bastion party, for the nearest free gate");
        RivalTestAccess.unseen(party);
        GateInstance its = GateData.get(server).get(party.gate);
        helper.assertTrue(its != null && its.gatePos().distSqr(p.blockPosition()) < 8 * 8, "a gate right by the hunter");
        run(p, "solo rival arrive");
        helper.assertTrue(party.state() == RivalParty.State.GATHERING, "arrive: they gather at it");
        run(p, "solo rival enter");
        helper.assertTrue(party.state() == RivalParty.State.INSIDE, "enter: they go in");
        run(p, "solo rival finish retreat");
        helper.assertTrue(party.outcome() == RivalParty.Outcome.RETREATED, "finish retreat: they pull out");
        helper.assertTrue(run(p, "solo rival list") >= 1, "the list runs");

        long gold = Economy.gold(p);
        run(p, "solo hire porter d");
        Hire porter = Hires.record(p).porter();
        helper.assertTrue(porter != null && porter.rank() == PlayerRank.D, "a D-rank porter, for nothing");
        run(p, "solo hire hunter c guardian");
        helper.assertTrue(Hires.record(p).hunters() == 1 && Hires.record(p).hires().stream().anyMatch(h -> !h.porter() && h.cls == PlayerClass.GUARDIAN
                && h.rank() == PlayerRank.C), "a C-rank guardian");
        helper.assertTrue(Economy.gold(p) == gold, "hired by command, nobody was paid");
        porter.pack().set(0, new ItemStack(Items.EMERALD, 5));
        run(p, "solo wipe system");
        helper.assertTrue(Hires.record(p).hires().isEmpty(), "wiped: every hand is let go");
        helper.assertTrue(p.getInventory().countItem(Items.EMERALD) == 5, "and the porter's pack is handed over");
        tidy(helper, List.of(party), g, its);
        helper.succeed();
    }

    private RivalGameTests() {}
}
