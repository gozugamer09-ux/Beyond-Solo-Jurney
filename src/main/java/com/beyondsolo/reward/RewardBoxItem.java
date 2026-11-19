package com.beyondsolo.reward;

import com.beyondsolo.system.PlayerRank;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
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
 * A random box of a rank ({@link BoxKind}). Use it to open it: the System opens it for its hunter ({@link RewardBoxes#open}).
 * The rank sits in the stack's custom data, and a blessed box keeps the seed its three offers are rolled from.
 */
public class RewardBoxItem extends Item {
    private final BoxKind kind;

    public RewardBoxItem(BoxKind kind, Properties props) {
        super(props);
        this.kind = kind;
    }

    public BoxKind kind() {
        return kind;
    }

    public static PlayerRank rank(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        int t = tag.contains("rank") ? tag.getInt("rank") : 0;
        return PlayerRank.values()[Math.max(0, Math.min(PlayerRank.values().length - 1, t))];
    }

    /** A blessed box's seed, or 0 if it has none yet. */
    public static long seed(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getLong("seed");
    }

    static void setSeed(ItemStack stack, long seed) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putLong("seed", seed));
    }

    /** A box of {@code kind} and {@code rank}. */
    public static ItemStack of(BoxKind kind, PlayerRank rank, int count) {
        ItemStack stack = new ItemStack(RewardModule.box(kind), Math.max(1, count));
        CompoundTag tag = new CompoundTag();
        tag.putInt("rank", rank.tier());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer p)) return InteractionResultHolder.success(stack);
        return RewardBoxes.open(p, hand) ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.beyondsolo." + kind.id() + ".ranked", rank(stack).displayName());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.beyondsolo." + kind.id() + ".desc").withStyle(kind == BoxKind.CURSED ? ChatFormatting.RED : ChatFormatting.GRAY));
        lines.add(Component.translatable("item.beyondsolo.random_box.open").withStyle(ChatFormatting.DARK_AQUA));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return kind == BoxKind.BLESSED;
    }
}
