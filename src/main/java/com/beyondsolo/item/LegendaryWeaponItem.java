package com.beyondsolo.item;

import com.beyondsolo.combat.DamageElement;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Unbreakable;

import java.util.List;

/**
 * One of the legendary weapons: the strongest weapons in the game, and the hidden reward for killing the Hollow
 * Arbiter in the awakening trial. They never break, don't burn, and carry far more element than anything else.
 */
public class LegendaryWeaponItem extends SystemWeaponItem {
    public static final int GOLD = 0xFFD166;

    public LegendaryWeaponItem(Tier tier, int damage, float speed, DamageElement element, float buildup, float weight) {
        super(tier, damage, speed, element, buildup, weight, new Properties().rarity(Rarity.EPIC).fireResistant()
                .component(DataComponents.UNBREAKABLE, new Unbreakable(false)));
    }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withStyle(s -> s.withColor(TextColor.fromRgb(GOLD)).withBold(true));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.beyondsolo.legendary").withStyle(s -> s.withColor(TextColor.fromRgb(GOLD))));
        lines.add(Component.translatable(getDescriptionId() + ".lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        super.appendHoverText(stack, context, lines, flag);
    }
}
