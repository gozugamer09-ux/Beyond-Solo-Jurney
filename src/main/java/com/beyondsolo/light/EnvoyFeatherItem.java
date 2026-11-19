package com.beyondsolo.light;

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
 * The Envoy's feather: the light gives it with its call to the Investiture. Raised, it begins the vigil where the hunter
 * stands ({@link Vigil#begin}); it isn't used up (a failed vigil can be kept again, later).
 */
public class EnvoyFeatherItem extends Item {
    public EnvoyFeatherItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer p)) return InteractionResultHolder.success(stack);
        if (!Vigil.begin(p)) return InteractionResultHolder.fail(stack);
        p.getCooldowns().addCooldown(this, 60);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.beyondsolo.envoy_feather.desc").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.beyondsolo.envoy_feather.use").withStyle(ChatFormatting.GOLD));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
