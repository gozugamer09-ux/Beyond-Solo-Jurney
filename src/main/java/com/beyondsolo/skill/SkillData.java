package com.beyondsolo.skill;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A player's skills: what they know (level and proficiency), the skill bar, cooldowns and short-lived buffs.
 * Stored as its own attachment ({@code ModAttachments.SKILLS}), kept through death, mirrored to the client.
 * Change it through {@link SkillAPI}.
 */
public final class SkillData implements INBTSerializable<CompoundTag> {
    public static final int SLOTS = 4;

    public static final class Known {
        int level;
        int proficiency;
        /** Game time it was learned, so the Skills tab can mark it NEW. */
        long learnedAt;

        Known(int level, int proficiency, long learnedAt) {
            this.level = level;
            this.proficiency = proficiency;
            this.learnedAt = learnedAt;
        }

        public int level() { return level; }
        public int proficiency() { return proficiency; }
        public long learnedAt() { return learnedAt; }
    }

    /** Learned skills by path, in the order they were learned. */
    private final Map<String, Known> known = new LinkedHashMap<>();
    private final String[] slots = new String[SLOTS];
    /** skill path -> game time it is ready again */
    private final Map<String, Long> readyAt = new LinkedHashMap<>();
    /** skill path -> cooldown length when it was started (for the sweep) */
    private final Map<String, Integer> cooldownLength = new LinkedHashMap<>();
    /** buff key -> game time it ends (e.g. venom_edge, shadowstep's empowered strike) */
    private final Map<String, Long> buffs = new LinkedHashMap<>();

    public SkillData() {
        Arrays.fill(slots, "");
    }

    // ---- reads ----

    public Map<String, Known> known() { return known; }
    public boolean knows(String path) { return known.containsKey(path); }
    public int level(String path) {
        Known k = known.get(path);
        return k == null ? 0 : k.level;
    }
    public @Nullable Known get(String path) { return known.get(path); }
    public String slot(int i) { return i >= 0 && i < SLOTS ? slots[i] : ""; }
    public int slotOf(String path) {
        for (int i = 0; i < SLOTS; i++) if (slots[i].equals(path)) return i;
        return -1;
    }
    public long readyAt(String path) { return readyAt.getOrDefault(path, 0L); }
    public int cooldownLength(String path) { return cooldownLength.getOrDefault(path, 0); }
    /** Ticks of cooldown left at this game time (0 = ready). */
    public long cooldownLeft(String path, long gameTime) { return Math.max(0, readyAt(path) - gameTime); }
    public boolean hasBuff(String key, long gameTime) { return buffs.getOrDefault(key, 0L) > gameTime; }
    public long buffEnds(String key) { return buffs.getOrDefault(key, 0L); }

    // ---- writes (SkillAPI) ----

    void learn(String path, int level, long time) {
        known.put(path, new Known(level, 0, time));
    }

    void forget(String path) {
        known.remove(path);
        int s = slotOf(path);
        if (s >= 0) slots[s] = "";
        readyAt.remove(path);
        cooldownLength.remove(path);
    }

    void setSlot(int i, String path) {
        if (i < 0 || i >= SLOTS) return;
        // a skill sits in one slot only
        for (int j = 0; j < SLOTS; j++) if (slots[j].equals(path)) slots[j] = "";
        slots[i] = path == null ? "" : path;
    }

    void startCooldown(String path, long gameTime, int ticks) {
        readyAt.put(path, gameTime + ticks);
        cooldownLength.put(path, ticks);
    }

    void clearCooldowns() {
        readyAt.clear();
        cooldownLength.clear();
    }

    void setBuff(String key, long until) {
        buffs.put(key, until);
    }

    void clearBuff(String key) {
        buffs.remove(key);
    }

    void reset() {
        known.clear();
        Arrays.fill(slots, "");
        readyAt.clear();
        cooldownLength.clear();
        buffs.clear();
    }

    // ---- save / sync ----

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (var e : known.entrySet()) {
            CompoundTag k = new CompoundTag();
            k.putString("id", e.getKey());
            k.putInt("level", e.getValue().level);
            k.putInt("proficiency", e.getValue().proficiency);
            k.putLong("learnedAt", e.getValue().learnedAt);
            list.add(k);
        }
        tag.put("known", list);
        ListTag slotTag = new ListTag();
        for (String s : slots) slotTag.add(StringTag.valueOf(s));
        tag.put("slots", slotTag);
        CompoundTag cd = new CompoundTag();
        for (var e : readyAt.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("at", e.getValue());
            c.putInt("len", cooldownLength.getOrDefault(e.getKey(), 0));
            cd.put(e.getKey(), c);
        }
        tag.put("cooldowns", cd);
        CompoundTag b = new CompoundTag();
        for (var e : buffs.entrySet()) b.putLong(e.getKey(), e.getValue());
        tag.put("buffs", b);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        reset();
        for (Tag t : tag.getList("known", Tag.TAG_COMPOUND)) {
            CompoundTag k = (CompoundTag) t;
            known.put(k.getString("id"), new Known(Math.max(1, k.getInt("level")), k.getInt("proficiency"), k.getLong("learnedAt")));
        }
        ListTag slotTag = tag.getList("slots", Tag.TAG_STRING);
        for (int i = 0; i < SLOTS && i < slotTag.size(); i++) slots[i] = slotTag.getString(i);
        CompoundTag cd = tag.getCompound("cooldowns");
        for (String key : cd.getAllKeys()) {
            CompoundTag c = cd.getCompound(key);
            readyAt.put(key, c.getLong("at"));
            cooldownLength.put(key, c.getInt("len"));
        }
        CompoundTag b = tag.getCompound("buffs");
        for (String key : b.getAllKeys()) buffs.put(key, b.getLong(key));
    }
}
