package com.beyondsolo.gear;

import com.beyondsolo.skill.SkillElement;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Refining stones, ward seals and infusion crystals: what the System forge uses up. */
public class ForgeMaterialItem extends Item {
    private final @Nullable SkillElement element;

    public ForgeMaterialItem(@Nullable SkillElement element, Properties props) {
        super(props);
        this.element = element;
    }

    /** The element an infusion crystal carries (null for stones and seals). */
    public @Nullable SkillElement element() {
        return element;
    }

    @Override
    public Component getName(ItemStack stack) {
        Component name = super.getName(stack);
        return element == null ? name : name.copy().withStyle(s -> s.withColor(TextColor.fromRgb(element.color())));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return element != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        if (element != null) {
            lines.add(Component.translatable("gear.beyondsolo.crystal.desc", element.displayName())
                    .withStyle(s -> s.withColor(TextColor.fromRgb(element.color()))));
        } else {
            lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("gear.beyondsolo.forge_hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
