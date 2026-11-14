package com.beyondsolo.gate;

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
        List<? extends DeferredItem<? extends Item>> weapons = t <= 1
                ? List.of(ModItems.HUNTER_DAGGER, ModItems.KNIGHT_SWORD, ModItems.STEEL_GREATSWORD, ModItems.HUNTING_SPEAR)
                : t <= 3 ? List.of(ModItems.EMBERBRAND, ModItems.VENOM_FANG, ModItems.KNIGHT_SWORD, ModItems.STEEL_GREATSWORD)
                : List.of(ModItems.EMBERBRAND, ModItems.VENOM_FANG, ModItems.CRIMSON_REAPER, ModItems.BLOODLETTER);
        if (r.nextFloat() < 0.55f + 0.08f * t) out.add(new ItemStack(weapons.get(r.nextInt(weapons.size())).get()));
        int armorStart = t <= 1 ? 0 : t <= 2 ? 4 : 8;
        int pieces = r.nextFloat() < 0.7f ? 1 + (t >= 4 ? 1 : 0) : 0;
        for (int i = 0; i < pieces; i++) out.add(new ItemStack(ModItems.ARMOR.get(armorStart + r.nextInt(4)).get()));
        return out;
    }

    private GateLoot() {}
}
