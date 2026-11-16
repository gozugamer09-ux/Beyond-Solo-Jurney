package com.beyondsolo.item;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.ModAttributes;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
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
 * ({@code gear.BossSets}) and the armory's sets ({@code armory.ArmorCatalog}) also have a four-piece perk, a mechanic
 * run by {@code gear.SetEffects} or {@code armory.SetPerks}.
 *
 * <p>Most sets are made for one class: anyone can wear them for their armor, but only a hunter of that class draws
 * out the set's bonuses and perk (creatures always do). A set of a gate rank needs a level to put on: E 1, D 10,
 * C 20, B 30, A 40, S 50, divine 60; divine pieces also refuse every other class.
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
        /** The class whose wearers get the bonuses and perk (null: anyone). */
        public @Nullable PlayerClass playerClass;
        /** Gate rank tier E..S = 0..5, {@link #DIVINE_TIER} for the divine sets, -1 for shop armor. */
        public int tier = -1;
        /** The level a hunter needs to put a piece on (0: none). */
        public int requiredLevel;
        /**
         * Where the set comes from, for tooltips and the drop tables: {@code boss:<kind>}, {@code creature:<kind>},
         * {@code villager:<set>} or {@code divine}. Null for shop armor.
         */
        public @Nullable String source;

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

        /** Made for this class: only its hunters draw out the bonuses. */
        public Set forClass(PlayerClass cls) {
            this.playerClass = cls;
            return this;
        }

        /** The gate rank tier the set belongs to; sets the level it needs. */
        public Set tier(int tier) {
            this.tier = tier;
            this.requiredLevel = levelFor(tier);
            return this;
        }

        public Set from(String source) {
            this.source = source;
            return this;
        }

        public boolean divine() {
            return tier >= DIVINE_TIER;
        }

        /** True for a boss's signature set or a divine set: the reward cache calls them out. */
        public boolean signature() {
            return divine() || (source != null && source.startsWith("boss:"));
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

    public static final int DIVINE_TIER = 6;

    /** The level a set of this tier needs: E 1, D 10, C 20, B 30, A 40, S 50, divine 60 (none for shop armor). */
    public static int levelFor(int tier) {
        return tier < 0 ? 0 : tier == 0 ? 1 : tier * 10;
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

    /** The four armor slots, helmet first. */
    public static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public static int pieces(LivingEntity entity, Set set) {
        int n = 0;
        for (EquipmentSlot slot : ARMOR) {
            if (entity.getItemBySlot(slot).getItem() instanceof SystemArmorItem a && a.set() == set) n++;
        }
        return n;
    }

    /**
     * True when this wearer draws out the set's bonuses: anyone for a classless set, any creature, otherwise only an
     * awakened hunter of the set's class.
     */
    public static boolean attuned(LivingEntity entity, Set set) {
        if (set.playerClass == null || !(entity instanceof Player p)) return true;
        SystemData d = SystemAPI.get(p);
        return d.isAwakened() && d.playerClass() == set.playerClass;
    }

    /** Why this hunter can't put on a piece of the set (a message), or null if they can. Creative players always can. */
    public static @Nullable Component refusal(Player player, Set set) {
        if (player.isCreative() || player.isSpectator()) return null;
        SystemData d = SystemAPI.get(player);
        if (set.divine() && set.playerClass != null && (!d.isAwakened() || d.playerClass() != set.playerClass)) {
            return Component.translatable("message.beyondsolo.armor_bound", set.playerClass.displayName());
        }
        if (set.requiredLevel > 1 && d.level() < set.requiredLevel) {
            return Component.translatable("message.beyondsolo.armor_level", set.requiredLevel, d.level());
        }
        return null;
    }

    /** The set the entity wears all four pieces of, if it draws out its bonuses; null otherwise. */
    public static @Nullable Set fullSet(LivingEntity entity) {
        if (!(entity.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof SystemArmorItem head)) return null;
        Set set = head.set();
        for (int i = 1; i < ARMOR.length; i++) {
            if (!(entity.getItemBySlot(ARMOR[i]).getItem() instanceof SystemArmorItem a) || a.set() != set) return null;
        }
        return attuned(entity, set) ? set : null;
    }

    /** True when the entity wears all four pieces of a set with this perk (and draws it out). */
    public static boolean hasPerk(LivingEntity entity, String perk) {
        Set set = fullSet(entity);
        return set != null && perk.equals(set.perk);
    }

    /** Sum of the weight of everything worn: extra sprint exhaustion as a fraction. */
    public static float weight(LivingEntity entity) {
        float w = 0;
        for (EquipmentSlot slot : ARMOR) {
            if (entity.getItemBySlot(slot).getItem() instanceof SystemArmorItem a) w += a.set().weight;
        }
        return w;
    }

    /** Puts on or takes off every set bonus to match what the entity wears (and whether it is attuned to it). */
    public static void refresh(LivingEntity entity) {
        for (Set set : REGISTERED) {
            int worn = pieces(entity, set);
            boolean on = worn >= 2 && attuned(entity, set);
            apply(entity, set, set.two(), 2, on);
            apply(entity, set, set.bonus(), 4, on && worn == 4);
        }
    }

    /** Movement speed and max health bonuses are fractions of the base; the rest are added as they are. */
    public static AttributeModifier.Operation operation(Holder<Attribute> attribute) {
        return attribute == Attributes.MOVEMENT_SPEED || attribute == Attributes.MAX_HEALTH
                ? AttributeModifier.Operation.ADD_MULTIPLIED_BASE : AttributeModifier.Operation.ADD_VALUE;
    }

    private static void apply(LivingEntity entity, Set set, Map<Holder<Attribute>, Double> map, int pieces, boolean on) {
        map.forEach((attribute, amount) -> {
            AttributeInstance inst = entity.getAttribute(attribute);
            if (inst == null) return;
            ResourceLocation id = set.modifierId(attribute, pieces);
            AttributeModifier current = inst.getModifier(id);
            if (on) {
                AttributeModifier.Operation op = operation(attribute);
                if (current == null || current.amount() != amount || current.operation() != op) {
                    inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
                }
            } else if (current != null) {
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
