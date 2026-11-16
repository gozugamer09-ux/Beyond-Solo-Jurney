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

    /** More damage from this entity's spells (magic skill hits): 0.2 = +20%. Staffs carry it. */
    public static final DeferredHolder<Attribute, Attribute> SPELL_POWER = ATTRIBUTES.register("spell_power",
            () -> new RangedAttribute("attribute.beyondsolo.spell_power", 0, 0, 10).setSyncable(true));

    /** Skills cost this fraction less mana (0.1 = 10% cheaper, at most {@link #MAX_MANA_EFFICIENCY}). Armor sets carry it. */
    public static final DeferredHolder<Attribute, Attribute> MANA_EFFICIENCY = ATTRIBUTES.register("mana_efficiency",
            () -> new RangedAttribute("attribute.beyondsolo.mana_efficiency", 0, 0, 1).setSyncable(true));
    /** Skills recharge this fraction faster (0.1 = 10% shorter cooldowns, at most {@link #MAX_SKILL_HASTE}). Armor sets carry it. */
    public static final DeferredHolder<Attribute, Attribute> SKILL_HASTE = ATTRIBUTES.register("skill_haste",
            () -> new RangedAttribute("attribute.beyondsolo.skill_haste", 0, 0, 1).setSyncable(true));
    public static final float MAX_MANA_EFFICIENCY = 0.5f;
    public static final float MAX_SKILL_HASTE = 0.4f;

    /** What a skill's mana cost is multiplied by for this caster (1 = full price). */
    public static float manaFactor(LivingEntity entity) {
        if (entity.getAttribute(MANA_EFFICIENCY) == null) return 1f;
        return 1f - (float) Math.min(MAX_MANA_EFFICIENCY, entity.getAttributeValue(MANA_EFFICIENCY));
    }

    /** What a skill's cooldown is multiplied by for this caster (1 = full length). */
    public static float hasteFactor(LivingEntity entity) {
        if (entity.getAttribute(SKILL_HASTE) == null) return 1f;
        return 1f - (float) Math.min(MAX_SKILL_HASTE, entity.getAttributeValue(SKILL_HASTE));
    }

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
                    EXHAUSTION_RESISTANCE, ELEMENTAL_POWER, SPELL_POWER, MANA_EFFICIENCY, SKILL_HASTE}) {
                if (!event.has(type, a)) event.add(type, a);
            }
        }
    }

    private ModAttributes() {}
}
