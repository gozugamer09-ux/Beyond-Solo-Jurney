package com.beyondsolo.job;

import com.beyondsolo.network.JobSyncPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTemplate;
import com.beyondsolo.skill.SkillAPI;
import com.beyondsolo.system.Stat;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The one entry point for the job change: reading a player's subclass, opening the trial, and changing jobs.
 *
 * <p>For later systems: {@link #subclass} tells you a player's subclass (null before the job change), and
 * {@link Subclass#canRaiseShadows()} is true only for the necromancer. Listen to {@link JobEvent.Changed}.</p>
 */
public final class JobAPI {
    public static JobData get(Player player) {
        return player.getData(JobModule.JOB.get());
    }

    /** The player's subclass, or null before their job change. Server side (the client has {@code ClientJob}). */
    public static @Nullable Subclass subclass(Player player) {
        return get(player).subclass();
    }

    public static boolean hasChangedJob(Player player) {
        return get(player).changed();
    }

    public static int unlockLevel() {
        return JobConfig.UNLOCK_LEVEL.get();
    }

    /** True if the player may walk into the trial now. */
    public static boolean canAttemptTrial(ServerPlayer player) {
        SystemData sys = SystemAPI.get(player);
        JobData data = get(player);
        return sys.isAwakened() && data.unlocked() && !data.changed() && !data.inTrial()
                && player.level().getGameTime() >= data.retryAt();
    }

    /**
     * Opens the trial if the player has reached the milestone: the System announces it, starts the job change
     * quest and hands over the Key of Ascension. Does nothing if it is already open or done.
     */
    public static boolean unlockIfReady(ServerPlayer player) {
        SystemData sys = SystemAPI.get(player);
        JobData data = get(player);
        if (!sys.isAwakened() || data.unlocked() || data.changed() || sys.level() < unlockLevel()) return false;
        unlock(player);
        return true;
    }

    /** Opens the trial now, whatever the level (commands, tests). */
    public static void unlock(ServerPlayer player) {
        JobData data = get(player);
        data.setUnlocked(true);
        sync(player);
        Notifications.send(player, new SystemNotification(SystemNotification.Kind.QUEST,
                Component.translatable("notify.beyondsolo.job_unlocked.title"),
                Component.translatable("notify.beyondsolo.job_unlocked.body"), 260));
        if (!QuestEngine.data(player).has(JobModule.QUEST_TEMPLATE)) {
            QuestEngine.start(player, JobModule.QUEST_TEMPLATE, QuestTemplate.Context.NONE);
        }
        giveKey(player);
        NeoForge.EVENT_BUS.post(new JobEvent.TrialUnlocked(player));
    }

    /** Hands the Key of Ascension over again if the player has none (lost, dropped, died with it). */
    public static void giveKey(ServerPlayer player) {
        JobData data = get(player);
        if (!data.unlocked() || data.changed()) return;
        if (player.getInventory().contains(new ItemStack(JobModule.ASCENSION_KEY.get()))) return;
        ItemStack key = new ItemStack(JobModule.ASCENSION_KEY.get());
        if (!player.getInventory().add(key) && !key.isEmpty()) player.drop(key, false);
    }

    /**
     * Makes the player a subclass: bonus stats along the subclass's weights, the base class's advanced skills and
     * the subclass's own skills. Skills of any other subclass are forgotten.
     */
    public static void changeJob(ServerPlayer player, Subclass subclass) {
        JobData data = get(player);
        Subclass old = data.subclass();
        data.setSubclass(subclass, player.level().getGameTime());
        data.setUnlocked(true);
        if (old != subclass) {
            SystemData sys = SystemAPI.get(player);
            int points = subclass.bonusPoints();
            int total = 0;
            for (Stat s : Stat.values()) total += subclass.statWeight(s);
            int given = 0;
            Stat main = Stat.STR;
            for (Stat s : Stat.values()) {
                int share = points * subclass.statWeight(s) / Math.max(1, total);
                if (share > 0) SystemAPI.setStat(player, s, sys.stat(s) + share);
                given += share;
                if (subclass.statWeight(s) > subclass.statWeight(main)) main = s;
            }
            if (points > given) SystemAPI.setStat(player, main, sys.stat(main) + points - given);
        }
        SkillAPI.refreshForClass(player);
        SkillAPI.grantAdvanced(player);
        SkillAPI.grantSubclass(player);
        // remove the key once it has done its job
        player.getInventory().clearOrCountMatchingItems(s -> s.is(JobModule.ASCENSION_KEY.get()), -1, player.inventoryMenu.getCraftSlots());
        sync(player);
        NeoForge.EVENT_BUS.post(new JobEvent.Changed(player, old, subclass));
    }

    /** Takes the job change back (commands, tests). Subclass skills are forgotten; stats stay. */
    public static void reset(ServerPlayer player) {
        get(player).reset();
        SkillAPI.refreshForClass(player);
        sync(player);
    }

    /** Rolls a subclass for the player's base class, with the configured rare chance (times {@code luck}). */
    public static Subclass roll(ServerPlayer player, double luck) {
        double chance = Math.min(1, JobConfig.RARE_CHANCE.get() * luck);
        return Subclass.roll(SystemAPI.get(player).playerClass(), player.getRandom(), chance);
    }

    static void enterTrial(ServerPlayer player) {
        JobData data = get(player);
        data.addAttempt();
        data.enterTrial(player.level().dimension().location(), player.position(), player.getYRot());
    }

    static void leaveTrial(ServerPlayer player, boolean failed) {
        JobData data = get(player);
        data.leaveTrial();
        if (failed) data.setRetryAt(player.level().getServer().overworld().getGameTime() + JobConfig.RETRY_MINUTES.get() * 1200L);
    }

    static void setRetryAt(ServerPlayer player, long at) {
        get(player).setRetryAt(at);
    }

    public static void sync(ServerPlayer player) {
        JobData data = get(player);
        PacketDistributor.sendToPlayer(player, new JobSyncPayload(data.subclass() == null ? -1 : data.subclass().ordinal(), data.unlocked()));
    }

    private JobAPI() {}
}
