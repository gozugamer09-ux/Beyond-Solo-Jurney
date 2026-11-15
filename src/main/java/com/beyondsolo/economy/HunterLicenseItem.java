package com.beyondsolo.economy;

import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The hunter license: an ID card with the holder's rank, class, level and gates cleared. Issued at the evaluation,
 * kept up to date while it sits in its owner's inventory, and shown full screen with a right click.
 */
public class HunterLicenseItem extends Item {
    public HunterLicenseItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            LicenseInfo info = stack.get(EconomyModule.LICENSE.get());
            if (info != null) com.beyondsolo.client.economy.EconomyClient.openLicense(info);
        } else if (player instanceof ServerPlayer sp) {
            refresh(sp, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player) || player.tickCount % 20 != 7) return;
        refresh(player, stack);
    }

    /** Stamps the player's current numbers on their own card (or binds a blank card to them). */
    public static void refresh(ServerPlayer player, ItemStack stack) {
        LicenseInfo info = stack.get(EconomyModule.LICENSE.get());
        if (info != null && !info.owner().equals(player.getUUID())) return;
        if (info == null && !SystemAPI.isAwakened(player)) return;
        LicenseInfo now = LicenseInfo.of(player);
        if (!now.equals(info)) stack.set(EconomyModule.LICENSE.get(), now);
    }

    @Override
    public Component getName(ItemStack stack) {
        LicenseInfo info = stack.get(EconomyModule.LICENSE.get());
        Component base = super.getName(stack);
        if (info == null) return base;
        return base.copy().withStyle(s -> s.withColor(TextColor.fromRgb(info.playerRank().color())));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        LicenseInfo info = stack.get(EconomyModule.LICENSE.get());
        if (info == null) {
            lines.add(Component.translatable("tooltip.beyondsolo.license.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        lines.add(Component.literal(info.name()).withStyle(ChatFormatting.WHITE));
        Component rank = Component.translatable("tooltip.beyondsolo.license.rank", info.playerRank().displayName())
                .withStyle(s -> s.withColor(TextColor.fromRgb(info.playerRank().color())));
        lines.add(rank);
        PlayerClass cls = info.playerClass();
        if (cls != null) {
            Component c = info.sub() != null ? info.sub().displayName() : cls.displayName();
            lines.add(Component.translatable("tooltip.beyondsolo.license.class", c, info.level()).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("tooltip.beyondsolo.license.clears", info.clears()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.beyondsolo.license.number", info.number()).withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("tooltip.beyondsolo.license.use").withStyle(ChatFormatting.DARK_AQUA));
    }
}
