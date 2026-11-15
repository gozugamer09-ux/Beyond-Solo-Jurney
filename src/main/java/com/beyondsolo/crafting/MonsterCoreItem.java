package com.beyondsolo.crafting;

import com.beyondsolo.enemy.Affinity;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A monster core: the crystallised heart a gate creature leaves behind, one item per rank (E..S), and a bigger,
 * brighter boss core per rank. A core taken from a creature with an affinity keeps it
 * ({@link CraftingModule#CORE_AFFINITY}), which some recipes ask for. Sold to the System shop or used in crafting.
 */
public class MonsterCoreItem extends Item {
    private final PlayerRank rank;
    private final boolean boss;

    public MonsterCoreItem(PlayerRank rank, boolean boss, Properties props) {
        super(props);
        this.rank = rank;
        this.boss = boss;
    }

    public PlayerRank rank() {
        return rank;
    }

    public boolean boss() {
        return boss;
    }

    /** The affinity a core was taken with, NONE if it has none (or is not a core). */
    public static Affinity affinity(ItemStack stack) {
        Affinity a = stack.get(CraftingModule.CORE_AFFINITY.get());
        return a == null ? Affinity.NONE : a;
    }

    /** A core of this rank, with an affinity unless NONE. */
    public static ItemStack of(PlayerRank rank, boolean boss, Affinity affinity, int count) {
        ItemStack s = new ItemStack((boss ? CraftingModule.BOSS_CORES : CraftingModule.CORES).get(rank.tier()).get(), count);
        if (affinity != Affinity.NONE) s.set(CraftingModule.CORE_AFFINITY.get(), affinity);
        return s;
    }

    @Override
    public Component getName(ItemStack stack) {
        Affinity a = affinity(stack);
        Component base = super.getName(stack);
        Component name = a == Affinity.NONE ? base : Component.translatable("item.beyondsolo.core.with_affinity", a.adjective(), base);
        int color = a == Affinity.NONE ? rank.color() : a.color;
        return name.copy().withStyle(s -> s.withColor(TextColor.fromRgb(color)));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return boss;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(boss ? "item.beyondsolo.boss_core.desc" : "item.beyondsolo.monster_core.desc", rank.displayName())
                .withStyle(s -> s.withColor(TextColor.fromRgb(rank.color()))));
        Affinity a = affinity(stack);
        if (a != Affinity.NONE && a.element != null) {
            lines.add(Component.translatable("item.beyondsolo.core.affinity", a.element.displayName())
                    .withStyle(s -> s.withColor(TextColor.fromRgb(a.color))));
        }
        lines.add(Component.translatable("craft.beyondsolo.hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
