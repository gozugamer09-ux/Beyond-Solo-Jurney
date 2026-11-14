package com.beyondsolo.skill;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.Buildups;
import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.combat.Statuses;
import com.beyondsolo.network.SkillFxPayload;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.LightLayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Subclass passives and the lasting parts of subclass actives (lifesteal, reflected damage, marks, cheating death,
 * summoned wolves). Attribute passives are transient modifiers put back by {@link SkillPassives#apply}.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class SubclassPassives {
    // ================================================================ numbers (shared with descriptions)

    public static float rageBonus(int l) { return 3 * l; }
    public static float titanBonus(int l) { return 10 + 2 * l; }
    public static float nightBonus(int l) { return 4 + 2 * l; }
    public static float virulence(int l) { return 4 + l; }
    public static float evasion(int l) { return 2 + l; }
    public static float openingBonus(int l) { return 5 + 3 * l; }
    public static float duskBonus(int l) { return 12 + 2 * l; }
    public static float kindle(int l) { return 3 + l; }
    public static float staticRegen(int l) { return 0.15f * l; }
    public static float deepMana(int l) { return 6 * l; }
    public static float pactHeal(int l) { return 1 + 0.2f * l; }
    public static float pactMana(int l) { return 2 + 0.5f * l; }
    public static float celestialMana(int l) { return 15 * l; }
    public static float celestialBonus(int l) { return 10 + 2 * l; }
    public static float devotionRegen(int l) { return 0.1f * l; }
    public static float wardReduction(int l) { return 10 + 3 * l; }
    public static float spite(int l) { return 8 + 2 * l; }
    /** Seconds between two saves from death (Undying, Eternal Grace). */
    public static float undyingCooldown(int l) { return 300 - 15 * l; }
    public static float blessedRegen(int l) { return 0.1f * l; }
    public static float sunRegen(int l) { return 0.15f * l; }
    public static float foresightMana(int l) { return 5 * l; }
    public static float foresightDodge(int l) { return 1 + l; }
    public static float vigorBlood(int l) { return 3 + l; }
    public static float graceRegen(int l) { return 0.4f * l; }
    public static float steadyAim(int l) { return 4 + 2 * l; }
    public static float fieldCraft(int l) { return 5 + 2 * l; }
    public static float quiverBonus(int l) { return 3 + l; }
    public static float quiverSpark(int l) { return 10 + l; }
    public static float eagleBonus(int l) { return 15 + 3 * l; }

    /** Buff key: saved from death recently (the cooldown of Undying and Eternal Grace). */
    public static final String BUFF_SAVED = "undying_spent";

    // ================================================================ attribute passives

    private record Bonus(Skill skill, Holder<Attribute> attribute, float perLevel, AttributeModifier.Operation op, String key) {
        ResourceLocation id() {
            return BeyondSolo.id("skill/" + skill.path() + "/" + key);
        }
    }

    private static final AttributeModifier.Operation ADD = AttributeModifier.Operation.ADD_VALUE;
    private static final AttributeModifier.Operation MUL = AttributeModifier.Operation.ADD_MULTIPLIED_BASE;

    private static Bonus[] bonuses;

    private static Bonus[] bonuses() {
        if (bonuses == null) {
            bonuses = new Bonus[]{
                    new Bonus(SubclassSkills.COMMANDING_PRESENCE, Attributes.MAX_HEALTH, 1f, ADD, "health"),
                    new Bonus(SubclassSkills.COMMANDING_PRESENCE, Attributes.ARMOR_TOUGHNESS, 0.3f, ADD, "toughness"),
                    new Bonus(SubclassSkills.FLOWING_BLADE, Attributes.ATTACK_SPEED, 0.03f, MUL, "attack_speed"),
                    new Bonus(SubclassSkills.IRON_HIDE, Attributes.MAX_HEALTH, 0.8f, ADD, "health"),
                    new Bonus(SubclassSkills.IRON_HIDE, Attributes.KNOCKBACK_RESISTANCE, 0.04f, ADD, "knockback"),
                    new Bonus(SubclassSkills.TITAN_BLOOD, Attributes.MAX_HEALTH, 1.5f, ADD, "health"),
                    new Bonus(SubclassSkills.SOVEREIGN_OF_DUSK, Attributes.MOVEMENT_SPEED, 0.02f, MUL, "speed"),
                    new Bonus(SubclassSkills.DEVOTION, Attributes.ARMOR, 0.4f, ADD, "armor"),
                    new Bonus(SubclassSkills.UNYIELDING, Attributes.ARMOR_TOUGHNESS, 0.5f, ADD, "toughness"),
                    new Bonus(SubclassSkills.UNYIELDING, Attributes.ARMOR, 0.3f, ADD, "armor"),
                    new Bonus(SubclassSkills.UNDYING, Attributes.MAX_HEALTH, 2f, ADD, "health"),
                    new Bonus(SubclassSkills.CRIMSON_VIGOR, Attributes.MAX_HEALTH, 1f, ADD, "health"),
                    new Bonus(SubclassSkills.BEAST_BOND, Attributes.MOVEMENT_SPEED, 0.015f, MUL, "speed"),
                    new Bonus(SubclassSkills.EAGLE_SOVEREIGN, Attributes.MOVEMENT_SPEED, 0.02f, MUL, "speed"),
            };
        }
        return bonuses;
    }

    /** Puts the subclass attribute passives back to match the player's skill levels. */
    static void apply(ServerPlayer player, SkillData data) {
        for (Bonus b : bonuses()) {
            AttributeInstance inst = player.getAttribute(b.attribute);
            if (inst == null) continue;
            int level = data.level(b.skill.path());
            if (level <= 0) inst.removeModifier(b.id());
            else inst.addOrUpdateTransientModifier(new AttributeModifier(b.id(), b.perLevel * level, b.op));
        }
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    /** Extra max mana from subclass passives. */
    static float manaBonus(SkillData data) {
        return deepMana(data.level(SubclassSkills.DEEP_CURRENT.path()))
                + celestialMana(data.level(SubclassSkills.CELESTIAL_CORE.path()))
                + foresightMana(data.level(SubclassSkills.FORESIGHT.path()));
    }

    /** Once a second: regeneration and mana passives. */
    static void tickSecond(ServerPlayer p, SkillData data) {
        if (!p.isAlive()) return;
        float heal = 0;
        int devotion = data.level(SubclassSkills.DEVOTION.path());
        if (devotion > 0 && p.getHealth() < p.getMaxHealth() * 0.5f) heal += devotionRegen(devotion);
        int sun = data.level(SubclassSkills.PHOTOSYNTHESIS.path());
        if (sun > 0 && p.level().isDay() && p.level().canSeeSky(p.blockPosition().above())) heal += sunRegen(sun);
        int grace = data.level(SubclassSkills.ETERNAL_GRACE.path());
        if (grace > 0) heal += graceRegen(grace);
        int blessed = data.level(SubclassSkills.BLESSED_AURA.path());
        if (blessed > 0) {
            for (Player ally : p.level().getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(8), Player::isAlive)) {
                if (ally.getHealth() < ally.getMaxHealth()) ally.heal(blessedRegen(blessed));
            }
        }
        if (heal > 0 && p.getHealth() < p.getMaxHealth()) p.heal(heal);
        int charge = data.level(SubclassSkills.STATIC_CHARGE.path());
        if (charge > 0) SystemAPI.restoreMana(p, staticRegen(charge));
        int core = data.level(SubclassSkills.CELESTIAL_CORE.path());
        if (core > 0) SystemAPI.restoreMana(p, 0.3f * core);
    }

    // ================================================================ hits

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        Entity direct = event.getSource().getDirectEntity();
        Entity attacker = event.getSource().getEntity();
        float amount = event.getAmount();
        long now = target.level().getGameTime();

        // ---- the player strikes
        if (attacker instanceof ServerPlayer p && target != p) {
            SkillData data = SkillAPI.get(p);
            boolean melee = direct == p;
            boolean projectile = direct instanceof Projectile;
            boolean magic = event.getSource().is(DamageTypeTags.WITCH_RESISTANT_TO) || event.getSource().is(DamageTypeTags.BYPASSES_ARMOR);
            float bonus = 0;

            int rage = data.level(SubclassSkills.RAGE_ENGINE.path());
            if (rage > 0 && p.getHealth() < p.getMaxHealth() * 0.5f) bonus += rageBonus(rage);
            int titan = data.level(SubclassSkills.TITAN_BLOOD.path());
            if (titan > 0) bonus += titanBonus(titan);
            int night = data.level(SubclassSkills.NIGHT_HUNTER.path());
            if (night > 0 && dark(p)) bonus += nightBonus(night);
            int opening = data.level(SubclassSkills.OPENING_STRIKE.path());
            if (opening > 0 && target.getHealth() >= target.getMaxHealth() * 0.9f) bonus += openingBonus(opening);
            int dusk = data.level(SubclassSkills.SOVEREIGN_OF_DUSK.path());
            if (dusk > 0) bonus += duskBonus(dusk);
            int core = data.level(SubclassSkills.CELESTIAL_CORE.path());
            if (core > 0 && !melee && !projectile) bonus += celestialBonus(core);
            int field = data.level(SubclassSkills.FIELD_CRAFT.path());
            if (field > 0 && target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) bonus += fieldCraft(field);
            if (projectile) {
                bonus += steadyAim(data.level(SubclassSkills.STEADY_AIM.path())) * sign(data.level(SubclassSkills.STEADY_AIM.path()));
                int quiver = data.level(SubclassSkills.CHARGED_QUIVER.path());
                if (quiver > 0) {
                    bonus += quiverBonus(quiver);
                    if (p.getRandom().nextFloat() * 100 < quiverSpark(quiver)) {
                        amount += 2 + quiver;
                        SkillFx.play(target, SkillFxPayload.Kind.HEAL, SubclassSkills.CHARGED_QUIVER.color(), target.position(), 0.8f, 0);
                        target.level().playSound(null, target.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.25f, 2f);
                    }
                }
                int eagle = data.level(SubclassSkills.EAGLE_SOVEREIGN.path());
                if (eagle > 0) bonus += eagleBonus(eagle);
            }
            if (data.hasBuff(SubclassActions.BUFF_ECLIPSE, now)) bonus += 40;

            // Death Sentence: more damage from the hunter who marked it
            long[] sentence = SubclassActions.SENTENCES.get(target);
            if (sentence != null) {
                if (sentence[0] > now && sentence[2] == p.getId()) bonus += sentence[1];
                else if (sentence[0] <= now) SubclassActions.SENTENCES.remove(target);
            }
            if (bonus != 0) amount *= 1 + bonus / 100f;

            // on-hit statuses
            int virulence = data.level(SubclassSkills.VIRULENCE.path());
            if (virulence > 0 && melee) Statuses.apply(target, DamageElement.POISON, virulence(virulence), p);
            int kindled = data.level(SubclassSkills.KINDLED_SOUL.path());
            if (kindled > 0) Statuses.apply(target, DamageElement.FIRE, kindle(kindled), p);
            int vigor = data.level(SubclassSkills.CRIMSON_VIGOR.path());
            if (vigor > 0) Buildups.add(target, Buildups.BLOOD, vigorBlood(vigor), p);
            // Blood Frenzy lifesteal
            if (melee && data.hasBuff(SubclassActions.BUFF_FRENZY, now)) {
                p.heal(amount * SubclassActions.frenzyLifesteal(Math.max(1, data.level(SubclassSkills.BLOOD_FRENZY.path()))) / 100f);
            }
            if (projectile && direct.getTags().contains(SubclassActions.STORM_ARROW_TAG) && direct instanceof AbstractArrow arrow && arrow.getOwner() == target) {
                event.setCanceled(true);
                return;
            }
        }

        // ---- the player is struck
        if (target instanceof ServerPlayer p && attacker != p) {
            SkillData data = SkillAPI.get(p);
            int dodge = data.level(SubclassSkills.EVASIVE_STEP.path());
            int foresight = data.level(SubclassSkills.FORESIGHT.path());
            float chance = (dodge > 0 ? evasion(dodge) : 0) + (foresight > 0 ? foresightDodge(foresight) : 0);
            if (chance > 0 && attacker instanceof LivingEntity && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                    && p.getRandom().nextFloat() * 100 < chance) {
                event.setCanceled(true);
                p.level().playSound(null, p.blockPosition(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.6f, 1.6f);
                SkillFx.play(p, SkillFxPayload.Kind.DASH, SubclassSkills.EVASIVE_STEP.color(), p.position(), 0.8f, 6);
                return;
            }
            int ward = data.level(SubclassSkills.ARCANE_WARD.path());
            if (ward > 0 && (event.getSource().is(DamageTypeTags.WITCH_RESISTANT_TO) || event.getSource().is(DamageTypeTags.BYPASSES_ARMOR))) {
                amount *= 1 - wardReduction(ward) / 100f;
            }
            if (data.hasBuff(SubclassActions.BUFF_MANA_BARRIER, now)) SystemAPI.restoreMana(p, amount * 0.5f);
            if (data.hasBuff(SubclassActions.BUFF_RETRIBUTION, now)) {
                float[] stored = SubclassActions.STORED.get(p);
                if (stored != null) stored[0] += amount;
            }
            if (attacker instanceof LivingEntity foe && foe.isAlive()) {
                int spite = data.level(SubclassSkills.BURNING_SPITE.path());
                if (spite > 0) Statuses.apply(foe, DamageElement.FIRE, spite(spite), p);
                if (data.hasBuff(SubclassActions.BUFF_FORTRESS, now) && direct == foe) {
                    foe.hurt(p.damageSources().thorns(p), amount * 0.3f);
                }
            }
        }
        if (amount != event.getAmount()) event.setAmount(amount);
    }

    private static int sign(int level) {
        return level > 0 ? 1 : 0;
    }

    private static boolean dark(ServerPlayer p) {
        var level = p.level();
        int block = level.getBrightness(LightLayer.BLOCK, p.blockPosition());
        int sky = level.getBrightness(LightLayer.SKY, p.blockPosition()) - level.getSkyDarken();
        return Math.max(block, sky) < 8;
    }

    // ================================================================ deaths

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        // Undying / Eternal Grace: the player refuses to fall, now and then
        if (dead instanceof ServerPlayer p) {
            SkillData data = SkillAPI.get(p);
            int undying = Math.max(data.level(SubclassSkills.UNDYING.path()), data.level(SubclassSkills.ETERNAL_GRACE.path()));
            if (undying > 0 && !data.hasBuff(BUFF_SAVED, p.level().getGameTime()) && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                event.setCanceled(true);
                p.setHealth(p.getMaxHealth() * 0.5f);
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 3));
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
                SkillAPI.setBuff(p, BUFF_SAVED, Math.round(undyingCooldown(undying) * 20));
                p.level().playSound(null, p.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8f, 1.2f);
                SkillFx.play(p, SkillFxPayload.Kind.DOME, SubclassSkills.UNDYING.color(), p.position(), 2, 40);
                SkillFx.play(p, SkillFxPayload.Kind.HEAL, SubclassSkills.UNDYING.color(), p.position(), 1.5f, 0);
                return;
            }
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || dead == p) return;
        SkillData data = SkillAPI.get(p);
        int pact = data.level(SubclassSkills.GRAVE_PACT.path());
        if (pact > 0) {
            p.heal(pactHeal(pact));
            SystemAPI.restoreMana(p, pactMana(pact));
            SkillFx.play(dead, SkillFxPayload.Kind.BOLT, SubclassSkills.GRAVE_PACT.color(), p.position().add(0, 1, 0), 0.2f, 0);
        }
        long[] sentence = SubclassActions.SENTENCES.remove(dead);
        if (sentence != null && sentence[0] > p.level().getGameTime() && sentence[2] == p.getId()) {
            // the contract is fulfilled: Death Sentence is ready again and the hunter is paid
            SkillAPI.get(p).startCooldown(SubclassSkills.DEATH_SENTENCE.path(), p.level().getGameTime(), 0);
            SystemAPI.restoreMana(p, 20);
            SkillAPI.sync(p);
            p.level().playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 0.6f);
        }
    }

    // ================================================================ upkeep

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) SubclassActions.endColossus(p);
    }

    @SubscribeEvent
    static void onBuffEnd(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p && p.tickCount % 10 == 0
                && p.getAttribute(Attributes.SCALE) != null
                && p.getAttribute(Attributes.SCALE).getModifier(BeyondSolo.id("skill/colossus_form/scale")) != null
                && !SkillAPI.hasBuff(p, SubclassActions.BUFF_COLOSSUS)) {
            SubclassActions.endColossus(p);
        }
    }

    /** Summoned wolves go home when their time is up, also after a restart. */
    @SubscribeEvent
    static void onEntityTick(EntityTickEvent.Post event) {
        Entity e = event.getEntity();
        if (e.tickCount % 20 != 0 || !(e.level() instanceof ServerLevel level) || !e.getTags().contains(SubclassActions.SUMMON_TAG)) return;
        if (level.getGameTime() > e.getPersistentData().getLong(SubclassActions.SUMMON_EXPIRES)) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, e.getX(), e.getY() + 0.5, e.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
            e.discard();
        }
    }

    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity().getTags().contains(SubclassActions.SUMMON_TAG)
                && event.getLevel().getGameTime() > event.getEntity().getPersistentData().getLong(SubclassActions.SUMMON_EXPIRES)) {
            event.setCanceled(true);
        }
    }

    private SubclassPassives() {}
}
