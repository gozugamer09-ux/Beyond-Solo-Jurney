package com.beyondsolo.gear;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.item.SystemArmorItem;
import dev.shadowsoffire.apothic_attributes.api.ALObjects;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

/**
 * Turns a stack's bonus stats and enhancement level into attribute modifiers while it is held or worn, so they show
 * in the attribute tooltip and stack with everything else like any other gear stat.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class GearAttributes {
    @SubscribeEvent
    static void modifiers(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        GearKind kind = GearKind.of(stack);
        if (kind == null) return;
        EquipmentSlotGroup slot = kind == GearKind.ARMOR && stack.getItem() instanceof SystemArmorItem a
                ? EquipmentSlotGroup.bySlot(a.getEquipmentSlot()) : EquipmentSlotGroup.MAINHAND;
        String where = slot.getSerializedName();

        BonusStats stats = stack.get(GearModule.BONUS_STATS.get());
        if (stats != null) {
            int i = 0;
            for (BonusStats.Roll r : stats.rolls()) {
                event.addModifier(r.stat().attribute(), new AttributeModifier(
                        BeyondSolo.id("gear.bonus." + r.stat().id() + "." + i++ + "." + where), r.value(), r.stat().operation), slot);
            }
        }

        int level = Enhancement.level(stack);
        if (level > 0) {
            switch (kind) {
                case MELEE -> event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                        BeyondSolo.id("gear.enhance.damage"), Enhancement.damageBonus(level), Operation.ADD_MULTIPLIED_TOTAL), slot);
                case BOW -> event.addModifier(ALObjects.Attributes.ARROW_DAMAGE, new AttributeModifier(
                        BeyondSolo.id("gear.enhance.arrow"), Enhancement.damageBonus(level), Operation.ADD_VALUE), slot);
                case ARMOR -> {
                    event.addModifier(Attributes.ARMOR, new AttributeModifier(
                            BeyondSolo.id("gear.enhance.armor." + where), level * Enhancement.armorPerLevel(), Operation.ADD_VALUE), slot);
                    event.addModifier(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(
                            BeyondSolo.id("gear.enhance.toughness." + where), level * 0.25, Operation.ADD_VALUE), slot);
                }
            }
        }
    }

    private GearAttributes() {}
}
