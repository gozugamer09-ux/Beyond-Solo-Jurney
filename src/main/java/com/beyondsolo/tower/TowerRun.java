package com.beyondsolo.tower;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.enemy.Affix;
import com.beyondsolo.enemy.Enemies;
import com.beyondsolo.enemy.EnemyFx;
import com.beyondsolo.enemy.EnemyRoster;
import com.beyondsolo.enemy.EnemyTraits;
import com.beyondsolo.enemy.Variant;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.DungeonBoss;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.gate.RewardCacheBlock;
import com.beyondsolo.gate.RewardCacheBlockEntity;
import com.beyondsolo.network.CutscenePayload;
import com.beyondsolo.network.GatePayload;
import com.beyondsolo.network.TowerActionPayload;
import com.beyondsolo.network.TowerPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.registry.ModBlocks;
import com.beyondsolo.registry.ModEntities;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A climb of the Crimson Citadel, a run of the Endless Spire, or a boss rush: one hunter at a time, in their own slot
 * of the Citadel's dimension.
 *
 * <p><b>The climb.</b> The System pulls the hunter in (a crimson ring, a pillar, a flash) onto the floor above their
 * last checkpoint. Each floor is a hall ({@link CitadelHalls}) with its title card, then its fight: waves of the
 * floor's rank, a champion and its elites, a siege to hold, or a gatekeeper (a named gate boss) in a round hall. Won,
 * the seal on the stair door lifts and the exit sigil lights; the next hall is already being raised on the other pad,
 * so the stair leads straight up into it. A beaten gatekeeper is a checkpoint. On the hundredth floor the lord waits
 * ({@link CitadelLordEntity}); once he falls the stair climbs on into the Endless Spire, every floor a little stronger,
 * its best floor kept as a record.</p>
 *
 * <p><b>The rush.</b> All six gate bosses of the roster at the tier's rank, one after another in the colosseum, the
 * last one ascended; a breather between them (some health and mana back). The time is kept per tier. The Sovereign
 * rush (opened by the Sigil of Dominion) brings all six ascended at S rank, and then the lord.</p>
 *
 * <p>A blow that would kill the hunter ends the run instead: no death, nothing lost but the floors above the
 * checkpoint; they are thrown home weakened. Logging out ends it too (they wake up at home).</p>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class TowerRun {
    public enum Mode { CASTLE, ENDLESS, RUSH }

    public enum Phase { PULL, ARRIVING, FIGHT, CLEARED, CLIMBING, VICTORY, BREATHER, RUSH_DONE, FALLEN, LEAVING }

    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, BeyondSolo.id("crimson_citadel"));
    public static final String FOE_TAG = "beyondsolo_tower";
    public static final ResourceLocation XP_SOURCE = BeyondSolo.id("tower");
    public static final String CONQUEROR_TITLE = "title.beyondsolo.citadel_conqueror";
    public static final String SOVEREIGN_TITLE = "title.beyondsolo.rush_sovereign";

    static final int PULL_TICKS = 50;
    static final int ARRIVE_TICKS = 60;
    static final int CLIMB_TICKS = 36;
    static final int CLIMB_SWAP = 16;
    static final int VICTORY_TICKS = 170;
    static final int FALLEN_TICKS = 80;
    static final int LEAVE_TICKS = 40;
    public static final int EXIT_HOLD = 40;
    public static final int BREATHER_TICKS = 20 * 12;
    static final int RUSH_DONE_TICKS = 20 * 60;
    static final int SPAWN_WARN = 18;
    static final int BOUT_WAKE = 40;
    static final int KEEPER_WAKE_TICKS = 100;
    static final double KEEPER_WAKE_RANGE = 14;
    static final int FALL_DEBUFF_TICKS = 20 * 60;
    static final float BREATHER_HEAL = 0.3f;
    /** The sigil: one lord kill in four drops it, and the fourth without one always does. */
    public static final float SIGIL_CHANCE = 0.25f;
    public static final int SIGIL_PITY = 4;
    static final int CRIMSON = 0xFF2E3F;
    static final int EMBER = 0xFF7A3D;
    static final int GOLD = 0xFFD166;

    private static final ResourceLocation FREEZE = BeyondSolo.id("tower_freeze");
    private static final ResourceLocation SPIRE_HEALTH = BeyondSolo.id("spire_health");
    private static final ResourceLocation SPIRE_DAMAGE = BeyondSolo.id("spire_damage");

    record Pending(Vec3 at, int due, EntityType<?> type, EnemyTraits traits) {}

    static final class Session {
        final ServerPlayer player;
        final int slot;
        Mode mode;
        @Nullable final RushTier tier;
        ServerLevel level;
        Phase phase = Phase.PULL;
        int tick;
        int floor;
        int pad;
        @Nullable CitadelHalls.Layout hall;
        /** The hall being raised: the first one before the pull lands, then always the next floor's. */
        @Nullable CitadelHalls.Job build;
        int buildPad;
        int wave;
        int waves;
        int siegeLeft;
        EnemyRoster.Mix mix;
        final List<UUID> foes = new ArrayList<>();
        final List<Pending> spawns = new ArrayList<>();
        @Nullable UUID boss;
        boolean bossAwake;
        boolean bossEnraged;
        /** The phase tick the boss fell on, or -1. */
        int bossDown = -1;
        boolean moon;
        /** The floor's reward cache, until it is opened. */
        @Nullable BlockPos cache;
        int cacheHint = -100;
        int hold;
        /** The boss rush: the bouts, which one, and the clock (running from the first boss waking to the last one falling). */
        List<RushTier.Bout> bouts = List.of();
        int bout;
        int clock;
        boolean clockOn;
        int floorsCleared;
        boolean record;
        boolean sigilDropped;
        int event = TowerPayload.EV_NONE;
        boolean ended;

        Session(ServerPlayer player, int slot, Mode mode, @Nullable RushTier tier) {
            this.player = player;
            this.slot = slot;
            this.mode = mode;
            this.tier = tier;
            this.mix = EnemyRoster.random(PlayerRank.E, player.getRandom());
        }

        PlayerRank rank() {
            return mode == Mode.RUSH && tier != null ? tier.rank : TowerFloors.rank(floor);
        }

        TowerFloors.Kind kind() {
            return mode == Mode.RUSH ? TowerFloors.Kind.KEEPER : TowerFloors.kind(floor);
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    // ================================================================ going in

    public static TowerData data(Player player) {
        return player.getData(TowerModule.CITADEL);
    }

    /** The Citadel's level. The game-test server has no datapack dimensions, so there it is the overworld. */
    @Nullable
    public static ServerLevel citadel(MinecraftServer server) {
        ServerLevel level = server.getLevel(DIMENSION);
        if (level == null && server instanceof net.minecraft.gametest.framework.GameTestServer) return server.overworld();
        return level;
    }

    public static boolean inRun(Player player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    @Nullable
    public static Phase phase(Player player) {
        Session s = SESSIONS.get(player.getUUID());
        return s == null ? null : s.phase;
    }

    /** Somewhere the System can't take the player from right now. */
    static boolean occupied(ServerPlayer p) {
        if (!p.isAlive() || p.isSpectator()) return true;
        if (GateService.isDungeon(p.level()) && !(p.server instanceof net.minecraft.gametest.framework.GameTestServer)) return true;
        if (GateService.inTransit(p)) return true;
        if (com.beyondsolo.penalty.PenaltyZone.busy(p)) return true;
        if (com.beyondsolo.job.JobTrial.inTrial(p)) return true;
        if (com.beyondsolo.awakening.AwakeningService.inSequence(p)) return true;
        return com.beyondsolo.evaluation.EvaluationManager.isBeingEvaluated(p);
    }

    /** Why a hunter can't go in right now (a lang key), or null if they can. */
    @Nullable
    public static String refusal(ServerPlayer p, Mode mode, @Nullable RushTier tier) {
        if (!SystemAPI.hasSystem(p) || !SystemAPI.isAwakened(p)) return "tower.beyondsolo.refuse.asleep";
        TowerData d = data(p);
        if (!d.bound) return "tower.beyondsolo.refuse.no_key";
        if (SESSIONS.containsKey(p.getUUID())) return "tower.beyondsolo.refuse.inside";
        if (occupied(p)) return "tower.beyondsolo.refuse.busy";
        if (mode == Mode.ENDLESS && !d.conquered()) return "tower.beyondsolo.refuse.not_conquered";
        if (mode == Mode.RUSH) {
            if (tier == null) return "tower.beyondsolo.refuse.busy";
            if (tier.sovereign() && !d.sigil) return "tower.beyondsolo.refuse.sigil";
            if (!tier.openTo(SystemAPI.get(p).rank(), d.sigil)) return "tower.beyondsolo.refuse.rank";
        }
        if (citadel(p.server) == null) return "tower.beyondsolo.refuse.closed";
        return null;
    }

    /** A button of the CITADEL page. */
    public static void action(ServerPlayer p, int action, int arg) {
        switch (action) {
            case TowerActionPayload.CLIMB, TowerActionPayload.THRONE -> enter(p, Mode.CASTLE, null);
            case TowerActionPayload.ENDLESS -> enter(p, Mode.ENDLESS, null);
            case TowerActionPayload.RUSH -> {
                RushTier[] all = RushTier.values();
                if (arg >= 0 && arg < all.length) enter(p, Mode.RUSH, all[arg]);
            }
            case TowerActionPayload.LEAVE -> {
                Session s = SESSIONS.get(p.getUUID());
                if (s != null && s.phase != Phase.LEAVING && s.phase != Phase.FALLEN && s.phase != Phase.PULL) leave(s);
            }
            default -> {}
        }
    }

    /** Goes in if the hunter may (else tells them why not). */
    public static boolean enter(ServerPlayer p, Mode mode, @Nullable RushTier tier) {
        String why = refusal(p, mode, tier);
        if (why != null) {
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.ALERT,
                    Component.translatable("tower.beyondsolo.refuse.title"), Component.translatable(why)));
            return false;
        }
        TowerData d = data(p);
        int floor = switch (mode) {
            case CASTLE -> d.startFloor();
            case ENDLESS -> TowerFloors.SPIRE;
            case RUSH -> 0;
        };
        return begin(p, mode, floor, tier);
    }

    /** Starts a run without asking (commands and tests): the pull, while the first hall goes up. */
    public static boolean begin(ServerPlayer p, Mode mode, int floor, @Nullable RushTier tier) {
        if (SESSIONS.containsKey(p.getUUID())) return false;
        ServerLevel level = citadel(p.server);
        if (level == null || mode == Mode.RUSH && tier == null) return false;
        Session s = new Session(p, freeSlot(), mode, tier);
        s.level = level;
        s.floor = Math.max(1, floor);
        if (mode == Mode.CASTLE && TowerFloors.endless(s.floor)) s.mode = Mode.ENDLESS;
        SESSIONS.put(p.getUUID(), s);
        if (mode == Mode.RUSH) {
            s.bouts = tier.order(level.random);
            s.floor = 0;
            s.buildPad = CitadelHalls.COLOSSEUM_PAD;
            s.build = CitadelHalls.job(level, CitadelHalls.layout(CitadelHalls.Shape.COLOSSEUM, CitadelHalls.origin(s.slot, s.buildPad)),
                    TowerFloors.Wing.THRONE, false);
        } else {
            if (s.mode == Mode.ENDLESS) data(p).spireRuns++;
            s.buildPad = 0;
            s.build = hallJob(s, s.floor, 0);
        }
        setPhase(s, Phase.PULL);
        freeze(p, true);
        sound(p.serverLevel(), p.position(), SoundEvents.BEACON_ACTIVATE, 1.2f, 0.5f);
        sound(p.serverLevel(), p.position(), SoundEvents.WARDEN_HEARTBEAT, 1f, 0.6f);
        send(s);
        return true;
    }

    private static CitadelHalls.Job hallJob(Session s, int floor, int pad) {
        CitadelHalls.Layout l = CitadelHalls.layout(CitadelHalls.shapeFor(floor), CitadelHalls.origin(s.slot, pad));
        return CitadelHalls.job(s.level, l, TowerFloors.wing(floor), TowerFloors.endless(floor));
    }

    private static int freeSlot() {
        int slot = 0;
        boolean taken;
        do {
            taken = false;
            for (Session s : SESSIONS.values()) {
                if (s.slot == slot) {
                    taken = true;
                    slot++;
                    break;
                }
            }
        } while (taken);
        return slot;
    }

    // ================================================================ ticking

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty()) return;
        List<Session> ended = new ArrayList<>();
        for (Iterator<Session> it = SESSIONS.values().iterator(); it.hasNext(); ) {
            Session s = it.next();
            if (s.player.hasDisconnected()) {
                it.remove();
                abandon(s);
                continue;
            }
            s.tick++;
            try {
                if (step(s)) ended.add(s);
            } catch (RuntimeException e) {
                BeyondSolo.LOGGER.error("Crimson Citadel run failed for {}", s.player.getGameProfile().getName(), e);
                ended.add(s);
                cleanup(s);
                freeze(s.player, false);
                returnHome(s.player);
                send(s.player, TowerPayload.none());
            }
        }
        for (Session s : ended) SESSIONS.remove(s.player.getUUID());
    }

    /** One tick of a run. True when it is over. */
    private static boolean step(Session s) {
        ServerPlayer p = s.player;
        if (s.build != null && !s.build.done()) s.build.step(CitadelHalls.BUDGET);
        if (s.clockOn) s.clock++;
        // taken out some other way (a command, a portal): the run is over, quietly
        if (s.phase != Phase.PULL && s.phase != Phase.LEAVING && s.phase != Phase.FALLEN && p.level() != s.level) {
            cleanup(s);
            freeze(p, false);
            data(p).leave();
            send(p, TowerPayload.none());
            return true;
        }
        switch (s.phase) {
            case PULL -> {
                pullFx(s);
                if (s.tick >= PULL_TICKS) arrive(s);
            }
            case ARRIVING -> {
                if (s.tick >= ARRIVE_TICKS) {
                    setPhase(s, Phase.FIGHT);
                    if (s.mode == Mode.RUSH) beginBout(s);
                    else beginFloor(s);
                }
            }
            case FIGHT -> fight(s);
            case CLEARED -> {
                if (exitHold(s)) return false;
                CitadelHalls.Layout h = s.hall;
                if (h != null && h.stairBox() != null && p.getBoundingBox().intersects(h.stairBox())) {
                    if (cacheSealed(s)) {
                        // the cache first: its loot would be left behind
                        if (s.tick - s.cacheHint > 60 || s.tick < s.cacheHint) {
                            s.cacheHint = s.tick;
                            send(s, TowerPayload.EV_CACHE);
                        }
                    } else {
                        climb(s);
                    }
                } else if (s.tick % 20 == 0) send(s);
            }
            case CLIMBING -> climbing(s);
            case VICTORY -> {
                victoryFx(s);
                if (s.tick >= VICTORY_TICKS) {
                    freeze(p, false);
                    setPhase(s, Phase.CLEARED);
                    send(s);
                }
            }
            case BREATHER -> {
                if (s.tick % 20 == 0) send(s);
                if (s.tick % 4 == 0) s.level.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 2.1, p.getZ(), 1, 0.3, 0.1, 0.3, 0);
                if (s.tick >= BREATHER_TICKS) {
                    setPhase(s, Phase.FIGHT);
                    beginBout(s);
                }
            }
            case RUSH_DONE -> {
                if (exitHold(s)) return false;
                if (s.tick >= RUSH_DONE_TICKS) leave(s);
                else if (s.tick % 20 == 0) send(s);
            }
            case FALLEN -> {
                if (s.tick >= FALLEN_TICKS) {
                    release(s, true);
                    return true;
                }
            }
            case LEAVING -> {
                leavingFx(s);
                if (s.tick >= LEAVE_TICKS) {
                    release(s, false);
                    return true;
                }
            }
        }
        return s.ended;
    }

    // ================================================================ the pull

    /** A ring of crimson light under the hunter, the light rising, then the flash that takes them. */
    private static void pullFx(Session s) {
        ServerPlayer p = s.player;
        ServerLevel here = p.serverLevel();
        if (s.tick == 2) EnemyFx.telegraphCircle(here, p.position(), 2.2f, PULL_TICKS - 4, CRIMSON);
        if (s.tick % 3 == 0) {
            double a = s.tick * 0.45;
            for (int i = 0; i < 3; i++) {
                double b = a + i * Math.PI * 2 / 3;
                here.sendParticles(dust(CRIMSON, 1.1f), p.getX() + Math.cos(b) * 1.6, p.getY() + 0.1 + s.tick * 0.04, p.getZ() + Math.sin(b) * 1.6, 1, 0, 0, 0, 0);
            }
        }
        if (s.tick == PULL_TICKS - 14) {
            EnemyFx.pillar(here, p.position(), CRIMSON, 1.4f, 12f);
            sound(here, p.position(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 1.2f, 0.6f);
        }
    }

    /** Lands the hunter in the first hall. */
    private static void arrive(Session s) {
        ServerPlayer p = s.player;
        if (s.build == null) throw new IllegalStateException("no hall to arrive in");
        s.build.finish();
        s.hall = s.build.layout;
        s.pad = s.buildPad;
        s.build = null;
        TowerData d = data(p);
        if (!d.inside()) d.enter(p.level().dimension().location(), p.position(), p.getYRot());
        p.serverLevel().sendParticles(ParticleTypes.FLASH, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
        teleport(p, s.level, s.hall.start(), s.hall.startYaw());
        freeze(p, false);
        p.clearFire();
        s.level.sendParticles(dust(CRIMSON, 1.4f), p.getX(), p.getY() + 1, p.getZ(), 40, 0.4, 0.9, 0.4, 0);
        sound(s.level, p.position(), SoundEvents.ENDER_DRAGON_GROWL, 0.35f, 0.5f);
        setPhase(s, Phase.ARRIVING);
        send(s);
    }

    // ================================================================ a floor

    private static void beginFloor(Session s) {
        int f = s.floor;
        s.wave = 0;
        s.waves = 1;
        s.foes.clear();
        s.spawns.clear();
        s.boss = null;
        s.bossAwake = false;
        s.bossEnraged = false;
        s.bossDown = -1;
        s.moon = false;
        s.hold = 0;
        s.mix = EnemyRoster.random(TowerFloors.rank(f), s.level.random);
        switch (TowerFloors.kind(f)) {
            case BATTLE -> {
                s.waves = TowerFloors.waves(f);
                nextWave(s);
            }
            case ELITE -> {
                s.waves = 2;
                eliteWave(s);
            }
            case SIEGE -> {
                s.wave = 1;
                s.siegeLeft = TowerFloors.SIEGE_TICKS;
                for (int i = 0; i < TowerFloors.siegeCrowd(f) / 2 + 1; i++) queueSpawn(s, i == 0, null, spot(s), i * 4);
                send(s, TowerPayload.EV_WAVE);
            }
            case KEEPER -> {
                s.wave = 1;
                spawnKeeper(s);
                send(s);
            }
            case LORD -> {
                s.wave = 1;
                spawnLord(s);
                send(s);
            }
        }
    }

    private static void fight(Session s) {
        releaseDue(s);
        if (s.tick % 20 == 0) keepFoes(s);
        List<LivingEntity> alive = living(s);
        TowerFloors.Kind kind = s.kind();
        switch (kind) {
            case BATTLE -> {
                if (s.spawns.isEmpty() && alive.size() <= (s.wave < s.waves ? 1 : 0)) {
                    if (s.wave < s.waves) nextWave(s);
                    else floorWon(s);
                }
            }
            case ELITE -> {
                if (s.spawns.isEmpty() && s.wave < s.waves && alive.size() <= 1) champion(s);
                else if (s.spawns.isEmpty() && s.wave >= s.waves && alive.isEmpty()) floorWon(s);
            }
            case SIEGE -> {
                s.siegeLeft--;
                if (s.siegeLeft == 200) send(s, TowerPayload.EV_SIEGE_END);
                if (s.siegeLeft <= 0) {
                    crumble(s);
                    floorWon(s);
                    return;
                }
                if (s.tick % 20 == 10 && alive.size() + s.spawns.size() < TowerFloors.siegeCrowd(s.floor)) {
                    int n = Math.min(2, TowerFloors.siegeCrowd(s.floor) - alive.size() - s.spawns.size());
                    float late = 1f - s.siegeLeft / (float) TowerFloors.SIEGE_TICKS;
                    for (int i = 0; i < n; i++) queueSpawn(s, s.level.random.nextFloat() < TowerFloors.leaderChance(s.floor) * (0.5f + late), null, spot(s), i * 5);
                }
            }
            case KEEPER, LORD -> bossTick(s);
        }
        if (s.phase == Phase.FIGHT && s.tick % 10 == 0) send(s);
    }

    private static void nextWave(Session s) {
        s.wave++;
        RandomSource r = s.level.random;
        int n = TowerFloors.waveSize(s.floor);
        for (int i = 0; i < n; i++) queueSpawn(s, r.nextFloat() < TowerFloors.leaderChance(s.floor), null, spot(s), i * 3);
        send(s, TowerPayload.EV_WAVE);
    }

    /** An elite floor's first wave: elites and a few at their side. */
    private static void eliteWave(Session s) {
        s.wave = 1;
        int elites = 2 + (s.floor >= 45 ? 1 : 0);
        for (int i = 0; i < elites; i++) queueSpawn(s, true, Variant.ELITE, spot(s), i * 4);
        for (int i = 0; i < 2; i++) queueSpawn(s, false, null, spot(s), 6 + i * 3);
        send(s, TowerPayload.EV_WAVE);
    }

    /** The champion comes for whoever thinned out its elites. */
    private static void champion(Session s) {
        s.wave = 2;
        Vec3 at = spot(s);
        queueSpawn(s, true, Variant.CHAMPION, at, 6);
        if (s.floor >= 65) queueSpawn(s, true, Variant.ELITE, spot(s), 12);
        EnemyFx.pillar(s.level, at, GOLD, 1.6f, 9f);
        sound(s.level, at, SoundEvents.RAID_HORN.value(), 1.5f, 0.6f);
        send(s, TowerPayload.EV_CHAMPION);
    }

    // ---------------------------------------------------------------- creatures

    /** A spot to bring a creature out at, away from the hunter. */
    private static Vec3 spot(Session s) {
        List<Vec3> spots = s.hall == null ? List.of() : s.hall.spawns();
        if (spots.isEmpty()) return s.player.position().add(4, 0, -4);
        RandomSource r = s.level.random;
        Vec3 best = spots.get(r.nextInt(spots.size()));
        for (int i = 0; i < 8; i++) {
            Vec3 c = spots.get(r.nextInt(spots.size()));
            if (c.distanceToSqr(s.player.position()) > 7 * 7) return c;
            best = c;
        }
        return best;
    }

    private static void queueSpawn(Session s, boolean leader, @Nullable Variant forced, Vec3 at, int delay) {
        RandomSource r = s.level.random;
        PlayerRank rank = s.rank();
        EntityType<?> type = s.mix.pick(r);
        EnemyTraits traits = Enemies.roll(rank, s.mix, type, r, leader, s.wave >= s.waves);
        if (forced != null) {
            traits = new EnemyTraits(s.mix.affinityFor(type, r, true), forced, Affix.mask(Affix.roll(forced == Variant.CHAMPION ? 2 : 1, r)));
        }
        Vec3 jittered = at.add(r.nextDouble() - 0.5, 0, r.nextDouble() - 0.5);
        s.spawns.add(new Pending(jittered, s.tick + SPAWN_WARN + delay, type, traits));
        EnemyFx.telegraphCircle(s.level, jittered, traits.variant() == Variant.CHAMPION ? 2.4f : traits.variant().special() ? 1.7f : 1.2f,
                SPAWN_WARN + delay, traits.variant().special() ? GOLD : CRIMSON);
    }

    private static void releaseDue(Session s) {
        if (s.spawns.isEmpty()) return;
        List<Pending> due = new ArrayList<>();
        s.spawns.removeIf(ps -> {
            if (ps.due > s.tick) return false;
            due.add(ps);
            return true;
        });
        for (Pending ps : due) rise(s, ps);
    }

    /** A creature steps out of the crimson light. */
    private static void rise(Session s, Pending ps) {
        ServerLevel level = s.level;
        Mob mob = Enemies.spawn(level, s.rank(), ps.type, BlockPos.containing(ps.at), ps.traits, -1);
        level.sendParticles(dust(CRIMSON, 1.2f), ps.at.x, ps.at.y + 0.6, ps.at.z, 18, 0.35, 0.5, 0.35, 0);
        sound(level, ps.at, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.5f, 1.6f);
        if (mob == null) return;
        adopt(s, mob);
        if (ps.traits.variant() == Variant.CHAMPION) {
            EnemyFx.pillar(level, ps.at, GOLD, 1.6f, 7f);
            sound(level, ps.at, SoundEvents.WITHER_SPAWN, 0.5f, 1.2f);
        }
    }

    /** Makes a creature one of the run's: tagged, kept, set on the hunter, made stronger in the spire. */
    private static void adopt(Session s, Mob mob) {
        mob.addTag(FOE_TAG);
        mob.setPersistenceRequired();
        AttributeInstance follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (follow != null) follow.setBaseValue(Math.max(follow.getBaseValue(), 48));
        if (s.mode != Mode.RUSH && TowerFloors.endless(s.floor)) {
            scale(mob, Attributes.MAX_HEALTH, SPIRE_HEALTH, TowerFloors.healthScale(s.floor));
            scale(mob, Attributes.ATTACK_DAMAGE, SPIRE_DAMAGE, TowerFloors.damageScale(s.floor));
            mob.setHealth(mob.getMaxHealth());
        }
        mob.setTarget(s.player);
        s.foes.add(mob.getUUID());
    }

    private static void scale(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, ResourceLocation id, float by) {
        AttributeInstance a = mob.getAttribute(attr);
        if (a == null || by <= 1.0001f) return;
        a.removeModifier(id);
        a.addPermanentModifier(new AttributeModifier(id, by - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** Creatures that wandered out of the hall are brought back, and kept on the hunter. */
    private static void keepFoes(Session s) {
        if (s.hall == null) return;
        AABB bounds = s.hall.bounds().inflate(1.5);
        for (LivingEntity e : living(s)) {
            if (!bounds.contains(e.position())) {
                Vec3 at = spot(s);
                e.teleportTo(at.x, at.y, at.z);
            }
            if (e instanceof Mob m && m.getTarget() != s.player && s.player.isAlive()) m.setTarget(s.player);
        }
    }

    /** A siege's time is up: what is left of the horde falls to ash. */
    private static void crumble(Session s) {
        s.spawns.clear();
        for (LivingEntity e : living(s)) {
            s.level.sendParticles(ParticleTypes.ASH, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
            s.level.sendParticles(dust(CRIMSON, 1f), e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 12, 0.3, 0.5, 0.3, 0);
            e.discard();
        }
        s.foes.clear();
    }

    // ---------------------------------------------------------------- gatekeepers, the lord, the rush's bosses

    private static void spawnKeeper(Session s) {
        int f = s.floor;
        GateBossEntity boss = ModEntities.GATE_BOSS.get().create(s.level);
        if (boss == null) return;
        boss.configure(TowerFloors.rank(f), TowerFloors.keeper(f), TowerFloors.keeperAscended(f));
        boss.setCustomName(TowerFloors.keeperName(f));
        boss.setCustomNameVisible(false);
        placeBoss(s, boss);
    }

    private static void spawnLord(Session s) {
        CitadelLordEntity lord = TowerModule.CITADEL_LORD.get().create(s.level);
        if (lord == null) return;
        placeBoss(s, lord);
    }

    private static void placeBoss(Session s, Mob boss) {
        Vec3 at = s.hall.bossSpot();
        boss.moveTo(at.x, at.y, at.z, 0f, 0f);
        boss.setYHeadRot(0f);
        boss.yBodyRot = 0f;
        boss.addTag(FOE_TAG);
        boss.setPersistenceRequired();
        if (s.mode != Mode.RUSH && TowerFloors.endless(s.floor)) {
            scale(boss, Attributes.MAX_HEALTH, SPIRE_HEALTH, TowerFloors.healthScale(s.floor));
            scale(boss, Attributes.ATTACK_DAMAGE, SPIRE_DAMAGE, TowerFloors.damageScale(s.floor));
            boss.setHealth(boss.getMaxHealth());
        }
        s.level.addFreshEntity(boss);
        s.boss = boss.getUUID();
        s.bossAwake = false;
        s.bossEnraged = false;
        s.bossDown = -1;
        s.moon = false;
        s.level.sendParticles(dust(CRIMSON, 1.6f), at.x, at.y + 1, at.z, 40, 0.8, 1.2, 0.8, 0);
    }

    @Nullable
    private static LivingEntity boss(Session s) {
        if (s.boss == null) return null;
        return s.level.getEntity(s.boss) instanceof LivingEntity l ? l : null;
    }

    private static void bossTick(Session s) {
        LivingEntity boss = boss(s);
        if (s.bossDown >= 0) {
            int length = boss instanceof CitadelLordEntity || s.kind() == TowerFloors.Kind.LORD ? CitadelLordEntity.DEATH_TICKS : GateBossEntity.DEATH_TICKS;
            if (s.tick - s.bossDown >= length - 6) bossFallen(s);
            return;
        }
        if (boss == null) {
            // gone without dying where we could see it (a command, or it fell out of the world): it is beaten
            if (s.boss != null && s.level.isLoaded(BlockPos.containing(s.hall.bossSpot()))) {
                s.bossDown = s.tick - 200;
            } else if (s.boss == null && s.kind() == TowerFloors.Kind.LORD && s.mode != Mode.RUSH) {
                spawnLord(s);
            }
            return;
        }
        if (boss.isDeadOrDying()) {
            bossDefeated(s, boss);
            return;
        }
        if (!s.bossAwake) {
            int wakeAt = s.mode == Mode.RUSH ? BOUT_WAKE : KEEPER_WAKE_TICKS;
            if (s.tick >= wakeAt || s.mode != Mode.RUSH && s.player.distanceTo(boss) < KEEPER_WAKE_RANGE) wakeBoss(s, boss);
            return;
        }
        if (boss instanceof Mob m && m.getTarget() != s.player && s.player.isAlive()) m.setTarget(s.player);
        if (boss instanceof DungeonBoss db && db.isEnraged() && !s.bossEnraged) {
            s.bossEnraged = true;
            enraged(s, boss);
        }
        if (boss instanceof CitadelLordEntity lord && lord.bloodMoon() && !s.moon) {
            s.moon = true;
            send(s, TowerPayload.EV_MOON);
        }
        // a boss that strays from its hall comes back
        if (s.tick % 20 == 0 && s.hall != null && !s.hall.bounds().inflate(3).contains(boss.position())) {
            Vec3 at = s.hall.bossSpot();
            boss.teleportTo(at.x, at.y, at.z);
        }
    }

    private static void wakeBoss(Session s, LivingEntity boss) {
        s.bossAwake = true;
        if (boss instanceof GateBossEntity g) g.wake();
        else if (boss instanceof CitadelLordEntity lord) lord.wake();
        if (boss instanceof Mob m) m.setTarget(s.player);
        if (s.mode == Mode.RUSH && !s.clockOn) s.clockOn = true;
        PlayerRank rank = boss instanceof DungeonBoss db ? db.rank() : s.rank();
        send(s.player, new GatePayload(GatePayload.BOSS_INTRO, rank.ordinal(), boss.getId(), 0, bossName(s, boss), bossTitle(s)));
        send(s);
    }

    private static Component bossName(Session s, LivingEntity boss) {
        if (boss instanceof CitadelLordEntity) return Component.translatable("tower.beyondsolo.lord.name");
        if (s.mode != Mode.RUSH) return TowerFloors.keeperName(s.floor);
        return boss.getDisplayName();
    }

    private static Component bossTitle(Session s) {
        if (s.mode == Mode.RUSH) return Component.translatable("tower.beyondsolo.rush.bout", s.bout + 1, s.bouts.size());
        return TowerFloors.keeperTitle(s.floor);
    }

    /** Its second wind: the overlay knows, and a gatekeeper calls two of its hall's creatures to its side. */
    private static void enraged(Session s, LivingEntity boss) {
        PlayerRank rank = boss instanceof DungeonBoss db ? db.rank() : s.rank();
        send(s.player, new GatePayload(GatePayload.ENRAGED, rank.ordinal(), boss.getId(), 0, bossName(s, boss), Component.empty()));
        if (boss instanceof CitadelLordEntity) return;
        for (int i = 0; i < 2; i++) {
            float a = s.level.random.nextFloat() * Mth.TWO_PI;
            Vec3 at = boss.position().add(Mth.cos(a) * (boss.getBbWidth() / 2 + 3), 0, Mth.sin(a) * (boss.getBbWidth() / 2 + 3));
            queueSpawn(s, false, null, at, i * 4);
        }
    }

    /** The lord's last stand: two champions of his court come out of the light at his side. */
    public static void lordGuards(ServerLevel level, CitadelLordEntity lord) {
        for (Session s : SESSIONS.values()) {
            if (!lord.getUUID().equals(s.boss) || s.level != level) continue;
            s.mix = EnemyRoster.random(PlayerRank.S, level.random);
            for (int i = 0; i < 2; i++) {
                float a = (i == 0 ? -1 : 1) * Mth.HALF_PI + lord.getYRot() * Mth.DEG_TO_RAD;
                Vec3 at = lord.position().add(Mth.cos(a) * 3.5, 0, Mth.sin(a) * 3.5);
                queueSpawn(s, true, Variant.CHAMPION, at, i * 6);
            }
        }
    }

    private static void bossDefeated(Session s, LivingEntity boss) {
        s.bossDown = s.tick;
        PlayerRank rank = boss instanceof DungeonBoss db ? db.rank() : s.rank();
        send(s.player, new GatePayload(GatePayload.BOSS_DEFEATED, rank.ordinal(), boss.getId(), 0, bossName(s, boss),
                Component.translatable(boss instanceof CitadelLordEntity ? "tower.beyondsolo.lord.defeated" : "gate.beyondsolo.boss_defeated")));
        // what is left of its court falls with it
        s.spawns.clear();
        for (LivingEntity e : living(s)) {
            if (e == boss) continue;
            s.level.sendParticles(ParticleTypes.ASH, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 16, 0.3, 0.6, 0.3, 0.02);
            e.discard();
        }
    }

    /** The boss's death has played out. */
    private static void bossFallen(Session s) {
        s.bossDown = -1;
        if (s.mode == Mode.RUSH) {
            boutWon(s);
            return;
        }
        if (s.kind() == TowerFloors.Kind.LORD) {
            victory(s);
            return;
        }
        dropCache(s, TowerFloors.rank(s.floor), TowerFloors.keeper(s.floor), TowerFloors.keeperAscended(s.floor));
        floorWon(s);
    }

    private static void dropCache(Session s, PlayerRank rank, @Nullable BossKind kind, boolean ascended) {
        if (s.hall == null) return;
        BlockPos at = BlockPos.containing(s.hall.bossSpot()).offset(0, 0, 3);
        for (int i = 0; i < 3 && !s.level.getBlockState(at).isAir(); i++) at = at.above();
        s.level.setBlock(at, ModBlocks.REWARD_CACHE.get().defaultBlockState().setValue(RewardCacheBlock.FACING, Direction.SOUTH), 3);
        if (s.level.getBlockEntity(at) instanceof RewardCacheBlockEntity cache) {
            cache.setup(rank, kind, ascended, s.level.getGameTime());
            s.cache = at.immutable();
        }
    }

    // ---------------------------------------------------------------- won

    /** The floor is won: its rewards, the seal off the stair, the next hall going up. */
    private static void floorWon(Session s) {
        ServerPlayer p = s.player;
        TowerData d = data(p);
        int f = s.floor;
        s.spawns.clear();
        s.floorsCleared++;
        d.floorsCleared++;
        SystemAPI.grantXp(p, TowerFloors.xp(f), XP_SOURCE);
        Economy.add(p, TowerFloors.gold(f), Economy.Reason.TOWER);
        int event = TowerPayload.EV_NONE;
        if (!TowerFloors.endless(f)) {
            d.highest = Math.max(d.highest, f);
            if (TowerFloors.kind(f) == TowerFloors.Kind.KEEPER && f > d.checkpoint) {
                d.checkpoint = TowerFloors.checkpointOf(f);
                event = TowerPayload.EV_CHECKPOINT;
            }
        } else if (f > d.spireBest) {
            d.spireBest = f;
            if (!s.record) event = TowerPayload.EV_RECORD;
            s.record = true;
        }
        openWay(s);
        setPhase(s, Phase.CLEARED);
        sound(s.level, p.position(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 0.8f);
        sound(s.level, p.position(), SoundEvents.BEACON_POWER_SELECT, 1f, 1.3f);
        send(s, event);
        TowerAPI.sync(p);
    }

    /** The seal comes off the stair door and the next hall starts going up. */
    private static void openWay(Session s) {
        if (s.hall == null) return;
        CitadelHalls.openDoor(s.level, s.hall);
        if (s.hall.door() != null) {
            Vec3 door = Vec3.atBottomCenterOf(s.hall.door()).add(0, 1.5, 0.5);
            s.level.sendParticles(dust(CRIMSON, 1.4f), door.x, door.y, door.z, 60, 1.2, 1.4, 0.2, 0);
            sound(s.level, door, SoundEvents.IRON_DOOR_OPEN, 1.4f, 0.5f);
            sound(s.level, door, SoundEvents.BEACON_DEACTIVATE, 1.2f, 0.7f);
        }
        s.buildPad = s.pad == 0 ? 1 : 0;
        s.build = hallJob(s, s.floor + 1, s.buildPad);
    }

    /** The lord has fallen: the Citadel is theirs (and perhaps the Sigil of Dominion). */
    private static void victory(Session s) {
        ServerPlayer p = s.player;
        TowerData d = data(p);
        RandomSource r = s.level.random;
        boolean first = d.lordKills == 0;
        d.lordKills++;
        d.checkpoint = TowerFloors.TOP;
        d.highest = Math.max(d.highest, TowerFloors.TOP);
        d.floorsCleared++;
        s.floorsCleared++;
        SystemAPI.grantXp(p, TowerFloors.xp(TowerFloors.TOP), XP_SOURCE);
        Economy.add(p, TowerFloors.gold(TowerFloors.TOP), Economy.Reason.TOWER);
        dropCache(s, PlayerRank.S, BossKind.KNIGHT, true);
        // the sigil: one kill in four, and never more than four without it
        boolean sigil;
        if (!d.sigil) {
            d.sigilDry++;
            sigil = d.sigilDry >= SIGIL_PITY || r.nextFloat() < SIGIL_CHANCE;
        } else {
            sigil = r.nextFloat() < 0.1f;
        }
        if (sigil) {
            d.sigil = true;
            d.sigilDry = 0;
            s.sigilDropped = true;
            ItemStack stack = new ItemStack(TowerModule.SIGIL_OF_DOMINION.get());
            if (!p.getInventory().add(stack)) p.drop(stack, false);
        }
        if (first) SystemAPI.setTitle(p, CONQUEROR_TITLE);
        openWay(s);
        setPhase(s, Phase.VICTORY);
        freeze(p, true);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, VICTORY_TICKS + 20, 4, false, false, false));
        Vec3 c = s.hall.bossSpot();
        EnemyFx.pillar(s.level, c, CRIMSON, 2.4f, 30f);
        EnemyFx.burst(s.level, c.add(0, 4, 0), GOLD, 4f);
        sound(s.level, p.position(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.6f);
        sound(s.level, c, SoundEvents.ENDER_DRAGON_DEATH, 0.5f, 1.2f);
        send(s, sigil ? TowerPayload.EV_SIGIL : TowerPayload.EV_NONE);
        Notifications.send(p, new SystemNotification(SystemNotification.Kind.REWARD,
                Component.translatable(first ? "notify.beyondsolo.tower.conquered.title" : "notify.beyondsolo.tower.reconquered.title"),
                Component.translatable(sigil ? "notify.beyondsolo.tower.sigil.body" : "notify.beyondsolo.tower.conquered.body"), 220));
        TowerAPI.sync(p);
    }

    private static void victoryFx(Session s) {
        if (s.hall == null) return;
        Vec3 c = s.hall.bossSpot();
        if (s.tick % 3 == 0) {
            double a = s.tick * 0.3;
            s.level.sendParticles(dust(GOLD, 1f), c.x + Math.cos(a) * 3, c.y + 0.2 + (s.tick % 40) * 0.15, c.z + Math.sin(a) * 3, 1, 0, 0, 0, 0);
        }
        if (s.tick == 40 && s.sigilDropped) {
            ServerPlayer p = s.player;
            EnemyFx.pillar(s.level, p.position(), 0xB070FF, 1.2f, 14f);
            sound(s.level, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4f, 0.6f);
            sound(s.level, p.position(), SoundEvents.TOTEM_USE, 0.6f, 0.7f);
        }
    }

    /** The floor's reward cache stands unopened. */
    private static boolean cacheSealed(Session s) {
        if (s.cache == null) return false;
        if (s.level.getBlockEntity(s.cache) instanceof RewardCacheBlockEntity c && !c.isOpen()) return true;
        s.cache = null;
        return false;
    }

    /** What the hall's floor still holds (the cache's loot, dropped things, experience) goes with the hunter. */
    private static void sweep(Session s) {
        if (s.hall == null) return;
        ServerPlayer p = s.player;
        AABB box = s.hall.bounds().inflate(2);
        for (net.minecraft.world.entity.item.ItemEntity item : s.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box)) {
            ItemStack stack = item.getItem().copy();
            item.discard();
            if (!p.getInventory().add(stack) && !stack.isEmpty()) p.drop(stack, false);
        }
        for (net.minecraft.world.entity.ExperienceOrb orb : s.level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, box)) {
            p.giveExperiencePoints(orb.getValue());
            orb.discard();
        }
    }

    // ---------------------------------------------------------------- up the stair

    private static void climb(Session s) {
        setPhase(s, Phase.CLIMBING);
        freeze(s.player, true);
        sound(s.level, s.player.position(), SoundEvents.STONE_STEP, 1f, 0.6f);
        send(s);
    }

    private static void climbing(Session s) {
        ServerPlayer p = s.player;
        if (s.tick == CLIMB_SWAP) {
            if (s.build == null) s.build = hallJob(s, s.floor + 1, s.pad == 0 ? 1 : 0);
            s.build.finish();
            sweep(s);
            cleanup(s);
            s.cache = null;
            s.hall = s.build.layout;
            s.pad = s.buildPad;
            s.build = null;
            s.floor++;
            if (TowerFloors.endless(s.floor) && s.mode == Mode.CASTLE) {
                s.mode = Mode.ENDLESS;
                data(p).spireRuns++;
            }
            teleport(p, s.level, s.hall.start(), s.hall.startYaw());
            sound(s.level, p.position(), SoundEvents.ENDER_DRAGON_GROWL, 0.25f, 0.6f);
        }
        if (s.tick >= CLIMB_TICKS) {
            freeze(p, false);
            setPhase(s, Phase.ARRIVING);
            send(s);
        }
    }

    // ---------------------------------------------------------------- the boss rush

    private static void beginBout(Session s) {
        s.foes.clear();
        s.spawns.clear();
        s.hold = 0;
        if (s.bout >= s.bouts.size()) {
            rushWon(s);
            return;
        }
        RushTier.Bout b = s.bouts.get(s.bout);
        s.mix = EnemyRoster.random(s.rank(), s.level.random);
        s.wave = s.bout + 1;
        s.waves = s.bouts.size();
        if (b.lord()) {
            spawnLord(s);
        } else {
            GateBossEntity boss = ModEntities.GATE_BOSS.get().create(s.level);
            if (boss == null) return;
            boss.configure(s.rank(), b.kind(), b.ascended());
            placeBoss(s, boss);
        }
        sound(s.level, s.hall.bossSpot(), SoundEvents.RAID_HORN.value(), 2f, 0.5f);
        send(s, TowerPayload.EV_WAVE);
    }

    /** A bout won: a breather, then the next. */
    private static void boutWon(Session s) {
        ServerPlayer p = s.player;
        s.bout++;
        crumble(s);
        if (s.bout >= s.bouts.size()) {
            rushWon(s);
            return;
        }
        p.heal(p.getMaxHealth() * BREATHER_HEAL);
        if (SystemAPI.hasSystem(p)) SystemAPI.restoreMana(p, SystemAPI.get(p).maxMana() * BREATHER_HEAL);
        s.level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 20, 0.5, 0.8, 0.5, 0);
        sound(s.level, p.position(), SoundEvents.BEACON_POWER_SELECT, 1f, 1.5f);
        setPhase(s, Phase.BREATHER);
        send(s);
    }

    private static void rushWon(Session s) {
        ServerPlayer p = s.player;
        TowerData d = data(p);
        RushTier tier = s.tier;
        s.clockOn = false;
        boolean best = d.rushCleared(tier, s.clock);
        s.record = best;
        int t = tier.rank.tier();
        long xp = 600L * (1 + t) * (1 + t) * (tier.sovereign() ? 3 : 1);
        long gold = com.beyondsolo.economy.EconomyHooks.GATE_GOLD[t] * 2L * (tier.sovereign() ? 3 : 1);
        SystemAPI.grantXp(p, xp, XP_SOURCE);
        Economy.add(p, gold, Economy.Reason.TOWER);
        RushTier.Bout last = s.bouts.isEmpty() ? null : s.bouts.get(s.bouts.size() - 1);
        dropCache(s, tier.rank, last == null || last.lord() ? BossKind.KNIGHT : last.kind(), true);
        if (tier.sovereign() && d.rushClears[tier.ordinal()] == 1) SystemAPI.setTitle(p, SOVEREIGN_TITLE);
        setPhase(s, Phase.RUSH_DONE);
        sound(s.level, p.position(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.7f);
        EnemyFx.pillar(s.level, s.hall.bossSpot(), GOLD, 2f, 24f);
        send(s, best ? TowerPayload.EV_RECORD : TowerPayload.EV_NONE);
        Notifications.send(p, new SystemNotification(SystemNotification.Kind.REWARD,
                Component.translatable("notify.beyondsolo.tower.rush.title", tier.title()),
                Component.translatable(best ? "notify.beyondsolo.tower.rush.best" : "notify.beyondsolo.tower.rush.body", time(s.clock)), 200));
        TowerAPI.sync(p);
    }

    /** "4:07" for a number of ticks. */
    public static String time(long ticks) {
        long sec = Math.max(0, ticks) / 20;
        return sec / 60 + ":" + (sec % 60 < 10 ? "0" : "") + sec % 60;
    }

    // ================================================================ leaving

    /** Standing on the exit sigil: true once it has taken them. */
    private static boolean exitHold(Session s) {
        if (s.hall == null) return false;
        Vec3 c = Vec3.atBottomCenterOf(s.hall.exit());
        Vec3 at = s.player.position();
        boolean on = Math.abs(at.x - c.x) < 1.4 && Math.abs(at.z - c.z) < 1.4 && Math.abs(at.y - c.y) < 1.3;
        int before = s.hold;
        s.hold = on ? s.hold + 1 : Math.max(0, s.hold - 3);
        if (on && s.hold % 8 == 1) sound(s.level, c, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 0.6f + s.hold / (float) EXIT_HOLD);
        if (s.hold != before && (s.hold % 4 == 0 || s.hold == 0 || on && before == 0)) send(s);
        if (s.hold >= EXIT_HOLD) {
            leave(s);
            return true;
        }
        return false;
    }

    /** Out: the light takes them up. */
    private static void leave(Session s) {
        ServerPlayer p = s.player;
        s.spawns.clear();
        s.clockOn = false;
        setPhase(s, Phase.LEAVING);
        freeze(p, true);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, LEAVE_TICKS + 20, 4, false, false, false));
        EnemyFx.pillar(s.level, p.position(), 0xFFE2D0, 1.2f, 16f);
        sound(s.level, p.position(), SoundEvents.BEACON_DEACTIVATE, 1.2f, 0.8f);
        sound(s.level, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 0.7f);
        send(s);
    }

    private static void leavingFx(Session s) {
        ServerPlayer p = s.player;
        if (s.tick == 8) p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, LEAVE_TICKS, 0, false, false, false));
        if (s.tick % 2 == 0) s.level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() - 0.2, p.getZ(), 2, 0.4, 0.2, 0.4, 0.02);
    }

    /** A blow that would have killed them: they fall, and the run is over. */
    private static void fall(Session s) {
        ServerPlayer p = s.player;
        s.spawns.clear();
        s.clockOn = false;
        setPhase(s, Phase.FALLEN);
        freeze(p, true);
        p.setHealth(Math.max(1f, p.getMaxHealth() * 0.2f));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, FALLEN_TICKS + 40, 4, false, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, FALLEN_TICKS, 0, false, false, false));
        data(p).falls++;
        send(p, CutscenePayload.stop());
        sound(s.level, p.position(), SoundEvents.WITHER_DEATH, 0.4f, 0.6f);
        send(s);
    }

    /** Home, after the last card. */
    private static void release(Session s, boolean fallen) {
        ServerPlayer p = s.player;
        sweep(s);
        cleanup(s);
        freeze(p, false);
        p.removeEffect(MobEffects.LEVITATION);
        p.removeEffect(MobEffects.BLINDNESS);
        returnHome(p);
        TowerData d = data(p);
        if (fallen) {
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, FALL_DEBUFF_TICKS, 1));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, FALL_DEBUFF_TICKS, 0));
            Component body = s.mode == Mode.RUSH ? Component.translatable("notify.beyondsolo.tower.fallen.rush", s.bout, s.bouts.size())
                    : s.mode == Mode.ENDLESS ? Component.translatable("notify.beyondsolo.tower.fallen.spire", s.floor, Math.max(d.spireBest, 0))
                    : Component.translatable("notify.beyondsolo.tower.fallen.body", s.floor, d.startFloor());
            Notifications.send(p, new SystemNotification(SystemNotification.Kind.ALERT, Component.translatable("notify.beyondsolo.tower.fallen.title"), body, 200));
        } else if (s.mode != Mode.RUSH) {
            Notifications.send(p, new SystemNotification(SystemNotification.Kind.INFO, Component.translatable("notify.beyondsolo.tower.left.title"),
                    Component.translatable("notify.beyondsolo.tower.left.body", s.floorsCleared, d.startFloor()), 160));
        }
        send(p, TowerPayload.none());
        TowerAPI.sync(p);
    }

    /** They logged out: the run ends; they wake up at home. */
    private static void abandon(Session s) {
        freeze(s.player, false);
        cleanup(s);
    }

    private static void cleanup(Session s) {
        if (s.level == null) return;
        for (UUID id : s.foes) {
            Entity e = s.level.getEntity(id);
            if (e != null) e.discard();
        }
        if (s.boss != null) {
            Entity e = s.level.getEntity(s.boss);
            if (e != null) e.discard();
        }
        s.foes.clear();
        s.spawns.clear();
        s.boss = null;
    }

    /** Sends a player back to where the System took them from. */
    static void returnHome(ServerPlayer p) {
        TowerData d = data(p);
        if (!d.inside()) return;
        MinecraftServer server = p.server;
        ResourceLocation dim = d.returnDimension();
        ServerLevel home = dim == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
        if (home == null) home = server.overworld();
        Vec3 at = d.returnPos();
        teleport(p, home, at, d.returnYaw());
        d.leave();
        home.sendParticles(dust(CRIMSON, 1.2f), at.x, at.y + 1, at.z, 40, 0.4, 0.9, 0.4, 0);
    }

    private static void teleport(ServerPlayer p, ServerLevel level, Vec3 at, float yaw) {
        p.stopRiding();
        p.teleportTo(level, at.x, at.y, at.z, yaw, 0f);
        if (p.level() == level && p.position().distanceToSqr(at) > 1) p.moveTo(at.x, at.y, at.z, yaw, 0f);
        p.fallDistance = 0;
        p.setDeltaMovement(Vec3.ZERO);
    }

    // ================================================================ events

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.getTags().contains(FOE_TAG)) {
            UUID id = dead.getUUID();
            for (Session s : SESSIONS.values()) {
                if (s.foes.remove(id) && s.phase == Phase.FIGHT) send(s);
            }
            return;
        }
        if (!(dead instanceof ServerPlayer p)) return;
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || s.phase == Phase.PULL) return;
        event.setCanceled(true);
        p.setHealth(Math.max(1f, p.getMaxHealth() * 0.2f));
        if (s.phase != Phase.FALLEN && s.phase != Phase.LEAVING) fall(s);
    }

    /** A hit that would kill them ends the run instead: no real death, and no totem used up. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onFatalDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || s.phase == Phase.PULL) return;
        if (event.getNewDamage() < p.getHealth()) return;
        event.setNewDamage(0);
        if (s.phase == Phase.FALLEN || s.phase == Phase.LEAVING) return;
        p.server.execute(() -> {
            if (SESSIONS.get(p.getUUID()) == s && s.phase != Phase.FALLEN && s.phase != Phase.LEAVING) fall(s);
        });
    }

    @SubscribeEvent
    static void onHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Session s = SESSIONS.get(p.getUUID());
        if (s == null) return;
        switch (s.phase) {
            case PULL, ARRIVING, CLIMBING, VICTORY, FALLEN, LEAVING -> event.setCanceled(true);
            default -> {}
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        TowerData d = data(p);
        if (d.inside() && !SESSIONS.containsKey(p.getUUID())) {
            freeze(p, false);
            returnHome(p);
        }
        TowerAPI.sync(p);
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        SESSIONS.clear();
    }

    /** The Citadel's stone can't be broken or built on (the halls would be no obstacle), except by builders. */
    @SubscribeEvent
    static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && level.dimension().equals(DIMENSION) && !event.getPlayer().isCreative()) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level && level.dimension().equals(DIMENSION)
                && !(event.getEntity() instanceof Player pl && pl.isCreative())) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onExplode(ExplosionEvent.Detonate event) {
        if (event.getLevel().dimension().equals(DIMENSION)) event.getAffectedBlocks().clear();
    }

    // ================================================================ helpers

    private static List<LivingEntity> living(Session s) {
        List<LivingEntity> out = new ArrayList<>();
        if (s.level == null) return out;
        for (Iterator<UUID> it = s.foes.iterator(); it.hasNext(); ) {
            Entity e = s.level.getEntity(it.next());
            if (e instanceof LivingEntity l && l.isAlive()) out.add(l);
            else if (e == null || !e.isAlive()) it.remove();
        }
        return out;
    }

    private static void setPhase(Session s, Phase phase) {
        s.phase = phase;
        s.tick = 0;
        if (phase != Phase.CLEARED && phase != Phase.RUSH_DONE) s.hold = 0;
    }

    /** Holds the player still while the System has them. */
    static void freeze(ServerPlayer p, boolean on) {
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance jump = p.getAttribute(Attributes.JUMP_STRENGTH);
        for (AttributeInstance a : new AttributeInstance[]{speed, jump}) {
            if (a == null) continue;
            a.removeModifier(FREEZE);
            if (on) a.addTransientModifier(new AttributeModifier(FREEZE, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        if (on) p.setDeltaMovement(Vec3.ZERO);
    }

    private static void send(Session s) {
        send(s, TowerPayload.EV_NONE);
    }

    private static void send(Session s, int event) {
        ServerPlayer p = s.player;
        TowerData d = data(p);
        int phase = switch (s.phase) {
            case PULL -> TowerPayload.PULL;
            case ARRIVING -> TowerPayload.ARRIVING;
            case FIGHT -> TowerPayload.FIGHT;
            case CLEARED -> TowerPayload.CLEARED;
            case CLIMBING -> TowerPayload.CLIMBING;
            case VICTORY -> TowerPayload.VICTORY;
            case BREATHER -> TowerPayload.BREATHER;
            case RUSH_DONE -> TowerPayload.RUSH_DONE;
            case FALLEN -> TowerPayload.FALLEN;
            case LEAVING -> TowerPayload.LEAVING;
        };
        int length = switch (s.phase) {
            case PULL -> PULL_TICKS;
            case ARRIVING -> ARRIVE_TICKS;
            case CLIMBING -> CLIMB_TICKS;
            case VICTORY -> VICTORY_TICKS;
            case BREATHER -> BREATHER_TICKS;
            case RUSH_DONE -> RUSH_DONE_TICKS;
            case FALLEN -> FALLEN_TICKS;
            case LEAVING -> LEAVE_TICKS;
            default -> 0;
        };
        int mode = switch (s.mode) {
            case CASTLE -> TowerPayload.CASTLE;
            case ENDLESS -> TowerPayload.ENDLESS;
            case RUSH -> TowerPayload.RUSH;
        };
        int flags = 0;
        CitadelHalls.Layout h = s.hall;
        boolean won = s.phase == Phase.CLEARED || s.phase == Phase.CLIMBING || s.phase == Phase.VICTORY || s.phase == Phase.RUSH_DONE;
        if (won && h != null && h.door() != null) flags |= TowerPayload.F_DOOR_OPEN | TowerPayload.F_STAIR;
        if (s.phase == Phase.CLEARED || s.phase == Phase.RUSH_DONE) flags |= TowerPayload.F_EXIT;
        if (s.moon && s.phase == Phase.FIGHT) flags |= TowerPayload.F_MOON;
        if (s.record) flags |= TowerPayload.F_RECORD;
        if (d.conquered()) flags |= TowerPayload.F_CONQUERED;
        int kind = s.mode == Mode.RUSH ? TowerFloors.Kind.KEEPER.ordinal() : TowerFloors.kind(Math.max(1, s.floor)).ordinal();
        int wing = TowerFloors.wing(Math.max(1, s.floor)).ordinal();
        int clock = s.mode == Mode.RUSH ? s.clock : s.kind() == TowerFloors.Kind.SIEGE ? Math.max(0, s.siegeLeft) : 0;
        int best = s.mode == Mode.RUSH && s.tier != null ? (int) Math.min(Integer.MAX_VALUE, d.rushBest[s.tier.ordinal()]) : d.spireBest;
        Component title = s.mode == Mode.RUSH && s.tier != null ? s.tier.title()
                : TowerFloors.endless(Math.max(1, s.floor)) ? Component.translatable("tower.beyondsolo.spire")
                : TowerFloors.wing(Math.max(1, s.floor)).title();
        Component subtitle = s.mode == Mode.RUSH ? Component.translatable("tower.beyondsolo.rush.subtitle", s.bouts.size())
                : switch (TowerFloors.kind(Math.max(1, s.floor))) {
            case KEEPER, LORD -> TowerFloors.keeperName(s.floor);
            default -> Component.translatable("tower.beyondsolo.kind." + TowerFloors.kind(Math.max(1, s.floor)).id());
        };
        BlockPos door = h == null || h.door() == null ? BlockPos.ZERO : h.door();
        BlockPos exit = h == null ? BlockPos.ZERO : h.exit();
        BlockPos stair = h == null ? BlockPos.ZERO : h.stairTop();
        int tickShown = s.phase == Phase.FIGHT && s.mode == Mode.RUSH ? s.clock : s.tick;
        PacketDistributor.sendToPlayer(p, new TowerPayload(phase, mode, s.floor, kind, wing, tickShown, length, s.wave, s.waves,
                living(s).size() + s.spawns.size(), event, s.tier == null ? -1 : s.tier.ordinal(), Math.min(s.bout + 1, Math.max(1, s.bouts.size())),
                s.bouts.size(), clock, best, s.hold, flags, door, exit, stair, title, subtitle));
    }

    private static void send(ServerPlayer p, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(p, payload);
    }

    private static DustParticleOptions dust(int rgb, float scale) {
        return new DustParticleOptions(new org.joml.Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f), Math.min(4f, scale));
    }

    private static void sound(@Nullable ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        if (level != null) level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    // ================================================================ tests and commands

    /** Tests: the run's living creatures. */
    public static List<LivingEntity> foes(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        return s == null ? List.of() : living(s);
    }

    /** Tests: the boss standing in the hall (gatekeeper, lord, or the rush's), or null. */
    @Nullable
    public static LivingEntity currentBoss(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        return s == null ? null : boss(s);
    }

    /** Tests and commands: the floor the run is on (0 in a rush). */
    public static int floor(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        return s == null ? -1 : s.floor;
    }

    @Nullable
    public static Mode mode(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        return s == null ? null : s.mode;
    }

    @Nullable
    public static CitadelHalls.Layout hall(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        return s == null ? null : s.hall;
    }

    /** Tests: the bouts of a rush, and which one is up (from 0). */
    public static int bout(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        return s == null ? -1 : s.bout;
    }

    public static List<RushTier.Bout> bouts(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        return s == null ? List.of() : s.bouts;
    }

    public static int clock(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        return s == null ? -1 : s.clock;
    }

    /** A line for {@code /system tower info}. */
    public static String describe(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) return "not in the Citadel";
        String where = s.mode == Mode.RUSH ? "rush " + (s.tier == null ? "?" : s.tier.id()) + " bout " + (s.bout + 1) + "/" + s.bouts.size() + " clock " + time(s.clock)
                : "floor " + s.floor + " (" + TowerFloors.kind(Math.max(1, s.floor)).id() + ", " + TowerFloors.wing(Math.max(1, s.floor)).id() + ", " + TowerFloors.rank(Math.max(1, s.floor)) + ")";
        return s.mode + " " + where + ", phase " + s.phase + " t" + s.tick + ", wave " + s.wave + "/" + s.waves + ", foes " + living(s).size()
                + " (+" + s.spawns.size() + " coming)" + (s.boss != null ? ", boss " + (s.bossAwake ? "awake" : "dormant") : "");
    }

    /**
     * Commands and tests: skip ahead. "arrive" ends the pull or the title card; "clear" wins the floor (or the bout)
     * now; "climb" goes up the stair; "wake" wakes the boss; "kill" kills the boss; "enrage" takes it below half;
     * "fall" ends the run as a fall; "breather" ends a breather; "leave" takes the exit; "siege" leaves 3 seconds.
     */
    public static boolean jump(ServerPlayer player, String what) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || s.level == null) return false;
        switch (what) {
            case "arrive" -> {
                if (s.phase == Phase.PULL) arrive(s);
                if (s.phase == Phase.ARRIVING) s.tick = ARRIVE_TICKS - 1;
            }
            case "clear" -> {
                if (s.phase == Phase.PULL) arrive(s);
                if (s.phase == Phase.ARRIVING) {
                    setPhase(s, Phase.FIGHT);
                    if (s.mode == Mode.RUSH) beginBout(s);
                    else beginFloor(s);
                }
                if (s.phase != Phase.FIGHT) return false;
                LivingEntity boss = boss(s);
                if (boss != null && s.bossDown < 0) {
                    boss.discard();
                    s.bossDown = s.tick - 200;
                    bossFallen(s);
                } else if (s.bossDown >= 0) {
                    bossFallen(s);
                } else {
                    crumble(s);
                    floorWon(s);
                }
            }
            case "climb" -> {
                if (s.phase != Phase.CLEARED) return false;
                climb(s);
            }
            case "wake" -> {
                LivingEntity boss = boss(s);
                if (boss == null || s.bossAwake) return false;
                wakeBoss(s, boss);
            }
            case "kill" -> {
                LivingEntity boss = boss(s);
                if (boss == null) return false;
                if (!s.bossAwake) wakeBoss(s, boss);
                        boss.setInvulnerable(false);
                boss.kill();
            }
            case "enrage" -> {
                LivingEntity boss = boss(s);
                if (boss == null) return false;
                if (!s.bossAwake) wakeBoss(s, boss);
                boss.setHealth(boss.getMaxHealth() * 0.45f);
            }
            case "moon" -> {
                LivingEntity boss = boss(s);
                if (!(boss instanceof CitadelLordEntity lord)) return false;
                if (!s.bossAwake) wakeBoss(s, boss);
                lord.setHealth(lord.getMaxHealth() * 0.28f);
            }
            case "fall" -> {
                if (s.phase == Phase.FALLEN || s.phase == Phase.LEAVING || s.phase == Phase.PULL) return false;
                fall(s);
            }
            case "breather" -> {
                if (s.phase != Phase.BREATHER) return false;
                s.tick = BREATHER_TICKS - 1;
            }
            case "leave" -> {
                if (s.phase == Phase.PULL || s.phase == Phase.LEAVING || s.phase == Phase.FALLEN) return false;
                leave(s);
            }
            case "siege" -> {
                if (s.kind() != TowerFloors.Kind.SIEGE || s.phase != Phase.FIGHT) return false;
                s.siegeLeft = 60;
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Commands and tests: ends a run at once and sends the player home, with no outcome. */
    public static void cancel(ServerPlayer player) {
        Session s = SESSIONS.remove(player.getUUID());
        if (s != null) {
            cleanup(s);
            freeze(player, false);
            send(player, CutscenePayload.stop());
        }
        returnHome(player);
        send(player, TowerPayload.none());
    }

    private TowerRun() {}
}
