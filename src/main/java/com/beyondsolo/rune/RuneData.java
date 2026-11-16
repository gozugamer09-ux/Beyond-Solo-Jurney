package com.beyondsolo.rune;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A player's rune state: the engravings carved into their skills, and how many rune stones they have absorbed.
 * Stored as its own attachment ({@link RuneModule#RUNES}), kept through death, mirrored to the client. Change it
 * through {@link Runes}.
 */
public final class RuneData implements INBTSerializable<CompoundTag> {
    /** skill path -> its engraving */
    private final Map<String, Engraving> engravings = new LinkedHashMap<>();
    private int absorbed;

    public @Nullable Engraving engraving(String path) {
        return engravings.get(path);
    }

    public Map<String, Engraving> engravings() {
        return Collections.unmodifiableMap(engravings);
    }

    /** Rune stones absorbed in all. */
    public int absorbed() {
        return absorbed;
    }

    void engrave(String path, @Nullable Engraving e) {
        if (e == null) engravings.remove(path);
        else engravings.put(path, e);
    }

    void countAbsorbed() {
        absorbed++;
    }

    void clear() {
        engravings.clear();
        absorbed = 0;
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        CompoundTag eng = new CompoundTag();
        engravings.forEach((path, e) -> eng.putString(path, e.id()));
        tag.put("engravings", eng);
        tag.putInt("absorbed", absorbed);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        engravings.clear();
        CompoundTag eng = tag.getCompound("engravings");
        for (String path : eng.getAllKeys()) {
            Engraving e = Engraving.byId(eng.getString(path));
            if (e != null) engravings.put(path, e);
        }
        absorbed = tag.getInt("absorbed");
    }
}
