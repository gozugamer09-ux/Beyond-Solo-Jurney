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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Armor sets: their grade and weight, a bonus for wearing two pieces and a bigger one for all four. Boss sets
 * ({@code gear.BossSets}) also have a four-piece perk, a mechanic run by {@code gear.SetEffects}.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class ArmorSets {
    public static final class Set {
        public final String name;
        /** Extra sprint exhaustion per piece worn (0.15 = +15%). */
        public final float weight;
        public final ItemGrade grade;
        private final Supplier<Map<Holder<Attribute>, Double>> two;
        private final Supplier<Map<Holder<Attribute>, Double>> bonus;
        /** A four-piece mechanic run by SetEffects, or null. */
        public final @Nullable String perk;
        /** The boss whose signature loot this set is (a gate rank's name, e.g. "s"), or null. */
        public final @Nullable String boss;

        public Set(String name, float weight, ItemGrade grade, Supplier<Map<Holder<Attribute>, Double>> two,
                   Supplier<Map<Holder<Attribute>, Double>> bonus, @Nullable String perk, @Nullable String boss) {
            this.name = name;
            this.weight = weight;
            this.grade = grade;
            this.two = two;
            this.bonus = bonus;
            this.perk = perk;
            this.boss = boss;
        }

        /** Four-piece attribute bonus. */
        public Map<Holder<Attribute>, Double> bonus() {
            return bonus.get();
        }

        /** Two-piece attribute bonus. */
        public Map<Holder<Attribute>, Double> two() {
            return two.get();
        }

        ResourceLocation modifierId(Holder<Attribute> attribute, int pieces) {
            return BeyondSolo.id("set." + name + (pieces == 2 ? ".2." : ".") + attribute.unwrapKey().orElseThrow().location().getPath());
        }
    }

    private static final List<Set> REGISTERED = new ArrayList<>();
    public static final List<Set> ALL = Collections.unmodifiableList(REGISTERED);

    /** Light leather and steel. 2: 10% less exhaustion. 4: 20% more, faster on foot. */
    public static final Set HUNTER = register(new Set("hunter", 0f, ItemGrade.COMMON,
            () -> Map.of(ModAttributes.EXHAUSTION_RESISTANCE, 0.1),
            () -> Map.of(ModAttributes.EXHAUSTION_RESISTANCE, 0.2, Attributes.MOVEMENT_SPEED, 0.01), null, null));
    /** Heavy plate. 2: some blood resistance. 4: shrugs off bleeding and stands firm. */
    public static final Set KNIGHT = register(new Set("knight", 0.15f, ItemGrade.RARE,
            () -> Map.of(ModAttributes.BLOOD_RESISTANCE, 0.1),
            () -> Map.of(ModAttributes.BLOOD_RESISTANCE, 0.2, Attributes.KNOCKBACK_RESISTANCE, 0.2), null, null));
    /** Dark forged steel. Resists fire, poison and crimson. */
    public static final Set DUSK = register(new Set("dusk", 0.08f, ItemGrade.EPIC,
            () -> Map.of(ModAttributes.FIRE_RESISTANCE, 0.08, ModAttributes.POISON_RESISTANCE, 0.08, ModAttributes.CRIMSON_RESISTANCE, 0.08),
            () -> Map.of(ModAttributes.FIRE_RESISTANCE, 0.15, ModAttributes.POISON_RESISTANCE, 0.15, ModAttributes.CRIMSON_RESISTANCE, 0.15), null, null));

    public static Set register(Set set) {
        REGISTERED.add(set);
        return set;
    }

    public static @Nullable Set byName(String name) {
        for (Set s : REGISTERED) if (s.name.equals(name)) return s;
        return null;
    }

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public static int pieces(LivingEntity entity, Set set) {
        int n = 0;
        for (EquipmentSlot slot : ARMOR) {
            if (entity.getItemBySlot(slot).getItem() instanceof SystemArmorItem a && a.set() == set) n++;
        }
        return n;
    }

    /** True when the entity wears all four pieces of a set with this perk. */
    public static boolean hasPerk(LivingEntity entity, String perk) {
        for (Set s : REGISTERED) if (perk.equals(s.perk) && pieces(entity, s) == 4) return true;
        return false;
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
        for (Set set : REGISTERED) {
            int worn = pieces(entity, set);
            apply(entity, set, set.two(), 2, worn >= 2);
            apply(entity, set, set.bonus(), 4, worn == 4);
        }
    }

    private static void apply(LivingEntity entity, Set set, Map<Holder<Attribute>, Double> map, int pieces, boolean on) {
        map.forEach((attribute, amount) -> {
            AttributeInstance inst = entity.getAttribute(attribute);
            if (inst == null) return;
            ResourceLocation id = set.modifierId(attribute, pieces);
            if (on) {
                boolean multiplied = attribute == Attributes.MOVEMENT_SPEED || attribute == Attributes.MAX_HEALTH;
                inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount,
                        multiplied ? AttributeModifier.Operation.ADD_MULTIPLIED_BASE : AttributeModifier.Operation.ADD_VALUE));
            } else {
                inst.removeModifier(id);
            }
        });
    }

    @SubscribeEvent
    static void equipmentChanged(LivingEquipmentChangeEvent event) {
        if (!event.getEntity().level().isClientSide && event.getSlot().isArmor()) refresh(event.getEntity());
    }

    private ArmorSets() {}
}
