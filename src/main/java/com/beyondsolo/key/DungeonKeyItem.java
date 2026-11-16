package com.beyondsolo.key;

import com.beyondsolo.gate.BossKind;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A dungeon key of one rank (see {@link DungeonKeys}). Hold use for a moment and turn it in the air: a lock of light
 * forms a few steps ahead, cracks, gives, and a gate of the key's rank opens there. Let go early and nothing is lost.
 */
public class DungeonKeyItem extends Item {
    private final PlayerRank rank;

    public DungeonKeyItem(PlayerRank rank, Properties properties) {
        super(properties);
        this.rank = rank;
    }

    public PlayerRank rank() {
        return rank;
    }

    @Override
    public Component getName(ItemStack stack) {
        int style = DungeonKeys.style(stack);
        String key = switch (style) {
            case 2 -> "item.beyondsolo.dungeon_key.ascendant";
            case 1 -> "item.beyondsolo.dungeon_key.sealed";
            default -> "item.beyondsolo.dungeon_key";
        };
        return Component.translatable(key, rank.displayName()).withStyle(Style.EMPTY.withColor(0xFF000000 | DungeonKeys.color(rank, style)));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        KeySeal seal = DungeonKeys.sealOf(stack);
        tooltip.add(Component.translatable("key.beyondsolo.tip.rank", rank.displayName()).withStyle(ChatFormatting.GOLD));
        BossKind boss = seal.boss();
        if (boss != null && boss.livesAt(rank)) {
            Component name = boss.name(rank);
            tooltip.add(Component.translatable(seal.ascendant() ? "key.beyondsolo.tip.sigil_ascendant" : "key.beyondsolo.tip.sigil", name)
                    .withStyle(seal.ascendant() ? ChatFormatting.YELLOW : ChatFormatting.LIGHT_PURPLE));
        } else if (seal.ascendant()) {
            tooltip.add(Component.translatable("key.beyondsolo.tip.ascendant").withStyle(ChatFormatting.YELLOW));
        }
        tooltip.add(Component.translatable("key.beyondsolo.tip.what").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("key.beyondsolo.tip.use").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        // the turn is drawn by client.key (the key held out and turned in the lock)
        return UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return DungeonKeys.USE_TICKS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            if (DungeonKeys.clientCheck.apply(player, stack) != null) return InteractionResultHolder.fail(stack);
        } else if (!(player instanceof ServerPlayer sp) || !DungeonKeys.begin(sp, stack)) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!level.isClientSide && entity instanceof ServerPlayer p) DungeonKeys.crack(p, DungeonKeys.USE_TICKS - remaining);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer p && DungeonKeys.finish(p, stack)) stack.consume(1, p);
        return stack;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        // let go early: the lock fades and the key stays
        if (!level.isClientSide && entity instanceof ServerPlayer p) DungeonKeys.cancel(p);
    }
}
