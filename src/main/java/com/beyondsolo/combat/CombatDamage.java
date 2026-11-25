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

    /**
     * The System's fire element (a burn, a fire skill's blow), as against a flame in the world. Vanilla spares a
     * fire-immune body every fire; the System's bosses carry that flag only to stand in fire and lava, so they ask
     * {@link #spared} instead, and how much of the element lands is its {@code ElementalProfile}'s business.
     */
    public static boolean systemFire(DamageSource source) {
        return source.is(FIRE);
    }

    /**
     * What a boss made to stand in flames answers to {@code isInvulnerableTo}: the System's fire lands on it like any
     * blow would ({@link #systemFire}), every other fire is shrugged off as before. {@code otherwise} is what it says
     * for anything else (its own rules, then vanilla's).
     */
    public static boolean spared(Entity e, DamageSource source, boolean otherwise) {
        if (!systemFire(source)) return otherwise;
        return e.isRemoved() || e.isInvulnerable() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.isCreativePlayer();
    }

    private CombatDamage() {}
}
