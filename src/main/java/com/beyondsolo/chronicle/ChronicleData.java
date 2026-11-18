package com.beyondsolo.chronicle;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A hunter's chronicle ({@code beyondsolo:chronicle}, kept through death): the titles they have earned (and when), the
 * feats the chronicle counts itself (creatures slain, bosses, dungeon breaks...), the journal's pages (what they have
 * seen, how many they have slain, which forms of a boss they have met) and what they have not looked at yet. The title
 * they wear stays in {@code SystemData} where the status window always read it. The client gets a copy.
 */
public final class ChronicleData implements INBTSerializable<CompoundTag> {
    /** One page of the journal. */
    public static final class Mark {
        /** Game time it was first seen. */
        public long seen;
        public int kills;
        /** Bit per form met (a boss's ranks, its ascended form). */
        public int forms;
    }

    private final Map<String, Long> titles = new LinkedHashMap<>();
    private final Map<String, Long> counters = new HashMap<>();
    private final Map<String, Mark> journal = new LinkedHashMap<>();
    private final Set<String> fresh = new LinkedHashSet<>();
    /** The hunter picked the title they wear themselves: a better one earned later does not replace it. */
    private boolean chosen;
    /** The title worn before the chronicle existed has been counted as earned. */
    private boolean migrated;

    // ------------------------------------------------------------------ titles

    public boolean hasTitle(String key) {
        return titles.containsKey(key);
    }

    /** Earned titles (lang keys), in the order earned, with the game time each was earned. */
    public Map<String, Long> titles() {
        return Collections.unmodifiableMap(titles);
    }

    /** True if it is new. */
    public boolean addTitle(String key, long time) {
        if (key == null || key.isEmpty() || titles.containsKey(key)) return false;
        titles.put(key, time);
        fresh.add(key);
        return true;
    }

    public boolean removeTitle(String key) {
        fresh.remove(key);
        return titles.remove(key) != null;
    }

    public boolean chosen() {
        return chosen;
    }

    public void setChosen(boolean chosen) {
        this.chosen = chosen;
    }

    public boolean migrated() {
        return migrated;
    }

    public void setMigrated() {
        migrated = true;
    }

    // ------------------------------------------------------------------ counters

    public long count(String counter) {
        return counters.getOrDefault(counter, 0L);
    }

    public long add(String counter, long n) {
        long v = count(counter) + n;
        counters.put(counter, v);
        return v;
    }

    /** Keeps the larger (a best, like the longest combo). True if it went up. */
    public boolean max(String counter, long value) {
        if (value <= count(counter)) return false;
        counters.put(counter, value);
        return true;
    }

    public void set(String counter, long value) {
        if (value == 0) counters.remove(counter);
        else counters.put(counter, value);
    }

    public Map<String, Long> counters() {
        return Collections.unmodifiableMap(counters);
    }

    // ------------------------------------------------------------------ journal

    public @Nullable Mark mark(String entry) {
        return journal.get(entry);
    }

    public boolean knows(String entry) {
        return journal.containsKey(entry);
    }

    /** The page is filled in (seen for the first time). True if it is new. */
    public boolean see(String entry, long time) {
        if (journal.containsKey(entry)) return false;
        Mark m = new Mark();
        m.seen = time;
        journal.put(entry, m);
        fresh.add(entry);
        return true;
    }

    /** One more slain (the page is filled in too, if it wasn't). */
    public void kill(String entry, long time) {
        see(entry, time);
        journal.get(entry).kills++;
    }

    /** A form met; true if it is a new one. */
    public boolean addForm(String entry, int bit, long time) {
        see(entry, time);
        Mark m = journal.get(entry);
        int before = m.forms;
        m.forms |= 1 << bit;
        return m.forms != before;
    }

    public int pages() {
        return journal.size();
    }

    public Map<String, Mark> journal() {
        return Collections.unmodifiableMap(journal);
    }

    public void forget(String entry) {
        journal.remove(entry);
        fresh.remove(entry);
    }

    // ------------------------------------------------------------------ not looked at yet

    public boolean isFresh(String id) {
        return fresh.contains(id);
    }

    public boolean markRead(String id) {
        return fresh.remove(id);
    }

    public Set<String> fresh() {
        return Collections.unmodifiableSet(fresh);
    }

    public void reset() {
        titles.clear();
        counters.clear();
        journal.clear();
        fresh.clear();
        chosen = false;
        migrated = false;
    }

    // ------------------------------------------------------------------ saving

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        CompoundTag t = new CompoundTag();
        titles.forEach(t::putLong);
        tag.put("titles", t);
        CompoundTag c = new CompoundTag();
        counters.forEach(c::putLong);
        tag.put("counters", c);
        CompoundTag j = new CompoundTag();
        journal.forEach((k, m) -> {
            CompoundTag e = new CompoundTag();
            e.putLong("t", m.seen);
            if (m.kills > 0) e.putInt("k", m.kills);
            if (m.forms != 0) e.putInt("f", m.forms);
            j.put(k, e);
        });
        tag.put("journal", j);
        ListTag f = new ListTag();
        for (String s : fresh) f.add(StringTag.valueOf(s));
        tag.put("fresh", f);
        tag.putBoolean("chosen", chosen);
        tag.putBoolean("migrated", migrated);
        return tag;
    }

    @Override
    public void deserializeNBT(@Nullable HolderLookup.Provider provider, CompoundTag tag) {
        reset();
        CompoundTag t = tag.getCompound("titles");
        for (String k : t.getAllKeys()) titles.put(k, t.getLong(k));
        CompoundTag c = tag.getCompound("counters");
        for (String k : c.getAllKeys()) counters.put(k, c.getLong(k));
        CompoundTag j = tag.getCompound("journal");
        for (String k : j.getAllKeys()) {
            CompoundTag e = j.getCompound(k);
            Mark m = new Mark();
            m.seen = e.getLong("t");
            m.kills = e.getInt("k");
            m.forms = e.getInt("f");
            journal.put(k, m);
        }
        ListTag f = tag.getList("fresh", Tag.TAG_STRING);
        for (int i = 0; i < f.size(); i++) fresh.add(f.getString(i));
        chosen = tag.getBoolean("chosen");
        migrated = tag.getBoolean("migrated");
    }
}
