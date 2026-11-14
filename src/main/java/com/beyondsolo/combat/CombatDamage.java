package com.beyondsolo.combat;

import com.beyondsolo.BeyondSolo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Element damage: status ticks and buildup pops. All four types skip armor, knockback and hit cooldown
 * (see the damage type tags), and {@link #deal} keeps the target's hit cooldown as it was, so a proc in the
 * middle of a sword combo neither eats the next swing nor gets eaten by it.
 */
public final class CombatDamage {
    public static final ResourceKey<DamageType> FIRE = key("fire");
    public static final ResourceKey<DamageType> POISON = key("poison");
    public static final ResourceKey<DamageType> CRIMSON = key("crimson");
    public static final ResourceKey<DamageType> BLOOD = key("blood");

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, BeyondSolo.id(name));
    }

    public static @Nullable ResourceKey<DamageType> typeFor(DamageElement element) {
        if (element == DamageElement.FIRE) return FIRE;
        if (element == DamageElement.POISON) return POISON;
        if (element == DamageElement.CRIMSON) return CRIMSON;
        if (element == DamageElement.BLOOD) return BLOOD;
        return null;
    }

    public static DamageSource source(LivingEntity target, DamageElement element, @Nullable Entity attacker) {
        ResourceKey<DamageType> type = typeFor(element);
        if (type == null) return attacker instanceof LivingEntity l ? target.damageSources().mobAttack(l) : target.damageSources().generic();
        return attacker == null ? target.damageSources().source(type) : target.damageSources().source(type, attacker);
    }

    /** Deals element damage without touching the target's hit cooldown. Returns true if it landed. */
    public static boolean deal(LivingEntity target, DamageElement element, float amount, @Nullable Entity attacker) {
        if (amount <= 0 || !target.isAlive()) return false;
        int invulnerable = target.invulnerableTime;
        float lastHurt = target.lastHurt;
        boolean hit = target.hurt(source(target, element, attacker), amount);
        target.invulnerableTime = invulnerable;
        target.lastHurt = lastHurt;
        return hit;
    }

    private CombatDamage() {}
}
