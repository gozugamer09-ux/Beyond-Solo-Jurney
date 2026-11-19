package com.beyondsolo.rival;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.association.Association;
import com.beyondsolo.association.AssociationData;
import com.beyondsolo.chronicle.ranking.Rankings;
import com.beyondsolo.chronicle.ranking.Rival;
import com.beyondsolo.chronicle.ranking.Rivals;
import com.beyondsolo.gate.DungeonLayout;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.gate.GateSpawner;
import com.beyondsolo.guild.GuildHunter;
import com.beyondsolo.guild.GuildHunterEntity;
import com.beyondsolo.guild.Recruits;
import com.beyondsolo.isle.IsleRaids;
import com.beyondsolo.network.RivalSyncPayload;
import com.beyondsolo.network.SkillFxPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.redgate.RedGates;
import com.beyondsolo.skill.SkillFx;
import com.beyondsolo.system.Balance;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.util.OnlinePlayers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The world's other hunters, racing the player for gates. Now and then a gate that opens near a hunter draws a party
 * (a leader of the national rankings with their guild's hunters, or a band of independents, all of the gate's rank):
 * they set out for it, reach it, gather in front of it and go in, and the association gives the gate's clearing rights
 * to whoever went in first. A hunter who gets there first beats them to it (most parties move on; a bold one comes in
 * after them, and it is a race to the boss, whoever deals it most taking its cache); a hunter who forces their way into
 * a gate a party holds pays for it in merit. While nobody watches a party is its record and its clocks
 * ({@link RivalParty}); its hunters ({@link RivalHunterEntity}) walk the world and the cave only while someone is near
 * enough to see them. A ranked leader's clears count on the national rankings.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class RivalParties {
    static final String KEY = "rival.beyondsolo.";
    /** A party's hunters are out in the world while a hunter is this near them (blocks, on the map). */
    static final int SEE = 96;
    /** Hunters this near a gate are told of a party setting out for it. */
    static final int TELL = 160;
    /** A party done this long ago is forgotten. */
    static final int FORGET = 20 * 60 * 5;
    /** Its hunters stay this long after it is done (walking off, or seeing the cave they cleared). */
    static final int WALK_OFF = 20 * 12;
    /** At most this long to walk from where they stand into the gate. */
    static final int WALK_IN = 20 * 8;
    /** The share of a clear that is the way to the boss's door (the rest is the boss). */
    public static final float ROUTE_SHARE = 0.8f;
    /** A party that won't make it pulls out about this far through. */
    static final float RETREAT_AT = 0.55f;
    /** A hunter of a rank this much higher than the party is someone they defer to. */
    static final int DEFER = 2;

    /** Each cave's way from the entrance to the boss, as its walkers take it. */
    private static final Map<UUID, Route> ROUTES = new HashMap<>();
    /** When each hunter last heard a line from a rival (a line a second and a half at most). */
    private static final Map<UUID, Long> TALKED = new HashMap<>();
    /** Parties nobody ever sees, whoever stands near (game tests: their hunters never step into other tests' plots). */
    static final Set<UUID> UNSEEN = ConcurrentHashMap.newKeySet();
    /** Hunters last told they know of no party (told again once they do). */
    private static final Set<UUID> QUIET = new HashSet<>();

    /** What a party's hunters fight for: their party's run at its gate. They keep near no hunter. */
    public static final GuildHunterEntity.Cause CAUSE = new GuildHunterEntity.Cause() {
        @Override
        public boolean keeps(GuildHunterEntity e) {
            if (!(e instanceof RivalHunterEntity r) || !(e.level() instanceof ServerLevel level) || r.party() == null) return false;
            RivalParty p = RivalData.get(level.getServer()).party(r.party());
            if (p == null || r.member() >= p.members.size()) return false;
            RivalParty.Member m = p.members.get(r.member());
            if (m.down || !e.getUUID().equals(m.body)) return false;
            GateInstance g = GateData.get(level.getServer()).get(p.gate);
            if (g == null) return false;
            long now = now(level.getServer());
            boolean cave = inCave(g, e);
            return switch (p.state) {
                case EN_ROUTE, GATHERING -> !cave && level.dimension().equals(g.gateDimension());
                case INSIDE -> cave || now - p.enteredAt < WALK_IN && level.dimension().equals(g.gateDimension());
                case DONE -> now - p.endedAt < WALK_OFF;
            };
        }

        @Override
        public @Nullable ServerPlayer leader(GuildHunterEntity e) {
            return null;
        }

        @Override
        public void fell(ServerLevel level, GuildHunterEntity e) {
            if (e instanceof RivalHunterEntity r) memberFell(level, r);
        }
    };

    /** The way through a cave: its points (where to stand, in the cave's level) and the room each is in (-1: a passage). */
    record Route(List<Vec3> points, int[] rooms) {
        int size() {
            return points.size();
        }
    }

    public static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    // ================================================================ setting out

    /** A gate opened near a hunter ({@link GateSpawner}): now and then another party hears of it and sets out for it. */
    public static void gateOpened(ServerLevel level, GateBlockEntity gate, ServerPlayer near, boolean first) {
        // a hunter's very first gate is theirs alone
        if (first || gate.gateId() == null || !SystemAPI.isAwakened(near)) return;
        GateInstance g = GateData.get(level.getServer()).get(gate.gateId());
        if (g == null || !eligible(level.getServer(), g)) return;
        if (level.random.nextDouble() >= RivalConfig.dispatchChance()) return;
        dispatch(level.getServer(), g, null, null, level.random);
    }

    /**
     * True for a gate a party may set out for: an open gate in the world that is nobody's yet (no raid, no trial, no
     * keyed run, no red gate in waiting, no association claim, no other party on it), not broken out.
     */
    public static boolean eligible(MinecraftServer server, GateInstance g) {
        if (g.state() != GateInstance.State.OPEN || g.isTrial() || g.isKeyed() || g.isRaid() || g.bossDefeated()) return false;
        if (!g.gateDimension().equals(Level.OVERWORLD) || g.breakState().hasBroken()) return false;
        if (RedGates.latent(server, g)) return false;
        if (AssociationData.get(server).claim(g.id()) != null) return false;
        return RivalData.get(server).forGate(g.id()) == null;
    }

    /**
     * Sends a party for a gate: led by {@code leader} (a hunter of the rankings), for {@code guild}, or (null) as it
     * falls out. Everyone awakened near the gate is told. Null if a party is already on it.
     */
    @Nullable
    public static RivalParty dispatch(MinecraftServer server, GateInstance g, @Nullable RivalGuild guild, @Nullable Rival leader, RandomSource r) {
        RivalData data = RivalData.get(server);
        if (data.forGate(g.id()) != null) return null;
        PlayerRank rank = g.rank();
        // one of the rankings' own hunters of the gate's rank leads it now and then, if one is free (and of the guild)
        if (leader == null && r.nextDouble() < RivalConfig.rankedLeader()) {
            List<Rival> free = new ArrayList<>();
            for (Rival v : Rivals.ALL) {
                if (v.rank() != rank || data.busy(v.id())) continue;
                if (guild != null && RivalGuild.of(v.guild()) != guild) continue;
                free.add(v);
            }
            if (!free.isEmpty()) leader = free.get(r.nextInt(free.size()));
        }
        if (guild == null) guild = leader != null ? RivalGuild.of(leader.guild()) : rollGuild(r);
        long now = now(server);
        List<RivalParty.Member> members = members(server, rank, leader, r);
        RivalParty p = new RivalParty(UUID.randomUUID(), g.id(), rank, guild, leader == null ? "" : leader.id(), members, now);
        int eta = RivalConfig.etaMinTicks() + r.nextInt(Math.max(1, RivalConfig.etaMaxTicks() - RivalConfig.etaMinTicks() + 1));
        p.arriveAt = now + eta;
        // they come from about as far off as they need the time for, from somewhere round the gate
        float a = r.nextFloat() * Mth.TWO_PI;
        double d = Mth.clamp(eta / 20.0 * 0.9, 36, 140);
        p.from = new BlockPos(g.gatePos().getX() + Mth.floor(Mth.cos(a) * d), g.gatePos().getY(), g.gatePos().getZ() + Mth.floor(Mth.sin(a) * d));
        data.add(p);
        // the hunters near the gate hear of it (the association's board, the talk of the town)
        ServerLevel world = server.getLevel(g.gateDimension());
        for (ServerPlayer pl : OnlinePlayers.all(server)) {
            if (world == null || pl.level() != world || !SystemAPI.isAwakened(pl)) continue;
            double dist = Math.sqrt(GateSpawner.flatDistSqr(pl.position(), g.gatePos()));
            if (dist > TELL) continue;
            p.watchers.add(pl.getUUID());
            // one of the guild's own (under contract with it): no race, they'll let them in
            String dispatched = com.beyondsolo.scouting.Scouting.allied(pl, p.guild) ? "notify.dispatched.ally" : "notify.dispatched";
            Notifications.send(pl, SystemNotification.of(dispatched.endsWith("ally") ? SystemNotification.Kind.INFO : SystemNotification.Kind.ALERT,
                    Component.translatable(KEY + dispatched + ".title", p.guild.displayName()),
                    Component.translatable(KEY + dispatched + ".body", p.partyName(), rank.displayName(),
                            Mth.floor(dist + 0.5), GateSpawner.direction(pl.position(), g.gatePos()), clock(eta))));
        }
        data.setDirty();
        return p;
    }

    /** Which guild a party hunts for when it falls out: the four guilds, and independents as often as any. */
    static RivalGuild rollGuild(RandomSource r) {
        RivalGuild[] all = RivalGuild.values();
        return all[r.nextInt(all.length)];
    }

    /** A party of three to five of the gate's rank: its leader first, someone to stand in front, and a healer for four or more. */
    static List<RivalParty.Member> members(MinecraftServer server, PlayerRank rank, @Nullable Rival leader, RandomSource r) {
        int size = 3 + r.nextInt(3);
        int from = Recruits.bandStart(rank), to = Recruits.bandEnd(rank);
        List<PlayerClass> classes = new ArrayList<>();
        PlayerClass[] all = PlayerClass.values();
        classes.add(leader != null ? leader.playerClass() : all[r.nextInt(all.length)]);
        if (!classes.contains(PlayerClass.GUARDIAN) && !classes.contains(PlayerClass.WARRIOR)) classes.add(r.nextBoolean() ? PlayerClass.GUARDIAN : PlayerClass.WARRIOR);
        if (size >= 4 && !classes.contains(PlayerClass.HEALER)) classes.add(PlayerClass.HEALER);
        while (classes.size() < size) classes.add(all[r.nextInt(all.length)]);
        List<RivalParty.Member> out = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            PlayerClass cls = classes.get(i);
            if (i == 0 && leader != null) {
                int level = Mth.clamp(Math.round(leader.levelOn(Rankings.day(server))), 1, Balance.MAX_LEVEL);
                out.add(new RivalParty.Member(UUID.nameUUIDFromBytes(("beyondsolo:rival/" + leader.id()).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        leader.name(), cls, level, Math.floorMod(leader.id().hashCode(), GuildHunter.SKINS.length)));
                continue;
            }
            // the leader is the strongest of them; the rest mostly near the start of the rank
            double k = i == 0 ? 0.55 + r.nextDouble() * 0.45 : Math.pow(r.nextDouble(), 1.4) * 0.8;
            int level = Math.min(to, from + (int) Math.floor(k * (to - from + 1)));
            out.add(new RivalParty.Member(new UUID(r.nextLong(), r.nextLong()), Recruits.name(r), cls, Math.max(1, level),
                    r.nextInt(GuildHunter.SKINS.length)));
        }
        return out;
    }

    // ================================================================ the clocks

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        if (tick % 10 != 0) return;
        RivalData data = RivalData.get(server);
        if (data.parties().isEmpty()) return;
        long now = now(server);
        for (RivalParty p : List.copyOf(data.parties())) {
            if (tick % 20 == 0) advance(server, data, p, now);
            if (data.party(p.id) != null) bodies(server, data, p, now);
        }
        if (tick % 20 == 0) sync(server, data, now);
    }

    /** One second of a party's run. */
    static void advance(MinecraftServer server, RivalData data, RivalParty p, long now) {
        if (p.state == RivalParty.State.DONE) {
            if (now - p.endedAt > FORGET) {
                data.remove(p.id);
                UNSEEN.remove(p.id);
            }
            return;
        }
        GateInstance g = GateData.get(server).get(p.gate);
        // the gate closed, broke out or was wiped before they were through with it
        if (g == null || g.state() == GateInstance.State.CLOSED || g.breakState().hasBroken()) {
            end(server, data, p, RivalParty.Outcome.DISBANDED, now);
            tell(server, p, SystemNotification.Kind.INFO, "disbanded", p.partyName());
            return;
        }
        switch (p.state) {
            case EN_ROUTE -> {
                if (g.bossDefeated()) standDown(server, data, p, now, "too_late");
                else if (now >= p.arriveAt) reach(server, data, p, g, now);
            }
            case GATHERING -> {
                if (g.bossDefeated()) standDown(server, data, p, now, "too_late");
                // the association gave the gate to somebody meanwhile (a permit, an exam): they move on
                else if (AssociationData.get(server).claim(g.id()) != null) standDown(server, data, p, now, "claimed");
                else if (now >= p.gatherUntil) goIn(server, data, p, g, now);
            }
            case INSIDE -> inside(server, data, p, g, now);
            default -> {
            }
        }
    }

    /** They reach the gate and gather in front of it (or, beaten to it, think again). */
    static void reach(MinecraftServer server, RivalData data, RivalParty p, GateInstance g, long now) {
        p.state = RivalParty.State.GATHERING;
        p.gatherUntil = now + RivalConfig.gatherTicks();
        data.setDirty();
        // whoever walks out of the world now walks to their place in front of the gate
        ServerLevel world = server.getLevel(g.gateDimension());
        if (world != null) {
            for (int i = 0; i < p.members.size(); i++) {
                RivalHunterEntity e = body(server, p.members.get(i));
                if (e != null && !inCave(g, e) && e.level() == world) e.march(walk(world, e.position(), gatherSpot(world, g, i, p.members.size())), 1.0);
            }
        }
        tell(server, p, SystemNotification.Kind.INFO, "arrived", p.partyName(), clock(RivalConfig.gatherTicks()));
    }

    /** Someone got there first: they move on (and tell the hunters who watched them come why). */
    static void standDown(MinecraftServer server, RivalData data, RivalParty p, long now, String why) {
        end(server, data, p, RivalParty.Outcome.STOOD_DOWN, now);
        tell(server, p, SystemNotification.Kind.INFO, "stood_down." + why, p.partyName());
        // they walk back the way they came
        for (RivalParty.Member m : p.members) {
            RivalHunterEntity e = body(server, m);
            if (e != null && e.level() instanceof ServerLevel l) e.march(walk(l, e.position(), Vec3.atBottomCenterOf(p.from)), 0.9);
        }
    }

    /** They go in: first in, the association gives them the gate; after a hunter, it is a race. */
    static void goIn(MinecraftServer server, RivalData data, RivalParty p, GateInstance g, long now) {
        RandomSource r = server.overworld().random;
        p.state = RivalParty.State.INSIDE;
        p.enteredAt = now;
        p.progress = 0;
        boolean after = p.racing || !present(server, g).isEmpty();
        p.racing = after;
        p.holds = !after;
        p.clearTicks = Math.max(20 * 30, (int) (RivalConfig.clearTicks(p.rank) * (0.85 + r.nextDouble() * 0.3)));
        p.succeeds = r.nextDouble() < (p.guild == RivalGuild.INDEPENDENT ? RivalConfig.independentSuccess() : RivalConfig.guildSuccess());
        data.setDirty();
        // the ones standing in front of it walk in
        GateBlockEntity gate = gateBlock(server, g);
        Vec3 into = gate != null ? gate.center() : Vec3.atCenterOf(g.gatePos());
        for (RivalParty.Member m : p.members) {
            RivalHunterEntity e = body(server, m);
            if (e != null && !inCave(g, e)) e.march(List.of(new Vec3(into.x, g.gatePos().getY(), into.z)), 1.0);
        }
        tell(server, p, after ? SystemNotification.Kind.ALERT : SystemNotification.Kind.INFO, after ? "went_in.race" : "went_in.holds",
                p.partyName(), p.rank.displayName());
    }

    /** A second inside the gate: their clock runs while nobody sees them; their hunters' feet are the clock while someone does. */
    static void inside(MinecraftServer server, RivalData data, RivalParty p, GateInstance g, long now) {
        List<RivalHunterEntity> cave = caveBodies(server, p, g);
        if (cave.isEmpty()) {
            p.progress += 20f / Math.max(20, p.clearTicks);
            data.setDirty();
            if (!p.succeeds && p.progress >= RETREAT_AT) retreat(server, data, p, now, "beaten_back");
            else if (p.progress >= 1) {
                if (p.succeeds) clearAlone(server, data, p, g, now);
                else retreat(server, data, p, now, "beaten_back");
            }
            return;
        }
        Route route = route(g);
        int best = 0;
        for (RivalHunterEntity e : cave) best = Math.max(best, e.routeAt());
        float walked = ROUTE_SHARE * Math.min(1f, best / (float) Math.max(1, route.size() - 1));
        if (walked > p.progress) {
            p.progress = walked;
            data.setDirty();
        }
    }

    /** They cleared the gate with nobody there to see it: the boss is gone, the gate closes, the clear is theirs. */
    static void clearAlone(MinecraftServer server, RivalData data, RivalParty p, GateInstance g, long now) {
        ServerLevel dungeon = GateService.dungeon(server);
        if (dungeon != null && g.boss() != null) {
            Entity boss = dungeon.getEntity(g.boss());
            if (boss != null) boss.discard();
        }
        g.setBossDefeated(true);
        g.setState(GateInstance.State.CLEARED);
        g.setClearedAt(dungeon != null ? dungeon.getGameTime() : now);
        GateData.get(server).setDirty();
        p.progress = 1;
        data.addClear(p.rival);
        end(server, data, p, RivalParty.Outcome.CLEARED, now);
        tell(server, p, SystemNotification.Kind.INFO, "cleared", p.partyName(), p.rank.displayName());
        com.beyondsolo.news.HunterNews.partyCleared(server, p, p.rank);
    }

    /** They pull out: the gate is open again, nobody's. */
    static void retreat(MinecraftServer server, RivalData data, RivalParty p, long now, String why) {
        end(server, data, p, RivalParty.Outcome.RETREATED, now);
        tell(server, p, SystemNotification.Kind.INFO, "retreated." + why, p.partyName(), p.rank.displayName());
    }

    /** The party's run is over. */
    static void end(MinecraftServer server, RivalData data, RivalParty p, RivalParty.Outcome outcome, long now) {
        p.state = RivalParty.State.DONE;
        p.outcome = outcome;
        p.endedAt = now;
        p.holds = false;
        data.setDirty();
    }

    // ================================================================ hunters and the party

    /** Who is in a gate's cave now (awake, alive hunters). */
    static List<ServerPlayer> present(MinecraftServer server, GateInstance g) {
        List<ServerPlayer> out = new ArrayList<>();
        ServerLevel dungeon = GateService.dungeon(server);
        if (dungeon == null) return out;
        for (ServerPlayer pl : OnlinePlayers.all(server)) {
            if (pl.level() != dungeon || !pl.isAlive() || pl.isSpectator()) continue;
            GateInstance at = GateData.get(server).bySlotPos(pl.blockPosition());
            if (at != null && at.id().equals(g.id())) out.add(pl);
        }
        return out;
    }

    /** The hunters in a gate's cave who are the party's guild's own (under contract with it). */
    static List<ServerPlayer> allies(MinecraftServer server, RivalParty p, GateInstance g) {
        List<ServerPlayer> out = present(server, g);
        out.removeIf(pl -> !com.beyondsolo.scouting.Scouting.allied(pl, p.guild));
        return out;
    }

    /** A hunter went into a gate ({@code GateService.arrive}): a party on its way was beaten to it, a party in it has company. */
    public static void arrived(ServerPlayer player, GateInstance g) {
        arrived(player, g, player.server.overworld().random.nextFloat());
    }

    /** As {@link #arrived(ServerPlayer, GateInstance)}; a party on its way follows them in if {@code roll} is under its nerve. */
    static void arrived(ServerPlayer player, GateInstance g, float roll) {
        MinecraftServer server = player.server;
        RivalData data = RivalData.get(server);
        RivalParty p = data.forGate(g.id());
        if (p == null) return;
        long now = now(server);
        p.watchers.add(player.getUUID());
        // one of the guild's own (under contract with it, {@code scouting.Scouting}): the gate is theirs
        boolean ally = com.beyondsolo.scouting.Scouting.allied(player, p.guild);
        switch (p.state) {
            case EN_ROUTE, GATHERING -> {
                if (ally) {
                    standDown(server, data, p, now, "ally");
                    return;
                }
                if (p.racing) return;
                // the bold ones come in after them anyway
                if (roll < p.guild.nerve * RivalConfig.followInScale()) {
                    p.racing = true;
                    data.setDirty();
                    tell(server, p, SystemNotification.Kind.ALERT, "following", p.partyName());
                } else {
                    standDown(server, data, p, now, "beaten");
                }
            }
            case INSIDE -> {
                if (ally) return;
                if (p.holds && !p.trespassers.contains(player.getUUID())) trespass(server, data, p, player);
                else if (!p.racing) {
                    p.racing = true;
                    data.setDirty();
                }
            }
            default -> {
            }
        }
    }

    /**
     * Why a hunter can't go into a gate a party holds ({@code GateService.beginEnter}), or null: they may if they
     * already forced their way in once, and anyone may force their way in crouching (it costs merit, inside).
     */
    @Nullable
    public static String refusal(ServerPlayer player, GateInstance g) {
        RivalParty p = RivalData.get(player.server).forGate(g.id());
        if (p == null || p.state != RivalParty.State.INSIDE || !p.holds) return null;
        if (p.trespassers.contains(player.getUUID()) || player.isShiftKeyDown()) return null;
        // the guild's own hunter is welcome
        if (com.beyondsolo.scouting.Scouting.allied(player, p.guild)) return null;
        return KEY + "refuse.held";
    }

    /** A hunter forced their way into a gate a party holds: the association docks their merit, and the race is on. */
    static void trespass(MinecraftServer server, RivalData data, RivalParty p, ServerPlayer player) {
        if (!p.trespassers.add(player.getUUID())) return;
        p.racing = true;
        data.setDirty();
        long merit = RivalConfig.trespassMerit(p.rank);
        if (SystemAPI.isAwakened(player)) {
            Association.takeMerit(player, merit);
            Hires.record(player).trespasses++;
        }
        Notifications.send(player, SystemNotification.of(SystemNotification.Kind.ALERT,
                Component.translatable(KEY + "notify.trespass.title"),
                Component.translatable(KEY + "notify.trespass.body", p.partyName(), merit)));
    }

    /** True while a party holds the gate's clearing rights (it went in first). */
    public static boolean holds(MinecraftServer server, UUID gate) {
        RivalParty p = RivalData.get(server).forGate(gate);
        return p != null && p.state == RivalParty.State.INSIDE && p.holds;
    }

    /** The party going for a gate now, or null. */
    @Nullable
    public static RivalParty partyFor(MinecraftServer server, UUID gate) {
        return RivalData.get(server).forGate(gate);
    }

    /** The party still going whose gate is nearest {@code at} (on the map), or null (commands). */
    @Nullable
    public static RivalParty nearest(MinecraftServer server, Vec3 at) {
        GateData gates = GateData.get(server);
        RivalParty best = null;
        double bestD = Double.MAX_VALUE;
        for (RivalParty p : RivalData.get(server).active()) {
            GateInstance g = gates.get(p.gate);
            if (g == null) continue;
            double d = GateSpawner.flatDistSqr(at, g.gatePos());
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    // ================================================================ the boss

    /** Tallies the race for a boss: what the party dealt it, and what the hunters (and all who fight for them) did. */
    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof GateBossEntity boss) || boss.gateId() == null || !(boss.level() instanceof ServerLevel level)) return;
        float dealt = event.getNewDamage();
        if (dealt <= 0) return;
        RivalData data = RivalData.get(level.getServer());
        RivalParty p = data.forGate(boss.gateId());
        if (p == null || p.state != RivalParty.State.INSIDE) return;
        Entity by = event.getSource().getEntity();
        if (by instanceof RivalHunterEntity r) {
            if (p.id.equals(r.party())) p.rivalDamage += dealt;
        } else if (IsleRaids.hunterSide(by)) {
            p.hunterDamage += dealt;
            // fighting beside the guild's own hunter is no race
            GateInstance g = GateData.get(level.getServer()).get(p.gate);
            if (g == null || allies(level.getServer(), p, g).isEmpty()) p.racing = true;
        } else return;
        data.setDirty();
    }

    /**
     * A gate's boss fell ({@code GateService.bossDefeated}). With a party in the gate it was a race: whoever dealt the
     * boss most takes it. True when the party took it (and with it the boss's cache).
     */
    public static boolean bossFell(ServerLevel level, GateInstance g, DamageSource source) {
        MinecraftServer server = level.getServer();
        RivalData data = RivalData.get(server);
        RivalParty p = data.forGate(g.id());
        if (p == null) return false;
        long now = now(server);
        if (p.state != RivalParty.State.INSIDE) {
            standDown(server, data, p, now, "too_late");
            return false;
        }
        float total = p.rivalDamage + p.hunterDamage;
        boolean theirs = total <= 0 ? source.getEntity() instanceof RivalHunterEntity : p.rivalDamage > p.hunterDamage;
        int share = total <= 0 ? (theirs ? 100 : 0) : Math.round(100 * p.rivalDamage / total);
        List<ServerPlayer> inside = present(server, g);
        p.progress = 1;
        // with the guild's own hunter in there it was a clear together: the clear is theirs too, the boss's cache the hunter's
        if (!allies(server, p, g).isEmpty()) {
            data.addClear(p.rival);
            end(server, data, p, RivalParty.Outcome.CLEARED, now);
            for (ServerPlayer pl : inside) {
                Notifications.send(pl, SystemNotification.of(SystemNotification.Kind.REWARD, Component.translatable(KEY + "notify.title", p.guild.displayName()),
                        Component.translatable(KEY + "notify.joint", p.partyName(), p.rank.displayName())));
            }
            for (UUID id : p.watchers) {
                ServerPlayer pl = OnlinePlayers.get(server, id);
                if (pl == null || inside.contains(pl)) continue;
                Notifications.send(pl, SystemNotification.of(SystemNotification.Kind.INFO, Component.translatable(KEY + "notify.title", p.guild.displayName()),
                        Component.translatable(KEY + "notify.cleared", p.partyName(), p.rank.displayName())));
            }
            com.beyondsolo.news.HunterNews.partyCleared(server, p, p.rank);
            return false;
        }
        if (theirs) {
            data.addClear(p.rival);
            end(server, data, p, RivalParty.Outcome.CLEARED, now);
            com.beyondsolo.news.HunterNews.raceLost(server, inside, p, p.rank);
            for (ServerPlayer pl : inside) {
                if (SystemAPI.isAwakened(pl)) Hires.record(pl).raceLosses++;
                Notifications.send(pl, SystemNotification.of(SystemNotification.Kind.ALERT,
                        Component.translatable(KEY + "notify.race_lost.title"),
                        Component.translatable(KEY + "notify.race_lost.body", p.partyName(), share)));
            }
        } else {
            end(server, data, p, RivalParty.Outcome.BEATEN, now);
            com.beyondsolo.news.HunterNews.raceWon(server, inside, p, p.rank);
            for (ServerPlayer pl : inside) {
                Notifications.send(pl, SystemNotification.of(SystemNotification.Kind.REWARD,
                        Component.translatable(KEY + "notify.race_won.title"),
                        Component.translatable(KEY + "notify.race_won.body", p.partyName(), 100 - share)));
                if (!SystemAPI.isAwakened(pl)) continue;
                RivalRecord rec = Hires.record(pl);
                rec.raceWins++;
                if (rec.raceWins == RivalModule.RACER_WINS) SystemAPI.setTitle(pl, "title.beyondsolo.gate_racer");
                // won a gate they had no right to be in
                if (p.trespassers.contains(pl.getUUID())) SystemAPI.setTitle(pl, "title.beyondsolo.claim_jumper");
            }
        }
        // the ones who saw it from outside hear how it went
        for (UUID id : p.watchers) {
            ServerPlayer pl = OnlinePlayers.get(server, id);
            if (pl == null || inside.contains(pl)) continue;
            Notifications.send(pl, SystemNotification.of(SystemNotification.Kind.INFO, Component.translatable(KEY + "notify.title", p.guild.displayName()),
                    Component.translatable(KEY + (theirs ? "notify.cleared" : "notify.beaten"), p.partyName(), p.rank.displayName())));
        }
        return theirs;
    }

    /** One of a party's hunters fell: out for good. With all of them down, the run is over. */
    static void memberFell(ServerLevel level, RivalHunterEntity e) {
        MinecraftServer server = level.getServer();
        RivalData data = RivalData.get(server);
        RivalParty p = e.party() == null ? null : data.party(e.party());
        if (p == null || e.member() >= p.members.size()) return;
        RivalParty.Member m = p.members.get(e.member());
        m.down = true;
        m.body = null;
        data.setDirty();
        if (p.active() && p.standing().isEmpty()) {
            if (p.state == RivalParty.State.INSIDE) retreat(server, data, p, now(server), "wiped");
            else {
                end(server, data, p, RivalParty.Outcome.DISBANDED, now(server));
                tell(server, p, SystemNotification.Kind.INFO, "disbanded", p.partyName());
            }
        }
    }

    // ================================================================ their bodies

    /** A member's body, wherever it is loaded, or null. */
    @Nullable
    static RivalHunterEntity body(MinecraftServer server, RivalParty.Member m) {
        if (m.body == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(m.body) instanceof RivalHunterEntity e && e.isAlive() && !e.isRemoved()) return e;
        }
        return null;
    }

    /** True if the entity stands in the gate's own cave. */
    static boolean inCave(GateInstance g, Entity e) {
        if (!GateService.isDungeon(e.level())) return false;
        return GateData.slotAt(e.blockPosition()) == g.slot();
    }

    /** The party's hunters out in the gate's cave. */
    static List<RivalHunterEntity> caveBodies(MinecraftServer server, RivalParty p, GateInstance g) {
        List<RivalHunterEntity> out = new ArrayList<>();
        for (RivalParty.Member m : p.members) {
            RivalHunterEntity e = body(server, m);
            if (e != null && inCave(g, e)) out.add(e);
        }
        return out;
    }

    /** Puts the party's hunters out where someone can see them, and takes them away where nobody can. */
    static void bodies(MinecraftServer server, RivalData data, RivalParty p, long now) {
        GateInstance g = GateData.get(server).get(p.gate);
        if (g == null || UNSEEN.contains(p.id)) {
            removeBodies(server, p);
            return;
        }
        ServerLevel world = server.getLevel(g.gateDimension());
        switch (p.state) {
            case EN_ROUTE, GATHERING -> {
                if (world == null) return;
                Vec3 at = where(server, p, g, now);
                if (seen(server, world, at, SEE) && world.isLoaded(BlockPos.containing(at))) worldBodies(server, world, p, g, at, now);
                else removeBodies(server, p);
            }
            case INSIDE -> {
                // the ones still walking in from the world vanish into the gate as they reach it
                GateBlockEntity gate = gateBlock(server, g);
                Vec3 into = gate != null ? gate.center() : Vec3.atCenterOf(g.gatePos());
                for (RivalParty.Member m : p.members) {
                    RivalHunterEntity e = body(server, m);
                    if (e == null || inCave(g, e)) continue;
                    double dx = e.getX() - into.x, dz = e.getZ() - into.z;
                    if (dx * dx + dz * dz < 1.6 * 1.6 || now - p.enteredAt >= WALK_IN || !seen(server, (ServerLevel) e.level(), e.position(), SEE)) {
                        if (e.level() instanceof ServerLevel l && seen(server, l, e.position(), SEE)) {
                            l.sendParticles(ParticleTypes.PORTAL, e.getX(), e.getY() + 1, e.getZ(), 24, 0.3, 0.7, 0.3, 0.2);
                            l.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.PORTAL_TRAVEL, SoundSource.NEUTRAL, 0.15f, 1.6f);
                        }
                        e.discard();
                        m.body = null;
                    }
                }
                List<ServerPlayer> in = present(server, g);
                ServerLevel dungeon = GateService.dungeon(server);
                if (!in.isEmpty() && dungeon != null && g.built()) caveBodies(server, dungeon, p, g, now);
                else {
                    for (RivalHunterEntity e : caveBodies(server, p, g)) {
                        e.discard();
                        p.members.get(e.member()).body = null;
                    }
                }
            }
            default -> {
            }
        }
    }

    /** Where a party out in the world is now: on its way from where it set out, or in front of the gate. */
    static Vec3 where(MinecraftServer server, RivalParty p, GateInstance g, long now) {
        Vec3 front = front(server, g);
        if (p.state != RivalParty.State.EN_ROUTE) return front;
        double t = Mth.clamp((now - p.dispatchedAt) / (double) Math.max(1, p.arriveAt - p.dispatchedAt), 0, 1);
        Vec3 from = Vec3.atBottomCenterOf(p.from);
        return new Vec3(Mth.lerp(t, from.x, front.x), front.y, Mth.lerp(t, from.z, front.z));
    }

    static Vec3 front(MinecraftServer server, GateInstance g) {
        GateBlockEntity gate = gateBlock(server, g);
        return gate != null ? gate.front() : Vec3.atBottomCenterOf(g.gatePos().relative(Direction.NORTH, 2));
    }

    @Nullable
    static GateBlockEntity gateBlock(MinecraftServer server, GateInstance g) {
        ServerLevel world = server.getLevel(g.gateDimension());
        if (world == null || !world.isLoaded(g.gatePos())) return null;
        return world.getBlockEntity(g.gatePos()) instanceof GateBlockEntity gate ? gate : null;
    }

    /** Where the {@code i}th of {@code n} stands while they gather: an arc in front of the gate, facing it. */
    static Vec3 gatherSpot(ServerLevel world, GateInstance g, int i, int n) {
        Direction f = world.getBlockEntity(g.gatePos()) instanceof GateBlockEntity gate ? gate.facing() : Direction.NORTH;
        Direction side = f.getClockWise();
        double out = 3.4 + (i % 2) * 1.3;
        double across = (i - (n - 1) / 2.0) * 1.7;
        double x = g.gatePos().getX() + 0.5 + f.getStepX() * out + side.getStepX() * across;
        double z = g.gatePos().getZ() + 0.5 + f.getStepZ() * out + side.getStepZ() * across;
        int y = surface(world, Mth.floor(x), Mth.floor(z), g.gatePos().getY());
        return new Vec3(x, y, z);
    }

    /** A walk over the land from {@code from} to {@code to}: a point every dozen blocks, each on the ground where it is loaded. */
    static List<Vec3> walk(ServerLevel level, Vec3 from, Vec3 to) {
        List<Vec3> out = new ArrayList<>();
        double dx = to.x - from.x, dz = to.z - from.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        int steps = Math.max(1, (int) Math.ceil(len / 12));
        int y = Mth.floor(from.y);
        for (int i = 1; i < steps; i++) {
            double x = from.x + dx * i / steps, z = from.z + dz * i / steps;
            BlockPos column = BlockPos.containing(x, y, z);
            if (level.isLoaded(column)) y = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).getY();
            out.add(new Vec3(x, y, z));
        }
        out.add(to);
        return out;
    }

    /** The ground at a column, near {@code near}'s height ({@code near} if the ground is far off it). */
    static int surface(ServerLevel level, int x, int z, int near) {
        int y = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, near, z)).getY();
        return Math.abs(y - near) <= 5 ? y : near;
    }

    /** True if a hunter (awake, alive) is this near {@code at} in that level. */
    static boolean seen(MinecraftServer server, ServerLevel level, Vec3 at, int range) {
        for (ServerPlayer pl : OnlinePlayers.all(server)) {
            if (pl.level() != level || !pl.isAlive() || pl.isSpectator()) continue;
            double dx = pl.getX() - at.x, dz = pl.getZ() - at.z;
            if (dx * dx + dz * dz < (double) range * range) return true;
        }
        return false;
    }

    /** The party out in the world (on its way, or gathering): each hunter missing steps out near where the party is. */
    static void worldBodies(MinecraftServer server, ServerLevel world, RivalParty p, GateInstance g, Vec3 at, long now) {
        int n = p.members.size();
        for (int i = 0; i < n; i++) {
            RivalParty.Member m = p.members.get(i);
            if (m.down) continue;
            RivalHunterEntity have = body(server, m);
            if (have != null && have.level() == world && !inCave(g, have)) {
                if (p.state == RivalParty.State.EN_ROUTE) keepPace(have, gatherSpot(world, g, i, n), p.arriveAt - now);
                continue;
            }
            if (have != null) have.discard();
            RivalHunterEntity e = RivalModule.RIVAL.get().create(world);
            if (e == null) continue;
            e.setup(p, i);
            Vec3 spot;
            Vec3 goal = gatherSpot(world, g, i, n);
            if (p.state == RivalParty.State.GATHERING) spot = goal;
            else {
                // a loose file on the way, the leader in front
                Vec3 dir = goal.subtract(at).multiply(1, 0, 1);
                dir = dir.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : dir.normalize();
                Vec3 side = new Vec3(-dir.z, 0, dir.x);
                double back = i * 1.6, across = (i % 2 == 0 ? 1 : -1) * (i == 0 ? 0 : 0.9);
                spot = at.subtract(dir.scale(back)).add(side.scale(across));
            }
            int y = surface(world, Mth.floor(spot.x), Mth.floor(spot.z), Mth.floor(at.y));
            e.moveTo(spot.x, y, spot.z, (float) (Mth.atan2(goal.z - spot.z, goal.x - spot.x) * Mth.RAD_TO_DEG) - 90f, 0f);
            world.addFreshEntity(e);
            m.body = e.getUUID();
            if (p.state == RivalParty.State.EN_ROUTE) {
                e.march(walk(world, e.position(), goal), 0.8);
                keepPace(e, goal, p.arriveAt - now);
            } else e.march(List.of(goal), 1.0);
        }
    }

    /**
     * On the road a hunter keeps to the party's clock, so the party a hunter sees is where its clock says it is: each
     * walks at what gets them to their place in front of the gate as the clock runs out (a little over the straight
     * line, for the turns of the land).
     */
    static void keepPace(RivalHunterEntity e, Vec3 goal, long ticksLeft) {
        double dx = goal.x - e.getX(), dz = goal.z - e.getZ();
        e.walkAt(Math.sqrt(dx * dx + dz * dz) * 1.15 / Math.max(20, ticksLeft));
    }

    /**
     * The party in a cave with a hunter in it: each hunter missing steps out where the party has got to on its way
     * (the caverns behind them already emptied), and walks on. Creatures idling near them go for them, and they stir
     * what waits ahead as they come.
     */
    static void caveBodies(MinecraftServer server, ServerLevel dungeon, RivalParty p, GateInstance g, long now) {
        Route route = route(g);
        if (route.size() == 0) return;
        int at = Math.min(route.size() - 1, (int) Math.floor(Math.min(1f, p.progress / ROUTE_SHARE) * (route.size() - 1)));
        boolean spawned = false;
        for (int i = 0; i < p.members.size(); i++) {
            RivalParty.Member m = p.members.get(i);
            if (m.down) continue;
            RivalHunterEntity have = body(server, m);
            if (have != null && inCave(g, have)) continue;
            if (have != null) continue; // still walking in from the world
            // the caverns they have been through are empty: nothing rises there any more
            for (int k = 0; k < at; k++) {
                int room = route.rooms()[k];
                if (room >= 0 && !g.roomTriggered(room)) {
                    g.triggerRoom(room);
                    GateData.get(server).setDirty();
                }
            }
            RivalHunterEntity e = RivalModule.RIVAL.get().create(dungeon);
            if (e == null) continue;
            e.setup(p, i);
            Vec3 base = route.points().get(at);
            float a = i * 2.1f;
            Vec3 spot = footing(dungeon, base.add(Mth.cos(a) * (i == 0 ? 0 : 1.4), 0, Mth.sin(a) * (i == 0 ? 0 : 1.4)));
            Vec3 next = route.points().get(Math.min(route.size() - 1, at + 1));
            e.moveTo(spot.x, spot.y, spot.z, (float) (Mth.atan2(next.z - spot.z, next.x - spot.x) * Mth.RAD_TO_DEG) - 90f, 0f);
            dungeon.addFreshEntity(e);
            m.body = e.getUUID();
            e.marchFrom(route.points(), at + 1, 1.0);
            spawned = true;
        }
        if (spawned) GateData.get(server).setDirty();
        // what waits ahead wakes as they come, and what idles near them goes for them
        for (RivalHunterEntity e : caveBodies(server, p, g)) {
            GateService.stir(dungeon, g, e.blockPosition());
            for (Mob mob : dungeon.getEntitiesOfClass(Mob.class, e.getBoundingBox().inflate(10, 4, 10),
                    mob -> mob instanceof Enemy && !(mob instanceof GateBossEntity) && mob.getTarget() == null && mob.isAlive())) {
                mob.setTarget(e);
            }
        }
    }

    /** Takes every body of the party out of the world, quietly (nobody is near enough to see it). */
    static void removeBodies(MinecraftServer server, RivalParty p) {
        for (RivalParty.Member m : p.members) {
            RivalHunterEntity e = body(server, m);
            if (e != null) e.discard();
            m.body = null;
        }
    }

    /** Somewhere to stand at or near {@code at}: two blocks of air over firm ground (searching a little round it). */
    public static Vec3 footing(ServerLevel level, Vec3 at) {
        BlockPos c = BlockPos.containing(at);
        for (int r = 0; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy = 2; dy >= -3; dy--) {
                        BlockPos p = c.offset(dx, dy, dz);
                        if (stand(level, p)) return new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                    }
                }
            }
        }
        return at;
    }

    private static boolean stand(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getBlockState(p).getFluidState().isEmpty()
                && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP);
    }

    // ================================================================ the way through a cave

    /** The way from a cave's entrance to its boss, room by room along its passages (never through a hidden chamber). */
    static Route route(GateInstance g) {
        Route cached = ROUTES.get(g.id());
        if (cached != null) return cached;
        Route r = plan(g);
        ROUTES.put(g.id(), r);
        return r;
    }

    static Route plan(GateInstance g) {
        DungeonLayout l = g.layout();
        int n = l.rooms().size();
        int boss = l.boss().index();
        int[] prev = new int[n];
        Arrays.fill(prev, -2);
        DungeonLayout.Tunnel[] via = new DungeonLayout.Tunnel[n];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(0);
        prev[0] = -1;
        while (!queue.isEmpty()) {
            int a = queue.poll();
            if (a == boss) break;
            for (DungeonLayout.Tunnel t : l.tunnelsOf(a)) {
                int b = t.from() == a ? t.to() : t.from();
                if (b < 0 || b >= n || prev[b] != -2 || l.rooms().get(b).kind() == DungeonLayout.Kind.HIDDEN) continue;
                prev[b] = a;
                via[b] = t;
                queue.add(b);
            }
        }
        List<Integer> chain = new ArrayList<>();
        if (prev[boss] == -2) {
            chain.add(0);
            chain.add(boss);
        } else {
            for (int c = boss; c != -1; c = prev[c]) chain.add(0, c);
        }
        BlockPos o = g.origin();
        List<Vec3> points = new ArrayList<>();
        List<Integer> rooms = new ArrayList<>();
        for (int i = 0; i < chain.size(); i++) {
            DungeonLayout.Room room = l.rooms().get(chain.get(i));
            points.add(new Vec3(o.getX() + room.center().getX() + 0.5, room.floorY(), o.getZ() + room.center().getZ() + 0.5));
            rooms.add(room.index());
            if (i + 1 >= chain.size()) break;
            DungeonLayout.Tunnel t = via[chain.get(i + 1)];
            if (t == null) continue;
            boolean forward = t.from() == room.index();
            int steps = Math.max(2, (int) Math.ceil(t.length() / 4.0));
            for (int s = 1; s < steps; s++) {
                double u = forward ? s / (double) steps : 1 - s / (double) steps;
                double[] q = DungeonLayout.along(t, u);
                double y = t.style() == DungeonLayout.Style.TUBE ? q[1] - 2 : q[1];
                points.add(new Vec3(o.getX() + q[0] + 0.5, Math.floor(y), o.getZ() + q[2] + 0.5));
                rooms.add(-1);
            }
        }
        int[] ids = new int[rooms.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = rooms.get(i);
        return new Route(List.copyOf(points), ids);
    }

    // ================================================================ talking

    /** A hunter speaks to one of a party's hunters: a line for where the party is at, and for who is asking. */
    public static void talk(ServerPlayer player, RivalHunterEntity e) {
        long now = now(player.server);
        Long last = TALKED.get(player.getUUID());
        if (last != null && now - last < 30) return;
        TALKED.put(player.getUUID(), now);
        RivalParty p = e.party() == null ? null : RivalData.get(player.server).party(e.party());
        RandomSource r = player.getRandom();
        String line;
        Object[] args = new Object[0];
        if (p == null) line = "idle";
        else {
            boolean outranked = SystemAPI.isAwakened(player) && SystemAPI.get(player).rank().tier() >= p.rank.tier() + DEFER;
            boolean early = p.state == RivalParty.State.EN_ROUTE || p.state == RivalParty.State.GATHERING;
            if (com.beyondsolo.scouting.Scouting.allied(player, p.guild) && p.active()) line = "ally";
            else if (early && !e.leader() && r.nextInt(3) == 0) {
                line = "ask_leader";
                args = new Object[]{p.leaderName()};
            } else if (early && outranked) line = "defer";
            else line = switch (p.state) {
                case EN_ROUTE -> "en_route";
                case GATHERING -> "gathering";
                case INSIDE -> p.trespassers.contains(player.getUUID()) ? "trespasser" : p.racing ? "race" : "inside";
                case DONE -> switch (p.outcome) {
                    case CLEARED -> "cleared";
                    case BEATEN -> "beaten";
                    case STOOD_DOWN -> "stood_down";
                    default -> "retreated";
                };
            };
        }
        int variant = line.equals("ask_leader") || line.equals("idle") ? 0 : r.nextInt(3);
        String key = KEY + "talk." + line + (line.equals("ask_leader") || line.equals("idle") ? "" : "." + variant);
        player.sendSystemMessage(Component.translatable(KEY + "says", e.getDisplayName(),
                Component.translatable(key, args).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));
        e.getLookControl().setLookAt(player, 30, 30);
    }

    // ================================================================ telling hunters

    /** Tells the hunters watching a party how it goes ({@code notify.<what>}). */
    static void tell(MinecraftServer server, RivalParty p, SystemNotification.Kind kind, String what, Object... args) {
        for (UUID id : p.watchers) {
            ServerPlayer pl = OnlinePlayers.get(server, id);
            if (pl == null) continue;
            String line = what, title = "notify.title";
            SystemNotification.Kind k = kind;
            // one of the guild's own (under contract with it): the party is on their side, there is no race for them
            if (com.beyondsolo.scouting.Scouting.allied(pl, p.guild)) {
                if (what.equals("following")) continue;
                if (what.startsWith("went_in.")) {
                    line = "went_in.ally";
                    k = SystemNotification.Kind.INFO;
                }
                title = "notify.title.ally";
            }
            Notifications.send(pl, SystemNotification.of(k, Component.translatable(KEY + title, p.guild.displayName()),
                    Component.translatable(KEY + "notify." + line, args)));
        }
    }

    /** "1:45". */
    public static String clock(long ticks) {
        long s = Math.max(0, ticks) / 20;
        return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
    }

    /** What each hunter's HUD shows of the parties near them (the one in their gate first, then the nearest), every second. */
    static void sync(MinecraftServer server, RivalData data, long now) {
        for (ServerPlayer pl : server.getPlayerList().getPlayers()) {
            CompoundTag view = view(server, data, pl, now);
            // a hunter who knows of no party hears so once, not every second
            boolean none = view.getList("parties", Tag.TAG_COMPOUND).isEmpty();
            if (none && !QUIET.add(pl.getUUID())) continue;
            if (!none) QUIET.remove(pl.getUUID());
            PacketDistributor.sendToPlayer(pl, new RivalSyncPayload(view));
        }
    }

    /** One hunter's view of the parties: their gate's first, then those they watch by distance. */
    public static CompoundTag view(MinecraftServer server, RivalData data, ServerPlayer pl, long now) {
        GateInstance here = GateService.isDungeon(pl.level()) ? GateData.get(server).bySlotPos(pl.blockPosition()) : null;
        List<RivalParty> mine = new ArrayList<>();
        for (RivalParty p : data.parties()) {
            boolean recent = p.active() || now - p.endedAt < 20 * 15;
            if (!recent) continue;
            if (here != null && here.id().equals(p.gate) || p.watchers.contains(pl.getUUID())) mine.add(p);
        }
        GateData gates = GateData.get(server);
        mine.sort(Comparator.comparing((RivalParty p) -> here == null || !here.id().equals(p.gate))
                .thenComparing(p -> !p.active())
                .thenComparingDouble(p -> {
                    GateInstance g = gates.get(p.gate);
                    return g == null ? Double.MAX_VALUE : GateSpawner.flatDistSqr(pl.position(), g.gatePos());
                }));
        CompoundTag t = new CompoundTag();
        ListTag list = new ListTag();
        for (RivalParty p : mine) {
            if (list.size() >= 4) break;
            GateInstance g = gates.get(p.gate);
            CompoundTag c = new CompoundTag();
            c.putUUID("id", p.id);
            c.putInt("guild", p.guild.ordinal());
            c.putString("leader", p.leaderName());
            c.putBoolean("ranked", !p.rival.isEmpty());
            c.putInt("rank", p.rank.ordinal());
            c.putInt("state", p.state.ordinal());
            c.putInt("outcome", p.outcome.ordinal());
            c.putInt("standing", p.standing().size());
            c.putInt("size", p.members.size());
            long left = switch (p.state) {
                case EN_ROUTE -> p.arriveAt - now;
                case GATHERING -> p.gatherUntil - now;
                default -> 0;
            };
            c.putLong("left", Math.max(0, left));
            c.putFloat("progress", p.progress);
            c.putBoolean("racing", p.racing);
            c.putBoolean("holds", p.holds);
            float total = p.rivalDamage + p.hunterDamage;
            c.putFloat("share", total <= 0 ? -1f : p.rivalDamage / total);
            c.putBoolean("here", here != null && here.id().equals(p.gate));
            c.putBoolean("trespasser", p.trespassers.contains(pl.getUUID()));
            c.putBoolean("ally", com.beyondsolo.scouting.Scouting.allied(pl, p.guild));
            if (g != null) {
                c.putLong("gate", g.gatePos().asLong());
                c.putBoolean("overworld", g.gateDimension().equals(Level.OVERWORLD));
            }
            list.add(c);
        }
        t.put("parties", list);
        return t;
    }

    // ================================================================ commands and tests

    /** Has a party reach its gate now (it gathers there, its hunters in their places). */
    public static void arriveNow(MinecraftServer server, RivalParty p) {
        GateInstance g = GateData.get(server).get(p.gate);
        if (g == null || p.state != RivalParty.State.EN_ROUTE) return;
        // the ones still on the road are there at once: they step out in their places in front of the gate
        removeBodies(server, p);
        reach(server, RivalData.get(server), p, g, now(server));
    }

    /** Has a party go into its gate now. */
    public static void enterNow(MinecraftServer server, RivalParty p) {
        GateInstance g = GateData.get(server).get(p.gate);
        if (g == null || !p.active() || p.state == RivalParty.State.INSIDE) return;
        RivalData data = RivalData.get(server);
        if (p.state == RivalParty.State.EN_ROUTE) reach(server, data, p, g, now(server));
        goIn(server, data, p, g, now(server));
    }

    /** Ends a party's run in its gate now: cleared (as if nobody saw it) or pulled out. */
    public static void finishNow(MinecraftServer server, RivalParty p, boolean cleared) {
        GateInstance g = GateData.get(server).get(p.gate);
        if (g == null || p.state != RivalParty.State.INSIDE) return;
        RivalData data = RivalData.get(server);
        if (cleared) clearAlone(server, data, p, g, now(server));
        else retreat(server, data, p, now(server), "beaten_back");
    }

    /** Forgets every party (their hunters go). */
    public static void forget(MinecraftServer server) {
        RivalData data = RivalData.get(server);
        for (RivalParty p : data.parties()) removeBodies(server, p);
        data.clear();
        ROUTES.clear();
        UNSEEN.clear();
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        ROUTES.clear();
        TALKED.clear();
        UNSEEN.clear();
        QUIET.clear();
    }

    private RivalParties() {}
}
