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

    // ---------- awakening / rank / class ----------

    /** Activates the System for the player with the given rank and class and hands out bonus stats. */
    public static void awaken(ServerPlayer player, PlayerRank rank, PlayerClass playerClass) {
        SystemData data = get(player);
        data.setAwakened(true);
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
