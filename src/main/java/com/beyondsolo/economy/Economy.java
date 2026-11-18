package com.beyondsolo.economy;

import com.beyondsolo.network.EconomySyncPayload;
import com.beyondsolo.network.GoldPayload;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Gold, the System's currency. Every change goes through here so the client stays in sync and sees the gain or
 * the spend. Gold lives in {@link HunterData} and is kept through death.
 */
public final class Economy {
    /** Why gold changed (shown with the change on the client). */
    public enum Reason { KILL, QUEST, GATE, PURCHASE, SALE, COMMAND, FORGE, CRAFT, TOWER, RECOVERY, PARTY }

    public static HunterData data(Player player) {
        return player.getData(EconomyModule.HUNTER.get());
    }

    public static long gold(Player player) {
        return data(player).gold;
    }

    /** Adds gold (negative amounts take it, never below zero). */
    public static void add(ServerPlayer player, long amount, Reason reason) {
        if (amount == 0) return;
        // the worn title's gold perk (kills, quests, gates and the Citadel only)
        amount = com.beyondsolo.chronicle.title.TitleService.gold(player, amount, reason);
        HunterData d = data(player);
        long before = d.gold;
        d.gold = Math.max(0, d.gold + amount);
        if (amount > 0) d.earned += amount;
        long delta = d.gold - before;
        if (delta != 0) PacketDistributor.sendToPlayer(player, new GoldPayload(delta, d.gold, reason.ordinal()));
    }

    /** Takes gold if the player has enough. */
    public static boolean spend(ServerPlayer player, long amount, Reason reason) {
        if (amount < 0 || gold(player) < amount) return false;
        add(player, -amount, reason);
        return true;
    }

    public static void set(ServerPlayer player, long amount) {
        add(player, Math.max(0, amount) - gold(player), Reason.COMMAND);
    }

    /** Empties everything: gold, storage, expansions, clears, license (for tests and commands). */
    public static void reset(ServerPlayer player) {
        data(player).reset();
        sync(player);
    }

    /** Counts a gate clear for the license. */
    public static void countClear(ServerPlayer player, PlayerRank rank) {
        data(player).clears[rank.ordinal()]++;
        sync(player);
    }

    /** Sends the whole picture: gold, storage size, clears, today's shop purchases. */
    public static void sync(ServerPlayer player) {
        HunterData d = data(player);
        CompoundTag tag = new CompoundTag();
        tag.putLong("gold", d.gold);
        tag.putLong("earned", d.earned);
        tag.putInt("expansions", d.expansions);
        tag.putInt("capacity", SystemStorage.capacity(player));
        tag.putIntArray("clears", d.clears);
        tag.putLong("shopDay", d.shopDay);
        CompoundTag bought = new CompoundTag();
        d.purchases.forEach(bought::putInt);
        tag.put("bought", bought);
        tag.putString("number", LicenseInfo.number(player.getUUID()));
        PacketDistributor.sendToPlayer(player, new EconomySyncPayload(tag));
    }

    private Economy() {}
}
