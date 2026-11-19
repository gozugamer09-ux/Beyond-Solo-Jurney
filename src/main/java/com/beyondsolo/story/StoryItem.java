package com.beyondsolo.story;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * One of the things the hunter's story turns on (the Long Sleep's ingredients and its elixir): a name in its own
 * colour, a line on what it is, and a shimmer when it is the elixir.
 */
public class StoryItem extends Item {
    private final int color;
    private final boolean shimmer;

    public StoryItem(int color, boolean shimmer, Properties props) {
        super(props);
        this.color = color;
        this.shimmer = shimmer;
    }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withStyle(s -> s.withColor(TextColor.fromRgb(color)));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return shimmer || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.beyondsolo.story.tag").withStyle(s -> s.withColor(TextColor.fromRgb(0xFFB38A)).withItalic(true)));
    }
}
