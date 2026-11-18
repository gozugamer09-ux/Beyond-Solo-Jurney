package com.beyondsolo.quest.situation;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestBuilder;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestEvent;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The player walks into a gate above their own rank. "Beyond Your Rank: clear it, and come back alive." Paid by how far
 * above them it is. If it goes badly (their health falls under {@link #RETREAT_AT}) the System advises retreat and the
 * quest becomes "Retreat: leave the gate alive": knowing when to run is paid too, less. Clearing it anyway pays it all.
 *
 * <p>It also keeps track of the gate the player is in for {@link Secrets#UNTOUCHED} (a gate cleared without a hit).</p>
 */
public final class BeyondRank extends Situation {
    public static final String CLEAR = "clear";
    public static final String RETREAT = "retreat";
    static final String BOSS = BeyondSolo.id("beyond_boss").toString();
    static final String LEAVE = BeyondSolo.id("beyond_leave").toString();
    static final String GATE = "gate";
    static final String GAP = "gap";

    /** Under this share of their health inside such a gate, the System advises retreat. */
    public static final float RETREAT_AT = 0.3f;

    public BeyondRank() {
        super("beyond_rank");
    }

    @Override
    public int cooldownTicks() {
        return 20 * 60;
    }

    /** The gate the player is standing in, or null. */
    @Nullable
    static GateInstance gateOf(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !GateService.isDungeon(level)) return null;
        return GateData.get(level.getServer()).bySlotPos(player.blockPosition());
    }

    /** Once a second: which gate the player is in, what happens in it. */
    static void track(ServerPlayer player, Watch w) {
        GateInstance g = gateOf(player);
        if (g == null) {
            w.gate = null;
            return;
        }
        if (!g.id().equals(w.gate)) {
            // a new run: unhurt so far, and a boss to beat (unless it already fell)
            w.gate = g.id();
            w.hurtInGate = false;
            w.gateCleared = g.bossDefeated();
            if (!g.isTrial()) {
                int gap = g.rank().tier() - SystemAPI.get(player).rank().tier();
                if (gap >= 1) Situations.tryStart(player, Situations.BEYOND_RANK, () -> Situations.BEYOND_RANK.clear(player, g, gap));
            }
        }
        if (!w.gateCleared && g.bossDefeated()) {
            w.gateCleared = true;
            if (!w.hurtInGate && !g.isTrial()) Secrets.reveal(player, Secrets.UNTOUCHED);
        }
    }

    public Quest clear(ServerPlayer player, GateInstance g, int gap) {
        CompoundTag run = Run.start(this, CLEAR, QuestEngine.gameTime(player));
        run.putUUID(GATE, g.id());
        run.putInt(GAP, gap);
        return clearQuest(player, g.rank(), SystemAPI.get(player).rank(), gap, run).build();
    }

    private QuestBuilder clearQuest(ServerPlayer player, PlayerRank gate, PlayerRank own, int gap, CompoundTag run) {
        return quest(player, CLEAR, QuestCategory.SURPRISE, run, gate.displayName(), own.displayName())
                .line(BOSS, text("obj.boss"))
                .rule(text("obj.alive"))
                .xp(300L * gap).statPoints(gap);
    }

    @Override
    public void update(ServerPlayer player, Quest q, Run run) {
        UUID id = run.tag().hasUUID(GATE) ? run.tag().getUUID(GATE) : null;
        GateInstance g = id == null ? null : GateData.get(player.server).get(id);
        GateInstance in = gateOf(player);
        boolean inside = in != null && in.id().equals(id);
        if (g != null && g.bossDefeated()) {
            boolean alone = !(player.level() instanceof ServerLevel level) || GateService.playersIn(level, g).size() <= 1;
            if (alone) Secrets.reveal(player, Secrets.LONE_WOLF);
            if (run.is(CLEAR)) {
                q.setOutcome(outcome("beyond_cleared"));
                Situations.set(player, q, BOSS, 1);
            } else {
                PlayerRank own = SystemAPI.get(player).rank();
                QuestEngine.complete(player, q, outcome("beyond_cleared"), clearQuest(player, g.rank(), own, run.count(GAP), run.next(CLEAR)).build().rewards());
            }
            return;
        }
        if (!inside) {
            if (run.is(RETREAT)) QuestEngine.complete(player, q, outcome("retreated"));
            else QuestEngine.fail(player, q, QuestEvent.FailReason.FLED, outcome("left_gate"));
            return;
        }
        if (run.is(CLEAR) && player.getHealth() < player.getMaxHealth() * RETREAT_AT) {
            int gap = run.count(GAP);
            Quest retreat = quest(player, RETREAT, QuestCategory.SURPRISE, run.next(RETREAT))
                    .line(LEAVE, text("obj.leave"))
                    .rule(text("obj.alive"))
                    .xp(120L * gap)
                    .build();
            Situations.branch(player, q, retreat, false);
        }
    }

    @Override
    public void playerDied(ServerPlayer player, Quest q, Run run, DamageSource source) {
        QuestEngine.fail(player, q, QuestEvent.FailReason.DIED, outcome("beyond_fallen"));
    }
}
