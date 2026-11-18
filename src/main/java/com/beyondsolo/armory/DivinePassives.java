package com.beyondsolo.armory;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.ArsenalHits;
import com.beyondsolo.combat.CombatDamage;
import com.beyondsolo.combat.CombatFx;
import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.combat.ElementalHits;
import com.beyondsolo.combat.Statuses;
import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.item.arsenal.ArsenalWeapon;
import com.beyondsolo.item.arsenal.Signature;
import com.beyondsolo.network.CombatFxPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The divine weapons' passives: always on while the weapon is in the hand of the class it is bound to, on top of its
 * traits and its signature art. Each is its own mechanic:
 * <pre>
 * Ruinbringer            Ruin            every melee hit marks a foe; the fifth mark detonates in fire around it
 * Calamity Fang          Vanishing Edge  a kill hides you for 3 s; your next strike within 5 s hits 80% harder
 * Eye of the Insatiable  Insatiable      each kill feeds it (up to 10, for 30 s): +3% spell damage per kill fed
 * Colossus Aegis         Unyielding      once every 90 s a lethal blow leaves you at 1 health, braced and taunting
 * World Tree Branch      Rootbound Life  allies near you that fall under 30% health are caught by roots and healed
 * Starfall Longbow       Astral Quiver   every 4th full draw looses a star arrow: +50% damage and a burst of starlight
 * </pre>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class DivinePassives {
    public static final int RUIN_MARKS = 5;
    public static final float VANISHING_STRIKE = 0.8f;
    public static final float HUNGER_PER_KILL = 0.03f;
    public static final int HUNGER_MAX = 10;
    public static final int UNYIELDING_COOLDOWN = 90 * 20;
    public static final float ROOTBOUND_AT = 0.3f;
    public static final int ROOTBOUND_COOLDOWN = 30 * 20;
    public static final int STAR_EVERY = 4;
    public static final float STAR_ARROW = 0.5f;
    private static final String STAR_TAG = "beyondsolo.star";

    public enum Passive {
        RUIN(Signature.CATACLYSM_CLEAVE, 0xFF7A2E),
        VANISHING_EDGE(Signature.DRAGONS_WRATH, 0xE0306E),
        INSATIABLE(Signature.DEVOURING_STAR, 0x9B7BFF),
        UNYIELDING(Signature.COLOSSUS_STANCE, 0xFFC85A),
        ROOTBOUND_LIFE(Signature.VERDANT_BLESSING, 0x8CE05A),
        ASTRAL_QUIVER(Signature.STARFALL, 0xFFF0A8);

        public final Signature signature;
        public final int color;

        Passive(Signature signature, int color) {
            this.signature = signature;
            this.color = color;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public Component displayName() {
            return Component.translatable("passive.beyondsolo." + key());
        }

        public Component description() {
            return Component.translatable("passive.beyondsolo." + key() + ".desc");
        }

        /** The passive of a divine weapon (each has its own signature art), or null for any other weapon. */
        public static @Nullable Passive of(ArsenalWeapon w) {
            if (w.baseGrade() != ItemGrade.DIVINE) return null;
            for (Passive p : values()) if (p.signature == w.signature()) return p;
            return null;
        }
    }

    /** The passive of the divine weapon in this entity's main hand, if the weapon answers to it. */
    public static @Nullable Passive held(LivingEntity e) {
        if (!(e.getMainHandItem().getItem() instanceof ArsenalWeapon w)) return null;
        Passive p = Passive.of(w);
        return p == null || ArsenalHits.refuses(e, w) ? null : p;
    }

    /** Tooltip lines for a divine weapon's passive. */
    public static void tooltip(ArsenalWeapon w, List<Component> lines) {
        Passive p = Passive.of(w);
        if (p == null) return;
        lines.add(Component.translatable("tooltip.beyondsolo.divine_passive", p.displayName())
                .withStyle(s -> s.withColor(TextColor.fromRgb(ItemGrade.DIVINE.color())).withBold(true)));
        lines.add(Component.literal("   ").append(p.description()).withStyle(ChatFormatting.GRAY));
    }

    // ------------------------------------------------------------------ Ruin: marks that detonate

    private record Ruin(int marks, long tick) {}

    private static final Map<LivingEntity, Ruin> RUIN = new WeakHashMap<>();

    public static int ruinMarks(LivingEntity target) {
        Ruin r = RUIN.get(target);
        return r == null || target.level().getGameTime() - r.tick() > 6 * 20 ? 0 : r.marks();
    }

    private static void ruin(LivingEntity attacker, LivingEntity target, float dealt) {
        int marks = ruinMarks(target) + 1;
        if (marks < RUIN_MARKS) {
            RUIN.put(target, new Ruin(marks, target.level().getGameTime()));
            SetPerks.burst(target, ParticleTypes.SMALL_FLAME, 3 + marks * 2, 0.3);
            return;
        }
        RUIN.remove(target);
        float blast = com.beyondsolo.system.Balance.flatDamage(attacker, 6f) + dealt * 0.4f;
        for (LivingEntity e : ArsenalHits.foesAround(attacker, target.position(), 3)) {
            ArsenalHits.proc(() -> CombatDamage.deal(e, DamageElement.FIRE, blast, attacker));
            if (e.isAlive()) Statuses.apply(e, DamageElement.FIRE, 25f * ElementalHits.power(attacker), attacker);
        }
        if (target.isAlive() && !ArsenalHits.foesAround(attacker, target.position(), 3).contains(target)) {
            ArsenalHits.proc(() -> CombatDamage.deal(target, DamageElement.FIRE, blast, attacker));
        }
        CombatFx.play(target, CombatFxPayload.Kind.NOVA, ArsenalHits.packNova(Passive.RUIN.color, 3));
        SetPerks.burst(target, ParticleTypes.LAVA, 10, 0.6);
        SetPerks.burst(target, ParticleTypes.FLAME, 24, 0.9);
        SetPerks.sound(target, SoundEvents.GENERIC_EXPLODE.value(), 0.7f, 0.8f);
    }

    // ------------------------------------------------------------------ hooks

    @SubscribeEvent(priority = EventPriority.LOW)
    static void incoming(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        if (target.level().isClientSide || ArsenalHits.inProc() || ElementalHits.isProc(source)) return;
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == target) return;
        Passive p = held(attacker);
        if (p == Passive.VANISHING_EDGE && SetPerks.melee(source, attacker) && SetPerks.consume(attacker, "vanish")) {
            event.setAmount(event.getAmount() * (1f + VANISHING_STRIKE));
            attacker.removeEffect(MobEffects.INVISIBILITY);
            SetPerks.burst(target, ParticleTypes.CRIT, 16, 0.4);
            SetPerks.sound(target, SoundEvents.PLAYER_ATTACK_CRIT, 1f, 0.7f);
        }
        if (p == Passive.INSATIABLE && SetPerks.spell(source, attacker)) {
            int hunger = SetPerks.stacks(attacker, "hunger");
            if (hunger > 0) event.setAmount(event.getAmount() * (1f + HUNGER_PER_KILL * hunger));
        }
    }

    @SubscribeEvent
    static void afterDamage(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        if (target.level().isClientSide || ArsenalHits.inProc() || ElementalHits.isProc(source) || event.getNewDamage() <= 0) return;
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == target) return;
        if (source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getPersistentData().getBoolean(STAR_TAG)) {
            starBurst(attacker, target);
            return;
        }
        if (held(attacker) == Passive.RUIN && SetPerks.melee(source, attacker) && target.isAlive()) ruin(attacker, target, event.getNewDamage());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void died(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide) return;
        // Unyielding: the Aegis holds its guardian up
        if (held(dead) == Passive.UNYIELDING && SetPerks.ready(dead, "unyielding", UNYIELDING_COOLDOWN)) {
            event.setCanceled(true);
            dead.setHealth(1f);
            dead.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 4 * 20, 2));
            SetPerks.taunt(dead, 10);
            CombatFx.play(dead, CombatFxPayload.Kind.NOVA, ArsenalHits.packNova(Passive.UNYIELDING.color, 5));
            SetPerks.sound(dead, SoundEvents.TOTEM_USE, 0.8f, 0.8f);
            if (dead instanceof ServerPlayer sp) {
                sp.displayClientMessage(Passive.UNYIELDING.displayName().copy().withStyle(s -> s.withColor(Passive.UNYIELDING.color).withBold(true)), true);
            }
            return;
        }
        if (!(event.getSource().getEntity() instanceof LivingEntity killer) || killer == dead) return;
        Passive p = held(killer);
        if (p == Passive.VANISHING_EDGE) {
            killer.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 3 * 20, 0, false, false, true));
            SetPerks.prime(killer, "vanish", 5 * 20);
            SetPerks.burst(killer, ParticleTypes.SQUID_INK, 14, 0.5);
            SetPerks.sound(killer, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.8f, 1.2f);
        } else if (p == Passive.INSATIABLE) {
            int n = SetPerks.addStack(killer, "hunger", HUNGER_MAX, 30 * 20);
            SetPerks.burst(dead, ParticleTypes.REVERSE_PORTAL, 12, 0.4);
            if (killer instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("passive.beyondsolo.insatiable.stacks", n, Math.round(n * HUNGER_PER_KILL * 100))
                        .withStyle(s -> s.withColor(Passive.INSATIABLE.color)), true);
            }
        }
    }

    @SubscribeEvent
    static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || (p.tickCount + p.getId()) % 10 != 0 || !p.isAlive()) return;
        if (held(p) != Passive.ROOTBOUND_LIFE) return;
        for (Player ally : SetPerks.allies(p, 10)) {
            if (ally.getHealth() >= ally.getMaxHealth() * ROOTBOUND_AT || !SetPerks.ready(ally, "rootbound", ROOTBOUND_COOLDOWN)) continue;
            com.beyondsolo.system.Balance.healRaw(ally, ally.getMaxHealth() * 0.25f);
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0));
            if (ally != p) SetPerks.line(p, ally, ParticleTypes.HAPPY_VILLAGER);
            SetPerks.burst(ally, ParticleTypes.SPORE_BLOSSOM_AIR, 20, 0.6);
            SetPerks.sound(ally, SoundEvents.ROOTED_DIRT_PLACE, 1f, 0.8f);
            SetPerks.sound(ally, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.4f);
        }
    }

    // ------------------------------------------------------------------ Astral Quiver: star arrows

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void arrowLoosed(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof AbstractArrow arrow) || arrow.tickCount > 0) return;
        if (!(arrow.getOwner() instanceof LivingEntity owner) || !arrow.isCritArrow()) return;
        var data = arrow.getPersistentData();
        if (!data.contains(ArsenalHits.BOW_TAG) || data.getBoolean("beyondsolo.split") || data.getBoolean(STAR_TAG) || data.getBoolean("beyondsolo.counted")) return;
        Item bow = BuiltInRegistries.ITEM.get(ResourceLocation.parse(data.getString(ArsenalHits.BOW_TAG)));
        if (!(bow instanceof ArsenalWeapon w) || Passive.of(w) != Passive.ASTRAL_QUIVER || ArsenalHits.refuses(owner, w)) return;
        data.putBoolean("beyondsolo.counted", true);
        if (SetPerks.bump(owner, "quiver") % STAR_EVERY != 0) return;
        data.putBoolean(STAR_TAG, true);
        arrow.setBaseDamage(arrow.getBaseDamage() * (1f + STAR_ARROW));
        arrow.setGlowingTag(true);
        SetPerks.sound(owner, SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 1.8f);
    }

    private static void starBurst(LivingEntity attacker, LivingEntity target) {
        if (!(target.level() instanceof ServerLevel)) return;
        for (LivingEntity e : ArsenalHits.foesAround(attacker, target.position(), 4)) {
            if (e != target) ArsenalHits.magicHit(attacker, e, com.beyondsolo.system.Balance.flatDamage(attacker, 6f));
        }
        CombatFx.play(target, CombatFxPayload.Kind.NOVA, ArsenalHits.packNova(Passive.ASTRAL_QUIVER.color, 4));
        SetPerks.burst(target, ParticleTypes.END_ROD, 24, 0.8);
        SetPerks.sound(target, SoundEvents.AMETHYST_BLOCK_BREAK, 1.2f, 1.4f);
    }

    private DivinePassives() {}
}
