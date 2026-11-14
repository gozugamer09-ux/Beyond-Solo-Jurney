package com.beyondsolo.quest;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** What finishing a quest gives. Applied by {@link QuestEngine#complete}. */
public record QuestRewards(long xp, int statPoints, float mana, List<ItemStack> items, String title) {
    public static final QuestRewards NONE = new QuestRewards(0, 0, 0, List.of(), "");

    public QuestRewards {
        items = List.copyOf(items);
        title = title == null ? "" : title;
    }

    CompoundTag save(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("xp", xp);
        tag.putInt("statPoints", statPoints);
        tag.putFloat("mana", mana);
        tag.putString("title", title);
        ListTag list = new ListTag();
        for (ItemStack stack : items) if (!stack.isEmpty()) list.add(stack.save(provider));
        tag.put("items", list);
        return tag;
    }

    static QuestRewards load(CompoundTag tag, HolderLookup.Provider provider) {
        List<ItemStack> items = new ArrayList<>();
        for (Tag t : tag.getList("items", Tag.TAG_COMPOUND)) {
            ItemStack stack = ItemStack.parseOptional(provider, (CompoundTag) t);
            if (!stack.isEmpty()) items.add(stack);
        }
        return new QuestRewards(tag.getLong("xp"), tag.getInt("statPoints"), tag.getFloat("mana"), items, tag.getString("title"));
    }
}
