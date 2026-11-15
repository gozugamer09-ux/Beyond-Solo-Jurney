package com.beyondsolo.economy;

import com.beyondsolo.item.ElixirItem;
import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.registry.ModComponents;
import com.beyondsolo.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * What the System shop pays for things, per item. Graded gear pays by its grade; loot from gates and monsters has
 * fixed prices; everything else is worth nothing to the System. Always well below what the shop charges, so
 * buying to sell never pays.
 */
public final class SellPrices {
    /** By grade: common .. divine. */
    public static final long[] GRADE = {40, 150, 600, 2000, 6000, 20000};

    /** Monster cores by rank E..S (boss cores are worth six times as much). */
    public static final long[] CORE = {20, 45, 100, 220, 500, 1200};

    private static Map<Item, Long> fixed;

    private static synchronized Map<Item, Long> fixed() {
        if (fixed != null) return fixed;
        Map<Item, Long> m = new IdentityHashMap<>();
        // gate treasure
        m.put(Items.EMERALD, 12L);
        m.put(Items.GOLD_INGOT, 6L);
        m.put(Items.GOLD_NUGGET, 0L);
        m.put(Items.IRON_INGOT, 3L);
        m.put(Items.DIAMOND, 40L);
        m.put(Items.NETHERITE_SCRAP, 90L);
        m.put(Items.NETHERITE_INGOT, 400L);
        m.put(Items.GOLDEN_APPLE, 30L);
        m.put(Items.ENCHANTED_GOLDEN_APPLE, 600L);
        m.put(Items.TOTEM_OF_UNDYING, 600L);
        m.put(Items.AMETHYST_SHARD, 2L);
        m.put(Items.LAPIS_LAZULI, 1L);
        m.put(Items.QUARTZ, 1L);
        // what monsters drop
        m.put(Items.ROTTEN_FLESH, 1L);
        m.put(Items.BONE, 1L);
        m.put(Items.STRING, 1L);
        m.put(Items.SPIDER_EYE, 2L);
        m.put(Items.GUNPOWDER, 3L);
        m.put(Items.ARROW, 0L);
        m.put(Items.SLIME_BALL, 2L);
        m.put(Items.ENDER_PEARL, 10L);
        m.put(Items.BLAZE_ROD, 8L);
        m.put(Items.GHAST_TEAR, 20L);
        m.put(Items.MAGMA_CREAM, 4L);
        m.put(Items.PHANTOM_MEMBRANE, 6L);
        m.put(Items.PRISMARINE_SHARD, 2L);
        m.put(Items.SHULKER_SHELL, 60L);
        m.put(Items.ECHO_SHARD, 50L);
        m.put(Items.NETHER_STAR, 1500L);
        m.put(Items.DRAGON_BREATH, 25L);
        // System things
        m.put(ModItems.SKILL_TOME.get(), 300L);
        m.put(com.beyondsolo.gear.GearModule.REFINING_STONE.get(), 15L);
        m.put(com.beyondsolo.gear.GearModule.WARD_SEAL.get(), 150L);
        com.beyondsolo.gear.GearModule.CRYSTALS.values().forEach(c -> m.put(c.get(), 60L));
        // monster cores, boss cores, mana stones, materials
        for (int i = 0; i < CORE.length; i++) {
            m.put(com.beyondsolo.crafting.CraftingModule.CORES.get(i).get(), CORE[i]);
            m.put(com.beyondsolo.crafting.CraftingModule.BOSS_CORES.get(i).get(), CORE[i] * 6);
        }
        long[] mana = {6, 30, 120};
        for (int i = 0; i < mana.length; i++) m.put(com.beyondsolo.crafting.CraftingModule.MANA_STONES.get(i).get(), mana[i]);
        com.beyondsolo.crafting.CraftingModule.MATERIALS.values().forEach(x -> m.put(x.get(), 8L));
        fixed = m;
        return m;
    }

    /** Gold for one of this item, 0 if the shop won't take it. */
    public static long each(ItemStack stack) {
        if (stack.isEmpty() || stack.has(EconomyModule.LICENSE.get())) return 0;
        if (stack.is(EconomyModule.HUNTER_LICENSE.get())) return 0;
        ItemGrade grade = stack.get(ModComponents.GRADE.get());
        if (grade != null) {
            long base = GRADE[grade.ordinal()];
            // refined gear and rolled stats are worth more
            int level = com.beyondsolo.gear.Enhancement.level(stack);
            int rolls = com.beyondsolo.gear.GearRolls.stats(stack).rolls().size();
            base = Math.round(base * (1 + 0.15 * level + 0.1 * rolls));
            // worn gear is worth less
            if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
                float health = 1f - (float) stack.getDamageValue() / stack.getMaxDamage();
                base = Math.max(1, Math.round(base * (0.4f + 0.6f * health)));
            }
            return base;
        }
        if (stack.getItem() instanceof ElixirItem || stack.getItem() instanceof com.beyondsolo.crafting.SystemPotionItem) return 15;
        Long v = fixed().get(stack.getItem());
        return v == null ? 0 : v;
    }

    /** Gold for the whole stack. */
    public static long of(ItemStack stack) {
        return each(stack) * stack.getCount();
    }

    private SellPrices() {}
}
