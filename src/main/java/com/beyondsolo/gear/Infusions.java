package com.beyondsolo.gear;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.ArsenalHits;
import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.combat.ElementalHits;
import com.beyondsolo.combat.Statuses;
import com.beyondsolo.skill.SkillCombat;
import com.beyondsolo.skill.SkillElement;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Weapon element infusions: any class can carry an element on its weapon. An infused weapon's hits (and its bow's
 * arrows) strike with that element through the skill system's {@link SkillCombat#strike}: they leave the element on
 * the creature and set off element reactions with whatever it already carries (a water blade after a lightning
 * spell is a Surge). Each hit adds {@link #STRIKE} of its damage as element damage, more if it reacts. Fire, nature
 * and shadow also add a little burn, venom or crimson buildup.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class Infusions {
    public static final String ARROW_TAG = "beyondsolo.infusion";
    /** Share of a hit added as element damage. */
    public static final float STRIKE = 0.15f;

    public static @Nullable SkillElement of(ItemStack stack) {
        return stack.get(GearModule.INFUSION.get());
    }

    public static void set(ItemStack stack, @Nullable SkillElement element) {
        if (element == null || element == SkillElement.PHYSICAL) stack.remove(GearModule.INFUSION.get());
        else stack.set(GearModule.INFUSION.get(), element);
    }

    /** Called when a System bow looses an arrow: the arrow carries the bow's infusion. */
    public static void tagArrow(AbstractArrow arrow, ItemStack bow) {
        SkillElement e = of(bow);
        if (e != null) arrow.getPersistentData().putString(ARROW_TAG, e.name());
    }

    public static void copyTag(AbstractArrow from, AbstractArrow to) {
        if (from.getPersistentData().contains(ARROW_TAG)) to.getPersistentData().putString(ARROW_TAG, from.getPersistentData().getString(ARROW_TAG));
    }

    /** The element a weapon hit carries, or null. */
    static @Nullable SkillElement elementOf(DamageSource source) {
        if (source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.MAGIC)) return null;
        Entity attacker = source.getEntity();
        Entity direct = source.getDirectEntity();
        if (!(attacker instanceof LivingEntity living)) return null;
        if (direct == attacker && !source.is(DamageTypeTags.IS_PROJECTILE)) {
            ItemStack held = living.getMainHandItem();
            return GearKind.of(held) == GearKind.MELEE ? of(held) : null;
        }
        if (direct instanceof AbstractArrow arrow && arrow.getPersistentData().contains(ARROW_TAG)) {
            try {
                return SkillElement.valueOf(arrow.getPersistentData().getString(ARROW_TAG));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    @SubscribeEvent
    static void afterHit(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide || ArsenalHits.inProc() || event.getNewDamage() <= 0) return;
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player) || player == target || !target.isAlive()) return;
        SkillElement element = elementOf(source);
        if (element == null) return;
        strike(player, target, element, event.getNewDamage());
    }

    /** One infused hit's element part. Returns the element damage dealt. */
    public static float strike(ServerPlayer player, LivingEntity target, SkillElement element, float hit) {
        float extra = SkillCombat.strike(player, target, element, Math.max(0.5f, hit * STRIKE));
        if (target.isAlive()) ArsenalHits.magicHit(player, target, extra);
        DamageElement buildup = switch (element) {
            case FIRE -> DamageElement.FIRE;
            case NATURE -> DamageElement.POISON;
            case SHADOW -> DamageElement.CRIMSON;
            default -> null;
        };
        if (buildup != null && target.isAlive()) Statuses.apply(target, buildup, 8f * ElementalHits.power(player), player);
        return extra;
    }

    private Infusions() {}
}
