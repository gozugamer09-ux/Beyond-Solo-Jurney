package com.beyondsolo.gear;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.ArsenalHits;
import com.beyondsolo.combat.CombatFx;
import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.combat.ElementalHits;
import com.beyondsolo.combat.ModEffects;
import com.beyondsolo.combat.Statuses;
import com.beyondsolo.item.ArmorSets;
import com.beyondsolo.network.CombatFxPayload;
import com.beyondsolo.skill.SkillElement;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/** The four-piece perks of the boss signature sets (see {@link BossSets}). */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class SetEffects {
    public static final float UNBROKEN_AT = 0.3f;
    public static final int UNBROKEN_COOLDOWN = 60 * 20;
    public static final float NIGHTS_EDGE = 0.3f;
    public static final float DOMINION_BONUS = 0.15f;
    public static final float DOMINION_HEAL = 0.06f;

    private static final Map<LivingEntity, Long> UNBROKEN_USED = new WeakHashMap<>();

    /** A melee hit from the attacker itself (not a spell, arrow or proc). */
    private static boolean melee(DamageSource source, LivingEntity attacker) {
        return source.getDirectEntity() == attacker && !source.is(DamageTypeTags.IS_PROJECTILE)
                && !source.is(DamageTypes.INDIRECT_MAGIC) && !source.is(DamageTypes.MAGIC);
    }

    /** The element a set gives its wearer's hits when their weapon has no infusion of its own. */
    public static @Nullable SkillElement innateElement(LivingEntity wearer) {
        if (ArmorSets.hasPerk(wearer, "frost_aegis")) return SkillElement.WATER;
        if (ArmorSets.hasPerk(wearer, "nights_edge")) return SkillElement.SHADOW;
        if (ArmorSets.hasPerk(wearer, "living_flame")) return SkillElement.FIRE;
        return null;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void incoming(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide || ArsenalHits.inProc()) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == target) return;
        float mult = 1f;
        if (ArmorSets.hasPerk(attacker, "nights_edge") && melee(event.getSource(), attacker) && behind(attacker, target)) mult += NIGHTS_EDGE;
        if (ArmorSets.hasPerk(attacker, "crimson_dominion") && target.hasEffect(ModEffects.CRIMSON_BRAND)) mult += DOMINION_BONUS;
        if (mult != 1f) event.setAmount(event.getAmount() * mult);
    }

    @SubscribeEvent
    static void afterDamage(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        float dealt = event.getNewDamage();
        DamageSource source = event.getSource();

        // the wearer was hit
        if (dealt > 0 && ArmorSets.hasPerk(target, "unbroken") && target.isAlive() && target.getHealth() < target.getMaxHealth() * UNBROKEN_AT) {
            long now = target.level().getGameTime();
            Long used = UNBROKEN_USED.get(target);
            if (used == null || now - used >= UNBROKEN_COOLDOWN || now < used) {
                UNBROKEN_USED.put(target, now);
                target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 1));
                target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1));
                target.level().playSound(null, target.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.8f, 0.6f);
                CombatFx.play(target, CombatFxPayload.Kind.NOVA, ArsenalHits.packNova(0xC9A35A, 3));
            }
        }
        if (dealt > 0 && !ArsenalHits.inProc() && source.getEntity() instanceof LivingEntity attacker && attacker != target && melee(source, attacker)) {
            if (ArmorSets.hasPerk(target, "venom_skin")) Statuses.apply(attacker, DamageElement.POISON, 15f, target);
            if (ArmorSets.hasPerk(target, "frost_aegis") && target.getRandom().nextFloat() < 0.25f) ArsenalHits.frostbite(attacker, target, 2f);
        }

        // the wearer hit something
        if (dealt <= 0 || ArsenalHits.inProc() || !(source.getEntity() instanceof LivingEntity attacker) || attacker == target || !target.isAlive()) return;
        boolean meleeHit = melee(source, attacker);
        if (ArmorSets.hasPerk(attacker, "living_flame") && meleeHit) {
            Statuses.apply(target, DamageElement.FIRE, 15f * ElementalHits.power(attacker), attacker);
        }
        if (ArmorSets.hasPerk(attacker, "crimson_dominion") && meleeHit) {
            Statuses.apply(target, DamageElement.CRIMSON, 18f * ElementalHits.power(attacker), attacker);
            attacker.heal(dealt * DOMINION_HEAL);
        }
        // sets that carry an element strike with it, unless the weapon has its own infusion
        if (meleeHit && attacker instanceof ServerPlayer p && Infusions.of(p.getMainHandItem()) == null) {
            SkillElement innate = innateElement(p);
            if (innate != null) Infusions.strike(p, target, innate, dealt);
        }
    }

    private static boolean behind(LivingEntity attacker, LivingEntity target) {
        Vec3 facing = Vec3.directionFromRotation(0, target.getYRot());
        Vec3 toAttacker = attacker.position().subtract(target.position()).multiply(1, 0, 1).normalize();
        return facing.dot(toAttacker) < -0.3;
    }

    private SetEffects() {}
}
