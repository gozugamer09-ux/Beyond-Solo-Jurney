package com.beyondsolo.item;

import com.beyondsolo.npc.NpcService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The sealed parcel of a delivery request. Use it at the marked spot to deliver it. It carries the request's token,
 * so it only counts for the request it came with, and it disappears when that request ends.
 */
public class ParcelItem extends Item {
    public ParcelItem(Properties properties) {
        super(properties);
    }

    public static String token(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("token");
    }

    public static ItemStack forToken(ItemStack stack, String token, String from) {
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("token", token);
        tag.putString("from", from);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        return NpcService.useParcel((ServerPlayer) player, stack)
                ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String from = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("from");
        if (!from.isEmpty()) tooltip.add(Component.translatable("item.beyondsolo.sealed_parcel.from", from).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.beyondsolo.sealed_parcel.hint").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
