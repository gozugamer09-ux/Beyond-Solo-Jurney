package com.beyondsolo.quest.situation;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.breaks.DungeonBreaks;
import com.beyondsolo.crafting.MonsterCoreItem;
import com.beyondsolo.enemy.Affinity;
import com.beyondsolo.enemy.EnemyTraits;
import com.beyondsolo.enemy.HiddenLevel;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestData;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestEvent;
import com.beyondsolo.quest.QuestObjective;
import com.beyondsolo.quest.QuestRewards;
import com.beyondsolo.quest.trigger.QuestTriggers;
import com.beyondsolo.rune.Runes;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.tower.TowerAPI;
import com.beyondsolo.util.ParticleGuard;
import com.beyondsolo.worldboss.WorldBosses;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.warden.Warden;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A creature far stronger than the player (a red name) comes for them, and the System's first word is "Survive". If
 * the player turns the fight around and breaks it (its health under {@link #BREAKING}), the quest becomes "Annihilate".
 * A wounded creature left standing too long recovers in a fury ("Survive its fury"), and once the fury is spent it is
 * Annihilate again. A player who runs is let go, but the creature remembers them: later it tracks them down as their
 * nemesis, a little stronger each time it comes back, until one of them falls.
 *
 * <pre>
 *   survive --its health breaks--> annihilate --clock runs out--> fury --held out--> annihilate ...
 *      |  held out: done                 |  slain: done (cores)        \ slain: done
 *      \  ran: half paid, nemesis later  \  ran: failed, nemesis later
 *   nemesis (a follow-up): slay it, or run again and it comes back stronger
 * </pre>
 */
public final class OverwhelmingFoe extends Situation {
    public static final String SURVIVE = "survive";
    public static final String ANNIHILATE = "annihilate";
    public static final String FURY = "fury";
    public static final String NEMESIS = "nemesis";
    /** Follow-up kind: the creature the player fled from comes back for them. */
    public static final String NEMESIS_FOLLOW_UP = BeyondSolo.id("nemesis").toString();

    static final String HOLD = BeyondSolo.id("foe_hold").toString();
    static final String KILL = BeyondSolo.id("foe_kill").toString();
    static final String HEALTH = BeyondSolo.id("foe_health").toString();

    /** How close a red-named creature hunting the player has to be. */
    public static final double RANGE = 16;
    /**
     * A creature that is not one of the System's (it has no coloured name to warn the player) has to be this much
     * stronger to count.
     */
    public static final double PLAIN_RATIO = 2.0;
    /** The player is out of the fight beyond this distance, or when the creature stops hunting them. */
    public static final double ESCAPE_DISTANCE = 40;
    /** Seconds out of the fight before the System counts it as an escape. */
    public static final int ESCAPE_SECONDS = 12;
    public static final int SURVIVE_SECONDS = 60;
    public static final int ANNIHILATE_SECONDS = 90;
    public static final int FURY_SECONDS = 25;
    /** At or under this share of its health the creature is breaking: Survive becomes Annihilate. */
    public static final float BREAKING = 0.4f;
    /** How many times a wounded creature can recover in a fury before the System calls the fight lost. */
    public static final int MAX_FURIES = 2;
    /** Share of its health a creature gets back when it recovers in a fury. */
    public static final float RECOVER = 0.3f;
    /** How many times a nemesis comes back before it loses the trail for good. */
    public static final int MAX_RETURNS = 3;
    /** How much stronger (health and damage) a nemesis is each time it comes back. */
    public static final float GROWTH = 0.2f;

    static final String LOST = "lost";
    static final String RATIO = "ratio";
    static final String RETURNS = "returns";
    static final String BODY = "body";
    /** On the creature: when it last recovered in a fury (game time). */
    static final String ROUSED = "beyondsolo_roused";

    /** The creatures of running fights as they were last seen (their return needs them), by creature. Memory only. */
    private static final Map<UUID, CompoundTag> SEEN = new HashMap<>();

    public OverwhelmingFoe() {
        super("foe");
        FollowUps.register(NEMESIS_FOLLOW_UP, this::returnAsNemesis);
    }

    @Override
    public int cooldownTicks() {
        return 20 * 60 * 5;
    }

    // ------------------------------------------------------------------ noticing it

    @Override
    @Nullable
    public Quest detect(ServerPlayer player, Watch watch) {
        if (QuestTriggers.inEmergency(QuestEngine.data(player))) return null;
        Mob best = null;
        double bestRatio = 0;
        for (Mob m : Foes.huntersOf(player, RANGE)) {
            double r = Foes.ratio(m, player);
            double need = HiddenLevel.has(m) ? HiddenLevel.Threat.FAR_FROM : PLAIN_RATIO;
            if (r < need || r <= bestRatio || !player.hasLineOfSight(m)) continue;
            best = m;
            bestRatio = r;
        }
        return best == null ? null : survive(player, best, bestRatio);
    }

    /** The first quest, about this creature (also for /system situation start). */
    public Quest survive(ServerPlayer player, LivingEntity foe, double ratio) {
        CompoundTag run = Run.start(this, SURVIVE, QuestEngine.gameTime(player));
        Run.setTarget(run, foe);
        Run.setName(run, Foes.name(foe), player.registryAccess());
        run.putDouble(RATIO, ratio);
        remember(foe);
        Component name = Foes.name(foe);
        return quest(player, SURVIVE, QuestCategory.EMERGENCY, run, name)
                .hold(HOLD, SURVIVE_SECONDS, text("obj.survive"))
                .watch(HEALTH, name, Foes.healthPercent(foe))
                .xp(200).mana(40)
                .penalty(25, 15)
                .build();
    }

    private Quest annihilate(ServerPlayer player, Component name, int health, CompoundTag run, @Nullable LivingEntity foe) {
        double ratio = Math.max(1, Math.min(4, run.getDouble(RATIO)));
        Affinity affinity = foe == null ? Affinity.NONE : EnemyTraits.of(foe).affinity();
        return quest(player, ANNIHILATE, QuestCategory.EMERGENCY, run, name)
                .line(KILL, text("obj.annihilate", name))
                .watch(HEALTH, name, health)
                .timeLimit(ANNIHILATE_SECONDS * 20)
                .xp(Math.round(260 * ratio)).statPoints(1)
                .item(MonsterCoreItem.of(SystemAPI.get(player).rank(), false, affinity, 2))
                .penalty(25, 15)
                .build();
    }

    private Quest fury(ServerPlayer player, Component name, int health, CompoundTag run) {
        return quest(player, FURY, QuestCategory.EMERGENCY, run, name)
                .hold(HOLD, FURY_SECONDS, text("obj.fury"))
                .watch(HEALTH, name, health)
                .xp(140)
                .penalty(25, 15)
                .build();
    }

    private Quest nemesis(ServerPlayer player, Component name, int health, CompoundTag run, int returns) {
        PlayerRank rank = SystemAPI.get(player).rank();
        PlayerRank stone = PlayerRank.values()[Math.min(PlayerRank.values().length - 1, rank.ordinal() + (returns > 1 ? 1 : 0))];
        return quest(player, NEMESIS, QuestCategory.EMERGENCY, run, name, returns)
                .line(KILL, text("obj.annihilate", name))
                .watch(HEALTH, name, health)
                .xp(420 + 160L * returns).statPoints(1)
                .item(Runes.sealed(stone))
                .penalty(30, 20)
                .build();
    }

    // ------------------------------------------------------------------ the fight

    @Override
    public void update(ServerPlayer player, Quest q, Run run) {
        LivingEntity foe = Foes.find(player, run.target());
        if (foe != null) {
            if (run.refreshTargetId(foe)) QuestEngine.sync(player);
            remember(foe);
            Situations.set(player, q, HEALTH, Foes.healthPercent(foe));
            if (!Situations.isRunning(player, q)) return;
        }
        // still in the fight? out of reach, or no longer hunting the player from any distance, counts towards an escape
        boolean engaged = foe != null && foe.distanceToSqr(player) <= ESCAPE_DISTANCE * ESCAPE_DISTANCE
                && (!(foe instanceof Mob m) || m.getTarget() == player || foe.distanceToSqr(player) < 12 * 12);
        int lost = engaged ? 0 : run.count(LOST) + 1;
        run.setCount(LOST, lost);
        if (lost >= (foe == null ? 4 : ESCAPE_SECONDS)) {
            escaped(player, q, run, foe);
            return;
        }
        if (foe == null || !engaged) return;
        switch (run.stage()) {
            case SURVIVE -> {
                if (foe.getHealth() <= foe.getMaxHealth() * BREAKING) {
                    toAnnihilate(player, q, run, foe, false);
                    return;
                }
                QuestObjective o = q.objectives().get(Situations.objective(q, HOLD));
                if (o.progress() + 1 >= o.required()) q.setOutcome(outcome("survived", run.name(player.registryAccess())));
                Situations.add(player, q, HOLD, 1);
            }
            case FURY -> {
                QuestObjective o = q.objectives().get(Situations.objective(q, HOLD));
                if (o.progress() + 1 >= o.required()) spent(player, q, run, foe);
                else Situations.add(player, q, HOLD, 1);
            }
            default -> {
            }
        }
    }

    /** It is breaking (or its fury is spent): finish it. */
    private void toAnnihilate(ServerPlayer player, Quest q, Run run, LivingEntity foe, boolean payFrom) {
        CompoundTag next = run.next(ANNIHILATE);
        next.putInt(LOST, 0);
        Component name = run.name(player.registryAccess());
        Situations.branch(player, q, annihilate(player, name, Foes.healthPercent(foe), next, foe), payFrom);
    }

    /**
     * Whether the creature may recover now: not if it already did for another hunter's quest within one fury, so a
     * party hunting it together doesn't heal it once each.
     */
    private static boolean rouse(LivingEntity foe) {
        long now = foe.level().getGameTime();
        long last = foe.getPersistentData().getLong(ROUSED);
        if (last > 0 && now - last < FURY_SECONDS * 20L) return false;
        foe.getPersistentData().putLong(ROUSED, now);
        return true;
    }

    /** The fury is spent: the creature staggers, and the player is told to finish it. Surviving it is paid. */
    private void spent(ServerPlayer player, Quest q, Run run, LivingEntity foe) {
        foe.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 10, 0));
        foe.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 10, 1));
        if (foe.level() instanceof ServerLevel level) {
            ParticleGuard.send(level, ParticleTypes.SMOKE, foe.getX(), foe.getY() + foe.getBbHeight() * 0.6, foe.getZ(), 18, 0.4, 0.4, 0.4, 0.02);
        }
        toAnnihilate(player, q, run, foe, true);
    }

    @Override
    public boolean expiring(ServerPlayer player, Quest q, Run run, QuestEvent.FailReason reason) {
        if (!run.is(ANNIHILATE) || reason != QuestEvent.FailReason.TIME) return false;
        LivingEntity foe = Foes.find(player, run.target());
        Component name = run.name(player.registryAccess());
        if (foe == null) return false;
        if (run.loops() >= MAX_FURIES) {
            QuestEngine.fail(player, q, QuestEvent.FailReason.TIME, outcome("outlasted", name));
            return true;
        }
        // left standing too long, it recovers, and it is furious (once for everyone hunting it; a boss fights by its own rules)
        if (!Foes.isBoss(foe) && rouse(foe)) {
            foe.heal(foe.getMaxHealth() * RECOVER);
            foe.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, FURY_SECONDS * 20, 0));
            foe.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, FURY_SECONDS * 20, 0));
        }
        if (foe.level() instanceof ServerLevel level) {
            ParticleGuard.send(level, ParticleTypes.ANGRY_VILLAGER, foe.getX(), foe.getY() + foe.getBbHeight(), foe.getZ(), 8, 0.5, 0.3, 0.5, 0.0);
            level.playSound(null, foe.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.2f, 0.8f);
        }
        CompoundTag next = run.next(FURY);
        next.putInt(Run.LOOPS, run.loops() + 1);
        next.putInt(LOST, 0);
        Situations.branch(player, q, fury(player, name, Foes.healthPercent(foe), next), false);
        return true;
    }

    @Override
    public void runEntityDied(ServerPlayer player, Quest q, Run run, LivingEntity dead, DamageSource source) {
        if (!dead.getUUID().equals(run.target())) return;
        SEEN.remove(dead.getUUID());
        boolean mine = Foes.killedBy(player, source, dead);
        Component name = run.name(player.registryAccess());
        switch (run.stage()) {
            case SURVIVE -> {
                if (mine) {
                    // it never even got to break: everything Annihilate would have paid
                    QuestRewards all = annihilate(player, name, 0, run.next(ANNIHILATE), dead).rewards();
                    QuestEngine.complete(player, q, outcome("felled", name), all);
                } else {
                    QuestEngine.complete(player, q, outcome("it_fell", name));
                }
            }
            case FURY -> {
                if (mine) QuestEngine.complete(player, q, outcome("annihilated", name), annihilate(player, name, 0, run.next(ANNIHILATE), dead).rewards());
                else QuestEngine.complete(player, q, outcome("someone_else", name), q.rewards().partial(0.5f));
            }
            case ANNIHILATE, NEMESIS -> {
                if (mine) {
                    boolean nemesis = run.is(NEMESIS);
                    q.setOutcome(outcome(nemesis ? "nemesis_slain" : "annihilated", name));
                    Situations.set(player, q, KILL, 1);
                    if (nemesis) {
                        FollowUps.data(player).countNemesis();
                        Secrets.reveal(player, Secrets.NEMESIS_SLAIN);
                    }
                } else {
                    QuestEngine.complete(player, q, outcome("someone_else", name), q.rewards().partial(0.5f));
                }
            }
            default -> {
            }
        }
    }

    @Override
    public void playerDied(ServerPlayer player, Quest q, Run run, DamageSource source) {
        QuestEngine.fail(player, q, QuestEvent.FailReason.DIED, outcome("fallen", run.name(player.registryAccess())));
    }

    @Override
    public void ended(ServerPlayer player, Quest q, Run run, @Nullable QuestEvent.FailReason reason) {
        UUID target = run.target();
        if (target != null) SEEN.remove(target);
    }

    // ------------------------------------------------------------------ running, and what comes of it

    /** The player got away. Survival is still survival (half paid); walking away from a breaking foe is a failure. */
    private void escaped(ServerPlayer player, Quest q, Run run, @Nullable LivingEntity foe) {
        Component name = run.name(player.registryAccess());
        boolean hunted = scheduleReturn(player, run, foe);
        switch (run.stage()) {
            case SURVIVE, FURY -> QuestEngine.complete(player, q, outcome(hunted ? "escaped_hunted" : "escaped", name), q.rewards().partial(0.5f));
            case ANNIHILATE -> QuestEngine.fail(player, q, QuestEvent.FailReason.FLED, outcome(hunted ? "let_go_hunted" : "let_go", name));
            case NEMESIS -> QuestEngine.fail(player, q, QuestEvent.FailReason.FLED, outcome(hunted ? "nemesis_again" : "nemesis_lost", name));
            default -> QuestEngine.fail(player, q, QuestEvent.FailReason.FLED);
        }
    }

    /**
     * It will come for them later: the creature as it is now is kept with a follow-up, and it withdraws (it leaves no
     * twin behind). Returns false when it can't come back (a gate's boss, a break's creature, a world boss, follow-ups
     * turned off, or it has come back too often).
     */
    private boolean scheduleReturn(ServerPlayer player, Run run, @Nullable LivingEntity foe) {
        if (!SituationConfig.followUps()) return false;
        int returns = run.count(RETURNS);
        if (returns >= MAX_RETURNS) return false;
        UUID target = run.target();
        CompoundTag body = foe != null ? snapshot(foe) : target == null ? null : SEEN.get(target);
        if (body == null) return false;
        CompoundTag data = new CompoundTag();
        data.put(BODY, body);
        data.putInt(RETURNS, returns);
        data.putDouble(RATIO, run.tag().getDouble(RATIO));
        data.putString(Run.NAME, run.tag().getString(Run.NAME));
        FollowUps.schedule(player, NEMESIS_FOLLOW_UP, SituationConfig.nemesisDelay(player.getRandom()), data);
        if (target != null) SEEN.remove(target);
        if (foe != null && foe.level() instanceof ServerLevel level) {
            ParticleGuard.send(level, ParticleTypes.LARGE_SMOKE, foe.getX(), foe.getY() + foe.getBbHeight() * 0.5, foe.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
            foe.discard();
        }
        return true;
    }

    /** The follow-up: the creature finds the player again. False (try again later) while it can't happen. */
    private boolean returnAsNemesis(ServerPlayer player, CompoundTag data) {
        if (!player.isAlive() || !SystemAPI.isAwakened(player) || QuestTriggers.quiet(player)) return false;
        QuestData quests = QuestEngine.data(player);
        if (!quests.started() || QuestTriggers.inEmergency(quests) || quests.has(template(NEMESIS))) return false;
        // it can't follow them into a gate or the Citadel: it waits until they are out
        if (!(player.level() instanceof ServerLevel level) || BeyondRank.gateOf(player) != null || GateService.inTransit(player)
                || TowerAPI.inCitadel(player)) return false;
        // out of sight at first if it can be, closer if the ground allows nothing else
        BlockPos at = Foes.spotAround(level, player.blockPosition(), 14, 22);
        if (at == null) at = Foes.spotAround(level, player.blockPosition(), 7, 13);
        if (at == null) return false;
        CompoundTag body = data.getCompound(BODY).copy();
        Entity made = EntityType.create(body, level).orElse(null);
        if (!(made instanceof Mob mob)) return true; // it can't be made any more: let it go
        int returns = data.getInt(RETURNS) + 1;
        mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
        grow(mob, returns);
        mob.setHealth(mob.getMaxHealth());
        mob.setPersistenceRequired();
        if (!level.addFreshEntity(mob)) return false;
        mob.setTarget(player);
        if (HiddenLevel.has(mob)) HiddenLevel.refresh(mob);
        ParticleGuard.send(level, ParticleTypes.SCULK_SOUL, mob.getX(), mob.getY() + 0.5, mob.getZ(), 14, 0.5, 0.5, 0.5, 0.02);

        CompoundTag run = Run.start(this, NEMESIS, QuestEngine.gameTime(player));
        Run.setTarget(run, mob);
        run.putString(Run.NAME, data.getString(Run.NAME));
        run.putDouble(RATIO, Math.max(data.getDouble(RATIO), Foes.ratio(mob, player)));
        run.putInt(RETURNS, returns);
        Component name = nameOf(run, player, mob);
        Quest q = nemesis(player, name, Foes.healthPercent(mob), run, returns);
        if (!Situations.begin(player, this, q)) {
            mob.discard();
            return false;
        }
        remember(mob);
        Notifications.send(player, new SystemNotification(SystemNotification.Kind.ALERT,
                text("nemesis.alert.title"), text("nemesis.alert.body", name), 20 * 7));
        level.playSound(null, mob.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.4f, 1.25f);
        return true;
    }

    private static Component nameOf(CompoundTag run, ServerPlayer player, LivingEntity mob) {
        String json = run.getString(Run.NAME);
        if (!json.isEmpty()) {
            try {
                Component c = Component.Serializer.fromJson(json, player.registryAccess());
                if (c != null) return c;
            } catch (RuntimeException ignored) {
                // fall back to its name now
            }
        }
        return Foes.name(mob);
    }

    /** Each return makes it stronger: health and damage, {@link #GROWTH} a time. */
    static void grow(Mob mob, int returns) {
        set(mob.getAttribute(Attributes.MAX_HEALTH), "nemesis_health", GROWTH * returns);
        set(mob.getAttribute(Attributes.ATTACK_DAMAGE), "nemesis_damage", GROWTH * returns);
    }

    private static void set(@Nullable AttributeInstance inst, String id, double amount) {
        if (inst == null) return;
        inst.removeModifier(BeyondSolo.id(id));
        inst.addPermanentModifier(new AttributeModifier(BeyondSolo.id(id), amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** Keeps how the creature looks now, for its return. */
    private static void remember(LivingEntity foe) {
        if (!SEEN.containsKey(foe.getUUID())) {
            CompoundTag body = snapshot(foe);
            if (body != null) SEEN.put(foe.getUUID(), body);
        }
    }

    /**
     * The creature as a fresh copy would be made from it: its kind, attributes, traits and level, but not who or where
     * it is. Null for a creature that can't come back after the player (a boss, a break's creature, a monarch's
     * soldier, someone's summon).
     */
    @Nullable
    static CompoundTag snapshot(LivingEntity e) {
        if (!(e instanceof Mob) || Foes.isBoss(e) || e instanceof Warden || DungeonBreaks.fromBreak(e)
                || e.getPersistentData().hasUUID(WorldBosses.ARMY_TAG) || Foes.playerBehind(e) != null) return null;
        CompoundTag body = e.saveWithoutId(new CompoundTag());
        for (String k : List.of("UUID", "Pos", "Motion", "Rotation", "Passengers", "leash", "Leash", "Brain", "HurtByTimestamp", "DeathTime",
                "HurtTime", "Fire", "FallDistance", "active_effects", "Health", "AbsorptionAmount", "PortalCooldown", "OnGround", "Air",
                "CustomNameVisible", "PersistenceRequired", "DropChances")) {
            body.remove(k);
        }
        CompoundTag kept = body.getCompound("NeoForgeData");
        for (String k : List.of(GateService.MOB_TAG, GateService.ELEMENT_TAG, DungeonBreaks.BREAK_TAG, Situations.TARGET_MARK)) kept.remove(k);
        body.put("NeoForgeData", kept);
        body.putString("id", EntityType.getKey(e.getType()).toString());
        return body;
    }
}
