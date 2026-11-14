package com.beyondsolo.gate;

import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.item.arsenal.Arsenal;
import com.beyondsolo.registry.ModItems;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.ArrayList;
import java.util.List;

/** What a reward cache holds, by the gate's rank: better gear, more remedies and more treasure the higher it goes. */
public final class GateLoot {
    public static List<ItemStack> roll(PlayerRank rank, RandomSource r) {
        int t = rank.tier();
        List<ItemStack> out = new ArrayList<>();
        out.add(new ItemStack(Items.EXPERIENCE_BOTTLE, 4 + 3 * t + r.nextInt(4)));
        out.add(new ItemStack(Items.EMERALD, 2 + 2 * t + r.nextInt(3)));
        if (t >= 1) out.add(new ItemStack(Items.GOLD_INGOT, 2 + t + r.nextInt(3)));
        if (t >= 2) out.add(new ItemStack(Items.DIAMOND, t - 1 + r.nextInt(2)));
        if (t >= 4) out.add(new ItemStack(Items.NETHERITE_SCRAP, t - 3));
        if (t >= 3) out.add(new ItemStack(Items.GOLDEN_APPLE, 1 + r.nextInt(2)));
        if (t >= 5 && r.nextBoolean()) out.add(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));

        // remedies for the gate's own element, and a little more
        out.add(new ItemStack(t >= 2 ? ModItems.GREATER_ANTIDOTE.get() : ModItems.ANTIDOTE.get(), 1 + r.nextInt(2)));
        out.add(new ItemStack(ModItems.CLOTTING_TONIC.get(), 1 + r.nextInt(2)));
        if (t >= 2) out.add(new ItemStack(ModItems.QUENCHING_ELIXIR.get()));

        // gear
        if (r.nextFloat() < 0.55f + 0.08f * t) out.add(weapon(t, r));
        if (t >= 4 && r.nextFloat() < 0.35f) out.add(weapon(t, r));
        int armorStart = t <= 1 ? 0 : t <= 2 ? 4 : 8;
        int pieces = r.nextFloat() < 0.7f ? 1 + (t >= 4 ? 1 : 0) : 0;
        for (int i = 0; i < pieces; i++) out.add(new ItemStack(ModItems.ARMOR.get(armorStart + r.nextInt(4)).get()));
        // skill tomes: rarer in low gates, almost certain at the top
        if (r.nextFloat() < 0.3f + 0.12f * t) out.add(com.beyondsolo.skill.SkillTomeItem.unread());
        if (t >= 4 && r.nextFloat() < 0.4f) out.add(com.beyondsolo.skill.SkillTomeItem.unread());
        return out;
    }

    /** Chance (out of 1000) of each grade, by gate rank E..S: common, rare, epic, legendary, mythic, divine. */
    static final int[][] GRADE_WEIGHTS = {
            {850, 150, 0, 0, 0, 0},
            {600, 350, 50, 0, 0, 0},
            {250, 500, 220, 30, 0, 0},
            {0, 450, 400, 140, 10, 0},
            {0, 200, 465, 310, 20, 5},
            {0, 0, 400, 520, 60, 20},
    };

    /** Rolls a weapon's grade for a gate of this rank. */
    public static ItemGrade rollGrade(int tier, RandomSource r) {
        int[] w = GRADE_WEIGHTS[Math.max(0, Math.min(tier, GRADE_WEIGHTS.length - 1))];
        int roll = r.nextInt(1000);
        for (int i = 0; i < w.length; i++) {
            roll -= w[i];
            if (roll < 0) return ItemGrade.values()[i];
        }
        return ItemGrade.COMMON;
    }

    static ItemStack weapon(int tier, RandomSource r) {
        ItemStack stack = Arsenal.random(rollGrade(tier, r), r);
        return stack != null ? stack : new ItemStack(ModItems.KNIGHT_SWORD.get());
    }

    private GateLoot() {}
}
