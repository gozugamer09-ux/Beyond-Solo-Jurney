package com.beyondsolo.combat;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.Config;
import com.beyondsolo.item.SystemWeaponItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Where elements come from: System weapons, mobs tagged as element attackers (so mobs build up on players too),
 * and plain burning. Also the status side effects on damage and healing.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class ElementalHits {
    public static final TagKey<EntityType<?>> FIRE_ATTACKERS = tag("fire");
    public static final TagKey<EntityType<?>> POISON_ATTACKERS = tag("poison");
    public static final TagKey<EntityType<?>> HEAVY_POISON_ATTACKERS = tag("heavy_poison");
    public static final TagKey<EntityType<?>> CRIMSON_ATTACKERS = tag("crimson");
    public static final TagKey<EntityType<?>> BLOOD_ATTACKERS = tag("blood");

    /** Crimson brand: extra damage taken, and the share of damage an attacker heals from a branded target. */
    public static final float BRAND_DAMAGE = 1.25f;
    public static final float BRAND_LIFESTEAL = 0.10f;

    private static final Map<Player, Integer> LAST_CRIT = new WeakHashMap<>();

    private static TagKey<EntityType<?>> tag(String name) {
        return TagKey.create(Registries.ENTITY_TYPE, BeyondSolo.id("element_attackers/" + name));
    }

    /** The element a damage source belongs to, for colours and numbers. */
    public static DamageElement elementOf(DamageSource source) {
        if (source.is(CombatDamage.FIRE) || source.is(DamageTypeTags.IS_FIRE)) return DamageElement.FIRE;
        if (source.is(CombatDamage.POISON)) return DamageElement.POISON;
        if (source.is(CombatDamage.CRIMSON)) return DamageElement.CRIMSON;
        if (source.is(CombatDamage.BLOOD)) return DamageElement.BLOOD;
        return DamageElement.PHYSICAL;
    }

    static boolean isProc(DamageSource source) {
        return source.is(CombatDamage.FIRE) || source.is(CombatDamage.POISON) || source.is(CombatDamage.CRIMSON) || source.is(CombatDamage.BLOOD);
    }

    /** Multiplier on the buildup an attacker deals: its elemental power attribute (INT feeds it for players). */
    public static float power(LivingEntity attacker) {
        return 1f + (float) attacker.getAttributeValue(ModAttributes.ELEMENTAL_POWER);
    }

    @SubscribeEvent
    static void crit(CriticalHitEvent event) {
        if (event.isCriticalHit()) LAST_CRIT.put(event.getEntity(), event.getEntity().tickCount);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void incoming(LivingIncomingDamageEvent event) {
        if (event.getEntity().hasEffect(ModEffects.CRIMSON_BRAND)) event.setAmount(event.getAmount() * BRAND_DAMAGE);
    }

    @SubscribeEvent
    static void afterDamage(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        float dealt = event.getNewDamage();
        if (target.level().isClientSide || dealt <= 0) return;
        Entity attacker = source.getEntity();
        boolean melee = attacker != null && source.getDirectEntity() == attacker;
        boolean crit = attacker instanceof Player p && melee && LAST_CRIT.getOrDefault(p, -1) == p.tickCount;
        DamageNumbers.report(target, dealt, elementOf(source), crit, attacker);

        if (isProc(source)) return;

        if (attacker instanceof LivingEntity living && living != target) {
            // weapons (players, and mobs that pick one up)
            if (melee && living.getMainHandItem().getItem() instanceof SystemWeaponItem weapon && weapon.buildup() > 0) {
                Statuses.apply(target, weapon.element(), weapon.buildup() * power(living), living);
                if (weapon.element() == DamageElement.FIRE) target.igniteForSeconds(3);
            }
            // element attackers (arrows and fangs count: the owner is the attacker)
            EntityType<?> type = living.getType();
            if (type.is(FIRE_ATTACKERS)) Statuses.apply(target, DamageElement.FIRE, Config.MOB_FIRE_BUILDUP.get().floatValue() * power(living), living);
            if (type.is(POISON_ATTACKERS)) Statuses.apply(target, DamageElement.POISON, Config.MOB_POISON_BUILDUP.get().floatValue() * power(living), living);
            if (type.is(HEAVY_POISON_ATTACKERS) && ModAttributes.resistance(target, DamageElement.POISON) < 0.9f) Statuses.applyHeavyVenom(target, living);
            if (type.is(CRIMSON_ATTACKERS)) Statuses.apply(target, DamageElement.CRIMSON, Config.MOB_CRIMSON_BUILDUP.get().floatValue() * power(living), living);
            if (type.is(BLOOD_ATTACKERS)) Statuses.apply(target, DamageElement.BLOOD, Config.MOB_BLOOD_BUILDUP.get().floatValue() * power(living), living);

            // crimson brand: the attacker drinks a share of the hit
            if (target.hasEffect(ModEffects.CRIMSON_BRAND)) living.heal(dealt * BRAND_LIFESTEAL);
        }

        // plain burning slowly turns into a deep burn
        if (source.is(DamageTypeTags.IS_FIRE)) {
            float amount = Config.BURNING_BUILDUP.get().floatValue() * (source.is(DamageTypes.LAVA) ? 3 : 1);
            Statuses.apply(target, DamageElement.FIRE, amount, null);
        }
    }

    @SubscribeEvent
    static void heal(LivingHealEvent event) {
        LivingEntity e = event.getEntity();
        if (e.hasEffect(ModEffects.HEAVY_VENOM)) {
            event.setCanceled(true);
            return;
        }
        float factor = 1f;
        if (e.hasEffect(ModEffects.DEEP_BURN)) factor *= 0.5f;
        if (e.hasEffect(ModEffects.CRIMSON_BRAND)) factor *= 0.5f;
        if (factor < 1f) event.setAmount(event.getAmount() * factor);
    }

    private ElementalHits() {}
}
