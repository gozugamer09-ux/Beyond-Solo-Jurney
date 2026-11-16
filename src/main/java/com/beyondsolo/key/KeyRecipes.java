package com.beyondsolo.key;

import com.beyondsolo.crafting.CraftingModule;
import com.beyondsolo.crafting.SystemRecipes;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The KEYS page of the CRAFT tab: a dungeon key forged around a boss core of its rank (so clearing a gate is what
 * pays for the next one), with monster cores, mana stones and a little metal. Numbers are first guesses; the
 * balancing pass tunes them.
 */
public final class KeyRecipes {
    /** Gold to forge a key, by rank E..S. */
    public static final long[] GOLD = {40, 120, 320, 800, 1800, 4000};

    /** Called from {@link SystemRecipes} while the book is built. */
    public static void add(Map<String, SystemRecipes.Recipe> m) {
        Item[] metal = {Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND, Items.DIAMOND, Items.NETHERITE_SCRAP, Items.NETHERITE_INGOT};
        int[] metalCount = {2, 2, 1, 2, 1, 1};
        for (PlayerRank r : PlayerRank.values()) {
            int t = r.tier();
            Item stone = t >= 3 ? CraftingModule.GREATER_MANA_STONE.get() : CraftingModule.MANA_STONE.get();
            int stones = t >= 3 ? t - 2 : t >= 2 ? 2 : 1;
            String id = "forge_key_" + r.name().toLowerCase(Locale.ROOT);
            m.put(id, new SystemRecipes.Recipe(id, SystemRecipes.Category.KEYS, () -> DungeonKeys.key(r), List.of(
                    item(CraftingModule.BOSS_CORES.get(t).get(), 1),
                    item(CraftingModule.CORES.get(t).get(), 4),
                    item(stone, stones),
                    item(metal[t], metalCount[t])), GOLD[t], r, false));
        }
    }

    static SystemRecipes.Need item(Item item, int count) {
        return new SystemRecipes.Need(s -> s.is(item), count, () -> new ItemStack(item), null);
    }

    private KeyRecipes() {}
}
