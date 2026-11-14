package com.beyondsolo.combat;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.HashMap;
import java.util.Map;

/** Current buildup amounts on one entity, by buildup type id. */
public final class BuildupData implements INBTSerializable<CompoundTag> {
    private final Map<ResourceLocation, Float> amounts = new HashMap<>();

    public float get(ResourceLocation type) {
        return amounts.getOrDefault(type, 0f);
    }

    public void set(ResourceLocation type, float value) {
        if (value <= 0) amounts.remove(type);
        else amounts.put(type, value);
    }

    public Map<ResourceLocation, Float> view() {
        return Map.copyOf(amounts);
    }

    public boolean isEmpty() {
        return amounts.isEmpty();
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        amounts.forEach((id, v) -> tag.putFloat(id.toString(), v));
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        amounts.clear();
        for (String key : tag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null) amounts.put(id, tag.getFloat(key));
        }
    }
}
