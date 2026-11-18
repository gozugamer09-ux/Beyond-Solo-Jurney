package com.beyondsolo.system;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.Config;
import com.beyondsolo.network.SyncSystemDataPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.registry.ModAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The one entry point other modules should use to read or change a player's System.
 * Every write keeps the client in sync and fires the matching {@link SystemEvent}.
 */
public final class SystemAPI {
    public static final ResourceLocation XP_SOURCE_KILL = BeyondSolo.id("kill");
    public static final ResourceLocation XP_SOURCE_COMMAND = BeyondSolo.id("command");

    // ---------- reads ----------

    public static SystemData get(Player player) {
        return player.getData(ModAttachments.SYSTEM);
    }

    public static boolean isAwakened(Player player) {
        return get(player).isAwakened();
    }

    /**
     * True once the System is bound to the player (the awakening trial, see {@code awakening.AwakeningService}).
     * Before that there are no System windows, pop-ups or quests. Evaluated players always have it.
     */
    public static boolean hasSystem(Player player) {
        return get(player).hasSystem();
    }

    /** Binds the System to the player. Fires {@link SystemEvent.SystemGranted} the first time. */
    public static void grantSystem(ServerPlayer player) {
        SystemData data = get(player);
        if (data.hasSystem()) return;
        data.setHasSystem(true);
        sync(player);
        NeoForge.EVENT_BUS.post(new SystemEvent.SystemGranted(player));
    }

    // ---------- awakening / rank / class ----------

    /** Activates the System for the player with the given rank and class and hands out bonus stats. */
    public static void awaken(ServerPlayer player, PlayerRank rank, PlayerClass playerClass) {
        SystemData data = get(player);
        data.setAwakened(true);
        data.setHasSystem(true);
        data.setRank(rank);
        data.setPlayerClass(playerClass);
        data.resetStats();
        distributeBonusStats(data, rank.bonusStatPoints(), playerClass);
        data.setMana(data.maxMana());
        if (data.objective().equals(Objectives.FIND_VILLAGE) || data.objective().equals(Objectives.FIND_EVALUATION_ZONE)) {
            setObjectiveSilently(player, Objectives.NONE);
        }
        StatEffects.apply(player);
        player.setHealth(player.getMaxHealth());
        sync(player);
        NeoForge.EVENT_BUS.post(new SystemEvent.Awakened(player, rank, playerClass));
    }

    public static void setRank(ServerPlayer player, PlayerRank rank) {
        SystemData data = get(player);
        PlayerRank old = data.rank();
        if (old == rank) return;
        data.setRank(rank);
        sync(player);
        NeoForge.EVENT_BUS.post(new SystemEvent.RankChanged(player, old, rank));
    }

    /**
     * Changes the class and nothing else (commands): stats stay where they are. Callers bring skills and the job in
     * line with the new class.
     */
    public static void setClass(ServerPlayer player, PlayerClass playerClass) {
        SystemData data = get(player);
        if (data.playerClass() == playerClass) return;
        data.setPlayerClass(playerClass);
        sync(player);
    }

    public static void setTitle(ServerPlayer player, String title) {
        get(player).setTitle(title);
        sync(player);
    }

    /** Clears everything (used by /system reset). */
    public static void reset(ServerPlayer player) {
        get(player).reset();
        StatEffects.apply(player);
        sync(player);
    }

    private static void distributeBonusStats(SystemData data, int points, PlayerClass playerClass) {
        int totalWeight = 0;
        for (Stat s : Stat.values()) totalWeight += playerClass.statWeight(s);
        int given = 0;
        for (Stat s : Stat.values()) {
            int share = points * playerClass.statWeight(s) / totalWeight;
            data.setStat(s, data.stat(s) + share);
            given += share;
        }
        // Rounding leftovers go to the class's main stat.
        Stat main = Stat.STR;
        for (Stat s : Stat.values()) if (playerClass.statWeight(s) > playerClass.statWeight(main)) main = s;
        data.setStat(main, data.stat(main) + (points - given));
    }

    // ---------- XP and levels ----------

    /** Gives System XP. Does nothing for players who have not been evaluated yet. */
    public static void grantXp(ServerPlayer player, long amount, ResourceLocation source) {
        SystemData data = get(player);
        if (!data.isAwakened() || amount <= 0) return;
        SystemEvent.XpGain event = NeoForge.EVENT_BUS.post(new SystemEvent.XpGain(player, amount, source));
        if (event.isCanceled() || event.getAmount() <= 0) return;

        int oldLevel = data.level();
        long xp = data.xp() + event.getAmount();
        int level = oldLevel;
        while (level < Progression.MAX_LEVEL && xp >= Progression.xpToNext(level)) {
            xp -= Progression.xpToNext(level);
            level++;
        }
        data.setXp(xp);
        if (level != oldLevel) {
            data.setLevel(level);
            data.setStatPoints(data.statPoints() + (level - oldLevel) * Config.STAT_POINTS_PER_LEVEL.get());
            // Like the stories: a level up fully restores you.
            player.setHealth(player.getMaxHealth());
            data.setMana(data.maxMana());
            data.setExhaustion(0);
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
            Notifications.send(player, SystemNotification.of(SystemNotification.Kind.LEVEL_UP,
                    Component.translatable("notify.beyondsolo.level_up.title"),
                    Component.translatable("notify.beyondsolo.level_up.body", level, data.statPoints())));
        }
        sync(player);
        if (level != oldLevel) NeoForge.EVENT_BUS.post(new SystemEvent.LevelUp(player, oldLevel, level));
    }

