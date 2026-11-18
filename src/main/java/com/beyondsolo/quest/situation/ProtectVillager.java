package com.beyondsolo.quest.situation;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.npc.NpcNames;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestEvent;
import com.beyondsolo.quest.QuestObjective;
import com.beyondsolo.quest.trigger.QuestTriggers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A villager is attacked within sight of the player. "Protect them": drive the attackers off while their health bar
 * is watched. Saving them is remembered (the villager trusts the player more, and thanks them with what they have);
 * walking away is remembered too (the village saw). If they die, the quest becomes "Avenge them": bring down their
 * killer before it gets away.
 */
public final class ProtectVillager extends Situation {
    public static final String PROTECT = "protect";
    public static final String AVENGE = "avenge";
    static final String ATTACKERS = BeyondSolo.id("protect_attackers").toString();
    static final String HEALTH = BeyondSolo.id("protect_health").toString();
    static final String KILL = BeyondSolo.id("avenge_kill").toString();
    static final String KILLER_HEALTH = BeyondSolo.id("avenge_health").toString();
    static final String LIST = "attackers";
    static final String VILLAGER = "villager";
    static final String VILLAGER_NAME = "villagerName";
    static final String LOST = "lost";

    /** How close the player has to be to notice. */
    public static final double NOTICE = 24;
    public static final double LEAVE = 48;
    public static final int PROTECT_SECONDS = 120;
    public static final int AVENGE_SECONDS = 60;

    public ProtectVillager() {
        super("protect");
    }

    @Override
    public int cooldownTicks() {
        return 20 * 60 * 3;
    }

