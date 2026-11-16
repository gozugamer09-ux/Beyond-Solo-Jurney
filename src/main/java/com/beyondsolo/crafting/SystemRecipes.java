package com.beyondsolo.crafting;

import com.beyondsolo.enemy.Affinity;
import com.beyondsolo.gear.GearModule;
import com.beyondsolo.item.arsenal.Arsenal;
import com.beyondsolo.registry.ModItems;
import com.beyondsolo.skill.SkillElement;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Every System recipe. The same list is built on both sides (like the shop), so the CRAFT tab draws it without
 * asking. Ingredients come from the inventory first, then the System storage. Numbers are first guesses; the
 * balancing pass tunes them.
 */
public final class SystemRecipes {
    public enum Category { POTIONS, FORGE, CORES, GEAR, RUNES, KEYS }

    /**
     * One ingredient line.
     *
     * @param match what counts
     * @param count how many
     * @param icon  what the tab shows
     * @param label lang key for a line that is not one plain item ("any venom core"), else null
     */
    public record Need(Predicate<ItemStack> match, int count, Supplier<ItemStack> icon, @Nullable String label) {
        public Component name() {
            return label != null ? Component.translatable(label) : icon.get().getHoverName();
        }
    }

    /**
     * @param gear the output is graded gear: it rolls bonus stats when made
     */
    public record Recipe(String id, Category category, Supplier<ItemStack> output, List<Need> needs, long gold, PlayerRank minRank, boolean gear) {
        public ItemStack display() {
            return output.get();
        }
    }

    private static Map<String, Recipe> recipes;

    public static synchronized Map<String, Recipe> all() {
        if (recipes == null) recipes = build();
        return recipes;
    }

    public static @Nullable Recipe get(String id) {
        return all().get(id);
    }

    public static List<Recipe> of(Category c) {
        List<Recipe> out = new ArrayList<>();
        for (Recipe r : all().values()) if (r.category() == c) out.add(r);
        return out;
    }

    // ------------------------------------------------------------------ ingredients

    static Need item(Item item, int count) {
        return new Need(s -> s.is(item), count, () -> new ItemStack(item), null);
    }

    static Need core(PlayerRank rank, int count) {
        return item(CraftingModule.CORES.get(rank.tier()).get(), count);
    }

    static Need bossCore(PlayerRank rank, int count) {
        return item(CraftingModule.BOSS_CORES.get(rank.tier()).get(), count);
    }

    /** Any monster core (not a boss core) taken from a creature of this affinity, rank {@code min} or higher. */
    static Need affinityCore(Affinity a, PlayerRank min, int count) {
        return new Need(s -> s.getItem() instanceof MonsterCoreItem c && !c.boss() && c.rank().tier() >= min.tier() && MonsterCoreItem.affinity(s) == a,
                count, () -> MonsterCoreItem.of(min, false, a, 1), "craft.beyondsolo.need.affinity_core." + a.name().toLowerCase());
    }

    static Need bottle() {
        return item(Items.GLASS_BOTTLE, 1);
    }

    // ------------------------------------------------------------------ the book

