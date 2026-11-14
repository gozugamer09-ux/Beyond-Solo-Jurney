package com.beyondsolo.combat;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.effect.CombatStatusEffect;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;

/**
 * Combat statuses. What triggers them lives in {@link Statuses}; what cures them is listed here.
 *
 * <ul>
 *   <li>Deep Burn: never wears off. Burns a little every 3 s and halves healing. Ignores milk, water and
 *       totems: only a Quenching Elixir (or death) ends it. Fire Resistance pauses the damage.</li>
 *   <li>Venom: stacks (I to IV). Milk, honey or an Antidote cure it. A fifth stack turns it into Heavy Venom.</li>
 *   <li>Heavy Venom: strong poison that stops healing and slows. Only a Greater Antidote cures it.</li>
 *   <li>Crimson Brand: takes 25% more damage, heals half as much, and attackers drink 10% of the damage dealt.</li>
 * </ul>
 */
public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, BeyondSolo.MOD_ID);

    public static final DeferredHolder<MobEffect, MobEffect> DEEP_BURN = EFFECTS.register("deep_burn",
            () -> new CombatStatusEffect(MobEffectCategory.HARMFUL, 0xFF6A1A, Set.of(ElementCures.ELIXIR), 60, (e, amp) -> {
                if (e.hasEffect(MobEffects.FIRE_RESISTANCE)) return;
                CombatDamage.deal(e, DamageElement.FIRE, 1f + e.getMaxHealth() * 0.01f * (amp + 1), null);
            }));

    public static final DeferredHolder<MobEffect, MobEffect> VENOM = EFFECTS.register("venom",
            () -> new CombatStatusEffect(MobEffectCategory.HARMFUL, 0x7CCF3A,
                    Set.of(EffectCures.MILK, EffectCures.HONEY, ElementCures.ANTIDOTE, ElementCures.GREATER_ANTIDOTE), 30,
                    (e, amp) -> CombatDamage.deal(e, DamageElement.POISON, 0.5f * (amp + 1), null)));

    public static final DeferredHolder<MobEffect, MobEffect> HEAVY_VENOM = EFFECTS.register("heavy_venom",
            () -> new CombatStatusEffect(MobEffectCategory.HARMFUL, 0x4E7A1E, Set.of(ElementCures.GREATER_ANTIDOTE), 20,
                    (e, amp) -> CombatDamage.deal(e, DamageElement.POISON, 1f + e.getMaxHealth() * 0.01f, null))
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, BeyondSolo.id("effect.heavy_venom"), -0.15,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    public static final DeferredHolder<MobEffect, MobEffect> CRIMSON_BRAND = EFFECTS.register("crimson_brand",
            () -> new CombatStatusEffect(MobEffectCategory.HARMFUL, 0xC2185B, Set.of(EffectCures.MILK, ElementCures.TONIC), 0, (e, amp) -> {}));

    // Wards: short resistances from the remedies.
    public static final DeferredHolder<MobEffect, MobEffect> FIRE_WARD = ward("fire_ward", 0xFFA45C, ModAttributes.FIRE_RESISTANCE);
    public static final DeferredHolder<MobEffect, MobEffect> POISON_WARD = ward("poison_ward", 0xA8E06A, ModAttributes.POISON_RESISTANCE);
    public static final DeferredHolder<MobEffect, MobEffect> BLOOD_WARD = ward("blood_ward", 0xE0556A, ModAttributes.BLOOD_RESISTANCE);

    private static DeferredHolder<MobEffect, MobEffect> ward(String name, int color, Holder<net.minecraft.world.entity.ai.attributes.Attribute> resistance) {
        return EFFECTS.register(name, () -> new CombatStatusEffect(MobEffectCategory.BENEFICIAL, color,
                Set.of(EffectCures.MILK), 0, (e, amp) -> {})
                .addAttributeModifier(resistance, BeyondSolo.id("effect." + name), 0.5, AttributeModifier.Operation.ADD_VALUE));
    }

    private ModEffects() {}
}
