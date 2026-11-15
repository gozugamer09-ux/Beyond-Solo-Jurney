package com.beyondsolo.gear;

import com.beyondsolo.combat.ModAttributes;
import dev.shadowsoffire.apothic_attributes.api.ALObjects;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * A random bonus stat that dropped gear can roll. {@code min..max} is the roll range for a common item; every grade
 * above multiplies it (see {@link GearRolls#gradeScale}). Percent stats are stored as fractions (0.05 = 5%).
 */
public enum BonusStat {
    // weapons
    DAMAGE(Target.MELEE, () -> Attributes.ATTACK_DAMAGE, Operation.ADD_MULTIPLIED_TOTAL, 0.03f, 0.06f, true),
    ATTACK_SPEED(Target.MELEE, () -> Attributes.ATTACK_SPEED, Operation.ADD_MULTIPLIED_TOTAL, 0.03f, 0.06f, true),
    CRIT_CHANCE(Target.WEAPON, () -> ALObjects.Attributes.CRIT_CHANCE, Operation.ADD_VALUE, 0.02f, 0.04f, true),
    CRIT_DAMAGE(Target.WEAPON, () -> ALObjects.Attributes.CRIT_DAMAGE, Operation.ADD_VALUE, 0.05f, 0.10f, true),
    LIFE_STEAL(Target.WEAPON, () -> ALObjects.Attributes.LIFE_STEAL, Operation.ADD_VALUE, 0.01f, 0.025f, true),
    ARMOR_PIERCE(Target.WEAPON, () -> ALObjects.Attributes.ARMOR_PIERCE, Operation.ADD_VALUE, 0.5f, 1.5f, false),
    ARROW_DAMAGE(Target.BOW, () -> ALObjects.Attributes.ARROW_DAMAGE, Operation.ADD_VALUE, 0.04f, 0.08f, true),
    DRAW_SPEED(Target.BOW, () -> ALObjects.Attributes.DRAW_SPEED, Operation.ADD_VALUE, 0.04f, 0.08f, true),
    ELEMENTAL_POWER(Target.WEAPON, () -> ModAttributes.ELEMENTAL_POWER, Operation.ADD_VALUE, 0.04f, 0.08f, true),
    SPELL_POWER(Target.WEAPON, () -> ModAttributes.SPELL_POWER, Operation.ADD_VALUE, 0.03f, 0.06f, true),
    // armor
    MAX_HEALTH(Target.ARMOR, () -> Attributes.MAX_HEALTH, Operation.ADD_VALUE, 1f, 2f, false),
    ARMOR(Target.ARMOR, () -> Attributes.ARMOR, Operation.ADD_VALUE, 0.5f, 1f, false),
    TOUGHNESS(Target.ARMOR, () -> Attributes.ARMOR_TOUGHNESS, Operation.ADD_VALUE, 0.3f, 0.7f, false),
    MOVE_SPEED(Target.ARMOR, () -> Attributes.MOVEMENT_SPEED, Operation.ADD_MULTIPLIED_BASE, 0.01f, 0.02f, true),
    DODGE(Target.ARMOR, () -> ALObjects.Attributes.DODGE_CHANCE, Operation.ADD_VALUE, 0.01f, 0.02f, true),
    FIRE_RES(Target.ARMOR, () -> ModAttributes.FIRE_RESISTANCE, Operation.ADD_VALUE, 0.02f, 0.04f, true),
    POISON_RES(Target.ARMOR, () -> ModAttributes.POISON_RESISTANCE, Operation.ADD_VALUE, 0.02f, 0.04f, true),
    CRIMSON_RES(Target.ARMOR, () -> ModAttributes.CRIMSON_RESISTANCE, Operation.ADD_VALUE, 0.02f, 0.04f, true),
    BLOOD_RES(Target.ARMOR, () -> ModAttributes.BLOOD_RESISTANCE, Operation.ADD_VALUE, 0.02f, 0.04f, true),
    STAMINA(Target.ARMOR, () -> ModAttributes.EXHAUSTION_RESISTANCE, Operation.ADD_VALUE, 0.02f, 0.04f, true);

    /** What kind of gear can roll a stat. */
    public enum Target { MELEE, BOW, WEAPON, ARMOR }

    public final Target target;
    private final Supplier<Holder<Attribute>> attribute;
    public final Operation operation;
    public final float min;
    public final float max;
    public final boolean percent;

    BonusStat(Target target, Supplier<Holder<Attribute>> attribute, Operation operation, float min, float max, boolean percent) {
        this.target = target;
        this.attribute = attribute;
        this.operation = operation;
        this.min = min;
        this.max = max;
        this.percent = percent;
    }

    public Holder<Attribute> attribute() {
        return attribute.get();
    }

    /** Can gear of this kind roll the stat? */
    public boolean fits(GearKind kind) {
        return switch (target) {
            case MELEE -> kind == GearKind.MELEE;
            case BOW -> kind == GearKind.BOW;
            case WEAPON -> kind == GearKind.MELEE || kind == GearKind.BOW;
            case ARMOR -> kind == GearKind.ARMOR;
        };
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static @Nullable BonusStat byId(String id) {
        for (BonusStat s : values()) if (s.id().equals(id)) return s;
        return null;
    }

    /** "+4.5% Crit chance" / "+1.5 Max health". */
    public Component describe(float value) {
        String num = percent ? fmt(value * 100) + "%" : fmt(value);
        return Component.translatable("gear.beyondsolo.stat_line", "+" + num, Component.translatable("gear.beyondsolo.stat." + id()));
    }

    public static String fmt(float v) {
        float r = Math.round(v * 10) / 10f;
        return r == (int) r ? String.valueOf((int) r) : String.valueOf(r);
    }
}
