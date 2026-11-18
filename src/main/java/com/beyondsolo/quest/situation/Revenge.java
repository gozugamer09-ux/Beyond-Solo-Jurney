package com.beyondsolo.quest.situation;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * A creature worth remembering killed the player. It does not wander off: it waits where they fell. When they are back
 * on their feet the System offers "Unfinished Business": go back and bring it down. Its marker follows it once they are
 * close; if it is gone when they get there, so is the quest.
 */
public final class Revenge extends Situation {
    public static final String HUNT = "hunt";
    /** Follow-up kind: the offer after respawning. */
    public static final String FOLLOW_UP = BeyondSolo.id("revenge").toString();
    static final String KILL = BeyondSolo.id("revenge_kill").toString();
    static final String HEALTH = BeyondSolo.id("revenge_health").toString();
    static final String AT = "at";
    static final String DIM = "dim";
    static final String POS = "pos";
    static final String MISSING = "missing";

    public static final int MINUTES = 15;
    /** An offer that could not be made within this long after the death is not made at all. */
    public static final long STALE = 20 * 60 * 5;

    public Revenge() {
        super("revenge");
        FollowUps.register(FOLLOW_UP, this::offerRevenge);
    }

    @Override
    public int cooldownTicks() {
        return 20 * 60 * 2;
    }

    @Override
    public boolean offered() {
        return true;
    }

    /** The player died: if a creature worth it killed them, it is remembered (and kept from wandering off). */
    static void noteDeath(ServerPlayer player, DamageSource source) {
        if (!SituationConfig.followUps() || !(source.getEntity() instanceof Mob killer) || Foes.ownedBy(killer, player) || !killer.isAlive()) return;
        // a creature at least the player's match: a weak one is bad luck, not unfinished business
        if (Foes.ratio(killer, player) < 1.0) return;
        killer.setPersistenceRequired();
        Situations.markTarget(killer);
        CompoundTag t = new CompoundTag();
        t.putUUID(Run.TARGET, killer.getUUID());
        t.putString(DIM, killer.level().dimension().location().toString());
        t.putLong(POS, killer.blockPosition().asLong());
        t.putLong(AT, QuestEngine.gameTime(player));
        Run.setName(t, Foes.name(killer), player.registryAccess());
        FollowUps.data(player).setLastKiller(t);
    }

    /** Back on their feet: the offer comes a few seconds later. */
    static void respawned(ServerPlayer player) {
        SituationData data = FollowUps.data(player);
        CompoundTag killer = data.lastKiller();
        if (killer == null) return;
        data.setLastKiller(null);
        FollowUps.schedule(player, FOLLOW_UP, 20 * 4, killer.copy());
    }

    private boolean offerRevenge(ServerPlayer player, CompoundTag data) {
        if (QuestEngine.gameTime(player) - data.getLong(AT) > STALE) return true;
        if (!player.isAlive()) return false;
        return Situations.tryStart(player, this, () -> hunt(player, data));
    }

    @Nullable
    public Quest hunt(ServerPlayer player, CompoundTag killer) {
        if (!killer.hasUUID(Run.TARGET)) return null;
        CompoundTag run = Run.start(this, HUNT, QuestEngine.gameTime(player));
        run.putUUID(Run.TARGET, killer.getUUID(Run.TARGET));
        run.putString(Run.NAME, killer.getString(Run.NAME));
        ResourceLocation dim = ResourceLocation.tryParse(killer.getString(DIM));
        if (dim != null) Run.setWaypoint(run, ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(killer.getLong(POS)));
        Component name = Run.name(run, player.registryAccess());
        return quest(player, HUNT, QuestCategory.SURPRISE, run, name)
                .line(KILL, text("obj.kill", name))
                .watch(HEALTH, name, 100)
                .timeLimit(MINUTES * 60 * 20)
                .xp(320).statPoints(1)
                .build();
    }

    @Override
    public void update(ServerPlayer player, Quest q, Run run) {
        ResourceKey<Level> dim = Run.waypointDimension(q);
        if (dim == null || !player.level().dimension().equals(dim)) return;
        LivingEntity killer = Foes.find(player, run.target());
        if (killer != null) {
            boolean changed = run.refreshTargetId(killer);
            Situations.markTarget(killer);
            BlockPos wp = Run.waypoint(q, dim);
            if (wp == null || wp.distSqr(killer.blockPosition()) > 9) {
                Run.setWaypoint(run.tag(), dim, killer.blockPosition());
                changed = true;
            }
            run.setCount(MISSING, 0);
            if (changed) QuestEngine.sync(player);
            Situations.set(player, q, HEALTH, Foes.healthPercent(killer));
            return;
        }
        // standing where it should be, and it isn't there: it is gone
        BlockPos wp = Run.waypoint(q, dim);
        if (wp == null || wp.distSqr(player.blockPosition()) > 20 * 20) return;
        int missing = run.count(MISSING) + 1;
        run.setCount(MISSING, missing);
        if (missing >= 5) QuestEngine.fail(player, q, QuestEvent.FailReason.BROKEN, outcome("revenge_gone", run.name(player.registryAccess())));
    }

    @Override
    public void runEntityDied(ServerPlayer player, Quest q, Run run, LivingEntity dead, DamageSource source) {
        if (!dead.getUUID().equals(run.target())) return;
        Component name = run.name(player.registryAccess());
        if (Foes.killedBy(player, source, dead)) {
            q.setOutcome(outcome("revenge_taken", name));
            Situations.set(player, q, KILL, 1);
        } else {
            QuestEngine.fail(player, q, QuestEvent.FailReason.BROKEN, outcome("revenge_other", name));
        }
    }
}
