package com.beyondsolo.crafting;

import com.beyondsolo.combat.CombatFx;
import com.beyondsolo.network.CombatFxPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
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

/**
 * A System potion: brewed from cores and materials in the CRAFT tab. Drinking one runs its effect, plays a rising
 * ring of its colour around the drinker ({@link CombatFxPayload.Kind#RESTORE}) and hands back the bottle.
 */
public class SystemPotionItem extends Item {
    private final int color;
    private final int drinkTicks;
    private final Consumer<LivingEntity> effect;

    public SystemPotionItem(int color, int drinkTicks, Consumer<LivingEntity> effect, Properties props) {
        super(props);
        this.color = color;
        this.drinkTicks = drinkTicks;
        this.effect = effect;
    }

    public int color() {
        return color;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide) {
            effect.accept(entity);
            level.playSound(null, entity.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.2f);
            level.playSound(null, entity.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.5f, 1.6f);
            CombatFx.play(entity, CombatFxPayload.Kind.RESTORE, color);
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
        return drinkTicks;
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
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withStyle(s -> s.withColor(TextColor.fromRgb(color)));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
