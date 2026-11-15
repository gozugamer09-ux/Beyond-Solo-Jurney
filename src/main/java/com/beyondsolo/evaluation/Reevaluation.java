package com.beyondsolo.evaluation;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.Config;
import com.beyondsolo.network.ReevaluationSyncPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.registry.ModAttachments;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.Stat;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import com.beyondsolo.system.SystemEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Hidden rank thresholds and re-evaluation.
 *
 * <p>Every rank above E has a hidden threshold on the total of the five stats ({@code reevaluation.rankThresholds}).
 * A player whose total crosses one is, in truth, that rank, but nothing shows it: the System window, the HUD and
 * the license keep the rank of their last evaluation. Only an Evaluation Crystal can read the true rank, and it does
 * that once per level milestone ({@code reevaluation.milestoneLevels}, every 10 levels by default). A re-evaluation
 * never lowers a rank, and can jump several ranks at once.</p>
 *
 * <p>The System may whisper (never naming the rank) when a threshold is crossed: {@code reevaluation.hintOnThreshold}.</p>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class Reevaluation {
    /** How often (ticks) players are checked for a crossed threshold, for stat changes that fire no event. */
    private static final int CHECK_EVERY = 40;

    public static EvaluationRecord record(ServerPlayer player) {
        return player.getData(ModAttachments.EVALUATION_RECORD);
    }

    // ---------------------------------------------------------------- thresholds

    public static int statTotal(SystemData data) {
        int total = 0;
        for (Stat s : Stat.values()) total += data.stat(s);
        return total;
    }

    /** The stat total needed for {@code rank}; 0 for E. */
    public static int threshold(PlayerRank rank) {
        if (rank.tier() == 0) return 0;
        List<? extends Integer> list = Config.RANK_THRESHOLDS.get();
        int i = rank.tier() - 1;
        if (i < list.size()) return list.get(i);
        return list.isEmpty() ? Integer.MAX_VALUE : list.get(list.size() - 1) * (i + 2) / (list.size() + 1);
    }

    /** The highest rank whose threshold a stat total reaches. */
    public static PlayerRank earnedRank(int total) {
        PlayerRank best = PlayerRank.E;
        for (PlayerRank r : PlayerRank.values()) if (total >= threshold(r)) best = r;
        return best;
    }

    /**
     * The rank the player truly is: their evaluated rank, or higher if their stats crossed a hidden threshold.
     * Server only and never shown; the crystal reveals it.
     */
    public static PlayerRank trueRank(ServerPlayer player) {
        SystemData data = SystemAPI.get(player);
        PlayerRank earned = earnedRank(statTotal(data));
        return earned.tier() > data.rank().tier() ? earned : data.rank();
    }

    // ---------------------------------------------------------------- milestones

    public static int milestoneSize() {
        return Config.REEVALUATION_MILESTONE.get();
    }

    public static int milestone(int level) {
        return level / milestoneSize();
    }

    /** True if the player was evaluated and has reached a milestone since their last (re-)evaluation. */
    public static boolean isAvailable(ServerPlayer player) {
        SystemData data = SystemAPI.get(player);
        if (!data.isAwakened()) return false;
        EvaluationRecord r = record(player);
        ensureStarted(player, r);
        return milestone(data.level()) > r.lastMilestone;
    }

    /** The level at which the next re-evaluation opens (after the one already used). */
    public static int nextLevel(ServerPlayer player) {
        EvaluationRecord r = record(player);
        ensureStarted(player, r);
        if (r.lastMilestone == EvaluationRecord.NONE) return milestoneSize();
        return Math.max(0, (r.lastMilestone + 1) * milestoneSize());
    }

    /** Saves from before re-evaluation existed: count the player as read at their current milestone. */
    private static void ensureStarted(ServerPlayer player, EvaluationRecord r) {
        SystemData data = SystemAPI.get(player);
        if (r.lastMilestone != EvaluationRecord.NONE || !data.isAwakened()) return;
        markEvaluated(player);
    }

    /** The crystal just read the player: their milestone is used up and their stats become the new baseline. */
    static void markEvaluated(ServerPlayer player) {
        SystemData data = SystemAPI.get(player);
        EvaluationRecord r = record(player);
        r.lastMilestone = milestone(data.level());
        for (Stat s : Stat.values()) r.snapshot[s.ordinal()] = data.stat(s);
        r.hinted = Math.max(r.hinted, data.rank().ordinal());
    }

    static void countReevaluation(ServerPlayer player) {
        record(player).reevaluations++;
    }

    /** Wipes the record (used with /system reset and the test commands). */
    public static void reset(ServerPlayer player) {
        record(player).reset();
        sync(player);
    }

    /** Lets the player be re-evaluated again right away (testing). */
    public static void unlock(ServerPlayer player) {
        EvaluationRecord r = record(player);
        ensureStarted(player, r);
        r.lastMilestone = milestone(SystemAPI.get(player).level()) - 1;
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ReevaluationSyncPayload(isAvailable(player), nextLevel(player)));
    }

    // ---------------------------------------------------------------- refunds

    /**
     * Gives back every stat point the player spent by hand (Elixir of Rebirth). Points from the rank, the job change
     * and rewards stay where they are. Returns how many points came back.
     */
    public static int refundAllocated(ServerPlayer player) {
        SystemData data = SystemAPI.get(player);
        EvaluationRecord r = record(player);
        int refunded = 0;
        for (Stat s : Stat.values()) {
            int spent = Math.min(r.allocated[s.ordinal()], data.stat(s) - Stat.BASE);
            if (spent > 0) {
                SystemAPI.setStat(player, s, data.stat(s) - spent);
                refunded += spent;
            }
            r.allocated[s.ordinal()] = 0;
        }
        if (refunded > 0) SystemAPI.addStatPoints(player, refunded);
        return refunded;
    }

    // ---------------------------------------------------------------- hooks

    @SubscribeEvent
    static void onAwakened(SystemEvent.Awakened event) {
        ServerPlayer p = event.getPlayer();
        EvaluationRecord r = record(p);
        java.util.Arrays.fill(r.allocated, 0);
        r.hinted = -1;
        markEvaluated(p);
        sync(p);
    }

    @SubscribeEvent
    static void onAllocated(SystemEvent.StatAllocated event) {
        record(event.getPlayer()).allocated[event.getStat().ordinal()]++;
        checkThreshold(event.getPlayer());
    }

    @SubscribeEvent
    static void onLevelUp(SystemEvent.LevelUp event) {
        ServerPlayer p = event.getPlayer();
        EvaluationRecord r = record(p);
        ensureStarted(p, r);
        int before = milestone(event.getOldLevel());
        int now = milestone(event.getNewLevel());
        if (now > before && now > r.lastMilestone) {
            Notifications.send(p, new SystemNotification(SystemNotification.Kind.QUEST,
                    Component.translatable("notify.beyondsolo.reeval_ready.title"),
                    Component.translatable("notify.beyondsolo.reeval_ready.body", now * milestoneSize()), 220));
        }
        sync(p);
    }

    @SubscribeEvent
    static void onRank(SystemEvent.RankChanged event) {
        EvaluationRecord r = record(event.getPlayer());
        r.hinted = Math.max(r.hinted, event.getNewRank().ordinal());
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p);
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p);
    }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p && p.tickCount % CHECK_EVERY == 17) checkThreshold(p);
    }

    /**
     * When the stat total crosses a hidden threshold for the first time, the System stirs. The pop-up never names
     * the rank; it only says the crystal would read the player differently now.
     */
    private static void checkThreshold(ServerPlayer p) {
        SystemData data = SystemAPI.get(p);
        if (!data.isAwakened()) return;
        EvaluationRecord r = record(p);
        if (r.lastMilestone == EvaluationRecord.NONE) return;
        PlayerRank truly = trueRank(p);
        if (truly.ordinal() <= Math.max(r.hinted, data.rank().ordinal())) return;
        r.hinted = truly.ordinal();
        if (!Config.REEVALUATION_HINT.get()) return;
        boolean ready = isAvailable(p);
        Notifications.send(p, new SystemNotification(SystemNotification.Kind.ALERT,
                Component.translatable("notify.beyondsolo.threshold.title"),
                ready ? Component.translatable("notify.beyondsolo.threshold.body.ready")
                        : Component.translatable("notify.beyondsolo.threshold.body", nextLevel(p)), 240));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8f, 0.6f);
    }

    private Reevaluation() {}
}
