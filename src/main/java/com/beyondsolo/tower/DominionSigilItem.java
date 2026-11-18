package com.beyondsolo.tower;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * The Sigil of Dominion: a crown-shaped seal of black iron with a crimson heart, the Citadel lord's rare drop. Holding
 * one marks the hunter for good ({@link TowerAPI#hasSigil}): it opens the Sovereign boss rush, and it is what the
 * monarch path asks for: a hunter who holds it, has changed jobs and is high enough is called to the empty throne, and
 * raising it takes them there ({@link com.beyondsolo.monarch.MonarchAPI#raiseSigil}).
 */
public class DominionSigilItem extends Item {
    public DominionSigilItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof ServerPlayer p) || entity.tickCount % 40 != 0) return;
        TowerData d = TowerAPI.data(p);
        if (!d.sigil) {
            d.sigil = true;
            d.sigilDry = 0;
            TowerAPI.sync(p);
        }
        com.beyondsolo.monarch.MonarchAPI.callIfReady(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer p) {
            level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 0.5f);
            level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1f, 0.6f);
            com.beyondsolo.monarch.MonarchAPI.raiseSigil(p);
        }
        player.getCooldowns().addCooldown(this, 40);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.beyondsolo.sigil_of_dominion.tip").withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("item.beyondsolo.sigil_of_dominion.rush").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.beyondsolo.sigil_of_dominion.throne").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("item.beyondsolo.sigil_of_dominion.lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
