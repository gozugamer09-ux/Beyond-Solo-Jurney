package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.awakening.AwakeningService;
import com.beyondsolo.chronicle.ChronicleModule;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.guild.Guild;
import com.beyondsolo.guild.GuildConfig;
import com.beyondsolo.guild.GuildData;
import com.beyondsolo.guild.GuildHunter;
import com.beyondsolo.guild.GuildPerks;
import com.beyondsolo.guild.GuildRole;
import com.beyondsolo.guild.Guilds;
import com.beyondsolo.guild.Recruits;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.raid.RaidState;
import com.beyondsolo.raid.Raids;
import com.beyondsolo.system.Balance;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.util.OnlinePlayers;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Guilds: founding one and what stops it, members joining, rising, being removed and leaving (the lead handed on, the
 * last one out ending it), the treasury and guild levels and what they give, hiring hunters and letting them go, what
 * a guild keeps over a restart and sends its page, the raid permit's refusals, and the /solo guild and raid commands.
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GuildGameTests {
    private static final String ARENA = "evaluation_zone";

    /**
     * A fresh hunter at {@code at} in the arena, evaluated at C-rank in {@code cls} (null: not yet, no System), with no
     * gold, findable by id like a real one.
     */
    static ServerPlayer hunter(GameTestHelper helper, String name, BlockPos at, @Nullable PlayerClass cls) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        AwakeningService.reset(p);
        Economy.reset(p);
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        if (cls != null) SystemAPI.awaken(p, PlayerRank.C, cls);
        BlockPos pos = helper.absolutePos(at);
        p.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        OnlinePlayers.addTestPlayer(helper, p);
        return p;
    }

    /** {@code master} founds a guild (handed the gold for it first). */
    static Guild found(GameTestHelper helper, ServerPlayer master, String name, String tag) {
        Economy.set(master, GuildConfig.FOUNDING_COST.get());
        Guilds.Outcome out = Guilds.found(master, name, tag, 0, 0);
        helper.assertTrue(out.ok(), "founded " + name + ": " + key(out));
        return Guilds.of(master);
    }

    /** {@code from} invites {@code to}, who accepts. */
    static void join(GameTestHelper helper, ServerPlayer from, ServerPlayer to) {
        Guilds.invite(from, to);
        helper.assertTrue(Guilds.accept(to, null).ok(), to.getGameProfile().getName() + " joins");
    }

    /** The lang key of an outcome's message. */
    static String key(Guilds.Outcome out) {
        return key(out.message());
    }

    static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
    }

    // ------------------------------------------------------------------ founding

    @GameTest(template = ARENA)
    public static void foundingAGuildAndWhatStopsIt(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        long cost = GuildConfig.FOUNDING_COST.get();
        ServerPlayer none = hunter(helper, "guild_none", new BlockPos(1, 2, 1), null);
        Economy.set(none, cost);
        helper.assertTrue(key(Guilds.found(none, "Unready Hall", "UNR", 0, 0)).equals("guild.beyondsolo.found.unevaluated"),
                "no guild before the evaluation");

        ServerPlayer a = hunter(helper, "guild_a", new BlockPos(3, 2, 1), PlayerClass.WARRIOR);
        Economy.set(a, cost - 1);
        helper.assertTrue(key(Guilds.found(a, "Ember Wardens", "EMW", 0, 0)).equals("guild.beyondsolo.found.gold") && Economy.gold(a) == cost - 1,
                "it costs gold, and nothing is taken when it falls short");
        Economy.set(a, cost);
        helper.assertTrue(key(Guilds.found(a, "Em", "EMW", 0, 0)).equals("guild.beyondsolo.found.name_length"), "a name too short");
        helper.assertTrue(key(Guilds.found(a, "x".repeat(Guilds.NAME_MAX + 1), "EMW", 0, 0)).equals("guild.beyondsolo.found.name_length"), "or too long");
        helper.assertTrue(key(Guilds.found(a, "Ember <Wardens>", "EMW", 0, 0)).equals("guild.beyondsolo.found.name_chars"), "or of odd characters");
        helper.assertTrue(key(Guilds.found(a, "Ember Wardens", "E", 0, 0)).equals("guild.beyondsolo.found.tag_bad"), "a tag of one letter");
        helper.assertTrue(key(Guilds.found(a, "Ember Wardens", "EMBER", 0, 0)).equals("guild.beyondsolo.found.tag_bad"), "or of five");
        helper.assertTrue(Economy.gold(a) == cost, "nothing paid for a refusal");

        Guilds.Outcome founded = Guilds.found(a, "  Ember   Wardens ", "emw", 3, 5);
        helper.assertTrue(founded.ok() && key(founded).equals("guild.beyondsolo.founded"), "founded: " + key(founded));
        Guild g = Guilds.of(a);
        helper.assertTrue(g != null && g.name().equals("Ember Wardens") && g.tag().equals("EMW") && g.crest() == 3 && g.color() == 5,
                "under its name (tidied), its tag (in capitals), its crest and colour");
        helper.assertTrue(g.isMaster(a.getUUID()) && g.role(a.getUUID()) == GuildRole.MASTER && g.size() == 1 && g.level() == 1 && g.treasury() == 0,
                "a guild of one, led by its founder");
        helper.assertTrue(Economy.gold(a) == 0, "the founding is paid");
        helper.assertTrue(ChronicleModule.data(a).hasTitle(Guilds.FOUNDER_TITLE), "with a title for it");
        helper.assertTrue(!g.log().isEmpty() && g.log().get(0).key().equals("founded"), "the guild's log begins");
        helper.assertTrue(key(Guilds.found(a, "Second Wardens", "SCW", 0, 0)).equals("guild.beyondsolo.found.already"), "one guild at a time");

        ServerPlayer b = hunter(helper, "guild_b", new BlockPos(5, 2, 1), PlayerClass.MAGE);
        Economy.set(b, cost);
        helper.assertTrue(key(Guilds.found(b, "ember wardens", "EWX", 0, 0)).equals("guild.beyondsolo.found.name_taken"), "a name is taken in any case");
        helper.assertTrue(key(Guilds.found(b, "Ash Wardens", "emw", 0, 0)).equals("guild.beyondsolo.found.tag_taken"), "and so is a tag");
        helper.assertTrue(Economy.gold(b) == cost, "b paid nothing");
        helper.assertTrue(Guilds.data(server).named("emw") == g && Guilds.data(server).named("EMBER WARDENS") == g, "found by its tag or its name");

        // the master ends it, and its treasury goes to them
        Guilds.setTreasury(server, g, 700);
        helper.assertTrue(key(Guilds.disband(b)).equals("guild.beyondsolo.none"), "b leads no guild");
        helper.assertTrue(Guilds.disband(a).ok() && Guilds.of(a) == null && Guilds.data(server).get(g.id()) == null, "disbanded");
        helper.assertTrue(Economy.gold(a) == 700, "the treasury goes to the master");
        helper.succeed();
    }

    // ------------------------------------------------------------------ members

    @GameTest(template = ARENA)
    public static void membersJoinRiseAndLeave(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer a = hunter(helper, "guild_a", new BlockPos(1, 2, 1), PlayerClass.WARRIOR);
        ServerPlayer b = hunter(helper, "guild_b", new BlockPos(3, 2, 1), PlayerClass.MAGE);
        ServerPlayer c = hunter(helper, "guild_c", new BlockPos(5, 2, 1), PlayerClass.HEALER);
        ServerPlayer d = hunter(helper, "guild_d", new BlockPos(1, 2, 3), PlayerClass.RANGER);
        Guild g = found(helper, a, "Iron Lanterns", "IRL");

        helper.assertTrue(key(Guilds.invite(b, c)).equals("guild.beyondsolo.none"), "b has no guild to invite anyone into");
        helper.assertTrue(key(Guilds.invite(a, a)).equals("guild.beyondsolo.invite.self"), "nobody invites themselves");
        Guilds.Outcome sent = Guilds.invite(a, b);
        helper.assertTrue(sent.ok() && key(sent).equals("guild.beyondsolo.invite.sent"), "invited: " + key(sent));
        helper.assertTrue(Guilds.invitesFor(b).equals(List.of(g.id())), "the invitation waits for them");
        helper.assertTrue(key(Guilds.invite(a, b)).equals("guild.beyondsolo.invite.again"), "asked again");
        Guilds.Outcome joined = Guilds.accept(b, null);
        helper.assertTrue(joined.ok() && key(joined).equals("guild.beyondsolo.joined") && g.has(b.getUUID()) && g.role(b.getUUID()) == GuildRole.MEMBER,
                "b joins, as a member");
        helper.assertTrue(Guilds.invitesFor(b).isEmpty(), "the invitation is used up");
        helper.assertTrue(Guilds.together(server, a.getUUID(), b.getUUID()) && !Guilds.together(server, a.getUUID(), c.getUUID()), "guild mates");
        helper.assertTrue(key(Guilds.invite(b, c)).equals("guild.beyondsolo.not_officer"), "a plain member invites nobody");
        helper.assertTrue(key(Guilds.invite(a, b)).equals("guild.beyondsolo.invite.member"), "b is in already");

        // turning one down, and accepting one that is gone
        Guilds.invite(a, c);
        helper.assertTrue(Guilds.decline(c, null).ok() && !g.has(c.getUUID()) && Guilds.invitesFor(c).isEmpty(), "declined");
        helper.assertTrue(key(Guilds.accept(c, null)).equals("guild.beyondsolo.accept.none"), "nothing left to accept");
        helper.assertTrue(key(Guilds.accept(c, g.id())).equals("guild.beyondsolo.accept.expired"), "nor that one");

        // officers: the master makes them; they invite and remove plain members, not the master
        helper.assertTrue(key(Guilds.promote(b, a.getUUID())).equals("guild.beyondsolo.not_master"), "only the master promotes");
        helper.assertTrue(Guilds.promote(a, b.getUUID()).ok() && g.role(b.getUUID()) == GuildRole.OFFICER, "b is an officer");
        helper.assertTrue(key(Guilds.promote(a, b.getUUID())).equals("guild.beyondsolo.promote.already"), "already");
        helper.assertTrue(key(Guilds.promote(a, a.getUUID())).equals("guild.beyondsolo.role.self"), "the master has no rank to change");
        Guilds.invite(b, c);
        helper.assertTrue(Guilds.accept(c, g.id()).ok() && g.size() == 3, "c joins at the officer's invitation");
        helper.assertTrue(key(Guilds.kick(b, a.getUUID())).equals("guild.beyondsolo.kick.rank"), "an officer can't remove the master");
        helper.assertTrue(key(Guilds.kick(c, b.getUUID())).equals("guild.beyondsolo.not_officer"), "a member removes nobody");
        helper.assertTrue(key(Guilds.kick(b, b.getUUID())).equals("guild.beyondsolo.kick.self"), "nor themselves");
        helper.assertTrue(key(Guilds.kick(b, d.getUUID())).equals("guild.beyondsolo.not_member"), "nor someone outside it");
        helper.assertTrue(Guilds.kick(b, c.getUUID()).ok() && !g.has(c.getUUID()) && Guilds.of(c) == null, "c is removed");

        // the lead handed on: the old master stays on as an officer
        helper.assertTrue(Guilds.demote(a, b.getUUID()).ok() && g.role(b.getUUID()) == GuildRole.MEMBER, "b is a member again");
        helper.assertTrue(key(Guilds.demote(a, b.getUUID())).equals("guild.beyondsolo.demote.already"), "already");
        helper.assertTrue(Guilds.transfer(a, b.getUUID()).ok() && g.isMaster(b.getUUID()) && g.role(a.getUUID()) == GuildRole.OFFICER,
                "b leads, and a is an officer");
        helper.assertTrue(key(Guilds.transfer(a, b.getUUID())).equals("guild.beyondsolo.not_master"), "a no longer can");

        // a master can't walk out of a guild they lead by joining another; a member can, leaving the old one
        ServerPlayer e = hunter(helper, "guild_e", new BlockPos(3, 2, 3), PlayerClass.ASSASSIN);
        Guild other = found(helper, e, "Hollow Pines", "HLP");
        Guilds.invite(e, b);
        helper.assertTrue(key(Guilds.accept(b, other.id())).equals("guild.beyondsolo.accept.master") && g.isMaster(b.getUUID())
                && Guilds.invitesFor(b).contains(other.id()), "b leads Iron Lanterns: they can't just leave it, and the invitation keeps");
        Guilds.invite(b, c);
        Guilds.accept(c, g.id());
        Guilds.invite(e, c);
        helper.assertTrue(Guilds.accept(c, other.id()).ok() && !g.has(c.getUUID()) && other.has(c.getUUID()), "c goes over to Hollow Pines");
        Guilds.disband(e);

        // a master who leaves hands the guild to their first officer...
        Guilds.invite(b, c);
        Guilds.accept(c, g.id());
        Guilds.invite(b, d);
        Guilds.accept(d, g.id());
        helper.assertTrue(Guilds.leave(b).ok() && Guilds.of(b) == null && g.isMaster(a.getUUID()) && g.size() == 3, "b leaves: their officer a leads");
        // ...or, with none, to the member who has been in it longest
        helper.assertTrue(Guilds.leave(a).ok() && g.isMaster(c.getUUID()) && g.size() == 2, "a leaves: c, in before d, leads");
        // the last one out ends it
        helper.assertTrue(Guilds.leave(d).ok() && g.size() == 1, "d leaves");
        Guilds.Outcome last = Guilds.leave(c);
        helper.assertTrue(last.ok() && key(last).equals("guild.beyondsolo.left.ended") && Guilds.data(server).get(g.id()) == null, "the last one out ends it");
        helper.assertTrue(key(Guilds.leave(c)).equals("guild.beyondsolo.none"), "c is in none now");
        helper.succeed();
    }

    // ------------------------------------------------------------------ the treasury and levels

    @GameTest(template = ARENA)
    public static void theTreasuryAndGuildLevels(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer a = hunter(helper, "guild_a", new BlockPos(1, 2, 1), PlayerClass.WARRIOR);
        ServerPlayer b = hunter(helper, "guild_b", new BlockPos(3, 2, 1), PlayerClass.MAGE);
        Guild g = found(helper, a, "Gilded Hollow", "GDH");
        join(helper, a, b);
        Economy.set(a, 10_000);
        Economy.set(b, 3_000);

        helper.assertTrue(key(Guilds.deposit(b, 0)).equals("guild.beyondsolo.deposit.amount"), "nothing to pay in");
        helper.assertTrue(key(Guilds.deposit(b, 3_001)).equals("guild.beyondsolo.deposit.gold") && Economy.gold(b) == 3_000, "no more than they carry");
        helper.assertTrue(Guilds.deposit(b, 3_000).ok() && g.treasury() == 3_000 && Economy.gold(b) == 0, "b pays in");
        long per = GuildConfig.GOLD_PER_XP.get();
        helper.assertTrue(g.xp() == 3_000 / per && g.member(b.getUUID()).contribution() == 3_000 / per,
                "a part of it is guild XP, to b's name: " + g.xp());
        helper.assertTrue(key(Guilds.withdraw(b, 100)).equals("guild.beyondsolo.not_master"), "only the master takes gold out");
        helper.assertTrue(key(Guilds.withdraw(a, 3_001)).equals("guild.beyondsolo.withdraw.short"), "no more than there is");
        helper.assertTrue(Guilds.withdraw(a, 1_000).ok() && g.treasury() == 2_000 && Economy.gold(a) == 11_000, "a takes some out");
        helper.assertTrue(Guilds.spend(server, g, 2_000) && g.treasury() == 0 && !Guilds.spend(server, g, 1), "the guild spends what it has, no more");

        // guild XP: as many levels as it reaches at once
        Guilds.setLevel(server, g, 1);
        Guilds.addXp(server, g, GuildPerks.xpToNext(1) + GuildPerks.xpToNext(2) + 5, a.getUUID());
        helper.assertTrue(g.level() == 3 && g.xp() == 5, "two levels up, and the rest kept: " + g.level() + " / " + g.xp());
        // what a level gives
        helper.assertTrue(GuildPerks.memberCap(3) == GuildPerks.memberCap(1) + 2 * GuildConfig.MEMBERS_PER_LEVEL.get()
                && GuildPerks.rosterCap(3) == GuildPerks.rosterCap(1) + 1, "room for more members and more hunters");
        helper.assertTrue(GuildPerks.permitRank(1) == PlayerRank.C && GuildPerks.permitRank(3) == PlayerRank.B
                && GuildPerks.permitRank(GuildConfig.MAX_LEVEL) == PlayerRank.S, "raid permits of higher ranks");
        helper.assertTrue(GuildPerks.permitPrice(5, PlayerRank.C) < GuildPerks.permitPrice(1, PlayerRank.C), "and cheaper");
        helper.assertTrue(GuildPerks.gold(a, 1_000, Economy.Reason.KILL) == Math.round(1_000 * (1 + GuildPerks.goldBonus(3)))
                && GuildPerks.gold(a, 1_000, Economy.Reason.SALE) == 1_000, "a little more gold from kills, none from selling");
        helper.assertTrue(GuildPerks.xpBonus(3) > 0 && GuildPerks.xpBonus(1) == 0, "and a little more System XP");

        // a member's gate clear counts, and earns guild XP by its rank
        GateBlockEntity block = GateService.spawnGate(helper.getLevel(), helper.absolutePos(new BlockPos(5, 2, 5)), PlayerRank.D, Direction.NORTH, false);
        GateInstance gate = GateData.get(server).get(block.gateId());
        long xp = g.xp();
        Guilds.gateCleared(b, gate);
        helper.assertTrue(g.gatesCleared() == 1 && g.xp() == xp + GuildPerks.gateXp(PlayerRank.D), "a D-rank clear: " + g.xp());

        // the top level holds no more XP
        Guilds.addXp(server, g, Long.MAX_VALUE / 4, null);
        helper.assertTrue(g.level() == GuildConfig.MAX_LEVEL && g.xp() == 0, "the top: " + g.level() + " / " + g.xp());
        Guilds.disband(a);
        helper.succeed();
    }

    // ------------------------------------------------------------------ hunters

    @GameTest(template = ARENA)
    public static void hiringAndLettingGoOfHunters(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer a = hunter(helper, "guild_a", new BlockPos(1, 2, 1), PlayerClass.WARRIOR);
        ServerPlayer b = hunter(helper, "guild_b", new BlockPos(3, 2, 1), PlayerClass.MAGE);
        Guild g = found(helper, a, "Quiet Harbor", "QHB");
        join(helper, a, b);

        List<GuildHunter> pool = Guilds.recruits(server, g);
        helper.assertTrue(pool.size() == GuildConfig.RECRUITS.get(), "today's recruits: " + pool.size());
        helper.assertTrue(Guilds.recruits(server, g).equals(pool), "the same all day");
        GuildHunter first = pool.get(0), second = pool.get(1), third = pool.get(2);
        for (GuildHunter h : pool) {
            helper.assertTrue(h.rank() == Balance.rankFor(h.level()) && h.level() >= Recruits.bandStart(h.rank()) && h.level() <= Recruits.bandEnd(h.rank())
                    && h.cost() == Recruits.price(h.level()), h.name() + ": their rank is their level's, and their price their rank's");
        }
        helper.assertTrue(Recruits.price(Recruits.bandStart(PlayerRank.B) + 3) > Recruits.price(Recruits.bandStart(PlayerRank.B))
                && Recruits.price(Recruits.bandStart(PlayerRank.B)) > Recruits.price(Recruits.bandStart(PlayerRank.C)), "the further along, the dearer");

        helper.assertTrue(key(Guilds.hire(b, first.id())).equals("guild.beyondsolo.not_officer"), "a plain member hires nobody");
        helper.assertTrue(key(Guilds.hire(a, first.id())).equals("guild.beyondsolo.hire.treasury"), "paid from the treasury, which is empty");
        Guilds.setTreasury(server, g, 1_000_000);
        Guilds.Outcome hired = Guilds.hire(a, first.id());
        helper.assertTrue(hired.ok() && g.hunters().size() == 1 && g.hunter(first.id()) != null && g.treasury() == 1_000_000 - first.cost(),
                "hired, for their price: " + key(hired));
        helper.assertTrue(!Guilds.recruits(server, g).contains(first) && key(Guilds.hire(a, first.id())).equals("guild.beyondsolo.hire.gone"),
                "they look for a guild no longer");
        helper.assertTrue(Guilds.hire(a, second.id()).ok(), "a second");
        int cap = GuildPerks.rosterCap(g.level());
        helper.assertTrue(g.hunters().size() == cap && key(Guilds.hire(a, third.id())).equals("guild.beyondsolo.hire.full"), "no more than its roster holds");

        // hurt in a raid: they stay home until they heal
        long now = server.overworld().getGameTime();
        Guilds.hunterFell(server, g.id(), first.id());
        helper.assertTrue(first.injured(now) && !Guilds.fit(server, g).contains(first) && Guilds.fit(server, g).size() == 1, "hurt hunters stay home");
        Guilds.healHunters(server, g);
        helper.assertTrue(!first.injured(now) && Guilds.fit(server, g).size() == 2, "healed");

        // what a raid teaches them
        int before = second.level();
        int gained = Guilds.hunterRaided(server, g.id(), second.id(), 2, 5);
        helper.assertTrue(gained == 2 && second.level() == before + 2 && second.raids() == 1 && second.kills() == 5,
                "two levels, a raid and five kills to their name");

        // letting one go
        helper.assertTrue(key(Guilds.dismiss(b, first.id())).equals("guild.beyondsolo.not_officer"), "a plain member lets nobody go");
        helper.assertTrue(Guilds.dismiss(a, first.id()).ok() && g.hunter(first.id()) == null && g.hunters().size() == 1, "let go");
        helper.assertTrue(key(Guilds.dismiss(a, first.id())).equals("guild.beyondsolo.dismiss.gone"), "already gone");
        Guilds.disband(a);
        helper.succeed();
    }

    // ------------------------------------------------------------------ saving, the page, wiping

    @GameTest(template = ARENA)
    public static void guildsOutlastARestart(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var registries = helper.getLevel().registryAccess();
        ServerPlayer a = hunter(helper, "guild_a", new BlockPos(1, 2, 1), PlayerClass.WARRIOR);
        ServerPlayer b = hunter(helper, "guild_b", new BlockPos(3, 2, 1), PlayerClass.MAGE);
        ServerPlayer c = hunter(helper, "guild_c", new BlockPos(5, 2, 1), PlayerClass.HEALER);
        Guild g = found(helper, a, "Long Watch", "LWT");
        join(helper, a, b);
        Guilds.promote(a, b.getUUID());
        Guilds.setTreasury(server, g, 4_321);
        Guilds.addXp(server, g, 77, b.getUUID());
        GuildHunter h = Recruits.of(RandomSource.create(42), PlayerRank.C, PlayerClass.RANGER);
        Guilds.enlist(server, g, h);
        Guilds.hunterRaided(server, g.id(), h.id(), 1, 3);

        GuildData loaded = GuildData.load(Guilds.data(server).save(new CompoundTag(), registries), registries);
        Guild back = loaded.get(g.id());
        helper.assertTrue(back != null && back.name().equals("Long Watch") && back.tag().equals("LWT") && back.crest() == g.crest() && back.color() == g.color(),
                "the same name, tag, crest and colour");
        helper.assertTrue(back.master().equals(a.getUUID()) && back.role(b.getUUID()) == GuildRole.OFFICER && back.members().equals(g.members())
                && back.name(b.getUUID()).equals("guild_b"), "the same members, in their ranks, under their names");
        helper.assertTrue(back.level() == g.level() && back.xp() == g.xp() && back.treasury() == 4_321 && back.foundedDay() == g.foundedDay(),
                "its level, XP, treasury and founding day");
        helper.assertTrue(back.member(b.getUUID()).contribution() == 77, "what each member gave");
        GuildHunter hb = back.hunter(h.id());
        helper.assertTrue(hb != null && hb.name().equals(h.name()) && hb.playerClass() == PlayerClass.RANGER && hb.level() == h.level()
                && hb.skin() == h.skin() && hb.cost() == h.cost() && hb.raids() == 1 && hb.kills() == 3, "its hunters, and their records");
        helper.assertTrue(back.log().size() == g.log().size(), "its log");
        helper.assertTrue(loaded.of(b.getUUID()) == back && loaded.named("lwt") == back, "found by its members and its tag");

        // what the GUILD page is sent: the guild, or an outsider's invitations
        CompoundTag mine = Guilds.syncTag(b);
        helper.assertTrue(mine.getBoolean("in") && mine.getString("name").equals("Long Watch") && mine.getLong("treasury") == 4_321, "the page sees the guild");
        Guilds.invite(a, c);
        CompoundTag theirs = Guilds.syncTag(c);
        helper.assertTrue(!theirs.getBoolean("in") && theirs.getList("invites", Tag.TAG_COMPOUND).size() == 1
                && theirs.getList("invites", Tag.TAG_COMPOUND).getCompound(0).getString("tag").equals("LWT"), "and an outsider their invitation");

        // /solo wipe takes a hunter out of their guild as if they left
        Guilds.wipe(a);
        helper.assertTrue(Guilds.of(a) == null && g.isMaster(b.getUUID()), "a is out, and their officer b leads");
        Guilds.wipe(c);
        helper.assertTrue(Guilds.invitesFor(c).isEmpty(), "c's invitation is forgotten");
        Guilds.disband(b);
        helper.succeed();
    }

    // ------------------------------------------------------------------ raid permits

    @GameTest(template = ARENA)
    public static void aRaidPermitAsksTheRightThings(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer a = hunter(helper, "guild_a", new BlockPos(1, 2, 1), PlayerClass.WARRIOR);
        ServerPlayer b = hunter(helper, "guild_b", new BlockPos(3, 2, 1), PlayerClass.MAGE);
        helper.assertTrue(key(Guilds.permit(a, PlayerRank.E)).equals("guild.beyondsolo.none"), "no guild, no permit");
        Guild g = found(helper, a, "Permit Seekers", "PMS");
        join(helper, a, b);
        helper.assertTrue(key(Guilds.permit(b, PlayerRank.E)).equals("guild.beyondsolo.not_officer"), "members don't ask for raids");
        helper.assertTrue(key(Guilds.permit(a, PlayerRank.A)).equals("guild.beyondsolo.permit.level"), "a new guild can't ask for an A-rank raid");
        helper.assertTrue(key(Guilds.permit(a, PlayerRank.C)).equals("guild.beyondsolo.permit.treasury"), "nor for one it can't pay for");
        long price = GuildPerks.permitPrice(1, PlayerRank.C);
        Guilds.setTreasury(server, g, price);

        // the guild's own raid gate is open already
        GateBlockEntity block = GateService.spawnGate(helper.getLevel(), helper.absolutePos(new BlockPos(5, 2, 5)), PlayerRank.C, Direction.NORTH, false);
        GateInstance raid = GateData.get(server).get(block.gateId());
        Raids.makeRaid(helper.getLevel(), block, raid, g.id());
        helper.assertTrue(key(Guilds.permit(a, PlayerRank.C)).equals("guild.beyondsolo.permit.open"), "one raid of its own at a time");
        CompoundTag page = Raids.guildRaidTag(server, g);
        helper.assertTrue(page != null && page.getBoolean("permit") && page.getString("phase").equals(RaidState.Phase.MUSTER.name()),
                "the GUILD page shows it mustering");
        // and on a raid's roster, nobody asks for another
        helper.assertTrue(Raids.join(a, raid).ok(), "a musters");
        helper.assertTrue(key(Guilds.permit(a, PlayerRank.C)).equals("guild.beyondsolo.permit.in_raid"), "a is on a raid already");
        helper.assertTrue(g.treasury() == price, "nothing paid for a refusal");
        Raids.leave(a);
        Guilds.disband(a);
        helper.assertTrue(raid.raid().guild == null, "a guild that ends lets go of its raid");
        helper.succeed();
    }

    // ------------------------------------------------------------------ /solo guild and /solo raid

    /** Runs a command as the player, with an operator's permission. */
    private static int run(ServerPlayer p, String command) throws CommandSyntaxException {
        return p.server.getCommands().getDispatcher().execute(command, p.createCommandSourceStack().withPermission(4).withSuppressedOutput());
    }

    /** The message key of a command's error, or "" if it ran. */
    private static String fails(ServerPlayer p, String command) {
        try {
            run(p, command);
            return "";
        } catch (CommandSyntaxException e) {
            return e.getRawMessage() instanceof Component c ? key(c) : String.valueOf(e.getRawMessage());
        }
    }

    @GameTest(template = ARENA)
    public static void soloGuildAndRaidCommands(GameTestHelper helper) throws CommandSyntaxException {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer p = hunter(helper, "guild_op", new BlockPos(3, 2, 1), null);
        run(p, "solo awaken c warrior");
        run(p, "solo guild create @s CMD Command Hall");
        Guild g = Guilds.of(p);
        helper.assertTrue(g != null && g.name().equals("Command Hall") && g.tag().equals("CMD") && Economy.gold(p) == 0, "founded, on the house");
        helper.assertTrue(fails(p, "solo guild create @s CMX Command Annex").equals("guild.beyondsolo.found.already") && Economy.gold(p) == 0,
                "a refusal is the command's error, and costs nothing");
        run(p, "solo guild level 5");
        helper.assertTrue(g.level() == 5, "level 5");
        run(p, "solo guild gold 12345");
        helper.assertTrue(g.treasury() == 12_345, "its treasury set");
        run(p, "solo guild hunter b guardian");
        helper.assertTrue(g.hunters().size() == 1 && g.hunters().get(0).playerClass() == PlayerClass.GUARDIAN && g.hunters().get(0).rank() == PlayerRank.B,
                "a B-rank guardian joins its roster");
        Guilds.hunterFell(server, g.id(), g.hunters().get(0).id());
        run(p, "solo guild heal hunters");
        helper.assertTrue(Guilds.fit(server, g).size() == 1, "healed");
        helper.assertTrue(run(p, "solo help guilds") == 1, "the help page for guilds and raids");

        // the nearest gate becomes a raid gate; taken in at once
        GateBlockEntity block = GateService.spawnGate(helper.getLevel(), helper.absolutePos(new BlockPos(5, 2, 5)), PlayerRank.C, Direction.NORTH, false);
        GateInstance gate = GateData.get(server).get(block.gateId());
        run(p, "solo raid make");
        helper.assertTrue(gate.isRaid() && gate.raid().phase == RaidState.Phase.MUSTER, "the gate musters a raid");
        helper.assertTrue(fails(p, "solo raid make").equals("command.beyondsolo.solo.error.already_raid"), "it is one already");
        Raids.join(p, gate);
        run(p, "solo raid start now");
        helper.assertTrue(gate.raid().phase == RaidState.Phase.RUNNING, "underway");
        helper.assertTrue(fails(p, "solo raid phase barrier").equals("raid.beyondsolo.no_boss"), "no boss to force while its cave isn't built");
        Raids.fail(server, gate);

        run(p, "solo guild disband");
        helper.assertTrue(Guilds.of(p) == null && Guilds.data(server).get(g.id()) == null, "disbanded");
        helper.assertTrue(fails(p, "solo guild level 2").equals("command.beyondsolo.solo.error.no_guild"), "no guild to set");
        helper.succeed();
    }

    private GuildGameTests() {}
}
