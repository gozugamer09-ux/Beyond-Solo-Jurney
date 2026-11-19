package com.beyondsolo.home;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.association.Association;
import com.beyondsolo.association.Broker;
import com.beyondsolo.awakening.AwakeningData;
import com.beyondsolo.awakening.AwakeningService;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.evaluation.EvaluationManager;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.network.HomeActionPayload;
import com.beyondsolo.network.HomeEventPayload;
import com.beyondsolo.network.HomeSyncPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.party.Parties;
import com.beyondsolo.party.Party;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.story.Kin;
import com.beyondsolo.story.KinRecord;
import com.beyondsolo.story.StoryConfig;
import com.beyondsolo.story.StoryScenes;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import com.beyondsolo.util.OnlinePlayers;
import com.beyondsolo.util.ParticleGuard;
import com.beyondsolo.worldboss.WorldBossKind;
import com.beyondsolo.worldboss.WorldBossModule;
import com.beyondsolo.worldboss.WorldBossRecord;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The hunter's residence: a home of their own on the top floor of a tower, over a city at night ({@link HomeWorld}),
 * that they reach from wherever they stand.
 *
 * <ul>
 * <li><b>Owning it.</b> The home comes in four parts ({@link HomeTier}), each bought with gold once the hunter's level
 * and rank are high enough ({@link HomeConfig}): the Studio, then the Family Home's wing, the Training Hall's, and the
 * Penthouse over them. A part is built the moment it is bought ({@link HomeBuilder}), the city round the tower the first
 * time.</li>
 * <li><b>Going home.</b> From the HOME page or {@code /home}: the hunter stands still for a moment (moving or being hurt
 * breaks it) and is home, in the foyer. Nobody goes home out of a gate, a trial, the Citadel, the Penalty Zone or the
 * island, and after arriving they wait {@code home.cooldownSeconds} before they can go again. The lift's button (or
 * leaving from the page) takes them back to the very spot they came from. A party member may visit; a guest touches
 * nothing (no chests, no beds, no building), and leaves the same way.</li>
 * <li><b>The rooms.</b> Everything inside the rooms they own is the hunter's to arrange (the walls, floors, the lift and
 * what the home is built round stay as they are). A night in their own bed brings back health, mana and fatigue, and
 * once a day leaves them Well Rested. The Family Home's study has the Association's desk (its office, for the gates
 * round where they left) and the gate broker's bell; their mother, once she is awake, moves into her room there. The
 * Training Hall's dummies take every blow and skill and show the damage a second ({@link TrainingDummy}); the
 * Penthouse's trophy hall has a pedestal for every great threat, with a likeness of the ones they have slain.</li>
 * </ul>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class Homes {
    static final String KEY = "home.beyondsolo.";
    /** How far (blocks) a hunter may drift while they gather before it breaks. */
    static final double DRIFT = 0.75;
    /** This far under a home's floor (blocks), someone has fallen out of it and is put back in. */
    static final int FALL = 12;
    /** How often (ticks) hunters in a home are looked over: guests still welcome, the dummies and pedestals in place. */
    static final int CHECK = 40;

    /** The answer to a button or command: done, or refused and why. */
    public record Outcome(boolean ok, Component text) {
        static Outcome yes(String key, Object... args) {
            return new Outcome(true, Component.translatable(KEY + "outcome." + key, args));
        }

        static Outcome no(String key, Object... args) {
            return new Outcome(false, Component.translatable(KEY + "outcome." + key, args));
        }
    }

    /** A hunter standing still to be taken home: whose home (null: their own), where they stand, since when. */
    private static final class Channel {
        @Nullable final UUID host;
        final ResourceKey<Level> dim;
        final Vec3 at;
        final long started;
        float health;

        Channel(@Nullable UUID host, ResourceKey<Level> dim, Vec3 at, long started, float health) {
            this.host = host;
            this.dim = dim;
            this.at = at;
            this.started = started;
            this.health = health;
        }
    }

    private static final Map<UUID, Channel> CHANNELS = new HashMap<>();
    /** The homes being built: a floor's new parts, or a city. */
    private static final List<HomeBuilder> BUILDS = new ArrayList<>();

    public static HomeRecord record(Player p) {
        return p.getData(HomeModule.RECORD);
    }

    static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    // ================================================================ owning a home

    /** The part a hunter would buy next, or null if they own it all. */
    @Nullable
    public static HomeTier next(Player p) {
        return record(p).tier().next();
    }

    /** Buys the next part of the hunter's home. */
    public static Outcome buy(ServerPlayer p) {
        if (!HomeConfig.enabled()) return Outcome.no("off");
        if (!SystemAPI.isAwakened(p)) return Outcome.no("unawakened");
        if (HomeWorld.level(p.server) == null) return Outcome.no("no_world");
        HomeTier next = next(p);
        if (next == null) return Outcome.no("top");
        SystemData sd = SystemAPI.get(p);
        int level = HomeConfig.level(next);
        PlayerRank rank = HomeConfig.rank(next);
        long gold = HomeConfig.gold(next);
        if (sd.level() < level) return Outcome.no("level", next.displayName(), level);
        if (sd.rank().tier() < rank.tier()) return Outcome.no("rank", next.displayName(), rank.displayName());
        if (!Economy.spend(p, gold, Economy.Reason.HOME)) return Outcome.no("gold", next.displayName(), gold);
        own(p, next);
        return Outcome.yes(next == HomeTier.STUDIO ? "bought" : "upgraded", next.displayName());
    }

    /**
     * The hunter's home is {@code tier} now (bought, or given by a command): what is new of it is built, they hear what
     * the new rooms are for, and its titles are theirs. {@link HomeTier#NONE} takes it away ({@link #takeAway}).
     */
    public static void own(ServerPlayer p, HomeTier tier) {
        if (tier == HomeTier.NONE) {
            takeAway(p);
            return;
        }
        HomeRecord rec = record(p);
        HomeTier was = rec.tier();
        rec.tier = tier.ordinal();
        ServerLevel level = HomeWorld.level(p.server);
        if (level != null) build(level, HomeData.get(p.server).claim(p.getUUID()), tier);
        HomeJournal.owned(p, tier);
        if (!was.atLeast(tier)) {
            Notifications.send(p, new SystemNotification(SystemNotification.Kind.REWARD,
                    Component.translatable(KEY + "notify.owned." + tier.id()),
                    Component.translatable(KEY + "notify.owned." + tier.id() + ".body"), SystemNotification.DEFAULT_DURATION + 60));
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.2f);
        }
        // already home: the new rooms are open now
        if (level != null && inOwnHome(p)) arrived(p, level, HomeWorld.plan(level, HomeData.get(p.server).plot(p.getUUID())), p.getUUID(), false);
        sync(p);
    }

    /**
     * The hunter's home is gone (operator commands, a wipe): they are put back out in the world if they are in it, their
     * mother goes back to the village, and the floor is cleared to be built again from nothing if they ever buy it again
     * (the plot stays theirs).
     */
    public static void takeAway(ServerPlayer p) {
        HomeRecord rec = record(p);
        if (inAnyHome(p)) goBack(p, false);
        if (rec.motherHere) Kin.moveOut(p);
        int plot = HomeData.get(p.server).plot(p.getUUID());
        if (plot >= 0) HomeData.get(p.server).setFloorBuilt(plot, HomeTier.NONE);
        BUILDS.removeIf(b -> b.layout().plot() == plot && !b.city());
        cancel(p, null);
        rec.reset();
        sync(p);
    }

    // ================================================================ building

    /** Lays what a plot's owner has that is not built yet: the floor's new parts, and the city round it once. */
    static void build(ServerLevel level, int plot, HomeTier tier) {
        HomeData data = HomeData.get(level.getServer());
        HomeWorld.Layout l = HomeWorld.plan(level, plot);
        HomeTier laid = data.floorBuilt(plot);
        for (HomeBuilder b : BUILDS) if (b.layout().plot() == plot && !b.city() && b.to().atLeast(laid)) laid = b.to();
        if (!laid.atLeast(tier)) BUILDS.add(new HomeBuilder(l, laid, tier, false));
        if (!l.small() && data.cityBuilt(plot) < HomeBuilder.VERSION && BUILDS.stream().noneMatch(b -> b.layout().plot() == plot && b.city())) {
            BUILDS.add(new HomeBuilder(l, HomeTier.NONE, HomeTier.NONE, true));
        }
    }

    /** Builds the floor of a plot now, whatever ground is missing (someone is coming in). The city keeps going up slowly. */
    static void finishFloor(ServerLevel level, int plot) {
        for (HomeBuilder b : List.copyOf(BUILDS)) {
            if (b.layout().plot() != plot || b.city()) continue;
            b.finish(level);
            finished(level, b);
            BUILDS.remove(b);
        }
    }

    /** Rebuilds a plot's floor from nothing, as its owner has it (operator commands). */
    public static void rebuild(ServerLevel level, int plot, HomeTier tier, boolean city) {
        BUILDS.removeIf(b -> b.layout().plot() == plot);
        HomeData data = HomeData.get(level.getServer());
        data.setFloorBuilt(plot, HomeTier.NONE);
        if (city) data.setCityBuilt(plot, 0);
        build(level, plot, tier);
    }

    /** How far the build of a plot is (0..1), or -1 if nothing is being built there. */
    public static float building(int plot) {
        float least = -1;
        for (HomeBuilder b : BUILDS) {
            if (b.layout().plot() != plot) continue;
            float f = b.progress();
            least = least < 0 ? f : Math.min(least, f);
        }
        return least;
    }

    /** True while anything is being built. */
    public static boolean isBuilding() {
        return !BUILDS.isEmpty();
    }

    /** Builds everything that is going up, now (tests, commands). */
    public static void finishAll(ServerLevel level) {
        Set<Integer> plots = new HashSet<>();
        for (HomeBuilder b : List.copyOf(BUILDS)) {
            b.finish(level);
            finished(level, b);
            plots.add(b.layout().plot());
        }
        BUILDS.clear();
        HomeData data = HomeData.get(level.getServer());
        for (int plot : plots) {
            ServerPlayer owner = OnlinePlayers.get(level.getServer(), data.owner(plot));
            if (owner != null) sync(owner);
        }
    }

    private static void finished(ServerLevel level, HomeBuilder b) {
        HomeData data = HomeData.get(level.getServer());
        int plot = b.layout().plot();
        if (b.city()) {
            data.setCityBuilt(plot, HomeBuilder.VERSION);
            return;
        }
        if (!data.floorBuilt(plot).atLeast(b.to())) data.setFloorBuilt(plot, b.to());
        // whoever is in it sees the new rooms filled
        UUID owner = data.owner(plot);
        ServerPlayer p = OnlinePlayers.get(level.getServer(), owner);
        if (p != null && inOwnHome(p)) arrived(p, level, b.layout(), owner, false);
    }

    private static void tickBuilds(MinecraftServer server) {
        if (BUILDS.isEmpty()) return;
        ServerLevel level = HomeWorld.level(server);
        if (level == null) {
            BUILDS.clear();
            return;
        }
        // the floors first (someone may be waiting to come in), one city at a time
        boolean city = false;
        Set<Integer> done = new HashSet<>();
        for (HomeBuilder b : List.copyOf(BUILDS)) {
            if (b.city()) {
                if (city) continue;
                city = true;
            }
            if (b.tick(level)) {
                finished(level, b);
                BUILDS.remove(b);
                done.add(b.layout().plot());
            }
        }
        // the owners see how far their builders are (the HOME page): every two seconds, and the moment a part is done
        if (server.getTickCount() % CHECK == 0) for (HomeBuilder b : BUILDS) done.add(b.layout().plot());
        HomeData data = HomeData.get(server);
        for (int plot : done) {
            ServerPlayer owner = OnlinePlayers.get(server, data.owner(plot));
            if (owner != null) sync(owner);
        }
    }

    // ================================================================ going home

    /** Starts gathering a hunter to go to their own home. */
    public static Outcome goHome(ServerPlayer p) {
        return start(p, null);
    }

    /** Starts gathering a hunter to go to a party member's home. */
    public static Outcome visit(ServerPlayer p, UUID host) {
        return start(p, host.equals(p.getUUID()) ? null : host);
    }

    private static Outcome start(ServerPlayer p, @Nullable UUID host) {
        Outcome no = refusal(p, host, true);
        if (no != null) return no;
        Component where = host == null ? Component.translatable(KEY + "where.own") : hostName(p.server, host);
        int ticks = HomeConfig.channelTicks();
        if (ticks <= 0) {
            travel(p, host);
            return Outcome.yes(host == null ? "home" : "visiting", where);
        }
        CHANNELS.put(p.getUUID(), new Channel(host, p.level().dimension(), p.position(), now(p.server), p.getHealth()));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7f, 1.4f);
        PacketDistributor.sendToPlayer(p, new HomeEventPayload(HomeEventPayload.CHANNEL_START, ticks, where));
        return Outcome.yes(host == null ? "going" : "going_visit", where);
    }

    /**
     * Why a hunter can't go to a home now (their own if {@code host} is null), or null if they can. {@code fresh}:
     * starting (the cooldown counts); otherwise finishing one already gathered.
     */
    @Nullable
    static Outcome refusal(ServerPlayer p, @Nullable UUID host, boolean fresh) {
        MinecraftServer server = p.server;
        if (!HomeConfig.enabled()) return Outcome.no("off");
        if (!SystemAPI.isAwakened(p)) return Outcome.no("unawakened");
        if (HomeWorld.level(server) == null) return Outcome.no("no_world");
        HomeData data = HomeData.get(server);
        int plot;
        if (host == null) {
            if (!record(p).owns()) return Outcome.no("none");
            plot = data.plot(p.getUUID());
        } else {
            if (!HomeConfig.visits()) return Outcome.no("visits_off");
            if (!Parties.together(server, p.getUUID(), host)) return Outcome.no("not_party");
            plot = data.plot(host);
            if (plot < 0 || !hostTier(server, host).atLeast(HomeTier.STUDIO)) return Outcome.no("host_none", hostName(server, host));
        }
        if (fresh && CHANNELS.containsKey(p.getUUID())) return Outcome.no("already");
        long wait = record(p).readyAt - now(server);
        if (fresh && wait > 0) return Outcome.no("wait", clock(wait));
        if (!p.isAlive() || p.isSpectator() || busy(p)) return Outcome.no("busy");
        if (plot >= 0 && HomeWorld.plotAt(p.level(), p.blockPosition()) == plot) return Outcome.no("already_there");
        return null;
    }

    /** Somewhere nobody is taken home from: a gate, a trial, the awakening, the Citadel, the Penalty Zone, the island. */
    static boolean busy(ServerPlayer p) {
        if (GateService.isDungeon(p.level()) && !HomeWorld.testing(p.server)) return true;
        if (GateService.inTransit(p) || AwakeningService.inSequence(p) || EvaluationManager.isBeingEvaluated(p)) return true;
        AwakeningData.Stage stage = AwakeningService.stage(p);
        if (stage == AwakeningData.Stage.CALLED || stage == AwakeningData.Stage.TRIAL || stage == AwakeningData.Stage.AWAKENING) return true;
        if (com.beyondsolo.job.JobTrial.inTrial(p) || com.beyondsolo.monarch.ThroneTrial.inTrial(p)) return true;
        if (com.beyondsolo.penalty.PenaltyZone.inZone(p) || com.beyondsolo.penalty.PenaltyZone.busy(p)) return true;
        if (com.beyondsolo.tower.TowerAPI.inCitadel(p) || com.beyondsolo.redgate.RedGates.occupied(p)) return true;
        if (com.beyondsolo.isle.IsleRaids.occupied(p) || StoryScenes.playing(p)) return true;
        return com.beyondsolo.party.Regroup.channeling(p);
    }

    /** True while a hunter is gathering to go home. */
    public static boolean channeling(Entity p) {
        return CHANNELS.containsKey(p.getUUID());
    }

    /** Stops a hunter gathering ({@code why}: and says why on their screen). */
    public static void cancel(ServerPlayer p, @Nullable Component why) {
        if (CHANNELS.remove(p.getUUID()) == null || why == null || p.connection == null) return;
        PacketDistributor.sendToPlayer(p, new HomeEventPayload(HomeEventPayload.CHANNEL_CANCEL, 0, why));
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6f, 1.5f);
    }

    private static void tickChannels(MinecraftServer server) {
        if (CHANNELS.isEmpty()) return;
        long now = now(server);
        for (Map.Entry<UUID, Channel> e : List.copyOf(CHANNELS.entrySet())) {
            ServerPlayer p = OnlinePlayers.get(server, e.getKey());
            Channel ch = e.getValue();
            if (p == null) {
                CHANNELS.remove(e.getKey());
                continue;
            }
            if (!p.level().dimension().equals(ch.dim) || horizontal(p.position(), ch.at) > DRIFT) {
                cancel(p, Component.translatable(KEY + "channel.moved"));
                continue;
            }
            if (p.getHealth() < ch.health - 0.01f) {
                cancel(p, Component.translatable(KEY + "channel.hurt"));
                continue;
            }
            ch.health = Math.max(ch.health, p.getHealth());
            Outcome no = refusal(p, ch.host, false);
            if (no != null) {
                cancel(p, no.text());
                continue;
            }
            long t = now - ch.started;
            int total = HomeConfig.channelTicks();
            if (t % 4 == 0) {
                // the city's lights gather round them: pale motes rising in a slow spiral
                ServerLevel level = p.serverLevel();
                double a = t * 0.45;
                double up = Math.min(1.0, t / (double) Math.max(1, total));
                ParticleGuard.send(level, ParticleTypes.END_ROD, p.getX() + Math.cos(a) * 0.8, p.getY() + 0.1 + up * 1.8,
                        p.getZ() + Math.sin(a) * 0.8, 1, 0.02, 0.05, 0.02, 0.005);
                ParticleGuard.send(level, ParticleTypes.WAX_OFF, p.getX() - Math.cos(a) * 0.7, p.getY() + 0.2 + up * 1.4,
                        p.getZ() - Math.sin(a) * 0.7, 1, 0.02, 0.05, 0.02, 0.0);
                if (t % 20 == 0) level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 0.9f + t / (float) Math.max(1, total));
            }
            if (t >= total) {
                CHANNELS.remove(e.getKey());
                travel(p, ch.host);
            }
        }
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Takes a hunter into a home now (the end of the gathering; tests and commands use it directly): their own if
     * {@code host} is null, else that party member's. What they own that isn't built yet is built first. False if there
     * is no such home (or no world for homes).
     */
    public static boolean travel(ServerPlayer p, @Nullable UUID host) {
        MinecraftServer server = p.server;
        ServerLevel level = HomeWorld.level(server);
        if (level == null) return false;
        UUID owner = host == null ? p.getUUID() : host;
        HomeData data = HomeData.get(server);
        int plot = data.plot(owner);
        HomeTier tier = host == null ? record(p).tier() : hostTier(server, host);
        if (plot < 0 || tier == HomeTier.NONE) return false;
        build(level, plot, tier);
        finishFloor(level, plot);
        HomeWorld.Layout l = HomeWorld.plan(level, plot);
        HomeRecord rec = record(p);
        // where they go back to: where they stand now, unless they come from another home
        if (!HomeWorld.isResidence(p.level()) && HomeWorld.plotAt(p.level(), p.blockPosition()) < 0) {
            rec.setAway(p.level().dimension(), p.position(), p.getYRot());
        }
        rec.visiting = owner.equals(p.getUUID()) ? null : owner;
        rec.readyAt = now(server) + HomeConfig.cooldownTicks();
        CHANNELS.remove(p.getUUID());
        ServerLevel from = p.serverLevel();
        Vec3 was = p.position();
        place(p, level, l.arrival(), l.arrivalYaw());
        ParticleGuard.send(from, ParticleTypes.REVERSE_PORTAL, was.x, was.y + 1, was.z, 24, 0.3, 0.6, 0.3, 0.02);
        from.playSound(null, BlockPos.containing(was), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5f, 1.4f);
        Vec3 to = l.arrival();
        level.playSound(null, BlockPos.containing(to), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 1.3f);
        ParticleGuard.send(level, ParticleTypes.END_ROD, to.x, to.y + 1, to.z, 16, 0.3, 0.6, 0.3, 0.02);
        arrived(p, level, l, owner, true);
        return true;
    }

    /**
     * A hunter is in a home (just came in, or its rooms just changed round them): what is in it is put right, and if it
     * is their own, the trophies are theirs, their mother moves in once there's a room for her, and the new rooms are
     * explained. {@code came}: they walked in just now (the HUD shows it).
     */
    static void arrived(ServerPlayer p, ServerLevel level, HomeWorld.Layout l, UUID owner, boolean came) {
        boolean own = owner.equals(p.getUUID());
        HomeTier tier = own ? record(p).tier() : hostTier(p.server, owner);
        ensureFixtures(level, l, tier);
        if (own) {
            refreshTrophies(level, l, p);
            motherMovesIn(p, level, l);
            welcome(p, tier);
        }
        if (came) {
            PacketDistributor.sendToPlayer(p, new HomeEventPayload(HomeEventPayload.ARRIVED, tier.ordinal(),
                    own ? Component.empty() : hostName(p.server, owner)));
        }
        sync(p);
    }

    /** The first time a hunter walks into a part of their home, the System says what its rooms are for. */
    private static void welcome(ServerPlayer p, HomeTier tier) {
        HomeRecord rec = record(p);
        for (HomeTier t : HomeTier.values()) {
            if (t == HomeTier.NONE || !tier.atLeast(t) || (rec.welcomed & (1 << t.ordinal())) != 0) continue;
            rec.welcomed |= 1 << t.ordinal();
            Notifications.send(p, new SystemNotification(SystemNotification.Kind.INFO,
                    Component.translatable(KEY + "notify.rooms." + t.id()),
                    Component.translatable(KEY + "notify.rooms." + t.id() + ".body"), SystemNotification.DEFAULT_DURATION + 100));
        }
    }

    /** Leaves the home the hunter is in, back to where they came from. */
    public static Outcome leave(ServerPlayer p) {
        if (!inAnyHome(p)) return Outcome.no("not_home");
        goBack(p, true);
        return Outcome.yes("left");
    }

    /**
     * Back out into the world: to the spot they stood on before they came in (or the nearest free spot above it), else
     * their spawn. {@code fx}: the lift's chime and light.
     */
    static void goBack(ServerPlayer p, boolean fx) {
        HomeRecord rec = record(p);
        ServerLevel to = rec.awayDim == null ? null : p.server.getLevel(rec.awayDim);
        Vec3 at = rec.away;
        float yaw = rec.awayYaw;
        rec.clearAway();
        CHANNELS.remove(p.getUUID());
        ServerLevel from = p.serverLevel();
        Vec3 was = p.position();
        if (to != null && at != null && HomeWorld.plotAt(to, BlockPos.containing(at)) < 0 && !HomeWorld.isResidence(to)) {
            place(p, to, freeSpot(to, at), yaw);
        } else {
            toSpawn(p);
        }
        if (fx) {
            from.playSound(null, BlockPos.containing(was), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
            ServerLevel now = p.serverLevel();
            ParticleGuard.send(now, ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 16, 0.3, 0.6, 0.3, 0.02);
            now.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8f, 1.0f);
        }
        if (p.connection != null) PacketDistributor.sendToPlayer(p, new HomeEventPayload(HomeEventPayload.LEFT, 0, Component.empty()));
        sync(p);
    }

    /** The spot itself if a hunter fits there, else the nearest free one just above it, else the ground there. */
    static Vec3 freeSpot(ServerLevel level, Vec3 at) {
        BlockPos base = BlockPos.containing(at);
        level.getChunkAt(base);
        AABB box = Player.STANDING_DIMENSIONS.makeBoundingBox(at);
        if (level.noCollision(box) && !level.containsAnyLiquid(box)) return at;
        for (int dy = 1; dy <= 6; dy++) {
            Vec3 up = new Vec3(at.x, Math.floor(at.y) + dy, at.z);
            AABB b = Player.STANDING_DIMENSIONS.makeBoundingBox(up);
            if (level.noCollision(b) && !level.containsAnyLiquid(b)) return up;
        }
        return Vec3.atBottomCenterOf(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base));
    }

    /** Out at the hunter's own spawn in the overworld (nowhere else to go back to). */
    static void toSpawn(ServerPlayer p) {
        ServerLevel world = p.server.overworld();
        BlockPos spawn = p.getRespawnPosition() != null && Level.OVERWORLD.equals(p.getRespawnDimension()) ? p.getRespawnPosition() : world.getSharedSpawnPos();
        if (HomeWorld.plotAt(world, spawn) >= 0) spawn = world.getSharedSpawnPos();
        world.getChunkAt(spawn);
        place(p, world, freeSpot(world, Vec3.atBottomCenterOf(spawn)), p.getYRot());
    }

    /** Takes a hunter somewhere at once (fake players in tests ignore a teleport within their level). */
    static void place(ServerPlayer p, ServerLevel level, Vec3 to, float yaw) {
        if (p.isSleeping()) p.stopSleepInBed(true, true);
        p.stopRiding();
        p.teleportTo(level, to.x, to.y, to.z, yaw, 0f);
        if (p.level() == level && p.distanceToSqr(to) > 4) p.moveTo(to.x, to.y, to.z, yaw, 0f);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
    }

    // ================================================================ who is where

    /** The plot a hunter stands in, or -1. */
    public static int plotOf(Player p) {
        return HomeWorld.plotAt(p.level(), p.blockPosition());
    }

    /** True while a hunter is in a home: their own or a party member's. */
    public static boolean inAnyHome(Player p) {
        return plotOf(p) >= 0 || HomeWorld.isResidence(p.level());
    }

    /** Whose home a hunter is in, for their party: nothing in their own, else its owner's name. */
    public static Component hostOf(ServerPlayer p) {
        int plot = plotOf(p);
        UUID owner = plot < 0 ? null : HomeData.get(p.server).owner(plot);
        if (owner == null || owner.equals(p.getUUID())) return Component.empty();
        return hostName(p.server, owner);
    }

    /** True while a hunter is in their own home. */
    public static boolean inOwnHome(ServerPlayer p) {
        int plot = plotOf(p);
        return plot >= 0 && plot == HomeData.get(p.server).plot(p.getUUID()) && record(p).owns();
    }

    /**
     * Where a hunter is in the world: where they stand, or, while they are in a home, the spot they go back to (the
     * Association's desk reckons distances from it).
     */
    public static Vec3 worldPosition(ServerPlayer p) {
        HomeRecord rec = record(p);
        if (inAnyHome(p) && rec.away != null) return rec.away;
        return p.position();
    }

    /** The tier of a party member's home (online: their record; away: what is built of it). */
    static HomeTier hostTier(MinecraftServer server, UUID host) {
        ServerPlayer h = OnlinePlayers.get(server, host);
        if (h != null) return record(h).tier();
        int plot = HomeData.get(server).plot(host);
        return plot < 0 ? HomeTier.NONE : HomeData.get(server).floorBuilt(plot);
    }

    static Component hostName(MinecraftServer server, UUID host) {
        ServerPlayer h = OnlinePlayers.get(server, host);
        if (h != null) return Component.literal(h.getGameProfile().getName());
        Party party = Parties.of(server, host);
        if (party != null) return Component.literal(party.name(host));
        return Component.literal(server.getProfileCache() == null ? "?" : server.getProfileCache().get(host).map(g -> g.getName()).orElse("?"));
    }

    /** "4:05" */
    static Component clock(long ticks) {
        long s = (ticks + 19) / 20;
        return Component.literal(s / 60 + ":" + String.format(Locale.ROOT, "%02d", s % 60));
    }

    // ================================================================ what stands in a home

    /** Puts right what a home is built round: the lift's button, the hall's dummies and weapons, the trophy pedestals. */
    static void ensureFixtures(ServerLevel level, HomeWorld.Layout l, HomeTier tier) {
        if (!tier.atLeast(HomeTier.STUDIO)) return;
        BlockPos button = l.button();
        if (level.isLoaded(button) && level.getBlockState(button).isAir()) {
            level.setBlock(button, Blocks.POLISHED_BLACKSTONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.WALL)
                    .setValue(ButtonBlock.FACING, Direction.NORTH), 3);
        }
        if (tier.atLeast(HomeTier.HALL)) {
            ensureDummies(level, l);
            ItemStack[] rack = {new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_AXE), new ItemStack(Items.BOW),
                    new ItemStack(Items.TRIDENT), new ItemStack(Items.SHIELD)};
            for (int i = 0; i < rack.length; i++) {
                BlockPos at = l.at(12 + i * 3, 3, 9);
                if (!entitiesLoaded(level, at) || !level.getEntitiesOfClass(ItemFrame.class, new AABB(at)).isEmpty()) continue;
                if (!level.getBlockState(at.south()).isFaceSturdy(level, at.south(), Direction.NORTH)) continue;
                ItemFrame f = new ItemFrame(level, at, Direction.NORTH);
                f.setItem(rack[i], false);
                level.addFreshEntity(f);
            }
        }
        if (tier.atLeast(HomeTier.PENTHOUSE)) {
            for (int i = 0; i < HomeWorld.TROPHIES.length; i++) {
                BlockPos p = l.pedestal(i);
                if (!level.isLoaded(p)) continue;
                if (!level.getBlockState(p).is(HomeModule.PEDESTAL.get())) {
                    level.setBlock(p, HomeModule.PEDESTAL.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, l.pedestalFacing(i)), 3);
                }
                if (level.getBlockEntity(p) instanceof TrophyPedestalBlockEntity be) be.setKind(HomeWorld.TROPHIES[i]);
            }
        }
    }

    private static boolean entitiesLoaded(ServerLevel level, BlockPos at) {
        return level.isLoaded(at) && level.areEntitiesLoaded(ChunkPos.asLong(at));
    }

    /** The hall's dummies: one on each stand (a new one where one is missing, put back on its stand if it was moved). */
    public static void ensureDummies(ServerLevel level, HomeWorld.Layout l) {
        for (int i = 0; i < HomeWorld.DUMMIES; i++) {
            Vec3 at = l.dummy(i);
            BlockPos pos = BlockPos.containing(at);
            if (!entitiesLoaded(level, pos)) continue;
            final int slot = i;
            List<TrainingDummy> there = level.getEntitiesOfClass(TrainingDummy.class, new AABB(pos).inflate(4),
                    d -> d.isAlive() && d.slot() == slot && d.plot() == l.plot());
            if (!there.isEmpty()) {
                TrainingDummy d = there.get(0);
                if (d.distanceToSqr(at) > 0.04) d.moveTo(at.x, at.y, at.z, l.dummyYaw(), 0);
                continue;
            }
            TrainingDummy d = HomeModule.DUMMY.get().create(level);
            if (d == null) continue;
            d.moveTo(at.x, at.y, at.z, l.dummyYaw(), 0);
            d.setYHeadRot(l.dummyYaw());
            d.setYBodyRot(l.dummyYaw());
            d.configure(l.plot(), i, i == HomeWorld.ARMOURED_DUMMY);
            level.addFreshEntity(d);
        }
    }

    /** The trophy hall shows the hunter's own kills: each pedestal how many times its great threat fell to them. */
    public static void refreshTrophies(ServerLevel level, HomeWorld.Layout l, ServerPlayer owner) {
        if (!record(owner).tier().atLeast(HomeTier.PENTHOUSE)) return;
        WorldBossRecord kills = owner.getData(WorldBossModule.RECORD.get());
        for (int i = 0; i < HomeWorld.TROPHIES.length; i++) {
            BlockPos p = l.pedestal(i);
            if (!level.isLoaded(p) || !(level.getBlockEntity(p) instanceof TrophyPedestalBlockEntity be)) continue;
            be.setKind(HomeWorld.TROPHIES[i]);
            be.setSlain(kills.slain(HomeWorld.TROPHIES[i]));
        }
    }

    /** Their mother, once she is awake, moves into the room made for her (the Family Home's). */
    static void motherMovesIn(ServerPlayer p, ServerLevel level, HomeWorld.Layout l) {
        HomeRecord rec = record(p);
        if (!rec.tier().atLeast(HomeTier.FAMILY) || !StoryConfig.enabled()) return;
        KinRecord kin = Kin.record(p);
        if (!kin.awake()) return;
        BlockPos bed = l.motherBed();
        if (level.dimension().equals(kin.bedDimension()) && bed.equals(kin.bed()) && rec.motherHere) return;
        if (!level.isLoaded(bed) || Kin.moveIn(p, level, bed) == null) return;
        boolean first = !rec.motherHere;
        rec.motherHere = true;
        if (first) {
            Notifications.send(p, new SystemNotification(SystemNotification.Kind.INFO,
                    Component.translatable(KEY + "notify.mother"), Component.translatable(KEY + "notify.mother.body"),
                    SystemNotification.DEFAULT_DURATION + 60));
        }
    }

    /** A bout at a dummy is over: a hunter's best damage a second over ten seconds, if they beat it. */
    static void bout(ServerPlayer p, TrainingDummy d, float dps) {
        HomeRecord rec = record(p);
        String n = String.format(Locale.ROOT, "%,.0f", dps);
        if (dps <= rec.bestDps) {
            p.displayClientMessage(Component.translatable(KEY + "dummy.bout", n, String.format(Locale.ROOT, "%,.0f", rec.bestDps))
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        rec.bestDps = dps;
        p.displayClientMessage(Component.translatable(KEY + "dummy.best", n).withStyle(ChatFormatting.GOLD), true);
        p.serverLevel().playSound(null, d.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.6f);
        sync(p);
    }

    // ================================================================ rest

    /** A night in their own bed: health, mana and fatigue back, and once a day, Well Rested. Then they wake. */
    static void rest(ServerPlayer p) {
        p.setHealth(p.getMaxHealth());
        SystemAPI.restoreMana(p, 99999);
        if (!SystemAPI.get(p).isCollapsed()) SystemAPI.addExhaustion(p, -100);
        HomeRecord rec = record(p);
        long day = QuestEngine.day(p);
        p.stopSleepInBed(false, true);
        if (rec.restDay != day) {
            rec.restDay = day;
            p.addEffect(new MobEffectInstance(HomeModule.WELL_RESTED, HomeConfig.restTicks(), 0, false, true, true));
            Notifications.send(p, new SystemNotification(SystemNotification.Kind.REWARD,
                    Component.translatable(KEY + "notify.rested"), Component.translatable(KEY + "notify.rested.body", HomeConfig.REST_MINUTES.get()),
                    SystemNotification.DEFAULT_DURATION));
            sync(p);
        } else {
            p.displayClientMessage(Component.translatable(KEY + "rested.again").withStyle(ChatFormatting.AQUA), true);
        }
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7f, 1.4f);
    }

    // ================================================================ the HOME page

    /** A button on the HOME page. */
    public static void handle(ServerPlayer p, HomeActionPayload a) {
        Outcome o = switch (a.action()) {
            case HomeActionPayload.BUY -> buy(p);
            case HomeActionPayload.GO -> goHome(p);
            case HomeActionPayload.LEAVE -> leave(p);
            case HomeActionPayload.VISIT -> a.args().hasUUID("host") ? visit(p, a.args().getUUID("host")) : null;
            case HomeActionPayload.CANCEL -> {
                cancel(p, Component.translatable(KEY + "channel.stopped"));
                yield null;
            }
            default -> null;
        };
        if (o != null) PacketDistributor.sendToPlayer(p, new HomeEventPayload(HomeEventPayload.OUTCOME, o.ok() ? 1 : 0, o.text()));
        sync(p);
    }

    /** What the HOME page shows. */
    public static void sync(ServerPlayer p) {
        if (p.connection == null) return;
        PacketDistributor.sendToPlayer(p, new HomeSyncPayload(syncTag(p)));
    }

    static CompoundTag syncTag(ServerPlayer p) {
        MinecraftServer server = p.server;
        HomeRecord rec = record(p);
        HomeData data = HomeData.get(server);
        CompoundTag t = new CompoundTag();
        t.putBoolean("enabled", HomeConfig.enabled());
        t.putBoolean("world", HomeWorld.level(server) != null);
        t.putBoolean("visits", HomeConfig.visits());
        t.putInt("tier", rec.tier);
        int plot = data.plot(p.getUUID());
        t.putInt("plot", plot);
        int[] levels = new int[HomeTier.values().length], ranks = new int[HomeTier.values().length];
        long[] gold = new long[HomeTier.values().length];
        for (HomeTier tier : HomeTier.values()) {
            levels[tier.ordinal()] = HomeConfig.level(tier);
            ranks[tier.ordinal()] = HomeConfig.rank(tier).ordinal();
            gold[tier.ordinal()] = HomeConfig.gold(tier);
        }
        t.putIntArray("levels", levels);
        t.putIntArray("ranks", ranks);
        t.putLongArray("gold", gold);
        t.putInt("ready", (int) Math.max(0, Math.min(Integer.MAX_VALUE, rec.readyAt - now(server))));
        t.putInt("channel", HomeConfig.channelTicks());
        int at = plotOf(p);
        t.putBoolean("home", at >= 0 && at == plot && rec.owns());
        t.putBoolean("away", inAnyHome(p));
        if (rec.visiting != null && at >= 0 && at == data.plot(rec.visiting)) t.putString("guestOf", hostName(server, rec.visiting).getString());
        KinRecord kin = Kin.record(p);
        t.putBoolean("mother", rec.motherHere);
        t.putBoolean("motherAwake", kin.awake());
        t.putBoolean("story", kin.stage() > 0);
        t.putBoolean("rested", rec.restDay == QuestEngine.day(p));
        t.putFloat("best", rec.bestDps);
        WorldBossRecord kills = p.getData(WorldBossModule.RECORD.get());
        int[] slain = new int[HomeWorld.TROPHIES.length];
        for (int i = 0; i < slain.length; i++) slain[i] = kills.slain(HomeWorld.TROPHIES[i]);
        t.putIntArray("trophies", slain);
        ListTag kindList = new ListTag();
        for (WorldBossKind k : HomeWorld.TROPHIES) kindList.add(net.minecraft.nbt.StringTag.valueOf(k.id));
        t.put("kinds", kindList);
        t.putFloat("building", plot < 0 ? -1 : building(plot));
        ListTag hosts = new ListTag();
        Party party = Parties.of(server, p.getUUID());
        if (party != null) {
            for (UUID m : party.members()) {
                if (m.equals(p.getUUID())) continue;
                CompoundTag c = new CompoundTag();
                c.putUUID("id", m);
                c.putString("name", party.name(m));
                c.putInt("tier", hostTier(server, m).ordinal());
                c.putBoolean("online", OnlinePlayers.get(server, m) != null);
                hosts.add(c);
            }
        }
        t.put("hosts", hosts);
        return t;
    }

    // ================================================================ events: the clock

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        tickBuilds(server);
        tickChannels(server);
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.isAlive()) return;
        if (!inAnyHome(p)) return;
        // a night in their own bed
        if (p.isSleeping() && p.getSleepTimer() >= 100) {
            if (inOwnHome(p)) rest(p);
            else p.stopSleepInBed(false, true);
        }
        if ((p.tickCount + p.getId()) % 10 != 0 || p.isSpectator()) return;
        ServerLevel level = HomeWorld.level(p.server);
        if (level == null) return;
        int plot = plotOf(p);
        HomeRecord rec = record(p);
        HomeData data = HomeData.get(p.server);
        // fallen out of the home (or wandered off into the empty city): back in at the foyer
        if (!p.isCreative()) {
            int home = rec.visiting != null ? data.plot(rec.visiting) : data.plot(p.getUUID());
            boolean lost = plot < 0 || plot != home && (rec.visiting != null || plot != data.plot(p.getUUID()));
            if (!lost && p.getY() < HomeWorld.plan(level, plot).origin().getY() - FALL) lost = true;
            if (lost) {
                if (home >= 0 && (rec.visiting != null ? hostTier(p.server, rec.visiting) : rec.tier()).atLeast(HomeTier.STUDIO)) {
                    HomeWorld.Layout l = HomeWorld.plan(level, home);
                    place(p, level, l.arrival(), l.arrivalYaw());
                    p.displayClientMessage(Component.translatable(KEY + "caught").withStyle(ChatFormatting.GRAY), true);
                } else {
                    goBack(p, false);
                }
                return;
            }
        }
        if ((p.tickCount + p.getId()) % CHECK != 0 || plot < 0) return;
        // a guest stays while they are of the host's party and the host has a home
        if (rec.visiting != null && !p.isCreative()) {
            if (!HomeConfig.visits() || !Parties.together(p.server, p.getUUID(), rec.visiting) || !hostTier(p.server, rec.visiting).atLeast(HomeTier.STUDIO)) {
                p.displayClientMessage(Component.translatable(KEY + "visit_over").withStyle(ChatFormatting.GRAY), true);
                goBack(p, true);
                return;
            }
        }
        UUID owner = data.owner(plot);
        if (owner != null) ensureFixtures(level, HomeWorld.plan(level, plot), owner.equals(p.getUUID()) ? rec.tier() : hostTier(p.server, owner));
    }

    // ================================================================ events: coming and going

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        HomeRecord rec = record(p);
        ServerLevel level = HomeWorld.level(p.server);
        if (inAnyHome(p) && !p.isCreative() && !p.isSpectator()) {
            int plot = plotOf(p);
            HomeData data = HomeData.get(p.server);
            boolean own = plot >= 0 && plot == data.plot(p.getUUID()) && rec.owns();
            boolean guest = plot >= 0 && rec.visiting != null && plot == data.plot(rec.visiting) && HomeConfig.visits()
                    && Parties.together(p.server, p.getUUID(), rec.visiting) && hostTier(p.server, rec.visiting).atLeast(HomeTier.STUDIO);
            if (!HomeConfig.enabled() || !own && !guest) goBack(p, false);
        } else if (!inAnyHome(p) && rec.away != null) {
            rec.clearAway();
        }
        // the build of their home goes on where it stopped
        if (level != null && rec.owns()) {
            int plot = HomeData.get(p.server).claim(p.getUUID());
            build(level, plot, rec.tier());
        }
        sync(p);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CHANNELS.remove(event.getEntity().getUUID());
    }

    /** Back from death somewhere else: there is no home to go back out of. */
    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && !inAnyHome(p)) {
            record(p).clearAway();
            sync(p);
        }
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        CHANNELS.clear();
        BUILDS.clear();
    }

    /** A blow on a training dummy: its bout counts it. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof TrainingDummy d) d.took(event.getSource(), event.getNewDamage());
    }

    // ================================================================ events: the home holds

    /**
     * Whether a player may change the block at {@code pos} in the homes' world: the home's owner, inside the rooms they
     * own, but never what the home is built round ({@link HomeWorld.Layout#fixture}); creative players anything.
     */
    public static boolean mayEdit(Player p, Level level, BlockPos pos) {
        int plot = HomeWorld.plotAt(level, pos);
        if (plot < 0) return !HomeWorld.isResidence(level) || p.isCreative();
        if (p.isCreative()) return true;
        if (!(level instanceof ServerLevel server)) return true;
        UUID owner = HomeData.get(server.getServer()).owner(plot);
        if (owner == null || !owner.equals(p.getUUID())) return false;
        HomeWorld.Layout l = HomeWorld.plan(server, plot);
        if (l.fixture(pos)) return false;
        HomeWorld.Room room = l.roomAt(pos);
        return room != null && record(p).tier().atLeast(room.tier);
    }

    /** True for a spot in the homes' world (in tests, the homes' lane). */
    static boolean protectedSpot(Level level, BlockPos pos) {
        return HomeWorld.isResidence(level) || HomeWorld.plotAt(level, pos) >= 0;
    }

    @SubscribeEvent
    static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && protectedSpot(level, event.getPos()) && !mayEdit(event.getPlayer(), level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof Level level) || !protectedSpot(level, event.getPos())) return;
        if (!(event.getEntity() instanceof Player p) || !mayEdit(p, level, event.getPos())) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onToolUse(BlockEvent.BlockToolModificationEvent event) {
        if (!(event.getLevel() instanceof Level level) || !protectedSpot(level, event.getPos())) return;
        if (event.getPlayer() == null || !mayEdit(event.getPlayer(), level, event.getPos())) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onExplode(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level.isClientSide) return;
        event.getAffectedBlocks().removeIf(pos -> protectedSpot(level, pos));
        event.getAffectedEntities().removeIf(e -> (e instanceof HangingEntity || e instanceof ArmorStand) && protectedSpot(level, e.blockPosition()));
    }

    @SubscribeEvent
    static void onPiston(PistonEvent.Pre event) {
        if (event.getLevel() instanceof Level level && protectedSpot(level, event.getPos())) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onPortal(BlockEvent.PortalSpawnEvent event) {
        if (event.getLevel() instanceof Level level && protectedSpot(level, event.getPos())) event.setCanceled(true);
    }

    /** A home's beds never become a spawn point: dying sends a hunter where it always did. */
    @SubscribeEvent
    static void onSetSpawn(PlayerSetSpawnEvent event) {
        if (event.getNewSpawn() == null || !(event.getEntity() instanceof ServerPlayer p)) return;
        ServerLevel level = p.server.getLevel(event.getSpawnLevel());
        if (level != null && protectedSpot(level, event.getNewSpawn())) event.setCanceled(true);
    }

    /** Only the owner sleeps in a home's beds. */
    @SubscribeEvent
    static void onSleep(CanPlayerSleepEvent event) {
        ServerPlayer p = event.getEntity();
        if (!protectedSpot(event.getLevel(), event.getPos()) || p.isCreative()) return;
        int plot = HomeWorld.plotAt(event.getLevel(), event.getPos());
        if (plot < 0 || plot != HomeData.get(p.server).plot(p.getUUID())) {
            event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
            p.displayClientMessage(Component.translatable(KEY + "not_your_bed").withStyle(ChatFormatting.GRAY), true);
        }
    }

    /**
     * What a hand does in a home: the lift's button takes anyone back out; the owner's desk opens the Association's
     * office and the bell brings the broker; a guest opens nothing (chests, the desk, the bell); no one lights a fire.
     */
    @SubscribeEvent
    static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!protectedSpot(level, pos)) return;
        Player player = event.getEntity();
        ItemStack held = event.getItemStack();
        if (!player.isCreative() && (held.getItem() instanceof FlintAndSteelItem || held.getItem() instanceof FireChargeItem || held.is(Items.LAVA_BUCKET))) {
            deny(event);
            return;
        }
        if (level.isClientSide || !(player instanceof ServerPlayer p)) return;
        int plot = HomeWorld.plotAt(level, pos);
        if (plot < 0) return;
        HomeWorld.Layout l = HomeWorld.plan((ServerLevel) level, plot);
        boolean main = event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND;
        if (pos.equals(l.button())) {
            deny(event);
            if (main) leave(p);
            return;
        }
        boolean own = plot == HomeData.get(p.server).plot(p.getUUID()) && record(p).owns();
        BlockState state = level.getBlockState(pos);
        if (own && pos.equals(l.desk()) && record(p).tier().atLeast(HomeTier.FAMILY)) {
            deny(event);
            if (main) openDesk(p, false);
            return;
        }
        if (own && pos.equals(l.bell()) && record(p).tier().atLeast(HomeTier.FAMILY)) {
            deny(event);
            if (main) openDesk(p, true);
            return;
        }
        // a guest's hands stay off the host's things (the beds answer for themselves: her bed is hers to talk from)
        if (!own && !p.isCreative() && level.getBlockEntity(pos) != null && !(state.getBlock() instanceof BedBlock)) {
            deny(event);
            if (main) p.displayClientMessage(Component.translatable(KEY + "guest_hands").withStyle(ChatFormatting.GRAY), true);
        }
    }

    @SubscribeEvent
    static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (player.isCreative() || !protectedSpot(event.getLevel(), player.blockPosition())) return;
        ItemStack held = event.getItemStack();
        boolean fire = held.getItem() instanceof FlintAndSteelItem || held.getItem() instanceof FireChargeItem || held.is(Items.LAVA_BUCKET);
        boolean bucket = held.getItem() instanceof BucketItem;
        if (!fire && !bucket) return;
        // water is the owner's to pour in their own rooms; fire is nobody's
        if (fire || event.getLevel().isClientSide || !(player instanceof ServerPlayer p) || !inOwnHome(p)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /** Item frames and stands are the owner's: nobody else knocks them down or turns them. */
    @SubscribeEvent
    static void onAttack(AttackEntityEvent event) {
        Entity t = event.getTarget();
        if ((t instanceof HangingEntity || t instanceof ArmorStand) && protectedSpot(t.level(), t.blockPosition())
                && !mayEdit(event.getEntity(), t.level(), t.blockPosition())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        Entity t = event.getTarget();
        if ((t instanceof HangingEntity || t instanceof ArmorStand) && protectedSpot(t.level(), t.blockPosition())
                && !mayEdit(event.getEntity(), t.level(), t.blockPosition())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    static void onInteractEntityAt(PlayerInteractEvent.EntityInteractSpecific event) {
        Entity t = event.getTarget();
        if ((t instanceof HangingEntity || t instanceof ArmorStand) && protectedSpot(t.level(), t.blockPosition())
                && !mayEdit(event.getEntity(), t.level(), t.blockPosition())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    private static void deny(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /**
     * The study's desk: the Association's office ({@code broker}: the gate broker's window), for the gates round where
     * the hunter left the world (or the world's spawn if they came from nowhere).
     */
    static void openDesk(ServerPlayer p, boolean broker) {
        HomeRecord rec = record(p);
        ResourceKey<Level> dim = rec.awayDim != null && rec.away != null ? rec.awayDim : Level.OVERWORLD;
        BlockPos office = rec.awayDim != null && rec.away != null ? BlockPos.containing(rec.away) : p.server.overworld().getSharedSpawnPos();
        p.serverLevel().playSound(null, p.blockPosition(), broker ? SoundEvents.BELL_BLOCK : SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS,
                broker ? 0.6f : 1.0f, broker ? 1.4f : 1.0f);
        if (broker) Broker.openAt(p, dim, office);
        else Association.openAt(p, dim, office, -1);
    }

    private Homes() {}
}
