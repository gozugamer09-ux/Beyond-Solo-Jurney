package com.beyondsolo.worldboss;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A Monarch's Seal: a world boss's rare gift to the hunter who did the most against it. Broken in the overworld, the
 * gate it held shut tears open in the sky, and a monarch comes down through it, some way off. Not while another great
 * threat is abroad.
 */
public class MonarchSealItem extends Item {
    public MonarchSealItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer p)) return InteractionResultHolder.success(stack);
        if (level.dimension() != Level.OVERWORLD) {
            p.displayClientMessage(Component.translatable("item.beyondsolo.monarch_seal.overworld").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        if (WorldBossData.get(server.getServer()).current() != null) {
            p.displayClientMessage(Component.translatable("item.beyondsolo.monarch_seal.busy").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        BlockPos spot = WorldBosses.findSpot(server, p.position(), 36, 64, server.random);
        if (spot == null) {
            p.displayClientMessage(Component.translatable("item.beyondsolo.monarch_seal.no_room").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        WorldBossKind[] monarchs = WorldBossKind.monarchs();
        WorldBosses.begin(server, monarchs[server.random.nextInt(monarchs.length)], spot, true);
        server.playSound(null, p.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2f, 0.4f);
        server.playSound(null, p.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.8f, 0.8f);
        if (!p.isCreative()) stack.shrink(1);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.beyondsolo.monarch_seal.desc").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.beyondsolo.monarch_seal.warn").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
