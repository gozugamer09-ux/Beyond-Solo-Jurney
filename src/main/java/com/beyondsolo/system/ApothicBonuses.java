package com.beyondsolo.system;

import dev.shadowsoffire.apothic_attributes.api.ALObjects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;

/** Extra stat bonuses that use Apothic Attributes (crit, dodge, cooldowns, ...). Values are fractions: 0.01 = 1%. */
final class ApothicBonuses {
    static void registerAll() {
        // SENSE: finding weak spots
        StatEffects.register("sense_crit_chance", ALObjects.Attributes.CRIT_CHANCE, Operation.ADD_VALUE,
                d -> Math.min(0.5, StatEffects.above(d, Stat.SENSE) * 0.005));
        // STR: harder crits
        StatEffects.register("str_crit_damage", ALObjects.Attributes.CRIT_DAMAGE, Operation.ADD_VALUE,
                d -> StatEffects.above(d, Stat.STR) * 0.01);
        // AGI: dodge
        StatEffects.register("agi_dodge", ALObjects.Attributes.DODGE_CHANCE, Operation.ADD_VALUE,
                d -> Math.min(0.3, StatEffects.above(d, Stat.AGI) * 0.0025));
        // AGI: bow draw speed
        StatEffects.register("agi_draw_speed", ALObjects.Attributes.DRAW_SPEED, Operation.ADD_VALUE,
                d -> Math.min(1.0, StatEffects.above(d, Stat.AGI) * 0.01));
        // INT: faster skill and item cooldowns
        StatEffects.register("int_cooldown", ALObjects.Attributes.COOLDOWN_REDUCTION, Operation.ADD_VALUE,
                d -> Math.min(0.4, StatEffects.above(d, Stat.INT) * 0.005));
        // VIT: more healing received
        StatEffects.register("vit_healing", ALObjects.Attributes.HEALING_RECEIVED, Operation.ADD_VALUE,
                d -> StatEffects.above(d, Stat.VIT) * 0.01);
    }

    private ApothicBonuses() {}
}
