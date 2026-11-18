package com.beyondsolo.worldboss;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.ArsenalHits;
import com.beyondsolo.combat.CombatDamage;
import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.combat.ModAttributes;
import com.beyondsolo.gear.GearRolls;
import com.beyondsolo.item.ArmorSets;
import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.item.SystemArmorItem;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Monarch armor: the grade above divine ({@link ItemGrade#MONARCH}), left only by slain monarchs. Each monarch leaves
 * pieces of its own regalia, shaped like its own armor, but the light in it is its wearer's: the pieces take the colour
 * of the aura of the hunter who wears them (their class's colour), and glow and move with it ({@code
 * client.worldboss.MonarchAuraLayer}).
 *
 * <p>Any hunter can wear them (level {@link #LEVEL}). Two pieces and four give big bonuses, and the full regalia its
 * monarch's power:
 * <ul>
 *   <li><b>Stillness</b>: blows slow what they strike, and every fifth one freezes it in place.</li>
 *   <li><b>Hunger</b>: every kill feeds the wearer: health back, and strength that builds up to three times.</li>
 *   <li><b>Storms</b>: every fourth blow calls lightning down on the foe, and it leaps to two more.</li>
 * </ul>
 * A slain monarch leaves each hunter who took a tenth of its health or more a piece by chance
 * ({@code worldbosses.monarchArmorChance}); one who has fought {@code worldbosses.monarchArmorPity} monarchs to the end
 * without one gets one for sure. Numbers are first guesses for the balancing pass.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class MonarchArmor {
    /** The set tier above divine. */
    public static final int TIER = ArmorSets.DIVINE_TIER + 1;
    /** The level a hunter needs to put a piece on. */
    public static final int LEVEL = 60;
    /** The light of a piece that has never been worn, or is worn by someone without a class. */
    public static final int DEFAULT_AURA = 0xC77DFF;

    public static final int STILLNESS_EVERY = 5;
    public static final int STILLNESS_SLOW_TICKS = 60;
    public static final int STILLNESS_FREEZE_TICKS = 30;
    public static final float HUNGER_HEAL = 0.12f;
    public static final int HUNGER_MAX = 3;
    public static final int HUNGER_TICKS = 160;
    public static final int STORMS_EVERY = 4;
    public static final float STORMS_BOLT = 0.4f;
    public static final float STORMS_CHAIN = 0.3f;

    public static final ArmorSets.Set STILLNESS = set("monarch_stillness", "monarch_stillness",
            Map.of(Attributes.ARMOR_TOUGHNESS, 3.0, ModAttributes.ELEMENTAL_POWER, 0.15),
            Map.of(Attributes.MAX_HEALTH, 0.25, Attributes.ARMOR, 4.0, ModAttributes.FIRE_RESISTANCE, 0.3));
    public static final ArmorSets.Set HUNGER = set("monarch_hunger", "monarch_hunger",
            Map.of(Attributes.ATTACK_DAMAGE, 3.0, ModAttributes.BLOOD_RESISTANCE, 0.2),
            Map.of(Attributes.ATTACK_DAMAGE, 5.0, Attributes.MOVEMENT_SPEED, 0.06, ModAttributes.BLOOD_RESISTANCE, 0.3));
    public static final ArmorSets.Set STORMS = set("monarch_storms", "monarch_storms",
            Map.of(ModAttributes.ELEMENTAL_POWER, 0.2, Attributes.MOVEMENT_SPEED, 0.04),
            Map.of(ModAttributes.ELEMENTAL_POWER, 0.3, Attributes.ATTACK_SPEED, 0.4, Attributes.ARMOR_TOUGHNESS, 3.0));

    private static ArmorSets.Set set(String name, String perk, Map<Holder<Attribute>, Double> two, Map<Holder<Attribute>, Double> four) {
        ArmorSets.Set s = new ArmorSets.Set(name, 0.05f, ItemGrade.MONARCH, () -> two, () -> four, perk, null).tier(TIER).from("monarch");
        s.requiredLevel = LEVEL;
        return ArmorSets.register(s);
    }

    /** Every monarch set, in {@link WorldBossKind#monarchs()} order. */
    public static List<ArmorSets.Set> sets() {
        return List.of(STILLNESS, HUNGER, STORMS);
    }

    /** The regalia a monarch leaves. */
    public static ArmorSets.Set setOf(WorldBossKind kind) {
        return switch (kind) {
            case HUNGER -> HUNGER;
            case STORMS -> STORMS;
            default -> STILLNESS;
        };
    }

    public static boolean is(ItemStack stack) {
        return stack.getItem() instanceof SystemArmorItem a && sets().contains(a.set());
    }

    // ================================================================ the aura

    /** The colour of a hunter's aura: a crowned hunter's crown's, their class's, or the monarchs' violet when they have none. */
    public static int auraOf(LivingEntity wearer) {
        if (wearer instanceof Player p) {
            com.beyondsolo.monarch.Dominion crown = com.beyondsolo.monarch.MonarchAPI.dominion(p);
            if (crown != null) return crown.color();
            SystemData d = SystemAPI.get(p);
            if (d.isAwakened() && d.playerClass() != null) return d.playerClass().color();
        }
        return DEFAULT_AURA;
    }

    /** The hunter's aura changed (crowned, or the crown taken back): the pieces they wear take its new colour. */
    public static void retint(Player wearer) {
        int aura = auraOf(wearer);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!slot.isArmor()) continue;
            ItemStack worn = wearer.getItemBySlot(slot);
            if (is(worn) && aura(worn) != aura) worn.set(WorldBossModule.AURA.get(), aura);
        }
    }

    /** The light in a piece: its last wearer's aura. */
    public static int aura(ItemStack stack) {
        Integer c = stack.get(WorldBossModule.AURA.get());
        return c == null ? DEFAULT_AURA : c;
    }

    /** A piece put on takes its wearer's aura. */
    @SubscribeEvent
    static void equipped(LivingEquipmentChangeEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || !event.getSlot().isArmor() || !is(event.getTo())) return;
        int aura = auraOf(e);
        ItemStack worn = e.getItemBySlot(event.getSlot());
        if (is(worn) && aura(worn) != aura) worn.set(WorldBossModule.AURA.get(), aura);
        if (e.level() instanceof ServerLevel level) {
            // it forms round them: a flash of their own light
            Vector3f c = rgb(aura);
            level.sendParticles(new DustParticleOptions(c, 1.4f), e.getX(), e.getY() + 1, e.getZ(), 24, 0.35, 0.7, 0.35, 0.02);
            level.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY() + 1, e.getZ(), 6, 0.3, 0.6, 0.3, 0.03);
            level.playSound(null, e.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.5f, 1.6f);
        }
    }

    static Vector3f rgb(int color) {
        return new Vector3f(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f);
    }

    // ================================================================ drops

    /**
     * A slain monarch's gift to one hunter: a piece of its regalia by chance, or for sure once the pity count is reached.
     * Prefers a piece the hunter doesn't have yet. Updates the record either way.
     */
    static ItemStack roll(ServerPlayer p, WorldBossKind kind, WorldBossRecord record, RandomSource r) {
        int pity = WorldBossConfig.monarchArmorPity();
        boolean lucky = r.nextDouble() < WorldBossConfig.monarchArmorChance() || (pity > 0 && record.dryMonarchs() + 1 >= pity);
        if (!lucky) {
            record.addDry();
            return ItemStack.EMPTY;
        }
        record.addArmor();
        return piece(p, setOf(kind), r);
    }

    /** A piece of a monarch's regalia for a hunter (one they lack, if any), its stats rolled, in their aura. */
    public static ItemStack piece(ServerPlayer p, ArmorSets.Set set, RandomSource r) {
        List<DeferredItem<SystemArmorItem>> pieces = WorldBossModule.PIECES.get(set.name);
        List<DeferredItem<SystemArmorItem>> missing = new ArrayList<>();
        for (DeferredItem<SystemArmorItem> piece : pieces) if (!owns(p, piece.get())) missing.add(piece);
        List<DeferredItem<SystemArmorItem>> from = missing.isEmpty() ? pieces : missing;
        ItemStack stack = new ItemStack(from.get(r.nextInt(from.size())).get());
        GearRolls.roll(stack, ItemGrade.MONARCH, r);
        stack.set(WorldBossModule.AURA.get(), auraOf(p));
        return stack;
    }

    private static boolean owns(ServerPlayer p, SystemArmorItem item) {
        for (EquipmentSlot slot : ArmorSets.ARMOR) if (p.getItemBySlot(slot).is(item)) return true;
        return p.getInventory().contains(new ItemStack(item));
    }

    // ================================================================ the regalia's powers

    private static final Map<UUID, Integer> HITS = new HashMap<>();
    private static final Map<UUID, Integer> FED = new HashMap<>();

    @SubscribeEvent
    static void afterDamage(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide || !(target.level() instanceof ServerLevel level) || event.getNewDamage() <= 0) return;
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof LivingEntity wearer) || wearer == target || ArsenalHits.inProc()) return;
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return;
        ArmorSets.Set set = ArmorSets.fullSet(wearer);
        if (set == null || set.perk == null || !set.perk.startsWith("monarch_")) return;
        int aura = auraOf(wearer);
        switch (set.perk) {
            case "monarch_stillness" -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, STILLNESS_SLOW_TICKS, 1), wearer);
                if (count(wearer) % STILLNESS_EVERY == 0 && target.isAlive()) {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, STILLNESS_FREEZE_TICKS, 9), wearer);
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, STILLNESS_FREEZE_TICKS, 1), wearer);
                    target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + 20));
                    level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 30,
                            target.getBbWidth() / 2, target.getBbHeight() / 2, target.getBbWidth() / 2, 0.05);
                    level.sendParticles(new DustParticleOptions(rgb(aura), 1.2f), target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                            16, target.getBbWidth() / 2, target.getBbHeight() / 2, target.getBbWidth() / 2, 0.01);
                    level.playSound(null, target.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8f, 0.6f);
                }
            }
            case "monarch_storms" -> {
                if (count(wearer) % STORMS_EVERY != 0 || !target.isAlive()) return;
                float blow = event.getNewDamage();
                WorldBossFx.bolt(level, target.position(), 0.7f, aura);
                level.playSound(null, target.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1f, 1.3f);
                ArsenalHits.proc(() -> CombatDamage.deal(target, DamageElement.PHYSICAL, blow * STORMS_BOLT, wearer));
                int chained = 0;
                for (LivingEntity e : ArsenalHits.foesAround(wearer, target.position(), 6)) {
                    if (e == target || chained >= 2) continue;
                    chained++;
                    WorldBossFx.bolt(level, e.position(), 0.5f, aura);
                    ArsenalHits.proc(() -> CombatDamage.deal(e, DamageElement.PHYSICAL, blow * STORMS_CHAIN, wearer));
                }
            }
            default -> {
            }
        }
    }

    private static int count(LivingEntity wearer) {
        return HITS.merge(wearer.getUUID(), 1, Integer::sum);
    }

    @SubscribeEvent
    static void died(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide || !(event.getSource().getEntity() instanceof LivingEntity killer) || killer == dead) return;
        ArmorSets.Set set = ArmorSets.fullSet(killer);
        if (set == null || !"monarch_hunger".equals(set.perk) || !(dead.level() instanceof ServerLevel level)) return;
        com.beyondsolo.system.Balance.healRaw(killer, killer.getMaxHealth() * HUNGER_HEAL);
        MobEffectInstance had = killer.getEffect(MobEffects.DAMAGE_BOOST);
        int stacks = had == null ? 1 : Math.min(HUNGER_MAX, had.getAmplifier() + 2);
        killer.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, HUNGER_TICKS, stacks - 1, false, true));
        FED.put(killer.getUUID(), stacks);
        Vec3 at = dead.position().add(0, dead.getBbHeight() / 2, 0);
        Vector3f c = rgb(auraOf(killer));
        // the fallen's last light runs into the wearer
        for (int i = 0; i < 8; i++) {
            Vec3 p = at.lerp(killer.position().add(0, 1, 0), i / 8.0);
            level.sendParticles(new DustParticleOptions(c, 1.3f), p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0);
        }
        level.playSound(null, killer.blockPosition(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.4f, 0.5f);
    }

    /** Forgets a hunter's counts (logout). */
    static void forget(UUID id) {
        HITS.remove(id);
        FED.remove(id);
    }

    private MonarchArmor() {}
}
