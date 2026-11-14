package com.beyondsolo.npc;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One villager's request board, stored on the villager. What is on it is not stored: every slot is rolled from
 * the villager, the day and the slot number ({@link NpcQuests#plan}), so all players see the same requests and
 * each gets them scaled to their own rank. The board only remembers which slots each player already took today,
 * and which players let this villager down (no requests from them until the next day).
 */
public final class NpcBoard implements INBTSerializable<CompoundTag> {
    private long day = -1;
    /** Bumped by /system npc reroll to roll new requests in the middle of a day. */
    private int rerolls;
    private final Map<UUID, Set<Integer>> taken = new HashMap<>();
    /** Player -> the last day they can't get requests here. */
    private final Map<UUID, Long> shunned = new HashMap<>();

    /** Starts a new day if {@code today} is one: every slot is open again. */
    public void roll(long today) {
        if (today == day) return;
        day = today;
        taken.clear();
        shunned.values().removeIf(until -> until < today);
    }

    public long day() { return day; }
    public int rerolls() { return rerolls; }

    public void reroll() {
        rerolls++;
        taken.clear();
    }

    public boolean isTaken(UUID player, int slot) {
        Set<Integer> s = taken.get(player);
        return s != null && s.contains(slot);
    }

    public void take(UUID player, int slot) {
        taken.computeIfAbsent(player, k -> new HashSet<>()).add(slot);
    }

    public boolean isShunned(UUID player, long today) {
        Long until = shunned.get(player);
        return until != null && until >= today;
    }

    /** No requests for this player until the day after {@code today}. */
    public void shun(UUID player, long today) {
        shunned.put(player, today);
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("day", day);
        tag.putInt("rerolls", rerolls);
        CompoundTag t = new CompoundTag();
        taken.forEach((id, slots) -> t.putIntArray(id.toString(), slots.stream().mapToInt(Integer::intValue).toArray()));
        tag.put("taken", t);
        CompoundTag s = new CompoundTag();
        shunned.forEach((id, until) -> s.putLong(id.toString(), until));
        tag.put("shunned", s);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        day = tag.contains("day") ? tag.getLong("day") : -1;
        rerolls = tag.getInt("rerolls");
        taken.clear();
        shunned.clear();
        CompoundTag t = tag.getCompound("taken");
        for (String key : t.getAllKeys()) {
            Tag v = t.get(key);
            if (!(v instanceof IntArrayTag arr)) continue;
            Set<Integer> slots = new HashSet<>();
            for (int i : arr.getAsIntArray()) slots.add(i);
            try {
                taken.put(UUID.fromString(key), slots);
            } catch (IllegalArgumentException ignored) {
            }
        }
        CompoundTag s = tag.getCompound("shunned");
        for (String key : s.getAllKeys()) {
            try {
                shunned.put(UUID.fromString(key), s.getLong(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
