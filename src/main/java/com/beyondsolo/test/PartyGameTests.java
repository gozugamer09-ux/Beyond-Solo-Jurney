package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.awakening.AwakeningData;
import com.beyondsolo.awakening.AwakeningService;
import com.beyondsolo.depths.DepthsBuild;
import com.beyondsolo.depths.HiddenKind;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.gate.DungeonLayout;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.key.DungeonKeys;
import com.beyondsolo.key.KeyedRuns;
import com.beyondsolo.network.PartyActionPayload;
import com.beyondsolo.network.PartyEventPayload;
import com.beyondsolo.network.PartySyncPayload;
import com.beyondsolo.party.Parties;
import com.beyondsolo.party.Party;
import com.beyondsolo.party.PartyConfig;
import com.beyondsolo.party.PartyData;
import com.beyondsolo.party.Regroup;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.spawn.LandingData;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import com.beyondsolo.util.OnlinePlayers;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Parties: forming one, leaving, removing and handing on the lead, surviving a restart, reaching the client, sharing
 * kills, no friendly fire, gates that wait for the party, regrouping, landing together on a first join, and a party of
 * friends without the System going through one awakening trial together.
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PartyGameTests {
    private static final String ARENA = "evaluation_zone";

    /** A fresh player (no System, not called) standing at {@code at} in the arena, findable by id like a real one. */
    private static ServerPlayer hunter(GameTestHelper helper, ServerLevel level, String name, BlockPos at) {
        ServerPlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        AwakeningService.reset(p);
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        BlockPos pos = helper.absolutePos(at);
        p.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        OnlinePlayers.addTestPlayer(p);
        return p;
    }

    private static ServerPlayer hunter(GameTestHelper helper, String name, BlockPos at) {
        return hunter(helper, helper.getLevel(), name, at);
    }

    /** {@code leader} invites the others and they accept: one party. */
    private static Party form(ServerPlayer leader, ServerPlayer... others) {
        for (ServerPlayer o : others) {
            Parties.invite(leader, o);
            Parties.accept(o, leader.getUUID());
        }
        return Parties.of(leader);
    }

    /** The lang key of an outcome's message. */
    private static String key(Parties.Outcome out) {
        return out.message().getContents() instanceof TranslatableContents t ? t.getKey() : "";
    }

    // ------------------------------------------------------------------ forming and leaving

    @GameTest(template = ARENA)
    public static void anAcceptedInvitationFormsAParty(GameTestHelper helper) {
        ServerPlayer a = hunter(helper, "party_a", new BlockPos(1, 2, 1));
        ServerPlayer b = hunter(helper, "party_b", new BlockPos(5, 2, 1));
        ServerPlayer c = hunter(helper, "party_c", new BlockPos(1, 2, 5));
        helper.assertTrue(key(Parties.invite(a, a)).equals("party.beyondsolo.invite.self"), "nobody invites themselves");
        Parties.Outcome sent = Parties.invite(a, b);
        helper.assertTrue(sent.ok() && key(sent).equals("party.beyondsolo.invite.sent"), "invited: " + key(sent));
        helper.assertTrue(Parties.invitesFor(b).equals(List.of(a.getUUID())), "the invitation waits for them");
        helper.assertTrue(Parties.inviterNames(b).equals(List.of("party_a")), "under their name");
        helper.assertTrue(Parties.of(a) == null, "a party of one is no party");
        helper.assertTrue(key(Parties.invite(a, b)).equals("party.beyondsolo.invite.again"), "asked again");

        Parties.Outcome joined = Parties.accept(b, a.getUUID());
        helper.assertTrue(joined.ok() && key(joined).equals("party.beyondsolo.joined"), "joined: " + key(joined));
        Party party = Parties.of(a);
        helper.assertTrue(party != null && party == Parties.of(b) && party.isLeader(a.getUUID()) && party.size() == 2,
                "one party, led by whoever invited");
        helper.assertTrue(Parties.together(a, b) && !Parties.together(a, c) && !Parties.together(a, a), "together, and only them");
        helper.assertTrue(Parties.mates(a).equals(List.of(b)), "a's party mate is b");
        helper.assertTrue(Parties.invitesFor(b).isEmpty(), "the invitation is used up");
        helper.assertTrue(key(Parties.invite(b, c)).equals("party.beyondsolo.not_leader"), "only the leader invites");
        helper.assertTrue(key(Parties.invite(a, b)).equals("party.beyondsolo.invite.member"), "already in");

        // turning one down
        Parties.invite(a, c);
        helper.assertTrue(Parties.decline(c, null).ok() && Parties.of(c) == null && Parties.invitesFor(c).isEmpty(), "declined");
        helper.assertTrue(key(Parties.accept(c, null)).equals("party.beyondsolo.accept.none"), "nothing left to accept");
        helper.assertTrue(key(Parties.accept(c, a.getUUID())).equals("party.beyondsolo.accept.expired"), "nor that one");

        // what the PARTY page is sent builds for everyone, in a party or not
        Parties.sync(a);
        Parties.sync(c);
        helper.assertTrue(key(Parties.disband(b)).equals("party.beyondsolo.not_leader"), "only the leader disbands");
        helper.assertTrue(Parties.disband(a).ok() && Parties.of(a) == null && Parties.of(b) == null, "disbanded");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void theLeadIsHandedOnAndAPartyOfOneEnds(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer a = hunter(helper, "party_a", new BlockPos(1, 2, 1));
        ServerPlayer b = hunter(helper, "party_b", new BlockPos(5, 2, 1));
        ServerPlayer c = hunter(helper, "party_c", new BlockPos(1, 2, 5));
        ServerPlayer d = hunter(helper, "party_d", new BlockPos(5, 2, 5));
        Party party = form(a, b, c);
        helper.assertTrue(party != null && party.size() == 3 && party.members().equals(List.of(a.getUUID(), b.getUUID(), c.getUUID())),
                "three, in the order they joined");
        helper.assertTrue(key(Parties.kick(b, c.getUUID())).equals("party.beyondsolo.not_leader"), "only the leader removes");
        helper.assertTrue(key(Parties.kick(a, a.getUUID())).equals("party.beyondsolo.kick.self"), "not themselves");
        helper.assertTrue(key(Parties.promote(a, b.getUUID())).equals("party.beyondsolo.promoted") && party.isLeader(b.getUUID()),
                "the lead handed on");
        helper.assertTrue(Parties.kick(b, c.getUUID()).ok() && Parties.of(c) == null && party.size() == 2, "removed");

        // the leader leaves: the lead goes to the first member still on the server
        Parties.invite(b, c);
        Parties.accept(c, b.getUUID());
        helper.assertTrue(Parties.leave(b).ok() && Parties.of(b) == null, "left");
        helper.assertTrue(party.isLeader(a.getUUID()) && party.size() == 2, "a leads again");

        // joining another party leaves the old one, which (one member left) is no more
        Parties.invite(d, c);
        helper.assertTrue(Parties.accept(c, d.getUUID()).ok(), "c goes with d");
        helper.assertTrue(Parties.of(a) == null && Parties.data(server).get(party.id()) == null, "the old party ended");
        Party second = Parties.of(d);
        helper.assertTrue(second != null && second.isLeader(d.getUUID()) && Parties.together(c, d), "a new party, led by d");
        helper.assertTrue(key(Parties.leave(a)).equals("party.beyondsolo.none"), "a is in none");

        // the last two: one leaves and the party ends
        helper.assertTrue(Parties.leave(c).ok() && Parties.of(d) == null && Parties.data(server).get(second.id()) == null, "a party of one ends");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void partiesOutlastARestart(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var registries = helper.getLevel().registryAccess();
        ServerPlayer a = hunter(helper, "party_a", new BlockPos(1, 2, 1));
        ServerPlayer b = hunter(helper, "party_b", new BlockPos(5, 2, 1));
        ServerPlayer c = hunter(helper, "party_c", new BlockPos(1, 2, 5));
        Party party = form(a, b, c);
        Parties.promote(a, c.getUUID());
        PartyData loaded = PartyData.load(Parties.data(server).save(new CompoundTag(), registries), registries);
        Party back = loaded.get(party.id());
        helper.assertTrue(back != null && back.members().equals(party.members()) && back.isLeader(c.getUUID()), "the same party, the same lead");
        helper.assertTrue(back.name(b.getUUID()).equals("party_b") && loaded.of(b.getUUID()) == back, "names and members kept");

        // a damaged save: a hunter found in two parties stays in the first, and a party of one is dropped
        UUID x = UUID.randomUUID(), y = UUID.randomUUID(), z = UUID.randomUUID();
        ListTag list = new ListTag();
        list.add(partyTag(x, x, y));
        list.add(partyTag(y, y, z));
        list.add(partyTag(z, z));
        CompoundTag bad = new CompoundTag();
        bad.put("parties", list);
        PartyData fixed = PartyData.load(bad, registries);
        helper.assertTrue(fixed.all().size() == 1 && fixed.of(x) != null && fixed.of(x).has(y) && fixed.of(z) == null,
                "cleaned up: " + fixed.all().size() + " parties");
        Parties.disband(c);
        helper.succeed();
    }

    private static CompoundTag partyTag(UUID leader, UUID... members) {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", UUID.randomUUID());
        t.putUUID("leader", leader);
        ListTag list = new ListTag();
        for (UUID m : members) {
            CompoundTag e = new CompoundTag();
            e.putUUID("id", m);
            e.putString("name", "hunter");
            list.add(e);
        }
        t.put("members", list);
        return t;
    }

    @GameTest(template = ARENA)
    public static void thePartyReachesTheClientWhole(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        PartySyncPayload sync = new PartySyncPayload(true, a, List.of(
                new PartySyncPayload.Member(a, "Kael", true, true, 3, 2, -1, 24, 13.5f, 20f, PartySyncPayload.WORLD, -1,
                        Component.empty(), Level.OVERWORLD.location(), 12.5, 64, -30.25),
                new PartySyncPayload.Member(b, "Mira", true, false, -1, -1, -1, 0, 5f, 20f, PartySyncPayload.GATE, 1,
                        Component.literal("Ashen Hollow"), GateService.DIMENSION.location(), 4096.5, 40, 0)),
                List.of(new PartySyncPayload.Invite(b, "Sora", 3, 840)), 6, -1);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        PartySyncPayload.STREAM_CODEC.encode(buf, sync);
        PartySyncPayload back = PartySyncPayload.STREAM_CODEC.decode(buf);
        helper.assertTrue(back.equals(sync), "the same on the other side: " + back);
        helper.assertTrue(buf.readableBytes() == 0, "nothing left over");
        PartySyncPayload none = PartySyncPayload.none(List.of(), 6, 1200);
        PartySyncPayload.STREAM_CODEC.encode(buf, none);
        helper.assertTrue(PartySyncPayload.STREAM_CODEC.decode(buf).equals(none), "no party, a regroup clock");

        ByteBuf raw = Unpooled.buffer();
        PartyActionPayload act = new PartyActionPayload(PartyActionPayload.REGROUP, b);
        PartyActionPayload.STREAM_CODEC.encode(raw, act);
        helper.assertTrue(PartyActionPayload.STREAM_CODEC.decode(raw).equals(act), "a button");
        PartyEventPayload ev = new PartyEventPayload(PartyEventPayload.OUTCOME, 1, Component.translatable("party.beyondsolo.joined", Component.literal("Kael")));
        PartyEventPayload.STREAM_CODEC.encode(buf, ev);
        helper.assertTrue(PartyEventPayload.STREAM_CODEC.decode(buf).equals(ev), "and its answer");
        helper.succeed();
    }

    // ------------------------------------------------------------------ fighting together

    @GameTest(template = ARENA)
    public static void killsSideBySideAreShared(GameTestHelper helper) {
        ServerPlayer a = hunter(helper, "party_a", new BlockPos(1, 2, 1));
        ServerPlayer b = hunter(helper, "party_b", new BlockPos(5, 2, 1));
        ServerPlayer c = hunter(helper, "party_c", new BlockPos(1, 2, 5));
        for (ServerPlayer p : List.of(a, b, c)) SystemAPI.awaken(p, PlayerRank.E, PlayerClass.WARRIOR);
        form(a, b);
        Zombie z = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
        helper.assertTrue(Parties.sharers(a, z).equals(List.of(b)), "the party member beside them shares it, the stranger doesn't");
        b.moveTo(b.getX() + 200, b.getY(), b.getZ());
        helper.assertTrue(Parties.sharers(a, z).isEmpty(), "not from far away");
        b.moveTo(b.getX() - 200, b.getY(), b.getZ());
        helper.assertTrue(Parties.share(10) == Math.round(10 * PartyConfig.KILL_SHARE.get()) && Parties.share(1) == 1 && Parties.share(0) == 0,
                "a part of it, at least one: " + Parties.share(10));

        long goldA = Economy.gold(a), goldB = Economy.gold(b), goldC = Economy.gold(c);
        SystemData db = SystemAPI.get(b), dc = SystemAPI.get(c);
        int levelB = db.level(), levelC = dc.level();
        long xpB = db.xp(), xpC = dc.xp();
        z.hurt(a.damageSources().playerAttack(a), 1000f);
        helper.assertTrue(!z.isAlive(), "slain");
        helper.assertTrue(Economy.gold(a) > goldA, "the killer's gold");
        helper.assertTrue(Economy.gold(b) > goldB, "a share of it for the party member");
        helper.assertTrue(Economy.gold(c) == goldC, "none for the stranger");
        db = SystemAPI.get(b);
        dc = SystemAPI.get(c);
        helper.assertTrue(db.level() > levelB || db.xp() > xpB, "and a share of the XP");
        helper.assertTrue(dc.level() == levelC && dc.xp() == xpC, "none for the stranger either");
        Parties.disband(a);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void partyMembersCantHurtEachOther(GameTestHelper helper) {
        ServerPlayer a = hunter(helper, "party_a", new BlockPos(1, 2, 1));
        ServerPlayer b = hunter(helper, "party_b", new BlockPos(5, 2, 1));
        ServerPlayer c = hunter(helper, "party_c", new BlockPos(1, 2, 5));
        form(a, b);
        helper.assertTrue(!PartyConfig.FRIENDLY_FIRE.get(), "friendly fire is off by default");
        helper.assertTrue(blocked(b, a.damageSources().playerAttack(a)), "a party member's blow is turned aside");
        helper.assertTrue(!blocked(b, c.damageSources().playerAttack(c)), "a stranger's lands");

        Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
        arrow.setOwner(a);
        helper.assertTrue(a.getUUID().equals(Parties.culprit(arrow)), "an arrow is its archer's");
        helper.assertTrue(blocked(b, helper.getLevel().damageSources().arrow(arrow, a)), "so its hit is turned aside too");

        Wolf wolf = helper.spawnWithNoFreeWill(EntityType.WOLF, new BlockPos(3, 2, 3));
        wolf.setTame(true, false);
        wolf.setOwnerUUID(b.getUUID());
        helper.assertTrue(b.getUUID().equals(Parties.culprit(wolf)), "a pet's bite is its owner's");
        helper.assertTrue(blocked(wolf, a.damageSources().playerAttack(a)), "nor are each other's pets and shadows hurt");
        helper.assertTrue(!blocked(wolf, c.damageSources().playerAttack(c)), "a stranger can");

        Parties.disband(a);
        helper.assertTrue(!blocked(b, a.damageSources().playerAttack(a)), "out of the party, blows land again");
        wolf.discard();
        helper.succeed();
    }

    private static boolean blocked(LivingEntity victim, DamageSource source) {
        return NeoForge.EVENT_BUS.post(new LivingIncomingDamageEvent(victim, new DamageContainer(source, 4f))).isCanceled();
    }

    // ------------------------------------------------------------------ gates wait for the party

    @GameTest(template = ARENA)
    public static void aGateHoldsForThePartyOnTheWay(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer a = hunter(helper, "party_a", new BlockPos(1, 2, 1));
        ServerPlayer b = hunter(helper, "party_b", new BlockPos(5, 2, 1));
        ServerPlayer c = hunter(helper, "party_c", new BlockPos(1, 2, 5));
        UUID gate = UUID.randomUUID();
        BlockPos at = helper.absolutePos(new BlockPos(3, 2, 3));
        ResourceKey<Level> dim = helper.getLevel().dimension();
        helper.assertTrue(!Parties.holdFor(server, gate, dim, at, List.of(a.getUUID()), 0), "nobody to wait for out of a party");
        form(a, c);
        c.moveTo(c.getX() + 200, c.getY(), c.getZ());
        helper.assertTrue(!Parties.holdFor(server, gate, dim, at, List.of(a.getUUID()), 0), "nor for one far away");
        Parties.disband(a);

        form(a, b);
        helper.assertTrue(Parties.followers(server, dim, at, List.of(a.getUUID())).equals(List.of(b)), "b is on the way");
        helper.assertTrue(Parties.holdFor(server, gate, dim, at, List.of(a.getUUID()), 0), "the gate holds for them");
        helper.assertTrue(!Parties.holdFor(server, gate, dim, at, List.of(a.getUUID()), PartyConfig.followTicks()), "but not forever");
        helper.assertTrue(!Parties.holdFor(server, gate, dim, at, List.of(a.getUUID(), b.getUUID()), 0), "nor once they are in");
        Parties.disband(a);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void aKeyedGatesDoorWaitsForTheParty(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer a = hunter(helper, "party_a", new BlockPos(3, 2, 0));
        ServerPlayer b = hunter(helper, "party_b", new BlockPos(1, 2, 1));
        SystemAPI.awaken(a, PlayerRank.E, PlayerClass.WARRIOR);
        SystemAPI.awaken(b, PlayerRank.E, PlayerClass.MAGE);
        DungeonKeys.data(a).reset();
        for (int x = 0; x < 7; x++) for (int y = 2; y < 7; y++) for (int z = 0; z < 7; z++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        GateBlockEntity door = DungeonKeys.open(a, DungeonKeys.key(PlayerRank.E),
                new DungeonKeys.Spot(helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 3)), Direction.NORTH));
        GateInstance g = GateData.get(server).get(door.gateId());
        form(a, b);
        long now = server.overworld().getGameTime();
        g.keyed().inside.add(a.getUUID());
        g.keyed().firstEntryAt = now - DungeonKeys.SEAL_TICKS;
        KeyedRuns.tick(server, 20);
        helper.assertTrue(!g.keyed().doorClosed && !door.isClosing(), "due to seal, but held open for the party member on the way");
        g.keyed().firstEntryAt = now - DungeonKeys.SEAL_TICKS - PartyConfig.followTicks();
        KeyedRuns.tick(server, 20);
        helper.assertTrue(g.keyed().doorClosed && door.isClosing(), "a while, then it shuts all the same");
        g.keyed().inside.clear();
        KeyedRuns.tick(server, 20);
        Parties.disband(a);
        helper.succeed();
    }

    // ------------------------------------------------------------------ getting together

    @GameTest(template = ARENA)
    public static void regroupingTakesYouToYourParty(GameTestHelper helper) {
        ServerPlayer a = hunter(helper, "party_a", new BlockPos(1, 2, 1));
        ServerPlayer b = hunter(helper, "party_b", new BlockPos(5, 2, 1));
        ServerPlayer c = hunter(helper, "party_c", new BlockPos(1, 2, 5));
        helper.assertTrue(key(Regroup.start(a, b.getUUID())).equals("party.beyondsolo.none"), "only in a party");
        form(a, b);
        helper.assertTrue(key(Regroup.start(a, c.getUUID())).equals("party.beyondsolo.not_member"), "only to a party member");
        helper.assertTrue(key(Regroup.start(a, a.getUUID())).equals("party.beyondsolo.regroup.self"), "not to themselves");
        helper.assertTrue(key(Regroup.start(a, b.getUUID())).equals("party.beyondsolo.regroup.near"), "not to someone right there");

        b.moveTo(b.getX() + 300, b.getY(), b.getZ());
        Parties.Outcome started = Regroup.start(a, b.getUUID());
        helper.assertTrue(started.ok() && Regroup.channeling(a), "gathering: " + key(started));
        helper.assertTrue(key(Regroup.start(a, b.getUUID())).equals("party.beyondsolo.regroup.already"), "once at a time");
        Regroup.cancel(a, false);
        helper.assertTrue(!Regroup.channeling(a), "broken off");

        helper.assertTrue(Regroup.travel(a, b), "travels");
        helper.assertTrue(a.distanceTo(b) < 3.5, "at their side: " + a.distanceTo(b));
        helper.assertTrue(Regroup.readyIn(a) > 0 && Regroup.readyIn(a) <= PartyConfig.regroupCooldownTicks(), "and waits before the next");
        b.moveTo(b.getX() - 300, b.getY(), b.getZ());
        helper.assertTrue(key(Regroup.start(a, b.getUUID())).equals("party.beyondsolo.regroup.wait"), "not again so soon");

        // stepping away while gathering breaks it
        helper.assertTrue(Regroup.start(b, a.getUUID()).ok() && Regroup.channeling(b), "b gathers to go back");
        b.moveTo(b.getX() + 2, b.getY(), b.getZ());
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(!Regroup.channeling(b), "stepping away broke it");
            Parties.disband(a);
            helper.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void friendsLandSideBySide(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LandingData landing = new LandingData();
        helper.assertTrue(landing.near(level, RandomSource.create(1)) == null, "the first to join lands wherever");
        BlockPos first = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                helper.absolutePos(new BlockPos(3, 2, 3)).offset(40, 0, 40));
        landing.set(first);
        RandomSource random = RandomSource.create(7);
        for (int i = 0; i < 6; i++) {
            BlockPos next = landing.near(level, random);
            double dx = next.getX() - first.getX(), dz = next.getZ() - first.getZ();
            helper.assertTrue(Math.sqrt(dx * dx + dz * dz) <= 8.5 && Math.abs(next.getY() - first.getY()) <= 6,
                    "everyone after lands beside them: " + next + " vs " + first);
        }
        CompoundTag saved = landing.save(new CompoundTag(), level.registryAccess());
        helper.assertTrue(saved.contains("landing"), "kept with the world");
        helper.succeed();
    }

    // ------------------------------------------------------------------ the awakening, together

    @GameTest(template = ARENA)
    public static void aFriendWithoutTheSystemStillFindsTheDoor(GameTestHelper helper) {
        ServerPlayer hunter = hunter(helper, "party_hunter", new BlockPos(1, 2, 1));
        SystemAPI.awaken(hunter, PlayerRank.D, PlayerClass.WARRIOR);
        ServerPlayer alone = hunter(helper, "party_alone", new BlockPos(5, 2, 1));
        SystemAPI.awaken(alone, PlayerRank.D, PlayerClass.RANGER);
        GateInstance solo = gate(helper, PlayerRank.D);
        AwakeningService.enteringGate(alone, solo);
        helper.assertTrue(solo.depths().hidden(solo) != HiddenKind.SEALED, "a gate someone with the System walks into hides no door");

        ServerPlayer friend = hunter(helper, "party_friend", new BlockPos(1, 2, 5));
        form(hunter, friend);
        GateInstance found = null;
        for (int i = 0; i < 8 && found == null; i++) {
            GateInstance g = gate(helper, PlayerRank.D);
            AwakeningService.enteringGate(hunter, g);
            if (g.depths().hidden(g) == HiddenKind.SEALED) found = g;
        }
        helper.assertTrue(found != null, "but with a friend still waiting for their door beside them, it hides it");
        Parties.disband(hunter);
        helper.succeed();
    }

    private static GateInstance gate(GameTestHelper helper, PlayerRank rank) {
        GateBlockEntity gate = GateService.spawnGate(helper.getLevel(), helper.absolutePos(new BlockPos(5, 2, 5)), rank, Direction.NORTH, false);
        return GateData.get(helper.getLevel().getServer()).get(gate.gateId());
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void aPartyGoesThroughOneSealedDoorTogether(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel dungeon = GateService.dungeon(server);
        ServerPlayer a = hunter(helper, "party_a", new BlockPos(1, 2, 1));
        // b waits in the cave (a test player stays in the level it was made in)
        ServerPlayer b = hunter(helper, dungeon, "party_b", new BlockPos(5, 2, 1));
        ServerPlayer stranger = hunter(helper, "party_c", new BlockPos(1, 2, 5));
        form(a, b);

        GateInstance cave = null;
        for (int i = 0; i < 8 && cave == null; i++) {
            GateInstance g = gate(helper, PlayerRank.D);
            AwakeningService.enteringGate(a, g);
            if (g.depths().hidden(g) == HiddenKind.SEALED) cave = g;
        }
        helper.assertTrue(cave != null, "a D-rank cave hides the door");
        GateService.buildNow(server, cave);
        AwakeningService.gateCleared(dungeon, cave, List.of(a, b));
        helper.assertTrue(AwakeningService.stage(a) == AwakeningData.Stage.CALLED && AwakeningService.stage(b) == AwakeningData.Stage.CALLED,
                "the boss falls and calls them both");
        DungeonLayout.Room chamber = cave.layout().hidden();
        BlockPos floor = DepthsBuild.floorCenter(dungeon, cave, chamber);
        b.moveTo(floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5);
        AwakeningService.revealDoors(dungeon, cave, List.of(b, a));

        AwakeningData da = AwakeningService.data(a), db = AwakeningService.data(b);
        helper.assertTrue(da.trial() != null && da.trial().equals(db.trial()) && da.sealedPos() != null && da.sealedPos().equals(db.sealedPos()),
                "one door for the party");
        BlockPos second = DepthsBuild.sealedDoor(dungeon, cave, 1).pos();
        helper.assertTrue(!(dungeon.getBlockEntity(second) instanceof GateBlockEntity), "not one each");
        GateInstance trial = GateData.get(server).get(da.trial());
        helper.assertTrue(trial != null && trial.isTrial() && a.getUUID().equals(trial.owner()), "opened for the party's leader");
        helper.assertTrue(AwakeningService.mayEnter(a, trial) && AwakeningService.mayEnter(b, trial), "it lets them both through");
        helper.assertTrue(!AwakeningService.mayEnter(stranger, trial), "nobody else");

        // a goes through first: the door stays open for b, still in the cave, and shuts behind the last of them
        BlockPos doorPos = da.sealedPos();
        helper.assertTrue(GateService.arrive(a, trial), "a goes through");
        helper.assertTrue(AwakeningService.stage(a) == AwakeningData.Stage.TRIAL, "a is in the trial");
        helper.assertTrue(dungeon.getBlockEntity(doorPos) instanceof GateBlockEntity open && !open.isClosing(), "the door waits for b");
        helper.assertTrue(GateService.arrive(b, trial), "b follows");
        helper.assertTrue(AwakeningService.stage(b) == AwakeningData.Stage.TRIAL, "b is in the trial");
        helper.assertTrue(dungeon.getBlockEntity(doorPos) instanceof GateBlockEntity shut && shut.isClosing(), "and it shuts behind them");
        Parties.disband(a);
        helper.succeed();
    }

    private PartyGameTests() {}
}
