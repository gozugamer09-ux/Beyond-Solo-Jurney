package com.beyondsolo.penalty;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.enemy.Affix;
import com.beyondsolo.enemy.Enemies;
import com.beyondsolo.enemy.EnemyFx;
import com.beyondsolo.enemy.EnemyRoster;
import com.beyondsolo.enemy.EnemyTraits;
import com.beyondsolo.enemy.Variant;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.network.CutscenePayload;
import com.beyondsolo.network.PenaltyPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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
 * The Penalty Zone: what the System does to a hunter who fails its daily quest.
 *
 * <p>A failed (or abandoned) daily leaves a <b>sentence</b> ({@link #sentence}). As soon as the player is free (not
 * in a gate, a trial, the awakening or an evaluation) a red warning window counts down; then the System pulls them
 * into the Wastes, a red desert under an eclipse in its own dimension ({@link PenaltyArena}). A camera sweeps in from
 * above, and the timer starts: they must <b>survive</b> until it runs out while creatures of their rank claw out of the
 * sand around them, ever more of them, with surges at a third and two thirds of the time and a champion for the last
 * stretch. The sand under their feet erupts now and then (red rings warn first; creatures caught in it are hit too).
 * The zone's edge pushes back.</p>
 *
 * <p>Surviving: every creature crumbles, a pillar of light lifts the player and they are sent home. A blow that would
 * kill them ends it instead (no death, nothing dropped): they are thrown home collapsed (full exhaustion) and weak.
 * Logging out does not escape it: they are sent home on the next login and the sentence starts over shortly after.</p>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class PenaltyZone {
    public enum Phase { WARNING, ARRIVAL, SURVIVE, SURVIVED, FAILED }

    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, BeyondSolo.id("penalty_zone"));
    public static final String FOE_TAG = "beyondsolo_penalty";

    static final int ARRIVAL_TICKS = 150;
    static final int CAMERA_TICKS = 100;
    static final int SURVIVED_TICKS = 100;
    static final int FAILED_TICKS = 70;
    static final int SPAWN_WARN_TICKS = 18;
    static final int ERUPT_WARN_TICKS = 30;
    static final int RESUME_GRACE_TICKS = 20 * 15;
    static final int SENTENCE_DELAY_TICKS = 20 * 5;
    static final int ACCENT = 0xFF3B30;
    static final int EMBER = 0xFF7A3D;

    private static final ResourceLocation FREEZE = BeyondSolo.id("penalty_freeze");

    record PendingSpawn(Vec3 at, int due, EntityType<?> type, EnemyTraits traits) {}

    record Eruption(Vec3 at, int due, float radius) {}

    static final class Session {
        final ServerPlayer player;
        final int slot;
        final BlockPos center;
        final Component reason;
        @Nullable ServerLevel level;
        Phase phase = Phase.WARNING;
        int tick;
        int warning;
        int total;
        int elapsed;
        int kills;
        final PlayerRank rank;
        EnemyRoster.Mix mix;
        final List<UUID> foes = new ArrayList<>();
        final List<PendingSpawn> spawns = new ArrayList<>();
        final List<Eruption> eruptions = new ArrayList<>();
        int nextEruption;
        boolean surge1, surge2, finalStretch;
        int edgeCooldown;

        Session(ServerPlayer player, int slot, Component reason) {
            this.player = player;
            this.slot = slot;
            this.center = PenaltyArena.center(slot);
            this.reason = reason;
            this.rank = SystemAPI.get(player).rank();
            this.mix = EnemyRoster.random(rank, player.getRandom());
        }

        Vec3 middle() {
            return Vec3.atBottomCenterOf(center);
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    // ================================================================ sentencing

    /**
     * Sentences a player to the Penalty Zone for failing {@code reason} (a quest's title). Returns false when the zone
     * is turned off or the player has no System, so the caller applies its ordinary penalty instead.
     */
    public static boolean sentence(ServerPlayer player, Component reason) {
        if (!PenaltyConfig.enabled() || !SystemAPI.hasSystem(player)) return false;
        PenaltyData data = data(player);
        if (SESSIONS.containsKey(player.getUUID())) return true; // already paying for something
        data.sentence(reason);
        // a few seconds for the quest's own "failed" moment, then the warning starts once the player is free
        data.setNotBefore(player.serverLevel().getGameTime() + SENTENCE_DELAY_TICKS);
        return true;
    }

    public static PenaltyData data(Player player) {
        return player.getData(PenaltyModule.PENALTY);
    }

    /** True while the player is being warned, is inside, or is being let out. */
    public static boolean busy(Player player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    /** True while the player is inside the zone (not just warned). */
    public static boolean inZone(Player player) {
        Session s = SESSIONS.get(player.getUUID());
        return s != null && s.phase != Phase.WARNING;
    }

    @Nullable
    public static Phase phase(Player player) {
        Session s = SESSIONS.get(player.getUUID());
        return s == null ? null : s.phase;
    }

    /** The level the zone is in. The game-test server has no datapack dimensions, so there it is the overworld. */
    @Nullable
    public static ServerLevel zoneLevel(MinecraftServer server) {
        ServerLevel level = server.getLevel(DIMENSION);
        if (level == null && server instanceof net.minecraft.gametest.framework.GameTestServer) return server.overworld();
        return level;
    }

    /** Somewhere the System can't take the player from right now. */
    static boolean occupied(ServerPlayer p) {
        if (!p.isAlive() || p.isSpectator()) return true;
        if (p.level().dimension().equals(DIMENSION)) return true;
        if (GateService.isDungeon(p.level()) && !(p.server instanceof net.minecraft.gametest.framework.GameTestServer)) return true;
        if (GateService.inTransit(p)) return true;
        if (com.beyondsolo.job.JobTrial.inTrial(p) || com.beyondsolo.monarch.ThroneTrial.inTrial(p)) return true;
        if (com.beyondsolo.redgate.RedGates.occupied(p)) return true;
        if (com.beyondsolo.isle.IsleRaids.occupied(p)) return true;
        if (com.beyondsolo.awakening.AwakeningService.inSequence(p)) return true;
        if (com.beyondsolo.tower.TowerAPI.inCitadel(p)) return true;
        return com.beyondsolo.evaluation.EvaluationManager.isBeingEvaluated(p);
    }

    /** Starts the warning (or, with {@code now}, pulls the player in at once). False if one is running. */
    public static boolean begin(ServerPlayer player, boolean now) {
        if (SESSIONS.containsKey(player.getUUID())) return false;
        PenaltyData data = data(player);
        Component reason = data.reason().getString().isEmpty() ? Component.translatable("penalty.beyondsolo.reason.unknown") : data.reason();
        if (!data.pending()) data.sentence(reason);
        Session s = new Session(player, freeSlot(), reason);
        SESSIONS.put(player.getUUID(), s);
        s.total = PenaltyConfig.surviveTicks();
        s.warning = PenaltyConfig.warningTicks();
        ServerLevel level = zoneLevel(player.server);
        if (level == null) {
            BeyondSolo.LOGGER.error("Penalty Zone: the zone's dimension is missing");
            SESSIONS.remove(player.getUUID());
            return false;
        }
        prepare(s, level);
        if (now) {
            enter(s);
        } else {
            send(s, PenaltyPayload.EV_NONE);
            sound(player.serverLevel(), player.position(), SoundEvents.BELL_BLOCK, 1f, 0.5f);
            sound(player.serverLevel(), player.position(), SoundEvents.WARDEN_HEARTBEAT, 1f, 0.7f);
        }
        return true;
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

    /** Lays out the Wastes (during the warning, so the ground is ready when the player lands). */
    private static void prepare(Session s, ServerLevel level) {
        if (s.level == level) return;
        s.level = level;
        clearArena(level, s.center);
        PenaltyArena.build(level, s.center);
    }

    /** Pulls the player in. */
    private static void enter(Session s) {
        ServerPlayer p = s.player;
        PenaltyData data = data(p);
        if (!data.inZone()) data.enter(p.level().dimension().location(), p.position(), p.getYRot());
        Vec3 start = PenaltyArena.start(s.center);
        double y = PenaltyArena.groundY(s.level, start.x, start.z);
        start = new Vec3(start.x, y, start.z);
        p.stopRiding();
        p.teleportTo(s.level, start.x, start.y, start.z, 180f, 0f);
        if (p.position().distanceToSqr(start) > 1) p.moveTo(start.x, start.y, start.z, 180f, 0f);
        p.fallDistance = 0;
        p.setHealth(p.getMaxHealth());
        p.clearFire();
        setPhase(s, Phase.ARRIVAL);
        freeze(p, true);
        Vec3 c = s.middle();
        // the camera falls from high over the basin, then settles behind the player looking at the pillar
        send(p, CutscenePayload.glide(c.add(-18, 30, 30), start.add(1.2, 2.8, 5.5), c.add(0, 0, 0), c.add(0, 3, -6), CAMERA_TICKS));
        sound(s.level, start, SoundEvents.SAND_BREAK, 1.5f, 0.5f);
        sound(s.level, c, SoundEvents.WITHER_SPAWN, 0.5f, 0.6f);
        s.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.RED_SAND.defaultBlockState()), start.x, start.y + 0.3, start.z, 80, 1.2, 0.2, 1.2, 0.2);
        send(s, PenaltyPayload.EV_NONE);
    }

    // ================================================================ ticking

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 == 0) startPending(server);
        if (SESSIONS.isEmpty()) return;
        Iterator<Session> it = SESSIONS.values().iterator();
        List<Session> ended = new ArrayList<>();
        while (it.hasNext()) {
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
                BeyondSolo.LOGGER.error("Penalty Zone failed for {}", s.player.getGameProfile().getName(), e);
                ended.add(s);
                if (s.phase != Phase.WARNING) {
                    freeze(s.player, false);
                    returnHome(s.player);
                }
                cleanup(s);
            }
        }
        for (Session s : ended) SESSIONS.remove(s.player.getUUID());
    }

    /** Once a second: players with a sentence who are free now get their warning. */
    private static void startPending(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) tryStart(p);
    }

    /** Starts the warning if the player owes a sentence and is free. Returns true if it started. */
    public static boolean tryStart(ServerPlayer p) {
        if (SESSIONS.containsKey(p.getUUID())) return false;
        PenaltyData data = data(p);
        if (!data.pending() || !PenaltyConfig.enabled() || !SystemAPI.hasSystem(p)) return false;
        if (p.serverLevel().getGameTime() < data.notBefore() || occupied(p)) return false;
        return begin(p, false);
    }

    /** One tick of a session. Returns true when it is over. */
    private static boolean step(Session s) {
        ServerPlayer p = s.player;
        switch (s.phase) {
            case WARNING -> {
                if (occupied(p)) {
                    // walked into a gate or a trial mid-warning: the sentence waits until they are out
                    send(p, PenaltyPayload.none());
                    return true;
                }
                warningFx(s);
                if (s.tick >= s.warning) enter(s);
            }
            case ARRIVAL -> {
                if (s.tick == CAMERA_TICKS - 10) send(s, PenaltyPayload.EV_NONE);
                if (s.tick >= ARRIVAL_TICKS) {
                    send(p, CutscenePayload.stop());
                    freeze(p, false);
                    setPhase(s, Phase.SURVIVE);
                    s.nextEruption = 20 * 22;
                    sound(s.level, p.position(), SoundEvents.ENDER_DRAGON_GROWL, 0.6f, 0.5f);
                    send(s, PenaltyPayload.EV_NONE);
                }
            }
            case SURVIVE -> {
                s.elapsed++;
                survive(s);
                if (s.elapsed >= s.total) survived(s);
            }
            case SURVIVED -> {
                survivedFx(s);
                if (s.tick >= SURVIVED_TICKS) {
                    release(s, true);
                    return true;
                }
            }
            case FAILED -> {
                if (s.tick >= FAILED_TICKS) {
                    release(s, false);
                    return true;
                }
            }
        }
        return false;
    }

    // ================================================================ the warning

    private static void warningFx(Session s) {
        ServerPlayer p = s.player;
        ServerLevel here = p.serverLevel();
        Vec3 at = p.position();
        int left = s.warning - s.tick;
        if (s.tick % 20 == 0) {
            float k = s.tick / (float) s.warning;
            sound(here, at, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 1f, 0.5f + k * 0.3f);
            sound(here, at, SoundEvents.WARDEN_HEARTBEAT, 0.8f + k, 0.8f + k * 0.4f);
            send(s, PenaltyPayload.EV_NONE);
        }
        // red motes gather round the player, faster and tighter as the count runs out
        double k = s.tick / (double) s.warning;
        double r = 2.4 - 1.6 * k;
        int n = 2 + (int) (k * 4);
        for (int i = 0; i < n; i++) {
            double a = s.tick * (0.18 + 0.25 * k) + i * Math.PI * 2 / n;
            here.sendParticles(dust(ACCENT, 1.0f + (float) k), at.x + Math.cos(a) * r, at.y + 0.1 + (s.tick % 40) / 20.0, at.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        if (left == 60) {
            // the ground under them gives: a red ring fills while the System takes hold
            EnemyFx.telegraphCircle(here, at, 2.2f, 60, ACCENT);
            sound(here, at, SoundEvents.BEACON_DEACTIVATE, 1f, 0.5f);
        }
        if (left <= 60 && s.tick % 3 == 0) {
            here.sendParticles(ParticleTypes.CRIMSON_SPORE, at.x, at.y + 1, at.z, 6, 0.8, 0.9, 0.8, 0.02);
            here.sendParticles(dust(0x5A0A0A, 1.4f), at.x, at.y + 0.1, at.z, 4, 1.2, 0.05, 1.2, 0);
        }
        if (left == 4) {
            here.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1, at.z, 120, 0.4, 1, 0.4, 0.6);
            sound(here, at, SoundEvents.PORTAL_TRAVEL, 0.35f, 0.6f);
            EnemyFx.pillar(here, at, ACCENT, 1.4f, 9f);
        }
    }

    // ================================================================ surviving

    private static void survive(Session s) {
        ServerPlayer p = s.player;
        ServerLevel level = s.level;
        float f = s.elapsed / (float) s.total;
        int remaining = s.total - s.elapsed;

        // spawns and eruptions that were warned of land now
        for (Iterator<PendingSpawn> it = s.spawns.iterator(); it.hasNext(); ) {
            PendingSpawn ps = it.next();
            if (s.elapsed >= ps.due) {
                it.remove();
                rise(s, ps);
            }
        }
        for (Iterator<Eruption> it = s.eruptions.iterator(); it.hasNext(); ) {
            Eruption e = it.next();
            if (s.elapsed >= e.due) {
                it.remove();
                erupt(s, e);
            }
        }

        // the sands send more the longer it lasts
        if (s.elapsed % 30 == 0) {
            int alive = living(s).size() + s.spawns.size();
            int want = 3 + Math.round(f * 5) + s.rank.tier() / 2;
            int missing = want - alive;
            if (missing > 0) for (int i = 0; i < Math.min(2, missing); i++) queueSpawn(s, false, null, 8, 14);
        }
        if (!s.surge1 && f >= 1 / 3f) {
            s.surge1 = true;
            surge(s, 4);
        }
        if (!s.surge2 && f >= 2 / 3f) {
            s.surge2 = true;
            surge(s, 6);
        }
        if (!s.finalStretch && remaining <= 30 * 20 && s.total >= 60 * 20) {
            s.finalStretch = true;
            Vec3 ahead = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(10));
            queueSpawn(s, true, Variant.CHAMPION, 0, 0, inside(s, ahead, 4));
            send(s, PenaltyPayload.EV_FINAL);
            sound(level, p.position(), SoundEvents.RAVAGER_ROAR, 1f, 0.6f);
        }

        // the ground erupts under the player now and then
        if (s.elapsed >= s.nextEruption && remaining > 40) {
            eruptionsAround(s);
            int gap = Math.round(Mth.lerp(f, 220, 130));
            s.nextEruption = s.elapsed + gap + level.random.nextInt(60);
        }

        if (s.elapsed % 20 == 0) {
            keepFoes(s);
            send(s, PenaltyPayload.EV_NONE);
        }
        edge(s);
        // the final seconds tick out loud
        if (remaining <= 200 && remaining % 20 == 0) sound(level, p.position(), SoundEvents.NOTE_BLOCK_HAT.value(), 0.9f, 1.6f);
    }

    /** "The sands stir": a ring of creatures around the player, with a leader. */
    private static void surge(Session s, int count) {
        ServerPlayer p = s.player;
        double base = s.level.random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < count; i++) {
            double a = base + Math.PI * 2 * i / count;
            Vec3 at = inside(s, p.position().add(Math.cos(a) * 9, 0, Math.sin(a) * 9), 3);
            queueSpawn(s, i == 0, null, 0, 0, at);
        }
        send(s, PenaltyPayload.EV_SURGE);
        sound(s.level, p.position(), SoundEvents.EVOKER_PREPARE_SUMMON, 1f, 0.5f);
        sound(s.level, p.position(), SoundEvents.SAND_BREAK, 1.5f, 0.4f);
    }

    private static void queueSpawn(Session s, boolean leader, @Nullable Variant forced, double near, double far) {
        ServerPlayer p = s.player;
        RandomSource r = s.level.random;
        double a = r.nextDouble() * Math.PI * 2;
        double d = near + r.nextDouble() * (far - near);
        queueSpawn(s, leader, forced, near, far, inside(s, p.position().add(Math.cos(a) * d, 0, Math.sin(a) * d), 3));
    }

    private static void queueSpawn(Session s, boolean leader, @Nullable Variant forced, double near, double far, Vec3 spot) {
        RandomSource r = s.level.random;
        Vec3 at = new Vec3(spot.x, PenaltyArena.groundY(s.level, spot.x, spot.z), spot.z);
        EntityType<?> type = s.mix.pick(r);
        EnemyTraits traits = Enemies.roll(s.rank, s.mix, type, r, leader, s.elapsed > s.total / 2);
        if (forced != null) {
            traits = new EnemyTraits(s.mix.affinityFor(type, r, true), forced, Affix.mask(Affix.roll(forced == Variant.CHAMPION ? 2 : 1, r)));
        }
        s.spawns.add(new PendingSpawn(at, s.elapsed + SPAWN_WARN_TICKS, type, traits));
        int color = traits.variant().special() ? 0xFFB347 : EMBER;
        EnemyFx.telegraphCircle(s.level, at, traits.variant() == Variant.CHAMPION ? 2.4f : 1.3f, SPAWN_WARN_TICKS, color);
        s.level.sendParticles(sandDust(), at.x, at.y + 0.1, at.z, 12, 0.5, 0.05, 0.5, 0.05);
    }

    /** A creature claws its way out of the sand. */
    private static void rise(Session s, PendingSpawn ps) {
        ServerLevel level = s.level;
        Mob mob = Enemies.spawn(level, s.rank, ps.type, BlockPos.containing(ps.at), ps.traits, -1);
        level.sendParticles(sandDust(), ps.at.x, ps.at.y + 0.4, ps.at.z, 40, 0.4, 0.4, 0.4, 0.25);
        sound(level, ps.at, SoundEvents.SAND_BREAK, 1.2f, 0.6f);
        sound(level, ps.at, SoundEvents.ROOTED_DIRT_BREAK, 1f, 0.5f);
        if (mob == null) return;
        // as strong as creatures made for the hunter's level (their rank alone would be far off for a high rank at a low level)
        Enemies.strengthForLevel(mob, com.beyondsolo.enemy.HiddenLevel.hunterLevel(SystemAPI.get(s.player)));
        mob.setHealth(mob.getMaxHealth());
        mob.addTag(FOE_TAG);
        mob.setPersistenceRequired();
        AttributeInstance follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (follow != null) follow.setBaseValue(64);
        // undead stand their ground under the eclipse
        mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
        mob.setTarget(s.player);
        s.foes.add(mob.getUUID());
        if (ps.traits.variant() == Variant.CHAMPION) {
            EnemyFx.pillar(level, ps.at, 0xFFB347, 1.6f, 7f);
            sound(level, ps.at, SoundEvents.WITHER_SPAWN, 0.5f, 1.2f);
        }
    }

    /** Red rings under and around the player; a moment later the sand bursts up out of them. */
    private static void eruptionsAround(Session s) {
        ServerPlayer p = s.player;
        RandomSource r = s.level.random;
        Vec3 here = p.position();
        Vec3 move = p.getDeltaMovement().multiply(1, 0, 1);
        List<Vec3> spots = new ArrayList<>();
        spots.add(here);
        spots.add(here.add(move.scale(18)).add((r.nextDouble() - 0.5) * 4, 0, (r.nextDouble() - 0.5) * 4));
        double a = r.nextDouble() * Math.PI * 2;
        spots.add(here.add(Math.cos(a) * 4.5, 0, Math.sin(a) * 4.5));
        if (s.elapsed > s.total / 2) spots.add(here.add(Math.cos(a + Math.PI) * 5, 0, Math.sin(a + Math.PI) * 5));
        for (Vec3 spot : spots) {
            Vec3 in = inside(s, spot, 2);
            Vec3 at = new Vec3(in.x, PenaltyArena.groundY(s.level, in.x, in.z), in.z);
            float radius = 2.4f;
            EnemyFx.telegraphCircle(s.level, at, radius, ERUPT_WARN_TICKS, ACCENT);
            s.eruptions.add(new Eruption(at, s.elapsed + ERUPT_WARN_TICKS, radius));
        }
        sound(s.level, here, SoundEvents.SAND_STEP, 1.5f, 0.4f);
        send(s, PenaltyPayload.EV_ERUPT);
    }

    private static void erupt(Session s, Eruption e) {
        ServerLevel level = s.level;
        level.sendParticles(sandDust(), e.at.x, e.at.y + 0.6, e.at.z, 60, e.radius * 0.35, 0.8, e.radius * 0.35, 0.35);
        level.sendParticles(ParticleTypes.LAVA, e.at.x, e.at.y + 0.3, e.at.z, 6, e.radius * 0.3, 0.1, e.radius * 0.3, 0);
        EnemyFx.burst(level, e.at.add(0, 0.6, 0), EMBER, e.radius * 0.9f);
        sound(level, e.at, SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 0.7f);
        sound(level, e.at, SoundEvents.SAND_BREAK, 1.4f, 0.5f);
        float damage = 4 + 2.5f * s.rank.tier();
        AABB box = new AABB(e.at, e.at).inflate(e.radius, 2.5, e.radius);
        for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, box, l -> l.isAlive()
                && l.position().distanceToSqr(e.at.x, l.getY(), e.at.z) <= e.radius * e.radius)) {
            // creatures caught in it are hit harder: worth luring them in (grown like they are)
            float amount = hit instanceof Player ? damage
                    : (float) (damage * 2 * com.beyondsolo.system.Balance.healthAt(com.beyondsolo.enemy.HiddenLevel.hunterLevel(SystemAPI.get(s.player))));
            hit.hurt(level.damageSources().magic(), amount);
            hit.push(0, 0.75, 0);
            hit.hurtMarked = true;
        }
    }

    /** The edge of the zone: pushed back towards the pillar, singed. */
    private static void edge(Session s) {
        ServerPlayer p = s.player;
        if (s.edgeCooldown > 0) s.edgeCooldown--;
        Vec3 c = s.middle();
        Vec3 off = p.position().subtract(c).multiply(1, 0, 1);
        double d = off.length();
        if (d > PenaltyArena.RADIUS + 12 || p.level() != s.level) {
            Vec3 start = PenaltyArena.start(s.center);
            p.teleportTo(s.level, start.x, PenaltyArena.groundY(s.level, start.x, start.z), start.z, 180f, 0f);
            return;
        }
        if (d <= PenaltyArena.RADIUS - 0.5) return;
        Vec3 back = off.normalize().scale(-0.9);
        p.setDeltaMovement(back.x, 0.35, back.z);
        p.hurtMarked = true;
        if (s.edgeCooldown == 0) {
            s.edgeCooldown = 20;
            p.hurt(s.level.damageSources().magic(), 2f);
            sound(s.level, p.position(), SoundEvents.FIRECHARGE_USE, 0.8f, 0.5f);
            send(s, PenaltyPayload.EV_EDGE);
        }
    }

    private static void keepFoes(Session s) {
        Vec3 c = s.middle();
        for (LivingEntity e : living(s)) {
            if (e.position().distanceTo(c) > PenaltyArena.RADIUS + 3) {
                Vec3 at = inside(s, e.position(), 4);
                e.teleportTo(at.x, PenaltyArena.groundY(s.level, at.x, at.z), at.z);
            }
            if (e instanceof Mob m && m.getTarget() != s.player) m.setTarget(s.player);
        }
    }

    // ================================================================ the end

    /** The timer ran out: the zone lets go. */
    private static void survived(Session s) {
        ServerPlayer p = s.player;
        setPhase(s, Phase.SURVIVED);
        s.spawns.clear();
        s.eruptions.clear();
        for (LivingEntity e : living(s)) {
            s.level.sendParticles(sandDust(), e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 30, 0.3, e.getBbHeight() / 3, 0.3, 0.05);
            s.level.sendParticles(ParticleTypes.ASH, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
            e.discard();
        }
        s.foes.clear();
        freeze(p, true);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, SURVIVED_TICKS + 40, 4, false, false, false));
        Vec3 c = s.middle();
        EnemyFx.pillar(s.level, c, 0xFFE2B8, 2.2f, 30f);
        EnemyFx.burst(s.level, c.add(0, 8, 0), 0xFFE2B8, 4f);
        sound(s.level, c, SoundEvents.BEACON_POWER_SELECT, 1.2f, 0.7f);
        sound(s.level, p.position(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 0.9f);
        send(s, PenaltyPayload.EV_NONE);
    }

    /** The light takes the player up. */
    private static void survivedFx(Session s) {
        ServerPlayer p = s.player;
        if (s.tick == 30) {
            p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, SURVIVED_TICKS, 0, false, false, false));
            EnemyFx.pillar(s.level, p.position(), 0xFFFFFF, 1.3f, 20f);
            sound(s.level, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 0.8f);
        }
        if (s.tick > 30 && s.tick % 2 == 0) {
            s.level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() - 0.2, p.getZ(), 2, 0.4, 0.2, 0.4, 0.02);
        }
    }

    /** A blow that would have killed the player: they fall, and are thrown out. */
    private static void fail(Session s) {
        ServerPlayer p = s.player;
        setPhase(s, Phase.FAILED);
        s.spawns.clear();
        s.eruptions.clear();
        send(p, CutscenePayload.stop());
        freeze(p, true);
        p.setHealth(Math.max(1f, p.getMaxHealth() * 0.2f));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, FAILED_TICKS + 40, 4, false, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, FAILED_TICKS, 0, false, false, false));
        sound(s.level, p.position(), SoundEvents.WITHER_DEATH, 0.4f, 0.6f);
        send(s, PenaltyPayload.EV_NONE);
    }

    /** Sends the player home after the end card. */
    private static void release(Session s, boolean survived) {
        ServerPlayer p = s.player;
        cleanup(s);
        freeze(p, false);
        p.removeEffect(MobEffects.LEVITATION);
        p.removeEffect(MobEffects.BLINDNESS);
        returnHome(p);
        PenaltyData data = data(p);
        data.clearSentence();
        if (survived) {
            data.addServed();
            p.setHealth(p.getMaxHealth());
            Notifications.send(p, new SystemNotification(SystemNotification.Kind.INFO,
                    Component.translatable("notify.beyondsolo.penalty_served.title"),
                    Component.translatable("notify.beyondsolo.penalty_served.body", s.kills), 200));
        } else {
            data.addFailed();
            p.removeAllEffects();
            // straight to a collapse, whatever their resistance to fatigue
            if (SystemAPI.isAwakened(p) && !SystemAPI.get(p).isCollapsed()) com.beyondsolo.system.Exhaustion.collapse(p);
            int ticks = PenaltyConfig.failDebuffTicks();
            if (ticks > 0) {
                p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1));
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 1));
            }
            Notifications.send(p, new SystemNotification(SystemNotification.Kind.ALERT,
                    Component.translatable("notify.beyondsolo.penalty_failed.title"),
                    Component.translatable("notify.beyondsolo.penalty_failed.body"), 200));
        }
        send(p, PenaltyPayload.none());
        NeoForge.EVENT_BUS.post(new PenaltyEvent.Ended(p, survived, s.kills));
    }

    /** The player logged out: the sentence stands and starts over after they come back. */
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
        s.foes.clear();
        s.spawns.clear();
        s.eruptions.clear();
    }

    /** Sends a player back to where they were pulled from. */
    static void returnHome(ServerPlayer p) {
        PenaltyData data = data(p);
        if (!data.inZone()) return;
        MinecraftServer server = p.server;
        ResourceLocation dim = data.returnDimension();
        ServerLevel home = dim == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
        if (home == null) home = server.overworld();
        Vec3 at = data.returnPos();
        p.teleportTo(home, at.x, at.y, at.z, data.returnYaw(), 0f);
        if (p.level() == home && p.position().distanceToSqr(at) > 1) p.moveTo(at.x, at.y, at.z, data.returnYaw(), 0f);
        p.fallDistance = 0;
        data.leave();
        home.sendParticles(dust(ACCENT, 1.2f), at.x, at.y + 1, at.z, 40, 0.4, 0.9, 0.4, 0);
    }

    private static void clearArena(ServerLevel level, BlockPos c) {
        AABB box = new AABB(c).inflate(PenaltyArena.OUTER + 4, 24, PenaltyArena.OUTER + 4);
        for (Entity e : level.getEntitiesOfClass(Entity.class, box, e -> !(e instanceof Player))) e.discard();
    }

    // ================================================================ events

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.getTags().contains(FOE_TAG)) {
            UUID id = dead.getUUID();
            for (Session s : SESSIONS.values()) {
                if (s.foes.remove(id)) {
                    s.kills++;
                    send(s, PenaltyPayload.EV_NONE);
                }
            }
            return;
        }
        if (!(dead instanceof ServerPlayer p)) return;
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || s.phase == Phase.WARNING) return;
        event.setCanceled(true);
        p.setHealth(Math.max(1f, p.getMaxHealth() * 0.2f));
        if (s.phase == Phase.SURVIVE || s.phase == Phase.ARRIVAL) fail(s);
    }

    /** A hit that would kill the player ends the sentence instead: no real death, and no totem used up. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onFatalDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || s.phase == Phase.WARNING || s.level == null) return;
        if (event.getNewDamage() < p.getHealth()) return;
        event.setNewDamage(0);
        if (s.phase != Phase.SURVIVE) return;
        p.server.execute(() -> {
            if (SESSIONS.get(p.getUUID()) == s && s.phase == Phase.SURVIVE) fail(s);
        });
    }

    @SubscribeEvent
    static void onHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Session s = SESSIONS.get(p.getUUID());
        if (s == null) return;
        // no harm while the camera has the player, or once it is over
        if (s.phase == Phase.ARRIVAL || s.phase == Phase.SURVIVED || s.phase == Phase.FAILED) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        PenaltyData data = data(p);
        if (data.inZone() && !SESSIONS.containsKey(p.getUUID())) {
            freeze(p, false);
            returnHome(p);
        }
        if (data.pending()) {
            data.setNotBefore(p.serverLevel().getGameTime() + RESUME_GRACE_TICKS);
            Notifications.send(p, new SystemNotification(SystemNotification.Kind.ALERT,
                    Component.translatable("notify.beyondsolo.penalty_owed.title"),
                    Component.translatable("notify.beyondsolo.penalty_owed.body"), 160));
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        SESSIONS.clear();
    }

    // ================================================================ helpers

    private static List<LivingEntity> living(Session s) {
        List<LivingEntity> out = new ArrayList<>();
        if (s.level == null) return out;
        for (Iterator<UUID> it = s.foes.iterator(); it.hasNext(); ) {
            Entity e = s.level.getEntity(it.next());
            if (e instanceof LivingEntity l && l.isAlive()) out.add(l);
            else if (e == null && s.level.isLoaded(s.center)) it.remove();
        }
        return out;
    }

    /** {@code at}, pulled inside the zone's edge by {@code margin} blocks. */
    private static Vec3 inside(Session s, Vec3 at, double margin) {
        Vec3 c = s.middle();
        Vec3 off = at.subtract(c).multiply(1, 0, 1);
        double max = PenaltyArena.RADIUS - margin;
        if (off.length() > max) off = off.normalize().scale(max);
        // not on the pillar
        if (off.length() < 2.5) off = off.lengthSqr() < 1e-4 ? new Vec3(0, 0, 3) : off.normalize().scale(3);
        return c.add(off.x, 0, off.z);
    }

    private static void setPhase(Session s, Phase phase) {
        s.phase = phase;
        s.tick = 0;
    }

    /** Holds the player still while the camera has them. */
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

    private static void send(Session s, int event) {
        int phase = switch (s.phase) {
            case WARNING -> PenaltyPayload.WARNING;
            case ARRIVAL -> PenaltyPayload.ARRIVAL;
            case SURVIVE -> PenaltyPayload.SURVIVE;
            case SURVIVED -> PenaltyPayload.SURVIVED;
            case FAILED -> PenaltyPayload.FAILED;
        };
        int length = switch (s.phase) {
            case WARNING -> s.warning;
            case ARRIVAL -> ARRIVAL_TICKS;
            case SURVIVE -> s.total;
            case SURVIVED -> SURVIVED_TICKS;
            case FAILED -> FAILED_TICKS;
        };
        int tick = s.phase == Phase.SURVIVE ? s.elapsed : s.tick;
        PacketDistributor.sendToPlayer(s.player, new PenaltyPayload(phase, tick, length, Math.max(0, s.total - s.elapsed), s.kills, event,
                s.center.getX(), s.center.getZ(), PenaltyArena.RADIUS, s.reason));
    }

    private static void send(ServerPlayer p, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(p, payload);
    }

    private static BlockParticleOption sandDust() {
        return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.RED_SAND.defaultBlockState());
    }

    private static DustParticleOptions dust(int rgb, float scale) {
        return new DustParticleOptions(new org.joml.Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f), Math.min(4f, scale));
    }

    private static void sound(@Nullable ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        if (level != null) level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    // ================================================================ tests and commands

    /** Tests: the living creatures of a player's sentence. */
    public static List<LivingEntity> foes(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        return s == null ? List.of() : living(s);
    }

    /** Tests and commands: ticks left to survive, or -1. */
    public static int remaining(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        return s == null ? -1 : s.total - s.elapsed;
    }

    @Nullable
    public static BlockPos center(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        return s == null ? null : s.center;
    }

    /** Commands: skip ahead. "arrive" ends the warning, "time &lt;seconds&gt;" sets the time left, "survive", "fail",
     * "surge", "erupt", "champion". */
    public static boolean jump(ServerPlayer player, String what, int seconds) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || s.level == null) return false;
        switch (what) {
            case "arrive" -> {
                if (s.phase == Phase.WARNING) enter(s);
            }
            case "fight" -> {
                if (s.phase == Phase.WARNING) enter(s);
                if (s.phase == Phase.ARRIVAL) s.tick = ARRIVAL_TICKS - 1;
            }
            case "time" -> {
                if (s.phase != Phase.SURVIVE) return false;
                s.elapsed = Math.max(0, s.total - seconds * 20);
                send(s, PenaltyPayload.EV_NONE);
            }
            case "survive" -> {
                if (s.phase != Phase.SURVIVE) return false;
                s.elapsed = s.total - 1;
            }
            case "fail" -> {
                if (s.phase != Phase.SURVIVE) return false;
                fail(s);
            }
            case "surge" -> {
                if (s.phase != Phase.SURVIVE) return false;
                surge(s, Math.max(3, seconds));
            }
            case "erupt" -> {
                if (s.phase != Phase.SURVIVE) return false;
                eruptionsAround(s);
            }
            case "champion" -> {
                if (s.phase != Phase.SURVIVE) return false;
                Vec3 ahead = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(8));
                queueSpawn(s, true, Variant.CHAMPION, 0, 0, inside(s, ahead, 4));
                send(s, PenaltyPayload.EV_FINAL);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Commands: sentences the player for {@code reason} and pulls them in at once. False if a sentence is already running. */
    public static boolean sendNow(ServerPlayer player, Component reason) {
        if (SESSIONS.containsKey(player.getUUID())) return false;
        data(player).sentence(reason);
        return begin(player, true);
    }

    /** Commands: lets the player off: out of the zone if they are in it, and no sentence left waiting for them. */
    public static void pardon(ServerPlayer player) {
        cancel(player);
        data(player).clearSentence();
    }

    /** Commands: ends any sentence and forgets everything the zone knows about the player (served, failed, pending). */
    public static void reset(ServerPlayer player) {
        cancel(player);
        data(player).reset();
    }

    /** Commands and tests: ends a running sentence at once and sends the player home, without any outcome. */
    public static void cancel(ServerPlayer player) {
        Session s = SESSIONS.remove(player.getUUID());
        if (s != null) {
            cleanup(s);
            freeze(player, false);
            send(player, CutscenePayload.stop());
        }
        returnHome(player);
        send(player, PenaltyPayload.none());
    }

    private PenaltyZone() {}
}
