package com.beyondsolo.item;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.ModAttributes;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** The three armor sets, their weight, and the bonus for wearing all four pieces. */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class ArmorSets {
    public static final class Set {
        public final String name;
        /** Extra sprint exhaustion per piece worn (0.15 = +15%). */
        public final float weight;
        private final Supplier<Map<Holder<Attribute>, Double>> bonus;

        Set(String name, float weight, Supplier<Map<Holder<Attribute>, Double>> bonus) {
            this.name = name;
            this.weight = weight;
            this.bonus = bonus;
        }

        public Map<Holder<Attribute>, Double> bonus() {
            return bonus.get();
        }

        ResourceLocation modifierId(Holder<Attribute> attribute) {
            return BeyondSolo.id("set." + name + "." + attribute.unwrapKey().orElseThrow().location().getPath());
        }
    }

    /** Light leather and steel. Full set: 20% less exhaustion, faster roll recharge. */
    public static final Set HUNTER = new Set("hunter", 0f, () -> Map.of(
            ModAttributes.EXHAUSTION_RESISTANCE, 0.2,
            Attributes.MOVEMENT_SPEED, 0.01));
    /** Heavy plate. Full set: shrugs off bleeding and stands firm. */
    public static final Set KNIGHT = new Set("knight", 0.15f, () -> Map.of(
            ModAttributes.BLOOD_RESISTANCE, 0.2,
            Attributes.KNOCKBACK_RESISTANCE, 0.2));
    /** Dark forged steel. Full set: resists fire, poison and crimson. */
    public static final Set DUSK = new Set("dusk", 0.08f, () -> Map.of(
            ModAttributes.FIRE_RESISTANCE, 0.15,
            ModAttributes.POISON_RESISTANCE, 0.15,
            ModAttributes.CRIMSON_RESISTANCE, 0.15));

    public static final List<Set> ALL = List.of(HUNTER, KNIGHT, DUSK);

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public static int pieces(LivingEntity entity, Set set) {
        int n = 0;
        for (EquipmentSlot slot : ARMOR) {
            if (entity.getItemBySlot(slot).getItem() instanceof SystemArmorItem a && a.set() == set) n++;
        }
        return n;
    }

    /** Sum of the weight of everything worn: extra sprint exhaustion as a fraction. */
    public static float weight(LivingEntity entity) {
        float w = 0;
        for (EquipmentSlot slot : ARMOR) {
            if (entity.getItemBySlot(slot).getItem() instanceof SystemArmorItem a) w += a.set().weight;
        }
        return w;
    }

    public static void refresh(LivingEntity entity) {
        for (Set set : ALL) {
            boolean full = pieces(entity, set) == 4;
            set.bonus().forEach((attribute, amount) -> {
                AttributeInstance inst = entity.getAttribute(attribute);
                if (inst == null) return;
                ResourceLocation id = set.modifierId(attribute);
                if (full) {
                    inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount,
                            attribute == Attributes.MOVEMENT_SPEED ? AttributeModifier.Operation.ADD_MULTIPLIED_BASE : AttributeModifier.Operation.ADD_VALUE));
                } else {
                    inst.removeModifier(id);
                }
            });
        }
    }

    @SubscribeEvent
    static void equipmentChanged(LivingEquipmentChangeEvent event) {
        if (!event.getEntity().level().isClientSide && event.getSlot().isArmor()) refresh(event.getEntity());
    }

    private ArmorSets() {}
}