    /**
     * Sets the level directly (commands). Going up gives the free stat points those levels are worth and restores
     * the player like a level up; going down takes those points back (never below zero). Either way the XP towards
     * the next level starts over and {@link SystemEvent.LevelUp} fires, so what follows a level (skill unlocks, the
     * job change, the army's size) catches up.
     */
    public static void setLevel(ServerPlayer player, int level) {
        SystemData data = get(player);
        int oldLevel = data.level();
        int target = Math.max(1, Math.min(Progression.MAX_LEVEL, level));
        if (target == oldLevel) return;
        data.setLevel(target);
        data.setXp(0);
        data.setStatPoints(data.statPoints() + (target - oldLevel) * Config.STAT_POINTS_PER_LEVEL.get());
        if (target > oldLevel) {
            player.setHealth(player.getMaxHealth());
            data.setMana(data.maxMana());
            data.setExhaustion(0);
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
            Notifications.send(player, SystemNotification.of(SystemNotification.Kind.LEVEL_UP,
                    Component.translatable("notify.beyondsolo.level_up.title"),
                    Component.translatable("notify.beyondsolo.level_up.body", target, data.statPoints())));
        }
        sync(player);
        NeoForge.EVENT_BUS.post(new SystemEvent.LevelUp(player, oldLevel, target));
    }

    /**
     * Puts every stat back to its base value and hands the difference back as free points (commands). Returns how
     * many points came back.
     */
    public static int refundStats(ServerPlayer player) {
        SystemData data = get(player);
        int back = 0;
        for (Stat s : Stat.values()) back += data.stat(s) - Stat.BASE;
        data.resetStats();
        data.setStatPoints(data.statPoints() + back);
        data.setMana(data.mana());
        StatEffects.apply(player);
        sync(player);
        return back;
    }

    public static void addStatPoints(ServerPlayer player, int points) {
        SystemData data = get(player);
        data.setStatPoints(data.statPoints() + points);
        sync(player);
    }

    /** Spends one free point on a stat. Returns false if the player has no points. */
    public static boolean allocateStat(ServerPlayer player, Stat stat) {
        SystemData data = get(player);
        if (!data.isAwakened() || data.statPoints() <= 0) return false;
        data.setStatPoints(data.statPoints() - 1);
        data.setStat(stat, data.stat(stat) + 1);
        StatEffects.apply(player);
        sync(player);
        NeoForge.EVENT_BUS.post(new SystemEvent.StatAllocated(player, stat, data.stat(stat)));
        return true;
    }

    /**
     * Spends up to {@code count} free points on one stat at once (shift-click in the STATUS tab). Fires
     * {@link SystemEvent.StatAllocated} once per point, like single clicks would. Returns how many were spent.
     */
    public static int allocateStats(ServerPlayer player, Stat stat, int count) {
        int spent = 0;
        while (spent < count && allocateStat(player, stat)) spent++;
        return spent;
    }

    /** Sets a stat directly (rewards, items, commands). Never goes below the base value. */
    public static void setStat(ServerPlayer player, Stat stat, int value) {
        get(player).setStat(stat, value);
        StatEffects.apply(player);
        sync(player);
    }

    // ---------- mana and exhaustion ----------

    /** Tries to spend mana. Returns false (and spends nothing) if the player has too little. */
    public static boolean consumeMana(ServerPlayer player, float amount) {
        SystemData data = get(player);
        if (data.mana() < amount) return false;
        data.setMana(data.mana() - amount);
        sync(player);
        return true;
    }

    public static void restoreMana(ServerPlayer player, float amount) {
        SystemData data = get(player);
        data.setMana(data.mana() + amount);
        sync(player);
    }

    /** Sets the extra max mana skills give. Does nothing if it is unchanged. */
    public static void setManaBonus(ServerPlayer player, float bonus) {
        SystemData data = get(player);
        if (Math.abs(data.manaBonus() - bonus) < 0.001f) return;
        data.setManaBonus(bonus);
        data.setMana(data.mana());
        sync(player);
    }

    /** Adds (or with a negative value removes) exhaustion. Reaching 100% makes the player collapse. */
    public static void addExhaustion(ServerPlayer player, float amount) {
        Exhaustion.add(player, amount);
    }

    // ---------- objective ----------

    public static void setObjective(ServerPlayer player, String objective) {
        String old = get(player).objective();
        if (old.equals(objective)) return;
        setObjectiveSilently(player, objective);
        sync(player);
        if (!objective.isEmpty()) {
            Notifications.send(player, SystemNotification.of(SystemNotification.Kind.QUEST,
                    Objectives.title(objective), Objectives.description(objective)));
        }
    }

    private static void setObjectiveSilently(ServerPlayer player, String objective) {
        SystemData data = get(player);
        String old = data.objective();
        data.setObjective(objective);
        NeoForge.EVENT_BUS.post(new SystemEvent.ObjectiveChanged(player, old, objective));
    }

    // ---------- misc ----------

    public static void markFirstJoinHandled(ServerPlayer player) {
        get(player).setFirstJoinHandled(true);
    }

    /** Sends the whole System state to the player's client. Cheap; call it after any change. */
    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SyncSystemDataPayload(get(player).serializeNBT(player.registryAccess())));
    }

    private SystemAPI() {}
}