    /** A monster hurt a villager: every awakened player close enough is asked to protect them. */
    static void alarm(AbstractVillager villager, LivingEntity hurter) {
        if (!villager.isAlive() || !(villager.level() instanceof ServerLevel level)) return;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(villager) > NOTICE * NOTICE || Situations.running(p, Situations.PROTECT) != null) continue;
            Situations.tryStart(p, Situations.PROTECT, () -> Situations.PROTECT.protect(p, villager, hurter));
        }
    }

    @Nullable
    public Quest protect(ServerPlayer player, AbstractVillager villager, @Nullable LivingEntity hurter) {
        if (QuestTriggers.inEmergency(QuestEngine.data(player))) return null;
        List<Mob> attackers = attackersOf(villager, hurter);
        if (attackers.isEmpty()) return null;
        CompoundTag run = Run.start(this, PROTECT, QuestEngine.gameTime(player));
        Run.setTarget(run, villager);
        Run.setWard(run, true);
        Component name = NpcNames.name(villager);
        Run.setName(run, name, player.registryAccess());
        run.putString(VILLAGER_NAME, run.getString(Run.NAME));
        run.putUUID(VILLAGER, villager.getUUID());
        List<UUID> ids = new ArrayList<>();
        for (Mob m : attackers) {
            ids.add(m.getUUID());
            Situations.markTarget(m);
        }
        Run.putList(run, LIST, ids);
        return quest(player, PROTECT, QuestCategory.EMERGENCY, run, name)
                .custom(ATTACKERS, attackers.size(), text("obj.attackers"))
                .watch(HEALTH, name, Foes.healthPercent(villager))
                .rule(text("obj.keep_alive", name))
                .timeLimit(PROTECT_SECONDS * 20)
                .xp(150)
                .penalty(15, 10)
                .build();
    }

    /** The monsters going for this villager (the one that hurt them first). */
    static List<Mob> attackersOf(LivingEntity villager, @Nullable LivingEntity hurter) {
        List<Mob> out = new ArrayList<>(villager.level().getEntitiesOfClass(Mob.class, villager.getBoundingBox().inflate(16),
                m -> m.isAlive() && m instanceof Enemy && m.getTarget() == villager));
        if (hurter instanceof Mob m && m.isAlive() && !out.contains(m)) out.add(0, m);
        return out;
    }

    @Override
    public void update(ServerPlayer player, Quest q, Run run) {
        LivingEntity watched = Foes.find(player, run.target());
        if (watched != null) {
            if (run.refreshTargetId(watched)) QuestEngine.sync(player);
            Situations.set(player, q, run.is(PROTECT) ? HEALTH : KILLER_HEALTH, Foes.healthPercent(watched));
            if (!Situations.isRunning(player, q)) return;
        }
        boolean near = watched != null && watched.distanceToSqr(player) <= LEAVE * LEAVE;
        int lost = near ? 0 : run.count(LOST) + 1;
        run.setCount(LOST, lost);
        if (run.is(PROTECT)) {
            if (lost >= 6) {
                QuestEngine.fail(player, q, QuestEvent.FailReason.FLED, outcome("left_them", run.name(player.registryAccess())));
                return;
            }
            if (watched != null && attackersLeft(player, run, watched) == 0) {
                QuestEngine.complete(player, q, outcome("saved", run.name(player.registryAccess())));
            }
        } else if (lost >= 10) {
            QuestEngine.fail(player, q, QuestEvent.FailReason.FLED, outcome("killer_escaped", villagerName(player, run)));
        }
    }

    /** Attackers still alive and still around the villager. */
    private static int attackersLeft(ServerPlayer player, Run run, LivingEntity villager) {
        int n = 0;
        for (UUID id : run.list(LIST)) {
            LivingEntity m = Foes.find(player, id);
            if (m != null && m.distanceToSqr(villager) < 24 * 24) n++;
        }
        return n;
    }

    @Override
    public void killed(ServerPlayer player, LivingEntity victim, Watch watch) {
        Quest q = Situations.running(player, this);
        Run run = q == null ? null : Run.of(q);
        if (run == null || !run.is(PROTECT) || !run.list(LIST).contains(victim.getUUID())) return;
        QuestObjective o = q.objectives().get(Situations.objective(q, ATTACKERS));
        if (o.progress() + 1 >= o.required()) q.setOutcome(outcome("saved", run.name(player.registryAccess())));
        Situations.add(player, q, ATTACKERS, 1);
    }

    @Override
    public void runEntityDied(ServerPlayer player, Quest q, Run run, LivingEntity dead, DamageSource source) {
        if (!dead.getUUID().equals(run.target())) return;
        if (run.is(PROTECT)) {
            Component name = run.name(player.registryAccess());
            if (!(source.getEntity() instanceof LivingEntity killer) || killer instanceof Player || !killer.isAlive()) {
                QuestEngine.fail(player, q, QuestEvent.FailReason.BROKEN, outcome("lost", name));
                return;
            }
            // they are gone: their killer is not getting away with it
            CompoundTag next = run.next(AVENGE);
            Run.setTarget(next, killer);
            Run.setWard(next, false);
            Component killerName = Foes.name(killer);
            Run.setName(next, killerName, player.registryAccess());
            next.putInt(LOST, 0);
            Quest avenge = quest(player, AVENGE, QuestCategory.EMERGENCY, next, name, killerName)
                    .line(KILL, text("obj.avenge", killerName))
                    .watch(KILLER_HEALTH, killerName, Foes.healthPercent(killer))
                    .timeLimit(AVENGE_SECONDS * 20)
                    .xp(110)
                    .penalty(10, 10)
                    .build();
            Situations.branch(player, q, avenge, false);
            return;
        }
        Component villager = villagerName(player, run);
        if (Foes.playerBehind(source.getEntity()) == player) {
            q.setOutcome(outcome("avenged", villager));
            Situations.set(player, q, KILL, 1);
        } else {
            QuestEngine.complete(player, q, outcome("avenged_by_other", villager), q.rewards().partial(0.5f));
        }
    }

    @Override
    public boolean expiring(ServerPlayer player, Quest q, Run run, QuestEvent.FailReason reason) {
        if (reason != QuestEvent.FailReason.TIME) return false;
        if (run.is(AVENGE)) QuestEngine.fail(player, q, reason, outcome("killer_escaped", villagerName(player, run)));
        else QuestEngine.fail(player, q, reason, outcome("too_slow_protect", run.name(player.registryAccess())));
        return true;
    }

    @Override
    public void ended(ServerPlayer player, Quest q, Run run, @Nullable QuestEvent.FailReason reason) {
        if (!run.is(PROTECT)) return;
        LivingEntity villager = Foes.find(player, run.target());
        if (reason == null && villager instanceof AbstractVillager v) {
            thank(player, v, run);
        } else if (reason == QuestEvent.FailReason.FLED || reason == QuestEvent.FailReason.TIME) {
            // the village saw the hunter walk away
            for (Villager other : player.level().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(48))) {
                other.getGossips().add(player.getUUID(), GossipType.MINOR_NEGATIVE, 10);
            }
        }
    }

    /** Saved: the villager remembers it (better prices) and gives the hunter what they can spare. */
    private static void thank(ServerPlayer player, AbstractVillager v, Run run) {
        if (v instanceof Villager villager) {
            villager.getGossips().add(player.getUUID(), GossipType.MAJOR_POSITIVE, 10);
            villager.getGossips().add(player.getUUID(), GossipType.MINOR_POSITIVE, 25);
        }
        int emeralds = 1 + v.getRandom().nextInt(3) + run.list(LIST).size() / 2;
        ItemStack gift = new ItemStack(Items.EMERALD, emeralds);
        if (!player.getInventory().add(gift) && !gift.isEmpty()) player.drop(gift, false);
        v.playSound(SoundEvents.VILLAGER_CELEBRATE, 1f, 1f);
        player.level().playSound(null, v.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
        Notifications.send(player, new SystemNotification(SystemNotification.Kind.REWARD, NpcNames.full(v),
                Situations.PROTECT.text("thanks", emeralds), 20 * 6));
    }

    private static Component villagerName(ServerPlayer player, Run run) {
        String json = run.tag().getString(VILLAGER_NAME);
        if (!json.isEmpty()) {
            try {
                Component c = Component.Serializer.fromJson(json, player.registryAccess());
                if (c != null) return c;
            } catch (RuntimeException ignored) {
                // unreadable: fall through
            }
        }
        return run.name(player.registryAccess());
    }
}
