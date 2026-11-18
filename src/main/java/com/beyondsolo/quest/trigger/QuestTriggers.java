package com.beyondsolo.quest.trigger;

import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestData;
import com.beyondsolo.quest.QuestEngine;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Surprise quests. Built in: a spawner nest (emergency), an ambush (emergency) and rich veins underground
 * (offered). Add more with {@link #register}.
 */
public final class QuestTriggers {
    private static final List<QuestTrigger> TRIGGERS = new ArrayList<>();
    /** Triggers are looked at every this many seconds of a player's quest ticks. */
    private static final int CHECK_EVERY = 2;
    /** Creative players get no surprise quests unless this is on (dev and recording). */
    public static boolean allowCreative;

    public static final SpawnerNestTrigger SPAWNER_NEST = register(new SpawnerNestTrigger());
    public static final AmbushTrigger AMBUSH = register(new AmbushTrigger());
    public static final DeepVeinsTrigger DEEP_VEINS = register(new DeepVeinsTrigger());

    public static <T extends QuestTrigger> T register(T trigger) {
        TRIGGERS.removeIf(t -> t.id().equals(trigger.id()));
        TRIGGERS.add(trigger);
        return trigger;
    }

    public static List<QuestTrigger> all() {
        return List.copyOf(TRIGGERS);
    }

    @Nullable
    public static QuestTrigger byId(String id) {
        for (QuestTrigger t : TRIGGERS) if (t.id().equals(id) || t.id().endsWith(":" + id)) return t;
        return null;
    }

    /**
     * True while the System should not spring anything new on the player: a spectator, a creative player (unless
     * {@link #allowCreative}), someone in the job change trial, the Penalty Zone, a red gate or a run of the Crimson
     * Citadel.
     */
    public static boolean quiet(ServerPlayer player) {
        if (player.isSpectator() || player.isCreative() && !allowCreative) return true;
        // no surprise quests in the middle of the job change trial
        if (com.beyondsolo.job.JobTrial.inTrial(player)) return true;
        // nor in the Penalty Zone, nor in a red gate, nor while climbing the Citadel
        return com.beyondsolo.penalty.PenaltyZone.busy(player) || com.beyondsolo.redgate.RedGates.occupied(player)
                || com.beyondsolo.tower.TowerAPI.busy(player);
    }

    /** The player is in an emergency already (one at a time). */
    public static boolean inEmergency(QuestData data) {
        for (Quest q : data.active()) if (q.category() == QuestCategory.EMERGENCY) return true;
        return false;
    }

    public static void check(ServerPlayer player, QuestData data) {
        if ((player.tickCount / 20) % CHECK_EVERY != 0) return;
        if (quiet(player)) return;
        // one emergency at a time
        boolean inEmergency = inEmergency(data);
        long now = QuestEngine.gameTime(player);
        for (QuestTrigger t : TRIGGERS) {
            if (now < data.cooldownUntil(t.id())) continue;
            if (fire(player, data, t, inEmergency)) return;
        }
    }

    /** Tests one trigger now (ignores the cooldown, not the conditions). Returns true if it started a quest. */
    public static boolean fire(ServerPlayer player, QuestData data, QuestTrigger trigger, boolean inEmergency) {
        Quest q = trigger.test(player, data);
        if (q == null || inEmergency && q.isForced() || data.has(q.template())) return false;
        data.setCooldown(trigger.id(), QuestEngine.gameTime(player) + trigger.cooldownTicks());
        QuestEngine.offer(player, q, QuestEngine.OFFER_TICKS);
        return true;
    }

    private QuestTriggers() {}
}
