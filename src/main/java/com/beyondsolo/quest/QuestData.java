package com.beyondsolo.quest;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A player's quests: active ones, open offers, finished template ids (for quest lines and one-time quests),
 * trigger cooldowns and a few counters. Stored on the player, kept through death. Change it through
 * {@link QuestEngine}.
 */
public final class QuestData implements INBTSerializable<CompoundTag> {
    private final List<Quest> quests = new ArrayList<>();
    private final Set<String> finished = new LinkedHashSet<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    /** True once the first System quests were handed out after the evaluation. */
    private boolean started;
    /** The in-game day the current daily quest belongs to, or -1. */
    private long dailyDay = -1;
    private int completed;
    private int failed;

    public List<Quest> all() { return quests; }

    /** Quests the player has: in progress, or done and waiting to be handed in. */
    public List<Quest> active() {
        return quests.stream().filter(Quest::isTaken).toList();
    }

    /** Quests whose objectives are done and that wait to be handed in to their giver. */
    public List<Quest> ready() {
        return quests.stream().filter(q -> q.status() == Quest.Status.READY).toList();
    }

    public List<Quest> offers() {
        return quests.stream().filter(q -> q.status() == Quest.Status.OFFERED).toList();
    }

    @Nullable
    public Quest find(UUID id) {
        for (Quest q : quests) if (q.id().equals(id)) return q;
        return null;
    }

    public boolean has(String template) {
        for (Quest q : quests) if (q.template().equals(template)) return true;
        return false;
    }

    public boolean hasFinished(String template) { return finished.contains(template); }
    public boolean started() { return started; }
    public long dailyDay() { return dailyDay; }
    public int completedCount() { return completed; }
    public int failedCount() { return failed; }

    public long cooldownUntil(String key) {
        return cooldowns.getOrDefault(key, 0L);
    }

    void add(Quest quest) { quests.add(quest); }
    void remove(Quest quest) { quests.remove(quest); }
    void markFinished(String template) { finished.add(template); }
    void setStarted(boolean value) { started = value; }
    void setDailyDay(long day) { dailyDay = day; }
    void countCompleted() { completed++; }
    void countFailed() { failed++; }
    public void setCooldown(String key, long until) { cooldowns.put(key, until); }

    void reset() {
        quests.clear();
        finished.clear();
        cooldowns.clear();
        started = false;
        dailyDay = -1;
        completed = 0;
        failed = 0;
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Quest q : quests) list.add(q.save(provider));
        tag.put("quests", list);
        ListTag done = new ListTag();
        for (String s : finished) done.add(StringTag.valueOf(s));
        tag.put("finished", done);
        CompoundTag cd = new CompoundTag();
        cooldowns.forEach(cd::putLong);
        tag.put("cooldowns", cd);
        tag.putBoolean("started", started);
        tag.putLong("dailyDay", dailyDay);
        tag.putInt("completed", completed);
        tag.putInt("failed", failed);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        reset();
        for (Tag t : tag.getList("quests", Tag.TAG_COMPOUND)) quests.add(Quest.load((CompoundTag) t, provider));
        for (Tag t : tag.getList("finished", Tag.TAG_STRING)) finished.add(t.getAsString());
        CompoundTag cd = tag.getCompound("cooldowns");
        for (String key : cd.getAllKeys()) cooldowns.put(key, cd.getLong(key));
        started = tag.getBoolean("started");
        dailyDay = tag.contains("dailyDay") ? tag.getLong("dailyDay") : -1;
        completed = tag.getInt("completed");
        failed = tag.getInt("failed");
    }
}
