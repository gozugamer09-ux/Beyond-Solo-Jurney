package com.beyondsolo.quest.situation;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.enemy.EnemyTraits;
import com.beyondsolo.enemy.Variant;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestEvent;
import com.beyondsolo.quest.QuestObjective;
import com.beyondsolo.quest.trigger.QuestTriggers;
import com.beyondsolo.util.ParticleGuard;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A pack comes for the player behind a leader: a champion (or an elite with a big enough pack), or a patrol's captain.
 * "Head of the Pack: bring down its leader." When the leader falls by the player's hand the pack's nerve breaks (it is
 * weakened and slowed, some of it scatters) and the quest becomes "Rout": finish what is left before it regroups.
 */
public final class PackLeader extends Situation {
    public static final String HEAD = "head";
    public static final String ROUT = "rout";
    static final String KILL = BeyondSolo.id("pack_leader").toString();
    static final String HEALTH = BeyondSolo.id("pack_health").toString();
    static final String REST = BeyondSolo.id("pack_rest").toString();
    static final String PACK = "pack";
    static final String LOST = "lost";

    public static final double RANGE = 20;
    public static final int HEAD_SECONDS = 180;
    public static final int ROUT_SECONDS = 40;
    public static final double ESCAPE_DISTANCE = 48;

    public PackLeader() {
        super("pack");
    }

    @Override
    public int cooldownTicks() {
        return 20 * 60 * 6;
    }

    @Override
    @Nullable
    public Quest detect(ServerPlayer player, Watch watch) {
        if (QuestTriggers.inEmergency(QuestEngine.data(player))) return null;
        Mob leader = null;
        List<Mob> pack = List.of();
        for (Mob m : Foes.huntersOf(player, RANGE)) {
            int need = need(m);
            if (need <= 0) continue;
            List<Mob> around = packOf(m, player);
            if (around.size() < need) continue;
            if (leader == null || rank(m) > rank(leader)) {
                leader = m;
                pack = around;
            }
        }
        return leader == null ? null : head(player, leader, pack);
    }

    /** How many followers a creature needs around it to lead a pack (0: it never leads one). */
    static int need(Mob m) {
        if (m instanceof PatrollingMonster p && p.isPatrolLeader()) return 2;
        Variant v = EnemyTraits.of(m).variant();
        return v == Variant.CHAMPION ? 2 : v == Variant.ELITE ? 3 : 0;
    }

    private static int rank(Mob m) {
        return EnemyTraits.of(m).variant().ordinal();
    }

    /** The creatures that run with this leader: monsters close to it that hunt the player, or are of its kind. */
    static List<Mob> packOf(Mob leader, ServerPlayer player) {
        return leader.level().getEntitiesOfClass(Mob.class, leader.getBoundingBox().inflate(16), o -> o != leader && o.isAlive()
                && o instanceof Enemy && !Foes.ownedBy(o, player) && (Foes.hunting(o, player) || o.getType() == leader.getType()));
    }

    public Quest head(ServerPlayer player, Mob leader, List<Mob> pack) {
        CompoundTag run = Run.start(this, HEAD, QuestEngine.gameTime(player));
        Run.setTarget(run, leader);
        Component name = Foes.name(leader);
        Run.setName(run, name, player.registryAccess());
        List<UUID> ids = new ArrayList<>();
        for (Mob m : pack) ids.add(m.getUUID());
        Run.putList(run, PACK, ids);
        return quest(player, HEAD, QuestCategory.EMERGENCY, run, name, pack.size())
                .line(KILL, text("obj.leader", name))
                .watch(HEALTH, name, Foes.healthPercent(leader))
                .timeLimit(HEAD_SECONDS * 20)
                .xp(240)
                .penalty(20, 15)
                .build();
    }

    @Override
    public void update(ServerPlayer player, Quest q, Run run) {
        if (run.is(HEAD)) {
            LivingEntity leader = Foes.find(player, run.target());
            if (leader != null) {
                if (run.refreshTargetId(leader)) QuestEngine.sync(player);
                Situations.set(player, q, HEALTH, Foes.healthPercent(leader));
            }
            boolean near = leader != null && leader.distanceToSqr(player) <= ESCAPE_DISTANCE * ESCAPE_DISTANCE;
            int lost = near ? 0 : run.count(LOST) + 1;
            run.setCount(LOST, lost);
            if (lost >= 10) QuestEngine.fail(player, q, QuestEvent.FailReason.FLED, outcome("leader_escaped", run.name(player.registryAccess())));
            return;
        }
        // the rout: done when nothing of the pack is left close by
        if (alive(player, run).isEmpty()) QuestEngine.complete(player, q, outcome("pack_broken"), q.rewards());
    }