    private static Map<String, Recipe> build() {
        Map<String, Recipe> m = new LinkedHashMap<>();
        PlayerRank E = PlayerRank.E, D = PlayerRank.D, C = PlayerRank.C, B = PlayerRank.B, A = PlayerRank.A;
        // potions
        add(m, Category.POTIONS, CraftingModule.HEALING_DRAUGHT.get(), 1, 10, E, bottle(), core(E, 1));
        add(m, Category.POTIONS, CraftingModule.GREATER_HEALING_DRAUGHT.get(), 1, 60, C, bottle(), core(C, 1), item(CraftingModule.HOLLOW_ESSENCE.get(), 1));
        add(m, Category.POTIONS, CraftingModule.SUPREME_HEALING_ELIXIR.get(), 1, 400, A, bottle(), core(A, 1), item(CraftingModule.GREATER_MANA_STONE.get(), 1));
        add(m, Category.POTIONS, CraftingModule.MANA_DRAUGHT.get(), 1, 10, E, bottle(), item(CraftingModule.MANA_SHARD.get(), 2));
        add(m, Category.POTIONS, CraftingModule.GREATER_MANA_DRAUGHT.get(), 1, 60, C, bottle(), item(CraftingModule.MANA_STONE.get(), 1), core(D, 1));
        add(m, Category.POTIONS, CraftingModule.STAMINA_TONIC.get(), 1, 15, E, bottle(), core(E, 1), item(CraftingModule.GLOOMFANG_FANG.get(), 2));
        add(m, Category.POTIONS, CraftingModule.DRAUGHT_OF_MIGHT.get(), 1, 40, D, bottle(), core(D, 1), item(CraftingModule.ASHEN_PLATE.get(), 1));
        add(m, Category.POTIONS, CraftingModule.IRONSKIN_DRAUGHT.get(), 1, 40, D, bottle(), core(D, 1), item(CraftingModule.SHARD_CARAPACE.get(), 2));
        add(m, Category.POTIONS, CraftingModule.ELIXIR_OF_REBIRTH.get(), 1, 250, C, bottle(), core(C, 2), item(CraftingModule.HOLLOW_ESSENCE.get(), 2), item(CraftingModule.MANA_STONE.get(), 1));
        add(m, Category.POTIONS, ModItems.ANTIDOTE.get(), 1, 10, E, bottle(), core(E, 1), item(CraftingModule.BROOD_SILK.get(), 1));
        add(m, Category.POTIONS, ModItems.CLOTTING_TONIC.get(), 1, 10, E, bottle(), core(E, 1), item(CraftingModule.GLOOMFANG_FANG.get(), 1));
        add(m, Category.POTIONS, ModItems.GREATER_ANTIDOTE.get(), 1, 50, D, bottle(), affinityCore(Affinity.VENOM, E, 1), item(CraftingModule.BROOD_SILK.get(), 2));
        add(m, Category.POTIONS, ModItems.QUENCHING_ELIXIR.get(), 1, 50, D, bottle(), core(D, 1), item(CraftingModule.EMBER_CINDER.get(), 2));
        // forge materials
        add(m, Category.FORGE, GearModule.REFINING_STONE.get(), 2, 20, E, core(E, 2), item(CraftingModule.MANA_SHARD.get(), 1));
        add(m, Category.FORGE, GearModule.WARD_SEAL.get(), 1, 150, C, bossCore(C, 1), item(CraftingModule.MANA_STONE.get(), 2), item(CraftingModule.ASHEN_PLATE.get(), 2));
        crystal(m, SkillElement.FIRE, item(CraftingModule.EMBER_CINDER.get(), 2), item(CraftingModule.MANA_STONE.get(), 1));
        crystal(m, SkillElement.WATER, affinityCore(Affinity.FROST, D, 1), item(CraftingModule.MANA_STONE.get(), 1));
        crystal(m, SkillElement.LIGHTNING, affinityCore(Affinity.STORM, D, 1), item(CraftingModule.MANA_STONE.get(), 1));
        crystal(m, SkillElement.NATURE, affinityCore(Affinity.VENOM, D, 1), item(CraftingModule.BROOD_SILK.get(), 2));
        crystal(m, SkillElement.SHADOW, affinityCore(Affinity.SHADE, D, 1), item(CraftingModule.HOLLOW_ESSENCE.get(), 1));
        crystal(m, SkillElement.EARTH, item(CraftingModule.SHARD_CARAPACE.get(), 3), item(CraftingModule.MANA_STONE.get(), 1));
        crystal(m, SkillElement.AIR, item(CraftingModule.GLOOMFANG_FANG.get(), 3), item(CraftingModule.MANA_STONE.get(), 1));
        crystal(m, SkillElement.LIGHT, item(CraftingModule.ASHEN_PLATE.get(), 2), item(CraftingModule.GREATER_MANA_STONE.get(), 1));
        crystal(m, SkillElement.ARCANE, item(CraftingModule.HOLLOW_ESSENCE.get(), 2), item(CraftingModule.GREATER_MANA_STONE.get(), 1));
        // cores: four of a rank fuse into one of the next; mana stones the same
        for (PlayerRank r : PlayerRank.values()) {
            if (r == PlayerRank.S) break;
            PlayerRank up = PlayerRank.values()[r.ordinal() + 1];
            add(m, "fuse_core_" + r.name().toLowerCase(), Category.CORES, () -> new ItemStack(CraftingModule.CORES.get(up.tier()).get()),
                    25L * (1L << r.tier()), r, false, core(r, 4));
        }
        add(m, "fuse_mana_stone", Category.CORES, () -> new ItemStack(CraftingModule.MANA_STONE.get()), 20, E, false, item(CraftingModule.MANA_SHARD.get(), 4));
        add(m, "fuse_greater_mana_stone", Category.CORES, () -> new ItemStack(CraftingModule.GREATER_MANA_STONE.get()), 80, C, false, item(CraftingModule.MANA_STONE.get(), 4));
        add(m, "extract_mana", Category.CORES, () -> new ItemStack(CraftingModule.MANA_SHARD.get(), 2), 0, E, false, core(D, 1));
        // gear: the epic arsenal, forged around a C-rank boss core
        gear(m, Arsenal.THUNDERFANG_LONGSWORD.get(), affinityCore(Affinity.STORM, C, 1), item(CraftingModule.ASHEN_PLATE.get(), 3));
        gear(m, Arsenal.CRIMSON_COMMANDER_BLADE.get(), affinityCore(Affinity.BLOOD, C, 1), item(CraftingModule.ASHEN_PLATE.get(), 3));
        gear(m, Arsenal.SWARM_KING_TALONS.get(), item(CraftingModule.BROOD_SILK.get(), 4), item(CraftingModule.GLOOMFANG_FANG.get(), 3));
        gear(m, Arsenal.FROSTBEAR_GAUNTLETS.get(), affinityCore(Affinity.FROST, C, 1), item(CraftingModule.SHARD_CARAPACE.get(), 3));
        gear(m, Arsenal.DAWNPETAL_RAPIER.get(), item(CraftingModule.GLOOMFANG_FANG.get(), 3), item(CraftingModule.ASHEN_PLATE.get(), 2));
        gear(m, Arsenal.EMBER_SAGE_STAFF.get(), affinityCore(Affinity.FLAME, C, 1), item(CraftingModule.EMBER_CINDER.get(), 3));
        gear(m, Arsenal.LIFEBLOOM_STAFF.get(), item(CraftingModule.HOLLOW_ESSENCE.get(), 3), item(CraftingModule.BROOD_SILK.get(), 2));
        gear(m, Arsenal.SERPENTSTING_BOW.get(), affinityCore(Affinity.VENOM, C, 1), item(CraftingModule.BROOD_SILK.get(), 3));
        // runes: sealing, fusing and the engraving runes
        com.beyondsolo.rune.RuneRecipes.add(m);
        com.beyondsolo.key.KeyRecipes.add(m);
        return Collections.unmodifiableMap(m);
    }

