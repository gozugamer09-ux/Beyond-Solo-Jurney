package com.beyondsolo.rune;

import com.beyondsolo.crafting.CraftingModule;
import com.beyondsolo.crafting.SystemRecipes;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * The RUNES page of the CRAFT tab: sealing cores into a rune stone, fusing three sealed stones into one of the next
 * grade, and the engraving runes. Recipes only ever take sealed stones, never inscribed ones, so a rune the player
 * wants to keep is never eaten. Numbers are first guesses; the balancing pass tunes them.
 */
public final class RuneRecipes {
    /** Sealed stones of a grade that fuse into one of the next. */
    public static final int FUSE = 3;

    /** Called from {@link SystemRecipes} while the book is built. */
    public static void add(Map<String, SystemRecipes.Recipe> m) {
        PlayerRank E = PlayerRank.E, C = PlayerRank.C;
        // a sealed E stone from cores and a mana stone
        put(m, "seal_rune", () -> Runes.sealed(E), 60, E,
                item(CraftingModule.CORES.get(E.tier()).get(), 4), item(CraftingModule.MANA_STONE.get(), 1));
        // three sealed stones of a grade fuse into one of the next
        for (PlayerRank r : PlayerRank.values()) {
            if (r == PlayerRank.S) break;
            PlayerRank up = PlayerRank.values()[r.ordinal() + 1];
            put(m, "fuse_rune_" + r.name().toLowerCase(Locale.ROOT), () -> Runes.sealed(up), 80L * (1L << r.tier()), r,
                    sealed(r, FUSE), item(CraftingModule.CORES.get(up.tier()).get(), 1),
                    item(r.tier() >= 3 ? CraftingModule.GREATER_MANA_STONE.get() : CraftingModule.MANA_STONE.get(), 1));
        }
        // the engraving runes: a sealed C stone, the matching creature's spoils and a greater mana stone
        put(m, "rune_of_haste", () -> new ItemStack(Engraving.HASTE.item()), 400, C,
                sealed(C, 1), item(CraftingModule.GLOOMFANG_FANG.get(), 3), item(CraftingModule.GREATER_MANA_STONE.get(), 1));
        put(m, "rune_of_thrift", () -> new ItemStack(Engraving.THRIFT.item()), 400, C,
                sealed(C, 1), item(CraftingModule.HOLLOW_ESSENCE.get(), 3), item(CraftingModule.GREATER_MANA_STONE.get(), 1));
        put(m, "rune_of_might", () -> new ItemStack(Engraving.MIGHT.item()), 400, C,
                sealed(C, 1), item(CraftingModule.ASHEN_PLATE.get(), 3), item(CraftingModule.GREATER_MANA_STONE.get(), 1));
    }

    /** Sealed rune stones of exactly this grade. */
    static SystemRecipes.Need sealed(PlayerRank grade, int count) {
        return new SystemRecipes.Need(s -> Runes.isSealed(s) && Runes.gradeOf(s) == grade, count, () -> Runes.sealed(grade), null);
    }

    static SystemRecipes.Need item(Item item, int count) {
        return new SystemRecipes.Need(s -> s.is(item), count, () -> new ItemStack(item), null);
    }

    private static void put(Map<String, SystemRecipes.Recipe> m, String id, java.util.function.Supplier<ItemStack> out, long gold, PlayerRank rank,
                            SystemRecipes.Need... needs) {
        m.put(id, new SystemRecipes.Recipe(id, SystemRecipes.Category.RUNES, out, List.of(needs), gold, rank, false));
    }

    private RuneRecipes() {}
}
