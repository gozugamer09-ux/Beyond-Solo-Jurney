package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.association.Association;
import com.beyondsolo.association.AssociationModule;
import com.beyondsolo.chronicle.ChronicleModule;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.economy.EconomyHooks;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.news.Headline;
import com.beyondsolo.news.HunterNews;
import com.beyondsolo.news.NewsData;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.rival.RivalData;
import com.beyondsolo.rival.RivalGuild;
import com.beyondsolo.rival.RivalModule;
import com.beyondsolo.rival.RivalParties;
import com.beyondsolo.rival.RivalParty;
import com.beyondsolo.rival.RivalTestAccess;
import com.beyondsolo.scouting.GuildTerms;
import com.beyondsolo.scouting.ScoutEntity;
import com.beyondsolo.scouting.ScoutRecord;
import com.beyondsolo.scouting.Scouting;
import com.beyondsolo.scouting.ScoutingConfig;
import com.beyondsolo.scouting.ScoutingModule;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemEvent;
import com.beyondsolo.util.OnlinePlayers;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The great guilds' scouts, their offers and contracts ({@link Scouting}) and the hunter news ({@link HunterNews}). Run
 * with {@code ./gradlew runGameTestServer}. Offers are put on the table by hand where a test needs exact figures; the
 * rolls a hunter's holding out depends on are passed in.
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ScoutingGameTests {
    private static final String ARENA = "evaluation_zone";

    /** A hunter in the plot at that rank, with no gold, no offers and a clean record. */
    private static ServerPlayer hunter(GameTestHelper helper, String name, PlayerRank rank) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        p.removeData(AssociationModule.RECORD);
        p.removeData(RivalModule.RECORD);
        p.setGameMode(GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 2));
        p.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        SystemAPI.awaken(p, rank, PlayerClass.WARRIOR);
        // awakening at C and up brings the guilds already: start from nothing
        p.removeData(ScoutingModule.RECORD);
        Economy.reset(p);
        QuestTestAccess.markStarted(p);
        OnlinePlayers.addTestPlayer(helper, p);
        return p;
    }

    private static long now(GameTestHelper helper) {
        return helper.getLevel().getServer().overworld().getGameTime();
    }

    /** An offer put on the table by hand, with exact figures. */
    private static ScoutRecord.Offer offer(ServerPlayer p, RivalGuild guild, long bonus, long ceiling, long openAt) {
        GuildTerms t = GuildTerms.of(guild);
        ScoutRecord.Offer o = new ScoutRecord.Offer(UUID.randomUUID(), guild, SystemAPI.get(p).rank(), bonus, ceiling, t.cut(),
                ScoutingConfig.contractDays(), openAt, false, "Test Scout", 0);
        Scouting.record(p).offers().add(o);
        return o;
    }

    /** The translation key of a line ("" if it isn't one). */
    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
    }

    @Nullable
    private static Headline latest(MinecraftServer server) {
        List<Headline> all = NewsData.get(server).headlines();
        return all.isEmpty() ? null : all.get(all.size() - 1);
    }

    private static int run(ServerPlayer p, String command) throws CommandSyntaxException {
        return p.server.getCommands().getDispatcher().execute(command, p.createCommandSourceStack().withPermission(4).withSuppressedOutput());
    }

    // ================================================================ offers

    /** A rank-up brings the great guilds that want the new rank, one after another; each comes once for a rank. */
    @GameTest(template = ARENA)
    public static void aRankUpBringsTheGuildsThatWantIt(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "Climber", PlayerRank.D);
        ScoutRecord r = Scouting.record(p);
        helper.assertTrue(Scouting.scout(p, PlayerRank.D).isEmpty(), "no great guild comes for a D-rank hunter");

        SystemAPI.setRank(p, PlayerRank.C);
        helper.assertTrue(r.offers().size() == 1 && r.offers().get(0).guild == RivalGuild.RED_LANTERN,
                "at C only Red Lantern comes: " + r.offers().size());
        ScoutRecord.Offer lantern = r.offers().get(0);
        long n = now(helper);
        helper.assertTrue(!lantern.open(n) && lantern.openAt - n >= ScoutingConfig.firstScoutTicks(), "the offer is on its way, not on the table yet");
        helper.assertTrue(lantern.ceiling >= lantern.bonus && lantern.bonus % 100 == 0 && lantern.bonus > 0, "a round bonus, and room to raise it: "
                + lantern.bonus + " / " + lantern.ceiling);

        SystemAPI.setRank(p, PlayerRank.S);
        List<RivalGuild> came = new ArrayList<>();
        for (ScoutRecord.Offer o : r.offers()) if (o.rank == PlayerRank.S) came.add(o.guild);
        helper.assertTrue(came.size() == 3 && !came.contains(RivalGuild.RED_LANTERN),
                "at S the other three come (Red Lantern's offer is still on its way): " + came);
        long last = -1;
        for (ScoutRecord.Offer o : r.offers()) {
            if (o.rank != PlayerRank.S) continue;
            helper.assertTrue(o.openAt > last, "the scouts come one after another");
            last = o.openAt;
        }
        for (RivalGuild g : came) {
            ScoutRecord.Offer o = r.offerFrom(g);
            double base = EconomyHooks.GATE_GOLD[PlayerRank.S.tier()] * ScoutingConfig.bonusGates() * GuildTerms.of(g).factor();
            helper.assertTrue(o != null && o.bonus >= base * 0.9 - 100 && o.bonus <= base * 1.1 + 100,
                    g.key() + "'s bonus is about " + ScoutingConfig.bonusGates() + " S-rank gates' gold at its rate: " + (o == null ? -1 : o.bonus));
        }
        helper.assertTrue(Scouting.scout(p, PlayerRank.S).isEmpty(), "a guild comes for a rank once");
        SystemAPI.setRank(p, PlayerRank.A);
        helper.assertTrue(r.offers().size() == 4, "nobody comes for a step down");
        Scouting.reset(p);
        helper.succeed();
    }

    /** Holding out: a better offer on the table gets beaten (up to the guild's ceiling); without one a guild raises, or walks away. */
    @GameTest(template = ARENA)
    public static void holdingOutBeatsARivalOrRaisesOrWalks(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "Holdout", PlayerRank.S);
        ScoutRecord r = Scouting.record(p);
        long n = now(helper);
        ScoutRecord.Offer tide = offer(p, RivalGuild.AZURE_TIDE, 10_000, 20_000, n);
        ScoutRecord.Offer bastion = offer(p, RivalGuild.IRON_BASTION, 12_000, 13_000, n);

        Association.Outcome o = Scouting.holdOut(p, tide.id, 0.99, 0.0);
        helper.assertTrue(o.ok() && key(o.text()).endsWith("outcome.beat") && tide.bonus == 12_600 && tide.raises == 1 && !tide.last,
                "Azure Tide beats Iron Bastion's 12,000 by 5%: " + tide.bonus);
        o = Scouting.holdOut(p, bastion.id, 0.99, 0.0);
        helper.assertTrue(o.ok() && key(o.text()).endsWith("outcome.beat_final") && bastion.bonus == 13_000 && bastion.last,
                "Iron Bastion can only go to its ceiling, and says that's final: " + bastion.bonus);
        helper.assertFalse(Scouting.holdOut(p, bastion.id, 0.99, 0.0).ok(), "a final offer goes no higher");
        Scouting.holdOut(p, tide.id, 0.99, 0.0);
        helper.assertTrue(tide.bonus == 13_700, "Azure Tide beats 13,000: " + tide.bonus);

        o = Scouting.decline(p, bastion.id);
        helper.assertTrue(o.ok() && key(o.text()).endsWith("outcome.declined_raised") && r.offerFrom(RivalGuild.IRON_BASTION) == null,
                "turned down, Iron Bastion's offer is gone");
        helper.assertTrue(tide.bonus == 14_400, "word gets round: Azure Tide adds 5%: " + tide.bonus);

        o = Scouting.holdOut(p, tide.id, 0.9, 0.5);
        helper.assertTrue(o.ok() && key(o.text()).endsWith("outcome.raised") && tide.bonus == 16_600 && tide.raises == 3,
                "nobody else on the table: Azure Tide raises 15% this time: " + tide.bonus);
        o = Scouting.holdOut(p, tide.id, 0.0, 0.5);
        helper.assertTrue(!o.ok() && key(o.text()).endsWith("outcome.walked") && r.offers().isEmpty(), "pushed too far, they walk away");
        helper.assertTrue(r.declined() == 1 && r.walked() == 1 && r.raised() == 4,
                "one turned down, one walked away, four raises: " + r.declined() + " / " + r.walked() + " / " + r.raised());

        // a rival offer above what the guild can ever pay
        ScoutRecord.Offer lantern = offer(p, RivalGuild.RED_LANTERN, 15_000, 16_000, n);
        ScoutRecord.Offer sunfall = offer(p, RivalGuild.SUNFALL, 10_000, 12_000, n);
        o = Scouting.holdOut(p, sunfall.id, 0.99, 0.0);
        helper.assertTrue(o.ok() && key(o.text()).endsWith("outcome.cant_match") && sunfall.bonus == 12_000 && sunfall.last,
                "Sunfall can't match Red Lantern: it goes to its ceiling and stops");
        CompoundTag office = Scouting.officeTag(p);
        ListTag offers = office.getList("offers", Tag.TAG_COMPOUND);
        helper.assertTrue(offers.size() == 2 && offers.getCompound(0).getUUID("id").equals(lantern.id) && offers.getCompound(1).getBoolean("last"),
                "the office lists the table, the best offer first");
        helper.assertTrue(office.getList("guilds", Tag.TAG_COMPOUND).size() == GuildTerms.ALL.size() && office.getCompound("contract").isEmpty(),
                "the office shows every great guild's terms, and no contract yet");
        Scouting.reset(p);
        helper.succeed();
    }

    /** Every great guild turned down after a rank-up: Not for Sale, and the news has it. */
    @GameTest(template = ARENA)
    public static void turningEveryGreatGuildDownMakesAHunterNotForSale(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "Unsigned", PlayerRank.S);
        ScoutRecord r = Scouting.record(p);
        List<ScoutRecord.Offer> came = Scouting.scout(p, PlayerRank.S);
        helper.assertTrue(came.size() == 4, "all four great guilds come for an S-rank hunter: " + came.size());
        long n = now(helper);
        for (ScoutRecord.Offer o : came) o.openAt = n;
        ScoutRecord.Offer second = came.get(1);
        long before = second.bonus;
        Association.Outcome o = Scouting.decline(p, came.get(0).id);
        helper.assertTrue(o.ok() && second.bonus > before && second.bonus <= second.ceiling, "the others offer more: " + before + " -> " + second.bonus);
        Scouting.decline(p, came.get(1).id);
        Scouting.decline(p, came.get(2).id);
        helper.assertFalse(ChronicleModule.data(p).hasTitle("title.beyondsolo.not_for_sale"), "three of four isn't every guild");
        o = Scouting.decline(p, came.get(3).id);
        helper.assertTrue(o.ok() && key(o.text()).endsWith("outcome.not_for_sale"), "the last one turned down: " + key(o.text()));
        helper.assertTrue(ChronicleModule.data(p).hasTitle("title.beyondsolo.not_for_sale"), "Not for Sale");
        Headline h = latest(p.server);
        helper.assertTrue(h != null && h.breaking() && h.text().contains("news.beyondsolo.not_for_sale"), "breaking news");
        helper.assertTrue(r.offers().isEmpty() && r.declined() == 4, "the table is empty");
        Scouting.reset(p);
        helper.succeed();
    }

    // ================================================================ contracts

    /** Signing pays the bonus; the rest withdraw; the guild takes its cut of gate gold; another guild can buy the hunter out; walking out repays the rest. */
    @GameTest(template = ARENA)
    public static void signingPaysTheBonusAndTheGuildTakesItsCut(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "Signee", PlayerRank.B);
        ScoutRecord r = Scouting.record(p);
        long n = now(helper);
        offer(p, RivalGuild.RED_LANTERN, 5_000, 7_000, n);
        ScoutRecord.Offer bastion = offer(p, RivalGuild.IRON_BASTION, 4_000, 5_200, n);
        Association.Outcome o = Scouting.sign(p, bastion.id);
        helper.assertTrue(o.ok() && Economy.gold(p) == 4_000, "the bonus is paid on signing: " + Economy.gold(p));
        ScoutRecord.Contract c = r.contract();
        helper.assertTrue(c != null && c.guild() == RivalGuild.IRON_BASTION && c.daysLeft(c.fromDay()) == ScoutingConfig.contractDays(),
                "under contract with Iron Bastion for " + ScoutingConfig.contractDays() + " days");
        helper.assertTrue(r.offers().isEmpty() && r.signed() == 1 && r.best() == 4_000, "Red Lantern withdrew its offer");
        Headline h = latest(p.server);
        helper.assertTrue(h != null && h.kind().equals(HunterNews.GUILD) && h.text().contains("news.beyondsolo.signed"), "the signing makes the news");

        Economy.add(p, 1_000, Economy.Reason.GATE);
        helper.assertTrue(Economy.gold(p) == 4_850 && r.paid() == 150, "Iron Bastion takes 15% of gate gold: " + Economy.gold(p));
        Economy.add(p, 1_000, Economy.Reason.KILL);
        helper.assertTrue(Economy.gold(p) == 5_850, "and nothing else");
        helper.assertTrue(Scouting.hireFee(p, 1_000) == 700, "its hunters hire hands for 30% less");
        helper.assertTrue(Scouting.merit(p, 100) == 100, "merit is Sunfall's perk, not Iron Bastion's");
        helper.assertTrue(Scouting.allied(p, RivalGuild.IRON_BASTION) && !Scouting.allied(p, RivalGuild.SUNFALL), "one of Iron Bastion's own");

        ScoutRecord.Offer lantern = offer(p, RivalGuild.RED_LANTERN, 6_000, 8_000, n);
        o = Scouting.sign(p, lantern.id);
        helper.assertTrue(o.ok() && key(o.text()).endsWith("outcome.signed.buyout") && r.contract() != null
                && r.contract().guild() == RivalGuild.RED_LANTERN && Economy.gold(p) == 11_850, "Red Lantern buys the hunter out");
        helper.assertTrue(r.signed() == 2 && r.paid() == 0 && r.best() == 6_000, "a new contract, nothing taken yet");

        long owe = Scouting.owed(r.contract(), r.contract().fromDay());
        helper.assertTrue(owe == 6_000, "on its first day walking out costs the whole bonus: " + owe);
        Economy.set(p, 1_000);
        helper.assertFalse(Scouting.breakContract(p).ok(), "not without the gold to pay it back");
        helper.assertTrue(r.contract() != null, "the contract stands");
        Economy.set(p, 10_000);
        o = Scouting.breakContract(p);
        helper.assertTrue(o.ok() && r.contract() == null && Economy.gold(p) == 4_000, "walked out: 6,000 paid back, " + Economy.gold(p) + " left");
        h = latest(p.server);
        helper.assertTrue(h != null && h.text().contains("news.beyondsolo.walked_out"), "and the news has that too");
        Scouting.reset(p);
        helper.succeed();
    }

    /** A contract that runs its course brings the guild back to renew; an offer nobody answers lapses; each guild's perk. */
    @GameTest(template = ARENA)
    public static void aContractRunsItsCourseAndOffersLapse(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "Renewer", PlayerRank.S);
        ScoutRecord r = Scouting.record(p);
        ScoutRecord.Offer sunfall = Scouting.offerNow(p, GuildTerms.of(RivalGuild.SUNFALL));
        helper.assertTrue(Scouting.sign(p, sunfall.id).ok(), "signed with Sunfall");
        helper.assertTrue(ChronicleModule.data(p).hasTitle("title.beyondsolo.franchise_hunter"), "an S-rank hunter signing is a Franchise Hunter");
        helper.assertTrue(Scouting.merit(p, 100) == 115, "Sunfall's own earn 15% more merit");
        SystemEvent.XpGain xp = NeoForge.EVENT_BUS.post(new SystemEvent.XpGain(p, 100, BeyondSolo.id("test")));
        helper.assertTrue(xp.getAmount() == 100, "XP is Azure Tide's perk");

        long n = now(helper);
        helper.assertTrue(Scouting.expire(p) && r.contract() == null, "the contract ran its course");
        ScoutRecord.Offer renewal = r.offerFrom(RivalGuild.SUNFALL);
        helper.assertTrue(renewal != null && renewal.renewal && !renewal.open(n), "Sunfall asks to renew: a scout is on the way");

        ScoutRecord.Offer tide = offer(p, RivalGuild.AZURE_TIDE, 20_000, 30_000, n);
        helper.assertTrue(Scouting.sign(p, tide.id).ok() && r.offerFrom(RivalGuild.SUNFALL) == null,
                "signing elsewhere withdraws the renewal on its way too");
        xp = NeoForge.EVENT_BUS.post(new SystemEvent.XpGain(p, 100, BeyondSolo.id("test")));
        helper.assertTrue(xp.getAmount() == 105, "Azure Tide's own earn 5% more System XP: " + xp.getAmount());
        Scouting.endContract(p, Scouting.End.COMMAND);

        ScoutRecord.Offer lantern = offer(p, RivalGuild.RED_LANTERN, 9_000, 10_000, n);
        offer(p, RivalGuild.IRON_BASTION, 9_000, 10_000, n + 20 * 60);
        helper.assertTrue(Scouting.lapseAll(p) == 1 && r.offerFrom(RivalGuild.RED_LANTERN) == null && r.lapsed() == 1,
                "an offer left unanswered lapses");
        helper.assertTrue(r.offerFrom(RivalGuild.IRON_BASTION) != null, "one not yet on the table doesn't");
        helper.assertFalse(Scouting.sign(p, lantern.id).ok(), "a lapsed offer can't be signed");
        Scouting.reset(p);
        helper.succeed();
    }

    // ================================================================ the guild's own parties

    /** A guild's parties are on its hunter's side: they stand down for them, let them in, and clear a gate with them. */
    @GameTest(template = ARENA)
    public static void theGuildsOwnPartiesStandDownForItsHunter(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        ServerPlayer p = hunter(helper, "GuildHunter", PlayerRank.A);
        helper.assertTrue(Scouting.sign(p, Scouting.offerNow(p, GuildTerms.of(RivalGuild.SUNFALL)).id).ok(), "signed with Sunfall");
        List<RivalParty> parties = new ArrayList<>();
        GateInstance first = gate(helper, PlayerRank.C, new BlockPos(5, 2, 5));
        GateInstance held = gate(helper, PlayerRank.C, new BlockPos(1, 2, 5));
        GateInstance other = gate(helper, PlayerRank.C, new BlockPos(5, 2, 1));

        RivalParty coming = send(helper, first, RivalGuild.SUNFALL);
        parties.add(coming);
        RivalTestAccess.arrived(p, first, 0f);
        helper.assertTrue(coming.outcome() == RivalParty.Outcome.STOOD_DOWN, "one of their own got there first: they stand down, whatever their nerve");

        RivalParty inside = send(helper, held, RivalGuild.SUNFALL);
        parties.add(inside);
        RivalParties.arriveNow(server, inside);
        RivalParties.enterNow(server, inside);
        helper.assertTrue(inside.holds(), "first in, they hold the gate");
        helper.assertTrue(RivalParties.refusal(p, held) == null, "but their guild's own hunter walks straight in");
        CompoundTag hud = shown(p, inside);
        helper.assertTrue(hud != null && hud.getBoolean("ally"), "the HUD shows them as the hunter's guild");
        into(p, held);
        RivalTestAccess.arrived(p, held, 0f);
        helper.assertFalse(RivalParties.bossFell(level, held, level.damageSources().generic()), "the boss's reward is the hunter's");
        helper.assertTrue(inside.outcome() == RivalParty.Outcome.CLEARED, "and the clear is the party's too: " + inside.outcome());

        RivalParty lantern = send(helper, other, RivalGuild.RED_LANTERN);
        parties.add(lantern);
        RivalParties.arriveNow(server, lantern);
        RivalParties.enterNow(server, lantern);
        helper.assertTrue(RivalParties.refusal(p, other) != null, "another guild's party still turns them away");
        for (RivalParty party : parties) RivalTestAccess.drop(server, party);
        for (GateInstance g : List.of(first, held, other)) {
            g.setState(GateInstance.State.CLOSED);
            GateData.get(server).remove(g.id());
        }
        Scouting.reset(p);
        helper.succeed();
    }

    private static GateInstance gate(GameTestHelper helper, PlayerRank rank, BlockPos at) {
        return GateData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), rank, helper.getLevel().random.nextLong(),
                Level.OVERWORLD, helper.absolutePos(at));
    }

    private static RivalParty send(GameTestHelper helper, GateInstance g, RivalGuild guild) {
        RivalParty p = RivalParties.dispatch(helper.getLevel().getServer(), g, guild, null, helper.getLevel().random);
        if (p == null) throw new IllegalStateException("no party was sent for the gate");
        RivalTestAccess.unseen(p);
        return p;
    }

    private static void into(ServerPlayer p, GateInstance g) {
        ServerLevel dungeon = GateService.dungeon(p.server);
        BlockPos o = g.origin();
        if (p.level() == dungeon) p.moveTo(o.getX() + 0.5, o.getY() + 1, o.getZ() + 0.5, 0f, 0f);
        else p.teleportTo(dungeon, o.getX() + 0.5, o.getY() + 1, o.getZ() + 0.5, 0f, 0f);
    }

    @Nullable
    private static CompoundTag shown(ServerPlayer p, RivalParty party) {
        MinecraftServer server = p.server;
        ListTag list = RivalParties.view(server, RivalData.get(server), p, RivalParties.now(server)).getList("parties", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            if (list.getCompound(i).getUUID("id").equals(party.id)) return list.getCompound(i);
        }
        return null;
    }

    // ================================================================ the scout

    /** A scout sets out for the hunter with the offer, wears their guild, and goes home when told. */
    @GameTest(template = ARENA)
    public static void aScoutComesWithTheOffer(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "Scouted", PlayerRank.A);
        ScoutRecord.Offer o = offer(p, RivalGuild.SUNFALL, 8_000, 10_000, now(helper));
        ScoutEntity scout = Scouting.sendScout(p, o);
        helper.assertTrue(scout != null && !scout.isRemoved(), "a scout sets out");
        helper.assertTrue(scout.guild() == RivalGuild.SUNFALL && p.getUUID().equals(scout.hunter()) && o.id.equals(scout.offer())
                && scout.scoutName().equals("Test Scout"), "Sunfall's scout, for this hunter, with this offer");
        double d = Math.sqrt(scout.distanceToSqr(p));
        helper.assertTrue(d > 8 && d < 30, "they come from out of sight: " + Math.round(d) + " blocks off");
        helper.assertTrue(scout.isInvulnerable() && !scout.shouldBeSaved(), "nobody can hurt them, and they're never saved with the world");
        Scouting.wipe(p);
        helper.assertTrue(scout.isRemoved(), "sent home");
        Scouting.reset(p);
        helper.succeed();
    }

    // ================================================================ the news

    /** The news keeps its last headlines; a rank-up below C isn't news, an S-rank one is breaking. */
    @GameTest(template = ARENA)
    public static void theNewsKeepsItsLastHeadlines(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        HunterNews.clear(server);
        int keep = ScoutingConfig.newsKeep();
        for (int i = 0; i < keep + 5; i++) HunterNews.post(server, HunterNews.WORLD, Component.literal("headline " + i), HunterNews.STEEL, false);
        List<Headline> all = NewsData.get(server).headlines();
        helper.assertTrue(all.size() == keep, "the news keeps its last " + keep + ": " + all.size());
        helper.assertTrue(all.get(0).text().contains("headline 5") && all.get(all.size() - 1).text().contains("headline " + (keep + 4)),
                "the oldest go first");
        for (int i = 1; i < all.size(); i++) helper.assertTrue(all.get(i).id() > all.get(i - 1).id(), "each headline has its own number");

        ServerPlayer p = hunter(helper, "Headliner", PlayerRank.D);
        HunterNews.rankUp(p, PlayerRank.D);
        Headline h = latest(server);
        helper.assertTrue(h != null && h.text().contains("headline " + (keep + 4)), "a D-rank hunter isn't news yet");
        HunterNews.rankUp(p, PlayerRank.S);
        h = latest(server);
        helper.assertTrue(h != null && h.breaking() && h.kind().equals(HunterNews.RANK) && h.text().contains("news.beyondsolo.rank_up.s"),
                "a new S-rank hunter is breaking news");
        h = HunterNews.ambient(server, helper.getLevel().random);
        helper.assertTrue(h != null && h.kind().equals(HunterNews.WORLD) && !h.breaking(), "the world's other hunters make the news too");
        HunterNews.clear(server);
        helper.assertTrue(NewsData.get(server).headlines().isEmpty(), "cleared");
        helper.succeed();
    }

    // ================================================================ commands

    @GameTest(template = ARENA)
    public static void soloScoutContractAndNewsCommands(GameTestHelper helper) throws CommandSyntaxException {
        ServerPlayer p = hunter(helper, "Commander", PlayerRank.S);
        ScoutRecord r = Scouting.record(p);
        run(p, "solo scout offer azure_tide");
        ScoutRecord.Offer tide = r.offerFrom(RivalGuild.AZURE_TIDE);
        helper.assertTrue(tide != null && tide.open(now(helper)), "/solo scout offer puts the guild's offer on the table now");
        run(p, "solo scout offer all");
        helper.assertTrue(r.offers().size() == 4, "every guild that wants an S-rank hunter: " + r.offers().size());
        run(p, "solo scout list");
        run(p, "solo contract sign sunfall");
        helper.assertTrue(r.contract() != null && r.contract().guild() == RivalGuild.SUNFALL && r.offers().isEmpty(), "/solo contract sign");
        run(p, "solo contract info");
        run(p, "solo contract expire");
        helper.assertTrue(r.contract() == null && r.offerFrom(RivalGuild.SUNFALL) != null, "/solo contract expire: Sunfall asks to renew");
        boolean failed = false;
        try {
            run(p, "solo contract end");
        } catch (CommandSyntaxException e) {
            failed = true;
        }
        helper.assertTrue(failed, "/solo contract end with no contract is an error");
        run(p, "solo scout offer red_lantern");
        helper.assertTrue(run(p, "solo scout lapse") == 1 && r.offerFrom(RivalGuild.RED_LANTERN) == null, "/solo scout lapse");
        run(p, "solo scout reset");
        helper.assertTrue(Scouting.record(p).offers().isEmpty() && Scouting.record(p).lapsed() == 0, "/solo scout reset");
        helper.assertTrue(run(p, "solo news ambient 2") == 2, "/solo news ambient 2");
        run(p, "solo news rankup");
        Headline h = latest(p.server);
        helper.assertTrue(h != null && h.text().contains("news.beyondsolo.rank_up.s"), "/solo news rankup");
        run(p, "solo news list");
        run(p, "solo news clear");
        helper.assertTrue(NewsData.get(p.server).headlines().isEmpty(), "/solo news clear");
        run(p, "solo help scouting");
        helper.succeed();
    }
}
