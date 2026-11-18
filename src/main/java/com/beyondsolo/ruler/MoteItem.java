package com.beyondsolo.ruler;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A Mote of Radiance: a mote of a ruler's light, left to the hunters who fought at its side or felled it. Used, it
 * gives the Blessing of Light ({@code rulers.blessingMinutes}): harder blows on monarchs and their soldiers, and a
 * monarch's presence weighs nothing. The light doesn't answer a crowned hunter.
 */
public class MoteItem extends Item {
    public MoteItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer p)) return InteractionResultHolder.success(stack);
        if (!Rulers.bless(p, RulerConfig.blessingTicks(), false)) {
            p.displayClientMessage(Component.translatable("item.beyondsolo.mote_of_radiance.crowned").withStyle(ChatFormatting.GOLD), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!p.getAbilities().instabuild) stack.shrink(1);
        p.getCooldowns().addCooldown(this, 40);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.beyondsolo.mote_of_radiance.desc", RulerConfig.blessingTicks() / 1200).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.beyondsolo.mote_of_radiance.blessing", Math.round(RulerConfig.blessingMight() * 100))
                .withStyle(ChatFormatting.GOLD));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
