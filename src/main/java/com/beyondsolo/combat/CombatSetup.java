package com.beyondsolo.combat;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.system.Stat;
import com.beyondsolo.system.StatEffects;
import com.beyondsolo.system.SystemData;
import net.combat_roll.api.CombatRoll;
import net.combat_roll.api.event.ServerSideRollEvents;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/** Wires combat into the stat system and into Combat Roll once everything is registered. */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class CombatSetup {
    @SubscribeEvent
    static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            registerStatBonuses();
            if (ModList.get().isLoaded("combat_roll")) RollHook.register();
        });
    }

    private static double above(SystemData d, Stat stat) {
        return d.stat(stat) - Stat.BASE;
    }

    static void registerStatBonuses() {
        // INT: stronger elemental hits (more buildup per hit)
        StatEffects.register("int_elemental_power", ModAttributes.ELEMENTAL_POWER, Operation.ADD_VALUE,
                d -> Math.min(1.0, above(d, Stat.INT) * 0.01));
        // VIT: a sturdier body resists every element a little
        for (Holder<Attribute> resistance : new Holder[]{ModAttributes.FIRE_RESISTANCE, ModAttributes.POISON_RESISTANCE,
                ModAttributes.CRIMSON_RESISTANCE, ModAttributes.BLOOD_RESISTANCE}) {
            String name = "vit_" + resistance.unwrapKey().orElseThrow().location().getPath();
            StatEffects.register(name, resistance, Operation.ADD_VALUE, d -> Math.min(0.25, above(d, Stat.VIT) * 0.004));
        }
        // VIT: endurance
        StatEffects.register("vit_exhaustion_resistance", ModAttributes.EXHAUSTION_RESISTANCE, Operation.ADD_VALUE,
                d -> Math.min(0.3, above(d, Stat.VIT) * 0.003));
        if (ModList.get().isLoaded("combat_roll")) RollHook.registerStatBonuses();
    }

    /** Kept in its own class so nothing touches Combat Roll classes when the mod is missing. */
    static final class RollHook {
        static void register() {
            ServerSideRollEvents.PLAYER_START_ROLLING.register((player, velocity) -> CombatExhaustion.rolled(player));
        }

        static void registerStatBonuses() {
            // AGI: longer rolls, faster recharge, and an extra roll every 25 points
            StatEffects.register("agi_roll_distance", CombatRoll.Attributes.DISTANCE.entry, Operation.ADD_VALUE,
                    d -> Math.min(2.0, above(d, Stat.AGI) * 0.02));
            StatEffects.register("agi_roll_recharge", CombatRoll.Attributes.RECHARGE.entry, Operation.ADD_MULTIPLIED_BASE,
                    d -> Math.min(1.0, above(d, Stat.AGI) * 0.01));
            StatEffects.register("agi_roll_count", CombatRoll.Attributes.COUNT.entry, Operation.ADD_VALUE,
                    d -> Math.min(2.0, Math.floor(above(d, Stat.AGI) / 25.0)));
        }
    }

    private CombatSetup() {}
}
