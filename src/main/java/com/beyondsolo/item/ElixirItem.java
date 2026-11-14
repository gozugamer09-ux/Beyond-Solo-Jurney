package com.beyondsolo.item;

import com.beyondsolo.combat.CombatFx;
import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.network.CombatFxPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Consumer;

/** A drinkable remedy: cures what it cures, then hands back the bottle. */
public class ElixirItem extends Item {
    private final DamageElement element;
    private final Consumer<LivingEntity> effect;

    public ElixirItem(DamageElement element, Consumer<LivingEntity> effect, Properties props) {
        super(props);
        this.element = element;
        this.effect = effect;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide) {
            effect.accept(entity);
            level.playSound(null, entity.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.4f);
            CombatFx.play(entity, CombatFxPayload.Kind.CURE, element.index());
        }
        if (entity instanceof ServerPlayer player) player.awardStat(Stats.ITEM_USED.get(this));
        if (entity instanceof Player player && player.getAbilities().instabuild) return stack;
        stack.shrink(1);
        if (stack.isEmpty()) return new ItemStack(Items.GLASS_BOTTLE);
        if (entity instanceof Player player && !player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE))) {
            player.drop(new ItemStack(Items.GLASS_BOTTLE), false);
        }
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 32;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
