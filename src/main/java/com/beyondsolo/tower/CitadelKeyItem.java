package com.beyondsolo.tower;

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
 * The Key to the Crimson Citadel: an iron key with a crimson eye in its bow. Used, it binds to its holder for good
 * (it is used up) and the CITADEL page of the System window opens.
 */
public class CitadelKeyItem extends Item {
    public CitadelKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (player instanceof ServerPlayer sp && TowerAPI.bind(sp)) {
            if (!sp.getAbilities().instabuild) stack.shrink(1);
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.fail(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.beyondsolo.citadel_key.tip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.beyondsolo.citadel_key.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
