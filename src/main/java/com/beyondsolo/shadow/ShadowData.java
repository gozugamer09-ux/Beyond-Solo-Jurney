package com.beyondsolo.shadow;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A necromancer's army: every shadow they have extracted (kept through death), which of them are out in the world
 * right now (not saved: shadows go back into their master's shadow when the master leaves), and the shadow essence
 * the dark has gathered for promotions ({@link ShadowPromotions}). Change it through {@link ShadowService}.
 */
public final class ShadowData implements INBTSerializable<CompoundTag> {
    private final List<ShadowRecord> shadows = new ArrayList<>();
    /** record id -> the entity standing in the world */
    private final Map<UUID, UUID> active = new LinkedHashMap<>();
    /** Shadow essence: what the souls that were not kept left behind, spent on promotions. */
    private int essence;

    public List<ShadowRecord> shadows() { return Collections.unmodifiableList(shadows); }
    public int size() { return shadows.size(); }
    public int essence() { return essence; }

    /** How many shadows carry a name their master gave them. */
    public int bonded() {
        int n = 0;
        for (ShadowRecord r : shadows) if (r.bonded()) n++;
        return n;
    }

    public @Nullable ShadowRecord get(UUID id) {
        for (ShadowRecord r : shadows) if (r.id().equals(id)) return r;
        return null;
    }

    public @Nullable ShadowRecord byIndex(int i) {
        return i >= 0 && i < shadows.size() ? shadows.get(i) : null;
    }

    public boolean isActive(UUID recordId) { return active.containsKey(recordId); }
    public @Nullable UUID entityOf(UUID recordId) { return active.get(recordId); }
    public Map<UUID, UUID> active() { return Collections.unmodifiableMap(active); }
    public int activeCount() { return active.size(); }

    /** Shadows standing guard somewhere that are not in the world right now (their spot unloaded, or another world). */
    public int awayPosts() {
        int n = 0;
        for (ShadowRecord r : shadows) if (r.posted() && !active.containsKey(r.id())) n++;
        return n;
    }

    /** How many of the master's places for shadows out are taken: those out, and the guards away at their posts. */
    public int used() { return active.size() + awayPosts(); }

    void add(ShadowRecord r) { shadows.add(r); }

    void setEssence(int amount) { essence = Math.max(0, amount); }

    void addEssence(int amount) { essence = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, (long) essence + amount)); }

    void remove(UUID id) {
        shadows.removeIf(r -> r.id().equals(id));
        active.remove(id);
    }

    void setActive(UUID recordId, UUID entityId) { active.put(recordId, entityId); }
    void setInactive(UUID recordId) { active.remove(recordId); }
    void clearActive() { active.clear(); }

    void clear() {
        shadows.clear();
        active.clear();
        essence = 0;
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (ShadowRecord r : shadows) list.add(r.save());
        tag.put("shadows", list);
        tag.putInt("essence", essence);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        shadows.clear();
        active.clear();
        ListTag list = tag.getList("shadows", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) shadows.add(ShadowRecord.load(list.getCompound(i)));
        essence = Math.max(0, tag.getInt("essence"));
    }
}
