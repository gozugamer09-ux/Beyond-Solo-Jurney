package com.beyondsolo.item;

import com.beyondsolo.BeyondSolo;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.List;
import java.util.Map;

/**
 * A piece of a System armor set: vanilla armor values plus element resistances, a weight that makes sprinting
 * more tiring, and a set bonus when all four pieces are worn (see {@link ArmorSets}).
 */
public class SystemArmorItem extends ArmorItem {
    private final ArmorSets.Set set;
    private final Map<Holder<Attribute>, Double> extras;
    private ItemAttributeModifiers modifiers;

    public SystemArmorItem(Holder<ArmorMaterial> material, Type type, ArmorSets.Set set, Map<Holder<Attribute>, Double> extras, Properties props) {
        super(material, type, props);
        this.set = set;
        this.extras = extras;
    }

    public ArmorSets.Set set() {
        return set;
    }

    @Override
    @SuppressWarnings("deprecation")
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        if (modifiers == null) {
            ItemAttributeModifiers base = super.getDefaultAttributeModifiers();
            ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
            base.modifiers().forEach(e -> builder.add(e.attribute(), e.modifier(), e.slot()));
            EquipmentSlotGroup slot = EquipmentSlotGroup.bySlot(type.getSlot());
            extras.forEach((attribute, amount) -> builder.add(attribute, new AttributeModifier(
                    BeyondSolo.id("armor." + type.getName() + "." + attribute.unwrapKey().orElseThrow().location().getPath()),
                    amount, AttributeModifier.Operation.ADD_VALUE), slot));
            modifiers = builder.build();
        }
        return modifiers;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.beyondsolo.set." + set.name, Component.translatable("set.beyondsolo." + set.name))
                .withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("set.beyondsolo." + set.name + ".bonus").withStyle(ChatFormatting.GRAY));
        if (set.weight > 0) lines.add(Component.translatable("tooltip.beyondsolo.armor_weight").withStyle(ChatFormatting.DARK_GRAY));
    }
}
