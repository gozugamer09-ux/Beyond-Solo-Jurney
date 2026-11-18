package com.beyondsolo.monarch;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.enemy.EnemyModule;
import com.beyondsolo.enemy.MonsterKind;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.network.CutscenePayload;
import com.beyondsolo.network.JobTrialPayload;
import com.beyondsolo.network.MonarchFxPayload;
import com.beyondsolo.network.SkillFxPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.system.Balance;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Throne of Dominion: the monarch path's trial.
 *
 * <p>Raising the Sigil of Dominion starts a countdown, then the System takes the hunter into the Hall of the Empty
 * Throne ({@link ThroneHall}). The camera comes down the hall from the throne. Two waves of the fallen court rise from
 * the pillars, the strongest creatures of the gates made for a hunter far above the hunter's own level; then the
 * Arbiter kneeling before the dais tears free ({@link ThroneArbiterEntity}). When it falls the hunter climbs to the
 * throne and picks the crown they will wear ({@link Dominion}); the hall lights in its colour as they sit
 * ({@link CoronationTimeline}), and they are sent home a monarch.</p>
 *
 * <p>Falling in the hall doesn't kill: the hunter is thrown out and may come back after a while. Like the job change's
 * hall it stands in the gate dimension (the overworld in game tests), on its own lane.</p>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class ThroneTrial {
    public enum Phase { COUNTDOWN, INTRO, COURT, COURT_BREAK, ARBITER_INTRO, ARBITER, ARBITER_DOWN, CHOICE, CORONATION }

    static final int COUNTDOWN_TICKS = 80;
    static final int INTRO_TICKS = 170;
    static final int BREAK_TICKS = 60;
    static final int ARBITER_INTRO_TICKS = 135;
    /** The Arbiter takes {@code ArbiterEntity.DEATH_TICKS} to fall apart. */
    static final int ARBITER_DOWN_TICKS = 118;
    static final int WAVES = 2;
    /** Courtiers: the fallen court's health and blows next to creatures made for the hunter's level. */
    static final double COURT_HEALTH = 2.2;
    static final double COURT_DAMAGE = 1.3;
    /** The Arbiter's health and blows next to a gate boss made for the hunter's level. */
    static final double ARBITER_HEALTH = 3.0;
    static final double ARBITER_DAMAGE = 1.3;

    private static final ResourceLocation FREEZE = BeyondSolo.id("throne_trial_freeze");
    private static final String COURT_TAG = "beyondsolo_courtier";
    private static final int VIOLET = 0xB57BFF;
    private static final int CRIMSON = 0xE0304A;

    static final class Session {
        final ServerPlayer player;
        final int slot;
        final BlockPos center;
        @Nullable ServerLevel level;
        Phase phase = Phase.COUNTDOWN;
        int tick;
        int wave;
        int defeated;
        int goal;
        final List<UUID> foes = new ArrayList<>();
        /** Courtiers of the current wave that were killed (an unloaded or missing one is not a defeated one). */
        final Set<UUID> killed = new HashSet<>();
        @Nullable UUID arbiter;
        boolean flawless = true;
        @Nullable Dominion choice;
        int choiceTicks;
        int finishAt = CoronationTimeline.FINISH;

        Session(ServerPlayer player, int slot) {
            this.player = player;
            this.slot = slot;
            this.center = ThroneHall.center(slot);
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    // ================================================================ starting

    /** The sigil was raised by a hunter the throne will take: checks they may go now, then counts down. */
    static boolean requestStart(ServerPlayer player) {
        MonarchData data = MonarchAPI.get(player);
        if (SESSIONS.containsKey(player.getUUID())) return false;
        if (player.level().getGameTime() < data.retryAt()) {
            long seconds = (data.retryAt() - player.level().getGameTime()) / 20;
            MonarchAPI.info(player, "monarch_wait", seconds / 60, String.format("%02d", seconds % 60));
            return false;
        }
        if (busyElsewhere(player)) {
            MonarchAPI.info(player, "monarch_not_here");
            return false;
        }
        return begin(player, false);
    }

    /** Somewhere the throne can't take the hunter from. */
    static boolean busyElsewhere(ServerPlayer p) {
        return GateService.isDungeon(p.level()) || GateService.inTransit(p)
                || com.beyondsolo.awakening.AwakeningService.inSequence(p)
                || com.beyondsolo.penalty.PenaltyZone.busy(p)
                || com.beyondsolo.redgate.RedGates.occupied(p)
                || com.beyondsolo.job.JobTrial.inTrial(p)
                || com.beyondsolo.tower.TowerAPI.inCitadel(p)
                || com.beyondsolo.evaluation.EvaluationManager.isBeingEvaluated(p);
    }

    /** Carves the hall once per session (during the countdown, so its chunks are ready when the hunter arrives). */
    private static void prepare(Session s, ServerLevel level) {
        if (s.level == level) return;
        s.level = level;
        clearHall(level, s.center);
        ThroneHall.build(level, s.center);
    }

    /** Starts the trial for a hunter (with {@code now} = true the countdown is skipped). Returns false if one is running. */
    public static boolean begin(ServerPlayer player, boolean now) {
        if (SESSIONS.containsKey(player.getUUID())) return false;
        Session s = new Session(player, freeSlot());
        SESSIONS.put(player.getUUID(), s);
        if (now) {
            enter(s);
        } else {
            ServerLevel level = GateService.dungeon(player.server);
            if (level != null) prepare(s, level);
            Notifications.send(player, new SystemNotification(SystemNotification.Kind.QUEST,
                    Component.translatable("notify.beyondsolo.monarch_countdown.title"),
                    Component.translatable("notify.beyondsolo.monarch_countdown.body"), COUNTDOWN_TICKS + 20));
            sound(player.serverLevel(), player.position(), SoundEvents.BEACON_ACTIVATE, 1f, 0.5f);
            sound(player.serverLevel(), player.position(), SoundEvents.WARDEN_HEARTBEAT, 1f, 0.5f);
        }
        return SESSIONS.containsKey(player.getUUID());
    }

    public static boolean inTrial(Player player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    @Nullable
    public static Phase phase(Player player) {
        Session s = SESSIONS.get(player.getUUID());
        return s == null ? null : s.phase;
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

    /** Builds the hall and takes the hunter in. */
    private static void enter(Session s) {
        ServerPlayer p = s.player;
        ServerLevel level = GateService.dungeon(p.server);
        if (level == null) {
            BeyondSolo.LOGGER.error("Throne trial: the gate dimension is missing");
            SESSIONS.remove(p.getUUID());
            return;
        }
        prepare(s, level);
        MonarchAPI.enterTrial(p);
        Vec3 start = ThroneHall.start(s.center);
        p.teleportTo(level, start.x, start.y, start.z, 180f, 0f);
        if (p.position().distanceToSqr(start) > 1) p.moveTo(start.x, start.y, start.z, 180f, 0f);
        p.fallDistance = 0;
        p.setHealth(p.getMaxHealth());
        SystemAPI.restoreMana(p, 99999);
        setPhase(s, Phase.INTRO);
        freeze(p, true);
        spawnArbiter(s);
        Vec3 c = Vec3.atBottomCenterOf(s.center);
        Vec3 seat = ThroneHall.seat(s.center);
        // the camera starts at the empty throne and comes down the hall to the hunter
        send(p, CutscenePayload.glide(seat.add(0, 7, 4), start.add(0, 2.8, 6.5), seat.add(0, 2, 0), c.add(0, 4, -12), INTRO_TICKS - 50));
        sound(level, c, SoundEvents.END_PORTAL_SPAWN, 0.6f, 0.9f);
        sound(level, c, SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value(), 1f, 0.6f);
        banner(s, JobTrialPayload.INTRO, Component.translatable("monarch.beyondsolo.trial.title"), Component.translatable("monarch.beyondsolo.trial.intro"), 0, 0);
    }

    // ================================================================ ticking

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
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
                BeyondSolo.LOGGER.error("Throne trial failed for {}", s.player.getGameProfile().getName(), e);
                ended.add(s);
            }
        }
        for (Session s : ended) SESSIONS.remove(s.player.getUUID());
    }

    /** One tick of a session. Returns true when it is over. */
    private static boolean step(Session s) {
        ServerPlayer p = s.player;
        switch (s.phase) {
            case COUNTDOWN -> {
                // stepped into a gate (or somewhere else the throne can't take them from) while it counted down
                if (busyElsewhere(p) || !p.isAlive()) {
                    MonarchAPI.info(p, "monarch_not_here");
                    return true;
                }
                ServerLevel here = p.serverLevel();
                Vec3 at = p.position();
                double r = 1.8 - s.tick / (double) COUNTDOWN_TICKS;
                for (int i = 0; i < 4; i++) {
                    double a = s.tick * 0.3 + i * Math.PI / 2;
                    here.sendParticles(dust(i % 2 == 0 ? CRIMSON : VIOLET, 1.2f), at.x + Math.cos(a) * r, at.y + s.tick / 35.0, at.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
                if (s.tick % 20 == 0) sound(here, at, SoundEvents.WARDEN_HEARTBEAT, 1f, 0.5f + s.tick / 160f);
                if (s.tick >= COUNTDOWN_TICKS) {
                    here.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1, at.z, 100, 0.4, 1.2, 0.4, 0.4);
                    sound(here, at, SoundEvents.PORTAL_TRAVEL, 0.35f, 1.4f);
                    enter(s);
                }
            }
            case INTRO -> {
                if (s.tick >= INTRO_TICKS) {
                    send(p, CutscenePayload.stop());
                    freeze(p, false);
                    startWave(s, 1);
                }
            }
            case COURT -> {
                keepFoesInside(s);
                countFoes(s);
                if (s.tick % 10 == 0) waveBanner(s);
                if (s.defeated >= s.goal) {
                    setPhase(s, Phase.COURT_BREAK);
                    Balance.healRaw(p, p.getMaxHealth() * 0.3f);
                    SystemAPI.restoreMana(p, 200);
                    sound(s.level, p.position(), SoundEvents.PLAYER_LEVELUP, 0.5f, 1.4f);
                    banner(s, JobTrialPayload.WAVE, Component.translatable("monarch.beyondsolo.trial.court_clear", s.wave, WAVES),
                            Component.translatable(s.wave >= WAVES ? "monarch.beyondsolo.trial.arbiter_near" : "monarch.beyondsolo.trial.court_next"), s.goal, s.goal);
                }
            }
            case COURT_BREAK -> {
                if (s.tick >= BREAK_TICKS) {
                    if (s.wave < WAVES) startWave(s, s.wave + 1);
                    else startArbiter(s);
                }
            }
            case ARBITER_INTRO -> {
                ThroneArbiterEntity boss = arbiter(s);
                if (s.tick == 12 && boss != null) boss.wake();
                if (s.tick >= ARBITER_INTRO_TICKS) {
                    send(p, CutscenePayload.stop());
                    freeze(p, false);
                    setPhase(s, Phase.ARBITER);
                }
            }
            case ARBITER -> {
                ThroneArbiterEntity boss = arbiter(s);
                if (boss == null || !boss.isAlive()) {
                    setPhase(s, Phase.ARBITER_DOWN);
                    banner(s, JobTrialPayload.BOSS, Component.translatable("entity.beyondsolo.throne_arbiter"),
                            Component.translatable("monarch.beyondsolo.trial.arbiter_down"), 0, 100);
                    for (UUID id : s.foes) discard(s, id);
                    return false;
                }
                if (boss.position().distanceTo(Vec3.atBottomCenterOf(s.center)) > ThroneHall.RADIUS) {
                    Vec3 b = ThroneHall.arbiterSpot(s.center);
                    boss.teleportTo(b.x, b.y, b.z);
                }
                if (boss.getTarget() != p) boss.setTarget(p);
                if (s.tick % 5 == 0) {
                    banner(s, JobTrialPayload.BOSS, Component.translatable("entity.beyondsolo.throne_arbiter"),
                            Component.translatable(s.flawless ? "monarch.beyondsolo.trial.arbiter_flawless" : "monarch.beyondsolo.trial.arbiter_sub"),
                            Math.round(100 * boss.getHealth() / boss.getMaxHealth()), 100);
                }
            }
            case ARBITER_DOWN -> {
                if (s.tick >= ARBITER_DOWN_TICKS) startChoice(s);
            }
            case CHOICE -> {
                choiceStep(s);
                if (s.tick >= s.choiceTicks) choose(s, Dominion.leaning(SystemAPI.get(p).playerClass()));
            }
            case CORONATION -> {
                coronationStep(s);
                if (s.tick >= s.finishAt) {
                    finish(s);
                    return true;
                }
            }
        }
        // the hunter wandered out of the hall somehow (the walls are thick, but just in case)
        if (s.level != null && s.phase != Phase.COUNTDOWN && p.level() == s.level
                && p.position().distanceTo(Vec3.atBottomCenterOf(s.center)) > ThroneHall.RADIUS + 6) {
            Vec3 start = ThroneHall.start(s.center);
            p.teleportTo(s.level, start.x, start.y, start.z, 180f, 0f);
        }
        return false;
    }

    // ================================================================ the fallen court

    private static void startWave(Session s, int wave) {
        setPhase(s, Phase.COURT);
        s.wave = wave;
        s.defeated = 0;
        s.foes.clear();
        s.killed.clear();
        ServerLevel level = s.level;
        List<Vec3> feet = ThroneHall.pillarFeet(s.center);
        List<EntityType<? extends Mob>> roster = roster(wave);
        s.goal = roster.size();
        SystemData sys = SystemAPI.get(s.player);
        float hunter = com.beyondsolo.enemy.HiddenLevel.hunterLevel(sys);
        double health = COURT_HEALTH * Balance.healthAt(hunter);
        double damage = COURT_DAMAGE * Balance.damageAt(hunter);
        for (int i = 0; i < roster.size(); i++) {
            Vec3 at = feet.get((i * 3 + wave) % feet.size());
            Mob mob = roster.get(i).create(level);
            if (mob == null) {
                s.goal--;
                continue;
            }
            mob.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360, 0);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.MOB_SUMMONED, null);
            dressCourtier(mob, wave, health, damage);
            level.addFreshEntity(mob);
            mob.setTarget(s.player);
            s.foes.add(mob.getUUID());
            fxAt(level, SkillFxPayload.Kind.HEAL, VIOLET, at, 1.6f, 0);
            level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 1, at.z, 16, 0.3, 0.8, 0.3, 0.03);
        }
        Vec3 c = Vec3.atBottomCenterOf(s.center);
        sound(level, c, SoundEvents.EVOKER_PREPARE_SUMMON, 1f, 0.5f);
        sound(level, c, SoundEvents.WITHER_AMBIENT, 0.6f, 0.5f);
        waveBanner(s);
    }

    /** The fallen court: what is left of those who sat here before, the strongest kinds of the gates. */
    private static List<EntityType<? extends Mob>> roster(int wave) {
        List<MonsterKind> kinds = wave <= 1
                ? List.of(MonsterKind.RIME_STALKER, MonsterKind.RIME_STALKER, MonsterKind.WAILING_WRAITH, MonsterKind.WAILING_WRAITH,
                MonsterKind.HIVE_REAVER, MonsterKind.BRIMLING)
                : List.of(MonsterKind.RUNE_COLOSSUS, MonsterKind.PIT_RAVAGER, MonsterKind.RIMECLAW, MonsterKind.TUSK_SHAMAN,
                MonsterKind.STATIC_DRIFTER, MonsterKind.STATIC_DRIFTER, MonsterKind.HOLLOW_ACOLYTE);
        List<EntityType<? extends Mob>> out = new ArrayList<>();
        for (MonsterKind k : kinds) out.add(EnemyModule.type(k));
        return out;
    }

    private static void dressCourtier(Mob mob, int wave, double health, double damage) {
        mob.addTag(COURT_TAG);
        mob.setPersistenceRequired();
        mob.setCustomName(Component.translatable("entity.beyondsolo.courtier").withStyle(ChatFormatting.DARK_PURPLE));
        mob.setCustomNameVisible(false);
        AttributeInstance hp = mob.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(hp.getBaseValue() * health * (1 + 0.2f * wave));
        AttributeInstance dmg = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue(dmg.getBaseValue() * damage);
        AttributeInstance follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (follow != null) follow.setBaseValue(64);
        mob.setHealth(mob.getMaxHealth());
        mob.setDropChance(EquipmentSlot.HEAD, 0);
        mob.setDropChance(EquipmentSlot.MAINHAND, 0);
        mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
    }

    private static void countFoes(Session s) {
        boolean loaded = s.level.isLoaded(s.center);
        int defeated = 0;
        for (UUID id : s.foes) {
            Entity e = s.level.getEntity(id);
            if (e instanceof LivingEntity l && l.isAlive()) {
                if (s.tick % 40 == 0) s.level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 2, 0.2, 0.3, 0.2, 0.01);
                if (l instanceof Mob m && m.getTarget() != s.player) m.setTarget(s.player);
            } else if (s.killed.contains(id) || (e == null && loaded && s.player.level() == s.level)) {
                defeated++;
            }
        }
        s.defeated = defeated;
    }

    private static void keepFoesInside(Session s) {
        if (s.tick % 20 != 0) return;
        Vec3 c = Vec3.atBottomCenterOf(s.center);
        for (UUID id : s.foes) {
            Entity e = s.level.getEntity(id);
            if (e != null && e.position().distanceTo(c) > ThroneHall.RADIUS + 2) e.teleportTo(c.x, c.y, c.z);
        }
    }

    private static void waveBanner(Session s) {
        banner(s, JobTrialPayload.WAVE, Component.translatable("monarch.beyondsolo.trial.court", s.wave, WAVES),
                Component.translatable("monarch.beyondsolo.trial.court_sub"), s.defeated, s.goal);
    }

    // ================================================================ the Arbiter

    /** It kneels before the dais from the start, chained to nothing, waiting. */
    private static void spawnArbiter(Session s) {
        if (s.arbiter != null && arbiter(s) != null) return;
        ServerLevel level = s.level;
        ThroneArbiterEntity boss = MonarchModule.THRONE_ARBITER.get().create(level);
        if (boss == null) return;
        Vec3 at = ThroneHall.arbiterSpot(s.center);
        boss.moveTo(at.x, at.y, at.z, 0f, 0f);
        boss.setYHeadRot(0f);
        boss.setYBodyRot(0f);
        SystemData sys = SystemAPI.get(s.player);
        float hunter = com.beyondsolo.enemy.HiddenLevel.hunterLevel(sys);
        boss.getAttribute(Attributes.MAX_HEALTH).setBaseValue(ARBITER_HEALTH * Balance.BOSS_HEALTH * Balance.healthAt(hunter));
        boss.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(ARBITER_DAMAGE * Balance.BOSS_DAMAGE * Balance.damageAt(hunter));
        boss.setHealth(boss.getMaxHealth());
        boss.addTag(COURT_TAG);
        level.addFreshEntity(boss);
        s.arbiter = boss.getUUID();
    }

    private static void startArbiter(Session s) {
        setPhase(s, Phase.ARBITER_INTRO);
        ServerLevel level = s.level;
        ServerPlayer p = s.player;
        spawnArbiter(s);
        freeze(p, true);
        Vec3 start = ThroneHall.start(s.center);
        p.teleportTo(level, start.x, start.y, start.z, 180f, 0f);
        Vec3 at = ThroneHall.arbiterSpot(s.center);
        Vec3 face = at.add(0, 8.5, 0);
        // over the hunter's shoulder, pushing in on the Arbiter as it stands
        send(p, CutscenePayload.glide(start.add(1.8, 2.6, 3.5), at.add(3.5, 4.5, 14), face.add(0, -2.5, 0), face, ARBITER_INTRO_TICKS - 45));
        sound(level, at, SoundEvents.WARDEN_EMERGE, 1.6f, 0.5f);
        level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 1, at.z, 80, 2, 2, 2, 0.05);
        banner(s, JobTrialPayload.BOSS, Component.translatable("entity.beyondsolo.throne_arbiter"),
                Component.translatable("monarch.beyondsolo.trial.arbiter_intro"), 100, 100);
    }

    @Nullable
    private static ThroneArbiterEntity arbiter(Session s) {
        if (s.arbiter == null || s.level == null) return null;
        return s.level.getEntity(s.arbiter) instanceof ThroneArbiterEntity b ? b : null;
    }

    // ================================================================ the throne

    /** The way to the throne is open: the hunter stands at its foot and picks their crown. */
    private static void startChoice(Session s) {
        setPhase(s, Phase.CHOICE);
        ServerPlayer p = s.player;
        if (s.arbiter != null) discard(s, s.arbiter);
        Vec3 foot = ThroneHall.throneFoot(s.center);
        Vec3 seat = ThroneHall.seat(s.center);
        p.teleportTo(s.level, foot.x, foot.y, foot.z, 180f, 0f);
        freeze(p, true);
        p.setHealth(p.getMaxHealth());
        s.choiceTicks = MonarchConfig.CHOICE_SECONDS.get() * 20;
        Dominion leaning = Dominion.leaning(SystemAPI.get(p).playerClass());
        // behind the hunter, looking up at the empty throne
        send(p, CutscenePayload.glide(foot.add(2.5, 3.2, 7), foot.add(1.2, 2.6, 4.5), seat.add(0, 1, 0), seat.add(0, 2.5, -0.5), 80));
        PacketDistributor.sendToPlayer(p, new MonarchFxPayload(MonarchFxPayload.CHOOSE, p.getId(), VIOLET, leaning.ordinal(), s.choiceTicks));
        banner(s, JobTrialPayload.VERDICT, Component.empty(), Component.empty(), 0, 0);
        sound(s.level, seat, SoundEvents.BEACON_POWER_SELECT, 1.2f, 0.5f);
        sound(s.level, seat, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 0.6f);
    }

    /** Light drifting up from the throne while it waits. */
    private static void choiceStep(Session s) {
        if (s.tick % 4 != 0) return;
        Vec3 seat = ThroneHall.seat(s.center);
        double a = s.tick * 0.08;
        s.level.sendParticles(dust(VIOLET, 1.1f), seat.x + Math.cos(a) * 1.6, seat.y + 0.4 + (s.tick % 40) / 12.0, seat.z + Math.sin(a) * 1.6, 1, 0, 0, 0, 0);
        s.level.sendParticles(ParticleTypes.END_ROD, seat.x, seat.y + 2, seat.z, 1, 0.6, 1.2, 0.3, 0.01);
    }

    /** The hunter picked a crown at the throne (from their screen). */
    public static void choose(ServerPlayer player, Dominion dominion) {
        Session s = SESSIONS.get(player.getUUID());
        if (s != null) choose(s, dominion);
    }

    private static void choose(Session s, Dominion dominion) {
        if (s.phase != Phase.CHOICE) return;
        s.choice = dominion;
        startCoronation(s);
    }

    /** The hunter sits: the hall lights in their crown's colour while the camera circles them. */
    private static void startCoronation(Session s) {
        setPhase(s, Phase.CORONATION);
        ServerPlayer p = s.player;
        Dominion d = s.choice;
        s.finishAt = CoronationTimeline.FINISH;
        Vec3 seat = ThroneHall.seat(s.center);
        p.teleportTo(s.level, seat.x, seat.y, seat.z, 0f, 0f);
        freeze(p, true);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, s.finishAt + 40, 4, false, false, false));
        p.setHealth(p.getMaxHealth());
        send(p, CutscenePayload.orbit(seat.add(0, 1.4, 0), 7.5, 2.2, 0.25f, 0.8f, s.finishAt));
        PacketDistributor.sendToPlayer(p, new MonarchFxPayload(MonarchFxPayload.CORONATION, p.getId(), d.color(), d.ordinal(), s.finishAt));
        banner(s, JobTrialPayload.VERDICT, Component.empty(), Component.empty(), 0, 0);
        sound(s.level, seat, SoundEvents.BEACON_POWER_SELECT, 1.2f, 0.6f);
    }

    /** The hall's side of the coronation: the braziers flare one by one, light gathers on the throne, and a burst at the lock. */
    private static void coronationStep(Session s) {
        ServerLevel level = s.level;
        Dominion d = s.choice;
        int color = d.color();
        int t = s.tick;
        Vec3 seat = ThroneHall.seat(s.center);
        List<BlockPos> tops = ThroneHall.pillarTops(s.center);
        if (t < CoronationTimeline.LOCK) {
            int lit = Math.min(tops.size(), t * tops.size() / CoronationTimeline.GATHER);
            for (int i = 0; i < lit; i++) {
                BlockPos top = tops.get(i);
                if (t % 4 == 0) level.sendParticles(dust(color, 1.3f), top.getX() + 0.5, top.getY() + 1.2, top.getZ() + 0.5, 2, 0.15, 0.3, 0.15, 0);
                if (t % 3 == 0) {
                    double k = level.random.nextDouble();
                    Vec3 from = Vec3.atCenterOf(top).add(0, 1, 0);
                    Vec3 at = from.add(seat.add(0, 1.2, 0).subtract(from).scale(k));
                    level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                }
            }
            if (t % 7 == 0 && lit > 0) sound(level, Vec3.atCenterOf(tops.get(lit - 1)), SoundEvents.FIRECHARGE_USE, 0.6f, 0.7f + lit * 0.08f);
            if (t % 2 == 0) {
                double a = t * 0.3;
                level.sendParticles(dust(0xFFFFFF, 0.9f), seat.x + Math.cos(a) * 1.5, seat.y + t / 60.0, seat.z + Math.sin(a) * 1.5, 1, 0, 0, 0, 0);
            }
        }
        if (t == CoronationTimeline.LOCK) {
            for (int i = 0; i < 72; i++) {
                double a = i / 72.0 * Math.PI * 2;
                level.sendParticles(dust(color, 1.5f), seat.x + Math.cos(a) * 3, seat.y - 0.3, seat.z + Math.sin(a) * 3, 1, 0, 0.05, 0, 0);
            }
            level.sendParticles(ParticleTypes.END_ROD, seat.x, seat.y + 1.2, seat.z, 100, 0.4, 0.8, 0.4, 0.3);
            fxAt(level, SkillFxPayload.Kind.NOVA, color, seat, 14, 3);
            fxAt(level, SkillFxPayload.Kind.HEAL, color, seat, 3f, 0);
            fxAt(level, SkillFxPayload.Kind.ROAR, color, seat, 22, 0);
            sound(level, seat, SoundEvents.END_PORTAL_FRAME_FILL, 1.4f, 0.6f);
            sound(level, seat, SoundEvents.WITHER_SPAWN, 0.8f, 0.8f);
            sound(level, seat, SoundEvents.TRIDENT_THUNDER.value(), 1.2f, 0.6f);
        }
        if (t > CoronationTimeline.LOCK && t < CoronationTimeline.LOCK + 48 && t % 8 == 0) {
            fxAt(level, SkillFxPayload.Kind.NOVA, t % 16 == 0 ? 0xFFFFFF : color, seat, 5 + (t - CoronationTimeline.LOCK) / 3f, 3);
        }
        if (t > CoronationTimeline.LOCK && t % 5 == 0) {
            level.sendParticles(dust(color, 1.1f), seat.x, seat.y + 1, seat.z, 4, 0.6, 1, 0.6, 0);
        }
    }

    /** The coronation was skipped on the hunter's screen: the crown still takes shape (the lock), then they go home. */
    public static void skipCoronation(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || s.phase != Phase.CORONATION) return;
        s.finishAt = Math.min(s.finishAt, Math.max(s.tick, CoronationTimeline.LOCK) + 15);
    }

    /** The crown is theirs: they go home a monarch. */
    private static void finish(Session s) {
        ServerPlayer p = s.player;
        Dominion d = s.choice;
        send(p, CutscenePayload.stop());
        banner(s, JobTrialPayload.NONE, Component.empty(), Component.empty(), 0, 0);
        freeze(p, false);
        returnHome(p, false);
        crowned(p, d, s.flawless);
        p.setHealth(p.getMaxHealth());
        Notifications.send(p, new SystemNotification(SystemNotification.Kind.LEVEL_UP,
                Component.translatable("notify.beyondsolo.monarch_crowned.title"),
                Component.translatable("notify.beyondsolo.monarch_crowned.body", d.title()), 300));
        Component msg = Component.translatable("chat.beyondsolo.monarch_crowned", p.getDisplayName(),
                d.title().copy().withStyle(st -> st.withColor(TextColor.fromRgb(d.color())).withBold(true))).withStyle(ChatFormatting.LIGHT_PURPLE);
        p.server.getPlayerList().broadcastSystemMessage(msg, false);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.2f, 0.6f);
        cleanup(s);
    }

    /** What the crown gives, wherever it ends (the hall, or a hunter who left during the coronation). */
    private static void crowned(ServerPlayer p, Dominion d, boolean flawless) {
        MonarchAPI.crown(p, d);
        if (flawless) SystemAPI.setTitle(p, MonarchModule.UNBLEMISHED);
        QuestEngine.progress(p, MonarchModule.THRONE_KEY, 1);
    }

    // ================================================================ failing, leaving

    /** The hunter fell in the hall: thrown out, alive, and may come back later. */
    private static void fail(Session s) {
        ServerPlayer p = s.player;
        send(p, CutscenePayload.stop());
        freeze(p, false);
        p.setHealth(p.getMaxHealth() * 0.5f);
        p.removeAllEffects();
        p.clearFire();
        returnHome(p, true);
        banner(s, JobTrialPayload.FAILED, Component.translatable("monarch.beyondsolo.trial.failed"),
                Component.translatable("monarch.beyondsolo.trial.failed_sub", MonarchConfig.RETRY_MINUTES.get()), 0, 0);
        sound(p.serverLevel(), p.position(), SoundEvents.WITHER_DEATH, 0.3f, 1.2f);
        cleanup(s);
    }

    /** The hunter logged out mid-trial. Past the Arbiter, the crown is theirs anyway. */
    private static void abandon(Session s) {
        if (s.phase == Phase.CHOICE || s.phase == Phase.CORONATION) {
            Dominion d = s.choice != null ? s.choice : Dominion.leaning(SystemAPI.get(s.player).playerClass());
            crowned(s.player, d, s.flawless);
        }
        freeze(s.player, false);
        cleanup(s);
        // inTrial stays set: they are sent home when they log back in
    }

    private static void cleanup(Session s) {
        if (s.level == null) return;
        for (UUID id : s.foes) discard(s, id);
        if (s.arbiter != null) discard(s, s.arbiter);
    }

    private static void discard(Session s, UUID id) {
        Entity e = s.level.getEntity(id);
        if (e != null) e.discard();
    }

    /**
     * Commands: ends a running trial at once with no outcome. The hunter goes back where they came from, nothing changes
     * and they may try again whenever they like. Returns false if they were not in one.
     */
    public static boolean cancel(ServerPlayer player) {
        Session s = SESSIONS.remove(player.getUUID());
        boolean inside = MonarchAPI.get(player).inTrial();
        if (s == null && !inside) return false;
        if (s != null) {
            send(player, CutscenePayload.stop());
            banner(s, JobTrialPayload.NONE, Component.empty(), Component.empty(), 0, 0);
            cleanup(s);
        }
        freeze(player, false);
        if (inside) returnHome(player, false);
        return true;
    }

    /** Sends a hunter back where they came from. */
    static void returnHome(ServerPlayer p, boolean failed) {
        MonarchData data = MonarchAPI.get(p);
        MinecraftServer server = p.server;
        ResourceLocation dim = data.returnDimension();
        ServerLevel home = dim == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
        if (home == null) home = server.overworld();
        Vec3 at = data.inTrial() ? data.returnPos() : p.position();
        if (data.inTrial()) {
            p.teleportTo(home, at.x, at.y, at.z, data.returnYaw(), 0f);
            if (p.level() == home && p.position().distanceToSqr(at) > 1) p.moveTo(at.x, at.y, at.z, data.returnYaw(), 0f);
        }
        p.fallDistance = 0;
        MonarchAPI.leaveTrial(p, failed);
        home.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1, at.z, 60, 0.4, 1, 0.4, 0.2);
    }

    private static void clearHall(ServerLevel level, BlockPos c) {
        AABB box = new AABB(c).inflate(ThroneHall.RADIUS + 4, ThroneHall.HEIGHT + 4, ThroneHall.RADIUS + 4);
        for (Entity e : level.getEntitiesOfClass(Entity.class, box, e -> !(e instanceof Player))) e.discard();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().getTags().contains(COURT_TAG)) {
            UUID id = event.getEntity().getUUID();
            for (Session s : SESSIONS.values()) if (s.foes.contains(id)) s.killed.add(id);
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || s.phase == Phase.COUNTDOWN) return;
        event.setCanceled(true);
        p.setHealth(p.getMaxHealth() * 0.5f);
        if (s.phase == Phase.CHOICE || s.phase == Phase.CORONATION) return;
        SESSIONS.remove(p.getUUID());
        fail(s);
    }

    /** A hit that would kill the hunter ends the trial instead: no real death, and no totem used up. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onFatalDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || s.phase == Phase.COUNTDOWN || s.level == null) return;
        if (event.getNewDamage() < p.getHealth()) return;
        event.setNewDamage(0);
        if (s.phase == Phase.CHOICE || s.phase == Phase.CORONATION) return;
        // ended on the next tick, outside the damage call
        p.server.execute(() -> {
            if (SESSIONS.remove(p.getUUID()) == s) fail(s);
        });
    }

    @SubscribeEvent
    static void onHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Session s = SESSIONS.get(p.getUUID());
        if (s == null) return;
        // no harm while the camera has the hunter (intros, the throne)
        if (s.phase == Phase.INTRO || s.phase == Phase.ARBITER_INTRO || s.phase == Phase.ARBITER_DOWN
                || s.phase == Phase.CHOICE || s.phase == Phase.CORONATION) {
            event.setCanceled(true);
            return;
        }
        if (s.phase == Phase.ARBITER && s.arbiter != null && event.getSource().getEntity() != null
                && s.arbiter.equals(event.getSource().getEntity().getUUID())) {
            s.flawless = false;
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) loggedIn(p);
    }

    /** A hunter came back: one still marked as in the hall (they left mid-trial) is sent home. */
    public static void loggedIn(ServerPlayer p) {
        MonarchData data = MonarchAPI.get(p);
        if (data.inTrial() && !SESSIONS.containsKey(p.getUUID())) {
            freeze(p, false);
            returnHome(p, false);
            Dominion d = MonarchAPI.dominion(p);
            if (d != null) MonarchAPI.info(p, "monarch_returned_crowned", d.title());
            else MonarchAPI.info(p, "monarch_returned");
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) loggedOut(p);
    }

    /**
     * Logging out ends the session here, before the player is saved (a change made on a later tick would be lost with
     * the old player object): past the Arbiter, the crown is theirs.
     */
    public static void loggedOut(ServerPlayer p) {
        Session s = SESSIONS.remove(p.getUUID());
        if (s != null) abandon(s);
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        SESSIONS.clear();
    }

    // ================================================================ helpers

    private static void setPhase(Session s, Phase phase) {
        s.phase = phase;
        s.tick = 0;
    }

    /** Holds the hunter still while the camera has them. */
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

    private static void banner(Session s, int phase, Component title, Component sub, int progress, int goal) {
        PacketDistributor.sendToPlayer(s.player, new JobTrialPayload(phase, title, sub, progress, goal, s.tick));
    }

    private static void send(ServerPlayer p, CutscenePayload shot) {
        PacketDistributor.sendToPlayer(p, shot);
    }

    static void fxAt(ServerLevel level, SkillFxPayload.Kind kind, int color, Vec3 at, float size, int duration) {
        PacketDistributor.sendToPlayersNear(level, null, at.x, at.y, at.z, 96,
                new SkillFxPayload(-1, kind, color & 0xFFFFFF, (float) at.x, (float) at.y, (float) at.z, size, duration));
    }

    static DustParticleOptions dust(int rgb, float scale) {
        return new DustParticleOptions(new org.joml.Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f), scale);
    }

    private static void sound(@Nullable ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        if (level != null) level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    // ================================================================ tests and commands

    /** Tests: the living courtiers and the Arbiter of a hunter's running trial. */
    public static List<LivingEntity> foes(ServerPlayer player) {
        List<LivingEntity> out = new ArrayList<>();
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || s.level == null) return out;
        for (UUID id : s.foes) if (s.level.getEntity(id) instanceof LivingEntity e && e.isAlive()) out.add(e);
        ThroneArbiterEntity b = arbiter(s);
        if (b != null && b.isAlive() && s.phase.ordinal() >= Phase.ARBITER_INTRO.ordinal()) out.add(b);
        return out;
    }

    /** Tests: whether the Arbiter has not landed a blow yet. */
    public static boolean flawless(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        return s != null && s.flawless;
    }

    /**
     * Commands: skips to a part of a running trial: the court's waves ("1", "2"), the Arbiter ("arbiter") or the throne
     * ("throne"; with a dominion, the crown is picked at once).
     */
    public static boolean jump(ServerPlayer player, String to, @Nullable Dominion crown) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || s.level == null) return false;
        for (UUID id : s.foes) discard(s, id);
        s.foes.clear();
        switch (to) {
            case "arbiter" -> {
                send(player, CutscenePayload.stop());
                freeze(player, false);
                startArbiter(s);
            }
            case "throne" -> {
                startChoice(s);
                if (crown != null) choose(s, crown);
            }
            default -> {
                send(player, CutscenePayload.stop());
                freeze(player, false);
                startWave(s, Mth.clamp(parseInt(to), 1, WAVES));
            }
        }
        return true;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private ThroneTrial() {}
}