    private static List<Mob> alive(ServerPlayer player, Run run) {
        List<Mob> out = new ArrayList<>();
        for (UUID id : run.list(PACK)) {
            if (Foes.find(player, id) instanceof Mob m && m.distanceToSqr(player) < 40 * 40) out.add(m);
        }
        return out;
    }

    @Override
    public void runEntityDied(ServerPlayer player, Quest q, Run run, LivingEntity dead, DamageSource source) {
        if (!run.is(HEAD) || !dead.getUUID().equals(run.target())) return;
        Component name = run.name(player.registryAccess());
        if (Foes.playerBehind(source.getEntity()) != player) {
            QuestEngine.complete(player, q, outcome("leader_fell", name), q.rewards().partial(0.5f));
            return;
        }
        // the leader is down: the pack's nerve breaks
        List<Mob> rest = new ArrayList<>();
        for (UUID id : run.list(PACK)) {
            if (Foes.find(player, id) instanceof Mob m && m.distanceToSqr(dead) < 40 * 40) rest.add(m);
        }
        for (Mob m : rest) falter(m, dead.position());
        if (dead.level() instanceof ServerLevel level) {
            level.playSound(null, dead.blockPosition(), SoundEvents.RAVAGER_STUNNED, SoundSource.HOSTILE, 1f, 0.8f);
        }
        if (rest.isEmpty()) {
            q.setOutcome(outcome("pack_broken"));
            Situations.set(player, q, KILL, 1);
            return;
        }
        CompoundTag next = run.next(ROUT);
        List<UUID> ids = new ArrayList<>();
        for (Mob m : rest) {
            ids.add(m.getUUID());
            Situations.markTarget(m);
        }
        Run.putList(next, PACK, ids);
        next.remove(Run.TARGET);
        next.remove(Run.TARGET_ID);
        Quest rout = quest(player, ROUT, QuestCategory.EMERGENCY, next, name)
                .custom(REST, rest.size(), text("obj.rest"))
                .timeLimit(ROUT_SECONDS * 20)
                .xp(160)
                .build();
        Situations.branch(player, q, rout, true);
    }

    /** One of the pack loses its nerve: weakened and slowed, and some of them run. */
    static void falter(Mob m, Vec3 from) {
        m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 20, 0));
        m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 20, 0));
        if (m.level() instanceof ServerLevel level) {
            ParticleGuard.send(level, ParticleTypes.SMOKE, m.getX(), m.getY() + m.getBbHeight(), m.getZ(), 6, 0.2, 0.2, 0.2, 0.01);
        }
        if (m.getRandom().nextBoolean()) {
            Vec3 away = m.position().subtract(from).normalize().scale(14).add(m.position());
            m.setTarget(null);
            m.getNavigation().moveTo(away.x, away.y, away.z, 1.3);
        }
    }

    @Override
    public void killed(ServerPlayer player, LivingEntity victim, Watch watch) {
        Quest q = Situations.running(player, this);
        Run run = q == null ? null : Run.of(q);
        if (run == null || !run.is(ROUT) || !run.list(PACK).contains(victim.getUUID())) return;
        QuestObjective o = q.objectives().get(Situations.objective(q, REST));
        if (o.progress() + 1 >= o.required()) q.setOutcome(outcome("pack_broken"));
        Situations.add(player, q, REST, 1);
    }

    @Override
    public boolean expiring(ServerPlayer player, Quest q, Run run, QuestEvent.FailReason reason) {
        if (!run.is(ROUT)) {
            if (reason == QuestEvent.FailReason.TIME) {
                QuestEngine.fail(player, q, reason, outcome("pack_still_hunts"));
                return true;
            }
            return false;
        }
        // the leader was the point: whatever is left scatters
        QuestEngine.complete(player, q, outcome("scattered"), q.rewards().partial(q.fraction()));
        return true;
    }
}
