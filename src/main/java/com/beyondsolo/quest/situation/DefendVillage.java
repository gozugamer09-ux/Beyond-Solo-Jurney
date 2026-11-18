package com.beyondsolo.quest.situation;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestEvent;
import com.beyondsolo.quest.QuestObjective;
import com.beyondsolo.quest.trigger.QuestTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.raid.Raid;
import org.jetbrains.annotations.Nullable;

/**
 * A raid falls on a village the player is in. "Defend the village": the waves broken are counted as the raid goes. When
 * its last wave arrives the quest becomes "Final Assault: break it". Victory is paid (and the village's hero is the
 * player, as ever); a lost village fails the quest; leaving it to its fate fails it too.
 */
public final class DefendVillage extends Situation {
    public static final String DEFEND = "defend";
    public static final String FINAL_WAVE = "final_wave";
    static final String WAVES = BeyondSolo.id("raid_waves").toString();
    static final String RAIDERS = BeyondSolo.id("raid_raiders").toString();
    static final String RAID = "raid";
    static final String CENTER = "center";
    static final String AWAY = "away";

    /** Beyond this distance from the raid's centre the player has left the village to it. */
    public static final double LEAVE = 112;

    public DefendVillage() {
        super("raid");
    }

    @Override
    public int cooldownTicks() {
        return 20 * 60 * 2;
    }

    @Override
    @Nullable
    public Quest detect(ServerPlayer player, Watch watch) {
        if (QuestTriggers.inEmergency(QuestEngine.data(player)) || !(player.level() instanceof ServerLevel level)) return null;
        Raid raid = level.getRaidAt(player.blockPosition());
        if (raid == null || !raid.isStarted() || raid.isOver() || raid.isStopped()) return null;
        return defend(player, raid);
    }

    /** All the waves the raid will send (its bonus wave too, under a strong omen). */
    static int waves(Raid raid, ServerLevel level) {
        return Math.max(1, raid.getNumGroups(level.getDifficulty()) + (raid.getRaidOmenLevel() > 1 ? 1 : 0));
    }

    public Quest defend(ServerPlayer player, Raid raid) {
        ServerLevel level = (ServerLevel) player.level();
        CompoundTag run = Run.start(this, DEFEND, QuestEngine.gameTime(player));
        run.putInt(RAID, raid.getId());
        run.putLong(CENTER, raid.getCenter().asLong());
        Run.setWaypoint(run, level.dimension(), raid.getCenter());
        int waves = waves(raid, level);
        return quest(player, DEFEND, QuestCategory.EMERGENCY, run, waves)
                .custom(WAVES, waves, text("obj.waves"))
                .rule(text("obj.stand"))
                .xp(380).statPoints(1)
                .penalty(25, 15)
                .build();
    }

    @Override
    public void update(ServerPlayer player, Quest q, Run run) {
        if (!(player.level() instanceof ServerLevel level)) return;
        Raid raid = level.getRaids().get(run.tag().getInt(RAID));
        if (raid == null || raid.isStopped()) {
            QuestEngine.fail(player, q, QuestEvent.FailReason.BROKEN, outcome("raid_ended"));
            return;
        }
        if (raid.isVictory()) {
            Secrets.reveal(player, Secrets.VALOR);
            QuestEngine.complete(player, q, outcome("village_saved"));
            return;
        }
        if (raid.isLoss()) {
            QuestEngine.fail(player, q, QuestEvent.FailReason.BROKEN, outcome("village_fell"));
            return;
        }
        BlockPos center = BlockPos.of(run.tag().getLong(CENTER));
        int away = player.blockPosition().distSqr(center) > LEAVE * LEAVE ? run.count(AWAY) + 1 : 0;
        run.setCount(AWAY, away);
        if (away >= 15) {
            QuestEngine.fail(player, q, QuestEvent.FailReason.FLED, outcome("abandoned"));
            return;
        }
        int alive = raid.getTotalRaidersAlive();
        if (run.is(DEFEND)) {
            int total = waves(raid, level);
            int broken = Math.max(0, raid.getGroupsSpawned() - (alive > 0 ? 1 : 0));
            // the last wave is the Final Assault; the count never finishes the quest by itself (victory does)
            QuestObjective o = q.objectives().get(Situations.objective(q, WAVES));
            Situations.set(player, q, WAVES, Math.min(o.required() - 1, broken));
            if (raid.getGroupsSpawned() >= total && alive > 0) finalWave(player, q, run, alive);
        } else {
            QuestObjective o = q.objectives().get(Situations.objective(q, RAIDERS));
            Situations.set(player, q, RAIDERS, Math.min(o.required() - 1, o.required() - alive));
        }
    }

    private void finalWave(ServerPlayer player, Quest q, Run run, int alive) {
        CompoundTag next = run.next(FINAL_WAVE);
        Quest last = quest(player, FINAL_WAVE, QuestCategory.EMERGENCY, next, alive)
                .custom(RAIDERS, Math.max(1, alive), text("obj.final"))
                .rule(text("obj.stand"))
                .xp(520).statPoints(1)
                .penalty(25, 15)
                .build();
        Situations.branch(player, q, last, false);
    }
}
