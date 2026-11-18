package com.beyondsolo.raid;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.enemy.EnemyFx;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.DungeonLayout;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.gate.GateSpawner;
import com.beyondsolo.gate.GateWiper;
import com.beyondsolo.gate.RewardCacheBlock;
import com.beyondsolo.gate.RewardCacheBlockEntity;
import com.beyondsolo.guild.Guild;
import com.beyondsolo.guild.GuildColors;
import com.beyondsolo.guild.GuildHunter;
import com.beyondsolo.guild.GuildHunterEntity;
import com.beyondsolo.guild.GuildModule;
import com.beyondsolo.guild.Guilds;
import com.beyondsolo.network.GatePayload;
import com.beyondsolo.network.RaidActionPayload;
import com.beyondsolo.network.RaidEventPayload;
import com.beyondsolo.network.RaidSyncPayload;
import com.beyondsolo.network.SkillFxPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestBuilder;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestEvent;
import com.beyondsolo.registry.ModBlocks;
import com.beyondsolo.registry.ModEntities;
import com.beyondsolo.skill.SkillFx;
import com.beyondsolo.system.Balance;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import com.beyondsolo.util.OnlinePlayers;
import com.beyondsolo.util.ServerScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Raids: gates too big for one party. A raid gate opens on its own now and then among the high ranks
 * ({@code raid.naturalChance}), or a guild requests one with a permit ({@link Guilds#permit}). Hunters who walk into it
 * muster at its mouth rather than go through: the briefing shows who is coming and in which role
 * ({@link RaidRole}), and the leader (the first to muster) starts the countdown. When it runs out every raider near the
 * gate goes through together, with the hunters of the leader's guild; from then on the gate lets nobody else in.
 *
 * <p>Inside, its packs are bigger, lieutenants (bosses of their own) hold the way, and the boss's chamber stays sealed
 * until they have all fallen. The boss is grown for the number of raiders and fights in phases: at 70% a barrier only
 * its pylons' breaking can bring down (it reels when it falls), at 50% it rages (as any boss does, with more help),
 * and from 25% it calls down cataclysms that only the light it leaves on the floor shelters from. It minds whoever
 * hurts it most, and a vanguard's blows count three times. The clear pays two and a half times a gate's, a reward cache
 * for each raider, guild XP, levels for the hunters who came, and titles; a raid in which every raider fell or left is
 * failed, its gate shuts and its hunters limp home.</p>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class Raids {
    /** Lang key prefix of the raid's messages. */
    public static final String KEY = "raid.beyondsolo.";
    /** Persistent data on a lieutenant: the id of its raid's gate. */
    public static final String LIEUTENANT_TAG = "beyondsolo_raid_lieutenant";
    public static final String QUEST_TEMPLATE = BeyondSolo.id("raid/clear").toString();
    /** Quest objective: a raid lieutenant felled. */
    public static final String LIEUTENANT_KEY = BeyondSolo.id("raid_lieutenant").toString();
    public static final String BREAKER_TITLE = "title.beyondsolo.raid_breaker";
    public static final String MVP_TITLE = "title.beyondsolo.raid_mvp";
    /** The raid's colours: its gold, the barrier's violet, a cataclysm's red. */
    public static final int GOLD = 0xFFC247, BARRIER = 0xB98BFF, DOOM = 0xFF3348;
    /** The boss's barrier rises at this share of its health; its cataclysms start at this one. */
    public static final float BARRIER_AT = 0.7f, CATACLYSM_AT = 0.25f;
    /** Ticks a cataclysm takes to land (the light shows at once). */
    public static final int CHANNEL_TICKS = 80;
    /** How far from a light's middle it shelters. */
    public static final float REFUGE_RADIUS = 3.2f;

    /** What a running raid keeps only while it runs: who the bosses mind, who dealt what, the cataclysm's clock. */
    private static final class Run {
        /** Per boss or lieutenant (entity id): how much it minds each raider (player id or hunter id). */
        final Map<UUID, Map<UUID, Double>> threat = new HashMap<>();
        /** Damage dealt to the raid's creatures and bosses, by raider (player id or hunter id). */
        final Map<UUID, Double> dealt = new LinkedHashMap<>();
        long nextCataclysm = -1;
        long cataclysmLands = -1;
        final List<Vec3> refuges = new ArrayList<>();
        long lastBarrierDraw;
        long lastSupport;
        long emptySince = -1;
    }

    private static final Map<UUID, Run> RUNS = new HashMap<>();
    /** When each player was last told why the gate turned them away (so walking into it doesn't flood them). */
    private static final Map<UUID, Long> SAID = new HashMap<>();
    /** Raid bosses and lieutenants (entity ids): their targets are the raid's to choose. */
    private static final Set<UUID> CONTROLLED = new HashSet<>();
    /** Raid bosses (entity ids) whose barrier stands. */
    private static final Set<UUID> SHIELDED = new HashSet<>();
    /** True while the raid itself sets a controlled boss's target. */
    private static boolean choosing;

    /** What a button or command came to: done, or why not. */
    public record Outcome(boolean ok, Component message) {
        public static Outcome ok(String key, Object... args) {
            return new Outcome(true, Component.translatable(KEY + key, args));
        }

        public static Outcome no(String key, Object... args) {
            return new Outcome(false, Component.translatable(KEY + key, args));
        }
    }

    // ------------------------------------------------------------------ reads

    /** Every raid gate the server knows. */
    public static List<GateInstance> all(MinecraftServer server) {
        List<GateInstance> out = new ArrayList<>();
        for (GateInstance g : GateData.get(server).all()) if (g.isRaid()) out.add(g);
        return out;
    }

    /** The raid (mustering, counting down or running) whose roster holds this player, or null. */
    @Nullable
    public static GateInstance raidOf(MinecraftServer server, UUID player) {
        for (GateInstance g : GateData.get(server).all()) {
            RaidState s = g.raid();
            if (s != null && s.phase.open() && s.has(player)) return g;
        }
        return null;
    }

    private static RaidState state(GateInstance g) {
        return g.raid();
    }

    private static Run run(UUID gate) {
        return RUNS.computeIfAbsent(gate, k -> new Run());
    }

    private static void dirty(MinecraftServer server) {
        GateData.get(server).setDirty();
    }

    /** True if a raid that is not over has this guild hunter on its roster. */
    public static boolean hunterBusy(MinecraftServer server, UUID hunter) {
        for (GateInstance g : all(server)) {
            RaidState s = g.raid();
            if (s.phase.open() && s.hired(hunter) != null) return true;
        }
        return false;
    }

    /** True while the barrier of a raid's boss stands. */
    public static boolean barrierUp(MinecraftServer server, UUID gate) {
        GateInstance g = GateData.get(server).get(gate);
        return g != null && g.isRaid() && g.raid().phase == RaidState.Phase.RUNNING && g.raid().barrierUp;
    }

    // ------------------------------------------------------------------ guilds

    /** Why a guild's officer can't open a permit raid where they stand (a lang key of the guild's), or null. */
    @Nullable
    public static String permitRefusal(ServerPlayer player, Guild g) {
        if (!player.level().dimension().equals(Level.OVERWORLD)) return "permit.overworld";
        if (raidOf(player.server, player.getUUID()) != null) return "permit.in_raid";
        for (GateInstance gate : all(player.server)) {
            RaidState s = gate.raid();
            if (s.phase.open() && g.id().equals(s.guild)) return "permit.open";
        }
        return null;
    }

    /** Opens a guild's permit raid gate of this rank in front of {@code player}, who leads its muster. */
    public static boolean openPermitGate(ServerPlayer player, Guild guild, PlayerRank rank) {
        ServerLevel level = player.serverLevel();
        GateBlockEntity gate = GateSpawner.trySpawnNear(level, player, rank, true, true);
        if (gate == null || gate.gateId() == null) return false;
        GateInstance g = GateData.get(player.server).get(gate.gateId());
        if (g == null) return false;
        makeRaid(level, gate, g, guild.id());
        join(player, g);
        Component where = Component.translatable(KEY + "permit.where", g.rank().displayName(), player.getGameProfile().getName());
        for (ServerPlayer m : Guilds.online(player.server, guild)) {
            if (m != player) Guilds.inform(m, where, true);
        }
        return true;
    }

    /** A guild was dissolved: its hunters leave any raid they were in. */
    public static void guildGone(MinecraftServer server, UUID guild) {
        ServerLevel dungeon = GateService.dungeon(server);
        for (GateInstance g : all(server)) {
            RaidState s = g.raid();
            if (guild.equals(s.guild)) s.guild = null;
            for (RaidState.Hired h : List.copyOf(s.hunters)) {
                if (!h.guild.equals(guild)) continue;
                if (dungeon != null && h.entity != null && dungeon.getEntity(h.entity) instanceof GuildHunterEntity e) e.leave();
                s.hunters.remove(h);
            }
            if (s.phase.open()) syncRaid(server, g);
        }
        dirty(server);
    }

    /** The guild's raid as the GUILD page shows it (its permit raid, or one its members are on), or null. */
    @Nullable
    public static CompoundTag guildRaidTag(MinecraftServer server, Guild guild) {
        GateInstance best = null;
        for (GateInstance g : all(server)) {
            RaidState s = g.raid();
            if (!s.phase.open()) continue;
            boolean ours = guild.id().equals(s.guild);
            if (!ours) for (UUID m : guild.members()) if (s.has(m)) ours = true;
            if (ours) best = g;
        }
        if (best == null) return null;
        RaidState s = best.raid();
        CompoundTag t = new CompoundTag();
        t.putUUID("gate", best.id());
        t.putInt("rank", best.rank().ordinal());
        t.putString("phase", s.phase.name());
        t.putString("leader", s.leaderName());
        t.putInt("raiders", s.raiders.size());
        t.putInt("hunters", s.hunters.size());
        t.putInt("x", best.gatePos().getX());
        t.putInt("z", best.gatePos().getZ());
        t.putBoolean("permit", guild.id().equals(s.guild));
        t.putInt("lieutenants", s.lieutenants.size());
        return t;
    }

    // ------------------------------------------------------------------ raid gates

    /** Makes a gate a raid gate: its muster opens, everyone near hears of it. */
    public static void makeRaid(ServerLevel level, GateBlockEntity gate, GateInstance g, @Nullable UUID guild) {
        g.setRaid(RaidState.fresh(guild));
        gate.setRaid(true);
        dirty(level.getServer());
        level.playSound(null, gate.getBlockPos(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 3f, 0.8f);
        level.playSound(null, gate.getBlockPos(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2f, 0.5f);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(gate.getBlockPos()).inflate(GateSpawner.NEAR))) {
            if (!SystemAPI.hasSystem(p)) continue;
            int blocks = Mth.floor(Math.sqrt(GateSpawner.flatDistSqr(p.position(), gate.getBlockPos())) + 0.5);
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.ALERT,
                    Component.translatable("notify.beyondsolo.raid.opened.title", g.rank().displayName()),
                    Component.translatable("notify.beyondsolo.raid.opened.body", blocks, GateSpawner.direction(p.position(), gate.getBlockPos()))));
        }
    }

    /** A gate that opened on its own may be a raid gate ({@code raid.naturalChance}). */
    public static boolean naturalRoll(ServerLevel level, GateBlockEntity gate, RandomSource r) {
        if (gate.gateId() == null) return false;
        GateInstance g = GateData.get(level.getServer()).get(gate.gateId());
        if (g == null || g.isRaid() || g.isTrial() || g.isKeyed()) return false;
        int chance = RaidConfig.naturalChance(g.rank().tier());
        if (chance <= 0 || r.nextInt(100) >= chance) return false;
        makeRaid(level, gate, g, null);
        return true;
    }

    /** Opens a raid gate of a rank in front of a player (commands). */
    @Nullable
    public static GateInstance open(ServerPlayer near, PlayerRank rank) {
        ServerLevel level = near.serverLevel();
        GateBlockEntity gate = GateSpawner.trySpawnNear(level, near, rank, true, true);
        if (gate == null || gate.gateId() == null) return null;
        GateInstance g = GateData.get(near.server).get(gate.gateId());
        if (g == null) return null;
        makeRaid(level, gate, g, null);
        return g;
    }

    /** The gate in the world of a raid, if it is loaded. */
    @Nullable
    private static GateBlockEntity worldGate(MinecraftServer server, GateInstance g) {
        ServerLevel world = server.getLevel(g.gateDimension());
        if (world == null || !world.isLoaded(g.gatePos())) return null;
        return world.getBlockEntity(g.gatePos()) instanceof GateBlockEntity gate ? gate : null;
    }

    // ------------------------------------------------------------------ the muster

    /**
     * Someone walked into a raid gate. Before the start they join its muster (and see the briefing) instead of going
     * through; once it has begun only its raiders who have not been in yet pass. True if the gate is handled here.
     */
    public static boolean enter(ServerPlayer player, GateBlockEntity gate, GateInstance g) {
        RaidState s = state(g);
        if (s == null) return false;
        long now = player.level().getGameTime();
        switch (s.phase) {
            case MUSTER, COUNTDOWN -> {
                if (!SystemAPI.isAwakened(player)) return refuse(player, gate, "need_system");
                if (!s.has(player.getUUID())) {
                    Outcome out = join(player, g);
                    if (!out.ok()) return refuse(player, gate, out.message());
                }
                push(player, gate);
                if (SAID.getOrDefault(player.getUUID(), 0L) <= now) {
                    SAID.put(player.getUUID(), now + 50);
                    brief(player);
                }
                return true;
            }
            case RUNNING -> {
                RaidState.Raider r = s.raider(player.getUUID());
                if (r == null) return refuse(player, gate, "sealed");
                if (r.status == RaidState.Status.FALLEN) return refuse(player, gate, "fallen");
                return false;
            }
            case FAILED -> {
                return refuse(player, gate, "failed");
            }
            default -> {
                return false;
            }
        }
    }

    private static boolean refuse(ServerPlayer player, GateBlockEntity gate, String key) {
        return refuse(player, gate, Component.translatable(KEY + key));
    }

    private static boolean refuse(ServerPlayer player, GateBlockEntity gate, Component why) {
        long now = player.level().getGameTime();
        if (SAID.getOrDefault(player.getUUID(), 0L) <= now) {
            player.displayClientMessage(why, true);
            SAID.put(player.getUUID(), now + 60);
        }
        push(player, gate);
        return true;
    }

    /** Back out of the gate's mouth. */
    private static void push(ServerPlayer player, GateBlockEntity gate) {
        Vec3 away = player.position().subtract(gate.center()).multiply(1, 0, 1);
        away = away.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, gate.facing().toYRot()) : away.normalize();
        player.push(away.x * 0.6, 0.25, away.z * 0.6);
        player.hurtMarked = true;
    }

    /** Opens the briefing on the player's screen. */
    public static void brief(ServerPlayer player) {
        sync(player);
        PacketDistributor.sendToPlayer(player, new RaidEventPayload(RaidEventPayload.BRIEF, new CompoundTag(), Component.empty(), Component.empty()));
    }

    /** Joins a raid's muster (the first to join leads it). */
    public static Outcome join(ServerPlayer player, GateInstance g) {
        RaidState s = state(g);
        MinecraftServer server = player.server;
        if (s == null) return Outcome.no("none");
        if (!SystemAPI.isAwakened(player)) return Outcome.no("need_system");
        if (s.has(player.getUUID())) return Outcome.no("already");
        if (s.phase != RaidState.Phase.MUSTER && s.phase != RaidState.Phase.COUNTDOWN) return Outcome.no("sealed");
        GateInstance other = raidOf(server, player.getUUID());
        if (other != null && other != g) return Outcome.no("other_raid");
        int max = RaidConfig.MAX_SIZE.get();
        if (s.raiders.size() >= max) return Outcome.no("full", max);
        SystemData d = SystemAPI.get(player);
        RaidState.Raider r = new RaidState.Raider(player.getUUID(), player.getGameProfile().getName(),
                d.playerClass() != null ? RaidRole.forClass(d.playerClass()) : RaidRole.STRIKER);
        r.cls = d.playerClass() != null ? d.playerClass().ordinal() : -1;
        r.level = d.level();
        s.raiders.put(r.id, r);
        if (s.leader == null || !s.has(s.leader)) s.leader = player.getUUID();
        dirty(server);
        for (ServerPlayer m : roster(server, s)) {
            if (m != player) m.displayClientMessage(Component.translatable(KEY + "joined.other", player.getGameProfile().getName()), true);
        }
        syncRaid(server, g);
        return Outcome.ok("joined", g.rank().displayName());
    }

    /** Leaves the muster of the raid the player is on (before it starts). */
    public static Outcome leave(ServerPlayer player) {
        MinecraftServer server = player.server;
        GateInstance g = raidOf(server, player.getUUID());
        if (g == null) return Outcome.no("not_in");
        RaidState s = state(g);
        if (s.phase == RaidState.Phase.RUNNING) return Outcome.no("leave.running");
        drop(server, g, player.getUUID(), "left.other");
        sync(player);
        return Outcome.ok("left");
    }

    /** Takes someone off a muster's roster; the next raider leads if they led, and a countdown stops without its leader. */
    private static void drop(MinecraftServer server, GateInstance g, UUID player, String why) {
        RaidState s = state(g);
        RaidState.Raider gone = s.raiders.remove(player);
        if (gone == null) return;
        if (player.equals(s.leader)) {
            s.leader = s.raiders.isEmpty() ? null : s.raiders.keySet().iterator().next();
            if (s.phase == RaidState.Phase.COUNTDOWN) {
                s.phase = RaidState.Phase.MUSTER;
                s.countdownEnd = -1;
                for (ServerPlayer m : roster(server, s)) m.displayClientMessage(Component.translatable(KEY + "countdown.stopped"), true);
            }
        }
        if (s.raiders.isEmpty() && s.phase == RaidState.Phase.COUNTDOWN) {
            s.phase = RaidState.Phase.MUSTER;
            s.countdownEnd = -1;
        }
        dirty(server);
        for (ServerPlayer m : roster(server, s)) m.displayClientMessage(Component.translatable(KEY + why, gone.name), true);
        syncRaid(server, g);
    }

    /** Picks the role the player fights in. */
    public static Outcome role(ServerPlayer player, RaidRole role) {
        GateInstance g = raidOf(player.server, player.getUUID());
        if (g == null) return Outcome.no("not_in");
        RaidState s = state(g);
        if (s.phase == RaidState.Phase.RUNNING) return Outcome.no("role.running");
        RaidState.Raider r = s.raider(player.getUUID());
        r.role = role;
        dirty(player.server);
        syncRaid(player.server, g);
        return Outcome.ok("role.set", role.displayName());
    }

    /** The leader brings their guild's hunters, or leaves them home. */
    public static Outcome bring(ServerPlayer player, boolean on) {
        GateInstance g = raidOf(player.server, player.getUUID());
        if (g == null) return Outcome.no("not_in");
        RaidState s = state(g);
        if (!player.getUUID().equals(s.leader)) return Outcome.no("not_leader", s.leaderName());
        if (s.phase == RaidState.Phase.RUNNING) return Outcome.no("role.running");
        s.bringHunters = on;
        dirty(player.server);
        syncRaid(player.server, g);
        return Outcome.ok(on ? "bring.on" : "bring.off");
    }

    /** The leader starts the countdown. */
    public static Outcome start(ServerPlayer player) {
        GateInstance g = raidOf(player.server, player.getUUID());
        if (g == null) return Outcome.no("not_in");
        RaidState s = state(g);
        if (!player.getUUID().equals(s.leader)) return Outcome.no("not_leader", s.leaderName());
        if (s.phase != RaidState.Phase.MUSTER) return Outcome.no("started");
        s.phase = RaidState.Phase.COUNTDOWN;
        s.countdownEnd = player.level().getGameTime() + RaidConfig.COUNTDOWN_SECONDS.get() * 20L;
        dirty(player.server);
        ServerLevel world = player.server.getLevel(g.gateDimension());
        if (world != null) world.playSound(null, g.gatePos(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4f, 1.0f);
        for (ServerPlayer m : roster(player.server, s)) {
            banner(m, Component.translatable(KEY + "banner.countdown"), Component.translatable(KEY + "banner.countdown.sub",
                    RaidConfig.COUNTDOWN_SECONDS.get()), GOLD, "flag");
        }
        syncRaid(player.server, g);
        return Outcome.ok("countdown", RaidConfig.COUNTDOWN_SECONDS.get());
    }

    /** The countdown ran out: everyone near goes through together, the hunters with them; the gate admits nobody else. */
    public static void launch(MinecraftServer server, GateInstance g) {
        RaidState s = state(g);
        GateBlockEntity gate = worldGate(server, g);
        if (gate == null) {
            // the gate's chunk was unloaded under the muster: wait for it
            s.countdownEnd = server.overworld().getGameTime() + 40;
            return;
        }
        double range = RaidConfig.MUSTER_RANGE.get();
        List<ServerPlayer> going = new ArrayList<>();
        for (RaidState.Raider r : List.copyOf(s.raiders.values())) {
            ServerPlayer p = OnlinePlayers.get(server, r.id);
            if (p == null || !p.isAlive() || !p.level().dimension().equals(g.gateDimension())
                    || p.distanceToSqr(Vec3.atCenterOf(g.gatePos())) > range * range) {
                s.raiders.remove(r.id);
                if (p != null) {
                    p.displayClientMessage(Component.translatable(KEY + "too_far"), false);
                    sync(p);
                }
                continue;
            }
            going.add(p);
        }
        if (going.isEmpty()) {
            s.phase = RaidState.Phase.MUSTER;
            s.countdownEnd = -1;
            s.leader = null;
            dirty(server);
            return;
        }
        if (s.leader == null || !s.has(s.leader)) s.leader = going.get(0).getUUID();
        // the leader's guild sends its hunters along, as many as there is room for
        s.hunters.clear();
        Guild guild = Guilds.of(server, s.leader);
        if (guild != null && s.bringHunters) {
            int room = RaidConfig.MAX_SIZE.get() - s.raiders.size();
            for (GuildHunter h : Guilds.fit(server, guild)) {
                if (room-- <= 0) break;
                if (hunterBusy(server, h.id())) continue;
                s.hunters.add(new RaidState.Hired(guild.id(), h.id(), h.name(), h.playerClass(), h.level(), h.skin()));
            }
        }
        s.weight = Math.max(0f, s.raiders.size() + 0.5f * s.hunters.size() - 1f);
        s.phase = RaidState.Phase.RUNNING;
        s.startedAt = server.overworld().getGameTime();
        s.countdownEnd = -1;
        dirty(server);
        RUNS.put(g.id(), new Run());
        for (ServerPlayer p : going) GateService.pullIn(p, gate);
        syncRaid(server, g);
    }

    /** Muster upkeep: wanderers drop out, the countdown ticks down and launches. */
    private static void musterTick(MinecraftServer server, GateInstance g, long now) {
        RaidState s = state(g);
        double range = RaidConfig.MUSTER_RANGE.get() + 16;
        for (RaidState.Raider r : List.copyOf(s.raiders.values())) {
            ServerPlayer p = OnlinePlayers.get(server, r.id);
            if (p == null) {
                drop(server, g, r.id, "left.offline");
                continue;
            }
            if (!p.level().dimension().equals(g.gateDimension()) || p.distanceToSqr(Vec3.atCenterOf(g.gatePos())) > range * range) {
                drop(server, g, r.id, "left.wandered");
                p.displayClientMessage(Component.translatable(KEY + "wandered"), false);
                sync(p);
            }
        }
        if (s.phase == RaidState.Phase.COUNTDOWN) {
            if (now >= s.countdownEnd) launch(server, g);
            else if (now % 20 == 0) syncRaid(server, g);
        }
    }

    // ------------------------------------------------------------------ inside

    /** A raider arrived inside: counted in; the first brings the hunters through. True if the gate is a raid. */
    public static boolean arrived(ServerPlayer player, GateInstance g) {
        RaidState s = state(g);
        if (s == null) return false;
        RaidState.Raider r = s.raider(player.getUUID());
        if (r != null && s.phase == RaidState.Phase.RUNNING) {
            r.status = RaidState.Status.INSIDE;
            dirty(player.server);
            ServerLevel dungeon = player.serverLevel();
            ServerScheduler.schedule(10, () -> {
                if (player.isAlive() && GateService.isDungeon(player.level())) ensureHunters(dungeon, g, s, List.of(player));
            });
            giveQuest(player, g, s);
            banner(player, Component.translatable(KEY + "banner.inside", g.rank().displayName()),
                    Component.translatable(KEY + "banner.inside.sub", s.lieutenantsTotal > 0 ? s.lieutenantsTotal : lieutenantCount(g)), GOLD, "crown");
            syncRaid(player.server, g);
        }
        return true;
    }

    private static void giveQuest(ServerPlayer player, GateInstance g, RaidState s) {
        if (!SystemAPI.isAwakened(player) || g.bossDefeated()) return;
        for (Quest q : QuestEngine.data(player).active()) {
            if (QUEST_TEMPLATE.equals(q.template()) && g.id().toString().equals(q.extra().getString("gate"))) return;
        }
        int tier = g.rank().tier();
        CompoundTag extra = new CompoundTag();
        extra.putString("gate", g.id().toString());
        int lts = Math.max(1, s.lieutenantsTotal > 0 ? s.lieutenantsTotal : lieutenantCount(g));
        Quest q = QuestBuilder.of(QUEST_TEMPLATE, QuestCategory.GATE, tier, SystemAPI.get(player).level())
                .title(Component.translatable("quest.beyondsolo.raid_clear", g.rank().displayName()))
                .description(Component.translatable("quest.beyondsolo.raid_clear.desc", g.dungeonName(), g.bossName()))
                .custom(LIEUTENANT_KEY, lts, Component.translatable("quest_objective.beyondsolo.raid_lieutenants"))
                .custom(GateService.BOSS_KEY, 1, Component.translatable("quest_objective.beyondsolo.gate_boss", g.bossName()))
                .fixed().xp(400L * (1 + tier)).statPoints(tier >= 3 ? 3 : 2)
                .extra(extra)
                .build();
        QuestEngine.start(player, q);
    }

    /** Ends this player's quest for the raid at that gate (if they still hold it), saying why. */
    private static void endQuest(ServerPlayer player, UUID gate, QuestEvent.FailReason reason, @Nullable Component outcome) {
        for (Quest q : List.copyOf(QuestEngine.data(player).active())) {
            if (QUEST_TEMPLATE.equals(q.template()) && gate.toString().equals(q.extra().getString("gate"))) QuestEngine.fail(player, q, reason, outcome);
        }
    }

    /**
     * A raid quest whose raid is over (it failed, or was cleared, while this player was away) or that the player is no
     * longer on: dropped when they come back (and in tests), so it does not hang on the HUD for good.
     */
    public static void dropStaleQuests(ServerPlayer player) {
        for (Quest q : List.copyOf(QuestEngine.data(player).active())) {
            if (!QUEST_TEMPLATE.equals(q.template())) continue;
            UUID id;
            try {
                id = UUID.fromString(q.extra().getString("gate"));
            } catch (IllegalArgumentException e) {
                id = null;
            }
            GateInstance g = id == null ? null : GateData.get(player.server).get(id);
            RaidState s = g == null || !g.isRaid() ? null : g.raid();
            boolean on = s != null && s.phase == RaidState.Phase.RUNNING && !g.bossDefeated()
                    && s.raider(player.getUUID()) != null && s.raider(player.getUUID()).status != RaidState.Status.FALLEN
                    && s.raider(player.getUUID()).status != RaidState.Status.LEFT;
            if (!on) QuestEngine.fail(player, q, QuestEvent.FailReason.BROKEN, Component.translatable(KEY + "quest.ended"));
        }
    }

    /** How many lieutenants a raid of this gate's rank holds: two, three from B up (no more than its caverns allow). */
    public static int lieutenantCount(GateInstance g) {
        return Math.min(g.rank().tier() >= 3 ? 3 : 2, lieutenantRooms(g).size());
    }

    /** The caverns lieutenants may hold: those with a pack on the way to the boss, entrance and side rooms left out. */
    private static List<DungeonLayout.Room> lieutenantRooms(GateInstance g) {
        List<DungeonLayout.Room> out = new ArrayList<>();
        for (DungeonLayout.Room room : g.layout().rooms()) {
            if (room.kind() == DungeonLayout.Kind.CAVERN && room.index() > 0) out.add(room);
        }
        out.sort(Comparator.comparingInt(DungeonLayout.Room::index));
        return out;
    }

    /** The raid's cave is carved: its boss is grown for the raiders and its lieutenants take their caverns. */
    public static void caveBuilt(ServerLevel dungeon, GateInstance g) {
        RaidState s = state(g);
        if (s == null) return;
        if (g.boss() != null && dungeon.getEntity(g.boss()) instanceof GateBossEntity boss) grow(dungeon, s, boss);
        if (s.lieutenantsTotal == 0) {
            RandomSource r = RandomSource.create(g.seed() ^ 0x11E07L);
            List<BossKind> kinds = new ArrayList<>();
            for (BossKind k : BossKind.values()) if (k.livesAt(g.rank()) && k != g.bossKind()) kinds.add(k);
            if (kinds.isEmpty()) kinds.add(g.bossKind());
            List<BossKind> left = new ArrayList<>(kinds);
            for (BlockPos post : lieutenantPosts(g)) {
                // a different one in each cavern while the rank has kinds enough
                if (left.isEmpty()) left.addAll(kinds);
                BossKind kind = left.remove(r.nextInt(left.size()));
                GateBossEntity lt = spawnLieutenant(dungeon, g, post, kind, s);
                if (lt != null) s.lieutenants.add(lt.getUUID());
            }
            s.lieutenantsTotal = s.lieutenants.size();
        }
        dirty(dungeon.getServer());
    }

    /**
     * Grows the raid's boss for its raiders, once (when the cave is built, or the first time the raid finds it loaded
     * if its chamber wasn't then).
     */
    private static void grow(ServerLevel dungeon, RaidState s, GateBossEntity boss) {
        CONTROLLED.add(boss.getUUID());
        if (s.bossScaled) return;
        float hp = (float) (boss.getMaxHealth() * (RaidConfig.BOSS_HEALTH.get() + RaidConfig.BOSS_HEALTH_PER_RAIDER.get() * s.weight));
        boss.getAttribute(Attributes.MAX_HEALTH).setBaseValue(boss.getAttribute(Attributes.MAX_HEALTH).getBaseValue() * hp / boss.getMaxHealth());
        boss.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(boss.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue() * RaidConfig.BOSS_DAMAGE.get());
        boss.setHealth(boss.getMaxHealth());
        s.bossScaled = true;
        dirty(dungeon.getServer());
    }

    /**
     * Where a raid's lieutenants stand guard (in the world): the middle of the floor of their caverns, spread along
     * the way to the boss.
     */
    public static List<BlockPos> lieutenantPosts(GateInstance g) {
        List<DungeonLayout.Room> rooms = lieutenantRooms(g);
        int n = lieutenantCount(g);
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            DungeonLayout.Room room = rooms.get(Math.min(rooms.size() - 1, (i + 1) * rooms.size() / (n + 1)));
            out.add(g.world(new BlockPos(room.center().getX(), room.floorY(), room.center().getZ())));
        }
        return out;
    }

    @Nullable
    private static GateBossEntity spawnLieutenant(ServerLevel dungeon, GateInstance g, BlockPos post, BossKind kind, RaidState s) {
        GateBossEntity lt = ModEntities.GATE_BOSS.get().create(dungeon);
        if (lt == null) return null;
        BlockPos at = floor(dungeon, post);
        lt.configure(g.rank(), kind, false);
        double hp = RaidConfig.LIEUTENANT_HEALTH.get() + RaidConfig.LIEUTENANT_HEALTH_PER_RAIDER.get() * s.weight;
        lt.getAttribute(Attributes.MAX_HEALTH).setBaseValue(lt.getAttribute(Attributes.MAX_HEALTH).getBaseValue() * hp);
        lt.setHealth(lt.getMaxHealth());
        Vec3 home = Vec3.atCenterOf(g.entrance()).subtract(Vec3.atCenterOf(at)).multiply(1, 0, 1);
        float yaw = home.lengthSqr() < 1e-4 ? 0f : (float) (Mth.atan2(home.z, home.x) * Mth.RAD_TO_DEG) - 90f;
        lt.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0f);
        lt.setYHeadRot(yaw);
        lt.setYBodyRot(yaw);
        GateService.claim(lt, g);
        lt.getPersistentData().putUUID(LIEUTENANT_TAG, g.id());
        lt.setCustomName(Component.translatable(KEY + "lieutenant_name", kind.name(g.rank())));
        lt.setCustomNameVisible(false);
        dungeon.addFreshEntity(lt);
        CONTROLLED.add(lt.getUUID());
        return lt;
    }

    /** Standing room on the floor at or near {@code p}. */
    private static BlockPos floor(ServerLevel level, BlockPos p) {
        BlockPos q = standable(level, p);
        return q == null ? p : q;
    }

    /** Where someone can stand in this column, a few blocks up or down from {@code p} (null: nowhere). */
    @Nullable
    private static BlockPos standable(ServerLevel level, BlockPos p) {
        for (int dy = 3; dy >= -4; dy--) {
            BlockPos q = p.offset(0, dy, 0);
            BlockState below = level.getBlockState(q.below());
            if (level.getBlockState(q).getCollisionShape(level, q).isEmpty() && level.getBlockState(q.above()).getCollisionShape(level, q.above()).isEmpty()
                    && below.isFaceSturdy(level, q.below(), Direction.UP)) return q;
        }
        return null;
    }

    /**
     * A spot 2.5 blocks from a raider, as near the way asked ({@code angle}, radians on their yaw's scale) as the cave
     * allows: the ways either side of it are tried in turn, and their own feet are the last resort.
     */
    private static BlockPos spotNear(ServerLevel level, ServerPlayer p, float angle) {
        for (int attempt = 0; attempt < 9; attempt++) {
            float a = angle + (attempt % 2 == 0 ? 1 : -1) * ((attempt + 1) / 2) * 0.6f;
            BlockPos at = standable(level, BlockPos.containing(p.getX() - Mth.sin(a) * 2.5, p.getY(), p.getZ() + Mth.cos(a) * 2.5));
            if (at != null && Math.abs(at.getY() - p.getY()) <= 3) return at;
        }
        return p.blockPosition();
    }

    /** A raid's packs are bigger ({@code raid.packScale}). */
    public static int packSize(GateInstance g, int count) {
        return g.isRaid() ? (int) Math.ceil(count * RaidConfig.PACK_SCALE.get()) : count;
    }

    /** A raid's clear pays more ({@code rewards.scale}). */
    public static float rewardBonus(GateInstance g) {
        return g.isRaid() ? RaidConfig.REWARD_SCALE.get().floatValue() : 1f;
    }

    /** True while lieutenants still stand: the boss's chamber is sealed (its boss does not wake). */
    public static boolean sealed(GateInstance g) {
        RaidState s = state(g);
        return s != null && !s.lieutenants.isEmpty() && !g.bossDefeated();
    }

    /** The boss intro's subtitle for a raid boss. */
    public static Component bossSubtitle(GateInstance g) {
        return Component.translatable(KEY + "boss_subtitle", g.rank().displayName());
    }

    /**
     * Every half second for each raider inside: lieutenants wake as they come near, and the sealed chamber turns them
     * back while any lieutenant stands.
     */
    public static void watch(ServerPlayer player, GateInstance g) {
        RaidState s = state(g);
        if (s == null || s.phase != RaidState.Phase.RUNNING) return;
        ServerLevel dungeon = player.serverLevel();
        for (UUID id : List.copyOf(s.lieutenants)) {
            if (!(dungeon.getEntity(id) instanceof GateBossEntity lt) || lt.isAwake() || !lt.isAlive()) continue;
            double dx = lt.getX() - player.getX(), dz = lt.getZ() - player.getZ();
            if (dx * dx + dz * dz < 13 * 13 && Math.abs(lt.getY() - player.getY()) < 7) {
                lt.wake();
                for (ServerPlayer p : inside(dungeon, g)) {
                    PacketDistributor.sendToPlayer(p, new GatePayload(GatePayload.BOSS_INTRO, g.theme().ordinal(), lt.getId(), 0,
                            lt.getDisplayName(), Component.translatable(KEY + "lieutenant_subtitle", g.rank().displayName())));
                }
            }
        }
        if (!sealed(g)) return;
        DungeonLayout.Room room = g.layout().boss();
        BlockPos c = g.world(room.center());
        BlockPos at = player.blockPosition();
        if (at.getY() < room.floorY() - 6 || at.getY() > room.topY() + 4) return;
        double dx = at.getX() - c.getX(), dz = at.getZ() - c.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > room.reach() + 2) return;
        // the chamber's seal throws them back the way they came
        Vec3 back = GateService.doorward(g, player.position());
        player.push(back.x * 1.1, 0.35, back.z * 1.1);
        player.hurtMarked = true;
        long now = dungeon.getGameTime();
        if (SAID.getOrDefault(player.getUUID(), 0L) <= now) {
            SAID.put(player.getUUID(), now + 50);
            player.displayClientMessage(Component.translatable(KEY + "chamber_sealed", s.lieutenants.size()), true);
            dungeon.playSound(null, at, SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 1.4f, 0.5f);
            SkillFx.play(player, SkillFxPayload.Kind.DOME, BARRIER, player.position(), 1.6f, 14);
        }
    }

    /** The boss raged at half its health: more creatures come at its call in a raid. */
    public static void bossEnraged(ServerLevel level, GateBossEntity boss, GateInstance g) {
        RaidState s = state(g);
        if (s == null) return;
        int extra = Math.min(4, Math.round(s.weight));
        com.beyondsolo.enemy.EnemyRoster.Mix mix = g.mix();
        for (int i = 0; i < extra; i++) {
            float a = level.random.nextFloat() * Mth.TWO_PI;
            BlockPos p = floor(level, BlockPos.containing(boss.getX() + Mth.cos(a) * (boss.getBbWidth() / 2 + 4), boss.getY(), boss.getZ() + Mth.sin(a) * (boss.getBbWidth() / 2 + 4)));
            var type = mix.pick(level.random);
            Mob add = GateService.claim(com.beyondsolo.enemy.Enemies.spawn(level, g.rank(), type, p,
                    com.beyondsolo.enemy.Enemies.roll(g.rank(), mix, type, level.random, false, true), g.theme().ordinal()), g);
            com.beyondsolo.breaks.DungeonBreaks.adopt(boss, add);
            if (add != null && boss.getTarget() != null) add.setTarget(boss.getTarget());
        }
    }

    // ------------------------------------------------------------------ the fight

    /** Every half second for each raid underway. */
    private static void runTick(MinecraftServer server, ServerLevel dungeon, GateInstance g, long now) {
        RaidState s = state(g);
        Run run = run(g.id());
        List<ServerPlayer> inside = new ArrayList<>();
        for (ServerPlayer p : inside(dungeon, g)) {
            RaidState.Raider r = s.raider(p.getUUID());
            if (r != null && p.isAlive() && !p.isSpectator()) {
                inside.add(p);
                if (r.status != RaidState.Status.INSIDE) {
                    r.status = RaidState.Status.INSIDE;
                    dirty(server);
                }
            }
        }
        // nobody left inside (and nobody on the way in): after a while the raid has failed
        if (inside.isEmpty()) {
            if (GateService.inTransitTo(g.id())) {
                run.emptySince = -1;
                return;
            }
            boolean waiting = false, onIt = false, online = false;
            for (RaidState.Raider r : s.raiders.values()) {
                if (r.status == RaidState.Status.WAITING && OnlinePlayers.get(server, r.id) != null && now - s.startedAt < 20 * 30) waiting = true;
                if (r.status != RaidState.Status.INSIDE && r.status != RaidState.Status.WAITING) continue;
                onIt = true;
                if (OnlinePlayers.get(server, r.id) != null) online = true;
            }
            if (waiting) return;
            // everyone still on the raid is offline (a restart, a lost connection): it waits for them, as a gate does
            if (onIt && !online) {
                run.emptySince = -1;
                return;
            }
            if (run.emptySince < 0) run.emptySince = now;
            else if (now - run.emptySince > 20 * 15) fail(server, g);
            return;
        }
        run.emptySince = -1;
        if (!g.built()) return;
        ensureHunters(dungeon, g, s, inside);
        List<GuildHunterEntity> hunters = hunterBodies(dungeon, s);
        if (now % 20 == 0) aggro(dungeon, hunters);
        supportPulse(dungeon, s, run, inside, hunters, now);
        // the bosses: who each minds, and the phases of the raid boss
        for (UUID id : List.copyOf(s.lieutenants)) {
            if (!(dungeon.getEntity(id) instanceof GateBossEntity lt) || !lt.isAlive()) continue;
            CONTROLLED.add(id);
            if (lt.isAwake()) control(lt, run, s, inside, hunters);
        }
        if (g.boss() != null && dungeon.getEntity(g.boss()) instanceof GateBossEntity boss && boss.isAlive()) grow(dungeon, s, boss);
        if (g.boss() != null && dungeon.getEntity(g.boss()) instanceof GateBossEntity boss && boss.isAlive() && g.bossAwake()) {
            control(boss, run, s, inside, hunters);
            phases(server, dungeon, g, s, run, boss, inside, hunters, now);
        }
        syncInside(server, dungeon, g, inside);
    }

    /**
     * The players in a raid's cave: {@link GateService#playersIn}, and a game test's stand-ins (which the level's
     * player list never holds) standing in it too.
     */
    static List<ServerPlayer> inside(ServerLevel dungeon, GateInstance g) {
        List<ServerPlayer> out = new ArrayList<>();
        if (!GateService.isDungeon(dungeon)) return out;
        GateData data = GateData.get(dungeon.getServer());
        for (ServerPlayer p : OnlinePlayers.all(dungeon.getServer())) {
            if (p.level() != dungeon) continue;
            GateInstance at = data.bySlotPos(p.blockPosition());
            if (at != null && at.id().equals(g.id())) out.add(p);
        }
        return out;
    }

    /** The hunters' bodies in the cave right now. */
    private static List<GuildHunterEntity> hunterBodies(ServerLevel dungeon, RaidState s) {
        List<GuildHunterEntity> out = new ArrayList<>();
        for (RaidState.Hired h : s.hunters) {
            if (h.fallen || h.entity == null) continue;
            if (dungeon.getEntity(h.entity) instanceof GuildHunterEntity e && e.isAlive()) out.add(e);
        }
        return out;
    }

    /** Every hunter on the roster who has not fallen stands beside a raider (brought in, or back after an unload). */
    private static void ensureHunters(ServerLevel dungeon, GateInstance g, RaidState s, List<ServerPlayer> inside) {
        if (s.phase != RaidState.Phase.RUNNING || inside.isEmpty() || !g.built()) return;
        ServerPlayer near = null;
        for (ServerPlayer p : inside) if (p.getUUID().equals(s.leader)) near = p;
        if (near == null) near = inside.get(0);
        Guild guild = null;
        int i = 0, n = 0;
        for (RaidState.Hired h : s.hunters) if (!h.fallen) n++;
        for (RaidState.Hired h : s.hunters) {
            if (h.fallen) continue;
            // behind the raider, fanned out to either side: none of them blocks the way on or stands in front of their eyes
            float a = near.getYRot() * Mth.DEG_TO_RAD + Mth.PI + (i++ - (n - 1) / 2f) * 0.85f;
            if (h.entity != null && dungeon.getEntity(h.entity) instanceof GuildHunterEntity e && e.isAlive()) continue;
            GuildHunterEntity e = GuildModule.HUNTER.get().create(dungeon);
            if (e == null) continue;
            if (guild == null || !guild.id().equals(h.guild)) guild = Guilds.data(dungeon.getServer()).get(h.guild);
            e.configure(h, g.id(), guild == null ? 0x4FA8FF : GuildColors.color(guild.color()), guild == null ? "?" : guild.tag());
            BlockPos at = spotNear(dungeon, near, a);
            e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, near.getYRot(), 0f);
            e.setYHeadRot(near.getYRot());
            dungeon.addFreshEntity(e);
            h.entity = e.getUUID();
            SkillFx.play(e, SkillFxPayload.Kind.BLINK, 0xFFE9B0, e.position(), 1f, 1);
        }
    }

    /** True while the raid still wants this hunter's body in its cave. */
    public static boolean keeps(GuildHunterEntity e) {
        if (!(e.level() instanceof ServerLevel level) || e.gateId() == null) return false;
        GateInstance g = GateData.get(level.getServer()).get(e.gateId());
        if (g == null || !g.isRaid()) return false;
        RaidState s = state(g);
        RaidState.Hired h = e.hunterId() == null ? null : s.hired(e.hunterId());
        if (h == null || h.fallen || !e.getUUID().equals(h.entity)) return false;
        if (s.phase == RaidState.Phase.RUNNING) return true;
        // after the clear they stay a little to see it, then head home
        return s.phase == RaidState.Phase.CLEARED && level.getGameTime() - s.clearedAt < 20 * 12;
    }

    /** The raider a hunter keeps near: the leader if they are inside, else the nearest raider. */
    @Nullable
    public static ServerPlayer followTarget(ServerLevel level, UUID gate, GuildHunterEntity e) {
        GateInstance g = GateData.get(level.getServer()).get(gate);
        if (g == null || !g.isRaid()) return null;
        RaidState s = state(g);
        ServerPlayer best = null;
        double bestD = Double.MAX_VALUE;
        for (ServerPlayer p : inside(level, g)) {
            if (!s.has(p.getUUID()) || !p.isAlive() || p.isSpectator()) continue;
            if (p.getUUID().equals(s.leader)) return p;
            double d = p.distanceToSqr(e);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    /** A hunter left far behind steps out of the air beside their raider. */
    public static void slipTo(GuildHunterEntity e, ServerPlayer p) {
        if (!(e.level() instanceof ServerLevel level)) return;
        // at their back, a little to one side, as on arrival
        float a = p.getYRot() * Mth.DEG_TO_RAD + Mth.PI + (level.random.nextFloat() - 0.5f) * 1.7f;
        BlockPos at = spotNear(level, p, a);
        SkillFx.play(e, SkillFxPayload.Kind.BLINK, 0xFFE9B0, e.position(), 1f, 1);
        e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getYRot(), 0f);
        e.getNavigation().stop();
    }

    /** Creatures standing idle near a hunter go for them. */
    private static void aggro(ServerLevel dungeon, List<GuildHunterEntity> hunters) {
        for (GuildHunterEntity h : hunters) {
            for (Mob m : dungeon.getEntitiesOfClass(Mob.class, h.getBoundingBox().inflate(10, 4, 10),
                    m -> m instanceof Enemy && !(m instanceof GateBossEntity) && m.getTarget() == null && m.isAlive())) {
                m.setTarget(h);
            }
        }
    }

    /** Supports' pulse: every ten seconds the raiders near each support heal a little. */
    private static void supportPulse(ServerLevel dungeon, RaidState s, Run run, List<ServerPlayer> inside, List<GuildHunterEntity> hunters, long now) {
        if (now - run.lastSupport < 200) return;
        run.lastSupport = now;
        float share = RaidConfig.SUPPORT_PULSE.get().floatValue();
        if (share <= 0) return;
        for (ServerPlayer support : inside) {
            RaidState.Raider r = s.raider(support.getUUID());
            if (r == null || r.role != RaidRole.SUPPORT) continue;
            List<LivingEntity> near = new ArrayList<>(inside);
            near.addAll(hunters);
            for (LivingEntity e : near) {
                if (e.distanceToSqr(support) > 8 * 8 || !e.isAlive()) continue;
                Balance.healRaw(e, e.getMaxHealth() * share);
            }
            SkillFx.play(support, SkillFxPayload.Kind.PULSE, RaidRole.SUPPORT.color, support.position(), 8f, 0);
            dungeon.playSound(null, support.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8f, 1.5f);
        }
    }

    /**
     * Picks who a raid boss (or lieutenant) fights: whoever it minds most, and it minds the blows it takes (a
     * vanguard's three times over, {@code roles.vanguardThreat}) and a vanguard standing up to it. Its mind changes only
     * when someone else is clearly ahead.
     */
    private static void control(GateBossEntity boss, Run run, RaidState s, List<ServerPlayer> inside, List<GuildHunterEntity> hunters) {
        Map<UUID, Double> threat = run.threat.computeIfAbsent(boss.getUUID(), k -> new HashMap<>());
        threat.replaceAll((k, v) -> v * 0.97);
        List<LivingEntity> candidates = new ArrayList<>();
        for (ServerPlayer p : inside) if (p.distanceToSqr(boss) < 28 * 28) candidates.add(p);
        for (GuildHunterEntity h : hunters) if (h.distanceToSqr(boss) < 28 * 28) candidates.add(h);
        if (candidates.isEmpty()) return;
        double vanguardHold = boss.getMaxHealth() * 0.004;
        for (LivingEntity c : candidates) {
            if (roleOf(s, c) == RaidRole.VANGUARD && c.distanceToSqr(boss) < 10 * 10) threat.merge(raiderId(c), vanguardHold, Double::sum);
        }
        LivingEntity current = boss.getTarget();
        LivingEntity best = null;
        double bestT = -1;
        for (LivingEntity c : candidates) {
            double t = threat.getOrDefault(raiderId(c), 0.0);
            if (t > bestT) {
                bestT = t;
                best = c;
            }
        }
        if (bestT <= 0) {
            // nobody has done anything yet: the nearest vanguard, else whoever is nearest
            if (current != null && current.isAlive() && candidates.contains(current)) return;
            best = null;
            double bestD = Double.MAX_VALUE;
            for (LivingEntity c : candidates) {
                double d = c.distanceToSqr(boss) * (roleOf(s, c) == RaidRole.VANGUARD ? 0.25 : 1);
                if (d < bestD) {
                    bestD = d;
                    best = c;
                }
            }
        } else if (current != null && current.isAlive() && candidates.contains(current) && best != current) {
            double held = threat.getOrDefault(raiderId(current), 0.0);
            if (bestT < held * 1.15) return;
        }
        if (best == null || best == current) return;
        choosing = true;
        try {
            boss.setTarget(best);
        } finally {
            choosing = false;
        }
    }

    /** A player's id, or a hunter's guild id for a hunter. */
    private static UUID raiderId(Entity e) {
        if (e instanceof GuildHunterEntity h && h.hunterId() != null) return h.hunterId();
        return e.getUUID();
    }

    @Nullable
    private static RaidRole roleOf(RaidState s, Entity e) {
        if (e instanceof GuildHunterEntity h) return h.role();
        RaidState.Raider r = s.raider(e.getUUID());
        return r == null ? null : r.role;
    }

    /** The raid boss's phases: the barrier at 70%, cataclysms from 25%. */
    private static void phases(MinecraftServer server, ServerLevel dungeon, GateInstance g, RaidState s, Run run, GateBossEntity boss,
                               List<ServerPlayer> inside, List<GuildHunterEntity> hunters, long now) {
        float share = boss.getHealth() / Math.max(1f, boss.getMaxHealth());
        if (!s.barrierDone && !s.barrierUp && share <= BARRIER_AT) raiseBarrier(dungeon, g, s, run, boss, inside, now);
        if (s.barrierUp) {
            SHIELDED.add(boss.getUUID());
            List<RaidPylonEntity> standing = dungeon.getEntitiesOfClass(RaidPylonEntity.class, boss.getBoundingBox().inflate(48, 16, 48),
                    p -> g.id().equals(p.gateId()) && p.isAlive());
            // the chamber was unloaded while it stood: its pylons rise again
            if (standing.isEmpty() && s.pylonsLeft > 0) placePylons(dungeon, g, s, boss, s.pylonsLeft);
            if (now % 100 == 0) SkillFx.play(boss, SkillFxPayload.Kind.DOME, BARRIER, boss.position(), boss.getBbWidth() + 1.5f, 110);
            // it draws on them: every fifteen seconds a little health for each pylon still standing
            if (now - run.lastBarrierDraw >= 300) {
                run.lastBarrierDraw = now;
                Balance.healRaw(boss, boss.getMaxHealth() * 0.03f * Math.max(1, standing.size()));
                EnemyFx.burst(dungeon, boss.position().add(0, boss.getBbHeight() * 0.5, 0), BARRIER, boss.getBbWidth() + 1);
            }
        } else {
            SHIELDED.remove(boss.getUUID());
        }
        if (share <= CATACLYSM_AT && !s.barrierUp) {
            if (run.nextCataclysm < 0) run.nextCataclysm = now + 60;
            if (run.cataclysmLands < 0 && now >= run.nextCataclysm) beginCataclysm(dungeon, g, run, boss, inside, hunters, now);
        }
        if (run.cataclysmLands >= 0 && now >= run.cataclysmLands) landCataclysm(dungeon, g, s, run, boss, inside, hunters, now);
    }

    private static void raiseBarrier(ServerLevel dungeon, GateInstance g, RaidState s, Run run, GateBossEntity boss, List<ServerPlayer> inside, long now) {
        s.barrierUp = true;
        int raiders = s.raiders.size() + s.hunters.size();
        s.pylonsTotal = RaidConfig.PYLONS.get() + Math.max(0, (raiders - 4) / 4);
        s.pylonsLeft = s.pylonsTotal;
        run.lastBarrierDraw = now;
        SHIELDED.add(boss.getUUID());
        placePylons(dungeon, g, s, boss, s.pylonsLeft);
        dirty(dungeon.getServer());
        SkillFx.play(boss, SkillFxPayload.Kind.DOME, BARRIER, boss.position(), boss.getBbWidth() + 1.5f, 110);
        EnemyFx.burst(dungeon, boss.position().add(0, boss.getBbHeight() * 0.5, 0), BARRIER, boss.getBbWidth() + 3);
        dungeon.playSound(null, boss.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 3f, 0.5f);
        dungeon.playSound(null, boss.blockPosition(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.HOSTILE, 2.5f, 0.6f);
        for (ServerPlayer p : inside) {
            banner(p, Component.translatable(KEY + "banner.barrier"), Component.translatable(KEY + "banner.barrier.sub", s.pylonsTotal), BARRIER, "shield");
        }
    }

    private static void placePylons(ServerLevel dungeon, GateInstance g, RaidState s, GateBossEntity boss, int count) {
        DungeonLayout.Room room = g.layout().boss();
        BlockPos c = g.world(new BlockPos(room.center().getX(), room.floorY(), room.center().getZ()));
        int raiders = s.raiders.size() + s.hunters.size();
        int hits = RaidConfig.PYLON_HITS.get() + Math.max(0, (raiders - 1) / 2);
        double radius = Math.max(6, room.reach() * 0.6);
        float start = dungeon.random.nextFloat() * Mth.TWO_PI;
        for (int i = 0; i < count; i++) {
            float a = start + i * Mth.TWO_PI / count;
            BlockPos at = floor(dungeon, BlockPos.containing(c.getX() + 0.5 + Mth.cos(a) * radius, c.getY(), c.getZ() + 0.5 + Mth.sin(a) * radius));
            RaidPylonEntity pylon = RaidModule.PYLON.get().create(dungeon);
            if (pylon == null) continue;
            pylon.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
            pylon.configure(g.id(), boss, hits);
            dungeon.addFreshEntity(pylon);
            EnemyFx.pillar(dungeon, Vec3.atBottomCenterOf(at), BARRIER, 1.2f, 6f);
        }
    }

    /** A pylon broke; the last one brings the barrier down and the boss reels. */
    public static void pylonBroken(ServerLevel level, UUID gate) {
        MinecraftServer server = level.getServer();
        GateInstance g = GateData.get(server).get(gate);
        if (g == null || !g.isRaid()) return;
        RaidState s = state(g);
        if (!s.barrierUp) return;
        s.pylonsLeft = Math.max(0, s.pylonsLeft - 1);
        dirty(server);
        ServerLevel dungeon = GateService.dungeon(server);
        if (dungeon == null) return;
        List<ServerPlayer> inside = inside(dungeon, g);
        if (s.pylonsLeft > 0) {
            for (ServerPlayer p : inside) p.displayClientMessage(Component.translatable(KEY + "pylon_down", s.pylonsLeft), true);
            syncInside(server, dungeon, g, inside);
            return;
        }
        s.barrierUp = false;
        s.barrierDone = true;
        if (g.boss() != null && dungeon.getEntity(g.boss()) instanceof GateBossEntity boss) {
            SHIELDED.remove(boss.getUUID());
            boss.stagger(RaidConfig.STAGGER_SECONDS.get() * 20);
            EnemyFx.burst(dungeon, boss.position().add(0, boss.getBbHeight() * 0.5, 0), 0xFFFFFF, boss.getBbWidth() + 4);
            SkillFx.play(boss, SkillFxPayload.Kind.NOVA, BARRIER, boss.position(), boss.getBbWidth() + 6, 3);
            GateService.shake(dungeon, boss, 1.2f, 30);
            dungeon.playSound(null, boss.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 3f, 0.4f);
            dungeon.playSound(null, boss.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 2f, 0.8f);
        }
        for (ServerPlayer p : inside) {
            banner(p, Component.translatable(KEY + "banner.broken"), Component.translatable(KEY + "banner.broken.sub",
                    Math.round(RaidConfig.STAGGER_DAMAGE.get() * 100)), 0xFFFFFF, "lightning");
        }
        syncInside(server, dungeon, g, inside);
    }

    private static void beginCataclysm(ServerLevel dungeon, GateInstance g, Run run, GateBossEntity boss, List<ServerPlayer> inside,
                                       List<GuildHunterEntity> hunters, long now) {
        run.cataclysmLands = now + CHANNEL_TICKS;
        run.refuges.clear();
        DungeonLayout.Room room = g.layout().boss();
        BlockPos c = g.world(new BlockPos(room.center().getX(), room.floorY(), room.center().getZ()));
        int count = 2 + (inside.size() + hunters.size() >= 6 ? 1 : 0);
        RandomSource r = dungeon.random;
        for (int attempt = 0; attempt < 40 && run.refuges.size() < count; attempt++) {
            float a = r.nextFloat() * Mth.TWO_PI;
            double d = room.reach() * (0.25 + r.nextDouble() * 0.5);
            BlockPos at = floor(dungeon, BlockPos.containing(c.getX() + 0.5 + Mth.cos(a) * d, c.getY(), c.getZ() + 0.5 + Mth.sin(a) * d));
            Vec3 v = Vec3.atBottomCenterOf(at);
            boolean apart = true;
            for (Vec3 o : run.refuges) if (o.distanceToSqr(v) < 7 * 7) apart = false;
            if (apart) run.refuges.add(v);
        }
        boss.channel(CHANNEL_TICKS);
        SkillFx.play(boss, SkillFxPayload.Kind.CAST, DOOM, boss.position(), boss.getBbWidth() + 3, 0);
        EnemyFx.telegraphCircle(dungeon, Vec3.atBottomCenterOf(c), room.reach() + 2, CHANNEL_TICKS, DOOM);
        for (Vec3 v : run.refuges) {
            SkillFx.play(boss, SkillFxPayload.Kind.ZONE, GOLD, v.add(0, 0.05, 0), REFUGE_RADIUS, CHANNEL_TICKS + 10);
            EnemyFx.pillar(dungeon, v, GOLD, 1.4f, 8f);
        }
        dungeon.playSound(null, boss.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.5f, 0.6f);
        for (ServerPlayer p : inside) {
            CompoundTag t = new CompoundTag();
            t.putInt("ticks", CHANNEL_TICKS);
            PacketDistributor.sendToPlayer(p, new RaidEventPayload(RaidEventPayload.CATACLYSM, t,
                    Component.translatable(KEY + "banner.cataclysm"), Component.translatable(KEY + "banner.cataclysm.sub")));
        }
        // the hunters run for the nearest light
        for (GuildHunterEntity h : hunters) {
            Vec3 best = null;
            for (Vec3 v : run.refuges) if (best == null || v.distanceToSqr(h.position()) < best.distanceToSqr(h.position())) best = v;
            if (best != null) h.seekRefuge(best, now + CHANNEL_TICKS + 15);
        }
    }

    private static void landCataclysm(ServerLevel dungeon, GateInstance g, RaidState s, Run run, GateBossEntity boss, List<ServerPlayer> inside,
                                      List<GuildHunterEntity> hunters, long now) {
        run.cataclysmLands = -1;
        run.nextCataclysm = now + RaidConfig.CATACLYSM_SECONDS.get() * 20L;
        DungeonLayout.Room room = g.layout().boss();
        BlockPos c = g.world(room.center());
        float share = RaidConfig.CATACLYSM_DAMAGE.get().floatValue();
        List<LivingEntity> raiders = new ArrayList<>(inside);
        raiders.addAll(hunters);
        for (LivingEntity e : raiders) {
            double dx = e.getX() - c.getX(), dz = e.getZ() - c.getZ();
            if (Math.sqrt(dx * dx + dz * dz) > room.reach() + 6 || e.getY() < room.floorY() - 6 || e.getY() > room.topY() + 4) continue;
            boolean safe = false;
            for (Vec3 v : run.refuges) {
                double rx = e.getX() - v.x, rz = e.getZ() - v.z;
                if (rx * rx + rz * rz <= (REFUGE_RADIUS + 0.4) * (REFUGE_RADIUS + 0.4)) safe = true;
            }
            if (safe) continue;
            e.hurt(boss.damageSources().indirectMagic(boss, boss), e.getMaxHealth() * share);
        }
        EnemyFx.burst(dungeon, boss.position().add(0, boss.getBbHeight() * 0.5, 0), DOOM, room.reach());
        SkillFx.play(boss, SkillFxPayload.Kind.NOVA, DOOM, boss.position(), room.reach() + 2, 4);
        for (Vec3 v : run.refuges) EnemyFx.pillar(dungeon, v, GOLD, 2f, 10f);
        // it has landed: the hunters leave the lights and fight on
        for (GuildHunterEntity h : hunters) h.seekRefuge(null, 0);
        GateService.shake(dungeon, boss, 1.4f, room.reach() + 20);
        dungeon.playSound(null, boss.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3f, 0.5f);
        dungeon.playSound(null, boss.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 2.5f, 0.5f);
        run.refuges.clear();
    }

    // ------------------------------------------------------------------ the end

    /** The raid boss fell: the raid is cleared. More caches, guild XP, hunters' levels, titles and the results. */
    public static void bossDefeated(ServerLevel level, GateInstance g, GateBossEntity boss, List<ServerPlayer> players) {
        RaidState s = state(g);
        if (s == null || s.phase == RaidState.Phase.CLEARED) return;
        MinecraftServer server = level.getServer();
        s.phase = RaidState.Phase.CLEARED;
        s.clearedAt = level.getGameTime();
        s.barrierUp = false;
        SHIELDED.remove(boss.getUUID());
        CONTROLLED.remove(boss.getUUID());
        dirty(server);
        Run run = RUNS.remove(g.id());
        List<ServerPlayer> raiders = new ArrayList<>();
        for (ServerPlayer p : players) if (s.has(p.getUUID()) && !raiders.contains(p)) raiders.add(p);
        for (ServerPlayer p : inside(level, g)) if (s.has(p.getUUID()) && !raiders.contains(p)) raiders.add(p);
        // the boss is the clear: lieutenants felled while a raider was elsewhere (or never counted) no longer stand in the
        // way of their quest, as a gate's leftover creatures don't
        for (ServerPlayer p : raiders) QuestEngine.progress(p, LIEUTENANT_KEY, Short.MAX_VALUE);

        // a cache for each raider (the boss's own is the first), around where it fell
        BlockPos center = boss.blockPosition();
        Vec3 toDoor = GateService.doorward(g, boss.position());
        Direction facing = Direction.getNearest(toDoor.x, 0, toDoor.z);
        int caches = Math.min(RaidConfig.CACHES.get(), Math.max(1, raiders.size())) - 1;
        ServerScheduler.schedule(GateBossEntity.DEATH_TICKS - 4, () -> {
            for (int i = 0; i < caches; i++) {
                int side = i % 2 == 0 ? 1 : -1;
                int step = 3 + 2 * (i / 2);
                BlockPos p = floor(level, center.relative(facing, 3).relative(side > 0 ? facing.getClockWise() : facing.getCounterClockWise(), step));
                level.setBlock(p, ModBlocks.REWARD_CACHE.get().defaultBlockState().setValue(RewardCacheBlock.FACING, facing), 3);
                if (level.getBlockEntity(p) instanceof RewardCacheBlockEntity cache) cache.setup(g.rank(), g.bossKind(), g.bossAscended(), level.getGameTime());
            }
        });

        // the guilds who were there grow; their hunters learn from it
        Map<UUID, List<UUID>> byGuild = new LinkedHashMap<>();
        for (ServerPlayer p : raiders) {
            Guild guild = Guilds.of(p);
            if (guild != null) byGuild.computeIfAbsent(guild.id(), k -> new ArrayList<>()).add(p.getUUID());
        }
        for (RaidState.Hired h : s.hunters) byGuild.computeIfAbsent(h.guild, k -> new ArrayList<>());
        for (Map.Entry<UUID, List<UUID>> e : byGuild.entrySet()) {
            Guild guild = Guilds.data(server).get(e.getKey());
            if (guild != null) Guilds.raidCleared(server, guild, g.rank(), e.getValue());
        }
        List<CompoundTag> lines = new ArrayList<>();
        for (RaidState.Hired h : s.hunters) {
            int levels = h.fallen ? 0 : Mth.clamp(g.rank().tier() - Balance.rankFor(h.level).tier() + 1, 1, 3);
            int gained = Guilds.hunterRaided(server, h.guild, h.hunter, levels, h.kills);
            CompoundTag t = line(h.name, true, h.role(), run == null ? 0 : run.dealt.getOrDefault(h.hunter, 0.0), h.cls.ordinal());
            t.putInt("levels", gained);
            t.putBoolean("fallen", h.fallen);
            lines.add(t);
        }
        Guild hunterGuild = s.hunters.isEmpty() ? null : Guilds.data(server).get(s.hunters.get(0).guild);
        if (hunterGuild != null) Guilds.syncGuild(server, hunterGuild);

        // the titles, and the best of them
        int size = s.raiders.size() + s.hunters.size();
        ServerPlayer mvp = null;
        double most = 0;
        for (RaidState.Raider r : s.raiders.values()) {
            double dealt = run == null ? 0 : run.dealt.getOrDefault(r.id, 0.0);
            CompoundTag t = line(r.name, false, r.role, dealt, r.cls);
            t.putBoolean("fallen", r.status == RaidState.Status.FALLEN);
            lines.add(t);
            ServerPlayer p = OnlinePlayers.get(server, r.id);
            if (p != null && raiders.contains(p) && dealt > most) {
                most = dealt;
                mvp = p;
            }
        }
        for (ServerPlayer p : raiders) SystemAPI.setTitle(p, BREAKER_TITLE);
        if (mvp != null && size >= 4) SystemAPI.setTitle(mvp, MVP_TITLE);

        lines.sort(Comparator.comparingDouble((CompoundTag t) -> -t.getDouble("dealt")));
        CompoundTag results = new CompoundTag();
        results.putBoolean("cleared", true);
        results.putInt("rank", g.rank().ordinal());
        results.putLong("ticks", s.startedAt < 0 ? 0 : s.clearedAt - s.startedAt);
        results.putString("mvp", mvp == null ? "" : mvp.getGameProfile().getName());
        results.putFloat("scale", rewardBonus(g));
        results.putLong("guildXp", com.beyondsolo.guild.GuildPerks.raidXp(g.rank()));
        results.putInt("caches", caches + 1);
        ListTag list = new ListTag();
        list.addAll(lines);
        results.put("lines", list);
        Component dungeonName = g.dungeonName();
        ServerScheduler.schedule(GateBossEntity.DEATH_TICKS + 40, () -> {
            for (ServerPlayer p : raiders) {
                if (p.connection == null) continue;
                PacketDistributor.sendToPlayer(p, new RaidEventPayload(RaidEventPayload.RESULTS, results,
                        Component.translatable(KEY + "results.cleared"), dungeonName));
                sync(p);
            }
        });
        syncInside(server, level, g, raiders);
    }

    private static CompoundTag line(String name, boolean hunter, RaidRole role, double dealt, int cls) {
        CompoundTag t = new CompoundTag();
        t.putString("name", name);
        t.putBoolean("hunter", hunter);
        t.putString("role", role.id());
        t.putDouble("dealt", dealt);
        t.putInt("cls", cls);
        return t;
    }

    /** Every raider fell or left: the raid failed. The gate shuts; the hunters still standing make their way home. */
    public static void fail(MinecraftServer server, GateInstance g) {
        RaidState s = state(g);
        if (s == null || s.phase != RaidState.Phase.RUNNING) return;
        s.phase = RaidState.Phase.FAILED;
        g.setState(GateInstance.State.CLOSED);
        dirty(server);
        Run run = RUNS.remove(g.id());
        ServerLevel dungeon = GateService.dungeon(server);
        if (dungeon != null) {
            for (RaidState.Hired h : s.hunters) {
                if (h.entity != null && dungeon.getEntity(h.entity) instanceof GuildHunterEntity e) e.leave();
            }
            if (g.boss() != null) {
                CONTROLLED.remove(g.boss());
                SHIELDED.remove(g.boss());
            }
            for (UUID id : s.lieutenants) CONTROLLED.remove(id);
        }
        GateBlockEntity gate = worldGate(server, g);
        if (gate != null) GateService.close((ServerLevel) gate.getLevel(), gate);
        CompoundTag results = new CompoundTag();
        results.putBoolean("cleared", false);
        results.putInt("rank", g.rank().ordinal());
        results.putLong("ticks", s.startedAt < 0 ? 0 : server.overworld().getGameTime() - s.startedAt);
        ListTag list = new ListTag();
        for (RaidState.Raider r : s.raiders.values()) {
            CompoundTag t = line(r.name, false, r.role, run == null ? 0 : run.dealt.getOrDefault(r.id, 0.0), r.cls);
            t.putBoolean("fallen", r.status == RaidState.Status.FALLEN);
            list.add(t);
        }
        for (RaidState.Hired h : s.hunters) {
            CompoundTag t = line(h.name, true, h.role(), run == null ? 0 : run.dealt.getOrDefault(h.hunter, 0.0), h.cls.ordinal());
            t.putBoolean("fallen", h.fallen);
            list.add(t);
        }
        results.put("lines", list);
        for (ServerPlayer p : roster(server, s)) {
            endQuest(p, g.id(), QuestEvent.FailReason.BROKEN, Component.translatable(KEY + "quest.failed"));
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.ALERT,
                    Component.translatable("notify.beyondsolo.raid.failed.title"), Component.translatable("notify.beyondsolo.raid.failed.body", g.rank().displayName())));
            PacketDistributor.sendToPlayer(p, new RaidEventPayload(RaidEventPayload.RESULTS, results, Component.translatable(KEY + "results.failed"), g.dungeonName()));
            sync(p);
        }
    }

    /** A hired hunter fell: out of the raid, and hurt for a while. */
    public static void hunterDown(ServerLevel level, GuildHunterEntity e) {
        MinecraftServer server = level.getServer();
        if (e.gateId() == null || e.hunterId() == null) return;
        GateInstance g = GateData.get(server).get(e.gateId());
        if (g == null || !g.isRaid()) return;
        RaidState s = state(g);
        RaidState.Hired h = s.hired(e.hunterId());
        if (h == null || h.fallen) return;
        h.fallen = true;
        dirty(server);
        Guilds.hunterFell(server, h.guild, h.hunter);
        for (ServerPlayer p : inside(level, g)) p.displayClientMessage(Component.translatable(KEY + "hunter_down", h.name), true);
    }

    // ------------------------------------------------------------------ keeping clients up to date

    /** Sends a player the raid they are on the roster of (or none). */
    public static void sync(ServerPlayer player) {
        if (player.connection == null) return;
        GateInstance g = raidOf(player.server, player.getUUID());
        if (g == null) {
            // a cleared raid they were in still shows until they leave its cave
            GateInstance here = GateService.isDungeon(player.level()) ? GateData.get(player.server).bySlotPos(player.blockPosition()) : null;
            if (here != null && here.isRaid() && here.raid().has(player.getUUID()) && here.raid().phase == RaidState.Phase.CLEARED) g = here;
        }
        if (g == null) {
            PacketDistributor.sendToPlayer(player, RaidSyncPayload.none());
            return;
        }
        ServerLevel dungeon = GateService.dungeon(player.server);
        PacketDistributor.sendToPlayer(player, new RaidSyncPayload(syncTag(player.server, dungeon, g, player.getUUID()), g.dungeonName(), g.bossName()));
    }

    /** Sends everyone on a raid's roster the raid as it stands. */
    public static void syncRaid(MinecraftServer server, GateInstance g) {
        for (ServerPlayer p : roster(server, state(g))) sync(p);
    }

    private static void syncInside(MinecraftServer server, ServerLevel dungeon, GateInstance g, List<ServerPlayer> inside) {
        for (ServerPlayer p : inside) {
            if (p.connection == null) continue;
            PacketDistributor.sendToPlayer(p, new RaidSyncPayload(syncTag(server, dungeon, g, p.getUUID()), g.dungeonName(), g.bossName()));
        }
    }

    /** The online players on a raid's roster. */
    public static List<ServerPlayer> roster(MinecraftServer server, @Nullable RaidState s) {
        List<ServerPlayer> out = new ArrayList<>();
        if (s == null) return out;
        for (UUID id : s.raiders.keySet()) {
            ServerPlayer p = OnlinePlayers.get(server, id);
            if (p != null) out.add(p);
        }
        return out;
    }

    /** Everything the briefing and the raid HUD show, for this player. */
    public static CompoundTag syncTag(MinecraftServer server, @Nullable ServerLevel dungeon, GateInstance g, UUID me) {
        RaidState s = state(g);
        long now = server.overworld().getGameTime();
        CompoundTag t = new CompoundTag();
        t.putBoolean("active", true);
        t.putUUID("gate", g.id());
        t.putString("phase", s.phase.name());
        t.putInt("rank", g.rank().ordinal());
        t.putInt("realm", g.realm().ordinal());
        if (s.leader != null) t.putUUID("leader", s.leader);
        t.putString("leaderName", s.leaderName());
        RaidState.Raider mine = s.raider(me);
        t.putString("role", mine == null ? "" : mine.role.id());
        t.putString("status", mine == null ? "" : mine.status.name());
        t.putInt("countdown", s.phase == RaidState.Phase.COUNTDOWN ? (int) Math.max(0, s.countdownEnd - now) : -1);
        t.putInt("maxSize", RaidConfig.MAX_SIZE.get());
        t.putBoolean("bring", s.bringHunters);
        t.putInt("lieutenants", s.lieutenantsTotal > 0 ? s.lieutenantsTotal : lieutenantCount(g));
        t.putInt("lieutenantsLeft", s.lieutenantsTotal > 0 ? s.lieutenants.size() : lieutenantCount(g));
        t.putBoolean("barrier", s.barrierUp);
        t.putBoolean("barrierDone", s.barrierDone);
        t.putInt("pylons", s.pylonsTotal);
        t.putInt("pylonsLeft", s.pylonsLeft);
        t.putLong("elapsed", s.startedAt < 0 ? 0 : (s.phase == RaidState.Phase.CLEARED ? s.clearedAt : now) - s.startedAt);
        t.putFloat("reward", rewardBonus(g));
        t.putLong("guildXp", com.beyondsolo.guild.GuildPerks.raidXp(g.rank()));
        t.putInt("x", g.gatePos().getX());
        t.putInt("z", g.gatePos().getZ());
        // the hunters the leader's guild would bring (before the start), or those who came
        ListTag hunters = new ListTag();
        if (s.phase == RaidState.Phase.MUSTER || s.phase == RaidState.Phase.COUNTDOWN) {
            Guild guild = s.leader == null ? null : Guilds.of(server, s.leader);
            int room = RaidConfig.MAX_SIZE.get() - s.raiders.size();
            t.putInt("guildHunters", guild == null ? 0 : Guilds.fit(server, guild).size());
            if (guild != null && s.bringHunters) {
                for (GuildHunter h : Guilds.fit(server, guild)) {
                    if (room-- <= 0 || hunterBusy(server, h.id())) continue;
                    CompoundTag ht = new CompoundTag();
                    ht.putUUID("id", h.id());
                    ht.putString("name", h.name());
                    ht.putInt("cls", h.playerClass().ordinal());
                    ht.putInt("level", h.level());
                    ht.putInt("skin", h.skin());
                    ht.putString("role", h.role().id());
                    ht.putFloat("hp", 1);
                    hunters.add(ht);
                }
            }
        } else {
            for (RaidState.Hired h : s.hunters) {
                CompoundTag ht = new CompoundTag();
                ht.putUUID("id", h.hunter);
                ht.putString("name", h.name);
                ht.putInt("cls", h.cls.ordinal());
                ht.putInt("level", h.level);
                ht.putInt("skin", h.skin);
                ht.putString("role", h.role().id());
                ht.putBoolean("fallen", h.fallen);
                float hp = h.fallen ? 0 : 1;
                if (!h.fallen && dungeon != null && h.entity != null && dungeon.getEntity(h.entity) instanceof GuildHunterEntity e) {
                    hp = e.getHealth() / Math.max(1, e.getMaxHealth());
                }
                ht.putFloat("hp", hp);
                hunters.add(ht);
            }
        }
        t.put("hunters", hunters);
        ListTag raiders = new ListTag();
        for (RaidState.Raider r : s.raiders.values()) {
            CompoundTag rt = new CompoundTag();
            rt.putUUID("id", r.id);
            rt.putString("name", r.name);
            rt.putString("role", r.role.id());
            rt.putString("status", r.status.name());
            ServerPlayer p = OnlinePlayers.get(server, r.id);
            rt.putBoolean("online", p != null);
            if (p != null && SystemAPI.isAwakened(p)) {
                SystemData d = SystemAPI.get(p);
                rt.putInt("cls", d.playerClass() == null ? -1 : d.playerClass().ordinal());
                rt.putInt("level", d.level());
                rt.putInt("prank", d.rank() == null ? -1 : d.rank().ordinal());
            } else {
                rt.putInt("cls", r.cls);
                rt.putInt("level", r.level);
                rt.putInt("prank", -1);
            }
            rt.putFloat("hp", p == null || !p.isAlive() ? 0 : p.getHealth() / Math.max(1, p.getMaxHealth()));
            raiders.add(rt);
        }
        t.put("raiders", raiders);
        // the boss
        float bossHp = 1;
        boolean awake = g.bossAwake() && !g.bossDefeated();
        if (dungeon != null && g.boss() != null && dungeon.getEntity(g.boss()) instanceof GateBossEntity boss) {
            bossHp = boss.getHealth() / Math.max(1, boss.getMaxHealth());
        }
        if (g.bossDefeated()) bossHp = 0;
        t.putBoolean("bossAwake", awake);
        t.putFloat("bossHp", bossHp);
        Run run = RUNS.get(g.id());
        t.putInt("cataclysm", run == null || run.cataclysmLands < 0 ? -1 : (int) Math.max(0, run.cataclysmLands - now));
        return t;
    }

    /** A line across the screen of a raider. */
    public static void banner(ServerPlayer p, Component title, Component text, int color, String icon) {
        CompoundTag t = new CompoundTag();
        t.putInt("color", color);
        t.putString("icon", icon);
        PacketDistributor.sendToPlayer(p, new RaidEventPayload(RaidEventPayload.BANNER, t, title, text));
    }

    // ------------------------------------------------------------------ the briefing's buttons

    /** A briefing button: does it and tells the client how it went. */
    public static void handle(ServerPlayer player, RaidActionPayload p) {
        CompoundTag a = p.args();
        Outcome out = switch (p.action()) {
            case RaidActionPayload.JOIN -> {
                GateInstance g = a.hasUUID("gate") ? GateData.get(player.server).get(a.getUUID("gate")) : null;
                if (g == null || !g.isRaid()) yield Outcome.no("none");
                if (!player.level().dimension().equals(g.gateDimension())
                        || player.distanceToSqr(Vec3.atCenterOf(g.gatePos())) > Math.pow(RaidConfig.MUSTER_RANGE.get(), 2)) yield Outcome.no("too_far_join");
                yield join(player, g);
            }
            case RaidActionPayload.LEAVE -> leave(player);
            case RaidActionPayload.ROLE -> {
                RaidRole role = RaidRole.byId(a.getString("role"));
                yield role == null ? Outcome.no("none") : role(player, role);
            }
            case RaidActionPayload.START -> start(player);
            case RaidActionPayload.BRING -> bring(player, a.getBoolean("on"));
            default -> null;
        };
        if (out == null) return;
        CompoundTag t = new CompoundTag();
        t.putBoolean("ok", out.ok());
        PacketDistributor.sendToPlayer(player, new RaidEventPayload(RaidEventPayload.OUTCOME, t, out.message(), Component.empty()));
    }

    /** The raid gate nearest to a player within the muster range, or null. */
    @Nullable
    public static GateInstance nearest(ServerPlayer player) {
        GateInstance best = null;
        double bestD = Math.pow(RaidConfig.MUSTER_RANGE.get(), 2);
        for (GateInstance g : all(player.server)) {
            if (!g.raid().phase.open() || !g.gateDimension().equals(player.level().dimension())) continue;
            double d = player.distanceToSqr(Vec3.atCenterOf(g.gatePos()));
            if (d <= bestD) {
                bestD = d;
                best = g;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 10 != 0) return;
        long now = server.overworld().getGameTime();
        ServerLevel dungeon = GateService.dungeon(server);
        for (GateInstance g : all(server)) {
            RaidState s = g.raid();
            switch (s.phase) {
                case MUSTER, COUNTDOWN -> musterTick(server, g, now);
                case RUNNING -> {
                    if (dungeon != null) runTick(server, dungeon, g, now);
                }
                default -> {
                }
            }
        }
    }

    /** The barrier turns every blow aside; a reeling boss takes more; vanguards take less and strikers deal more. */
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        if (SHIELDED.contains(victim.getUUID()) && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
            if (victim.tickCount % 8 == 0 && victim.level() instanceof ServerLevel server) {
                server.playSound(null, victim.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.2f, 0.6f);
                SkillFx.play(victim, SkillFxPayload.Kind.DOME, BARRIER, victim.position(), victim.getBbWidth() + 1.5f, 12);
            }
            return;
        }
        if (victim instanceof GateBossEntity boss && boss.staggered() && CONTROLLED.contains(boss.getUUID())) {
            event.setAmount(event.getAmount() * (1f + RaidConfig.STAGGER_DAMAGE.get().floatValue()));
        }
        if (!(victim.level() instanceof ServerLevel level) || !GateService.isDungeon(level)) return;
        if (victim instanceof ServerPlayer p) {
            RaidRole role = runningRole(p);
            if (role == RaidRole.VANGUARD) event.setAmount(event.getAmount() * (1f - RaidConfig.VANGUARD_GUARD.get().floatValue()));
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && !(victim instanceof net.minecraft.world.entity.player.Player)) {
            RaidRole role = runningRole(attacker);
            if (role == RaidRole.STRIKER) event.setAmount(event.getAmount() * (1f + RaidConfig.STRIKER_DAMAGE.get().floatValue()));
        }
    }

    /** The role a player fights in, if they are inside a running raid. */
    @Nullable
    private static RaidRole runningRole(ServerPlayer p) {
        GateInstance g = GateData.get(p.server).bySlotPos(p.blockPosition());
        if (g == null || !g.isRaid() || g.raid().phase != RaidState.Phase.RUNNING) return null;
        RaidState.Raider r = g.raid().raider(p.getUUID());
        return r == null ? null : r.role;
    }

    /** Who dealt what (for the results and the best of them), and what the bosses mind. */
    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || !GateService.isDungeon(level) || event.getNewDamage() <= 0) return;
        Entity by = event.getSource().getEntity();
        if (!(by instanceof ServerPlayer) && !(by instanceof GuildHunterEntity)) return;
        GateInstance g = GateData.get(level.getServer()).bySlotPos(victim.blockPosition());
        if (g == null || !g.isRaid() || g.raid().phase != RaidState.Phase.RUNNING) return;
        RaidState s = g.raid();
        if (by instanceof ServerPlayer p && !s.has(p.getUUID())) return;
        Run run = run(g.id());
        UUID who = raiderId(by);
        float dealt = event.getNewDamage();
        run.dealt.merge(who, (double) dealt, Double::sum);
        if (CONTROLLED.contains(victim.getUUID())) {
            double k = roleOf(s, by) == RaidRole.VANGUARD ? RaidConfig.VANGUARD_THREAT.get() : 1.0;
            run.threat.computeIfAbsent(victim.getUUID(), x -> new HashMap<>()).merge(who, dealt * k, Double::sum);
        }
    }

    /** Raiders fall out of the raid; lieutenants fall and the seal weakens; hunters count their kills. */
    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        if (victim instanceof ServerPlayer p) {
            GateInstance g = GateService.isDungeon(level) ? GateData.get(server).bySlotPos(p.blockPosition()) : null;
            if (g == null || !g.isRaid() || g.raid().phase != RaidState.Phase.RUNNING) return;
            RaidState.Raider r = g.raid().raider(p.getUUID());
            if (r == null) return;
            r.status = RaidState.Status.FALLEN;
            dirty(server);
            endQuest(p, g.id(), QuestEvent.FailReason.DIED, null);
            for (ServerPlayer m : inside(level, g)) {
                if (m != p) m.displayClientMessage(Component.translatable(KEY + "raider_down", r.name), true);
            }
            ServerScheduler.schedule(2, () -> sync(p));
            return;
        }
        if (victim instanceof GateBossEntity lt && victim.getPersistentData().hasUUID(LIEUTENANT_TAG)) {
            GateInstance g = GateData.get(server).get(victim.getPersistentData().getUUID(LIEUTENANT_TAG));
            if (g == null || !g.isRaid()) return;
            lieutenantDown(level, g, lt);
            return;
        }
        if (event.getSource().getEntity() instanceof GuildHunterEntity hunter && hunter.gateId() != null && hunter.hunterId() != null) {
            GateInstance g = GateData.get(server).get(hunter.gateId());
            RaidState.Hired h = g == null || !g.isRaid() ? null : g.raid().hired(hunter.hunterId());
            if (h != null) h.kills++;
        }
    }

    private static void lieutenantDown(ServerLevel level, GateInstance g, GateBossEntity lt) {
        RaidState s = state(g);
        if (!s.lieutenants.remove(lt.getUUID())) return;
        CONTROLLED.remove(lt.getUUID());
        MinecraftServer server = level.getServer();
        dirty(server);
        List<ServerPlayer> inside = inside(level, g);
        for (ServerPlayer p : inside) {
            if (s.has(p.getUUID())) QuestEngine.progress(p, LIEUTENANT_KEY, 1);
            if (s.lieutenants.isEmpty()) {
                banner(p, Component.translatable(KEY + "banner.unsealed"), Component.translatable(KEY + "banner.unsealed.sub", g.bossName()), GOLD, "unlock");
            } else {
                banner(p, Component.translatable(KEY + "banner.lieutenant"), Component.translatable(KEY + "banner.lieutenant.sub", s.lieutenants.size()), GOLD, "skull");
            }
        }
        // its cache comes down once it has gone
        BlockPos at = lt.blockPosition();
        BossKind kind = lt.kind();
        ServerScheduler.schedule(GateBossEntity.DEATH_TICKS - 4, () -> {
            BlockPos p = floor(level, at);
            level.setBlock(p, ModBlocks.REWARD_CACHE.get().defaultBlockState().setValue(RewardCacheBlock.FACING, Direction.NORTH), 3);
            if (level.getBlockEntity(p) instanceof RewardCacheBlockEntity cache) cache.setup(g.rank(), kind, false, level.getGameTime());
        });
        if (s.lieutenants.isEmpty()) {
            level.playSound(null, g.bossRoomCenter(), SoundEvents.IRON_DOOR_OPEN, SoundSource.HOSTILE, 3f, 0.5f);
            level.playSound(null, g.bossRoomCenter(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 3f, 0.6f);
        }
        syncInside(server, level, g, inside);
    }

    /** A raid boss or lieutenant fights whom the raid picks ({@link #control}), not whoever last hit it. */
    @SubscribeEvent
    static void onTarget(LivingChangeTargetEvent event) {
        if (choosing || !CONTROLLED.contains(event.getEntity().getUUID())) return;
        if (!(event.getEntity() instanceof Mob mob)) return;
        LivingEntity current = mob.getTarget();
        LivingEntity next = event.getNewAboutToBeSetTarget();
        if (current != null && current.isAlive() && next != null && next != current) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        dropStaleQuests(p);
        sync(p);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        GateInstance g = raidOf(p.server, p.getUUID());
        if (g != null && (g.raid().phase == RaidState.Phase.MUSTER || g.raid().phase == RaidState.Phase.COUNTDOWN)) {
            ServerScheduler.schedule(1, () -> drop(p.server, g, p.getUUID(), "left.offline"));
        }
        SAID.remove(p.getUUID());
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        RUNS.clear();
        SAID.clear();
        CONTROLLED.clear();
        SHIELDED.clear();
    }

    /** Takes a player off any raid for {@code /solo wipe}: off a muster, or out of a raid underway. */
    public static void wipe(ServerPlayer player) {
        GateInstance g = raidOf(player.server, player.getUUID());
        if (g == null) return;
        RaidState s = g.raid();
        if (s.phase == RaidState.Phase.RUNNING) {
            RaidState.Raider r = s.raider(player.getUUID());
            if (r != null) r.status = RaidState.Status.LEFT;
            dirty(player.server);
        } else {
            drop(player.server, g, player.getUUID(), "left.other");
        }
        sync(player);
    }

    // ------------------------------------------------------------------ commands and tests

    /** Starts a raid at once, skipping the countdown (commands, tests). */
    public static Outcome startNow(ServerPlayer player) {
        GateInstance g = raidOf(player.server, player.getUUID());
        if (g == null) return Outcome.no("not_in");
        RaidState s = state(g);
        if (s.phase != RaidState.Phase.MUSTER && s.phase != RaidState.Phase.COUNTDOWN) return Outcome.no("started");
        if (s.leader == null) s.leader = player.getUUID();
        launch(player.server, g);
        return Outcome.ok("countdown", 0);
    }

    /** Forces a phase on the raid boss of the player's raid: "barrier" or "cataclysm" (commands). */
    public static Outcome forcePhase(ServerPlayer player, String phase) {
        GateInstance g = raidOf(player.server, player.getUUID());
        ServerLevel dungeon = GateService.dungeon(player.server);
        if (g == null || dungeon == null) return Outcome.no("not_in");
        RaidState s = state(g);
        if (s.phase != RaidState.Phase.RUNNING || g.boss() == null || !(dungeon.getEntity(g.boss()) instanceof GateBossEntity boss)) return Outcome.no("no_boss");
        if (!g.bossAwake()) GateService.wakeBossNow(dungeon, g);
        s.lieutenants.clear();
        Run run = run(g.id());
        List<ServerPlayer> inside = inside(dungeon, g);
        long now = dungeon.getGameTime();
        switch (phase) {
            case "barrier" -> {
                s.barrierDone = false;
                s.barrierUp = false;
                boss.setHealth(Math.min(boss.getHealth(), boss.getMaxHealth() * BARRIER_AT));
                raiseBarrier(dungeon, g, s, run, boss, inside, now);
            }
            case "cataclysm" -> {
                s.barrierUp = false;
                s.barrierDone = true;
                SHIELDED.remove(boss.getUUID());
                boss.setHealth(Math.min(boss.getHealth(), boss.getMaxHealth() * CATACLYSM_AT));
                beginCataclysm(dungeon, g, run, boss, inside, hunterBodies(dungeon, s), now);
            }
            default -> {
                return Outcome.no("none");
            }
        }
        dirty(player.server);
        return Outcome.ok("phase", phase);
    }

    /** How much each raider (player id or hunter id) has dealt in a raid underway (tests). */
    public static double dealt(UUID gate, UUID raider) {
        Run run = RUNS.get(gate);
        return run == null ? 0 : run.dealt.getOrDefault(raider, 0.0);
    }

    /** True if the raid picks this boss's targets (tests). */
    public static boolean controls(Entity boss) {
        return CONTROLLED.contains(boss.getUUID());
    }

    /** The cataclysm's lights right now (tests). */
    public static List<Vec3> refuges(UUID gate) {
        Run run = RUNS.get(gate);
        return run == null ? List.of() : List.copyOf(run.refuges);
    }

    /** Lands the cataclysm now (tests). */
    public static void landNow(MinecraftServer server, GateInstance g) {
        ServerLevel dungeon = GateService.dungeon(server);
        Run run = RUNS.get(g.id());
        if (dungeon == null || run == null || g.boss() == null || !(dungeon.getEntity(g.boss()) instanceof GateBossEntity boss)) return;
        landCataclysm(dungeon, g, state(g), run, boss, inside(dungeon, g), hunterBodies(dungeon, state(g)), dungeon.getGameTime());
    }

    /** Runs a raid's upkeep now (tests). */
    public static void tickNow(MinecraftServer server, GateInstance g) {
        ServerLevel dungeon = GateService.dungeon(server);
        if (dungeon != null && g.isRaid() && g.raid().phase == RaidState.Phase.RUNNING) runTick(server, dungeon, g, dungeon.getGameTime());
        else if (g.isRaid()) musterTick(server, g, server.overworld().getGameTime());
    }

    /** Forgets everything a raid keeps while it runs (tests). */
    public static void clearRuntime() {
        RUNS.clear();
        SAID.clear();
    }

    private Raids() {}
}
