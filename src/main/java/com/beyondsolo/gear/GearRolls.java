package com.beyondsolo.gear;

import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.registry.ModComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Rolls a dropped piece of gear: its grade and its random bonus stats. Higher grades roll more stats and bigger
 * numbers: common 0-1, rare 1, epic 2, legendary 3, mythic 4, divine 5. Gear bought in the shop is plain (no stats),
 * so the best pieces always come out of gates.
 */
public final class GearRolls {
    /** Stats rolled by grade (common may roll none, see {@link #count}). */
    public static final int[] COUNT = {1, 1, 2, 3, 4, 5};

    /** Multiplier on a stat's roll range by grade: common 1x up to divine 2.75x. */
    public static float gradeScale(ItemGrade grade) {
        return 1f + 0.35f * grade.tier();
    }

    public static int count(ItemGrade grade, RandomSource r) {
        if (grade == ItemGrade.COMMON) return r.nextFloat() < 0.5f ? 1 : 0;
        return COUNT[grade.tier()];
    }

    /** Rolls stats onto a piece of System gear at its current grade (does nothing for other items). */
    public static ItemStack roll(ItemStack stack, RandomSource r) {
        ItemGrade grade = ItemGrade.of(stack);
        return grade == null ? stack : roll(stack, grade, r);
    }

    /** Sets the grade (never below the item's own) and rolls fresh stats for it. */
    public static ItemStack roll(ItemStack stack, ItemGrade grade, RandomSource r) {
        GearKind kind = GearKind.of(stack);
        if (kind == null) return stack;
        ItemGrade base = stack.getItem().components().get(ModComponents.GRADE.get());
        if (base != null && base.tier() > grade.tier()) grade = base;
        stack.set(ModComponents.GRADE.get(), grade);
        List<BonusStat> pool = new ArrayList<>();
        for (BonusStat s : BonusStat.values()) if (s.fits(kind)) pool.add(s);
        int n = Math.min(pool.size(), count(grade, r));
        List<BonusStats.Roll> rolls = new ArrayList<>();
        float scale = gradeScale(grade);
        for (int i = 0; i < n; i++) {
            BonusStat s = pool.remove(r.nextInt(pool.size()));
            float v = (s.min + r.nextFloat() * (s.max - s.min)) * scale;
            v = Math.round(v * 1000) / 1000f;
            rolls.add(new BonusStats.Roll(s, v));
        }
        if (rolls.isEmpty()) stack.remove(GearModule.BONUS_STATS.get());
        else stack.set(GearModule.BONUS_STATS.get(), new BonusStats(rolls));
        return stack;
    }

    /**
     * Armor dropped in a gate may come out one grade above its set's own (15%, 25% from A rank), capped at
     * legendary: a rare Knight helm can drop epic. Weapons keep their grade: each one is designed for it.
     */
    public static ItemGrade dropGrade(ItemStack stack, int gateTier, RandomSource r) {
        ItemGrade g = ItemGrade.of(stack);
        if (g == null) return ItemGrade.COMMON;
        if (GearKind.of(stack) == GearKind.ARMOR && g.tier() < ItemGrade.LEGENDARY.tier()
                && r.nextFloat() < (gateTier >= 4 ? 0.25f : 0.15f)) {
            return ItemGrade.values()[g.tier() + 1];
        }
        return g;
    }

    public static BonusStats stats(ItemStack stack) {
        BonusStats s = stack.get(GearModule.BONUS_STATS.get());
        return s == null ? BonusStats.NONE : s;
    }

    private GearRolls() {}
}
