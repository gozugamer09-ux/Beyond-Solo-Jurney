package com.beyondsolo.job;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.network.CutscenePayload;
import com.beyondsolo.network.JobRevealPayload;
import com.beyondsolo.network.JobTrialPayload;
import com.beyondsolo.network.SkillFxPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.registry.ModEntities;
import com.beyondsolo.system.PlayerRank;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
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
 * The Trial of Ascension: the job change's solo trial.
 *
 * <p>Using the Key of Ascension starts a short countdown, then the System pulls the player into a sealed hall
 * ({@link TrialArena}). A camera sweep shows the hall, three waves of Echoes rise from the pillars, then the Arbiter of
 * Ascension wakes (a camera push-in on its roar). When it falls the player is lifted to the sigil in the middle, the
 * camera circles them and the reveal picks their subclass ({@link JobRevealTimeline}). Then they are sent home as
 * their new subclass. Falling in the trial doesn't kill: the player is thrown out and may try again later.</p>
 *
 * <p>The hall is in the gate dimension (the overworld in game tests), on its own lane far from gate caves.</p>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class JobTrial {
    public enum Phase { COUNTDOWN, INTRO, WAVE, WAVE_BREAK, BOSS_INTRO, BOSS, BOSS_DOWN, VERDICT }

    static final int COUNTDOWN_TICKS = 60;
    static final int INTRO_TICKS = 110;
    static final int WAVE_BREAK_TICKS = 50;
    static final int BOSS_INTRO_TICKS = 100;
    static final int BOSS_DOWN_TICKS = 70;
    static final int WAVES = 3;

    private static final ResourceLocation FREEZE = BeyondSolo.id("job_trial_freeze");
    private static final String ECHO_TAG = "beyondsolo_echo";

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
        @Nullable UUID boss;
        boolean flawless = true;
        @Nullable Subclass result;
        int finishAt;

        Session(ServerPlayer player, int slot) {
            this.player = player;
            this.slot = slot;
            this.center = TrialArena.center(slot);
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    // ================================================================ starting

    /** The player used the key: checks they may, then counts down. */
    public static void requestStart(ServerPlayer player) {
        JobData data = JobAPI.get(player);
        if (SESSIONS.containsKey(player.getUUID())) return;
        if (data.changed()) {
            info(player, "job_done");
            return;
        }
        if (!data.unlocked()) {
            info(player, "job_locked", JobAPI.unlockLevel());
            return;
        }
        if (player.level().getGameTime() < data.retryAt()) {
            long seconds = (data.retryAt() - player.level().getGameTime()) / 20;
            info(player, "job_wait", seconds / 60, String.format("%02d", seconds % 60));
            return;
        }
        if (GateService.isDungeon(player.level()) || GateService.inTransit(player)) {
            info(player, "job_not_here");
            return;
        }
        begin(player, false);
    }

    /** Starts the trial for a player (with {@code now} = true the countdown is skipped). Returns false if one is running. */
    public static boolean begin(ServerPlayer player, boolean now) {
        if (SESSIONS.containsKey(player.getUUID())) return false;
        Session s = new Session(player, freeSlot());
        SESSIONS.put(player.getUUID(), s);
        if (now) {
            enter(s);
        } else {
            Notifications.send(player, new SystemNotification(SystemNotification.Kind.QUEST,
                    Component.translatable("notify.beyondsolo.job_countdown.title"),
                    Component.translatable("notify.beyondsolo.job_countdown.body"), COUNTDOWN_TICKS + 20));
            sound(player.serverLevel(), player.position(), SoundEvents.BEACON_ACTIVATE, 1f, 0.6f);
        }
        return true;
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

    /** Builds the hall and takes the player in. */
    private static void enter(Session s) {
        ServerPlayer p = s.player;
        ServerLevel level = GateService.dungeon(p.server);
        if (level == null) {
            BeyondSolo.LOGGER.error("Job change trial: the gate dimension is missing");
            SESSIONS.remove(p.getUUID());
            return;
        }
        s.level = level;
        clearArena(level, s.center);
        TrialArena.build(level, s.center);
        JobAPI.enterTrial(p);
        Vec3 start = TrialArena.start(s.center);
        p.teleportTo(level, start.x, start.y, start.z, 180f, 0f);
        if (p.position().distanceToSqr(start) > 1) p.moveTo(start.x, start.y, start.z, 180f, 0f);
        p.fallDistance = 0;
        p.setHealth(p.getMaxHealth());
        SystemAPI.restoreMana(p, 9999);
        setPhase(s, Phase.INTRO);
        freeze(p, true);
        Vec3 c = Vec3.atBottomCenterOf(s.center);
        // the camera drops in from under the dome and settles behind the player
        send(p, CutscenePayload.glide(c.add(0, 13, 10), start.add(0, 2.6, 5.5), c.add(0, 0, -4), c.add(0, 2, -8), INTRO_TICKS - 10));
        sound(level, c, SoundEvents.END_PORTAL_SPAWN, 0.6f, 1.4f);
        sound(level, c, SoundEvents.AMBIENT_CAVE.value(), 1f, 0.6f);
        banner(s, JobTrialPayload.INTRO, Component.translatable("job.beyondsolo.trial.title"), Component.translatable("job.beyondsolo.trial.intro"), 0, 0);
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
                BeyondSolo.LOGGER.error("Job change trial failed for {}", s.player.getGameProfile().getName(), e);
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
                ServerLevel here = p.serverLevel();
                Vec3 at = p.position();
                double r = 1.6 - s.tick / (double) COUNTDOWN_TICKS;
                for (int i = 0; i < 3; i++) {
                    double a = (s.tick * 0.35 + i * Math.PI * 2 / 3);
                    here.sendParticles(dust(0x9B7BE0, 1.1f), at.x + Math.cos(a) * r, at.y + s.tick / 30.0, at.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
                if (s.tick % 20 == 0) sound(here, at, SoundEvents.NOTE_BLOCK_BELL.value(), 0.8f, 0.6f + s.tick / 60f);
                if (s.tick >= COUNTDOWN_TICKS) {
                    here.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1, at.z, 80, 0.4, 1, 0.4, 0.4);
                    sound(here, at, SoundEvents.PORTAL_TRAVEL, 0.35f, 1.6f);
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
            case WAVE -> {
                keepFoesInside(s);
                countFoes(s);
                if (s.tick % 10 == 0) waveBanner(s);
                if (s.defeated >= s.goal) {
                    setPhase(s, Phase.WAVE_BREAK);
                    p.heal(p.getMaxHealth() * 0.25f);
                    sound(s.level, p.position(), SoundEvents.PLAYER_LEVELUP, 0.5f, 1.6f);
                    banner(s, JobTrialPayload.WAVE, Component.translatable("job.beyondsolo.trial.wave_clear", s.wave, WAVES),
                            Component.translatable(s.wave >= WAVES ? "job.beyondsolo.trial.boss_near" : "job.beyondsolo.trial.wave_next"), s.goal, s.goal);
                }
            }
            case WAVE_BREAK -> {
                if (s.tick >= WAVE_BREAK_TICKS) {
                    if (s.wave < WAVES) startWave(s, s.wave + 1);
                    else startBoss(s);
                }
            }
            case BOSS_INTRO -> {
                GateBossEntity boss = boss(s);
                if (s.tick == 34 && boss != null) boss.wake();
                if (s.tick >= BOSS_INTRO_TICKS) {
                    send(p, CutscenePayload.stop());
                    freeze(p, false);
                    setPhase(s, Phase.BOSS);
                }
            }
            case BOSS -> {
                GateBossEntity boss = boss(s);
                if (boss == null || !boss.isAlive()) {
                    setPhase(s, Phase.BOSS_DOWN);
                    banner(s, JobTrialPayload.BOSS, Component.translatable("job.beyondsolo.trial.arbiter"),
                            Component.translatable("job.beyondsolo.trial.arbiter_down"), 0, 100);
                    for (UUID id : s.foes) discard(s, id);
                    return false;
                }
                if (boss.position().distanceTo(Vec3.atBottomCenterOf(s.center)) > TrialArena.RADIUS) {
                    Vec3 b = TrialArena.bossSpot(s.center);
                    boss.teleportTo(b.x, b.y, b.z);
                }
                if (boss.getTarget() != p) boss.setTarget(p);
                if (s.tick % 5 == 0) {
                    banner(s, JobTrialPayload.BOSS, Component.translatable("job.beyondsolo.trial.arbiter"),
                            Component.translatable(s.flawless ? "job.beyondsolo.trial.arbiter_flawless" : "job.beyondsolo.trial.arbiter_sub"),
                            Math.round(100 * boss.getHealth() / boss.getMaxHealth()), 100);
                }
            }
            case BOSS_DOWN -> {
                if (s.tick >= BOSS_DOWN_TICKS) startVerdict(s);
            }
            case VERDICT -> {
                verdictStep(s);
                if (s.tick >= s.finishAt) {
                    finish(s);
                    return true;
                }
            }
        }
        // the player wandered out of the hall somehow (the walls are thick, but just in case)
        if (s.level != null && s.phase != Phase.COUNTDOWN && p.level() == s.level
                && p.position().distanceTo(Vec3.atBottomCenterOf(s.center)) > TrialArena.RADIUS + 6) {
            Vec3 start = TrialArena.start(s.center);
            p.teleportTo(s.level, start.x, start.y, start.z, 180f, 0f);
        }
        return false;
    }

    // ================================================================ waves

    private static void startWave(Session s, int wave) {
        setPhase(s, Phase.WAVE);
        s.wave = wave;
        s.defeated = 0;
        s.foes.clear();
        ServerLevel level = s.level;
        List<Vec3> feet = TrialArena.pillarFeet(s.center);
        List<EntityType<? extends Mob>> roster = roster(wave);
        s.goal = roster.size();
        SystemData sys = SystemAPI.get(s.player);
        float scale = 1f + 0.3f * sys.rank().tier() + sys.level() / 30f;
        for (int i = 0; i < roster.size(); i++) {
            Vec3 at = feet.get((i * 3 + wave) % feet.size());
            Mob mob = roster.get(i).create(level);
            if (mob == null) {
                s.goal--;
                continue;
            }
            mob.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360, 0);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.MOB_SUMMONED, null);
            dressEcho(mob, wave, scale);
            level.addFreshEntity(mob);
            mob.setTarget(s.player);
            s.foes.add(mob.getUUID());
            fxAt(level, SkillFxPayload.Kind.HEAL, 0x9B7BE0, at, 1.4f, 0);
            level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 1, at.z, 14, 0.3, 0.8, 0.3, 0.03);
        }
        sound(level, Vec3.atBottomCenterOf(s.center), SoundEvents.EVOKER_PREPARE_SUMMON, 1f, 0.6f);
        sound(level, Vec3.atBottomCenterOf(s.center), SoundEvents.SOUL_ESCAPE.value(), 1f, 0.5f);
        waveBanner(s);
    }

    private static List<EntityType<? extends Mob>> roster(int wave) {
        return switch (wave) {
            case 1 -> List.of(EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SKELETON);
            case 2 -> List.of(EntityType.SKELETON, EntityType.STRAY, EntityType.HUSK, EntityType.HUSK, EntityType.ZOMBIE, EntityType.STRAY);
            default -> List.of(EntityType.WITHER_SKELETON, EntityType.WITHER_SKELETON, EntityType.VINDICATOR, EntityType.VINDICATOR,
                    EntityType.PILLAGER, EntityType.SKELETON);
        };
    }

    /** Echoes: the System's copies of hunters who failed this trial. Tougher with the player's rank and level. */
    private static void dressEcho(Mob mob, int wave, float scale) {
        mob.addTag(ECHO_TAG);
        mob.setPersistenceRequired();
        mob.setCustomName(Component.translatable("entity.beyondsolo.echo").withStyle(ChatFormatting.LIGHT_PURPLE));
        mob.setCustomNameVisible(false);
        AttributeInstance hp = mob.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(hp.getBaseValue() * scale * (1 + 0.15f * wave));
        AttributeInstance dmg = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue(dmg.getBaseValue() * (0.8f + 0.25f * scale));
        AttributeInstance follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (follow != null) follow.setBaseValue(64);
        mob.setHealth(mob.getMaxHealth());
        if (mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
        mob.setDropChance(EquipmentSlot.HEAD, 0);
        mob.setDropChance(EquipmentSlot.MAINHAND, 0);
        mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
    }

    private static void countFoes(Session s) {
        int alive = 0;
        for (UUID id : s.foes) {
            Entity e = s.level.getEntity(id);
            if (e instanceof LivingEntity l && l.isAlive()) {
                alive++;
                if (s.tick % 40 == 0) s.level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 2, 0.2, 0.3, 0.2, 0.01);
                if (l instanceof Mob m && m.getTarget() != s.player) m.setTarget(s.player);
            }
        }
        s.defeated = s.goal - alive;
    }

    private static void keepFoesInside(Session s) {
        if (s.tick % 20 != 0) return;
        Vec3 c = Vec3.atBottomCenterOf(s.center);
        for (UUID id : s.foes) {
            Entity e = s.level.getEntity(id);
            if (e != null && e.position().distanceTo(c) > TrialArena.RADIUS + 2) e.teleportTo(c.x, c.y, c.z);
        }
    }

    private static void waveBanner(Session s) {
        banner(s, JobTrialPayload.WAVE, Component.translatable("job.beyondsolo.trial.wave", s.wave, WAVES),
                Component.translatable("job.beyondsolo.trial.wave_sub"), s.defeated, s.goal);
    }

    // ================================================================ the Arbiter

    private static void startBoss(Session s) {
        setPhase(s, Phase.BOSS_INTRO);
        ServerLevel level = s.level;
        ServerPlayer p = s.player;
        SystemData sys = SystemAPI.get(p);
        PlayerRank look = PlayerRank.values()[Math.min(PlayerRank.values().length - 1, sys.rank().ordinal() + 1)];
        GateBossEntity boss = ModEntities.GATE_BOSS.get().create(level);
        if (boss == null) {
            setPhase(s, Phase.BOSS_DOWN);
            return;
        }
        Vec3 at = TrialArena.bossSpot(s.center);
        // an instance only for its looks and base numbers: it is never registered, so no gate reacts to this boss
        boss.configure(new GateInstance(UUID.randomUUID(), look, 0, 0, Level.OVERWORLD, BlockPos.ZERO));
        boss.moveTo(at.x, at.y, at.z, 0f, 0f);
        boss.setYHeadRot(0f);
        float hp = 160 + 70 * sys.rank().tier() + 4 * sys.level();
        boss.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        boss.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(7 + 2.5 * sys.rank().tier() + sys.level() / 8.0);
        boss.setHealth(boss.getMaxHealth());
        boss.setCustomName(Component.translatable("job.beyondsolo.trial.arbiter"));
        boss.addTag(ECHO_TAG);
        boss.setPersistenceRequired();
        level.addFreshEntity(boss);
        s.boss = boss.getUUID();

        freeze(p, true);
        Vec3 start = TrialArena.start(s.center);
        p.teleportTo(level, start.x, start.y, start.z, 180f, 0f);
        Vec3 face = at.add(0, 3.2, 0);
        // over the player's shoulder, pushing in on the Arbiter as it wakes
        send(p, CutscenePayload.glide(start.add(1.6, 2.4, 3.5), at.add(2.5, 2.6, 7.5), face.add(0, -0.5, 0), face, BOSS_INTRO_TICKS - 10));
        sound(level, at, SoundEvents.WARDEN_EMERGE, 1.4f, 0.6f);
        level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 1, at.z, 60, 1.5, 1.5, 1.5, 0.05);
        banner(s, JobTrialPayload.BOSS, Component.translatable("job.beyondsolo.trial.arbiter"), Component.translatable("job.beyondsolo.trial.arbiter_intro"), 100, 100);
    }

    @Nullable
    private static GateBossEntity boss(Session s) {
        if (s.boss == null || s.level == null) return null;
        return s.level.getEntity(s.boss) instanceof GateBossEntity b ? b : null;
    }

    // ================================================================ the verdict

    private static void startVerdict(Session s) {
        setPhase(s, Phase.VERDICT);
        ServerPlayer p = s.player;
        double luck = s.flawless ? JobConfig.FLAWLESS_MULTIPLIER.get() : 1;
        if (s.result == null) s.result = JobAPI.roll(p, luck);
        s.finishAt = JobRevealTimeline.finish(s.result.rare());
        freeze(p, true);
        Vec3 sigil = Vec3.atBottomCenterOf(s.center);
        p.teleportTo(s.level, sigil.x, sigil.y, sigil.z, 180f, 0f);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, s.finishAt + 40, 4, false, false, false));
        p.setHealth(p.getMaxHealth());
        // the camera circles the player, slowly, all through the reveal
        send(p, CutscenePayload.orbit(sigil.add(0, 1.2, 0), 6.5, 1.6, 0.25f, 0.85f, s.finishAt));
        PacketDistributor.sendToPlayer(p, new JobRevealPayload(s.result.ordinal(), s.flawless));
        banner(s, JobTrialPayload.VERDICT, Component.empty(), Component.empty(), 0, 0);
        sound(s.level, sigil, SoundEvents.BEACON_POWER_SELECT, 1f, 0.6f);
    }

    /** The world side of the reveal: the braziers flare one by one, light gathers on the sigil, and a burst at the lock. */
    private static void verdictStep(Session s) {
        ServerLevel level = s.level;
        Vec3 c = Vec3.atBottomCenterOf(s.center);
        int t = s.tick;
        Subclass sub = s.result;
        List<BlockPos> tops = TrialArena.pillarTops(s.center);
        if (t < JobRevealTimeline.LOCK) {
            int lit = Math.min(tops.size(), t / 12);
            for (int i = 0; i < lit; i++) {
                BlockPos top = tops.get(i);
                if (t % 4 == 0) level.sendParticles(dust(sub.base().color(), 1.2f), top.getX() + 0.5, top.getY() + 1.2, top.getZ() + 0.5, 2, 0.15, 0.3, 0.15, 0);
                // streams of light from the lit braziers into the sigil
                if (t % 3 == 0) {
                    double k = level.random.nextDouble();
                    Vec3 from = Vec3.atCenterOf(top).add(0, 1, 0);
                    Vec3 at = from.add(c.add(0, 1.2, 0).subtract(from).scale(k));
                    level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                }
            }
            if (t % 12 == 0 && lit > 0) sound(level, Vec3.atCenterOf(tops.get(lit - 1)), SoundEvents.FIRECHARGE_USE, 0.6f, 0.8f + lit * 0.1f);
            if (t % 2 == 0) {
                double a = t * 0.3;
                level.sendParticles(dust(0xFFFFFF, 0.8f), c.x + Math.cos(a) * 1.4, c.y + 0.1 + t / 120.0, c.z + Math.sin(a) * 1.4, 1, 0, 0, 0, 0);
            }
        }
        if (t == JobRevealTimeline.LOCK) {
            int color = sub.color();
            for (int i = 0; i < 60; i++) {
                double a = i / 60.0 * Math.PI * 2;
                level.sendParticles(dust(color, 1.4f), c.x + Math.cos(a) * 2.5, c.y + 0.2, c.z + Math.sin(a) * 2.5, 1, 0, 0.05, 0, 0);
            }
            level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 1.2, c.z, 80, 0.3, 0.6, 0.3, 0.25);
            fxAt(level, SkillFxPayload.Kind.NOVA, color, c, 9, 0);
            fxAt(level, SkillFxPayload.Kind.HEAL, color, c, 2.5f, 0);
            sound(level, c, SoundEvents.END_PORTAL_FRAME_FILL, 1.2f, 0.8f);
            sound(level, c, SoundEvents.ENCHANTMENT_TABLE_USE, 1f, 0.6f);
            if (sub.rare()) {
                sound(level, c, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
                sound(level, c, SoundEvents.TRIDENT_THUNDER.value(), 1f, 0.7f);
                fxAt(level, SkillFxPayload.Kind.ROAR, 0xFFD166, c, 18, 0);
            }
        }
        if (sub.rare() && t > JobRevealTimeline.LOCK && t < JobRevealTimeline.LOCK + 40 && t % 6 == 0) {
            fxAt(level, SkillFxPayload.Kind.NOVA, t % 12 == 0 ? 0xFFD166 : sub.color(), c, 4 + (t - JobRevealTimeline.LOCK) / 3f, 0);
        }
        if (t > JobRevealTimeline.LOCK && t % 5 == 0) {
            level.sendParticles(dust(sub.color(), 1f), c.x, c.y + 1, c.z, 3, 0.5, 0.9, 0.5, 0);
        }
    }

    /** The player skipped the reveal on their screen. */
    public static void skipReveal(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || s.phase != Phase.VERDICT || s.tick < JobRevealTimeline.LOCK) return;
        s.finishAt = Math.min(s.finishAt, s.tick + 15);
    }

    /** The reveal is over: the job changes and the player goes home. */
    private static void finish(Session s) {
        ServerPlayer p = s.player;
        Subclass sub = s.result;
        send(p, CutscenePayload.stop());
        banner(s, JobTrialPayload.NONE, Component.empty(), Component.empty(), 0, 0);
        freeze(p, false);
        returnHome(p, false);
        JobAPI.changeJob(p, sub);
        QuestEngine.progress(p, JobModule.TRIAL_KEY, 1);
        p.setHealth(p.getMaxHealth());
        Notifications.send(p, new SystemNotification(SystemNotification.Kind.LEVEL_UP,
                Component.translatable("notify.beyondsolo.job_changed.title"),
                Component.translatable("notify.beyondsolo.job_changed.body", sub.base().displayName(), sub.displayName()), 240));
        if (sub.rare()) {
            Component msg = Component.translatable("chat.beyondsolo.job_rare", p.getDisplayName(),
                    sub.displayName().copy().withStyle(st -> st.withColor(TextColor.fromRgb(sub.color())).withBold(true))).withStyle(ChatFormatting.GOLD);
            p.server.getPlayerList().broadcastSystemMessage(msg, false);
        }
        NeoForge.EVENT_BUS.post(new JobEvent.TrialEnded(p, true));
        cleanup(s);
    }

    // ================================================================ failing, leaving

    /** The player fell in the trial: they are thrown out, alive, and may come back later. */
    private static void fail(Session s) {
        ServerPlayer p = s.player;
        send(p, CutscenePayload.stop());
        freeze(p, false);
        p.setHealth(p.getMaxHealth() * 0.5f);
        p.removeAllEffects();
        p.clearFire();
        returnHome(p, true);
        banner(s, JobTrialPayload.FAILED, Component.translatable("job.beyondsolo.trial.failed"),
                Component.translatable("job.beyondsolo.trial.failed_sub", JobConfig.RETRY_MINUTES.get()), 0, 0);
        sound(p.serverLevel(), p.position(), SoundEvents.WITHER_DEATH, 0.3f, 1.4f);
        NeoForge.EVENT_BUS.post(new JobEvent.TrialEnded(p, false));
        cleanup(s);
    }

    /** The player logged out mid-trial. If the verdict was already reached they keep their subclass. */
    private static void abandon(Session s) {
        if (s.phase == Phase.VERDICT && s.result != null) {
            JobAPI.changeJob(s.player, s.result);
            QuestEngine.progress(s.player, JobModule.TRIAL_KEY, 1);
        }
        freeze(s.player, false);
        cleanup(s);
        // inTrial stays set: they are sent home when they log back in
    }

    private static void cleanup(Session s) {
        if (s.level == null) return;
        for (UUID id : s.foes) discard(s, id);
        if (s.boss != null) discard(s, s.boss);
    }

    private static void discard(Session s, UUID id) {
        Entity e = s.level.getEntity(id);
        if (e != null) e.discard();
    }

    /** Sends a player back where they came from. */
    static void returnHome(ServerPlayer p, boolean failed) {
        JobData data = JobAPI.get(p);
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
        JobAPI.leaveTrial(p, failed);
        home.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1, at.z, 60, 0.4, 1, 0.4, 0.2);
    }

    private static void clearArena(ServerLevel level, BlockPos c) {
        AABB box = new AABB(c).inflate(TrialArena.RADIUS + 4, TrialArena.HEIGHT + 4, TrialArena.RADIUS + 4);
        for (Entity e : level.getEntitiesOfClass(Entity.class, box, e -> !(e instanceof Player))) e.discard();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || s.phase == Phase.COUNTDOWN) return;
        event.setCanceled(true);
        if (s.phase == Phase.VERDICT) {
            p.setHealth(p.getMaxHealth());
            return;
        }
        SESSIONS.remove(p.getUUID());
        fail(s);
    }

    @SubscribeEvent
    static void onHurt(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Session s = SESSIONS.get(p.getUUID());
            if (s == null) return;
            // no harm while the camera has the player (intros, the verdict)
            if (s.phase == Phase.INTRO || s.phase == Phase.BOSS_INTRO || s.phase == Phase.BOSS_DOWN || s.phase == Phase.VERDICT) {
                event.setCanceled(true);
                return;
            }
            if (s.phase == Phase.BOSS && s.boss != null && event.getSource().getEntity() != null
                    && s.boss.equals(event.getSource().getEntity().getUUID())) {
                s.flawless = false;
            }
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        JobData data = JobAPI.get(p);
        if (data.inTrial() && !SESSIONS.containsKey(p.getUUID())) {
            freeze(p, false);
            returnHome(p, false);
            info(p, "job_returned");
        }
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

    private static void banner(Session s, int phase, Component title, Component sub, int progress, int goal) {
        PacketDistributor.sendToPlayer(s.player, new JobTrialPayload(phase, title, sub, progress, goal, s.tick));
    }

    private static void send(ServerPlayer p, CutscenePayload shot) {
        PacketDistributor.sendToPlayer(p, shot);
    }

    private static void info(ServerPlayer p, String key, Object... args) {
        Notifications.send(p, SystemNotification.of(SystemNotification.Kind.INFO,
                Component.translatable("notify.beyondsolo." + key + ".title"),
                Component.translatable("notify.beyondsolo." + key + ".body", args)));
    }

    static void fxAt(ServerLevel level, SkillFxPayload.Kind kind, int color, Vec3 at, float size, int duration) {
        PacketDistributor.sendToPlayersNear(level, null, at.x, at.y, at.z, 96,
                new SkillFxPayload(-1, kind, color & 0xFFFFFF, (float) at.x, (float) at.y, (float) at.z, size, duration));
    }

    private static DustParticleOptions dust(int rgb, float scale) {
        return new DustParticleOptions(new org.joml.Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f), scale);
    }

    private static void sound(@Nullable ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        if (level != null) level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    /** For tests: jump a running trial ahead. */
    static void forTests(ServerPlayer player, java.util.function.Consumer<Session> change) {
        Session s = SESSIONS.get(player.getUUID());
        if (s != null) change.accept(s);
    }

    /** Commands: skips to a phase of a running trial (waves, boss, verdict), optionally forcing the result. */
    public static boolean jump(ServerPlayer player, String to, @Nullable Subclass result) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || s.level == null) return false;
        if (result != null) s.result = result;
        for (UUID id : s.foes) discard(s, id);
        s.foes.clear();
        switch (to) {
            case "boss" -> {
                send(player, CutscenePayload.stop());
                freeze(player, false);
                startBoss(s);
            }
            case "verdict" -> {
                if (s.boss != null) discard(s, s.boss);
                startVerdict(s);
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

    private JobTrial() {}
}
