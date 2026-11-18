package com.beyondsolo.system;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.util.ServerScheduler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Keeps the big numbers of {@link Balance} fair where the rest of the game still works in vanilla sizes:
 * <ul>
 *   <li>armour protects by the size of a blow next to the body it lands on ({@link Balance#armorScale});</li>
 *   <li>hazards with no attacker (falls, lava, drowning, traps, storms) and the blows of creatures that are not the
 *       System's are multiplied by the hunter's {@link Balance#legacyScale}, so they keep their old share of health;</li>
 *   <li>flat heals (food, potions, regeneration, most healing skills) are multiplied the same way; {@link Balance#healRaw}
 *       heals (a share of max health or of a blow) and other mods' life steal inside a blow are already sized and are
 *       left alone;</li>
 *   <li>absorption shields from the Absorption effect are topped up to the same share;</li>
 *   <li>a hunter's arrows and thrown weapons grow with SENSE and level ({@link Balance#rangedPower}), the arrows of a
 *       skill with their {@link Balance#power}.</li>
 * </ul>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class BalanceHooks {
    /** How many blows are being worked out right now (between their Pre and Post). Heals inside one are life steal. */
    private static int blowDepth;

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        DamageSource source = event.getSource();

        if (victim instanceof ServerPlayer p && SystemAPI.isAwakened(p) && sizedForVanilla(source)) {
            double scale = Balance.legacyScale(SystemAPI.get(p));
            if (scale > 1.0001) event.setAmount((float) (event.getAmount() * scale));
        }

        if (source.getDirectEntity() instanceof AbstractArrow arrow && source.getEntity() instanceof ServerPlayer shooter
                && SystemAPI.isAwakened(shooter)) {
            boolean skill = arrow.getTags().contains(com.beyondsolo.skill.SkillActions.RAIN_TAG);
            double m = skill ? Balance.power(shooter) : Balance.rangedPower(SystemAPI.get(shooter));
            if (m > 1.0001) event.setAmount((float) (event.getAmount() * m));
        }

        float scale = Balance.armorScale(victim);
        if (scale > 1.001f && !source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            event.getContainer().addModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> {
                float dealt = container.getNewDamage();
                if (dealt <= 0) return reduction;
                float after = CombatRules.getDamageAfterAbsorb(victim, dealt / scale, container.getSource(), victim.getArmorValue(),
                        (float) victim.getAttributeValue(Attributes.ARMOR_TOUGHNESS)) * scale;
                return Math.max(0, dealt - after);
            });
        }
    }

    /**
     * True for damage sized for vanilla's 20 health: hazards with no attacker (unless it is one of the System's own
     * element types, which already grow) and the blows of creatures that are not the System's.
     */
    public static boolean sizedForVanilla(DamageSource source) {
        if (source.typeHolder().unwrapKey().map(k -> k.location().getNamespace().equals(BeyondSolo.MOD_ID)).orElse(false)) return false;
        Entity attacker = source.getEntity();
        return attacker == null || !systemSized(attacker);
    }

    /** Players, their pets and summons, and every creature the System made or grew. */
    static boolean systemSized(Entity e) {
        if (e instanceof Player) return true;
        if (e instanceof OwnableEntity o && o.getOwnerUUID() != null) return true;
        if (BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals(BeyondSolo.MOD_ID)) return true;
        return e.getPersistentData().contains(com.beyondsolo.enemy.Enemies.RANK_KEY);
    }

    // ------------------------------------------------------------------ heals

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onBlowStart(LivingDamageEvent.Pre event) {
        if (!event.getEntity().level().isClientSide) blowDepth++;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onBlowEnd(LivingDamageEvent.Post event) {
        if (!event.getEntity().level().isClientSide && blowDepth > 0) blowDepth--;
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Pre event) {
        // a blow that threw half way through never reached its Post
        blowDepth = 0;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || Balance.healingRaw()) return;
        if (!SystemAPI.isAwakened(p)) return;
        // another mod's heal in the middle of a blow is life steal (Apothic's): a share of the blow, already sized
        if (blowDepth > 0 && !healedByThisMod()) return;
        double scale = Balance.legacyScale(SystemAPI.get(p));
        if (scale > 1.0001) event.setAmount((float) (event.getAmount() * scale));
    }

    private static final String HEAL_OWNER = LivingEntity.class.getName();
    private static final String MOD_PACKAGE = "com.beyondsolo.";

    /** Whether the code that called {@link LivingEntity#heal} is this mod's (its sized heals go through healRaw). */
    static boolean healedByThisMod() {
        return StackWalker.getInstance().walk(frames -> frames
                .dropWhile(f -> !(f.getClassName().equals(HEAL_OWNER) && f.getMethodName().equals("heal")))
                .skip(1)
                .findFirst()
                .map(f -> f.getClassName().startsWith(MOD_PACKAGE))
                .orElse(true));
    }

    /** True while a blow is being worked out (life steal and other heals made by a blow). For tests. */
    public static boolean inBlow() {
        return blowDepth > 0;
    }

    // ------------------------------------------------------------------ absorption

    @SubscribeEvent
    static void onEffectAdded(MobEffectEvent.Added event) {
        MobEffectInstance added = event.getEffectInstance();
        if (added == null || !added.is(MobEffects.ABSORPTION) || !(event.getEntity() instanceof ServerPlayer p) || !SystemAPI.isAwakened(p)) return;
        double scale = Balance.legacyScale(SystemAPI.get(p));
        if (scale <= 1.0001) return;
        float shield = (float) (4 * (1 + added.getAmplifier()) * scale);
        // the effect puts its vanilla-sized shield up right after this event: top it up once it has
        ServerScheduler.schedule(0, () -> topUp(p, shield));
    }

    static void topUp(ServerPlayer p, float shield) {
        if (!p.isAlive() || !p.hasEffect(MobEffects.ABSORPTION)) return;
        if (p.getAbsorptionAmount() < shield) p.setAbsorptionAmount(shield);
    }

    private BalanceHooks() {}
}
