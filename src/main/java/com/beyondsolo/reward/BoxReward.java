package com.beyondsolo.reward;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * What a box gives: items, gold and stat points, and the kind of thing it is (shown on the card). One plain or
 * cursed box rolls one of these; a blessed box rolls three and the hunter takes one.
 */
public record BoxReward(Category category, List<ItemStack> items, long gold, int statPoints) {
    /** The kinds of things a box can hold. */
    public enum Category {
        GOLD, REMEDIES, WEAPON, ARMOR, CORES, TOME, RUNE, KEY, STATS;

        public Component displayName() {
            return Component.translatable("box.beyondsolo.category." + name().toLowerCase());
        }

        /** A line on what this kind of thing is for (the blessed box's cards). */
        public Component hint() {
            return Component.translatable("box.beyondsolo.category." + name().toLowerCase() + ".hint");
        }

        public static Category byIndex(int i) {
            Category[] all = values();
            return all[Math.max(0, Math.min(all.length - 1, i))];
        }
    }

    public BoxReward {
        items = List.copyOf(items);
    }

    public static BoxReward gold(long gold) {
        return new BoxReward(Category.GOLD, List.of(), gold, 0);
    }

    public static BoxReward stats(int points) {
        return new BoxReward(Category.STATS, List.of(), 0, points);
    }

    public static BoxReward items(Category category, List<ItemStack> items) {
        return new BoxReward(category, items, 0, 0);
    }

    /** The first item, or empty: the one drawn big on the card. */
    public ItemStack lead() {
        return items.isEmpty() ? ItemStack.EMPTY : items.get(0);
    }

    public CompoundTag save(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("category", category.ordinal());
        tag.putLong("gold", gold);
        tag.putInt("stats", statPoints);
        ListTag list = new ListTag();
        for (ItemStack s : items) if (!s.isEmpty()) list.add(s.save(provider));
        tag.put("items", list);
        return tag;
    }

    public static BoxReward load(CompoundTag tag, HolderLookup.Provider provider) {
        List<ItemStack> items = new ArrayList<>();
        for (Tag t : tag.getList("items", Tag.TAG_COMPOUND)) {
            ItemStack s = ItemStack.parseOptional(provider, (CompoundTag) t);
            if (!s.isEmpty()) items.add(s);
        }
        return new BoxReward(Category.byIndex(tag.getInt("category")), items, tag.getLong("gold"), tag.getInt("stats"));
    }
}
