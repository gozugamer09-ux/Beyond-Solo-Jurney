package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.chronicle.ChronicleModule;
import com.beyondsolo.gate.DungeonLayout;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.guild.Guild;
import com.beyondsolo.guild.GuildConfig;
import com.beyondsolo.guild.GuildHunter;
import com.beyondsolo.guild.GuildHunterEntity;
import com.beyondsolo.guild.GuildPerks;
import com.beyondsolo.guild.Guilds;
import com.beyondsolo.guild.Recruits;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestObjective;
import com.beyondsolo.raid.RaidConfig;
import com.beyondsolo.raid.RaidPylonEntity;
import com.beyondsolo.raid.RaidRole;
import com.beyondsolo.raid.RaidState;
import com.beyondsolo.raid.Raids;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Raids: the muster (joining in roles, the leader and the countdown, the gate sealing once it starts, a raid that
 * fails), the cave (lieutenants sealing the boss's chamber, the boss grown for its raiders, its barrier and the pylons
 * that hold it, its cataclysm and the lights that shelter from it, the clear and its titles), a guild's raid (its
 * hunters coming along, fighting beside the raiders and growing from it, and the guild's share), what the roles do to
 * blows, and a raid kept over a restart.
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RaidGameTests {
    private static final String ARENA = "evaluation_zone";

    /** Opens a gate of {@code rank} in the arena and makes it a raid gate (a guild's permit raid if {@code guild} is set). */
    private static GateInstance raidGate(GameTestHelper helper, BlockPos at, PlayerRank rank, @Nullable UUID guild) {
        GateBlockEntity gate = GateService.spawnGate(helper.getLevel(), helper.absolutePos(at), rank, Direction.NORTH, false);
        GateInstance g = GateData.get(helper.getLevel().getServer()).get(gate.gateId());
        Raids.makeRaid(helper.getLevel(), gate, g, guild);
        return g;
    }

    private static GateBlockEntity block(GameTestHelper helper, GateInstance g) {
        return (GateBlockEntity) helper.getLevel().getBlockEntity(g.gatePos());
    }

    private static String key(Raids.Outcome out) {
        return GuildGameTests.key(out.message());
    }

    /** The chunks within {@code r} of the one holding {@code p}. */
    private static List<ChunkPos> around(BlockPos p, int r) {
        ChunkPos c = new ChunkPos(p);
        List<ChunkPos> out = new ArrayList<>();
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) out.add(new ChunkPos(c.x + x, c.z + z));
        return out;
    }

    private static void force(ServerLevel level, List<ChunkPos> chunks, boolean on) {
        for (ChunkPos c : chunks) level.setChunkForced(c.x, c.z, on);
    }

    @Nullable
    private static Quest raidQuest(ServerPlayer p, GateInstance g) {
        for (Quest q : QuestEngine.data(p).active()) {
            if (Raids.QUEST_TEMPLATE.equals(q.template()) && g.id().toString().equals(q.extra().getString("gate"))) return q;
        }
        return null;
    }

    private static QuestObjective objective(Quest q, String target) {
        return q.objectives().stream().filter(o -> o.target().equals(target)).findFirst().orElseThrow();
    }

    private static List<RaidPylonEntity> pylons(ServerLevel d, GateInstance g, GateBossEntity boss) {
        return d.getEntitiesOfClass(RaidPylonEntity.class, boss.getBoundingBox().inflate(64, 24, 64), p -> g.id().equals(p.gateId()) && p.isAlive());
    }

    @Nullable
    private static GuildHunterEntity body(ServerLevel d, RaidState s, GuildHunter h) {
        RaidState.Hired hired = s.hired(h.id());
        return hired == null || hired.entity == null ? null : d.getEntity(hired.entity) instanceof GuildHunterEntity e && e.isAlive() ? e : null;
    }

    /** Standing room at or near {@code p} (two blocks of air over a solid floor), or null. */
    @Nullable
    private static BlockPos stand(ServerLevel d, BlockPos p) {
        for (int dy = 3; dy >= -4; dy--) {
            BlockPos q = p.offset(0, dy, 0);
            if (d.getBlockState(q).getCollisionShape(d, q).isEmpty() && d.getBlockState(q.above()).getCollisionShape(d, q.above()).isEmpty()
                    && d.getBlockState(q.below()).isFaceSturdy(d, q.below(), Direction.UP)) return q;
        }
        return null;
    }

    /** A spot on the boss chamber's floor well away from every light, or null. */
    @Nullable
    private static Vec3 openFloor(ServerLevel d, GateInstance g, List<Vec3> lights) {
        DungeonLayout.Room room = g.layout().boss();
        BlockPos c = g.bossRoomCenter();
        for (int ring = 2; ring <= room.reach(); ring += 2) {
            for (int k = 0; k < 16; k++) {
                double a = k * Math.PI / 8;
                BlockPos at = stand(d, BlockPos.containing(c.getX() + 0.5 + Math.cos(a) * ring, c.getY(), c.getZ() + 0.5 + Math.sin(a) * ring));
                if (at == null) continue;
                Vec3 v = Vec3.atBottomCenterOf(at);
                boolean clear = true;
                for (Vec3 l : lights) if (Math.hypot(v.x - l.x, v.z - l.z) < Raids.REFUGE_RADIUS + 1.5) clear = false;
                if (clear) return v;
            }
        }
        return null;
    }

    /** What a blow of {@code amount} comes to once everything that weighs on incoming damage has had its say. */
    private static float incoming(LivingEntity victim, DamageSource source, float amount) {
        LivingIncomingDamageEvent event = new LivingIncomingDamageEvent(victim, new DamageContainer(source, amount));
        NeoForge.EVENT_BUS.post(event);
        return event.isCanceled() ? 0f : event.getAmount();
    }

    // ------------------------------------------------------------------ the muster

    @GameTest(template = ARENA)
    public static void raidersMusterInRolesAndTheLeaderTakesThemIn(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GateInstance g = raidGate(helper, new BlockPos(5, 2, 5), PlayerRank.C, null);
        RaidState s = g.raid();
        helper.assertTrue(g.isRaid() && s.phase == RaidState.Phase.MUSTER && s.raiders.isEmpty() && s.leader == null && s.bringHunters,
                "a raid gate opens on its muster");
        ServerPlayer none = GuildGameTests.hunter(helper, "raid_none", new BlockPos(1, 2, 5), null);
        helper.assertTrue(key(Raids.join(none, g)).equals("raid.beyondsolo.need_system"), "only hunters with the System muster");

        ServerPlayer a = GuildGameTests.hunter(helper, "raid_a", new BlockPos(1, 2, 1), PlayerClass.WARRIOR);
        ServerPlayer b = GuildGameTests.hunter(helper, "raid_b", new BlockPos(3, 2, 1), PlayerClass.GUARDIAN);
        ServerPlayer c = GuildGameTests.hunter(helper, "raid_c", new BlockPos(5, 2, 1), PlayerClass.HEALER);
        Raids.Outcome joined = Raids.join(a, g);
        helper.assertTrue(joined.ok() && key(joined).equals("raid.beyondsolo.joined") && a.getUUID().equals(s.leader), "the first to muster leads");
        helper.assertTrue(Raids.join(b, g).ok() && Raids.join(c, g).ok() && s.raiders.size() == 3, "three muster");
        helper.assertTrue(s.raider(a.getUUID()).role == RaidRole.STRIKER && s.raider(b.getUUID()).role == RaidRole.VANGUARD
                && s.raider(c.getUUID()).role == RaidRole.SUPPORT, "each in their class's role: warrior striker, guardian vanguard, healer support");
        helper.assertTrue(key(Raids.join(b, g)).equals("raid.beyondsolo.already"), "b is on it already");
        helper.assertTrue(Raids.raidOf(server, b.getUUID()) == g, "b's raid");
        helper.assertTrue(key(Raids.start(b)).equals("raid.beyondsolo.not_leader") && key(Raids.bring(b, false)).equals("raid.beyondsolo.not_leader"),
                "only the leader starts it, or decides on the hunters");
        helper.assertTrue(Raids.role(b, RaidRole.STRIKER).ok() && s.raider(b.getUUID()).role == RaidRole.STRIKER, "anyone may take another role");
        Raids.role(b, RaidRole.VANGUARD);

        // the countdown
        Raids.Outcome started = Raids.start(a);
        helper.assertTrue(started.ok() && s.phase == RaidState.Phase.COUNTDOWN && s.countdownEnd > helper.getLevel().getGameTime(), "the countdown runs");
        helper.assertTrue(key(Raids.start(a)).equals("raid.beyondsolo.started"), "once");
        // someone on one raid can't muster for another
        GateInstance other = raidGate(helper, new BlockPos(1, 2, 3), PlayerRank.D, null);
        helper.assertTrue(key(Raids.join(b, other)).equals("raid.beyondsolo.other_raid"), "one raid at a time");
        // the leader leaves: the lead passes on and the countdown stops
        helper.assertTrue(Raids.leave(a).ok() && !s.has(a.getUUID()) && b.getUUID().equals(s.leader) && s.phase == RaidState.Phase.MUSTER && s.countdownEnd < 0,
                "b leads now, and the countdown waits for them");
        helper.assertTrue(key(Raids.leave(a)).equals("raid.beyondsolo.not_in"), "a is on no raid");

        // the new leader takes them in at once
        helper.assertTrue(Raids.startNow(b).ok() && s.phase == RaidState.Phase.RUNNING && s.startedAt >= 0 && s.raiders.size() == 2, "in they go, b and c");
        helper.assertTrue(s.weight == 1f, "the raid is made for two: " + s.weight);
        // from now on the gate is theirs alone
        helper.assertTrue(key(Raids.join(a, g)).equals("raid.beyondsolo.sealed"), "nobody new musters");
        GateBlockEntity gate = block(helper, g);
        helper.assertTrue(Raids.enter(a, gate, g), "the gate turns a away");
        helper.assertTrue(!Raids.enter(b, gate, g), "and lets its raiders through");
        helper.assertTrue(key(Raids.leave(b)).equals("raid.beyondsolo.leave.running") && key(Raids.role(b, RaidRole.SUPPORT)).equals("raid.beyondsolo.role.running"),
                "no leaving the muster or changing roles once inside");

        // everyone fell or left: the raid fails and its gate shuts
        Raids.fail(server, g);
        helper.assertTrue(s.phase == RaidState.Phase.FAILED && g.state() == GateInstance.State.CLOSED, "failed, and the gate shuts");
        helper.assertTrue(Raids.raidOf(server, b.getUUID()) == null, "nobody is on it any more");
        helper.assertTrue(Raids.enter(b, gate, g), "a failed raid lets nobody in");
        helper.succeed();
    }

    // ------------------------------------------------------------------ the cave

    @GameTest(template = ARENA, timeoutTicks = 1200)
    public static void lieutenantsSealTheChamberAndTheBossFightsInPhases(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel d = GateService.dungeon(server);
        ServerPlayer a = GuildGameTests.hunter(helper, "raid_a", new BlockPos(1, 2, 1), PlayerClass.WARRIOR);
        ServerPlayer b = GuildGameTests.hunter(helper, "raid_b", new BlockPos(3, 2, 1), PlayerClass.HEALER);
        GateInstance g = raidGate(helper, new BlockPos(5, 2, 5), PlayerRank.C, null);
        RaidState s = g.raid();
        Raids.join(a, g);
        Raids.join(b, g);
        helper.assertTrue(Raids.startNow(a).ok() && s.phase == RaidState.Phase.RUNNING, "underway");
        GateService.buildNow(server, g);
        int count = Raids.lieutenantCount(g);
        helper.assertTrue(count >= 1 && s.lieutenantsTotal == count && s.lieutenants.size() == count,
                "its lieutenants take their caverns: " + s.lieutenants.size() + " of " + count + " in " + g.realm().id() + "/" + g.shape().id());
        helper.assertTrue(Raids.sealed(g), "and seal the boss's chamber");

        List<ChunkPos> chunks = new ArrayList<>(around(g.bossRoomCenter(), 2));
        for (BlockPos post : Raids.lieutenantPosts(g)) chunks.addAll(around(post, 1));
        chunks.addAll(around(g.entrance(), 1));
        force(d, chunks, true);
        TestChunks.ticking(d, chunks);
        helper.assertTrue(GateService.arrive(a, g) && GateService.arrive(b, g), "both go through");
        helper.assertTrue(s.raider(a.getUUID()).status == RaidState.Status.INSIDE && s.raider(b.getUUID()).status == RaidState.Status.INSIDE, "counted in");
        Quest quest = raidQuest(a, g);
        helper.assertTrue(quest != null && objective(quest, Raids.LIEUTENANT_KEY).required() == count, "the raid's quest: its lieutenants, then its boss");

        TestChunks.settle(d, () -> g.boss() != null && d.getEntity(g.boss()) instanceof GateBossEntity
                && s.lieutenants.stream().allMatch(id -> d.getEntity(id) instanceof GateBossEntity));
        helper.assertTrue(g.boss() != null && d.getEntity(g.boss()) instanceof GateBossEntity, "the boss waits");
        GateBossEntity boss = (GateBossEntity) d.getEntity(g.boss());
        Raids.tickNow(server, g);
        helper.assertTrue(s.bossScaled && Raids.controls(boss), "grown for its raiders, and fighting whom the raid picks");
        for (UUID id : List.copyOf(s.lieutenants)) {
            helper.assertTrue(d.getEntity(id) instanceof GateBossEntity lt && g.id().equals(lt.getPersistentData().getUUID(Raids.LIEUTENANT_TAG))
                    && Raids.controls(lt) && !lt.isAwake(), "a lieutenant of this raid, waiting in its cavern");
        }
        // the lieutenants fall one by one; with the last the seal lifts (and none of them clears the gate)
        for (UUID id : List.copyOf(s.lieutenants)) {
            ((GateBossEntity) d.getEntity(id)).kill();
            helper.assertTrue(!s.lieutenants.contains(id), "a lieutenant down");
        }
        helper.assertTrue(!Raids.sealed(g) && !g.bossDefeated() && g.state() == GateInstance.State.OPEN, "the chamber opens");
        helper.assertTrue(objective(quest, Raids.LIEUTENANT_KEY).progress() == count, "the quest counts them");

        GateService.wakeBossNow(d, g);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(boss.introTicks() <= 0, "the intro is over: " + boss.introTicks()))
                .thenExecute(() -> {
                    // at 70% its barrier rises, held up by pylons
                    helper.assertTrue(Raids.forcePhase(a, "barrier").ok() && s.barrierUp && Raids.barrierUp(server, g.id()), "the barrier is up");
                    helper.assertTrue(s.pylonsTotal == RaidConfig.PYLONS.get() && s.pylonsLeft == s.pylonsTotal, "three pylons hold it");
                    List<RaidPylonEntity> standing = pylons(d, g, boss);
                    helper.assertTrue(standing.size() == s.pylonsTotal, "standing in the chamber: " + standing.size());
                    float hp = boss.getHealth();
                    boss.hurt(d.damageSources().playerAttack(a), 50f);
                    helper.assertTrue(boss.getHealth() == hp, "nothing gets through it");
                    RaidPylonEntity first = standing.get(0);
                    int left = first.hitsLeft();
                    helper.assertTrue(!first.hurt(d.damageSources().generic(), 5f) && first.hitsLeft() == left, "a stray blow doesn't count");
                    helper.assertTrue(first.hurt(d.damageSources().playerAttack(a), 1f) && first.hitsLeft() == left - 1, "a raider's does");
                    helper.assertTrue(!first.hurt(d.damageSources().playerAttack(b), 1f) && first.hitsLeft() == left - 1, "two in the same moment are one");
                })
                .thenWaitUntil(() -> {
                    // the raiders break every one of them
                    for (RaidPylonEntity p : pylons(d, g, boss)) p.hurt(d.damageSources().playerAttack(a), 1f);
                    helper.assertTrue(s.pylonsLeft == 0, "pylons standing: " + s.pylonsLeft);
                })
                .thenExecute(() -> {
                    helper.assertTrue(!s.barrierUp && s.barrierDone && !Raids.barrierUp(server, g.id()), "the barrier falls with its last pylon");
                    helper.assertTrue(boss.staggered(), "and the boss reels");
                    float hp = boss.getHealth();
                    boss.hurt(d.damageSources().playerAttack(a), 50f);
                    helper.assertTrue(boss.getHealth() < hp && Raids.dealt(g.id(), a.getUUID()) > 0, "blows land again, and count to a's name");

                    // from 25% the cataclysms: only the lights it leaves on the floor shelter from them
                    helper.assertTrue(Raids.forcePhase(a, "cataclysm").ok(), "a cataclysm comes");
                    List<Vec3> lights = Raids.refuges(g.id());
                    helper.assertTrue(lights.size() >= 2, "lights to shelter in: " + lights.size());
                    BlockPos c = g.bossRoomCenter();
                    for (Vec3 v : lights) {
                        helper.assertTrue(Math.hypot(v.x - c.getX(), v.z - c.getZ()) <= g.layout().boss().reach() + 1, "each in the chamber");
                    }
                    // and it falls
                    boss.hurt(d.damageSources().playerAttack(a), boss.getMaxHealth() * 10f);
                    helper.assertTrue(g.bossDefeated() && s.phase == RaidState.Phase.CLEARED, "the raid is cleared");
                    helper.assertTrue(ChronicleModule.data(a).hasTitle(Raids.BREAKER_TITLE) && ChronicleModule.data(b).hasTitle(Raids.BREAKER_TITLE),
                            "both raiders earn the raid's title");
                    helper.assertTrue(!ChronicleModule.data(a).hasTitle(Raids.MVP_TITLE), "a raid of two singles nobody out");
                    helper.assertTrue(Raids.raidOf(server, a.getUUID()) == null, "the raid is over");
                })
                .thenExecute(() -> force(d, chunks, false))
                .thenSucceed();
    }

    // ------------------------------------------------------------------ a guild's raid

    @GameTest(template = ARENA, timeoutTicks = 1200)
    public static void aGuildsHuntersFightBesideItsRaidersAndGrow(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel d = GateService.dungeon(server);
        ServerPlayer a = GuildGameTests.hunter(helper, "raidg_a", new BlockPos(1, 2, 1), PlayerClass.WARRIOR);
        ServerPlayer b = GuildGameTests.hunter(helper, "raidg_b", new BlockPos(3, 2, 1), PlayerClass.MAGE);
        Guild guild = GuildGameTests.found(helper, a, "Raid Test Wardens", "RTW");
        GuildHunter tank = Recruits.of(RandomSource.create(7), PlayerRank.E, PlayerClass.GUARDIAN);
        GuildHunter archer = Recruits.of(RandomSource.create(8), PlayerRank.D, PlayerClass.RANGER);
        Guilds.enlist(server, guild, tank);
        Guilds.enlist(server, guild, archer);
        int tankLevel = tank.level(), archerLevel = archer.level();

        GateInstance g = raidGate(helper, new BlockPos(5, 2, 5), PlayerRank.C, guild.id());
        RaidState s = g.raid();
        helper.assertTrue(guild.id().equals(s.guild), "the guild's own raid");
        Raids.join(a, g);
        Raids.join(b, g);
        helper.assertTrue(Raids.bring(a, false).ok() && !s.bringHunters && Raids.bring(a, true).ok() && s.bringHunters,
                "the leader decides whether the guild's hunters come");
        helper.assertTrue(Raids.startNow(a).ok(), "underway");
        helper.assertTrue(s.hunters.size() == 2 && s.hired(tank.id()) != null && s.hired(archer.id()) != null, "the guild's hunters come along");
        helper.assertTrue(s.weight == 2f, "made for two raiders and two hunters (half a raider each): " + s.weight);
        helper.assertTrue(Raids.hunterBusy(server, tank.id()) && GuildGameTests.key(Guilds.dismiss(a, tank.id())).equals("guild.beyondsolo.dismiss.raid"),
                "a hunter out on a raid can't be let go");

        GateService.buildNow(server, g);
        List<ChunkPos> chunks = new ArrayList<>(around(g.bossRoomCenter(), 2));
        chunks.addAll(around(g.entrance(), 1));
        force(d, chunks, true);
        TestChunks.ticking(d, chunks);
        GateService.arrive(a, g);
        GateService.arrive(b, g);
        TestChunks.settle(d, () -> g.boss() != null && d.getEntity(g.boss()) instanceof GateBossEntity);
        helper.assertTrue(g.boss() != null && d.getEntity(g.boss()) instanceof GateBossEntity, "the boss waits");
        GateBossEntity boss = (GateBossEntity) d.getEntity(g.boss());

        helper.startSequence()
                // a moment after the first raider is in, the hunters step through beside the leader
                .thenWaitUntil(() -> helper.assertTrue(body(d, s, tank) != null && body(d, s, archer) != null, "the hunters are in"))
                .thenExecute(() -> {
                    GuildHunterEntity t = body(d, s, tank), r = body(d, s, archer);
                    helper.assertTrue(tank.id().equals(t.hunterId()) && guild.id().equals(t.guildId()) && t.role() == RaidRole.VANGUARD
                            && r.role() == RaidRole.STRIKER, "each as who they are: the guardian a vanguard, the ranger a striker");
                    helper.assertTrue(Raids.keeps(t) && t.distanceTo(a) < 8, "beside the leader");
                    helper.assertTrue(!t.hurt(d.damageSources().playerAttack(a), 5f), "a raider's blow doesn't hurt them");

                    // a cataclysm: the light shelters, the open floor doesn't
                    helper.assertTrue(Raids.forcePhase(a, "cataclysm").ok(), "a cataclysm comes");
                    List<Vec3> lights = Raids.refuges(g.id());
                    helper.assertTrue(!lights.isEmpty(), "with its lights");
                    Vec3 open = openFloor(d, g, lights);
                    helper.assertTrue(open != null, "and open floor between them");
                    Vec3 safe = lights.get(0);
                    t.moveTo(safe.x, safe.y, safe.z);
                    r.moveTo(open.x, open.y, open.z);
                    float tHp = t.getHealth(), rHp = r.getHealth();
                    Raids.landNow(server, g);
                    helper.assertTrue(t.getHealth() == tHp, "the light shelters the tank");
                    helper.assertTrue(r.getHealth() < rHp && r.isAlive(), "the open floor doesn't spare the archer: " + rHp + " -> " + r.getHealth());
                    // back to the leader, mended, away from what the boss calls up
                    for (GuildHunterEntity e : List.of(t, r)) {
                        e.setHealth(e.getMaxHealth());
                        Raids.slipTo(e, a);
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(boss.introTicks() <= 0, "the boss's intro is over: " + boss.introTicks()))
                .thenExecute(() -> {
                    boss.hurt(d.damageSources().playerAttack(a), boss.getMaxHealth() * 10f);
                    helper.assertTrue(s.phase == RaidState.Phase.CLEARED, "cleared");
                    // two raiders and two hunters: a raid of four singles out who dealt the most
                    helper.assertTrue(ChronicleModule.data(a).hasTitle(Raids.MVP_TITLE) && !ChronicleModule.data(b).hasTitle(Raids.MVP_TITLE), "a, who felled it");
                    helper.assertTrue(guild.raidsCleared() == 1, "the guild counts the raid");
                    // a raid's guild XP for each member who was there (only a: b is in no guild)
                    long xp = GuildPerks.raidXp(PlayerRank.C);
                    int level = 1;
                    while (level < GuildConfig.MAX_LEVEL && xp >= GuildPerks.xpToNext(level)) xp -= GuildPerks.xpToNext(level++);
                    helper.assertTrue(guild.level() == level && guild.xp() == xp, "guild XP: level " + guild.level() + ", " + guild.xp());
                    // the hunters learn from it: the further the raid's rank is above theirs, the more (one to three levels)
                    helper.assertTrue(tank.raids() == 1 && tank.level() == tankLevel + 3, "the E-rank tank grows three levels: " + tankLevel + " -> " + tank.level());
                    helper.assertTrue(archer.raids() == 1 && archer.level() == archerLevel + 2, "the D-rank archer two: " + archerLevel + " -> " + archer.level());
                    helper.assertTrue(!Raids.hunterBusy(server, tank.id()), "free again");
                })
                .thenExecute(() -> {
                    force(d, chunks, false);
                    Guilds.disband(a);
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------ roles

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void strikersHitHarderAndVanguardsTakeLess(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel d = GateService.dungeon(server);
        ServerPlayer a = GuildGameTests.hunter(helper, "raid_a", new BlockPos(1, 2, 1), PlayerClass.WARRIOR);
        ServerPlayer b = GuildGameTests.hunter(helper, "raid_b", new BlockPos(3, 2, 1), PlayerClass.GUARDIAN);
        GateInstance g = raidGate(helper, new BlockPos(5, 2, 5), PlayerRank.E, null);
        RaidState s = g.raid();
        Raids.join(a, g);
        Raids.join(b, g);
        Raids.startNow(a);
        GateService.arrive(a, g);
        GateService.arrive(b, g);
        Zombie z = new Zombie(EntityType.ZOMBIE, d);
        z.moveTo(a.getX() + 1, a.getY(), a.getZ());

        float struck = incoming(z, d.damageSources().playerAttack(a), 10f);
        s.raider(a.getUUID()).role = RaidRole.SUPPORT;
        float plain = incoming(z, d.damageSources().playerAttack(a), 10f);
        helper.assertTrue(struck > plain * 1.05f, "a striker hits harder: " + struck + " against " + plain);

        float guarded = incoming(b, d.damageSources().mobAttack(z), 10f);
        s.raider(b.getUUID()).role = RaidRole.STRIKER;
        float bare = incoming(b, d.damageSources().mobAttack(z), 10f);
        helper.assertTrue(guarded < bare * 0.95f && guarded > 0, "a vanguard takes less: " + guarded + " against " + bare);

        // once the raid is over, roles count for nothing
        Raids.fail(server, g);
        s.raider(a.getUUID()).role = RaidRole.STRIKER;
        helper.assertTrue(Math.abs(incoming(z, d.damageSources().playerAttack(a), 10f) - plain) < 1e-3f, "no role outside a raid underway");
        helper.succeed();
    }

    // ------------------------------------------------------------------ saving

    @GameTest(template = ARENA)
    public static void raidsOutlastARestart(GameTestHelper helper) {
        // a copy of a raid gate's instance, so the server's own raids don't pick it up
        GateInstance g = GateInstance.load(raidGate(helper, new BlockPos(5, 2, 5), PlayerRank.B, null).save());
        RaidState s = g.raid();
        UUID guild = UUID.randomUUID();
        RaidState.Raider r = new RaidState.Raider(UUID.randomUUID(), "keeper", RaidRole.VANGUARD);
        r.status = RaidState.Status.FALLEN;
        r.cls = PlayerClass.GUARDIAN.ordinal();
        r.level = 31;
        s.raiders.put(r.id, r);
        s.leader = r.id;
        s.guild = guild;
        s.phase = RaidState.Phase.RUNNING;
        s.startedAt = 1234;
        s.bringHunters = false;
        RaidState.Hired h = new RaidState.Hired(guild, UUID.randomUUID(), "Ash Vale", PlayerClass.HEALER, 18, 3);
        h.fallen = true;
        h.kills = 7;
        s.hunters.add(h);
        UUID lt = UUID.randomUUID();
        s.lieutenants.add(lt);
        s.lieutenantsTotal = 3;
        s.weight = 2.5f;
        s.bossScaled = true;
        s.barrierDone = true;
        s.pylonsTotal = 4;
        s.pylonsLeft = 1;

        GateInstance back = GateInstance.load(g.save());
        RaidState t = back.raid();
        helper.assertTrue(back.isRaid() && t != null && t.phase == RaidState.Phase.RUNNING && t.startedAt == 1234 && !t.bringHunters
                && guild.equals(t.guild) && r.id.equals(t.leader), "the raid, its phase, its guild and its leader");
        RaidState.Raider rb = t.raider(r.id);
        helper.assertTrue(rb != null && rb.name.equals("keeper") && rb.role == RaidRole.VANGUARD && rb.status == RaidState.Status.FALLEN
                && rb.cls == PlayerClass.GUARDIAN.ordinal() && rb.level == 31, "its raiders, their roles and how they fared");
        RaidState.Hired hb = t.hired(h.hunter);
        helper.assertTrue(hb != null && hb.name.equals("Ash Vale") && hb.cls == PlayerClass.HEALER && hb.level == 18 && hb.skin == 3 && hb.fallen && hb.kills == 7
                && guild.equals(hb.guild) && hb.role() == RaidRole.SUPPORT, "its hunters and how they fared");
        helper.assertTrue(t.lieutenants.equals(List.of(lt)) && t.lieutenantsTotal == 3 && t.weight == 2.5f && t.bossScaled && t.barrierDone && !t.barrierUp
                && t.pylonsTotal == 4 && t.pylonsLeft == 1, "how far the fight got");
        helper.succeed();
    }

    private RaidGameTests() {}
}
