package com.beyondsolo.economy;

import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.HashMap;
import java.util.Map;

/**
 * A hunter's own things outside the System's numbers: their gold, the System storage, storage expansions bought,
 * gates cleared by rank, their license and what they bought from the shop today. Kept through death.
 * Change it through {@link Economy} and {@link SystemStorage}.
 */
public final class HunterData implements INBTSerializable<CompoundTag> {
    /** The most slots storage can ever have (two pages of 54). */
    public static final int MAX_SLOTS = 108;

    long gold;
    long earned;
    int expansions;
    final int[] clears = new int[PlayerRank.values().length];
    boolean licenseIssued;
    long licenseIssuedDay = -1;
    /** Shop entry id -> bought today, for entries with a daily limit. Reset when {@link #shopDay} changes. */
    final Map<String, Integer> purchases = new HashMap<>();
    long shopDay = -1;
    final NonNullList<ItemStack> storage = NonNullList.withSize(MAX_SLOTS, ItemStack.EMPTY);

    public long gold() { return gold; }
    public long earned() { return earned; }
    public int expansions() { return expansions; }
    public boolean licenseIssued() { return licenseIssued; }
    public long licenseIssuedDay() { return licenseIssuedDay; }

    public int clears(PlayerRank rank) { return clears[rank.ordinal()]; }

    public int totalClears() {
        int n = 0;
        for (int c : clears) n += c;
        return n;
    }

    /** The highest gate rank this hunter has cleared, or null. */
    public PlayerRank bestClear() {
        for (int i = clears.length - 1; i >= 0; i--) if (clears[i] > 0) return PlayerRank.values()[i];
        return null;
    }

    public NonNullList<ItemStack> storage() { return storage; }

    int bought(String entry, long day) {
        if (day != shopDay) return 0;
        return purchases.getOrDefault(entry, 0);
    }

    void addBought(String entry, long day, int n) {
        if (day != shopDay) {
            purchases.clear();
            shopDay = day;
        }
        purchases.merge(entry, n, Integer::sum);
    }

    void reset() {
        gold = 0;
        earned = 0;
        expansions = 0;
        java.util.Arrays.fill(clears, 0);
        licenseIssued = false;
        licenseIssuedDay = -1;
        purchases.clear();
        shopDay = -1;
        storage.replaceAll(s -> ItemStack.EMPTY);
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("gold", gold);
        tag.putLong("earned", earned);
        tag.putInt("expansions", expansions);
        tag.putIntArray("clears", clears);
        tag.putBoolean("license", licenseIssued);
        tag.putLong("licenseDay", licenseIssuedDay);
        CompoundTag bought = new CompoundTag();
        purchases.forEach(bought::putInt);
        tag.put("bought", bought);
        tag.putLong("shopDay", shopDay);
        ListTag items = new ListTag();
        for (int i = 0; i < storage.size(); i++) {
            ItemStack s = storage.get(i);
            if (s.isEmpty()) continue;
            CompoundTag t = (CompoundTag) s.save(provider);
            t.putShort("Slot", (short) i);
            items.add(t);
        }
        tag.put("storage", items);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        gold = tag.getLong("gold");
        earned = tag.getLong("earned");
        expansions = tag.getInt("expansions");
        int[] c = tag.getIntArray("clears");
        for (int i = 0; i < Math.min(c.length, clears.length); i++) clears[i] = c[i];
        licenseIssued = tag.getBoolean("license");
        licenseIssuedDay = tag.contains("licenseDay") ? tag.getLong("licenseDay") : -1;
        purchases.clear();
        CompoundTag bought = tag.getCompound("bought");
        for (String k : bought.getAllKeys()) purchases.put(k, bought.getInt(k));
        shopDay = tag.contains("shopDay") ? tag.getLong("shopDay") : -1;
        storage.replaceAll(s -> ItemStack.EMPTY);
        for (Tag t : tag.getList("storage", Tag.TAG_COMPOUND)) {
            CompoundTag ct = (CompoundTag) t;
            int slot = ct.getShort("Slot");
            if (slot >= 0 && slot < storage.size()) storage.set(slot, ItemStack.parseOptional(provider, ct));
        }
    }
}
