package com.beyondsolo.gear;

import com.beyondsolo.economy.SellPrices;
import com.beyondsolo.item.ItemGrade;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * Gear enhancement, +1 to +10. Each try costs gold (by grade and level) and refining stones, and can fail: the
 * chance falls from 100% for +1 to 20% for +10. A failure from +6 up also knocks the gear down a level, unless a
 * ward seal takes the blow (the seal is used up only when it saves something). Nothing ever breaks.
 */
public final class Enhancement {
    public static final int MAX = 10;
    /** Success chance (percent) of reaching level i (index 1..10). */
    public static final int[] CHANCE = {100, 100, 95, 90, 80, 70, 60, 50, 40, 30, 20};
    /** Weapon damage bonus (percent) at each level. Bows get the same on arrows. */
    public static final int[] DAMAGE = {0, 3, 6, 9, 13, 17, 22, 27, 33, 40, 50};
    /** Failing at this level or above (trying for it) drops the gear one level. */
    public static final int RISKY_FROM = 7;

    public enum Outcome { SUCCESS, FAILED, DOWNGRADED, WARDED, MAXED, INVALID }

    public static int level(ItemStack stack) {
        Integer l = stack.get(GearModule.ENHANCE.get());
        return l == null ? 0 : l;
    }

    public static void set(ItemStack stack, int level) {
        if (level <= 0) stack.remove(GearModule.ENHANCE.get());
        else stack.set(GearModule.ENHANCE.get(), Math.min(MAX, level));
    }

    /** Chance (0..1) of the next try succeeding. */
    public static float chance(int current) {
        return current >= MAX ? 0 : CHANCE[current + 1] / 100f;
    }

    /** Gold for one try at the next level. */
    public static long gold(ItemGrade grade, int current) {
        long base = SellPrices.GRADE[grade.ordinal()];
        return Math.max(20, Math.round(base * (0.3 + 0.12 * current)));
    }

    /** Refining stones for one try at the next level. */
    public static int stones(int current) {
        return current < 4 ? 1 : current < 7 ? 2 : 3;
    }

    /** Whether failing the next try would cost a level. */
    public static boolean risky(int current) {
        return current + 1 >= RISKY_FROM;
    }

    /** Armor points per level, on top of the piece's own. */
    public static float armorPerLevel() {
        return 0.5f;
    }

    public static float damageBonus(int level) {
        return DAMAGE[Math.max(0, Math.min(MAX, level))] / 100f;
    }

    /**
     * Rolls one try. Changes the stack's level; costs are paid by the caller. {@code ward}: a ward seal is ready to
     * take a downgrade.
     */
    public static Outcome attempt(ItemStack stack, RandomSource r, boolean ward) {
        if (GearKind.of(stack) == null) return Outcome.INVALID;
        int now = level(stack);
        if (now >= MAX) return Outcome.MAXED;
        if (r.nextFloat() < chance(now)) {
            set(stack, now + 1);
            return Outcome.SUCCESS;
        }
        if (!risky(now)) return Outcome.FAILED;
        if (ward) return Outcome.WARDED;
        set(stack, now - 1);
        return Outcome.DOWNGRADED;
    }

    private Enhancement() {}
}
