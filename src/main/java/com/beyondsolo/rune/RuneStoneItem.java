package com.beyondsolo.rune;

import com.beyondsolo.skill.Skill;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * A rune stone (see {@link Runes}). Hold use for two seconds: the stone cracks and its light leaks out, then it
 * shatters and the light pours into the holder. Let go early and nothing is lost.
 */
public class RuneStoneItem extends Item {
    public RuneStoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        Skill s = Runes.skillOf(stack);
        PlayerRank grade = Runes.gradeOf(stack);
        Component name = s == null
                ? Component.translatable("item.beyondsolo.rune_stone.sealed", grade.name())
                : Component.translatable("item.beyondsolo.rune_stone.named", s.displayName());
        return name.copy().withStyle(Style.EMPTY.withColor(0xFF000000 | Runes.gradeColor(grade)));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Skill s = Runes.skillOf(stack);
        PlayerRank grade = Runes.gradeOf(stack);
        tooltip.add(Component.translatable("rune.beyondsolo.tip.grade", grade.name()).withStyle(ChatFormatting.GOLD));
        if (s == null) {
            tooltip.add(Component.translatable("rune.beyondsolo.tip.sealed").withStyle(ChatFormatting.GRAY));
        } else {
            Component kind;
            if (s.rune()) kind = Component.translatable("rune.beyondsolo.tip.rune_art");
            else if (s.subclass() != null) kind = Component.translatable("rune.beyondsolo.tip.class_rune", s.subclass().displayName());
            else if (s.owner() != null) kind = Component.translatable("rune.beyondsolo.tip.class_rune", s.owner().displayName());
            else kind = Component.translatable("skills.beyondsolo.common");
            tooltip.add(kind.copy().withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable(s.isActive() ? "skills.beyondsolo.active" : "skills.beyondsolo.passive").copy()
                    .append(" · ").append(s.element().displayName())
                    .withStyle(Style.EMPTY.withColor(0xFF000000 | s.element().color())));
            tooltip.add(s.flavour().copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        tooltip.add(Component.translatable("rune.beyondsolo.tip.use", Runes.RAISE_LEVELS).withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        // the hold is drawn by client.rune (the stone raised to the chest, cracking)
        return UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return Runes.USE_TICKS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            if (Runes.clientCheck.apply(player, stack) != null) return InteractionResultHolder.fail(stack);
        } else if (!(player instanceof ServerPlayer sp) || !Runes.begin(sp, stack)) {
            return InteractionResultHolder.fail(stack);
        } else {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 0.6f);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.8f, 1.6f);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (level.isClientSide) return;
        int held = Runes.USE_TICKS - remaining;
        // the stone cracks twice before it gives
        if (held == Runes.USE_TICKS / 3 || held == Runes.USE_TICKS * 2 / 3) {
            float pitch = held < Runes.USE_TICKS / 2 ? 0.9f : 1.15f;
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.DEEPSLATE_TILES_BREAK, SoundSource.PLAYERS, 0.9f, pitch);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, pitch);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer p && Runes.absorb(p, stack)) {
            if (!p.isCreative()) stack.shrink(1);
        }
        return stack;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        // let go early: the light sinks back into the stone
        if (!level.isClientSide) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.AMETHYST_BLOCK_STEP, SoundSource.PLAYERS, 1f, 0.5f);
        }
    }
}
