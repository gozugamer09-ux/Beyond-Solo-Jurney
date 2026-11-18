package com.beyondsolo.chronicle.title;

import com.beyondsolo.combat.ModAttributes;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * What wearing a title does. Small on purpose (the numbers wait for the balancing pass). Some are attribute modifiers
 * put on the hunter while the title is worn; the others are read where they apply (XP, gold, damage dealt and taken),
 * see {@link TitleService}.
 */
public enum TitlePerk {
    /** More System XP. */
    XP(null, null, true),
    /** More gold from kills, quests, gates and the Citadel. */
    GOLD(null, null, true),
    /** More damage to every System creature (bosses too). */
    CREATURE_DAMAGE(null, null, true),
    /** More damage to bosses. */
    BOSS_DAMAGE(null, null, true),
    /** More damage to one family of creatures. */
    FAMILY_DAMAGE(null, null, true),
    /** More damage to creatures whose name burns red (far stronger than the hunter). */
    GIANT_DAMAGE(null, null, true),
    /** Less damage taken. */
    GUARD(null, null, true),
    /** Below 30% health: more damage dealt and less taken. */
    LAST_STAND(null, null, true),
    MIGHT(() -> Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, true),
    VITALITY(() -> Attributes.MAX_HEALTH, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, true),
    SWIFTNESS(() -> Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, true),
    ARMOR(() -> Attributes.ARMOR, AttributeModifier.Operation.ADD_VALUE, false),
    LUCK(() -> Attributes.LUCK, AttributeModifier.Operation.ADD_VALUE, false),
    ELEMENTAL(() -> ModAttributes.ELEMENTAL_POWER, AttributeModifier.Operation.ADD_VALUE, true),
    SPELL(() -> ModAttributes.SPELL_POWER, AttributeModifier.Operation.ADD_VALUE, true),
    FOCUS(() -> ModAttributes.MANA_EFFICIENCY, AttributeModifier.Operation.ADD_VALUE, true),
    HASTE(() -> ModAttributes.SKILL_HASTE, AttributeModifier.Operation.ADD_VALUE, true),
    STAMINA(() -> ModAttributes.EXHAUSTION_RESISTANCE, AttributeModifier.Operation.ADD_VALUE, true);

    /** Below this share of their health a hunter fights with {@link #LAST_STAND}. */
    public static final float LAST_STAND_BELOW = 0.3f;

    private final @Nullable Supplier<Holder<Attribute>> attribute;
    private final @Nullable AttributeModifier.Operation operation;
    private final boolean percent;

    TitlePerk(@Nullable Supplier<Holder<Attribute>> attribute, @Nullable AttributeModifier.Operation operation, boolean percent) {
        this.attribute = attribute;
        this.operation = operation;
        this.percent = percent;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The attribute it modifies while worn, or null if it is read where it applies. */
    public @Nullable Holder<Attribute> attribute() {
        return attribute == null ? null : attribute.get();
    }

    public @Nullable AttributeModifier.Operation operation() {
        return operation;
    }

    /** Shown as a percentage (0.05 reads "5"). */
    public boolean percent() {
        return percent;
    }

    /** The number as shown: "5" for 0.05 of a percentage, "2" for 2 armour, "7.5" when it has a half. */
    public String number(float value) {
        float v = percent ? value * 100f : value;
        float r = Math.round(v * 10f) / 10f;
        return r == Math.round(r) ? String.valueOf(Math.round(r)) : String.valueOf(r);
    }
}
