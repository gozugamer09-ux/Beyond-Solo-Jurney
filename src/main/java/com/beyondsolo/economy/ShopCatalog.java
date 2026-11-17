package com.beyondsolo.economy;

import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.item.arsenal.Arsenal;
import com.beyondsolo.registry.ModItems;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Everything the System shop sells. The same list is built on both sides, so the client can draw the shop
 * without asking the server. Some things need a rank, some can only be bought a few times a day, and three
 * entries a day are on sale ({@link #featured}).
 */
public final class ShopCatalog {
    public enum Category { FEATURED, SUPPLIES, REMEDIES, WEAPONS, ARMOR, FORGE, SPECIAL }

    /** Specials that are not a plain item. */
    public enum Special { NONE, EXPANSION, LICENSE }

    /** Featured entries cost this fraction of their price. */
    public static final float DEAL = 0.7f;
    public static final int FEATURED_COUNT = 3;

    /**
     * @param id         stable id (also what a purchase is counted under)
     * @param stack      one lot (count included)
     * @param price      gold for one lot
     * @param minRank    rank needed to buy
     * @param dailyLimit lots per in-game day, 0 for no limit
     */
    public record Entry(String id, Category category, Supplier<ItemStack> stack, long price, PlayerRank minRank,
                        int dailyLimit, Special special) {
        public ItemStack display() {
            return stack.get();
        }

        public boolean stackable() {
            return special == Special.NONE && display().getMaxStackSize() > 1;
        }
    }

    private static Map<String, Entry> entries;

    public static synchronized Map<String, Entry> entries() {
        if (entries == null) entries = build();
        return entries;
    }

    public static @Nullable Entry get(String id) {
        return entries().get(id);
    }

    public static List<Entry> of(Category category, long day) {
        if (category == Category.FEATURED) return featured(day);
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries().values()) if (e.category() == category) out.add(e);
        return out;
    }

    /** Today's three deals, the same for everyone: picked from remedies, weapons and armor by the day number. */
    public static List<Entry> featured(long day) {
        List<Entry> pool = new ArrayList<>();
        for (Entry e : entries().values()) {
            if (e.category() == Category.REMEDIES || e.category() == Category.WEAPONS || e.category() == Category.ARMOR) pool.add(e);
        }
        Collections.shuffle(pool, new java.util.Random(day * 0x9E3779B97F4A7C15L + 1234567L));
        return List.copyOf(pool.subList(0, Math.min(FEATURED_COUNT, pool.size())));
    }

    public static boolean isFeatured(Entry e, long day) {
        for (Entry f : featured(day)) if (f.id().equals(e.id())) return true;
        return false;
    }

    /** The price of one lot today. Expansions get dearer with each one bought. */
    public static long price(Entry e, long day, int expansions) {
        if (e.special() == Special.EXPANSION) return e.price() << Math.min(expansions, 8);
        return isFeatured(e, day) ? Math.round(e.price() * DEAL) : e.price();
    }

    // ------------------------------------------------------------------ the stock

    private static Map<String, Entry> build() {
        Map<String, Entry> m = new LinkedHashMap<>();
        // supplies
        item(m, Category.SUPPLIES, Items.COOKED_BEEF, 8, 40, PlayerRank.E);
        item(m, Category.SUPPLIES, Items.GOLDEN_CARROT, 8, 90, PlayerRank.E);
        item(m, Category.SUPPLIES, Items.TORCH, 32, 20, PlayerRank.E);
        item(m, Category.SUPPLIES, Items.ARROW, 32, 40, PlayerRank.E);
        item(m, Category.SUPPLIES, Items.SHIELD, 1, 80, PlayerRank.E);
        item(m, Category.SUPPLIES, Items.ENDER_PEARL, 4, 120, PlayerRank.D);
        item(m, Category.SUPPLIES, Items.GOLDEN_APPLE, 1, 150, PlayerRank.D);
        item(m, Category.SUPPLIES, Items.EXPERIENCE_BOTTLE, 8, 160, PlayerRank.C);
        // remedies: potions and the System's own cures
        potion(m, "healing_potion", Potions.STRONG_HEALING, 60, PlayerRank.E);
        potion(m, "regeneration_potion", Potions.REGENERATION, 90, PlayerRank.E);
        potion(m, "swiftness_potion", Potions.SWIFTNESS, 60, PlayerRank.E);
        potion(m, "fire_resistance_potion", Potions.LONG_FIRE_RESISTANCE, 110, PlayerRank.D);
        potion(m, "strength_potion", Potions.STRENGTH, 140, PlayerRank.D);
        item(m, Category.REMEDIES, ModItems.ANTIDOTE.get(), 1, 50, PlayerRank.E);
        item(m, Category.REMEDIES, ModItems.CLOTTING_TONIC.get(), 1, 60, PlayerRank.E);
        item(m, Category.REMEDIES, ModItems.QUENCHING_ELIXIR.get(), 1, 180, PlayerRank.D);
        item(m, Category.REMEDIES, ModItems.GREATER_ANTIDOTE.get(), 1, 220, PlayerRank.D);
        // System potions (cheaper to brew in the CRAFT tab)
        item(m, Category.REMEDIES, com.beyondsolo.crafting.CraftingModule.HEALING_DRAUGHT.get(), 1, 70, PlayerRank.E);
        item(m, Category.REMEDIES, com.beyondsolo.crafting.CraftingModule.MANA_DRAUGHT.get(), 1, 70, PlayerRank.E);
        item(m, Category.REMEDIES, com.beyondsolo.crafting.CraftingModule.STAMINA_TONIC.get(), 1, 90, PlayerRank.E);
        item(m, Category.REMEDIES, com.beyondsolo.crafting.CraftingModule.GREATER_HEALING_DRAUGHT.get(), 1, 300, PlayerRank.C);
        // weapons: the arsenal up to epic; legendary and up only come from gates
        for (var w : Arsenal.ofGrade(ItemGrade.COMMON)) gear(m, Category.WEAPONS, w.get(), 180, PlayerRank.E, 0);
        for (var w : Arsenal.ofGrade(ItemGrade.RARE)) gear(m, Category.WEAPONS, w.get(), 650, PlayerRank.D, 0);
        for (var w : Arsenal.ofGrade(ItemGrade.EPIC)) gear(m, Category.WEAPONS, w.get(), 2400, PlayerRank.C, 1);
        // armor: the three sets
        long[] armorPrice = {120, 350, 900};
        PlayerRank[] armorRank = {PlayerRank.E, PlayerRank.D, PlayerRank.C};
        for (int i = 0; i < ModItems.ARMOR.size(); i++) {
            int set = Math.min(2, i / 4);
            gear(m, Category.ARMOR, ModItems.ARMOR.get(i).get(), armorPrice[set], armorRank[set], 0);
        }
        // the forge: refining stones, ward seals, infusion crystals
        item(m, Category.FORGE, com.beyondsolo.gear.GearModule.REFINING_STONE.get(), 4, 240, PlayerRank.E);
        add(m, new Entry("ward_seal", Category.FORGE, () -> new ItemStack(com.beyondsolo.gear.GearModule.WARD_SEAL.get()), 900, PlayerRank.C, 2, Special.NONE));
        com.beyondsolo.gear.GearModule.CRYSTALS.values().forEach(c -> item(m, Category.FORGE, c.get(), 1, 350, PlayerRank.D));
        // special
        add(m, new Entry("skill_tome", Category.SPECIAL, com.beyondsolo.skill.SkillTomeItem::unread, 1500, PlayerRank.D, 1, Special.NONE));
        add(m, new Entry("sealed_rune", Category.SPECIAL, () -> com.beyondsolo.rune.Runes.sealed(PlayerRank.D), 2000, PlayerRank.D, 1, Special.NONE));
        // dungeon keys: a run whenever you like, a few a day (higher keys come from gates and the forge)
        add(m, new Entry("dungeon_key_e", Category.SPECIAL, () -> com.beyondsolo.key.DungeonKeys.key(PlayerRank.E), 450, PlayerRank.E, 3, Special.NONE));
        add(m, new Entry("dungeon_key_d", Category.SPECIAL, () -> com.beyondsolo.key.DungeonKeys.key(PlayerRank.D), 1200, PlayerRank.D, 2, Special.NONE));
        add(m, new Entry("dungeon_key_c", Category.SPECIAL, () -> com.beyondsolo.key.DungeonKeys.key(PlayerRank.C), 3200, PlayerRank.C, 1, Special.NONE));
        for (var e : com.beyondsolo.rune.Engraving.values()) {
            add(m, new Entry("rune_of_" + e.id(), Category.SPECIAL, () -> new ItemStack(e.item()), 3500, PlayerRank.C, 1, Special.NONE));
        }
        add(m, new Entry("citadel_key", Category.SPECIAL, () -> new ItemStack(com.beyondsolo.tower.TowerModule.CITADEL_KEY.get()), 12000,
                com.beyondsolo.tower.TowerAPI.KEY_RANK, 1, Special.NONE));
        add(m, new Entry("totem_of_undying", Category.SPECIAL, () -> new ItemStack(Items.TOTEM_OF_UNDYING), 3000, PlayerRank.B, 1, Special.NONE));
        add(m, new Entry("storage_expansion", Category.SPECIAL, () -> new ItemStack(Items.CHEST), 800, PlayerRank.E, 0, Special.EXPANSION));
        add(m, new Entry("hunter_license", Category.SPECIAL, () -> new ItemStack(EconomyModule.HUNTER_LICENSE.get()), 100, PlayerRank.E, 0, Special.LICENSE));
        return Collections.unmodifiableMap(m);
    }

    private static void add(Map<String, Entry> m, Entry e) {
        m.put(e.id(), e);
    }

    private static void item(Map<String, Entry> m, Category c, Item item, int count, long price, PlayerRank rank) {
        add(m, new Entry(BuiltInRegistries.ITEM.getKey(item).getPath(), c, () -> new ItemStack(item, count), price, rank, 0, Special.NONE));
    }

    private static void gear(Map<String, Entry> m, Category c, Item item, long price, PlayerRank rank, int limit) {
        add(m, new Entry(BuiltInRegistries.ITEM.getKey(item).getPath(), c, () -> new ItemStack(item), price, rank, limit, Special.NONE));
    }

    private static void potion(Map<String, Entry> m, String id, Holder<Potion> potion, long price, PlayerRank rank) {
        add(m, new Entry(id, Category.REMEDIES, () -> PotionContents.createItemStack(Items.POTION, potion), price, rank, 0, Special.NONE));
    }

    /** Picks a random entry (for tests). */
    static Entry any(RandomSource r) {
        List<Entry> all = new ArrayList<>(entries().values());
        return all.get(r.nextInt(all.size()));
    }

    private ShopCatalog() {}
}
