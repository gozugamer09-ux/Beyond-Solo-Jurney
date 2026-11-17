package com.beyondsolo.rune;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * An engraving rune: use it to pick one of your skills, and the rune carves its {@link Engraving} into that skill
 * for good (the choice is made on the client's engraving screen; the server checks it again).
 */
public class EngravingRuneItem extends Item {
    private final Engraving engraving;

    public EngravingRuneItem(Engraving engraving, Properties properties) {
        super(properties);
        this.engraving = engraving;
    }

    public Engraving engraving() {
        return engraving;
    }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withStyle(Style.EMPTY.withColor(0xFF000000 | engraving.color));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(engraving.effect().copy().withStyle(Style.EMPTY.withColor(0xFF000000 | engraving.color)));
        tooltip.add(Component.translatable("rune.beyondsolo.tip.engraving").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("rune.beyondsolo.tip.engrave_use").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) Runes.clientEngrave.accept(hand, engraving);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
