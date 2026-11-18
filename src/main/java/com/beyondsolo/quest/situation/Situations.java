package com.beyondsolo.quest.situation;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestData;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestEvent;
import com.beyondsolo.quest.QuestObjective;
import com.beyondsolo.quest.trigger.QuestTriggers;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The System reading the situation. Every awakened player is watched: what hunts them, how hurt they are, what they
 * kill and how, who is in trouble around them, where they stand. When a {@link Situation} is recognised the System
 * answers with a quest, keeps watching while it runs, rewrites it when the situation turns ({@link #branch}), and
 * decides what the ending leads to.
 *
 * <p>Rules that keep it from turning into noise: one emergency at a time (the same rule as every emergency), one
 * other situation at a time, at least {@link SituationConfig#gapTicks()} between two that are not emergencies, and each
 * situation has its own cooldown. Nothing new starts while the player is somewhere the System leaves them be (see
 * {@link QuestTriggers#quiet}).</p>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class Situations {
    /** Cooldown key: the earliest a situation that is not an emergency may start again. */
    public static final String GAP_KEY = BeyondSolo.id("situation_gap").toString();
    /** Persistent-data flag on a creature (or person) some running situation is about, so its death is looked at. */
    public static final String TARGET_MARK = "beyondsolo_quest_target";

    private static final List<Situation> ALL = new ArrayList<>();
    private static final Map<UUID, Watch> WATCH = new HashMap<>();

    // in the order they are looked for: danger first
    public static final OverwhelmingFoe FOE = register(new OverwhelmingFoe());
    public static final LastStand LAST_STAND = register(new LastStand());
    public static final PackLeader PACK_LEADER = register(new PackLeader());
    public static final ProtectVillager PROTECT = register(new ProtectVillager());
    public static final DefendVillage DEFEND = register(new DefendVillage());
    public static final BeyondRank BEYOND_RANK = register(new BeyondRank());
    public static final Streaks.Untouchable UNTOUCHABLE = register(new Streaks.Untouchable());
    public static final Streaks.Frenzy FRENZY = register(new Streaks.Frenzy());
    public static final CatchBreath CATCH_BREATH = register(new CatchBreath());
    public static final Cure CURE = register(new Cure());
    public static final Places.SilentCity SILENT_CITY = register(new Places.SilentCity());
    public static final Places.Outpost OUTPOST = register(new Places.Outpost());
    public static final Places.Ruins RUINS = register(new Places.Ruins());
    public static final Revenge REVENGE = register(new Revenge());

    public static <T extends Situation> T register(T situation) {
        ALL.removeIf(s -> s.id().equals(situation.id()));
        ALL.add(situation);
        return situation;
    }

    public static List<Situation> all() {
        return List.copyOf(ALL);
    }

    @Nullable
    public static Situation byId(String id) {
        for (Situation s : ALL) if (s.id().equals(id) || s.path().equals(id)) return s;
        return null;
    }

    public static Watch watch(Player player) {
        return WATCH.computeIfAbsent(player.getUUID(), k -> new Watch());
    }

    /** Marks a creature a running situation is about: its death will be looked at. */
    public static void markTarget(Entity e) {
        e.getPersistentData().putBoolean(TARGET_MARK, true);
    }

    // ------------------------------------------------------------------ ticking

    /** Once a second for every awakened player (from {@link QuestEngine#tick}). */
    public static void tick(ServerPlayer player, QuestData data) {
        if (!SituationConfig.enabled()) return;
        long now = QuestEngine.gameTime(player);
        // what is running keeps watching
        for (Quest q : List.copyOf(data.active())) {
            Run run = Run.of(q);
            if (run == null || q.status() != Quest.Status.ACTIVE || !data.all().contains(q)) continue;
            Situation s = byId(run.situation());
            if (s != null) s.update(player, q, run);
        }
        // what was left for later comes back
        FollowUps.tick(player, now);
        // where the player is (which gate), and new situations
        Watch watch = watch(player);
        BeyondRank.track(player, watch);
        if (QuestTriggers.quiet(player)) return;
        for (Situation s : ALL) {
            if (now < data.cooldownUntil(s.id())) continue;
            Quest q = s.detect(player, watch);
            if (q != null && begin(player, s, q)) return;
        }
    }

    /** Starts (or offers) a situation's first quest now, if nothing stands in its way. For situations started by events. */
    public static boolean tryStart(ServerPlayer player, Situation s, Supplier<Quest> make) {
        if (!SituationConfig.enabled() || !SystemAPI.isAwakened(player) || !QuestEngine.data(player).started()) return false;
        if (QuestTriggers.quiet(player)) return false;
        QuestData data = QuestEngine.data(player);
        if (QuestEngine.gameTime(player) < data.cooldownUntil(s.id())) return false;
        Quest q = make.get();
        return q != null && begin(player, s, q);
    }

    /** Whether a quest like this one could start now: one emergency at a time, one calmer situation at a time, the gap. */
    public static boolean room(ServerPlayer player, boolean forced) {
        QuestData data = QuestEngine.data(player);
        if (forced) return !QuestTriggers.inEmergency(data);
        if (QuestEngine.gameTime(player) < data.cooldownUntil(GAP_KEY)) return false;
        for (Quest q : data.all()) {
            if (!q.isForced() && Run.of(q) != null && q.category() != QuestCategory.SECRET) return false;
        }
        return true;
    }

    static boolean begin(ServerPlayer player, Situation s, Quest q) {
        QuestData data = QuestEngine.data(player);
        if (!room(player, q.isForced()) || data.has(q.template())) return false;
        data.setCooldown(s.id(), QuestEngine.gameTime(player) + s.cooldownTicks());
        if (s.offered() && !q.isForced()) QuestEngine.offer(player, q, QuestEngine.OFFER_TICKS);
        else QuestEngine.start(player, q);
        return true;
    }

    /**
     * The situation turned: the System rewrites {@code from} into {@code to} (built with {@link Run#next}), and the
     * player sees it happen. With {@code pay}, what {@code from} promised is paid first.
     */
    public static void branch(ServerPlayer player, Quest from, Quest to, boolean pay) {
        QuestEngine.branch(player, from, to, pay);
    }

    /** The running quest of a situation for this player, or null. */
    @Nullable
    public static Quest running(ServerPlayer player, Situation s) {
        for (Quest q : QuestEngine.data(player).active()) {
            Run run = Run.of(q);
            if (run != null && run.situation().equals(s.id()) && q.status() == Quest.Status.ACTIVE) return q;
        }
        return null;
    }

    /** Index of the first objective of a quest with this key, or -1. */
    public static int objective(Quest q, String key) {
        for (int i = 0; i < q.objectives().size(); i++) if (q.objectives().get(i).target().equals(key)) return i;
        return -1;
    }

    /** Sets an objective by key, if the quest has it. */
    public static void set(ServerPlayer player, Quest q, String key, int value) {
        int i = objective(q, key);
        if (i >= 0) QuestEngine.setProgress(player, q, i, value);
    }

    /** Adds to an objective by key, if the quest has it. */
    public static void add(ServerPlayer player, Quest q, String key, int amount) {
        int i = objective(q, key);
        if (i >= 0) QuestEngine.setProgress(player, q, i, q.objectives().get(i).progress() + amount);
    }

    private static void emptyHealth(ServerPlayer player, Quest q) {
        for (int i = 0; i < q.objectives().size(); i++) {
            QuestObjective o = q.objectives().get(i);
            if (o.display() == QuestObjective.Display.HEALTH && o.progress() > 0) QuestEngine.setProgress(player, q, i, 0);
        }
    }

    /** The quest is still the player's and still running (it hasn't ended or been rewritten). */
    public static boolean isRunning(ServerPlayer player, Quest q) {
        return q.status() == Quest.Status.ACTIVE && QuestEngine.data(player).all().contains(q);
    }

    /** Clears a player's situations: running ones, cooldowns aside (for /system situation clear and tests). */
    public static void clear(ServerPlayer player) {
        QuestData data = QuestEngine.data(player);
        data.all().removeIf(q -> Run.of(q) != null);
        FollowUps.data(player).clear();
        WATCH.remove(player.getUUID());
        QuestEngine.sync(player);
    }

    // ------------------------------------------------------------------ events

    private static boolean watched(ServerPlayer p) {
        return SituationConfig.enabled() && SystemAPI.isAwakened(p) && QuestEngine.data(p).started();
    }

    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || event.getNewDamage() <= 0) return;
        DamageSource source = event.getSource();
        if (victim instanceof ServerPlayer p && watched(p)) {
            long now = QuestEngine.gameTime(p);
            Watch w = watch(p);
            w.lastHurt = now;
            // a hit is a blow from someone (a creature, an arrow, a spell): a fall or a fire is not
            if (source.getEntity() != null && source.getEntity() != p) {
                w.cleanKills = 0;
                if (w.gate != null) w.hurtInGate = true;
            }
            if (source.getEntity() instanceof LivingEntity && source.getEntity() != p) w.lastHurtByCreature = now;
            float share = p.getHealth() / Math.max(1f, p.getMaxHealth());
            for (Quest q : QuestEngine.data(p).active()) {
                Run run = Run.of(q);
                if (run != null) run.noteLow(share);
            }
            for (Situation s : ALL) s.playerHurt(p, source, event.getNewDamage(), w);
        }
        ServerPlayer attacker = Foes.playerBehind(source.getEntity());
        if (attacker != null && victim != attacker && watched(attacker)) {
            Watch w = watch(attacker);
            w.lastHit = QuestEngine.gameTime(attacker);
            for (Situation s : ALL) s.playerHit(attacker, victim, event.getNewDamage(), w);
        }
        if (victim instanceof AbstractVillager v && source.getEntity() instanceof LivingEntity hurter && hurter instanceof Enemy) {
            ProtectVillager.alarm(v, hurter);
        }
    }

    /** Runs before the quest engine counts the kill, so a situation can change its quest first. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide) return;
        if (dead instanceof ServerPlayer p) {
            if (!watched(p)) return;
            Revenge.noteDeath(p, event.getSource());
            // whatever the System had them doing ends with them
            for (Quest q : List.copyOf(QuestEngine.data(p).active())) {
                Run run = Run.of(q);
                if (run == null || !QuestEngine.data(p).all().contains(q)) continue;
                Situation s = byId(run.situation());
                if (s != null) s.playerDied(p, q, run, event.getSource());
            }
            return;
        }
        if (dead.getPersistentData().getBoolean(TARGET_MARK) && dead.level() instanceof ServerLevel level) {
            // everyone it may matter to: the players online, and whoever dealt the blow (a stand-in player too)
            Set<ServerPlayer> told = new LinkedHashSet<>(level.getServer().getPlayerList().getPlayers());
            ServerPlayer behind = Foes.playerBehind(event.getSource().getEntity());
            if (behind != null) told.add(behind);
            for (ServerPlayer p : told) {
                for (Quest q : List.copyOf(QuestEngine.data(p).active())) {
                    Run run = Run.of(q);
                    if (run == null || q.status() != Quest.Status.ACTIVE) continue;
                    Situation s = byId(run.situation());
                    if (s == null) continue;
                    // what the quest watched has fallen: its health rows read empty in what the player sees next
                    if (dead.getUUID().equals(run.target())) emptyHealth(p, q);
                    s.runEntityDied(p, q, run, dead, event.getSource());
                }
            }
        }
        ServerPlayer killer = Foes.playerBehind(event.getSource().getEntity());
        if (killer != null && watched(killer)) {
            Watch w = watch(killer);
            long now = QuestEngine.gameTime(killer);
            if (dead instanceof Enemy) {
                w.recentKills.addLast(now);
                if (now - w.lastHurt > 0) {
                    w.cleanKills++;
                    w.lastCleanKill = now;
                }
            }
            for (Situation s : ALL) s.killed(killer, dead, w);
            Secrets.killed(killer, dead);
        }
    }

    @SubscribeEvent
    static void onExpiring(QuestEvent.Expiring event) {
        Run run = Run.of(event.getQuest());
        if (run == null) return;
        Situation s = byId(run.situation());
        if (s != null && s.expiring(event.getPlayer(), event.getQuest(), run, event.getReason())) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onCompleted(QuestEvent.Completed event) {
        ended(event.getPlayer(), event.getQuest(), null);
    }

    @SubscribeEvent
    static void onFailed(QuestEvent.Failed event) {
        ended(event.getPlayer(), event.getQuest(), event.getReason());
    }

    private static void ended(ServerPlayer player, Quest q, @Nullable QuestEvent.FailReason reason) {
        Run run = Run.of(q);
        if (run == null) return;
        if (!q.isForced() && q.category() != QuestCategory.SECRET) {
            QuestEngine.data(player).setCooldown(GAP_KEY, QuestEngine.gameTime(player) + SituationConfig.gapTicks());
        }
        Situation s = byId(run.situation());
        if (s != null) s.ended(player, q, run, reason);
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && !event.isEndConquered()) Revenge.respawned(p);
    }

    @SubscribeEvent
    static void onConvert(LivingConversionEvent.Post event) {
        if (!event.getEntity().level().isClientSide) Cure.converted(event.getEntity(), event.getOutcome());
    }

    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer p && watched(p)) Places.opened(p, event.getPos());
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        WATCH.remove(event.getEntity().getUUID());
    }

    private Situations() {}
}
