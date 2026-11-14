package com.beyondsolo.combat;

import com.beyondsolo.BeyondSolo;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import org.jetbrains.annotations.Nullable;

/**
 * Combat attributes, on every living entity (players and mobs).
 * Resistances are fractions: 0.25 means 25% less buildup of that element. They are capped at 90%.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class ModAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, BeyondSolo.MOD_ID);

    public static final DeferredHolder<Attribute, Attribute> FIRE_RESISTANCE = resistance("fire_resistance");
    public static final DeferredHolder<Attribute, Attribute> POISON_RESISTANCE = resistance("poison_resistance");
    public static final DeferredHolder<Attribute, Attribute> CRIMSON_RESISTANCE = resistance("crimson_resistance");
    public static final DeferredHolder<Attribute, Attribute> BLOOD_RESISTANCE = resistance("blood_resistance");
    /** Less exhaustion from every source (players only use it). */
    public static final DeferredHolder<Attribute, Attribute> EXHAUSTION_RESISTANCE = resistance("exhaustion_resistance");
    /** More buildup dealt by this entity's elemental hits: 0.2 = +20%. */
    public static final DeferredHolder<Attribute, Attribute> ELEMENTAL_POWER = ATTRIBUTES.register("elemental_power",
            () -> new RangedAttribute("attribute.beyondsolo.elemental_power", 0, 0, 10).setSyncable(true));

    private static DeferredHolder<Attribute, Attribute> resistance(String name) {
        return ATTRIBUTES.register(name, () -> new RangedAttribute("attribute.beyondsolo." + name, 0, 0, 1).setSyncable(true));
    }

    /** The resistance attribute for an element, or null for physical. */
    public static @Nullable Holder<Attribute> resistanceFor(DamageElement element) {
        if (element == DamageElement.FIRE) return FIRE_RESISTANCE;
        if (element == DamageElement.POISON) return POISON_RESISTANCE;
        if (element == DamageElement.CRIMSON) return CRIMSON_RESISTANCE;
        if (element == DamageElement.BLOOD) return BLOOD_RESISTANCE;
        return null;
    }

    /** 0..0.9: how much of an element's buildup this entity shrugs off. */
    public static float resistance(LivingEntity entity, DamageElement element) {
        Holder<Attribute> attribute = resistanceFor(element);
        if (attribute == null || entity.getAttribute(attribute) == null) return 0;
        return (float) Math.min(0.9, entity.getAttributeValue(attribute));
    }

    @SubscribeEvent
    static void addToEntities(EntityAttributeModificationEvent event) {
        for (EntityType<? extends LivingEntity> type : event.getTypes()) {
            for (Holder<Attribute> a : new Holder[]{FIRE_RESISTANCE, POISON_RESISTANCE, CRIMSON_RESISTANCE, BLOOD_RESISTANCE,
                    EXHAUSTION_RESISTANCE, ELEMENTAL_POWER}) {
                if (!event.has(type, a)) event.add(type, a);
            }
        }
    }

    private ModAttributes() {}
}