    private static void crystal(Map<String, Recipe> m, SkillElement e, Need... needs) {
        add(m, Category.FORGE, GearModule.CRYSTALS.get(e).get(), 1, 60, PlayerRank.D, needs);
    }

    private static void gear(Map<String, Recipe> m, Item weapon, Need... extra) {
        List<Need> needs = new ArrayList<>();
        needs.add(bossCore(PlayerRank.C, 1));
        needs.add(item(CraftingModule.MANA_STONE.get(), 2));
        needs.addAll(List.of(extra));
        add(m, BuiltInRegistries.ITEM.getKey(weapon).getPath(), Category.GEAR, () -> new ItemStack(weapon), 600, PlayerRank.C, true, needs.toArray(Need[]::new));
    }

    private static void add(Map<String, Recipe> m, Category c, Item out, int count, long gold, PlayerRank rank, Need... needs) {
        add(m, BuiltInRegistries.ITEM.getKey(out).getPath(), c, () -> new ItemStack(out, count), gold, rank, false, needs);
    }

    private static void add(Map<String, Recipe> m, String id, Category c, Supplier<ItemStack> out, long gold, PlayerRank rank, boolean gear, Need... needs) {
        m.put(id, new Recipe(id, c, out, List.of(needs), gold, rank, gear));
    }

    private SystemRecipes() {}
}
